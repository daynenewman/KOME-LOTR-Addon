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

public class KOMEPacketConquestTransfer implements IMessage {
    public static final String OFFER = "offer";
    public static final String ACCEPT = "accept";
    public static final String CANCEL = "cancel";

    public String tileId;
    public String targetFaction;
    public String action = OFFER;

    public KOMEPacketConquestTransfer() {
    }

    public KOMEPacketConquestTransfer(String tileId, String targetFaction) {
        this(tileId, targetFaction, OFFER);
    }

    public KOMEPacketConquestTransfer(String tileId, String targetFaction, String action) {
        this.tileId = tileId;
        this.targetFaction = targetFaction;
        this.action = action;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        tileId = ByteBufUtils.readUTF8String(buf);
        targetFaction = ByteBufUtils.readUTF8String(buf);
        action = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, tileId);
        ByteBufUtils.writeUTF8String(buf, targetFaction);
        ByteBufUtils.writeUTF8String(buf, action);
    }

    public static class Handler implements IMessageHandler<KOMEPacketConquestTransfer, IMessage> {
        @Override
        public IMessage onMessage(KOMEPacketConquestTransfer message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            KOMEWorldData data = KOMEWorldData.get(KOMEReflection.getWorld(player));
            String pledge = KOMEAlliance.normalizeFactionKey(getPlayerFaction(data, player));
            if (pledge.length() == 0) {
                player.addChatMessage(new ChatComponentText("You must be pledged to a faction to transfer conquest tiles."));
                return null;
            }
            String tileId = KOMEConquestTile.normalizeId(message.tileId);
            if (tileId.isEmpty() || !KOMEConquestTile.isCanonicalTileId(tileId)) {
                player.addChatMessage(new ChatComponentText("Invalid conquest tile."));
                return null;
            }
            KOMEConquestTile tile = data.getConquestTile(tileId);
            if (!tile.isClaimed()) {
                player.addChatMessage(new ChatComponentText("Tile " + tileId + " is unclaimed."));
                return null;
            }
            String rulingFaction = tile.currentRulingFaction();
            if (ACCEPT.equals(message.action)) {
                acceptTransfer(player, pledge, data, tile);
                return null;
            }
            if (!data.isFactionKing(rulingFaction, KOMEReflection.getEntityUUID(player))) {
                player.addChatMessage(new ChatComponentText("Only the owning faction's king can offer this conquest tile."));
                return null;
            }
            if (CANCEL.equals(message.action)) {
                tile.clearPendingTransfer();
                data.markDirty();
                data.syncConquestTiles();
                KOMEPacketConquestOpenCapture.sendTileCommand(player, tileId);
                player.addChatMessage(new ChatComponentText("Cancelled pending transfer for conquest tile " + tileId + "."));
                return null;
            }
            LOTRFaction target = LOTRFaction.forName(message.targetFaction);
            if (target == null || !target.isPlayableAlignmentFaction()) {
                player.addChatMessage(new ChatComponentText("Unknown target faction."));
                return null;
            }
            String targetFaction = KOMEAlliance.normalizeFactionKey(target.codeName());
            if (pledge.equals(targetFaction)) {
                player.addChatMessage(new ChatComponentText("That tile is already owned by your faction."));
                return null;
            }
            if (!data.hasFactionKing(rulingFaction) || !data.hasFactionKing(targetFaction)) {
                player.addChatMessage(new ChatComponentText("Tile trades require real player kings for both factions."));
                return null;
            }
            tile.proposeTransfer(rulingFaction, targetFaction);
            data.markDirty();
            data.syncConquestTiles();
            KOMEPacketConquestOpenCapture.sendTileCommand(player, tileId);
            player.addChatMessage(new ChatComponentText("Offered conquest tile " + tileId + " to " + target.factionName() + ". Their king must accept."));
            return null;
        }

        private void acceptTransfer(EntityPlayerMP player, String pledge, KOMEWorldData data, KOMEConquestTile tile) {
            if (!tile.hasPendingTransfer()) {
                player.addChatMessage(new ChatComponentText("This conquest tile has no pending transfer."));
                return;
            }
            String targetFaction = KOMEAlliance.normalizeFactionKey(tile.pendingTransferToFaction);
            if (!pledge.equals(targetFaction)) {
                player.addChatMessage(new ChatComponentText("This conquest tile is not being offered to your faction."));
                return;
            }
            if (!data.isFactionKing(targetFaction, KOMEReflection.getEntityUUID(player))) {
                player.addChatMessage(new ChatComponentText("Only your faction's king can accept this conquest tile."));
                return;
            }
            if (!data.hasFactionKing(tile.pendingTransferFromFaction) || !data.hasFactionKing(targetFaction)) {
                player.addChatMessage(new ChatComponentText("Tile trades require real player kings for both factions."));
                return;
            }
            String tileId = tile.id;
            data.claimTile(tile, targetFaction, KOMEReflection.getTotalWorldTime(KOMEReflection.getWorld(player)), KOMEReflection.getEntityUUID(player), player.getCommandSenderName());
            data.ensureDefaultArrivalPoint(tile);
            data.syncConquestTiles();
            KOMEPacketConquestOpenCapture.sendTileCommand(player, tileId);
            player.addChatMessage(new ChatComponentText("Accepted conquest tile " + tileId + " for " + KOMEAlliance.displayFactionName(pledge) + "."));
        }

        private String getPlayerFaction(KOMEWorldData data, EntityPlayerMP player) {
            LOTRFaction pledge = LOTRLevelData.getData(player).getPledgeFaction();
            return pledge == null ? "" : KOMEAlliance.normalizeFactionKey(pledge.codeName());
        }
    }
}
