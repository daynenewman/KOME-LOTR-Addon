package kome.common.siege.geometry;

import kome.common.siege.validation.KOMEValidationCode;
import kome.common.siege.validation.KOMEValidationResult;
import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class KOMEGeometryTest {
    @Test public void convexPointClassificationDistinguishesInteriorBoundaryAndExterior(){
        KOMEPolygon square=square(0,0,10,10);
        assertEquals(KOMEPointClassification.INTERIOR,square.classify(p(5,5)));
        assertEquals(KOMEPointClassification.BOUNDARY,square.classify(p(0,5)));
        assertEquals(KOMEPointClassification.BOUNDARY,square.classify(p(10,10)));
        assertEquals(KOMEPointClassification.OUTSIDE,square.classify(p(11,5)));
    }
    @Test public void concavePointClassificationHonorsTheConcavity(){
        KOMEPolygon concave=KOMEPolygon.of(p(0,0),p(6,0),p(6,2),p(2,2),p(2,6),p(0,6));
        assertEquals(KOMEPointClassification.INTERIOR,concave.classify(p(1,5)));
        assertEquals(KOMEPointClassification.OUTSIDE,concave.classify(p(4,4)));
        assertEquals(KOMEPointClassification.BOUNDARY,concave.classify(p(2,4)));
    }
    @Test public void clockwiseAndCounterclockwisePolygonsClassifyEquivalently(){
        List<KOMEXZPoint> points=new ArrayList<KOMEXZPoint>(square(0,0,10,10).getVertices());
        KOMEPolygon ccw=new KOMEPolygon(points);Collections.reverse(points);KOMEPolygon cw=new KOMEPolygon(points);
        for(KOMEXZPoint sample:Arrays.asList(p(5,5),p(0,5),p(20,20)))assertEquals(ccw.classify(sample),cw.classify(sample));
        assertTrue(KOMEPolygonValidator.validate(ccw,"ccw").isValid());
        assertTrue(KOMEPolygonValidator.validate(cw,"cw").isValid());
    }
    @Test public void malformedPolygonsReturnExplicitCodes(){
        assertCode(new KOMEPolygon(Arrays.asList(p(0,0),p(1,0))),KOMEValidationCode.POLYGON_TOO_FEW_DISTINCT_VERTICES);
        KOMEPolygon consecutive=KOMEPolygon.of(p(0,0),p(4,0),p(4,0),p(4,4),p(0,4));
        assertCode(consecutive,KOMEValidationCode.POLYGON_CONSECUTIVE_DUPLICATE);
        assertCode(consecutive,KOMEValidationCode.POLYGON_ZERO_LENGTH_EDGE);
        assertCode(KOMEPolygon.of(p(0,0),p(2,0),p(4,0)),KOMEValidationCode.POLYGON_ZERO_AREA);
        assertCode(KOMEPolygon.of(p(0,0),p(4,0),p(4,4),p(2,2),p(0,4),p(4,0)),KOMEValidationCode.POLYGON_REPEATED_VERTEX);
        assertCode(KOMEPolygon.of(p(0,0),p(4,4),p(0,4),p(4,0)),KOMEValidationCode.POLYGON_SELF_INTERSECTION);
    }
    @Test public void inclusiveAdminYConvertsToHalfOpenRuntimeRange(){
        KOMEPolygonPrism prism=KOMEPolygonPrism.fromInclusiveBlockHeights(square(0,0,10,10),60,70);
        assertEquals(60,prism.getMinYInclusive());assertEquals(71,prism.getMaxYExclusive());assertEquals(70,prism.getMaxYInclusive());
        assertEquals(KOMEPointClassification.INTERIOR,prism.classify(5,60,5));
        assertEquals(KOMEPointClassification.INTERIOR,prism.classify(5,70,5));
        assertEquals(KOMEPointClassification.OUTSIDE,prism.classify(5,71,5));
        assertEquals(KOMEPointClassification.BOUNDARY,prism.classify(0,65,5));
    }
    @Test public void xzOverlapWithDisjointOrTouchingYIsNotTrue3dOverlap(){
        KOMEPolygon polygon=square(0,0,10,10);
        KOMEPolygonPrism low=new KOMEPolygonPrism(polygon,0,5);
        KOMEPolygonPrism high=new KOMEPolygonPrism(polygon,6,10);
        KOMEPolygonPrism touching=new KOMEPolygonPrism(polygon,5,10);
        assertFalse(KOMEGeometryPredicates.prismsHaveInteriorOverlap(low,high));
        assertFalse(KOMEGeometryPredicates.prismsTouchOrOverlap(low,high));
        assertFalse(KOMEGeometryPredicates.prismsHaveInteriorOverlap(low,touching));
        assertTrue(KOMEGeometryPredicates.prismsTouchOrOverlap(low,touching));
    }
    @Test public void sharedXzEdgeIsContactButNotInteriorOverlap(){
        KOMEPolygon left=square(0,0,10,10),right=square(10,0,20,10);
        assertFalse(KOMEGeometryPredicates.polygonsHaveInteriorOverlap(left,right));
        assertTrue(KOMEGeometryPredicates.polygonsTouchOrOverlap(left,right));
        assertFalse(KOMEGeometryPredicates.prismsHaveInteriorOverlap(new KOMEPolygonPrism(left,0,10),new KOMEPolygonPrism(right,0,10)));
    }
    @Test public void positiveXzAndYIntersectionIsTrue3dOverlap(){
        KOMEPolygonPrism first=new KOMEPolygonPrism(square(0,0,10,10),0,10);
        KOMEPolygonPrism second=new KOMEPolygonPrism(square(5,5,15,15),5,15);
        assertTrue(KOMEGeometryPredicates.prismsHaveInteriorOverlap(first,second));
    }
    @Test public void exactPredicatesHandleCoincidentAndPartialCollinearOverlap(){
        KOMEPolygon first=square(0,0,10,10),same=square(0,0,10,10),partial=square(5,0,15,10);
        assertTrue(KOMEGeometryPredicates.polygonsHaveInteriorOverlap(first,same));
        assertTrue(KOMEGeometryPredicates.polygonsHaveInteriorOverlap(first,partial));
    }
    @Test public void inscribedDiamondHasInteriorOverlapDespiteAllVerticesBeingOnBoundary(){
        KOMEPolygon outer=square(0,0,10,10),diamond=KOMEPolygon.of(p(5,0),p(10,5),p(5,10),p(0,5));
        for(KOMEXZPoint vertex:diamond.getVertices())assertEquals(KOMEPointClassification.BOUNDARY,outer.classify(vertex));
        assertOverlapForAllOrientations(outer,diamond,true);
        assertContainmentForAllOrientations(outer,diamond,true);
        assertFalse(KOMEGeometryPredicates.polygonContainsPolygon(diamond,outer));
    }
    @Test public void rectangleAcrossConcaveNotchIsNotContainedDespiteBoundaryVertices(){
        KOMEPolygon outer=uShape(),bridge=square(2,2,6,6);
        for(KOMEXZPoint vertex:bridge.getVertices())assertEquals(KOMEPointClassification.BOUNDARY,outer.classify(vertex));
        assertContainmentForAllOrientations(outer,bridge,false);
        assertOverlapForAllOrientations(outer,bridge,false);
        assertTrue(KOMEGeometryPredicates.polygonsTouchOrOverlap(outer,bridge));
    }
    @Test public void containmentChecksEveryEdgeIntervalRatherThanOnlyWholeEdgeMidpoints(){
        KOMEPolygon outer=KOMEPolygon.of(p(0,0),p(12,0),p(12,8),p(10,8),p(10,6),p(10,2),p(8,2),
            p(8,6),p(8,8),p(4,8),p(4,6),p(4,2),p(2,2),p(2,6),p(2,8),p(0,8));
        KOMEPolygon bridge=square(2,2,10,6);
        assertTrue(KOMEPolygonValidator.validate(outer,"outer").isValid());
        for(KOMEXZPoint vertex:bridge.getVertices())assertEquals(KOMEPointClassification.BOUNDARY,outer.classify(vertex));
        assertEquals(KOMEPointClassification.INTERIOR,outer.classify(p(6,4)));
        assertContainmentForAllOrientations(outer,bridge,false);
        assertOverlapForAllOrientations(outer,bridge,true);
    }
    @Test public void concaveContainedPolygonAndBoundaryOnlyContactsKeepTheirSemantics(){
        KOMEPolygon outer=uShape(),base=square(0,0,8,2);
        assertContainmentForAllOrientations(outer,base,true);
        assertOverlapForAllOrientations(outer,base,true);
        KOMEPolygon left=square(0,0,10,10),edge=square(10,2,12,8),vertex=square(10,10,12,12);
        assertOverlapForAllOrientations(left,edge,false);
        assertOverlapForAllOrientations(left,vertex,false);
        assertTrue(KOMEGeometryPredicates.polygonsTouchOrOverlap(left,edge));
        assertTrue(KOMEGeometryPredicates.polygonsTouchOrOverlap(left,vertex));
        assertContainmentForAllOrientations(left,left,true);
        assertContainmentForAllOrientations(left,edge,false);
    }
    @Test public void edgeSamplingIsExactForHalfGridPointsAndExtremeIntegerCoordinates(){
        KOMEPolygon small=square(0,0,2,2),smallDiamond=KOMEPolygon.of(p(1,0),p(2,1),p(1,2),p(0,1));
        assertOverlapForAllOrientations(small,smallDiamond,true);
        assertContainmentForAllOrientations(small,smallDiamond,true);
        KOMEPolygon outer=square(Integer.MIN_VALUE,Integer.MIN_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE);
        KOMEPolygon diamond=KOMEPolygon.of(p(0,Integer.MIN_VALUE),p(Integer.MAX_VALUE,0),
            p(0,Integer.MAX_VALUE),p(Integer.MIN_VALUE,0));
        assertOverlapForAllOrientations(outer,diamond,true);
        assertContainmentForAllOrientations(outer,diamond,true);
    }
    @Test public void polygonDefensivelyCopiesItsVertexList(){
        List<KOMEXZPoint> source=new ArrayList<KOMEXZPoint>(Arrays.asList(p(0,0),p(5,0),p(0,5)));
        KOMEPolygon polygon=new KOMEPolygon(source);source.clear();assertEquals(3,polygon.size());
        try{polygon.getVertices().clear();fail("Expected immutable vertices");}catch(UnsupportedOperationException expected){}
    }
    private static void assertCode(KOMEPolygon polygon,KOMEValidationCode code){
        KOMEValidationResult result=KOMEPolygonValidator.validate(polygon,"test");assertFalse(result.isValid());assertTrue(result.hasCode(code));
    }
    private static KOMEXZPoint p(int x,int z){return new KOMEXZPoint(x,z);}
    private static KOMEPolygon uShape(){
        return KOMEPolygon.of(p(0,0),p(8,0),p(8,8),p(6,8),p(6,2),p(2,2),p(2,8),p(0,8));
    }
    private static KOMEPolygon reversed(KOMEPolygon polygon){
        List<KOMEXZPoint> points=new ArrayList<KOMEXZPoint>(polygon.getVertices());
        Collections.reverse(points);return new KOMEPolygon(points);
    }
    private static void assertOverlapForAllOrientations(KOMEPolygon a,KOMEPolygon b,boolean expected){
        for(KOMEPolygon first:Arrays.asList(a,reversed(a)))for(KOMEPolygon second:Arrays.asList(b,reversed(b))){
            assertEquals(expected,KOMEGeometryPredicates.polygonsHaveInteriorOverlap(first,second));
            assertEquals(expected,KOMEGeometryPredicates.polygonsHaveInteriorOverlap(second,first));
        }
    }
    private static void assertContainmentForAllOrientations(KOMEPolygon outer,KOMEPolygon inner,boolean expected){
        for(KOMEPolygon first:Arrays.asList(outer,reversed(outer)))for(KOMEPolygon second:Arrays.asList(inner,reversed(inner)))
            assertEquals(expected,KOMEGeometryPredicates.polygonContainsPolygon(first,second));
    }
    private static KOMEPolygon square(int minX,int minZ,int maxX,int maxZ){
        return KOMEPolygon.of(p(minX,minZ),p(maxX,minZ),p(maxX,maxZ),p(minX,maxZ));
    }
}
