package kome.common.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import kome.common.config.KOMEConfigRegistry;

import static kome.common.data.KOMEConflictContracts.*;
import static kome.common.data.KOMEConflictRecord.*;

/**
 * Server-authoritative ordinary-combat episode clock. ConflictRecord remains the only durable
 * episode/timer authority; this service retains only the current server session's uncheckpointed
 * active time so wall-clock downtime can never advance a response window.
 */
public final class KOMEOrdinaryBattleLifecycleService {
    public static final long CHECKPOINT_CADENCE_MILLIS = 5_000L;
    public static final KOMEOrdinaryBattleLifecycleService INSTANCE =
        new KOMEOrdinaryBattleLifecycleService(new EvaluationSource() {
            public KOMECombatStateEvaluator.Result evaluate(KOMEWorldData data,
                    KOMEConflictRecord conflict) {
                return new KOMECombatStateEvaluator().evaluate(data, conflict);
            }
        }, new FortificationSource() {
            public KOMEFortificationContextResolver.Decision evaluate(KOMEWorldData data,
                    String tileId) {
                return KOMEFortificationContextResolver.evaluate(data, tileId);
            }
        }, new DurationSource() {
            public KOMEOrdinaryBattlePlanner.DurationPlan select(KOMEWorldData data,
                    KOMEConflictRecord conflict) {
                return KOMEOrdinaryBattlePlanner.responseDuration(
                    data.getConquestTileIfPresent(conflict.getTileId()),
                    KOMEConfigRegistry.requireReadySnapshot().getBattle());
            }
        }, new NotificationSink() {
            public void episodeStarted(KOMEConflictRecord record, long durationMillis) {
                long minutes = durationMillis / 60_000L;
                KOMENotificationService.timeSensitive("Battle " + record.getConflictId()
                    + " on " + record.getTileId() + " began combat episode "
                    + record.getCombatEpisode().episodeId + ". Response window: "
                    + minutes + " minute" + (minutes == 1L ? "" : "s") + ".");
            }
        }, BeforePublication.NONE);

    public enum Code {
        NO_CONFLICT,
        NOT_ORDINARY,
        DEFERRED_FORTIFIED,
        DEFERRED_UNKNOWN_AUTHORITY,
        DEFERRED_INVALID_DURATION,
        NO_CHANGE,
        EPISODE_STARTED,
        TIMER_CHECKPOINTED,
        TIMER_COMPLETED_HOSTILITY_CONTINUES,
        EPISODE_ENDED_RESOLUTION_ELIGIBLE,
        STALE_RETRY,
        PUBLICATION_REJECTED
    }

    public static final class Result {
        public final Code code;
        public final String tileId;
        public final String conflictId;
        public final String episodeId;
        public final long elapsedMillis;
        public final long durationMillis;
        public final boolean responseComplete;
        public final String reason;

        private Result(Code code, KOMEConflictRecord record, String episodeId,
                long elapsed, long duration, boolean complete, String reason) {
            this.code = code;
            tileId = record == null ? "" : record.getTileId();
            conflictId = record == null ? "" : record.getConflictId();
            this.episodeId = episodeId == null ? "" : episodeId;
            elapsedMillis = elapsed;
            durationMillis = duration;
            responseComplete = complete;
            this.reason = reason == null ? "" : reason;
        }
    }

    interface EvaluationSource {
        KOMECombatStateEvaluator.Result evaluate(KOMEWorldData data,
            KOMEConflictRecord conflict);
    }

    interface FortificationSource {
        KOMEFortificationContextResolver.Decision evaluate(KOMEWorldData data,
            String tileId);
    }

    interface DurationSource {
        KOMEOrdinaryBattlePlanner.DurationPlan select(KOMEWorldData data,
            KOMEConflictRecord conflict);
    }

    interface NotificationSink {
        void episodeStarted(KOMEConflictRecord record, long durationMillis);
    }

    interface BeforePublication {
        BeforePublication NONE = new BeforePublication() {
            public void run(KOMEWorldData data, KOMEConflictRecord evaluated) { }
        };
        void run(KOMEWorldData data, KOMEConflictRecord evaluated);
    }

    private static final class ActiveClock {
        final String conflictId;
        final String episodeId;
        long lastObservedMillis;
        long pendingActiveMillis;

        ActiveClock(String conflictId, String episodeId, long now) {
            this.conflictId = conflictId;
            this.episodeId = episodeId;
            lastObservedMillis = now;
        }

        boolean matches(KOMEConflictRecord record) {
            return conflictId.equals(record.getConflictId())
                && episodeId.equals(record.getCombatEpisode().episodeId);
        }
    }

    private final EvaluationSource evaluationSource;
    private final FortificationSource fortificationSource;
    private final DurationSource durationSource;
    private final NotificationSink notificationSink;
    private final BeforePublication beforePublication;
    private final Map<KOMEWorldData, Map<String, ActiveClock>> clocks =
        new WeakHashMap<KOMEWorldData, Map<String, ActiveClock>>();

    KOMEOrdinaryBattleLifecycleService(EvaluationSource evaluation,
            FortificationSource fortification, DurationSource duration,
            NotificationSink notifications, BeforePublication beforePublication) {
        if (evaluation == null || fortification == null || duration == null
                || notifications == null || beforePublication == null)
            throw new IllegalArgumentException("Ordinary battle lifecycle dependencies are required.");
        evaluationSource = evaluation;
        fortificationSource = fortification;
        durationSource = duration;
        notificationSink = notifications;
        this.beforePublication = beforePublication;
    }

    /** Deterministic tile order keeps publication and diagnostics stable. */
    public List<Result> processAll(KOMEWorldData data, long nowMillis) {
        if (data == null) throw new IllegalArgumentException("World data is required.");
        List<String> tiles = new ArrayList<String>(data.getConflictService().records().keySet());
        Collections.sort(tiles);
        List<Result> results = new ArrayList<Result>();
        for (String tile : tiles) results.add(process(data, tile, nowMillis));
        return Collections.unmodifiableList(results);
    }

    /** Clears only uncheckpointed runtime clocks; durable ConflictRecord state is untouched. */
    public void resetRuntimeState() {
        clocks.clear();
    }

    public Result process(KOMEWorldData data, String tileId, long nowMillis) {
        if (data == null) throw new IllegalArgumentException("World data is required.");
        if (nowMillis < 0L) throw new IllegalArgumentException("Lifecycle timestamp must be nonnegative.");
        KOMEConflictRecord record = data.getConflictService().get(tileId);
        if (record == null || !record.isActive()) {
            removeClock(data, tileId);
            return result(Code.NO_CONFLICT, record, "No active conflict is present.");
        }
        if (record.getState() != State.ORDINARY) {
            removeClock(data, record.getTileId());
            return result(Code.NOT_ORDINARY, record,
                "Ordinary battle lifecycle does not process fortified conflict state.");
        }
        if (nowMillis < record.getLastTransition().timestampMillis)
            return result(Code.PUBLICATION_REJECTED, record,
                "Lifecycle timestamp precedes the latest authoritative conflict transition.");

        KOMEFortificationContextResolver.Decision fortification =
            fortificationSource.evaluate(data, record.getTileId());
        if (fortification == null
                || fortification.status != KOMEFortificationContextResolver.Status.CLEAR) {
            removeClock(data, record.getTileId());
            return result(Code.DEFERRED_FORTIFIED, record, fortification == null
                ? "Defensive context is unavailable." : fortification.reason);
        }

        KOMECombatStateEvaluator.Result combat;
        try {
            combat = evaluationSource.evaluate(data, record);
        } catch (RuntimeException failure) {
            combat = null;
        }
        CombatEpisode episode = record.getCombatEpisode();
        if (episode.state != EpisodeState.ACTIVE) {
            removeClock(data, record.getTileId());
            if (!resolved(combat))
                return result(Code.DEFERRED_UNKNOWN_AUTHORITY, record,
                    "Strategic military or diplomacy authority is unresolved.");
            if (!combat.hostilePairPresent)
                return result(Code.NO_CHANGE, record, "No hostile military pair is active.");
            return startEpisode(data, record, nowMillis);
        }

        ActiveClock clock = observe(data, record, nowMillis);
        long pending = clock.pendingActiveMillis;
        TimerSlot previous = record.getResponseTimer();
        if (previous.status == TimerStatus.NOT_STARTED
                || !previous.episodeId.equals(episode.episodeId)) {
            removeClock(data, record.getTileId());
            return result(Code.PUBLICATION_REJECTED, record,
                "Active episode lacks its authoritative response timer.");
        }
        long elapsed = addCapped(previous.elapsedMillis, pending,
            previous.durationSnapshotMillis);
        boolean complete = previous.status == TimerStatus.COMPLETE
            || elapsed >= previous.durationSnapshotMillis;

        if (!resolved(combat)) {
            if (shouldCheckpoint(previous, pending, complete)) {
                KOMEConflictService.Result published = checkpointTimer(data, record,
                    nowMillis, elapsed, complete, clock);
                if (published == null
                        || published.code != KOMEConflictContracts.Code.SUCCESS)
                    return publicationFailure(record, published);
            }
            KOMEConflictRecord current = data.getConflictService().get(record.getTileId());
            return result(Code.DEFERRED_UNKNOWN_AUTHORITY,
                current == null ? record : current,
                "Strategic military or diplomacy authority is unresolved; cessation is deferred.");
        }

        if (!combat.hostilePairPresent)
            return endEpisode(data, record, nowMillis, elapsed, complete, clock);

        if (!shouldCheckpoint(previous, pending, complete))
            return result(Code.NO_CHANGE, record,
                "Active server time remains below the checkpoint cadence.");
        KOMEConflictService.Result published = checkpointTimer(data, record, nowMillis,
            elapsed, complete, clock);
        if (published == null || published.code != KOMEConflictContracts.Code.SUCCESS)
            return publicationFailure(record, published);
        return result(complete && previous.status != TimerStatus.COMPLETE
                ? Code.TIMER_COMPLETED_HOSTILITY_CONTINUES : Code.TIMER_CHECKPOINTED,
            published.record, complete
                ? "Response window completed; hostile military factions remain."
                : "Server-active response time checkpointed.");
    }

    private Result startEpisode(KOMEWorldData data, KOMEConflictRecord record,
            long nowMillis) {
        KOMEOrdinaryBattlePlanner.DurationPlan duration = durationSource.select(data, record);
        if (duration == null || !duration.selected())
            return result(Code.DEFERRED_INVALID_DURATION, record, duration == null
                ? "Response duration authority is unavailable." : duration.reason);
        long sequence = record.getCombatEpisode().sequence + 1L;
        if (sequence < 1L)
            return result(Code.PUBLICATION_REJECTED, record,
                "Combat episode sequence is exhausted.");
        String episodeId = record.getConflictId() + ":E" + sequence;
        CombatEpisode episode = new CombatEpisode(sequence, episodeId,
            EpisodeState.ACTIVE, Long.valueOf(nowMillis), null);
        TimerSlot response = new TimerSlot(TimerStatus.RUNNING,
            duration.durationMillis, 0L, Long.valueOf(nowMillis), episodeId);
        beforePublication.run(data, record);
        KOMEConflictService.Result published = data.getConflictService().checkpointCombat(
            record.getTileId(), ExpectedConflict.at(record.getConflictId(), record.getRevision()),
            episode, response, TimerSlot.unstarted(),
            new Context(nowMillis, "ordinary-battle", "start hostile combat episode"));
        if (published.code != KOMEConflictContracts.Code.SUCCESS)
            return publicationFailure(record, published);
        data.markDirty();
        clocksFor(data).put(record.getTileId(),
            new ActiveClock(record.getConflictId(), episodeId, nowMillis));
        notificationSink.episodeStarted(published.record, duration.durationMillis);
        return result(Code.EPISODE_STARTED, published.record,
            "A new hostile combat episode was published.");
    }

    private Result endEpisode(KOMEWorldData data, KOMEConflictRecord record,
            long nowMillis, long elapsed, boolean complete, ActiveClock clock) {
        CombatEpisode previous = record.getCombatEpisode();
        CombatEpisode ended = new CombatEpisode(previous.sequence, previous.episodeId,
            EpisodeState.INACTIVE, previous.startedAtMillis, Long.valueOf(nowMillis));
        TimerSlot response = new TimerSlot(complete ? TimerStatus.COMPLETE
            : TimerStatus.PAUSED, record.getResponseTimer().durationSnapshotMillis,
            elapsed, Long.valueOf(nowMillis), previous.episodeId);
        beforePublication.run(data, record);
        KOMEConflictService.Result published = data.getConflictService().checkpointCombat(
            record.getTileId(), ExpectedConflict.at(record.getConflictId(), record.getRevision()),
            ended, response, record.getCaptureTimer(),
            new Context(nowMillis, "ordinary-battle", "end hostile combat episode"));
        if (published.code != KOMEConflictContracts.Code.SUCCESS)
            return publicationFailure(record, published);
        data.markDirty();
        removeClock(data, record.getTileId());
        return result(Code.EPISODE_ENDED_RESOLUTION_ELIGIBLE, published.record,
            complete ? "Hostility ceased after the response window completed."
                : "Hostility ceased; Phase 3 must preserve the incomplete response-window constraint.");
    }

    private KOMEConflictService.Result checkpointTimer(KOMEWorldData data,
            KOMEConflictRecord record, long nowMillis, long elapsed, boolean complete,
            ActiveClock clock) {
        TimerSlot response = new TimerSlot(complete ? TimerStatus.COMPLETE
            : TimerStatus.RUNNING, record.getResponseTimer().durationSnapshotMillis,
            elapsed, Long.valueOf(nowMillis), record.getCombatEpisode().episodeId);
        beforePublication.run(data, record);
        KOMEConflictService.Result published = data.getConflictService().checkpointCombat(
            record.getTileId(), ExpectedConflict.at(record.getConflictId(), record.getRevision()),
            record.getCombatEpisode(), response, record.getCaptureTimer(),
            new Context(nowMillis, "ordinary-battle", "checkpoint server-active response time"));
        if (published.code == KOMEConflictContracts.Code.SUCCESS) {
            data.markDirty();
            clock.pendingActiveMillis = 0L;
            clock.lastObservedMillis = nowMillis;
        } else {
            KOMEConflictRecord current = data.getConflictService().get(record.getTileId());
            if (current == null || !clock.matches(current))
                removeClock(data, record.getTileId());
        }
        return published;
    }

    private ActiveClock observe(KOMEWorldData data, KOMEConflictRecord record,
            long nowMillis) {
        Map<String, ActiveClock> byTile = clocksFor(data);
        ActiveClock clock = byTile.get(record.getTileId());
        if (clock == null || !clock.matches(record)) {
            clock = new ActiveClock(record.getConflictId(),
                record.getCombatEpisode().episodeId, nowMillis);
            byTile.put(record.getTileId(), clock);
            return clock;
        }
        if (nowMillis > clock.lastObservedMillis) {
            clock.pendingActiveMillis = saturatingAdd(clock.pendingActiveMillis,
                nowMillis - clock.lastObservedMillis);
            clock.lastObservedMillis = nowMillis;
        }
        return clock;
    }

    private Map<String, ActiveClock> clocksFor(KOMEWorldData data) {
        Map<String, ActiveClock> result = clocks.get(data);
        if (result == null) {
            result = new LinkedHashMap<String, ActiveClock>();
            clocks.put(data, result);
        }
        return result;
    }

    private void removeClock(KOMEWorldData data, String tileId) {
        Map<String, ActiveClock> byTile = clocks.get(data);
        if (byTile == null) return;
        byTile.remove(KOMEConquestTile.normalizeId(tileId));
        if (byTile.isEmpty()) clocks.remove(data);
    }

    private static boolean resolved(KOMECombatStateEvaluator.Result combat) {
        return combat != null && combat.code == KOMECombatStateEvaluator.Code.RESOLVED
            && !combat.unknownAuthorityPresent;
    }

    private static boolean shouldCheckpoint(TimerSlot previous, long pending,
            boolean complete) {
        return previous.status != TimerStatus.COMPLETE
            && (complete || pending >= CHECKPOINT_CADENCE_MILLIS);
    }

    private static long addCapped(long elapsed, long pending, long duration) {
        return Math.min(duration, saturatingAdd(elapsed, pending));
    }

    private static long saturatingAdd(long first, long second) {
        if (second > Long.MAX_VALUE - first) return Long.MAX_VALUE;
        return first + second;
    }

    private Result publicationFailure(KOMEConflictRecord evaluated,
            KOMEConflictService.Result published) {
        if (published != null && (published.code == KOMEConflictContracts.Code.STALE_REVISION
                || published.code == KOMEConflictContracts.Code.STALE_CONFLICT_ID))
            return result(Code.STALE_RETRY, evaluated,
                "Conflict changed during lifecycle evaluation; the next tick will reevaluate it.");
        return result(Code.PUBLICATION_REJECTED, evaluated, published == null
            ? "Combat checkpoint publication failed."
            : published.code + (published.reason.length() == 0 ? "" : ": " + published.reason));
    }

    private static Result result(Code code, KOMEConflictRecord record, String reason) {
        CombatEpisode episode = record == null ? CombatEpisode.unstarted()
            : record.getCombatEpisode();
        TimerSlot timer = record == null ? TimerSlot.unstarted()
            : record.getResponseTimer();
        boolean complete = timer.status == TimerStatus.COMPLETE
            || timer.status != TimerStatus.NOT_STARTED && timer.durationSnapshotMillis > 0L
                && timer.elapsedMillis >= timer.durationSnapshotMillis;
        return new Result(code, record, episode.episodeId, timer.elapsedMillis,
            timer.durationSnapshotMillis, complete, reason);
    }
}
