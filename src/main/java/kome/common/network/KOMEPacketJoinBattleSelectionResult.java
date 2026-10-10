package kome.common.network;

import cpw.mods.fml.common.network.simpleimpl.*;
import io.netty.buffer.ByteBuf;
import kome.common.KOMEAddon;
import kome.common.data.*;

/** Server-authoritative physical Join Battle entry result. */
public final class KOMEPacketJoinBattleSelectionResult implements IMessage {
    public enum Status { DEPLOYED, ENTRY_PENDING, REJECTED }
    public Status status=Status.REJECTED;
    public KOMEJoinBattleService.Reason reason=KOMEJoinBattleService.Reason.INVALID_REQUEST;
    public String message=KOMEJoinBattleText.forReason(reason);
    public String receiptId="";
    public KOMEPacketJoinBattleViewResponse current=new KOMEPacketJoinBattleViewResponse();
    public KOMEPacketJoinBattleSelectionResult() { }
    public static KOMEPacketJoinBattleSelectionResult from(KOMEJoinBattleEntryService.Result value,
            net.minecraft.entity.player.EntityPlayerMP player){
        KOMEPacketJoinBattleSelectionResult result=new KOMEPacketJoinBattleSelectionResult();
        result.status=value.status==KOMEJoinBattleEntryService.Status.DEPLOYED?Status.DEPLOYED:
            value.status==KOMEJoinBattleEntryService.Status.ENTRY_PENDING?Status.ENTRY_PENDING:Status.REJECTED;
        result.reason=value.reason;result.message=value.message;result.receiptId=value.receiptId;
        result.current=value.status==KOMEJoinBattleEntryService.Status.ENTRY_PENDING
            ?KOMEPacketJoinBattleViewResponse.from(value.current,value.actionToken)
            :KOMEPacketJoinBattleViewResponse.forPlayer(value.current,
                kome.common.KOMEReflection.getEntityUUID(player),KOMEWorldData.get(player.worldObj));
        return result;
    }
    static KOMEPacketJoinBattleSelectionResult invalid(KOMEJoinBattleService.Projection current,
            net.minecraft.entity.player.EntityPlayerMP player){
        KOMEPacketJoinBattleSelectionResult result=new KOMEPacketJoinBattleSelectionResult();
        result.current=KOMEPacketJoinBattleViewResponse.forPlayer(current,
            kome.common.KOMEReflection.getEntityUUID(player),KOMEWorldData.get(player.worldObj));return result;
    }
    @Override public void fromBytes(ByteBuf buf){
        try{status=Status.valueOf(KOMEJoinBattleWire.text(buf,32,"selection status"));}
        catch(RuntimeException invalid){throw new IllegalArgumentException("Invalid Join Battle selection status",invalid);}
        reason=KOMEJoinBattleWire.reason(KOMEJoinBattleWire.text(buf,64,"reason"));
        message=KOMEJoinBattleWire.text(buf,256,"message");
        receiptId=KOMEJoinBattleWire.text(buf,KOMEJoinBattleWire.MAX_RECEIPT_LENGTH,"receipt");
        current=new KOMEPacketJoinBattleViewResponse();current.fromBytes(buf);
    }
    @Override public void toBytes(ByteBuf buf){KOMEPopulationWire.writePacket(buf,out->{
        KOMEJoinBattleWire.write(out,status.name(),32,"selection status");
        KOMEJoinBattleWire.write(out,reason.name(),64,"reason");KOMEJoinBattleWire.write(out,message,256,"message");
        KOMEJoinBattleWire.write(out,receiptId,KOMEJoinBattleWire.MAX_RECEIPT_LENGTH,"receipt");current.toBytes(out);
    });}
    public static final class Handler implements IMessageHandler<KOMEPacketJoinBattleSelectionResult,IMessage>{
        @Override public IMessage onMessage(KOMEPacketJoinBattleSelectionResult message,MessageContext context){
            final KOMEPacketJoinBattleSelectionResult copy=KOMEPopulationWire.copyForPublication(message,KOMEPacketJoinBattleSelectionResult::new);
            KOMEAddon.proxy.enqueueClientTask(()->KOMEAddon.proxy.displayJoinBattleSelectionResult(copy));return null;
        }
    }
}
