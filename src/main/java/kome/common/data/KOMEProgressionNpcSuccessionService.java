package kome.common.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Compatibility entry points: Kings keep their identity and wait for capital respawn. */
public final class KOMEProgressionNpcSuccessionService {
    private KOMEProgressionNpcSuccessionService() { }
    /** No immediate succession: the canonical King keeps the same ruler slot. */
    public static UUID handleKingDeath(KOMEWorldData data, UUID deadKing) {
        KOMEProgressionRulerService.noteDeath(data,deadKing);
        return null;
    }
    /** Compatibility overload; entity-load state intentionally has no bearing on succession. */
    public static UUID handleKingDeath(KOMEWorldData data, UUID deadKing, Set<UUID> ignoredLiveEligibleNobles) { return handleKingDeath(data, deadKing); }
    public static boolean invalidatePrinceDeath(KOMEWorldData data, UUID deadNpc) { if(data==null||deadNpc==null)return false; KOMEProgressionNpcRankRecord record=data.progressionNpcRanks.get(deadNpc); if(record==null||record.rank!=KOMEProgressionNpcRank.PRINCE)return false; data.progressionNpcRanks.remove(deadNpc);data.markDirty();return true; }
}
