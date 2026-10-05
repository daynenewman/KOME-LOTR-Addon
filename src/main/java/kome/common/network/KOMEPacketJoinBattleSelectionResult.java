package kome.common.network;

import cpw.mods.fml.common.network.simpleimpl.*;
import io.netty.buffer.ByteBuf;
import kome.common.KOMEAddon;
import kome.common.data.*;

/** Readiness only: READY_FOR_DEPLOYMENT never claims or performs physical deployment. */
public final class KOMEPacketJoinBattleSelectionResult implements IMessage {
    public enum Status { READY_FOR_DEPLOYMENT, REJECTED }
    public Status status=Status.REJECTED;
    public KOMEJoinBattleService.Reason reason=KOMEJoinBattleService.Reason.INVALID_REQUEST;
    public String message=KOMEJoinBattleText.forReason(reason);
    public KOMEPacketJoinBattleViewResponse current=new KOMEPacketJoinBattleViewResponse();
    public KOMEPacketJoinBattleSelectionResult() { }
    public static KOMEPacketJoinBattleSelectionResult from(KOMEJoinBattleService.SelectionResult value){
        KOMEPacketJoinBattleSelectionResult result=new KOMEPacketJoinBattleSelectionResult();
        result.status=value.isAllowed()?Status.READY_FOR_DEPLOYMENT:Status.REJECTED; result.reason=value.reason;
        result.message=value.isAllowed()?"Selection valid. Physical deployment is not implemented yet.":KOMEJoinBattleText.forReason(value.reason);
        result.current=KOMEPacketJoinBattleViewResponse.from(value.current); return result;
    }
    static KOMEPacketJoinBattleSelectionResult invalid(KOMEJoinBattleService.Projection current){
        KOMEPacketJoinBattleSelectionResult result=new KOMEPacketJoinBattleSelectionResult();
        result.current=KOMEPacketJoinBattleViewResponse.from(current);return result;
    }
    @Override public void fromBytes(ByteBuf buf){
        try{status=Status.valueOf(KOMEJoinBattleWire.text(buf,32,"selection status"));}
        catch(RuntimeException invalid){throw new IllegalArgumentException("Invalid Join Battle selection status",invalid);}
        reason=KOMEJoinBattleWire.reason(KOMEJoinBattleWire.text(buf,64,"reason"));
        message=KOMEJoinBattleWire.text(buf,256,"message"); current=new KOMEPacketJoinBattleViewResponse();current.fromBytes(buf);
    }
    @Override public void toBytes(ByteBuf buf){KOMEPopulationWire.writePacket(buf,out->{
        KOMEJoinBattleWire.write(out,status.name(),32,"selection status");
        KOMEJoinBattleWire.write(out,reason.name(),64,"reason");KOMEJoinBattleWire.write(out,message,256,"message");current.toBytes(out);
    });}
    public static final class Handler implements IMessageHandler<KOMEPacketJoinBattleSelectionResult,IMessage>{
        @Override public IMessage onMessage(KOMEPacketJoinBattleSelectionResult message,MessageContext context){
            final KOMEPacketJoinBattleSelectionResult copy=KOMEPopulationWire.copyForPublication(message,KOMEPacketJoinBattleSelectionResult::new);
            KOMEAddon.proxy.enqueueClientTask(()->KOMEAddon.proxy.displayJoinBattleSelectionResult(copy));return null;
        }
    }
}
