package com.lotrcharactercreation.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.lotrcharactercreation.network.ModNetwork;
import com.lotrcharactercreation.race.PlayerRaceData;
import com.lotrcharactercreation.trait.RaceTraitService;

public class CommandLotrCreation extends CommandBase {

    @Override
    public String getCommandName() {
        return "lotrcreation";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/lotrcreation [complete|reset]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] arguments) {
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);

        if (arguments.length == 0) {
            reportStatus(sender, player);
            return;
        }

        if (arguments.length != 1) {
            sendUsageError(sender);
            return;
        }

        if (arguments[0].equalsIgnoreCase("complete")) {
            PlayerRaceData.setCharacterCreationComplete(player, true);
        } else if (arguments[0].equalsIgnoreCase("reset")) {
            PlayerRaceData.setCharacterCreationComplete(player, false);
        } else {
            sendUsageError(sender);
            return;
        }

        RaceTraitService.refreshDerivedAttributes(player);
        ModNetwork.sendPlayerAppearanceToTrackingAndSelf(player);
        reportStatus(sender, player);
    }

    private static void reportStatus(ICommandSender sender, EntityPlayerMP player) {
        boolean complete = PlayerRaceData.isCharacterCreationComplete(player);
        sender.addChatMessage(new ChatComponentText("Character creation complete: " + complete));
    }

    private static void sendUsageError(ICommandSender sender) {
        ChatComponentText error = new ChatComponentText("Usage: /lotrcreation [complete|reset].");
        error.getChatStyle()
            .setColor(EnumChatFormatting.RED);
        sender.addChatMessage(error);
    }
}
