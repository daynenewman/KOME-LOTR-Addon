package kome.common.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import kome.common.KOMEReflection;
import kome.common.data.KOMEConquestTile;
import kome.common.data.KOMEProgressionPermissions;
import kome.common.data.KOMEWorldData;
import net.minecraft.entity.player.EntityPlayerMP;

public class KOMEPacketConquestOpenCapture implements IMessage {
    public String tileId;

    public KOMEPacketConquestOpenCapture() {
    }

    public KOMEPacketConquestOpenCapture(String tileId) {
        this.tileId = tileId;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        tileId = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, tileId);
    }

    public static class Handler implements IMessageHandler<KOMEPacketConquestOpenCapture, IMessage> {
        @Override
        public IMessage onMessage(KOMEPacketConquestOpenCapture message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            String tileId = KOMEConquestTile.normalizeId(message.tileId);
            if (tileId.isEmpty() || !KOMEConquestTile.isCanonicalTileId(tileId)) {
                return null;
            }
            if (!KOMEProgressionPermissions.require(player, KOMEProgressionPermissions.TAKE_WAYPOINTS)) {
                return null;
            }
            KOMEConquestTile tile = KOMEWorldData.get(KOMEReflection.getWorld(player)).getConquestTile(tileId);
            KOMEPacketHandler.network.sendTo(new KOMEPacketConquestCaptureGui(tile.id, tile.ownerFaction, tile.ruler), player);
            return null;
        }
    }
}
