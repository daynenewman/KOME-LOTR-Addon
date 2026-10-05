package kome.common.siege;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import kome.common.siege.KOMESiegeReadinessEvaluator.AssignedBuild;
import kome.common.siege.KOMESiegeReadinessEvaluator.Diagnostic;
import kome.common.siege.KOMESiegeReadinessEvaluator.Report;
import kome.common.siege.KOMESiegeReadinessEvaluator.Source;
import kome.common.siege.geometry.KOMEPolygon;
import kome.common.siege.geometry.KOMEPolygonPrism;
import kome.common.siege.geometry.KOMEXZPoint;
import kome.common.siege.validation.KOMEValidationSeverity;
import kome.common.tactical.KOMEForceDeploymentArea;
import org.junit.Test;
import static kome.common.siege.KOMESiegeReadinessFixtures.*;
import static org.junit.Assert.*;

public class KOMESiegeReadinessEvaluatorTest {
    private final KOMESiegeReadinessEvaluator evaluator = new KOMESiegeReadinessEvaluator();
    private static final AssignedBuild VALID_BUILD = new AssignedBuild(" b1 ", Collections.emptyList());

    @Test public void emptyAuthoringIsValidButNotReady() {
        Report report = evaluator.evaluate(empty("FORT", "T100", 0), context(Collections.emptyList()));
        assertTrue(report.getDefinitionValidation().isValid()); assertTrue(report.getGeometryValidation().isValid());
        assertFalse(report.isReady()); assertCode(report, "NO_VALID_DEFENSIVE_BUILD");
        assertCode(report, "NO_NORMAL_SEGMENTS"); assertCode(report, "NO_EXTERIOR_PATH");
    }
    @Test public void geometryWithoutAssignedBuildIsNotReady() {
        assertCode(evaluator.evaluate(minimal(), context(Collections.emptyList())), "NO_VALID_DEFENSIVE_BUILD");
    }
    @Test public void validBuildWithoutNormalSegmentIsNotReady() {
        assertCode(evaluator.evaluate(empty("FORT", "T100", 0), context(Arrays.asList(VALID_BUILD))), "NO_NORMAL_SEGMENTS");
    }
    @Test public void normalWithoutExteriorPathIsNotReady() {
        KOMESiegeComplex base = minimal();
        KOMESiegeComplex noEntry = new KOMESiegeComplex("FORT", "T100", 0, 0, base.getNormalSegments(),
            Collections.emptyList(), Collections.emptyList(), null, Collections.emptyList());
        Report report = evaluate(noEntry); assertCode(report, "NO_EXTERIOR_PATH");
        assertCode(report, "NORMAL_SEGMENT_UNREACHABLE"); assertTrue(report.getDefinitionValidation().isValid());
    }
    @Test public void minimalReadyComplexNeedsNoWallGatePreferredAreaOrAccountingMinimum() {
        KOMESiegeComplex base = minimal(); Report report = evaluate(base);
        assertTrue(report.isReady()); assertTrue(report.getWarnings().isEmpty());
        assertTrue(base.getWallZones().isEmpty()); assertFalse(base.getConnections().get(0).isGated());
        assertFalse(base.getPreferredForceDeploymentAreaId().isPresent());
        assertEquals(Collections.singleton("A"), report.getReachableNormalSegmentIds());
    }
    @Test public void branchingAuthoredGraphReachesEveryNormal() {
        Report report = evaluate(branching()); assertTrue(report.isReady());
        assertEquals(Arrays.asList("A", "B", "C"), new ArrayList<String>(report.getReachableNormalSegmentIds()));
    }
    @Test public void unreachableAuthoredNormalIsNeverSilentlyIgnored() {
        KOMESiegeComplex base = minimal();
        KOMESiegeComplex disconnected = new KOMESiegeComplex("FORT", "T100", 0, 0,
            Arrays.asList(base.getNormalSegments().get(0), new KOMENormalSegment("B", "Unfinished", prism(20, 0, 30, 10))),
            base.getWallZones(), base.getTransitionZones(), null, base.getConnections());
        Report report = evaluate(disconnected); assertCode(report, "NORMAL_SEGMENT_UNREACHABLE");
        assertEquals("B", report.getBlockingDiagnostics().get(0).getSegmentId());
        assertEquals(Collections.singleton("A"), report.getReachableNormalSegmentIds());
    }
    @Test public void parallelOpenEntrancesDoNotRequireGatesOrUseEveryEntrance() {
        KOMESiegeComplex base = parallel(null); Report report = evaluate(base);
        assertTrue(report.isReady()); assertEquals(2, report.getAuthoredConnectionIds().size());
    }
    @Test public void brokenParallelGateIsNotConvertedToOpenEvenWithAnotherLegalEntrance() {
        KOMESiegeComplex base = parallel(new KOMEDefensiveGateRef("B1", "G1"));
        Report report = evaluator.evaluate(base, new Context() {
            public List<Diagnostic> inspectGate(KOMESiegeConnection connection) {
                return Collections.singletonList(new Diagnostic(KOMEValidationSeverity.ERROR, Source.CONFIGURATION,
                    "GATE_RECORD_MISSING", "Missing gate", "FORT", null, connection.getId(), "B1", "G1", Collections.emptyList()));
            }
        });
        assertCode(report, "GATE_RECORD_MISSING"); assertEquals(Collections.singleton("A"), report.getReachableNormalSegmentIds());
        assertFalse(hasCode(report.getBlockingDiagnostics(), "NO_EXTERIOR_PATH"));
    }
    @Test public void gateWithoutResolutionFactsIsBlocking() {
        Report report = evaluator.evaluate(KOMESiegeReadinessFixtures.minimal("FORT", "T100", 0, new KOMEDefensiveGateRef("B1", "G1")), new Context() {
            public List<Diagnostic> inspectGate(KOMESiegeConnection connection) { return null; }
        });
        assertCode(report, "GATE_REFERENCE_UNRESOLVED"); assertCode(report, "NO_EXTERIOR_PATH");
    }
    @Test public void malformedGeometryBlocksWithoutRepairingOrPretendingToTraverseIt() {
        KOMESiegeComplex base = minimal();
        KOMEPolygonPrism malformed = new KOMEPolygonPrism(KOMEPolygon.of(new KOMEXZPoint(0, 0),
            new KOMEXZPoint(1, 0), new KOMEXZPoint(2, 0)), 10, 0);
        KOMESiegeComplex invalid = new KOMESiegeComplex("FORT", "T100", 0, 0,
            Collections.singletonList(new KOMENormalSegment("A", "Bad", malformed)), base.getWallZones(),
            base.getTransitionZones(), null, base.getConnections());
        Report report = evaluate(invalid); assertFalse(report.isReady()); assertFalse(report.getGeometryValidation().isValid());
        assertCode(report, "PRISM_MALFORMED_Y"); assertSame(malformed, invalid.findNormalSegment("A").getPrism());
        assertTrue(report.getReachableNormalSegmentIds().isEmpty());
    }
    @Test public void invalidAuthoredWallReferenceBlocksAsConfigurationWhileWallsRemainOptional() {
        KOMESiegeComplex base = minimal();
        KOMESiegeComplex invalid = new KOMESiegeComplex("FORT", "T100", 0, 0, base.getNormalSegments(),
            Collections.singletonList(new KOMEWallZone("W", "Wall", prism(0, 10, 10, 12), Collections.emptyList())),
            base.getTransitionZones(), null, base.getConnections());
        Report report = evaluate(invalid); assertCode(report, "WALL_ACCESS_EMPTY");
        assertTrue(report.getGeometryValidation().isValid()); assertFalse(report.isConfigurationValid());
    }
    @Test public void invalidCorridorIsNotAPathEvenWhenItsEndpointIdsExist() {
        KOMESiegeComplex base = minimal();
        KOMESiegeComplex invalid = new KOMESiegeComplex("FORT", "T100", 0, 0, base.getNormalSegments(), base.getWallZones(),
            Collections.singletonList(new KOMETransitionZone("T_ENTRY", "Wrong place", prism(-20, 2, -10, 4))), null, base.getConnections());
        assertCode(evaluate(invalid), "CONNECTION_TRANSITION_MISSES_ENDPOINT");
    }
    @Test public void connectionIdMatchingNormalIdDoesNotConflateDiagnosticRoles() {
        KOMESiegeComplex base = minimal();
        KOMESiegeComplex invalid = new KOMESiegeComplex("FORT", "T100", 0, 0, base.getNormalSegments(), base.getWallZones(),
            base.getTransitionZones(), null, Collections.singletonList(KOMESiegeConnection.gateLess("A",
                KOMESiegeAreaRef.exterior(), KOMESiegeAreaRef.normal("A"), "MISSING")));
        for (Diagnostic diagnostic : evaluate(invalid).getBlockingDiagnostics()) {
            if (!diagnostic.getCode().equals("CONNECTION_TRANSITION_UNKNOWN")) continue;
            assertEquals("A", diagnostic.getConnectionId()); assertEquals("", diagnostic.getSegmentId()); return;
        }
        fail("Expected connection transition diagnostic.");
    }
    @Test public void absentPreferredAreaIsValidButExplicitUnresolvedReferenceIsConfigurationError() {
        assertTrue(evaluate(minimal()).isReady());
        assertCode(evaluate(preferred(minimal(), "MISSING")), "PREFERRED_DEPLOYMENT_AREA_UNKNOWN");
    }
    @Test public void crossTileAndWrongDimensionPreferredReferencesRetainExistingCodes() {
        KOMESiegeComplex complex = preferred(minimal(), "FIELD");
        for (KOMEForceDeploymentArea area : Arrays.asList(area("FIELD", "T101", 0), area("FIELD", "T100", -1))) {
            Context context = new Context(); context.area = area;
            Report report = evaluator.evaluate(complex, context);
            assertCode(report, area.getDimensionId() == 0 ? "PREFERRED_DEPLOYMENT_AREA_WRONG_TILE" : "PREFERRED_DEPLOYMENT_AREA_WRONG_DIMENSION");
        }
    }
    @Test public void explicitPreferredAreaGeometryIsValidatedWithoutFortressOverlapPolicy() {
        Context context = new Context(); context.area = area("FIELD", "T100", 0);
        // The field intentionally overlaps the courtyard; staging-vs-fortress policy is still out of scope.
        assertTrue(evaluator.evaluate(preferred(minimal(), "FIELD"), context).isReady());
        context.area = new KOMEForceDeploymentArea("FIELD", "T100", 0, "Field", new KOMEPolygonPrism(KOMEPolygon.of(
            new KOMEXZPoint(0, 0), new KOMEXZPoint(1, 0), new KOMEXZPoint(2, 0)), 0, 10), 0);
        assertCode(evaluator.evaluate(preferred(minimal(), "FIELD"), context), "POLYGON_ZERO_AREA");
    }
    @Test public void exteriorDoesNotResolveToPreferredAreaPolygonOrCreateAnEntry() {
        KOMESiegeComplex base = minimal(); Context context = new Context(); context.area = area("EXTERIOR", "T100", 0);
        KOMESiegeComplex noEntry = new KOMESiegeComplex("FORT", "T100", 0, 0, base.getNormalSegments(),
            Collections.emptyList(), Collections.emptyList(), "EXTERIOR", Collections.emptyList());
        assertCode(evaluator.evaluate(noEntry, context), "NO_EXTERIOR_PATH");
    }
    @Test public void normalNamedExteriorStillNeedsAnEntryFromTheDistinctTypedOutsideNode() {
        KOMESiegeComplex base = minimal();
        KOMENormalSegment namedExterior = new KOMENormalSegment("EXTERIOR", "A normal named Exterior", base.getNormalSegments().get(0).getPrism());
        KOMESiegeComplex disconnected = new KOMESiegeComplex("FORT", "T100", 0, 0, Collections.singletonList(namedExterior),
            Collections.emptyList(), Collections.emptyList(), null, Collections.emptyList());
        assertCode(evaluate(disconnected), "NO_EXTERIOR_PATH");
        KOMESiegeComplex connected = new KOMESiegeComplex("FORT", "T100", 0, 0, Collections.singletonList(namedExterior),
            Collections.emptyList(), base.getTransitionZones(), null, Collections.singletonList(KOMESiegeConnection.gateLess(
                "ENTRY", KOMESiegeAreaRef.exterior(), KOMESiegeAreaRef.normal("EXTERIOR"), "T_ENTRY")));
        Report report = evaluate(connected); assertTrue(report.isReady());
        assertEquals(Collections.singleton("EXTERIOR"), report.getReachableNormalSegmentIds());
    }
    @Test public void preferredAreaDiagnosticDoesNotMasqueradeAsSameNamedLocalSegment() {
        Context context = new Context();
        context.area = new KOMEForceDeploymentArea("A", "T100", 0, "Area named like segment", new KOMEPolygonPrism(KOMEPolygon.of(
            new KOMEXZPoint(0, 0), new KOMEXZPoint(1, 0), new KOMEXZPoint(2, 0)), 0, 10), 0);
        Report report = evaluator.evaluate(preferred(minimal(), "A"), context);
        assertCode(report, "POLYGON_ZERO_AREA");
        for (Diagnostic diagnostic : report.getBlockingDiagnostics()) {
            assertEquals("", diagnostic.getSegmentId()); assertEquals(Collections.singletonList("A"), diagnostic.getSubjectIds());
        }
    }
    @Test public void invalidUnusedAssignedBuildDoesNotAddAnAllBuildsValidRequirement() {
        Report report = evaluator.evaluate(minimal(), context(Arrays.asList(VALID_BUILD,
            new AssignedBuild("B2", Collections.singletonList("BUILD_INACTIVE")))));
        assertTrue(report.isReady()); assertTrue(hasCode(report.getWarnings(), "BUILD_INACTIVE"));
        Report noneValid = evaluator.evaluate(minimal(), context(Collections.singletonList(
            new AssignedBuild("B2", Collections.singletonList("BUILD_INACTIVE")))));
        assertCode(noneValid, "NO_VALID_DEFENSIVE_BUILD"); assertCode(noneValid, "BUILD_INACTIVE");
    }
    @Test public void diagnosticsAndTraversalAreDeterministicAcrossAuthoredCollectionOrder() {
        KOMESiegeComplex base = branching();
        List<KOMENormalSegment> normals = new ArrayList<KOMENormalSegment>(base.getNormalSegments()); Collections.reverse(normals);
        List<KOMETransitionZone> transitions = new ArrayList<KOMETransitionZone>(base.getTransitionZones()); Collections.reverse(transitions);
        List<KOMESiegeConnection> connections = new ArrayList<KOMESiegeConnection>(base.getConnections()); Collections.reverse(connections);
        KOMESiegeComplex reversed = new KOMESiegeComplex("FORT", "T100", 0, 17, normals, base.getWallZones(), transitions, null, connections);
        AssignedBuild missing = new AssignedBuild("B2", Collections.singletonList("BUILD_MISSING"));
        AssignedBuild inactive = new AssignedBuild("B1", Collections.singletonList("BUILD_INACTIVE"));
        Report first = evaluator.evaluate(base, context(Arrays.asList(missing, inactive)));
        Report second = evaluator.evaluate(reversed, context(Arrays.asList(inactive, missing)));
        assertEquals(first.getReachableNormalSegmentIds(), second.getReachableNormalSegmentIds());
        assertEquals(signatures(first), signatures(second));
    }
    @Test public void existingValidationSubjectsArePreservedIncludingRepeatedZoneTypes() {
        KOMESiegeComplex base = minimal();
        KOMESiegeComplex duplicate = new KOMESiegeComplex("FORT", "T100", 0, 0,
            Arrays.asList(base.getNormalSegments().get(0), base.getNormalSegments().get(0)), base.getWallZones(),
            base.getTransitionZones(), null, base.getConnections());
        Report report = evaluate(duplicate);
        for (Diagnostic diagnostic : report.getBlockingDiagnostics()) if (diagnostic.getCode().equals("DUPLICATE_ZONE_ID")) {
            assertEquals(Arrays.asList("A", "NORMAL", "NORMAL"), diagnostic.getSubjectIds()); return;
        }
        fail("Expected duplicate-zone diagnostic.");
    }
    @Test public void reportsAndEvidenceUseImmutableDefensiveCollections() {
        Report report = evaluate(minimal());
        try { report.getAuthoredConnectionIds().clear(); fail(); } catch (UnsupportedOperationException expected) { }
        try { report.getReachableNormalSegmentIds().clear(); fail(); } catch (UnsupportedOperationException expected) { }
        try { report.getBlockingDiagnostics().clear(); fail(); } catch (UnsupportedOperationException expected) { }
        try { report.getWarnings().clear(); fail(); } catch (UnsupportedOperationException expected) { }
        List<String> problems = new ArrayList<String>(); problems.add("BUILD_MISSING");
        AssignedBuild assessment = new AssignedBuild("B1", problems); problems.clear(); assertFalse(assessment.isValid());
    }

    private Report evaluate(KOMESiegeComplex complex) { return evaluator.evaluate(complex, new Context()); }
    private static KOMESiegeComplex minimal() { return KOMESiegeReadinessFixtures.minimal("FORT", "T100", 0, null); }
    private static KOMEForceDeploymentArea area(String id, String tile, int dimension) {
        return new KOMEForceDeploymentArea(id, tile, dimension, "Staging", prism(0, 0, 10, 10), 0);
    }
    private static KOMESiegeComplex parallel(KOMEDefensiveGateRef gate) {
        KOMESiegeComplex base = minimal();
        return new KOMESiegeComplex("FORT", "T100", 0, 0, base.getNormalSegments(), base.getWallZones(),
            Arrays.asList(base.getTransitionZones().get(0), new KOMETransitionZone("T_SECOND", "Second", prism(-2, 6, 0, 8))), null,
            Arrays.asList(base.getConnections().get(0), new KOMESiegeConnection("SECOND", KOMESiegeAreaRef.normal("A"),
                KOMESiegeAreaRef.exterior(), "T_SECOND", gate)));
    }
    private static Context context(Collection<AssignedBuild> builds) { Context context = new Context(); context.builds = builds; return context; }
    private static class Context implements KOMESiegeReadinessEvaluator.Context {
        Collection<AssignedBuild> builds = Collections.singletonList(VALID_BUILD);
        KOMEForceDeploymentArea area;
        public Collection<AssignedBuild> getAssignedBuilds() { return builds; }
        public List<Diagnostic> inspectGate(KOMESiegeConnection connection) { return Collections.emptyList(); }
        public KOMEForceDeploymentArea getPreferredForceDeploymentArea() { return area; }
    }
    private static void assertCode(Report report, String code) { assertFalse(report.isReady()); assertTrue(code, hasCode(report.getBlockingDiagnostics(), code)); }
    private static boolean hasCode(List<Diagnostic> issues, String code) {
        for (Diagnostic diagnostic : issues) if (code.equals(diagnostic.getCode())) return true; return false;
    }
    private static List<String> signatures(Report report) {
        List<String> result = new ArrayList<String>();
        for (Diagnostic diagnostic : report.getBlockingDiagnostics()) result.add(diagnostic.getCode() + diagnostic.getSubjectIds());
        return result;
    }
}
