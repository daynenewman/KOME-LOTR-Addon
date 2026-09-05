package com.lotrcharactercreation.network;

import com.lotrcharactercreation.LOTRCharacterCreation;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class StartingWaypointAppliedMessage implements IMessage {

    private String serializedFactionId;
    private String waypointCodeName;

    public StartingWaypointAppliedMessage() {}

    public StartingWaypointAppliedMessage(String serializedFactionId, String waypointCodeName) {
        this.serializedFactionId = serializedFactionId;
        this.waypointCodeName = waypointCodeName;
    }

    public String getSerializedFactionId() {
        return serializedFactionId;
    }

    public String getWaypointCodeName() {
        return waypointCodeName;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        serializedFactionId = ByteBufUtils.readUTF8String(buffer);
        waypointCodeName = ByteBufUtils.readUTF8String(buffer);
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        ByteBufUtils.writeUTF8String(buffer, serializedFactionId);
        ByteBufUtils.writeUTF8String(buffer, waypointCodeName);
    }

    public static class Handler implements IMessageHandler<StartingWaypointAppliedMessage, IMessage> {

        @Override
        public IMessage onMessage(StartingWaypointAppliedMessage message, MessageContext context) {
            LOTRCharacterCreation.proxy
                .handleStartingWaypointApplied(message.getSerializedFactionId(), message.getWaypointCodeName());
            return null;
        }
    }
}
