package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static org.junit.Assert.*;

/** Regression coverage for permanent combat spending and retired-ledger isolation. */
public class KOMEPopulationPermanentSpendTest {
    @Test
    public void activePopulationUsesLivingHighWaterAndRemovalNeverCreditsTheBank() {
        KOMEWorldData data = new KOMEWorldData("test");
        data.grantFactionPopulation("gondor", 100);
        KOMEHiredUnitRecord living = combat("gondor", 30, 45);
        KOMEHiredUnitRecord second = combat("gondor", 20, 20);

        assertEquals(6500L, KOMEPopulationService.getActivePopulationCenti("gondor",
            Arrays.asList(living, second)));
        assertEquals(4500L, KOMEPopulationService.getActivePopulationCenti("gondor",
            Collections.singletonList(living)));

        long before = KOMEPopulationService.getAvailablePopulationCenti(data, "gondor");
        assertFalse(data.releasePopulationForOrdinaryUnitRemoval(second));
        assertEquals(before, KOMEPopulationService.getAvailablePopulationCenti(data, "gondor"));
    }

    @Test
    public void farmhandPersistenceAndReconciliationRemainPopulationNeutral() {
        KOMEHiredUnitRecord farmhand = combat("gondor", 99, 99);
        farmhand.farmhand = true;
        NBTTagCompound persisted = farmhand.writeToNBT();
        KOMEHiredUnitRecord restored = new KOMEHiredUnitRecord();
        restored.readFromNBT(persisted);

        assertEquals(0, restored.cost);
        assertEquals(0, restored.baseCost);
        assertEquals(0, restored.populationSpent);
        assertEquals(0L, KOMEPopulationService.getActivePopulationCenti("gondor",
            Collections.singletonList(restored)));
        KOMEWorldData data = new KOMEWorldData("test");
        assertEquals(0, KOMEUnitPopulationCostService.reconcileBankedUnitCost(data, restored, 250));
        assertTrue(data.factionPopulations.isEmpty());
    }

    @Test
    public void stewardshipDebitsNativeFactionAndDemobilizationDoesNotRefundEitherFaction() {
        KOMEWorldData data = new KOMEWorldData("test");
        data.grantFactionPopulation("rohan", 50);
        data.grantFactionPopulation("gondor", 50);
        KOMEHiredUnitRecord record = combat("", 25, 25);
        KOMEPopulationService.recordStewardshipCombatHirePayment(record, "rohan");
        KOMEPopulationService.CombatHireDebit debit = KOMEPopulationService.beginCombatHireDebit(data, "rohan", 25);
        assertNotNull(debit);
        debit.commit();
        assertEquals(2500L, KOMEPopulationService.getAvailablePopulationCenti(data, "rohan"));
        assertEquals(5000L, KOMEPopulationService.getAvailablePopulationCenti(data, "gondor"));
        assertEquals(KOMEHiredUnitRecord.SOURCE_STEWARDSHIP_RESERVATION, record.sourceType);
        assertEquals("rohan", record.populationOwningFaction);

        KOMEPledgeReleaseTombstone tombstone = new KOMEPledgeReleaseTombstone();
        long nativeBefore = KOMEPopulationService.getAvailablePopulationCenti(data, "rohan");
        long supportingBefore = KOMEPopulationService.getAvailablePopulationCenti(data, "gondor");
        KOMEWartimeStewardshipService.markDemobilizedPopulationPermanentlySpent(record, tombstone);
        assertEquals(nativeBefore, KOMEPopulationService.getAvailablePopulationCenti(data, "rohan"));
        assertEquals(supportingBefore, KOMEPopulationService.getAvailablePopulationCenti(data, "gondor"));
        assertTrue(tombstone.populationReturned);
        assertTrue(record.populationReturned);
        assertEquals("STEWARDSHIP_DEMOBILIZED_PERMANENTLY_SPENT", record.releaseState);
    }

    @Test
    public void transferPreservesHighWaterAndDoesNotChargeOrRefund() {
        KOMEWorldData data = new KOMEWorldData("test");
        UUID owner = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        data.lastKnownPlayerFactions.put(recipient, "gondor");
        data.grantFactionPopulation("gondor", 24);
        KOMEHiredUnitRecord record = combat("gondor", 20, 40);
        record.owner = owner;
        record.sourcePlayer = owner;
        record.sourceFaction = "gondor";
        KOMEPopulationService.recordCombatHirePayment(record, "gondor");
        KOMEArmyCompany company = new KOMEArmyCompany();
        company.id = "transfer";
        company.owner = owner;
        company.faction = "gondor";
        company.nativeFaction = "gondor";
        company.units.add(record.entity);
        record.companyId = company.id;
        data.hiredUnits.put(record.entity, record);

        assertTrue(KOMECompanyTransferService.offer(data, company, owner, recipient, "Recipient", 10L).success);
        assertTrue(KOMECompanyTransferService.accept(data, company, recipient, "Recipient", 11L).success);
        assertEquals(2400L, KOMEPopulationService.getAvailablePopulationCenti(data, "gondor"));
        assertEquals(40, record.populationSpent);
        assertEquals("gondor", record.populationOwningFaction);
    }

    @Test
    public void combatLifecycleSourcePathsCannotMutateRetiredPopulationLedgers() throws Exception {
        String events = source("src/main/java/kome/common/data/KOMEEvents.java");
        String hire = between(events, "private void handleHiredUnit", "private void denyAlliedHire");
        String reconcile = between(events, "private void updateTrackedPopulationCost", "private void denyLevelUpForPopulation");
        String removal = between(events, "private void releaseIfTracked", "private void releaseLinkedInactiveUnits");
        assertFalse(hire.contains("getPopulation("));
        assertFalse(hire.contains("getFundingPool("));
        assertFalse(hire.contains("consumeAllocationForHire"));
        assertFalse(reconcile.contains("getPopulation("));
        assertFalse(reconcile.contains("getFundingPool("));
        assertFalse(removal.contains(".release("));
        assertFalse(removal.contains("grantFactionPopulation"));

        String pledge = source("src/main/java/kome/common/data/KOMEPledgeReleaseService.java");
        String stewardship = source("src/main/java/kome/common/data/KOMEWartimeStewardshipService.java");
        String transfer = source("src/main/java/kome/common/data/KOMECompanyTransferService.java");
        String troops = source("src/main/java/kome/common/command/KOMECommandTroops.java");
        String ruler = source("src/main/java/kome/common/data/KOMERulerService.java");
        assertFalse(pledge.contains(".release("));
        assertFalse(pledge.contains("grantFactionPopulation"));
        assertFalse(stewardship.contains(".release("));
        assertFalse(stewardship.contains("grantFactionPopulation"));
        assertFalse(transfer.contains("KOMEPlayerPopulation"));
        assertFalse(transfer.contains("KOMETilePopulation"));
        assertFalse(transfer.contains("KOMEPlayerTilePopulationAllocation"));
        assertFalse(troops.contains("grantFactionPopulation"));
        assertFalse(troops.contains("rollbackCombatHireDebit"));
        assertFalse(ruler.contains("grantFactionPopulation"));
    }

    private static KOMEHiredUnitRecord combat(String faction, int currentCost, int populationSpent) {
        KOMEHiredUnitRecord record = new KOMEHiredUnitRecord();
        record.entity = UUID.randomUUID();
        record.owner = UUID.randomUUID();
        record.sourcePlayer = record.owner;
        record.populationOwningFaction = faction;
        record.sourceFaction = faction;
        record.cost = currentCost;
        record.baseCost = currentCost;
        record.populationSpent = populationSpent;
        return record;
    }

    private static String source(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    private static String between(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from);
        assertTrue("Missing source range " + start + " to " + end, from >= 0 && to > from);
        return source.substring(from, to);
    }
}
