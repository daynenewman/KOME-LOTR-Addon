package kome.common.command;

import kome.common.data.KOMETerritory;
import kome.common.data.KOMEWorldData;
import kome.common.network.KOMEPacketHandler;
import kome.common.network.KOMEPacketTerritoryGui;
import lotr.common.fac.LOTRFaction;
import lotr.common.world.map.LOTRWaypoint;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;

public class KOMECommandTerritory extends CommandBase {
    @Override
    public String getCommandName() {
        return "territory";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/territory get|gui <waypoint> | set <waypoint> <faction|none> <ruler|none> [display name...] | clear <waypoint>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            throw new WrongUsageException(getCommandUsage(sender));
        }

        LOTRWaypoint waypoint = LOTRWaypoint.waypointForName(args[1]);
        if (waypoint == null) {
            throw new WrongUsageException("Unknown waypoint: " + args[1]);
        }
        KOMEWorldData data = KOMEWorldData.get(sender.getEntityWorld());
        KOMETerritory territory = data.getTerritory(waypoint.getCodeName());

        if ("get".equalsIgnoreCase(args[0])) {
            sender.addChatMessage(new ChatComponentText(waypoint.getDisplayName() + ": faction=" + valueOrNone(territory.faction) + ", ruler=" + valueOrNone(territory.ruler) + ", display=" + valueOrNone(territory.displayName)));
            return;
        }
        if ("gui".equalsIgnoreCase(args[0])) {
            if (sender instanceof EntityPlayerMP) {
                KOMEPacketHandler.network.sendTo(new KOMEPacketTerritoryGui(waypoint.getCodeName(), waypoint.getDisplayName(), territory.faction, territory.ruler, territory.displayName), (EntityPlayerMP) sender);
            }
            return;
        }
        if ("clear".equalsIgnoreCase(args[0])) {
            data.territories.remove(waypoint.getCodeName());
            data.markDirty();
            data.syncTerritories();
            sender.addChatMessage(new ChatComponentText("Cleared territory for " + waypoint.getDisplayName()));
            return;
        }
        if ("set".equalsIgnoreCase(args[0])) {
            if (args.length < 4) {
                throw new WrongUsageException(getCommandUsage(sender));
            }
            territory.faction = "none".equalsIgnoreCase(args[2]) ? "" : args[2];
            territory.ruler = "none".equalsIgnoreCase(args[3]) ? "" : args[3];
            territory.displayName = args.length >= 5 ? joinFrom(args, 4) : "";
            data.markDirty();
            data.syncTerritories();
            sender.addChatMessage(new ChatComponentText("Saved territory for " + waypoint.getDisplayName()));
            return;
        }
        throw new WrongUsageException(getCommandUsage(sender));
    }

    private static String valueOrNone(String value) {
        return value == null || value.isEmpty() ? "none" : value;
    }

    @Override
    public java.util.List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "get", "gui", "set", "clear");
        }
        if (args.length == 2) {
            return getListOfStringsMatchingLastWord(args, waypointNames());
        }
        if (args.length == 3 && "set".equalsIgnoreCase(args[0])) {
            java.util.List names = LOTRFaction.getPlayableAlignmentFactionNames();
            names.add("none");
            return getListOfStringsMatchingLastWord(args, (String[]) names.toArray(new String[names.size()]));
        }
        if (args.length == 4 && "set".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, MinecraftServer.getServer().getAllUsernames());
        }
        return null;
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

    private static String[] waypointNames() {
        LOTRWaypoint[] waypoints = LOTRWaypoint.values();
        String[] names = new String[waypoints.length];
        for (int i = 0; i < waypoints.length; i++) {
            names[i] = waypoints[i].getCodeName();
        }
        return names;
    }
}
