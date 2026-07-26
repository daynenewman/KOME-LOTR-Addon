package kome.common.data;

/**
 * Overflow-safe presentation math shared by Tile Command population graphs.
 * It does not mutate or recompute authoritative population pools.
 */
public final class KOMEPopulationGraph {
    private KOMEPopulationGraph() {
    }

    public static Segments segments(int physical, int usable, int used) {
        int safePhysical = nonNegative(physical);
        int safeUsable = Math.min(safePhysical, nonNegative(usable));
        int safeUsed = Math.min(safeUsable, nonNegative(used));
        return new Segments(safePhysical, safeUsable, safeUsed,
            safeUsable - safeUsed, safePhysical - safeUsable);
    }

    public static String accessLabel(boolean controllerOwned, int physical, int usable) {
        Segments values = segments(physical, usable, 0);
        if (values.physical > 0 && values.usable == 0) {
            return "0% Owner Access While Occupied";
        }
        return controllerOwned ? "100% Controller-Owned Access" : "50% Captured Access";
    }

    public static int saturatingAdd(int current, int value) {
        long result = (long) nonNegative(current) + nonNegative(value);
        return result > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) result;
    }

    private static int nonNegative(int value) {
        return Math.max(0, value);
    }

    public static final class Segments {
        public final int physical;
        public final int usable;
        public final int used;
        public final int available;
        public final int inaccessible;

        private Segments(int physical, int usable, int used, int available, int inaccessible) {
            this.physical = physical;
            this.usable = usable;
            this.used = used;
            this.available = available;
            this.inaccessible = inaccessible;
        }
    }
}
