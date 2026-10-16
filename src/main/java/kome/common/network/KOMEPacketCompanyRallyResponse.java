package kome.common.network;

import java.util.UUID;
import io.netty.buffer.ByteBuf;
import cpw.mods.fml.common.network.simpleimpl.*;
import kome.common.KOMEAddon;
import kome.common.data.KOMECompanyRallySnapshot;
import static kome.common.data.KOMECompanyRallySnapshot.*;

public final class KOMEPacketCompanyRallyResponse implements IMessage {
    private KOMECompanyRallySnapshot snapshot;
    public KOMEPacketCompanyRallyResponse(){ }
    public KOMEPacketCompanyRallyResponse(KOMECompanyRallySnapshot snapshot){validate(snapshot);this.snapshot=snapshot;}
    public KOMECompanyRallySnapshot snapshot(){return snapshot;}
    private static void validate(KOMECompanyRallySnapshot s){
        if(s==null||s.session==null||s.token==null||s.phase==null||s.revision<0||s.total<0||s.total>MAX_CANDIDATES
                ||s.evaluated<0||s.evaluated>s.total||s.included<0||s.included>s.evaluated||s.processed<0||s.processed>s.total
                ||s.rallied<0||s.rallied>s.included||s.rallied>s.processed)throw new IllegalArgumentException("Invalid Rally counters");
        KOMECompanyDestinationWire.tile(s.destination,false);KOMECompanyDestinationWire.check(s.message,256);
        if(s.phase!=Phase.ERROR&&s.token.equals(new UUID(0,0)))throw new IllegalArgumentException("Missing Rally token");
        if((s.phase==Phase.READY||s.phase==Phase.EXECUTING||s.phase==Phase.DONE)&&s.evaluated!=s.total
                ||s.phase==Phase.DONE&&s.processed!=s.total)throw new IllegalArgumentException("Incomplete Rally phase");
        if(s.entry!=null){
            Entry e=s.entry;if(e.index<0||e.index>=s.total||e.company==null||e.outcome==null)throw new IllegalArgumentException("Invalid Rally row");
            KOMECompanyDestinationWire.validate(e.company); // shared projection validation
            KOMECompanyDestinationWire.check(e.faction,64);KOMECompanyDestinationWire.check(e.reason,256);
            if(!s.destination.equals(e.company.destinationTile))throw new IllegalArgumentException("Wrong Rally destination");
        }
    }
    public void toBytes(ByteBuf b){validate(snapshot);KOMECompanyRallySnapshot s=snapshot;KOMECompanyDestinationWire.atomic(b,out->{
        out.writeLong(s.session.getMostSignificantBits());out.writeLong(s.session.getLeastSignificantBits());out.writeLong(s.token.getMostSignificantBits());out.writeLong(s.token.getLeastSignificantBits());
        KOMECompanyDestinationWire.text(out,s.destination,24);KOMECompanyDestinationWire.text(out,s.phase.name(),16);
        out.writeInt(s.revision);out.writeInt(s.total);out.writeInt(s.evaluated);out.writeInt(s.included);out.writeInt(s.processed);out.writeInt(s.rallied);
        KOMECompanyDestinationWire.text(out,s.message,256);out.writeBoolean(s.entry!=null);
        if(s.entry!=null){
            Entry e=s.entry;out.writeInt(e.index);KOMECompanyDestinationWire.row(out,e.company);KOMECompanyDestinationWire.text(out,e.faction,64);
            out.writeBoolean(e.included);KOMECompanyDestinationWire.text(out,e.outcome.name(),16);KOMECompanyDestinationWire.text(out,e.reason,256);
        }
    });}
    public void fromBytes(ByteBuf b){
        KOMECompanyDestinationWire.size(b);UUID session=new UUID(b.readLong(),b.readLong()),token=new UUID(b.readLong(),b.readLong());
        String destination=KOMECompanyDestinationWire.text(b,24);Phase phase=KOMECompanyDestinationWire.enumeration(b,Phase.class);
        int revision=b.readInt(),total=b.readInt(),evaluated=b.readInt(),included=b.readInt(),processed=b.readInt(),rallied=b.readInt();
        String message=KOMECompanyDestinationWire.text(b,256);Entry entry=null;
        if(b.readBoolean())entry=new Entry(b.readInt(),KOMECompanyDestinationWire.row(b),KOMECompanyDestinationWire.text(b,64),
            b.readBoolean(),KOMECompanyDestinationWire.enumeration(b,Outcome.class),KOMECompanyDestinationWire.text(b,256));
        KOMEPopulationWire.requireFullyRead(b);
        snapshot=new KOMECompanyRallySnapshot(session,token,destination,phase,revision,total,evaluated,included,processed,rallied,message,entry);validate(snapshot);
    }
    public static final class Handler implements IMessageHandler<KOMEPacketCompanyRallyResponse,IMessage>{
        public IMessage onMessage(KOMEPacketCompanyRallyResponse m,MessageContext ctx){
            final KOMECompanyRallySnapshot copy=KOMEPopulationWire.copyForPublication(m,KOMEPacketCompanyRallyResponse::new).snapshot;
            final Object connection=ctx.getClientHandler();
            try{KOMEAddon.proxy.enqueueClientTask(()->KOMEAddon.proxy.acceptCompanyRally(copy,connection));}
            catch(java.util.concurrent.RejectedExecutionException closed){ }
            return null;
        }
    }
}