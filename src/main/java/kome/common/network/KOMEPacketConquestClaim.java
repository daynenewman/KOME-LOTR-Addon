package kome.common.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import kome.common.KOMEReflection;
import kome.common.data.KOMEAlliance;
import kome.common.data.KOMEConquestTile;
import kome.common.data.KOMEWorldData;
import lotr.common.LOTRLevelData;
import lotr.common.fac.LOTRFaction;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;

public class KOMEPacketConquestClaim implements IMessage {
    public String tileId;

    public KOMEPacketConquestClaim() {
    }

    public KOMEPacketConquestClaim(String tileId) {
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

    public static class Handler implements IMessageHandler<KOMEPacketConquestClaim, IMessage> {
        @Override
        public IMessage onMessage(KOMEPacketConquestClaim message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            KOMEWorldData data = KOMEWorldData.get(KOMEReflection.getWorld(player));
            String pledge = KOMEAlliance.normalizeFactionKey(getPlayerFaction(data, player));
            if (pledge.length() == 0) {
                player.addChatMessage(new ChatComponentText("You must be pledged to a faction to claim conquest tiles."));
                return null;
            }
            String tileId = KOMEConquestTile.normalizeId(message.tileId);
            if (tileId.isEmpty() || !KOMEConquestTile.isCanonicalTileId(tileId)) {
                player.addChatMessage(new ChatComponentText("Invalid conquest tile."));
                return null;
            }
            KOMEConquestTile tile = data.getConquestTile(tileId);
            data.claimTile(tile, pledge, KOMEReflection.getTotalWorldTime(KOMEReflection.getWorld(player)), KOMEReflection.getEntityUUID(player), player.getCommandSenderName());
            data.ensureDefaultArrivalPoint(tile);
            data.markDirty();
            data.syncConquestTiles();
            KOMEPacketConquestOpenCapture.sendTileCommand(player, tileId);
            player.addChatMessage(new ChatComponentText("Claimed conquest tile " + tileId + " for " + KOMEAlliance.displayFactionName(pledge)));
            return null;
        }

        private String getPlayerFaction(KOMEWorldData data, EntityPlayerMP player) {
            LOTRFaction pledge = LOTRLevelData.getData(player).getPledgeFaction();
            if (pledge != null) {
                return KOMEAlliance.normalizeFactionKey(pledge.codeName());
            }
            return data.getPlayerFactionKey(KOMEReflection.getEntityUUID(player));
        }
    }
}
