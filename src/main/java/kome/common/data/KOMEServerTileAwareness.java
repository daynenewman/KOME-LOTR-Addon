package kome.common.data;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import kome.common.KOMEReflection;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.entity.npc.LOTRHiredNPCInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * Transient physical observations, not strategic company state. All access while running is
 * server-thread-only. See docs/KOME_SERVER_TILE_AWARENESS.md for freshness and lifecycle.
 */
public final class KOMEServerTileAwareness {
    public static final KOMEServerTileAwareness INSTANCE =
        new KOMEServerTileAwareness(KOMETileWorldResolver.INSTANCE);

    public enum Kind { PLAYER, HIRED_UNIT }
    public enum Availability {
        AVAILABLE, PENDING_SAMPLE, NOT_TRACKED, SERVER_STOPPED, STALE_POSITION, STALE_GEOMETRY
    }
    public enum Type { INITIALIZED, CHANGED, RESOLUTION_FAILED, REMOVED }
    public enum Cause {
        FIRST_OBSERVATION, LOGIN, RESPAWN, REPLACED, POSITION_CHANGED, DIMENSION_CHANGED,
        GEOMETRY_CHANGED, DISCONNECTED, DIED, NO_LONGER_RELEVANT, ENTITY_UNLOADED,
        CHUNK_UNLOADED, WORLD_UNLOADED, SERVER_STOP
    }

    public static final class Observation {
        public final UUID entityId;
        public final Kind kind;
        public final long session, incarnation, observedTick;
        public final double x, z;
        public final KOMETileResolution location;

        private Observation(Entry entry, long session) {
            entityId = entry.id; kind = entry.kind; incarnation = entry.incarnation;
            this.session = session; observedTick = entry.sampledTick;
            x = entry.x; z = entry.z; location = entry.location;
        }
    }

    public static final class Current {
        public final Availability availability;
        private final Observation observation;
        private Current(Availability availability, Observation observation) {
            this.availability = availability; this.observation = observation;
        }
        /** Present only for a live observation matching the current position and resolver view. */
        public Optional<Observation> observation() { return Optional.ofNullable(observation); }
    }

    public static final class Transition {
        public final UUID entityId;
        public final Kind kind;
        public final Type type;
        public final Cause cause;
        public final long session, tick;
        private final Observation before, after;
        private final long sequence;
        private Transition(Type type, Cause cause, Observation before, Observation after, long tick, long sequence) {
            Observation identity = after == null ? before : after;
            entityId = identity.entityId; kind = identity.kind; session = identity.session;
            this.type = type; this.cause = cause; this.before = before; this.after = after; this.tick = tick; this.sequence = sequence;
        }
        public Optional<Observation> previous() { return Optional.ofNullable(before); }
        public Optional<Observation> current() { return Optional.ofNullable(after); }
    }

    public interface Listener { void onTransition(Transition transition); }

    /** Runs inside the existing END sampling pass, before publishing transitions. */
    public interface BoundaryGuard {
        boolean onSample(Entity entity, KOMETileResolution physical, KOMETileWorldResolver.ReadView view, long tick);
        void removed(Entity entity);
        void reset();
    }

    public final class Subscription implements AutoCloseable {
        private final Listener listener;
        private boolean active = true;
        private final long firstSequence;
        private Subscription(Listener listener) {
            this.listener = listener; firstSequence = sequence + 1L;
        }
        @Override public void close() {
            checkThread();
            active = false;
            listeners.remove(this);
        }
    }

    private static final class Entry {
        final Entity entity;
        final UUID id;
        final Kind kind;
        final long incarnation;
        final Cause initialCause;
        World sampledWorld;
        long sampledTick;
        double x, z;
        KOMETileResolution location;
        KOMETileWorldResolver.ReadView view;
        Entry(Entity entity, UUID id, Kind kind, long incarnation, Cause initialCause) {
            this.entity = entity; this.id = id; this.kind = kind;
            this.incarnation = incarnation; this.initialCause = initialCause;
        }
    }

    private final KOMETileWorldResolver resolver;
    private final Map<UUID, Entry> entries = new LinkedHashMap<UUID, Entry>();
    private final List<Subscription> listeners = new ArrayList<Subscription>();
    private final ArrayDeque<Transition> notifications = new ArrayDeque<Transition>();
    private Thread serverThread;
    private boolean running, notifying;
    private long session, incarnation, tick, lookups, sequence;
    private BoundaryGuard boundaryGuard;

    KOMEServerTileAwareness(KOMETileWorldResolver resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    public void setBoundaryGuard(BoundaryGuard guard) {
        if (running) throw new IllegalStateException("Configure boundary guard before session starts");
        boundaryGuard = guard;
    }

    /** Called in FML serverAboutToStart, before worlds load; discovers startup entities without scanning. */
    public void startSession() {
        if (notifying) throw new IllegalStateException("Cannot restart tile awareness during notification");
        if (running) throw new IllegalStateException("Tile awareness session already running");
        serverThread = Thread.currentThread();
        if (boundaryGuard != null) boundaryGuard.reset();
        entries.clear(); notifications.clear(); listeners.clear();
        session++; tick = 0L; incarnation = 0L; lookups = 0L; sequence = 0L; running = true;
    }

    /** Removes all observations before notification; no world, entity, or persisted-record writes. */
    public void stopSession() {
        if (!running) return;
        checkThread();
        for (Entry entry : entries.values()) enqueueRemoval(entry, Cause.SERVER_STOP);
        entries.clear();
        if (boundaryGuard != null) boundaryGuard.reset();
        running = false;
        flush();
        // A callback may stop the session. Its queued removals still belong to these subscribers.
        if (!notifying) clearListeners();
    }

    private void clearListeners() {
        for (Subscription subscription : listeners) subscription.active = false;
        listeners.clear();
    }

    public Subscription subscribe(Listener listener) {
        checkThread();
        if (!running) throw new IllegalStateException("Subscribe after serverStarting, once per server session");
        Subscription subscription = new Subscription(Objects.requireNonNull(listener, "listener"));
        listeners.add(subscription);
        return subscription;
    }

    public Current current(UUID entityId) {
        checkThread();
        if (!running) return new Current(Availability.SERVER_STOPPED, null);
        Entry entry = entries.get(entityId);
        if (entry == null || removalCause(entry) != null) return new Current(Availability.NOT_TRACKED, null);
        if (entry.location == null) return new Current(Availability.PENDING_SAMPLE, null);
        if (entry.view != resolver.readView()) return new Current(Availability.STALE_GEOMETRY, null);
        if (!samePosition(entry)) return new Current(Availability.STALE_POSITION, null);
        return new Current(Availability.AVAILABLE, new Observation(entry, session));
    }

    public long currentTick() { checkThread(); return tick; }
    public int trackedCount() { checkThread(); return entries.size(); }
    /** Session counter for diagnostics and bounded workload measurements. */
    public long resolutionCount() { checkThread(); return lookups; }

    private void checkThread() {
        if (serverThread != null && Thread.currentThread() != serverThread)
            throw new IllegalStateException("Tile awareness requires the server thread");
    }

    // Forge/FML integration only. No public registration API for fictional or unloaded positions.
    void consider(Entity entity, Cause cause) {
        if (entity == null || !(entity.worldObj instanceof WorldServer) || entity.worldObj.isRemote) return;
        if (!running) return;
        checkThread();
        Kind kind = kind(entity);
        if (kind == null || !((EntityLivingBase) entity).isEntityAlive()) {
            remove(entity, entity.isDead ? Cause.DIED : Cause.NO_LONGER_RELEVANT);
            return;
        }
        UUID id = KOMEReflection.getEntityUUID(entity);
        if (id == null) return;
        Entry existing = entries.get(id);
        if (existing != null && existing.entity == entity) return;
        if (existing != null) {
            entries.remove(id);
            enqueueRemoval(existing, cause == Cause.RESPAWN ? cause : Cause.REPLACED);
        }
        // Complete replacement before callbacks can stop/unload/reenter the service.
        entries.put(id, new Entry(entity, id, kind, ++incarnation, cause));
        flush();
    }

    void respawn(Entity entity) {
        if (!running) return;
        checkThread();
        if (entity == null) return;
        Entry previous = entries.remove(KOMEReflection.getEntityUUID(entity));
        if (previous != null) enqueueRemoval(previous, Cause.RESPAWN);
        consider(entity, Cause.RESPAWN);
        flush();
    }

    void remove(Entity entity, Cause cause) {
        if (!running || entity == null) return;
        checkThread();
        Entry entry = entries.get(KOMEReflection.getEntityUUID(entity));
        // A late unload/logout for the previous incarnation must not remove the replacement.
        if (entry != null && entry.entity == entity) {
            entries.remove(entry.id);
            enqueueRemoval(entry, cause); flush();
        }
    }

    void unloadWorld(World world) {
        if (!running) return;
        checkThread();
        Iterator<Entry> iterator = entries.values().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            if (entry.entity.worldObj == world) {
                iterator.remove(); enqueueRemoval(entry, Cause.WORLD_UNLOADED);
            }
        }
        flush();
    }

    /** Once at server END, after movement/teleports and world entity updates. O(loaded relevant entities). */
    void sampleTick() {
        if (!running) return;
        checkThread();
        if (notifying) throw new IllegalStateException("Cannot sample tile awareness during notification");
        tick++;
        KOMETileWorldResolver.ReadView view = resolver.readView();
        Iterator<Entry> iterator = entries.values().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            Cause removal = removalCause(entry);
            if (removal != null) { iterator.remove(); enqueueRemoval(entry, removal); continue; }
            if (entry.location != null && entry.view == view && samePosition(entry)) {
                entry.sampledTick = tick; // No result/string/observation allocation on unchanged samples.
            } else {
                sampleEntry(entry, view);
            }
            // O(1) record eligibility lookup; full confinement work is explicit CAMPAIGN only.
            // No second world/entity scan and no route-status exemption (including WAITING_NEXT_STEP).
            if (boundaryGuard != null && entry.kind == Kind.HIRED_UNIT
                    && boundaryGuard.onSample(entry.entity, entry.location, entry.view, tick)) {
                sampleEntry(entry, resolver.readView()); // Publish fresh corrected evidence this same tick.
            }
        }
        // Publish the entire sampling pass before any consumer runs; callbacks may query other entities.
        flush();
    }

    private void sampleEntry(Entry entry, KOMETileWorldResolver.ReadView view) {
        KOMETileResolution location = view.resolveWorldPosition(
            entry.entity.worldObj.provider.dimensionId, entry.entity.posX, entry.entity.posZ);
        lookups++;
        boolean changed = entry.location == null || !sameLocation(entry.location, location);
        Observation before = changed && entry.location != null ? new Observation(entry, session) : null;
        Cause cause = entry.location == null ? entry.initialCause
            : entry.location.dimension != location.dimension ? Cause.DIMENSION_CHANGED
            : entry.view != view ? Cause.GEOMETRY_CHANGED : Cause.POSITION_CHANGED;
        entry.x = entry.entity.posX; entry.z = entry.entity.posZ; entry.sampledTick = tick;
        entry.sampledWorld = entry.entity.worldObj; entry.view = view; entry.location = location;
        if (changed) {
            Type type = isFailure(location) ? Type.RESOLUTION_FAILED
                : before == null ? Type.INITIALIZED : Type.CHANGED;
            notifications.add(new Transition(type, cause, before, new Observation(entry, session), tick, ++sequence));
        }
    }

    private static boolean sameLocation(KOMETileResolution a, KOMETileResolution b) {
        return a.status == b.status && a.dimension == b.dimension && a.tileId.equals(b.tileId)
            && a.exclusion().equals(b.exclusion());
    }

    private static boolean isFailure(KOMETileResolution result) {
        return result.status == KOMETileResolution.Status.INVALID_SNAPSHOT
            || result.status == KOMETileResolution.Status.INVALID_COORDINATE;
    }

    private static boolean samePosition(Entry entry) {
        return entry.sampledWorld == entry.entity.worldObj
            && entry.location.dimension == entry.entity.worldObj.provider.dimensionId
            && Double.doubleToLongBits(entry.x) == Double.doubleToLongBits(entry.entity.posX)
            && Double.doubleToLongBits(entry.z) == Double.doubleToLongBits(entry.entity.posZ);
    }

    private static Kind kind(Entity entity) {
        if (entity instanceof EntityPlayerMP) return Kind.PLAYER;
        if (!(entity instanceof LOTREntityNPC)) return null;
        LOTRHiredNPCInfo info = ((LOTREntityNPC) entity).hiredNPCInfo;
        return info != null && info.isActive && info.getHiringPlayerUUID() != null
            && (info.getTask() == LOTRHiredNPCInfo.Task.WARRIOR || info.getTask() == LOTRHiredNPCInfo.Task.FARMER)
            ? Kind.HIRED_UNIT : null;
    }

    private static Cause removalCause(Entry entry) {
        Entity entity = entry.entity;
        if (entity.isDead || !((EntityLivingBase) entity).isEntityAlive()) return Cause.DIED;
        World world = entity.worldObj;
        if (!(world instanceof WorldServer) || world.isRemote || world.provider == null) return Cause.WORLD_UNLOADED;
        // WorldServer's entityIdMap lookup is O(1), never a World.loadedEntityList scan or a chunk load.
        if (world.getEntityByID(entity.getEntityId()) != entity) return Cause.ENTITY_UNLOADED;
        return kind(entity) == entry.kind ? null : Cause.NO_LONGER_RELEVANT;
    }

    private void enqueueRemoval(Entry entry, Cause cause) {
        if (boundaryGuard != null) boundaryGuard.removed(entry.entity);
        if (entry.location != null)
            notifications.add(new Transition(Type.REMOVED, cause, new Observation(entry, session), null, tick, ++sequence));
    }

    private void flush() {
        if (notifying || notifications.isEmpty()) return;
        notifying = true;
        try {
            while (!notifications.isEmpty()) {
                Transition event = notifications.removeFirst();
                for (Subscription subscription : new ArrayList<Subscription>(listeners)) {
                    if (!subscription.active || event.sequence < subscription.firstSequence) continue;
                    try { subscription.listener.onTransition(event); }
                    catch (RuntimeException failure) {
                        // A broken optional consumer cannot stop observation of every other entity.
                        subscription.close();
                        System.err.println("[KOME] Tile awareness listener disabled after failure: " + failure);
                    }
                }
            }
        } finally {
            notifying = false;
            if (!running) clearListeners();
        }
    }
}
