package com.enovak.lotrmoremobs.siege.creation;

import com.enovak.lotrmoremobs.siege.SiegeRegistry;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public final class GateSourceBlockValidator {

    private GateSourceBlockValidator() {
    }

    public static boolean isValid(World world, int x, int y, int z) {
        if (world == null || !world.blockExists(x, y, z)) {
            return false;
        }

        Block block = world.getBlock(x, y, z);
        int metadata = world.getBlockMetadata(x, y, z);

        return isValidDefinition(world, x, y, z, block, metadata)
                && hasCapturableSourceTileEntity(
                world,
                x,
                y,
                z,
                block,
                metadata
        );
    }

    static boolean matchesSelection(
            World world,
            GateSelectionData selection
    ) {
        if (world == null || selection == null) {
            return false;
        }
        GateBlockPosition position = selection.getPosition();
        if (!isValid(
                world,
                position.getX(),
                position.getY(),
                position.getZ()
        )) {
            return false;
        }
        return world.getBlock(
                position.getX(),
                position.getY(),
                position.getZ()
        ) == selection.getSourceBlock()
                && world.getBlockMetadata(
                        position.getX(),
                        position.getY(),
                        position.getZ()
                ) == selection.getSourceMetadata();
    }

    public static boolean isValidDefinition(
            World world,
            int x,
            int y,
            int z,
            Block block,
            int metadata
    ) {
        if (world == null
                || block == null
                || block == Blocks.air
                || block == SiegeRegistry.gateController
                || block == SiegeRegistry.gatePart) {
            return false;
        }

        try {
            /*
             * TileEntity admission is deliberately NOT decided here. This
             * definition check is also used by durable restoration/recovery
             * for saved gates, so it only validates the stable block identity.
             * New source selection separately requires any live TileEntity to
             * be present and capturable before conversion.
             */

            /*
             * Gate source blocks become inert appearance data after finalization.
             *
             * Their original movement, redstone, activation, ticking, collision,
             * and other gameplay behavior will not survive conversion to GateParts,
             * so those properties are not reasons to reject them here.
             */
            /*
             * Deliberately do NOT require:
             *
             *   renderAsNormalBlock()
             *   render type 0
             *   a full/non-null collision box
             *
             * Those old restrictions excluded native metadata-driven geometry
             * such as fences, fence gates, stairs, slabs, panes, walls, doors,
             * trapdoors, and compatible modded equivalents.
             *
             * Once accepted into a finalized siege gate, this source block is
             * appearance data only. The physical world contains a GatePart, so
             * the source block itself cannot tick, activate, provide collision,
             * run redstone logic, or otherwise retain normal functionality.
             */
            return getRegisteredName(block) != null;

        } catch (RuntimeException ignored) {
            return false;
        }
    }

    /**
     * TileEntity-backed source blocks are allowed when their live TileEntity
     * exists at selection/finalization time. Gate finalization/edit commit
     * snapshots inert TE NBT before replacing the source with an inert
     * GatePart. Inventory-backed TileEntities have their item contents removed
     * from the stored snapshot so replacement drops cannot be restored a second
     * time later. The existing restoration path reapplies the remaining state
     * when the source block is restored.
     *
     * We intentionally do not instantiate arbitrary captured TileEntities
     * while the block belongs to a moving Siege Gate. Their NBT remains inert
     * stored data until restoration.
     */
    private static boolean hasCapturableSourceTileEntity(
            World world,
            int x,
            int y,
            int z,
            Block block,
            int metadata
    ) {
        try {
            boolean declaresTileEntity = block.hasTileEntity(metadata);
            TileEntity tileEntity = world.getTileEntity(x, y, z);

            if (!declaresTileEntity && tileEntity == null) {
                return true;
            }

            /*
             * If either the block definition or the live world says this
             * source carries TileEntity state, require the real TE to be
             * present so finalization/edit capture cannot silently lose it.
             */
            return tileEntity != null;

        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static String getRegisteredName(Block block) {
        if (block == null || block == Blocks.air) {
            return null;
        }
        String registeredName =
                Block.blockRegistry.getNameForObject(block);
        return registeredName == null || registeredName.isEmpty()
                ? null
                : registeredName;
    }
}
