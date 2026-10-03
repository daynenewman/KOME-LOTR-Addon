package kome.common.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Deterministic NPC King succession; it never promotes Lords or creates a King without a Prince. */
public final class KOMEProgressionNpcSuccessionService {
    private KOMEProgressionNpcSuccessionService() { }
    /** Persisted explicit Prince records are authoritative even while their entities are unloaded. */
    public static UUID handleKingDeath(KOMEWorldData data, UUID deadKing) {
        if(data==null||deadKing==null) return null; KOMEProgressionNpcRankRecord king=data.progressionNpcRanks.get(deadKing);
        if(king==null||king.rank!=KOMEProgressionNpcRank.KING) return null;
        data.progressionNpcRanks.remove(deadKing); KOMEFactionCapitalRecord capital=KOMEFactionCapitalService.getCapital(data,king.factionKey);
        if(capital!=null) data.progressionNpcRoyalRestorations.put(deadKing,new KOMEProgressionNpcRoyalRestoration(deadKing,king.factionKey,capital));
        if(KOMERulerService.hasRuler(data,king.factionKey)){data.markDirty();return null;}
        List<UUID> candidates=new ArrayList<UUID>();
        for(KOMEProgressionNpcRankRecord record:data.progressionNpcRanks.values()) if(record.rank==KOMEProgressionNpcRank.PRINCE&&king.factionKey.equals(record.factionKey)) candidates.add(record.npcUuid);
        Collections.sort(candidates,new java.util.Comparator<UUID>(){public int compare(UUID a,UUID b){return a.toString().compareTo(b.toString());}});
        if(candidates.isEmpty()){data.markDirty();return null;} UUID successor=candidates.get(0); KOMEProgressionNpcRankRecord prince=data.progressionNpcRanks.get(successor); data.progressionNpcRanks.put(successor,new KOMEProgressionNpcRankRecord(successor,prince.factionKey,KOMEProgressionNpcRank.KING,prince.displayName)); data.markDirty(); return successor;
    }
    /** Compatibility overload; entity-load state intentionally has no bearing on succession. */
    public static UUID handleKingDeath(KOMEWorldData data, UUID deadKing, Set<UUID> ignoredLiveEligibleNobles) { return handleKingDeath(data, deadKing); }
    public static boolean invalidatePrinceDeath(KOMEWorldData data, UUID deadNpc) { if(data==null||deadNpc==null)return false; KOMEProgressionNpcRankRecord record=data.progressionNpcRanks.get(deadNpc); if(record==null||record.rank!=KOMEProgressionNpcRank.PRINCE)return false; data.progressionNpcRanks.remove(deadNpc);data.markDirty();return true; }
}
