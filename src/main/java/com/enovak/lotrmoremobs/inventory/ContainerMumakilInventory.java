package com.enovak.lotrmoremobs.inventory;

import com.enovak.lotrmoremobs.Main;
import com.enovak.lotrmoremobs.entity.animal.LOTREntityMumakil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.ContainerHorseInventory;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

public class ContainerMumakilInventory extends ContainerHorseInventory {
    // BABY_INVENTORY_AND_PLAYER_SPEED_FIX_V1
    // CHILD_HOWDAH_HARD_BLOCK_V1
    private static final int SADDLE_SLOT = 0;
    private static final int HOWDAH_SLOT = 1;

    private final IInventory mumakilInventory;
    private final LOTREntityMumakil mumakil;
    private final EntityPlayer interactingPlayer;

    public ContainerMumakilInventory(InventoryPlayer playerInventory, IInventory mumakilInventory, LOTREntityMumakil mumakil) {
        super(playerInventory, mumakilInventory, mumakil);

        this.mumakilInventory = mumakilInventory;
        this.mumakil = mumakil;
        this.interactingPlayer = playerInventory.player;

        this.replaceHowdahSlot();
    }

    @Override
    public ItemStack slotClick(
            int slotIndex,
            int mouseButton,
            int clickMode,
            EntityPlayer player
    ) {
        ItemStack result =
                super.slotClick(slotIndex, mouseButton, clickMode, player);
        if (this.enforceHowdahDependency(player)) {
            /*
             * Container#slotClick has already completed its normal sync by
             * this point. Send the cleared howdah slot and its exact returned
             * player-inventory slot immediately in the same interaction.
             */
            super.detectAndSendChanges();
        }
        return result;
    }

    @Override
    public void detectAndSendChanges() {
        this.enforceHowdahDependency(this.interactingPlayer);
        super.detectAndSendChanges();
    }

    @Override
    public void onContainerClosed(EntityPlayer player) {
        if (this.enforceHowdahDependency(player)) {
            super.detectAndSendChanges();
        }
        super.onContainerClosed(player);
    }

    private boolean enforceHowdahDependency(EntityPlayer player) {
        return this.mumakil != null
                && this.mumakil
                .enforceTamedAdultHowdahRequiresSaddle(player);
    }

    private void replaceHowdahSlot() {
        if (this.mumakilInventory == null || this.mumakilInventory.getSizeInventory() <= HOWDAH_SLOT) {
            return;
        }

        if (this.inventorySlots == null || this.inventorySlots.size() <= HOWDAH_SLOT) {
            return;
        }

        Slot howdahSlot = new Slot(this.mumakilInventory, HOWDAH_SLOT, 8, 36) {
            @Override
            public boolean isItemValid(ItemStack stack) {
                return mumakil != null
                        && !mumakil.isChild()
                        && mumakil.isTamedMumakilMode()
                        && mumakil.hasMumakilSaddleEquipped()
                        && stack != null
                        && stack.getItem() == Main.mumakilHowdah;
            }

            @Override
            public int getSlotStackLimit() {
                return 1;
            }
        };

        howdahSlot.slotNumber = HOWDAH_SLOT;
        this.inventorySlots.set(HOWDAH_SLOT, howdahSlot);
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        if (this.mumakil == null
                || !this.mumakil.isEntityAlive()
                || !this.mumakil.canPlayerUseMumakilInventory(player)) {
            return false;
        }

        /*
         * Vanilla horse inventory closes when the rider is too far from the horse's entity origin.
         * Our rider sits high on the Mumakil model, so allow the GUI while actively riding this Mumakil.
         */
        if (player.ridingEntity == this.mumakil) {
            return true;
        }

        return this.mumakilInventory.isUseableByPlayer(player)
                && this.mumakil.getDistanceToEntity(player) < 32.0F;
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int slotIndex) {
        ItemStack copiedStack = null;
        Slot slot = (Slot)this.inventorySlots.get(slotIndex);

        if (slot != null && slot.getHasStack()) {
            ItemStack stackInSlot = slot.getStack();
            copiedStack = stackInSlot.copy();

            /*
             * Explicitly reject shift-click insertion of a howdah from the
             * player's inventory while the Mumakil is physically a child.
             * This is separate from Slot#isItemValid so every transfer path
             * is server-authoritatively blocked.
             */
            if (this.mumakil != null
                    && this.mumakil.isChild()
                    && slotIndex != HOWDAH_SLOT
                    && stackInSlot.getItem() == Main.mumakilHowdah) {
                return null;
            }

            if (slotIndex == SADDLE_SLOT || slotIndex == HOWDAH_SLOT) {
                if (!this.mergeItemStack(stackInSlot, 2, this.inventorySlots.size(), true)) {
                    return null;
                }
            } else {
                if (stackInSlot.getItem() == Items.saddle && !((Slot)this.inventorySlots.get(SADDLE_SLOT)).getHasStack()) {
                    if (!this.mergeItemStack(stackInSlot, SADDLE_SLOT, SADDLE_SLOT + 1, false)) {
                        return null;
                    }
                } else if (!this.mumakil.isChild()
                        && stackInSlot.getItem() == Main.mumakilHowdah
                        && !((Slot)this.inventorySlots.get(HOWDAH_SLOT)).getHasStack()) {
                    if (!this.mergeItemStack(stackInSlot, HOWDAH_SLOT, HOWDAH_SLOT + 1, false)) {
                        return null;
                    }
                } else if (!this.mergeItemStack(stackInSlot, 2, this.inventorySlots.size(), false)) {
                    return null;
                }
            }

            if (stackInSlot.stackSize == 0) {
                slot.putStack(null);
            } else {
                slot.onSlotChanged();
            }

            if (stackInSlot.stackSize == copiedStack.stackSize) {
                return null;
            }

            slot.onPickupFromSlot(player, stackInSlot);
        }

        return copiedStack;
    }
}
