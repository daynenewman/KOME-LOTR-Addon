package kome.common.tactical;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import kome.common.siege.KOMESiegeZone;
import kome.common.siege.geometry.KOMEPolygon;
import kome.common.siege.geometry.KOMEPolygonPrism;
import kome.common.siege.geometry.KOMEXZPoint;
import kome.common.siege.validation.KOMEValidationCode;
import kome.common.siege.validation.KOMEValidationResult;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMEForceDeploymentAreaTest {
    private final KOMEForceDeploymentAreaValidator validator = new KOMEForceDeploymentAreaValidator();

    @Test public void ordinaryTileCanDefineDeploymentGeometryWithoutAnySiegeComplex() {
        KOMEForceDeploymentArea area = area("FIELD", " t277 ", 0, "Field", rectangle(0), 0);
        assertEquals("T277", area.getTileId());
        assertTrue(validator.validate(area).isValid());
        assertFalse(KOMESiegeZone.class.isAssignableFrom(KOMEForceDeploymentArea.class));
        assertSame(Object.class, KOMEForceDeploymentArea.class.getSuperclass());
    }

    @Test public void multipleAreasOnOneTileMayHaveTheSameDisplayLabel() {
        List<KOMEForceDeploymentArea> areas = Arrays.asList(
            area("WEST_FIELD", "T277", 0, "Staging", rectangle(0), 2),
            area("EAST_FIELD", "T277", 0, "Staging", rectangle(100), 7));
        assertEquals(areas.get(0).getTileId(), areas.get(1).getTileId());
        assertNotEquals(areas.get(0).getAreaId(), areas.get(1).getAreaId());
        assertEquals(areas.get(0).getLabel(), areas.get(1).getLabel());
        for (KOMEForceDeploymentArea area : areas) assertTrue(validator.validate(area).isValid());
    }

    @Test public void identityIsCanonicalAndSurvivesLabelGeometryAndRevisionChanges() {
        KOMEForceDeploymentArea original = area(" West-Field ", "T277", 0, " West ", rectangle(0), 3);
        KOMEForceDeploymentArea revised = area("west-field", "T277", 0, "Renamed", rectangle(100), 4);
        assertEquals("WEST-FIELD", original.getAreaId());
        assertEquals(original.getAreaId(), revised.getAreaId());
        assertEquals("West", original.getLabel());
        assertNotEquals(original.getLabel(), revised.getLabel());
        assertNotEquals(original.getPrism(), revised.getPrism());
        assertEquals(3L, original.getRevision());
        assertEquals(4L, revised.getRevision());
    }

    @Test public void identityUsesRootLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            assertEquals("I-FIELD", area(" i-field ", "T277", 0, "", rectangle(0), 0).getAreaId());
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test public void nullEmptyAndWhitespaceOnlyIdentitiesAreRejected() {
        for (String id : Arrays.asList(null, "", " \t\r\n ")) {
            try {
                area(id, "T277", 0, "", rectangle(0), 0);
                fail("Expected a required area identity.");
            } catch (IllegalArgumentException expected) { }
        }
    }

    @Test public void missingOrMalformedTileAssociationIsDiagnosed() {
        for (String tile : Arrays.asList(null, "", " ", "WEST_FIELD", "T?")) {
            KOMEValidationResult result = validator.validate(area("FIELD", tile, 0, "", rectangle(0), 0));
            assertFalse(result.isValid());
            assertTrue(result.hasCode(KOMEValidationCode.INVALID_FORCE_DEPLOYMENT_AREA_TILE_ID));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void missingDimensionMetadataIsRejectedRatherThanDefaultedToZero() {
        new KOMEForceDeploymentArea("FIELD", "T277", null, "", rectangle(0), 0);
    }

    @Test public void signedDimensionMetadataDoesNotRequireALoadedWorld() {
        for (int dimension : new int[] {-1, 0, 100, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            KOMEForceDeploymentArea area = area("FIELD", "T277", dimension, "", rectangle(0), 0);
            assertEquals(dimension, area.getDimensionId());
            assertTrue(validator.validate(area).isValid());
        }
    }

    @Test public void negativeRevisionDiagnosticIdentifiesTheArea() {
        KOMEValidationResult result = validator.validate(area("FIELD", "T277", 0, "", rectangle(0), -1));
        assertFalse(result.isValid());
        assertTrue(result.hasCode(KOMEValidationCode.INVALID_FORCE_DEPLOYMENT_AREA_REVISION));
        assertEquals(Collections.singletonList("FIELD"), result.getIssues().get(0).getSubjectIds());
    }

    @Test public void malformedPolygonAndHeightReceiveExistingGeometryDiagnostics() {
        KOMEPolygon polygon = KOMEPolygon.of(new KOMEXZPoint(0,0), new KOMEXZPoint(10,10),
            new KOMEXZPoint(0,10), new KOMEXZPoint(10,0));
        KOMEPolygonPrism malformed = new KOMEPolygonPrism(polygon, 5, 5);
        KOMEForceDeploymentArea area = area("FIELD", "T277", 0, "", malformed, 0);
        KOMEValidationResult result = validator.validate(area);
        assertSame(malformed, area.getPrism());
        assertFalse(result.isValid());
        assertTrue(result.hasCode(KOMEValidationCode.POLYGON_SELF_INTERSECTION));
        assertTrue(result.hasCode(KOMEValidationCode.PRISM_MALFORMED_Y));
        for (kome.common.siege.validation.KOMEValidationIssue issue : result.getIssues()) {
            assertEquals(Collections.singletonList("FIELD"), issue.getSubjectIds());
        }
    }

    @Test public void unfinishedPolygonRemainsAvailableForDiagnostics() {
        KOMEPolygonPrism unfinished = new KOMEPolygonPrism(new KOMEPolygon(Collections.<KOMEXZPoint>emptyList()), 0, 10);
        KOMEForceDeploymentArea area = area("FIELD", "T277", 0, "", unfinished, 0);
        assertSame(unfinished, area.getPrism());
        assertTrue(validator.validate(area).hasCode(KOMEValidationCode.POLYGON_TOO_FEW_DISTINCT_VERTICES));
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullGeometryIsRejected() {
        area("FIELD", "T277", 0, "", null, 0);
    }

    private static KOMEForceDeploymentArea area(String id, String tile, int dimension,
            String label, KOMEPolygonPrism prism, long revision) {
        return new KOMEForceDeploymentArea(id, tile, dimension, label, prism, revision);
    }

    private static KOMEPolygonPrism rectangle(int offset) {
        return new KOMEPolygonPrism(KOMEPolygon.of(new KOMEXZPoint(offset,0), new KOMEXZPoint(offset+10,0),
            new KOMEXZPoint(offset+10,10), new KOMEXZPoint(offset,10)), 0, 10);
    }
}
