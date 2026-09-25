package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

public class KOMEHiredUnitLiveUpdateServiceTest {
    @Test public void unchangedLoadedUnitDoesNotDirtyOrPublishEveryTick() {
        CountingWorldData data = dataWithPopulation();
        KOMEHiredUnitRecord record = unit(data, 20);
        final int[] snapshots = {0};
        KOMEHiredUnitLiveUpdateService.Observation unchanged = observation(record, 20, 1, false);
        data.resetCounts();

        for (long tick = 0L; tick < 1000L; tick++) {
            KOMEHiredUnitLiveUpdateService.Result result =
                KOMEHiredUnitLiveUpdateService.update(data, record, unchanged, tick, false,
                    sameSnapshot(snapshots));
            assertFalse(result.rejected);
            assertFalse(result.published);
        }

        assertEquals(0, data.dirtyCalls);
        assertEquals(0, data.syncCalls);
        assertEquals(5, snapshots[0]);
    }

    @Test public void multipleUnchangedUnitsStaggerPersistenceWithoutAnyIdleBroadcasts() {
        CountingWorldData data = dataWithPopulation();
        List<KOMEHiredUnitRecord> records = new ArrayList<KOMEHiredUnitRecord>();
        final int[] snapshots = {0};
        for (int i = 0; i < 48; i++) records.add(unit(data, 20));
        data.resetCounts();

        for (long tick = 0L; tick < KOMEHiredUnitLiveUpdateService.SNAPSHOT_INTERVAL_TICKS; tick++) {
            for (KOMEHiredUnitRecord record : records) {
                KOMEHiredUnitLiveUpdateService.update(data, record,
                    observation(record, 20, 1, false), tick, false, sameSnapshot(snapshots));
            }
        }

        assertEquals(records.size(), snapshots[0]);
        assertEquals(0, data.dirtyCalls);
        assertEquals(0, data.syncCalls);
    }

    @Test public void freshRegistrationSkipsDuplicateSnapshotButNeverSkipsARealChange() {
        CountingWorldData data = dataWithPopulation();
        KOMEHiredUnitRecord record = unit(data, 20);
        final int[] snapshots = {0};
        data.resetCounts();

        KOMEHiredUnitLiveUpdateService.update(data, record,
            observation(record, 20, 1, false), dueTick(record.entity), true,
            sameSnapshot(snapshots));
        assertEquals(0, snapshots[0]);
        assertEquals(0, data.dirtyCalls);
        assertEquals(0, data.syncCalls);

        KOMEHiredUnitLiveUpdateService.update(data, record,
            observation(record, 21, 2, false), dueTick(record.entity), true,
            sameSnapshot(snapshots));
        assertEquals(1, snapshots[0]);
        assertEquals(1, data.dirtyCalls);
        assertEquals(1, data.syncCalls);
    }

    @Test public void realCostChangeDebitsPersistsRecalculatesAndPublishesExactlyOnce() {
        CountingWorldData data = dataWithPopulation();
        KOMEHiredUnitRecord record = unit(data, 20);
        KOMEArmyCompany company = company(data, record);
        final int[] snapshots = {0};
        data.resetCounts();
        long before = KOMEPopulationService.getAvailablePopulationCenti(data, "gondor");

        KOMEHiredUnitLiveUpdateService.Result changed =
            KOMEHiredUnitLiveUpdateService.update(data, record,
                observation(record, 21, 2, false), 1L, false, sameSnapshot(snapshots));

        assertTrue(changed.persisted);
        assertTrue(changed.published);
        assertEquals(21, record.cost);
        assertEquals(21, record.populationSpent);
        assertEquals(21, record.baseCost);
        assertEquals(2, record.level);
        assertEquals(21, company.totalPopulation);
        assertEquals(before - 100L,
            KOMEPopulationService.getAvailablePopulationCenti(data, "gondor"));
        assertEquals(1, data.dirtyCalls);
        assertEquals(1, data.syncCalls);
        assertEquals(1, snapshots[0]);

        for (long tick = 2L; tick < 402L; tick++) {
            KOMEHiredUnitLiveUpdateService.update(data, record,
                observation(record, 21, 2, false), tick, false, sameSnapshot(snapshots));
        }
        assertEquals(1, data.dirtyCalls);
        assertEquals(1, data.syncCalls);
        assertTrue(snapshots[0] <= 3);
    }

    @Test public void changedCheckpointPersistsForRestartWithoutConquestPublication() {
        CountingWorldData data = dataWithPopulation();
        KOMEHiredUnitRecord record = unit(data, 20);
        final NBTTagCompound moved = snapshot("moved");
        data.resetCounts();
        long due = dueTick(record.entity);

        KOMEHiredUnitLiveUpdateService.Result result =
            KOMEHiredUnitLiveUpdateService.update(data, record,
                observation(record, 20, 1, false), due, false,
                new KOMEHiredUnitLiveUpdateService.SnapshotSupplier() {
                    @Override public NBTTagCompound snapshot() { return moved; }
                });

        assertTrue(result.persisted);
        assertFalse(result.published);
        assertEquals("moved", record.stationedEntityData.getString("State"));
        assertEquals(1, data.dirtyCalls);
        assertEquals(0, data.syncCalls);
        NBTTagCompound saved = new NBTTagCompound();
        data.writeToNBT(saved);
        KOMEWorldData restored = new KOMEWorldData("restored");
        restored.readFromNBT(saved);
        assertEquals("moved", restored.hiredUnits.get(record.entity)
            .stationedEntityData.getString("State"));
    }

    @Test public void mountedCategoryChangeUpdatesCompanyAndPublishesOnce() {
        CountingWorldData data = dataWithPopulation();
        KOMEHiredUnitRecord record = unit(data, 20);
        KOMEArmyCompany company = company(data, record);
        data.resetCounts();

        KOMEHiredUnitLiveUpdateService.update(data, record,
            observation(record, 45, 1, true), 1L, false, sameSnapshot(new int[1]));

        assertTrue(record.mounted);
        assertEquals(45, record.cost);
        assertEquals(45, company.totalPopulation);
        assertEquals(45, company.mountedPopulation);
        assertEquals(0, company.groundPopulation);
        assertEquals(1, data.syncCalls);
        data.resetCounts();
        KOMEHiredUnitLiveUpdateService.update(data, record,
            observation(record, 45, 1, true), 2L, false, sameSnapshot(new int[1]));
        assertEquals(0, data.dirtyCalls);
        assertEquals(0, data.syncCalls);
    }

    private static CountingWorldData dataWithPopulation() {
        CountingWorldData data = new CountingWorldData();
        KOMEPopulationService.grantCenti(data, "gondor", 100000L);
        return data;
    }

    private static KOMEHiredUnitRecord unit(KOMEWorldData data, int cost) {
        KOMEHiredUnitRecord record = new KOMEHiredUnitRecord();
        record.entity = UUID.randomUUID();
        record.owner = UUID.randomUUID();
        record.sourcePlayer = record.owner;
        record.unitName = "Guard";
        record.unitEntityId = "test.guard";
        record.cost = cost;
        record.baseCost = cost;
        record.populationSpent = cost;
        record.level = 1;
        record.currentTile = "T001";
        record.stationedEntityData = snapshot("stable");
        KOMEPopulationService.recordCombatHirePayment(record, "gondor");
        data.hiredUnits.put(record.entity, record);
        return record;
    }

    private static KOMEArmyCompany company(KOMEWorldData data, KOMEHiredUnitRecord record) {
        KOMEArmyCompany company = new KOMEArmyCompany();
        company.id = "C_" + record.entity;
        company.units.add(record.entity);
        company.totalPopulation = record.cost;
        company.groundPopulation = record.cost;
        data.armyCompanies.put(company.id, company);
        record.companyId = company.id;
        return company;
    }

    private static KOMEHiredUnitLiveUpdateService.Observation observation(
            KOMEHiredUnitRecord record, int calculatedCost, int level, boolean mounted) {
        return new KOMEHiredUnitLiveUpdateService.Observation(
            "Guard", "test.guard", level, calculatedCost, mounted, calculatedCost);
    }

    private static KOMEHiredUnitLiveUpdateService.SnapshotSupplier sameSnapshot(
            final int[] snapshots) {
        return new KOMEHiredUnitLiveUpdateService.SnapshotSupplier() {
            @Override public NBTTagCompound snapshot() {
                snapshots[0]++;
                return KOMEHiredUnitLiveUpdateServiceTest.snapshot("stable");
            }
        };
    }

    private static NBTTagCompound snapshot(String state) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("State", state);
        return tag;
    }

    private static long dueTick(UUID entityId) {
        for (long tick = 0L; tick < KOMEHiredUnitLiveUpdateService.SNAPSHOT_INTERVAL_TICKS; tick++) {
            if (KOMEHiredUnitLiveUpdateService.isSnapshotDue(entityId, tick)) return tick;
        }
        throw new AssertionError("No snapshot checkpoint found");
    }

    private static final class CountingWorldData extends KOMEWorldData {
        int dirtyCalls;
        int syncCalls;

        CountingWorldData() {
            super("live-update");
        }

        @Override public void markDirty() {
            dirtyCalls++;
            super.markDirty();
        }

        @Override public void syncConquestTiles() {
            syncCalls++;
        }

        void resetCounts() {
            dirtyCalls = 0;
            syncCalls = 0;
            setDirty(false);
        }
    }
}
