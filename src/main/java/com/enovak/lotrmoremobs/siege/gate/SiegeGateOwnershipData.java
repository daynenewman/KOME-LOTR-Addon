package com.enovak.lotrmoremobs.siege.gate;

import com.enovak.lotrmoremobs.siege.SiegeRegistry;
import com.enovak.lotrmoremobs.siege.creation.GateSourceBlockValidator;
import com.enovak.lotrmoremobs.siege.edit.GateEditCommitMaterial;
import com.enovak.lotrmoremobs.siege.tile.TileEntitySiegeGate;
import cpw.mods.fml.common.FMLLog;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

/**
 * Per-dimension durable ownership and mutation authority for Siege Gates.
 *
 * <p>This data intentionally contains only serializable values. Runtime World,
 * Chunk, Block, TileEntity, player, and entity references never enter the
 * persistent records.</p>
 */
public final class SiegeGateOwnershipData extends WorldSavedData {

    public static final String DATA_NAME =
            "lotrmoremobs_siege_gate_ownership";
    public static final int FORMAT_VERSION = 2;
    private static final int LEGACY_FORMAT_VERSION = 1;
    public static final int MAX_CONTROLLER_RECORDS = 4096;
    public static final int MAX_TOTAL_OWNERSHIP_PARTS = 262144;
    public static final int MAX_ACTIVE_JOBS = 64;
    public static final int MAX_TOTAL_JOB_ENTRIES =
            MAX_ACTIVE_JOBS * GateStructureValidator.MAX_GATE_PARTS;
    public static final int MAX_EDIT_COMMIT_JOBS = 64;
    public static final int MAX_EDIT_COMMIT_OPERATIONS_PER_JOB =
            GateStructureValidator.MAX_GATE_PARTS * 2;
    public static final int MAX_TOTAL_EDIT_COMMIT_OPERATIONS =
            MAX_EDIT_COMMIT_JOBS * MAX_EDIT_COMMIT_OPERATIONS_PER_JOB;
    public static final int MAX_TARGET_RESERVATIONS =
            MAX_TOTAL_EDIT_COMMIT_OPERATIONS / 2;
    public static final int MAX_COMPLETED_EDIT_COMMIT_TOMBSTONES = 128;
    public static final int MAX_RECONCILIATION_ENTRIES_PER_TICK = 64;
    public static final int MAX_RECONCILIATION_CHUNKS_PER_TICK = 8;
    public static final int MAX_EDIT_COMMIT_OPERATIONS_PER_TICK = 32;
    public static final int MAX_EDIT_COMMIT_CHUNKS_PER_TICK = 8;
    public static final int MAX_EDIT_COMMIT_OPERATIONS_PER_JOB_PER_TICK = 8;
    public static final int MAX_EDIT_COMMIT_JOB_DISCOVERY_PER_TICK = 64;
    public static final int MAX_EDIT_COMMIT_CONTROLLER_ATTEMPTS_PER_TICK = 4;
    public static final int MAX_EDIT_COMMIT_CONTROLLER_CHUNKS_PER_TICK = 4;
    public static final int MAX_EDIT_COMMIT_ARCHIVAL_ATTEMPTS_PER_TICK = 2;
    public static final int MAX_EDIT_COMMIT_ARCHIVAL_DISCOVERY_PER_TICK = 8;
    private static final int MAX_EDIT_COMMIT_INDEX_REPAIRS_PER_TICK = 1;

    private static final int MAX_BLOCK_NAME_LENGTH = 256;
    private static final int MAX_REASON_LENGTH = 160;
    private static final int TAG_BYTE = 1;
    private static final int TAG_INT = 3;
    private static final int TAG_LONG = 4;
    private static final int TAG_STRING = 8;
    private static final int TAG_LIST = 9;
    private static final int TAG_COMPOUND = 10;

    private static final String NBT_FORMAT_VERSION = "FormatVersion";
    private static final String NBT_CONTROLLERS = "Controllers";
    private static final String NBT_JOBS = "MutationJobs";
    private static final String NBT_EDIT_COMMIT_JOBS = "EditCommitJobs";
    private static final String NBT_TARGET_RESERVATIONS = "TargetReservations";
    private static final String NBT_COMPLETED_EDIT_COMMIT_TOMBSTONES =
            "CompletedEditCommitTombstones";
    private static final String NBT_GATE_UUID = "GateUUID";
    private static final String NBT_DIMENSION = "Dimension";
    private static final String NBT_CONTROLLER_X = "ControllerX";
    private static final String NBT_CONTROLLER_Y = "ControllerY";
    private static final String NBT_CONTROLLER_Z = "ControllerZ";
    private static final String NBT_STRUCTURE_REVISION =
            "StructureRevision";
    private static final String NBT_STATUS = "Status";
    private static final String NBT_LAST_GATE_STATE = "LastGateState";
    private static final String NBT_PART_COUNT = "PartCount";
    private static final String NBT_PARTS = "Parts";
    private static final String NBT_RELATIVE_X = "RelativeX";
    private static final String NBT_RELATIVE_Y = "RelativeY";
    private static final String NBT_RELATIVE_Z = "RelativeZ";
    private static final String NBT_LEAF = "Leaf";
    private static final String NBT_SOURCE_BLOCK = "SourceBlock";
    private static final String NBT_SOURCE_META = "SourceMeta";
    private static final String NBT_SOURCE_TILE_ENTITY =
            "SourceTileEntity";
    private static final String NBT_SOURCE_RESTORABLE = "SourceRestorable";
    private static final String NBT_EXPECTED_BLOCK = "ExpectedBlock";
    private static final String NBT_EXPECTED_META = "ExpectedMeta";
    private static final String NBT_JOB_UUID = "JobUUID";
    private static final String NBT_TYPE = "Type";
    private static final String NBT_STATE = "State";
    private static final String NBT_CREATED_TICK = "CreatedTick";
    private static final String NBT_CONTROLLER_REMOVED =
            "ControllerRemoved";
    private static final String NBT_ENTRY_COUNT = "EntryCount";
    private static final String NBT_ENTRIES = "Entries";
    private static final String NBT_TARGET_X = "TargetX";
    private static final String NBT_TARGET_Y = "TargetY";
    private static final String NBT_TARGET_Z = "TargetZ";
    private static final String NBT_INTENDED_BLOCK = "IntendedBlock";
    private static final String NBT_INTENDED_META = "IntendedMeta";
    private static final String NBT_ENTRY_STATUS = "EntryStatus";
    private static final String NBT_CONFLICT_REASON = "ConflictReason";
    private static final String NBT_CONFLICT_LOGGED = "ConflictLogged";
    private static final String NBT_BASE_REVISION = "BaseRevision";
    private static final String NBT_TARGET_REVISION = "TargetRevision";
    private static final String NBT_INITIATOR_UUID = "InitiatorUUID";
    private static final String NBT_UPDATED_TICK = "UpdatedTick";
    private static final String NBT_ORIGINAL_SNAPSHOT = "OriginalSnapshot";
    private static final String NBT_TARGET_SNAPSHOT = "TargetSnapshot";
    private static final String NBT_ORIENTATION = "Orientation";
    private static final String NBT_OPENING_DIRECTION = "OpeningDirection";
    private static final String NBT_BORDER_TEXTURE_ENABLED =
            "BorderTextureEnabled";
    private static final String NBT_LEFT_HINGE = "LeftHinge";
    private static final String NBT_RIGHT_HINGE = "RightHinge";
    private static final String NBT_HINGE_SIDE = "Side";
    private static final String NBT_OPERATION_COUNT = "OperationCount";
    private static final String NBT_OPERATIONS = "PhysicalOperations";
    private static final String NBT_OPERATION_KIND = "OperationKind";
    private static final String NBT_ORDINAL = "Ordinal";
    private static final String NBT_EXPECTED_BEFORE_BLOCK = "ExpectedBeforeBlock";
    private static final String NBT_EXPECTED_BEFORE_META = "ExpectedBeforeMeta";
    private static final String NBT_EXPECTED_AFTER_BLOCK = "ExpectedAfterBlock";
    private static final String NBT_EXPECTED_AFTER_META = "ExpectedAfterMeta";
    private static final String NBT_PROGRESS_HINT = "ProgressHint";
    private static final String NBT_FAILURE_CODE = "FailureCode";
    private static final String NBT_FAILURE_X = "FailureX";
    private static final String NBT_FAILURE_Y = "FailureY";
    private static final String NBT_FAILURE_Z = "FailureZ";
    private static final String NBT_COMPLETED_TICK = "CompletedTick";

    /**
     * TEST-ONLY transient pause points for the EDIT_EXISTING crash/recovery
     * fixture. These values are never serialized and are never transaction
     * authority.
     */
    public enum EditCommitDebugPausePoint {
        NONE,
        PREPARED,
        APPLYING_WORLD,
        PHYSICAL_AFTER,
        PROMOTING_CONTROLLER,
        CONTROLLER_AFTER,
        PROMOTING_OWNERSHIP,
        COMPLETE
    }

    private final Map<UUID, ControllerRecord> controllersByUuid =
            new HashMap<UUID, ControllerRecord>();
    private final Map<BlockPosition, ControllerRecord>
            controllersByPosition =
            new HashMap<BlockPosition, ControllerRecord>();
    private final Map<BlockPosition, PartRecordRef> ownersByPart =
            new HashMap<BlockPosition, PartRecordRef>();
    private final Map<Long, List<PartRecordRef>> ownersByChunk =
            new HashMap<Long, List<PartRecordRef>>();
    private final Map<UUID, MutationJob> jobsByUuid =
            new HashMap<UUID, MutationJob>();
    private final Map<UUID, UUID> jobUuidByGateUuid =
            new HashMap<UUID, UUID>();
    private final Map<Long, List<JobEntryRef>> pendingEntriesByChunk =
            new HashMap<Long, List<JobEntryRef>>();
    private final Map<Long, Set<UUID>> controllerJobsByChunk =
            new HashMap<Long, Set<UUID>>();
    private final LinkedHashSet<Long> pendingChunkQueue =
            new LinkedHashSet<Long>();
    private final Map<UUID, EditCommitJob> editCommitJobsByUuid =
            new HashMap<UUID, EditCommitJob>();
    private final Map<UUID, UUID> activeEditCommitJobUuidByGateUuid =
            new HashMap<UUID, UUID>();
    private final Map<BlockPosition, TargetReservation>
            targetReservationsByPosition =
            new HashMap<BlockPosition, TargetReservation>();
    private final Map<UUID, Set<BlockPosition>> targetReservationPositionsByJob =
            new HashMap<UUID, Set<BlockPosition>>();
    private final List<CompletedEditCommitTombstone>
            completedEditCommitTombstones =
            new ArrayList<CompletedEditCommitTombstone>();
    private final Map<Long, LinkedHashSet<EditCommitOperationRef>>
            pendingEditCommitOperationsByChunk =
            new HashMap<Long, LinkedHashSet<EditCommitOperationRef>>();
    private final Map<UUID, Map<Integer, EditCommitOperationRef>>
            editCommitOperationRefsByJob =
            new HashMap<UUID, Map<Integer, EditCommitOperationRef>>();
    private final Map<UUID, Integer> remainingEditCommitOperationsByJob =
            new HashMap<UUID, Integer>();
    private final LinkedHashSet<Long> pendingEditCommitChunkQueue =
            new LinkedHashSet<Long>();
    private final LinkedHashSet<Long> editCommitChunkDiscoveryQueue =
            new LinkedHashSet<Long>();
    private final Set<UUID> editCommitPhysicalCompleteLogged =
            new HashSet<UUID>();
    private final Map<UUID, Integer> editCommitNextProgressPercentByJob =
            new HashMap<UUID, Integer>();
    private final Set<UUID> editCommitPhysicalVerifiedThisEpoch =
            new HashSet<UUID>();
    private final Map<Long, LinkedHashSet<UUID>>
            editCommitControllerJobsByChunk =
            new HashMap<Long, LinkedHashSet<UUID>>();
    private final Map<UUID, Long> editCommitControllerChunkByJob =
            new HashMap<UUID, Long>();
    private final LinkedHashSet<Long> pendingEditCommitControllerChunkQueue =
            new LinkedHashSet<Long>();
    private final LinkedHashSet<Long> editCommitControllerChunkDiscoveryQueue =
            new LinkedHashSet<Long>();
    private final Set<UUID> editCommitControllerWaitingLogged =
            new HashSet<UUID>();
    private final Set<UUID> editCommitControllerAfterLogged =
            new HashSet<UUID>();
    private final Set<UUID> editCommitControllerVerifiedThisEpoch =
            new HashSet<UUID>();
    private UUID debugPausedEditCommitJobUuid;
    private EditCommitDebugPausePoint debugEditCommitPausePoint =
            EditCommitDebugPausePoint.NONE;
    private final LinkedHashSet<UUID> pendingEditCommitArchivalJobUuids =
            new LinkedHashSet<UUID>();
    private final Set<UUID> blockedEditCommitArchivalJobUuids =
            new HashSet<UUID>();
    private List<UUID> editCommitArchivalDiscoverySnapshot;
    private int editCommitArchivalDiscoveryCursor;
    private boolean editCommitArchivalDiscoveryComplete;

    private int totalOwnershipParts;
    private int totalJobEntries;
    private int totalEditCommitOperations;
    private boolean readOnlyDueToInvalidData;
    private String lastLoadValidationError;
    private boolean capacityWarningLogged;
    private int boundDimension = Integer.MIN_VALUE;

    public SiegeGateOwnershipData(String name) {
        super(name);
    }

    public synchronized void setEditCommitDebugPause(
            UUID jobUuid,
            EditCommitDebugPausePoint pausePoint
    ) {
        if (jobUuid == null || pausePoint == null
                || pausePoint == EditCommitDebugPausePoint.NONE) {
            clearEditCommitDebugPause();
            return;
        }
        debugPausedEditCommitJobUuid = jobUuid;
        debugEditCommitPausePoint = pausePoint;
    }

    public synchronized void clearEditCommitDebugPause() {
        debugPausedEditCommitJobUuid = null;
        debugEditCommitPausePoint = EditCommitDebugPausePoint.NONE;
    }

    private boolean isEditCommitDebugPaused(
            EditCommitJob job,
            EditCommitDebugPausePoint pausePoint
    ) {
        return job != null
                && pausePoint != null
                && job.getJobUuid().equals(debugPausedEditCommitJobUuid)
                && debugEditCommitPausePoint == pausePoint;
    }

    public static SiegeGateOwnershipData get(World world, boolean create) {
        if (world == null || world.isRemote || world.perWorldStorage == null) {
            return null;
        }
        SiegeGateOwnershipData data =
                (SiegeGateOwnershipData)world.perWorldStorage.loadData(
                        SiegeGateOwnershipData.class,
                        DATA_NAME
                );
        if (data == null && create) {
            data = new SiegeGateOwnershipData(DATA_NAME);
            world.perWorldStorage.setData(DATA_NAME, data);
            data.markDirty();
        }
        if (data != null) {
            data.bindToDimension(world.provider.dimensionId);
        }
        return data;
    }

    private synchronized void bindToDimension(int dimension) {
        if (boundDimension == dimension) {
            return;
        }
        if (boundDimension != Integer.MIN_VALUE) {
            rejectLoadedData("one data instance was reused across dimensions");
            return;
        }
        for (ControllerRecord controller : controllersByUuid.values()) {
            if (controller.dimension != dimension) {
                rejectLoadedData("controller dimension does not match storage");
                return;
            }
        }
        for (MutationJob job : jobsByUuid.values()) {
            for (MutationEntry entry : job.entries) {
                if (entry.dimension != dimension) {
                    rejectLoadedData("job target dimension does not match storage");
                    return;
                }
            }
        }
        for (EditCommitJob job : editCommitJobsByUuid.values()) {
            if (job.getDimension() != dimension) {
                rejectLoadedData("edit-commit dimension does not match storage");
                return;
            }
        }
        for (TargetReservation reservation
                : targetReservationsByPosition.values()) {
            if (reservation.getDimension() != dimension) {
                rejectLoadedData("target reservation dimension does not match storage");
                return;
            }
        }
        for (CompletedEditCommitTombstone tombstone
                : completedEditCommitTombstones) {
            if (tombstone.getDimension() != dimension) {
                rejectLoadedData("edit-commit tombstone dimension does not match storage");
                return;
            }
        }
        boundDimension = dimension;
    }

    public synchronized boolean canAcceptController(
            int dimension,
            UUID gateUuid,
            int controllerX,
            int controllerY,
            int controllerZ,
            int structureRevision,
            Collection<GatePartData> parts
    ) {
        if (readOnlyDueToInvalidData
                || gateUuid == null
                || structureRevision <= 0
                || !isSanePosition(controllerX, controllerY, controllerZ)
                || parts == null
                || parts.isEmpty()
                || parts.size() > GateStructureValidator.MAX_GATE_PARTS) {
            return false;
        }

        ControllerRecord existing = controllersByUuid.get(gateUuid);
        ControllerRecord atPosition = controllersByPosition.get(
                new BlockPosition(controllerX, controllerY, controllerZ)
        );
        if (existing != null
                && (!existing.matchesController(
                        dimension,
                        controllerX,
                        controllerY,
                        controllerZ
                ) || hasUnresolvedJob(gateUuid))) {
            return false;
        }
        if (atPosition != null && atPosition != existing) {
            return false;
        }
        int projectedOwnershipParts = totalOwnershipParts
                - (existing == null ? 0 : existing.parts.size())
                + parts.size();
        if ((existing == null
                && controllersByUuid.size() >= MAX_CONTROLLER_RECORDS)
                || projectedOwnershipParts
                > MAX_TOTAL_OWNERSHIP_PARTS) {
            logCapacityWarning(dimension);
            return false;
        }

        Set<BlockPosition> prospectivePositions =
                new HashSet<BlockPosition>();
        for (GatePartData part : parts) {
            if (part == null || !part.hasValidAbsolutePosition(
                    controllerX,
                    controllerY,
                    controllerZ
            )) {
                return false;
            }
            BlockPosition position = new BlockPosition(
                    part.getAbsoluteX(controllerX),
                    part.getAbsoluteY(controllerY),
                    part.getAbsoluteZ(controllerZ)
            );
            if (!prospectivePositions.add(position)) {
                return false;
            }
            PartRecordRef owner = ownersByPart.get(position);
            if (owner != null && owner.controller != existing) {
                return false;
            }
        }

        return projectedOwnershipParts <= MAX_TOTAL_OWNERSHIP_PARTS;
    }

    public synchronized boolean registerOrUpdateController(
            TileEntitySiegeGate gate
    ) {
        if (gate == null
                || gate.getWorldObj() == null
                || gate.getWorldObj().isRemote
                || gate.isGateStructureQuarantined()) {
            return false;
        }

        List<GatePartData> parts = gate.getGateParts();
        GateStructureValidator.ValidationResult validation =
                gate.hasCompleteHingeConfiguration()
                ? GateStructureValidator.validateFinalized(
                        parts,
                        gate.getLeftHinge(),
                        gate.getRightHinge(),
                        gate.getGateOrientation(),
                        gate.getOpeningDirection(),
                        gate.xCoord,
                        gate.yCoord,
                        gate.zCoord
                )
                : GateStructureValidator.validateStructure(
                        parts,
                        gate.xCoord,
                        gate.yCoord,
                        gate.zCoord
                );
        if (!validation.isValid()) {
            FMLLog.warning(
                    "[SIEGE_EDIT_COMMIT][DEBUG_RELOAD] finalized validation failed "
                            + "at controller=%d,%d,%d parts=%d revision=%d",
                    gate.xCoord,
                    gate.yCoord,
                    gate.zCoord,
                    parts.size(),
                    gate.getStructureRevision()
            );
            EditCommitJob invalidControllerJob = getEditCommitJob(
                    gate.getExistingGateUuid()
            );
            if (invalidControllerJob != null) {
                conflictEditCommit(invalidControllerJob.getJobUuid(),
                        EditCommitJob.FailureCode.CONTROLLER_MISMATCH,
                        gate.xCoord, gate.yCoord, gate.zCoord,
                        gate.getWorldObj().getTotalWorldTime());
            }
            return false;
        }

        int dimension = gate.getWorldObj().provider.dimensionId;
        UUID gateUuid = gate.getGateUuid();
        int revision = gate.getStructureRevision();
        ControllerRecord existing = controllersByUuid.get(gateUuid);
        EditCommitJob activeEditJob = getEditCommitJob(gateUuid);
        if (activeEditJob != null) {
            if (existing == null
                    || !isExactEditCommitControllerReload(
                            activeEditJob, existing, gate)) {

                FMLLog.warning(
                        "[SIEGE_EDIT_COMMIT][DEBUG_RELOAD] exact reload check failed "
                                + "job=%s state=%s controllerStatus=%s "
                                + "controllerRevision=%d teRevision=%d parts=%d "
                                + "at=%d,%d,%d",
                        shortUuid(activeEditJob.getJobUuid()),
                        activeEditJob.getState(),
                        existing == null ? "MISSING" : existing.status.name(),
                        existing == null ? -1 : existing.structureRevision,
                        gate.getStructureRevision(),
                        parts.size(),
                        gate.xCoord,
                        gate.yCoord,
                        gate.zCoord
                );

                conflictEditCommit(activeEditJob.getJobUuid(),
                        EditCommitJob.FailureCode.CONTROLLER_MISMATCH,
                        gate.xCoord, gate.yCoord, gate.zCoord,
                        gate.getWorldObj().getTotalWorldTime());
                return false;
            }
            // A nonterminal edit job owns the base durable record until Phase
            // 4F. Never route its exact base/target TE through ACTIVE handling.
            return true;
        }
        if (!canAcceptController(
                dimension,
                gateUuid,
                gate.xCoord,
                gate.yCoord,
                gate.zCoord,
                revision,
                parts
        )) {
            return false;
        }

        if (existing != null) {
            if (revision < existing.structureRevision) {
                warnIntegrity(
                        "Rejected stale Siege Gate controller revision "
                                + revision + " for " + gateUuid + "."
                );
                return false;
            }
            if (revision == existing.structureRevision
                    && !existing.hasEquivalentParts(parts)) {
                existing.status = ControllerStatus.QUARANTINED;
                markDirty();
                warnIntegrity(
                        "Quarantined conflicting Siege Gate ownership "
                                + gateUuid + " at revision " + revision + "."
                );
                return false;
            }
            if (revision == existing.structureRevision) {
                boolean changed = existing.lastGateState
                        != gate.getGateState()
                        || existing.status != ControllerStatus.ACTIVE;
                existing.lastGateState = gate.getGateState();
                existing.status = ControllerStatus.ACTIVE;
                if (changed) {
                    markDirty();
                }
                return true;
            }
            removeControllerIndex(existing);
            totalOwnershipParts -= existing.parts.size();
            existing.structureRevision = revision;
            existing.lastGateState = gate.getGateState();
            existing.status = ControllerStatus.ACTIVE;
            existing.parts.clear();
            existing.parts.addAll(buildPartRecords(
                    gate.xCoord,
                    gate.yCoord,
                    gate.zCoord,
                    revision,
                    parts
            ));
            totalOwnershipParts += existing.parts.size();
            indexController(existing);
            markDirty();
            return true;
        }

        ControllerRecord created = new ControllerRecord(
                gateUuid,
                dimension,
                gate.xCoord,
                gate.yCoord,
                gate.zCoord,
                revision,
                ControllerStatus.ACTIVE,
                gate.getGateState(),
                buildPartRecords(
                        gate.xCoord,
                        gate.yCoord,
                        gate.zCoord,
                        revision,
                        parts
                )
        );
        controllersByUuid.put(gateUuid, created);
        totalOwnershipParts += created.parts.size();
        indexController(created);
        markDirty();
        return true;
    }

    public synchronized void synchronizeGateState(
            TileEntitySiegeGate gate
    ) {
        if (gate == null || gate.getWorldObj() == null) {
            return;
        }
        ControllerRecord record = controllersByUuid.get(gate.getGateUuid());
        if (record != null
                && record.matchesController(
                        gate.getWorldObj().provider.dimensionId,
                        gate.xCoord,
                        gate.yCoord,
                        gate.zCoord
                )
                && record.structureRevision == gate.getStructureRevision()
                && record.status == ControllerStatus.ACTIVE
                && record.lastGateState != gate.getGateState()) {
            record.lastGateState = gate.getGateState();
            markDirty();
        }
    }

    public synchronized void markControllerQuarantined(
            int dimension,
            UUID gateUuid,
            int controllerX,
            int controllerY,
            int controllerZ
    ) {
        ControllerRecord record = gateUuid == null
                ? null
                : controllersByUuid.get(gateUuid);
        if (record == null) {
            record = controllersByPosition.get(new BlockPosition(
                    controllerX,
                    controllerY,
                    controllerZ
            ));
        }
        if (record != null
                && record.dimension == dimension
                && record.status != ControllerStatus.QUARANTINED) {
            record.status = ControllerStatus.QUARANTINED;
            MutationJob job = getJobForGate(record.gateUuid);
            if (job != null) {
                for (MutationEntry entry : job.entries) {
                    if (entry.status == EntryStatus.PENDING) {
                        removePendingEntryIndex(job, entry);
                    }
                }
            }
            markDirty();
            warnIntegrity(
                    "Suspended durable ownership for quarantined Siege Gate "
                            + record.gateUuid + "."
            );
        }
    }

    public synchronized DurablePartOwner findPartOwner(
            int dimension,
            int x,
            int y,
            int z
    ) {
        PartRecordRef ref = ownersByPart.get(new BlockPosition(x, y, z));
        if (ref == null || ref.controller.dimension != dimension) {
            return null;
        }
        return new DurablePartOwner(ref.controller, ref.part);
    }

    public synchronized boolean hasControllerRecord(
            int dimension,
            int x,
            int y,
            int z
    ) {
        ControllerRecord record = controllersByPosition.get(
                new BlockPosition(x, y, z)
        );
        return record != null && record.dimension == dimension;
    }

    /** Phase 4A inert query only; no gameplay path creates edit jobs yet. */
    public synchronized boolean hasInertEditCommitJob(UUID gateUuid) {
        return gateUuid != null
                && activeEditCommitJobUuidByGateUuid.containsKey(gateUuid);
    }

    /**
     * The Phase 4C point-of-no-return transition. The caller has already made
     * the fresh world/preflight decision; this method deliberately rechecks
     * durable authority only and never reads or mutates World state.
     */
    public synchronized EditCommitPrepareResult prepareEditCommit(
            GateEditCommitMaterial material,
            UUID initiatorUuid,
            long createdTick
    ) {
        if (readOnlyDueToInvalidData) {
            return EditCommitPrepareResult.rejected(
                    EditCommitPrepareResult.State.READ_ONLY
            );
        }
        if (material == null || material.getGateUuid() == null
                || material.getBaseRevision() <= 0
                || material.getBaseRevision() == Integer.MAX_VALUE
                || !isSanePosition(material.getControllerX(),
                        material.getControllerY(), material.getControllerZ())
                || (boundDimension != Integer.MIN_VALUE
                && material.getDimension() != boundDimension)) {
            return EditCommitPrepareResult.rejected(
                    EditCommitPrepareResult.State.INVALID_MATERIAL
            );
        }
        int targetRevision = material.getBaseRevision() + 1;
        ControllerRecord controller = controllersByUuid.get(
                material.getGateUuid()
        );
        if (controller == null
                || controllersByPosition.get(new BlockPosition(
                        material.getControllerX(), material.getControllerY(),
                        material.getControllerZ())) != controller
                || !controller.matchesController(material.getDimension(),
                        material.getControllerX(), material.getControllerY(),
                        material.getControllerZ())
                || controller.structureRevision != material.getBaseRevision()
                || !controller.hasEquivalentParts(material.getOriginalParts())) {
            return EditCommitPrepareResult.rejected(
                    EditCommitPrepareResult.State.OWNERSHIP_CONFLICT
            );
        }
        if (controller.status != ControllerStatus.ACTIVE) {
            return EditCommitPrepareResult.rejected(
                    EditCommitPrepareResult.State.MUTATION_IN_PROGRESS
            );
        }
        if (getJobForGate(material.getGateUuid()) != null
                || getEditCommitJob(material.getGateUuid()) != null) {
            return EditCommitPrepareResult.rejected(
                    EditCommitPrepareResult.State.MUTATION_IN_PROGRESS
            );
        }
        if (editCommitJobsByUuid.size() >= MAX_EDIT_COMMIT_JOBS
                || totalEditCommitOperations
                + material.getPhysicalOperations().size()
                > MAX_TOTAL_EDIT_COMMIT_OPERATIONS) {
            return EditCommitPrepareResult.rejected(
                    EditCommitPrepareResult.State.CAPACITY_REJECTED
            );
        }

        UUID jobUuid = newUniqueEditCommitJobUuid();
        if (jobUuid == null) {
            return EditCommitPrepareResult.rejected(
                    EditCommitPrepareResult.State.INTERNAL_REJECTED
            );
        }
        EditCommitJob.Snapshot original = new EditCommitJob.Snapshot(
                material.getGateUuid(), material.getDimension(),
                material.getControllerX(), material.getControllerY(),
                material.getControllerZ(), material.getBaseRevision(),
                material.getOrientation(), material.getOriginalOpeningDirection(),
                material.isOriginalBorderTextureEnabled(),
                material.getOriginalLeftHinge(), material.getOriginalRightHinge(),
                material.getOriginalParts()
        );
        EditCommitJob.Snapshot target = new EditCommitJob.Snapshot(
                material.getGateUuid(), material.getDimension(),
                material.getControllerX(), material.getControllerY(),
                material.getControllerZ(), targetRevision,
                material.getOrientation(), material.getTargetOpeningDirection(),
                material.isTargetBorderTextureEnabled(),
                material.getTargetLeftHinge(), material.getTargetRightHinge(),
                material.getTargetParts()
        );
        String gatePartName = Block.blockRegistry.getNameForObject(
                SiegeRegistry.gatePart
        );
        if (!isBoundedBlockName(gatePartName)) {
            return EditCommitPrepareResult.rejected(
                    EditCommitPrepareResult.State.INVALID_MATERIAL
            );
        }
        List<EditCommitJob.PhysicalOperation> operations =
                new ArrayList<EditCommitJob.PhysicalOperation>(
                        material.getPhysicalOperations().size()
                );
        List<TargetReservation> reservations =
                new ArrayList<TargetReservation>();
        Set<BlockPosition> proposedAddPositions =
                new HashSet<BlockPosition>();
        int ordinal = 0;
        for (GateEditCommitMaterial.PhysicalOperation materialOperation
                : material.getPhysicalOperations()) {
            if (materialOperation == null || materialOperation.getPart() == null
                    || materialOperation.getKind() == null) {
                return EditCommitPrepareResult.rejected(
                        EditCommitPrepareResult.State.INVALID_MATERIAL
                );
            }
            GatePartData part = materialOperation.getPart();
            int x = part.getAbsoluteX(material.getControllerX());
            int y = part.getAbsoluteY(material.getControllerY());
            int z = part.getAbsoluteZ(material.getControllerZ());
            boolean add = materialOperation.getKind()
                    == GateEditCommitMaterial.PhysicalOperationKind.ADD;
            boolean restorable = part.hasStoredSourceBlock()
                    && part.getSourceBlockForRestoration() != null;
            String before = add ? part.getSourceBlockName() : gatePartName;
            int beforeMeta = add ? part.getSourceMetadata() : 0;
            String after = add ? gatePartName
                    : (restorable ? part.getSourceBlockName() : "minecraft:air");
            int afterMeta = add ? 0 : (restorable ? part.getSourceMetadata() : 0);
            operations.add(new EditCommitJob.PhysicalOperation(
                    add ? EditCommitJob.OperationKind.ADD
                            : EditCommitJob.OperationKind.REMOVE,
                    ordinal++, material.getDimension(), x, y, z,
                    part.getRelativeX(), part.getRelativeY(), part.getRelativeZ(),
                    part.getLeaf(), before, beforeMeta, after, afterMeta,
                    part.getSourceBlockName(), part.getSourceMetadata(), restorable,
                    EditCommitJob.ProgressHint.PENDING,
                    EditCommitJob.FailureCode.NONE, null, false
            ));
            if (add) {
                BlockPosition position = new BlockPosition(x, y, z);
                if (!proposedAddPositions.add(position)
                        || ownersByPart.containsKey(position)
                        || targetReservationsByPosition.containsKey(position)) {
                    return EditCommitPrepareResult.rejected(
                            targetReservationsByPosition.containsKey(position)
                            ? EditCommitPrepareResult.State.RESERVATION_CONFLICT
                            : EditCommitPrepareResult.State.OWNERSHIP_CONFLICT
                    );
                }
                reservations.add(new TargetReservation(jobUuid,
                        material.getGateUuid(), material.getBaseRevision(),
                        targetRevision, material.getDimension(), x, y, z));
            }
        }
        if (targetReservationsByPosition.size() + reservations.size()
                > MAX_TARGET_RESERVATIONS) {
            return EditCommitPrepareResult.rejected(
                    EditCommitPrepareResult.State.CAPACITY_REJECTED
            );
        }
        EditCommitJob job = new EditCommitJob(jobUuid, material.getGateUuid(),
                material.getDimension(), material.getControllerX(),
                material.getControllerY(), material.getControllerZ(),
                material.getBaseRevision(), targetRevision, initiatorUuid,
                EditCommitJob.State.PREPARED, Math.max(0L, createdTick),
                Math.max(0L, createdTick), original, target, operations,
                EditCommitJob.FailureCode.NONE, 0, 0, 0);
        if (!isValidPreparedEditCommitJob(job)
                || reservations.size() != countAddOperations(job)) {
            return EditCommitPrepareResult.rejected(
                    EditCommitPrepareResult.State.INVALID_MATERIAL
            );
        }

        // Every failure condition above was checked before touching live maps.
        editCommitJobsByUuid.put(jobUuid, job);
        activeEditCommitJobUuidByGateUuid.put(material.getGateUuid(), jobUuid);
        totalEditCommitOperations += operations.size();
        indexEditCommitOperations(job);
        Set<BlockPosition> reservationPositions =
                new HashSet<BlockPosition>();
        for (TargetReservation reservation : reservations) {
            BlockPosition position = new BlockPosition(reservation.getX(),
                    reservation.getY(), reservation.getZ());
            targetReservationsByPosition.put(position, reservation);
            reservationPositions.add(position);
        }
        if (!reservationPositions.isEmpty()) {
            targetReservationPositionsByJob.put(jobUuid, reservationPositions);
        }
        controller.status = ControllerStatus.MUTATING;
        markDirty();
        FMLLog.info("[SIEGE_EDIT_COMMIT][PREPARED] job=%s gate=%s %d->%d "
                        + "parts=%d->%d add=%d remove=%d",
                shortUuid(jobUuid), shortUuid(material.getGateUuid()),
                material.getBaseRevision(), targetRevision,
                material.getOriginalParts().size(), material.getTargetParts().size(),
                reservations.size(), operations.size() - reservations.size());
        return EditCommitPrepareResult.prepared(jobUuid, material.getGateUuid(),
                material.getBaseRevision(), targetRevision);
    }

    /**
     * Read-only Phase 4B classifier for durable structural mutation authority.
     * This deliberately reads only ownership-data indexes: it never touches a
     * world, chunk, controller tile entity, or transaction state.
     */
    public synchronized GateMutationState getGateMutationState(
            UUID gateUuid,
            int dimension,
            int controllerX,
            int controllerY,
            int controllerZ
    ) {
        if (readOnlyDueToInvalidData) {
            return GateMutationState.CORRUPT;
        }
        if (gateUuid == null) {
            // A missing identity is validated by the caller's ownership path;
            // it is not durable evidence that a transaction is active.
            return GateMutationState.NONE;
        }
        if (!isSanePosition(controllerX, controllerY, controllerZ)) {
            return GateMutationState.INCONSISTENT;
        }

        ControllerRecord record = controllersByUuid.get(gateUuid);
        if (record == null) {
            // Missing ownership remains the caller's existing ownership problem;
            // it is not itself evidence of an in-progress mutation.
            return GateMutationState.NONE;
        }
        if (!record.matchesController(
                dimension, controllerX, controllerY, controllerZ
        )) {
            return GateMutationState.INCONSISTENT;
        }

        MutationJob legacyJob = getJobForGate(gateUuid);
        EditCommitJob editJob = getEditCommitJob(gateUuid);
        if (legacyJob != null && editJob != null) {
            return GateMutationState.INCONSISTENT;
        }
        if (legacyJob != null) {
            if ((legacyJob.type != TransactionType.DISMANTLE_RESTORE
                    && legacyJob.type != TransactionType.CONTROLLER_REMOVAL)
                    || legacyJob.state == TransactionState.COMPLETE
                    || legacyJob.structureRevision != record.structureRevision
                    || (record.status != ControllerStatus.MUTATING
                    && record.status != ControllerStatus.TOMBSTONED)) {
                return GateMutationState.INCONSISTENT;
            }
            return GateMutationState.LEGACY_REMOVAL;
        }
        if (editJob != null) {
            if (!isEditCommitControllerConsistent(editJob)) {
                return GateMutationState.INCONSISTENT;
            }
            switch (editJob.getState()) {
                case PREPARED:
                    return GateMutationState.EDIT_COMMIT_PREPARED;
                case APPLYING_WORLD:
                    return GateMutationState.EDIT_COMMIT_APPLYING_WORLD;
                case PROMOTING_CONTROLLER:
                    return GateMutationState.EDIT_COMMIT_PROMOTING_CONTROLLER;
                case PROMOTING_OWNERSHIP:
                    return GateMutationState.EDIT_COMMIT_PROMOTING_OWNERSHIP;
                case CONFLICT:
                    return GateMutationState.EDIT_COMMIT_CONFLICT;
                case COMPLETE:
                default:
                    return GateMutationState.INCONSISTENT;
            }
        }
        if (record.status == ControllerStatus.QUARANTINED) {
            return GateMutationState.QUARANTINED;
        }
        return record.status == ControllerStatus.ACTIVE
                ? GateMutationState.NONE
                : GateMutationState.INCONSISTENT;
    }

    /** Read-only convenience predicate for gameplay mutation admission. */
    public synchronized boolean isGateMutationLocked(
            UUID gateUuid,
            int dimension,
            int controllerX,
            int controllerY,
            int controllerZ
    ) {
        return getGateMutationState(
                gateUuid, dimension, controllerX, controllerY, controllerZ
        ) != GateMutationState.NONE;
    }

    /** Phase 4A inert query only. */
    public synchronized int getEditCommitJobCount() {
        return editCommitJobsByUuid.size();
    }

    /** Phase 4A inert query only. */
    synchronized EditCommitJob getEditCommitJobByJobUuid(UUID jobUuid) {
        return jobUuid == null ? null : editCommitJobsByUuid.get(jobUuid);
    }

    /** Phase 4A inert query only. */
    synchronized EditCommitJob getEditCommitJob(UUID gateUuid) {
        UUID jobUuid = gateUuid == null
                ? null : activeEditCommitJobUuidByGateUuid.get(gateUuid);
        return jobUuid == null ? null : editCommitJobsByUuid.get(jobUuid);
    }

    /** Phase 4A inert query only. */
    synchronized TargetReservation getTargetReservation(
            int dimension, int x, int y, int z
    ) {
        TargetReservation reservation = targetReservationsByPosition.get(
                new BlockPosition(x, y, z)
        );
        return reservation != null && reservation.getDimension() == dimension
                ? reservation : null;
    }

    /**
     * Side-effect-free finalized-controller consistency check for inspection.
     * It intentionally refuses every non-ACTIVE or unresolved ownership state.
     */
    public synchronized boolean matchesActiveController(
            TileEntitySiegeGate gate
    ) {
        if (readOnlyDueToInvalidData
                || gate == null
                || gate.getWorldObj() == null
                || gate.getWorldObj().isRemote
                || gate.isGateStructureQuarantined()
                || !gate.isFinalized()
                || gate.getStructureRevision() <= 0) {
            return false;
        }
        UUID gateUuid = gate.getExistingGateUuid();
        if (gateUuid == null) {
            return false;
        }
        ControllerRecord record = controllersByUuid.get(gateUuid);
        return record != null
                && record.status == ControllerStatus.ACTIVE
                && record.matchesController(
                        gate.getWorldObj().provider.dimensionId,
                        gate.xCoord,
                        gate.yCoord,
                        gate.zCoord
                )
                && record.structureRevision == gate.getStructureRevision()
                && !hasUnresolvedJob(gateUuid)
                && record.hasEquivalentParts(gate.getGateParts());
    }

    /**
     * Read-only Phase-3 diagnostic version of matchesActiveController. It does
     * not register, recover, mark dirty, or change durable state.
     */
    public synchronized ActiveControllerCheck evaluateActiveController(
            TileEntitySiegeGate gate,
            UUID expectedGateUuid,
            int expectedRevision,
            Collection<GatePartData> expectedParts
    ) {
        if (readOnlyDueToInvalidData) return ActiveControllerCheck.INVALID_DATA;
        if (gate == null || gate.getWorldObj() == null || gate.getWorldObj().isRemote
                || expectedGateUuid == null || expectedRevision <= 0) return ActiveControllerCheck.MISSING;
        UUID actualUuid = gate.getExistingGateUuid();
        if (actualUuid == null || !expectedGateUuid.equals(actualUuid)) return ActiveControllerCheck.UUID_MISMATCH;
        ControllerRecord record = controllersByUuid.get(expectedGateUuid);
        if (record == null) return ActiveControllerCheck.MISSING;
        if (!record.matchesController(gate.getWorldObj().provider.dimensionId, gate.xCoord, gate.yCoord, gate.zCoord)) return ActiveControllerCheck.CONTROLLER_MISMATCH;
        if (record.status == ControllerStatus.QUARANTINED || gate.isGateStructureQuarantined()) return ActiveControllerCheck.QUARANTINED;
        if (hasUnresolvedJob(expectedGateUuid) || record.status != ControllerStatus.ACTIVE) return ActiveControllerCheck.MUTATION_IN_PROGRESS;
        if (gate.getStructureRevision() != expectedRevision || record.structureRevision != expectedRevision) return ActiveControllerCheck.STALE_REVISION;
        if (!record.hasEquivalentParts(gate.getGateParts()) || !record.hasEquivalentParts(expectedParts)) return ActiveControllerCheck.PARTS_MISMATCH;
        return ActiveControllerCheck.ACTIVE;
    }

    /**
     * Read-only expected-base check for a pending REMOVE. The caller must not
     * invoke it until the physical chunk is naturally loaded.
     */
    public synchronized ExpectedBasePartCheck checkExpectedBasePart(
            World world, UUID expectedGateUuid, int expectedControllerX,
            int expectedControllerY, int expectedControllerZ, int expectedRevision,
            int x, int y, int z
    ) {
        if (world == null || world.isRemote || expectedGateUuid == null
                || !world.getChunkProvider().chunkExists(x >> 4, z >> 4)) return ExpectedBasePartCheck.OWNERSHIP_MISMATCH;
        PartRecordRef ref = ownersByPart.get(new BlockPosition(x, y, z));
        if (ref == null) return ExpectedBasePartCheck.OWNERSHIP_MISMATCH;
        if (!expectedGateUuid.equals(ref.controller.gateUuid)) return ExpectedBasePartCheck.FOREIGN_OWNER;
        if (!ref.controller.matchesController(world.provider.dimensionId, expectedControllerX, expectedControllerY, expectedControllerZ)
                || ref.controller.status != ControllerStatus.ACTIVE
                || ref.part.structureRevision != expectedRevision
                || ref.controller.structureRevision != expectedRevision
                || hasUnresolvedJob(expectedGateUuid)) return ExpectedBasePartCheck.OWNERSHIP_MISMATCH;
        Block current = world.getBlock(x, y, z);
        String currentName = GateSourceBlockValidator.getRegisteredName(current);
        return ref.part.expectedBlockName.equals(currentName)
                && ref.part.expectedMetadata == world.getBlockMetadata(x, y, z)
                ? ExpectedBasePartCheck.MATCH : ExpectedBasePartCheck.TARGET_CHANGED;
    }

    public synchronized boolean canBeginRemoval(
            TileEntitySiegeGate gate
    ) {
        if (gate == null
                || gate.getWorldObj() == null
                || gate.getWorldObj().isRemote
                || gate.isGateStructureQuarantined()
                || readOnlyDueToInvalidData) {
            return false;
        }
        UUID gateUuid = gate.getGateUuid();
        MutationJob existingJob = getJobForGate(gateUuid);
        if (existingJob != null) {
            return existingJob.structureRevision
                    == gate.getStructureRevision();
        }
        if (isGateMutationLocked(gateUuid,
                gate.getWorldObj().provider.dimensionId,
                gate.xCoord, gate.yCoord, gate.zCoord)) {
            return false;
        }
        ControllerRecord record = controllersByUuid.get(gateUuid);
        if (record == null
                || record.status != ControllerStatus.ACTIVE
                || record.structureRevision != gate.getStructureRevision()) {
            return false;
        }
        if (jobsByUuid.size() >= MAX_ACTIVE_JOBS
                || totalJobEntries + record.parts.size()
                > MAX_TOTAL_JOB_ENTRIES) {
            logCapacityWarning(record.dimension);
            return false;
        }
        return true;
    }

    public synchronized boolean prepareRemoval(
            TileEntitySiegeGate gate,
            TransactionType type
    ) {
        if (type != TransactionType.DISMANTLE_RESTORE
                && type != TransactionType.CONTROLLER_REMOVAL) {
            return false;
        }

        MutationJob existingJob =
                gate == null
                        ? null
                        : getJobForGate(
                        gate.getGateUuid()
                );

        if (existingJob != null) {
            return existingJob.structureRevision
                    == gate.getStructureRevision();
        }

        if (gate == null
                || gate.getWorldObj() == null
                || isGateMutationLocked(
                gate.getExistingGateUuid(),
                gate.getWorldObj()
                        .provider.dimensionId,
                gate.xCoord,
                gate.yCoord,
                gate.zCoord
        )) {
            return false;
        }

        if (!registerOrUpdateController(gate)
                || !canBeginRemoval(gate)) {
            return false;
        }

        UUID gateUuid =
                gate.getGateUuid();

        ControllerRecord record =
                controllersByUuid.get(gateUuid);

        if (record == null) {
            return false;
        }

        String gatePartName =
                Block.blockRegistry.getNameForObject(
                        SiegeRegistry.gatePart
                );

        if (!isBoundedBlockName(gatePartName)) {
            return false;
        }

        List<GatePartData> liveParts =
                gate.getGateParts();

        if (liveParts.size()
                != record.parts.size()) {
            return false;
        }

        List<MutationEntry> entries =
                new ArrayList<MutationEntry>(
                        liveParts.size()
                );

        for (GatePartData part : liveParts) {
            boolean restorable =
                    part.hasStoredSourceBlock()
                            && part
                            .getSourceBlockForRestoration()
                            != null;

            String intendedBlock =
                    restorable
                            ? part.getSourceBlockName()
                            : "minecraft:air";

            int intendedMeta =
                    restorable
                            ? part.getSourceMetadata()
                            : 0;

            entries.add(
                    new MutationEntry(
                            record.dimension,
                            part.getAbsoluteX(
                                    gate.xCoord
                            ),
                            part.getAbsoluteY(
                                    gate.yCoord
                            ),
                            part.getAbsoluteZ(
                                    gate.zCoord
                            ),
                            gatePartName,
                            0,
                            intendedBlock,
                            intendedMeta,
                            part.getSourceBlockName(),
                            part.getSourceMetadata(),
                            part.getSourceTileEntityNbt(),
                            restorable,
                            record.structureRevision,
                            EntryStatus.PENDING,
                            null,
                            false
                    )
            );
        }

        MutationJob job =
                new MutationJob(
                        UUID.randomUUID(),
                        gateUuid,
                        record.structureRevision,
                        type,
                        TransactionState.PREPARED,
                        gate.getWorldObj()
                                .getTotalWorldTime(),
                        false,
                        entries
                );

        record.status =
                ControllerStatus.MUTATING;

        jobsByUuid.put(
                job.jobUuid,
                job
        );

        jobUuidByGateUuid.put(
                job.gateUuid,
                job.jobUuid
        );

        totalJobEntries +=
                entries.size();

        indexJob(job);
        markDirty();

        return true;
    }

    public synchronized void activateRemoval(
            World world,
            UUID gateUuid,
            int structureRevision
    ) {
        MutationJob job = getJobForGate(gateUuid);
        if (world == null
                || job == null
                || job.structureRevision != structureRevision) {
            return;
        }
        if (job.state == TransactionState.PREPARED) {
            job.state = TransactionState.APPLYING;
            markDirty();
        }
        ControllerRecord controller = controllersByUuid.get(gateUuid);
        if (controller != null) {
            enqueueChunk(chunkKey(
                    controller.controllerX >> 4,
                    controller.controllerZ >> 4
            ));
        }
        confirmControllerRemovalIfPresent(world, job);
    }

    public synchronized void abortPreparedRemoval(
            UUID gateUuid,
            int structureRevision
    ) {
        MutationJob job = getJobForGate(gateUuid);
        if (job == null
                || job.structureRevision != structureRevision
                || job.controllerRemoved
                || !job.allEntriesPending()) {
            return;
        }
        ControllerRecord record = controllersByUuid.get(gateUuid);
        removeJob(job);
        if (record != null
                && record.status == ControllerStatus.MUTATING) {
            record.status = ControllerStatus.ACTIVE;
        }
        markDirty();
    }

    public synchronized void recoverLoadedController(
            World world,
            UUID gateUuid,
            int structureRevision,
            int controllerX,
            int controllerY,
            int controllerZ
    ) {
        MutationJob job = getJobForGate(gateUuid);
        if (world == null
                || job == null
                || job.structureRevision != structureRevision
                || job.controllerRemoved
                || !job.allEntriesPending()
                || !world.blockExists(controllerX, controllerY, controllerZ)
                || world.getBlock(controllerX, controllerY, controllerZ)
                != SiegeRegistry.gateController) {
            return;
        }
        abortPreparedRemoval(gateUuid, structureRevision);
    }

    public synchronized void quarantineMissingController(
            int dimension,
            UUID gateUuid,
            int controllerX,
            int controllerY,
            int controllerZ
    ) {
        markControllerQuarantined(
                dimension,
                gateUuid,
                controllerX,
                controllerY,
                controllerZ
        );
    }

    public synchronized void onChunkLoaded(int chunkX, int chunkZ) {
        long key = chunkKey(chunkX, chunkZ);
        if (hasPendingWorkForChunk(key)) {
            enqueueChunk(key);
        }
        if (pendingEditCommitOperationsByChunk.containsKey(key)) {
            enqueueEditCommitChunk(key);
        }
        if (editCommitControllerJobsByChunk.containsKey(key)) {
            enqueueEditCommitControllerChunk(key);
        }
    }

    public synchronized void processReconciliation(World world) {
        if (world == null || world.isRemote || readOnlyDueToInvalidData) {
            return;
        }
        int entryBudget = MAX_RECONCILIATION_ENTRIES_PER_TICK;
        int chunkBudget = MAX_RECONCILIATION_CHUNKS_PER_TICK;
        while (chunkBudget-- > 0
                && entryBudget > 0
                && !pendingChunkQueue.isEmpty()) {
            Iterator<Long> iterator = pendingChunkQueue.iterator();
            long key = iterator.next().longValue();
            iterator.remove();
            entryBudget -= reconcileChunk(world, key, entryBudget);
            if (hasPendingWorkForChunk(key)
                    && isChunkLoaded(world, key)) {
                pendingChunkQueue.add(Long.valueOf(key));
            }
        }
    }

    public synchronized void clearTransientQueue() {
        pendingChunkQueue.clear();
        clearTransientEditCommitScheduling();
    }

    /**
     * Phase 4D sibling reconciler. EditCommit jobs deliberately remain outside
     * the legacy MutationJob queue and are driven only from loaded chunks.
     */
    public synchronized void processEditCommitReconciliation(World world) {
        if (world == null || world.isRemote || readOnlyDueToInvalidData) {
            return;
        }
        repairEditCommitSchedulingIndexes();
        processPreparedEditCommitJobs(world.getTotalWorldTime());

        int operationBudget = MAX_EDIT_COMMIT_OPERATIONS_PER_TICK;
        int chunkBudget = MAX_EDIT_COMMIT_CHUNKS_PER_TICK;
        Map<UUID, Integer> perJobCounts =
                new HashMap<UUID, Integer>();
        boolean[] progressDirty = new boolean[] {false};
        while (operationBudget > 0 && chunkBudget-- > 0
                && !pendingEditCommitChunkQueue.isEmpty()) {
            Iterator<Long> iterator = pendingEditCommitChunkQueue.iterator();
            long key = iterator.next().longValue();
            iterator.remove();
            if (!isChunkLoaded(world, key)) {
                continue;
            }
            operationBudget -= processEditCommitChunk(world, key,
                    operationBudget, perJobCounts, progressDirty);
            if (hasPendingEditCommitWorkForChunk(key)) {
                enqueueEditCommitChunk(key);
            }
        }
        if (progressDirty[0]) {
            markDirty();
        }
        discoverLoadedEditCommitChunks(world, chunkBudget);
        advanceReadyEditCommitControllerPromotions(
                world.getTotalWorldTime()
        );
        processEditCommitControllerPromotions(world);
        discoverLoadedEditCommitControllerChunks(world,
                MAX_EDIT_COMMIT_CONTROLLER_CHUNKS_PER_TICK);
        processEditCommitOwnershipPromotions(world.getTotalWorldTime());
        discoverEditCommitArchivalJobs();
        processEditCommitArchival(world.getTotalWorldTime());
    }

    @Override
    public synchronized void readFromNBT(NBTTagCompound nbt) {
        clearAllRecords();
        lastLoadValidationError = null;
        if (!nbt.hasKey(NBT_FORMAT_VERSION, TAG_INT)) {
            rejectLoadedData("unsupported or missing FormatVersion");
            return;
        }
        int loadedFormatVersion = nbt.getInteger(NBT_FORMAT_VERSION);
        if (loadedFormatVersion != LEGACY_FORMAT_VERSION
                && loadedFormatVersion != FORMAT_VERSION) {
            rejectLoadedData("unsupported FormatVersion");
            return;
        }
        if (!nbt.hasKey(NBT_CONTROLLERS, TAG_LIST)
                || !nbt.hasKey(NBT_JOBS, TAG_LIST)) {
            rejectLoadedData("missing Controllers or MutationJobs list");
            return;
        }

        NBTTagList controllerList =
                (NBTTagList)nbt.getTag(NBT_CONTROLLERS);
        if ((controllerList.tagCount() > 0
                && controllerList.func_150303_d() != TAG_COMPOUND)
                || controllerList.tagCount() > MAX_CONTROLLER_RECORDS) {
            rejectLoadedData("controller list exceeds its hard cap");
            return;
        }
        for (int i = 0; i < controllerList.tagCount(); ++i) {
            ControllerRecord record = readControllerRecord(
                    controllerList.getCompoundTagAt(i)
            );
            if (record == null) {
                rejectLoadedData(
                        "invalid controller record at index "
                                + i
                                + loadValidationSuffix()
                );
                return;
            }
            if (controllersByUuid.containsKey(record.gateUuid)) {
                rejectLoadedData(
                        "duplicate controller UUID at index "
                                + i
                                + ": "
                                + record.gateUuid
                );
                return;
            }
            if (controllersByPosition.containsKey(
                    record.controllerPosition()
            )) {
                rejectLoadedData(
                        "duplicate controller position at index "
                                + i
                                + ": "
                                + record.controllerX
                                + ","
                                + record.controllerY
                                + ","
                                + record.controllerZ
                );
                return;
            }
            if (totalOwnershipParts + record.parts.size()
                    > MAX_TOTAL_OWNERSHIP_PARTS) {
                rejectLoadedData(
                        "controller ownership-part capacity exceeded at index "
                                + i
                );
                return;
            }
            controllersByUuid.put(record.gateUuid, record);
            totalOwnershipParts += record.parts.size();
            if (!indexController(record)) {
                rejectLoadedData("overlapping part ownership records");
                return;
            }
        }

        NBTTagList jobList = (NBTTagList)nbt.getTag(NBT_JOBS);
        if ((jobList.tagCount() > 0
                && jobList.func_150303_d() != TAG_COMPOUND)
                || jobList.tagCount() > MAX_ACTIVE_JOBS) {
            rejectLoadedData("mutation job list exceeds its hard cap");
            return;
        }
        boolean normalizedJobs = false;
        for (int i = 0; i < jobList.tagCount(); ++i) {
            MutationJob job = readMutationJob(jobList.getCompoundTagAt(i));
            if (job == null
                    || jobsByUuid.containsKey(job.jobUuid)
                    || jobUuidByGateUuid.containsKey(job.gateUuid)
                    || totalJobEntries + job.entries.size()
                    > MAX_TOTAL_JOB_ENTRIES) {
                rejectLoadedData("invalid or duplicate mutation job");
                return;
            }
            ControllerRecord controller =
                    controllersByUuid.get(job.gateUuid);
            if (controller == null
                    || controller.structureRevision
                    != job.structureRevision
                    || !jobMatchesController(job, controller)
                    || (job.type != TransactionType.DISMANTLE_RESTORE
                    && job.type != TransactionType.CONTROLLER_REMOVAL)) {
                boolean newlyConflicted = job.hasPendingEntries();
                job.state = TransactionState.CONFLICT;
                job.markPendingEntriesConflict(
                        "STALE_OR_MISSING_CONTROLLER_REVISION"
                );
                normalizedJobs |= newlyConflicted;
                if (newlyConflicted) {
                    FMLLog.warning(
                            "[LOTRMoreMobs] Retained Siege Gate job %s as "
                                    + "CONFLICT because its controller/revision "
                                    + "record is stale, missing, or unsupported; "
                                    + "no world blocks were changed.",
                            job.jobUuid
                    );
                }
            }
            jobsByUuid.put(job.jobUuid, job);
            jobUuidByGateUuid.put(job.gateUuid, job.jobUuid);
            totalJobEntries += job.entries.size();
            indexJob(job);
        }

        if (loadedFormatVersion == FORMAT_VERSION) {
            if (!readV2EditCommitData(nbt)) {
                rejectLoadedData("invalid V2 edit-commit data");
                return;
            }
        }
        readOnlyDueToInvalidData = false;
        for (MutationJob job
                : new ArrayList<MutationJob>(jobsByUuid.values())) {
            refreshJobState(job);
        }
        if (normalizedJobs) {
            markDirty();
        }
        if (loadedFormatVersion == LEGACY_FORMAT_VERSION) {
            // Successful migration is mechanical only: no World/controller access.
            FMLLog.info("[LOTRMoreMobs] Migrated Siege Gate ownership data "
                    + "from FormatVersion 1 to 2 with no edit-commit jobs.");
            markDirty();
        }
    }

    @Override
    public synchronized void writeToNBT(NBTTagCompound nbt) {
        nbt.setInteger(NBT_FORMAT_VERSION, FORMAT_VERSION);

        NBTTagList controllerList = new NBTTagList();
        int writtenControllers = 0;
        int writtenParts = 0;
        List<ControllerRecord> sortedControllers =
                new ArrayList<ControllerRecord>(controllersByUuid.values());
        Collections.sort(sortedControllers, CONTROLLER_RECORD_COMPARATOR);
        for (ControllerRecord record : sortedControllers) {
            if (record.parts.isEmpty()
                    || record.parts.size()
                    > GateStructureValidator.MAX_GATE_PARTS
                    || ++writtenControllers > MAX_CONTROLLER_RECORDS
                    || writtenParts + record.parts.size()
                    > MAX_TOTAL_OWNERSHIP_PARTS) {
                throw new IllegalStateException(
                        "Siege Gate ownership output exceeds hard caps"
                );
            }
            writtenParts += record.parts.size();
            controllerList.appendTag(writeControllerRecord(record));
        }
        nbt.setTag(NBT_CONTROLLERS, controllerList);

        NBTTagList jobList = new NBTTagList();
        int writtenJobs = 0;
        int writtenEntries = 0;
        List<MutationJob> sortedMutationJobs =
                new ArrayList<MutationJob>(jobsByUuid.values());
        Collections.sort(sortedMutationJobs, MUTATION_JOB_COMPARATOR);
        for (MutationJob job : sortedMutationJobs) {
            if (job.entries.isEmpty()
                    || job.entries.size()
                    > GateStructureValidator.MAX_GATE_PARTS
                    || ++writtenJobs > MAX_ACTIVE_JOBS
                    || writtenEntries + job.entries.size()
                    > MAX_TOTAL_JOB_ENTRIES) {
                throw new IllegalStateException(
                        "Siege Gate mutation output exceeds hard caps"
                );
            }
            writtenEntries += job.entries.size();
            jobList.appendTag(writeMutationJob(job));
        }
        nbt.setTag(NBT_JOBS, jobList);
        writeV2EditCommitData(nbt);
    }

    /**
     * Parses inert V2 edit data only. It performs no World/controller access,
     * queues no work, and does not alter ControllerRecord status.
     */
    private boolean readV2EditCommitData(NBTTagCompound root) {
        if (!root.hasKey(NBT_EDIT_COMMIT_JOBS, TAG_LIST)
                || !root.hasKey(NBT_TARGET_RESERVATIONS, TAG_LIST)
                || !root.hasKey(NBT_COMPLETED_EDIT_COMMIT_TOMBSTONES, TAG_LIST)) {
            return false;
        }
        NBTTagList jobs = (NBTTagList)root.getTag(NBT_EDIT_COMMIT_JOBS);
        NBTTagList reservations = (NBTTagList)root.getTag(NBT_TARGET_RESERVATIONS);
        NBTTagList tombstones = (NBTTagList)root.getTag(
                NBT_COMPLETED_EDIT_COMMIT_TOMBSTONES
        );
        if (!isCompoundListWithin(jobs, MAX_EDIT_COMMIT_JOBS)
                || !isCompoundListWithin(reservations, MAX_TARGET_RESERVATIONS)
                || !isCompoundListWithin(
                        tombstones,
                        MAX_COMPLETED_EDIT_COMMIT_TOMBSTONES
                )) {
            return false;
        }
        for (int i = 0; i < jobs.tagCount(); ++i) {
            EditCommitJob job = readEditCommitJob(jobs.getCompoundTagAt(i));
            if (job == null || !indexEditCommitJob(job)) {
                FMLLog.warning("[LOTRMoreMobs] Rejected malformed or duplicate "
                        + "inert Siege Gate EditCommitJob while loading V2 data.");
                return false;
            }
        }
        for (int i = 0; i < reservations.tagCount(); ++i) {
            TargetReservation reservation = readTargetReservation(
                    reservations.getCompoundTagAt(i)
            );
            if (reservation == null || !indexTargetReservation(reservation)) {
                FMLLog.warning("[LOTRMoreMobs] Rejected malformed or duplicate "
                        + "Siege Gate target reservation while loading V2 data.");
                return false;
            }
        }
        for (int i = 0; i < tombstones.tagCount(); ++i) {
            CompletedEditCommitTombstone tombstone = readCompletedTombstone(
                    tombstones.getCompoundTagAt(i)
            );
            if (tombstone == null
                    || containsTombstoneJobUuid(tombstone.getJobUuid())
                    || editCommitJobsByUuid.containsKey(
                            tombstone.getJobUuid())) {
                return false;
            }
            completedEditCommitTombstones.add(tombstone);
        }
        if (!normalizeLoadedEditCommitMutationStatus()) {
            return false;
        }
        boolean consistent = validateV2EditCommitConsistency();
        if (!consistent) {
            FMLLog.warning("[LOTRMoreMobs] Rejected inconsistent inert Siege "
                    + "Gate EditCommitJob/reservation V2 data.");
            return false;
        }
        return true;
    }

    private void writeV2EditCommitData(NBTTagCompound root) {
        NBTTagList jobs = new NBTTagList();
        List<EditCommitJob> sortedJobs =
                new ArrayList<EditCommitJob>(editCommitJobsByUuid.values());
        Collections.sort(sortedJobs, EDIT_COMMIT_JOB_COMPARATOR);
        if (sortedJobs.size() > MAX_EDIT_COMMIT_JOBS) {
            throw new IllegalStateException("Siege Gate edit-commit output exceeds hard caps");
        }
        int writtenOperations = 0;
        for (EditCommitJob job : sortedJobs) {
            writtenOperations += job.getPhysicalOperations().size();
            if (job.getPhysicalOperations().size()
                    > MAX_EDIT_COMMIT_OPERATIONS_PER_JOB
                    || writtenOperations > MAX_TOTAL_EDIT_COMMIT_OPERATIONS) {
                throw new IllegalStateException("Siege Gate edit-commit operations exceed hard caps");
            }
            jobs.appendTag(writeEditCommitJob(job));
        }
        root.setTag(NBT_EDIT_COMMIT_JOBS, jobs);

        NBTTagList reservations = new NBTTagList();
        List<TargetReservation> sortedReservations =
                new ArrayList<TargetReservation>(
                        targetReservationsByPosition.values()
                );
        Collections.sort(sortedReservations, TARGET_RESERVATION_COMPARATOR);
        if (sortedReservations.size() > MAX_TARGET_RESERVATIONS) {
            throw new IllegalStateException("Siege Gate target reservations exceed hard caps");
        }
        for (TargetReservation reservation : sortedReservations) {
            reservations.appendTag(writeTargetReservation(reservation));
        }
        root.setTag(NBT_TARGET_RESERVATIONS, reservations);

        NBTTagList tombstones = new NBTTagList();
        List<CompletedEditCommitTombstone> sortedTombstones =
                new ArrayList<CompletedEditCommitTombstone>(
                        completedEditCommitTombstones
                );
        Collections.sort(sortedTombstones, EDIT_COMMIT_TOMBSTONE_COMPARATOR);
        if (sortedTombstones.size() > MAX_COMPLETED_EDIT_COMMIT_TOMBSTONES) {
            throw new IllegalStateException("Siege Gate edit-commit tombstones exceed hard caps");
        }
        for (CompletedEditCommitTombstone tombstone : sortedTombstones) {
            tombstones.appendTag(writeCompletedTombstone(tombstone));
        }
        root.setTag(NBT_COMPLETED_EDIT_COMMIT_TOMBSTONES, tombstones);
    }

    private boolean indexEditCommitJob(EditCommitJob job) {
        if (job == null || editCommitJobsByUuid.containsKey(job.getJobUuid())
                || editCommitJobsByUuid.size() >= MAX_EDIT_COMMIT_JOBS
                || totalEditCommitOperations + job.getPhysicalOperations().size()
                > MAX_TOTAL_EDIT_COMMIT_OPERATIONS) {
            return false;
        }
        if (job.getState() != EditCommitJob.State.COMPLETE
                && activeEditCommitJobUuidByGateUuid.containsKey(
                        job.getGateUuid()
                )) {
            return false;
        }
        editCommitJobsByUuid.put(job.getJobUuid(), job);
        if (job.getState() != EditCommitJob.State.COMPLETE) {
            activeEditCommitJobUuidByGateUuid.put(
                    job.getGateUuid(), job.getJobUuid()
            );
        }
        totalEditCommitOperations += job.getPhysicalOperations().size();
        indexEditCommitOperations(job);
        indexEditCommitControllerPromotion(job);
        queueEditCommitArchival(job);
        return true;
    }

    private boolean indexTargetReservation(TargetReservation reservation) {
        if (reservation == null
                || targetReservationsByPosition.size() >= MAX_TARGET_RESERVATIONS) {
            return false;
        }
        BlockPosition position = new BlockPosition(
                reservation.getX(), reservation.getY(), reservation.getZ()
        );
        if (targetReservationsByPosition.containsKey(position)) {
            return false;
        }
        targetReservationsByPosition.put(position, reservation);
        Set<BlockPosition> positions = targetReservationPositionsByJob.get(
                reservation.getJobUuid()
        );
        if (positions == null) {
            positions = new HashSet<BlockPosition>();
            targetReservationPositionsByJob.put(reservation.getJobUuid(), positions);
        }
        positions.add(position);
        return true;
    }

    private boolean validateV2EditCommitConsistency() {
        for (EditCommitJob job : editCommitJobsByUuid.values()) {
            if (!isEditCommitControllerConsistent(job)
                    || !areEditCommitReservationsConsistent(job)) {
                return false;
            }
        }
        for (TargetReservation reservation
                : targetReservationsByPosition.values()) {
            EditCommitJob job = editCommitJobsByUuid.get(
                    reservation.getJobUuid()
            );
            if (job == null || !reservationMatchesJob(reservation, job)
                    || !hasAddOperationAt(job, reservation.getX(),
                            reservation.getY(), reservation.getZ())) {
                return false;
            }
        }
        return true;
    }

    private boolean isEditCommitControllerConsistent(EditCommitJob job) {
        ControllerRecord controller = controllersByUuid.get(job.getGateUuid());
        if (job.getState() == EditCommitJob.State.COMPLETE) {
            return isHistoricalCompleteEditCommit(job);
        }
        if (controller == null || !controller.matchesController(job.getDimension(),
                job.getControllerX(), job.getControllerY(), job.getControllerZ())) {
            return false;
        }
        if (job.getState() == EditCommitJob.State.CONFLICT) {
            return controller.status == ControllerStatus.QUARANTINED
                    && (controller.structureRevision == job.getBaseRevision()
                    || controller.structureRevision == job.getTargetRevision());
        }
        return controller.structureRevision == job.getBaseRevision()
                && controller.status == ControllerStatus.MUTATING
                && controller.hasEquivalentParts(
                        job.getOriginalSnapshot().getParts());
    }

    /** Repairs only the legacy Phase 4C ACTIVE-status reload divergence. */
    private boolean normalizeLoadedEditCommitMutationStatus() {
        boolean changed = false;
        for (EditCommitJob job : editCommitJobsByUuid.values()) {
            if (job.getState() != EditCommitJob.State.PREPARED
                    && job.getState() != EditCommitJob.State.APPLYING_WORLD
                    && job.getState()
                    != EditCommitJob.State.PROMOTING_CONTROLLER
                    && job.getState()
                    != EditCommitJob.State.PROMOTING_OWNERSHIP) {
                continue;
            }
            ControllerRecord controller = controllersByUuid.get(
                    job.getGateUuid());
            if (controller == null
                    || controller.structureRevision != job.getBaseRevision()
                    || !controller.hasEquivalentParts(
                            job.getOriginalSnapshot().getParts())
                    || (controller.status != ControllerStatus.MUTATING
                    && controller.status != ControllerStatus.ACTIVE)) {
                return false;
            }
            if (controller.status != ControllerStatus.MUTATING
                    && (job.getState() == EditCommitJob.State.PROMOTING_CONTROLLER
                    || job.getState()
                    == EditCommitJob.State.PROMOTING_OWNERSHIP)) {
                return false;
            }
            if (controller.status != ControllerStatus.MUTATING) {
                controller.status = ControllerStatus.MUTATING;
                changed = true;
            }
        }
        if (changed) {
            markDirty();
        }
        return true;
    }

    private boolean areEditCommitReservationsConsistent(EditCommitJob job) {
        Set<BlockPosition> actual = targetReservationPositionsByJob.get(
                job.getJobUuid()
        );
        if (job.getState() == EditCommitJob.State.COMPLETE) {
            if (actual != null && !actual.isEmpty()) {
                return false;
            }
            for (TargetReservation reservation
                    : targetReservationsByPosition.values()) {
                if (reservation.getJobUuid().equals(job.getJobUuid())) {
                    return false;
                }
            }
            return true;
        }
        int addCount = 0;
        for (EditCommitJob.PhysicalOperation operation
                : job.getPhysicalOperations()) {
            if (operation.getKind() != EditCommitJob.OperationKind.ADD) {
                continue;
            }
            ++addCount;
            BlockPosition position = new BlockPosition(
                    operation.getX(), operation.getY(), operation.getZ()
            );
            if (actual == null || !actual.contains(position)) {
                return false;
            }
        }
        if (addCount == 0) {
            return actual == null || actual.isEmpty();
        }
        return actual != null && actual.size() == addCount;
    }

    /**
     * COMPLETE is retained historical evidence.  It must remain valid after a
     * later same-GateUUID revision advances, but can never authorize that later
     * ownership or repair any current state.
     */
    private boolean isHistoricalCompleteEditCommit(EditCommitJob job) {
        if (job == null || job.getState() != EditCommitJob.State.COMPLETE
                || readOnlyDueToInvalidData || job.getBaseRevision() <= 0
                || job.getTargetRevision() != job.getBaseRevision() + 1
                || job.getUpdatedTick() < job.getCreatedTick()
                || hasActiveEditCommitJobUuid(job.getJobUuid())
                || containsTombstoneJobUuid(job.getJobUuid())
                || !snapshotMatchesJob(job.getOriginalSnapshot(),
                        job.getGateUuid(), job.getDimension(),
                        job.getControllerX(), job.getControllerY(),
                        job.getControllerZ(), job.getBaseRevision())
                || !snapshotMatchesJob(job.getTargetSnapshot(),
                        job.getGateUuid(), job.getDimension(),
                        job.getControllerX(), job.getControllerY(),
                        job.getControllerZ(), job.getTargetRevision())
                || !isValidEditCommitDelta(job.getOriginalSnapshot(),
                        job.getTargetSnapshot(), job.getPhysicalOperations())
                || !areEditCommitReservationsConsistent(job)) {
            return false;
        }
        ControllerRecord current = controllersByUuid.get(job.getGateUuid());
        if (current == null) {
            return true;
        }
        return current.matchesController(job.getDimension(),
                        job.getControllerX(), job.getControllerY(),
                        job.getControllerZ())
                && current.structureRevision >= job.getTargetRevision()
                && hasExactControllerPartIndexes(current,
                        current.structureRevision)
                && hasNoExtraGateOwnership(job.getGateUuid(), current);
    }

    private static boolean reservationMatchesJob(
            TargetReservation reservation, EditCommitJob job
    ) {
        return reservation.getGateUuid().equals(job.getGateUuid())
                && reservation.getBaseRevision() == job.getBaseRevision()
                && reservation.getTargetRevision() == job.getTargetRevision()
                && reservation.getDimension() == job.getDimension();
    }

    private static boolean hasAddOperationAt(
            EditCommitJob job, int x, int y, int z
    ) {
        for (EditCommitJob.PhysicalOperation operation
                : job.getPhysicalOperations()) {
            if (operation.getKind() == EditCommitJob.OperationKind.ADD
                    && operation.getX() == x && operation.getY() == y
                    && operation.getZ() == z) {
                return true;
            }
        }
        return false;
    }

    private boolean containsTombstoneJobUuid(UUID jobUuid) {
        for (CompletedEditCommitTombstone tombstone
                : completedEditCommitTombstones) {
            if (tombstone.getJobUuid().equals(jobUuid)) {
                return true;
            }
        }
        return false;
    }

    private UUID newUniqueEditCommitJobUuid() {
        for (int attempt = 0; attempt < 16; ++attempt) {
            UUID candidate = UUID.randomUUID();
            if (!editCommitJobsByUuid.containsKey(candidate)
                    && !containsTombstoneJobUuid(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /** Uses the existing V2 parser as the single canonical job-shape check. */
    private boolean isValidPreparedEditCommitJob(EditCommitJob job) {
        return job != null && job.getState() == EditCommitJob.State.PREPARED
                && readEditCommitJob(writeEditCommitJob(job)) != null;
    }

    private static int countAddOperations(EditCommitJob job) {
        int count = 0;
        for (EditCommitJob.PhysicalOperation operation
                : job.getPhysicalOperations()) {
            if (operation.getKind() == EditCommitJob.OperationKind.ADD) {
                ++count;
            }
        }
        return count;
    }

    private static String shortUuid(UUID value) {
        if (value == null) {
            return "null";
        }
        String text = value.toString();
        return text.substring(0, Math.min(8, text.length()));
    }

    private EditCommitJob readEditCommitJob(NBTTagCompound nbt) {
        if (!nbt.hasKey(NBT_JOB_UUID, TAG_STRING)
                || !nbt.hasKey(NBT_GATE_UUID, TAG_STRING)
                || !nbt.hasKey(NBT_DIMENSION, TAG_INT)
                || !nbt.hasKey(NBT_CONTROLLER_X, TAG_INT)
                || !nbt.hasKey(NBT_CONTROLLER_Y, TAG_INT)
                || !nbt.hasKey(NBT_CONTROLLER_Z, TAG_INT)
                || !nbt.hasKey(NBT_BASE_REVISION, TAG_INT)
                || !nbt.hasKey(NBT_TARGET_REVISION, TAG_INT)
                || !nbt.hasKey(NBT_STATE, TAG_STRING)
                || !nbt.hasKey(NBT_CREATED_TICK, TAG_LONG)
                || !nbt.hasKey(NBT_UPDATED_TICK, TAG_LONG)
                || !nbt.hasKey(NBT_ORIGINAL_SNAPSHOT, TAG_COMPOUND)
                || !nbt.hasKey(NBT_TARGET_SNAPSHOT, TAG_COMPOUND)
                || !nbt.hasKey(NBT_OPERATION_COUNT, TAG_INT)
                || !nbt.hasKey(NBT_OPERATIONS, TAG_LIST)
                || !nbt.hasKey(NBT_FAILURE_CODE, TAG_STRING)
                || !nbt.hasKey(NBT_FAILURE_X, TAG_INT)
                || !nbt.hasKey(NBT_FAILURE_Y, TAG_INT)
                || !nbt.hasKey(NBT_FAILURE_Z, TAG_INT)) {
            return null;
        }
        UUID jobUuid = readUuid(nbt.getString(NBT_JOB_UUID));
        UUID gateUuid = readUuid(nbt.getString(NBT_GATE_UUID));
        boolean hasInitiatorUuid = nbt.hasKey(NBT_INITIATOR_UUID, TAG_STRING);
        UUID initiatorUuid = hasInitiatorUuid
                ? readUuid(nbt.getString(NBT_INITIATOR_UUID)) : null;
        EditCommitJob.State state = EditCommitJob.State.fromName(
                nbt.getString(NBT_STATE)
        );
        EditCommitJob.FailureCode failureCode =
                EditCommitJob.FailureCode.fromName(
                        nbt.getString(NBT_FAILURE_CODE)
                );
        int dimension = nbt.getInteger(NBT_DIMENSION);
        int controllerX = nbt.getInteger(NBT_CONTROLLER_X);
        int controllerY = nbt.getInteger(NBT_CONTROLLER_Y);
        int controllerZ = nbt.getInteger(NBT_CONTROLLER_Z);
        int baseRevision = nbt.getInteger(NBT_BASE_REVISION);
        int targetRevision = nbt.getInteger(NBT_TARGET_REVISION);
        int operationCount = nbt.getInteger(NBT_OPERATION_COUNT);
        NBTTagList operationsNbt = (NBTTagList)nbt.getTag(NBT_OPERATIONS);
        if (jobUuid == null || gateUuid == null
                || (hasInitiatorUuid && initiatorUuid == null) || state == null
                || failureCode == null || !isSanePosition(
                        controllerX, controllerY, controllerZ)
                || baseRevision <= 0 || baseRevision == Integer.MAX_VALUE
                || targetRevision != baseRevision + 1
                || operationCount < 0
                || operationCount > MAX_EDIT_COMMIT_OPERATIONS_PER_JOB
                || operationsNbt.tagCount() != operationCount
                || !isSanePosition(nbt.getInteger(NBT_FAILURE_X),
                        nbt.getInteger(NBT_FAILURE_Y),
                        nbt.getInteger(NBT_FAILURE_Z))
                || !isCompoundListWithin(
                        operationsNbt, MAX_EDIT_COMMIT_OPERATIONS_PER_JOB
                )) {
            return null;
        }
        EditCommitJob.Snapshot original = readEditCommitSnapshot(
                nbt.getCompoundTag(NBT_ORIGINAL_SNAPSHOT)
        );
        EditCommitJob.Snapshot target = readEditCommitSnapshot(
                nbt.getCompoundTag(NBT_TARGET_SNAPSHOT)
        );
        if (!snapshotMatchesJob(original, gateUuid, dimension, controllerX,
                controllerY, controllerZ, baseRevision)
                || !snapshotMatchesJob(target, gateUuid, dimension, controllerX,
                        controllerY, controllerZ, targetRevision)
                || original.getOrientation() != target.getOrientation()) {
            return null;
        }
        List<EditCommitJob.PhysicalOperation> operations =
                new ArrayList<EditCommitJob.PhysicalOperation>(operationCount);
        Set<BlockPosition> positions = new HashSet<BlockPosition>();
        Set<Integer> ordinals = new HashSet<Integer>();
        int adds = 0;
        int removes = 0;
        for (int i = 0; i < operationCount; ++i) {
            EditCommitJob.PhysicalOperation operation = readPhysicalOperation(
                    operationsNbt.getCompoundTagAt(i)
            );
            if (operation == null || operation.getDimension() != dimension
                    || operation.getOrdinal() >= operationCount
                    || !positions.add(new BlockPosition(operation.getX(),
                            operation.getY(), operation.getZ()))
                    || !ordinals.add(Integer.valueOf(operation.getOrdinal()))) {
                return null;
            }
            if (operation.getKind() == EditCommitJob.OperationKind.ADD) {
                ++adds;
            } else {
                ++removes;
            }
            operations.add(operation);
        }
        if (adds > GateStructureValidator.MAX_GATE_PARTS
                || removes > GateStructureValidator.MAX_GATE_PARTS
                || !isValidEditCommitDelta(original, target, operations)) {
            return null;
        }
        return new EditCommitJob(jobUuid, gateUuid, dimension, controllerX,
                controllerY, controllerZ, baseRevision, targetRevision,
                initiatorUuid, state, Math.max(0L, nbt.getLong(NBT_CREATED_TICK)),
                Math.max(0L, nbt.getLong(NBT_UPDATED_TICK)), original, target,
                operations, failureCode, nbt.getInteger(NBT_FAILURE_X),
                nbt.getInteger(NBT_FAILURE_Y), nbt.getInteger(NBT_FAILURE_Z));
    }

    private EditCommitJob.Snapshot readEditCommitSnapshot(NBTTagCompound nbt) {
        if (!nbt.hasKey(NBT_GATE_UUID, TAG_STRING)
                || !nbt.hasKey(NBT_DIMENSION, TAG_INT)
                || !nbt.hasKey(NBT_CONTROLLER_X, TAG_INT)
                || !nbt.hasKey(NBT_CONTROLLER_Y, TAG_INT)
                || !nbt.hasKey(NBT_CONTROLLER_Z, TAG_INT)
                || !nbt.hasKey(NBT_STRUCTURE_REVISION, TAG_INT)
                || !nbt.hasKey(NBT_ORIENTATION, TAG_STRING)
                || !nbt.hasKey(NBT_OPENING_DIRECTION, TAG_STRING)
                || !nbt.hasKey(NBT_LEFT_HINGE, TAG_COMPOUND)
                || !nbt.hasKey(NBT_RIGHT_HINGE, TAG_COMPOUND)
                || !nbt.hasKey(NBT_PART_COUNT, TAG_INT)
                || !nbt.hasKey(NBT_PARTS, TAG_LIST)) {
            return null;
        }
        UUID gateUuid = readUuid(nbt.getString(NBT_GATE_UUID));
        int dimension = nbt.getInteger(NBT_DIMENSION);
        int controllerX = nbt.getInteger(NBT_CONTROLLER_X);
        int controllerY = nbt.getInteger(NBT_CONTROLLER_Y);
        int controllerZ = nbt.getInteger(NBT_CONTROLLER_Z);
        int revision = nbt.getInteger(NBT_STRUCTURE_REVISION);
        GateOrientation orientation = GateOrientation.fromSerializedName(
                nbt.getString(NBT_ORIENTATION)
        );
        GateOpeningDirection direction =
                GateOpeningDirection.fromSerializedName(
                        nbt.getString(NBT_OPENING_DIRECTION)
                );
        boolean borderTextureEnabled =
                !nbt.hasKey(NBT_BORDER_TEXTURE_ENABLED, TAG_BYTE)
                        || nbt.getBoolean(NBT_BORDER_TEXTURE_ENABLED);
        GateHinge left = readEditCommitHinge(
                nbt.getCompoundTag(NBT_LEFT_HINGE)
        );
        GateHinge right = readEditCommitHinge(
                nbt.getCompoundTag(NBT_RIGHT_HINGE)
        );
        int partCount = nbt.getInteger(NBT_PART_COUNT);
        NBTTagList partsNbt = (NBTTagList)nbt.getTag(NBT_PARTS);
        if (gateUuid == null || !isSanePosition(controllerX, controllerY,
                controllerZ) || revision <= 0 || orientation == null
                || direction == null || left == null || right == null
                || partCount <= 0
                || partCount > GateStructureValidator.MAX_GATE_PARTS
                || partsNbt.tagCount() != partCount
                || !isCompoundListWithin(partsNbt,
                        GateStructureValidator.MAX_GATE_PARTS)) {
            return null;
        }
        List<GatePartData> parts = new ArrayList<GatePartData>(partCount);
        Set<BlockPosition> positions = new HashSet<BlockPosition>();
        for (int i = 0; i < partCount; ++i) {
            GatePartData part = readEditCommitPart(partsNbt.getCompoundTagAt(i));
            if (part == null || !part.hasValidAbsolutePosition(controllerX,
                    controllerY, controllerZ)
                    || !positions.add(new BlockPosition(
                            part.getAbsoluteX(controllerX),
                            part.getAbsoluteY(controllerY),
                            part.getAbsoluteZ(controllerZ)))) {
                return null;
            }
            parts.add(part);
        }
        GateStructureValidator.ValidationResult validation =
                GateStructureValidator.validateFinalized(parts, left, right,
                        orientation, direction, controllerX, controllerY,
                        controllerZ);
        if (!validation.isValid()) {
            return null;
        }
        return new EditCommitJob.Snapshot(gateUuid, dimension, controllerX,
                controllerY, controllerZ, revision, validation.getOrientation(),
                direction, borderTextureEnabled, validation.getLeftHinge(),
                validation.getRightHinge(), parts);
    }

    private GatePartData readEditCommitPart(NBTTagCompound nbt) {
        if (!nbt.hasKey(NBT_RELATIVE_X, TAG_INT)
                || !nbt.hasKey(NBT_RELATIVE_Y, TAG_INT)
                || !nbt.hasKey(NBT_RELATIVE_Z, TAG_INT)
                || !nbt.hasKey(NBT_LEAF, TAG_STRING)
                || !nbt.hasKey(NBT_SOURCE_BLOCK, TAG_STRING)
                || !nbt.hasKey(NBT_SOURCE_META, TAG_INT)
                || !nbt.hasKey(NBT_SOURCE_RESTORABLE, TAG_BYTE)) {
            return null;
        }

        GateLeaf leaf =
                GateLeaf.fromSerializedName(
                        nbt.getString(NBT_LEAF)
                );

        String sourceBlock =
                nbt.getString(NBT_SOURCE_BLOCK);

        int sourceMeta =
                nbt.getInteger(NBT_SOURCE_META);

        NBTTagCompound sourceTileEntityNbt =
                nbt.hasKey(
                        NBT_SOURCE_TILE_ENTITY,
                        TAG_COMPOUND
                )
                        ? nbt.getCompoundTag(
                        NBT_SOURCE_TILE_ENTITY
                )
                        : null;

        boolean restorable =
                nbt.getBoolean(NBT_SOURCE_RESTORABLE);

        if (leaf == null
                || !isBoundedBlockName(sourceBlock)
                || sourceMeta < 0
                || sourceMeta > 15) {
            return null;
        }

        return decodeStoredPartDefinition(
                nbt.getInteger(NBT_RELATIVE_X),
                nbt.getInteger(NBT_RELATIVE_Y),
                nbt.getInteger(NBT_RELATIVE_Z),
                leaf,
                sourceBlock,
                sourceMeta,
                sourceTileEntityNbt,
                restorable
        );
    }


    private GateHinge readEditCommitHinge(NBTTagCompound nbt) {
        if (!nbt.hasKey(NBT_RELATIVE_X, TAG_INT)
                || !nbt.hasKey(NBT_RELATIVE_Z, TAG_INT)
                || !nbt.hasKey(NBT_HINGE_SIDE, TAG_STRING)) {
            return null;
        }
        GateHingeSide side = GateHingeSide.fromSerializedName(
                nbt.getString(NBT_HINGE_SIDE)
        );
        return side == null ? null : new GateHinge(nbt.getInteger(NBT_RELATIVE_X),
                nbt.getInteger(NBT_RELATIVE_Z), side);
    }

    private EditCommitJob.PhysicalOperation readPhysicalOperation(
            NBTTagCompound nbt
    ) {
        if (!nbt.hasKey(NBT_OPERATION_KIND, TAG_STRING)
                || !nbt.hasKey(NBT_ORDINAL, TAG_INT)
                || !nbt.hasKey(NBT_DIMENSION, TAG_INT)
                || !nbt.hasKey(NBT_TARGET_X, TAG_INT)
                || !nbt.hasKey(NBT_TARGET_Y, TAG_INT)
                || !nbt.hasKey(NBT_TARGET_Z, TAG_INT)
                || !nbt.hasKey(NBT_RELATIVE_X, TAG_INT)
                || !nbt.hasKey(NBT_RELATIVE_Y, TAG_INT)
                || !nbt.hasKey(NBT_RELATIVE_Z, TAG_INT)
                || !nbt.hasKey(NBT_LEAF, TAG_STRING)
                || !nbt.hasKey(NBT_EXPECTED_BEFORE_BLOCK, TAG_STRING)
                || !nbt.hasKey(NBT_EXPECTED_BEFORE_META, TAG_INT)
                || !nbt.hasKey(NBT_EXPECTED_AFTER_BLOCK, TAG_STRING)
                || !nbt.hasKey(NBT_EXPECTED_AFTER_META, TAG_INT)
                || !nbt.hasKey(NBT_SOURCE_BLOCK, TAG_STRING)
                || !nbt.hasKey(NBT_SOURCE_META, TAG_INT)
                || !nbt.hasKey(NBT_SOURCE_RESTORABLE, TAG_BYTE)
                || !nbt.hasKey(NBT_PROGRESS_HINT, TAG_STRING)
                || !nbt.hasKey(NBT_FAILURE_CODE, TAG_STRING)
                || !nbt.hasKey(NBT_CONFLICT_LOGGED, TAG_BYTE)) {
            return null;
        }
        EditCommitJob.OperationKind kind = EditCommitJob.OperationKind.fromName(
                nbt.getString(NBT_OPERATION_KIND)
        );
        GateLeaf leaf = GateLeaf.fromSerializedName(nbt.getString(NBT_LEAF));
        EditCommitJob.ProgressHint progress =
                EditCommitJob.ProgressHint.fromName(
                        nbt.getString(NBT_PROGRESS_HINT)
                );
        EditCommitJob.FailureCode failure =
                EditCommitJob.FailureCode.fromName(
                        nbt.getString(NBT_FAILURE_CODE)
                );
        String before = nbt.getString(NBT_EXPECTED_BEFORE_BLOCK);
        String after = nbt.getString(NBT_EXPECTED_AFTER_BLOCK);
        String source = nbt.getString(NBT_SOURCE_BLOCK);
        String reason = bounded(nbt.getString(NBT_CONFLICT_REASON),
                MAX_REASON_LENGTH);
        if (reason != null && reason.isEmpty()) {
            reason = null;
        }
        int x = nbt.getInteger(NBT_TARGET_X);
        int y = nbt.getInteger(NBT_TARGET_Y);
        int z = nbt.getInteger(NBT_TARGET_Z);
        int beforeMeta = nbt.getInteger(NBT_EXPECTED_BEFORE_META);
        int afterMeta = nbt.getInteger(NBT_EXPECTED_AFTER_META);
        int sourceMeta = nbt.getInteger(NBT_SOURCE_META);
        if (kind == null || leaf == null || progress == null || failure == null
                || nbt.getInteger(NBT_ORDINAL) < 0 || !isSanePosition(x, y, z)
                || !isBoundedBlockName(before) || !isBoundedBlockName(after)
                || !isBoundedBlockName(source) || beforeMeta < 0 || beforeMeta > 15
                || afterMeta < 0 || afterMeta > 15 || sourceMeta < 0
                || sourceMeta > 15
                || (progress == EditCommitJob.ProgressHint.CONFLICT
                && failure == EditCommitJob.FailureCode.NONE)
                || (failure != EditCommitJob.FailureCode.NONE
                && (reason == null || reason.isEmpty()))) {
            return null;
        }
        if (Block.getBlockFromName(before) == null
                || Block.getBlockFromName(after) == null
                || Block.getBlockFromName(source) == null) {
            return null;
        }
        return new EditCommitJob.PhysicalOperation(kind,
                nbt.getInteger(NBT_ORDINAL), nbt.getInteger(NBT_DIMENSION), x, y,
                z, nbt.getInteger(NBT_RELATIVE_X),
                nbt.getInteger(NBT_RELATIVE_Y),
                nbt.getInteger(NBT_RELATIVE_Z), leaf, before, beforeMeta, after,
                afterMeta, source, sourceMeta,
                nbt.getBoolean(NBT_SOURCE_RESTORABLE), progress, failure, reason,
                nbt.getBoolean(NBT_CONFLICT_LOGGED));
    }

    private TargetReservation readTargetReservation(NBTTagCompound nbt) {
        if (!nbt.hasKey(NBT_JOB_UUID, TAG_STRING)
                || !nbt.hasKey(NBT_GATE_UUID, TAG_STRING)
                || !nbt.hasKey(NBT_BASE_REVISION, TAG_INT)
                || !nbt.hasKey(NBT_TARGET_REVISION, TAG_INT)
                || !nbt.hasKey(NBT_DIMENSION, TAG_INT)
                || !nbt.hasKey(NBT_TARGET_X, TAG_INT)
                || !nbt.hasKey(NBT_TARGET_Y, TAG_INT)
                || !nbt.hasKey(NBT_TARGET_Z, TAG_INT)) {
            return null;
        }
        UUID jobUuid = readUuid(nbt.getString(NBT_JOB_UUID));
        UUID gateUuid = readUuid(nbt.getString(NBT_GATE_UUID));
        int baseRevision = nbt.getInteger(NBT_BASE_REVISION);
        int targetRevision = nbt.getInteger(NBT_TARGET_REVISION);
        int x = nbt.getInteger(NBT_TARGET_X);
        int y = nbt.getInteger(NBT_TARGET_Y);
        int z = nbt.getInteger(NBT_TARGET_Z);
        if (jobUuid == null || gateUuid == null || baseRevision <= 0
                || baseRevision == Integer.MAX_VALUE
                || targetRevision != baseRevision + 1
                || !isSanePosition(x, y, z)) {
            return null;
        }
        return new TargetReservation(jobUuid, gateUuid, baseRevision,
                targetRevision, nbt.getInteger(NBT_DIMENSION), x, y, z);
    }

    private CompletedEditCommitTombstone readCompletedTombstone(
            NBTTagCompound nbt
    ) {
        if (!nbt.hasKey(NBT_JOB_UUID, TAG_STRING)
                || !nbt.hasKey(NBT_GATE_UUID, TAG_STRING)
                || !nbt.hasKey(NBT_DIMENSION, TAG_INT)
                || !nbt.hasKey(NBT_CONTROLLER_X, TAG_INT)
                || !nbt.hasKey(NBT_CONTROLLER_Y, TAG_INT)
                || !nbt.hasKey(NBT_CONTROLLER_Z, TAG_INT)
                || !nbt.hasKey(NBT_BASE_REVISION, TAG_INT)
                || !nbt.hasKey(NBT_TARGET_REVISION, TAG_INT)
                || !nbt.hasKey(NBT_COMPLETED_TICK, TAG_LONG)) {
            return null;
        }
        UUID jobUuid = readUuid(nbt.getString(NBT_JOB_UUID));
        UUID gateUuid = readUuid(nbt.getString(NBT_GATE_UUID));
        int baseRevision = nbt.getInteger(NBT_BASE_REVISION);
        int targetRevision = nbt.getInteger(NBT_TARGET_REVISION);
        int x = nbt.getInteger(NBT_CONTROLLER_X);
        int y = nbt.getInteger(NBT_CONTROLLER_Y);
        int z = nbt.getInteger(NBT_CONTROLLER_Z);
        if (jobUuid == null || gateUuid == null || baseRevision <= 0
                || baseRevision == Integer.MAX_VALUE
                || targetRevision != baseRevision + 1
                || !isSanePosition(x, y, z)) {
            return null;
        }
        return new CompletedEditCommitTombstone(jobUuid, gateUuid,
                nbt.getInteger(NBT_DIMENSION), x, y, z, baseRevision,
                targetRevision, Math.max(0L, nbt.getLong(NBT_COMPLETED_TICK)));
    }

    private static boolean snapshotMatchesJob(EditCommitJob.Snapshot snapshot,
            UUID gateUuid, int dimension, int controllerX, int controllerY,
            int controllerZ, int revision) {
        return snapshot != null && gateUuid.equals(snapshot.getGateUuid())
                && snapshot.getDimension() == dimension
                && snapshot.getControllerX() == controllerX
                && snapshot.getControllerY() == controllerY
                && snapshot.getControllerZ() == controllerZ
                && snapshot.getRevision() == revision;
    }

    private static boolean isValidEditCommitDelta(
            EditCommitJob.Snapshot original, EditCommitJob.Snapshot target,
            List<EditCommitJob.PhysicalOperation> operations
    ) {
        Map<BlockPosition, GatePartData> originalByPosition =
                mapSnapshotParts(original);
        Map<BlockPosition, GatePartData> targetByPosition =
                mapSnapshotParts(target);
        Map<BlockPosition, EditCommitJob.PhysicalOperation> operationByPosition =
                new HashMap<BlockPosition, EditCommitJob.PhysicalOperation>();
        for (EditCommitJob.PhysicalOperation operation : operations) {
            BlockPosition position = new BlockPosition(operation.getX(),
                    operation.getY(), operation.getZ());
            if (operationByPosition.put(position, operation) != null) {
                return false;
            }
        }
        String gatePartName = Block.blockRegistry.getNameForObject(
                SiegeRegistry.gatePart
        );
        if (!isBoundedBlockName(gatePartName)) {
            return false;
        }
        for (Map.Entry<BlockPosition, GatePartData> entry
                : originalByPosition.entrySet()) {
            GatePartData targetPart = targetByPosition.get(entry.getKey());
            EditCommitJob.PhysicalOperation operation = operationByPosition.remove(
                    entry.getKey()
            );
            if (targetPart != null) {
                if (operation != null) {
                    return false;
                }
                continue;
            }
            if (!matchesRemoveOperation(operation, entry.getValue(), gatePartName)) {
                return false;
            }
        }
        for (Map.Entry<BlockPosition, GatePartData> entry
                : targetByPosition.entrySet()) {
            if (originalByPosition.containsKey(entry.getKey())) {
                continue;
            }
            EditCommitJob.PhysicalOperation operation = operationByPosition.remove(
                    entry.getKey()
            );
            if (!matchesAddOperation(operation, entry.getValue(), gatePartName)) {
                return false;
            }
        }
        return operationByPosition.isEmpty();
    }

    private static Map<BlockPosition, GatePartData> mapSnapshotParts(
            EditCommitJob.Snapshot snapshot
    ) {
        Map<BlockPosition, GatePartData> parts =
                new HashMap<BlockPosition, GatePartData>();
        for (GatePartData part : snapshot.getParts()) {
            parts.put(new BlockPosition(
                    part.getAbsoluteX(snapshot.getControllerX()),
                    part.getAbsoluteY(snapshot.getControllerY()),
                    part.getAbsoluteZ(snapshot.getControllerZ())), part);
        }
        return parts;
    }

    private static boolean matchesAddOperation(
            EditCommitJob.PhysicalOperation operation, GatePartData target,
            String gatePartName
    ) {
        return operation != null
                && operation.getKind() == EditCommitJob.OperationKind.ADD
                && operation.getRelativeX() == target.getRelativeX()
                && operation.getRelativeY() == target.getRelativeY()
                && operation.getRelativeZ() == target.getRelativeZ()
                && operation.getFinalLeaf() == target.getLeaf()
                && target.getSourceBlockName().equals(
                        operation.getExpectedBeforeBlock())
                && target.getSourceMetadata()
                        == operation.getExpectedBeforeMetadata()
                && gatePartName.equals(operation.getExpectedAfterBlock())
                && operation.getExpectedAfterMetadata() == 0
                && matchesOperationSource(operation, target);
    }

    private static boolean matchesRemoveOperation(
            EditCommitJob.PhysicalOperation operation, GatePartData original,
            String gatePartName
    ) {
        boolean restorable = original.hasStoredSourceBlock()
                && original.getSourceBlockForRestoration() != null;
        return operation != null
                && operation.getKind() == EditCommitJob.OperationKind.REMOVE
                && operation.getRelativeX() == original.getRelativeX()
                && operation.getRelativeY() == original.getRelativeY()
                && operation.getRelativeZ() == original.getRelativeZ()
                && operation.getFinalLeaf() == original.getLeaf()
                && gatePartName.equals(operation.getExpectedBeforeBlock())
                && operation.getExpectedBeforeMetadata() == 0
                && (restorable
                ? original.getSourceBlockName().equals(
                        operation.getExpectedAfterBlock())
                && original.getSourceMetadata()
                        == operation.getExpectedAfterMetadata()
                : "minecraft:air".equals(operation.getExpectedAfterBlock())
                && operation.getExpectedAfterMetadata() == 0)
                && matchesOperationSource(operation, original);
    }

    private static boolean matchesOperationSource(
            EditCommitJob.PhysicalOperation operation, GatePartData part
    ) {
        boolean restorable = part.hasStoredSourceBlock()
                && part.getSourceBlockForRestoration() != null;
        return operation.isSourceRestorable() == restorable
                && part.getSourceBlockName().equals(operation.getSourceBlock())
                && part.getSourceMetadata() == operation.getSourceMetadata();
    }

    private static boolean isCompoundListWithin(NBTTagList list, int cap) {
        return list != null && list.tagCount() <= cap
                && (list.tagCount() == 0 || list.func_150303_d() == TAG_COMPOUND);
    }

    private static NBTTagCompound writeEditCommitJob(EditCommitJob job) {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString(NBT_JOB_UUID, job.getJobUuid().toString());
        nbt.setString(NBT_GATE_UUID, job.getGateUuid().toString());
        nbt.setInteger(NBT_DIMENSION, job.getDimension());
        nbt.setInteger(NBT_CONTROLLER_X, job.getControllerX());
        nbt.setInteger(NBT_CONTROLLER_Y, job.getControllerY());
        nbt.setInteger(NBT_CONTROLLER_Z, job.getControllerZ());
        nbt.setInteger(NBT_BASE_REVISION, job.getBaseRevision());
        nbt.setInteger(NBT_TARGET_REVISION, job.getTargetRevision());
        if (job.getInitiatorUuid() != null) {
            nbt.setString(NBT_INITIATOR_UUID, job.getInitiatorUuid().toString());
        }
        nbt.setString(NBT_STATE, job.getState().name());
        nbt.setLong(NBT_CREATED_TICK, job.getCreatedTick());
        nbt.setLong(NBT_UPDATED_TICK, job.getUpdatedTick());
        nbt.setTag(NBT_ORIGINAL_SNAPSHOT,
                writeEditCommitSnapshot(job.getOriginalSnapshot()));
        nbt.setTag(NBT_TARGET_SNAPSHOT,
                writeEditCommitSnapshot(job.getTargetSnapshot()));
        List<EditCommitJob.PhysicalOperation> operations =
                new ArrayList<EditCommitJob.PhysicalOperation>(
                        job.getPhysicalOperations()
                );
        Collections.sort(operations, PHYSICAL_OPERATION_COMPARATOR);
        nbt.setInteger(NBT_OPERATION_COUNT, operations.size());
        NBTTagList operationList = new NBTTagList();
        for (EditCommitJob.PhysicalOperation operation : operations) {
            operationList.appendTag(writePhysicalOperation(operation));
        }
        nbt.setTag(NBT_OPERATIONS, operationList);
        nbt.setString(NBT_FAILURE_CODE, job.getFailureCode().name());
        nbt.setInteger(NBT_FAILURE_X, job.getFailureX());
        nbt.setInteger(NBT_FAILURE_Y, job.getFailureY());
        nbt.setInteger(NBT_FAILURE_Z, job.getFailureZ());
        return nbt;
    }

    private static NBTTagCompound writeEditCommitSnapshot(
            EditCommitJob.Snapshot snapshot
    ) {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString(NBT_GATE_UUID, snapshot.getGateUuid().toString());
        nbt.setInteger(NBT_DIMENSION, snapshot.getDimension());
        nbt.setInteger(NBT_CONTROLLER_X, snapshot.getControllerX());
        nbt.setInteger(NBT_CONTROLLER_Y, snapshot.getControllerY());
        nbt.setInteger(NBT_CONTROLLER_Z, snapshot.getControllerZ());
        nbt.setInteger(NBT_STRUCTURE_REVISION, snapshot.getRevision());
        nbt.setString(NBT_ORIENTATION, snapshot.getOrientation().name());
        nbt.setString(NBT_OPENING_DIRECTION,
                snapshot.getOpeningDirection().name());
        nbt.setBoolean(
                NBT_BORDER_TEXTURE_ENABLED,
                snapshot.isBorderTextureEnabled()
        );
        nbt.setTag(NBT_LEFT_HINGE, writeEditCommitHinge(snapshot.getLeftHinge()));
        nbt.setTag(NBT_RIGHT_HINGE, writeEditCommitHinge(snapshot.getRightHinge()));
        List<GatePartData> parts = new ArrayList<GatePartData>(
                snapshot.getParts()
        );
        Collections.sort(parts, GATE_PART_DATA_COMPARATOR);
        nbt.setInteger(NBT_PART_COUNT, parts.size());
        NBTTagList partList = new NBTTagList();
        for (GatePartData part : parts) {
            NBTTagCompound partNbt = new NBTTagCompound();
            partNbt.setInteger(NBT_RELATIVE_X, part.getRelativeX());
            partNbt.setInteger(NBT_RELATIVE_Y, part.getRelativeY());
            partNbt.setInteger(NBT_RELATIVE_Z, part.getRelativeZ());
            partNbt.setString(NBT_LEAF, part.getLeaf().name());
            partNbt.setString(NBT_SOURCE_BLOCK, part.getSourceBlockName());
            partNbt.setInteger(NBT_SOURCE_META, part.getSourceMetadata());
            if (part.hasSourceTileEntityNbt()) {
                partNbt.setTag(
                        NBT_SOURCE_TILE_ENTITY,
                        part.getSourceTileEntityNbt()
                );
            }
            partNbt.setBoolean(NBT_SOURCE_RESTORABLE,
                    part.hasStoredSourceBlock()
                    && part.getSourceBlockForRestoration() != null);
            partList.appendTag(partNbt);
        }
        nbt.setTag(NBT_PARTS, partList);
        return nbt;
    }

    private static NBTTagCompound writeEditCommitHinge(GateHinge hinge) {
        NBTTagCompound nbt = new NBTTagCompound();

        if (hinge == null || hinge.getSide() == null) {
            return nbt;
        }

        nbt.setInteger(NBT_RELATIVE_X, hinge.getRelativeX());
        nbt.setInteger(NBT_RELATIVE_Z, hinge.getRelativeZ());
        nbt.setString(NBT_HINGE_SIDE, hinge.getSide().name());

        return nbt;
    }

    private static NBTTagCompound writePhysicalOperation(
            EditCommitJob.PhysicalOperation operation
    ) {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString(NBT_OPERATION_KIND, operation.getKind().name());
        nbt.setInteger(NBT_ORDINAL, operation.getOrdinal());
        nbt.setInteger(NBT_DIMENSION, operation.getDimension());
        nbt.setInteger(NBT_TARGET_X, operation.getX());
        nbt.setInteger(NBT_TARGET_Y, operation.getY());
        nbt.setInteger(NBT_TARGET_Z, operation.getZ());
        nbt.setInteger(NBT_RELATIVE_X, operation.getRelativeX());
        nbt.setInteger(NBT_RELATIVE_Y, operation.getRelativeY());
        nbt.setInteger(NBT_RELATIVE_Z, operation.getRelativeZ());
        nbt.setString(NBT_LEAF, operation.getFinalLeaf().name());
        nbt.setString(NBT_EXPECTED_BEFORE_BLOCK,
                operation.getExpectedBeforeBlock());
        nbt.setInteger(NBT_EXPECTED_BEFORE_META,
                operation.getExpectedBeforeMetadata());
        nbt.setString(NBT_EXPECTED_AFTER_BLOCK,
                operation.getExpectedAfterBlock());
        nbt.setInteger(NBT_EXPECTED_AFTER_META,
                operation.getExpectedAfterMetadata());
        nbt.setString(NBT_SOURCE_BLOCK, operation.getSourceBlock());
        nbt.setInteger(NBT_SOURCE_META, operation.getSourceMetadata());
        nbt.setBoolean(NBT_SOURCE_RESTORABLE, operation.isSourceRestorable());
        nbt.setString(NBT_PROGRESS_HINT, operation.getProgressHint().name());
        nbt.setString(NBT_FAILURE_CODE, operation.getFailureCode().name());
        if (operation.getFailureReason() != null) {
            nbt.setString(NBT_CONFLICT_REASON, operation.getFailureReason());
        }
        nbt.setBoolean(NBT_CONFLICT_LOGGED, operation.isFailureLogged());
        return nbt;
    }

    private static NBTTagCompound writeTargetReservation(
            TargetReservation reservation
    ) {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString(NBT_JOB_UUID, reservation.getJobUuid().toString());
        nbt.setString(NBT_GATE_UUID, reservation.getGateUuid().toString());
        nbt.setInteger(NBT_BASE_REVISION, reservation.getBaseRevision());
        nbt.setInteger(NBT_TARGET_REVISION, reservation.getTargetRevision());
        nbt.setInteger(NBT_DIMENSION, reservation.getDimension());
        nbt.setInteger(NBT_TARGET_X, reservation.getX());
        nbt.setInteger(NBT_TARGET_Y, reservation.getY());
        nbt.setInteger(NBT_TARGET_Z, reservation.getZ());
        return nbt;
    }

    private static NBTTagCompound writeCompletedTombstone(
            CompletedEditCommitTombstone tombstone
    ) {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString(NBT_JOB_UUID, tombstone.getJobUuid().toString());
        nbt.setString(NBT_GATE_UUID, tombstone.getGateUuid().toString());
        nbt.setInteger(NBT_DIMENSION, tombstone.getDimension());
        nbt.setInteger(NBT_CONTROLLER_X, tombstone.getControllerX());
        nbt.setInteger(NBT_CONTROLLER_Y, tombstone.getControllerY());
        nbt.setInteger(NBT_CONTROLLER_Z, tombstone.getControllerZ());
        nbt.setInteger(NBT_BASE_REVISION, tombstone.getBaseRevision());
        nbt.setInteger(NBT_TARGET_REVISION, tombstone.getTargetRevision());
        nbt.setLong(NBT_COMPLETED_TICK, tombstone.getCompletedTick());
        return nbt;
    }

    private static final Comparator<EditCommitJob> EDIT_COMMIT_JOB_COMPARATOR =
            new Comparator<EditCommitJob>() {
                @Override
                public int compare(EditCommitJob first, EditCommitJob second) {
                    return first.getJobUuid().toString().compareTo(
                            second.getJobUuid().toString()
                    );
                }
            };

    private static final Comparator<ControllerRecord>
            CONTROLLER_RECORD_COMPARATOR = new Comparator<ControllerRecord>() {
                @Override
                public int compare(ControllerRecord first,
                        ControllerRecord second) {
                    return first.gateUuid.toString().compareTo(
                            second.gateUuid.toString()
                    );
                }
            };

    private static final Comparator<MutationJob> MUTATION_JOB_COMPARATOR =
            new Comparator<MutationJob>() {
                @Override
                public int compare(MutationJob first, MutationJob second) {
                    return first.jobUuid.toString().compareTo(
                            second.jobUuid.toString()
                    );
                }
            };

    private static final Comparator<TargetReservation>
            TARGET_RESERVATION_COMPARATOR = new Comparator<TargetReservation>() {
                @Override
                public int compare(TargetReservation first,
                        TargetReservation second) {
                    int value = first.getDimension() - second.getDimension();
                    if (value == 0) value = first.getY() - second.getY();
                    if (value == 0) value = first.getX() - second.getX();
                    if (value == 0) value = first.getZ() - second.getZ();
                    return value;
                }
            };

    private static final Comparator<CompletedEditCommitTombstone>
            EDIT_COMMIT_TOMBSTONE_COMPARATOR =
            new Comparator<CompletedEditCommitTombstone>() {
                @Override
                public int compare(CompletedEditCommitTombstone first,
                        CompletedEditCommitTombstone second) {
                    int value = first.getCompletedTick() < second.getCompletedTick()
                            ? -1 : first.getCompletedTick() == second.getCompletedTick()
                            ? 0 : 1;
                    return value != 0 ? value : first.getJobUuid().toString()
                            .compareTo(second.getJobUuid().toString());
                }
            };

    private static final Comparator<EditCommitJob.PhysicalOperation>
            PHYSICAL_OPERATION_COMPARATOR =
            new Comparator<EditCommitJob.PhysicalOperation>() {
                @Override
                public int compare(EditCommitJob.PhysicalOperation first,
                        EditCommitJob.PhysicalOperation second) {
                    return first.getOrdinal() - second.getOrdinal();
                }
            };

    private static final Comparator<GatePartData> GATE_PART_DATA_COMPARATOR =
            new Comparator<GatePartData>() {
                @Override
                public int compare(GatePartData first, GatePartData second) {
                    int value = first.getRelativeY() - second.getRelativeY();
                    if (value == 0) value = first.getRelativeX() - second.getRelativeX();
                    if (value == 0) value = first.getRelativeZ() - second.getRelativeZ();
                    return value;
                }
            };

    private void processPreparedEditCommitJobs(long currentTick) {
        List<EditCommitJob> jobs =
                new ArrayList<EditCommitJob>(editCommitJobsByUuid.values());
        Collections.sort(jobs, EDIT_COMMIT_JOB_COMPARATOR);
        int considered = 0;
        for (EditCommitJob job : jobs) {
            if (considered++ >= MAX_EDIT_COMMIT_JOB_DISCOVERY_PER_TICK) {
                return;
            }
            if (job.getState() == EditCommitJob.State.PREPARED) {
                if (isEditCommitDebugPaused(
                        job,
                        EditCommitDebugPausePoint.PREPARED
                )) {
                    continue;
                }

                beginEditCommitWorldApplication(job.getJobUuid(), currentTick);
                return;
            }
        }
    }

    /** WSD-only PREPARED -> APPLYING_WORLD transition. */
    private boolean beginEditCommitWorldApplication(UUID jobUuid,
            long currentTick) {
        if (readOnlyDueToInvalidData || jobUuid == null) {
            return false;
        }
        EditCommitJob job = editCommitJobsByUuid.get(jobUuid);
        if (job == null) {
            return false;
        }
        if (job.getState() == EditCommitJob.State.APPLYING_WORLD) {
            return isExactApplyingEditCommit(job);
        }
        if (job.getState() != EditCommitJob.State.PREPARED) {
            return false;
        }
        if (!isValidPreparedEditCommitJob(job)
                || !isExactBaseEditCommitAuthority(job)) {
            conflictEditCommit(jobUuid, EditCommitJob.FailureCode.MALFORMED_DATA,
                    job.getControllerX(), job.getControllerY(),
                    job.getControllerZ(), currentTick);
            return false;
        }
        EditCommitJob applying = job.withState(
                EditCommitJob.State.APPLYING_WORLD, currentTick);
        editCommitJobsByUuid.put(jobUuid, applying);
        markDirty();
        FMLLog.info("[SIEGE_EDIT_COMMIT][APPLYING_WORLD] job=%s gate=%s %d->%d "
                        + "ops=%d",
                shortUuid(applying.getJobUuid()), shortUuid(applying.getGateUuid()),
                applying.getBaseRevision(), applying.getTargetRevision(),
                applying.getPhysicalOperations().size());
        if (applying.getPhysicalOperations().isEmpty()) {
            editCommitPhysicalVerifiedThisEpoch.add(applying.getJobUuid());
            logEditCommitPhysicalComplete(applying);
        }
        return true;
    }

    private void discoverLoadedEditCommitChunks(World world, int chunkBudget) {
        while (chunkBudget-- > 0 && !editCommitChunkDiscoveryQueue.isEmpty()) {
            Iterator<Long> iterator = editCommitChunkDiscoveryQueue.iterator();
            long key = iterator.next().longValue();
            iterator.remove();
            if (isChunkLoaded(world, key)
                    && hasPendingEditCommitWorkForChunk(key)) {
                enqueueEditCommitChunk(key);
            }
        }
    }

    private void advanceReadyEditCommitControllerPromotions(long currentTick) {
        List<EditCommitJob> jobs =
                new ArrayList<EditCommitJob>(editCommitJobsByUuid.values());
        Collections.sort(jobs, EDIT_COMMIT_JOB_COMPARATOR);
        int considered = 0;
        for (EditCommitJob job : jobs) {
            if (considered++ >= MAX_EDIT_COMMIT_JOB_DISCOVERY_PER_TICK) {
                return;
            }
            if (job.getState() == EditCommitJob.State.APPLYING_WORLD
                    && hasCurrentEditCommitPhysicalProof(job)) {

                if (isEditCommitDebugPaused(
                        job,
                        EditCommitDebugPausePoint.PHYSICAL_AFTER
                )) {
                    continue;
                }

                beginEditCommitControllerPromotion(job.getJobUuid(),
                        currentTick);
            }
        }
    }

    /** WSD-only APPLYING_WORLD -> PROMOTING_CONTROLLER boundary. */
    private boolean beginEditCommitControllerPromotion(UUID jobUuid,
            long currentTick) {
        if (readOnlyDueToInvalidData || jobUuid == null) {
            return false;
        }
        EditCommitJob job = editCommitJobsByUuid.get(jobUuid);
        if (job == null) {
            return false;
        }
        if (job.getState() == EditCommitJob.State.PROMOTING_CONTROLLER) {
            return isExactPromotionEditCommit(job);
        }
        if (job.getState() != EditCommitJob.State.APPLYING_WORLD
                || !hasCurrentEditCommitPhysicalProof(job)) {
            return false;
        }
        if (!isExactPromotionAuthority(job)) {
            conflictEditCommit(jobUuid, EditCommitJob.FailureCode.MALFORMED_DATA,
                    job.getControllerX(), job.getControllerY(),
                    job.getControllerZ(), currentTick);
            return false;
        }
        EditCommitJob promoting = job.withState(
                EditCommitJob.State.PROMOTING_CONTROLLER, currentTick);
        editCommitJobsByUuid.put(jobUuid, promoting);
        indexEditCommitControllerPromotion(promoting);
        markDirty();
        FMLLog.info("[SIEGE_EDIT_COMMIT][PROMOTING_CONTROLLER] job=%s gate=%s "
                        + "%d->%d controller=%d,%d,%d",
                shortUuid(promoting.getJobUuid()),
                shortUuid(promoting.getGateUuid()),
                promoting.getBaseRevision(), promoting.getTargetRevision(),
                promoting.getControllerX(), promoting.getControllerY(),
                promoting.getControllerZ());
        return true;
    }

    private void processEditCommitControllerPromotions(World world) {
        int attemptBudget = MAX_EDIT_COMMIT_CONTROLLER_ATTEMPTS_PER_TICK;
        int chunkBudget = MAX_EDIT_COMMIT_CONTROLLER_CHUNKS_PER_TICK;
        Set<UUID> attempted = new HashSet<UUID>();

        while (attemptBudget > 0 && chunkBudget-- > 0
                && !pendingEditCommitControllerChunkQueue.isEmpty()) {

            Iterator<Long> iterator =
                    pendingEditCommitControllerChunkQueue.iterator();
            long key = iterator.next().longValue();
            iterator.remove();

            LinkedHashSet<UUID> jobs =
                    editCommitControllerJobsByChunk.get(Long.valueOf(key));

            if (jobs == null || jobs.isEmpty()) {
                continue;
            }

            if (!isChunkLoaded(world, key)) {
                for (UUID jobUuid : jobs) {
                    logEditCommitControllerWaiting(
                            editCommitJobsByUuid.get(jobUuid)
                    );
                }
                continue;
            }

            int candidates = jobs.size();

            while (attemptBudget > 0
                    && candidates-- > 0
                    && !jobs.isEmpty()) {

                Iterator<UUID> jobIterator = jobs.iterator();
                UUID jobUuid = jobIterator.next();
                jobIterator.remove();

                EditCommitJob job = editCommitJobsByUuid.get(jobUuid);

                if (job == null
                        || !isEditCommitControllerPromotionState(
                        job.getState())) {
                    clearTransientEditCommitSchedulingForJob(jobUuid);
                    continue;
                }

                jobs.add(jobUuid);

                /*
                 * TEST-ONLY crash/recovery fixture.
                 *
                 * Freeze an exact PROMOTING_CONTROLLER transaction before
                 * processEditCommitControllerPromotion() can read or mutate
                 * the controller TileEntity.
                 */
                if (job.getState()
                        == EditCommitJob.State.PROMOTING_CONTROLLER
                        && isEditCommitDebugPaused(
                        job,
                        EditCommitDebugPausePoint.PROMOTING_CONTROLLER
                )) {
                    continue;
                }

                if (!attempted.add(jobUuid)
                        || !hasCurrentEditCommitPhysicalProof(job)
                        || (job.getState()
                        == EditCommitJob.State.PROMOTING_OWNERSHIP
                        && editCommitControllerVerifiedThisEpoch.contains(
                        jobUuid))) {
                    continue;
                }

                --attemptBudget;
                processEditCommitControllerPromotion(world, job);
            }

            if (hasPendingEditCommitControllerWorkForChunk(key)) {
                enqueueEditCommitControllerChunk(key);
            }
        }
    }

    private void processEditCommitControllerPromotion(World world,
            EditCommitJob job) {
        if (!isExactPromotionEditCommit(job)
                || !hasCurrentEditCommitPhysicalProof(job)) {
            conflictEditCommit(job.getJobUuid(),
                    EditCommitJob.FailureCode.CONTROLLER_MISMATCH,
                    job.getControllerX(), job.getControllerY(),
                    job.getControllerZ(), world.getTotalWorldTime());
            return;
        }
        if (world.getBlock(job.getControllerX(), job.getControllerY(),
                job.getControllerZ()) != SiegeRegistry.gateController) {
            conflictEditCommit(job.getJobUuid(),
                    EditCommitJob.FailureCode.CONTROLLER_MISMATCH,
                    job.getControllerX(), job.getControllerY(),
                    job.getControllerZ(), world.getTotalWorldTime());
            return;
        }
        TileEntity tileEntity = world.getTileEntity(job.getControllerX(),
                job.getControllerY(), job.getControllerZ());
        if (!(tileEntity instanceof TileEntitySiegeGate)) {
            conflictEditCommit(job.getJobUuid(),
                    EditCommitJob.FailureCode.CONTROLLER_MISMATCH,
                    job.getControllerX(), job.getControllerY(),
                    job.getControllerZ(), world.getTotalWorldTime());
            return;
        }
        TileEntitySiegeGate gate = (TileEntitySiegeGate)tileEntity;
        TileEntitySiegeGate.EditCommitTargetApplyResult result =
                gate.applyEditCommitTarget(
                        job.getGateUuid(), job.getBaseRevision(),
                        job.getTargetRevision(),
                        job.getOriginalSnapshot().getParts(),
                        job.getOriginalSnapshot().getLeftHinge(),
                        job.getOriginalSnapshot().getRightHinge(),
                        job.getOriginalSnapshot().getOrientation(),
                        job.getOriginalSnapshot().getOpeningDirection(),
                        job.getOriginalSnapshot().isBorderTextureEnabled(),
                        job.getTargetSnapshot().getParts(),
                        job.getTargetSnapshot().getLeftHinge(),
                        job.getTargetSnapshot().getRightHinge(),
                        job.getTargetSnapshot().getOpeningDirection(),
                        job.getTargetSnapshot().isBorderTextureEnabled()
                );
        if (result == TileEntitySiegeGate.EditCommitTargetApplyResult.UNEXPECTED) {
            conflictEditCommit(job.getJobUuid(),
                    EditCommitJob.FailureCode.CONTROLLER_MISMATCH,
                    job.getControllerX(), job.getControllerY(),
                    job.getControllerZ(), world.getTotalWorldTime());
            return;
        }
        editCommitControllerVerifiedThisEpoch.add(job.getJobUuid());
        if (result == TileEntitySiegeGate.EditCommitTargetApplyResult.BEFORE_APPLIED) {
            FMLLog.info("[SIEGE_EDIT_COMMIT][CONTROLLER_AFTER] job=%s gate=%s "
                            + "%d->%d controller=%d,%d,%d result=BEFORE_APPLIED",
                    shortUuid(job.getJobUuid()), shortUuid(job.getGateUuid()),
                    job.getBaseRevision(), job.getTargetRevision(),
                    job.getControllerX(), job.getControllerY(),
                    job.getControllerZ());
        } else if (editCommitControllerAfterLogged.add(job.getJobUuid())) {
            FMLLog.info("[SIEGE_EDIT_COMMIT][CONTROLLER_AFTER] job=%s gate=%s "
                            + "%d->%d controller=%d,%d,%d result=ALREADY_AFTER",
                    shortUuid(job.getJobUuid()), shortUuid(job.getGateUuid()),
                    job.getBaseRevision(), job.getTargetRevision(),
                    job.getControllerX(), job.getControllerY(),
                    job.getControllerZ());
        }
        if (job.getState() == EditCommitJob.State.PROMOTING_CONTROLLER) {

            if (isEditCommitDebugPaused(
                    job,
                    EditCommitDebugPausePoint.CONTROLLER_AFTER
            )) {
                return;
            }

            finishEditCommitControllerPromotion(job.getJobUuid(),
                    world.getTotalWorldTime());
        }
    }

    /** WSD-only PROMOTING_CONTROLLER -> PROMOTING_OWNERSHIP boundary. */
    private boolean finishEditCommitControllerPromotion(UUID jobUuid,
            long currentTick) {
        EditCommitJob job = editCommitJobsByUuid.get(jobUuid);
        if (job == null || job.getState()
                != EditCommitJob.State.PROMOTING_CONTROLLER
                || !hasCurrentEditCommitPhysicalProof(job)
                || !isExactPromotionAuthority(job)) {
            return false;
        }
        EditCommitJob ownershipPending = job.withState(
                EditCommitJob.State.PROMOTING_OWNERSHIP, currentTick);
        editCommitJobsByUuid.put(jobUuid, ownershipPending);
        markDirty();
        FMLLog.info("[SIEGE_EDIT_COMMIT][PROMOTING_OWNERSHIP] job=%s gate=%s "
                        + "%d->%d; controller target confirmed, ownership deferred.",
                shortUuid(ownershipPending.getJobUuid()),
                shortUuid(ownershipPending.getGateUuid()),
                ownershipPending.getBaseRevision(),
                ownershipPending.getTargetRevision());
        return true;
    }

    /**
     * Phase 4F's WSD-only completion boundary.  The current recovery epoch must
     * have independently re-established both world AFTER and controller AFTER;
     * persisted operation hints and the PROMOTING_OWNERSHIP label are not proof.
     */
    private void processEditCommitOwnershipPromotions(long currentTick) {
        List<EditCommitJob> jobs =
                new ArrayList<EditCommitJob>(editCommitJobsByUuid.values());
        Collections.sort(jobs, EDIT_COMMIT_JOB_COMPARATOR);
        int considered = 0;
        for (EditCommitJob job : jobs) {
            if (considered++ >= MAX_EDIT_COMMIT_JOB_DISCOVERY_PER_TICK) {
                return;
            }
            if (job.getState() != EditCommitJob.State.PROMOTING_OWNERSHIP
                    || !hasCurrentEditCommitPhysicalProof(job)
                    || !editCommitControllerVerifiedThisEpoch.contains(
                            job.getJobUuid())) {
                continue;
            }
            if (isEditCommitDebugPaused(
                    job,
                    EditCommitDebugPausePoint.PROMOTING_OWNERSHIP
            )) {
                continue;
            }
            EditCommitOwnershipPlan plan =
                    buildEditCommitOwnershipBeforePlan(job);
            if (plan == null) {
                conflictEditCommit(job.getJobUuid(),
                        EditCommitJob.FailureCode.MALFORMED_DATA,
                        job.getControllerX(), job.getControllerY(),
                        job.getControllerZ(), currentTick);
                continue;
            }
            promoteEditCommitOwnership(plan, currentTick);
        }
    }

    /**
     * Builds the complete N+1 durable target before any live ownership index is
     * changed.  Null is deliberately a fail-closed OWNERSHIP_UNEXPECTED result.
     */
    private EditCommitOwnershipPlan buildEditCommitOwnershipBeforePlan(
            EditCommitJob job
    ) {
        if (job == null || job.getState()
                != EditCommitJob.State.PROMOTING_OWNERSHIP
                || !hasCurrentEditCommitPhysicalProof(job)
                || !editCommitControllerVerifiedThisEpoch.contains(
                        job.getJobUuid())
                || !isExactPromotionAuthority(job)) {
            return null;
        }
        ControllerRecord base = controllersByUuid.get(job.getGateUuid());
        if (base == null || base.lastGateState != GateState.CLOSED
                || controllersByPosition.get(base.controllerPosition()) != base
                || !hasExactControllerPartIndexes(base,
                        job.getBaseRevision())
                || !hasNoExtraGateOwnership(job.getGateUuid(), base)) {
            return null;
        }

        List<PartRecord> targetParts;
        try {
            targetParts = buildPartRecords(job.getControllerX(),
                    job.getControllerY(), job.getControllerZ(),
                    job.getTargetRevision(),
                    job.getTargetSnapshot().getParts());
        } catch (RuntimeException ignored) {
            return null;
        }
        ControllerRecord target = new ControllerRecord(job.getGateUuid(),
                job.getDimension(), job.getControllerX(), job.getControllerY(),
                job.getControllerZ(), job.getTargetRevision(),
                ControllerStatus.ACTIVE, GateState.CLOSED, targetParts);
        if (!target.hasEquivalentParts(job.getTargetSnapshot().getParts())
                || !hasCanonicalTargetPartRecords(target,
                        job.getTargetRevision())) {
            return null;
        }

        Set<BlockPosition> basePositions = partPositions(base.parts);
        Set<BlockPosition> targetPositions = partPositions(target.parts);
        if (basePositions.size() != base.parts.size()
                || targetPositions.size() != target.parts.size()) {
            return null;
        }
        Set<BlockPosition> addPositions = operationPositions(job,
                EditCommitJob.OperationKind.ADD);
        Set<BlockPosition> removePositions = operationPositions(job,
                EditCommitJob.OperationKind.REMOVE);
        Set<BlockPosition> targetOnly = new HashSet<BlockPosition>(targetPositions);
        targetOnly.removeAll(basePositions);
        Set<BlockPosition> baseOnly = new HashSet<BlockPosition>(basePositions);
        baseOnly.removeAll(targetPositions);
        if (!targetOnly.equals(addPositions) || !baseOnly.equals(removePositions)
                || addPositions.size() + removePositions.size()
                != job.getPhysicalOperations().size()) {
            return null;
        }
        for (BlockPosition position : targetPositions) {
            TargetReservation reservation = targetReservationsByPosition.get(
                    position);
            if ((addPositions.contains(position) && reservation == null)
                    || (!addPositions.contains(position)
                    && reservation != null)) {
                return null;
            }
        }
        for (BlockPosition position : targetOnly) {
            if (ownersByPart.containsKey(position)) {
                return null;
            }
        }
        if (totalOwnershipParts - base.parts.size() + target.parts.size() < 0
                || totalOwnershipParts - base.parts.size() + target.parts.size()
                > MAX_TOTAL_OWNERSHIP_PARTS) {
            return null;
        }

        List<TargetReservation> reservations =
                exactEditCommitAddReservations(job, addPositions);
        if (reservations == null) {
            return null;
        }
        return new EditCommitOwnershipPlan(job, base, target, reservations,
                addPositions.size(), removePositions.size());
    }

    private boolean hasExactControllerPartIndexes(ControllerRecord controller,
            int expectedRevision) {
        Set<BlockPosition> positions = partPositions(controller.parts);
        if (positions.size() != controller.parts.size()) {
            return false;
        }
        for (PartRecord part : controller.parts) {
            PartRecordRef ref = ownersByPart.get(part.absolutePosition());
            if (part.structureRevision != expectedRevision || ref == null
                    || ref.controller != controller || ref.part != part) {
                return false;
            }
            List<PartRecordRef> chunkOwners = ownersByChunk.get(Long.valueOf(
                    chunkKey(part.absoluteX >> 4, part.absoluteZ >> 4)));
            if (chunkOwners == null || !chunkOwners.contains(ref)) {
                return false;
            }
        }
        for (Map.Entry<BlockPosition, PartRecordRef> entry
                : ownersByPart.entrySet()) {
            PartRecordRef ref = entry.getValue();
            if (ref.controller == controller
                    && (!positions.contains(entry.getKey())
                    || ref.part == null
                    || !entry.getKey().equals(ref.part.absolutePosition()))) {
                return false;
            }
        }
        for (List<PartRecordRef> chunkOwners : ownersByChunk.values()) {
            for (PartRecordRef ref : chunkOwners) {
                if (ref.controller == controller
                        && (!positions.contains(ref.part.absolutePosition())
                        || ownersByPart.get(ref.part.absolutePosition()) != ref)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean hasCanonicalTargetPartRecords(ControllerRecord target,
            int expectedRevision) {
        String expectedBlock = Block.blockRegistry.getNameForObject(
                SiegeRegistry.gatePart);
        if (expectedBlock == null) {
            return false;
        }
        for (PartRecord part : target.parts) {
            if (part.structureRevision != expectedRevision
                    || !expectedBlock.equals(part.expectedBlockName)
                    || part.expectedMetadata != 0 || part.leaf == null
                    || part.sourceBlockName == null) {
                return false;
            }
        }
        return true;
    }

    /** Rejects stale records/index references for this GateUUID, not just parts
     * reachable from the current ControllerRecord. */
    private boolean hasNoExtraGateOwnership(UUID gateUuid,
            ControllerRecord expectedController) {
        Set<BlockPosition> expected = partPositions(expectedController.parts);
        for (Map.Entry<BlockPosition, PartRecordRef> entry
                : ownersByPart.entrySet()) {
            PartRecordRef ref = entry.getValue();
            if (ref.controller.gateUuid.equals(gateUuid)
                    && (ref.controller != expectedController
                    || !expected.contains(entry.getKey()))) {
                return false;
            }
        }
        for (List<PartRecordRef> chunkOwners : ownersByChunk.values()) {
            for (PartRecordRef ref : chunkOwners) {
                if (ref.controller.gateUuid.equals(gateUuid)
                        && (ref.controller != expectedController
                        || !expected.contains(ref.part.absolutePosition())
                        || ownersByPart.get(ref.part.absolutePosition()) != ref)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static Set<BlockPosition> partPositions(
            Collection<PartRecord> parts
    ) {
        Set<BlockPosition> positions = new HashSet<BlockPosition>();
        for (PartRecord part : parts) {
            positions.add(part.absolutePosition());
        }
        return positions;
    }

    private static Set<BlockPosition> operationPositions(EditCommitJob job,
            EditCommitJob.OperationKind kind) {
        Set<BlockPosition> positions = new HashSet<BlockPosition>();
        for (EditCommitJob.PhysicalOperation operation
                : job.getPhysicalOperations()) {
            if (operation.getKind() == kind) {
                positions.add(new BlockPosition(operation.getX(),
                        operation.getY(), operation.getZ()));
            }
        }
        return positions;
    }

    private List<TargetReservation> exactEditCommitAddReservations(
            EditCommitJob job, Set<BlockPosition> addPositions
    ) {
        Set<BlockPosition> reverse = targetReservationPositionsByJob.get(
                job.getJobUuid());
        if (addPositions.isEmpty()) {
            if (reverse != null && !reverse.isEmpty()) {
                return null;
            }
        } else if (reverse == null || !reverse.equals(addPositions)) {
            return null;
        }
        List<TargetReservation> reservations =
                new ArrayList<TargetReservation>(addPositions.size());
        for (BlockPosition position : addPositions) {
            TargetReservation reservation = targetReservationsByPosition.get(
                    position);
            if (reservation == null
                    || !reservation.getJobUuid().equals(job.getJobUuid())
                    || !reservationMatchesJob(reservation, job)
                    || reservation.getX() != position.x
                    || reservation.getY() != position.y
                    || reservation.getZ() != position.z) {
                return null;
            }
            reservations.add(reservation);
        }
        int matching = 0;
        for (TargetReservation reservation
                : targetReservationsByPosition.values()) {
            if (reservation.getJobUuid().equals(job.getJobUuid())) {
                ++matching;
                if (!addPositions.contains(new BlockPosition(
                        reservation.getX(), reservation.getY(),
                        reservation.getZ()))) {
                    return null;
                }
            }
        }
        return matching == addPositions.size() ? reservations : null;
    }

    /**
     * Installs a complete prevalidated ownership replacement and COMPLETE in one
     * synchronized WSD mutation.  It never reads or writes world/TileEntity.
     */
    private boolean promoteEditCommitOwnership(EditCommitOwnershipPlan plan,
            long currentTick) {
        EditCommitJob job = plan.job;
        ControllerRecord priorController = plan.baseController;
        int priorTotal = totalOwnershipParts;
        EditCommitJob priorJob = editCommitJobsByUuid.get(job.getJobUuid());
        try {
            if (priorJob != job) {
                throw new IllegalStateException("EditCommit job changed");
            }
            removeControllerIndex(priorController);
            controllersByUuid.put(job.getGateUuid(), plan.targetController);
            totalOwnershipParts = priorTotal - priorController.parts.size()
                    + plan.targetController.parts.size();
            if (!indexController(plan.targetController)) {
                throw new IllegalStateException("Target ownership index collision");
            }
            for (TargetReservation reservation : plan.reservations) {
                BlockPosition position = new BlockPosition(reservation.getX(),
                        reservation.getY(), reservation.getZ());
                if (targetReservationsByPosition.remove(position) != reservation) {
                    throw new IllegalStateException("Reservation changed");
                }
            }
            targetReservationPositionsByJob.remove(job.getJobUuid());
            EditCommitJob complete = job.withState(EditCommitJob.State.COMPLETE,
                    currentTick);
            editCommitJobsByUuid.put(job.getJobUuid(), complete);
            UUID activeJobUuid = activeEditCommitJobUuidByGateUuid.remove(
                    job.getGateUuid());
            if (!job.getJobUuid().equals(activeJobUuid)) {
                throw new IllegalStateException("Active edit index changed");
            }
            clearTransientEditCommitSchedulingForJob(job.getJobUuid());
            queueEditCommitArchival(complete);
            markDirty();
            FMLLog.info("[SIEGE_EDIT_COMMIT][OWNERSHIP_COMPLETE] job=%s gate=%s "
                            + "%d->%d parts=%d adds=%d removes=%d reservations=%d",
                    shortUuid(job.getJobUuid()), shortUuid(job.getGateUuid()),
                    job.getBaseRevision(), job.getTargetRevision(),
                    plan.targetController.parts.size(), plan.addCount,
                    plan.removeCount, plan.reservations.size());
            return true;
        } catch (RuntimeException ignored) {
            removeControllerIndex(plan.targetController);
            controllersByUuid.put(job.getGateUuid(), priorController);
            totalOwnershipParts = priorTotal;
            indexController(priorController);
            for (TargetReservation reservation : plan.reservations) {
                BlockPosition position = new BlockPosition(reservation.getX(),
                        reservation.getY(), reservation.getZ());
                targetReservationsByPosition.put(position, reservation);
            }
            if (!plan.reservations.isEmpty()) {
                targetReservationPositionsByJob.put(job.getJobUuid(),
                        new HashSet<BlockPosition>(operationPositions(job,
                                EditCommitJob.OperationKind.ADD)));
            }
            editCommitJobsByUuid.put(job.getJobUuid(), job);
            activeEditCommitJobUuidByGateUuid.put(job.getGateUuid(),
                    job.getJobUuid());
            conflictEditCommit(job.getJobUuid(),
                    EditCommitJob.FailureCode.MALFORMED_DATA,
                    job.getControllerX(), job.getControllerY(),
                    job.getControllerZ(), currentTick);
            return false;
        }
    }

    private void queueEditCommitArchival(EditCommitJob job) {
        if (job != null && job.getState() == EditCommitJob.State.COMPLETE
                && !blockedEditCommitArchivalJobUuids.contains(
                        job.getJobUuid())) {
            pendingEditCommitArchivalJobUuids.add(job.getJobUuid());
        }
    }

    private void discoverEditCommitArchivalJobs() {
        if (editCommitArchivalDiscoveryComplete) {
            return;
        }
        if (editCommitArchivalDiscoverySnapshot == null) {
            List<EditCommitJob> jobs =
                    new ArrayList<EditCommitJob>(editCommitJobsByUuid.values());
            Collections.sort(jobs, EDIT_COMMIT_JOB_COMPARATOR);
            editCommitArchivalDiscoverySnapshot = new ArrayList<UUID>(
                    jobs.size());
            for (EditCommitJob job : jobs) {
                editCommitArchivalDiscoverySnapshot.add(job.getJobUuid());
            }
            editCommitArchivalDiscoveryCursor = 0;
        }
        int budget = MAX_EDIT_COMMIT_ARCHIVAL_DISCOVERY_PER_TICK;
        while (budget-- > 0 && editCommitArchivalDiscoveryCursor
                < editCommitArchivalDiscoverySnapshot.size()) {
            UUID jobUuid = editCommitArchivalDiscoverySnapshot.get(
                    editCommitArchivalDiscoveryCursor++);
            queueEditCommitArchival(editCommitJobsByUuid.get(jobUuid));
        }
        if (editCommitArchivalDiscoveryCursor
                >= editCommitArchivalDiscoverySnapshot.size()) {
            editCommitArchivalDiscoverySnapshot = null;
            editCommitArchivalDiscoveryCursor = 0;
            editCommitArchivalDiscoveryComplete = true;
        }
    }

    private void processEditCommitArchival(long currentTick) {
        int attempts = MAX_EDIT_COMMIT_ARCHIVAL_ATTEMPTS_PER_TICK;
        while (attempts-- > 0 && !pendingEditCommitArchivalJobUuids.isEmpty()) {
            Iterator<UUID> iterator = pendingEditCommitArchivalJobUuids.iterator();
            UUID jobUuid = iterator.next();
            iterator.remove();
            if (blockedEditCommitArchivalJobUuids.contains(jobUuid)) {
                continue;
            }
            EditCommitJob job = editCommitJobsByUuid.get(jobUuid);
            if (job != null
                    && isEditCommitDebugPaused(
                    job,
                    EditCommitDebugPausePoint.COMPLETE
            )) {
                pendingEditCommitArchivalJobUuids.add(jobUuid);
                continue;
            }
            if (classifyEditCommitCleanup(jobUuid, null)
                    != EditCommitCleanupState.CLEANUP_BEFORE) {
                if (job != null) {
                    blockedEditCommitArchivalJobUuids.add(jobUuid);
                    FMLLog.warning("[SIEGE_EDIT_COMMIT][ARCHIVE_BLOCKED] job=%s "
                                    + "gate=%s state=%s; evidence was retained.",
                            shortUuid(job.getJobUuid()),
                            shortUuid(job.getGateUuid()), job.getState());
                }
                continue;
            }
            archiveCompletedEditCommit(jobUuid, currentTick);
        }
    }

    private EditCommitCleanupState classifyEditCommitCleanup(UUID jobUuid,
            CompletedEditCommitTombstone expectedAfter) {
        EditCommitJob job = editCommitJobsByUuid.get(jobUuid);
        CompletedEditCommitTombstone tombstone = findEditCommitTombstone(jobUuid);
        if (job == null) {
            return tombstone != null && expectedAfter != null
                    && tombstoneMatches(tombstone, expectedAfter)
                    && !hasEditCommitReservation(jobUuid)
                    && !hasActiveEditCommitJobUuid(jobUuid)
                    ? EditCommitCleanupState.CLEANUP_AFTER
                    : EditCommitCleanupState.CLEANUP_UNEXPECTED;
        }
        return job.getState() == EditCommitJob.State.COMPLETE
                && tombstone == null && isHistoricalCompleteEditCommit(job)
                ? EditCommitCleanupState.CLEANUP_BEFORE
                : EditCommitCleanupState.CLEANUP_UNEXPECTED;
    }

    /** WSD-only COMPLETE -> tombstone + full-job removal boundary. */
    private boolean archiveCompletedEditCommit(UUID jobUuid, long currentTick) {
        EditCommitJob job = editCommitJobsByUuid.get(jobUuid);
        if (classifyEditCommitCleanup(jobUuid, null)
                != EditCommitCleanupState.CLEANUP_BEFORE || job == null
                || totalEditCommitOperations < job.getPhysicalOperations().size()) {
            return false;
        }
        CompletedEditCommitTombstone tombstone =
                new CompletedEditCommitTombstone(job.getJobUuid(),
                        job.getGateUuid(), job.getDimension(),
                        job.getControllerX(), job.getControllerY(),
                        job.getControllerZ(), job.getBaseRevision(),
                        job.getTargetRevision(), job.getUpdatedTick());
        if (!isValidCompletedTombstone(tombstone)
                || (boundDimension != Integer.MIN_VALUE
                && tombstone.getDimension() != boundDimension)
                || completedEditCommitTombstones.size()
                > MAX_COMPLETED_EDIT_COMMIT_TOMBSTONES) {
            return false;
        }
        CompletedEditCommitTombstone evicted = null;
        if (completedEditCommitTombstones.size()
                == MAX_COMPLETED_EDIT_COMMIT_TOMBSTONES) {
            List<CompletedEditCommitTombstone> sorted =
                    new ArrayList<CompletedEditCommitTombstone>(
                            completedEditCommitTombstones);
            Collections.sort(sorted, EDIT_COMMIT_TOMBSTONE_COMPARATOR);
            evicted = sorted.get(0);
        }
        int previousOperationCount = totalEditCommitOperations;
        boolean evictedRemoved = false;
        boolean tombstoneAdded = false;
        boolean jobRemoved = false;
        try {
            if (evicted != null) {
                evictedRemoved = completedEditCommitTombstones.remove(evicted);
                if (!evictedRemoved) {
                    throw new IllegalStateException("Tombstone eviction changed");
                }
            }
            completedEditCommitTombstones.add(tombstone);
            tombstoneAdded = true;
            if (editCommitJobsByUuid.remove(jobUuid) != job) {
                throw new IllegalStateException("Completed job changed");
            }
            jobRemoved = true;
            totalEditCommitOperations = previousOperationCount
                    - job.getPhysicalOperations().size();
            clearTransientEditCommitSchedulingForJob(jobUuid);
            if (classifyEditCommitCleanup(jobUuid, tombstone)
                    != EditCommitCleanupState.CLEANUP_AFTER) {
                throw new IllegalStateException("Archived state was not exact");
            }
            markDirty();
            if (evicted != null) {
                FMLLog.info("[SIEGE_EDIT_COMMIT][TOMBSTONE_EVICT] job=%s "
                                + "gate=%s %d->%d completed=%d",
                        shortUuid(evicted.getJobUuid()),
                        shortUuid(evicted.getGateUuid()),
                        evicted.getBaseRevision(), evicted.getTargetRevision(),
                        evicted.getCompletedTick());
            }
            FMLLog.info("[SIEGE_EDIT_COMMIT][ARCHIVED] job=%s gate=%s %d->%d "
                            + "completed=%d operations=%d tombstones=%d",
                    shortUuid(job.getJobUuid()), shortUuid(job.getGateUuid()),
                    job.getBaseRevision(), job.getTargetRevision(),
                    job.getUpdatedTick(), job.getPhysicalOperations().size(),
                    completedEditCommitTombstones.size());
            return true;
        } catch (RuntimeException ignored) {
            if (jobRemoved) {
                editCommitJobsByUuid.put(jobUuid, job);
            }
            totalEditCommitOperations = previousOperationCount;
            if (tombstoneAdded) {
                completedEditCommitTombstones.remove(tombstone);
            }
            if (evictedRemoved) {
                completedEditCommitTombstones.add(evicted);
            }
            queueEditCommitArchival(job);
            return false;
        }
    }

    private boolean hasEditCommitReservation(UUID jobUuid) {
        Set<BlockPosition> reverse = targetReservationPositionsByJob.get(jobUuid);
        if (reverse != null && !reverse.isEmpty()) {
            return true;
        }
        for (TargetReservation reservation
                : targetReservationsByPosition.values()) {
            if (reservation.getJobUuid().equals(jobUuid)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasActiveEditCommitJobUuid(UUID jobUuid) {
        for (UUID activeJobUuid : activeEditCommitJobUuidByGateUuid.values()) {
            if (jobUuid.equals(activeJobUuid)) {
                return true;
            }
        }
        return false;
    }

    private CompletedEditCommitTombstone findEditCommitTombstone(UUID jobUuid) {
        for (CompletedEditCommitTombstone tombstone
                : completedEditCommitTombstones) {
            if (tombstone.getJobUuid().equals(jobUuid)) {
                return tombstone;
            }
        }
        return null;
    }

    private static boolean tombstoneMatches(
            CompletedEditCommitTombstone first,
            CompletedEditCommitTombstone second
    ) {
        return first != null && second != null
                && first.getJobUuid().equals(second.getJobUuid())
                && first.getGateUuid().equals(second.getGateUuid())
                && first.getDimension() == second.getDimension()
                && first.getControllerX() == second.getControllerX()
                && first.getControllerY() == second.getControllerY()
                && first.getControllerZ() == second.getControllerZ()
                && first.getBaseRevision() == second.getBaseRevision()
                && first.getTargetRevision() == second.getTargetRevision()
                && first.getCompletedTick() == second.getCompletedTick();
    }

    private static boolean isValidCompletedTombstone(
            CompletedEditCommitTombstone tombstone
    ) {
        return tombstone != null && tombstone.getJobUuid() != null
                && tombstone.getGateUuid() != null
                && tombstone.getBaseRevision() > 0
                && tombstone.getBaseRevision() != Integer.MAX_VALUE
                && tombstone.getTargetRevision()
                == tombstone.getBaseRevision() + 1
                && tombstone.getCompletedTick() >= 0L
                && isSanePosition(tombstone.getControllerX(),
                        tombstone.getControllerY(), tombstone.getControllerZ());
    }

    private boolean hasCurrentEditCommitPhysicalProof(EditCommitJob job) {
        return job != null && (job.getPhysicalOperations().isEmpty()
                || editCommitPhysicalVerifiedThisEpoch.contains(
                        job.getJobUuid()));
    }

    private boolean isExactPromotionAuthority(EditCommitJob job) {
        return job != null && job.getBaseRevision() > 0
                && job.getTargetRevision() == job.getBaseRevision() + 1
                && snapshotMatchesJob(job.getOriginalSnapshot(),
                        job.getGateUuid(), job.getDimension(),
                        job.getControllerX(), job.getControllerY(),
                        job.getControllerZ(), job.getBaseRevision())
                && snapshotMatchesJob(job.getTargetSnapshot(),
                        job.getGateUuid(), job.getDimension(),
                        job.getControllerX(), job.getControllerY(),
                        job.getControllerZ(), job.getTargetRevision())
                && isEditCommitControllerConsistent(job)
                && isValidEditCommitDelta(job.getOriginalSnapshot(),
                job.getTargetSnapshot(), job.getPhysicalOperations())
                && areEditCommitReservationsConsistent(job)
                && isExactPreOwnershipEditCommit(job);
    }

    private boolean isExactPromotionEditCommit(EditCommitJob job) {
        return job != null && isEditCommitControllerPromotionState(
                job.getState()) && isExactPromotionAuthority(job);
    }

    private static boolean isEditCommitControllerPromotionState(
            EditCommitJob.State state
    ) {
        return state == EditCommitJob.State.PROMOTING_CONTROLLER
                || state == EditCommitJob.State.PROMOTING_OWNERSHIP;
    }

    private void indexEditCommitControllerPromotion(EditCommitJob job) {
        if (job == null || !isEditCommitControllerPromotionState(
                job.getState()) || editCommitControllerChunkByJob.containsKey(
                job.getJobUuid())) {
            return;
        }
        long key = chunkKey(job.getControllerX() >> 4,
                job.getControllerZ() >> 4);
        LinkedHashSet<UUID> jobs = editCommitControllerJobsByChunk.get(
                Long.valueOf(key));
        if (jobs == null) {
            jobs = new LinkedHashSet<UUID>();
            editCommitControllerJobsByChunk.put(Long.valueOf(key), jobs);
        }
        jobs.add(job.getJobUuid());
        editCommitControllerChunkByJob.put(job.getJobUuid(), Long.valueOf(key));
        editCommitControllerChunkDiscoveryQueue.add(Long.valueOf(key));
    }

    private void discoverLoadedEditCommitControllerChunks(World world,
            int chunkBudget) {
        while (chunkBudget-- > 0
                && !editCommitControllerChunkDiscoveryQueue.isEmpty()) {
            Iterator<Long> iterator = editCommitControllerChunkDiscoveryQueue.iterator();
            long key = iterator.next().longValue();
            iterator.remove();
            if (isChunkLoaded(world, key)
                    && hasPendingEditCommitControllerWorkForChunk(key)) {
                enqueueEditCommitControllerChunk(key);
            }
        }
    }

    private void enqueueEditCommitControllerChunk(long key) {
        if (hasPendingEditCommitControllerWorkForChunk(key)) {
            pendingEditCommitControllerChunkQueue.add(Long.valueOf(key));
        }
    }

    private boolean hasPendingEditCommitControllerWorkForChunk(long key) {
        LinkedHashSet<UUID> jobs = editCommitControllerJobsByChunk.get(
                Long.valueOf(key));
        if (jobs == null || jobs.isEmpty()) {
            return false;
        }
        for (UUID jobUuid : jobs) {
            EditCommitJob job = editCommitJobsByUuid.get(jobUuid);
            if (job != null && (job.getState()
                    == EditCommitJob.State.PROMOTING_CONTROLLER
                    || (job.getState()
                    == EditCommitJob.State.PROMOTING_OWNERSHIP
                    && !editCommitControllerVerifiedThisEpoch.contains(
                            jobUuid)))) {
                return true;
            }
        }
        return false;
    }

    private void logEditCommitControllerWaiting(EditCommitJob job) {
        if (job == null || !editCommitControllerWaitingLogged.add(
                job.getJobUuid())) {
            return;
        }
        FMLLog.info("[SIEGE_EDIT_COMMIT][CONTROLLER_WAIT] job=%s gate=%s "
                        + "%d->%d controller=%d,%d,%d",
                shortUuid(job.getJobUuid()), shortUuid(job.getGateUuid()),
                job.getBaseRevision(), job.getTargetRevision(),
                job.getControllerX(), job.getControllerY(),
                job.getControllerZ());
    }

    private int processEditCommitChunk(World world, long key, int budget,
                                       Map<UUID, Integer> perJobCounts, boolean[] progressDirty) {
        LinkedHashSet<EditCommitOperationRef> refs =
                pendingEditCommitOperationsByChunk.get(key);
        if (refs == null || refs.isEmpty()) {
            return 0;
        }

        int processed = 0;
        int candidates = refs.size();

        while (processed < budget && candidates-- > 0 && !refs.isEmpty()) {
            Iterator<EditCommitOperationRef> iterator = refs.iterator();
            EditCommitOperationRef ref = iterator.next();
            iterator.remove();

            EditCommitJob job = editCommitJobsByUuid.get(ref.jobUuid);

            if (job == null || job.getState() == EditCommitJob.State.CONFLICT) {
                removeEditCommitOperationRef(ref);
                continue;
            }

            if (isEditCommitDebugPaused(
                    job,
                    EditCommitDebugPausePoint.PREPARED
            )) {
                refs.add(ref);
                continue;
            }

            if (isEditCommitDebugPaused(
                    job,
                    EditCommitDebugPausePoint.APPLYING_WORLD
            )) {
                refs.add(ref);
                continue;
            }

            Integer current = perJobCounts.get(ref.jobUuid);
            if (current != null && current.intValue()
                    >= MAX_EDIT_COMMIT_OPERATIONS_PER_JOB_PER_TICK) {
                refs.add(ref);
                continue;
            }

            perJobCounts.put(ref.jobUuid, Integer.valueOf(
                    current == null ? 1 : current.intValue() + 1));

            ++processed;
            processEditCommitOperation(world, job, ref, progressDirty);
        }

        if (refs.isEmpty()) {
            pendingEditCommitOperationsByChunk.remove(Long.valueOf(key));
        }

        return processed;
    }

    private void processEditCommitOperation(
            World world,
            EditCommitJob job,
            EditCommitOperationRef ref,
            boolean[] progressDirty
    ) {
        if (!isExactPreOwnershipEditCommit(job)
                || !operationMatchesReference(job, ref)) {

            conflictEditCommit(
                    job.getJobUuid(),
                    EditCommitJob.FailureCode.CONTROLLER_MISMATCH,
                    ref.operation.getX(),
                    ref.operation.getY(),
                    ref.operation.getZ(),
                    world.getTotalWorldTime()
            );

            return;
        }

        OperationWorldState state =
                classifyEditCommitOperation(
                        world,
                        job,
                        ref.operation
                );

        if (state == OperationWorldState.UNEXPECTED) {
            conflictEditCommit(
                    job.getJobUuid(),
                    EditCommitJob.FailureCode.WORLD_STATE_MISMATCH,
                    ref.operation.getX(),
                    ref.operation.getY(),
                    ref.operation.getZ(),
                    world.getTotalWorldTime()
            );

            return;
        }

        if (state == OperationWorldState.EXPECTED_BEFORE) {
            if (!applyEditCommitOperation(
                    world,
                    job,
                    ref.operation
            )) {
                conflictEditCommit(
                        job.getJobUuid(),
                        EditCommitJob.FailureCode.WORLD_STATE_MISMATCH,
                        ref.operation.getX(),
                        ref.operation.getY(),
                        ref.operation.getZ(),
                        world.getTotalWorldTime()
                );

                return;
            }

        } else if (state
                == OperationWorldState.EXPECTED_AFTER) {

            /*
             * Crash recovery case:
             *
             * The source block may already have been restored before the crash,
             * while the durable operation still says PENDING. Reapply the stored
             * TileEntity snapshot before acknowledging the operation.
             */
            if (!restoreEditCommitSourceTileEntityIfNeeded(
                    world,
                    job,
                    ref.operation
            )) {
                conflictEditCommit(
                        job.getJobUuid(),
                        EditCommitJob.FailureCode.WORLD_STATE_MISMATCH,
                        ref.operation.getX(),
                        ref.operation.getY(),
                        ref.operation.getZ(),
                        world.getTotalWorldTime()
                );

                return;
            }
        }

        EditCommitJob current =
                editCommitJobsByUuid.get(
                        job.getJobUuid()
                );

        if (current == null
                || !isEditCommitPhysicalReconciliationState(
                current.getState()
        )) {
            return;
        }

        editCommitJobsByUuid.put(
                current.getJobUuid(),
                current.withOperationApplied(
                        ref.operation.getOrdinal(),
                        world.getTotalWorldTime()
                )
        );

        removeEditCommitOperationRef(ref);

        progressDirty[0] = true;

        Integer remaining =
                remainingEditCommitOperationsByJob.get(
                        current.getJobUuid()
                );

        logEditCommitProgress(
                editCommitJobsByUuid.get(
                        current.getJobUuid()
                ),
                remaining
        );

        if (remaining != null
                && remaining.intValue() == 0) {

            editCommitPhysicalVerifiedThisEpoch.add(
                    current.getJobUuid()
            );

            logEditCommitPhysicalComplete(
                    editCommitJobsByUuid.get(
                            current.getJobUuid()
                    )
            );
        }
    }

    private OperationWorldState classifyEditCommitOperation(World world,
            EditCommitJob job, EditCommitJob.PhysicalOperation operation) {
        if (operation.getKind() == EditCommitJob.OperationKind.ADD) {
            if (!hasExactAddAuthority(job, operation)) {
                return OperationWorldState.UNEXPECTED;
            }
        } else if (!hasExactRemoveAuthority(job, operation)) {
            return OperationWorldState.UNEXPECTED;
        }
        if (matchesEditCommitWorldAfterOperation(
                world,
                operation
        )) {
            return OperationWorldState.EXPECTED_AFTER;
        }
        if (!matchesEditCommitWorld(world, operation,
                operation.getExpectedBeforeBlock(),
                operation.getExpectedBeforeMetadata())) {
            return OperationWorldState.UNEXPECTED;
        }
        if (operation.getKind() == EditCommitJob.OperationKind.ADD
                && !GateSourceBlockValidator.isValid(world, operation.getX(),
                        operation.getY(), operation.getZ())) {
            return OperationWorldState.UNEXPECTED;
        }
        return OperationWorldState.EXPECTED_BEFORE;
    }

    private boolean applyEditCommitOperation(
            World world,
            EditCommitJob job,
            EditCommitJob.PhysicalOperation operation
    ) {
        try {
            if (operation.getKind()
                    == EditCommitJob.OperationKind.ADD) {

                boolean placed =
                        world.setBlock(
                                operation.getX(),
                                operation.getY(),
                                operation.getZ(),
                                SiegeRegistry.gatePart,
                                0,
                                3
                        );

                if (!placed) {
                    return false;
                }

            } else {
                Block after =
                        Block.getBlockFromName(
                                operation.getExpectedAfterBlock()
                        );

                if (after == null) {
                    return false;
                }

                if (after == Blocks.air) {
                    world.setBlockToAir(
                            operation.getX(),
                            operation.getY(),
                            operation.getZ()
                    );

                } else {
                    GatePartData originalPart =
                            findOriginalEditCommitPart(
                                    job,
                                    operation
                            );

                    if (originalPart == null
                            || !operation.isSourceRestorable()
                            || !originalPart.hasStoredSourceBlock()
                            || !operation.getSourceBlock().equals(
                            originalPart.getSourceBlockName()
                    )
                            || operation.getSourceMetadata()
                            != originalPart.getSourceMetadata()
                            || !isPersistedRestorationDefinitionUsable(
                            originalPart.getSourceBlockName(),
                            originalPart.getSourceMetadata(),
                            originalPart.getSourceTileEntityNbt(),
                            true,
                            operation.getExpectedAfterBlock(),
                            operation.getExpectedAfterMetadata()
                    )) {
                        return false;
                    }

                    boolean placed =
                            world.setBlock(
                                    operation.getX(),
                                    operation.getY(),
                                    operation.getZ(),
                                    after,
                                    operation
                                            .getExpectedAfterMetadata(),
                                    3
                            );

                    if (!placed) {
                        return false;
                    }
                }
            }

        } catch (RuntimeException exception) {
            return false;
        }

        if (!matchesEditCommitWorldAfterOperation(
                world,
                operation
        )) {
            return false;
        }

        /*
         * REMOVE may have restored a TE-backed source block.
         * Restore the captured source TileEntity only after the correct block
         * and metadata have been verified in the world.
         */
        return restoreEditCommitSourceTileEntityIfNeeded(
                world,
                job,
                operation
        );
    }

    private boolean restoreEditCommitSourceTileEntityIfNeeded(
            World world,
            EditCommitJob job,
            EditCommitJob.PhysicalOperation operation
    ) {
        if (world == null
                || job == null
                || operation == null) {
            return false;
        }

        /*
         * ADD deliberately destroys the source TE while the source becomes
         * a GatePart. There is nothing to restore at that point.
         */
        if (operation.getKind()
                != EditCommitJob.OperationKind.REMOVE) {
            return true;
        }

        /*
         * Non-restorable removals intentionally become air.
         */
        if (!operation.isSourceRestorable()
                || "minecraft:air".equals(
                operation.getExpectedAfterBlock()
        )) {
            return true;
        }

        GatePartData originalPart =
                findOriginalEditCommitPart(
                        job,
                        operation
                );

        if (originalPart == null) {
            return false;
        }

        NBTTagCompound snapshot =
                originalPart.getSourceTileEntityNbt();

        /*
         * Ordinary source blocks need only ID + metadata.
         */
        if (snapshot == null) {
            return true;
        }

        if (!originalPart
                .getSourceBlockName()
                .equals(operation.getSourceBlock())
                || originalPart.getSourceMetadata()
                != operation.getSourceMetadata()) {
            return false;
        }

        snapshot.setInteger(
                "x",
                operation.getX()
        );

        snapshot.setInteger(
                "y",
                operation.getY()
        );

        snapshot.setInteger(
                "z",
                operation.getZ()
        );

        try {
            TileEntity restored =
                    world.getTileEntity(
                            operation.getX(),
                            operation.getY(),
                            operation.getZ()
                    );

            /*
             * Most blocks create their TE automatically when setBlock runs.
             * Populate that fresh TE with the saved source state.
             */
            if (restored != null) {
                GateSourceTileEntitySnapshot
                        .applyForRestoration(
                                restored,
                                snapshot
                        );
                restored.markDirty();

                world.markBlockForUpdate(
                        operation.getX(),
                        operation.getY(),
                        operation.getZ()
                );

                return true;
            }

            /*
             * Some mod blocks do not immediately instantiate their TileEntity.
             * Reconstruct it directly from its registered TE id.
             */
            restored =
                    GateSourceTileEntitySnapshot
                            .createForRestoration(
                                    snapshot
                            );

            if (restored == null) {
                return false;
            }

            world.setTileEntity(
                    operation.getX(),
                    operation.getY(),
                    operation.getZ(),
                    restored
            );

            restored.markDirty();

            world.markBlockForUpdate(
                    operation.getX(),
                    operation.getY(),
                    operation.getZ()
            );

            return world.getTileEntity(
                    operation.getX(),
                    operation.getY(),
                    operation.getZ()
            ) != null;

        } catch (RuntimeException exception) {
            return false;
        }
    }

    private GatePartData findOriginalEditCommitPart(
            EditCommitJob job,
            EditCommitJob.PhysicalOperation operation
    ) {
        if (job == null
                || job.getOriginalSnapshot() == null
                || operation == null) {
            return null;
        }

        for (GatePartData part
                : job.getOriginalSnapshot()
                .getParts()) {

            if (part.getRelativeX()
                    == operation.getRelativeX()
                    && part.getRelativeY()
                    == operation.getRelativeY()
                    && part.getRelativeZ()
                    == operation.getRelativeZ()
                    && part.getLeaf()
                    == operation.getFinalLeaf()) {

                return part;
            }
        }

        return null;
    }

    private boolean isExactBaseEditCommitAuthority(EditCommitJob job) {
        if (job == null || job.getState() != EditCommitJob.State.PREPARED
                || !activeEditCommitJobUuidByGateUuid.containsKey(
                        job.getGateUuid())
                || !job.getJobUuid().equals(
                        activeEditCommitJobUuidByGateUuid.get(
                                job.getGateUuid()))
                || getJobForGate(job.getGateUuid()) != null
                || !isEditCommitControllerConsistent(job)
                || !areEditCommitReservationsConsistent(job)
                || !isValidEditCommitDelta(job.getOriginalSnapshot(),
                        job.getTargetSnapshot(), job.getPhysicalOperations())) {
            return false;
        }
        ControllerRecord controller = controllersByUuid.get(job.getGateUuid());
        return controller != null && controller.status == ControllerStatus.MUTATING
                && controller.structureRevision == job.getBaseRevision()
                && controller.hasEquivalentParts(
                        job.getOriginalSnapshot().getParts());
    }

    private boolean isExactApplyingEditCommit(EditCommitJob job) {
        return job != null && job.getState()
                == EditCommitJob.State.APPLYING_WORLD
                && isExactPreOwnershipEditCommit(job);
    }

    private boolean isExactPreOwnershipEditCommit(EditCommitJob job) {
        if (job == null || !isEditCommitPhysicalReconciliationState(
                job.getState())
                || readOnlyDueToInvalidData
                || !job.getJobUuid().equals(
                        activeEditCommitJobUuidByGateUuid.get(
                                job.getGateUuid()))
                || getJobForGate(job.getGateUuid()) != null) {
            return false;
        }
        ControllerRecord controller = controllersByUuid.get(job.getGateUuid());
        return controller != null
                && controller.matchesController(job.getDimension(),
                        job.getControllerX(), job.getControllerY(),
                        job.getControllerZ())
                && controller.status == ControllerStatus.MUTATING
                && controller.structureRevision == job.getBaseRevision()
                && controller.hasEquivalentParts(
                        job.getOriginalSnapshot().getParts());
    }

    private static boolean isEditCommitPhysicalReconciliationState(
            EditCommitJob.State state
    ) {
        return state == EditCommitJob.State.APPLYING_WORLD
                || state == EditCommitJob.State.PROMOTING_CONTROLLER
                || state == EditCommitJob.State.PROMOTING_OWNERSHIP;
    }

    private boolean isExactBaseEditCommitController(EditCommitJob job,
            ControllerRecord controller, int dimension, int controllerX,
            int controllerY, int controllerZ, int revision,
            Collection<GatePartData> parts) {
        return job != null && controller != null
                && job.getDimension() == dimension
                && job.getControllerX() == controllerX
                && job.getControllerY() == controllerY
                && job.getControllerZ() == controllerZ
                && job.getBaseRevision() == revision
                && job.getTargetRevision() == revision + 1
                && controller.matchesController(dimension, controllerX,
                        controllerY, controllerZ)
                && controller.structureRevision == revision
                && controller.hasEquivalentParts(parts)
                && controller.hasEquivalentParts(
                        job.getOriginalSnapshot().getParts());
    }

    private boolean isExactEditCommitControllerReload(EditCommitJob job,
            ControllerRecord controller, TileEntitySiegeGate gate) {
        if (job == null || controller == null || gate == null
                || gate.getWorldObj() == null || gate.getWorldObj().isRemote
                || !isEditCommitPreOwnershipState(job.getState())
                || controller.status != ControllerStatus.MUTATING
                || controller.structureRevision != job.getBaseRevision()
                || !controller.matchesController(job.getDimension(),
                        job.getControllerX(), job.getControllerY(),
                        job.getControllerZ())
                || !controller.hasEquivalentParts(
                        job.getOriginalSnapshot().getParts())
                || gate.getWorldObj().provider.dimensionId != job.getDimension()
                || gate.xCoord != job.getControllerX()
                || gate.yCoord != job.getControllerY()
                || gate.zCoord != job.getControllerZ()) {
            return false;
        }
        if (matchesEditCommitControllerSnapshot(gate,
                job.getOriginalSnapshot())) {
            return true;
        }
        return isEditCommitControllerPromotionState(job.getState())
                && matchesEditCommitControllerSnapshot(gate,
                        job.getTargetSnapshot());
    }

    private boolean matchesEditCommitControllerSnapshot(
            TileEntitySiegeGate gate, EditCommitJob.Snapshot snapshot
    ) {
        if (gate == null || snapshot == null || gate.getWorldObj() == null
                || gate.getWorldObj().isRemote
                || !snapshot.getGateUuid().equals(gate.getExistingGateUuid())
                || gate.getWorldObj().provider.dimensionId
                != snapshot.getDimension()
                || gate.xCoord != snapshot.getControllerX()
                || gate.yCoord != snapshot.getControllerY()
                || gate.zCoord != snapshot.getControllerZ()
                || gate.getStructureRevision() != snapshot.getRevision()
                || !gate.isFinalized() || gate.isGateStructureQuarantined()
                || !gate.hasCompleteHingeConfiguration()
                || gate.getGateState() != GateState.CLOSED
                || gate.isRepairActive()
                || gate.getReservedRamUuid() != null
                || !gate.getGateParts().containsAll(snapshot.getParts())
                || !snapshot.getParts().containsAll(gate.getGateParts())
                || !gate.getLeftHinge().equals(snapshot.getLeftHinge())
                || !gate.getRightHinge().equals(snapshot.getRightHinge())
                || gate.getGateOrientation() != snapshot.getOrientation()
                || gate.getOpeningDirection()
                != snapshot.getOpeningDirection()
                || gate.isGateBorderTextureEnabled()
                != snapshot.isBorderTextureEnabled()) {
            return false;
        }
        GateStructureValidator.ValidationResult validation =
                GateStructureValidator.validateFinalized(
                        gate.getGateParts(), gate.getLeftHinge(),
                        gate.getRightHinge(), gate.getGateOrientation(),
                        gate.getOpeningDirection(), gate.xCoord, gate.yCoord,
                        gate.zCoord
                );
        return validation.isValid();
    }

    private static boolean isEditCommitPreOwnershipState(
            EditCommitJob.State state
    ) {
        return state == EditCommitJob.State.PREPARED
                || state == EditCommitJob.State.APPLYING_WORLD
                || state == EditCommitJob.State.PROMOTING_CONTROLLER
                || state == EditCommitJob.State.PROMOTING_OWNERSHIP;
    }

    private boolean hasExactAddAuthority(EditCommitJob job,
            EditCommitJob.PhysicalOperation operation) {
        BlockPosition position = new BlockPosition(operation.getX(),
                operation.getY(), operation.getZ());
        TargetReservation reservation = targetReservationsByPosition.get(position);
        return operation.getKind() == EditCommitJob.OperationKind.ADD
                && ownersByPart.get(position) == null
                && matchesSnapshotOperation(job.getTargetSnapshot(), operation)
                && reservation != null
                && reservation.getJobUuid().equals(job.getJobUuid())
                && reservation.getGateUuid().equals(job.getGateUuid())
                && reservation.getDimension() == job.getDimension()
                && reservation.getBaseRevision() == job.getBaseRevision()
                && reservation.getTargetRevision() == job.getTargetRevision()
                && reservation.getX() == operation.getX()
                && reservation.getY() == operation.getY()
                && reservation.getZ() == operation.getZ();
    }

    private boolean hasExactRemoveAuthority(EditCommitJob job,
            EditCommitJob.PhysicalOperation operation) {
        if (operation.getKind() != EditCommitJob.OperationKind.REMOVE) {
            return false;
        }
        PartRecordRef ref = ownersByPart.get(new BlockPosition(operation.getX(),
                operation.getY(), operation.getZ()));
        return ref != null && ref.controller == controllersByUuid.get(
                        job.getGateUuid())
                && matchesSnapshotOperation(job.getOriginalSnapshot(), operation)
                && !snapshotContainsPosition(job.getTargetSnapshot(), operation)
                && ref.part.structureRevision == job.getBaseRevision()
                && ref.part.relativeX == operation.getRelativeX()
                && ref.part.relativeY == operation.getRelativeY()
                && ref.part.relativeZ == operation.getRelativeZ()
                && ref.part.leaf == operation.getFinalLeaf()
                && ref.part.sourceBlockName.equals(operation.getSourceBlock())
                && ref.part.sourceMetadata == operation.getSourceMetadata()
                && ref.part.sourceRestorable == operation.isSourceRestorable()
                && ref.part.expectedBlockName.equals(
                        operation.getExpectedBeforeBlock())
                && ref.part.expectedMetadata
                == operation.getExpectedBeforeMetadata();
    }

    private boolean operationMatchesReference(EditCommitJob job,
            EditCommitOperationRef ref) {
        Map<Integer, EditCommitOperationRef> refs =
                editCommitOperationRefsByJob.get(job.getJobUuid());
        if (refs == null || refs.get(Integer.valueOf(ref.ordinal)) != ref
                || ref.operation.getDimension() != job.getDimension()) {
            return false;
        }
        for (EditCommitJob.PhysicalOperation operation
                : job.getPhysicalOperations()) {
            if (operation.getOrdinal() == ref.ordinal) {
                return operation.getKind() == ref.operation.getKind()
                        && operation.getX() == ref.operation.getX()
                        && operation.getY() == ref.operation.getY()
                        && operation.getZ() == ref.operation.getZ()
                        && operation.getRelativeX()
                        == ref.operation.getRelativeX()
                        && operation.getRelativeY()
                        == ref.operation.getRelativeY()
                        && operation.getRelativeZ()
                        == ref.operation.getRelativeZ()
                        && operation.getExpectedBeforeBlock().equals(
                                ref.operation.getExpectedBeforeBlock())
                        && operation.getExpectedBeforeMetadata()
                        == ref.operation.getExpectedBeforeMetadata()
                        && operation.getExpectedAfterBlock().equals(
                                ref.operation.getExpectedAfterBlock())
                        && operation.getExpectedAfterMetadata()
                        == ref.operation.getExpectedAfterMetadata();
            }
        }
        return false;
    }

    private boolean matchesSnapshotOperation(EditCommitJob.Snapshot snapshot,
            EditCommitJob.PhysicalOperation operation) {
        if (snapshot == null) {
            return false;
        }
        for (GatePartData part : snapshot.getParts()) {
            if (part.getAbsoluteX(snapshot.getControllerX())
                    == operation.getX()
                    && part.getAbsoluteY(snapshot.getControllerY())
                    == operation.getY()
                    && part.getAbsoluteZ(snapshot.getControllerZ())
                    == operation.getZ()) {
                return part.getRelativeX() == operation.getRelativeX()
                        && part.getRelativeY() == operation.getRelativeY()
                        && part.getRelativeZ() == operation.getRelativeZ()
                        && part.getLeaf() == operation.getFinalLeaf()
                        && part.getSourceBlockName().equals(
                                operation.getSourceBlock())
                        && part.getSourceMetadata()
                        == operation.getSourceMetadata()
                        && (part.hasStoredSourceBlock()
                        && part.getSourceBlockForRestoration() != null)
                        == operation.isSourceRestorable();
            }
        }
        return false;
    }

    private boolean snapshotContainsPosition(EditCommitJob.Snapshot snapshot,
            EditCommitJob.PhysicalOperation operation) {
        if (snapshot == null) {
            return false;
        }
        for (GatePartData part : snapshot.getParts()) {
            if (part.getAbsoluteX(snapshot.getControllerX())
                    == operation.getX()
                    && part.getAbsoluteY(snapshot.getControllerY())
                    == operation.getY()
                    && part.getAbsoluteZ(snapshot.getControllerZ())
                    == operation.getZ()) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesEditCommitWorld(World world,
            EditCommitJob.PhysicalOperation operation, String blockName,
            int metadata) {
        Block block = world.getBlock(operation.getX(), operation.getY(),
                operation.getZ());
        String actual = Block.blockRegistry.getNameForObject(block);
        return blockName.equals(actual)
                && world.getBlockMetadata(operation.getX(), operation.getY(),
                        operation.getZ()) == metadata;
    }

    private boolean matchesEditCommitWorldAfterOperation(
            World world,
            EditCommitJob.PhysicalOperation operation
    ) {
        if (operation == null) {
            return false;
        }

        if (operation.getKind()
                != EditCommitJob.OperationKind.REMOVE) {
            return matchesEditCommitWorld(
                    world,
                    operation,
                    operation.getExpectedAfterBlock(),
                    operation.getExpectedAfterMetadata()
            );
        }

        return GateSourceRestorationAdapter.matchesRestoredBlock(
                world,
                operation.getX(),
                operation.getY(),
                operation.getZ(),
                operation.getExpectedAfterBlock(),
                operation.getExpectedAfterMetadata()
        );
    }

    private void conflictEditCommit(UUID jobUuid,
            EditCommitJob.FailureCode failureCode, int x, int y, int z,
            long currentTick) {
        EditCommitJob job = editCommitJobsByUuid.get(jobUuid);
        if (job == null || job.getState() == EditCommitJob.State.CONFLICT) {
            return;
        }
        int operationOrdinal = -1;
        for (EditCommitJob.PhysicalOperation operation
                : job.getPhysicalOperations()) {
            if (operation.getX() == x && operation.getY() == y
                    && operation.getZ() == z) {
                operationOrdinal = operation.getOrdinal();
                break;
            }
        }
        EditCommitJob conflicted = operationOrdinal >= 0
                ? job.withConflict(operationOrdinal, failureCode,
                        failureCode == null ? "UNKNOWN" : failureCode.name(),
                        x, y, z, currentTick)
                : job.withJobConflict(failureCode, x, y, z, currentTick);
        editCommitJobsByUuid.put(jobUuid, conflicted);
        ControllerRecord controller = controllersByUuid.get(job.getGateUuid());
        if (controller != null) {
            controller.status = ControllerStatus.QUARANTINED;
        }
        clearTransientEditCommitSchedulingForJob(jobUuid);
        markDirty();
        FMLLog.warning("[SIEGE_EDIT_COMMIT][CONFLICT] job=%s gate=%s %d->%d "
                        + "at=%d,%d,%d code=%s; world state was preserved.",
                shortUuid(job.getJobUuid()), shortUuid(job.getGateUuid()),
                job.getBaseRevision(), job.getTargetRevision(), x, y, z,
                failureCode);
    }

    private void logEditCommitPhysicalComplete(EditCommitJob job) {
        if (job == null || !editCommitPhysicalCompleteLogged.add(
                job.getJobUuid())) {
            return;
        }
        FMLLog.info("[SIEGE_EDIT_COMMIT][WORLD_AFTER] job=%s gate=%s %d->%d "
                        + "ops=%d; awaiting controller promotion.",
                shortUuid(job.getJobUuid()), shortUuid(job.getGateUuid()),
                job.getBaseRevision(), job.getTargetRevision(),
                job.getPhysicalOperations().size());
    }

    private void logEditCommitProgress(EditCommitJob job, Integer remaining) {
        if (job == null || remaining == null
                || job.getPhysicalOperations().isEmpty()) {
            return;
        }
        int total = job.getPhysicalOperations().size();
        int applied = Math.max(0, total - remaining.intValue());
        int percent = applied * 100 / total;
        Integer next = editCommitNextProgressPercentByJob.get(job.getJobUuid());
        if (next == null || percent < next.intValue()) {
            return;
        }
        FMLLog.info("[SIEGE_EDIT_COMMIT][PROGRESS] job=%s gate=%s %d->%d "
                        + "applied=%d/%d",
                shortUuid(job.getJobUuid()), shortUuid(job.getGateUuid()),
                job.getBaseRevision(), job.getTargetRevision(), applied, total);
        int following = next.intValue();
        while (following <= percent) {
            following += 25;
        }
        editCommitNextProgressPercentByJob.put(job.getJobUuid(),
                Integer.valueOf(following));
    }

    private enum EditCommitCleanupState {
        CLEANUP_BEFORE,
        CLEANUP_AFTER,
        CLEANUP_UNEXPECTED
    }

    private enum OperationWorldState {
        EXPECTED_BEFORE,
        EXPECTED_AFTER,
        UNEXPECTED
    }

    private int reconcileChunk(World world, long key, int budget) {
        List<UUID> controllerJobs = controllerJobsByChunk.containsKey(key)
                ? new ArrayList<UUID>(controllerJobsByChunk.get(key))
                : Collections.<UUID>emptyList();
        for (UUID jobUuid : controllerJobs) {
            MutationJob job = jobsByUuid.get(jobUuid);
            if (job != null && !job.controllerRemoved) {
                confirmControllerRemovalIfPresent(world, job);
            }
        }

        int processed = 0;
        List<JobEntryRef> refs = pendingEntriesByChunk.containsKey(key)
                ? new ArrayList<JobEntryRef>(pendingEntriesByChunk.get(key))
                : Collections.<JobEntryRef>emptyList();
        for (JobEntryRef ref : refs) {
            if (processed >= budget) {
                break;
            }
            MutationJob job = jobsByUuid.get(ref.jobUuid);
            if (job == null
                    || ref.entry.status != EntryStatus.PENDING
                    || !job.controllerRemoved) {
                continue;
            }
            ControllerRecord controller =
                    controllersByUuid.get(job.gateUuid);
            if (controller == null
                    || controller.status == ControllerStatus.QUARANTINED
                    || controller.structureRevision
                    != job.structureRevision) {
                continue;
            }
            if (ref.entry.dimension != world.provider.dimensionId) {
                markEntryConflict(
                        world,
                        job,
                        ref.entry,
                        "TARGET_DIMENSION_MISMATCH"
                );
                ++processed;
                refreshJobState(job);
                continue;
            }
            if (!world.blockExists(
                    ref.entry.targetX,
                    ref.entry.targetY,
                    ref.entry.targetZ
            )) {
                continue;
            }
            applyEntry(world, job, ref.entry);
            ++processed;
            refreshJobState(job);
        }
        return processed;
    }

    private void confirmControllerRemovalIfPresent(
            World world,
            MutationJob job
    ) {
        ControllerRecord record = controllersByUuid.get(job.gateUuid);
        if (record == null
                || record.status == ControllerStatus.QUARANTINED
                || !world.blockExists(
                        record.controllerX,
                        record.controllerY,
                        record.controllerZ
                )) {
            return;
        }
        if (world.getBlock(
                record.controllerX,
                record.controllerY,
                record.controllerZ
        ) == SiegeRegistry.gateController) {
            TileEntity tileEntity = world.getTileEntity(
                    record.controllerX,
                    record.controllerY,
                    record.controllerZ
            );
            if (tileEntity == null) {
                return;
            }
            if (tileEntity instanceof TileEntitySiegeGate
                    && job.gateUuid.equals(
                            ((TileEntitySiegeGate)tileEntity).getGateUuid()
                    )
                    && job.structureRevision
                    == ((TileEntitySiegeGate)tileEntity)
                            .getStructureRevision()
                    && !job.controllerRemoved
                    && job.allEntriesPending()) {
                abortPreparedRemoval(
                        job.gateUuid,
                        job.structureRevision
                );
            } else {
                markControllerCoordinateConflict(job, record);
            }
            return;
        }

        job.controllerRemoved = true;
        job.state = TransactionState.APPLYING;
        record.status = ControllerStatus.TOMBSTONED;
        markDirty();
        enqueueLoadedEntryChunks(world, job);
        refreshJobState(job);
    }

    private void applyEntry(
            World world,
            MutationJob job,
            MutationEntry entry
    ) {
        /*
         * Crash-safe roll-forward:
         *
         * The block may already have been restored before a crash while the
         * durable job still says PENDING. In that case, re-apply the TE snapshot
         * before acknowledging the operation.
         */
        if (matchesRestoredMutationWorld(
                world,
                entry
        )) {
            if (!restoreSourceTileEntityIfNeeded(
                    world,
                    entry
            )) {
                markEntryConflict(
                        world,
                        job,
                        entry,
                        "SOURCE_TILE_ENTITY_RESTORE_FAILED"
                );
                return;
            }

            markEntryApplied(
                    job,
                    entry
            );
            return;
        }

        if (!matchesWorld(
                world,
                entry,
                entry.expectedBeforeBlockName,
                entry.expectedBeforeMetadata
        )) {
            markEntryConflict(
                    world,
                    job,
                    entry,
                    "WORLD_STATE_MISMATCH"
            );
            return;
        }

        Block intended =
                Block.getBlockFromName(
                        entry.intendedBlockName
                );

        if (intended == null) {
            markEntryConflict(
                    world,
                    job,
                    entry,
                    "INTENDED_BLOCK_UNAVAILABLE"
            );
            return;
        }

        if (intended != Blocks.air
                && !isPersistedRestorationDefinitionUsable(
                        entry.sourceBlockName,
                        entry.sourceMetadata,
                        entry.sourceTileEntityNbt,
                        entry.sourceRestorable,
                        entry.intendedBlockName,
                        entry.intendedMetadata
                )) {
            markEntryConflict(
                    world,
                    job,
                    entry,
                    "INTENDED_SOURCE_DEFINITION_UNSAFE"
            );
            return;
        }

        try {
            if (intended == Blocks.air) {
                world.setBlockToAir(
                        entry.targetX,
                        entry.targetY,
                        entry.targetZ
                );
            } else {
                boolean placed =
                        world.setBlock(
                                entry.targetX,
                                entry.targetY,
                                entry.targetZ,
                                intended,
                                entry.intendedMetadata,
                                3
                        );

                if (!placed) {
                    markEntryConflict(
                            world,
                            job,
                            entry,
                            "WORLD_MUTATION_NOT_ACKNOWLEDGED"
                    );
                    return;
                }
            }

        } catch (RuntimeException exception) {
            markEntryConflict(
                    world,
                    job,
                    entry,
                    "WORLD_MUTATION_EXCEPTION"
            );
            return;
        }

        if (!matchesRestoredMutationWorld(
                world,
                entry
        )) {
            markEntryConflict(
                    world,
                    job,
                    entry,
                    "WORLD_MUTATION_NOT_ACKNOWLEDGED"
            );
            return;
        }

        if (!restoreSourceTileEntityIfNeeded(
                world,
                entry
        )) {
            markEntryConflict(
                    world,
                    job,
                    entry,
                    "SOURCE_TILE_ENTITY_RESTORE_FAILED"
            );
            return;
        }

        markEntryApplied(
                job,
                entry
        );
    }

    private static boolean isPersistedRestorationDefinitionUsable(
            String sourceBlockName,
            int sourceMetadata,
            NBTTagCompound sourceTileEntityNbt,
            boolean sourceRestorable,
            String intendedBlockName,
            int intendedMetadata
    ) {
        if (!sourceRestorable
                || sourceBlockName == null
                || intendedBlockName == null
                || !sourceBlockName.equals(intendedBlockName)
                || sourceMetadata != intendedMetadata) {
            return false;
        }

        GatePartData sourceDefinition =
                GatePartData.fromPersistedSourceSnapshot(
                        0,
                        0,
                        0,
                        GateLeaf.LEFT,
                        sourceBlockName,
                        sourceMetadata,
                        sourceTileEntityNbt,
                        true
                );

        return sourceDefinition != null
                && sourceDefinition.hasStoredSourceAppearance()
                && sourceDefinition.hasStoredSourceBlock()
                && sourceDefinition.getSourceBlockForRestoration() != null;
    }

    private boolean restoreSourceTileEntityIfNeeded(
            World world,
            MutationEntry entry
    ) {
        if (entry == null
                || entry.sourceTileEntityNbt == null) {
            return true;
        }

        if (!entry.sourceRestorable
                || !entry.intendedBlockName.equals(
                entry.sourceBlockName
        )) {
            return false;
        }

        NBTTagCompound snapshot =
                (NBTTagCompound)
                        entry.sourceTileEntityNbt.copy();

        snapshot.setInteger(
                "x",
                entry.targetX
        );

        snapshot.setInteger(
                "y",
                entry.targetY
        );

        snapshot.setInteger(
                "z",
                entry.targetZ
        );

        try {
            TileEntity restored =
                    world.getTileEntity(
                            entry.targetX,
                            entry.targetY,
                            entry.targetZ
                    );

            if (restored != null) {
                GateSourceTileEntitySnapshot
                        .applyForRestoration(
                                restored,
                                snapshot
                        );
                restored.markDirty();

                world.markBlockForUpdate(
                        entry.targetX,
                        entry.targetY,
                        entry.targetZ
                );

                return true;
            }

            restored =
                    GateSourceTileEntitySnapshot
                            .createForRestoration(
                                    snapshot
                            );

            if (restored == null) {
                return false;
            }

            world.setTileEntity(
                    entry.targetX,
                    entry.targetY,
                    entry.targetZ,
                    restored
            );

            restored.markDirty();

            world.markBlockForUpdate(
                    entry.targetX,
                    entry.targetY,
                    entry.targetZ
            );

            return world.getTileEntity(
                    entry.targetX,
                    entry.targetY,
                    entry.targetZ
            ) != null;

        } catch (RuntimeException exception) {
            return false;
        }
    }

    private void markEntryApplied(MutationJob job, MutationEntry entry) {
        entry.status = EntryStatus.APPLIED;
        entry.conflictReason = null;
        removePendingEntryIndex(job, entry);
        markDirty();
    }

    private void markControllerCoordinateConflict(
            MutationJob job,
            ControllerRecord record
    ) {
        boolean shouldLog = false;
        for (MutationEntry entry : job.entries) {
            if (entry.status == EntryStatus.PENDING) {
                shouldLog |= !entry.conflictLogged;
                entry.status = EntryStatus.CONFLICT;
                entry.conflictReason = "CONTROLLER_COORDINATE_REPLACED";
                entry.conflictLogged = true;
                removePendingEntryIndex(job, entry);
            }
        }
        job.state = TransactionState.CONFLICT;
        record.status = ControllerStatus.QUARANTINED;
        if (shouldLog) {
            FMLLog.warning(
                    "[LOTRMoreMobs] Siege Gate transaction conflict: "
                            + "job=%s gate=%s controller=%d,%d,%d was "
                            + "replaced by another controller; world blocks "
                            + "were preserved.",
                    job.jobUuid,
                    job.gateUuid,
                    Integer.valueOf(record.controllerX),
                    Integer.valueOf(record.controllerY),
                    Integer.valueOf(record.controllerZ)
            );
        }
        markDirty();
    }

    private void markEntryConflict(
            World world,
            MutationJob job,
            MutationEntry entry,
            String reason
    ) {
        entry.status = EntryStatus.CONFLICT;
        entry.conflictReason = bounded(reason, MAX_REASON_LENGTH);
        removePendingEntryIndex(job, entry);
        job.state = TransactionState.CONFLICT;
        if (!entry.conflictLogged) {
            entry.conflictLogged = true;
            FMLLog.warning(
                    "[LOTRMoreMobs] Siege Gate transaction conflict: "
                            + "job=%s gate=%s dim=%d target=%d,%d,%d "
                            + "reason=%s; world block preserved.",
                    job.jobUuid,
                    job.gateUuid,
                    Integer.valueOf(entry.dimension),
                    Integer.valueOf(entry.targetX),
                    Integer.valueOf(entry.targetY),
                    Integer.valueOf(entry.targetZ),
                    entry.conflictReason
            );
        }
        markDirty();
    }

    private void refreshJobState(MutationJob job) {
        boolean pending = false;
        boolean conflict = false;
        for (MutationEntry entry : job.entries) {
            pending |= entry.status == EntryStatus.PENDING;
            conflict |= entry.status == EntryStatus.CONFLICT;
        }
        if (conflict) {
            if (job.state != TransactionState.CONFLICT) {
                job.state = TransactionState.CONFLICT;
                markDirty();
            }
            return;
        }
        if (pending || !job.controllerRemoved) {
            TransactionState nextState =
                    job.state == TransactionState.PREPARED
                    ? TransactionState.PREPARED
                    : TransactionState.APPLYING;
            if (job.state != nextState) {
                job.state = nextState;
                markDirty();
            }
            return;
        }

        job.state = TransactionState.COMPLETE;
        ControllerRecord controller = controllersByUuid.get(job.gateUuid);
        removeJob(job);
        if (controller != null) {
            removeController(controller);
        }
        markDirty();
    }

    private boolean matchesRestoredMutationWorld(
            World world,
            MutationEntry entry
    ) {
        if (entry == null) {
            return false;
        }

        return GateSourceRestorationAdapter.matchesRestoredBlock(
                world,
                entry.targetX,
                entry.targetY,
                entry.targetZ,
                entry.intendedBlockName,
                entry.intendedMetadata
        );
    }

    private boolean matchesWorld(
            World world,
            MutationEntry entry,
            String blockName,
            int metadata
    ) {
        if (!world.blockExists(
                entry.targetX,
                entry.targetY,
                entry.targetZ
        )) {
            return false;
        }
        Block block = world.getBlock(
                entry.targetX,
                entry.targetY,
                entry.targetZ
        );
        String actualName = Block.blockRegistry.getNameForObject(block);
        return blockName.equals(actualName)
                && world.getBlockMetadata(
                        entry.targetX,
                        entry.targetY,
                        entry.targetZ
                ) == metadata;
    }

    private void enqueueLoadedEntryChunks(World world, MutationJob job) {
        Set<Long> seen = new HashSet<Long>();
        for (MutationEntry entry : job.entries) {
            if (entry.status == EntryStatus.PENDING
                    && world.blockExists(
                            entry.targetX,
                            entry.targetY,
                            entry.targetZ
                    )) {
                long key = chunkKey(entry.targetX >> 4, entry.targetZ >> 4);
                if (seen.add(Long.valueOf(key))) {
                    enqueueChunk(key);
                }
            }
        }
    }

    private void enqueueChunk(long key) {
        pendingChunkQueue.add(Long.valueOf(key));
    }

    private boolean hasPendingWorkForChunk(long key) {
        List<JobEntryRef> entries = pendingEntriesByChunk.get(key);
        Set<UUID> controllers = controllerJobsByChunk.get(key);
        if (entries != null) {
            for (JobEntryRef ref : entries) {
                MutationJob job = jobsByUuid.get(ref.jobUuid);
                ControllerRecord controller = job == null
                        ? null
                        : controllersByUuid.get(job.gateUuid);
                if (job != null
                        && job.controllerRemoved
                        && ref.entry.status == EntryStatus.PENDING
                        && controller != null
                        && controller.status
                        != ControllerStatus.QUARANTINED) {
                    return true;
                }
            }
        }
        if (controllers != null) {
            for (UUID jobUuid : controllers) {
                MutationJob job = jobsByUuid.get(jobUuid);
                ControllerRecord controller = job == null
                        ? null
                        : controllersByUuid.get(job.gateUuid);
                if (job != null
                        && !job.controllerRemoved
                        && job.state != TransactionState.CONFLICT
                        && controller != null
                        && controller.status
                        != ControllerStatus.QUARANTINED) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isChunkLoaded(World world, long key) {
        int chunkX = (int)(key >> 32);
        int chunkZ = (int)key;
        return world.blockExists(chunkX << 4, 0, chunkZ << 4);
    }

    private boolean hasUnresolvedJob(UUID gateUuid) {
        return getJobForGate(gateUuid) != null;
    }

    private MutationJob getJobForGate(UUID gateUuid) {
        UUID jobUuid = jobUuidByGateUuid.get(gateUuid);
        return jobUuid == null ? null : jobsByUuid.get(jobUuid);
    }

    private List<PartRecord> buildPartRecords(
            int controllerX,
            int controllerY,
            int controllerZ,
            int revision,
            Collection<GatePartData> parts
    ) {
        String expectedBlock =
                Block.blockRegistry.getNameForObject(
                        SiegeRegistry.gatePart
                );

        List<PartRecord> records =
                new ArrayList<PartRecord>(
                        parts.size()
                );

        for (GatePartData part : parts) {
            records.add(
                    new PartRecord(
                            part.getRelativeX(),
                            part.getRelativeY(),
                            part.getRelativeZ(),
                            part.getAbsoluteX(controllerX),
                            part.getAbsoluteY(controllerY),
                            part.getAbsoluteZ(controllerZ),
                            part.getLeaf(),
                            part.getSourceBlockName(),
                            part.getSourceMetadata(),
                            part.getSourceTileEntityNbt(),
                            part.hasStoredSourceBlock()
                                    && part.getSourceBlockForRestoration()
                                    != null,
                            expectedBlock,
                            0,
                            revision
                    )
            );
        }

        return records;
    }

    private boolean indexController(ControllerRecord record) {
        controllersByPosition.put(record.controllerPosition(), record);
        List<PartRecordRef> added = new ArrayList<PartRecordRef>();
        for (PartRecord part : record.parts) {
            BlockPosition position = part.absolutePosition();
            if (ownersByPart.containsKey(position)) {
                for (PartRecordRef ref : added) {
                    removePartIndex(ref);
                }
                controllersByPosition.remove(record.controllerPosition());
                return false;
            }
            PartRecordRef ref = new PartRecordRef(record, part);
            ownersByPart.put(position, ref);
            long key = chunkKey(part.absoluteX >> 4, part.absoluteZ >> 4);
            List<PartRecordRef> chunkOwners = ownersByChunk.get(key);
            if (chunkOwners == null) {
                chunkOwners = new ArrayList<PartRecordRef>();
                ownersByChunk.put(key, chunkOwners);
            }
            chunkOwners.add(ref);
            added.add(ref);
        }
        return true;
    }

    private void removeControllerIndex(ControllerRecord record) {
        controllersByPosition.remove(record.controllerPosition());
        for (PartRecord part : record.parts) {
            PartRecordRef ref = ownersByPart.get(part.absolutePosition());
            if (ref != null && ref.controller == record) {
                removePartIndex(ref);
            }
        }
    }

    private void removePartIndex(PartRecordRef ref) {
        ownersByPart.remove(ref.part.absolutePosition());
        long key = chunkKey(
                ref.part.absoluteX >> 4,
                ref.part.absoluteZ >> 4
        );
        List<PartRecordRef> chunkOwners = ownersByChunk.get(key);
        if (chunkOwners != null) {
            chunkOwners.remove(ref);
            if (chunkOwners.isEmpty()) {
                ownersByChunk.remove(key);
            }
        }
    }

    private void removeController(ControllerRecord record) {
        removeControllerIndex(record);
        controllersByUuid.remove(record.gateUuid);
        totalOwnershipParts -= record.parts.size();
    }

    private void indexJob(MutationJob job) {
        ControllerRecord controller = controllersByUuid.get(job.gateUuid);
        if (controller != null) {
            long key = chunkKey(
                    controller.controllerX >> 4,
                    controller.controllerZ >> 4
            );
            Set<UUID> jobs = controllerJobsByChunk.get(key);
            if (jobs == null) {
                jobs = new HashSet<UUID>();
                controllerJobsByChunk.put(key, jobs);
            }
            jobs.add(job.jobUuid);
        }
        boolean suspended = controller == null
                || controller.status == ControllerStatus.QUARANTINED;
        for (MutationEntry entry : job.entries) {
            if (entry.status != EntryStatus.PENDING || suspended) {
                continue;
            }
            long key = chunkKey(entry.targetX >> 4, entry.targetZ >> 4);
            List<JobEntryRef> refs = pendingEntriesByChunk.get(key);
            if (refs == null) {
                refs = new ArrayList<JobEntryRef>();
                pendingEntriesByChunk.put(key, refs);
            }
            refs.add(new JobEntryRef(job.jobUuid, entry));
        }
    }

    private void removePendingEntryIndex(
            MutationJob job,
            MutationEntry entry
    ) {
        long key = chunkKey(entry.targetX >> 4, entry.targetZ >> 4);
        List<JobEntryRef> refs = pendingEntriesByChunk.get(key);
        if (refs == null) {
            return;
        }
        for (Iterator<JobEntryRef> iterator = refs.iterator();
                iterator.hasNext();) {
            JobEntryRef ref = iterator.next();
            if (ref.jobUuid.equals(job.jobUuid) && ref.entry == entry) {
                iterator.remove();
                break;
            }
        }
        if (refs.isEmpty()) {
            pendingEntriesByChunk.remove(key);
        }
    }

    private void removeJob(MutationJob job) {
        jobsByUuid.remove(job.jobUuid);
        jobUuidByGateUuid.remove(job.gateUuid);
        totalJobEntries -= job.entries.size();
        for (MutationEntry entry : job.entries) {
            removePendingEntryIndex(job, entry);
        }
        ControllerRecord controller = controllersByUuid.get(job.gateUuid);
        if (controller != null) {
            long key = chunkKey(
                    controller.controllerX >> 4,
                    controller.controllerZ >> 4
            );
            Set<UUID> jobs = controllerJobsByChunk.get(key);
            if (jobs != null) {
                jobs.remove(job.jobUuid);
                if (jobs.isEmpty()) {
                    controllerJobsByChunk.remove(key);
                }
            }
        }
    }

    private static boolean jobMatchesController(
            MutationJob job,
            ControllerRecord controller
    ) {
        if (job == null
                || controller == null
                || job.entries.size() != controller.parts.size()) {
            return false;
        }
        Map<BlockPosition, PartRecord> partsByPosition =
                new HashMap<BlockPosition, PartRecord>();
        for (PartRecord part : controller.parts) {
            partsByPosition.put(part.absolutePosition(), part);
        }
        for (MutationEntry entry : job.entries) {
            PartRecord part = partsByPosition.get(new BlockPosition(
                    entry.targetX,
                    entry.targetY,
                    entry.targetZ
            ));
            if (part == null
                    || entry.dimension != controller.dimension
                    || entry.structureRevision
                    != controller.structureRevision
                    || !entry.expectedBeforeBlockName.equals(
                            part.expectedBlockName
                    )
                    || entry.expectedBeforeMetadata
                    != part.expectedMetadata
                    || !entry.sourceBlockName.equals(
                            part.sourceBlockName
                    )
                    || entry.sourceMetadata != part.sourceMetadata
                    || entry.sourceRestorable != part.sourceRestorable) {
                return false;
            }
        }
        return true;
    }

    private ControllerRecord readControllerRecord(NBTTagCompound nbt) {
        if (!hasControllerFields(nbt)) {
            return null;
        }
        UUID gateUuid = readUuid(nbt.getString(NBT_GATE_UUID));
        ControllerStatus status = ControllerStatus.fromName(
                nbt.getString(NBT_STATUS)
        );
        GateState lastState = readGateState(
                nbt.getString(NBT_LAST_GATE_STATE)
        );
        int dimension = nbt.getInteger(NBT_DIMENSION);
        int controllerX = nbt.getInteger(NBT_CONTROLLER_X);
        int controllerY = nbt.getInteger(NBT_CONTROLLER_Y);
        int controllerZ = nbt.getInteger(NBT_CONTROLLER_Z);
        int revision = nbt.getInteger(NBT_STRUCTURE_REVISION);
        int partCount = nbt.getInteger(NBT_PART_COUNT);
        NBTTagList partList = (NBTTagList)nbt.getTag(NBT_PARTS);
        if (gateUuid == null
                || status == null
                || lastState == null
                || revision <= 0
                || !isSanePosition(controllerX, controllerY, controllerZ)
                || partCount <= 0
                || partCount > GateStructureValidator.MAX_GATE_PARTS
                || partList.tagCount() != partCount
                || (partCount > 0
                && partList.func_150303_d() != TAG_COMPOUND)) {
            setLoadValidationError(
                    "controller fields invalid"
                            + " uuid=" + nbt.getString(NBT_GATE_UUID)
                            + " pos=" + controllerX + ","
                            + controllerY + "," + controllerZ
                            + " revision=" + revision
                            + " partCount=" + partCount
            );
            return null;
        }

        List<PartRecord> parts = new ArrayList<PartRecord>(partCount);
        List<GatePartData> validationParts =
                new ArrayList<GatePartData>(partCount);
        Set<BlockPosition> positions = new HashSet<BlockPosition>();
        for (int i = 0; i < partCount; ++i) {
            PartRecord part = readPartRecord(
                    partList.getCompoundTagAt(i),
                    controllerX,
                    controllerY,
                    controllerZ,
                    revision
            );
            if (part == null) {
                if (lastLoadValidationError == null) {
                    setLoadValidationError(
                            "controller " + gateUuid
                                    + " part[" + i + "] invalid"
                    );
                } else {
                    setLoadValidationError(
                            "controller " + gateUuid
                                    + " part[" + i + "]: "
                                    + lastLoadValidationError
                    );
                }
                return null;
            }
            if (!positions.add(part.absolutePosition())) {
                setLoadValidationError(
                        "controller " + gateUuid
                                + " has duplicate part position "
                                + part.absoluteX + ","
                                + part.absoluteY + ","
                                + part.absoluteZ
                );
                return null;
            }
            parts.add(part);
            validationParts.add(part.toGatePartData());
        }
        GateStructureValidator.ValidationResult structureValidation =
                GateStructureValidator.validateStructure(
                        validationParts,
                        controllerX,
                        controllerY,
                        controllerZ
                );
        if (!structureValidation.isValid()) {
            setLoadValidationError(
                    "controller " + gateUuid
                            + " structure invalid: "
                            + structureValidation.getFailure()
                            + (structureValidation.getMessage() == null
                            ? ""
                            : " (" + structureValidation.getMessage() + ")")
            );
            return null;
        }
        return new ControllerRecord(
                gateUuid,
                dimension,
                controllerX,
                controllerY,
                controllerZ,
                revision,
                status,
                lastState,
                parts
        );
    }

    private static GatePartData decodeStoredPartDefinition(
            int relativeX,
            int relativeY,
            int relativeZ,
            GateLeaf leaf,
            String sourceBlock,
            int sourceMeta,
            NBTTagCompound sourceTileEntityNbt,
            boolean sourceRestorable
    ) {
        if (leaf == null
                || sourceBlock == null
                || sourceMeta < 0
                || sourceMeta > 15) {
            return null;
        }

        /*
         * Backward compatibility:
         *
         * Older non-restorable durable records represented "no exact source"
         * as the iron-block fallback with SourceRestorable=false.
         */
        if (!sourceRestorable
                && GatePartData.FALLBACK_SOURCE_BLOCK.equals(
                sourceBlock
        )
                && sourceMeta == 0
                && sourceTileEntityNbt == null) {

            return new GatePartData(
                    relativeX,
                    relativeY,
                    relativeZ,
                    leaf
            );
        }

        /*
         * Source appearance and restoration state now include the optional
         * captured source TileEntity snapshot.
         */
        GatePartData part =
                GatePartData.fromPersistedSourceSnapshot(
                        relativeX,
                        relativeY,
                        relativeZ,
                        leaf,
                        sourceBlock,
                        sourceMeta,
                        sourceTileEntityNbt,
                        sourceRestorable
                );

        boolean actualRestorable =
                part.hasStoredSourceBlock()
                        && part.getSourceBlockForRestoration()
                        != null;

        if (!part.hasStoredSourceAppearance()
                || !sourceBlock.equals(
                part.getSourceBlockName()
        )
                || sourceMeta
                != part.getSourceMetadata()
                || actualRestorable
                != sourceRestorable) {
            return null;
        }

        return part;
    }

    private PartRecord readPartRecord(
            NBTTagCompound nbt,
            int controllerX,
            int controllerY,
            int controllerZ,
            int controllerRevision
    ) {
        if (!nbt.hasKey(NBT_RELATIVE_X, TAG_INT)
                || !nbt.hasKey(NBT_RELATIVE_Y, TAG_INT)
                || !nbt.hasKey(NBT_RELATIVE_Z, TAG_INT)
                || !nbt.hasKey(NBT_LEAF, TAG_STRING)
                || !nbt.hasKey(NBT_SOURCE_BLOCK, TAG_STRING)
                || !nbt.hasKey(NBT_SOURCE_META, TAG_INT)
                || !nbt.hasKey(NBT_SOURCE_RESTORABLE, TAG_BYTE)
                || !nbt.hasKey(NBT_EXPECTED_BLOCK, TAG_STRING)
                || !nbt.hasKey(NBT_EXPECTED_META, TAG_INT)
                || !nbt.hasKey(NBT_STRUCTURE_REVISION, TAG_INT)) {
            return null;
        }

        GateLeaf leaf =
                GateLeaf.fromSerializedName(
                        nbt.getString(NBT_LEAF)
                );

        String sourceBlock =
                nbt.getString(NBT_SOURCE_BLOCK);

        String expectedBlock =
                nbt.getString(NBT_EXPECTED_BLOCK);

        int relativeX =
                nbt.getInteger(NBT_RELATIVE_X);

        int relativeY =
                nbt.getInteger(NBT_RELATIVE_Y);

        int relativeZ =
                nbt.getInteger(NBT_RELATIVE_Z);

        int sourceMeta =
                nbt.getInteger(NBT_SOURCE_META);

        int expectedMeta =
                nbt.getInteger(NBT_EXPECTED_META);

        int revision =
                nbt.getInteger(NBT_STRUCTURE_REVISION);

        boolean sourceRestorable =
                nbt.getBoolean(NBT_SOURCE_RESTORABLE);

        NBTTagCompound sourceTileEntityNbt =
                nbt.hasKey(
                        NBT_SOURCE_TILE_ENTITY,
                        TAG_COMPOUND
                )
                        ? nbt.getCompoundTag(
                        NBT_SOURCE_TILE_ENTITY
                )
                        : null;

        String registeredGatePart =
                Block.blockRegistry.getNameForObject(
                        SiegeRegistry.gatePart
                );

        GatePartData sourceDefinition =
                decodeStoredPartDefinition(
                        relativeX,
                        relativeY,
                        relativeZ,
                        leaf,
                        sourceBlock,
                        sourceMeta,
                        sourceTileEntityNbt,
                        sourceRestorable
                );

        long absoluteX =
                (long)controllerX + relativeX;

        long absoluteY =
                (long)controllerY + relativeY;

        long absoluteZ =
                (long)controllerZ + relativeZ;

        if (leaf == null
                || !isBoundedBlockName(sourceBlock)
                || !isBoundedBlockName(expectedBlock)
                || sourceMeta < 0
                || sourceMeta > 15
                || expectedMeta < 0
                || expectedMeta > 15
                || revision != controllerRevision
                || registeredGatePart == null
                || !registeredGatePart.equals(expectedBlock)
                || expectedMeta != 0
                || sourceDefinition == null
                || absoluteX < -30000000L
                || absoluteX >= 30000000L
                || absoluteY < 0L
                || absoluteY >= 256L
                || absoluteZ < -30000000L
                || absoluteZ >= 30000000L) {
            setLoadValidationError(
                    "part definition invalid"
                            + " rel=" + relativeX + ","
                            + relativeY + "," + relativeZ
                            + " source=" + sourceBlock + ":"
                            + sourceMeta
                            + " savedRestorable=" + sourceRestorable
                            + " expected=" + expectedBlock + ":"
                            + expectedMeta
                            + " revision=" + revision
            );
            return null;
        }

        return new PartRecord(
                relativeX,
                relativeY,
                relativeZ,
                (int)absoluteX,
                (int)absoluteY,
                (int)absoluteZ,
                leaf,
                sourceBlock,
                sourceMeta,
                sourceTileEntityNbt,
                sourceRestorable,
                expectedBlock,
                expectedMeta,
                revision
        );
    }

    private MutationJob readMutationJob(NBTTagCompound nbt) {
        if (!nbt.hasKey(NBT_JOB_UUID, TAG_STRING)
                || !nbt.hasKey(NBT_GATE_UUID, TAG_STRING)
                || !nbt.hasKey(NBT_STRUCTURE_REVISION, TAG_INT)
                || !nbt.hasKey(NBT_TYPE, TAG_STRING)
                || !nbt.hasKey(NBT_STATE, TAG_STRING)
                || !nbt.hasKey(NBT_CREATED_TICK, TAG_LONG)
                || !nbt.hasKey(NBT_CONTROLLER_REMOVED, TAG_BYTE)
                || !nbt.hasKey(NBT_ENTRY_COUNT, TAG_INT)
                || !nbt.hasKey(NBT_ENTRIES, TAG_LIST)) {
            return null;
        }
        UUID jobUuid = readUuid(nbt.getString(NBT_JOB_UUID));
        UUID gateUuid = readUuid(nbt.getString(NBT_GATE_UUID));
        TransactionType type = TransactionType.fromName(
                nbt.getString(NBT_TYPE)
        );
        TransactionState state = TransactionState.fromName(
                nbt.getString(NBT_STATE)
        );
        int revision = nbt.getInteger(NBT_STRUCTURE_REVISION);
        int entryCount = nbt.getInteger(NBT_ENTRY_COUNT);
        NBTTagList entryList = (NBTTagList)nbt.getTag(NBT_ENTRIES);
        if (jobUuid == null
                || gateUuid == null
                || type == null
                || state == null
                || revision <= 0
                || entryCount <= 0
                || entryCount > GateStructureValidator.MAX_GATE_PARTS
                || entryList.tagCount() != entryCount
                || (entryCount > 0
                && entryList.func_150303_d() != TAG_COMPOUND)) {
            return null;
        }
        List<MutationEntry> entries =
                new ArrayList<MutationEntry>(entryCount);
        Set<BlockPosition> positions = new HashSet<BlockPosition>();
        for (int i = 0; i < entryCount; ++i) {
            MutationEntry entry = readMutationEntry(
                    entryList.getCompoundTagAt(i),
                    revision
            );
            if (entry == null
                    || !positions.add(new BlockPosition(
                            entry.targetX,
                            entry.targetY,
                            entry.targetZ
                    ))) {
                return null;
            }
            entries.add(entry);
        }
        return new MutationJob(
                jobUuid,
                gateUuid,
                revision,
                type,
                state,
                Math.max(0L, nbt.getLong(NBT_CREATED_TICK)),
                nbt.getBoolean(NBT_CONTROLLER_REMOVED),
                entries
        );
    }

    private MutationEntry readMutationEntry(
            NBTTagCompound nbt,
            int jobRevision
    ) {
        if (!nbt.hasKey(NBT_DIMENSION, TAG_INT)
                || !nbt.hasKey(NBT_TARGET_X, TAG_INT)
                || !nbt.hasKey(NBT_TARGET_Y, TAG_INT)
                || !nbt.hasKey(NBT_TARGET_Z, TAG_INT)
                || !nbt.hasKey(NBT_EXPECTED_BLOCK, TAG_STRING)
                || !nbt.hasKey(NBT_EXPECTED_META, TAG_INT)
                || !nbt.hasKey(NBT_INTENDED_BLOCK, TAG_STRING)
                || !nbt.hasKey(NBT_INTENDED_META, TAG_INT)
                || !nbt.hasKey(NBT_SOURCE_BLOCK, TAG_STRING)
                || !nbt.hasKey(NBT_SOURCE_META, TAG_INT)
                || !nbt.hasKey(NBT_SOURCE_RESTORABLE, TAG_BYTE)
                || !nbt.hasKey(NBT_STRUCTURE_REVISION, TAG_INT)
                || !nbt.hasKey(NBT_ENTRY_STATUS, TAG_STRING)
                || !nbt.hasKey(NBT_CONFLICT_LOGGED, TAG_BYTE)) {
            return null;
        }

        boolean hasSourceTileEntity =
                nbt.hasKey(
                        NBT_SOURCE_TILE_ENTITY,
                        TAG_COMPOUND
                );

        if (nbt.hasKey(NBT_SOURCE_TILE_ENTITY)
                && !hasSourceTileEntity) {
            return null;
        }

        NBTTagCompound sourceTileEntityNbt =
                hasSourceTileEntity
                        ? nbt.getCompoundTag(
                        NBT_SOURCE_TILE_ENTITY
                )
                        : null;

        String expected =
                nbt.getString(NBT_EXPECTED_BLOCK);

        String intended =
                nbt.getString(NBT_INTENDED_BLOCK);

        String source =
                nbt.getString(NBT_SOURCE_BLOCK);

        EntryStatus status =
                EntryStatus.fromName(
                        nbt.getString(NBT_ENTRY_STATUS)
                );

        String conflictReason =
                bounded(
                        nbt.getString(
                                NBT_CONFLICT_REASON
                        ),
                        MAX_REASON_LENGTH
                );

        if (conflictReason != null
                && conflictReason.isEmpty()) {
            conflictReason = null;
        }

        int x = nbt.getInteger(NBT_TARGET_X);
        int y = nbt.getInteger(NBT_TARGET_Y);
        int z = nbt.getInteger(NBT_TARGET_Z);

        int expectedMeta =
                nbt.getInteger(NBT_EXPECTED_META);

        int intendedMeta =
                nbt.getInteger(NBT_INTENDED_META);

        int sourceMeta =
                nbt.getInteger(NBT_SOURCE_META);

        int revision =
                nbt.getInteger(NBT_STRUCTURE_REVISION);

        boolean sourceRestorable =
                nbt.getBoolean(
                        NBT_SOURCE_RESTORABLE
                );

        String registeredGatePart =
                Block.blockRegistry.getNameForObject(
                        SiegeRegistry.gatePart
                );

        GatePartData sourceDefinition =
                GatePartData.fromPersistedSourceSnapshot(
                        0,
                        0,
                        0,
                        GateLeaf.LEFT,
                        source,
                        sourceMeta,
                        sourceTileEntityNbt,
                        sourceRestorable
                );

        boolean validIntendedAfter =
                sourceRestorable
                        ? source.equals(intended)
                        && sourceMeta == intendedMeta
                        && sourceDefinition
                        .hasStoredSourceBlock()
                        && sourceDefinition
                        .getSourceBlockForRestoration()
                        != null
                        : "minecraft:air".equals(
                        intended
                )
                        && intendedMeta == 0;

        if (!isSanePosition(x, y, z)
                || !isBoundedBlockName(expected)
                || !isBoundedBlockName(intended)
                || !isBoundedBlockName(source)
                || expectedMeta < 0
                || expectedMeta > 15
                || intendedMeta < 0
                || intendedMeta > 15
                || sourceMeta < 0
                || sourceMeta > 15
                || revision != jobRevision
                || registeredGatePart == null
                || !registeredGatePart.equals(expected)
                || expectedMeta != 0
                || !validIntendedAfter
                || status == null
                || (status == EntryStatus.CONFLICT
                && (conflictReason == null
                || conflictReason.isEmpty()))) {
            return null;
        }

        return new MutationEntry(
                nbt.getInteger(NBT_DIMENSION),
                x,
                y,
                z,
                expected,
                expectedMeta,
                intended,
                intendedMeta,
                source,
                sourceMeta,
                sourceTileEntityNbt,
                sourceRestorable,
                revision,
                status,
                conflictReason,
                nbt.getBoolean(
                        NBT_CONFLICT_LOGGED
                )
        );
    }

    private static NBTTagCompound writeControllerRecord(
            ControllerRecord record
    ) {
        NBTTagCompound nbt =
                new NBTTagCompound();

        nbt.setString(
                NBT_GATE_UUID,
                record.gateUuid.toString()
        );

        nbt.setInteger(
                NBT_DIMENSION,
                record.dimension
        );

        nbt.setInteger(
                NBT_CONTROLLER_X,
                record.controllerX
        );

        nbt.setInteger(
                NBT_CONTROLLER_Y,
                record.controllerY
        );

        nbt.setInteger(
                NBT_CONTROLLER_Z,
                record.controllerZ
        );

        nbt.setInteger(
                NBT_STRUCTURE_REVISION,
                record.structureRevision
        );

        nbt.setString(
                NBT_STATUS,
                record.status.name()
        );

        nbt.setString(
                NBT_LAST_GATE_STATE,
                record.lastGateState.name()
        );

        nbt.setInteger(
                NBT_PART_COUNT,
                record.parts.size()
        );

        NBTTagList parts =
                new NBTTagList();

        for (PartRecord part
                : record.parts) {

            NBTTagCompound partNbt =
                    new NBTTagCompound();

            partNbt.setInteger(
                    NBT_RELATIVE_X,
                    part.relativeX
            );

            partNbt.setInteger(
                    NBT_RELATIVE_Y,
                    part.relativeY
            );

            partNbt.setInteger(
                    NBT_RELATIVE_Z,
                    part.relativeZ
            );

            partNbt.setString(
                    NBT_LEAF,
                    part.leaf.name()
            );

            partNbt.setString(
                    NBT_SOURCE_BLOCK,
                    part.sourceBlockName
            );

            partNbt.setInteger(
                    NBT_SOURCE_META,
                    part.sourceMetadata
            );

            if (part.sourceTileEntityNbt != null) {
                partNbt.setTag(
                        NBT_SOURCE_TILE_ENTITY,
                        (NBTTagCompound)
                                part.sourceTileEntityNbt.copy()
                );
            }

            partNbt.setBoolean(
                    NBT_SOURCE_RESTORABLE,
                    part.sourceRestorable
            );

            partNbt.setString(
                    NBT_EXPECTED_BLOCK,
                    part.expectedBlockName
            );

            partNbt.setInteger(
                    NBT_EXPECTED_META,
                    part.expectedMetadata
            );

            partNbt.setInteger(
                    NBT_STRUCTURE_REVISION,
                    part.structureRevision
            );

            parts.appendTag(
                    partNbt
            );
        }

        nbt.setTag(
                NBT_PARTS,
                parts
        );

        return nbt;
    }

    private static NBTTagCompound writeMutationJob(MutationJob job) {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString(NBT_JOB_UUID, job.jobUuid.toString());
        nbt.setString(NBT_GATE_UUID, job.gateUuid.toString());
        nbt.setInteger(NBT_STRUCTURE_REVISION, job.structureRevision);
        nbt.setString(NBT_TYPE, job.type.name());
        nbt.setString(NBT_STATE, job.state.name());
        nbt.setLong(NBT_CREATED_TICK, job.createdTick);
        nbt.setBoolean(NBT_CONTROLLER_REMOVED, job.controllerRemoved);
        nbt.setInteger(NBT_ENTRY_COUNT, job.entries.size());
        NBTTagList entries = new NBTTagList();
        for (MutationEntry entry : job.entries) {
            NBTTagCompound entryNbt = new NBTTagCompound();
            entryNbt.setInteger(NBT_DIMENSION, entry.dimension);
            entryNbt.setInteger(NBT_TARGET_X, entry.targetX);
            entryNbt.setInteger(NBT_TARGET_Y, entry.targetY);
            entryNbt.setInteger(NBT_TARGET_Z, entry.targetZ);
            entryNbt.setString(
                    NBT_EXPECTED_BLOCK,
                    entry.expectedBeforeBlockName
            );
            entryNbt.setInteger(
                    NBT_EXPECTED_META,
                    entry.expectedBeforeMetadata
            );
            if (entry.sourceTileEntityNbt != null) {
                entryNbt.setTag(
                        NBT_SOURCE_TILE_ENTITY,
                        (NBTTagCompound)
                                entry.sourceTileEntityNbt.copy()
                );
            }
            entryNbt.setString(
                    NBT_INTENDED_BLOCK,
                    entry.intendedBlockName
            );
            entryNbt.setInteger(
                    NBT_INTENDED_META,
                    entry.intendedMetadata
            );
            entryNbt.setString(NBT_SOURCE_BLOCK, entry.sourceBlockName);
            entryNbt.setInteger(NBT_SOURCE_META, entry.sourceMetadata);
            entryNbt.setBoolean(
                    NBT_SOURCE_RESTORABLE,
                    entry.sourceRestorable
            );
            entryNbt.setInteger(
                    NBT_STRUCTURE_REVISION,
                    entry.structureRevision
            );
            entryNbt.setString(NBT_ENTRY_STATUS, entry.status.name());
            if (entry.conflictReason != null) {
                entryNbt.setString(
                        NBT_CONFLICT_REASON,
                        entry.conflictReason
                );
            }
            entryNbt.setBoolean(
                    NBT_CONFLICT_LOGGED,
                    entry.conflictLogged
            );
            entries.appendTag(entryNbt);
        }
        nbt.setTag(NBT_ENTRIES, entries);
        return nbt;
    }

    private static boolean hasControllerFields(NBTTagCompound nbt) {
        return nbt.hasKey(NBT_GATE_UUID, TAG_STRING)
                && nbt.hasKey(NBT_DIMENSION, TAG_INT)
                && nbt.hasKey(NBT_CONTROLLER_X, TAG_INT)
                && nbt.hasKey(NBT_CONTROLLER_Y, TAG_INT)
                && nbt.hasKey(NBT_CONTROLLER_Z, TAG_INT)
                && nbt.hasKey(NBT_STRUCTURE_REVISION, TAG_INT)
                && nbt.hasKey(NBT_STATUS, TAG_STRING)
                && nbt.hasKey(NBT_LAST_GATE_STATE, TAG_STRING)
                && nbt.hasKey(NBT_PART_COUNT, TAG_INT)
                && nbt.hasKey(NBT_PARTS, TAG_LIST);
    }

    private void setLoadValidationError(String reason) {
        lastLoadValidationError = bounded(
                reason,
                MAX_REASON_LENGTH
        );
    }

    private String loadValidationSuffix() {
        return lastLoadValidationError == null
                || lastLoadValidationError.isEmpty()
                ? ""
                : ": " + lastLoadValidationError;
    }

    private void rejectLoadedData(String reason) {
        clearAllRecords();
        readOnlyDueToInvalidData = true;
        FMLLog.severe(
                "[LOTRMoreMobs] Siege Gate ownership data is read-only: %s. "
                        + "No ownership migration or destructive transaction "
                        + "will begin until the data is repaired.",
                reason
        );
    }

    private void indexEditCommitOperations(EditCommitJob job) {
        if (job == null || (job.getState() != EditCommitJob.State.PREPARED
                && !isEditCommitPhysicalReconciliationState(
                        job.getState()))) {
            return;
        }
        clearTransientEditCommitSchedulingForJob(job.getJobUuid());
        Map<Integer, EditCommitOperationRef> byOrdinal =
                new HashMap<Integer, EditCommitOperationRef>();
        for (EditCommitJob.PhysicalOperation operation
                : job.getPhysicalOperations()) {
            long key = chunkKey(operation.getX() >> 4, operation.getZ() >> 4);
            EditCommitOperationRef ref = new EditCommitOperationRef(
                    job.getJobUuid(), operation, key);
            byOrdinal.put(Integer.valueOf(operation.getOrdinal()), ref);
            LinkedHashSet<EditCommitOperationRef> refs =
                    pendingEditCommitOperationsByChunk.get(Long.valueOf(key));
            if (refs == null) {
                refs = new LinkedHashSet<EditCommitOperationRef>();
                pendingEditCommitOperationsByChunk.put(Long.valueOf(key), refs);
            }
            refs.add(ref);
            editCommitChunkDiscoveryQueue.add(Long.valueOf(key));
        }
        if (!byOrdinal.isEmpty()) {
            editCommitOperationRefsByJob.put(job.getJobUuid(), byOrdinal);
            remainingEditCommitOperationsByJob.put(job.getJobUuid(),
                    Integer.valueOf(byOrdinal.size()));
            editCommitNextProgressPercentByJob.put(job.getJobUuid(),
                    Integer.valueOf(25));
        }
    }

    private void repairEditCommitSchedulingIndexes() {
        int repaired = 0;
        for (EditCommitJob job : editCommitJobsByUuid.values()) {
            if (repaired >= MAX_EDIT_COMMIT_INDEX_REPAIRS_PER_TICK) {
                return;
            }
            if ((job.getState() == EditCommitJob.State.PREPARED
                    || isEditCommitPhysicalReconciliationState(
                            job.getState()))
                    && !job.getPhysicalOperations().isEmpty()
                    && !editCommitPhysicalVerifiedThisEpoch.contains(
                            job.getJobUuid())
                    && !editCommitOperationRefsByJob.containsKey(
                            job.getJobUuid())) {
                indexEditCommitOperations(job);
                ++repaired;
            }
            if (repaired >= MAX_EDIT_COMMIT_INDEX_REPAIRS_PER_TICK) {
                return;
            }
            if (isEditCommitControllerPromotionState(job.getState())
                    && !editCommitControllerChunkByJob.containsKey(
                            job.getJobUuid())) {
                indexEditCommitControllerPromotion(job);
                ++repaired;
            }
        }
    }

    private void removeEditCommitOperationRef(EditCommitOperationRef ref) {
        if (ref == null) {
            return;
        }
        Map<Integer, EditCommitOperationRef> refs =
                editCommitOperationRefsByJob.get(ref.jobUuid);
        if (refs != null && refs.remove(Integer.valueOf(ref.ordinal)) != null) {
            if (refs.isEmpty()) {
                editCommitOperationRefsByJob.remove(ref.jobUuid);
            }
            Integer remaining = remainingEditCommitOperationsByJob.get(
                    ref.jobUuid);
            int next = remaining == null ? 0
                    : Math.max(0, remaining.intValue() - 1);
            remainingEditCommitOperationsByJob.put(ref.jobUuid,
                    Integer.valueOf(next));
        }
        LinkedHashSet<EditCommitOperationRef> byChunk =
                pendingEditCommitOperationsByChunk.get(Long.valueOf(ref.chunkKey));
        if (byChunk != null) {
            byChunk.remove(ref);
            if (byChunk.isEmpty()) {
                pendingEditCommitOperationsByChunk.remove(
                        Long.valueOf(ref.chunkKey));
            }
        }
    }

    private void clearTransientEditCommitSchedulingForJob(UUID jobUuid) {
        Map<Integer, EditCommitOperationRef> refs =
                editCommitOperationRefsByJob.remove(jobUuid);
        if (refs != null) {
            for (EditCommitOperationRef ref : refs.values()) {
                LinkedHashSet<EditCommitOperationRef> byChunk =
                        pendingEditCommitOperationsByChunk.get(
                                Long.valueOf(ref.chunkKey));
                if (byChunk != null) {
                    byChunk.remove(ref);
                    if (byChunk.isEmpty()) {
                        pendingEditCommitOperationsByChunk.remove(
                                Long.valueOf(ref.chunkKey));
                    }
                }
            }
        }
        remainingEditCommitOperationsByJob.remove(jobUuid);
        editCommitPhysicalVerifiedThisEpoch.remove(jobUuid);
        editCommitPhysicalCompleteLogged.remove(jobUuid);
        editCommitNextProgressPercentByJob.remove(jobUuid);
        Long controllerChunk = editCommitControllerChunkByJob.remove(jobUuid);
        if (controllerChunk != null) {
            LinkedHashSet<UUID> jobs = editCommitControllerJobsByChunk.get(
                    controllerChunk
            );
            if (jobs != null) {
                jobs.remove(jobUuid);
                if (jobs.isEmpty()) {
                    editCommitControllerJobsByChunk.remove(controllerChunk);
                }
            }
        }
        editCommitControllerWaitingLogged.remove(jobUuid);
        editCommitControllerAfterLogged.remove(jobUuid);
        editCommitControllerVerifiedThisEpoch.remove(jobUuid);
        pendingEditCommitArchivalJobUuids.remove(jobUuid);
        blockedEditCommitArchivalJobUuids.remove(jobUuid);
    }

    private void clearTransientEditCommitScheduling() {
        pendingEditCommitOperationsByChunk.clear();
        editCommitOperationRefsByJob.clear();
        remainingEditCommitOperationsByJob.clear();
        pendingEditCommitChunkQueue.clear();
        editCommitChunkDiscoveryQueue.clear();
        editCommitPhysicalCompleteLogged.clear();
        editCommitNextProgressPercentByJob.clear();
        editCommitPhysicalVerifiedThisEpoch.clear();
        editCommitControllerJobsByChunk.clear();
        editCommitControllerChunkByJob.clear();
        pendingEditCommitControllerChunkQueue.clear();
        editCommitControllerChunkDiscoveryQueue.clear();
        editCommitControllerWaitingLogged.clear();
        editCommitControllerAfterLogged.clear();
        editCommitControllerVerifiedThisEpoch.clear();
        pendingEditCommitArchivalJobUuids.clear();
        blockedEditCommitArchivalJobUuids.clear();
        editCommitArchivalDiscoverySnapshot = null;
        editCommitArchivalDiscoveryCursor = 0;
        editCommitArchivalDiscoveryComplete = false;

        clearEditCommitDebugPause();
    }

    private void enqueueEditCommitChunk(long key) {
        if (hasPendingEditCommitWorkForChunk(key)) {
            pendingEditCommitChunkQueue.add(Long.valueOf(key));
        }
    }

    private boolean hasPendingEditCommitWorkForChunk(long key) {
        LinkedHashSet<EditCommitOperationRef> refs =
                pendingEditCommitOperationsByChunk.get(Long.valueOf(key));
        return refs != null && !refs.isEmpty();
    }

    private void clearAllRecords() {
        controllersByUuid.clear();
        controllersByPosition.clear();
        ownersByPart.clear();
        ownersByChunk.clear();
        jobsByUuid.clear();
        jobUuidByGateUuid.clear();
        pendingEntriesByChunk.clear();
        controllerJobsByChunk.clear();
        pendingChunkQueue.clear();
        editCommitJobsByUuid.clear();
        activeEditCommitJobUuidByGateUuid.clear();
        targetReservationsByPosition.clear();
        targetReservationPositionsByJob.clear();
        completedEditCommitTombstones.clear();
        clearTransientEditCommitScheduling();
        totalOwnershipParts = 0;
        totalJobEntries = 0;
        totalEditCommitOperations = 0;
    }

    private void logCapacityWarning(int dimension) {
        if (capacityWarningLogged) {
            return;
        }
        capacityWarningLogged = true;
        FMLLog.warning(
                "[LOTRMoreMobs] Siege Gate ownership/transaction capacity "
                        + "is exhausted in dimension %d. Refusing a new "
                        + "destructive transaction; administrative recovery "
                        + "is required and unresolved evidence was retained.",
                Integer.valueOf(dimension)
        );
    }

    private static void warnIntegrity(String message) {
        FMLLog.warning("[LOTRMoreMobs] " + message);
    }

    private static UUID readUuid(String value) {
        if (value == null || value.length() > 36) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static GateState readGateState(String value) {
        try {
            return GateState.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        } catch (NullPointerException ignored) {
            return null;
        }
    }

    private static boolean isBoundedBlockName(String value) {
        return value != null
                && !value.isEmpty()
                && value.length() <= MAX_BLOCK_NAME_LENGTH;
    }

    private static boolean isSanePosition(int x, int y, int z) {
        return x >= -30000000 && x < 30000000
                && y >= 0 && y < 256
                && z >= -30000000 && z < 30000000;
    }

    private static String bounded(String value, int maximumLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maximumLength
                ? value
                : value.substring(0, maximumLength);
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long)chunkX << 32) | (chunkZ & 0xffffffffL);
    }

    public enum ControllerStatus {
        ACTIVE,
        MUTATING,
        TOMBSTONED,
        QUARANTINED;

        private static ControllerStatus fromName(String name) {
            try {
                return valueOf(name);
            } catch (IllegalArgumentException ignored) {
                return null;
            } catch (NullPointerException ignored) {
                return null;
            }
        }
    }

    /** Bounded, read-only durable mutation classifications for Phase 4B. */
    public enum GateMutationState {
        NONE,
        LEGACY_REMOVAL,
        EDIT_COMMIT_PREPARED,
        EDIT_COMMIT_APPLYING_WORLD,
        EDIT_COMMIT_PROMOTING_CONTROLLER,
        EDIT_COMMIT_PROMOTING_OWNERSHIP,
        EDIT_COMMIT_CONFLICT,
        QUARANTINED,
        INCONSISTENT,
        CORRUPT
    }

    /** Bounded result of the server-only Phase 4C PREPARED transition. */
    public static final class EditCommitPrepareResult {
        public enum State {
            PREPARED,
            READ_ONLY,
            INVALID_MATERIAL,
            MUTATION_IN_PROGRESS,
            OWNERSHIP_CONFLICT,
            RESERVATION_CONFLICT,
            CAPACITY_REJECTED,
            INTERNAL_REJECTED
        }

        private final State state;
        private final UUID jobUuid;
        private final UUID gateUuid;
        private final int baseRevision;
        private final int targetRevision;

        private EditCommitPrepareResult(State state, UUID jobUuid, UUID gateUuid,
                int baseRevision, int targetRevision) {
            this.state = state;
            this.jobUuid = jobUuid;
            this.gateUuid = gateUuid;
            this.baseRevision = baseRevision;
            this.targetRevision = targetRevision;
        }

        private static EditCommitPrepareResult prepared(UUID jobUuid,
                UUID gateUuid, int baseRevision, int targetRevision) {
            return new EditCommitPrepareResult(State.PREPARED, jobUuid, gateUuid,
                    baseRevision, targetRevision);
        }

        private static EditCommitPrepareResult rejected(State state) {
            return new EditCommitPrepareResult(state, null, null, 0, 0);
        }

        public State getState() { return state; }
        public UUID getJobUuid() { return jobUuid; }
        public UUID getGateUuid() { return gateUuid; }
        public int getBaseRevision() { return baseRevision; }
        public int getTargetRevision() { return targetRevision; }
    }

    public enum ActiveControllerCheck {
        ACTIVE, INVALID_DATA, MISSING, UUID_MISMATCH, CONTROLLER_MISMATCH,
        STALE_REVISION, MUTATION_IN_PROGRESS, QUARANTINED, PARTS_MISMATCH
    }

    public enum ExpectedBasePartCheck {
        MATCH, TARGET_CHANGED, FOREIGN_OWNER, OWNERSHIP_MISMATCH
    }

    public enum TransactionType {
        DISMANTLE_RESTORE,
        CONTROLLER_REMOVAL,
        FINALIZE_COMMIT,
        EDIT_COMMIT;

        private static TransactionType fromName(String name) {
            try {
                return valueOf(name);
            } catch (IllegalArgumentException ignored) {
                return null;
            } catch (NullPointerException ignored) {
                return null;
            }
        }
    }

    public enum TransactionState {
        PREPARED,
        APPLYING,
        CONFLICT,
        COMPLETE;

        private static TransactionState fromName(String name) {
            try {
                return valueOf(name);
            } catch (IllegalArgumentException ignored) {
                return null;
            } catch (NullPointerException ignored) {
                return null;
            }
        }
    }

    public enum EntryStatus {
        PENDING,
        APPLIED,
        CONFLICT;

        private static EntryStatus fromName(String name) {
            try {
                return valueOf(name);
            } catch (IllegalArgumentException ignored) {
                return null;
            } catch (NullPointerException ignored) {
                return null;
            }
        }
    }

    public static final class DurablePartOwner {
        private final UUID gateUuid;
        private final int controllerX;
        private final int controllerY;
        private final int controllerZ;
        private final int structureRevision;
        private final ControllerStatus status;
        private final GateState lastGateState;

        private DurablePartOwner(
                ControllerRecord controller,
                PartRecord part
        ) {
            gateUuid = controller.gateUuid;
            controllerX = controller.controllerX;
            controllerY = controller.controllerY;
            controllerZ = controller.controllerZ;
            structureRevision = part.structureRevision;
            status = controller.status;
            lastGateState = controller.lastGateState;
        }

        public UUID getGateUuid() {
            return gateUuid;
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

        public int getStructureRevision() {
            return structureRevision;
        }

        public ControllerStatus getStatus() {
            return status;
        }

        public GateState getLastGateState() {
            return lastGateState;
        }
    }

    private static final class ControllerRecord {
        private final UUID gateUuid;
        private final int dimension;
        private final int controllerX;
        private final int controllerY;
        private final int controllerZ;
        private int structureRevision;
        private ControllerStatus status;
        private GateState lastGateState;
        private final List<PartRecord> parts;

        private ControllerRecord(
                UUID gateUuid,
                int dimension,
                int controllerX,
                int controllerY,
                int controllerZ,
                int structureRevision,
                ControllerStatus status,
                GateState lastGateState,
                List<PartRecord> parts
        ) {
            this.gateUuid = gateUuid;
            this.dimension = dimension;
            this.controllerX = controllerX;
            this.controllerY = controllerY;
            this.controllerZ = controllerZ;
            this.structureRevision = structureRevision;
            this.status = status;
            this.lastGateState = lastGateState;
            this.parts = parts;
        }

        private boolean matchesController(
                int dimension,
                int x,
                int y,
                int z
        ) {
            return this.dimension == dimension
                    && controllerX == x
                    && controllerY == y
                    && controllerZ == z;
        }

        private BlockPosition controllerPosition() {
            return new BlockPosition(controllerX, controllerY, controllerZ);
        }

        private boolean hasEquivalentParts(Collection<GatePartData> data) {
            if (data == null || data.size() != parts.size()) {
                return false;
            }
            Map<BlockPosition, PartRecord> current =
                    new HashMap<BlockPosition, PartRecord>();
            for (PartRecord part : parts) {
                current.put(part.absolutePosition(), part);
            }
            for (GatePartData part : data) {
                BlockPosition position = new BlockPosition(
                        part.getAbsoluteX(controllerX),
                        part.getAbsoluteY(controllerY),
                        part.getAbsoluteZ(controllerZ)
                );
                PartRecord stored = current.get(position);
                if (stored == null || !stored.matches(part)) {
                    return false;
                }
            }
            return true;
        }
    }

    private static final class PartRecord {

        private final int relativeX;
        private final int relativeY;
        private final int relativeZ;

        private final int absoluteX;
        private final int absoluteY;
        private final int absoluteZ;

        private final GateLeaf leaf;

        private final String sourceBlockName;
        private final int sourceMetadata;
        private final NBTTagCompound sourceTileEntityNbt;
        private final boolean sourceRestorable;

        private final String expectedBlockName;
        private final int expectedMetadata;
        private final int structureRevision;

        private PartRecord(
                int relativeX,
                int relativeY,
                int relativeZ,
                int absoluteX,
                int absoluteY,
                int absoluteZ,
                GateLeaf leaf,
                String sourceBlockName,
                int sourceMetadata,
                NBTTagCompound sourceTileEntityNbt,
                boolean sourceRestorable,
                String expectedBlockName,
                int expectedMetadata,
                int structureRevision
        ) {
            this.relativeX = relativeX;
            this.relativeY = relativeY;
            this.relativeZ = relativeZ;

            this.absoluteX = absoluteX;
            this.absoluteY = absoluteY;
            this.absoluteZ = absoluteZ;

            this.leaf = leaf;

            this.sourceBlockName =
                    sourceBlockName;

            this.sourceMetadata =
                    sourceMetadata;

            this.sourceTileEntityNbt =
                    sourceTileEntityNbt == null
                            ? null
                            : (NBTTagCompound)
                            sourceTileEntityNbt.copy();

            this.sourceRestorable =
                    sourceRestorable;

            this.expectedBlockName =
                    expectedBlockName;

            this.expectedMetadata =
                    expectedMetadata;

            this.structureRevision =
                    structureRevision;
        }

        private BlockPosition absolutePosition() {
            return new BlockPosition(
                    absoluteX,
                    absoluteY,
                    absoluteZ
            );
        }

        private GatePartData toGatePartData() {
            /*
             * Legacy fallback records had no genuine source appearance.
             */
            if (!sourceRestorable
                    && GatePartData.FALLBACK_SOURCE_BLOCK.equals(
                    sourceBlockName
            )
                    && sourceMetadata == 0
                    && sourceTileEntityNbt == null) {

                return new GatePartData(
                        relativeX,
                        relativeY,
                        relativeZ,
                        leaf
                );
            }

            return GatePartData.fromPersistedSourceSnapshot(
                    relativeX,
                    relativeY,
                    relativeZ,
                    leaf,
                    sourceBlockName,
                    sourceMetadata,
                    sourceTileEntityNbt,
                    sourceRestorable
            );
        }

        private boolean matches(
                GatePartData part
        ) {
            if (part == null) {
                return false;
            }

            boolean partRestorable =
                    part.hasStoredSourceBlock()
                            && part.getSourceBlockForRestoration()
                            != null;

            return relativeX == part.getRelativeX()
                    && relativeY == part.getRelativeY()
                    && relativeZ == part.getRelativeZ()
                    && leaf == part.getLeaf()
                    && sourceBlockName.equals(
                    part.getSourceBlockName()
            )
                    && sourceMetadata
                    == part.getSourceMetadata()
                    && sourceRestorable
                    == partRestorable
                    && tagsEqual(
                    sourceTileEntityNbt,
                    part.getSourceTileEntityNbt()
            );
        }

        private static boolean tagsEqual(
                NBTTagCompound first,
                NBTTagCompound second
        ) {
            if (first == second) {
                return true;
            }

            if (first == null
                    || second == null) {
                return false;
            }

            return first.equals(second);
        }
    }

    private static final class MutationJob {
        private final UUID jobUuid;
        private final UUID gateUuid;
        private final int structureRevision;
        private final TransactionType type;
        private TransactionState state;
        private final long createdTick;
        private boolean controllerRemoved;
        private final List<MutationEntry> entries;

        private MutationJob(
                UUID jobUuid,
                UUID gateUuid,
                int structureRevision,
                TransactionType type,
                TransactionState state,
                long createdTick,
                boolean controllerRemoved,
                List<MutationEntry> entries
        ) {
            this.jobUuid = jobUuid;
            this.gateUuid = gateUuid;
            this.structureRevision = structureRevision;
            this.type = type;
            this.state = state;
            this.createdTick = createdTick;
            this.controllerRemoved = controllerRemoved;
            this.entries = entries;
        }

        private boolean allEntriesPending() {
            for (MutationEntry entry : entries) {
                if (entry.status != EntryStatus.PENDING) {
                    return false;
                }
            }
            return true;
        }

        private boolean hasPendingEntries() {
            for (MutationEntry entry : entries) {
                if (entry.status == EntryStatus.PENDING) {
                    return true;
                }
            }
            return false;
        }

        private void markPendingEntriesConflict(String reason) {
            for (MutationEntry entry : entries) {
                if (entry.status == EntryStatus.PENDING) {
                    entry.status = EntryStatus.CONFLICT;
                    entry.conflictReason = bounded(reason, MAX_REASON_LENGTH);
                    entry.conflictLogged = true;
                }
            }
        }

        private long controllerChunkKey(SiegeGateOwnershipData data) {
            ControllerRecord controller = data.controllersByUuid.get(gateUuid);
            return controller == null
                    ? 0L
                    : chunkKey(
                            controller.controllerX >> 4,
                            controller.controllerZ >> 4
                    );
        }
    }

    private static final class MutationEntry {

        private final int dimension;
        private final int targetX;
        private final int targetY;
        private final int targetZ;

        private final String expectedBeforeBlockName;
        private final int expectedBeforeMetadata;

        private final String intendedBlockName;
        private final int intendedMetadata;

        private final String sourceBlockName;
        private final int sourceMetadata;
        private final NBTTagCompound sourceTileEntityNbt;
        private final boolean sourceRestorable;

        private final int structureRevision;

        private EntryStatus status;
        private String conflictReason;
        private boolean conflictLogged;

        private MutationEntry(
                int dimension,
                int targetX,
                int targetY,
                int targetZ,
                String expectedBeforeBlockName,
                int expectedBeforeMetadata,
                String intendedBlockName,
                int intendedMetadata,
                String sourceBlockName,
                int sourceMetadata,
                NBTTagCompound sourceTileEntityNbt,
                boolean sourceRestorable,
                int structureRevision,
                EntryStatus status,
                String conflictReason,
                boolean conflictLogged
        ) {
            this.dimension = dimension;
            this.targetX = targetX;
            this.targetY = targetY;
            this.targetZ = targetZ;

            this.expectedBeforeBlockName =
                    expectedBeforeBlockName;

            this.expectedBeforeMetadata =
                    expectedBeforeMetadata;

            this.intendedBlockName =
                    intendedBlockName;

            this.intendedMetadata =
                    intendedMetadata;

            this.sourceBlockName =
                    sourceBlockName;

            this.sourceMetadata =
                    sourceMetadata;

            this.sourceTileEntityNbt =
                    sourceTileEntityNbt == null
                            ? null
                            : (NBTTagCompound)
                            sourceTileEntityNbt.copy();

            this.sourceRestorable =
                    sourceRestorable;

            this.structureRevision =
                    structureRevision;

            this.status = status;
            this.conflictReason = conflictReason;
            this.conflictLogged = conflictLogged;
        }
    }

    private static final class PartRecordRef {
        private final ControllerRecord controller;
        private final PartRecord part;

        private PartRecordRef(
                ControllerRecord controller,
                PartRecord part
        ) {
            this.controller = controller;
            this.part = part;
        }
    }

    private static final class JobEntryRef {
        private final UUID jobUuid;
        private final MutationEntry entry;

        private JobEntryRef(UUID jobUuid, MutationEntry entry) {
            this.jobUuid = jobUuid;
            this.entry = entry;
        }
    }

    private static final class EditCommitOperationRef {
        private final UUID jobUuid;
        private final int ordinal;
        private final EditCommitJob.PhysicalOperation operation;
        private final long chunkKey;

        private EditCommitOperationRef(UUID jobUuid,
                EditCommitJob.PhysicalOperation operation, long chunkKey) {
            this.jobUuid = jobUuid;
            ordinal = operation.getOrdinal();
            this.operation = operation;
            this.chunkKey = chunkKey;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof EditCommitOperationRef
                    && jobUuid.equals(((EditCommitOperationRef)other).jobUuid)
                    && ordinal == ((EditCommitOperationRef)other).ordinal;
        }

        @Override
        public int hashCode() {
            return 31 * jobUuid.hashCode() + ordinal;
        }
    }

    /** Fully prevalidated, in-memory-only Phase 4F ownership replacement. */
    private static final class EditCommitOwnershipPlan {
        private final EditCommitJob job;
        private final ControllerRecord baseController;
        private final ControllerRecord targetController;
        private final List<TargetReservation> reservations;
        private final int addCount;
        private final int removeCount;

        private EditCommitOwnershipPlan(EditCommitJob job,
                ControllerRecord baseController,
                ControllerRecord targetController,
                List<TargetReservation> reservations, int addCount,
                int removeCount) {
            this.job = job;
            this.baseController = baseController;
            this.targetController = targetController;
            this.reservations = reservations;
            this.addCount = addCount;
            this.removeCount = removeCount;
        }
    }

    private static final class BlockPosition {
        private final int x;
        private final int y;
        private final int z;

        private BlockPosition(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof BlockPosition)) {
                return false;
            }
            BlockPosition position = (BlockPosition)other;
            return x == position.x && y == position.y && z == position.z;
        }

        @Override
        public int hashCode() {
            int result = x;
            result = 31 * result + y;
            return 31 * result + z;
        }
    }
}
