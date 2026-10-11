package kome.common.network;

import cpw.mods.fml.common.network.simpleimpl.*;
import io.netty.buffer.ByteBuf;
import kome.common.data.KOMECampaignHiredPresentation;

/** Requests native hire synchronization after the client has installed the spawned entity. */
public final class KOMEPacketCampaignHiredInfoRequest implements IMessage {
    public int dimension,entityId;
    public KOMEPacketCampaignHiredInfoRequest() { }
    public KOMEPacketCampaignHiredInfoRequest(int dimension,int entityId){this.dimension=dimension;this.entityId=entityId;}
    @Override public void toBytes(ByteBuf buffer){buffer.writeInt(dimension);buffer.writeInt(entityId);}
    @Override public void fromBytes(ByteBuf buffer){dimension=buffer.readInt();entityId=buffer.readInt();}
    public static final class Handler implements IMessageHandler<KOMEPacketCampaignHiredInfoRequest,IMessage> {
        @Override public IMessage onMessage(KOMEPacketCampaignHiredInfoRequest message,MessageContext context){
            KOMECampaignHiredPresentation.refresh(context.getServerHandler().playerEntity,message.dimension,message.entityId);
            return null;
        }
    }
}
