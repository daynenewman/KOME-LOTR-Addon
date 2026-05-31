package kome.common.data;

import lotr.common.LOTRLevelData;
import lotr.common.fac.LOTRFaction;
import net.minecraft.entity.player.EntityPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class KOMEAllianceRecordBuilder {
    public static List build(KOMEWorldData data, EntityPlayer viewer) {
        List lines = new ArrayList();
        List allianceLines = new ArrayList();
        for (KOMEAlliance alliance : data.alliances.values()) {
            if (alliance == null || !alliance.hasAnyAlliance()) {
                continue;
            }
            allianceLines.add(formatAlliance(data, alliance));
        }
        Collections.sort(allianceLines);
        lines.add("SUMMARY\t" + allianceLines.size());
        String viewerFactionKey = getViewerFactionKey(data, viewer);
        lines.add("VIEWER\t" + viewerFactionKey + "\t" + getViewerFactionName(data, viewer)
            + "\t" + (isViewerKing(data, viewer, viewerFactionKey) ? "1" : "0")
            + "\t" + (data.hasFactionKing(viewerFactionKey) ? "1" : "0"));
        lines.addAll(allianceLines);
        return lines;
    }

    public static List build(KOMEWorldData data) {
        return build(data, null);
    }

    private static String formatAlliance(KOMEWorldData data, KOMEAlliance alliance) {
        return "ALLIANCE\t"
            + alliance.factionA + "\t"
            + alliance.factionB + "\t"
            + displayFaction(alliance.factionA) + "\t"
            + displayFaction(alliance.factionB) + "\t"
            + alliance.civilTier + "\t"
            + alliance.militaryTier + "\t"
            + alliance.tradeTier + "\t"
            + alliance.lastUpdatedBy + "\t"
            + alliance.updatedWorldTime + "\t"
            + safe(alliance.getAssignment("military.food")) + "\t"
            + alliance.getDelivered("military.food") + "\t"
            + safe(alliance.getAssignment("trade.food")) + "\t"
            + alliance.getDelivered("trade.food") + "\t"
            + alliance.getDelivered("civil.trade") + "\t"
            + alliance.getDelivered("military.kills") + "\t"
            + alliance.getDelivered("trade.t2.coins") + "\t"
            + data.getFactionFarmerPop(alliance.factionA);
    }

    private static String safe(String value) {
        return value == null ? "" : value.replace('\t', ' ');
    }

    private static String displayFaction(String key) {
        LOTRFaction faction = findFaction(key);
        return faction == null ? key : faction.factionName();
    }

    private static String getViewerFactionKey(KOMEWorldData data, EntityPlayer viewer) {
        LOTRFaction faction = getViewerFaction(data, viewer);
        return faction == null ? "" : faction.codeName();
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
        KOMEPlayerProgression progression = data.getProgression(kome.common.KOMEReflection.getEntityUUID(viewer));
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
