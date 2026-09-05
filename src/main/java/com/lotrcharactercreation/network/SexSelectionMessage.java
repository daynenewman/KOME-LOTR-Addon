package com.lotrcharactercreation.network;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class SexSelectionMessage implements IMessage {

    private String serializedSexId;

    public SexSelectionMessage() {}

    public SexSelectionMessage(String serializedSexId) {
        this.serializedSexId = serializedSexId;
    }

    public String getSerializedSexId() {
        return serializedSexId;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        serializedSexId = ByteBufUtils.readUTF8String(buffer);
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        ByteBufUtils.writeUTF8String(buffer, serializedSexId);
    }

    public static class Handler implements IMessageHandler<SexSelectionMessage, IMessage> {

        @Override
        public IMessage onMessage(SexSelectionMessage message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().playerEntity;
            ModNetwork.enqueueSexSelection(player, message.getSerializedSexId());
            return null;
        }
    }
}
