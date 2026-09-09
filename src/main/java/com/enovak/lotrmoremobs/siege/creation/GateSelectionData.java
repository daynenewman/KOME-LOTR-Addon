package com.enovak.lotrmoremobs.siege.creation;

import com.enovak.lotrmoremobs.siege.gate.GateLeaf;
import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;

final class GateSelectionData {

    private final GateBlockPosition position;
    private final GateLeaf leaf;
    private final Block sourceBlock;
    private final String sourceBlockName;
    private final int sourceMetadata;

    /*
     * Optional inert snapshot of the original source TileEntity.
     * Inventory item contents may be intentionally omitted so replacement
     * drops cannot also be restored later.
     *
     * This is DATA ONLY. It is never ticked or inserted into the world while
     * the block belongs to a finalized Siege Gate.
     */
    private final NBTTagCompound sourceTileEntityNbt;

    GateSelectionData(
            GateBlockPosition position,
            GateLeaf leaf,
            Block sourceBlock,
            String sourceBlockName,
            int sourceMetadata
    ) {
        this(
                position,
                leaf,
                sourceBlock,
                sourceBlockName,
                sourceMetadata,
                null
        );
    }

    GateSelectionData(
            GateBlockPosition position,
            GateLeaf leaf,
            Block sourceBlock,
            String sourceBlockName,
            int sourceMetadata,
            NBTTagCompound sourceTileEntityNbt
    ) {
        this.position = position;
        this.leaf = leaf;
        this.sourceBlock = sourceBlock;
        this.sourceBlockName = sourceBlockName;
        this.sourceMetadata = sourceMetadata;
        this.sourceTileEntityNbt =
                copyTag(sourceTileEntityNbt);
    }

    GateSelectionData withLeaf(GateLeaf newLeaf) {
        return new GateSelectionData(
                position,
                newLeaf,
                sourceBlock,
                sourceBlockName,
                sourceMetadata,
                sourceTileEntityNbt
        );
    }

    GateSelectionData withSourceTileEntityNbt(
            NBTTagCompound newSourceTileEntityNbt
    ) {
        return new GateSelectionData(
                position,
                leaf,
                sourceBlock,
                sourceBlockName,
                sourceMetadata,
                newSourceTileEntityNbt
        );
    }

    GateBlockPosition getPosition() {
        return position;
    }

    GateLeaf getLeaf() {
        return leaf;
    }

    Block getSourceBlock() {
        return sourceBlock;
    }

    String getSourceBlockName() {
        return sourceBlockName;
    }

    int getSourceMetadata() {
        return sourceMetadata;
    }

    boolean hasSourceTileEntityNbt() {
        return sourceTileEntityNbt != null;
    }

    NBTTagCompound getSourceTileEntityNbt() {
        return copyTag(sourceTileEntityNbt);
    }

    private static NBTTagCompound copyTag(
            NBTTagCompound tag
    ) {
        return tag == null
                ? null
                : (NBTTagCompound)tag.copy();
    }
}