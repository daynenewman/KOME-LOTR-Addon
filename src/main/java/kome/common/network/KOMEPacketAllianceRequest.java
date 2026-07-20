package kome.common.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import kome.common.KOMEReflection;
import kome.common.data.KOMEAllianceRecordBuilder;
import kome.common.data.KOMEWorldData;
import net.minecraft.entity.player.EntityPlayerMP;

public class KOMEPacketAllianceRequest implements IMessage {
    public boolean operatorView;

    public KOMEPacketAllianceRequest() {
    }

    public KOMEPacketAllianceRequest(boolean operatorView) {
        this.operatorView = operatorView;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        operatorView = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(operatorView);
    }

    public static class Handler implements IMessageHandler<KOMEPacketAllianceRequest, IMessage> {
        @Override
        public IMessage onMessage(KOMEPacketAllianceRequest message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            KOMEAllianceRecordBuilder.setOperatorView(player, message.operatorView);
            KOMEPacketHandler.network.sendTo(new KOMEPacketAllianceData(KOMEAllianceRecordBuilder.build(KOMEWorldData.get(KOMEReflection.getWorld(player)), player)), player);
            return null;
        }
    }
}
