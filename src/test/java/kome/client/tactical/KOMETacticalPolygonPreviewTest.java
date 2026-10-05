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
}
