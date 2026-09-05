package com.lotrcharactercreation.network;

import com.lotrcharactercreation.LOTRCharacterCreation;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class AppearanceSelectionResultMessage implements IMessage {

    private boolean accepted;
    private String presetId;

    public AppearanceSelectionResultMessage() {}

    public AppearanceSelectionResultMessage(boolean accepted, String presetId) {
        this.accepted = accepted;
        this.presetId = presetId;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public String getPresetId() {
        return presetId;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        accepted = buffer.readBoolean();
        presetId = ByteBufUtils.readUTF8String(buffer);
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeBoolean(accepted);
        ByteBufUtils.writeUTF8String(buffer, presetId == null ? "" : presetId);
    }

    public static class Handler implements IMessageHandler<AppearanceSelectionResultMessage, IMessage> {

        @Override
        public IMessage onMessage(AppearanceSelectionResultMessage message, MessageContext context) {
            LOTRCharacterCreation.proxy.handleAppearanceSelectionResult(message.isAccepted(), message.getPresetId());
            return null;
        }
    }
}
