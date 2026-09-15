package kome.common.data;

import kome.common.config.KOMEConfigRegistry;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;
import static org.junit.Assert.*;

/** Exercises the actual exact Build -> rate -> boundary -> bank/persistence pipeline. */
public class KOMEPopulationPayoutProcessorTest {
    private KOMEPopulationTestConfig config;
    private static final Instant START = Instant.parse("2026-01-10T18:00:00Z");
    @Before public void setup() throws Exception { config = new KOMEPopulationTestConfig(); }
    @After public void restore() throws Exception { config.close(); }

    @Test public void wholeCentiAndSubCentiGenerationAreExactAndIdempotent() {
        for (long hours : new long[] {1000L, 10L, 5L, 1025L}) {
            KOMEWorldData data = world("gondor", hours);
            Instant first = initializeAndNext(data);
            KOMEPopulationPayoutProcessor.Result one = pay(data, first);
            long rate = hours * 1000L;
            assertEquals(rate / 10000L, bank(data, "gondor"));
            assertEquals(BigInteger.valueOf(rate), one.factions.get(0).exactRateUnits);
            assertEquals(rate % 10000L, remainder(data, "gondor"));
            NBTTagCompound before = save(data);
            pay(data, first);
            assertEquals(before, save(data));
            pay(data, KOMEPopulationPayoutProcessor.nextBoundary(first));
            assertEquals(rate * 2L / 10000L, bank(data, "gondor"));
            assertEquals(rate * 2L % 10000L, remainder(data, "gondor"));
        }
    }

    @Test public void remainderSurvivesThreeRestartsZeroRateAndCaptureChanges() {
        KOMEWorldData data = world("gondor", 5L);
        Instant due = initializeAndNext(data); pay(data, due);
        assertEquals(5000L, remainder(data, "gondor"));
        for (int i = 0; i < 3; i++) { data = reload(data); pay(data, due); assertEquals(5000L, remainder(data, "gondor")); }
        data.builds.get("B-gondor").active = false;
        due = KOMEPopulationPayoutProcessor.nextBoundary(due); pay(data, due);
        assertEquals(5000L, remainder(data, "gondor"));
        data.builds.get("B-gondor").active = true;
        data.conquestTiles.get("T-GONDOR").claim("rohan", 0L);
        due = KOMEPopulationPayoutProcessor.nextBoundary(due); pay(data, due);
        assertEquals(5000L, remainder(data, "gondor")); assertEquals(2500L, remainder(data, "rohan"));
        data.conquestTiles.get("T-GONDOR").claim("gondor", 0L);
        pay(data, KOMEPopulationPayoutProcessor.nextBoundary(due));
        assertEquals(1L, bank(data, "gondor")); assertEquals(0L, remainder(data, "gondor"));
    }

    @Test public void multipleFactionsCapturedDefensivePendingRejectedInactiveAndZeroRates() {
        KOMEWorldData data = world("gondor", 500L);
        add(data, "rohan", KOMEBuildType.NORMAL, 1000L);
        add(data, "mordor", KOMEBuildType.DEFENSIVE, Long.MAX_VALUE);
        add(data, "pending", KOMEBuildType.NORMAL, 1000L).contributions.get(0).status = KOMEBuildContribution.PENDING;
        add(data, "rejected", KOMEBuildType.NORMAL, 1000L).contributions.get(0).status = KOMEBuildContribution.REJECTED;
        add(data, "inactive", KOMEBuildType.NORMAL, 1000L).active = false;
        add(data, "zero", KOMEBuildType.NORMAL, 0L);
        data.conquestTiles.get("T-ROHAN").claim("gondor", 0L);
        data.conquestTiles.get("T-MORDOR").claim("gondor", 0L);
        KOMEPopulationPayoutProcessor.Result result = pay(data, initializeAndNext(data));
        assertEquals(100L, bank(data, "gondor")); assertEquals(1, data.factionPopulations.size());
        assertEquals("gondor", result.factions.get(0).faction);
        assertEquals("pending", result.factions.get(1).faction);
        assertEquals("rejected", result.factions.get(2).faction);
        assertEquals("zero", result.factions.get(3).faction);
        data.conquestTiles.get("T-ROHAN").claim("rohan", 0L);
        pay(data, KOMEPopulationPayoutProcessor.nextBoundary(Instant.ofEpochMilli(data.lastPopulationPayoutBoundaryMillis)));
        assertEquals(100L, bank(data, "rohan")); assertEquals(150L, bank(data, "gondor"));
    }

    @Test public void factionAggregationPrecedesRoundingAndCentiConversion() {
        config.set("population.hoursPerPopulationPoint", "3");
        KOMEWorldData data = world("gondor", 1L);
        KOMEPlayerBuild second = add(data, "second", KOMEBuildType.NORMAL, 1L);
        second.populationFaction = "gondor"; data.conquestTiles.get("T-SECOND").claim("gondor", 0L);
        assertEquals(6667L, KOMEPopulationService.getDailyPopulationRate(data, "gondor").getFixedUnitsPerDay());
        Instant due = initializeAndNext(data);
        pay(data, due); pay(data, KOMEPopulationPayoutProcessor.nextBoundary(due));
        assertEquals(1L, bank(data, "gondor")); assertEquals(3334L, remainder(data, "gondor"));
    }

    @Test public void exactUnsaturatedRateFeedsPayoutWithoutUnderpaying() {
        config.set("population.hoursPerPopulationPoint", "0.01");
        KOMEWorldData data = world("gondor", 10_000_000_000_000L);
        assertEquals(Long.MAX_VALUE, KOMEPopulationService.getDailyPopulationRate(data, "gondor").getFixedUnitsPerDay());
        Map<String, BigInteger> exact = KOMEPopulationRateService.getExactDailyPopulationRates(data, KOMEConfigRegistry.population());
        assertEquals(new BigInteger("10000000000000000000"), exact.get("gondor"));
        pay(data, initializeAndNext(data));
        assertEquals(1_000_000_000_000_000L, bank(data, "gondor"));
    }

    @Test public void unrepresentableExactGrantFailsAndCapCanMakeItRepresentable() {
        config.set("population.hoursPerPopulationPoint", "0.01");
        KOMEWorldData data = world("gondor", Long.MAX_VALUE);
        Instant due = initializeAndNext(data);
        NBTTagCompound before = save(data);
        assertFalse(KOMEPopulationPayoutProcessor.processLiveDueBoundaries(data, due).success);
        assertEquals(before, save(data));
        config.set("population.populationCapEnabled", "true", "population.populationCapValue", "92233720368547758.07");
        KOMEPopulationPayoutProcessor.Result result = pay(data, due);
        assertEquals(Long.MAX_VALUE, bank(data, "gondor"));
        assertTrue(result.factions.get(0).generatedCenti.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0);
        assertTrue(result.factions.get(0).blockedCenti.signum() > 0);
    }

    @Test public void maximumBankSucceedsThenFirstOverflowFailsAtomicallyAndCanRetry() {
        KOMEWorldData data = world("gondor", 10L);
        add(data, "rohan", KOMEBuildType.NORMAL, 10L);
        KOMEPopulationService.grantCenti(data, "rohan", Long.MAX_VALUE - 1L);
        Instant first = initializeAndNext(data); pay(data, first);
        assertEquals(Long.MAX_VALUE, bank(data, "rohan"));
        data.populationPayoutRemainders.put("gondor", 7L);
        data.populationPayoutRemainders.put("rohan", 9L);
        Instant second = KOMEPopulationPayoutProcessor.nextBoundary(first);
        NBTTagCompound before = save(data);
        data.setDirty(false);
        KOMEPopulationPayoutProcessor.Result failure = KOMEPopulationPayoutProcessor.processLiveDueBoundaries(data, second);
        assertFalse(failure.success); assertTrue(failure.message.contains("rohan")); assertTrue(failure.message.contains("centi"));
        assertEquals(before, save(data)); // includes every bank, remainder, cursor, signature and audit
        assertFalse(data.isDirty());
        assertFalse(data.populationPayoutLastFailure.isEmpty());
        assertTrue(KOMEPopulationService.trySpendCenti(data, "rohan", 1L));
        pay(data, second); NBTTagCompound paid = save(data); pay(data, second); assertEquals(paid, save(data));
    }

    @Test public void fractionalCapFillsExactRoomAndDiscardsExcess() {
        config.set("population.populationCapEnabled", "true", "population.populationCapValue", "24.50");
        for (long initial : new long[] {2350L, 2400L, 2449L, 2450L, 2500L}) {
            KOMEWorldData data = world("gondor", initial == 2449L ? 20L : 1000L);
            KOMEPopulationService.grantCenti(data, "gondor", initial);
            KOMEPopulationPayoutProcessor.FactionResult result = pay(data, initializeAndNext(data)).factions.get(0);
            assertEquals(Math.max(initial, 2450L), bank(data, "gondor"));
            assertEquals(Math.max(0L, 2450L - initial), result.grantedCenti);
            assertEquals(result.generatedCenti.subtract(BigInteger.valueOf(result.grantedCenti)), result.blockedCenti);
        }
    }

    @Test public void capPreservesSubCentiRemainderButNotBlockedCenti() {
        config.set("population.populationCapEnabled", "true", "population.populationCapValue", "0.01");
        KOMEWorldData data = world("gondor", 25L);
        Instant first = initializeAndNext(data); pay(data, first);
        assertEquals(1L, bank(data, "gondor")); assertEquals(5000L, remainder(data, "gondor"));
        KOMEPopulationService.trySpendCenti(data, "gondor", 1L);
        data.builds.get("B-gondor").contributions.get(0).centiHours = 5L;
        pay(data, KOMEPopulationPayoutProcessor.nextBoundary(first));
        assertEquals(1L, bank(data, "gondor")); assertEquals(0L, remainder(data, "gondor"));
    }

    @Test public void disabledCapDoesNotLimitFractionalGeneration() {
        config.set("population.populationCapValue", "0.01");
        KOMEWorldData data = world("gondor", 1025L); pay(data, initializeAndNext(data));
        assertEquals(102L, bank(data, "gondor")); assertEquals(5000L, remainder(data, "gondor"));
    }

    @Test public void firstStartAndRepeatedInitializationNeverPayHistoricalAnchor() {
        for (Instant now : new Instant[] {START, Instant.parse("2026-01-11T02:00:00Z")}) {
            KOMEWorldData data = world("gondor", 1000L);
            assertTrue(KOMEPopulationPayoutProcessor.initializeOrProcessStartup(data, now).initialized);
            assertEquals(0L, bank(data, "gondor")); NBTTagCompound before = save(data);
            assertTrue(KOMEPopulationPayoutProcessor.initializeOrProcessStartup(data, now).success);
            assertEquals(before, save(data));
            pay(data, KOMEPopulationPayoutProcessor.nextBoundary(Instant.ofEpochMilli(data.lastPopulationPayoutBoundaryMillis)));
            assertEquals(100L, bank(data, "gondor"));
        }
    }

    @Test public void multipleYearCatchUpIsPopulationOnlyAndRestartIsIdempotent() {
        KOMEWorldData data = world("gondor", 5L);
        Instant first = initializeAndNext(data);
        KOMEArmyMovementOrder movement = KOMEArmyMovementOrder.newRoute(2);
        movement.id = "M1"; movement.dailyStepsRemaining = 0; movement.currentTile = "T-GONDOR";
        movement.nextDailyStepMillis = first.toEpochMilli(); data.armyMovements.put(movement.id, movement);
        NBTTagCompound movementBefore = movement.writeToNBT();
        KOMEDailyBoundary schedule = data.populationPayoutSchedule();
        Instant later = schedule.boundary(schedule.localDate(first).plusYears(3));
        long count = java.time.temporal.ChronoUnit.DAYS.between(schedule.localDate(first), schedule.localDate(later)) + 1L;
        KOMEPopulationPayoutProcessor.Result result = KOMEPopulationPayoutProcessor.initializeOrProcessStartup(data, later);
        assertTrue(result.message, result.success); assertEquals(count, result.processedBoundaries);
        assertEquals(count / 2L, bank(data, "gondor")); assertEquals(count % 2L * 5000L, remainder(data, "gondor"));
        assertEquals(movementBefore, movement.writeToNBT());
        assertTrue(data.centralAudit.size() <= KOMEAuditService.MAX_ENTRIES);
        KOMEWorldData restored = reload(data); NBTTagCompound before = save(restored);
        assertTrue(KOMEPopulationPayoutProcessor.initializeOrProcessStartup(restored, later).success);
        assertEquals(before, save(restored));
    }

    @Test public void offlineDisabledSkipsMissedButStalledLiveChecksPayDueBoundaries() {
        config.set("population.offlinePopulationCatchUp", "false");
        KOMEWorldData data = world("gondor", 1000L);
        Instant first = initializeAndNext(data), third = data.populationPayoutSchedule().boundary(
                data.populationPayoutSchedule().localDate(first).plusDays(2));
        assertTrue(KOMEPopulationPayoutProcessor.initializeOrProcessStartup(data, third).skipped);
        assertEquals(0L, bank(data, "gondor"));
        Instant fifth = data.populationPayoutSchedule().boundary(data.populationPayoutSchedule().localDate(third).plusDays(2));
        assertEquals(2L, pay(data, fifth).processedBoundaries); assertEquals(200L, bank(data, "gondor"));
    }

    @Test public void catchUpCommitsCompletedBoundariesBeforeLaterFailure() {
        KOMEWorldData data = world("gondor", 10L);
        KOMEPopulationService.grantCenti(data, "gondor", Long.MAX_VALUE - 1L);
        Instant first = initializeAndNext(data), second = KOMEPopulationPayoutProcessor.nextBoundary(first);
        KOMEPopulationPayoutProcessor.Result result = KOMEPopulationPayoutProcessor.initializeOrProcessStartup(data, second);
        assertFalse(result.success); assertEquals(1L, result.processedBoundaries);
        assertEquals(first.toEpochMilli(), data.lastPopulationPayoutBoundaryMillis);
        assertEquals(Long.MAX_VALUE, bank(data, "gondor"));
    }

    @Test public void scheduleChangesReconcileOldScheduleThenAnchorNewWithoutDoublePaying() {
        for (String[] change : new String[][] {
                {"dailyBatch.timezone", "Asia/Kolkata"}, {"dailyBatch.localTime", "09:15"},
                {"dailyBatch.timezone", "Pacific/Auckland", "dailyBatch.localTime", "01:30"}}) {
            for (boolean catchUp : new boolean[] {true, false}) {
                config.set("dailyBatch.timezone", "America/Chicago", "dailyBatch.localTime", "20:00",
                        "population.offlinePopulationCatchUp", Boolean.toString(catchUp));
                KOMEWorldData data = world("gondor", 1000L);
                initializeAndNext(data); KOMEDailyBoundary old = data.populationPayoutSchedule();
                Instant later = START.plusSeconds(3L * 86400L);
                config.set(change);
                KOMEPopulationPayoutProcessor.Result result = KOMEPopulationPayoutProcessor.initializeOrProcessStartup(data, later);
                assertTrue(result.message, result.success);
                assertEquals(catchUp ? 300L : 0L, bank(data, "gondor"));
                assertNotEquals(old.signature(), data.populationPayoutSchedule().signature());
                assertEquals(KOMEPopulationPayoutProcessor.latestBoundaryAtOrBefore(later).toEpochMilli(), data.lastPopulationPayoutBoundaryMillis);
                Instant next = KOMEPopulationPayoutProcessor.nextBoundary(Instant.ofEpochMilli(data.lastPopulationPayoutBoundaryMillis));
                assertTrue(next.isAfter(later)); NBTTagCompound before = save(data);
                assertTrue(KOMEPopulationPayoutProcessor.initializeOrProcessStartup(data, later).success);
                assertEquals(before, save(data)); pay(data, next); assertEquals(catchUp ? 400L : 100L, bank(data, "gondor"));
            }
        }
    }

    @Test public void failedScheduleReconciliationRetainsOldIdentityAndRuntimeRetriesStartup() {
        KOMEWorldData data = world("gondor", 10L);
        Instant due = initializeAndNext(data); KOMEPopulationService.grantCenti(data, "gondor", Long.MAX_VALUE);
        config.set("dailyBatch.localTime", "09:15");
        NBTTagCompound before = save(data);
        KOMEPopulationPayoutRuntime runtime = new KOMEPopulationPayoutRuntime();
        assertFalse(runtime.onStartup(data, due).success); assertFalse(runtime.hasStarted(data));
        assertEquals(before, save(data));
        assertFalse(runtime.onLiveCheck(data, due).success); assertEquals(before, save(data));
        KOMEPopulationService.trySpendCenti(data, "gondor", 1L);
        assertTrue(runtime.onLiveCheck(data, due).success); assertTrue(runtime.hasStarted(data));
        assertEquals("09:15", data.populationPayoutLocalTime);
        assertNull(runtime.onStartup(data, due));
    }

    @Test public void liveScheduleMismatchAndUnreadyConfigDoNotMutateGameplay() throws Exception {
        KOMEWorldData data = world("gondor", 1000L); Instant due = initializeAndNext(data);
        config.set("dailyBatch.localTime", "09:15");
        NBTTagCompound before = save(data);
        assertFalse(KOMEPopulationPayoutProcessor.processLiveDueBoundaries(data, due).success); assertEquals(before, save(data));
        KOMEConfigRegistry.ValidatedConfig snapshot = KOMEConfigRegistry.currentValidated();
        Field ready = snapshot.getClass().getDeclaredField("validationComplete"); ready.setAccessible(true);
        try {
            ready.setBoolean(snapshot, false);
            KOMEWorldData fresh = world("gondor", 1000L); NBTTagCompound freshBefore = save(fresh);
            assertFalse(KOMEPopulationPayoutProcessor.initializeOrProcessStartup(fresh, START).success);
            assertEquals(freshBefore, save(fresh));
            assertFalse(KOMEPopulationPayoutProcessor.processLiveDueBoundaries(data, due).success); assertEquals(before, save(data));
        } finally { ready.setBoolean(snapshot, true); }
    }

    @Test public void frozenBoundariesConsumeCursorWithoutAccrualOrLostRemainder() {
        KOMEWorldData data = world("gondor", 5L); Instant due = initializeAndNext(data); pay(data, due);
        for (KOMEWarSeasonState.Phase phase : KOMEWarSeasonState.Phase.values()) {
            if (phase == KOMEWarSeasonState.Phase.WAR || phase == KOMEWarSeasonState.Phase.FINALE) continue;
            data.warSeason.phase = phase; due = KOMEPopulationPayoutProcessor.nextBoundary(due);
            assertEquals(0, pay(data, due).factions.size()); assertEquals(0L, bank(data, "gondor"));
            assertEquals(5000L, remainder(data, "gondor")); assertEquals(due.toEpochMilli(), data.lastPopulationPayoutBoundaryMillis);
            assertEquals("FROZEN", data.centralAudit.get(data.centralAudit.size() - 1).action);
        }
        data.warSeason.phase = KOMEWarSeasonState.Phase.WAR;
        pay(data, KOMEPopulationPayoutProcessor.nextBoundary(due)); assertEquals(1L, bank(data, "gondor"));
    }

    @Test public void malformedPayoutPersistenceFailsClosedBeforeAnyPublication() {
        KOMEWorldData source = world("gondor", 5L); pay(source, initializeAndNext(source));
        for (String key : new String[] {"PopulationPayoutDataSchemaVersion", "PopulationPayoutTimezone",
                "PopulationPayoutLocalTime", "LastPopulationPayoutBoundaryMillis", "PopulationPayoutRemainders"}) {
            NBTTagCompound invalid = save(source); invalid.removeTag(key); assertRejected(invalid);
        }
        for (long bad : new long[] {-1L, 10000L, Long.MAX_VALUE}) {
            NBTTagCompound invalid = save(source);
            invalid.getTagList("PopulationPayoutRemainders", 10).getCompoundTagAt(0).setLong("RemainderUnits", bad);
            assertRejected(invalid);
        }
        for (String faction : new String[] {"", " ", "Gondor"}) {
            NBTTagCompound invalid = save(source);
            invalid.getTagList("PopulationPayoutRemainders", 10).getCompoundTagAt(0).setString("Faction", faction); assertRejected(invalid);
        }
        NBTTagCompound duplicate = save(source); NBTTagList entries = duplicate.getTagList("PopulationPayoutRemainders", 10);
        entries.appendTag(entries.getCompoundTagAt(0).copy()); assertRejected(duplicate);
        for (String timezone : new String[] {"CST", "Bad/Zone", ""}) {
            NBTTagCompound invalid = save(source); invalid.setString("PopulationPayoutTimezone", timezone); assertRejected(invalid);
        }
        NBTTagCompound invalid = save(source); invalid.setString("PopulationPayoutLocalTime", "25:00"); assertRejected(invalid);
        invalid = save(source); invalid.setLong("LastPopulationPayoutBoundaryMillis", 123L); assertRejected(invalid);
        invalid = save(source); invalid.setInteger("PopulationPayoutDataSchemaVersion", 77); assertRejected(invalid);
        invalid = save(source); invalid.setDouble("LastPopulationPayoutBoundaryMillis", 0.0D); assertRejected(invalid);
        invalid = save(source); invalid.getTagList("PopulationPayoutRemainders", 10).getCompoundTagAt(0)
                .setInteger("RemainderUnits", 5); assertRejected(invalid);
        invalid = save(source); NBTTagList strings = new NBTTagList(); strings.appendTag(new net.minecraft.nbt.NBTTagString("invalid"));
        invalid.setTag("PopulationPayoutRemainders", strings); assertRejected(invalid);
        invalid = save(new KOMEWorldData("pre-E-empty")); invalid.removeTag("PopulationPayoutDataSchemaVersion"); assertRejected(invalid);
    }

    @Test public void inspectionReportsUnitsSchedulesAndFailureWithoutMutating() {
        KOMEWorldData data = world("gondor", 1025L); pay(data, initializeAndNext(data));
        NBTTagCompound before = save(data);
        String lines = KOMEPopulationPayoutProcessor.inspection(data).toString();
        for (String value : Arrays.asList("America/Chicago@20:00", "rateUnits", "generatedCenti=102", "grantedCenti=102",
                "blockedCenti=0", "nextRemainderUnits=5000", "last=", "next=", "1.02", "Latest payout failure=none"))
            assertTrue(value, lines.contains(value));
        assertEquals(before, save(data));
    }

    @Test public void payoutDoesNotMutateBuildContributionsOrCompatibilityPacket() {
        KOMEWorldData data = world("gondor", 1000L);
        NBTTagCompound before = data.builds.get("B-gondor").writeToNBT(); pay(data, initializeAndNext(data));
        assertEquals(before, data.builds.get("B-gondor").writeToNBT());
        kome.common.network.KOMEPacketPopulationGui sent = new kome.common.network.KOMEPacketPopulationGui();
        sent.viewerFaction = "gondor"; sent.availablePopulation = 4; sent.activePopulation = 2; sent.dailyPopulationRateUnits = 50000L;
        io.netty.buffer.ByteBuf bytes = io.netty.buffer.Unpooled.buffer(); sent.toBytes(bytes);
        kome.common.network.KOMEPacketPopulationGui received = new kome.common.network.KOMEPacketPopulationGui(); received.fromBytes(bytes);
        assertEquals(4, received.availablePopulation); assertEquals(2, received.activePopulation); assertEquals(50000L, received.dailyPopulationRateUnits);
    }

    @Test public void authoritativePathHasNoLegacyOrFloatingArithmetic() throws Exception {
        String payout = text("src/main/java/kome/common/data/KOMEPopulationPayoutProcessor.java");
        assertTrue(payout.contains("KOMEPopulationService.grantCenti("));
        for (String forbidden : Arrays.asList("KOMEPopulationService.grant(", "getFixedUnitsPerDay(", "getPopulationCapValue(", "getAllDailyPopulationRates(", "double ", "float ", "Long.MAX_VALUE"))
            assertFalse(forbidden, payout.contains(forbidden));
        String rates = text("src/main/java/kome/common/data/KOMEPopulationRateService.java");
        assertFalse(rates.contains("double ")); assertFalse(rates.contains("float "));
        assertTrue(rates.contains("getExactDailyPopulationRates(data, KOMEConfigRegistry.population())"));
        assertFalse(java.nio.file.Files.exists(java.nio.file.Paths.get("src/main/java/kome/common/data/KOMEHalfHourService.java")));
    }

    @Test public void rejectedPlanningPreservesRawOwnershipAndEveryPayoutField() {
        KOMEWorldData data = world("gondor", 1025L);
        add(data, "rohan", KOMEBuildType.NORMAL, 1025L);
        KOMEPopulationService.grantCenti(data, "rohan", Long.MAX_VALUE);
        Instant due = initializeAndNext(data);
        KOMEConquestTile nativeTile = data.conquestTiles.get("T-GONDOR"), fallbackTile = data.conquestTiles.get("T-ROHAN");
        nativeTile.currentRulingFaction = " Gondor "; nativeTile.ownerFaction = "mordor";
        fallbackTile.currentRulingFaction = " \t"; fallbackTile.ownerFaction = " ROHAN ";
        data.populationPayoutRemainders.put("gondor", 7L); data.populationPayoutRemainders.put("rohan", 9L);
        data.setDirty(false);
        PayoutState before = new PayoutState(data);
        NBTTagCompound buildBefore = data.builds.get("B-gondor").writeToNBT();
        KOMEPopulationPayoutProcessor.Result result = KOMEPopulationPayoutProcessor.processLiveDueBoundaries(data, due);
        assertFalse(result.success); assertTrue(result.message.contains("rohan"));
        assertEquals(0L, result.committedTransitions);
        // These assertions deliberately precede and avoid tile/world NBT serialization.
        assertEquals(" Gondor ", nativeTile.currentRulingFaction); assertEquals("mordor", nativeTile.ownerFaction);
        assertEquals(" \t", fallbackTile.currentRulingFaction); assertEquals(" ROHAN ", fallbackTile.ownerFaction);
        before.assertUnchanged(data);
        assertEquals(buildBefore, data.builds.get("B-gondor").writeToNBT());
    }

    @Test public void publicationFailuresRestoreBanksRemaindersTrimmedAuditAndDirtyThenRetryExactlyOnce() {
        for (boolean initiallyDirty : new boolean[] {false, true}) {
            for (boolean afterSuper : new boolean[] {false, true}) {
                // First bank write, persisted audit append, and final transaction dirty hook.
                for (int failAt : new int[] {1, 4, 5}) {
                    DirtyFailureData data = failureWorld();
                    add(data, "mordor", KOMEBuildType.NORMAL, 1025L);
                    add(data, "rohan", KOMEBuildType.NORMAL, 1025L);
                    KOMEPopulationService.grantCenti(data, "gondor", 10L);
                    KOMEPopulationService.grantCenti(data, "rohan", 20L);
                    Instant due = initializeAndNext(data);
                    data.populationPayoutRemainders.put("gondor", 7L);
                    data.populationPayoutRemainders.put("orphan", 9L);
                    fillAudit(data);
                    data.setDirty(initiallyDirty);
                    PayoutState before = new PayoutState(data);
                    data.arm(failAt, afterSuper, () -> {
                        assertEquals(112L, bank(data, "gondor")); // already mutated, even at the first hook
                        if (failAt >= 4) {
                            assertEquals(102L, bank(data, "mordor"));
                            assertEquals(122L, bank(data, "rohan"));
                            assertEquals(5007L, remainder(data, "gondor"));
                            assertEquals(due.toEpochMilli(), data.lastPopulationPayoutBoundaryMillis);
                            assertEquals("PAID", lastAction(data));
                            assertEquals(KOMEAuditService.MAX_ENTRIES, data.centralAudit.size());
                            assertEquals("old-1", data.centralAudit.get(0).subject); // old-0 has been trimmed
                        }
                    });
                    KOMEPopulationPayoutProcessor.Result failed = KOMEPopulationPayoutProcessor.processLiveDueBoundaries(data, due);
                    assertFalse(failed.success); assertTrue(failed.message.contains("injected dirty failure"));
                    assertEquals(0L, failed.committedTransitions); assertEquals(0L, failed.processedBoundaries);
                    assertEquals(failAt, data.dirtyCalls);
                    before.assertUnchanged(data);
                    assertFalse(data.factionPopulations.containsKey("mordor"));
                    assertFalse(data.populationPayoutLastFailure.isEmpty());
                    data.disarm();
                    KOMEPopulationPayoutProcessor.Result retry = pay(data, due);
                    assertEquals(1L, retry.committedTransitions);
                    assertEquals(112L, bank(data, "gondor")); assertEquals(102L, bank(data, "mordor"));
                    assertEquals(122L, bank(data, "rohan"));
                    PayoutState paid = new PayoutState(data);
                    assertEquals(0L, pay(data, due).committedTransitions); paid.assertUnchanged(data);
                }
            }
        }
    }

    @Test public void everyCursorTransitionRollsBackAuditIdentityAndDirtyOnFailure() {
        for (String action : new String[] {"INITIALIZED", "FROZEN", "SKIPPED", "RECONCILED"}) {
            for (boolean initiallyDirty : new boolean[] {false, true}) {
                for (boolean afterSuper : new boolean[] {false, true}) {
                    for (int failAt : new int[] {1, 2}) {
                        config.set("dailyBatch.localTime", "20:00", "population.offlinePopulationCatchUp", "true");
                        DirtyFailureData data = failureWorld();
                        Instant now = prepareTransition(data, action);
                        fillAudit(data); data.setDirty(initiallyDirty);
                        PayoutState before = new PayoutState(data);
                        data.arm(failAt, afterSuper, () -> {
                            assertTrue(data.populationPayoutInitialized);
                            assertEquals(action, lastAction(data));
                            assertEquals("old-1", data.centralAudit.get(0).subject);
                        });
                        KOMEPopulationPayoutProcessor.Result failed = KOMEPopulationPayoutProcessor.initializeOrProcessStartup(data, now);
                        assertFalse(action, failed.success); assertEquals(0L, failed.committedTransitions);
                        before.assertUnchanged(data);
                        if ("INITIALIZED".equals(action)) assertFalse(data.populationPayoutInitialized);
                        data.disarm();
                        KOMEPopulationPayoutProcessor.Result retry = KOMEPopulationPayoutProcessor.initializeOrProcessStartup(data, now);
                        assertTrue(retry.message, retry.success); assertEquals(1L, retry.committedTransitions);
                        assertEquals(action, lastAction(data)); assertEquals(0L, bank(data, "gondor"));
                        PayoutState committed = new PayoutState(data);
                        assertEquals(0L, KOMEPopulationPayoutProcessor.initializeOrProcessStartup(data, now).committedTransitions);
                        committed.assertUnchanged(data);
                    }
                }
            }
        }
    }

    @Test public void reconciliationFailureReportsEarlierPaidOrSkippedCommitAndRuntimeRetriesOnlyAnchor() {
        for (boolean catchUp : new boolean[] {true, false}) {
            config.set("dailyBatch.localTime", "20:00", "population.offlinePopulationCatchUp", Boolean.toString(catchUp));
            DirtyFailureData data = failureWorld();
            Instant due = initializeAndNext(data); data.setDirty(false);
            config.set("dailyBatch.localTime", "09:15");
            data.arm(catchUp ? 5 : 4, true, () -> assertEquals("RECONCILED", lastAction(data)));
            java.util.List<String> failures = new java.util.ArrayList<String>();
            KOMEPopulationPayoutRuntime runtime = new KOMEPopulationPayoutRuntime(failures::add);
            KOMEPopulationPayoutProcessor.Result failed = runtime.onStartup(data, due);
            assertFalse(failed.success); assertFalse(runtime.hasStarted(data));
            assertEquals(1L, failed.committedTransitions);
            assertEquals(catchUp ? 1L : 0L, failed.processedBoundaries);
            assertEquals(!catchUp, failed.skipped);
            assertEquals(catchUp ? 102L : 0L, bank(data, "gondor"));
            assertEquals(catchUp ? 5000L : 0L, remainder(data, "gondor"));
            assertEquals(due.toEpochMilli(), data.lastPopulationPayoutBoundaryMillis);
            assertEquals("20:00", data.populationPayoutLocalTime);
            assertEquals(catchUp ? "PAID" : "SKIPPED", lastAction(data));
            assertTrue(data.isDirty()); // the earlier complete transition must still be saved
            assertTrue(failures.get(0).contains("prior committed transitions retained=1"));
            data.disarm();
            KOMEPopulationPayoutProcessor.Result retry = runtime.onStartup(data, due);
            assertTrue(retry.message, retry.success); assertTrue(runtime.hasStarted(data));
            assertEquals(1L, retry.committedTransitions); assertEquals(0L, retry.processedBoundaries);
            assertEquals("09:15", data.populationPayoutLocalTime);
            assertEquals(catchUp ? 102L : 0L, bank(data, "gondor"));
            PayoutState committed = new PayoutState(data);
            assertNull(runtime.onStartup(data, due)); assertTrue(runtime.onLiveCheck(data, due).success);
            committed.assertUnchanged(data);
        }
    }

    @Test public void consoleFailuresAfterEveryCommitRemainSuccessfulAndNeverPermitDuplicatePayout() {
        for (String action : new String[] {"INITIALIZED", "PAID", "FROZEN", "SKIPPED", "RECONCILED"}) {
            config.set("dailyBatch.localTime", "20:00", "population.offlinePopulationCatchUp", "true");
            DirtyFailureData data = failureWorld();
            Instant now = prepareTransition(data, action);
            KOMEPopulationPayoutRuntime runtime = new KOMEPopulationPayoutRuntime(message -> fail("Committed work was reported retryable: " + message));
            java.io.PrintStream originalOut = System.out, originalErr = System.err;
            java.io.PrintStream broken = new java.io.PrintStream(new java.io.ByteArrayOutputStream()) {
                @Override public void println(String line) { throw new IllegalStateException("injected console failure"); }
            };
            try {
                System.setOut(broken); System.setErr(broken);
                KOMEPopulationPayoutProcessor.Result result = runtime.onStartup(data, now);
                assertTrue(action + ": " + result.message, result.success);
                assertEquals(1L, result.committedTransitions);
                assertEquals(action, lastAction(data)); assertTrue(data.isDirty());
                assertTrue(runtime.hasStarted(data)); assertEquals("", data.populationPayoutLastFailure);
                assertEquals("PAID".equals(action) ? 102L : 0L, bank(data, "gondor"));
                PayoutState committed = new PayoutState(data);
                assertNull(runtime.onStartup(data, now));
                assertEquals(0L, runtime.onLiveCheck(data, now).committedTransitions);
                committed.assertUnchanged(data);
            } finally { System.setOut(originalOut); System.setErr(originalErr); broken.close(); }
        }
    }

    @Test public void overlapScheduleChangeProcessesOldDayThenUsesOnlyUnpaidEarlierOccurrence() {
        for (boolean catchUp : new boolean[] {true, false}) {
            config.set("dailyBatch.localTime", "20:00", "population.offlinePopulationCatchUp", Boolean.toString(catchUp));
            KOMEWorldData data = world("gondor", 1000L);
            Instant oldAnchor = Instant.parse("2026-10-31T01:00:00Z");
            assertTrue(KOMEPopulationPayoutProcessor.initializeOrProcessStartup(data, oldAnchor).initialized);
            config.set("dailyBatch.localTime", "01:30");
            Instant restart = Instant.parse("2026-11-01T07:15:00Z"); // second 01:15 (CST)
            assertEquals("-06:00", restart.atZone(ZoneId.of("America/Chicago")).getOffset().toString());
            KOMEPopulationPayoutProcessor.Result result = KOMEPopulationPayoutProcessor.initializeOrProcessStartup(data, restart);
            assertTrue(result.message, result.success);
            assertEquals(2L, result.committedTransitions); // old PAID/SKIPPED plus new anchor
            assertEquals(catchUp ? 1L : 0L, result.processedBoundaries);
            assertEquals(catchUp ? 100L : 0L, bank(data, "gondor"));
            assertEquals(Instant.parse("2026-11-01T06:30:00Z").toEpochMilli(), data.lastPopulationPayoutBoundaryMillis);
            assertEquals(Instant.parse("2026-11-02T07:30:00Z"),
                    data.populationPayoutSchedule().nextBoundary(Instant.ofEpochMilli(data.lastPopulationPayoutBoundaryMillis)));
            PayoutState anchored = new PayoutState(data);
            for (int i = 0; i < 3; i++) {
                assertEquals(0L, KOMEPopulationPayoutProcessor.initializeOrProcessStartup(data, restart).committedTransitions);
                assertEquals(0L, pay(data, Instant.parse("2026-11-01T07:30:00Z")).committedTransitions);
                anchored.assertUnchanged(data);
            }
            data = reload(data);
            assertEquals(0L, KOMEPopulationPayoutProcessor.initializeOrProcessStartup(data, Instant.parse("2026-11-01T07:30:00Z")).committedTransitions);
            Instant next = Instant.parse("2026-11-02T07:30:00Z");
            assertEquals(1L, pay(data, next).committedTransitions);
            assertEquals(catchUp ? 200L : 100L, bank(data, "gondor"));
            assertEquals(0L, pay(data, next).committedTransitions);
        }
    }

    private Instant prepareTransition(DirtyFailureData data, String action) {
        if ("INITIALIZED".equals(action)) return START;
        Instant due = initializeAndNext(data);
        if ("FROZEN".equals(action)) data.warSeason.phase = KOMEWarSeasonState.Phase.MAINTENANCE;
        if ("SKIPPED".equals(action)) config.set("population.offlinePopulationCatchUp", "false");
        if ("RECONCILED".equals(action)) { config.set("dailyBatch.localTime", "09:15"); return START; }
        return due;
    }

    private static DirtyFailureData failureWorld() {
        DirtyFailureData data = new DirtyFailureData();
        data.warSeason.recordLegalConflict(0L, -1L);
        add(data, "gondor", KOMEBuildType.NORMAL, 1025L);
        return data;
    }

    private static void fillAudit(KOMEWorldData data) {
        data.centralAudit.clear();
        for (int i = 0; i < KOMEAuditService.MAX_ENTRIES; i++)
            data.centralAudit.add(new KOMEAuditEntry(i, "TEST", "OLD", "test", "old-" + i, "prior entry", ""));
    }

    private static String lastAction(KOMEWorldData data) {
        return data.centralAudit.get(data.centralAudit.size() - 1).action;
    }

    /** Snapshot raw state: serializing WorldData would itself repair tile compatibility fields. */
    private static final class PayoutState {
        final java.util.Map<String, KOMEFactionPopulation> banks;
        final java.util.Map<String, Long> balances = new java.util.HashMap<String, Long>();
        final java.util.Map<String, Long> remainders;
        final java.util.List<KOMEAuditEntry> audit;
        final long cursor;
        final boolean initialized, dirty;
        final String timezone, localTime;
        PayoutState(KOMEWorldData data) {
            banks = new java.util.HashMap<String, KOMEFactionPopulation>(data.factionPopulations);
            for (String faction : banks.keySet()) balances.put(faction, bank(data, faction));
            remainders = new java.util.HashMap<String, Long>(data.populationPayoutRemainders);
            audit = new java.util.ArrayList<KOMEAuditEntry>(data.centralAudit);
            cursor = data.lastPopulationPayoutBoundaryMillis; initialized = data.populationPayoutInitialized;
            timezone = data.populationPayoutTimezone; localTime = data.populationPayoutLocalTime; dirty = data.isDirty();
        }
        void assertUnchanged(KOMEWorldData data) {
            assertEquals(banks.keySet(), data.factionPopulations.keySet());
            for (String faction : banks.keySet()) {
                assertSame(banks.get(faction), data.factionPopulations.get(faction));
                assertEquals(balances.get(faction).longValue(), bank(data, faction));
            }
            assertEquals(remainders, data.populationPayoutRemainders);
            assertEquals(cursor, data.lastPopulationPayoutBoundaryMillis); assertEquals(initialized, data.populationPayoutInitialized);
            assertEquals(timezone, data.populationPayoutTimezone); assertEquals(localTime, data.populationPayoutLocalTime);
            assertEquals(audit, data.centralAudit); assertEquals(dirty, data.isDirty());
        }
    }

    private static final class DirtyFailureData extends KOMEWorldData {
        int failAt, dirtyCalls;
        boolean afterSuper, rejectHooks;
        Runnable observer;
        DirtyFailureData() { super("failure"); }
        void arm(int failAt, boolean afterSuper, Runnable observer) {
            this.failAt = failAt; this.afterSuper = afterSuper; this.observer = observer; dirtyCalls = 0;
        }
        void disarm() { failAt = 0; rejectHooks = false; }
        @Override public void markDirty() {
            if (rejectHooks) throw new IllegalStateException("Rollback must not call mutation hooks");
            if (failAt > 0 && ++dirtyCalls == failAt) {
                observer.run();
                if (afterSuper) super.markDirty();
                rejectHooks = true;
                throw new IllegalStateException("injected dirty failure");
            }
            super.markDirty();
        }
        @Override public void setDirty(boolean value) {
            if (rejectHooks) throw new IllegalStateException("Rollback must bypass overridable setDirty");
            super.setDirty(value);
        }
    }

    static KOMEWorldData world(String faction, long centiHours) {
        KOMEWorldData data = new KOMEWorldData("payout"); data.warSeason.recordLegalConflict(0L, -1L);
        add(data, faction, KOMEBuildType.NORMAL, centiHours); return data;
    }
    static KOMEPlayerBuild add(KOMEWorldData data, String faction, KOMEBuildType type, long centiHours) {
        KOMEConquestTile tile = new KOMEConquestTile("T-" + faction); tile.claim(faction, 0L); data.conquestTiles.put(tile.id, tile);
        KOMEPlayerBuild build = new KOMEPlayerBuild(); build.id = "B-" + faction; build.tileId = tile.id;
        build.populationFaction = faction; build.type = type;
        KOMEBuildContribution contribution = new KOMEBuildContribution(); contribution.id = "H"; contribution.centiHours = centiHours;
        contribution.status = KOMEBuildContribution.APPROVED; build.contributions.add(contribution); data.builds.put(build.id, build); return build;
    }
    static Instant initializeAndNext(KOMEWorldData data) {
        assertTrue(KOMEPopulationPayoutProcessor.initializeOrProcessStartup(data, START).success);
        return KOMEPopulationPayoutProcessor.nextBoundary(Instant.ofEpochMilli(data.lastPopulationPayoutBoundaryMillis));
    }
    static KOMEPopulationPayoutProcessor.Result pay(KOMEWorldData data, Instant due) {
        KOMEPopulationPayoutProcessor.Result result = KOMEPopulationPayoutProcessor.processLiveDueBoundaries(data, due);
        assertTrue(result.message, result.success); return result;
    }
    static long bank(KOMEWorldData data, String faction) { return KOMEPopulationService.getAvailablePopulationCenti(data, faction); }
    static long remainder(KOMEWorldData data, String faction) { return data.populationPayoutRemainders.containsKey(faction) ? data.populationPayoutRemainders.get(faction) : 0L; }
    static NBTTagCompound save(KOMEWorldData data) { NBTTagCompound tag = new NBTTagCompound(); data.writeToNBT(tag); return tag; }
    static KOMEWorldData reload(KOMEWorldData data) { KOMEWorldData restored = new KOMEWorldData("restored"); restored.readFromNBT(save(data)); return restored; }
    private static void assertRejected(NBTTagCompound invalid) {
        NBTTagCompound original = (NBTTagCompound) invalid.copy(); KOMEWorldData data = new KOMEWorldData("rejected");
        try { data.readFromNBT(invalid); fail("Expected payout schema rejection"); } catch (IllegalStateException expected) { assertTrue(expected.getMessage().contains("payout")); }
        assertTrue(data.isWriteBlocked()); assertFalse(data.isDirty()); assertFalse(data.isIntegratedRootInitialized());
        assertTrue(data.builds.isEmpty()); assertTrue(data.factionPopulations.isEmpty());
        try { data.initializeIntegratedWorld(); fail(); } catch (IllegalStateException expected) { }
        try { data.writeToNBT(invalid); fail(); } catch (IllegalStateException expected) { }
        assertEquals(original, invalid);
    }
    static String text(String path) throws Exception { return new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(path)), java.nio.charset.StandardCharsets.UTF_8); }
}
