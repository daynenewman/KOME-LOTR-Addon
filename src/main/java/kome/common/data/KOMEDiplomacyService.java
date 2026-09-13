package kome.common.data;

import lotr.common.fac.LOTRFaction;
import lotr.common.fac.LOTRFactionRelations;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Authoritative Neutral / Friends / Allies diplomacy service.
 *
 * Canonical KOME diplomacy is authoritative. LOTR relations are a projection
 * target only and are never read back to determine KOME diplomacy.
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
        if (data == null || a.length() == 0 || b.length() == 0 || a.equals(b)) {
            return KOMEDiplomacyRelation.NEUTRAL;
        }

        KOMEDiplomacyRecord record =
            data.canonicalDiplomacyRecords.get(KOMEDiplomacyRecord.pairKey(a, b));

        return record == null
            ? KOMEDiplomacyRelation.NEUTRAL
            : record.relation;
    }

    public static boolean isNeutral(
            KOMEWorldData data, String first, String second) {
        return getRelation(data, first, second) == KOMEDiplomacyRelation.NEUTRAL;
    }

    public static boolean areFriends(
            KOMEWorldData data, String first, String second) {
        return getRelation(data, first, second) == KOMEDiplomacyRelation.FRIENDS;
    }

    public static boolean areAllies(
            KOMEWorldData data, String first, String second) {
        return getRelation(data, first, second) == KOMEDiplomacyRelation.ALLIES;
    }

    public static boolean relationAtLeast(
            KOMEWorldData data,
            String first,
            String second,
            KOMEDiplomacyRelation required) {
        return required != null
            && getRelation(data, first, second).rank() >= required.rank();
    }

    public static Map<String, KOMEDiplomacyRecord> records(KOMEWorldData data) {
        if (data == null) {
            return Collections.emptyMap();
        }

        return Collections.unmodifiableMap(
            new TreeMap<String, KOMEDiplomacyRecord>(
                data.canonicalDiplomacyRecords));
    }

    public static Result requestIncrease(
            KOMEWorldData data,
            String from,
            String to,
            KOMEDiplomacyRelation target,
            UUID actor,
            long now) {

        String requester = KOMEAlliance.normalizeFactionKey(from);
        String receiver = KOMEAlliance.normalizeFactionKey(to);

        if (data == null
                || requester.length() == 0
                || receiver.length() == 0
                || requester.equals(receiver)
                || target == null
                || target == KOMEDiplomacyRelation.NEUTRAL) {
            return Result.denied("Invalid diplomacy request");
        }

        if (!KOMERulerAuthorization.canActAsRuler(data, requester, actor)) {
            return Result.denied("Requester must be the recognized King");
        }

        if (!data.hasFactionKing(receiver)) {
            return Result.denied("Receiving faction has no recognized King");
        }

        String key = KOMEDiplomacyRecord.pairKey(requester, receiver);
        KOMEDiplomacyRecord record =
            data.canonicalDiplomacyRecords.get(key);

        KOMEDiplomacyRelation current =
            record == null
                ? KOMEDiplomacyRelation.NEUTRAL
                : record.relation;

        if (target.rank() <= current.rank()) {
            return Result.denied(
                "Requested relation must increase current relation");
        }

        if (record != null && record.pendingTarget != null) {
            return Result.denied(
                "A diplomacy request is already pending");
        }

        if (record == null) {
            record = new KOMEDiplomacyRecord(requester, receiver);
            data.canonicalDiplomacyRecords.put(record.key(), record);
        }

        record.pendingTarget = target;
        record.requestingFaction = requester;
        record.receivingFaction = receiver;
        record.requesterIdentity =
            actor == null ? "" : actor.toString();
        record.requestedAt = now;
        record.updatedAt = now;
        record.lastUpdatedBy =
            actor == null ? "" : actor.toString();

        data.markDirty();
        KOMEAuditService.record(data, now, "DIPLOMACY", "REQUEST", actor == null ? "" : actor.toString(),
            record.key(), "Diplomacy relation request created", target.key);
        return Result.ok(record);
    }

    public static Result acceptPendingIncrease(
            KOMEWorldData data,
            String receiver,
            String other,
            UUID actor,
            long now) {

        if (data == null) {
            return Result.denied("World data is required");
        }

        KOMEDiplomacyRecord record =
            data.canonicalDiplomacyRecords.get(
                KOMEDiplomacyRecord.pairKey(receiver, other));

        if (record == null || record.pendingTarget == null) {
            return Result.denied("No pending diplomacy request");
        }

        String receivingFaction =
            KOMEAlliance.normalizeFactionKey(receiver);

        if (!receivingFaction.equals(record.receivingFaction)
                || !KOMERulerAuthorization.canActAsRuler(data, receivingFaction, actor)) {
            return Result.denied(
                "Only the receiving faction King may accept");
        }

        record.relation = record.pendingTarget;
        clearPending(record);
        record.updatedAt = now;
        record.lastUpdatedBy =
            actor == null ? "" : actor.toString();

        data.markDirty();
        KOMEAuditService.record(data, now, "DIPLOMACY", "ACCEPT", actor == null ? "" : actor.toString(),
            record.key(), "Diplomacy relation changed", record.relation.key);
        KOMEMovementAccessService.revalidateAll(data, now);
        return Result.ok(record);
    }

    public static Result cancelPendingRequest(
            KOMEWorldData data,
            String from,
            String to,
            UUID actor,
            boolean admin) {

        if (data == null) {
            return Result.denied("World data is required");
        }

        String key = KOMEDiplomacyRecord.pairKey(from, to);
        KOMEDiplomacyRecord record =
            data.canonicalDiplomacyRecords.get(key);

        if (record == null || record.pendingTarget == null) {
            return Result.denied("No pending diplomacy request");
        }

        String requester =
            KOMEAlliance.normalizeFactionKey(record.requestingFaction);

        if (!admin && !KOMERulerAuthorization.canActAsRuler(data, requester, actor)) {
            return Result.denied(
                "Only the requesting faction King may cancel");
        }

        clearPending(record);
        record.updatedAt = System.currentTimeMillis();
        record.lastUpdatedBy =
            actor == null ? "" : actor.toString();

        if (record.relation == KOMEDiplomacyRelation.NEUTRAL) {
            data.canonicalDiplomacyRecords.remove(key);
        }

        data.markDirty();
        return Result.ok(record);
    }

    private static void clearPending(KOMEDiplomacyRecord record) {
        record.pendingTarget = null;
        record.requestingFaction = "";
        record.receivingFaction = "";
        record.requesterIdentity = "";
        record.requestedAt = 0L;
    }

    public static void projectLotrRelation(
            KOMEWorldData data, String first, String second) {
        if (data == null) {
            return;
        }

        String a = KOMEAlliance.normalizeFactionKey(first);
        String b = KOMEAlliance.normalizeFactionKey(second);

        if (a.length() == 0 || b.length() == 0 || a.equals(b)) {
            return;
        }

        LOTRFaction factionA = KOMEAlliance.findLotrFaction(a);
        LOTRFaction factionB = KOMEAlliance.findLotrFaction(b);

        if (factionA == null || factionB == null || factionA == factionB) {
            return;
        }

        KOMEDiplomacyRelation relation = getRelation(data, a, b);

        LOTRFactionRelations.Relation projected =
            relation == KOMEDiplomacyRelation.ALLIES
                ? LOTRFactionRelations.Relation.ALLY
                : relation == KOMEDiplomacyRelation.FRIENDS
                    ? LOTRFactionRelations.Relation.FRIEND
                    : LOTRFactionRelations.Relation.NEUTRAL;

        LOTRFactionRelations.overrideRelations(
            factionA, factionB, projected);
    }

    public static void reapplyLotrProjection(KOMEWorldData data) {
        if (data == null) {
            return;
        }

        for (KOMEDiplomacyRecord record : records(data).values()) {
            if (record != null
                    && record.relation != KOMEDiplomacyRelation.NEUTRAL) {
                projectLotrRelation(
                    data, record.factionA, record.factionB);
            }
        }
    }

    public static final class Result {
        public final boolean accepted;
        public final String reason;
        public final KOMEDiplomacyRecord record;

        private Result(
                boolean accepted,
                String reason,
                KOMEDiplomacyRecord record) {
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
