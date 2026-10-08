package kome.client.tactical;

import java.util.*;
import kome.common.siege.*;
import kome.common.siege.geometry.*;
import kome.common.tactical.edit.*;
import kome.common.tactical.edit.KOMETacticalComplexCatalog.*;
import kome.client.tactical.KOMETacticalComplexDraft.ZoneType;
import org.junit.*;
import static org.junit.Assert.*;

public class KOMETacticalComplexEditorTest {
    private UUID player, token; private List<KOMETacticalEditRequest> requests; private KOMETacticalAreaEditor editor;
    private KOMESiegeComplex original;
    @Before public void setup() {
        player = UUID.randomUUID(); token = UUID.randomUUID(); requests = new ArrayList<KOMETacticalEditRequest>();
        editor = new KOMETacticalAreaEditor(requests::add);
        editor.acceptCatalog(new KOMETacticalAreaCatalog("T100", -1, 10, 0, 0, Collections.emptyList()));
        editor.switchBrowser(true); editor.acceptComplexCatalog(new KOMETacticalComplexCatalog(Kind.COMPLEXES, "T100", null, -1, 10, 0, 0, Collections.emptyList()));
        original = KOMESiegeReadinessFixtures.empty("A", "T100", -1);
        editor.openComplex("A", false); editor.accept(snapshot(original, 0, false, null), KOMETacticalEditSessionManager.Status.OPENED); requests.clear();
    }
    private KOMETacticalEditSnapshot snapshot(KOMESiegeComplex complex, long sequence, boolean closed, KOMETacticalEditPreflight preflight) {
        return new KOMETacticalEditSnapshot(player, token, new KOMETacticalEditScope(KOMETacticalEditScope.Type.SIEGE_COMPLEX, "T100", complex.getComplexId(), complex.getComplexId(), -1),
            1, sequence + 1, 10, complex.getRevision(), sequence, 10, closed, new KOMETacticalEditDraft(complex), preflight);
    }
    private void options(Kind kind, Row... rows) {
        editor.complexPage(kind, 0);
        assertTrue(editor.acceptComplexCatalog(new KOMETacticalComplexCatalog(kind, "T100", "A", -1, 10, 0, rows.length, Arrays.asList(rows))));
        requests.clear();
    }
    private void zone(ZoneType type, String id) { editor.zoneType(type); editor.createZone(id); }
    @Test public void allZoneTypesUseSameOrderedVerticesYBoundsUndoAndClear() {
        for (ZoneType type : ZoneType.values()) {
            zone(type, type + "Local"); editor.addVertex(3, -5); editor.addVertex(-7, 9); editor.addVertex(20, 30);
            editor.setYRangeInclusive(-2, 70);
            assertEquals(Arrays.asList(new KOMEXZPoint(3, -5), new KOMEXZPoint(-7, 9), new KOMEXZPoint(20, 30)), editor.getGeometry().getPolygon().getVertices());
            assertEquals(-2, editor.getGeometry().getMinYInclusive()); assertEquals(71, editor.getGeometry().getMaxYExclusive());
            editor.undo(); assertEquals(2, editor.getGeometry().getPolygon().getVertices().size()); editor.clearVertices();
            editor.select(KOMETacticalAreaEditor.Selection.VERTICES); editor.worldPoint(4, 60, 8);
            assertEquals(Collections.singletonList(new KOMEXZPoint(4, 8)), editor.getConfirmedVertices());
            assertEquals(1,editor.getConfirmedVertexCount()); // incomplete input is visible in the actual draft immediately
            editor.select(KOMETacticalAreaEditor.Selection.UPPER_Y); editor.worldPoint(0, 72, 0); assertEquals(73, editor.getGeometry().getMaxYExclusive());
            editor.select(KOMETacticalAreaEditor.Selection.NONE);
        }
        assertTrue(original.getNormalSegments().isEmpty()); assertTrue(original.getWallZones().isEmpty()); assertTrue(original.getTransitionZones().isEmpty());
        assertTrue(requests.stream().noneMatch(r -> r.getAction() == KOMETacticalEditRequest.Action.SAVE));
    }
    @Test public void localCaseLabelsAndCrossTypeUniquenessArePreserved() {
        zone(ZoneType.NORMAL, " lower "); editor.setLabel("Lower courtyard"); assertEquals("lower", editor.getZoneId());
        editor.createZone("LOWER"); assertEquals(2, editor.getComplexDraft().getNormalSegments().size());
        assertEquals("Lower courtyard", editor.getComplexDraft().findNormalSegment("lower").getLabel());
        editor.zoneType(ZoneType.WALL); assertThrows(IllegalArgumentException.class, () -> editor.createZone("lower"));
        editor.createZone("wall"); editor.toggleWallAccess("lower"); assertEquals(Collections.singleton("lower"), ((KOMEWallZone) editor.getZone()).getAccessibleFromNormalSegmentIds());
        editor.toggleWallAccess("lower"); assertTrue(((KOMEWallZone) editor.getZone()).getAccessibleFromNormalSegmentIds().isEmpty());
    }
    @Test public void everyZoneCanBeReopenedEditedAndDeletedLocally() {
        for (ZoneType type : ZoneType.values()) {
            zone(type, type.name()); editor.setLabel("First"); editor.selectZone(type.name()); editor.setLabel("Second");
            assertEquals("Second", editor.getGeometryLabel()); editor.deleteZone(); assertNull(editor.getZone());
            assertTrue(KOMETacticalComplexDraft.zones(editor.getComplexDraft(), type).isEmpty());
        }
    }
    @Test public void geometryEditsAndZoneDeletionNeverAlterExistingConnections() {
        KOMESiegeComplex c = KOMESiegeReadinessFixtures.minimal("A", "T100", -1, null);
        editor.accept(snapshot(c, 1, false, null), KOMETacticalEditSessionManager.Status.UPDATED);
        String id = c.getNormalSegments().get(0).getId(); editor.selectZone(id); editor.setLabel("Edited"); editor.deleteZone();
        assertEquals(c.getConnections(), editor.getComplexDraft().getConnections());
    }
    @Test public void malformedDuplicateLocalIdsCannotBeSilentlyCollapsedByEditingOrDeletion() {
        KOMENormalSegment zone = new KOMENormalSegment("dup", "", KOMESiegeReadinessFixtures.prism(0, 0, 10, 10));
        KOMESiegeComplex c = new KOMESiegeComplex("A", "T100", -1, 0, Arrays.asList(zone, zone), Collections.emptyList(), Collections.emptyList(), null, Collections.emptyList());
        editor.accept(snapshot(c, 1, false, null), KOMETacticalEditSessionManager.Status.UPDATED); editor.selectZone("dup");
        assertThrows(IllegalArgumentException.class, () -> editor.setLabel("Changed")); assertThrows(IllegalArgumentException.class, () -> editor.deleteZone());
        assertSame(c, editor.getComplexDraft()); assertEquals(2, editor.getComplexDraft().getNormalSegments().size());
    }
    @Test public void overlaysAreTypedSelectedAndRestrictedToCurrentPlayerDimensionAndComplex() {
        zone(ZoneType.NORMAL, "n"); zone(ZoneType.WALL, "w"); zone(ZoneType.TRANSITION, "t");
        List<KOMETacticalAreaEditor.Overlay> overlays = editor.overlays(player, -1);
        assertEquals(3, overlays.size()); assertEquals(1, overlays.stream().filter(o -> o.selected).count());
        assertEquals(ZoneType.TRANSITION, overlays.get(2).type); assertTrue(overlays.get(2).selected);
        assertThrows(UnsupportedOperationException.class, () -> overlays.clear());
        assertTrue(editor.overlays(UUID.randomUUID(), -1).isEmpty()); assertTrue(editor.overlays(player, 0).isEmpty());
        assertNull(editor.overlay(player, -1)); // complex geometry never becomes a tile deployment polygon
    }
    @Test public void cancelTimeoutAndResetClearComplexOverlayAndSelection() {
        zone(ZoneType.NORMAL, "n"); editor.select(KOMETacticalAreaEditor.Selection.VERTICES); editor.cancel();
        assertTrue(editor.overlays(player, -1).isEmpty()); assertFalse(editor.consumesClicks(player, -1));
        token = UUID.randomUUID(); editor.openComplex("A", false); editor.accept(snapshot(original, 0, false, null), KOMETacticalEditSessionManager.Status.OPENED);
        editor.accept(snapshot(original, 1, true, null), KOMETacticalEditSessionManager.Status.EXPIRED); assertFalse(editor.isEditing());
        editor.reset(); assertNull(editor.getComplexCatalog()); assertNull(editor.getOptions()); assertFalse(editor.isComplexBrowser());
    }
    @Test public void preferredSelectionUsesServerChoicesAndCanBeClearedWithoutCopyingGeometry() {
        options(Kind.PREFERRED_AREAS, new Row("FIELD", "Field", "", null, 7, 0));
        assertThrows(IllegalArgumentException.class, () -> editor.setPreferredArea("FOREIGN"));
        editor.setPreferredArea("FIELD"); assertEquals("FIELD", editor.getComplexDraft().getPreferredForceDeploymentAreaId().get());
        assertTrue(editor.getComplexDraft().getNormalSegments().isEmpty()); assertNull(editor.getDraft());
        editor.setPreferredArea(null); assertFalse(editor.getComplexDraft().getPreferredForceDeploymentAreaId().isPresent());
    }
    @Test public void membershipUsesSeparateIntentExpectedOldAndAcknowledgedSave() {
        Row build = new Row("B1", "Build", "Assigned to B", "B", 0, 0); options(Kind.BUILDS, build);
        editor.membership(build, KOMETacticalEditDraft.MembershipAction.REASSIGN);
        KOMETacticalEditDraft intent = KOMETacticalEditWire.decodeDraft(requests.get(0).getPayload());
        assertEquals("B", intent.getExpectedOldComplexId()); assertEquals(KOMETacticalEditDraft.MembershipAction.REASSIGN, intent.getMembershipAction());
        editor.accept(new KOMETacticalEditSnapshot(player, token, snapshot(original, 0, false, null).getScope(), 1, 2, 10,
            original.getRevision(), 1, 10, false, intent, null), KOMETacticalEditSessionManager.Status.UPDATED);
        assertEquals(KOMETacticalEditRequest.Action.SAVE, requests.get(1).getAction()); assertEquals(1, requests.get(1).getExpectedSequence());
    }
    @Test public void membershipCannotSmuggleUnsavedDefinitionChanges() {
        Row build = new Row("B1", "Build", "Unassigned", null, 0, 0); options(Kind.BUILDS, build);
        zone(ZoneType.NORMAL, "n"); assertThrows(IllegalArgumentException.class, () -> editor.membership(build, KOMETacticalEditDraft.MembershipAction.ASSIGN));
        assertTrue(requests.isEmpty());
    }
    @Test public void closedSaveRetainsMembershipImpactMessagesAndRefreshesComplexList() {
        KOMETacticalEditPreflight p = new KOMETacticalEditPreflight(true, true, KOMETacticalEditPreflight.State.INCOMPLETE, Collections.singletonList("AFFECTED B/ENTRY B1/G1"));
        editor.accept(snapshot(original, 1, true, p), KOMETacticalEditSessionManager.Status.SAVED);
        assertTrue(editor.getMessage().contains("AFFECTED B/ENTRY")); assertFalse(editor.isEditing());
        assertEquals(KOMETacticalEditRequest.Action.BROWSE_COMPLEXES, requests.get(requests.size() - 1).getAction());
    }
    @Test public void notReadyPreflightIsSeparateFromSaveAdmissionAndStaleRetainsLocalZoneDraft() {
        zone(ZoneType.TRANSITION, "t"); KOMESiegeComplex local = editor.getComplexDraft();
        KOMETacticalEditPreflight p = new KOMETacticalEditPreflight(true, false, KOMETacticalEditPreflight.State.INVALID, Collections.singletonList("TRANSITION_UNUSED"));
        editor.accept(snapshot(local, 1, false, p), KOMETacticalEditSessionManager.Status.VALIDATED);
        assertTrue(editor.getMessage().contains("NOT READY")); assertTrue(editor.getMessage().contains("can be saved"));
        editor.accept(snapshot(original, 1, false, null), KOMETacticalEditSessionManager.Status.STALE_STORE);
        assertSame(local, editor.getComplexDraft()); assertTrue(editor.getMessage().contains("Cancel, refresh and reopen"));
    }
    @Test public void wrongScopeOldRevisionAndUnexpectedCatalogueResponsesAreRejected() {
        editor.complexPage(Kind.BUILDS, 0);
        assertFalse(editor.acceptComplexCatalog(new KOMETacticalComplexCatalog(Kind.BUILDS, "T100", "B", -1, 10, 0, 0, Collections.emptyList())));
        assertFalse(editor.acceptComplexCatalog(new KOMETacticalComplexCatalog(Kind.BUILDS, "T101", "A", -1, 10, 0, 0, Collections.emptyList())));
        assertFalse(editor.acceptComplexCatalog(new KOMETacticalComplexCatalog(Kind.BUILDS, "T100", "A", -1, 9, 0, 0, Collections.emptyList())));
        assertTrue(editor.acceptComplexCatalog(new KOMETacticalComplexCatalog(Kind.BUILDS, "T100", "A", -1, 12, 0, 0, Collections.emptyList())));
        assertFalse(editor.acceptComplexCatalog(new KOMETacticalComplexCatalog(Kind.BUILDS, "T100", "A", -1, 13, 0, 0, Collections.emptyList())));
    }
    @Test public void newComplexStartsDetachedAndCannotAttemptMembershipUntilSaved() {
        editor.cancel(); token = UUID.randomUUID(); editor.openComplex("A", true); editor.accept(snapshot(original, 0, false, null), KOMETacticalEditSessionManager.Status.OPENED);
        assertTrue(editor.isCreating()); assertTrue(editor.isComplexEditing());
        assertThrows(IllegalArgumentException.class, () -> editor.complexPage(Kind.BUILDS, 0)); assertThrows(IllegalArgumentException.class, () -> editor.delete());
    }
}
