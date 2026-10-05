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
        editor.clearVertices(); editor.undo(); assertTrue(editor.getDraft().getPrism().getPolygon().getVertices().isEmpty());
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
    }
    @Test public void onlyExplicitWorldModeConsumesClicksAndClosingStopsSuppression() {
        assertFalse(editor.consumesClicks(player, -1)); editor.select(KOMETacticalAreaEditor.Selection.VERTICES);
        assertTrue(editor.consumesClicks(player, -1)); assertFalse(editor.consumesClicks(UUID.randomUUID(), -1));
        assertFalse(editor.consumesClicks(player, 0)); editor.cancel();
        assertFalse(editor.consumesClicks(player, -1)); assertNull(editor.overlay(player, -1));
    }
    @Test public void timeoutAndLifecycleResetClearOverlayInputAndDraft() {
        editor.select(KOMETacticalAreaEditor.Selection.VERTICES);
        editor.accept(snapshot(initial, 0, 2, true), KOMETacticalEditSessionManager.Status.EXPIRED);
        assertNull(editor.overlay(player, -1)); assertFalse(editor.consumesClicks(player, -1));
        assertTrue(editor.getMessage().contains("expired"));
        editor.reset(); assertNull(editor.getCatalog()); assertNull(editor.getDraft());
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
}
