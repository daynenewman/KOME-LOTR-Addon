package kome.common.command;

import kome.common.data.KOMEConquestTile;
import kome.common.data.KOMEWorldData;
import lotr.common.LOTRLevelData;
import lotr.common.fac.LOTRFaction;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class KOMECommandConquest extends CommandBase {
    @Override
    public String getCommandName() {
        return "conquest";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/conquest get <tile> | claim <tile> <faction|none> | transfer <tile> <faction> | accept <tile> | cancelTransfer <tile> | clear <tile> | clearAll | list | purgeLegacy";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length < 1) {
            throw new WrongUsageException(getCommandUsage(sender));
        }

        KOMEWorldData data = KOMEWorldData.get(sender.getEntityWorld());
        if ("list".equalsIgnoreCase(args[0])) {
            listTiles(sender, data);
            return;
        }
        if ("purgeLegacy".equalsIgnoreCase(args[0])) {
            requireStaff(sender);
            purgeLegacyTiles(sender, data);
            return;
        }
        if ("clearAll".equalsIgnoreCase(args[0])) {
            requireStaff(sender);
            clearAllTiles(sender, data);
            return;
        }
        if (args.length < 2) {
            throw new WrongUsageException(getCommandUsage(sender));
        }

        String tileId = KOMEConquestTile.normalizeId(args[1]);
        if (tileId.isEmpty()) {
            throw new WrongUsageException("Tile id cannot be blank");
        }

        if ("get".equalsIgnoreCase(args[0])) {
            KOMEConquestTile tile = data.conquestTiles.get(tileId);
            if (tile == null || !tile.isClaimed()) {
                sender.addChatMessage(new ChatComponentText(tileId + ": unclaimed"));
            } else {
                sender.addChatMessage(new ChatComponentText(tileId + ": faction=" + tile.ownerFaction + ", time=" + tile.claimedWorldTime));
                if (tile.hasPendingTransfer()) {
                    sender.addChatMessage(new ChatComponentText("Pending transfer: " + tile.pendingTransferFromFaction + " -> " + tile.pendingTransferToFaction));
                }
            }
            return;
        }

        if ("clear".equalsIgnoreCase(args[0])) {
            requireStaff(sender);
            KOMEConquestTile tile = data.getConquestTile(tileId);
            tile.clear();
            data.markDirty();
            data.syncConquestTiles();
            sender.addChatMessage(new ChatComponentText("Cleared conquest tile " + tileId));
            return;
        }

        if ("claim".equalsIgnoreCase(args[0])) {
            if (args.length < 3) {
                throw new WrongUsageException(getCommandUsage(sender));
            }
            if (!KOMEConquestTile.isCanonicalTileId(tileId)) {
                sender.addChatMessage(new ChatComponentText("Use the map capture UI to claim tiles for now. Label IDs like " + tileId + " are not claimable until the final tile ID mask is added."));
                return;
            }
            String faction = parseFaction(args[2]);
            requireClaimPermission(sender, faction);
            KOMEConquestTile tile = data.getConquestTile(tileId);
            if (faction.isEmpty()) {
                tile.clear();
                sender.addChatMessage(new ChatComponentText("Set conquest tile " + tileId + " to unclaimed"));
            } else {
                tile.claim(faction, sender.getEntityWorld().getTotalWorldTime());
                sender.addChatMessage(new ChatComponentText("Claimed conquest tile " + tileId + " for " + faction));
            }
            data.markDirty();
            data.syncConquestTiles();
            return;
        }

        if ("transfer".equalsIgnoreCase(args[0]) || "trade".equalsIgnoreCase(args[0])) {
            if (args.length < 3) {
                throw new WrongUsageException(getCommandUsage(sender));
            }
            String faction = parseFaction(args[2]);
            if (faction.isEmpty()) {
                throw new WrongUsageException("Transfer faction cannot be none.");
            }
            KOMEConquestTile tile = data.getConquestTile(tileId);
            if (!tile.isClaimed()) {
                throw new WrongUsageException("Tile " + tileId + " is unclaimed.");
            }
            if (faction.equals(tile.ownerFaction)) {
                throw new WrongUsageException("That tile is already owned by " + faction + ".");
            }
            requireTransferOfferPermission(sender, data, tile, faction);
            tile.proposeTransfer(tile.ownerFaction, faction);
            data.markDirty();
            data.syncConquestTiles();
            sender.addChatMessage(new ChatComponentText("Offered conquest tile " + tileId + " to " + faction + ". Their king must accept."));
            return;
        }

        if ("accept".equalsIgnoreCase(args[0])) {
            KOMEConquestTile tile = data.getConquestTile(tileId);
            requireTransferAcceptPermission(sender, data, tile);
            String faction = tile.pendingTransferToFaction;
            tile.claim(faction, sender.getEntityWorld().getTotalWorldTime());
            data.markDirty();
            data.syncConquestTiles();
            sender.addChatMessage(new ChatComponentText("Accepted conquest tile " + tileId + " for " + faction));
            return;
        }

        if ("cancelTransfer".equalsIgnoreCase(args[0]) || "canceltrade".equalsIgnoreCase(args[0])) {
            KOMEConquestTile tile = data.getConquestTile(tileId);
            requireTransferCancelPermission(sender, tile);
            tile.clearPendingTransfer();
            data.markDirty();
            data.syncConquestTiles();
            sender.addChatMessage(new ChatComponentText("Cancelled pending transfer for conquest tile " + tileId));
            return;
        }

        throw new WrongUsageException(getCommandUsage(sender));
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "get", "claim", "transfer", "trade", "accept", "cancelTransfer", "clear", "clearAll", "list", "purgeLegacy");
        }
        if (args.length == 3 && ("claim".equalsIgnoreCase(args[0]) || "transfer".equalsIgnoreCase(args[0]) || "trade".equalsIgnoreCase(args[0]))) {
            List names = LOTRFaction.getPlayableAlignmentFactionNames();
            names.add("none");
            return getListOfStringsMatchingLastWord(args, (String[]) names.toArray(new String[names.size()]));
        }
        return null;
    }

    private static void listTiles(ICommandSender sender, KOMEWorldData data) {
        List<String> ids = new ArrayList<>(data.conquestTiles.keySet());
        Collections.sort(ids);
        if (ids.isEmpty()) {
            sender.addChatMessage(new ChatComponentText("No conquest tiles have been claimed yet."));
            return;
        }
        int count = 0;
        StringBuilder line = new StringBuilder("Claimed tiles: ");
        for (String id : ids) {
            KOMEConquestTile tile = data.conquestTiles.get(id);
            if (tile == null || !tile.isClaimed()) {
                continue;
            }
            if (count > 0) {
                line.append(", ");
            }
            line.append(id).append("=").append(tile.ownerFaction);
            count++;
            if (count >= 12) {
                line.append("...");
                break;
            }
        }
        sender.addChatMessage(new ChatComponentText(count == 0 ? "No conquest tiles have been claimed yet." : line.toString()));
    }

    private static void purgeLegacyTiles(ICommandSender sender, KOMEWorldData data) {
        int removed = 0;
        List<String> ids = new ArrayList<>(data.conquestTiles.keySet());
        for (String id : ids) {
            if (!KOMEConquestTile.isCanonicalTileId(id)) {
                data.conquestTiles.remove(id);
                removed++;
            }
        }
        if (removed > 0) {
            data.markDirty();
        }
        sender.addChatMessage(new ChatComponentText("Removed " + removed + " legacy conquest tile record(s)."));
    }

    private static void clearAllTiles(ICommandSender sender, KOMEWorldData data) {
        int cleared = 0;
        for (KOMEConquestTile tile : data.conquestTiles.values()) {
            if (tile != null && tile.isClaimed()) {
                tile.clear();
                cleared++;
            }
        }
        if (cleared > 0) {
            data.markDirty();
        }
        data.syncConquestTiles();
        sender.addChatMessage(new ChatComponentText("Cleared " + cleared + " claimed conquest tile(s)."));
    }

    private static String parseFaction(String value) {
        if ("none".equalsIgnoreCase(value)) {
            return "";
        }
        LOTRFaction resolved = LOTRFaction.forName(value);
        if (resolved != null && resolved.isPlayableAlignmentFaction()) {
            return resolved.codeName();
        }
        for (Object object : LOTRFaction.getPlayableAlignmentFactionNames()) {
            String faction = (String) object;
            if (faction.equalsIgnoreCase(value)) {
                return faction;
            }
        }
        throw new WrongUsageException("Unknown faction: " + value);
    }

    private void requireClaimPermission(ICommandSender sender, String faction) {
        if (sender.canCommandSenderUseCommand(2, getCommandName())) {
            return;
        }
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        LOTRFaction pledge = LOTRLevelData.getData(player).getPledgeFaction();
        if (pledge == null || faction.isEmpty() || !pledge.codeName().equals(faction)) {
            throw new WrongUsageException("You can only claim conquest tiles for your pledged faction.");
        }
    }

    private void requireTransferOfferPermission(ICommandSender sender, KOMEWorldData data, KOMEConquestTile tile, String targetFaction) {
        if (!data.hasFactionKing(tile.ownerFaction) || !data.hasFactionKing(targetFaction)) {
            throw new WrongUsageException("Tile trades require real player kings for both factions.");
        }
        if (sender.canCommandSenderUseCommand(2, getCommandName())) {
            return;
        }
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        if (!data.isFactionKing(tile.ownerFaction, kome.common.KOMEReflection.getEntityUUID(player))) {
            throw new WrongUsageException("Only the owning faction's king can offer this conquest tile.");
        }
    }

    private void requireTransferAcceptPermission(ICommandSender sender, KOMEWorldData data, KOMEConquestTile tile) {
        if (!tile.hasPendingTransfer()) {
            throw new WrongUsageException("This conquest tile has no pending transfer.");
        }
        if (!data.hasFactionKing(tile.pendingTransferFromFaction) || !data.hasFactionKing(tile.pendingTransferToFaction)) {
            throw new WrongUsageException("Tile trades require real player kings for both factions.");
        }
        if (sender.canCommandSenderUseCommand(2, getCommandName())) {
            return;
        }
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        if (!data.isFactionKing(tile.pendingTransferToFaction, kome.common.KOMEReflection.getEntityUUID(player))) {
            throw new WrongUsageException("Only the receiving faction's king can accept this conquest tile.");
        }
    }

    private void requireTransferCancelPermission(ICommandSender sender, KOMEConquestTile tile) {
        if (!tile.hasPendingTransfer()) {
            throw new WrongUsageException("This conquest tile has no pending transfer.");
        }
        if (sender.canCommandSenderUseCommand(2, getCommandName())) {
            return;
        }
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        if (!KOMEWorldData.get(sender.getEntityWorld()).isFactionKing(tile.pendingTransferFromFaction, kome.common.KOMEReflection.getEntityUUID(player))) {
            throw new WrongUsageException("Only the offering faction's king can cancel this transfer.");
        }
    }

    private void requireStaff(ICommandSender sender) {
        if (!sender.canCommandSenderUseCommand(2, getCommandName())) {
            throw new WrongUsageException("You do not have permission to use this conquest admin command.");
        }
    }

}
