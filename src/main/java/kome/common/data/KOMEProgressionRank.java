package kome.common.data;

import java.util.Locale;

/** The permanent player-progression ranks. Political offices are deliberately excluded. */
public enum KOMEProgressionRank {
    WANDERER("wanderer", "Wanderer", 0),
    SERF("serf", "Serf", 1),
    KNIGHT("knight", "Knight", 2),
    LORD("lord", "Lord", 3),
    PRINCE("prince", "Prince", 4);

    public final String key;
    public final String displayName;
    public final int order;

    KOMEProgressionRank(String key, String displayName, int order) {
        this.key = key;
        this.displayName = displayName;
        this.order = order;
    }

    /** Parses only the stable machine key; display names and legacy groups are not aliases. */
    public static KOMEProgressionRank forKey(String key) {
        if (key == null) return null;
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        for (KOMEProgressionRank rank : values()) {
            if (rank.key.equals(normalized)) return rank;
        }
        return null;
    }

    static void validateSchema() {
        java.util.HashSet<String> keys = new java.util.HashSet<String>();
        int expectedOrder = 0;
        for (KOMEProgressionRank rank : values()) {
            if (rank.key.length() == 0 || !keys.add(rank.key) || rank.order != expectedOrder++) {
                throw new IllegalStateException("Invalid canonical progression rank schema");
            }
        }
    }
}
