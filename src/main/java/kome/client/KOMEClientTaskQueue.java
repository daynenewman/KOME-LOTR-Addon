package kome.client;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.concurrent.RejectedExecutionException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Owned and registered only by the client proxy; no worker thread is created. */
public final class KOMEClientTaskQueue {
    public static final int MAX_PENDING_TASKS = 128;
    private static final Logger LOGGER = LogManager.getLogger("KOMEClientTaskQueue");
    private final Object lock = new Object();
    private final Queue<Runnable> tasks = new ArrayDeque<Runnable>();
    private boolean connected;
    private final BooleanSupplier onClientThread;
    // Only read/written by the draining client thread; off-thread enqueue never reads it.
    private boolean draining;

    public KOMEClientTaskQueue() { this(() -> false); }

    KOMEClientTaskQueue(BooleanSupplier onClientThread) {
        this.onClientThread = Objects.requireNonNull(onClientThread, "Client thread check");
    }

    public void enqueue(Runnable task) {
        if (task == null) throw new IllegalArgumentException("Client task is required");
        // Forge 1.7.10 often dispatches FML packets from Minecraft's network pump on the
        // client thread. Do not retain a whole conquest snapshot until the following tick.
        boolean immediate = onClientThread.getAsBoolean() && !draining;
        if (immediate) drain(); // Older queued/reset work must precede this publication.
        synchronized (lock) {
            if (!connected) throw new RejectedExecutionException("KOME client is disconnected");
            if (tasks.size() >= MAX_PENDING_TASKS)
                throw new RejectedExecutionException("KOME client task queue is full; newest task rejected");
            tasks.add(task);
        }
        if (immediate) drain();
    }

    /** Discards the previous connection's work; reset itself runs on the client thread. */
    public void resetSession(boolean connected, Runnable reset) {
        if (reset == null) throw new IllegalArgumentException("Client reset is required");
        synchronized (lock) {
            this.connected = connected;
            tasks.clear();
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

    /** Snapshot bound: tasks enqueued by a running task wait for a later drain. */
    public int drain() {
        if (draining) return 0;
        draining = true;
        try {
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
        } finally {
            draining = false;
        }
    }
}
