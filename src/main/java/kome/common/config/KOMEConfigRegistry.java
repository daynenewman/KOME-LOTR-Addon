package kome.common.config;


import java.io.File;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

import net.minecraftforge.common.config.Configuration;

/** Canonical, typed KOME configuration values. */
public final class KOMEConfigRegistry {
    public static final String DAILY_BATCH_CATEGORY = "dailyBatch";
    public static final String POPULATION_CATEGORY = "population";
    public static final String MOVEMENT_CATEGORY = "movement";
    public static final String BATTLE_CATEGORY = "battle";
    public static final String LOCAL_TIME = "localTime";
    public static final String TIMEZONE = "timezone";
    public static final String HOURS_PER_POPULATION_POINT = "hoursPerPopulationPoint";
    public static final String CAPTURED_BUILD_MULTIPLIER = "capturedBuildMultiplier";
    public static final String OFFLINE_POPULATION_CATCH_UP = "offlinePopulationCatchUp";
    public static final String POPULATION_CAP_ENABLED = "populationCapEnabled";
    public static final String ENCIRCLEMENT_POPULATION_SUPPRESSION_ENABLED =
            "encirclementPopulationSuppressionEnabled";
    public static final String FOOT_OR_MIXED_TILES_PER_DAY = "footOrMixedTilesPerDay";
    public static final String FULLY_MOUNTED_TILES_PER_DAY = "fullyMountedTilesPerDay";
    public static final String RESPONSE_LEVEL_1_MINUTES = "responseLevel1Minutes";
    public static final String RESPONSE_LEVEL_2_MINUTES = "responseLevel2Minutes";
    public static final String RESPONSE_LEVEL_3_MINUTES = "responseLevel3Minutes";

    private static final String DEFAULT_LOCAL_TIME = "20:00";
    private static final String DEFAULT_TIMEZONE = "America/Chicago";
    private static final DateTimeFormatter DAILY_TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm")
                    .withResolverStyle(ResolverStyle.STRICT);

    private static volatile DailyBatchSettings dailyBatch =
            new DailyBatchSettings(LocalTime.parse(DEFAULT_LOCAL_TIME),
                    ZoneId.of(DEFAULT_TIMEZONE));
    private static volatile PopulationSettings population =
            new PopulationSettings(10, 0.50D, true, false, false);
    private static volatile MovementSettings movement = new MovementSettings(1, 2);
    private static volatile BattleSettings battle = new BattleSettings(20, 35, 50);

    private KOMEConfigRegistry() {
    }

    public static synchronized void load(File file) {
        Configuration configuration = new Configuration(file);
        configuration.load();
        DailyBatchSettings loadedDailyBatch = readDailyBatch(configuration);
        PopulationSettings loadedPopulation = readPopulation(configuration);
        MovementSettings loadedMovement = readMovement(configuration);
        BattleSettings loadedBattle = readBattle(configuration);
        dailyBatch = loadedDailyBatch;
        population = loadedPopulation;
        movement = loadedMovement;
        battle = loadedBattle;
        if (configuration.hasChanged()) {
            configuration.save();
        }
    }

    public static DailyBatchSettings dailyBatch() {
        return dailyBatch;
    }

    public static PopulationSettings population() {
        return population;
    }

    public static MovementSettings movement() {
        return movement;
    }

    public static BattleSettings battle() {
        return battle;
    }

    private static DailyBatchSettings readDailyBatch(Configuration c) {
        return new DailyBatchSettings(
                parseDailyTime(value(c, DAILY_BATCH_CATEGORY, LOCAL_TIME,
                        DEFAULT_LOCAL_TIME)),
                parseTimezone(value(c, DAILY_BATCH_CATEGORY, TIMEZONE,
                        DEFAULT_TIMEZONE)));
    }

    private static PopulationSettings readPopulation(Configuration c) {
        int hours = positive(POPULATION_CATEGORY, HOURS_PER_POPULATION_POINT,
                value(c, POPULATION_CATEGORY, HOURS_PER_POPULATION_POINT, "10"));
        double multiplier = multiplier(value(c, POPULATION_CATEGORY,
                CAPTURED_BUILD_MULTIPLIER, "0.5"));
        boolean catchUp = bool(POPULATION_CATEGORY, OFFLINE_POPULATION_CATCH_UP,
                value(c, POPULATION_CATEGORY, OFFLINE_POPULATION_CATCH_UP, "true"));
        boolean cap = bool(POPULATION_CATEGORY, POPULATION_CAP_ENABLED,
                value(c, POPULATION_CATEGORY, POPULATION_CAP_ENABLED, "false"));
        boolean suppression = bool(POPULATION_CATEGORY,
                ENCIRCLEMENT_POPULATION_SUPPRESSION_ENABLED,
                value(c, POPULATION_CATEGORY,
                        ENCIRCLEMENT_POPULATION_SUPPRESSION_ENABLED, "false"));
        return new PopulationSettings(hours, multiplier, catchUp, cap, suppression);
    }

    private static MovementSettings readMovement(Configuration c) {
        int foot = positive(MOVEMENT_CATEGORY, FOOT_OR_MIXED_TILES_PER_DAY,
                value(c, MOVEMENT_CATEGORY, FOOT_OR_MIXED_TILES_PER_DAY, "1"));
        String mountedValue = value(c, MOVEMENT_CATEGORY,
                FULLY_MOUNTED_TILES_PER_DAY, "2");
        int mounted = positive(MOVEMENT_CATEGORY, FULLY_MOUNTED_TILES_PER_DAY,
                mountedValue);
        if (mounted < foot) {
            throw invalid(MOVEMENT_CATEGORY, FULLY_MOUNTED_TILES_PER_DAY,
                    mountedValue,
                    "must be greater than or equal to footOrMixedTilesPerDay");
        }
        return new MovementSettings(foot, mounted);
    }

    private static BattleSettings readBattle(Configuration c) {
        int level1 = positive(BATTLE_CATEGORY, RESPONSE_LEVEL_1_MINUTES,
                value(c, BATTLE_CATEGORY, RESPONSE_LEVEL_1_MINUTES, "20"));
        String level2Value = value(c, BATTLE_CATEGORY, RESPONSE_LEVEL_2_MINUTES,
                "35");
        int level2 = positive(BATTLE_CATEGORY, RESPONSE_LEVEL_2_MINUTES, level2Value);
        if (level2 <= level1) {
            throw invalid(BATTLE_CATEGORY, RESPONSE_LEVEL_2_MINUTES, level2Value,
                    "must be greater than responseLevel1Minutes");
        }
        String level3Value = value(c, BATTLE_CATEGORY, RESPONSE_LEVEL_3_MINUTES,
                "50");
        int level3 = positive(BATTLE_CATEGORY, RESPONSE_LEVEL_3_MINUTES, level3Value);
        if (level3 <= level2) {
            throw invalid(BATTLE_CATEGORY, RESPONSE_LEVEL_3_MINUTES, level3Value,
                    "must be greater than responseLevel2Minutes");
        }
        return new BattleSettings(level1, level2, level3);
    }

    private static String value(Configuration c, String category, String key,
            String defaultValue) {
        return c.get(category, key, defaultValue).getString();
    }

    private static int positive(String category, String key, String value) {
        try {
            int result = Integer.parseInt(value);
            if (result > 0) {
                return result;
            }
        } catch (NumberFormatException ignored) {
        }
        throw invalid(category, key, value, "must be an integer greater than 0");
    }

    private static double multiplier(String value) {
        try {
            double result = Double.parseDouble(value);
            if (!Double.isNaN(result) && !Double.isInfinite(result)
                    && result >= 0.0D && result <= 1.0D) {
                return result;
            }
        } catch (NumberFormatException ignored) {
        }
        throw invalid(POPULATION_CATEGORY, CAPTURED_BUILD_MULTIPLIER, value,
                "must be between 0.0 and 1.0");
    }

    private static LocalTime parseDailyTime(String value) {
        if (!value.matches("[0-9]{2}:[0-9]{2}")) {
            throw invalid(DAILY_BATCH_CATEGORY, LOCAL_TIME, value, "must use HH:mm");
        }
        try {
            return LocalTime.parse(value, DAILY_TIME_FORMAT);
        } catch (DateTimeParseException ignored) {
            throw invalid(DAILY_BATCH_CATEGORY, LOCAL_TIME, value, "must use HH:mm");
        }
    }

    private static ZoneId parseTimezone(String value) {
        try {
            if (!ZoneId.getAvailableZoneIds().contains(value)) {
                throw invalid(DAILY_BATCH_CATEGORY, TIMEZONE, value,
                        "must be a recognized IANA timezone");
            }
            return ZoneId.of(value);
        } catch (RuntimeException ignored) {
            throw invalid(DAILY_BATCH_CATEGORY, TIMEZONE, value,
                    "must be a recognized IANA timezone");
        }
    }

    private static boolean bool(String category, String key, String value) {
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        throw invalid(category, key, value, "must be true or false");
    }

    private static KOMEConfigValidationException invalid(String category, String key,
            String value, String requirement) {
        return new KOMEConfigValidationException(category + "." + key, value,
                requirement);
    }

    public static final class DailyBatchSettings {
        private final LocalTime localTime;
        private final ZoneId timezone;

        private DailyBatchSettings(LocalTime localTime, ZoneId timezone) {
            this.localTime = localTime;
            this.timezone = timezone;
        }

        public LocalTime getLocalTime() {
            return localTime;
        }

        public ZoneId getTimezone() {
            return timezone;
        }
    }

    public static final class PopulationSettings {
        private final int hoursPerPopulationPoint;
        private final double capturedBuildMultiplier;
        private final boolean offlinePopulationCatchUp;
        private final boolean populationCapEnabled;
        private final boolean encirclementPopulationSuppressionEnabled;

        private PopulationSettings(int hours, double multiplier, boolean catchUp,
                boolean cap, boolean suppression) {
            hoursPerPopulationPoint = hours;
            capturedBuildMultiplier = multiplier;
            offlinePopulationCatchUp = catchUp;
            populationCapEnabled = cap;
            encirclementPopulationSuppressionEnabled = suppression;
        }

        public int getHoursPerPopulationPoint() {
            return hoursPerPopulationPoint;
        }

        public double getCapturedBuildMultiplier() {
            return capturedBuildMultiplier;
        }

        public boolean isOfflinePopulationCatchUp() {
            return offlinePopulationCatchUp;
        }

        public boolean isPopulationCapEnabled() {
            return populationCapEnabled;
        }

        public boolean isEncirclementPopulationSuppressionEnabled() {
            return encirclementPopulationSuppressionEnabled;
        }
    }

    public static final class MovementSettings {
        private final int footOrMixedTilesPerDay;
        private final int fullyMountedTilesPerDay;

        private MovementSettings(int foot, int mounted) {
            footOrMixedTilesPerDay = foot;
            fullyMountedTilesPerDay = mounted;
        }

        public int getFootOrMixedTilesPerDay() {
            return footOrMixedTilesPerDay;
        }

        public int getFullyMountedTilesPerDay() {
            return fullyMountedTilesPerDay;
        }
    }

    public static final class BattleSettings {
        private final int responseLevel1Minutes;
        private final int responseLevel2Minutes;
        private final int responseLevel3Minutes;

        private BattleSettings(int level1, int level2, int level3) {
            responseLevel1Minutes = level1;
            responseLevel2Minutes = level2;
            responseLevel3Minutes = level3;
        }

        public int getResponseLevel1Minutes() {
            return responseLevel1Minutes;
        }

        public int getResponseLevel2Minutes() {
            return responseLevel2Minutes;
        }

        public int getResponseLevel3Minutes() {
            return responseLevel3Minutes;
        }
    }
}
