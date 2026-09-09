package com.enovak.lotrmoremobs.siege.management;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.player.EntityPlayerMP;

/** Lifecycle cleanup for transient INSPECT_EXISTING sessions only. */
public final class GateInspectionSessionEventHandler {

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            GateInspectionSessionManager.tick();
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        close(event.player);
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(
            PlayerEvent.PlayerChangedDimensionEvent event
    ) {
        close(event.player);
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        close(event.player);
    }

    private static void close(net.minecraft.entity.player.EntityPlayer player) {
        if (player instanceof EntityPlayerMP) {
            GateInspectionSessionManager.closeForPlayer(
                    (EntityPlayerMP)player
            );
        }
    }
}
