package com.enovak.lotrmoremobs.siege.gate;

import com.enovak.lotrmoremobs.siege.SiegeRegistry;
import com.enovak.lotrmoremobs.siege.tile.TileEntitySiegeGate;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * Common-side virtual block view used only for Siege Gate lighting queries.
 *
 * While a gate is CLOSED, positions occupied by GateParts are exposed as their
 * captured source blocks and metadata. Everything outside the gate delegates
 * to the real world.
 *
 * This lets Forge's coordinate-aware light-value and light-opacity hooks ask
 * the original source block how it should participate in world lighting
 * without restoring that block physically.
 *
 * This class deliberately does not recreate or tick captured TileEntities.
 */
public final class GateLightingBlockAccess
        implements IBlockAccess {

    private final TileEntitySiegeGate controller;
    private final World world;

    public GateLightingBlockAccess(
            TileEntitySiegeGate controller
    ) {
        if (controller == null
                || controller.getWorldObj() == null) {

            throw new IllegalArgumentException(
                    "Siege gate controller/world cannot be null"
            );
        }

        this.controller =
                controller;

        this.world =
                controller.getWorldObj();
    }

    @Override
    public Block getBlock(
            int x,
            int y,
            int z
    ) {
        GatePartData part =
                getClosedGatePart(
                        x,
                        y,
                        z
                );

        if (part == null) {
            return world.getBlock(
                    x,
                    y,
                    z
            );
        }

        Block sourceBlock =
                part.getSourceBlock();

        /*
         * Never allow the placeholder/controller to recurse back into the
         * virtual gate-lighting lookup.
         */
        if (sourceBlock == null
                || sourceBlock == Blocks.air
                || sourceBlock == SiegeRegistry.gatePart
                || sourceBlock == SiegeRegistry.gateController) {

            return Blocks.iron_block;
        }

        return sourceBlock;
    }

    @Override
    public TileEntity getTileEntity(
            int x,
            int y,
            int z
    ) {
        /*
         * Captured source TileEntities remain inert. Lighting emulation must
         * never recreate gameplay TileEntities in the real world.
         */
        if (getClosedGatePart(
                x,
                y,
                z
        ) != null) {

            return null;
        }

        return world.getTileEntity(
                x,
                y,
                z
        );
    }

    @Override
    public int getLightBrightnessForSkyBlocks(
            int x,
            int y,
            int z,
            int minimumBlockLight
    ) {
        return world.getLightBrightnessForSkyBlocks(
                x,
                y,
                z,
                minimumBlockLight
        );
    }

    @Override
    public int getBlockMetadata(
            int x,
            int y,
            int z
    ) {
        GatePartData part =
                getClosedGatePart(
                        x,
                        y,
                        z
                );

        return part == null
                ? world.getBlockMetadata(
                x,
                y,
                z
        )
                : part.getSourceMetadata();
    }

    @Override
    public boolean isAirBlock(
            int x,
            int y,
            int z
    ) {
        return getBlock(
                x,
                y,
                z
        ) == Blocks.air;
    }

    @Override
    public BiomeGenBase getBiomeGenForCoords(
            int x,
            int z
    ) {
        return world.getBiomeGenForCoords(
                x,
                z
        );
    }

    @Override
    public int getHeight() {
        return world.getHeight();
    }

    @Override
    public boolean extendedLevelsInChunkCache() {
        return world.extendedLevelsInChunkCache();
    }

    @Override
    public int isBlockProvidingPowerTo(
            int x,
            int y,
            int z,
            int side
    ) {
        /*
         * This is a lighting-only view. Captured source redstone behavior is
         * intentionally not reactivated.
         */
        return 0;
    }

    @Override
    public boolean isSideSolid(
            int x,
            int y,
            int z,
            ForgeDirection side,
            boolean defaultValue
    ) {
        Block block =
                getBlock(
                        x,
                        y,
                        z
                );

        if (block == null
                || block == Blocks.air) {

            return false;
        }

        try {
            return block.isSideSolid(
                    this,
                    x,
                    y,
                    z,
                    side
            );

        } catch (RuntimeException ignored) {
            return defaultValue;
        }
    }

    private GatePartData getClosedGatePart(
            int x,
            int y,
            int z
    ) {
        if (controller.getGateState()
                != GateState.CLOSED) {

            return null;
        }

        GatePartData part =
                controller.getGatePartData(
                        x - controller.xCoord,
                        y - controller.yCoord,
                        z - controller.zCoord
                );

        if (part == null
                || !part.hasValidAbsolutePosition(
                controller.xCoord,
                controller.yCoord,
                controller.zCoord
        )
                || !world.blockExists(
                x,
                y,
                z
        )
                || world.getBlock(
                x,
                y,
                z
        ) != SiegeRegistry.gatePart) {

            return null;
        }

        return part;
    }
}