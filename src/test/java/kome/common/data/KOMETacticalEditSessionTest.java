package kome.common.data;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import kome.common.siege.*;
import kome.common.siege.geometry.KOMEPolygon;
import kome.common.siege.geometry.KOMEPolygonPrism;
import kome.common.tactical.KOMEForceDeploymentArea;
import kome.common.tactical.KOMETacticalConfiguration;
import kome.common.tactical.KOMETacticalConfigurationCodec;
import kome.common.tactical.edit.*;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import static kome.common.data.KOMETacticalEditorFixtures.*;
import static kome.common.tactical.edit.KOMETacticalEditSessionManager.Status.*;
import static org.junit.Assert.*;

public class KOMETacticalEditSessionTest {
    @Rule public final KOMETileTestResources tiles = new KOMETileTestResources();
    private CountingWorld data;
    private Actor actor;
    private KOMETacticalEditSessionManager manager;
    @Before public void setup() { data = world(); actor = new Actor(); manager = new KOMETacticalEditSessionManager(3, 2); }
    private KOMETacticalEditSnapshot open(KOMETacticalEditScope scope) {
        KOMETacticalEditSessionManager.Result result = manager.handle(actor, data, KOMETacticalEditRequest.open(scope));
        assertEquals(OPENED, result.getStatus()); return result.getSnapshot();
    }
    private KOMETacticalEditSessionManager.Result action(KOMETacticalEditRequest.Action action, KOMETacticalEditSnapshot snapshot) {
        return manager.handle(actor, data, KOMETacticalEditRequest.action(action, snapshot));
    }
    private KOMETacticalEditSnapshot edit(KOMETacticalEditSnapshot snapshot, KOMETacticalEditDraft draft) {
        KOMETacticalEditSessionManager.Result result = manager.handle(actor, data, KOMETacticalEditRequest.update(snapshot, draft));
        assertEquals(UPDATED, result.getStatus()); return result.getSnapshot();
    }
    private NBTTagCompound state() { return KOMETacticalConfigurationCodec.encode(data.getTacticalConfigurationSnapshot()); }
    private void assertUntouched(NBTTagCompound before) { assertEquals(before, state()); assertFalse(data.isDirty()); assertEquals(0, data.dirtyCalls); }

    @Test public void sessionHasPlayerScopeTokenRevisionsAndBoundedOneDraft() {
        KOMETacticalEditSnapshot s = open(areaScope("field"));
        assertEquals(actor.id, s.getPlayerId()); assertNotEquals(new UUID(0, 0), s.getToken());
        assertEquals("FIELD", s.getScope().getTargetId()); assertNull(s.getScope().getComplexId());
        assertEquals(revision(data), s.getBaseRevision()); assertEquals(7L, s.getBaseObjectRevision()); assertEquals(0L, s.getDraftSequence());
        assertEquals(1, manager.getSessionCount()); assertFalse(data.isDirty());
    }
    @Test public void wrongPlayerTokenAndScopeCannotAccessDraft() {
        KOMETacticalEditSnapshot s = open(areaScope("FIELD"));
        Actor stranger = new Actor();
        assertEquals(INVALID_SESSION, manager.handle(stranger, data, KOMETacticalEditRequest.action(KOMETacticalEditRequest.Action.REFRESH, s)).getStatus());
        assertNull(manager.handle(stranger, data, KOMETacticalEditRequest.action(KOMETacticalEditRequest.Action.REFRESH, s)).getSnapshot());
        assertEquals(INVALID_SESSION, manager.handle(actor, data, new KOMETacticalEditRequest(KOMETacticalEditRequest.Action.SAVE,
            s.getScope(), UUID.randomUUID(), 0, new byte[0])).getStatus());
        assertEquals(INVALID_SESSION, manager.handle(actor, data, new KOMETacticalEditRequest(KOMETacticalEditRequest.Action.REFRESH,
            complexScope("A"), s.getToken(), 0, new byte[0])).getStatus());
    }
    @Test public void conflictingOpenIsExplicitAndSameScopeReopenRetainsDraft() {
        KOMETacticalEditSnapshot s = edit(open(areaScope("FIELD")), new KOMETacticalEditDraft(area("FIELD", "Edited", 7)));
        assertEquals(SESSION_ACTIVE, manager.handle(actor, data, KOMETacticalEditRequest.open(complexScope("A"))).getStatus());
        KOMETacticalEditSnapshot reopened = open(areaScope("FIELD"));
        assertEquals(s.getToken(), reopened.getToken()); assertEquals("Edited", reopened.getDraft().getArea().getLabel());
    }
    @Test public void boundedSessionCountRejectsAnotherPlayerWithoutReplacingExisting() {
        open(areaScope("FIELD")); Actor second = new Actor(), third = new Actor();
        assertEquals(OPENED, manager.handle(second, data, KOMETacticalEditRequest.open(complexScope("A"))).getStatus());
        assertEquals(LIMIT_REACHED, manager.handle(third, data, KOMETacticalEditRequest.open(complexScope("B"))).getStatus());
        assertEquals(2, manager.getSessionCount());
    }
    @Test public void expiryDiscardsDraftWithoutPublishing() {
        NBTTagCompound before = state();
        KOMETacticalEditSnapshot s = edit(open(areaScope("FIELD")), new KOMETacticalEditDraft(area("FIELD", "Discard", 7)));
        assertTrue(manager.tick().isEmpty()); assertTrue(manager.tick().isEmpty());
        assertEquals(EXPIRED, manager.tick().get(0).getStatus()); assertEquals(0, manager.getSessionCount());
        assertEquals(INVALID_SESSION, action(KOMETacticalEditRequest.Action.SAVE, s).getStatus()); assertUntouched(before);
    }
    @Test public void activityRefreshesExpiryAndWrongTokenDoesNot() {
        KOMETacticalEditSnapshot s = open(areaScope("FIELD")); manager.tick(); manager.tick();
        s = action(KOMETacticalEditRequest.Action.REFRESH, s).getSnapshot();
        manager.tick(); manager.tick(); assertEquals(1, manager.getSessionCount());
        manager.handle(actor, data, new KOMETacticalEditRequest(KOMETacticalEditRequest.Action.REFRESH, s.getScope(), UUID.randomUUID(), 0, new byte[0]));
        assertEquals(1, manager.tick().size());
    }
    @Test public void draftAndNbtCopiesCannotMutateLiveStateOrDirtyIt() {
        NBTTagCompound before = state();
        KOMETacticalEditSnapshot s = edit(open(areaScope("FIELD")), new KOMETacticalEditDraft(area("FIELD", "Draft only", 7)));
        s.getDraft().encode().setString("AreaId", "HACK");
        assertEquals("Draft only", s.getDraft().getArea().getLabel()); assertEquals(1L, s.getDraftSequence()); assertUntouched(before);
    }
    @Test public void cancelAndLifecycleClosureDiscardWithoutPublishing() {
        NBTTagCompound before = state();
        KOMETacticalEditSnapshot s = edit(open(areaScope("FIELD")), new KOMETacticalEditDraft(area("FIELD", "Discard", 7)));
        assertEquals(CANCELLED, action(KOMETacticalEditRequest.Action.CANCEL, s).getStatus()); assertEquals(0, manager.getSessionCount());
        open(complexScope("A")); assertTrue(manager.closePlayer(actor.id).getSnapshot().isClosed()); assertUntouched(before);
    }
    @Test public void savePublishesOnceAndSurvivesWorldPersistence() {
        long before = revision(data);
        KOMETacticalEditSnapshot s = edit(open(areaScope("FIELD")), new KOMETacticalEditDraft(area("FIELD", "Saved", 7)));
        assertEquals(SAVED, action(KOMETacticalEditRequest.Action.SAVE, s).getStatus());
        assertEquals(before + 1, revision(data)); assertEquals(8, data.getTacticalConfigurationSnapshot().findForceDeploymentArea("FIELD").getRevision());
        assertEquals("Saved", data.getTacticalConfigurationSnapshot().findForceDeploymentArea("FIELD").getLabel());
        assertTrue(data.isDirty()); assertEquals(1, data.dirtyCalls); assertEquals(0, manager.getSessionCount());
        KOMEWorldData loaded = new KOMEWorldData("roundTrip"); loaded.readFromNBT(KOMETacticalMembershipFixtures.save(data));
        assertEquals(state(), KOMETacticalConfigurationCodec.encode(loaded.getTacticalConfigurationSnapshot()));
    }
    @Test public void equivalentDecodedDefinitionSaveIsNoOp() {
        NBTTagCompound before = state();
        KOMETacticalEditSnapshot s = open(areaScope("FIELD"));
        s = edit(s, KOMETacticalEditDraft.decode(s.getDraft().encode()));
        assertEquals(NO_CHANGE, action(KOMETacticalEditRequest.Action.SAVE, s).getStatus()); assertUntouched(before);
    }
    @Test public void staleSaveRetainsDraftAndCannotOverwriteNewerAdminWork() {
        KOMETacticalEditSnapshot old = edit(open(areaScope("FIELD")), new KOMETacticalEditDraft(area("FIELD", "Old draft", 7)));
        Actor other = new Actor();
        KOMETacticalEditSnapshot newer = manager.handle(other, data, KOMETacticalEditRequest.open(areaScope("FIELD"))).getSnapshot();
        newer = manager.handle(other, data, KOMETacticalEditRequest.update(newer, new KOMETacticalEditDraft(area("FIELD", "New data", 7)))).getSnapshot();
        assertEquals(SAVED, manager.handle(other, data, KOMETacticalEditRequest.action(KOMETacticalEditRequest.Action.SAVE, newer)).getStatus());
        data.setDirty(false); data.dirtyCalls = 0; NBTTagCompound before = state();
        KOMETacticalEditSessionManager.Result stale = action(KOMETacticalEditRequest.Action.SAVE, old);
        assertEquals(STALE_STORE, stale.getStatus()); assertFalse(stale.getSnapshot().isClosed());
        assertEquals("Old draft", stale.getSnapshot().getDraft().getArea().getLabel()); assertEquals(1, manager.getSessionCount()); assertUntouched(before);
    }
    @Test public void objectRevisionChecksRejectForgedUpdateAndIncorrectSaveBase() {
        NBTTagCompound before = state(); KOMETacticalEditSnapshot s = open(areaScope("FIELD"));
        assertEquals(STALE_OBJECT, manager.handle(actor, data, KOMETacticalEditRequest.update(s, new KOMETacticalEditDraft(area("FIELD", "Forged", 8)))).getStatus());
        assertEquals(KOMETacticalEditService.Status.STALE_OBJECT, KOMETacticalEditService.save(data, s.getScope(), s.getDraft(), s.getBaseRevision(), 6).getStatus());
        assertUntouched(before);
    }
    @Test public void staleSequenceCannotReplaceDraftOrSaveIt() {
        KOMETacticalEditSnapshot original = open(areaScope("FIELD"));
        KOMETacticalEditSnapshot current = edit(original, new KOMETacticalEditDraft(area("FIELD", "Current", 7)));
        assertEquals(STALE_SEQUENCE, manager.handle(actor, data, KOMETacticalEditRequest.update(original, new KOMETacticalEditDraft(area("FIELD", "Stale", 7)))).getStatus());
        assertEquals(STALE_SEQUENCE, action(KOMETacticalEditRequest.Action.SAVE, original).getStatus());
        assertEquals("Current", action(KOMETacticalEditRequest.Action.REFRESH, current).getSnapshot().getDraft().getArea().getLabel());
        assertFalse(data.isDirty());
    }
    @Test public void permissionAndDimensionAreRecheckedForAuthoritativeRequests() {
        NBTTagCompound before = state(); KOMETacticalEditSnapshot s = open(areaScope("FIELD"));
        actor.authorized = false; assertEquals(DENIED, action(KOMETacticalEditRequest.Action.SAVE, s).getStatus());
        actor.authorized = true; actor.dimension++; assertEquals(WRONG_DIMENSION, action(KOMETacticalEditRequest.Action.SAVE, s).getStatus());
        assertUntouched(before);
    }
    @Test public void malformedWireDraftIsRejectedWithoutChangingSequenceOrData() {
        NBTTagCompound before = state(); KOMETacticalEditSnapshot s = open(areaScope("FIELD"));
        assertEquals(INVALID_DRAFT, manager.handle(actor, data, new KOMETacticalEditRequest(KOMETacticalEditRequest.Action.UPDATE,
            s.getScope(), s.getToken(), 0, new byte[] {1, 2, 3})).getStatus());
        assertEquals(0, action(KOMETacticalEditRequest.Action.REFRESH, s).getSnapshot().getDraftSequence()); assertUntouched(before);
    }
    @Test public void ownerCannotBeMovedByDraftUpdate() {
        KOMETacticalEditSnapshot s = open(areaScope("FIELD"));
        KOMETacticalEditDraft wrong = new KOMETacticalEditDraft(new KOMEForceDeploymentArea("FIELD", "T101", dimension(), "Wrong", s.getDraft().getArea().getPrism(), 7));
        assertEquals(INVALID_DRAFT, manager.handle(actor, data, KOMETacticalEditRequest.update(s, wrong)).getStatus());
    }
    @Test public void ordinaryTileAreaScopeNeedsNoComplex() {
        KOMETacticalConfiguration onlyAreas = new KOMETacticalConfiguration(); onlyAreas.addForceDeploymentArea(area("FIELD", "Ordinary tile", 7));
        KOMETacticalMembershipFixtures.installConfiguration(data, data, onlyAreas);
        KOMETacticalEditSnapshot s = open(areaScope("FIELD"));
        assertTrue(data.getTacticalConfigurationSnapshot().getComplexesById().isEmpty());
        assertEquals(KOMETacticalEditPreflight.State.VALID, action(KOMETacticalEditRequest.Action.PREFLIGHT, s).getSnapshot().getPreflight().getState());
    }
    @Test public void incompleteComplexIsSaveableAndReadinessIsSeparate() {
        KOMETacticalEditSnapshot s = open(complexScope("EMPTY"));
        KOMETacticalEditPreflight preflight = action(KOMETacticalEditRequest.Action.PREFLIGHT, s).getSnapshot().getPreflight();
        assertTrue(preflight.canSave()); assertTrue(preflight.isStructurallyValid()); assertFalse(preflight.isReady());
        assertEquals(KOMETacticalEditPreflight.State.INCOMPLETE, preflight.getState());
        s = edit(s, new KOMETacticalEditDraft(KOMESiegeReadinessFixtures.preferred(s.getDraft().getComplex(), "UNRESOLVED")));
        assertEquals(SAVED, action(KOMETacticalEditRequest.Action.SAVE, s).getStatus());
        assertEquals("UNRESOLVED", data.getTacticalConfigurationSnapshot().findComplex("EMPTY").getPreferredForceDeploymentAreaId().get());
    }
    @Test public void readyComplexPreflightReusesReadinessWithoutMutation() {
        assertTrue(KOMETacticalMembershipService.assignBuild(data, "B1", "A", revision(data)).isChanged());
        data.setDirty(false); data.dirtyCalls = 0; NBTTagCompound before = state();
        KOMETacticalEditPreflight p = action(KOMETacticalEditRequest.Action.PREFLIGHT, open(complexScope("A"))).getSnapshot().getPreflight();
        assertTrue(p.isReady()); assertTrue(p.canSave()); assertUntouched(before);
    }
    @Test public void baseMembershipSnapshotIsDetachedFromLaterStoreChanges() {
        assertTrue(KOMETacticalMembershipService.assignBuild(data, "B1", "A", revision(data)).isChanged());
        KOMETacticalEditSnapshot s = open(complexScope("A")); assertEquals(Collections.singletonList("B1"), s.getAssignedBuildIds());
        assertThrows(UnsupportedOperationException.class, () -> s.getAssignedBuildIds().clear());
        assertTrue(KOMETacticalMembershipService.unassignBuild(data, "B1", revision(data)).isChanged());
        assertEquals(Collections.singletonList("B1"), s.getAssignedBuildIds()); assertEquals(1, s.getTotalAssignedBuildCount());
    }
    @Test public void representableMalformedGeometryIsDiagnosedSeparatelyFromAdmission() {
        KOMETacticalEditSnapshot s = open(areaScope("FIELD"));
        KOMEForceDeploymentArea malformed = new KOMEForceDeploymentArea("FIELD", "T100", dimension(), "Invalid authored geometry",
            new KOMEPolygonPrism(new KOMEPolygon(Collections.emptyList()), 10, 0), 7);
        s = edit(s, new KOMETacticalEditDraft(malformed));
        KOMETacticalEditPreflight p = action(KOMETacticalEditRequest.Action.PREFLIGHT, s).getSnapshot().getPreflight();
        assertTrue(p.canSave()); assertFalse(p.isStructurallyValid()); assertEquals(KOMETacticalEditPreflight.State.INVALID, p.getState());
        assertFalse(p.getDiagnostics().isEmpty()); assertEquals(SAVED, action(KOMETacticalEditRequest.Action.SAVE, s).getStatus());
    }
    @Test public void storeCrossReferenceInvariantStillBlocksSave() {
        NBTTagCompound before = state(); KOMETacticalEditSnapshot s = open(complexScope("A"));
        s = edit(s, new KOMETacticalEditDraft(KOMESiegeReadinessFixtures.preferred(s.getDraft().getComplex(), "WRONG_TILE")));
        assertFalse(action(KOMETacticalEditRequest.Action.PREFLIGHT, s).getSnapshot().getPreflight().canSave());
        assertEquals(REJECTED, action(KOMETacticalEditRequest.Action.SAVE, s).getStatus()); assertUntouched(before);
    }
    @Test public void failedDirtyPublicationRollsBackDefinitionAndRevision() {
        NBTTagCompound before = state(); KOMETacticalEditSnapshot s = edit(open(areaScope("FIELD")), new KOMETacticalEditDraft(area("FIELD", "Fail", 7)));
        data.failDirty = true; assertEquals(COMMIT_FAILED, action(KOMETacticalEditRequest.Action.SAVE, s).getStatus());
        assertEquals(before, state()); assertFalse(data.isDirty()); assertEquals(1, manager.getSessionCount());
    }
    @Test public void membershipIntentIsDetachedAndUsesWorldAwareServiceOnSave() {
        NBTTagCompound before = state(); KOMETacticalEditSnapshot s = open(complexScope("A"));
        s = edit(s, s.getDraft().withMembership(KOMETacticalEditDraft.MembershipAction.ASSIGN, " b1 ", null));
        assertTrue(action(KOMETacticalEditRequest.Action.PREFLIGHT, s).getSnapshot().getPreflight().isReady()); assertUntouched(before);
        assertEquals(SAVED, action(KOMETacticalEditRequest.Action.SAVE, s).getStatus());
        assertEquals("A", data.getTacticalConfigurationSnapshot().findAssignedComplexId("B1").get());
        assertEquals(1, data.dirtyCalls); assertEquals(17L, data.getTacticalConfigurationSnapshot().findComplex("A").getRevision());
    }
    @Test public void invalidMembershipAndMixedDefinitionMembershipCannotBypassServices() {
        NBTTagCompound before = state(); KOMETacticalEditSnapshot s = open(complexScope("A"));
        s = edit(s, s.getDraft().withMembership(KOMETacticalEditDraft.MembershipAction.ASSIGN, "ABSENT", null));
        assertEquals(REJECTED, action(KOMETacticalEditRequest.Action.SAVE, s).getStatus());
        KOMETacticalEditDraft mixed = new KOMETacticalEditDraft(KOMESiegeReadinessFixtures.preferred(s.getDraft().getComplex(), "FIELD"))
            .withMembership(KOMETacticalEditDraft.MembershipAction.ASSIGN, "B1", null);
        s = edit(s, mixed); assertEquals(REJECTED, action(KOMETacticalEditRequest.Action.SAVE, s).getStatus()); assertUntouched(before);
    }
    @Test public void reassignmentAndUnassignmentPreserveConnectionsAndReportImpacts() {
        KOMETacticalMembershipFixtures.installConfiguration(data, data, membershipConfiguration());
        data.setDirty(false); data.dirtyCalls = 0;
        KOMETacticalEditSnapshot s = open(complexScope("B"));
        s = edit(s, s.getDraft().withMembership(KOMETacticalEditDraft.MembershipAction.REASSIGN, "B1", "A"));
        KOMETacticalEditSessionManager.Result saved = action(KOMETacticalEditRequest.Action.SAVE, s);
        assertEquals(SAVED, saved.getStatus()); assertTrue(saved.getSnapshot().getPreflight().getDiagnostics().stream().anyMatch(d -> d.startsWith("AFFECTED A/ENTRY")));
        assertTrue(data.getTacticalConfigurationSnapshot().findComplex("A").getConnections().get(0).getGateRef().isPresent());
        s = open(complexScope("B")); s = edit(s, s.getDraft().withMembership(KOMETacticalEditDraft.MembershipAction.UNASSIGN, "B1", null));
        assertEquals(SAVED, action(KOMETacticalEditRequest.Action.SAVE, s).getStatus());
        assertFalse(data.getTacticalConfigurationSnapshot().findAssignedComplexId("B1").isPresent());
    }
    private KOMETacticalConfiguration membershipConfiguration() {
        KOMETacticalConfiguration c = new KOMETacticalConfiguration();
        c.addComplex(KOMESiegeReadinessFixtures.minimal("A", "T100", dimension(), new KOMEDefensiveGateRef("B1", "G1")));
        c.addComplex(KOMESiegeReadinessFixtures.minimal("B", "T100", dimension(), null)); c.assignBuild("B1", "A"); return c;
    }
    @Test public void scopedUnassignmentCannotRemoveAnotherComplexMembership() {
        assertTrue(KOMETacticalMembershipService.assignBuild(data, "B1", "A", revision(data)).isChanged());
        data.setDirty(false); data.dirtyCalls = 0; NBTTagCompound before = state();
        KOMETacticalEditSnapshot s = open(complexScope("B")); s = edit(s, s.getDraft().withMembership(KOMETacticalEditDraft.MembershipAction.UNASSIGN, "B1", null));
        assertEquals(REJECTED, action(KOMETacticalEditRequest.Action.SAVE, s).getStatus()); assertUntouched(before);
    }
    @Test public void snapshotCollectionsAreImmutable() {
        KOMETacticalEditSnapshot s = open(complexScope("A"));
        assertThrows(UnsupportedOperationException.class, () -> s.getDraft().getComplex().getConnections().clear());
        KOMETacticalEditPreflight p = action(KOMETacticalEditRequest.Action.PREFLIGHT, s).getSnapshot().getPreflight();
        assertThrows(UnsupportedOperationException.class, () -> p.getDiagnostics().clear());
    }
    @Test public void serverThreadGuardRejectsOffThreadMutation() throws Exception {
        NBTTagCompound before = state(); AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        Thread worker = new Thread(() -> { try { manager.handle(actor, data, KOMETacticalEditRequest.open(areaScope("FIELD"))); }
            catch (Throwable error) { failure.set(error); } });
        worker.start(); worker.join(); assertTrue(failure.get() instanceof IllegalStateException); assertEquals(0, manager.getSessionCount()); assertUntouched(before);
    }
}
