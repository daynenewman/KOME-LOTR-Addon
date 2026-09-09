package com.fuzs.aquaacrobatics.client.render;

import com.fuzs.aquaacrobatics.config.ConfigHandler;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Client-only warped-water overlay policy preserved from the former ItemRenderer Mixin. */
@SideOnly(Side.CLIENT)
public final class AquaWaterOverlayRenderLogic {

    private AquaWaterOverlayRenderLogic() {}

    public static float getWarpedOverlayAlpha(float originalOpacity) {
        return ConfigHandler.BlocksConfig.newWaterColors ? 0.1F : originalOpacity;
    }
}
