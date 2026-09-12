package com.fuzs.aquaacrobatics.entity;

import net.minecraft.block.Block;
import net.minecraft.block.BlockVine;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;

import com.enovak.lotrmoremobs.config.PlayerMovementMode;
import net.minecraft.entity.player.EntityPlayer;

import com.fuzs.aquaacrobatics.config.ConfigHandler;
import com.fuzs.aquaacrobatics.entity.player.IPlayerResizeable;

/** Shared generic Entity water, bubble-column, and climbing primitives. */
public final class AquaEntityPrimitiveLogic {

    private AquaEntityPrimitiveLogic() {}

    public static double adjustWaterMovementY(Entity entity, double original) {
        return entity instanceof IPlayerResizeable
            && (!(entity instanceof EntityPlayer) || PlayerMovementMode.useModernPlayerMovement((EntityPlayer) entity))
            && ((IPlayerResizeable) entity).getPose() == Pose.SWIMMING
                ? -0.2500000059604645D
                : original;
    }

    public static void onEnterBubbleColumn(Entity entity, boolean downwards) {
        if (entity instanceof EntityPlayer
                && !PlayerMovementMode.useModernPlayerMovement((EntityPlayer)entity)) return;
        if (!downwards) {
            entity.motionY = Math.min(0.7D, entity.motionY + 0.06D);
        } else entity.motionY = Math.max(-0.3D, entity.motionY - 0.03D);
        entity.fallDistance = 0.0F;
    }

    public static void onEnterBubbleColumnWithAirAbove(Entity entity, boolean downwards) {
        if (entity instanceof EntityPlayer
                && !PlayerMovementMode.useModernPlayerMovement((EntityPlayer)entity)) return;
        if (!downwards) {
            entity.motionY = Math.min(1.8D, entity.motionY + 0.1D);
        } else entity.motionY = Math.max(-0.9D, entity.motionY - 0.03D);
    }

    public static Block getFakeClimbingBlock(Block original) {
        if (ConfigHandler.MovementConfig.newClimbingBehavior && original instanceof BlockVine) return Blocks.ladder;
        return original;
    }
}
