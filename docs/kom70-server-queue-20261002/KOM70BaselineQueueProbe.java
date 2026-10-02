package kome.common.network;

import java.util.concurrent.atomic.AtomicInteger;

/** Disposable measurement of the reviewed pre-KOM-70 implementation; no world/player access. */
public final class KOM70BaselineQueueProbe {
    public static void main(String[] args) {
        int[] loads = {100, 1024, 10000, 100000};
        System.out.println("source_head=170d887; baseline only; inert counter callbacks; no gameplay/TPS claim");
        for (int load : loads) {
            KOMEPacketHandler.clearPendingServerTasks();
            AtomicInteger calls = new AtomicInteger();
            Runnable callback = calls::incrementAndGet;
            long begin = System.nanoTime();
            for (int n = 0; n < load; n++) KOMEPacketHandler.enqueueServerTask(callback);
            long admissionNanos = System.nanoTime() - begin;
            int pendingBefore = KOMEPacketHandler.pendingServerTaskCount();
            begin = System.nanoTime();
            int drained = KOMEPacketHandler.runPendingServerTasks();
            long drainNanos = System.nanoTime() - begin;
            if (drained != load || calls.get() != load || KOMEPacketHandler.pendingServerTaskCount() != 0)
                throw new AssertionError("Baseline measurement did not execute exactly once");
            System.out.printf(java.util.Locale.ROOT,
                "offered=%d pending_before=%d one_tick_attempts=%d callbacks=%d admission_ms=%.3f drain_ms=%.3f%n",
                load, pendingBefore, drained, calls.get(), admissionNanos / 1e6, drainNanos / 1e6);
        }
        KOMEPacketHandler.clearPendingServerTasks();
    }
}
