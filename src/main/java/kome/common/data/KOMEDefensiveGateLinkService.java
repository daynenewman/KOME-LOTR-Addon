package kome.common.data;

import java.util.UUID;

/** Server-authoritative logical linkage operations and the initial physical-health commit boundary. */
public final class KOMEDefensiveGateLinkService {
    private KOMEDefensiveGateLinkService() {
    }

    public static OperationResult link(KOMEWorldData data, KOMEPlayerBuild parent,
            KOMEPhysicalGateInspection.Result inspection, UUID actorUuid, String actorName,
            boolean administrator, long timestamp) {
        OperationResult validation = validateNewLink(data, parent, inspection, administrator);
        if (!validation.isSuccessful()) return validation;
        return createLink(data, parent, inspection, actorUuid, actorName, timestamp, null);
    }

    /**
     * Creates a new logical record and commits its one-time physical-health initialization as
     * one server action. The record is not audited or dirtied as a successful link until the
     * physical gate accepts the initialization.
     */
    public static OperationResult linkAndInitializePhysicalHealth(KOMEWorldData data,
            KOMEPlayerBuild parent, KOMEPhysicalGateInspection.Result inspection,
            UUID actorUuid, String actorName, boolean administrator, long timestamp,
            PhysicalHealthApplication physicalHealth) {
        OperationResult validation = validateNewLink(data, parent, inspection, administrator);
        if (!validation.isSuccessful()) return validation;
        boolean canApply = false;
        try {
            canApply = physicalHealth != null && physicalHealth.canApply();
        } catch (RuntimeException ignored) {
        }
        if (!canApply) {
            return OperationResult.failure(
                "The physical Siege Gate cannot accept its initial KOME health.");
        }
        return createLink(data, parent, inspection, actorUuid, actorName, timestamp,
            physicalHealth);
    }

    /** Non-mutating preflight used before calculating and applying initial physical health. */
    public static OperationResult validateNewLink(KOMEWorldData data, KOMEPlayerBuild parent,
            KOMEPhysicalGateInspection.Result inspection, boolean administrator) {
        OperationResult validation = validateMutation(data, parent, inspection, administrator);
        if (!validation.isSuccessful()) return validation;
        ActiveLink existing = findActiveLinkByPhysicalGateUuid(data, inspection.getGateUuid());
        if (existing != null) {
            return OperationResult.failure("That physical Siege Gate is already linked to Build "
                + existing.parent.id + " record " + existing.record.id + ".");
        }
        ActiveLink replacedAtController = findActiveLinkAtController(data, inspection);
        if (replacedAtController != null) {
            return OperationResult.failure("A previous physical binding at this controller belongs to Build "
                + replacedAtController.parent.id + " record " + replacedAtController.record.id
                + "; use Relink or explicitly Unlink it first.");
        }
        return OperationResult.validationSuccess();
    }

    private static OperationResult createLink(KOMEWorldData data, KOMEPlayerBuild parent,
            KOMEPhysicalGateInspection.Result inspection, UUID actorUuid, String actorName,
            long timestamp, PhysicalHealthApplication physicalHealth) {
        // All validation precedes allocation; a later physical failure intentionally burns its
        // transient G-number so persisted logical IDs remain monotonic and are never reused.
        String recordId = parent.allocateDefensiveGateRecordId();
        KOMEDefensiveGateRecord record = new KOMEDefensiveGateRecord();
        record.id = recordId;
        record.createdAtMillis = Math.max(0L, timestamp);
        record.updatedAtMillis = Math.max(0L, timestamp);
        inspection.applyTo(record, timestamp);
        parent.addDefensiveGateRecord(record);
        if (physicalHealth != null) {
            boolean applied;
            try {
                applied = physicalHealth.apply();
            } catch (RuntimeException ignored) {
                applied = false;
            }
            if (!applied) {
                parent.removeDefensiveGateRecord(record.id);
                data.markDirty(); // Persist the intentionally burned monotonic G-number.
                return OperationResult.failure(
                    "The physical Siege Gate rejected KOME health initialization; the link was rolled back.");
            }
        }
        parent.updatedAtMillis = Math.max(parent.updatedAtMillis, timestamp);
        audit(data, timestamp, "LINK", actorUuid, actorName, parent, record,
            "physicalGateUuid=" + inspection.getGateUuid());
        data.markDirty();
        return OperationResult.success(record);
    }

    /** Small adapter so the data service can coordinate without owning a physical TileEntity. */
    public interface PhysicalHealthApplication {
        boolean canApply();
        boolean apply();
    }

    public static OperationResult unlink(KOMEWorldData data, KOMEPlayerBuild parent,
            String recordId, UUID actorUuid, String actorName, boolean administrator,
            long timestamp) {
        OperationResult validation = validateParent(data, parent, administrator);
        if (!validation.isSuccessful()) return validation;
        KOMEDefensiveGateRecord record = parent.getDefensiveGateRecord(recordId);
        if (record == null) return OperationResult.failure("The defensive gate record does not exist.");
        UUID physicalUuid = record.gateUuid;
        if (!parent.removeDefensiveGateRecord(record.id)) {
            return OperationResult.failure("The defensive gate record could not be unlinked.");
        }
        parent.updatedAtMillis = Math.max(parent.updatedAtMillis, timestamp);
        audit(data, timestamp, "UNLINK", actorUuid, actorName, parent, record,
            "physicalGateUuid=" + (physicalUuid == null ? "" : physicalUuid.toString()));
        data.markDirty();
        return OperationResult.success(record);
    }

    /** Reinspection of the same UUID and controller identity preserves the logical record/G-number. */
    public static OperationResult refresh(KOMEWorldData data, KOMEPlayerBuild parent,
            String recordId, KOMEPhysicalGateInspection.Result inspection, UUID actorUuid,
            String actorName, boolean administrator, long timestamp) {
        OperationResult validation = validateMutation(data, parent, inspection, administrator);
        if (!validation.isSuccessful()) return validation;
        KOMEDefensiveGateRecord record = parent.getDefensiveGateRecord(recordId);
        if (record == null) return OperationResult.failure("The defensive gate record does not exist.");
        if (!isSamePhysicalController(record, inspection)) {
            return OperationResult.failure("The physical gate identity changed; explicit Relink is required.");
        }
        ActiveLink duplicate = findActiveLinkByPhysicalGateUuid(data, inspection.getGateUuid());
        if (duplicate != null && duplicate.record != record) {
            return OperationResult.failure("That physical Siege Gate is already linked elsewhere.");
        }
        inspection.applyTo(record, timestamp);
        parent.updatedAtMillis = Math.max(parent.updatedAtMillis, timestamp);
        audit(data, timestamp, "REFRESH", actorUuid, actorName, parent, record,
            "structureRevision=" + inspection.getStructureRevision());
        data.markDirty();
        return OperationResult.success(record);
    }

    /** Explicitly replaces a broken binding while retaining its logical G-number and override history. */
    public static OperationResult relink(KOMEWorldData data, KOMEPlayerBuild parent,
            String recordId, KOMEPhysicalGateInspection.Result inspection,
            boolean previousBindingBroken, UUID actorUuid, String actorName,
            boolean administrator, long timestamp) {
        return relink(data, parent, recordId, inspection, previousBindingBroken, actorUuid,
            actorName, administrator, timestamp, null);
    }

    /**
     * Replaces a broken logical binding and applies the recalculated maximum to the new physical
     * gate. The TileEntity owns first-initialization versus clamp-only current-health semantics.
     */
    public static OperationResult relinkAndApplyPhysicalHealth(KOMEWorldData data,
            KOMEPlayerBuild parent, String recordId,
            KOMEPhysicalGateInspection.Result inspection, boolean previousBindingBroken,
            UUID actorUuid, String actorName, boolean administrator, long timestamp,
            PhysicalHealthApplication physicalHealth) {
        return relink(data, parent, recordId, inspection, previousBindingBroken, actorUuid,
            actorName, administrator, timestamp, physicalHealth);
    }

    private static OperationResult relink(KOMEWorldData data, KOMEPlayerBuild parent,
            String recordId, KOMEPhysicalGateInspection.Result inspection,
            boolean previousBindingBroken, UUID actorUuid, String actorName,
            boolean administrator, long timestamp,
            PhysicalHealthApplication physicalHealth) {
        OperationResult validation = validateMutation(data, parent, inspection, administrator);
        if (!validation.isSuccessful()) return validation;
        KOMEDefensiveGateRecord record = parent.getDefensiveGateRecord(recordId);
        if (record == null) return OperationResult.failure("The defensive gate record does not exist.");
        if (!previousBindingBroken) {
            return OperationResult.failure("The existing physical binding is still valid; Unlink it first.");
        }
        ActiveLink duplicate = findActiveLinkByPhysicalGateUuid(data, inspection.getGateUuid());
        if (duplicate != null && duplicate.record != record) {
            return OperationResult.failure("That physical Siege Gate is already linked to Build "
                + duplicate.parent.id + " record " + duplicate.record.id + ".");
        }
        ActiveLink replacedAtController = findActiveLinkAtController(data, inspection);
        if (replacedAtController != null && replacedAtController.record != record) {
            return OperationResult.failure("That controller location belongs to another broken "
                + "KOME record; Relink that record or Unlink it first.");
        }
        if (physicalHealth != null) {
            boolean canApply = false;
            try {
                canApply = physicalHealth.canApply();
            } catch (RuntimeException ignored) {
            }
            if (!canApply) {
                return OperationResult.failure(
                    "The physical Siege Gate cannot accept recalculated KOME health.");
            }
            boolean applied;
            try {
                applied = physicalHealth.apply();
            } catch (RuntimeException ignored) {
                applied = false;
            }
            if (!applied) {
                return OperationResult.failure(
                    "The physical Siege Gate rejected recalculated KOME health; the link was not changed.");
            }
        }
        UUID previousUuid = record.gateUuid;
        record.clearAdminConfirmedDimensions(timestamp);
        inspection.applyTo(record, timestamp);
        parent.updatedAtMillis = Math.max(parent.updatedAtMillis, timestamp);
        audit(data, timestamp, "RELINK", actorUuid, actorName, parent, record,
            "previousPhysicalGateUuid=" + (previousUuid == null ? "" : previousUuid.toString())
                + ";physicalGateUuid=" + inspection.getGateUuid());
        data.markDirty();
        return OperationResult.success(record);
    }

    public static OperationResult confirmDimensions(KOMEWorldData data, KOMEPlayerBuild parent,
            String recordId, KOMEPhysicalGateInspection.Result currentInspection,
            int width, int height, UUID actorUuid, String actorName, boolean administrator,
            long timestamp) {
        OperationResult validation = validateMutation(data, parent, currentInspection, administrator);
        if (!validation.isSuccessful()) return validation;
        if (width <= 0 || height <= 0) {
            return OperationResult.failure("Confirmed gate dimensions must be positive integers.");
        }
        KOMEDefensiveGateRecord record = parent.getDefensiveGateRecord(recordId);
        if (record == null) return OperationResult.failure("The defensive gate record does not exist.");
        if (!isSamePhysicalController(record, currentInspection)
                || record.capturedStructureRevision != currentInspection.getStructureRevision()) {
            return OperationResult.failure("The physical gate changed; refresh it before confirming dimensions.");
        }
        KOMEDefensiveGateRecord.DimensionDetectionStatus status = currentInspection.getStatus();
        if (status != KOMEDefensiveGateRecord.DimensionDetectionStatus.AMBIGUOUS
                && status != KOMEDefensiveGateRecord.DimensionDetectionStatus.IRREGULAR) {
            return OperationResult.failure("Manual dimensions are only required for ambiguous or irregular gates.");
        }
        record.setAdminConfirmedDimensions(width, height, currentInspection.getStructureRevision(),
            actorUuid, actorName, timestamp, "Administrator confirmed effective gate dimensions");
        parent.updatedAtMillis = Math.max(parent.updatedAtMillis, timestamp);
        audit(data, timestamp, "CONFIRM_DIMENSIONS", actorUuid, actorName, parent, record,
            "width=" + width + ";height=" + height
                + ";structureRevision=" + currentInspection.getStructureRevision());
        data.markDirty();
        return OperationResult.success(record);
    }

    public static ActiveLink findActiveLinkByPhysicalGateUuid(KOMEWorldData data, UUID gateUuid) {
        if (data == null || gateUuid == null) return null;
        for (KOMEPlayerBuild build : data.builds.values()) {
            if (build == null || !build.active || !build.isDefensive()) continue;
            for (KOMEDefensiveGateRecord record : build.getDefensiveGateRecords()) {
                if (record != null && record.hasPhysicalBinding() && gateUuid.equals(record.gateUuid)) {
                    return new ActiveLink(build, record);
                }
            }
        }
        return null;
    }

    private static ActiveLink findActiveLinkAtController(KOMEWorldData data,
            KOMEPhysicalGateInspection.Result inspection) {
        if (data == null || inspection == null) return null;
        for (KOMEPlayerBuild build : data.builds.values()) {
            if (build == null || !build.active || !build.isDefensive()) continue;
            for (KOMEDefensiveGateRecord record : build.getDefensiveGateRecords()) {
                if (record != null && record.hasPhysicalBinding()
                        && record.gateDimension.intValue() == inspection.getDimension()
                        && record.controllerX.intValue() == inspection.getControllerX()
                        && record.controllerY.intValue() == inspection.getControllerY()
                        && record.controllerZ.intValue() == inspection.getControllerZ()
                        && !inspection.getGateUuid().equals(record.gateUuid)) {
                    return new ActiveLink(build, record);
                }
            }
        }
        return null;
    }

    private static OperationResult validateMutation(KOMEWorldData data, KOMEPlayerBuild parent,
            KOMEPhysicalGateInspection.Result inspection, boolean administrator) {
        OperationResult parentValidation = validateParent(data, parent, administrator);
        if (!parentValidation.isSuccessful()) return parentValidation;
        if (inspection == null || !inspection.isLinkable() || inspection.getGateUuid() == null
                || inspection.getStructureRevision() <= 0) {
            return OperationResult.failure(inspection == null
                ? "Physical gate inspection is unavailable."
                : "Physical gate inspection is invalid: " + inspection.getDiagnostic());
        }
        return OperationResult.validationSuccess();
    }

    private static OperationResult validateParent(KOMEWorldData data, KOMEPlayerBuild parent,
            boolean administrator) {
        if (!administrator) return OperationResult.failure("Only a server administrator may change KOME gate links.");
        if (data == null || data.isWriteBlocked()) {
            return OperationResult.failure("KOME world data is unavailable or write-blocked.");
        }
        if (parent == null || data.getBuild(parent.id) != parent || !parent.active) {
            return OperationResult.failure("The selected KOME Build is not active.");
        }
        if (!parent.isDefensive()) return OperationResult.failure("Only a DEFENSIVE Build may own gate links.");
        return OperationResult.validationSuccess();
    }

    public static boolean isSamePhysicalController(KOMEDefensiveGateRecord record,
            KOMEPhysicalGateInspection.Result inspection) {
        return record != null && inspection != null && record.gateUuid != null
            && record.gateUuid.equals(inspection.getGateUuid())
            && record.gateDimension != null
            && record.gateDimension.intValue() == inspection.getDimension()
            && record.controllerX != null && record.controllerX.intValue() == inspection.getControllerX()
            && record.controllerY != null && record.controllerY.intValue() == inspection.getControllerY()
            && record.controllerZ != null && record.controllerZ.intValue() == inspection.getControllerZ();
    }

    private static void audit(KOMEWorldData data, long timestamp, String action, UUID actorUuid,
            String actorName, KOMEPlayerBuild parent, KOMEDefensiveGateRecord record,
            String details) {
        KOMEAuditService.record(data, timestamp, "DEFENSIVE_GATE", action,
            actorUuid == null ? safe(actorName) : actorUuid.toString(),
            parent.id + "/" + record.id,
            "Administrator " + action.toLowerCase(java.util.Locale.ROOT) + " operation", details);
    }

    private static String safe(String value) { return value == null ? "" : value.trim(); }

    public static final class ActiveLink {
        private final KOMEPlayerBuild parent;
        private final KOMEDefensiveGateRecord record;
        private ActiveLink(KOMEPlayerBuild parent, KOMEDefensiveGateRecord record) {
            this.parent = parent;
            this.record = record;
        }
        public KOMEPlayerBuild getParent() { return parent; }
        public KOMEDefensiveGateRecord getRecord() { return record; }
    }

    public static final class OperationResult {
        private final boolean successful;
        private final String message;
        private final KOMEDefensiveGateRecord record;
        private OperationResult(boolean successful, String message, KOMEDefensiveGateRecord record) {
            this.successful = successful;
            this.message = message == null ? "" : message;
            this.record = record;
        }
        private static OperationResult validationSuccess() { return new OperationResult(true, "", null); }
        private static OperationResult success(KOMEDefensiveGateRecord record) {
            return new OperationResult(true, "", record);
        }
        private static OperationResult failure(String message) {
            return new OperationResult(false, message, null);
        }
        public boolean isSuccessful() { return successful; }
        public String getMessage() { return message; }
        public KOMEDefensiveGateRecord getRecord() { return record; }
    }
}
