package com.fuzs.aquaacrobatics.core.asm;

import net.minecraft.entity.player.EntityPlayer;

import com.fuzs.aquaacrobatics.entity.player.AquaPoseLogic;
import com.fuzs.aquaacrobatics.entity.player.AquaPlayerPresentationLogic;
import com.fuzs.aquaacrobatics.entity.player.AquaPlayerWaterLogic;
import com.fuzs.aquaacrobatics.entity.player.IPlayerResizeable;

/** Thin runtime bridge called by the EntityPlayer ASM insertion. */
public final class AquaPlayerAsmHooks {

    private AquaPlayerAsmHooks() {}

    public static void onPlayerPrePostTick(EntityPlayer player) {
        AquaPlayerPresentationLogic.updatePrePostTick(player);
    }

    public static void onPlayerWaterStateUpdate(EntityPlayer player) {
        AquaPlayerWaterLogic.updateWaterAndSwimmingState(player);
    }

    public static void onPlayerPoseUpdate(EntityPlayer player) {
        AquaPoseLogic.updatePose(player, (IPlayerResizeable) player);
    }
}
