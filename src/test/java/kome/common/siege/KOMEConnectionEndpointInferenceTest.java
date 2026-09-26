package kome.common.siege;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import static kome.common.siege.KOMESiegeFixtures.*;
import static org.junit.Assert.*;

public class KOMEConnectionEndpointInferenceTest {
    @Test public void transitionUniquelyBetweenTwoNormalsInfersBoth(){
        KOMENormalSegment a=normal("A",0,0,10,10),b=normal("B",12,0,22,10);KOMETransitionZone t=transition("T",10,2,12,4);
        KOMEConnectionEndpointInference.Result result=KOMEConnectionEndpointInference.infer(
            complex(Arrays.asList(a,b),none(),Arrays.asList(t),none(),none()),"T");
        assertTrue(result.isSuccessful());assertEquals(KOMESiegeAreaRef.normal("A"),result.getEndpointA());assertEquals(KOMESiegeAreaRef.normal("B"),result.getEndpointB());
    }
    @Test public void transitionFromOneNormalIntoExteriorInfersExteriorAndNormal(){
        KOMENormalSegment a=normal("A",0,0,10,10);KOMETransitionZone t=transition("WEST",-2,2,0,4);
        KOMEConnectionEndpointInference.Result result=KOMEConnectionEndpointInference.infer(
            complex(Arrays.asList(a),none(),Arrays.asList(t),none(),none()),"WEST");
        assertTrue(result.isSuccessful());assertEquals(KOMESiegeAreaRef.exterior(),result.getEndpointA());assertEquals(KOMESiegeAreaRef.normal("A"),result.getEndpointB());
    }
    @Test public void transitionWhollyInsideOneNormalDoesNotInventExterior(){
        KOMENormalSegment a=normal("A",0,0,10,10);KOMETransitionZone t=transition("INSIDE",2,2,4,4);
        KOMEConnectionEndpointInference.Result result=KOMEConnectionEndpointInference.infer(
            complex(Arrays.asList(a),none(),Arrays.asList(t),none(),none()),"INSIDE");
        assertEquals(KOMEConnectionEndpointInference.Status.EXTERIOR_NOT_ESTABLISHED,result.getStatus());
    }
    @Test public void zeroAndMoreThanTwoNormalCandidatesAreRejected(){
        KOMETransitionZone isolated=transition("ISO",100,100,102,102);
        KOMESiegeComplex noCandidates=complex(Arrays.asList(normal("A",0,0,10,10)),none(),Arrays.asList(isolated),none(),none());
        assertEquals(KOMEConnectionEndpointInference.Status.NO_ENDPOINT_CANDIDATES,
            KOMEConnectionEndpointInference.infer(noCandidates,"ISO").getStatus());
        KOMENormalSegment a=normal("A",0,0,10,10),b=normal("B",12,0,22,10),c=normal("C",10,4,12,6);
        KOMETransitionZone crowded=transition("T",10,2,12,4);
        KOMESiegeComplex three=complex(Arrays.asList(a,b,c),none(),Arrays.asList(crowded),none(),none());
        assertEquals(KOMEConnectionEndpointInference.Status.TOO_MANY_NORMAL_ENDPOINTS,
            KOMEConnectionEndpointInference.infer(three,"T").getStatus());
    }
    @Test public void inferenceIsDeterministicAcrossHashIterationOrder(){
        KOMENormalSegment a=normal("A",0,0,10,10),b=normal("B",12,0,22,10);KOMETransitionZone t=transition("T",10,2,12,4);
        HashSet<KOMENormalSegment> first=new HashSet<KOMENormalSegment>(Arrays.asList(b,a));
        HashSet<KOMENormalSegment> second=new HashSet<KOMENormalSegment>(Arrays.asList(a,b));
        KOMEConnectionEndpointInference.Result one=KOMEConnectionEndpointInference.infer(complex(first,none(),Arrays.asList(t),none(),none()),"T");
        KOMEConnectionEndpointInference.Result two=KOMEConnectionEndpointInference.infer(complex(second,none(),Arrays.asList(t),none(),none()),"T");
        assertEquals(one.getEndpointA(),two.getEndpointA());assertEquals(one.getEndpointB(),two.getEndpointB());
    }
    @Test public void overlappingTransitionCorridorMakesInferenceAmbiguous(){
        KOMENormalSegment a=normal("A",0,0,10,10),b=normal("B",12,0,22,10);
        KOMETransitionZone t1=transition("T1",10,2,12,5),t2=transition("T2",10,4,12,7);
        KOMESiegeComplex complex=complex(Arrays.asList(a,b),none(),Arrays.asList(t1,t2),none(),none());
        assertEquals(KOMEConnectionEndpointInference.Status.AMBIGUOUS_CORRIDOR,
            KOMEConnectionEndpointInference.infer(complex,"T1").getStatus());
    }
    @Test public void missingAndInvalidTransitionReturnExplicitStatuses(){
        KOMENormalSegment a=normal("A",0,0,10,10);
        KOMESiegeComplex missing=complex(Arrays.asList(a),none(),none(),none(),none());
        assertEquals(KOMEConnectionEndpointInference.Status.TRANSITION_NOT_FOUND,
            KOMEConnectionEndpointInference.infer(missing,"NONE").getStatus());
        KOMETransitionZone invalid=new KOMETransitionZone("BAD","BAD",prism(-2,2,0,4,10,10));
        assertEquals(KOMEConnectionEndpointInference.Status.INVALID_TRANSITION_GEOMETRY,
            KOMEConnectionEndpointInference.infer(complex(Arrays.asList(a),none(),Arrays.asList(invalid),none(),none()),"BAD").getStatus());
    }
}
