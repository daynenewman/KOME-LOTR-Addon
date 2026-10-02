package kome.common.data;

import java.util.Optional;

/** Read-only spatial identity, never a copy of a tile's mutable ownership. */
public final class KOMETileResolution {
    public enum Status {
        RESOLVED, IN_BOUNDS_GAP, OUTSIDE_MASK, UNSUPPORTED_DIMENSION, INVALID_SNAPSHOT,
        INVALID_COORDINATE, CLASSIFIED_EXCLUSION
    }

    public final Status status;
    public final int dimension;
    public final int worldX;
    public final int worldZ;
    public final boolean hasWorldCoordinate;
    public final long maskX;
    public final long maskY;
    public final boolean hasMaskCoordinate;
    /** Empty unless status is RESOLVED. Prefer resolvedTileId() for optional use. */
    public final String tileId;
    public final String diagnostic;
    private final KOMETileExclusions.Zone exclusion;

    KOMETileResolution(Status status, int dimension, int worldX, int worldZ,
            boolean hasWorldCoordinate, long maskX, long maskY, boolean hasMaskCoordinate,
            String tileId, String diagnostic) {
        this(status, dimension, worldX, worldZ, hasWorldCoordinate, maskX, maskY, hasMaskCoordinate,
            tileId, diagnostic, null);
    }

    KOMETileResolution(Status status, int dimension, int worldX, int worldZ,
            boolean hasWorldCoordinate, long maskX, long maskY, boolean hasMaskCoordinate,
            String tileId, String diagnostic, KOMETileExclusions.Zone exclusion) {
        this.exclusion = exclusion;
        this.status = status;
        this.dimension = dimension;
        this.worldX = worldX;
        this.worldZ = worldZ;
        this.hasWorldCoordinate = hasWorldCoordinate;
        this.maskX = maskX;
        this.maskY = maskY;
        this.hasMaskCoordinate = hasMaskCoordinate;
        this.tileId = tileId;
        this.diagnostic = diagnostic;
    }

    public Optional<String> resolvedTileId() {
        return status == Status.RESOLVED ? Optional.of(tileId) : Optional.<String>empty();
    }

    /** Exclusions alone establish false; tile identity is not permission to capture. */
    public Optional<Boolean> capturable() {
        return exclusion == null ? Optional.<Boolean>empty() : Optional.of(false);
    }

    public Optional<KOMETileExclusions.Zone> exclusion() { return Optional.ofNullable(exclusion); }

    /** Requires future unit-specific movement rules; neither tiles nor exclusions imply passage. */
    public Optional<Boolean> traversable() { return Optional.empty(); }

    static KOMETileResolution unavailable(Status status, int dimension, int x, int z, String reason) {
        return new KOMETileResolution(status, dimension, x, z, status != Status.INVALID_COORDINATE,
            0L, 0L, false, "", reason);
    }

    @Override public String toString() {
        return status + " dimension=" + dimension
            + (hasWorldCoordinate ? " world=" + worldX + "," + worldZ : "")
            + (hasMaskCoordinate ? " mask=" + maskX + "," + maskY : "")
            + (tileId.isEmpty() ? "" : " tile=" + tileId) + " (" + diagnostic + ")";
    }
}
