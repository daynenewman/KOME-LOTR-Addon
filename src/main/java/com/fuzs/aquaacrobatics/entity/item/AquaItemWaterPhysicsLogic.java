package com.fuzs.aquaacrobatics.entity.item;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.entity.item.EntityItem;

import com.fuzs.aquaacrobatics.config.ConfigHandler;
import com.fuzs.aquaacrobatics.integration.IntegrationManager;
import com.fuzs.aquaacrobatics.integration.ae2.AE2Integration;
import com.fuzs.aquaacrobatics.util.BlockPos;

/** Exact former EntityItemMixin buoyancy policy. */
public final class AquaItemWaterPhysicsLogic {
    private AquaItemWaterPhysicsLogic() {}

    public static double getMotionYForUpdate(EntityItem item) {
        if (!shouldBeBuoyant(item)) return -0.03999999910593033D;
        double eyePosition = item.posY + (double) item.getEyeHeight();
        BlockPos eyeBlockPos = new BlockPos(item.posX, eyePosition, item.posZ);
        Block state = item.worldObj.getBlock(eyeBlockPos.getX(), eyeBlockPos.getY(), eyeBlockPos.getZ());
        int metadata = item.worldObj.getBlockMetadata(eyeBlockPos.getX(), eyeBlockPos.getY(), eyeBlockPos.getZ());
        if (state.getMaterial() == Material.water && state instanceof BlockLiquid) {
            float thresholdHeight = eyeBlockPos.getY() + BlockLiquid.getLiquidHeightPercent(metadata) + (1f / 9f);
            if (eyePosition < thresholdHeight) {
                applyFloatMotion(item);
                return 0.03999999910593033D;
            }
        }
        return item.motionY;
    }

    private static boolean shouldBeBuoyant(EntityItem item) {
        return ConfigHandler.MiscellaneousConfig.floatingItems
            && !(IntegrationManager.isAE2Enabled() && AE2Integration.isGrowingCrystal(item));
    }

    private static void applyFloatMotion(EntityItem item) {
        if (item.motionY < (double) 0.06F) item.motionY += (double) 5.0E-4F;
        item.motionX *= 0.99F;
        item.motionZ *= 0.99F;
    }
}
