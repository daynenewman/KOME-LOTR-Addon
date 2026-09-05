package com.lotrcharactercreation.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.lotrcharactercreation.network.ModNetwork;
import com.lotrcharactercreation.race.PlayerRaceData;

public class CommandCharacter extends CommandBase {

    @Override
    public String getCommandName() {
        return "character";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/character";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] arguments) {
        if (arguments.length != 0) {
            sendUsage(sender);
            return;
        }
        if (!(sender instanceof EntityPlayerMP)) {
            sender.addChatMessage(new ChatComponentText("Character creation is only available to players."));
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP) sender;
        if (PlayerRaceData.isCharacterCreationComplete(player)) {
            player.addChatMessage(new ChatComponentText("Your character has already been created."));
            return;
        }

        ModNetwork.sendCharacterCreationRequired(player);
    }

    private static void sendUsage(ICommandSender sender) {
        ChatComponentText error = new ChatComponentText("Usage: /character");
        error.getChatStyle()
            .setColor(EnumChatFormatting.RED);
        sender.addChatMessage(error);
    }
}
