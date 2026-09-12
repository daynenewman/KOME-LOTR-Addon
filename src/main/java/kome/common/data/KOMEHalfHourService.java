package kome.common.data;

/** Exact half-hour validation and display for Build contribution input. */
public final class KOMEHalfHourService {
    public static final int DEFAULT_STAGE_THREE_REQUIRED_HALF_HOURS = 20;
    private KOMEHalfHourService() { }

    public static boolean isValidHours(double hours) {
        if (Double.isNaN(hours) || Double.isInfinite(hours) || hours < 0.0D) return false;
        double halfHours = hours * 2.0D;
        return Math.abs(halfHours - Math.rint(halfHours)) < 0.000001D;
    }

    public static int toHalfHours(double hours) {
        if (!isValidHours(hours)) throw invalidHours();
        double halfHours = Math.rint(hours * 2.0D);
        if (halfHours > Integer.MAX_VALUE) throw new IllegalArgumentException("Build hours exceed the supported range.");
        return (int) halfHours;
    }

    public static int parseHalfHours(String value) {
        String text = value == null ? "" : value.trim();
        if (!text.matches("(?:\\d+|\\d+\\.0|\\d*\\.5)")) throw invalidHours();
        try { return toHalfHours(Double.parseDouble(text)); }
        catch (NumberFormatException error) { throw invalidHours(); }
    }

    public static int adjustHalfHours(int halfHours, int delta) {
        long adjusted = (long) Math.max(0, halfHours) + delta;
        return adjusted <= 0L ? 0 : adjusted >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) adjusted;
    }

    public static String displayHours(int halfHours) {
        int safe = Math.max(0, halfHours);
        return safe % 2 == 0 ? Integer.toString(safe / 2) : safe / 2 + ".5";
    }

    private static IllegalArgumentException invalidHours() {
        return new IllegalArgumentException("Hours must be 0 or a positive whole/half-hour value (for example 0, 0.5, 1, or 1.5).");
    }
}
