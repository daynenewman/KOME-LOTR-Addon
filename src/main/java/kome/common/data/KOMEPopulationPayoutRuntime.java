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

    public KOMEPopulationPayoutRuntime() { this(new FailureReporter() { public void report(String message) { System.err.println("[KOME] population transition rejected; uncommitted work remains retryable: " + message); } }); }
    KOMEPopulationPayoutRuntime(FailureReporter reporter) { failureReporter = reporter == null ? new FailureReporter() { public void report(String message) { } } : reporter; }

    public KOMEPopulationPayoutProcessor.Result onStartup(KOMEWorldData data, Instant now) {
        if (data == null || now == null || started.contains(data)) return null;
        KOMEPopulationDevelopmentService.Result development =
            KOMEPopulationDevelopmentService.initializeOrSkipStartup(data, now);
        if (!development.success) return developmentFailure(development, now);
        KOMEPopulationPayoutProcessor.Result result = report(KOMEPopulationPayoutProcessor.initializeOrProcessStartup(data, now), now);
        if (result.success) {
            // Population catch-up does not replenish missed movement days.
            kome.common.command.KOMECommandTroops.anchorMovementSchedule(data, now.toEpochMilli());
            started.add(data);
        }
        return result;
    }

    public KOMEPopulationPayoutProcessor.Result onLiveCheck(KOMEWorldData data, Instant now) {
        if (data == null || now == null) return null;
        if (!started.contains(data)) return onStartup(data, now);
        KOMEPopulationDevelopmentService.Result development =
            KOMEPopulationDevelopmentService.processLiveDueBoundaries(data, now);
        if (!development.success) return developmentFailure(development, now);
        return report(KOMEPopulationPayoutProcessor.processLiveDueBoundaries(data, now), now);
    }

    public void resetSession() { started.clear(); lastFailure = ""; lastFailureLogMillis = 0L; }
    public boolean hasStarted(KOMEWorldData data) { return started.contains(data); }

    private KOMEPopulationPayoutProcessor.Result developmentFailure(
            KOMEPopulationDevelopmentService.Result failure, Instant now) {
        KOMEPopulationPayoutProcessor.Result result =
            new KOMEPopulationPayoutProcessor.Result(
                java.util.Collections.<KOMEPopulationPayoutProcessor.FactionResult>emptyList(),
                0L, 0L, false, false, false,
                "Population development rejected: " + failure.message);
        return report(result, now);
    }

    private KOMEPopulationPayoutProcessor.Result report(KOMEPopulationPayoutProcessor.Result result, Instant now) {
        if (result != null && !result.success) {
            String failure = result.message + "@" + result.factions.size();
            if (!failure.equals(lastFailure) || now.toEpochMilli() - lastFailureLogMillis >= 60000L) {
                try { failureReporter.report(result.message + "; prior committed transitions retained=" + result.committedTransitions); } catch (RuntimeException ignored) { }
                lastFailure = failure; lastFailureLogMillis = now.toEpochMilli();
            }
        } else if (result != null && result.success) lastFailure = "";
        return result;
    }
    interface FailureReporter { void report(String message); }
}
