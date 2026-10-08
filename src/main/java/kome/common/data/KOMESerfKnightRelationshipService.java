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

    public static boolean canEstablishLiege(KOMEPlayerProgression progression,long day) {
        if(progression==null||progression.getCanonicalRank().order<KOMEProgressionRank.KNIGHT.order)return false;
        KOMESerfKnightProgression state=progression.getSerfKnightProgression();
        return !state.hasLiege()&&!state.isLockedOut(day);
    }

    /** The same captain eligibility as a standing trial, without a new trial or rank mutation. */
    public static Result establishLiege(net.minecraft.entity.player.EntityPlayerMP player,
            KOMEWorldData data,lotr.common.entity.npc.LOTREntityNPC npc) {
        if(player!=null&&data!=null&&data.getProgression(player.getUniqueID()).getCanonicalRank()==KOMEProgressionRank.PRINCE){
            lotr.common.fac.LOTRFaction pledge=lotr.common.LOTRLevelData.getData(player).getPledgeFaction();
            if(pledge!=null&&KOMERulerService.hasRuler(data,pledge.codeName()))
                return reject("Your faction's player King holds your allegiance. Seek "+KOMERulerService.getRulerName(data,pledge.codeName())+".");
        }
        if(player==null||data==null||npc==null||player.worldObj.isRemote
                ||player.getDistanceSqToEntity(npc)>64D
                ||data!=KOMEWorldData.get(player.worldObj)
                ||!KOMEProgressionOfferBridge.canReplaceLiegeFrom(player,npc))
            return reject("That NPC is not eligible to become your Liege.");
        KOMEPlayerProgression progression=data.getProgression(player.getUniqueID());
        progression.getSerfKnightProgression().setLiege(KOMEProgressionNpcRankService.referenceOf(npc));
        KOMEProgressionNpcRoles.syncPlayer(data,player.getUniqueID());
        data.markDirty();
        return ok();
    }

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
        if (state.hasLiege()) state.leaveLiege();
        state.setSerfdomMaster(target);
        if (level != ForceLevel.SERF) {
            for (KOMESerfKnightDutyType duty : KOMESerfKnightDutyType.values()) {
                state.assignDuty(duty, null);
                state.completeDuty(duty);
            }
            state.setLiege(target);
        }
        KOMECanonicalRankService.setCanonicalRank(data, playerId, level.rank);
        KOMEProgressionNpcRoles.syncPlayer(data,playerId);
        data.markDirty();
        return ok();
    }

    /** Uses the ordinary canonical leave methods; caller owns world encounter cleanup. */
    public static Result clear(KOMEWorldData data, UUID playerId, String targetedNpcId) {
        if (data == null || playerId == null) return reject("Missing player progression.");
        KOMEPlayerProgression progression = data.getProgression(playerId);
        KOMESerfKnightProgression state = progression.getSerfKnightProgression();
        boolean master = state.getSerfdomMaster().isSet() && state.getSerfdomMaster().entityUuid.equals(targetedNpcId);
        boolean liege = state.hasLiege() && state.getLiege().entityUuid.equals(targetedNpcId);
        if (!master && !liege) return reject("The targeted NPC has no relationship to clear.");
        KOMESerfKnightService.Result result = master
            ? KOMESerfKnightService.leaveSerfdomMaster(state)
            : KOMESerfKnightService.leaveLiege(progression);
        if (!result.success) return reject(result.reason);
        KOMEProgressionNpcRoles.syncPlayer(data,playerId);
        data.markDirty();
        return ok();
    }
}
