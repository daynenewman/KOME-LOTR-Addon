package com.enovak.lotrmoremobs.network;

import com.enovak.lotrmoremobs.pickupfilter.PickupFilterRequestManager;
import com.enovak.lotrmoremobs.pickupfilter.PlayerPickupFilterData;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

/**
 * Client -> server request to toggle one item in the player's pickup filter.
 * The handler performs bounded intake only; mutation occurs on the server tick.
 */
public class PickupFilterTogglePacket implements IMessage {

    private ItemStack stack;

    public PickupFilterTogglePacket() {
    }

    public PickupFilterTogglePacket(ItemStack stack) {
        this.stack = PlayerPickupFilterData.sanitizeStack(stack);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        stack = null;
        try {
            stack = PlayerPickupFilterData.sanitizeStack(
                    ByteBufUtils.readItemStack(buf)
            );
        } catch (RuntimeException ignored) {
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeItemStack(buf, stack);
    }

    public static class Handler
            implements IMessageHandler<PickupFilterTogglePacket, IMessage> {

        @Override
        public IMessage onMessage(
                PickupFilterTogglePacket message,
                MessageContext ctx
        ) {
            if (message == null || message.stack == null) {
                return null;
            }

            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player != null) {
                PickupFilterRequestManager.enqueueToggle(
                        player,
                        message.stack
                );
            }
            return null;
        }
    }
}
