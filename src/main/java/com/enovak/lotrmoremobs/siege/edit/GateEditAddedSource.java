package com.enovak.lotrmoremobs.siege.edit;

import net.minecraft.nbt.NBTTagCompound;

/**
 * Immutable server-only source evidence captured when a real block
 * is drafted into an existing Siege Gate.
 */
final class GateEditAddedSource {

    private final String registryName;
    private final int metadata;
    private final NBTTagCompound sourceTileEntityNbt;
    private final boolean restorable;

    GateEditAddedSource(
            String registryName,
            int metadata,
            NBTTagCompound sourceTileEntityNbt,
            boolean restorable
    ) {
        if (registryName == null
                || registryName.isEmpty()) {
            throw new IllegalArgumentException(
                    "Added draft source is required."
            );
        }

        this.registryName =
                registryName;

        this.metadata =
                metadata;

        this.sourceTileEntityNbt =
                copyTag(
                        sourceTileEntityNbt
                );

        this.restorable =
                restorable;
    }

    String getRegistryName() {
        return registryName;
    }

    int getMetadata() {
        return metadata;
    }

    boolean hasSourceTileEntityNbt() {
        return sourceTileEntityNbt != null;
    }

    NBTTagCompound getSourceTileEntityNbt() {
        return copyTag(
                sourceTileEntityNbt
        );
    }

    boolean isRestorable() {
        return restorable;
    }

    private static NBTTagCompound copyTag(
            NBTTagCompound tag
    ) {
        return tag == null
                ? null
                : (NBTTagCompound)tag.copy();
    }
}