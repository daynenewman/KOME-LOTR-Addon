package kome.common.data;

import java.time.Instant;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMEMovementAllowanceTest {
    @org.junit.Rule public final KOMETileTestResources geometry = new KOMETileTestResources();
    private static final long NOW = Instant.parse("2026-01-10T12:00:00Z").toEpochMilli();

    private KOMEArmyCompany company(KOMEWorldData data, boolean mounted) {
        KOMEArmyCompany company = new KOMEArmyCompany();
        company.id = "C" + data.armyCompanies.size(); company.owner = UUID.randomUUID();
        company.currentTile = "T001"; company.faction = company.nativeFaction = "gondor";
        KOMEHiredUnitRecord unit = new KOMEHiredUnitRecord();
        unit.entity = UUID.randomUUID(); unit.owner = company.owner; unit.companyId = company.id;
        unit.currentTile = company.currentTile; unit.mounted = mounted;
        unit.unitFaction = unit.populationOwningFaction = "gondor";
        KOMEHiredUnitClassification.assignForCampaignWorkflow(unit);
        data.hiredUnits.put(unit.entity, unit); company.units.add(unit.entity);
        data.armyCompanies.put(company.id, company);
        data.recalculateCampaignCompanyComposition(company);
        KOMEMovementDayService.initializeNewCompany(company, NOW);
        return company;
    }
    private KOMEArmyMovementOrder route(KOMEArmyCompany company) {
        KOMEArmyMovementOrder order = KOMEArmyMovementOrder.newRoute(company.getTilesPerDay());
        order.companyId = company.id;
        assertEquals("A new order grants nothing", 0, order.dailyStepsRemaining);
        return order;
    }

    @Test public void defaultFootMixedAndMountedEntitlementAndImmediateSpending() throws Exception {
        try (KOMEPopulationTestConfig config = new KOMEPopulationTestConfig()) {
            KOMEWorldData data = new KOMEWorldData("allowance");
            KOMEArmyCompany foot = company(data, false), mounted = company(data, true);
            assertEquals(1, foot.movementAllowance); assertEquals(2, mounted.movementAllowance);
            mounted.groundPopulation = 1;
            assertEquals(1, mounted.getTilesPerDay());
            mounted.groundPopulation = 0;
            assertTrue(KOMEMovementDayService.depart(data, route(foot), true, () -> true));
            assertFalse(KOMEMovementDayService.depart(data, route(foot), true, () -> { fail("Exhausted company cannot depart"); return true; }));
            assertTrue(KOMEMovementDayService.depart(data, route(mounted), true, () -> true));
            assertTrue(KOMEMovementDayService.depart(data, route(mounted), true, () -> true));
            assertEquals(0, mounted.movementAllowance);
        }
    }

    @Test public void configuredEntitlementsAreUsed() throws Exception {
        try (KOMEPopulationTestConfig config = new KOMEPopulationTestConfig()) {
            config.set("movement.footOrMixedTilesPerDay", "3", "movement.fullyMountedTilesPerDay", "5");
            KOMEWorldData data = new KOMEWorldData("configured");
            assertEquals(3, company(data, false).movementAllowance);
            assertEquals(5, company(data, true).movementAllowance);
        }
    }

    @Test public void completedCancelledReplacedAndReissuedRoutesCannotGrantCredit() throws Exception {
        try (KOMEPopulationTestConfig config = new KOMEPopulationTestConfig()) {
            KOMEWorldData data = new KOMEWorldData("routes"); KOMEArmyCompany company = company(data, false);
            KOMEArmyMovementOrder first = route(company);
            assertTrue(KOMEMovementDayService.depart(data, first, true, () -> true));
            for (String terminal : new String[]{KOMEArmyMovementOrder.ARRIVED, KOMEArmyMovementOrder.CANCELLED, KOMEArmyMovementOrder.STOPPED}) {
                first.status = terminal; company.movementOrderId = "";
                assertFalse(KOMEMovementDayService.depart(data, route(company), true, () -> true));
                KOMEMovementDayService.initializeNewCompany(company, NOW);
                assertEquals(0, company.movementAllowance);
            }
        }
    }

    @Test public void rejectedDepartureDoesNotSpendOrRunWithNoCredit() throws Exception {
        try (KOMEPopulationTestConfig config = new KOMEPopulationTestConfig()) {
            KOMEWorldData data = new KOMEWorldData("reject"); KOMEArmyCompany company = company(data, true);
            NBTTagCompound before = company.writeToNBT();
            assertFalse(KOMEMovementDayService.depart(data, route(company), true, () -> false));
            assertEquals(before, company.writeToNBT());
        }
    }

    @Test public void currentBoundaryInitializationIsOnceAndLiveBoundaryIsIdempotent() throws Exception {
        try (KOMEPopulationTestConfig config = new KOMEPopulationTestConfig()) {
            KOMEWorldData data = new KOMEWorldData("days"); KOMEArmyCompany company = company(data, true);
            KOMEMovementDayService.anchor(data, NOW);
            assertFalse(KOMEMovementDayService.applyBoundary(data, company.movementBoundaryMillis));
            assertTrue(KOMEMovementDayService.depart(data, route(company), true, () -> true));
            long next = KOMEMovementDayService.schedule().nextBoundary(Instant.ofEpochMilli(data.movementBoundaryMillis)).toEpochMilli();
            assertTrue(KOMEMovementDayService.applyBoundary(data, next));
            assertEquals(2, company.movementAllowance);
            assertTrue(KOMEMovementDayService.depart(data, route(company), true, () -> true));
            data.setDirty(false);
            assertFalse(KOMEMovementDayService.applyBoundary(data, next));
            assertFalse(data.isDirty()); assertEquals(1, company.movementAllowance);
        }
    }

    @Test public void exhaustedRestartAndOfflineDaysDoNotCatchUp() throws Exception {
        try (KOMEPopulationTestConfig config = new KOMEPopulationTestConfig()) {
            KOMEWorldData data = new KOMEWorldData("restart"); KOMEArmyCompany company = company(data, false);
            KOMEMovementDayService.anchor(data, NOW);
            assertTrue(KOMEMovementDayService.depart(data, route(company), true, () -> true));
            NBTTagCompound root = new NBTTagCompound(); data.writeToNBT(root);
            assertEquals(9, root.getInteger(KOMEWorldData.KOME_DATA_SCHEMA_KEY));
            KOMEWorldData restored = new KOMEWorldData("restored"); restored.readFromNBT(root);
            KOMEArmyCompany copy = restored.armyCompanies.get(company.id);
            assertNotNull(copy); assertEquals(0, copy.movementAllowance);
            long later = NOW + 9 * 86400000L;
            KOMEMovementDayService.anchor(restored, later);
            KOMEMovementDayService.observe(restored, later);
            assertEquals(0, copy.movementAllowance);
            long next = KOMEMovementDayService.schedule().nextBoundary(Instant.ofEpochMilli(restored.movementBoundaryMillis)).toEpochMilli();
            assertTrue(KOMEMovementDayService.applyBoundary(restored, next)); assertEquals(1, copy.movementAllowance);
        }
    }

    @Test public void splitChildHasZeroAndOriginalIsCappedWithoutReset() throws Exception {
        try (KOMEPopulationTestConfig config = new KOMEPopulationTestConfig()) {
            KOMEWorldData data = new KOMEWorldData("split"); KOMEArmyCompany parent = company(data, true);
            KOMEArmyCompany child = new KOMEArmyCompany(); child.mountedPopulation = 1;
            parent.groundPopulation = 1;
            long boundary = parent.movementBoundaryMillis;
            KOMEMovementDayService.split(parent, child);
            assertEquals(1, parent.movementAllowance); assertEquals(0, child.movementAllowance);
            assertEquals(boundary, child.movementBoundaryMillis);
            KOMEMovementDayService.initializeNewCompany(child, NOW);
            assertEquals(0, child.movementAllowance);
        }
    }

    @Test public void mergeUsesMaximumNotSumAndCapsResultingComposition() throws Exception {
        try (KOMEPopulationTestConfig config = new KOMEPopulationTestConfig()) {
            KOMEWorldData data = new KOMEWorldData("merge");
            KOMEArmyCompany survivor = company(data, true), absorbed = company(data, true);
            survivor.movementAllowance = 1;
            KOMEMovementDayService.merge(survivor, absorbed);
            assertEquals(2, survivor.movementAllowance);
            survivor.groundPopulation = 1;
            KOMEMovementDayService.merge(survivor, absorbed);
            assertEquals(1, survivor.movementAllowance);
            assertEquals(absorbed.movementBoundaryMillis, survivor.movementBoundaryMillis);
        }
    }

    @Test public void schemaEightMigratesConservativelyAndSchemaNineMissingAuthorityFailsAtomically() throws Exception {
        try (KOMEPopulationTestConfig config = new KOMEPopulationTestConfig()) {
            KOMEWorldData data = new KOMEWorldData("schema"); KOMEArmyCompany company = company(data, false);
            NBTTagCompound root = new NBTTagCompound(); data.writeToNBT(root);
            NBTTagCompound old = (NBTTagCompound) root.copy(); old.setInteger(KOMEWorldData.KOME_DATA_SCHEMA_KEY, 8);
            old.removeTag("MovementBoundary"); old.getTagList("ArmyCompanies",10).getCompoundTagAt(0).removeTag("MovementAllowance");
            KOMEWorldData migrated = new KOMEWorldData("migrated"); migrated.readFromNBT(old);
            assertTrue(migrated.isDirty()); assertEquals(0, migrated.armyCompanies.get(company.id).movementAllowance);
            for (String missing : new String[]{"MovementBoundary", "MovementAllowance"}) {
                NBTTagCompound corrupt = (NBTTagCompound) root.copy();
                if (missing.equals("MovementBoundary")) corrupt.removeTag(missing);
                else corrupt.getTagList("ArmyCompanies",10).getCompoundTagAt(0).removeTag(missing);
                KOMEWorldData target = new KOMEWorldData("target");
                try { target.readFromNBT(corrupt); fail("Missing required authority"); }
                catch (IllegalStateException expected) { assertTrue(target.isWriteBlocked()); assertTrue(target.armyCompanies.isEmpty()); }
            }
        }
    }
    @Test public void failedWorldLoadWriteBlockPreventsNewMovementAuthorityMutations() throws Exception {
        try (KOMEPopulationTestConfig config = new KOMEPopulationTestConfig()) {
            KOMEWorldData data = new KOMEWorldData("blocked"); KOMEArmyCompany company = company(data, false);
            NBTTagCompound before = company.writeToNBT(), corrupt = new NBTTagCompound();
            corrupt.setInteger(KOMEWorldData.KOME_DATA_SCHEMA_KEY, 0);
            try { data.readFromNBT(corrupt); fail("Unsupported root"); } catch (IllegalStateException expected) { }
            assertTrue(data.isWriteBlocked());
            for (Runnable mutate : new Runnable[]{
                    () -> KOMEMovementDayService.anchor(data, NOW),
                    () -> KOMEMovementDayService.applyBoundary(data, company.movementBoundaryMillis),
                    () -> KOMEMovementDayService.depart(data, route(company), true, () -> true)}) {
                try { mutate.run(); fail("Write-blocked world must reject movement authority changes"); }
                catch (IllegalStateException expected) { assertEquals(before, company.writeToNBT()); assertEquals(0L, data.movementBoundaryMillis); }
            }
        }
    }
}
