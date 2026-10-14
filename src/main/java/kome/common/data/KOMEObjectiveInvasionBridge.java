package kome.common.data;

import java.util.*;
import lotr.common.entity.LOTREntityInvasionSpawner;
import lotr.common.entity.npc.*;
import lotr.common.world.spawning.LOTRInvasions;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.*;
import net.minecraft.world.World;
import static kome.common.data.KOMEKnightCommission.*;

/** A narrow binding adapter around the real v36.15 invasion controller. Native code owns
 * composition, spawn pacing, kill progress, reversion, bonuses and final victory. */
public final class KOMEObjectiveInvasionBridge {
    private KOMEObjectiveInvasionBridge(){}
    static boolean safeComposition(LOTRInvasions type){
        if(type==null||type.invasionMobs==null||type.invasionMobs.isEmpty())return false;
        for(Object value:type.invasionMobs){
            String unit=((LOTRInvasions.InvasionSpawnEntry)value).getEntityClass().getSimpleName().toLowerCase(Locale.ROOT);
            // These compositions can damage blocks or use integrated mounts with a separate lifecycle.
            for(String unsafe:new String[]{"troll","mumak","entityent","bomber","sapper","siege","nazgul"})
                if(unit.contains(unsafe))return false;
        }
        return true;
    }
    static void tick(EntityPlayerMP player,KOMEPlayerProgression p,KOMELordshipTrial t){
        KOMEKnightCommission a=t.objective;KOMEWorldData data=KOMEWorldData.get(player.worldObj);
        if(t.ready()||a.stage!=Stage.ACTIVE||t.playerDied)return;
        if(!a.encounterCreated){
            if(player.worldObj.getTotalWorldTime()<a.activationRetryAt||!a.atSite(player.dimension,player.posX,player.posZ,40)
                    ||!KOMEKnightCommissionService.loadedAround(player.worldObj,a.x,a.z))return;
            if(!activate(player,t)){retry(player,t,"The invasion could not be prepared safely. Return to your Prince for reassignment, or try approaching again.");return;}
        }
        Entity entity=KOMEKnightCommissionService.findLoaded(player.worldObj,t.invasionId);
        if(entity instanceof LOTREntityInvasionSpawner&&!entity.isDead){
            LOTREntityInvasionSpawner controller=(LOTREntityInvasionSpawner)entity;
            t.invasionSize=controller.getInvasionSize();t.invasionRemaining=Math.max(0,Math.round(controller.getInvasionHealthStatus()*t.invasionSize));
            t.controllerMissingTicks=0;
        }else if(a.atSite(player.dimension,player.posX,player.posZ,96)&&KOMEKnightCommissionService.loadedAround(player.worldObj,a.x,a.z)){
            // Never recreate a lost controller with its old remaining counter: that could manufacture victory.
            t.controllerMissingTicks+=20;if(t.controllerMissingTicks>=1200)retry(player,t,"The invasion controller is unavailable. Return to your Prince for reassignment.");
        }
        for(Actor actor:a.actors)if(!actor.dead){Entity npc=KOMEKnightCommissionService.findLoaded(player.worldObj,actor.id);if(npc!=null){actor.x=npc.posX;actor.y=npc.posY;actor.z=npc.posZ;}}
        data.markDirty();
    }
    static boolean activate(EntityPlayerMP player,KOMELordshipTrial t){
        World world=player.worldObj;KOMEKnightCommission a=t.objective;
        LOTRInvasions type=LOTRInvasions.forName(t.invasionType);
        lotr.common.fac.LOTRFaction defender=KOMEProgressionFactionResolver.resolve(a.faction);
        if(!safeComposition(type)||!KOMEProgressionRegionalEnemies.choices(world,defender,a.x,a.z).contains(type)
                ||world.difficultySetting==net.minecraft.world.EnumDifficulty.PEACEFUL||!lotr.common.LOTRMod.canSpawnMobs(world))return false;
        double[] site=KOMEKnightCommissionService.safeSite(world,(int)a.x,(int)a.z);
        if(site==null||!KOMEProgressionEncounterSites.openGround(world,site))return false;
        LOTREntityInvasionSpawner controller=new LOTREntityInvasionSpawner(world);
        controller.setInvasionType(type);controller.setLocationAndAngles(site[0],site[1],site[2],0,0);
        if(!controller.canInvasionSpawnHere())return false;
        a.y=site[1];t.invasionId=controller.getUniqueID().toString();
        KOMEProgressionEncounterMarker.mark(controller,KOMELordshipTrialService.MARKER,player.getUniqueID(),a.token);
        KOMEWorldData.get(world).markDirty();
        if(!world.spawnEntityInWorld(controller)){t.invasionId="";return false;}
        a.encounterCreated=true;a.activationPending=false;t.invasionEnded=false;
        // The native default is 30-70. A 30-life invasion is serious without choosing its maximum.
        controller.selectAppropriateBonusFactions();controller.startInvasion(player,30);t.invasionSize=t.invasionRemaining=30;
        KOMEProgressionNpcRoles.syncPlayer(KOMEWorldData.get(world),player.getUniqueID());return true;
    }
    /** Called before a native-spawned NPC joins the world (its native invasion ID is already set). */
    public static void bind(KOMEWorldData data,LOTREntityNPC npc){
        if(!npc.isInvasionSpawned())return;
        for(Map.Entry<UUID,KOMEPlayerProgression> row:data.progressions.entrySet()){
            KOMELordshipTrial t=row.getValue().getLordship().assignment();
            if(t==null||!t.nativeInvasion||!t.invasionId.equals(npc.getInvasionID().toString())||t.objective.stage!=Stage.ACTIVE)continue;
            KOMEKnightCommission a=t.objective;
            for(Actor actor:a.actors)if(actor.id.equals(npc.getUniqueID().toString()))return;
            if(!KOMEProgressionFactionResolver.matches(a.enemyFaction,npc.getFaction()))return;
            // Discard dead records before bounding the active list. Native progress is separate.
            a.actors.removeIf(actor->actor.role==Role.ENEMY&&actor.dead);
            if(a.actors.size()>=128)return;
            a.actors.add(new Actor(npc.getUniqueID().toString(),npc.getClass().getName(),Role.ENEMY,npc.posX,npc.posY,npc.posZ));
            KOMEProgressionEncounterMarker.mark(npc,KOMELordshipTrialService.MARKER,row.getKey(),a.token);
            KOMEProgressionNpcRoles.syncPlayer(data,row.getKey());data.markDirty();return;
        }
    }
    public static void ended(LOTREntityInvasionSpawner controller,boolean victory){
        if(controller.worldObj.isRemote)return;
        KOMEWorldData data=KOMEWorldData.get(controller.worldObj);
        for(Map.Entry<UUID,KOMEPlayerProgression> row:data.progressions.entrySet()){
            KOMELordshipTrial t=row.getValue().getLordship().assignment();
            if(t==null||!t.nativeInvasion||t.invasionEnded||!t.invasionId.equals(controller.getUniqueID().toString())||t.objective.stage!=Stage.ACTIVE)continue;
            t.invasionEnded=true;EntityPlayer owner=controller.worldObj.func_152378_a(row.getKey());
            if(!t.objective.encounterCreated||controller.getInvasionSize()<=0){retry(controller.worldObj,row.getKey(),t);return;}
            if(victory&&!t.playerDied&&t.objective.participated&&(owner==null||owner.isEntityAlive()
                    &&owner.dimension==t.objective.dimension&&owner.getDistanceSqToEntity(controller)<=96*96)){
                t.nativeVictory=true;t.invasionRemaining=0;t.objective.threatResolved=true;t.objective.stage=Stage.READY_TO_REPORT;t.finishedAt=controller.worldObj.getTotalWorldTime();
                data.markDirty();if(owner instanceof EntityPlayerMP)KOMEProgressionAutoCompleter.syncPlayer((EntityPlayerMP)owner,row.getValue());
            }else if(!victory)retry(controller.worldObj,row.getKey(),t);
            else KOMELordshipTrialService.fail(data,controller.worldObj,row.getKey(),t,"participation");
            data.markDirty();return;
        }
    }
    static void npcDeath(KOMEWorldData data,UUID owner,KOMELordshipTrial t,String id){
        if(t.authority.entityUuid.equals(id)){t.authority=KOMEProgressionNpcRef.EMPTY;data.markDirty();}
        for(Actor actor:t.objective.actors)if(actor.id.equals(id)){actor.dead=true;data.markDirty();return;}
    }
    public static void playerDeath(EntityPlayer player){
        if(player==null||player.worldObj.isRemote)return;
        KOMEWorldData data=KOMEWorldData.get(player.worldObj);KOMEPlayerProgression p=data.getProgression(player.getUniqueID());
        KOMELordshipTrial t=p.getLordship().assignment();
        if(t==null||t.objective.stage!=Stage.ACTIVE)return;
        t.playerDied=true;KOMELordshipTrialService.fail(data,player.worldObj,player.getUniqueID(),t,"player_death");
    }
    static void retry(EntityPlayerMP player,KOMELordshipTrial t,String message){
        retry(player.worldObj,player.getUniqueID(),t);
        KOMEProgressionAutoCompleter.syncPlayer(player,KOMEWorldData.get(player.worldObj).getProgression(player.getUniqueID()));
    }
    private static void retry(World world,UUID owner,KOMELordshipTrial t){
        cleanupController(world,t);
        for(Actor actor:new ArrayList<Actor>(t.objective.actors))if(actor.role==Role.ENEMY){
            Entity enemy=KOMEKnightCommissionService.findLoaded(world,actor.id);if(enemy!=null)enemy.setDead();
        }
        t.objective.actors.removeIf(actor->actor.role==Role.ENEMY);
        t.invasionId="";t.invasionEnded=false;t.controllerMissingTicks=0;t.objective.encounterCreated=false;
        t.objective.participated=false;t.objective.activationPending=true;t.objective.activationRetryAt=world.getTotalWorldTime()+200;
        t.failureReason="technical";KOMEProgressionNpcRoles.syncPlayer(KOMEWorldData.get(world),owner);KOMEWorldData.get(world).markDirty();
    }
    static void cleanupController(World world,KOMELordshipTrial t){
        if(!t.nativeInvasion||t.invasionId.isEmpty())return;
        Entity entity=KOMEKnightCommissionService.findLoaded(world,t.invasionId);
        if(entity instanceof LOTREntityInvasionSpawner)entity.setDead();
    }
    public static void reconcileController(KOMEWorldData data,LOTREntityInvasionSpawner controller){
        KOMEProgressionEncounterMarker.Marker marker=KOMEProgressionEncounterMarker.read(controller);if(marker==null||!KOMELordshipTrialService.MARKER.equals(marker.kind))return;
        KOMEPlayerProgression p=data.progressions.get(marker.owner);KOMELordshipTrial t=p==null?null:p.getLordship().assignment();
        if(t==null||!t.nativeInvasion||t.objective.stage!=Stage.ACTIVE||!t.invasionId.equals(controller.getUniqueID().toString())||!t.objective.token.equals(marker.token))controller.setDead();
    }
    static boolean nativeInvader(KOMEWorldData data,LOTREntityNPC npc){
        KOMEProgressionEncounterMarker.Marker marker=KOMEProgressionEncounterMarker.read(npc);
        if(marker==null||!KOMELordshipTrialService.MARKER.equals(marker.kind)||!npc.isInvasionSpawned())return false;
        KOMEPlayerProgression p=data.progressions.get(marker.owner);KOMELordshipTrial t=p==null?null:p.getLordship().assignment();
        if(t==null||!t.nativeInvasion||!t.objective.token.equals(marker.token)||!t.invasionId.equals(npc.getInvasionID().toString()))return false;
        for(Actor actor:t.objective.actors)if(actor.role==Role.ENEMY&&actor.id.equals(npc.getUniqueID().toString()))return true;
        return false;
    }
}
