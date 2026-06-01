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
    public int offensivePop;
    public int defensivePop;
    public int mountedPop;
    public int groundPop;
    public int incomingPop;
    public int outgoingPop;
    public long incomingEtaMillis;

    public KOMEPacketConquestCaptureGui() {
    }

    public KOMEPacketConquestCaptureGui(String tileId, String ownerFaction, String pendingFromFaction, String pendingToFaction) {
        this(tileId, ownerFaction, pendingFromFaction, pendingToFaction, 0, 0, 0, 0, 0, 0, 0L);
    }

    public KOMEPacketConquestCaptureGui(String tileId, String ownerFaction, String pendingFromFaction, String pendingToFaction, int offensivePop, int defensivePop, int mountedPop, int groundPop, int incomingPop, int outgoingPop, long incomingEtaMillis) {
        this.tileId = tileId;
        this.ownerFaction = ownerFaction;
        this.pendingFromFaction = pendingFromFaction;
        this.pendingToFaction = pendingToFaction;
        this.offensivePop = offensivePop;
        this.defensivePop = defensivePop;
        this.mountedPop = mountedPop;
        this.groundPop = groundPop;
        this.incomingPop = incomingPop;
        this.outgoingPop = outgoingPop;
        this.incomingEtaMillis = incomingEtaMillis;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        tileId = ByteBufUtils.readUTF8String(buf);
        ownerFaction = ByteBufUtils.readUTF8String(buf);
        pendingFromFaction = ByteBufUtils.readUTF8String(buf);
        pendingToFaction = ByteBufUtils.readUTF8String(buf);
        offensivePop = buf.readInt();
        defensivePop = buf.readInt();
        mountedPop = buf.readInt();
        groundPop = buf.readInt();
        incomingPop = buf.readInt();
        outgoingPop = buf.readInt();
        incomingEtaMillis = buf.readLong();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, tileId);
        ByteBufUtils.writeUTF8String(buf, ownerFaction);
        ByteBufUtils.writeUTF8String(buf, pendingFromFaction);
        ByteBufUtils.writeUTF8String(buf, pendingToFaction);
        buf.writeInt(offensivePop);
        buf.writeInt(defensivePop);
        buf.writeInt(mountedPop);
        buf.writeInt(groundPop);
        buf.writeInt(incomingPop);
        buf.writeInt(outgoingPop);
        buf.writeLong(incomingEtaMillis);
    }

    public static class Handler implements IMessageHandler<KOMEPacketConquestCaptureGui, IMessage> {
        @Override
        public IMessage onMessage(KOMEPacketConquestCaptureGui message, MessageContext ctx) {
            KOMEAddon.proxy.displayConquestCaptureGui(message.tileId, message.ownerFaction, message.pendingFromFaction, message.pendingToFaction, message.offensivePop, message.defensivePop, message.mountedPop, message.groundPop, message.incomingPop, message.outgoingPop, message.incomingEtaMillis);
            return null;
        }
    }
}
