package kome.client.tactical;

import java.util.*;
import kome.common.siege.*;
import kome.common.siege.geometry.*;
import kome.common.tactical.edit.*;
import org.junit.*;
import static org.junit.Assert.*;
import static kome.common.siege.KOMESiegeReadinessFixtures.*;

public class KOMETacticalConnectionEditorTest {
    private KOMETacticalAreaEditor editor;
    private final List<KOMETacticalEditRequest> requests = new ArrayList<>();
    private final UUID player = UUID.randomUUID(), token = UUID.randomUUID();
    private KOMESiegeComplex original;
    @Before public void setup() { load(branching()); }
    private void load(KOMESiegeComplex c) {
        original = c; editor = new KOMETacticalAreaEditor(requests::add);
        editor.acceptCatalog(new KOMETacticalAreaCatalog("T100", c.getDimensionId(), 10, 0, 0, Collections.emptyList()));
        editor.openComplex(c.getComplexId(), false); editor.accept(snapshot(c, 0, false, null), KOMETacticalEditSessionManager.Status.OPENED); requests.clear();
    }
    private KOMETacticalEditSnapshot snapshot(KOMESiegeComplex c, long seq, boolean closed, KOMETacticalEditPreflight p) {
        return new KOMETacticalEditSnapshot(player, token, new KOMETacticalEditScope(KOMETacticalEditScope.Type.SIEGE_COMPLEX,
            c.getTileId(), c.getComplexId(), c.getComplexId(), c.getDimensionId()), 1, seq + 1, 10, c.getRevision(), seq, 10, closed, new KOMETacticalEditDraft(c), p);
    }
    @Test public void connectionCrudIsLocalCaseSensitiveAndPreservesAllOtherFields() {
        load(preferred(original, "FIELD")); editor.createConnection(" new ");
        assertEquals("new", editor.getConnectionId()); assertFalse(editor.getConnection().isGated());
        editor.setConnectionEndpoint(true, KOMESiegeAreaRef.normal("B")); editor.setConnectionTransition("T_AB");
        assertTrue(editor.getConnection().connects(KOMESiegeAreaRef.normal("A")));
        assertTrue(editor.getConnection().connects(KOMESiegeAreaRef.normal("B")));
        KOMESiegeComplex draft = editor.getComplexDraft();
        assertEquals(original.getNormalSegments(), draft.getNormalSegments()); assertEquals(original.getWallZones(), draft.getWallZones());
        assertEquals(original.getTransitionZones(), draft.getTransitionZones()); assertEquals(original.getPreferredForceDeploymentAreaId(), draft.getPreferredForceDeploymentAreaId());
        assertEquals(original.getRevision(), draft.getRevision());
        assertThrows(IllegalArgumentException.class, () -> editor.createConnection("new")); editor.createConnection("NEW");
        editor.selectConnection("new"); editor.deleteConnection(); assertNull(KOMETacticalConnectionDraft.find(editor.getComplexDraft(), "new"));
        editor.selectConnection("NEW"); editor.deleteConnection(); assertEquals(original.getConnections(), editor.getComplexDraft().getConnections());
        assertTrue(requests.isEmpty());
    }
    @Test public void endpointChoicesAreTypedAndContainOnlyOwnNormalsAndExterior() {
        assertEquals(Arrays.asList(KOMESiegeAreaRef.exterior(), KOMESiegeAreaRef.normal("A"), KOMESiegeAreaRef.normal("B"), KOMESiegeAreaRef.normal("C")), editor.endpointChoices());
        editor.selectConnection("ENTRY"); editor.setConnectionEndpoint(true, KOMESiegeAreaRef.exterior());
        for (String forbidden : Arrays.asList("OTHER_COMPLEX_NORMAL", "T_ENTRY", "FIELD", "WALL"))
            assertThrows(IllegalArgumentException.class, () -> editor.setConnectionEndpoint(false, KOMESiegeAreaRef.normal(forbidden)));
        assertThrows(IllegalArgumentException.class, () -> editor.setConnectionTransition("OTHER_TRANSITION"));
        assertThrows(UnsupportedOperationException.class, () -> editor.endpointChoices().clear());
    }
    @Test public void inferenceRequiresAcceptanceAndManualOverrideInvalidatesSuggestion() {
        editor.createConnection("new"); editor.setConnectionTransition("T_AB");
        KOMESiegeComplex before = editor.getComplexDraft(); editor.inferEndpoints();
        assertTrue(editor.getInference().isSuccessful()); assertSame(before, editor.getComplexDraft());
        assertTrue(editor.getMessage().contains("Accept suggestion")); editor.acceptInference();
        assertEquals(KOMESiegeAreaRef.normal("A"), editor.getConnection().getEndpointA()); assertEquals(KOMESiegeAreaRef.normal("B"), editor.getConnection().getEndpointB());
        editor.setConnectionTransition("T_ENTRY"); editor.inferEndpoints(); assertTrue(editor.getInference().getEndpointA().isExterior());
        editor.rejectInference(); assertEquals(KOMESiegeAreaRef.normal("B"), editor.getConnection().getEndpointB());
        editor.inferEndpoints(); editor.setConnectionEndpoint(false, KOMESiegeAreaRef.normal("C")); assertNull(editor.getInference());
        assertThrows(IllegalArgumentException.class, () -> editor.acceptInference());
    }
    @Test public void exteriorSuggestionIsAcceptedExplicitlyWithoutDeploymentPolygon() {
        editor.selectConnection("AB"); editor.setConnectionTransition("T_ENTRY"); editor.inferEndpoints(); editor.acceptInference();
        assertTrue(editor.getConnection().getEndpointA().isExterior()); assertEquals("A", editor.getConnection().getEndpointB().getNormalSegmentId());
        assertNull(editor.getDraft()); assertFalse(editor.getComplexDraft().getPreferredForceDeploymentAreaId().isPresent());
    }
    @Test public void ambiguousAndImpossibleInferenceAreVisibleWithoutChangingDraft() {
        KOMESiegeComplex c = new KOMESiegeComplex("FORT", "T100", 0, 1,
            Arrays.asList(new KOMENormalSegment("A", "", prism(0,0,2,2)), new KOMENormalSegment("B", "", prism(3,0,5,2)),
                new KOMENormalSegment("C", "", prism(6,0,8,2))), Collections.emptyList(),
            Arrays.asList(new KOMETransitionZone("TOO_MANY", "", prism(0,0,8,2)), new KOMETransitionZone("DISTANT", "", prism(40,40,42,42))), null, Collections.emptyList());
        load(c); editor.createConnection("entry"); editor.setConnectionTransition("TOO_MANY"); editor.inferEndpoints();
        assertEquals(KOMEConnectionEndpointInference.Status.TOO_MANY_NORMAL_ENDPOINTS, editor.getInference().getStatus()); assertTrue(editor.getMessage().contains("Ambiguous"));
        assertThrows(IllegalArgumentException.class, () -> editor.acceptInference());
        editor.setConnectionTransition("DISTANT"); editor.inferEndpoints(); assertEquals(KOMEConnectionEndpointInference.Status.NO_ENDPOINT_CANDIDATES, editor.getInference().getStatus());
        assertTrue(editor.getMessage().contains("No endpoints"));
    }
    @Test public void malformedRelevantNormalBlocksButDistantMalformedNormalDoesNot() {
        for (boolean distant : Arrays.asList(false, true)) {
            KOMESiegeComplex base = minimal("FORT", "T100", 0, null);
            KOMENormalSegment bad = new KOMENormalSegment("bad", "", new KOMEPolygonPrism(KOMEPolygon.of(
                new KOMEXZPoint(distant ? 100 : 0, 2), new KOMEXZPoint(distant ? 101 : 1, 3)), 0, 10));
            load(new KOMESiegeComplex("FORT", "T100", 0, 1, Arrays.asList(base.getNormalSegments().get(0), bad),
                base.getWallZones(), base.getTransitionZones(), null, base.getConnections()));
            editor.selectConnection("ENTRY"); editor.inferEndpoints(); assertEquals(distant, editor.getInference().isSuccessful());
            if (!distant) assertTrue(editor.getMessage().contains("bad"));
        }
    }
    @Test public void duplicateNormalIdsRemainGlobalInferenceError() {
        KOMESiegeComplex base = minimal("FORT", "T100", 0, null);
        load(new KOMESiegeComplex("FORT", "T100", 0, 1, Arrays.asList(base.getNormalSegments().get(0), new KOMENormalSegment("A", "", prism(100,100,110,110))),
            base.getWallZones(), base.getTransitionZones(), null, base.getConnections()));
        editor.selectConnection("ENTRY"); editor.inferEndpoints(); assertEquals(KOMEConnectionEndpointInference.Status.DUPLICATE_NORMAL_ID, editor.getInference().getStatus());
        assertTrue(editor.getMessage().contains("duplicate Normal IDs"));
    }
    @Test public void reusedTransitionAndUndeclaredThirdNormalRemainDiagnosed() {
        editor.createConnection("extra"); editor.setConnectionTransition("T_AB");
        assertTrue(editor.connectionDiagnostics().stream().anyMatch(s -> s.contains("CONNECTION_TRANSITION_REUSED")));
        editor.setConnectionEndpoint(false, KOMESiegeAreaRef.normal("C"));
        assertTrue(editor.connectionDiagnostics().stream().anyMatch(s -> s.contains("CONNECTION_TRANSITION_THIRD_NORMAL")));
        assertTrue(editor.connectionDiagnostics().stream().anyMatch(s -> s.contains("CONNECTION_TRANSITION_MISSES_ENDPOINT")));
    }
    @Test public void gateChoicesUseExactCompositeIdentityAndCannotBeForgedLocally() {
        editor.selectConnection("ENTRY"); KOMETacticalComplexCatalog.Row gate = new KOMETacticalComplexCatalog.Row("G1", "Build", "Physical UNKNOWN", "B7", 0, 0);
        assertThrows(IllegalArgumentException.class, () -> editor.setConnectionGate(gate));
        editor.complexPage(KOMETacticalComplexCatalog.Kind.GATES, 0); editor.acceptComplexCatalog(new KOMETacticalComplexCatalog(
            KOMETacticalComplexCatalog.Kind.GATES, "T100", "FORT", 0, 10, 0, 1, Collections.singletonList(gate)));
        editor.setConnectionGate(gate); assertEquals(new KOMEDefensiveGateRef("B7", "G1"), editor.getConnection().getGateRef().get());
        KOMESiegeComplex copy = KOMETacticalEditWire.decodeDraft(KOMETacticalEditWire.encodeDraft(new KOMETacticalEditDraft(editor.getComplexDraft()))).getComplex();
        assertEquals(editor.getComplexDraft().getConnections(), copy.getConnections()); editor.setConnectionGate(null); assertFalse(editor.getConnection().isGated());
    }
    @Test public void unresolvedGateIsDisplayedAndRetainedUntilExplicitGatelessAction() {
        load(minimal("FORT", "T100", 0, new KOMEDefensiveGateRef("MISSING", "G2"))); editor.selectConnection("ENTRY");
        editor.setConnectionEndpoint(true, KOMESiegeAreaRef.exterior()); editor.inferEndpoints(); editor.acceptInference();
        assertEquals(new KOMEDefensiveGateRef("MISSING", "G2"), editor.getConnection().getGateRef().get());
        editor.setConnectionGate(null); assertFalse(editor.getConnection().isGated()); assertTrue(original.getConnections().get(0).isGated());
    }
    private KOMESiegeComplex withGates(KOMESiegeComplex c, Map<String,KOMEDefensiveGateRef> refs) {
        List<KOMESiegeConnection> connections=new ArrayList<>();
        for (KOMESiegeConnection old:c.getConnections()) connections.add(new KOMESiegeConnection(old.getId(),old.getEndpointA(),old.getEndpointB(),old.getTransitionZoneId(),refs.get(old.getId())));
        return new KOMESiegeComplex(c.getComplexId(),c.getTileId(),c.getDimensionId(),c.getRevision(),c.getNormalSegments(),c.getWallZones(),c.getTransitionZones(),c.getPreferredForceDeploymentAreaId().orElse(null),connections);
    }
    private KOMETacticalComplexCatalog.Row offer(String record) {
        KOMETacticalComplexCatalog.Row row=new KOMETacticalComplexCatalog.Row(record,"Build","Physical UNKNOWN","B7",0,0);
        editor.complexPage(KOMETacticalComplexCatalog.Kind.GATES,0); editor.acceptComplexCatalog(new KOMETacticalComplexCatalog(
            KOMETacticalComplexCatalog.Kind.GATES,"T100","FORT",0,10,0,1,Collections.singletonList(row))); return row;
    }
    @Test public void usedGateCannotBeNewlySelectedButOwnGateRemainsSelectable() {
        load(withGates(original,Collections.singletonMap("ENTRY",new KOMEDefensiveGateRef("B7","G1"))));
        editor.selectConnection("AB"); KOMETacticalComplexCatalog.Row g1=offer("G1");
        assertFalse(editor.canSelectConnectionGate(g1)); assertTrue(editor.gateChoiceUsage(g1).contains("Used by ENTRY"));
        assertThrows(IllegalArgumentException.class,()->editor.setConnectionGate(g1)); assertFalse(editor.getConnection().isGated());
        editor.selectConnection("ENTRY"); assertTrue(editor.canSelectConnectionGate(g1)); editor.setConnectionGate(g1);
        assertEquals(new KOMEDefensiveGateRef("B7","G1"),editor.getConnection().getGateRef().get());
    }
    @Test public void changingOwnGateGatelessOrRemovingConnectionFreesOldChoice() {
        for (String operation:Arrays.asList("change","gateless","delete")) {
            load(withGates(original,Collections.singletonMap("ENTRY",new KOMEDefensiveGateRef("B7","G1")))); editor.selectConnection("ENTRY");
            if(operation.equals("change")) editor.setConnectionGate(offer("G2"));
            else if(operation.equals("gateless")) editor.setConnectionGate(null); else editor.deleteConnection();
            editor.selectConnection("AB"); KOMETacticalComplexCatalog.Row row=offer("G1"); assertTrue(editor.canSelectConnectionGate(row));
            editor.setConnectionGate(row); assertEquals(new KOMEDefensiveGateRef("B7","G1"),editor.getConnection().getGateRef().get());
        }
    }
    @Test public void historicalDuplicatesRemainVisibleWithBothIdsAndAreRepairable() {
        Map<String,KOMEDefensiveGateRef> refs=new HashMap<>(); refs.put("ENTRY",new KOMEDefensiveGateRef("B7","G1")); refs.put("AB",refs.get("ENTRY"));
        load(withGates(original,refs)); editor.selectConnection("AB"); KOMETacticalComplexCatalog.Row row=offer("G1");
        assertTrue(editor.canSelectConnectionGate(row)); assertTrue(editor.gateChoiceUsage(row).contains("repair duplicate"));
        assertTrue(editor.connectionDiagnostics().stream().anyMatch(d->d.contains("CONNECTION_GATE_REUSED")&&d.contains("AB")&&d.contains("ENTRY")));
        editor.setConnectionGate(row); assertEquals(2,editor.getComplexDraft().getConnections().stream().filter(KOMESiegeConnection::isGated).count());
        editor.selectConnection("AC"); assertFalse(editor.canSelectConnectionGate(row));
        assertThrows(IllegalArgumentException.class,()->editor.setConnectionGate(row));
        editor.selectConnection("AB"); editor.setConnectionGate(null);
        assertTrue(KOMESiegeGateUsage.validate(editor.getComplexDraft()).isValid()); assertEquals(2,original.getConnections().stream().filter(KOMESiegeConnection::isGated).count());
    }
    @Test public void saveUsesExistingAcknowledgedSequenceAndCancelDiscardsOnlyDraft() {
        editor.createConnection("new"); editor.save(); assertEquals(KOMETacticalEditRequest.Action.UPDATE, requests.get(0).getAction());
        KOMETacticalEditDraft sent = KOMETacticalEditWire.decodeDraft(requests.get(0).getPayload());
        assertNull(sent.getMembershipAction()); editor.accept(snapshot(sent.getComplex(), 1, false, null), KOMETacticalEditSessionManager.Status.UPDATED);
        assertEquals(KOMETacticalEditRequest.Action.SAVE, requests.get(1).getAction()); assertEquals(1, requests.get(1).getExpectedSequence());
        editor.cancel(); assertFalse(editor.isEditing()); assertEquals(3, original.getConnections().size());
    }
    @Test public void staleSaveLeavesEditedTopologyAndOriginalIndependent() {
        editor.createConnection("new"); KOMESiegeComplex draft = editor.getComplexDraft();
        editor.accept(snapshot(original, 0, false, null), KOMETacticalEditSessionManager.Status.STALE_STORE);
        assertSame(draft, editor.getComplexDraft()); assertEquals(3, original.getConnections().size()); assertTrue(editor.getMessage().contains("draft was kept"));
    }
    @Test public void topologyOverlayIsScopedTypedDeterministicAndClearsOnCancel() {
        editor.selectConnection("ENTRY"); List<KOMETacticalAreaEditor.ConnectionOverlay> overlays = editor.connectionOverlays(player, 0);
        assertEquals("AB", overlays.get(0).connection.getId()); assertEquals("ENTRY", overlays.get(2).connection.getId());
        KOMETacticalAreaEditor.ConnectionOverlay entry = overlays.get(2); assertNull(entry.normalA); assertNotNull(entry.normalB); assertTrue(entry.selected); assertFalse(entry.invalid);
        assertEquals(2, editor.overlays(player,0).stream().filter(o -> o.selected).count());
        assertTrue(editor.connectionOverlays(UUID.randomUUID(),0).isEmpty()); assertTrue(editor.connectionOverlays(player,1).isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> overlays.clear()); editor.cancel(); assertTrue(editor.connectionOverlays(player,0).isEmpty());
    }
    @Test public void deterministicConnectionCatalogueRejectsDuplicateIdsRatherThanCollapsing() {
        assertEquals(Arrays.asList("AB", "AC", "ENTRY"), Arrays.asList(editor.getConnections().get(0).getId(), editor.getConnections().get(1).getId(), editor.getConnections().get(2).getId()));
        KOMESiegeConnection c = original.getConnections().get(0); load(new KOMESiegeComplex("FORT", "T100", 0, 1, original.getNormalSegments(),
            original.getWallZones(), original.getTransitionZones(), null, Arrays.asList(c,c)));
        assertThrows(IllegalArgumentException.class, () -> editor.selectConnection(c.getId())); assertEquals(2, editor.getConnections().size());
    }
    @Test public void topologyViewingDoesNotConsumeGeometryClicksOrChangePolygonInput() {
        editor.selectConnection("ENTRY"); KOMESiegeComplex before=editor.getComplexDraft(); editor.select(KOMETacticalAreaEditor.Selection.TOPOLOGY);
        assertFalse(editor.consumesClicks(player,0)); editor.worldPoint(100,64,100); editor.cycleCorner(); assertSame(before,editor.getComplexDraft());
        assertEquals(KOMETacticalPolygonPreview.Corner.NW,editor.getCorner()); editor.select(KOMETacticalAreaEditor.Selection.NONE);
        assertEquals("ENTRY",editor.getConnectionId());
    }
}
