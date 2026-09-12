package com.enovak.lotrmoremobs.network;

import com.enovak.lotrmoremobs.pickupfilter.PickupFilterRequestManager;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;

/** Client -> server request to clear the complete pickup filter. */
public class PickupFilterClearPacket implements IMessage {

    @Override
    public void fromBytes(ByteBuf buf) {
    }

    @Override
    public void toBytes(ByteBuf buf) {
    }

    public static class Handler
            implements IMessageHandler<PickupFilterClearPacket, IMessage> {

        @Override
        public IMessage onMessage(
                PickupFilterClearPacket message,
                MessageContext ctx
        ) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player != null) {
                PickupFilterRequestManager.enqueueClear(player);
            }
            return null;
        }
    }
}
