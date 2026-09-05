package com.lotrcharactercreation.network;

import com.lotrcharactercreation.LOTRCharacterCreation;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class ElfGrappleStateMessage implements IMessage {

    private int playerEntityId;
    private int targetEntityId;
    private boolean active;
    private boolean ready;

    public ElfGrappleStateMessage() {}

    public ElfGrappleStateMessage(int playerEntityId, int targetEntityId, boolean active, boolean ready) {
        this.playerEntityId = playerEntityId;
        this.targetEntityId = targetEntityId;
        this.active = active;
        this.ready = ready;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        playerEntityId = buffer.readInt();
        targetEntityId = buffer.readInt();
        active = buffer.readBoolean();
        ready = buffer.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(playerEntityId);
        buffer.writeInt(targetEntityId);
        buffer.writeBoolean(active);
        buffer.writeBoolean(ready);
    }

    public static class Handler implements IMessageHandler<ElfGrappleStateMessage, IMessage> {

        @Override
        public IMessage onMessage(ElfGrappleStateMessage message, MessageContext context) {
            LOTRCharacterCreation.proxy
                .handleElfGrappleState(message.playerEntityId, message.targetEntityId, message.active, message.ready);
            return null;
        }
    }
}
