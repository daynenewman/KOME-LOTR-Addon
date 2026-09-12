package kome.common.command;

import kome.common.KOMEReflection;
import kome.common.data.KOMEAlliance;
import kome.common.data.KOMEBuildContribution;
import kome.common.data.KOMEHalfHourService;
import kome.common.data.KOMEBuildService;
import kome.common.data.KOMEPlayerBuild;
import kome.common.data.KOMEForeignConstructionPermission;
import kome.common.data.KOMEForeignConstructionService;
import kome.common.data.KOMEBuildType;
import kome.common.data.KOMETilePopulation;
import kome.common.data.KOMEWorldData;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;

import java.util.List;
import java.util.UUID;

/** Minimal operator repair/debug surface for schema-1 Builds and split population pools. */
public class KOMECommandBuild extends CommandBase {
    @Override
    public String getCommandName() {
        return "build";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/build grants <tile> | grant <tile> <faction> | revoke <tile> <faction> | list [tile] | inspect <id> | reassign <id> <onlinePlayer> | remove <id> | sethours <id> <normal|defensive> <hours>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0) throw new WrongUsageException(getCommandUsage(sender));
        KOMEWorldData data = KOMEWorldData.get(sender.getEntityWorld());
        String action = args[0].toLowerCase(java.util.Locale.ROOT);
        if ("grants".equals(action) && args.length == 2) {
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
            List<KOMEPlayerBuild> builds = tile.length() == 0
                ? new java.util.ArrayList<KOMEPlayerBuild>(data.builds.values())
                : KOMEBuildService.buildsInTile(data, tile, true);
            sender.addChatMessage(new ChatComponentText("Build records: " + builds.size()));
            for (KOMEPlayerBuild build : builds) {
                if (build != null) sender.addChatMessage(new ChatComponentText(summary(data, build)));
            }
            return;
        }
        if (args.length < 2) throw new WrongUsageException(getCommandUsage(sender));
        KOMEPlayerBuild build = data.getBuild(args[1]);
        if (build == null) throw new WrongUsageException("Unknown Build ID: " + args[1]);
        if ("inspect".equals(action) && args.length == 2) {
            sender.addChatMessage(new ChatComponentText(summary(data, build)));
            sender.addChatMessage(new ChatComponentText("Builder=" + build.builderName + " manager="
                + (build.managerName.length() == 0 ? "unassigned" : build.managerName) + " coordinates="
                + build.dimension + ":" + round(build.x) + "," + round(build.y) + "," + round(build.z)));
            sender.addChatMessage(new ChatComponentText("Contributions=" + build.contributions.size()
                + " pending=" + build.pendingCount()));
            return;
        }
        if ("reassign".equals(action) && args.length == 3) {
            requireStaff(sender);
            EntityPlayerMP target = getPlayer(sender, args[2]);
            build.managerUuid = KOMEReflection.getEntityUUID(target);
            build.managerName = target.getCommandSenderName();
            build.updatedAtMillis = System.currentTimeMillis();
            data.markDirty();
            sender.addChatMessage(new ChatComponentText("Reassigned " + build.id + " to " + build.managerName + "."));
            return;
        }
        if ("remove".equals(action) && args.length == 2) {
            requireStaff(sender);
            KOMEBuildService.Decision decision = KOMEBuildService.deleteBuild(data, build, null,
                sender.getCommandSenderName(), true, "Administrative repair removal", System.currentTimeMillis());
            if (!decision.allowed) throw new WrongUsageException(decision.reason);
            sender.addChatMessage(new ChatComponentText("Removed Build " + build.id + "."));
            return;
        }
        if ("sethours".equals(action) && args.length == 4) {
            requireStaff(sender);
            KOMEBuildType type = parseType(args[2]);
            if (type != build.type) throw new WrongUsageException("Hours must match this Build's " + build.type.key + " type.");
            double hours;
            try {
                hours = Double.parseDouble(args[3]);
            } catch (NumberFormatException error) {
                throw new WrongUsageException("Hours must be a number in 0.5 increments.");
            }
            int halfHours;
            try {
                halfHours = KOMEHalfHourService.toHalfHours(hours);
            } catch (IllegalArgumentException error) {
                throw new WrongUsageException(error.getMessage());
            }
            for (KOMEBuildContribution contribution : build.contributions) {
                if (contribution == null || !contribution.isApproved()) continue;
                contribution.halfHours = 0;
            }
            if (halfHours > 0) {
                KOMEBuildContribution repair = new KOMEBuildContribution();
                repair.id = data.nextBuildContributionId(build);
                repair.contributorName = sender.getCommandSenderName();
                repair.contributorFaction = build.originalBuilderFaction;
                repair.halfHours = halfHours;
                repair.status = KOMEBuildContribution.APPROVED;
                repair.submittedAtMillis = repair.decidedAtMillis = System.currentTimeMillis();
                repair.decidedByName = sender.getCommandSenderName();
                repair.decisionReason = "Administrative sethours repair";
                build.contributions.add(repair);
            }
            build.updatedAtMillis = System.currentTimeMillis();
            data.markDirty();
            sender.addChatMessage(new ChatComponentText("Set " + build.id + " " + type.key + " hours to "
                + KOMEHalfHourService.displayHours(halfHours) + "."));
            return;
        }
        throw new WrongUsageException(getCommandUsage(sender));
    }

    private void requireStaff(ICommandSender sender) {
        if (!sender.canCommandSenderUseCommand(2, getCommandName())) {
            throw new WrongUsageException("Only administrators may modify Build records or configuration.");
        }
    }

    private static String summary(KOMEWorldData data, KOMEPlayerBuild build) {
        return build.id + " [" + (build.active ? "active" : "deleted") + "] " + build.displayName
            + " tile=" + build.tileId + " owner=" + KOMEAlliance.displayFactionName(build.populationFaction)
            + " type=" + build.type.key + " hours="
            + KOMEHalfHourService.displayHours(build.approvedHalfHours());
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
