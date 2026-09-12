package com.enovak.lotrmoremobs.handler;

import com.enovak.lotrmoremobs.config.MumakilConfig;
import com.enovak.lotrmoremobs.pickupfilter.PlayerPickupFilterData;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;

/**
 * Server-authoritative pickup interception for the player's persistent filter.
 */
public class PickupFilterEventHandler {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onEntityItemPickup(EntityItemPickupEvent event) {
        if (!MumakilConfig.enableItemPickupFilter
                || event == null
                || event.entityPlayer == null
                || event.entityPlayer.worldObj == null
                || event.entityPlayer.worldObj.isRemote
                || event.item == null) {
            return;
        }

        ItemStack pickedUp = event.item.getEntityItem();

        if (pickedUp != null && PlayerPickupFilterData.isExcluded(event.entityPlayer, pickedUp)) {
            event.setCanceled(true);
        }
    }
}
