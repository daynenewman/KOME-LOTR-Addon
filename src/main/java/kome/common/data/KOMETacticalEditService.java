package kome.common.data;

import java.util.ArrayList;
import java.util.List;
import kome.common.siege.KOMEDefensiveGateRef;
import kome.common.siege.KOMESiegeComplex;
import kome.common.siege.KOMESiegeConnection;
import kome.common.siege.KOMESiegeGateUsage;
import kome.common.siege.KOMESiegeReadinessEvaluator;
import kome.common.siege.validation.KOMEValidationIssue;
import kome.common.siege.validation.KOMEValidationResult;
import kome.common.tactical.KOMEForceDeploymentAreaValidator;
import kome.common.tactical.KOMETacticalConfiguration;
import kome.common.tactical.KOMETacticalActivityLock;
import kome.common.tactical.edit.*;

/** Narrow definition/membership commit boundary. Session layer owns permission and server-thread enforcement. */
public final class KOMETacticalEditService {
    public enum Status { CHANGED, NO_CHANGE, STALE_STORE, STALE_OBJECT, INVALID_DRAFT, REJECTED, COMMIT_FAILED, ACTIVITY_LOCKED }
    private KOMETacticalEditService() { }

    public static KOMETacticalEditPreflight preflightCreation(KOMEWorldData data, KOMETacticalEditScope scope,
            KOMETacticalEditDraft draft, long baseRevision) {
        synchronized (data) {
            List<String> issues = new ArrayList<String>();
            boolean admission = true, structural = false;
            try {
                data.ensureWritable(); draft.requireScope(scope); requireUnlocked(data, scope); KOMETacticalEditWire.encodeDraft(draft);
                if (draft.getMembershipAction() != null || draft.getObjectRevision() != 0L) throw new IllegalArgumentException("Invalid new definition.");
                KOMETacticalConfiguration candidate = data.getTacticalConfigurationSnapshot();
                if (candidate.getRevision() != baseRevision) { admission = false; issues.add("Configuration changed; cancel and reopen."); }
                if (draft.getComplex() != null) {
                    requireNewGateSelections(data, candidate, null, draft.getComplex());
                    candidate.addComplex(draft.getComplex());
                    KOMESiegeReadinessEvaluator.Report report = KOMESiegeReadinessResolver.evaluate(data, candidate, draft.getComplex(), null);
                    structural = report.getGeometryValidation().isValid();
                    for (KOMESiegeReadinessEvaluator.Diagnostic d : report.getBlockingDiagnostics()) issues.add(describe(d));
                    for (KOMESiegeReadinessEvaluator.Diagnostic d : report.getWarnings()) issues.add("Warning: " + describe(d));
                    return new KOMETacticalEditPreflight(admission, structural, state(report), issues);
                } else {
                    candidate.addForceDeploymentArea(draft.getArea());
                    KOMEValidationResult validation = new KOMEForceDeploymentAreaValidator().validate(draft.getArea());
                    structural = validation.isValid();
                    for (KOMEValidationIssue issue : validation.getIssues()) issues.add(issue.getMessage());
                }
            } catch (RuntimeException invalid) { admission = false; issues.add(invalid.getMessage()); }
            return new KOMETacticalEditPreflight(admission, structural,
                structural ? KOMETacticalEditPreflight.State.VALID : KOMETacticalEditPreflight.State.INVALID, issues);
        }
    }

    public static Result saveCreation(KOMEWorldData data, KOMETacticalEditScope scope, KOMETacticalEditDraft draft, long baseRevision) {
        synchronized (data) {
            if (data.getTacticalConfigurationSnapshot().getRevision() != baseRevision) return new Result(Status.STALE_STORE, null);
            KOMETacticalEditPreflight preflight = preflightCreation(data, scope, draft, baseRevision);
            if (!preflight.canSave()) return new Result(Status.REJECTED, preflight);
            try {
                KOMETacticalConfiguration candidate = data.getTacticalConfigurationSnapshot();
                if (draft.getComplex() != null) {
                    candidate.addComplex(draft.atRevision(1L).getComplex());
                    data.publishTacticalComplexLifecycle(baseRevision, scope.getTargetId(), candidate, true);
                } else {
                    candidate.addForceDeploymentArea(draft.atRevision(1L).getArea());
                    data.publishTacticalAreaLifecycle(baseRevision, scope.getTargetId(), candidate, true);
                }
                return new Result(Status.CHANGED, preflight);
            } catch (KOMETacticalActivityLock.LockedException locked) { return lockedResult(locked); }
            catch (RuntimeException failure) { return new Result(Status.COMMIT_FAILED, preflight); }
        }
    }

    public static Result deleteArea(KOMEWorldData data, KOMETacticalEditScope scope, long baseRevision, long objectRevision) {
        return deleteDefinition(data, scope, baseRevision, objectRevision);
    }

    public static Result deleteDefinition(KOMEWorldData data, KOMETacticalEditScope scope, long baseRevision, long objectRevision) {
        synchronized (data) {
            KOMETacticalConfiguration candidate = data.getTacticalConfigurationSnapshot();
            if (candidate.getRevision() != baseRevision) return new Result(Status.STALE_STORE, null);
            try {
                data.ensureWritable();
                requireUnlocked(data, scope);
                KOMETacticalEditDraft original = current(candidate, scope);
                if (original.getObjectRevision() != objectRevision) return new Result(Status.STALE_OBJECT, null);
                if (original.getComplex() != null) candidate.removeComplex(scope.getTargetId());
                else candidate.removeForceDeploymentArea(scope.getTargetId());
            } catch (RuntimeException rejected) {
                return new Result(Status.REJECTED, new KOMETacticalEditPreflight(false, true,
                    KOMETacticalEditPreflight.State.INVALID, java.util.Collections.singletonList(rejected.getMessage())));
            }
            try {
                if (scope.getType() == KOMETacticalEditScope.Type.SIEGE_COMPLEX)
                    data.publishTacticalComplexLifecycle(baseRevision, scope.getTargetId(), candidate, false);
                else data.publishTacticalAreaLifecycle(baseRevision, scope.getTargetId(), candidate, false);
                return new Result(Status.CHANGED, null);
            } catch (KOMETacticalActivityLock.LockedException locked) { return lockedResult(locked); }
            catch (RuntimeException failure) { return new Result(Status.COMMIT_FAILED, null); }
        }
    }

    public static KOMETacticalEditDraft current(KOMETacticalConfiguration configuration, KOMETacticalEditScope scope) {
        KOMETacticalEditDraft draft = scope.getType() == KOMETacticalEditScope.Type.SIEGE_COMPLEX
            ? new KOMETacticalEditDraft(configuration.findComplex(scope.getComplexId()))
            : new KOMETacticalEditDraft(configuration.findForceDeploymentArea(scope.getTargetId()));
        draft.requireScope(scope); return draft;
    }

    /** Invalid authored geometry remains inspectable/saveable if representable and store invariants hold. */
    public static KOMETacticalEditPreflight preflight(KOMEWorldData data, KOMETacticalEditScope scope,
            KOMETacticalEditDraft draft, long baseRevision, long baseObjectRevision) {
        synchronized (data) {
            List<String> issues = new ArrayList<String>();
            boolean admission = true, structural = false;
            KOMETacticalEditPreflight.State state = KOMETacticalEditPreflight.State.INVALID;
            try {
                data.ensureWritable(); draft.requireScope(scope); requireUnlocked(data, scope); KOMETacticalEditWire.encodeDraft(draft);
                KOMETacticalConfiguration candidate = data.getTacticalConfigurationSnapshot();
                KOMETacticalEditDraft original = current(candidate, scope);
                if (candidate.getRevision() != baseRevision) { admission = false; issues.add("STALE_STORE"); }
                if (original.getObjectRevision() != baseObjectRevision) { admission = false; issues.add("STALE_OBJECT"); }
                if (draft.getObjectRevision() != baseObjectRevision) throw new IllegalArgumentException("Client changed object revision.");
                if (draft.getMembershipAction() != null) {
                    if (!draft.sameDefinition(original)) throw new IllegalArgumentException("Membership commit cannot also change definition.");
                    requireScopedUnassignment(candidate, scope, draft);
                    KOMETacticalMembershipService.Result preview = membership(data, draft, baseRevision, false);
                    if (!preview.isSuccessful()) { admission = false; issues.add(preview.getStatus().name() + ": " + preview.getMessage()); }
                    else applyPreview(candidate, draft);
                } else if (!draft.sameDefinition(original)) {
                    if (draft.getComplex() != null) requireNewGateSelections(data, candidate, original.getComplex(), draft.getComplex());
                    if (draft.getComplex() != null) candidate.replaceComplex(draft.getComplex());
                    else candidate.replaceForceDeploymentArea(draft.getArea());
                }
                if (draft.getComplex() != null) {
                    KOMESiegeReadinessEvaluator.Report report = KOMESiegeReadinessResolver.evaluate(data, candidate,
                        candidate.findComplex(scope.getComplexId()), null);
                    structural = report.getGeometryValidation().isValid();
                    state = report.isReady() ? KOMETacticalEditPreflight.State.READY
                        : !structural || !report.isConfigurationValid() ? KOMETacticalEditPreflight.State.INVALID
                        : KOMETacticalEditPreflight.State.INCOMPLETE;
                    for (KOMESiegeReadinessEvaluator.Diagnostic d : report.getBlockingDiagnostics()) issues.add(describe(d));
                    for (KOMESiegeReadinessEvaluator.Diagnostic d : report.getWarnings()) issues.add("WARNING " + describe(d));
                } else {
                    KOMEValidationResult validation = new KOMEForceDeploymentAreaValidator().validate(draft.getArea());
                    structural = validation.isValid();
                    state = structural ? KOMETacticalEditPreflight.State.VALID : KOMETacticalEditPreflight.State.INVALID;
                    for (KOMEValidationIssue issue : validation.getIssues()) issues.add(issue.getMessage());
                }
            } catch (RuntimeException invalid) {
                admission = false; issues.add("SAVE_REJECTED " + invalid.getMessage());
            }
            return new KOMETacticalEditPreflight(admission, structural, state, issues);
        }
    }

    public static Result save(KOMEWorldData data, KOMETacticalEditScope scope, KOMETacticalEditDraft draft,
            long baseRevision, long baseObjectRevision) {
        synchronized (data) {
            KOMETacticalConfiguration candidate = data.getTacticalConfigurationSnapshot();
            if (candidate.getRevision() != baseRevision) return new Result(Status.STALE_STORE, null);
            KOMETacticalEditDraft original;
            try { original = current(candidate, scope); }
            catch (RuntimeException invalid) { return new Result(Status.STALE_OBJECT, null); }
            if (original.getObjectRevision() != baseObjectRevision) return new Result(Status.STALE_OBJECT, null);
            KOMETacticalEditPreflight preflight = preflight(data, scope, draft, baseRevision, baseObjectRevision);
            if (!preflight.canSave()) return new Result(Status.REJECTED, preflight);
            if (draft.getMembershipAction() != null) {
                KOMETacticalMembershipService.Result membership = membership(data, draft, baseRevision, true);
                List<String> impacts = new ArrayList<String>(preflight.getDiagnostics());
                if (!membership.isSuccessful()) impacts.add(membership.getStatus().name() + ": " + membership.getMessage());
                for (KOMETacticalGateReferenceResolver.Diagnostic affected : membership.getAffectedReferences()) {
                    impacts.add("AFFECTED " + affected.getComplexId() + "/" + affected.getConnectionId()
                        + " " + affected.getBuildId() + "/" + affected.getGateRecordId() + " " + affected.getCodes());
                }
                return new Result(membership.isChanged() ? Status.CHANGED : membership.isSuccessful() ? Status.NO_CHANGE : Status.REJECTED,
                    new KOMETacticalEditPreflight(membership.isSuccessful(), preflight.isStructurallyValid(), preflight.getState(), impacts));
            }
            if (draft.sameDefinition(original)) return new Result(Status.NO_CHANGE, preflight);
            if (baseObjectRevision == Long.MAX_VALUE || baseRevision == Long.MAX_VALUE) return new Result(Status.REJECTED, preflight);
            KOMETacticalEditDraft updated = draft.atRevision(baseObjectRevision + 1L);
            try {
                if (updated.getComplex() != null) candidate.replaceComplex(updated.getComplex());
                else candidate.replaceForceDeploymentArea(updated.getArea());
                data.publishTacticalDefinition(baseRevision, scope.getComplexId(),
                    scope.getComplexId() == null ? scope.getTargetId() : null, candidate);
                return new Result(Status.CHANGED, preflight);
            } catch (KOMETacticalActivityLock.LockedException locked) { return lockedResult(locked); }
            catch (RuntimeException failure) { return new Result(Status.COMMIT_FAILED, preflight); }
        }
    }

    public static void requireUnlocked(KOMEWorldData data, KOMETacticalEditScope scope) {
        if (scope.getType() == KOMETacticalEditScope.Type.SIEGE_COMPLEX)
            KOMETacticalActivityLock.requireUnlocked(data, scope.getTileId(), scope.getDimensionId(), scope.getComplexId());
        else KOMETacticalActivityLock.requireAreaUnlocked(data, scope.getTargetId());
    }
    private static Result lockedResult(KOMETacticalActivityLock.LockedException locked) {
        return new Result(Status.ACTIVITY_LOCKED, new KOMETacticalEditPreflight(false, true,
            KOMETacticalEditPreflight.State.INVALID, java.util.Collections.singletonList(locked.getMessage())));
    }

    private static String describe(KOMESiegeReadinessEvaluator.Diagnostic d) {
        List<String> identities = new ArrayList<String>();
        if (d.getSegmentId() != null) identities.add("segment " + d.getSegmentId());
        if (d.getConnectionId() != null) identities.add("connection " + d.getConnectionId());
        if (d.getBuildId() != null) identities.add("Build " + d.getBuildId());
        if (d.getGateRecordId() != null) identities.add("gate record " + d.getGateRecordId());
        return d.getCode() + ": " + d.getMessage() + (identities.isEmpty() ? "" : " (" + String.join(", ", identities) + ")")
            + (d.getSubjectIds().isEmpty() ? "" : " " + d.getSubjectIds());
    }
    /** Existing unresolved authored pairs survive. Newly selected pairs must belong to this complex now. */
    private static void requireNewGateSelections(KOMEWorldData data, KOMETacticalConfiguration config,
            KOMESiegeComplex original, KOMESiegeComplex draft) {
        KOMESiegeGateUsage.requireNoNewConflicts(original, draft);
        for (KOMESiegeConnection connection : draft.getConnections()) {
            if (!connection.isGated()) continue;
            KOMEDefensiveGateRef ref = connection.getGateRef().get();
            boolean retained = false;
            if (original != null) for (KOMESiegeConnection old : original.getConnections())
                if (old.getId().equals(connection.getId()) && old.getGateRef().isPresent()
                        && old.getGateRef().get().equals(ref)) retained = true;
            if (retained) continue;
            KOMEPlayerBuild build = data.getBuild(ref.getBuildId());
            if (!KOMETacticalGateReferenceResolver.buildProblems(build, draft).isEmpty()
                    || !config.findAssignedComplexId(ref.getBuildId()).orElse("").equals(draft.getComplexId())
                    || build.getDefensiveGateRecord(ref.getGateRecordId()) == null)
                throw new IllegalArgumentException("Connection " + connection.getId() + ": select an existing gate record from an active Defensive Build assigned to this complex.");
        }
    }
    private static KOMETacticalEditPreflight.State state(KOMESiegeReadinessEvaluator.Report report) {
        return report.isReady() ? KOMETacticalEditPreflight.State.READY
            : !report.getGeometryValidation().isValid() || !report.isConfigurationValid()
                ? KOMETacticalEditPreflight.State.INVALID : KOMETacticalEditPreflight.State.INCOMPLETE;
    }
    private static void requireScopedUnassignment(KOMETacticalConfiguration candidate, KOMETacticalEditScope scope, KOMETacticalEditDraft draft) {
        String old = candidate.findAssignedComplexId(draft.getBuildId()).orElse(null);
        if (draft.getMembershipAction() == KOMETacticalEditDraft.MembershipAction.UNASSIGN
                && old != null && !scope.getComplexId().equals(old)) throw new IllegalArgumentException("Build belongs to another scope.");
    }
    private static void applyPreview(KOMETacticalConfiguration candidate, KOMETacticalEditDraft draft) {
        switch (draft.getMembershipAction()) {
            case ASSIGN: candidate.assignBuild(draft.getBuildId(), draft.getComplex().getComplexId()); break;
            case UNASSIGN: candidate.unassignBuild(draft.getBuildId()); break;
            case REASSIGN: candidate.reassignBuild(draft.getBuildId(), draft.getExpectedOldComplexId(), draft.getComplex().getComplexId()); break;
            default: throw new IllegalArgumentException("Invalid membership action.");
        }
    }
    private static KOMETacticalMembershipService.Result membership(KOMEWorldData data, KOMETacticalEditDraft draft, long revision, boolean commit) {
        switch (draft.getMembershipAction()) {
            case ASSIGN: return commit ? KOMETacticalMembershipService.assignBuild(data, draft.getBuildId(), draft.getComplex().getComplexId(), revision)
                : KOMETacticalMembershipService.previewAssignBuild(data, draft.getBuildId(), draft.getComplex().getComplexId(), revision);
            case UNASSIGN: return commit ? KOMETacticalMembershipService.unassignBuild(data, draft.getBuildId(), revision)
                : KOMETacticalMembershipService.previewUnassignBuild(data, draft.getBuildId(), revision);
            case REASSIGN: return commit ? KOMETacticalMembershipService.reassignBuild(data, draft.getBuildId(), draft.getExpectedOldComplexId(), draft.getComplex().getComplexId(), revision)
                : KOMETacticalMembershipService.previewReassignBuild(data, draft.getBuildId(), draft.getExpectedOldComplexId(), draft.getComplex().getComplexId(), revision);
            default: throw new IllegalArgumentException("Invalid membership action.");
        }
    }
    public static final class Result {
        private final Status status;
        private final KOMETacticalEditPreflight preflight;
        private Result(Status status, KOMETacticalEditPreflight preflight) { this.status = status; this.preflight = preflight; }
        public Status getStatus() { return status; }
        public KOMETacticalEditPreflight getPreflight() { return preflight; }
    }
}
