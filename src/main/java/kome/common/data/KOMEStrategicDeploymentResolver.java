package kome.common.data;

import lotr.common.LOTRMod;
import lotr.common.LOTRDimension;
import net.minecraft.block.Block;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Reusable server-side standing-anchor validation for strategic deployment systems. */
public final class KOMEStrategicDeploymentResolver {
    public static final int DEFAULT_SEARCH_RADIUS = 24;
    private KOMEStrategicDeploymentResolver() { }

    public static Validation validateMetadata(String expectedTile, int dimensionId,
            double x, double y, double z) {
        if (!finite(x) || !finite(y) || !finite(z))
            return Validation.invalid("Capital deployment coordinates must be finite.");
        if (dimensionId != LOTRDimension.MIDDLE_EARTH.dimensionID)
            return Validation.invalid("Capital deployment dimension is not Middle-earth: " + dimensionId);
        if (y < 1.0D || y >= 255.0D)
            return Validation.invalid("Capital deployment height is outside the valid world range.");
        String tile = KOMEConquestTile.normalizeId(expectedTile);
        if (!KOMEConquestTile.isCanonicalTileId(tile) || KOMEConquestTileDefaults.isRetiredTile(tile)
                || !KOMEConquestTileDefaults.getKnownTileIds().contains(tile))
            return Validation.invalid("Capital tile is unknown, malformed, or retired: " + tile);
        String actual = KOMEBuildService.tileAtWorldCoordinates(x, z);
        if (!tile.equals(actual))
            return Validation.invalid("Capital deployment X/Z is not inside capital tile " + tile + ".");
        return Validation.valid(new Anchor(dimensionId, x, y, z));
    }

    /** Finds a safe point near a live position without leaving the designated strategic tile. */
    public static Validation resolveAround(World world, String expectedTile, double preferredX,
            double preferredY, double preferredZ, int maximumRadius) {
        return resolveAround(world, expectedTile, preferredX, preferredY, preferredZ,
            maximumRadius, 0.6D, 1.8D);
    }

    public static Validation resolveAround(World world, String expectedTile, double preferredX,
            double preferredY, double preferredZ, int maximumRadius,
            double requiredWidth, double requiredHeight) {
        if (!finite(preferredX) || !finite(preferredY) || !finite(preferredZ))
            return Validation.invalid("Capital deployment coordinates must be finite.");
        if (!finite(requiredWidth) || !finite(requiredHeight)
                || requiredWidth <= 0.0D || requiredHeight <= 0.0D)
            return Validation.invalid("Deployment clearance dimensions must be finite and positive.");
        if (world == null || world.provider == null
                || world.provider.dimensionId != LOTRDimension.MIDDLE_EARTH.dimensionID)
            return Validation.invalid("The live Middle-earth world is unavailable.");
        String tile = KOMEConquestTile.normalizeId(expectedTile);
        if (!KOMEConquestTile.isCanonicalTileId(tile) || KOMEConquestTileDefaults.isRetiredTile(tile)
                || !KOMEConquestTileDefaults.getKnownTileIds().contains(tile))
            return Validation.invalid("Capital tile is unknown, malformed, or retired: " + tile);
        int originX = MathHelper.floor_double(preferredX);
        int originZ = MathHelper.floor_double(preferredZ);
        int radiusLimit = Math.max(0, maximumRadius);
        for (int radius = 0; radius <= radiusLimit; radius++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                    int x = originX + dx, z = originZ + dz;
                    if (!tile.equals(KOMEBuildService.tileAtWorldCoordinates(x + 0.5D, z + 0.5D)))
                        continue;
                    if (!ensureChunkAvailable(world, x, z)) continue;
                    int liveY;
                    try {
                        liveY = LOTRMod.getTrueTopBlock(world, x, z);
                    } catch (Throwable unavailable) {
                        continue;
                    }
                    if (radius == 0) {
                        int requestedY = MathHelper.floor_double(preferredY);
                        if (isSafeStandingAnchor(world, x, requestedY, z,
                                requiredWidth, requiredHeight))
                            return Validation.valid(new Anchor(world.provider.dimensionId,
                                x + 0.5D, requestedY, z + 0.5D));
                    }
                    for (int offset = -1; offset <= 2; offset++) {
                        int y = liveY + offset;
                        if (isSafeStandingAnchor(world, x, y, z,
                                requiredWidth, requiredHeight))
                            return Validation.valid(new Anchor(world.provider.dimensionId,
                                x + 0.5D, y, z + 0.5D));
                    }
                }
            }
        }
        return Validation.invalid("No safe capital deployment anchor was found inside " + tile
            + " within " + radiusLimit + " blocks of the requested position.");
    }

    /**
     * Small reusable formation primitive. Each returned standing box is on a distinct block,
     * remains inside the strategic tile, and is checked against live block collisions.
     */
    public static Formation resolveCompactFormation(World world, String expectedTile,
            double preferredX, double preferredY, double preferredZ, int unitCount,
            int maximumRadius) {
        if (unitCount < 1 || unitCount > 64)
            return Formation.invalid("Compact formation size must be between 1 and 64.");
        Validation first = resolveAround(world, expectedTile, preferredX, preferredY,
            preferredZ, maximumRadius);
        if (!first.valid) return Formation.invalid(first.reason);
        List<Anchor> anchors = new ArrayList<Anchor>();
        anchors.add(first.anchor);
        int originX = MathHelper.floor_double(preferredX);
        int originZ = MathHelper.floor_double(preferredZ);
        String tile = KOMEConquestTile.normalizeId(expectedTile);
        for (int radius = 1; radius <= maximumRadius && anchors.size() < unitCount; radius++) {
            for (int dz = -radius; dz <= radius && anchors.size() < unitCount; dz++) {
                for (int dx = -radius; dx <= radius && anchors.size() < unitCount; dx++) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                    int x = originX + dx, z = originZ + dz;
                    if (!tile.equals(KOMEBuildService.tileAtWorldCoordinates(
                            x + 0.5D, z + 0.5D)) || !ensureChunkAvailable(world, x, z)) continue;
                    int liveY;
                    try { liveY = LOTRMod.getTrueTopBlock(world, x, z); }
                    catch (Throwable unavailable) { continue; }
                    for (int offset = -1; offset <= 2; offset++) {
                        int y = liveY + offset;
                        if (isSafeStandingAnchor(world, x, y, z)) {
                            if (!containsColumn(anchors, x, z))
                                anchors.add(new Anchor(world.provider.dimensionId,
                                    x + 0.5D, y, z + 0.5D));
                            break;
                        }
                    }
                }
            }
        }
        return anchors.size() == unitCount ? Formation.valid(anchors)
            : Formation.invalid("No collision-free compact formation of " + unitCount
                + " units fits inside " + tile + " within " + maximumRadius + " blocks.");
    }

    /** Exact read-only validation; this never searches, repairs, or manufactures a point. */
    public static Validation validateStored(World world, String expectedTile, int dimensionId,
            double x, double y, double z) {
        Validation metadata = validateMetadata(expectedTile, dimensionId, x, y, z);
        if (!metadata.valid) return metadata;
        if (world == null || world.provider == null || world.provider.dimensionId != dimensionId)
            return Validation.invalid("Capital deployment world is unavailable or has the wrong dimension.");
        int blockX = MathHelper.floor_double(x);
        int blockY = MathHelper.floor_double(y);
        int blockZ = MathHelper.floor_double(z);
        if (!ensureChunkAvailable(world, blockX, blockZ))
            return Validation.invalid("Capital deployment chunk is unavailable.");
        if (!isSafeStandingAnchor(world, blockX, blockY, blockZ))
            return Validation.invalid("Stored capital deployment anchor is no longer safe for standing.");
        return metadata;
    }

    public static boolean ensureChunkAvailable(World world, int x, int z) {
        if (world == null) return false;
        int chunkX = x >> 4, chunkZ = z >> 4;
        try {
            IChunkProvider provider = world.getChunkProvider();
            if (provider != null && !provider.chunkExists(chunkX, chunkZ)) {
                Chunk loaded = provider.provideChunk(chunkX, chunkZ);
                if (loaded == null) return false;
            }
            world.getChunkFromChunkCoords(chunkX, chunkZ);
            return world.blockExists(x, 64, z);
        } catch (Throwable unavailable) {
            return false;
        }
    }

    private static boolean isSafeStandingAnchor(World world, int x, int y, int z) {
        return isSafeStandingAnchor(world, x, y, z, 0.6D, 1.8D);
    }

    static boolean isSafeStandingAnchor(World world, int x, int y, int z,
            double requiredWidth, double requiredHeight) {
        if (world == null || !finite(requiredWidth) || !finite(requiredHeight)
                || requiredWidth <= 0.0D || requiredHeight <= 0.0D
                || y < 1 || y + requiredHeight > world.getActualHeight()
                || !world.blockExists(x, y, z)) return false;
        double halfWidth = requiredWidth / 2.0D;
        AxisAlignedBB body = AxisAlignedBB.getBoundingBox(
            x + 0.5D - halfWidth, y, z + 0.5D - halfWidth,
            x + 0.5D + halfWidth, y + requiredHeight, z + 0.5D + halfWidth);
        int minimumX = MathHelper.floor_double(body.minX);
        int maximumX = MathHelper.floor_double(body.maxX - 1.0E-7D);
        int minimumZ = MathHelper.floor_double(body.minZ);
        int maximumZ = MathHelper.floor_double(body.maxZ - 1.0E-7D);
        for (int supportX = minimumX; supportX <= maximumX; supportX++) {
            for (int supportZ = minimumZ; supportZ <= maximumZ; supportZ++) {
                if (!ensureChunkAvailable(world, supportX, supportZ)) return false;
                Block ground = world.getBlock(supportX, y - 1, supportZ);
                if (ground == null || ground.getMaterial().isLiquid()
                        || ground.getCollisionBoundingBoxFromPool(
                            world, supportX, y - 1, supportZ) == null) return false;
            }
        }
        try {
            return world.getCollidingBoundingBoxes(null, body).isEmpty();
        } catch (Throwable invalidGeometry) {
            return false;
        }
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static boolean containsColumn(List<Anchor> anchors, int x, int z) {
        for (Anchor anchor : anchors) {
            if (MathHelper.floor_double(anchor.x) == x
                    && MathHelper.floor_double(anchor.z) == z) return true;
        }
        return false;
    }

    public static final class Anchor {
        public final int dimensionId;
        public final double x, y, z;
        public Anchor(int dimensionId, double x, double y, double z) {
            this.dimensionId = dimensionId; this.x = x; this.y = y; this.z = z;
        }
    }

    public static final class Formation {
        public final boolean valid;
        public final String reason;
        public final List<Anchor> anchors;
        private Formation(boolean valid, String reason, List<Anchor> anchors) {
            this.valid = valid;
            this.reason = reason == null ? "" : reason;
            this.anchors = anchors;
        }
        static Formation valid(List<Anchor> anchors) {
            return new Formation(true, "Ready", Collections.unmodifiableList(
                new ArrayList<Anchor>(anchors)));
        }
        static Formation invalid(String reason) {
            return new Formation(false, reason, Collections.<Anchor>emptyList());
        }
    }

    public static final class Validation {
        public final boolean valid;
        public final String reason;
        public final Anchor anchor;
        private Validation(boolean valid, String reason, Anchor anchor) {
            this.valid = valid; this.reason = reason == null ? "" : reason; this.anchor = anchor;
        }
        public static Validation valid(Anchor anchor) { return new Validation(true, "Ready", anchor); }
        public static Validation invalid(String reason) { return new Validation(false, reason, null); }
    }
}
