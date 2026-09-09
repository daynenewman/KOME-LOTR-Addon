package com.fuzs.aquaacrobatics.entity;

import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;

import com.fuzs.aquaacrobatics.config.ConfigHandler;

/** Exact former EntityLivingBaseMixin air and climbing policies. */
public final class AquaLivingEntityLogic {

    private AquaLivingEntityLogic() {}

    public static boolean checkBubbleBreathing(EntityLivingBase living, Material material) {
        if (material == Material.water) return isLosingAir(living);
        return living.isInsideOfMaterial(material);
    }

    public static int getNewAirValue(EntityLivingBase living, int original) {
        if (ConfigHandler.MiscellaneousConfig.slowAirReplenish && original == 300 && living.getAir() >= -20
            && !isLosingAir(living)) {
            int oldAirValue = Math.max(living.getAir(), 0);
            return Math.min(oldAirValue + 4, 300);
        }
        return original;
    }

    public static boolean isJumpingOnLadder(EntityLivingBase living) {
        if (ConfigHandler.MovementConfig.newClimbingBehavior) {
            return living.isCollidedHorizontally || ((IAquaLivingJumpingAccess) living).aqua$isJumping();
        }
        return living.isCollidedHorizontally;
    }

    private static boolean isLosingAir(EntityLivingBase living) {
        return living.isInsideOfMaterial(Material.water);
    }
}
