package kome.common.data;

/** Central exact-rank relationship policy for canonical player progression. */
public final class KOMEProgressionLiegePolicy {
    private KOMEProgressionLiegePolicy() { }
    public static KOMEProgressionNpcRank requiredNpcRankForPlayerRank(KOMEProgressionRank rank) {
        if(rank==KOMEProgressionRank.SERF) return KOMEProgressionNpcRank.UNRANKED;
        if(rank==KOMEProgressionRank.KNIGHT) return KOMEProgressionNpcRank.LORD;
        if(rank==KOMEProgressionRank.LORD) return KOMEProgressionNpcRank.PRINCE;
        if(rank==KOMEProgressionRank.PRINCE) return KOMEProgressionNpcRank.KING;
        return null;
    }
    public static boolean hasRequiredNpcRank(KOMEProgressionRank playerRank, KOMEProgressionNpcRank npcRank) { return requiredNpcRankForPlayerRank(playerRank)==npcRank; }
}
