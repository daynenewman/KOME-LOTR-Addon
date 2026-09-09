package com.enovak.lotrmoremobs.siege.repair;

import lotr.common.item.LOTRItemCoin;
import net.minecraft.entity.player.EntityPlayer;

public final class SiegeCurrency {

    private SiegeCurrency() {
    }

    public static int getCoinValue(EntityPlayer player) {
        return player == null
                ? 0
                : Math.max(0, LOTRItemCoin.getInventoryValue(player, false));
    }

    public static boolean tryTakeCoinValue(
            EntityPlayer player,
            int value
    ) {
        if (player == null || value < 0) {
            return false;
        }
        if (value == 0) {
            return true;
        }
        if (getCoinValue(player) < value) {
            return false;
        }
        LOTRItemCoin.takeCoins(value, player);
        player.inventory.markDirty();
        return true;
    }
}
