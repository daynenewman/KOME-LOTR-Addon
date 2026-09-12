package kome.common.data;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMEUnitPopulationCostServiceTest {
    @Test public void defaultAndMountedAndFarmhandCostsAreDeterministic() {
        assertEquals(34, KOMEUnitPopulationCostService.calculate("lotr.guard", 34, false, false, Collections.<String,Integer>emptyMap()));
        assertEquals(59, KOMEUnitPopulationCostService.calculate("lotr.guard", 34, true, false, Collections.<String,Integer>emptyMap()));
        assertEquals(0, KOMEUnitPopulationCostService.calculate("lotr.farmer", 40, true, true, Collections.<String,Integer>emptyMap()));
    }
    @Test public void stableIdOverrideReplacesFormulaIncludingMountedSurcharge() {
        Map<String,Integer> overrides = new HashMap<String,Integer>(); overrides.put("lotr.troll", Integer.valueOf(90));
        assertEquals(90, KOMEUnitPopulationCostService.calculate("LOTR.TROLL", 120, true, false, overrides));
        assertEquals(35, KOMEUnitPopulationCostService.calculate("lotr.guard", 35, false, false, overrides));
    }
    @Test public void bankedHireAndLevelIncreaseSpendPermanentlyAndRejectUnaffordableIncrease() {
        KOMEWorldData data = new KOMEWorldData("cost"); data.grantFactionPopulation("gondor", 60);
        KOMEHiredUnitRecord unit = unit(30); KOMEPopulationService.recordCombatHirePayment(unit, "gondor");
        assertTrue(KOMEPopulationService.tryDebitCombatHire(data, "gondor", 30)); assertEquals(30, KOMEPopulationService.getAvailablePopulation(data,"gondor"));
        assertEquals(15, KOMEUnitPopulationCostService.reconcileBankedUnitCost(data, unit, 45)); assertEquals(15, KOMEPopulationService.getAvailablePopulation(data,"gondor"));
        assertEquals(-1, KOMEUnitPopulationCostService.reconcileBankedUnitCost(data, unit, 70)); assertEquals(15, KOMEPopulationService.getAvailablePopulation(data,"gondor"));
        assertEquals(0, KOMEUnitPopulationCostService.reconcileBankedUnitCost(data, unit, 20)); assertEquals(20, unit.cost); assertEquals(45, unit.populationSpent); assertEquals(15, KOMEPopulationService.getAvailablePopulation(data,"gondor"));
    }
    @Test public void deathDismissalAndRestartDoNotRefundRecordedBankPayment() {
        KOMEWorldData data = new KOMEWorldData("cost"); data.grantFactionPopulation("gondor", 30);
        KOMEHiredUnitRecord unit = unit(30); KOMEPopulationService.recordCombatHirePayment(unit,"gondor"); assertTrue(KOMEPopulationService.tryDebitCombatHire(data,"gondor",30));
        assertFalse(data.releasePopulationForOrdinaryUnitRemoval(unit)); assertEquals(0,KOMEPopulationService.getAvailablePopulation(data,"gondor"));
        NBTTagCompound tag = unit.writeToNBT(); KOMEHiredUnitRecord restored = new KOMEHiredUnitRecord(); restored.readFromNBT(tag);
        assertEquals(30,restored.cost); assertEquals(30,restored.populationSpent); assertEquals(KOMEHiredUnitRecord.SOURCE_FACTION_POPULATION_BANK,restored.sourceType);
    }
    @Test public void farmhandsAreExcludedFromActivePopulation() {
        KOMEHiredUnitRecord farmhand=unit(0); farmhand.farmhand=true; farmhand.cost=0; farmhand.populationSpent=0; farmhand.populationOwningFaction="gondor";
        assertEquals(0,KOMEPopulationService.getActivePopulation("gondor",Collections.singletonList(farmhand)));
        assertEquals(-1,new KOMEWorldData("cost").getFarmhandLimit(UUID.randomUUID()));
    }
    private static KOMEHiredUnitRecord unit(int cost) { KOMEHiredUnitRecord r=new KOMEHiredUnitRecord(); r.entity=UUID.randomUUID(); r.owner=UUID.randomUUID(); r.sourcePlayer=r.owner; r.cost=cost; r.populationSpent=cost; r.unitEntityId="lotr.guard"; return r; }
}
