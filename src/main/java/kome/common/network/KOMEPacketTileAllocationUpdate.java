package kome.common.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import kome.common.KOMEReflection;
import kome.common.data.KOMEAlliance;
import kome.common.data.KOMEConquestTile;
import kome.common.data.KOMEPopulationType;
import kome.common.data.KOMEWorldData;
import lotr.common.LOTRLevelData;
import lotr.common.fac.LOTRFaction;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;

public class KOMEPacketTileAllocationUpdate implements IMessage {
    public String tileId = "";
    public String playerName = "";
    public String type = "";
    public int amount;
    public boolean add;

    public KOMEPacketTileAllocationUpdate() {
    }

    public KOMEPacketTileAllocationUpdate(String tileId, String playerName, String type, int amount, boolean add) {
        this.tileId = tileId;
        this.playerName = playerName;
        this.type = type;
        this.amount = amount;
        this.add = add;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        tileId = ByteBufUtils.readUTF8String(buf);
        playerName = ByteBufUtils.readUTF8String(buf);
        type = ByteBufUtils.readUTF8String(buf);
        amount = buf.readInt();
        add = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, tileId);
        ByteBufUtils.writeUTF8String(buf, playerName);
        ByteBufUtils.writeUTF8String(buf, type);
        buf.writeInt(amount);
        buf.writeBoolean(add);
    }

    public static class Handler implements IMessageHandler<KOMEPacketTileAllocationUpdate, IMessage> {
        @Override
        public IMessage onMessage(KOMEPacketTileAllocationUpdate message, MessageContext ctx) {
            EntityPlayerMP actor = ctx.getServerHandler().playerEntity;
            if (!legacyPopulationMutationsEnabled()) {
                actor.addChatMessage(new ChatComponentText("Tile population allocations are unavailable pending canonical population migration."));
                return null;
            }
            KOMEWorldData data = KOMEWorldData.get(KOMEReflection.getWorld(actor));
            String tileId = KOMEConquestTile.normalizeId(message.tileId);
            KOMEConquestTile tile = data.conquestTiles.get(tileId);
            if (tile == null || !tile.isClaimed()) {
                actor.addChatMessage(new ChatComponentText("That conquest tile is not claimed."));
                return null;
            }
            String rulingFaction = tile.currentRulingFaction();
            boolean admin = actor.canCommandSenderUseCommand(2, "population");
            if (!admin && !data.isFactionKing(rulingFaction, KOMEReflection.getEntityUUID(actor))) {
                actor.addChatMessage(new ChatComponentText("Only the owning faction's king or an admin can manage allocations."));
                return null;
            }
            EntityPlayerMP target = MinecraftServer.getServer().getConfigurationManager().func_152612_a(message.playerName);
            if (target == null) {
                actor.addChatMessage(new ChatComponentText("Target player must be online."));
                return null;
            }
            LOTRFaction targetPledge = LOTRLevelData.getData(target).getPledgeFaction();
            String targetFaction = targetPledge == null ? "" : targetPledge.codeName();
            if (!admin && !KOMEAlliance.normalizeFactionKey(rulingFaction).equals(KOMEAlliance.normalizeFactionKey(targetFaction))) {
                actor.addChatMessage(new ChatComponentText("The target player must belong to the tile owner's faction."));
                return null;
            }
            KOMEPopulationType type = KOMEPopulationType.forName(message.type);
            int amount = Math.max(0, message.amount);
            if (type == null || amount <= 0) {
                actor.addChatMessage(new ChatComponentText("Allocation type and amount are invalid."));
                return null;
            }
            boolean changed = message.add
                ? data.allocatePopulation(tileId, rulingFaction, KOMEReflection.getEntityUUID(target), target.getCommandSenderName(), type, amount)
                : data.unallocatePopulation(tileId, rulingFaction, KOMEReflection.getEntityUUID(target), type, amount);
            actor.addChatMessage(new ChatComponentText(changed
                ? (message.add ? "Allocated " : "Unallocated ") + amount + " " + type.key + " population " + (message.add ? "to " : "from ") + target.getCommandSenderName() + "."
                : message.add ? "Not enough unallocated effective tile population." : "Allocation cannot be reduced below active unit usage."));
            KOMEPacketConquestOpenCapture.sendTileCommand(actor, tileId);
            return null;
        }
    }

    private static boolean legacyPopulationMutationsEnabled() { return false; }
}
