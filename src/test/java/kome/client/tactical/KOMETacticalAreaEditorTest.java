package kome.client.tactical;

import java.util.*;
import kome.common.siege.geometry.*;
import kome.common.tactical.KOMEForceDeploymentArea;
import kome.common.tactical.edit.*;
import org.junit.*;
import static org.junit.Assert.*;

public class KOMETacticalAreaEditorTest {
    private final UUID player = UUID.randomUUID(), token = UUID.randomUUID();
    private List<KOMETacticalEditRequest> requests;
    private KOMETacticalAreaEditor editor;
    private KOMEForceDeploymentArea initial;
    @Before public void setup() {
        requests = new ArrayList<KOMETacticalEditRequest>(); editor = new KOMETacticalAreaEditor(requests::add);
        editor.acceptCatalog(new KOMETacticalAreaCatalog("T100", -1, 10, 0, 0, Collections.emptyList()));
        editor.open(" field ", true);
        initial = new KOMEForceDeploymentArea("FIELD", "T100", -1, "", new KOMEPolygonPrism(new KOMEPolygon(Collections.emptyList()), 0, 1), 0);
        editor.accept(snapshot(initial, 0, 1, false), KOMETacticalEditSessionManager.Status.OPENED); requests.clear();
    }
    private KOMETacticalEditSnapshot snapshot(KOMEForceDeploymentArea area, long sequence, long publication, boolean closed) {
        return new KOMETacticalEditSnapshot(player, token,
            new KOMETacticalEditScope(KOMETacticalEditScope.Type.TILE_FORCE_DEPLOYMENT_AREA, "T100", null, "FIELD", -1),
            1, publication, 10, 0, sequence, 10, closed, new KOMETacticalEditDraft(area), null);
    }
    @Test public void localVerticesPreserveOrderAndDoNotChangeOriginalOrIssueSave() {
        editor.addVertex(-4, 7); editor.addVertex(10, -3); editor.addVertex(5, 20);
        assertEquals(Arrays.asList(new KOMEXZPoint(-4, 7), new KOMEXZPoint(10, -3), new KOMEXZPoint(5, 20)),
            editor.getDraft().getPrism().getPolygon().getVertices());
        assertTrue(initial.getPrism().getPolygon().getVertices().isEmpty()); assertTrue(requests.isEmpty());
    }
    @Test public void undoAndClearRestartPolygon() {
        editor.addVertex(1, 2); editor.addVertex(3, 4); editor.undo();
        assertEquals(Collections.singletonList(new KOMEXZPoint(1, 2)), editor.getDraft().getPrism().getPolygon().getVertices());
        editor.clearVertices(); editor.undo(); assertEquals(Collections.singletonList(new KOMEXZPoint(1, 2)), editor.getConfirmedVertices());
    }
    @Test public void inclusiveTopBlockMapsToExclusivePrismBoundExactly() {
        editor.setYRangeInclusive(-5, 12);
        assertEquals(-5, editor.getDraft().getPrism().getMinYInclusive()); assertEquals(13, editor.getDraft().getPrism().getMaxYExclusive());
        editor.select(KOMETacticalAreaEditor.Selection.UPPER_Y); editor.worldPoint(0, 70, 0);
        assertEquals(71, editor.getDraft().getPrism().getMaxYExclusive());
        editor.select(KOMETacticalAreaEditor.Selection.LOWER_Y); editor.worldPoint(0, 60, 0);
        assertEquals(60, editor.getDraft().getPrism().getMinYInclusive());
        assertThrows(IllegalArgumentException.class, () -> editor.setYRangeInclusive(0, Integer.MAX_VALUE));
    }
    @Test public void labelCannotChangeCanonicalIdentity() {
        editor.setLabel("New display label"); assertEquals("FIELD", editor.getDraft().getAreaId()); assertEquals("New display label", editor.getDraft().getLabel());
    }
    @Test public void overlayIsCurrentGeometryAndRequiresMatchingPlayerDimension() {
        editor.addVertex(20, 30); editor.setYRangeInclusive(4, 8);
        assertSame(editor.getDraft(), editor.overlay(player, -1)); assertNull(editor.overlay(UUID.randomUUID(), -1)); assertNull(editor.overlay(player, 0));
        assertEquals(new KOMEXZPoint(20, 30), editor.overlay(player, -1).getPrism().getPolygon().getVertices().get(0));
        editor.setLabel("Exterior staging");
        KOMETacticalAreaEditor.Overlay exterior = editor.overlays(player, -1).get(0);
        assertEquals("FIELD", exterior.id); assertEquals("Exterior staging", exterior.label);
        assertTrue(exterior.selected); assertTrue(exterior.unsaved);
    }
    @Test public void onlyExplicitWorldModeConsumesClicksAndClosingStopsSuppression() {
        assertFalse(editor.consumesClicks(player, -1)); editor.select(KOMETacticalAreaEditor.Selection.VERTICES);
        assertTrue(editor.consumesClicks(player, -1)); assertFalse(editor.consumesClicks(UUID.randomUUID(), -1));
        assertFalse(editor.consumesClicks(player, 0)); editor.cancel();
        assertFalse(editor.consumesClicks(player, -1)); assertNull(editor.overlay(player, -1));
    }
    @Test public void timeoutAndLifecycleResetClearAuthorityButRetainExpiredDraft() {
        editor.select(KOMETacticalAreaEditor.Selection.VERTICES);
        KOMEForceDeploymentArea draft = editor.getDraft();
        editor.accept(snapshot(initial, 0, 2, true), KOMETacticalEditSessionManager.Status.EXPIRED);
        assertNull(editor.overlay(player, -1)); assertFalse(editor.consumesClicks(player, -1));
        assertTrue(editor.getMessage().contains("expired"));
        editor.reset(); assertNull(editor.getCatalog()); assertSame(draft, editor.getDraft());
        assertTrue(editor.hasRetainedDraft()); assertFalse(editor.canRecoverRetainedDraft());
        assertThrows(IllegalArgumentException.class, () -> editor.recoverRetainedDraft(player, -1));
        editor.discardRetainedDraft(); assertNull(editor.getDraft());
    }
    @Test public void unexpectedExpirationKeepsExactUnsentAreaGeometryWithoutAuthority() {
        editor.addVertex(7, 9); editor.setLabel("Unsent label"); editor.setYRangeInclusive(61, 74);
        KOMEForceDeploymentArea unsent = editor.getDraft();
        editor.accept(snapshot(initial, 0, 2, true), KOMETacticalEditSessionManager.Status.EXPIRED);
        assertSame(unsent, editor.getDraft());
        assertTrue(editor.hasRetainedDraft()); assertEquals(10, editor.getRetainedBaseRevision());
        assertEquals(0, editor.getRetainedObjectRevision());
        assertTrue(editor.retainedInspectionLines().stream().anyMatch(line -> line.contains("Unsent label") && line.contains("61..74")));
        assertTrue(editor.retainedInspectionLines().stream().anyMatch(line -> line.contains("X 7, Z 9")));
        assertFalse(editor.isEditing()); assertNull(editor.overlay(player, -1));
        assertThrows(IllegalArgumentException.class, () -> editor.save());
        assertThrows(IllegalArgumentException.class, () -> editor.validate());
        editor.accept(snapshot(initial, 0, 1, false), KOMETacticalEditSessionManager.Status.REFRESHED);
        assertSame(unsent, editor.getDraft()); assertTrue(editor.hasRetainedDraft());
        editor.recoverRetainedDraft(player, -1);
        assertEquals(KOMETacticalEditRequest.Action.CREATE, requests.get(0).getAction());
        assertThrows(IllegalArgumentException.class, () -> editor.save());
        UUID freshToken = UUID.randomUUID();
        editor.accept(new KOMETacticalEditSnapshot(player, freshToken, editor.getRetainedScope(), 2, 3, 10, 0, 0, 10,
            false, new KOMETacticalEditDraft(initial), null), KOMETacticalEditSessionManager.Status.OPENED);
        assertFalse(editor.hasRetainedDraft()); assertTrue(editor.isEditing()); assertSame(unsent, editor.getDraft());
        assertTrue(requests.stream().noneMatch(r -> r.getAction() == KOMETacticalEditRequest.Action.UPDATE || r.getAction() == KOMETacticalEditRequest.Action.SAVE));
        editor.save(); assertEquals(freshToken, requests.get(1).getToken());
    }
    @Test public void changedRevisionBlocksRecoveryAndIntentionalDiscardRemovesRetainedDraft() {
        editor.addVertex(2, 4); KOMEForceDeploymentArea unsent = editor.getDraft();
        editor.accept(snapshot(initial, 0, 2, true), KOMETacticalEditSessionManager.Status.EXPIRED);
        editor.recoverRetainedDraft(player, -1);
        UUID freshToken = UUID.randomUUID(); KOMETacticalEditScope scope = editor.getRetainedScope();
        KOMETacticalEditSnapshot changed = new KOMETacticalEditSnapshot(player, freshToken, scope, 2, 3, 11, 0, 0, 11,
            false, new KOMETacticalEditDraft(initial), null);
        editor.accept(changed, KOMETacticalEditSessionManager.Status.OPENED);
        assertSame(unsent, editor.getDraft()); assertTrue(editor.hasRetainedDraft()); assertFalse(editor.isEditing());
        assertEquals(KOMETacticalEditRequest.Action.CANCEL, requests.get(1).getAction());
        editor.accept(new KOMETacticalEditSnapshot(player, freshToken, scope, 2, 4, 11, 0, 0, 11,
            true, new KOMETacticalEditDraft(initial), null), KOMETacticalEditSessionManager.Status.CANCELLED);
        assertTrue(editor.getMessage().contains("changed"));
        editor.discardRetainedDraft(); assertNull(editor.getDraft()); assertFalse(editor.hasRetainedDraft());
    }
    @Test public void deniedSessionWithExistingTokenClosesBeforeFreshAuthorizedRecovery() {
        editor.setLabel("Keep this draft"); KOMEForceDeploymentArea unsent = editor.getDraft();
        editor.accept(null, KOMETacticalEditSessionManager.Status.DENIED);
        requests.clear(); editor.recoverRetainedDraft(player, -1);
        assertEquals(KOMETacticalEditRequest.Action.CREATE, requests.get(0).getAction());
        editor.accept(snapshot(initial, 0, 2, false), KOMETacticalEditSessionManager.Status.OPENED);
        assertSame(unsent, editor.getDraft()); assertFalse(editor.isEditing());
        assertEquals(KOMETacticalEditRequest.Action.CANCEL, requests.get(1).getAction());
        assertEquals(token, requests.get(1).getToken());
        editor.accept(snapshot(initial, 0, 3, true), KOMETacticalEditSessionManager.Status.CANCELLED);
        assertEquals(KOMETacticalEditRequest.Action.CREATE, requests.get(2).getAction());
        UUID fresh = UUID.randomUUID();
        editor.accept(new KOMETacticalEditSnapshot(player, fresh, snapshot(initial, 0, 1, false).getScope(),
            2, 4, 10, 0, 0, 10, false, new KOMETacticalEditDraft(initial), null),
            KOMETacticalEditSessionManager.Status.OPENED);
        assertTrue(editor.isEditing()); assertSame(unsent, editor.getDraft());
        editor.save(); assertEquals(fresh, requests.get(3).getToken());
    }
    @Test public void deniedCleanupAndLateRepliesLeaveInspectorUsable() {
        editor.addVertex(4, 7); KOMEForceDeploymentArea unsent = editor.getDraft();
        editor.accept(null, KOMETacticalEditSessionManager.Status.DENIED); requests.clear();
        editor.recoverRetainedDraft(player, -1);
        editor.accept(snapshot(initial, 0, 2, false), KOMETacticalEditSessionManager.Status.OPENED);
        assertEquals(KOMETacticalEditRequest.Action.CANCEL, requests.get(1).getAction());
        editor.accept(null, KOMETacticalEditSessionManager.Status.DENIED);
        assertFalse(editor.isBusy()); assertFalse(editor.isRecoveringDraft());
        assertSame(unsent, editor.getDraft()); assertTrue(editor.hasRetainedDraft());
        editor.accept(snapshot(initial, 0, 3, false), KOMETacticalEditSessionManager.Status.REFRESHED);
        assertFalse(editor.isEditing()); assertSame(unsent, editor.getDraft());
        editor.recoverRetainedDraft(player, -1);
        assertEquals(KOMETacticalEditRequest.Action.CREATE, requests.get(2).getAction());
        editor.accept(null, KOMETacticalEditSessionManager.Status.DENIED);
        assertFalse(editor.isRecoveringDraft()); editor.discardRetainedDraft(); assertNull(editor.getDraft());
    }
    @Test public void reorderedRepliesCannotAuthorizeOldOrUnrelatedRecoverySessions() {
        editor.setLabel("Retain safely"); KOMEForceDeploymentArea unsent = editor.getDraft();
        editor.accept(null, KOMETacticalEditSessionManager.Status.DENIED); requests.clear();
        editor.recoverRetainedDraft(player, -1);
        editor.accept(snapshot(initial, 0, 2, false), KOMETacticalEditSessionManager.Status.OPENED);
        KOMETacticalEditScope scope = editor.getRetainedScope();
        UUID unrelatedToken = UUID.randomUUID();
        KOMEForceDeploymentArea unrelated = new KOMEForceDeploymentArea("OTHER", "T100", -1, "",
            initial.getPrism(), 0);
        editor.accept(new KOMETacticalEditSnapshot(player, unrelatedToken,
            new KOMETacticalEditScope(KOMETacticalEditScope.Type.TILE_FORCE_DEPLOYMENT_AREA, "T100", null, "OTHER", -1),
            2, 3, 10, 0, 0, 10, false, new KOMETacticalEditDraft(unrelated), null), KOMETacticalEditSessionManager.Status.OPENED);
        editor.accept(snapshot(initial, 0, 4, false), KOMETacticalEditSessionManager.Status.REFRESHED);
        assertFalse(editor.isEditing()); assertSame(unsent, editor.getDraft()); assertEquals(2, requests.size());
        editor.accept(snapshot(initial, 0, 5, true), KOMETacticalEditSessionManager.Status.CANCELLED);
        assertEquals(3, requests.size()); assertEquals(KOMETacticalEditRequest.Action.CREATE, requests.get(2).getAction());
        editor.accept(snapshot(initial, 0, 6, true), KOMETacticalEditSessionManager.Status.CANCELLED);
        assertTrue(editor.isRecoveringDraft()); assertFalse(editor.isEditing());
        UUID fresh = UUID.randomUUID();
        editor.accept(new KOMETacticalEditSnapshot(player, fresh, scope, 2, 7, 10, 0, 0, 10,
            false, new KOMETacticalEditDraft(initial), null), KOMETacticalEditSessionManager.Status.OPENED);
        assertTrue(editor.isEditing()); assertSame(unsent, editor.getDraft());
        editor.accept(snapshot(initial, 0, 8, false), KOMETacticalEditSessionManager.Status.OPENED);
        assertSame(unsent, editor.getDraft()); assertTrue(editor.isEditing());
    }
    @Test public void missingRecoveryCloseReplyTimesOutWithoutLosingDraft() {
        editor.addVertex(1, 9); KOMEForceDeploymentArea unsent = editor.getDraft();
        editor.accept(null, KOMETacticalEditSessionManager.Status.DENIED); requests.clear();
        editor.recoverRetainedDraft(player, -1);
        editor.accept(snapshot(initial, 0, 2, false), KOMETacticalEditSessionManager.Status.OPENED);
        assertTrue(editor.isRecoveringDraft());
        for (int i = 0; i < KOMETacticalAreaEditor.RECOVERY_WAIT_TICKS; i++) editor.tickSession(player, -1);
        assertFalse(editor.isBusy()); assertFalse(editor.isRecoveringDraft());
        assertSame(unsent, editor.getDraft()); assertTrue(editor.hasRetainedDraft());
        assertTrue(editor.canRecoverRetainedDraft()); assertTrue(editor.getMessage().contains("timed out"));
        editor.accept(snapshot(initial, 0, 3, true), KOMETacticalEditSessionManager.Status.CANCELLED);
        assertFalse(editor.isEditing()); assertSame(unsent, editor.getDraft());
        editor.discardRetainedDraft(); assertNull(editor.getDraft());
    }
    @Test public void recoveryComparesOriginalSavedSourceAfterAnUnpublishedServerUpdate() {
        editor.setLabel("Locally authored"); editor.addVertex(3, 5);
        KOMEForceDeploymentArea local = editor.getDraft();
        editor.validate();
        editor.accept(snapshot(local, 1, 2, false), KOMETacticalEditSessionManager.Status.UPDATED);
        editor.accept(snapshot(local, 1, 3, true), KOMETacticalEditSessionManager.Status.EXPIRED);
        assertSame(local, editor.getDraft()); assertTrue(editor.hasRetainedDraft());
        editor.recoverRetainedDraft(player, -1);
        UUID freshToken = UUID.randomUUID();
        editor.accept(new KOMETacticalEditSnapshot(player, freshToken, editor.getRetainedScope(), 2, 4, 10, 0, 0, 10,
            false, new KOMETacticalEditDraft(initial), null), KOMETacticalEditSessionManager.Status.OPENED);
        assertFalse(editor.hasRetainedDraft()); assertSame(local, editor.getDraft()); assertTrue(editor.isEditing());
        assertTrue(requests.stream().noneMatch(r -> r.getAction() == KOMETacticalEditRequest.Action.SAVE));
    }
    @Test public void oneKeepalivePerMinutePreservesLocalDraftAndStopsAfterCancel() {
        editor.setLabel("Unsent"); KOMEForceDeploymentArea local = editor.getDraft();
        for (int i = 0; i < KOMETacticalAreaEditor.KEEPALIVE_INTERVAL_TICKS - 1; i++) editor.tickSession(player, -1);
        assertTrue(requests.isEmpty()); editor.tickSession(player, -1);
        assertEquals(1, requests.size()); assertEquals(KOMETacticalEditRequest.Action.REFRESH, requests.get(0).getAction());
        assertTrue(editor.isKeepalivePending());
        for (int i = 0; i < 2400; i++) editor.tickSession(player, -1);
        assertEquals(1, requests.size());
        editor.accept(snapshot(initial, 0, 2, false), KOMETacticalEditSessionManager.Status.REFRESHED);
        assertSame(local, editor.getDraft()); assertFalse(editor.isKeepalivePending());
        for (int i = 0; i < KOMETacticalAreaEditor.KEEPALIVE_INTERVAL_TICKS; i++) editor.tickSession(player, -1);
        assertEquals(2, requests.size());
        editor.cancel();
        for (int i = 0; i < 2400; i++) editor.tickSession(player, -1);
        assertEquals(3, requests.size()); assertEquals(KOMETacticalEditRequest.Action.CANCEL, requests.get(2).getAction());
    }
    @Test public void pendingSaveDoesNotGenerateKeepalives() {
        editor.setLabel("Pending"); editor.save();
        for (int i = 0; i < 6000; i++) editor.tickSession(player, -1);
        assertEquals(1, requests.size()); assertEquals(KOMETacticalEditRequest.Action.UPDATE, requests.get(0).getAction());
    }
    @Test public void failedRefreshIsThrottledAndExpiryStopsKeepalive() {
        List<KOMETacticalEditRequest> attempts = new ArrayList<KOMETacticalEditRequest>();
        KOMETacticalAreaEditor flaky = new KOMETacticalAreaEditor(request -> { attempts.add(request); throw new IllegalStateException("offline"); });
        flaky.acceptCatalog(new KOMETacticalAreaCatalog("T100", -1, 10, 0, 0, Collections.emptyList()));
        flaky.accept(snapshot(initial, 0, 1, false), KOMETacticalEditSessionManager.Status.OPENED);
        for (int i = 0; i < 2400; i++) flaky.tickSession(player, -1);
        assertEquals(2, attempts.size());
        flaky.accept(snapshot(initial, 0, 2, true), KOMETacticalEditSessionManager.Status.EXPIRED);
        for (int i = 0; i < 6000; i++) flaky.tickSession(player, -1);
        assertEquals(2, attempts.size()); assertTrue(flaky.hasRetainedDraft());
    }
    @Test public void authorityDenialRetainsLocalDraftAndStopsRefreshes() {
        editor.addVertex(8, 12); KOMEForceDeploymentArea local = editor.getDraft();
        editor.accept(null, KOMETacticalEditSessionManager.Status.DENIED);
        assertSame(local, editor.getDraft()); assertTrue(editor.hasRetainedDraft()); assertFalse(editor.isEditing());
        for (int i = 0; i < 6000; i++) editor.tickSession(player, -1);
        assertTrue(requests.isEmpty());
    }
    @Test public void validateAndSaveWaitForAcknowledgedDraftSequence() {
        editor.setLabel("Local"); editor.save();
        assertEquals(KOMETacticalEditRequest.Action.UPDATE, requests.get(0).getAction()); assertTrue(editor.isBusy());
        KOMEForceDeploymentArea local = editor.getDraft();
        editor.accept(snapshot(initial, 0, 2, false), KOMETacticalEditSessionManager.Status.REFRESHED);
        assertTrue(editor.isBusy()); assertSame(local, editor.getDraft()); assertEquals(1, requests.size());
        editor.accept(snapshot(local, 1, 3, false), KOMETacticalEditSessionManager.Status.UPDATED);
        assertEquals(2, requests.size()); assertEquals(KOMETacticalEditRequest.Action.SAVE, requests.get(1).getAction());
        assertEquals(1, requests.get(1).getExpectedSequence());
    }
    @Test public void cancelDoesNotTurnLateUpdateIntoSaveAndRetriesStaleCancellation() {
        editor.save(); editor.cancel(); int before = requests.size();
        editor.accept(snapshot(initial, 1, 2, false), KOMETacticalEditSessionManager.Status.UPDATED);
        assertEquals(before, requests.size()); assertNull(editor.getDraft());
        editor.accept(snapshot(initial, 1, 3, false), KOMETacticalEditSessionManager.Status.STALE_SEQUENCE);
        assertEquals(KOMETacticalEditRequest.Action.CANCEL, requests.get(requests.size() - 1).getAction());
        assertEquals(1, requests.get(requests.size() - 1).getExpectedSequence());
    }
    @Test public void staleSaveKeepsLocalDraftAndShowsRefreshInstruction() {
        editor.setLabel("Keep me"); KOMEForceDeploymentArea local = editor.getDraft();
        editor.accept(snapshot(initial, 0, 2, false), KOMETacticalEditSessionManager.Status.STALE_STORE);
        assertSame(local, editor.getDraft()); assertTrue(editor.getMessage().contains("Cancel, refresh and reopen"));
    }
    @Test public void refreshedServerDraftCannotOverwriteWorldSelectionChanges() {
        editor.addVertex(9, 10);
        editor.accept(snapshot(initial, 0, 2, false), KOMETacticalEditSessionManager.Status.REFRESHED);
        assertEquals(1, editor.getDraft().getPrism().getPolygon().getVertices().size());
    }
    @Test public void staleRefreshCannotRollBackNewerAcceptedSequenceOrLocalGeometry() {
        editor.setLabel("Latest local"); KOMEForceDeploymentArea local = editor.getDraft();
        editor.accept(snapshot(local, 1, 3, false), KOMETacticalEditSessionManager.Status.UPDATED);
        editor.accept(snapshot(initial, 0, 2, false), KOMETacticalEditSessionManager.Status.REFRESHED);
        assertSame(local, editor.getDraft());
        editor.save(); assertEquals(1, requests.get(requests.size() - 1).getExpectedSequence());
    }
    @Test public void duplicateAndPermissionFailuresAreReadableAndDoNotOpenAnEditor() {
        editor.cancel(); editor.open("FIELD", true); editor.accept(null, KOMETacticalEditSessionManager.Status.DUPLICATE_ID);
        assertFalse(editor.isEditing()); assertFalse(editor.isBusy()); assertTrue(editor.getMessage().contains("already exists"));
        editor.open("FIELD", true); editor.accept(null, KOMETacticalEditSessionManager.Status.DENIED);
        assertFalse(editor.isEditing()); assertTrue(editor.getMessage().contains("Creative or operator level 2"));
    }
    @Test public void newDraftCannotDeleteAndRawLocalGeometryRemainsRepresentableForDiagnostics() {
        assertThrows(IllegalArgumentException.class, () -> editor.delete());
        editor.setYRangeInclusive(20, 10); assertFalse(editor.getDraft().getPrism().hasValidYRange());
        editor.validate(); assertEquals(KOMETacticalEditRequest.Action.UPDATE, requests.get(0).getAction());
    }
    @Test public void cleanAreaBackClosesSessionBeforeRefreshingParentList() {
        editor = new KOMETacticalAreaEditor(requests::add);
        editor.acceptCatalog(new KOMETacticalAreaCatalog("T100", -1, 10, 0, 0, Collections.emptyList()));
        editor.open("FIELD", false); editor.accept(snapshot(initial, 0, 1, false), KOMETacticalEditSessionManager.Status.OPENED);
        requests.clear(); editor.backToAreaList();
        assertTrue(editor.isBusy()); assertEquals(KOMETacticalEditRequest.Action.CANCEL, requests.get(0).getAction());
        editor.accept(snapshot(initial, 0, 2, true), KOMETacticalEditSessionManager.Status.CANCELLED);
        assertNull(editor.getDraft()); assertEquals(KOMETacticalEditRequest.Action.BROWSE, requests.get(1).getAction());
        assertTrue(requests.stream().noneMatch(r -> r.getAction() == KOMETacticalEditRequest.Action.DELETE));
    }
    @Test public void noChangeSaveAlsoReopensAndWrongScopeCannotReplacePendingGeometry() {
        editor.save();
        KOMEForceDeploymentArea local = editor.getDraft();
        editor.accept(snapshot(local, 1, 2, false), KOMETacticalEditSessionManager.Status.UPDATED);
        editor.accept(snapshot(local, 1, 3, true), KOMETacticalEditSessionManager.Status.NO_CHANGE);
        assertTrue(editor.isReopeningAfterSave()); assertFalse(editor.isEditing());
        assertEquals(KOMETacticalEditRequest.Action.OPEN, requests.get(requests.size() - 1).getAction());
        int pendingRenewalRequests = requests.size();
        for (int i = 0; i < 6000; i++) editor.tickSession(player, -1);
        assertEquals(pendingRenewalRequests, requests.size());
        KOMETacticalEditScope other = new KOMETacticalEditScope(KOMETacticalEditScope.Type.TILE_FORCE_DEPLOYMENT_AREA,
            "T100", null, "OTHER", -1);
        KOMEForceDeploymentArea otherArea = new KOMEForceDeploymentArea("OTHER", "T100", -1, "Other", local.getPrism(), 0);
        editor.accept(new KOMETacticalEditSnapshot(player, UUID.randomUUID(), other, 2, 4, 10, 0, 0, 10, false,
            new KOMETacticalEditDraft(otherArea), null), KOMETacticalEditSessionManager.Status.OPENED);
        assertFalse(editor.isEditing()); assertFalse(editor.isReopeningAfterSave());
        assertTrue(editor.getMessage().contains("could not reopen this geometry"));
    }
    @Test public void freshAreaDefaultsToFixedHeightAndNeverResetsAnUnsavedDraft() {
        editor = new KOMETacticalAreaEditor(requests::add);
        editor.acceptCatalog(new KOMETacticalAreaCatalog("T100", -1, 10, 0, 0, Collections.emptyList()));
        editor.open("FIELD", true); editor.accept(snapshot(initial, 0, 1, false), KOMETacticalEditSessionManager.Status.OPENED);
        assertEquals(64, editor.getGeometry().getMinYInclusive()); assertEquals(70, editor.getGeometry().getMaxYInclusive());
        assertEquals(71, editor.getGeometry().getMaxYExclusive());
        editor.setYRangeInclusive(80, 83);
        editor.accept(snapshot(initial, 0, 2, false), KOMETacticalEditSessionManager.Status.OPENED);
        assertEquals(80, editor.getGeometry().getMinYInclusive()); assertEquals(84, editor.getGeometry().getMaxYExclusive());
        assertEquals(0, initial.getPrism().getMinYInclusive());
    }
    @Test public void existingAreaGeometryIsPreserved() {
        editor = new KOMETacticalAreaEditor(requests::add);
        editor.acceptCatalog(new KOMETacticalAreaCatalog("T100", -1, 10, 0, 0, Collections.emptyList()));
        editor.open("FIELD", false); editor.accept(snapshot(initial, 0, 1, false), KOMETacticalEditSessionManager.Status.OPENED);
        assertEquals(0, editor.getGeometry().getMinYInclusive()); assertEquals(1, editor.getGeometry().getMaxYExclusive());
    }
}
