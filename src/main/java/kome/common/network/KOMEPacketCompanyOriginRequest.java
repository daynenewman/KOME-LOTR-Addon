package kome.common.network;

import cpw.mods.fml.common.network.simpleimpl.*;
import io.netty.buffer.ByteBuf;
import java.util.*;
import kome.common.command.*;
import kome.common.data.*;
import net.minecraft.entity.player.EntityPlayerMP;

/** Two bounded read-only queries: source metadata or exact company/destination evidence. */
public final class KOMEPacketCompanyOriginRequest implements IMessage {
    public UUID session;
    public long sequence;
    public boolean targeted;
    public String source="",company="",destination="",after="",token="";
    private long receivedNanos;
    public KOMEPacketCompanyOriginRequest() { }
    public KOMEPacketCompanyOriginRequest(UUID session,long seq,String source,String after,String token){
        this.session=session;sequence=seq;this.source=source;this.after=after;this.token=token;validate();
    }
    public static KOMEPacketCompanyOriginRequest target(UUID session,long seq,String source,String company,String destination){
        KOMEPacketCompanyOriginRequest r=new KOMEPacketCompanyOriginRequest();r.session=session;r.sequence=seq;
        r.targeted=true;r.source=source;r.company=company;r.destination=destination;r.validate();return r;
    }
    private void validate(){
        if(session==null||sequence<1)throw new IllegalArgumentException("Invalid origin query correlation");
        KOMECompanyDestinationWire.tile(source,false);
        if(targeted){
            KOMECompanyDestinationWire.id(company,false);KOMECompanyDestinationWire.tile(destination,false);
            if(!after.isEmpty()||!token.isEmpty())throw new IllegalArgumentException("Targeted queries have no catalogue cursor");
        }else{
            if(!company.isEmpty()||!destination.isEmpty())throw new IllegalArgumentException("Source queries have no destination");
            KOMECompanyDestinationWire.id(after,true);KOMECompanyDestinationWire.check(token,36);
            if(after.isEmpty()!=token.isEmpty())throw new IllegalArgumentException("Incomplete source cursor");
            if(!token.isEmpty())UUID.fromString(token);
        }
    }
    public void fromBytes(ByteBuf b){
        KOMECompanyDestinationWire.size(b);session=new UUID(b.readLong(),b.readLong());sequence=b.readLong();targeted=b.readBoolean();
        source=KOMECompanyDestinationWire.text(b,24);company=KOMECompanyDestinationWire.text(b,64);destination=KOMECompanyDestinationWire.text(b,24);
        after=KOMECompanyDestinationWire.text(b,64);token=KOMECompanyDestinationWire.text(b,36);
        validate();KOMEPopulationWire.requireFullyRead(b);receivedNanos=System.nanoTime();
    }
    public void toBytes(ByteBuf b){validate();KOMECompanyDestinationWire.atomic(b,out->{
        out.writeLong(session.getMostSignificantBits());out.writeLong(session.getLeastSignificantBits());out.writeLong(sequence);out.writeBoolean(targeted);
        KOMECompanyDestinationWire.text(out,source,24);KOMECompanyDestinationWire.text(out,company,64);KOMECompanyDestinationWire.text(out,destination,24);
        KOMECompanyDestinationWire.text(out,after,64);KOMECompanyDestinationWire.text(out,token,36);
    });}
    public static final class Handler implements IMessageHandler<KOMEPacketCompanyOriginRequest,IMessage>{
        public IMessage onMessage(KOMEPacketCompanyOriginRequest m,MessageContext ctx){
            m.validate();EntityPlayerMP actor=ctx.getServerHandler().playerEntity;long now=System.nanoTime();
            KOMECompanyDestinationRateLimit.Result admission=KOMEPacketHandler.DESTINATION_RATE_LIMIT.acquire(ctx.getServerHandler(),m.receivedNanos==0?now:m.receivedNanos,now);
            if(m.targeted) {
                KOMECompanyDestinationOptions result=admission.accepted?
                    KOMECompanyOriginQuery.INSTANCE.target(KOMEWorldData.get(actor.worldObj),actor,m.session,m.sequence,m.source,m.company,m.destination):
                    new KOMECompanyDestinationOptions(m.session,m.sequence,m.destination,KOMECompanyDestinationOptions.Result.RATE_LIMITED,
                        Collections.<KOMECompanyDestinationOptions.Row>emptyList(),"","",false,admission.retryMillis);
                try{return KOMEPacketCompanyDestinationResponse.validated(result);}
                catch(IllegalArgumentException oversized){return new KOMEPacketCompanyDestinationResponse(
                    KOMECompanyDestinationQuery.error(m.session,m.sequence,m.destination,KOMECompanyDestinationOptions.Result.PROJECTION_LIMIT));}
            }
            KOMECompanySourceOptions result=admission.accepted?
                KOMECompanyOriginQuery.INSTANCE.source(KOMEWorldData.get(actor.worldObj),actor,m.session,m.sequence,m.source,m.after,m.token):
                new KOMECompanySourceOptions(m.session,m.sequence,m.source,KOMECompanyDestinationOptions.Result.RATE_LIMITED,
                    Collections.<KOMECompanySourceOptions.Row>emptyList(),"","",false,admission.retryMillis);
            try{return KOMEPopulationWire.copyForPublication(new KOMEPacketCompanySourceResponse(result),KOMEPacketCompanySourceResponse::new);}
            catch(IllegalArgumentException oversized){return new KOMEPacketCompanySourceResponse(
                new KOMECompanySourceOptions(m.session,m.sequence,m.source,KOMECompanyDestinationOptions.Result.PROJECTION_LIMIT,
                    Collections.<KOMECompanySourceOptions.Row>emptyList(),"","",false,0));}
        }
    }
}