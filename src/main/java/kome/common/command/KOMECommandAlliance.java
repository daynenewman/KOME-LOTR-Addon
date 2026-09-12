package kome.common.command;

import kome.common.KOMEAddon;
import kome.common.data.KOMEAlliance;
import kome.common.data.KOMEAllianceAuthority;
import kome.common.data.KOMEArmyCompany;
import kome.common.data.KOMEAllianceFactionLedger;
import kome.common.data.KOMEAllianceInventory;
import kome.common.data.KOMEAllianceRecordBuilder;
import kome.common.data.KOMEAllianceRequirements;
import kome.common.data.KOMEAllianceQuotaPool;
import kome.common.data.KOMEAllianceProgressionService;
import kome.common.data.KOMEConquestTile;
import kome.common.data.KOMEConquestTileDefaults;
import kome.common.data.KOMEHiredUnitRecord;
import kome.common.data.KOMEPopulationType;
import kome.common.data.KOMEPlayerTilePopulationAllocation;
import kome.common.data.KOMETilePopulation;
import kome.common.data.KOMEWorldData;
import kome.common.data.KOMEDiplomacyService;
import kome.common.data.KOMEWarService;
import kome.common.data.KOMEWartimeStewardshipService;
import kome.common.gui.KOMEAllianceGuiHandler;
import kome.common.network.KOMEPacketAllianceData;
import kome.common.network.KOMEPacketHandler;
import lotr.common.LOTRLevelData;
import lotr.common.fac.LOTRFaction;
import lotr.common.fac.LOTRFactionRelations;
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
        return "/alliance list [faction] | get <factionA> <factionB> | request <from> <to> <friends|allies> | accept <from> <to> | cancel <from> <to>";
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
        if ("config".equalsIgnoreCase(args[0])
                || "benefits".equalsIgnoreCase(args[0])) {
            throw new WrongUsageException(
                "Legacy Stage and quota diplomacy commands have been retired.");
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

            String factionA = parseFaction(args[1]);
            String factionB = parseFaction(args[2]);

            sendDiplomacy(sender, data, factionA, factionB);
            return;
        }
        if ("request".equalsIgnoreCase(args[0])) {
            if (args.length != 4) {
                throw new WrongUsageException(getCommandUsage(sender));
            }
            String senderFaction = parseFaction(args[1]);
            String receiverFaction = parseFaction(args[2]);
            kome.common.data.KOMEDiplomacyRelation target = kome.common.data.KOMEDiplomacyRelation.parse(args[3]); EntityPlayerMP actor=sender instanceof EntityPlayerMP?(EntityPlayerMP)sender:null;
            KOMEDiplomacyService.Result result=KOMEDiplomacyService.requestIncrease(data,senderFaction,receiverFaction,target,actor==null?null:kome.common.KOMEReflection.getEntityUUID(actor),System.currentTimeMillis()); if(!result.accepted)throw new WrongUsageException(result.reason); sender.addChatMessage(new ChatComponentText("Diplomacy request sent: "+target.displayName+"."));
            return;
        }
        if ("accept".equalsIgnoreCase(args[0])) {
            if (args.length != 3) {
                throw new WrongUsageException(getCommandUsage(sender));
            }
            String senderFaction = parseFaction(args[1]);
            String receiverFaction = parseFaction(args[2]);
            EntityPlayerMP actor=sender instanceof EntityPlayerMP?(EntityPlayerMP)sender:null; UUID acceptor=actor==null?null:kome.common.KOMEReflection.getEntityUUID(actor); KOMEDiplomacyService.Result result=KOMEDiplomacyService.acceptPendingIncrease(data,receiverFaction,senderFaction,acceptor,System.currentTimeMillis()); if(!result.accepted)throw new WrongUsageException(result.reason); KOMEDiplomacyService.projectLotrRelation(data,senderFaction,receiverFaction); sender.addChatMessage(new ChatComponentText("Diplomacy relation accepted."));
            return;
        }
        if ("cancel".equalsIgnoreCase(args[0])) { if(args.length!=3)throw new WrongUsageException(getCommandUsage(sender)); EntityPlayerMP actor=sender instanceof EntityPlayerMP?(EntityPlayerMP)sender:null; KOMEDiplomacyService.Result result=KOMEDiplomacyService.cancelPendingRequest(data,parseFaction(args[1]),parseFaction(args[2]),actor==null?null:kome.common.KOMEReflection.getEntityUUID(actor),sender.canCommandSenderUseCommand(2,getCommandName())); if(!result.accepted)throw new WrongUsageException(result.reason); sender.addChatMessage(new ChatComponentText("Diplomacy request cancelled.")); return; }
        if ("break".equalsIgnoreCase(args[0]) || "revoke".equalsIgnoreCase(args[0])) {
            throw new WrongUsageException("Accepted relation downgrade policy is not configured.");
            /*
            if (args.length != 3) {
                throw new WrongUsageException(getCommandUsage(sender));
            }
            String senderFaction = parseFaction(args[1]);
            String receiverFaction = parseFaction(args[2]);
            KOMEAlliance alliance = data.getAlliance(senderFaction, receiverFaction, false);
            EntityPlayerMP actor = sender instanceof EntityPlayerMP ? (EntityPlayerMP) sender : null;
            KOMEAllianceAuthority.Decision breakDecision = actor == null && sender.canCommandSenderUseCommand(2, getCommandName())
                ? KOMEAllianceAuthority.Decision.allow(false)
                : new KOMEAllianceAuthority(data).canBreakAlliance(actor, alliance);
            if (!breakDecision.allowed) {
                throw new WrongUsageException(breakDecision.reason);
            }
            boolean removed = data.clearAlliance(senderFaction, receiverFaction);
            if (removed) {
                syncRelationsForAlliancePair(data, senderFaction, receiverFaction);
                sendAllianceRefreshToParticipants(data, alliance);
            }
            sender.addChatMessage(new ChatComponentText((removed ? "Broke the formal alliance between " : "No alliance found for ") + displayFaction(senderFaction) + " and " + displayFaction(receiverFaction) + ". Stage 2 merchant entitlements remain unlocked."));
            return; */
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
                throw new WrongUsageException("Alliance goods unlock after the formal relationship is accepted.");
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
                throw new WrongUsageException("Alliance goods can be claimed only after the formal relationship is accepted.");
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
        if (args.length == 3 && "stage3hours".equalsIgnoreCase(args[1])) {
            double hours;
            try {
                hours = Double.parseDouble(args[2]);
            } catch (NumberFormatException error) {
                throw new WrongUsageException("Stage 3 hours must use 0.5-hour increments.");
            }
            int halfHours;
            try {
                halfHours = kome.common.data.KOMEHalfHourService.toHalfHours(hours);
            } catch (IllegalArgumentException error) {
                throw new WrongUsageException(error.getMessage());
            }
            if (halfHours < 1) throw new WrongUsageException("Stage 3 hours must be at least 0.5.");
            data.allianceStageThreeRequiredHalfHours = halfHours;
            recordAndRefresh(sender, data, "set Stage 3 Build-hour threshold " + hours);
            sender.addChatMessage(new ChatComponentText("Stage 3 Build contribution threshold set to "
                + kome.common.data.KOMEHalfHourService.displayHours(halfHours) + " hours."));
            return;
        }
        if (args.length == 4 && "stagequota".equalsIgnoreCase(args[1])) {
            int stage = parseIntBounded(sender, args[2], 1, 4);
            int value = parseIntBounded(sender, args[3], 1, 1000000);
            String type = KOMEAllianceProgressionService.stageQuotaType(stage);
            int tier = KOMEAllianceProgressionService.stageQuotaTier(stage);
            data.setAllianceRequirement(type, tier, "items", value);
            KOMEAllianceQuotaPool.reconcileConfiguredQuantities(data);
            recordAndRefresh(sender, data, "set Stage " + stage + " quota base " + value);
            sender.addChatMessage(new ChatComponentText("Configured Stage " + stage
                + " rolled quota base to " + value + " stack-equivalents. Claimed stages were not revoked."));
            return;
        }
        throw new WrongUsageException("/alliance config difficulty <easy|standard|hard> | stagequota <1-4> <stack-equivalents> | stage3hours <hours> | quota item <registry[:meta]> <weight|max|enabled|show> [value]");
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(
                args,
                "request",
                "accept",
                "cancel",
                "goods",
                "claimGoods",
                "get",
                "list");
        }

        if (args.length >= 2 && args.length <= 3
                && ("request".equalsIgnoreCase(args[0])
                || "accept".equalsIgnoreCase(args[0])
                || "cancel".equalsIgnoreCase(args[0])
                || "goods".equalsIgnoreCase(args[0])
                || "claimGoods".equalsIgnoreCase(args[0])
                || "get".equalsIgnoreCase(args[0]))) {
            List names = LOTRFaction.getPlayableAlignmentFactionNames();
            return getListOfStringsMatchingLastWord(
                args,
                (String[]) names.toArray(new String[names.size()]));
        }

        if (args.length == 2 && "list".equalsIgnoreCase(args[0])) {
            List names = LOTRFaction.getPlayableAlignmentFactionNames();
            return getListOfStringsMatchingLastWord(
                args,
                (String[]) names.toArray(new String[names.size()]));
        }

        if (args.length == 4 && "request".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(
                args,
                "friends",
                "allies");
        }

        return null;
    }
    private boolean isFactionArgument(String[] args) {
        if (args.length == 2) {
            return "request".equalsIgnoreCase(args[0])
                || "accept".equalsIgnoreCase(args[0])
                || "cancel".equalsIgnoreCase(args[0])
                || "goods".equalsIgnoreCase(args[0])
                || "claimGoods".equalsIgnoreCase(args[0])
                || "claim".equalsIgnoreCase(args[0])
                || "get".equalsIgnoreCase(args[0])
                || "list".equalsIgnoreCase(args[0]);
        }

        if (args.length == 3) {
            return "request".equalsIgnoreCase(args[0])
                || "accept".equalsIgnoreCase(args[0])
                || "cancel".equalsIgnoreCase(args[0])
                || "goods".equalsIgnoreCase(args[0])
                || "claimGoods".equalsIgnoreCase(args[0])
                || "claim".equalsIgnoreCase(args[0])
                || "get".equalsIgnoreCase(args[0]);
        }

        return false;
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
        if (sender instanceof EntityPlayerMP
                && !sender.canCommandSenderUseCommand(2, getCommandName())) {
            String viewerFaction =
                new KOMEAllianceAuthority(data).getPlayerFaction((EntityPlayerMP) sender);

            if (faction.length() > 0 && !faction.equals(viewerFaction)) {
                throw new WrongUsageException(
                    "You may list only diplomacy involving your pledged faction.");
            }

            faction = viewerFaction;
        }

        List<String> lines = new ArrayList<String>();

        for (kome.common.data.KOMEDiplomacyRecord record
                : KOMEDiplomacyService.records(data).values()) {
            if (record == null) {
                continue;
            }

            if (!faction.isEmpty()
                    && !faction.equals(record.factionA)
                    && !faction.equals(record.factionB)) {
                continue;
            }

            String line =
                "[" + record.key() + "] "
                    + displayFaction(record.factionA)
                    + " <-> "
                    + displayFaction(record.factionB)
                    + ": "
                    + record.relation.displayName;

            if (record.pendingTarget != null) {
                line +=
                    " | Pending "
                        + record.pendingTarget.displayName
                        + " ("
                        + displayFaction(record.requestingFaction)
                        + " -> "
                        + displayFaction(record.receivingFaction)
                        + ")";
            }

            lines.add(line);
        }

        Collections.sort(lines);

        if (lines.isEmpty()) {
            sender.addChatMessage(new ChatComponentText(
                faction.isEmpty()
                    ? "No diplomacy relationships recorded."
                    : "No diplomacy relationships recorded for "
                        + displayFaction(faction)
                        + "."));
            return;
        }

        sender.addChatMessage(
            new ChatComponentText("Recorded diplomacy relationships:"));

        for (String line : lines) {
            sender.addChatMessage(new ChatComponentText(line));
        }
    }
    private void sendDiplomacy(
            ICommandSender sender,
            KOMEWorldData data,
            String factionA,
            String factionB) {

        if (sender instanceof EntityPlayerMP
                && !sender.canCommandSenderUseCommand(2, getCommandName())) {
            String viewerFaction =
                new KOMEAllianceAuthority(data).getPlayerFaction((EntityPlayerMP) sender);

            if (!factionA.equals(viewerFaction)
                    && !factionB.equals(viewerFaction)) {
                throw new WrongUsageException(
                    "You may view only diplomacy involving your pledged faction.");
            }
        }

        String key =
            kome.common.data.KOMEDiplomacyRecord.pairKey(
                factionA, factionB);

        kome.common.data.KOMEDiplomacyRecord record =
            KOMEDiplomacyService.records(data).get(key);

        if (record == null) {
            sender.addChatMessage(new ChatComponentText(
                displayFaction(factionA)
                    + " <-> "
                    + displayFaction(factionB)
                    + ": "
                    + KOMEDiplomacyService.getRelation(
                        data, factionA, factionB).displayName));
            return;
        }

        String line =
            "[" + record.key() + "] "
                + displayFaction(record.factionA)
                + " <-> "
                + displayFaction(record.factionB)
                + ": "
                + record.relation.displayName;

        if (record.pendingTarget != null) {
            line +=
                " | Pending "
                    + record.pendingTarget.displayName
                    + " ("
                    + displayFaction(record.requestingFaction)
                    + " -> "
                    + displayFaction(record.receivingFaction)
                    + ")";
        }

        sender.addChatMessage(new ChatComponentText(line));
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

    private boolean parseOnOff(String value) {
        if ("on".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value)) {
            return true;
        }
        if ("off".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value) || "no".equalsIgnoreCase(value)) {
            return false;
        }
        throw new WrongUsageException("Value must be on or off.");
    }

    private String formatAlliance(KOMEAlliance alliance) {
        return "[" + alliance.getPairKey() + "] "
            + displayFaction(alliance.factionA) + " <-> " + displayFaction(alliance.factionB)
            + ": " + displayFaction(alliance.factionA) + " Stage " + alliance.getFactionStage(alliance.factionA)
            + ", " + displayFaction(alliance.factionB) + " Stage " + alliance.getFactionStage(alliance.factionB)
            + ", shared relation " + KOMEAllianceAuthority.relationName(strongestAllianceRelation(alliance));
    }

    private void sendBenefits(ICommandSender sender) {
        sender.addChatMessage(new ChatComponentText("Stage 0 Formal Neutrality: accepted relationship; no directional benefit."));
        sender.addChatMessage(new ChatComponentText("Stage 1 Cooperation: directional allied-farmer hiring."));
        sender.addChatMessage(new ChatComponentText("Stage 2 Friends: persistent Produce merchant-slot entitlement (future Produce integration)."));
        sender.addChatMessage(new ChatComponentText("Stage 3 Allies: directional company passage through partner-controlled tiles."));
        sender.addChatMessage(new ChatComponentText("Stage 4 Military Partnership: explicit company delegation and restricted kingless wartime authority."));
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

    public static void syncRelationsForAlliancePair(KOMEWorldData data, String factionA, String factionB) {
        LOTRFaction a = KOMEAlliance.findLotrFaction(factionA);
        LOTRFaction b = KOMEAlliance.findLotrFaction(factionB);
        if (a == null || b == null || a == b) {
            return;
        }
        LOTRFactionRelations.Relation relation = getDefaultRelation(factionA, factionB);
        LOTRFactionRelations.Relation formal = strongestAllianceRelation(data.getAlliance(factionA, factionB, false));
        if (formal != null) relation = formal;
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
            String receiver = alliance.getPendingReceiver(KOMEAlliance.CIVIL);
            String requester = alliance.getRequestedBy(KOMEAlliance.CIVIL);
            int automaticStage = KOMEAllianceAuthority.automaticStageForKinglessRelation(
                KOMEAllianceAuthority.getDefaultRelation(requester, receiver));
            if (alliance.getRelationshipStatus() == kome.common.data.KOMEAllianceTrackStatus.PENDING
                    && receiver.length() > 0 && !data.hasFactionKing(receiver) && data.hasFactionKing(requester)
                    && automaticStage >= 0) {
                alliance.acceptTrack(KOMEAlliance.CIVIL, "Automatic acceptance: no receiving king", worldTime);
                alliance.setFactionStage(requester, automaticStage, "Kingless automatic acceptance", worldTime, System.currentTimeMillis());
                alliance.setFactionStage(receiver, automaticStage, "Kingless automatic acceptance", worldTime, System.currentTimeMillis());
                allianceChanged = true;
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
        if (alliance == null || alliance.getRelationshipStatus() != kome.common.data.KOMEAllianceTrackStatus.ACTIVE) {
            return null;
        }
        int stage = alliance.getSharedRelationStage();
        if (stage >= 3) {
            return LOTRFactionRelations.Relation.ALLY;
        }
        if (stage >= 2) {
            return LOTRFactionRelations.Relation.FRIEND;
        }
        if (stage >= 0) {
            return LOTRFactionRelations.Relation.NEUTRAL;
        }
        return null;
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
