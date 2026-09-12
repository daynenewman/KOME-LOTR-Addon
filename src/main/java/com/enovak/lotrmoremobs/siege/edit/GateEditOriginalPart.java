package com.enovak.lotrmoremobs.siege.edit;

import com.enovak.lotrmoremobs.siege.gate.GateLeaf;
import com.enovak.lotrmoremobs.siege.gate.GatePartData;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Immutable, server-only provenance copied from one revision-N GatePartData.
 */
public final class GateEditOriginalPart {

    private final int relativeX;
    private final int relativeY;
    private final int relativeZ;

    private final GateLeaf leaf;

    private final String sourceBlockName;
    private final int sourceMetadata;
    private final NBTTagCompound sourceTileEntityNbt;
    private final boolean sourceRestorable;

    GateEditOriginalPart(
            GatePartData part
    ) {
        if (part == null) {
            throw new IllegalArgumentException(
                    "Original gate part is required."
            );
        }

        relativeX =
                part.getRelativeX();

        relativeY =
                part.getRelativeY();

        relativeZ =
                part.getRelativeZ();

        leaf =
                part.getLeaf();

        sourceBlockName =
                part.getSourceBlockName();

        sourceMetadata =
                part.getSourceMetadata();

        sourceTileEntityNbt =
                copyTag(
                        part.getSourceTileEntityNbt()
                );

        sourceRestorable =
                part.hasStoredSourceBlock()
                        && part.getSourceBlockForRestoration()
                        != null;
    }

    public int getRelativeX() {
        return relativeX;
    }

    public int getRelativeY() {
        return relativeY;
    }

    public int getRelativeZ() {
        return relativeZ;
    }

    public GateLeaf getLeaf() {
        return leaf;
    }

    public String getSourceBlockName() {
        return sourceBlockName;
    }

    public int getSourceMetadata() {
        return sourceMetadata;
    }

    public boolean hasSourceTileEntityNbt() {
        return sourceTileEntityNbt != null;
    }

    public NBTTagCompound getSourceTileEntityNbt() {
        return copyTag(
                sourceTileEntityNbt
        );
    }

    public boolean isSourceRestorable() {
        return sourceRestorable;
    }

    private static NBTTagCompound copyTag(
            NBTTagCompound tag
    ) {
        return tag == null
                ? null
                : (NBTTagCompound)tag.copy();
    }
}