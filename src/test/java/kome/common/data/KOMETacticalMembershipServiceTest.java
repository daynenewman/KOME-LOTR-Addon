package kome.common.data;

import java.util.List;
import kome.common.tactical.KOMETacticalConfiguration;
import kome.common.tactical.KOMETacticalConfigurationCodec;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Rule;
import org.junit.Test;
import static kome.common.data.KOMETacticalMembershipFixtures.*;
import static kome.common.data.KOMETacticalMembershipService.Status.*;
import static org.junit.Assert.*;

public class KOMETacticalMembershipServiceTest {
    @Rule public final KOMETileTestResources tiles = new KOMETileTestResources();

    @Test public void validAssignmentUsesCanonicalBuildIdentityAndMarksDirtyOnce() {
        CountingWorld data = install(new CountingWorld());
        long before = revision(data);
        KOMETacticalMembershipService.Result result = KOMETacticalMembershipService.assignBuild(data, " b1 ", " a ", before);
        assertEquals(CHANGED, result.getStatus());
        assertEquals("B1", result.getBuildId());
        assertEquals("A", data.getTacticalConfigurationSnapshot().findAssignedComplexId("b1").get());
        assertEquals(before + 1, revision(data));
        assertEquals(before + 1, result.getRevision());
        assertTrue(data.isDirty());
        assertEquals(1, data.dirtyCalls);
        assertEquals(17L, data.getTacticalConfigurationSnapshot().findComplex("A").getRevision());
    }

    @Test public void missingBuildRejected() { assertRejected(world(), "MISSING", "A", BUILD_MISSING); }
    @Test public void inactiveBuildRejected() {
        KOMEWorldData data = world(); data.getBuild("B1").active = false;
        assertRejected(data, "B1", "A", BUILD_INACTIVE);
    }
    @Test public void nonDefensiveBuildRejected() {
        KOMEWorldData data = world(); data.getBuild("B1").type = KOMEBuildType.NORMAL;
        assertRejected(data, "B1", "A", BUILD_NOT_DEFENSIVE);
    }
    @Test public void absentComplexRejected() { assertRejected(world(), "B1", "ABSENT", COMPLEX_MISSING); }
    @Test public void wrongTileRejectedFromBuildOwnedTileId() { assertRejected(world(), "B1", "OTHER_TILE", BUILD_TILE_MISMATCH); }
    @Test public void wrongDimensionRejectedFromBuildOwnedDimension() {
        assertRejected(world(), "B1", "OTHER_DIMENSION", BUILD_DIMENSION_MISMATCH);
    }
    @Test public void normalizedBuildTileAcceptedWithoutUsingTileAnchor() {
        KOMEWorldData data = world(); data.getBuild("B1").tileId = " t100 ";
        assign(data, "B1", "A");
    }
    @Test public void blankBuildLocationCannotProveSameTile() {
        KOMEWorldData data = world(); data.getBuild("B1").tileId = null;
        assertRejected(data, "B1", "A", BUILD_TILE_MISMATCH);
    }
    @Test public void blankIdsRejectedWithoutMutating() { assertRejected(world(), "  ", "A", INVALID_ID); }

    @Test public void sameAssignmentIsNoOpAndSecondComplexRequiresReassignment() {
        CountingWorld data = install(new CountingWorld()); assign(data, "B1", "A");
        data.setDirty(false); data.dirtyCalls = 0;
        long before = revision(data);
        assertEquals(NO_CHANGE, KOMETacticalMembershipService.assignBuild(data, "B1", "A", before).getStatus());
        assertEquals(before, revision(data)); assertFalse(data.isDirty()); assertEquals(0, data.dirtyCalls);
        assertRejected(data, "B1", "B", BUILD_ALREADY_ASSIGNED);
    }

    @Test public void multipleBuildsCanBelongToOneComplex() {
        KOMEWorldData data = world(); assign(data, "B1", "A"); assign(data, "B2", "A");
        assertEquals(java.util.Arrays.asList("B1", "B2"), data.getTacticalConfigurationSnapshot().listAssignedBuildIds("A"));
    }

    @Test public void reassignmentIsExplicitAndReportsIntactOldReferencesDeterministically() {
        CountingWorld data = install(new CountingWorld()); link(data, "B1"); assign(data, "B1", "A");
        List<?> oldConnections = data.getTacticalConfigurationSnapshot().findComplex("A").getConnections();
        long before = revision(data); data.setDirty(false); data.dirtyCalls = 0;
        KOMETacticalMembershipService.Result result = KOMETacticalMembershipService.reassignBuild(data, " b1 ", " a ", "b", before);
        assertEquals(CHANGED, result.getStatus()); assertTrue(data.isDirty());
        assertEquals(1, data.dirtyCalls);
        assertEquals(before + 1, revision(data));
        assertEquals("A", result.getOldComplexId()); assertEquals("B", result.getNewComplexId());
        assertEquals("B", data.getTacticalConfigurationSnapshot().findAssignedComplexId("B1").get());
        assertTrue(data.getTacticalConfigurationSnapshot().listAssignedBuildIds("A").isEmpty());
        assertEquals(oldConnections, data.getTacticalConfigurationSnapshot().findComplex("A").getConnections());
        assertEquals(2, result.getAffectedReferences().size());
        assertEquals("ENTRY", result.getAffectedReferences().get(0).getConnectionId());
        assertEquals("Z_ENTRY", result.getAffectedReferences().get(1).getConnectionId());
        for (KOMETacticalGateReferenceResolver.Diagnostic impact : result.getAffectedReferences()) {
            assertEquals("A", impact.getComplexId()); assertEquals("G1", impact.getGateRecordId());
            assertTrue(impact.getCodes().contains(KOMETacticalGateReferenceResolver.Code.BUILD_ASSIGNED_TO_DIFFERENT_COMPLEX));
        }
        assertNotNull(data.getBuild("B1").getDefensiveGateRecord("G1"));
    }

    @Test public void wrongExpectedOwnerAndInvalidNewTargetLeaveEverythingUnchanged() {
        KOMEWorldData data = world(); assign(data, "B1", "A"); data.setDirty(false);
        NBTTagCompound before = save(data); long revision = revision(data);
        assertEquals(EXPECTED_ASSIGNMENT_MISMATCH, KOMETacticalMembershipService.reassignBuild(data, "B1", "B", "B", revision).getStatus());
        assertEquals(BUILD_TILE_MISMATCH, KOMETacticalMembershipService.reassignBuild(data, "B1", "A", "OTHER_TILE", revision).getStatus());
        assertEquals(before, save(data)); assertFalse(data.isDirty()); assertEquals(revision, revision(data));
    }

    @Test public void staleRevisionRejectsAllThreeOperations() {
        KOMEWorldData data = world(); long stale = revision(data); assign(data, "B1", "A"); data.setDirty(false);
        NBTTagCompound before = save(data);
        assertEquals(STALE_CONFIGURATION_REVISION, KOMETacticalMembershipService.assignBuild(data, "B2", "A", stale).getStatus());
        assertEquals(STALE_CONFIGURATION_REVISION, KOMETacticalMembershipService.reassignBuild(data, "B1", "A", "B", stale).getStatus());
        assertEquals(STALE_CONFIGURATION_REVISION, KOMETacticalMembershipService.unassignBuild(data, "B1", stale).getStatus());
        assertEquals(before, save(data)); assertFalse(data.isDirty());
    }

    @Test public void sameOwnerReassignmentIsNoOpOnlyWithCorrectExpectedOwner() {
        KOMEWorldData data = world(); assign(data, "B1", "A"); data.setDirty(false); long revision = revision(data);
        assertEquals(NO_CHANGE, KOMETacticalMembershipService.reassignBuild(data, "B1", "A", "A", revision).getStatus());
        assertEquals(EXPECTED_ASSIGNMENT_MISMATCH, KOMETacticalMembershipService.reassignBuild(data, "B1", "B", "A", revision).getStatus());
        assertEquals(revision, revision(data)); assertFalse(data.isDirty());
    }

    @Test public void unassignmentPreservesAndReportsOldReferencesAndIsIdempotent() {
        CountingWorld data = install(new CountingWorld()); link(data, "B1"); assign(data, "B1", "A");
        data.setDirty(false); data.dirtyCalls = 0; long before = revision(data);
        KOMETacticalMembershipService.Result result = KOMETacticalMembershipService.unassignBuild(data, "b1", before);
        assertTrue(result.isChanged()); assertEquals(1, data.dirtyCalls); assertTrue(data.isDirty());
        assertEquals(before + 1, revision(data));
        assertFalse(data.getTacticalConfigurationSnapshot().findAssignedComplexId("B1").isPresent());
        assertEquals(2, result.getAffectedReferences().size());
        assertTrue(result.getAffectedReferences().get(0).getCodes().contains(KOMETacticalGateReferenceResolver.Code.BUILD_UNASSIGNED));
        assertTrue(data.getTacticalConfigurationSnapshot().findComplex("A").getConnections().get(0).isGated());
        data.setDirty(false); data.dirtyCalls = 0;
        assertEquals(NO_CHANGE, KOMETacticalMembershipService.unassignBuild(data, "B1", revision(data)).getStatus());
        assertFalse(data.isDirty()); assertEquals(0, data.dirtyCalls);
    }

    @Test public void unassignmentCanRepairMissingOrInactiveBuildAssignment() {
        KOMEWorldData data = world(); assign(data, "B1", "A"); assign(data, "B2", "A");
        data.builds.remove("B1"); data.getBuild("B2").active = false;
        assertTrue(KOMETacticalMembershipService.unassignBuild(data, "B1", revision(data)).isChanged());
        assertTrue(KOMETacticalMembershipService.unassignBuild(data, "B2", revision(data)).isChanged());
        assertTrue(data.getTacticalConfigurationSnapshot().listAssignedBuildIds("A").isEmpty());
    }

    @Test public void allMembershipMutationsSurviveRootSaveLoad() {
        KOMEWorldData data = world(); assign(data, "B1", "A"); assign(data, "B2", "A");
        assertRoundTrip(data);
        assertTrue(KOMETacticalMembershipService.reassignBuild(data, "B1", "A", "B", revision(data)).isChanged());
        assertRoundTrip(data);
        assertTrue(KOMETacticalMembershipService.unassignBuild(data, "B2", revision(data)).isChanged());
        assertRoundTrip(data);
    }

    @Test public void membershipChangesPreserveKom10HoursRecordsOverridesAndAudit() {
        KOMEWorldData data = world(); KOMEPlayerBuild build = data.getBuild("B1");
        KOMEBuildContribution approved = new KOMEBuildContribution();
        approved.id = "approved"; approved.centiHours = 125L; approved.status = KOMEBuildContribution.APPROVED;
        build.contributions.add(approved);
        KOMEDefensiveGateRecord record = link(data, "B1");
        record.setAdminMaxHpOverride(7777, null, "Admin", 11L, "Preserve accounting");
        NBTTagCompound before = build.writeToNBT();
        assign(data, "B1", "A");
        assertTrue(KOMETacticalMembershipService.reassignBuild(data, "B1", "A", "B", revision(data)).isChanged());
        assertTrue(KOMETacticalMembershipService.unassignBuild(data, "B1", revision(data)).isChanged());
        assertEquals(before, build.writeToNBT()); assertSame(record, build.getDefensiveGateRecord("G1"));
        assertEquals(125L, build.approvedDefensiveCentiHours()); assertEquals(Integer.valueOf(7777), record.getAdminMaxHpOverride());
    }

    @Test public void validationRejectionPreservesAnAlreadyDirtyWorld() {
        KOMEWorldData data = world(); data.setDirty(true); long before = revision(data); NBTTagCompound root = save(data);
        assertEquals(BUILD_MISSING, KOMETacticalMembershipService.assignBuild(data, "MISSING", "A", before).getStatus());
        assertTrue(data.isDirty()); assertEquals(before, revision(data)); assertEquals(root, save(data));
    }

    @Test public void publicationRollbackRestoresRevisionAndBothDirtyStates() {
        CountingWorld data = install(new CountingWorld());
        for (boolean initiallyDirty : new boolean[] {false, true}) {
            data.setDirty(initiallyDirty); data.failDirty = true;
            NBTTagCompound before = save(data); long revision = revision(data);
            assertEquals(COMMIT_FAILED, KOMETacticalMembershipService.assignBuild(data, "B1", "A", revision).getStatus());
            assertEquals(before, save(data)); assertEquals(revision, revision(data)); assertEquals(initiallyDirty, data.isDirty());
        }
    }

    @Test public void internalHookRejectsStaleOrDefinitionChangingCandidatesAndDetachesPublishedCopy() {
        KOMEWorldData data = world(); KOMETacticalConfiguration candidate = data.getTacticalConfigurationSnapshot();
        long before = candidate.getRevision(); candidate.assignBuild("B1", "A");
        try { data.publishTacticalMembership(before - 1, candidate); fail(); } catch (IllegalStateException expected) { }
        KOMETacticalConfiguration wrong = data.getTacticalConfigurationSnapshot();
        wrong.replaceComplex(complex("A", "T100", KOMETileTestResources.dimension()));
        try { data.publishTacticalMembership(before, wrong); fail(); } catch (IllegalArgumentException expected) { }
        assertFalse(data.isDirty()); assertEquals(before, revision(data));
        data.publishTacticalMembership(before, candidate);
        candidate.unassignBuild("B1"); data.setDirty(false);
        assertEquals("A", data.getTacticalConfigurationSnapshot().findAssignedComplexId("B1").get());
        assertEquals(before + 1, revision(data)); assertFalse(data.isDirty());
    }

    @Test public void exhaustedRevisionAndWriteBlockedWorldRejectWithoutChanges() {
        KOMEWorldData data = world(); NBTTagCompound root = save(data);
        root.getCompoundTag("TacticalConfiguration").setLong("Revision", Long.MAX_VALUE); data.readFromNBT(root); data.setDirty(false);
        assertRejected(data, "B1", "A", REVISION_EXHAUSTED);
        root.removeTag("TacticalConfiguration");
        try { data.readFromNBT(root); fail(); } catch (IllegalStateException expected) { }
        assertEquals(WRITE_BLOCKED, KOMETacticalMembershipService.assignBuild(data, "B1", "A", Long.MAX_VALUE).getStatus());
        assertEquals(Long.MAX_VALUE, revision(data)); assertFalse(data.isDirty());
    }

    private static void assertRejected(KOMEWorldData data, String build, String complex, KOMETacticalMembershipService.Status status) {
        data.setDirty(false); long beforeRevision = revision(data); NBTTagCompound before = save(data);
        assertEquals(status, KOMETacticalMembershipService.assignBuild(data, build, complex, beforeRevision).getStatus());
        assertEquals(before, save(data)); assertEquals(beforeRevision, revision(data)); assertFalse(data.isDirty());
    }

    private static void assertRoundTrip(KOMEWorldData data) {
        KOMEWorldData loaded = new KOMEWorldData("loaded"); loaded.readFromNBT(save(data));
        assertEquals(KOMETacticalConfigurationCodec.encode(data.getTacticalConfigurationSnapshot()),
            KOMETacticalConfigurationCodec.encode(loaded.getTacticalConfigurationSnapshot()));
    }

    private static final class CountingWorld extends KOMEWorldData {
        private int dirtyCalls;
        private boolean failDirty;
        @Override public void markDirty() {
            dirtyCalls++; super.markDirty();
            if (failDirty) throw new IllegalStateException("Injected publication failure.");
        }
    }
}
