package kome.common.network;

import cpw.mods.fml.common.network.simpleimpl.*;
import io.netty.buffer.ByteBuf;
import java.util.*;
import kome.common.KOMEAddon;
import kome.common.data.*;
import static kome.common.data.KOMECompanyDestinationOptions.*;

public final class KOMEPacketCompanySourceResponse implements IMessage {
    private KOMECompanySourceOptions snapshot;
    public KOMEPacketCompanySourceResponse(){ }
    public KOMEPacketCompanySourceResponse(KOMECompanySourceOptions value){validate(value);snapshot=value;}
    public KOMECompanySourceOptions snapshot(){return snapshot;}
    private static void validate(KOMECompanySourceOptions p){
        if(p==null||p.session==null||p.sequence<1||p.result==null||p.rows.size()>MAX_ROWS)throw new IllegalArgumentException("Invalid source page");
        KOMECompanyDestinationWire.tile(p.source,false);KOMECompanyDestinationWire.id(p.next,true);
        KOMECompanyDestinationWire.check(p.token,36);if(!p.token.isEmpty())UUID.fromString(p.token);
        if(p.more&&(p.next.isEmpty()||p.token.isEmpty()))throw new IllegalArgumentException("Missing source continuation");
        if(p.retryAfterMillis < -1||p.retryAfterMillis>1000||p.result!=Result.RATE_LIMITED&&p.retryAfterMillis!=0)throw new IllegalArgumentException("Invalid source retry");
        Set<String> ids=new HashSet<String>();
        for(KOMECompanySourceOptions.Row r:p.rows){
            KOMECompanyDestinationWire.id(r.companyId,false);KOMECompanyDestinationWire.check(r.name,128);KOMECompanyDestinationWire.check(r.state,32);
            if(!ids.add(r.companyId)||r.units<1||r.units>MAX_MEMBERS||r.tilesPerDay<1||r.allowance<0||r.allowance>r.tilesPerDay
                    ||r.populationCenti < -1||r.populationCenti>(long)MAX_MEMBERS*Integer.MAX_VALUE*100)throw new IllegalArgumentException("Invalid source row");
        }
        if(!p.rows.isEmpty()&&!p.next.equals(p.rows.get(p.rows.size()-1).companyId))throw new IllegalArgumentException("Invalid source cursor");
    }
    public void toBytes(ByteBuf b){validate(snapshot);KOMECompanySourceOptions p=snapshot;KOMECompanyDestinationWire.atomic(b,out->{
        out.writeLong(p.session.getMostSignificantBits());out.writeLong(p.session.getLeastSignificantBits());out.writeLong(p.sequence);
        KOMECompanyDestinationWire.text(out,p.source,24);KOMECompanyDestinationWire.text(out,p.result.name(),64);
        KOMECompanyDestinationWire.text(out,p.token,36);KOMECompanyDestinationWire.text(out,p.next,64);out.writeBoolean(p.more);out.writeLong(p.retryAfterMillis);
        out.writeInt(p.rows.size());for(KOMECompanySourceOptions.Row r:p.rows){
            KOMECompanyDestinationWire.text(out,r.companyId,64);KOMECompanyDestinationWire.text(out,r.name,128);out.writeInt(r.units);out.writeLong(r.populationCenti);
            out.writeBoolean(r.mounted);out.writeInt(r.allowance);out.writeInt(r.tilesPerDay);KOMECompanyDestinationWire.text(out,r.state,32);out.writeBoolean(r.held);
        }
    });}
    public void fromBytes(ByteBuf b){
        KOMECompanyDestinationWire.size(b);UUID session=new UUID(b.readLong(),b.readLong());long seq=b.readLong();
        String source=KOMECompanyDestinationWire.text(b,24);Result result=KOMECompanyDestinationWire.enumeration(b,Result.class);
        String token=KOMECompanyDestinationWire.text(b,36),next=KOMECompanyDestinationWire.text(b,64);boolean more=b.readBoolean();long retry=b.readLong();
        int count=b.readInt();if(count<0||count>MAX_ROWS)throw new IllegalArgumentException("Oversized source page");
        List<KOMECompanySourceOptions.Row> rows=new ArrayList<KOMECompanySourceOptions.Row>();
        for(int i=0;i<count;i++)rows.add(new KOMECompanySourceOptions.Row(KOMECompanyDestinationWire.text(b,64),
            KOMECompanyDestinationWire.text(b,128),b.readInt(),b.readLong(),b.readBoolean(),b.readInt(),b.readInt(),KOMECompanyDestinationWire.text(b,32),b.readBoolean()));
        KOMEPopulationWire.requireFullyRead(b);snapshot=new KOMECompanySourceOptions(session,seq,source,result,rows,token,next,more,retry);validate(snapshot);
    }
    public static final class Handler implements IMessageHandler<KOMEPacketCompanySourceResponse,IMessage>{
        public IMessage onMessage(KOMEPacketCompanySourceResponse m,MessageContext ctx){
            final KOMECompanySourceOptions copy=KOMEPopulationWire.copyForPublication(m,KOMEPacketCompanySourceResponse::new).snapshot;
            final Object connection=ctx.getClientHandler();
            try{KOMEAddon.proxy.enqueueClientTask(()->KOMEAddon.proxy.acceptCompanySourceOptions(copy,connection));}
            catch(java.util.concurrent.RejectedExecutionException closed){ }
            return null;
        }
    }
}