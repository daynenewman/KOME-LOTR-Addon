package kome.common.config;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KOMECampaignReadinessValidatorTest {
    private final KOMECampaignReadinessValidator validator = new KOMECampaignReadinessValidator();

    @Test
    public void validCampaignIncludingGateLessConnectionIsReady() {
        Fixture data = validFixture();
        assertTrue(validator.validate(data).isReady());
    }

    @Test
    public void missingCapitalNamesFaction() {
        Fixture data = validFixture();
        data.capitals.remove("rohan");
        assertFailure(validator.validate(data), "capital", "rohan", "capitalTileId");
    }

    @Test
    public void unknownCapitalTileFails() {
        Fixture unknown = validFixture();
        unknown.capitals.put("gondor", "T999");
        assertFailure(validator.validate(unknown), "capital", "gondor", "capitalTileId");
    }

    @Test
    public void defenseAndSiegeReferenceFailuresAreReported() {
        Fixture missingComplex = validFixture();
        missingComplex.builds.add(new Build("b2", "missing", true, "c1"));
        assertFailure(validator.validate(missingComplex), "defensiveBuild", "b2", "siegeComplexId");
        Fixture brokenSegment = validFixture();
        brokenSegment.complexes.get("s1").connections.add(new Link("unknown", "s2", false, ""));
        assertFailure(validator.validate(brokenSegment), "siegeConnection", "s1", "fromSegmentId");
        Fixture missingGate = validFixture();
        missingGate.complexes.get("s1").connections.add(new Link("s1", "s2", true, "g2"));
        assertFailure(validator.validate(missingGate), "siegeConnection", "s1", "gateId");
        Fixture blankEntry = validFixture();
        blankEntry.builds.add(new Build("b2", "s1", true, ""));
        assertFailure(validator.validate(blankEntry), "defensiveBuild", "b2",
                "entryConnectionId");
        Fixture unknownEntry = validFixture();
        unknownEntry.builds.add(new Build("b2", "s1", true, "missing"));
        assertFailure(validator.validate(unknownEntry), "defensiveBuild", "b2",
                "entryConnectionId");
    }

    @Test
    public void missingMapAssetAndFailuresHaveDeterministicOrder() {
        Fixture data = validFixture();
        data.capitals.remove("rohan");
        data.requiredAssets.add("missing-overlay");
        KOMECampaignReadinessValidator.ReadinessResult result = validator.validate(data);
        assertFalse(result.isReady());
        assertEquals(2, result.getFailures().size());
        assertEquals("capital", result.getFailures().get(0).getCategory());
        assertEquals("mapAsset", result.getFailures().get(1).getCategory());
        assertEquals("missing-overlay", result.getFailures().get(1).getValue());
    }

    @Test
    public void validationDoesNotManufactureReferences() {
        Fixture data = validFixture();
        data.capitals.remove("rohan");
        validator.validate(data);
        assertFalse(data.capitals.containsKey("rohan"));
        assertEquals(1, data.builds.size());
    }

    private static void assertFailure(KOMECampaignReadinessValidator.ReadinessResult result,
            String category, String owner, String reference) {
        for (KOMECampaignReadinessValidator.ValidationFailure failure : result.getFailures()) {
            if (category.equals(failure.getCategory()) && owner.equals(failure.getOwnerId())
                    && reference.equals(failure.getReference())) return;
        }
        throw new AssertionError("Expected " + category + "/" + owner + "/" + reference);
    }

    private static Fixture validFixture() {
        Fixture data = new Fixture();
        data.factions.addAll(Arrays.asList("gondor", "rohan"));
        data.capitals.put("gondor", "T001");
        data.capitals.put("rohan", "T002");
        data.tiles.put("T001", new Tile());
        data.tiles.put("T002", new Tile());
        Complex complex = new Complex("s1");
        complex.segments.addAll(Arrays.asList("s1", "s2"));
        complex.gates.add("g1");
        complex.connections.add(new Link("s1", "s2", true, "g1"));
        complex.connections.add(new Link("s2", "s1", false, ""));
        complex.connectionIds.add("c1");
        data.complexes.put("s1", complex);
        data.builds.add(new Build("b1", "s1", true, "c1"));
        data.requiredAssets.add("strategic-overlay");
        data.assets.add("strategic-overlay");
        return data;
    }

    private static final class Fixture implements KOMECampaignReadinessValidator.CampaignReadinessData {
        final List<String> factions = new ArrayList<String>();
        final Map<String, String> capitals = new HashMap<String, String>();
        final Map<String, Tile> tiles = new HashMap<String, Tile>();
        final List<Build> builds = new ArrayList<Build>();
        final Map<String, Complex> complexes = new HashMap<String, Complex>();
        final Set<String> requiredAssets = new HashSet<String>();
        final Set<String> assets = new HashSet<String>();
        public Collection<String> getPlayableFactions() { return factions; }
        public String getCapitalTileId(String factionId) { return capitals.get(factionId); }
        public KOMECampaignReadinessValidator.StrategicTile findStrategicTile(String id) { return tiles.get(id); }
        public Collection<KOMECampaignReadinessValidator.DefensiveBuild> getDefensiveBuilds() { return new ArrayList<KOMECampaignReadinessValidator.DefensiveBuild>(builds); }
        public KOMECampaignReadinessValidator.SiegeComplex findSiegeComplex(String id) { return complexes.get(id); }
        public Collection<String> getRequiredMapAssetIds() { return requiredAssets; }
        public boolean hasMapAsset(String id) { return assets.contains(id); }
    }

    private static final class Tile implements KOMECampaignReadinessValidator.StrategicTile {
    }
    private static final class Build implements KOMECampaignReadinessValidator.DefensiveBuild {
        final String id, complex, entry; final boolean requiresEntry;
        Build(String id, String complex, boolean requiresEntry, String entry) { this.id = id; this.complex = complex; this.requiresEntry = requiresEntry; this.entry = entry; }
        public String getId() { return id; }
        public String getSiegeComplexId() { return complex; }
        public boolean requiresEntry() { return requiresEntry; }
        public String getEntryConnectionId() { return entry; }
    }
    private static final class Link implements KOMECampaignReadinessValidator.Connection {
        final String from, to, gate; final boolean gated;
        Link(String from, String to, boolean gated, String gate) { this.from = from; this.to = to; this.gated = gated; this.gate = gate; }
        public String getFromSegmentId() { return from; }
        public String getToSegmentId() { return to; }
        public boolean requiresGate() { return gated; }
        public String getGateId() { return gate; }
    }
    private static final class Complex implements KOMECampaignReadinessValidator.SiegeComplex {
        final String id; final Set<String> segments = new HashSet<String>(); final Set<String> gates = new HashSet<String>(); final Set<String> connectionIds = new HashSet<String>(); final List<Link> connections = new ArrayList<Link>();
        Complex(String id) { this.id = id; }
        public String getId() { return id; }
        public boolean hasSegment(String id) { return segments.contains(id); }
        public boolean hasGate(String id) { return gates.contains(id); }
        public boolean hasConnection(String id) { return connectionIds.contains(id); }
        public Collection<KOMECampaignReadinessValidator.Connection> getConnections() { return new ArrayList<KOMECampaignReadinessValidator.Connection>(connections); }
    }
}
