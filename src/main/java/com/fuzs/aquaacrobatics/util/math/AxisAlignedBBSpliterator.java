package com.fuzs.aquaacrobatics.util.math;

import java.util.List;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import com.fuzs.aquaacrobatics.util.BlockPos;
import com.google.common.collect.Lists;

public class AxisAlignedBBSpliterator extends Spliterators.AbstractSpliterator<AxisAlignedBB> {

    @Nullable
    private final Entity entity;
    private final AxisAlignedBB aabb;
    private final CubeCoordinateIterator cubeCoordinateIterator;
    private final World reader;
    private boolean isEntityPresent;
    private final BiPredicate<Block, BlockPos> statePositionPredicate;
    public static final AxisAlignedBB FULL_BLOCK_AABB = AxisAlignedBB
        .getBoundingBox(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);

    public AxisAlignedBBSpliterator(World reader, @Nullable Entity entity, AxisAlignedBB aabb) {

        this(reader, entity, aabb, (state, pos) -> true);
    }

    public AxisAlignedBBSpliterator(World reader, @Nullable Entity entity, AxisAlignedBB aabb,
        BiPredicate<Block, BlockPos> statePositionPredicate) {

        super(Long.MAX_VALUE, Spliterator.NONNULL | Spliterator.IMMUTABLE);
        this.reader = reader;
        this.isEntityPresent = entity != null;
        this.entity = entity;
        this.aabb = aabb;
        this.statePositionPredicate = statePositionPredicate;
        int startX = MathHelper.floor_double(aabb.minX - 1.0E-7D) - 1;
        int endX = MathHelper.floor_double(aabb.maxX + 1.0E-7D) + 1;
        int startY = MathHelper.floor_double(aabb.minY - 1.0E-7D) - 1;
        int heightY = MathHelper.floor_double(aabb.maxY + 1.0E-7D) + 1;
        int startZ = MathHelper.floor_double(aabb.minZ - 1.0E-7D) - 1;
        int endZ = MathHelper.floor_double(aabb.maxZ + 1.0E-7D) + 1;
        this.cubeCoordinateIterator = new CubeCoordinateIterator(startX, startY, startZ, endX, heightY, endZ);
    }

    public boolean tryAdvance(Consumer<? super AxisAlignedBB> consumer) {

        return this.isEntityPresent && this.isEntityOutsideOfBorder(consumer) || this.isAABBColliding(consumer);
    }

    private boolean isAABBColliding(Consumer<? super AxisAlignedBB> consumer) {

        BlockPos.PooledMutableBlockPos mutablePos = BlockPos.PooledMutableBlockPos.retain();
        while (this.cubeCoordinateIterator.hasNext()) {

            int x = this.cubeCoordinateIterator.getX();
            int y = this.cubeCoordinateIterator.getY();
            int z = this.cubeCoordinateIterator.getZ();

            int boundariesTouched = this.cubeCoordinateIterator.numBoundariesTouched();
            if (boundariesTouched == 3) {

                continue;
            }

            mutablePos.setPos(x, y, z);
            if (!this.reader.blockExists(mutablePos.getX(), mutablePos.getY(), mutablePos.getZ())) {

                continue;
            }

            // piston check is new, not sure if it really helps in this version
            Block blockstate = this.reader.getBlock(mutablePos.getX(), mutablePos.getY(), mutablePos.getZ());
            if (!this.statePositionPredicate.test(blockstate, mutablePos)
                || boundariesTouched == 2 && blockstate != Blocks.piston_extension) {

                continue;
            }

            // check full blocks first as they're easier to handle
            AxisAlignedBB collisionBoundingBox = blockstate
                .getCollisionBoundingBoxFromPool(this.reader, mutablePos.getX(), mutablePos.getY(), mutablePos.getZ());
            if (collisionBoundingBox == FULL_BLOCK_AABB && blockstate.renderAsNormalBlock()) {

                // second check probably not necessary
                AxisAlignedBB aabbOffset = collisionBoundingBox.offset(x, y, z);
                if (!this.aabb.intersectsWith(aabbOffset)
                    || this.entity != null && !this.entity.boundingBox.intersectsWith(aabbOffset)) {

                    continue;
                }

                consumer.accept(collisionBoundingBox);
                mutablePos.release();
                return true;
            }

            List<AxisAlignedBB> collidingBoxes = Lists.newArrayList();
            this.getCollisionBoxList(collidingBoxes, blockstate, mutablePos);
            if (collidingBoxes.isEmpty()) {

                continue;
            }

            consumer.accept(collisionBoundingBox);
            mutablePos.release();
            return true;
        }

        mutablePos.release();
        return false;
    }

    private void getCollisionBoxList(List<AxisAlignedBB> collidingBoxes, Block blockstate,
        BlockPos.PooledMutableBlockPos mutablePos) {

        // only retain boxes colliding with both the area of interest and the current entity if present
        blockstate.addCollisionBoxesToList(
            this.reader,
            mutablePos.getX(),
            mutablePos.getY(),
            mutablePos.getZ(),
            this.aabb,
            collidingBoxes,
            this.entity);
        if (this.entity != null) {

            List<AxisAlignedBB> entityCollidingBoxes = Lists.newArrayList();
            blockstate.addCollisionBoxesToList(
                this.reader,
                mutablePos.getX(),
                mutablePos.getY(),
                mutablePos.getZ(),
                this.entity.boundingBox,
                entityCollidingBoxes,
                this.entity);
            collidingBoxes.retainAll(entityCollidingBoxes);
        }
    }

    private boolean isEntityOutsideOfBorder(Consumer<? super AxisAlignedBB> consumer) {

        Objects.requireNonNull(this.entity);
        this.isEntityPresent = false;
        // WorldBorder worldborder = this.reader.getWorldBorder();
        // AxisAlignedBB axisalignedbb = this.entity.getEntityBoundingBox();
        // if (!isBoundingBoxWithinBorder(worldborder, axisalignedbb)) {
        //
        // AxisAlignedBB borderShape = new AxisAlignedBB(worldborder.minX(), Double.NEGATIVE_INFINITY,
        // worldborder.minZ(), worldborder.maxX(), Double.POSITIVE_INFINITY, worldborder.maxZ());
        // consumer.accept(borderShape);
        // return true;
        // }

        return false;
    }

    // public static boolean isBoundingBoxWithinBorder(WorldBorder worldBorder, AxisAlignedBB entityBoundingBox) {
    //
    // double minX = MathHelper.floor(worldBorder.minX());
    // double minZ = MathHelper.floor(worldBorder.minZ());
    // double maxX = MathHelper.ceil(worldBorder.maxX());
    // double maxZ = MathHelper.ceil(worldBorder.maxZ());
    // return entityBoundingBox.minX > minX && entityBoundingBox.minX < maxX && entityBoundingBox.minZ > minZ &&
    // entityBoundingBox.minZ < maxZ && entityBoundingBox.maxX > minX && entityBoundingBox.maxX < maxX &&
    // entityBoundingBox.maxZ > minZ && entityBoundingBox.maxZ < maxZ;
    // }

}
