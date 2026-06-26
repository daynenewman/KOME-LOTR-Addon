package kome.common.command;

import kome.common.data.KOMEAlliance;
import kome.common.data.KOMEConquestTileDefaults;
import kome.common.data.KOMEConquestTile;
import kome.common.data.KOMETileWaypointLink;
import kome.common.data.KOMEWorldData;
import lotr.common.LOTRLevelData;
import lotr.common.fac.LOTRFaction;
import lotr.common.world.map.LOTRWaypoint;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class KOMECommandConquest extends CommandBase {
    @Override
    public String getCommandName() {
        return "conquest";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/conquest get <tile> | claim <tile> <faction|none> | transfer <tile> <faction> | accept <tile> | cancelTransfer <tile> | clear <tile> | clearAll | reset | list | waypoint <list|link|unlink|get|nearest|autolink|autolinkall> ... | purgeLegacy";
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
        if ("reset".equalsIgnoreCase(args[0])) {
            requireStaff(sender);
            resetConquestOwnership(sender, data);
            return;
        }
        if ("waypoint".equalsIgnoreCase(args[0])) {
            try {
                handleWaypoint(sender, data, args);
            } catch (WrongUsageException e) {
                throw e;
            } catch (Throwable e) {
                sender.addChatMessage(new ChatComponentText("KOME waypoint command failed: " + e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : ": " + e.getMessage())));
                e.printStackTrace();
            }
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
            if (tile == null) {
                sender.addChatMessage(new ChatComponentText(tileId + ": no conquest tile record."));
            } else {
                String current = tile.currentRulingFaction();
                String defaultFaction = KOMEAlliance.normalizeFactionKey(tile.defaultRulingFaction);
                sender.addChatMessage(new ChatComponentText(tileId + ": current ruling faction="
                    + (current.length() == 0 ? "unclaimed" : current) + ", default ruling faction="
                    + (defaultFaction.length() == 0 ? "unclaimed" : defaultFaction) + ", level="
                    + (tile.waypointLevel >= 1 && tile.waypointLevel <= 3 ? tile.waypointLevel : 0)
                    + ", region=" + safe(tile.mapRegion) + ", time=" + tile.claimedWorldTime));
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
            requireClaimPermission(sender, data, faction);
            KOMEConquestTile tile = data.getConquestTile(tileId);
            if (faction.isEmpty()) {
                tile.clear();
                sender.addChatMessage(new ChatComponentText("Set conquest tile " + tileId + " to unclaimed"));
            } else {
                EntityPlayerMP claimant = sender instanceof EntityPlayerMP ? (EntityPlayerMP) sender : null;
                data.claimTile(tile, faction, sender.getEntityWorld().getTotalWorldTime(), claimant == null ? null : kome.common.KOMEReflection.getEntityUUID(claimant), claimant == null ? sender.getCommandSenderName() : claimant.getCommandSenderName());
                data.ensureDefaultArrivalPoint(tile);
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
            String currentOwner = KOMEAlliance.normalizeFactionKey(tile.currentRulingFaction());
            if (faction.equals(currentOwner)) {
                throw new WrongUsageException("That tile is already owned by " + faction + ".");
            }
            requireTransferOfferPermission(sender, data, tile, faction);
            tile.proposeTransfer(currentOwner, faction);
            data.markDirty();
            data.syncConquestTiles();
            sender.addChatMessage(new ChatComponentText("Offered conquest tile " + tileId + " to " + faction + ". Their king must accept."));
            return;
        }

        if ("accept".equalsIgnoreCase(args[0])) {
            KOMEConquestTile tile = data.getConquestTile(tileId);
            requireTransferAcceptPermission(sender, data, tile);
            String faction = tile.pendingTransferToFaction;
            EntityPlayerMP claimant = sender instanceof EntityPlayerMP ? (EntityPlayerMP) sender : null;
            data.claimTile(tile, faction, sender.getEntityWorld().getTotalWorldTime(), claimant == null ? null : kome.common.KOMEReflection.getEntityUUID(claimant), claimant == null ? sender.getCommandSenderName() : claimant.getCommandSenderName());
            data.ensureDefaultArrivalPoint(tile);
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
            return getListOfStringsMatchingLastWord(args, "get", "claim", "transfer", "trade", "accept", "cancelTransfer", "clear", "clearAll", "reset", "list", "waypoint", "purgeLegacy");
        }
        if (args.length == 2 && "waypoint".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "list", "link", "unlink", "get", "nearest", "autolink", "autolinkall");
        }
        if (args.length == 3 && "waypoint".equalsIgnoreCase(args[0]) && "autolinkall".equalsIgnoreCase(args[1])) {
            return getListOfStringsMatchingLastWord(args, "overwrite");
        }
        if (args.length == 4 && "waypoint".equalsIgnoreCase(args[0]) && "link".equalsIgnoreCase(args[1])) {
            return getListOfStringsMatchingLastWord(args, waypointCodeNames());
        }
        if (args.length == 3 && ("claim".equalsIgnoreCase(args[0]) || "transfer".equalsIgnoreCase(args[0]) || "trade".equalsIgnoreCase(args[0]))) {
            List names = LOTRFaction.getPlayableAlignmentFactionNames();
            names.add("none");
            return getListOfStringsMatchingLastWord(args, (String[]) names.toArray(new String[names.size()]));
        }
        return null;
    }

    private void handleWaypoint(ICommandSender sender, KOMEWorldData data, String[] args) {
        requireStaff(sender);
        if (args.length < 2) {
            throw new WrongUsageException("/conquest waypoint <list|link|unlink|get|nearest|autolink|autolinkall> ...");
        }
        if ("list".equalsIgnoreCase(args[1])) {
            listLotrWaypoints(sender, args.length >= 3 ? args[2] : "");
            return;
        }
        if ("autolinkall".equalsIgnoreCase(args[1])) {
            boolean overwrite = args.length >= 3 && ("overwrite".equalsIgnoreCase(args[2]) || "--overwrite".equalsIgnoreCase(args[2]));
            autolinkAllWaypoints(sender, data, overwrite);
            return;
        }
        if (args.length < 3) {
            throw new WrongUsageException("/conquest waypoint " + args[1] + " <tileId>");
        }
        String tileId = KOMEConquestTile.normalizeId(args[2]);
        if (tileId.length() == 0 || !KOMEConquestTile.isCanonicalTileId(tileId)) {
            throw new WrongUsageException("Invalid conquest tile id: " + args[2]);
        }
        if ("get".equalsIgnoreCase(args[1])) {
            printWaypointLink(sender, data, tileId);
            return;
        }
        if ("unlink".equalsIgnoreCase(args[1])) {
            boolean removed = data.unlinkTileWaypoint(tileId);
            data.syncConquestTiles();
            sender.addChatMessage(new ChatComponentText((removed ? "Unlinked " : "No LOTR waypoint link existed for ") + tileId + "."));
            return;
        }
        if ("link".equalsIgnoreCase(args[1])) {
            if (args.length < 4) {
                throw new WrongUsageException("/conquest waypoint link <tileId> <lotrWaypointKey>");
            }
            LOTRWaypoint waypoint = findWaypoint(args[3]);
            if (waypoint == null) {
                throw new WrongUsageException("Unknown LOTR waypoint: " + args[3] + ". Use /conquest waypoint list " + args[3]);
            }
            UUID uuid = senderUuid(sender);
            KOMETileWaypointLink link = data.linkTileWaypoint(tileId, waypoint, uuid, sender.getCommandSenderName());
            data.syncConquestTiles();
            sender.addChatMessage(new ChatComponentText("Linked " + tileId + " to LOTR waypoint " + safeLinkDisplay(link) + " (" + link.lotrWaypointKey + ")."));
            return;
        }
        if ("nearest".equalsIgnoreCase(args[1])) {
            LOTRWaypoint waypoint = automaticWaypointForTile(tileId);
            if (waypoint == null) {
                sender.addChatMessage(new ChatComponentText("No LOTR waypoint falls inside " + tileId + "."));
            } else {
                sender.addChatMessage(new ChatComponentText("Automatic LOTR waypoint for " + tileId + ": " + safeWaypointDisplay(waypoint)
                    + " (" + waypoint.getCodeName() + "), map " + Math.round(waypoint.getX()) + ", " + Math.round(waypoint.getY())
                    + ". Use /conquest waypoint link " + tileId + " " + waypoint.getCodeName() + " to link it."));
            }
            return;
        }
        if ("autolink".equalsIgnoreCase(args[1])) {
            LOTRWaypoint waypoint = automaticWaypointForTile(tileId);
            if (waypoint == null) {
                sender.addChatMessage(new ChatComponentText("No LOTR waypoint falls inside " + tileId + "; no automatic link was created."));
                return;
            }
            UUID uuid = senderUuid(sender);
            KOMETileWaypointLink link = data.linkTileWaypoint(tileId, waypoint, uuid, sender.getCommandSenderName(), KOMETileWaypointLink.SOURCE_AUTO_COMMAND, false);
            data.syncConquestTiles();
            sender.addChatMessage(new ChatComponentText("Auto-linked " + tileId + " to contained LOTR waypoint " + safeLinkDisplay(link) + " (" + link.lotrWaypointKey + ")."));
            return;
        }
        throw new WrongUsageException("/conquest waypoint <list|link|unlink|get|nearest|autolink|autolinkall> ...");
    }

    private static void autolinkAllWaypoints(ICommandSender sender, KOMEWorldData data, boolean overwrite) {
        if (overwrite) {
            List<String> remove = new ArrayList<String>();
            for (Object key : data.tileWaypointLinksByTileId.keySet()) {
                remove.add(String.valueOf(key));
            }
            for (String key : remove) {
                data.tileWaypointLinksByTileId.remove(key);
            }
            data.markDirty();
        }
        int before = data.tileWaypointLinksByTileId.size();
        data.ensureAutomaticTileWaypointLinks();
        int after = data.tileWaypointLinksByTileId.size();
        data.syncConquestTiles();
        sender.addChatMessage(new ChatComponentText("Automatic LOTR waypoint links are now enabled for the world."));
        sender.addChatMessage(new ChatComponentText("Links before: " + before + ", after: " + after
            + ". Tiles only link when an LOTR waypoint falls inside that conquest tile."));
        List<String> examples = new ArrayList<String>();
        List<String> keys = new ArrayList<String>(data.tileWaypointLinksByTileId.keySet());
        Collections.sort(keys);
        for (String key : keys) {
            KOMETileWaypointLink link = data.tileWaypointLinksByTileId.get(key);
            if (link != null && examples.size() < 8) {
                examples.add(key + " -> " + safeLinkDisplay(link));
            }
        }
        if (!examples.isEmpty()) {
            sender.addChatMessage(new ChatComponentText("Examples: " + joinExamples(examples)));
        }
    }

    private static void listLotrWaypoints(ICommandSender sender, String searchText) {
        String search = searchText == null ? "" : searchText.toLowerCase(Locale.ROOT);
        int shown = 0;
        int matches = 0;
        sender.addChatMessage(new ChatComponentText("LOTR waypoints" + (search.length() == 0 ? ":" : " matching '" + searchText + "':")));
        for (LOTRWaypoint waypoint : LOTRWaypoint.values()) {
            if (waypoint == null || waypoint.isHidden()) {
                continue;
            }
            String key = waypoint.getCodeName();
            String display = safeWaypointDisplay(waypoint);
            String haystack = (key + " " + display).toLowerCase(Locale.ROOT);
            if (search.length() > 0 && !haystack.contains(search)) {
                continue;
            }
            matches++;
            if (shown < 20) {
                sender.addChatMessage(new ChatComponentText("  " + key + " - " + display
                    + " (map " + Math.round(waypoint.getX()) + ", " + Math.round(waypoint.getY()) + ")"));
                shown++;
            }
        }
        if (matches == 0) {
            sender.addChatMessage(new ChatComponentText("  No LOTR waypoints matched."));
        } else if (matches > shown) {
            sender.addChatMessage(new ChatComponentText("  +" + (matches - shown) + " more. Add a search term to narrow the list."));
        }
    }

    private static void printWaypointLink(ICommandSender sender, KOMEWorldData data, String tileId) {
        KOMETileWaypointLink link = data.getTileWaypointLink(tileId);
        if (link == null) {
            sender.addChatMessage(new ChatComponentText(tileId + " LOTR waypoint: missing"));
            return;
        }
        sender.addChatMessage(new ChatComponentText(tileId + " LOTR waypoint: " + safeLinkDisplay(link) + " (" + link.lotrWaypointKey + ")"));
        sender.addChatMessage(new ChatComponentText("  Region: " + safe(link.waypointRegion) + ", faction: " + safe(link.waypointFaction)));
        sender.addChatMessage(new ChatComponentText("  Map: " + Math.round(link.waypointMapX) + ", " + Math.round(link.waypointMapZ)
            + "; world: " + link.waypointWorldX + ", " + link.waypointWorldZ + "; dim " + link.dimensionId));
    }

    private static LOTRWaypoint findWaypoint(String value) {
        if (value == null || value.trim().length() == 0) {
            return null;
        }
        String search = value.trim();
        LOTRWaypoint waypoint = LOTRWaypoint.waypointForName(search.toUpperCase(Locale.ROOT));
        if (waypoint != null) {
            return waypoint;
        }
        for (LOTRWaypoint candidate : LOTRWaypoint.values()) {
            if (candidate.getCodeName().equalsIgnoreCase(search) || safeWaypointDisplay(candidate).equalsIgnoreCase(search)) {
                return candidate;
            }
        }
        String lower = search.toLowerCase(Locale.ROOT);
        for (LOTRWaypoint candidate : LOTRWaypoint.values()) {
            if (candidate.getCodeName().toLowerCase(Locale.ROOT).contains(lower)
                    || safeWaypointDisplay(candidate).toLowerCase(Locale.ROOT).contains(lower)) {
                return candidate;
            }
        }
        return null;
    }

    private static LOTRWaypoint automaticWaypointForTile(String tileId) {
        KOMEConquestTileDefaults.TileCenter center = KOMEConquestTileDefaults.getTileCenter(tileId);
        if (center == null) {
            return null;
        }
        LOTRWaypoint best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LOTRWaypoint waypoint : LOTRWaypoint.values()) {
            if (waypoint == null || waypoint.isHidden()) {
                continue;
            }
            String waypointTile = KOMEConquestTileDefaults.getTileIdAtMapPosition(waypoint.getX(), waypoint.getY());
            if (!KOMEConquestTile.normalizeId(tileId).equals(waypointTile)) {
                continue;
            }
            double dx = waypoint.getXCoord() - center.x;
            double dz = waypoint.getZCoord() - center.z;
            double distance = dx * dx + dz * dz;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = waypoint;
            }
        }
        return best;
    }

    private static UUID senderUuid(ICommandSender sender) {
        if (sender instanceof EntityPlayerMP) {
            return kome.common.KOMEReflection.getEntityUUID((EntityPlayerMP) sender);
        }
        return null;
    }

    private static String safeWaypointDisplay(LOTRWaypoint waypoint) {
        if (waypoint == null) {
            return "";
        }
        try {
            String display = waypoint.getDisplayName();
            return display == null || display.length() == 0 ? waypoint.getCodeName() : display;
        } catch (Throwable ignored) {
            return waypoint.getCodeName();
        }
    }

    private static String safeLinkDisplay(KOMETileWaypointLink link) {
        if (link == null) {
            return "";
        }
        try {
            return link.displayName();
        } catch (Throwable ignored) {
            return link.waypointDisplayName == null || link.waypointDisplayName.length() == 0 ? link.lotrWaypointKey : link.waypointDisplayName;
        }
    }

    private static String joinExamples(List<String> examples) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < examples.size(); i++) {
            if (i > 0) {
                text.append("; ");
            }
            text.append(examples.get(i));
        }
        return text.toString();
    }

    private static String[] waypointCodeNames() {
        List<String> names = new ArrayList<String>();
        for (LOTRWaypoint waypoint : LOTRWaypoint.values()) {
            if (waypoint != null && !waypoint.isHidden()) {
                names.add(waypoint.getCodeName());
            }
        }
        return names.toArray(new String[names.size()]);
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
            line.append(id).append("=").append(tile.currentRulingFaction());
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

    public static void resetConquestOwnership(ICommandSender sender, KOMEWorldData data) {
        int changed = data.resetConquestOwnershipToDefaults(sender.getEntityWorld().getTotalWorldTime());
        sender.addChatMessage(new ChatComponentText("Reset conquest ownership for " + changed + " tile(s) to waypoint defaults/unclaimed. Population data was preserved."));
    }

    private static String parseFaction(String value) {
        String normalized = KOMEAlliance.normalizeFactionKey(value);
        if (normalized.length() == 0) {
            return "";
        }
        LOTRFaction resolved = KOMEAlliance.findLotrFaction(value);
        if (resolved != null && resolved.isPlayableAlignmentFaction()) {
            return KOMEAlliance.normalizeFactionKey(resolved.codeName());
        }
        throw new WrongUsageException("Unknown faction: " + value);
    }

    private static String safe(String value) {
        return value == null || value.length() == 0 ? "None" : value;
    }

    private void requireClaimPermission(ICommandSender sender, KOMEWorldData data, String faction) {
        if (sender.canCommandSenderUseCommand(2, getCommandName())) {
            return;
        }
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        String pledgedFaction = KOMEAlliance.normalizeFactionKey(getPlayerFaction(data, player));
        if (pledgedFaction.isEmpty() || faction.isEmpty() || !pledgedFaction.equals(faction)) {
            throw new WrongUsageException("You can only claim conquest tiles for your pledged faction.");
        }
    }

    private void requireTransferOfferPermission(ICommandSender sender, KOMEWorldData data, KOMEConquestTile tile, String targetFaction) {
        String rulingFaction = tile.currentRulingFaction();
        if (!data.hasFactionKing(rulingFaction) || !data.hasFactionKing(targetFaction)) {
            throw new WrongUsageException("Tile trades require real player kings for both factions.");
        }
        if (sender.canCommandSenderUseCommand(2, getCommandName())) {
            return;
        }
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        if (!data.isFactionKing(rulingFaction, kome.common.KOMEReflection.getEntityUUID(player))) {
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

    private String getPlayerFaction(KOMEWorldData data, EntityPlayerMP player) {
        LOTRFaction pledge = LOTRLevelData.getData(player).getPledgeFaction();
        if (pledge != null) {
            return KOMEAlliance.normalizeFactionKey(pledge.codeName());
        }
        return data.getPlayerFactionKey(kome.common.KOMEReflection.getEntityUUID(player));
    }

}
