package com.lotrcharactercreation.network;

import java.util.UUID;

import com.lotrcharactercreation.LOTRCharacterCreation;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class PlayerAppearanceSyncMessage implements IMessage {

    private long playerIdMost;
    private long playerIdLeast;
    private int entityId;
    private String serializedRaceId;
    private String serializedSexId;
    private String appearancePresetId;
    private boolean characterCreationComplete;

    public PlayerAppearanceSyncMessage() {}

    public PlayerAppearanceSyncMessage(UUID playerId, int entityId, String serializedRaceId, String serializedSexId,
        String appearancePresetId, boolean characterCreationComplete) {
        playerIdMost = playerId.getMostSignificantBits();
        playerIdLeast = playerId.getLeastSignificantBits();
        this.entityId = entityId;
        this.serializedRaceId = serializedRaceId;
        this.serializedSexId = serializedSexId;
        this.appearancePresetId = appearancePresetId;
        this.characterCreationComplete = characterCreationComplete;
    }

    public UUID getPlayerId() {
        return new UUID(playerIdMost, playerIdLeast);
    }

    public int getEntityId() {
        return entityId;
    }

    public String getSerializedRaceId() {
        return serializedRaceId;
    }

    public String getSerializedSexId() {
        return serializedSexId;
    }

    public String getAppearancePresetId() {
        return appearancePresetId;
    }

    public boolean isCharacterCreationComplete() {
        return characterCreationComplete;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        playerIdMost = buffer.readLong();
        playerIdLeast = buffer.readLong();
        entityId = buffer.readInt();
        serializedRaceId = readNullableString(buffer);
        serializedSexId = readNullableString(buffer);
        appearancePresetId = readNullableString(buffer);
        characterCreationComplete = buffer.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeLong(playerIdMost);
        buffer.writeLong(playerIdLeast);
        buffer.writeInt(entityId);
        writeNullableString(buffer, serializedRaceId);
        writeNullableString(buffer, serializedSexId);
        writeNullableString(buffer, appearancePresetId);
        buffer.writeBoolean(characterCreationComplete);
    }

    private static String readNullableString(ByteBuf buffer) {
        String value = ByteBufUtils.readUTF8String(buffer);
        return value.isEmpty() ? null : value;
    }

    private static void writeNullableString(ByteBuf buffer, String value) {
        ByteBufUtils.writeUTF8String(buffer, value == null ? "" : value);
    }

    public static class Handler implements IMessageHandler<PlayerAppearanceSyncMessage, IMessage> {

        @Override
        public IMessage onMessage(PlayerAppearanceSyncMessage message, MessageContext context) {
            LOTRCharacterCreation.proxy.handlePlayerAppearanceSync(
                message.getPlayerId(),
                message.getEntityId(),
                message.getSerializedRaceId(),
                message.getSerializedSexId(),
                message.getAppearancePresetId(),
                message.isCharacterCreationComplete());
            return null;
        }
    }
}
