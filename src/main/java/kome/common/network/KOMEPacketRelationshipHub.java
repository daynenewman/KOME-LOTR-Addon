package kome.common.network;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.*;
import io.netty.buffer.ByteBuf;
import kome.common.KOMEAddon;
/** Client presentation only; relationship authority remains on the server action packet. */
public class KOMEPacketRelationshipHub implements IMessage {
 public int entityId,relationship; public String name,faction;
 public KOMEPacketRelationshipHub(){}
 public KOMEPacketRelationshipHub(int id,int type,String n,String f){entityId=id;relationship=type;name=n==null?"":n;faction=f==null?"":f;}
 public void fromBytes(ByteBuf b){entityId=b.readInt();relationship=b.readByte();name=ByteBufUtils.readUTF8String(b);faction=ByteBufUtils.readUTF8String(b);}
 public void toBytes(ByteBuf b){b.writeInt(entityId);b.writeByte(relationship);ByteBufUtils.writeUTF8String(b,name);ByteBufUtils.writeUTF8String(b,faction);}
 public static class Handler implements IMessageHandler<KOMEPacketRelationshipHub,IMessage>{public IMessage onMessage(KOMEPacketRelationshipHub m,MessageContext c){KOMEAddon.proxy.displayRelationshipHub(m.entityId,m.relationship,m.name,m.faction);return null;}}
}
