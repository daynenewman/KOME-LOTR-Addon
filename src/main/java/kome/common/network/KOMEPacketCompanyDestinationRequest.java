package kome.common.network;

import cpw.mods.fml.common.network.simpleimpl.*;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import kome.common.command.KOMECompanyDestinationQuery;
import kome.common.data.*;
import net.minecraft.entity.player.EntityPlayerMP;

/** Immutable intent after decoding. Connection supplies actor identity; UUID is correlation only. */
public final class KOMEPacketCompanyDestinationRequest implements IMessage {
    public UUID session;
    public long sequence;
    public String destination="",afterCompanyId="",catalogueToken="";
    public int limit;
    // Captured at intake, never supplied by the client. Prevent queued floods from aging into fresh requests.
    private long receivedNanos;
    public KOMEPacketCompanyDestinationRequest() { }
    public KOMEPacketCompanyDestinationRequest(UUID session,long sequence,String destination,int limit,String after,String catalogue) {
        this.session=session;this.sequence=sequence;this.destination=destination;this.limit=limit;afterCompanyId=after;catalogueToken=catalogue;
        validate();
    }
    private void validate() {
        if(session==null||sequence<=0||limit<1||limit>KOMECompanyDestinationOptions.MAX_ROWS)
            throw new IllegalArgumentException("Invalid destination request");
        KOMECompanyDestinationWire.tile(destination,false);KOMECompanyDestinationWire.id(afterCompanyId,true);
        KOMECompanyDestinationWire.check(catalogueToken,36);
        if(!catalogueToken.isEmpty())UUID.fromString(catalogueToken);
        if(afterCompanyId.isEmpty()!=catalogueToken.isEmpty())throw new IllegalArgumentException("Incomplete destination cursor");
    }
    @Override public void fromBytes(ByteBuf b) {
        KOMECompanyDestinationWire.size(b);session=new UUID(b.readLong(),b.readLong());sequence=b.readLong();
        destination=KOMECompanyDestinationWire.text(b,24);limit=b.readInt();
        afterCompanyId=KOMECompanyDestinationWire.text(b,64);catalogueToken=KOMECompanyDestinationWire.text(b,36);
        validate();KOMEPopulationWire.requireFullyRead(b);receivedNanos=System.nanoTime();
    }
    @Override public void toBytes(ByteBuf b) {
        validate();KOMECompanyDestinationWire.atomic(b,out->{
            out.writeLong(session.getMostSignificantBits());out.writeLong(session.getLeastSignificantBits());out.writeLong(sequence);
            KOMECompanyDestinationWire.text(out,destination,24);out.writeInt(limit);
            KOMECompanyDestinationWire.text(out,afterCompanyId,64);KOMECompanyDestinationWire.text(out,catalogueToken,36);
        });
    }
    public static final class Handler implements IMessageHandler<KOMEPacketCompanyDestinationRequest,IMessage> {
        @Override public IMessage onMessage(KOMEPacketCompanyDestinationRequest m,MessageContext context) {
            m.validate();EntityPlayerMP actor=context.getServerHandler().playerEntity;
            long now=System.nanoTime();
            KOMECompanyDestinationRateLimit.Result admission=KOMEPacketHandler.DESTINATION_RATE_LIMIT.acquire(
                context.getServerHandler(),m.receivedNanos==0?now:m.receivedNanos,now);
            if(!admission.accepted)
                return new KOMEPacketCompanyDestinationResponse(new KOMECompanyDestinationOptions(m.session,m.sequence,m.destination,
                    KOMECompanyDestinationOptions.Result.RATE_LIMITED,java.util.Collections.<KOMECompanyDestinationOptions.Row>emptyList(),
                    "","",false,admission.retryMillis));
            KOMECompanyDestinationOptions page=KOMECompanyDestinationQuery.INSTANCE.query(KOMEWorldData.get(actor.worldObj),actor,
                m.session,m.sequence,m.destination,m.limit,m.afterCompanyId,m.catalogueToken);
            try { return KOMEPacketCompanyDestinationResponse.forQuery(page); }
            catch(IllegalArgumentException oversized) {
                return new KOMEPacketCompanyDestinationResponse(KOMECompanyDestinationQuery.error(m.session,m.sequence,m.destination,
                    KOMECompanyDestinationOptions.Result.PROJECTION_LIMIT));
            }
        }
    }
}
