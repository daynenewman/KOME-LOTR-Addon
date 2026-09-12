package com.lotrcharactercreation.network;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class CharacterCreationBackMessage implements IMessage {

    private String serializedSourceStageId;
    private boolean valid;

    public CharacterCreationBackMessage() {}

    public CharacterCreationBackMessage(String serializedSourceStageId) {
        this.serializedSourceStageId = serializedSourceStageId;
        valid = LegacyC2SProtocol.isValidRequiredString(serializedSourceStageId, LegacyC2SProtocol.MAX_STAGE_ID_BYTES);
    }

    public String getSerializedSourceStageId() {
        return serializedSourceStageId;
    }

    public boolean isValid() {
        return valid;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        valid = false;
        serializedSourceStageId = null;
        try {
            serializedSourceStageId = LegacyC2SProtocol
                .readRequiredString(buffer, LegacyC2SProtocol.MAX_STAGE_ID_BYTES);
            LegacyC2SProtocol.requireFullyRead(buffer);
            valid = true;
        } catch (RuntimeException exception) {
            LegacyC2SProtocol.warnMalformedOnce("CharacterCreationBack", exception);
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        ByteBufUtils.writeUTF8String(buffer, serializedSourceStageId);
    }

    public static class Handler implements IMessageHandler<CharacterCreationBackMessage, IMessage> {

        @Override
        public IMessage onMessage(CharacterCreationBackMessage message, MessageContext context) {
            if (message.isValid()) {
                EntityPlayerMP player = context.getServerHandler().playerEntity;
                ModNetwork.enqueueCharacterCreationBack(player, message.getSerializedSourceStageId());
            }
            return null;
        }
    }
}
