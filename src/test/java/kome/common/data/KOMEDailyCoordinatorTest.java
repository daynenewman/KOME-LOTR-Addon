package kome.common.data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Actual population authorities and atomic compressed canonical saves, not fake successful stages. */
public class KOMEDailyCoordinatorTest {
    @Rule public final KOMETileTestResources geometry = new KOMETileTestResources();
    @Rule public final TemporaryFolder files = new TemporaryFolder();
    private KOMEPopulationTestConfig config;
    private final Instant start = Instant.parse("2026-01-10T18:00:00Z");
    private final List<String> notifications = new ArrayList<String>();
    @Before public void setup() throws Exception {
        config = new KOMEPopulationTestConfig(); config.set("population.offlinePopulationCatchUp", "true");
        KOMENotificationService.setMinecraftBroadcaster(notifications::add);
    }
    @After public void cleanup() throws Exception { KOMENotificationService.resetMinecraftBroadcaster(); config.close(); }
    private KOMEWorldData world() {
        KOMEWorldData data = KOMEPopulationPayoutProcessorTest.world("gondor", 10000);
        data.builds.get("B-gondor").developedNativeCentiHours = 0;
        assertTrue(new KOMEPopulationPayoutRuntime().onStartup(data, start).success);
        return data;
    }
    private Instant due(KOMEWorldData data) { return KOMEPopulationPayoutProcessor.nextBoundary(Instant.ofEpochMilli(data.lastPopulationPayoutBoundaryMillis)); }
    private KOMEWorldData read(Path file) throws Exception {
        KOMEWorldData data = new KOMEWorldData("reloaded");
        try (java.io.InputStream input = Files.newInputStream(file)) { data.readFromNBT(CompressedStreamTools.readCompressed(input).getCompoundTag("data")); }
        return data;
    }

    @Test public void realDevelopmentThenPayoutIsDurableAndRepeatedBoundaryDoesNothing() throws Exception {
        KOMEWorldData data = world(); Instant due = due(data);
        Path file = files.getRoot().toPath().resolve("KOME_ServerRules.dat");
        KOMEWorldCheckpoint checkpoint = new KOMEWorldCheckpoint(file);
        KOMEDailyCoordinator coordinator = new KOMEDailyCoordinator(Clock.fixed(due, ZoneOffset.UTC));
        coordinator.startSession(data, start); coordinator.process(data, checkpoint, start);
        assertTrue(coordinator.process(data, checkpoint).handled);
        KOMEWorldData loaded = read(file);
        assertEquals(100, loaded.builds.get("B-gondor").developedNativeCentiHours);
        assertEquals(10, KOMEPopulationPayoutProcessorTest.bank(loaded, "gondor"));
        assertEquals("COMPLETE", loaded.dailyJournal.status()); assertEquals(due.toEpochMilli(), loaded.dailyJournal.lastComplete());
        List<String> stages = new ArrayList<String>();
        for (KOMEAuditEntry entry : loaded.centralAudit) if ("DAILY".equals(entry.domain)) stages.add(entry.action);
        assertEquals(Arrays.asList("MOVEMENT", "CONFLICT", "DEVELOPMENT", "PAYOUT", "STARVATION", "EVENTS", "SUMMARY"), stages);
        byte[] before = Files.readAllBytes(file);
        assertFalse(coordinator.process(data, checkpoint).handled);
        assertArrayEquals(before, Files.readAllBytes(file)); assertEquals(1, notifications.size());
    }

    @Test public void everyFailedStageSaveRetriesWithoutDoubleEffectsOrPrematureSummary() throws Exception {
        for (int failAt = 0; failAt <= 7; failAt++) {
            KOMEWorldData data = world(); Instant due = due(data);
            Path file = files.getRoot().toPath().resolve("stage-" + failAt + ".dat");
            KOMEWorldCheckpoint checkpoint = new KOMEWorldCheckpoint(file);
            KOMEDailyCoordinator coordinator = new KOMEDailyCoordinator();
            coordinator.startSession(data, start); coordinator.process(data, checkpoint, start);
            final int target = failAt; final boolean[] failed = { false };
            int sent = notifications.size();
            coordinator.process(data, value -> {
                if (!failed[0] && value.dailyJournal.boundary() >= 0 && value.dailyJournal.completedStages() == target) {
                    failed[0] = true; throw new IOException("injected stage " + target);
                }
                checkpoint.save(value);
            }, due);
            assertTrue(failed[0]); assertEquals(sent, notifications.size());
            assertNotEquals("COMPLETE", read(file).dailyJournal.status());
            coordinator.process(data, checkpoint, due);
            KOMEWorldData saved = read(file);
            assertEquals(100, saved.builds.get("B-gondor").developedNativeCentiHours);
            assertEquals(10, KOMEPopulationPayoutProcessorTest.bank(saved, "gondor"));
            coordinator.process(data, checkpoint, due);
            // A crash/failure after the notification claim may omit that notification, never duplicate it.
            assertTrue(notifications.size() - sent <= 1);
            assertEquals(due.toEpochMilli(), saved.dailyJournal.lastComplete());
        }
    }

    @Test public void coldRestartCatchesUpPayoutOnlyAndSkipsOfflineDevelopmentAndSummaries() throws Exception {
        KOMEWorldData data = world(); Instant due = due(data);
        Path file = files.getRoot().toPath().resolve("restart.dat"); KOMEWorldCheckpoint checkpoint = new KOMEWorldCheckpoint(file);
        KOMEDailyCoordinator coordinator = new KOMEDailyCoordinator(); coordinator.startSession(data, start); coordinator.process(data, checkpoint, start);
        coordinator.process(data, checkpoint, due);
        KOMEWorldData loaded = read(file); Instant later = due.plusSeconds(3 * 86400);
        KOMEPopulationPayoutRuntime runtime = new KOMEPopulationPayoutRuntime(); assertTrue(runtime.onStartup(loaded, later).success);
        KOMEDailyCoordinator restarted = new KOMEDailyCoordinator(); restarted.startSession(loaded, later);
        assertFalse(restarted.process(loaded, checkpoint, later).handled);
        assertEquals(100, loaded.builds.get("B-gondor").developedNativeCentiHours);
        assertEquals(40, KOMEPopulationPayoutProcessorTest.bank(loaded, "gondor")); assertEquals(1, notifications.size());
        assertEquals(due.toEpochMilli(), loaded.dailyJournal.lastComplete());
        assertTrue(runtime.onStartup(read(file), later).success);
        assertEquals(40, KOMEPopulationPayoutProcessorTest.bank(read(file), "gondor"));
    }

    @Test public void restartBeforeDevelopmentPreservesReceiptsAndDoesNotReplayAnOfflineStage() throws Exception {
        KOMEWorldData data = world(); Instant due = due(data);
        Path file = files.getRoot().toPath().resolve("interrupted.dat"); KOMEWorldCheckpoint checkpoint = new KOMEWorldCheckpoint(file);
        KOMEDailyCoordinator coordinator = new KOMEDailyCoordinator(); coordinator.startSession(data, start); coordinator.process(data, checkpoint, start);
        coordinator.process(data, value -> { if (value.dailyJournal.completedStages() == 3) throw new IOException("stop after in-memory development"); checkpoint.save(value); }, due);
        KOMEWorldData loaded = read(file); assertEquals(2, loaded.dailyJournal.completedStages());
        assertEquals(0, loaded.builds.get("B-gondor").developedNativeCentiHours);
        assertTrue(new KOMEPopulationPayoutRuntime().onStartup(loaded, due).success);
        KOMEDailyCoordinator restarted = new KOMEDailyCoordinator(); restarted.startSession(loaded, due); restarted.process(loaded, checkpoint, due);
        assertEquals(0, loaded.builds.get("B-gondor").developedNativeCentiHours);
        assertEquals(-1, loaded.dailyJournal.lastComplete()); assertTrue(notifications.isEmpty());
        assertTrue(loaded.centralAudit.stream().anyMatch(e -> "DAILY".equals(e.domain) && "INTERRUPTED".equals(e.action)));
    }

    @Test public void missingMovementAndStarvationAuthoritiesAreVisibleAndKeepLegacyRuntime() throws Exception {
        KOMEWorldData data = world(); Instant due = due(data);
        KOMEDailyCoordinator coordinator = new KOMEDailyCoordinator();
        KOMEDailyCoordinator.Checkpoint checkpoint = value -> { };
        coordinator.startSession(data, start); coordinator.process(data, checkpoint, start);
        KOMEArmyCompany company = new KOMEArmyCompany(); data.armyCompanies.put("C1", company);
        assertFalse(coordinator.process(data, checkpoint, due).handled);
        assertTrue(data.dailyJournal.reason().contains("KOM47")); assertEquals(-1, data.dailyJournal.lastComplete());
        int audit = data.centralAudit.size(); coordinator.process(data, checkpoint, due); assertEquals(audit, data.centralAudit.size());
        data.armyCompanies.clear(); data.hiredUnits.put(java.util.UUID.randomUUID(), new KOMEHiredUnitRecord());
        assertFalse(coordinator.process(data, checkpoint, due).handled); assertTrue(data.dailyJournal.reason().contains("KOM24"));
        assertTrue(notifications.isEmpty());
    }

    @Test public void clockRegressionAndScheduleChangeNeverRepeatACompletedBoundary() throws Exception {
        KOMEWorldData data = world(); Instant due = due(data);
        Path file = files.getRoot().toPath().resolve("clock.dat"); KOMEWorldCheckpoint checkpoint = new KOMEWorldCheckpoint(file);
        KOMEDailyCoordinator coordinator = new KOMEDailyCoordinator(); coordinator.startSession(data, start); coordinator.process(data, checkpoint, start);
        coordinator.process(data, checkpoint, due);
        assertFalse(coordinator.process(data, checkpoint, start).handled); assertTrue(data.dailyJournal.reason().contains("CLOCK_REGRESSED"));
        final int[] repeatedSaves = {0};
        coordinator.process(data, value -> { repeatedSaves[0]++; checkpoint.save(value); }, start);
        assertEquals("Repeated blocked clock checks must not sync the same file every server tick", 0, repeatedSaves[0]);
        config.set("dailyBatch.localTime", "09:15"); coordinator.process(data, checkpoint, due.plusSeconds(60));
        assertEquals(10, KOMEPopulationPayoutProcessorTest.bank(data, "gondor")); assertEquals(1, notifications.size());
        assertEquals(due.toEpochMilli(), read(file).dailyJournal.lastComplete());
    }

    @Test public void resetPrecedesDueDailyWorkAndPreservesInterruptedCursor() throws Exception {
        KOMEWorldData data = world(); Instant boundary = due(data);
        KOMEDailyCoordinator coordinator = new KOMEDailyCoordinator();
        KOMEWorldCheckpoint checkpoint = new KOMEWorldCheckpoint(files.getRoot().toPath().resolve("reset.dat"));
        coordinator.startSession(data, start); coordinator.process(data, checkpoint, start);
        coordinator.process(data, value -> {
            if (value.dailyJournal.completedStages() == 2) throw new IOException("interrupted before development");
            checkpoint.save(value);
        }, boundary);
        data.warSeason.phase = KOMEWarSeasonState.Phase.RESET;
        NBTTagCompound journal = data.dailyJournal.write();
        int audit = data.centralAudit.size();
        assertTrue(coordinator.process(data, value -> { fail("RESET must not checkpoint ordinary daily work"); }, boundary).handled);
        assertTrue(coordinator.process(data, (net.minecraft.world.World)null, boundary).handled);
        assertEquals(journal, data.dailyJournal.write()); assertEquals(audit, data.centralAudit.size());
        assertEquals(0, data.builds.get("B-gondor").developedNativeCentiHours);
        assertEquals(0, KOMEPopulationPayoutProcessorTest.bank(data, "gondor"));
        assertTrue(notifications.isEmpty());
    }

    @Test public void actualResetTickSkipsDevelopmentAndDoesNotCatchItUpAfterCompletion() throws Exception {
        KOMEWorldData data = world(); Instant boundary = due(data);
        KOMEPopulationPayoutRuntime runtime = new KOMEPopulationPayoutRuntime();
        KOMEEvents.processCampaignTick(data, null, start.toEpochMilli(), runtime);
        data.warSeason.phase = KOMEWarSeasonState.Phase.RESET;
        NBTTagCompound journal = data.dailyJournal.write();
        KOMEEvents.processCampaignTick(data, null, boundary.toEpochMilli(), runtime);
        assertEquals(1, data.seasonReset.seasonId);
        assertFalse(data.seasonReset.complete()); // unavailable real checkpoint fails closed
        assertEquals(journal, data.dailyJournal.write());
        assertEquals(0, data.builds.get("B-gondor").developedNativeCentiHours);
        assertEquals(0, KOMEPopulationPayoutProcessorTest.bank(data, "gondor"));
        assertTrue(notifications.isEmpty());
        data.warSeason.phase = KOMEWarSeasonState.Phase.WAR;
        runtime.onLiveCheck(data, boundary);
        assertEquals(0, data.builds.get("B-gondor").developedNativeCentiHours);
        assertEquals(0, KOMEPopulationPayoutProcessorTest.bank(data, "gondor"));
    }

    @Test public void malformedJournalRejectsTheWholeCandidateWithoutPartialPublication() {
        KOMEWorldData original = world(); NBTTagCompound tag = KOMEPopulationPayoutProcessorTest.save(original);
        tag.getCompoundTag("DailyJournal").setInteger("NextStage", 99);
        KOMEWorldData target = new KOMEWorldData("bad");
        try { target.readFromNBT(tag); fail(); } catch (IllegalStateException expected) { assertTrue(target.isWriteBlocked()); }
        assertTrue(target.builds.isEmpty()); assertEquals(-1, target.dailyJournal.boundary());
    }

    @Test public void unavailableWorldCannotPretendToHaveCheckpointed() {
        KOMEWorldData data = world(); KOMEDailyCoordinator coordinator = new KOMEDailyCoordinator();
        assertFalse(coordinator.process(data, (net.minecraft.world.World) null, start).handled);
        assertTrue(data.dailyJournal.reason().contains("PERSISTENCE_UNAVAILABLE")); assertTrue(notifications.isEmpty());
    }
}
