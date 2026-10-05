package kome.common.data;

import kome.common.config.KOMEConfigRegistry;

/** Pure response-duration and CombatEpisode decision helpers; performs no ticking or mutation. */
public final class KOMEOrdinaryBattlePlanner {
    public enum DurationCode { SELECTED, INVALID_OBJECTIVE_LEVEL, INVALID_CONFIGURATION }
    public enum EpisodeCode {
        HOSTILITY_CONTINUES,
        HOSTILITY_CONTINUES_RESPONSE_EXPIRED,
        WAITING_FOR_RESPONSE_WINDOW,
        READY_FOR_OWNERSHIP_RESOLUTION,
        START_NEW_EPISODE,
        INACTIVE,
        DEFERRED_UNKNOWN_AUTHORITY,
        NOT_ORDINARY_CONFLICT
    }

    public static final class DurationPlan {
        public final DurationCode code;
        public final int objectiveLevel;
        public final long durationMillis;
        public final String reason;

        private DurationPlan(DurationCode code, int level, long duration, String reason) {
            this.code = code;
            objectiveLevel = level;
            durationMillis = duration;
            this.reason = reason;
        }

        public boolean selected() { return code == DurationCode.SELECTED; }
    }

    public static final class EpisodePlan {
        public final EpisodeCode code;
        public final boolean responseWindowComplete;
        public final String reason;

        private EpisodePlan(EpisodeCode code, boolean complete, String reason) {
            this.code = code;
            responseWindowComplete = complete;
            this.reason = reason;
        }
    }

    private KOMEOrdinaryBattlePlanner() { }

    public static DurationPlan responseDuration(KOMEConquestTile tile,
            KOMEConfigRegistry.BattleSettings settings) {
        if (tile == null || settings == null)
            return new DurationPlan(DurationCode.INVALID_CONFIGURATION, 0, 0L,
                "Tile and battle configuration are required.");
        return responseDuration(tile.waypointLevel,
            settings.getResponseLevel1Minutes(), settings.getResponseLevel2Minutes(),
            settings.getResponseLevel3Minutes());
    }

    public static DurationPlan responseDuration(int objectiveLevel,
            int level1Minutes, int level2Minutes, int level3Minutes) {
        if (objectiveLevel < 1 || objectiveLevel > 3)
            return new DurationPlan(DurationCode.INVALID_OBJECTIVE_LEVEL,
                objectiveLevel, 0L, "Conquest objective level must be 1, 2, or 3.");
        if (level1Minutes <= 0 || level2Minutes <= level1Minutes
                || level3Minutes <= level2Minutes)
            return new DurationPlan(DurationCode.INVALID_CONFIGURATION,
                objectiveLevel, 0L, "Battle response durations must be positive and increasing.");
        int minutes = objectiveLevel == 1 ? level1Minutes
            : objectiveLevel == 2 ? level2Minutes : level3Minutes;
        try {
            return new DurationPlan(DurationCode.SELECTED, objectiveLevel,
                Math.multiplyExact((long) minutes, 60_000L),
                "Response duration selected from objective level " + objectiveLevel + ".");
        } catch (ArithmeticException overflow) {
            return new DurationPlan(DurationCode.INVALID_CONFIGURATION,
                objectiveLevel, 0L, "Battle response duration exceeds the supported clock range.");
        }
    }

    public static EpisodePlan evaluateEpisode(KOMEConflictRecord conflict,
            KOMECombatStateEvaluator.Result combat) {
        if (conflict == null || !conflict.isActive()
                || conflict.getState() != KOMEConflictRecord.State.ORDINARY)
            return new EpisodePlan(EpisodeCode.NOT_ORDINARY_CONFLICT, false,
                "Only an active ordinary conflict is evaluated here.");
        if (combat == null || combat.code != KOMECombatStateEvaluator.Code.RESOLVED
                || combat.unknownAuthorityPresent)
            return new EpisodePlan(EpisodeCode.DEFERRED_UNKNOWN_AUTHORITY, false,
                "Strategic military or diplomacy authority is unresolved.");

        KOMEConflictRecord.CombatEpisode episode = conflict.getCombatEpisode();
        boolean complete = responseComplete(conflict.getResponseTimer());
        if (episode.state != KOMEConflictRecord.EpisodeState.ACTIVE) {
            return combat.hostilePairPresent
                ? new EpisodePlan(EpisodeCode.START_NEW_EPISODE, false,
                    "Renewed hostility requires a fresh combat episode.")
                : new EpisodePlan(EpisodeCode.INACTIVE, complete,
                    "No hostile military pair is active.");
        }
        if (combat.hostilePairPresent) {
            return new EpisodePlan(complete
                    ? EpisodeCode.HOSTILITY_CONTINUES_RESPONSE_EXPIRED
                    : EpisodeCode.HOSTILITY_CONTINUES,
                complete, complete
                    ? "The response window is complete, but hostile military factions remain."
                    : "Hostile military factions remain in the active response window.");
        }
        return complete
            ? new EpisodePlan(EpisodeCode.READY_FOR_OWNERSHIP_RESOLUTION, true,
                "Timing is complete and no hostile military pair remains.")
            : new EpisodePlan(EpisodeCode.WAITING_FOR_RESPONSE_WINDOW, false,
                "No hostile military pair remains, but the response window is incomplete.");
    }

    private static boolean responseComplete(KOMEConflictRecord.TimerSlot timer) {
        return timer != null && timer.status != KOMEConflictRecord.TimerStatus.NOT_STARTED
            && (timer.status == KOMEConflictRecord.TimerStatus.COMPLETE
                || timer.durationSnapshotMillis > 0L
                    && timer.elapsedMillis >= timer.durationSnapshotMillis);
    }
}
