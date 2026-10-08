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
    /** Serfs approach the Lord they will serve as Knights; this is not a Master check. */
    public static KOMEProgressionNpcRank requiredSuperior(KOMEProgressionRank rank) {
        return rank==KOMEProgressionRank.SERF?KOMEProgressionNpcRank.LORD:requiredNpcRankForPlayerRank(rank);
    }
    public static boolean accepts(KOMEWorldData data,KOMEProgressionRank rank,KOMEProgressionNpcRank npcRank,
            String pledge,String npcFaction) {
        lotr.common.fac.LOTRFaction faction=KOMEProgressionFactionResolver.resolve(pledge);
        return faction!=null&&faction.isPlayableAlignmentFaction()
            &&KOMEProgressionFactionResolver.matches(npcFaction,faction)&&requiredSuperior(rank)==npcRank
            &&(rank!=KOMEProgressionRank.PRINCE||!KOMERulerService.hasRuler(data,pledge));
    }
    /** Political authority seam for later Prince service; it issues no quests or promotion. */
    public static java.util.UUID sovereignPlayer(KOMEWorldData data,String faction){return KOMERulerService.getRuler(data,faction);}
    public static java.util.UUID sovereignNpc(KOMEWorldData data,String faction){
        if(sovereignPlayer(data,faction)!=null)return null;
        KOMEProgressionNpcRankRecord ruler=KOMEProgressionRulerService.slot(data,faction);return ruler==null?null:ruler.npcUuid;
    }
    public static KOMEProgressionRankSummary presentAuthority(KOMEProgressionRankSummary summary,KOMEWorldData data,KOMEPlayerProgression p,String faction){
        if(p.getCanonicalRank()!=KOMEProgressionRank.PRINCE||sovereignPlayer(data,faction)==null)return summary;
        return new KOMEProgressionRankSummary(summary.factionKey,summary.currentRank,summary.nextRank,summary.promotionTitle,summary.requirements,
            "Fealty","Your sovereign","Your faction's King is "+KOMERulerService.getRulerName(data,faction)+". Seek your sovereign; the ruler here cannot receive your political fealty.");
    }
}
