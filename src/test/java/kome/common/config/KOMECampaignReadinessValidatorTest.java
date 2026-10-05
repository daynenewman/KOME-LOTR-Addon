package kome.common.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import kome.common.siege.KOMEDefensiveGateRef;
import kome.common.siege.KOMESiegeAreaRef;
import kome.common.siege.KOMESiegeComplex;
import kome.common.siege.KOMESiegeConnection;
import kome.common.siege.KOMESiegeReadinessEvaluator;
import kome.common.siege.KOMESiegeReadinessEvaluator.AssignedBuild;
import kome.common.siege.KOMESiegeReadinessEvaluator.Diagnostic;
import kome.common.siege.KOMESiegeReadinessEvaluator.Report;
import kome.common.siege.KOMESiegeReadinessEvaluator.Source;
import kome.common.siege.validation.KOMEValidationSeverity;
import kome.common.tactical.KOMEForceDeploymentArea;
import org.junit.Test;
import static kome.common.siege.KOMESiegeReadinessFixtures.*;
import static org.junit.Assert.*;

public class KOMECampaignReadinessValidatorTest {
    private final KOMECampaignReadinessValidator validator = new KOMECampaignReadinessValidator();

    @Test public void validCampaignIncludingGatelessExteriorConnectionIsReady() {
        Fixture data = validFixture(); assertTrue(validator.validate(data).isReady());
    }
    @Test public void missingCapitalNamesFaction() {
        Fixture data = validFixture(); data.capitals.remove("rohan");
        assertFailure(validator.validate(data), "capital", "rohan", "capitalTileId");
    }
    @Test public void unknownCapitalTileFails() {
        Fixture data = validFixture(); data.capitals.put("gondor", "T999");
        assertFailure(validator.validate(data), "capital", "gondor", "capitalTileId");
    }
    @Test public void scopedReportsAndExplicitBuildEntryRequirementsAreConsumed() {
        Fixture missing = validFixture(); missing.builds.add(new Build("b2", "missing", true, "ENTRY"));
        assertFailure(validator.validate(missing), "defensiveBuild", "b2", "siegeComplexId");
        Fixture invalid = validFixture(); KOMESiegeComplex base = invalid.complexes.get("S1");
        invalid.complexes.put("S1", new KOMESiegeComplex("S1", "T100", 0, 0, base.getNormalSegments(), base.getWallZones(),
            base.getTransitionZones(), null, Collections.singletonList(KOMESiegeConnection.gateLess("BAD",
                KOMESiegeAreaRef.exterior(), KOMESiegeAreaRef.normal("unknown"), "T_ENTRY"))));
        assertFailure(validator.validate(invalid), "siegeComplex", "S1", "CONNECTION_ENDPOINT_UNKNOWN");
        assertFailure(validator.validate(gatedFixture()), "siegeComplex", "S1", "GATE_RECORD_MISSING");
        Fixture blankEntry = validFixture(); blankEntry.builds.add(new Build("b2", "s1", true, ""));
        assertFailure(validator.validate(blankEntry), "defensiveBuild", "b2", "entryConnectionId");
        Fixture unknownEntry = validFixture(); unknownEntry.builds.add(new Build("b2", "s1", true, "missing"));
        assertFailure(validator.validate(unknownEntry), "defensiveBuild", "b2", "entryConnectionId");
    }
    @Test public void missingMapAssetAndFailuresHaveDeterministicOrder() {
        Fixture data = validFixture(); data.capitals.remove("rohan"); data.requiredAssets.add("missing-overlay");
        KOMECampaignReadinessValidator.ReadinessResult result = validator.validate(data);
        assertFalse(result.isReady()); assertEquals(2, result.getFailures().size());
        assertEquals("capital", result.getFailures().get(0).getCategory());
        assertEquals("mapAsset", result.getFailures().get(1).getCategory());
        assertEquals("missing-overlay", result.getFailures().get(1).getValue());
    }
    @Test public void validationDoesNotManufactureReferences() {
        Fixture data = validFixture(); data.capitals.remove("rohan"); validator.validate(data);
        assertFalse(data.capitals.containsKey("rohan")); assertEquals(1, data.builds.size());
    }
    @Test public void twoBuildsDoNotCauseRepeatedScopedGateOrTopologyValidation() {
        Fixture data = gatedFixture(); data.builds.add(new Build("b2", "s1", false, ""));
        KOMECampaignReadinessValidator.ReadinessResult result = validator.validate(data);
        assertEquals(1, data.reportRequests); assertEquals(1, data.gateAssessments);
        assertEquals(1, result.getSiegeComplexReports().size());
        int gateErrors = 0;
        for (KOMECampaignReadinessValidator.ValidationFailure error : result.getFailures()) {
            if ("GATE_RECORD_MISSING".equals(error.getReference())) {
                gateErrors++; assertTrue(error.getValue().contains("connection=ENTRY"));
                assertTrue(error.getValue().contains("build=b1")); assertTrue(error.getValue().contains("gateRecord=G1"));
            }
        }
        assertEquals(1, gateErrors);
    }
    @Test public void physicalDeferredWarningsDoNotBecomeCampaignFailures() {
        Fixture data = gatedFixture(); data.gateCode = "PHYSICAL_AVAILABILITY_UNKNOWN";
        KOMECampaignReadinessValidator.ReadinessResult result = validator.validate(data);
        assertTrue(result.isReady()); assertEquals(1, result.getWarnings().size());
        assertEquals("PHYSICAL_AVAILABILITY_UNKNOWN", result.getWarnings().get(0).getReference());
        try { result.getWarnings().clear(); fail(); } catch (UnsupportedOperationException expected) { }
        try { result.getSiegeComplexReports().clear(); fail(); } catch (UnsupportedOperationException expected) { }
    }
    @Test public void unassignedBuildIsAllowedUnlessItsOwnCampaignEntryRequirementNeedsMembership() {
        Fixture data = validFixture(); data.builds.add(new Build("b2", "", false, ""));
        assertTrue(validator.validate(data).isReady());
        data.builds.add(new Build("b3", "", true, ""));
        assertFailure(validator.validate(data), "defensiveBuild", "b3", "siegeComplexId");
    }
    @Test public void incompleteComplexReportDoesNotChangeOtherComplexReport() {
        Fixture data = validFixture(); data.complexes.put("S2", empty("S2", "T100", 0));
        KOMECampaignReadinessValidator.ReadinessResult result = validator.validate(data);
        assertFalse(result.isReady()); assertTrue(result.getSiegeComplexReports().get(0).isReady());
        assertFalse(result.getSiegeComplexReports().get(1).isReady());
        for (KOMECampaignReadinessValidator.ValidationFailure failure : result.getFailures()) assertEquals("S2", failure.getOwnerId());
    }

    private static void assertFailure(KOMECampaignReadinessValidator.ReadinessResult result, String category, String owner, String reference) {
        for (KOMECampaignReadinessValidator.ValidationFailure failure : result.getFailures()) {
            if (category.equals(failure.getCategory()) && owner.equals(failure.getOwnerId()) && reference.equals(failure.getReference())) return;
        }
        fail("Expected " + category + "/" + owner + "/" + reference);
    }
    private static Fixture validFixture() {
        Fixture data = new Fixture(); data.factions.addAll(Arrays.asList("gondor", "rohan"));
        data.capitals.put("gondor", "T001"); data.capitals.put("rohan", "T002");
        data.tiles.put("T001", new Tile()); data.tiles.put("T002", new Tile());
        data.complexes.put("S1", minimal("S1", "T100", 0, null));
        data.builds.add(new Build("b1", "s1", true, "ENTRY"));
        data.requiredAssets.add("strategic-overlay"); data.assets.add("strategic-overlay"); return data;
    }
    private static Fixture gatedFixture() {
        Fixture data = validFixture(); data.complexes.put("S1", minimal("S1", "T100", 0, new KOMEDefensiveGateRef("b1", "G1")));
        return data;
    }
    private static final class Fixture implements KOMECampaignReadinessValidator.CampaignReadinessData {
        final List<String> factions = new ArrayList<String>();
        final Map<String, String> capitals = new HashMap<String, String>();
        final Map<String, Tile> tiles = new HashMap<String, Tile>();
        final List<Build> builds = new ArrayList<Build>();
        final Map<String, KOMESiegeComplex> complexes = new TreeMap<String, KOMESiegeComplex>();
        final Set<String> requiredAssets = new HashSet<String>(), assets = new HashSet<String>();
        int reportRequests, gateAssessments;
        String gateCode = "GATE_RECORD_MISSING";
        public Collection<String> getPlayableFactions() { return factions; }
        public String getCapitalTileId(String faction) { return capitals.get(faction); }
        public KOMECampaignReadinessValidator.StrategicTile findStrategicTile(String id) { return tiles.get(id); }
        public Collection<KOMECampaignReadinessValidator.DefensiveBuild> getDefensiveBuilds() {
            return new ArrayList<KOMECampaignReadinessValidator.DefensiveBuild>(builds);
        }
        public Collection<Report> getSiegeComplexReadinessReports() {
            reportRequests++; List<Report> reports = new ArrayList<Report>();
            for (final KOMESiegeComplex complex : complexes.values()) {
                final List<AssignedBuild> assigned = new ArrayList<AssignedBuild>();
                for (Build build : builds) if (complex.getComplexId().equals(build.complex.toUpperCase(Locale.ROOT))) {
                    assigned.add(new AssignedBuild(build.id, Collections.emptyList()));
                }
                reports.add(new KOMESiegeReadinessEvaluator().evaluate(complex, new KOMESiegeReadinessEvaluator.Context() {
                    public Collection<AssignedBuild> getAssignedBuilds() { return assigned; }
                    public KOMEForceDeploymentArea getPreferredForceDeploymentArea() { return null; }
                    public List<Diagnostic> inspectGate(KOMESiegeConnection connection) {
                        gateAssessments++;
                        return Collections.singletonList(new Diagnostic(gateCode.equals("PHYSICAL_AVAILABILITY_UNKNOWN")
                            ? KOMEValidationSeverity.WARNING : KOMEValidationSeverity.ERROR, Source.CONFIGURATION, gateCode,
                            "Gate assessment", complex.getComplexId(), null, connection.getId(), connection.getGateRef().get().getBuildId(),
                            connection.getGateRef().get().getGateRecordId(), Collections.emptyList()));
                    }
                }));
            }
            return reports;
        }
        public Collection<String> getRequiredMapAssetIds() { return requiredAssets; }
        public boolean hasMapAsset(String id) { return assets.contains(id); }
    }
    private static final class Tile implements KOMECampaignReadinessValidator.StrategicTile { }
    private static final class Build implements KOMECampaignReadinessValidator.DefensiveBuild {
        final String id, complex, entry; final boolean requiresEntry;
        Build(String id, String complex, boolean requiresEntry, String entry) {
            this.id = id; this.complex = complex; this.requiresEntry = requiresEntry; this.entry = entry;
        }
        public String getId() { return id; }
        public String getSiegeComplexId() { return complex; }
        public boolean requiresEntry() { return requiresEntry; }
        public String getEntryConnectionId() { return entry; }
    }
}
