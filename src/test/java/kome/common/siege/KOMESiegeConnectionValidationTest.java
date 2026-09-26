package kome.common.siege;

import kome.common.siege.validation.KOMEValidationCode;
import kome.common.siege.validation.KOMEValidationResult;
import org.junit.Test;
import java.util.Arrays;
import static kome.common.siege.KOMESiegeFixtures.*;
import static org.junit.Assert.*;

public class KOMESiegeConnectionValidationTest {
    private final KOMESiegeComplexValidator validator=new KOMESiegeComplexValidator();
    @Test public void normalToNormalConnectionThroughItsTwoEndpointsIsValid(){assertTrue(validator.validate(validAB()).isValid());}
    @Test public void exteriorToNormalConnectionIsValid(){
        KOMENormalSegment a=normal("A",0,0,10,10);KOMETransitionZone west=transition("WEST",-2,2,0,4);
        KOMESiegeConnection c=KOMESiegeConnection.gateLess("WEST_ENTRY",KOMESiegeAreaRef.exterior(),KOMESiegeAreaRef.normal("A"),"WEST");
        assertTrue(validator.validate(complex(Arrays.asList(a),none(),Arrays.asList(west),none(),Arrays.asList(c))).isValid());
    }
    @Test public void sameEndpointAndExteriorToExteriorAreRejected(){
        KOMENormalSegment a=normal("A",0,0,10,10);KOMETransitionZone t=transition("T",-2,2,0,4);
        KOMESiegeConnection same=KOMESiegeConnection.gateLess("SAME",KOMESiegeAreaRef.normal("A"),KOMESiegeAreaRef.normal("A"),"T");
        assertCode(complex(Arrays.asList(a),none(),Arrays.asList(t),none(),Arrays.asList(same)),KOMEValidationCode.CONNECTION_SAME_ENDPOINT);
        KOMESiegeConnection outside=KOMESiegeConnection.gateLess("OUT",KOMESiegeAreaRef.exterior(),KOMESiegeAreaRef.exterior(),"T");
        assertCode(complex(Arrays.asList(a),none(),Arrays.asList(t),none(),Arrays.asList(outside)),KOMEValidationCode.CONNECTION_EXTERIOR_EXTERIOR);
    }
    @Test public void wallAndTransitionIdsCannotMasqueradeAsNormalEndpoints(){
        KOMENormalSegment a=normal("A",0,0,10,10);KOMEWallZone wall=wall("W",prism(0,10,10,12,0,10),"A");
        KOMETransitionZone t=transition("T",10,2,12,4);
        KOMESiegeConnection wallEnd=KOMESiegeConnection.gateLess("CW",KOMESiegeAreaRef.normal("A"),KOMESiegeAreaRef.normal("W"),"T");
        assertCode(complex(Arrays.asList(a),Arrays.asList(wall),Arrays.asList(t),none(),Arrays.asList(wallEnd)),KOMEValidationCode.CONNECTION_ENDPOINT_NOT_NORMAL);
        KOMESiegeConnection transitionEnd=KOMESiegeConnection.gateLess("CT",KOMESiegeAreaRef.normal("A"),KOMESiegeAreaRef.normal("T"),"T");
        assertCode(complex(Arrays.asList(a),Arrays.asList(wall),Arrays.asList(t),none(),Arrays.asList(transitionEnd)),KOMEValidationCode.CONNECTION_ENDPOINT_NOT_NORMAL);
    }
    @Test public void exteriorDeploymentAreaCannotMasqueradeAsConnectionEndpoint(){
        KOMENormalSegment a=normal("A",0,0,10,10);KOMETransitionZone t=transition("T",-2,2,0,4);
        KOMEExteriorDeploymentArea area=new KOMEExteriorDeploymentArea("DEPLOY","DEPLOY",prism(-10,0,-5,5,0,10));
        KOMESiegeConnection invalid=KOMESiegeConnection.gateLess("C",KOMESiegeAreaRef.normal("A"),KOMESiegeAreaRef.normal("DEPLOY"),"T");
        assertCode(complex(Arrays.asList(a),none(),Arrays.asList(t),Arrays.asList(area),Arrays.asList(invalid)),KOMEValidationCode.CONNECTION_ENDPOINT_NOT_NORMAL);
    }
    @Test public void missingTransitionAndBrokenNormalEndpointAreRejected(){
        KOMENormalSegment a=normal("A",0,0,10,10);
        KOMESiegeConnection missingTransition=KOMESiegeConnection.gateLess("C",KOMESiegeAreaRef.normal("A"),KOMESiegeAreaRef.exterior(),"MISSING");
        assertCode(complex(Arrays.asList(a),none(),none(),none(),Arrays.asList(missingTransition)),KOMEValidationCode.CONNECTION_TRANSITION_UNKNOWN);
        KOMETransitionZone t=transition("T",-2,2,0,4);
        KOMESiegeConnection broken=KOMESiegeConnection.gateLess("B",KOMESiegeAreaRef.normal("MISSING"),KOMESiegeAreaRef.exterior(),"T");
        assertCode(complex(Arrays.asList(a),none(),Arrays.asList(t),none(),Arrays.asList(broken)),KOMEValidationCode.CONNECTION_ENDPOINT_UNKNOWN);
    }
    @Test public void transitionTouchingThirdNormalIsRejected(){
        KOMENormalSegment a=normal("A",0,0,10,10),b=normal("B",12,0,22,10),c=normal("C",10,4,12,6);
        KOMETransitionZone t=transition("T",10,2,12,4);
        KOMESiegeConnection edge=KOMESiegeConnection.gateLess("AB",KOMESiegeAreaRef.normal("A"),KOMESiegeAreaRef.normal("B"),"T");
        assertCode(complex(Arrays.asList(a,b,c),none(),Arrays.asList(t),none(),Arrays.asList(edge)),KOMEValidationCode.CONNECTION_TRANSITION_THIRD_NORMAL);
    }
    @Test public void oneTransitionCannotBelongToMultipleConnections(){
        KOMESiegeComplex base=validAB();KOMESiegeConnection first=base.getConnections().get(0);
        KOMESiegeConnection second=KOMESiegeConnection.gateLess("AB2",first.getEndpointA(),first.getEndpointB(),first.getTransitionZoneId());
        KOMESiegeComplex reused=complex(base.getNormalSegments(),none(),base.getTransitionZones(),none(),Arrays.asList(first,second));
        assertCode(reused,KOMEValidationCode.CONNECTION_TRANSITION_REUSED);
    }
    @Test public void distinctParallelConnectionsBetweenSameSegmentsAreValid(){
        KOMENormalSegment a=normal("A",0,0,10,10),b=normal("B",12,0,22,10);
        KOMETransitionZone north=transition("NORTH",10,1,12,3),south=transition("SOUTH",10,7,12,9);
        KOMESiegeConnection n=KOMESiegeConnection.gateLess("CN",KOMESiegeAreaRef.normal("A"),KOMESiegeAreaRef.normal("B"),"NORTH");
        KOMESiegeConnection s=KOMESiegeConnection.gateLess("CS",KOMESiegeAreaRef.normal("B"),KOMESiegeAreaRef.normal("A"),"SOUTH");
        assertTrue(validator.validate(complex(Arrays.asList(a,b),none(),Arrays.asList(north,south),none(),Arrays.asList(n,s))).isValid());
    }
    @Test public void duplicateGeometricCorridorIsRejected(){
        KOMENormalSegment a=normal("A",0,0,10,10),b=normal("B",12,0,22,10);
        KOMETransitionZone first=transition("T1",10,2,12,4),second=transition("T2",10,2,12,4);
        KOMESiegeConnection c1=KOMESiegeConnection.gateLess("C1",KOMESiegeAreaRef.normal("A"),KOMESiegeAreaRef.normal("B"),"T1");
        KOMESiegeConnection c2=KOMESiegeConnection.gateLess("C2",KOMESiegeAreaRef.normal("A"),KOMESiegeAreaRef.normal("B"),"T2");
        assertCode(complex(Arrays.asList(a,b),none(),Arrays.asList(first,second),none(),Arrays.asList(c1,c2)),KOMEValidationCode.CONNECTION_DUPLICATE_CORRIDOR);
    }
    @Test public void gateLessAndLogicalGatedConnectionsKeepOnlyTheExpectedReference(){
        KOMESiegeConnection gateLess=validAB().getConnections().get(0);assertFalse(gateLess.isGated());assertFalse(gateLess.getGateRef().isPresent());
        KOMEDefensiveGateRef gate=new KOMEDefensiveGateRef("BUILD-1","G1");
        KOMESiegeConnection gated=KOMESiegeConnection.gated("G",gateLess.getEndpointA(),gateLess.getEndpointB(),"T",gate);
        assertTrue(gated.isGated());assertEquals(gate,gated.getGateRef().get());
        assertEquals(gate,new KOMEDefensiveGateRef("BUILD-1","G1"));
        assertNotEquals(gate,new KOMEDefensiveGateRef("BUILD-2","G1"));
        assertNotEquals(gate,new KOMEDefensiveGateRef("BUILD-1","G2"));
    }
    @Test public void duplicateConnectionIdsAreRejected(){
        KOMESiegeComplex base=validAB();KOMESiegeConnection first=base.getConnections().get(0);
        KOMETransitionZone south=transition("SOUTH",10,7,12,9);
        KOMESiegeConnection duplicate=KOMESiegeConnection.gateLess(first.getId(),first.getEndpointA(),first.getEndpointB(),"SOUTH");
        assertCode(complex(base.getNormalSegments(),none(),Arrays.asList(base.getTransitionZones().get(0),south),none(),Arrays.asList(first,duplicate)),KOMEValidationCode.DUPLICATE_CONNECTION_ID);
    }
    @Test public void multipleEntrancesAndBranchingTopologyAreAccepted(){
        KOMENormalSegment a=normal("A",0,0,10,10),b=normal("B",12,0,22,10),c=normal("C",24,0,34,10);
        KOMETransitionZone west=transition("WEST",-2,2,0,4),ab=transition("AB",10,2,12,4),bc=transition("BC",22,2,24,4),east=transition("EAST",34,6,36,8);
        KOMESiegeConnection c1=KOMESiegeConnection.gateLess("WEST_IN",KOMESiegeAreaRef.exterior(),KOMESiegeAreaRef.normal("A"),"WEST");
        KOMESiegeConnection c2=KOMESiegeConnection.gateLess("AB_C",KOMESiegeAreaRef.normal("A"),KOMESiegeAreaRef.normal("B"),"AB");
        KOMESiegeConnection c3=KOMESiegeConnection.gateLess("BC_C",KOMESiegeAreaRef.normal("B"),KOMESiegeAreaRef.normal("C"),"BC");
        KOMESiegeConnection c4=KOMESiegeConnection.gateLess("EAST_IN",KOMESiegeAreaRef.normal("C"),KOMESiegeAreaRef.exterior(),"EAST");
        KOMESiegeComplex result=complex(Arrays.asList(a,b,c),none(),Arrays.asList(west,ab,bc,east),none(),Arrays.asList(c1,c2,c3,c4));
        assertTrue(validator.validate(result).isValid());assertEquals(Arrays.asList("A","C"),Arrays.asList(result.getAdjacentNormalSegmentIds("B").toArray(new String[0])));
    }
    private KOMESiegeComplex validAB(){
        KOMENormalSegment a=normal("A",0,0,10,10),b=normal("B",12,0,22,10);KOMETransitionZone t=transition("T",10,2,12,4);
        KOMESiegeConnection c=KOMESiegeConnection.gateLess("AB",KOMESiegeAreaRef.normal("A"),KOMESiegeAreaRef.normal("B"),"T");
        return complex(Arrays.asList(a,b),none(),Arrays.asList(t),none(),Arrays.asList(c));
    }
    private void assertCode(KOMESiegeComplex complex,KOMEValidationCode code){
        KOMEValidationResult result=validator.validate(complex);assertFalse(result.isValid());assertTrue(result.hasCode(code));
    }
}
