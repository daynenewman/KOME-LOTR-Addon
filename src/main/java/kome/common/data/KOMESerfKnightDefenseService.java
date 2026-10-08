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
    static final String ACTIVATED="DefenseActivated", OBJECTIVE="DefenseObjective", ENEMIES="DefenseEnemies", DEAD="DefenseDead", ENEMY_FACTION="DefenseEnemyFaction", PARTICIPATED="DefenseParticipated";
    static final int ATTACKERS=2, MIN_OBJECTIVE_DISTANCE_SQ=144, MAX_OBJECTIVE_DISTANCE_SQ=16384, MIN_SPAWN_DISTANCE=48, MAX_SPAWN_DISTANCE=56;
    private KOMESerfKnightDefenseService() { }

    /** Selects an existing independent ally and records every entity identity before spawning. */
    public static boolean activate(EntityPlayerMP player,KOMEPlayerProgression progression,LOTREntityNPC liege) {
        if(player==null||progression==null||liege==null||player.worldObj.isRemote)return false;
        KOMESerfKnightProgression state=progression.getSerfKnightProgression(); KOMESerfKnightTrialAssignment assignment=state.getTrialAssignment();
        if(!isDefense(assignment)||assignment.stage!=KOMESerfKnightTrialAssignment.Stage.ASSIGNED)return false;
        NBTTagCompound data=(NBTTagCompound)assignment.data.copy(); if(data.getBoolean(ACTIVATED))return false;
        LOTREntityNPC objective=findObjective(player,state,liege); if(objective==null)return false;
        List<LOTRInvasions> forces=KOMEProgressionRegionalEnemies.choices(player.worldObj,liege.getFaction(),objective.posX,objective.posZ); LOTRInvasions invasion=forces.isEmpty()?null:forces.get(Math.floorMod(assignment.assignmentToken.hashCode(),forces.size())); if(invasion==null||invasion.invasionMobs==null||invasion.invasionMobs.isEmpty())return false;
        List<LOTREntityNPC> attackers=new ArrayList<LOTREntityNPC>();
        for(int i=0;i<ATTACKERS;i++){LOTREntityNPC attacker=createNativeAttacker(player.worldObj,invasion,i,assignment.assignmentToken);if(attacker==null)return false;attackers.add(attacker);}
        data.setBoolean(ACTIVATED,true); data.setBoolean(PARTICIPATED,false); data.setTag(OBJECTIVE,KOMEProgressionNpcRankService.referenceOf(objective).writeToNBT()); data.setString(ENEMY_FACTION,invasion.invasionFaction.codeName());
        NBTTagList ids=new NBTTagList(); for(int i=0;i<attackers.size();i++){LOTREntityNPC attacker=attackers.get(i);if(!placeAttacker(attacker,objective,i,assignment))return false;NBTTagCompound id=new NBTTagCompound();id.setString("Id",attacker.getUniqueID().toString());id.setString("Class",attacker.getClass().getName());id.setDouble("X",attacker.posX);id.setDouble("Y",attacker.posY);id.setDouble("Z",attacker.posZ);ids.appendTag(id);} data.setTag(ENEMIES,ids); data.setTag(DEAD,new NBTTagList());
        state.updateTrialAssignment(assignment.withStage(KOMESerfKnightTrialAssignment.Stage.ACTIVE,data)); KOMEWorldData worldData=KOMEWorldData.get(player.worldObj);KOMEProgressionNpcRoles.syncPlayer(worldData,player.getUniqueID()); worldData.markDirty();
        for(int i=0;i<attackers.size();i++) { LOTREntityNPC attacker=attackers.get(i); placeAttacker(attacker,objective,i,assignment); attacker.setAttackTarget(objective,true); KOMEProgressionEncounterMarker.mark(attacker,KOMEProgressionEncounterMarker.DEFENSE,player.getUniqueID(),assignment.assignmentToken); if(!player.worldObj.spawnEntityInWorld(attacker)){fail(state,worldData,player.worldObj);cleanup(player,state.getTrialAssignment());return false;} KOMEProgressionEncounterMarker.mark(attacker,KOMEProgressionEncounterMarker.DEFENSE,player.getUniqueID(),assignment.assignmentToken); }
        return true;
    }

    public static void tickPlayer(EntityPlayerMP player) {
        if(player==null||player.worldObj.isRemote)return; KOMEWorldData world=KOMEWorldData.get(player.worldObj); KOMEPlayerProgression progression=world.getProgression(player.getUniqueID()); KOMESerfKnightProgression state=progression.getSerfKnightProgression(); KOMESerfKnightTrialAssignment assignment=state.getTrialAssignment();
        if(!isDefense(assignment)||assignment.stage!=KOMESerfKnightTrialAssignment.Stage.ACTIVE)return;
        Entity entity=findLoaded(player.worldObj,objective(assignment).entityUuid);
        if(entity==null){
            KOMEProgressionNpcRef ref=objective(assignment);NBTTagCompound evidence=(NBTTagCompound)assignment.data.copy();
            int missing=ref.isSet()&&player.getDistanceSq(ref.x,ref.y,ref.z)<=96*96&&KOMEKnightCommissionService.loadedAround(player.worldObj,ref.x,ref.z)
                ?evidence.getInteger("DefenseObjectiveMissingTicks")+20:0;
            evidence.setInteger("DefenseObjectiveMissingTicks",missing);state.updateTrialAssignment(assignment.withStage(assignment.stage,evidence));world.markDirty();
            if(missing>=1200){fail(state,world,player.worldObj);KOMEProgressionAutoCompleter.syncPlayer(player,progression);}return;
        }
        if(assignment.data.getInteger("DefenseObjectiveMissingTicks")>0){
            NBTTagCompound evidence=(NBTTagCompound)assignment.data.copy();evidence.removeTag("DefenseObjectiveMissingTicks");
            state.updateTrialAssignment(assignment.withStage(assignment.stage,evidence));assignment=state.getTrialAssignment();world.markDirty();
        }
        if(!(entity instanceof LOTREntityNPC)||!entity.isEntityAlive()||!objective(assignment).hasSameIdentity(KOMEProgressionNpcRankService.referenceOf((LOTREntityNPC)entity))){fail(state,world,player.worldObj);return;}
        for(String id:enemyIds(assignment)){Entity attacker=findLoaded(player.worldObj,id);if(attacker!=null&&(!(attacker instanceof LOTREntityNPC)||!matchesBinding((LOTREntityNPC)attacker,assignment))){fail(state,world,player.worldObj);return;}}
        if(!recoverMissing(player,world,state,assignment,(LOTREntityNPC)entity)){fail(state,world,player.worldObj);return;} assignment=state.getTrialAssignment();
        if(allDead(assignment)&&!hasParticipation(assignment)){fail(state,world,player.worldObj);return;}
        if(allDead(assignment)&&KOMESerfKnightService.markTrialObjectiveComplete(state).success){KOMEProgressionServiceRewards.trial(player,state);KOMEProgressionNpcRoles.syncPlayer(world,player.getUniqueID());world.markDirty();KOMEProgressionAutoCompleter.syncPlayer(player,progression);KOMEProgressionNpcSpeech.say(player,(LOTREntityNPC)entity,"Our people stand because you stood with them. You have met this trial well.");}
    }


    /** Records that the owning player actually joined the defense by damaging one assigned attacker. */
    public static boolean notePlayerParticipation(KOMEWorldData world,UUID playerId,String attackerUuid) {
        if(world==null||playerId==null||attackerUuid==null)return false;
        KOMEPlayerProgression progression=world.progressions.get(playerId); if(progression==null)return false;
        KOMESerfKnightProgression state=progression.getSerfKnightProgression(); KOMESerfKnightTrialAssignment assignment=state.getTrialAssignment();
        if(!isDefense(assignment)||assignment.stage!=KOMESerfKnightTrialAssignment.Stage.ACTIVE||!enemyIds(assignment).contains(attackerUuid)||hasParticipation(assignment))return false;
        NBTTagCompound data=(NBTTagCompound)assignment.data.copy(); data.setBoolean(PARTICIPATED,true); state.updateTrialAssignment(assignment.withStage(KOMESerfKnightTrialAssignment.Stage.ACTIVE,data)); world.markDirty(); return true;
    }

    static boolean hasParticipation(KOMESerfKnightTrialAssignment assignment){return isDefense(assignment)&&assignment.data.getBoolean(PARTICIPATED);}

    private static boolean recoverMissing(EntityPlayerMP player,KOMEWorldData world,KOMESerfKnightProgression state,
            KOMESerfKnightTrialAssignment assignment,LOTREntityNPC objective){
        NBTTagCompound data=(NBTTagCompound)assignment.data.copy();NBTTagList entries=data.getTagList(ENEMIES,10);
        for(int i=0;i<entries.tagCount();i++){
            NBTTagCompound entry=entries.getCompoundTagAt(i);String id=entry.getString("Id");
            if(deadIds(assignment).contains(id))continue; // A legitimate defeat never creates another slot.
            Entity live=findLoaded(player.worldObj,id);
            if(live!=null){entry.setDouble("X",live.posX);entry.setDouble("Y",live.posY);entry.setDouble("Z",live.posZ);entry.setInteger("MissingChecks",0);continue;}
            double x=entry.hasKey("X")?entry.getDouble("X"):objective.posX,z=entry.hasKey("Z")?entry.getDouble("Z"):objective.posZ;
            if(player.getDistanceSq(x,objective.posY,z)>96*96||!KOMEKnightCommissionService.loadedAround(player.worldObj,x,z)){entry.setInteger("MissingChecks",0);continue;}
            int missing=entry.getInteger("MissingChecks")+1;entry.setInteger("MissingChecks",missing);if(missing<5)continue;
            if(entry.getInteger("Recoveries")>=3)return false;
            String type=entry.getString("Class");
            if(type.isEmpty()){LOTRInvasions invasion=LOTRInvasions.forName(data.getString(ENEMY_FACTION));if(invasion==null)return false;type=invasion.invasionMobs.get(i%invasion.invasionMobs.size()).getEntityClass().getName();}
            LOTREntityNPC replacement=KOMEKnightCommissionService.construct(player.worldObj,type);
            if(replacement==null||!matchesBinding(replacement,assignment))return false;
            double[] site=KOMEKnightCommissionService.safeSite(player.worldObj,(int)x,(int)z);if(site==null)return false;
            replacement.onArtificalSpawn();replacement.setLocationAndAngles(site[0],site[1],site[2],0,0);
            replacement.setAttackTarget(objective,true);
            KOMEProgressionEncounterMarker.mark(replacement,KOMEProgressionEncounterMarker.DEFENSE,player.getUniqueID(),assignment.assignmentToken);
            entry.setString("Id",replacement.getUniqueID().toString());entry.setString("Class",type);
            entry.setDouble("X",site[0]);entry.setDouble("Y",site[1]);entry.setDouble("Z",site[2]);
            entry.setInteger("MissingChecks",0);entry.setInteger("Recoveries",entry.getInteger("Recoveries")+1);
            state.updateTrialAssignment(assignment.withStage(KOMESerfKnightTrialAssignment.Stage.ACTIVE,data));
            KOMEProgressionNpcRoles.syncPlayer(world,player.getUniqueID());world.markDirty();
            if(!player.worldObj.spawnEntityInWorld(replacement))return false;
        }
        state.updateTrialAssignment(assignment.withStage(KOMESerfKnightTrialAssignment.Stage.ACTIVE,data));world.markDirty();return true;
    }

    /** Death is proof; a missing loaded/unloaded attacker is never inferred to be defeated. */
    public static void handleNpcDeath(KOMEWorldData world,String uuid){handleNpcDeath(world,uuid,null);}
    public static void handleNpcDeath(KOMEWorldData world,String uuid,World liveWorld) {
        if(world==null||uuid==null)return; for(KOMEPlayerProgression progression:world.progressions.values()) { KOMESerfKnightProgression state=progression.getSerfKnightProgression(); KOMESerfKnightTrialAssignment assignment=state.getTrialAssignment(); if(!isDefense(assignment)||assignment.stage!=KOMESerfKnightTrialAssignment.Stage.ACTIVE)continue;
            if(uuid.equals(objective(assignment).entityUuid)){fail(state,world,liveWorld);continue;} if(!enemyIds(assignment).contains(uuid)||deadIds(assignment).contains(uuid))continue;
            NBTTagCompound data=(NBTTagCompound)assignment.data.copy(); NBTTagList dead=data.getTagList(DEAD,10); NBTTagCompound entry=new NBTTagCompound(); entry.setString("Id",uuid); dead.appendTag(entry); data.setTag(DEAD,dead); state.updateTrialAssignment(assignment.withStage(KOMESerfKnightTrialAssignment.Stage.ACTIVE,data)); world.markDirty();
        }
    }

    /** Defense attackers are KOME-created encounter entities and are removed when the encounter ends. */
    public static void cleanup(EntityPlayerMP player,KOMESerfKnightTrialAssignment assignment){
        if(player==null)return;
        cleanup(player.worldObj,player.getUniqueID(),assignment);
    }
    static void cleanup(World world,UUID owner,KOMESerfKnightTrialAssignment assignment){
        if(world==null||owner==null||!isDefense(assignment))return;
        for(String id:enemyIds(assignment)){
            Entity entity=findLoaded(world,id);
            if(entity instanceof LOTREntityNPC){
                LOTREntityNPC npc=(LOTREntityNPC)entity;
                KOMEProgressionEncounterMarker.Marker marker=KOMEProgressionEncounterMarker.read(npc);
                if(marker!=null
                        &&KOMEProgressionEncounterMarker.DEFENSE.equals(marker.kind)
                        &&owner.equals(marker.owner)
                        &&assignment.assignmentToken.equals(marker.token)){
                    npc.setDead();
                }
            }
        }
    }

    public static void reconcileLoadedNpc(KOMEWorldData world,LOTREntityNPC npc){
        KOMEProgressionEncounterMarker.Marker marker=KOMEProgressionEncounterMarker.read(npc);
        if(marker==null||!KOMEProgressionEncounterMarker.DEFENSE.equals(marker.kind))return;

        boolean active=false;
        if(world!=null){
            KOMEPlayerProgression progression=world.progressions.get(marker.owner);
            if(progression!=null){
                KOMESerfKnightTrialAssignment assignment=
                    progression.getSerfKnightProgression().getTrialAssignment();

                active=isDefense(assignment)
                    &&assignment.stage==KOMESerfKnightTrialAssignment.Stage.ACTIVE
                    &&marker.token.equals(assignment.assignmentToken)
                    &&enemyIds(assignment).contains(npc.getUniqueID().toString())
                    &&!deadIds(assignment).contains(npc.getUniqueID().toString())
                    &&matchesBinding(npc,assignment);
            }
        }

        if(active)return;

        npc.setDead();
    }
    static boolean isDefense(KOMESerfKnightTrialAssignment assignment){return assignment!=null&&"defense".equals(assignment.trialId);}
    static boolean hostile(LOTRFaction defender,LOTRFaction attacker){return defender!=null&&attacker!=null&&defender!=attacker&&!defender.isGoodRelation(attacker)&&!attacker.isGoodRelation(defender)&&(defender.isBadRelation(attacker)||attacker.isBadRelation(defender));}
    static LOTRInvasions chooseHostileInvasion(LOTRFaction defender,String token){List<LOTRInvasions> choices=new ArrayList<LOTRInvasions>();for(LOTRInvasions invasion:LOTRInvasions.values())if(invasion.invasionFaction!=null&&hostile(defender,invasion.invasionFaction)&&invasion.invasionMobs!=null&&!invasion.invasionMobs.isEmpty())choices.add(invasion);return choices.isEmpty()?null:choices.get(Math.floorMod(token==null?0:token.hashCode(),choices.size()));}
    private static LOTREntityNPC createNativeAttacker(World world,LOTRInvasions invasion,int index,String token){
        for(int offset=0;offset<invasion.invasionMobs.size();offset++)try{
            Object entry=invasion.invasionMobs.get(Math.floorMod((token==null?0:token.hashCode())+index+offset,invasion.invasionMobs.size()));
            Class type=((LOTRInvasions.InvasionSpawnEntry)entry).getEntityClass();LOTREntityNPC npc=(LOTREntityNPC)type.getConstructor(World.class).newInstance(world);
            if(npc.getFaction()!=invasion.invasionFaction||npc.isChild()||npc.bossInfo!=null)continue;
            npc.onArtificalSpawn();if(npc.getFaction()==invasion.invasionFaction&&!npc.isChild())return npc;
        }catch(ReflectiveOperationException invalid){ }
        return null;
    }
    private static LOTREntityNPC findObjective(EntityPlayerMP player,KOMESerfKnightProgression state,LOTREntityNPC liege){LOTRFaction faction=liege.getFaction();for(Object value:player.worldObj.loadedEntityList)if(value instanceof LOTREntityNPC){LOTREntityNPC npc=(LOTREntityNPC)value;double distance=liege.getDistanceSqToEntity(npc);if(npc!=liege&&!state.getSerfdomMaster().hasSameIdentity(KOMEProgressionNpcRankService.referenceOf(npc))&&KOMEProgressionNpcRoles.availableForNewRole(KOMEWorldData.get(player.worldObj),npc.getUniqueID())&&npc.isEntityAlive()&&!npc.isChild()&&KOMEProgressionNpcRankService.isValidFactionNpc(npc)&&npc.getFaction()==faction&&distance>=MIN_OBJECTIVE_DISTANCE_SQ&&distance<=MAX_OBJECTIVE_DISTANCE_SQ&&npc.hiredNPCInfo!=null&&!npc.hiredNPCInfo.isActive)return npc;}return null;}
    private static boolean placeAttacker(LOTREntityNPC attacker,LOTREntityNPC objective,int index,KOMESerfKnightTrialAssignment assignment){double[] site=KOMEProgressionEncounterSites.attacker(attacker.worldObj,objective.posX,objective.posZ,assignment.liege,assignment.assignmentToken,index);if(site==null)return false;attacker.setLocationAndAngles(site[0],site[1],site[2],0F,0F);return true;}
    private static KOMEProgressionNpcRef objective(KOMESerfKnightTrialAssignment assignment){return assignment.data.hasKey(OBJECTIVE,10)?KOMEProgressionNpcRef.readFromNBT(assignment.data.getCompoundTag(OBJECTIVE)):KOMEProgressionNpcRef.EMPTY;}
    private static boolean matchesBinding(LOTREntityNPC attacker,KOMESerfKnightTrialAssignment assignment){return attacker!=null&&assignment!=null&&attacker.getFaction()!=null&&assignment.data.getString(ENEMY_FACTION).equals(attacker.getFaction().codeName());}
    static List<String> enemyIds(KOMESerfKnightTrialAssignment assignment){return ids(assignment.data.getTagList(ENEMIES,10));} static List<String> deadIds(KOMESerfKnightTrialAssignment assignment){return ids(assignment.data.getTagList(DEAD,10));} private static List<String> ids(NBTTagList list){List<String> result=new ArrayList<String>();for(int i=0;i<list.tagCount();i++){String id=list.getCompoundTagAt(i).getString("Id");try{UUID.fromString(id);result.add(id);}catch(Exception ignored){}}return result;}
    static boolean allDead(KOMESerfKnightTrialAssignment assignment){List<String> enemies=enemyIds(assignment),dead=deadIds(assignment);return !enemies.isEmpty()&&dead.containsAll(enemies);}
    static int trackerTotalAttackers(KOMESerfKnightTrialAssignment assignment){return isDefense(assignment)?enemyIds(assignment).size():0;}
    static int trackerDefeatedAttackers(KOMESerfKnightTrialAssignment assignment){return isDefense(assignment)?deadIds(assignment).size():0;}
    private static Entity findLoaded(World world,String uuid){if(world==null||uuid==null)return null;for(Object value:world.loadedEntityList)if(value instanceof Entity&&uuid.equals(((Entity)value).getUniqueID().toString()))return (Entity)value;return null;}
    private static void fail(KOMESerfKnightProgression state,KOMEWorldData world,World liveWorld){KOMEProgressionEncounterCleanup.failTrial(world,liveWorld,state);}
}
