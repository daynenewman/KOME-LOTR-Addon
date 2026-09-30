package kome.common.data;

import kome.common.config.KOMEConfigRegistry;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import java.math.BigInteger;
import java.time.Instant;

import static org.junit.Assert.*;

public class KOMEPopulationDevelopmentServiceTest {
    @Test public void freshPendingHoursDevelopOneHourAtEachLiveBoundary() throws Exception {
        try (KOMEPopulationTestConfig ignored = new KOMEPopulationTestConfig()) {
            KOMEWorldData data = world();
            KOMEPlayerBuild build = build(data, "B1", "gondor", "T100", 10000L, 0L, 1L);
            Instant startup = Instant.parse("2026-01-10T12:00:00Z");
            assertTrue(KOMEPopulationDevelopmentService.initializeOrSkipStartup(data, startup).success);
            assertEquals(BigInteger.ZERO, data.populationDevelopment.getRateCeilingUnits());
            Instant first = next(data);
            KOMEPopulationDevelopmentService.Result result =
                KOMEPopulationDevelopmentService.processLiveDueBoundaries(data, first);
            assertTrue(result.success); assertEquals(1L, result.processedBoundaries);
            assertEquals(100L, build.developedNativeCentiHours);
            assertEquals(9900L, build.pendingNativeCentiHours());
            assertEquals(BigInteger.valueOf(100000L), result.rateCeilingUnits);
            assertEquals(100000L, KOMEPopulationRateService
                .getExactNativeDevelopedRates(data, KOMEConfigRegistry.population())
                .get("gondor").longValueExact());
            assertTrue(KOMEPopulationDevelopmentService.processLiveDueBoundaries(
                data, next(data)).success);
            assertEquals(200L, build.developedNativeCentiHours);
        }
    }

    @Test public void catchUpAndAboveCeilingDevelopmentShareOneBoundaryBudgetRule() throws Exception {
        try (KOMEPopulationTestConfig ignored = new KOMEPopulationTestConfig()) {
            KOMEWorldData data = world();
            addTile(data, "T101", "rohan", "rohan");
            KOMEPlayerBuild established = build(data, "OLD", "gondor", "T100", 5000L, 2000L, 1L);
            KOMEPlayerBuild lagging = build(data, "NEW", "rohan", "T101", 5000L, 0L, 1L);
            KOMEPopulationDevelopmentService.initializeOrSkipStartup(data,
                Instant.parse("2026-01-10T12:00:00Z"));
            data.populationDevelopment.rateCeilingUnits = BigInteger.valueOf(1000000L);
            assertTrue(KOMEPopulationDevelopmentService.processLiveDueBoundaries(data, next(data)).success);
            assertEquals(BigInteger.valueOf(1100000L), data.populationDevelopment.rateCeilingUnits);
            assertEquals(2100L, established.developedNativeCentiHours);
            assertEquals(1100L, lagging.developedNativeCentiHours);
            assertEquals(1100000L, KOMEPopulationRateService
                .getExactNativeDevelopedRates(data, KOMEConfigRegistry.population())
                .get("rohan").longValueExact());
        }
    }

    @Test public void oldestBuildThenStableIdDeterminesAllocation() throws Exception {
        try (KOMEPopulationTestConfig ignored = new KOMEPopulationTestConfig()) {
            KOMEWorldData data = world();
            KOMEPlayerBuild b2 = build(data, "B2", "gondor", "T100", 100L, 0L, 10L);
            KOMEPlayerBuild b1 = build(data, "B1", "gondor", "T100", 50L, 0L, 10L);
            KOMEPlayerBuild newer = build(data, "A0", "gondor", "T100", 100L, 0L, 20L);
            KOMEPopulationDevelopmentService.initializeOrSkipStartup(data,
                Instant.parse("2026-01-10T12:00:00Z"));
            KOMEPopulationDevelopmentService.processLiveDueBoundaries(data, next(data));
            assertEquals(50L, b1.developedNativeCentiHours);
            assertEquals(50L, b2.developedNativeCentiHours);
            assertEquals(0L, newer.developedNativeCentiHours);
        }
    }

    @Test public void capturedPendingIsFrozenAndDoesNotAdvancePausedCeiling() throws Exception {
        try (KOMEPopulationTestConfig ignored = new KOMEPopulationTestConfig()) {
            KOMEWorldData data = world();
            data.conquestTiles.get("T100").claim("rohan", 0L);
            KOMEPlayerBuild captured = build(data, "B", "gondor", "T100", 10000L, 4000L, 1L);
            KOMEPopulationDevelopmentService.initializeOrSkipStartup(data,
                Instant.parse("2026-01-10T12:00:00Z"));
            KOMEPopulationDevelopmentService.processLiveDueBoundaries(data, next(data));
            assertEquals(4000L, captured.developedNativeCentiHours);
            assertEquals(6000L, captured.pendingNativeCentiHours());
            assertEquals(BigInteger.ZERO, data.populationDevelopment.rateCeilingUnits);
            assertEquals(2000000L, KOMEPopulationRateService.getExactDailyPopulationRates(
                data, KOMEConfigRegistry.population()).get("rohan").longValueExact());
        }
    }

    @Test public void ceilingAdvancesWithoutPendingWhenPauseIsDisabled() throws Exception {
        try (KOMEPopulationTestConfig config = new KOMEPopulationTestConfig()) {
            config.set("population.pauseRateCeilingWhenNoPendingHours", "false");
            KOMEWorldData data = world();
            KOMEPopulationDevelopmentService.initializeOrSkipStartup(data,
                Instant.parse("2026-01-10T12:00:00Z"));
            assertTrue(KOMEPopulationDevelopmentService.processLiveDueBoundaries(
                data, next(data)).success);
            assertEquals(BigInteger.valueOf(100000L),
                data.populationDevelopment.getRateCeilingUnits());
        }
    }

    @Test public void fractionalCentiHourBudgetIsCarriedExactlyWithoutEarlyRounding() throws Exception {
        try (KOMEPopulationTestConfig config = new KOMEPopulationTestConfig()) {
            config.set("population.bottleneckRatePerActiveServerDay", "0.0005");
            KOMEWorldData data = world();
            KOMEPlayerBuild build = build(data, "B", "gondor", "T100", 100L, 0L, 1L);
            KOMEPopulationDevelopmentService.initializeOrSkipStartup(data,
                Instant.parse("2026-01-10T12:00:00Z"));
            assertTrue(KOMEPopulationDevelopmentService.processLiveDueBoundaries(
                data, next(data)).success);
            assertEquals(0L, build.developedNativeCentiHours);
            assertEquals(Long.valueOf(500000L),
                data.populationDevelopment.getFactionCentiHourRemainders().get("gondor"));
            assertTrue(KOMEPopulationDevelopmentService.processLiveDueBoundaries(
                data, next(data)).success);
            assertEquals(1L, build.developedNativeCentiHours);
            assertFalse(data.populationDevelopment.getFactionCentiHourRemainders()
                .containsKey("gondor"));
        }
    }

    @Test public void developmentIsIndependentOfCampaignPhase() throws Exception {
        try (KOMEPopulationTestConfig ignored = new KOMEPopulationTestConfig()) {
            KOMEWorldData data = world();
            KOMEPlayerBuild build = build(data, "B", "gondor", "T100", 100L, 0L, 1L);
            data.warSeason.phase = KOMEWarSeasonState.Phase.RESET;
            KOMEPopulationDevelopmentService.initializeOrSkipStartup(data,
                Instant.parse("2026-01-10T12:00:00Z"));
            assertTrue(KOMEPopulationDevelopmentService.processLiveDueBoundaries(
                data, next(data)).success);
            assertEquals(100L, build.developedNativeCentiHours);
        }
    }

    @Test public void startupSkipsOfflineBoundariesWithoutDevelopmentReplay() throws Exception {
        try (KOMEPopulationTestConfig ignored = new KOMEPopulationTestConfig()) {
            KOMEWorldData data = world();
            KOMEPlayerBuild build = build(data, "B", "gondor", "T100", 10000L, 0L, 1L);
            KOMEPopulationDevelopmentService.initializeOrSkipStartup(data,
                Instant.parse("2026-01-10T12:00:00Z"));
            assertTrue(KOMEPopulationDevelopmentService.initializeOrSkipStartup(data,
                Instant.parse("2026-01-15T12:00:00Z")).success);
            assertEquals(0L, build.developedNativeCentiHours);
            assertEquals(BigInteger.ZERO, data.populationDevelopment.rateCeilingUnits);
            assertEquals(0L, KOMEPopulationDevelopmentService.processLiveDueBoundaries(
                data, Instant.parse("2026-01-15T12:00:00Z")).processedBoundaries);
        }
    }

    @Test public void startupNeverMovesProcessedCursorBackwardOrReplaysBoundary() throws Exception {
        try (KOMEPopulationTestConfig ignored = new KOMEPopulationTestConfig()) {
            KOMEWorldData data = world();
            KOMEPlayerBuild build = build(data, "B", "gondor", "T100", 10000L, 0L, 1L);
            assertTrue(KOMEPopulationDevelopmentService.initializeOrSkipStartup(data,
                Instant.parse("2026-01-10T12:00:00Z")).success);
            Instant processed = next(data);
            assertTrue(KOMEPopulationDevelopmentService.processLiveDueBoundaries(
                data, processed).success);
            long cursor = data.populationDevelopment.lastLiveBoundaryMillis;
            long developed = build.developedNativeCentiHours;

            KOMEPopulationDevelopmentService.Result startup =
                KOMEPopulationDevelopmentService.initializeOrSkipStartup(data,
                    processed.minusSeconds(3600L));
            assertTrue(startup.success);
            assertEquals(cursor, data.populationDevelopment.lastLiveBoundaryMillis);
            assertEquals(developed, build.developedNativeCentiHours);
            KOMEPopulationDevelopmentService.Result duplicate =
                KOMEPopulationDevelopmentService.processLiveDueBoundaries(data, processed);
            assertTrue(duplicate.success);
            assertEquals(0L, duplicate.processedBoundaries);
            assertEquals(developed, build.developedNativeCentiHours);
        }
    }

    @Test public void backwardScheduleReconciliationFailsClosedWithoutRegressingCursor() throws Exception {
        try (KOMEPopulationTestConfig config = new KOMEPopulationTestConfig()) {
            KOMEWorldData data = world();
            assertTrue(KOMEPopulationDevelopmentService.initializeOrSkipStartup(data,
                Instant.parse("2026-01-10T12:00:00Z")).success);
            Instant processed = next(data);
            assertTrue(KOMEPopulationDevelopmentService.processLiveDueBoundaries(
                data, processed).success);
            long cursor = data.populationDevelopment.lastLiveBoundaryMillis;
            String timezone = data.populationDevelopment.timezone;
            String localTime = data.populationDevelopment.localTime;

            config.set("dailyBatch.localTime", "19:00");
            KOMEPopulationDevelopmentService.Result startup =
                KOMEPopulationDevelopmentService.initializeOrSkipStartup(data, processed);
            assertFalse(startup.success);
            assertEquals(cursor, data.populationDevelopment.lastLiveBoundaryMillis);
            assertEquals(timezone, data.populationDevelopment.timezone);
            assertEquals(localTime, data.populationDevelopment.localTime);
        }
    }

    @Test public void fractionalRemainderIsIncorporatedWhenFactionFallsBelowNewCeiling() throws Exception {
        try (KOMEPopulationTestConfig config = new KOMEPopulationTestConfig()) {
            config.set("population.bottleneckRatePerActiveServerDay", "0.0004");
            KOMEWorldData data = world();
            KOMEPlayerBuild build = build(data, "B", "gondor", "T100", 100L, 1L, 1L);
            assertTrue(KOMEPopulationDevelopmentService.initializeOrSkipStartup(data,
                Instant.parse("2026-01-10T12:00:00Z")).success);
            data.populationDevelopment.rateCeilingUnits = BigInteger.valueOf(1100L);
            data.populationDevelopment.factionCentiHourRemainders.put(
                "gondor", Long.valueOf(400000L));

            assertTrue(KOMEPopulationDevelopmentService.processLiveDueBoundaries(
                data, next(data)).success);
            assertEquals(BigInteger.valueOf(1500L),
                data.populationDevelopment.rateCeilingUnits);
            assertEquals(1L, build.developedNativeCentiHours);
            assertEquals(Long.valueOf(500000L),
                data.populationDevelopment.factionCentiHourRemainders.get("gondor"));
        }
    }

    @Test public void aggregatePendingBeyondLongMaxUsesOnlyBoundedDailyBudget() throws Exception {
        try (KOMEPopulationTestConfig ignored = new KOMEPopulationTestConfig()) {
            KOMEWorldData data = world();
            KOMEPlayerBuild oldest = build(data, "A", "gondor", "T100",
                Long.MAX_VALUE, 0L, 1L);
            KOMEPlayerBuild newer = build(data, "B", "gondor", "T100",
                Long.MAX_VALUE, 0L, 2L);
            assertTrue(KOMEPopulationDevelopmentService.initializeOrSkipStartup(data,
                Instant.parse("2026-01-10T12:00:00Z")).success);

            KOMEPopulationDevelopmentService.Result result =
                KOMEPopulationDevelopmentService.processLiveDueBoundaries(data, next(data));
            assertTrue(result.success);
            assertEquals(100L, oldest.developedNativeCentiHours);
            assertEquals(0L, newer.developedNativeCentiHours);
        }
    }

    @Test public void exactStateAndDevelopedHoursRoundTripAndMalformedStateRejects() throws Exception {
        try (KOMEPopulationTestConfig ignored = new KOMEPopulationTestConfig()) {
            KOMEWorldData data = world();
            KOMEPlayerBuild build = build(data, "B", "gondor", "T100", 1000L, 400L, 1L);
            NBTTagCompound buildTag = build.writeToNBT();
            KOMEPlayerBuild restored = new KOMEPlayerBuild(); restored.readFromNBT(buildTag);
            assertEquals(400L, restored.developedNativeCentiHours);
            assertEquals(600L, restored.pendingNativeCentiHours());

            KOMEPopulationDevelopmentService.initializeOrSkipStartup(data,
                Instant.parse("2026-01-10T12:00:00Z"));
            data.populationDevelopment.rateCeilingUnits = BigInteger.valueOf(123456L);
            NBTTagCompound stateTag = data.populationDevelopment.writeToNBT();
            KOMEPopulationDevelopmentState state = KOMEPopulationDevelopmentState.readFromNBT(stateTag);
            assertEquals(BigInteger.valueOf(123456L), state.getRateCeilingUnits());
            stateTag.removeTag("RateCeilingUnits");
            try { KOMEPopulationDevelopmentState.readFromNBT(stateTag); fail("Expected malformed state rejection"); }
            catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("missing")); }
        }
    }

    private static Instant next(KOMEWorldData data) {
        return data.populationDevelopment.schedule().nextBoundary(
            Instant.ofEpochMilli(data.populationDevelopment.lastLiveBoundaryMillis));
    }

    private static KOMEWorldData world() {
        KOMEWorldData data = new KOMEWorldData("development");
        addTile(data, "T100", "gondor", "gondor");
        return data;
    }

    private static void addTile(KOMEWorldData data, String id, String defaults, String controller) {
        KOMEConquestTile tile = new KOMEConquestTile(id);
        tile.defaultRulingFaction = defaults; tile.claim(controller, 0L);
        data.conquestTiles.put(id, tile);
    }

    private static KOMEPlayerBuild build(KOMEWorldData data, String id, String faction,
            String tile, long approved, long developed, long created) {
        KOMEPlayerBuild build = new KOMEPlayerBuild();
        build.id = id; build.displayName = id; build.tileId = tile;
        build.populationFaction = faction; build.originalBuilderFaction = faction;
        build.type = KOMEBuildType.NORMAL; build.active = true;
        build.createdAtMillis = created; build.updatedAtMillis = created;
        KOMEBuildContribution contribution = new KOMEBuildContribution();
        contribution.id = "H-" + id; contribution.centiHours = approved;
        contribution.status = KOMEBuildContribution.APPROVED;
        build.contributions.add(contribution);
        build.developedNativeCentiHours = developed;
        build.validateContributions(); data.builds.put(id, build);
        return build;
    }
}
