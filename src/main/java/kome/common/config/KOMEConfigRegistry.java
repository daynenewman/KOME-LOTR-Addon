package kome.common.config;


import java.io.File;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import net.minecraftforge.common.config.Configuration;

/** Canonical, typed KOME configuration values. */
public final class KOMEConfigRegistry {
    public static final String DAILY_BATCH_CATEGORY = "dailyBatch";
    public static final String POPULATION_CATEGORY = "population";
    public static final String MOVEMENT_CATEGORY = "movement";
    public static final String BATTLE_CATEGORY = "battle";
    public static final String MUSTER_CATEGORY = "muster";
    public static final String SIEGE_CATEGORY = "siege";
    public static final String BATTLE_SUPPORT_CATEGORY = "battleSupport";
    public static final String ENCIRCLEMENT_CATEGORY = "encirclement";
    public static final String SEASON_CATEGORY = "season";
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
    public static final String THREAT_DISTANCE_TILES = "threatDistanceTiles";
    public static final String BUDGET_DAILY_POPULATION_MULTIPLIER =
            "budgetDailyPopulationMultiplier";
    public static final String ARRIVAL_DELAY_HOURS = "arrivalDelayHours";
    public static final String ENCIRCLED_CAPITAL_ARRIVAL_POLICY =
            "encircledCapitalArrivalPolicy";
    public static final String GATE_HP_PER_APPROVED_HOUR = "gateHpPerApprovedHour";
    public static final String NORMAL_SEGMENT_SUPPORT_MINIMUM_TROOPS =
            "normalSegmentSupportMinimumTroops";
    public static final String SUPPORT_FALLBACK_GRACE_SECONDS =
            "supportFallbackGraceSeconds";
    public static final String PRE_BREACH_REPAIR = "preBreachRepair";
    public static final String POST_BREACH_REPAIR_ENABLED = "postBreachRepairEnabled";
    public static final String BATTLE_SUPPORT_MODE = "mode";
    public static final String FULL_DAMAGE_DISTANCE_BLOCKS = "fullDamageDistanceBlocks";
    public static final String HALF_DAMAGE_DISTANCE_BLOCKS = "halfDamageDistanceBlocks";
    public static final String LOW_DAMAGE_DISTANCE_BLOCKS = "lowDamageDistanceBlocks";
    public static final String MINIMUM_DAMAGE_DISTANCE_BLOCKS = "minimumDamageDistanceBlocks";
    public static final String HALF_DAMAGE_MULTIPLIER = "halfDamageMultiplier";
    public static final String LOW_DAMAGE_MULTIPLIER = "lowDamageMultiplier";
    public static final String MINIMUM_DAMAGE_MULTIPLIER = "minimumDamageMultiplier";
    public static final String HARD_FALLBACK_DISTANCE_BLOCKS = "hardFallbackDistanceBlocks";
    public static final String OPEN_BATTLE_RADIUS_BLOCKS = "openBattleRadiusBlocks";
    public static final String STARVATION_GRACE_DAYS = "starvationGraceDays";
    public static final String ANNOUNCED_ASSAULT_NOTICE_HOURS = "announcedAssaultNoticeHours";
    public static final String OFFLINE_STARVATION_CATCH_UP = "offlineStarvationCatchUp";
    public static final String MINIMUM_WAR_SEASON_LENGTH_DAYS = "minimumWarSeasonLengthDays";
    public static final String AUTOMATIC_FINALE_ENABLED = "automaticFinaleEnabled";

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
    private static volatile MusterSettings muster = new MusterSettings(2, 21, 24,
            EncircledCapitalArrivalPolicy.TBD);
    private static volatile SiegeSettings siege = new SiegeSettings(OptionalDouble.empty(),
            1, 15, PreBreachRepair.TBD, false);
    private static volatile BattleSupportSettings battleSupport = new BattleSupportSettings(
            BattleSupportMode.CURVE, 32, 48, 64, 70, 0.50D, 0.10D, 0.01D, 48, 192);
    private static volatile EncirclementSettings encirclement =
            new EncirclementSettings(10, 48, false);
    private static volatile SeasonSettings season = new SeasonSettings(OptionalInt.empty(), false);

    private KOMEConfigRegistry() {
    }

    public static synchronized void load(File file) {
        Configuration configuration = new Configuration(file);
        configuration.load();
        DailyBatchSettings loadedDailyBatch = readDailyBatch(configuration);
        PopulationSettings loadedPopulation = readPopulation(configuration);
        MovementSettings loadedMovement = readMovement(configuration);
        BattleSettings loadedBattle = readBattle(configuration);
        MusterSettings loadedMuster = readMuster(configuration);
        SiegeSettings loadedSiege = readSiege(configuration);
        BattleSupportSettings loadedBattleSupport = readBattleSupport(configuration);
        EncirclementSettings loadedEncirclement = readEncirclement(configuration);
        SeasonSettings loadedSeason = readSeason(configuration);
        dailyBatch = loadedDailyBatch;
        population = loadedPopulation;
        movement = loadedMovement;
        battle = loadedBattle;
        muster = loadedMuster;
        siege = loadedSiege;
        battleSupport = loadedBattleSupport;
        encirclement = loadedEncirclement;
        season = loadedSeason;
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

    public static MusterSettings muster() {
        return muster;
    }

    public static SiegeSettings siege() {
        return siege;
    }

    public static BattleSupportSettings battleSupport() {
        return battleSupport;
    }

    public static EncirclementSettings encirclement() {
        return encirclement;
    }

    public static SeasonSettings season() {
        return season;
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

    private static MusterSettings readMuster(Configuration c) {
        int threatDistance = positive(MUSTER_CATEGORY, THREAT_DISTANCE_TILES,
                value(c, MUSTER_CATEGORY, THREAT_DISTANCE_TILES, "2"));
        int budgetMultiplier = positive(MUSTER_CATEGORY,
                BUDGET_DAILY_POPULATION_MULTIPLIER,
                value(c, MUSTER_CATEGORY, BUDGET_DAILY_POPULATION_MULTIPLIER, "21"));
        int arrivalDelay = positive(MUSTER_CATEGORY, ARRIVAL_DELAY_HOURS,
                value(c, MUSTER_CATEGORY, ARRIVAL_DELAY_HOURS, "24"));
        EncircledCapitalArrivalPolicy policy = parseEnum(MUSTER_CATEGORY,
                ENCIRCLED_CAPITAL_ARRIVAL_POLICY,
                value(c, MUSTER_CATEGORY, ENCIRCLED_CAPITAL_ARRIVAL_POLICY, "TBD"),
                EncircledCapitalArrivalPolicy.class);
        return new MusterSettings(threatDistance, budgetMultiplier, arrivalDelay, policy);
    }

    private static SiegeSettings readSiege(Configuration c) {
        OptionalDouble gateHpPerApprovedHour = parseOptionalPositiveDouble(
                GATE_HP_PER_APPROVED_HOUR,
                value(c, SIEGE_CATEGORY, GATE_HP_PER_APPROVED_HOUR, "TBD"));
        int supportMinimum = positive(SIEGE_CATEGORY,
                NORMAL_SEGMENT_SUPPORT_MINIMUM_TROOPS,
                value(c, SIEGE_CATEGORY, NORMAL_SEGMENT_SUPPORT_MINIMUM_TROOPS, "1"));
        int fallbackGrace = nonNegative(SIEGE_CATEGORY, SUPPORT_FALLBACK_GRACE_SECONDS,
                value(c, SIEGE_CATEGORY, SUPPORT_FALLBACK_GRACE_SECONDS, "15"));
        PreBreachRepair preBreachRepair = parseEnum(SIEGE_CATEGORY, PRE_BREACH_REPAIR,
                value(c, SIEGE_CATEGORY, PRE_BREACH_REPAIR, "TBD"),
                PreBreachRepair.class);
        boolean postBreachRepairEnabled = bool(SIEGE_CATEGORY,
                POST_BREACH_REPAIR_ENABLED,
                value(c, SIEGE_CATEGORY, POST_BREACH_REPAIR_ENABLED, "false"));
        return new SiegeSettings(gateHpPerApprovedHour, supportMinimum, fallbackGrace,
                preBreachRepair, postBreachRepairEnabled);
    }

    private static BattleSupportSettings readBattleSupport(Configuration c) {
        BattleSupportMode mode = parseEnum(BATTLE_SUPPORT_CATEGORY, BATTLE_SUPPORT_MODE,
                value(c, BATTLE_SUPPORT_CATEGORY, BATTLE_SUPPORT_MODE, "CURVE"),
                BattleSupportMode.class);
        int fullDistance = positive(BATTLE_SUPPORT_CATEGORY, FULL_DAMAGE_DISTANCE_BLOCKS,
                value(c, BATTLE_SUPPORT_CATEGORY, FULL_DAMAGE_DISTANCE_BLOCKS, "32"));
        String halfDistanceValue = value(c, BATTLE_SUPPORT_CATEGORY,
                HALF_DAMAGE_DISTANCE_BLOCKS, "48");
        int halfDistance = positive(BATTLE_SUPPORT_CATEGORY, HALF_DAMAGE_DISTANCE_BLOCKS,
                halfDistanceValue);
        requireGreater(BATTLE_SUPPORT_CATEGORY, HALF_DAMAGE_DISTANCE_BLOCKS,
                halfDistanceValue, halfDistance, fullDistance, FULL_DAMAGE_DISTANCE_BLOCKS);
        String lowDistanceValue = value(c, BATTLE_SUPPORT_CATEGORY,
                LOW_DAMAGE_DISTANCE_BLOCKS, "64");
        int lowDistance = positive(BATTLE_SUPPORT_CATEGORY, LOW_DAMAGE_DISTANCE_BLOCKS,
                lowDistanceValue);
        requireGreater(BATTLE_SUPPORT_CATEGORY, LOW_DAMAGE_DISTANCE_BLOCKS, lowDistanceValue,
                lowDistance, halfDistance, HALF_DAMAGE_DISTANCE_BLOCKS);
        String minimumDistanceValue = value(c, BATTLE_SUPPORT_CATEGORY,
                MINIMUM_DAMAGE_DISTANCE_BLOCKS, "70");
        int minimumDistance = positive(BATTLE_SUPPORT_CATEGORY,
                MINIMUM_DAMAGE_DISTANCE_BLOCKS, minimumDistanceValue);
        requireGreater(BATTLE_SUPPORT_CATEGORY, MINIMUM_DAMAGE_DISTANCE_BLOCKS,
                minimumDistanceValue, minimumDistance, lowDistance,
                LOW_DAMAGE_DISTANCE_BLOCKS);
        double halfMultiplier = finiteUnitInterval(BATTLE_SUPPORT_CATEGORY,
                HALF_DAMAGE_MULTIPLIER,
                value(c, BATTLE_SUPPORT_CATEGORY, HALF_DAMAGE_MULTIPLIER, "0.5"));
        String lowMultiplierValue = value(c, BATTLE_SUPPORT_CATEGORY,
                LOW_DAMAGE_MULTIPLIER, "0.1");
        double lowMultiplier = finiteUnitInterval(BATTLE_SUPPORT_CATEGORY,
                LOW_DAMAGE_MULTIPLIER, lowMultiplierValue);
        requireNoGreater(BATTLE_SUPPORT_CATEGORY, LOW_DAMAGE_MULTIPLIER,
                lowMultiplierValue, lowMultiplier, halfMultiplier, HALF_DAMAGE_MULTIPLIER);
        String minimumMultiplierValue = value(c, BATTLE_SUPPORT_CATEGORY,
                MINIMUM_DAMAGE_MULTIPLIER, "0.01");
        double minimumMultiplier = finiteUnitInterval(BATTLE_SUPPORT_CATEGORY,
                MINIMUM_DAMAGE_MULTIPLIER, minimumMultiplierValue);
        requireNoGreater(BATTLE_SUPPORT_CATEGORY, MINIMUM_DAMAGE_MULTIPLIER,
                minimumMultiplierValue, minimumMultiplier, lowMultiplier,
                LOW_DAMAGE_MULTIPLIER);
        int hardFallbackDistance = positive(BATTLE_SUPPORT_CATEGORY,
                HARD_FALLBACK_DISTANCE_BLOCKS,
                value(c, BATTLE_SUPPORT_CATEGORY, HARD_FALLBACK_DISTANCE_BLOCKS, "48"));
        int openBattleRadius = positive(BATTLE_SUPPORT_CATEGORY, OPEN_BATTLE_RADIUS_BLOCKS,
                value(c, BATTLE_SUPPORT_CATEGORY, OPEN_BATTLE_RADIUS_BLOCKS, "192"));
        return new BattleSupportSettings(mode, fullDistance, halfDistance, lowDistance,
                minimumDistance, halfMultiplier, lowMultiplier, minimumMultiplier,
                hardFallbackDistance, openBattleRadius);
    }

    private static EncirclementSettings readEncirclement(Configuration c) {
        int graceDays = nonNegative(ENCIRCLEMENT_CATEGORY, STARVATION_GRACE_DAYS,
                value(c, ENCIRCLEMENT_CATEGORY, STARVATION_GRACE_DAYS, "10"));
        int noticeHours = positive(ENCIRCLEMENT_CATEGORY, ANNOUNCED_ASSAULT_NOTICE_HOURS,
                value(c, ENCIRCLEMENT_CATEGORY, ANNOUNCED_ASSAULT_NOTICE_HOURS, "48"));
        String catchUpValue = value(c, ENCIRCLEMENT_CATEGORY,
                OFFLINE_STARVATION_CATCH_UP, "false");
        boolean catchUp = bool(ENCIRCLEMENT_CATEGORY, OFFLINE_STARVATION_CATCH_UP,
                catchUpValue);
        if (catchUp) {
            throw invalid(ENCIRCLEMENT_CATEGORY, OFFLINE_STARVATION_CATCH_UP,
                    catchUpValue, "must be false because offline starvation does not catch up");
        }
        return new EncirclementSettings(graceDays, noticeHours, false);
    }

    private static SeasonSettings readSeason(Configuration c) {
        OptionalInt minimumLength = parseOptionalPositiveInt(MINIMUM_WAR_SEASON_LENGTH_DAYS,
                value(c, SEASON_CATEGORY, MINIMUM_WAR_SEASON_LENGTH_DAYS, "TBD"));
        String automaticFinaleValue = value(c, SEASON_CATEGORY, AUTOMATIC_FINALE_ENABLED,
                "false");
        boolean automaticFinale = bool(SEASON_CATEGORY, AUTOMATIC_FINALE_ENABLED,
                automaticFinaleValue);
        if (automaticFinale) {
            throw invalid(SEASON_CATEGORY, AUTOMATIC_FINALE_ENABLED, automaticFinaleValue,
                    "must be false because Draft 0.4 never starts Finale automatically");
        }
        return new SeasonSettings(minimumLength, false);
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

    private static int nonNegative(String category, String key, String value) {
        try {
            int result = Integer.parseInt(value);
            if (result >= 0) {
                return result;
            }
        } catch (NumberFormatException ignored) {
        }
        throw invalid(category, key, value, "must be an integer greater than or equal to 0");
    }

    private static OptionalDouble parseOptionalPositiveDouble(String key, String value) {
        if ("TBD".equals(value)) {
            return OptionalDouble.empty();
        }
        try {
            double result = Double.parseDouble(value);
            if (!Double.isNaN(result) && !Double.isInfinite(result) && result > 0.0D) {
                return OptionalDouble.of(result);
            }
        } catch (NumberFormatException ignored) {
        }
        throw invalid(SIEGE_CATEGORY, key, value,
                "must be TBD or a finite number greater than 0");
    }

    private static OptionalInt parseOptionalPositiveInt(String key, String value) {
        if ("TBD".equals(value)) {
            return OptionalInt.empty();
        }
        try {
            int result = Integer.parseInt(value);
            if (result > 0) {
                return OptionalInt.of(result);
            }
        } catch (NumberFormatException ignored) {
        }
        throw invalid(SEASON_CATEGORY, key, value,
                "must be TBD or an integer greater than 0");
    }

    private static double finiteUnitInterval(String category, String key, String value) {
        try {
            double result = Double.parseDouble(value);
            if (!Double.isNaN(result) && !Double.isInfinite(result)
                    && result >= 0.0D && result <= 1.0D) {
                return result;
            }
        } catch (NumberFormatException ignored) {
        }
        throw invalid(category, key, value, "must be a finite number between 0.0 and 1.0");
    }

    private static void requireGreater(String category, String key, String value,
            int actual, int previous, String previousKey) {
        if (actual <= previous) {
            throw invalid(category, key, value, "must be greater than " + previousKey);
        }
    }

    private static void requireNoGreater(String category, String key, String value,
            double actual, double previous, String previousKey) {
        if (actual > previous) {
            throw invalid(category, key, value,
                    "must be less than or equal to " + previousKey);
        }
    }

    private static <T extends Enum<T>> T parseEnum(String category, String key,
            String value, Class<T> enumType) {
        try {
            return Enum.valueOf(enumType, value.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            throw invalid(category, key, value, "must be a supported value");
        }
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

    public enum EncircledCapitalArrivalPolicy {
        TBD,
        GARRISON,
        RELIEF
    }

    public enum PreBreachRepair {
        TBD,
        ENABLED,
        DISABLED
    }

    public enum BattleSupportMode {
        CURVE,
        HARD_FALLBACK
    }

    public static final class MusterSettings {
        private final int threatDistanceTiles;
        private final int budgetDailyPopulationMultiplier;
        private final int arrivalDelayHours;
        private final EncircledCapitalArrivalPolicy encircledCapitalArrivalPolicy;

        private MusterSettings(int threatDistanceTiles,
                int budgetDailyPopulationMultiplier, int arrivalDelayHours,
                EncircledCapitalArrivalPolicy encircledCapitalArrivalPolicy) {
            this.threatDistanceTiles = threatDistanceTiles;
            this.budgetDailyPopulationMultiplier = budgetDailyPopulationMultiplier;
            this.arrivalDelayHours = arrivalDelayHours;
            this.encircledCapitalArrivalPolicy = encircledCapitalArrivalPolicy;
        }

        public int getThreatDistanceTiles() {
            return threatDistanceTiles;
        }

        public int getBudgetDailyPopulationMultiplier() {
            return budgetDailyPopulationMultiplier;
        }

        public int getArrivalDelayHours() {
            return arrivalDelayHours;
        }

        public EncircledCapitalArrivalPolicy getEncircledCapitalArrivalPolicy() {
            return encircledCapitalArrivalPolicy;
        }
    }

    public static final class SiegeSettings {
        private final OptionalDouble gateHpPerApprovedHour;
        private final int normalSegmentSupportMinimumTroops;
        private final int supportFallbackGraceSeconds;
        private final PreBreachRepair preBreachRepair;
        private final boolean postBreachRepairEnabled;

        private SiegeSettings(OptionalDouble gateHpPerApprovedHour,
                int normalSegmentSupportMinimumTroops,
                int supportFallbackGraceSeconds, PreBreachRepair preBreachRepair,
                boolean postBreachRepairEnabled) {
            this.gateHpPerApprovedHour = gateHpPerApprovedHour;
            this.normalSegmentSupportMinimumTroops = normalSegmentSupportMinimumTroops;
            this.supportFallbackGraceSeconds = supportFallbackGraceSeconds;
            this.preBreachRepair = preBreachRepair;
            this.postBreachRepairEnabled = postBreachRepairEnabled;
        }

        public OptionalDouble getGateHpPerApprovedHour() {
            return gateHpPerApprovedHour;
        }

        public int getNormalSegmentSupportMinimumTroops() {
            return normalSegmentSupportMinimumTroops;
        }

        public int getSupportFallbackGraceSeconds() {
            return supportFallbackGraceSeconds;
        }

        public PreBreachRepair getPreBreachRepair() {
            return preBreachRepair;
        }

        public boolean isPostBreachRepairEnabled() {
            return postBreachRepairEnabled;
        }
    }

    public static final class BattleSupportSettings {
        private final BattleSupportMode mode;
        private final int fullDamageDistanceBlocks;
        private final int halfDamageDistanceBlocks;
        private final int lowDamageDistanceBlocks;
        private final int minimumDamageDistanceBlocks;
        private final double halfDamageMultiplier;
        private final double lowDamageMultiplier;
        private final double minimumDamageMultiplier;
        private final int hardFallbackDistanceBlocks;
        private final int openBattleRadiusBlocks;

        private BattleSupportSettings(BattleSupportMode mode, int fullDistance,
                int halfDistance, int lowDistance, int minimumDistance,
                double halfMultiplier, double lowMultiplier, double minimumMultiplier,
                int hardFallbackDistance, int openBattleRadius) {
            this.mode = mode;
            fullDamageDistanceBlocks = fullDistance;
            halfDamageDistanceBlocks = halfDistance;
            lowDamageDistanceBlocks = lowDistance;
            minimumDamageDistanceBlocks = minimumDistance;
            halfDamageMultiplier = halfMultiplier;
            lowDamageMultiplier = lowMultiplier;
            minimumDamageMultiplier = minimumMultiplier;
            hardFallbackDistanceBlocks = hardFallbackDistance;
            openBattleRadiusBlocks = openBattleRadius;
        }

        public BattleSupportMode getMode() { return mode; }
        public int getFullDamageDistanceBlocks() { return fullDamageDistanceBlocks; }
        public int getHalfDamageDistanceBlocks() { return halfDamageDistanceBlocks; }
        public int getLowDamageDistanceBlocks() { return lowDamageDistanceBlocks; }
        public int getMinimumDamageDistanceBlocks() { return minimumDamageDistanceBlocks; }
        public double getHalfDamageMultiplier() { return halfDamageMultiplier; }
        public double getLowDamageMultiplier() { return lowDamageMultiplier; }
        public double getMinimumDamageMultiplier() { return minimumDamageMultiplier; }
        public int getHardFallbackDistanceBlocks() { return hardFallbackDistanceBlocks; }
        public int getOpenBattleRadiusBlocks() { return openBattleRadiusBlocks; }
    }

    public static final class EncirclementSettings {
        private final int starvationGraceDays;
        private final int announcedAssaultNoticeHours;
        private final boolean offlineStarvationCatchUp;

        private EncirclementSettings(int starvationGraceDays,
                int announcedAssaultNoticeHours, boolean offlineStarvationCatchUp) {
            this.starvationGraceDays = starvationGraceDays;
            this.announcedAssaultNoticeHours = announcedAssaultNoticeHours;
            this.offlineStarvationCatchUp = offlineStarvationCatchUp;
        }

        public int getStarvationGraceDays() { return starvationGraceDays; }
        public int getAnnouncedAssaultNoticeHours() { return announcedAssaultNoticeHours; }
        public boolean isOfflineStarvationCatchUp() { return offlineStarvationCatchUp; }
    }

    public static final class SeasonSettings {
        private final OptionalInt minimumWarSeasonLengthDays;
        private final boolean automaticFinaleEnabled;

        private SeasonSettings(OptionalInt minimumWarSeasonLengthDays,
                boolean automaticFinaleEnabled) {
            this.minimumWarSeasonLengthDays = minimumWarSeasonLengthDays;
            this.automaticFinaleEnabled = automaticFinaleEnabled;
        }

        public OptionalInt getMinimumWarSeasonLengthDays() {
            return minimumWarSeasonLengthDays;
        }

        public boolean isAutomaticFinaleEnabled() { return automaticFinaleEnabled; }
    }
}
