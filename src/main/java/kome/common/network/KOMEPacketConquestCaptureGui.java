package kome.common.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import kome.common.KOMEAddon;

public class KOMEPacketConquestCaptureGui implements IMessage {
    public String tileId;
    public String ownerFaction;
    public String pendingFromFaction;
    public String pendingToFaction;

    public KOMEPacketConquestCaptureGui() {
    }

    public KOMEPacketConquestCaptureGui(String tileId, String ownerFaction, String pendingFromFaction, String pendingToFaction) {
        this.tileId = tileId;
        this.ownerFaction = ownerFaction;
        this.pendingFromFaction = pendingFromFaction;
        this.pendingToFaction = pendingToFaction;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        tileId = ByteBufUtils.readUTF8String(buf);
        ownerFaction = ByteBufUtils.readUTF8String(buf);
        pendingFromFaction = ByteBufUtils.readUTF8String(buf);
        pendingToFaction = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, tileId);
        ByteBufUtils.writeUTF8String(buf, ownerFaction);
        ByteBufUtils.writeUTF8String(buf, pendingFromFaction);
        ByteBufUtils.writeUTF8String(buf, pendingToFaction);
    }

    public static class Handler implements IMessageHandler<KOMEPacketConquestCaptureGui, IMessage> {
        @Override
        public IMessage onMessage(KOMEPacketConquestCaptureGui message, MessageContext ctx) {
            KOMEAddon.proxy.displayConquestCaptureGui(message.tileId, message.ownerFaction, message.pendingFromFaction, message.pendingToFaction);
            return null;
        }
    }
}
