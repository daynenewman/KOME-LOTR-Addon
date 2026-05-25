package kome.common.command;

import kome.common.data.KOMEConquestTile;
import kome.common.data.KOMEWorldData;
import lotr.common.fac.LOTRFaction;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
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
        return "/conquest get <tile> | claim <tile> <faction|none> [ruler...] | clear <tile> | clearAll | list | purgeLegacy";
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
            purgeLegacyTiles(sender, data);
            return;
        }
        if ("clearAll".equalsIgnoreCase(args[0])) {
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
                sender.addChatMessage(new ChatComponentText(tileId + ": faction=" + tile.ownerFaction + ", ruler=" + valueOrNone(tile.ruler) + ", claimedBy=" + valueOrNone(tile.lastClaimedBy) + ", time=" + tile.claimedWorldTime));
            }
            return;
        }

        if ("clear".equalsIgnoreCase(args[0])) {
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
            String ruler = args.length >= 4 ? joinFrom(args, 3) : sender.getCommandSenderName();
            KOMEConquestTile tile = data.getConquestTile(tileId);
            if (faction.isEmpty()) {
                tile.clear();
                sender.addChatMessage(new ChatComponentText("Set conquest tile " + tileId + " to unclaimed"));
            } else {
                tile.claim(faction, ruler, sender.getCommandSenderName(), sender.getEntityWorld().getTotalWorldTime());
                sender.addChatMessage(new ChatComponentText("Claimed conquest tile " + tileId + " for " + faction + " under " + valueOrNone(ruler)));
            }
            data.markDirty();
            data.syncConquestTiles();
            return;
        }

        throw new WrongUsageException(getCommandUsage(sender));
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "get", "claim", "clear", "clearAll", "list", "purgeLegacy");
        }
        if (args.length == 3 && "claim".equalsIgnoreCase(args[0])) {
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

    private static String joinFrom(String[] args, int start) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (i > start) {
                sb.append(' ');
            }
            sb.append(args[i]);
        }
        return sb.toString();
    }

    private static String valueOrNone(String value) {
        return value == null || value.trim().isEmpty() ? "none" : value;
    }
}
