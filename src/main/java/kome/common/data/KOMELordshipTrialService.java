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
        return KOMECurrentLiege.valid(player,npc)&&KOMEWorldData.get(player.worldObj).getProgression(player.getUniqueID()).getCanonicalRank()==KOMEProgressionRank.KNIGHT;
    }
    /** An ongoing/failed trial remains discussable even if live alignment subsequently falls. */
    public static boolean eligible(EntityPlayer player,LOTREntityNPC npc) {
        if(!validLiege(player,npc))return false;
        KOMEPlayerProgression p=KOMEWorldData.get(player.worldObj).getProgression(player.getUniqueID());
        KOMELordshipTrial trial=p.getLordship().assignment();
        if(trial!=null)return KOMEProgressionFactionResolver.matches(trial.objective.faction,npc.getFaction());
        KOMEKnightCommission commission=p.getKnightService().assignment();
        return commission==null&&prerequisites(p,LOTRLevelData.getData(player).getAlignment(npc.getFaction()),npc.getFaction().codeName());
    }
    public static KOMELordshipTrial prepare(EntityPlayerMP player,LOTREntityNPC liege) {
        if(!eligible(player,liege))return null;
        KOMEWorldData data=KOMEWorldData.get(player.worldObj);KOMEPlayerProgression p=data.getProgression(player.getUniqueID());
        if(p.getLordship().assignment()!=null)return p.getLordship().assignment();
        List<KOMELordshipTrial.Scenario> scenarios=new ArrayList<KOMELordshipTrial.Scenario>(Arrays.asList(KOMELordshipTrial.Scenario.values()));
        Collections.shuffle(scenarios,player.worldObj.rand);
        for(KOMELordshipTrial.Scenario scenario:scenarios){KOMELordshipTrial t=generate(player,liege,scenario);if(t!=null&&p.getLordship().offer(t)){data.markDirty();return t;}}
        return null;
    }
    static KOMELordshipTrial generate(EntityPlayerMP player,LOTREntityNPC liege,KOMELordshipTrial.Scenario scenario) {
        KOMEKnightCommission objective=KOMEKnightCommissionService.generate(player,liege,scenario.objectiveType);
        if(objective==null)return null;
        String guard=guardClass(player.worldObj,liege.getFaction());if(guard==null)return null;
        // A larger bound threat, using only the already validated hostile context.
        while(objective.enemyClasses.size()<5)objective.enemyClasses.add(objective.enemyClasses.get(objective.enemyClasses.size()%3));
        return new KOMELordshipTrial(scenario,objective,Arrays.asList(guard,guard,guard,guard));
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
        if(a.stage==Stage.FAILED){cleanup(player.worldObj,player.getUniqueID(),t);p.getLordship().archive();speech(player,liege,t,"retry");changed(data,player,p);return true;}
        if(t.ready())return promote(player,liege);
        if(a.stage==Stage.OFFERED){
            LOTRFaction pledge=LOTRLevelData.getData(player).getPledgeFaction();
            if(!prerequisites(p,LOTRLevelData.getData(player).getAlignment(pledge),pledge==null?"":pledge.codeName())||p.getKnightService().assignment()!=null
                    ||!a.liege.hasSameIdentity(p.getSerfKnightProgression().getLiege()))return false;
            a.stage=Stage.ACTIVE;a.acceptedAt=player.worldObj.getTotalWorldTime();
            for(int i=0;i<t.guardClasses.size();i++){
                double[] site=KOMEKnightCommissionService.safeSite(player.worldObj,(int)liege.posX+i*2,(int)liege.posZ+3);
                LOTREntityNPC guard=site==null?null:KOMEKnightCommissionService.spawn(player,a,t.guardClasses.get(i),Role.GUARD,site,MARKER);
                if(guard==null){fail(data,player.worldObj,player.getUniqueID(),t,"generation");break;}follow(guard,player);
            }
            if(a.stage==Stage.ACTIVE&&a.type==Type.DANGEROUS_ESCORT){
                double[] site=KOMEKnightCommissionService.safeSite(player.worldObj,(int)liege.posX,(int)liege.posZ);
                LOTREntityNPC charge=site==null?null:KOMEKnightCommissionService.spawn(player,a,a.civilianClass,Role.CHARGE,site,MARKER);
                if(charge==null)fail(data,player.worldObj,player.getUniqueID(),t,"generation");else follow(charge,player);
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
                    if(entity!=null&&entity.isEntityAlive()){actor.x=entity.posX;actor.y=entity.posY;actor.z=entity.posZ;}
                    else if(player.dimension==a.dimension&&player.getDistanceSq(actor.x,actor.y,actor.z)<=96*96
                            &&KOMEKnightCommissionService.loadedAround(player.worldObj,actor.x,actor.z))missing=true;
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
        if(!a.encounterCreated&&a.atSite(player.dimension,player.posX,player.posZ,72)
                &&KOMEKnightCommissionService.loaded(player.worldObj,a.x,a.z)) {
            if(!KOMEKnightCommissionService.activate(player,a,MARKER)){fail(data,player.worldObj,player.getUniqueID(),t,"generation");return;}
            changed(data,player,p);
        }
        boolean missing=false;
        for(Actor actor:a.actors)if(!actor.dead){Entity entity=KOMEKnightCommissionService.findLoaded(player.worldObj,actor.id);
            if(entity!=null){
                if(!entity.isEntityAlive()){fail(data,player.worldObj,player.getUniqueID(),t,"missing");return;}
                actor.x=entity.posX;actor.y=entity.posY;actor.z=entity.posZ;
            } else if(player.dimension==a.dimension&&player.getDistanceSq(actor.x,actor.y,actor.z)<=96*96
                    &&KOMEKnightCommissionService.loadedAround(player.worldObj,actor.x,actor.z))missing=true;
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
                ||t.confirmedSurvivors()<t.requiredSurvivors)return false;
        if(returnedSurvivors(t,owner)<t.requiredSurvivors)return false;
        if(a.type==Type.BORDER_INCURSION)return true;
        Actor beneficiary=a.protectedActor();Entity e=beneficiary==null?null:KOMEKnightCommissionService.findLoaded(owner.worldObj,beneficiary.id);
        if(beneficiary==null||beneficiary.dead||e==null||!e.isEntityAlive())return false;
        return a.type!=Type.DANGEROUS_ESCORT||(a.atDestination(e.worldObj.provider.dimensionId,e.posX,e.posZ)&&owner.getDistanceSqToEntity(e)<=256);
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
                    if(npc.hiredNPCInfo.isActive)npc.hiredNPCInfo.dismissUnit(false);npc.setDead();
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
            KOMEKnightCommission a=t.objective;
            if(a.liege.entityUuid.equals(id)){if(t.ready())relationshipLost(world,row.getKey(),row.getValue());else fail(data,world,row.getKey(),t,"liege");continue;}
            for(Actor actor:a.actors)if(actor.id.equals(id)&&!actor.dead&&(!t.ready()||actor.role==Role.GUARD)){
                actor.dead=true;
                if(actor.role==Role.GUARD){if(t.confirmedSurvivors()<t.requiredSurvivors)fail(data,world,row.getKey(),t,"casualties");}
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
        if(t==null||!t.ready()||!KOMEProgressionFactionResolver.matches(t.objective.faction,pledge)
                ||!prerequisites(p,LOTRLevelData.getData(player).getAlignment(pledge),pledge==null?"":pledge.codeName())){
            if(t!=null)speech(player,liege,t,"standing");return false;
        }
        if(!t.forceReleased&&returnedSurvivors(t,player)<t.requiredSurvivors){speech(player,liege,t,"bring_survivors");return false;}
        cleanup(player.worldObj,player.getUniqueID(),t);t.forceReleased=true;
        if(!KOMECanonicalRankService.setCanonicalRank(data,player.getUniqueID(),KOMEProgressionRank.LORD))return false;
        t.objective.stage=Stage.REPORTED;t.objective.reportedAt=player.worldObj.getTotalWorldTime();
        p.getLordship().archive();p.grant("knight.title_lord");KOMEProgressionAutoCompleter.applyUnlocks(p);
        speech(player,liege,t,"promoted");changed(data,player,p);return true;
    }
    static void fail(KOMEWorldData data,World world,UUID owner,KOMELordshipTrial t,String reason) {
        t.objective.stage=Stage.FAILED;t.objective.cleanupPending=true;t.failureReason=reason;
        cleanup(world,owner,t);KOMEProgressionNpcRoles.syncPlayer(data,owner);data.markDirty();
    }
    /** Relationship cleanup preserves earned field evidence; explicit reset/override uses cancel. */
    public static void relationshipLost(World world,UUID owner,KOMEPlayerProgression p) {
        KOMELordshipTrial t=p.getLordship().assignment();if(t==null)return;
        if(t.ready()){cleanup(world,owner,t);t.forceReleased=true;KOMEProgressionNpcRoles.syncPlayer(KOMEWorldData.get(world),owner);KOMEWorldData.get(world).markDirty();}else fail(KOMEWorldData.get(world),world,owner,t,"liege");
    }
    public static void cancel(World world,UUID owner,KOMEPlayerProgression p) {
        KOMELordshipTrial t=p.getLordship().assignment();if(t!=null)fail(KOMEWorldData.get(world),world,owner,t,"cancelled");
    }
    static void cleanup(World world,UUID owner,KOMELordshipTrial t) {
        if(world==null)return;
        for(Object entity:new ArrayList<Object>(world.loadedEntityList))if(entity instanceof LOTREntityNPC){
            LOTREntityNPC npc=(LOTREntityNPC)entity;KOMEProgressionEncounterMarker.Marker m=KOMEProgressionEncounterMarker.read(npc);
            if(m!=null&&MARKER.equals(m.kind)&&owner.equals(m.owner)&&t.objective.token.equals(m.token)){
                if(npc.hiredNPCInfo!=null&&npc.hiredNPCInfo.isActive)npc.hiredNPCInfo.dismissUnit(false);npc.setDead();
            }
        }
    }
    private static Actor boundActor(KOMEWorldData data,LOTREntityNPC npc) {
        KOMEProgressionEncounterMarker.Marker m=KOMEProgressionEncounterMarker.read(npc);
        if(m==null||!MARKER.equals(m.kind))return null;
        KOMEPlayerProgression p=data.progressions.get(m.owner);KOMELordshipTrial t=p==null?null:p.getLordship().assignment();
        if(t==null||p.getCanonicalRank()!=KOMEProgressionRank.KNIGHT||(t.objective.stage!=Stage.ACTIVE&&!t.ready())||t.forceReleased
                ||!t.objective.token.equals(m.token)||!t.objective.liege.hasSameIdentity(p.getSerfKnightProgression().getLiege()))return null;
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
        if(actor==null){if(npc.hiredNPCInfo!=null&&npc.hiredNPCInfo.isActive)npc.hiredNPCInfo.dismissUnit(false);npc.setDead();return;}
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
}
