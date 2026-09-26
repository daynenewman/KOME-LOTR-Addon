package kome.common.command;

import kome.common.KOMEReflection;
import kome.common.data.KOMEAlliance;
import kome.common.data.KOMEBuildContribution;
import kome.common.data.KOMEBuildTime;
import kome.common.data.KOMEBuildService;
import kome.common.data.KOMEPlayerBuild;
import kome.common.data.KOMEForeignConstructionPermission;
import kome.common.data.KOMEForeignConstructionService;
import kome.common.data.KOMEBuildType;
import kome.common.data.KOMEWorldData;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;

import java.util.List;
import java.util.UUID;

/** Existing Build inspection and authorized repair surface; hours are exact decimals. */
public class KOMECommandBuild extends KOMEPublicCommand {
    @Override
    public String getCommandName() {
        return "build";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        if (!isStaff(sender)) return "/build list [tile] | inspect <buildId> | grants <tile> | grant/revoke <tile> <faction> (ruler only); submit and review through Tile Command";
        return "/build grants <tile> | grant <tile> <faction> | revoke <tile> <faction> | list [tile] | inspect <id> | import <tile> <faction> <hours> [name] | reassign <id> <onlinePlayer> | remove <id> | sethours <id> <normal|defensive> <hours> | adjust <id> <contribution> <hours> [reason]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0) throw new WrongUsageException(getCommandUsage(sender));
        if ("reassign".equalsIgnoreCase(args[0]) || "remove".equalsIgnoreCase(args[0])
                || "sethours".equalsIgnoreCase(args[0]) || "adjust".equalsIgnoreCase(args[0])
                || "import".equalsIgnoreCase(args[0])) requireStaff(sender);
        if ("grant".equalsIgnoreCase(args[0]) || "revoke".equalsIgnoreCase(args[0])) getCommandSenderAsPlayer(sender);
        KOMEWorldData data = KOMEWorldData.get(sender.getEntityWorld());
        String action = args[0].toLowerCase(java.util.Locale.ROOT);
        if ("grants".equals(action) && args.length == 2) {
            requirePublicTile(data, args[1]);
            List<KOMEForeignConstructionPermission> grants = KOMEForeignConstructionService.currentForTile(data, args[1]);
            sender.addChatMessage(new ChatComponentText("Foreign construction grants: " + grants.size()));
            for (KOMEForeignConstructionPermission grant : grants) sender.addChatMessage(new ChatComponentText(KOMEAlliance.displayFactionName(grant.granteeFaction) + " granted by " + KOMEAlliance.displayFactionName(grant.grantingFaction)));
            return;
        }
        if (("grant".equals(action) || "revoke".equals(action)) && args.length == 3) {
            if (!(sender instanceof EntityPlayerMP)) throw new WrongUsageException("Only a recognized ruler may manage construction grants.");
            EntityPlayerMP player = (EntityPlayerMP) sender;
            KOMEForeignConstructionService.Decision decision = "grant".equals(action)
                ? KOMEForeignConstructionService.grant(data, args[1], KOMEReflection.getEntityUUID(player), args[2], System.currentTimeMillis())
                : KOMEForeignConstructionService.revoke(data, args[1], KOMEReflection.getEntityUUID(player), args[2]);
            if (!decision.allowed) throw new WrongUsageException(decision.reason);
            sender.addChatMessage(new ChatComponentText("grant".equals(action) ? "Construction permission granted." : "Construction permission revoked for future Builds."));
            return;
        }
        if ("list".equals(action)) {
            String tile = args.length > 1 ? args[1] : "";
            if (tile.length() > 0) requirePublicTile(data, tile);
            List<KOMEPlayerBuild> builds = tile.length() == 0
                ? new java.util.ArrayList<KOMEPlayerBuild>(data.builds.values())
                : KOMEBuildService.buildsInTile(data, tile, true);
            java.util.Iterator<KOMEPlayerBuild> visible = builds.iterator();
            while (visible.hasNext()) {
                KOMEPlayerBuild build = visible.next();
                if (build == null || data.getPublicConquestTile(build.tileId) == null) visible.remove();
            }
            sender.addChatMessage(new ChatComponentText("Build records: " + builds.size()));
            for (KOMEPlayerBuild build : builds) {
                sender.addChatMessage(new ChatComponentText(summary(data, build)));
            }
            return;
        }
        if ("import".equals(action) && args.length >= 4) {
            EntityPlayerMP player = getCommandSenderAsPlayer(sender);
            StringBuilder name = new StringBuilder();
            for (int i = 4; i < args.length; i++) {
                if (name.length() > 0) name.append(' ');
                name.append(args[i]);
            }
            long hours;
            try { hours = KOMEBuildTime.parseHours(args[3]); }
            catch (IllegalArgumentException invalid) {
                throw new WrongUsageException(invalid.getMessage());
            }
            KOMEPlayerBuild imported = KOMEBuildService.importGrandfatheredNormal(
                data, true, name.length() == 0 ? "Imported " + args[1] : name.toString(),
                args[1], player.dimension, player.posX, player.posY, player.posZ,
                actorId(sender), sender.getCommandSenderName(), args[2], hours,
                System.currentTimeMillis());
            data.syncConquestTiles();
            sender.addChatMessage(new ChatComponentText("Imported " + imported.id
                + " with " + KOMEBuildTime.formatHours(imported.approvedCentiHours())
                + " approved and developed hours."));
            return;
        }
        if (args.length < 2) throw new WrongUsageException(getCommandUsage(sender));
        KOMEPlayerBuild build = data.getBuild(args[1]);
        if (build == null) throw new WrongUsageException("Unknown Build ID: " + args[1]);
        if ("inspect".equals(action) && args.length == 2) {
            requirePublicTile(data, build.tileId);
            sender.addChatMessage(new ChatComponentText(summary(data, build)));
            sender.addChatMessage(new ChatComponentText("Builder=" + build.builderName + " manager="
                + (build.managerName.length() == 0 ? "unassigned" : build.managerName) + " coordinates="
                + build.dimension + ":" + round(build.x) + "," + round(build.y) + "," + round(build.z)));
            sender.addChatMessage(new ChatComponentText("Contributions=" + build.contributions.size()
                + " pending=" + build.pendingCount()));
            for (KOMEBuildContribution contribution : build.contributions) {
                sender.addChatMessage(new ChatComponentText(contribution.id + " " + contribution.status
                    + " hours=" + KOMEBuildTime.formatHours(contribution.centiHours) + " reviewer="
                    + contribution.decidedByName + " reason=" + contribution.decisionReason));
            }
            if (isStaff(sender)) for (String entry : build.auditHistory()) sender.addChatMessage(new ChatComponentText(entry));
            return;
        }
        if ("reassign".equals(action) && args.length == 3) {
            requireStaff(sender);
            EntityPlayerMP target = getPlayer(sender, args[2]);
            KOMEBuildService.Decision decision = KOMEBuildService.reassignManager(data, build, actorId(sender),
                sender.getCommandSenderName(), true, KOMEReflection.getEntityUUID(target),
                target.getCommandSenderName(), System.currentTimeMillis());
            if (!decision.allowed) throw new WrongUsageException(decision.reason);
            sender.addChatMessage(new ChatComponentText("Reassigned " + build.id + " to " + build.managerName + "."));
            return;
        }
        if ("remove".equals(action) && args.length == 2) {
            requireStaff(sender);
            KOMEBuildService.Decision decision = KOMEBuildService.deleteBuild(data, build, actorId(sender),
                sender.getCommandSenderName(), true, "Administrative repair removal", System.currentTimeMillis());
            if (!decision.allowed) throw new WrongUsageException(decision.reason);
            data.syncConquestTiles();
            sender.addChatMessage(new ChatComponentText("Removed Build " + build.id + "."));
            return;
        }
        if ("sethours".equals(action) && args.length == 4) {
            requireStaff(sender);
            KOMEBuildType type = parseType(args[2]);
            if (type != build.type) throw new WrongUsageException("Hours must match this Build's " + build.type.key + " type.");
            KOMEBuildService.Decision decision = KOMEBuildService.setApprovedHours(data, build, actorId(sender),
                sender.getCommandSenderName(), true, args[3], System.currentTimeMillis());
            if (!decision.allowed) throw new WrongUsageException(decision.reason);
            data.syncConquestTiles();
            sender.addChatMessage(new ChatComponentText("Set " + build.id + " " + type.key + " hours to "
                + KOMEBuildTime.formatHours(build.approvedCentiHours()) + "."));
            return;
        }
        if ("adjust".equals(action) && args.length >= 4) {
            requireStaff(sender);
            StringBuilder reason = new StringBuilder();
            for (int i = 4; i < args.length; i++) {
                if (reason.length() > 0) reason.append(' ');
                reason.append(args[i]);
            }
            KOMEBuildService.Decision decision = KOMEBuildService.adjustSubmission(data, build, args[2], actorId(sender),
                sender.getCommandSenderName(), true, args[3], reason.toString(), System.currentTimeMillis());
            if (!decision.allowed) throw new WrongUsageException(decision.reason);
            data.syncConquestTiles();
            sender.addChatMessage(new ChatComponentText("Adjusted " + args[2] + " to "
                + KOMEBuildTime.formatHours(build.getContribution(args[2]).centiHours) + " hours."));
            return;
        }
        throw new WrongUsageException(getCommandUsage(sender));
    }

    private static UUID actorId(ICommandSender sender) {
        return sender instanceof EntityPlayerMP ? KOMEReflection.getEntityUUID((EntityPlayerMP) sender) : null;
    }

    @Override
    public java.util.List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length != 1) return java.util.Collections.emptyList();
        return isStaff(sender)
            ? getListOfStringsMatchingLastWord(args, "list", "inspect", "grants", "grant", "revoke", "import", "reassign", "remove", "sethours", "adjust")
            : getListOfStringsMatchingLastWord(args, "list", "inspect", "grants", "grant", "revoke");
    }

    private static void requirePublicTile(KOMEWorldData data, String tile) {
        if (data.getPublicConquestTile(tile) == null)
            throw new WrongUsageException("Unknown or unavailable public tile: " + tile);
    }

    private void requireStaff(ICommandSender sender) {
        if (!sender.canCommandSenderUseCommand(2, getCommandName())) {
            throw new WrongUsageException("Only administrators may modify Build records or configuration.");
        }
    }

    private static String summary(KOMEWorldData data, KOMEPlayerBuild build) {
        kome.common.data.KOMEPopulationRateContribution projected = null;
        for (kome.common.data.KOMEPopulationRateContribution row
                : kome.common.data.KOMEPopulationRateService.getPopulationRateContributions(data)) {
            if (build.id.equals(row.buildId)) { projected = row; break; }
        }
        String production = projected == null ? "inactive; current 0/day"
            : projected.status + " x" + projected.multiplier + "; current "
                + projected.formatCurrentRate();
        return build.id + " [" + (build.active ? "active" : "deleted") + "] " + build.displayName
            + " tile=" + build.tileId + " owner=" + KOMEAlliance.displayFactionName(build.populationFaction)
            + " type=" + build.type.key + " approved/developed/pending="
            + KOMEBuildTime.formatHours(build.approvedCentiHours()) + "/"
            + KOMEBuildTime.formatHours(build.developedNativeCentiHours) + "/"
            + KOMEBuildTime.formatHours(build.pendingNativeCentiHours())
            + " hours; " + production;
    }

    private static KOMEBuildType parseType(String value) {
        if ("normal".equalsIgnoreCase(value)) return KOMEBuildType.NORMAL;
        if ("def".equalsIgnoreCase(value) || "defensive".equalsIgnoreCase(value)) return KOMEBuildType.DEFENSIVE;
        throw new WrongUsageException("Build type must be normal or defensive.");
    }

    private static int round(double value) {
        return (int) Math.floor(value);
    }
}
