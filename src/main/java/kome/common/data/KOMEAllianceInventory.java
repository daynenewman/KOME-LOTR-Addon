package kome.common.data;

import kome.common.network.KOMEPacketHandler;
import kome.common.network.KOMEPacketQuotaLedger;
import lotr.common.item.LOTRItemCoin;
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
    private final String name;

    public KOMEAllianceInventory(KOMEWorldData data, KOMEAlliance alliance, EntityPlayerMP viewer) {
        this.data = data;
        this.alliance = alliance;
        this.viewer = viewer;
        this.name = "Alliance Ledger";
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
        if (stack != null && (depositQuotaStack(stack) || depositCoinStack(stack))) {
            stack = stack.stackSize > 0 ? stack : null;
        }
        alliance.setStorage(index, stack);
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
        applyCoinUnlocks();
        data.markDirty();
        sendLedger();
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        return true;
    }

    @Override
    public void openInventory() {
    }

    @Override
    public void closeInventory() {
        markDirty();
    }

    @Override
    public boolean isItemValidForSlot(int index, ItemStack stack) {
        return stack != null && isAcceptedRequirement(stack);
    }

    private void applyCoinUnlocks() {
        if (alliance.civilTier == 0 && alliance.getDelivered("civil.coins") >= 1000) {
            alliance.setTier(KOMEAlliance.CIVIL, 1, "Alliance goods", alliance.updatedWorldTime);
        }
        if (alliance.tradeTier == 0 && alliance.getDelivered("trade.coins") >= 5000) {
            Quota quota = parseQuota(alliance.getAssignment("trade.food"));
            if (quota != null && alliance.getDelivered("trade.food") >= quota.requiredUnits) {
                alliance.setTier(KOMEAlliance.TRADE, 1, "Alliance goods", alliance.updatedWorldTime);
            }
        }
        Quota militaryQuota = parseQuota(alliance.getAssignment("military.food"));
        if (alliance.militaryTier == 0 && militaryQuota != null && alliance.getDelivered("military.food") >= militaryQuota.requiredUnits) {
            alliance.setTier(KOMEAlliance.MILITARY, 1, "Alliance goods", alliance.updatedWorldTime);
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
            needed += Math.max(0, 5000 - alliance.getDelivered("trade.coins"));
        }
        return needed;
    }

    private boolean depositQuotaStack(ItemStack stack) {
        return depositQuotaStack(stack, "military.food", alliance.militaryTier)
            || depositQuotaStack(stack, "trade.food", alliance.tradeTier);
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
            || depositCoins(stack, value, "trade.coins", 5000, alliance.tradeTier);
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
        addCoinLine(lines, "Civil Coins", "civil.coins", 1000, alliance.civilTier == 0);
        addQuotaLine(lines, "Military Food", "military.food");
        addCoinLine(lines, "Trade Coins", "trade.coins", 5000, alliance.tradeTier == 0);
        addQuotaLine(lines, "Trade Food", "trade.food");
        return lines;
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
