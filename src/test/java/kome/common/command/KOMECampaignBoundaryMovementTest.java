package kome.common.command;

import kome.common.data.*;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.profiler.Profiler;
import net.minecraft.world.*;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.ISaveHandler;
import org.junit.Test;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import static org.junit.Assert.*;

public class KOMECampaignBoundaryMovementTest {
    private static final Instant START = Instant.parse("2026-01-10T12:00:00Z");
    private static final Instant DUE = Instant.parse("2026-01-11T02:00:00Z");

    @Test public void newFootAndMountedRoutesConsumeOnlySuccessfulDepartures() {
        for (int allowance : new int[] {1, 2}) {
            KOMEArmyMovementOrder order = KOMEArmyMovementOrder.newRoute(allowance);
            assertEquals(allowance, order.dailyStepsRemaining);
            assertFalse(order.tryDepart(true, () -> false));
            assertEquals(allowance, order.dailyStepsRemaining);
            assertTrue(order.tryDepart(true, () -> true));
            assertEquals(allowance - 1, order.dailyStepsRemaining);
            if (allowance == 2) assertTrue(order.tryDepart(true, () -> true));
            assertFalse(order.tryDepart(true, () -> { fail("Spent allowance must not attempt departure"); return true; }));
            NBTTagCompound tag = order.writeToNBT();
            KOMEArmyMovementOrder restored = new KOMEArmyMovementOrder(); restored.readFromNBT(tag);
            assertEquals(0, restored.dailyStepsRemaining);
        }
    }

    @Test public void observedResetIsOnceAndRestartDoesNotReplayMissedMovement() throws Exception {
        try (KOMEPopulationTestConfig ignored = new KOMEPopulationTestConfig()) {
            KOMEWorldData data = new KOMEWorldData("movement");
            KOMEArmyMovementOrder order = route(data, 2);
            order.dailyStepsRemaining = 0;
            KOMECommandTroops.anchorMovementSchedule(data, START.toEpochMilli());
            KOMECommandTroops.resetDailyMovementAllowances(data, DUE.minusMillis(1).toEpochMilli());
            assertEquals(0, order.dailyStepsRemaining);
            KOMECommandTroops.resetDailyMovementAllowances(data, DUE.toEpochMilli());
            assertEquals(2, order.dailyStepsRemaining);
            assertTrue(order.tryDepart(true, () -> true));
            KOMECommandTroops.resetDailyMovementAllowances(data, DUE.toEpochMilli());
            assertEquals(1, order.dailyStepsRemaining);
            NBTTagCompound tag = new NBTTagCompound(); data.writeToNBT(tag);
            KOMEWorldData restored = new KOMEWorldData("restart"); restored.readFromNBT(tag);
            Instant restart = DUE.plusSeconds(86400L * 8);
            KOMECommandTroops.anchorMovementSchedule(restored, restart.toEpochMilli());
            KOMECommandTroops.resetDailyMovementAllowances(restored, restart.toEpochMilli());
            assertEquals(1, restored.armyMovements.get(order.id).dailyStepsRemaining);
            assertTrue(restored.armyMovements.get(order.id).nextDailyStepMillis > restart.toEpochMilli());
        }
    }

    @Test public void blockedWorldThenMissingUnitPreserveAllowanceUntilRealDepartureCommits() throws Exception {
        try (KOMEPopulationTestConfig ignored = new KOMEPopulationTestConfig()) {
            KOMEWorldData data = new KOMEWorldData("movement");
            KOMEArmyMovementOrder order = route(data, 1);
            UUID unit = UUID.randomUUID(); order.units.add(unit);
            assertTrue(KOMECommandTroops.processWaitingStepDepartures(data, null, START.toEpochMilli()));
            assertEquals("DEPARTURE_DIMENSION_UNAVAILABLE", order.lastSpawnFailureCode);
            assertEquals(1, order.dailyStepsRemaining);
            World world = world();
            assertTrue(KOMECommandTroops.processWaitingStepDepartures(data, world, order.nextStepDepartureMillis));
            assertEquals("MISSING_UNIT_RECORD", order.lastSpawnFailureCode);
            assertEquals(1, order.dailyStepsRemaining);
            KOMEHiredUnitRecord record = new KOMEHiredUnitRecord();
            record.entity = unit; record.movementOrderId = order.id;
            record.stationedEntityData = new NBTTagCompound(); data.hiredUnits.put(unit, record);
            assertTrue(KOMECommandTroops.processWaitingStepDepartures(data, world, order.nextStepDepartureMillis));
            assertEquals(KOMEArmyMovementOrder.MOVING, order.status);
            assertEquals(0, order.dailyStepsRemaining);
            assertEquals("", order.lastSpawnFailureCode);
            assertFalse(KOMECommandTroops.processWaitingStepDepartures(data, world, order.nextStepDepartureMillis));
            assertEquals(0, order.dailyStepsRemaining);
        }
    }

    @Test public void actualArrivalAndHistoryPublishBeforeSameBoundaryCapturedBuildPayout() throws Exception {
        try (KOMEPopulationTestConfig ignored = new KOMEPopulationTestConfig()) {
            ArrivalObservedData data = new ArrivalObservedData();
            data.warSeason.recordLegalConflict(0L, -1L);
            KOMEArmyMovementOrder order = route(data, 1);
            order.status = KOMEArmyMovementOrder.MOVING; order.dailyStepsRemaining = 0;
            order.arrivalMillis = DUE.toEpochMilli();
            order.arrivalX = 10.0D; order.arrivalY = 64.0D; order.arrivalZ = 20.0D;
            KOMEPlayerBuild build = new KOMEPlayerBuild(); build.id = "captured"; build.tileId = "T002";
            build.populationFaction = "rohan"; build.type = KOMEBuildType.NORMAL;
            KOMEBuildContribution hours = new KOMEBuildContribution(); hours.id = "hours";
            hours.centiHours = 1000L; hours.status = KOMEBuildContribution.APPROVED;
            build.contributions.add(hours); build.developedNativeCentiHours = 1000L;
            data.builds.put(build.id, build);
            KOMEPopulationPayoutRuntime runtime = new KOMEPopulationPayoutRuntime();
            assertTrue(runtime.onStartup(data, START).success);
            assertTrue(KOMEEvents.processCampaignTick(data, world(), DUE.toEpochMilli(), runtime).success);
            assertEquals(KOMEArmyMovementOrder.ARRIVED, order.status);
            assertEquals("T002", order.currentTile);
            assertTrue(data.sawPayoutAfterArrival);
            assertEquals(50L, KOMEPopulationService.getAvailablePopulationCenti(data, "gondor"));
            assertEquals(0L, KOMEPopulationService.getAvailablePopulationCenti(data, "rohan"));
            KOMEEvents.processCampaignTick(data, world(), DUE.toEpochMilli(), runtime);
            assertEquals(50L, KOMEPopulationService.getAvailablePopulationCenti(data, "gondor"));
        }
    }

    @Test public void startTickOwnsOneSharedScheduleWithoutAlternateAllowanceRefresh() throws Exception {
        String events = source("data/KOMEEvents.java");
        assertTrue(events.contains("event.phase != TickEvent.Phase.START"));
        String troops = source("command/KOMECommandTroops.java");
        assertFalse(troops.contains("Calendar")); assertFalse(troops.contains("movementDailyResetTimezone"));
        assertFalse(troops.contains("dailyStepsRemaining = Math.max"));
        assertEquals(1, troops.split("KOMEArmyMovementOrder.newRoute\\(", -1).length - 1);
        assertTrue(troops.contains("order.tryDepart(isDailyMovementMode(data)"));
    }

    @Test public void zeroBudgetSurvivesRepeatedCommandsRetriesReloadAndRestartUntilObservedBoundary() throws Exception {
        try (KOMEPopulationTestConfig ignored = new KOMEPopulationTestConfig()) {
            for (int allowance : new int[] {1, 2}) {
                KOMEWorldData data = new KOMEWorldData("zero-budget");
                KOMEArmyMovementOrder order = route(data, allowance);
                KOMEConquestTile third = new KOMEConquestTile("T003"); third.claim("gondor", 0L);
                third.setAnchor(0, 10.0D, 64.0D, 20.0D); data.conquestTiles.put(third.id, third);
                order.routeTiles.add("T003"); order.traveledRouteTiles.add("T002");
                order.currentTile = "T002"; order.currentStepOriginTile = "T002";
                order.nextTile = "T003"; order.currentStepDestinationTile = "T003";
                order.destinationTile = "T003"; order.finalDestinationTile = "T003";
                order.currentRouteIndex = 1; order.nextRouteIndex = 2; order.finalRouteIndex = 2;
                order.totalSteps = 2; order.distanceTiles = 2;
                order.status = KOMEArmyMovementOrder.ACCESS_HALTED; order.dailyStepsRemaining = 0;
                World world = world();
                FixturePlayer player = (FixturePlayer) allocate(FixturePlayer.class);
                player.worldObj = world;
                Field uuid = Entity.class.getDeclaredField("entityUniqueID"); uuid.setAccessible(true);
                order.owner = UUID.randomUUID(); uuid.set(player, order.owner);
                java.lang.reflect.Method command = KOMECommandTroops.class.getDeclaredMethod("handleMovementAdmin",
                        net.minecraft.command.ICommandSender.class, net.minecraft.entity.player.EntityPlayerMP.class,
                        KOMEWorldData.class, String[].class);
                command.setAccessible(true);
                KOMECommandTroops handler = new KOMECommandTroops();
                int acceptedRetreats = 0;
                for (int i = 0; i < 4; i++) {
                    for (String action : new String[] {"resume", "retreat", "resume"}) {
                        if ("retreat".equals(action)) {
                            // A legal retreat requires a halted order; use the real ordinary stay command.
                            command.invoke(handler, player, player, data, new String[] {"movement", "stay", order.id});
                            assertEquals(0, order.dailyStepsRemaining);
                        }
                        command.invoke(handler, player, player, data, new String[] {"movement", action, order.id});
                        if ("retreat".equals(action)) acceptedRetreats++;
                        assertEquals(0, order.dailyStepsRemaining);
                        assertEquals(KOMEArmyMovementOrder.WAITING_NEXT_STEP, order.status);
                        assertEquals("T002", order.currentTile);
                        // The automatic departure retry uses the production path, not a synthetic commit.
                        assertFalse(KOMECommandTroops.processWaitingStepDepartures(data, world, order.nextStepDepartureMillis));
                    }
                    // Manual spawn retry and time overrides are operator-only, not ordinary-controller privileges.
                    for (String action : new String[] {"retry", "advance", "ticknow", "advanceall"}) {
                        NBTTagCompound before = order.writeToNBT();
                        try {
                            command.invoke(handler, player, player, data, new String[] {"movement", action, order.id});
                            fail("Ordinary controller must not receive operator action " + action);
                        } catch (java.lang.reflect.InvocationTargetException denied) {
                            assertTrue(denied.getCause() instanceof net.minecraft.command.WrongUsageException);
                        }
                        assertEquals(before, order.writeToNBT()); assertEquals(0, order.dailyStepsRemaining);
                    }
                }
                assertTrue(acceptedRetreats > 0);
                NBTTagCompound persisted = new NBTTagCompound(); data.writeToNBT(persisted);
                KOMEWorldData restored = new KOMEWorldData("restart"); restored.readFromNBT(persisted);
                KOMEArmyMovementOrder reloaded = restored.armyMovements.get(order.id);
                assertEquals(0, reloaded.dailyStepsRemaining);
                Instant restart = DUE.plusSeconds(8L * 86400L + 3600L);
                KOMEPopulationPayoutRuntime runtime = new KOMEPopulationPayoutRuntime();
                assertTrue(runtime.onStartup(restored, restart).success);
                assertNull(runtime.onStartup(restored, restart));
                assertEquals(0, reloaded.dailyStepsRemaining);
                long next = reloaded.nextDailyStepMillis;
                assertTrue(next > restart.toEpochMilli());
                KOMECommandTroops.resetDailyMovementAllowances(restored, next - 1L);
                assertEquals(0, reloaded.dailyStepsRemaining);
                assertFalse(KOMECommandTroops.processWaitingStepDepartures(restored, world, next - 1L));
                KOMECommandTroops.resetDailyMovementAllowances(restored, next);
                assertEquals(allowance, reloaded.dailyStepsRemaining);
                assertTrue(KOMECommandTroops.processWaitingStepDepartures(restored, world, next));
                assertEquals(KOMEArmyMovementOrder.MOVING, reloaded.status);
                assertEquals(allowance - 1, reloaded.dailyStepsRemaining);
                KOMECommandTroops.resetDailyMovementAllowances(restored, next);
                assertEquals(allowance - 1, reloaded.dailyStepsRemaining);
            }
        }
    }

    private static Object allocate(Class<?> type) throws Exception {
        Class<?> unsafe = Class.forName("sun.misc.Unsafe");
        Field singleton = unsafe.getDeclaredField("theUnsafe"); singleton.setAccessible(true);
        return unsafe.getMethod("allocateInstance", Class.class).invoke(singleton.get(null), type);
    }

    /** Inert, non-operator controller; real command permission/dispatch logic remains under test. */
    private static final class FixturePlayer extends net.minecraft.entity.player.EntityPlayerMP {
        private FixturePlayer() { super(null, null, null, null); }
        @Override public boolean canCommandSenderUseCommand(int level, String command) { return false; }
        @Override public String getCommandSenderName() { return "controller"; }
        @Override public void addChatMessage(net.minecraft.util.IChatComponent message) { }
    }

    private static KOMEArmyMovementOrder route(KOMEWorldData data, int allowance) {
        for (String id : new String[] {"T001", "T002"}) {
            KOMEConquestTile tile = new KOMEConquestTile(id); tile.claim("gondor", 0L);
            tile.setAnchor(0, 10.0D, 64.0D, 20.0D); data.conquestTiles.put(id, tile);
        }
        KOMEArmyMovementOrder order = KOMEArmyMovementOrder.newRoute(allowance);
        order.id = "M1"; order.ownerFaction = "gondor"; order.status = KOMEArmyMovementOrder.WAITING_NEXT_STEP;
        order.originTile = "T001"; order.destinationTile = "T002";
        order.currentTile = "T001"; order.nextTile = "T002";
        order.currentStepOriginTile = "T001"; order.currentStepDestinationTile = "T002";
        order.routeTiles.add("T001"); order.routeTiles.add("T002");
        order.currentRouteIndex = 0; order.nextRouteIndex = 1; order.distanceTiles = 1;
        order.traveledRouteTiles.add("T001"); data.armyMovements.put(order.id, order); return order;
    }

    /** Observes the real persistence hook, not an injected movement/payout callback. */
    private static final class ArrivalObservedData extends KOMEWorldData {
        boolean sawPayoutAfterArrival;
        ArrivalObservedData() { super("arrival"); }
        @Override public void markDirty() {
            if (KOMEPopulationService.getAvailablePopulationCenti(this, "gondor") > 0L) {
                assertEquals(KOMEArmyMovementOrder.ARRIVED, armyMovements.get("M1").status);
                assertEquals(KOMEMovementHistoryRecord.ARRIVED, movementHistory.get("M1").status);
                sawPayoutAfterArrival = true;
            }
            super.markDirty();
        }
    }

    /** No entity spawning or game bootstrap: exercises actual strategic arrival/departure services. */
    private static World world() throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field singleton = unsafeClass.getDeclaredField("theUnsafe"); singleton.setAccessible(true);
        FixtureWorld world = (FixtureWorld) unsafeClass.getMethod("allocateInstance", Class.class).invoke(singleton.get(null), FixtureWorld.class);
        Field provider = World.class.getDeclaredField("provider"); provider.setAccessible(true);
        provider.set(world, new WorldProviderSurface());
        world.loadedEntityList = new ArrayList<Entity>(); world.playerEntities = new ArrayList(); return world;
    }
    private static final class FixtureWorld extends World {
        private FixtureWorld() { super((ISaveHandler) null, "test", (WorldProvider) null, (WorldSettings) null, (Profiler) null); }
        @Override protected IChunkProvider createChunkProvider() { return null; }
        @Override protected int func_152379_p() { return 0; }
        @Override public Entity getEntityByID(int id) { return null; }
        @Override public IChunkProvider getChunkProvider() { return null; }
        @Override public Chunk getChunkFromChunkCoords(int x, int z) { return null; }
        @Override public boolean blockExists(int x, int y, int z) { return true; }
    }
    private static String source(String path) throws Exception {
        return new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("src/main/java/kome/common/" + path)), java.nio.charset.StandardCharsets.UTF_8);
    }
}
