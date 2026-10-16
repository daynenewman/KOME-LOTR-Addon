package kome.common.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import kome.common.KOMEAddon;
import kome.common.data.KOMEFormalRetreatService;

/** Client-visible result of the server-derived formal-retreat operation. */
public final class KOMEPacketJoinBattleRetreatResult implements IMessage {
    public java.util.UUID correlation=new java.util.UUID(0,0);
    public KOMEFormalRetreatService.Code code=KOMEFormalRetreatService.Code.NOT_DEPLOYED;
    public String tileId="",conflictId="",message="";

    public static KOMEPacketJoinBattleRetreatResult from(KOMEFormalRetreatService.Result value){
        KOMEPacketJoinBattleRetreatResult result=new KOMEPacketJoinBattleRetreatResult();
        result.code=value.code;result.tileId=value.tileId;result.conflictId=value.conflictId;
        result.message=value.message;return result;
    }
    @Override public void fromBytes(ByteBuf buf){
        correlation=new java.util.UUID(buf.readLong(),buf.readLong());
        try{code=KOMEFormalRetreatService.Code.valueOf(KOMEJoinBattleWire.text(buf,40,"retreat result"));}
        catch(RuntimeException invalid){throw new IllegalArgumentException("Invalid formal retreat result",invalid);}
        tileId=KOMEJoinBattleWire.text(buf,KOMEJoinBattleWire.MAX_TILE_LENGTH,"retreat tile");
        conflictId=KOMEJoinBattleWire.text(buf,KOMEJoinBattleWire.MAX_CONFLICT_LENGTH,"retreat conflict");
        message=KOMEJoinBattleWire.text(buf,256,"retreat message");
        KOMEPopulationWire.requireFullyRead(buf);
    }
    @Override public void toBytes(ByteBuf buf){KOMEPopulationWire.writePacket(buf,out->{
        out.writeLong(correlation.getMostSignificantBits());out.writeLong(correlation.getLeastSignificantBits());
        KOMEJoinBattleWire.write(out,code.name(),40,"retreat result");
        KOMEJoinBattleWire.write(out,tileId,KOMEJoinBattleWire.MAX_TILE_LENGTH,"retreat tile");
        KOMEJoinBattleWire.write(out,conflictId,KOMEJoinBattleWire.MAX_CONFLICT_LENGTH,"retreat conflict");
        KOMEJoinBattleWire.write(out,message,256,"retreat message");
    });}
    public static final class Handler implements IMessageHandler<KOMEPacketJoinBattleRetreatResult,IMessage>{
        @Override public IMessage onMessage(KOMEPacketJoinBattleRetreatResult message,MessageContext context){
            final KOMEPacketJoinBattleRetreatResult copy=KOMEPopulationWire.copyForPublication(
                message,KOMEPacketJoinBattleRetreatResult::new);
            final Object connection=context.getClientHandler();
            try { KOMEAddon.proxy.enqueueClientTask(()->KOMEAddon.proxy.acceptJoinBattleRetreatResult(copy,connection)); }
            catch(java.util.concurrent.RejectedExecutionException closed) { /* Disconnected publication. */ }
            return null;
        }
    }
}
