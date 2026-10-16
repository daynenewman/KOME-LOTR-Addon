package kome.common.data;

import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.fac.LOTRFaction;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
import static kome.common.data.KOMEKnightCommission.*;

/** A small credible reinforcement journey: a proven commander, four physical followers, a return defense. */
final class KOMEKnightHelpService {
    private KOMEKnightHelpService() {}
    static KOMEProgressionNpcRef contact(World world,KOMEKnightCommission a){
        for(Object o:world.loadedEntityList)if(o instanceof LOTREntityNPC){LOTREntityNPC npc=(LOTREntityNPC)o;
            double distance=(npc.posX-a.x)*(npc.posX-a.x)+(npc.posZ-a.z)*(npc.posZ-a.z);
            if(distance>=128*128&&distance<=3000*3000&&!a.liege.entityUuid.equals(npc.getUniqueID().toString())&&npc.isEntityAlive()&&!npc.isChild()
                    &&KOMEProgressionFactionResolver.matches(a.faction,npc.getFaction())&&npc.hiredNPCInfo!=null&&!npc.hiredNPCInfo.isActive&&KOMEProgressionServiceLocation.regional(npc))return KOMEProgressionNpcRankService.referenceOf(npc);
        }return KOMEProgressionNpcRef.EMPTY;
    }
    static boolean interact(EntityPlayerMP player,LOTREntityNPC npc){
        KOMEWorldData data=KOMEWorldData.get(player.worldObj);KOMEPlayerProgression p=data.getProgression(player.getUniqueID());KOMEKnightCommission a=p.getKnightService().assignment();
        if(a==null||a.type!=Type.GO_FOR_HELP||a.stage!=Stage.ACTIVE||npc.worldObj!=player.worldObj||!npc.isEntityAlive()||player.getDistanceSqToEntity(npc)>64
                ||p.getCanonicalRank()!=KOMEProgressionRank.KNIGHT||!a.liege.hasSameIdentity(p.getSerfKnightProgression().getLiege())
                ||!KOMEProgressionFactionResolver.matches(a.faction,lotr.common.LOTRLevelData.getData(player).getPledgeFaction()))return false;
        Actor witness=a.protectedActor();
        if(witness!=null&&witness.id.equals(npc.getUniqueID().toString())&&a.phase==Phase.INVESTIGATE){
            KOMEProgressionEncounterMarker.Marker marker=KOMEProgressionEncounterMarker.read(npc);
            if(marker==null||!a.token.equals(marker.token)||!player.getUniqueID().equals(marker.owner))return false;
            a.phase=Phase.SEEK_HELP;KOMEProgressionNpcSpeech.say(player,npc,"Raiders are gathering beyond the road. Ask "+a.helpCommander.displayName+" at the marked garrison for help, and bring the soldiers here.");data.markDirty();KOMEProgressionAutoCompleter.syncPlayer(player,p);return true;
        }
        if(!a.helpCommander.entityUuid.equals(npc.getUniqueID().toString())||a.phase!=Phase.SEEK_HELP)return false;
        if(!KOMEProgressionServiceLocation.regional(npc)||!KOMEProgressionFactionResolver.matches(a.faction,npc.getFaction())){KOMEKnightCommissionService.technicalRetry(player,a);return true;}
        String soldier=KOMEProgressionTrialSoldiers.resolve(player.worldObj,npc.getFaction());if(soldier==null){KOMEKnightCommissionService.technicalRetry(player,a);return true;}
        a.phase=Phase.RETURN_WITH_HELP;data.markDirty();
        for(int i=0;i<4;i++){
            double[] site=KOMEKnightCommissionService.safeSite(player.worldObj,(int)npc.posX+i*2,(int)npc.posZ+3);
            LOTREntityNPC guard=site==null?null:KOMEKnightCommissionService.spawn(player,a,soldier,Role.GUARD,site,KOMEKnightCommissionService.MARKER);
            if(guard==null){KOMEKnightCommissionService.technicalRetry(player,a);return true;}KOMEKnightFieldService.follow(guard,player);
        }
        KOMEProgressionNpcSpeech.say(player,npc,"Take these four soldiers. Lead them back to the threatened refuge. They follow your road; do not leave them behind.");data.markDirty();KOMEProgressionAutoCompleter.syncPlayer(player,p);return true;
    }
    static boolean tick(EntityPlayerMP player,KOMEKnightCommission a){
        if(a.type!=Type.GO_FOR_HELP)return true;
        if(a.phase==Phase.SEEK_HELP){
            boolean loaded=player.dimension==a.helpCommander.dimension&&player.getDistanceSq(a.helpCommander.x,a.helpCommander.y,a.helpCommander.z)<=96*96&&KOMEKnightCommissionService.loadedAround(player.worldObj,a.helpCommander.x,a.helpCommander.z);
            a.helpMissingTicks=loaded&&KOMEKnightCommissionService.findLoaded(player.worldObj,a.helpCommander.entityUuid)==null?a.helpMissingTicks+20:0;
            return a.helpMissingTicks<1200;
        }
        if(a.phase!=Phase.RETURN_WITH_HELP)return true;
        boolean alive=false,present=false;
        for(Actor actor:a.actors)if(actor.role==Role.GUARD&&!actor.dead){alive=true;Entity entity=KOMEKnightCommissionService.findLoaded(player.worldObj,actor.id);if(entity!=null&&entity.isEntityAlive()&&a.atSite(player.dimension,entity.posX,entity.posZ,32))present=true;
            if(entity==null&&player.dimension==a.dimension&&player.getDistanceSq(actor.x,actor.y,actor.z)<=96*96&&KOMEKnightCommissionService.loadedAround(player.worldObj,actor.x,actor.z)){actor.missingTicks+=20;if(actor.missingTicks>=1200)return false;}
        }
        if(!alive){a.failureReason="The relief party was lost. Return for new orders; earlier service remains honoured.";KOMEKnightCommissionService.fail(KOMEWorldData.get(player.worldObj),player.worldObj,player.getUniqueID(),a);return true;}
        if(!present||!a.atSite(player.dimension,player.posX,player.posZ,40)||!KOMEKnightCommissionService.loadedAround(player.worldObj,a.x,a.z))return true;
        a.phase=Phase.ENGAGE;KOMEWorldData.get(player.worldObj).markDirty();
        Entity target=KOMEKnightFieldService.attackTarget(player,a);
        for(int i=0;i<a.enemyClasses.size();i++){
            double[] site=KOMEProgressionEncounterSites.attacker(player.worldObj,a.x,a.z,a.liege,a.token,i,40,64);
            LOTREntityNPC enemy=site==null?null:KOMEKnightCommissionService.spawn(player,a,a.enemyClasses.get(i),Role.ENEMY,site,KOMEKnightCommissionService.MARKER);
            if(enemy==null)return false;if(target instanceof net.minecraft.entity.EntityLivingBase)enemy.setAttackTarget((net.minecraft.entity.EntityLivingBase)target,true);
        }return true;
    }
}
