package com.enovak.lotrmoremobs.siege.gate;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

/**
 * Side-effect-free validation for a canonical sparse Siege Gate structure.
 * This class never queries or mutates a World and is safe to call before
 * block conversion, registry indexing, or persisted-data acceptance.
 */
public final class GateStructureValidator {

    public static final int MAX_GATE_PARTS = 20 * 40 * 4;
    public static final int MAX_GATE_WIDTH = 20;
    public static final int MAX_GATE_HEIGHT = 40;
    public static final int MAX_GATE_THICKNESS = 4;

    private static final int[][] FACE_OFFSETS = {
            {1, 0, 0}, {-1, 0, 0},
            {0, 1, 0}, {0, -1, 0},
            {0, 0, 1}, {0, 0, -1}
    };

    private GateStructureValidator() {
    }

    public static ValidationResult validateStructure(
            Collection<GatePartData> parts,
            int controllerX,
            int controllerY,
            int controllerZ
    ) {
        return validate(
                parts,
                null,
                null,
                null,
                null,
                controllerX,
                controllerY,
                controllerZ,
                false
        );
    }

    public static ValidationResult validateFinalized(
            Collection<GatePartData> parts,
            GateHinge leftHinge,
            GateHinge rightHinge,
            GateOrientation orientation,
            GateOpeningDirection openingDirection,
            int controllerX,
            int controllerY,
            int controllerZ
    ) {
        return validate(
                parts,
                leftHinge,
                rightHinge,
                orientation,
                openingDirection,
                controllerX,
                controllerY,
                controllerZ,
                true
        );
    }

    private static ValidationResult validate(
            Collection<GatePartData> parts,
            GateHinge leftHinge,
            GateHinge rightHinge,
            GateOrientation requestedOrientation,
            GateOpeningDirection openingDirection,
            int controllerX,
            int controllerY,
            int controllerZ,
            boolean requireConfiguration
    ) {
        if (parts == null || parts.isEmpty()) {
            return ValidationResult.failure(
                    Failure.EMPTY_STRUCTURE,
                    "A gate must contain at least one part."
            );
        }
        if (parts.size() > MAX_GATE_PARTS) {
            return ValidationResult.failure(
                    Failure.TOO_MANY_PARTS,
                    "A gate may contain at most " + MAX_GATE_PARTS + " parts."
            );
        }

        Set<PartPosition> all = new HashSet<PartPosition>();
        Set<PartPosition> left = new HashSet<PartPosition>();
        Set<PartPosition> right = new HashSet<PartPosition>();
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        boolean controllerAdjacent = false;

        for (GatePartData part : parts) {
            if (part == null) {
                return ValidationResult.failure(
                        Failure.NULL_PART,
                        "A gate part entry is missing."
                );
            }
            if (part.getLeaf() == null) {
                return ValidationResult.failure(
                        Failure.INVALID_LEAF,
                        "Every gate part must belong to LEFT, RIGHT, or SPLIT_CENTER."
                );
            }
            if (!part.hasValidAbsolutePosition(
                    controllerX,
                    controllerY,
                    controllerZ
            )) {
                return ValidationResult.failure(
                        Failure.INVALID_COORDINATE,
                        "A gate part lies outside sane world coordinates."
                );
            }

            PartPosition position = new PartPosition(
                    part.getRelativeX(),
                    part.getRelativeY(),
                    part.getRelativeZ()
            );
            if (!all.add(position)) {
                return ValidationResult.failure(
                        Failure.DUPLICATE_COORDINATE,
                        "A gate coordinate occurs more than once."
                );
            }
            if (part.getLeaf().contributesToLeft()) {
                left.add(position);
            }
            if (part.getLeaf().contributesToRight()) {
                right.add(position);
            }

            minX = Math.min(minX, part.getRelativeX());
            minY = Math.min(minY, part.getRelativeY());
            minZ = Math.min(minZ, part.getRelativeZ());
            maxX = Math.max(maxX, part.getRelativeX());
            maxY = Math.max(maxY, part.getRelativeY());
            maxZ = Math.max(maxZ, part.getRelativeZ());

            long controllerDistance = Math.abs((long)part.getRelativeX())
                    + Math.abs((long)part.getRelativeY())
                    + Math.abs((long)part.getRelativeZ());
            controllerAdjacent |= controllerDistance == 1L;
        }

        if (left.isEmpty() || right.isEmpty()) {
            return ValidationResult.failure(
                    Failure.EMPTY_LEAF,
                    "Both LEFT and RIGHT leaves need at least one part."
            );
        }

        long spanX = (long)maxX - (long)minX + 1L;
        long spanY = (long)maxY - (long)minY + 1L;
        long spanZ = (long)maxZ - (long)minZ + 1L;
        boolean validHorizontalBounds =
                (spanX <= MAX_GATE_WIDTH
                        && spanZ <= MAX_GATE_THICKNESS)
                || (spanZ <= MAX_GATE_WIDTH
                        && spanX <= MAX_GATE_THICKNESS);
        if (spanY > MAX_GATE_HEIGHT || !validHorizontalBounds) {
            return ValidationResult.failure(
                    Failure.ENVELOPE_EXCEEDED,
                    "Gate bounds must fit " + MAX_GATE_WIDTH + " wide x "
                            + MAX_GATE_HEIGHT + " high x "
                            + MAX_GATE_THICKNESS + " thick."
            );
        }
        if (!isFaceConnected(left)) {
            return ValidationResult.failure(
                    Failure.LEFT_LEAF_DISCONNECTED,
                    "The LEFT leaf is not face-connected."
            );
        }
        if (!isFaceConnected(right)) {
            return ValidationResult.failure(
                    Failure.RIGHT_LEAF_DISCONNECTED,
                    "The RIGHT leaf is not face-connected."
            );
        }
        if (!controllerAdjacent) {
            return ValidationResult.failure(
                    Failure.CONTROLLER_NOT_ADJACENT,
                    "At least one gate part must touch the controller face-to-face."
            );
        }
        if (!requireConfiguration) {
            return ValidationResult.success(null, null, null);
        }
        if (leftHinge == null || rightHinge == null) {
            return ValidationResult.failure(
                    Failure.INVALID_HINGE,
                    "Both leaves require a valid hinge."
            );
        }
        if (openingDirection == null) {
            return ValidationResult.failure(
                    Failure.INVALID_OPENING_DIRECTION,
                    "A finalized gate requires an opening direction."
            );
        }

        GateOrientation determinedOrientation =
                GateConfigurationValidator.determineOrientation(
                        parts,
                        leftHinge,
                        rightHinge
                );
        if (determinedOrientation == null
                || (requestedOrientation != null
                && requestedOrientation != determinedOrientation)) {
            return ValidationResult.failure(
                    Failure.INVALID_ORIENTATION,
                    "Hinges must identify one unambiguous pair of opposite outside gate-width edges."
            );
        }
        if (!areSplitCentersOnCenterLine(
                parts,
                determinedOrientation,
                minX,
                maxX,
                minZ,
                maxZ
        )) {
            return ValidationResult.failure(
                    Failure.INVALID_SPLIT_CENTER,
                    "Center-split blocks require the odd-width geometric center line."
            );
        }

        GateHinge configuredLeft =
                GateConfigurationValidator.configureHinge(
                        parts,
                        leftHinge,
                        determinedOrientation
                );
        GateHinge configuredRight =
                GateConfigurationValidator.configureHinge(
                        parts,
                        rightHinge,
                        determinedOrientation
                );
        if (!GateConfigurationValidator.isValid(
                parts,
                configuredLeft,
                configuredRight,
                determinedOrientation
        )) {
            return ValidationResult.failure(
                    Failure.INVALID_HINGE,
                    "The gate hinge configuration is invalid."
            );
        }
        return ValidationResult.success(
                configuredLeft,
                configuredRight,
                determinedOrientation
        );
    }

    private static boolean isFaceConnected(Set<PartPosition> positions) {
        if (positions == null || positions.isEmpty()) {
            return false;
        }
        Set<PartPosition> visited = new HashSet<PartPosition>();
        Queue<PartPosition> pending = new ArrayDeque<PartPosition>();
        PartPosition first = positions.iterator().next();
        visited.add(first);
        pending.add(first);

        while (!pending.isEmpty()) {
            PartPosition current = pending.remove();
            for (int[] offset : FACE_OFFSETS) {
                PartPosition neighbor = current.offset(
                        offset[0],
                        offset[1],
                        offset[2]
                );
                if (positions.contains(neighbor)
                        && visited.add(neighbor)) {
                    pending.add(neighbor);
                }
            }
        }
        return visited.size() == positions.size();
    }

    private static boolean areSplitCentersOnCenterLine(
            Collection<GatePartData> parts,
            GateOrientation orientation,
            int minX,
            int maxX,
            int minZ,
            int maxZ
    ) {
        int minimum = orientation == GateOrientation.WIDTH_X ? minX : minZ;
        int maximum = orientation == GateOrientation.WIDTH_X ? maxX : maxZ;
        int width = maximum - minimum + 1;
        int center = (minimum + maximum) / 2;
        for (GatePartData part : parts) {
            if (part.getLeaf().isSplitCenter()
                    && ((width & 1) == 0
                    || (orientation == GateOrientation.WIDTH_X
                    ? part.getRelativeX()
                    : part.getRelativeZ()) != center)) {
                return false;
            }
        }
        return true;
    }

    public enum Failure {
        NONE,
        EMPTY_STRUCTURE,
        TOO_MANY_PARTS,
        NULL_PART,
        INVALID_LEAF,
        INVALID_COORDINATE,
        DUPLICATE_COORDINATE,
        EMPTY_LEAF,
        ENVELOPE_EXCEEDED,
        LEFT_LEAF_DISCONNECTED,
        RIGHT_LEAF_DISCONNECTED,
        CONTROLLER_NOT_ADJACENT,
        INVALID_HINGE,
        INVALID_ORIENTATION,
        INVALID_OPENING_DIRECTION,
        INVALID_SPLIT_CENTER
    }

    public static final class ValidationResult {
        private final boolean valid;
        private final Failure failure;
        private final String message;
        private final GateHinge leftHinge;
        private final GateHinge rightHinge;
        private final GateOrientation orientation;

        private ValidationResult(
                boolean valid,
                Failure failure,
                String message,
                GateHinge leftHinge,
                GateHinge rightHinge,
                GateOrientation orientation
        ) {
            this.valid = valid;
            this.failure = failure;
            this.message = message;
            this.leftHinge = leftHinge;
            this.rightHinge = rightHinge;
            this.orientation = orientation;
        }

        public boolean isValid() {
            return valid;
        }

        public Failure getFailure() {
            return failure;
        }

        public String getMessage() {
            return message;
        }

        public GateHinge getLeftHinge() {
            return leftHinge;
        }

        public GateHinge getRightHinge() {
            return rightHinge;
        }

        public GateOrientation getOrientation() {
            return orientation;
        }

        private static ValidationResult success(
                GateHinge leftHinge,
                GateHinge rightHinge,
                GateOrientation orientation
        ) {
            return new ValidationResult(
                    true,
                    Failure.NONE,
                    null,
                    leftHinge,
                    rightHinge,
                    orientation
            );
        }

        private static ValidationResult failure(
                Failure failure,
                String message
        ) {
            return new ValidationResult(
                    false,
                    failure,
                    message,
                    null,
                    null,
                    null
            );
        }
    }

    private static final class PartPosition {
        private final int x;
        private final int y;
        private final int z;

        private PartPosition(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        private PartPosition offset(int xOffset, int yOffset, int zOffset) {
            return new PartPosition(
                    x + xOffset,
                    y + yOffset,
                    z + zOffset
            );
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof PartPosition)) {
                return false;
            }
            PartPosition position = (PartPosition)other;
            return x == position.x
                    && y == position.y
                    && z == position.z;
        }

        @Override
        public int hashCode() {
            int result = x;
            result = 31 * result + y;
            return 31 * result + z;
        }
    }
}
