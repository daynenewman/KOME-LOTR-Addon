package kome.common.siege;

import kome.common.siege.geometry.KOMEPolygonPrism;
import kome.common.siege.validation.KOMEValidationCode;
import kome.common.siege.validation.KOMEValidationResult;
import kome.common.tactical.KOMEForceDeploymentArea;
import org.junit.Test;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import static kome.common.siege.KOMESiegeFixtures.*;
import static org.junit.Assert.*;

public class KOMESiegeZoneValidationTest {
    private final KOMESiegeComplexValidator validator=new KOMESiegeComplexValidator();
    @Test public void normalNormalTrueOverlapIsRejected(){
        KOMESiegeComplex c=complex(Arrays.asList(normal("A",0,0,10,10),normal("B",5,5,15,15)),none(),none(),none());
        assertCode(c,KOMEValidationCode.NORMAL_NORMAL_OVERLAP);
    }
    @Test public void normalWallTrueOverlapIsRejected(){
        KOMENormalSegment a=normal("A",0,0,10,10);KOMEWallZone wall=wall("W",prism(5,5,15,15,0,10),"A");
        assertCode(complex(Arrays.asList(a),Arrays.asList(wall),none(),none()),KOMEValidationCode.NORMAL_WALL_OVERLAP);
    }
    @Test public void wallWallTrueOverlapIsRejected(){
        KOMENormalSegment a=normal("A",0,0,10,10);
        KOMEWallZone first=wall("W1",prism(0,10,10,14,0,10),"A"),second=wall("W2",prism(5,11,15,15,0,10),"A");
        assertCode(complex(Arrays.asList(a),Arrays.asList(first,second),none(),none()),KOMEValidationCode.WALL_WALL_OVERLAP);
    }
    @Test public void normalWallBoundaryTouchingIsAccepted(){
        KOMENormalSegment a=normal("A",0,0,10,10);KOMEWallZone wall=wall("W",prism(10,0,12,10,0,10),"A");
        assertTrue(validator.validate(complex(Arrays.asList(a),Arrays.asList(wall),none(),none())).isValid());
    }
    @Test public void verticallyStackedNormalAndWallAreAccepted(){
        KOMENormalSegment a=normal("A",0,0,10,10);KOMEWallZone wall=wall("W",prism(0,0,10,10,10,20),"A");
        assertTrue(validator.validate(complex(Arrays.asList(a),Arrays.asList(wall),none(),none())).isValid());
    }
    @Test public void wallAllowsOneOrMultipleLegitimateNormalSources(){
        KOMENormalSegment a=normal("A",0,0,10,10),c=normal("C",20,0,30,10);
        KOMEWallZone one=wall("W1",prism(0,10,10,12,0,10),"A");
        KOMEWallZone many=wall("W2",prism(20,10,30,12,0,10),"C","A","A");
        KOMESiegeComplex complex=complex(Arrays.asList(a,c),Arrays.asList(one,many),none(),none());
        assertTrue(validator.validate(complex).isValid());
        assertEquals(Arrays.asList("A","C"),Arrays.asList(many.getAccessibleFromNormalSegmentIds().toArray(new String[0])));
    }
    @Test public void missingWallAccessReferenceIsRejected(){
        KOMEWallZone wall=wall("W",prism(0,10,10,12,0,10),"MISSING");
        assertCode(complex(Arrays.asList(normal("A",0,0,10,10)),Arrays.asList(wall),none(),none()),
            KOMEValidationCode.WALL_ACCESS_UNKNOWN_NORMAL);
    }
    @Test public void wallRequiresAtLeastOneStaticAccessSource(){
        KOMEWallZone wall=wall("W",prism(0,10,10,12,0,10));
        assertCode(complex(Arrays.asList(normal("A",0,0,10,10)),Arrays.asList(wall),none(),none()),KOMEValidationCode.WALL_ACCESS_EMPTY);
    }
    @Test public void wallMultiAccessDoesNotCreateNormalAdjacency(){
        KOMENormalSegment a=normal("A",0,0,10,10),c=normal("C",20,0,30,10);
        KOMEWallZone wall=wall("W",prism(0,10,10,12,0,10),"A","C");
        KOMESiegeComplex complex=complex(Arrays.asList(a,c),Arrays.asList(wall),none(),none());
        assertTrue(complex.getAdjacentNormalSegmentIds("A").isEmpty());
        assertTrue(complex.getAdjacentNormalSegmentIds("C").isEmpty());
    }
    @Test public void duplicateZoneIdsAcrossTypesAreRejected(){
        KOMENormalSegment a=normal("DUP",0,0,10,10);KOMEWallZone wall=wall("DUP",prism(10,0,12,10,0,10),"DUP");
        assertCode(complex(Arrays.asList(a),Arrays.asList(wall),none(),none()),KOMEValidationCode.DUPLICATE_ZONE_ID);
    }
    @Test public void malformedYAndTransitionWallOverlapAreExplicitErrors(){
        KOMENormalSegment a=normal("A",0,0,10,10);KOMEWallZone wall=wall("W",prism(10,0,14,10,0,10),"A");
        KOMETransitionZone transition=transition("T",12,2,16,4);
        KOMESiegeConnection connection=KOMESiegeConnection.gateLess("C",KOMESiegeAreaRef.normal("A"),KOMESiegeAreaRef.exterior(),"T");
        KOMESiegeComplex overlap=complex(Arrays.asList(a),Arrays.asList(wall),Arrays.asList(transition),Arrays.asList(connection));
        assertCode(overlap,KOMEValidationCode.TRANSITION_WALL_OVERLAP);
        KOMENormalSegment bad=new KOMENormalSegment("BAD","BAD",prism(20,0,30,10,5,5));
        assertCode(complex(Arrays.asList(bad),none(),none(),none()),KOMEValidationCode.PRISM_MALFORMED_Y);
    }
    @Test public void strongholdFootprintIncludesOnlyNormalsAndWalls(){
        KOMENormalSegment a=normal("A",0,0,10,10);KOMEWallZone wall=wall("W",prism(10,0,12,10,0,10),"A");
        KOMETransitionZone transition=transition("T",12,2,14,4);
        KOMESiegeConnection connection=KOMESiegeConnection.gateLess("C",KOMESiegeAreaRef.normal("A"),KOMESiegeAreaRef.exterior(),"T");
        KOMESiegeComplex complex=complex(Arrays.asList(a),Arrays.asList(wall),Arrays.asList(transition),Arrays.asList(connection));
        assertEquals(Arrays.asList("A","W"),Arrays.asList(complex.getStrongholdZoneIds().toArray(new String[0])));
    }
    @Test public void topologyContainsNoLiveGateStateHealthOrPhysicalIdentity(){
        for(Class<?> type:Arrays.<Class<?>>asList(KOMESiegeConnection.class,KOMEDefensiveGateRef.class,KOMEForceDeploymentArea.class))for(Field field:type.getDeclaredFields()){
            String name=field.getName().toLowerCase();assertFalse(name.contains("health"));assertFalse(name.contains("uuid"));
            assertFalse(name.contains("gatestate"));assertFalse(name.contains("breach"));assertFalse(name.contains("controller"));
        }
    }
    private void assertCode(KOMESiegeComplex complex,KOMEValidationCode code){
        KOMEValidationResult result=validator.validate(complex);assertFalse(result.isValid());assertTrue(result.hasCode(code));
    }
}
