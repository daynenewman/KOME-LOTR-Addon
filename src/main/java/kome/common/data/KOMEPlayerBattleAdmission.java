package kome.common.data;

import java.util.UUID;

/** Pure lifecycle policy, not proof of strategic support, deployment, geometry, or physical delivery. */
public final class KOMEPlayerBattleAdmission {
    private KOMEPlayerBattleAdmission() {}

    public enum Decision {
        ALLOW_EXISTING_TACTICAL_PARTICIPATION,
        BLOCK_COOLDOWN,
        REENTRY_AVAILABLE,
        BLOCK_NO_OWN_FACTION_SUPPORT,
        BLOCK_FORMAL_RETREAT,
        BLOCK_CONFLICT_ENDED,
        REENTRY_DELIVERY_PENDING
    }

    /**
     * Immutable facts selected from one coherent, detached registry snapshot. Support and exact
     * conflict activity are assertions supplied by the authoritative caller, never calculated here.
     */
    public static final class Facts {
        public final KOMEPlayerBattleLifecycle.Key key;
        public final KOMEPlayerBattleLifecycle lifecycle;
        public final boolean conflictTerminal;
        public final boolean exactConflictActive;
        public final boolean ownFactionCommittedSupport;
        public final long nowMillis;
        public final long pendingDeliveryGeneration;

        public Facts(UUID playerId, String conflictId, KOMEPlayerBattleLifecycleRegistry.Snapshot snapshot,
                boolean exactConflictActive, boolean ownFactionCommittedSupport, long nowMillis) {
            this(playerId, conflictId, snapshot, exactConflictActive, ownFactionCommittedSupport, nowMillis, 0);
        }

        /**
         * Zero means no delivery intent. A positive generation asserts a pending delivery for this
         * exact key and interruption. Future callers must obtain it from authoritative delivery
         * state; this projection neither creates nor durably records an intent. Stale intents fail
         * closed instead of offering a duplicate action or admitting a player.
         */
        public Facts(UUID playerId, String conflictId, KOMEPlayerBattleLifecycleRegistry.Snapshot snapshot,
                boolean exactConflictActive, boolean ownFactionCommittedSupport, long nowMillis,
                long pendingDeliveryGeneration) {
            KOMEPlayerBattleLifecycle.require(snapshot != null, "Lifecycle snapshot is required.");
            KOMEPlayerBattleLifecycle.require(nowMillis >= 0, "Query time cannot be negative.");
            KOMEPlayerBattleLifecycle.require(pendingDeliveryGeneration >= 0,
                "Delivery generation cannot be negative.");
            key = new KOMEPlayerBattleLifecycle.Key(playerId, conflictId);
            lifecycle = snapshot.records.get(key);
            conflictTerminal = snapshot.terminalConflicts.containsKey(key.conflictId);
            KOMEPlayerBattleLifecycle.require(pendingDeliveryGeneration == 0 || (lifecycle != null
                && lifecycle.interruptionActive && lifecycle.generation == pendingDeliveryGeneration),
                "Pending delivery must match the active interruption generation.");
            this.exactConflictActive = exactConflictActive;
            this.ownFactionCommittedSupport = ownFactionCommittedSupport;
            this.nowMillis = nowMillis;
            this.pendingDeliveryGeneration = pendingDeliveryGeneration;
        }
    }

    public static final class Result {
        public final Decision decision;
        /** Present only for active cooldown/re-entry decisions; always the persisted deadline. */
        public final Long eligibleAtMillis;
        public final long remainingMillis;

        private Result(Decision decision, Long eligibleAtMillis, long remainingMillis) {
            this.decision = decision;
            this.eligibleAtMillis = eligibleAtMillis;
            this.remainingMillis = remainingMillis;
        }

        /** Lifecycle permission only. The caller still owns every other tactical admission gate. */
        public boolean isTacticallyAdmitted() {
            return decision == Decision.ALLOW_EXISTING_TACTICAL_PARTICIPATION;
        }

        /** Offers only re-entry, not an ordinary first Join, and never starts physical delivery. */
        public boolean shouldOfferReentry() { return decision == Decision.REENTRY_AVAILABLE; }

        /** Optional UI projection; contains no receipt IDs, conflict IDs, or generation values. */
        public String reasonText() {
            switch (decision) {
                case BLOCK_COOLDOWN:
                    // Ceiling without adding 999 to a potentially maximal timestamp difference.
                    long seconds = remainingMillis / 1000 + (remainingMillis % 1000 == 0 ? 0 : 1);
                    return "You can re-enter this battle in " + seconds + (seconds == 1 ? " second." : " seconds.");
                case REENTRY_AVAILABLE:
                    return "You can re-enter this battle beside an eligible supported friendly group.";
                case BLOCK_NO_OWN_FACTION_SUPPORT:
                    return "Your faction has no eligible committed Campaign forces remaining in this battle.";
                case BLOCK_FORMAL_RETREAT:
                    return "You formally retreated from this battle and cannot rejoin it.";
                case BLOCK_CONFLICT_ENDED:
                    return "This battle has ended or been replaced.";
                case REENTRY_DELIVERY_PENDING:
                    return "Your re-entry is being completed safely.";
                default:
                    return "";
            }
        }
    }

    /** No mutation, clock access, live-world queries, receipt operations, or terminal cleanup. */
    public static Result evaluate(Facts facts) {
        KOMEPlayerBattleLifecycle.require(facts != null, "Admission facts are required.");
        if (!facts.exactConflictActive || facts.conflictTerminal)
            return result(Decision.BLOCK_CONFLICT_ENDED);
        KOMEPlayerBattleLifecycle lifecycle = facts.lifecycle;
        if (lifecycle != null && lifecycle.isFormalRetreatBarred())
            return result(Decision.BLOCK_FORMAL_RETREAT);
        if (lifecycle == null || !lifecycle.interruptionActive)
            return result(Decision.ALLOW_EXISTING_TACTICAL_PARTICIPATION);

        long deadline = lifecycle.eligibleAtMillis;
        // Both inputs are nonnegative, so subtraction in this branch cannot overflow.
        if (facts.nowMillis < deadline)
            return new Result(Decision.BLOCK_COOLDOWN, deadline, deadline - facts.nowMillis);
        if (facts.pendingDeliveryGeneration != 0)
            return new Result(Decision.REENTRY_DELIVERY_PENDING, deadline, 0);
        return new Result(facts.ownFactionCommittedSupport ? Decision.REENTRY_AVAILABLE
            : Decision.BLOCK_NO_OWN_FACTION_SUPPORT, deadline, 0);
    }

    private static Result result(Decision decision) { return new Result(decision, null, 0); }
}
