package com.enovak.lotrmoremobs.siege.network;
import com.enovak.lotrmoremobs.Main;
import com.enovak.lotrmoremobs.siege.edit.*;
import cpw.mods.fml.common.network.simpleimpl.*;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
/** Bounded S2C edit-session status; no draft or source provenance is serialized. */
public final class GateEditSessionStatusPacket implements IMessage {
 private GateEditStatus status; private UUID token,gateUuid; private int dimension,x,y,z,revision; private boolean valid=true;
 public GateEditSessionStatusPacket(){} public GateEditSessionStatusPacket(GateEditStatus s, GateEditSession session){status=s;if(session!=null){token=session.getSessionToken();gateUuid=session.getGateUuid();dimension=session.getDimensionId();x=session.getControllerX();y=session.getControllerY();z=session.getControllerZ();revision=session.getBaseRevision();}}
 public void fromBytes(ByteBuf b){int code=b.readUnsignedByte(); if(code>=GateEditStatus.values().length){valid=false;return;}status=GateEditStatus.values()[code]; boolean opened=b.readBoolean(); if(opened){token=new UUID(b.readLong(),b.readLong());gateUuid=new UUID(b.readLong(),b.readLong());dimension=b.readInt();x=b.readInt();y=b.readInt();z=b.readInt();revision=b.readInt(); if(token.getMostSignificantBits()==0L&&token.getLeastSignificantBits()==0L)valid=false;}}
 public void toBytes(ByteBuf b){b.writeByte(status==null?GateEditStatus.GATE_UNAVAILABLE.ordinal():status.ordinal()); boolean opened=status==GateEditStatus.OPENED&&token!=null&&gateUuid!=null; b.writeBoolean(opened);if(opened){b.writeLong(token.getMostSignificantBits());b.writeLong(token.getLeastSignificantBits());b.writeLong(gateUuid.getMostSignificantBits());b.writeLong(gateUuid.getLeastSignificantBits());b.writeInt(dimension);b.writeInt(x);b.writeInt(y);b.writeInt(z);b.writeInt(revision);}}
 public GateEditStatus getStatus(){return status;} public UUID getToken(){return token;} public UUID getGateUuid(){return gateUuid;} public int getDimension(){return dimension;} public int getX(){return x;} public int getY(){return y;} public int getZ(){return z;} public int getRevision(){return revision;} public boolean isValid(){return valid&&status!=null;}
 public static final class Handler implements IMessageHandler<GateEditSessionStatusPacket,IMessage>{public IMessage onMessage(GateEditSessionStatusPacket m,MessageContext c){if(m.isValid())Main.proxy.handleGateEditSessionStatus(m);return null;}}
}
