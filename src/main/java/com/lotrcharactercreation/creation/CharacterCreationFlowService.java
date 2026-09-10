package com.lotrcharactercreation.creation;

import net.minecraft.entity.player.EntityPlayerMP;

import com.lotrcharactercreation.appearance.AppearancePreset;
import com.lotrcharactercreation.appearance.AppearancePresetRegistry;
import com.lotrcharactercreation.appearance.AppearanceSelectionRules;
import com.lotrcharactercreation.appearance.PlayerSex;
import com.lotrcharactercreation.faction.StartingFaction;
import com.lotrcharactercreation.race.PlayerRace;
import com.lotrcharactercreation.race.PlayerRaceData;

/**
 * Determines the next mandatory character-creation stage from the existing
 * persistent choices. No separate stage value is stored.
 */
public final class CharacterCreationFlowService {

    private CharacterCreationFlowService() {}

    public static CharacterCreationStage getNextRequiredStage(EntityPlayerMP player) {
        if (PlayerRaceData.isCharacterCreationComplete(player)) {
            return CharacterCreationStage.COMPLETE;
        }
        if (!PlayerRaceData.isRaceSelectionComplete(player)) {
            return CharacterCreationStage.RACE;
        }

        ensureInherentSex(player);
        PlayerRace race = PlayerRaceData.getRace(player);
        PlayerSex sex = AppearanceSelectionRules.getSelectionSex(race, PlayerRaceData.getSex(player));
        if (sex == null) {
            return CharacterCreationStage.SEX;
        }

        StartingFaction faction = PlayerRaceData.getStartingFaction(player);
        if (!PlayerRaceData.isFactionSelectionComplete(player) || !faction.isAllowedFor(race)) {
            return CharacterCreationStage.FACTION;
        }

        String presetId = PlayerRaceData.getAppearancePresetId(player);
        if (!PlayerRaceData.isAppearanceInitialized(player)
            || !AppearanceSelectionRules.isPresetAllowed(race, sex, faction, presetId)) {
            return CharacterCreationStage.APPEARANCE;
        }
        return CharacterCreationStage.CONFIRMATION;
    }

    public static boolean isReadyForFinalization(EntityPlayerMP player) {
        return getNextRequiredStage(player) == CharacterCreationStage.CONFIRMATION;
    }

    /**
     * Central server-side policy for all character-selection mutations. Normal
     * creation requires the exact current stage and untouched one-time setup
     * gates. A future administrative flow may persistently authorize editing,
     * but must still deliberately reopen the intended stage.
     */
    public static boolean isSelectionMutationAuthorized(EntityPlayerMP player, CharacterCreationStage requiredStage) {
        if (player == null || requiredStage == null || requiredStage == CharacterCreationStage.COMPLETE
            || getNextRequiredStage(player) != requiredStage) {
            return false;
        }

        if (PlayerRaceData.isCharacterEditAuthorized(player)) {
            return true;
        }

        return !PlayerRaceData.isCharacterCreationComplete(player)
            && !PlayerRaceData.isStartingFactionApplied(player)
            && !PlayerRaceData.isStartingWaypointApplied(player);
    }

    public static boolean selectRace(EntityPlayerMP player, PlayerRace race) {
        if (race == null || !isSelectionMutationAuthorized(player, CharacterCreationStage.RACE)) {
            return false;
        }

        PlayerRaceData.setRace(player, race);
        PlayerRaceData.setRaceSelectionComplete(player, true);
        PlayerRaceData.clearSex(player);
        if (race == PlayerRace.ORC || race == PlayerRace.URUK_HAI) {
            PlayerRaceData.setSex(player, PlayerSex.NONE);
        }
        PlayerRaceData.setFactionSelectionComplete(player, false);
        PlayerRaceData.clearAppearancePreset(player);
        PlayerRaceData.setAppearanceInitialized(player, false);
        return true;
    }

    public static boolean selectSex(EntityPlayerMP player, PlayerSex sex) {
        if (player == null) {
            return false;
        }

        PlayerRace race = PlayerRaceData.getRace(player);
        if (!isSelectionMutationAuthorized(player, CharacterCreationStage.SEX)
            || !AppearanceSelectionRules.supportsSelectableSex(race)
            || (sex != PlayerSex.MALE && sex != PlayerSex.FEMALE)) {
            return false;
        }

        PlayerRaceData.setSex(player, sex);
        requireAppearanceConfirmation(player);
        return true;
    }

    public static boolean selectStartingFaction(EntityPlayerMP player, StartingFaction faction) {
        if (player == null || faction == null) {
            return false;
        }

        PlayerRace race = PlayerRaceData.getRace(player);
        if (!isSelectionMutationAuthorized(player, CharacterCreationStage.FACTION)
            || !faction.isAllowedFor(race)) {
            return false;
        }

        PlayerRaceData.setStartingFaction(player, faction);
        PlayerRaceData.setFactionSelectionComplete(player, true);
        requireAppearanceConfirmation(player);
        return true;
    }

    public static boolean selectAppearance(EntityPlayerMP player, String presetId) {
        if (!isSelectionMutationAuthorized(player, CharacterCreationStage.APPEARANCE)) {
            return false;
        }

        PlayerRace race = PlayerRaceData.getRace(player);
        PlayerSex sex = AppearanceSelectionRules.getSelectionSex(race, PlayerRaceData.getSex(player));
        StartingFaction faction = PlayerRaceData.getStartingFaction(player);
        AppearancePreset preset = AppearancePresetRegistry.findById(presetId);
        if (preset == null || !AppearanceSelectionRules.isPresetAllowed(race, sex, faction, presetId)) {
            return false;
        }

        PlayerRaceData.setAppearancePreset(player, preset);
        PlayerRaceData.setAppearanceInitialized(player, true);
        return true;
    }

    public static boolean goBackFrom(EntityPlayerMP player, CharacterCreationStage sourceStage) {
        if (!isSelectionMutationAuthorized(player, sourceStage)) {
            return false;
        }

        switch (sourceStage) {
            case CONFIRMATION:
                PlayerRaceData.setAppearanceInitialized(player, false);
                return true;
            case APPEARANCE:
                PlayerRaceData.setFactionSelectionComplete(player, false);
                PlayerRaceData.setAppearanceInitialized(player, false);
                return true;
            case FACTION:
                if (AppearanceSelectionRules.supportsSelectableSex(PlayerRaceData.getRace(player))) {
                    returnToSex(player);
                } else {
                    returnToRace(player);
                }
                return true;
            case SEX:
                returnToRace(player);
                return true;
            default:
                return false;
        }
    }

    public static void ensureInherentSex(EntityPlayerMP player) {
        if (!PlayerRaceData.isRaceSelectionComplete(player)) {
            return;
        }

        PlayerRace race = PlayerRaceData.getRace(player);
        if ((race == PlayerRace.ORC || race == PlayerRace.URUK_HAI)
            && PlayerRaceData.getSex(player) != PlayerSex.NONE) {
            PlayerRaceData.setSex(player, PlayerSex.NONE);
            requireAppearanceConfirmation(player);
        }
    }

    public static void requireAppearanceConfirmation(EntityPlayerMP player) {
        PlayerRace race = PlayerRaceData.getRace(player);
        PlayerSex sex = AppearanceSelectionRules.getSelectionSex(race, PlayerRaceData.getSex(player));
        StartingFaction faction = PlayerRaceData.getStartingFaction(player);
        String presetId = PlayerRaceData.getAppearancePresetId(player);
        boolean allowed = PlayerRaceData.isFactionSelectionComplete(player)
            && AppearanceSelectionRules.isPresetAllowed(race, sex, faction, presetId);

        if (!allowed) {
            PlayerRaceData.clearAppearancePreset(player);
        }
        PlayerRaceData.setAppearanceInitialized(player, false);
    }

    private static void returnToSex(EntityPlayerMP player) {
        PlayerRaceData.clearSex(player);
        PlayerRaceData.setFactionSelectionComplete(player, false);
        PlayerRaceData.clearAppearancePreset(player);
        PlayerRaceData.setAppearanceInitialized(player, false);
    }

    private static void returnToRace(EntityPlayerMP player) {
        PlayerRaceData.setRaceSelectionComplete(player, false);
        PlayerRaceData.clearSex(player);
        PlayerRaceData.setFactionSelectionComplete(player, false);
        PlayerRaceData.clearAppearancePreset(player);
        PlayerRaceData.setAppearanceInitialized(player, false);
    }
}
