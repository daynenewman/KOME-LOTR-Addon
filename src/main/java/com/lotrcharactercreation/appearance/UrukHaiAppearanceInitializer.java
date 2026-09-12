package com.lotrcharactercreation.appearance;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lotrcharactercreation.race.PlayerRace;
import com.lotrcharactercreation.race.PlayerRaceData;

public final class UrukHaiAppearanceInitializer {

    private static final Logger LOGGER = LogManager.getLogger("LOTR Character Creation");

    private UrukHaiAppearanceInitializer() {}

    public static AppearancePreset initializeIfNeeded(EntityPlayerMP player, UrukHaiAppearanceGroup group) {
        return assign(player, group, false);
    }

    public static AppearancePreset reroll(EntityPlayerMP player, UrukHaiAppearanceGroup group) {
        return assign(player, group, true);
    }

    private static AppearancePreset assign(EntityPlayerMP player, UrukHaiAppearanceGroup group, boolean forceReroll) {
        if (PlayerRaceData.getRace(player) != PlayerRace.URUK_HAI) {
            throw new IllegalArgumentException("Uruk-hai appearances can only be assigned to Uruk-hai players");
        }
        if (group == null) {
            throw new IllegalArgumentException("Uruk-hai appearance group cannot be null");
        }
        AppearancePresetCatalog catalog = ServerCustomSkinLibrary.getInstance().getCurrentCatalog();

        String storedPresetId = PlayerRaceData.getAppearancePresetId(player);
        PlayerSex storedSex = PlayerRaceData.getSex(player);
        AppearancePreset storedPreset = catalog.findById(storedPresetId);
        boolean storedAppearanceValid = storedSex == PlayerSex.NONE
            && AppearancePresetRegistry.isUrukHaiPresetValid(catalog, storedPresetId, group);
        boolean storedDataMateriallyValid = storedSex == PlayerSex.NONE
            && AppearancePresetRegistry.isPresetValid(
                catalog, PlayerRace.URUK_HAI, PlayerSex.NONE, storedPresetId);

        if (!forceReroll && PlayerRaceData.isAppearanceInitialized(player) && storedAppearanceValid) {
            return storedPreset;
        }

        if (!storedDataMateriallyValid && (storedPresetId != null || PlayerRaceData.isAppearanceInitialized(player))) {
            LOGGER.warn(
                "Replacing invalid Uruk-hai appearance for {}: stored sex={}, preset={}, initialized={}, requested group={}",
                player.getCommandSenderName(),
                storedSex == null ? "missing/invalid" : storedSex.getSerializedId(),
                storedPresetId == null ? "missing" : storedPresetId,
                PlayerRaceData.isAppearanceInitialized(player),
                group.getSerializedId());
        }

        List<AppearancePreset> candidates = new ArrayList<AppearancePreset>(
            AppearancePresetRegistry.getUrukHaiPresets(catalog, group));
        if (forceReroll && storedAppearanceValid && candidates.size() > 1) {
            candidates.remove(storedPreset);
        }
        if (candidates.isEmpty()) {
            throw new IllegalStateException("No valid Uruk-hai appearance presets for " + group.getSerializedId());
        }

        AppearancePreset selected = candidates.get(
            player.getRNG()
                .nextInt(candidates.size()));
        PlayerRaceData.setSex(player, PlayerSex.NONE);
        PlayerRaceData.setAppearancePreset(player, selected);
        PlayerRaceData.setAppearanceInitialized(player, true);
        return selected;
    }
}
