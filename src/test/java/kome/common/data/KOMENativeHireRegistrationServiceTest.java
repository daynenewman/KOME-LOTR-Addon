package kome.common.data;

import org.junit.Test;

import java.math.BigInteger;
import java.util.UUID;

import static org.junit.Assert.*;

public class KOMENativeHireRegistrationServiceTest {
    @Test public void ordinaryCombatHireDebitsOnceAndRemainsActiveWithoutStrategicState() {
        KOMEWorldData data = new KOMEWorldData("test");
        data.grantFactionPopulationCenti("gondor", 5000L);
        KOMEHiredUnitRecord record = combatUnit(25);

        assertTrue(KOMENativeHireRegistrationService.registerOrdinaryCombatHire(
            data, record, "gondor"));
        assertTrue(KOMENativeHireRegistrationService.registerOrdinaryCombatHire(
            data, record, "gondor"));

        assertSame(record, data.hiredUnits.get(record.entity));
        assertEquals(2500L, KOMEPopulationService.getAvailablePopulationCenti(data, "gondor"));
        assertEquals(BigInteger.valueOf(2500L),
            KOMEPopulationProjection.of(data, "gondor").activePopulationCenti);
        assertEquals(KOMEHiredUnitClass.ORDINARY,
            KOMEHiredUnitClassification.getUnitClass(record));
        assertEquals("", record.companyId);
        assertEquals("", record.movementOrderId);
        assertEquals("", record.currentTile);
        assertNull(record.movingEntityData);
        assertTrue(data.armyCompanies.isEmpty());
        assertTrue(data.armyMovements.isEmpty());
        KOMEEmergencyDefenseActivity activity =
            data.emergencyDefenseActivities.get("gondor");
        assertNotNull(activity);
        assertTrue(activity.hasKnownQualifyingHire());
        assertEquals(KOMEEmergencyDefenseActivity.PLAYER_COMBAT_HIRE,
            activity.updateSource);
    }

    @Test public void insufficientPopulationLeavesNoRecordAndNoPartialDebit() {
        KOMEWorldData data = new KOMEWorldData("test");
        data.grantFactionPopulationCenti("gondor", 2400L);
        KOMEHiredUnitRecord record = combatUnit(25);

        assertFalse(KOMENativeHireRegistrationService.registerOrdinaryCombatHire(
            data, record, "gondor"));
        assertEquals(2400L, KOMEPopulationService.getAvailablePopulationCenti(data, "gondor"));
        assertFalse(data.hiredUnits.containsKey(record.entity));
        assertFalse(data.emergencyDefenseActivities.containsKey("gondor"));
        assertEquals(BigInteger.ZERO,
            KOMEPopulationProjection.of(data, "gondor").activePopulationCenti);
    }

    @Test public void nativeRegistrationCannotBeUsedAsCampaignEnrollment() {
        KOMEWorldData data = new KOMEWorldData("test");
        data.grantFactionPopulationCenti("gondor", 5000L);
        KOMEHiredUnitRecord record = combatUnit(25);
        KOMEHiredUnitClassification.assignForCampaignWorkflow(record);
        try {
            KOMENativeHireRegistrationService.registerOrdinaryCombatHire(
                data, record, "gondor");
            fail("Campaign unit must not pass native hire registration");
        } catch (IllegalArgumentException expected) {
            assertEquals(5000L,
                KOMEPopulationService.getAvailablePopulationCenti(data, "gondor"));
            assertFalse(data.hiredUnits.containsKey(record.entity));
        }
    }

    @Test public void troopClassDoesNotChangePopulationCostOrActiveProjection() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEHiredUnitRecord ordinary = combatUnit(40);
        KOMEHiredUnitRecord campaign = combatUnit(40);
        KOMEHiredUnitClassification.assignForCampaignWorkflow(campaign);
        data.hiredUnits.put(ordinary.entity, ordinary);
        data.hiredUnits.put(campaign.entity, campaign);

        assertEquals(KOMEUnitPopulationCostService.calculate("unit", 40, false, false), ordinary.cost);
        assertEquals(ordinary.cost, campaign.cost);
        assertEquals(BigInteger.valueOf(8000L),
            KOMEPopulationProjection.of(data, "gondor").activePopulationCenti);
    }

    @Test public void farmhandsRemainZeroCostAndExcludedFromActivePopulation() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEHiredUnitRecord farmhand = combatUnit(25);
        farmhand.farmhand = true;
        farmhand.cost = farmhand.baseCost = farmhand.populationSpent = 0;
        data.hiredUnits.put(farmhand.entity, farmhand);

        assertEquals(0, KOMEUnitPopulationCostService.calculate("farmhand", 20, false, true));
        assertEquals(BigInteger.ZERO,
            KOMEPopulationProjection.of(data, "gondor").activePopulationCenti);
    }

    private static KOMEHiredUnitRecord combatUnit(int cost) {
        KOMEHiredUnitRecord record = new KOMEHiredUnitRecord();
        record.entity = UUID.randomUUID();
        record.owner = UUID.randomUUID();
        record.sourcePlayer = record.owner;
        record.type = KOMEPopulationType.OFFENSIVE;
        record.cost = cost;
        record.baseCost = cost;
        record.populationSpent = cost;
        record.unitEntityId = "unit";
        KOMEPopulationService.recordCombatHirePayment(record, "gondor");
        return record;
    }
}
