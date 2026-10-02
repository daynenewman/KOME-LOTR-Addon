package kome.common.network;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Bounded admission, per-connection FIFO and round-robin service. Callbacks never hold the lock. */
final class KOMEServerTaskQueue {
    enum Admission { ACCEPTED, FULL, CLOSED }
    private final int capacity, perConnection, tickLimit;
    private final long tickBudgetNanos;
    private final Map<Object, Lane> lanes = new IdentityHashMap<Object, Lane>();
    private final ArrayDeque<Lane> ready = new ArrayDeque<Lane>();
    private int pending;
    private long sequence, generation;
    private boolean open;

    KOMEServerTaskQueue(int capacity, int perConnection, int tickLimit, long tickBudgetNanos) {
        if (capacity < 1 || perConnection < 1 || tickLimit < 1 || tickBudgetNanos < 1)
            throw new IllegalArgumentException("Positive queue limits required");
        this.capacity = capacity; this.perConnection = perConnection;
        this.tickLimit = tickLimit; this.tickBudgetNanos = tickBudgetNanos;
    }
    synchronized long open() { reset(); open = true; return generation; }
    synchronized void close() { reset(); open = false; }
    private void reset() { lanes.clear(); ready.clear(); pending = 0; generation++; }
    synchronized int pending() { return pending; }
    synchronized Admission offer(long session, Object connection, Runnable task, Runnable busyNotice) {
        if (connection == null || task == null) throw new IllegalArgumentException("Connection and task required");
        if (!open || generation != session) return Admission.CLOSED;
        Lane lane = lanes.get(connection);
        if (pending >= capacity || (lane != null && lane.tasks.size() >= perConnection)) {
            // Only existing bounded lanes retain a notification; a full queue never allocates another lane.
            if (lane != null) lane.busy = true;
            return Admission.FULL;
        }
        if (lane == null) { lane = new Lane(connection); lanes.put(connection, lane); ready.addLast(lane); }
        lane.tasks.addLast(new Entry(++sequence, task, busyNotice)); pending++;
        return Admission.ACCEPTED;
    }
    synchronized void forget(Object connection) {
        Lane lane = lanes.remove(connection);
        if (lane != null) { ready.remove(lane); pending -= lane.tasks.size(); }
    }
    private synchronized Entry poll(long session, long watermark) {
        if (!open || generation != session) return null;
        int remainingLanes = ready.size();
        while (remainingLanes-- > 0) {
            Lane lane = ready.removeFirst();
            if (lane.tasks.peekFirst().sequence > watermark) { ready.addLast(lane); continue; }
            Entry entry = lane.tasks.removeFirst(); pending--;
            entry.busy = lane.busy; lane.busy = false;
            if (lane.tasks.isEmpty()) lanes.remove(lane.connection); else ready.addLast(lane);
            return entry;
        }
        return null;
    }
    int drain(Consumer<RuntimeException> failures, LongSupplier clock) {
        final long session, watermark;
        synchronized (this) { session = generation; watermark = sequence; }
        long start = clock.getAsLong(); int attempted = 0;
        // At least one attempt makes progress; an indivisible delegate can exceed the soft time budget.
        while (attempted < tickLimit && (attempted == 0 || clock.getAsLong() - start < tickBudgetNanos)) {
            Entry entry = poll(session, watermark);
            if (entry == null) break;
            attempted++;
            try { entry.task.run(); } catch (RuntimeException error) { failures.accept(error); }
            if (entry.busy && entry.busyNotice != null) {
                try { entry.busyNotice.run(); } catch (RuntimeException error) { failures.accept(error); }
            }
        }
        return attempted;
    }
    private static final class Lane {
        final Object connection; final ArrayDeque<Entry> tasks = new ArrayDeque<Entry>(); boolean busy;
        Lane(Object connection) { this.connection = connection; }
    }
    private static final class Entry {
        final long sequence; final Runnable task, busyNotice; boolean busy;
        Entry(long sequence, Runnable task, Runnable busyNotice) {
            this.sequence = sequence; this.task = task; this.busyNotice = busyNotice;
        }
    }
}
