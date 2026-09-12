package com.fuzs.aquaacrobatics.integration.efr;

import net.minecraft.entity.player.EntityPlayer;

public class EFRIntegration {

    public static boolean isElytraFlying(EntityPlayer entityPlayer) {
        return false;
    }

    public static float getTicksElytraFlying(EntityPlayer entityPlayer) {
        return 0.0F;
    }

    public static boolean isSpectator(EntityPlayer entityPlayer) {
        return false;
    }

    public static byte elytraDataWatcherFlag() {
        return 7;
    }
}