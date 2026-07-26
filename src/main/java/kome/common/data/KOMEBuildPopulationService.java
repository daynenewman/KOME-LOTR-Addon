package kome.common.data;

/** Canonical Build-hours conversion and validation boundary. */
public final class KOMEBuildPopulationService {
    public static final int DEFAULT_POPULATION_PER_HALF_HOUR = 5;
    public static final int DEFAULT_STAGE_THREE_REQUIRED_HALF_HOURS = 20;

    private KOMEBuildPopulationService() {
    }

    public static boolean isValidHours(double hours) {
        if (Double.isNaN(hours) || Double.isInfinite(hours) || hours < 0.0D) return false;
        double halfHours = hours * 2.0D;
        return Math.abs(halfHours - Math.rint(halfHours)) < 0.000001D;
    }

    public static int toHalfHours(double hours) {
        if (!isValidHours(hours)) {
            throw new IllegalArgumentException("Build hours must be zero or a positive half-hour increment.");
        }
        double halfHours = Math.rint(hours * 2.0D);
        if (halfHours > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Build hours exceed the supported range.");
        }
        return (int) halfHours;
    }

    public static double toHours(int halfHours) {
        return Math.max(0, halfHours) / 2.0D;
    }

    public static int generatedPopulation(int halfHours, int populationPerHalfHour) {
        long result = (long) Math.max(0, halfHours) * Math.max(1, populationPerHalfHour);
        return result > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) result;
    }

    public static String displayHours(int halfHours) {
        int safe = Math.max(0, halfHours);
        return safe % 2 == 0 ? Integer.toString(safe / 2) : safe / 2 + ".5";
    }
}
