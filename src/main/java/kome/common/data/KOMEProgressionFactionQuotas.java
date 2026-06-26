package kome.common.data;

import lotr.common.fac.LOTRFaction;

public class KOMEProgressionFactionQuotas {
    public static final String[] FACTIONS = new String[] {
        "Harad", "Mordor", "Woodland Realm", "Gondor", "Rohan", "Blue Mountains",
        "Gundabad", "Hobbits", "High Elves", "Dunedain", "Dorwinion",
        "Angmar", "Dol Guldur", "Rhudel", "Bree-land", "Half-trolls", "Fangorn",
        "Morwaith", "Dale", "Dunland", "Durin's Folk", "Isengard", "Lothlorien"
    };

    public static String describe(String factionName) {
        LOTRFaction faction = findFaction(factionName);
        float threshold = getThreshold(faction);
        String type = isElite(faction) ? "elite" : "non-elite";
        return "Earn +/-" + (int) threshold + " alignment with " + factionName + " (" + type + " faction)";
    }

    public static LOTRFaction getAssignedFaction(String assignment) {
        if (assignment == null || assignment.length() == 0) {
            return null;
        }
        String name = assignment;
        int withMarker = name.lastIndexOf(" with ");
        if (withMarker >= 0) {
            name = name.substring(withMarker + 6);
        } else {
            int forMarker = name.lastIndexOf(" for ");
            if (forMarker >= 0) {
                name = name.substring(forMarker + 5);
            }
        }
        int paren = name.indexOf('(');
        if (paren >= 0) {
            name = name.substring(0, paren);
        }
        return findFaction(name.trim());
    }

    public static float getThreshold(LOTRFaction faction) {
        return isElite(faction) ? 750.0f : 500.0f;
    }

    public static boolean isElite(LOTRFaction faction) {
        String name = normalize(faction == null ? "" : faction.codeName());
        return "highelves".equals(name) || "lothlorien".equals(name) || "durinsfolk".equals(name)
            || "gondor".equals(name) || "rohan".equals(name) || "mordor".equals(name)
            || "angmar".equals(name) || "dolguldur".equals(name) || "isengard".equals(name);
    }

    private static LOTRFaction findFaction(String name) {
        return KOMEAlliance.findLotrFaction(name);
    }

    private static String normalize(String value) {
        return KOMEAlliance.normalizeFactionKey(value);
    }
}
