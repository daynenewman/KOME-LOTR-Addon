package kome.common.gui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

public class KOMEContainerAllianceLedger extends Container {
    public static final int LEDGER_SLOTS = 9;
    public static final int GUI_WIDTH = 620;
    public static final int GUI_HEIGHT = 460;
    public static final int DEPOSIT_X = 420;
    public static final int DEPOSIT_Y = 276;
    public static final int INVENTORY_X = 229;
    public static final int INVENTORY_Y = 338;
    public static final int HOTBAR_Y = 394;

    private final IInventory ledgerInventory;

    public KOMEContainerAllianceLedger(IInventory playerInventory, IInventory ledgerInventory) {
        this.ledgerInventory = ledgerInventory;
        ledgerInventory.openInventory();
        int i;
        int j;
        for (i = 0; i < LEDGER_SLOTS; i++) {
            addSlotToContainer(new Slot(ledgerInventory, i, DEPOSIT_X + i * 18, DEPOSIT_Y));
        }
        for (j = 0; j < 3; j++) {
            for (i = 0; i < 9; i++) {
                addSlotToContainer(new Slot(playerInventory, i + j * 9 + 9, INVENTORY_X + i * 18, INVENTORY_Y + j * 18));
            }
        }
        for (i = 0; i < 9; i++) {
            addSlotToContainer(new Slot(playerInventory, i, INVENTORY_X + i * 18, HOTBAR_Y));
        }
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return ledgerInventory.isUseableByPlayer(player);
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        ItemStack copy = null;
        Slot slot = (Slot) inventorySlots.get(index);
        if (slot != null && slot.getHasStack()) {
            ItemStack stack = slot.getStack();
            copy = stack.copy();
            if (index < LEDGER_SLOTS) {
                if (!mergeItemStack(stack, LEDGER_SLOTS, inventorySlots.size(), true)) {
                    return null;
                }
            } else if (!mergeItemStack(stack, 0, LEDGER_SLOTS, false)) {
                return null;
            }
            if (stack.stackSize == 0) {
                slot.putStack(null);
            } else {
                slot.onSlotChanged();
            }
        }
        return copy;
    }

    @Override
    public void onContainerClosed(EntityPlayer player) {
        super.onContainerClosed(player);
        ledgerInventory.closeInventory();
    }
}
