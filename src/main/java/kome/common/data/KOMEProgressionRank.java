package kome.common.data;

import java.util.Locale;

/** The permanent player-progression ranks. Political offices are deliberately excluded. */
public enum KOMEProgressionRank {
    COMMONER("commoner", "Commoner", 1),
    KNIGHT("knight", "Knight", 2),
    LORD("lord", "Lord", 3),
    PRINCE("prince", "Prince", 4);

    /** Source compatibility for existing service code; neither is a separate rank. */
    @Deprecated public static final KOMEProgressionRank WANDERER = COMMONER;
    @Deprecated public static final KOMEProgressionRank SERF = COMMONER;

    public final String key;
    public final String displayName;
    public final int order;

    KOMEProgressionRank(String key, String displayName, int order) {
        this.key = key;
        this.displayName = displayName;
        this.order = order;
    }

    /** Legacy starting keys migrate to Commoner; King remains an independent office. */
    public static KOMEProgressionRank forKey(String key) {
        if (key == null) return null;
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        if ("wanderer".equals(normalized) || "serf".equals(normalized)) return COMMONER;
        for (KOMEProgressionRank rank : values()) {
            if (rank.key.equals(normalized)) return rank;
        }
        return null;
    }

    static void validateSchema() {
        java.util.HashSet<String> keys = new java.util.HashSet<String>();
        int expectedOrder = 1;
        for (KOMEProgressionRank rank : values()) {
            if (rank.key.length() == 0 || !keys.add(rank.key) || rank.order != expectedOrder++) {
                throw new IllegalStateException("Invalid canonical progression rank schema");
            }
        }
    }
}
