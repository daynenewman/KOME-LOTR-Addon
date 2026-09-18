package kome.common.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;

/** Public command entry only. Every administrative/domain action still checks its own authority. */
public abstract class KOMEPublicCommand extends CommandBase {
    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        // 1.7.10 EntityPlayerMP rejects non-ops even at level 0 for custom command names.
        // Do not delegate the public dispatcher gate to the sender's operator check.
        return sender != null;
    }

    protected final boolean isStaff(ICommandSender sender) {
        return sender != null && sender.canCommandSenderUseCommand(2, getCommandName());
    }

    /** Authorize private inspection before resolving another player or accessing WorldData. */
    protected final EntityPlayerMP privateInspectionTarget(ICommandSender sender, String targetName) {
        if (targetName == null) return getCommandSenderAsPlayer(sender);
        if (sender instanceof EntityPlayerMP
                && sender.getCommandSenderName().equalsIgnoreCase(targetName)) {
            return (EntityPlayerMP) sender;
        }
        if (!isStaff(sender)) {
            throw new WrongUsageException("Only operators may inspect another player's private records. Omit the player name to view your own.");
        }
        return getPlayer(sender, targetName);
    }
}
