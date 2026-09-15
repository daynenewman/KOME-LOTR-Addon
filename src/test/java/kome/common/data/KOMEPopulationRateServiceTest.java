package kome.common.data;

import org.junit.Test;
import kome.common.config.KOMEConfigRegistry;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.OptionalLong;
import java.util.UUID;
import static org.junit.Assert.*;

public class KOMEPopulationRateServiceTest {
    @Test public void exactRatesProjectDivergentOwnershipWithoutRepairingRawFields() throws Exception {
        try (KOMEPopulationTestConfig ignored = new KOMEPopulationTestConfig()) {
            for (String[] raw : new String[][] {{"  Gondor ", " ROHAN "}, {" \t", " ROHAN "}, {null, " ROHAN "}}) {
                KOMEWorldData data = data();
                KOMEPlayerBuild build = build(data, "B", "gondor", KOMEBuildType.NORMAL, 20);
                KOMEConquestTile tile = data.conquestTiles.get("T1");
                tile.currentRulingFaction = raw[0]; tile.ownerFaction = raw[1];
                data.setDirty(false);
                boolean nativeControl = raw[0] != null && raw[0].trim().equals("Gondor");
                String receiver = nativeControl ? "gondor" : "rohan";
                assertEquals(receiver, tile.projectRulingFaction());
                assertEquals(java.math.BigInteger.valueOf(nativeControl ? 1_000_000L : 500_000L),
                        KOMEPopulationRateService.getExactDailyPopulationRates(data, KOMEConfigRegistry.population()).get(receiver));
                // Never serialize the tile: writeToNBT itself repairs compatibility fields.
                assertEquals(raw[0], tile.currentRulingFaction); assertEquals(raw[1], tile.ownerFaction);
                assertEquals(1000L, build.contributions.get(0).centiHours);
                assertEquals(KOMEBuildContribution.APPROVED, build.contributions.get(0).status);
                assertTrue(build.auditHistory().isEmpty()); assertTrue(data.centralAudit.isEmpty());
                assertTrue(data.factionPopulations.isEmpty()); assertTrue(data.populationPayoutRemainders.isEmpty());
                assertFalse(data.populationPayoutInitialized); assertEquals(-1L, data.lastPopulationPayoutBoundaryMillis);
                assertEquals("", data.populationPayoutTimezone); assertEquals("", data.populationPayoutLocalTime);
                assertFalse(data.isDirty());
            }
        }
    }

    @Test public void resolvedBasisPointsDriveBothAttributionAndExactAggregation() throws Exception {
        try (KOMEPopulationTestConfig config = new KOMEPopulationTestConfig()) {
            String[] configured = {"0.5000", "0", "1.0000", "0.0125"};
            long[] weights = {5000L, 0L, 10000L, 125L};
            String[] displayed = {"0.5", "0", "1", "0.0125"};
            for (int i = 0; i < configured.length; i++) {
                config.set("population.capturedBuildMultiplier", configured[i]);
                for (boolean captured : new boolean[] {false, true}) {
                    KOMEWorldData data = data(); build(data, "B", "gondor", KOMEBuildType.NORMAL, 20);
                    String receiver = captured ? "rohan" : "gondor";
                    data.conquestTiles.get("T1").claim(receiver, 0L);
                    KOMEPopulationRateContribution row = KOMEPopulationRateService.getPopulationRateContributions(data).get(0);
                    long weight = captured ? weights[i] : 10000L;
                    assertEquals(weight, row.multiplierBasisPoints);
                    assertEquals(captured ? displayed[i] : "1", row.multiplier);
                    assertEquals(captured ? "CAPTURED" : "NATIVE", row.status);
                    assertEquals(receiver, row.receivingFaction);
                    assertEquals(weight * 100L, row.currentRate.getFixedUnitsPerDay());
                    assertEquals(java.math.BigInteger.valueOf(weight * 100L),
                            KOMEPopulationRateService.getExactDailyPopulationRates(data, KOMEConfigRegistry.population()).get(receiver));
                }
            }
        }
    }

    @Test public void fixedRatesPreservePriorHalfHourValuesExactly() {
        assertEquals(1_000_000L, KOMEPopulationRateService.rate(1000L, 1000L).getFixedUnitsPerDay());
        assertEquals(500_000L, KOMEPopulationRateService.rate(500L, 1000L).getFixedUnitsPerDay());
        assertEquals(50_000L, KOMEPopulationRateService.rate(50L, 1000L).getFixedUnitsPerDay());
        assertEquals(500_000L, KOMEPopulationRateService.rate(1000L, 2000L).getFixedUnitsPerDay());
    }

    @Test public void nativeNormalBuildsAndDefensiveBuildsRemainSeparated() {
        KOMEWorldData data = data();
        KOMEPlayerBuild first = build(data, "B2", "gondor", KOMEBuildType.NORMAL, 10);
        KOMEPlayerBuild second = build(data, "B1", "gondor", KOMEBuildType.NORMAL, 20);
        KOMEPlayerBuild defensive = build(data, "B3", "gondor", KOMEBuildType.DEFENSIVE, 40);
        assertEquals(1_500_000L, KOMEPopulationService.getDailyPopulationRate(data, "gondor").getFixedUnitsPerDay());
        assertEquals("B1", KOMEPopulationService.getPopulationRateContributions(data).get(0).buildId);
        data.conquestTiles.get("T1").claim("rohan", 0L);
        assertEquals(750_000L, KOMEPopulationService.getDailyPopulationRate(data, "rohan").getFixedUnitsPerDay());
        assertEquals("CAPTURED", KOMEPopulationService.getPopulationRateContributions(data).get(0).status);
        assertFalse(KOMEPopulationService.getPopulationRateContributions(data).toString().contains(defensive.id));
    }

    @Test public void captureRecaptureAndForeignTransferRedirectCurrentRate() {
        KOMEWorldData data=data(); build(data,"B","gondor",KOMEBuildType.NORMAL,1);
        data.conquestTiles.get("T1").claim("rohan",0L);
        KOMEPopulationRateContribution row=KOMEPopulationService.getPopulationRateContributions(data).get(0);
        assertEquals("gondor",row.populationFaction); assertEquals("rohan",row.receivingFaction); assertEquals(25_000L,row.currentRate.getFixedUnitsPerDay()); assertEquals("0.5",row.multiplier);
        data.conquestTiles.get("T1").claim("mordor",0L); assertEquals(25_000L,KOMEPopulationService.getDailyPopulationRate(data,"mordor").getFixedUnitsPerDay()); assertEquals(0L,KOMEPopulationService.getDailyPopulationRate(data,"rohan").getFixedUnitsPerDay());
        data.conquestTiles.get("T1").claim("gondor",0L); assertEquals(50_000L,KOMEPopulationService.getDailyPopulationRate(data,"gondor").getFixedUnitsPerDay());
    }

    @Test public void capturedRatesAggregateBeforeFixedPointRounding() {
        KOMEWorldData data=data(); build(data,"B1","gondor",KOMEBuildType.NORMAL,1); build(data,"B2","gondor",KOMEBuildType.NORMAL,1); data.conquestTiles.get("T1").claim("rohan",0L);
        assertEquals(50_000L,KOMEPopulationService.getDailyPopulationRate(data,"rohan").getFixedUnitsPerDay());
    }

    @Test public void configuredCapturedMultiplierChangesImmediately() throws Exception {
        KOMEWorldData data=data(); build(data,"B","gondor",KOMEBuildType.NORMAL,20); data.conquestTiles.get("T1").claim("rohan",0L);
        KOMEConfigRegistry.ValidatedConfig config=KOMEConfigRegistry.currentValidated(); Field field=KOMEConfigRegistry.ValidatedConfig.class.getDeclaredField("population"); field.setAccessible(true); Object prior=field.get(config); KOMEConfigRegistry.PopulationSettings old=(KOMEConfigRegistry.PopulationSettings)prior;
        Constructor<KOMEConfigRegistry.PopulationSettings> c=KOMEConfigRegistry.PopulationSettings.class.getDeclaredConstructor(long.class,long.class,boolean.class,boolean.class,OptionalLong.class,boolean.class); c.setAccessible(true);
        try { field.set(config,c.newInstance(old.getHoursPerPopulationPointCentiHours(),2500L,old.isOfflinePopulationCatchUp(),old.isPopulationCapEnabled(),old.getPopulationCapCenti(),old.isEncirclementPopulationSuppressionEnabled())); assertEquals(250_000L,KOMEPopulationService.getDailyPopulationRate(data,"rohan").getFixedUnitsPerDay()); }
        finally { field.set(config,prior); }
    }

    @Test public void uncontrolledBuildRetainsOriginalButProducesNothing() {
        KOMEWorldData data=data(); KOMEPlayerBuild b=build(data,"B","gondor",KOMEBuildType.NORMAL,20); b.tileId="missing"; KOMEPopulationRateContribution row=KOMEPopulationService.getPopulationRateContributions(data).get(0);
        assertEquals("UNCONTROLLED",row.status); assertEquals(1_000_000L,row.originalRate.getFixedUnitsPerDay()); assertEquals(0L,row.currentRate.getFixedUnitsPerDay()); assertTrue(row.receivingFaction.length()==0);
    }

    private static KOMEWorldData data() {
        KOMEWorldData data = new KOMEWorldData("rate");
        KOMEConquestTile tile = new KOMEConquestTile("T1"); tile.claim("gondor", 0L); data.conquestTiles.put("T1", tile);
        return data;
    }
    private static KOMEPlayerBuild build(KOMEWorldData data, String id, String faction, KOMEBuildType type, int hours) {
        KOMEPlayerBuild build = new KOMEPlayerBuild(); build.id = id; build.tileId = "T1"; build.populationFaction = faction;
        build.type = type; build.active = true; KOMEBuildContribution c = new KOMEBuildContribution();
        c.id = "H" + id; c.centiHours = Math.multiplyExact((long) hours, 50L); c.status = KOMEBuildContribution.APPROVED; build.contributions.add(c);
        data.builds.put(id, build); return build;
    }
}
