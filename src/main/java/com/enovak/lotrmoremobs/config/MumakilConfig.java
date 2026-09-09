package com.enovak.lotrmoremobs.config;

import java.io.File;
import java.util.Arrays;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

/**
 * Player-facing configuration for the addon.
 *
 * CONFIG_CATEGORIES_V2_1_0_1
 *
 * Content registrations stay stable for world/save compatibility. The master
 * feature switches control normal gameplay entry points instead of removing
 * registered blocks, items, tile entities, or entities from the registry.
 */
public final class MumakilConfig {

    public static final String CATEGORY_MUMAKIL = "mumakil";
    public static final String CATEGORY_PICKUP_FILTER = "item pickup filter";
    public static final String CATEGORY_MORTAL_GANDALF = "mortal gandalf";
    public static final String CATEGORY_SIEGE_GATES = "siege gates";
    public static final String CATEGORY_BATTLE_RAMS = "battle rams";
    public static final String CATEGORY_PLAYER_ANIMATIONS = "player animations";
    public static final String CATEGORY_SERVER_GAMEPLAY = "server gameplay";

    /* Legacy category names retained only so existing config values can migrate. */
    private static final String LEGACY_CATEGORY_GAMEPLAY = "general gameplay";
    private static final String LEGACY_CATEGORY_SPAWNING = "mumak spawning";

    private static final boolean DEFAULT_ENABLE_MUMAKIL = true;
    private static final boolean DEFAULT_ENABLE_NATURAL_MUMAK_SPAWNING = true;
    private static final int DEFAULT_MUMAK_SPAWN_WEIGHT = 8;
    private static final int DEFAULT_MUMAK_MIN_GROUP_SIZE = 3;
    private static final int DEFAULT_MUMAK_MAX_GROUP_SIZE = 5;
    private static final boolean DEFAULT_MUMAK_BREAKS_TREES = true;

    private static final int DEFAULT_HOME_UNIT_ROLL_DENOMINATOR = 50;
    private static final boolean DEFAULT_ENABLE_CONQUEST_FORMATIONS = true;
    private static final float DEFAULT_CONQUEST_FORMATION_MINIMUM_CONQUEST = 500.0F;
    private static final int DEFAULT_CONQUEST_UNIT_ROLL_DENOMINATOR = 100;
    private static final int DEFAULT_CONQUEST_MINIMUM_DENOMINATOR = 50;
    private static final float DEFAULT_CONQUEST_STRENGTH_PER_STEP = 100.0F;
    private static final boolean DEFAULT_ENABLE_INVASION_FORMATIONS = true;
    private static final int DEFAULT_INVASION_UNIT_ROLL_DENOMINATOR = 40;

    private static final boolean DEFAULT_ENABLE_PICKUP_FILTER = true;
    private static final boolean DEFAULT_MORTAL_GANDALF = false;
    private static final boolean DEFAULT_ENABLE_SIEGE_GATES = true;
    private static final int DEFAULT_GATE_HEALTH = 1000;
    private static final boolean DEFAULT_ENABLE_SIEGE_GATE_BREACH_NPC_RALLY =
            true;
    private static final boolean DEFAULT_ENABLE_BATTLE_RAMS = true;
    private static final int DEFAULT_RAM_SIEGE_DAMAGE = 100;
    private static final int DEFAULT_RAM_CARRIER_RESPAWN_DELAY_SECONDS = 30;
    private static final boolean DEFAULT_MODERN_PLAYER_ANIMATIONS = true;
    private static final boolean DEFAULT_NPC_BOMBER_BLOCK_DAMAGE = true;

    private static final int MIN_DENOMINATOR = 1;
    private static final int MAX_DENOMINATOR = 10000;
    private static final float MIN_CONQUEST = 0.0F;
    private static final float MAX_CONQUEST = 100000.0F;
    private static final float MIN_CONQUEST_STEP = 1.0F;
    private static final float MAX_CONQUEST_STEP = 100000.0F;
    private static final int MIN_MUMAK_SPAWN_WEIGHT = 1;
    private static final int MAX_MUMAK_SPAWN_WEIGHT = 1000;
    private static final int MIN_MUMAK_GROUP_SIZE = 1;
    private static final int MAX_MUMAK_GROUP_SIZE = 20;
    private static final int MIN_GATE_HEALTH = 1;
    private static final int MAX_GATE_HEALTH = 1000000;
    private static final int MIN_RAM_DAMAGE = 1;
    private static final int MAX_RAM_DAMAGE = 1000000;
    private static final int MIN_RAM_RESPAWN_SECONDS = 1;
    private static final int MAX_RAM_RESPAWN_SECONDS = 3600;

    /*
     * These are intentionally internal safeguards, not player-facing config.
     * They used to appear in the GUI as placement retry options, but they are
     * too implementation-specific to be useful to ordinary players.
     */
    public static final int placementRetryIntervalTicks = 20;
    public static final int placementRetryTimeoutTicks = 200;
    public static final int maxPlacementRetries = 10;
    public static final int maxApprovedRecordsProcessedPerTick = 2;

    private static final String ROLL_RESULT_DESCRIPTION =
            " A denominator of 1 means every otherwise eligible NPC passes "
                    + "its random roll. Placement, capacity, terrain, "
                    + "nearby-entity, loaded-chunk, and transactional factory "
                    + "checks can still preserve the ordinary NPC.";

    public static volatile boolean enableMumakil = DEFAULT_ENABLE_MUMAKIL;
    public static volatile boolean enableNaturalMumakSpawning =
            DEFAULT_ENABLE_NATURAL_MUMAK_SPAWNING;
    public static volatile int mumakNaturalSpawnWeight =
            DEFAULT_MUMAK_SPAWN_WEIGHT;
    public static volatile int mumakNaturalSpawnMinGroupSize =
            DEFAULT_MUMAK_MIN_GROUP_SIZE;
    public static volatile int mumakNaturalSpawnMaxGroupSize =
            DEFAULT_MUMAK_MAX_GROUP_SIZE;
    public static volatile boolean mumakBreaksTrees =
            DEFAULT_MUMAK_BREAKS_TREES;

    public static volatile int homeUnitRollDenominator =
            DEFAULT_HOME_UNIT_ROLL_DENOMINATOR;
    public static volatile boolean enableMumakWarFormationsInConquest =
            DEFAULT_ENABLE_CONQUEST_FORMATIONS;
    public static volatile float conquestFormationMinimumConquest =
            DEFAULT_CONQUEST_FORMATION_MINIMUM_CONQUEST;
    public static volatile int conquestUnitRollDenominator =
            DEFAULT_CONQUEST_UNIT_ROLL_DENOMINATOR;
    public static volatile int conquestMinimumDenominator =
            DEFAULT_CONQUEST_MINIMUM_DENOMINATOR;
    public static volatile float conquestStrengthPerStep =
            DEFAULT_CONQUEST_STRENGTH_PER_STEP;
    public static volatile boolean enableMumakWarFormationsInInvasions =
            DEFAULT_ENABLE_INVASION_FORMATIONS;
    public static volatile int invasionUnitRollDenominator =
            DEFAULT_INVASION_UNIT_ROLL_DENOMINATOR;

    public static volatile boolean enableItemPickupFilter =
            DEFAULT_ENABLE_PICKUP_FILTER;
    public static volatile boolean mortalGandalf = DEFAULT_MORTAL_GANDALF;
    public static volatile boolean enableSiegeGates =
            DEFAULT_ENABLE_SIEGE_GATES;
    public static volatile int defaultGateHealth = DEFAULT_GATE_HEALTH;
    public static volatile boolean enableSiegeGateBreachNpcRally =
            DEFAULT_ENABLE_SIEGE_GATE_BREACH_NPC_RALLY;
    public static volatile boolean enableBattleRams =
            DEFAULT_ENABLE_BATTLE_RAMS;
    public static volatile int ramSiegeDamage = DEFAULT_RAM_SIEGE_DAMAGE;
    public static volatile int ramCarrierRespawnDelaySeconds =
            DEFAULT_RAM_CARRIER_RESPAWN_DELAY_SECONDS;
    public static volatile boolean modernPlayerAnimations =
            DEFAULT_MODERN_PLAYER_ANIMATIONS;
    public static volatile boolean npcBomberBlockDamage =
            DEFAULT_NPC_BOMBER_BLOCK_DAMAGE;

    /*
     * Invasion-duration value assigned to each formation member.
     * Total formation value: 15 Mumak + 1 driver + 17 archers = 33 points.
     */
    public static final int INVASION_MUMAK_BUDGET_VALUE = 15;
    public static final int INVASION_DRIVER_BUDGET_VALUE = 1;
    public static final int INVASION_ARCHER_BUDGET_VALUE = 1;
    public static final int INVASION_FORMATION_BUDGET_VALUE =
            INVASION_MUMAK_BUDGET_VALUE
                    + INVASION_DRIVER_BUDGET_VALUE
                    + 17 * INVASION_ARCHER_BUDGET_VALUE;

    private static Configuration configuration;
    private static boolean runtimeValuesInitialized;

    private MumakilConfig() {
    }

    public static synchronized void load(File file) {
        configuration = new Configuration(file);
        configuration.load();
        runtimeValuesInitialized = false;
        syncFromConfiguration();
    }

    public static synchronized void syncFromConfiguration() {
        if (configuration == null) {
            throw new IllegalStateException(
                    "Mumak configuration has not been initialized"
            );
        }

        boolean preserveRestartRequiredValues = runtimeValuesInitialized;
        boolean activeEnableMumakil = enableMumakil;
        boolean activeNaturalSpawning = enableNaturalMumakSpawning;
        int activeSpawnWeight = mumakNaturalSpawnWeight;
        int activeMinHerdSize = mumakNaturalSpawnMinGroupSize;
        int activeMaxHerdSize = mumakNaturalSpawnMaxGroupSize;
        boolean activePickupFilter = enableItemPickupFilter;
        boolean activeSiegeGates = enableSiegeGates;
        boolean activeBattleRams = enableBattleRams;

        syncMumakilCategory();
        syncPickupFilterCategory();
        syncMortalGandalfCategory();
        syncSiegeGateCategory();
        syncBattleRamCategory();
        syncPlayerAnimationsCategory();
        syncServerGameplayCategory();

        if (conquestMinimumDenominator > conquestUnitRollDenominator) {
            conquestMinimumDenominator = conquestUnitRollDenominator;
            configuration.get(
                    CATEGORY_MUMAKIL,
                    "conquestMinimumDenominator",
                    DEFAULT_CONQUEST_MINIMUM_DENOMINATOR
            ).set(conquestMinimumDenominator);
        }

        if (mumakNaturalSpawnMinGroupSize > mumakNaturalSpawnMaxGroupSize) {
            mumakNaturalSpawnMaxGroupSize = mumakNaturalSpawnMinGroupSize;
            configuration.get(
                    CATEGORY_MUMAKIL,
                    "naturalSpawnMaxHerdSize",
                    DEFAULT_MUMAK_MAX_GROUP_SIZE
            ).set(mumakNaturalSpawnMaxGroupSize);
        }

        removeLegacyCategories();

        if (configuration.hasChanged()) {
            configuration.save();
        }

        if (preserveRestartRequiredValues) {
            enableMumakil = activeEnableMumakil;
            enableNaturalMumakSpawning = activeNaturalSpawning;
            mumakNaturalSpawnWeight = activeSpawnWeight;
            mumakNaturalSpawnMinGroupSize = activeMinHerdSize;
            mumakNaturalSpawnMaxGroupSize = activeMaxHerdSize;
            enableItemPickupFilter = activePickupFilter;
            enableSiegeGates = activeSiegeGates;
            enableBattleRams = activeBattleRams;
        } else {
            runtimeValuesInitialized = true;
        }

        printSummary();
    }

    private static void syncMumakilCategory() {
        configuration.setCategoryComment(
                CATEGORY_MUMAKIL,
                "Mumakil spawning, world interaction, and Near Harad war "
                        + "formation settings. The master switch is restart-required "
                        + "so registration-time integrations stay predictable."
        );
        configuration.setCategoryLanguageKey(
                CATEGORY_MUMAKIL,
                "config.lotrmoremobs.category.mumakil"
        );
        configuration.setCategoryPropertyOrder(
                CATEGORY_MUMAKIL,
                Arrays.asList(
                        "enableMumakilFeatures",
                        "enableNaturalMumakSpawning",
                        "naturalSpawnWeight",
                        "naturalSpawnMinHerdSize",
                        "naturalSpawnMaxHerdSize",
                        "mumakBreaksTrees",
                        "homeUnitRollDenominator",
                        "enableMumakWarFormationsInConquest",
                        "conquestFormationMinimumConquest",
                        "conquestUnitRollDenominator",
                        "conquestMinimumDenominator",
                        "conquestStrengthPerStep",
                        "enableMumakWarFormationsInInvasions",
                        "invasionUnitRollDenominator"
                )
        );

        enableMumakil = configuration.getBoolean(
                "enableMumakilFeatures",
                CATEGORY_MUMAKIL,
                DEFAULT_ENABLE_MUMAKIL,
                "Master switch for Mumakil gameplay. Disabling this prevents "
                        + "normal spawning, hiring/trader integration, Mumakil "
                        + "recipes/achievements, and Mumakil event handlers. "
                        + "Registered content remains available internally so "
                        + "existing worlds can still load safely. Restart required: Yes.",
                "config.lotrmoremobs.enableMumakilFeatures"
        );

        enableNaturalMumakSpawning = configuration.getBoolean(
                "enableNaturalMumakSpawning",
                CATEGORY_MUMAKIL,
                DEFAULT_ENABLE_NATURAL_MUMAK_SPAWNING,
                "Controls natural wild Mumakil herd spawning in the configured "
                        + "southern LOTR regions. Restart required: Yes.",
                "config.lotrmoremobs.enableNaturalMumakSpawning"
        );

        mumakNaturalSpawnWeight = configuration.getInt(
                "naturalSpawnWeight",
                CATEGORY_MUMAKIL,
                DEFAULT_MUMAK_SPAWN_WEIGHT,
                MIN_MUMAK_SPAWN_WEIGHT,
                MAX_MUMAK_SPAWN_WEIGHT,
                "Relative natural spawn weight for wild Mumakil. Higher values "
                        + "make Mumakil more common relative to other creatures. "
                        + "Restart required: Yes.",
                "config.lotrmoremobs.naturalSpawnWeight"
        );

        mumakNaturalSpawnMinGroupSize = configuration.getInt(
                "naturalSpawnMinHerdSize",
                CATEGORY_MUMAKIL,
                DEFAULT_MUMAK_MIN_GROUP_SIZE,
                MIN_MUMAK_GROUP_SIZE,
                MAX_MUMAK_GROUP_SIZE,
                "Minimum number of Mumakil in a naturally spawned herd. "
                        + "Restart required: Yes.",
                "config.lotrmoremobs.naturalSpawnMinHerdSize"
        );

        mumakNaturalSpawnMaxGroupSize = configuration.getInt(
                "naturalSpawnMaxHerdSize",
                CATEGORY_MUMAKIL,
                DEFAULT_MUMAK_MAX_GROUP_SIZE,
                MIN_MUMAK_GROUP_SIZE,
                MAX_MUMAK_GROUP_SIZE,
                "Maximum number of Mumakil in a naturally spawned herd. "
                        + "Restart required: Yes.",
                "config.lotrmoremobs.naturalSpawnMaxHerdSize"
        );

        mumakBreaksTrees = configuration.getBoolean(
                "mumakBreaksTrees",
                CATEGORY_MUMAKIL,
                DEFAULT_MUMAK_BREAKS_TREES,
                "When enabled, an aggressive unmounted Mumak may break logs "
                        + "and leaves that block its path to a target. Default: enabled. "
                        + "Restart required: No.",
                "config.lotrmoremobs.mumakBreaksTrees"
        );

        homeUnitRollDenominator = configuration.getInt(
                "homeUnitRollDenominator",
                CATEGORY_MUMAKIL,
                legacyInt(
                        LEGACY_CATEGORY_SPAWNING,
                        "homeUnitRollDenominator",
                        DEFAULT_HOME_UNIT_ROLL_DENOMINATOR
                ),
                MIN_DENOMINATOR,
                MAX_DENOMINATOR,
                "One independent 1/N replacement roll per eligible native "
                        + "Near Harad military NPC. Lower values increase "
                        + "formation frequency; higher values reduce it."
                        + ROLL_RESULT_DESCRIPTION
                        + " Restart required: No.",
                "config.lotrmoremobs.homeUnitRollDenominator"
        );

        enableMumakWarFormationsInConquest = configuration.getBoolean(
                "enableMumakWarFormationsInConquest",
                CATEGORY_MUMAKIL,
                legacyBoolean(
                        LEGACY_CATEGORY_SPAWNING,
                        "enableMumakWarFormationsInConquest",
                        DEFAULT_ENABLE_CONQUEST_FORMATIONS
                ),
                "Controls whether Mumak-with-howdah formations may spawn "
                        + "through Near Harad conquest spawning at or above the "
                        + "configured conquest threshold. Restart required: No.",
                "config.lotrmoremobs.enableConquestFormations"
        );

        conquestFormationMinimumConquest = configuration.getFloat(
                "conquestFormationMinimumConquest",
                CATEGORY_MUMAKIL,
                legacyFloat(
                        LEGACY_CATEGORY_SPAWNING,
                        "conquestFormationMinimumConquest",
                        DEFAULT_CONQUEST_FORMATION_MINIMUM_CONQUEST
                ),
                MIN_CONQUEST,
                MAX_CONQUEST,
                "Minimum direct Near Harad conquest required at the NPC's "
                        + "coordinates before a conquest replacement roll is "
                        + "allowed. Restart required: No.",
                "config.lotrmoremobs.conquestMinimum"
        );

        conquestUnitRollDenominator = configuration.getInt(
                "conquestUnitRollDenominator",
                CATEGORY_MUMAKIL,
                legacyInt(
                        LEGACY_CATEGORY_SPAWNING,
                        "conquestUnitRollDenominator",
                        DEFAULT_CONQUEST_UNIT_ROLL_DENOMINATOR
                ),
                MIN_DENOMINATOR,
                MAX_DENOMINATOR,
                "Base 1/N replacement denominator for each eligible Near "
                        + "Harad conquest military NPC. Lower values increase "
                        + "formation frequency; higher values reduce it."
                        + ROLL_RESULT_DESCRIPTION
                        + " Restart required: No.",
                "config.lotrmoremobs.conquestBaseDenominator"
        );

        conquestMinimumDenominator = configuration.getInt(
                "conquestMinimumDenominator",
                CATEGORY_MUMAKIL,
                legacyInt(
                        LEGACY_CATEGORY_SPAWNING,
                        "conquestMinimumDenominator",
                        DEFAULT_CONQUEST_MINIMUM_DENOMINATOR
                ),
                MIN_DENOMINATOR,
                MAX_DENOMINATOR,
                "Lowest denominator conquest scaling may reach. This value is "
                        + "clamped so it cannot exceed the conquest base "
                        + "denominator. Restart required: No.",
                "config.lotrmoremobs.conquestMinimumDenominator"
        );

        conquestStrengthPerStep = configuration.getFloat(
                "conquestStrengthPerStep",
                CATEGORY_MUMAKIL,
                legacyFloat(
                        LEGACY_CATEGORY_SPAWNING,
                        "conquestStrengthPerStep",
                        DEFAULT_CONQUEST_STRENGTH_PER_STEP
                ),
                MIN_CONQUEST_STEP,
                MAX_CONQUEST_STEP,
                "Direct Near Harad conquest above the minimum required to "
                        + "reduce the conquest denominator by one. Restart required: No.",
                "config.lotrmoremobs.conquestStrengthPerStep"
        );

        enableMumakWarFormationsInInvasions = configuration.getBoolean(
                "enableMumakWarFormationsInInvasions",
                CATEGORY_MUMAKIL,
                legacyBoolean(
                        LEGACY_CATEGORY_SPAWNING,
                        "enableMumakWarFormationsInInvasions",
                        DEFAULT_ENABLE_INVASION_FORMATIONS
                ),
                "Controls whether ordinary units from eligible Near Harad "
                        + "invasions may be replaced by Mumak-with-howdah "
                        + "formations. Restart required: No.",
                "config.lotrmoremobs.enableInvasionFormations"
        );

        invasionUnitRollDenominator = configuration.getInt(
                "invasionUnitRollDenominator",
                CATEGORY_MUMAKIL,
                legacyInt(
                        LEGACY_CATEGORY_SPAWNING,
                        "invasionUnitRollDenominator",
                        DEFAULT_INVASION_UNIT_ROLL_DENOMINATOR
                ),
                MIN_DENOMINATOR,
                MAX_DENOMINATOR,
                "One independent 1/N replacement roll per eligible ordinary "
                        + "NPC in a configured Near Harad invasion. Lower values "
                        + "increase formation frequency; higher values reduce it."
                        + ROLL_RESULT_DESCRIPTION
                        + " Restart required: No.",
                "config.lotrmoremobs.invasionUnitRollDenominator"
        );

        markPropertiesRestartRequired(
                CATEGORY_MUMAKIL,
                "enableMumakilFeatures",
                "enableNaturalMumakSpawning",
                "naturalSpawnWeight",
                "naturalSpawnMinHerdSize",
                "naturalSpawnMaxHerdSize"
        );
        markPropertiesRuntimeEditable(
                CATEGORY_MUMAKIL,
                "mumakBreaksTrees",
                "homeUnitRollDenominator",
                "enableMumakWarFormationsInConquest",
                "conquestFormationMinimumConquest",
                "conquestUnitRollDenominator",
                "conquestMinimumDenominator",
                "conquestStrengthPerStep",
                "enableMumakWarFormationsInInvasions",
                "invasionUnitRollDenominator"
        );
    }

    private static void syncPickupFilterCategory() {
        configuration.setCategoryComment(
                CATEGORY_PICKUP_FILTER,
                "Controls the inventory item-pickup filtering feature. Existing "
                        + "player filter lists are preserved while disabled."
        );
        configuration.setCategoryLanguageKey(
                CATEGORY_PICKUP_FILTER,
                "config.lotrmoremobs.category.itemPickupFilter"
        );
        configuration.setCategoryPropertyOrder(
                CATEGORY_PICKUP_FILTER,
                Arrays.asList("enableItemPickupFilter")
        );
        enableItemPickupFilter = configuration.getBoolean(
                "enableItemPickupFilter",
                CATEGORY_PICKUP_FILTER,
                DEFAULT_ENABLE_PICKUP_FILTER,
                "Master switch for the item pickup filter, inventory button, "
                        + "filter commands, and pickup interception. Restart required: Yes.",
                "config.lotrmoremobs.enableItemPickupFilter"
        );
        markPropertiesRestartRequired(
                CATEGORY_PICKUP_FILTER,
                "enableItemPickupFilter"
        );
    }

    private static void syncMortalGandalfCategory() {
        configuration.setCategoryComment(
                CATEGORY_MORTAL_GANDALF,
                "Optional change to the LOTR Mod's normal Gandalf immortality."
        );
        configuration.setCategoryLanguageKey(
                CATEGORY_MORTAL_GANDALF,
                "config.lotrmoremobs.category.mortalGandalf"
        );
        configuration.setCategoryPropertyOrder(
                CATEGORY_MORTAL_GANDALF,
                Arrays.asList("enableMortalGandalf")
        );
        mortalGandalf = configuration.getBoolean(
                "enableMortalGandalf",
                CATEGORY_MORTAL_GANDALF,
                legacyBoolean(
                        LEGACY_CATEGORY_GAMEPLAY,
                        "mortalGandalf",
                        DEFAULT_MORTAL_GANDALF
                ),
                "When enabled, Gandalf can be damaged and killed by normal "
                        + "Minecraft and LOTR damage sources. When disabled, "
                        + "the LOTR Mod's standard Gandalf immortality is preserved. "
                        + "Restart required: No.",
                "config.lotrmoremobs.mortalGandalf"
        );
        markPropertiesRuntimeEditable(
                CATEGORY_MORTAL_GANDALF,
                "enableMortalGandalf"
        );
    }

    private static void syncSiegeGateCategory() {
        configuration.setCategoryComment(
                CATEGORY_SIEGE_GATES,
                "Siege Gate feature and default balance settings. Existing gates "
                        + "retain the maximum health saved in their NBT."
        );
        configuration.setCategoryLanguageKey(
                CATEGORY_SIEGE_GATES,
                "config.lotrmoremobs.category.siegeGates"
        );
        configuration.setCategoryPropertyOrder(
                CATEGORY_SIEGE_GATES,
                Arrays.asList(
                        "enableSiegeGates",
                        "defaultGateHealth",
                        "enableBreachNpcRally"
                )
        );
        enableSiegeGates = configuration.getBoolean(
                "enableSiegeGates",
                CATEGORY_SIEGE_GATES,
                DEFAULT_ENABLE_SIEGE_GATES,
                "Master switch for normal Siege Gate creation, management, "
                        + "client controls, and gate-aware AI behavior. Registered "
                        + "gate blocks and saved gate data remain loadable. Restart required: Yes.",
                "config.lotrmoremobs.enableSiegeGates"
        );
        defaultGateHealth = configuration.getInt(
                "defaultGateHealth",
                CATEGORY_SIEGE_GATES,
                DEFAULT_GATE_HEALTH,
                MIN_GATE_HEALTH,
                MAX_GATE_HEALTH,
                "Maximum health assigned to newly created Siege Gates. Existing "
                        + "gates keep their saved maximum health. Default: 1000. "
                        + "Restart required: No.",
                "config.lotrmoremobs.defaultGateHealth"
        );
        enableSiegeGateBreachNpcRally = configuration.getBoolean(
                "enableBreachNpcRally",
                CATEGORY_SIEGE_GATES,
                DEFAULT_ENABLE_SIEGE_GATE_BREACH_NPC_RALLY,
                "When enabled, nearby allied defenders and hostile attackers "
                        + "temporarily rush toward a Siege Gate when it is newly "
                        + "breached. Normal LOTR combat AI still takes priority. "
                        + "Restart required: No.",
                "config.lotrmoremobs.enableBreachNpcRally"
        );
        markPropertiesRestartRequired(
                CATEGORY_SIEGE_GATES,
                "enableSiegeGates"
        );
        markPropertiesRuntimeEditable(
                CATEGORY_SIEGE_GATES,
                "defaultGateHealth",
                "enableBreachNpcRally"
        );
    }

    private static void syncBattleRamCategory() {
        configuration.setCategoryComment(
                CATEGORY_BATTLE_RAMS,
                "Battle Ram feature and combat/crew balance settings."
        );
        configuration.setCategoryLanguageKey(
                CATEGORY_BATTLE_RAMS,
                "config.lotrmoremobs.category.battleRams"
        );
        configuration.setCategoryPropertyOrder(
                CATEGORY_BATTLE_RAMS,
                Arrays.asList(
                        "enableBattleRams",
                        "ramDamagePerImpact",
                        "ramCarrierRespawnDelaySeconds"
                )
        );
        enableBattleRams = configuration.getBoolean(
                "enableBattleRams",
                CATEGORY_BATTLE_RAMS,
                DEFAULT_ENABLE_BATTLE_RAMS,
                "Master switch for normal Battle Ram hiring, controls, targeting, "
                        + "and active siege behavior. Registered ram entities remain "
                        + "loadable for world safety. Restart required: Yes.",
                "config.lotrmoremobs.enableBattleRams"
        );
        ramSiegeDamage = configuration.getInt(
                "ramDamagePerImpact",
                CATEGORY_BATTLE_RAMS,
                DEFAULT_RAM_SIEGE_DAMAGE,
                MIN_RAM_DAMAGE,
                MAX_RAM_DAMAGE,
                "Siege damage dealt to a gate by each successful Battle Ram "
                        + "impact. Default: 100. Restart required: No.",
                "config.lotrmoremobs.ramDamagePerImpact"
        );
        ramCarrierRespawnDelaySeconds = configuration.getInt(
                "ramCarrierRespawnDelaySeconds",
                CATEGORY_BATTLE_RAMS,
                DEFAULT_RAM_CARRIER_RESPAWN_DELAY_SECONDS,
                MIN_RAM_RESPAWN_SECONDS,
                MAX_RAM_RESPAWN_SECONDS,
                "Delay in seconds before a dead Battle Ram carrier becomes "
                        + "eligible to respawn. Default: 30 seconds. Restart required: No.",
                "config.lotrmoremobs.ramCarrierRespawnDelaySeconds"
        );
        markPropertiesRestartRequired(
                CATEGORY_BATTLE_RAMS,
                "enableBattleRams"
        );
        markPropertiesRuntimeEditable(
                CATEGORY_BATTLE_RAMS,
                "ramDamagePerImpact",
                "ramCarrierRespawnDelaySeconds"
        );
    }

    private static void syncPlayerAnimationsCategory() {
        configuration.setCategoryComment(
                CATEGORY_PLAYER_ANIMATIONS,
                "Server/world-controlled player movement and presentation settings. In "
                        + "multiplayer, the server value overrides each client's "
                        + "local preference so all players use the same movement mode."
        );
        configuration.setCategoryLanguageKey(
                CATEGORY_PLAYER_ANIMATIONS,
                "config.lotrmoremobs.category.playerAnimations"
        );
        configuration.setCategoryPropertyOrder(
                CATEGORY_PLAYER_ANIMATIONS,
                Arrays.asList("modernPlayerAnimations")
        );
        modernPlayerAnimations = configuration.getBoolean(
                "modernPlayerAnimations",
                CATEGORY_PLAYER_ANIMATIONS,
                DEFAULT_MODERN_PLAYER_ANIMATIONS,
                "When enabled, players use KOME's integrated modern swimming, "
                        + "crawling, crouching, player sizing, camera, and matching "
                        + "1.13-style presentation. When disabled, player movement, "
                        + "stance sizing, camera behavior, and animations return to "
                        + "classic Minecraft 1.7.10 behavior. Server/world controlled. "
                        + "Restart required: No.",
                "config.lotrmoremobs.modernPlayerAnimations"
        );
        markPropertiesRuntimeEditable(
                CATEGORY_PLAYER_ANIMATIONS,
                "modernPlayerAnimations"
        );
    }

    private static void syncServerGameplayCategory() {
        configuration.setCategoryComment(
                CATEGORY_SERVER_GAMEPLAY,
                "Server-side world/gameplay safety settings."
        );
        configuration.setCategoryLanguageKey(
                CATEGORY_SERVER_GAMEPLAY,
                "config.lotrmoremobs.category.serverGameplay"
        );
        configuration.setCategoryPropertyOrder(
                CATEGORY_SERVER_GAMEPLAY,
                Arrays.asList("npcBomberBlockDamage")
        );
        npcBomberBlockDamage = configuration.getBoolean(
                "npcBomberBlockDamage",
                CATEGORY_SERVER_GAMEPLAY,
                DEFAULT_NPC_BOMBER_BLOCK_DAMAGE,
                "When enabled, LOTR NPC-thrown Orc bombs and Warg Bombardier "
                        + "explosions can damage terrain normally. When disabled, "
                        + "those NPC explosions still damage entities and play their "
                        + "normal effects but do not destroy blocks. Player-thrown "
                        + "Orc bombs are unaffected. Restart required: No.",
                "config.lotrmoremobs.npcBomberBlockDamage"
        );
        markPropertiesRuntimeEditable(
                CATEGORY_SERVER_GAMEPLAY,
                "npcBomberBlockDamage"
        );
    }

    private static void removeLegacyCategories() {
        if (configuration.hasCategory(LEGACY_CATEGORY_GAMEPLAY)) {
            configuration.removeCategory(
                    configuration.getCategory(LEGACY_CATEGORY_GAMEPLAY)
            );
        }
        if (configuration.hasCategory(LEGACY_CATEGORY_SPAWNING)) {
            configuration.removeCategory(
                    configuration.getCategory(LEGACY_CATEGORY_SPAWNING)
            );
        }
    }

    private static boolean legacyBoolean(
            String category,
            String key,
            boolean fallback
    ) {
        if (!configuration.hasKey(category, key)) {
            return fallback;
        }
        return configuration.get(category, key, fallback).getBoolean(fallback);
    }

    private static int legacyInt(
            String category,
            String key,
            int fallback
    ) {
        if (!configuration.hasKey(category, key)) {
            return fallback;
        }
        return configuration.get(category, key, fallback).getInt(fallback);
    }

    private static float legacyFloat(
            String category,
            String key,
            float fallback
    ) {
        if (!configuration.hasKey(category, key)) {
            return fallback;
        }
        return (float)configuration.get(category, key, fallback).getDouble(fallback);
    }

    private static void markPropertiesRuntimeEditable(
            String categoryName,
            String... propertyNames
    ) {
        ConfigCategory category = configuration.getCategory(categoryName);
        for (int i = 0; i < propertyNames.length; ++i) {
            Property property = category.get(propertyNames[i]);
            if (property != null) {
                property.setRequiresMcRestart(false);
                property.setRequiresWorldRestart(false);
            }
        }
    }

    private static void markPropertiesRestartRequired(
            String categoryName,
            String... propertyNames
    ) {
        ConfigCategory category = configuration.getCategory(categoryName);
        for (int i = 0; i < propertyNames.length; ++i) {
            Property property = category.get(propertyNames[i]);
            if (property != null) {
                property.setRequiresMcRestart(true);
                property.setRequiresWorldRestart(false);
            }
        }
    }

    public static int getRamCarrierRespawnDelayTicks() {
        long ticks = (long)ramCarrierRespawnDelaySeconds * 20L;
        return (int)Math.min((long)Integer.MAX_VALUE, Math.max(0L, ticks));
    }

    public static Configuration getConfiguration() {
        if (configuration == null) {
            throw new IllegalStateException(
                    "Mumak configuration has not been initialized"
            );
        }
        return configuration;
    }

    private static void printSummary() {
        System.out.println(
                "[LOTRMoreMobs] Addon config:"
                        + " mumakil=" + enableMumakil
                        + " naturalMumak=" + enableNaturalMumakSpawning
                        + " spawnWeight=" + mumakNaturalSpawnWeight
                        + " herd=" + mumakNaturalSpawnMinGroupSize
                        + "-" + mumakNaturalSpawnMaxGroupSize
                        + " breakTrees=" + mumakBreaksTrees
                        + " pickupFilter=" + enableItemPickupFilter
                        + " mortalGandalf=" + mortalGandalf
                        + " siegeGates=" + enableSiegeGates
                        + " defaultGateHealth=" + defaultGateHealth
                        + " battleRams=" + enableBattleRams
                        + " ramDamage=" + ramSiegeDamage
                        + " carrierRespawnSeconds=" + ramCarrierRespawnDelaySeconds
                        + " modernAnimations=" + modernPlayerAnimations
                        + " npcBomberBlockDamage=" + npcBomberBlockDamage
        );
    }
}
