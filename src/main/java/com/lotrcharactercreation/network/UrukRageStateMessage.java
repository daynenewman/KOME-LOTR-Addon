package com.lotrcharactercreation.network;

import com.lotrcharactercreation.LOTRCharacterCreation;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class UrukRageStateMessage implements IMessage {

    private boolean active;
    private float rage;

    public UrukRageStateMessage() {}

    public UrukRageStateMessage(boolean active, float rage) {
        this.active = active;
        this.rage = rage;
    }

    public boolean isActive() {
        return active;
    }

    public float getRage() {
        return rage;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        active = buffer.readBoolean();
        rage = buffer.readFloat();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeBoolean(active);
        buffer.writeFloat(rage);
    }

    public static class Handler implements IMessageHandler<UrukRageStateMessage, IMessage> {

        @Override
        public IMessage onMessage(UrukRageStateMessage message, MessageContext context) {
            LOTRCharacterCreation.proxy.handleUrukRageState(message.isActive(), message.getRage());
            return null;
        }
    }
}
