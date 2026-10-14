package kome.common.network;

import java.util.UUID;
import io.netty.buffer.ByteBuf;
import cpw.mods.fml.common.network.simpleimpl.*;
import kome.common.command.KOMECompanyRallyService;
import kome.common.data.*;

/** No company IDs, routes, eligibility flags or player Join authority are accepted. */
public final class KOMEPacketCompanyRallyRequest implements IMessage {
    public enum Action { OPEN, CONFIRM, CANCEL }
    public UUID session=new UUID(0,0),token=new UUID(0,0);
    public String destination="";
    public Action action=Action.OPEN;
    private long receivedNanos;
    public KOMEPacketCompanyRallyRequest(){ }
    public KOMEPacketCompanyRallyRequest(Action action,UUID session,UUID token,String destination){
        this.action=action;this.session=session;this.token=token;this.destination=destination;validate();
    }
    private void validate(){
        if(action==null||session==null||token==null||session.equals(new UUID(0,0)))throw new IllegalArgumentException("Invalid Rally intent");
        KOMECompanyDestinationWire.tile(destination,false);
        if(action==Action.OPEN&&!token.equals(new UUID(0,0))||action==Action.CONFIRM&&token.equals(new UUID(0,0)))throw new IllegalArgumentException("Invalid Rally evidence");
    }
    public void fromBytes(ByteBuf b){
        KOMECompanyDestinationWire.size(b);action=KOMECompanyDestinationWire.enumeration(b,Action.class);
        session=new UUID(b.readLong(),b.readLong());token=new UUID(b.readLong(),b.readLong());destination=KOMECompanyDestinationWire.text(b,24);
        validate();KOMEPopulationWire.requireFullyRead(b);receivedNanos=System.nanoTime();
    }
    public void toBytes(ByteBuf b){validate();KOMECompanyDestinationWire.atomic(b,out->{
        KOMECompanyDestinationWire.text(out,action.name(),16);out.writeLong(session.getMostSignificantBits());out.writeLong(session.getLeastSignificantBits());
        out.writeLong(token.getMostSignificantBits());out.writeLong(token.getLeastSignificantBits());KOMECompanyDestinationWire.text(out,destination,24);
    });}
    public static final class Handler implements IMessageHandler<KOMEPacketCompanyRallyRequest,IMessage>{
        public IMessage onMessage(KOMEPacketCompanyRallyRequest m,MessageContext ctx){
            m.validate();KOMEPacketHandler.Requester requester=KOMEPacketHandler.captureRequester(ctx);
            if(requester==null||!requester.isCurrent())return null;
            if(m.action==Action.CANCEL){KOMEPacketHandler.RALLY.cancel(ctx.getServerHandler(),m.session,m.token);return null;}
            long nowNanos=System.nanoTime();
            KOMECompanyDestinationRateLimit.Result admission=KOMEPacketHandler.DESTINATION_RATE_LIMIT.acquire(ctx.getServerHandler(),
                m.receivedNanos==0?nowNanos:m.receivedNanos,nowNanos);
            if(!admission.accepted)return new KOMEPacketCompanyRallyResponse(new KOMECompanyRallySnapshot(m.session,new UUID(0,0),m.destination,
                KOMECompanyRallySnapshot.Phase.ERROR,0,0,0,0,0,0,"Rally queries are busy. Refresh shortly.",null));
            KOMECompanyRallySnapshot response=m.action==Action.OPEN?
                KOMEPacketHandler.RALLY.open(ctx.getServerHandler(),KOMEWorldData.get(requester.player.worldObj),requester.player,m.session,m.destination,
                    requester::isCurrent,snapshot->{if(requester.isCurrent())KOMEPacketHandler.network.sendTo(new KOMEPacketCompanyRallyResponse(snapshot),requester.player);},System.currentTimeMillis()):
                KOMEPacketHandler.RALLY.confirm(ctx.getServerHandler(),requester.player,m.session,m.token,m.destination,System.currentTimeMillis());
            return response==null?null:new KOMEPacketCompanyRallyResponse(response);
        }
    }
}