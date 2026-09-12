package com.lotrcharactercreation.appearance;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lotrcharactercreation.race.PlayerRace;
import com.lotrcharactercreation.race.PlayerRaceData;

public final class ElfAppearanceInitializer {

    private static final Logger LOGGER = LogManager.getLogger("LOTR Character Creation");

    private ElfAppearanceInitializer() {}

    public static AppearancePreset initializeIfNeeded(EntityPlayerMP player, PlayerSex sex, ElfAppearanceGroup group) {
        return assign(player, sex, group, false);
    }

    public static AppearancePreset reroll(EntityPlayerMP player, PlayerSex sex, ElfAppearanceGroup group) {
        return assign(player, sex, group, true);
    }

    private static AppearancePreset assign(EntityPlayerMP player, PlayerSex sex, ElfAppearanceGroup group,
        boolean forceReroll) {
        if (PlayerRaceData.getRace(player) != PlayerRace.ELF) {
            throw new IllegalArgumentException("Elf appearances can only be assigned to Elf players");
        }
        if (!AppearancePresetRegistry.isSexValidForRace(PlayerRace.ELF, sex)) {
            throw new IllegalArgumentException("Elf appearance sex must be male or female");
        }
        if (group == null) {
            throw new IllegalArgumentException("Elf appearance group cannot be null");
        }
        AppearancePresetCatalog catalog = ServerCustomSkinLibrary.getInstance().getCurrentCatalog();

        String storedPresetId = PlayerRaceData.getAppearancePresetId(player);
        PlayerSex storedSex = PlayerRaceData.getSex(player);
        AppearancePreset storedPreset = catalog.findById(storedPresetId);
        boolean storedAppearanceValid = storedSex == sex
            && AppearancePresetRegistry.isElfPresetValid(catalog, storedPresetId, sex, group);

        if (!forceReroll && PlayerRaceData.isAppearanceInitialized(player) && storedAppearanceValid) {
            return storedPreset;
        }

        if (!storedAppearanceValid && (storedPresetId != null || PlayerRaceData.isAppearanceInitialized(player))) {
            LOGGER.warn(
                "Replacing invalid Elf appearance for {}: stored sex={}, preset={}, initialized={}, requested group={}",
                player.getCommandSenderName(),
                storedSex == null ? "missing/invalid" : storedSex.getSerializedId(),
                storedPresetId == null ? "missing" : storedPresetId,
                PlayerRaceData.isAppearanceInitialized(player),
                group.getSerializedId());
        }

        List<AppearancePreset> candidates = new ArrayList<AppearancePreset>(
            catalog.getPresets(PlayerRace.ELF, sex, group.getSerializedId()));
        if (forceReroll && storedAppearanceValid && candidates.size() > 1) {
            candidates.remove(storedPreset);
        }
        if (candidates.isEmpty()) {
            throw new IllegalStateException("No valid Elf appearance presets for " + sex + " / " + group);
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
