package com.fuzs.aquaacrobatics.entity.player;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraftforge.fluids.IFluidBlock;

import com.enovak.lotrmoremobs.config.PlayerMovementMode;

/** Shared predicates and numerical rules for Aqua's existing sprint-swim travel path. */
public final class AquaSwimmingTravelLogic {

    private AquaSwimmingTravelLogic() {}

    public static boolean isActive(EntityPlayer player, IPlayerResizeable resizeable) {
        return PlayerMovementMode.useModernPlayerMovement(player) && resizeable.isSwimming() && !player.isRiding() && !player.capabilities.isFlying && player.isInWater()
            && !player.isOnLadder();
    }

    /**
     * Performs the complete former EntityPlayerMixin HEAD-cancelled swim path.
     *
     * @return {@code true} only when Aqua performed travel and vanilla must return immediately
     */
    public static boolean travelIfActive(EntityPlayer player, IPlayerResizeable resizeable, float strafe, float forward) {
        if (!isActive(player, resizeable)) return false;

        double d0 = player.posX;
        double d1 = player.posY;
        double d2 = player.posZ;

        double d3 = player.getLookVec().yCoord;
        double d4 = getVerticalSteeringAcceleration(d3);
        Block fluidState = player.worldObj.getBlock(
            (int) player.posX,
            (int) (player.posY + 1.0 - 0.1),
            (int) player.posZ);
        if (d3 <= 0.0 || ((IAquaJumpingAccess) player).aqua$isJumping() || fluidState instanceof BlockLiquid
            || fluidState instanceof IFluidBlock) {

            double d5 = player.motionY;
            player.motionY += (d3 - d5) * d4;
        }

        double d8 = player.posY;
        float f5 = getHorizontalDrag();
        float f6 = getBaseAcceleration();
        float f7 = 1; // (float) EnchantmentHelper.getDepthStriderModifier(player);
        if (f7 > 3.0F) {

            f7 = 3.0F;
        }

        if (!player.onGround) {

            f7 *= 0.5F;
        }

        if (f7 > 0.0F) {

            f5 += (0.54600006F - f5) * f7 / 3.0F;
            f6 += (player.getAIMoveSpeed() - f6) * f7 / 3.0F;
        }

        player.moveFlying(strafe, forward, f6);
        player.moveEntity(player.motionX, player.motionY, player.motionZ);
        player.motionX *= f5;
        player.motionY *= 0.8;
        player.motionZ *= f5;
        applyGravity(player);
        if (player.isCollidedHorizontally && player.isOffsetPositionInLiquid(
            player.motionX,
            player.motionY + 0.6000000238418579D - player.posY + d8,
            player.motionZ)) {

            player.motionY = 0.3;
        }
        updateLimbSwing(player);
        player.addMovementStat(player.posX - d0, player.posY - d1, player.posZ - d2);
        return true;
    }

    public static float getVerticalSteeringAcceleration(double lookY) {
        return lookY < -0.2D ? 0.085F : 0.06F;
    }

    public static float getHorizontalDrag() {
        return 0.9F;
    }

    public static float getBaseAcceleration() {
        return 0.02F;
    }

    public static void applyGravity(EntityPlayer player) {
        if (!player.isSprinting()) {
            if (player.motionY <= 0.0D && Math.abs(player.motionY - 0.005D) >= 0.003D
                && Math.abs(player.motionY - 0.08D / 16.0D) < 0.003D) {
                player.motionY = -0.003D;
            } else {
                player.motionY -= 0.08D / 16.0D;
            }
        }
    }

    private static void updateLimbSwing(EntityPlayer player) {
        player.prevLimbSwingAmount = player.limbSwingAmount;
        double d5 = player.posX - player.prevPosX;
        double d7 = player.posZ - player.prevPosZ;
        // double d9 = player instanceof EntityFlying ? player.posY - player.prevPosY : 0.0;
        double d9 = 0.0;
        float f10 = MathHelper.sqrt_double(d5 * d5 + d9 * d9 + d7 * d7) * 4.0F;

        if (f10 > 1.0F) {

            f10 = 1.0F;
        }

        player.limbSwingAmount += (f10 - player.limbSwingAmount) * 0.4F;
        player.limbSwing += player.limbSwingAmount;
    }
}
