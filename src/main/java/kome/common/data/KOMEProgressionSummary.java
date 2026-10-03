package kome.common.data;

/** Read-only canonical progression-book projection. */
public final class KOMEProgressionSummary {
    private KOMEProgressionSummary() {}

    public static String text(KOMEPlayerProgression p) { return text(p, ""); }
    public static String text(KOMEPlayerProgression p, boolean hasPlayablePledge) { return text(p, hasPlayablePledge ? "Pledged Faction" : ""); }

    public static String text(KOMEPlayerProgression p, String pledgeName) {
        return text(p, pledgeName, pledgeName);
    }

    public static String text(KOMEPlayerProgression p, String pledgeName, String pledgeKey) {
        KOMESerfKnightProgression s = p.getSerfKnightProgression();
        String rank = "Rank: " + KOMEFactionProgressionTitles.title(pledgeKey, p.getCanonicalRank());
        if(KOMEProgressionFactionResolver.missingNativeLiege(pledgeKey)&&p.getCanonicalRank().order<KOMEProgressionRank.KNIGHT.order)rank+="\n"+KOMEKnightCommissionPresentation.unavailableLiege();
        if (p.getCanonicalRank() == KOMEProgressionRank.WANDERER) {
            boolean pledged = pledgeName != null && pledgeName.trim().length() != 0;
            return rank + "\nPledge: " + (pledged ? pledgeName : "None") + "\nNext: " + (pledged ? "Find a Master" : "Pledge to a faction");
        }
        if (p.getCanonicalRank() == KOMEProgressionRank.KNIGHT) return rank + KOMEKnightCommissionPresentation.summary(p,pledgeKey);
        if (p.getCanonicalRank() != KOMEProgressionRank.SERF) return rank
            +(s.hasLiege()?"\nLiege: "+s.getLiege().displayName:"\nLiege: None\nNext: Seek an eligible captain and accept their Liege offer");
        if (!s.getSerfdomMaster().isSet()) return rank + "\nMaster: None\nNext: Find a Master";

        String base = rank + "\nMaster: " + s.getSerfdomMaster().displayName;
        if (s.hasLiege() && s.getTrialId().length() == 0) return base + "\nLiege: " + s.getLiege().displayName + "\nNext: Ask your Liege for a Trial of Standing";
        if ("courier".equals(s.getActiveAssignmentKind())) {
            KOMESerfCourierAssignment a = KOMESerfCourierAssignment.readFromNBT(s.getDuty(KOMESerfKnightDutyType.COURIER).getAssignmentData());
            if (a != null) return base + "\nCurrent Duty: Courier\nMessage: " + (a.stage == KOMESerfCourierAssignment.Stage.DELIVERED ? "Delivered\nNext: Report to your Master" : "Undelivered\nDestination: " + a.destinationName + (a.recipient.isSet() ? "\nRecipient: " + a.recipient.displayName : "\nRecipient: Find them there"));
        }
        if ("provisioning".equals(s.getActiveAssignmentKind())) {
            KOMESerfProvisioningAssignment a = KOMESerfProvisioningAssignment.readFromNBT(s.getDuty(KOMESerfKnightDutyType.PROVISIONING).getAssignmentData());
            if (a != null) {
                return base + "\nCurrent Duty: Provisioning\n" + a.progressList("\n");
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
            if(assignment!=null&&assignment.stage==KOMESerfKnightTrialAssignment.Stage.FAILED) return base + "\nLiege: " + s.getLiege().displayName + "\nTrial of Standing: " + (trial==null?"Trial":trial.displayName) + "\nObjective: This trial is lost. Seek a new Liege.";
            if(assignment!=null&&"escort".equals(assignment.trialId)) objective=assignment.stage==KOMESerfKnightTrialAssignment.Stage.ASSIGNED?"Meet your charge at your Liege's side":assignment.stage==KOMESerfKnightTrialAssignment.Stage.FAILED?"Your charge was lost": "See your charge safely through the journey";
            if(assignment!=null&&"recovery".equals(assignment.trialId)) objective=assignment.data.getBoolean("RecoveryRetrieved")?"Return the recovered item to your Liege":"Recover the lost item";
            if(assignment!=null&&"defense".equals(assignment.trialId)) objective=assignment.stage==KOMESerfKnightTrialAssignment.Stage.ASSIGNED?"Defend your people from the attack":KOMESerfKnightDefenseService.allDead(assignment)?"Return to your Liege":"Defeat the remaining attackers";
            return base + "\nLiege: " + s.getLiege().displayName + "\nTrial of Standing: " + (trial==null?"Trial":trial.displayName) + "\nObjective: " + objective;
        }
        if (s.getActiveAssignmentKind().length() != 0) return base + "\nCurrent Duty: " + s.getActiveAssignmentKind() + "\nNext: Complete your duty";
        if (s.isTrialCompleted() && !s.hasPartingGift()) return base + "\nNext: Return to your Master for a parting gift";
        if (KOMESerfKnightService.allDutiesComplete(s) && !s.hasLiege()) return base + "\nNext: Seek an eligible prospective Liege";
        return base + "\nNext: Speak with your Master";
    }
    public static String text(KOMEPlayerProgression p,String pledgeName,String pledgeKey,double alignment){
        String result=text(p,pledgeName,pledgeKey);
        if(p.getCanonicalRank()==KOMEProgressionRank.KNIGHT)result+="\n"+KOMELordshipTrialPresentation.standing(p,alignment,pledgeKey)+"\n"+KOMELordshipTrialPresentation.status(p,alignment,pledgeKey);
        return result;
    }

    static String professionText(String base, KOMESerfProfessionAssignment assignment) {
        String q = base + "\nCurrent Duty: Profession\nMaster's Trade: " + assignment.tradeDisplayName;
        for (KOMESerfProfessionAssignment.Requirement material : assignment.requirements) q += "\n" + material.displayName + ": " + material.delivered + " / " + material.required;
        return q;
    }


    public static String findLabel(KOMEPlayerProgression p) {
        return "";
    }

    public static String leaveRelationshipType(KOMEPlayerProgression p) {
        if (p.getCanonicalRank().order < KOMEProgressionRank.SERF.order) return "";
        KOMESerfKnightProgression s = p.getSerfKnightProgression();

        if (p.getCanonicalRank().order >= KOMEProgressionRank.KNIGHT.order) {
            return s.hasLiege() ? "liege" : "";
        }

        return s.hasLiege()
            && (s.getTrialId().length() == 0
                || s.getPhase() == KOMESerfKnightPhase.TRIAL_ASSIGNED)
            ? "liege"
            : s.getSerfdomMaster().isSet() ? "master" : "";
    }

    public static String leaveRelationshipLabel(KOMEPlayerProgression p) {
        String type = leaveRelationshipType(p);
        return "master".equals(type) ? "Leave Master" : "liege".equals(type) ? "Leave Liege" : "";
    }

    public static String leaveRelationshipName(KOMEPlayerProgression p) {
        KOMESerfKnightProgression s = p.getSerfKnightProgression();
        String type = leaveRelationshipType(p);
        return "master".equals(type) ? s.getSerfdomMaster().displayName : "liege".equals(type) ? s.getLiege().displayName : "";
    }

}
