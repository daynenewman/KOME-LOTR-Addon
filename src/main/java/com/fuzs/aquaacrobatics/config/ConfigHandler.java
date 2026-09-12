package com.fuzs.aquaacrobatics.config;

import java.io.File;

import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

/**
 * Forge-native Aqua Acrobatics configuration.
 *
 * <p>This intentionally preserves the GTNHLib-generated file name, category paths,
 * key names, comments, and defaults so an existing {@code aquaacrobatics.cfg} can
 * be read without migration.</p>
 */
public final class ConfigHandler {

    private static final String GENERAL = Configuration.CATEGORY_GENERAL;
    private static final String BLOCKS = GENERAL + ".blocks";
    private static final String MOVEMENT = GENERAL + ".movement";
    private static final String MISCELLANEOUS = GENERAL + ".miscellaneous";
    private static final String INTEGRATION = GENERAL + ".integration";
    private static final String LANG_KEY = "";

    private static Configuration configuration;

    public static PlayerBlockCollisions playerBlockCollisions = PlayerBlockCollisions.APPROXIMATE;

    @SuppressWarnings("unused")
    public static BlocksConfig blocksConfig;
    @SuppressWarnings("unused")
    public static MovementConfig movementConfig;
    @SuppressWarnings("unused")
    public static MiscellaneousConfig miscellaneousConfig;
    @SuppressWarnings("unused")
    public static IntegrationConfig integrationConfig;

    private ConfigHandler() {}

    /** Called from the common proxy so both clients and dedicated servers load the same file. */
    public static synchronized void load(File file) {
        configuration = new Configuration(file);
        sync();
    }

    /** Reloads the already-selected Aqua config file, if one has been initialized. */
    public static synchronized void reload() {
        if (configuration != null) sync();
    }

    private static void sync() {
        configuration.load();

        setCategoryComment(BLOCKS, "Block-related config options (must match server).");
        setCategoryComment(MOVEMENT, "Movement related config options.");
        setCategoryComment(MISCELLANEOUS, "Config options for various features of the mod.");
        setCategoryComment(INTEGRATION, "Control compatibility settings for individual mods.");

        String[] collisionValues = enumNames(PlayerBlockCollisions.values());
        String collision = configuration.getString(
            "Push Player Out Of Blocks",
            GENERAL,
            PlayerBlockCollisions.APPROXIMATE.name(),
            "STANDARD - The player will occasionally be pushed out of certain spaces. Collisions are evaluated for full cubes only, non-full cubes are ignored. This is the default behavior up to Minecraft 1.12.\n"
                + "APPROXIMATE - The player can move into more spaces, but will still be pushed out of some. Collisions are evaluated for full cubes only, non-full cubes are ignored.\n"
                + "EXACT - The player can move into all spaces as expected. Collisions are evaluated for all types of cubes. This is the default behavior in Minecraft 1.13 and onwards.",
            collisionValues,
            LANG_KEY);
        try {
            playerBlockCollisions = PlayerBlockCollisions.valueOf(collision);
        } catch (IllegalArgumentException ignored) {
            playerBlockCollisions = PlayerBlockCollisions.APPROXIMATE;
        }

        MovementConfig.easyElytraTakeoff = bool(
            MOVEMENT,
            "Easy Elytra Takeoff (Not working)",
            true,
            "Taking off with an elytra from the ground is now far easier like in Minecraft 1.15 and onwards.");
        MovementConfig.noDoubleTapSprinting = bool(
            MOVEMENT,
            "No Double Tab Sprinting",
            false,
            "Prevent sprinting from being triggered by double tapping the walk forward key.");
        MovementConfig.sidewaysSprinting = bool(
            MOVEMENT,
            "Sideways Sprinting",
            false,
            "Enables sprinting to the left and right.");
        MovementConfig.sidewaysSwimming = bool(
            MOVEMENT,
            "Sideways Swimming",
            false,
            "Enables swimming to the left and right.");
        MovementConfig.enableCrawling = bool(
            MOVEMENT,
            "Enable Crawling",
            true,
            "Enables crawling to prevent suffocation. Note that if you disable this there will probably be behavioral differences from 1.13.");
        MovementConfig.enableToggleCrawling = bool(
            MOVEMENT,
            "Enable Toggle Crawling",
            false,
            "Enables a keybind to toggle crawling.");
        MovementConfig.newProjectileBehavior = bool(
            MOVEMENT,
            "New Projectile Behavior",
            false,
            "Modify projectile behavior to be closer to that of newer versions (fixes MC-73884 and allows bubble columns to work with ender pearls).");
        MovementConfig.newClimbingBehavior = bool(
            MOVEMENT,
            "New Climbing Behavior",
            false,
            "Allow climbing vines and climbing by pressing jump.");
        MovementConfig.effectsWhileCrawling = bool(
            MOVEMENT,
            "Effects While Crawling",
            false,
            "Apply slowness and mining fatigue while crawling.");

        BlocksConfig.seagrass = bool(BLOCKS, "Seagrass", false, "Allow seagrass to generate in the world.");
        BlocksConfig.brighterWater = bool(
            BLOCKS,
            "Brighter Water",
            true,
            "Make water only reduce light level by 1 per Y-level, instead of 3.");
        BlocksConfig.newWaterColors = bool(BLOCKS, "New Water", false, "Use the new water rendering in 1.13+.");
        BlocksConfig.newWaterFog = bool(BLOCKS, "New Water Fog", true, "Use the new fog rendering in 1.13+.");

        MiscellaneousConfig.slowAirReplenish = bool(
            MISCELLANEOUS,
            "Replenish Air Slowly",
            false,
            "Replenish air slowly when out of water instead of immediately.");
        MiscellaneousConfig.sneakingForParrots = bool(
            MISCELLANEOUS,
            "Sneaking Dismounts Parrots",
            false,
            "Parrots no longer leave the players shoulders as easily, instead the player needs to press the sneak key.");
        MiscellaneousConfig.eatingAnimation = bool(
            MISCELLANEOUS,
            "Eating Animation",
            true,
            "Animate eating in third-person view.");
        MiscellaneousConfig.bubbleColumns = bool(MISCELLANEOUS, "Bubble Columns", false, "Enable bubble columns.");
        MiscellaneousConfig.customBiomeWaterColors = stringList(
            MISCELLANEOUS,
            "Custom Biome Water Colors",
            new String[] {},
            "Allows overriding the water and fog colors for a biome. Specify each entry like this (without quotes) - 'modname:biome,color,fogcolor'");
        MiscellaneousConfig.providerFogBlacklist = stringList(
            MISCELLANEOUS,
            "WorldProvider Fog Blacklist",
            new String[] { "thebetweenlands.common.world.WorldProviderBetweenlands" },
            "List of WorldProviders in which fog should be disabled.");
        MiscellaneousConfig.floatingItems = bool(
            MISCELLANEOUS,
            "Floating Items",
            true,
            "Whether or not items should float in water like in 1.13+.");
        MiscellaneousConfig.BoatId = integer(
            MISCELLANEOUS,
            "Boat ID",
            29,
            "Change the Boat datawatchers ID.");
        MiscellaneousConfig.poseId = integer(
            MISCELLANEOUS,
            "Pose ID",
            30,
            "Change the pose datawatchers ID.");
        MiscellaneousConfig.CrawlingId = integer(
            MISCELLANEOUS,
            "Crawling ID",
            31,
            "Change the Crawling datawatchers ID.");

        IntegrationConfig.ae2Integration = restartBoolean(
            INTEGRATION,
            "Applied Energistics 2 Integration",
            true,
            "Only applies when the mod is installed.");
        IntegrationConfig.morphIntegration = bool(
            INTEGRATION,
            "Morph Integration",
            true,
            "Only applies when the mod is installed.");
        IntegrationConfig.hatsIntegration = restartBoolean(
            INTEGRATION,
            "Hats Integration",
            true,
            "Only applies when the mod is installed.");
        IntegrationConfig.efrIntegration = restartBoolean(
            INTEGRATION,
            "EFR Integration",
            true,
            "Only applies when the mod is installed.");

        if (configuration.hasChanged()) configuration.save();
    }

    private static boolean bool(String category, String name, boolean defaultValue, String comment) {
        return configuration.getBoolean(name, category, defaultValue, comment, LANG_KEY);
    }

    private static boolean restartBoolean(String category, String name, boolean defaultValue, String comment) {
        boolean value = bool(category, name, defaultValue, comment);
        ConfigCategory configCategory = configuration.getCategory(category);
        Property property = configCategory.get(name);
        if (property != null) property.setRequiresMcRestart(true);
        return value;
    }

    private static int integer(String category, String name, int defaultValue, String comment) {
        return configuration.getInt(
            name,
            category,
            defaultValue,
            Integer.MIN_VALUE,
            Integer.MAX_VALUE,
            comment,
            LANG_KEY);
    }

    private static String[] stringList(String category, String name, String[] defaultValue, String comment) {
        return configuration.getStringList(name, category, defaultValue, comment, null, LANG_KEY);
    }

    private static void setCategoryComment(String category, String comment) {
        configuration.getCategory(category).setComment(comment);
    }

    private static String[] enumNames(Enum<?>[] values) {
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) names[i] = values[i].name();
        return names;
    }

    public static class MovementConfig {
        public static boolean easyElytraTakeoff = true;
        public static boolean noDoubleTapSprinting = false;
        public static boolean sidewaysSprinting = false;
        public static boolean sidewaysSwimming = false;
        public static boolean enableCrawling = true;
        public static boolean enableToggleCrawling = false;
        public static boolean newProjectileBehavior = false;
        public static boolean newClimbingBehavior = false;
        public static boolean effectsWhileCrawling = false;
    }

    public static class BlocksConfig {
        public static boolean seagrass = false;
        public static boolean brighterWater = true;
        public static boolean newWaterColors = false;
        public static boolean newWaterFog = true;
    }

    public static class MiscellaneousConfig {
        public static boolean slowAirReplenish = false;
        public static boolean sneakingForParrots = false;
        public static boolean eatingAnimation = true;
        public static boolean bubbleColumns = false;
        public static String[] customBiomeWaterColors = new String[] {};
        public static String[] providerFogBlacklist = new String[] {
            "thebetweenlands.common.world.WorldProviderBetweenlands" };
        public static boolean floatingItems = true;
        public static int BoatId = 29;
        public static int poseId = 30;
        public static int CrawlingId = 31;
    }

    public static class IntegrationConfig {
        public static boolean ae2Integration = true;
        public static boolean morphIntegration = true;
        public static boolean hatsIntegration = true;
        public static boolean efrIntegration = true;
    }

    public enum PlayerBlockCollisions {
        STANDARD,
        APPROXIMATE,
        EXACT
    }
}
