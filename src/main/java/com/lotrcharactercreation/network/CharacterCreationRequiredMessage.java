package com.lotrcharactercreation.network;

import com.lotrcharactercreation.LOTRCharacterCreation;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class CharacterCreationRequiredMessage implements IMessage {

    private String serializedStageId;
    private String serializedRaceId;
    private String serializedSexId;
    private String serializedFactionId;
    private String appearancePresetId;

    public CharacterCreationRequiredMessage() {}

    public CharacterCreationRequiredMessage(String serializedStageId, String serializedRaceId, String serializedSexId,
        String serializedFactionId, String appearancePresetId) {
        this.serializedStageId = serializedStageId;
        this.serializedRaceId = serializedRaceId;
        this.serializedSexId = serializedSexId;
        this.serializedFactionId = serializedFactionId;
        this.appearancePresetId = appearancePresetId;
    }

    public String getSerializedStageId() {
        return serializedStageId;
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

    public String getAppearancePresetId() {
        return appearancePresetId;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        serializedStageId = ByteBufUtils.readUTF8String(buffer);
        serializedRaceId = ByteBufUtils.readUTF8String(buffer);
        serializedSexId = ByteBufUtils.readUTF8String(buffer);
        serializedFactionId = ByteBufUtils.readUTF8String(buffer);
        appearancePresetId = ByteBufUtils.readUTF8String(buffer);
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        ByteBufUtils.writeUTF8String(buffer, serializedStageId);
        ByteBufUtils.writeUTF8String(buffer, serializedRaceId);
        ByteBufUtils.writeUTF8String(buffer, serializedSexId == null ? "" : serializedSexId);
        ByteBufUtils.writeUTF8String(buffer, serializedFactionId);
        ByteBufUtils.writeUTF8String(buffer, appearancePresetId == null ? "" : appearancePresetId);
    }

    public static class Handler implements IMessageHandler<CharacterCreationRequiredMessage, IMessage> {

        @Override
        public IMessage onMessage(CharacterCreationRequiredMessage message, MessageContext context) {
            LOTRCharacterCreation.proxy.handleCharacterCreationRequired(
                message.getSerializedStageId(),
                message.getSerializedRaceId(),
                message.getSerializedSexId(),
                message.getSerializedFactionId(),
                message.getAppearancePresetId());
            return null;
        }
    }
}
