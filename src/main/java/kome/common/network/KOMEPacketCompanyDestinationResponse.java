package kome.common.network;

import cpw.mods.fml.common.network.simpleimpl.*;
import io.netty.buffer.*;
import java.util.*;
import kome.common.KOMEAddon;
import kome.common.data.KOMECompanyDestinationOptions;
import kome.common.data.KOMECompanyDestinationOptions.*;
import static kome.common.data.KOMECompanyDestinationOptions.*;

public final class KOMEPacketCompanyDestinationResponse implements IMessage {
    private KOMECompanyDestinationOptions snapshot;
    public KOMEPacketCompanyDestinationResponse() { }
    public KOMEPacketCompanyDestinationResponse(KOMECompanyDestinationOptions snapshot) {this.snapshot=Objects.requireNonNull(snapshot);}
    public KOMECompanyDestinationOptions snapshot() {return snapshot;}
    public static KOMEPacketCompanyDestinationResponse validated(KOMECompanyDestinationOptions snapshot) {
        return KOMEPopulationWire.copyForPublication(new KOMEPacketCompanyDestinationResponse(snapshot),KOMEPacketCompanyDestinationResponse::new);
    }
    /** Pack a server query prefix by encoded bytes as well as row count; cursor always names the last included row. */
    public static KOMEPacketCompanyDestinationResponse forQuery(KOMECompanyDestinationOptions page) {
        validate(page);
        for(int count=page.rows.size();count>=0;count--) {
            KOMECompanyDestinationOptions candidate=count==page.rows.size()?page:
                new KOMECompanyDestinationOptions(page.session,page.sequence,page.destination,page.result,
                    page.rows.subList(0,count),page.catalogueToken,count==0?"":page.rows.get(count-1).companyId,true);
            try {return validated(candidate);}
            catch(KOMECompanyDestinationWire.PacketLimitExceeded limit) {
                if(count<=1)throw limit; // A bounded single row must fit; never emit an unusable empty continuation.
            }
        }
        throw new IllegalStateException("Unreachable destination packing state");
    }
    @Override public void fromBytes(ByteBuf b) {
        KOMECompanyDestinationWire.size(b);UUID session=new UUID(b.readLong(),b.readLong());long sequence=b.readLong();
        String destination=KOMECompanyDestinationWire.text(b,24);
        Result result=KOMECompanyDestinationWire.enumeration(b,Result.class);
        String token=KOMECompanyDestinationWire.text(b,36),next=KOMECompanyDestinationWire.text(b,64);boolean more=b.readBoolean();long retry=b.readLong();
        int count=b.readInt();if(count<0||count>MAX_ROWS)throw new IllegalArgumentException("Oversized destination rows");
        List<Row> rows=new ArrayList<Row>();for(int i=0;i<count;i++)rows.add(KOMECompanyDestinationWire.row(b));
        KOMEPopulationWire.requireFullyRead(b);
        KOMECompanyDestinationOptions value=new KOMECompanyDestinationOptions(session,sequence,destination,result,rows,token,next,more,retry);
        validate(value);snapshot=value;
    }
    private static void validate(KOMECompanyDestinationOptions p) {
        if(p==null||p.sequence<=0||p.rows.size()>MAX_ROWS)throw new IllegalArgumentException("Invalid destination response");
        KOMECompanyDestinationWire.tile(p.destination,false);KOMECompanyDestinationWire.id(p.nextCompanyId,true);
        KOMECompanyDestinationWire.check(p.catalogueToken,36);
        if(!p.catalogueToken.isEmpty())UUID.fromString(p.catalogueToken);
        if(p.retryAfterMillis < -1 || p.retryAfterMillis > 1000 || p.result!=Result.RATE_LIMITED && p.retryAfterMillis!=0)
            throw new IllegalArgumentException("Invalid destination retry timing");
        if(p.hasMore&&(p.catalogueToken.isEmpty()||p.nextCompanyId.isEmpty()))throw new IllegalArgumentException("Missing destination continuation");
        Set<String> ids=new HashSet<String>();
        for(Row r:p.rows) {
            KOMECompanyDestinationWire.validate(r);
            if(!p.destination.equals(r.destinationTile)||!ids.add(r.companyId))throw new IllegalArgumentException("Inconsistent destination row");
        }
        if(!p.rows.isEmpty()&&!p.nextCompanyId.equals(p.rows.get(p.rows.size()-1).companyId))
            throw new IllegalArgumentException("Destination cursor must match last row");
    }
    @Override public void toBytes(ByteBuf b) {
        KOMECompanyDestinationOptions p=snapshot;validate(p);
        KOMECompanyDestinationWire.atomic(b,out->{
            out.writeLong(p.session.getMostSignificantBits());out.writeLong(p.session.getLeastSignificantBits());out.writeLong(p.sequence);
            KOMECompanyDestinationWire.text(out,p.destination,24);KOMECompanyDestinationWire.text(out,p.result.name(),64);
            KOMECompanyDestinationWire.text(out,p.catalogueToken,36);KOMECompanyDestinationWire.text(out,p.nextCompanyId,64);
            out.writeBoolean(p.hasMore);out.writeLong(p.retryAfterMillis);out.writeInt(p.rows.size());for(Row r:p.rows)KOMECompanyDestinationWire.row(out,r);
        });
    }
    public static final class Handler implements IMessageHandler<KOMEPacketCompanyDestinationResponse,IMessage> {
        @Override public IMessage onMessage(KOMEPacketCompanyDestinationResponse m,MessageContext context) {
            final KOMECompanyDestinationOptions copy=validated(m.snapshot).snapshot;
            final Object connection=context.getClientHandler();
            try {KOMEAddon.proxy.enqueueClientTask(()->KOMEAddon.proxy.acceptCompanyDestinationOptions(copy,connection));}
            catch(java.util.concurrent.RejectedExecutionException lateOrFull) { /* Closed lifecycle cannot revive a snapshot. */ }
            return null;
        }
    }
}
