package kome.client.tactical;

import net.minecraft.block.Block;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/** Bounded crosshair walk for physical block surfaces. A missing chunk ends the walk without loading it. */
public final class KOMETacticalBlockTarget {
    public interface Cells {
        boolean loaded(int x, int y, int z);
        MovingObjectPosition collide(int x, int y, int z, Vec3 from, Vec3 to);
    }
    private KOMETacticalBlockTarget() { }

    public static MovingObjectPosition trace(final World world, double ox, double oy, double oz,
            double dx, double dy, double dz, double reach) {
        if (world == null || world.getChunkProvider() == null) return null;
        return trace(new Cells() {
            @Override public boolean loaded(int x, int y, int z) {
                return world.getChunkProvider().chunkExists(x >> 4, z >> 4);
            }
            @Override public MovingObjectPosition collide(int x, int y, int z, Vec3 from, Vec3 to) {
                if (y < 0 || y >= 256) return null;
                Block block = world.getBlock(x, y, z);
                return block.getCollisionBoundingBoxFromPool(world, x, y, z) != null
                    && block.canCollideCheck(world.getBlockMetadata(x, y, z), false)
                    ? block.collisionRayTrace(world, x, y, z, from, to) : null;
            }
        }, ox, oy, oz, dx, dy, dz, reach);
    }

    public static MovingObjectPosition trace(Cells cells, double ox, double oy, double oz,
            double dx, double dy, double dz, double reach) {
        if (cells == null || !Double.isFinite(ox) || !Double.isFinite(oy) || !Double.isFinite(oz)
                || !Double.isFinite(dx) || !Double.isFinite(dy) || !Double.isFinite(dz)
                || !Double.isFinite(reach) || reach <= 0 || reach > 128) return null;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0E-8D) return null;
        dx /= length; dy /= length; dz /= length;
        double ex = ox + dx * reach, ey = oy + dy * reach, ez = oz + dz * reach;
        if (Math.abs(ox) > Integer.MAX_VALUE - 256D || Math.abs(oy) > Integer.MAX_VALUE - 256D
                || Math.abs(oz) > Integer.MAX_VALUE - 256D || Math.abs(ex) > Integer.MAX_VALUE - 256D
                || Math.abs(ey) > Integer.MAX_VALUE - 256D || Math.abs(ez) > Integer.MAX_VALUE - 256D) return null;
        Vec3 from = Vec3.createVectorHelper(ox, oy, oz), to = Vec3.createVectorHelper(ex, ey, ez);
        int x = (int)Math.floor(ox), y = (int)Math.floor(oy), z = (int)Math.floor(oz);
        int sx = dx > 0 ? 1 : dx < 0 ? -1 : 0;
        int sy = dy > 0 ? 1 : dy < 0 ? -1 : 0;
        int sz = dz > 0 ? 1 : dz < 0 ? -1 : 0;
        double tx = sx == 0 ? Double.POSITIVE_INFINITY : ((sx > 0 ? x + 1D : x) - ox) / dx;
        double ty = sy == 0 ? Double.POSITIVE_INFINITY : ((sy > 0 ? y + 1D : y) - oy) / dy;
        double tz = sz == 0 ? Double.POSITIVE_INFINITY : ((sz > 0 ? z + 1D : z) - oz) / dz;
        double stepx = sx == 0 ? Double.POSITIVE_INFINITY : Math.abs(1D / dx);
        double stepy = sy == 0 ? Double.POSITIVE_INFINITY : Math.abs(1D / dy);
        double stepz = sz == 0 ? Double.POSITIVE_INFINITY : Math.abs(1D / dz);
        for (int i = 0; i < 390; i++) {
            if (!cells.loaded(x, y, z)) return null;
            MovingObjectPosition hit = cells.collide(x, y, z, from, to);
            if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) return hit;
            double next = Math.min(tx, Math.min(ty, tz));
            if (next > reach || !Double.isFinite(next)) return null;
            if (tx <= ty && tx <= tz) { x += sx; tx += stepx; }
            else if (ty <= tz) { y += sy; ty += stepy; }
            else { z += sz; tz += stepz; }
        }
        return null;
    }
}
