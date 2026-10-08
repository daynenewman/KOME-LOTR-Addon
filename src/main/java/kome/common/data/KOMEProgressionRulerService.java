package kome.common.data;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lotr.common.entity.npc.LOTREntityNPC;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

/** One persistent KING identity per supported faction, separate from the player King office. */
public final class KOMEProgressionRulerService {
    public static final long RESPAWN_MILLIS=600_000L;
    private static final String GENERATION="KOMERulerIncarnation";
    interface NpcFactory { LOTREntityNPC create(World world,KOMEProgressionNativeAuthority.Definition definition); }
    static NpcFactory npcFactory=(world,definition)->definition.create(world);
    private KOMEProgressionRulerService(){}
    public static KOMEProgressionNpcRankRecord slot(KOMEWorldData data,String faction){
        KOMEProgressionNpcRankRecord winner=null;
        if(data!=null)for(KOMEProgressionNpcRankRecord r:data.progressionNpcRanks.values())
            if(r.rank==KOMEProgressionNpcRank.KING&&r.factionKey.equals(KOMEAlliance.normalizeFactionKey(faction))
                &&(winner==null||r.npcUuid.toString().compareTo(winner.npcUuid.toString())<0))winner=r;
        return winner;
    }
    static boolean noteDeath(KOMEWorldData data,UUID id){
        KOMEProgressionNpcRankRecord r=data==null?null:data.progressionNpcRanks.get(id);
        if(r==null||r.rank!=KOMEProgressionNpcRank.KING||r.respawnRemainingMillis>=0)return false;
        r.rulerInitialized=true;r.rulerSpawned=false;r.respawnRemainingMillis=RESPAWN_MILLIS;
        r.lastRuntimeNanos=System.nanoTime();r.missingChecks=0;data.markDirty();return true;
    }
    static void advance(KOMEProgressionNpcRankRecord r,long elapsedMillis){
        if(r.respawnRemainingMillis>0)r.respawnRemainingMillis=Math.max(0,r.respawnRemainingMillis-Math.max(0,elapsedMillis));
    }
    static boolean noteDeath(KOMEWorldData data,LOTREntityNPC npc){
        KOMEProgressionNpcRankRecord r=data.progressionNpcRanks.get(npc.getUniqueID());
        return r!=null&&r.rank==KOMEProgressionNpcRank.KING
            &&KOMEProgressionEncounterMarker.rulerIncarnation(npc)==r.incarnation
            &&KOMEProgressionFactionResolver.matches(r.factionKey,npc.getFaction())&&noteDeath(data,npc.getUniqueID());
    }
    static boolean canRespawn(KOMEProgressionNpcRankRecord r){return r!=null&&!r.rulerSpawned&&r.respawnRemainingMillis<=0;}
    /** Join guard before ordinary role reconciliation. Duplicate entities never drop inventory. */
    public static boolean reconcileJoin(KOMEWorldData data,LOTREntityNPC npc){
        String copiedSlot=KOMEProgressionEncounterMarker.rulerSlot(npc);
        if(!copiedSlot.isEmpty()&&!copiedSlot.equals(npc.getUniqueID().toString()))return true;
        KOMEProgressionNpcRankRecord r=data.progressionNpcRanks.get(npc.getUniqueID());
        if(r==null||r.rank!=KOMEProgressionNpcRank.KING)return false;
        if(!KOMEProgressionFactionResolver.matches(r.factionKey,npc.getFaction()))return true;
        KOMEFactionCapitalRecord capital=KOMEFactionCapitalService.getCapital(data,r.factionKey);
        if(capital!=null&&capital.getDeploymentDimensionId()!=npc.worldObj.provider.dimensionId){
            r.rulerInitialized=true;r.rulerSpawned=false;r.respawnRemainingMillis=0;data.markDirty();return true;
        }
        if(slot(data,r.factionKey)!=r)return true;
        int generation=KOMEProgressionEncounterMarker.rulerIncarnation(npc);
        if(r.rulerInitialized&&(r.respawnRemainingMillis>=0||generation!=r.incarnation))return true;
        for(Object o:npc.worldObj.loadedEntityList)if(o instanceof LOTREntityNPC&&o!=npc
                &&((Entity)o).isEntityAlive()&&r.npcUuid.equals(((Entity)o).getUniqueID()))return true;
        r.rulerInitialized=true;r.rulerSpawned=true;r.respawnRemainingMillis=-1;
        if(r.displayName.isEmpty())r.displayName=npc.getNPCName();
        KOMEProgressionEncounterMarker.stampRuler(npc,r.incarnation);
        npc.setShouldTraderRespawn(false);
        r.rulerLocation=KOMEProgressionNpcRankService.referenceOf(npc);
        applyHome(data,npc);data.markDirty();return false;
    }
    private static void applyHome(KOMEWorldData data,LOTREntityNPC npc){
        KOMEFactionCapitalRecord c=KOMEFactionCapitalService.getCapital(data,npc.getFaction().codeName());
        if(c!=null&&c.getDeploymentDimensionId()==npc.worldObj.provider.dimensionId)
            npc.setHomeArea((int)Math.floor(c.getDeploymentX()),(int)Math.floor(npc.posY),(int)Math.floor(c.getDeploymentZ()),24);
    }
    public static void tick(KOMEWorldData data,World world){
        tick(data,world,System.nanoTime());
    }
    static void tick(KOMEWorldData data,World world,long now){
        if(data==null||world==null||world.isRemote)return;
        for(KOMEProgressionNpcRankRecord r:data.progressionNpcRanks.values())if(r.rank==KOMEProgressionNpcRank.KING){
            if(r.lastRuntimeNanos!=0&&r.respawnRemainingMillis>0){long elapsed=(now-r.lastRuntimeNanos)/1_000_000L;if(elapsed>0){advance(r,elapsed);data.markDirty();}}
            r.lastRuntimeNanos=now;
        }
        if(world.provider==null||world.provider.dimensionId!=lotr.common.LOTRDimension.MIDDLE_EARTH.dimensionID||world.getWorldInfo()==null)return;
        for(KOMEProgressionNativeAuthority.Definition d:KOMEProgressionNativeAuthority.definitions().values()){
            KOMEFactionCapitalRecord capital=KOMEFactionCapitalService.getCapital(data,d.faction);if(capital==null)continue;
            KOMEProgressionNpcRankRecord r=slot(data,d.faction);
            if(r==null){
                UUID id=UUID.nameUUIDFromBytes(("kome:capital-ruler:v1|"+world.getSeed()+"|"+d.faction).getBytes(StandardCharsets.UTF_8));
                r=new KOMEProgressionNpcRankRecord(id,d.faction,KOMEProgressionNpcRank.KING,"");
                r.rulerInitialized=true;data.progressionNpcRanks.put(id,r);data.markDirty();
            }
            r.lastRuntimeNanos=now;
            LOTREntityNPC live=find(world,r.npcUuid);
            if(live!=null){
                for(Object o:world.loadedEntityList)if(o instanceof LOTREntityNPC&&o!=live&&r.npcUuid.equals(((Entity)o).getUniqueID()))((Entity)o).setDead();
                if(KOMEProgressionEncounterMarker.rulerIncarnation(live)!=r.incarnation||r.respawnRemainingMillis>=0){live.setDead();continue;}
                r.rulerSpawned=true;r.missingChecks=0;
                if(!r.capitalBound&&KOMEKnightCommissionService.loadedAround(world,capital.getDeploymentX(),capital.getDeploymentZ())){
                    KOMEStrategicDeploymentResolver.Validation safe=KOMEStrategicDeploymentResolver.resolveAround(world,capital.getCapitalTileId(),capital.getDeploymentX(),capital.getDeploymentY(),capital.getDeploymentZ(),24,live.width,live.height);
                    if(safe.valid){live.setLocationAndAngles(safe.anchor.x,safe.anchor.y,safe.anchor.z,live.rotationYaw,0);r.capitalBound=true;data.markDirty();}
                }
                KOMEProgressionNpcRef ref=KOMEProgressionNpcRankService.referenceOf(live);
                if(!r.rulerLocation.isSet()||live.getDistanceSq(r.rulerLocation.x,r.rulerLocation.y,r.rulerLocation.z)>16){r.rulerLocation=ref;data.markDirty();}
                applyHome(data,live);continue;
            }
            // Unloaded is inconclusive. Saved last location is the recovery anchor.
            if(r.rulerSpawned){
                if(!r.rulerLocation.isSet()||!KOMEKnightCommissionService.loadedAround(world,r.rulerLocation.x,r.rulerLocation.z)){r.missingChecks=0;continue;}
                if(++r.missingChecks<5)continue;
                r.rulerSpawned=false;data.markDirty();
            }
            if(!r.rulerInitialized)continue; // Legacy explicit Kings are adopted on entity load.
            if(canRespawn(r))spawn(data,world,r,capital,d);
        }
    }
    private static LOTREntityNPC find(World world,UUID id){
        for(Object o:world.loadedEntityList)if(o instanceof LOTREntityNPC&&((Entity)o).isEntityAlive()&&id.equals(((Entity)o).getUniqueID()))return (LOTREntityNPC)o;
        return null;
    }
    private static boolean spawn(KOMEWorldData data,World world,KOMEProgressionNpcRankRecord r,KOMEFactionCapitalRecord c,KOMEProgressionNativeAuthority.Definition d){
        if(c.getDeploymentDimensionId()!=world.provider.dimensionId||!KOMEKnightCommissionService.loadedAround(world,c.getDeploymentX(),c.getDeploymentZ()))return false;
        LOTREntityNPC npc=npcFactory.create(world,d);if(npc==null||!KOMEProgressionFactionResolver.matches(d.faction,npc.getFaction())||!KOMEProgressionLords.isStandingTrialLiegeCandidate(npc))return false;
        KOMEStrategicDeploymentResolver.Validation safe=KOMEStrategicDeploymentResolver.resolveAround(world,c.getCapitalTileId(),c.getDeploymentX(),c.getDeploymentY(),c.getDeploymentZ(),24,npc.width,npc.height);
        if(!safe.valid)return false;
        float adultWidth=npc.width,adultHeight=npc.height;
        npc.setLocationAndAngles(safe.anchor.x,safe.anchor.y,safe.anchor.z,world.rand.nextFloat()*360,0);
        UUID temporary=npc.getUniqueID();npc.onArtificalSpawn();npc.setupNPCName();
        data.progressionNpcRanks.remove(temporary);
        if(npc.familyInfo!=null&&npc.isChild())npc.familyInfo.setAge(0);
        npc.setShouldTraderRespawn(false);
        safe=KOMEStrategicDeploymentResolver.resolveAround(world,c.getCapitalTileId(),c.getDeploymentX(),c.getDeploymentY(),c.getDeploymentZ(),24,Math.max(adultWidth,npc.width),Math.max(adultHeight,npc.height));
        if(!safe.valid){discardMount(npc);return false;}
        NBTTagCompound tag=new NBTTagCompound();npc.writeToNBT(tag);
        tag.setLong("UUIDMost",r.npcUuid.getMostSignificantBits());tag.setLong("UUIDLeast",r.npcUuid.getLeastSignificantBits());npc.readFromNBT(tag);
        int previous=r.incarnation;r.incarnation++;r.respawnRemainingMillis=-1;
        if(r.displayName.isEmpty())r.displayName=npc.getNPCName();
        if(npc.familyInfo!=null)npc.familyInfo.setName(r.displayName);
        KOMEProgressionEncounterMarker.stampRuler(npc,r.incarnation);
        npc.setLocationAndAngles(safe.anchor.x,safe.anchor.y,safe.anchor.z,world.rand.nextFloat()*360,0);
        if(npc.ridingEntity!=null)npc.ridingEntity.setLocationAndAngles(safe.anchor.x,safe.anchor.y,safe.anchor.z,npc.rotationYaw,0);
        KOMEProgressionNativeAuthority.applyName(npc,r);
        applyHome(data,npc);
        if(!world.spawnEntityInWorld(npc)){discardMount(npc);r.incarnation=previous;r.rulerSpawned=false;r.respawnRemainingMillis=0;data.markDirty();return false;}
        r.rulerSpawned=true;r.capitalBound=true;r.rulerLocation=KOMEProgressionNpcRankService.referenceOf(npc);data.markDirty();return true;
    }
    private static void discardMount(LOTREntityNPC npc){Entity mount=npc.ridingEntity;if(mount!=null){npc.mountEntity(null);mount.setDead();}}
    public static List<KOMEVisualMarker> markers(KOMEWorldData data){
        List<KOMEVisualMarker> markers=new ArrayList<KOMEVisualMarker>();if(data==null)return markers;
        for(KOMEProgressionNpcRankRecord r:data.progressionNpcRanks.values())if(r.rank==KOMEProgressionNpcRank.KING&&slot(data,r.factionKey)==r&&r.respawnRemainingMillis<0){
            KOMEFactionCapitalRecord c=KOMEFactionCapitalService.getCapital(data,r.factionKey);if(c==null)continue;
            KOMEProgressionNpcRef ref=r.rulerLocation;
            KOMEProgressionNativeAuthority.Definition d=KOMEProgressionNativeAuthority.definition(r.factionKey);
            if(d==null)continue;
            markers.add(new KOMEVisualMarker(KOMEVisualMarker.Role.RULER,r.npcUuid.toString(),r.rulerInitialized&&!r.displayName.isEmpty()?KOMEProgressionNativeAuthority.name(r.factionKey,r.rank,r.displayName):d.rulerTitle,"",
                c.getDeploymentDimensionId(),ref.isSet()?ref.x:c.getDeploymentX(),ref.isSet()?ref.y:c.getDeploymentY(),ref.isSet()?ref.z:c.getDeploymentZ(),false));
        }
        return markers;
    }
}
