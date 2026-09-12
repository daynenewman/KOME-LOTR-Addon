package com.lotrcharactercreation.network;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class RaceSelectionMessage implements IMessage {

    private String serializedRaceId;
    private boolean valid;

    public RaceSelectionMessage() {}

    public RaceSelectionMessage(String serializedRaceId) {
        this.serializedRaceId = serializedRaceId;
        valid = LegacyC2SProtocol.isValidRequiredString(serializedRaceId, LegacyC2SProtocol.MAX_RACE_ID_BYTES);
    }

    public String getSerializedRaceId() {
        return serializedRaceId;
    }

    public boolean isValid() {
        return valid;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        valid = false;
        serializedRaceId = null;
        try {
            serializedRaceId = LegacyC2SProtocol.readRequiredString(buffer, LegacyC2SProtocol.MAX_RACE_ID_BYTES);
            LegacyC2SProtocol.requireFullyRead(buffer);
            valid = true;
        } catch (RuntimeException exception) {
            LegacyC2SProtocol.warnMalformedOnce("RaceSelection", exception);
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        ByteBufUtils.writeUTF8String(buffer, serializedRaceId);
    }

    public static class Handler implements IMessageHandler<RaceSelectionMessage, IMessage> {

        @Override
        public IMessage onMessage(RaceSelectionMessage message, MessageContext context) {
            if (message.isValid()) {
                EntityPlayerMP player = context.getServerHandler().playerEntity;
                ModNetwork.enqueueRaceSelection(player, message.getSerializedRaceId());
            }
            return null;
        }
    }
}
