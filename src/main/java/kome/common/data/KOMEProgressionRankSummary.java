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
        public final List<Requirement> children;

        public Requirement(String label, int current, int required, boolean complete) {
            this(label, current, required, complete, Collections.<Requirement>emptyList());
        }

        public Requirement(String label, int current, int required, boolean complete,
                List<Requirement> children) {
            this.label = safe(label);
            this.current = Math.max(0, current);
            this.required = Math.max(0, required);
            this.complete = complete;
            this.children = Collections.unmodifiableList(new ArrayList<Requirement>(
                children == null ? Collections.<Requirement>emptyList() : children));
        }

        public boolean hasChildren() {
            return !children.isEmpty();
        }
    }

    public final String factionKey;
    public final String currentRank, nextRank, promotionTitle;
    public final List<Requirement> requirements;
    public final String activityHeading, activityTitle, activityObjective;

    public KOMEProgressionRankSummary(String currentRank, String nextRank, String promotionTitle,
            List<Requirement> requirements, String activityHeading, String activityTitle,
            String activityObjective) {
        this("", currentRank, nextRank, promotionTitle, requirements, activityHeading, activityTitle, activityObjective);
    }

    public KOMEProgressionRankSummary(String factionKey, String currentRank, String nextRank, String promotionTitle,
            List<Requirement> requirements, String activityHeading, String activityTitle,
            String activityObjective) {
        this.factionKey = safe(factionKey);
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
        return project(progression, pledgedFactionAlignment, "");
    }

    public static KOMEProgressionRankSummary project(KOMEPlayerProgression progression,
            double pledgedFactionAlignment, String factionKey) {
        return project(progression, pledgedFactionAlignment, factionKey, null);
    }

    public static KOMEProgressionRankSummary project(KOMEPlayerProgression progression,
            double pledgedFactionAlignment, String factionKey, java.util.UUID playerId) {
        if (progression == null) return EMPTY;
        KOMEProgressionRank rank = progression.getCanonicalRank();
        KOMEProgressionRank next = next(rank);
        List<Requirement> requirements = new ArrayList<Requirement>();
        String heading = "", title = "", objective = "";

        if (rank == KOMEProgressionRank.SERF) {
            KOMESerfKnightProgression state = progression.getSerfKnightProgression();
            int alignment = (int) Math.floor(Math.max(0D, pledgedFactionAlignment));
            int duties = 0;
            List<Requirement> dutyRequirements = new ArrayList<Requirement>();
            for (KOMESerfKnightDutyType type : KOMESerfKnightDutyType.values()) {
                boolean dutyComplete = state.getDuty(type).isCompleted();
                if (dutyComplete) duties++;
                dutyRequirements.add(new Requirement(type.displayName, dutyComplete ? 1 : 0,
                    1, dutyComplete));
            }
            int requiredAlignment=state.hasLiege()||state.isTrialCompleted()?KOMESerfKnightService.REQUIRED_ALIGNMENT:KOMEStandingTrialEligibility.requiredAlignment(factionKey);
            requirements.add(new Requirement("Faction Alignment", alignment,
                requiredAlignment,
                pledgedFactionAlignment >= requiredAlignment));
            requirements.add(new Requirement("Duties", duties,
                KOMESerfKnightDutyType.values().length, KOMESerfKnightService.allDutiesComplete(state),
                dutyRequirements));
            requirements.add(new Requirement("Trial of Standing", state.isTrialCompleted() ? 1 : 0,
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
                heading = "Trial of Standing";
                KOMESerfKnightTrial trial = KOMESerfKnightTrial.forId(state.getTrialId());
                title = trial == null ? "Trial" : trial.displayName;
                objective = trialObjective(state, trial);
            } else if (KOMESerfKnightService.allDutiesComplete(state)
                    && state.getSerfdomMaster().isSet()
                    && safe(factionKey).length() != 0
                    && state.getTrialId().length() == 0
                    && KOMESerfKnightService.mayIssueTrial(
                        state, KOMESerfKnightService.calendarDayNow(), playerId)) {
                heading = "Trial of Standing";
                if(!state.hasLiege()&&!KOMEStandingTrialEligibility.meetsAlignment(state,pledgedFactionAlignment,factionKey)) {
                    heading="Faction Alignment";
                    title=KOMEFactionProgressionTitles.title(factionKey,KOMEProgressionRank.SERF);
                    objective="Reach "+requiredAlignment+" faction alignment before seeking a Liege.";
                } else if (state.hasLiege()) {
                    title = "Ready to request";
                    objective = "Request your Trial of Standing from your Liege.";
                } else {
                    title = "Seek a prospective Liege";
                    objective = "Seek an eligible Liege for your Trial of Standing.";
                }
            }
        } else if (rank == KOMEProgressionRank.KNIGHT) {
            int credits=Math.min(3,progression.getKnightService().qualifyingTypes(factionKey).size());
            requirements.add(new Requirement("Service to your Liege",credits,3,credits>=3));
            requirements.add(new Requirement("Faction Alignment",Double.isFinite(pledgedFactionAlignment)?(int)Math.floor(Math.max(0D,pledgedFactionAlignment)):0,2000,Double.isFinite(pledgedFactionAlignment)&&pledgedFactionAlignment>=2000D));
            KOMELordshipTrial trial=progression.getLordship().assignment();
            requirements.add(new Requirement("Trial of Lordship",trial!=null&&trial.ready()?1:0,1,trial!=null&&trial.ready()));
            heading="Trial of Lordship";title=trial==null?"Service to your Liege":KOMELordshipTrialPresentation.title(trial);
            objective=KOMELordshipTrialPresentation.status(progression,pledgedFactionAlignment,factionKey);
        } else if (rank == KOMEProgressionRank.LORD) {
            KOMEHigherRankTransitionService.Transition transition =
                KOMEHigherRankTransitionService.forCurrentRank(rank);
            if (transition != null) {
                for (KOMEHigherRankTransitionService.RequirementGroup group : transition.groups) {
                    List<Requirement> children = new ArrayList<Requirement>();
                    int complete = 0;
                    for (String id : group.achievementIds) {
                        Requirement child = legacyRequirement(progression, pledgedFactionAlignment, id);
                        if (child.complete) complete++;
                        children.add(child);
                    }
                    requirements.add(new Requirement(group.label, complete, children.size(),
                        complete == children.size(), children));
                }
                KOMEProgressionAchievement marker =
                    KOMEProgressionAchievement.forID(transition.completionMarkerId);
                boolean markerComplete = marker != null && progression.isCompleted(marker);
                requirements.add(new Requirement(marker == null ? "Earn New Standing" : marker.title,
                    markerComplete ? 1 : 0, 1, markerComplete));

                if (!markerComplete
                        && KOMEHigherRankTransitionService.requirementsComplete(progression, transition)) {
                    heading = "Standing";
                    title = "Ready to advance";
                    objective = "Complete " + (marker == null ? "the final standing step" : marker.title)
                        + " to advance to " + KOMEFactionProgressionTitles.title(factionKey, transition.toRank) + ".";
                }
            }
        }

        if(rank==KOMEProgressionRank.KNIGHT&&heading.length()==0){
            KOMEKnightCommission commission=progression.getKnightService().assignment();
            heading="Liege Commission";title=commission==null?"Service to your Liege":KOMEKnightCommissionPresentation.title(commission.type);
            objective=KOMEKnightCommissionPresentation.summary(progression,factionKey).trim();
        }
        else if(rank==KOMEProgressionRank.KNIGHT&&progression.getLordship().assignment()==null) objective+="\n"+KOMEKnightCommissionPresentation.summary(progression,factionKey).trim();
        String currentName = KOMEFactionProgressionTitles.title(factionKey, rank);
        if(rank.order<=KOMEProgressionRank.KNIGHT.order&&KOMEProgressionFactionResolver.missingNativeLiege(factionKey))objective+="\n"+KOMEKnightCommissionPresentation.unavailableLiege();
        String nextName = next == null ? "" : KOMEFactionProgressionTitles.title(factionKey, next);
        String promotion = next == null ? "Highest Rank" : "Requirements for " + nextName;
        return new KOMEProgressionRankSummary(factionKey, currentName, nextName, promotion, requirements,
            heading, title, objective);
    }

    private static Requirement legacyRequirement(KOMEPlayerProgression progression,
            double pledgedFactionAlignment, String id) {
        KOMEProgressionAchievement achievement = KOMEProgressionAchievement.forID(id);
        String label = achievement == null ? id : achievement.title;
        boolean completed = achievement != null && progression.isCompleted(achievement);
        if ("knight.alignment_2000".equals(id)) {
            int current = (int)Math.floor(Math.max(0D, pledgedFactionAlignment));
            return new Requirement(label, current, 2000, completed);
        }
        if ("lord.alignment_3000".equals(id)) {
            int current = (int)Math.floor(Math.max(0D, pledgedFactionAlignment));
            return new Requirement(label, current, 3000, completed);
        }
        return new Requirement(label, completed ? 1 : 0, 1, completed);
    }

    private static String provisioningObjective(KOMESerfKnightProgression state) {
        KOMESerfProvisioningAssignment assignment = KOMESerfProvisioningAssignment.readFromNBT(
            state.getDuty(KOMESerfKnightDutyType.PROVISIONING).getAssignmentData());
        if (assignment == null) return "Bring the requested provisions to your Master.";
        return "Bring to your Master: " + assignment.progressList("; ") + ".";
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
        if (assignment.confirmedRecipientDeath) return "The recipient has died. Return the current letter to your Master.";
        if (assignment.stage == KOMESerfCourierAssignment.Stage.DELIVERED)
            return "Return to your Master and report the delivery.";
        String recipient = assignment.recipient.isSet() ? " and seek " + assignment.recipient.displayName
            : " and find the recipient there";
        return "Carry the message to " + assignment.destinationName + recipient + ".";
    }

    private static String trialObjective(KOMESerfKnightProgression state, KOMESerfKnightTrial trial) {
        KOMESerfKnightTrialAssignment assignment = state.getTrialAssignment();
        if (assignment != null && assignment.stage == KOMESerfKnightTrialAssignment.Stage.FAILED)
            return "This trial is lost. Seek a new Liege.";
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
