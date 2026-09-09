package com.enovak.lotrmoremobs.siege.gate;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;

public final class GatePartData {

    public static final String FALLBACK_SOURCE_BLOCK =
            "minecraft:iron_block";

    private static final int MIN_WORLD_XZ = -30000000;
    private static final int MAX_WORLD_XZ = 30000000;
    private static final int MIN_WORLD_Y = 0;
    private static final int MAX_WORLD_Y = 256;

    private final int relativeX;
    private final int relativeY;
    private final int relativeZ;
    private final GateLeaf leaf;

    private final String sourceBlockName;
    private final int sourceMetadata;
    private final NBTTagCompound sourceTileEntityNbt;

    private final boolean hasStoredSourceAppearance;
    private final boolean hasStoredSourceBlock;

    public GatePartData(
            int relativeX,
            int relativeY,
            int relativeZ,
            GateLeaf leaf
    ) {
        this(
                relativeX,
                relativeY,
                relativeZ,
                leaf,
                null,
                0,
                null
        );
    }

    public GatePartData(
            int relativeX,
            int relativeY,
            int relativeZ,
            GateLeaf leaf,
            String sourceBlockName,
            int sourceMetadata
    ) {
        this(
                relativeX,
                relativeY,
                relativeZ,
                leaf,
                sourceBlockName,
                sourceMetadata,
                null
        );
    }

    public GatePartData(
            int relativeX,
            int relativeY,
            int relativeZ,
            GateLeaf leaf,
            String sourceBlockName,
            int sourceMetadata,
            NBTTagCompound sourceTileEntityNbt
    ) {
        this(
                relativeX,
                relativeY,
                relativeZ,
                leaf,
                sourceBlockName,
                sourceMetadata,
                sourceTileEntityNbt,
                false,
                null
        );
    }

    /**
     * Rehydrates source state that was already accepted and persisted by a
     * compatible gate version. Durable decoding is intentionally separate
     * from live source admission: changing what NEW gates may absorb must not
     * make an existing saved gate unreadable or unrestorable.
     */
    public static GatePartData fromPersistedSourceSnapshot(
            int relativeX,
            int relativeY,
            int relativeZ,
            GateLeaf leaf,
            String sourceBlockName,
            int sourceMetadata,
            NBTTagCompound sourceTileEntityNbt,
            boolean sourceRestorable
    ) {
        return new GatePartData(
                relativeX,
                relativeY,
                relativeZ,
                leaf,
                sourceBlockName,
                sourceMetadata,
                sourceTileEntityNbt,
                true,
                Boolean.valueOf(sourceRestorable)
        );
    }

    private GatePartData(
            int relativeX,
            int relativeY,
            int relativeZ,
            GateLeaf leaf,
            String sourceBlockName,
            int sourceMetadata,
            NBTTagCompound sourceTileEntityNbt,
            boolean persistedSnapshot,
            Boolean persistedRestorable
    ) {
        if (leaf == null) {
            throw new IllegalArgumentException(
                    "Gate leaf cannot be null"
            );
        }

        this.relativeX = relativeX;
        this.relativeY = relativeY;
        this.relativeZ = relativeZ;
        this.leaf = leaf;

        Block sourceBlock =
                sourceBlockName == null
                        ? null
                        : Block.getBlockFromName(
                        sourceBlockName
                );

        int sanitizedMetadata =
                sanitizeMetadata(
                        sourceMetadata
                );

        /*
         * Appearance storage is intentionally broader than ordinary
         * block restoration. Unusual blocks and TE-backed blocks may still
         * preserve their source definition for rendering.
         */
        this.hasStoredSourceAppearance =
                persistedSnapshot
                        ? isSafePersistedSourceAppearance(
                        sourceBlock
                )
                        : isSafeStoredSourceAppearance(
                        sourceBlock
                );

        this.sourceBlockName =
                hasStoredSourceAppearance
                        ? Block.blockRegistry.getNameForObject(
                        sourceBlock
                )
                        : FALLBACK_SOURCE_BLOCK;

        this.sourceMetadata =
                hasStoredSourceAppearance
                        ? sanitizedMetadata
                        : 0;

        /*
         * TileEntity NBT is inert stored source state.
         *
         * Keeping this snapshot does not cause the original TileEntity
         * to exist, tick, activate, provide redstone, or otherwise function
         * while the block is part of a Siege Gate.
         */
        this.sourceTileEntityNbt =
                hasStoredSourceAppearance
                        ? copyTag(
                        sourceTileEntityNbt
                )
                        : null;

        /*
         * A source is physically restorable when:
         *
         * - it is a valid stored source appearance, and
         * - either it does not require a TileEntity,
         *   or its TileEntity NBT was captured.
         */
        boolean restorableByDefinition =
                hasStoredSourceAppearance
                        && (persistedSnapshot
                        ? isSafePersistedRestorableSource(
                        sourceBlock,
                        sanitizedMetadata,
                        this.sourceTileEntityNbt
                )
                        : isSafeRestorableSource(
                        sourceBlock,
                        sanitizedMetadata,
                        this.sourceTileEntityNbt
                ));

        /*
         * SourceRestorable is part of the durable record. A persisted false
         * remains false even if today's live rules would allow restoration;
         * a persisted true is honored only when the saved definition is still
         * structurally reconstructible (registered block, and TE NBT when the
         * block requires a TileEntity).
         */
        this.hasStoredSourceBlock =
                persistedRestorable == null
                        ? restorableByDefinition
                        : persistedRestorable.booleanValue()
                        && restorableByDefinition;
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

    public boolean hasStoredSourceAppearance() {
        return hasStoredSourceAppearance;
    }

    public boolean hasStoredSourceBlock() {
        return hasStoredSourceBlock;
    }

    public Block getSourceBlock() {
        Block sourceBlock =
                Block.getBlockFromName(
                        sourceBlockName
                );

        return sourceBlock == null
                || sourceBlock == Blocks.air
                ? Blocks.iron_block
                : sourceBlock;
    }

    public Block getSourceBlockForRestoration() {
        if (!hasStoredSourceBlock) {
            return null;
        }

        Block sourceBlock =
                Block.getBlockFromName(
                        sourceBlockName
                );

        return sourceBlock == null
                || sourceBlock == Blocks.air
                ? null
                : sourceBlock;
    }

    public boolean hasSameRelativePosition(
            int relativeX,
            int relativeY,
            int relativeZ
    ) {
        return this.relativeX == relativeX
                && this.relativeY == relativeY
                && this.relativeZ == relativeZ;
    }

    public boolean hasValidAbsolutePosition(
            int controllerX,
            int controllerY,
            int controllerZ
    ) {
        long absoluteX =
                (long)controllerX
                        + relativeX;

        long absoluteY =
                (long)controllerY
                        + relativeY;

        long absoluteZ =
                (long)controllerZ
                        + relativeZ;

        return absoluteX >= MIN_WORLD_XZ
                && absoluteX < MAX_WORLD_XZ
                && absoluteY >= MIN_WORLD_Y
                && absoluteY < MAX_WORLD_Y
                && absoluteZ >= MIN_WORLD_XZ
                && absoluteZ < MAX_WORLD_XZ;
    }

    public int getAbsoluteX(
            int controllerX
    ) {
        return controllerX
                + relativeX;
    }

    public int getAbsoluteY(
            int controllerY
    ) {
        return controllerY
                + relativeY;
    }

    public int getAbsoluteZ(
            int controllerZ
    ) {
        return controllerZ
                + relativeZ;
    }

    @Override
    public boolean equals(
            Object other
    ) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof GatePartData)) {
            return false;
        }

        GatePartData part =
                (GatePartData)other;

        return relativeX
                == part.relativeX
                && relativeY
                == part.relativeY
                && relativeZ
                == part.relativeZ
                && leaf
                == part.leaf
                && sourceMetadata
                == part.sourceMetadata
                && hasStoredSourceBlock
                == part.hasStoredSourceBlock
                && sourceBlockName.equals(
                part.sourceBlockName
        )
                && tagsEqual(
                sourceTileEntityNbt,
                part.sourceTileEntityNbt
        );
    }

    @Override
    public int hashCode() {
        int result =
                relativeX;

        result =
                31 * result
                        + relativeY;

        result =
                31 * result
                        + relativeZ;

        result =
                31 * result
                        + leaf.hashCode();

        result =
                31 * result
                        + sourceBlockName.hashCode();

        result =
                31 * result
                        + sourceMetadata;

        result =
                31 * result
                        + (hasStoredSourceBlock
                        ? 1
                        : 0);

        result =
                31 * result
                        + (sourceTileEntityNbt == null
                        ? 0
                        : sourceTileEntityNbt.hashCode());

        return result;
    }

    private static NBTTagCompound copyTag(
            NBTTagCompound tag
    ) {
        return tag == null
                ? null
                : (NBTTagCompound)tag.copy();
    }

    private static boolean tagsEqual(
            NBTTagCompound first,
            NBTTagCompound second
    ) {
        if (first == second) {
            return true;
        }

        if (first == null
                || second == null) {
            return false;
        }

        return first.equals(
                second
        );
    }

    private static int sanitizeMetadata(
            int metadata
    ) {
        return Math.max(
                0,
                Math.min(
                        metadata,
                        15
                )
        );
    }

    private static boolean isSafeStoredSourceAppearance(
            Block sourceBlock
    ) {
        try {
            if (sourceBlock == null
                    || sourceBlock == Blocks.air) {
                return false;
            }

            String registeredName =
                    Block.blockRegistry.getNameForObject(
                            sourceBlock
                    );

            return registeredName != null
                    && !"lotrmoremobs:siege_gate_controller"
                    .equals(
                            registeredName
                    )
                    && !"lotrmoremobs:siege_gate_part"
                    .equals(
                            registeredName
                    );

        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean isSafePersistedSourceAppearance(
            Block sourceBlock
    ) {
        try {
            if (sourceBlock == null
                    || sourceBlock == Blocks.air) {
                return false;
            }

            String registeredName =
                    Block.blockRegistry.getNameForObject(
                            sourceBlock
                    );

            return registeredName != null
                    && !"lotrmoremobs:siege_gate_controller"
                    .equals(registeredName)
                    && !"lotrmoremobs:siege_gate_part"
                    .equals(registeredName);

        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean isSafePersistedRestorableSource(
            Block sourceBlock,
            int metadata,
            NBTTagCompound sourceTileEntityNbt
    ) {
        try {
            if (!isSafePersistedSourceAppearance(
                    sourceBlock
            )) {
                return false;
            }

            if (!sourceBlock.hasTileEntity(
                    sanitizeMetadata(metadata)
            )) {
                return true;
            }

            return sourceTileEntityNbt != null;

        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean isSafeRestorableSource(
            Block sourceBlock,
            int metadata,
            NBTTagCompound sourceTileEntityNbt
    ) {
        try {
            if (!isSafeStoredSourceAppearance(
                    sourceBlock
            )) {
                return false;
            }

            /*
             * A non-TE source can be reconstructed completely using
             * registry ID + metadata.
             */
            if (!sourceBlock.hasTileEntity(
                    sanitizeMetadata(
                            metadata
                    )
            )) {
                return true;
            }

            /*
             * A TE-backed source additionally requires its captured
             * TileEntity NBT snapshot.
             */
            return sourceTileEntityNbt
                    != null;

        } catch (RuntimeException ignored) {
            return false;
        }
    }
}