package kome.common.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import kome.common.KOMEAddon;

public class KOMEPacketLordHighlight implements IMessage {
    public int entityId;
    public String lordName;
    public double x;
    public double y;
    public double z;

    public KOMEPacketLordHighlight() {
    }

    public KOMEPacketLordHighlight(int entityId, String lordName, double x, double y, double z) {
        this.entityId = entityId;
        this.lordName = lordName == null ? "" : lordName;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        entityId = buf.readInt();
        lordName = ByteBufUtils.readUTF8String(buf);
        x = buf.readDouble();
        y = buf.readDouble();
        z = buf.readDouble();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(entityId);
        ByteBufUtils.writeUTF8String(buf, lordName);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
    }

    public static class Handler implements IMessageHandler<KOMEPacketLordHighlight, IMessage> {
        @Override
        public IMessage onMessage(KOMEPacketLordHighlight message, MessageContext ctx) {
            KOMEAddon.proxy.highlightEntity(message.entityId, message.lordName, message.x, message.y, message.z);
            return null;
        }
    }
}
