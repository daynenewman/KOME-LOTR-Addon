package com.lotrcharactercreation.race;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;

import com.lotrcharactercreation.appearance.AppearancePreset;
import com.lotrcharactercreation.appearance.AppearancePresetRegistry;
import com.lotrcharactercreation.appearance.PlayerSex;
import com.lotrcharactercreation.faction.StartingFaction;

public final class PlayerRaceData {

    private static final String MOD_DATA_TAG = "lotrcharactercreation";
    private static final String RACE_TAG = "race";
    private static final String SEX_TAG = "sex";
    private static final String APPEARANCE_PRESET_TAG = "appearancePreset";
    private static final String APPEARANCE_INITIALIZED_TAG = "appearanceInitialized";
    private static final String RACE_SELECTION_COMPLETE_TAG = "raceSelectionComplete";
    private static final String STARTING_FACTION_TAG = "startingFaction";
    private static final String FACTION_SELECTION_COMPLETE_TAG = "factionSelectionComplete";
    private static final String STARTING_FACTION_APPLIED_TAG = "startingFactionApplied";
    private static final String STARTING_WAYPOINT_TAG = "startingWaypoint";
    private static final String STARTING_WAYPOINT_APPLIED_TAG = "startingWaypointApplied";
    private static final String CHARACTER_CREATION_COMPLETE_TAG = "characterCreationComplete";
    private static final String DWARF_STAMINA_TAG = "dwarfStamina";
    private static final String DWARF_FEAST_TAG = "dwarfFeast";
    private static final String DWARF_STAMINA_EXHAUSTED_TAG = "dwarfStaminaExhausted";
    private static final String ELF_GRAPPLE_COOLDOWN_TICKS_TAG = "elfGrappleCooldownTicks";
    private static final String ELF_GRAPPLE_CLEANUP_PENDING_TAG = "elfGrappleCleanupPending";

    private PlayerRaceData() {}

    public static PlayerRace getRace(EntityPlayerMP player) {
        NBTTagCompound modData = getModData(player, false);
        if (modData == null || !modData.hasKey(RACE_TAG, Constants.NBT.TAG_STRING)) {
            return PlayerRace.MAN;
        }

        return PlayerRace.fromSerializedId(modData.getString(RACE_TAG));
    }

    public static void setRace(EntityPlayerMP player, PlayerRace race) {
        if (race == null) {
            throw new IllegalArgumentException("race cannot be null");
        }

        getModData(player, true).setString(RACE_TAG, race.getSerializedId());
    }

    public static PlayerSex getSex(EntityPlayerMP player) {
        NBTTagCompound modData = getModData(player, false);
        if (modData == null || !modData.hasKey(SEX_TAG, Constants.NBT.TAG_STRING)) {
            return null;
        }

        return PlayerSex.findBySerializedId(modData.getString(SEX_TAG));
    }

    public static void setSex(EntityPlayerMP player, PlayerSex sex) {
        if (!AppearancePresetRegistry.isSexValidForRace(getRace(player), sex)) {
            throw new IllegalArgumentException("sex is not valid for the player's race");
        }

        getModData(player, true).setString(SEX_TAG, sex.getSerializedId());
    }

    public static void clearSex(EntityPlayerMP player) {
        NBTTagCompound modData = getModData(player, false);
        if (modData != null) {
            modData.removeTag(SEX_TAG);
        }
    }

    public static String getAppearancePresetId(EntityPlayerMP player) {
        NBTTagCompound modData = getModData(player, false);
        if (modData == null || !modData.hasKey(APPEARANCE_PRESET_TAG, Constants.NBT.TAG_STRING)) {
            return null;
        }

        String presetId = modData.getString(APPEARANCE_PRESET_TAG);
        return presetId.isEmpty() ? null : presetId;
    }

    public static AppearancePreset getAppearancePreset(EntityPlayerMP player) {
        return AppearancePresetRegistry.findById(getAppearancePresetId(player));
    }

    public static void setAppearancePreset(EntityPlayerMP player, AppearancePreset preset) {
        if (preset == null) {
            throw new IllegalArgumentException("appearance preset cannot be null");
        }
        if (!AppearancePresetRegistry.isPresetValid(getRace(player), getSex(player), preset.getId())) {
            throw new IllegalArgumentException("appearance preset is not valid for the player's race and sex");
        }

        getModData(player, true).setString(APPEARANCE_PRESET_TAG, preset.getId());
    }

    public static void clearAppearancePreset(EntityPlayerMP player) {
        NBTTagCompound modData = getModData(player, false);
        if (modData != null) {
            modData.removeTag(APPEARANCE_PRESET_TAG);
        }
    }

    public static boolean isAppearanceInitialized(EntityPlayerMP player) {
        NBTTagCompound modData = getModData(player, false);
        return modData != null && modData.hasKey(APPEARANCE_INITIALIZED_TAG, Constants.NBT.TAG_BYTE)
            && modData.getBoolean(APPEARANCE_INITIALIZED_TAG);
    }

    public static void setAppearanceInitialized(EntityPlayerMP player, boolean initialized) {
        if (initialized && !AppearancePresetRegistry
            .isAppearanceValid(getRace(player), getSex(player), getAppearancePresetId(player))) {
            throw new IllegalStateException("cannot initialize invalid appearance data");
        }

        getModData(player, true).setBoolean(APPEARANCE_INITIALIZED_TAG, initialized);
    }

    public static PlayerSex migrateMissingSexIfSafe(EntityPlayerMP player) {
        PlayerRace race = getRace(player);
        PlayerSex storedSex = getSex(player);
        if (AppearancePresetRegistry.isSexValidForRace(race, storedSex)) {
            return storedSex;
        }

        PlayerSex migrationSex = AppearancePresetRegistry.getConservativeMigrationSex(race);
        if (migrationSex != null) {
            setSex(player, migrationSex);
        }
        return migrationSex;
    }

    public static boolean isRaceSelectionComplete(EntityPlayerMP player) {
        NBTTagCompound modData = getModData(player, false);
        return modData != null && modData.hasKey(RACE_SELECTION_COMPLETE_TAG, Constants.NBT.TAG_BYTE)
            && modData.getBoolean(RACE_SELECTION_COMPLETE_TAG);
    }

    public static void setRaceSelectionComplete(EntityPlayerMP player, boolean complete) {
        getModData(player, true).setBoolean(RACE_SELECTION_COMPLETE_TAG, complete);
    }

    public static StartingFaction getStartingFaction(EntityPlayerMP player) {
        NBTTagCompound modData = getModData(player, false);
        if (modData == null || !modData.hasKey(STARTING_FACTION_TAG, Constants.NBT.TAG_STRING)) {
            return StartingFaction.WANDERER;
        }

        return StartingFaction.fromSerializedId(modData.getString(STARTING_FACTION_TAG));
    }

    public static void setStartingFaction(EntityPlayerMP player, StartingFaction faction) {
        if (faction == null) {
            throw new IllegalArgumentException("faction cannot be null");
        }

        getModData(player, true).setString(STARTING_FACTION_TAG, faction.getSerializedId());
    }

    public static boolean isFactionSelectionComplete(EntityPlayerMP player) {
        NBTTagCompound modData = getModData(player, false);
        return modData != null && modData.hasKey(FACTION_SELECTION_COMPLETE_TAG, Constants.NBT.TAG_BYTE)
            && modData.getBoolean(FACTION_SELECTION_COMPLETE_TAG);
    }

    public static void setFactionSelectionComplete(EntityPlayerMP player, boolean complete) {
        getModData(player, true).setBoolean(FACTION_SELECTION_COMPLETE_TAG, complete);
    }

    public static boolean isStartingFactionApplied(EntityPlayerMP player) {
        NBTTagCompound modData = getModData(player, false);
        return modData != null && modData.hasKey(STARTING_FACTION_APPLIED_TAG, Constants.NBT.TAG_BYTE)
            && modData.getBoolean(STARTING_FACTION_APPLIED_TAG);
    }

    public static void setStartingFactionApplied(EntityPlayerMP player, boolean applied) {
        getModData(player, true).setBoolean(STARTING_FACTION_APPLIED_TAG, applied);
    }

    public static String getStartingWaypointCodeName(EntityPlayerMP player) {
        NBTTagCompound modData = getModData(player, false);
        if (modData == null || !modData.hasKey(STARTING_WAYPOINT_TAG, Constants.NBT.TAG_STRING)) {
            return null;
        }

        String codeName = modData.getString(STARTING_WAYPOINT_TAG);
        return codeName.isEmpty() ? null : codeName;
    }

    public static void setStartingWaypointCodeName(EntityPlayerMP player, String codeName) {
        if (codeName == null || codeName.isEmpty()) {
            throw new IllegalArgumentException("codeName cannot be null or empty");
        }

        getModData(player, true).setString(STARTING_WAYPOINT_TAG, codeName);
    }

    public static boolean isStartingWaypointApplied(EntityPlayerMP player) {
        NBTTagCompound modData = getModData(player, false);
        return modData != null && modData.hasKey(STARTING_WAYPOINT_APPLIED_TAG, Constants.NBT.TAG_BYTE)
            && modData.getBoolean(STARTING_WAYPOINT_APPLIED_TAG);
    }

    public static void setStartingWaypointApplied(EntityPlayerMP player, boolean applied) {
        getModData(player, true).setBoolean(STARTING_WAYPOINT_APPLIED_TAG, applied);
    }

    public static boolean isCharacterCreationComplete(EntityPlayerMP player) {
        NBTTagCompound modData = getModData(player, false);
        return modData != null && modData.hasKey(CHARACTER_CREATION_COMPLETE_TAG, Constants.NBT.TAG_BYTE)
            && modData.getBoolean(CHARACTER_CREATION_COMPLETE_TAG);
    }

    public static void setCharacterCreationComplete(EntityPlayerMP player, boolean complete) {
        getModData(player, true).setBoolean(CHARACTER_CREATION_COMPLETE_TAG, complete);
    }

    public static boolean hasDwarfResourceData(EntityPlayerMP player) {
        NBTTagCompound modData = getModData(player, false);
        return modData != null && modData.hasKey(DWARF_STAMINA_TAG, Constants.NBT.TAG_ANY_NUMERIC);
    }

    public static float getDwarfStamina(EntityPlayerMP player) {
        NBTTagCompound modData = getModData(player, false);
        return modData == null || !modData.hasKey(DWARF_STAMINA_TAG, Constants.NBT.TAG_ANY_NUMERIC) ? 100.0F
            : modData.getFloat(DWARF_STAMINA_TAG);
    }

    public static void setDwarfStamina(EntityPlayerMP player, float stamina) {
        getModData(player, true).setFloat(DWARF_STAMINA_TAG, stamina);
    }

    public static int getDwarfFeast(EntityPlayerMP player) {
        NBTTagCompound modData = getModData(player, false);
        return modData == null || !modData.hasKey(DWARF_FEAST_TAG, Constants.NBT.TAG_ANY_NUMERIC) ? 0
            : modData.getInteger(DWARF_FEAST_TAG);
    }

    public static void setDwarfFeast(EntityPlayerMP player, int feast) {
        getModData(player, true).setInteger(DWARF_FEAST_TAG, feast);
    }

    public static boolean isDwarfStaminaExhausted(EntityPlayerMP player) {
        NBTTagCompound modData = getModData(player, false);
        return modData != null && modData.hasKey(DWARF_STAMINA_EXHAUSTED_TAG, Constants.NBT.TAG_BYTE)
            && modData.getBoolean(DWARF_STAMINA_EXHAUSTED_TAG);
    }

    public static void setDwarfStaminaExhausted(EntityPlayerMP player, boolean exhausted) {
        getModData(player, true).setBoolean(DWARF_STAMINA_EXHAUSTED_TAG, exhausted);
    }

    public static int getElfGrappleCooldownTicks(EntityPlayerMP player) {
        NBTTagCompound modData = getModData(player, false);
        if (modData == null || !modData.hasKey(ELF_GRAPPLE_COOLDOWN_TICKS_TAG, Constants.NBT.TAG_ANY_NUMERIC)) {
            return 0;
        }
        return Math.max(0, modData.getInteger(ELF_GRAPPLE_COOLDOWN_TICKS_TAG));
    }

    public static void setElfGrappleCooldownTicks(EntityPlayerMP player, int cooldownTicks) {
        getModData(player, true).setInteger(ELF_GRAPPLE_COOLDOWN_TICKS_TAG, Math.max(0, cooldownTicks));
    }

    public static boolean isElfGrappleCleanupPending(EntityPlayerMP player) {
        NBTTagCompound modData = getModData(player, false);
        return modData != null && modData.hasKey(ELF_GRAPPLE_CLEANUP_PENDING_TAG, Constants.NBT.TAG_BYTE)
            && modData.getBoolean(ELF_GRAPPLE_CLEANUP_PENDING_TAG);
    }

    public static void setElfGrappleCleanupPending(EntityPlayerMP player, boolean cleanupPending) {
        getModData(player, true).setBoolean(ELF_GRAPPLE_CLEANUP_PENDING_TAG, cleanupPending);
    }

    private static NBTTagCompound getModData(EntityPlayerMP player, boolean create) {
        NBTTagCompound entityData = player.getEntityData();
        if (!entityData.hasKey(EntityPlayer.PERSISTED_NBT_TAG, Constants.NBT.TAG_COMPOUND)) {
            if (!create) {
                return null;
            }

            entityData.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        }

        NBTTagCompound persistedData = entityData.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        if (!persistedData.hasKey(MOD_DATA_TAG, Constants.NBT.TAG_COMPOUND)) {
            if (!create) {
                return null;
            }

            persistedData.setTag(MOD_DATA_TAG, new NBTTagCompound());
        }

        return persistedData.getCompoundTag(MOD_DATA_TAG);
    }
}
