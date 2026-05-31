package kome.common.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import kome.common.KOMEReflection;
import kome.common.data.KOMEPlayerProgression;
import kome.common.data.KOMEProgressionLords;
import kome.common.data.KOMEWorldData;
import lotr.common.entity.npc.LOTRHireableBase;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;

public class KOMEPacketLordAction implements IMessage {
    public static final int PLEDGE = 0;
    public static final int OFFERINGS = 1;
    public int entityId;
    public int action;

    public KOMEPacketLordAction() {
    }

    public KOMEPacketLordAction(int entityId, int action) {
        this.entityId = entityId;
        this.action = action;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        entityId = buf.readInt();
        action = buf.readByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeByte(action);
    }

    public static class Handler implements IMessageHandler<KOMEPacketLordAction, IMessage> {
        @Override
        public IMessage onMessage(KOMEPacketLordAction message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            Entity entity = KOMEReflection.getWorld(player).getEntityByID(message.entityId);
            if (!(entity instanceof LOTRHireableBase) || !KOMEProgressionLords.isPledgeLord((LOTRHireableBase) entity) || player.getDistanceSqToEntity(entity) > 64.0D) {
                player.addChatMessage(new ChatComponentText("That lord is no longer close enough."));
                return null;
            }
            KOMEWorldData data = KOMEWorldData.get(KOMEReflection.getWorld(player));
            KOMEPlayerProgression progression = data.getProgression(KOMEReflection.getEntityUUID(player));
            if (message.action == OFFERINGS) {
                if (!KOMEProgressionLords.isPledgedLord(entity, progression)) {
                    player.addChatMessage(new ChatComponentText("You can only open offerings for your pledged lord."));
                    return null;
                }
                KOMEProgressionLords.openOfferings(player);
            } else {
                KOMEProgressionLords.pledgeToLord(player, (LOTRHireableBase) entity);
            }
            return null;
        }
    }
}
