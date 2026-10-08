package kome.common.data;

import lotr.common.entity.npc.LOTREntityNPC;
import net.minecraft.nbt.NBTTagCompound;

/** A reversible lease of LOTR's supported teleportAutomatically flag, saved on the NPC. */
public final class KOMEProgressionEscortFollowing {
    static final String ORIGINAL="KOMEProgressionEscortTeleport";
    private KOMEProgressionEscortFollowing() {}
    public static boolean interact(net.minecraft.entity.player.EntityPlayerMP player,LOTREntityNPC npc){
        if(player==null||npc==null||player.worldObj!=npc.worldObj||player.getDistanceSqToEntity(npc)>64)return false;
        KOMEWorldData world=KOMEWorldData.get(npc.worldObj);
        if(!KOMESerfKnightEscortService.isActiveEscort(world,npc)||!player.getUniqueID().equals(npc.hiredNPCInfo.getHiringPlayerUUID()))return false;
        KOMEProgressionEncounterMarker.Marker marker=KOMEProgressionEncounterMarker.read(npc);
        if(marker==null)return false;
        KOMEPlayerProgression p=world.progressions.get(marker.owner);
        KOMEKnightCommission a=p==null?null:p.getLordship().assignment()!=null?p.getLordship().assignment().objective:p.getKnightService().assignment();
        if(!KOMEProgressionEncounterMarker.ESCORT.equals(marker.kind)&&(a==null||a.type!=KOMEKnightCommission.Type.DANGEROUS_ESCORT
                ||a.protectedActor()==null||!a.protectedActor().id.equals(npc.getUniqueID().toString())))return false;
        disable(npc);
        if(player.isSneaking()&&!npc.hiredNPCInfo.isHalted()){
            npc.hiredNPCInfo.halt();npc.getNavigator().clearPathEntity();KOMEProgressionNpcSpeech.say(player,npc,"I shall wait here. Speak to me when you are ready to go on.");
        }else{
            npc.hiredNPCInfo.ready();npc.hiredNPCInfo.setGuardMode(false);npc.setAttackTarget(null);
            npc.getNavigator().clearPathEntity();npc.getNavigator().tryMoveToEntityLiving(player,1D);
            KOMEProgressionNpcSpeech.say(player,npc,"Lead on. I will follow you.");
        }
        return true;
    }
    public static void guide(KOMEWorldData world,LOTREntityNPC npc){
        if(!KOMESerfKnightEscortService.isActiveEscort(world,npc))return;
        KOMEProgressionEncounterMarker.Marker marker=KOMEProgressionEncounterMarker.read(npc);
        KOMEPlayerProgression p=marker==null?null:world.progressions.get(marker.owner);
        KOMEKnightCommission a=p==null?null:p.getLordship().assignment()!=null?p.getLordship().assignment().objective:p.getKnightService().assignment();
        if(marker==null||!KOMEProgressionEncounterMarker.ESCORT.equals(marker.kind)
                &&(a==null||a.type!=KOMEKnightCommission.Type.DANGEROUS_ESCORT||a.protectedActor()==null
                ||!a.protectedActor().id.equals(npc.getUniqueID().toString())))return;
        disable(npc);
        net.minecraft.entity.player.EntityPlayer owner=npc.hiredNPCInfo.getHiringPlayer();
        if(npc.getAttackTarget()!=null&&(npc.isCivilianNPC()||owner==null||owner.getDistanceSqToEntity(npc.getAttackTarget())>16*16)){
            npc.setAttackTarget(null);npc.getNavigator().clearPathEntity();
        }
    }
    public static void disable(LOTREntityNPC npc) {
        if(npc==null||npc.hiredNPCInfo==null)return;
        NBTTagCompound tag=npc.getEntityData();
        if(!tag.hasKey(ORIGINAL))tag.setBoolean(ORIGINAL,npc.hiredNPCInfo.teleportAutomatically);
        npc.hiredNPCInfo.teleportAutomatically=false;
    }
    public static void restore(LOTREntityNPC npc) {
        if(npc==null||npc.hiredNPCInfo==null)return;
        NBTTagCompound tag=npc.getEntityData();
        if(tag.hasKey(ORIGINAL)){npc.hiredNPCInfo.teleportAutomatically=tag.getBoolean(ORIGINAL);tag.removeTag(ORIGINAL);}
    }
    public static void reconcile(KOMEWorldData world,LOTREntityNPC npc) {
        KOMEProgressionEncounterMarker.Marker marker=KOMEProgressionEncounterMarker.read(npc);
        boolean escort=false;
        if(marker!=null&&KOMESerfKnightEscortService.isActiveEscort(world,npc)) {
            KOMEPlayerProgression p=world.progressions.get(marker.owner);
            if(KOMEProgressionEncounterMarker.ESCORT.equals(marker.kind))escort=true;
            else if(p!=null){
                KOMEKnightCommission a=p.getLordship().assignment()!=null?p.getLordship().assignment().objective:p.getKnightService().assignment();
                KOMEKnightCommission.Actor charge=a==null?null:a.protectedActor();
                escort=a!=null&&a.type==KOMEKnightCommission.Type.DANGEROUS_ESCORT
                    &&a.stage==KOMEKnightCommission.Stage.ACTIVE&&charge!=null&&charge.id.equals(npc.getUniqueID().toString());
            }
        }
        if(escort)disable(npc);else restore(npc);
    }
}
