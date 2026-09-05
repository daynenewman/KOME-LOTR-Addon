package com.lotrcharactercreation.network;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class CharacterFinalizationMessage implements IMessage {

    @Override
    public void fromBytes(ByteBuf buffer) {}

    @Override
    public void toBytes(ByteBuf buffer) {}

    public static class Handler implements IMessageHandler<CharacterFinalizationMessage, IMessage> {

        @Override
        public IMessage onMessage(CharacterFinalizationMessage message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().playerEntity;
            ModNetwork.enqueueCharacterFinalization(player);
            return null;
        }
    }
}
