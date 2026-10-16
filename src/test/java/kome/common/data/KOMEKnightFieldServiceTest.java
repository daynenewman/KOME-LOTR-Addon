package kome.common.data;

import java.util.*;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.fac.LOTRFaction;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;
import static org.junit.Assert.*;
import static kome.common.data.KOMEKnightCommission.*;

/** Production lifecycle fixtures; actual Minecraft pathfinding is explicitly a live acceptance task. */
public class KOMEKnightFieldServiceTest {
    static KOMEKnightCommission field(KOMEKnightCommissionGameplayTest.Session s,Type type,int tier){
        KOMEKnightCommission a=s.active(type);KOMEKnightFieldService.profile(a,tier);a.complication=Complication.NONE;return a;
    }
    static void kill(KOMEKnightCommissionGameplayTest.Session s,KOMEKnightCommission a,LOTREntityNPC enemy){
        KOMEKnightCommissionService.noteDamage(s.f.data,s.f.player.id,enemy.getUniqueID().toString(),16);
        KOMEKnightCommissionService.npcDeath(s.f.data,s.f.world,enemy.getUniqueID().toString());enemy.setDead();
    }
    @Test public void difficultyRisesOnlyWithEarnedCurrentFactionReports(){
        KOMEKnightServiceRecord r=new KOMEKnightServiceRecord();assertEquals(1,KOMEKnightFieldService.tier(r,"rohan"));
        for(int i=0;i<3;i++){KOMEKnightCommission a=KOMEKnightCommissionStateTest.commission(Type.values()[i]);r.offer(a);a.stage=Stage.READY_TO_REPORT;assertTrue(r.report(a.liege));assertEquals(Math.min(3,i+2),KOMEKnightFieldService.tier(r,"rohan"));assertEquals(1,KOMEKnightFieldService.tier(r,"gondor"));}
        KOMEKnightCommission forced=KOMEKnightCommissionStateTest.commission(Type.RELIEF);forced.adminForced=true;r.offer(forced);forced.stage=Stage.READY_TO_REPORT;r.report(forced.liege);assertEquals(3,r.qualifyingTypes("rohan").size());
    }
    @Test public void weightedTierSelectionVariesWithoutDiscardingFallbacks(){
        Set<Type> first=new HashSet<Type>(),third=new HashSet<Type>();
        for(int seed=0;seed<180;seed++)for(int tier=1;tier<=3;tier++){
            List<Type> order=KOMEProgressionVariety.order(Arrays.asList(Type.values()),Collections.<KOMEKnightCommission>emptyList(),"rohan",new Random(seed*999L),tier);
            assertEquals(Type.values().length,new HashSet<Type>(order).size());assertEquals(8,KOMEKnightFieldService.preference(order.get(0),tier));if(tier==1)first.add(order.get(0));if(tier==3)third.add(order.get(0));
        }assertTrue(first.size()>=3);assertTrue(third.size()>=3);
    }
    @Test public void offersFreezeAllProfileFieldsAndLegacyKeepsTierZero()throws Exception{
        try(KOMEKnightCommissionGameplayTest.Session s=new KOMEKnightCommissionGameplayTest.Session()){
            LOTREntityNPC liege=KOMEKnightCommissionInteractionTest.captain(s);KOMEKnightCommission a=KOMEKnightCommissionInteractionTest.offered(s,liege,Type.PROTECT_LIEGE);KOMEKnightFieldService.profile(a,3);a.complication=Complication.COUNTERATTACK;
            assertSame(a,KOMEKnightCommissionService.prepare(s.f.player,liege));NBTTagCompound saved=a.writeToNBT();s.reload();assertEquals(saved,s.p.getKnightService().assignment().writeToNBT());assertEquals(saved,KOMEKnightCommissionService.prepare(s.f.player,liege).writeToNBT());
            NBTTagCompound legacy=(NBTTagCompound)saved.copy();legacy.removeTag("Tier");legacy.removeTag("Phase");legacy.removeTag("Complication");assertEquals(0,KOMEKnightCommission.readFromNBT(legacy).tier);
        }
    }
    @Test public void rescueRequiresLivingSavedSurvivorAndActualRefugeArrival()throws Exception{
        try(KOMEKnightCommissionGameplayTest.Session s=new KOMEKnightCommissionGameplayTest.Session()){
            KOMEKnightCommission a=field(s,Type.RESCUE_PATROL,2);a.encounterCreated=true;
            LOTREntityNPC survivor=KOMEKnightCommissionGameplayTest.actor(s,a,Role.BENEFICIARY),enemy=KOMEKnightCommissionGameplayTest.actor(s,a,Role.ENEMY);
            assertFalse(KOMEKnightCommissionService.activeEscort(s.f.data,survivor));kill(s,a,enemy);KOMEKnightCommissionService.tickPlayer(s.f.player);
            assertEquals(Phase.EXTRACT,a.phase);assertEquals(Stage.ACTIVE,a.stage);assertTrue(KOMEKnightCommissionService.activeEscort(s.f.data,survivor));assertFalse(survivor.hiredNPCInfo.teleportAutomatically);
            s.reload();a=s.p.getKnightService().assignment();survivor.posX=a.destinationX;survivor.posZ=a.destinationZ;s.f.player.posX=survivor.posX;s.f.player.posZ=survivor.posZ;KOMEKnightCommissionService.tickPlayer(s.f.player);
            assertEquals(Stage.READY_TO_REPORT,a.stage);assertFalse(survivor.hiredNPCInfo.isActive);assertTrue(s.p.getKnightService().qualifyingTypes("rohan").isEmpty());
        }
    }
    @Test public void survivorLossFailsOnlyCurrentMission()throws Exception{
        try(KOMEKnightCommissionGameplayTest.Session s=new KOMEKnightCommissionGameplayTest.Session()){
            KOMEKnightCommission a=field(s,Type.RESCUE_PATROL,2);a.encounterCreated=true;LOTREntityNPC survivor=KOMEKnightCommissionGameplayTest.actor(s,a,Role.BENEFICIARY);
            KOMEKnightCommissionService.npcDeath(s.f.data,s.f.world,survivor.getUniqueID().toString());assertEquals(Stage.FAILED,a.stage);assertFalse(a.threatResolved);
        }
    }
    @Test public void counterattackIsSavedOnceAndCannotBeSkippedByFinalKill()throws Exception{
        try(KOMEKnightCommissionGameplayTest.Session s=new KOMEKnightCommissionGameplayTest.Session()){
            KOMEKnightCommission a=field(s,Type.BORDER_INCURSION,2);a.encounterCreated=true;a.complication=Complication.COUNTERATTACK;LOTREntityNPC enemy=KOMEKnightCommissionGameplayTest.actor(s,a,Role.ENEMY);kill(s,a,enemy);
            assertEquals(Stage.ACTIVE,a.stage);KOMEKnightCommissionService.tickPlayer(s.f.player);assertTrue(a.complicationTriggered);assertEquals(3,a.actors.size());s.reload();a=s.p.getKnightService().assignment();KOMEKnightCommissionService.tickPlayer(s.f.player);assertEquals(3,a.actors.size());
            for(Actor actor:new ArrayList<Actor>(a.actors))if(!actor.dead)kill(s,a,(LOTREntityNPC)KOMEKnightCommissionService.findLoaded(s.f.world,actor.id));assertEquals(Stage.READY_TO_REPORT,a.stage);
        }
    }
    @Test public void technicalRetryInvalidatesActorsAndPreservesOrdersAndComplication()throws Exception{
        try(KOMEKnightCommissionGameplayTest.Session s=new KOMEKnightCommissionGameplayTest.Session()){
            KOMEKnightCommission a=field(s,Type.WAR_PLANS,3);a.complication=Complication.COUNTERATTACK;a.encounterCreated=true;LOTREntityNPC npc=KOMEKnightCommissionGameplayTest.actor(s,a,Role.ENEMY);a.targetId=npc.getUniqueID().toString();a.dispatchStolen=true;
            ItemStack old=KOMEKnightEspionage.dispatch(a,s.f.player.id);KOMEKnightCommissionService.technicalRetry(s.f.player,a);
            assertTrue(npc.isDead);assertTrue(a.activationPending);assertEquals(3,a.tier);assertEquals(Complication.COUNTERATTACK,a.complication);assertFalse(a.dispatchStolen);assertFalse(KOMEKnightEspionage.matches(old,a,s.f.player.id));assertTrue(a.actors.isEmpty());
        }
    }
    @Test public void dispatchRequiresSavedTheftExactActorOwnerFactionTokenAndRevision()throws Exception{
        try(KOMEKnightCommissionGameplayTest.Session s=new KOMEKnightCommissionGameplayTest.Session()){
            KOMEKnightCommission a=field(s,Type.WAR_PLANS,3);a.encounterCreated=true;LOTREntityNPC npc=KOMEKnightCommissionGameplayTest.actor(s,a,Role.ENEMY);a.targetId=npc.getUniqueID().toString();
            ItemStack forged=KOMEKnightEspionage.dispatch(a,s.f.player.id);assertFalse(KOMEKnightEspionage.matches(forged,a,s.f.player.id));
            assertTrue(KOMEKnightEspionage.validTarget(s.f.player,npc,a));ItemStack item=KOMEKnightEspionage.steal(s.f.player,npc,a);assertTrue(KOMEKnightEspionage.matches(item,a,s.f.player.id));assertFalse(KOMEKnightEspionage.validTarget(s.f.player,npc,a));assertFalse(KOMEKnightEspionage.matches(item,a,UUID.randomUUID()));
            for(String key:new String[]{"Token","Target","Faction","Owner"}){ItemStack bad=item.copy();bad.getTagCompound().getCompoundTag(KOMEKnightEspionage.DISPATCH).setString(key,UUID.randomUUID().toString());assertFalse(key,KOMEKnightEspionage.matches(bad,a,s.f.player.id));}
            assertFalse(KOMEKnightEspionage.matches(new ItemStack(Items.book),a,s.f.player.id));a.revision++;assertFalse(KOMEKnightEspionage.matches(item,a,s.f.player.id));
        }
    }
    @Test public void theftCannotBeReplacedByOfficerKillAndEscapeRequiresDistanceAndProperty()throws Exception{
        try(KOMEKnightCommissionGameplayTest.Session s=new KOMEKnightCommissionGameplayTest.Session()){
            KOMEKnightCommission a=field(s,Type.WAR_PLANS,3);a.encounterCreated=true;LOTREntityNPC npc=KOMEKnightCommissionGameplayTest.actor(s,a,Role.ENEMY);a.targetId=npc.getUniqueID().toString();
            kill(s,a,npc);assertEquals(Stage.FAILED,a.stage);assertFalse(a.dispatchStolen);
        }
        try(KOMEKnightCommissionGameplayTest.Session s=new KOMEKnightCommissionGameplayTest.Session()){
            KOMEKnightCommission a=field(s,Type.WAR_PLANS,3);a.encounterCreated=true;LOTREntityNPC npc=KOMEKnightCommissionGameplayTest.actor(s,a,Role.ENEMY);a.targetId=npc.getUniqueID().toString();s.f.player.inventory.mainInventory[0]=KOMEKnightEspionage.steal(s.f.player,npc,a);
            assertFalse(KOMEKnightEspionage.escaped(s.f.player,a));s.f.player.posX+=160;assertTrue(KOMEKnightEspionage.escaped(s.f.player,a));s.f.player.inventory.mainInventory[0]=null;assertFalse(KOMEKnightEspionage.escaped(s.f.player,a));
        }
    }
    @Test public void realLiegeMustBePresentAndIsNotAnEncounterActor()throws Exception{
        try(KOMEKnightCommissionGameplayTest.Session s=new KOMEKnightCommissionGameplayTest.Session()){
            LOTREntityNPC liege=KOMEKnightCommissionInteractionTest.captain(s);KOMEKnightCommission a=KOMEKnightCommissionInteractionTest.offered(s,liege,Type.PROTECT_LIEGE);KOMEKnightFieldService.profile(a,2);a.stage=Stage.ACTIVE;a.x=liege.posX;a.z=liege.posZ;a.y=65;
            assertTrue(KOMEKnightFieldService.liegeAvailable(s.f.player,a));assertSame(liege,KOMEKnightFieldService.attackTarget(s.f.player,a));assertNull(KOMEProgressionEncounterMarker.read(liege));s.f.world.loadedEntityList.remove(liege);assertFalse(KOMEKnightFieldService.liegeAvailable(s.f.player,a));assertFalse(KOMEKnightCommissionService.activate(s.f.player,a));
        }
    }
    @Test public void temporarySoldierLossDoesNotFailMissionAndCleanupDismissesAll()throws Exception{
        try(KOMEKnightCommissionGameplayTest.Session s=new KOMEKnightCommissionGameplayTest.Session()){
            KOMEKnightCommission a=field(s,Type.FIELD_COMMAND,2);a.encounterCreated=true;LOTREntityNPC guard=KOMEKnightCommissionGameplayTest.actor(s,a,Role.GUARD);KOMEKnightFieldService.follow(guard,s.f.player);assertTrue(KOMEKnightCommissionService.activeEscort(s.f.data,guard));
            KOMEKnightCommissionService.npcDeath(s.f.data,s.f.world,guard.getUniqueID().toString());assertEquals(Stage.ACTIVE,a.stage);KOMEKnightCommissionService.cancel(s.f.world,s.f.player.id,s.p);assertFalse(guard.hiredNPCInfo.isActive);assertTrue(s.f.data.hiredUnits.isEmpty());assertTrue(s.f.data.armyCompanies.isEmpty());
        }
    }
    @Test public void reliefNeedsBothActualDeliveryAndCombat()throws Exception{
        try(KOMEKnightCommissionGameplayTest.Session s=new KOMEKnightCommissionGameplayTest.Session()){
            KOMEKnightCommission a=field(s,Type.RELIEF,2);a.encounterCreated=true;a.goods.add(new Goods(String.valueOf(net.minecraft.item.Item.itemRegistry.getNameForObject(Items.bread)),0,6));LOTREntityNPC host=KOMEKnightCommissionGameplayTest.actor(s,a,Role.BENEFICIARY),enemy=KOMEKnightCommissionGameplayTest.actor(s,a,Role.ENEMY);s.f.player.inventory.mainInventory[0]=new ItemStack(Items.bread,6);
            assertTrue(KOMEKnightCommissionService.deliverRelief(s.f.player,host));assertEquals(Stage.ACTIVE,a.stage);kill(s,a,enemy);assertEquals(Stage.READY_TO_REPORT,a.stage);
        }
    }
    @Test public void staffSessionPersistsButCannotPromoteOrPolluteEarnedHistory()throws Exception{
        try(KOMEKnightCommissionGameplayTest.Session s=new KOMEKnightCommissionGameplayTest.Session()){
            LOTREntityNPC liege=KOMEKnightCommissionInteractionTest.captain(s);s.p.getKnightService().testing(true);KOMEKnightCommission a=KOMEKnightCommissionInteractionTest.offered(s,liege,Type.BORDER_INCURSION);a.adminForced=true;a.stage=Stage.READY_TO_REPORT;
            s.reload();assertTrue(s.p.getKnightService().testing());assertTrue(KOMEKnightCommissionService.acceptOrReport(s.f.player,liege));assertNull(s.p.getKnightService().assignment());assertTrue(s.p.getKnightService().history().isEmpty());assertEquals(KOMEProgressionRank.KNIGHT,s.p.getCanonicalRank());
            assertFalse(KOMEProgressionDevelopment.commission(s.f.player,new String[]{"start","BORDER_INCURSION","3"}).isEmpty());assertNull(s.p.getKnightService().assignment());
            s.f.player.operator=true;assertTrue(KOMEProgressionDevelopment.commission(s.f.player,new String[]{"list"}).get(0).contains("WAR_PLANS"));KOMEProgressionDevelopment.commission(s.f.player,new String[]{"end"});assertFalse(s.p.getKnightService().testing());
        }
    }
    @Test public void finishedFieldEvidenceAndAuthenticPropertySurviveLegitimateLiegeLoss()throws Exception{
        try(KOMEKnightCommissionGameplayTest.Session s=new KOMEKnightCommissionGameplayTest.Session()){
            LOTREntityNPC old=KOMEKnightCommissionInteractionTest.captain(s);KOMEKnightCommission a=KOMEKnightCommissionInteractionTest.offered(s,old,Type.STOLEN_GOODS);a.tier=2;a.stage=Stage.READY_TO_REPORT;a.threatResolved=true;a.goods.add(new Goods(String.valueOf(net.minecraft.item.Item.itemRegistry.getNameForObject(Items.iron_ingot)),0,1));s.f.player.inventory.mainInventory[0]=KOMEKnightCommissionService.property(a,s.f.player.id);
            KOMEProgressionEncounterCleanup.lostSuperior(s.f.world,s.f.player.id,s.p);s.p.getSerfKnightProgression().handleLiegeDeath(false);old.setDead();KOMEKnightCommissionService.npcDeath(s.f.data,s.f.world,old.getUniqueID().toString());KOMEKnightCommissionService.tickPlayer(s.f.player);assertEquals(Stage.READY_TO_REPORT,a.stage);assertNotNull(s.f.player.inventory.mainInventory[0]);
            LOTREntityNPC replacement=KOMEKnightCommissionInteractionTest.captain(s);s.reload();assertTrue(KOMEKnightCommissionService.acceptOrReport(s.f.player,replacement));assertNull(s.f.player.inventory.mainInventory[0]);assertEquals(1,s.p.getKnightService().qualifyingTypes("rohan").size());assertEquals(a.liege.entityUuid,s.p.getKnightService().history().get(0).liege.entityUuid);
        }
    }
    @Test public void infiltrationUsesNarrowNativeSightAndAlarmsRemainSaved()throws Exception{
        try(KOMEKnightCommissionGameplayTest.Session s=new KOMEKnightCommissionGameplayTest.Session()){
            KOMEKnightCommission a=field(s,Type.WAR_PLANS,3);a.encounterCreated=true;LOTREntityNPC target=KOMEKnightCommissionGameplayTest.actor(s,a,Role.ENEMY);a.targetId=target.getUniqueID().toString();s.f.player.setSneaking(true);
            KOMEKnightEspionage.Sight old=KOMEKnightEspionage.sight;
            try{KOMEKnightEspionage.sight=(npc,player)->false;assertTrue(KOMEKnightEspionage.interceptTarget(target,s.f.player));assertFalse(a.detected);
                LOTREntityNPC unrelated=s.npc(LOTRFaction.MORDOR);assertFalse(KOMEKnightEspionage.interceptTarget(unrelated,s.f.player));
                KOMEKnightEspionage.sight=(npc,player)->true;assertFalse(KOMEKnightEspionage.interceptTarget(target,s.f.player));assertTrue(a.detected);s.reload();assertTrue(s.p.getKnightService().assignment().detected);assertFalse(KOMEKnightEspionage.interceptTarget(target,s.f.player));
            }finally{KOMEKnightEspionage.sight=old;}
        }
    }
    @Test public void staffRewardMarkerOnlyRemovesNativeRewardsAndNeverGrantsAuthority()throws Exception{
        try(KOMEKnightCommissionGameplayTest.Session s=new KOMEKnightCommissionGameplayTest.Session()){
            LOTREntityNPC npc=s.npc(LOTRFaction.MORDOR);assertEquals(5,KOMEProgressionDevelopment.experience(5,npc));assertEquals(5,KOMEProgressionDevelopment.alignment(5,npc),0);
            KOMEProgressionEncounterMarker.markTesting(npc);assertEquals(0,KOMEProgressionDevelopment.experience(5,npc));assertEquals(0,KOMEProgressionDevelopment.alignment(5,npc),0);assertNull(KOMEProgressionDevelopment.killAchievement(lotr.common.LOTRAchievement.killOrc,npc));assertNull(KOMEProgressionEncounterMarker.read(npc));assertTrue(s.f.data.hiredUnits.isEmpty());
        }
    }
}
