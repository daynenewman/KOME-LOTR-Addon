package com.lotrcharactercreation.config;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public final class ModConfiguration {

    private static final String CHARACTER_CREATION_CATEGORY = "characterCreation";
    private static final String AUTOMATIC_STARTING_ALLEGIANCE_PROPERTY = "automaticStartingAllegiance";
    private static final String AUTOMATIC_STARTING_ALLEGIANCE_COMMENT = "When enabled, choosing a starting faction grants the minimum required LOTR alignment and automatically "
        + "pledges the player to that faction. When disabled, the chosen faction determines only the player's "
        + "starting location; alignment and pledging must be earned normally through LOTR gameplay.";

    private static boolean automaticStartingAllegiance = true;

    private ModConfiguration() {}

    public static void load(File configFile) {
        Configuration configuration = new Configuration(configFile);
        configuration.load();

        automaticStartingAllegiance = configuration
            .get(
                CHARACTER_CREATION_CATEGORY,
                AUTOMATIC_STARTING_ALLEGIANCE_PROPERTY,
                true,
                AUTOMATIC_STARTING_ALLEGIANCE_COMMENT)
            .getBoolean(true);

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }

    public static boolean isAutomaticStartingAllegianceEnabled() {
        return automaticStartingAllegiance;
    }
}
