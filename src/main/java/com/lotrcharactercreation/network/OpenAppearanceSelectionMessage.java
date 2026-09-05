package com.lotrcharactercreation.network;

import com.lotrcharactercreation.LOTRCharacterCreation;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class OpenAppearanceSelectionMessage implements IMessage {

    private String serializedRaceId;
    private String serializedSexId;
    private String serializedFactionId;
    private String currentPresetId;

    public OpenAppearanceSelectionMessage() {}

    public OpenAppearanceSelectionMessage(String serializedRaceId, String serializedSexId, String serializedFactionId,
        String currentPresetId) {
        this.serializedRaceId = serializedRaceId;
        this.serializedSexId = serializedSexId;
        this.serializedFactionId = serializedFactionId;
        this.currentPresetId = currentPresetId;
    }

    public String getSerializedRaceId() {
        return serializedRaceId;
    }

    public String getSerializedSexId() {
        return serializedSexId;
    }

    public String getSerializedFactionId() {
        return serializedFactionId;
    }

    public String getCurrentPresetId() {
        return currentPresetId;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        serializedRaceId = ByteBufUtils.readUTF8String(buffer);
        serializedSexId = readNullableString(buffer);
        serializedFactionId = ByteBufUtils.readUTF8String(buffer);
        currentPresetId = readNullableString(buffer);
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        ByteBufUtils.writeUTF8String(buffer, serializedRaceId);
        writeNullableString(buffer, serializedSexId);
        ByteBufUtils.writeUTF8String(buffer, serializedFactionId);
        writeNullableString(buffer, currentPresetId);
    }

    private static String readNullableString(ByteBuf buffer) {
        String value = ByteBufUtils.readUTF8String(buffer);
        return value.isEmpty() ? null : value;
    }

    private static void writeNullableString(ByteBuf buffer, String value) {
        ByteBufUtils.writeUTF8String(buffer, value == null ? "" : value);
    }

    public static class Handler implements IMessageHandler<OpenAppearanceSelectionMessage, IMessage> {

        @Override
        public IMessage onMessage(OpenAppearanceSelectionMessage message, MessageContext context) {
            LOTRCharacterCreation.proxy.handleOpenAppearanceSelection(
                message.getSerializedRaceId(),
                message.getSerializedSexId(),
                message.getSerializedFactionId(),
                message.getCurrentPresetId());
            return null;
        }
    }
}
