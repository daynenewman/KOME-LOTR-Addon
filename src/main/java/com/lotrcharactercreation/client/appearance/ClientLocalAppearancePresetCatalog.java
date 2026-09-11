package com.lotrcharactercreation.client.appearance;

import java.io.File;
import java.util.List;

import org.apache.logging.log4j.LogManager;

import com.lotrcharactercreation.appearance.AppearancePreset;
import com.lotrcharactercreation.appearance.AppearancePresetCatalog;
import com.lotrcharactercreation.appearance.AppearancePresetRegistry;
import com.lotrcharactercreation.appearance.ExternalAppearancePresetScanner;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Temporary local-file client catalog retained for gameplay compatibility
 * until the connection-scoped network catalog is introduced.
 */
@SideOnly(Side.CLIENT)
public final class ClientLocalAppearancePresetCatalog {

    private static volatile AppearancePresetCatalog catalog = AppearancePresetRegistry.getBuiltInCatalog();

    private ClientLocalAppearancePresetCatalog() {}

    public static void initialize(File customSkinRoot) {
        List<AppearancePreset> localExternalPresets = ExternalAppearancePresetScanner.scan(
            customSkinRoot,
            LogManager.getLogger("LOTRCharacterCreation"));
        catalog = AppearancePresetCatalog.combine(
            AppearancePresetRegistry.getBuiltInCatalog(),
            localExternalPresets);
    }

    public static AppearancePresetCatalog get() {
        return catalog;
    }
}
