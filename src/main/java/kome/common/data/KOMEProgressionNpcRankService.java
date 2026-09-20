package kome.common.data;

import java.util.UUID;
import kome.common.KOMEReflection;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.fac.LOTRFaction;

/** Authority and derived-effective-rank logic for the NPC social hierarchy. */
public final class KOMEProgressionNpcRankService {
    public static final class Result { public final boolean success; public final String reason; private Result(boolean success,String reason){this.success=success;this.reason=reason;} }
    private KOMEProgressionNpcRankService() { }
    private static Result ok(){return new Result(true,"");} private static Result reject(String reason){return new Result(false,reason);}
    public static KOMEProgressionNpcRank effectiveRank(KOMEWorldData data, UUID npcUuid, boolean combatUnitHiring) {
        KOMEProgressionNpcRankRecord record=data==null || npcUuid==null ? null : data.progressionNpcRanks.get(npcUuid);
        if (!combatUnitHiring) return KOMEProgressionNpcRank.UNRANKED;
        return record == null ? KOMEProgressionNpcRank.LORD : record.rank;
    }
    public static KOMEProgressionNpcRank effectiveRank(KOMEWorldData data, LOTREntityNPC npc) { if(npc==null) return KOMEProgressionNpcRank.UNRANKED; boolean hiring=KOMEProgressionLords.isCombatUnitHiringNpc(npc); KOMEProgressionNpcRankRecord record=data==null ? null : data.progressionNpcRanks.get(KOMEReflection.getEntityUUID(npc)); return record!=null && !hiring ? KOMEProgressionNpcRank.UNRANKED : effectiveRank(data,KOMEReflection.getEntityUUID(npc),hiring); }
    public static boolean isValidFactionNpc(LOTREntityNPC npc) { return npc != null && npc.getFaction()!=null; }
    public static Result assignElevatedRank(KOMEWorldData data, UUID npcUuid, String factionKey, KOMEProgressionNpcRank rank, String displayName, boolean combatUnitHiring) {
        if(data==null || npcUuid==null) return reject("World data and NPC UUID are required."); if(!combatUnitHiring) return reject("Only combat-unit-hiring NPCs may hold an elevated noble rank.");
        if(rank!=KOMEProgressionNpcRank.PRINCE && rank!=KOMEProgressionNpcRank.KING) return reject("Only Prince or King may be assigned explicitly.");
        KOMEProgressionNpcRankRecord record; try { record=new KOMEProgressionNpcRankRecord(npcUuid,factionKey,rank,displayName); } catch(IllegalArgumentException invalid) { return reject(invalid.getMessage()); }
        KOMEProgressionNpcRankRecord existing=data.progressionNpcRanks.get(npcUuid);
        if(existing!=null && existing.rank==rank && existing.factionKey.equals(record.factionKey)) return ok();
        if(rank==KOMEProgressionNpcRank.KING) for(KOMEProgressionNpcRankRecord other:data.progressionNpcRanks.values()) if(other.rank==KOMEProgressionNpcRank.KING && other.factionKey.equals(record.factionKey) && !other.npcUuid.equals(npcUuid)) return reject("That faction already has an NPC King.");
        data.progressionNpcRanks.put(npcUuid,record); data.markDirty(); return ok();
    }
    public static Result assignElevatedRank(KOMEWorldData data, LOTREntityNPC npc, KOMEProgressionNpcRank rank) {
        if(!isValidFactionNpc(npc)) return reject("A valid faction NPC is required."); LOTRFaction faction=npc.getFaction();
        Result result=assignElevatedRank(data,KOMEReflection.getEntityUUID(npc),faction.codeName(),rank,npc.getNPCName(),KOMEProgressionLords.isCombatUnitHiringNpc(npc)); if(result.success) npc.func_110163_bv(); return result;
    }
    public static boolean isActivePoliticalNpcKing(KOMEWorldData data, UUID npcUuid) { if(data==null || npcUuid==null) return false; KOMEProgressionNpcRankRecord record=data.progressionNpcRanks.get(npcUuid); return record!=null && record.rank==KOMEProgressionNpcRank.KING && !KOMERulerService.hasRuler(data,record.factionKey); }
    public static boolean isProgressionReferenced(KOMEWorldData data, UUID npcUuid) { if(data==null || npcUuid==null) return false; String id=npcUuid.toString(); for(KOMEPlayerProgression progression:data.progressions.values()) if(progression!=null && (id.equals(progression.getSerfKnightProgression().getSerfdomMaster().entityUuid) || id.equals(progression.getSerfKnightProgression().getProspectiveLiege().entityUuid))) return true; return false; }
    public static boolean shouldPreventNaturalDespawn(KOMEWorldData data, UUID npcUuid) { KOMEProgressionNpcRankRecord record=data==null || npcUuid==null ? null : data.progressionNpcRanks.get(npcUuid); return isProgressionReferenced(data,npcUuid) || (record!=null && (record.rank==KOMEProgressionNpcRank.PRINCE || record.rank==KOMEProgressionNpcRank.KING)); }
    public static void applyPersistenceProtection(KOMEWorldData data, LOTREntityNPC npc) { if(npc!=null && shouldPreventNaturalDespawn(data,KOMEReflection.getEntityUUID(npc))) npc.func_110163_bv(); }
    public static KOMEProgressionNpcRef referenceOf(LOTREntityNPC npc) { if(!isValidFactionNpc(npc)) return KOMEProgressionNpcRef.EMPTY; LOTRFaction faction=npc.getFaction(); return new KOMEProgressionNpcRef(KOMEReflection.getEntityUUID(npc).toString(),npc.getNPCName(),faction.codeName(),KOMEReflection.getWorld(npc).provider.dimensionId,npc.posX,npc.posY,npc.posZ); }
}
