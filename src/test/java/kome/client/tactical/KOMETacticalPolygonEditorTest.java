package kome.client.tactical;

import java.util.*;
import kome.common.siege.*;
import kome.common.siege.geometry.*;
import kome.common.tactical.*;
import kome.common.tactical.edit.*;
import kome.client.tactical.KOMETacticalComplexDraft.ZoneType;
import org.junit.*;
import static org.junit.Assert.*;

/** Shared world-corner input, exact drafts, and retained live-validator regression cases. */
public class KOMETacticalPolygonEditorTest {
    private List<KOMETacticalEditRequest> requests;
    private KOMETacticalAreaEditor editor;
    private UUID player, token;
    @Before public void setup() {
        requests = new ArrayList<>(); editor = new KOMETacticalAreaEditor(requests::add);
        player = UUID.randomUUID(); token = UUID.randomUUID();
        editor.acceptCatalog(new KOMETacticalAreaCatalog("T100", -1, 10, 0, 0, Collections.emptyList()));
    }
    private KOMETacticalEditSnapshot snapshot(KOMETacticalEditDraft draft, long sequence, boolean closed) {
        String id = draft.getComplex() == null ? draft.getArea().getAreaId() : draft.getComplex().getComplexId();
        KOMETacticalEditScope scope = new KOMETacticalEditScope(draft.getComplex() == null ? KOMETacticalEditScope.Type.TILE_FORCE_DEPLOYMENT_AREA
            : KOMETacticalEditScope.Type.SIEGE_COMPLEX, "T100", draft.getComplex() == null ? null : id, id, -1);
        return new KOMETacticalEditSnapshot(player, token, scope, 1, sequence+1, 10, draft.getObjectRevision(), sequence, 10, closed, draft, null);
    }
    private void area() {
        editor.open("FIELD", true);
        editor.accept(snapshot(new KOMETacticalEditDraft(new KOMEForceDeploymentArea("FIELD", "T100", -1, "",
            new KOMEPolygonPrism(new KOMEPolygon(Collections.emptyList()), 60, 71), 0)), 0, false), KOMETacticalEditSessionManager.Status.OPENED); requests.clear();
    }
    private void complex(KOMESiegeComplex definition) {
        editor.openComplex("FORT", false); editor.accept(snapshot(new KOMETacticalEditDraft(definition), 0, false), KOMETacticalEditSessionManager.Status.OPENED); requests.clear();
    }
    private void rectangle(int x, int z) {
        editor.select(KOMETacticalAreaEditor.Selection.VERTICES);
        while(editor.getCorner()!=KOMETacticalPolygonPreview.Corner.NW) editor.cycleCorner();
        editor.worldPoint(x,60,z); editor.worldPoint(x+4,64,z); editor.worldPoint(x+4,65,z+2); editor.worldPoint(x,66,z+2);
    }
    private void confirm(int... coordinates) {
        editor.select(KOMETacticalAreaEditor.Selection.VERTICES);
        for(int i=0;i<coordinates.length;i+=2) editor.worldPoint(coordinates[i],60,coordinates[i+1]);
    }
    @Test public void everyConfirmationImmediatelyAddsExactlyTheHighlightedCorner() {
        area(); editor.select(KOMETacticalAreaEditor.Selection.VERTICES);
        for(int i=0;i<4;i++) {
            KOMEXZPoint highlighted=editor.aimedCorner(-4+i*3,7+i*2);
            editor.worldPoint(-4+i*3,90,7+i*2);
            assertEquals(highlighted,editor.getConfirmedVertices().get(i));
            assertEquals(i+1,editor.getConfirmedVertexCount()); editor.cycleCorner();
        }
        assertEquals(60,editor.getGeometry().getMinYInclusive());assertEquals(71,editor.getGeometry().getMaxYExclusive());
        assertThrows(UnsupportedOperationException.class,()->editor.getConfirmedVertices().clear());
    }
    @Test public void tabCyclesCornersAndChangingAimPreservesOrientation() {
        area();editor.select(KOMETacticalAreaEditor.Selection.VERTICES);
        for(KOMETacticalPolygonPreview.Corner c:KOMETacticalPolygonPreview.Corner.values()) {
            assertEquals(c,editor.getCorner());assertEquals(c.point(7,-3),editor.aimedCorner(7,-3));
            assertEquals(c.point(-8,11),editor.aimedCorner(-8,11));assertEquals(c,editor.getCorner());editor.cycleCorner();
        }
        assertEquals(KOMETacticalPolygonPreview.Corner.NW,editor.getCorner());
        editor.cycleCorner();editor.worldPoint(0,60,0);editor.worldPoint(8,60,3);
        assertEquals(Arrays.asList(new KOMEXZPoint(1,0),new KOMEXZPoint(9,3)),editor.getConfirmedVertices());
        editor.select(KOMETacticalAreaEditor.Selection.NONE);editor.select(KOMETacticalAreaEditor.Selection.VERTICES);
        assertEquals(KOMETacticalPolygonPreview.Corner.NE,editor.getCorner());
    }
    @Test public void validTriangleAndQuadrilateralHaveExactlyTheirConfirmedDistinctVertices() {
        area();confirm(0,0,5,0,0,5);
        assertEquals(3,editor.getConfirmedVertexCount());assertEquals(3,editor.getDistinctVertexCount());assertFalse(editor.geometryFeedback().isInvalid());
        assertTrue(KOMEPolygonValidator.validate(editor.getGeometry().getPolygon(),"triangle").isValid());
        editor.clearVertices();rectangle(0,0);
        assertEquals(4,editor.getConfirmedVertexCount());assertEquals(4,editor.getDistinctVertexCount());assertFalse(editor.geometryFeedback().isInvalid());
    }
    @Test public void formerlyOversizedRasterDiagonalNowStaysAnExactValidThreeVertexPolygon() {
        area();confirm(0,0,100,100,0,100);
        assertEquals(Arrays.asList(new KOMEXZPoint(0,0),new KOMEXZPoint(100,100),new KOMEXZPoint(0,100)),editor.getConfirmedVertices());
        assertEquals(3,editor.getDistinctVertexCount());assertFalse(editor.geometryFeedback().isInvalid());
        assertFalse(new KOMEForceDeploymentAreaValidator().validate(editor.getDraft()).hasCode(kome.common.siege.validation.KOMEValidationCode.POLYGON_TOO_FEW_DISTINCT_VERTICES));
        assertFalse(editor.getFillTriangles().isEmpty());
    }
    @Test public void concavePolygonsAreUnchangedAndNeverSimplifiedOrFilledFromBlocks() {
        area();confirm(0,0,8,0,8,2,2,2,2,8,0,8);
        assertEquals(6,editor.getConfirmedVertexCount());assertTrue(KOMEPolygonValidator.validate(editor.getGeometry().getPolygon(),"concave").isValid());
        assertEquals(KOMEPointClassification.OUTSIDE,editor.getGeometry().getPolygon().classify(new KOMEXZPoint(6,6)));
        assertFalse(editor.getFillTriangles().isEmpty());
    }
    @Test public void duplicateExactCornerIsExplicitlyRejectedWithoutChangingAnyDraftOrCount() {
        area();confirm(0,0,5,0,0,5);KOMEPolygonPrism before=editor.getGeometry();int requestsBefore=requests.size();
        IllegalArgumentException error=assertThrows(IllegalArgumentException.class,()->editor.worldPoint(0,60,0));
        assertTrue(error.getMessage().contains("already a polygon vertex"));assertEquals(before,editor.getGeometry());
        assertEquals(3,editor.getConfirmedVertexCount());assertEquals(3,editor.getDistinctVertexCount());assertEquals(requestsBefore,requests.size());
        assertFalse(editor.geometryFeedback().isInvalid());
        // Different blocks/corners can identify the SAME grid coordinate; this is equally diagnosed.
        editor.cycleCorner();assertThrows(IllegalArgumentException.class,()->editor.worldPoint(-1,60,0));assertEquals(before,editor.getGeometry());
        editor.error(error.getMessage());
        KOMEForceDeploymentArea empty=new KOMEForceDeploymentArea("FIELD","T100",-1,"",new KOMEPolygonPrism(new KOMEPolygon(Collections.emptyList()),60,71),0);
        editor.accept(snapshot(new KOMETacticalEditDraft(empty),0,false),KOMETacticalEditSessionManager.Status.REFRESHED);
        assertEquals(error.getMessage(),editor.getMessage());assertEquals(before,editor.getGeometry());assertEquals(3,editor.getConfirmedVertexCount());
    }
    @Test public void existingMalformedDuplicateGeometryCountsInputHonestlyAndRemainsRepairable() {
        area();KOMEForceDeploymentArea malformed=new KOMEForceDeploymentArea("FIELD","T100",-1,"",new KOMEPolygonPrism(KOMEPolygon.of(
            new KOMEXZPoint(0,0),new KOMEXZPoint(4,0),new KOMEXZPoint(0,0),new KOMEXZPoint(4,0)),60,71),0);
        editor.accept(snapshot(new KOMETacticalEditDraft(malformed),1,false),KOMETacticalEditSessionManager.Status.UPDATED);
        assertEquals(4,editor.getConfirmedVertexCount());assertEquals(2,editor.getDistinctVertexCount());assertTrue(editor.geometryFeedback().isInvalid());
        editor.clearVertices();confirm(0,0,4,0,0,4);assertFalse(editor.geometryFeedback().isInvalid());assertEquals(3,editor.getDistinctVertexCount());
    }
    @Test public void undoRemovesOnlyLatestVertexAndDeleteClearsExactlyTheDraftInput() {
        area();rectangle(0,0);editor.undo();assertEquals(Arrays.asList(new KOMEXZPoint(0,0),new KOMEXZPoint(4,0),new KOMEXZPoint(4,2)),editor.getConfirmedVertices());
        assertTrue(KOMEPolygonValidator.validate(editor.getGeometry().getPolygon(),"triangle").isValid());editor.undo();assertEquals(2,editor.getConfirmedVertexCount());
        assertTrue(editor.geometryFeedback().isInvalid());editor.clearVertices();assertEquals(0,editor.getConfirmedVertexCount());assertTrue(editor.getFillTriangles().isEmpty());
        editor.undo();assertEquals(0,editor.getConfirmedVertexCount());rectangle(10,10);assertEquals(4,editor.getConfirmedVertexCount());
    }
    @Test public void everyZoneUsesSameCornerSelectionAndEditsStayDetachedAndIndependent() {
        complex(KOMESiegeReadinessFixtures.empty("FORT","T100",-1));List<KOMEPolygon> polygons=new ArrayList<>();
        for(ZoneType type:ZoneType.values()) {
            editor.zoneType(type);editor.createZone(type.name());rectangle(10,10);polygons.add(editor.getGeometry().getPolygon());assertEquals(4,editor.getConfirmedVertexCount());
        }
        assertEquals(polygons.get(0),polygons.get(1));assertEquals(polygons.get(1),polygons.get(2));
        editor.zoneType(ZoneType.NORMAL);editor.selectZone("NORMAL");editor.undo();assertEquals(3,editor.getConfirmedVertexCount());
        editor.zoneType(ZoneType.WALL);editor.selectZone("WALL");assertEquals(polygons.get(1),editor.getGeometry().getPolygon());
        assertTrue(requests.stream().noneMatch(r->r.getAction()==KOMETacticalEditRequest.Action.SAVE));
    }
    @Test public void previewSaveDecodeAndReopenPreserveEveryIntegerVertexInOrder() {
        area();confirm(-4,7,10,-3,5,20,-8,18);KOMEPolygonPrism preview=editor.getGeometry();
        assertEquals(preview,editor.overlays(player,-1).get(0).prism);editor.save();
        KOMETacticalEditDraft submitted=KOMETacticalEditWire.decodeDraft(requests.get(requests.size()-1).getPayload());assertEquals(preview,submitted.getArea().getPrism());
        editor.accept(snapshot(submitted,1,false),KOMETacticalEditSessionManager.Status.UPDATED);assertEquals(KOMETacticalEditRequest.Action.SAVE,requests.get(requests.size()-1).getAction());
        editor.accept(snapshot(submitted,1,true),KOMETacticalEditSessionManager.Status.SAVED);
        editor.acceptCatalog(new KOMETacticalAreaCatalog("T100",-1,11,0,1,Collections.singletonList(new KOMETacticalAreaCatalog.Row("FIELD","",1))));
        token=UUID.randomUUID();editor.open("FIELD",false);editor.accept(snapshot(submitted.atRevision(1),0,false),KOMETacticalEditSessionManager.Status.OPENED);
        assertEquals(preview,editor.getGeometry());assertEquals(preview.getPolygon().getVertices(),editor.getConfirmedVertices());
        editor.select(KOMETacticalAreaEditor.Selection.VERTICES);assertEquals(preview,editor.getGeometry());
        editor.undo();assertEquals(preview.getPolygon().getVertices().subList(0,3),editor.getConfirmedVertices());
    }
    @Test public void malformedCrossingRetainsAllConfirmedPointsAndShowsActualValidatorDiagnostic() {
        area();confirm(0,0,5,5,0,5,5,0);
        assertEquals(4,editor.getConfirmedVertexCount());assertEquals(4,editor.getDistinctVertexCount());assertTrue(editor.geometryFeedback().isInvalid());
        assertTrue(editor.geometryFeedback().messages.stream().anyMatch(m->m.contains("POLYGON_SELF_INTERSECTION")));
        assertTrue(editor.geometryFeedback().messages.stream().noneMatch(m->m.contains("POLYGON_TOO_FEW_DISTINCT_VERTICES")));
        assertTrue(editor.overlays(player,-1).get(0).invalid);editor.undo();assertFalse(editor.geometryFeedback().isInvalid());
    }
    private KOMESiegeComplex withNormal() {
        return new KOMESiegeComplex("FORT","T100",-1,1,Collections.singletonList(new KOMENormalSegment("COURT","",
            KOMESiegeReadinessFixtures.prism(0,0,10,10))),Collections.emptyList(),Collections.emptyList(),null,Collections.emptyList());
    }
    @Test public void forbiddenOverlapTurnsPreviewRedAndIdentifiesConflictingZoneUsingServerValidator() {
        complex(withNormal());editor.zoneType(ZoneType.WALL);editor.createZone("WALL");rectangle(5,5);
        assertTrue(new KOMESiegeComplexValidator().validate(editor.getComplexDraft()).hasCode(kome.common.siege.validation.KOMEValidationCode.NORMAL_WALL_OVERLAP));
        assertTrue(editor.geometryFeedback().messages.stream().anyMatch(m->m.contains("NORMAL_WALL_OVERLAP")&&m.contains("COURT")&&m.contains("WALL")));
        assertTrue(editor.geometryFeedback().conflictingZoneIds.contains("COURT"));assertTrue(editor.overlays(player,-1).stream().filter(o->o.selected).allMatch(o->o.invalid));
        assertThrows(UnsupportedOperationException.class,()->editor.geometryFeedback().messages.clear());
    }
    @Test public void permittedBoundaryContactAndNormalTransitionOverlapAreNotFalsePreviewErrors() {
        complex(withNormal());editor.zoneType(ZoneType.WALL);editor.createZone("WALL");rectangle(10,0);
        assertFalse(editor.geometryFeedback().isInvalid()); // missing wall access is configuration, not overlap
        editor.zoneType(ZoneType.TRANSITION);editor.createZone("T");rectangle(1,1);assertFalse(editor.geometryFeedback().isInvalid());
        assertFalse(editor.overlays(player,-1).stream().filter(o->o.selected).findFirst().get().invalid);
        editor.save();assertEquals(KOMETacticalEditRequest.Action.UPDATE,requests.get(requests.size()-1).getAction());
    }
    @Test public void liveYSeparationUsesExistingThreeDimensionalRulesAndCleanupClearsVertices() {
        complex(withNormal());editor.zoneType(ZoneType.WALL);editor.createZone("W");rectangle(1,1);
        assertTrue(editor.geometryFeedback().isInvalid());editor.setYRangeInclusive(10,19);assertFalse(editor.geometryFeedback().isInvalid());
        editor.cancel();assertTrue(editor.getConfirmedVertices().isEmpty());assertTrue(editor.overlays(player,-1).isEmpty());editor.reset();assertTrue(editor.geometryFeedback().messages.isEmpty());
    }
    @Test public void authoredTransitionContactRulesAlsoAppearInLiveFeedback() {
        KOMESiegeComplex original=KOMESiegeReadinessFixtures.minimal("FORT","T100",-1,null);
        List<KOMENormalSegment> normals=new ArrayList<>(original.getNormalSegments());normals.add(new KOMENormalSegment("OTHER","",KOMESiegeReadinessFixtures.prism(-6,2,-4,5)));
        complex(new KOMESiegeComplex("FORT","T100",-1,original.getRevision(),normals,original.getWallZones(),original.getTransitionZones(),null,original.getConnections()));
        editor.zoneType(ZoneType.TRANSITION);editor.selectZone("T_ENTRY");assertFalse(editor.geometryFeedback().isInvalid());
        editor.clearVertices();rectangle(-4,2);
        assertTrue(editor.geometryFeedback().messages.stream().anyMatch(m->m.contains("CONNECTION_TRANSITION_THIRD_NORMAL")&&m.contains("OTHER")));
        assertTrue(editor.overlays(player,-1).stream().filter(o->o.selected).findFirst().get().invalid);
    }
    @Test public void allSixPairRulesAgreeBetweenLivePreviewAndCurrentValidatorForOverlapAndBoundaryTouch() {
        for(int i=0;i<ZoneType.values().length;i++)for(int j=i;j<ZoneType.values().length;j++) {
            ZoneType a=ZoneType.values()[i],b=ZoneType.values()[j];
            for(boolean overlap:new boolean[]{false,true}) {
                if(editor.isEditing())editor.cancel();token=UUID.randomUUID();KOMESiegeComplex c=KOMESiegeReadinessFixtures.empty("FORT","T100",-1);
                c=KOMETacticalComplexDraft.create(c,a,"FIRST");c=KOMETacticalComplexDraft.edit(c,a,"FIRST","",KOMESiegeReadinessFixtures.prism(0,0,10,10));
                c=KOMETacticalComplexDraft.create(c,b,"SECOND");c=KOMETacticalComplexDraft.edit(c,b,"SECOND","",KOMESiegeReadinessFixtures.prism(overlap?5:10,0,15,10));
                complex(c);editor.zoneType(a);editor.selectZone("FIRST");boolean forbidden=overlap&&!(a==ZoneType.NORMAL&&b==ZoneType.TRANSITION);
                boolean serverOverlap=new KOMESiegeComplexValidator().validate(c).getIssues().stream().anyMatch(issue->issue.getCode().name().endsWith("_OVERLAP"));
                assertEquals(a+"/"+b+" overlap="+overlap,forbidden,serverOverlap);assertEquals(a+"/"+b+" preview",forbidden,editor.geometryFeedback().isInvalid());
            }
        }
    }
}
