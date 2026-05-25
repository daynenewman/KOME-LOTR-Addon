package kome.common.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import kome.client.KOMEUnitCapClientState;

public class KOMEPacketUnitCapSync implements IMessage {
    public int entityId;
    public int cap;

    public KOMEPacketUnitCapSync() {
    }

    public KOMEPacketUnitCapSync(int entityId, int cap) {
        this.entityId = entityId;
        this.cap = cap;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        entityId = buf.readInt();
        cap = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeInt(cap);
    }

    public static class Handler implements IMessageHandler<KOMEPacketUnitCapSync, IMessage> {
        @Override
        public IMessage onMessage(KOMEPacketUnitCapSync message, MessageContext ctx) {
            KOMEUnitCapClientState.setCap(message.entityId, message.cap);
            return null;
        }
    }
}
