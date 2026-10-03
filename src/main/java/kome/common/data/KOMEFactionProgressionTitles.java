package kome.common.data;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Player-facing faction-specific names for the permanent KOME progression ranks.
 *
 * Canonical save ranks remain WANDERER / SERF / KNIGHT / LORD / PRINCE.
 * This class is presentation-only and must never be used as save-state authority.
 */
public final class KOMEFactionProgressionTitles {
    private static final Map<String, TitleSet> BY_FACTION;

    static {
        Map<String, TitleSet> titles = new HashMap<String, TitleSet>();

        put(titles, "hobbit",
            "Hayward", "Bounder", "Shirriff", "Chief Shirriff");

        put(titles, "bree",
            "Townsman", "Trustee", "Champion", "Captain");

        put(titles, "dunedain",
            "Warden", "Ranger", "Roquen", "Captain");

        put(titles, "bluemountains",
            "Warden", "Axebearer", "Captain", "Noble");

        put(titles, "highelves",
            "Mahtar", "Tercáno", "Hesto", "Cáno Noldoron");

        put(titles, "gundabad",
            "Raider", "Ravager", "Scourge", "Warlord");

        put(titles, "angmar",
            "Kinsman", "Warrior", "Champion", "Warlord");

        put(titles, "woodelf",
            "Marchwarden", "Herald", "Captain", "Arphen Eryn");

        put(titles, "dolguldur",
            "Brigand", "Despoiler", "Captain", "Khamûl's Lieutenant");

        put(titles, "dale",
            "Soldier", "Herald", "Captain", "Marshal");

        put(titles, "durinsfolk",
            "Oathfriend", "Axebearer", "Commander", "Lord");

        put(titles, "lothlorien",
            "Glandirron", "Méthor", "Hest Lórien", "Arphen Lórien");

        put(titles, "dunland",
            "Kinsman", "Warrior", "Avenger", "Warlord");

        put(titles, "isengard",
            "Soldier", "Berserker", "Hand of Isengard", "Captain of the Hand");

        put(titles, "fangorn",
            "Friend", "Treeherd", "Master", "Elder");

        put(titles, "rohan",
            "Eorling-at-Arms", "Rider of Rohan", "Captain", "Marshal");

        put(titles, "gondor",
            "Gondorian-at-Arms", "Knight", "Captain", "Lord");

        put(titles, "mordor",
            "Brigand", "Despoiler", "Captain", "Commander of Lugbúrz");

        put(titles, "dorwinion",
            "Vinehand", "Vintner Guard", "Captain", "Master");

        put(titles, "rhudel",
            "Clansman", "Warrior", "Golden Easterling", "Warlord");

        put(titles, "harad",
            "Kinsman", "Warrior", "Serpent Guard", "Warlord");

        put(titles, "morwaith",
            "Kinsman", "Hunter", "Lion-warrior", "Lion-chief");

        put(titles, "taurethrim",
            "Forest-Man", "Warrior", "Champion", "Warlord");

        put(titles, "halftroll",
            "Troll-kin", "Warrior", "Raider", "Warlord");

        BY_FACTION = Collections.unmodifiableMap(titles);
    }

    private KOMEFactionProgressionTitles() {
    }

    public static String title(String factionKey, KOMEProgressionRank rank) {
        KOMEProgressionRank canonical = rank == null
            ? KOMEProgressionRank.WANDERER
            : rank;

        if (canonical == KOMEProgressionRank.WANDERER) {
            return canonical.displayName;
        }

        TitleSet titles = BY_FACTION.get(KOMEAlliance.normalizeFactionKey(factionKey));
        if (titles == null) {
            return canonical.displayName;
        }

        switch (canonical) {
            case SERF:
                return titles.tierOne;
            case KNIGHT:
                return titles.tierTwo;
            case LORD:
                return titles.tierThree;
            case PRINCE:
                return titles.tierFour;
            default:
                return canonical.displayName;
        }
    }

    public static String[] ladder(String factionKey) {
        return new String[] {
            title(factionKey, KOMEProgressionRank.SERF),
            title(factionKey, KOMEProgressionRank.KNIGHT),
            title(factionKey, KOMEProgressionRank.LORD),
            title(factionKey, KOMEProgressionRank.PRINCE)
        };
    }

    private static void put(Map<String, TitleSet> titles, String factionKey,
            String tierOne, String tierTwo, String tierThree, String tierFour) {
        titles.put(KOMEAlliance.normalizeFactionKey(factionKey),
            new TitleSet(tierOne, tierTwo, tierThree, tierFour));
    }

    private static final class TitleSet {
        private final String tierOne;
        private final String tierTwo;
        private final String tierThree;
        private final String tierFour;

        private TitleSet(String tierOne, String tierTwo, String tierThree, String tierFour) {
            this.tierOne = tierOne;
            this.tierTwo = tierTwo;
            this.tierThree = tierThree;
            this.tierFour = tierFour;
        }
    }
}