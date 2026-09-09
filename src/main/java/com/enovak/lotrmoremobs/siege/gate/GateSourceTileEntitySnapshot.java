package com.enovak.lotrmoremobs.siege.gate;

import net.minecraft.block.BlockJukebox;
import net.minecraft.inventory.IInventory;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

/**
 * Captures inert source TileEntity state for Siege Gate restoration.
 *
 * Inventory-backed TileEntities require one extra safety rule. Replacing a
 * chest/furnace/hopper/etc. with a GatePart may cause the source block to drop
 * its live inventory. Persisting those same stacks in the restoration NBT
 * would duplicate them when the source block is later restored.
 *
 * To avoid mutating the live source TileEntity before the existing gate
 * transaction replaces it, inventory contents are removed from a detached
 * copy of the captured TileEntity. All other TileEntity state remains in the
 * stored snapshot.
 *
 * Vanilla jukeboxes are the important non-IInventory exception: their
 * breakBlock path ejects the stored record. Until gate source metadata can
 * be transactionally rewritten alongside TE state, a non-empty jukebox is
 * rejected here rather than allowing a record duplication path.
 */
public final class GateSourceTileEntitySnapshot {

    private GateSourceTileEntitySnapshot() {
    }

    public static NBTTagCompound captureForGateStorage(
            TileEntity sourceTileEntity
    ) {
        if (sourceTileEntity == null) {
            return null;
        }

        NBTTagCompound snapshot =
                new NBTTagCompound();

        sourceTileEntity.writeToNBT(
                snapshot
        );

        if (sourceTileEntity
                instanceof BlockJukebox.TileEntityJukebox
                && (snapshot.hasKey("RecordItem", 10)
                || snapshot.getInteger("Record") > 0)) {
            throw new IllegalStateException(
                    "A non-empty jukebox cannot be captured safely"
            );
        }

        if (!(sourceTileEntity instanceof IInventory)) {
            return snapshot;
        }

        return withoutInventoryContents(
                snapshot
        );
    }

    private static NBTTagCompound withoutInventoryContents(
            NBTTagCompound snapshot
    ) {
        TileEntity detached =
                TileEntity.createAndLoadEntity(
                        (NBTTagCompound)snapshot.copy()
                );

        if (!(detached instanceof IInventory)) {
            throw new IllegalStateException(
                    "Inventory TileEntity could not be reconstructed safely"
            );
        }

        clearInventoryContents(
                detached
        );

        NBTTagCompound sanitized =
                new NBTTagCompound();

        detached.writeToNBT(
                sanitized
        );

        /*
         * Vanilla inventories serialize their stacks under Items. Clearing the
         * detached IInventory above is authoritative; removing the conventional
         * tag as well makes the persisted invariant explicit and protects
         * against stale list data surviving a custom writeToNBT implementation.
         */
        sanitized.removeTag(
                "Items"
        );

        return sanitized;
    }

    /**
     * Applies a persisted source TileEntity snapshot without ever restoring
     * inventory contents that may already have dropped when the source block
     * was converted into a GatePart.
     *
     * Capture-time sanitization remains the first line of defense. This method
     * is the restoration-boundary invariant: even an older or malformed saved
     * snapshot cannot repopulate an IInventory during gate dismantle, edit
     * rollback, or finalization rollback.
     */
    public static void applyForRestoration(
            TileEntity restored,
            NBTTagCompound snapshot
    ) {
        if (restored == null
                || snapshot == null) {
            throw new IllegalArgumentException(
                    "Restored TileEntity and snapshot are required"
            );
        }

        restored.readFromNBT(
                (NBTTagCompound)snapshot.copy()
        );

        clearInventoryContents(
                restored
        );
    }

    /**
     * Reconstructs a source TileEntity from persisted NBT and immediately
     * enforces the same no-restored-inventory invariant.
     */
    public static TileEntity createForRestoration(
            NBTTagCompound snapshot
    ) {
        if (snapshot == null) {
            return null;
        }

        TileEntity restored =
                TileEntity.createAndLoadEntity(
                        (NBTTagCompound)snapshot.copy()
                );

        if (restored != null) {
            clearInventoryContents(
                    restored
            );
        }

        return restored;
    }

    private static void clearInventoryContents(
            TileEntity tileEntity
    ) {
        if (!(tileEntity instanceof IInventory)) {
            return;
        }

        IInventory inventory =
                (IInventory)tileEntity;

        for (int slot = 0;
                slot < inventory.getSizeInventory();
                ++slot) {
            inventory.setInventorySlotContents(
                    slot,
                    null
            );
        }
    }
}
