package com.lotrcharactercreation.creation;

import net.minecraft.entity.player.EntityPlayerMP;

import com.lotrcharactercreation.race.PlayerRaceData;

/**
 * Server-owned lifecycle for a non-destructive administrative character edit.
 * Recreation reuses the normal staged selection flow while preserving all
 * one-time starting-character state.
 */
public final class CharacterRecreationService {

    private CharacterRecreationService() {}

    public static StartResult begin(EntityPlayerMP player) {
        if (player == null) {
            throw new IllegalArgumentException("player cannot be null");
        }

        if (PlayerRaceData.isCharacterEditAuthorized(player)) {
            if (PlayerRaceData.isCharacterCreationComplete(player)) {
                reopenAtRaceSelection(player);
            }
            return StartResult.RESUMED;
        }
        if (!PlayerRaceData.isCharacterCreationComplete(player)) {
            return StartResult.ALREADY_IN_CREATION;
        }

        PlayerRaceData.setCharacterEditAuthorized(player, true);
        reopenAtRaceSelection(player);
        return StartResult.STARTED;
    }

    public static boolean isInProgress(EntityPlayerMP player) {
        return player != null && PlayerRaceData.isCharacterEditAuthorized(player)
            && !PlayerRaceData.isCharacterCreationComplete(player);
    }

    /**
     * Completes only the selection portion of an authorized recreation. This
     * deliberately does not invoke starting allegiance or waypoint services.
     */
    public static boolean complete(EntityPlayerMP player) {
        if (!isInProgress(player) || !CharacterCreationFlowService.isReadyForFinalization(player)) {
            return false;
        }

        PlayerRaceData.setCharacterCreationComplete(player, true);
        return true;
    }

    private static void reopenAtRaceSelection(EntityPlayerMP player) {
        PlayerRaceData.setCharacterCreationComplete(player, false);
        PlayerRaceData.setRaceSelectionComplete(player, false);
    }

    public enum StartResult {
        STARTED,
        RESUMED,
        ALREADY_IN_CREATION
    }
}
