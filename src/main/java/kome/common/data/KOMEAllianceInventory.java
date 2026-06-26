package kome.common.data;

import kome.common.network.KOMEPacketHandler;
import kome.common.network.KOMEPacketQuotaLedger;
import lotr.common.LOTRLevelData;
import lotr.common.fac.LOTRFaction;
import lotr.common.item.LOTRItemCoin;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class KOMEAllianceInventory implements IInventory {
    private static final int TRADE_T1_COINS_REQUIRED = 5000;
    private static final int TRADE_T2_COINS_REQUIRED = 10000;
    private static final int MILITARY_T4_COINS_REQUIRED = 30000;
    private static final int MILITARY_T4_POP_REQUIRED = 50;
    private static final String[] CLAIM_IDS = new String[] {"civil.coins", "trade.coins", "trade.t2.coins", "military.t4.coins", "military.food", "trade.food"};
    private final KOMEWorldData data;
    private final KOMEAlliance alliance;
    private final EntityPlayerMP viewer;
    private final String name;

    public KOMEAllianceInventory(KOMEWorldData data, KOMEAlliance alliance, EntityPlayerMP viewer) {
        this.data = data;
        this.alliance = alliance;
        this.viewer = viewer;
        this.name = "Alliance Ledger";
        sendLedger();
    }

    @Override
    public int getSizeInventory() {
        return KOMEAlliance.STORAGE_SLOTS;
    }

    @Override
    public ItemStack getStackInSlot(int slotIn) {
        return alliance.getStorage(slotIn);
    }

    @Override
    public ItemStack decrStackSize(int index, int count) {
        ItemStack stack = alliance.decrStorage(index, count);
        if (stack != null) {
            markDirty();
        }
        return stack;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int index) {
        ItemStack stack = alliance.getStorage(index);
        alliance.setStorage(index, null);
        markDirty();
        return stack;
    }

    @Override
    public void setInventorySlotContents(int index, ItemStack stack) {
        if (stack != null && stack.stackSize > getInventoryStackLimit()) {
            stack.stackSize = getInventoryStackLimit();
        }
        alliance.setStorage(index, absorbStack(stack));
        markDirty();
    }

    @Override
    public String getInventoryName() {
        return name;
    }

    @Override
    public boolean hasCustomInventoryName() {
        return true;
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    @Override
    public void markDirty() {
        absorbStoredStacks();
        applyCoinUnlocks();
        data.markDirty();
        sendLedger();
        if (viewer != null && viewer.openContainer != null) {
            viewer.openContainer.detectAndSendChanges();
        }
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        return true;
    }

    @Override
    public void openInventory() {
        sendLedger();
    }

    @Override
    public void closeInventory() {
        markDirty();
    }

    @Override
    public boolean isItemValidForSlot(int index, ItemStack stack) {
        return canViewerDeposit() && stack != null && isAcceptedRequirement(stack);
    }

    private void applyCoinUnlocks() {
        if (alliance.civilTier == 0 && alliance.getDelivered("civil.coins") >= 1000) {
            alliance.setTier(KOMEAlliance.CIVIL, 1, "Alliance goods", alliance.updatedWorldTime);
        }
        if (alliance.tradeTier == 0 && alliance.getDelivered("trade.coins") >= TRADE_T1_COINS_REQUIRED) {
            Quota quota = parseQuota(alliance.getAssignment("trade.food"));
            if (quota != null && alliance.getDelivered("trade.food") >= quota.requiredUnits) {
                alliance.setTier(KOMEAlliance.TRADE, 1, "Alliance goods", alliance.updatedWorldTime);
            }
        }
        if (alliance.tradeTier == 1 && alliance.getDelivered("trade.t2.coins") >= TRADE_T2_COINS_REQUIRED && data.getFactionFarmerPop(alliance.factionA) >= 50) {
            alliance.setTier(KOMEAlliance.TRADE, 2, "Alliance goods", alliance.updatedWorldTime);
        }
        Quota militaryQuota = parseQuota(alliance.getAssignment("military.food"));
        if (alliance.militaryTier == 0 && militaryQuota != null && alliance.getDelivered("military.food") >= militaryQuota.requiredUnits) {
            alliance.setTier(KOMEAlliance.MILITARY, 1, "Alliance goods", alliance.updatedWorldTime);
        }
        if (alliance.militaryTier == 3
            && alliance.getDelivered("military.t4.coins") >= MILITARY_T4_COINS_REQUIRED
            && data.getFactionPopulation(alliance.factionA) >= MILITARY_T4_POP_REQUIRED) {
            alliance.setTier(KOMEAlliance.MILITARY, 4, "Alliance goods", alliance.updatedWorldTime);
        }
    }

    private int getCoinValue() {
        int value = 0;
        for (int i = 0; i < getSizeInventory(); i++) {
            ItemStack stack = getStackInSlot(i);
            if (stack != null && stack.getItem() instanceof LOTRItemCoin) {
                value += LOTRItemCoin.values[Math.max(0, Math.min(stack.getItemDamage(), LOTRItemCoin.values.length - 1))] * stack.stackSize;
            }
        }
        return value;
    }

    private boolean isAcceptedRequirement(ItemStack stack) {
        if (stack.getItem() instanceof LOTRItemCoin) {
            return getNeededCoinValue() > 0;
        }
        return matchesActiveQuota(stack);
    }

    private boolean matchesActiveQuota(ItemStack stack) {
        Quota militaryQuota = parseQuota(alliance.getAssignment("military.food"));
        if (alliance.militaryTier == 0 && militaryQuota != null && alliance.getDelivered("military.food") < militaryQuota.requiredUnits && matches(stack, militaryQuota)) {
            return true;
        }
        Quota tradeQuota = parseQuota(alliance.getAssignment("trade.food"));
        return alliance.tradeTier == 0 && tradeQuota != null && alliance.getDelivered("trade.food") < tradeQuota.requiredUnits && matches(stack, tradeQuota);
    }

    private int getNeededCoinValue() {
        int needed = 0;
        if (alliance.civilTier == 0) {
            needed += Math.max(0, 1000 - alliance.getDelivered("civil.coins"));
        }
        if (alliance.tradeTier == 0) {
            needed += Math.max(0, TRADE_T1_COINS_REQUIRED - alliance.getDelivered("trade.coins"));
        }
        if (alliance.tradeTier == 1) {
            needed += Math.max(0, TRADE_T2_COINS_REQUIRED - alliance.getDelivered("trade.t2.coins"));
        }
        if (alliance.militaryTier == 3) {
            needed += Math.max(0, MILITARY_T4_COINS_REQUIRED - alliance.getDelivered("military.t4.coins"));
        }
        return needed;
    }

    private boolean depositQuotaStack(ItemStack stack) {
        return depositQuotaStack(stack, "military.food", alliance.militaryTier)
            || depositQuotaStack(stack, "trade.food", alliance.tradeTier);
    }

    private ItemStack absorbStack(ItemStack stack) {
        if (canViewerDeposit() && stack != null && (depositQuotaStack(stack) || depositCoinStack(stack)) && stack.stackSize <= 0) {
            return null;
        }
        return stack;
    }

    private void absorbStoredStacks() {
        for (int i = 0; i < getSizeInventory(); i++) {
            ItemStack stack = alliance.getStorage(i);
            if (stack != null) {
                alliance.setStorage(i, absorbStack(stack));
            }
        }
    }

    private boolean depositQuotaStack(ItemStack stack, String id, int tier) {
        Quota quota = parseQuota(alliance.getAssignment(id));
        if (tier != 0 || quota == null || !matches(stack, quota)) {
            return false;
        }
        int needed = quota.requiredUnits - alliance.getDelivered(id);
        if (needed <= 0) {
            return false;
        }
        int taken = Math.min(stack.stackSize, needed);
        ItemStack sample = stack.copy();
        sample.stackSize = 1;
        alliance.addDelivered(id, taken);
        alliance.addClaimGoods(id, sample, taken);
        stack.stackSize -= taken;
        return true;
    }

    private boolean depositCoinStack(ItemStack stack) {
        if (!(stack.getItem() instanceof LOTRItemCoin)) {
            return false;
        }
        int value = LOTRItemCoin.values[Math.max(0, Math.min(stack.getItemDamage(), LOTRItemCoin.values.length - 1))];
        return depositCoins(stack, value, "civil.coins", 1000, alliance.civilTier)
            || depositCoins(stack, value, "trade.coins", TRADE_T1_COINS_REQUIRED, alliance.tradeTier == 0 ? 0 : -1)
            || depositCoins(stack, value, "trade.t2.coins", TRADE_T2_COINS_REQUIRED, alliance.tradeTier == 1 ? 0 : -1)
            || depositCoins(stack, value, "military.t4.coins", MILITARY_T4_COINS_REQUIRED, alliance.militaryTier == 3 ? 0 : -1);
    }

    private boolean depositCoins(ItemStack stack, int coinValue, String id, int required, int tier) {
        if (tier != 0 || coinValue <= 0) {
            return false;
        }
        int neededValue = required - alliance.getDelivered(id);
        if (neededValue <= 0) {
            return false;
        }
        int neededCoins = (neededValue + coinValue - 1) / coinValue;
        int taken = Math.min(stack.stackSize, neededCoins);
        ItemStack sample = stack.copy();
        sample.stackSize = 1;
        alliance.addDelivered(id, taken * coinValue);
        alliance.addClaimGoods(id, sample, taken);
        stack.stackSize -= taken;
        return true;
    }

    private void sendLedger() {
        if (viewer == null) {
            return;
        }
        KOMEPacketHandler.network.sendTo(new KOMEPacketQuotaLedger(getLedgerLines()), viewer);
    }

    private List getLedgerLines() {
        List lines = new ArrayList();
        boolean canClaim = canViewerClaim();
        lines.add("SUMMARY\t" + displayFaction(alliance.factionA) + "\t" + displayFaction(alliance.factionB) + "\t" + alliance.factionA + "\t" + alliance.factionB);
        lines.add("VIEWER\t" + getViewerFactionName() + "\t" + (canViewerDeposit() ? "1" : "0") + "\t" + (canClaim ? "1" : "0"));
        if (alliance.civilTier != KOMEAlliance.NONE) {
            addProgressLine(lines, "Civil", alliance.civilTier, getCivilRequirement(), getCivilProgressLabel(), getCivilDelivered(), getCivilRequired(), getCivilReward());
        }
        if (alliance.militaryTier != KOMEAlliance.NONE) {
            addProgressLine(lines, "Military", alliance.militaryTier, getMilitaryRequirement(), getMilitaryProgressLabel(), getMilitaryDelivered(), getMilitaryRequired(), getMilitaryReward());
            addQuotaProgressLine(lines, "Military", "military.food");
        }
        if (alliance.tradeTier != KOMEAlliance.NONE) {
            addProgressLine(lines, "Trade", alliance.tradeTier, getTradeRequirement(), getTradeProgressLabel(), getTradeDelivered(), getTradeRequired(), getTradeReward());
            addQuotaProgressLine(lines, "Trade", "trade.food");
        }
        lines.add("DEPOSIT\tPlace required coins or quota foods in the chest slots. Accepted goods are compressed into this ledger.");
        lines.add("CLAIM\t" + getClaimSummary() + "\t" + (canClaim ? "1" : "0") + "\t" + (canClaim ? "Receiving faction king" : "Only the receiving faction king can claim"));
        if (alliance.civilTier != KOMEAlliance.NONE) {
            addCoinLine(lines, "Civil Coins", "civil.coins", 1000, alliance.civilTier == 0);
        }
        if (alliance.militaryTier != KOMEAlliance.NONE) {
            addQuotaLine(lines, "Military Food", "military.food");
        }
        if (alliance.tradeTier != KOMEAlliance.NONE) {
            addCoinLine(lines, "Trade T1 Coins", "trade.coins", TRADE_T1_COINS_REQUIRED, alliance.tradeTier == 0);
        }
        if (alliance.tradeTier != KOMEAlliance.NONE) {
            addQuotaLine(lines, "Trade Food", "trade.food");
        }
        if (alliance.tradeTier != KOMEAlliance.NONE) {
            addCoinLine(lines, "Trade T2 Coins", "trade.t2.coins", TRADE_T2_COINS_REQUIRED, alliance.tradeTier == 1);
            if (alliance.tradeTier == 1) {
                int pop = Math.min(data.getFactionFarmerPop(alliance.factionA), 50);
                lines.add("Trade T2 Farmer Pop Cost: " + pop + "/50 available" + (pop >= 50 ? " complete" : ""));
            } else if (alliance.tradeTier >= 2) {
                lines.add("Trade T2 Farmer Pop Cost: 50/50 spent complete");
            }
        }
        if (alliance.militaryTier != KOMEAlliance.NONE) {
            addCoinLine(lines, "Military T4 Coins", "military.t4.coins", MILITARY_T4_COINS_REQUIRED, alliance.militaryTier == 3);
            if (alliance.militaryTier == 3) {
                int pop = Math.min(data.getFactionPopulation(alliance.factionA), MILITARY_T4_POP_REQUIRED);
                lines.add("Military T4 Pop Cost: " + pop + "/" + MILITARY_T4_POP_REQUIRED + " available" + (pop >= MILITARY_T4_POP_REQUIRED ? " complete" : ""));
            } else if (alliance.militaryTier >= 4) {
                lines.add("Military T4 Pop Cost: " + MILITARY_T4_POP_REQUIRED + "/" + MILITARY_T4_POP_REQUIRED + " spent complete");
            }
        }
        return lines;
    }

    private void addProgressLine(List lines, String type, int tier, String requirement, String progressLabel, int delivered, int required, String reward) {
        lines.add("PROGRESS\t" + type + "\t" + displayTier(tier) + "\t" + requirement + "\t" + progressLabel + "\t" + delivered + "\t" + required + "\t" + reward);
    }

    private void addQuotaProgressLine(List lines, String type, String id) {
        Quota quota = parseQuota(alliance.getAssignment(id));
        if (quota == null) {
            lines.add("QUOTA\t" + type + "\t\t0\t0\t");
            return;
        }
        int delivered = Math.min(alliance.getDelivered(id), quota.requiredUnits);
        int shownDelivered = quota.stacks ? delivered / 64 : delivered;
        int shownRequired = quota.stacks ? quota.requiredUnits / 64 : quota.requiredUnits;
        String unit = quota.stacks ? "stacks" : "items";
        lines.add("QUOTA\t" + type + "\t" + quota.item + "\t" + shownDelivered + "\t" + shownRequired + "\t" + unit);
    }

    private String getCivilRequirement() {
        if (alliance.civilTier < 0) {
            return alliance.civilTier == KOMEAlliance.PENDING ? "Receiving faction must accept the request." : "No active civil alliance.";
        }
        if (alliance.civilTier == 0) {
            return "Deposit 1000 coins.";
        }
        if (alliance.civilTier == 1) {
            return "Trade 500 coins worth of goods with the receiver.";
        }
        return "Civil alliance requirements complete.";
    }

    private String getCivilProgressLabel() {
        if (alliance.civilTier == 0) {
            return "Coins delivered";
        }
        if (alliance.civilTier == 1) {
            return "Trade delivered";
        }
        return alliance.civilTier >= 2 ? "Complete" : "Not started";
    }

    private int getCivilDelivered() {
        if (alliance.civilTier == 0) {
            return Math.min(alliance.getDelivered("civil.coins"), 1000);
        }
        if (alliance.civilTier == 1) {
            return Math.min(alliance.getDelivered("civil.trade"), 500);
        }
        return alliance.civilTier >= 2 ? 1 : 0;
    }

    private int getCivilRequired() {
        if (alliance.civilTier == 0) {
            return 1000;
        }
        if (alliance.civilTier == 1) {
            return 500;
        }
        return alliance.civilTier >= 2 ? 1 : 0;
    }

    private String getCivilReward() {
        if (alliance.civilTier < 1) {
            return "Unlock Civil T1 benefits";
        }
        if (alliance.civilTier == 1) {
            return "Unlock faction waypoints";
        }
        return "Civil benefits unlocked";
    }

    private String getMilitaryRequirement() {
        if (alliance.militaryTier < 0) {
            return alliance.militaryTier == KOMEAlliance.PENDING ? "Receiving faction must accept the request." : "No active military alliance.";
        }
        if (alliance.militaryTier == 0) {
            String quota = alliance.getAssignment("military.food");
            return quota.length() == 0 ? "Roll a military food quota." : quota;
        }
        if (alliance.militaryTier == 1) {
            return "Kill 2000 enemies of the receiver.";
        }
        if (alliance.militaryTier == 2) {
            return "Complete the population build and waypoint battle.";
        }
        if (alliance.militaryTier == 3) {
            return "Provide 50 population and 30000 coins.";
        }
        return "Military alliance requirements complete.";
    }

    private String getMilitaryProgressLabel() {
        if (alliance.militaryTier == 0) {
            return "Food delivered";
        }
        if (alliance.militaryTier == 1) {
            return "Kills delivered";
        }
        if (alliance.militaryTier == 3) {
            return "Coins delivered";
        }
        return alliance.militaryTier >= 4 ? "Complete" : "Status";
    }

    private int getMilitaryDelivered() {
        if (alliance.militaryTier == 0) {
            Quota quota = parseQuota(alliance.getAssignment("military.food"));
            return quota == null ? 0 : Math.min(alliance.getDelivered("military.food"), quota.requiredUnits);
        }
        if (alliance.militaryTier == 1) {
            return Math.min(alliance.getDelivered("military.kills"), 2000);
        }
        if (alliance.militaryTier == 3) {
            return Math.min(alliance.getDelivered("military.t4.coins"), MILITARY_T4_COINS_REQUIRED);
        }
        return alliance.militaryTier >= 4 ? 1 : 0;
    }

    private int getMilitaryRequired() {
        if (alliance.militaryTier == 0) {
            Quota quota = parseQuota(alliance.getAssignment("military.food"));
            return quota == null ? 0 : quota.requiredUnits;
        }
        if (alliance.militaryTier == 1) {
            return 2000;
        }
        if (alliance.militaryTier == 3) {
            return MILITARY_T4_COINS_REQUIRED;
        }
        return alliance.militaryTier >= 4 ? 1 : 0;
    }

    private String getMilitaryReward() {
        if (alliance.militaryTier < 1) {
            return "Unlock Military T1 benefits";
        }
        if (alliance.militaryTier == 1) {
            return "Unlock military cooperation";
        }
        if (alliance.militaryTier == 2) {
            return "Unlock army command";
        }
        if (alliance.militaryTier == 3) {
            return "Unlock captain spawning";
        }
        return "Military benefits unlocked";
    }

    private String getTradeRequirement() {
        if (alliance.tradeTier < 0) {
            return alliance.tradeTier == KOMEAlliance.PENDING ? "Receiving faction must accept the request." : "No active trade alliance.";
        }
        if (alliance.tradeTier == 0) {
            String quota = alliance.getAssignment("trade.food");
            return "Deposit 5000 coins" + (quota.length() == 0 ? " and roll a trade food quota." : " and " + quota + ".");
        }
        if (alliance.tradeTier == 1) {
            return "Provide 50 farmer population and 10000 coins.";
        }
        return "Trade alliance requirements complete.";
    }

    private String getTradeProgressLabel() {
        if (alliance.tradeTier == 0) {
            return "Coins delivered";
        }
        if (alliance.tradeTier == 1) {
            return "T2 coins delivered";
        }
        return alliance.tradeTier >= 2 ? "Complete" : "Not started";
    }

    private int getTradeDelivered() {
        if (alliance.tradeTier == 0) {
            return Math.min(alliance.getDelivered("trade.coins"), TRADE_T1_COINS_REQUIRED);
        }
        if (alliance.tradeTier == 1) {
            return Math.min(alliance.getDelivered("trade.t2.coins"), TRADE_T2_COINS_REQUIRED);
        }
        return alliance.tradeTier >= 2 ? 1 : 0;
    }

    private int getTradeRequired() {
        if (alliance.tradeTier == 0) {
            return TRADE_T1_COINS_REQUIRED;
        }
        if (alliance.tradeTier == 1) {
            return TRADE_T2_COINS_REQUIRED;
        }
        return alliance.tradeTier >= 2 ? 1 : 0;
    }

    private String getTradeReward() {
        if (alliance.tradeTier < 1) {
            return "Unlock building in receiver land";
        }
        if (alliance.tradeTier == 1) {
            return "Unlock merchant crop production";
        }
        return "Trade benefits unlocked";
    }

    private boolean canViewerClaim() {
        return viewer != null && data.isFactionKing(alliance.factionB, kome.common.KOMEReflection.getEntityUUID(viewer));
    }

    private boolean canViewerDeposit() {
        if (viewer == null) {
            return false;
        }
        if (viewer.canCommandSenderUseCommand(2, "alliance")) {
            return true;
        }
        LOTRFaction faction = LOTRLevelData.getData(viewer).getPledgeFaction();
        String viewerFaction = faction == null ? "" : faction.codeName();
        if (viewerFaction.length() == 0) {
            KOMEPlayerProgression progression = data.getProgression(kome.common.KOMEReflection.getEntityUUID(viewer));
            viewerFaction = progression.getPledgedLordFaction();
        }
        return alliance.factionA.equals(KOMEAlliance.normalizeFactionKey(viewerFaction));
    }

    private String getViewerFactionName() {
        if (viewer == null) {
            return "";
        }
        LOTRFaction faction = LOTRLevelData.getData(viewer).getPledgeFaction();
        if (faction != null) {
            return KOMEAlliance.displayFactionName(faction.codeName());
        }
        KOMEPlayerProgression progression = data.getProgression(kome.common.KOMEReflection.getEntityUUID(viewer));
        return KOMEAlliance.displayFactionName(progression.getPledgedLordFaction());
    }

    private String getClaimSummary() {
        int total = 0;
        int categories = 0;
        for (int i = 0; i < CLAIM_IDS.length; i++) {
            int amount = alliance.getClaimAmount(CLAIM_IDS[i]);
            if (amount > 0) {
                total += amount;
                categories++;
            }
        }
        if (total <= 0) {
            return "No goods are currently claimable.";
        }
        return total + " items/stacks across " + categories + " ledger entries are claimable.";
    }

    private String displayTier(int tier) {
        return tier == KOMEAlliance.PENDING ? "Pending" : tier < 0 ? "None" : "T" + tier;
    }

    private String displayFaction(String key) {
        return KOMEAlliance.displayFactionName(key);
    }

    private String formatFactionName(String key) {
        return KOMEAlliance.displayFactionName(key);
    }

    private void addCoinLine(List lines, String label, String id, int required, boolean active) {
        if (!active && alliance.getDelivered(id) <= 0) {
            return;
        }
        int delivered = Math.min(alliance.getDelivered(id), required);
        lines.add(label + ": " + delivered + "/" + required + " coins" + (delivered >= required ? " complete" : ""));
    }

    private void addQuotaLine(List lines, String label, String id) {
        Quota quota = parseQuota(alliance.getAssignment(id));
        if (quota == null) {
            return;
        }
        int delivered = Math.min(alliance.getDelivered(id), quota.requiredUnits);
        int shownDelivered = quota.stacks ? delivered / 64 : delivered;
        int shownRequired = quota.stacks ? quota.requiredUnits / 64 : quota.requiredUnits;
        String unit = quota.stacks ? "stacks" : "units";
        lines.add(label + ": " + quota.item);
        lines.add("  Delivered: " + shownDelivered + "/" + shownRequired + " " + unit + (delivered >= quota.requiredUnits ? " complete" : ""));
    }

    private boolean matches(ItemStack stack, Quota quota) {
        String wanted = normalize(quota.item);
        return normalize(stack.getDisplayName()).contains(wanted) || normalize(stack.getUnlocalizedName()).contains(wanted);
    }

    private Quota parseQuota(String text) {
        if (text == null || !text.startsWith("Collect ")) {
            return null;
        }
        String rest = text.substring("Collect ".length());
        int firstSpace = rest.indexOf(' ');
        if (firstSpace <= 0) {
            return null;
        }
        int amount = parseInt(rest.substring(0, firstSpace));
        String afterAmount = rest.substring(firstSpace + 1);
        int ofIndex = afterAmount.indexOf(" of ");
        if (amount <= 0 || ofIndex <= 0) {
            return null;
        }
        String unit = afterAmount.substring(0, ofIndex).trim();
        String item = afterAmount.substring(ofIndex + 4).trim();
        boolean stacks = "stacks".equalsIgnoreCase(unit);
        return new Quota(item, stacks ? amount * 64 : amount, stacks);
    }

    private int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private static class Quota {
        private final String item;
        private final int requiredUnits;
        private final boolean stacks;

        private Quota(String item, int requiredUnits, boolean stacks) {
            this.item = item;
            this.requiredUnits = requiredUnits;
            this.stacks = stacks;
        }
    }
}
