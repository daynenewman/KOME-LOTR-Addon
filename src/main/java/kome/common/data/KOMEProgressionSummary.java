package kome.common.data;

/** Read-only canonical progression-book projection. */
public final class KOMEProgressionSummary {
    private KOMEProgressionSummary() {}
    public static String courierCopy(KOMEPlayerProgression p,long now){
        KOMESerfKnightProgression s=p.getSerfKnightProgression();
        if(!"courier".equals(s.getActiveAssignmentKind()))return "";
        KOMESerfCourierAssignment a=KOMESerfCourierAssignment.readFromNBT(s.getDuty(KOMESerfKnightDutyType.COURIER).getAssignmentData());
        if(a==null||a.stage==KOMESerfCourierAssignment.Stage.DELIVERED)return "";
        long remaining=a.replacementTicksRemaining(now);
        return "\nLetter: "+(a.replacements>=3?"No copies left":remaining>0?"Speak with your Master in "+KOMECourierIssuance.cooldownText(remaining):"Ask your Master for another copy");
    }

    public static String text(KOMEPlayerProgression p) { return text(p, ""); }
    public static String text(KOMEPlayerProgression p, boolean hasPlayablePledge) { return text(p, hasPlayablePledge ? "Pledged Faction" : ""); }

    public static String text(KOMEPlayerProgression p, String pledgeName) {
        return text(p, pledgeName, pledgeName);
    }

    public static String text(KOMEPlayerProgression p,String pledgeName,String pledgeKey){return text(p,pledgeName,pledgeKey,0D);}
    public static String text(KOMEPlayerProgression p,String pledgeName,String pledgeKey,double alignment){
        KOMEProgressionRankSummary summary=KOMEProgressionRankSummary.project(p,alignment,pledgeKey);
        StringBuilder text=new StringBuilder("Rank: ").append(summary.currentRank).append("\nPledge: ").append(pledgeName==null||pledgeName.isEmpty()?"None":pledgeName);
        if(summary.hasActivity())text.append("\n").append(summary.activityHeading).append(": ").append(summary.activityTitle).append("\n").append(summary.activityObjective);
        for(KOMEServiceRecognition.Record record:p.getServiceRecognition().records())text.append("\nFormer superior: ").append(record.issuer.displayName).append(" - ").append(record.accomplishments);
        return text.toString();
    }
    static String professionText(String base, KOMESerfProfessionAssignment assignment) {
        String q = base + "\nCurrent duty: Trade assistance\nMaster's trade: " + assignment.tradeDisplayName;
        for (KOMESerfProfessionAssignment.Requirement material : assignment.requirements) q += "\n" + KOMEProgressionGoodsRules.name(material.requestedStack(),material.displayName) + ": " + material.delivered + " / " + material.required;
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
        return "master".equals(type) ? "Leave Master service" : "liege".equals(type) ? "Leave Liege service" : "";
    }

    public static String leaveRelationshipName(KOMEPlayerProgression p) {
        KOMESerfKnightProgression s = p.getSerfKnightProgression();
        String type = leaveRelationshipType(p);
        return "master".equals(type) ? s.getSerfdomMaster().displayName : "liege".equals(type) ? s.getLiege().displayName : "";
    }

}
