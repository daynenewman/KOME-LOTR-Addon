package kome.common.network;

import cpw.mods.fml.common.network.simpleimpl.*;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import kome.common.command.*;
import kome.common.data.*;
import net.minecraft.entity.player.EntityPlayerMP;

/** Single-company intent: no route, allowance, actor or conflict authority is accepted from the client. */
public final class KOMEPacketCompanyMovementRequest implements IMessage {
    public UUID session;
    public long sequence, previewSequence;
    public String companyId="",destination="",legacyFingerprint="",confirmationFingerprint="";
    public KOMEPacketCompanyMovementRequest() { }
    public KOMEPacketCompanyMovementRequest(UUID session,long sequence,long preview,String company,String destination,String legacy,String guard) {
        this.session=session;this.sequence=sequence;previewSequence=preview;companyId=company;this.destination=destination;
        legacyFingerprint=legacy;confirmationFingerprint=guard;validate();
    }
    private void validate() {
        if(session==null||sequence<=0||previewSequence<=0)throw new IllegalArgumentException("Invalid movement correlation");
        KOMECompanyDestinationWire.id(companyId,false);KOMECompanyDestinationWire.tile(destination,false);
        KOMECompanyDestinationWire.check(legacyFingerprint,64);KOMECompanyDestinationWire.check(confirmationFingerprint,64);
        KOMECompanyDestinationWire.fingerprint(legacyFingerprint,false);KOMECompanyDestinationWire.fingerprint(confirmationFingerprint,false);
    }
    @Override public void fromBytes(ByteBuf b) {
        KOMECompanyDestinationWire.size(b);session=new UUID(b.readLong(),b.readLong());sequence=b.readLong();previewSequence=b.readLong();
        companyId=KOMECompanyDestinationWire.text(b,64);destination=KOMECompanyDestinationWire.text(b,24);
        legacyFingerprint=KOMECompanyDestinationWire.text(b,64);confirmationFingerprint=KOMECompanyDestinationWire.text(b,64);
        validate();KOMEPopulationWire.requireFullyRead(b);
    }
    @Override public void toBytes(ByteBuf b) {
        validate();KOMECompanyDestinationWire.atomic(b,out->{
            out.writeLong(session.getMostSignificantBits());out.writeLong(session.getLeastSignificantBits());out.writeLong(sequence);out.writeLong(previewSequence);
            KOMECompanyDestinationWire.text(out,companyId,64);KOMECompanyDestinationWire.text(out,destination,24);
            KOMECompanyDestinationWire.text(out,legacyFingerprint,64);KOMECompanyDestinationWire.text(out,confirmationFingerprint,64);
        });
    }
    public static final class Handler implements IMessageHandler<KOMEPacketCompanyMovementRequest,IMessage> {
        @Override public IMessage onMessage(KOMEPacketCompanyMovementRequest m,MessageContext context) {
            m.validate();KOMECompanyMovementResult result;
            if(!KOMEPacketHandler.MOVEMENT_REPLAY_GUARD.acquire(context.getServerHandler(),m.sequence))
                result=KOMECompanyMovementResult.denied(m.session,m.sequence,m.previewSequence,m.companyId,m.destination,
                    KOMECompanyMovementResult.Outcome.REJECTED,"REPLAY_REJECTED","This movement request was already consumed. Refresh the preview.");
            else try {
                EntityPlayerMP actor=context.getServerHandler().playerEntity;
                result=KOMECompanyMapMovementSubmission.submit(KOMECompanyMovementService.INSTANCE,KOMEWorldData.get(actor.worldObj),actor,
                    m.session,m.sequence,m.previewSequence,m.companyId,m.destination,m.legacyFingerprint,m.confirmationFingerprint);
            } catch(RuntimeException error) {
                cpw.mods.fml.common.FMLLog.warning("KOME map movement submission failed: %s",error.toString());
                result=KOMECompanyMovementResult.denied(m.session,m.sequence,m.previewSequence,m.companyId,m.destination,
                    KOMECompanyMovementResult.Outcome.SERVER_ERROR,"SERVER_ERROR","Movement outcome could not be confirmed. Refresh before placing another order.");
            }
            return new KOMEPacketCompanyMovementResult(result);
        }
    }
}
