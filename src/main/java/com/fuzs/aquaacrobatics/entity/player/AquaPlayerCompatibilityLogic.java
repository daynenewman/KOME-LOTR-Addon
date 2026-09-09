package com.fuzs.aquaacrobatics.entity.player;

import net.minecraft.entity.player.EntityPlayer;

import com.fuzs.aquaacrobatics.entity.Pose;

/** Shared implementations for pure EntityPlayer compatibility and clearance facades. */
public final class AquaPlayerCompatibilityLogic {

    private AquaPlayerCompatibilityLogic() {}

    public static boolean isActuallySneaking(EntityPlayer player) {
        return player.isSneaking();
    }

    public static boolean getShouldBeDead(EntityPlayer player) {
        return player.deathTime > 0;
    }

    public static boolean canForceCrawling(EntityPlayer player) {
        return AquaMovementLogic.canForceCrawling(player);
    }

    public static boolean isPoseClear(EntityPlayer player, Pose pose) {
        return AquaPoseLogic.isPoseClear(player, (IPlayerResizeable) player, pose);
    }
}
