package com.lotrcharactercreation.network;

import com.lotrcharactercreation.LOTRCharacterCreation;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class OpenSexSelectionMessage implements IMessage {

    private String serializedRaceId;
    private String serializedSexId;

    public OpenSexSelectionMessage() {}

    public OpenSexSelectionMessage(String serializedRaceId, String serializedSexId) {
        this.serializedRaceId = serializedRaceId;
        this.serializedSexId = serializedSexId;
    }

    public String getSerializedRaceId() {
        return serializedRaceId;
    }

    public String getSerializedSexId() {
        return serializedSexId;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        serializedRaceId = ByteBufUtils.readUTF8String(buffer);
        serializedSexId = ByteBufUtils.readUTF8String(buffer);
        if (serializedSexId.isEmpty()) {
            serializedSexId = null;
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        ByteBufUtils.writeUTF8String(buffer, serializedRaceId);
        ByteBufUtils.writeUTF8String(buffer, serializedSexId == null ? "" : serializedSexId);
    }

    public static class Handler implements IMessageHandler<OpenSexSelectionMessage, IMessage> {

        @Override
        public IMessage onMessage(OpenSexSelectionMessage message, MessageContext context) {
            LOTRCharacterCreation.proxy
                .handleOpenSexSelection(message.getSerializedRaceId(), message.getSerializedSexId());
            return null;
        }
    }
}
