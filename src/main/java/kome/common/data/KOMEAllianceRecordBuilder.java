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
        return buildCanonical(data, viewer);
    }
    private static List buildCanonical(KOMEWorldData data, EntityPlayer viewer) {
        List lines=new ArrayList(); String viewerFactionKey=getViewerFactionKey(data,viewer); boolean operator=viewer==null||viewer.canCommandSenderUseCommand(2,"alliance");
        lines.add("SUMMARY\t"+data.canonicalDiplomacyRecords.size()); lines.add("VIEWER\t"+viewerFactionKey+"\t"+getViewerFactionName(data,viewer)+"\t"+flag(isViewerKing(data,viewer,viewerFactionKey))+"\t"+flag(data.hasFactionKing(viewerFactionKey))+"\t"+flag(operator)+"\t"+flag(operator));
        for(KOMEDiplomacyRecord record:KOMEDiplomacyService.records(data).values()) { if(!operator&&!record.factionA.equals(viewerFactionKey)&&!record.factionB.equals(viewerFactionKey))continue; boolean canAccept=viewer!=null&&record.pendingTarget!=null&&KOMERulerAuthorization.canActAsRuler(data, record.receivingFaction,kome.common.KOMEReflection.getEntityUUID(viewer)); boolean canCancel=record.pendingTarget!=null&&(operator||(viewer!=null&&KOMERulerAuthorization.canActAsRuler(data, record.requestingFaction,kome.common.KOMEReflection.getEntityUUID(viewer)))); lines.add("DIPLOMACY_RELATION\t"+record.key()+"\t"+record.factionA+"\t"+record.factionB+"\t"+record.relation.key+"\t"+flag(record.pendingTarget!=null)+"\t"+(record.pendingTarget==null?"":record.pendingTarget.key)+"\t"+record.requestingFaction+"\t"+record.receivingFaction+"\t"+flag(canAccept)+"\t"+flag(canCancel)+"\t"+record.lastUpdatedBy+"\t"+record.updatedAt); }
        if(viewer!=null) for(LOTRFaction faction:LOTRFaction.values()) if(faction!=null&&faction.isPlayableAlignmentFaction()){String key=KOMEAlliance.normalizeFactionKey(faction.codeName());if(!key.equals(viewerFactionKey)){KOMEDiplomacyRelation current=KOMEDiplomacyService.getRelation(data,viewerFactionKey,key);boolean receiver=data.hasFactionKing(key);lines.add("DIPLOMACY_REQUEST_OPTION\t"+key+"\t"+displayFaction(key)+"\t"+current.key+"\t"+flag(receiver)+"\t"+flag(receiver&&current.rank()<KOMEDiplomacyRelation.FRIENDS.rank())+"\t"+flag(receiver&&current.rank()<KOMEDiplomacyRelation.ALLIES.rank())+"\t"+(receiver?"":"That faction has no recognized King to accept diplomacy."));}}
        return lines;
    }

    public static List build(KOMEWorldData data) {
        return build(data, null);
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
            && KOMERulerAuthorization.canActAsRuler(data, factionKey, kome.common.KOMEReflection.getEntityUUID(viewer));
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
