package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Deterministic coverage for the faction-bank portion of a new combat-hire transaction. */
public class KOMECombatHirePopulationTest {
    @Test
    public void fundedCombatHireDebitsThePayingFactionExactlyOnceAndRecordsIt() {
        KOMEWorldData data = new KOMEWorldData("test");
        data.grantFactionPopulation("gondor", 30);
        KOMEHiredUnitRecord record = new KOMEHiredUnitRecord();

        assertTrue(KOMEPopulationService.tryDebitCombatHire(data, "GONDOR", 12));
        KOMEPopulationService.recordCombatHirePayment(record, "GONDOR");

        assertEquals(18, KOMEPopulationService.getAvailablePopulation(data, "gondor"));
        assertEquals(KOMEHiredUnitRecord.SOURCE_FACTION_POPULATION_BANK, record.sourceType);
        assertEquals("gondor", record.populationOwningFaction);
        assertEquals("gondor", record.sourceFaction);
        assertTrue(data.isDirty());

        NBTTagCompound saved = new NBTTagCompound();
        data.writeToNBT(saved);
        KOMEWorldData restored = new KOMEWorldData("test");
        restored.readFromNBT(saved);
        assertEquals(18, KOMEPopulationService.getAvailablePopulation(restored, "gondor"));
    }

    @Test
    public void factionBankFundingProvenanceRoundTripsWithoutLegacyFallback() {
        KOMEHiredUnitRecord original = new KOMEHiredUnitRecord();
        original.entity = UUID.randomUUID();
        original.owner = UUID.randomUUID();
        original.sourcePlayer = original.owner;
        original.sourceType = KOMEHiredUnitRecord.SOURCE_FACTION_POPULATION_BANK;
        original.populationOwningFaction = "gondor";
        original.sourceFaction = "gondor";
        original.cost = 37;

        KOMEHiredUnitRecord restored = new KOMEHiredUnitRecord();
        restored.readFromNBT(original.writeToNBT());

        assertEquals(KOMEHiredUnitRecord.SOURCE_FACTION_POPULATION_BANK, restored.sourceType);
        assertEquals("gondor", restored.populationOwningFaction);
        assertEquals(37, restored.cost);
    }

    @Test
    public void insufficientFactionPopulationDoesNotMutateOrCreateAnotherDebit() {
        KOMEWorldData data = new KOMEWorldData("test");
        data.grantFactionPopulation("gondor", 5);
        data.setDirty(false);

        assertFalse(KOMEPopulationService.tryDebitCombatHire(data, "gondor", 6));
        assertEquals(5, KOMEPopulationService.getAvailablePopulation(data, "gondor"));
        assertFalse(data.isDirty());
    }

    @Test
    public void offensiveAndDefensiveLegacySelectionsUseTheSameFactionBank() {
        KOMEWorldData data = new KOMEWorldData("test");
        data.grantFactionPopulation("gondor", 20);
        KOMEPlayerPopulation legacySelector = data.getPopulation(java.util.UUID.randomUUID());

        legacySelector.hireType = KOMEPopulationType.OFFENSIVE;
        assertTrue(KOMEPopulationService.tryDebitCombatHire(data, "gondor", 5));
        legacySelector.hireType = KOMEPopulationType.DEFENSIVE;
        assertTrue(KOMEPopulationService.tryDebitCombatHire(data, "gondor", 5));

        assertEquals(10, KOMEPopulationService.getAvailablePopulation(data, "gondor"));
    }

    @Test
    public void factionBankDebitNeedsNoTileAllocationOrBuildCapacity() {
        KOMEWorldData data = new KOMEWorldData("test");
        data.grantFactionPopulation("gondor", 9);

        assertTrue(data.tilePopulations.isEmpty());
        assertTrue(data.populationAllocations.isEmpty());
        assertTrue(data.builds.isEmpty());
        assertTrue(KOMEPopulationService.tryDebitCombatHire(data, "gondor", 9));
        assertEquals(0, KOMEPopulationService.getAvailablePopulation(data, "gondor"));
    }

    @Test
    public void failedHireRollbackRestoresExactlyItsOwnDebitAndCannotBeReused() {
        KOMEWorldData data = new KOMEWorldData("test");
        data.grantFactionPopulation("gondor", 10);
        data.grantFactionPopulation("rohan", 3);

        assertTrue(KOMEPopulationService.beginCombatHireDebit(data, "rohan", 4) == null);
        KOMEPopulationService.CombatHireDebit debit = KOMEPopulationService.beginCombatHireDebit(data, "gondor", 6);
        assertTrue(debit != null);
        assertEquals(400L, KOMEPopulationService.getAvailablePopulationCenti(data, "gondor"));
        debit.rollback();

        assertEquals(10, KOMEPopulationService.getAvailablePopulation(data, "gondor"));
        assertEquals(3, KOMEPopulationService.getAvailablePopulation(data, "rohan"));
        try {
            debit.rollback();
            org.junit.Assert.fail("A completed rollback token must not be reusable");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("already closed"));
        }
    }

    @Test
    public void committedHireCannotUseRollbackAndFailureBeforeDebitChangesNothing() {
        KOMEWorldData data = new KOMEWorldData("test");
        data.grantFactionPopulation("gondor", 10);
        KOMEPopulationService.CombatHireDebit debit = KOMEPopulationService.beginCombatHireDebit(data, "gondor", 6);
        assertTrue(debit != null);
        debit.commit();
        try {
            debit.rollback();
            org.junit.Assert.fail("A committed hire must not be refundable");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("already closed"));
        }
        assertEquals(400L, KOMEPopulationService.getAvailablePopulationCenti(data, "gondor"));
        assertTrue(KOMEPopulationService.beginCombatHireDebit(data, "gondor", 5) == null);
        assertEquals(400L, KOMEPopulationService.getAvailablePopulationCenti(data, "gondor"));
    }

    @Test
    public void farmhandRecordHasNoCanonicalPopulationCostOrBankFunding() {
        KOMEHiredUnitRecord farmhand = new KOMEHiredUnitRecord();
        farmhand.farmhand = true;
        farmhand.cost = 0;
        farmhand.baseCost = 0;
        farmhand.populationSpent = 0;

        assertEquals(0, farmhand.cost);
        assertFalse(farmhand.isFactionPopulationBankFunded());
    }
}
