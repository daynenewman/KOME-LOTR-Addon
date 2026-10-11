package kome.common.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import kome.common.data.KOMEFormalRetreatService;
import kome.common.data.KOMEWorldData;
import net.minecraft.entity.player.EntityPlayerMP;

/** Server-authoritative retreat request; the client may identify only an exact conflict. */
public final class KOMEPacketJoinBattleRetreatRequest implements IMessage {
    public String conflictId="";
    public KOMEPacketJoinBattleRetreatRequest() { }
    public KOMEPacketJoinBattleRetreatRequest(String conflictId){
        this.conflictId=conflictId==null?"":conflictId.trim();
    }
    @Override public void fromBytes(ByteBuf buf) {
        conflictId=KOMEJoinBattleWire.text(buf,KOMEJoinBattleWire.MAX_CONFLICT_LENGTH,
            "retreat conflict");KOMEPopulationWire.requireFullyRead(buf);
    }
    @Override public void toBytes(ByteBuf buf) {
        KOMEJoinBattleWire.write(buf,conflictId,KOMEJoinBattleWire.MAX_CONFLICT_LENGTH,
            "retreat conflict");
    }

    public static final class Handler implements IMessageHandler<KOMEPacketJoinBattleRetreatRequest,IMessage> {
        @Override public IMessage onMessage(KOMEPacketJoinBattleRetreatRequest message,
                MessageContext context) {
            EntityPlayerMP player=context.getServerHandler().playerEntity;
            return KOMEPacketJoinBattleRetreatResult.from(
                KOMEFormalRetreatService.INSTANCE.retreat(
                    KOMEWorldData.get(player.worldObj),player,message.conflictId,
                    System.currentTimeMillis()));
        }
    }
}
