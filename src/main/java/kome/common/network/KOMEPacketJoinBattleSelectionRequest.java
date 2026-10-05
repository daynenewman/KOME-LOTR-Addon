package kome.common.network;

import cpw.mods.fml.common.network.simpleimpl.*;
import io.netty.buffer.ByteBuf;
import kome.common.data.*;
import net.minecraft.entity.player.EntityPlayerMP;

/** Exact optimistic selection request; intentionally carries no faction or placement authority. */
public final class KOMEPacketJoinBattleSelectionRequest implements IMessage {
    public String tileId="", conflictId="", companyId=""; public long conflictRevision;
    public KOMEPacketJoinBattleSelectionRequest() { }
    public KOMEPacketJoinBattleSelectionRequest(String tile,String conflict,long revision,String company){
        tileId=tile;conflictId=conflict;conflictRevision=revision;companyId=company;
    }
    @Override public void fromBytes(ByteBuf buf){
        tileId=KOMEJoinBattleWire.text(buf,KOMEJoinBattleWire.MAX_TILE_LENGTH,"tile");
        conflictId=KOMEJoinBattleWire.text(buf,KOMEJoinBattleWire.MAX_CONFLICT_LENGTH,"conflict");
        conflictRevision=buf.readLong(); if(conflictRevision<0)throw new IllegalArgumentException("Invalid Join Battle revision");
        companyId=KOMEJoinBattleWire.text(buf,KOMEJoinBattleWire.MAX_COMPANY_LENGTH,"company");
        KOMEPopulationWire.requireFullyRead(buf);
    }
    @Override public void toBytes(ByteBuf buf){KOMEPopulationWire.writePacket(buf,out->{
        KOMEJoinBattleWire.write(out,tileId,KOMEJoinBattleWire.MAX_TILE_LENGTH,"tile");
        KOMEJoinBattleWire.write(out,conflictId,KOMEJoinBattleWire.MAX_CONFLICT_LENGTH,"conflict");
        if(conflictRevision<0)throw new IllegalArgumentException("Invalid Join Battle revision");out.writeLong(conflictRevision);
        KOMEJoinBattleWire.write(out,companyId,KOMEJoinBattleWire.MAX_COMPANY_LENGTH,"company");
    });}
    public static final class Handler implements IMessageHandler<KOMEPacketJoinBattleSelectionRequest,IMessage>{
        @Override public IMessage onMessage(KOMEPacketJoinBattleSelectionRequest message,MessageContext context){
            EntityPlayerMP player=context.getServerHandler().playerEntity;
            KOMEJoinBattleService.SelectionResult result;
            try { result=KOMEJoinBattleService.INSTANCE.validateSelectedCompany(KOMEWorldData.get(player.worldObj),player,
                message.tileId,KOMEConflictContracts.ExpectedConflict.at(message.conflictId,message.conflictRevision),message.companyId); }
            catch(IllegalArgumentException invalid){
                return KOMEPacketJoinBattleSelectionResult.invalid(
                    KOMEJoinBattleService.INSTANCE.evaluate(KOMEWorldData.get(player.worldObj),player,message.tileId));
            }
            return KOMEPacketJoinBattleSelectionResult.from(result);
        }
    }
}
