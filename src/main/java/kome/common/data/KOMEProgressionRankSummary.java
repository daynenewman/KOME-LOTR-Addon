package kome.common.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Server-authored, presentation-safe state for the Ranks page of the progression book. */
public final class KOMEProgressionRankSummary {
    public static final String[] LADDER = {"Serf", "Knight", "Lord", "Prince"};
    public static final KOMEProgressionRankSummary EMPTY = new KOMEProgressionRankSummary(
        "", "", "", Collections.<Requirement>emptyList(), "", "", "");

    public static final class Requirement {
        public final String label;
        public final int current, required;
        public final boolean complete;

        public Requirement(String label, int current, int required, boolean complete) {
            this.label = safe(label);
            this.current = Math.max(0, current);
            this.required = Math.max(0, required);
            this.complete = complete;
        }
    }

    public final String currentRank, nextRank, promotionTitle;
    public final List<Requirement> requirements;
    public final String activityHeading, activityTitle, activityObjective;

    public KOMEProgressionRankSummary(String currentRank, String nextRank, String promotionTitle,
            List<Requirement> requirements, String activityHeading, String activityTitle,
            String activityObjective) {
        this.currentRank = safe(currentRank);
        this.nextRank = safe(nextRank);
        this.promotionTitle = safe(promotionTitle);
        this.requirements = Collections.unmodifiableList(new ArrayList<Requirement>(
            requirements == null ? Collections.<Requirement>emptyList() : requirements));
        this.activityHeading = safe(activityHeading);
        this.activityTitle = safe(activityTitle);
        this.activityObjective = safe(activityObjective);
    }

    public boolean hasActivity() {
        return activityHeading.length() != 0;
    }

    public static KOMEProgressionRankSummary project(KOMEPlayerProgression progression,
            double pledgedFactionAlignment) {
        if (progression == null) return EMPTY;
        KOMEProgressionRank rank = progression.getCanonicalRank();
        KOMEProgressionRank next = next(rank);
        List<Requirement> requirements = new ArrayList<Requirement>();
        String heading = "", title = "", objective = "";

        if (rank == KOMEProgressionRank.SERF) {
            KOMESerfKnightProgression state = progression.getSerfKnightProgression();
            int alignment = (int) Math.floor(Math.max(0D, pledgedFactionAlignment));
            int duties = 0;
            for (KOMESerfKnightDutyType type : KOMESerfKnightDutyType.values())
                if (state.getDuty(type).isCompleted()) duties++;
            requirements.add(new Requirement("Faction Alignment", alignment,
                KOMESerfKnightService.REQUIRED_ALIGNMENT,
                pledgedFactionAlignment >= KOMESerfKnightService.REQUIRED_ALIGNMENT));
            requirements.add(new Requirement("Duties", duties,
                KOMESerfKnightDutyType.values().length, KOMESerfKnightService.allDutiesComplete(state)));
            requirements.add(new Requirement("Trial of Knighthood", state.isTrialCompleted() ? 1 : 0,
                1, state.isTrialCompleted()));
            requirements.add(new Requirement("Master's Parting Gift", state.hasPartingGift() ? 1 : 0,
                1, state.hasPartingGift()));

            String active = state.getActiveAssignmentKind();
            if ("provisioning".equals(active)) {
                heading = "Current Duty";
                title = "Deliver Provisions";
                objective = provisioningObjective(state);
            } else if ("profession".equals(active)) {
                heading = "Current Duty";
                title = "Gather Materials";
                objective = professionObjective(state);
            } else if ("courier".equals(active)) {
                heading = "Current Duty";
                title = "Courier Service";
                objective = courierObjective(state);
            } else if ("trial".equals(active)) {
                heading = "Trial of Knighthood";
                KOMESerfKnightTrial trial = KOMESerfKnightTrial.forId(state.getTrialId());
                title = trial == null ? "Trial" : trial.displayName;
                objective = trialObjective(state, trial);
            } else if (KOMESerfKnightService.allDutiesComplete(state)
                    && state.getProspectiveLiege().isSet() && state.getTrialId().length() == 0) {
                heading = "Trial of Knighthood";
                title = "Awaiting assignment";
                objective = "Awaiting assignment from your Liege.";
            }
        }

        String nextName = next == null ? "" : next.displayName;
        String promotion = next == null ? "Highest Rank" : "Requirements for " + nextName;
        return new KOMEProgressionRankSummary(rank.displayName, nextName, promotion, requirements,
            heading, title, objective);
    }

    private static String provisioningObjective(KOMESerfKnightProgression state) {
        KOMESerfProvisioningAssignment assignment = KOMESerfProvisioningAssignment.readFromNBT(
            state.getDuty(KOMESerfKnightDutyType.PROVISIONING).getAssignmentData());
        if (assignment == null) return "Bring the requested provisions to your Master.";
        String text = "Bring to your Master: ";
        for (KOMESerfProvisioningAssignment.Requirement food : assignment.foods)
            text = appendQuota(text, food.displayName, food.delivered, food.required);
        return appendQuota(text, assignment.drink.displayName, assignment.drink.delivered,
            assignment.drink.required) + ".";
    }

    private static String professionObjective(KOMESerfKnightProgression state) {
        KOMESerfProfessionAssignment assignment = KOMESerfProfessionAssignment.readFromNBT(
            state.getDuty(KOMESerfKnightDutyType.PROFESSION).getAssignmentData());
        if (assignment == null) return "Bring the requested materials to your Master.";
        String text = "For your Master's " + assignment.tradeDisplayName + " trade: ";
        for (KOMESerfProfessionAssignment.Requirement material : assignment.requirements)
            text = appendQuota(text, material.displayName, material.delivered, material.required);
        return text + ".";
    }

    private static String courierObjective(KOMESerfKnightProgression state) {
        KOMESerfCourierAssignment assignment = KOMESerfCourierAssignment.readFromNBT(
            state.getDuty(KOMESerfKnightDutyType.COURIER).getAssignmentData());
        if (assignment == null) return "Carry out your Master's delivery.";
        if (assignment.stage == KOMESerfCourierAssignment.Stage.DELIVERED)
            return "Return to your Master and report the delivery.";
        String recipient = assignment.recipient.isSet() ? " and seek " + assignment.recipient.displayName
            : " and find the recipient there";
        return "Carry the message to " + assignment.destinationName + recipient + ".";
    }

    private static String trialObjective(KOMESerfKnightProgression state, KOMESerfKnightTrial trial) {
        KOMESerfKnightTrialAssignment assignment = state.getTrialAssignment();
        if (assignment != null && assignment.stage == KOMESerfKnightTrialAssignment.Stage.FAILED)
            return "This trial is lost. Seek a new liege.";
        if (assignment != null && "escort".equals(assignment.trialId))
            return assignment.stage == KOMESerfKnightTrialAssignment.Stage.ASSIGNED
                ? "Meet your charge at your Liege's side."
                : "See your charge safely through the journey.";
        if (assignment != null && "recovery".equals(assignment.trialId))
            return assignment.data.getBoolean("RecoveryRetrieved")
                ? "Return the recovered item to your Liege." : "Recover the lost item.";
        if (assignment != null && "defense".equals(assignment.trialId))
            return assignment.stage == KOMESerfKnightTrialAssignment.Stage.ASSIGNED
                ? "Defend your people from the attack."
                : KOMESerfKnightDefenseService.allDead(assignment)
                    ? "Return to your Liege." : "Defeat the remaining attackers.";
        return trial == null ? "Complete your Trial." : trial.description;
    }

    private static String appendQuota(String text, String label, int current, int required) {
        return text + (text.endsWith(": ") ? "" : ", ") + safe(label) + " " + current + " / " + required;
    }

    private static KOMEProgressionRank next(KOMEProgressionRank rank) {
        if (rank == null || rank == KOMEProgressionRank.PRINCE) return null;
        return KOMEProgressionRank.values()[rank.ordinal() + 1];
    }

    private static String safe(String value) { return value == null ? "" : value; }
}
