package kome.common.data;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import kome.common.siege.KOMESiegeConnection;
import kome.common.tactical.KOMETacticalConfiguration;
import kome.common.tactical.KOMETacticalConfigurationCodec;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Rule;
import org.junit.Test;
import static kome.common.data.KOMETacticalMembershipFixtures.*;
import static kome.common.data.KOMETacticalGateReferenceResolver.*;
import static kome.common.data.KOMETacticalGateReferenceResolver.Code.*;
import static org.junit.Assert.*;

public class KOMETacticalGateReferenceResolverTest {
    @Rule public final KOMETileTestResources tiles = new KOMETileTestResources();

    @Test public void validLogicalReferenceCanHaveVerifiedPhysicalBinding() {
        KOMEWorldData data = assignedWorld(); KOMEDefensiveGateRecord record = data.getBuild("B1").getDefensiveGateRecord("G1");
        StubLookup lookup = verified(record);
        Diagnostic diagnostic = resolve(data, "a", "ENTRY", lookup);
        assertTrue(diagnostic.isLogicallyValid()); assertTrue(diagnostic.getCodes().isEmpty());
        assertEquals(PhysicalStatus.VERIFIED, diagnostic.getPhysicalStatus());
        assertEquals("A", diagnostic.getComplexId()); assertEquals("B1", diagnostic.getBuildId());
        assertEquals("G1", diagnostic.getGateRecordId()); assertEquals(1, lookup.inspections);
        assertEquals(record.getGateUuid(), lookup.lastBinding.getGateUuid());
    }

    @Test public void unassignedBuildIsLogicalFailureEvenWithExistingGate() {
        KOMEWorldData data = world(); link(data, "B1");
        Diagnostic diagnostic = resolve(data, "A", "ENTRY", new StubLookup());
        assertTrue(diagnostic.getCodes().contains(BUILD_UNASSIGNED)); assertFalse(diagnostic.isLogicallyValid());
        assertEquals(PhysicalStatus.NOT_EVALUATED, diagnostic.getPhysicalStatus());
    }

    @Test public void otherComplexAssignmentDoesNotShareGateSupport() {
        KOMEWorldData data = assignedWorld();
        Diagnostic diagnostic = resolve(data, "B", "ENTRY", new StubLookup());
        assertTrue(diagnostic.getCodes().contains(BUILD_ASSIGNED_TO_DIFFERENT_COMPLEX));
        assertFalse(diagnostic.isLogicallyValid());
    }

    @Test public void missingBuildRetainsPairAndAssignmentForRepair() {
        KOMEWorldData data = assignedWorld(); data.builds.remove("B1");
        Diagnostic diagnostic = resolve(data, "A", "ENTRY", new StubLookup());
        assertTrue(diagnostic.getCodes().contains(BUILD_MISSING));
        assertEquals("G1", diagnostic.getGateRecordId()); assertAuthoredGate(data);
        assertEquals("A", data.getTacticalConfigurationSnapshot().findAssignedComplexId("B1").get());
    }

    @Test public void inactiveAndNonDefensiveBuildProblemsAreSeparate() {
        KOMEWorldData data = assignedWorld(); KOMEPlayerBuild build = data.getBuild("B1");
        build.active = false; build.type = KOMEBuildType.NORMAL;
        Diagnostic diagnostic = resolve(data, "A", "ENTRY", new StubLookup());
        assertTrue(diagnostic.getCodes().contains(BUILD_INACTIVE));
        assertTrue(diagnostic.getCodes().contains(BUILD_NOT_DEFENSIVE)); assertAuthoredGate(data);
    }

    @Test public void buildLocationMismatchesAreReportedWithoutInferringFromGateOrTileAnchor() {
        KOMEWorldData data = assignedWorld(); KOMEPlayerBuild build = data.getBuild("B1");
        build.tileId = "T101"; build.dimension++;
        Diagnostic diagnostic = resolve(data, "A", "ENTRY", new StubLookup());
        assertTrue(diagnostic.getCodes().contains(BUILD_TILE_MISMATCH));
        assertTrue(diagnostic.getCodes().contains(BUILD_DIMENSION_MISMATCH));
        assertFalse(diagnostic.isLogicallyValid());
    }

    @Test public void missingGateRecordRetainsGatedConnection() {
        KOMEWorldData data = assignedWorld(); data.getBuild("B1").removeDefensiveGateRecord("G1");
        assertTrue(resolve(data, "A", "ENTRY", new StubLookup()).getCodes().contains(GATE_RECORD_MISSING));
        assertAuthoredGate(data);
    }

    @Test public void actualSoftDeleteKeepsTacticalHistoryWhileClearingKom10GateRecords() {
        KOMEWorldData data = assignedWorld(); KOMEPlayerBuild build = data.getBuild("B1");
        long revision = revision(data); long hours = build.approvedCentiHours();
        assertTrue(KOMEBuildService.deleteBuild(data, build, null, "Admin", true, "Test deletion", 20L).allowed);
        assertFalse(build.active); assertTrue(build.getDefensiveGateRecords().isEmpty());
        assertEquals(hours, build.approvedCentiHours()); assertEquals(0L, build.approvedDefensiveCentiHours());
        assertEquals(1L, build.getDefensiveGateRecordSequence());
        assertEquals(revision, revision(data)); data.setDirty(false);
        Diagnostic diagnostic = resolve(data, "A", "ENTRY", new StubLookup());
        assertTrue(diagnostic.getCodes().contains(BUILD_INACTIVE)); assertTrue(diagnostic.getCodes().contains(GATE_RECORD_MISSING));
        assertEquals("A", data.getTacticalConfigurationSnapshot().findAssignedComplexId("B1").get());
        assertAuthoredGate(data); assertFalse(data.isDirty());
    }

    @Test public void actualRelinkKeepsLogicalPairAndInspectsReplacementBinding() {
        KOMEWorldData data = assignedWorld(); KOMEPlayerBuild build = data.getBuild("B1");
        KOMEDefensiveGateRecord record = build.getDefensiveGateRecord("G1");
        record.setAdminMaxHpOverride(7777, null, "Admin", 11L, "Keep override");
        UUID replacement = UUID.randomUUID();
        KOMEPhysicalGateInspection.Result inspection = inspection(replacement, 4, 30);
        NBTTagCompound tacticalBefore = KOMETacticalConfigurationCodec.encode(data.getTacticalConfigurationSnapshot());
        assertTrue(KOMEDefensiveGateLinkService.relink(data, build, "G1", inspection, true, null, "Admin", true, 20L).isSuccessful());
        assertSame(record, build.getDefensiveGateRecord("G1")); assertEquals(replacement, record.getGateUuid());
        assertEquals(Integer.valueOf(7777), record.getAdminMaxHpOverride());
        StubLookup lookup = new StubLookup(); lookup.observation = PhysicalObservation.inspected(inspection);
        assertEquals(PhysicalStatus.VERIFIED, resolve(data, "A", "ENTRY", lookup).getPhysicalStatus());
        assertEquals(replacement, lookup.lastBinding.getGateUuid());
        assertEquals(30, lookup.lastBinding.getControllerX());
        assertEquals(tacticalBefore, KOMETacticalConfigurationCodec.encode(data.getTacticalConfigurationSnapshot()));
    }

    @Test public void actualUnlinkRemovesOnlyKom10RecordAndReportsUnresolvedReference() {
        KOMEWorldData data = assignedWorld();
        long before = revision(data);
        assertTrue(KOMEDefensiveGateLinkService.unlink(data, data.getBuild("B1"), "G1", null, "Admin", true, 20L).isSuccessful());
        List<Diagnostic> affected = listReferencesForGate(data, " b1 ", "G1");
        assertEquals(3, affected.size());
        for (Diagnostic diagnostic : affected) assertTrue(diagnostic.getCodes().contains(GATE_RECORD_MISSING));
        assertAuthoredGate(data); assertEquals(before, revision(data));
    }

    @Test public void unloadedDimensionSkipsAllControllerAccessAndIsUnknownNotBroken() {
        KOMEWorldData data = assignedWorld(); StubLookup lookup = new StubLookup(); lookup.dimensionAvailable = false;
        Diagnostic diagnostic = resolve(data, "A", "ENTRY", lookup);
        assertUnknown(diagnostic); assertEquals(0, lookup.controllerChecks); assertEquals(0, lookup.inspections);
    }

    @Test public void unloadedChunkSkipsInspectionAndIsUnknownNotBroken() {
        KOMEWorldData data = assignedWorld(); StubLookup lookup = new StubLookup(); lookup.controllerAvailable = false;
        Diagnostic diagnostic = resolve(data, "A", "ENTRY", lookup);
        assertUnknown(diagnostic); assertEquals(1, lookup.controllerChecks); assertEquals(0, lookup.inspections);
    }

    @Test public void defaultLookupDefersUnloadedDimensionWithoutCreatingAWorld() {
        KOMEWorldData data = assignedWorld();
        int dimension = KOMETileTestResources.dimension();
        assertNull(net.minecraftforge.common.DimensionManager.getWorld(dimension));
        assertUnknown(resolve(data, "A", "ENTRY"));
        assertNull(net.minecraftforge.common.DimensionManager.getWorld(dimension));
    }

    @Test public void mutationDeferredObservationRemainsUnknown() {
        KOMEWorldData data = assignedWorld(); StubLookup lookup = new StubLookup();
        lookup.observation = PhysicalObservation.unknown("Physical gate mutation in progress.");
        assertUnknown(resolve(data, "A", "ENTRY", lookup));
    }

    @Test public void failedOrAbsentInspectionCannotProveBreakage() {
        KOMEWorldData data = assignedWorld(); StubLookup lookup = new StubLookup(); lookup.throwInspection = true;
        assertUnknown(resolve(data, "A", "ENTRY", lookup));
        lookup.throwInspection = false; lookup.observation = null;
        assertUnknown(resolve(data, "A", "ENTRY", lookup));
    }

    @Test public void loadedKnownMissingPhysicalGateIsBrokenWithLogicalPairStillValid() {
        KOMEWorldData data = assignedWorld(); StubLookup lookup = new StubLookup();
        lookup.observation = PhysicalObservation.broken("No Siege Gate at loaded controller.");
        Diagnostic diagnostic = resolve(data, "A", "ENTRY", lookup);
        assertTrue(diagnostic.isLogicallyValid()); assertEquals(PhysicalStatus.BROKEN, diagnostic.getPhysicalStatus());
        assertTrue(diagnostic.getCodes().contains(PHYSICAL_BINDING_BROKEN));
        assertFalse(diagnostic.getCodes().contains(PHYSICAL_AVAILABILITY_UNKNOWN)); assertAuthoredGate(data);
    }

    @Test public void replacementUuidOrControllerDoesNotVerifyOldBinding() {
        KOMEWorldData data = assignedWorld(); KOMEDefensiveGateRecord record = data.getBuild("B1").getDefensiveGateRecord("G1");
        StubLookup lookup = new StubLookup();
        for (KOMEPhysicalGateInspection.Result replacement : Arrays.asList(
                inspection(UUID.randomUUID(), 1, 10), inspection(record.getGateUuid(), 1, 11))) {
            lookup.observation = PhysicalObservation.inspected(replacement);
            assertTrue(resolve(data, "A", "ENTRY", lookup).getCodes().contains(PHYSICAL_BINDING_BROKEN));
        }
    }

    @Test public void knownInvalidInspectionIsBrokenNotUnknown() {
        KOMEWorldData data = assignedWorld(); StubLookup lookup = new StubLookup();
        lookup.observation = PhysicalObservation.inspected(KOMEPhysicalGateInspection.inspect((KOMEPhysicalGateInspection.Snapshot) null));
        assertTrue(resolve(data, "A", "ENTRY", lookup).getCodes().contains(PHYSICAL_BINDING_BROKEN));
    }

    @Test public void incompletePhysicalMetadataAndInconsistentDimensionAreKnownInvalid() {
        KOMEWorldData data = assignedWorld(); KOMEDefensiveGateRecord record = data.getBuild("B1").getDefensiveGateRecord("G1");
        StubLookup lookup = new StubLookup();
        Integer dimension = record.gateDimension;
        record.gateDimension = null;
        Diagnostic diagnostic = resolve(data, "A", "ENTRY", lookup);
        assertTrue(diagnostic.isLogicallyValid()); assertEquals(PhysicalStatus.BROKEN, diagnostic.getPhysicalStatus());
        assertTrue(diagnostic.getCodes().contains(PHYSICAL_BINDING_INVALID)); assertEquals(0, lookup.inspections);
        record.gateDimension = dimension + 1;
        assertTrue(resolve(data, "A", "ENTRY", lookup).getCodes().contains(PHYSICAL_BINDING_INVALID));
        record.gateDimension = dimension; record.capturedStructureRevision = 0;
        assertTrue(resolve(data, "A", "ENTRY", lookup).getCodes().contains(PHYSICAL_BINDING_INVALID));
    }

    @Test public void changedStructureRevisionIsVerifiedButMetadataStaleWithoutRefreshingKom10() {
        KOMEWorldData data = assignedWorld(); KOMEDefensiveGateRecord record = data.getBuild("B1").getDefensiveGateRecord("G1");
        StubLookup lookup = new StubLookup(); lookup.observation = PhysicalObservation.inspected(inspection(record.getGateUuid(), 2, 10));
        Diagnostic diagnostic = resolve(data, "A", "ENTRY", lookup);
        assertTrue(diagnostic.isLogicallyValid()); assertEquals(PhysicalStatus.VERIFIED, diagnostic.getPhysicalStatus());
        assertTrue(diagnostic.getCodes().contains(PHYSICAL_METADATA_STALE));
        assertFalse(diagnostic.getCodes().contains(PHYSICAL_BINDING_BROKEN)); assertEquals(1, record.getCapturedStructureRevision());
    }

    @Test public void gateIdsAreLocalCaseSensitivePairsWhileBuildLookupIsCanonical() {
        KOMEWorldData data = assignedWorld(); link(data, "B2"); assign(data, "B2", "B");
        KOMETacticalConfiguration candidate = data.getTacticalConfigurationSnapshot();
        candidate.replaceComplex(complex("B", "T100", KOMETileTestResources.dimension(), gate("ENTRY", " b2 ", "G1")));
        candidate.addComplex(complex("CASE", "T100", KOMETileTestResources.dimension(), gate("ENTRY", "B1", "g1")));
        installConfiguration(data, data, candidate);
        assertEquals(PhysicalStatus.VERIFIED, resolve(data, "B", "ENTRY", verified(data.getBuild("B2").getDefensiveGateRecord("G1"))).getPhysicalStatus());
        assertTrue(resolve(data, "A", "Z_ENTRY", verified(data.getBuild("B1").getDefensiveGateRecord("G1"))).isLogicallyValid());
        assertTrue(resolve(data, "CASE", "ENTRY", new StubLookup()).getCodes().contains(GATE_RECORD_MISSING));
        assertEquals(2, listReferencesForGate(data, " b1 ", "G1").size());
        assertEquals(1, listReferencesForGate(data, "B1", "g1").size());
        assertEquals(1, listReferencesForGate(data, "B2", "G1").size());
    }

    @Test public void diagnosticListsAreDeterministicImmutableAndIncludeBothComplexContexts() {
        KOMEWorldData data = assignedWorld(); StubLookup lookup = new StubLookup(); lookup.dimensionAvailable = false;
        List<Diagnostic> diagnostics = listReferences(data, lookup);
        assertEquals(3, diagnostics.size());
        assertEquals("A:ENTRY", key(diagnostics.get(0))); assertEquals("A:Z_ENTRY", key(diagnostics.get(1)));
        assertEquals("B:ENTRY", key(diagnostics.get(2)));
        List<Diagnostic> again = listReferences(data, lookup);
        for (int i = 0; i < diagnostics.size(); i++) {
            assertEquals(key(diagnostics.get(i)), key(again.get(i)));
            assertEquals(diagnostics.get(i).getCodes(), again.get(i).getCodes());
        }
        try { diagnostics.clear(); fail(); } catch (UnsupportedOperationException expected) { }
        try { diagnostics.get(0).getCodes().clear(); fail(); } catch (UnsupportedOperationException expected) { }
    }

    @Test public void diagnosticGenerationChangesNoWorldBuildGateGeometryOrAccountingState() {
        KOMEWorldData data = assignedWorld(); KOMEDefensiveGateRecord record = data.getBuild("B1").getDefensiveGateRecord("G1");
        record.setAdminMaxHpOverride(7777, null, "Admin", 11L, "Read only"); data.setDirty(false);
        NBTTagCompound root = save(data); NBTTagCompound build = data.getBuild("B1").writeToNBT();
        NBTTagCompound gate = record.writeToNBT(); long before = revision(data);
        listReferences(data, verified(record)); listReferencesForBuild(data, "b1"); listReferencesForGate(data, "b1", "G1");
        assertEquals(root, save(data)); assertEquals(build, data.getBuild("B1").writeToNBT());
        assertEquals(gate, record.writeToNBT()); assertEquals(before, revision(data)); assertFalse(data.isDirty());
        assertSame(record, data.getBuild("B1").getDefensiveGateRecord("G1"));
    }

    @Test public void duplicateConnectionIdsAreReportedByBulkScanButNeverSilentlyResolved() {
        KOMEWorldData data = assignedWorld(); KOMETacticalConfiguration candidate = data.getTacticalConfigurationSnapshot();
        candidate.replaceComplex(complex("A", "T100", KOMETileTestResources.dimension(),
            gate("ENTRY", "B1", "G1"), gate("ENTRY", "B2", "G1")));
        installConfiguration(data, data, candidate);
        assertEquals(3, listReferences(data, new StubLookup()).size());
        try { resolve(data, "A", "ENTRY", new StubLookup()); fail(); } catch (IllegalArgumentException expected) { }
    }

    private static KOMEWorldData assignedWorld() {
        KOMEWorldData data = world(); link(data, "B1"); assign(data, "B1", "A"); return data;
    }
    private static String key(Diagnostic diagnostic) { return diagnostic.getComplexId() + ":" + diagnostic.getConnectionId(); }
    private static void assertAuthoredGate(KOMEWorldData data) {
        KOMESiegeConnection connection = data.getTacticalConfigurationSnapshot().findComplex("A").getConnections().get(1);
        assertTrue(connection.isGated()); assertEquals("B1", connection.getGateRef().get().getBuildId());
        assertEquals("G1", connection.getGateRef().get().getGateRecordId());
    }
    private static void assertUnknown(Diagnostic diagnostic) {
        assertTrue(diagnostic.isLogicallyValid()); assertEquals(PhysicalStatus.UNKNOWN, diagnostic.getPhysicalStatus());
        assertTrue(diagnostic.getCodes().contains(PHYSICAL_AVAILABILITY_UNKNOWN));
        assertFalse(diagnostic.getCodes().contains(PHYSICAL_BINDING_BROKEN));
        assertFalse(diagnostic.getCodes().contains(PHYSICAL_BINDING_INVALID));
    }
    private static StubLookup verified(KOMEDefensiveGateRecord record) {
        StubLookup lookup = new StubLookup();
        lookup.observation = PhysicalObservation.inspected(inspection(record.getGateUuid(), record.getCapturedStructureRevision(), record.getControllerX()));
        return lookup;
    }
    private static final class StubLookup implements PhysicalLookup {
        boolean dimensionAvailable = true, controllerAvailable = true, throwInspection;
        int controllerChecks, inspections;
        Binding lastBinding;
        PhysicalObservation observation = PhysicalObservation.unknown("Deferred test inspection.");
        public boolean isDimensionAvailable(int dimension) { return dimensionAvailable; }
        public boolean isControllerAvailable(Binding binding) { controllerChecks++; return controllerAvailable; }
        public PhysicalObservation inspect(Binding binding) {
            inspections++; lastBinding = binding;
            if (throwInspection) throw new IllegalStateException("Unavailable test inspection.");
            return observation;
        }
    }
}
