package kome.common.data;

/** Immutable population/day rate in millionths; 1,000,000 fixed units equals one population/day. */
public final class KOMEPopulationRate {
    public static final long SCALE = 1_000_000L;
    public static final KOMEPopulationRate ZERO = new KOMEPopulationRate(0L);
    private final long fixedUnitsPerDay;

    public KOMEPopulationRate(long fixedUnitsPerDay) { this.fixedUnitsPerDay = Math.max(0L, fixedUnitsPerDay); }
    public long getFixedUnitsPerDay() { return fixedUnitsPerDay; }
    public boolean isZero() { return fixedUnitsPerDay == 0L; }
}
