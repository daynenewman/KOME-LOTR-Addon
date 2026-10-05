package kome.common.tactical.edit;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Bounded cross-thread intake; contains intent and weak player handles, never World tasks. */
public final class KOMETacticalEditRequestQueue<T> {
    public static final int MAX_PENDING = 64, MAX_PER_PLAYER = 8, MAX_PER_TICK = 8;
    private final Deque<Entry<T>> pending = new ArrayDeque<Entry<T>>();
    private final Map<UUID, Integer> counts = new HashMap<UUID, Integer>();
    public synchronized boolean offer(UUID playerId, T handle, KOMETacticalEditRequest request) {
        if (playerId == null || handle == null || request == null || pending.size() >= MAX_PENDING
                || counts.getOrDefault(playerId, 0) >= MAX_PER_PLAYER) return false;
        pending.addLast(new Entry<T>(playerId, handle, request));
        counts.put(playerId, counts.getOrDefault(playerId, 0) + 1); return true;
    }
    public synchronized Entry<T> poll() {
        Entry<T> entry = pending.pollFirst();
        if (entry != null) {
            int count = counts.get(entry.playerId) - 1;
            if (count == 0) counts.remove(entry.playerId); else counts.put(entry.playerId, count);
        }
        return entry;
    }
    public synchronized void clearPlayer(UUID playerId) {
        Iterator<Entry<T>> iterator = pending.iterator();
        while (iterator.hasNext()) if (iterator.next().playerId.equals(playerId)) iterator.remove();
        counts.remove(playerId);
    }
    public synchronized void clear() { pending.clear(); counts.clear(); }
    public synchronized int size() { return pending.size(); }
    public static final class Entry<T> {
        private final UUID playerId;
        private final T handle;
        private final KOMETacticalEditRequest request;
        private Entry(UUID playerId, T handle, KOMETacticalEditRequest request) { this.playerId = playerId; this.handle = handle; this.request = request; }
        public UUID getPlayerId() { return playerId; }
        public T getHandle() { return handle; }
        public KOMETacticalEditRequest getRequest() { return request; }
    }
}
