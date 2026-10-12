package kome.common.command;

import java.util.List;
import java.util.UUID;
import kome.common.KOMEReflection;
import kome.common.data.KOMEGovernanceService;
import kome.common.data.KOMEPlayerGovernance;
import kome.common.data.KOMEWorldData;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;

/** Self-service governance. No target-player, origin, season, rank or native-pledge mutation input. */
public final class KOMECommandGovernance extends KOMEPublicCommand {
    public String getCommandName() { return "governance"; }
    public int getRequiredPermissionLevel() { return 0; }
    public String getCommandUsage(ICommandSender sender) {
        return "/governance status [warId] | submit <warId> | exile <warId> <alliedHost>";
    }
    public void processCommand(ICommandSender sender, String[] args) {
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        UUID actor = KOMEReflection.getEntityUUID(player);
        KOMEWorldData data = KOMEWorldData.get(sender.getEntityWorld());
        if (args.length >= 1 && args.length <= 2 && "status".equalsIgnoreCase(args[0])) {
            List<KOMEPlayerGovernance> records = KOMEGovernanceService.records(data, actor);
            int shown = 0;
            for (int i = records.size() - 1; i >= 0 && shown < 10; i--) {
                KOMEPlayerGovernance record = records.get(i);
                if (args.length == 2 && !record.warId.equals(args[1])) continue;
                sender.addChatMessage(new ChatComponentText("Governance " + record.warId + " season " + record.season
                    + ": " + record.state + "; origin=" + record.origin + "; host=" + record.host
                    + "; revision=" + record.revision + "; " + record.reason));
                shown++;
            }
            if (shown == 0) sender.addChatMessage(new ChatComponentText("No saved governance choice. A defeated faction cannot participate before a valid choice. Use /war list for war IDs."));
            return;
        }
        boolean submit = args.length == 2 && "submit".equalsIgnoreCase(args[0]);
        boolean exile = args.length == 3 && "exile".equalsIgnoreCase(args[0]);
        if (!submit && !exile) throw new WrongUsageException(getCommandUsage(sender));
        KOMEGovernanceService.Decision result = KOMEGovernanceService.choose(data, actor, args[1],
            submit ? KOMEPlayerGovernance.State.SUBMITTED : KOMEPlayerGovernance.State.EXILED,
            exile ? args[2] : "", System.currentTimeMillis());
        if (!result.allowed) throw new WrongUsageException(result.reason);
        sender.addChatMessage(new ChatComponentText("Governance saved for " + args[1] + ": "
            + (submit ? "SUBMITTED; civilian activities remain available." : "EXILED under " + result.faction + ".")
            + " Native pledge, progression and assets are preserved."));
    }
}
