package kome.common.data;

import java.time.Instant;
import java.time.ZoneId;
import kome.common.config.KOMEConfigRegistry;

/** Shared server-calendar authority for progression cadence and betrayal consequences. */
public final class KOMEProgressionCalendar {
    private KOMEProgressionCalendar() { }
    /** Uses the configured server daily timezone, never the dedicated server OS timezone. */
    public static ZoneId configuredTimezone() { return KOMEConfigRegistry.dailyBatch().getTimezone(); }
    public static long currentEpochDay() { return epochDay(Instant.ofEpochMilli(System.currentTimeMillis()), configuredTimezone()); }
    public static long epochDay(Instant instant, ZoneId timezone) {
        if (instant == null || timezone == null) throw new IllegalArgumentException("An instant and configured timezone are required.");
        return instant.atZone(timezone).toLocalDate().toEpochDay();
    }
    public static boolean isSanePersistedDay(long day) { return day >= 0L && day <= currentEpochDay() + 366L; }
}
