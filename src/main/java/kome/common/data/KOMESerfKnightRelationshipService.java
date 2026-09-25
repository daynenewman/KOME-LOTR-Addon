package kome.common.data;

import java.util.UUID;

/**
 * Single server-side boundary for changing the persisted Serf-to-Knight
 * relationship. Normal gameplay enters through the selection methods on
 * {@link KOMESerfKnightService}; staff testing uses the same state and the
 * same leave methods through this deliberately explicit override.
 */
public final class KOMESerfKnightRelationshipService {
    public enum ForceLevel {
        SERF(KOMEProgressionRank.SERF), KNIGHT(KOMEProgressionRank.KNIGHT), LORD(KOMEProgressionRank.LORD);
        public final KOMEProgressionRank rank;
        ForceLevel(KOMEProgressionRank rank) { this.rank = rank; }
        public static ForceLevel forCommand(String value) {
            if (value == null) return null;
            for (ForceLevel level : values()) if (level.name().equalsIgnoreCase(value)) return level;
            return null;
        }
    }

    public static final class Result {
        public final boolean success;
        public final String reason;
        private Result(boolean success, String reason) { this.success = success; this.reason = reason; }
    }

    private KOMESerfKnightRelationshipService() { }
    private static Result ok() { return new Result(true, ""); }
    private static Result reject(String reason) { return new Result(false, reason); }

    /**
     * Staff-only callers have already validated the target NPC. This does not
     * grant trials, achievements, or permissions. Higher relationships need
     * completed duty records because the canonical load reconciler otherwise
     * rejects a persisted liege as impossible; these records are structural
     * state only and do not invoke any duty reward path.
     */
    public static Result force(KOMEWorldData data, UUID playerId, KOMEProgressionNpcRef target, ForceLevel level) {
        if (data == null || playerId == null || target == null || !target.isSet() || level == null)
            return reject("A valid relationship target and level are required.");
        KOMEPlayerProgression progression = data.getProgression(playerId);
        KOMESerfKnightProgression state = progression.getSerfKnightProgression();
        if (state.getSerfdomMaster().isSet()) state.leaveSerfdomMaster();
        else if (state.getProspectiveLiege().isSet()) state.leaveProspectiveLiege();
        state.setSerfdomMaster(target);
        if (level != ForceLevel.SERF) {
            for (KOMESerfKnightDutyType duty : KOMESerfKnightDutyType.values()) {
                state.assignDuty(duty, null);
                state.completeDuty(duty);
            }
            state.setProspectiveLiege(target);
        }
        KOMECanonicalRankService.setCanonicalRank(data, playerId, level.rank);
        KOMEProgressionNpcRoles.syncPlayer(data,playerId);
        data.markDirty();
        return ok();
    }

    /** Uses the ordinary canonical leave methods; caller owns world encounter cleanup. */
    public static Result clear(KOMEWorldData data, UUID playerId, String targetedNpcId) {
        if (data == null || playerId == null) return reject("Missing player progression.");
        KOMESerfKnightProgression state = data.getProgression(playerId).getSerfKnightProgression();
        boolean master = state.getSerfdomMaster().isSet() && state.getSerfdomMaster().entityUuid.equals(targetedNpcId);
        boolean liege = state.getProspectiveLiege().isSet() && state.getProspectiveLiege().entityUuid.equals(targetedNpcId);
        if (!master && !liege) return reject("The targeted NPC has no relationship to clear.");
        KOMESerfKnightService.Result result = master
            ? KOMESerfKnightService.leaveSerfdomMaster(state)
            : KOMESerfKnightService.leaveProspectiveLiege(state);
        if (!result.success) return reject(result.reason);
        KOMEProgressionNpcRoles.syncPlayer(data,playerId);
        data.markDirty();
        return ok();
    }
}
