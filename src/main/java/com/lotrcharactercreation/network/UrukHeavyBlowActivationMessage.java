package com.lotrcharactercreation.network;

import com.lotrcharactercreation.LOTRCharacterCreation;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** Owner-only notification that the server consumed Rage for an empowered melee hit. */
public class UrukHeavyBlowActivationMessage implements IMessage {

    private long activationId;

    public UrukHeavyBlowActivationMessage() {}

    public UrukHeavyBlowActivationMessage(long activationId) {
        this.activationId = activationId;
    }

    public long getActivationId() {
        return activationId;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        activationId = buffer.readLong();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeLong(activationId);
    }

    public static class Handler implements IMessageHandler<UrukHeavyBlowActivationMessage, IMessage> {

        @Override
        public IMessage onMessage(UrukHeavyBlowActivationMessage message, MessageContext context) {
            LOTRCharacterCreation.proxy.handleUrukHeavyBlowActivation(message.getActivationId());
            return null;
        }
    }
}
