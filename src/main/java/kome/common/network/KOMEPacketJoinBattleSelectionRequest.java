package kome.common.network;

import cpw.mods.fml.common.network.simpleimpl.*;
import io.netty.buffer.ByteBuf;
import kome.common.data.*;
import net.minecraft.entity.player.EntityPlayerMP;

/** Exact optimistic selection request; intentionally carries no faction or placement authority. */
public final class KOMEPacketJoinBattleSelectionRequest implements IMessage {
    public String tileId="", conflictId="", companyId="", actionToken=""; public long conflictRevision;
    public KOMEPacketJoinBattleSelectionRequest() { }
    public KOMEPacketJoinBattleSelectionRequest(String tile,String conflict,long revision,String company,String token){
        tileId=tile;conflictId=conflict;conflictRevision=revision;companyId=company;actionToken=token;
    }
    @Override public void fromBytes(ByteBuf buf){
        tileId=KOMEJoinBattleWire.text(buf,KOMEJoinBattleWire.MAX_TILE_LENGTH,"tile");
        conflictId=KOMEJoinBattleWire.text(buf,KOMEJoinBattleWire.MAX_CONFLICT_LENGTH,"conflict");
        conflictRevision=buf.readLong(); if(conflictRevision<0)throw new IllegalArgumentException("Invalid Join Battle revision");
        companyId=KOMEJoinBattleWire.text(buf,KOMEJoinBattleWire.MAX_COMPANY_LENGTH,"company");
        actionToken=KOMEJoinBattleWire.text(buf,KOMEJoinBattleWire.MAX_ACTION_TOKEN_LENGTH,"action token");
        KOMEPopulationWire.requireFullyRead(buf);
    }
    @Override public void toBytes(ByteBuf buf){KOMEPopulationWire.writePacket(buf,out->{
        KOMEJoinBattleWire.write(out,tileId,KOMEJoinBattleWire.MAX_TILE_LENGTH,"tile");
        KOMEJoinBattleWire.write(out,conflictId,KOMEJoinBattleWire.MAX_CONFLICT_LENGTH,"conflict");
        if(conflictRevision<0)throw new IllegalArgumentException("Invalid Join Battle revision");out.writeLong(conflictRevision);
        KOMEJoinBattleWire.write(out,companyId,KOMEJoinBattleWire.MAX_COMPANY_LENGTH,"company");
        KOMEJoinBattleWire.write(out,actionToken,KOMEJoinBattleWire.MAX_ACTION_TOKEN_LENGTH,"action token");
    });}
    public static final class Handler implements IMessageHandler<KOMEPacketJoinBattleSelectionRequest,IMessage>{
        @Override public IMessage onMessage(KOMEPacketJoinBattleSelectionRequest message,MessageContext context){
            EntityPlayerMP player=context.getServerHandler().playerEntity;
            KOMEJoinBattleEntryService.Result result;
            try { result=KOMEJoinBattleEntryService.INSTANCE.enter(KOMEWorldData.get(player.worldObj),player,
                new KOMEJoinBattleEntryService.Request(message.tileId,message.conflictId,
                    message.conflictRevision,message.companyId,message.actionToken)); }
            catch(IllegalArgumentException invalid){
                return KOMEPacketJoinBattleSelectionResult.invalid(
                    KOMEJoinBattleService.INSTANCE.evaluate(KOMEWorldData.get(player.worldObj),player,message.tileId),player);
            }
            return KOMEPacketJoinBattleSelectionResult.from(result,player);
        }
    }
}
