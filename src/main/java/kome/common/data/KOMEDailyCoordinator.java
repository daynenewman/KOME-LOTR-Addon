package kome.common.data;

import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import kome.common.config.KOMEConfigRegistry;
import net.minecraft.world.World;

/** Available daily orchestration. Missing gameplay authorities never become successful stage stubs. */
public final class KOMEDailyCoordinator {
    public interface Checkpoint { void save(KOMEWorldData data) throws Exception; }
    public static final class Outcome {
        public final boolean handled;
        public final KOMEPopulationPayoutProcessor.Result payout;
        Outcome(boolean handled, KOMEPopulationPayoutProcessor.Result payout) { this.handled = handled; this.payout = payout; }
    }
    private final Clock clock;
    private final Map<KOMEWorldData, Long> sessions = new IdentityHashMap<KOMEWorldData, Long>();
    private final Set<KOMEWorldData> needsCheckpoint = Collections.newSetFromMap(new IdentityHashMap<KOMEWorldData, Boolean>());
    public KOMEDailyCoordinator() { this(Clock.systemUTC()); }
    public KOMEDailyCoordinator(Clock clock) { if (clock == null) throw new IllegalArgumentException("Clock required"); this.clock = clock; }
    public void startSession(KOMEWorldData data, Instant now) { sessions.put(data, Long.valueOf(now.toEpochMilli())); }
    public void resetSession() { sessions.clear(); needsCheckpoint.clear(); }

    public Outcome process(KOMEWorldData data, World world, Instant now) {
        Checkpoint checkpoint;
        try { checkpoint = KOMEWorldCheckpoint.forWorld(data, world); }
        catch (Exception unavailable) {
            blocked(data, "PERSISTENCE_UNAVAILABLE: " + unavailable.getMessage(), now.toEpochMilli());
            return new Outcome(false, null);
        }
        return process(data, checkpoint, now);
    }
    public Outcome process(KOMEWorldData data, Checkpoint checkpoint) { return process(data, checkpoint, clock.instant()); }

    Outcome process(KOMEWorldData data, Checkpoint checkpoint, Instant now) {
        data.ensureWritable();
        KOMEDailyJournal j = data.dailyJournal;
        KOMEDailyBoundary schedule = KOMEDailyBoundary.from(KOMEConfigRegistry.dailyBatch());
        long millis = now.toEpochMilli(), latest = schedule.latestBoundaryAtOrBefore(now).toEpochMilli();
        KOMEPopulationPayoutProcessor.Result payout = null;
        try {
            // Effects already applied in memory must reach disk before any stage is executed again.
            if (needsCheckpoint.contains(data)) { checkpoint.save(data); needsCheckpoint.remove(data); }
            if (millis < j.lastObserved) {
                blocked(data, "CLOCK_REGRESSED: waiting for " + j.lastObserved, millis);
                flush(data, checkpoint); return new Outcome(false, null);
            }
            boolean newSession = sessions.containsKey(data);
            boolean scheduleChanged = !schedule.timezone().equals(j.timezone) || !schedule.localTime().equals(j.localTime);
            if (newSession || scheduleChanged || j.anchor < 0) {
                long sessionStart = newSession ? sessions.remove(data).longValue() : millis;
                long sessionBoundary = schedule.latestBoundaryAtOrBefore(Instant.ofEpochMilli(sessionStart)).toEpochMilli();
                // Startup population authority skips development and performs payout catch-up itself.
                // Resume only a batch whose population stages were already durably completed.
                boolean resume = !scheduleChanged && j.boundary == sessionBoundary && j.nextStage >= 4 && !j.summaryClaimed;
                if (!resume) {
                    if (j.boundary >= 0 && !j.summaryClaimed)
                        KOMEAuditService.record(data, millis, "DAILY", "INTERRUPTED", "system", String.valueOf(j.boundary),
                            "Restart/schedule change skipped remaining non-payout stages", "completedStages=" + j.nextStage);
                    j.boundary = -1; j.nextStage = 0; j.summaryClaimed = false; j.status = "IDLE"; j.reason = "";
                }
                j.anchor = Math.max(j.anchor, sessionBoundary);
                j.timezone = schedule.timezone(); j.localTime = schedule.localTime(); j.lastObserved = millis;
                flush(data, checkpoint);
            }
            String unavailable = unavailable(data, millis);
            if (!unavailable.isEmpty()) {
                if (j.boundary >= 0 && !j.summaryClaimed) {
                    KOMEAuditService.record(data, millis, "DAILY", "INTERRUPTED", "system", String.valueOf(j.boundary),
                        "Required authority became unavailable; legacy runtime retained", "completedStages=" + j.nextStage);
                    j.boundary = -1; j.nextStage = 0;
                }
                boolean changed = blocked(data, unavailable, millis);
                // Legacy runtime still owns the available gameplay work; never claim this boundary complete.
                if (latest > j.anchor) { j.anchor = latest; changed = true; }
                if (changed) flush(data, checkpoint);
                return new Outcome(false, null);
            }
            boolean partial = j.boundary >= 0 && !j.summaryClaimed;
            if (partial && j.boundary < latest) {
                KOMEAuditService.record(data, millis, "DAILY", "INTERRUPTED", "system", String.valueOf(j.boundary),
                    "Expired boundary skipped; no replay of remaining non-payout stages", "completedStages=" + j.nextStage);
                j.boundary = -1; j.nextStage = 0; j.anchor = latest; j.status = "IDLE"; j.reason = "";
                flush(data, checkpoint); return new Outcome(false, null);
            }
            if ("BLOCKED".equals(j.status)) {
                j.status = partial ? "RUNNING" : j.summaryClaimed ? "COMPLETE" : "IDLE"; j.reason = "";
                flush(data, checkpoint);
            }
            if (!partial && latest <= j.anchor) return new Outcome(false, null);
            if (!partial) {
                j.boundary = latest; j.anchor = latest; j.nextStage = 0; j.summaryClaimed = false;
                j.status = "RUNNING"; j.reason = ""; j.lastObserved = millis;
                flush(data, checkpoint);
            }
            while (j.nextStage < KOMEDailyJournal.Stage.values().length) {
                KOMEDailyJournal.Stage stage = KOMEDailyJournal.Stage.values()[j.nextStage];
                String detail;
                switch (stage) {
                    case DEVELOPMENT:
                        KOMEPopulationDevelopmentService.Result development = KOMEPopulationDevelopmentService.processLiveDueBoundaries(data, Instant.ofEpochMilli(j.boundary));
                        if (!development.success) throw new IllegalStateException("DEVELOPMENT: " + development.message);
                        detail = "Active boundary development before spendable payout; processed=" + development.processedBoundaries; break;
                    case PAYOUT:
                        payout = KOMEPopulationPayoutProcessor.processLiveDueBoundaries(data, Instant.ofEpochMilli(j.boundary));
                        if (!payout.success) throw new IllegalStateException("PAYOUT: " + payout.message);
                        detail = "Existing payout authority and persisted watermark"; break;
                    case SUMMARY:
                        detail = "Available daily batch complete; notification claim persisted before send";
                        j.lastComplete = j.boundary; j.summaryClaimed = true; j.status = "COMPLETE"; break;
                    default:
                        detail = "INAPPLICABLE: " + noWorkReason(stage); break;
                }
                j.nextStage++; j.lastObserved = millis; j.reason = "";
                KOMEAuditService.record(data, millis, "DAILY", stage.name(), "system", String.valueOf(j.boundary), detail, "stage=" + j.nextStage);
                flush(data, checkpoint);
                if (stage == KOMEDailyJournal.Stage.SUMMARY)
                    KOMENotificationService.dailySummary(Collections.singletonList("boundary=" + j.boundary + "; development then payout; other stages inapplicable"));
            }
            return new Outcome(true, payout);
        } catch (Exception failure) {
            // Keep the cursor/effects for a checkpoint retry; never rerun completed stages or send an uncommitted summary.
            blocked(data, "CHECKPOINT_OR_STAGE_FAILED at " + j.stage() + ": " + failure.getMessage(), millis);
            needsCheckpoint.add(data);
            return new Outcome(true, payout);
        }
    }

    static String unavailable(KOMEWorldData data, long now) {
        if (!data.armyMovements.isEmpty() || !data.armyCompanies.isEmpty())
            return "MOVEMENT_BLOCKED_KOM47: daily allowance/arrival completion contract unavailable; existing movement runtime retained";
        for (KOMEConflictRecord record : data.getConflictService().records().values()) if (record.isActive())
            return "CONFLICT_BLOCKED: active conflict requires its owning daily/referee authority; KOM-17 registry remains available";
        if (!data.hiredUnits.isEmpty()) return "STARVATION_BLOCKED_KOM24: unit eligibility/casualty authority unavailable";
        for (KOMEMusterRecord record : data.civilianMusters.values())
            if (record.getStatus() != KOMEMusterRecord.Status.ARRIVED && now >= record.dueAtMillis)
                return "EVENTS_BLOCKED_KOM78: due reserves require faction-force delivery or unresolved placement/season policy";
        return "";
    }
    private static String noWorkReason(KOMEDailyJournal.Stage stage) {
        switch (stage) {
            case MOVEMENT: return "no companies or movement orders";
            case CONFLICT: return "no arrivals or active conflicts";
            case STARVATION: return "no hired units or campaign detachments";
            case EVENTS: return "no due undelivered reserves; immediate events retain their existing handlers";
            default: throw new IllegalArgumentException("Stage requires authority");
        }
    }
    private void flush(KOMEWorldData data, Checkpoint checkpoint) throws Exception {
        data.markDirty(); needsCheckpoint.add(data); checkpoint.save(data); needsCheckpoint.remove(data);
    }
    private static boolean blocked(KOMEWorldData data, String reason, long now) {
        KOMEDailyJournal j = data.dailyJournal;
        String bounded = KOMEAdminDiagnostics.line(reason);
        if ("BLOCKED".equals(j.status) && bounded.equals(j.reason)) return false;
        j.status = "BLOCKED"; j.reason = bounded;
        KOMEAuditService.record(data, Math.max(0, now), "DAILY", "BLOCKED", "system", j.stage(), bounded, "boundary=" + j.boundary);
        data.markDirty(); return true;
    }
}
