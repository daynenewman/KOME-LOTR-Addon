package kome.common.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import kome.common.KOMEReflection;
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
            LOTRFaction pledge = LOTRLevelData.getData(player).getPledgeFaction();
            if (pledge == null) {
                player.addChatMessage(new ChatComponentText("You must be pledged to a faction to transfer conquest tiles."));
                return null;
            }
            String tileId = KOMEConquestTile.normalizeId(message.tileId);
            if (tileId.isEmpty() || !KOMEConquestTile.isCanonicalTileId(tileId)) {
                player.addChatMessage(new ChatComponentText("Invalid conquest tile."));
                return null;
            }
            KOMEWorldData data = KOMEWorldData.get(KOMEReflection.getWorld(player));
            KOMEConquestTile tile = data.getConquestTile(tileId);
            if (!tile.isClaimed()) {
                player.addChatMessage(new ChatComponentText("Tile " + tileId + " is unclaimed."));
                return null;
            }
            if (ACCEPT.equals(message.action)) {
                acceptTransfer(player, pledge, data, tile);
                return null;
            }
            if (!data.isFactionKing(tile.ownerFaction, KOMEReflection.getEntityUUID(player))) {
                player.addChatMessage(new ChatComponentText("Only the owning faction's king can offer this conquest tile."));
                return null;
            }
            if (CANCEL.equals(message.action)) {
                tile.clearPendingTransfer();
                data.markDirty();
                data.syncConquestTiles();
                player.addChatMessage(new ChatComponentText("Cancelled pending transfer for conquest tile " + tileId + "."));
                return null;
            }
            LOTRFaction target = LOTRFaction.forName(message.targetFaction);
            if (target == null || !target.isPlayableAlignmentFaction()) {
                player.addChatMessage(new ChatComponentText("Unknown target faction."));
                return null;
            }
            if (pledge.codeName().equals(target.codeName())) {
                player.addChatMessage(new ChatComponentText("That tile is already owned by your faction."));
                return null;
            }
            if (!data.hasFactionKing(tile.ownerFaction) || !data.hasFactionKing(target.codeName())) {
                player.addChatMessage(new ChatComponentText("Tile trades require real player kings for both factions."));
                return null;
            }
            tile.proposeTransfer(tile.ownerFaction, target.codeName());
            data.markDirty();
            data.syncConquestTiles();
            player.addChatMessage(new ChatComponentText("Offered conquest tile " + tileId + " to " + target.factionName() + ". Their king must accept."));
            return null;
        }

        private void acceptTransfer(EntityPlayerMP player, LOTRFaction pledge, KOMEWorldData data, KOMEConquestTile tile) {
            if (!tile.hasPendingTransfer()) {
                player.addChatMessage(new ChatComponentText("This conquest tile has no pending transfer."));
                return;
            }
            if (!pledge.codeName().equals(tile.pendingTransferToFaction)) {
                player.addChatMessage(new ChatComponentText("This conquest tile is not being offered to your faction."));
                return;
            }
            if (!data.isFactionKing(tile.pendingTransferToFaction, KOMEReflection.getEntityUUID(player))) {
                player.addChatMessage(new ChatComponentText("Only your faction's king can accept this conquest tile."));
                return;
            }
            if (!data.hasFactionKing(tile.pendingTransferFromFaction) || !data.hasFactionKing(tile.pendingTransferToFaction)) {
                player.addChatMessage(new ChatComponentText("Tile trades require real player kings for both factions."));
                return;
            }
            String tileId = tile.id;
            tile.claim(tile.pendingTransferToFaction, KOMEReflection.getTotalWorldTime(KOMEReflection.getWorld(player)));
            data.markDirty();
            data.syncConquestTiles();
            player.addChatMessage(new ChatComponentText("Accepted conquest tile " + tileId + " for " + pledge.factionName() + "."));
        }
    }
}
