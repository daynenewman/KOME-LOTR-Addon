package com.lotrcharactercreation.network;

import com.lotrcharactercreation.LOTRCharacterCreation;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class SexSelectionResultMessage implements IMessage {

    private boolean accepted;
    private String serializedSexId;

    public SexSelectionResultMessage() {}

    public SexSelectionResultMessage(boolean accepted, String serializedSexId) {
        this.accepted = accepted;
        this.serializedSexId = serializedSexId;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public String getSerializedSexId() {
        return serializedSexId;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        accepted = buffer.readBoolean();
        serializedSexId = ByteBufUtils.readUTF8String(buffer);
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeBoolean(accepted);
        ByteBufUtils.writeUTF8String(buffer, serializedSexId == null ? "" : serializedSexId);
    }

    public static class Handler implements IMessageHandler<SexSelectionResultMessage, IMessage> {

        @Override
        public IMessage onMessage(SexSelectionResultMessage message, MessageContext context) {
            LOTRCharacterCreation.proxy.handleSexSelectionResult(message.isAccepted(), message.getSerializedSexId());
            return null;
        }
    }
}
