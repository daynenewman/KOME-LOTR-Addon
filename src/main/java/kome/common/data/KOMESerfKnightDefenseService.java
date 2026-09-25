package kome.common.data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.fac.LOTRFaction;
import lotr.common.world.spawning.LOTRInvasions;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;

/** Coordinates one modest native LOTR NPC attack; LOTR owns all targeting, navigation, gear and combat. */
public final class KOMESerfKnightDefenseService {
    static final String ACTIVATED="DefenseActivated", OBJECTIVE="DefenseObjective", ENEMIES="DefenseEnemies", DEAD="DefenseDead", ENEMY_FACTION="DefenseEnemyFaction";
    static final int ATTACKERS=2, MIN_OBJECTIVE_DISTANCE_SQ=144, MAX_OBJECTIVE_DISTANCE_SQ=16384, MIN_SPAWN_DISTANCE=14, MAX_SPAWN_DISTANCE=20;
    private KOMESerfKnightDefenseService() { }

    /** Selects an existing independent ally and records every entity identity before spawning. */
    public static boolean activate(EntityPlayerMP player,KOMEPlayerProgression progression,LOTREntityNPC liege) {
        if(player==null||progression==null||liege==null||player.worldObj.isRemote)return false;
        KOMESerfKnightProgression state=progression.getSerfKnightProgression(); KOMESerfKnightTrialAssignment assignment=state.getTrialAssignment();
        if(!isDefense(assignment)||assignment.stage!=KOMESerfKnightTrialAssignment.Stage.ASSIGNED)return false;
        NBTTagCompound data=(NBTTagCompound)assignment.data.copy(); if(data.getBoolean(ACTIVATED))return false;
        LOTREntityNPC objective=findObjective(player,state,liege); if(objective==null)return false;
        LOTRInvasions invasion=chooseHostileInvasion(liege.getFaction(),assignment.assignmentToken); if(invasion==null||invasion.invasionMobs==null||invasion.invasionMobs.isEmpty())return false;
        List<LOTREntityNPC> attackers=new ArrayList<LOTREntityNPC>();
        for(int i=0;i<ATTACKERS;i++){LOTREntityNPC attacker=createNativeAttacker(player.worldObj,invasion,i,assignment.assignmentToken);if(attacker==null)return false;attackers.add(attacker);}
        data.setBoolean(ACTIVATED,true); data.setTag(OBJECTIVE,KOMEProgressionNpcRankService.referenceOf(objective).writeToNBT()); data.setString(ENEMY_FACTION,invasion.invasionFaction.codeName());
        NBTTagList ids=new NBTTagList(); for(LOTREntityNPC attacker:attackers){NBTTagCompound id=new NBTTagCompound();id.setString("Id",attacker.getUniqueID().toString());ids.appendTag(id);} data.setTag(ENEMIES,ids); data.setTag(DEAD,new NBTTagList());
        state.updateTrialAssignment(assignment.withStage(KOMESerfKnightTrialAssignment.Stage.ACTIVE,data)); KOMEWorldData worldData=KOMEWorldData.get(player.worldObj);KOMEProgressionNpcRoles.syncPlayer(worldData,player.getUniqueID()); worldData.markDirty();
        for(int i=0;i<attackers.size();i++) { LOTREntityNPC attacker=attackers.get(i); placeAttacker(attacker,objective,i,assignment); attacker.setAttackTarget(objective,true); if(!player.worldObj.spawnEntityInWorld(attacker)){fail(state,worldData);return false;} }
        return true;
    }

    public static void tickPlayer(EntityPlayerMP player) {
        if(player==null||player.worldObj.isRemote)return; KOMEWorldData world=KOMEWorldData.get(player.worldObj); KOMEPlayerProgression progression=world.getProgression(player.getUniqueID()); KOMESerfKnightProgression state=progression.getSerfKnightProgression(); KOMESerfKnightTrialAssignment assignment=state.getTrialAssignment();
        if(!isDefense(assignment)||assignment.stage!=KOMESerfKnightTrialAssignment.Stage.ACTIVE)return;
        Entity entity=findLoaded(player.worldObj,objective(assignment).entityUuid); if(entity==null)return; // unloaded is deliberately inconclusive
        if(!(entity instanceof LOTREntityNPC)||!entity.isEntityAlive()||!objective(assignment).hasSameIdentity(KOMEProgressionNpcRankService.referenceOf((LOTREntityNPC)entity))){fail(state,world);return;}
        for(String id:enemyIds(assignment)){Entity attacker=findLoaded(player.worldObj,id);if(attacker!=null&&(!(attacker instanceof LOTREntityNPC)||!matchesBinding((LOTREntityNPC)attacker,assignment))){fail(state,world);return;}}
        if(allDead(assignment)&&KOMESerfKnightService.markTrialObjectiveComplete(state).success){KOMEProgressionNpcRoles.syncPlayer(world,player.getUniqueID());world.markDirty();KOMEProgressionAutoCompleter.syncPlayer(player,progression);KOMEProgressionNpcSpeech.say(player,(LOTREntityNPC)entity,"Our people stand because you stood with them. You have met this trial well.");}
    }

    /** Death is proof; a missing loaded/unloaded attacker is never inferred to be defeated. */
    public static void handleNpcDeath(KOMEWorldData world,String uuid) {
        if(world==null||uuid==null)return; for(KOMEPlayerProgression progression:world.progressions.values()) { KOMESerfKnightProgression state=progression.getSerfKnightProgression(); KOMESerfKnightTrialAssignment assignment=state.getTrialAssignment(); if(!isDefense(assignment)||assignment.stage!=KOMESerfKnightTrialAssignment.Stage.ACTIVE)continue;
            if(uuid.equals(objective(assignment).entityUuid)){fail(state,world);continue;} if(!enemyIds(assignment).contains(uuid)||deadIds(assignment).contains(uuid))continue;
            NBTTagCompound data=(NBTTagCompound)assignment.data.copy(); NBTTagList dead=data.getTagList(DEAD,10); NBTTagCompound entry=new NBTTagCompound(); entry.setString("Id",uuid); dead.appendTag(entry); data.setTag(DEAD,dead); state.updateTrialAssignment(assignment.withStage(KOMESerfKnightTrialAssignment.Stage.ACTIVE,data)); world.markDirty();
        }
    }
    /** Stop coordinating known attackers on abandonment without deleting ordinary LOTR NPCs. */
    public static void cleanup(EntityPlayerMP player,KOMESerfKnightTrialAssignment assignment){if(player==null||!isDefense(assignment))return;for(String id:enemyIds(assignment)){Entity entity=findLoaded(player.worldObj,id);if(entity instanceof LOTREntityNPC&&matchesBinding((LOTREntityNPC)entity,assignment))((LOTREntityNPC)entity).setAttackTarget(null,false);}}

    static boolean isDefense(KOMESerfKnightTrialAssignment assignment){return assignment!=null&&"defense".equals(assignment.trialId);}
    static boolean hostile(LOTRFaction defender,LOTRFaction attacker){return defender!=null&&attacker!=null&&(defender.isBadRelation(attacker)||attacker.isBadRelation(defender));}
    static LOTRInvasions chooseHostileInvasion(LOTRFaction defender,String token){List<LOTRInvasions> choices=new ArrayList<LOTRInvasions>();for(LOTRInvasions invasion:LOTRInvasions.values())if(invasion.invasionFaction!=null&&hostile(defender,invasion.invasionFaction)&&invasion.invasionMobs!=null&&!invasion.invasionMobs.isEmpty())choices.add(invasion);return choices.isEmpty()?null:choices.get(Math.floorMod(token==null?0:token.hashCode(),choices.size()));}
    private static LOTREntityNPC createNativeAttacker(World world,LOTRInvasions invasion,int index,String token){try{Object entry=invasion.invasionMobs.get(Math.floorMod((token==null?0:token.hashCode())+index,invasion.invasionMobs.size()));Class type=((LOTRInvasions.InvasionSpawnEntry)entry).getEntityClass();LOTREntityNPC npc=(LOTREntityNPC)type.getConstructor(World.class).newInstance(world);npc.onArtificalSpawn();npc.func_110163_bv();return npc;}catch(Exception ignored){return null;}}
    private static LOTREntityNPC findObjective(EntityPlayerMP player,KOMESerfKnightProgression state,LOTREntityNPC liege){LOTRFaction faction=liege.getFaction();for(Object value:player.worldObj.loadedEntityList)if(value instanceof LOTREntityNPC){LOTREntityNPC npc=(LOTREntityNPC)value;double distance=liege.getDistanceSqToEntity(npc);if(npc!=liege&&!state.getSerfdomMaster().hasSameIdentity(KOMEProgressionNpcRankService.referenceOf(npc))&&npc.isEntityAlive()&&!npc.isChild()&&KOMEProgressionNpcRankService.isValidFactionNpc(npc)&&npc.getFaction()==faction&&distance>=MIN_OBJECTIVE_DISTANCE_SQ&&distance<=MAX_OBJECTIVE_DISTANCE_SQ&&npc.hiredNPCInfo!=null&&!npc.hiredNPCInfo.isActive)return npc;}return null;}
    private static void placeAttacker(LOTREntityNPC attacker,LOTREntityNPC objective,int index,KOMESerfKnightTrialAssignment assignment){double angle=((assignment.assignmentToken.hashCode()+index*211)&0x7fffffff)%6283/1000D;double distance=MIN_SPAWN_DISTANCE+(index%(MAX_SPAWN_DISTANCE-MIN_SPAWN_DISTANCE+1));int x=(int)Math.floor(objective.posX+Math.cos(angle)*distance),z=(int)Math.floor(objective.posZ+Math.sin(angle)*distance),y=attacker.worldObj.getTopSolidOrLiquidBlock(x,z);attacker.setLocationAndAngles(x+0.5D,y,z+0.5D,attacker.worldObj.rand.nextFloat()*360F,0F);}
    private static KOMEProgressionNpcRef objective(KOMESerfKnightTrialAssignment assignment){return assignment.data.hasKey(OBJECTIVE,10)?KOMEProgressionNpcRef.readFromNBT(assignment.data.getCompoundTag(OBJECTIVE)):KOMEProgressionNpcRef.EMPTY;}
    private static boolean matchesBinding(LOTREntityNPC attacker,KOMESerfKnightTrialAssignment assignment){return attacker!=null&&assignment!=null&&attacker.getFaction()!=null&&assignment.data.getString(ENEMY_FACTION).equals(attacker.getFaction().codeName());}
    private static List<String> enemyIds(KOMESerfKnightTrialAssignment assignment){return ids(assignment.data.getTagList(ENEMIES,10));} private static List<String> deadIds(KOMESerfKnightTrialAssignment assignment){return ids(assignment.data.getTagList(DEAD,10));} private static List<String> ids(NBTTagList list){List<String> result=new ArrayList<String>();for(int i=0;i<list.tagCount();i++){String id=list.getCompoundTagAt(i).getString("Id");try{UUID.fromString(id);result.add(id);}catch(Exception ignored){}}return result;}
    static boolean allDead(KOMESerfKnightTrialAssignment assignment){List<String> enemies=enemyIds(assignment),dead=deadIds(assignment);return !enemies.isEmpty()&&dead.containsAll(enemies);}
    private static Entity findLoaded(World world,String uuid){if(world==null||uuid==null)return null;for(Object value:world.loadedEntityList)if(value instanceof Entity&&uuid.equals(((Entity)value).getUniqueID().toString()))return (Entity)value;return null;}
    private static void fail(KOMESerfKnightProgression state,KOMEWorldData world){KOMESerfKnightTrialAssignment assignment=state.getTrialAssignment();if(assignment!=null){state.updateTrialAssignment(assignment.withStage(KOMESerfKnightTrialAssignment.Stage.FAILED,null));KOMEProgressionNpcRoles.rebuild(world);world.markDirty();}}
}
