package com.fuzs.aquaacrobatics.integration;

import java.util.LinkedList;
import java.util.List;

import com.fuzs.aquaacrobatics.config.ConfigHandler;

import cpw.mods.fml.common.Loader;

public class IntegrationManager {

    private static boolean isMorphLoaded;
    private static boolean isHatsLoaded;
    private static boolean isAE2Loaded;
    private static boolean isEFRLoaded;

    public static List<IElytraOpenHook> elytraOpenHooks = new LinkedList<>();

    public static void loadCompat() {
        isAE2Loaded = Loader.isModLoaded("appliedenergistics2");
        isMorphLoaded = Loader.isModLoaded("Morph");
        isHatsLoaded = Loader.isModLoaded("Hats");
        isEFRLoaded = Loader.isModLoaded("etfuturum");
    }

    public static boolean isAE2Enabled() {

        return isAE2Loaded && ConfigHandler.IntegrationConfig.ae2Integration;
    }

    public static boolean isMorphEnabled() {

        return isMorphLoaded && ConfigHandler.IntegrationConfig.morphIntegration;
    }

    public static boolean isHatsEnabled() {

        return isHatsLoaded && ConfigHandler.IntegrationConfig.hatsIntegration;
    }

    public static boolean isEFREnabled() {

        return isEFRLoaded && ConfigHandler.IntegrationConfig.efrIntegration;
    }

}
