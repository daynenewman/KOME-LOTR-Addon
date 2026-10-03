package kome.common.tactical;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import kome.common.data.KOMEPlayerBuild;
import kome.common.data.KOMEWorldData;
import kome.common.siege.KOMEDefensiveGateRef;
import kome.common.siege.KOMENormalSegment;
import kome.common.siege.KOMESiegeAreaRef;
import kome.common.siege.KOMESiegeComplex;
import kome.common.siege.KOMESiegeComplexValidator;
import kome.common.siege.KOMESiegeConnection;
import kome.common.siege.KOMETransitionZone;
import kome.common.siege.KOMEWallZone;
import kome.common.siege.geometry.KOMEPolygon;
import kome.common.siege.geometry.KOMEPolygonPrism;
import kome.common.siege.geometry.KOMEXZPoint;
import kome.common.siege.validation.KOMEValidationCode;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMETacticalConfigurationTest {
    @Test public void sameTileComplexesAreIndependentlyAddressableByCanonicalIdentity() {
        KOMETacticalConfiguration store = new KOMETacticalConfiguration();
        KOMESiegeComplex west = connectedComplex("west", null, 0, "B", "B1");
        KOMESiegeComplex east = connectedComplex("east", null, 100, "C", "B2");
        store.addComplex(west);
        store.addComplex(east);
        assertSame(west, store.findComplex(" West "));
        assertSame(east, store.findComplex("EAST"));
        assertEquals(Arrays.asList("EAST", "WEST"), complexIds(store.listComplexesForTile(" t277 ")));
        assertEquals(Collections.singleton("B"), store.findComplex("WEST").getAdjacentNormalSegmentIds("A"));
        assertEquals(Collections.singleton("C"), store.findComplex("EAST").getAdjacentNormalSegmentIds("A"));
        assertNull(west.findNormalSegment("C"));
        assertNull(east.findNormalSegment("B"));
    }

    @Test public void complexListsAreSortedRegardlessOfInsertionOrderAndFilterByTile() {
        for (List<String> ids : Arrays.asList(Arrays.asList("C", "A", "B"), Arrays.asList("B", "C", "A"))) {
            KOMETacticalConfiguration store = new KOMETacticalConfiguration();
            for (String id : ids) store.addComplex(complex(id, "T277", 0, null, 0));
            store.addComplex(complex("OTHER", "T278", 0, null, 0));
            assertEquals(Arrays.asList("A", "B", "C"), complexIds(store.listComplexesForTile("T277")));
            assertEquals(Collections.singletonList("OTHER"), complexIds(store.listComplexesForTile("T278")));
        }
    }

    @Test public void ordinaryTileCanHaveMultipleAreasAndNoComplexes() {
        KOMETacticalConfiguration store = new KOMETacticalConfiguration();
        KOMEForceDeploymentArea west = area("WEST", "T277", 0, 3);
        KOMEForceDeploymentArea east = area("EAST", "T277", 0, 7);
        store.addForceDeploymentArea(west);
        store.addForceDeploymentArea(east);
        assertTrue(store.listComplexesForTile("T277").isEmpty());
        assertEquals(Arrays.asList("EAST", "WEST"), areaIds(store.listForceDeploymentAreasForTile("T277")));
        assertSame(west, store.findForceDeploymentArea(" west "));
        assertSame(east, store.findForceDeploymentArea("East"));
        assertEquals(west.getLabel(), east.getLabel());
    }

    @Test public void areaListsAreSortedRegardlessOfInsertionOrderAndFilterByTile() {
        for (List<String> ids : Arrays.asList(Arrays.asList("C", "A", "B"), Arrays.asList("B", "C", "A"))) {
            KOMETacticalConfiguration store = new KOMETacticalConfiguration();
            for (String id : ids) store.addForceDeploymentArea(area(id, "T277", 0, 0));
            store.addForceDeploymentArea(area("OTHER", "T278", 0, 0));
            assertEquals(Arrays.asList("A", "B", "C"), areaIds(store.listForceDeploymentAreasForTile(" t277 ")));
            assertEquals(Collections.singletonList("OTHER"), areaIds(store.listForceDeploymentAreasForTile("T278")));
        }
    }

    @Test public void duplicateCanonicalComplexAndAreaAddsRequireExplicitReplacement() {
        KOMETacticalConfiguration store = populated();
        rejectedUnchanged(store, IllegalStateException.class, () -> store.addComplex(complex(" a ", "T278", 0, null, 99)));
        rejectedUnchanged(store, IllegalStateException.class, () -> store.addForceDeploymentArea(area(" field ", "T278", 0, 99)));
    }

    @Test public void replacementRequiresAnExistingIdentity() {
        KOMETacticalConfiguration store = populated();
        rejectedUnchanged(store, IllegalArgumentException.class, () -> store.replaceComplex(complex("MISSING", "T277", 0, null, 0)));
        rejectedUnchanged(store, IllegalArgumentException.class, () -> store.replaceForceDeploymentArea(area("MISSING", "T277", 0, 0)));
    }

    @Test public void absentAssignmentIsUnassignedAndUnassignIsIdempotent() {
        KOMETacticalConfiguration store = populated();
        long revision = store.getRevision();
        assertFalse(store.findAssignedComplexId("B1").isPresent());
        assertFalse(store.unassignBuild("B1"));
        assertTrue(store.listAssignedBuildIds("A").isEmpty());
        assertEquals(revision, store.getRevision());
    }

    @Test public void multipleBuildsCanShareOneComplexWithDeterministicMembership() {
        KOMETacticalConfiguration store = populated();
        assertTrue(store.assignBuild("B3", "A"));
        assertTrue(store.assignBuild(" b1 ", " a "));
        assertTrue(store.assignBuild("B2", "A"));
        assertEquals(Arrays.asList("B1", "B2", "B3"), store.listAssignedBuildIds(" a "));
        assertEquals("A", store.findAssignedComplexId(" b1 ").get());
        assertTrue(store.listAssignedBuildIds("B").isEmpty());
        assertFalse(store.assignBuild(" B1 ", "a"));
    }

    @Test public void assigningElsewhereCannotAccidentallyReassignAnExistingBuild() {
        KOMETacticalConfiguration store = populated();
        store.assignBuild("B1", "A");
        rejectedUnchanged(store, IllegalStateException.class, () -> store.assignBuild(" b1 ", "B"));
        assertEquals("A", store.findAssignedComplexId("B1").get());
        assertTrue(store.listAssignedBuildIds("B").isEmpty());
    }

    @Test public void assignmentToNonexistentComplexIsRejected() {
        KOMETacticalConfiguration store = populated();
        rejectedUnchanged(store, IllegalArgumentException.class, () -> store.assignBuild("B1", "MISSING"));
        assertFalse(store.findAssignedComplexId("B1").isPresent());
    }

    @Test public void buildNormalizationMatchesActualWorldDataLookupIncludingRootLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            KOMEWorldData worldData = new KOMEWorldData("lookup-convention");
            KOMEPlayerBuild build = new KOMEPlayerBuild();
            build.id = "I-BUILD";
            worldData.builds.put(build.id, build);
            KOMETacticalConfiguration store = populated();
            store.assignBuild(" i-build ", "a");
            assertSame(build, worldData.getBuild(" i-build "));
            assertEquals(Collections.singletonList(worldData.getBuild(" i-build ").id), store.listAssignedBuildIds("A"));
            assertEquals("A", store.findAssignedComplexId("I-BUILD").get());
            assertEquals("", KOMETacticalIds.buildLookup(null));
            assertNull(worldData.getBuild(null));
            assertFalse(store.findAssignedComplexId(null).isPresent());
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test public void assignmentDoesNotRequireWorldBuildRecordsOrReadiness() {
        KOMETacticalConfiguration store = populated();
        assertTrue(store.assignBuild("not-a-generated-build-number", "A"));
        assertEquals("A", store.findAssignedComplexId("NOT-A-GENERATED-BUILD-NUMBER").get());
    }

    @Test public void nullAndBlankMutationKeysAreRejectedWithoutChangingState() {
        KOMETacticalConfiguration store = populated();
        for (String id : Arrays.asList(null, "", " \t ")) {
            rejectedUnchanged(store, IllegalArgumentException.class, () -> store.assignBuild(id, "A"));
            rejectedUnchanged(store, IllegalArgumentException.class, () -> store.unassignBuild(id));
            rejectedUnchanged(store, IllegalArgumentException.class, () -> store.assignBuild("B1", id));
            rejectedUnchanged(store, IllegalArgumentException.class, () -> store.removeComplex(id));
            rejectedUnchanged(store, IllegalArgumentException.class, () -> store.removeForceDeploymentArea(id));
        }
        rejectedUnchanged(store, IllegalArgumentException.class, () -> store.addComplex(null));
        rejectedUnchanged(store, IllegalArgumentException.class, () -> store.addForceDeploymentArea(null));
    }

    @Test public void explicitReassignmentPreservesDefinitionsAndReportsTheOldReferenceSnapshot() {
        KOMETacticalConfiguration store = new KOMETacticalConfiguration();
        KOMESiegeComplex old = connectedComplex("A", null, 0, "INNER", "b1");
        KOMESiegeComplex target = complex("B", "T277", 0, null, 8);
        store.addComplex(old);
        store.addComplex(target);
        store.assignBuild("B1", "A");
        KOMETacticalConfiguration.BuildReassignment result = store.reassignBuild(" b1 ", " a ", " b ");
        assertTrue(result.isChanged());
        assertEquals("B1", result.getBuildId());
        assertEquals("B", result.getNewComplexId());
        assertSame(old, result.getOldComplex());
        assertEquals("b1", result.getOldComplex().getConnections().get(0).getGateRef().get().getBuildId());
        assertSame(old, store.findComplex("A"));
        assertSame(target, store.findComplex("B"));
        assertTrue(target.getConnections().isEmpty());
        assertEquals("B", store.findAssignedComplexId("B1").get());
        assertTrue(store.listAssignedBuildIds("A").isEmpty());
        assertEquals(Collections.singletonList("B1"), store.listAssignedBuildIds("B"));
        store.replaceComplex(complex("A", "T277", 0, null, 9));
        assertSame(old, result.getOldComplex());
        assertEquals(2, result.getOldComplex().getConnections().size());
    }

    @Test public void rejectedReassignmentsLeaveAllAuthoritiesAndRevisionUnchanged() {
        KOMETacticalConfiguration store = populated();
        store.assignBuild("B1", "A");
        rejectedUnchanged(store, IllegalStateException.class, () -> store.reassignBuild("B1", "B", "A"));
        rejectedUnchanged(store, IllegalStateException.class, () -> store.reassignBuild("UNASSIGNED", "A", "B"));
        rejectedUnchanged(store, IllegalArgumentException.class, () -> store.reassignBuild("B1", "A", "MISSING"));
        rejectedUnchanged(store, IllegalArgumentException.class, () -> store.reassignBuild("B1", " ", "B"));
        rejectedUnchanged(store, IllegalArgumentException.class, () -> store.reassignBuild("B1", "A", null));
    }

    @Test public void sameOwnerReassignmentVerifiesExpectationButIsANoOp() {
        KOMETacticalConfiguration store = populated();
        store.assignBuild("B1", "A");
        long revision = store.getRevision();
        assertFalse(store.reassignBuild(" b1 ", " a ", "A").isChanged());
        assertEquals(revision, store.getRevision());
        rejectedUnchanged(store, IllegalStateException.class, () -> store.reassignBuild("B1", "B", "A"));
    }

    @Test public void complexDeletionRequiresExplicitMembershipCleanup() {
        KOMETacticalConfiguration store = populated();
        store.assignBuild("B1", "A");
        store.assignBuild("B2", "A");
        rejectedUnchanged(store, IllegalStateException.class, () -> store.removeComplex(" a "));
        store.unassignBuild("B1");
        rejectedUnchanged(store, IllegalStateException.class, () -> store.removeComplex("A"));
        store.reassignBuild("B2", "A", "B");
        assertTrue(store.removeComplex("A"));
        assertNull(store.findComplex("A"));
        assertFalse(store.findAssignedComplexId("B1").isPresent());
        assertEquals("B", store.findAssignedComplexId("B2").get());
    }

    @Test public void areaDeletionRequiresEveryPreferredReferenceToBeExplicitlyUpdated() {
        KOMETacticalConfiguration store = new KOMETacticalConfiguration();
        store.addForceDeploymentArea(area("FIELD", "T277", 0, 0));
        store.addComplex(complex("A", "T277", 0, "FIELD", 0));
        store.addComplex(complex("B", "T277", 0, "FIELD", 0));
        rejectedUnchanged(store, IllegalStateException.class, () -> store.removeForceDeploymentArea(" field "));
        store.replaceComplex(complex("A", "T277", 0, null, 1));
        rejectedUnchanged(store, IllegalStateException.class, () -> store.removeForceDeploymentArea("FIELD"));
        store.replaceComplex(complex("B", "T277", 0, "UNRESOLVED", 1));
        assertTrue(store.removeForceDeploymentArea("FIELD"));
        assertFalse(store.findComplex("A").getPreferredForceDeploymentAreaId().isPresent());
        assertEquals("UNRESOLVED", store.findComplex("B").getPreferredForceDeploymentAreaId().get());
    }

    @Test public void unresolvedReferencesSurviveAddAndReplaceAndCanResolveLater() {
        KOMETacticalConfiguration store = new KOMETacticalConfiguration();
        store.addComplex(complex("A", "T277", 0, "MISSING", 0));
        assertEquals("MISSING", store.findComplex("A").getPreferredForceDeploymentAreaId().get());
        KOMESiegeComplexValidator validator = new KOMESiegeComplexValidator();
        assertTrue(validator.validatePreferredForceDeploymentArea(store.findComplex("A"),
            store.findForceDeploymentArea("MISSING")).hasCode(KOMEValidationCode.PREFERRED_DEPLOYMENT_AREA_UNKNOWN));
        store.replaceComplex(complex("A", "T277", 0, "FIELD", 1));
        assertEquals("FIELD", store.findComplex("A").getPreferredForceDeploymentAreaId().get());
        store.addForceDeploymentArea(area("FIELD", "T277", 0, 0));
        assertTrue(validator.validatePreferredForceDeploymentArea(store.findComplex("A"),
            store.findForceDeploymentArea("FIELD")).isValid());
    }

    @Test public void knownAreaTileAndDimensionMustMatchOnComplexAddOrReplace() {
        KOMETacticalConfiguration store = populated();
        for (KOMESiegeComplex wrong : Arrays.asList(
                complex("NEW", "T278", 0, "FIELD", 0), complex("NEW", "T277", -1, "FIELD", 0))) {
            rejectedUnchanged(store, IllegalArgumentException.class, () -> store.addComplex(wrong));
        }
        for (KOMESiegeComplex wrong : Arrays.asList(
                complex("A", "T278", 0, "FIELD", 1), complex("A", "T277", -1, "FIELD", 1))) {
            rejectedUnchanged(store, IllegalArgumentException.class, () -> store.replaceComplex(wrong));
        }
    }

    @Test public void resolvingAnAreaCannotInvalidatePreviouslyUnresolvedComplexReferences() {
        KOMETacticalConfiguration store = new KOMETacticalConfiguration();
        store.addComplex(complex("A", "T277", 0, "FIELD", 0));
        rejectedUnchanged(store, IllegalArgumentException.class, () -> store.addForceDeploymentArea(area("FIELD", "T278", 0, 0)));
        rejectedUnchanged(store, IllegalArgumentException.class, () -> store.addForceDeploymentArea(area("FIELD", "T277", -1, 0)));
        store.addForceDeploymentArea(area("FIELD", "T277", 0, 0));
        assertEquals("FIELD", store.findComplex("A").getPreferredForceDeploymentAreaId().get());
    }

    @Test public void replacingAreaContextChecksEveryReferencingComplex() {
        KOMETacticalConfiguration store = populated();
        store.replaceComplex(complex("B", "T277", 0, "FIELD", 1));
        rejectedUnchanged(store, IllegalArgumentException.class, () -> store.replaceForceDeploymentArea(area("FIELD", "T278", 0, 9)));
        rejectedUnchanged(store, IllegalArgumentException.class, () -> store.replaceForceDeploymentArea(area("FIELD", "T277", -1, 9)));
        assertTrue(store.replaceForceDeploymentArea(area("FIELD", "T277", 0, 9)));
        assertEquals(9L, store.findForceDeploymentArea("FIELD").getRevision());
    }

    @Test public void incompatibleUnresolvedOwnersCannotBeSilentlyResolvedToOneArea() {
        KOMETacticalConfiguration store = new KOMETacticalConfiguration();
        store.addComplex(complex("A", "T277", 0, "FIELD", 0));
        store.addComplex(complex("B", "T278", 0, "FIELD", 0));
        rejectedUnchanged(store, IllegalArgumentException.class, () -> store.addForceDeploymentArea(area("FIELD", "T277", 0, 0)));
        assertNull(store.findForceDeploymentArea("FIELD"));
        assertEquals("FIELD", store.findComplex("B").getPreferredForceDeploymentAreaId().get());
    }

    @Test public void malformedAuthoredGeometryIsStoredWithoutDuplicatingValidation() {
        KOMETacticalConfiguration store = new KOMETacticalConfiguration();
        KOMEPolygonPrism bad = new KOMEPolygonPrism(new KOMEPolygon(Collections.<KOMEXZPoint>emptyList()), 5, 5);
        KOMEForceDeploymentArea area = new KOMEForceDeploymentArea("FIELD", "T277", 0, "", bad, 0);
        KOMESiegeComplex complex = new KOMESiegeComplex("A", "T277", 0, 0,
            Collections.singletonList(new KOMENormalSegment("BAD", "", bad)), Collections.<KOMEWallZone>emptyList(),
            Collections.<KOMETransitionZone>emptyList(), "FIELD", Collections.<KOMESiegeConnection>emptyList());
        store.addForceDeploymentArea(area);
        store.addComplex(complex);
        assertSame(area, store.findForceDeploymentArea("FIELD"));
        assertSame(complex, store.findComplex("A"));
        assertTrue(new KOMEForceDeploymentAreaValidator().validate(area).hasCode(KOMEValidationCode.PRISM_MALFORMED_Y));
        assertTrue(new KOMESiegeComplexValidator().validate(complex).hasCode(KOMEValidationCode.POLYGON_TOO_FEW_DISTINCT_VERTICES));
    }

    @Test public void sharingAnAreaDoesNotMergeComplexGraphsGateReferencesOrDefinitionRevisions() {
        KOMETacticalConfiguration store = new KOMETacticalConfiguration();
        KOMEForceDeploymentArea area = area("FIELD", "T277", 0, 50);
        KOMESiegeComplex west = connectedComplex("WEST", "FIELD", 0, "B", "B1");
        KOMESiegeComplex east = connectedComplex("EAST", "FIELD", 100, "C", "B2");
        store.addForceDeploymentArea(area);
        store.addComplex(west);
        store.addComplex(east);
        assertEquals(Collections.singleton("B"), store.findComplex("WEST").getAdjacentNormalSegmentIds("A"));
        assertEquals(Collections.singleton("C"), store.findComplex("EAST").getAdjacentNormalSegmentIds("A"));
        assertNotSame(west.findNormalSegment("A"), east.findNormalSegment("A"));
        assertSame(west.getConnections().get(0), west.getConnectionsFor(KOMESiegeAreaRef.exterior()).get(0));
        assertSame(east.getConnections().get(0), east.getConnectionsFor(KOMESiegeAreaRef.exterior()).get(0));
        assertEquals("B1", west.getConnections().get(0).getGateRef().get().getBuildId());
        assertEquals("B2", east.getConnections().get(0).getGateRef().get().getBuildId());
        store.assignBuild("B1", "WEST");
        store.assignBuild("B2", "EAST");
        assertEquals(Collections.singletonList("B1"), store.listAssignedBuildIds("WEST"));
        assertEquals(Collections.singletonList("B2"), store.listAssignedBuildIds("EAST"));
        assertEquals(3L, west.getRevision());
        assertEquals(3L, east.getRevision());
        assertEquals(50L, area.getRevision());
        assertEquals(5L, store.getRevision());
    }

    @Test public void allReturnedCollectionsAreImmutableAndDetachedSnapshots() {
        KOMETacticalConfiguration store = populated();
        store.assignBuild("B1", "A");
        List<KOMESiegeComplex> complexes = store.listComplexesForTile("T277");
        List<KOMEForceDeploymentArea> areas = store.listForceDeploymentAreasForTile("T277");
        List<String> builds = store.listAssignedBuildIds("A");
        Map<String, KOMESiegeComplex> complexMap = store.getComplexesById();
        Map<String, KOMEForceDeploymentArea> areaMap = store.getForceDeploymentAreasById();
        Map<String, String> assignments = store.getBuildAssignmentsByBuildId();
        assertThrows(UnsupportedOperationException.class, () -> complexes.clear());
        assertThrows(UnsupportedOperationException.class, () -> areas.clear());
        assertThrows(UnsupportedOperationException.class, () -> builds.add("B2"));
        assertThrows(UnsupportedOperationException.class, () -> complexMap.clear());
        assertThrows(UnsupportedOperationException.class, () -> areaMap.clear());
        assertThrows(UnsupportedOperationException.class, () -> assignments.put("B2", "B"));
        assertThrows(UnsupportedOperationException.class, () -> assignments.entrySet().iterator().next().setValue("B"));
        store.addComplex(complex("C", "T277", 0, null, 0));
        store.addForceDeploymentArea(area("OTHER", "T277", 0, 0));
        store.unassignBuild("B1");
        assertEquals(Arrays.asList("A", "B"), complexIds(complexes));
        assertEquals(Collections.singletonList("FIELD"), areaIds(areas));
        assertEquals(Collections.singletonList("B1"), builds);
        assertEquals(2, complexMap.size());
        assertEquals(1, areaMap.size());
        assertEquals(Collections.singletonMap("B1", "A"), assignments);
    }

    @Test public void derivedIndexesFollowEveryKindOfMutationIncludingLocationReplacement() {
        KOMETacticalConfiguration store = populated();
        store.assignBuild("B1", "A");
        store.replaceComplex(complex("A", "T278", -1, null, 8));
        assertEquals(Collections.singletonList("B"), complexIds(store.listComplexesForTile("T277")));
        assertEquals(Collections.singletonList("A"), complexIds(store.listComplexesForTile("T278")));
        assertEquals(Collections.singletonList("B1"), store.listAssignedBuildIds("A"));
        store.replaceForceDeploymentArea(area("FIELD", "T278", -1, 8));
        assertTrue(store.listForceDeploymentAreasForTile("T277").isEmpty());
        assertEquals(Collections.singletonList("FIELD"), areaIds(store.listForceDeploymentAreasForTile("T278")));
        store.reassignBuild("B1", "A", "B");
        assertTrue(store.listAssignedBuildIds("A").isEmpty());
        assertEquals(Collections.singletonList("B1"), store.listAssignedBuildIds("B"));
        store.unassignBuild("B1");
        assertTrue(store.listAssignedBuildIds("B").isEmpty());
        store.removeComplex("A");
        store.removeForceDeploymentArea("FIELD");
        assertTrue(store.listComplexesForTile("T278").isEmpty());
        assertTrue(store.listForceDeploymentAreasForTile("T278").isEmpty());
        assertEquals(Collections.singletonList("B"), new ArrayList<String>(store.getComplexesById().keySet()));
        assertTrue(store.getBuildAssignmentsByBuildId().isEmpty());
    }

    @Test public void revisionAdvancesOncePerChangeAndNeverRewritesObjectRevisions() {
        KOMETacticalConfiguration store = new KOMETacticalConfiguration();
        KOMESiegeComplex a = complex("A", "T277", 0, null, 100);
        KOMESiegeComplex b = complex("B", "T277", 0, null, 7);
        KOMEForceDeploymentArea area = area("FIELD", "T277", 0, 33);
        assertEquals(0L, store.getRevision());
        store.addComplex(a); assertEquals(1L, store.getRevision());
        store.addComplex(b); assertEquals(2L, store.getRevision());
        store.addForceDeploymentArea(area); assertEquals(3L, store.getRevision());
        store.assignBuild("B1", "A"); assertEquals(4L, store.getRevision());
        assertFalse(store.assignBuild("B1", "A"));
        assertFalse(store.replaceComplex(a));
        assertFalse(store.replaceForceDeploymentArea(area));
        assertEquals(4L, store.getRevision());
        store.replaceComplex(complex("A", "T277", 0, null, 101)); assertEquals(5L, store.getRevision());
        store.replaceForceDeploymentArea(area("FIELD", "T277", 0, 34)); assertEquals(6L, store.getRevision());
        store.reassignBuild("B1", "A", "B"); assertEquals(7L, store.getRevision());
        assertFalse(store.reassignBuild("B1", "B", "B").isChanged());
        store.unassignBuild("B1"); assertEquals(8L, store.getRevision());
        assertFalse(store.unassignBuild("B1"));
        store.removeComplex("A"); assertEquals(9L, store.getRevision());
        store.removeForceDeploymentArea("FIELD"); assertEquals(10L, store.getRevision());
        assertFalse(store.removeComplex("A"));
        assertFalse(store.removeForceDeploymentArea("FIELD"));
        assertEquals(10L, store.getRevision());
        assertEquals(100L, a.getRevision());
        assertEquals(7L, b.getRevision());
        assertEquals(33L, area.getRevision());
    }

    @Test public void readOperationsNeverAdvanceRevision() {
        KOMETacticalConfiguration store = populated();
        long revision = store.getRevision();
        store.findComplex("A"); store.findComplex(null);
        store.findForceDeploymentArea("FIELD"); store.findForceDeploymentArea(null);
        store.findAssignedComplexId("B1"); store.findAssignedComplexId(null);
        store.listComplexesForTile("T277"); store.listForceDeploymentAreasForTile("T277");
        store.listAssignedBuildIds("A"); store.listAssignedBuildIds("MISSING");
        store.getComplexesById(); store.getForceDeploymentAreasById(); store.getBuildAssignmentsByBuildId();
        assertEquals(revision, store.getRevision());
    }

    @Test public void exhaustedRevisionRejectsChangesBeforePublishingAnything() throws Exception {
        KOMETacticalConfiguration store = populated();
        store.assignBuild("B1", "A");
        // Reach the valid counter boundary without performing Long.MAX_VALUE operations.
        Field revision = KOMETacticalConfiguration.class.getDeclaredField("revision");
        revision.setAccessible(true);
        revision.setLong(store, Long.MAX_VALUE);
        rejectedUnchanged(store, IllegalStateException.class, () -> store.addComplex(complex("C", "T277", 0, null, 0)));
        rejectedUnchanged(store, IllegalStateException.class, () -> store.replaceComplex(complex("A", "T277", 0, null, 1)));
        rejectedUnchanged(store, IllegalStateException.class, () -> store.removeComplex("B"));
        rejectedUnchanged(store, IllegalStateException.class, () -> store.addForceDeploymentArea(area("OTHER", "T277", 0, 0)));
        rejectedUnchanged(store, IllegalStateException.class, () -> store.replaceForceDeploymentArea(area("FIELD", "T277", 0, 1)));
        rejectedUnchanged(store, IllegalStateException.class, () -> store.removeForceDeploymentArea("FIELD"));
        rejectedUnchanged(store, IllegalStateException.class, () -> store.assignBuild("B2", "A"));
        rejectedUnchanged(store, IllegalStateException.class, () -> store.unassignBuild("B1"));
        rejectedUnchanged(store, IllegalStateException.class, () -> store.reassignBuild("B1", "A", "B"));
        assertFalse(store.assignBuild("B1", "A"));
        assertFalse(store.reassignBuild("B1", "A", "A").isChanged());
        assertFalse(store.replaceComplex(store.findComplex("A")));
        assertEquals(Long.MAX_VALUE, store.getRevision());
    }

    @Test public void competingAssignmentsCannotGiveOneBuildTwoOwners() throws Exception {
        KOMETacticalConfiguration store = populated();
        long before = store.getRevision();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> a = executor.submit(() -> competingAssign(store, start, "A"));
            Future<Boolean> b = executor.submit(() -> competingAssign(store, start, "B"));
            start.countDown();
            assertNotEquals(a.get(5, TimeUnit.SECONDS), b.get(5, TimeUnit.SECONDS));
            assertEquals(1, store.listAssignedBuildIds("A").size() + store.listAssignedBuildIds("B").size());
            assertEquals(1, store.getBuildAssignmentsByBuildId().size());
            assertEquals(before + 1, store.getRevision());
        } finally {
            executor.shutdownNow();
        }
    }

    private static boolean competingAssign(KOMETacticalConfiguration store, CountDownLatch start, String target) throws InterruptedException {
        start.await();
        try { return store.assignBuild("B1", target); }
        catch (IllegalStateException alreadyAssigned) { return false; }
    }

    private static KOMETacticalConfiguration populated() {
        KOMETacticalConfiguration store = new KOMETacticalConfiguration();
        store.addComplex(complex("A", "T277", 0, null, 0));
        store.addComplex(complex("B", "T277", 0, null, 0));
        store.addForceDeploymentArea(area("FIELD", "T277", 0, 0));
        return store;
    }

    private static KOMESiegeComplex complex(String id, String tile, int dimension, String preferred, long revision) {
        return new KOMESiegeComplex(id, tile, dimension, revision, Collections.<KOMENormalSegment>emptyList(),
            Collections.<KOMEWallZone>emptyList(), Collections.<KOMETransitionZone>emptyList(), preferred,
            Collections.<KOMESiegeConnection>emptyList());
    }

    private static KOMEForceDeploymentArea area(String id, String tile, int dimension, long revision) {
        return new KOMEForceDeploymentArea(id, tile, dimension, "Staging", prism(0), revision);
    }

    private static KOMEPolygonPrism prism(int offset) {
        return new KOMEPolygonPrism(KOMEPolygon.of(new KOMEXZPoint(offset,0), new KOMEXZPoint(offset+10,0),
            new KOMEXZPoint(offset+10,10), new KOMEXZPoint(offset,10)), 0, 10);
    }

    private static KOMESiegeComplex connectedComplex(String id, String preferred, int offset, String innerId, String buildId) {
        KOMENormalSegment outer = new KOMENormalSegment("A", "", prism(offset));
        KOMENormalSegment inner = new KOMENormalSegment(innerId, "", prism(offset+12));
        KOMETransitionZone entry = new KOMETransitionZone("GATE", "", new KOMEPolygonPrism(KOMEPolygon.of(
            new KOMEXZPoint(offset-2,2), new KOMEXZPoint(offset,2), new KOMEXZPoint(offset,4), new KOMEXZPoint(offset-2,4)), 0, 10));
        KOMETransitionZone internal = new KOMETransitionZone("INTERNAL", "", new KOMEPolygonPrism(KOMEPolygon.of(
            new KOMEXZPoint(offset+10,6), new KOMEXZPoint(offset+12,6), new KOMEXZPoint(offset+12,8), new KOMEXZPoint(offset+10,8)), 0, 10));
        KOMESiegeConnection entrance = KOMESiegeConnection.gated("ENTRY", KOMESiegeAreaRef.exterior(),
            KOMESiegeAreaRef.normal("A"), "GATE", new KOMEDefensiveGateRef(buildId, "G1"));
        KOMESiegeConnection link = KOMESiegeConnection.gateLess("LINK", KOMESiegeAreaRef.normal("A"),
            KOMESiegeAreaRef.normal(innerId), "INTERNAL");
        return new KOMESiegeComplex(id, "T277", 0, 3, Arrays.asList(outer, inner), Collections.<KOMEWallZone>emptyList(),
            Arrays.asList(entry, internal), preferred, Arrays.asList(entrance, link));
    }

    private static List<String> complexIds(List<KOMESiegeComplex> complexes) {
        List<String> result = new ArrayList<String>();
        for (KOMESiegeComplex complex : complexes) result.add(complex.getComplexId());
        return result;
    }

    private static List<String> areaIds(List<KOMEForceDeploymentArea> areas) {
        List<String> result = new ArrayList<String>();
        for (KOMEForceDeploymentArea area : areas) result.add(area.getAreaId());
        return result;
    }

    private static void rejectedUnchanged(KOMETacticalConfiguration store, Class<? extends Throwable> type, Runnable action) {
        Map<String, KOMESiegeComplex> complexes = store.getComplexesById();
        Map<String, KOMEForceDeploymentArea> areas = store.getForceDeploymentAreasById();
        Map<String, String> assignments = store.getBuildAssignmentsByBuildId();
        long revision = store.getRevision();
        assertThrows(type, action::run);
        assertEquals(complexes, store.getComplexesById());
        assertEquals(areas, store.getForceDeploymentAreasById());
        assertEquals(assignments, store.getBuildAssignmentsByBuildId());
        assertEquals(revision, store.getRevision());
    }
}
