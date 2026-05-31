package kome.common.command;

import kome.common.KOMEReflection;
import kome.common.data.KOMEProgressionAutoCompleter;
import kome.common.data.KOMEPlayerProgression;
import kome.common.data.KOMEProgressionAchievement;
import kome.common.data.KOMEProgressionLords;
import kome.common.data.KOMEProgressionTaskGenerator;
import kome.common.data.KOMEProgressionTitles;
import kome.common.data.KOMEWorldData;
import lotr.common.entity.npc.LOTRHireableBase;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class KOMECommandProgression extends CommandBase {
    private static final String[] GROUPS = new String[] {"baseline", "wanderer", "serf", "knight", "lord", "prince_king"};

    @Override
    public String getCommandName() {
        return "progression";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/progression status | enable | disable | get [player] | list [player] <group> | pledge | offerings | complete/uncomplete <id> | roll <id> | reroll <player> <id> | grant/revoke <player> <id> | grantall <player> | reset <player>";
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
        if ("status".equalsIgnoreCase(args[0])) {
            KOMEWorldData data = KOMEWorldData.get(getSenderWorld(sender));
            sender.addChatMessage(new ChatComponentText("KOME progression restrictions are " + (data.isProgressionEnabled() ? "enabled." : "disabled.")));
            return;
        }
        if ("enable".equalsIgnoreCase(args[0]) || "disable".equalsIgnoreCase(args[0])) {
            requireStaff(sender);
            if (args.length != 1) {
                throw new WrongUsageException(getCommandUsage(sender));
            }
            boolean enabled = "enable".equalsIgnoreCase(args[0]);
            KOMEWorldData data = KOMEWorldData.get(getSenderWorld(sender));
            data.setProgressionEnabled(enabled);
            sender.addChatMessage(new ChatComponentText("KOME progression restrictions are now " + (enabled ? "enabled." : "disabled.")));
            return;
        }
        if ("get".equalsIgnoreCase(args[0])) {
            EntityPlayerMP player = args.length >= 2 ? getPlayer(sender, args[1]) : getCommandSenderAsPlayer(sender);
            sendSummary(sender, player);
            return;
        }
        if ("list".equalsIgnoreCase(args[0])) {
            listProgression(sender, args);
            return;
        }
        if ("pledge".equalsIgnoreCase(args[0])) {
            if (args.length != 1) {
                throw new WrongUsageException(getCommandUsage(sender));
            }
            EntityPlayerMP player = getCommandSenderAsPlayer(sender);
            LOTRHireableBase lord = KOMEProgressionLords.findNearbyPledgeLord(player);
            if (lord == null) {
                throw new WrongUsageException("Stand within 8 blocks of a captain or unit-trading lord, or shift-click one to open the lord menu.");
            }
            KOMEProgressionLords.pledgeToLord(player, lord);
            return;
        }
        if ("offerings".equalsIgnoreCase(args[0]) || "lordinv".equalsIgnoreCase(args[0]) || "quota".equalsIgnoreCase(args[0])) {
            if (args.length != 1) {
                throw new WrongUsageException(getCommandUsage(sender));
            }
            EntityPlayerMP player = getCommandSenderAsPlayer(sender);
            KOMEProgressionLords.openOfferings(player);
            return;
        }
        if ("complete".equalsIgnoreCase(args[0])) {
            if (args.length != 2) {
                throw new WrongUsageException(getCommandUsage(sender));
            }
            EntityPlayerMP player = getCommandSenderAsPlayer(sender);
            KOMEProgressionAchievement achievement = getSelfCompletableAchievement(args[1]);
            KOMEWorldData data = KOMEWorldData.get(KOMEReflection.getWorld(player));
            KOMEPlayerProgression progression = data.getProgression(KOMEReflection.getEntityUUID(player));
            boolean changed = progression.grant(achievement.id);
            changed = KOMEProgressionAutoCompleter.applyUnlocks(progression) > 0 || changed;
            data.markDirty();
            syncProgression(player, progression);
            player.addChatMessage(new ChatComponentText((changed ? "Completed: " : "Already complete: ") + achievement.title));
            return;
        }
        if ("uncomplete".equalsIgnoreCase(args[0])) {
            if (args.length != 2) {
                throw new WrongUsageException(getCommandUsage(sender));
            }
            EntityPlayerMP player = getCommandSenderAsPlayer(sender);
            KOMEProgressionAchievement achievement = getSelfCompletableAchievement(args[1]);
            KOMEWorldData data = KOMEWorldData.get(KOMEReflection.getWorld(player));
            KOMEPlayerProgression progression = data.getProgression(KOMEReflection.getEntityUUID(player));
            boolean changed = progression.revoke(achievement.id);
            changed = KOMEProgressionAutoCompleter.recomputeUnlocks(progression) > 0 || changed;
            data.markDirty();
            syncProgression(player, progression);
            player.addChatMessage(new ChatComponentText((changed ? "Removed: " : "Was not complete: ") + achievement.title));
            return;
        }
        if ("reroll".equalsIgnoreCase(args[0])) {
            requireStaff(sender);
            if (args.length != 3) {
                throw new WrongUsageException(getCommandUsage(sender));
            }
            EntityPlayerMP player = getPlayer(sender, args[1]);
            KOMEProgressionAchievement achievement = KOMEProgressionAchievement.forID(args[2]);
            if (achievement == null) {
                throw new WrongUsageException("Unknown progression achievement: " + args[2]);
            }
            if (!KOMEProgressionTaskGenerator.canRoll(achievement.id)) {
                throw new WrongUsageException("That progression step does not use a random assignment.");
            }
            KOMEWorldData data = KOMEWorldData.get(KOMEReflection.getWorld(player));
            KOMEPlayerProgression progression = data.getProgression(KOMEReflection.getEntityUUID(player));
            long seed = KOMEReflection.getTotalWorldTime(KOMEReflection.getWorld(player)) ^ KOMEReflection.getEntityUUID(player).getMostSignificantBits() ^ System.nanoTime();
            String assignment = KOMEProgressionTaskGenerator.roll(achievement.id, seed);
            progression.setAssignment(achievement.id, assignment);
            data.markDirty();
            syncProgression(player, progression);
            sender.addChatMessage(new ChatComponentText("Rerolled " + player.getCommandSenderName() + " " + achievement.title + ": " + assignment));
            player.addChatMessage(new ChatComponentText("Your " + achievement.title + " assignment was rerolled: " + assignment));
            return;
        }
        if ("roll".equalsIgnoreCase(args[0])) {
            if (args.length != 2) {
                throw new WrongUsageException(getCommandUsage(sender));
            }
            EntityPlayerMP player = getCommandSenderAsPlayer(sender);
            KOMEProgressionAchievement achievement = getPlayerProgressionAchievement(args[1]);
            if (!KOMEProgressionTaskGenerator.canRoll(achievement.id)) {
                throw new WrongUsageException("That progression step does not use a random assignment.");
            }
            KOMEWorldData data = KOMEWorldData.get(KOMEReflection.getWorld(player));
            KOMEPlayerProgression progression = data.getProgression(KOMEReflection.getEntityUUID(player));
            String current = progression.getAssignment(achievement.id);
            if (current == null || current.trim().isEmpty()) {
                long seed = KOMEReflection.getTotalWorldTime(KOMEReflection.getWorld(player)) ^ KOMEReflection.getEntityUUID(player).getLeastSignificantBits();
                current = KOMEProgressionTaskGenerator.roll(achievement.id, seed);
                progression.setAssignment(achievement.id, current);
                data.markDirty();
            }
            syncProgression(player, progression);
            player.addChatMessage(new ChatComponentText(achievement.title + ": " + current));
            return;
        }
        if ("grant".equalsIgnoreCase(args[0]) || "revoke".equalsIgnoreCase(args[0])) {
            requireStaff(sender);
            if (args.length != 3) {
                throw new WrongUsageException(getCommandUsage(sender));
            }
            EntityPlayerMP player = getPlayer(sender, args[1]);
            KOMEProgressionAchievement achievement = KOMEProgressionAchievement.forID(args[2]);
            if (achievement == null) {
                throw new WrongUsageException("Unknown progression achievement: " + args[2]);
            }
            KOMEWorldData data = KOMEWorldData.get(KOMEReflection.getWorld(player));
            KOMEPlayerProgression progression = data.getProgression(KOMEReflection.getEntityUUID(player));
            boolean changed = "grant".equalsIgnoreCase(args[0]) ? progression.grant(achievement.id) : progression.revoke(achievement.id);
            if ("grant".equalsIgnoreCase(args[0])) {
                changed = KOMEProgressionAutoCompleter.applyUnlocks(progression) > 0 || changed;
            } else {
                changed = KOMEProgressionAutoCompleter.recomputeUnlocks(progression) > 0 || changed;
            }
            data.markDirty();
            syncProgression(player, progression);
            sender.addChatMessage(new ChatComponentText((changed ? "Updated " : "No change for ") + player.getCommandSenderName() + ": " + achievement.id + " = " + progression.isCompleted(achievement)));
            return;
        }
        if ("grantall".equalsIgnoreCase(args[0])) {
            requireStaff(sender);
            if (args.length != 2) {
                throw new WrongUsageException(getCommandUsage(sender));
            }
            EntityPlayerMP player = getPlayer(sender, args[1]);
            KOMEWorldData data = KOMEWorldData.get(KOMEReflection.getWorld(player));
            data.rememberPlayerName(KOMEReflection.getEntityUUID(player), player.getCommandSenderName());
            KOMEPlayerProgression progression = data.getProgression(KOMEReflection.getEntityUUID(player));
            int changed = 0;
            for (KOMEProgressionAchievement achievement : KOMEProgressionAchievement.ALL) {
                changed += progression.grant(achievement.id) ? 1 : 0;
            }
            data.markDirty();
            syncProgression(player, progression);
            sender.addChatMessage(new ChatComponentText("Granted all progression to " + player.getCommandSenderName() + " (" + changed + " newly completed)."));
            player.addChatMessage(new ChatComponentText("All KOME progression steps have been granted by an admin."));
            return;
        }
        if ("reset".equalsIgnoreCase(args[0])) {
            requireStaff(sender);
            if (args.length != 2) {
                throw new WrongUsageException(getCommandUsage(sender));
            }
            EntityPlayerMP player = getPlayer(sender, args[1]);
            KOMEWorldData data = KOMEWorldData.get(KOMEReflection.getWorld(player));
            data.getProgression(KOMEReflection.getEntityUUID(player)).reset();
            data.markDirty();
            syncProgression(player, data.getProgression(KOMEReflection.getEntityUUID(player)));
            sender.addChatMessage(new ChatComponentText("Reset progression for " + player.getCommandSenderName() + ". Default baseline unlocks still apply."));
            return;
        }
        throw new WrongUsageException(getCommandUsage(sender));
    }

    private void sendSummary(ICommandSender sender, EntityPlayerMP player) {
        KOMEWorldData data = KOMEWorldData.get(KOMEReflection.getWorld(player));
        KOMEPlayerProgression progression = data.getProgression(KOMEReflection.getEntityUUID(player));
        sender.addChatMessage(new ChatComponentText("Progression restrictions: " + (data.isProgressionEnabled() ? "enabled" : "disabled")));
        sender.addChatMessage(new ChatComponentText(player.getCommandSenderName() + " progression: " + progression.getCompletedCount(null) + "/" + progression.getTotalCount(null) + " complete"));
        sender.addChatMessage(new ChatComponentText("Pledged lord: " + progression.getPledgedLordDisplay()));
        for (String group : GROUPS) {
            sender.addChatMessage(new ChatComponentText(group + ": " + progression.getCompletedCount(group) + "/" + progression.getTotalCount(group)));
        }
        sender.addChatMessage(new ChatComponentText("Use /progression list " + player.getCommandSenderName() + " <group> to see achievement IDs."));
    }

    private void syncProgression(EntityPlayerMP player, KOMEPlayerProgression progression) {
        KOMEProgressionAutoCompleter.syncPlayer(player, progression);
        KOMEProgressionTitles.updatePlayerTitle(player);
    }

    private void listProgression(ICommandSender sender, String[] args) {
        EntityPlayerMP player;
        String group;
        if (args.length == 2) {
            player = getCommandSenderAsPlayer(sender);
            group = args[1];
        } else if (args.length == 3) {
            player = getPlayer(sender, args[1]);
            group = args[2];
        } else {
            throw new WrongUsageException(getCommandUsage(sender));
        }
        if (!KOMEProgressionAchievement.isGroup(group)) {
            throw new WrongUsageException("Unknown progression group: " + group);
        }
        KOMEWorldData data = KOMEWorldData.get(KOMEReflection.getWorld(player));
        UUID playerID = KOMEReflection.getEntityUUID(player);
        KOMEPlayerProgression progression = data.getProgression(playerID);
        sender.addChatMessage(new ChatComponentText(player.getCommandSenderName() + " " + group + ": " + progression.getCompletedCount(group) + "/" + progression.getTotalCount(group)));
        for (KOMEProgressionAchievement achievement : KOMEProgressionAchievement.forGroup(group)) {
            String mark = progression.isCompleted(achievement) ? "[x] " : "[ ] ";
            sender.addChatMessage(new ChatComponentText(mark + achievement.id + " - " + achievement.title + " (" + achievement.category + ")"));
        }
    }

    private void requireStaff(ICommandSender sender) {
        if (!sender.canCommandSenderUseCommand(2, getCommandName())) {
            throw new WrongUsageException("You do not have permission to change progression.");
        }
    }

    private World getSenderWorld(ICommandSender sender) {
        if (sender instanceof EntityPlayerMP) {
            return KOMEReflection.getWorld((EntityPlayerMP) sender);
        }
        return MinecraftServer.getServer().worldServers[0];
    }

    private KOMEProgressionAchievement getSelfCompletableAchievement(String id) {
        KOMEProgressionAchievement achievement = getPlayerProgressionAchievement(id);
        if (KOMEProgressionAchievement.isAutoManaged(achievement.id)) {
            throw new WrongUsageException("That progression step is automatic and cannot be manually changed.");
        }
        return achievement;
    }

    private KOMEProgressionAchievement getPlayerProgressionAchievement(String id) {
        KOMEProgressionAchievement achievement = KOMEProgressionAchievement.forID(id);
        if (achievement == null) {
            throw new WrongUsageException("Unknown progression achievement: " + id);
        }
        if ("baseline".equals(achievement.group) || achievement.defaultUnlocked) {
            throw new WrongUsageException("Permissions are unlocked by completing progression tasks.");
        }
        return achievement;
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "status", "enable", "disable", "get", "list", "pledge", "offerings", "complete", "uncomplete", "roll", "reroll", "grant", "revoke", "grantall", "reset");
        }
        if (args.length == 2 && ("get".equalsIgnoreCase(args[0]) || "grant".equalsIgnoreCase(args[0]) || "revoke".equalsIgnoreCase(args[0]) || "grantall".equalsIgnoreCase(args[0]) || "reset".equalsIgnoreCase(args[0]) || "reroll".equalsIgnoreCase(args[0]))) {
            return getListOfStringsMatchingLastWord(args, MinecraftServer.getServer().getAllUsernames());
        }
        if (args.length == 2 && ("complete".equalsIgnoreCase(args[0]) || "uncomplete".equalsIgnoreCase(args[0]) || "roll".equalsIgnoreCase(args[0]))) {
            List ids = new ArrayList();
            for (KOMEProgressionAchievement achievement : KOMEProgressionAchievement.ALL) {
                if ("roll".equalsIgnoreCase(args[0]) && KOMEProgressionTaskGenerator.canRoll(achievement.id)) {
                    ids.add(achievement.id);
                } else if (!"roll".equalsIgnoreCase(args[0]) && !"baseline".equals(achievement.group) && !achievement.defaultUnlocked && !KOMEProgressionAchievement.isAutoManaged(achievement.id)) {
                    ids.add(achievement.id);
                }
            }
            return getListOfStringsFromIterableMatchingLastWord(args, ids);
        }
        if (args.length == 2 && "list".equalsIgnoreCase(args[0])) {
            List completions = new ArrayList();
            for (String group : GROUPS) {
                completions.add(group);
            }
            String[] usernames = MinecraftServer.getServer().getAllUsernames();
            for (String username : usernames) {
                completions.add(username);
            }
            return getListOfStringsFromIterableMatchingLastWord(args, completions);
        }
        if (args.length == 3 && "list".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, GROUPS);
        }
        if (args.length == 3 && ("grant".equalsIgnoreCase(args[0]) || "revoke".equalsIgnoreCase(args[0]))) {
            List ids = new ArrayList();
            for (KOMEProgressionAchievement achievement : KOMEProgressionAchievement.ALL) {
                ids.add(achievement.id);
            }
            return getListOfStringsFromIterableMatchingLastWord(args, ids);
        }
        if (args.length == 3 && "reroll".equalsIgnoreCase(args[0])) {
            List ids = new ArrayList();
            for (KOMEProgressionAchievement achievement : KOMEProgressionAchievement.ALL) {
                if (KOMEProgressionTaskGenerator.canRoll(achievement.id)) {
                    ids.add(achievement.id);
                }
            }
            return getListOfStringsFromIterableMatchingLastWord(args, ids);
        }
        return null;
    }
}
