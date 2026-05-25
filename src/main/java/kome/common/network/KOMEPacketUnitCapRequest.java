package kome.common.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import kome.common.KOMEReflection;
import kome.common.data.KOMEHiredUnitRecord;
import kome.common.data.KOMEWorldData;
import lotr.common.entity.npc.LOTREntityNPC;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

public class KOMEPacketUnitCapRequest implements IMessage {
    public int entityId;

    public KOMEPacketUnitCapRequest() {
    }

    public KOMEPacketUnitCapRequest(int entityId) {
        this.entityId = entityId;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        entityId = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(entityId);
    }

    public static class Handler implements IMessageHandler<KOMEPacketUnitCapRequest, IMessage> {
        @Override
        public IMessage onMessage(KOMEPacketUnitCapRequest message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            World world = KOMEReflection.getWorld(player);
            Entity entity = world.getEntityByID(message.entityId);
            int cap = 0;
            if (entity instanceof LOTREntityNPC) {
                KOMEHiredUnitRecord record = KOMEWorldData.get(world).hiredUnits.get(KOMEReflection.getEntityUUID(entity));
                cap = record == null ? 0 : Math.max(0, record.levelCap);
            }
            KOMEPacketHandler.network.sendTo(new KOMEPacketUnitCapSync(message.entityId, cap), player);
            return null;
        }
    }
}
