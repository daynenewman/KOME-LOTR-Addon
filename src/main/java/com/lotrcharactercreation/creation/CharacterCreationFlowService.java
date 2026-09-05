package com.lotrcharactercreation.creation;

import net.minecraft.entity.player.EntityPlayerMP;

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

    public static boolean selectRace(EntityPlayerMP player, PlayerRace race) {
        if (race == null || !choicesAreEditable(player)
            || getNextRequiredStage(player) != CharacterCreationStage.RACE) {
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

    public static boolean goBackFrom(EntityPlayerMP player, CharacterCreationStage sourceStage) {
        if (sourceStage == null || !choicesAreEditable(player) || getNextRequiredStage(player) != sourceStage) {
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

    private static boolean choicesAreEditable(EntityPlayerMP player) {
        return !PlayerRaceData.isCharacterCreationComplete(player) && !PlayerRaceData.isStartingFactionApplied(player)
            && !PlayerRaceData.isStartingWaypointApplied(player);
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
