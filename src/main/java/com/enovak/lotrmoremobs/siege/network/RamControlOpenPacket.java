package com.enovak.lotrmoremobs.siege.network;

import com.enovak.lotrmoremobs.Main;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class RamControlOpenPacket implements IMessage {

    private int dimensionId;
    private int entityId;

    public RamControlOpenPacket() {
    }

    public RamControlOpenPacket(int dimensionId, int entityId) {
        this.dimensionId = dimensionId;
        this.entityId = entityId;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        dimensionId = buffer.readInt();
        entityId = buffer.readInt();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(dimensionId);
        buffer.writeInt(entityId);
    }

    public int getDimensionId() {
        return dimensionId;
    }

    public int getEntityId() {
        return entityId;
    }

    public static class Handler implements IMessageHandler<
            RamControlOpenPacket,
            IMessage> {

        @Override
        public IMessage onMessage(
                RamControlOpenPacket message,
                MessageContext context
        ) {
            Main.proxy.handleRamControlOpen(message);
            return null;
        }
    }
}
