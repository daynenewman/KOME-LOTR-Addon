package com.lotrcharactercreation.network;

import com.lotrcharactercreation.LOTRCharacterCreation;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class RaceSelectionAcceptedMessage implements IMessage {

    private String serializedRaceId;

    public RaceSelectionAcceptedMessage() {}

    public RaceSelectionAcceptedMessage(String serializedRaceId) {
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

    public static class Handler implements IMessageHandler<RaceSelectionAcceptedMessage, IMessage> {

        @Override
        public IMessage onMessage(RaceSelectionAcceptedMessage message, MessageContext context) {
            LOTRCharacterCreation.proxy.handleRaceSelectionAccepted(message.getSerializedRaceId());
            return null;
        }
    }
}
