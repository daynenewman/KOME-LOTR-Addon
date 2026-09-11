package kome.common.config;

import net.minecraftforge.common.config.Configuration;

import java.io.File;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;

/** Canonical, typed KOME configuration values. */
public final class KOMEConfigRegistry {
    public static final String POPULATION_CATEGORY = "population";
    public static final String HOURS_PER_POPULATION_POINT = "hoursPerPopulationPoint";
    public static final String CAPTURED_BUILD_MULTIPLIER = "capturedBuildMultiplier";
    public static final String DAILY_BATCH_LOCAL_TIME = "dailyBatchLocalTime";
    public static final String TIMEZONE = "timezone";
    public static final String OFFLINE_POPULATION_CATCH_UP = "offlinePopulationCatchUp";
    public static final String POPULATION_CAP_ENABLED = "populationCapEnabled";
    public static final String ENCIRCLEMENT_POPULATION_SUPPRESSION_ENABLED =
            "encirclementPopulationSuppressionEnabled";

    private static final int DEFAULT_HOURS_PER_POPULATION_POINT = 10;
    private static final double DEFAULT_CAPTURED_BUILD_MULTIPLIER = 0.50D;
    private static final String DEFAULT_DAILY_BATCH_LOCAL_TIME = "20:00";
    private static final String DEFAULT_TIMEZONE = "America/Chicago";
    private static final boolean DEFAULT_OFFLINE_POPULATION_CATCH_UP = true;
    private static final boolean DEFAULT_POPULATION_CAP_ENABLED = false;
    private static final boolean DEFAULT_ENCIRCLEMENT_POPULATION_SUPPRESSION_ENABLED = false;
    private static final DateTimeFormatter DAILY_TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(ResolverStyle.STRICT);

    private static volatile PopulationSettings population = defaults();

    private KOMEConfigRegistry() {
    }

    public static synchronized void load(File file) {
        Configuration configuration = new Configuration(file);
        configuration.load();
        PopulationSettings loaded = readPopulation(configuration);
        population = loaded;
        if (configuration.hasChanged()) {
            configuration.save();
        }
    }

    public static PopulationSettings population() {
        return population;
    }

    private static PopulationSettings defaults() {
        return new PopulationSettings(
                DEFAULT_HOURS_PER_POPULATION_POINT,
                DEFAULT_CAPTURED_BUILD_MULTIPLIER,
                LocalTime.parse(DEFAULT_DAILY_BATCH_LOCAL_TIME),
                ZoneId.of(DEFAULT_TIMEZONE),
                DEFAULT_OFFLINE_POPULATION_CATCH_UP,
                DEFAULT_POPULATION_CAP_ENABLED,
                DEFAULT_ENCIRCLEMENT_POPULATION_SUPPRESSION_ENABLED
        );
    }

    private static PopulationSettings readPopulation(Configuration configuration) {
        int hours = parsePositiveInt(HOURS_PER_POPULATION_POINT,
                value(configuration, HOURS_PER_POPULATION_POINT,
                        Integer.toString(DEFAULT_HOURS_PER_POPULATION_POINT)));
        double capturedMultiplier = parseCapturedMultiplier(value(configuration,
                CAPTURED_BUILD_MULTIPLIER,
                Double.toString(DEFAULT_CAPTURED_BUILD_MULTIPLIER)));
        LocalTime dailyTime = parseDailyTime(value(configuration, DAILY_BATCH_LOCAL_TIME,
                DEFAULT_DAILY_BATCH_LOCAL_TIME));
        ZoneId timezone = parseTimezone(value(configuration, TIMEZONE, DEFAULT_TIMEZONE));
        boolean offlineCatchUp = parseBoolean(OFFLINE_POPULATION_CATCH_UP,
                value(configuration, OFFLINE_POPULATION_CATCH_UP,
                        Boolean.toString(DEFAULT_OFFLINE_POPULATION_CATCH_UP)));
        boolean capEnabled = parseBoolean(POPULATION_CAP_ENABLED,
                value(configuration, POPULATION_CAP_ENABLED,
                        Boolean.toString(DEFAULT_POPULATION_CAP_ENABLED)));
        boolean encirclementSuppression = parseBoolean(
                ENCIRCLEMENT_POPULATION_SUPPRESSION_ENABLED,
                value(configuration, ENCIRCLEMENT_POPULATION_SUPPRESSION_ENABLED,
                        Boolean.toString(DEFAULT_ENCIRCLEMENT_POPULATION_SUPPRESSION_ENABLED)));
        return new PopulationSettings(hours, capturedMultiplier, dailyTime, timezone,
                offlineCatchUp, capEnabled, encirclementSuppression);
    }

    private static String value(Configuration configuration, String key, String defaultValue) {
        return configuration.get(POPULATION_CATEGORY, key, defaultValue).getString();
    }

    private static int parsePositiveInt(String key, String value) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed > 0) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
        }
        throw invalid(key, value, "must be an integer greater than 0");
    }

    private static double parseCapturedMultiplier(String value) {
        try {
            double parsed = Double.parseDouble(value);
            if (!Double.isNaN(parsed) && !Double.isInfinite(parsed)
                    && parsed >= 0.0D && parsed <= 1.0D) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
        }
        throw invalid(CAPTURED_BUILD_MULTIPLIER, value, "must be between 0.0 and 1.0");
    }

    private static LocalTime parseDailyTime(String value) {
        if (!value.matches("[0-9]{2}:[0-9]{2}")) {
            throw invalid(DAILY_BATCH_LOCAL_TIME, value, "must use HH:mm");
        }
        try {
            return LocalTime.parse(value, DAILY_TIME_FORMAT);
        } catch (DateTimeParseException ignored) {
            throw invalid(DAILY_BATCH_LOCAL_TIME, value, "must use HH:mm");
        }
    }

    private static ZoneId parseTimezone(String value) {
        try {
            if (!ZoneId.getAvailableZoneIds().contains(value)) {
                throw invalid(TIMEZONE, value, "must be a recognized IANA timezone");
            }
            return ZoneId.of(value);
        } catch (RuntimeException ignored) {
            throw invalid(TIMEZONE, value, "must be a recognized IANA timezone");
        }
    }

    private static boolean parseBoolean(String key, String value) {
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        throw invalid(key, value, "must be true or false");
    }

    private static KOMEConfigValidationException invalid(String key, String value,
            String requirement) {
        return new KOMEConfigValidationException(POPULATION_CATEGORY + "." + key,
                value, requirement);
    }

    public static final class PopulationSettings {
        private final int hoursPerPopulationPoint;
        private final double capturedBuildMultiplier;
        private final LocalTime dailyBatchLocalTime;
        private final ZoneId timezone;
        private final boolean offlinePopulationCatchUp;
        private final boolean populationCapEnabled;
        private final boolean encirclementPopulationSuppressionEnabled;

        private PopulationSettings(int hoursPerPopulationPoint, double capturedBuildMultiplier,
                LocalTime dailyBatchLocalTime, ZoneId timezone, boolean offlinePopulationCatchUp,
                boolean populationCapEnabled, boolean encirclementPopulationSuppressionEnabled) {
            this.hoursPerPopulationPoint = hoursPerPopulationPoint;
            this.capturedBuildMultiplier = capturedBuildMultiplier;
            this.dailyBatchLocalTime = dailyBatchLocalTime;
            this.timezone = timezone;
            this.offlinePopulationCatchUp = offlinePopulationCatchUp;
            this.populationCapEnabled = populationCapEnabled;
            this.encirclementPopulationSuppressionEnabled = encirclementPopulationSuppressionEnabled;
        }

        public int getHoursPerPopulationPoint() { return hoursPerPopulationPoint; }
        public double getCapturedBuildMultiplier() { return capturedBuildMultiplier; }
        public LocalTime getDailyBatchLocalTime() { return dailyBatchLocalTime; }
        public ZoneId getTimezone() { return timezone; }
        public boolean isOfflinePopulationCatchUp() { return offlinePopulationCatchUp; }
        public boolean isPopulationCapEnabled() { return populationCapEnabled; }
        public boolean isEncirclementPopulationSuppressionEnabled() {
            return encirclementPopulationSuppressionEnabled;
        }
    }
}
