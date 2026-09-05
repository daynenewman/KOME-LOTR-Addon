package com.lotrcharactercreation.network;

import com.lotrcharactercreation.LOTRCharacterCreation;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class DwarfTraitStateMessage implements IMessage {

    private boolean active;
    private float stamina;
    private int feast;
    private boolean exhausted;

    public DwarfTraitStateMessage() {}

    public DwarfTraitStateMessage(boolean active, float stamina, int feast, boolean exhausted) {
        this.active = active;
        this.stamina = stamina;
        this.feast = feast;
        this.exhausted = exhausted;
    }

    public boolean isActive() {
        return active;
    }

    public float getStamina() {
        return stamina;
    }

    public int getFeast() {
        return feast;
    }

    public boolean isExhausted() {
        return exhausted;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        active = buffer.readBoolean();
        stamina = buffer.readFloat();
        feast = buffer.readUnsignedByte();
        exhausted = buffer.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeBoolean(active);
        buffer.writeFloat(stamina);
        buffer.writeByte(feast);
        buffer.writeBoolean(exhausted);
    }

    public static class Handler implements IMessageHandler<DwarfTraitStateMessage, IMessage> {

        @Override
        public IMessage onMessage(DwarfTraitStateMessage message, MessageContext context) {
            LOTRCharacterCreation.proxy.handleDwarfTraitState(
                message.isActive(),
                message.getStamina(),
                message.getFeast(),
                message.isExhausted());
            return null;
        }
    }
}
