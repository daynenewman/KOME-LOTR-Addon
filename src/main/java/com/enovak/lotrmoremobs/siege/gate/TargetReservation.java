package com.enovak.lotrmoremobs.siege.gate;

import java.util.UUID;

/** Persisted-only Phase 4A ADD-coordinate reservation; no acquisition API yet. */
final class TargetReservation {
    private final UUID jobUuid;
    private final UUID gateUuid;
    private final int baseRevision;
    private final int targetRevision;
    private final int dimension;
    private final int x;
    private final int y;
    private final int z;

    TargetReservation(UUID jobUuid, UUID gateUuid, int baseRevision,
            int targetRevision, int dimension, int x, int y, int z) {
        this.jobUuid = jobUuid;
        this.gateUuid = gateUuid;
        this.baseRevision = baseRevision;
        this.targetRevision = targetRevision;
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    UUID getJobUuid() { return jobUuid; }
    UUID getGateUuid() { return gateUuid; }
    int getBaseRevision() { return baseRevision; }
    int getTargetRevision() { return targetRevision; }
    int getDimension() { return dimension; }
    int getX() { return x; }
    int getY() { return y; }
    int getZ() { return z; }
}
