package kome.common.data;

import lotr.common.LOTRLevelData;
import lotr.common.fac.LOTRFaction;
import net.minecraft.entity.player.EntityPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Builds the schema-7 records consumed by the single relationship screen.
 * Retired per-track presentation records are intentionally not emitted.
 */
public class KOMEAllianceRecordBuilder {
    private static final Set<UUID> operatorViewers = Collections.synchronizedSet(new HashSet<UUID>());

    public static void setOperatorView(EntityPlayer viewer, boolean requested) {
        if (viewer == null) {
            return;
        }
        UUID viewerId = kome.common.KOMEReflection.getEntityUUID(viewer);
        boolean operator = viewer.canCommandSenderUseCommand(2, "alliance");
        if (resolveOperatorView(operator, requested)) {
            operatorViewers.add(viewerId);
        } else {
            operatorViewers.remove(viewerId);
        }
    }

    public static void clearOperatorView(EntityPlayer viewer) {
        if (viewer != null) {
            operatorViewers.remove(kome.common.KOMEReflection.getEntityUUID(viewer));
        }
    }

    public static void resetSessionState() {
        operatorViewers.clear();
    }

    static boolean resolveOperatorView(boolean operator, boolean requested) {
        return operator && requested;
    }

    static boolean includeViewerRecord(boolean operatorView, boolean participant) {
        return operatorView || participant;
    }

    public static List build(KOMEWorldData data, EntityPlayer viewer) {
        List lines = new ArrayList();
        return buildCanonical(data, viewer);
        /*
        List stageLines = new ArrayList();
        String viewerFactionKey = getViewerFactionKey(data, viewer);
        boolean operator = viewer == null || viewer.canCommandSenderUseCommand(2, "alliance");
        boolean operatorView = viewer == null || resolveOperatorView(operator,
            operatorViewers.contains(kome.common.KOMEReflection.getEntityUUID(viewer)));
        for (KOMEAlliance alliance : data.alliances.values()) {
            if (alliance == null || !alliance.hasAnyAlliance() && !alliance.hasRecoverableGoods()) {
                continue;
            }
            if (includeViewerRecord(operatorView, alliance.involves(viewerFactionKey))) {
                stageLines.add(formatStageAlliance(data, alliance, viewerFactionKey, viewer, operator));
            }
        }
        Collections.sort(stageLines);
        lines.add("SUMMARY\t" + stageLines.size());
        lines.add("VIEWER\t" + viewerFactionKey + "\t" + getViewerFactionName(data, viewer)
            + "\t" + flag(isViewerKing(data, viewer, viewerFactionKey))
            + "\t" + flag(data.hasFactionKing(viewerFactionKey))
            + "\t" + flag(operator) + "\t" + flag(operatorView));
        for (LOTRFaction faction : LOTRFaction.values()) {
            if (faction == null || !faction.isPlayableAlignmentFaction()) {
                continue;
            }
            String key = KOMEAlliance.normalizeFactionKey(faction.codeName());
            if (viewer != null && viewerFactionKey.length() > 0 && !key.equals(viewerFactionKey)) {
                boolean viewerKing = isViewerKing(data, viewer, viewerFactionKey);
                boolean receiverKing = data.hasFactionKing(key);
                KOMEAllianceAuthority.Decision decision = KOMEAllianceAuthority.decideRequestAlliance(
                    KOMEAlliance.CIVIL, viewerFactionKey, key, viewerFactionKey, viewerKing,
                    data.hasFactionKing(viewerFactionKey), receiverKing,
                    KOMEAllianceAuthority.getDefaultRelation(viewerFactionKey, key));
                lines.add("REQUEST_OPTION_V2\t" + key + "\t" + displayFaction(key) + "\t"
                    + flag(decision.allowed) + "\t" + flag(receiverKing) + "\t"
                    + flag(decision.automaticAcceptance) + "\t" + decision.automaticStage + "\t"
                    + safe(decision.reason));
            }
            if (data.hasFactionKing(key)
                    && (operatorView || key.equals(viewerFactionKey)
                    || isVisiblePartner(data, viewerFactionKey, key))) {
                lines.add("KING\t" + key + "\t" + data.getFactionKingName(key));
            }
        }
        lines.addAll(stageLines);
        return lines;
        */
    }

    private static List buildCanonical(KOMEWorldData data, EntityPlayer viewer) {
        List lines=new ArrayList(); String viewerFactionKey=getViewerFactionKey(data,viewer); boolean operator=viewer==null||viewer.canCommandSenderUseCommand(2,"alliance");
        lines.add("SUMMARY\t"+data.canonicalDiplomacyRecords.size()); lines.add("VIEWER\t"+viewerFactionKey+"\t"+getViewerFactionName(data,viewer)+"\t"+flag(isViewerKing(data,viewer,viewerFactionKey))+"\t"+flag(data.hasFactionKing(viewerFactionKey))+"\t"+flag(operator)+"\t"+flag(operator));
        for(KOMEDiplomacyRecord record:KOMEDiplomacyService.records(data).values()) { if(!operator&&!record.factionA.equals(viewerFactionKey)&&!record.factionB.equals(viewerFactionKey))continue; boolean canAccept=viewer!=null&&record.pendingTarget!=null&&data.isFactionKing(record.receivingFaction,kome.common.KOMEReflection.getEntityUUID(viewer)); lines.add("DIPLOMACY_RELATION\t"+record.key()+"\t"+record.factionA+"\t"+record.factionB+"\t"+record.relation.key+"\t"+flag(record.pendingTarget!=null)+"\t"+(record.pendingTarget==null?"":record.pendingTarget.key)+"\t"+record.requestingFaction+"\t"+record.receivingFaction+"\t"+flag(canAccept)+"\t"+record.lastUpdatedBy+"\t"+record.updatedAt); }
        if(viewer!=null) for(LOTRFaction faction:LOTRFaction.values()) if(faction!=null&&faction.isPlayableAlignmentFaction()){String key=KOMEAlliance.normalizeFactionKey(faction.codeName());if(!key.equals(viewerFactionKey)){KOMEDiplomacyRelation current=KOMEDiplomacyService.getRelation(data,viewerFactionKey,key);boolean receiver=data.hasFactionKing(key);lines.add("DIPLOMACY_REQUEST_OPTION\t"+key+"\t"+displayFaction(key)+"\t"+current.key+"\t"+flag(receiver)+"\t"+flag(receiver&&current.rank()<KOMEDiplomacyRelation.FRIENDS.rank())+"\t"+flag(receiver&&current.rank()<KOMEDiplomacyRelation.ALLIES.rank())+"\t"+(receiver?"":"That faction has no recognized King to accept diplomacy."));}}
        return lines;
    }

    public static List build(KOMEWorldData data) {
        return build(data, null);
    }

    private static String formatStageAlliance(KOMEWorldData data, KOMEAlliance alliance,
            String viewerFaction, EntityPlayer viewer, boolean admin) {
        String side = alliance.involves(viewerFaction) ? viewerFaction : alliance.factionA;
        String partner = alliance.getOtherFaction(side);
        KOMEAllianceStageProgress progress = alliance.getStageProgress(side);
        KOMEAllianceStageProgress partnerProgress = alliance.getStageProgress(partner);
        int stage = alliance.getRelationshipStatus() == KOMEAllianceTrackStatus.ACTIVE
            ? alliance.getFactionStage(side) : KOMEAlliance.NONE;
        int otherStage = alliance.getRelationshipStatus() == KOMEAllianceTrackStatus.ACTIVE
            ? alliance.getFactionStage(partner) : KOMEAlliance.NONE;
        int nextStage = stage >= 0 && stage < 4 ? stage + 1 : 0;
        String quotaName = "";
        int quotaRequired = 0;
        int quotaDelivered = 0;
        if (nextStage > 0) {
            String id = KOMEAllianceProgressionService.stageRequirementId(nextStage);
            KOMEAllianceQuotaPool.Requirement quota =
                KOMEAllianceQuotaPool.parse(alliance.getAssignment(side, id));
            if (quota != null) {
                quotaName = quota.displayName;
                quotaRequired = quota.requiredUnits;
            }
            quotaDelivered = alliance.getDelivered(side, id);
        }
        int fixedProgress = 1;
        int fixedRequired = 1;
        if (nextStage == 3) {
            fixedProgress = KOMEBuildService.approvedHalfHoursForPartner(data, side, partner);
            fixedRequired = Math.max(1, data.allianceStageThreeRequiredHalfHours);
        } else if (nextStage == 4) {
            fixedProgress = progress != null && progress.qualifyingDeploymentAtMillis > 0L ? 1 : 0;
        }
        boolean canManage = admin || viewer != null
            && data.isFactionKing(side, kome.common.KOMEReflection.getEntityUUID(viewer));
        String sharedRelation = alliance.getRelationshipStatus() == KOMEAllianceTrackStatus.ACTIVE
            ? sharedRelationName(alliance.getSharedRelationStage()) : "Default";
        return "STAGE_RELATION\t" + alliance.getPairKey() + "\t"
            + alliance.factionA + "\t" + alliance.factionB + "\t"
            + displayFaction(alliance.factionA) + "\t" + displayFaction(alliance.factionB) + "\t"
            + side + "\t" + partner + "\t" + stage + "\t" + otherStage + "\t"
            + alliance.getRelationshipStatus().key + "\t"
            + safe(alliance.getRequestedBy(KOMEAlliance.CIVIL)) + "\t"
            + safe(alliance.getPendingReceiver(KOMEAlliance.CIVIL)) + "\t" + sharedRelation + "\t"
            + flag(canManage) + "\t" + flag(data.hasFactionKing(side)) + "\t"
            + flag(data.hasFactionKing(partner)) + "\t" + nextStage + "\t"
            + safe(quotaName) + "\t" + quotaRequired + "\t" + quotaDelivered + "\t"
            + fixedProgress + "\t" + fixedRequired + "\t"
            + safe(nextStage > 0
                ? KOMEAllianceProgressionService.fixedMilestoneDescription(data, alliance, side, nextStage)
                : "All four stages claimed.") + "\t"
            + flag(progress != null && progress.produceMerchantSlotUnlocked) + "\t"
            + safe(progress == null ? "" : progress.qualifyingWarId) + "\t"
            + safe(progress == null ? "" : progress.qualifyingCompanyId) + "\t"
            + flag(partnerProgress != null && partnerProgress.produceMerchantSlotUnlocked) + "\t"
            + safe(alliance.lastUpdatedBy) + "\t" + alliance.updatedWorldTime;
    }

    private static String sharedRelationName(int stage) {
        if (stage >= 3) return "Allies";
        if (stage >= 2) return "Friends";
        return "Neutral";
    }

    private static boolean isVisiblePartner(KOMEWorldData data, String viewerFaction, String candidate) {
        if (viewerFaction == null || viewerFaction.length() == 0) {
            return false;
        }
        KOMEAlliance alliance = data.getAlliance(viewerFaction, candidate, false);
        return alliance != null && (alliance.hasAnyAlliance() || alliance.hasRecoverableGoods());
    }

    private static String safe(String value) {
        return value == null ? "" : value.replace('\t', ' ');
    }

    private static String flag(boolean value) {
        return value ? "1" : "0";
    }

    private static String displayFaction(String key) {
        return KOMEAlliance.displayFactionName(key);
    }

    private static String getViewerFactionKey(KOMEWorldData data, EntityPlayer viewer) {
        LOTRFaction faction = getViewerFaction(data, viewer);
        return faction == null ? "" : KOMEAlliance.normalizeFactionKey(faction.codeName());
    }

    private static String getViewerFactionName(KOMEWorldData data, EntityPlayer viewer) {
        LOTRFaction faction = getViewerFaction(data, viewer);
        return faction == null ? "No pledged faction" : faction.factionName();
    }

    private static LOTRFaction getViewerFaction(KOMEWorldData data, EntityPlayer viewer) {
        if (viewer == null) {
            return null;
        }
        LOTRFaction pledge = LOTRLevelData.getData(viewer).getPledgeFaction();
        if (pledge != null) {
            return pledge;
        }
        KOMEPlayerProgression progression =
            data.getProgression(kome.common.KOMEReflection.getEntityUUID(viewer));
        return findFaction(progression.getPledgedLordFaction());
    }

    private static boolean isViewerKing(KOMEWorldData data, EntityPlayer viewer, String factionKey) {
        return viewer != null && factionKey != null && factionKey.length() > 0
            && data.isFactionKing(factionKey, kome.common.KOMEReflection.getEntityUUID(viewer));
    }

    private static LOTRFaction findFaction(String value) {
        LOTRFaction direct = LOTRFaction.forName(value);
        if (direct != null) {
            return direct;
        }
        String normalized = KOMEAlliance.normalizeFactionKey(value);
        for (LOTRFaction faction : LOTRFaction.values()) {
            if (faction != null && faction.isPlayableAlignmentFaction()
                    && (KOMEAlliance.normalizeFactionKey(faction.codeName()).equals(normalized)
                    || KOMEAlliance.normalizeFactionKey(faction.factionName()).equals(normalized))) {
                return faction;
            }
        }
        return null;
    }
}
