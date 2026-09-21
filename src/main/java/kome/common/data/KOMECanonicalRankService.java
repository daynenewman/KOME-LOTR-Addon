package kome.common.data;

import java.util.UUID;

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

    /** The only gameplay transition into Serfdom; pledge state alone is never rank authority. */
    public static boolean enterSerfdom(KOMEWorldData data, UUID playerId) {
        if (data == null || playerId == null) return false;
        KOMEPlayerProgression progression = data.getProgression(playerId);
        if (progression.getCanonicalRank() != KOMEProgressionRank.WANDERER
            || !progression.getSerfKnightProgression().getSerfdomMaster().isSet()) return false;
        progression.setCanonicalRank(KOMEProgressionRank.SERF);
        data.markDirty();
        return true;
    }
}
