package kome.common.data;

import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.fac.LOTRFaction;
import lotr.common.world.biome.LOTRBiome;
import lotr.common.world.spawning.LOTRInvasions;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;

/** Incidental native threats: no objective token, lease, kill credit or required enemy marker. */
public final class KOMEProgressionEscortJourney {
    static final int WARNING_DISTANCE=24,WARNING_TICKS=400,AMBUSH_INTERVAL=1200,HOSTILE_CAP=4;
    private static final String KEY="KOMEEscortJourney",AMBUSH="KOMEEscortAmbush";
    private KOMEProgressionEscortJourney() {}
    static boolean shouldWarn(double distanceSq,long now,long previous){return distanceSq>=WARNING_DISTANCE*WARNING_DISTANCE&&now-previous>=WARNING_TICKS;}
    static double chance(boolean friendly,double traveled,int hostiles){return hostiles>0||traveled<128?0:friendly?0.06:0.18;}
    public static void tick(EntityPlayerMP player,LOTREntityNPC traveler){
        if(player==null||traveler.worldObj.isRemote||traveler.ticksExisted%20!=0||player.worldObj!=traveler.worldObj||!traveler.isEntityAlive())return;
        long now=traveler.worldObj.getTotalWorldTime();NBTTagCompound root=traveler.getEntityData();
        if(!root.hasKey(KEY,10)){NBTTagCompound n=new NBTTagCompound();n.setLong("LastAmbush",now);n.setLong("LastWarning",now-WARNING_TICKS);n.setDouble("X",traveler.posX);n.setDouble("Z",traveler.posZ);root.setTag(KEY,n);}
        NBTTagCompound n=root.getCompoundTag(KEY);double dx=traveler.posX-n.getDouble("X"),dz=traveler.posZ-n.getDouble("Z");
        n.setDouble("Travel",Math.min(3000,n.getDouble("Travel")+Math.min(16,Math.sqrt(dx*dx+dz*dz))));n.setDouble("X",traveler.posX);n.setDouble("Z",traveler.posZ);
        double distance=player.getDistanceSqToEntity(traveler);
        if(shouldWarn(distance,now,n.getLong("LastWarning"))){n.setLong("LastWarning",now);KOMEProgressionNpcSpeech.say(player,traveler,KOMEProgressionDialogue.warning(traveler,traveler.worldObj.rand.nextInt(3)));}
        if(now<n.getLong("LastAmbush")){n.setLong("LastAmbush",now);return;}
        if(now-n.getLong("LastAmbush")<AMBUSH_INTERVAL||distance>48*48||traveler.hiredNPCInfo.isHalted()||traveler.getAttackTarget()!=null)return;
        int nearby=0;LOTRFaction faction=traveler.getFaction();
        for(Object o:traveler.worldObj.getEntitiesWithinAABB(LOTREntityNPC.class,traveler.boundingBox.expand(64,32,64))){LOTREntityNPC npc=(LOTREntityNPC)o;if(npc.isEntityAlive()&&(KOMESerfKnightDefenseService.hostile(faction,npc.getFaction())||npc.getEntityData().getBoolean(AMBUSH)))nearby++;}
        if(nearby>=HOSTILE_CAP)return;
        net.minecraft.world.biome.BiomeGenBase base=traveler.worldObj.getWorldChunkManager().getBiomeGenAt((int)traveler.posX,(int)traveler.posZ);
        if(!(base instanceof LOTRBiome)||((LOTRBiome)base).isWateryBiome())return;
        boolean friendly=KOMEKnightCommissionLocations.territory(traveler.worldObj,faction,(LOTRBiome)base,(int)traveler.posX,(int)traveler.posZ);
        n.setLong("LastAmbush",now);
        if(traveler.worldObj.rand.nextDouble()>=chance(friendly,n.getDouble("Travel"),nearby))return;
        java.util.List<LOTRInvasions> forces=KOMEProgressionRegionalEnemies.choices(traveler.worldObj,faction,traveler.posX,traveler.posZ);if(forces.isEmpty())return;
        LOTRInvasions force=forces.get(traveler.worldObj.rand.nextInt(forces.size()));
        for(int i=0;i<Math.min(2,HOSTILE_CAP-nearby);i++){
            LOTRInvasions.InvasionSpawnEntry entry=force.invasionMobs.get(traveler.worldObj.rand.nextInt(force.invasionMobs.size()));
            LOTREntityNPC enemy=KOMEKnightCommissionService.construct(traveler.worldObj,entry.getEntityClass().getName());
            if(enemy==null||enemy.getFaction()!=force.invasionFaction||enemy.isCivilianNPC()||enemy.bossInfo!=null||enemy instanceof lotr.common.entity.npc.LOTRUnitTradeable||enemy instanceof lotr.common.entity.npc.LOTRNPCMount)continue;
            double[] site=KOMEProgressionEncounterSites.attacker(traveler.worldObj,traveler.posX,traveler.posZ,KOMEProgressionNpcRankService.referenceOf(traveler),traveler.getUniqueID().toString()+now,i);
            if(site==null||player.getDistanceSq(site[0],site[1],site[2])<24*24||!KOMEProgressionRegionalEnemies.choices(traveler.worldObj,faction,site[0],site[2]).contains(force))continue;
            enemy.spawnRidingHorse=false;enemy.setLocationAndAngles(site[0],site[1],site[2],0,0);enemy.onSpawnWithEgg(null);
            if(enemy.isChild()||enemy.hiredNPCInfo==null||enemy.hiredNPCInfo.isActive||enemy.ridingEntity!=null||!enemy.getCanSpawnHere())continue;
            enemy.getEntityData().setBoolean(AMBUSH,true);
            if(traveler.worldObj.spawnEntityInWorld(enemy))enemy.setAttackTarget(traveler,true);
        }
    }
    public static void release(LOTREntityNPC npc){npc.getEntityData().removeTag(KEY);}
}
