package com.enovak.lotrmoremobs.siege.gate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Immutable, persisted-only Phase 4A representation of a future edit commit.
 * This type deliberately has no World, controller, queue, or mutation methods.
 */
final class EditCommitJob {

    enum State {
        PREPARED,
        APPLYING_WORLD,
        PROMOTING_CONTROLLER,
        PROMOTING_OWNERSHIP,
        COMPLETE,
        CONFLICT;

        static State fromName(String name) {
            try {
                return valueOf(name);
            } catch (IllegalArgumentException ignored) {
                return null;
            } catch (NullPointerException ignored) {
                return null;
            }
        }
    }

    enum OperationKind {
        ADD,
        REMOVE;

        static OperationKind fromName(String name) {
            try {
                return valueOf(name);
            } catch (IllegalArgumentException ignored) {
                return null;
            } catch (NullPointerException ignored) {
                return null;
            }
        }
    }

    enum ProgressHint {
        PENDING,
        APPLIED,
        CONFLICT;

        static ProgressHint fromName(String name) {
            try {
                return valueOf(name);
            } catch (IllegalArgumentException ignored) {
                return null;
            } catch (NullPointerException ignored) {
                return null;
            }
        }
    }

    enum FailureCode {
        NONE,
        WORLD_STATE_MISMATCH,
        CONTROLLER_MISMATCH,
        RESERVATION_MISMATCH,
        MALFORMED_DATA,
        UNKNOWN;

        static FailureCode fromName(String name) {
            try {
                return valueOf(name);
            } catch (IllegalArgumentException ignored) {
                return null;
            } catch (NullPointerException ignored) {
                return null;
            }
        }
    }

    static final class Snapshot {
        private final UUID gateUuid;
        private final int dimension;
        private final int controllerX;
        private final int controllerY;
        private final int controllerZ;
        private final int revision;
        private final GateOrientation orientation;
        private final GateOpeningDirection openingDirection;
        private final boolean borderTextureEnabled;
        private final GateHinge leftHinge;
        private final GateHinge rightHinge;
        private final List<GatePartData> parts;

        Snapshot(UUID gateUuid, int dimension, int controllerX, int controllerY,
                int controllerZ, int revision, GateOrientation orientation,
                GateOpeningDirection openingDirection,
                boolean borderTextureEnabled, GateHinge leftHinge,
                GateHinge rightHinge, List<GatePartData> parts) {
            this.gateUuid = gateUuid;
            this.dimension = dimension;
            this.controllerX = controllerX;
            this.controllerY = controllerY;
            this.controllerZ = controllerZ;
            this.revision = revision;
            this.orientation = orientation;
            this.openingDirection = openingDirection;
            this.borderTextureEnabled = borderTextureEnabled;
            this.leftHinge = leftHinge;
            this.rightHinge = rightHinge;
            this.parts = Collections.unmodifiableList(
                    new ArrayList<GatePartData>(parts)
            );
        }

        UUID getGateUuid() { return gateUuid; }
        int getDimension() { return dimension; }
        int getControllerX() { return controllerX; }
        int getControllerY() { return controllerY; }
        int getControllerZ() { return controllerZ; }
        int getRevision() { return revision; }
        GateOrientation getOrientation() { return orientation; }
        GateOpeningDirection getOpeningDirection() { return openingDirection; }
        boolean isBorderTextureEnabled() { return borderTextureEnabled; }
        GateHinge getLeftHinge() { return leftHinge; }
        GateHinge getRightHinge() { return rightHinge; }
        List<GatePartData> getParts() { return parts; }
    }

    static final class PhysicalOperation {
        private final OperationKind kind;
        private final int ordinal;
        private final int dimension;
        private final int x;
        private final int y;
        private final int z;
        private final int relativeX;
        private final int relativeY;
        private final int relativeZ;
        private final GateLeaf finalLeaf;
        private final String expectedBeforeBlock;
        private final int expectedBeforeMetadata;
        private final String expectedAfterBlock;
        private final int expectedAfterMetadata;
        private final String sourceBlock;
        private final int sourceMetadata;
        private final boolean sourceRestorable;
        private final ProgressHint progressHint;
        private final FailureCode failureCode;
        private final String failureReason;
        private final boolean failureLogged;

        PhysicalOperation(OperationKind kind, int ordinal, int dimension, int x,
                int y, int z, int relativeX, int relativeY, int relativeZ,
                GateLeaf finalLeaf, String expectedBeforeBlock,
                int expectedBeforeMetadata, String expectedAfterBlock,
                int expectedAfterMetadata, String sourceBlock, int sourceMetadata,
                boolean sourceRestorable, ProgressHint progressHint,
                FailureCode failureCode, String failureReason,
                boolean failureLogged) {
            this.kind = kind;
            this.ordinal = ordinal;
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            this.relativeX = relativeX;
            this.relativeY = relativeY;
            this.relativeZ = relativeZ;
            this.finalLeaf = finalLeaf;
            this.expectedBeforeBlock = expectedBeforeBlock;
            this.expectedBeforeMetadata = expectedBeforeMetadata;
            this.expectedAfterBlock = expectedAfterBlock;
            this.expectedAfterMetadata = expectedAfterMetadata;
            this.sourceBlock = sourceBlock;
            this.sourceMetadata = sourceMetadata;
            this.sourceRestorable = sourceRestorable;
            this.progressHint = progressHint;
            this.failureCode = failureCode;
            this.failureReason = failureReason;
            this.failureLogged = failureLogged;
        }

        OperationKind getKind() { return kind; }
        int getOrdinal() { return ordinal; }
        int getDimension() { return dimension; }
        int getX() { return x; }
        int getY() { return y; }
        int getZ() { return z; }
        int getRelativeX() { return relativeX; }
        int getRelativeY() { return relativeY; }
        int getRelativeZ() { return relativeZ; }
        GateLeaf getFinalLeaf() { return finalLeaf; }
        String getExpectedBeforeBlock() { return expectedBeforeBlock; }
        int getExpectedBeforeMetadata() { return expectedBeforeMetadata; }
        String getExpectedAfterBlock() { return expectedAfterBlock; }
        int getExpectedAfterMetadata() { return expectedAfterMetadata; }
        String getSourceBlock() { return sourceBlock; }
        int getSourceMetadata() { return sourceMetadata; }
        boolean isSourceRestorable() { return sourceRestorable; }
        ProgressHint getProgressHint() { return progressHint; }
        FailureCode getFailureCode() { return failureCode; }
        String getFailureReason() { return failureReason; }
        boolean isFailureLogged() { return failureLogged; }
    }

    private final UUID jobUuid;
    private final UUID gateUuid;
    private final int dimension;
    private final int controllerX;
    private final int controllerY;
    private final int controllerZ;
    private final int baseRevision;
    private final int targetRevision;
    private final UUID initiatorUuid;
    private final State state;
    private final long createdTick;
    private final long updatedTick;
    private final Snapshot originalSnapshot;
    private final Snapshot targetSnapshot;
    private final List<PhysicalOperation> physicalOperations;
    private final FailureCode failureCode;
    private final int failureX;
    private final int failureY;
    private final int failureZ;

    EditCommitJob(UUID jobUuid, UUID gateUuid, int dimension, int controllerX,
            int controllerY, int controllerZ, int baseRevision,
            int targetRevision, UUID initiatorUuid, State state, long createdTick,
            long updatedTick, Snapshot originalSnapshot, Snapshot targetSnapshot,
            List<PhysicalOperation> physicalOperations, FailureCode failureCode,
            int failureX, int failureY, int failureZ) {
        this.jobUuid = jobUuid;
        this.gateUuid = gateUuid;
        this.dimension = dimension;
        this.controllerX = controllerX;
        this.controllerY = controllerY;
        this.controllerZ = controllerZ;
        this.baseRevision = baseRevision;
        this.targetRevision = targetRevision;
        this.initiatorUuid = initiatorUuid;
        this.state = state;
        this.createdTick = createdTick;
        this.updatedTick = updatedTick;
        this.originalSnapshot = originalSnapshot;
        this.targetSnapshot = targetSnapshot;
        this.physicalOperations = Collections.unmodifiableList(
                new ArrayList<PhysicalOperation>(physicalOperations)
        );
        this.failureCode = failureCode;
        this.failureX = failureX;
        this.failureY = failureY;
        this.failureZ = failureZ;
    }

    UUID getJobUuid() { return jobUuid; }
    UUID getGateUuid() { return gateUuid; }
    int getDimension() { return dimension; }
    int getControllerX() { return controllerX; }
    int getControllerY() { return controllerY; }
    int getControllerZ() { return controllerZ; }
    int getBaseRevision() { return baseRevision; }
    int getTargetRevision() { return targetRevision; }
    UUID getInitiatorUuid() { return initiatorUuid; }
    State getState() { return state; }
    long getCreatedTick() { return createdTick; }
    long getUpdatedTick() { return updatedTick; }
    Snapshot getOriginalSnapshot() { return originalSnapshot; }
    Snapshot getTargetSnapshot() { return targetSnapshot; }
    List<PhysicalOperation> getPhysicalOperations() { return physicalOperations; }
    FailureCode getFailureCode() { return failureCode; }
    int getFailureX() { return failureX; }
    int getFailureY() { return failureY; }
    int getFailureZ() { return failureZ; }

    EditCommitJob withState(State nextState, long nextUpdatedTick) {
        return new EditCommitJob(jobUuid, gateUuid, dimension, controllerX,
                controllerY, controllerZ, baseRevision, targetRevision,
                initiatorUuid, nextState, createdTick, Math.max(0L,
                        nextUpdatedTick), originalSnapshot, targetSnapshot,
                physicalOperations, failureCode, failureX, failureY, failureZ);
    }

    EditCommitJob withOperationApplied(int ordinal, long nextUpdatedTick) {
        return withOperationResult(ordinal, ProgressHint.APPLIED,
                FailureCode.NONE, null, false, state, failureCode, failureX,
                failureY, failureZ, nextUpdatedTick);
    }

    EditCommitJob withConflict(int ordinal, FailureCode nextFailureCode,
            String reason, int x, int y, int z, long nextUpdatedTick) {
        FailureCode safeCode = nextFailureCode == null
                || nextFailureCode == FailureCode.NONE
                ? FailureCode.UNKNOWN : nextFailureCode;
        return withOperationResult(ordinal, ProgressHint.CONFLICT, safeCode,
                reason, true, State.CONFLICT, safeCode, x, y, z,
                nextUpdatedTick);
    }

    EditCommitJob withJobConflict(FailureCode nextFailureCode, int x, int y,
            int z, long nextUpdatedTick) {
        FailureCode safeCode = nextFailureCode == null
                || nextFailureCode == FailureCode.NONE
                ? FailureCode.UNKNOWN : nextFailureCode;
        return new EditCommitJob(jobUuid, gateUuid, dimension, controllerX,
                controllerY, controllerZ, baseRevision, targetRevision,
                initiatorUuid, State.CONFLICT, createdTick, Math.max(0L,
                        nextUpdatedTick), originalSnapshot, targetSnapshot,
                physicalOperations, safeCode, x, y, z);
    }

    private EditCommitJob withOperationResult(int ordinal,
            ProgressHint nextProgress, FailureCode nextOperationFailure,
            String nextOperationReason, boolean nextLogged, State nextState,
            FailureCode nextJobFailure, int nextFailureX, int nextFailureY,
            int nextFailureZ, long nextUpdatedTick) {
        List<PhysicalOperation> nextOperations =
                new ArrayList<PhysicalOperation>(physicalOperations.size());
        boolean replaced = false;
        for (PhysicalOperation operation : physicalOperations) {
            if (operation.ordinal == ordinal) {
                nextOperations.add(new PhysicalOperation(operation.kind,
                        operation.ordinal, operation.dimension, operation.x,
                        operation.y, operation.z, operation.relativeX,
                        operation.relativeY, operation.relativeZ,
                        operation.finalLeaf, operation.expectedBeforeBlock,
                        operation.expectedBeforeMetadata,
                        operation.expectedAfterBlock,
                        operation.expectedAfterMetadata, operation.sourceBlock,
                        operation.sourceMetadata, operation.sourceRestorable,
                        nextProgress, nextOperationFailure,
                        nextOperationReason, nextLogged));
                replaced = true;
            } else {
                nextOperations.add(operation);
            }
        }
        if (!replaced) {
            return this;
        }
        return new EditCommitJob(jobUuid, gateUuid, dimension, controllerX,
                controllerY, controllerZ, baseRevision, targetRevision,
                initiatorUuid, nextState, createdTick, Math.max(0L,
                        nextUpdatedTick), originalSnapshot, targetSnapshot,
                nextOperations, nextJobFailure, nextFailureX, nextFailureY,
                nextFailureZ);
    }
}
