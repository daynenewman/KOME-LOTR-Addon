package com.enovak.lotrmoremobs.siege.gate;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

/**
 * Small compatibility layer for verifying restored Siege Gate source blocks.
 *
 * Most blocks must round-trip with the exact stored metadata. Vanilla chests
 * are the important exception for Siege Gates: BlockChest may recalculate its
 * facing during the placement callback, so an otherwise successful setBlock()
 * can immediately differ from the persisted facing and strand the durable
 * removal/edit transaction in CONFLICT.
 *
 * This adapter is deliberately narrow. It never relaxes block identity, never
 * affects source admission, and never changes durable journal state. When the
 * expected and actual blocks are the same chest type and both metadata values
 * are valid chest facings (2..5), it reapplies the persisted facing without a
 * neighbor notification and then requires an exact match again.
 */
public final class GateSourceRestorationAdapter {

    private GateSourceRestorationAdapter() {
    }

    public static boolean matchesRestoredBlock(
            World world,
            int x,
            int y,
            int z,
            String expectedBlockName,
            int expectedMetadata
    ) {
        if (world == null
                || expectedBlockName == null
                || !world.blockExists(x, y, z)) {
            return false;
        }

        Block actualBlock = world.getBlock(x, y, z);
        String actualBlockName =
                Block.blockRegistry.getNameForObject(actualBlock);

        if (!expectedBlockName.equals(actualBlockName)) {
            return false;
        }

        int actualMetadata = world.getBlockMetadata(x, y, z);

        if (actualMetadata == expectedMetadata) {
            return true;
        }

        Block expectedBlock =
                Block.getBlockFromName(expectedBlockName);

        if (!isChest(expectedBlock)
                || !isValidChestFacing(expectedMetadata)
                || !isValidChestFacing(actualMetadata)) {
            return false;
        }

        try {
            world.setBlockMetadataWithNotify(
                    x,
                    y,
                    z,
                    expectedMetadata,
                    2
            );
        } catch (RuntimeException ignored) {
            return false;
        }

        Block reconciledBlock = world.getBlock(x, y, z);
        String reconciledName =
                Block.blockRegistry.getNameForObject(reconciledBlock);

        return expectedBlockName.equals(reconciledName)
                && world.getBlockMetadata(x, y, z)
                == expectedMetadata;
    }

    private static boolean isChest(Block block) {
        return block == Blocks.chest
                || block == Blocks.trapped_chest;
    }

    private static boolean isValidChestFacing(int metadata) {
        return metadata >= 2
                && metadata <= 5;
    }
}
