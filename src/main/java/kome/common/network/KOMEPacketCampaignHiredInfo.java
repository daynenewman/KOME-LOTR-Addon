package kome.common.network;

import cpw.mods.fml.common.network.simpleimpl.*;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import kome.common.KOMEAddon;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.network.LOTRPacketHiredInfo;

/** Native hired-info payload, fenced to one physical entity and delivered after client spawn. */
public final class KOMEPacketCampaignHiredInfo implements IMessage {
    public int dimension, entityId;
    public UUID physicalId;
    public LOTRPacketHiredInfo nativeInfo;
    public KOMEPacketCampaignHiredInfo() { }
    public KOMEPacketCampaignHiredInfo(LOTREntityNPC npc) {
        dimension=npc.dimension; entityId=npc.getEntityId(); physicalId=npc.getUniqueID();
        nativeInfo=new LOTRPacketHiredInfo(entityId,npc.hiredNPCInfo.getHiringPlayerUUID(),
            npc.hiredNPCInfo.getTask(),npc.hiredNPCInfo.getSquadron(),npc.hiredNPCInfo.xpLevel);
    }
    @Override public void toBytes(ByteBuf buf) {
        buf.writeInt(dimension);buf.writeInt(entityId);
        buf.writeLong(physicalId.getMostSignificantBits());buf.writeLong(physicalId.getLeastSignificantBits());
        nativeInfo.toBytes(buf);
    }
    @Override public void fromBytes(ByteBuf buf) {
        dimension=buf.readInt();entityId=buf.readInt();physicalId=new UUID(buf.readLong(),buf.readLong());
        nativeInfo=new LOTRPacketHiredInfo();nativeInfo.fromBytes(buf);
    }
    public static final class Handler implements IMessageHandler<KOMEPacketCampaignHiredInfo,IMessage> {
        @Override public IMessage onMessage(KOMEPacketCampaignHiredInfo message,MessageContext context) {
            KOMEAddon.proxy.acceptCampaignHiredInfo(message,context.getClientHandler());return null;
        }
    }
}
