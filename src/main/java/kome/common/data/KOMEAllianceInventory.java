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
        return true;
    }

    private void applyCoinUnlocks() {
        int coins = getCoinValue();
        if (alliance.civilTier == 0 && coins >= 1000) {
            alliance.setTier(KOMEAlliance.CIVIL, 1, "Alliance goods", alliance.updatedWorldTime);
        }
        if (alliance.tradeTier == 0 && coins >= 5000) {
            alliance.setTier(KOMEAlliance.TRADE, 1, "Alliance goods", alliance.updatedWorldTime);
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
}
