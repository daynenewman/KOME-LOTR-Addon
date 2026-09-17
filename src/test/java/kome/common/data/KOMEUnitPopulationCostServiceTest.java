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
        KOMEWorldData data = new KOMEWorldData("cost"); data.grantFactionPopulationCenti("gondor", 7500L);
        KOMEHiredUnitRecord unit = unit(25); KOMEPopulationService.recordCombatHirePayment(unit, "gondor");
        assertTrue(KOMEPopulationService.tryDebitCombatHire(data, "gondor", 25)); assertEquals(5000L, KOMEPopulationService.getAvailablePopulationCenti(data,"gondor"));
        assertEquals(15, KOMEUnitPopulationCostService.reconcileBankedUnitCost(data, unit, 40)); assertEquals(3500L, KOMEPopulationService.getAvailablePopulationCenti(data,"gondor"));
        assertEquals(0, KOMEUnitPopulationCostService.reconcileBankedUnitCost(data, unit, 30)); assertEquals(30, unit.cost); assertEquals(40, unit.populationSpent);
        assertEquals(0, KOMEUnitPopulationCostService.reconcileBankedUnitCost(data, unit, 35)); assertEquals(35, unit.cost); assertEquals(40, unit.populationSpent);
        assertEquals(10, KOMEUnitPopulationCostService.reconcileBankedUnitCost(data, unit, 50)); assertEquals(2500L, KOMEPopulationService.getAvailablePopulationCenti(data,"gondor")); assertEquals(50, unit.populationSpent);
        assertEquals(-1, KOMEUnitPopulationCostService.reconcileBankedUnitCost(data, unit, 76)); assertEquals(2500L, KOMEPopulationService.getAvailablePopulationCenti(data,"gondor")); assertEquals(50, unit.populationSpent);
    }

    @Test public void wholeCostConversionIsExactForConfiguredOverrideAndMountedCost() {
        assertEquals(214748364700L, KOMEPopulationService.wholeToCenti(Integer.MAX_VALUE));
        Map<String,Integer> overrides = new HashMap<String,Integer>(); overrides.put("lotr.rider", Integer.valueOf(73));
        int configured = KOMEUnitPopulationCostService.calculate("LOTR.RIDER", 25, true, false, overrides);
        assertEquals(73, configured);
        assertEquals(7300L, KOMEPopulationService.wholeToCenti(configured));
    }
    @Test public void deathDismissalAndRestartDoNotRefundRecordedBankPayment() {
        KOMEWorldData data = new KOMEWorldData("cost"); data.grantFactionPopulationCenti("gondor", 3000L);
        KOMEHiredUnitRecord unit = unit(30); KOMEPopulationService.recordCombatHirePayment(unit,"gondor"); assertTrue(KOMEPopulationService.tryDebitCombatHire(data,"gondor",30));
        unit.entity = java.util.UUID.randomUUID(); data.hiredUnits.put(unit.entity, unit);
        assertSame(unit, data.removeTerminatedHiredUnit(unit.entity, "Unit removed")); assertEquals(0L, KOMEPopulationService.getAvailablePopulationCenti(data,"gondor"));
        NBTTagCompound tag = unit.writeToNBT(); KOMEHiredUnitRecord restored = new KOMEHiredUnitRecord(); restored.readFromNBT(tag);
        assertEquals(30,restored.cost); assertEquals(30,restored.populationSpent); assertEquals(KOMEHiredUnitRecord.SOURCE_FACTION_POPULATION_BANK,restored.sourceType);
    }
    @Test public void farmhandsAreExcludedFromActivePopulation() {
        KOMEHiredUnitRecord farmhand=unit(0); farmhand.farmhand=true; farmhand.cost=0; farmhand.populationSpent=0; farmhand.populationOwningFaction="gondor";
        assertEquals(java.math.BigInteger.ZERO,KOMEPopulationService.getExactActivePopulationCenti("gondor",Collections.singletonList(farmhand)));
    }
    private static KOMEHiredUnitRecord unit(int cost) { KOMEHiredUnitRecord r=new KOMEHiredUnitRecord(); r.entity=UUID.randomUUID(); r.owner=UUID.randomUUID(); r.sourcePlayer=r.owner; r.cost=cost; r.populationSpent=cost; r.unitEntityId="lotr.guard"; return r; }
}
