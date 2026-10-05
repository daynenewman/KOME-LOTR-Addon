package kome.common.data;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import kome.common.KOMEAccessFixture;
import lotr.common.LOTRLevelData;
import lotr.common.LOTRPlayerData;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.entity.npc.LOTREntityQuestInfo;
import lotr.common.entity.npc.LOTRHiredNPCInfo;
import lotr.common.fac.LOTRFaction;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.event.entity.living.LivingEvent;
import org.junit.Test;
import static org.junit.Assert.*;

/** Executes live conferral, offer acceptance, hiring enforcement and physical cleanup. */
public class KOMESerfKnightStabilizationTest {
    @Test public void completedTrialAndDeadLiegeStillReceiveActualGiftAndKnightRank()throws Exception {
        try(KOMEProgressionFollowupTest.NativeItems items=new KOMEProgressionFollowupTest.NativeItems();
                KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()) {
            s.ready();TrialCaptain captain=captain(s);KOMEProgressionNpcRef master=s.state.getSerfdomMaster();
            s.state.setLiege(KOMEProgressionNpcRankService.referenceOf(captain));
            s.state.setTrial(KOMESerfKnightTrialAssignment.create(KOMESerfKnightTrial.forId("escort"),s.state.getLiege(),10,0));
            assertTrue(KOMESerfKnightService.markTrialObjectiveComplete(s.state).success);
            assertTrue(KOMESerfKnightService.handleNpcDeath(s.state,captain.getUniqueID().toString(),false,11));
            reload(s);assertFalse(s.state.hasLiege());assertTrue(s.state.isTrialCompleted());
            assertTrue(KOMESerfdomMasterService.conferKnighthood(s.f.player,s.f.data,s.master).success);
            assertEquals(KOMEProgressionRank.KNIGHT,s.p.getCanonicalRank());
            assertTrue(s.f.player.inventory.mainInventory[0].getItem() instanceof lotr.common.item.LOTRItemPouch);
            assertFalse(s.state.getSerfdomMaster().isSet());assertTrue(s.state.getFormerMaster().hasSameIdentity(master));
            KOMEPlayerProgression restored=new KOMEPlayerProgression();restored.readFromNBT(s.p.writeToNBT());
            assertEquals(master.entityUuid,restored.getSerfKnightProgression().getFormerMaster().entityUuid);
            assertEquals(master.factionKey,restored.getSerfKnightProgression().getFormerMaster().factionKey);
            assertEquals(master.displayName,restored.getSerfKnightProgression().getFormerMaster().displayName);
            assertFalse(restored.getSerfKnightProgression().getSerfdomMaster().isSet());
            assertFalse(KOMEProgressionNpcRoles.protects(s.f.data,UUID.fromString(master.entityUuid)));
            assertFalse(KOMESerfdomMasterService.conferKnighthood(s.f.player,s.f.data,s.master).success);
            assertFalse(KOMEProgressionNpcInteractionService.interact(s.f.player,s.f.data,s.master));
            assertTrue(s.f.player.messages.toString().contains("faithful service"));
        }
    }

    @Test public void completedServiceSurvivesMasterDeathAndReplacementCanConfer()throws Exception {
        try(KOMEProgressionFollowupTest.NativeItems items=new KOMEProgressionFollowupTest.NativeItems();
                KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()) {
            s.ready();assertTrue(KOMESerfKnightService.handleNpcDeath(s.state,s.master.getUniqueID().toString(),false,11));
            reload(s);assertTrue(s.state.isTrialCompleted());assertFalse(s.state.getSerfdomMaster().isSet());
            KOMEProgressionFixesTest.Worker replacement=KOMEAccessFixture.allocate(KOMEProgressionFixesTest.Worker.class);
            replacement.worldObj=s.f.world;replacement.setUniqueID(UUID.randomUUID());
            replacement.posX=s.master.posX;replacement.posY=64;replacement.posZ=s.master.posZ;s.f.world.loadedEntityList.add(replacement);
            assertTrue(KOMESerfdomMasterService.serve(s.f.player,s.f.data,replacement).success);
            assertTrue(KOMESerfdomMasterService.conferKnighthood(s.f.player,s.f.data,replacement).success);
            assertEquals(KOMEProgressionRank.KNIGHT,s.p.getCanonicalRank());
            assertEquals(replacement.getUniqueID().toString(),s.state.getFormerMaster().entityUuid);
        }
    }

    @Test public void missingOrVoluntarilyReleasedWitnessDoesNotEraseCompletedTrial()throws Exception {
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()) {
            s.ready();assertTrue(KOMESerfKnightService.leaveLiege(s.p).success);
            reload(s);assertTrue(s.state.isTrialCompleted());assertFalse(s.state.hasLiege());
            assertTrue(KOMESerfdomMasterService.conferKnighthood(s.f.data,s.f.player.id,s.state.getSerfdomMaster(),"rohan",150).success);
        }
    }

    @Test public void incompleteLiegeDeathClearsOldTrialAndAcceptsReplacement()throws Exception {
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()) {
            duties(s.state);TrialCaptain old=captain(s);
            assertTrue(KOMESerfKnightService.acceptStandingTrial(s.state,s.f.data,old,new Random(4),10,s.f.player.id).success);
            String token=s.state.getTrialAssignment().assignmentToken;
            KOMEProgressionEncounterCleanup.cleanup(s.f.player,s.p);
            assertTrue(KOMESerfKnightService.handleNpcDeath(s.state,old.getUniqueID().toString(),false,11));
            reload(s);assertFalse(s.state.hasLiege());assertEquals("",s.state.getTrialId());assertNull(s.state.getTrialAssignment());
            assertFalse(s.state.hasActiveAssignment());
            TrialCaptain replacement=captain(s);
            assertTrue(KOMEProgressionOfferBridge.canRequestStandingTrialFrom(s.f.player,replacement));
            assertTrue(KOMESerfKnightService.acceptStandingTrial(s.state,s.f.data,replacement,new Random(4),11,s.f.player.id).success);
            assertNotEquals(token,s.state.getTrialAssignment().assignmentToken);
            assertEquals(KOMEProgressionRank.SERF,s.p.getCanonicalRank());
        }
    }

    @Test public void knightReplacementUsesNpcOfferAndRestoresOfferingsWithoutRetrial()throws Exception {
        try(KOMEProgressionFollowupTest.NativeItems items=new KOMEProgressionFollowupTest.NativeItems();
                KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()) {
            s.p.setCanonicalRank(KOMEProgressionRank.KNIGHT);s.state.retireSerfdomMasterAfterPromotion();
            TrialCaptain captain=captain(s);
            assertTrue(KOMEProgressionOfferBridge.prepareStandingTrialInteraction(s.f.player,captain));
            KOMELiegeOfferQuest offer=(KOMELiegeOfferQuest)captain.questInfo.getOfferFor(s.f.player);
            assertTrue(offer.isReplacementOffer());assertFalse(offer.isStandingTrialOffer());
            KOMEProgressionOfferBridge.registerQuestType();NBTTagCompound savedOffer=new NBTTagCompound();offer.writeToNBT(savedOffer);
            KOMELiegeOfferQuest restoredOffer=new KOMELiegeOfferQuest(LOTRLevelData.getData(s.f.player));restoredOffer.readFromNBT(savedOffer);
            assertTrue(restoredOffer.isReplacementOffer());assertTrue(restoredOffer.canPlayerAccept(s.f.player));
            assertTrue(KOMEProgressionOfferBridge.handleResponse(captain.questInfo,s.f.player,true));
            assertTrue(s.state.getLiege().hasSameIdentity(KOMEProgressionNpcRankService.referenceOf(captain)));
            assertEquals(KOMEProgressionRank.KNIGHT,s.p.getCanonicalRank());assertNull(s.state.getTrialAssignment());
            assertFalse(s.state.getSerfdomMaster().isSet());reload(s);
            assertTrue(s.state.hasLiege());
            java.lang.reflect.Field bounds=net.minecraft.entity.Entity.class.getDeclaredField("boundingBox");bounds.setAccessible(true);
            bounds.set(s.f.player,net.minecraft.util.AxisAlignedBB.getBoundingBox(s.master.posX-1,63,s.master.posZ-1,s.master.posX+1,66,s.master.posZ+1));
            KOMEProgressionLords.openOfferings(s.f.player);
            assertTrue(s.f.player.openedInventory instanceof KOMEProgressionOfferingInventory);
            assertFalse(KOMEProgressionOfferBridge.canReplaceLiegeFrom(s.f.player,captain));
        }
    }

    @Test public void replacementRejectsWrongFactionLowAlignmentAndInaccessibleCaptain()throws Exception {
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()) {
            s.p.setCanonicalRank(KOMEProgressionRank.KNIGHT);TrialCaptain captain=captain(s);
            captain.accessible=false;assertFalse(KOMESerfKnightRelationshipService.establishLiege(s.f.player,s.f.data,captain).success);
            captain.accessible=true;alignment(s,149);assertFalse(KOMEProgressionOfferBridge.canReplaceLiegeFrom(s.f.player,captain));
            alignment(s,150);s.f.pledge(LOTRFaction.GONDOR);assertFalse(KOMEProgressionOfferBridge.canReplaceLiegeFrom(s.f.player,captain));
            assertFalse(s.state.hasLiege());
        }
    }

    @Test public void escortSurvivesHiringEnforcementUnloadReloadAndReconnect()throws Exception {
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()) {
            Charge charge=escort(s);new KOMEEvents().onLivingUpdate(new LivingEvent.LivingUpdateEvent(charge));
            assertFalse(charge.isDead);assertTrue(charge.hiredNPCInfo.isActive);assertTrue(s.f.data.hiredUnits.isEmpty());
            assertFalse(KOMEProgressionPermissions.has(s.f.player,KOMEProgressionPermissions.HIRE_UNITS));
            NBTTagCompound marker=(NBTTagCompound)charge.getEntityData().copy();UUID id=charge.getUniqueID();
            s.f.world.loadedEntityList.remove(charge);KOMESerfKnightEscortService.tickPlayer(s.f.player);
            assertEquals(KOMESerfKnightTrialAssignment.Stage.ACTIVE,s.state.getTrialAssignment().stage);
            reload(s);s.f.world.playerEntities.clear();
            Charge loaded=charge(s,id);KOMEProgressionEncounterMarker.mark(loaded.getEntityData(),"escort",s.f.player.id,s.state.getTrialAssignment().assignmentToken);
            assertEquals(marker,loaded.getEntityData());
            KOMESerfKnightEscortService.reconcileLoadedNpc(s.f.data,loaded);
            new KOMEEvents().onLivingUpdate(new LivingEvent.LivingUpdateEvent(loaded));
            assertFalse(loaded.isDead);assertTrue(loaded.hiredNPCInfo.isActive);
            s.f.world.playerEntities.add(s.f.player);s.f.world.loadedEntityList.add(loaded);
            new KOMEEvents().onLivingUpdate(new LivingEvent.LivingUpdateEvent(loaded));assertTrue(s.f.data.hiredUnits.isEmpty());
        }
    }

    @Test public void ordinaryUnauthorizedHireAndForgedEscortMarkerRemainBlocked()throws Exception {
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()) {
            Charge ordinary=charge(s,UUID.randomUUID());
            new KOMEEvents().onLivingUpdate(new LivingEvent.LivingUpdateEvent(ordinary));assertTrue(ordinary.isDead);
            Charge fake=charge(s,UUID.randomUUID());KOMEProgressionEncounterMarker.mark(fake,"escort",s.f.player.id,UUID.randomUUID().toString());
            new KOMEEvents().onLivingUpdate(new LivingEvent.LivingUpdateEvent(fake));
            assertFalse(fake.hiredNPCInfo.isActive);assertNull(KOMEProgressionEncounterMarker.read(fake));assertTrue(s.f.data.hiredUnits.isEmpty());
        }
    }

    @Test public void unloadedCancelledEscortReleasesItsRoleWhenLoadedAgain()throws Exception {
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()) {
            Charge charge=escort(s);s.f.world.loadedEntityList.remove(charge);
            KOMEProgressionEncounterCleanup.cleanup(s.f.player,s.p);assertTrue(KOMESerfKnightService.leaveLiege(s.p).success);
            reload(s);s.f.world.loadedEntityList.add(charge);
            KOMESerfKnightEscortService.reconcileLoadedNpc(s.f.data,charge);assertReleased(s,charge);
            assertNull(s.state.getTrialAssignment());assertFalse(charge.isDead);
        }
    }

    @Test public void escortCompletionAndFailureReleaseFollowerMarkerAndLease()throws Exception {
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()) {
            Charge charge=escort(s);charge.posX+=256;s.f.player.posX=charge.posX;
            KOMESerfKnightEscortService.tickPlayer(s.f.player);assertTrue(s.state.isTrialCompleted());assertReleased(s,charge);
        }
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()) {
            Charge charge=escort(s);KOMESerfKnightEscortService.handleTargetDeath(s.f.data,charge.getUniqueID().toString(),s.f.world);
            assertEquals(KOMESerfKnightTrialAssignment.Stage.FAILED,s.state.getTrialAssignment().stage);assertReleased(s,charge);
            assertTrue(KOMESerfKnightService.leaveLiege(s.p).success);assertFalse(s.state.hasActiveAssignment());
        }
    }

    @Test public void leavingMasterCleansEscortAndMakesTrialActivatableAgain()throws Exception {
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()) {
            Charge charge=escort(s);KOMEProgressionEncounterCleanup.cleanup(s.f.player,s.p);
            assertTrue(KOMESerfKnightService.leaveSerfdomMaster(s.state).success);
            KOMEProgressionNpcRoles.syncPlayer(s.f.data,s.f.player.id);assertReleased(s,charge);
            assertEquals(KOMESerfKnightTrialAssignment.Stage.ASSIGNED,s.state.getTrialAssignment().stage);
            assertTrue(KOMESerfKnightService.allDutiesComplete(s.state));assertTrue(s.state.hasLiege());
        }
    }

    @Test public void courierClicksNeverReplaceAndExplicitRequestsRequireMissingDispatch()throws Exception {
        try(KOMEProgressionFollowupTest.NativeItems items=new KOMEProgressionFollowupTest.NativeItems();
                KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()) {
            s.courier();assertTrue(KOMECourierIssuance.initial(s.f.player,s.f.data,s.master));
            ItemStack old=s.latest().copy();s.f.player.inventory.mainInventory[0]=old;
            assertTrue("issued inventory dispatch must match",KOMECourierService.hasMessage(s.f.player,s.active(),s.state.getSerfdomMaster()));
            s.f.world.testWorldTime=s.active().nextReplacementWorldTime;
            assertTrue(s.interact());assertTrue(s.replace());assertEquals(0,s.active().replacements);assertEquals(1,s.drops());
            s.loseDispatch();assertTrue(s.interact());assertEquals(0,s.active().replacements);
            assertTrue(s.replace());assertEquals(1,s.active().replacements);assertEquals(2,s.drops());
            assertFalse(KOMECourierService.matching(old,s.active(),s.f.player,s.state.getSerfdomMaster()));
            assertTrue(s.replace());assertEquals(1,s.active().replacements);
            s.loseDispatch();s.f.world.testWorldTime=s.active().nextReplacementWorldTime-1;
            assertTrue(s.replace());assertEquals(1,s.active().replacements);
        }
    }

    @Test public void missingCourierInteractionOpensExistingMenuAndOnlyButtonRequestReplaces()throws Exception {
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()) {
            s.courier();assertTrue(KOMECourierIssuance.initial(s.f.player,s.f.data,s.master));s.loseDispatch();
            s.f.world.testWorldTime=s.active().nextReplacementWorldTime;s.f.network.messages.clear();
            assertTrue(KOMEProgressionNpcInteractionService.interact(s.f.player,s.f.data,s.master));
            assertEquals(0,s.active().replacements);assertEquals(1,s.drops());
            assertTrue(s.f.network.messages.stream().anyMatch(packet->packet instanceof kome.common.network.KOMEPacketSerfdomMasterMenu));
            new kome.common.network.KOMEPacketSerfdomMasterAction.Handler().onMessage(
                new kome.common.network.KOMEPacketSerfdomMasterAction(s.master.getEntityId(),kome.common.network.KOMEPacketSerfdomMasterAction.REPLACE_COURIER_MESSAGE),s.f.context);
            assertEquals(1,s.active().replacements);assertEquals(2,s.drops());
        }
    }

    @Test public void loadedInvalidLiegeIsReleasedButAbsentChunksAreNotDeath()throws Exception {
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()) {
            s.ready();assertFalse(KOMEProgressionRelationshipLifecycle.reconcileLoadedRelationships(s.f.data,s.f.player));
            TrialCaptain captain=captain(s);s.state.setLiege(KOMEProgressionNpcRankService.referenceOf(captain));
            captain.hiredNPCInfo.isActive=true;
            assertTrue(KOMEProgressionRelationshipLifecycle.reconcileLoadedRelationships(s.f.data,s.f.player));
            assertFalse(s.state.hasLiege());assertTrue(s.state.isTrialCompleted());
        }
    }

    @Test public void formerMasterAbsentInOldSavesDefaultsToEmpty() {
        KOMESerfKnightProgression state=new KOMESerfKnightProgression();state.readFromNBT(new NBTTagCompound());
        assertFalse(state.getFormerMaster().isSet());assertFalse(state.getSerfdomMaster().isSet());
    }

    private static void reload(KOMEProgressionFollowupTest.Session s){s.p.readFromNBT(s.p.writeToNBT());KOMEProgressionNpcRoles.syncPlayer(s.f.data,s.f.player.id);}
    private static void duties(KOMESerfKnightProgression state){for(KOMESerfKnightDutyType type:KOMESerfKnightDutyType.values()){state.assignDuty(type,null);state.completeDuty(type);}}
    private static void alignment(KOMEProgressionFollowupTest.Session s,float value)throws Exception {
        java.lang.reflect.Field field=LOTRPlayerData.class.getDeclaredField("alignments");field.setAccessible(true);
        ((Map)field.get(LOTRLevelData.getData(s.f.player))).put(LOTRFaction.ROHAN,value);
    }
    private static TrialCaptain captain(KOMEProgressionFollowupTest.Session s)throws Exception {
        TrialCaptain npc=KOMEAccessFixture.allocate(TrialCaptain.class);
        npc.worldObj=s.f.world;npc.setUniqueID(UUID.randomUUID());npc.posX=s.master.posX;npc.posY=64;npc.posZ=s.master.posZ;
        java.lang.reflect.Field watcher=net.minecraft.entity.Entity.class.getDeclaredField("dataWatcher");watcher.setAccessible(true);
        net.minecraft.entity.DataWatcher dataWatcher=new net.minecraft.entity.DataWatcher(npc);dataWatcher.addObject(10,"");watcher.set(npc,dataWatcher);
        npc.accessible=true;npc.questInfo=new InertQuestInfo(npc);npc.hiredNPCInfo=new LOTRHiredNPCInfo(npc);
        s.f.world.loadedEntityList.add(npc);alignment(s,150);return npc;
    }
    private static Charge escort(KOMEProgressionFollowupTest.Session s)throws Exception {
        duties(s.state);TrialCaptain liege=captain(s);
        s.state.setLiege(KOMEProgressionNpcRankService.referenceOf(liege));
        s.state.setTrial(KOMESerfKnightTrialAssignment.create(KOMESerfKnightTrial.forId("escort"),s.state.getLiege(),10,0));
        Charge charge=charge(s,UUID.randomUUID());charge.hiredNPCInfo.isActive=false;s.f.world.loadedEntityList.add(charge);
        assertTrue(KOMESerfKnightEscortService.activate(s.f.player,s.p,liege));return charge;
    }
    private static Charge charge(KOMEProgressionFollowupTest.Session s,UUID id)throws Exception {
        Charge npc=KOMEAccessFixture.allocate(Charge.class);npc.worldObj=s.f.world;npc.setUniqueID(id);
        java.lang.reflect.Field equipment=net.minecraft.entity.EntityLiving.class.getDeclaredField("equipment");equipment.setAccessible(true);
        equipment.set(npc,new ItemStack[5]);
        npc.posX=s.master.posX;npc.posY=64;npc.posZ=s.master.posZ;
        npc.hiredNPCInfo=new InertFollower(npc);npc.hiredNPCInfo.setHiringPlayer(s.f.player);
        npc.hiredNPCInfo.setTask(LOTRHiredNPCInfo.Task.WARRIOR);npc.hiredNPCInfo.isActive=true;return npc;
    }
    private static void assertReleased(KOMEProgressionFollowupTest.Session s,Charge npc) {
        assertFalse(npc.hiredNPCInfo.isActive);assertNull(KOMEProgressionEncounterMarker.read(npc));
        assertFalse(KOMEProgressionNpcRoles.protects(s.f.data,npc.getUniqueID()));assertTrue(s.f.data.hiredUnits.isEmpty());
    }
    public static class Charge extends lotr.common.entity.npc.LOTREntityRohanMan {
        private Charge(){super(null);}
        @Override public String getNPCName(){return "Charge";}
        @Override public boolean isChild(){return false;}
        @Override public boolean isEntityAlive(){return !isDead;}
    }
    public static class TrialCaptain extends lotr.common.entity.npc.LOTREntityRohirrimMarshal {
        boolean accessible;
        private TrialCaptain(){super(null);}
        @Override public boolean isEntityAlive(){return !isDead;}
        @Override public boolean isChild(){return false;}
        @Override public String getNPCName(){return "Captain";}
        @Override public boolean canTradeWith(net.minecraft.entity.player.EntityPlayer player){return accessible;}
        @Override public lotr.common.quest.LOTRMiniQuest createMiniQuest(){return new Template();}
    }
    private static class Template extends lotr.common.quest.LOTRMiniQuest {
        Template(){super(null);questGroup=lotr.common.quest.LOTRMiniQuestFactory.ROHAN;
            speechBankStart="start";speechBankProgress="progress";speechBankComplete="complete";
            speechBankTooMany="many";quoteStart="Welcome";quoteComplete="Thank you";}
        public String getQuestObjective(){return "Service";}
        public String getObjectiveInSpeech(){return "serve";}
        public String getProgressedObjectiveInSpeech(){return "served";}
        public String getQuestProgress(){return "Service";}
        public String getQuestProgressShorthand(){return "Service";}
        public float getCompletionFactor(){return 0;}
        public ItemStack getQuestIcon(){return null;}
        public float getAlignmentBonus(){return 0;}
        public int getCoinBonus(){return 0;}
    }
    private static class InertFollower extends LOTRHiredNPCInfo {
        net.minecraft.entity.player.EntityPlayer owner;
        Task task;
        InertFollower(LOTREntityNPC npc){super(npc);}
        @Override public void setHiringPlayer(net.minecraft.entity.player.EntityPlayer player){owner=player;}
        @Override public net.minecraft.entity.player.EntityPlayer getHiringPlayer(){return owner;}
        @Override public UUID getHiringPlayerUUID(){return owner==null?null:owner.getUniqueID();}
        @Override public void setTask(Task value){task=value;}
        @Override public Task getTask(){return task;}
        @Override public void ready(){}
        @Override public boolean isHalted(){return false;}
        @Override public void dismissUnit(boolean disband){isActive=false;}
    }
    private static class InertQuestInfo extends LOTREntityQuestInfo {
        InertQuestInfo(LOTREntityNPC npc){super(npc);}
        @Override public void sendData(net.minecraft.entity.player.EntityPlayerMP player){}
    }
}
