package kome.common.data;

import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;

/** Controlled mutation boundary for revised player-rank authority. */
public final class KOMECanonicalRankService {
    private KOMECanonicalRankService() { }
    public static boolean setCanonicalRank(KOMEWorldData data, UUID playerId, KOMEProgressionRank rank) {
        if (data == null || playerId == null || rank == null) return false;
        KOMEPlayerProgression progression = data.getProgression(playerId);
        if (progression.getCanonicalRank() == rank) return false;
        progression.setCanonicalRank(rank);
        data.markDirty();
        return true;
    }

    /** Staff structural override: active Serf encounters never survive an incompatible rank change. */
    public static boolean overrideCanonicalRank(
            KOMEWorldData data,
            EntityPlayerMP player,
            KOMEProgressionRank rank) {
        if(data==null||player==null||rank==null)return false;

        UUID playerId=player.getUniqueID();
        KOMEPlayerProgression progression=data.getProgression(playerId);

        if(progression.getCanonicalRank()==rank)return false;

        KOMEProgressionEncounterCleanup.cleanup(player,progression);
        progression.getSerfKnightProgression().reset();
        progression.setCanonicalRank(rank);
        reconcileOverrideRankMarkers(progression,rank);

        KOMEProgressionAutoCompleter.recomputeUnlocks(progression);
        KOMEProgressionNpcRoles.syncPlayer(data,playerId);

        data.markDirty();
        return true;
    }
    private static void reconcileOverrideRankMarkers(
            KOMEPlayerProgression progression,
            KOMEProgressionRank rank) {
        setOverrideMarker(
            progression,
            "wanderer.find_serf_lord",
            rank.order>=KOMEProgressionRank.SERF.order);

        setOverrideMarker(
            progression,
            "serf.title_knight",
            rank.order>=KOMEProgressionRank.KNIGHT.order);

        setOverrideMarker(
            progression,
            "knight.title_lord",
            rank.order>=KOMEProgressionRank.LORD.order);

        setOverrideMarker(
            progression,
            "lord.title_prince_king",
            rank.order>=KOMEProgressionRank.PRINCE.order);
    }

    private static void setOverrideMarker(
            KOMEPlayerProgression progression,
            String id,
            boolean completed) {
        if(completed) progression.grant(id);
        else progression.revoke(id);
    }
    /** The only gameplay transition into Serfdom; pledge state alone is never rank authority. */
    public static boolean enterSerfdom(KOMEWorldData data, UUID playerId) {
        if (data == null || playerId == null) return false;
        KOMEPlayerProgression progression = data.getProgression(playerId);
        if (progression.getCanonicalRank() != KOMEProgressionRank.WANDERER
            || !progression.getSerfKnightProgression().getSerfdomMaster().isSet()) return false;
        progression.setCanonicalRank(KOMEProgressionRank.SERF);
        progression.grant("wanderer.find_serf_lord");
        KOMEProgressionAutoCompleter.applyUnlocks(progression);
        data.markDirty();
        return true;
    }
}
