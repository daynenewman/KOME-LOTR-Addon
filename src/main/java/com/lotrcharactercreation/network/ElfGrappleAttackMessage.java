package com.lotrcharactercreation.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class ElfGrappleAttackMessage implements IMessage {

    @Override
    public void fromBytes(ByteBuf buffer) {}

    @Override
    public void toBytes(ByteBuf buffer) {}

    public static class Handler implements IMessageHandler<ElfGrappleAttackMessage, IMessage> {

        @Override
        public IMessage onMessage(ElfGrappleAttackMessage message, MessageContext context) {
            ModNetwork.enqueueElfGrappleAttack(context.getServerHandler().playerEntity);
            return null;
        }
    }
}
