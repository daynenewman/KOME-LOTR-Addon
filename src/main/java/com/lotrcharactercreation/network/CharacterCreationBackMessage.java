package com.lotrcharactercreation.network;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class CharacterCreationBackMessage implements IMessage {

    private String serializedSourceStageId;

    public CharacterCreationBackMessage() {}

    public CharacterCreationBackMessage(String serializedSourceStageId) {
        this.serializedSourceStageId = serializedSourceStageId;
    }

    public String getSerializedSourceStageId() {
        return serializedSourceStageId;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        serializedSourceStageId = ByteBufUtils.readUTF8String(buffer);
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        ByteBufUtils.writeUTF8String(buffer, serializedSourceStageId);
    }

    public static class Handler implements IMessageHandler<CharacterCreationBackMessage, IMessage> {

        @Override
        public IMessage onMessage(CharacterCreationBackMessage message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().playerEntity;
            ModNetwork.enqueueCharacterCreationBack(player, message.getSerializedSourceStageId());
            return null;
        }
    }
}
