package com.fuzs.aquaacrobatics.core;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

import com.fuzs.aquaacrobatics.util.BlockPos;

import cpw.mods.fml.common.Loader;

public class UnderwaterGrassLikeHandler {

    public static boolean handleUnderwaterGrassLikeBlock(World world, int x, int y, int z, Random rand) {
        BlockPos pos = new BlockPos(x, y, z);
        if (world.isRemote || !world.doChunksNearChunkExist(pos.getX(), pos.getY(), pos.getZ(), 3)) {
            return true;
        }
        Block above = world.getBlock(
            pos.up()
                .getX(),
            pos.up()
                .getY(),
            pos.up()
                .getZ());
        if (above.getMaterial()
            .isLiquid()) {
            world.setBlock(pos.getX(), pos.getY(), pos.getZ(), Blocks.dirt);
            return true;
        }
        return false;
    }

    public static boolean handleUnderwaterGrassBlock(World world, int x, int y, int z, Random rand) {
        return !Loader.isModLoaded("hodgepodge") && handleUnderwaterGrassLikeBlock(world, x, y, z, rand);
    }

    public static boolean setBlockUnlessCoveredByLiquid(World world, int x, int y, int z, Block blockType) {
        BlockPos pos = new BlockPos(x, y, z);
        if (world.getBlock(
            pos.up()
                .getX(),
            pos.up()
                .getY(),
            pos.up()
                .getZ())
            .getMaterial()
            .isLiquid()) {
            return false;
        }
        return world.setBlock(pos.getX(), pos.getY(), pos.getZ(), blockType);
    }
}
