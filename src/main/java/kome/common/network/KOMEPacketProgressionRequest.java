package kome.common.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import kome.common.KOMEReflection;
import kome.common.data.KOMEPlayerProgression;
import kome.common.data.KOMEProgressionAchievement;
import kome.common.data.KOMEWorldData;
import net.minecraft.entity.player.EntityPlayerMP;

import java.util.ArrayList;
import java.util.List;

public class KOMEPacketProgressionRequest implements IMessage {
    @Override
    public void fromBytes(ByteBuf buf) {
    }

    @Override
    public void toBytes(ByteBuf buf) {
    }

    public static class Handler implements IMessageHandler<KOMEPacketProgressionRequest, IMessage> {
        @Override
        public IMessage onMessage(KOMEPacketProgressionRequest message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            KOMEWorldData data = KOMEWorldData.get(KOMEReflection.getWorld(player));
            KOMEPlayerProgression progression = data.getProgression(KOMEReflection.getEntityUUID(player));
            List completed = new ArrayList();
            for (KOMEProgressionAchievement achievement : KOMEProgressionAchievement.ALL) {
                if (progression.isCompleted(achievement)) {
                    completed.add(achievement.id);
                }
            }
            KOMEPacketHandler.network.sendTo(new KOMEPacketProgressionData(player.getCommandSenderName(), completed), player);
            return null;
        }
    }
}
