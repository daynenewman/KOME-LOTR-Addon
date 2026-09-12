package com.enovak.lotrmoremobs.handler;

import com.enovak.lotrmoremobs.pickupfilter.PickupFilterNetwork;
import com.enovak.lotrmoremobs.pickupfilter.PickupFilterRequestManager;
import com.enovak.lotrmoremobs.pickupfilter.PlayerPickupFilterData;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Pickup-filter lifecycle synchronization and authoritative server-tick request
 * processing.
 */
public class PickupFilterConnectionHandler {

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            PickupFilterRequestManager.processServerTick();
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        refresh(event.player);
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        clearPending(event.player);
        refresh(event.player);
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(
            PlayerEvent.PlayerChangedDimensionEvent event
    ) {
        clearPending(event.player);
        refresh(event.player);
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP)event.player;
            PickupFilterRequestManager.clearPlayer(player.getUniqueID());
            PlayerPickupFilterData.clearCache(player.getUniqueID());
        }
    }

    private void clearPending(net.minecraft.entity.player.EntityPlayer player) {
        if (player instanceof EntityPlayerMP) {
            PickupFilterRequestManager.clearPlayer(player.getUniqueID());
        }
    }

    private void refresh(net.minecraft.entity.player.EntityPlayer player) {
        if (player instanceof EntityPlayerMP) {
            PlayerPickupFilterData.clearCache(player);
            PickupFilterNetwork.syncToPlayer((EntityPlayerMP)player);
        }
    }
}
