package com.enovak.lotrmoremobs.siege.edit;

import com.enovak.lotrmoremobs.siege.gate.GateHinge;
import com.enovak.lotrmoremobs.siege.gate.GateOpeningDirection;
import com.enovak.lotrmoremobs.siege.gate.GateOrientation;
import com.enovak.lotrmoremobs.siege.gate.GatePartData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Detached, immutable server-draft material for a future durable PREPARED
 * admission. It intentionally contains no World, player, token, job UUID, or
 * persistent ownership mutation capability.
 */
public final class GateEditCommitMaterial {
    public enum PhysicalOperationKind {
        REMOVE,
        ADD
    }

    public static final class PhysicalOperation {
        private final PhysicalOperationKind kind;
        private final GatePartData part;

        PhysicalOperation(PhysicalOperationKind kind, GatePartData part) {
            this.kind = kind;
            this.part = part;
        }

        public PhysicalOperationKind getKind() { return kind; }
        public GatePartData getPart() { return part; }
    }

    private final UUID gateUuid;
    private final int dimension;
    private final int controllerX;
    private final int controllerY;
    private final int controllerZ;
    private final int baseRevision;
    private final GateOrientation orientation;
    private final GateOpeningDirection originalOpeningDirection;
    private final boolean originalBorderTextureEnabled;
    private final GateHinge originalLeftHinge;
    private final GateHinge originalRightHinge;
    private final GateOpeningDirection targetOpeningDirection;
    private final boolean targetBorderTextureEnabled;
    private final GateHinge targetLeftHinge;
    private final GateHinge targetRightHinge;
    private final List<GatePartData> originalParts;
    private final List<GatePartData> targetParts;
    private final List<PhysicalOperation> physicalOperations;

    GateEditCommitMaterial(UUID gateUuid, int dimension, int controllerX,
            int controllerY, int controllerZ, int baseRevision,
            GateOrientation orientation,
            GateOpeningDirection originalOpeningDirection,
            boolean originalBorderTextureEnabled,
            GateHinge originalLeftHinge, GateHinge originalRightHinge,
            GateOpeningDirection targetOpeningDirection,
            boolean targetBorderTextureEnabled,
            GateHinge targetLeftHinge, GateHinge targetRightHinge,
            List<GatePartData> originalParts, List<GatePartData> targetParts,
            List<PhysicalOperation> physicalOperations) {
        this.gateUuid = gateUuid;
        this.dimension = dimension;
        this.controllerX = controllerX;
        this.controllerY = controllerY;
        this.controllerZ = controllerZ;
        this.baseRevision = baseRevision;
        this.orientation = orientation;
        this.originalOpeningDirection = originalOpeningDirection;
        this.originalBorderTextureEnabled = originalBorderTextureEnabled;
        this.originalLeftHinge = copy(originalLeftHinge);
        this.originalRightHinge = copy(originalRightHinge);
        this.targetOpeningDirection = targetOpeningDirection;
        this.targetBorderTextureEnabled = targetBorderTextureEnabled;
        this.targetLeftHinge = copy(targetLeftHinge);
        this.targetRightHinge = copy(targetRightHinge);
        this.originalParts = Collections.unmodifiableList(
                new ArrayList<GatePartData>(originalParts)
        );
        this.targetParts = Collections.unmodifiableList(
                new ArrayList<GatePartData>(targetParts)
        );
        this.physicalOperations = Collections.unmodifiableList(
                new ArrayList<PhysicalOperation>(physicalOperations)
        );
    }

    public UUID getGateUuid() { return gateUuid; }
    public int getDimension() { return dimension; }
    public int getControllerX() { return controllerX; }
    public int getControllerY() { return controllerY; }
    public int getControllerZ() { return controllerZ; }
    public int getBaseRevision() { return baseRevision; }
    public GateOrientation getOrientation() { return orientation; }
    public GateOpeningDirection getOriginalOpeningDirection() { return originalOpeningDirection; }
    public boolean isOriginalBorderTextureEnabled() { return originalBorderTextureEnabled; }
    public GateHinge getOriginalLeftHinge() { return copy(originalLeftHinge); }
    public GateHinge getOriginalRightHinge() { return copy(originalRightHinge); }
    public GateOpeningDirection getTargetOpeningDirection() { return targetOpeningDirection; }
    public boolean isTargetBorderTextureEnabled() { return targetBorderTextureEnabled; }
    public GateHinge getTargetLeftHinge() { return copy(targetLeftHinge); }
    public GateHinge getTargetRightHinge() { return copy(targetRightHinge); }
    public List<GatePartData> getOriginalParts() { return originalParts; }
    public List<GatePartData> getTargetParts() { return targetParts; }
    public List<PhysicalOperation> getPhysicalOperations() { return physicalOperations; }

    private static GateHinge copy(GateHinge hinge) {
        return hinge == null ? null : new GateHinge(
                hinge.getRelativeX(), hinge.getRelativeZ(), hinge.getSide()
        );
    }
}
