package kome.common.command;

import kome.common.KOMEAddon;
import kome.common.data.KOMEAlliance;
import kome.common.data.KOMEAllianceAuthority;
import kome.common.data.KOMEAllianceBenefits;
import kome.common.data.KOMEArmyCompany;
import kome.common.data.KOMEAllianceFactionLedger;
import kome.common.data.KOMEAllianceGraceService;
import kome.common.data.KOMEAllianceInventory;
import kome.common.data.KOMEAllianceRecordBuilder;
import kome.common.data.KOMEAllianceRequirements;
import kome.common.data.KOMEAllianceQuotaPool;
import kome.common.data.KOMEConquestTile;
import kome.common.data.KOMEConquestTileDefaults;
import kome.common.data.KOMEHiredUnitRecord;
import kome.common.data.KOMEPopulationType;
import kome.common.data.KOMEPlayerTilePopulationAllocation;
import kome.common.data.KOMETilePopulation;
import kome.common.data.KOMEProgressionTaskGenerator;
import kome.common.data.KOMEWorldData;
import kome.common.data.KOMEWarService;
import kome.common.data.KOMEWartimeStewardshipService;
import kome.common.data.KOMEWaypointAccessService;
import kome.common.gui.KOMEAllianceGuiHandler;
import kome.common.network.KOMEPacketAllianceData;
import kome.common.network.KOMEPacketHandler;
import lotr.common.LOTRLevelData;
import lotr.common.LOTRPlayerData;
import lotr.common.fac.LOTRFaction;
import lotr.common.fac.LOTRFactionRelations;
import lotr.common.world.map.LOTRWaypoint;
import lotr.common.world.map.LOTRAbstractWaypoint;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class KOMECommandAlliance extends CommandBase {
    @Override
    public String getCommandName() {
        return "alliance";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/alliance request|accept|break ... | config difficulty|requirement|waypointRestriction|grace ... | waypoint bypass|check ... | grace status|set|expire ... | roll|reroll|goods|claimGoods|get|set|clear|list|benefits";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length < 1) {
            throw new WrongUsageException(getCommandUsage(sender));
        }
        KOMEWorldData data = KOMEWorldData.get(sender.getEntityWorld());
        if ("config".equalsIgnoreCase(args[0])) {
            processAllianceConfig(sender, args, data);
            return;
        }
        if ("waypoint".equalsIgnoreCase(args[0])) {
            processWaypointAdmin(sender, args, data);
            return;
        }
        if ("grace".equalsIgnoreCase(args[0])) {
            processGraceAdmin(sender, args, data);
            return;
        }
        if ("benefits".equalsIgnoreCase(args[0])) {
            sendBenefits(sender);
            return;
        }
        if ("list".equalsIgnoreCase(args[0])) {
            String faction = args.length >= 2 ? parseFaction(args[1]) : "";
            listAlliances(sender, data, faction);
            return;
        }
        if ("get".equalsIgnoreCase(args[0])) {
            if (args.length != 3) {
                throw new WrongUsageException(getCommandUsage(sender));
            }
            KOMEAlliance alliance = data.getAlliance(parseFaction(args[1]), parseFaction(args[2]), false);
            sendAlliance(sender, alliance, parseFaction(args[1]), parseFaction(args[2]));
            return;
        }
        if ("request".equalsIgnoreCase(args[0])) {
            if (args.length != 4) {
                throw new WrongUsageException(getCommandUsage(sender));
            }
            String type = parseType(args[1]);
            String senderFaction = parseFaction(args[2]);
            String receiverFaction = parseFaction(args[3]);
            if (senderFaction.equals(receiverFaction)) {
                throw new WrongUsageException("A faction cannot ally with itself.");
            }
            EntityPlayerMP actor = sender instanceof EntityPlayerMP ? (EntityPlayerMP) sender : null;
            KOMEAllianceAuthority.Decision requestDecision;
            if (actor == null && sender.canCommandSenderUseCommand(2, getCommandName())) {
                requestDecision = KOMEAllianceAuthority.Decision.allow(!data.hasFactionKing(receiverFaction));
            } else {
                requestDecision = new KOMEAllianceAuthority(data).canRequestAlliance(actor, type, senderFaction, receiverFaction);
            }
            if (!requestDecision.allowed) {
                throw new WrongUsageException(requestDecision.reason);
            }
            KOMEAlliance alliance = data.getAlliance(senderFaction, receiverFaction, true);
            if (alliance.getTier(type) != KOMEAlliance.NONE) {
                throw new WrongUsageException("A " + displayType(type) + " agreement already exists for this mutual alliance.");
            }
            boolean pending = !requestDecision.automaticAcceptance;
            alliance.requestTrack(type, sender.getCommandSenderName(), sender.getEntityWorld().getTotalWorldTime(), pending);
            if (pending) {
                alliance.setPendingParties(type, senderFaction, receiverFaction);
            }
            if (!pending) {
                KOMEAllianceFactionLedger waived = alliance.getFactionLedger(receiverFaction);
                if (waived != null) {
                    waived.kinglessWaived = true;
                }
                syncRelationsForAlliancePair(data, senderFaction, receiverFaction);
            }
            data.markDirty();
            sendAllianceRefreshToParticipants(data, alliance);
            if (pending) {
                sender.addChatMessage(new ChatComponentText("Requested mutual " + displayType(type) + " alliance between " + displayFaction(senderFaction) + " and " + displayFaction(receiverFaction) + ". Waiting for " + data.getFactionKingName(receiverFaction) + " to accept."));
            } else {
                sender.addChatMessage(new ChatComponentText("Accepted automatically: " + displayFaction(receiverFaction) + " is kingless and the default relation permits this mutual " + displayType(type) + " agreement. Its contribution track is waived until it gains a king."));
            }
            return;
        }
        if ("accept".equalsIgnoreCase(args[0])) {
            if (args.length != 4) {
                throw new WrongUsageException(getCommandUsage(sender));
            }
            String type = parseType(args[1]);
            String senderFaction = parseFaction(args[2]);
            String receiverFaction = parseFaction(args[3]);
            KOMEAlliance alliance = data.getAlliance(senderFaction, receiverFaction, false);
            if (alliance == null || !alliance.hasAnyAlliance()) {
                throw new WrongUsageException("No mutual alliance request exists between " + displayFaction(senderFaction) + " and " + displayFaction(receiverFaction) + ".");
            }
            EntityPlayerMP actor = sender instanceof EntityPlayerMP ? (EntityPlayerMP) sender : null;
            String authoritativeReceiver = alliance.getPendingReceiver(type).length() > 0
                ? alliance.getPendingReceiver(type) : receiverFaction;
            KOMEAllianceAuthority.Decision acceptDecision = actor == null && sender.canCommandSenderUseCommand(2, getCommandName())
                ? KOMEAllianceAuthority.Decision.allow(false)
                : new KOMEAllianceAuthority(data).canAcceptAlliance(actor, alliance, type, authoritativeReceiver);
            if (!acceptDecision.allowed) {
                throw new WrongUsageException(acceptDecision.reason);
            }
            if (alliance.getTier(type) != KOMEAlliance.PENDING) {
                throw new WrongUsageException("No pending mutual " + displayType(type) + " alliance exists between " + displayFaction(senderFaction) + " and " + displayFaction(receiverFaction) + ".");
            }
            alliance.acceptTrack(type, sender.getCommandSenderName(), sender.getEntityWorld().getTotalWorldTime());
            syncRelationsForAlliancePair(data, senderFaction, receiverFaction);
            data.markDirty();
            sendAllianceRefreshToParticipants(data, alliance);
            sender.addChatMessage(new ChatComponentText("Accepted mutual " + displayType(type) + " alliance between " + displayFaction(senderFaction) + " and " + displayFaction(receiverFaction) + "."));
            return;
        }
        if ("break".equalsIgnoreCase(args[0]) || "revoke".equalsIgnoreCase(args[0])) {
            if (args.length != 3 && args.length != 4) {
                throw new WrongUsageException(getCommandUsage(sender));
            }
            String type = args.length == 4 ? parseType(args[1]) : "";
            String senderFaction = parseFaction(args[args.length - 2]);
            String receiverFaction = parseFaction(args[args.length - 1]);
            KOMEAlliance alliance = data.getAlliance(senderFaction, receiverFaction, false);
            EntityPlayerMP actor = sender instanceof EntityPlayerMP ? (EntityPlayerMP) sender : null;
            KOMEAllianceAuthority.Decision breakDecision = actor == null && sender.canCommandSenderUseCommand(2, getCommandName())
                ? KOMEAllianceAuthority.Decision.allow(false)
                : new KOMEAllianceAuthority(data).canBreakAlliance(actor, alliance);
            if (!breakDecision.allowed) {
                throw new WrongUsageException(breakDecision.reason);
            }
            boolean removed;
            if (type.isEmpty()) {
                removed = data.clearAlliance(senderFaction, receiverFaction);
            } else {
                removed = alliance != null && alliance.getTier(type) != KOMEAlliance.NONE;
                if (removed) {
                    alliance.breakTrack(type, sender.getCommandSenderName(), sender.getEntityWorld().getTotalWorldTime());
                    data.markDirty();
                }
            }
            if (removed) {
                syncRelationsForAlliancePair(data, senderFaction, receiverFaction);
                sendAllianceRefreshToParticipants(data, alliance);
            }
            sender.addChatMessage(new ChatComponentText((removed ? "Broke " + (type.isEmpty() ? "the entire " : displayType(type) + " ") + "mutual alliance between " : "No alliance found for ") + displayFaction(senderFaction) + " and " + displayFaction(receiverFaction) + "."));
            return;
        }
        if ("roll".equalsIgnoreCase(args[0])) {
            if (args.length != 4) {
                throw new WrongUsageException(getCommandUsage(sender));
            }
            String type = parseType(args[1]);
            String senderFaction = parseFaction(args[2]);
            String receiverFaction = parseFaction(args[3]);
            KOMEAlliance alliance = data.getAlliance(senderFaction, receiverFaction, false);
            if (alliance == null || !alliance.hasAnyAlliance()) {
                throw new WrongUsageException("No alliance request exists for " + displayFaction(senderFaction) + " -> " + displayFaction(receiverFaction) + ".");
            }
            if (alliance.getTier(type) == KOMEAlliance.PENDING) {
                throw new WrongUsageException("This " + displayType(type) + " alliance request must be accepted before tiers can progress.");
            }
            String contributingFaction = sender instanceof EntityPlayerMP
                ? new KOMEAllianceAuthority(data).getPlayerFaction((EntityPlayerMP) sender) : senderFaction;
            if (!sender.canCommandSenderUseCommand(2, getCommandName()) && !alliance.involves(contributingFaction)) {
                throw new WrongUsageException("Only pledged members of a participating faction may roll this shared quota.");
            }
            int currentTier = alliance.getFactionTier(contributingFaction, type);
            int targetTier = currentTier + 1;
            if (currentTier < 0 || targetTier > KOMEAlliance.maxTier(type)) {
                throw new WrongUsageException("This " + displayType(type) + " track has no next quota tier for "
                    + displayFaction(contributingFaction) + ".");
            }
            String id = KOMEAllianceQuotaPool.assignmentId(type, targetTier);
            if (!alliance.getAssignment(contributingFaction, id).trim().isEmpty()) {
                KOMEAllianceQuotaPool.Requirement existing = KOMEAllianceQuotaPool.parse(alliance.getAssignment(contributingFaction, id));
                sender.addChatMessage(new ChatComponentText(displayType(type) + " T" + targetTier + " quota already rolled for " + displayFaction(contributingFaction) + ": "
                    + (existing == null ? alliance.getAssignment(contributingFaction, id) : existing.display())));
                return;
            }
            KOMEAllianceQuotaPool.Requirement requirement = KOMEAllianceQuotaPool.rollOnce(data, alliance, type, targetTier, contributingFaction);
            if (requirement == null) {
                throw new WrongUsageException("No obtainable registered requirement could be generated for this tier.");
            }
            data.markDirty();
            sender.addChatMessage(new ChatComponentText("Revealed shared " + displayType(type) + " T" + targetTier + " quota for " + displayFaction(contributingFaction) + ": " + requirement.display()));
            sendAllianceRefreshToParticipants(data, alliance);
            return;
        }
        if ("reroll".equalsIgnoreCase(args[0]) || "rerollQuota".equalsIgnoreCase(args[0])) {
            requireStaff(sender);
            if (args.length != 4) {
                throw new WrongUsageException(getCommandUsage(sender));
            }
            String type = parseType(args[1]);
            String senderFaction = parseFaction(args[2]);
            String receiverFaction = parseFaction(args[3]);
            KOMEAlliance alliance = data.getAlliance(senderFaction, receiverFaction, false);
            if (alliance == null || !alliance.hasAnyAlliance()) {
                throw new WrongUsageException("No alliance request exists for " + displayFaction(senderFaction) + " -> " + displayFaction(receiverFaction) + ".");
            }
            int targetTier = Math.min(KOMEAlliance.maxTier(type),
                Math.max(1, alliance.getFactionTier(senderFaction, type) + 1));
            String id = KOMEAllianceQuotaPool.assignmentId(type, targetTier);
            String previous = alliance.getAssignment(senderFaction, id);
            KOMEAllianceQuotaPool.Requirement rerolled = KOMEAllianceQuotaPool.reroll(data, alliance, type, targetTier, senderFaction, (int) (System.currentTimeMillis() & 0x7fffffff));
            if (rerolled == null) {
                throw new WrongUsageException("Quota repair failed: no enabled eligible item is within its configured maximum, or deposited goods could not be preserved for recovery.");
            }
            data.markDirty();
            sender.addChatMessage(new ChatComponentText("Rerolled " + displayType(type) + " alliance quota for " + displayFaction(senderFaction) + " -> " + displayFaction(receiverFaction) + "."));
            if (previous != null && previous.trim().length() > 0) {
                sender.addChatMessage(new ChatComponentText("Previous: " + previous));
            }
            sender.addChatMessage(new ChatComponentText("New: " + rerolled.display()));
            sendAllianceRefreshToParticipants(data, alliance);
            return;
        }
        if ("goods".equalsIgnoreCase(args[0]) || "storage".equalsIgnoreCase(args[0])) {
            if (args.length != 3) {
                throw new WrongUsageException(getCommandUsage(sender));
            }
            EntityPlayerMP player = getCommandSenderAsPlayer(sender);
            String senderFaction = parseFaction(args[1]);
            String receiverFaction = parseFaction(args[2]);
            KOMEAlliance alliance = data.getAlliance(senderFaction, receiverFaction, false);
            if (alliance == null || !alliance.hasAnyAlliance()) {
                throw new WrongUsageException("No alliance request exists for " + displayFaction(senderFaction) + " -> " + displayFaction(receiverFaction) + ".");
            }
            if (!alliance.hasAnyAcceptedAlliance()) {
                throw new WrongUsageException("Alliance goods unlock after at least one alliance type is accepted.");
            }
            requireGoodsLedgerPermission(sender, data, senderFaction, receiverFaction);
            KOMEAllianceGuiHandler.openAllianceLedger(player, alliance, senderFaction);
            player.openGui(KOMEAddon.instance, KOMEAllianceGuiHandler.ALLIANCE_LEDGER, player.worldObj, 0, 0, 0);
            sender.addChatMessage(new ChatComponentText("Opened alliance goods for " + displayFaction(senderFaction) + " -> " + displayFaction(receiverFaction) + ". Quota items are compressed into the ledger."));
            return;
        }
        if ("claimGoods".equalsIgnoreCase(args[0]) || "claim".equalsIgnoreCase(args[0])) {
            if (args.length != 3) {
                throw new WrongUsageException(getCommandUsage(sender));
            }
            EntityPlayerMP player = getCommandSenderAsPlayer(sender);
            String senderFaction = parseFaction(args[1]);
            String receiverFaction = parseFaction(args[2]);
            if (!data.hasFactionKing(receiverFaction)) {
                throw new WrongUsageException(displayFaction(receiverFaction) + " has no recorded king yet, so alliance goods cannot be claimed.");
            }
            if (!data.isFactionKing(receiverFaction, kome.common.KOMEReflection.getEntityUUID(player))) {
                throw new WrongUsageException("Only the receiving faction king can claim alliance goods.");
            }
            KOMEAlliance alliance = data.getAlliance(senderFaction, receiverFaction, false);
            if (alliance == null || !alliance.hasAnyAlliance()) {
                throw new WrongUsageException("No alliance request exists for " + displayFaction(senderFaction) + " -> " + displayFaction(receiverFaction) + ".");
            }
            if (!alliance.hasAnyAcceptedAlliance()) {
                throw new WrongUsageException("Alliance goods can be claimed only after at least one alliance type is accepted.");
            }
            int claimed = claimStoredGoods(player, alliance, senderFaction);
            data.markDirty();
            if (player.openContainer != null) {
                if (player.openContainer instanceof kome.common.gui.KOMEContainerAllianceLedger) {
                    ((kome.common.gui.KOMEContainerAllianceLedger) player.openContainer).refreshLedger();
                }
                player.openContainer.detectAndSendChanges();
            }
            sendAllianceRefreshToParticipants(data, alliance);
            sender.addChatMessage(new ChatComponentText("Claimed " + claimed + " alliance goods stacks/items from " + displayFaction(senderFaction) + " -> " + displayFaction(receiverFaction) + "."));
            return;
        }
        if ("set".equalsIgnoreCase(args[0])) {
            requireStaff(sender);
            if (args.length != 5) {
                throw new WrongUsageException(getCommandUsage(sender));
            }
            String type = KOMEAlliance.normalizeType(args[1]);
            if (!KOMEAlliance.isValidType(type)) {
                throw new WrongUsageException("Unknown alliance type: " + args[1]);
            }
            int tier = parseIntBounded(sender, args[4], 0, KOMEAlliance.maxTier(type));
            String factionA = parseFaction(args[2]);
            String factionB = parseFaction(args[3]);
            if (factionA.equals(factionB)) {
                throw new WrongUsageException("A faction cannot ally with itself.");
            }
            KOMEAlliance alliance = data.getAlliance(factionA, factionB, true);
            alliance.setTier(type, tier, sender.getCommandSenderName(), sender.getEntityWorld().getTotalWorldTime());
            syncRelationsForAlliancePair(data, factionA, factionB);
            data.markDirty();
            sendAllianceRefreshToParticipants(data, alliance);
            sender.addChatMessage(new ChatComponentText("Set mutual " + displayType(type) + " alliance between " + displayFaction(factionA) + " and " + displayFaction(factionB) + " to tier " + tier + "."));
            sender.addChatMessage(new ChatComponentText(getBenefit(type, tier)));
            return;
        }
        if ("clear".equalsIgnoreCase(args[0])) {
            requireStaff(sender);
            if (args.length != 3) {
                throw new WrongUsageException(getCommandUsage(sender));
            }
            String factionA = parseFaction(args[1]);
            String factionB = parseFaction(args[2]);
            boolean removed = data.clearAlliance(factionA, factionB);
            if (removed) {
                syncRelationsForAlliancePair(data, factionA, factionB);
            }
            sender.addChatMessage(new ChatComponentText((removed ? "Cleared" : "No alliance found for") + " " + args[1] + " -> " + args[2] + "."));
            return;
        }
        throw new WrongUsageException(getCommandUsage(sender));
    }

    private void processAllianceConfig(ICommandSender sender, String[] args, KOMEWorldData data) {
        requireStaff(sender);
        if ((args.length == 5 || args.length == 6) && "quota".equalsIgnoreCase(args[1])
                && "item".equalsIgnoreCase(args[2])) {
            KOMEAllianceQuotaPool.EntryView entry = KOMEAllianceQuotaPool.findEntry(data, args[3]);
            if (entry == null) {
                throw new WrongUsageException("Unknown quota-pool item. Use a registry ID such as minecraft:iron_sword or minecraft:wool:0.");
            }
            String setting = args[4].toLowerCase(java.util.Locale.ROOT);
            if (args.length == 5 && "show".equals(setting)) {
                sender.addChatMessage(new ChatComponentText("Quota item " + entry.key + ": weight=" + entry.effortWeight
                    + ", max=" + entry.maximumQuantity + ", enabled=" + entry.enabled + ", tracks="
                    + entry.allowedTracks + ", minimumTier=" + entry.minimumTier + ", category=" + entry.category + "."));
                return;
            }
            if (args.length != 6) {
                throw new WrongUsageException("/alliance config quota item <registry[:meta]> <weight|max|enabled|show> [value]");
            }
            if ("weight".equals(setting)) {
                int weight = parseIntBounded(sender, args[5], 1, 64);
                if (!KOMEAllianceRequirements.isSupportedWeight(weight)) {
                    throw new WrongUsageException("Quota weight must be 1, 2, 4, 8, 16, 32, or 64.");
                }
                data.setAllianceQuotaWeight(entry.key, weight);
            } else if ("max".equals(setting)) {
                data.setAllianceQuotaMaximum(entry.key, parseIntBounded(sender, args[5], 1, 1000000));
            } else if ("enabled".equals(setting)) {
                data.setAllianceQuotaItemEnabled(entry.key, parseOnOff(args[5]));
            } else {
                throw new WrongUsageException("Quota item setting must be weight, max, enabled, or show.");
            }
            KOMEAllianceQuotaPool.validateExistingRequirements(data);
            KOMEAllianceQuotaPool.EntryView updated = KOMEAllianceQuotaPool.findEntry(data, args[3]);
            recordAndRefresh(sender, data, "set quota item " + updated.key + " " + setting + " " + args[5]);
            sender.addChatMessage(new ChatComponentText("Quota item " + updated.key + " now has weight="
                + updated.effortWeight + ", max=" + updated.maximumQuantity + ", enabled=" + updated.enabled
                + ". Open invalid rolls are marked INVALID_REQUIREMENT and can be repaired with /alliance reroll."));
            return;
        }
        if (args.length == 3 && "difficulty".equalsIgnoreCase(args[1])) {
            String difficulty = KOMEAllianceRequirements.normalizeDifficulty(args[2]);
            if (!difficulty.equalsIgnoreCase(args[2])) {
                throw new WrongUsageException("Difficulty must be easy, standard, or hard.");
            }
            data.allianceDifficulty = difficulty;
            KOMEAllianceQuotaPool.reconcileConfiguredQuantities(data);
            recordAndRefresh(sender, data, "set difficulty " + difficulty);
            sender.addChatMessage(new ChatComponentText("Alliance difficulty set to " + difficulty + ". Existing completed tiers remain completed; open quotas were resized."));
            return;
        }
        if (args.length == 3 && "waypointRestriction".equalsIgnoreCase(args[1])) {
            data.waypointRestrictionEnabled = parseOnOff(args[2]);
            recordAndRefresh(sender, data, "set waypointRestriction " + data.waypointRestrictionEnabled);
            sender.addChatMessage(new ChatComponentText("KOME waypoint territory restriction " + (data.waypointRestrictionEnabled ? "enabled" : "disabled") + "."));
            return;
        }
        if (args.length == 4 && "grace".equalsIgnoreCase(args[1])) {
            long duration = parseDuration(args[3]);
            if ("succession".equalsIgnoreCase(args[2])) {
                data.successionGraceDefaultMillis = duration;
            } else if ("contribution".equalsIgnoreCase(args[2])) {
                data.contributionGraceDefaultMillis = duration;
            } else {
                throw new WrongUsageException("Grace type must be succession or contribution.");
            }
            recordAndRefresh(sender, data, "set " + args[2].toLowerCase() + " grace default " + duration + "ms");
            sender.addChatMessage(new ChatComponentText("Default " + args[2].toLowerCase() + " grace set to " + formatDuration(duration) + "."));
            return;
        }
        if (args.length == 6 && "requirement".equalsIgnoreCase(args[1])) {
            String type = parseType(args[2]);
            int tier = parseIntBounded(sender, args[3], 1, KOMEAlliance.maxTier(type));
            String kind = args[4].toLowerCase();
            if ("item".equals(kind)) {
                kind = "items";
            }
            if (!"items".equals(kind) && !"activity".equals(kind) && !"population".equals(kind)) {
                throw new WrongUsageException("Requirement kind must be items, activity, or population.");
            }
            int value = parseIntBounded(sender, args[5], 0, 1000000);
            data.setAllianceRequirement(type, tier, kind, value);
            KOMEAllianceQuotaPool.reconcileConfiguredQuantities(data);
            recordAndRefresh(sender, data, "set requirement " + type + " T" + tier + " " + kind + " " + value);
            sender.addChatMessage(new ChatComponentText("Configured " + displayType(type) + " T" + tier + " " + kind + " base to " + value + ". Completed tiers were not revoked."));
            return;
        }
        throw new WrongUsageException("/alliance config difficulty <easy|standard|hard> | requirement <type> <tier> <items|activity|population> <value> | quota item <registry[:meta]> <weight|max|enabled|show> [value] | waypointRestriction <on|off> | grace <succession|contribution> <duration>");
    }

    private void processWaypointAdmin(ICommandSender sender, String[] args, KOMEWorldData data) {
        requireStaff(sender);
        if (args.length == 4 && "bypass".equalsIgnoreCase(args[1])) {
            EntityPlayerMP player = getPlayer(sender, args[2]);
            boolean enabled = parseOnOff(args[3]);
            data.setWaypointRestrictionBypass(kome.common.KOMEReflection.getEntityUUID(player), enabled);
            recordAndRefresh(sender, data, "set waypoint bypass for " + player.getCommandSenderName() + " " + enabled);
            sender.addChatMessage(new ChatComponentText("Waypoint restriction bypass " + (enabled ? "enabled" : "disabled") + " for " + player.getCommandSenderName() + "."));
            return;
        }
        if ((args.length == 3 || args.length == 4) && "check".equalsIgnoreCase(args[1])) {
            EntityPlayerMP player = getPlayer(sender, args[2]);
            LOTRAbstractWaypoint waypoint;
            if (args.length == 3 || "current".equalsIgnoreCase(args[3])) {
                waypoint = LOTRLevelData.getData(player).getTargetFTWaypoint();
            } else {
                waypoint = LOTRWaypoint.waypointForName(args[3]);
            }
            if (waypoint == null) {
                throw new WrongUsageException("No queued current waypoint or matching explicit LOTR waypoint was found.");
            }
            boolean nativeEligible = waypoint.hasPlayerUnlocked(player);
            boolean structuralEligible = KOMEWaypointAccessService.hasNativeProgression(player, waypoint);
            KOMEWaypointAccessService.Decision decision = KOMEWaypointAccessService.evaluatePlayer(player, waypoint, structuralEligible);
            sender.addChatMessage(new ChatComponentText("Waypoint check for " + player.getCommandSenderName() + " -> " + waypoint.getDisplayName()
                + ": tile=" + (decision.tileId.length() == 0 ? "unmapped" : decision.tileId)
                + ", owner=" + (decision.tileOwner.length() == 0 ? "unclaimed" : displayFaction(decision.tileOwner))
                + ", playerFaction=" + (decision.playerFaction.length() == 0 ? "none" : displayFaction(decision.playerFaction))
                + ", civilTier=" + decision.civilTier + "."));
            sender.addChatMessage(new ChatComponentText("Native LOTR=" + nativeEligible + ", native progression=" + structuralEligible
                + ", KOME territory=" + decision.territoryAllowed + ", final=" + decision.finalAllowed + ": " + decision.reason));
            return;
        }
        throw new WrongUsageException("/alliance waypoint bypass <player> <on|off> | check <player> [current|waypointCode]");
    }

    private void processGraceAdmin(ICommandSender sender, String[] args, KOMEWorldData data) {
        requireStaff(sender);
        if (args.length < 4) {
            throw new WrongUsageException("/alliance grace status|set|expire <factionA> <factionB> ...");
        }
        String action = args[1].toLowerCase();
        String factionA = parseFaction(args[2]);
        String factionB = parseFaction(args[3]);
        KOMEAlliance alliance = data.getAlliance(factionA, factionB, false);
        if (alliance == null || !alliance.hasAnyAlliance()) {
            throw new WrongUsageException("No alliance exists for that faction pair.");
        }
        long now = System.currentTimeMillis();
        if ("status".equals(action) && (args.length == 4 || args.length == 5)) {
            if (args.length == 5) {
                KOMEAllianceFactionLedger ledger = requireAffectedLedger(alliance, parseFaction(args[4]));
                sendGraceStatus(sender, alliance, ledger, now);
            } else {
                sendGraceStatus(sender, alliance, alliance.getFactionLedger(alliance.factionA), now);
                sendGraceStatus(sender, alliance, alliance.getFactionLedger(alliance.factionB), now);
            }
            return;
        }
        if ("set".equals(action) && args.length == 7) {
            String affectedFaction = parseFaction(args[4]);
            requireAffectedLedger(alliance, affectedFaction);
            long duration = parseDuration(args[6]);
            KOMEAllianceGraceService.Change change;
            try {
                change = KOMEAllianceGraceService.set(alliance, affectedFaction, args[5], now, duration);
            } catch (IllegalArgumentException exception) {
                throw new WrongUsageException(exception.getMessage());
            }
            recordAndRefresh(sender, data, graceAudit(alliance, change));
            sender.addChatMessage(new ChatComponentText("Set " + displayFaction(change.affectedFaction) + "'s "
                + change.type + " grace to " + formatDuration(duration) + ". The other faction side was unchanged."));
            return;
        }
        if ("expire".equals(action) && args.length == 6) {
            String affectedFaction = parseFaction(args[4]);
            requireAffectedLedger(alliance, affectedFaction);
            KOMEAllianceGraceService.Change change;
            try {
                change = KOMEAllianceGraceService.expire(data, alliance, affectedFaction, args[5], now,
                    sender.getEntityWorld().getTotalWorldTime());
            } catch (IllegalArgumentException exception) {
                throw new WrongUsageException(exception.getMessage());
            }
            recordAndRefresh(sender, data, graceAudit(alliance, change));
            sender.addChatMessage(new ChatComponentText("Force-expired " + displayFaction(change.affectedFaction)
                + "'s " + change.type + " grace and reconciled that faction's tiers. The other faction side was unchanged."));
            return;
        }
        throw new WrongUsageException("/alliance grace status <factionA> <factionB> [affectedFaction] | set <factionA> <factionB> <affectedFaction> <succession|contribution> <duration> | expire <factionA> <factionB> <affectedFaction> <succession|contribution>");
    }

    private KOMEAllianceFactionLedger requireAffectedLedger(KOMEAlliance alliance, String affectedFaction) {
        try {
            return KOMEAllianceGraceService.requireLedger(alliance, affectedFaction);
        } catch (IllegalArgumentException exception) {
            throw new WrongUsageException(exception.getMessage());
        }
    }

    private String graceAudit(KOMEAlliance alliance, KOMEAllianceGraceService.Change change) {
        return change.reason + " pair=" + alliance.getPairKey() + " affected=" + change.affectedFaction
            + " type=" + change.type + " formerDeadline=" + change.formerDeadline
            + " newDeadline=" + change.newDeadline;
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "request", "accept", "break", "config", "waypoint", "grace", "roll", "reroll", "goods", "claimGoods", "get", "set", "clear", "list", "benefits");
        }
        if (args.length == 2 && "config".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "difficulty", "requirement", "quota", "waypointRestriction", "grace");
        }
        if (args.length == 2 && "waypoint".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "bypass", "check");
        }
        if (args.length == 2 && "grace".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "status", "set", "expire");
        }
        if (args.length == 3 && "config".equalsIgnoreCase(args[0]) && "difficulty".equalsIgnoreCase(args[1])) {
            return getListOfStringsMatchingLastWord(args, "easy", "standard", "hard");
        }
        if (args.length == 3 && "config".equalsIgnoreCase(args[0]) && "waypointRestriction".equalsIgnoreCase(args[1])) {
            return getListOfStringsMatchingLastWord(args, "on", "off");
        }
        if (args.length == 3 && "config".equalsIgnoreCase(args[0]) && "grace".equalsIgnoreCase(args[1])) {
            return getListOfStringsMatchingLastWord(args, "succession", "contribution");
        }
        if (args.length == 3 && "config".equalsIgnoreCase(args[0]) && "requirement".equalsIgnoreCase(args[1])) {
            return getListOfStringsMatchingLastWord(args, KOMEAlliance.CIVIL, KOMEAlliance.TRADE, KOMEAlliance.MILITARY);
        }
        if (args.length == 5 && "config".equalsIgnoreCase(args[0]) && "requirement".equalsIgnoreCase(args[1])) {
            return getListOfStringsMatchingLastWord(args, "items", "activity", "population");
        }
        if (args.length == 5 && "config".equalsIgnoreCase(args[0]) && "quota".equalsIgnoreCase(args[1])
                && "item".equalsIgnoreCase(args[2])) {
            return getListOfStringsMatchingLastWord(args, "weight", "max", "enabled", "show");
        }
        if (args.length == 6 && "config".equalsIgnoreCase(args[0]) && "quota".equalsIgnoreCase(args[1])
                && "item".equalsIgnoreCase(args[2]) && "weight".equalsIgnoreCase(args[4])) {
            return getListOfStringsMatchingLastWord(args, "1", "2", "4", "8", "16", "32", "64");
        }
        if (args.length == 6 && "config".equalsIgnoreCase(args[0]) && "quota".equalsIgnoreCase(args[1])
                && "item".equalsIgnoreCase(args[2]) && "enabled".equalsIgnoreCase(args[4])) {
            return getListOfStringsMatchingLastWord(args, "on", "off");
        }
        if (args.length == 6 && "grace".equalsIgnoreCase(args[0])
                && ("set".equalsIgnoreCase(args[1]) || "expire".equalsIgnoreCase(args[1]))) {
            return getListOfStringsMatchingLastWord(args, "succession", "contribution");
        }
        if ("grace".equalsIgnoreCase(args[0]) && args.length >= 3 && args.length <= 5) {
            List names = LOTRFaction.getPlayableAlignmentFactionNames();
            return getListOfStringsMatchingLastWord(args, (String[]) names.toArray(new String[names.size()]));
        }
        if (args.length == 4 && "waypoint".equalsIgnoreCase(args[0]) && "bypass".equalsIgnoreCase(args[1])) {
            return getListOfStringsMatchingLastWord(args, "on", "off");
        }
        if (args.length == 2 && "set".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, KOMEAlliance.CIVIL, KOMEAlliance.MILITARY, KOMEAlliance.TRADE);
        }
        if (args.length == 2 && ("request".equalsIgnoreCase(args[0]) || "accept".equalsIgnoreCase(args[0]) || "break".equalsIgnoreCase(args[0]) || "revoke".equalsIgnoreCase(args[0]))) {
            return getListOfStringsMatchingLastWord(args, KOMEAlliance.CIVIL, KOMEAlliance.MILITARY, KOMEAlliance.TRADE);
        }
        if (args.length == 2 && "roll".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, KOMEAlliance.CIVIL, KOMEAlliance.MILITARY, KOMEAlliance.TRADE);
        }
        if (args.length == 2 && ("reroll".equalsIgnoreCase(args[0]) || "rerollQuota".equalsIgnoreCase(args[0]))) {
            return getListOfStringsMatchingLastWord(args, KOMEAlliance.CIVIL, KOMEAlliance.MILITARY, KOMEAlliance.TRADE);
        }
        if (isFactionArgument(args)) {
            List names = LOTRFaction.getPlayableAlignmentFactionNames();
            return getListOfStringsMatchingLastWord(args, (String[]) names.toArray(new String[names.size()]));
        }
        return null;
    }

    private boolean isFactionArgument(String[] args) {
        return args.length == 2 && ("get".equalsIgnoreCase(args[0]) || "clear".equalsIgnoreCase(args[0]) || "break".equalsIgnoreCase(args[0]) || "revoke".equalsIgnoreCase(args[0]) || "goods".equalsIgnoreCase(args[0]) || "claimGoods".equalsIgnoreCase(args[0]) || "claim".equalsIgnoreCase(args[0]) || "list".equalsIgnoreCase(args[0]))
            || args.length == 3 && ("get".equalsIgnoreCase(args[0]) || "clear".equalsIgnoreCase(args[0]) || "break".equalsIgnoreCase(args[0]) || "revoke".equalsIgnoreCase(args[0]) || "goods".equalsIgnoreCase(args[0]) || "claimGoods".equalsIgnoreCase(args[0]) || "claim".equalsIgnoreCase(args[0]) || "set".equalsIgnoreCase(args[0]) || "roll".equalsIgnoreCase(args[0]) || "reroll".equalsIgnoreCase(args[0]) || "rerollQuota".equalsIgnoreCase(args[0]) || "request".equalsIgnoreCase(args[0]) || "accept".equalsIgnoreCase(args[0]))
            || args.length == 4 && ("set".equalsIgnoreCase(args[0]) || "roll".equalsIgnoreCase(args[0]) || "reroll".equalsIgnoreCase(args[0]) || "rerollQuota".equalsIgnoreCase(args[0]) || "request".equalsIgnoreCase(args[0]) || "accept".equalsIgnoreCase(args[0]) || "break".equalsIgnoreCase(args[0]) || "revoke".equalsIgnoreCase(args[0]));
    }

    private int claimStoredGoods(EntityPlayerMP player, KOMEAlliance alliance, String contributingFaction) {
        int claimed = 0;
        for (int i = 0; i < KOMEAlliance.STORAGE_SLOTS; i++) {
            ItemStack stack = alliance.getStorage(contributingFaction, i);
            if (stack == null) {
                continue;
            }
            ItemStack toGive = stack.copy();
            if (!player.inventory.addItemStackToInventory(toGive)) {
                player.dropPlayerItemWithRandomChoice(toGive, false);
            }
            alliance.setStorage(contributingFaction, i, null);
            claimed++;
        }
        KOMEAllianceFactionLedger ledger = alliance.getFactionLedger(contributingFaction);
        while (ledger != null && ledger.getRecoveryStackCount() > 0) {
            ItemStack recovery = ledger.removeRecoveryStack(0);
            if (recovery != null) {
                ItemStack toGive = recovery.copy();
                if (!player.inventory.addItemStackToInventory(toGive)) {
                    player.dropPlayerItemWithRandomChoice(toGive, false);
                }
                claimed++;
            }
        }
        if (ledger != null) {
            for (String id : ledger.getClaimIds()) {
                claimed += claimVirtualGoods(player, alliance, contributingFaction, id);
            }
        }
        player.inventoryContainer.detectAndSendChanges();
        return claimed;
    }

    private int claimVirtualGoods(EntityPlayerMP player, KOMEAlliance alliance, String contributingFaction, String id) {
        ItemStack sample = alliance.getClaimSample(contributingFaction, id);
        int amount = alliance.getClaimAmount(contributingFaction, id);
        if (sample == null || amount <= 0) {
            return 0;
        }
        int claimed = 0;
        int max = Math.max(1, sample.getMaxStackSize());
        while (amount > 0) {
            ItemStack stack = sample.copy();
            stack.stackSize = Math.min(max, amount);
            amount -= stack.stackSize;
            if (!player.inventory.addItemStackToInventory(stack)) {
                player.dropPlayerItemWithRandomChoice(stack, false);
            }
            claimed++;
        }
        alliance.clearClaimGoods(contributingFaction, id);
        return claimed;
    }

    private void listAlliances(ICommandSender sender, KOMEWorldData data, String faction) {
        if (sender instanceof EntityPlayerMP && !sender.canCommandSenderUseCommand(2, getCommandName())) {
            String viewerFaction = new KOMEAllianceAuthority(data).getPlayerFaction((EntityPlayerMP) sender);
            if (faction.length() > 0 && !faction.equals(viewerFaction)) {
                throw new WrongUsageException("You may list only alliances involving your pledged faction.");
            }
            faction = viewerFaction;
        }
        List<String> lines = new ArrayList<>();
        for (KOMEAlliance alliance : data.alliances.values()) {
            if (alliance == null || !alliance.hasAnyAlliance()) {
                continue;
            }
            if (!faction.isEmpty() && !faction.equals(alliance.factionA) && !faction.equals(alliance.factionB)) {
                continue;
            }
            lines.add(formatAlliance(alliance));
        }
        Collections.sort(lines);
        if (lines.isEmpty()) {
            sender.addChatMessage(new ChatComponentText(faction.isEmpty() ? "No alliances recorded." : "No alliances recorded for " + displayFaction(faction) + "."));
            return;
        }
        sender.addChatMessage(new ChatComponentText("Recorded alliances:"));
        for (String line : lines) {
            sender.addChatMessage(new ChatComponentText(line));
        }
    }

    private void sendAlliance(ICommandSender sender, KOMEAlliance alliance, String factionA, String factionB) {
        if (sender instanceof EntityPlayerMP && alliance != null
                && !new KOMEAllianceAuthority(KOMEWorldData.get(sender.getEntityWorld())).canViewAlliance((EntityPlayerMP) sender, alliance).allowed) {
            throw new WrongUsageException("You may view only alliances involving your pledged faction.");
        }
        if (alliance == null || !alliance.hasAnyAlliance()) {
            sender.addChatMessage(new ChatComponentText(displayFaction(factionA) + " -> " + displayFaction(factionB) + ": no alliance"));
            return;
        }
        sender.addChatMessage(new ChatComponentText(formatAlliance(alliance)));
    }

    private void sendAllianceRefresh(ICommandSender sender, KOMEWorldData data) {
        if (sender instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) sender;
            KOMEPacketHandler.network.sendTo(new KOMEPacketAllianceData(KOMEAllianceRecordBuilder.build(data, player)), player);
        }
    }

    public static void sendAllianceRefreshToParticipants(KOMEWorldData data, KOMEAlliance alliance) {
        if (data == null || alliance == null) {
            return;
        }
        long now = System.currentTimeMillis();
        KOMEWarService.reconcileAutomaticMilitarySupport(data, now, "Alliance lifecycle changed");
        KOMEWartimeStewardshipService.revalidateAll(data, now, "Alliance lifecycle changed");
        KOMECommandTroops.revalidateTemporaryControllers(data, now, "Alliance lifecycle changed");
        net.minecraft.server.MinecraftServer server = cpw.mods.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null || server.getConfigurationManager() == null) {
            return;
        }
        KOMEAllianceAuthority authority = new KOMEAllianceAuthority(data);
        for (Object object : server.getConfigurationManager().playerEntityList) {
            if (object instanceof EntityPlayerMP) {
                EntityPlayerMP player = (EntityPlayerMP) object;
                if (authority.canViewAlliance(player, alliance).allowed) {
                    KOMEPacketHandler.network.sendTo(new KOMEPacketAllianceData(KOMEAllianceRecordBuilder.build(data, player)), player);
                }
            }
        }
    }

    public static void sendAllianceRefreshToAll(KOMEWorldData data) {
        if (data == null) {
            return;
        }
        net.minecraft.server.MinecraftServer server = cpw.mods.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null || server.getConfigurationManager() == null) {
            return;
        }
        for (Object object : server.getConfigurationManager().playerEntityList) {
            if (object instanceof EntityPlayerMP) {
                EntityPlayerMP player = (EntityPlayerMP) object;
                KOMEPacketHandler.network.sendTo(new KOMEPacketAllianceData(KOMEAllianceRecordBuilder.build(data, player)), player);
            }
        }
    }

    private void recordAndRefresh(ICommandSender sender, KOMEWorldData data, String action) {
        data.recordAllianceAdminAction(sender.getCommandSenderName(), action);
        data.markDirty();
        sendAllianceRefreshToAll(data);
    }

    private void sendGraceStatus(ICommandSender sender, KOMEAlliance alliance, KOMEAllianceFactionLedger ledger, long now) {
        if (ledger == null) {
            return;
        }
        String contribution = ledger.graceEndMillis <= 0L ? "inactive"
            : ledger.isContributionGraceActive(now) ? formatDuration(ledger.graceEndMillis - now) + " remaining" : "expired";
        String succession = ledger.successionEndMillis <= 0L ? "inactive"
            : ledger.isSuccessionActive(now) ? formatDuration(ledger.successionEndMillis - now) + " remaining" : "expired";
        sender.addChatMessage(new ChatComponentText(displayFaction(ledger.faction) + " in " + alliance.getPairKey()
            + ": contribution=" + contribution + (ledger.graceReason.length() == 0 ? "" : " (" + ledger.graceReason + ")")
            + ", succession=" + succession + "."));
    }

    private boolean parseOnOff(String value) {
        if ("on".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value)) {
            return true;
        }
        if ("off".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value) || "no".equalsIgnoreCase(value)) {
            return false;
        }
        throw new WrongUsageException("Value must be on or off.");
    }

    private long parseDuration(String value) {
        try {
            return KOMEAllianceRequirements.parseDurationMillis(value);
        } catch (IllegalArgumentException exception) {
            throw new WrongUsageException(exception.getMessage() + " Example: 14d.");
        }
    }

    private String formatDuration(long millis) {
        long seconds = Math.max(0L, millis) / 1000L;
        if (seconds % 86400L == 0L) {
            return seconds / 86400L + "d";
        }
        if (seconds % 3600L == 0L) {
            return seconds / 3600L + "h";
        }
        if (seconds % 60L == 0L) {
            return seconds / 60L + "m";
        }
        return seconds + "s";
    }

    private String formatAlliance(KOMEAlliance alliance) {
        return "[" + alliance.getPairKey() + "] "
            + displayFaction(alliance.factionA) + " <-> " + displayFaction(alliance.factionB)
            + ": civil " + displayTier(alliance.civilTier)
            + ", military " + displayTier(alliance.militaryTier)
            + ", trade " + displayTier(alliance.tradeTier);
    }

    private static String displayTier(int tier) {
        return tier == KOMEAlliance.PENDING ? "pending" : tier < 0 ? "none" : "T" + tier;
    }

    private void sendBenefits(ICommandSender sender) {
        sender.addChatMessage(new ChatComponentText("Civil: T0 alliance begins, T1 use faction WPs, T2 hire farmhands."));
        sender.addChatMessage(new ChatComponentText("Military: T0 established, T1 allied combat recruit, T2 army passage, T3 voluntary delegation plus same-side active-war stewardship for kingless factions."));
        sender.addChatMessage(new ChatComponentText("Trade: T0 established, T1 mutual goods exchange, T2 "
            + KOMEAllianceBenefits.display(KOMEAlliance.TRADE, 2)));
    }

    private static String getBenefit(String type, int tier) {
        if (KOMEAlliance.CIVIL.equals(type)) {
            return tier == 0 ? "Benefit: alliance begins." : tier == 1 ? "Benefit: may use faction waypoints." : "Benefit: may hire farmhands.";
        }
        if (KOMEAlliance.MILITARY.equals(type)) {
            if (tier == 0) {
                return "Benefit: alliance begins.";
            }
            if (tier == 1) {
                return "Benefit: may hire 1 unit from that faction.";
            }
            if (tier == 2) {
                return "Benefit: may attack through that faction.";
            }
            if (tier == 3) {
                return "Benefit: may command the faction's armies while with units of that faction.";
            }
            return "Benefit: voluntary delegation for a king, or same-side active-war stewardship of eligible kingless native forces.";
        }
        return tier == 0 ? "Established: accepted base alliance; no tier benefit."
            : "Benefit: " + KOMEAllianceBenefits.display(type, tier);
    }

    private static String displayType(String type) {
        return Character.toUpperCase(type.charAt(0)) + type.substring(1);
    }

    private static String parseType(String value) {
        String type = KOMEAlliance.normalizeType(value);
        if (!KOMEAlliance.isValidType(type)) {
            throw new WrongUsageException("Unknown alliance type: " + value);
        }
        return type;
    }

    private void setInitialTiers(KOMEAlliance alliance, String type, int status, ICommandSender sender) {
        for (String impliedType : impliedAllianceTypes(type)) {
            if (alliance.getTier(impliedType) == KOMEAlliance.NONE) {
                alliance.setTier(impliedType, status, sender.getCommandSenderName(), sender.getEntityWorld().getTotalWorldTime());
            }
        }
    }

    private void ensureFoodQuota(KOMEAlliance alliance, String contributingFaction, String id, ICommandSender sender) {
        if (alliance.getAssignment(contributingFaction, id).trim().isEmpty()) {
            rollFoodQuota(alliance, contributingFaction, id, sender, false);
        }
    }

    private void rollFoodQuota(KOMEAlliance alliance, String contributingFaction, String id, ICommandSender sender, boolean resetDelivered) {
        long seed = alliance.getPairKey().hashCode() * 31L + KOMEAlliance.normalizeFactionKey(contributingFaction).hashCode() * 17L + id.hashCode();
        alliance.setAssignment(contributingFaction, id, KOMEProgressionTaskGenerator.roll("serf.food_quota_1", seed));
        if (resetDelivered) {
            alliance.setDelivered(contributingFaction, id, 0);
            alliance.clearClaimGoods(contributingFaction, id);
        }
    }

    private void acceptTiers(KOMEAlliance alliance, String type, ICommandSender sender) {
        for (String impliedType : impliedAllianceTypes(type)) {
            if (alliance.getTier(impliedType) == KOMEAlliance.PENDING) {
                alliance.setTier(impliedType, 0, sender.getCommandSenderName(), sender.getEntityWorld().getTotalWorldTime());
            }
        }
    }

    private String[] impliedAllianceTypes(String type) {
        if (KOMEAlliance.MILITARY.equals(type)) {
            return new String[] {KOMEAlliance.CIVIL, KOMEAlliance.TRADE, KOMEAlliance.MILITARY};
        }
        if (KOMEAlliance.TRADE.equals(type)) {
            return new String[] {KOMEAlliance.CIVIL, KOMEAlliance.TRADE};
        }
        return new String[] {KOMEAlliance.CIVIL};
    }

    private static String parseFaction(String value) {
        LOTRFaction resolved = KOMEAlliance.findLotrFaction(value);
        if (resolved != null && resolved.isPlayableAlignmentFaction()) {
            return KOMEAlliance.normalizeFactionKey(resolved.codeName());
        }
        if (KOMEAlliance.normalizeFactionKey(value).length() == 0) {
            return "";
        }
        throw new WrongUsageException("Unknown faction: " + value);
    }

    private static String displayFaction(String key) {
        return KOMEAlliance.displayFactionName(key);
    }

    private static boolean isEnemyAlliance(String factionA, String factionB) {
        LOTRFactionRelations.Relation relation = getCurrentRelation(factionA, factionB);
        return relation == LOTRFactionRelations.Relation.ENEMY || relation == LOTRFactionRelations.Relation.MORTAL_ENEMY;
    }

    private static LOTRFactionRelations.Relation getCurrentRelation(String factionA, String factionB) {
        LOTRFaction a = KOMEAlliance.findLotrFaction(factionA);
        LOTRFaction b = KOMEAlliance.findLotrFaction(factionB);
        return a == null || b == null ? LOTRFactionRelations.Relation.NEUTRAL : LOTRFactionRelations.getRelations(a, b);
    }

    private static LOTRFactionRelations.Relation getDefaultRelation(String factionA, String factionB) {
        LOTRFaction a = KOMEAlliance.findLotrFaction(factionA);
        LOTRFaction b = KOMEAlliance.findLotrFaction(factionB);
        if (a == null || b == null || a == b) {
            return LOTRFactionRelations.Relation.NEUTRAL;
        }
        LOTRFactionRelations.Relation relation = getDefaultRelationReflective(a, b);
        return relation == null ? LOTRFactionRelations.Relation.NEUTRAL : relation;
    }

    private static LOTRFactionRelations.Relation getDefaultRelationReflective(LOTRFaction a, LOTRFaction b) {
        try {
            java.lang.reflect.Method method = LOTRFactionRelations.class.getDeclaredMethod("getFromDefaultMap", LOTRFactionRelations.FactionPair.class);
            method.setAccessible(true);
            Object value = method.invoke(null, new LOTRFactionRelations.FactionPair(a, b));
            return value instanceof LOTRFactionRelations.Relation ? (LOTRFactionRelations.Relation) value : null;
        } catch (Throwable ignored) {
            return LOTRFactionRelations.getRelations(a, b);
        }
    }

    private static void overrideRelationsForAlliance(String factionA, String factionB, String type) {
        LOTRFaction a = KOMEAlliance.findLotrFaction(factionA);
        LOTRFaction b = KOMEAlliance.findLotrFaction(factionB);
        if (a != null && b != null) {
            LOTRFactionRelations.Relation relation = strongestRelation(getDefaultRelation(factionA, factionB), relationForAllianceType(type));
            LOTRFactionRelations.overrideRelations(a, b, relation);
        }
    }

    public static void syncRelationsForAlliancePair(KOMEWorldData data, String factionA, String factionB) {
        LOTRFaction a = KOMEAlliance.findLotrFaction(factionA);
        LOTRFaction b = KOMEAlliance.findLotrFaction(factionB);
        if (a == null || b == null || a == b) {
            return;
        }
        LOTRFactionRelations.Relation relation = getDefaultRelation(factionA, factionB);
        relation = strongestRelation(relation, strongestAllianceRelation(data.getAlliance(factionA, factionB, false)));
        LOTRFactionRelations.overrideRelations(a, b, relation);
    }

    public static void reapplyAllAllianceRelations(KOMEWorldData data) {
        if (data == null) {
            return;
        }
        for (KOMEAlliance alliance : data.alliances.values()) {
            if (alliance != null) {
                syncRelationsForAlliancePair(data, alliance.factionA, alliance.factionB);
            }
        }
    }

    public static void reconcileKinglessPendingAlliances(KOMEWorldData data, long worldTime) {
        if (data == null) {
            return;
        }
        boolean changed = false;
        for (KOMEAlliance alliance : data.alliances.values()) {
            if (alliance == null) {
                continue;
            }
            boolean allianceChanged = false;
            String[] types = new String[] {KOMEAlliance.CIVIL, KOMEAlliance.TRADE, KOMEAlliance.MILITARY};
            for (int i = 0; i < types.length; i++) {
                String type = types[i];
                String receiver = alliance.getPendingReceiver(type);
                String requester = alliance.getRequestedBy(type);
                if (alliance.getTier(type) == KOMEAlliance.PENDING && receiver.length() > 0
                        && !data.hasFactionKing(receiver) && data.hasFactionKing(requester)
                        && KOMEAllianceAuthority.relationAllows(type, KOMEAllianceAuthority.getDefaultRelation(requester, receiver))) {
                    alliance.acceptTrack(type, "Automatic acceptance: no receiving king", worldTime);
                    KOMEAllianceFactionLedger waived = alliance.getFactionLedger(receiver);
                    if (waived != null) {
                        waived.kinglessWaived = true;
                    }
                    allianceChanged = true;
                }
            }
            if (allianceChanged) {
                syncRelationsForAlliancePair(data, alliance.factionA, alliance.factionB);
                changed = true;
            }
        }
        if (changed) {
            data.markDirty();
        }
    }

    private static LOTRFactionRelations.Relation strongestAllianceRelation(KOMEAlliance alliance) {
        if (alliance == null) {
            return null;
        }
        if (alliance.militaryTier >= 0) {
            return LOTRFactionRelations.Relation.ALLY;
        }
        if (alliance.tradeTier >= 0) {
            return LOTRFactionRelations.Relation.FRIEND;
        }
        if (alliance.civilTier >= 0) {
            return LOTRFactionRelations.Relation.NEUTRAL;
        }
        return null;
    }

    private static LOTRFactionRelations.Relation strongestRelation(LOTRFactionRelations.Relation left, LOTRFactionRelations.Relation right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return relationStrength(right) > relationStrength(left) ? right : left;
    }

    private static int relationStrength(LOTRFactionRelations.Relation relation) {
        if (relation == LOTRFactionRelations.Relation.ALLY) {
            return 3;
        }
        if (relation == LOTRFactionRelations.Relation.FRIEND) {
            return 2;
        }
        if (relation == LOTRFactionRelations.Relation.NEUTRAL) {
            return 1;
        }
        return 0;
    }

    private static LOTRFactionRelations.Relation relationForAllianceType(String type) {
        if (KOMEAlliance.MILITARY.equals(type)) {
            return LOTRFactionRelations.Relation.ALLY;
        }
        if (KOMEAlliance.TRADE.equals(type)) {
            return LOTRFactionRelations.Relation.FRIEND;
        }
        return LOTRFactionRelations.Relation.NEUTRAL;
    }

    private static String relationName(LOTRFactionRelations.Relation relation) {
        if (relation == LOTRFactionRelations.Relation.MORTAL_ENEMY) {
            return "Mortal Enemy";
        }
        if (relation == LOTRFactionRelations.Relation.ENEMY) {
            return "Enemy";
        }
        if (relation == LOTRFactionRelations.Relation.FRIEND) {
            return "Friend";
        }
        if (relation == LOTRFactionRelations.Relation.ALLY) {
            return "Ally";
        }
        return "Neutral";
    }

    private static String parseFactionLenient(String value) {
        try {
            return parseFaction(value);
        } catch (RuntimeException e) {
            return value;
        }
    }

    private void requireBreakPermission(ICommandSender sender, KOMEWorldData data, String senderFaction, String receiverFaction) {
        if (sender.canCommandSenderUseCommand(2, getCommandName())) {
            return;
        }
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        String pledged = getPlayerFaction(data, player);
        if (!senderFaction.equals(pledged) && !data.isFactionKing(receiverFaction, kome.common.KOMEReflection.getEntityUUID(player))) {
            throw new WrongUsageException("Only staff, the sender faction, or the receiving faction king can break this alliance.");
        }
    }

    private void requireGoodsDepositPermission(ICommandSender sender, KOMEWorldData data, String senderFaction) {
        if (sender.canCommandSenderUseCommand(2, getCommandName())) {
            return;
        }
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        if (!senderFaction.equals(getPlayerFaction(data, player))) {
            throw new WrongUsageException("Only members of the sending faction can deposit alliance goods.");
        }
    }

    private void requireGoodsLedgerPermission(ICommandSender sender, KOMEWorldData data, String senderFaction, String receiverFaction) {
        if (sender.canCommandSenderUseCommand(2, getCommandName())) {
            return;
        }
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        String playerFaction = getPlayerFaction(data, player);
        if (senderFaction.equals(playerFaction) || data.isFactionKing(receiverFaction, kome.common.KOMEReflection.getEntityUUID(player))) {
            return;
        }
        throw new WrongUsageException("Only members of the sending faction or the receiving faction king can open alliance goods.");
    }

    private String getPlayerFaction(KOMEWorldData data, EntityPlayerMP player) {
        LOTRFaction pledge = LOTRLevelData.getData(player).getPledgeFaction();
        return pledge == null ? "" : KOMEAlliance.normalizeFactionKey(pledge.codeName());
    }

    private void requireStaff(ICommandSender sender) {
        if (!sender.canCommandSenderUseCommand(2, getCommandName())) {
            throw new WrongUsageException("You do not have permission to change alliances.");
        }
    }
}
