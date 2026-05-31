package kome.common.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import kome.common.KOMEAddon;

public class KOMEPacketLordMenu implements IMessage {
    public int entityId;
    public String lordName;
    public String factionName;
    public boolean currentLord;

    public KOMEPacketLordMenu() {
    }

    public KOMEPacketLordMenu(int entityId, String lordName, String factionName, boolean currentLord) {
        this.entityId = entityId;
        this.lordName = lordName == null ? "" : lordName;
        this.factionName = factionName == null ? "" : factionName;
        this.currentLord = currentLord;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        entityId = buf.readInt();
        lordName = ByteBufUtils.readUTF8String(buf);
        factionName = ByteBufUtils.readUTF8String(buf);
        currentLord = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(entityId);
        ByteBufUtils.writeUTF8String(buf, lordName);
        ByteBufUtils.writeUTF8String(buf, factionName);
        buf.writeBoolean(currentLord);
    }

    public static class Handler implements IMessageHandler<KOMEPacketLordMenu, IMessage> {
        @Override
        public IMessage onMessage(KOMEPacketLordMenu message, MessageContext ctx) {
            KOMEAddon.proxy.displayLordMenu(message.entityId, message.lordName, message.factionName, message.currentLord);
            return null;
        }
    }
}
