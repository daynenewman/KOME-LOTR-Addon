package kome.common.data;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kome.common.config.KOMECampaignReadinessValidator;
import kome.common.siege.KOMEDefensiveGateRef;
import kome.common.siege.KOMESiegeComplex;
import kome.common.siege.KOMESiegeAreaRef;
import kome.common.siege.KOMESiegeConnection;
import kome.common.siege.KOMETransitionZone;
import kome.common.siege.KOMESiegeReadinessEvaluator.Diagnostic;
import kome.common.siege.KOMESiegeReadinessEvaluator.Report;
import kome.common.tactical.KOMEForceDeploymentArea;
import kome.common.tactical.KOMETacticalConfiguration;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Rule;
import org.junit.Test;
import static kome.common.data.KOMETacticalMembershipFixtures.*;
import static kome.common.siege.KOMESiegeReadinessFixtures.*;
import static org.junit.Assert.*;

public class KOMESiegeReadinessResolverTest {
    @Rule public final KOMETileTestResources tiles = new KOMETileTestResources();

    @Test public void zeroHoursActiveDefensiveBuildAndOpenEntryAreReady() {
        KOMEWorldData data = configured(open("A")); assign(data, "B1", "A");
        assertEquals(0L, data.getBuild("B1").approvedCentiHours());
        assertTrue(KOMESiegeReadinessResolver.evaluate(data, " a ").isReady());
    }
    @Test public void noAssignedBuildIsUnreadyEvenWithValidGeometry() {
        assertError(KOMESiegeReadinessResolver.evaluate(configured(open("A")), "A"), "NO_VALID_DEFENSIVE_BUILD");
    }
    @Test public void emptyPersistedAuthoringRemainsEditableButNotReady() {
        KOMEWorldData data = configured(empty("A", "T100", KOMETileTestResources.dimension()));
        assign(data, "B1", "A"); KOMEWorldData loaded = new KOMEWorldData("loaded"); loaded.readFromNBT(save(data));
        Report report = KOMESiegeReadinessResolver.evaluate(loaded, "A");
        assertError(report, "NO_NORMAL_SEGMENTS"); assertTrue(report.getDefinitionValidation().isValid());
    }
    @Test public void missingAssignedBuildCannotSupplySupport() {
        KOMEWorldData data = readyOpen(); data.builds.remove("B1");
        assertError(KOMESiegeReadinessResolver.evaluate(data, "A"), "BUILD_MISSING");
    }
    @Test public void inactiveAssignedBuildCannotSupplySupport() {
        KOMEWorldData data = readyOpen(); data.getBuild("B1").active = false;
        assertError(KOMESiegeReadinessResolver.evaluate(data, "A"), "BUILD_INACTIVE");
    }
    @Test public void nonDefensiveAssignedBuildCannotSupplySupport() {
        KOMEWorldData data = readyOpen(); data.getBuild("B1").type = KOMEBuildType.NORMAL;
        assertError(KOMESiegeReadinessResolver.evaluate(data, "A"), "BUILD_NOT_DEFENSIVE");
    }
    @Test public void assignedBuildTileAndDimensionMustMatchComplex() {
        KOMEWorldData data = readyOpen(); KOMEPlayerBuild build = data.getBuild("B1");
        build.tileId = "T101"; build.dimension++;
        Report report = KOMESiegeReadinessResolver.evaluate(data, "A");
        assertError(report, "BUILD_TILE_MISMATCH"); assertError(report, "BUILD_DIMENSION_MISMATCH");
    }
    @Test public void validGatedEntryCanBeVerifiedWithoutDuplicatingKom10Data() {
        KOMEWorldData data = readyGated(); Lookup lookup = verified(data);
        Report report = KOMESiegeReadinessResolver.evaluate(data, "A", lookup);
        assertTrue(report.isReady()); assertTrue(report.getWarnings().isEmpty()); assertEquals(1, lookup.inspections);
    }
    @Test public void validGatedAndOpenParallelEntrancesCanCoexist() {
        KOMESiegeComplex base = gated("A");
        KOMESiegeComplex both = new KOMESiegeComplex("A", "T100", KOMETileTestResources.dimension(), 17,
            base.getNormalSegments(), base.getWallZones(), Arrays.asList(base.getTransitionZones().get(0),
                new KOMETransitionZone("T_OPEN", "Open entry", kome.common.siege.KOMESiegeReadinessFixtures.prism(-2, 6, 0, 8))), null,
            Arrays.asList(base.getConnections().get(0), KOMESiegeConnection.gateLess("OPEN", KOMESiegeAreaRef.exterior(),
                KOMESiegeAreaRef.normal("A"), "T_OPEN")));
        KOMEWorldData data = configured(both); link(data, "B1"); assign(data, "B1", "A");
        Report report = KOMESiegeReadinessResolver.evaluate(data, "A", verified(data));
        assertTrue(report.isReady()); assertEquals(2, report.getAuthoredConnectionIds().size());
    }
    @Test public void invalidGateBuildBlocksEvenWhenAnotherValidAssignedBuildSuppliesSupport() {
        for (String problem : Arrays.asList("BUILD_MISSING", "BUILD_INACTIVE", "BUILD_NOT_DEFENSIVE",
                "BUILD_TILE_MISMATCH", "BUILD_DIMENSION_MISMATCH")) {
            KOMEWorldData data = readyGated(); assign(data, "B2", "A"); KOMEPlayerBuild build = data.getBuild("B1");
            if (problem.equals("BUILD_MISSING")) data.builds.remove("B1");
            else if (problem.equals("BUILD_INACTIVE")) build.active = false;
            else if (problem.equals("BUILD_NOT_DEFENSIVE")) build.type = KOMEBuildType.NORMAL;
            else if (problem.equals("BUILD_TILE_MISMATCH")) build.tileId = "T101";
            else build.dimension++;
            Report report = KOMESiegeReadinessResolver.evaluate(data, "A"); assertError(report, problem);
            assertFalse(hasCode(report.getBlockingDiagnostics(), "NO_VALID_DEFENSIVE_BUILD"));
            assertEquals("ENTRY", error(report, problem).getConnectionId());
        }
    }
    @Test public void gateFromUnassignedBuildBlocksEvenWithAnotherValidSupportBuild() {
        KOMEWorldData data = configured(gated("A")); link(data, "B1"); assign(data, "B2", "A");
        assertError(KOMESiegeReadinessResolver.evaluate(data, "A"), "BUILD_UNASSIGNED");
    }
    @Test public void gateFromDifferentComplexBlocksAndDoesNotShareMembership() {
        KOMEWorldData data = configured(gated("A"), open("B")); link(data, "B1");
        assign(data, "B2", "A"); assign(data, "B1", "B");
        assertError(KOMESiegeReadinessResolver.evaluate(data, "A"), "BUILD_ASSIGNED_TO_DIFFERENT_COMPLEX");
        assertTrue(KOMESiegeReadinessResolver.evaluate(data, "B").isReady());
    }
    @Test public void missingGateRecordBlocksWithConnectionAndPairSubjects() {
        KOMEWorldData data = configured(gated("A")); assign(data, "B1", "A");
        Report report = KOMESiegeReadinessResolver.evaluate(data, "A"); assertError(report, "GATE_RECORD_MISSING");
        Diagnostic issue = error(report, "GATE_RECORD_MISSING");
        assertEquals("A", issue.getComplexId()); assertEquals("ENTRY", issue.getConnectionId());
        assertEquals("B1", issue.getBuildId()); assertEquals("G1", issue.getGateRecordId());
        assertTrue(data.getTacticalConfigurationSnapshot().findComplex("A").getConnections().get(0).isGated());
    }
    @Test public void unloadedPhysicalDimensionIsDeferredWithoutLoadingIt() {
        KOMEWorldData data = readyGated(); int dimension = KOMETileTestResources.dimension();
        assertNull(net.minecraftforge.common.DimensionManager.getWorld(dimension));
        assertDeferred(KOMESiegeReadinessResolver.evaluate(data, "A"));
        assertNull(net.minecraftforge.common.DimensionManager.getWorld(dimension));
    }
    @Test public void unloadedChunkAndUnknownInspectionAreWarnings() {
        KOMEWorldData data = readyGated(); Lookup lookup = new Lookup(); lookup.chunkAvailable = false;
        assertDeferred(KOMESiegeReadinessResolver.evaluate(data, "A", lookup)); assertEquals(0, lookup.inspections);
        lookup.chunkAvailable = true;
        assertDeferred(KOMESiegeReadinessResolver.evaluate(data, "A", lookup)); assertEquals(1, lookup.inspections);
    }
    @Test public void knownBrokenAndInvalidBindingsAreBlocking() {
        KOMEWorldData data = readyGated(); Lookup lookup = new Lookup();
        lookup.observation = KOMETacticalGateReferenceResolver.PhysicalObservation.broken("Loaded gate is gone.");
        assertError(KOMESiegeReadinessResolver.evaluate(data, "A", lookup), "PHYSICAL_BINDING_BROKEN");
        data.getBuild("B1").getDefensiveGateRecord("G1").gateDimension = KOMETileTestResources.dimension() + 1;
        assertError(KOMESiegeReadinessResolver.evaluate(data, "A", lookup), "PHYSICAL_BINDING_INVALID");
    }
    @Test public void relinkedLogicalGateRemainsUsableAndOverrideRemainsKom10Owned() {
        KOMEWorldData data = readyGated(); KOMEDefensiveGateRecord record = data.getBuild("B1").getDefensiveGateRecord("G1");
        record.setAdminMaxHpOverride(7777, null, "Admin", 11, "Keep");
        KOMEPhysicalGateInspection.Result replacement = inspection(UUID.randomUUID(), 4, 30);
        assertTrue(KOMEDefensiveGateLinkService.relink(data, data.getBuild("B1"), "G1", replacement, true,
            null, "Admin", true, 20).isSuccessful());
        Lookup lookup = new Lookup(); lookup.observation = KOMETacticalGateReferenceResolver.PhysicalObservation.inspected(replacement);
        assertTrue(KOMESiegeReadinessResolver.evaluate(data, "A", lookup).isReady());
        assertEquals(Integer.valueOf(7777), record.getAdminMaxHpOverride()); assertSame(record, data.getBuild("B1").getDefensiveGateRecord("G1"));
    }
    @Test public void verifiedIdentityWithStaleMetadataIsWarningWithoutRefreshingRecord() {
        KOMEWorldData data = readyGated(); KOMEDefensiveGateRecord record = data.getBuild("B1").getDefensiveGateRecord("G1");
        Lookup lookup = new Lookup(); lookup.observation = KOMETacticalGateReferenceResolver.PhysicalObservation.inspected(
            inspection(record.getGateUuid(), 2, 10));
        Report report = KOMESiegeReadinessResolver.evaluate(data, "A", lookup);
        assertTrue(report.isReady()); assertTrue(hasCode(report.getWarnings(), "PHYSICAL_METADATA_STALE"));
        assertEquals(1, record.getCapturedStructureRevision());
    }
    @Test public void deletedBuildAndUnlinkedGateRetainAuthoredReferencesAsBlockingProblems() {
        KOMEWorldData data = readyGated();
        assertTrue(KOMEDefensiveGateLinkService.unlink(data, data.getBuild("B1"), "G1", null, "Admin", true, 20).isSuccessful());
        assertError(KOMESiegeReadinessResolver.evaluate(data, "A"), "GATE_RECORD_MISSING");
        assertTrue(KOMEBuildService.deleteBuild(data, data.getBuild("B1"), null, "Admin", true, "Deleted", 30).allowed);
        assertError(KOMESiegeReadinessResolver.evaluate(data, "A"), "BUILD_INACTIVE");
        assertTrue(data.getTacticalConfigurationSnapshot().findComplex("A").getConnections().get(0).isGated());
    }
    @Test public void sameTileComplexesRemainIndependentAndUnassignedBuildDoesNotPoisonReadyComplex() {
        KOMEWorldData data = configured(open("A"), empty("B", "T100", KOMETileTestResources.dimension()));
        assign(data, "B1", "A");
        Map<String, Report> reports = KOMESiegeReadinessResolver.evaluateAll(data);
        assertTrue(reports.get("A").isReady()); assertFalse(reports.get("B").isReady());
        assertFalse(data.getTacticalConfigurationSnapshot().findAssignedComplexId("B2").isPresent());
        assertEquals(Collections.singleton("A"), reports.get("A").getReachableNormalSegmentIds());
        assertTrue(reports.get("B").getReachableNormalSegmentIds().isEmpty());
        try { reports.clear(); fail(); } catch (UnsupportedOperationException expected) { }
    }
    @Test public void twoComplexesMaySharePreferredAreaAndLocalIdsWithoutSharingReadiness() {
        KOMEWorldData data = configured(preferred(open("A"), "FIELD"), preferred(open("B"), "FIELD"));
        KOMETacticalConfiguration configuration = data.getTacticalConfigurationSnapshot();
        configuration.addForceDeploymentArea(new KOMEForceDeploymentArea("FIELD", "T100", KOMETileTestResources.dimension(),
            "Shared staging", kome.common.siege.KOMESiegeReadinessFixtures.prism(0, 0, 10, 10), 31));
        installConfiguration(data, data, configuration); assign(data, "B1", "A"); assign(data, "B2", "B");
        assertTrue(KOMESiegeReadinessResolver.evaluate(data, "A").isReady()); assertTrue(KOMESiegeReadinessResolver.evaluate(data, "B").isReady());
        KOMETacticalMembershipService.unassignBuild(data, "B2", revision(data));
        Map<String, Report> reports = KOMESiegeReadinessResolver.evaluateAll(data);
        assertTrue(reports.get("A").isReady()); assertFalse(reports.get("B").isReady());
    }
    @Test public void repeatedGateIdsAndSegmentIdsRemainComplexAndBuildScopedAfterReassignment() {
        KOMESiegeComplex second = minimal("B", "T100", KOMETileTestResources.dimension(), new KOMEDefensiveGateRef("B2", "G1"));
        KOMEWorldData data = configured(gated("A"), second); link(data, "B1"); link(data, "B2");
        assign(data, "B1", "A"); assign(data, "B2", "B");
        KOMETacticalGateReferenceResolver.PhysicalLookup lookup = new KOMETacticalGateReferenceResolver.PhysicalLookup() {
            public boolean isDimensionAvailable(int dimension) { return true; }
            public boolean isControllerAvailable(KOMETacticalGateReferenceResolver.Binding binding) { return true; }
            public KOMETacticalGateReferenceResolver.PhysicalObservation inspect(KOMETacticalGateReferenceResolver.Binding binding) {
                return KOMETacticalGateReferenceResolver.PhysicalObservation.inspected(
                    inspection(binding.getGateUuid(), 1, binding.getControllerX()));
            }
        };
        Map<String, Report> ready = KOMESiegeReadinessResolver.evaluateAll(data, lookup);
        assertTrue(ready.get("A").isReady()); assertTrue(ready.get("B").isReady());
        assertTrue(KOMETacticalMembershipService.reassignBuild(data, "B1", "A", "B", revision(data)).isChanged());
        Map<String, Report> changed = KOMESiegeReadinessResolver.evaluateAll(data, lookup);
        assertError(changed.get("A"), "BUILD_ASSIGNED_TO_DIFFERENT_COMPLEX"); assertTrue(changed.get("B").isReady());
        assertTrue(ready.get("A").isReady()); // Already returned immutable reports remain snapshots.
    }
    @Test public void evaluationLeavesWorldBuildGateConfigurationRevisionAndDirtyStateUntouched() {
        KOMEWorldData data = readyGated(); Lookup lookup = verified(data);
        KOMEDefensiveGateRecord record = data.getBuild("B1").getDefensiveGateRecord("G1");
        record.setAdminMaxHpOverride(7777, null, "Admin", 11, "Read only"); data.setDirty(false);
        NBTTagCompound before = save(data), build = data.getBuild("B1").writeToNBT(), gate = record.writeToNBT();
        long revision = revision(data);
        Report first = KOMESiegeReadinessResolver.evaluate(data, "A", lookup);
        Report second = KOMESiegeReadinessResolver.evaluateAll(data, lookup).get("A");
        assertEquals(first.getReachableNormalSegmentIds(), second.getReachableNormalSegmentIds());
        assertEquals(before, save(data)); assertEquals(build, data.getBuild("B1").writeToNBT()); assertEquals(gate, record.writeToNBT());
        assertEquals(revision, revision(data)); assertFalse(data.isDirty());
    }
    @Test public void worldReportsFeedCampaignOnceForManyBuildsWithoutReinspection() {
        KOMEWorldData data = readyGated(); assign(data, "B2", "A"); Lookup lookup = verified(data);
        final Collection<Report> reports = KOMESiegeReadinessResolver.evaluateAll(data, lookup).values();
        assertEquals(1, lookup.inspections);
        KOMECampaignReadinessValidator.ReadinessResult result = new KOMECampaignReadinessValidator().validate(
            new KOMECampaignReadinessValidator.CampaignReadinessData() {
                public Collection<String> getPlayableFactions() { return Collections.emptyList(); }
                public String getCapitalTileId(String faction) { return null; }
                public KOMECampaignReadinessValidator.StrategicTile findStrategicTile(String tile) { return null; }
                public Collection<KOMECampaignReadinessValidator.DefensiveBuild> getDefensiveBuilds() {
                    return Arrays.asList(requirement("B1"), requirement("B2"));
                }
                public Collection<Report> getSiegeComplexReadinessReports() { return reports; }
                public Collection<String> getRequiredMapAssetIds() { return Collections.emptyList(); }
                public boolean hasMapAsset(String id) { return false; }
            });
        assertTrue(result.isReady()); assertEquals(1, result.getSiegeComplexReports().size()); assertEquals(1, lookup.inspections);
    }

    private static KOMECampaignReadinessValidator.DefensiveBuild requirement(final String id) {
        return new KOMECampaignReadinessValidator.DefensiveBuild() {
            public String getId() { return id; }
            public String getSiegeComplexId() { return "A"; }
            public boolean requiresEntry() { return false; }
            public String getEntryConnectionId() { return ""; }
        };
    }
    private static KOMESiegeComplex open(String id) { return minimal(id, "T100", KOMETileTestResources.dimension(), null); }
    private static KOMESiegeComplex gated(String id) { return minimal(id, "T100", KOMETileTestResources.dimension(), new KOMEDefensiveGateRef("B1", "G1")); }
    private static KOMEWorldData configured(KOMESiegeComplex... complexes) {
        KOMEWorldData source = new KOMEWorldData("source"); source.initializeIntegratedWorld();
        addBuild(source, "B1"); addBuild(source, "B2"); KOMETacticalConfiguration configuration = new KOMETacticalConfiguration();
        for (KOMESiegeComplex complex : complexes) configuration.addComplex(complex);
        KOMEWorldData data = new KOMEWorldData("test"); installConfiguration(source, data, configuration); data.setDirty(false); return data;
    }
    private static KOMEWorldData readyOpen() { KOMEWorldData data = configured(open("A")); assign(data, "B1", "A"); return data; }
    private static KOMEWorldData readyGated() { KOMEWorldData data = configured(gated("A")); link(data, "B1"); assign(data, "B1", "A"); return data; }
    private static void assertDeferred(Report report) { assertTrue(report.isReady()); assertTrue(hasCode(report.getWarnings(), "PHYSICAL_AVAILABILITY_UNKNOWN")); }
    private static void assertError(Report report, String code) { assertFalse(report.isReady()); assertNotNull(code, error(report, code)); }
    private static Diagnostic error(Report report, String code) {
        for (Diagnostic diagnostic : report.getBlockingDiagnostics()) if (code.equals(diagnostic.getCode())) return diagnostic; return null;
    }
    private static boolean hasCode(List<Diagnostic> diagnostics, String code) {
        for (Diagnostic diagnostic : diagnostics) if (code.equals(diagnostic.getCode())) return true; return false;
    }
    private static Lookup verified(KOMEWorldData data) {
        Lookup lookup = new Lookup(); KOMEDefensiveGateRecord record = data.getBuild("B1").getDefensiveGateRecord("G1");
        lookup.observation = KOMETacticalGateReferenceResolver.PhysicalObservation.inspected(inspection(record.getGateUuid(), 1, 10)); return lookup;
    }
    private static final class Lookup implements KOMETacticalGateReferenceResolver.PhysicalLookup {
        int inspections; boolean chunkAvailable = true;
        KOMETacticalGateReferenceResolver.PhysicalObservation observation = KOMETacticalGateReferenceResolver.PhysicalObservation.unknown("Deferred.");
        public boolean isDimensionAvailable(int dimension) { return true; }
        public boolean isControllerAvailable(KOMETacticalGateReferenceResolver.Binding binding) { return chunkAvailable; }
        public KOMETacticalGateReferenceResolver.PhysicalObservation inspect(KOMETacticalGateReferenceResolver.Binding binding) { inspections++; return observation; }
    }
}
