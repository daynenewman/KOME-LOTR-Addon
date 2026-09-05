package com.lotrcharactercreation.body;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

import com.lotrcharactercreation.race.PlayerRace;
import com.lotrcharactercreation.race.PlayerRaceData;

public final class PlayerRaceEyeService {

    private static final float VANILLA_LOCAL_PLAYER_Y_OFFSET = 1.62F;

    private PlayerRaceEyeService() {}

    public static boolean applyStoredServerEyeHeight(EntityPlayerMP player) {
        return applyServerEyeHeight(player, PlayerRaceData.getRace(player));
    }

    public static boolean applyServerEyeHeight(EntityPlayerMP player, PlayerRace race) {
        if (player == null || !player.isEntityAlive()) {
            return false;
        }

        return setEyeHeight(player, getDesiredServerEyeHeight(player, race));
    }

    public static boolean applyLocalEyeHeight(EntityPlayer player, PlayerRace race) {
        if (player == null || !player.isEntityAlive()) {
            return false;
        }

        return setEyeHeight(player, getDesiredLocalEyeHeight(player, race));
    }

    public static float getDesiredServerEyeHeight(EntityPlayer player, PlayerRace race) {
        RaceBodyDefinition body = getPrototypeEyeBody(race);
        return body == null ? player.getDefaultEyeHeight() : body.getTargetEyeHeight();
    }

    public static float getDesiredLocalEyeHeight(EntityPlayer player, PlayerRace race) {
        RaceBodyDefinition body = getPrototypeEyeBody(race);
        if (body == null) {
            return player.getDefaultEyeHeight();
        }

        return player.getDefaultEyeHeight() + (body.getTargetEyeHeight() - VANILLA_LOCAL_PLAYER_Y_OFFSET);
    }

    public static float getLocalRenderCameraYOffset(PlayerRace race) {
        RaceBodyDefinition body = getPrototypeEyeBody(race);
        if (body == null) {
            return VANILLA_LOCAL_PLAYER_Y_OFFSET;
        }

        return VANILLA_LOCAL_PLAYER_Y_OFFSET + (VANILLA_LOCAL_PLAYER_Y_OFFSET - body.getTargetEyeHeight());
    }

    public static boolean hasPrototypeEyeHeight(PlayerRace race) {
        return getPrototypeEyeBody(race) != null;
    }

    private static boolean setEyeHeight(EntityPlayer player, float eyeHeight) {
        if (Float.compare(player.eyeHeight, eyeHeight) == 0) {
            return false;
        }

        player.eyeHeight = eyeHeight;
        return true;
    }

    private static RaceBodyDefinition getPrototypeEyeBody(PlayerRace race) {
        RaceBodyDefinition body = RaceBodyDefinition.forRace(race);
        return body.isPrototypeSizeEnabled() && body.hasTargetEyeHeight() ? body : null;
    }
}
