package kome.common.network;

import cpw.mods.fml.common.network.simpleimpl.*;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import kome.common.KOMEAddon;
import kome.common.data.KOMECompanyMovementResult;
import kome.common.data.KOMECompanyMovementResult.Outcome;

public final class KOMEPacketCompanyMovementResult implements IMessage {
    private KOMECompanyMovementResult result;
    public KOMEPacketCompanyMovementResult() { }
    public KOMEPacketCompanyMovementResult(KOMECompanyMovementResult result) {this.result=result;}
    public KOMECompanyMovementResult result() {return result;}
    private static void validate(KOMECompanyMovementResult r) {
        if(r==null||r.sequence<=0||r.previewSequence<=0||r.completedSteps<0||r.distance<0||r.completedSteps>r.distance)
            throw new IllegalArgumentException("Invalid movement result");
        KOMECompanyDestinationWire.id(r.companyId,false);KOMECompanyDestinationWire.tile(r.destination,false);
        KOMECompanyDestinationWire.id(r.orderId,true);KOMECompanyDestinationWire.tile(r.currentTile,true);
        KOMECompanyDestinationWire.check(r.orderStatus,64);KOMECompanyDestinationWire.check(r.code,64);KOMECompanyDestinationWire.check(r.detail,512);
        if(r.accepted()&&(r.orderId.isEmpty()||r.orderStatus.isEmpty()||r.currentTile.isEmpty()||!r.code.isEmpty()||!r.detail.isEmpty())
                ||!r.accepted()&&(r.code.isEmpty()||!r.orderId.isEmpty()||!r.orderStatus.isEmpty()||!r.currentTile.isEmpty()||r.distance!=0||r.completedSteps!=0)
                ||r.outcome==Outcome.ARRIVED&&(!("arrived".equals(r.orderStatus)||"conflict_held".equals(r.orderStatus)||"access_halted".equals(r.orderStatus))
                    ||!r.destination.equals(r.currentTile)||r.distance==0||r.completedSteps!=r.distance)
                ||r.outcome==Outcome.ORDER_ACCEPTED&&"arrived".equals(r.orderStatus))
            throw new IllegalArgumentException("Inconsistent movement result");
    }
    @Override public void fromBytes(ByteBuf b) {
        KOMECompanyDestinationWire.size(b);UUID session=new UUID(b.readLong(),b.readLong());long seq=b.readLong(),preview=b.readLong();
        String company=KOMECompanyDestinationWire.text(b,64),destination=KOMECompanyDestinationWire.text(b,24);
        Outcome outcome=KOMECompanyDestinationWire.enumeration(b,Outcome.class);
        String order=KOMECompanyDestinationWire.text(b,64),status=KOMECompanyDestinationWire.text(b,64),current=KOMECompanyDestinationWire.text(b,24);
        int steps=b.readInt(),distance=b.readInt();String code=KOMECompanyDestinationWire.text(b,64),detail=KOMECompanyDestinationWire.text(b,512);
        KOMEPopulationWire.requireFullyRead(b);
        result=new KOMECompanyMovementResult(session,seq,preview,company,destination,outcome,order,status,current,steps,distance,code,detail);validate(result);
    }
    @Override public void toBytes(ByteBuf b) {
        KOMECompanyMovementResult r=result;validate(r);KOMECompanyDestinationWire.atomic(b,out->{
            out.writeLong(r.session.getMostSignificantBits());out.writeLong(r.session.getLeastSignificantBits());out.writeLong(r.sequence);out.writeLong(r.previewSequence);
            KOMECompanyDestinationWire.text(out,r.companyId,64);KOMECompanyDestinationWire.text(out,r.destination,24);KOMECompanyDestinationWire.text(out,r.outcome.name(),64);
            KOMECompanyDestinationWire.text(out,r.orderId,64);KOMECompanyDestinationWire.text(out,r.orderStatus,64);KOMECompanyDestinationWire.text(out,r.currentTile,24);
            out.writeInt(r.completedSteps);out.writeInt(r.distance);KOMECompanyDestinationWire.text(out,r.code,64);KOMECompanyDestinationWire.text(out,r.detail,512);
        });
    }
    public static final class Handler implements IMessageHandler<KOMEPacketCompanyMovementResult,IMessage> {
        @Override public IMessage onMessage(KOMEPacketCompanyMovementResult m,MessageContext context) {
            final KOMECompanyMovementResult copy=KOMEPopulationWire.copyForPublication(m,KOMEPacketCompanyMovementResult::new).result;
            final Object connection=context.getClientHandler();
            try {KOMEAddon.proxy.enqueueClientTask(()->KOMEAddon.proxy.acceptCompanyMovementResult(copy,connection));}
            catch(java.util.concurrent.RejectedExecutionException closed) { /* Lifecycle cannot revive a result. */ }
            return null;
        }
    }
}
