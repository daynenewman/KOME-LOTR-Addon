package kome.common.data;

/** NPC social rank is distinct from permanent player progression rank. */
public enum KOMEProgressionNpcRank {
    UNRANKED("unranked", "Unranked", 0, KOMEProgressionNpcRankScarcity.UNRESTRICTED),
    LORD("lord", "Lord", 1, KOMEProgressionNpcRankScarcity.COMMON),
    PRINCE("prince", "Prince", 2, KOMEProgressionNpcRankScarcity.RARE),
    KING("king", "King", 3, KOMEProgressionNpcRankScarcity.UNIQUE);
    public final String key, displayName;
    public final int order;
    public final KOMEProgressionNpcRankScarcity scarcity;
    KOMEProgressionNpcRank(String key, String displayName, int order, KOMEProgressionNpcRankScarcity scarcity) { this.key=key; this.displayName=displayName; this.order=order; this.scarcity=scarcity; }
    /** Parses stable keys only; player rank names and display aliases are intentionally not accepted. */
    public static KOMEProgressionNpcRank forKey(String key) { if(key == null) return null; for(KOMEProgressionNpcRank rank:values()) if(rank.key.equals(key)) return rank; return null; }
}
