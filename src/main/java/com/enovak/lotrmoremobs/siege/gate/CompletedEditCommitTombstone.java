package com.enovak.lotrmoremobs.siege.gate;

import java.util.UUID;

/** Bounded post-completion forensic identity, intentionally without snapshots. */
final class CompletedEditCommitTombstone {
    private final UUID jobUuid;
    private final UUID gateUuid;
    private final int dimension;
    private final int controllerX;
    private final int controllerY;
    private final int controllerZ;
    private final int baseRevision;
    private final int targetRevision;
    private final long completedTick;

    CompletedEditCommitTombstone(UUID jobUuid, UUID gateUuid, int dimension,
            int controllerX, int controllerY, int controllerZ, int baseRevision,
            int targetRevision, long completedTick) {
        this.jobUuid = jobUuid;
        this.gateUuid = gateUuid;
        this.dimension = dimension;
        this.controllerX = controllerX;
        this.controllerY = controllerY;
        this.controllerZ = controllerZ;
        this.baseRevision = baseRevision;
        this.targetRevision = targetRevision;
        this.completedTick = completedTick;
    }

    UUID getJobUuid() { return jobUuid; }
    UUID getGateUuid() { return gateUuid; }
    int getDimension() { return dimension; }
    int getControllerX() { return controllerX; }
    int getControllerY() { return controllerY; }
    int getControllerZ() { return controllerZ; }
    int getBaseRevision() { return baseRevision; }
    int getTargetRevision() { return targetRevision; }
    long getCompletedTick() { return completedTick; }
}
