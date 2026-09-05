package com.lotrcharactercreation.appearance;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lotrcharactercreation.race.PlayerRace;
import com.lotrcharactercreation.race.PlayerRaceData;

public final class OrcAppearanceInitializer {

    private static final Logger LOGGER = LogManager.getLogger("LOTR Character Creation");
    private static final OrcAppearanceGroup BUILT_IN_GROUP = OrcAppearanceGroup.COMMON_ORC;

    private OrcAppearanceInitializer() {}

    public static AppearancePreset initializeIfNeeded(EntityPlayerMP player) {
        return assign(player, false);
    }

    public static AppearancePreset reroll(EntityPlayerMP player) {
        return assign(player, true);
    }

    private static AppearancePreset assign(EntityPlayerMP player, boolean forceReroll) {
        if (PlayerRaceData.getRace(player) != PlayerRace.ORC) {
            throw new IllegalArgumentException("Orc appearances can only be assigned to Orc players");
        }

        String storedPresetId = PlayerRaceData.getAppearancePresetId(player);
        PlayerSex storedSex = PlayerRaceData.getSex(player);
        AppearancePreset storedPreset = AppearancePresetRegistry.findById(storedPresetId);
        boolean storedAppearanceValid = storedSex == PlayerSex.NONE
            && AppearancePresetRegistry.isOrcPresetValid(storedPresetId, BUILT_IN_GROUP);

        if (!forceReroll && PlayerRaceData.isAppearanceInitialized(player) && storedAppearanceValid) {
            return storedPreset;
        }

        if (!storedAppearanceValid && (storedPresetId != null || PlayerRaceData.isAppearanceInitialized(player))) {
            LOGGER.warn(
                "Replacing invalid Orc appearance for {}: stored sex={}, preset={}, initialized={}, requested group={}",
                player.getCommandSenderName(),
                storedSex == null ? "missing/invalid" : storedSex.getSerializedId(),
                storedPresetId == null ? "missing" : storedPresetId,
                PlayerRaceData.isAppearanceInitialized(player),
                BUILT_IN_GROUP.getSerializedId());
        }

        List<AppearancePreset> candidates = new ArrayList<AppearancePreset>(
            AppearancePresetRegistry.getOrcPresets(BUILT_IN_GROUP));
        if (forceReroll && storedAppearanceValid && candidates.size() > 1) {
            candidates.remove(storedPreset);
        }
        if (candidates.isEmpty()) {
            throw new IllegalStateException("No valid Orc appearance presets for " + BUILT_IN_GROUP.getSerializedId());
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
