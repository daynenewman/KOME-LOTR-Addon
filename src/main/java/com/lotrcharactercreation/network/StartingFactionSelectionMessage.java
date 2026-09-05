package com.lotrcharactercreation.network;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class StartingFactionSelectionMessage implements IMessage {

    private String serializedFactionId;

    public StartingFactionSelectionMessage() {}

    public StartingFactionSelectionMessage(String serializedFactionId) {
        this.serializedFactionId = serializedFactionId;
    }

    public String getSerializedFactionId() {
        return serializedFactionId;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        serializedFactionId = ByteBufUtils.readUTF8String(buffer);
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        ByteBufUtils.writeUTF8String(buffer, serializedFactionId);
    }

    public static class Handler implements IMessageHandler<StartingFactionSelectionMessage, IMessage> {

        @Override
        public IMessage onMessage(StartingFactionSelectionMessage message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().playerEntity;
            ModNetwork.enqueueStartingFactionSelection(player, message.getSerializedFactionId());
            return null;
        }
    }
}
