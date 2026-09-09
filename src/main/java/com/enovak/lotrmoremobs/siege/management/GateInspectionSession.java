package com.enovak.lotrmoremobs.siege.management;

import java.util.UUID;

/** One disposable, read-only INSPECT_EXISTING session for one player. */
public final class GateInspectionSession {

    private final UUID playerUuid;
    private final UUID gateUuid;
    private final int dimensionId;
    private final int controllerX;
    private final int controllerY;
    private final int controllerZ;
    private final int baseRevision;
    private final FinalizedGateSnapshot snapshot;
    private final long expiresAtTick;

    GateInspectionSession(
            UUID playerUuid,
            FinalizedGateSnapshot snapshot,
            long expiresAtTick
    ) {
        if (playerUuid == null || snapshot == null) {
            throw new IllegalArgumentException("Inspection session identity is required.");
        }
        this.playerUuid = playerUuid;
        this.gateUuid = snapshot.getGateUuid();
        this.dimensionId = snapshot.getDimensionId();
        this.controllerX = snapshot.getControllerX();
        this.controllerY = snapshot.getControllerY();
        this.controllerZ = snapshot.getControllerZ();
        this.baseRevision = snapshot.getBaseStructureRevision();
        this.snapshot = snapshot;
        this.expiresAtTick = expiresAtTick;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public UUID getGateUuid() {
        return gateUuid;
    }

    public int getDimensionId() {
        return dimensionId;
    }

    public int getControllerX() {
        return controllerX;
    }

    public int getControllerY() {
        return controllerY;
    }

    public int getControllerZ() {
        return controllerZ;
    }

    public int getBaseRevision() {
        return baseRevision;
    }

    public FinalizedGateSnapshot getSnapshot() {
        return snapshot;
    }

    boolean isExpired(long currentTick) {
        return currentTick >= expiresAtTick;
    }
}
