package com.lotrcharactercreation.network;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class StartingFactionSelectionMessage implements IMessage {

    private String serializedFactionId;
    private boolean valid;

    public StartingFactionSelectionMessage() {}

    public StartingFactionSelectionMessage(String serializedFactionId) {
        this.serializedFactionId = serializedFactionId;
        valid = LegacyC2SProtocol
            .isValidRequiredString(serializedFactionId, LegacyC2SProtocol.MAX_FACTION_ID_BYTES);
    }

    public String getSerializedFactionId() {
        return serializedFactionId;
    }

    public boolean isValid() {
        return valid;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        valid = false;
        serializedFactionId = null;
        try {
            serializedFactionId = LegacyC2SProtocol
                .readRequiredString(buffer, LegacyC2SProtocol.MAX_FACTION_ID_BYTES);
            LegacyC2SProtocol.requireFullyRead(buffer);
            valid = true;
        } catch (RuntimeException exception) {
            LegacyC2SProtocol.warnMalformedOnce("StartingFactionSelection", exception);
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        ByteBufUtils.writeUTF8String(buffer, serializedFactionId);
    }

    public static class Handler implements IMessageHandler<StartingFactionSelectionMessage, IMessage> {

        @Override
        public IMessage onMessage(StartingFactionSelectionMessage message, MessageContext context) {
            if (message.isValid()) {
                EntityPlayerMP player = context.getServerHandler().playerEntity;
                ModNetwork.enqueueStartingFactionSelection(player, message.getSerializedFactionId());
            }
            return null;
        }
    }
}
