package kome.common.data;

import kome.common.command.KOMECommandAlliance;
import kome.common.network.KOMEPacketHandler;
import kome.common.network.KOMEPacketQuotaLedger;
import lotr.common.LOTRLevelData;
import lotr.common.fac.LOTRFaction;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class KOMEAllianceInventory implements IInventory {
    public static final int CIVIL_T2_TRADE_REQUIRED = 100;
    public static final int TRADE_T1_TRADE_REQUIRED = 50;
    public static final int MILITARY_T1_KILLS_REQUIRED = 50;
    public static final int MILITARY_T2_KILLS_REQUIRED = 500;
    public static final int MILITARY_T3_KILLS_REQUIRED = 1000;
    public static final int MILITARY_T1_POP_REQUIRED = 50;
    public static final int MILITARY_T2_POP_REQUIRED = 150;
    public static final int MILITARY_T3_POP_REQUIRED = 300;
    // Retired IDs remain claimable only so migration never destroys deposited legacy goods.
    private static final String[] CLAIM_IDS = new String[] {"civil.coins", "trade.coins", "trade.t2.coins", "military.t4.coins", "military.food", "trade.food"};
    private final KOMEWorldData data;
    private final KOMEAlliance alliance;
    private final EntityPlayerMP viewer;
    private final String ledgerFaction;
    private final String name;

    public KOMEAllianceInventory(KOMEWorldData data, KOMEAlliance alliance, EntityPlayerMP viewer, String ledgerFaction) {
        this.data = data;
        this.alliance = alliance;
        this.viewer = viewer;
        this.ledgerFaction = KOMEAlliance.normalizeFactionKey(ledgerFaction);
        this.name = "Alliance Ledger";
        sendLedger();
    }

    @Override
    public int getSizeInventory() {
        return KOMEAlliance.STORAGE_SLOTS;
    }

    @Override
    public ItemStack getStackInSlot(int slotIn) {
        return alliance.getStorage(ledgerFaction, slotIn);
    }

    @Override
    public ItemStack decrStackSize(int index, int count) {
        ItemStack stack = alliance.decrStorage(ledgerFaction, index, count);
        if (stack != null) {
            markDirty();
        }
        return stack;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int index) {
        ItemStack stack = alliance.getStorage(ledgerFaction, index);
        alliance.setStorage(ledgerFaction, index, null);
        markDirty();
        return stack;
    }

    @Override
    public void setInventorySlotContents(int index, ItemStack stack) {
        if (stack != null && stack.stackSize > getInventoryStackLimit()) {
            stack.stackSize = getInventoryStackLimit();
        }
        alliance.setStorage(ledgerFaction, index, absorbStack(stack));
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
        KOMECommandAlliance.sendAllianceRefreshToParticipants(data, alliance);
        if (viewer != null && viewer.openContainer != null) {
            viewer.openContainer.detectAndSendChanges();
        }
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP) || player != viewer || data.getAlliance(alliance.factionA, alliance.factionB, false) != alliance) {
            return false;
        }
        return new KOMEAllianceAuthority(data).canUseAllianceLedger((EntityPlayerMP) player, alliance, false).allowed;
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
        KOMEAllianceProgressionService.refreshFactionCompletion(data, alliance, ledgerFaction, KOMEAlliance.CIVIL, alliance.updatedWorldTime);
        KOMEAllianceProgressionService.refreshFactionCompletion(data, alliance, ledgerFaction, KOMEAlliance.TRADE, alliance.updatedWorldTime);
        KOMEAllianceProgressionService.refreshFactionCompletion(data, alliance, ledgerFaction, KOMEAlliance.MILITARY, alliance.updatedWorldTime);
    }

    private boolean isAcceptedRequirement(ItemStack stack) {
        return matchesActiveQuota(stack);
    }

    private boolean matchesActiveQuota(ItemStack stack) {
        String[] types = new String[] {KOMEAlliance.CIVIL, KOMEAlliance.TRADE, KOMEAlliance.MILITARY};
        for (int i = 0; i < types.length; i++) {
            int target = tier(types[i]) + 1;
            if (target < 1 || target > KOMEAlliance.maxTier(types[i])) {
                continue;
            }
            String id = KOMEAllianceQuotaPool.assignmentId(types[i], target);
            KOMEAllianceQuotaPool.Requirement requirement = KOMEAllianceQuotaPool.resolve(data, alliance, ledgerFaction, types[i], target);
            if (requirement != null && requirement.isValid()
                    && alliance.getDelivered(ledgerFaction, id) < requirement.requiredUnits && requirement.matches(stack)) {
                return true;
            }
        }
        return false;
    }

    private int getNeededCoinValue() {
        return 0;
    }

    private boolean depositQuotaStack(ItemStack stack) {
        return depositCurrentRequirement(stack, KOMEAlliance.CIVIL)
            || depositCurrentRequirement(stack, KOMEAlliance.TRADE)
            || depositCurrentRequirement(stack, KOMEAlliance.MILITARY);
    }

    private boolean depositCurrentRequirement(ItemStack stack, String type) {
        int target = tier(type) + 1;
        if (target < 1 || target > KOMEAlliance.maxTier(type)) {
            return false;
        }
        String id = KOMEAllianceQuotaPool.assignmentId(type, target);
        KOMEAllianceQuotaPool.Requirement requirement = KOMEAllianceQuotaPool.resolve(data, alliance, ledgerFaction, type, target);
        if (requirement == null || !requirement.isValid() || !requirement.matches(stack)) {
            return false;
        }
        int needed = requirement.requiredUnits - alliance.getDelivered(ledgerFaction, id);
        if (needed <= 0) {
            return false;
        }
        int taken = Math.min(stack.stackSize, needed);
        ItemStack sample = stack.copy();
        sample.stackSize = 1;
        alliance.addDelivered(ledgerFaction, id, taken);
        if (KOMEAlliance.TRADE.equals(type)) {
            alliance.addClaimGoods(ledgerFaction, id, sample, taken);
        }
        stack.stackSize -= taken;
        return true;
    }

    private ItemStack absorbStack(ItemStack stack) {
        if (canViewerDeposit() && stack != null && depositQuotaStack(stack) && stack.stackSize <= 0) {
            return null;
        }
        return stack;
    }

    private void absorbStoredStacks() {
        for (int i = 0; i < getSizeInventory(); i++) {
            ItemStack stack = alliance.getStorage(ledgerFaction, i);
            if (stack != null) {
                alliance.setStorage(ledgerFaction, i, absorbStack(stack));
            }
        }
    }

    private boolean depositQuotaStack(ItemStack stack, String id, int tier) {
        Quota quota = parseQuota(alliance.getAssignment(ledgerFaction, id));
        if (tier != 0 || quota == null || !matches(stack, quota)) {
            return false;
        }
        int needed = quota.requiredUnits - alliance.getDelivered(ledgerFaction, id);
        if (needed <= 0) {
            return false;
        }
        int taken = Math.min(stack.stackSize, needed);
        ItemStack sample = stack.copy();
        sample.stackSize = 1;
        alliance.addDelivered(ledgerFaction, id, taken);
        alliance.addClaimGoods(ledgerFaction, id, sample, taken);
        stack.stackSize -= taken;
        return true;
    }

    private void sendLedger() {
        if (viewer == null) {
            return;
        }
        KOMEPacketHandler.network.sendTo(new KOMEPacketQuotaLedger(getLedgerLines()), viewer);
    }

    /** Only the contributing side may take back a stack remainder that exceeded a rolled quota. */
    public boolean canViewerTakeRemainder() {
        return canViewerDeposit();
    }

    public void refreshViewer() {
        sendLedger();
    }

    private List getLedgerLines() {
        List lines = new ArrayList();
        boolean canClaim = canViewerClaim();
        String receivingFaction = alliance.getOtherFaction(ledgerFaction);
        lines.add(buildSummaryLine(alliance, ledgerFaction));
        lines.add("VIEWER\t" + getViewerFactionName() + "\t" + (canViewerDeposit() ? "1" : "0") + "\t" + (canClaim ? "1" : "0"));
        lines.add("SWITCH\tView " + displayFaction(receivingFaction) + " Ledger\t" + receivingFaction + "\t" + ledgerFaction + "\t" + (canViewerOpenLedger(receivingFaction, ledgerFaction) ? "1" : "0"));
        if (tier(KOMEAlliance.CIVIL) != KOMEAlliance.NONE) {
            addProgressLine(lines, "Civil", tier(KOMEAlliance.CIVIL), getCivilRequirement(), getCivilProgressLabel(), getCivilDelivered(), getCivilRequired(), getCivilReward());
            addQuotaProgressLine(lines, "Civil", currentRequirementId(KOMEAlliance.CIVIL));
        }
        if (tier(KOMEAlliance.MILITARY) != KOMEAlliance.NONE) {
            addProgressLine(lines, "Military", tier(KOMEAlliance.MILITARY), getMilitaryRequirement(), getMilitaryProgressLabel(), getMilitaryDelivered(), getMilitaryRequired(), getMilitaryReward());
            addQuotaProgressLine(lines, "Military", currentRequirementId(KOMEAlliance.MILITARY));
        }
        if (tier(KOMEAlliance.TRADE) != KOMEAlliance.NONE) {
            addProgressLine(lines, "Trade", tier(KOMEAlliance.TRADE), getTradeRequirement(), getTradeProgressLabel(), getTradeDelivered(), getTradeRequired(), getTradeReward());
            addQuotaProgressLine(lines, "Trade", currentRequirementId(KOMEAlliance.TRADE));
        }
        addFactionSideLine(lines, alliance.factionA);
        addFactionSideLine(lines, alliance.factionB);
        lines.add("DEPOSIT\tPlace only the exact server-rolled supplies in the chest slots. New reciprocal coin payments and legacy food quotas are not accepted. Civil and Military projects consume supplies; completed Trade exchanges make goods claimable by the opposite faction.");
        lines.add("CLAIM\t" + getClaimSummary() + "\t" + (canClaim ? "1" : "0") + "\t" + (canClaim ? "Receiving faction king" : "Only the receiving faction king can claim"));
        return lines;
    }

    static String buildSummaryLine(KOMEAlliance alliance, String contributingFaction) {
        String contributor = KOMEAlliance.normalizeFactionKey(contributingFaction);
        if (alliance == null || !alliance.involves(contributor)) {
            contributor = alliance == null ? "" : alliance.factionA;
        }
        String receiver = alliance == null ? "" : alliance.getOtherFaction(contributor);
        return "SUMMARY\t" + KOMEAlliance.displayFactionName(contributor) + "\t" + KOMEAlliance.displayFactionName(receiver) + "\t"
            + contributor + "\t" + receiver + "\t" + (alliance == null ? "" : alliance.getPairKey());
    }

    private void addProgressLine(List lines, String type, int tier, String requirement, String progressLabel, int delivered, int required, String reward) {
        lines.add("PROGRESS\t" + type + "\t" + displayTier(tier) + "\t" + requirement + "\t" + progressLabel + "\t" + delivered + "\t" + required + "\t" + reward);
    }

    private void addQuotaProgressLine(List lines, String type, String id) {
        int typeIndex = "Civil".equals(type) ? 0 : "Military".equals(type) ? 1 : 2;
        String typeKey = typeIndex == 0 ? KOMEAlliance.CIVIL : typeIndex == 1 ? KOMEAlliance.MILITARY : KOMEAlliance.TRADE;
        int target = Math.max(1, Math.min(KOMEAlliance.maxTier(typeKey), tier(typeKey) + 1));
        KOMEAllianceQuotaPool.Requirement requirement = KOMEAllianceQuotaPool.resolve(data, alliance, ledgerFaction, typeKey, target);
        if (requirement != null) {
            if (!requirement.isValid()) {
                lines.add("QUOTA\t" + type + "\tINVALID_REQUIREMENT: " + requirement.invalidReason + "\t0\t0\toperator reroll required");
                return;
            }
            int delivered = Math.min(alliance.getDelivered(ledgerFaction, id), requirement.requiredUnits);
            lines.add("QUOTA\t" + type + "\t" + requirement.displayName + "\t" + delivered + "\t" + requirement.requiredUnits + "\titems");
            return;
        }
        Quota quota = parseQuota(alliance.getAssignment(ledgerFaction, id));
        if (quota == null) {
            lines.add("QUOTA\t" + type + "\t\t0\t0\t");
            return;
        }
        int delivered = Math.min(alliance.getDelivered(ledgerFaction, id), quota.requiredUnits);
        int shownDelivered = quota.stacks ? delivered / 64 : delivered;
        int shownRequired = quota.stacks ? quota.requiredUnits / 64 : quota.requiredUnits;
        String unit = quota.stacks ? "stacks" : "items";
        lines.add("QUOTA\t" + type + "\t" + quota.item + "\t" + shownDelivered + "\t" + shownRequired + "\t" + unit);
    }

    private String currentRequirementId(String type) {
        int target = Math.max(1, tier(type) + 1);
        return KOMEAllianceQuotaPool.assignmentId(type, Math.min(KOMEAlliance.maxTier(type), target));
    }

    private void addFactionSideLine(List lines, String faction) {
        KOMEAllianceFactionLedger ledger = alliance.getFactionLedger(faction);
        if (ledger == null) {
            return;
        }
        long deadline = Math.max(ledger.graceEndMillis, ledger.successionEndMillis);
        lines.add("SIDE\t" + faction + "\t" + displayFaction(faction) + "\t"
            + ledger.getCompletedTier(KOMEAlliance.CIVIL) + "\t" + ledger.getCompletedTier(KOMEAlliance.TRADE) + "\t"
            + ledger.getCompletedTier(KOMEAlliance.MILITARY) + "\t" + (ledger.kinglessWaived ? "1" : "0") + "\t" + deadline);
    }

    private String getCivilRequirement() {
        if (tier(KOMEAlliance.CIVIL) < 0) {
            return tier(KOMEAlliance.CIVIL) == KOMEAlliance.PENDING ? "Receiving faction must accept the request." : "No active civil alliance.";
        }
        int target = tier(KOMEAlliance.CIVIL) + 1;
        return currentRequirementText(KOMEAlliance.CIVIL, target == 2
            ? " and complete " + data.getAllianceActivityRequirement(KOMEAlliance.CIVIL, 2) + " legitimate allied trades" : "");
    }

    private String getCivilProgressLabel() {
        if (tier(KOMEAlliance.CIVIL) >= 0 && tier(KOMEAlliance.CIVIL) < KOMEAlliance.maxTier(KOMEAlliance.CIVIL)) {
            return "Supplies delivered";
        }
        return tier(KOMEAlliance.CIVIL) >= 2 ? "Complete" : "Not started";
    }

    private int getCivilDelivered() {
        if (tier(KOMEAlliance.CIVIL) >= 0 && tier(KOMEAlliance.CIVIL) < KOMEAlliance.maxTier(KOMEAlliance.CIVIL)) {
            return currentRequirementDelivered(KOMEAlliance.CIVIL);
        }
        return tier(KOMEAlliance.CIVIL) >= 2 ? 1 : 0;
    }

    private int getCivilRequired() {
        if (tier(KOMEAlliance.CIVIL) >= 0 && tier(KOMEAlliance.CIVIL) < KOMEAlliance.maxTier(KOMEAlliance.CIVIL)) {
            return currentRequirementRequired(KOMEAlliance.CIVIL);
        }
        return tier(KOMEAlliance.CIVIL) >= 2 ? 1 : 0;
    }

    private String getCivilReward() {
        return nextBenefit(KOMEAlliance.CIVIL);
    }

    private String getMilitaryRequirement() {
        if (tier(KOMEAlliance.MILITARY) < 0) {
            return tier(KOMEAlliance.MILITARY) == KOMEAlliance.PENDING ? "Receiving faction must accept the request." : "No active military alliance.";
        }
        int target = tier(KOMEAlliance.MILITARY) + 1;
        String extra = target >= 1 && target <= 3 ? " and reach "
            + data.getAllianceActivityRequirement(KOMEAlliance.MILITARY, target) + " cumulative eligible kills plus "
            + data.getAlliancePopulationRequirement(KOMEAlliance.MILITARY, target) + " effective offensive population" : "";
        return currentRequirementText(KOMEAlliance.MILITARY, extra);
    }

    private String getMilitaryProgressLabel() {
        if (tier(KOMEAlliance.MILITARY) >= 0 && tier(KOMEAlliance.MILITARY) < KOMEAlliance.maxTier(KOMEAlliance.MILITARY)) {
            return "Supplies delivered";
        }
        return tier(KOMEAlliance.MILITARY) >= 3 ? "Complete" : "Status";
    }

    private int getMilitaryDelivered() {
        if (tier(KOMEAlliance.MILITARY) >= 0 && tier(KOMEAlliance.MILITARY) < KOMEAlliance.maxTier(KOMEAlliance.MILITARY)) {
            return currentRequirementDelivered(KOMEAlliance.MILITARY);
        }
        return tier(KOMEAlliance.MILITARY) >= 3 ? 1 : 0;
    }

    private int getMilitaryRequired() {
        if (tier(KOMEAlliance.MILITARY) >= 0 && tier(KOMEAlliance.MILITARY) < KOMEAlliance.maxTier(KOMEAlliance.MILITARY)) {
            return currentRequirementRequired(KOMEAlliance.MILITARY);
        }
        return tier(KOMEAlliance.MILITARY) >= 3 ? 1 : 0;
    }

    private String getMilitaryReward() {
        return nextBenefit(KOMEAlliance.MILITARY);
    }

    private String getTradeRequirement() {
        if (tier(KOMEAlliance.TRADE) < 0) {
            return tier(KOMEAlliance.TRADE) == KOMEAlliance.PENDING ? "Receiving faction must accept the request." : "No active trade alliance.";
        }
        int target = tier(KOMEAlliance.TRADE) + 1;
        String extra = target == 1 || target == 2 ? " and complete "
            + data.getAllianceActivityRequirement(KOMEAlliance.TRADE, target) + " cumulative legitimate allied trades" : "";
        return currentRequirementText(KOMEAlliance.TRADE, extra);
    }

    private String getTradeProgressLabel() {
        if (tier(KOMEAlliance.TRADE) >= 0 && tier(KOMEAlliance.TRADE) < KOMEAlliance.maxTier(KOMEAlliance.TRADE)) {
            return "Goods delivered";
        }
        return tier(KOMEAlliance.TRADE) >= 2 ? "Complete" : "Not started";
    }

    private int getTradeDelivered() {
        if (tier(KOMEAlliance.TRADE) >= 0 && tier(KOMEAlliance.TRADE) < KOMEAlliance.maxTier(KOMEAlliance.TRADE)) {
            return currentRequirementDelivered(KOMEAlliance.TRADE);
        }
        return tier(KOMEAlliance.TRADE) >= 2 ? 1 : 0;
    }

    private int getTradeRequired() {
        if (tier(KOMEAlliance.TRADE) >= 0 && tier(KOMEAlliance.TRADE) < KOMEAlliance.maxTier(KOMEAlliance.TRADE)) {
            return currentRequirementRequired(KOMEAlliance.TRADE);
        }
        return tier(KOMEAlliance.TRADE) >= 2 ? 1 : 0;
    }

    private String getTradeReward() {
        return nextBenefit(KOMEAlliance.TRADE);
    }

    private String nextBenefit(String type) {
        int next = tier(type) + 1;
        if (next < 1 || next > KOMEAlliance.maxTier(type)) {
            return displayType(type) + " track complete";
        }
        return KOMEAllianceBenefits.get(type, next).title;
    }

    private String currentRequirementText(String type, String extra) {
        int target = tier(type) + 1;
        if (target > KOMEAlliance.maxTier(type)) {
            return displayFaction(ledgerFaction) + " has completed this alliance track.";
        }
        String id = KOMEAllianceQuotaPool.assignmentId(type, target);
        KOMEAllianceQuotaPool.Requirement requirement = KOMEAllianceQuotaPool.resolve(data, alliance, ledgerFaction, type, target);
        if (requirement == null) {
            return "Reveal the " + displayType(type) + " T" + target + " faction quota.";
        }
        if (!requirement.isValid()) {
            return "INVALID_REQUIREMENT: " + requirement.invalidReason + ". An operator must reroll it; delivered goods remain recoverable.";
        }
        return requirement.display() + (extra == null ? "" : extra) + ".";
    }

    private int currentRequirementDelivered(String type) {
        int target = tier(type) + 1;
        if (target > KOMEAlliance.maxTier(type)) {
            return 1;
        }
        String id = KOMEAllianceQuotaPool.assignmentId(type, target);
        KOMEAllianceQuotaPool.Requirement requirement = KOMEAllianceQuotaPool.resolve(data, alliance, ledgerFaction, type, target);
        return requirement == null || !requirement.isValid() ? 0
            : Math.min(requirement.requiredUnits, alliance.getDelivered(ledgerFaction, id));
    }

    private int currentRequirementRequired(String type) {
        int target = tier(type) + 1;
        if (target > KOMEAlliance.maxTier(type)) {
            return 1;
        }
        KOMEAllianceQuotaPool.Requirement requirement = KOMEAllianceQuotaPool.resolve(data, alliance, ledgerFaction, type, target);
        return requirement == null || !requirement.isValid() ? 0 : requirement.requiredUnits;
    }

    private String displayType(String type) {
        String normalized = KOMEAlliance.normalizeType(type);
        return normalized.length() == 0 ? "Alliance" : Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private int tier(String type) {
        return alliance.getFactionTier(ledgerFaction, type);
    }

    private boolean canViewerClaim() {
        return viewer != null && data.isFactionKing(alliance.getOtherFaction(ledgerFaction), kome.common.KOMEReflection.getEntityUUID(viewer));
    }

    private boolean canViewerDeposit() {
        if (viewer == null) {
            return false;
        }
        if (viewer.canCommandSenderUseCommand(2, "alliance")) {
            return true;
        }
        return ledgerFaction.equals(getViewerFactionKey());
    }

    private boolean canViewerOpenLedger(String contributingFaction, String receivingFaction) {
        if (viewer == null) {
            return false;
        }
        if (viewer.canCommandSenderUseCommand(2, "alliance")) {
            return true;
        }
        return KOMEAlliance.normalizeFactionKey(contributingFaction).equals(getViewerFactionKey())
            || data.isFactionKing(receivingFaction, kome.common.KOMEReflection.getEntityUUID(viewer));
    }

    private String getViewerFactionKey() {
        if (viewer == null) {
            return "";
        }
        LOTRFaction faction = LOTRLevelData.getData(viewer).getPledgeFaction();
        String viewerFaction = faction == null ? "" : faction.codeName();
        if (viewerFaction.length() == 0) {
            KOMEPlayerProgression progression = data.getProgression(kome.common.KOMEReflection.getEntityUUID(viewer));
            viewerFaction = progression.getPledgedLordFaction();
        }
        return KOMEAlliance.normalizeFactionKey(viewerFaction);
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
        KOMEAllianceFactionLedger ledger = alliance.getFactionLedger(ledgerFaction);
        if (ledger == null) {
            return "No goods are currently claimable.";
        }
        for (String id : ledger.getClaimIds()) {
            int amount = alliance.getClaimAmount(ledgerFaction, id);
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
        if (!active && alliance.getDelivered(ledgerFaction, id) <= 0) {
            return;
        }
        int delivered = Math.min(alliance.getDelivered(ledgerFaction, id), required);
        lines.add(label + ": " + delivered + "/" + required + " coins" + (delivered >= required ? " complete" : ""));
    }

    private void addQuotaLine(List lines, String label, String id) {
        Quota quota = parseQuota(alliance.getAssignment(ledgerFaction, id));
        if (quota == null) {
            return;
        }
        int delivered = Math.min(alliance.getDelivered(ledgerFaction, id), quota.requiredUnits);
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
