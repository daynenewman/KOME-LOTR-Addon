package kome.client.tactical;

import java.util.*;
import java.math.BigInteger;
import kome.common.siege.geometry.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMETacticalPolygonPreviewTest {
    @Test public void fourCornersUseWorldAxesAndCycleInDocumentedOrder() {
        KOMETacticalPolygonPreview.Corner c=KOMETacticalPolygonPreview.Corner.NW;
        List<KOMEXZPoint> actual=new ArrayList<>();
        for(int i=0;i<4;i++) {actual.add(c.point(-3,7));c=c.next();}
        assertEquals(Arrays.asList(new KOMEXZPoint(-3,7),new KOMEXZPoint(-2,7),new KOMEXZPoint(-2,8),new KOMEXZPoint(-3,8)),actual);
        assertEquals(KOMETacticalPolygonPreview.Corner.NW,c);
    }
    @Test public void extremeCoordinatesAreExactAndCannotWrap() {
        assertEquals(new KOMEXZPoint(Integer.MAX_VALUE,Integer.MIN_VALUE),KOMETacticalPolygonPreview.Corner.NW.point(Integer.MAX_VALUE,Integer.MIN_VALUE));
        assertThrows(IllegalArgumentException.class,()->KOMETacticalPolygonPreview.Corner.NE.point(Integer.MAX_VALUE,0));
        assertThrows(IllegalArgumentException.class,()->KOMETacticalPolygonPreview.Corner.SW.point(0,Integer.MAX_VALUE));
    }
    @Test public void displayOnlyFillPreservesExactConvexConcaveDiagonalAreaAndOrder() {
        KOMEPolygon triangle=KOMEPolygon.of(new KOMEXZPoint(-8,0),new KOMEXZPoint(4,9),new KOMEXZPoint(-2,12));
        KOMEPolygon concave=KOMEPolygon.of(new KOMEXZPoint(0,0),new KOMEXZPoint(8,0),new KOMEXZPoint(8,2),new KOMEXZPoint(2,2),new KOMEXZPoint(2,8),new KOMEXZPoint(0,8));
        for(KOMEPolygon p:Arrays.asList(triangle,concave)) {
            List<KOMEXZPoint> before=new ArrayList<>(p.getVertices());List<KOMEXZPoint> fill=KOMETacticalPolygonPreview.triangles(p);assertFalse(fill.isEmpty());
            BigInteger area=BigInteger.ZERO;for(int i=0;i<fill.size();i+=3)area=area.add(KOMEPolygon.of(fill.get(i),fill.get(i+1),fill.get(i+2)).getSignedAreaTwice());
            assertEquals(p.getSignedAreaTwice(),area);assertEquals(before,p.getVertices());assertThrows(UnsupportedOperationException.class,()->fill.clear());
            List<KOMEXZPoint> reverse=new ArrayList<>(before);Collections.reverse(reverse);assertFalse(KOMETacticalPolygonPreview.triangles(new KOMEPolygon(reverse)).isEmpty());
        }
    }
    @Test public void malformedOutlineIsNotRepairedForDisplay() {
        KOMEPolygon p=KOMEPolygon.of(new KOMEXZPoint(0,0),new KOMEXZPoint(4,4),new KOMEXZPoint(0,4),new KOMEXZPoint(4,0));
        List<KOMEXZPoint> before=new ArrayList<>(p.getVertices());assertTrue(KOMETacticalPolygonPreview.triangles(p).isEmpty());assertEquals(before,p.getVertices());
    }
    @Test public void bottomFaceFollowsActualPlayerPositionAcrossZoneTypesAndExterior() {
        KOMEPolygonPrism normal = square(0, 0, 10, 10, 60, 70);
        KOMEPolygonPrism wall = square(10, 0, 20, 10, 60, 70);
        KOMEPolygonPrism transition = square(20, 0, 30, 10, 60, 70);
        KOMEPolygonPrism exterior = square(40, 0, 50, 10, 60, 70);
        KOMETacticalAreaEditor.Overlay[] overlays = {
            new KOMETacticalAreaEditor.Overlay(normal, KOMETacticalComplexDraft.ZoneType.NORMAL, "N", "", false, false, false),
            new KOMETacticalAreaEditor.Overlay(wall, KOMETacticalComplexDraft.ZoneType.WALL, "W", "", false, false, false),
            new KOMETacticalAreaEditor.Overlay(transition, KOMETacticalComplexDraft.ZoneType.TRANSITION, "T", "", false, false, false),
            new KOMETacticalAreaEditor.Overlay(exterior, null, "E", "", false, false, true)
        };
        for (int i = 0; i < overlays.length; i++) {
            KOMEPolygonPrism here = overlays[i].prism; double x = i == 3 ? 45 : i * 10 + 5;
            assertEquals(6, KOMETacticalPolygonPreview.bottomFaceForPlayer(here, x, 65, 5).size());
            assertTrue(KOMETacticalPolygonPreview.bottomFaceForPlayer(here, x, 59.99, 5).isEmpty());
            assertTrue(KOMETacticalPolygonPreview.bottomFaceForPlayer(here, x, 70, 5).isEmpty());
            assertTrue(KOMETacticalPolygonPreview.bottomFaceForPlayer(here, x, 65, 15).isEmpty());
        }
        assertTrue(KOMETacticalPolygonPreview.bottomFaceForPlayer(normal, 15, 65, 5).isEmpty());
        assertFalse(KOMETacticalPolygonPreview.bottomFaceForPlayer(wall, 15, 65, 5).isEmpty());
    }
    @Test public void boundariesAndOverlapsFillEveryContainingPrismWithoutSelectionPriority() {
        KOMEPolygonPrism a = square(0, 0, 10, 10, 60, 70);
        KOMEPolygonPrism b = square(5, 0, 15, 10, 60, 70);
        assertFalse(KOMETacticalPolygonPreview.bottomFaceForPlayer(a, 7, 60, 5).isEmpty());
        assertFalse(KOMETacticalPolygonPreview.bottomFaceForPlayer(b, 7, 60, 5).isEmpty());
        assertFalse(KOMETacticalPolygonPreview.bottomFaceForPlayer(a, 10, 65, 5).isEmpty());
        assertFalse(KOMETacticalPolygonPreview.bottomFaceForPlayer(b, 10, 65, 5).isEmpty());
        assertTrue(KOMETacticalPolygonPreview.bottomFaceForPlayer(a, Double.NaN, 65, 5).isEmpty());
        KOMETacticalAreaEditor.Overlay selectedOutside = new KOMETacticalAreaEditor.Overlay(b,
            KOMETacticalComplexDraft.ZoneType.WALL, "B", "", true, false, false);
        KOMETacticalAreaEditor.Overlay unselectedInside = new KOMETacticalAreaEditor.Overlay(a,
            KOMETacticalComplexDraft.ZoneType.NORMAL, "A", "", false, false, true);
        assertTrue(KOMETacticalPolygonPreview.bottomFaceForPlayer(selectedOutside.prism, 2, 65, 5).isEmpty());
        assertFalse(KOMETacticalPolygonPreview.bottomFaceForPlayer(unselectedInside.prism, 2, 65, 5).isEmpty());
        KOMEPolygonPrism malformed = new KOMEPolygonPrism(KOMEPolygon.of(new KOMEXZPoint(0,0),
            new KOMEXZPoint(10,10), new KOMEXZPoint(0,10), new KOMEXZPoint(10,0)), 60, 70);
        assertTrue(KOMETacticalPolygonPreview.bottomFaceForPlayer(malformed, 5, 65, 5).isEmpty());
    }
    @Test public void topHeightGuideShowsInclusiveBlockAndExclusiveStoredBoundary() {
        assertEquals(43D, KOMETacticalAreaInteractionHandler.heightGuideY(KOMETacticalAreaEditor.Selection.UPPER_Y, 42), 0D);
        assertEquals("Top Y 42 (upper face 43)", KOMETacticalAreaInteractionHandler.heightGuideLabel(KOMETacticalAreaEditor.Selection.UPPER_Y, 42));
        assertEquals(42D, KOMETacticalAreaInteractionHandler.heightGuideY(KOMETacticalAreaEditor.Selection.LOWER_Y, 42), 0D);
        assertEquals("Bottom Y 42", KOMETacticalAreaInteractionHandler.heightGuideLabel(KOMETacticalAreaEditor.Selection.LOWER_Y, 42));
        assertEquals(2147483648D, KOMETacticalAreaInteractionHandler.heightGuideY(KOMETacticalAreaEditor.Selection.UPPER_Y, Integer.MAX_VALUE), 0D);
    }
    private static KOMEPolygonPrism square(int x0, int z0, int x1, int z1, int low, int high) {
        return new KOMEPolygonPrism(KOMEPolygon.of(new KOMEXZPoint(x0,z0), new KOMEXZPoint(x1,z0),
            new KOMEXZPoint(x1,z1), new KOMEXZPoint(x0,z1)), low, high);
    }
}
