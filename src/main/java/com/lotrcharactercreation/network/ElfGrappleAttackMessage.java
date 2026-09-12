package com.lotrcharactercreation.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class ElfGrappleAttackMessage implements IMessage {

    private boolean valid;

    boolean isValid() {
        return valid;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        valid = !buffer.isReadable();
        if (!valid) {
            LegacyC2SProtocol.warnMalformedOnce(
                "ElfGrappleAttack",
                new IllegalArgumentException("grapple attack packet contains trailing data"));
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {}

    public static class Handler implements IMessageHandler<ElfGrappleAttackMessage, IMessage> {

        @Override
        public IMessage onMessage(ElfGrappleAttackMessage message, MessageContext context) {
            if (message.isValid()) {
                ModNetwork.enqueueElfGrappleAttack(context.getServerHandler().playerEntity);
            }
            return null;
        }
    }
}
