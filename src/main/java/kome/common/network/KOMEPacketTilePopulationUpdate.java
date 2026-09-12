package kome.common.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import kome.common.KOMEReflection;
import kome.common.data.KOMEConquestTile;
import kome.common.data.KOMEPopulationType;
import kome.common.data.KOMETilePopulation;
import kome.common.data.KOMEWorldData;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;

public class KOMEPacketTilePopulationUpdate implements IMessage {
    public String tileId = "";
    public String type = "";
    public int amount;
    public boolean add;

    public KOMEPacketTilePopulationUpdate() {
    }

    public KOMEPacketTilePopulationUpdate(String tileId, String type, int amount, boolean add) {
        this.tileId = tileId;
        this.type = type;
        this.amount = amount;
        this.add = add;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        tileId = ByteBufUtils.readUTF8String(buf);
        type = ByteBufUtils.readUTF8String(buf);
        amount = buf.readInt();
        add = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, tileId);
        ByteBufUtils.writeUTF8String(buf, type);
        buf.writeInt(amount);
        buf.writeBoolean(add);
    }

    public static class Handler implements IMessageHandler<KOMEPacketTilePopulationUpdate, IMessage> {
        @Override
        public IMessage onMessage(KOMEPacketTilePopulationUpdate message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (!legacyPopulationMutationsEnabled()) {
                player.addChatMessage(new ChatComponentText("Tile population editing is unavailable pending canonical population migration."));
                return null;
            }
            String tileId = KOMEConquestTile.normalizeId(message.tileId);
            if (tileId.isEmpty() || !KOMEConquestTile.isCanonicalTileId(tileId)) {
                player.addChatMessage(new ChatComponentText("Invalid conquest tile."));
                return null;
            }
            KOMEPopulationType type = KOMEPopulationType.forName(message.type);
            if (type == null) {
                player.addChatMessage(new ChatComponentText("Population type must be offensive or defensive."));
                return null;
            }
            int amount = Math.max(0, message.amount);
            if (amount <= 0) {
                player.addChatMessage(new ChatComponentText("Population amount must be positive."));
                return null;
            }
            KOMEWorldData data = KOMEWorldData.get(KOMEReflection.getWorld(player));
            KOMEConquestTile tile = data.getConquestTile(tileId);
            if (!tile.isClaimed()) {
                player.addChatMessage(new ChatComponentText("Population can only be edited on claimed tiles."));
                return null;
            }
            if (!KOMEPacketConquestOpenCapture.canEditPopulation(data, player, tile)) {
                player.addChatMessage(new ChatComponentText("Only admins or the owning faction's king can edit tile population."));
                return null;
            }
            String rulingFaction = tile.currentRulingFaction();
            KOMETilePopulation population = data.getTilePopulation(tileId);
            population.sourceFaction = kome.common.data.KOMEAlliance.normalizeFactionKey(rulingFaction);
            population.faction = population.sourceFaction;
            int delta = message.add ? amount : -amount;
            int before = population.getTotal(type);
            boolean changed = data.adjustTilePopulationTotal(tileId, type, delta);
            int after = population.getTotal(type);
            if (!changed) {
                player.addChatMessage(new ChatComponentText("Cannot reduce " + type.key + " tile population below active usage or king-granted allocations."));
            } else {
                player.addChatMessage(new ChatComponentText((message.add ? "Added " : "Removed ") + Math.abs(after - before) + " " + type.key + " population on tile " + tileId + ". Total: " + after + ", used: " + population.getUsed(type) + "."));
            }
            data.syncConquestTiles();
            KOMEPacketConquestOpenCapture.sendTileCommand(player, tileId);
            return null;
        }
    }

    private static boolean legacyPopulationMutationsEnabled() { return false; }
}
