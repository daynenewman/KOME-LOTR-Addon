package kome.common.data;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static kome.common.data.KOMEConflictContracts.*;

/**
 * Immutable tile-conflict snapshot. No world access, serialization or autonomous entity/gameplay behavior.
 * The service alone publishes snapshots; origin classifications are history, NOT diplomatic sides.
 */
public final class KOMEConflictRecord {
    public enum State { ORDINARY, ENCIRCLEMENT, ENDED }
    public enum EntryOrigin { LEGAL_ARRIVAL, ORIGINAL_GARRISON, RELIEF, EXTERIOR_ARRIVAL }
    public enum PlayerStatus { ACTIVE, WITHDRAWN }
    public enum GarrisonMemberState { ORIGINAL, UNKNOWN, CONFIRMED_TERMINAL }
    public enum TimerStatus { NOT_STARTED, RUNNING, PAUSED, COMPLETE }
    public enum EpisodeState { INACTIVE, ACTIVE }
    public enum ComplexState { UNTAKEN, ASSAULT_ACTIVE, CAPTURED }
    public enum Operation {
        CREATE, END, COMMIT, FACTION_JOIN, FACTION_LEAVE, PLAYER_REGISTER, PLAYER_WITHDRAW,
        COMPLEX_REGISTER, CATALOG_BIND, COMPLEX_CHECKPOINT, COMBAT_CHECKPOINT,
        ENCIRCLEMENT_CHECKPOINT, REFERENCE_DIAGNOSTIC, DETACHMENT_DEPARTURE,
        GARRISON_MEMBER_REKEY
    }

    private final String conflictId;
    private final String tileId;
    private final State state;
    private final long revision;
    private final long createdAtMillis;
    private final Long endedAtMillis;
    private final Map<String, Commitment> commitments;
    private final Map<String, FactionParticipation> factionParticipation;
    private final Map<UUID, PlayerParticipation> players;
    private final Map<String, GarrisonCohort> originalGarrison;
    private final Map<String, ComplexSubstate> complexes;
    private final ComplexCatalog complexCatalog;
    private final TimerSlot responseTimer;
    private final TimerSlot captureTimer;
    private final CombatEpisode combatEpisode;
    private final EncirclementLifecycle encirclement;
    private final Map<String, ReferenceDiagnostic> diagnostics;
    private final LastTransition lastTransition;

    KOMEConflictRecord(Draft draft) {
        conflictId = KOMEConflictIdAllocator.requireIdentity(draft.conflictId);
        tileId = tile(draft.tileId);
        state = required(draft.state, "Conflict state");
        if (draft.revision < 1L) throw new IllegalArgumentException("Revision must be positive.");
        revision = draft.revision;
        createdAtMillis = nonnegative(draft.createdAtMillis, "Creation timestamp");
        endedAtMillis = draft.endedAtMillis;
        if ((state == State.ENDED) != (endedAtMillis != null)
                || endedAtMillis != null && endedAtMillis < createdAtMillis)
            throw new IllegalArgumentException("Ended state and timestamp disagree.");
        commitments = freeze(draft.commitments);
        factionParticipation = freeze(draft.factionParticipation);
        players = freeze(draft.players);
        originalGarrison = freeze(draft.originalGarrison);
        complexes = freeze(draft.complexes);
        complexCatalog = required(draft.complexCatalog, "Complex catalog");
        responseTimer = required(draft.responseTimer, "Response timer");
        captureTimer = required(draft.captureTimer, "Capture timer");
        combatEpisode = required(draft.combatEpisode, "Combat episode");
        encirclement = draft.encirclement;
        diagnostics = freeze(draft.diagnostics);
        lastTransition = required(draft.lastTransition, "Last transition");
        validateAggregate();
    }

    /** Codec-independent shape invariants, also applicable to Phase 2's detached load candidate. */
    private void validateAggregate() {
        long lastTime = lastTransition.timestampMillis;
        if (lastTransition.revision != revision || lastTransition.toState != state || lastTime < createdAtMillis
                || endedAtMillis != null && endedAtMillis != lastTime)
            throw new IllegalArgumentException("Record and last transition disagree.");
        if (state == State.ENCIRCLEMENT && encirclement == null || state == State.ORDINARY && encirclement != null)
            throw new IllegalArgumentException("Umbrella state and Encirclement lifecycle disagree.");
        if (encirclement == null && (!originalGarrison.isEmpty() || !complexes.isEmpty()
                || complexCatalog.availability != CatalogAvailability.UNAVAILABLE))
            throw new IllegalArgumentException("Fortification state requires an Encirclement lifecycle.");
        if (encirclement != null && (encirclement.startedAtMillis != createdAtMillis
                || encirclement.checkpointAtMillis > lastTime
                || !java.util.Objects.equals(encirclement.endedAtMillis, endedAtMillis)))
            throw new IllegalArgumentException("Encirclement continuity and record timestamps disagree.");
        int validatedCreationEvents = 0;
        for (Map.Entry<String, Commitment> entry : commitments.entrySet()) {
            Commitment value = entry.getValue();
            requireKey(entry.getKey(), value.detachmentId);
            requireTime(value.acceptedAtMillis, lastTime);
            if (value.origin == EntryOrigin.ORIGINAL_GARRISON
                    && !originalGarrison.containsKey(value.detachmentId)
                    || value.origin != EntryOrigin.ORIGINAL_GARRISON
                    && originalGarrison.containsKey(value.detachmentId))
                throw new IllegalArgumentException("Original-garrison commitment and cohort disagree.");
            if (value.origin == EntryOrigin.RELIEF && encirclement == null)
                throw new IllegalArgumentException("Encirclement relief requires an Encirclement lifecycle.");
            if (value.validatedEvent != null) {
                ValidatedCommitmentEvent event = value.validatedEvent;
                if (!factionParticipation.containsKey(event.detachmentFactionId)
                        || !factionParticipation.containsKey(event.authorityFactionId))
                    throw new IllegalArgumentException(
                        "Validated commitment event lacks faction participation history.");
                for (String garrisonFaction : event.originalGarrisonFactions.values())
                    if (!factionParticipation.containsKey(garrisonFaction))
                        throw new IllegalArgumentException(
                            "Validated garrison event lacks faction participation history.");
                if (event.createdConflict) {
                    validatedCreationEvents++;
                    if (value.acceptedAtMillis != createdAtMillis
                            || event.defensiveContext != (encirclement != null)
                            || !event.originalGarrisonFactions.keySet()
                                .equals(originalGarrison.keySet()))
                        throw new IllegalArgumentException(
                            "Validated creation event and conflict origin disagree.");
                }
            }
        }
        if (validatedCreationEvents > 1)
            throw new IllegalArgumentException("Multiple validated conflict-creation events.");
        Set<UUID> cohortMembers = new LinkedHashSet<UUID>();
        for (Map.Entry<String, GarrisonCohort> entry : originalGarrison.entrySet()) {
            requireKey(entry.getKey(), entry.getValue().detachmentId);
            for (UUID member : entry.getValue().members.keySet())
                if (!cohortMembers.add(member)) throw new IllegalArgumentException("Duplicate original-garrison member.");
        }
        for (Map.Entry<String, FactionParticipation> entry : factionParticipation.entrySet()) {
            requireKey(entry.getKey(), entry.getValue().factionId);
            requireTime(entry.getValue().startedAtMillis, lastTime);
            if (entry.getValue().endedAtMillis != null) requireTime(entry.getValue().endedAtMillis, lastTime);
        }
        for (Map.Entry<UUID, PlayerParticipation> entry : players.entrySet()) {
            requireKey(entry.getKey(), entry.getValue().playerId);
            requireTime(entry.getValue().startedAtMillis, lastTime);
            if (entry.getValue().withdrawnAtMillis != null) requireTime(entry.getValue().withdrawnAtMillis, lastTime);
        }
        for (Map.Entry<String, ComplexSubstate> entry : complexes.entrySet()) requireKey(entry.getKey(), entry.getValue().complexId);
        if (complexCatalog.availability == CatalogAvailability.AVAILABLE && !complexCatalog.requiredComplexIds.equals(complexes.keySet()))
            throw new IllegalArgumentException("Bound catalog and complex references disagree.");
        for (Map.Entry<String, ReferenceDiagnostic> entry : diagnostics.entrySet()) requireKey(entry.getKey(), entry.getValue().key());
        if (combatEpisode.sequence > 0) {
            if (!combatEpisode.episodeId.equals(conflictId + ":E" + combatEpisode.sequence))
                throw new IllegalArgumentException("Episode belongs to another conflict or sequence.");
            requireTime(combatEpisode.startedAtMillis, lastTime);
            if (combatEpisode.endedAtMillis != null) requireTime(combatEpisode.endedAtMillis, lastTime);
        }
        for (TimerSlot timer : new TimerSlot[] {responseTimer, captureTimer}) {
            if (timer.status != TimerStatus.NOT_STARTED && (!timer.episodeId.equals(combatEpisode.episodeId)
                    || timer.checkpointAtMillis < combatEpisode.startedAtMillis || timer.checkpointAtMillis > lastTime))
                throw new IllegalArgumentException("Timer and episode checkpoint disagree.");
        }
    }

    private void requireTime(long timestamp, long lastTime) {
        if (timestamp < createdAtMillis || timestamp > lastTime) throw new IllegalArgumentException("Fact is outside conflict lifetime.");
    }
    private static void requireKey(Object key, Object identity) {
        if (!key.equals(identity)) throw new IllegalArgumentException("Collection key and identity disagree.");
    }

    public String getConflictId() { return conflictId; }
    public String getTileId() { return tileId; }
    public State getState() { return state; }
    public boolean isActive() { return state != State.ENDED; }
    public long getRevision() { return revision; }
    public long getCreatedAtMillis() { return createdAtMillis; }
    public Long getEndedAtMillis() { return endedAtMillis; }
    /** An ended snapshot retains historical facts only; consumers must check isActive(). */
    public Map<String, Commitment> getCommitments() { return commitments; }
    public Map<String, FactionParticipation> getFactionParticipation() { return factionParticipation; }
    public Map<UUID, PlayerParticipation> getPlayers() { return players; }
    public Map<String, GarrisonCohort> getOriginalGarrison() { return originalGarrison; }
    public Map<String, ComplexSubstate> getComplexes() { return complexes; }
    public ComplexCatalog getComplexCatalog() { return complexCatalog; }
    public TimerSlot getResponseTimer() { return responseTimer; }
    public TimerSlot getCaptureTimer() { return captureTimer; }
    public CombatEpisode getCombatEpisode() { return combatEpisode; }
    public EncirclementLifecycle getEncirclement() { return encirclement; }
    public Map<String, ReferenceDiagnostic> getDiagnostics() { return diagnostics; }
    public LastTransition getLastTransition() { return lastTransition; }
    public Set<UUID> getWithdrawnPlayers() {
        Set<UUID> result = new LinkedHashSet<UUID>();
        for (PlayerParticipation player : players.values())
            if (player.status == PlayerStatus.WITHDRAWN) result.add(player.playerId);
        return Collections.unmodifiableSet(result);
    }

    public static final class Commitment {
        public final String detachmentId;
        public final EntryOrigin origin;
        public final long acceptedAtMillis;
        public final String movementOrderId;
        public final ValidatedCommitmentEvent validatedEvent;

        public Commitment(String detachmentId, EntryOrigin origin, long acceptedAtMillis, String movementOrderId) {
            this(detachmentId, origin, acceptedAtMillis, movementOrderId, null);
        }

        public Commitment(String detachmentId, EntryOrigin origin, long acceptedAtMillis,
                String movementOrderId, ValidatedCommitmentEvent validatedEvent) {
            this.detachmentId = companyId(detachmentId);
            this.origin = required(origin, "Commitment origin");
            this.acceptedAtMillis = nonnegative(acceptedAtMillis, "Commitment timestamp");
            this.movementOrderId = optionalId(movementOrderId);
            this.validatedEvent = validatedEvent;
            if (origin == EntryOrigin.ORIGINAL_GARRISON && validatedEvent != null)
                throw new IllegalArgumentException(
                    "Original-garrison seeds are not strategic-arrival events.");
        }
    }

    /** Persisted exact-replay identity for a validated strategic commitment event. */
    public static final class ValidatedCommitmentEvent {
        public final String detachmentFactionId;
        public final ConflictAuthorityKind authorityKind;
        public final String authorityFactionId;
        public final boolean createdConflict;
        public final boolean defensiveContext;
        public final Map<String, String> originalGarrisonFactions;

        public ValidatedCommitmentEvent(String detachmentFactionId,
                ConflictAuthorityKind authorityKind, String authorityFactionId,
                boolean createdConflict, boolean defensiveContext,
                Map<String, String> originalGarrisonFactions) {
            this.detachmentFactionId = faction(detachmentFactionId);
            this.authorityKind = required(authorityKind, "Conflict authority kind");
            this.authorityFactionId = faction(authorityFactionId);
            this.createdConflict = createdConflict;
            this.defensiveContext = defensiveContext;
            required(originalGarrisonFactions, "Original-garrison faction map");
            Map<String, String> factions = new LinkedHashMap<String, String>();
            for (Map.Entry<String, String> entry : originalGarrisonFactions.entrySet()) {
                String detachmentId = companyId(entry.getKey());
                if (factions.put(detachmentId, faction(entry.getValue())) != null)
                    throw new IllegalArgumentException("Duplicate original-garrison detachment.");
            }
            this.originalGarrisonFactions = Collections.unmodifiableMap(factions);
            if (createdConflict
                    != (authorityKind == ConflictAuthorityKind.VALIDATED_DEFENDER))
                throw new IllegalArgumentException(
                    "Creation events require validated defender authority; joins require a participant.");
            if (!createdConflict && (defensiveContext || !factions.isEmpty())
                    || !defensiveContext && !factions.isEmpty())
                throw new IllegalArgumentException(
                    "Only a defensive creation event may retain an original-garrison snapshot.");
        }
    }

    public static final class FactionParticipation {
        public final String factionId;
        public final long continuitySequence;
        public final long startedAtMillis;
        public final Long endedAtMillis;

        public FactionParticipation(String factionId, long continuitySequence, long startedAtMillis, Long endedAtMillis) {
            this.factionId = faction(factionId);
            if (continuitySequence < 1L) throw new IllegalArgumentException("Continuity sequence must be positive.");
            this.continuitySequence = continuitySequence;
            this.startedAtMillis = nonnegative(startedAtMillis, "Participation timestamp");
            this.endedAtMillis = endedAtMillis;
            if (endedAtMillis != null && endedAtMillis < startedAtMillis)
                throw new IllegalArgumentException("Participation cannot end before it starts.");
        }
        public boolean isActive() { return endedAtMillis == null; }
    }

    public static final class PlayerParticipation {
        public final UUID playerId;
        public final String factionAtRegistration;
        public final long startedAtMillis;
        public final PlayerStatus status;
        public final Long withdrawnAtMillis;

        public PlayerParticipation(UUID playerId, String faction, long startedAtMillis,
                PlayerStatus status, Long withdrawnAtMillis) {
            this.playerId = required(playerId, "Player UUID");
            this.factionAtRegistration = faction(faction);
            this.startedAtMillis = nonnegative(startedAtMillis, "Player registration timestamp");
            this.status = required(status, "Player participation status");
            this.withdrawnAtMillis = withdrawnAtMillis;
            if ((status == PlayerStatus.WITHDRAWN) != (withdrawnAtMillis != null)
                    || withdrawnAtMillis != null && withdrawnAtMillis < startedAtMillis)
                throw new IllegalArgumentException("Withdrawal status and timestamp disagree.");
        }
    }

    public static final class GarrisonCohort {
        public final String detachmentId;
        public final Map<UUID, GarrisonMemberState> members;

        public GarrisonCohort(String detachmentId, Map<UUID, GarrisonMemberState> members) {
            this.detachmentId = companyId(detachmentId);
            this.members = freeze(members);
        }
        /** Not a count of observed living entities. UNKNOWN/missing is explicitly NOT terminal. */
        public int unconfirmedTerminalMembers() {
            int count = 0;
            for (GarrisonMemberState state : members.values()) if (state != GarrisonMemberState.CONFIRMED_TERMINAL) count++;
            return count;
        }
    }

    /** Stable segment checkpoints only: no geometry, percentages or duplicate physical gate HP. */
    public static final class ProgressCheckpoint {
        public final Set<String> securedSegmentIds;
        public ProgressCheckpoint(Collection<String> securedSegmentIds) { this.securedSegmentIds = ids(securedSegmentIds); }
        public static ProgressCheckpoint empty() { return new ProgressCheckpoint(Collections.<String>emptySet()); }
    }

    /** Applies only to its containing active assault; not a global leader or an authoritative side. */
    public static final class LeadAuthority {
        public final String factionId;
        public final UUID actorId;
        public LeadAuthority(String factionId, UUID actorId) { this.factionId = faction(factionId); this.actorId = actorId; }
    }

    public static final class ComplexSubstate {
        public final String complexId;
        public final ComplexState state;
        public final String activeAssaultId;
        public final ProgressCheckpoint progress;
        public final LeadAuthority lead;

        public ComplexSubstate(String complexId, ComplexState state, String activeAssaultId,
                ProgressCheckpoint progress, LeadAuthority lead) {
            this.complexId = id(complexId);
            this.state = required(state, "Complex state");
            this.activeAssaultId = optionalId(activeAssaultId);
            this.progress = required(progress, "Progress checkpoint");
            this.lead = lead;
            if ((state == ComplexState.ASSAULT_ACTIVE) != !this.activeAssaultId.isEmpty()
                    || lead != null && state != ComplexState.ASSAULT_ACTIVE)
                throw new IllegalArgumentException("Assault identity/lead must be scoped to an active assault.");
        }
        public boolean isCaptured() { return state == ComplexState.CAPTURED; }
        public static ComplexSubstate unstarted(String id) {
            return new ComplexSubstate(id, ComplexState.UNTAKEN, "", ProgressCheckpoint.empty(), null);
        }
    }

    /** Passive clock ledger. Constructing/updating it performs no ticking or expiry action. */
    public static final class TimerSlot {
        public final TimerStatus status;
        public final long durationSnapshotMillis;
        public final long elapsedMillis;
        public final Long checkpointAtMillis;
        public final String episodeId;

        public TimerSlot(TimerStatus status, long durationSnapshotMillis, long elapsedMillis,
                Long checkpointAtMillis, String episodeId) {
            this.status = required(status, "Timer status");
            this.durationSnapshotMillis = nonnegative(durationSnapshotMillis, "Timer duration");
            this.elapsedMillis = nonnegative(elapsedMillis, "Timer elapsed time");
            this.checkpointAtMillis = checkpointAtMillis;
            this.episodeId = optionalId(episodeId);
            if (status == TimerStatus.NOT_STARTED) {
                if (elapsedMillis != 0L || checkpointAtMillis != null || !this.episodeId.isEmpty())
                    throw new IllegalArgumentException("Unstarted timer cannot claim elapsed time or an episode.");
            } else if (checkpointAtMillis == null || checkpointAtMillis < 0L || this.episodeId.isEmpty()) {
                throw new IllegalArgumentException("Started timer requires checkpoint and episode identity.");
            }
            if (status == TimerStatus.COMPLETE && elapsedMillis < durationSnapshotMillis)
                throw new IllegalArgumentException("Complete timer has not reached its duration.");
        }
        public static TimerSlot unstarted() { return new TimerSlot(TimerStatus.NOT_STARTED, 0, 0, null, ""); }
    }

    public static final class CombatEpisode {
        public final long sequence;
        public final String episodeId;
        public final EpisodeState state;
        public final Long startedAtMillis;
        public final Long endedAtMillis;

        public CombatEpisode(long sequence, String episodeId, EpisodeState state, Long startedAtMillis, Long endedAtMillis) {
            this.sequence = nonnegative(sequence, "Episode sequence");
            this.episodeId = optionalId(episodeId);
            this.state = required(state, "Episode state");
            this.startedAtMillis = startedAtMillis;
            this.endedAtMillis = endedAtMillis;
            if (sequence == 0L) {
                if (!this.episodeId.isEmpty() || state != EpisodeState.INACTIVE || startedAtMillis != null || endedAtMillis != null)
                    throw new IllegalArgumentException("Unstarted episode cannot claim continuity.");
            } else if (this.episodeId.isEmpty() || startedAtMillis == null || startedAtMillis < 0L
                    || (state == EpisodeState.INACTIVE) != (endedAtMillis != null)
                    || endedAtMillis != null && endedAtMillis < startedAtMillis) {
                throw new IllegalArgumentException("Episode identity/status/continuity timestamps disagree.");
            }
        }
        public static CombatEpisode unstarted() { return new CombatEpisode(0, "", EpisodeState.INACTIVE, null, null); }
    }

    public static final class EncirclementLifecycle {
        public final long startedAtMillis;
        public final long liveElapsedMillis;
        public final long checkpointAtMillis;
        public final Long endedAtMillis;

        public EncirclementLifecycle(long startedAtMillis, long liveElapsedMillis, long checkpointAtMillis, Long endedAtMillis) {
            this.startedAtMillis = nonnegative(startedAtMillis, "Encirclement start");
            this.liveElapsedMillis = nonnegative(liveElapsedMillis, "Encirclement live elapsed time");
            this.checkpointAtMillis = checkpointAtMillis;
            this.endedAtMillis = endedAtMillis;
            if (checkpointAtMillis < startedAtMillis || endedAtMillis != null && endedAtMillis < checkpointAtMillis)
                throw new IllegalArgumentException("Encirclement timestamps are inconsistent.");
        }
    }

    public static final class LastTransition {
        public final Operation operation;
        public final State fromState;
        public final State toState;
        public final long revision;
        public final long timestampMillis;
        public final String actor;
        public final String reason;
        public final String subject;

        LastTransition(Operation operation, State fromState, State toState, long revision, Context context, String subject) {
            this.operation = required(operation, "Transition operation");
            this.fromState = fromState;
            this.toState = required(toState, "Transition destination");
            this.revision = revision;
            this.timestampMillis = context.timestampMillis;
            this.actor = context.actor;
            this.reason = context.reason;
            this.subject = optional(subject);
            if (revision < 1L || operation == Operation.CREATE && (toState == State.ENDED
                    || fromState != null && fromState != State.ENDED)
                    || operation == Operation.END && (fromState == null || fromState == State.ENDED || toState != State.ENDED)
                    || operation != Operation.CREATE && operation != Operation.END && (fromState != toState || toState == State.ENDED))
                throw new IllegalArgumentException("Invalid high-level transition metadata.");
        }
    }

    static <K, V> Map<K, V> freeze(Map<K, V> values) {
        required(values, "Map");
        Map<K, V> copy = new LinkedHashMap<K, V>();
        for (Map.Entry<K, V> entry : values.entrySet())
            copy.put(required(entry.getKey(), "Map key"), required(entry.getValue(), "Map value"));
        return Collections.unmodifiableMap(copy);
    }

    /** Private-to-package publication draft, never returned to consumers or inserted by callers. */
    static final class Draft {
        final String conflictId;
        final String tileId;
        State state;
        long revision;
        final long createdAtMillis;
        Long endedAtMillis;
        final Map<String, Commitment> commitments = new LinkedHashMap<String, Commitment>();
        final Map<String, FactionParticipation> factionParticipation = new LinkedHashMap<String, FactionParticipation>();
        final Map<UUID, PlayerParticipation> players = new LinkedHashMap<UUID, PlayerParticipation>();
        final Map<String, GarrisonCohort> originalGarrison = new LinkedHashMap<String, GarrisonCohort>();
        final Map<String, ComplexSubstate> complexes = new LinkedHashMap<String, ComplexSubstate>();
        ComplexCatalog complexCatalog = ComplexCatalog.unavailable();
        TimerSlot responseTimer = TimerSlot.unstarted();
        TimerSlot captureTimer = TimerSlot.unstarted();
        CombatEpisode combatEpisode = CombatEpisode.unstarted();
        EncirclementLifecycle encirclement;
        final Map<String, ReferenceDiagnostic> diagnostics = new LinkedHashMap<String, ReferenceDiagnostic>();
        LastTransition lastTransition;

        Draft(String conflictId, String tileId, State state, long createdAtMillis) {
            this.conflictId = conflictId; this.tileId = tileId; this.state = state; this.createdAtMillis = createdAtMillis;
            if (state == State.ENCIRCLEMENT) encirclement = new EncirclementLifecycle(createdAtMillis, 0, createdAtMillis, null);
        }
        Draft(KOMEConflictRecord record) {
            conflictId = record.conflictId; tileId = record.tileId; state = record.state;
            revision = record.revision; createdAtMillis = record.createdAtMillis; endedAtMillis = record.endedAtMillis;
            commitments.putAll(record.commitments); factionParticipation.putAll(record.factionParticipation);
            players.putAll(record.players); originalGarrison.putAll(record.originalGarrison); complexes.putAll(record.complexes);
            complexCatalog = record.complexCatalog; responseTimer = record.responseTimer; captureTimer = record.captureTimer;
            combatEpisode = record.combatEpisode; encirclement = record.encirclement;
            diagnostics.putAll(record.diagnostics); lastTransition = record.lastTransition;
        }
    }
}
