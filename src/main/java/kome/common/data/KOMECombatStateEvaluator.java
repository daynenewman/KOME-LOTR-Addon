package kome.common.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import static kome.common.data.KOMEConflictContracts.*;

/**
 * Pure projection of canonical strategic commitments into the military factions currently
 * participating in a conflict. Entity/chunk/player observations are deliberately not inputs.
 */
public final class KOMECombatStateEvaluator {
    public enum Code { RESOLVED, DEFERRED_UNKNOWN_AUTHORITY, INVALID_CONFLICT }
    public enum Relation { HOSTILE, ALLIED, NON_ALLIED, UNKNOWN }

    public interface Authority {
        DetachmentResolution resolveDetachment(String detachmentId);
        EmergencyDefensePresence emergencyDefense(KOMEConflictRecord conflict);
        Relation relation(String firstFactionId, String secondFactionId);
    }

    public static final class EmergencyDefensePresence {
        public enum Status { NONE, PRESENT, UNKNOWN }
        public final Status status;
        public final String factionId;
        public final String reason;

        private EmergencyDefensePresence(Status status, String factionId, String reason) {
            this.status = status;
            this.factionId = factionId == null ? ""
                : KOMEAlliance.normalizeFactionKey(factionId);
            this.reason = reason == null ? "" : reason;
            if (status == Status.PRESENT && this.factionId.length() == 0)
                throw new IllegalArgumentException("Emergency Defense presence requires a faction.");
        }

        public static EmergencyDefensePresence none() {
            return new EmergencyDefensePresence(Status.NONE, "", "");
        }

        public static EmergencyDefensePresence present(String factionId) {
            return new EmergencyDefensePresence(Status.PRESENT, factionId, "");
        }

        public static EmergencyDefensePresence unknown(String reason) {
            return new EmergencyDefensePresence(Status.UNKNOWN, "", reason);
        }
    }

    public static final class MilitaryFaction {
        public final String factionId;
        public final List<String> campaignDetachmentIds;
        public final boolean emergencyDefensePresent;

        private MilitaryFaction(String factionId, TreeSet<String> detachments,
                boolean emergencyDefense) {
            this.factionId = factionId;
            campaignDetachmentIds = Collections.unmodifiableList(
                new ArrayList<String>(detachments));
            emergencyDefensePresent = emergencyDefense;
        }

        public boolean hasCampaignClaimAuthority() {
            return !campaignDetachmentIds.isEmpty();
        }
    }

    public static final class FactionPair {
        public final String firstFactionId;
        public final String secondFactionId;
        public final Relation relation;

        private FactionPair(String first, String second, Relation relation) {
            firstFactionId = first;
            secondFactionId = second;
            this.relation = relation;
        }
    }

    public static final class Result {
        public final Code code;
        public final Map<String, MilitaryFaction> factions;
        public final List<FactionPair> pairs;
        public final List<String> diagnostics;
        public final boolean hostilePairPresent;
        public final boolean unknownAuthorityPresent;

        private Result(Code code, Map<String, MilitaryFaction> factions,
                List<FactionPair> pairs, List<String> diagnostics) {
            this.code = code;
            this.factions = Collections.unmodifiableMap(
                new TreeMap<String, MilitaryFaction>(factions));
            this.pairs = Collections.unmodifiableList(new ArrayList<FactionPair>(pairs));
            List<String> ordered = new ArrayList<String>(diagnostics);
            Collections.sort(ordered);
            this.diagnostics = Collections.unmodifiableList(ordered);
            boolean hostile = false;
            boolean unknown = code == Code.DEFERRED_UNKNOWN_AUTHORITY;
            for (FactionPair pair : pairs) {
                hostile |= pair.relation == Relation.HOSTILE;
                unknown |= pair.relation == Relation.UNKNOWN;
            }
            hostilePairPresent = hostile;
            unknownAuthorityPresent = unknown;
        }

        public MilitaryFaction faction(String factionId) {
            return factions.get(KOMEAlliance.normalizeFactionKey(factionId));
        }

        public Relation relation(String first, String second) {
            String a = KOMEAlliance.normalizeFactionKey(first);
            String b = KOMEAlliance.normalizeFactionKey(second);
            if (a.equals(b) && a.length() > 0) return Relation.ALLIED;
            for (FactionPair pair : pairs) {
                if (pair.firstFactionId.equals(a) && pair.secondFactionId.equals(b)
                        || pair.firstFactionId.equals(b) && pair.secondFactionId.equals(a))
                    return pair.relation;
            }
            return Relation.UNKNOWN;
        }
    }

    private static final class MutableFaction {
        final String id;
        final TreeSet<String> detachments = new TreeSet<String>();
        boolean emergencyDefense;
        MutableFaction(String id) { this.id = id; }
        MilitaryFaction freeze() {
            return new MilitaryFaction(id, detachments, emergencyDefense);
        }
    }

    public Result evaluate(KOMEWorldData data, KOMEConflictRecord conflict) {
        if (data == null) throw new IllegalArgumentException("World data is required.");
        return evaluate(conflict, productionAuthority(data));
    }

    public Result evaluate(KOMEConflictRecord conflict, Authority authority) {
        if (authority == null) throw new IllegalArgumentException("Combat authority is required.");
        Map<String, MutableFaction> mutable = new TreeMap<String, MutableFaction>();
        List<String> diagnostics = new ArrayList<String>();
        if (conflict == null || !conflict.isActive()) {
            diagnostics.add("An active ConflictRecord is required.");
            return result(Code.INVALID_CONFLICT, mutable, Collections.<FactionPair>emptyList(), diagnostics);
        }

        for (Map.Entry<String, KOMEConflictRecord.Commitment> entry
                : new TreeMap<String, KOMEConflictRecord.Commitment>(
                    conflict.getCommitments()).entrySet()) {
            DetachmentResolution resolution;
            try {
                resolution = authority.resolveDetachment(entry.getKey());
            } catch (RuntimeException failure) {
                resolution = null;
            }
            if (!validCampaignResolution(conflict, entry.getKey(), resolution)) {
                diagnostics.add("Detachment " + entry.getKey()
                    + " has unknown or incoherent strategic authority.");
                continue;
            }
            add(mutable, resolution.factionId).detachments.add(entry.getKey());
        }

        EmergencyDefensePresence defense;
        try {
            defense = authority.emergencyDefense(conflict);
        } catch (RuntimeException failure) {
            defense = EmergencyDefensePresence.unknown("Emergency Defense authority failed.");
        }
        if (defense == null || defense.status == EmergencyDefensePresence.Status.UNKNOWN) {
            diagnostics.add(defense == null || defense.reason.length() == 0
                ? "Emergency Defense authority is unknown." : defense.reason);
        } else if (defense.status == EmergencyDefensePresence.Status.PRESENT) {
            KOMEConflictRecord.FactionParticipation participation =
                conflict.getFactionParticipation().get(defense.factionId);
            if (participation == null || !participation.isActive()) {
                diagnostics.add("Emergency Defense faction " + defense.factionId
                    + " lacks active conflict participation authority.");
            } else {
                add(mutable, defense.factionId).emergencyDefense = true;
            }
        }

        List<FactionPair> pairs = new ArrayList<FactionPair>();
        List<String> factions = new ArrayList<String>(mutable.keySet());
        for (int i = 0; i < factions.size(); i++) {
            for (int j = i + 1; j < factions.size(); j++) {
                Relation relation;
                try {
                    relation = authority.relation(factions.get(i), factions.get(j));
                } catch (RuntimeException failure) {
                    relation = Relation.UNKNOWN;
                }
                if (relation == null) relation = Relation.UNKNOWN;
                pairs.add(new FactionPair(factions.get(i), factions.get(j), relation));
                if (relation == Relation.UNKNOWN)
                    diagnostics.add("Diplomacy is unknown for " + factions.get(i)
                        + " and " + factions.get(j) + ".");
            }
        }
        Code code = diagnostics.isEmpty() ? Code.RESOLVED
            : Code.DEFERRED_UNKNOWN_AUTHORITY;
        return result(code, mutable, pairs, diagnostics);
    }

    private static boolean validCampaignResolution(KOMEConflictRecord conflict,
            String expectedId, DetachmentResolution resolution) {
        if (resolution == null || resolution.status != ReferenceStatus.RESOLVED
                || resolution.classification != KOMEHiredUnitClass.CAMPAIGN
                || !expectedId.equals(resolution.detachmentId)
                || !conflict.getTileId().equals(resolution.strategicTileId)) return false;
        KOMEConflictRecord.FactionParticipation participation =
            conflict.getFactionParticipation().get(resolution.factionId);
        return participation != null && participation.isActive();
    }

    private static MutableFaction add(Map<String, MutableFaction> factions,
            String factionId) {
        String id = KOMEAlliance.normalizeFactionKey(factionId);
        MutableFaction faction = factions.get(id);
        if (faction == null) {
            faction = new MutableFaction(id);
            factions.put(id, faction);
        }
        return faction;
    }

    private static Result result(Code code, Map<String, MutableFaction> mutable,
            List<FactionPair> pairs, List<String> diagnostics) {
        Map<String, MilitaryFaction> frozen = new TreeMap<String, MilitaryFaction>();
        for (MutableFaction faction : mutable.values())
            frozen.put(faction.id, faction.freeze());
        return new Result(code, frozen, pairs, diagnostics);
    }

    private static Authority productionAuthority(final KOMEWorldData data) {
        return new Authority() {
            @Override public DetachmentResolution resolveDetachment(String detachmentId) {
                return KOMEConflictService.resolveProductionDetachment(data, detachmentId);
            }

            @Override public EmergencyDefensePresence emergencyDefense(
                    KOMEConflictRecord conflict) {
                return emergencyDefensePresence(data, conflict);
            }

            @Override public Relation relation(String first, String second) {
                String a = KOMEAlliance.normalizeFactionKey(first);
                String b = KOMEAlliance.normalizeFactionKey(second);
                if (a.length() == 0 || b.length() == 0
                        || KOMEAlliance.findLotrFaction(a) == null
                        || KOMEAlliance.findLotrFaction(b) == null)
                    return Relation.UNKNOWN;
                if (a.equals(b)) return Relation.ALLIED;
                KOMEDiplomacyRelation relation =
                    KOMEDiplomacyService.getRelation(data, a, b);
                if (relation == KOMEDiplomacyRelation.ENEMIES
                        || relation == KOMEDiplomacyRelation.MORTAL_ENEMIES)
                    return Relation.HOSTILE;
                return relation == KOMEDiplomacyRelation.ALLIES
                    ? Relation.ALLIED : Relation.NON_ALLIED;
            }
        };
    }

    static EmergencyDefensePresence emergencyDefensePresence(KOMEWorldData data,
            KOMEConflictRecord conflict) {
        KOMEEmergencyDefenseCommitment commitment =
            data.emergencyDefenseCommitments.get(conflict.getConflictId());
        KOMEEmergencyDefenseObservation observation =
            data.emergencyDefenseObservations.get(conflict.getConflictId());
        if (commitment != null && observation != null)
            return EmergencyDefensePresence.unknown(
                "Conflicting Emergency Defense authorities exist.");
        if (commitment == null) return EmergencyDefensePresence.none();
        if (!conflict.getConflictId().equals(commitment.conflictId)
                || !conflict.getTileId().equals(commitment.tileId))
            return EmergencyDefensePresence.unknown(
                "Emergency Defense identity does not match the active conflict.");
        int present = commitment.count(
            KOMEEmergencyDefenseCommitment.Disposition.PENDING)
            + commitment.count(KOMEEmergencyDefenseCommitment.Disposition.ACTIVE);
        if (present == 0) return EmergencyDefensePresence.none();
        if (commitment.state == KOMEEmergencyDefenseCommitment.State.ENDING
                || commitment.state == KOMEEmergencyDefenseCommitment.State.DEMOBILIZED)
            return EmergencyDefensePresence.unknown(
                "Emergency Defense lifecycle does not match the active conflict.");
        return EmergencyDefensePresence.present(commitment.nativeFaction);
    }
}
