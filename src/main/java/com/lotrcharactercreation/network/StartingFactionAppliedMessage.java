package com.lotrcharactercreation.network;

import com.lotrcharactercreation.LOTRCharacterCreation;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class StartingFactionAppliedMessage implements IMessage {

    private String serializedFactionId;

    public StartingFactionAppliedMessage() {}

    public StartingFactionAppliedMessage(String serializedFactionId) {
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

    public static class Handler implements IMessageHandler<StartingFactionAppliedMessage, IMessage> {

        @Override
        public IMessage onMessage(StartingFactionAppliedMessage message, MessageContext context) {
            LOTRCharacterCreation.proxy.handleStartingFactionApplied(message.getSerializedFactionId());
            return null;
        }
    }
}
