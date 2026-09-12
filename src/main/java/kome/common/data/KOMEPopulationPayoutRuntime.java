package kome.common.data;

import java.time.Instant;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Server-session bridge for the deterministic payout processor. */
public final class KOMEPopulationPayoutRuntime {
    private final Set<KOMEWorldData> started = Collections.newSetFromMap(new IdentityHashMap<KOMEWorldData, Boolean>());
    private final FailureReporter failureReporter;
    private String lastFailure = "";
    private long lastFailureLogMillis;

    public KOMEPopulationPayoutRuntime() { this(new FailureReporter() { public void report(String message) { System.err.println("[KOME] population payout failed; boundary remains retryable: " + message); } }); }
    KOMEPopulationPayoutRuntime(FailureReporter reporter) { failureReporter = reporter == null ? new FailureReporter() { public void report(String message) { } } : reporter; }

    public KOMEPopulationPayoutProcessor.Result onStartup(KOMEWorldData data, Instant now) {
        if (data == null || now == null || !started.add(data)) return null;
        return report(KOMEPopulationPayoutProcessor.initializeOrProcessStartup(data, now), now);
    }

    public KOMEPopulationPayoutProcessor.Result onLiveCheck(KOMEWorldData data, Instant now) {
        if (data == null || now == null) return null;
        return report(KOMEPopulationPayoutProcessor.processLiveDueBoundaries(data, now), now);
    }

    public void resetSession() { started.clear(); lastFailure = ""; lastFailureLogMillis = 0L; }

    private KOMEPopulationPayoutProcessor.Result report(KOMEPopulationPayoutProcessor.Result result, Instant now) {
        if (result != null && !result.success) {
            String failure = result.message + "@" + result.factions.size();
            if (!failure.equals(lastFailure) || now.toEpochMilli() - lastFailureLogMillis >= 60000L) {
                try { failureReporter.report(result.message); } catch (RuntimeException ignored) { }
                lastFailure = failure; lastFailureLogMillis = now.toEpochMilli();
            }
        } else if (result != null && result.success) lastFailure = "";
        return result;
    }
    interface FailureReporter { void report(String message); }
}
