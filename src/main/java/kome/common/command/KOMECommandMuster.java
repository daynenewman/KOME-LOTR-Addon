package kome.common.command;

import java.util.UUID;
import kome.common.KOMEReflection;
import kome.common.data.KOMEFactionCapitalRecord;
import kome.common.data.KOMEFactionCapitalService;
import kome.common.data.KOMEMusterNativeRoster;
import kome.common.data.KOMEMusterRecord;
import kome.common.data.KOMEMusterService;
import kome.common.data.KOMEWorldData;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

/** Native faction ruler action. No client-supplied budget, seed, roster, location or season. */
public final class KOMECommandMuster extends KOMEPublicCommand {
    public String getCommandName() { return "muster"; }
    public String getCommandUsage(ICommandSender sender) { return "/muster call | status"; }
    public int getRequiredPermissionLevel() { return 0; }
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length != 1 || !("call".equalsIgnoreCase(args[0]) || "status".equalsIgnoreCase(args[0])))
            throw new WrongUsageException(getCommandUsage(sender));
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        UUID actor = KOMEReflection.getEntityUUID(player);
        KOMEWorldData data = KOMEWorldData.get(sender.getEntityWorld());
        final String faction = data.getPlayerFactionKey(actor);
        if ("status".equalsIgnoreCase(args[0])) {
            java.util.List<KOMEMusterRecord> records = KOMEMusterService.records(data, faction);
            if (records.isEmpty()) sender.addChatMessage(new ChatComponentText("No civilian muster has been called for this faction."));
            for (KOMEMusterRecord record : records) sender.addChatMessage(new ChatComponentText(
                "Muster " + record.key() + ": " + record.getStatus() + "; due=" + record.dueAtMillis
                    + "; capital=" + record.capital.getCapitalTileId() + "; budgetUnits=" + record.budgetUnits
                    + "; spentUnits=" + record.spentUnits + "; " + record.getPendingReason()));
            return;
        }
        KOMEMusterService.Result result = KOMEMusterService.call(data, faction, actor,
            System.currentTimeMillis(), new KOMEMusterService.RosterSource() {
                public java.util.List<kome.common.data.KOMEMusterRoster.Unit> resolve(String nativeFaction) {
                    KOMEFactionCapitalRecord capital = KOMEFactionCapitalService.getCapital(data, nativeFaction);
                    World world = MinecraftServer.getServer().worldServerForDimension(capital.getDeploymentDimensionId());
                    return new KOMEMusterNativeRoster(world).resolve(nativeFaction);
                }
            });
        if (!result.success()) throw new WrongUsageException(result.reason);
        sender.addChatMessage(new ChatComponentText(result.reason));
    }
    @Override public java.util.List addTabCompletionOptions(ICommandSender sender, String[] args) {
        return args.length == 1 ? getListOfStringsMatchingLastWord(args, "call", "status") : java.util.Collections.emptyList();
    }
}
