package kome.common.data;

/** Read-only canonical progression-book projection. */
public final class KOMEProgressionSummary {
    private KOMEProgressionSummary() {}

    public static String text(KOMEPlayerProgression p) { return text(p, ""); }
    public static String text(KOMEPlayerProgression p, boolean hasPlayablePledge) { return text(p, hasPlayablePledge ? "Pledged Faction" : ""); }

    public static String text(KOMEPlayerProgression p, String pledgeName) {
        KOMESerfKnightProgression s = p.getSerfKnightProgression();
        String rank = "Rank: " + p.getCanonicalRank().displayName;
        if (p.getCanonicalRank() == KOMEProgressionRank.WANDERER) {
            boolean pledged = pledgeName != null && pledgeName.trim().length() != 0;
            return rank + "\nPledge: " + (pledged ? pledgeName : "None") + "\nNext: " + (pledged ? "Find a Serfdom Master" : "Pledge to a faction");
        }
        if (p.getCanonicalRank() != KOMEProgressionRank.SERF) return rank;
        if (!s.getSerfdomMaster().isSet()) return rank + "\nSerfdom Master: None\nNext: Find a Serfdom Master";

        String base = rank + "\nSerfdom Master: " + s.getSerfdomMaster().displayName;
        if (s.getProspectiveLiege().isSet() && s.getTrialId().length() == 0) return base + "\nProspective Liege: " + s.getProspectiveLiege().displayName + "\nNext: Speak with your Liege";
        if ("courier".equals(s.getActiveAssignmentKind())) {
            KOMESerfCourierAssignment a = KOMESerfCourierAssignment.readFromNBT(s.getDuty(KOMESerfKnightDutyType.COURIER).getAssignmentData());
            if (a != null) return base + "\nCurrent Duty: Courier\nMessage: " + (a.stage == KOMESerfCourierAssignment.Stage.DELIVERED ? "Delivered\nNext: Report to your Master" : "Undelivered\nDestination: " + a.destinationName + (a.recipient.isSet() ? "\nRecipient: " + a.recipient.displayName : "\nRecipient: Find them there"));
        }
        if ("provisioning".equals(s.getActiveAssignmentKind())) {
            KOMESerfProvisioningAssignment a = KOMESerfProvisioningAssignment.readFromNBT(s.getDuty(KOMESerfKnightDutyType.PROVISIONING).getAssignmentData());
            if (a != null) {
                String q = base + "\nCurrent Duty: Provisioning";
                for (KOMESerfProvisioningAssignment.Requirement food : a.foods) q += "\n" + food.displayName + ": " + food.delivered + " / " + food.required;
                return q + "\n" + a.drink.displayName + " in " + vesselName(a.drink.vessel) + ": " + a.drink.delivered + " / " + a.drink.required;
            }
        }
        if ("profession".equals(s.getActiveAssignmentKind())) {
            KOMESerfProfessionAssignment a = KOMESerfProfessionAssignment.readFromNBT(s.getDuty(KOMESerfKnightDutyType.PROFESSION).getAssignmentData());
            if (a != null) return professionText(base, a);
        }
        if ("trial".equals(s.getActiveAssignmentKind())) {
            KOMESerfKnightTrial trial=KOMESerfKnightTrial.forId(s.getTrialId());
            String objective=trial==null?"Complete your Trial":trial.description;
            KOMESerfKnightTrialAssignment assignment=s.getTrialAssignment();
            if(assignment!=null&&assignment.stage==KOMESerfKnightTrialAssignment.Stage.FAILED) return base + "\nProspective Liege: " + s.getProspectiveLiege().displayName + "\nTrial of Knighthood: " + (trial==null?"Trial":trial.displayName) + "\nObjective: This trial is lost. Seek a new liege.";
            if(assignment!=null&&"escort".equals(assignment.trialId)) objective=assignment.stage==KOMESerfKnightTrialAssignment.Stage.ASSIGNED?"Meet your charge at your Liege's side":assignment.stage==KOMESerfKnightTrialAssignment.Stage.FAILED?"Your charge was lost": "See your charge safely through the journey";
            if(assignment!=null&&"recovery".equals(assignment.trialId)) objective=assignment.data.getBoolean("RecoveryRetrieved")?"Return the recovered item to your Liege":"Recover the lost item";
            if(assignment!=null&&"defense".equals(assignment.trialId)) objective=assignment.stage==KOMESerfKnightTrialAssignment.Stage.ASSIGNED?"Defend your people from the attack":KOMESerfKnightDefenseService.allDead(assignment)?"Return to your Liege":"Defeat the remaining attackers";
            return base + "\nProspective Liege: " + s.getProspectiveLiege().displayName + "\nTrial of Knighthood: " + (trial==null?"Trial":trial.displayName) + "\nObjective: " + objective;
        }
        if (s.getActiveAssignmentKind().length() != 0) return base + "\nCurrent Duty: " + s.getActiveAssignmentKind() + "\nNext: Complete your duty";
        if (s.isTrialCompleted() && !s.hasPartingGift()) return base + "\nNext: Return to your Master for a parting gift";
        if (KOMESerfKnightService.allDutiesComplete(s) && !s.getProspectiveLiege().isSet()) return base + "\nNext: Find a Lord";
        return base + "\nNext: Speak with your Master";
    }

    static String professionText(String base, KOMESerfProfessionAssignment assignment) {
        String q = base + "\nCurrent Duty: Profession\nMaster's Trade: " + assignment.tradeDisplayName;
        for (KOMESerfProfessionAssignment.Requirement material : assignment.requirements) q += "\n" + material.displayName + ": " + material.delivered + " / " + material.required;
        return q;
    }

    private static String vesselName(String value) { return value == null ? "" : value.toLowerCase().replace('_', ' '); }

    public static String findLabel(KOMEPlayerProgression p) {
        return "";
    }

    public static String leaveRelationshipType(KOMEPlayerProgression p) {
        if (p.getCanonicalRank() != KOMEProgressionRank.SERF) return "";
        KOMESerfKnightProgression s = p.getSerfKnightProgression();
        return s.getProspectiveLiege().isSet() && (s.getTrialId().length() == 0 || s.getPhase() == KOMESerfKnightPhase.TRIAL_ASSIGNED) ? "liege" : s.getSerfdomMaster().isSet() ? "master" : "";
    }

    public static String leaveRelationshipLabel(KOMEPlayerProgression p) {
        String type = leaveRelationshipType(p);
        return "master".equals(type) ? "Leave Master" : "liege".equals(type) ? "Leave Liege" : "";
    }

    public static String leaveRelationshipName(KOMEPlayerProgression p) {
        KOMESerfKnightProgression s = p.getSerfKnightProgression();
        String type = leaveRelationshipType(p);
        return "master".equals(type) ? s.getSerfdomMaster().displayName : "liege".equals(type) ? s.getProspectiveLiege().displayName : "";
    }

}
