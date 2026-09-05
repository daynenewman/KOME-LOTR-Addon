package com.lotrcharactercreation.network;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class RaceSelectionMessage implements IMessage {

    private String serializedRaceId;

    public RaceSelectionMessage() {}

    public RaceSelectionMessage(String serializedRaceId) {
        this.serializedRaceId = serializedRaceId;
    }

    public String getSerializedRaceId() {
        return serializedRaceId;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        serializedRaceId = ByteBufUtils.readUTF8String(buffer);
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        ByteBufUtils.writeUTF8String(buffer, serializedRaceId);
    }

    public static class Handler implements IMessageHandler<RaceSelectionMessage, IMessage> {

        @Override
        public IMessage onMessage(RaceSelectionMessage message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().playerEntity;
            ModNetwork.enqueueRaceSelection(player, message.getSerializedRaceId());
            return null;
        }
    }
}
