package kome.common.siege;

import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import kome.common.siege.validation.KOMEValidationCode;
import kome.common.siege.validation.KOMEValidationResult;
import kome.common.tactical.KOMEForceDeploymentArea;
import kome.common.tactical.KOMEForceDeploymentAreaValidator;
import org.junit.Test;
import static kome.common.siege.KOMESiegeFixtures.*;
import static org.junit.Assert.*;

public class KOMEPreferredForceDeploymentAreaTest {
    private final KOMESiegeComplexValidator validator = new KOMESiegeComplexValidator();
    private final KOMEForceDeploymentAreaValidator areaValidator = new KOMEForceDeploymentAreaValidator();

    @Test public void noPreferredAreaIsValidAndDoesNotRequireAResolvedArea() {
        KOMESiegeComplex complex = definition("WEST", null, 0, 3, "B", "B1");
        assertFalse(complex.getPreferredForceDeploymentAreaId().isPresent());
        assertTrue(validator.validate(complex).isValid());
        assertTrue(validator.validatePreferredForceDeploymentArea(complex, null).isValid());
        assertTrue(validator.validatePreferredForceDeploymentArea(complex, area("UNSELECTED", "T999", 1)).isValid());
    }

    @Test public void preferredReferenceIsCanonicalAndResolvesOnItsOwnTileAndDimension() {
        KOMESiegeComplex complex = definition("WEST", " west-field ", 0, 3, "B", "B1");
        KOMEForceDeploymentArea area = area("West-Field", " t277 ", 0);
        assertEquals("WEST-FIELD", complex.getPreferredForceDeploymentAreaId().get());
        assertTrue(validator.validatePreferredForceDeploymentArea(complex, area).isValid());
    }

    @Test public void preferredReferenceUsesTheSameRootLocaleIdentityPolicy() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            KOMESiegeComplex complex = definition("WEST", " i-field ", 0, 3, "B", "B1");
            assertEquals("I-FIELD", complex.getPreferredForceDeploymentAreaId().get());
            assertTrue(validator.validatePreferredForceDeploymentArea(complex, area("I-FIELD", "T277", 0)).isValid());
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test public void blankExplicitReferenceIsRejectedInsteadOfBecomingFallback() {
        for (String id : Arrays.asList("", " \t ")) {
            try {
                definition("WEST", id, 0, 3, "B", "B1");
                fail("Expected an explicit area identity.");
            } catch (IllegalArgumentException expected) { }
        }
    }

    @Test public void explicitlyMissingAreaIsDiagnosed() {
        KOMESiegeComplex complex = definition("WEST", "FIELD", 0, 3, "B", "B1");
        KOMEValidationResult result = validator.validatePreferredForceDeploymentArea(complex, null);
        assertError(result, KOMEValidationCode.PREFERRED_DEPLOYMENT_AREA_UNKNOWN);
        assertEquals(Arrays.asList("FIELD", "WEST"), result.getIssues().get(0).getSubjectIds());
    }

    @Test public void aDifferentSuppliedAreaCannotSatisfyTheReference() {
        KOMESiegeComplex complex = definition("WEST", "FIELD", 0, 3, "B", "B1");
        assertError(validator.validatePreferredForceDeploymentArea(complex, area("OTHER", "T277", 0)),
            KOMEValidationCode.PREFERRED_DEPLOYMENT_AREA_UNKNOWN);
    }

    @Test public void crossTilePreferredAreaIsRejected() {
        KOMESiegeComplex complex = definition("WEST", "FIELD", 0, 3, "B", "B1");
        assertError(validator.validatePreferredForceDeploymentArea(complex, area("FIELD", "T278", 0)),
            KOMEValidationCode.PREFERRED_DEPLOYMENT_AREA_WRONG_TILE);
    }

    @Test public void wrongDimensionPreferredAreaIsRejected() {
        KOMESiegeComplex complex = definition("WEST", "FIELD", 0, 3, "B", "B1");
        assertError(validator.validatePreferredForceDeploymentArea(complex, area("FIELD", "T277", -1)),
            KOMEValidationCode.PREFERRED_DEPLOYMENT_AREA_WRONG_DIMENSION);
    }

    @Test public void bothTileAndDimensionErrorsAreReported() {
        KOMESiegeComplex complex = definition("WEST", "FIELD", 0, 3, "B", "B1");
        KOMEValidationResult result = validator.validatePreferredForceDeploymentArea(complex, area("FIELD", "T278", -1));
        assertError(result, KOMEValidationCode.PREFERRED_DEPLOYMENT_AREA_WRONG_TILE);
        assertError(result, KOMEValidationCode.PREFERRED_DEPLOYMENT_AREA_WRONG_DIMENSION);
        assertEquals(2, result.getIssues().size());
    }

    @Test public void sameTileComplexesCanPreferDifferentAreas() {
        KOMESiegeComplex west = definition("WEST", "WEST_FIELD", 0, 3, "B", "B1");
        KOMESiegeComplex east = definition("EAST", "EAST_FIELD", 100, 7, "C", "B2");
        assertEquals(west.getTileId(), east.getTileId());
        assertNotEquals(west.getPreferredForceDeploymentAreaId(), east.getPreferredForceDeploymentAreaId());
        assertTrue(validator.validatePreferredForceDeploymentArea(west, area("WEST_FIELD", "T277", 0)).isValid());
        assertTrue(validator.validatePreferredForceDeploymentArea(east, area("EAST_FIELD", "T277", 0)).isValid());
    }

    @Test public void sharedTileAreaDoesNotJoinGraphsGeometryGateReferencesOrRevisions() {
        KOMEForceDeploymentArea shared = area("FIELD", "T277", 0);
        KOMESiegeComplex west = definition("WEST", "FIELD", 0, 3, "B", "B1");
        KOMESiegeComplex east = definition("EAST", "FIELD", 100, 7, "C", "B2");
        assertTrue(validator.validatePreferredForceDeploymentArea(west, shared).isValid());
        assertTrue(validator.validatePreferredForceDeploymentArea(east, shared).isValid());
        assertTrue(validator.validate(west).isValid());
        assertTrue(validator.validate(east).isValid());
        assertEquals(Collections.singleton("B"), west.getAdjacentNormalSegmentIds("A"));
        assertEquals(Collections.singleton("C"), east.getAdjacentNormalSegmentIds("A"));
        assertNull(west.findNormalSegment("C"));
        assertNull(east.findNormalSegment("B"));
        assertNull(west.findNormalSegment(shared.getAreaId()));
        assertNotSame(west.findNormalSegment("A"), east.findNormalSegment("A"));
        assertNotSame(shared.getPrism(), west.findNormalSegment("A").getPrism());
        assertSame(west.getConnections().get(0), west.getConnectionsFor(KOMESiegeAreaRef.exterior()).get(0));
        assertSame(east.getConnections().get(0), east.getConnectionsFor(KOMESiegeAreaRef.exterior()).get(0));
        assertEquals("B1", west.getConnections().get(0).getGateRef().get().getBuildId());
        assertEquals("B2", east.getConnections().get(0).getGateRef().get().getBuildId());
        assertEquals("G1", west.getConnections().get(0).getGateRef().get().getGateRecordId());
        assertEquals("G1", east.getConnections().get(0).getGateRef().get().getGateRecordId());
        assertNotSame(west.getConnections().get(0), east.getConnections().get(0));

        KOMESiegeComplex revisedWest = definition("WEST", "FIELD", 0, 4, "B", "B1");
        KOMEForceDeploymentArea revisedArea = new KOMEForceDeploymentArea("FIELD", "T277", 0,
            "Renamed", prism(-40,0,-30,10,0,10), 10);
        assertEquals(west.getComplexId(), revisedWest.getComplexId());
        assertEquals(shared.getAreaId(), revisedArea.getAreaId());
        assertEquals(3L, west.getRevision());
        assertEquals(4L, revisedWest.getRevision());
        assertEquals(7L, east.getRevision());
        assertEquals(9L, shared.getRevision());
        assertEquals(10L, revisedArea.getRevision());
    }

    @Test public void exteriorRemainsConceptualEvenWhenAnAreaHasThatName() {
        KOMEForceDeploymentArea area = area("EXTERIOR", "T277", 0);
        KOMESiegeComplex complex = definition("WEST", "EXTERIOR", 0, 3, "B", "B1");
        assertTrue(validator.validatePreferredForceDeploymentArea(complex, area).isValid());
        assertNull(complex.findNormalSegment("EXTERIOR"));
        assertNull(complex.findTransitionZone("EXTERIOR"));
        assertFalse(complex.getStrongholdZoneIds().contains("EXTERIOR"));
        assertEquals("", KOMESiegeAreaRef.exterior().getNormalSegmentId());
        KOMEConnectionEndpointInference.Result inferred = KOMEConnectionEndpointInference.infer(complex, "GATE");
        assertTrue(inferred.isSuccessful());
        assertSame(KOMESiegeAreaRef.exterior(), inferred.getEndpointA());
        assertEquals(KOMESiegeAreaRef.normal("A"), inferred.getEndpointB());
    }

    @Test public void preferredAreaGeometryIsValidatedSeparatelyWithoutFortressOverlapPolicy() {
        KOMESiegeComplex complex = definition("WEST", "FIELD", 0, 3, "B", "B1");
        KOMEForceDeploymentArea overlapping = new KOMEForceDeploymentArea("FIELD", "T277", 0,
            "", prism(0,0,10,10,0,10), 0);
        assertTrue(areaValidator.validate(overlapping).isValid());
        assertTrue(validator.validate(complex).isValid());
        assertTrue(validator.validatePreferredForceDeploymentArea(complex, overlapping).isValid());
        KOMEForceDeploymentArea malformed = new KOMEForceDeploymentArea("FIELD", "T277", 0,
            "", prism(0,0,10,10,5,5), 0);
        assertTrue(validator.validatePreferredForceDeploymentArea(complex, malformed).isValid());
        assertError(areaValidator.validate(malformed), KOMEValidationCode.PRISM_MALFORMED_Y);
    }

    private static KOMEForceDeploymentArea area(String id, String tile, int dimension) {
        return new KOMEForceDeploymentArea(id, tile, dimension, "Staging", prism(-20,0,-10,10,0,10), 9);
    }

    private static KOMESiegeComplex definition(String id, String preferredAreaId, int offset,
            long revision, String innerId, String buildId) {
        KOMENormalSegment outer = normal("A", offset, 0, offset+10, 10);
        KOMENormalSegment inner = normal(innerId, offset+12, 0, offset+22, 10);
        KOMETransitionZone entry = transition("GATE", offset-2, 2, offset, 4);
        KOMETransitionZone internal = transition("INTERNAL", offset+10, 6, offset+12, 8);
        KOMESiegeConnection entrance = KOMESiegeConnection.gated("ENTRY", KOMESiegeAreaRef.exterior(),
            KOMESiegeAreaRef.normal("A"), "GATE", new KOMEDefensiveGateRef(buildId, "G1"));
        KOMESiegeConnection link = KOMESiegeConnection.gateLess("LINK", KOMESiegeAreaRef.normal("A"),
            KOMESiegeAreaRef.normal(innerId), "INTERNAL");
        return new KOMESiegeComplex(id, "T277", 0, revision, Arrays.asList(outer, inner), none(),
            Arrays.asList(entry, internal), preferredAreaId, Arrays.asList(entrance, link));
    }

    private static void assertError(KOMEValidationResult result, KOMEValidationCode code) {
        assertFalse(result.isValid());
        assertTrue(result.hasCode(code));
    }
}
