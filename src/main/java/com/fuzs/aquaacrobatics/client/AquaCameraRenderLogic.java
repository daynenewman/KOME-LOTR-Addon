package com.fuzs.aquaacrobatics.client;

import com.enovak.lotrmoremobs.config.PlayerMovementMode;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;

/** Client-only adapter for EntityRenderer's established orientCamera local. */
public final class AquaCameraRenderLogic {

    private AquaCameraRenderLogic() {}

    public static float getCameraHeight(EntityLivingBase viewEntity, float partialTicks, float vanillaHeight) {
        Minecraft minecraft = Minecraft.getMinecraft();
        EntityPlayer player = minecraft.thePlayer;
        if (viewEntity != player || player == null) return vanillaHeight;
        if (!PlayerMovementMode.useModernPlayerMovement(player)) return vanillaHeight;
        return AquaCameraState.INSTANCE.getLegacyCameraCorrection(player, partialTicks);
    }
}
