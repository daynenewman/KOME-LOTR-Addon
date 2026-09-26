package kome.common.data;

import lotr.common.fac.LOTRFaction;
import lotr.common.fac.LOTRFactionRelations;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Political workflow around LOTR's authoritative bilateral faction relations.
 *
 * KOME records contain pending consent and audit metadata. They are never an
 * alternate effective gameplay relation and are never projected during load.
 */
public final class KOMEDiplomacyService {
    private KOMEDiplomacyService() {
    }

    public static KOMEDiplomacyRelation getRelation(
            KOMEWorldData data, String first, String second) {
        String a = KOMEAlliance.normalizeFactionKey(first);
        String b = KOMEAlliance.normalizeFactionKey(second);
        if (a.length() > 0 && a.equals(b)) {
            return KOMEDiplomacyRelation.ALLIES;
        }
        return KOMEDiplomacyRelation.fromLotrRelation(
            KOMEAllianceAuthority.getCurrentRelation(a, b));
    }

    public static boolean isNeutral(KOMEWorldData data, String first, String second) {
        return getRelation(data, first, second) == KOMEDiplomacyRelation.NEUTRAL;
    }

    public static boolean areFriends(KOMEWorldData data, String first, String second) {
        return getRelation(data, first, second) == KOMEDiplomacyRelation.FRIENDS;
    }

    public static boolean areAllies(KOMEWorldData data, String first, String second) {
        return getRelation(data, first, second) == KOMEDiplomacyRelation.ALLIES;
    }

    public static boolean relationAtLeast(KOMEWorldData data, String first, String second,
            KOMEDiplomacyRelation required) {
        return required != null && getRelation(data, first, second).rank() >= required.rank();
    }

    public static Map<String, KOMEDiplomacyRecord> records(KOMEWorldData data) {
        if (data == null) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(
            new TreeMap<String, KOMEDiplomacyRecord>(data.canonicalDiplomacyRecords));
    }

    public static Result requestIncrease(KOMEWorldData data, String from, String to,
            KOMEDiplomacyRelation target, UUID actor, long now) {
        String requester = KOMEAlliance.normalizeFactionKey(from);
        String receiver = KOMEAlliance.normalizeFactionKey(to);
        Result validation = validatePair(data, requester, receiver, target);
        if (validation != null) {
            return validation;
        }
        if (!KOMERulerAuthorization.canActAsRuler(data, requester, actor)) {
            return Result.denied("Requester must be the recognized King");
        }
        if (!data.hasFactionKing(receiver)) {
            return Result.denied("Receiving faction has no recognized King");
        }

        KOMEDiplomacyRelation current = getRelation(data, requester, receiver);
        if (target.rank() == current.rank()) {
            return Result.denied("Requested relation is already " + current.displayName);
        }
        if (target.rank() < current.rank()) {
            return Result.denied("A worse relation is immediate; use /alliance break or revoke");
        }

        String key = KOMEDiplomacyRecord.pairKey(requester, receiver);
        KOMEDiplomacyRecord record = data.canonicalDiplomacyRecords.get(key);
        if (record != null && record.pendingTarget != null) {
            if (record.pendingTarget.rank() <= current.rank()) {
                discardStalePending(data, record, current, actor, now,
                    "External LOTR relation change made the pending request obsolete");
            } else {
                return Result.denied("A diplomacy request is already pending");
            }
        }
        if (record == null) {
            record = new KOMEDiplomacyRecord(requester, receiver);
            data.canonicalDiplomacyRecords.put(record.key(), record);
        }
        record.relation = current;
        record.pendingTarget = target;
        record.requestingFaction = requester;
        record.receivingFaction = receiver;
        record.requesterIdentity = actor == null ? "" : actor.toString();
        record.requestedAt = now;
        record.updatedAt = now;
        record.lastUpdatedBy = actor == null ? "" : actor.toString();

        data.markDirty();
        KOMEAuditService.record(data, now, "DIPLOMACY", "REQUEST",
            actor == null ? "" : actor.toString(), record.key(),
            "Diplomacy relation request created", current.key + " -> " + target.key);
        return Result.ok(record);
    }

    public static Result acceptPendingIncrease(KOMEWorldData data, String receiver, String other,
            UUID actor, long now) {
        if (data == null) {
            return Result.denied("World data is required");
        }
        String receivingFaction = KOMEAlliance.normalizeFactionKey(receiver);
        String otherFaction = KOMEAlliance.normalizeFactionKey(other);
        if (!KOMEAllianceAuthority.hasAuthoritativePair(receivingFaction, otherFaction)) {
            return Result.denied("Diplomacy requires two valid LOTR factions");
        }
        KOMEDiplomacyRecord record = data.canonicalDiplomacyRecords.get(
            KOMEDiplomacyRecord.pairKey(receivingFaction, otherFaction));
        if (record == null || record.pendingTarget == null) {
            return Result.denied("No pending diplomacy request");
        }
        if (!receivingFaction.equals(record.receivingFaction)
                || !KOMERulerAuthorization.canActAsRuler(data, receivingFaction, actor)) {
            return Result.denied("Only the receiving faction King may accept");
        }

        KOMEDiplomacyRelation current = getRelation(data, receivingFaction, otherFaction);
        KOMEDiplomacyRelation target = record.pendingTarget;
        if (target.rank() <= current.rank()) {
            discardStalePending(data, record, current, actor, now,
                "External LOTR relation change invalidated the pending request");
            return Result.denied(target == current
                ? "The requested relation is already active"
                : "The pending request no longer improves the current relation");
        }

        String requester = record.requestingFaction;
        setAuthoritativeRelation(record.factionA, record.factionB, target);
        record.relation = target;
        clearPending(record);
        record.updatedAt = now;
        record.lastUpdatedBy = actor == null ? "" : actor.toString();

        KOMEWarService.reconcileDirectPeace(data, requester, receivingFaction, now,
            "Accepted diplomatic improvement to " + target.displayName);
        KOMEWarService.reconcileDiplomacyLoss(data, record.factionA, record.factionB, target, now);
        data.markDirty();
        KOMEAuditService.record(data, now, "DIPLOMACY", "ACCEPT",
            actor == null ? "" : actor.toString(), record.key(),
            "LOTR faction relation changed", current.key + " -> " + target.key);
        revalidateConsequences(data, now);
        return Result.ok(record);
    }

    /** Immediate unilateral worsening by either faction's recognized ruler. */
    public static Result worsenRelation(KOMEWorldData data, String actingFaction, String otherFaction,
            KOMEDiplomacyRelation target, UUID actor, long now) {
        String acting = KOMEAlliance.normalizeFactionKey(actingFaction);
        String other = KOMEAlliance.normalizeFactionKey(otherFaction);
        Result validation = validatePair(data, acting, other, target);
        if (validation != null) {
            return validation;
        }
        if (!KOMERulerAuthorization.canActAsRuler(data, acting, actor)) {
            return Result.denied("Only the acting faction's recognized King may worsen relations");
        }
        KOMEDiplomacyRelation current = getRelation(data, acting, other);
        if (target.rank() == current.rank()) {
            return Result.denied("Relation is already " + current.displayName);
        }
        if (target.rank() > current.rank()) {
            return Result.denied(
                "A friendlier relation requires a request and the other faction King's acceptance");
        }

        setAuthoritativeRelation(acting, other, target);
        String key = KOMEDiplomacyRecord.pairKey(acting, other);
        KOMEDiplomacyRecord record = data.canonicalDiplomacyRecords.get(key);
        if (record == null) {
            record = new KOMEDiplomacyRecord(acting, other);
            data.canonicalDiplomacyRecords.put(record.key(), record);
        }
        record.relation = target;
        record.updatedAt = now;
        record.lastUpdatedBy = actor == null ? "" : actor.toString();

        KOMEWarService.reconcileDiplomacyLoss(data, acting, other, target, now);
        data.markDirty();
        KOMEAuditService.record(data, now, "DIPLOMACY", "WORSEN",
            actor == null ? "" : actor.toString(), record.key(),
            "LOTR faction relation worsened unilaterally", current.key + " -> " + target.key);
        revalidateConsequences(data, now);
        return Result.ok(record);
    }

    /** War is an immediate unilateral transition to Mortal Enemy; it does not create a war itself. */
    static boolean applyWarDeclaration(KOMEWorldData data, String attacker, String defender,
            String actor, long now) {
        if (!KOMEAllianceAuthority.hasAuthoritativePair(attacker, defender)) {
            return false;
        }
        KOMEDiplomacyRelation current = getRelation(data, attacker, defender);
        setAuthoritativeRelation(attacker, defender, KOMEDiplomacyRelation.MORTAL_ENEMIES);
        KOMEDiplomacyRecord record = data == null ? null : data.canonicalDiplomacyRecords.get(
            KOMEDiplomacyRecord.pairKey(attacker, defender));
        if (record != null) {
            record.relation = KOMEDiplomacyRelation.MORTAL_ENEMIES;
            record.updatedAt = now;
            record.lastUpdatedBy = actor == null ? "" : actor;
        }
        if (data != null) {
            data.markDirty();
            KOMEAuditService.record(data, now, "DIPLOMACY", "WAR",
                actor == null ? "" : actor, KOMEDiplomacyRecord.pairKey(attacker, defender),
                "War declaration set LOTR relation to Mortal Enemy",
                current.key + " -> " + KOMEDiplomacyRelation.MORTAL_ENEMIES.key);
        }
        return true;
    }

    public static Result cancelPendingRequest(KOMEWorldData data, String from, String to,
            UUID actor, boolean admin) {
        if (data == null) {
            return Result.denied("World data is required");
        }
        String key = KOMEDiplomacyRecord.pairKey(from, to);
        KOMEDiplomacyRecord record = data.canonicalDiplomacyRecords.get(key);
        if (record == null || record.pendingTarget == null) {
            return Result.denied("No pending diplomacy request");
        }
        String requester = KOMEAlliance.normalizeFactionKey(record.requestingFaction);
        if (!admin && !KOMERulerAuthorization.canActAsRuler(data, requester, actor)) {
            return Result.denied("Only the requesting faction King may cancel");
        }
        clearPending(record);
        record.relation = getRelation(data, record.factionA, record.factionB);
        record.updatedAt = System.currentTimeMillis();
        record.lastUpdatedBy = actor == null ? "" : actor.toString();
        data.markDirty();
        return Result.ok(record);
    }

    private static Result validatePair(KOMEWorldData data, String first, String second,
            KOMEDiplomacyRelation target) {
        if (data == null) {
            return Result.denied("World data is required");
        }
        if (first.length() == 0 || second.length() == 0 || first.equals(second)) {
            return Result.denied("Diplomacy requires two different factions");
        }
        if (target == null) {
            return Result.denied("A target relation is required");
        }
        if (!KOMEAllianceAuthority.hasAuthoritativePair(first, second)) {
            return Result.denied("Diplomacy requires two valid LOTR factions");
        }
        return null;
    }

    private static void setAuthoritativeRelation(String first, String second,
            KOMEDiplomacyRelation relation) {
        LOTRFaction factionA = KOMEAlliance.findLotrFaction(first);
        LOTRFaction factionB = KOMEAlliance.findLotrFaction(second);
        if (factionA == null || factionB == null || factionA == factionB || relation == null) {
            throw new IllegalArgumentException(
                "Diplomacy requires two valid LOTR factions and a relation");
        }
        LOTRFactionRelations.overrideRelations(factionA, factionB, relation.toLotrRelation());
    }

    private static void clearPending(KOMEDiplomacyRecord record) {
        record.pendingTarget = null;
        record.requestingFaction = "";
        record.receivingFaction = "";
        record.requesterIdentity = "";
        record.requestedAt = 0L;
    }

    private static void discardStalePending(KOMEWorldData data,
            KOMEDiplomacyRecord record, KOMEDiplomacyRelation current,
            UUID actor, long now, String reason) {
        KOMEDiplomacyRelation staleTarget = record.pendingTarget;
        clearPending(record);
        record.relation = current;
        record.updatedAt = now;
        record.lastUpdatedBy = actor == null ? "" : actor.toString();
        data.markDirty();
        KOMEAuditService.record(data, now, "DIPLOMACY", "PENDING_INVALIDATED",
            actor == null ? "" : actor.toString(), record.key(), reason,
            (staleTarget == null ? "" : staleTarget.key)
                + " against current " + current.key);
    }

    private static void revalidateConsequences(KOMEWorldData data, long now) {
        KOMEMovementAccessService.revalidateAll(data, now);
        KOMEWarService.reconcileAutomaticMilitarySupport(
            data, now, "Diplomacy relation changed");
        KOMEWartimeStewardshipService.revalidateAll(
            data, now, "Diplomacy relation changed");
    }

    public static final class Result {
        public final boolean accepted;
        public final String reason;
        public final KOMEDiplomacyRecord record;

        private Result(boolean accepted, String reason, KOMEDiplomacyRecord record) {
            this.accepted = accepted;
            this.reason = reason == null ? "" : reason;
            this.record = record;
        }

        static Result ok(KOMEDiplomacyRecord record) {
            return new Result(true, "", record);
        }

        static Result denied(String reason) {
            return new Result(false, reason, null);
        }
    }
}
