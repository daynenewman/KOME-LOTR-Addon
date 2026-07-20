package kome.common.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import kome.common.KOMEReflection;
import kome.common.data.KOMEAlliance;
import kome.common.data.KOMEPledgeReleaseService;
import kome.common.data.KOMEWorldData;
import net.minecraft.entity.player.EntityPlayerMP;

/** Requests only the sender's server-authoritative pledge-departure preview. */
public class KOMEPacketPledgeDepartureRequest implements IMessage {
    @Override public void fromBytes(ByteBuf buf) { }
    @Override public void toBytes(ByteBuf buf) { }

    public static class Handler implements IMessageHandler<KOMEPacketPledgeDepartureRequest, IMessage> {
        @Override
        public IMessage onMessage(KOMEPacketPledgeDepartureRequest message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            KOMEWorldData data = KOMEWorldData.get(KOMEReflection.getWorld(player));
            java.util.UUID id = KOMEReflection.getEntityUUID(player);
            String faction = KOMEAlliance.normalizeFactionKey(data.lastKnownPlayerFactions.get(id));
            if (faction.length() == 0) faction = data.getPlayerFactionKey(id);
            KOMEPledgeReleaseService.Preview preview = KOMEPledgeReleaseService.preview(data, id, faction);
            KOMEPacketHandler.network.sendTo(new KOMEPacketPledgeDepartureData(player.getCommandSenderName(), preview), player);
            return null;
        }
    }
}
