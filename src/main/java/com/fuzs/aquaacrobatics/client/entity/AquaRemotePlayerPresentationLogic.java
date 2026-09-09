package com.fuzs.aquaacrobatics.client.entity;

import net.minecraft.client.entity.EntityOtherPlayerMP;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Client-only remote-player presentation state. */
@SideOnly(Side.CLIENT)
public final class AquaRemotePlayerPresentationLogic {

    private AquaRemotePlayerPresentationLogic() {}

    public static void afterSuperOnUpdate(EntityOtherPlayerMP player) {
        player.yOffset = 0.0F;
    }
}
