package kome.common.data;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static kome.common.data.KOMEConflictContracts.*;
import static kome.common.data.KOMEConflictRecord.*;

/**
 * Authoritative in-memory registry/mutation boundary. No production singleton or world hook exists
 * in Phase 1. Future adapters must authorize gameplay requests BEFORE invoking these data commands.
 * All mutations publish a new immutable snapshot, with exact conflict-ID/revision preconditions.
 */
public final class KOMEConflictService {
    private final Map<String, KOMEConflictRecord> records = new LinkedHashMap<String, KOMEConflictRecord>();
    private final KOMEConflictIdAllocator allocator;

    public KOMEConflictService() { this(1L); }
    public KOMEConflictService(long nextConflictSequence) { allocator = new KOMEConflictIdAllocator(nextConflictSequence); }

    public synchronized KOMEConflictRecord get(String tileId) { return records.get(KOMEConquestTile.normalizeId(tileId)); }
    public synchronized Map<String, KOMEConflictRecord> records() {
        return Collections.unmodifiableMap(new LinkedHashMap<String, KOMEConflictRecord>(records));
    }
    public synchronized long getNextConflictSequence() { return allocator.getNextSequence(); }

    /** Atomic persistence projection: registry and allocator high-water are captured together. */
    synchronized PersistenceSnapshot persistenceSnapshot() {
        return new PersistenceSnapshot(records, allocator.getNextSequence());
    }

    /** Strict detached restore; no runtime/world-dependent reference resolution occurs here. */
    static KOMEConflictService restore(Map<String, KOMEConflictRecord> restoredRecords, long nextConflictSequence) {
        required(restoredRecords, "Conflict registry");
        if (nextConflictSequence < 1L)
            throw new IllegalArgumentException("Next conflict sequence must be positive.");
        Map<String, KOMEConflictRecord> validated = new LinkedHashMap<String, KOMEConflictRecord>();
        Set<String> conflictIds = new HashSet<String>();
        Set<String> activeDetachments = new HashSet<String>();
        long highestSequence = 0L;
        for (Map.Entry<String, KOMEConflictRecord> entry : restoredRecords.entrySet()) {
            KOMEConflictRecord record = required(entry.getValue(), "Conflict record");
            String tileId = tile(entry.getKey());
            if (!tileId.equals(entry.getKey()) || !tileId.equals(record.getTileId()))
                throw new IllegalArgumentException("Conflict registry tile key is noncanonical or mismatched.");
            if (validated.put(tileId, record) != null)
                throw new IllegalArgumentException("Duplicate conflict tile authority: " + tileId);
            if (!conflictIds.add(record.getConflictId()))
                throw new IllegalArgumentException("Duplicate conflict identity: " + record.getConflictId());
            highestSequence = Math.max(highestSequence, KOMEConflictIdAllocator.sequenceOf(record.getConflictId()));
            if (record.isActive()) {
                for (String detachmentId : record.getCommitments().keySet()) {
                    if (!activeDetachments.add(detachmentId))
                        throw new IllegalArgumentException("Detachment is committed to multiple active conflicts: " + detachmentId);
                }
            }
        }
        if (nextConflictSequence <= highestSequence)
            throw new IllegalArgumentException("Next conflict sequence does not exceed persisted conflict identities.");
        KOMEConflictService service = new KOMEConflictService(nextConflictSequence);
        service.records.putAll(validated);
        return service;
    }

    /** Candidate publication preserves this service object's identity for existing readers. */
    synchronized void replaceFrom(KOMEConflictService source) {
        PersistenceSnapshot snapshot = required(source, "Conflict service").persistenceSnapshot();
        KOMEConflictService validated = restore(snapshot.records, snapshot.nextConflictSequence);
        records.clear();
        records.putAll(validated.records);
        allocator.setNextSequence(validated.allocator.getNextSequence());
    }

    static final class PersistenceSnapshot {
        final Map<String, KOMEConflictRecord> records;
        final long nextConflictSequence;

        PersistenceSnapshot(Map<String, KOMEConflictRecord> records, long nextConflictSequence) {
            this.records = Collections.unmodifiableMap(new LinkedHashMap<String, KOMEConflictRecord>(records));
            this.nextConflictSequence = nextConflictSequence;
        }
    }

    /** Only absent/ended -> fresh ORDINARY/ENCIRCLEMENT; an ended predecessor must be acknowledged. */
    public synchronized Result start(String tileId, State initialState, ExpectedConflict expected,
            Collection<GarrisonSeed> originalGarrison, Context context) {
        String normalized;
        try { normalized = tile(tileId); required(context, "Context"); required(expected, "Expected conflict"); }
        catch (IllegalArgumentException invalid) { return Result.failure(Code.INVALID_REQUEST, null, invalid.getMessage()); }
        KOMEConflictRecord previous = records.get(normalized);
        if (initialState != State.ORDINARY && initialState != State.ENCIRCLEMENT)
            return Result.failure(Code.INVALID_TRANSITION, previous, "Only ordinary or encirclement may be started.");
        Code guard = guard(previous, expected, context);
        if (guard != Code.SUCCESS) return Result.failure(guard, previous, "Conflict creation precondition failed.");
        if (previous != null && previous.isActive())
            return Result.failure(Code.ACTIVE_CONFLICT_EXISTS, previous, "This tile already has an active conflict.");
        if (originalGarrison == null)
            return Result.failure(Code.INVALID_REQUEST, previous, "Explicit original-garrison snapshot required.");
        if (initialState != State.ENCIRCLEMENT && !originalGarrison.isEmpty())
            return Result.failure(Code.INVALID_TRANSITION, previous, "Only Encirclement has a trapped-garrison cohort.");
        if (allocator.getNextSequence() == Long.MAX_VALUE)
            return Result.failure(Code.IDENTITY_EXHAUSTED, previous, "Conflict identities are exhausted.");

        // Validate/build before consuming an identity or publishing any part of the new record.
        Draft draft = new Draft("CF" + allocator.getNextSequence(), normalized, initialState, context.timestampMillis);
        Set<UUID> members = new HashSet<UUID>();
        for (GarrisonSeed seed : originalGarrison) {
            if (seed == null) return Result.failure(Code.INVALID_REQUEST, previous, "Null garrison seed.");
            if (seed.classification != KOMEHiredUnitClass.CAMPAIGN)
                return Result.failure(Code.ORDINARY_NOT_ELIGIBLE, previous, "Garrison commitments require CAMPAIGN classification.");
            if (draft.commitments.containsKey(seed.detachmentId))
                return Result.failure(Code.DUPLICATE_DETACHMENT, previous, "Duplicate initial detachment.");
            if (committedElsewhere(seed.detachmentId, normalized))
                return Result.failure(Code.DETACHMENT_ALREADY_COMMITTED, previous, "Detachment is committed in another active tile.");
            Map<UUID, GarrisonMemberState> cohort = new LinkedHashMap<UUID, GarrisonMemberState>();
            for (UUID member : seed.originalMembers) {
                if (!members.add(member))
                    return Result.failure(Code.DUPLICATE_GARRISON_MEMBER, previous, "Original member appears in multiple cohorts.");
                cohort.put(member, GarrisonMemberState.ORIGINAL);
            }
            draft.commitments.put(seed.detachmentId,
                new Commitment(seed.detachmentId, EntryOrigin.ORIGINAL_GARRISON, context.timestampMillis, ""));
            draft.originalGarrison.put(seed.detachmentId, new GarrisonCohort(seed.detachmentId, cohort));
        }
        draft.revision = 1L;
        draft.lastTransition = new LastTransition(Operation.CREATE, previous == null ? null : previous.getState(),
            initialState, 1L, context, draft.conflictId);
        KOMEConflictRecord record = new KOMEConflictRecord(draft);
        allocator.allocate();
        records.put(normalized, record);
        return Result.success(previous, record);
    }

    /** Explicit authorized ending, not victory inference. Preserves the final diagnostic snapshot. */
    public synchronized Result end(String tileId, ExpectedConflict expected, Context context) {
        return mutate(tileId, expected, context, Operation.END, "", draft -> {
            draft.state = State.ENDED;
            draft.endedAtMillis = context.timestampMillis;
            for (String id : draft.factionParticipation.keySet()) {
                FactionParticipation faction = draft.factionParticipation.get(id);
                if (faction.isActive()) draft.factionParticipation.put(id, new FactionParticipation(id,
                    faction.continuitySequence, faction.startedAtMillis, context.timestampMillis));
            }
            if (draft.encirclement != null) draft.encirclement = new EncirclementLifecycle(
                draft.encirclement.startedAtMillis, draft.encirclement.liveElapsedMillis,
                draft.encirclement.checkpointAtMillis, context.timestampMillis);
            return Code.SUCCESS;
        });
    }

    public synchronized Result commit(String tileId, ExpectedConflict expected, CommitmentInput input, Context context) {
        return mutate(tileId, expected, context, Operation.COMMIT, input == null ? "" : input.detachmentId, draft -> {
            required(input, "Commitment input");
            if (input.classification != KOMEHiredUnitClass.CAMPAIGN) return Code.ORDINARY_NOT_ELIGIBLE;
            if (input.origin == EntryOrigin.ORIGINAL_GARRISON
                    || input.origin == EntryOrigin.RELIEF && draft.state != State.ENCIRCLEMENT) return Code.INVALID_ORIGIN;
            if (draft.commitments.containsKey(input.detachmentId)) return Code.DUPLICATE_DETACHMENT;
            if (committedElsewhere(input.detachmentId, draft.tileId)) return Code.DETACHMENT_ALREADY_COMMITTED;
            draft.commitments.put(input.detachmentId, new Commitment(input.detachmentId, input.origin,
                context.timestampMillis, input.movementOrderId));
            return Code.SUCCESS;
        });
    }

    /** No diplomacy/side selection occurs here. Presence alone never invokes this command. */
    public synchronized Result beginFactionParticipation(String tileId, ExpectedConflict expected, String factionId, Context context) {
        return mutate(tileId, expected, context, Operation.FACTION_JOIN, factionId, draft -> {
            String id = faction(factionId);
            FactionParticipation previous = draft.factionParticipation.get(id);
            if (previous != null && previous.isActive()) return Code.FACTION_ALREADY_ACTIVE;
            if (previous != null && previous.continuitySequence == Long.MAX_VALUE) return Code.REVISION_EXHAUSTED;
            draft.factionParticipation.put(id, new FactionParticipation(id,
                previous == null ? 1L : previous.continuitySequence + 1L, context.timestampMillis, null));
            return Code.SUCCESS;
        });
    }

    public synchronized Result endFactionParticipation(String tileId, ExpectedConflict expected, String factionId, Context context) {
        return mutate(tileId, expected, context, Operation.FACTION_LEAVE, factionId, draft -> {
            String id = faction(factionId);
            FactionParticipation previous = draft.factionParticipation.get(id);
            if (previous == null || !previous.isActive()) return Code.FACTION_NOT_ACTIVE;
            draft.factionParticipation.put(id, new FactionParticipation(id, previous.continuitySequence,
                previous.startedAtMillis, context.timestampMillis));
            return Code.SUCCESS;
        });
    }

    public synchronized Result registerPlayer(String tileId, ExpectedConflict expected, UUID playerId, String factionId, Context context) {
        return mutate(tileId, expected, context, Operation.PLAYER_REGISTER, String.valueOf(playerId), draft -> {
            required(playerId, "Player UUID");
            if (draft.players.containsKey(playerId)) return Code.PLAYER_ALREADY_REGISTERED;
            draft.players.put(playerId, new PlayerParticipation(playerId, factionId, context.timestampMillis, PlayerStatus.ACTIVE, null));
            return Code.SUCCESS;
        });
    }

    /** Stores an explicit participation fact only. No retreat, death, route or detachment effect. */
    public synchronized Result withdrawPlayer(String tileId, ExpectedConflict expected, UUID playerId, Context context) {
        return mutate(tileId, expected, context, Operation.PLAYER_WITHDRAW, String.valueOf(playerId), draft -> {
            required(playerId, "Player UUID");
            PlayerParticipation previous = draft.players.get(playerId);
            if (previous == null || previous.status != PlayerStatus.ACTIVE) return Code.PLAYER_NOT_ACTIVE;
            draft.players.put(playerId, new PlayerParticipation(playerId, previous.factionAtRegistration,
                previous.startedAtMillis, PlayerStatus.WITHDRAWN, context.timestampMillis));
            return Code.SUCCESS;
        });
    }

    public synchronized Result registerComplex(String tileId, ExpectedConflict expected, String complexId, Context context) {
        return mutate(tileId, expected, context, Operation.COMPLEX_REGISTER, complexId, draft -> {
            if (draft.state != State.ENCIRCLEMENT) return Code.INVALID_TRANSITION;
            String id = id(complexId);
            if (draft.complexes.containsKey(id)) return Code.DUPLICATE_COMPLEX;
            if (draft.complexCatalog.availability == CatalogAvailability.AVAILABLE) return Code.AMBIGUOUS_REFERENCE;
            draft.complexes.put(id, ComplexSubstate.unstarted(id));
            return Code.SUCCESS;
        });
    }

    /** Binding cannot silently discard previously referenced complexes/progress or replace a known manifest. */
    public synchronized Result bindComplexCatalog(String tileId, ExpectedConflict expected, ComplexCatalog catalog, Context context) {
        return mutate(tileId, expected, context, Operation.CATALOG_BIND, "", draft -> {
            required(catalog, "Complex catalog");
            if (draft.state != State.ENCIRCLEMENT) return Code.INVALID_TRANSITION;
            if (catalog.availability != CatalogAvailability.AVAILABLE) return Code.INVALID_REFERENCE;
            if (!catalog.requiredComplexIds.containsAll(draft.complexes.keySet())) return Code.AMBIGUOUS_REFERENCE;
            if (draft.complexCatalog.availability == CatalogAvailability.AVAILABLE)
                return draft.complexCatalog.requiredComplexIds.equals(catalog.requiredComplexIds) ? Code.NO_CHANGE : Code.AMBIGUOUS_REFERENCE;
            for (String id : catalog.requiredComplexIds)
                if (!draft.complexes.containsKey(id)) draft.complexes.put(id, ComplexSubstate.unstarted(id));
            draft.complexCatalog = catalog;
            return Code.SUCCESS;
        });
    }

    /** Validated phase-controller checkpoint, not assault authorization or capture calculation. */
    public synchronized Result checkpointComplex(String tileId, ExpectedConflict expected, ComplexSubstate complex, Context context) {
        return mutate(tileId, expected, context, Operation.COMPLEX_CHECKPOINT, complex == null ? "" : complex.complexId, draft -> {
            required(complex, "Complex substate");
            if (draft.state != State.ENCIRCLEMENT) return Code.INVALID_TRANSITION;
            if (!draft.complexes.containsKey(complex.complexId)) return Code.INVALID_REFERENCE;
            draft.complexes.put(complex.complexId, complex);
            return Code.SUCCESS;
        });
    }

    /** Atomic DATA checkpoint. No clock source, duration selection, ticking or expiry consequence. */
    public synchronized Result checkpointCombat(String tileId, ExpectedConflict expected, CombatEpisode episode,
            TimerSlot response, TimerSlot capture, Context context) {
        return mutate(tileId, expected, context, Operation.COMBAT_CHECKPOINT, episode == null ? "" : episode.episodeId, draft -> {
            required(episode, "Episode"); required(response, "Response timer"); required(capture, "Capture timer");
            CombatEpisode previous = draft.combatEpisode;
            if (episode.sequence < previous.sequence || episode.sequence > previous.sequence
                    && (previous.sequence == Long.MAX_VALUE || episode.sequence != previous.sequence + 1L
                        || previous.state == EpisodeState.ACTIVE)) return Code.INVALID_EPISODE;
            if (episode.sequence > 0 && !episode.episodeId.equals(draft.conflictId + ":E" + episode.sequence)) return Code.INVALID_EPISODE;
            if (episode.startedAtMillis != null && (episode.startedAtMillis < draft.createdAtMillis
                    || episode.startedAtMillis > context.timestampMillis)) return Code.TIME_REGRESSION;
            if (episode.endedAtMillis != null && episode.endedAtMillis > context.timestampMillis) return Code.TIME_REGRESSION;
            if (episode.sequence == previous.sequence && (!java.util.Objects.equals(episode.startedAtMillis, previous.startedAtMillis)
                    || previous.endedAtMillis != null && !java.util.Objects.equals(episode.endedAtMillis, previous.endedAtMillis)))
                return Code.INVALID_EPISODE;
            if (!timerMatches(response, episode, context) || !timerMatches(capture, episode, context)) return Code.INVALID_REFERENCE;
            if (episode.sequence == previous.sequence && (!timerAdvances(draft.responseTimer, response)
                    || !timerAdvances(draft.captureTimer, capture))) return Code.INVALID_EPISODE;
            draft.combatEpisode = episode; draft.responseTimer = response; draft.captureTimer = capture;
            return Code.SUCCESS;
        });
    }

    public synchronized Result checkpointEncirclement(String tileId, ExpectedConflict expected, long liveElapsedMillis, Context context) {
        return mutate(tileId, expected, context, Operation.ENCIRCLEMENT_CHECKPOINT, "", draft -> {
            if (draft.state != State.ENCIRCLEMENT) return Code.INVALID_TRANSITION;
            if (liveElapsedMillis < draft.encirclement.liveElapsedMillis) return Code.TIME_REGRESSION;
            draft.encirclement = new EncirclementLifecycle(draft.encirclement.startedAtMillis,
                liveElapsedMillis, context.timestampMillis, null);
            return Code.SUCCESS;
        });
    }

    /** Diagnostic fact only: it cannot remove commitments or infer peace/withdrawal/death/relocation. */
    public synchronized Result recordDiagnostic(String tileId, ExpectedConflict expected, ReferenceDiagnostic diagnostic, Context context) {
        return mutate(tileId, expected, context, Operation.REFERENCE_DIAGNOSTIC, diagnostic == null ? "" : diagnostic.key(), draft -> {
            required(diagnostic, "Diagnostic");
            ReferenceDiagnostic previous = draft.diagnostics.get(diagnostic.key());
            if (previous != null && previous.status == diagnostic.status && previous.reason.equals(diagnostic.reason)) return Code.NO_CHANGE;
            draft.diagnostics.put(diagnostic.key(), diagnostic);
            return Code.SUCCESS;
        });
    }

    private Result mutate(String tileId, ExpectedConflict expected, Context context, Operation operation, String subject, Change change) {
        String normalized;
        try { normalized = tile(tileId); required(context, "Context"); required(expected, "Expected conflict"); }
        catch (IllegalArgumentException invalid) { return Result.failure(Code.INVALID_REQUEST, null, invalid.getMessage()); }
        KOMEConflictRecord previous = records.get(normalized);
        Code guard = guard(previous, expected, context);
        if (guard != Code.SUCCESS) return Result.failure(guard, previous, "Mutation precondition failed.");
        if (previous == null) return Result.failure(Code.NOT_FOUND, null, "Conflict is absent.");
        if (!previous.isActive()) return Result.failure(Code.CONFLICT_ENDED, previous, "Ended conflict is immutable.");
        if (previous.getRevision() == Long.MAX_VALUE) return Result.failure(Code.REVISION_EXHAUSTED, previous, "Revision exhausted.");
        Draft draft = new Draft(previous);
        try {
            Code outcome = change.apply(draft);
            if (outcome == Code.NO_CHANGE) return new Result(Code.NO_CHANGE, previous, previous, "Already at this checkpoint.");
            if (outcome != Code.SUCCESS) return Result.failure(outcome, previous, "Rejected " + operation + ": " + outcome);
            draft.revision = previous.getRevision() + 1L;
            draft.lastTransition = new LastTransition(operation, previous.getState(), draft.state, draft.revision, context, subject);
            KOMEConflictRecord record = new KOMEConflictRecord(draft);
            records.put(normalized, record);
            return Result.success(previous, record);
        } catch (IllegalArgumentException invalid) {
            return Result.failure(Code.INVALID_REQUEST, previous, invalid.getMessage());
        }
    }

    private static Code guard(KOMEConflictRecord previous, ExpectedConflict expected, Context context) {
        if (expected.isAbsent()) {
            if (previous != null) return previous.isActive() ? Code.ACTIVE_CONFLICT_EXISTS : Code.STALE_CONFLICT_ID;
            return Code.SUCCESS;
        }
        if (previous == null) return Code.NOT_FOUND;
        if (!expected.conflictId.equals(previous.getConflictId())) return Code.STALE_CONFLICT_ID;
        if (expected.revision != previous.getRevision()) return Code.STALE_REVISION;
        if (context.timestampMillis < previous.getLastTransition().timestampMillis) return Code.TIME_REGRESSION;
        return Code.SUCCESS;
    }

    private boolean committedElsewhere(String detachmentId, String tileId) {
        for (KOMEConflictRecord record : records.values())
            if (record.isActive() && !record.getTileId().equals(tileId) && record.getCommitments().containsKey(detachmentId)) return true;
        return false;
    }

    private static boolean timerMatches(TimerSlot timer, CombatEpisode episode, Context context) {
        return timer.status == TimerStatus.NOT_STARTED || timer.episodeId.equals(episode.episodeId)
            && timer.checkpointAtMillis >= episode.startedAtMillis && timer.checkpointAtMillis <= context.timestampMillis;
    }

    /** A checkpoint cannot silently restart the same episode's duration/elapsed snapshot. */
    private static boolean timerAdvances(TimerSlot previous, TimerSlot next) {
        return previous.status == TimerStatus.NOT_STARTED || next.status != TimerStatus.NOT_STARTED
            && next.durationSnapshotMillis == previous.durationSnapshotMillis
            && next.elapsedMillis >= previous.elapsedMillis && next.checkpointAtMillis >= previous.checkpointAtMillis;
    }

    private interface Change { Code apply(Draft draft); }

    public static final class Result {
        public final Code code;
        public final KOMEConflictRecord previous;
        public final KOMEConflictRecord record;
        public final String reason;

        private Result(Code code, KOMEConflictRecord previous, KOMEConflictRecord record, String reason) {
            this.code = code; this.previous = previous; this.record = record; this.reason = reason;
        }
        public boolean isSuccess() { return code == Code.SUCCESS || code == Code.NO_CHANGE; }
        private static Result success(KOMEConflictRecord previous, KOMEConflictRecord record) {
            return new Result(Code.SUCCESS, previous, record, "");
        }
        private static Result failure(Code code, KOMEConflictRecord record, String reason) {
            return new Result(code, record, record, reason);
        }
    }
}
