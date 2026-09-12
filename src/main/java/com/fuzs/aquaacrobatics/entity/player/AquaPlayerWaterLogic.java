package com.fuzs.aquaacrobatics.entity.player;

import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;

import com.enovak.lotrmoremobs.config.PlayerMovementMode;

import com.fuzs.aquaacrobatics.integration.efr.EFRIntegration;

/** Shared general water, underwater, and synchronized swimming-state policy. */
public final class AquaPlayerWaterLogic {

    private AquaPlayerWaterLogic() {}

    public static void updateWaterAndSwimmingState(EntityPlayer player) {
        AquaPlayerState state = ((IAquaPlayerStateHolder) player).getAquaPlayerState();
        if (player.isInWater()) {
            int increment = EFRIntegration.isSpectator(player) ? 10 : 1;
            state.timeUnderwater = MathHelper.clamp_float(state.timeUnderwater + increment, 0, 600);
        } else if (state.timeUnderwater > 0) {
            state.timeUnderwater = MathHelper.clamp_float(state.timeUnderwater - 10, 0, 600);
        }

        state.eyesInWater = player.isInsideOfMaterial(Material.water);
        ((IPlayerResizeable) player).updateSwimming();
    }

    public static float getWaterVision(EntityPlayer player) {
        if (!player.isInWater()) return 0.0F;

        float timeUnderwater = ((IAquaPlayerStateHolder) player).getAquaPlayerState().timeUnderwater;
        if (timeUnderwater >= 600.0F) return 1.0F;

        float firstStage = MathHelper.clamp_float(timeUnderwater / 100.0F, 0.0F, 1.0F);
        float secondStage = timeUnderwater < 100.0F ? 0.0F
            : MathHelper.clamp_float((timeUnderwater - 100.0F) / 500.0F, 0.0F, 1.0F);
        return firstStage * 0.6F + secondStage * 0.39999998F;
    }

    public static boolean canSwim(EntityPlayer player) {
        return PlayerMovementMode.useModernPlayerMovement(player)
            && ((IAquaPlayerStateHolder) player).getAquaPlayerState().eyesInWater && player.isInWater();
    }

    public static void updateSwimming(EntityPlayer player) {
        AquaMovementLogic.updateSwimming(player, (IPlayerResizeable) player);
    }

    public static boolean isSwimming(EntityPlayer player, boolean swimmingFlag) {
        return PlayerMovementMode.useModernPlayerMovement(player)
            && !player.capabilities.isFlying && swimmingFlag && !EFRIntegration.isSpectator(player);
    }
}
