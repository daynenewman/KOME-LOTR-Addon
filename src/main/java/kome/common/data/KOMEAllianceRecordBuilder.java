package kome.common.data;

import lotr.common.fac.LOTRFaction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class KOMEAllianceRecordBuilder {
    public static List build(KOMEWorldData data) {
        List lines = new ArrayList();
        List allianceLines = new ArrayList();
        for (KOMEAlliance alliance : data.alliances.values()) {
            if (alliance == null || !alliance.hasAnyAlliance()) {
                continue;
            }
            allianceLines.add(formatAlliance(alliance));
        }
        Collections.sort(allianceLines);
        lines.add("SUMMARY\t" + allianceLines.size());
        lines.addAll(allianceLines);
        return lines;
    }

    private static String formatAlliance(KOMEAlliance alliance) {
        return "ALLIANCE\t"
            + alliance.factionA + "\t"
            + alliance.factionB + "\t"
            + displayFaction(alliance.factionA) + "\t"
            + displayFaction(alliance.factionB) + "\t"
            + alliance.civilTier + "\t"
            + alliance.militaryTier + "\t"
            + alliance.tradeTier + "\t"
            + alliance.lastUpdatedBy + "\t"
            + alliance.updatedWorldTime;
    }

    private static String displayFaction(String key) {
        LOTRFaction faction = LOTRFaction.forName(key);
        return faction == null ? key : faction.factionName();
    }
}
