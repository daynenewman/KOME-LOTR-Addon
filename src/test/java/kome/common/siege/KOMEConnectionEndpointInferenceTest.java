package kome.common.siege;

import kome.common.siege.geometry.KOMEPolygon;
import kome.common.siege.geometry.KOMEPolygonPrism;
import kome.common.siege.geometry.KOMEXZPoint;
import kome.common.siege.validation.KOMEValidationCode;
import kome.common.siege.validation.KOMEValidationIssue;
import kome.common.siege.validation.KOMEValidationResult;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import static kome.common.siege.KOMESiegeFixtures.*;
import static org.junit.Assert.*;

public class KOMEConnectionEndpointInferenceTest {
    @Test public void transitionUniquelyBetweenTwoNormalsInfersBoth(){
        KOMENormalSegment a=normal("A",0,0,10,10),b=normal("B",12,0,22,10);KOMETransitionZone t=transition("T",10,2,12,4);
        KOMEConnectionEndpointInference.Result result=KOMEConnectionEndpointInference.infer(
            complex(Arrays.asList(a,b),none(),Arrays.asList(t),none()),"T");
        assertTrue(result.isSuccessful());assertEquals(KOMESiegeAreaRef.normal("A"),result.getEndpointA());assertEquals(KOMESiegeAreaRef.normal("B"),result.getEndpointB());
    }
    @Test public void transitionFromOneNormalIntoExteriorInfersExteriorAndNormal(){
        KOMENormalSegment a=normal("A",0,0,10,10);KOMETransitionZone t=transition("WEST",-2,2,0,4);
        KOMEConnectionEndpointInference.Result result=KOMEConnectionEndpointInference.infer(
            complex(Arrays.asList(a),none(),Arrays.asList(t),none()),"WEST");
        assertTrue(result.isSuccessful());assertEquals(KOMESiegeAreaRef.exterior(),result.getEndpointA());assertEquals(KOMESiegeAreaRef.normal("A"),result.getEndpointB());
    }
    @Test public void transitionWhollyInsideOneNormalDoesNotInventExterior(){
        KOMENormalSegment a=normal("A",0,0,10,10);KOMETransitionZone t=transition("INSIDE",2,2,4,4);
        KOMEConnectionEndpointInference.Result result=KOMEConnectionEndpointInference.infer(
            complex(Arrays.asList(a),none(),Arrays.asList(t),none()),"INSIDE");
        assertEquals(KOMEConnectionEndpointInference.Status.EXTERIOR_NOT_ESTABLISHED,result.getStatus());
    }
    @Test public void zeroAndMoreThanTwoNormalCandidatesAreRejected(){
        KOMETransitionZone isolated=transition("ISO",100,100,102,102);
        KOMESiegeComplex noCandidates=complex(Arrays.asList(normal("A",0,0,10,10)),none(),Arrays.asList(isolated),none());
        assertEquals(KOMEConnectionEndpointInference.Status.NO_ENDPOINT_CANDIDATES,
            KOMEConnectionEndpointInference.infer(noCandidates,"ISO").getStatus());
        KOMENormalSegment a=normal("A",0,0,10,10),b=normal("B",12,0,22,10),c=normal("C",10,4,12,6);
        KOMETransitionZone crowded=transition("T",10,2,12,4);
        KOMESiegeComplex three=complex(Arrays.asList(a,b,c),none(),Arrays.asList(crowded),none());
        assertEquals(KOMEConnectionEndpointInference.Status.TOO_MANY_NORMAL_ENDPOINTS,
            KOMEConnectionEndpointInference.infer(three,"T").getStatus());
    }
    @Test public void inferenceIsDeterministicAcrossHashIterationOrder(){
        KOMENormalSegment a=normal("A",0,0,10,10),b=normal("B",12,0,22,10);KOMETransitionZone t=transition("T",10,2,12,4);
        KOMENormalSegment distant=new KOMENormalSegment("BAD","BAD",prism(100,100,110,110,10,5));
        HashSet<KOMENormalSegment> first=new HashSet<KOMENormalSegment>(Arrays.asList(b,distant,a));
        HashSet<KOMENormalSegment> second=new HashSet<KOMENormalSegment>(Arrays.asList(a,b,distant));
        for(Collection<KOMENormalSegment> normals:Arrays.<Collection<KOMENormalSegment>>asList(
                first,second,Arrays.asList(a,b,distant),Arrays.asList(distant,b,a))){
            KOMEConnectionEndpointInference.Result result=KOMEConnectionEndpointInference.infer(
                complex(normals,none(),Arrays.asList(t),none()),"T");
            assertTrue(result.isSuccessful());
            assertEquals(KOMESiegeAreaRef.normal("A"),result.getEndpointA());
            assertEquals(KOMESiegeAreaRef.normal("B"),result.getEndpointB());
        }
    }
    @Test public void overlappingTransitionCorridorMakesInferenceAmbiguous(){
        KOMENormalSegment a=normal("A",0,0,10,10),b=normal("B",12,0,22,10);
        KOMETransitionZone t1=transition("T1",10,2,12,5),t2=transition("T2",10,4,12,7);
        KOMESiegeComplex complex=complex(Arrays.asList(a,b),none(),Arrays.asList(t1,t2),none());
        assertEquals(KOMEConnectionEndpointInference.Status.AMBIGUOUS_CORRIDOR,
            KOMEConnectionEndpointInference.infer(complex,"T1").getStatus());
    }
    @Test public void missingAndInvalidTransitionReturnExplicitStatuses(){
        KOMENormalSegment a=normal("A",0,0,10,10);
        KOMESiegeComplex missing=complex(Arrays.asList(a),none(),none(),none());
        assertEquals(KOMEConnectionEndpointInference.Status.TRANSITION_NOT_FOUND,
            KOMEConnectionEndpointInference.infer(missing,"NONE").getStatus());
        KOMETransitionZone invalid=new KOMETransitionZone("BAD","BAD",prism(-2,2,0,4,10,10));
        assertEquals(KOMEConnectionEndpointInference.Status.INVALID_TRANSITION_GEOMETRY,
            KOMEConnectionEndpointInference.infer(complex(Arrays.asList(a),none(),Arrays.asList(invalid),none()),"BAD").getStatus());
    }
    @Test public void malformedNormalPolygonIsRejectedBeforeEndpointSelection(){
        KOMEPolygon crossed=KOMEPolygon.of(new KOMEXZPoint(0,0),new KOMEXZPoint(10,10),
            new KOMEXZPoint(0,10),new KOMEXZPoint(10,0));
        KOMENormalSegment invalid=new KOMENormalSegment("A","A",new KOMEPolygonPrism(crossed,0,10));
        assertFailure(KOMEConnectionEndpointInference.Status.INVALID_NORMAL_GEOMETRY,Arrays.asList(invalid),transition("T",-2,2,0,4));
    }
    @Test public void malformedNormalHeightRangeIsRejectedRatherThanUsedOrIgnored(){
        for(KOMEPolygonPrism shape:Arrays.asList(prism(0,0,10,10,10,10),prism(0,0,10,10,10,5))){
            KOMENormalSegment invalid=new KOMENormalSegment("A","A",shape);
            assertFailure(KOMEConnectionEndpointInference.Status.INVALID_NORMAL_GEOMETRY,Arrays.asList(invalid),transition("T",-2,2,0,4));
        }
    }
    @Test public void duplicateNormalCandidatesAreRejectedInEitherInputOrder(){
        KOMENormalSegment left=normal("A",0,0,10,10),right=normal("A",12,0,22,10);
        KOMETransitionZone transition=transition("T",10,2,12,4);
        assertFailure(KOMEConnectionEndpointInference.Status.DUPLICATE_NORMAL_ID,Arrays.asList(left,right),transition);
        assertFailure(KOMEConnectionEndpointInference.Status.DUPLICATE_NORMAL_ID,Arrays.asList(right,left),transition);
        assertFailure(KOMEConnectionEndpointInference.Status.DUPLICATE_NORMAL_ID,Arrays.asList(left,left),transition("T",-2,2,0,4));
    }
    @Test public void candidateIdDuplicatedByDistantNormalIsStillAmbiguous(){
        assertFailure(KOMEConnectionEndpointInference.Status.DUPLICATE_NORMAL_ID,Arrays.asList(normal("A",0,0,10,10),normal("A",100,100,110,110)),
            transition("T",-2,2,0,4));
    }
    @Test public void distantMalformedPolygonAllowsLocalInferenceButRemainsGloballyInvalid(){
        KOMENormalSegment invalid=crossedNormal(100,100,0,10);
        assertLocalInferenceWithGlobalError(invalid,KOMEValidationCode.POLYGON_SELF_INTERSECTION);
    }
    @Test public void distantMalformedYRangeAllowsLocalInferenceButRemainsGloballyInvalid(){
        for(KOMEPolygonPrism shape:Arrays.asList(prism(100,100,110,110,10,5),prism(100,100,110,110,5,5)))
            assertLocalInferenceWithGlobalError(new KOMENormalSegment("BAD","BAD",shape),KOMEValidationCode.PRISM_MALFORMED_Y);
    }
    @Test public void overlappingBoundsRequireValidationEvenWhenPolygonIsMalformed(){
        assertFailure(KOMEConnectionEndpointInference.Status.INVALID_NORMAL_GEOMETRY,
            Arrays.asList(crossedNormal(0,0,0,10)),transition("T",13,9,15,11));
    }
    @Test public void boundaryTouchingXzBoundsRemainPotentiallyRelevant(){
        for(KOMEPolygonPrism shape:Arrays.asList(prism(12,2,20,4,10,5),prism(10,4,12,6,10,5),prism(12,4,20,6,10,5)))
            assertFailure(KOMEConnectionEndpointInference.Status.INVALID_NORMAL_GEOMETRY,
                Arrays.asList(new KOMENormalSegment("BAD","BAD",shape)),transition("T",10,2,12,4));
    }
    @Test public void missingPolygonBoundsAreConservativelyValidated(){
        KOMENormalSegment unbounded=new KOMENormalSegment("BAD","BAD",new KOMEPolygonPrism(KOMEPolygon.of(),0,10));
        assertFalse(unbounded.getPrism().getPolygon().hasBounds());
        assertFailure(KOMEConnectionEndpointInference.Status.INVALID_NORMAL_GEOMETRY,
            Arrays.asList(normal("A",102,0,112,10),unbounded),transition("T",100,2,102,4));
    }
    @Test public void validYSeparationCanExcludeMalformedPolygon(){
        for(KOMENormalSegment invalid:Arrays.asList(crossedNormal(0,0,11,20),crossedNormal(0,0,-10,-1)))
            assertLocalInferenceWithGlobalError(invalid,KOMEValidationCode.POLYGON_SELF_INTERSECTION);
    }
    @Test public void yBoundaryContactDoesNotExcludeMalformedPolygon(){
        for(KOMENormalSegment invalid:Arrays.asList(crossedNormal(0,0,10,20),crossedNormal(0,0,-10,0)))
            assertFailure(KOMEConnectionEndpointInference.Status.INVALID_NORMAL_GEOMETRY,
                Arrays.asList(normal("A",0,0,10,10),invalid),transition("T",10,2,12,4));
    }
    @Test public void malformedYRangeCannotEstablishVerticalSeparation(){
        KOMENormalSegment invalid=new KOMENormalSegment("BAD","BAD",prism(0,0,20,20,20,11));
        assertFailure(KOMEConnectionEndpointInference.Status.INVALID_NORMAL_GEOMETRY,
            Arrays.asList(normal("A",0,0,10,10),invalid),transition("T",10,2,12,4));
    }
    @Test public void transitionAcrossConcaveNotchEstablishesExterior(){
        KOMEPolygon uShape=KOMEPolygon.of(new KOMEXZPoint(0,0),new KOMEXZPoint(8,0),new KOMEXZPoint(8,8),
            new KOMEXZPoint(6,8),new KOMEXZPoint(6,2),new KOMEXZPoint(2,2),new KOMEXZPoint(2,8),new KOMEXZPoint(0,8));
        KOMENormalSegment normal=new KOMENormalSegment("A","A",new KOMEPolygonPrism(uShape,0,10));
        KOMEConnectionEndpointInference.Result result=KOMEConnectionEndpointInference.infer(
            complex(Arrays.asList(normal),none(),Arrays.asList(transition("T",2,2,6,6)),none()),"T");
        assertTrue(result.isSuccessful());assertEquals(KOMESiegeAreaRef.exterior(),result.getEndpointA());
        assertEquals(KOMESiegeAreaRef.normal("A"),result.getEndpointB());
    }
    private static KOMENormalSegment crossedNormal(int x,int z,int minY,int maxY){
        KOMEPolygon crossed=KOMEPolygon.of(new KOMEXZPoint(x,z),new KOMEXZPoint(x+20,z+20),
            new KOMEXZPoint(x,z+20),new KOMEXZPoint(x+20,z));
        return new KOMENormalSegment("BAD","BAD",new KOMEPolygonPrism(crossed,minY,maxY));
    }
    private static void assertLocalInferenceWithGlobalError(KOMENormalSegment invalid,KOMEValidationCode code){
        KOMENormalSegment a=normal("A",0,0,10,10),b=normal("B",12,0,22,10);
        KOMETransitionZone transition=transition("T",10,2,12,4);
        KOMESiegeConnection connection=KOMESiegeConnection.gateLess("ENTRY",KOMESiegeAreaRef.normal("A"),
            KOMESiegeAreaRef.normal("B"),"T");
        KOMESiegeComplex valid=complex(Arrays.asList(a,b),none(),Arrays.asList(transition),Arrays.asList(connection));
        KOMESiegeComplex incomplete=complex(Arrays.asList(a,b,invalid),none(),Arrays.asList(transition),Arrays.asList(connection));
        KOMESiegeComplexValidator validator=new KOMESiegeComplexValidator();
        assertTrue(validator.validate(valid).isValid());
        KOMEConnectionEndpointInference.Result result=KOMEConnectionEndpointInference.infer(incomplete,"T");
        assertTrue(result.isSuccessful());
        assertEquals(KOMESiegeAreaRef.normal("A"),result.getEndpointA());
        assertEquals(KOMESiegeAreaRef.normal("B"),result.getEndpointB());
        KOMEValidationResult diagnostics=validator.validate(incomplete);
        assertFalse(diagnostics.isValid());
        for(KOMEValidationIssue issue:diagnostics.getIssues())
            if(issue.getCode()==code&&issue.getSubjectIds().contains(invalid.getId()))return;
        fail("Expected whole-complex diagnostic "+code+" for "+invalid.getId());
    }
    private static void assertFailure(KOMEConnectionEndpointInference.Status expected,List<KOMENormalSegment> normals,KOMETransitionZone transition){
        KOMEConnectionEndpointInference.Result result=KOMEConnectionEndpointInference.infer(
            complex(normals,none(),Arrays.asList(transition),none()),transition.getId());
        assertEquals(expected,result.getStatus());
        assertFalse(result.isSuccessful());assertNull(result.getEndpointA());assertNull(result.getEndpointB());
    }
}
