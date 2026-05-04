package kome.common.command;

import kome.common.data.KOMEPlayerPopulation;
import kome.common.data.KOMEPopulationType;
import kome.common.data.KOMEWorldData;
import kome.common.network.KOMEPacketHandler;
import kome.common.network.KOMEPacketPopulationGui;
import kome.common.KOMEReflection;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;

public class KOMECommandPopulation extends CommandBase {
    @Override
    public String getCommandName() {
        return "population";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/population get [player] | gui [player] | set/add/remove <player> <offensive|defensive> <amount>";
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
        if (args.length != 4) {
            throw new WrongUsageException(getCommandUsage(sender));
        }

        EntityPlayerMP player = getPlayer(sender, args[1]);
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

    private void sendStatus(ICommandSender sender, EntityPlayerMP player, boolean gui) {
        KOMEWorldData data = KOMEWorldData.get(KOMEReflection.getWorld(player));
        KOMEPlayerPopulation pop = data.getPopulation(KOMEReflection.getEntityUUID(player));
        int farmhandsUsed = data.getFarmhandsUsed(KOMEReflection.getEntityUUID(player));
        int farmhandsLimit = pop.getFarmhandLimit();
        int armyUsed = data.getArmyPopulationUsed(KOMEReflection.getEntityUUID(player));
        int armyTotal = pop.getCombinedTotal();
        if (gui && sender instanceof EntityPlayerMP) {
            KOMEPacketHandler.network.sendTo(new KOMEPacketPopulationGui(player.getCommandSenderName(), pop.offensiveTotal, pop.offensiveUsed, pop.defensiveTotal, pop.defensiveUsed, farmhandsUsed, farmhandsLimit, armyUsed, armyTotal), (EntityPlayerMP) sender);
            return;
        }
        sender.addChatMessage(new ChatComponentText(player.getCommandSenderName() + " population: Offensive total " + pop.offensiveTotal + ", Defensive total " + pop.defensiveTotal + ", Army " + armyUsed + "/" + armyTotal + " used, Farmhands " + farmhandsUsed + "/" + farmhandsLimit + " used"));
    }

    @Override
    public java.util.List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "get", "gui", "set", "add", "remove");
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
