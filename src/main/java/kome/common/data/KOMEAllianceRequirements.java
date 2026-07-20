package kome.common.data;

import java.util.Locale;

/** Exact server-side alliance requirement policy and predictable difficulty scaling. */
public final class KOMEAllianceRequirements {
    public static final String EASY = "easy";
    public static final String STANDARD = "standard";
    public static final String HARD = "hard";

    private static final int[][] ITEM_STACKS = {
        {4, 6},       // Civil
        {8, 12},      // Trade
        {6, 12, 20}   // Military
    };
    private static final int[][] ACTIVITY = {
        {0, 100},       // Civil: cumulative allied trades
        {50, 250},      // Trade: cumulative allied trades at both tiers
        {50, 500, 1000} // Military: cumulative eligible kills
    };
    private static final int[][] POPULATION = {
        {0, 0},
        {0, 0},
        {50, 150, 300}
    };

    private KOMEAllianceRequirements() {
    }

    public static String normalizeDifficulty(String value) {
        String key = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return EASY.equals(key) || HARD.equals(key) ? key : STANDARD;
    }

    public static double multiplier(String difficulty) {
        String key = normalizeDifficulty(difficulty);
        return EASY.equals(key) ? 0.65D : HARD.equals(key) ? 1.50D : 1.00D;
    }

    public static int standardItemStackEquivalents(String type, int tier) {
        return lookup(ITEM_STACKS, type, tier);
    }

    public static int standardActivity(String type, int tier) {
        return lookup(ACTIVITY, type, tier);
    }

    public static int standardPopulation(String type, int tier) {
        return lookup(POPULATION, type, tier);
    }

    public static int scaled(int baseValue, String difficulty) {
        if (baseValue <= 0) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(baseValue * multiplier(difficulty)));
    }

    public static int weightedItemQuantity(int stackEquivalents, int effortWeight, String difficulty) {
        int weight = normalizeWeight(effortWeight);
        double ordinaryItems = Math.max(0, stackEquivalents) * 64.0D * multiplier(difficulty);
        return ordinaryItems <= 0.0D ? 0 : Math.max(1, (int) Math.ceil(ordinaryItems / weight));
    }

    public static int normalizeWeight(int value) {
        return value >= 64 ? 64 : value >= 32 ? 32 : value >= 16 ? 16
            : value >= 8 ? 8 : value >= 4 ? 4 : value >= 2 ? 2 : 1;
    }

    public static boolean isSupportedWeight(int value) {
        return value == 1 || value == 2 || value == 4 || value == 8
            || value == 16 || value == 32 || value == 64;
    }

    public static long parseDurationMillis(String value) {
        if (value == null || value.trim().length() < 2) {
            throw new IllegalArgumentException("Duration must use s, m, h, or d.");
        }
        String text = value.trim().toLowerCase(Locale.ROOT);
        char suffix = text.charAt(text.length() - 1);
        long unit = suffix == 's' ? 1000L : suffix == 'm' ? 60000L : suffix == 'h' ? 3600000L
            : suffix == 'd' ? 86400000L : -1L;
        if (unit < 0L) {
            throw new IllegalArgumentException("Duration must use s, m, h, or d.");
        }
        long amount;
        try {
            amount = Long.parseLong(text.substring(0, text.length() - 1));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid duration: " + value);
        }
        if (amount <= 0L || amount > Long.MAX_VALUE / unit) {
            throw new IllegalArgumentException("Duration is outside the supported positive range.");
        }
        return amount * unit;
    }

    public static String key(String type, int tier, String kind) {
        return KOMEAlliance.normalizeType(type) + ".t" + Math.max(1, tier) + "." + (kind == null ? "" : kind.trim().toLowerCase(Locale.ROOT));
    }

    private static int lookup(int[][] values, String type, int tier) {
        int track = KOMEAlliance.CIVIL.equals(KOMEAlliance.normalizeType(type)) ? 0
            : KOMEAlliance.TRADE.equals(KOMEAlliance.normalizeType(type)) ? 1
            : KOMEAlliance.MILITARY.equals(KOMEAlliance.normalizeType(type)) ? 2 : -1;
        if (track < 0 || tier < 1 || tier > values[track].length) {
            return 0;
        }
        return values[track][tier - 1];
    }
}
