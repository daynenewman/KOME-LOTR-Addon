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

        if(rank==KOMEProgressionRank.WANDERER){
            heading="Allegiance";title="Pledge to a faction";objective="A legitimate faction pledge earns Serf standing.";
        } else if(rank==KOMEProgressionRank.SERF){
            KOMESerfKnightProgression state=progression.getSerfKnightProgression();
            int credits=KOMEServicePromotionService.duties(state);
            requirements.add(new Requirement("Reported service",credits,3,credits>=3));
            requirements.add(new Requirement("Faction Alignment",Double.isFinite(pledgedFactionAlignment)?(int)Math.floor(Math.max(0,pledgedFactionAlignment)):0,150,Double.isFinite(pledgedFactionAlignment)&&pledgedFactionAlignment>=150));
            heading="Master service";title=state.getSerfdomMaster().isSet()?state.getSerfdomMaster().displayName:"Seek a Master";
            String active=state.getActiveAssignmentKind();
            objective=!state.getSerfdomMaster().isSet()?"Enter service with a Master. Three duties earn Knight standing.":credits>=3?"Report to your Master. Your service is complete; Knight standing requires 150 faction alignment.":
                "Speak with your ordinary same-faction Master for your next duty. Three reported duties and 150 faction alignment earn Knight standing here.";
            if("provisioning".equals(active)){heading="Current Duty";title="Provisions";objective=provisioningObjective(state);}
            else if("profession".equals(active)){heading="Current Duty";title="Trade Assistance";objective=professionObjective(state);}
            else if("courier".equals(active)){heading="Current Duty";title="Courier Service";objective=courierObjective(state);}
            else if(state.ordinaryObjective()){
                heading="Current Duty";title=KOMESerfKnightDutyType.forKey(state.getTrialId()).displayName;
                objective=state.getTrialAssignment().stage==KOMESerfKnightTrialAssignment.Stage.OBJECTIVE_COMPLETE?"Report to your Master.":trialObjective(state,KOMESerfKnightTrial.forId(state.getTrialId())).replace("Liege","Master").replace("trial","duty");
            }
        } else if(rank==KOMEProgressionRank.KNIGHT){
            int credits=progression.getKnightService().qualifyingTypes(factionKey).size();
            requirements.add(new Requirement("Reported commissions",credits,3,credits>=3));
            requirements.add(new Requirement("Faction Alignment",Double.isFinite(pledgedFactionAlignment)?(int)Math.floor(Math.max(0,pledgedFactionAlignment)):0,2000,Double.isFinite(pledgedFactionAlignment)&&pledgedFactionAlignment>=2000));
            heading="Lord service";KOMEProgressionNpcRef lord=progression.getSerfKnightProgression().getLiege();title=lord.isSet()?lord.displayName:"Your recommendation";
            KOMEServiceRecognition.Record introduction=null;
            for(KOMEServiceRecognition.Record r:progression.getServiceRecognition().records())if(r.conferred==rank&&KOMEProgressionRelationshipLifecycle.sameFaction(r.faction,factionKey))introduction=r;
            title=lord.isSet()?lord.displayName:introduction==null?"Regional service":introduction.invalidated?"Recommendation expired":introduction.redeemed?"Introduction used":"Your recommendation";
            objective=!lord.isSet()?(introduction==null?"Seek advice about sponsored regional service; no earned recommendation is recorded.":introduction.invalidated?"Your former faction's letter remains a keepsake. It cannot sponsor service under a new pledge.":introduction.redeemed?"Your introduction has been used. Saved service survives the legitimate loss of a Liege.":"When ready, take your recommendation to a Lord or Prince at a verified native regional seat. Recover a lost copy with /progression recommendation."):credits>=3?"Report to your Liege. Your service is complete; Lord standing requires 2000 faction alignment.":"Three commissions earn Lord standing from this Liege on their final report. Required faction alignment: 2000.";
            KOMEKnightCommission a=progression.getKnightService().assignment();KOMELordshipTrial invasion=progression.getLordship().assignment();
            if(a!=null){heading="Current commission";title=KOMEKnightCommissionPresentation.title(a.type);objective=KOMEKnightCommissionPresentation.details(a);}
            else if(invasion!=null&&invasion.ordinaryService){heading="Current commission";title="Repel an Invasion";objective=KOMELordshipTrialPresentation.objective(invasion);}
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

        String currentName = KOMEFactionProgressionTitles.title(factionKey, rank);
        if(rank.order<=KOMEProgressionRank.KNIGHT.order&&KOMEProgressionFactionResolver.missingNativeLiege(factionKey))objective+="\n"+KOMEKnightCommissionPresentation.unavailableLiege();
        String nextName = next == null ? "" : KOMEFactionProgressionTitles.title(factionKey, next);
        String promotion = next == null ? "Highest Rank" : "Requirements for " + nextName;
        if (rank == KOMEProgressionRank.LORD) {
            promotion = "Legacy Prince checklist";
            heading="Future royal service";title="Crown at the faction capital";
            objective="Lord standing is earned. Royal leadership service awaits redesign; the legacy Prince checklist remains available.";
            for(KOMEServiceRecognition.Record r:progression.getServiceRecognition().records())if(r.conferred==rank&&!r.invalidated&&KOMEProgressionRelationshipLifecycle.sameFaction(r.faction,factionKey))
                objective+=" Your former Liege's recommendation is addressed to the Crown. Recover a lost copy with /progression recommendation.";
        }
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
            text = appendQuota(text, KOMEProgressionGoodsRules.name(material.requestedStack(),material.displayName), material.delivered, material.required);
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
                ? "Meet your charge at your Liege's side, then escort them to "+KOMESerfKnightEscortService.destinationName(assignment)+"."
                : "Escort your charge to "+KOMESerfKnightEscortService.destinationName(assignment)+".";
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
