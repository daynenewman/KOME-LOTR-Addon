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
    public static KOMEProgressionNpcRank naturalRank(java.util.Random random) {
        return random.nextInt(4)==0?KOMEProgressionNpcRank.PRINCE:KOMEProgressionNpcRank.LORD;
    }
    /** Called at native initial spawn, never on reload or during ordinary ticking. */
    public static void assignNatural(LOTREntityNPC npc) {
        if(npc==null||npc.worldObj==null||npc.worldObj.isRemote||!KOMEProgressionLords.isStandingTrialLiegeCandidate(npc))return;
        KOMEWorldData data=KOMEWorldData.get(npc.worldObj);UUID id=npc.getUniqueID();
        KOMEProgressionNpcRankRecord existing=data.progressionNpcRanks.get(id);
        if(existing==null||existing.provisionalSpawn){
            // Existing relationships are migrated as Lords, including old force-test saves.
            KOMEProgressionNpcRank rank=KOMEProgressionNpcRoles.protects(data,id)?KOMEProgressionNpcRank.LORD:naturalRank(npc.worldObj.rand);
            data.progressionNpcRanks.put(id,new KOMEProgressionNpcRankRecord(id,npc.getFaction().codeName(),rank,npc.getNPCName()));data.markDirty();
        }
        migrateLoaded(data,npc);
        if(existing==null||existing.provisionalSpawn)for(Object value:npc.worldObj.playerEntities)
            if(value instanceof net.minecraft.entity.player.EntityPlayerMP&&npc.getDistanceSqToEntity((net.minecraft.entity.player.EntityPlayerMP)value)<=1024D)
                KOMEProgressionOfferBridge.prepareStandingTrialInteraction((net.minecraft.entity.player.EntityPlayerMP)value,npc);
    }
    public static void migrateLoaded(KOMEWorldData data,LOTREntityNPC npc){
        if(data==null||npc==null||!KOMEProgressionLords.isStandingTrialLiegeCandidate(npc))return;
        UUID id=npc.getUniqueID();
        if(!data.progressionNpcRanks.containsKey(id)){KOMEProgressionNpcRankRecord record=new KOMEProgressionNpcRankRecord(id,npc.getFaction().codeName(),KOMEProgressionNpcRank.LORD,npc.getNPCName());record.provisionalSpawn=true;data.progressionNpcRanks.put(id,record);data.markDirty();}
        KOMEProgressionNpcRankRecord r=data.progressionNpcRanks.get(id);
        KOMEProgressionNativeAuthority.applyName(npc,r);
    }
    public static void migrateFromSave(LOTREntityNPC npc){
        if(npc==null||npc.worldObj==null||npc.worldObj.isRemote||!KOMEProgressionLords.isStandingTrialLiegeCandidate(npc))return;
        KOMEWorldData data=KOMEWorldData.get(npc.worldObj);migrateLoaded(data,npc);
        data.progressionNpcRanks.get(npc.getUniqueID()).provisionalSpawn=false;
    }
    /** Existing Lieges without records were historically Lords. Never infer a Prince or King. */
    static boolean migrateRelationships(KOMEWorldData data){
        boolean changed=false;
        for(KOMEPlayerProgression p:data.progressions.values()){
            KOMESerfKnightProgression state=p.getSerfKnightProgression();KOMEProgressionNpcRef liege=state.getLiege();if(!liege.isSet())continue;
            if(p.getCanonicalRank().order<KOMEProgressionRank.SERF.order)continue;
            UUID id=UUID.fromString(liege.entityUuid);KOMEProgressionNpcRankRecord r=data.progressionNpcRanks.get(id);
            if(r==null){r=new KOMEProgressionNpcRankRecord(id,liege.factionKey,KOMEProgressionNpcRank.LORD,liege.displayName);data.progressionNpcRanks.put(id,r);changed=true;}
            if(!KOMEProgressionLiegePolicy.accepts(data,p.getCanonicalRank(),r.rank,liege.factionKey,r.factionKey)){
                if(p.getCanonicalRank().order>=KOMEProgressionRank.KNIGHT.order)state.releaseLiegeAfterPromotion();
                else state.handleLiegeDeath(false);
                changed=true;
            }
        }
        return changed;
    }
    public static KOMEProgressionNpcRank effectiveRank(KOMEWorldData data, UUID npcUuid, boolean combatUnitHiring) {
        KOMEProgressionNpcRankRecord record=data==null || npcUuid==null ? null : data.progressionNpcRanks.get(npcUuid);
        if (!combatUnitHiring) return KOMEProgressionNpcRank.UNRANKED;
        return record == null ? KOMEProgressionNpcRank.LORD : record.rank;
    }
    public static KOMEProgressionNpcRank effectiveRank(KOMEWorldData data, LOTREntityNPC npc) { if(npc==null) return KOMEProgressionNpcRank.UNRANKED; boolean hiring=KOMEProgressionLords.isCombatUnitHiringNpc(npc); KOMEProgressionNpcRankRecord record=data==null ? null : data.progressionNpcRanks.get(KOMEReflection.getEntityUUID(npc)); return record!=null && (!hiring||!KOMEProgressionFactionResolver.matches(record.factionKey,npc.getFaction())) ? KOMEProgressionNpcRank.UNRANKED : effectiveRank(data,KOMEReflection.getEntityUUID(npc),hiring); }
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
        if(rank==KOMEProgressionNpcRank.KING){KOMEFactionCapitalRecord c=KOMEFactionCapitalService.getCapital(data,faction.codeName());
            if(c==null||KOMEProgressionNativeAuthority.definition(faction.codeName())==null||npc.worldObj.provider.dimensionId!=c.getDeploymentDimensionId())return reject("A canonical ruler requires a supported faction and its capital dimension.");}
        Result result=assignElevatedRank(data,KOMEReflection.getEntityUUID(npc),faction.codeName(),rank,npc.getNPCName(),KOMEProgressionLords.isCombatUnitHiringNpc(npc));
        if(result.success){migrateLoaded(data,npc);if(rank==KOMEProgressionNpcRank.KING)KOMEProgressionRulerService.reconcileJoin(data,npc);}return result;
    }
    public static boolean isActivePoliticalNpcKing(KOMEWorldData data, UUID npcUuid) { if(data==null || npcUuid==null) return false; KOMEProgressionNpcRankRecord record=data.progressionNpcRanks.get(npcUuid); return record!=null && record.rank==KOMEProgressionNpcRank.KING && !KOMERulerService.hasRuler(data,record.factionKey); }
    public static boolean isProgressionReferenced(KOMEWorldData data, UUID npcUuid) { return KOMEProgressionNpcRoles.protects(data,npcUuid); }
    public static boolean shouldPreventNaturalDespawn(KOMEWorldData data, UUID npcUuid) { KOMEProgressionNpcRankRecord record=data==null || npcUuid==null ? null : data.progressionNpcRanks.get(npcUuid); return isProgressionReferenced(data,npcUuid) || (record!=null && (record.rank==KOMEProgressionNpcRank.PRINCE || record.rank==KOMEProgressionNpcRank.KING)); }
    public static KOMEProgressionNpcRef referenceOf(LOTREntityNPC npc) { if(!isValidFactionNpc(npc)) return KOMEProgressionNpcRef.EMPTY; LOTRFaction faction=npc.getFaction(); String name=npc.getDataWatcher()!=null&&npc.hasCustomNameTag()?npc.getCustomNameTag():npc.getNPCName(); return new KOMEProgressionNpcRef(KOMEReflection.getEntityUUID(npc).toString(),name,faction.codeName(),KOMEReflection.getWorld(npc).provider.dimensionId,npc.posX,npc.posY,npc.posZ); }
}
