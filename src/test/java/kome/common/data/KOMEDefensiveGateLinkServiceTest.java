package kome.common.data;

import com.enovak.lotrmoremobs.siege.gate.GateHinge;
import com.enovak.lotrmoremobs.siege.gate.GateOpeningDirection;
import com.enovak.lotrmoremobs.siege.gate.GateOrientation;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class KOMEDefensiveGateLinkServiceTest {
    @org.junit.Rule public final KOMETileTestResources geometry = new KOMETileTestResources();
    @Test public void atomicLinkAcceptsAnyDefensiveBuildAndAllocatesExactlyOneRecord() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEPlayerBuild build = addBuild(data, "B1", KOMEBuildType.DEFENSIVE, 900, 5000);
        UUID gateUuid = UUID.randomUUID();
        KOMEDefensiveGateLinkService.OperationResult result = link(data, build,
            inspection(gateUuid, 1), true, 10L);
        assertTrue(result.isSuccessful());
        assertEquals("G1", result.getRecord().getId());
        assertEquals(gateUuid, result.getRecord().getGateUuid());
        assertEquals(1, build.getDefensiveGateRecords().size());
        assertEquals(1L, build.getDefensiveGateRecordSequence());
    }

    @Test public void failedValidationCreatesNoPlaceholderAndConsumesNoId() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEPlayerBuild normal = addBuild(data, "B1", KOMEBuildType.NORMAL, 0, 0);
        assertFalse(link(data, normal, inspection(UUID.randomUUID(), 1), true, 10L)
            .isSuccessful());
        assertTrue(normal.getDefensiveGateRecords().isEmpty());
        assertEquals(0L, normal.getDefensiveGateRecordSequence());

        KOMEPlayerBuild defensive = addBuild(data, "B2", KOMEBuildType.DEFENSIVE, 0, 0);
        assertFalse(link(data, defensive, inspection(UUID.randomUUID(), 1), false, 11L)
            .isSuccessful());
        assertTrue(defensive.getDefensiveGateRecords().isEmpty());
        assertEquals(0L, defensive.getDefensiveGateRecordSequence());
    }

    @Test public void initialPhysicalHealthIsAppliedBeforeLinkReportsSuccess() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEPlayerBuild build = addBuild(data, "B1", KOMEBuildType.DEFENSIVE, 0, 0);
        RecordingHealthApplication physical = new RecordingHealthApplication(true, true);

        KOMEDefensiveGateLinkService.OperationResult result =
            KOMEDefensiveGateLinkService.linkAndInitializePhysicalHealth(data, build,
                inspection(UUID.randomUUID(), 1), null, "Admin", true, 10L, physical);

        assertTrue(result.isSuccessful());
        assertTrue(physical.canApplyCalled);
        assertTrue(physical.applyCalled);
        assertEquals(1, build.getDefensiveGateRecords().size());
        assertEquals(result.getRecord(), build.getDefensiveGateRecord("G1"));
    }

    @Test public void physicalHealthFailureRollsBackNewRecordAndReportsFailure() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEPlayerBuild build = addBuild(data, "B1", KOMEBuildType.DEFENSIVE, 0, 0);
        RecordingHealthApplication physical = new RecordingHealthApplication(true, false);

        KOMEDefensiveGateLinkService.OperationResult result =
            KOMEDefensiveGateLinkService.linkAndInitializePhysicalHealth(data, build,
                inspection(UUID.randomUUID(), 1), null, "Admin", true, 10L, physical);

        assertFalse(result.isSuccessful());
        assertTrue(result.getMessage().contains("rolled back"));
        assertTrue(build.getDefensiveGateRecords().isEmpty());
        assertEquals(1L, build.getDefensiveGateRecordSequence());
        assertTrue(data.isDirty());
    }

    @Test public void ineligiblePhysicalTargetFailsBeforeRecordAllocation() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEPlayerBuild build = addBuild(data, "B1", KOMEBuildType.DEFENSIVE, 0, 0);
        RecordingHealthApplication physical = new RecordingHealthApplication(false, true);

        KOMEDefensiveGateLinkService.OperationResult result =
            KOMEDefensiveGateLinkService.linkAndInitializePhysicalHealth(data, build,
                inspection(UUID.randomUUID(), 1), null, "Admin", true, 10L, physical);

        assertFalse(result.isSuccessful());
        assertFalse(physical.applyCalled);
        assertTrue(build.getDefensiveGateRecords().isEmpty());
        assertEquals(0L, build.getDefensiveGateRecordSequence());
    }

    @Test public void physicalInitializationCannotBypassAdministratorValidation() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEPlayerBuild build = addBuild(data, "B1", KOMEBuildType.DEFENSIVE, 0, 0);
        RecordingHealthApplication physical = new RecordingHealthApplication(true, true);

        KOMEDefensiveGateLinkService.OperationResult result =
            KOMEDefensiveGateLinkService.linkAndInitializePhysicalHealth(data, build,
                inspection(UUID.randomUUID(), 1), null, "Player", false, 10L, physical);

        assertFalse(result.isSuccessful());
        assertFalse(physical.canApplyCalled);
        assertFalse(physical.applyCalled);
        assertTrue(build.getDefensiveGateRecords().isEmpty());
        assertEquals(0L, build.getDefensiveGateRecordSequence());
    }

    @Test public void writeBlockedWorldRejectsLinkBeforeMutatingCanonicalBuild() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEPlayerBuild build = addBuild(data, "B1", KOMEBuildType.DEFENSIVE, 0, 0);
        NBTTagCompound unsupported = new NBTTagCompound();
        unsupported.setString("RetiredDevelopmentData", "present");
        try {
            data.readFromNBT(unsupported);
        } catch (IllegalStateException expected) {
        }
        assertTrue(data.isWriteBlocked());

        KOMEDefensiveGateLinkService.OperationResult result = link(data, build,
            inspection(UUID.randomUUID(), 1), true, 10L);

        assertFalse(result.isSuccessful());
        assertTrue(result.getMessage().contains("write-blocked"));
        assertTrue(build.getDefensiveGateRecords().isEmpty());
        assertEquals(0L, build.getDefensiveGateRecordSequence());
    }

    @Test public void physicalUuidCannotBeActivelyLinkedToTwoBuilds() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEPlayerBuild first = addBuild(data, "B1", KOMEBuildType.DEFENSIVE, 0, 0);
        KOMEPlayerBuild second = addBuild(data, "B2", KOMEBuildType.DEFENSIVE, 0, 0);
        UUID gateUuid = UUID.randomUUID();
        assertTrue(link(data, first, inspection(gateUuid, 1), true, 10L).isSuccessful());
        assertFalse(link(data, second, inspection(gateUuid, 1), true, 11L).isSuccessful());
        assertTrue(second.getDefensiveGateRecords().isEmpty());
        assertEquals(0L, second.getDefensiveGateRecordSequence());
    }

    @Test public void unlinkReleasesPhysicalGateAndHighWaterRemainsMonotonic() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEPlayerBuild first = addBuild(data, "B1", KOMEBuildType.DEFENSIVE, 0, 0);
        KOMEPlayerBuild second = addBuild(data, "B2", KOMEBuildType.DEFENSIVE, 0, 0);
        UUID gateUuid = UUID.randomUUID();
        KOMEDefensiveGateRecord firstRecord = link(data, first, inspection(gateUuid, 1),
            true, 10L).getRecord();
        assertTrue(KOMEDefensiveGateLinkService.unlink(data, first, firstRecord.getId(),
            null, "Admin", true, 11L).isSuccessful());
        assertNull(KOMEDefensiveGateLinkService.findActiveLinkByPhysicalGateUuid(data, gateUuid));
        assertTrue(link(data, second, inspection(gateUuid, 1), true, 12L).isSuccessful());
        assertEquals("G2", link(data, first, inspectionAt(UUID.randomUUID(), 1, 50), true, 13L)
            .getRecord().getId());
    }

    @Test public void brokenBindingCanBeUnlinkedWithoutPhysicalInspection() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEPlayerBuild build = addBuild(data, "B1", KOMEBuildType.DEFENSIVE, 0, 0);
        KOMEDefensiveGateRecord record = link(data, build, inspection(UUID.randomUUID(), 1), true, 10L)
            .getRecord();
        // The service intentionally receives no replacement/current controller for Unlink.
        assertTrue(KOMEDefensiveGateLinkService.unlink(data, build, record.getId(),
            null, "Admin", true, 11L).isSuccessful());
        assertTrue(build.getDefensiveGateRecords().isEmpty());
        assertEquals(1L, build.getDefensiveGateRecordSequence());
    }

    @Test public void unlinkRejectsRecordFromAnotherBuild() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEPlayerBuild first = addBuild(data, "B1", KOMEBuildType.DEFENSIVE, 0, 0);
        KOMEPlayerBuild second = addBuild(data, "B2", KOMEBuildType.DEFENSIVE, 0, 0);
        KOMEDefensiveGateRecord record = link(data, first, inspection(UUID.randomUUID(), 1), true, 10L)
            .getRecord();
        assertFalse(KOMEDefensiveGateLinkService.unlink(data, second, record.getId(),
            null, "Admin", true, 11L).isSuccessful());
        assertEquals(1, first.getDefensiveGateRecords().size());
    }

    @Test public void sameUuidRevisionRefreshPreservesRecordAndStalesConfirmation() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEPlayerBuild build = addBuild(data, "B1", KOMEBuildType.DEFENSIVE, 0, 0);
        UUID gateUuid = UUID.randomUUID();
        KOMEPhysicalGateInspection.Result ambiguous1 = ambiguousInspection(gateUuid, 1);
        KOMEDefensiveGateRecord record = link(data, build, ambiguous1, true, 10L).getRecord();
        assertTrue(KOMEDefensiveGateLinkService.confirmDimensions(data, build, record.getId(),
            ambiguous1, 5, 7, null, "Admin", true, 11L).isSuccessful());
        assertTrue(record.hasCurrentAdminConfirmedDimensions());

        KOMEDefensiveGateLinkService.OperationResult refreshed =
            KOMEDefensiveGateLinkService.refresh(data, build, record.getId(),
                ambiguousInspection(gateUuid, 2), null, "Admin", true, 12L);
        assertTrue(refreshed.isSuccessful());
        assertEquals("G1", refreshed.getRecord().getId());
        assertEquals(2, refreshed.getRecord().getCapturedStructureRevision());
        assertFalse(refreshed.getRecord().hasCurrentAdminConfirmedDimensions());
        assertNotNull(refreshed.getRecord().getAdminConfirmedWidth());
    }

    @Test public void newUuidNeverSilentlyRefreshesOldLogicalRecord() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEPlayerBuild build = addBuild(data, "B1", KOMEBuildType.DEFENSIVE, 0, 0);
        UUID oldUuid = UUID.randomUUID();
        KOMEDefensiveGateRecord record = link(data, build, inspection(oldUuid, 1), true, 10L)
            .getRecord();
        assertFalse(KOMEDefensiveGateLinkService.refresh(data, build, record.getId(),
            inspection(UUID.randomUUID(), 2), null, "Admin", true, 11L).isSuccessful());
        assertEquals(oldUuid, record.getGateUuid());
        assertEquals(1, record.getCapturedStructureRevision());
    }

    @Test public void replacementAtSameControllerCannotCreateNewRecordInsteadOfRelink() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEPlayerBuild build = addBuild(data, "B1", KOMEBuildType.DEFENSIVE, 0, 0);
        KOMEDefensiveGateRecord old = link(data, build, inspection(UUID.randomUUID(), 1),
            true, 10L).getRecord();
        KOMEDefensiveGateLinkService.OperationResult freshLink = link(data, build,
            inspection(UUID.randomUUID(), 1), true, 11L);
        assertFalse(freshLink.isSuccessful());
        assertTrue(freshLink.getMessage().contains("Relink"));
        assertEquals(1, build.getDefensiveGateRecords().size());
        assertEquals("G1", old.getId());
        assertEquals(1L, build.getDefensiveGateRecordSequence());
    }

    @Test public void relinkPreservesLogicalIdAndOverrideButReplacesBindingAndConfirmation() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEPlayerBuild build = addBuild(data, "B1", KOMEBuildType.DEFENSIVE, 0, 0);
        UUID oldUuid = UUID.randomUUID();
        KOMEPhysicalGateInspection.Result oldInspection = ambiguousInspection(oldUuid, 1);
        KOMEDefensiveGateRecord record = link(data, build, oldInspection, true, 10L).getRecord();
        KOMEDefensiveGateLinkService.confirmDimensions(data, build, record.getId(), oldInspection,
            5, 7, null, "Admin", true, 11L);
        record.setAdminMaxHpOverride(9000, null, "Admin", 12L, "Keep across rebuild");
        UUID replacementUuid = UUID.randomUUID();
        KOMEDefensiveGateLinkService.OperationResult result =
            KOMEDefensiveGateLinkService.relink(data, build, record.getId(),
                inspection(replacementUuid, 3), true, null, "Admin", true, 13L);
        assertTrue(result.isSuccessful());
        assertEquals("G1", record.getId());
        assertEquals(replacementUuid, record.getGateUuid());
        assertEquals(Integer.valueOf(9000), record.getAdminMaxHpOverride());
        assertNull(record.getAdminConfirmedWidth());
    }

    @Test public void relinkAppliesRecalculatedPhysicalHealthBeforeChangingLogicalBinding() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEPlayerBuild build = addBuild(data, "B1", KOMEBuildType.DEFENSIVE, 0, 0);
        UUID oldUuid = UUID.randomUUID();
        KOMEDefensiveGateRecord record = link(data, build, inspection(oldUuid, 1), true, 10L)
            .getRecord();
        UUID replacementUuid = UUID.randomUUID();
        RecordingHealthApplication physical = new RecordingHealthApplication(true, true);

        KOMEDefensiveGateLinkService.OperationResult result =
            KOMEDefensiveGateLinkService.relinkAndApplyPhysicalHealth(data, build,
                record.getId(), inspection(replacementUuid, 2), true, null, "Admin", true,
                11L, physical);

        assertTrue(result.isSuccessful());
        assertTrue(physical.canApplyCalled);
        assertTrue(physical.applyCalled);
        assertEquals(replacementUuid, record.getGateUuid());
    }

    @Test public void failedRelinkHealthApplicationLeavesLogicalBindingUntouched() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEPlayerBuild build = addBuild(data, "B1", KOMEBuildType.DEFENSIVE, 0, 0);
        UUID oldUuid = UUID.randomUUID();
        KOMEDefensiveGateRecord record = link(data, build, inspection(oldUuid, 1), true, 10L)
            .getRecord();
        RecordingHealthApplication physical = new RecordingHealthApplication(true, false);

        KOMEDefensiveGateLinkService.OperationResult result =
            KOMEDefensiveGateLinkService.relinkAndApplyPhysicalHealth(data, build,
                record.getId(), inspection(UUID.randomUUID(), 2), true, null, "Admin", true,
                11L, physical);

        assertFalse(result.isSuccessful());
        assertTrue(physical.canApplyCalled);
        assertTrue(physical.applyCalled);
        assertEquals(oldUuid, record.getGateUuid());
        assertEquals(1, record.getCapturedStructureRevision());
    }

    @Test public void relinkRejectsTargetAlreadyLinkedElsewhere() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEPlayerBuild first = addBuild(data, "B1", KOMEBuildType.DEFENSIVE, 0, 0);
        KOMEPlayerBuild second = addBuild(data, "B2", KOMEBuildType.DEFENSIVE, 0, 0);
        KOMEDefensiveGateRecord old = link(data, first, inspection(UUID.randomUUID(), 1),
            true, 10L).getRecord();
        UUID occupied = UUID.randomUUID();
        link(data, second, inspectionAt(occupied, 1, 30), true, 11L);
        assertFalse(KOMEDefensiveGateLinkService.relink(data, first, old.getId(),
            inspectionAt(occupied, 2, 30), true, null, "Admin", true, 12L).isSuccessful());
    }

    @Test public void relinkRoundTripPreservesStableLogicalIdentityAndOverride() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEPlayerBuild build = addBuild(data, "B1", KOMEBuildType.DEFENSIVE, 0, 0);
        KOMEDefensiveGateRecord record = link(data, build, inspection(UUID.randomUUID(), 1),
            true, 10L).getRecord();
        record.setAdminMaxHpOverride(7777, null, "Admin", 11L, "Persistent override");
        UUID replacement = UUID.randomUUID();
        assertTrue(KOMEDefensiveGateLinkService.relink(data, build, record.getId(),
            inspection(replacement, 4), true, null, "Admin", true, 12L).isSuccessful());

        net.minecraft.nbt.NBTTagCompound saved = new net.minecraft.nbt.NBTTagCompound();
        data.writeToNBT(saved);
        KOMEWorldData restored = new KOMEWorldData("test");
        restored.readFromNBT(saved);
        KOMEDefensiveGateRecord loaded = restored.getBuild("B1").getDefensiveGateRecord("G1");
        assertNotNull(loaded);
        assertEquals(replacement, loaded.getGateUuid());
        assertEquals(4, loaded.getCapturedStructureRevision());
        assertEquals(Integer.valueOf(7777), loaded.getAdminMaxHpOverride());
        assertEquals(1L, restored.getBuild("B1").getDefensiveGateRecordSequence());
    }

    @Test public void deletingParentClearsActiveLinksWithoutReusingIds() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEPlayerBuild build = addBuild(data, "B1", KOMEBuildType.DEFENSIVE, 0, 0);
        UUID gateUuid = UUID.randomUUID();
        link(data, build, inspection(gateUuid, 1), true, 10L);
        build.managerUuid = UUID.randomUUID();
        assertTrue(KOMEBuildService.deleteBuild(data, build, build.managerUuid, "Manager", false,
            "deleted", 20L).allowed);
        assertTrue(build.getDefensiveGateRecords().isEmpty());
        assertEquals(1L, build.getDefensiveGateRecordSequence());
        assertNull(KOMEDefensiveGateLinkService.findActiveLinkByPhysicalGateUuid(data, gateUuid));
    }

    private static KOMEDefensiveGateLinkService.OperationResult link(KOMEWorldData data,
            KOMEPlayerBuild build, KOMEPhysicalGateInspection.Result inspection,
            boolean admin, long timestamp) {
        return KOMEDefensiveGateLinkService.link(data, build, inspection, null, "Admin",
            admin, timestamp);
    }

    private static KOMEPlayerBuild addBuild(KOMEWorldData data, String id, KOMEBuildType type,
            double x, double z) {
        KOMEPlayerBuild build = new KOMEPlayerBuild();
        build.id = id;
        build.displayName = id;
        build.tileId = "T100";
        build.populationFaction = "gondor";
        build.type = type;
        build.active = true;
        build.x = x;
        build.z = z;
        data.builds.put(id, build);
        return build;
    }

    private static KOMEPhysicalGateInspection.Result inspection(UUID uuid, int revision) {
        return inspectionAt(uuid, revision, 10);
    }

    private static KOMEPhysicalGateInspection.Result inspectionAt(UUID uuid, int revision,
            int controllerX) {
        return KOMEPhysicalGateInspection.inspect(new KOMEPhysicalGateInspection.Snapshot(
            0, controllerX, 64, 20, uuid, revision, true, false, true,
            GateOrientation.WIDTH_X, GateOpeningDirection.FORWARD,
            new GateHinge(1, 0), new GateHinge(3, 0),
            KOMEPhysicalGateInspectionTest.regularParts()));
    }

    private static KOMEPhysicalGateInspection.Result ambiguousInspection(UUID uuid, int revision) {
        java.util.List<com.enovak.lotrmoremobs.siege.gate.GatePartData> parts =
            KOMEPhysicalGateInspectionTest.regularParts();
        parts.add(new com.enovak.lotrmoremobs.siege.gate.GatePartData(1, 0, 1,
            com.enovak.lotrmoremobs.siege.gate.GateLeaf.LEFT));
        return KOMEPhysicalGateInspection.inspect(new KOMEPhysicalGateInspection.Snapshot(
            0, 10, 64, 20, uuid, revision, true, false, true,
            GateOrientation.WIDTH_X, GateOpeningDirection.FORWARD,
            new GateHinge(1, 0), new GateHinge(3, 0), parts));
    }

    private static final class RecordingHealthApplication
            implements KOMEDefensiveGateLinkService.PhysicalHealthApplication {
        private final boolean eligible;
        private final boolean result;
        private boolean canApplyCalled;
        private boolean applyCalled;

        private RecordingHealthApplication(boolean eligible, boolean result) {
            this.eligible = eligible;
            this.result = result;
        }

        public boolean canApply() {
            canApplyCalled = true;
            return eligible;
        }

        public boolean apply() {
            applyCalled = true;
            return result;
        }
    }
}
