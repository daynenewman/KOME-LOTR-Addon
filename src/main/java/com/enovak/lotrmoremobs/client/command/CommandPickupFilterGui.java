package com.enovak.lotrmoremobs.client.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import com.enovak.lotrmoremobs.client.pickupfilter.PickupFilterGuiOpenHandler;
import java.util.List;

/**
 * Temporary client-only command used to test the pickup-filter GUI.
 *
 * Remove this once the inventory button is wired up.
 */
public class CommandPickupFilterGui extends CommandBase {

    @Override
    public String getCommandName() {
        return "pickupfiltergui";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/pickupfiltergui";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public List addTabCompletionOptions(
            ICommandSender sender,
            String[] args
    ) {
        return null;
    }

    @Override
    public void processCommand(
            ICommandSender sender,
            String[] args
    ) {
        System.out.println(
                "[PickupFilter] /pickupfiltergui command fired"
        );

        PickupFilterGuiOpenHandler.requestOpen();
    }
}