package kome.common.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pure detached ownership candidate selection. It never edits a tile or ConflictRecord. */
public final class KOMEOrdinaryBattleOwnershipResolver {
    public enum Code {
        WINNER_DEFAULT_OWNER,
        WINNER_LONE_FACTION,
        WINNER_LONGEST_CONTINUITY,
        TIMING_INCOMPLETE,
        HOSTILE_CONTEST_ACTIVE,
        NO_ELIGIBLE_MILITARY_FACTION,
        NON_ALLIED_SURVIVORS,
        DEFERRED_UNKNOWN_AUTHORITY,
        DEFERRED_FORTIFIED,
        DEFERRED_ENCIRCLEMENT,
        INVALID_CONFLICT
    }

    public static final class Plan {
        public final Code code;
        public final String winnerFactionId;
        public final List<String> eligibleFactionIds;
        public final String reason;

        private Plan(Code code, String winner, List<String> eligible, String reason) {
            this.code = code;
            winnerFactionId = winner == null ? "" : winner;
            eligibleFactionIds = Collections.unmodifiableList(
                new ArrayList<String>(eligible));
            this.reason = reason;
        }

        public boolean hasWinner() {
            return code == Code.WINNER_DEFAULT_OWNER
                || code == Code.WINNER_LONE_FACTION
                || code == Code.WINNER_LONGEST_CONTINUITY;
        }
    }

    private KOMEOrdinaryBattleOwnershipResolver() { }

    public static Plan resolve(KOMEConflictRecord conflict, String defaultOwner,
            boolean timingComplete, KOMECombatStateEvaluator.Result combat,
            KOMEFortificationContextResolver.Decision fortification) {
        List<String> empty = Collections.emptyList();
        if (conflict == null || !conflict.isActive())
            return plan(Code.INVALID_CONFLICT, "", empty,
                "An active ConflictRecord is required.");
        if (conflict.getState() == KOMEConflictRecord.State.ENCIRCLEMENT)
            return plan(Code.DEFERRED_ENCIRCLEMENT, "", empty,
                "Encirclement ownership requires fortified resolution.");
        if (conflict.getState() != KOMEConflictRecord.State.ORDINARY)
            return plan(Code.INVALID_CONFLICT, "", empty,
                "Only an ordinary conflict may use ordinary ownership resolution.");
        if (fortification == null
                || fortification.status == KOMEFortificationContextResolver.Status.AMBIGUOUS)
            return plan(Code.DEFERRED_FORTIFIED, "", empty,
                fortification == null ? "Defensive context is unavailable."
                    : fortification.reason);
        if (fortification.status == KOMEFortificationContextResolver.Status.FORTIFIED)
            return plan(Code.DEFERRED_FORTIFIED, "", empty, fortification.reason);
        if (combat == null || combat.code != KOMECombatStateEvaluator.Code.RESOLVED
                || combat.unknownAuthorityPresent)
            return plan(Code.DEFERRED_UNKNOWN_AUTHORITY, "", empty,
                "Military presence or diplomacy is unresolved.");
        if (!timingComplete)
            return plan(Code.TIMING_INCOMPLETE, "", empty,
                "The response timing requirement is incomplete.");
        if (combat.hostilePairPresent)
            return plan(Code.HOSTILE_CONTEST_ACTIVE, "", empty,
                "Hostile committed military factions remain.");

        String owner = KOMEAlliance.normalizeFactionKey(defaultOwner);
        List<String> present = new ArrayList<String>(combat.factions.keySet());
        for (int i = 0; i < present.size(); i++) {
            for (int j = i + 1; j < present.size(); j++) {
                if (combat.relation(present.get(i), present.get(j))
                        != KOMECombatStateEvaluator.Relation.ALLIED)
                    return plan(Code.NON_ALLIED_SURVIVORS, "", present,
                        "Surviving military factions are not one allied coalition.");
            }
        }

        List<String> eligible = new ArrayList<String>();
        for (KOMECombatStateEvaluator.MilitaryFaction faction
                : combat.factions.values()) {
            if (faction.hasCampaignClaimAuthority()
                    || faction.factionId.equals(owner)) eligible.add(faction.factionId);
            else return plan(Code.DEFERRED_UNKNOWN_AUTHORITY, "", present,
                "Emergency Defense cannot create an attacking conquest claimant.");
        }
        Collections.sort(eligible);
        if (eligible.isEmpty())
            return plan(Code.NO_ELIGIBLE_MILITARY_FACTION, "", eligible,
                "No surviving eligible military faction remains.");
        if (owner.length() > 0 && eligible.contains(owner))
            return plan(Code.WINNER_DEFAULT_OWNER, owner, eligible,
                "The default owner remains in the surviving allied coalition.");
        if (eligible.size() == 1)
            return plan(Code.WINNER_LONE_FACTION, eligible.get(0), eligible,
                "One eligible military faction survives.");

        String winner = null;
        long earliest = Long.MAX_VALUE;
        for (String factionId : eligible) {
            KOMEConflictRecord.FactionParticipation participation =
                conflict.getFactionParticipation().get(factionId);
            if (participation == null || !participation.isActive())
                return plan(Code.DEFERRED_UNKNOWN_AUTHORITY, "", eligible,
                    "Faction continuity is unavailable for " + factionId + ".");
            if (winner == null || participation.startedAtMillis < earliest
                    || participation.startedAtMillis == earliest
                        && factionId.compareTo(winner) < 0) {
                winner = factionId;
                earliest = participation.startedAtMillis;
            }
        }
        return plan(Code.WINNER_LONGEST_CONTINUITY, winner, eligible,
            "The longest continuously participating allied attacker wins.");
    }

    private static Plan plan(Code code, String winner, List<String> eligible,
            String reason) {
        return new Plan(code, winner, eligible, reason);
    }
}
