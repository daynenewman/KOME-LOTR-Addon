package kome.common.data;

import java.util.UUID;

import kome.common.KOMEReflection;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.entity.npc.LOTRHiredNPCInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

/** Escort uses LOTR's native hired-NPC follow behaviour; this class owns only assignment binding and success checks. */
public final class KOMESerfKnightEscortService {
    public static final int MIN_ESCORT_DISTANCE = 256;
    private static final String TARGET="EscortTarget", ORIGIN_DIM="EscortOriginDim", ORIGIN_X="EscortOriginX", ORIGIN_Z="EscortOriginZ";
    private KOMESerfKnightEscortService() {}
    public static final int DESTINATION_RADIUS=32;
    static final String DEST_X="EscortDestinationX",DEST_Z="EscortDestinationZ",DEST_NAME="EscortDestinationName";
    public static boolean prepareDestination(KOMESerfKnightProgression state,World world) {
        KOMESerfKnightTrialAssignment a=state.getTrialAssignment();
        if(a==null||!"escort".equals(a.trialId))return true;
        if(hasDestination(a)&&a.data.hasKey(KOMEProgressionDestinations.PROOF,10))
            return KOMEProgressionDestinations.valid(world,a.data.getCompoundTag(KOMEProgressionDestinations.PROOF));
        KOMEProgressionNpcRef origin=a.liege;
        lotr.common.fac.LOTRFaction faction=KOMEProgressionFactionResolver.resolve(a.factionKey);
        if(world==null||world.provider==null||world.provider.dimensionId!=origin.dimension||faction==null)return false;
        KOMEKnightCommissionLocations.Destination destination=KOMEProgressionDestinations.find(world,origin,MIN_ESCORT_DISTANCE,3000);
        if(destination==null)return false;
        NBTTagCompound data=(NBTTagCompound)a.data.copy();data.setDouble(DEST_X,destination.x);data.setDouble(DEST_Z,destination.z);
        data.setString(DEST_NAME,destination.name);data.setInteger(ORIGIN_DIM,origin.dimension);
        data.setTag(KOMEProgressionDestinations.PROOF,destination.proof);
        state.updateTrialAssignment(a.withStage(a.stage,data));return true;
    }
    public static boolean hasDestination(KOMESerfKnightTrialAssignment a) {
        return a!=null&&a.data.hasKey(DEST_X,99)&&a.data.hasKey(DEST_Z,99)&&!a.data.getString(DEST_NAME).isEmpty()
            &&KOMEKnightCommissionLocations.coordinate(a.data.getDouble(DEST_X))&&KOMEKnightCommissionLocations.coordinate(a.data.getDouble(DEST_Z));
    }
    public static String destinationName(KOMESerfKnightTrialAssignment a){return hasDestination(a)&&a.data.hasKey(KOMEProgressionDestinations.PROOF,10)?a.data.getString(DEST_NAME):"the marked ground";}
    static boolean arrived(KOMESerfKnightTrialAssignment a,int dimension,double x,double z){
        if(!hasDestination(a)||dimension!=a.data.getInteger(ORIGIN_DIM))return false;
        double dx=x-a.data.getDouble(DEST_X),dz=z-a.data.getDouble(DEST_Z);
        return dx*dx+dz*dz<=DESTINATION_RADIUS*DESTINATION_RADIUS;
    }

    /** Activates once from the Liege Service route by binding a nearby independent faction NPC to this assignment. */
    public static boolean activate(EntityPlayerMP player, KOMEPlayerProgression progression, LOTREntityNPC liege) {
        if(player==null||progression==null||liege==null)return false;
        KOMESerfKnightProgression state=progression.getSerfKnightProgression(); KOMESerfKnightTrialAssignment assignment=state.getTrialAssignment();
        if(assignment==null||!"escort".equals(assignment.trialId)||assignment.stage!=KOMESerfKnightTrialAssignment.Stage.ASSIGNED)return false;
        if(!prepareDestination(state,player.worldObj))return false;
        assignment=state.getTrialAssignment();
        NBTTagCompound data=(NBTTagCompound)assignment.data.copy(); if(data.hasKey(TARGET,10))return false;
        LOTREntityNPC target=findCharge(player,state,liege); if(target==null)return false;
        target.hiredNPCInfo.isActive=true; target.hiredNPCInfo.setHiringPlayer(player); target.hiredNPCInfo.setTask(LOTRHiredNPCInfo.Task.WARRIOR); target.hiredNPCInfo.ready();
        NBTTagCompound binding=createEncounterData(KOMEProgressionNpcRankService.referenceOf(target),target.worldObj.provider.dimensionId,target.posX,target.posZ);
        for(Object key:binding.func_150296_c())data.setTag((String)key,binding.getTag((String)key));
        state.updateTrialAssignment(assignment.withStage(KOMESerfKnightTrialAssignment.Stage.ACTIVE,data));
        KOMEProgressionEncounterMarker.mark(target,KOMEProgressionEncounterMarker.ESCORT,player.getUniqueID(),assignment.assignmentToken);
        KOMEProgressionEscortFollowing.disable(target);
        KOMEWorldData world=KOMEWorldData.get(player.worldObj);KOMEProgressionNpcRoles.syncPlayer(world,player.getUniqueID());world.markDirty(); KOMEProgressionAutoCompleter.syncPlayer(player,progression);
        KOMEProgressionNpcSpeech.say(player,target,assignment.storyVariant%2==0?"I have been asked to travel under your protection. Lead on.":"The road is not safe alone. I will follow your lead."); return true;
    }

    /** Loaded-only reconciliation: absence means an unloaded chunk, never a silent failure or respawn. */
    public static void tickPlayer(EntityPlayerMP player) {
        if(player==null)return; KOMEWorldData world=KOMEWorldData.get(player.worldObj); KOMEPlayerProgression progression=world.getProgression(KOMEReflection.getEntityUUID(player)); KOMESerfKnightProgression state=progression.getSerfKnightProgression(); KOMESerfKnightTrialAssignment assignment=state.getTrialAssignment();
        if(assignment==null||!"escort".equals(assignment.trialId)||assignment.stage!=KOMESerfKnightTrialAssignment.Stage.ACTIVE||state.isTrialCompleted())return;
        if(!hasDestination(assignment)||!assignment.data.hasKey(KOMEProgressionDestinations.PROOF,10)){
            if(!prepareDestination(state,player.worldObj)){fail(state,assignment,world,player.worldObj);return;}
            assignment=state.getTrialAssignment();world.markDirty();
        }
        if(!KOMEProgressionDestinations.valid(player.worldObj,assignment.data.getCompoundTag(KOMEProgressionDestinations.PROOF))){
            fail(state,assignment,world,player.worldObj);KOMEProgressionAutoCompleter.syncPlayer(player,progression);return;
        }
        KOMEProgressionNpcRef targetRef=target(assignment); if(!targetRef.isSet()){fail(state,assignment,world,player.worldObj);return;}
        Entity entity=findLoaded(player,targetRef.entityUuid);
        if(entity==null){
            if(KOMEKnightCommissionService.loadedAround(player.worldObj,targetRef.x,targetRef.z)
                    &&player.getDistanceSq(targetRef.x,targetRef.y,targetRef.z)<=96*96){
                NBTTagCompound evidence=(NBTTagCompound)assignment.data.copy();int missing=evidence.getInteger("EscortMissingTicks")+20;
                evidence.setInteger("EscortMissingTicks",missing);state.updateTrialAssignment(assignment.withStage(assignment.stage,evidence));world.markDirty();
                if(missing>=1200){fail(state,assignment,world,player.worldObj);KOMEProgressionAutoCompleter.syncPlayer(player,progression);}
            }else if(assignment.data.getInteger("EscortMissingTicks")>0){
                NBTTagCompound evidence=(NBTTagCompound)assignment.data.copy();evidence.removeTag("EscortMissingTicks");
                state.updateTrialAssignment(assignment.withStage(assignment.stage,evidence));world.markDirty();
            }
            return;
        }
        if(assignment.data.getInteger("EscortMissingTicks")>0){NBTTagCompound evidence=(NBTTagCompound)assignment.data.copy();evidence.removeTag("EscortMissingTicks");state.updateTrialAssignment(assignment.withStage(assignment.stage,evidence));world.markDirty();}
        if(!(entity instanceof LOTREntityNPC)||!entity.isEntityAlive()||!isActiveEscort(world,(LOTREntityNPC)entity)){fail(state,assignment,world,player.worldObj);return;}
        LOTREntityNPC target=(LOTREntityNPC)entity; if(target.worldObj.provider.dimensionId!=assignment.data.getInteger(ORIGIN_DIM))return;
        if(arrived(assignment,target.worldObj.provider.dimensionId,target.posX,target.posZ)&&player.getDistanceSqToEntity(target)<=256D){
            if(KOMESerfKnightService.markTrialObjectiveComplete(state).success){KOMEProgressionServiceRewards.trial(player,state);KOMEProgressionNpcRoles.syncPlayer(world,player.getUniqueID());world.markDirty();KOMEProgressionAutoCompleter.syncPlayer(player,progression);KOMEProgressionEscortFollowing.restore(target);target.hiredNPCInfo.dismissUnit(false);KOMEProgressionEncounterMarker.clear(target);KOMEProgressionNpcSpeech.say(player,target,"We have come safely through. You have my thanks.");}
        }
    }
    public static void handleTargetDeath(KOMEWorldData world,String uuid){handleTargetDeath(world,uuid,null);}
    public static void handleTargetDeath(KOMEWorldData world, String uuid, World liveWorld) {
        if(world==null||uuid==null)return; for(java.util.Map.Entry<java.util.UUID,KOMEPlayerProgression> row:world.progressions.entrySet()){KOMESerfKnightProgression state=row.getValue().getSerfKnightProgression();KOMESerfKnightTrialAssignment a=state.getTrialAssignment();if(a!=null&&"escort".equals(a.trialId)&&!state.isTrialCompleted()&&uuid.equals(target(a).entityUuid)){fail(state,a,world,liveWorld);KOMEProgressionNpcRoles.syncPlayer(world,row.getKey());}}
    }

    /** Leaving the liege ends only this temporary hiring relationship; it never removes the NPC. */
    public static void cleanup(EntityPlayerMP player,KOMESerfKnightTrialAssignment assignment){
        if(player==null)return;
        cleanup(player.worldObj,player.getUniqueID(),assignment);
    }
    static void cleanup(World world,UUID owner,KOMESerfKnightTrialAssignment assignment){
        if(world==null||owner==null||assignment==null||!"escort".equals(assignment.trialId))return;
        Entity entity=findLoaded(world,target(assignment).entityUuid);
        if(entity instanceof LOTREntityNPC){
            LOTREntityNPC npc=(LOTREntityNPC)entity;
            KOMEProgressionEscortFollowing.restore(npc);
            if(npc.hiredNPCInfo!=null&&npc.hiredNPCInfo.isActive&&owner.equals(npc.hiredNPCInfo.getHiringPlayerUUID()))
                npc.hiredNPCInfo.dismissUnit(false);
            KOMEProgressionEncounterMarker.clear(npc);
        }
    }
    public static void reconcileLoadedNpc(KOMEWorldData world,LOTREntityNPC npc){
        KOMEProgressionEscortFollowing.reconcile(world,npc);
        KOMEProgressionEncounterMarker.Marker marker=KOMEProgressionEncounterMarker.read(npc);
        if(marker==null||!KOMEProgressionEncounterMarker.ESCORT.equals(marker.kind))return;

        if(isActiveEscort(world,npc))return;

        if(npc.hiredNPCInfo!=null
                &&npc.hiredNPCInfo.isActive
                &&marker.owner.equals(npc.hiredNPCInfo.getHiringPlayerUUID())){
            npc.hiredNPCInfo.dismissUnit(false);
        }
        KOMEProgressionEncounterMarker.clear(npc);
    }

    /** A saved marker alone cannot exempt an ordinary recruit from permission/accounting. */
    public static boolean isActiveEscort(KOMEWorldData world,LOTREntityNPC npc) {
        if(world!=null&&(KOMELordshipTrialService.temporaryFollower(world,npc)||KOMEKnightCommissionService.activeEscort(world,npc)))return true;
        KOMEProgressionEncounterMarker.Marker marker=KOMEProgressionEncounterMarker.read(npc);
        if(marker==null||!KOMEProgressionEncounterMarker.ESCORT.equals(marker.kind)
                ||npc.hiredNPCInfo==null||!npc.hiredNPCInfo.isActive
                ||!marker.owner.equals(npc.hiredNPCInfo.getHiringPlayerUUID()))return false;
        if(world!=null){
            KOMEPlayerProgression progression=world.progressions.get(marker.owner);
            if(progression!=null){
                KOMESerfKnightProgression state=progression.getSerfKnightProgression();
                KOMESerfKnightTrialAssignment assignment=state.getTrialAssignment();
                return progression.getCanonicalRank()==KOMEProgressionRank.SERF
                    &&!state.isTrialCompleted()&&assignment!=null
                    &&"escort".equals(assignment.trialId)
                    &&assignment.stage==KOMESerfKnightTrialAssignment.Stage.ACTIVE
                    &&marker.token.equals(assignment.assignmentToken)
                    &&npc.getUniqueID().toString().equals(target(assignment).entityUuid)
                    &&assignment.isValidFor(state.getTrialId(),state.getLiege())
                    &&KOMEProgressionFactionResolver.matches(assignment.factionKey,npc.getFaction());
            }
        }

        return false;
    }
    private static void fail(KOMESerfKnightProgression state,KOMESerfKnightTrialAssignment assignment,KOMEWorldData world,World liveWorld){KOMEProgressionEncounterCleanup.failTrial(world,liveWorld,state);}
    static NBTTagCompound createEncounterData(KOMEProgressionNpcRef target,int dimension,double x,double z){NBTTagCompound data=new NBTTagCompound();data.setTag(TARGET,target.writeToNBT());data.setInteger(ORIGIN_DIM,dimension);data.setDouble(ORIGIN_X,x);data.setDouble(ORIGIN_Z,z);return data;}
    static KOMEProgressionNpcRef target(KOMESerfKnightTrialAssignment assignment){return assignment!=null&&assignment.data.hasKey(TARGET,10)?KOMEProgressionNpcRef.readFromNBT(assignment.data.getCompoundTag(TARGET)):KOMEProgressionNpcRef.EMPTY;}
    static boolean sameEntityId(String uuid,java.util.UUID entityUuid){return uuid!=null&&entityUuid!=null&&uuid.equals(entityUuid.toString());}
    private static Entity findLoaded(EntityPlayerMP player,String uuid){return player==null?null:findLoaded(player.worldObj,uuid);}
    private static Entity findLoaded(World world,String uuid){if(world==null||uuid==null)return null;for(Object object:world.loadedEntityList)if(object instanceof Entity&&sameEntityId(uuid,KOMEReflection.getEntityUUID((Entity)object)))return (Entity)object;return null;}
    private static LOTREntityNPC findCharge(EntityPlayerMP player,KOMESerfKnightProgression state,LOTREntityNPC liege){LOTRFactionLoop:for(Object object:player.worldObj.loadedEntityList)if(object instanceof LOTREntityNPC){LOTREntityNPC npc=(LOTREntityNPC)object;if(!npc.isEntityAlive()||npc.isChild()||player.getDistanceSqToEntity(npc)>2304D||npc==liege||KOMEProgressionLords.isStandingTrialLiegeCandidate(npc)||state.getSerfdomMaster().hasSameIdentity(KOMEProgressionNpcRankService.referenceOf(npc))||!KOMEProgressionNpcRoles.availableForNewRole(KOMEWorldData.get(player.worldObj),npc.getUniqueID())||npc.hiredNPCInfo==null||npc.hiredNPCInfo.isActive||!KOMEProgressionNpcRankService.isValidFactionNpc(npc)||npc.getFaction()!=liege.getFaction())continue LOTRFactionLoop;return npc;}return null;}
}
