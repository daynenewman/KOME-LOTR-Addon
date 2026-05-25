package kome.common.command;

import kome.common.data.KOMEPlayerPopulation;
import kome.common.data.KOMEPopulationType;
import kome.common.data.KOMEProgressionPermissions;
import kome.common.data.KOMEWorldData;
import kome.common.network.KOMEPacketHandler;
import kome.common.network.KOMEPacketHireType;
import kome.common.network.KOMEPacketPopulationGui;
import kome.common.network.KOMEPacketPopulationUnitsGui;
import kome.common.KOMEReflection;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class KOMECommandPopulation extends CommandBase {
    @Override
    public String getCommandName() {
        return "population";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/population get [player] | gui [player] | units [player] | hiretype [player] <offensive|defensive> | set/add/remove <player> <offensive|defensive> <amount>";
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
        if ("get".equalsIgnoreCase(args[0]) || "gui".equalsIgnoreCase(args[0])) {
            EntityPlayerMP player = args.length >= 2 ? getPlayer(sender, args[1]) : getCommandSenderAsPlayer(sender);
            sendStatus(sender, player, "gui".equalsIgnoreCase(args[0]));
            return;
        }
        if ("units".equalsIgnoreCase(args[0])) {
            EntityPlayerMP player = args.length >= 2 ? getPlayer(sender, args[1]) : getCommandSenderAsPlayer(sender);
            sendUnitBreakdown(sender, player);
            return;
        }
        if ("hiretype".equalsIgnoreCase(args[0])) {
            setHireType(sender, args);
            return;
        }
        if (args.length != 4) {
            throw new WrongUsageException(getCommandUsage(sender));
        }

        EntityPlayerMP player = getPlayer(sender, args[1]);
        if (!canManagePopulation(sender, player)) {
            return;
        }
        KOMEPopulationType type = KOMEPopulationType.forName(args[2]);
        if (type == null) {
            throw new WrongUsageException("Population type must be offensive or defensive");
        }
        int amount = Math.max(0, parseInt(sender, args[3]));
        KOMEWorldData data = KOMEWorldData.get(KOMEReflection.getWorld(player));
        KOMEPlayerPopulation pop = data.getPopulation(KOMEReflection.getEntityUUID(player));

        if ("set".equalsIgnoreCase(args[0])) {
            pop.setTotal(type, amount);
        } else if ("add".equalsIgnoreCase(args[0])) {
            pop.addTotal(type, amount);
        } else if ("remove".equalsIgnoreCase(args[0])) {
            pop.addTotal(type, -amount);
        } else {
            throw new WrongUsageException(getCommandUsage(sender));
        }
        data.markDirty();
        sendStatus(sender, player, false);
    }

    private boolean canManagePopulation(ICommandSender sender, EntityPlayerMP target) {
        if (sender.canCommandSenderUseCommand(2, getCommandName())) {
            return true;
        }
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        if (!KOMEReflection.getEntityUUID(player).equals(KOMEReflection.getEntityUUID(target))) {
            KOMEProgressionPermissions.deny(player, "You can only change your own population.");
            return false;
        }
        return KOMEProgressionPermissions.require(player, KOMEProgressionPermissions.GROW_POPULATION);
    }

    private void sendStatus(ICommandSender sender, EntityPlayerMP player, boolean gui) {
        KOMEWorldData data = KOMEWorldData.get(KOMEReflection.getWorld(player));
        UUID playerID = KOMEReflection.getEntityUUID(player);
        data.removeInactiveLoadedHiredUnits(KOMEReflection.getWorld(player), playerID);
        KOMEPlayerPopulation pop = data.getPopulation(playerID);
        int farmhandsUsed = data.getFarmhandsUsed(playerID);
        int farmhandsLimit = pop.getFarmhandLimit();
        int offensiveUsed = data.getArmyPopulationUsed(playerID, KOMEPopulationType.OFFENSIVE);
        int defensiveUsed = data.getArmyPopulationUsed(playerID, KOMEPopulationType.DEFENSIVE);
        int armyUsed = offensiveUsed + defensiveUsed;
        int armyTotal = pop.getCombinedTotal();
        if (gui && sender instanceof EntityPlayerMP) {
            KOMEPacketHandler.network.sendTo(new KOMEPacketPopulationGui(player.getCommandSenderName(), pop.offensiveTotal, offensiveUsed, pop.defensiveTotal, defensiveUsed, farmhandsUsed, farmhandsLimit, armyUsed, armyTotal), (EntityPlayerMP) sender);
            return;
        }
        sender.addChatMessage(new ChatComponentText(player.getCommandSenderName() + " population: Offensive " + offensiveUsed + "/" + pop.offensiveTotal + " used, Defensive " + defensiveUsed + "/" + pop.defensiveTotal + " used, Next hire " + pop.hireType.key + ", Farmhands " + farmhandsUsed + "/" + farmhandsLimit + " used"));
    }

    private void sendUnitBreakdown(ICommandSender sender, EntityPlayerMP player) {
        KOMEWorldData data = KOMEWorldData.get(KOMEReflection.getWorld(player));
        UUID playerID = KOMEReflection.getEntityUUID(player);
        data.removeInactiveLoadedHiredUnits(KOMEReflection.getWorld(player), playerID);
        KOMEPlayerPopulation pop = data.getPopulation(playerID);
        int farmhandsUsed = data.getFarmhandsUsed(playerID);
        int farmhandsLimit = pop.getFarmhandLimit();
        int offensiveUsed = data.getArmyPopulationUsed(playerID, KOMEPopulationType.OFFENSIVE);
        int defensiveUsed = data.getArmyPopulationUsed(playerID, KOMEPopulationType.DEFENSIVE);
        int armyUsed = offensiveUsed + defensiveUsed;
        int armyTotal = pop.getCombinedTotal();
        List offensiveLines = new ArrayList();
        List defensiveLines = new ArrayList();
        List farmhandLines = new ArrayList();
        for (Object object : data.hiredUnits.entrySet()) {
            Map.Entry entry = (Map.Entry) object;
            kome.common.data.KOMEHiredUnitRecord record = (kome.common.data.KOMEHiredUnitRecord) entry.getValue();
            if (!playerID.equals(record.owner)) {
                continue;
            }
            int cap = Math.max(0, record.levelCap);
            if (record.farmhand) {
                farmhandLines.add(formatUnitOverviewLine(record, getFarmhandDisplayName(record), "farmhand", "0", false, cap));
            } else {
                String line = formatUnitOverviewLine(record, getUnitDisplayName(record), record.type.key, String.valueOf(record.cost), record.mounted, cap);
                if (record.type == KOMEPopulationType.DEFENSIVE) {
                    defensiveLines.add(line);
                } else {
                    offensiveLines.add(line);
                }
            }
        }
        List lines = new ArrayList();
        lines.add("Offensive Units (" + offensiveLines.size() + ")");
        lines.addAll(offensiveLines);
        lines.add("");
        lines.add("Defensive Units (" + defensiveLines.size() + ")");
        lines.addAll(defensiveLines);
        lines.add("");
        lines.add("Farmhands (" + farmhandLines.size() + ")");
        lines.addAll(farmhandLines);
        if (sender instanceof EntityPlayerMP) {
            KOMEPacketHandler.network.sendTo(new KOMEPacketPopulationUnitsGui(player.getCommandSenderName(), lines, armyUsed, armyTotal, farmhandsUsed, farmhandsLimit), (EntityPlayerMP) sender);
            return;
        }
        sender.addChatMessage(new ChatComponentText(player.getCommandSenderName() + " tracked units:"));
        if (lines.isEmpty()) {
            sender.addChatMessage(new ChatComponentText("No tracked hired units."));
        } else {
            for (Object line : lines) {
                sender.addChatMessage(new ChatComponentText(String.valueOf(line)));
            }
        }
    }

    private void setHireType(ICommandSender sender, String[] args) {
        EntityPlayerMP player;
        KOMEPopulationType type;
        if (args.length == 2) {
            player = getCommandSenderAsPlayer(sender);
            type = KOMEPopulationType.forName(args[1]);
        } else if (args.length == 3) {
            player = getPlayer(sender, args[1]);
            type = KOMEPopulationType.forName(args[2]);
        } else {
            throw new WrongUsageException("Usage: /population hiretype [player] <offensive|defensive>");
        }
        if (type == null) {
            throw new WrongUsageException("Hire type must be offensive or defensive");
        }
        KOMEWorldData data = KOMEWorldData.get(KOMEReflection.getWorld(player));
        KOMEPlayerPopulation pop = data.getPopulation(KOMEReflection.getEntityUUID(player));
        pop.hireType = type;
        data.markDirty();
        KOMEPacketHandler.network.sendTo(new KOMEPacketHireType(type), player);
        sender.addChatMessage(new ChatComponentText(player.getCommandSenderName() + " next hires will use " + type.key + " population."));
    }

    private String shortID(UUID id) {
        String value = id == null ? "Unknown unit" : id.toString();
        return value.length() <= 8 ? value : value.substring(0, 8);
    }

    private String getUnitDisplayName(kome.common.data.KOMEHiredUnitRecord record) {
        return record.unitName == null || record.unitName.trim().isEmpty() ? shortID(record.entity) : record.unitName;
    }

    private String getFarmhandDisplayName(kome.common.data.KOMEHiredUnitRecord record) {
        String name = getUnitDisplayName(record);
        return isNumberOnly(name) || shortID(record.entity).equals(name) ? "Farmhand" : name;
    }

    private String formatUnitOverviewLine(kome.common.data.KOMEHiredUnitRecord record, String name, String kind, String cost, boolean mounted, int cap) {
        return "UNITCAP\t" + record.entity + "\t" + name + "\t" + kind + "\t" + cost + "\t" + mounted + "\t" + cap;
    }

    private boolean isNumberOnly(String value) {
        return value != null && value.trim().matches("[0-9]+");
    }

    @Override
    public java.util.List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "get", "gui", "units", "hiretype", "set", "add", "remove");
        }
        if (args.length == 2 && "hiretype".equalsIgnoreCase(args[0])) {
            List completions = new ArrayList();
            completions.add("offensive");
            completions.add("defensive");
            String[] usernames = MinecraftServer.getServer().getAllUsernames();
            for (String username : usernames) {
                completions.add(username);
            }
            return getListOfStringsFromIterableMatchingLastWord(args, completions);
        }
        if (args.length == 3 && "hiretype".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "offensive", "defensive");
        }
        if (args.length == 2) {
            return getListOfStringsMatchingLastWord(args, MinecraftServer.getServer().getAllUsernames());
        }
        if (args.length == 3) {
            return getListOfStringsMatchingLastWord(args, "offensive", "defensive");
        }
        return null;
    }
}
