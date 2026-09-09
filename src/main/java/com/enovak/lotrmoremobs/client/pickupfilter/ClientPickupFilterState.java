package com.enovak.lotrmoremobs.client.pickupfilter;

import com.enovak.lotrmoremobs.pickupfilter.PlayerPickupFilterData;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side snapshot of the server-authoritative pickup filter.
 *
 * This is display/cache data only. Changes to the real filter must still
 * be requested from and applied by the server.
 */
public final class ClientPickupFilterState {

    private static final List<ItemStack> excludedItems =
            new ArrayList<ItemStack>();

    private ClientPickupFilterState() {
    }

    public static synchronized void setExcludedItems(
            List<ItemStack> items
    ) {
        excludedItems.clear();

        if (items == null) {
            return;
        }

        for (ItemStack stack : items) {
            if (excludedItems.size()
                    >= PlayerPickupFilterData.MAX_EXCLUDED_ITEMS) {
                break;
            }
            ItemStack copy = PlayerPickupFilterData.sanitizeStack(stack);
            if (copy != null) {
                excludedItems.add(copy);
            }
        }
    }

    public static synchronized List<ItemStack> getExcludedItems() {
        List<ItemStack> copy =
                new ArrayList<ItemStack>();

        for (ItemStack stack : excludedItems) {
            if (stack != null) {
                copy.add(stack.copy());
            }
        }

        return copy;
    }

    public static synchronized boolean isExcluded(ItemStack stack) {
        if (stack == null) {
            return false;
        }

        for (ItemStack excluded : excludedItems) {
            if (excluded != null
                    && excluded.isItemEqual(stack)) {
                return true;
            }
        }

        return false;
    }

    public static synchronized void clear() {
        excludedItems.clear();
    }
}