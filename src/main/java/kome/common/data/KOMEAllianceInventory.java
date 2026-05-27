package kome.common.data;

import lotr.common.item.LOTRItemCoin;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

public class KOMEAllianceInventory implements IInventory {
    private final KOMEWorldData data;
    private final KOMEAlliance alliance;
    private final String name;

    public KOMEAllianceInventory(KOMEWorldData data, KOMEAlliance alliance) {
        this.data = data;
        this.alliance = alliance;
        this.name = "Alliance Goods";
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
        processFoodQuota("military.food");
        processFoodQuota("trade.food");
        applyCoinUnlocks();
        data.markDirty();
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
        int coins = getCoinValue();
        if (alliance.civilTier == 0 && coins >= 1000) {
            alliance.setTier(KOMEAlliance.CIVIL, 1, "Alliance goods", alliance.updatedWorldTime);
        }
        if (alliance.tradeTier == 0 && coins >= 5000) {
            Quota quota = parseQuota(alliance.getAssignment("trade.food"));
            if (quota == null || alliance.getDelivered("trade.food") >= quota.requiredUnits) {
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

    private void processFoodQuota(String id) {
        Quota quota = parseQuota(alliance.getAssignment(id));
        if (quota == null) {
            return;
        }
        int deposited = 0;
        for (int i = 0; i < getSizeInventory(); i++) {
            ItemStack stack = getStackInSlot(i);
            if (stack == null || !matches(stack, quota)) {
                continue;
            }
            deposited += stack.stackSize;
        }
        alliance.setDelivered(id, Math.min(deposited, quota.requiredUnits));
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
            needed += 1000;
        }
        if (alliance.tradeTier == 0) {
            needed += 5000;
        }
        return Math.max(0, needed - getCoinValue());
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
