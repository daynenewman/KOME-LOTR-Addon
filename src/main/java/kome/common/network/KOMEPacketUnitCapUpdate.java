package kome.common.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import kome.common.KOMEReflection;
import kome.common.data.KOMEHiredUnitRecord;
import kome.common.data.KOMEUnitLevelCapHooks;
import kome.common.data.KOMEWorldData;
import lotr.common.entity.npc.LOTREntityNPC;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

public class KOMEPacketUnitCapUpdate implements IMessage {
    public int entityId;
    public int cap;

    public KOMEPacketUnitCapUpdate() {
    }

    public KOMEPacketUnitCapUpdate(int entityId, int cap) {
        this.entityId = entityId;
        this.cap = cap;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        entityId = buf.readInt();
        cap = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeInt(cap);
    }

    public static class Handler implements IMessageHandler<KOMEPacketUnitCapUpdate, IMessage> {
        @Override
        public IMessage onMessage(KOMEPacketUnitCapUpdate message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            World world = KOMEReflection.getWorld(player);
            Entity entity = world.getEntityByID(message.entityId);
            if (!(entity instanceof LOTREntityNPC) || !((LOTREntityNPC) entity).hiredNPCInfo.isActive) {
                player.addChatMessage(new ChatComponentText("That hired unit is no longer loaded."));
                return null;
            }
            LOTREntityNPC npc = (LOTREntityNPC) entity;
            KOMEWorldData data = KOMEWorldData.get(world);
            KOMEHiredUnitRecord record = data.hiredUnits.get(KOMEReflection.getEntityUUID(npc));
            if (record == null) {
                return null;
            }
            if (!canEditUnit(player, record)) {
                player.addChatMessage(new ChatComponentText("You can only change caps for your own hired units."));
                KOMEPacketHandler.network.sendTo(new KOMEPacketUnitCapSync(message.entityId, Math.max(0, record.levelCap)), player);
                return null;
            }
            record.levelCap = Math.max(0, message.cap);
            data.markDirty();
            KOMEUnitLevelCapHooks.enforceCap(npc);
            KOMEPacketHandler.network.sendTo(new KOMEPacketUnitCapSync(message.entityId, record.levelCap), player);
            return null;
        }

        private boolean canEditUnit(EntityPlayerMP player, KOMEHiredUnitRecord record) {
            if (player.canCommandSenderUseCommand(2, "population")) {
                return true;
            }
            return record.owner != null && record.owner.equals(KOMEReflection.getEntityUUID(player));
        }
    }
}
