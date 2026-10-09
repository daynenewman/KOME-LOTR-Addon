package kome.common.data;

import java.util.*;
import static kome.common.data.KOMEOrdinaryBattleRecoveryRegistry.*;

/** Detached WAL payloads only. Constructing these records never changes gameplay authority. */
public abstract class KOMERecoveryJournalRecord {
    public enum Type { EPISODE_CONTEXT, PREPARE, TERMINAL, LINEAGE }
    public final Type type;
    private KOMERecoveryJournalRecord(Type type) { this.type = type; }
    abstract String identity();

    public static final class EpisodeContext extends KOMERecoveryJournalRecord {
        public final Episode episode;
        public final Map<UUID, Target> targets;
        public final long nextConflictSequence, conflictRevision, episodeCheckpoint;
        public final float recoveryAmount;
        public EpisodeContext(Episode episode, Map<UUID, Target> targets, long nextConflictSequence,
                long conflictRevision, long episodeCheckpoint, float recoveryAmount) {
            super(Type.EPISODE_CONTEXT);
            this.episode = Objects.requireNonNull(episode);
            this.targets = Collections.unmodifiableMap(new TreeMap<UUID, Target>(targets));
            this.nextConflictSequence = nextConflictSequence;
            this.conflictRevision = conflictRevision; this.episodeCheckpoint = episodeCheckpoint;
            this.recoveryAmount = recoveryAmount;
            KOMEOrdinaryBattleRecoveryRegistry.restore(this.targets, Collections.singletonMap(episode.episodeId, episode));
            require(episode.endedAt != null, "Context must describe a resolved episode");
            require(nextConflictSequence > KOMEConflictIdAllocator.sequenceOf(episode.conflictId), "Unissued conflict watermark");
            require(conflictRevision > 0 && episodeCheckpoint < Long.MAX_VALUE && episodeCheckpoint >= episodeSequence(episode.episodeId), "Invalid conflict checkpoint");
            amount(recoveryAmount);
            require(episode.intent == null || episode.intent.expectedRevision <= conflictRevision
                && episode.intent.recoveryAmount == recoveryAmount, "Contradictory context intent");
            for (Map.Entry<UUID, Target> entry : this.targets.entrySet()) {
                require(entry.getKey().equals(entry.getValue().id), "Target map key mismatch");
                uuid(entry.getKey()); uuid(entry.getValue().physicalId);
                for (UUID alias : entry.getValue().aliases) uuid(alias);
            }
            for (Map.Entry<UUID, Participant> entry : episode.participants.entrySet()) {
                require(entry.getKey().equals(entry.getValue().id), "Participant map key mismatch"); uuid(entry.getKey());
            }
            for (Map.Entry<String, Grant> entry : episode.grants.entrySet()) {
                Grant g = entry.getValue();
                require(entry.getKey().equals(g.key()) && g.state == GrantState.PENDING && g.transition == null
                    && g.amount == recoveryAmount, "Context must contain only unprepared pending obligations");
                require(!this.targets.get(g.targetId).terminal && !terminal(episode.participants.get(g.participantId).disposition),
                    "Terminal context grant");
            }
        }
        public String intentFingerprint() { return KOMERecoveryJournalCodec.intentFingerprint(episode.intent); }
        @Override String identity() { return "CONTEXT:" + episode.episodeId; }
    }

    /** Durable context reference; hashes are immutable, canonical lowercase SHA-256 hex. */
    public static final class Binding {
        public final UUID journalId, targetId;
        public final long contextSequence;
        public final String contextHash, conflictId, episodeId;
        public final Component component;
        public Binding(UUID journalId, long contextSequence, String contextHash, String conflictId,
                String episodeId, UUID targetId, Component component) {
            this.journalId = uuid(journalId); this.targetId = uuid(targetId);
            require(contextSequence > 0 && contextSequence < Long.MAX_VALUE, "Invalid context sequence");
            this.contextSequence = contextSequence; this.contextHash = KOMERecoveryJournalCodec.hash(contextHash);
            KOMEConflictIdAllocator.requireIdentity(conflictId);
            require(episodeId != null && episodeId.matches(conflictId + ":E[1-9][0-9]*"), "Invalid episode identity");
            episodeSequence(episodeId);
            this.conflictId = conflictId; this.episodeId = episodeId; this.component = Objects.requireNonNull(component);
        }
    }
    public static final class Fence {
        public final UUID journalId, transitionId;
        public final long prepareSequence;
        public Fence(UUID journalId, long prepareSequence, UUID transitionId) {
            this.journalId = uuid(journalId); this.transitionId = uuid(transitionId);
            require(prepareSequence > 0 && prepareSequence < Long.MAX_VALUE, "Invalid fence sequence");
            this.prepareSequence = prepareSequence;
        }
    }
    public static final class Prepare extends KOMERecoveryJournalRecord {
        public final Binding binding;
        public final UUID participantId, physicalAlias, sourceTransitionId, recoveryTransitionId;
        public final long lineageSequence, sourceRevision, recoveryRevision;
        public final float sourceHp, targetHp, recoveryAmount, maxHp;
        public final Fence inheritedFence;
        public final String intentFingerprint;
        public Prepare(Binding binding, UUID participantId, UUID physicalAlias, long lineageSequence,
                long sourceRevision, UUID sourceTransitionId, float sourceHp, Fence inheritedFence,
                long recoveryRevision, UUID recoveryTransitionId, float targetHp, float recoveryAmount,
                float maxHp, String intentFingerprint) {
            super(Type.PREPARE); this.binding = Objects.requireNonNull(binding);
            this.participantId = uuid(participantId); this.physicalAlias = uuid(physicalAlias);
            this.sourceTransitionId = uuid(sourceTransitionId); this.recoveryTransitionId = uuid(recoveryTransitionId);
            require(lineageSequence >= 0 && lineageSequence < Long.MAX_VALUE, "Invalid lineage reference");
            require(sourceRevision >= 0 && sourceRevision < Long.MAX_VALUE - 1 && recoveryRevision == sourceRevision + 1,
                "Invalid/overflowed recovery revision reservation");
            require(!sourceTransitionId.equals(recoveryTransitionId), "Recovery must have a distinct transition identity");
            amount(sourceHp); amount(targetHp); amount(recoveryAmount); amount(maxHp);
            require(sourceHp > 0 && maxHp > 0 && sourceHp <= maxHp
                && targetHp == Math.min(maxHp, sourceHp + recoveryAmount), "Invalid prepared health calculation");
            this.lineageSequence = lineageSequence; this.sourceRevision = sourceRevision;
            this.recoveryRevision = recoveryRevision; this.sourceHp = sourceHp; this.targetHp = targetHp;
            this.recoveryAmount = recoveryAmount; this.maxHp = maxHp; this.inheritedFence = inheritedFence;
            this.intentFingerprint = KOMERecoveryJournalCodec.hash(intentFingerprint);
        }
        public String grantIdentity() { return binding.episodeId + ":" + binding.component + ":" + binding.targetId; }
        @Override String identity() { return "PREPARE:" + grantIdentity(); }
    }
    public static final class Terminal extends KOMERecoveryJournalRecord {
        public final Binding binding;
        public final UUID physicalAlias, transitionId;
        public final long healthRevision, observedAt;
        public final Disposition disposition;
        public final String reason;
        public final Fence fence;
        public Terminal(Binding binding, UUID physicalAlias, long healthRevision, UUID transitionId,
                Disposition disposition, long observedAt, String reason, Fence fence) {
            super(Type.TERMINAL); this.binding = Objects.requireNonNull(binding);
            this.physicalAlias = uuid(physicalAlias); this.transitionId = uuid(transitionId);
            require(healthRevision >= 0 && observedAt >= 0, "Invalid terminal revision/time");
            require(terminal(disposition), "Authoritative terminal disposition required");
            this.healthRevision = healthRevision; this.observedAt = observedAt; this.disposition = disposition;
            this.reason = text(reason); this.fence = fence;
        }
        @Override String identity() { return "TERMINAL:" + binding.targetId; }
    }
    /** Call only after a verified replacement has reached an irrevocable activation boundary. */
    public static final class Lineage extends KOMERecoveryJournalRecord {
        public final Binding binding;
        public final UUID oldAlias, newAlias, activationId, healthTransitionId;
        public final long previousLineageSequence, healthRevision;
        public final Fence fence;
        public Lineage(Binding binding, UUID oldAlias, UUID newAlias, UUID activationId,
                long previousLineageSequence, long healthRevision, UUID healthTransitionId, Fence fence) {
            super(Type.LINEAGE); this.binding = Objects.requireNonNull(binding);
            this.oldAlias = uuid(oldAlias); this.newAlias = uuid(newAlias); this.activationId = uuid(activationId);
            this.healthTransitionId = uuid(healthTransitionId);
            require(!oldAlias.equals(newAlias), "Replacement alias must change");
            require(previousLineageSequence >= 0 && previousLineageSequence < Long.MAX_VALUE && healthRevision >= 0,
                "Invalid lineage revision/reference");
            this.previousLineageSequence = previousLineageSequence; this.healthRevision = healthRevision; this.fence = fence;
        }
        @Override String identity() { return "LINEAGE:" + activationId; }
    }
    static UUID uuid(UUID value) {
        require(value != null && (value.getMostSignificantBits() != 0 || value.getLeastSignificantBits() != 0), "Missing/nil UUID");
        return value;
    }
    static long episodeSequence(String episode) {
        long value = Long.parseLong(episode.substring(episode.lastIndexOf('E') + 1));
        require(value > 0 && value < Long.MAX_VALUE, "Invalid/exhausted episode sequence"); return value;
    }
}
