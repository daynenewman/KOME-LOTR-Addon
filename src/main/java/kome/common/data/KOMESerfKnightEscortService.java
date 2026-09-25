package kome.common.data;

import kome.common.KOMEReflection;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.entity.npc.LOTRHiredNPCInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;

/** Escort uses LOTR's native hired-NPC follow behaviour; this class owns only assignment binding and success checks. */
public final class KOMESerfKnightEscortService {
    public static final int MIN_ESCORT_DISTANCE = 256;
    private static final String TARGET="EscortTarget", ORIGIN_DIM="EscortOriginDim", ORIGIN_X="EscortOriginX", ORIGIN_Z="EscortOriginZ";
    private KOMESerfKnightEscortService() {}

    /** Activates once from the Liege Service route by binding a nearby independent faction NPC to this assignment. */
    public static boolean activate(EntityPlayerMP player, KOMEPlayerProgression progression, LOTREntityNPC liege) {
        if(player==null||progression==null||liege==null)return false;
        KOMESerfKnightProgression state=progression.getSerfKnightProgression(); KOMESerfKnightTrialAssignment assignment=state.getTrialAssignment();
        if(assignment==null||!"escort".equals(assignment.trialId)||assignment.stage!=KOMESerfKnightTrialAssignment.Stage.ASSIGNED)return false;
        NBTTagCompound data=(NBTTagCompound)assignment.data.copy(); if(data.hasKey(TARGET,10))return false;
        LOTREntityNPC target=findCharge(player,state,liege); if(target==null)return false;
        target.hiredNPCInfo.isActive=true; target.hiredNPCInfo.setHiringPlayer(player); target.hiredNPCInfo.setTask(LOTRHiredNPCInfo.Task.WARRIOR); target.hiredNPCInfo.ready();
        data=createEncounterData(KOMEProgressionNpcRankService.referenceOf(target),target.worldObj.provider.dimensionId,target.posX,target.posZ);
        state.updateTrialAssignment(assignment.withStage(KOMESerfKnightTrialAssignment.Stage.ACTIVE,data));
        KOMEWorldData world=KOMEWorldData.get(player.worldObj);KOMEProgressionNpcRoles.syncPlayer(world,player.getUniqueID());world.markDirty(); KOMEProgressionAutoCompleter.syncPlayer(player,progression);
        KOMEProgressionNpcSpeech.say(player,target,assignment.storyVariant%2==0?"I have been asked to travel under your protection. Lead on.":"The road is not safe alone. I will follow your lead."); return true;
    }

    /** Loaded-only reconciliation: absence means an unloaded chunk, never a silent failure or respawn. */
    public static void tickPlayer(EntityPlayerMP player) {
        if(player==null)return; KOMEWorldData world=KOMEWorldData.get(player.worldObj); KOMEPlayerProgression progression=world.getProgression(KOMEReflection.getEntityUUID(player)); KOMESerfKnightProgression state=progression.getSerfKnightProgression(); KOMESerfKnightTrialAssignment assignment=state.getTrialAssignment();
        if(assignment==null||!"escort".equals(assignment.trialId)||assignment.stage!=KOMESerfKnightTrialAssignment.Stage.ACTIVE||state.isTrialCompleted())return;
        KOMEProgressionNpcRef targetRef=target(assignment); if(!targetRef.isSet())return;
        Entity entity=findLoaded(player,targetRef.entityUuid); if(entity==null)return;
        if(!(entity instanceof LOTREntityNPC)||!entity.isEntityAlive()||!player.getUniqueID().equals(((LOTREntityNPC)entity).hiredNPCInfo.getHiringPlayerUUID())){fail(state,assignment,world);return;}
        LOTREntityNPC target=(LOTREntityNPC)entity; if(target.worldObj.provider.dimensionId!=assignment.data.getInteger(ORIGIN_DIM))return;
        double dx=target.posX-assignment.data.getDouble(ORIGIN_X), dz=target.posZ-assignment.data.getDouble(ORIGIN_Z);
        if(hasReachedDestination(dx,dz)&&player.getDistanceSqToEntity(target)<=256D){
            if(KOMESerfKnightService.markTrialObjectiveComplete(state).success){KOMEProgressionNpcRoles.syncPlayer(world,player.getUniqueID());world.markDirty();KOMEProgressionAutoCompleter.syncPlayer(player,progression);target.hiredNPCInfo.dismissUnit(false);KOMEProgressionNpcSpeech.say(player,target,"We have come safely through. You have my thanks.");}
        }
    }
    public static void handleTargetDeath(KOMEWorldData world, String uuid) {
        if(world==null||uuid==null)return; for(java.util.Map.Entry<java.util.UUID,KOMEPlayerProgression> row:world.progressions.entrySet()){KOMESerfKnightProgression state=row.getValue().getSerfKnightProgression();KOMESerfKnightTrialAssignment a=state.getTrialAssignment();if(a!=null&&"escort".equals(a.trialId)&&!state.isTrialCompleted()&&uuid.equals(target(a).entityUuid)){fail(state,a,world);KOMEProgressionNpcRoles.syncPlayer(world,row.getKey());}}
    }
    /** Leaving the liege ends only this temporary hiring relationship; it never removes the NPC. */
    public static void cleanup(EntityPlayerMP player,KOMESerfKnightTrialAssignment assignment){if(player==null||assignment==null||!"escort".equals(assignment.trialId))return;Entity entity=findLoaded(player,target(assignment).entityUuid);if(entity instanceof LOTREntityNPC){LOTREntityNPC npc=(LOTREntityNPC)entity;if(npc.hiredNPCInfo!=null&&npc.hiredNPCInfo.isActive&&player.getUniqueID().equals(npc.hiredNPCInfo.getHiringPlayerUUID()))npc.hiredNPCInfo.dismissUnit(false);}}
    private static void fail(KOMESerfKnightProgression state,KOMESerfKnightTrialAssignment assignment,KOMEWorldData world){state.updateTrialAssignment(assignment.withStage(KOMESerfKnightTrialAssignment.Stage.FAILED,null));KOMEProgressionNpcRoles.rebuild(world);world.markDirty();}
    static NBTTagCompound createEncounterData(KOMEProgressionNpcRef target,int dimension,double x,double z){NBTTagCompound data=new NBTTagCompound();data.setTag(TARGET,target.writeToNBT());data.setInteger(ORIGIN_DIM,dimension);data.setDouble(ORIGIN_X,x);data.setDouble(ORIGIN_Z,z);return data;}
    static boolean hasReachedDestination(double horizontalX,double horizontalZ){return horizontalX*horizontalX+horizontalZ*horizontalZ>=MIN_ESCORT_DISTANCE*MIN_ESCORT_DISTANCE;}
    private static KOMEProgressionNpcRef target(KOMESerfKnightTrialAssignment assignment){return assignment.data.hasKey(TARGET,10)?KOMEProgressionNpcRef.readFromNBT(assignment.data.getCompoundTag(TARGET)):KOMEProgressionNpcRef.EMPTY;}
    private static Entity findLoaded(EntityPlayerMP player,String uuid){for(Object object:player.worldObj.loadedEntityList)if(object instanceof Entity&&uuid.equals(KOMEReflection.getEntityUUID((Entity)object)))return (Entity)object;return null;}
    private static LOTREntityNPC findCharge(EntityPlayerMP player,KOMESerfKnightProgression state,LOTREntityNPC liege){LOTRFactionLoop:for(Object object:player.worldObj.loadedEntityList)if(object instanceof LOTREntityNPC){LOTREntityNPC npc=(LOTREntityNPC)object;if(!npc.isEntityAlive()||npc.isChild()||player.getDistanceSqToEntity(npc)>2304D||npc==liege||state.getSerfdomMaster().hasSameIdentity(KOMEProgressionNpcRankService.referenceOf(npc))||npc.hiredNPCInfo==null||npc.hiredNPCInfo.isActive||!KOMEProgressionNpcRankService.isValidFactionNpc(npc)||npc.getFaction()!=liege.getFaction())continue LOTRFactionLoop;return npc;}return null;}
}
