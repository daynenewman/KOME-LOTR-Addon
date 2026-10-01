package kome.common.siege;

import kome.common.siege.validation.KOMEValidationCode;
import kome.common.siege.validation.KOMEValidationIssue;
import kome.common.siege.validation.KOMEValidationResult;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static kome.common.siege.KOMESiegeFixtures.*;
import static org.junit.Assert.*;

public class KOMESiegeComplexIdentityTest {
    private final KOMESiegeComplexValidator validator = new KOMESiegeComplexValidator();

    @Test public void complexIdentityIsTrimmedAndCaseInsensitive() {
        KOMESiegeComplex mixed = definition("  West-Fort  ", 0, 0, "B");
        KOMESiegeComplex lower = definition("west-fort", 0, 0, "B");

        assertEquals("WEST-FORT", mixed.getComplexId());
        assertEquals("WEST-FORT", lower.getComplexId());
        assertEquals(mixed.getComplexId(), lower.getComplexId());
        assertEquals("T277", mixed.getTileId());
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullComplexIdentityIsRejected() {
        definition(null, 0, 0, "B");
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyComplexIdentityIsRejected() {
        definition("", 0, 0, "B");
    }

    @Test(expected = IllegalArgumentException.class)
    public void whitespaceOnlyComplexIdentityIsRejected() {
        definition(" \t\r\n ", 0, 0, "B");
    }

    @Test public void sameTileAllowsDistinctComplexesWithRepeatedLocalIds() {
        KOMESiegeComplex west = definition("WEST", 3, 0, "B");
        KOMESiegeComplex east = definition("EAST", 7, 100, "C");

        assertEquals(west.getTileId(), east.getTileId());
        assertNotEquals(west.getComplexId(), east.getComplexId());
        assertEquals(west.findNormalSegment("A").getId(), east.findNormalSegment("A").getId());
        assertEquals(west.findTransitionZone("GATE").getId(), east.findTransitionZone("GATE").getId());
        assertEquals("ENTRY", west.getConnections().get(0).getId());
        assertEquals("ENTRY", east.getConnections().get(0).getId());
        assertTrue(validator.validate(west).isValid());
        assertTrue(validator.validate(east).isValid());
    }

    @Test public void allZoneLookupsResolveOnlyTheirContainingComplex() {
        KOMESiegeComplex west = definition("WEST", 3, 0, "B");
        KOMESiegeComplex east = definition("EAST", 7, 100, "C");

        assertOwnGeometry(west, east);
        assertOwnGeometry(east, west);
        assertNull(west.findNormalSegment("C"));
        assertNull(east.findNormalSegment("B"));
        assertEquals(Arrays.asList("A", "B", "WALL"),
            Arrays.asList(west.getStrongholdZoneIds().toArray(new String[0])));
        assertEquals(Arrays.asList("A", "C", "WALL"),
            Arrays.asList(east.getStrongholdZoneIds().toArray(new String[0])));
    }

    @Test public void sharedExteriorValueDoesNotJoinComplexConnectionGraphs() {
        KOMESiegeComplex west = definition("WEST", 3, 0, "B");
        KOMESiegeComplex east = definition("EAST", 7, 100, "C");
        KOMESiegeAreaRef exterior = KOMESiegeAreaRef.exterior();

        assertSame(exterior, west.getConnections().get(0).getEndpointA());
        assertSame(exterior, east.getConnections().get(0).getEndpointA());
        List<KOMESiegeConnection> westEntries = west.getConnectionsFor(exterior);
        List<KOMESiegeConnection> eastEntries = east.getConnectionsFor(exterior);
        assertEquals(1, westEntries.size());
        assertEquals(1, eastEntries.size());
        assertSame(west.getConnections().get(0), westEntries.get(0));
        assertSame(east.getConnections().get(0), eastEntries.get(0));
        assertNotSame(westEntries.get(0), eastEntries.get(0));
        assertEquals(Collections.singleton("B"), west.getAdjacentNormalSegmentIds("A"));
        assertEquals(Collections.singleton("C"), east.getAdjacentNormalSegmentIds("A"));
        assertTrue(west.getAdjacentNormalSegmentIds("C").isEmpty());
        assertTrue(east.getAdjacentNormalSegmentIds("B").isEmpty());
        assertSame(west.getConnections().get(1),
            west.getConnectionsFor(KOMESiegeAreaRef.normal("B")).get(0));
        assertSame(east.getConnections().get(1),
            east.getConnectionsFor(KOMESiegeAreaRef.normal("C")).get(0));
        assertTrue(west.getConnectionsFor(KOMESiegeAreaRef.normal("C")).isEmpty());
        assertTrue(east.getConnectionsFor(KOMESiegeAreaRef.normal("B")).isEmpty());
    }

    @Test public void identitySurvivesRevisionAndLocationChangesWithoutAffectingOtherComplexes() {
        KOMESiegeComplex west = definition("WEST", 3, 0, "B");
        KOMESiegeComplex east = definition("EAST", 7, 100, "C");
        KOMESiegeComplex revisedWest = new KOMESiegeComplex(west.getComplexId(), "t278", 1, 4,
            west.getNormalSegments(), west.getWallZones(), west.getTransitionZones(),
            west.getExteriorDeploymentAreas(), west.getConnections());

        assertEquals(west.getComplexId(), revisedWest.getComplexId());
        assertEquals("T278", revisedWest.getTileId());
        assertEquals(1, revisedWest.getDimensionId());
        assertEquals(4L, revisedWest.getRevision());
        assertEquals(3L, west.getRevision());
        assertEquals(7L, east.getRevision());
        assertEquals("T277", west.getTileId());
        assertEquals("T277", east.getTileId());
    }

    @Test public void invalidRevisionDiagnosticIdentifiesTheComplexRatherThanItsTile() {
        assertRevisionOwner(definition("WEST", -1, 0, "B"));
        assertRevisionOwner(definition("EAST", -2, 100, "C"));
    }

    private static void assertOwnGeometry(KOMESiegeComplex own, KOMESiegeComplex other) {
        assertSame(own.getNormalSegments().get(0), own.findNormalSegment("A"));
        assertSame(own.getWallZones().get(0), own.findWallZone("WALL"));
        assertSame(own.getTransitionZones().get(0), own.findTransitionZone("GATE"));
        assertSame(own.getExteriorDeploymentAreas().get(0), own.findExteriorDeploymentArea("DEPLOY"));
        assertNotSame(other.findNormalSegment("A"), own.findNormalSegment("A"));
        assertNotSame(other.findWallZone("WALL"), own.findWallZone("WALL"));
        assertNotSame(other.findTransitionZone("GATE"), own.findTransitionZone("GATE"));
        assertNotSame(other.findExteriorDeploymentArea("DEPLOY"), own.findExteriorDeploymentArea("DEPLOY"));
    }

    private void assertRevisionOwner(KOMESiegeComplex complex) {
        KOMEValidationResult result = validator.validate(complex);
        assertFalse(result.isValid());
        assertEquals(1, result.getIssues().size());
        KOMEValidationIssue issue = result.getIssues().get(0);
        assertEquals(KOMEValidationCode.INVALID_COMPLEX_REVISION, issue.getCode());
        assertEquals(Collections.singletonList(complex.getComplexId()), issue.getSubjectIds());
    }

    private static KOMESiegeComplex definition(String complexId, long revision, int offset, String innerId) {
        KOMENormalSegment outer = normal("A", offset, 0, offset + 10, 10);
        KOMENormalSegment inner = normal(innerId, offset + 12, 0, offset + 22, 10);
        KOMEWallZone wall = wall("WALL", prism(offset, 10, offset + 10, 12, 0, 10), "A");
        KOMETransitionZone entry = transition("GATE", offset - 2, 2, offset, 4);
        KOMETransitionZone internal = transition("INTERNAL", offset + 10, 6, offset + 12, 8);
        KOMEExteriorDeploymentArea deployment = new KOMEExteriorDeploymentArea("DEPLOY", "Deployment",
            prism(offset - 10, 0, offset - 5, 5, 0, 10));
        KOMESiegeConnection entrance = KOMESiegeConnection.gateLess("ENTRY", KOMESiegeAreaRef.exterior(),
            KOMESiegeAreaRef.normal("A"), "GATE");
        KOMESiegeConnection link = KOMESiegeConnection.gateLess("LINK", KOMESiegeAreaRef.normal("A"),
            KOMESiegeAreaRef.normal(innerId), "INTERNAL");
        return new KOMESiegeComplex(complexId, "t277", 0, revision, Arrays.asList(outer, inner),
            Arrays.asList(wall), Arrays.asList(entry, internal), Arrays.asList(deployment),
            Arrays.asList(entrance, link));
    }
}
