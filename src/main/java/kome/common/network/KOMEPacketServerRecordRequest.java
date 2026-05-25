package kome.common.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import kome.common.KOMEReflection;
import kome.common.data.KOMEServerRecordBuilder;
import net.minecraft.entity.player.EntityPlayerMP;

public class KOMEPacketServerRecordRequest implements IMessage {
    @Override
    public void fromBytes(ByteBuf buf) {
    }

    @Override
    public void toBytes(ByteBuf buf) {
    }

    public static class Handler implements IMessageHandler<KOMEPacketServerRecordRequest, IMessage> {
        @Override
        public IMessage onMessage(KOMEPacketServerRecordRequest message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            KOMEPacketHandler.network.sendTo(new KOMEPacketServerRecordData(KOMEServerRecordBuilder.build(KOMEReflection.getWorld(player))), player);
            return null;
        }
    }
}
