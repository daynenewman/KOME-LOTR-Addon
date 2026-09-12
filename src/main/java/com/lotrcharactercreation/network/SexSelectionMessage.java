package com.lotrcharactercreation.network;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class SexSelectionMessage implements IMessage {

    private String serializedSexId;
    private boolean valid;

    public SexSelectionMessage() {}

    public SexSelectionMessage(String serializedSexId) {
        this.serializedSexId = serializedSexId;
        valid = LegacyC2SProtocol.isValidRequiredString(serializedSexId, LegacyC2SProtocol.MAX_SEX_ID_BYTES);
    }

    public String getSerializedSexId() {
        return serializedSexId;
    }

    public boolean isValid() {
        return valid;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        valid = false;
        serializedSexId = null;
        try {
            serializedSexId = LegacyC2SProtocol.readRequiredString(buffer, LegacyC2SProtocol.MAX_SEX_ID_BYTES);
            LegacyC2SProtocol.requireFullyRead(buffer);
            valid = true;
        } catch (RuntimeException exception) {
            LegacyC2SProtocol.warnMalformedOnce("SexSelection", exception);
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        ByteBufUtils.writeUTF8String(buffer, serializedSexId);
    }

    public static class Handler implements IMessageHandler<SexSelectionMessage, IMessage> {

        @Override
        public IMessage onMessage(SexSelectionMessage message, MessageContext context) {
            if (message.isValid()) {
                EntityPlayerMP player = context.getServerHandler().playerEntity;
                ModNetwork.enqueueSexSelection(player, message.getSerializedSexId());
            }
            return null;
        }
    }
}
