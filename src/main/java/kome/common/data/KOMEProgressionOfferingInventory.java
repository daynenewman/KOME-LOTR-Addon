package kome.common.data;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

public class KOMEProgressionOfferingInventory implements IInventory {
    private final KOMEWorldData data;
    private final KOMEPlayerProgression progression;
    private final EntityPlayerMP player;
    private final String name;

    public KOMEProgressionOfferingInventory(KOMEWorldData data, KOMEPlayerProgression progression) {
        this(data, progression, null);
    }

    public KOMEProgressionOfferingInventory(KOMEWorldData data, KOMEPlayerProgression progression, EntityPlayerMP player) {
        this.data = data;
        this.progression = progression;
        this.player = player;
        this.name = "Lord Offerings";
    }

    @Override
    public int getSizeInventory() {
        return progression.getOfferingSlots();
    }

    @Override
    public ItemStack getStackInSlot(int slotIn) {
        return progression.getOffering(slotIn);
    }

    @Override
    public ItemStack decrStackSize(int index, int count) {
        ItemStack stack = progression.decrOffering(index, count);
        if (stack != null) {
            markDirty();
        }
        return stack;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int index) {
        ItemStack stack = progression.getOffering(index);
        progression.setOffering(index, null);
        markDirty();
        return stack;
    }

    @Override
    public void setInventorySlotContents(int index, ItemStack stack) {
        if (stack != null && stack.stackSize > getInventoryStackLimit()) {
            stack.stackSize = getInventoryStackLimit();
        }
        progression.setOffering(index, stack);
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
        int completedBefore = progression.getCompletedCount(null);
        KOMEProgressionQuotas.processDeposits(progression);
        KOMEProgressionAutoCompleter.applyUnlocks(progression);
        data.markDirty();
        if (player != null) {
            if (progression.getCompletedCount(null) != completedBefore) {
                KOMEProgressionAutoCompleter.syncPlayer(player, progression);
                KOMEProgressionTitles.updatePlayerTitle(player);
            }
            KOMEProgressionQuotas.sendQuotaLedger(player, progression);
        }
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
}
