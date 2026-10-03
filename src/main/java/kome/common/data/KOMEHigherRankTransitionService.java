package kome.common.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Canonical bridge for the higher player-rank transitions that still use the
 * legacy rank-quota / standing achievement definitions. Serf -> Knight remains
 * owned by the dedicated Master/Liege/Trial lifecycle.
 */
public final class KOMEHigherRankTransitionService {
    public static final class RequirementGroup {
        public final String label;
        public final String[] achievementIds;

        private RequirementGroup(String label, String... achievementIds) {
            this.label = label == null ? "" : label;
            this.achievementIds = achievementIds == null ? new String[0] : achievementIds.clone();
        }
    }

    public static final class Transition {
        public final KOMEProgressionRank fromRank;
        public final KOMEProgressionRank toRank;
        public final String completionMarkerId;
        public final RequirementGroup[] groups;

        private Transition(KOMEProgressionRank fromRank, KOMEProgressionRank toRank,
                String completionMarkerId, RequirementGroup... groups) {
            this.fromRank = fromRank;
            this.toRank = toRank;
            this.completionMarkerId = completionMarkerId;
            this.groups = groups == null ? new RequirementGroup[0] : groups.clone();
        }
    }

    public static final class Result {
        public final boolean success;
        public final String reason;
        public final KOMEProgressionRank newRank;

        private Result(boolean success, String reason, KOMEProgressionRank newRank) {
            this.success = success;
            this.reason = reason == null ? "" : reason;
            this.newRank = newRank;
        }
    }

    private static final Transition KNIGHT_TO_LORD = new Transition(
        KOMEProgressionRank.KNIGHT,
        KOMEProgressionRank.LORD,
        "knight.title_lord",
        new RequirementGroup("Rank Quotas",
            "knight.alignment_2000",
            "knight.hooligan",
            "knight.speared",
            "knight.deliver_drops",
            "knight.drop_quota_1",
            "knight.drop_quota_2",
            "knight.faction_1",
            "knight.faction_2"),
        new RequirementGroup("Rise in Standing",
            "knight.choose_capital",
            "knight.crown_king",
            "knight.record_king",
            "knight.conquest_fellowship")
    );

    private static final Transition LORD_TO_PRINCE = new Transition(
        KOMEProgressionRank.LORD,
        KOMEProgressionRank.PRINCE,
        "lord.title_prince_king",
        new RequirementGroup("Rank Quotas",
            "lord.alignment_3000",
            "lord.global_alignment",
            "lord.capture_5_waypoints"),
        new RequirementGroup("Highest Standing",
            "lord.fell_beast",
            "lord.fluttering_by")
    );

    private static final Transition[] TRANSITIONS = {
        KNIGHT_TO_LORD,
        LORD_TO_PRINCE
    };

    private KOMEHigherRankTransitionService() { }

    public static Transition forCurrentRank(KOMEProgressionRank rank) {
        if (rank == null) return null;
        for (Transition transition : TRANSITIONS) {
            if (transition.fromRank == rank) return transition;
        }
        return null;
    }

    public static Transition forMarker(String achievementId) {
        String normalized = normalize(achievementId);
        if (normalized.length() == 0) return null;
        for (Transition transition : TRANSITIONS) {
            if (transition.completionMarkerId.equals(normalized)) return transition;
        }
        return null;
    }

    public static boolean isTransitionMarker(String achievementId) {
        return forMarker(achievementId) != null;
    }

    public static List<String> missingRequirementIds(KOMEPlayerProgression progression,
            Transition transition) {
        if (progression == null || transition == null) return Collections.emptyList();
        List<String> missing = new ArrayList<String>();
        for (RequirementGroup group : transition.groups) {
            for (String id : group.achievementIds) {
                KOMEProgressionAchievement achievement = KOMEProgressionAchievement.forID(id);
                if (achievement == null || !progression.isCompleted(achievement)) missing.add(id);
            }
        }
        return Collections.unmodifiableList(missing);
    }

    public static boolean requirementsComplete(KOMEPlayerProgression progression,
            Transition transition) {
        return transition != KNIGHT_TO_LORD && progression != null && transition != null
            && missingRequirementIds(progression, transition).isEmpty();
    }

    public static String promotionReason(KOMEPlayerProgression progression, Transition transition) {
        if (transition == KNIGHT_TO_LORD) return "Return to your Liege after completing your Trial of Lordship.";
        if (progression == null) return "Missing player progression.";
        if (transition == null) return "That progression step is not a canonical rank transition.";
        if (progression.getCanonicalRank() != transition.fromRank) {
            if (progression.getCanonicalRank().order >= transition.toRank.order)
                return "That rank transition is already complete.";
            return "Your current faction rank is not eligible for that transition.";
        }
        List<String> missing = missingRequirementIds(progression, transition);
        if (!missing.isEmpty()) {
            KOMEProgressionAchievement achievement = KOMEProgressionAchievement.forID(missing.get(0));
            return achievement == null
                ? "Complete the remaining rank requirements first."
                : "Complete " + achievement.title + " first.";
        }
        return "";
    }

    /**
     * Performs only the canonical rank mutation and legacy completion marker.
     * Political ruler office is intentionally independent from player rank.
     */
    public static Result promote(KOMEWorldData data, UUID playerId, String completionMarkerId) {
        if (data == null || playerId == null) return reject("Missing player progression.");
        Transition transition = forMarker(completionMarkerId);
        KOMEPlayerProgression progression = data.getProgression(playerId);
        String reason = promotionReason(progression, transition);
        if (reason.length() != 0) return reject(reason);

        if (!KOMECanonicalRankService.setCanonicalRank(data, playerId, transition.toRank))
            return reject("That rank transition is already complete.");
        progression.grant(transition.completionMarkerId);

        KOMEProgressionAutoCompleter.applyUnlocks(progression);
        KOMEProgressionNpcRoles.syncPlayer(data, playerId);
        data.markDirty();
        return new Result(true, "", transition.toRank);
    }

    private static Result reject(String reason) {
        return new Result(false, reason, null);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
