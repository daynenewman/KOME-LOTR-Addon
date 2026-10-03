package kome.common.data;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import kome.common.siege.KOMESiegeComplex;
import kome.common.tactical.KOMETacticalConfiguration;
import kome.common.tactical.KOMETacticalIds;

/**
 * Server-side world-aware membership operations. Callers handle authorization; no editor/runtime state is owned here.
 * Each operation checks an expected tactical revision and publishes a detached membership-only candidate.
 */
public final class KOMETacticalMembershipService {
    public enum Status {
        CHANGED, NO_CHANGE, INVALID_ID, WORLD_DATA_MISSING, WRITE_BLOCKED, STALE_CONFIGURATION_REVISION,
        BUILD_MISSING, BUILD_INACTIVE, BUILD_NOT_DEFENSIVE, COMPLEX_MISSING,
        BUILD_TILE_MISMATCH, BUILD_DIMENSION_MISMATCH, BUILD_ALREADY_ASSIGNED,
        EXPECTED_ASSIGNMENT_MISMATCH, REVISION_EXHAUSTED, COMMIT_FAILED
    }
    private enum Operation { ASSIGN, UNASSIGN, REASSIGN }

    private KOMETacticalMembershipService() { }

    public static Result assignBuild(KOMEWorldData data, String buildId, String complexId, long expectedRevision) {
        return change(data, buildId, null, complexId, expectedRevision, Operation.ASSIGN, true);
    }

    /** Removal can repair an assignment even after its Build is missing, inactive or no longer DEFENSIVE. */
    public static Result unassignBuild(KOMEWorldData data, String buildId, long expectedRevision) {
        return change(data, buildId, null, null, expectedRevision, Operation.UNASSIGN, true);
    }

    public static Result reassignBuild(KOMEWorldData data, String buildId, String expectedOldComplexId,
            String newComplexId, long expectedRevision) {
        return change(data, buildId, expectedOldComplexId, newComplexId, expectedRevision, Operation.REASSIGN, true);
    }

    /** Read-only editor preflight uses exactly the commit validation, including world-aware Build checks. */
    public static Result previewAssignBuild(KOMEWorldData data, String buildId, String complexId, long expectedRevision) {
        return change(data, buildId, null, complexId, expectedRevision, Operation.ASSIGN, false);
    }
    public static Result previewUnassignBuild(KOMEWorldData data, String buildId, long expectedRevision) {
        return change(data, buildId, null, null, expectedRevision, Operation.UNASSIGN, false);
    }
    public static Result previewReassignBuild(KOMEWorldData data, String buildId, String expectedOldComplexId,
            String newComplexId, long expectedRevision) {
        return change(data, buildId, expectedOldComplexId, newComplexId, expectedRevision, Operation.REASSIGN, false);
    }

    private static Result change(KOMEWorldData data, String buildId, String expectedOldComplexId,
            String newComplexId, long expectedRevision, Operation operation, boolean publish) {
        if (data == null) return Result.failure(Status.WORLD_DATA_MISSING, "World data is required.");
        String buildKey = KOMETacticalIds.buildLookup(buildId);
        String targetKey = canonicalComplexId(newComplexId);
        String expectedOld = canonicalComplexId(expectedOldComplexId);
        if (buildKey.isEmpty() || (operation != Operation.UNASSIGN && targetKey.isEmpty())
                || (operation == Operation.REASSIGN && expectedOld.isEmpty())) {
            return Result.failure(Status.INVALID_ID, "Required identities cannot be blank.");
        }
        synchronized (data) {
            if (data.isWriteBlocked()) return Result.failure(Status.WRITE_BLOCKED, data.getLoadFailureReason());
            KOMETacticalConfiguration candidate = data.getTacticalConfigurationSnapshot();
            if (candidate.getRevision() != expectedRevision) {
                return Result.failure(Status.STALE_CONFIGURATION_REVISION, "Tactical configuration has changed; refresh the snapshot.");
            }
            String oldComplexId = candidate.findAssignedComplexId(buildKey).orElse(null);
            if (operation != Operation.UNASSIGN) {
                KOMESiegeComplex target = candidate.findComplex(targetKey);
                if (target == null) return Result.failure(Status.COMPLEX_MISSING, "Target Siege Complex does not exist.");
                EnumSet<KOMETacticalGateReferenceResolver.Code> problems =
                    KOMETacticalGateReferenceResolver.buildProblems(data.getBuild(buildKey), target);
                if (!problems.isEmpty()) {
                    Status status = Status.valueOf(problems.iterator().next().name());
                    return Result.failure(status, "Build cannot support this Siege Complex: " + status);
                }
                if (operation == Operation.ASSIGN && oldComplexId != null && !oldComplexId.equals(targetKey)) {
                    return Result.failure(Status.BUILD_ALREADY_ASSIGNED, "Build is assigned to " + oldComplexId + "; use explicit reassignment.");
                }
                if (operation == Operation.REASSIGN && !expectedOld.equals(oldComplexId)) {
                    return Result.failure(Status.EXPECTED_ASSIGNMENT_MISMATCH, "Build is not assigned to the expected old Siege Complex.");
                }
            }
            boolean unchanged = operation == Operation.UNASSIGN ? oldComplexId == null : targetKey.equals(oldComplexId);
            if (unchanged) return new Result(Status.NO_CHANGE, "No persistent change.", buildKey, oldComplexId,
                operation == Operation.UNASSIGN ? null : targetKey, candidate.getRevision(), Collections.emptyList());
            if (candidate.getRevision() == Long.MAX_VALUE) {
                return Result.failure(Status.REVISION_EXHAUSTED, "Tactical configuration revision is exhausted.");
            }
            if (operation == Operation.ASSIGN) candidate.assignBuild(buildKey, targetKey);
            else if (operation == Operation.UNASSIGN) candidate.unassignBuild(buildKey);
            else candidate.reassignBuild(buildKey, expectedOld, targetKey);

            List<KOMETacticalGateReferenceResolver.Diagnostic> affected = oldComplexId == null ? Collections.emptyList()
                : KOMETacticalGateReferenceResolver.membershipImpact(data, candidate, buildKey, oldComplexId);
            Result result = new Result(Status.CHANGED, "Membership updated.", buildKey, oldComplexId,
                operation == Operation.UNASSIGN ? null : targetKey, candidate.getRevision(), affected);
            if (!publish) return result;
            try {
                data.publishTacticalMembership(expectedRevision, candidate);
            } catch (RuntimeException failure) {
                return Result.failure(Status.COMMIT_FAILED, "Membership publication failed: " + failure.getMessage());
            }
            return result;
        }
    }

    private static String canonicalComplexId(String id) {
        return id == null ? "" : id.trim().toUpperCase(Locale.ROOT);
    }

    public static final class Result {
        private final Status status;
        private final String message, buildId, oldComplexId, newComplexId;
        private final long revision;
        private final List<KOMETacticalGateReferenceResolver.Diagnostic> affectedReferences;

        private Result(Status status, String message, String buildId, String oldComplexId, String newComplexId,
                long revision, List<KOMETacticalGateReferenceResolver.Diagnostic> affectedReferences) {
            this.status = status; this.message = message; this.buildId = buildId;
            this.oldComplexId = oldComplexId; this.newComplexId = newComplexId; this.revision = revision;
            this.affectedReferences = affectedReferences;
        }
        private static Result failure(Status status, String message) {
            return new Result(status, message, null, null, null, -1L, Collections.emptyList());
        }
        public Status getStatus() { return status; }
        public String getMessage() { return message; }
        public boolean isSuccessful() { return status == Status.CHANGED || status == Status.NO_CHANGE; }
        public boolean isChanged() { return status == Status.CHANGED; }
        public String getBuildId() { return buildId; }
        public String getOldComplexId() { return oldComplexId; }
        public String getNewComplexId() { return newComplexId; }
        /** Committed/current revision on success; -1 on rejection. */
        public long getRevision() { return revision; }
        public List<KOMETacticalGateReferenceResolver.Diagnostic> getAffectedReferences() { return affectedReferences; }
    }
}
