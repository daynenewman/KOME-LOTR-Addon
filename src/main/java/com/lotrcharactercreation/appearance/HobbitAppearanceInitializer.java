package com.lotrcharactercreation.appearance;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lotrcharactercreation.race.PlayerRace;
import com.lotrcharactercreation.race.PlayerRaceData;

public final class HobbitAppearanceInitializer {

    private static final Logger LOGGER = LogManager.getLogger("LOTR Character Creation");

    private HobbitAppearanceInitializer() {}

    public static AppearancePreset initializeIfNeeded(EntityPlayerMP player, PlayerSex sex) {
        return assign(player, sex, false);
    }

    public static AppearancePreset reroll(EntityPlayerMP player, PlayerSex sex) {
        return assign(player, sex, true);
    }

    private static AppearancePreset assign(EntityPlayerMP player, PlayerSex sex, boolean forceReroll) {
        if (PlayerRaceData.getRace(player) != PlayerRace.HOBBIT) {
            throw new IllegalArgumentException("Hobbit appearances can only be assigned to Hobbit players");
        }
        if (!AppearancePresetRegistry.isSexValidForRace(PlayerRace.HOBBIT, sex)) {
            throw new IllegalArgumentException("Hobbit appearance sex must be male or female");
        }

        String storedPresetId = PlayerRaceData.getAppearancePresetId(player);
        PlayerSex storedSex = PlayerRaceData.getSex(player);
        AppearancePreset storedPreset = AppearancePresetRegistry.findById(storedPresetId);
        boolean storedAppearanceValid = storedSex == sex
            && AppearancePresetRegistry.isPresetValid(PlayerRace.HOBBIT, sex, storedPresetId);

        if (!forceReroll && PlayerRaceData.isAppearanceInitialized(player) && storedAppearanceValid) {
            return storedPreset;
        }

        if (!storedAppearanceValid && (storedPresetId != null || PlayerRaceData.isAppearanceInitialized(player))) {
            LOGGER.warn(
                "Replacing invalid Hobbit appearance for {}: stored sex={}, preset={}, initialized={}",
                player.getCommandSenderName(),
                storedSex == null ? "missing/invalid" : storedSex.getSerializedId(),
                storedPresetId == null ? "missing" : storedPresetId,
                PlayerRaceData.isAppearanceInitialized(player));
        }

        List<AppearancePreset> candidates = new ArrayList<AppearancePreset>(
            AppearancePresetRegistry.getPresets(PlayerRace.HOBBIT, sex));
        if (forceReroll && storedAppearanceValid && candidates.size() > 1) {
            candidates.remove(storedPreset);
        }
        if (candidates.isEmpty()) {
            throw new IllegalStateException("No valid Hobbit appearance presets for " + sex);
        }

        AppearancePreset selected = candidates.get(
            player.getRNG()
                .nextInt(candidates.size()));
        PlayerRaceData.setSex(player, sex);
        PlayerRaceData.setAppearancePreset(player, selected);
        PlayerRaceData.setAppearanceInitialized(player, true);
        return selected;
    }
}
