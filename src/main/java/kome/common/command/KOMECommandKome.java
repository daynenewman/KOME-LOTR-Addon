package kome.common.command;

import com.lotrcharactercreation.LOTRCharacterCreation;
import com.lotrcharactercreation.creation.CharacterRecreationService;
import com.lotrcharactercreation.creation.CharacterRecreationService.StartResult;
import com.lotrcharactercreation.network.ModNetwork;
import kome.common.KOMEReflection;
import kome.common.config.KOMEConfigInspection;
import kome.common.data.KOMEAlliance;
import kome.common.data.KOMEWorldData;
import kome.common.data.KOMERulerService;
import kome.common.data.KOMETileOwnershipDefaults;
import kome.common.data.KOMEWaypointDefaults;
import kome.common.network.KOMEPacketUnitMapMarkers;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import lotr.common.fac.LOTRFaction;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class KOMECommandKome extends CommandBase {
    @Override
    public String getCommandName() {
        return "kome";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/kome character recreate <player> | config [category] | conquest <reset|balance> | waypointdefaults <reload|apply> | adminmarkers <on|off|status> | ruler <get|assign|remove|repair> ...";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 3 && "character".equalsIgnoreCase(args[0])
            && "recreate".equalsIgnoreCase(args[1])) {
            requireStaff(sender);
            EntityPlayerMP target = getPlayer(sender, args[2]);
            StartResult result = CharacterRecreationService.begin(target);
            if (result == StartResult.ALREADY_IN_CREATION) {
                sender.addChatMessage(
                    new ChatComponentText(target.getCommandSenderName() + " is already in Character Creation."));
                return;
            }

            LOTRCharacterCreation.refreshPlayerStateAndSynchronize(target);
            ModNetwork.sendCharacterCreationRequired(target);
            if (result == StartResult.STARTED) {
                sender.addChatMessage(
                    new ChatComponentText("Started safe character recreation for " + target.getCommandSenderName() + "."));
                target.addChatMessage(
                    new ChatComponentText("[LOTR Character Creation] An administrator reopened Character Creation."));
            } else {
                sender.addChatMessage(
                    new ChatComponentText("Reopened character recreation for " + target.getCommandSenderName() + "."));
            }
            return;
        }
        if (args.length >= 3 && "ruler".equalsIgnoreCase(args[0])) {
            requireStaff(sender);
            processRuler(sender, args);
            return;
        }
        if (args.length == 1 && "config".equalsIgnoreCase(args[0])) {
            requireStaff(sender);
            sendConfig(sender, KOMEConfigInspection.getAllEffectiveValues());
            return;
        }
        if (args.length == 2 && "config".equalsIgnoreCase(args[0])) {
            requireStaff(sender);
            try {
                sendConfig(sender, KOMEConfigInspection.getEffectiveValues(args[1]));
            } catch (IllegalArgumentException e) {
                throw new WrongUsageException(e.getMessage() + ". Use /kome config [dailyBatch|population|movement|battle|muster|siege|battleSupport|encirclement|season]");
            }
            return;
        }
        if (args.length == 2 && "conquest".equalsIgnoreCase(args[0]) && "reset".equalsIgnoreCase(args[1])) {
            requireStaff(sender);
            KOMECommandConquest.resetConquestOwnership(sender, KOMEWorldData.get(sender.getEntityWorld()));
            return;
        }
        if (args.length == 2 && "conquest".equalsIgnoreCase(args[0]) && "balance".equalsIgnoreCase(args[1])) {
            requireStaff(sender);
            KOMEWorldData data = KOMEWorldData.get(sender.getEntityWorld());
            for (String line : data.buildConquestBalanceReportLines()) {
                sender.addChatMessage(new ChatComponentText(line));
            }
            return;
        }
        if (args.length == 2 && "waypointdefaults".equalsIgnoreCase(args[0])) {
            requireStaff(sender);
            if ("reload".equalsIgnoreCase(args[1])) {
                KOMEWaypointDefaults.reload();
                KOMETileOwnershipDefaults.reload();
                sender.addChatMessage(new ChatComponentText("Reloaded " + KOMEWaypointDefaults.loadedEntryCount()
                    + " KOME waypoint default row(s) and " + KOMETileOwnershipDefaults.loadedEntryCount()
                    + " tile ownership default row(s)."));
                return;
            }
            if ("apply".equalsIgnoreCase(args[1])) {
                KOMEWorldData data = KOMEWorldData.get(sender.getEntityWorld());
                data.ensureAutomaticTileWaypointLinks();
                boolean changed = data.applyWaypointDefaults(false);
                if (changed) {
                    data.markDirty();
                }
                data.syncConquestTiles();
                sender.addChatMessage(new ChatComponentText("Applied waypoint and tile ownership default metadata"
                    + (changed ? "." : "; no changes were needed.")
                    + " Current conquest ownership was not reset."));
                return;
            }
        }
        if (args.length == 2 && "adminmarkers".equalsIgnoreCase(args[0])) {
            requireStaff(sender);
            if (!(sender instanceof EntityPlayerMP)) {
                throw new WrongUsageException("Only a player can change personal admin marker visibility.");
            }
            EntityPlayerMP player = (EntityPlayerMP) sender;
            KOMEWorldData data = KOMEWorldData.get(sender.getEntityWorld());
            if ("on".equalsIgnoreCase(args[1]) || "all".equalsIgnoreCase(args[1])) {
                data.setAdminUnitMapMarkersDisabled(KOMEReflection.getEntityUUID(player), false);
                KOMEPacketUnitMapMarkers.sendToPlayer(data, player);
                sender.addChatMessage(new ChatComponentText("Admin live unit markers: showing all tracked loaded units."));
                return;
            }
            if ("off".equalsIgnoreCase(args[1]) || "own".equalsIgnoreCase(args[1])) {
                data.setAdminUnitMapMarkersDisabled(KOMEReflection.getEntityUUID(player), true);
                KOMEPacketUnitMapMarkers.sendToPlayer(data, player);
                sender.addChatMessage(new ChatComponentText("Admin live unit markers disabled. Map now shows only your own loaded units."));
                return;
            }
            if ("status".equalsIgnoreCase(args[1])) {
                boolean disabled = data.isAdminUnitMapMarkersDisabled(KOMEReflection.getEntityUUID(player));
                sender.addChatMessage(new ChatComponentText(disabled
                    ? "Admin live unit markers are disabled; showing only your own loaded units."
                    : "Admin live unit markers are enabled; showing all tracked loaded units."));
                return;
            }
        }
        throw new WrongUsageException(getCommandUsage(sender));
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(
                args,
                "character",
                "config",
                "conquest",
                "waypointdefaults",
                "adminmarkers",
                "ruler");
        }
        if (args.length == 2 && "ruler".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "get", "assign", "remove", "repair");
        }
        if (args.length == 3 && "ruler".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, factionSuggestions());
        }
        if (args.length == 4 && "ruler".equalsIgnoreCase(args[0])
            && ("assign".equalsIgnoreCase(args[1]) || "repair".equalsIgnoreCase(args[1]))) {
            return getListOfStringsMatchingLastWord(args, MinecraftServer.getServer().getAllUsernames());
        }
        if (args.length == 2 && "character".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "recreate");
        }
        if (args.length == 3 && "character".equalsIgnoreCase(args[0])
            && "recreate".equalsIgnoreCase(args[1])) {
            return getListOfStringsMatchingLastWord(args, MinecraftServer.getServer().getAllUsernames());
        }
        if (args.length == 2 && "config".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(
                args,
                "dailyBatch",
                "population",
                "movement",
                "battle",
                "muster",
                "siege",
                "battleSupport",
                "encirclement",
                "season");
        }
        if (args.length == 2 && "conquest".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "reset", "balance");
        }
        if (args.length == 2 && "waypointdefaults".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "reload", "apply");
        }
        if (args.length == 2 && "adminmarkers".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "on", "off", "status");
        }
        return null;
    }

    private void requireStaff(ICommandSender sender) {
        if (!hasStaffPermission(sender)) {
            throw new WrongUsageException("You do not have permission to use this KOME admin command.");
        }
    }

    boolean hasStaffPermission(ICommandSender sender) {
        return sender != null && sender.canCommandSenderUseCommand(2, getCommandName());
    }

    private void processRuler(ICommandSender sender, String[] args) {
        if (args.length < 3) {
            throw new WrongUsageException("/kome ruler <get|assign|remove|repair> <faction> [player]");
        }
        String action = args[1].toLowerCase();
        String faction = resolveSupportedFaction(args[2]);
        KOMEWorldData data = KOMEWorldData.get(sender.getEntityWorld());
        if ("get".equals(action) && args.length == 3) {
            UUID ruler = KOMERulerService.getRuler(data, faction);
            if (ruler == null) {
                sender.addChatMessage(new ChatComponentText("" + KOMEAlliance.displayFactionName(faction) + " has no recognized ruler."));
            } else {
                sender.addChatMessage(new ChatComponentText(KOMEAlliance.displayFactionName(faction)
                    + " ruler: " + ruler + " (" + KOMERulerService.getRulerName(data, faction) + ")."));
            }
            return;
        }
        if ("assign".equals(action) && args.length == 4) {
            EntityPlayerMP target = getPlayer(sender, args[3]);
            UUID previous = KOMERulerService.getRuler(data, faction);
            KOMERulerService.assignRuler(data, faction, KOMEReflection.getEntityUUID(target), target.getCommandSenderName());
            sender.addChatMessage(new ChatComponentText("Assigned " + target.getCommandSenderName() + " as ruler of "
                + KOMEAlliance.displayFactionName(faction) + (previous == null ? "." : "; replaced " + previous + ".")));
            return;
        }
        if ("remove".equals(action) && args.length == 3) {
            UUID previous = KOMERulerService.getRuler(data, faction);
            if (previous == null) {
                sender.addChatMessage(new ChatComponentText(KOMEAlliance.displayFactionName(faction) + " has no recognized ruler; no change made."));
            } else {
                KOMERulerService.removeRuler(data, faction);
                sender.addChatMessage(new ChatComponentText("Removed recognized ruler " + previous + " from "
                    + KOMEAlliance.displayFactionName(faction) + "."));
            }
            return;
        }
        if ("repair".equals(action) && (args.length == 3 || args.length == 4)) {
            UUID authoritativeID = null;
            String authoritativeName = null;
            if (args.length == 4) {
                EntityPlayerMP target = getPlayer(sender, args[3]);
                authoritativeID = KOMEReflection.getEntityUUID(target);
                authoritativeName = target.getCommandSenderName();
            }
            KOMERulerService.RepairResult result = KOMERulerService.repair(data, faction, authoritativeID, authoritativeName);
            UUID ruler = KOMERulerService.getRuler(data, faction);
            sender.addChatMessage(new ChatComponentText("Ruler repair for " + KOMEAlliance.displayFactionName(faction)
                + ": " + (result.changed ? "changed" : "unchanged") + " - " + result.reason
                + ". Result: " + (ruler == null ? "no ruler" : ruler + " (" + KOMERulerService.getRulerName(data, faction) + ")")));
            return;
        }
        throw new WrongUsageException("/kome ruler <get|assign|remove|repair> <faction> [player]");
    }

    private String resolveSupportedFaction(String value) {
        LOTRFaction faction = KOMEAlliance.findLotrFaction(value);
        if (faction == null || !faction.isPlayableAlignmentFaction()) {
            throw new WrongUsageException("Unknown supported faction: " + value);
        }
        return KOMEAlliance.normalizeFactionKey(faction.codeName());
    }

    private String[] factionSuggestions() {
        List<String> suggestions = new ArrayList<String>();
        for (LOTRFaction faction : LOTRFaction.values()) {
            if (faction != null && faction.isPlayableAlignmentFaction()) {
                suggestions.add(faction.codeName());
            }
        }
        return suggestions.toArray(new String[suggestions.size()]);
    }

    private void sendConfig(ICommandSender sender,
            List<KOMEConfigInspection.EffectiveValue> values) {
        for (KOMEConfigInspection.EffectiveValue value : values) {
            sender.addChatMessage(new ChatComponentText(value.format()));
        }
    }
}
