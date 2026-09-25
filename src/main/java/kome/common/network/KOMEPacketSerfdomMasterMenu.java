package kome.common.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import kome.common.KOMEAddon;

/** Server-authored presentation state for the separate canonical Serfdom Master menu. */
public class KOMEPacketSerfdomMasterMenu implements IMessage {
    public int entityId, mode;
    public boolean canRequestDuty,hasActiveDuty;
    public String masterName, factionName, dutyStatus;
    public KOMEPacketSerfdomMasterMenu() { }
    public KOMEPacketSerfdomMasterMenu(int entityId,String masterName,String factionName,int mode,String dutyStatus,boolean canRequestDuty,boolean hasActiveDuty){this.entityId=entityId;this.masterName=masterName==null?"":masterName;this.factionName=factionName==null?"":factionName;this.mode=mode;this.dutyStatus=dutyStatus==null?"":dutyStatus;this.canRequestDuty=canRequestDuty;this.hasActiveDuty=hasActiveDuty;}
    public void fromBytes(ByteBuf buf){entityId=buf.readInt();masterName=ByteBufUtils.readUTF8String(buf);factionName=ByteBufUtils.readUTF8String(buf);mode=buf.readByte();dutyStatus=ByteBufUtils.readUTF8String(buf);canRequestDuty=buf.readBoolean();hasActiveDuty=buf.readBoolean();}
    public void toBytes(ByteBuf buf){buf.writeInt(entityId);ByteBufUtils.writeUTF8String(buf,masterName);ByteBufUtils.writeUTF8String(buf,factionName);buf.writeByte(mode);ByteBufUtils.writeUTF8String(buf,dutyStatus);buf.writeBoolean(canRequestDuty);buf.writeBoolean(hasActiveDuty);}
    public static class Handler implements IMessageHandler<KOMEPacketSerfdomMasterMenu,IMessage>{public IMessage onMessage(KOMEPacketSerfdomMasterMenu message,MessageContext ctx){KOMEAddon.proxy.displaySerfdomMasterMenu(message.entityId,message.masterName,message.factionName,message.mode,message.dutyStatus,message.canRequestDuty,message.hasActiveDuty);return null;}}
}
