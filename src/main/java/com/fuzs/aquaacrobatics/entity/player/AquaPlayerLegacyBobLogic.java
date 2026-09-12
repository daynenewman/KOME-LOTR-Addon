package com.fuzs.aquaacrobatics.entity.player;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;

/** Shared legacy EntityPlayer bob-field maintenance; independent from AquaCameraState. */
public final class AquaPlayerLegacyBobLogic {

    private AquaPlayerLegacyBobLogic() {}

    public static void update(EntityPlayer player) {
        float f = 0.0F;
        IPlayerResizeable resizeable = (IPlayerResizeable) player;
        if (player.onGround && !resizeable.getShouldBeDead() && !resizeable.isSwimming()) {
            f = Math.min(0.1F, MathHelper.sqrt_double(player.motionX * player.motionX + player.motionZ * player.motionZ));
        }

        player.cameraYaw = player.prevCameraYaw + (f - player.prevCameraYaw) * 0.4F;
        player.cameraPitch = 0.0F;
    }
}
