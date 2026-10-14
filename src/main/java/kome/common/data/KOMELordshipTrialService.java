package kome.common.data;

import java.util.*;
import lotr.common.LOTRLevelData;
import lotr.common.entity.npc.*;
import lotr.common.fac.LOTRFaction;
import lotr.common.world.spawning.LOTRInvasions;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
import static kome.common.data.KOMEKnightCommission.*;

/** Authoritative temporary command and promotion. No army records or permanent hiring are created. */
public final class KOMELordshipTrialService {
    public static final int REQUIRED_ALIGNMENT=2000, REQUIRED_SERVICE=3;
    static final String MARKER="lordship_trial";
    private KOMELordshipTrialService() {}

    public static boolean prerequisites(KOMEPlayerProgression p,double alignment) {
        return prerequisites(p,alignment,p==null?"":p.getSerfKnightProgression().getLiege().factionKey);
    }
    public static boolean prerequisites(KOMEPlayerProgression p,double alignment,String faction) {
        return p!=null&&p.getCanonicalRank()==KOMEProgressionRank.KNIGHT
            &&p.getKnightService().qualifyingTypes(faction).size()>=REQUIRED_SERVICE
            &&Double.isFinite(alignment)&&alignment>=REQUIRED_ALIGNMENT;
    }
    static boolean validLiege(EntityPlayer player,LOTREntityNPC npc) {
        if(player==null||npc==null||player.worldObj==null||player.worldObj.isRemote||npc.worldObj!=player.worldObj)return false;
        KOMEWorldData data=KOMEWorldData.get(player.worldObj);
        return data.getProgression(player.getUniqueID()).getCanonicalRank()==KOMEProgressionRank.KNIGHT
            &&LOTRLevelData.getData(player).getPledgeFaction()!=null&&npc.getFaction()!=null&&npc.getFaction().isPlayableAlignmentFaction()
            &&npc.isEntityAlive()&&!npc.isChild()&&npc.hiredNPCInfo!=null&&!npc.hiredNPCInfo.isActive
            &&LOTRLevelData.getData(player).getPledgeFaction()==npc.getFaction()
            &&KOMEProgressionLords.isStandingTrialLiegeCandidate(npc)
            &&KOMEProgressionNpcRankService.effectiveRank(data,npc)==KOMEProgressionNpcRank.PRINCE;
    }
    /** An ongoing/failed trial remains discussable even if live alignment subsequently falls. */
    public static boolean eligible(EntityPlayer player,LOTREntityNPC npc) {
        if(!validLiege(player,npc))return false;
        KOMEPlayerProgression p=KOMEWorldData.get(player.worldObj).getProgression(player.getUniqueID());
        KOMELordshipTrial trial=p.getLordship().assignment();
        if(trial!=null)return KOMEProgressionFactionResolver.matches(trial.objective.faction,npc.getFaction())&&(!trial.authority.isSet()||trial.authority.hasSameIdentity(KOMEProgressionNpcRankService.referenceOf(npc)));
        KOMEKnightCommission commission=p.getKnightService().assignment();
        return commission==null&&prerequisites(p,LOTRLevelData.getData(player).getAlignment(npc.getFaction()),npc.getFaction().codeName());
    }
    public static KOMELordshipTrial prepare(EntityPlayerMP player,LOTREntityNPC liege) {
        if(!eligible(player,liege))return null;
        KOMEWorldData data=KOMEWorldData.get(player.worldObj);KOMEPlayerProgression p=data.getProgression(player.getUniqueID());
        if(p.getLordship().assignment()!=null)return p.getLordship().assignment();
        KOMELordshipTrial t=generate(player,liege,KOMELordshipTrial.Scenario.NATIVE_INVASION);
        if(t!=null&&p.getLordship().offer(t)){data.markDirty();return t;}return null;
    }
    static KOMELordshipTrial generate(EntityPlayerMP player,LOTREntityNPC prince,KOMELordshipTrial.Scenario scenario) {
        KOMEProgressionNpcRef ref=KOMEProgressionNpcRankService.referenceOf(prince);
        KOMEKnightCommission a=new KOMEKnightCommission(Type.BORDER_INCURSION,ref);
        KOMEKnightCommissionLocations.Destination destination=KOMEKnightCommissionLocations.choose(ref,player.worldObj,Type.STOLEN_GOODS,a.token,
            KOMEWorldData.get(player.worldObj).getProgression(player.getUniqueID()).getSerfKnightProgression().variety);
        if(destination==null)return null;
        a.x=a.destinationX=destination.x;a.z=a.destinationZ=destination.z;a.y=64;a.place=destination.name;
        List<LOTRInvasions> choices=KOMEProgressionRegionalEnemies.choices(player.worldObj,prince.getFaction(),a.x,a.z);
        Collections.shuffle(choices,player.worldObj.rand);
        String guard=guardClass(player.worldObj,prince.getFaction());if(guard==null)return null;
        for(LOTRInvasions invasion:choices)if(KOMEObjectiveInvasionBridge.safeComposition(invasion)){
            a.enemyFaction=invasion.invasionFaction.codeName();
            KOMELordshipTrial t=new KOMELordshipTrial(KOMELordshipTrial.Scenario.NATIVE_INVASION,a,Arrays.asList(guard,guard,guard,guard));
            t.nativeInvasion=true;t.authority=ref;t.invasionType=invasion.codeName();return t;
        }
        return null;
    }
    static String guardClass(World world,LOTRFaction faction) {
        return KOMEProgressionTrialSoldiers.resolve(world,faction);
    }
    static boolean suitableGuard(LOTREntityNPC npc,LOTRFaction faction) {
        if(npc==null)return false;
        if(npc.getClass().getSimpleName().toLowerCase(Locale.ROOT).contains("troll")&&faction!=LOTRFaction.HALF_TROLL)return false;
        String type=npc.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        for(String excluded:new String[]{"elite","captain","berserk","warg","wolf","spider","siege","banner","entityent","nazgul"})
            if(type.contains(excluded))return false;
        return npc.getFaction()==faction&&!npc.isCivilianNPC()&&!npc.isChild()&&npc.bossInfo==null
            &&!npc.isTraderEscort&&!(npc instanceof LOTRNPCMount)&&!(npc instanceof LOTRUnitTradeable)
            &&npc.hiredNPCInfo!=null&&!npc.hiredNPCInfo.isActive&&npc.ridingEntity==null;
    }
    public static boolean acceptOrReport(EntityPlayerMP player,LOTREntityNPC liege) {
        if(!eligible(player,liege)||player.getDistanceSqToEntity(liege)>64)return false;
        KOMEWorldData data=KOMEWorldData.get(player.worldObj);KOMEPlayerProgression p=data.getProgression(player.getUniqueID());
        KOMELordshipTrial t=p.getLordship().assignment();if(t==null)return false;KOMEKnightCommission a=t.objective;
        if(a.activationPending){if(!p.getLordship().discardTechnical(player.worldObj.getTotalWorldTime())){speech(player,liege,t,"progress");return true;}cleanup(player.worldObj,player.getUniqueID(),t);KOMEProgressionNpcSpeech.say(player,liege,"This field could not sustain a safe trial. Your service remains honoured. Petition me for a new invasion trial.");changed(data,player,p);return true;}
        if(a.stage==Stage.FAILED){cleanup(player.worldObj,player.getUniqueID(),t);p.getLordship().archive();speech(player,liege,t,"retry");changed(data,player,p);return true;}
        if(t.ready())return promote(player,liege);
        if(a.stage==Stage.OFFERED){
            LOTRFaction pledge=LOTRLevelData.getData(player).getPledgeFaction();
            if(!prerequisites(p,LOTRLevelData.getData(player).getAlignment(pledge),pledge==null?"":pledge.codeName())||p.getKnightService().assignment()!=null
                    ||!validLiege(player,liege))return false;
            t.authority=KOMEProgressionNpcRankService.referenceOf(liege);a.stage=Stage.ACTIVE;a.acceptedAt=player.worldObj.getTotalWorldTime();
            for(int i=0;i<t.guardClasses.size();i++){
                double[] site=KOMEKnightCommissionService.safeSite(player.worldObj,(int)liege.posX+i*2,(int)liege.posZ+3);
                LOTREntityNPC guard=site==null?null:KOMEKnightCommissionService.spawn(player,a,t.guardClasses.get(i),Role.GUARD,site,MARKER);
                if(guard==null){KOMEObjectiveInvasionBridge.retry(player,t,"Soldiers could not be entrusted safely.");cleanup(player.worldObj,player.getUniqueID(),t);a.actors.clear();a.stage=Stage.OFFERED;break;}follow(guard,player);
            }
            if(a.stage==Stage.ACTIVE&&t.nativeInvasion){speech(player,liege,t,"assigned");changed(data,player,p);return true;}
            if(a.stage==Stage.ACTIVE&&a.type==Type.DANGEROUS_ESCORT){
                double[] site=KOMEKnightCommissionService.safeSite(player.worldObj,(int)liege.posX,(int)liege.posZ);
                LOTREntityNPC charge=site==null?null:KOMEKnightCommissionService.spawn(player,a,a.civilianClass,Role.CHARGE,site,MARKER);
                if(charge==null){KOMEObjectiveInvasionBridge.retry(player,t,"The saved escort could not be prepared safely.");cleanup(player.worldObj,player.getUniqueID(),t);a.actors.clear();a.stage=Stage.OFFERED;}else {follow(charge,player);KOMEProgressionEscortFollowing.disable(charge);}
            }
            speech(player,liege,t,a.stage==Stage.FAILED?"failed":"assigned");
        } else speech(player,liege,t,"progress");
        changed(data,player,p);return true;
    }
    private static void follow(LOTREntityNPC npc,EntityPlayer player) {
        npc.hiredNPCInfo.isActive=true;npc.hiredNPCInfo.setHiringPlayer(player);
        npc.hiredNPCInfo.setTask(LOTRHiredNPCInfo.Task.WARRIOR);npc.hiredNPCInfo.ready();
    }
    public static void tickPlayer(EntityPlayerMP player) {
        if(player==null||player.worldObj.isRemote)return;
        KOMEWorldData data=KOMEWorldData.get(player.worldObj);KOMEPlayerProgression p=data.getProgression(player.getUniqueID());
        LOTRFaction currentPledge=LOTRLevelData.getData(player).getPledgeFaction();
        if(reconcileAllegiance(player.worldObj,player.getUniqueID(),p,currentPledge==null?"":currentPledge.codeName()))return;
        KOMELordshipTrial t=p.getLordship().assignment();if(t==null)return;KOMEKnightCommission a=t.objective;
        if(a.stage==Stage.FAILED)return;
        if(t.nativeInvasion){KOMEObjectiveInvasionBridge.tick(player,p,t);return;}
        LOTRFaction pledge=LOTRLevelData.getData(player).getPledgeFaction();
        if(p.getCanonicalRank()!=KOMEProgressionRank.KNIGHT||pledge==null||!KOMEProgressionFactionResolver.matches(a.faction,pledge)){
            fail(data,player.worldObj,player.getUniqueID(),t,"allegiance");return;
        }
        // Completed field evidence survives the original Liege's loss and may be reported to a replacement.
        if(t.ready()){
            if(!a.liege.hasSameIdentity(p.getSerfKnightProgression().getLiege())&&!t.forceReleased)relationshipLost(player.worldObj,player.getUniqueID(),p);
            if(!t.forceReleased){
                boolean missing=false;
                for(Actor actor:a.actors)if(actor.role==Role.GUARD&&!actor.dead){
                    Entity entity=KOMEKnightCommissionService.findLoaded(player.worldObj,actor.id);
                    if(entity!=null&&entity.isEntityAlive()){actor.x=entity.posX;actor.y=entity.posY;actor.z=entity.posZ;actor.missingTicks=0;}
                    else if(player.dimension==a.dimension&&player.getDistanceSq(actor.x,actor.y,actor.z)<=96*96
                            &&KOMEKnightCommissionService.loadedAround(player.worldObj,actor.x,actor.z)){
                missing=true;
            } else actor.missingTicks=0;
                }
                a.missingTicks=missing?a.missingTicks+20:0;
                // Earned field evidence must not become a dead end through invalid entity lifecycle loss.
                if(a.missingTicks>=1200){cleanup(player.worldObj,player.getUniqueID(),t);t.forceReleased=true;changed(data,player,p);}
                data.markDirty();
            }
            return;
        }
        if(!a.liege.hasSameIdentity(p.getSerfKnightProgression().getLiege())){fail(data,player.worldObj,player.getUniqueID(),t,"liege");return;}
        if(a.stage!=Stage.ACTIVE)return;
        if(!KOMEKnightCommissionService.validateDestination(player.worldObj,a)){KOMEObjectiveInvasionBridge.retry(player,t,"Destination unavailable.");return;}
        if(!a.encounterCreated&&a.atSite(player.dimension,player.posX,player.posZ,72)
                &&KOMEKnightCommissionService.loaded(player.worldObj,a.x,a.z)) {
            if(!KOMEKnightCommissionService.activate(player,a,MARKER)){KOMEObjectiveInvasionBridge.retry(player,t,"Encounter could not be prepared.");return;}
            changed(data,player,p);
        }
        boolean missing=false;
        for(Actor actor:a.actors)if(!actor.dead){Entity entity=KOMEKnightCommissionService.findLoaded(player.worldObj,actor.id);
            if(entity!=null){
                if(!entity.isEntityAlive()){fail(data,player.worldObj,player.getUniqueID(),t,"missing");return;}
                actor.x=entity.posX;actor.y=entity.posY;actor.z=entity.posZ;actor.missingTicks=0;
            } else if(player.dimension==a.dimension&&player.getDistanceSq(actor.x,actor.y,actor.z)<=96*96
                    &&KOMEKnightCommissionService.loadedAround(player.worldObj,actor.x,actor.z)){
                if(actor.role==Role.ENEMY){actor.missingTicks+=20;if(actor.missingTicks>=100&&!KOMEKnightCommissionService.recoverEnemy(player,a,actor,MARKER)){KOMEObjectiveInvasionBridge.retry(player,t,"The enemy force is unavailable.");return;}}else if(actor.role!=Role.GUARD)missing=true;
            } else actor.missingTicks=0;
        }
        a.missingTicks=missing?a.missingTicks+20:0;
        if(a.missingTicks>=1200){fail(data,player.worldObj,player.getUniqueID(),t,"missing");return;}
        boolean present=a.type==Type.DANGEROUS_ESCORT ? a.threatResolved&&a.atDestination(player.dimension,player.posX,player.posZ) : a.atSite(player.dimension,player.posX,player.posZ,96);
        if(objectiveComplete(t,player,present)){completeField(data,player,t);return;}
        data.markDirty();
    }
    static boolean reconcileAllegiance(World world,UUID owner,KOMEPlayerProgression p,String faction) {
        KOMELordshipTrial t=p.getLordship().assignment();if(t==null)return false;
        if(p.getCanonicalRank()==KOMEProgressionRank.KNIGHT&&KOMEProgressionRelationshipLifecycle.sameFaction(t.objective.faction,faction))return false;
        cleanup(world,owner,t);t.objective.threatResolved=t.objective.threatResolved||t.ready();
        t.objective.stage=Stage.FAILED;t.failureReason="allegiance";p.getLordship().archive();
        KOMEWorldData data=KOMEWorldData.get(world);KOMEProgressionNpcRoles.syncPlayer(data,owner);data.markDirty();return true;
    }
    /** A surviving UUID is insufficient for success: the surviving force must actually be loaded. */
    static boolean objectiveComplete(KOMELordshipTrial t,EntityPlayerMP owner,boolean present) {
        KOMEKnightCommission a=t.objective;
        if(a.stage!=Stage.ACTIVE||!a.encounterCreated||!a.allEnemiesDead()||!a.participated||!present
                ||t.playerDied)return false;

        if(a.type==Type.BORDER_INCURSION)return true;
        Actor beneficiary=a.protectedActor();Entity e=beneficiary==null?null:KOMEKnightCommissionService.findLoaded(owner.worldObj,beneficiary.id);
        if(beneficiary==null||beneficiary.dead||e==null||!e.isEntityAlive())return false;
        return a.type!=Type.DANGEROUS_ESCORT||(a.atDestination(e.worldObj.provider.dimensionId,e.posX,e.posZ)&&e instanceof LOTREntityNPC&&KOMEProgressionDestinations.arrived((LOTREntityNPC)e,a.destinationProof)&&owner.getDistanceSqToEntity(e)<=256);
    }
    static int returnedSurvivors(KOMELordshipTrial t,EntityPlayerMP owner) {
        int present=0;
        for(Actor actor:t.objective.actors)if(actor.role==Role.GUARD&&!actor.dead){Entity e=KOMEKnightCommissionService.findLoaded(owner.worldObj,actor.id);
            if(e instanceof LOTREntityNPC&&e.isEntityAlive()&&e.worldObj.provider.dimensionId==t.objective.dimension
                    &&temporaryFollower(KOMEWorldData.get(owner.worldObj),(LOTREntityNPC)e)&&owner.getDistanceSqToEntity(e)<=96*96)present++;
        }
        return present;
    }
    private static void cleanupObjective(World world,UUID owner,KOMELordshipTrial t) {
        for(Actor actor:t.objective.actors)if(actor.role!=Role.GUARD){Entity e=KOMEKnightCommissionService.findLoaded(world,actor.id);
            if(e instanceof LOTREntityNPC){LOTREntityNPC npc=(LOTREntityNPC)e;KOMEProgressionEncounterMarker.Marker m=KOMEProgressionEncounterMarker.read(npc);
                if(m!=null&&MARKER.equals(m.kind)&&m.owner.equals(owner)&&t.objective.token.equals(m.token)){
                    if(t.ready()&&actor.role==Role.CHARGE){
                        EntityPlayer player=world.func_152378_a(owner);
                        if(player instanceof EntityPlayerMP)KOMEProgressionNpcSpeech.say((EntityPlayerMP)player,npc,KOMEProgressionDialogue.thanks(npc));
                        if(npc.hiredNPCInfo.isActive)KOMEProgressionEscortFollowing.releaseDelivered(npc);
                        KOMEProgressionEncounterMarker.clear(npc);
                    }else{if(npc.hiredNPCInfo.isActive)dismissFollower(npc);npc.setDead();}
                }
            }
        }
    }
    private static void completeField(KOMEWorldData data,EntityPlayerMP player,KOMELordshipTrial t) {
        t.objective.stage=Stage.READY_TO_REPORT;t.objective.threatResolved=true;t.objective.missingTicks=0;t.finishedAt=player.worldObj.getTotalWorldTime();
        // The civilian is safe; surviving soldiers accompany their Knight back to the Liege.
        cleanupObjective(player.worldObj,player.getUniqueID(),t);changed(data,player,data.getProgression(player.getUniqueID()));
    }
    public static boolean noteDamage(KOMEWorldData data,UUID owner,String target) {
        KOMEPlayerProgression p=data.progressions.get(owner);KOMELordshipTrial t=p==null?null:p.getLordship().assignment();
        if(t==null||t.objective.stage!=Stage.ACTIVE)return false;
        for(Actor actor:t.objective.actors)if(actor.role==Role.ENEMY&&!actor.dead&&actor.id.equals(target)){
            t.objective.participated=true;data.markDirty();return true;}
        return false;
    }
    public static void npcDeath(KOMEWorldData data,World world,String id) {
        for(Map.Entry<UUID,KOMEPlayerProgression> row:data.progressions.entrySet()){
            KOMELordshipTrial t=row.getValue().getLordship().assignment();if(t==null||(t.objective.stage!=Stage.ACTIVE&&!t.ready()))continue;
            if(t.authority.entityUuid.equals(id)){t.authority=KOMEProgressionNpcRef.EMPTY;data.markDirty();}
            if(t.nativeInvasion){KOMEObjectiveInvasionBridge.npcDeath(data,row.getKey(),t,id);continue;}
            KOMEKnightCommission a=t.objective;
            if(a.liege.entityUuid.equals(id)){if(t.ready())relationshipLost(world,row.getKey(),row.getValue());else fail(data,world,row.getKey(),t,"liege");continue;}
            for(Actor actor:a.actors)if(actor.id.equals(id)&&!actor.dead&&(!t.ready()||actor.role==Role.GUARD)){
                actor.dead=true;
                if(actor.role==Role.GUARD){ /* Soldier losses do not fail the player. */ }
                else if(actor.role!=Role.ENEMY)fail(data,world,row.getKey(),t,"beneficiary");
                else if(a.allEnemiesDead()) {
                    EntityPlayer owner=world==null?null:world.func_152378_a(row.getKey());
                    Entity fallen=KOMEKnightCommissionService.findLoaded(world,id);
                    double distance=owner==null?Double.POSITIVE_INFINITY:fallen==null?owner.getDistanceSq(actor.x,actor.y,actor.z):owner.getDistanceSqToEntity(fallen);
                    if(!a.participated||owner==null||owner.dimension!=a.dimension||distance>96*96)fail(data,world,row.getKey(),t,"participation");
                    else {a.threatResolved=true;if(owner instanceof EntityPlayerMP&&objectiveComplete(t,(EntityPlayerMP)owner,true))completeField(data,(EntityPlayerMP)owner,t);}
                }
                data.markDirty();break;
            }
        }
    }
    /** Only this personal report can perform gameplay promotion into Lord. */
    public static synchronized boolean promote(EntityPlayerMP player,LOTREntityNPC liege) {
        if(!validLiege(player,liege)||player.getDistanceSqToEntity(liege)>64)return false;
        KOMEWorldData data=KOMEWorldData.get(player.worldObj);KOMEPlayerProgression p=data.getProgression(player.getUniqueID());
        KOMELordshipTrial t=p.getLordship().assignment();LOTRFaction pledge=LOTRLevelData.getData(player).getPledgeFaction();
        if(t==null||!t.ready()||!eligible(player,liege)||!KOMEProgressionFactionResolver.matches(t.objective.faction,pledge)
                ||!prerequisites(p,LOTRLevelData.getData(player).getAlignment(pledge),pledge==null?"":pledge.codeName())){
            if(t!=null)speech(player,liege,t,"standing");return false;
        }
        if(t.playerDied||t.nativeInvasion&&!t.nativeVictory)return false;
        t.acknowledgedLosses=0;for(Actor actor:t.objective.actors)if(actor.role==Role.GUARD&&actor.dead)t.acknowledgedLosses++;
        cleanup(player.worldObj,player.getUniqueID(),t);t.forceReleased=true;
        if(!KOMECanonicalRankService.setCanonicalRank(data,player.getUniqueID(),KOMEProgressionRank.LORD))return false;
        t.objective.stage=Stage.REPORTED;t.objective.reportedAt=player.worldObj.getTotalWorldTime();
        p.getSerfKnightProgression().setLiege(KOMEProgressionNpcRankService.referenceOf(liege));p.getLordship().archive();p.grant("knight.title_lord");KOMEProgressionAutoCompleter.applyUnlocks(p);
        speech(player,liege,t,"promoted");KOMEProgressionAnnouncements.earned(player,KOMEProgressionRank.KNIGHT,t.objective.adminForced);changed(data,player,p);return true;
    }
    static void fail(KOMEWorldData data,World world,UUID owner,KOMELordshipTrial t,String reason) {
        t.objective.stage=Stage.FAILED;t.objective.cleanupPending=true;t.failureReason=reason;
        cleanup(world,owner,t);KOMEProgressionNpcRoles.syncPlayer(data,owner);data.markDirty();
    }
    /** Relationship cleanup preserves earned field evidence; explicit reset/override uses cancel. */
    public static void relationshipLost(World world,UUID owner,KOMEPlayerProgression p) {
        KOMELordshipTrial t=p.getLordship().assignment();if(t==null)return;
        if(t.nativeInvasion&&!t.ready())return;
        if(t.ready()){cleanup(world,owner,t);t.forceReleased=true;KOMEProgressionNpcRoles.syncPlayer(KOMEWorldData.get(world),owner);KOMEWorldData.get(world).markDirty();}else fail(KOMEWorldData.get(world),world,owner,t,"liege");
    }
    public static void cancel(World world,UUID owner,KOMEPlayerProgression p) {
        KOMELordshipTrial t=p.getLordship().assignment();if(t!=null)fail(KOMEWorldData.get(world),world,owner,t,"cancelled");
    }
    static void cleanup(World world,UUID owner,KOMELordshipTrial t) {
        if(world==null)return;
        KOMEObjectiveInvasionBridge.cleanupController(world,t);
        for(Object entity:new ArrayList<Object>(world.loadedEntityList))if(entity instanceof LOTREntityNPC){
            LOTREntityNPC npc=(LOTREntityNPC)entity;KOMEProgressionEncounterMarker.Marker m=KOMEProgressionEncounterMarker.read(npc);
            if(m!=null&&MARKER.equals(m.kind)&&owner.equals(m.owner)&&t.objective.token.equals(m.token)){
                if(npc.hiredNPCInfo!=null&&npc.hiredNPCInfo.isActive)dismissFollower(npc);npc.setDead();
            }
        }
    }
    private static Actor boundActor(KOMEWorldData data,LOTREntityNPC npc) {
        KOMEProgressionEncounterMarker.Marker m=KOMEProgressionEncounterMarker.read(npc);
        if(m==null||!MARKER.equals(m.kind))return null;
        KOMEPlayerProgression p=data.progressions.get(m.owner);KOMELordshipTrial t=p==null?null:p.getLordship().assignment();
        if(t==null||p.getCanonicalRank()!=KOMEProgressionRank.KNIGHT||(t.objective.stage!=Stage.ACTIVE&&!t.ready())||t.forceReleased
                ||!t.objective.token.equals(m.token))return null;
        for(Actor actor:t.objective.actors)if((!t.ready()||actor.role==Role.GUARD)&&!actor.dead&&actor.id.equals(npc.getUniqueID().toString())&&actor.className.equals(npc.getClass().getName())
                &&KOMEProgressionFactionResolver.matches(actor.role==Role.ENEMY?t.objective.enemyFaction:t.objective.faction,npc.getFaction()))return actor;
        return null;
    }
    public static boolean temporaryFollower(KOMEWorldData data,LOTREntityNPC npc) {
        Actor actor=boundActor(data,npc);KOMEProgressionEncounterMarker.Marker m=KOMEProgressionEncounterMarker.read(npc);
        return actor!=null&&(actor.role==Role.GUARD||actor.role==Role.CHARGE)&&npc.hiredNPCInfo!=null
            &&npc.hiredNPCInfo.isActive&&m.owner.equals(npc.hiredNPCInfo.getHiringPlayerUUID());
    }
    public static void reconcileNpc(KOMEWorldData data,LOTREntityNPC npc) {
        KOMEProgressionEncounterMarker.Marker m=KOMEProgressionEncounterMarker.read(npc);if(m==null||!MARKER.equals(m.kind))return;
        Actor actor=boundActor(data,npc);
        if(actor==null){if(npc.hiredNPCInfo!=null&&npc.hiredNPCInfo.isActive)dismissFollower(npc);npc.setDead();return;}
        if(actor.role==Role.GUARD||actor.role==Role.CHARGE){
            EntityPlayer owner=npc.worldObj.func_152378_a(m.owner);
            if(owner!=null&&(!npc.hiredNPCInfo.isActive||!m.owner.equals(npc.hiredNPCInfo.getHiringPlayerUUID())))follow(npc,owner);
        }
    }
    /** Native hiring GUI is bypassed. Sneak-interact with an entrusted guard toggles follow/wait. */
    public static boolean interactFollower(EntityPlayerMP player,LOTREntityNPC npc) {
        KOMEProgressionEncounterMarker.Marker m=KOMEProgressionEncounterMarker.read(npc);
        if(m==null||!MARKER.equals(m.kind))return false;
        KOMEWorldData data=KOMEWorldData.get(player.worldObj);Actor actor=boundActor(data,npc);
        if(actor==null||actor.role!=Role.GUARD&&actor.role!=Role.CHARGE)return false;
        if(actor!=null&&m.owner.equals(player.getUniqueID())&&player.getDistanceSqToEntity(npc)<=64){
            if(actor.role==Role.GUARD&&player.isSneaking()){
                if(npc.hiredNPCInfo.isHalted())npc.hiredNPCInfo.ready();else npc.hiredNPCInfo.halt();
                KOMEProgressionNpcSpeech.say(player,npc,KOMELordshipTrialPresentation.command(npc.hiredNPCInfo.isHalted()));
            } else KOMEProgressionNpcSpeech.say(player,npc,KOMELordshipTrialPresentation.command(false));
        }
        return true;
    }
    private static void speech(EntityPlayerMP player,LOTREntityNPC npc,KOMELordshipTrial t,String event) {
        KOMEProgressionNpcSpeech.say(player,npc,KOMELordshipTrialPresentation.speech(t,event));
    }
    private static void changed(KOMEWorldData data,EntityPlayerMP player,KOMEPlayerProgression p) {
        KOMEProgressionNpcRoles.syncPlayer(data,player.getUniqueID());data.markDirty();KOMEProgressionAutoCompleter.syncPlayer(player,p);
    }
    private static void dismissFollower(LOTREntityNPC npc) { KOMEProgressionEscortFollowing.restore(npc);npc.hiredNPCInfo.dismissUnit(false); }
}
