package com.fuzs.aquaacrobatics.entity.player;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovementInput;

import com.enovak.lotrmoremobs.config.PlayerMovementMode;

import com.fuzs.aquaacrobatics.config.ConfigHandler;
import com.fuzs.aquaacrobatics.entity.Pose;

/** Shared, hook-independent movement policy for player pose behavior. */
public final class AquaMovementLogic {

    public static final float FORCED_CRAWL_MOVEMENT_FACTOR = 0.3F;

    private AquaMovementLogic() {}

    public static boolean canForceCrawling(EntityPlayer player) {
        return PlayerMovementMode.useModernPlayerMovement(player)
            && ConfigHandler.MovementConfig.enableToggleCrawling && !player.isRiding()
            && !player.capabilities.isFlying && !player.isOnLadder();
    }

    public static void updateSwimming(EntityPlayer player, IPlayerResizeable resizeable) {
        if (!PlayerMovementMode.useModernPlayerMovement(player)) {
            resizeable.setSwimming(false);
            return;
        }
        if (resizeable.getShouldBeDead() || player.capabilities.isFlying || player.isRiding()) {
            resizeable.setSwimming(false);
        } else if (resizeable.isSwimming()) {
            resizeable.setSwimming(player.isSprinting() && player.isInWater());
        } else {
            resizeable.setSwimming(player.isSprinting() && resizeable.canSwim());
        }
    }

    public static boolean isForcedLandCrawling(EntityPlayer player, IPlayerResizeable resizeable,
        MovementInput movementInput, boolean forcedDown) {

        return PlayerMovementMode.useModernPlayerMovement(player)
            && !movementInput.sneak && forcedDown && !resizeable.isSwimming() && !player.isRiding()
            && !player.capabilities.isFlying;
    }

    public static void applyForcedLandCrawlMovement(EntityPlayer player, MovementInput movementInput) {
        player.setSprinting(false);
        movementInput.moveStrafe *= FORCED_CRAWL_MOVEMENT_FACTOR;
        movementInput.moveForward *= FORCED_CRAWL_MOVEMENT_FACTOR;
        player.moveStrafing = movementInput.moveStrafe;
        player.moveForward = movementInput.moveForward;
        if (((IPlayerResizeable) player).getPose() == Pose.CROUCHING && player.ySize < 0.2F) {
            player.ySize = 0.2F;
        }
    }
}
