package com.enovak.lotrmoremobs.command;

import com.enovak.lotrmoremobs.pickupfilter.PlayerPickupFilterData;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import com.enovak.lotrmoremobs.pickupfilter.PickupFilterNetwork;

import java.util.List;

/**
 * Supported text fallback for managing the Pickup Filter when a player prefers
 * commands or cannot use the inventory button. The normal GUI remains primary.
 */
public class CommandPickupFilter extends CommandBase {

    @Override
    public String getCommandName() {
        return "pickupfilter";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/pickupfilter <add|remove|clear|list>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return sender instanceof EntityPlayerMP;
    }


    @Override
    public List addTabCompletionOptions(
            ICommandSender sender,
            String[] args
    ) {
        if (args != null
                && args.length == 1) {

            return getListOfStringsMatchingLastWord(
                    args,
                    "add",
                    "remove",
                    "clear",
                    "list"
            );
        }

        return null;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length != 1) {
            throw new WrongUsageException(this.getCommandUsage(sender));
        }

        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        String action = args[0].toLowerCase();

        if ("clear".equals(action)) {
            PlayerPickupFilterData.clearExcludedItems(player);
            PickupFilterNetwork.syncToPlayer(player);
            sender.addChatMessage(new ChatComponentText("Pickup filter cleared."));
            return;
        }

        if ("list".equals(action)) {
            List<ItemStack> excluded = PlayerPickupFilterData.getExcludedItems(player);
            sender.addChatMessage(new ChatComponentText("Pickup filter contains " + excluded.size() + " item(s)."));

            for (ItemStack stack : excluded) {
                sender.addChatMessage(new ChatComponentText(" - " + stack.getDisplayName() + " (meta " + stack.getItemDamage() + ")"));
            }
            return;
        }

        ItemStack held = player.inventory.getCurrentItem();
        if (held == null) {
            sender.addChatMessage(
                    new ChatComponentText("Hold an item in your hand first.")
            );
            return;
        }

        if ("add".equals(action)) {
            if (PlayerPickupFilterData.addExcludedItem(player, held)) {
                PickupFilterNetwork.syncToPlayer(player);
                sender.addChatMessage(
                        new ChatComponentText(
                                "Added to pickup filter: " + held.getDisplayName()
                        )
                );
            } else {
                sender.addChatMessage(
                        new ChatComponentText(
                                "Already filtered: " + held.getDisplayName()
                        )
                );
            }
            return;
        }

        if ("remove".equals(action)) {
            if (PlayerPickupFilterData.removeExcludedItem(player, held)) {
                PickupFilterNetwork.syncToPlayer(player);
                sender.addChatMessage(
                        new ChatComponentText(
                                "Removed from pickup filter: " + held.getDisplayName()
                        )
                );
            } else {
                sender.addChatMessage(
                        new ChatComponentText(
                                "Not currently filtered: " + held.getDisplayName()
                        )
                );
            }
            return;
        }

        throw new WrongUsageException(this.getCommandUsage(sender));
    }
}