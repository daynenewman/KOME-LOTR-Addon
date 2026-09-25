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
                    assertEquals(weight * 100L, row.currentRateUnits.longValueExact());
                    assertEquals(java.math.BigInteger.valueOf(weight * 100L),
                            KOMEPopulationRateService.getExactDailyPopulationRates(data, KOMEConfigRegistry.population()).get(receiver));
                }
            }
        }
    }

    @Test public void fixedRatesPreservePriorHalfHourValuesExactly() throws Exception {
        assertEquals(1_000_000L, KOMEPopulationTestConfig.rateFromApprovedCentiHours(1000L, 1000L).longValueExact());
        assertEquals(500_000L, KOMEPopulationTestConfig.rateFromApprovedCentiHours(500L, 1000L).longValueExact());
        assertEquals(50_000L, KOMEPopulationTestConfig.rateFromApprovedCentiHours(50L, 1000L).longValueExact());
        assertEquals(500_000L, KOMEPopulationTestConfig.rateFromApprovedCentiHours(1000L, 2000L).longValueExact());
    }

    @Test public void nativeNormalBuildsAndDefensiveBuildsRemainSeparated() {
        KOMEWorldData data = data();
        KOMEPlayerBuild first = build(data, "B2", "gondor", KOMEBuildType.NORMAL, 10);
        KOMEPlayerBuild second = build(data, "B1", "gondor", KOMEBuildType.NORMAL, 20);
        KOMEPlayerBuild defensive = build(data, "B3", "gondor", KOMEBuildType.DEFENSIVE, 40);
        assertEquals(1_500_000L, kome.common.data.KOMEPopulationProjection.of(data, "gondor").dailyRateUnits.longValueExact());
        assertEquals("B1", KOMEPopulationService.getPopulationRateContributions(data).get(0).buildId);
        data.conquestTiles.get("T1").claim("rohan", 0L);
        assertEquals(750_000L, kome.common.data.KOMEPopulationProjection.of(data, "rohan").dailyRateUnits.longValueExact());
        assertEquals("CAPTURED", KOMEPopulationService.getPopulationRateContributions(data).get(0).status);
        assertFalse(KOMEPopulationService.getPopulationRateContributions(data).toString().contains(defensive.id));
    }

    @Test public void captureRecaptureAndForeignTransferRedirectCurrentRate() {
        KOMEWorldData data=data(); build(data,"B","gondor",KOMEBuildType.NORMAL,1);
        data.conquestTiles.get("T1").claim("rohan",0L);
        KOMEPopulationRateContribution row=KOMEPopulationService.getPopulationRateContributions(data).get(0);
        assertEquals("gondor",row.populationFaction); assertEquals("rohan",row.receivingFaction); assertEquals(25_000L,row.currentRateUnits.longValueExact()); assertEquals("0.5",row.multiplier);
        data.conquestTiles.get("T1").claim("mordor",0L); assertEquals(25_000L,kome.common.data.KOMEPopulationProjection.of(data, "mordor").dailyRateUnits.longValueExact()); assertEquals(0L,kome.common.data.KOMEPopulationProjection.of(data, "rohan").dailyRateUnits.longValueExact());
        data.conquestTiles.get("T1").claim("gondor",0L); assertEquals(50_000L,kome.common.data.KOMEPopulationProjection.of(data, "gondor").dailyRateUnits.longValueExact());
    }

    @Test public void capturedRatesAggregateBeforeFixedPointRounding() {
        KOMEWorldData data=data(); build(data,"B1","gondor",KOMEBuildType.NORMAL,1); build(data,"B2","gondor",KOMEBuildType.NORMAL,1); data.conquestTiles.get("T1").claim("rohan",0L);
        assertEquals(50_000L,kome.common.data.KOMEPopulationProjection.of(data, "rohan").dailyRateUnits.longValueExact());
    }

    @Test public void configuredCapturedMultiplierChangesImmediately() throws Exception {
        KOMEWorldData data=data(); build(data,"B","gondor",KOMEBuildType.NORMAL,20); data.conquestTiles.get("T1").claim("rohan",0L);
        KOMEConfigRegistry.ValidatedConfig config=KOMEConfigRegistry.currentValidated(); Field field=KOMEConfigRegistry.ValidatedConfig.class.getDeclaredField("population"); field.setAccessible(true); Object prior=field.get(config); KOMEConfigRegistry.PopulationSettings old=(KOMEConfigRegistry.PopulationSettings)prior;
        Constructor<KOMEConfigRegistry.PopulationSettings> c=KOMEConfigRegistry.PopulationSettings.class.getDeclaredConstructor(long.class,long.class,long.class,long.class,boolean.class,boolean.class,boolean.class,OptionalLong.class,boolean.class); c.setAccessible(true);
        try { field.set(config,c.newInstance(old.getHoursPerPopulationPointCentiHours(),2500L,
            old.getBottleneckRateUnitsPerActiveServerDay(), old.getRecruitmentTileActiveRateThresholdUnits(),
            old.isOfflinePopulationCatchUp(), old.isPauseRateCeilingWhenNoPendingHours(),
            old.isPopulationCapEnabled(),old.getPopulationCapCenti(),old.isEncirclementPopulationSuppressionEnabled())); assertEquals(250_000L,kome.common.data.KOMEPopulationProjection.of(data, "rohan").dailyRateUnits.longValueExact()); }
        finally { field.set(config,prior); }
    }

    @Test public void uncontrolledBuildRetainsOriginalButProducesNothing() {
        KOMEWorldData data=data(); KOMEPlayerBuild b=build(data,"B","gondor",KOMEBuildType.NORMAL,20); b.tileId="missing"; KOMEPopulationRateContribution row=KOMEPopulationService.getPopulationRateContributions(data).get(0);
        assertEquals("UNCONTROLLED",row.status); assertEquals(1_000_000L,row.originalRateUnits.longValueExact()); assertEquals(0L,row.currentRateUnits.longValueExact()); assertTrue(row.receivingFaction.length()==0);
    }

    @Test public void mixedNativeAndCapturedBuildsHaveExactlyOneCurrentRecipientAcrossTransfers() {
        KOMEWorldData data = data();
        build(data, "B-MORDOR", "mordor", KOMEBuildType.NORMAL, 100);
        build(data, "B-GONDOR", "gondor", KOMEBuildType.NORMAL, 100);
        data.conquestTiles.get("T1").claim("mordor", 1L);

        assertCurrentRates(data, "mordor", 7_500_000L, "gondor", 0L, "rohan", 0L);
        assertExclusiveRecipients(data, "mordor", "mordor");
        assertRow(data, "B-MORDOR", "NATIVE", "mordor", 5_000_000L);
        assertRow(data, "B-GONDOR", "CAPTURED", "mordor", 2_500_000L);

        // Native -> foreign capture replaces the old totals; it never layers new
        // recipients on top of cached/native production.
        data.conquestTiles.get("T1").claim("gondor", 2L);
        assertCurrentRates(data, "gondor", 7_500_000L, "mordor", 0L, "rohan", 0L);
        assertExclusiveRecipients(data, "gondor", "gondor");
        assertRow(data, "B-MORDOR", "CAPTURED", "gondor", 2_500_000L);
        assertRow(data, "B-GONDOR", "NATIVE", "gondor", 5_000_000L);

        // Foreign -> different foreign redirects the same developed capacity.
        data.conquestTiles.get("T1").claim("rohan", 3L);
        assertCurrentRates(data, "rohan", 5_000_000L, "gondor", 0L, "mordor", 0L);
        assertExclusiveRecipients(data, "rohan", "rohan");
        assertRow(data, "B-MORDOR", "CAPTURED", "rohan", 2_500_000L);
        assertRow(data, "B-GONDOR", "CAPTURED", "rohan", 2_500_000L);

        // Native recapture restores only the native controller's current total.
        data.conquestTiles.get("T1").claim("mordor", 4L);
        assertCurrentRates(data, "mordor", 7_500_000L, "gondor", 0L, "rohan", 0L);

        KOMEPlayerBuild uncontrolled = build(data, "B-UNCONTROLLED", "gondor",
            KOMEBuildType.NORMAL, 100);
        uncontrolled.tileId = "T-MISSING";
        assertRow(data, "B-UNCONTROLLED", "UNCONTROLLED", "", 0L);
        assertCurrentRates(data, "mordor", 7_500_000L, "gondor", 0L, "rohan", 0L);
    }

    private static void assertCurrentRates(KOMEWorldData data,
            String first, long firstUnits, String second, long secondUnits,
            String third, long thirdUnits) {
        assertEquals(firstUnits, KOMEPopulationProjection.of(data, first).dailyRateUnits.longValueExact());
        assertEquals(secondUnits, KOMEPopulationProjection.of(data, second).dailyRateUnits.longValueExact());
        assertEquals(thirdUnits, KOMEPopulationProjection.of(data, third).dailyRateUnits.longValueExact());
        java.util.Map<String, java.math.BigInteger> rates =
            KOMEPopulationRateService.getExactDailyPopulationRates(
                data, kome.common.config.KOMEConfigRegistry.population());
        assertEquals(firstUnits, rate(rates, first));
        assertEquals(secondUnits, rate(rates, second));
        assertEquals(thirdUnits, rate(rates, third));
    }

    private static long rate(java.util.Map<String, java.math.BigInteger> rates, String faction) {
        java.math.BigInteger value = rates.get(faction);
        return value == null ? 0L : value.longValueExact();
    }

    private static void assertExclusiveRecipients(KOMEWorldData data,
            String firstExpected, String secondExpected) {
        java.util.List<KOMEPopulationRateContribution> rows =
            KOMEPopulationRateService.getPopulationRateContributions(data);
        assertEquals(2, rows.size());
        assertEquals(firstExpected, rows.get(0).receivingFaction);
        assertEquals(secondExpected, rows.get(1).receivingFaction);
        java.util.Set<String> buildIds = new java.util.HashSet<String>();
        for (KOMEPopulationRateContribution row : rows) {
            assertTrue("duplicate contribution for " + row.buildId, buildIds.add(row.buildId));
        }
    }

    private static void assertRow(KOMEWorldData data, String buildId, String status,
            String recipient, long currentUnits) {
        for (KOMEPopulationRateContribution row
                : KOMEPopulationRateService.getPopulationRateContributions(data)) {
            if (!buildId.equals(row.buildId)) continue;
            assertEquals(status, row.status);
            assertEquals(recipient, row.receivingFaction);
            assertEquals(currentUnits, row.currentRateUnits.longValueExact());
            return;
        }
        fail("Missing contribution row " + buildId);
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
        if (type == KOMEBuildType.NORMAL) build.developedNativeCentiHours = c.centiHours;
        data.builds.put(id, build); return build;
    }
}
