package kome.common.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import kome.common.data.KOMEJoinBattleService;
import kome.common.data.KOMEWorldData;
import kome.common.KOMEReflection;
import net.minecraft.entity.player.EntityPlayerMP;

/** Client supplies only a tile; player identity and faction come from the connection. */
public final class KOMEPacketJoinBattleViewRequest implements IMessage {
    public String tileId = "";
    public java.util.UUID correlation = new java.util.UUID(0,0);

    public KOMEPacketJoinBattleViewRequest() { }
    public KOMEPacketJoinBattleViewRequest(String tileId) { this.tileId = tileId == null ? "" : tileId; }
    public KOMEPacketJoinBattleViewRequest(String tileId,java.util.UUID correlation) {this(tileId);this.correlation=java.util.Objects.requireNonNull(correlation);}

    @Override public void fromBytes(ByteBuf buf) {
        tileId = KOMEPopulationWire.readText(buf);
        correlation=new java.util.UUID(buf.readLong(),buf.readLong());
        KOMEJoinBattleWire.requireId(tileId, KOMEJoinBattleWire.MAX_TILE_LENGTH, "tile");
        KOMEPopulationWire.requireFullyRead(buf);
    }
    @Override public void toBytes(ByteBuf buf) {
        KOMEJoinBattleWire.requireId(tileId, KOMEJoinBattleWire.MAX_TILE_LENGTH, "tile");
        java.util.Objects.requireNonNull(correlation);
        KOMEPopulationWire.writePacket(buf, out -> {KOMEPopulationWire.writeText(out, tileId);out.writeLong(correlation.getMostSignificantBits());out.writeLong(correlation.getLeastSignificantBits());});
    }

    public static final class Handler implements IMessageHandler<KOMEPacketJoinBattleViewRequest, IMessage> {
        @Override public IMessage onMessage(KOMEPacketJoinBattleViewRequest message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().playerEntity;
            KOMEJoinBattleService.Projection projection=KOMEJoinBattleService.INSTANCE.evaluate(
                KOMEWorldData.get(player.worldObj), player, message.tileId);
            KOMEWorldData data=KOMEWorldData.get(player.worldObj);
            KOMEPacketJoinBattleViewResponse response=KOMEPacketJoinBattleViewResponse.forPlayer(projection,
                KOMEReflection.getEntityUUID(player),data);
            response.correlation=message.correlation;return response;
        }
    }
}
