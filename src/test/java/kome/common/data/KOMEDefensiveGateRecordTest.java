package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class KOMEDefensiveGateRecordTest {
    @Test public void defensiveBuildCanPersistWithZeroGateRecords() {
        KOMEPlayerBuild build = build(KOMEBuildType.DEFENSIVE);
        KOMEPlayerBuild restored = new KOMEPlayerBuild();
        restored.readFromNBT(build.writeToNBT());
        assertTrue(restored.getDefensiveGateRecords().isEmpty());
        assertEquals(0L, restored.getDefensiveGateRecordSequence());
    }

    @Test public void defensiveGateCollectionIsReadOnlyToOrdinaryCallers() {
        KOMEPlayerBuild build = build(KOMEBuildType.DEFENSIVE);
        addRecord(build);
        try {
            build.getDefensiveGateRecords().clear();
            fail("Expected a read-only defensive gate record collection");
        } catch (UnsupportedOperationException expected) {
        }
    }

    @Test(expected = IllegalStateException.class)
    public void normalBuildCannotAcquireDefensiveGateRecords() {
        KOMEPlayerBuild normal = build(KOMEBuildType.NORMAL);
        KOMEDefensiveGateRecord record = new KOMEDefensiveGateRecord();
        record.id = "G1";
        normal.addDefensiveGateRecord(record);
    }

    @Test public void normalBuildRejectsInjectedDefensiveGateRecords() {
        KOMEPlayerBuild defensive = build(KOMEBuildType.DEFENSIVE);
        addRecord(defensive);
        NBTTagCompound savedBuild = defensive.writeToNBT();
        savedBuild.setString("BuildType", "NORMAL");
        try {
            new KOMEPlayerBuild().readFromNBT(savedBuild);
            fail("Expected a NORMAL Build with defensive gate data to fail closed");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("DEFENSIVE"));
        }
    }

    @Test public void gateRecordIdsAllocateMonotonically() {
        KOMEPlayerBuild build = build(KOMEBuildType.DEFENSIVE);
        assertEquals("G1", addRecord(build).id);
        assertEquals("G2", addRecord(build).id);
        assertEquals("G3", addRecord(build).id);
        assertEquals(3L, build.getDefensiveGateRecordSequence());
    }

    @Test public void deletionGapDoesNotReuseAnId() {
        KOMEPlayerBuild build = build(KOMEBuildType.DEFENSIVE);
        addRecord(build);
        addRecord(build);
        addRecord(build);
        assertTrue(build.removeDefensiveGateRecord("G1"));
        assertEquals("G4", addRecord(build).id);
    }

    @Test public void deletingHighestIdDoesNotReuseIt() {
        KOMEPlayerBuild build = build(KOMEBuildType.DEFENSIVE);
        addRecord(build);
        addRecord(build);
        addRecord(build);
        assertTrue(build.removeDefensiveGateRecord("G3"));
        assertEquals("G4", addRecord(build).id);
    }

    @Test public void nbtRoundTripPreservesHighWaterAfterAllRecordsAreRemoved() {
        KOMEPlayerBuild build = build(KOMEBuildType.DEFENSIVE);
        addRecord(build);
        addRecord(build);
        assertTrue(build.removeDefensiveGateRecord("G1"));
        assertTrue(build.removeDefensiveGateRecord("G2"));

        KOMEPlayerBuild restored = new KOMEPlayerBuild();
        restored.readFromNBT(build.writeToNBT());
        assertTrue(restored.getDefensiveGateRecords().isEmpty());
        assertEquals(2L, restored.getDefensiveGateRecordSequence());
        assertEquals("G3", addRecord(restored).id);
    }

    @Test public void missingCanonicalSequenceIsNotMigrated() {
        NBTTagCompound nbt = build(KOMEBuildType.DEFENSIVE).writeToNBT();
        nbt.removeTag("DefensiveGateRecordSequence");
        NBTTagList records = new NBTTagList();
        records.appendTag(recordNbt("G10"));
        records.appendTag(recordNbt("G2"));
        nbt.setTag("DefensiveGateRecords", records);

        try {
            new KOMEPlayerBuild().readFromNBT(nbt);
            fail("Expected missing canonical gate sequence to fail closed");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("DefensiveGateRecordSequence"));
        }
    }

    @Test public void malformedIdsDoNotLowerOrCorruptPersistedSequence() {
        NBTTagCompound nbt = build(KOMEBuildType.DEFENSIVE).writeToNBT();
        nbt.setLong("DefensiveGateRecordSequence", 7L);
        NBTTagList records = new NBTTagList();
        records.appendTag(recordNbt("legacy-gate"));
        records.appendTag(recordNbt("G3"));
        records.appendTag(recordNbt("G0"));
        records.appendTag(recordNbt("g20"));
        records.appendTag(recordNbt("G999999999999999999999999999999"));
        nbt.setTag("DefensiveGateRecords", records);

        KOMEPlayerBuild restored = new KOMEPlayerBuild();
        restored.readFromNBT(nbt);
        assertEquals(7L, restored.getDefensiveGateRecordSequence());
        assertEquals("G8", addRecord(restored).id);
    }

    @Test public void duplicateRecordIdsRemainInvalid() {
        KOMEPlayerBuild build = build(KOMEBuildType.DEFENSIVE);
        KOMEDefensiveGateRecord first = new KOMEDefensiveGateRecord();
        first.id = "G1";
        build.addDefensiveGateRecord(first);
        KOMEDefensiveGateRecord duplicate = new KOMEDefensiveGateRecord();
        duplicate.id = "G1";
        try {
            build.addDefensiveGateRecord(duplicate);
            fail("Expected a duplicate defensive gate record ID to be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("G1"));
        }
    }

    @Test public void uuidOnlyPartialBindingIsNotComplete() {
        NBTTagCompound nbt = recordNbt("G1");
        nbt.setString("GateUuid", UUID.randomUUID().toString());
        assertFalse(readRecord(nbt).hasPhysicalBinding());
    }

    @Test public void bindingWithoutDimensionIsNotComplete() {
        NBTTagCompound nbt = completeBindingNbt("G1");
        nbt.removeTag("GateDimension");
        assertFalse(readRecord(nbt).hasPhysicalBinding());
    }

    @Test public void bindingWithoutValidRevisionIsNotComplete() {
        NBTTagCompound missingRevision = completeBindingNbt("G1");
        missingRevision.removeTag("CapturedStructureRevision");
        assertFalse(readRecord(missingRevision).hasPhysicalBinding());

        NBTTagCompound zeroRevision = completeBindingNbt("G2");
        zeroRevision.setInteger("CapturedStructureRevision", 0);
        assertFalse(readRecord(zeroRevision).hasPhysicalBinding());
    }

    @Test public void completeCompositeBindingMetadataIsRecognized() {
        KOMEDefensiveGateRecord record = readRecord(completeBindingNbt("G1"));
        assertTrue(record.hasPhysicalBinding());
        assertEquals(Integer.valueOf(0), record.getGateDimension());
        assertEquals(Integer.valueOf(0), record.getControllerX());
        assertEquals(Integer.valueOf(64), record.getControllerY());
        assertEquals(Integer.valueOf(-40), record.getControllerZ());
        assertEquals(7, record.getCapturedStructureRevision());
    }

    @Test public void logicalIdentityBindingDimensionsOverrideAndSequenceSurviveWorldRoundTrip() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEPlayerBuild build = build(KOMEBuildType.DEFENSIVE);
        data.builds.put(build.id, build);
        UUID actor = UUID.randomUUID();
        UUID gateUuid = UUID.randomUUID();
        KOMEDefensiveGateRecord first = addRecord(build);
        KOMEDefensiveGateRecord second = addRecord(build);
        assertEquals("G1", first.id);
        assertEquals("G2", second.id);

        applyCompleteBinding(first, gateUuid, 100, 120, 64, -40, 7);
        first.detectedOrientation = "WIDTH_X";
        first.detectedWidth = 6;
        first.detectedHeight = 8;
        first.detectedProjectedArea = 46;
        first.dimensionDetectionStatus = KOMEDefensiveGateRecord.DimensionDetectionStatus.AMBIGUOUS;
        first.setAdminConfirmedDimensions(5, 7, 7, actor, "Admin", 20L,
            "Irregular opening measured in world");
        first.setAdminMaxHpOverride(10000, actor, "Admin", 21L, "Reviewed fortress value");
        applyCompleteBinding(second, UUID.randomUUID(), 0, 0, 64, 0, 1);

        NBTTagCompound saved = new NBTTagCompound();
        data.writeToNBT(saved);
        assertEquals(KOMEWorldData.BUILD_DATA_SCHEMA_VERSION, saved.getInteger("BuildDataSchemaVersion"));
        KOMEWorldData restored = new KOMEWorldData("test");
        restored.readFromNBT(saved);
        KOMEPlayerBuild loadedBuild = restored.getBuild(build.id);
        assertNotNull(loadedBuild);
        assertEquals(2, loadedBuild.getDefensiveGateRecords().size());
        assertEquals(2L, loadedBuild.getDefensiveGateRecordSequence());
        KOMEDefensiveGateRecord loaded = loadedBuild.getDefensiveGateRecord("G1");
        assertNotNull(loaded);
        assertTrue(loaded.hasPhysicalBinding());
        assertEquals(gateUuid, loaded.getGateUuid());
        assertEquals(Integer.valueOf(100), loaded.getGateDimension());
        assertEquals(Integer.valueOf(120), loaded.getControllerX());
        assertEquals(Integer.valueOf(64), loaded.getControllerY());
        assertEquals(Integer.valueOf(-40), loaded.getControllerZ());
        assertEquals(7, loaded.getCapturedStructureRevision());
        assertEquals("WIDTH_X", loaded.getDetectedOrientation());
        assertEquals(6, loaded.getDetectedWidth());
        assertEquals(8, loaded.getDetectedHeight());
        assertEquals(46, loaded.getDetectedProjectedArea());
        assertEquals(KOMEDefensiveGateRecord.DimensionDetectionStatus.AMBIGUOUS,
            loaded.getDimensionDetectionStatus());
        assertEquals(Integer.valueOf(5), loaded.getAdminConfirmedWidth());
        assertEquals(Integer.valueOf(7), loaded.getAdminConfirmedHeight());
        assertEquals(7, loaded.getAdminConfirmationStructureRevision());
        assertEquals(actor, loaded.getDimensionsConfirmedByUuid());
        assertEquals("Irregular opening measured in world", loaded.getDimensionConfirmationReason());
        assertEquals(KOMEDefensiveGateRecord.EffectiveDimensionProvenance.ADMIN_CONFIRMED,
            loaded.effectiveDimensionProvenance());
        assertEquals(5, loaded.effectiveWidth());
        assertEquals(7, loaded.effectiveHeight());
        assertEquals(Integer.valueOf(10000), loaded.getAdminMaxHpOverride());
        assertEquals(actor, loaded.getMaxHpOverrideByUuid());
        assertEquals("Reviewed fortress value", loaded.getMaxHpOverrideReason());
    }

    @Test public void manualDimensionsBecomeUnavailableWhenStructureRevisionChanges() {
        KOMEDefensiveGateRecord record = new KOMEDefensiveGateRecord();
        record.capturedStructureRevision = 4;
        record.dimensionDetectionStatus = KOMEDefensiveGateRecord.DimensionDetectionStatus.AMBIGUOUS;
        record.setAdminConfirmedDimensions(6, 8, 4, UUID.randomUUID(), "Admin", 10L, "Measured");
        assertEquals(KOMEDefensiveGateRecord.EffectiveDimensionProvenance.ADMIN_CONFIRMED,
            record.effectiveDimensionProvenance());
        record.capturedStructureRevision = 5;
        assertEquals(KOMEDefensiveGateRecord.EffectiveDimensionProvenance.UNAVAILABLE,
            record.effectiveDimensionProvenance());
        assertEquals(0, record.effectiveWidth());
    }

    @Test public void reliableDetectedDimensionsRetainAutomaticProvenance() {
        KOMEDefensiveGateRecord record = new KOMEDefensiveGateRecord();
        record.id = "G1";
        record.detectedOrientation = "WIDTH_Z";
        record.detectedWidth = 3;
        record.detectedHeight = 4;
        record.detectedProjectedArea = 12;
        record.dimensionDetectionStatus = KOMEDefensiveGateRecord.DimensionDetectionStatus.RELIABLE;
        KOMEDefensiveGateRecord restored = new KOMEDefensiveGateRecord();
        restored.readFromNBT(record.writeToNBT());
        assertEquals(KOMEDefensiveGateRecord.EffectiveDimensionProvenance.AUTOMATIC,
            restored.effectiveDimensionProvenance());
        assertEquals(3, restored.effectiveWidth());
        assertEquals(4, restored.effectiveHeight());
    }

    @Test public void malformedReliableProjectedGeometryLoadsAsInvalid() {
        NBTTagCompound nbt = recordNbt("G1");
        nbt.setInteger("DetectedWidth", 3);
        nbt.setInteger("DetectedHeight", 4);
        nbt.setInteger("DetectedProjectedArea", 11);
        nbt.setString("DimensionDetectionStatus", "RELIABLE");
        KOMEDefensiveGateRecord restored = readRecord(nbt);
        assertEquals(KOMEDefensiveGateRecord.DimensionDetectionStatus.INVALID,
            restored.getDimensionDetectionStatus());
        assertEquals(KOMEDefensiveGateRecord.EffectiveDimensionProvenance.UNAVAILABLE,
            restored.effectiveDimensionProvenance());
    }

    @Test public void clearedOverrideHasNoPersistentValueOrAuditMetadata() {
        KOMEDefensiveGateRecord record = new KOMEDefensiveGateRecord();
        record.id = "G1";
        record.setAdminMaxHpOverride(9000, UUID.randomUUID(), "Admin", 10L, "Temporary");
        record.clearAdminMaxHpOverride(11L);
        NBTTagCompound nbt = record.writeToNBT();
        assertFalse(nbt.hasKey("AdminMaxHpOverride"));

        KOMEDefensiveGateRecord restored = readRecord(nbt);
        assertFalse(restored.hasAdminMaxHpOverride());
        assertNull(restored.getAdminMaxHpOverride());
        assertNull(restored.getMaxHpOverrideByUuid());
        assertEquals("", restored.getMaxHpOverrideReason());
    }

    private static KOMEDefensiveGateRecord addRecord(KOMEPlayerBuild build) {
        KOMEDefensiveGateRecord record = new KOMEDefensiveGateRecord();
        record.id = build.allocateDefensiveGateRecordId();
        build.addDefensiveGateRecord(record);
        return record;
    }

    private static NBTTagCompound recordNbt(String id) {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString("Id", id);
        return nbt;
    }

    private static NBTTagCompound completeBindingNbt(String id) {
        NBTTagCompound nbt = recordNbt(id);
        nbt.setString("GateUuid", UUID.randomUUID().toString());
        nbt.setInteger("GateDimension", 0);
        nbt.setInteger("ControllerX", 0);
        nbt.setInteger("ControllerY", 64);
        nbt.setInteger("ControllerZ", -40);
        nbt.setInteger("CapturedStructureRevision", 7);
        return nbt;
    }

    private static KOMEDefensiveGateRecord readRecord(NBTTagCompound nbt) {
        KOMEDefensiveGateRecord record = new KOMEDefensiveGateRecord();
        record.readFromNBT(nbt);
        return record;
    }

    private static void applyCompleteBinding(KOMEDefensiveGateRecord record, UUID gateUuid,
            int dimension, int x, int y, int z, int revision) {
        record.gateUuid = gateUuid;
        record.gateDimension = Integer.valueOf(dimension);
        record.controllerX = Integer.valueOf(x);
        record.controllerY = Integer.valueOf(y);
        record.controllerZ = Integer.valueOf(z);
        record.capturedStructureRevision = revision;
    }

    private static KOMEPlayerBuild build(KOMEBuildType type) {
        KOMEPlayerBuild build = new KOMEPlayerBuild();
        build.id = "B1";
        build.displayName = "Outer Wall";
        build.tileId = "T100";
        build.populationFaction = "gondor";
        build.type = type;
        build.active = true;
        return build;
    }
}
