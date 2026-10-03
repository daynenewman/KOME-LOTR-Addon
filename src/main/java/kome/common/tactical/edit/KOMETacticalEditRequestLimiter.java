package kome.common.tactical.edit;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/** UUID token buckets following SiegeRequestLimiter's pattern, with independent tactical budgets and bounded keys. */
public final class KOMETacticalEditRequestLimiter {
    private static final int MAX_PLAYERS = 128;
    private static final long IDLE_NANOS = 5L * 60L * 1000000000L;
    private enum Kind {
        SESSION(4D, 8), DRAFT(4D, 8), PREFLIGHT(2D, 4), SAVE(0.5D, 2);
        final double rate; final int burst;
        Kind(double rate, int burst) { this.rate = rate; this.burst = burst; }
    }
    private final Map<UUID, PlayerBuckets> players = new HashMap<UUID, PlayerBuckets>();
    private final LongSupplier clock;
    public KOMETacticalEditRequestLimiter() { this(System::nanoTime); }
    KOMETacticalEditRequestLimiter(LongSupplier clock) { this.clock = clock; }
    public synchronized boolean tryAcquire(UUID playerId, KOMETacticalEditRequest.Action action) {
        if (playerId == null || action == null) return false;
        long now = clock.getAsLong();
        Iterator<PlayerBuckets> iterator = players.values().iterator();
        while (iterator.hasNext()) if (now - iterator.next().lastSeen >= IDLE_NANOS) iterator.remove();
        PlayerBuckets buckets = players.get(playerId);
        if (buckets == null) {
            if (players.size() >= MAX_PLAYERS) return false;
            buckets = new PlayerBuckets(); players.put(playerId, buckets);
        }
        buckets.lastSeen = now;
        Kind kind = action == KOMETacticalEditRequest.Action.UPDATE ? Kind.DRAFT
            : action == KOMETacticalEditRequest.Action.PREFLIGHT ? Kind.PREFLIGHT
            : action == KOMETacticalEditRequest.Action.SAVE ? Kind.SAVE : Kind.SESSION;
        Bucket bucket = buckets.buckets.get(kind);
        if (bucket == null) { bucket = new Bucket(kind.burst, now); buckets.buckets.put(kind, bucket); }
        long elapsed = now - bucket.updated;
        if (elapsed > 0) { bucket.tokens = Math.min(kind.burst, bucket.tokens + elapsed * kind.rate / 1000000000D); bucket.updated = now; }
        if (bucket.tokens < 1D) return false;
        bucket.tokens -= 1D; return true;
    }
    public synchronized void clearPlayer(UUID id) { players.remove(id); }
    public synchronized void clear() { players.clear(); }
    synchronized int trackedPlayers() { return players.size(); }
    private static final class PlayerBuckets { long lastSeen; final Map<Kind, Bucket> buckets = new EnumMap<Kind, Bucket>(Kind.class); }
    private static final class Bucket { double tokens; long updated; Bucket(int burst, long now) { tokens = burst; updated = now; } }
}
