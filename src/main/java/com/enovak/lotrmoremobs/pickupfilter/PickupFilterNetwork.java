package com.enovak.lotrmoremobs.pickupfilter;

import com.enovak.lotrmoremobs.Main;
import com.enovak.lotrmoremobs.network.PickupFilterSyncPacket;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Common server-side networking helpers for the pickup filter.
 */
public final class PickupFilterNetwork {

    private PickupFilterNetwork() {
    }

    public static void syncToPlayer(EntityPlayerMP player) {
        if (player == null) {
            return;
        }

        Main.network.sendTo(
                new PickupFilterSyncPacket(
                        PlayerPickupFilterData.getExcludedItems(player)
                ),
                player
        );
    }
}