package kome.common.data;

import java.math.BigInteger;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMEPopulationProjectionTest {
    @Test public void formatsEveryCentiWithoutRoundingOrNarrowing() {
        long[] values = {0L, 1L, 50L, 100L, 2450L, Long.MAX_VALUE};
        String[] text = {"0.00", "0.01", "0.50", "1.00", "24.50", "92233720368547758.07"};
        for (int i = 0; i < values.length; i++) assertEquals(text[i], KOMEPopulationProjection.formatCenti(values[i]));
        assertEquals("9223372036854775807.00", KOMEPopulationProjection.formatCenti(BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(100L))));
    }

    @Test public void activeUsesHighWaterAndStableProvenanceNotCurrentPledgeOrCost() {
        KOMEWorldData data = new KOMEWorldData("projection");
        KOMEHiredUnitRecord first = unit(data, "gondor", 40);
        unit(data, "gondor", 25);
        unit(data, "rohan", 60);
        first.cost = 10;
        first.unitFaction = "rohan";
        data.lastKnownPlayerFactions.put(first.owner, "rohan");
        data.grantFactionPopulationCenti("gondor", 2450L);
        data.setDirty(false);
        KOMEPopulationProjection projection = KOMEPopulationProjection.of(data, "gondor");
        assertEquals(2450L, projection.availablePopulationCenti);
        assertEquals(BigInteger.valueOf(6500L), projection.activePopulationCenti);
        assertEquals(BigInteger.valueOf(8950L), projection.representedPopulationCenti);
        assertEquals(40, first.populationSpent);
        assertFalse(data.isDirty());
        data.hiredUnits.remove(first.entity);
        assertEquals(BigInteger.valueOf(2500L), KOMEPopulationProjection.of(data, "gondor").activePopulationCenti);
        assertEquals(2450L, KOMEPopulationService.getAvailablePopulationCenti(data, "gondor"));
    }

    @Test public void farmhandsNormalizeOnLoadAndNeverContribute() {
        KOMEWorldData data = new KOMEWorldData("projection");
        KOMEHiredUnitRecord farmhand = unit(data, "gondor", 999);
        farmhand.farmhand = true;
        KOMEHiredUnitRecord reloaded = new KOMEHiredUnitRecord();
        reloaded.readFromNBT(farmhand.writeToNBT());
        assertEquals(0, reloaded.populationSpent); assertEquals(0, reloaded.cost); assertEquals(0, reloaded.baseCost);
        assertEquals(BigInteger.ZERO, KOMEPopulationProjection.of(data, "gondor").activePopulationCenti);
        assertTrue(data.factionPopulations.isEmpty());
    }

    @Test public void progressionPreservesTwoHundredThresholdWithExactFractionalBasis() {
        KOMEWorldData data = new KOMEWorldData("projection");
        data.grantFactionPopulationCenti("gondor", 19999L);
        data.setDirty(false);
        assertFalse(KOMEProgressionAutoCompleter.meetsPopulationThreshold(data, "gondor"));
        assertFalse(data.isDirty());
        data.grantFactionPopulationCenti("gondor", 1L);
        assertTrue(KOMEProgressionAutoCompleter.meetsPopulationThreshold(data, "gondor"));
        assertTrue(KOMEPopulationService.trySpendCenti(data, "gondor", 20000L));
        unit(data, "gondor", 200);
        assertTrue(KOMEProgressionAutoCompleter.meetsPopulationThreshold(data, "gondor"));
        data.hiredUnits.clear(); unit(data, "gondor", 25);
        data.grantFactionPopulationCenti("gondor", 17499L);
        assertFalse(KOMEProgressionAutoCompleter.meetsPopulationThreshold(data, "gondor"));
        data.grantFactionPopulationCenti("gondor", 1L);
        assertTrue(KOMEProgressionAutoCompleter.meetsPopulationThreshold(data, "gondor"));
    }

    @Test public void recruitmentIsPositiveCanonicalPopulationAndReadOnlyControlProjection() {
        KOMEWorldData data = new KOMEWorldData("projection");
        UUID player = UUID.randomUUID();
        KOMEConquestTile tile = new KOMEConquestTile("T1");
        tile.currentRulingFaction = " Gondor "; tile.ownerFaction = " ROHAN ";
        data.conquestTiles.put(tile.id, tile);
        data.getPopulation(player).offensiveTotal = Integer.MAX_VALUE;
        data.getOrCreateAllocation("T1", "gondor", player, "Tester").offensiveAllocated = Integer.MAX_VALUE;
        data.setDirty(false);
        assertFalse(data.canUseRecruitmentTile(player, "gondor", "T1"));
        data.grantFactionPopulationCenti("gondor", 1L); data.setDirty(false);
        assertTrue(data.canUseRecruitmentTile(player, "gondor", "T1"));
        assertEquals(1L, KOMEPopulationService.getAvailablePopulationCenti(data, "gondor"));
        assertEquals(" Gondor ", tile.currentRulingFaction); assertEquals(" ROHAN ", tile.ownerFaction);
        assertFalse(data.isDirty());
        assertFalse(data.canUseRecruitmentTile(player, "rohan", "T1"));
        assertTrue(data.setActiveRecruitmentTile(player, "gondor", "T1"));
        assertEquals("T1", data.getActiveRecruitmentTile(player, "gondor"));
        assertTrue(KOMEPopulationService.trySpendCenti(data, "gondor", 1L));
        assertEquals("", data.getActiveRecruitmentTile(player, "gondor"));
        unit(data, "gondor", 1);
        assertEquals("T1", data.getActiveRecruitmentTile(player, "gondor"));
    }

    @Test public void representedPopulationCanExceedLongWithoutOverflow() {
        KOMEWorldData data = new KOMEWorldData("projection");
        data.grantFactionPopulationCenti("gondor", Long.MAX_VALUE);
        unit(data, "gondor", Integer.MAX_VALUE);
        BigInteger total = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(Integer.MAX_VALUE).multiply(BigInteger.valueOf(100L)));
        assertEquals(total, KOMEPopulationProjection.of(data, "gondor").representedPopulationCenti);
        assertTrue(KOMEProgressionAutoCompleter.meetsPopulationThreshold(data, "gondor"));
    }

    @Test public void exactRateAndRowsExceedLongAndUsePayoutResult() throws Exception {
        try (KOMEPopulationTestConfig config = new KOMEPopulationTestConfig()) {
            config.set("population.hoursPerPopulationPoint", "0.01");
            KOMEWorldData data = new KOMEWorldData("projection");
            KOMEConquestTile tile = new KOMEConquestTile("T100"); tile.claim("gondor", 0L);
            data.conquestTiles.put(tile.id, tile);
            KOMEBuildService.create(data, "Hall", tile.id, 0, 0D, 64D, 0D, UUID.randomUUID(), "Builder",
                    "gondor", "gondor", KOMEBuildType.NORMAL, Long.MAX_VALUE, 1L);
            BigInteger expected = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(KOMEPopulationRate.SCALE));
            assertEquals(expected, KOMEPopulationProjection.of(data, "gondor").dailyRateUnits);
            KOMEPopulationRateContribution row = KOMEPopulationRateService.getPopulationRateContributions(data).get(0);
            assertEquals(expected, row.originalRateUnits); assertEquals(expected, row.currentRateUnits);
            assertEquals("9223372036854775807", row.formatCurrentRate());
            assertEquals(expected, KOMEPopulationRateService.getExactDailyPopulationRates(data,
                    kome.common.config.KOMEConfigRegistry.population()).get("gondor"));
        }
    }

    @Test public void retainedRecordMembershipIsLoadInvariantAndDoesNotRepairMovement() {
        KOMEWorldData data = new KOMEWorldData("liveness");
        KOMEHiredUnitRecord dead = unit(data, "gondor", 25);
        KOMEHiredUnitRecord virtual = unit(data, "gondor", 40); virtual.movementOrderId = "M1";
        unit(data, "gondor", 30); // Unloaded, still in the canonical living record index.
        data.setDirty(false);
        assertEquals(BigInteger.valueOf(9500L), KOMEPopulationService.getExactActivePopulationCenti("gondor",
                KOMEPopulationService.livingRecords(data)));
        assertEquals("M1", virtual.movementOrderId);
        assertEquals(3, data.hiredUnits.size()); assertFalse(data.isDirty());
        assertTrue(data.factionPopulations.isEmpty());
    }

    @Test public void highWaterProjectionFollowsExistingCostReconciliationOnly() {
        KOMEWorldData data = new KOMEWorldData("highwater");
        KOMEHiredUnitRecord record = unit(data, "gondor", 25);
        data.grantFactionPopulationCenti("gondor", 10000L);
        int[] desired = {40, 30, 35, 50}; int[] charged = {15, 0, 0, 10}; int[] invested = {40, 40, 40, 50};
        for (int i = 0; i < desired.length; i++) {
            long before = KOMEPopulationService.getAvailablePopulationCenti(data, "gondor");
            assertEquals(charged[i], KOMEUnitPopulationCostService.reconcileBankedUnitCost(data, record, desired[i]));
            assertEquals(before - charged[i] * 100L, KOMEPopulationProjection.of(data, "gondor").availablePopulationCenti);
            assertEquals(BigInteger.valueOf(invested[i] * 100L), KOMEPopulationProjection.of(data, "gondor").activePopulationCenti);
        }
    }

    @Test public void eligibilityAndExactProjectionsSurviveThreeWorldRoundTrips() {
        KOMEWorldData data = new KOMEWorldData("roundtrip");
        UUID player = UUID.randomUUID();
        KOMEConquestTile tile = new KOMEConquestTile("T100"); tile.claim("gondor", 0L);
        data.conquestTiles.put(tile.id, tile);
        data.grantFactionPopulationCenti("gondor", 2450L); unit(data, "gondor", 200);
        assertTrue(data.setActiveRecruitmentTile(player, "gondor", tile.id));
        for (int i = 0; i < 3; i++) {
            NBTTagCompound tag = new NBTTagCompound(); data.writeToNBT(tag);
            KOMEWorldData reloaded = new KOMEWorldData("roundtrip-" + i); reloaded.readFromNBT(tag);
            reloaded.setDirty(false);
            assertEquals(2450L, KOMEPopulationProjection.of(reloaded, "gondor").availablePopulationCenti);
            assertEquals(BigInteger.valueOf(20000L), KOMEPopulationProjection.of(reloaded, "gondor").activePopulationCenti);
            assertTrue(KOMEProgressionAutoCompleter.meetsPopulationThreshold(reloaded, "gondor"));
            assertEquals(tile.id, reloaded.getActiveRecruitmentTile(player, "gondor"));
            assertFalse(reloaded.isDirty()); data = reloaded;
        }
    }

    @Test public void legacyLedgersCannotChangeProjectionEligibilityOrFundHire() {
        KOMEWorldData data = new KOMEWorldData("legacy"); UUID player = UUID.randomUUID();
        KOMEConquestTile tile = new KOMEConquestTile("T100"); tile.claim("gondor", 0L); data.conquestTiles.put(tile.id, tile);
        data.getPopulation(player).offensiveTotal = Integer.MAX_VALUE;
        data.getOrCreateTilePopulationPool(tile.id, "gondor").offensiveTotal = Integer.MAX_VALUE;
        data.getOrCreateAllocation(tile.id, "gondor", player, "Player").offensiveAllocated = Integer.MAX_VALUE;
        data.setDirty(false);
        assertEquals(0L, KOMEPopulationProjection.of(data, "gondor").availablePopulationCenti);
        assertEquals(BigInteger.ZERO, KOMEPopulationProjection.of(data, "gondor").activePopulationCenti);
        assertFalse(KOMEProgressionAutoCompleter.meetsPopulationThreshold(data, "gondor"));
        assertFalse(data.canUseRecruitmentTile(player, "gondor", tile.id));
        assertNull(KOMEPopulationService.beginCombatHireDebit(data, "gondor", 25));
        assertTrue(data.factionPopulations.isEmpty()); assertFalse(data.isDirty());
    }

    @Test public void commandUnitAndServerRecordProjectionsAgreeAndAreReadOnly() throws Exception {
        KOMEWorldData data = new KOMEWorldData("commands");
        data.grantFactionPopulationCenti("gondor", 2450L);
        KOMEHiredUnitRecord record = unit(data, "gondor", 40); record.cost = 25;
        data.setDirty(false); NBTTagCompound before = new NBTTagCompound(); data.writeToNBT(before);
        java.lang.reflect.Method summary = KOMEServerRecordBuilder.class.getDeclaredMethod("getPopulationSummary", KOMEWorldData.class, String.class);
        summary.setAccessible(true);
        String text = (String) summary.invoke(null, data, "gondor");
        assertEquals(KOMEPopulationProjection.of(data, "gondor").summary(), text);
        assertTrue(text.contains("Available Population 24.50")); assertTrue(text.contains("Active Population 40.00"));
        java.lang.reflect.Method unitRow = kome.common.command.KOMECommandPopulation.class.getDeclaredMethod("buildUnitGuiEntry", KOMEWorldData.class, KOMEHiredUnitRecord.class);
        unitRow.setAccessible(true);
        kome.common.network.KOMEUnitGuiEntry row = (kome.common.network.KOMEUnitGuiEntry) unitRow.invoke(new kome.common.command.KOMECommandPopulation(), data, record);
        assertEquals(4000L, row.populationSpentCenti); assertEquals(25, row.populationCost);
        assertEquals("No refund (permanently spent)", row.releasesTo);
        NBTTagCompound after = new NBTTagCompound(); data.writeToNBT(after);
        assertEquals(before.toString(), after.toString()); assertFalse(data.isDirty());
    }

    private static KOMEHiredUnitRecord unit(KOMEWorldData data, String faction, int investment) {
        KOMEHiredUnitRecord unit = new KOMEHiredUnitRecord();
        unit.entity = UUID.randomUUID(); unit.owner = UUID.randomUUID();
        unit.populationOwningFaction = faction; unit.sourceFaction = faction;
        unit.populationSpent = investment; unit.cost = investment;
        data.hiredUnits.put(unit.entity, unit);
        return unit;
    }
}
