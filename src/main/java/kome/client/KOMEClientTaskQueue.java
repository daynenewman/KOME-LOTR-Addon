package kome.client;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.RejectedExecutionException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Owned and registered only by the client proxy; no worker thread is created. */
public final class KOMEClientTaskQueue {
    public static final int MAX_PENDING_TASKS = 128;
    private static final Logger LOGGER = LogManager.getLogger("KOMEClientTaskQueue");
    private final Object lock = new Object();
    private final Queue<Runnable> tasks = new ArrayDeque<Runnable>();
    private final Map<Object, LatestTask> latestTasks = new HashMap<Object, LatestTask>();
    private boolean connected;

    public void enqueue(Runnable task) {
        if (task == null) throw new IllegalArgumentException("Client task is required");
        synchronized (lock) {
            if (!connected) throw new RejectedExecutionException("KOME client is disconnected");
            if (tasks.size() >= MAX_PENDING_TASKS)
                throw new RejectedExecutionException("KOME client task queue is full; newest task rejected");
            tasks.add(task);
        }
    }

    /**
     * Keeps at most one pending task for a publication stream. A newer complete
     * authoritative value replaces the older value without changing its queue
     * position. Disconnected sessions deliberately ignore late network work.
     */
    public boolean enqueueLatest(Object key, Runnable task) {
        if (key == null) throw new IllegalArgumentException("Client task key is required");
        if (task == null) throw new IllegalArgumentException("Client task is required");
        synchronized (lock) {
            if (!connected) return false;
            LatestTask pending = latestTasks.get(key);
            if (pending != null) {
                pending.task = task;
                return true;
            }
            if (tasks.size() >= MAX_PENDING_TASKS)
                throw new RejectedExecutionException("KOME client task queue is full; newest task rejected");
            LatestTask latest = new LatestTask(key, task);
            latestTasks.put(key, latest);
            tasks.add(latest);
            return true;
        }
    }

    /** Discards the previous connection's work; reset itself runs on the client thread. */
    public void resetSession(boolean connected, Runnable reset) {
        if (reset == null) throw new IllegalArgumentException("Client reset is required");
        synchronized (lock) {
            this.connected = connected;
            tasks.clear();
            latestTasks.clear();
            tasks.add(reset);
        }
    }

    public int pendingTasks() {
        synchronized (lock) { return tasks.size(); }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) drain();
    }

    /** Snapshot bound: tasks enqueued by a running task wait for a later tick. */
    public int drain() {
        int remaining = pendingTasks();
        int executed = 0;
        while (remaining-- > 0) {
            Runnable task;
            synchronized (lock) { task = tasks.poll(); }
            if (task == null) break;
            try {
                task.run();
            } catch (RuntimeException failure) {
                LOGGER.error("KOME client publication task failed", failure);
            }
            executed++;
        }
        return executed;
    }

    private final class LatestTask implements Runnable {
        private final Object key;
        private Runnable task;

        private LatestTask(Object key, Runnable task) {
            this.key = key;
            this.task = task;
        }

        @Override
        public void run() {
            Runnable publication;
            synchronized (lock) {
                if (latestTasks.get(key) == this) latestTasks.remove(key);
                publication = task;
            }
            publication.run();
        }
    }
}
