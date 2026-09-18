package kome.common.data;

import java.math.BigDecimal;
import java.math.BigInteger;

/** Exact Build-domain duration. Configuration owns its separate conversion settings. */
public final class KOMEBuildTime {
    public static final long CENTI_HOURS_PER_HOUR = 100L;
    private KOMEBuildTime() { }

    public static long parseHours(String input) {
        String text = input == null ? "" : input.trim();
        if (!text.matches("[0-9]+(?:\\.[0-9]{1,2})?")) {
            throw new IllegalArgumentException("Build hours must be nonnegative decimal hours with at most two decimal places.");
        }
        try {
            return new BigDecimal(text).multiply(BigDecimal.valueOf(CENTI_HOURS_PER_HOUR)).longValueExact();
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("Build hours exceed the supported centi-hour range.", overflow);
        }
    }

    public static long requireNonnegative(long centiHours) {
        if (centiHours < 0L) throw new IllegalArgumentException("Build centi-hours must be nonnegative.");
        return centiHours;
    }

    public static long add(long first, long second) {
        requireNonnegative(first);
        requireNonnegative(second);
        try {
            return Math.addExact(first, second);
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("Approved Build hours overflow the centi-hour range.", overflow);
        }
    }

    public static String formatHours(long centiHours) {
        return formatHours(BigInteger.valueOf(requireNonnegative(centiHours)));
    }

    /** Also formats derived cross-Build totals, which need not fit one record's long. */
    public static String formatHours(BigInteger centiHours) {
        if (centiHours == null || centiHours.signum() < 0) throw new IllegalArgumentException("Build centi-hours must be nonnegative.");
        return new BigDecimal(centiHours).divide(BigDecimal.valueOf(CENTI_HOURS_PER_HOUR)).setScale(2).toPlainString();
    }

    /** The existing +/- half-hour UI buttons preserve hundredths and reject invalid results. */
    public static long adjustHours(long centiHours, long deltaCentiHours) {
        requireNonnegative(centiHours);
        try {
            return requireNonnegative(Math.addExact(centiHours, deltaCentiHours));
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("Build hours exceed the supported centi-hour range.", overflow);
        }
    }
}
