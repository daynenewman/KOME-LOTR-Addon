package kome.common.data;

import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import kome.common.KOMEAccessFixture;
import lotr.common.entity.npc.LOTREntityGondorSoldier;
import lotr.common.entity.npc.LOTRHiredNPCInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static kome.common.data.KOMETileRasterSnapshotTest.*;
import static kome.common.data.KOMEServerTileAwareness.*;
import static kome.common.data.KOMETileResolution.Status.*;
import static org.junit.Assert.*;

public class KOMEServerTileAwarenessTest {
    private KOMETileWorldResolver resolver;
    private KOMEServerTileAwareness awareness;
    private KOMETileAwarenessEvents hooks;
    private TestWorld world;
    private List<Transition> events;

    @Before public void setUp() throws Exception {
        resolver = new KOMETileWorldResolver(); resolver.publish(snapshot(1, 2, 0));
        awareness = new KOMEServerTileAwareness(resolver); awareness.startSession();
        hooks = new KOMETileAwarenessEvents(awareness); world = world(173);
        events = new ArrayList<Transition>(); awareness.subscribe(events::add);
    }
    @After public void tearDown() { awareness.stopSession(); }

    static KOMETileRasterSnapshot snapshot(int a, int b, int c) throws Exception {
        return KOMETileRasterSnapshot.load(png(3, 1, argb(a), argb(b), argb(c)), text(MAPPING),
            new KOMETileRasterSnapshot.Transform(173, 1, 1, 128, 3, 1), IDS, Collections.<String>emptySet());
    }
    private static int argb(int color) { return color == 0 ? 0 : 0xFF000000 | color; }
    private void tick() { hooks.onServerTick(new TickEvent.ServerTickEvent(TickEvent.Phase.END)); }
    private Observation observation(Entity e) { return awareness.current(e.getUniqueID()).observation().get(); }
    private void position(Entity e, double x) { e.posX = x; e.posZ = -0.1; }
    private Player join(double x) throws Exception {
        Player player = player(world, UUID.randomUUID()); position(player, x);
        hooks.onLogin(new PlayerEvent.PlayerLoggedInEvent(player)); tick(); return player;
    }

    @Test public void exactFractionalNegativeBoundaryAndFreshness() throws Exception {
        Player p = join(-0.1);
        assertEquals("T001", observation(p).location.tileId);
        assertEquals(-1, observation(p).location.worldX);
        assertEquals(Type.INITIALIZED, events.get(0).type);
        p.posX = Math.nextDown(0D);
        assertEquals(Availability.STALE_POSITION, awareness.current(p.getUniqueID()).availability);
        assertFalse(awareness.current(p.getUniqueID()).observation().isPresent());
        tick(); assertEquals("T001", observation(p).location.tileId); assertEquals(1, events.size());
        p.posX = 0D; tick();
        assertEquals("T002", observation(p).location.tileId);
        Transition edge = events.get(1);
        assertEquals("T001", edge.previous().get().location.tileId);
        assertEquals("T002", edge.current().get().location.tileId);
        assertEquals(Cause.POSITION_CHANGED, edge.cause);
        p.posX = Math.nextUp(0D); tick(); assertEquals(2, events.size());
        assertEquals(awareness.currentTick(), observation(p).observedTick);
    }

    @Test public void gapOutsideAndReturnNeverInventTileOrCapturability() throws Exception {
        Player p = join(127.999);
        p.posX = 128D; tick(); assertEquals(IN_BOUNDS_GAP, observation(p).location.status);
        assertFalse(observation(p).location.capturable().isPresent());
        p.posX = 200; tick(); assertEquals(2, events.size());
        p.posX = 256; tick(); assertEquals(OUTSIDE_MASK, observation(p).location.status);
        p.posX = -1; tick(); assertEquals("T001", observation(p).location.tileId);
        assertEquals(4, events.size());
    }

    @Test public void teleportAndDimensionChangeUseActualWorldNotPersistedDimensionField() throws Exception {
        Player p = join(-64); p.dimension = 999; // The live world provider remains authoritative.
        p.posX = 64; tick(); assertEquals("T002", observation(p).location.tileId);
        TestWorld other = world(0); world.entities.remove(p.getEntityId());
        other.entities.put(p.getEntityId(), p); p.worldObj = other;
        assertEquals(Availability.STALE_POSITION, awareness.current(p.getUniqueID()).availability);
        tick(); assertEquals(UNSUPPORTED_DIMENSION, observation(p).location.status);
        assertEquals(Cause.DIMENSION_CHANGED, events.get(2).cause);
        other.entities.remove(p.getEntityId()); world.entities.put(p.getEntityId(), p); p.worldObj = world;
        tick(); assertEquals("T002", observation(p).location.tileId);
        assertEquals(Cause.DIMENSION_CHANGED, events.get(3).cause);
    }

    @Test public void capturedViewAndSnapshotReplacementAtUnchangedPosition() throws Exception {
        Player p = join(-64);
        KOMETileWorldResolver.ReadView old = resolver.readView();
        resolver.publish(snapshot(2, 1, 0));
        assertEquals("T001", old.resolveWorldPosition(173, -64, -1).tileId);
        assertEquals(Availability.STALE_GEOMETRY, awareness.current(p.getUniqueID()).availability);
        tick(); assertEquals("T002", observation(p).location.tileId);
        assertEquals(Cause.GEOMETRY_CHANGED, events.get(1).cause);
        long calls = awareness.resolutionCount();
        resolver.publish(snapshot(2, 1, 0)); tick(); // Revalidation, but no semantic transition.
        assertEquals(calls + 1, awareness.resolutionCount()); assertEquals(2, events.size());
    }

    @Test public void failuresClearPreviousAvailabilityAndRecoveryIsExplicit() throws Exception {
        Player p = join(-64); resolver.invalidate(); tick();
        assertEquals(INVALID_SNAPSHOT, observation(p).location.status);
        assertEquals(Type.RESOLUTION_FAILED, events.get(1).type);
        assertFalse(observation(p).location.resolvedTileId().isPresent());
        for (int i = 0; i < 5; i++) { resolver.invalidate(); tick(); }
        assertEquals(2, events.size());
        resolver.publish(snapshot(1, 2, 0)); tick();
        assertEquals(Type.CHANGED, events.get(2).type);
        p.posX = Double.NaN; tick();
        assertEquals(INVALID_COORDINATE, observation(p).location.status);
        assertEquals(Type.RESOLUTION_FAILED, events.get(3).type);
        tick(); assertEquals(4, events.size());
    }

    @Test public void initialFailureAndRejectedReloadHaveDifferentMeaning() throws Exception {
        resolver.invalidate(); Player p = join(-64);
        assertEquals(Type.RESOLUTION_FAILED, events.get(0).type);
        assertFalse(events.get(0).previous().isPresent());
        resolver.publish(snapshot(1, 2, 0)); tick();
        assertFalse(resolver.reload(null, text(MAPPING), snapshot(1, 2, 0).transform, IDS, Collections.<String>emptySet()));
        tick(); assertEquals("T001", observation(p).location.tileId);
        assertEquals(2, events.size()); // Failure preserves valid geometry, no fabricated location failure.
    }

    @Test public void unchangedPositionSkipsLookupAndSameTileMovementSkipsEvents() throws Exception {
        Player p = join(-64); long lookups = awareness.resolutionCount();
        for (int i = 0; i < 100; i++) tick();
        assertEquals(lookups, awareness.resolutionCount()); assertEquals(1, events.size());
        for (int i = 0; i < 10; i++) { p.posX += 0.125; tick(); }
        assertEquals(lookups + 10, awareness.resolutionCount()); assertEquals(1, events.size());
        assertEquals(p.posX, observation(p).x, 0);
    }

    @Test public void respawnReconnectAndLateOldRemovalPreserveNewIncarnation() throws Exception {
        Player old = join(-64); Observation first = observation(old);
        Player replacement = player(world, old.getUniqueID()); position(replacement, 1);
        hooks.onRespawn(new PlayerEvent.PlayerRespawnEvent(replacement));
        assertEquals(Type.REMOVED, events.get(1).type); assertEquals(Cause.RESPAWN, events.get(1).cause);
        assertEquals(Availability.PENDING_SAMPLE, awareness.current(old.getUniqueID()).availability);
        tick(); assertEquals(Type.INITIALIZED, events.get(2).type);
        assertTrue(observation(replacement).incarnation > first.incarnation);
        hooks.onLogout(new PlayerEvent.PlayerLoggedOutEvent(old));
        assertEquals("T002", observation(replacement).location.tileId);
        hooks.onLogout(new PlayerEvent.PlayerLoggedOutEvent(replacement));
        assertEquals(Availability.NOT_TRACKED, awareness.current(old.getUniqueID()).availability);
        Player reconnected = player(world, old.getUniqueID()); position(reconnected, -1);
        hooks.onLogin(new PlayerEvent.PlayerLoggedInEvent(reconnected)); tick();
        assertEquals("T001", observation(reconnected).location.tileId);
        assertEquals(Type.INITIALIZED, events.get(events.size()-1).type);
    }

    @Test public void vanillaRespawnJoinPrecedesRespawnEventWithoutDuplicateInitialization() throws Exception {
        Player old = join(-1);
        Player replacement = player(world, old.getUniqueID()); position(replacement, 1);
        world.entities.remove(old.getEntityId());
        // ServerConfigurationManager spawns the replacement before firing PlayerRespawnEvent.
        hooks.onJoin(new EntityJoinWorldEvent(replacement, world));
        hooks.onRespawn(new PlayerEvent.PlayerRespawnEvent(replacement));
        hooks.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, replacement));
        tick();
        assertEquals(3, events.size());
        assertEquals(Type.REMOVED, events.get(1).type);
        assertEquals(Type.INITIALIZED, events.get(2).type);
        assertEquals(Cause.RESPAWN, events.get(2).cause);
        assertEquals("T002", observation(replacement).location.tileId);
        assertEquals(1, awareness.trackedCount());
    }

    @Test public void deathRemovalHappensAfterLivingDeathCancellationCouldHaveRun() throws Exception {
        Player p = join(-1); p.alive = false;
        assertEquals(Availability.NOT_TRACKED, awareness.current(p.getUniqueID()).availability);
        tick(); assertEquals(Cause.DIED, events.get(1).cause);
        assertFalse(events.get(1).current().isPresent()); assertEquals(0, awareness.trackedCount());
    }

    @Test public void actualUnitAndPlayerHooksResolveEquivalentlyAndTrackNewHires() throws Exception {
        Player p = join(-0.1); Unit unit = unit(world);
        unit.hiredNPCInfo.isActive = false; position(unit, -0.1);
        hooks.onJoin(new EntityJoinWorldEvent(unit, world)); tick();
        assertEquals(1, awareness.trackedCount());
        unit.hiredNPCInfo.isActive = true;
        hooks.onLivingUpdate(new LivingEvent.LivingUpdateEvent(unit)); tick();
        assertEquals(Kind.HIRED_UNIT, observation(unit).kind);
        assertEquals(observation(p).location.toString(), observation(unit).location.toString());
        p.posX = unit.posX = 128; tick();
        assertEquals(observation(p).location.toString(), observation(unit).location.toString());
        unit.hiredNPCInfo.isActive = false; tick();
        assertEquals(Cause.NO_LONGER_RELEVANT, events.get(events.size()-1).cause);
        assertEquals(Availability.NOT_TRACKED, awareness.current(unit.getUniqueID()).availability);
    }

    @Test public void cancelledJoinAndEntityRemovedFromLiveMapNeverExposeStalePositions() throws Exception {
        Unit unit = unit(world); position(unit, -1);
        // Plain JUnit does not run FML's @Cancelable event transformer.
        EntityJoinWorldEvent canceled = new EntityJoinWorldEvent(unit, world) {
            @Override public boolean isCancelable() { return true; }
        };
        canceled.setCanceled(true);
        hooks.onJoin(canceled); tick(); assertEquals(0, awareness.trackedCount());
        hooks.onJoin(new EntityJoinWorldEvent(unit, world)); tick();
        world.entities.remove(unit.getEntityId());
        assertEquals(Availability.NOT_TRACKED, awareness.current(unit.getUniqueID()).availability);
        tick(); assertEquals(Cause.ENTITY_UNLOADED, events.get(events.size()-1).cause);
    }

    @Test public void chunkUnloadAndReloadUseOnlySuppliedEntities() throws Exception {
        Unit unit = unit(world); position(unit, -1);
        hooks.onJoin(new EntityJoinWorldEvent(unit, world)); tick();
        Chunk chunk = new Chunk(world, -1, -1); chunk.entityLists[0].add(unit);
        hooks.onChunkUnload(new ChunkEvent.Unload(chunk));
        assertEquals(0, awareness.trackedCount()); assertEquals(Cause.CHUNK_UNLOADED, events.get(1).cause);
        assertFalse(awareness.current(unit.getUniqueID()).observation().isPresent());
        hooks.onJoin(new EntityJoinWorldEvent(unit, world)); tick();
        assertEquals(Type.INITIALIZED, events.get(2).type);
    }

    @Test public void worldUnloadServerStopAndRestartClearStateAndSubscribers() throws Exception {
        Player p = join(-1); hooks.onWorldUnload(new WorldEvent.Unload(world));
        assertEquals(Cause.WORLD_UNLOADED, events.get(1).cause);
        hooks.onLogin(new PlayerEvent.PlayerLoggedInEvent(p)); tick(); long priorSession = observation(p).session;
        awareness.stopSession();
        assertEquals(Cause.SERVER_STOP, events.get(events.size()-1).cause);
        assertEquals(Availability.SERVER_STOPPED, awareness.current(p.getUniqueID()).availability);
        int before = events.size(); awareness.startSession();
        assertEquals(0, awareness.trackedCount()); p.posX = 1;
        hooks.onLogin(new PlayerEvent.PlayerLoggedInEvent(p)); tick();
        assertEquals(before, events.size()); assertTrue(observation(p).session > priorSession);
    }

    @Test public void consumerSeesPublishedObservationsWithoutChangingStrategicUnitRecords() throws Exception {
        KOMEHiredUnitRecord record = new KOMEHiredUnitRecord();
        Unit unit = unit(world); position(unit, -1); record.entity = unit.getUniqueID();
        record.currentTile = "T900"; record.companyId = "C_EXISTING"; record.sourceTileId = "T800";
        List<String> comparisons = new ArrayList<String>();
        awareness.subscribe(event -> {
            if (event.current().isPresent()) {
                Observation current = awareness.current(record.entity).observation().get();
                assertEquals(event.current().get().location.tileId, current.location.tileId);
                comparisons.add(record.companyId + ":" + current.location.status + ":" + current.location.tileId);
            } else assertFalse(awareness.current(record.entity).observation().isPresent());
        });
        hooks.onJoin(new EntityJoinWorldEvent(unit, world)); tick(); unit.posX = 1; tick(); unit.posX = 128; tick();
        assertEquals("C_EXISTING:RESOLVED:T001", comparisons.get(0));
        assertEquals("C_EXISTING:RESOLVED:T002", comparisons.get(1));
        assertEquals("C_EXISTING:IN_BOUNDS_GAP:", comparisons.get(2));
        assertEquals("T900", record.currentTile); assertEquals("T800", record.sourceTileId);
        assertEquals("C_EXISTING", record.companyId); assertNull(record.movingEntityData);
    }

    @Test public void notificationsFollowCompleteSamplingPassAndBrokenListenerIsIsolated() throws Exception {
        Player a = player(world, UUID.randomUUID()), b = player(world, UUID.randomUUID());
        position(a, -1); position(b, -1);
        hooks.onLogin(new PlayerEvent.PlayerLoggedInEvent(a)); hooks.onLogin(new PlayerEvent.PlayerLoggedInEvent(b));
        final int[] failed = {0};
        awareness.subscribe(event -> { failed[0]++; throw new IllegalStateException("test consumer"); });
        awareness.subscribe(event -> {
            if (event.type == Type.INITIALIZED) assertTrue(awareness.current(b.getUniqueID()).observation().isPresent());
        });
        tick(); assertEquals(1, failed[0]); assertEquals(2, events.size());
        a.posX = b.posX = 1; tick(); assertEquals(1, failed[0]); assertEquals(4, events.size());
    }

    @Test public void offThreadAccessIsRejectedAndClientLivingEventsAreIgnored() throws Exception {
        Player p = join(-1);
        AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        Thread other = new Thread(() -> {
            try { awareness.current(p.getUniqueID()); }
            catch (Throwable expected) { failure.set(expected); }
        });
        other.start(); other.join(); assertTrue(failure.get() instanceof IllegalStateException);
        Unit clientUnit = unit(world); world.isRemote = true;
        Thread client = new Thread(() -> hooks.onLivingUpdate(new LivingEvent.LivingUpdateEvent(clientUnit)));
        client.setUncaughtExceptionHandler((t, e) -> failure.set(e));
        failure.set(null); client.start(); client.join(); assertNull(failure.get()); world.isRemote = false;
    }

    @Test public void realRhunAndProtectedGapControlsThroughTracking() throws Exception {
        resolver.publish(KOMETileTestResources.real()); world.provider.dimensionId = KOMETileTestResources.dimension();
        Player p = player(world, UUID.randomUUID()); p.posX = 237248.5; p.posZ = Math.nextDown(87296D);
        hooks.onLogin(new PlayerEvent.PlayerLoggedInEvent(p)); tick();
        assertEquals("T401", observation(p).location.tileId);
        p.posZ = 87296D; tick(); assertEquals("T442", observation(p).location.tileId);
        p.posX = 34944.5; p.posZ = 640.5; tick(); assertEquals(IN_BOUNDS_GAP, observation(p).location.status);
        p.posX = Math.nextDown(189696D); p.posZ = -86016D; tick();
        assertEquals(IN_BOUNDS_GAP, observation(p).location.status); assertEquals(2291, observation(p).location.maskX);
        p.posX = 189696D; tick(); assertEquals("T001", observation(p).location.tileId);
    }

    @Test public void startupJoinWithoutLivingTickAndSubscriptionClosure() throws Exception {
        // A persisted hire may load before serverStarting and before it receives a living tick.
        Unit unit = unit(world); position(unit, -1);
        final int[] count = {0};
        KOMEServerTileAwareness.Subscription subscription = awareness.subscribe(event -> count[0]++);
        hooks.onJoin(new EntityJoinWorldEvent(unit, world));
        assertEquals(Availability.PENDING_SAMPLE, awareness.current(unit.getUniqueID()).availability);
        tick(); assertEquals("T001", observation(unit).location.tileId); assertEquals(1, count[0]);
        subscription.close(); subscription.close();
        unit.posX = 1; tick(); assertEquals("T002", observation(unit).location.tileId); assertEquals(1, count[0]);
    }

    @Test public void stopPublishesEmptyStateBeforeRemovalCallbacks() throws Exception {
        Player p = join(-1);
        awareness.subscribe(event -> {
            if (event.cause == Cause.SERVER_STOP) {
                assertEquals(0, awareness.trackedCount());
                assertEquals(Availability.SERVER_STOPPED, awareness.current(p.getUniqueID()).availability);
            }
        });
        awareness.stopSession();
    }


    @Test public void reentrantStopDeliversEveryRemovalBeforeClosingSubscribers() throws Exception {
        Player p = player(world, UUID.randomUUID()); position(p, -1);
        List<Type> seen = new ArrayList<Type>();
        awareness.subscribe(event -> { if (event.type == Type.INITIALIZED) awareness.stopSession(); });
        awareness.subscribe(event -> {
            seen.add(event.type);
            assertEquals(Availability.SERVER_STOPPED, awareness.current(p.getUniqueID()).availability);
        });
        hooks.onLogin(new PlayerEvent.PlayerLoggedInEvent(p)); tick();
        assertEquals(java.util.Arrays.asList(Type.INITIALIZED, Type.REMOVED), seen);
        assertEquals(0, awareness.trackedCount());
        awareness.startSession(); hooks.onLogin(new PlayerEvent.PlayerLoggedInEvent(p)); tick();
        assertEquals(2, seen.size()); // Previous session's listeners are gone.
    }

    @Test public void replacementCallbacksCannotResurrectTrackingAfterStop() throws Exception {
        Player old = join(-1);
        Player replacement = player(world, old.getUniqueID()); position(replacement, 1);
        awareness.subscribe(event -> {
            if (event.type == Type.REMOVED) awareness.stopSession();
        });
        hooks.onJoin(new EntityJoinWorldEvent(replacement, world));
        assertEquals(Availability.SERVER_STOPPED, awareness.current(old.getUniqueID()).availability);
        assertEquals(0, awareness.trackedCount());
    }

    @Test public void restartDuringRemovalNotificationIsRejectedWithoutLosingOtherListeners() throws Exception {
        Player p = join(-1); final int[] attempts = {0}, removals = {0};
        awareness.subscribe(event -> {
            if (event.type == Type.REMOVED) {
                attempts[0]++;
                try { awareness.startSession(); fail("Reentrant session restart must be rejected"); }
                catch (IllegalStateException expected) { }
            }
        });
        awareness.subscribe(event -> { if (event.type == Type.REMOVED) removals[0]++; });
        awareness.stopSession();
        assertEquals(1, attempts[0]); assertEquals(1, removals[0]);
        assertEquals(Availability.SERVER_STOPPED, awareness.current(p.getUniqueID()).availability);
        awareness.startSession(); assertEquals(0, awareness.trackedCount());
    }

    @Test public void subscriptionFromCallbackDoesNotReplayAlreadyQueuedEvents() throws Exception {
        Player a = player(world, UUID.randomUUID()), b = player(world, UUID.randomUUID());
        position(a, -1); position(b, -1);
        List<Transition> late = new ArrayList<Transition>();
        awareness.subscribe(event -> {
            if (event.entityId.equals(a.getUniqueID()) && event.type == Type.INITIALIZED)
                awareness.subscribe(late::add);
        });
        hooks.onLogin(new PlayerEvent.PlayerLoggedInEvent(a));
        hooks.onLogin(new PlayerEvent.PlayerLoggedInEvent(b)); tick();
        assertTrue(late.isEmpty());
        b.posX = 1; tick(); assertEquals(1, late.size()); assertEquals(Type.CHANGED, late.get(0).type);
    }

    @Test public void recursiveSamplingIsRejectedAndCannotAdvanceObservationClock() throws Exception {
        Player p = player(world, UUID.randomUUID()); position(p, -1);
        final int[] rejected = {0};
        awareness.subscribe(event -> {
            if (event.type != Type.INITIALIZED) return;
            try { tick(); fail("A callback cannot run another sample pass"); }
            catch (IllegalStateException expected) { rejected[0]++; }
        });
        hooks.onLogin(new PlayerEvent.PlayerLoggedInEvent(p)); tick();
        assertEquals(1, rejected[0]); assertEquals(1, awareness.currentTick());
    }

    @Test public void beforeEndQueryChecksCurrentFieldsWithoutClaimingFutureTickState() throws Exception {
        Player p = join(-1); long sampled = observation(p).observedTick;
        hooks.onServerTick(new TickEvent.ServerTickEvent(TickEvent.Phase.START));
        assertEquals(sampled, observation(p).observedTick); // Still true at query time.
        p.posX = 1;
        assertEquals(Availability.STALE_POSITION, awareness.current(p.getUniqueID()).availability);
        assertFalse(awareness.current(p.getUniqueID()).observation().isPresent());
        tick(); assertEquals("T002", observation(p).location.tileId);
        assertEquals(sampled + 1, observation(p).observedTick);
    }

    @Test public void consumerRequeriesHistoricalEventAfterEarlierListenerMovesEntity() throws Exception {
        Player a = player(world, UUID.randomUUID()), b = player(world, UUID.randomUUID());
        position(a, -1); position(b, -1);
        List<String> acceptedB = new ArrayList<String>();
        awareness.subscribe(event -> {
            if (event.entityId.equals(a.getUniqueID()) && event.type == Type.INITIALIZED) b.posX = 1;
        });
        awareness.subscribe(event -> {
            if (!event.entityId.equals(b.getUniqueID()) || !event.current().isPresent()) return;
            Current fresh = awareness.current(event.entityId);
            if (!fresh.observation().isPresent()) return;
            Observation now = fresh.observation().get(), historical = event.current().get();
            if (now.session != historical.session || now.incarnation != historical.incarnation
                    || now.observedTick != historical.observedTick) return;
            acceptedB.add(now.location.tileId);
        });
        hooks.onLogin(new PlayerEvent.PlayerLoggedInEvent(a));
        hooks.onLogin(new PlayerEvent.PlayerLoggedInEvent(b)); tick();
        assertTrue(acceptedB.isEmpty()); // The queued T001 event is historical, not a fresh arrival.
        tick(); assertEquals(Collections.singletonList("T002"), acceptedB);
    }

    @Test public void eligibilityLossIsUnavailableImmediatelyAndRehireInitializesNewObservation() throws Exception {
        Unit unit = unit(world); position(unit, -1);
        hooks.onJoin(new EntityJoinWorldEvent(unit, world)); tick();
        long original = observation(unit).incarnation;
        unit.hiredNPCInfo.isActive = false;
        assertEquals(Availability.NOT_TRACKED, awareness.current(unit.getUniqueID()).availability);
        hooks.onLivingUpdate(new LivingEvent.LivingUpdateEvent(unit));
        assertEquals(Cause.NO_LONGER_RELEVANT, events.get(events.size()-1).cause);
        unit.hiredNPCInfo.isActive = true;
        hooks.onLivingUpdate(new LivingEvent.LivingUpdateEvent(unit)); tick();
        assertTrue(observation(unit).incarnation > original);
        assertEquals(Type.INITIALIZED, events.get(events.size()-1).type);
    }

    @Test public void playersAndHiresShareZonesAndDeduplicateSameZoneMovement() throws Exception {
        resolver.publish(KOMETileExclusionsTest.load(KOMETileExclusionsTest.zones()));
        Player p = join(-1); Unit u = unit(world); position(u, -1);
        hooks.onJoin(new EntityJoinWorldEvent(u, world)); tick(); events.clear();
        for (Entity e : new Entity[] {p, u}) position(e, 0); tick();
        assertEquals(2, events.size());
        for (Entity e : new Entity[] {p, u}) {
            assertEquals(CLASSIFIED_EXCLUSION, observation(e).location.status);
            assertEquals("river-a", observation(e).location.exclusion().get().id);
            position(e, 127.999);
        }
        tick(); assertEquals(2, events.size());
        for (Entity e : new Entity[] {p, u}) position(e, 128); tick();
        assertEquals(4, events.size()); // Same zone type, different zone identity.
        for (Entity e : new Entity[] {p, u}) position(e, 256); tick();
        assertEquals(6, events.size()); assertEquals(IN_BOUNDS_GAP, observation(u).location.status);
        for (Entity e : new Entity[] {p, u}) position(e, -1); tick();
        assertEquals(8, events.size()); assertEquals("T001", observation(u).location.tileId);
    }

    @Test public void metadataReplacementAtUnchangedPositionPublishesSemanticChangeOnly() throws Exception {
        resolver.publish(KOMETileExclusionsTest.load(KOMETileExclusionsTest.zones()));
        Player p = join(1); events.clear();
        resolver.publish(KOMETileExclusionsTest.load(KOMETileExclusionsTest.zones())); tick(); assertTrue(events.isEmpty());
        resolver.publish(KOMETileExclusionsTest.load(KOMETileExclusionsTest.zones().replace("Fixture only, not approved geography", "Revised fixture reason")));
        assertEquals(Availability.STALE_GEOMETRY, awareness.current(p.getUniqueID()).availability);
        tick(); assertEquals(1, events.size()); assertEquals(Cause.GEOMETRY_CHANGED, events.get(0).cause);
        resolver.publish(KOMETileExclusionsTest.load("")); tick();
        assertEquals(2, events.size()); assertEquals(IN_BOUNDS_GAP, observation(p).location.status);
    }

    @Test public void boundedRepresentativeWorkload() throws Exception {
        // Inert physical entities and actual sampling code; no world AI/network/chunk-generation costs.
        resolver.publish(KOMETileTestResources.real()); world.provider.dimensionId = KOMETileTestResources.dimension();
        List<Entity> workload = new ArrayList<Entity>();
        for (int i = 0; i < 2000; i++) {
            Entity e = i < 100 ? player(world, UUID.randomUUID()) : unit(world);
            e.posX = 237248.5; e.posZ = 87295.5; workload.add(e);
            awareness.consider(e, Cause.FIRST_OBSERVATION);
        }
        for (int i = 0; i < 50; i++) tick();
        events.clear(); long initial = awareness.resolutionCount();
        long start = System.nanoTime();
        for (int i = 0; i < 200; i++) tick();
        long stationary = System.nanoTime() - start;
        assertEquals(initial, awareness.resolutionCount()); assertEquals(0, events.size());
        start = System.nanoTime();
        for (int i = 0; i < 200; i++) {
            for (Entity e : workload) e.posX += 0.03125;
            tick();
        }
        long moving = System.nanoTime() - start;
        assertEquals(initial + 400000, awareness.resolutionCount());
        assertEquals(2000, awareness.trackedCount());
        System.out.println("KOM60_WORKLOAD entities=2000 players=100 units=1900 ticksPerPhase=200 cadence=1"
            + " stationaryMs=" + stationary/1000000.0 + " movingMs=" + moving/1000000.0
            + " lookupsMoving=400000 eventsMoving=" + events.size()
            + " java=" + System.getProperty("java.version") + " os=" + System.getProperty("os.name"));
    }

    static TestWorld world(int dimension) throws Exception {
        TestWorld result = KOMEAccessFixture.allocate(TestWorld.class);
        Field provider = World.class.getDeclaredField("provider"); provider.setAccessible(true);
        provider.set(result, new WorldProviderSurface()); result.provider.dimensionId = dimension;
        result.entities = new HashMap<Integer, Entity>(); return result;
    }
    static Player player(TestWorld world, UUID id) throws Exception {
        Player p = KOMEAccessFixture.allocate(Player.class); p.alive = true; initialize(world, p, id); return p;
    }
    static Unit unit(TestWorld world) throws Exception {
        Unit unit = KOMEAccessFixture.allocate(Unit.class); unit.alive = true;
        initialize(world, unit, UUID.randomUUID()); unit.hiredNPCInfo = new LOTRHiredNPCInfo(unit);
        unit.hiredNPCInfo.isActive = true;
        Field owner = LOTRHiredNPCInfo.class.getDeclaredField("hiringPlayerUUID");
        owner.setAccessible(true); owner.set(unit.hiredNPCInfo, UUID.randomUUID()); return unit;
    }
    private static int nextEntityId;
    static void initialize(TestWorld world, Entity entity, UUID id) throws Exception {
        entity.worldObj = world; entity.dimension = world.provider.dimensionId;
        Field field = Entity.class.getDeclaredField("entityUniqueID"); field.setAccessible(true); field.set(entity, id);
        entity.setEntityId(++nextEntityId); entity.addedToChunk = true; world.entities.put(entity.getEntityId(), entity);
    }
    public static class Player extends EntityPlayerMP {
        boolean alive;
        private Player() { super(null, null, null, null); }
        @Override public boolean isEntityAlive() { return alive && !isDead; }
    }
    public static class Unit extends LOTREntityGondorSoldier {
        boolean alive;
        private Unit() { super(null); }
        @Override public boolean isEntityAlive() { return alive && !isDead; }
    }
    public static class TestWorld extends WorldServer {
        Map<Integer, Entity> entities;
        private TestWorld() { super(null, null, "", 0, null, null); }
        @Override public Entity getEntityByID(int id) { return entities.get(id); }
    }
}
