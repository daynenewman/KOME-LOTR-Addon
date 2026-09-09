package com.enovak.lotrmoremobs.network;

import com.enovak.lotrmoremobs.Main;
import com.enovak.lotrmoremobs.pickupfilter.PlayerPickupFilterData;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> client packet containing the player's complete pickup-filter list.
 * The server remains authoritative; this only updates the client display cache.
 */
public class PickupFilterSyncPacket implements IMessage {

    private List<ItemStack> excludedItems = new ArrayList<ItemStack>();

    public PickupFilterSyncPacket() {
    }

    public PickupFilterSyncPacket(List<ItemStack> excludedItems) {
        if (excludedItems == null) {
            return;
        }

        for (ItemStack stack : excludedItems) {
            if (this.excludedItems.size()
                    >= PlayerPickupFilterData.MAX_EXCLUDED_ITEMS) {
                break;
            }
            ItemStack copy = PlayerPickupFilterData.sanitizeStack(stack);
            if (copy != null) {
                this.excludedItems.add(copy);
            }
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        excludedItems.clear();

        int count = buf.readInt();
        if (count < 0
                || count > PlayerPickupFilterData.MAX_EXCLUDED_ITEMS) {
            throw new IllegalArgumentException(
                    "Invalid Pickup Filter sync count: " + count
            );
        }

        for (int i = 0; i < count; ++i) {
            ItemStack stack = PlayerPickupFilterData.sanitizeStack(
                    ByteBufUtils.readItemStack(buf)
            );
            if (stack != null) {
                excludedItems.add(stack);
            }
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        int count = Math.min(
                excludedItems.size(),
                PlayerPickupFilterData.MAX_EXCLUDED_ITEMS
        );
        buf.writeInt(count);

        for (int i = 0; i < count; ++i) {
            ByteBufUtils.writeItemStack(buf, excludedItems.get(i));
        }
    }

    public List<ItemStack> getExcludedItems() {
        List<ItemStack> copy = new ArrayList<ItemStack>();
        for (ItemStack stack : excludedItems) {
            ItemStack sanitized = PlayerPickupFilterData.sanitizeStack(stack);
            if (sanitized != null) {
                copy.add(sanitized);
            }
        }
        return copy;
    }

    public static class Handler
            implements IMessageHandler<PickupFilterSyncPacket, IMessage> {

        @Override
        public IMessage onMessage(
                PickupFilterSyncPacket message,
                MessageContext ctx
        ) {
            if (message != null) {
                Main.proxy.handlePickupFilterSync(message);
            }
            return null;
        }
    }
}
