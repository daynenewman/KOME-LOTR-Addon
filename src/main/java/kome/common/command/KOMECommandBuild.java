package kome.common.command;

import kome.common.KOMEReflection;
import kome.common.data.KOMEAlliance;
import kome.common.data.KOMEBuildContribution;
import kome.common.data.KOMEBuildPopulationService;
import kome.common.data.KOMEBuildService;
import kome.common.data.KOMEPlayerBuild;
import kome.common.data.KOMEPopulationType;
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
        return "/build list [tile] | inspect <id> | pools <tile> | reassign <id> <onlinePlayer> | remove <id> | sethours <id> <offensive|defensive> <hours> | config populationPerHalfHour <value>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0) throw new WrongUsageException(getCommandUsage(sender));
        KOMEWorldData data = KOMEWorldData.get(sender.getEntityWorld());
        String action = args[0].toLowerCase(java.util.Locale.ROOT);
        if ("config".equals(action) && args.length == 3
                && "populationperhalfhour".equalsIgnoreCase(args[1])) {
            int value;
            try {
                value = Integer.parseInt(args[2]);
            } catch (NumberFormatException error) {
                throw new WrongUsageException("Population per half-hour must be a positive integer.");
            }
            if (value < 1 || value > 100000) {
                throw new WrongUsageException("Population per half-hour must be between 1 and 100000.");
            }
            data.buildPopulationPerHalfHour = value;
            for (KOMEPlayerBuild candidate : data.builds.values()) {
                if (candidate != null) data.recalculateBuildPopulationPool(candidate.tileId, candidate.populationFaction);
            }
            data.reconcileBuildCommitments();
            data.markDirty();
            sender.addChatMessage(new ChatComponentText("Build conversion set to " + value
                + " population per approved half-hour."));
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
        if ("pools".equals(action) && args.length == 2) {
            List<KOMETilePopulation> pools = data.getTilePopulationPools(args[1]);
            sender.addChatMessage(new ChatComponentText("Population pools in " + args[1] + ": " + pools.size()));
            String controller = data.getConquestTile(args[1]).currentRulingFaction();
            for (KOMETilePopulation pool : pools) {
                sender.addChatMessage(new ChatComponentText(KOMEAlliance.displayFactionName(pool.sourceFaction)
                    + " base O" + pool.nativeOffensiveTotal + "/D" + pool.nativeDefensiveTotal
                    + " build O" + data.getBuildPopulationTotal(pool.tileId, pool.sourceFaction, KOMEPopulationType.OFFENSIVE)
                    + "/D" + data.getBuildPopulationTotal(pool.tileId, pool.sourceFaction, KOMEPopulationType.DEFENSIVE)
                    + " usable O" + pool.getEffectiveTotal(KOMEPopulationType.OFFENSIVE, controller)
                    + "/D" + pool.getEffectiveTotal(KOMEPopulationType.DEFENSIVE, controller)));
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
                + " pending=" + build.pendingCount() + " committed O" + build.offensiveCommittedPopulation
                + "/D" + build.defensiveCommittedPopulation));
            return;
        }
        if ("reassign".equals(action) && args.length == 3) {
            EntityPlayerMP target = getPlayer(sender, args[2]);
            build.managerUuid = KOMEReflection.getEntityUUID(target);
            build.managerName = target.getCommandSenderName();
            build.updatedAtMillis = System.currentTimeMillis();
            data.markDirty();
            sender.addChatMessage(new ChatComponentText("Reassigned " + build.id + " to " + build.managerName + "."));
            return;
        }
        if ("remove".equals(action) && args.length == 2) {
            KOMEBuildService.Decision decision = KOMEBuildService.deleteBuild(data, build, null,
                sender.getCommandSenderName(), true, "Administrative repair removal", System.currentTimeMillis());
            if (!decision.allowed) throw new WrongUsageException(decision.reason);
            sender.addChatMessage(new ChatComponentText("Removed Build " + build.id + "."));
            return;
        }
        if ("sethours".equals(action) && args.length == 4) {
            KOMEPopulationType type = parseType(args[2]);
            double hours;
            try {
                hours = Double.parseDouble(args[3]);
            } catch (NumberFormatException error) {
                throw new WrongUsageException("Hours must be a number in 0.5 increments.");
            }
            int halfHours;
            try {
                halfHours = KOMEBuildPopulationService.toHalfHours(hours);
            } catch (IllegalArgumentException error) {
                throw new WrongUsageException(error.getMessage());
            }
            int population = KOMEBuildPopulationService.generatedPopulation(halfHours, data.buildPopulationPerHalfHour);
            if (population < build.committedPopulation(type)) {
                throw new WrongUsageException("The requested total is below " + build.committedPopulation(type)
                    + " population already committed to living units.");
            }
            for (KOMEBuildContribution contribution : build.contributions) {
                if (contribution == null || !contribution.isApproved()) continue;
                if (type == KOMEPopulationType.DEFENSIVE) contribution.defensiveHalfHours = 0;
                else contribution.offensiveHalfHours = 0;
            }
            if (halfHours > 0) {
                KOMEBuildContribution repair = new KOMEBuildContribution();
                repair.id = data.nextBuildContributionId(build);
                repair.contributorName = sender.getCommandSenderName();
                repair.contributorFaction = build.originalBuilderFaction;
                repair.offensiveHalfHours = type == KOMEPopulationType.OFFENSIVE ? halfHours : 0;
                repair.defensiveHalfHours = type == KOMEPopulationType.DEFENSIVE ? halfHours : 0;
                repair.status = KOMEBuildContribution.APPROVED;
                repair.submittedAtMillis = repair.decidedAtMillis = System.currentTimeMillis();
                repair.decidedByName = sender.getCommandSenderName();
                repair.decisionReason = "Administrative sethours repair";
                build.contributions.add(repair);
            }
            build.updatedAtMillis = System.currentTimeMillis();
            data.recalculateBuildPopulationPool(build.tileId, build.populationFaction);
            data.markDirty();
            sender.addChatMessage(new ChatComponentText("Set " + build.id + " " + type.key + " hours to "
                + KOMEBuildPopulationService.displayHours(halfHours) + "."));
            return;
        }
        throw new WrongUsageException(getCommandUsage(sender));
    }

    private static String summary(KOMEWorldData data, KOMEPlayerBuild build) {
        return build.id + " [" + (build.active ? "active" : "deleted") + "] " + build.displayName
            + " tile=" + build.tileId + " owner=" + KOMEAlliance.displayFactionName(build.populationFaction)
            + " hours O" + KOMEBuildPopulationService.displayHours(build.approvedHalfHours(KOMEPopulationType.OFFENSIVE))
            + "/D" + KOMEBuildPopulationService.displayHours(build.approvedHalfHours(KOMEPopulationType.DEFENSIVE))
            + " pop O" + build.approvedPopulation(KOMEPopulationType.OFFENSIVE, data.buildPopulationPerHalfHour)
            + "/D" + build.approvedPopulation(KOMEPopulationType.DEFENSIVE, data.buildPopulationPerHalfHour);
    }

    private static KOMEPopulationType parseType(String value) {
        if ("off".equalsIgnoreCase(value) || "offensive".equalsIgnoreCase(value)) return KOMEPopulationType.OFFENSIVE;
        if ("def".equalsIgnoreCase(value) || "defensive".equalsIgnoreCase(value)) return KOMEPopulationType.DEFENSIVE;
        throw new WrongUsageException("Population type must be offensive or defensive.");
    }

    private static int round(double value) {
        return (int) Math.floor(value);
    }
}
