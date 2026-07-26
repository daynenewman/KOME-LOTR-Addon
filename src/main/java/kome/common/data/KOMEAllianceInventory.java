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
        KOMEAllianceProgressionService.refreshCurrentStageReadiness(
            data, alliance, ledgerFaction, System.currentTimeMillis());
    }

    private boolean isAcceptedRequirement(ItemStack stack) {
        return matchesActiveQuota(stack);
    }

    private boolean matchesActiveQuota(ItemStack stack) {
        int targetStage = alliance.getFactionStage(ledgerFaction) + 1;
        if (targetStage < 1 || targetStage > 4) return false;
        String type = KOMEAllianceProgressionService.stageQuotaType(targetStage);
        int tier = KOMEAllianceProgressionService.stageQuotaTier(targetStage);
        String id = KOMEAllianceProgressionService.stageRequirementId(targetStage);
        KOMEAllianceQuotaPool.Requirement requirement =
            KOMEAllianceQuotaPool.resolve(data, alliance, ledgerFaction, type, tier);
        return requirement != null && requirement.isValid()
            && alliance.getDelivered(ledgerFaction, id) < requirement.requiredUnits
            && requirement.matches(stack);
    }

    private boolean depositQuotaStack(ItemStack stack) {
        int targetStage = alliance.getFactionStage(ledgerFaction) + 1;
        if (targetStage < 1 || targetStage > 4) return false;
        String type = KOMEAllianceProgressionService.stageQuotaType(targetStage);
        int tier = KOMEAllianceProgressionService.stageQuotaTier(targetStage);
        String id = KOMEAllianceProgressionService.stageRequirementId(targetStage);
        KOMEAllianceQuotaPool.Requirement requirement =
            KOMEAllianceQuotaPool.resolve(data, alliance, ledgerFaction, type, tier);
        if (requirement == null || !requirement.isValid() || !requirement.matches(stack)) return false;
        int needed = requirement.requiredUnits - alliance.getDelivered(ledgerFaction, id);
        if (needed <= 0) return false;
        int taken = Math.min(stack.stackSize, needed);
        ItemStack sample = stack.copy();
        sample.stackSize = 1;
        alliance.addDelivered(ledgerFaction, id, taken);
        if (targetStage == 2) alliance.addClaimGoods(ledgerFaction, id, sample, taken);
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
        if (alliance.getRelationshipStatus() == KOMEAllianceTrackStatus.PENDING) {
            addProgressLine(lines, "Relationship", KOMEAlliance.PENDING,
                "The receiving king must accept before goods can be delivered.", "", 0, 0, "Stage 0");
        } else if (alliance.getRelationshipStatus() == KOMEAllianceTrackStatus.ACTIVE) {
            int stage = alliance.getFactionStage(ledgerFaction);
            int targetStage = stage + 1;
            if (targetStage <= 4) {
                String type = KOMEAllianceProgressionService.stageQuotaType(targetStage);
                int quotaTier = KOMEAllianceProgressionService.stageQuotaTier(targetStage);
                String id = KOMEAllianceProgressionService.stageRequirementId(targetStage);
                KOMEAllianceQuotaPool.Requirement requirement =
                    KOMEAllianceQuotaPool.resolve(data, alliance, ledgerFaction, type, quotaTier);
                int required = requirement == null ? 0 : requirement.requiredUnits;
                int delivered = Math.min(alliance.getDelivered(ledgerFaction, id), required);
                String label = "Stage " + targetStage;
                String fixed = KOMEAllianceProgressionService.fixedMilestoneDescription(
                    data, alliance, ledgerFaction, targetStage);
                addProgressLine(lines, label, stage, "Next: " + KOMEAlliance.stageName(targetStage)
                    + " | " + fixed, "Goods delivered", delivered, required,
                    KOMEAlliance.stageName(targetStage));
                addQuotaProgressLine(lines, label, id, type, quotaTier);
            } else {
                addProgressLine(lines, "Stage 4", 4, "All alliance stages claimed.", "",
                    0, 0, KOMEAlliance.stageName(4));
            }
        }
        lines.add("DEPOSIT\tPlace only the exact server-rolled supplies for this side's next stage in the ledger slots. Stage 2 support goods remain claimable by the partner; other stage goods are consumed as contributions.");
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
        String status = type != null && type.startsWith("Stage ") && tier >= 0
            ? "Current Stage " + tier : displayTier(tier);
        lines.add("PROGRESS\t" + type + "\t" + status + "\t" + requirement + "\t"
            + progressLabel + "\t" + delivered + "\t" + required + "\t" + reward);
    }

    private void addQuotaProgressLine(List lines, String label, String id, String type, int targetTier) {
        KOMEAllianceQuotaPool.Requirement requirement =
            KOMEAllianceQuotaPool.resolve(data, alliance, ledgerFaction, type, targetTier);
        if (requirement == null) {
            lines.add("QUOTA\t" + label + "\t\t0\t0\t");
            return;
        }
        if (!requirement.isValid()) {
            lines.add("QUOTA\t" + label + "\tINVALID_REQUIREMENT: " + requirement.invalidReason
                + "\t0\t0\toperator reroll required");
            return;
        }
        int delivered = Math.min(alliance.getDelivered(ledgerFaction, id), requirement.requiredUnits);
        lines.add("QUOTA\t" + label + "\t" + requirement.displayName + "\t"
            + delivered + "\t" + requirement.requiredUnits + "\titems");
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

}
