package com.lotrcharactercreation.client.command;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.lotrcharactercreation.client.gui.GuiManSkinReview;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class CommandManSkinReview extends CommandBase {

    private boolean openRequested;

    @Override
    public String getCommandName() {
        return "lotrmanreview";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/lotrmanreview";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] arguments) {
        if (arguments.length != 0) {
            ChatComponentText error = new ChatComponentText("Usage: " + getCommandUsage(sender));
            error.getChatStyle()
                .setColor(EnumChatFormatting.RED);
            sender.addChatMessage(error);
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.thePlayer != null && sender == minecraft.thePlayer) {
            openRequested = true;
        }
    }

    @SubscribeEvent
    public void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !openRequested) {
            return;
        }

        openRequested = false;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.thePlayer != null) {
            minecraft.displayGuiScreen(new GuiManSkinReview());
        }
    }
}
