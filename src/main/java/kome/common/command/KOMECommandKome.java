package kome.common.command;

import kome.common.data.KOMEWorldData;
import kome.common.data.KOMETileOwnershipDefaults;
import kome.common.data.KOMEWaypointDefaults;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.util.ChatComponentText;

import java.util.List;

public class KOMECommandKome extends CommandBase {
    @Override
    public String getCommandName() {
        return "kome";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/kome conquest <reset|balance> | waypointdefaults <reload|apply>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
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
        throw new WrongUsageException(getCommandUsage(sender));
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "conquest", "waypointdefaults");
        }
        if (args.length == 2 && "conquest".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "reset", "balance");
        }
        if (args.length == 2 && "waypointdefaults".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "reload", "apply");
        }
        return null;
    }

    private void requireStaff(ICommandSender sender) {
        if (!sender.canCommandSenderUseCommand(2, getCommandName())) {
            throw new WrongUsageException("You do not have permission to use this KOME admin command.");
        }
    }
}
