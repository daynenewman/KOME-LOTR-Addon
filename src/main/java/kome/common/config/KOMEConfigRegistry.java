package kome.common.config;


import java.io.File;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraftforge.common.config.Configuration;
import kome.common.data.KOMEProgressionAchievement;

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
    public static final String GEAR_CATEGORY = "gear";
    /** Semicolon-separated category~itemId~gearPermission~armorPermission~factions~npcAllowed~bossExclusive rules. */
    public static final String GEAR_RESTRICTION_RULES = "restrictionRules";
    public static final String LOCAL_TIME = "localTime";
    public static final String TIMEZONE = "timezone";
    public static final String HOURS_PER_POPULATION_POINT = "hoursPerPopulationPoint";
    public static final String CAPTURED_BUILD_MULTIPLIER = "capturedBuildMultiplier";
    public static final String OFFLINE_POPULATION_CATCH_UP = "offlinePopulationCatchUp";
    public static final String POPULATION_CAP_ENABLED = "populationCapEnabled";
    public static final String POPULATION_CAP_VALUE = "populationCapValue";
    public static final String ENCIRCLEMENT_POPULATION_SUPPRESSION_ENABLED =
            "encirclementPopulationSuppressionEnabled";
    /** Comma-separated stable entity-id=population-cost entries. Overrides replace the formula. */
    public static final String UNIT_POPULATION_COST_OVERRIDES = "unitPopulationCostOverrides";
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
    public static final String EXTERIOR_MARGIN_BLOCKS = "exteriorMarginBlocks";
    public static final String ACTIVE_SIEGE_CHECK_IN_WINDOW_MINUTES = "activeSiegeCheckInWindowMinutes";
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
    public static final String WAR_INACTIVITY_DURATION_MILLIS = "warInactivityDurationMillis";
    public static final String WAR_BONDS_ENABLED = "warBondsEnabled";
    public static final String ATTACKER_WAR_BOND = "attackerWarBond";
    public static final String PARTICIPATION_WAR_BOND = "participationWarBond";

    private static final String DEFAULT_LOCAL_TIME = "20:00";
    private static final String DEFAULT_TIMEZONE = "America/Chicago";
    private static final DateTimeFormatter DAILY_TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm")
                    .withResolverStyle(ResolverStyle.STRICT);

    private static volatile ValidatedConfig current = new ValidatedConfig(
            new DailyBatchSettings(LocalTime.parse(DEFAULT_LOCAL_TIME), ZoneId.of(DEFAULT_TIMEZONE)),
            new PopulationSettings(10, 0.50D, true, false, OptionalInt.empty(), false),
            new MovementSettings(1, 2), new BattleSettings(20, 35, 50),
            new MusterSettings(2, 21, 24, EncircledCapitalArrivalPolicy.TBD),
            new SiegeSettings(OptionalDouble.empty(), 1, 15, PreBreachRepair.TBD, false, 192, OptionalInt.empty()),
            new BattleSupportSettings(BattleSupportMode.CURVE, 32, 48, 64, 70, 0.50D, 0.10D, 0.01D, 48, 192),
            new EncirclementSettings(10, 48, false), new SeasonSettings(OptionalInt.empty(), false, OptionalInt.empty(), false, 0, 0),
            new GearSettings(Collections.<String, GearRuleSetting>emptyMap()));

    private KOMEConfigRegistry() {
    }

    public static synchronized void load(File file) {
        Configuration configuration = new Configuration(file);
        configuration.load();
        publish(readValidated(configuration));
        if (configuration.hasChanged()) {
            configuration.save();
        }
    }

    public static ValidatedConfig readValidated(File file) {
        Configuration configuration = new Configuration(file);
        configuration.load();
        return readValidated(configuration);
    }

    public static synchronized ConfigApplyResult applyValidated(ValidatedConfig candidate,
            RuntimeActivity activity) {
        KOMEConfigChangeSet changes = KOMEConfigChangeSet.compare(currentValidated(), candidate);
        ChangeDecision decision = KOMEConfigChangeGuard.evaluate(changes, activity);
        if (decision.isAllowed()) {
            publish(candidate);
        }
        return new ConfigApplyResult(changes, decision);
    }

    private static ValidatedConfig readValidated(Configuration configuration) {
        return new ValidatedConfig(readDailyBatch(configuration), readPopulation(configuration),
                readMovement(configuration), readBattle(configuration), readMuster(configuration),
                readSiege(configuration), readBattleSupport(configuration),
                readEncirclement(configuration), readSeason(configuration), readGear(configuration));
    }

    private static void publish(ValidatedConfig config) {
        current = config;
    }

    public static ValidatedConfig currentValidated() {
        return current;
    }

    public static DailyBatchSettings dailyBatch() {
        return current.getDailyBatch();
    }

    public static PopulationSettings population() {
        return current.getPopulation();
    }

    public static MovementSettings movement() {
        return current.getMovement();
    }

    public static BattleSettings battle() {
        return current.getBattle();
    }

    public static MusterSettings muster() {
        return current.getMuster();
    }

    public static SiegeSettings siege() {
        return current.getSiege();
    }

    public static BattleSupportSettings battleSupport() {
        return current.getBattleSupport();
    }

    public static EncirclementSettings encirclement() {
        return current.getEncirclement();
    }

    public static SeasonSettings season() {
        return current.getSeason();
    }

    public static GearSettings gear() {
        return current.getGear();
    }

    private static GearSettings readGear(Configuration c) {
        String source = value(c, GEAR_CATEGORY, GEAR_RESTRICTION_RULES, "");
        Map<String, GearRuleSetting> rules = new LinkedHashMap<String, GearRuleSetting>();
        if (source.trim().length() == 0) return new GearSettings(rules);
        for (String entry : source.split(";")) {
            String[] fields = entry.trim().split("~", -1);
            if (fields.length != 7 || fields[0].trim().length() == 0 || fields[1].trim().length() == 0) {
                throw invalid(GEAR_CATEGORY, GEAR_RESTRICTION_RULES, source,
                        "must contain category~itemId~gearPermission~armorPermission~factions~npcAllowed~bossExclusive entries");
            }
            String category = fields[0].trim().toLowerCase(java.util.Locale.ROOT);
            String itemId = fields[1].trim().toLowerCase(java.util.Locale.ROOT);
            if (!("mithril".equals(category) || "utumno".equals(category) || "gondolin".equals(category)
                    || "mallorn".equals(category) || "morgul".equals(category) || "galvorn".equals(category)
                    || "stone_tool".equals(category) || "boss".equals(category) || "future".equals(category))) {
                throw invalid(GEAR_CATEGORY, GEAR_RESTRICTION_RULES, source, "category must be a supported gear restriction category");
            }
            if (itemId.indexOf(':') <= 0) throw invalid(GEAR_CATEGORY, GEAR_RESTRICTION_RULES, source,
                    "item IDs must be namespaced registry identifiers");
            java.util.Set<String> factions = new java.util.LinkedHashSet<String>();
            if (!"-".equals(fields[4].trim()) && fields[4].trim().length() != 0) for (String faction : fields[4].split("\\|")) {
                String normalized = faction.trim().toLowerCase(java.util.Locale.ROOT);
                if (normalized.length() == 0 || !factions.add(normalized)) throw invalid(GEAR_CATEGORY, GEAR_RESTRICTION_RULES, source,
                        "faction lists must contain unique non-empty keys");
            }
            boolean npcAllowed = bool(GEAR_CATEGORY, GEAR_RESTRICTION_RULES, fields[5].trim());
            boolean bossExclusive = bool(GEAR_CATEGORY, GEAR_RESTRICTION_RULES, fields[6].trim());
            String gearPermission = dashToEmpty(fields[2]);
            String armorPermission = dashToEmpty(fields[3]);
            if ((gearPermission.length() != 0 && KOMEProgressionAchievement.forID(gearPermission) == null)
                    || (armorPermission.length() != 0 && KOMEProgressionAchievement.forID(armorPermission) == null)) {
                throw invalid(GEAR_CATEGORY, GEAR_RESTRICTION_RULES, source, "progression permissions must be canonical progression IDs or -");
            }
            GearRuleSetting rule = new GearRuleSetting(category, itemId, gearPermission, armorPermission, factions, npcAllowed, bossExclusive);
            if (rules.put(itemId, rule) != null) throw invalid(GEAR_CATEGORY, GEAR_RESTRICTION_RULES, source,
                    "must not contain duplicate item IDs");
        }
        return new GearSettings(rules);
    }

    private static String dashToEmpty(String value) { return "-".equals(value.trim()) ? "" : value.trim().toLowerCase(java.util.Locale.ROOT); }

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
        String capValue = value(c, POPULATION_CATEGORY, POPULATION_CAP_VALUE, "TBD");
        OptionalInt populationCapValue = parseOptionalPositiveInt(POPULATION_CATEGORY,
                POPULATION_CAP_VALUE, capValue);
        if (cap && !populationCapValue.isPresent()) {
            throw invalid(POPULATION_CATEGORY, POPULATION_CAP_VALUE, capValue,
                    "must be configured when populationCapEnabled is true");
        }
        boolean suppression = bool(POPULATION_CATEGORY,
                ENCIRCLEMENT_POPULATION_SUPPRESSION_ENABLED,
                value(c, POPULATION_CATEGORY,
                        ENCIRCLEMENT_POPULATION_SUPPRESSION_ENABLED, "false"));
        return new PopulationSettings(hours, multiplier, catchUp, cap, populationCapValue,
                suppression, parseUnitPopulationOverrides(value(c, POPULATION_CATEGORY,
                        UNIT_POPULATION_COST_OVERRIDES, "")));
    }

    private static Map<String, Integer> parseUnitPopulationOverrides(String value) {
        Map<String, Integer> result = new LinkedHashMap<String, Integer>();
        if (value == null || value.trim().length() == 0) return result;
        for (String part : value.split(",")) {
            String[] pair = part.trim().split("=", -1);
            if (pair.length != 2 || pair[0].trim().length() == 0) throw invalid(POPULATION_CATEGORY,
                    UNIT_POPULATION_COST_OVERRIDES, value, "must be comma-separated entityId=positiveCost entries");
            int cost;
            try { cost = Integer.parseInt(pair[1].trim()); }
            catch (NumberFormatException e) { throw invalid(POPULATION_CATEGORY, UNIT_POPULATION_COST_OVERRIDES, value, "costs must be positive integers"); }
            if (cost <= 0) throw invalid(POPULATION_CATEGORY, UNIT_POPULATION_COST_OVERRIDES, value, "costs must be positive integers");
            String id = pair[0].trim().toLowerCase(java.util.Locale.ROOT);
            if (result.put(id, Integer.valueOf(cost)) != null) throw invalid(POPULATION_CATEGORY,
                    UNIT_POPULATION_COST_OVERRIDES, value, "must not contain duplicate entity IDs");
        }
        return result;
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
        int exteriorMargin = positive(SIEGE_CATEGORY, EXTERIOR_MARGIN_BLOCKS,
                value(c, SIEGE_CATEGORY, EXTERIOR_MARGIN_BLOCKS, "192"));
        OptionalInt checkInWindow = parseOptionalPositiveInt(SIEGE_CATEGORY,
                ACTIVE_SIEGE_CHECK_IN_WINDOW_MINUTES,
                value(c, SIEGE_CATEGORY, ACTIVE_SIEGE_CHECK_IN_WINDOW_MINUTES, "TBD"));
        return new SiegeSettings(gateHpPerApprovedHour, supportMinimum, fallbackGrace,
                preBreachRepair, postBreachRepairEnabled, exteriorMargin, checkInWindow);
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
        OptionalInt minimumLength = parseOptionalPositiveInt(SEASON_CATEGORY,
                MINIMUM_WAR_SEASON_LENGTH_DAYS,
                value(c, SEASON_CATEGORY, MINIMUM_WAR_SEASON_LENGTH_DAYS, "TBD"));
        String automaticFinaleValue = value(c, SEASON_CATEGORY, AUTOMATIC_FINALE_ENABLED,
                "false");
        boolean automaticFinale = bool(SEASON_CATEGORY, AUTOMATIC_FINALE_ENABLED,
                automaticFinaleValue);
        if (automaticFinale) {
            throw invalid(SEASON_CATEGORY, AUTOMATIC_FINALE_ENABLED, automaticFinaleValue,
                    "must be false because Draft 0.4 never starts Finale automatically");
        }
        OptionalInt inactivity = parseOptionalPositiveInt(SEASON_CATEGORY, WAR_INACTIVITY_DURATION_MILLIS, value(c, SEASON_CATEGORY, WAR_INACTIVITY_DURATION_MILLIS, "TBD"));
        boolean bonds = bool(SEASON_CATEGORY, WAR_BONDS_ENABLED, value(c, SEASON_CATEGORY, WAR_BONDS_ENABLED, "false"));
        int attackerBond = nonNegative(SEASON_CATEGORY, ATTACKER_WAR_BOND, value(c, SEASON_CATEGORY, ATTACKER_WAR_BOND, "0"));
        int participationBond = nonNegative(SEASON_CATEGORY, PARTICIPATION_WAR_BOND, value(c, SEASON_CATEGORY, PARTICIPATION_WAR_BOND, "0"));
        return new SeasonSettings(minimumLength, false, inactivity, bonds, attackerBond, participationBond);
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

    private static OptionalInt parseOptionalPositiveInt(String category, String key,
            String value) {
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
        throw invalid(category, key, value,
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

    public static final class ValidatedConfig {
        private final DailyBatchSettings dailyBatch;
        private final PopulationSettings population;
        private final MovementSettings movement;
        private final BattleSettings battle;
        private final MusterSettings muster;
        private final SiegeSettings siege;
        private final BattleSupportSettings battleSupport;
        private final EncirclementSettings encirclement;
        private final SeasonSettings season;
        private final GearSettings gear;

        private ValidatedConfig(DailyBatchSettings dailyBatch, PopulationSettings population,
                MovementSettings movement, BattleSettings battle, MusterSettings muster,
                SiegeSettings siege, BattleSupportSettings battleSupport,
                EncirclementSettings encirclement, SeasonSettings season, GearSettings gear) {
            this.dailyBatch = dailyBatch; this.population = population; this.movement = movement;
            this.battle = battle; this.muster = muster; this.siege = siege;
            this.battleSupport = battleSupport; this.encirclement = encirclement;
            this.season = season;
            this.gear = gear;
        }
        public DailyBatchSettings getDailyBatch() { return dailyBatch; }
        public PopulationSettings getPopulation() { return population; }
        public MovementSettings getMovement() { return movement; }
        public BattleSettings getBattle() { return battle; }
        public MusterSettings getMuster() { return muster; }
        public SiegeSettings getSiege() { return siege; }
        public BattleSupportSettings getBattleSupport() { return battleSupport; }
        public EncirclementSettings getEncirclement() { return encirclement; }
        public SeasonSettings getSeason() { return season; }
        public GearSettings getGear() { return gear; }
    }

    /** Immutable, validated overrides for the central legendary-gear registry. */
    public static final class GearSettings {
        private final Map<String, GearRuleSetting> rulesByItemId;
        private GearSettings(Map<String, GearRuleSetting> rules) {
            rulesByItemId = Collections.unmodifiableMap(new LinkedHashMap<String, GearRuleSetting>(rules));
        }
        public Map<String, GearRuleSetting> getRulesByItemId() { return rulesByItemId; }
    }

    public static final class GearRuleSetting {
        private final String category, itemId, gearPermission, armorPermission;
        private final java.util.Set<String> permittedFactions;
        private final boolean npcAllowed, bossExclusive;
        private GearRuleSetting(String category, String itemId, String gearPermission,
                String armorPermission, java.util.Set<String> factions, boolean npcAllowed,
                boolean bossExclusive) {
            this.category = category; this.itemId = itemId; this.gearPermission = gearPermission;
            this.armorPermission = armorPermission;
            this.permittedFactions = Collections.unmodifiableSet(new java.util.LinkedHashSet<String>(factions));
            this.npcAllowed = npcAllowed; this.bossExclusive = bossExclusive;
        }
        public String getCategory() { return category; }
        public String getItemId() { return itemId; }
        public String getGearPermission() { return gearPermission; }
        public String getArmorPermission() { return armorPermission; }
        public java.util.Set<String> getPermittedFactions() { return permittedFactions; }
        public boolean isNpcAllowed() { return npcAllowed; }
        public boolean isBossExclusive() { return bossExclusive; }
        @Override public boolean equals(Object other) {
            if (!(other instanceof GearRuleSetting)) return false;
            GearRuleSetting that = (GearRuleSetting) other;
            return category.equals(that.category) && itemId.equals(that.itemId)
                    && gearPermission.equals(that.gearPermission) && armorPermission.equals(that.armorPermission)
                    && permittedFactions.equals(that.permittedFactions) && npcAllowed == that.npcAllowed
                    && bossExclusive == that.bossExclusive;
        }
        @Override public int hashCode() {
            int result = category.hashCode(); result = 31 * result + itemId.hashCode();
            result = 31 * result + gearPermission.hashCode(); result = 31 * result + armorPermission.hashCode();
            result = 31 * result + permittedFactions.hashCode(); result = 31 * result + (npcAllowed ? 1 : 0);
            return 31 * result + (bossExclusive ? 1 : 0);
        }
        @Override public String toString() {
            return category + "~" + itemId + "~" + (gearPermission.length() == 0 ? "-" : gearPermission)
                    + "~" + (armorPermission.length() == 0 ? "-" : armorPermission) + "~"
                    + (permittedFactions.isEmpty() ? "-" : join(permittedFactions)) + "~" + npcAllowed + "~" + bossExclusive;
        }
        private static String join(java.util.Set<String> values) {
            StringBuilder result = new StringBuilder();
            for (String value : values) { if (result.length() > 0) result.append('|'); result.append(value); }
            return result.toString();
        }
    }

    public interface RuntimeActivity {
        boolean isDailyTransactionInProgress();
        boolean isActiveSiegeInProgress();
        java.util.Collection<String> getActiveSiegeLockedConfigKeys();
    }

    public static final class ConfigApplyResult {
        private final KOMEConfigChangeSet changes;
        private final ChangeDecision decision;
        private ConfigApplyResult(KOMEConfigChangeSet changes, ChangeDecision decision) {
            this.changes = changes; this.decision = decision;
        }
        public KOMEConfigChangeSet getChanges() { return changes; }
        public ChangeDecision getDecision() { return decision; }
    }

    public static final class PopulationSettings {
        private final int hoursPerPopulationPoint;
        private final double capturedBuildMultiplier;
        private final boolean offlinePopulationCatchUp;
        private final boolean populationCapEnabled;
        private final OptionalInt populationCapValue;
        private final boolean encirclementPopulationSuppressionEnabled;
        private final Map<String, Integer> unitPopulationCostOverrides;

        private PopulationSettings(int hours, double multiplier, boolean catchUp,
                boolean cap, OptionalInt populationCapValue, boolean suppression) {
            this(hours, multiplier, catchUp, cap, populationCapValue, suppression,
                    Collections.<String, Integer>emptyMap());
        }
        private PopulationSettings(int hours, double multiplier, boolean catchUp,
                boolean cap, OptionalInt populationCapValue, boolean suppression,
                Map<String, Integer> overrides) {
            hoursPerPopulationPoint = hours;
            capturedBuildMultiplier = multiplier;
            offlinePopulationCatchUp = catchUp;
            populationCapEnabled = cap;
            this.populationCapValue = populationCapValue;
            encirclementPopulationSuppressionEnabled = suppression;
            unitPopulationCostOverrides = Collections.unmodifiableMap(new LinkedHashMap<String, Integer>(overrides));
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

        public OptionalInt getPopulationCapValue() {
            return populationCapValue;
        }

        public boolean isEncirclementPopulationSuppressionEnabled() {
            return encirclementPopulationSuppressionEnabled;
        }

        public Map<String, Integer> getUnitPopulationCostOverrides() { return unitPopulationCostOverrides; }
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
        private final int exteriorMarginBlocks;
        private final OptionalInt activeSiegeCheckInWindowMinutes;

        private SiegeSettings(OptionalDouble gateHpPerApprovedHour,
                int normalSegmentSupportMinimumTroops,
                int supportFallbackGraceSeconds, PreBreachRepair preBreachRepair,
                boolean postBreachRepairEnabled, int exteriorMarginBlocks,
                OptionalInt activeSiegeCheckInWindowMinutes) {
            this.gateHpPerApprovedHour = gateHpPerApprovedHour;
            this.normalSegmentSupportMinimumTroops = normalSegmentSupportMinimumTroops;
            this.supportFallbackGraceSeconds = supportFallbackGraceSeconds;
            this.preBreachRepair = preBreachRepair;
            this.postBreachRepairEnabled = postBreachRepairEnabled;
            this.exteriorMarginBlocks = exteriorMarginBlocks;
            this.activeSiegeCheckInWindowMinutes = activeSiegeCheckInWindowMinutes;
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

        public int getExteriorMarginBlocks() {
            return exteriorMarginBlocks;
        }

        public OptionalInt getActiveSiegeCheckInWindowMinutes() {
            return activeSiegeCheckInWindowMinutes;
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
        private final OptionalInt inactivityDurationMillis; private final boolean warBondsEnabled; private final int attackerWarBond, participationWarBond;
        private final OptionalInt minimumWarSeasonLengthDays;
        private final boolean automaticFinaleEnabled;

        private SeasonSettings(OptionalInt minimumWarSeasonLengthDays,
                boolean automaticFinaleEnabled, OptionalInt inactivityDurationMillis, boolean warBondsEnabled, int attackerWarBond, int participationWarBond) {
            this.minimumWarSeasonLengthDays = minimumWarSeasonLengthDays;
            this.automaticFinaleEnabled = automaticFinaleEnabled;
            this.inactivityDurationMillis=inactivityDurationMillis; this.warBondsEnabled=warBondsEnabled; this.attackerWarBond=attackerWarBond; this.participationWarBond=participationWarBond;
        }

        public OptionalInt getMinimumWarSeasonLengthDays() {
            return minimumWarSeasonLengthDays;
        }

        public boolean isAutomaticFinaleEnabled() { return automaticFinaleEnabled; }
        public OptionalInt getWarInactivityDurationMillis(){return inactivityDurationMillis;}
        public boolean isWarBondsEnabled(){return warBondsEnabled;}
        public int getAttackerWarBond(){return attackerWarBond;}
        public int getParticipationWarBond(){return participationWarBond;}
    }
}
