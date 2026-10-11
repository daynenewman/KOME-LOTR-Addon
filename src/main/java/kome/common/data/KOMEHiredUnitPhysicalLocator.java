package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;

import java.util.UUID;

/**
 * Durable address for one verified physical Campaign entity.
 *
 * This is only sufficient to attempt a bounded entity verification later. It is
 * not strategic position authority, proof that the entity is still alive, or
 * permission to load a chunk or teleport a player.
 */
public final class KOMEHiredUnitPhysicalLocator {
    public static final int DATA_SCHEMA_VERSION = 1;
    static final String TAG = "PhysicalLocator";

    public enum CaptureKind {
        LIVE_OBSERVATION,
        CHUNK_UNLOAD,
        STRATEGIC_RECONSTRUCTION
    }

    private final UUID entityId;
    private final int dimensionId;
    private final int chunkX;
    private final int chunkZ;
    private final double x;
    private final double y;
    private final double z;
    private final String physicalTileId;
    private final long observedAtMillis;
    private final CaptureKind captureKind;

    private KOMEHiredUnitPhysicalLocator(UUID entityId, int dimensionId,
            int chunkX, int chunkZ, double x, double y, double z,
            String physicalTileId, long observedAtMillis, CaptureKind captureKind,
            KOMETileWorldResolver resolver) {
        if (entityId == null) throw new IllegalArgumentException("Physical locator entity UUID is required");
        if (dimensionId == Integer.MIN_VALUE) throw new IllegalArgumentException("Physical locator dimension is unknown");
        if (!finite(x) || !finite(y) || !finite(z))
            throw new IllegalArgumentException("Physical locator coordinates must be finite");
        if (chunkX != block(x) >> 4 || chunkZ != block(z) >> 4)
            throw new IllegalArgumentException("Physical locator chunk does not match its coordinates");
        String tile = KOMEConquestTile.normalizeId(physicalTileId);
        if (!KOMEConquestTile.isCanonicalTileId(tile)
                || !KOMEConquestTileDefaults.getKnownTileIds().contains(tile))
            throw new IllegalArgumentException("Physical locator tile is not canonical: " + physicalTileId);
        if (observedAtMillis < 0L)
            throw new IllegalArgumentException("Physical locator observation timestamp is invalid");
        if (captureKind == null) throw new IllegalArgumentException("Physical locator capture kind is required");
        KOMETileResolution resolved = resolver.resolveWorldPosition(dimensionId, x, z);
        if (resolved.status != KOMETileResolution.Status.RESOLVED || !tile.equals(resolved.tileId))
            throw new IllegalArgumentException("Physical locator coordinates do not resolve to " + tile);
        this.entityId = entityId;
        this.dimensionId = dimensionId;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.x = x;
        this.y = y;
        this.z = z;
        this.physicalTileId = tile;
        this.observedAtMillis = observedAtMillis;
        this.captureKind = captureKind;
    }

    static KOMEHiredUnitPhysicalLocator verified(UUID entityId, int dimensionId,
            double x, double y, double z, String physicalTileId,
            long observedAtMillis, CaptureKind captureKind) {
        return verified(entityId, dimensionId, block(x) >> 4, block(z) >> 4,
            x, y, z, physicalTileId, observedAtMillis, captureKind,
            KOMETileWorldResolver.INSTANCE);
    }

    static KOMEHiredUnitPhysicalLocator verified(UUID entityId, int dimensionId,
            int chunkX, int chunkZ, double x, double y, double z,
            String physicalTileId, long observedAtMillis, CaptureKind captureKind,
            KOMETileWorldResolver resolver) {
        return new KOMEHiredUnitPhysicalLocator(entityId, dimensionId, chunkX,
            chunkZ, x, y, z, physicalTileId, observedAtMillis, captureKind, resolver);
    }

    static KOMEHiredUnitPhysicalLocator readFromNBT(NBTTagCompound nbt,
            UUID expectedEntityId) {
        return readFromNBT(nbt, expectedEntityId, KOMETileWorldResolver.INSTANCE);
    }

    static KOMEHiredUnitPhysicalLocator readFromNBT(NBTTagCompound nbt,
            UUID expectedEntityId, KOMETileWorldResolver resolver) {
        if (nbt == null) throw new IllegalArgumentException("Physical locator data is required");
        require(nbt, "PhysicalLocatorDataSchemaVersion", 3);
        int version = nbt.getInteger("PhysicalLocatorDataSchemaVersion");
        if (version != DATA_SCHEMA_VERSION)
            throw new IllegalArgumentException("Unsupported physical locator schema " + version);
        require(nbt, "Entity", 8);
        require(nbt, "Dimension", 3);
        require(nbt, "ChunkX", 3);
        require(nbt, "ChunkZ", 3);
        require(nbt, "X", 6);
        require(nbt, "Y", 6);
        require(nbt, "Z", 6);
        require(nbt, "PhysicalTile", 8);
        require(nbt, "ObservedAtMillis", 4);
        require(nbt, "CaptureKind", 8);
        UUID entityId;
        CaptureKind kind;
        try {
            entityId = UUID.fromString(nbt.getString("Entity"));
            kind = CaptureKind.valueOf(nbt.getString("CaptureKind"));
        } catch (RuntimeException malformed) {
            throw new IllegalArgumentException("Invalid physical locator identity or capture kind", malformed);
        }
        if (expectedEntityId == null || !expectedEntityId.equals(entityId))
            throw new IllegalArgumentException("Physical locator UUID does not match hired-unit record");
        return verified(entityId, nbt.getInteger("Dimension"), nbt.getInteger("ChunkX"),
            nbt.getInteger("ChunkZ"), nbt.getDouble("X"), nbt.getDouble("Y"),
            nbt.getDouble("Z"), nbt.getString("PhysicalTile"),
            nbt.getLong("ObservedAtMillis"), kind, resolver);
    }

    NBTTagCompound writeToNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setInteger("PhysicalLocatorDataSchemaVersion", DATA_SCHEMA_VERSION);
        nbt.setString("Entity", entityId.toString());
        nbt.setInteger("Dimension", dimensionId);
        nbt.setInteger("ChunkX", chunkX);
        nbt.setInteger("ChunkZ", chunkZ);
        nbt.setDouble("X", x);
        nbt.setDouble("Y", y);
        nbt.setDouble("Z", z);
        nbt.setString("PhysicalTile", physicalTileId);
        nbt.setLong("ObservedAtMillis", observedAtMillis);
        nbt.setString("CaptureKind", captureKind.name());
        return nbt;
    }

    void validateAttachedTo(UUID expectedEntityId) {
        if (expectedEntityId == null || !expectedEntityId.equals(entityId))
            throw new IllegalArgumentException("Physical locator UUID does not match hired-unit record");
    }

    boolean sameAddress(UUID id, int dimension, int chunkX, int chunkZ, String tile) {
        return entityId.equals(id) && dimensionId == dimension && this.chunkX == chunkX
            && this.chunkZ == chunkZ && physicalTileId.equals(KOMEConquestTile.normalizeId(tile));
    }

    boolean sameObservation(double x, double y, double z, CaptureKind kind) {
        return Double.compare(this.x, x) == 0 && Double.compare(this.y, y) == 0
            && Double.compare(this.z, z) == 0 && captureKind == kind;
    }

    public UUID getEntityId() { return entityId; }
    public int getDimensionId() { return dimensionId; }
    public int getChunkX() { return chunkX; }
    public int getChunkZ() { return chunkZ; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public String getPhysicalTileId() { return physicalTileId; }
    public long getObservedAtMillis() { return observedAtMillis; }
    public CaptureKind getCaptureKind() { return captureKind; }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static int block(double value) {
        if (!finite(value) || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Physical locator coordinate is outside block range");
        return (int) Math.floor(value);
    }

    private static void require(NBTTagCompound nbt, String key, int type) {
        if (!nbt.hasKey(key, type))
            throw new IllegalArgumentException("Physical locator is missing or has invalid " + key);
    }
}
