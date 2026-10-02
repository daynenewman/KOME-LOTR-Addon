package kome.common.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import kome.common.KOMEReflection;
import kome.common.data.*;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

/** Parsing only; inspection and mutations remain in authoritative domain services. */
final class KOMEAdminDiagnosticsCommands {
    static final String HELP = "/kome diagnostics <population|ruler|capital> <faction> | diplomacy <a> <b> | ownership <tile> | waypoint <uuid>"
        + " | /kome repair preview <ownership tile|diplomacy a b|ruler faction player|waypoint uuid> | /kome repair apply <token>";
    private final KOMEAdminRepairService repairs = new KOMEAdminRepairService();

    void process(ICommandSender sender, String[] args, KOMEWorldData data) {
        // Defence in depth; caller also checks before retrieving world data.
        if (!sender.canCommandSenderUseCommand(2, "kome"))
            throw new WrongUsageException("Operator permission level 2 is required");
        try {
            for (String arg : args) if (arg.length() > 128)
                throw new IllegalArgumentException("Diagnostic arguments must not exceed 128 characters");
            String actor = sender instanceof EntityPlayerMP
                ? KOMEReflection.getEntityUUID((EntityPlayerMP) sender).toString() : "console";
            long now = System.currentTimeMillis();
            if ("diagnostics".equalsIgnoreCase(args[0])) {
                if (args.length < 3) throw new IllegalArgumentException(HELP);
                String domain = args[1].toLowerCase(Locale.ROOT);
                if (args.length != ("diplomacy".equals(domain) ? 4 : 3)) throw new IllegalArgumentException(HELP);
                World world = null;
                if ("capital".equals(domain)) {
                    KOMEFactionCapitalRecord capital = KOMEFactionCapitalService.getCapital(data, args[2]);
                    MinecraftServer server = MinecraftServer.getServer();
                    if (capital != null && server != null)
                        world = server.worldServerForDimension(capital.getDeploymentDimensionId());
                }
                for (String line : KOMEAdminDiagnostics.inspect(data, domain, args[2],
                        args.length == 4 ? args[3] : "", world)) say(sender, line);
                return;
            }
            if (args.length == 3 && "apply".equalsIgnoreCase(args[1])) {
                EntityPlayerMP online = online(repairs.onlineRulerForPlan(data, actor, args[2]));
                KOMEAdminRepairService.Result result = repairs.apply(data, true, actor, args[2],
                    online == null ? null : KOMEReflection.getEntityUUID(online),
                    online == null ? null : online.getCommandSenderName(), now);
                say(sender, result.reason);
                // The existing periodic waypoint publisher observes the registry revision.
                return;
            }
            if (args.length >= 4 && "preview".equalsIgnoreCase(args[1])) {
                String domain = args[2].toLowerCase(Locale.ROOT);
                boolean pair = "ruler".equals(domain) || "diplomacy".equals(domain);
                if (args.length != (pair ? 5 : 4)) throw new IllegalArgumentException(HELP);
                EntityPlayerMP online = null;
                if ("ruler".equals(domain)) {
                    MinecraftServer server = MinecraftServer.getServer();
                    online = server == null ? null : server.getConfigurationManager().func_152612_a(args[4]);
                }
                KOMEAdminRepairService.Result result = repairs.preview(data, true, actor, domain, args[3],
                    pair ? args[4] : "", online == null ? null : KOMEReflection.getEntityUUID(online),
                    online == null ? null : online.getCommandSenderName(), now);
                say(sender, result.reason);
                return;
            }
            throw new IllegalArgumentException(HELP);
        } catch (IllegalArgumentException | IllegalStateException unavailable) {
            throw new WrongUsageException(KOMEAdminDiagnostics.line(unavailable.getMessage()));
        }
    }

    static void audit(ICommandSender sender, String[] args, KOMEWorldData data) {
        if (!sender.canCommandSenderUseCommand(2, "kome"))
            throw new WrongUsageException("Operator permission level 2 is required");
        if (args.length < 2 || args.length > 3) throw new WrongUsageException("/kome audit <list|summary> [page]");
        List<String> rows = new ArrayList<String>();
        if ("list".equalsIgnoreCase(args[1])) {
            List<KOMEAuditEntry> entries = KOMEAuditService.entries(data);
            for (int i = entries.size() - 1; i >= 0; i--) rows.add(entries.get(i).compact());
        } else if ("summary".equalsIgnoreCase(args[1])) rows.addAll(KOMEAuditService.summary(data));
        else throw new WrongUsageException("/kome audit <list|summary> [page]");
        int page = 1;
        try { if (args.length == 3) page = Integer.parseInt(args[2]); }
        catch (NumberFormatException invalid) { throw new WrongUsageException("Page must be a positive integer"); }
        int pages = Math.max(1, (rows.size() + 17) / 18);
        if (page < 1 || page > pages) throw new WrongUsageException("Page must be between 1 and " + pages);
        say(sender, "Audit page " + page + "/" + pages + "; " + rows.size() + " rows (newest first for list).");
        int start = (page - 1) * 18;
        for (int i = start; i < Math.min(rows.size(), start + 18); i++) say(sender, rows.get(i));
        if (rows.isEmpty()) say(sender, "No central audit entries.");
    }

    private static EntityPlayerMP online(UUID id) {
        MinecraftServer server = MinecraftServer.getServer();
        if (id == null || server == null || server.getConfigurationManager() == null) return null;
        for (Object entry : server.getConfigurationManager().playerEntityList) {
            if (entry instanceof EntityPlayerMP && id.equals(KOMEReflection.getEntityUUID((EntityPlayerMP) entry)))
                return (EntityPlayerMP) entry;
        }
        return null;
    }

    private static void say(ICommandSender sender, String text) {
        sender.addChatMessage(new ChatComponentText(KOMEAdminDiagnostics.line(text)));
    }
}
