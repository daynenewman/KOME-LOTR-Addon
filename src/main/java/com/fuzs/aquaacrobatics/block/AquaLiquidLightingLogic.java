package com.fuzs.aquaacrobatics.block;

import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;

import com.fuzs.aquaacrobatics.config.ConfigHandler;

/** Former BlockLiquidMixin brighter-water predicate. */
public final class AquaLiquidLightingLogic {
    private AquaLiquidLightingLogic() {}

    public static boolean hasBrighterWaterOpacity(BlockLiquid block) {
        return ConfigHandler.BlocksConfig.brighterWater && block.getMaterial() == Material.water;
    }
}
