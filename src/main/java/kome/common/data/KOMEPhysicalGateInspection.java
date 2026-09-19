package kome.common.data;

import com.enovak.lotrmoremobs.siege.gate.GateHinge;
import com.enovak.lotrmoremobs.siege.gate.GateOpeningDirection;
import com.enovak.lotrmoremobs.siege.gate.GateOrientation;
import com.enovak.lotrmoremobs.siege.gate.GatePartData;
import com.enovak.lotrmoremobs.siege.gate.GateStructureValidator;
import com.enovak.lotrmoremobs.siege.gate.SiegeGateOwnershipData;
import com.enovak.lotrmoremobs.siege.tile.TileEntitySiegeGate;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Explicit, O(stored gate parts) adapter from a physical controller to KOME metadata. */
public final class KOMEPhysicalGateInspection {
    private KOMEPhysicalGateInspection() {
    }

    public static Result inspect(TileEntitySiegeGate controller) {
        if (controller == null || controller.getWorldObj() == null
                || controller.getWorldObj().isRemote) {
            return Result.invalid("A loaded server-side Siege Gate controller is required.");
        }
        SiegeGateOwnershipData ownership = SiegeGateOwnershipData.get(
            controller.getWorldObj(), false);
        return inspect(new Snapshot(
            controller.getWorldObj().provider.dimensionId,
            controller.xCoord, controller.yCoord, controller.zCoord,
            controller.getExistingGateUuid(), controller.getStructureRevision(),
            controller.isFinalized(), controller.isGateStructureQuarantined(),
            controller.hasCompleteHingeConfiguration(), controller.getGateOrientation(),
            controller.getOpeningDirection(), controller.getLeftHinge(),
            controller.getRightHinge(), controller.getGateParts(),
            ownership != null && ownership.matchesActiveController(controller)));
    }

    /** Pure snapshot entry point used by deterministic tests and by the controller adapter. */
    public static Result inspect(Snapshot snapshot) {
        if (snapshot == null) return Result.invalid("Physical gate data is unavailable.");
        if (snapshot.gateUuid == null) return Result.invalid("The physical gate UUID is missing.");
        if (snapshot.structureRevision <= 0) return Result.invalid("The structure revision is invalid.");
        if (!snapshot.finalized || snapshot.quarantined) {
            return Result.invalid("The physical gate is not finalized or is quarantined.");
        }
        if (!snapshot.canonicalActive) {
            return Result.invalid("The physical gate is not an active canonical controller.");
        }
        if (!snapshot.completeHingeConfiguration || snapshot.orientation == null
                || snapshot.openingDirection == null || snapshot.leftHinge == null
                || snapshot.rightHinge == null) {
            return Result.invalid("The finalized gate configuration is incomplete.");
        }

        GateStructureValidator.ValidationResult validation =
            GateStructureValidator.validateFinalized(snapshot.parts, snapshot.leftHinge,
                snapshot.rightHinge, snapshot.orientation, snapshot.openingDirection,
                snapshot.controllerX, snapshot.controllerY, snapshot.controllerZ);
        if (!validation.isValid()) {
            return Result.invalid("Physical gate validation failed: " + validation.getFailure().name());
        }

        int minWidth = Integer.MAX_VALUE;
        int maxWidth = Integer.MIN_VALUE;
        int minHeight = Integer.MAX_VALUE;
        int maxHeight = Integer.MIN_VALUE;
        Set<Integer> depthPlanes = new HashSet<Integer>();
        Map<String, Integer> projectedCounts = new HashMap<String, Integer>();
        for (GatePartData part : snapshot.parts) {
            int widthCoordinate = snapshot.orientation == GateOrientation.WIDTH_X
                ? part.getRelativeX() : part.getRelativeZ();
            int depthCoordinate = snapshot.orientation == GateOrientation.WIDTH_X
                ? part.getRelativeZ() : part.getRelativeX();
            int heightCoordinate = part.getRelativeY();
            minWidth = Math.min(minWidth, widthCoordinate);
            maxWidth = Math.max(maxWidth, widthCoordinate);
            minHeight = Math.min(minHeight, heightCoordinate);
            maxHeight = Math.max(maxHeight, heightCoordinate);
            depthPlanes.add(Integer.valueOf(depthCoordinate));
            String projectedKey = widthCoordinate + ":" + heightCoordinate;
            Integer count = projectedCounts.get(projectedKey);
            projectedCounts.put(projectedKey, Integer.valueOf(count == null ? 1 : count.intValue() + 1));
        }

        int width = maxWidth - minWidth + 1;
        int height = maxHeight - minHeight + 1;
        int projectedArea = projectedCounts.size();
        long envelopeArea = (long) width * (long) height;
        boolean filledEnvelope = envelopeArea == projectedArea;
        boolean repeatedProjection = false;
        for (Integer count : projectedCounts.values()) {
            if (count.intValue() > 1) {
                repeatedProjection = true;
                break;
            }
        }

        KOMEDefensiveGateRecord.DimensionDetectionStatus status;
        if (!filledEnvelope) {
            status = KOMEDefensiveGateRecord.DimensionDetectionStatus.IRREGULAR;
        } else if (depthPlanes.size() != 1 || repeatedProjection) {
            status = KOMEDefensiveGateRecord.DimensionDetectionStatus.AMBIGUOUS;
        } else {
            status = KOMEDefensiveGateRecord.DimensionDetectionStatus.RELIABLE;
        }
        return new Result(true, "", snapshot.dimension, snapshot.gateUuid,
            snapshot.controllerX, snapshot.controllerY, snapshot.controllerZ,
            snapshot.structureRevision, snapshot.orientation.name(), width, height,
            projectedArea, status);
    }

    public static final class Snapshot {
        private final int dimension;
        private final int controllerX;
        private final int controllerY;
        private final int controllerZ;
        private final UUID gateUuid;
        private final int structureRevision;
        private final boolean finalized;
        private final boolean quarantined;
        private final boolean completeHingeConfiguration;
        private final GateOrientation orientation;
        private final GateOpeningDirection openingDirection;
        private final GateHinge leftHinge;
        private final GateHinge rightHinge;
        private final Collection<GatePartData> parts;
        private final boolean canonicalActive;

        public Snapshot(int dimension, int controllerX, int controllerY, int controllerZ,
                UUID gateUuid, int structureRevision, boolean finalized, boolean quarantined,
                boolean completeHingeConfiguration, GateOrientation orientation,
                GateOpeningDirection openingDirection, GateHinge leftHinge,
                GateHinge rightHinge, Collection<GatePartData> parts) {
            this(dimension, controllerX, controllerY, controllerZ, gateUuid,
                structureRevision, finalized, quarantined, completeHingeConfiguration,
                orientation, openingDirection, leftHinge, rightHinge, parts, true);
        }

        public Snapshot(int dimension, int controllerX, int controllerY, int controllerZ,
                UUID gateUuid, int structureRevision, boolean finalized, boolean quarantined,
                boolean completeHingeConfiguration, GateOrientation orientation,
                GateOpeningDirection openingDirection, GateHinge leftHinge,
                GateHinge rightHinge, Collection<GatePartData> parts,
                boolean canonicalActive) {
            this.dimension = dimension;
            this.controllerX = controllerX;
            this.controllerY = controllerY;
            this.controllerZ = controllerZ;
            this.gateUuid = gateUuid;
            this.structureRevision = structureRevision;
            this.finalized = finalized;
            this.quarantined = quarantined;
            this.completeHingeConfiguration = completeHingeConfiguration;
            this.orientation = orientation;
            this.openingDirection = openingDirection;
            this.leftHinge = leftHinge;
            this.rightHinge = rightHinge;
            this.parts = parts == null ? Collections.<GatePartData>emptyList() : parts;
            this.canonicalActive = canonicalActive;
        }
    }

    public static final class Result {
        private final boolean linkable;
        private final String diagnostic;
        private final int dimension;
        private final UUID gateUuid;
        private final int controllerX;
        private final int controllerY;
        private final int controllerZ;
        private final int structureRevision;
        private final String orientation;
        private final int detectedWidth;
        private final int detectedHeight;
        private final int projectedArea;
        private final KOMEDefensiveGateRecord.DimensionDetectionStatus status;

        private Result(boolean linkable, String diagnostic, int dimension, UUID gateUuid,
                int controllerX, int controllerY, int controllerZ, int structureRevision,
                String orientation, int detectedWidth, int detectedHeight, int projectedArea,
                KOMEDefensiveGateRecord.DimensionDetectionStatus status) {
            this.linkable = linkable;
            this.diagnostic = diagnostic == null ? "" : diagnostic;
            this.dimension = dimension;
            this.gateUuid = gateUuid;
            this.controllerX = controllerX;
            this.controllerY = controllerY;
            this.controllerZ = controllerZ;
            this.structureRevision = structureRevision;
            this.orientation = orientation == null ? "" : orientation;
            this.detectedWidth = detectedWidth;
            this.detectedHeight = detectedHeight;
            this.projectedArea = projectedArea;
            this.status = status;
        }

        private static Result invalid(String diagnostic) {
            return new Result(false, diagnostic, 0, null, 0, 0, 0, 0, "", 0, 0, 0,
                KOMEDefensiveGateRecord.DimensionDetectionStatus.INVALID);
        }

        public boolean isLinkable() { return linkable; }
        public String getDiagnostic() { return diagnostic; }
        public int getDimension() { return dimension; }
        public UUID getGateUuid() { return gateUuid; }
        public int getControllerX() { return controllerX; }
        public int getControllerY() { return controllerY; }
        public int getControllerZ() { return controllerZ; }
        public int getStructureRevision() { return structureRevision; }
        public String getOrientation() { return orientation; }
        public int getDetectedWidth() { return detectedWidth; }
        public int getDetectedHeight() { return detectedHeight; }
        public int getProjectedArea() { return projectedArea; }
        public KOMEDefensiveGateRecord.DimensionDetectionStatus getStatus() { return status; }

        void applyTo(KOMEDefensiveGateRecord record, long timestamp) {
            if (!linkable || record == null) {
                throw new IllegalArgumentException("Only a linkable inspection can populate a gate record.");
            }
            record.gateDimension = Integer.valueOf(dimension);
            record.gateUuid = gateUuid;
            record.controllerX = Integer.valueOf(controllerX);
            record.controllerY = Integer.valueOf(controllerY);
            record.controllerZ = Integer.valueOf(controllerZ);
            record.capturedStructureRevision = structureRevision;
            record.detectedOrientation = orientation;
            record.detectedWidth = detectedWidth;
            record.detectedHeight = detectedHeight;
            record.detectedProjectedArea = projectedArea;
            record.dimensionDetectionStatus = status;
            record.updatedAtMillis = Math.max(record.updatedAtMillis, timestamp);
        }
    }
}
