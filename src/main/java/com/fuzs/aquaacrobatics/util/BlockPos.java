package com.fuzs.aquaacrobatics.util;

import java.util.Iterator;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Lists;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * @author Ordinastie
 *
 */
public class BlockPos {

    private static final Logger LOGGER = LogManager.getLogger();
    /** An immutable block pos with zero as all coordinates. */
    public static final BlockPos ORIGIN = new BlockPos(0, 0, 0);
    // 1.8 BlockPos constants
    private static final int NUM_X_BITS = 26;
    private static final int NUM_Z_BITS = NUM_X_BITS;
    private static final int NUM_Y_BITS = 64 - NUM_X_BITS - NUM_Z_BITS;
    private static final int Y_SHIFT = 0 + NUM_Z_BITS;
    private static final int X_SHIFT = Y_SHIFT + NUM_Y_BITS;
    private static final long X_MASK = (1L << NUM_X_BITS) - 1L;
    private static final long Y_MASK = (1L << NUM_Y_BITS) - 1L;
    private static final long Z_MASK = (1L << NUM_Z_BITS) - 1L;

    protected int x;
    protected int y;
    protected int z;

    public BlockPos(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public BlockPos(BlockPos pos) {
        this.x = pos.x;
        this.y = pos.y;
        this.z = pos.z;
    }

    public BlockPos(double x, double y, double z) {
        this(MathHelper.floor_double(x), MathHelper.floor_double(y), MathHelper.floor_double(z));
    }

    public BlockPos(Vec3 vec) {
        this(
            MathHelper.floor_double(vec.xCoord),
            MathHelper.floor_double(vec.yCoord),
            MathHelper.floor_double(vec.zCoord));
    }

    public BlockPos(Entity entity) {
        this(entity.posX, entity.posY, entity.posZ);
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public int chunkX() {
        return x >> 4;
    }

    public int chunkZ() {
        return z >> 4;
    }

    public Block getBlock(World world) {
        return world.getBlock(getX(), getY(), getZ());
    }

    public int getMetadata(World world) {
        return world.getBlockMetadata(getX(), getY(), getZ());
    }

    /**
     * Add the given coordinates to the coordinates of this BlockPos
     *
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     */
    public BlockPos add(int x, int y, int z) {
        return new BlockPos(this.getX() + x, this.getY() + y, this.getZ() + z);
    }

    public BlockPos add(double x, double y, double z) {
        return x == 0.0D && y == 0.0D && z == 0.0D ? this
            : new BlockPos((double) this.getX() + x, (double) this.getY() + y, (double) this.getZ() + z);
    }

    public BlockPos add(BlockPos pos) {
        if (pos == null) return new BlockPos(getX(), getY(), getZ());
        return add(pos.getX(), pos.getY(), pos.getZ());
    }

    public BlockPos substract(BlockPos pos) {
        return add(-pos.getX(), -pos.getY(), -pos.getZ());
    }

    public BlockPos getPosition() {
        return new BlockPos(this.getX(), this.getY() + 0.5D, this.getZ());
    }

    public Vec3 getPositionVec() {
        return new BlockPos(this.getX(), this.getY() + 0.5D, this.getZ()).getPositionVec();
    }

    public Vec3 getPositionVector() {
        return Vec3.createVectorHelper(this.getX(), this.getY(), this.getZ());
    }

    // #region Moves

    /**
     * Offset this BlockPos 1 block up
     */
    public BlockPos up() {
        return this.up(1);
    }

    /**
     * Offset this BlockPos n blocks up
     */
    public BlockPos up(int n) {
        return this.offset(ForgeDirection.UP, n);
    }

    /**
     * Offset this BlockPos 1 block down
     */
    public BlockPos down() {
        return this.down(1);
    }

    /**
     * Offset this BlockPos n blocks down
     */
    public BlockPos down(int n) {
        return this.offset(ForgeDirection.DOWN, n);
    }

    /**
     * Offset this BlockPos 1 block in northern direction
     */
    public BlockPos north() {
        return this.north(1);
    }

    /**
     * Offset this BlockPos n blocks in northern direction
     */
    public BlockPos north(int n) {
        return this.offset(ForgeDirection.NORTH, n);
    }

    /**
     * Offset this BlockPos 1 block in southern direction
     */
    public BlockPos south() {
        return this.south(1);
    }

    /**
     * Offset this BlockPos n blocks in southern direction
     */
    public BlockPos south(int n) {
        return this.offset(ForgeDirection.SOUTH, n);
    }

    /**
     * Offset this BlockPos 1 block in western direction
     */
    public BlockPos west() {
        return this.west(1);
    }

    /**
     * Offset this BlockPos n blocks in western direction
     */
    public BlockPos west(int n) {
        return this.offset(ForgeDirection.WEST, n);
    }

    /**
     * Offset this BlockPos 1 block in eastern direction
     */
    public BlockPos east() {
        return this.east(1);
    }

    /**
     * Offset this BlockPos n blocks in eastern direction
     */
    public BlockPos east(int n) {
        return this.offset(ForgeDirection.EAST, n);
    }

    /**
     * Offset this BlockPos 1 block in the given direction
     */
    public BlockPos offset(ForgeDirection facing) {
        return this.offset(facing, 1);
    }

    /**
     * Offsets this BlockPos n blocks in the given direction
     *
     * @param facing The direction of the offset
     * @param n      The number of blocks to offset by
     */
    public BlockPos offset(ForgeDirection facing, int n) {
        return new BlockPos(
            this.getX() + facing.offsetX * n,
            this.getY() + facing.offsetY * n,
            this.getZ() + facing.offsetZ * n);
    }

    public BlockPos rotate(int rotation) {
        int[] cos = { 1, 0, -1, 0 };
        int[] sin = { 0, 1, 0, -1 };

        int a = rotation % 4;
        if (a < 0) a += 4;

        int newX = (x * cos[a]) - (z * sin[a]);
        int newZ = (x * sin[a]) + (z * cos[a]);

        return new BlockPos(newX, y, newZ);
    }

    // #end Moves

    public boolean isInRange(BlockPos pos, int range) {
        double x = pos.x - this.x;
        double y = pos.y - this.y;
        double z = pos.z - this.z;
        return (x * x + y * y + z * z) <= range * range;
    }

    public boolean isInside(AxisAlignedBB aabb) {
        return aabb.intersectsWith(AxisAlignedBB.getBoundingBox(x, y, z, x + 1, y + 1, z + 1));
    }

    public ChunkPosition toChunkPosition() {
        return new ChunkPosition(chunkX(), y, chunkZ());
    }

    public double getDistance(int xIn, int yIn, int zIn) {
        double d0 = (double) (this.getX() - xIn);
        double d1 = (double) (this.getY() - yIn);
        double d2 = (double) (this.getZ() - zIn);
        return Math.sqrt(d0 * d0 + d1 * d1 + d2 * d2);
    }

    /**
     * Calculate squared distance to the given coordinates
     */
    public double distanceSq(double toX, double toY, double toZ) {
        double d0 = (double) this.getX() - toX;
        double d1 = (double) this.getY() - toY;
        double d2 = (double) this.getZ() - toZ;
        return d0 * d0 + d1 * d1 + d2 * d2;
    }

    /**
     * Compute square of distance from point x, y, z to center of this Block
     */
    public double distanceSqToCenter(double xIn, double yIn, double zIn) {
        double d0 = (double) this.getX() + 0.5D - xIn;
        double d1 = (double) this.getY() + 0.5D - yIn;
        double d2 = (double) this.getZ() + 0.5D - zIn;
        return d0 * d0 + d1 * d1 + d2 * d2;
    }

    /**
     * Calculate squared distance to the given Vector
     */
    public double distanceSq(Vec3 to) {
        return this.distanceSq((double) to.xCoord, (double) to.yCoord, (double) to.zCoord);
    }

    public double distanceSq(BlockPos to) {
        return this.distanceSq((double) to.getX(), (double) to.getY(), (double) to.getZ());
    }

    public double squareDistanceTo(Vec3 vec) {
        double d0 = vec.xCoord - this.x;
        double d1 = vec.yCoord - this.y;
        double d2 = vec.zCoord - this.z;
        return d0 * d0 + d1 * d1 + d2 * d2;
    }

    public double squareDistanceTo(double xIn, double yIn, double zIn) {
        double d0 = xIn - this.x;
        double d1 = yIn - this.y;
        double d2 = zIn - this.z;
        return d0 * d0 + d1 * d1 + d2 * d2;
    }

    /**
     * Serialize this BlockPos into a long value
     */
    public long toLong() {
        return (this.getX() & X_MASK) << X_SHIFT | (this.getY() & Y_MASK) << Y_SHIFT | (this.getZ() & Z_MASK) << 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;

        if (!(obj instanceof BlockPos)) return false;

        BlockPos pos = (BlockPos) obj;
        return this.getX() != pos.getX() ? false : (this.getY() != pos.getY() ? false : this.getZ() == pos.getZ());
    }

    @Override
    public int hashCode() {
        return (this.getY() + this.getZ() * 31) * 31 + this.getX();
    }

    @Override
    public String toString() {
        return x + ", " + y + ", " + z;
    }

    /**
     * Create a BlockPos from a serialized long value (created by toLong)
     */
    public static BlockPos fromLong(long serialized) {
        int j = (int) (serialized << 64 - X_SHIFT - NUM_X_BITS >> 64 - NUM_X_BITS);
        int k = (int) (serialized << 64 - Y_SHIFT - NUM_Y_BITS >> 64 - NUM_Y_BITS);
        int l = (int) (serialized << 64 - NUM_Z_BITS >> 64 - NUM_Z_BITS);
        return new BlockPos(j, k, l);
    }

    public static BlockPos minOf(BlockPos p1, BlockPos p2) {
        return new BlockPos(
            Math.min(p1.getX(), p2.getX()),
            Math.min(p1.getY(), p2.getY()),
            Math.min(p1.getZ(), p2.getZ()));
    }

    public static BlockPos maxOf(BlockPos p1, BlockPos p2) {
        return new BlockPos(
            Math.max(p1.getX(), p2.getX()),
            Math.max(p1.getY(), p2.getY()),
            Math.max(p1.getZ(), p2.getZ()));
    }

    public static Iterable<BlockPos> getAllInBox(AxisAlignedBB aabb) {
        AABBFix(aabb);
        return getAllInBox(
            new BlockPos(aabb.minX, aabb.minY, aabb.minZ),
            new BlockPos(Math.ceil(aabb.maxX) - 1, Math.ceil(aabb.maxY) - 1, Math.ceil(aabb.maxZ) - 1));
    }

    /**
     * Create an {@link Iterable} that returns all positions in the box specified by the given corners.
     *
     * @param from the first corner
     * @param to   the second corner
     * @return the iterable
     */
    public static Iterable<BlockPos> getAllInBox(BlockPos from, BlockPos to) {
        return new BlockIterator(from, to).asIterable();
    }

    public static class BlockIterator implements Iterator<BlockPos> {

        private BlockPos from;
        private BlockPos to;

        private int x;
        private int y;
        private int z;

        public BlockIterator(BlockPos from, BlockPos to) {
            this.from = minOf(from, to);
            this.to = maxOf(from, to);

            x = from.getX();
            y = from.getY();
            z = from.getZ();
        }

        @Override
        public boolean hasNext() {
            return x <= to.getX() && y <= to.getY() && z <= to.getZ();
        }

        @Override
        public BlockPos next() {
            BlockPos retVal = hasNext() ? new BlockPos(x, y, z) : null;
            x++;
            if (x > to.getX()) {
                x = from.getX();
                y++;
                if (y > to.getY()) {
                    y = from.getY();
                    z++;
                }
            }
            return retVal;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        public Iterable<BlockPos> asIterable() {
            return new Iterable<BlockPos>() {

                @Override
                public Iterator<BlockPos> iterator() {
                    return BlockIterator.this;
                }
            };
        }
    }

    public static AxisAlignedBB AABBFix(AxisAlignedBB aabb) {
        double tmp;
        if (aabb.minX > aabb.maxX) {
            tmp = aabb.minX;
            aabb.minX = aabb.maxX;
            aabb.maxX = tmp;
        }

        if (aabb.minY > aabb.maxY) {
            tmp = aabb.minY;
            aabb.minY = aabb.maxY;
            aabb.maxY = tmp;
        }

        if (aabb.minZ > aabb.maxZ) {
            tmp = aabb.minZ;
            aabb.minZ = aabb.maxZ;
            aabb.maxZ = tmp;
        }

        return aabb;
    }

    /**
     * Returns a version of this BlockPos that is guaranteed to be immutable.
     *
     * <p>
     * When storing a BlockPos given to you for an extended period of time, make sure you
     * use this in case the value is changed internally.
     * </p>
     */
    public BlockPos toImmutable() {
        return this;
    }

    public static Iterable<BlockPos.MutableBlockPos> getAllInBoxMutable(BlockPos from, BlockPos to) {
        return getAllInBoxMutable(
            Math.min(from.getX(), to.getX()),
            Math.min(from.getY(), to.getY()),
            Math.min(from.getZ(), to.getZ()),
            Math.max(from.getX(), to.getX()),
            Math.max(from.getY(), to.getY()),
            Math.max(from.getZ(), to.getZ()));
    }

    public static Iterable<BlockPos.MutableBlockPos> getAllInBoxMutable(final int x1, final int y1, final int z1,
        final int x2, final int y2, final int z2) {
        return new Iterable<BlockPos.MutableBlockPos>() {

            public Iterator<BlockPos.MutableBlockPos> iterator() {
                return new AbstractIterator<MutableBlockPos>() {

                    private BlockPos.MutableBlockPos pos;

                    protected BlockPos.MutableBlockPos computeNext() {
                        if (this.pos == null) {
                            this.pos = new BlockPos.MutableBlockPos(x1, y1, z1);
                            return this.pos;
                        } else if (this.pos.x == x2 && this.pos.y == y2 && this.pos.z == z2) {
                            return (BlockPos.MutableBlockPos) this.endOfData();
                        } else {
                            if (this.pos.x < x2) {
                                ++this.pos.x;
                            } else if (this.pos.y < y2) {
                                this.pos.x = x1;
                                ++this.pos.y;
                            } else if (this.pos.z < z2) {
                                this.pos.x = x1;
                                this.pos.y = y1;
                                ++this.pos.z;
                            }

                            return this.pos;
                        }
                    }
                };
            }
        };
    }

    public static class MutableBlockPos extends BlockPos {

        /** Mutable X Coordinate */
        protected int x;
        /** Mutable Y Coordinate */
        protected int y;
        /** Mutable Z Coordinate */
        protected int z;

        public MutableBlockPos() {
            this(0, 0, 0);
        }

        public MutableBlockPos(BlockPos pos) {
            this(pos.getX(), pos.getY(), pos.getZ());
        }

        public MutableBlockPos(int x_, int y_, int z_) {
            super(0, 0, 0);
            this.x = x_;
            this.y = y_;
            this.z = z_;
        }

        /**
         * Add the given coordinates to the coordinates of this BlockPos
         */
        public BlockPos add(double x, double y, double z) {
            return super.add(x, y, z).toImmutable();
        }

        /**
         * Add the given coordinates to the coordinates of this BlockPos
         */
        public BlockPos add(int x, int y, int z) {
            return super.add(x, y, z).toImmutable();
        }

        /**
         * Offsets this BlockPos n blocks in the given direction
         */
        public BlockPos offset(ForgeDirection facing, int n) {
            return super.offset(facing, n).toImmutable();
        }

        public BlockPos rotate(int rotationIn) {
            return super.rotate(rotationIn).toImmutable();
        }

        /**
         * Gets the X coordinate.
         */
        public int getX() {
            return this.x;
        }

        /**
         * Gets the Y coordinate.
         */
        public int getY() {
            return this.y;
        }

        /**
         * Gets the Z coordinate.
         */
        public int getZ() {
            return this.z;
        }

        /**
         * None
         */
        public BlockPos.MutableBlockPos setPos(int xIn, int yIn, int zIn) {
            this.x = xIn;
            this.y = yIn;
            this.z = zIn;
            return this;
        }

        public BlockPos.MutableBlockPos setPos(double xIn, double yIn, double zIn) {
            return this
                .setPos(MathHelper.floor_double(xIn), MathHelper.floor_double(yIn), MathHelper.floor_double(zIn));
        }

        @SideOnly(Side.CLIENT)
        public BlockPos.MutableBlockPos setPos(Entity entityIn) {
            return this.setPos(entityIn.posX, entityIn.posY, entityIn.posZ);
        }

        public BlockPos.MutableBlockPos setPos(Vec3 vec) {
            return this.setPos(vec.xCoord, vec.yCoord, vec.zCoord);
        }

        public BlockPos.MutableBlockPos move(ForgeDirection facing) {
            return this.move(facing, 1);
        }

        public BlockPos.MutableBlockPos move(ForgeDirection facing, int n) {
            return this.setPos(this.x + facing.offsetX * n, this.y + facing.offsetY * n, this.z + facing.offsetZ * n);
        }

        public void setY(int yIn) {
            this.y = yIn;
        }

        /**
         * Returns a version of this BlockPos that is guaranteed to be immutable.
         *
         * <p>
         * When storing a BlockPos given to you for an extended period of time, make sure you
         * use this in case the value is changed internally.
         * </p>
         */
        public BlockPos toImmutable() {
            return new BlockPos(this);
        }
    }

    public static final class PooledMutableBlockPos extends BlockPos.MutableBlockPos {

        private boolean released;
        private static final List<PooledMutableBlockPos> POOL = Lists.<BlockPos.PooledMutableBlockPos>newArrayList();

        private PooledMutableBlockPos(int xIn, int yIn, int zIn) {
            super(xIn, yIn, zIn);
        }

        public static BlockPos.PooledMutableBlockPos retain() {
            return retain(0, 0, 0);
        }

        public static BlockPos.PooledMutableBlockPos retain(double xIn, double yIn, double zIn) {
            return retain(MathHelper.floor_double(xIn), MathHelper.floor_double(yIn), MathHelper.floor_double(zIn));
        }

        @SideOnly(Side.CLIENT)
        public static BlockPos.PooledMutableBlockPos retain(Vec3 vec) {
            return retain(vec.xCoord, vec.yCoord, vec.zCoord);
        }

        public static BlockPos.PooledMutableBlockPos retain(int xIn, int yIn, int zIn) {
            synchronized (POOL) {
                if (!POOL.isEmpty()) {
                    BlockPos.PooledMutableBlockPos blockpos$pooledmutableblockpos = POOL.remove(POOL.size() - 1);

                    if (blockpos$pooledmutableblockpos != null && blockpos$pooledmutableblockpos.released) {
                        blockpos$pooledmutableblockpos.released = false;
                        blockpos$pooledmutableblockpos.setPos(xIn, yIn, zIn);
                        return blockpos$pooledmutableblockpos;
                    }
                }
            }

            return new BlockPos.PooledMutableBlockPos(xIn, yIn, zIn);
        }

        public void release() {
            synchronized (POOL) {
                if (POOL.size() < 100) {
                    POOL.add(this);
                }

                this.released = true;
            }
        }

        /**
         * None
         */
        public BlockPos.PooledMutableBlockPos setPos(int xIn, int yIn, int zIn) {
            if (this.released) {
                BlockPos.LOGGER.error("PooledMutableBlockPosition modified after it was released.", new Throwable());
                this.released = false;
            }

            return (BlockPos.PooledMutableBlockPos) super.setPos(xIn, yIn, zIn);
        }

        @SideOnly(Side.CLIENT)
        public BlockPos.PooledMutableBlockPos setPos(Entity entityIn) {
            return (BlockPos.PooledMutableBlockPos) super.setPos(entityIn);
        }

        public BlockPos.PooledMutableBlockPos setPos(double xIn, double yIn, double zIn) {
            return (BlockPos.PooledMutableBlockPos) super.setPos(xIn, yIn, zIn);
        }

        public BlockPos.PooledMutableBlockPos setPos(Vec3 vec) {
            return (BlockPos.PooledMutableBlockPos) super.setPos(vec);
        }

        public BlockPos.PooledMutableBlockPos move(ForgeDirection facing) {
            return (BlockPos.PooledMutableBlockPos) super.move(facing);
        }

        public BlockPos.PooledMutableBlockPos move(ForgeDirection facing, int n) {
            return (BlockPos.PooledMutableBlockPos) super.move(facing, n);
        }
    }
}
