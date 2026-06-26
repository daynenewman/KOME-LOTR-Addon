package kome.common.command;

import kome.common.KOMEAddon;
import kome.common.data.KOMEAlliance;
import kome.common.data.KOMEAllianceRecordBuilder;
import kome.common.data.KOMEPlayerProgression;
import kome.common.data.KOMEProgressionTaskGenerator;
import kome.common.data.KOMEWorldData;
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

public class KOMECommandAlliance extends CommandBase {
    @Override
    public String getCommandName() {
        return "alliance";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/alliance request|accept|break <civil|military|trade> <senderFaction> <receiverFaction> | roll|reroll <military|trade> <senderFaction> <receiverFaction> | goods|claimGoods <senderFaction> <receiverFaction> | get <senderFaction> <receiverFaction> | set <civil|military|trade> <senderFaction> <receiverFaction> <tier> | clear <senderFaction> <receiverFaction> | list [faction] | benefits";
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
            requireSenderFaction(sender, data, senderFaction);
            if (senderFaction.equals(receiverFaction)) {
                throw new WrongUsageException("A faction cannot ally with itself.");
            }
            boolean senderIsKing = isSenderFactionKing(sender, data, senderFaction);
            requireDiplomacyPermission(sender, data, senderFaction, receiverFaction, type, senderIsKing);
            KOMEAlliance alliance = data.getAlliance(senderFaction, receiverFaction, true);
            if (alliance.getTier(type) != KOMEAlliance.NONE) {
                throw new WrongUsageException("A " + displayType(type) + " alliance already exists for " + displayFaction(senderFaction) + " -> " + displayFaction(receiverFaction) + ".");
            }
            int status = data.hasFactionKing(receiverFaction) ? KOMEAlliance.PENDING : 0;
            setInitialTiers(alliance, type, status, sender);
            if (status == 0) {
                overrideRelationsForAlliance(senderFaction, receiverFaction, type);
            }
            data.markDirty();
            if (status == KOMEAlliance.PENDING) {
                sender.addChatMessage(new ChatComponentText("Requested " + displayType(type) + " alliance: " + displayFaction(senderFaction) + " -> " + displayFaction(receiverFaction) + ". Waiting for " + data.getFactionKingName(receiverFaction) + " to accept."));
            } else {
                sender.addChatMessage(new ChatComponentText("Accepted automatically: " + displayFaction(receiverFaction) + " has no recorded king. " + displayFaction(senderFaction) + " now has " + displayType(type) + " tier 0."));
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
            if (!sender.canCommandSenderUseCommand(2, getCommandName())) {
                EntityPlayerMP player = getCommandSenderAsPlayer(sender);
                String pledged = getPlayerFaction(data, player);
                if (senderFaction.equals(pledged)) {
                    throw new WrongUsageException("The sending faction cannot accept its own alliance request.");
                }
                if (!data.isFactionKing(receiverFaction, kome.common.KOMEReflection.getEntityUUID(player))) {
                    throw new WrongUsageException("Only staff or the receiving faction king can accept this alliance.");
                }
            }
            KOMEAlliance alliance = data.getAlliance(senderFaction, receiverFaction, false);
            if (alliance == null || !alliance.hasAnyAlliance()) {
                throw new WrongUsageException("No alliance request exists for " + displayFaction(senderFaction) + " -> " + displayFaction(receiverFaction) + ".");
            }
            if (alliance.getTier(type) != KOMEAlliance.PENDING) {
                throw new WrongUsageException("No pending " + displayType(type) + " alliance exists for " + displayFaction(senderFaction) + " -> " + displayFaction(receiverFaction) + ".");
            }
            acceptTiers(alliance, type, sender);
            overrideRelationsForAlliance(senderFaction, receiverFaction, type);
            data.markDirty();
            sender.addChatMessage(new ChatComponentText("Accepted " + displayType(type) + " alliance: " + displayFaction(senderFaction) + " -> " + displayFaction(receiverFaction) + "."));
            return;
        }
        if ("break".equalsIgnoreCase(args[0]) || "revoke".equalsIgnoreCase(args[0])) {
            if (args.length != 3 && args.length != 4) {
                throw new WrongUsageException(getCommandUsage(sender));
            }
            String type = args.length == 4 ? parseType(args[1]) : "";
            String senderFaction = parseFaction(args[args.length - 2]);
            String receiverFaction = parseFaction(args[args.length - 1]);
            requireBreakPermission(sender, data, senderFaction, receiverFaction);
            KOMEAlliance alliance = data.getAlliance(senderFaction, receiverFaction, false);
            boolean removed;
            if (type.isEmpty()) {
                removed = data.clearAlliance(senderFaction, receiverFaction);
            } else {
                removed = alliance != null && alliance.getTier(type) != KOMEAlliance.NONE;
                if (removed) {
                    alliance.setTier(type, KOMEAlliance.NONE, sender.getCommandSenderName(), sender.getEntityWorld().getTotalWorldTime());
                    data.markDirty();
                }
            }
            if (removed) {
                syncRelationsForAlliancePair(data, senderFaction, receiverFaction);
            }
            sender.addChatMessage(new ChatComponentText((removed ? "Broke " + (type.isEmpty() ? "" : displayType(type) + " ") + "alliance: " : "No alliance found for ") + displayFaction(senderFaction) + " -> " + displayFaction(receiverFaction) + "."));
            return;
        }
        if ("roll".equalsIgnoreCase(args[0])) {
            if (args.length != 4) {
                throw new WrongUsageException(getCommandUsage(sender));
            }
            String type = parseType(args[1]);
            if (!KOMEAlliance.MILITARY.equals(type) && !KOMEAlliance.TRADE.equals(type)) {
                throw new WrongUsageException("Only military and trade alliances use food quotas.");
            }
            String senderFaction = parseFaction(args[2]);
            String receiverFaction = parseFaction(args[3]);
            requireSenderFaction(sender, data, senderFaction);
            KOMEAlliance alliance = data.getAlliance(senderFaction, receiverFaction, false);
            if (alliance == null || !alliance.hasAnyAlliance()) {
                throw new WrongUsageException("No alliance request exists for " + displayFaction(senderFaction) + " -> " + displayFaction(receiverFaction) + ".");
            }
            if (alliance.getTier(type) == KOMEAlliance.PENDING) {
                throw new WrongUsageException("This " + displayType(type) + " alliance request must be accepted before tiers can progress.");
            }
            if (alliance.getTier(type) != 0) {
                throw new WrongUsageException("Only tier 0 " + displayType(type) + " alliances need a food quota roll.");
            }
            String id = KOMEAlliance.MILITARY.equals(type) ? "military.food" : "trade.food";
            if (!alliance.getAssignment(id).trim().isEmpty()) {
                sender.addChatMessage(new ChatComponentText(displayType(type) + " quota already rolled: " + alliance.getAssignment(id)));
                return;
            }
            ensureFoodQuota(alliance, id, sender);
            data.markDirty();
            sender.addChatMessage(new ChatComponentText("Rolled " + displayType(type) + " alliance quota: " + alliance.getAssignment(id)));
            sendAllianceRefresh(sender, data);
            return;
        }
        if ("reroll".equalsIgnoreCase(args[0]) || "rerollQuota".equalsIgnoreCase(args[0])) {
            requireStaff(sender);
            if (args.length != 4) {
                throw new WrongUsageException(getCommandUsage(sender));
            }
            String type = parseType(args[1]);
            if (!KOMEAlliance.MILITARY.equals(type) && !KOMEAlliance.TRADE.equals(type)) {
                throw new WrongUsageException("Only military and trade alliances use food quotas.");
            }
            String senderFaction = parseFaction(args[2]);
            String receiverFaction = parseFaction(args[3]);
            KOMEAlliance alliance = data.getAlliance(senderFaction, receiverFaction, false);
            if (alliance == null || !alliance.hasAnyAlliance()) {
                throw new WrongUsageException("No alliance request exists for " + displayFaction(senderFaction) + " -> " + displayFaction(receiverFaction) + ".");
            }
            String id = KOMEAlliance.MILITARY.equals(type) ? "military.food" : "trade.food";
            String previous = alliance.getAssignment(id);
            rollFoodQuota(alliance, id, sender, true);
            data.markDirty();
            sender.addChatMessage(new ChatComponentText("Rerolled " + displayType(type) + " alliance quota for " + displayFaction(senderFaction) + " -> " + displayFaction(receiverFaction) + "."));
            if (previous != null && previous.trim().length() > 0) {
                sender.addChatMessage(new ChatComponentText("Previous: " + previous));
            }
            sender.addChatMessage(new ChatComponentText("New: " + alliance.getAssignment(id)));
            sendAllianceRefresh(sender, data);
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
            requireGoodsLedgerPermission(sender, data, senderFaction, receiverFaction);
            KOMEAllianceGuiHandler.openAllianceLedger(player, alliance);
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
            int claimed = claimStoredGoods(player, alliance);
            data.markDirty();
            if (player.openContainer != null) {
                player.openContainer.detectAndSendChanges();
            }
            sendAllianceRefresh(sender, data);
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
            if (isEnemyAlliance(factionA, factionB)) {
                throw new WrongUsageException("Enemy factions cannot form alliances.");
            }
            KOMEAlliance alliance = data.getAlliance(factionA, factionB, true);
            alliance.setTier(type, tier, sender.getCommandSenderName(), sender.getEntityWorld().getTotalWorldTime());
            syncRelationsForAlliancePair(data, factionA, factionB);
            data.markDirty();
            sender.addChatMessage(new ChatComponentText("Set " + displayType(type) + " alliance " + displayFaction(factionA) + " -> " + displayFaction(factionB) + " to tier " + tier + "."));
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

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "request", "accept", "break", "roll", "reroll", "goods", "claimGoods", "get", "set", "clear", "list", "benefits");
        }
        if (args.length == 2 && "set".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, KOMEAlliance.CIVIL, KOMEAlliance.MILITARY, KOMEAlliance.TRADE);
        }
        if (args.length == 2 && ("request".equalsIgnoreCase(args[0]) || "accept".equalsIgnoreCase(args[0]) || "break".equalsIgnoreCase(args[0]) || "revoke".equalsIgnoreCase(args[0]))) {
            return getListOfStringsMatchingLastWord(args, KOMEAlliance.CIVIL, KOMEAlliance.MILITARY, KOMEAlliance.TRADE);
        }
        if (args.length == 2 && "roll".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, KOMEAlliance.MILITARY, KOMEAlliance.TRADE);
        }
        if (args.length == 2 && ("reroll".equalsIgnoreCase(args[0]) || "rerollQuota".equalsIgnoreCase(args[0]))) {
            return getListOfStringsMatchingLastWord(args, KOMEAlliance.MILITARY, KOMEAlliance.TRADE);
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

    private int claimStoredGoods(EntityPlayerMP player, KOMEAlliance alliance) {
        int claimed = 0;
        for (int i = 0; i < KOMEAlliance.STORAGE_SLOTS; i++) {
            ItemStack stack = alliance.getStorage(i);
            if (stack == null) {
                continue;
            }
            ItemStack toGive = stack.copy();
            if (!player.inventory.addItemStackToInventory(toGive)) {
                player.dropPlayerItemWithRandomChoice(toGive, false);
            }
            alliance.setStorage(i, null);
            claimed++;
        }
        claimed += claimVirtualGoods(player, alliance, "military.food");
        claimed += claimVirtualGoods(player, alliance, "trade.food");
        claimed += claimVirtualGoods(player, alliance, "civil.coins");
        claimed += claimVirtualGoods(player, alliance, "trade.coins");
        claimed += claimVirtualGoods(player, alliance, "trade.t2.coins");
        claimed += claimVirtualGoods(player, alliance, "military.t4.coins");
        player.inventoryContainer.detectAndSendChanges();
        return claimed;
    }

    private int claimVirtualGoods(EntityPlayerMP player, KOMEAlliance alliance, String id) {
        ItemStack sample = alliance.getClaimSample(id);
        int amount = alliance.getClaimAmount(id);
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
        alliance.clearClaimGoods(id);
        return claimed;
    }

    private void listAlliances(ICommandSender sender, KOMEWorldData data, String faction) {
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

    private String formatAlliance(KOMEAlliance alliance) {
        return displayFaction(alliance.factionA) + " -> " + displayFaction(alliance.factionB)
            + ": civil " + displayTier(alliance.civilTier)
            + ", military " + displayTier(alliance.militaryTier)
            + ", trade " + displayTier(alliance.tradeTier);
    }

    private static String displayTier(int tier) {
        return tier == KOMEAlliance.PENDING ? "pending" : tier < 0 ? "none" : "T" + tier;
    }

    private void sendBenefits(ICommandSender sender) {
        sender.addChatMessage(new ChatComponentText("Civil: T0 alliance begins, T1 use faction WPs, T2 hire farmhands."));
        sender.addChatMessage(new ChatComponentText("Military: T0 alliance begins, T1 hire 1 unit, T2 attack through faction, T3 command armies, T4 spawn captain."));
        sender.addChatMessage(new ChatComponentText("Trade: T0 alliance begins, T1 build in faction land, T2 add produce merchant crop trade."));
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
            return "Benefit: may spawn your captain in that faction's land.";
        }
        return tier == 0 ? "Benefit: alliance begins." : tier == 1 ? "Benefit: may build in that faction's land." : "Benefit: may add a new crop trade to a produce merchant.";
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

    private void ensureFoodQuota(KOMEAlliance alliance, String id, ICommandSender sender) {
        if (alliance.getAssignment(id).trim().isEmpty()) {
            rollFoodQuota(alliance, id, sender, false);
        }
    }

    private void rollFoodQuota(KOMEAlliance alliance, String id, ICommandSender sender, boolean resetDelivered) {
        long seed = sender.getEntityWorld().getTotalWorldTime() ^ System.nanoTime() ^ id.hashCode();
        alliance.setAssignment(id, KOMEProgressionTaskGenerator.roll("serf.food_quota_1", seed));
        if (resetDelivered) {
            alliance.setDelivered(id, 0);
            alliance.clearClaimGoods(id);
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
        LOTRFactionRelations.Relation relation = getRelation(factionA, factionB);
        return relation == LOTRFactionRelations.Relation.ENEMY || relation == LOTRFactionRelations.Relation.MORTAL_ENEMY;
    }

    private static LOTRFactionRelations.Relation getRelation(String factionA, String factionB) {
        LOTRFaction a = KOMEAlliance.findLotrFaction(factionA);
        LOTRFaction b = KOMEAlliance.findLotrFaction(factionB);
        return a == null || b == null ? LOTRFactionRelations.Relation.NEUTRAL : LOTRFactionRelations.getRelations(a, b);
    }

    private static void overrideRelationsForAlliance(String factionA, String factionB, String type) {
        LOTRFaction a = KOMEAlliance.findLotrFaction(factionA);
        LOTRFaction b = KOMEAlliance.findLotrFaction(factionB);
        if (a != null && b != null) {
            LOTRFactionRelations.overrideRelations(a, b, relationForAllianceType(type));
        }
    }

    private static void syncRelationsForAlliancePair(KOMEWorldData data, String factionA, String factionB) {
        LOTRFaction a = KOMEAlliance.findLotrFaction(factionA);
        LOTRFaction b = KOMEAlliance.findLotrFaction(factionB);
        if (a == null || b == null || a == b) {
            return;
        }
        LOTRFactionRelations.Relation relation = strongestAllianceRelation(data.getAlliance(factionA, factionB, false));
        relation = strongestRelation(relation, strongestAllianceRelation(data.getAlliance(factionB, factionA, false)));
        if (relation == null) {
            relation = LOTRFactionRelations.getFromDefaultMap(new LOTRFactionRelations.FactionPair(a, b));
        }
        LOTRFactionRelations.overrideRelations(a, b, relation);
    }

    public static void reconcileKinglessPendingAlliances(KOMEWorldData data, long worldTime) {
        if (data == null) {
            return;
        }
        boolean changed = false;
        for (KOMEAlliance alliance : data.alliances.values()) {
            if (alliance == null || data.hasFactionKing(alliance.factionB)) {
                continue;
            }
            boolean allianceChanged = false;
            if (alliance.civilTier == KOMEAlliance.PENDING) {
                alliance.setTier(KOMEAlliance.CIVIL, 0, "Automatic acceptance: no receiving king", worldTime);
                allianceChanged = true;
            }
            if (alliance.militaryTier == KOMEAlliance.PENDING) {
                alliance.setTier(KOMEAlliance.MILITARY, 0, "Automatic acceptance: no receiving king", worldTime);
                allianceChanged = true;
            }
            if (alliance.tradeTier == KOMEAlliance.PENDING) {
                alliance.setTier(KOMEAlliance.TRADE, 0, "Automatic acceptance: no receiving king", worldTime);
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

    private static boolean relationAllowsKinglessRequest(String type, String factionA, String factionB) {
        LOTRFactionRelations.Relation relation = getRelation(factionA, factionB);
        if (KOMEAlliance.MILITARY.equals(type)) {
            return relation == LOTRFactionRelations.Relation.ALLY;
        }
        if (KOMEAlliance.TRADE.equals(type)) {
            return relation == LOTRFactionRelations.Relation.ALLY || relation == LOTRFactionRelations.Relation.FRIEND;
        }
        return relation == LOTRFactionRelations.Relation.ALLY
            || relation == LOTRFactionRelations.Relation.FRIEND
            || relation == LOTRFactionRelations.Relation.NEUTRAL;
    }

    private static String parseFactionLenient(String value) {
        try {
            return parseFaction(value);
        } catch (RuntimeException e) {
            return value;
        }
    }

    private void requireSenderFaction(ICommandSender sender, KOMEWorldData data, String senderFaction) {
        if (sender.canCommandSenderUseCommand(2, getCommandName())) {
            return;
        }
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        String pledged = getPlayerFaction(data, player);
        if (!senderFaction.equals(pledged)) {
            throw new WrongUsageException("You can only send alliance requests from your pledged faction.");
        }
    }

    private boolean isSenderFactionKing(ICommandSender sender, KOMEWorldData data, String senderFaction) {
        if (sender.canCommandSenderUseCommand(2, getCommandName())) {
            return true;
        }
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        return data.isFactionKing(senderFaction, kome.common.KOMEReflection.getEntityUUID(player));
    }

    private void requireDiplomacyPermission(ICommandSender sender, KOMEWorldData data, String senderFaction, String receiverFaction, String type, boolean senderIsKing) {
        if (senderIsKing) {
            return;
        }
        if (data.hasFactionKing(senderFaction)) {
            throw new WrongUsageException("Only the sending faction king can send alliance requests.");
        }
        if (!relationAllowsKinglessRequest(type, senderFaction, receiverFaction)) {
            throw new WrongUsageException("A kingless faction can only request civil alliances with neutral or better factions, trade alliances with friends or allies, and military alliances with allies.");
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
        if (pledge != null) {
            return KOMEAlliance.normalizeFactionKey(pledge.codeName());
        }
        KOMEPlayerProgression progression = data.getProgression(kome.common.KOMEReflection.getEntityUUID(player));
        String faction = progression.getPledgedLordFaction();
        return faction == null || faction.trim().isEmpty() ? "" : parseFaction(faction);
    }

    private void requireStaff(ICommandSender sender) {
        if (!sender.canCommandSenderUseCommand(2, getCommandName())) {
            throw new WrongUsageException("You do not have permission to change alliances.");
        }
    }
}
