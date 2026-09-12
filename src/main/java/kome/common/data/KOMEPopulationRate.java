package kome.common.data;

/** Immutable population/day rate in millionths; 1,000,000 fixed units equals one population/day. */
public final class KOMEPopulationRate {
    public static final long SCALE = 1_000_000L;
    public static final KOMEPopulationRate ZERO = new KOMEPopulationRate(0L);
    private final long fixedUnitsPerDay;

    public KOMEPopulationRate(long fixedUnitsPerDay) { this.fixedUnitsPerDay = Math.max(0L, fixedUnitsPerDay); }
    public long getFixedUnitsPerDay() { return fixedUnitsPerDay; }
    public boolean isZero() { return fixedUnitsPerDay == 0L; }
    /** Stable decimal display of the fixed-point rate; never uses binary floating point. */
    public String formatPerDay() {
        long whole = fixedUnitsPerDay / SCALE;
        long fraction = fixedUnitsPerDay % SCALE;
        if (fraction == 0L) return Long.toString(whole);
        String digits = String.format(java.util.Locale.ROOT, "%06d", Long.valueOf(fraction));
        int end = digits.length();
        while (end > 0 && digits.charAt(end - 1) == '0') end--;
        return whole + "." + digits.substring(0, end);
    }
}
