package kome.common.network;

import java.util.IdentityHashMap;
import java.util.Map;

/** Server-thread-only timestamps. No projections, world references or permission decisions are cached. */
final class KOMEServerRecordCooldown {
    private final int capacity;
    private final Map<Object, Stamp> stamps = new IdentityHashMap<Object, Stamp>();
    KOMEServerRecordCooldown(int capacity) { this.capacity = capacity; }
    Result acquire(Object connection, long now, int cooldownMillis) {
        return acquire(connection, now, now, cooldownMillis);
    }
    Result acquire(Object connection, long received, long now, int cooldownMillis) {
        long cooldown = cooldownMillis * 1_000_000L;
        Stamp previous = stamps.get(connection);
        if (previous != null && received - previous.time < cooldown) {
            boolean notify = !previous.notified; previous.notified = true;
            return new Result(false, notify, Math.max(0L, (cooldown - (now - previous.time) + 999_999L) / 1_000_000L));
        }
        if (previous == null && stamps.size() >= capacity) {
            // Keep each live connection's stamp until logout/restart, including expired timestamps:
            // queued old duplicates must not become fresh when capacity pressure reclaims a stamp.
            return new Result(false, true, -1);
        }
        stamps.put(connection, new Stamp(now));
        return new Result(true, false, 0);
    }
    void forget(Object connection) { stamps.remove(connection); }
    void clear() { stamps.clear(); }
    int size() { return stamps.size(); }
    private static final class Stamp { final long time; boolean notified; Stamp(long time) { this.time = time; } }
    static final class Result {
        final boolean accepted, notify; final long retryMillis;
        Result(boolean accepted, boolean notify, long retryMillis) {
            this.accepted = accepted; this.notify = notify; this.retryMillis = retryMillis;
        }
    }
}
