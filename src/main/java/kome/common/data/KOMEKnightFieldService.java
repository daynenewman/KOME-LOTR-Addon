package kome.common.data;

import java.util.*;
import lotr.common.fac.LOTRFaction;
import lotr.common.entity.npc.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
import static kome.common.data.KOMEKnightCommission.*;

/** Field profiles and bounded stages of the existing commission, not a separate quest ledger. */
public final class KOMEKnightFieldService {
    private KOMEKnightFieldService() {}
    public static int tier(KOMEKnightServiceRecord record,String faction){return Math.min(3,1+record.qualifyingTypes(faction).size());}
    static int preference(Type type,int tier){
        if(tier==1)return type==Type.BORDER_INCURSION||type==Type.STOLEN_GOODS||type==Type.DANGEROUS_ESCORT||type==Type.SETTLEMENT_DEFENSE?8:0;
        if(tier==2)return type==Type.RESCUE_PATROL||type==Type.PROTECT_LIEGE||type==Type.FIELD_COMMAND||type==Type.GO_FOR_HELP||type==Type.RELIEF||type==Type.DANGEROUS_ESCORT?8:1;
        return type==Type.RAID_CAMP||type==Type.WAR_PLANS||type==Type.NATIVE_INVASION||type==Type.FIELD_COMMAND||type==Type.PROTECT_LIEGE?8:1;
    }
    static void profile(KOMEKnightCommission a,int tier){
        a.tier=Math.max(1,Math.min(3,tier));
        a.complication=a.type!=Type.RELIEF&&a.type!=Type.NATIVE_INVASION&&Math.floorMod(a.token.hashCode(),3)==0?Complication.COUNTERATTACK:Complication.NONE;
    }
    static boolean hostileMission(Type type){return type==Type.RAID_CAMP||type==Type.WAR_PLANS;}
    static boolean escort(Type type){return type==Type.DANGEROUS_ESCORT||type==Type.RESCUE_PATROL;}
    static boolean combat(Type type){return type!=Type.WAR_PLANS&&type!=Type.NATIVE_INVASION;}
    static int enemyCount(KOMEKnightCommission a,Random random){return a.tier==0?5+random.nextInt(6):a.tier==1?3+random.nextInt(2):a.tier==2?5+random.nextInt(2):7+random.nextInt(2);}
    static boolean requiresExtraction(KOMEKnightCommission a){return a.type==Type.RESCUE_PATROL&&a.phase==Phase.EXTRACT;}
    static double goalX(KOMEKnightCommission a){return a.type==Type.GO_FOR_HELP&&a.phase==Phase.SEEK_HELP?a.helpCommander.x:requiresExtraction(a)||a.type==Type.DANGEROUS_ESCORT?a.destinationX:a.type==Type.WAR_PLANS&&a.dispatchStolen||a.phase==Phase.ESCAPE?a.liege.x:a.x;}
    static double goalZ(KOMEKnightCommission a){return a.type==Type.GO_FOR_HELP&&a.phase==Phase.SEEK_HELP?a.helpCommander.z:requiresExtraction(a)||a.type==Type.DANGEROUS_ESCORT?a.destinationZ:a.type==Type.WAR_PLANS&&a.dispatchStolen||a.phase==Phase.ESCAPE?a.liege.z:a.z;}
    static boolean qualifiedCombat(KOMEKnightCommission a){return a.participated&&(a.tier==0||a.combatDamage>=4*a.tier);}

    /** The real Liege remains an ordinary native combatant; only temporary attackers are owned. */
    static boolean liegeAvailable(EntityPlayerMP player,KOMEKnightCommission a){
        Entity entity=KOMEKnightCommissionService.findLoaded(player.worldObj,a.liege.entityUuid);
        return entity instanceof LOTREntityNPC&&KOMECurrentLiege.valid(player,(LOTREntityNPC)entity)
            &&player.getDistanceSqToEntity(entity)<=24*24&&entity.isEntityAlive();
    }
    static boolean liegeSurvives(EntityPlayerMP player,KOMEKnightCommission a){
        Entity entity=KOMEKnightCommissionService.findLoaded(player.worldObj,a.liege.entityUuid);
        return entity instanceof LOTREntityNPC&&entity.isEntityAlive()&&KOMECurrentLiege.valid(player,(LOTREntityNPC)entity)&&player.getDistanceSqToEntity(entity)<=96*96;
    }
    static Entity attackTarget(EntityPlayerMP player,KOMEKnightCommission a){
        if(a.type==Type.PROTECT_LIEGE)return KOMEKnightCommissionService.findLoaded(player.worldObj,a.liege.entityUuid);
        Actor charge=a.protectedActor();
        if(charge!=null&&(a.type==Type.SETTLEMENT_DEFENSE||a.type==Type.RESCUE_PATROL||a.type==Type.RELIEF||a.type==Type.GO_FOR_HELP)){
            Entity found=KOMEKnightCommissionService.findLoaded(player.worldObj,charge.id);if(found!=null)return found;
        }
        return player;
    }
    static void follow(LOTREntityNPC npc,EntityPlayerMP player){
        npc.hiredNPCInfo.isActive=true;npc.hiredNPCInfo.setHiringPlayer(player);npc.hiredNPCInfo.setTask(LOTRHiredNPCInfo.Task.WARRIOR);npc.hiredNPCInfo.ready();
        KOMEProgressionEscortFollowing.disable(npc);
    }
    static boolean startSoldiers(EntityPlayerMP player,KOMEKnightCommission a){
        if(a.type!=Type.FIELD_COMMAND)return true;
        for(Actor actor:a.actors)if(actor.role==Role.GUARD)return true;
        for(int i=0;i<4;i++){
            double[] site=KOMEKnightCommissionService.safeSite(player.worldObj,(int)player.posX+i*2,(int)player.posZ+3);
            LOTREntityNPC npc=site==null?null:KOMEKnightCommissionService.spawn(player,a,a.civilianClass,Role.GUARD,site,KOMEKnightCommissionService.MARKER);
            if(npc==null)return false;follow(npc,player);
        }
        return true;
    }
    static boolean activateProtected(EntityPlayerMP player,KOMEKnightCommission a,double[] site){
        if(a.type!=Type.RESCUE_PATROL)return true;
        LOTREntityNPC survivor=KOMEKnightCommissionService.spawn(player,a,a.civilianClass,Role.BENEFICIARY,site,KOMEKnightCommissionService.MARKER);
        if(survivor==null)return false;
        survivor.setHomeArea((int)site[0],(int)site[1],(int)site[2],8);return true;
    }
    /** A second direction attacks the charge/owner after the first threat is cleared.
     * All actors are reserved by spawn(); partial failure rolls the encounter back, never grants success. */
    static boolean complication(EntityPlayerMP player,KOMEKnightCommission a){
        if(a.tier==0||a.complication!=Complication.COUNTERATTACK||a.complicationTriggered||!a.allEnemiesDead()||!qualifiedCombat(a))return true;
        a.complicationTriggered=true;KOMEWorldData.get(player.worldObj).markDirty();
        Entity target=attackTarget(player,a);if(!(target instanceof net.minecraft.entity.EntityLivingBase))return false;
        for(int i=0;i<2;i++){
            double[] site=KOMEProgressionEncounterSites.attacker(player.worldObj,target.posX,target.posZ,
                a.type==Type.PROTECT_LIEGE?KOMEProgressionNpcRef.EMPTY:a.liege,a.token+"counter",i,24,40);
            LOTREntityNPC npc=site==null?null:KOMEKnightCommissionService.spawn(player,a,a.enemyClasses.get(i%a.enemyClasses.size()),Role.ENEMY,site,KOMEKnightCommissionService.MARKER);
            if(npc==null)return false;npc.setAttackTarget((net.minecraft.entity.EntityLivingBase)target,true);
        }
        player.addChatMessage(new net.minecraft.util.ChatComponentText(a.type==Type.PROTECT_LIEGE?"Another band is closing on your Liege!":"Scouts have circled behind you. Protect your charge and break their pursuit!"));
        return true;
    }
    static void afterCombat(EntityPlayerMP player,KOMEKnightCommission a){
        if(a.type==Type.RESCUE_PATROL&&a.threatResolved&&a.phase!=Phase.EXTRACT){
            Actor survivor=a.protectedActor();Entity entity=survivor==null?null:KOMEKnightCommissionService.findLoaded(player.worldObj,survivor.id);
            if(entity instanceof LOTREntityNPC&&!survivor.dead){a.phase=Phase.EXTRACT;follow((LOTREntityNPC)entity,player);KOMEProgressionProtectedActors.reconcile(KOMEWorldData.get(player.worldObj),(LOTREntityNPC)entity);((LOTREntityNPC)entity).detachHome();KOMEProgressionNpcSpeech.say(player,(LOTREntityNPC)entity,"You found me in time. Lead me to the refuge marked on your map.");}
        }
        if(a.type==Type.RESCUE_PATROL&&a.phase==Phase.EXTRACT){
            Actor survivor=a.protectedActor();Entity entity=survivor==null?null:KOMEKnightCommissionService.findLoaded(player.worldObj,survivor.id);
            if(entity instanceof LOTREntityNPC&&player.getDistanceSqToEntity(entity)<=16*16&&KOMEProgressionDestinations.arrived((LOTREntityNPC)entity,a.destinationProof))a.stage=Stage.READY_TO_REPORT;
        }
    }
    public static boolean follower(KOMEWorldData data,LOTREntityNPC npc){
        KOMEProgressionEncounterMarker.Marker marker=KOMEProgressionEncounterMarker.read(npc);
        if(marker==null||!KOMEKnightCommissionService.MARKER.equals(marker.kind)||npc.hiredNPCInfo==null||!npc.hiredNPCInfo.isActive||!marker.owner.equals(npc.hiredNPCInfo.getHiringPlayerUUID()))return false;
        KOMEPlayerProgression p=data.progressions.get(marker.owner);KOMEKnightCommission a=p==null?null:p.getKnightService().assignment();
        if(a==null||a.stage!=Stage.ACTIVE||!a.token.equals(marker.token)||!a.liege.hasSameIdentity(p.getSerfKnightProgression().getLiege()))return false;
        for(Actor actor:a.actors)if(!actor.dead&&actor.id.equals(npc.getUniqueID().toString())&&KOMEProgressionFactionResolver.matches(a.faction,npc.getFaction()))
            return (a.type==Type.FIELD_COMMAND||a.type==Type.GO_FOR_HELP)&&actor.role==Role.GUARD||a.type==Type.RESCUE_PATROL&&a.phase==Phase.EXTRACT&&actor.role==Role.BENEFICIARY;
        return false;
    }
}
