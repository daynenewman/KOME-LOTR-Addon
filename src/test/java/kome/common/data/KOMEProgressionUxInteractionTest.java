package kome.common.data;

import java.util.*;
import lotr.common.LOTRDimension;
import lotr.common.fac.LOTRFaction;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import kome.common.KOMEAccessFixture;
import org.junit.Test;
import static org.junit.Assert.*;
import static kome.common.data.KOMEKnightCommission.*;

public class KOMEProgressionUxInteractionTest {
    @Test public void wandererGuidanceIsDerivedAndSurvivesReload()throws Exception {
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()){
            s.p.setCanonicalRank(KOMEProgressionRank.WANDERER);s.state.reset();
            assertEquals("Find a Master to serve",KOMEProgressionTrackerSnapshot.project(s.f.player,s.p).objective);
            s.p.readFromNBT(s.p.writeToNBT());assertTrue(KOMEProgressionTrackerSnapshot.project(s.f.player,s.p).visible);
            assertTrue(KOMESerfdomMasterService.serve(s.f.player,s.f.data,s.master).success);
            assertFalse(KOMEProgressionTrackerSnapshot.project(s.f.player,s.p).objective.contains("Find a Master"));
        }
    }
    @Test public void giftIsOptionalPhysicalAndOnceEvenWithFullInventory()throws Exception {
        try(KOMEProgressionFollowupTest.NativeItems items=new KOMEProgressionFollowupTest.NativeItems();KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()){
            s.ready();assertTrue(KOMESerfKnightService.canPromote(s.state,150));
            for(KOMEProgressionRankSummary.Requirement r:KOMEProgressionRankSummary.project(s.p,150).requirements)assertFalse(r.label.contains("Gift"));
            Arrays.fill(s.f.player.inventory.mainInventory,new ItemStack(Items.bread));
            KOMEPartingGiftService.tickPlayer(s.f.player);assertEquals(0,s.drops());assertFalse(s.state.hasPartingGift());
            assertTrue(KOMEProgressionNpcInteractionService.interact(s.f.player,s.f.data,s.master));assertEquals(1,s.drops());assertTrue(s.state.hasPartingGift());
            assertSame(lotr.common.LOTRMod.pouch,s.latest().getItem());assertEquals(1,s.latest().getItemDamage());
            s.p.readFromNBT(s.p.writeToNBT());KOMEPartingGiftService.tickPlayer(s.f.player);
            assertTrue(KOMESerfdomMasterService.conferKnighthood(s.f.player,s.f.data,s.master).success);
            assertEquals(1,s.drops());assertEquals(1,Collections.frequency(s.f.world.playedSounds,"random.pop"));
            assertFalse(KOMESerfdomMasterService.conferKnighthood(s.f.player,s.f.data,s.master).success);
            assertEquals(KOMEProgressionRank.KNIGHT,s.p.getCanonicalRank());
        }
    }
    @Test public void failedGiftSpawnDoesNotBlockPromotionAndFormerMasterCanIssueLater()throws Exception {
        try(KOMEProgressionFollowupTest.NativeItems items=new KOMEProgressionFollowupTest.NativeItems();KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()){
            s.ready();s.f.world.spawnSucceeds=false;
            assertTrue(KOMESerfdomMasterService.conferKnighthood(s.f.player,s.f.data,s.master).success);
            assertFalse(s.state.hasPartingGift());assertNotNull(s.state.getPendingPartingGift());assertEquals(0,s.drops());
            s.p.readFromNBT(s.p.writeToNBT());s.f.world.spawnSucceeds=true;
            assertTrue(KOMEPartingGiftService.drop(s.f.player,s.f.data,s.master));assertEquals(1,s.drops());
            assertFalse(KOMEPartingGiftService.drop(s.f.player,s.f.data,s.master));
        }
    }
    @Test public void unissuedGiftRetainsFormerIdentityButHasNoOrphanMarkerAfterDeathOrPledgeChange()throws Exception {
        try(KOMEProgressionFollowupTest.NativeItems items=new KOMEProgressionFollowupTest.NativeItems();KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()){
            s.ready();s.f.world.spawnSucceeds=false;assertTrue(KOMESerfdomMasterService.conferKnighthood(s.f.player,s.f.data,s.master).success);
            s.f.world.spawnSucceeds=true;s.f.pledge(LOTRFaction.GONDOR);
            assertFalse(KOMEPartingGiftService.drop(s.f.player,s.f.data,s.master));
            KOMEVisualLocationService.syncIfChanged(s.f.player,s.f.data,s.p,true);
            kome.common.network.KOMEPacketVisualMarkers packet=(kome.common.network.KOMEPacketVisualMarkers)s.f.network.messages.get(s.f.network.messages.size()-1);
            assertFalse(packet.markers.stream().anyMatch(m->m.role==KOMEVisualMarker.Role.MASTER_GIFT));
            s.f.pledge(LOTRFaction.ROHAN);String master=s.state.getFormerMaster().entityUuid;
            assertTrue(KOMESerfKnightService.handleNpcDeath(s.state,master,false,31));s.p.readFromNBT(s.p.writeToNBT());
            assertEquals(master,s.state.getFormerMaster().entityUuid);assertNull(s.state.getPendingPartingGift());assertFalse(s.state.hasPartingGift());
            assertFalse(KOMEVisualLocationService.markersFor(s.p).stream().anyMatch(m->m.role==KOMEVisualMarker.Role.MASTER_GIFT));
            assertFalse(KOMEPartingGiftService.drop(s.f.player,s.f.data,s.master));assertEquals(0,s.drops());assertEquals(KOMEProgressionRank.KNIGHT,s.p.getCanonicalRank());
        }
    }
    @Test public void progressionDropsSoundOnlyOnSuccessfulServerIssuance()throws Exception {
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()){
            assertNotNull(KOMEProgressionItemDrops.drop(s.f.world,0,65,0,new ItemStack(Items.paper)));
            assertEquals(Arrays.asList("random.pop"),s.f.world.playedSounds);
            s.f.world.spawnSucceeds=false;assertNull(KOMEProgressionItemDrops.drop(s.f.world,0,65,0,new ItemStack(Items.paper)));
            s.f.world.isRemote=true;assertNull(KOMEProgressionItemDrops.drop(s.f.world,0,65,0,new ItemStack(Items.paper)));
            assertEquals(1,s.f.world.playedSounds.size());
        }
    }
    static KOMESerfKnightTrialAssignment escort(KOMESerfKnightProgression state,KOMEProgressionNpcRef liege){
        state.setLiege(liege);state.setTrial(KOMESerfKnightTrialAssignment.create(KOMESerfKnightTrial.forId("escort"),liege,30,0));return state.getTrialAssignment();
    }
    @Test public void escortChoosesNamedWaypointThenKeepsCoordinatesAcrossReload()throws Exception {
        try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){
            KOMEProgressionHardeningGeographyTest.territory(f);f.s.chunks=false;
            KOMESerfKnightProgression state=f.s.p.getSerfKnightProgression();
            f.s.original.ready();f.s.p.setCanonicalRank(KOMEProgressionRank.SERF);
            lotr.common.world.map.LOTRWaypoint edoras=lotr.common.world.map.LOTRWaypoint.EDORAS;
            KOMEProgressionNpcRef liege=new KOMEProgressionNpcRef(f.liege.getUniqueID().toString(),"Liege","rohan",f.s.f.player.dimension,edoras.getXCoord()+900,65,edoras.getZCoord());
            KOMEProgressionGameplayFixture.record(f.s.f.world,edoras.getXCoord(),edoras.getZCoord(),"rohan");
            escort(state,liege);assertTrue(KOMESerfKnightEscortService.prepareDestination(state,f.s.f.world));
            KOMESerfKnightTrialAssignment a=state.getTrialAssignment();assertTrue(KOMESerfKnightEscortService.hasDestination(a));
            assertEquals("the shelter marked on your map",KOMESerfKnightEscortService.destinationName(a));
            NBTTagCompound saved=(NBTTagCompound)a.data.copy();state.readFromNBT(state.writeToNBT(),KOMEProgressionRank.SERF);
            KOMESerfKnightTrialAssignment loaded=state.getTrialAssignment();assertNotNull(loaded);
            assertEquals(saved,loaded.data);assertTrue(KOMESerfKnightEscortService.arrived(loaded,liege.dimension,edoras.getXCoord(),edoras.getZCoord()));
            f.s.f.world.loadedEntityList.clear();f.s.chunks=false;assertTrue(KOMESerfKnightEscortService.prepareDestination(state,f.s.f.world));assertEquals(saved,state.getTrialAssignment().data);
            state.retryUnfinishedTrial();assertEquals(saved,state.getTrialAssignment().data);
            assertFalse(KOMESerfKnightEscortService.arrived(loaded,liege.dimension,liege.x+900,liege.z));
            assertFalse(KOMESerfKnightEscortService.arrived(loaded,liege.dimension+1,edoras.getXCoord(),edoras.getZCoord()));
        }
    }
    @Test public void escortOnlyNpcArrivalCompletesAndRestoresTeleport()throws Exception {
        try(KOMEKnightCommissionGameplayTest.Session s=new KOMEKnightCommissionGameplayTest.Session()){
            s.original.ready();s.p.setCanonicalRank(KOMEProgressionRank.SERF);
            KOMESerfKnightProgression state=s.p.getSerfKnightProgression();KOMESerfKnightTrialAssignment a=escort(state,state.getLiege());
            KOMEKnightCommissionGameplayTest.TestNpc charge=s.npc(LOTRFaction.ROHAN);
            charge.hiredNPCInfo.isActive=true;charge.hiredNPCInfo.setHiringPlayer(s.f.player);charge.hiredNPCInfo.teleportAutomatically=true;
            NBTTagCompound data=KOMESerfKnightEscortService.createEncounterData(KOMEProgressionNpcRankService.referenceOf(charge),0,0,0);
            data.setDouble(KOMESerfKnightEscortService.DEST_X,1000);data.setDouble(KOMESerfKnightEscortService.DEST_Z,1000);data.setString(KOMESerfKnightEscortService.DEST_NAME,"Refuge");
            data.setTag(KOMEProgressionDestinations.PROOF,KOMEProgressionGameplayFixture.shelter(s.f.world,1000,1000,"rohan"));
            state.updateTrialAssignment(a.withStage(KOMESerfKnightTrialAssignment.Stage.ACTIVE,data));
            s.f.world.loadedEntityList.add(charge);KOMEProgressionEncounterMarker.mark(charge,KOMEProgressionEncounterMarker.ESCORT,s.f.player.id,a.assignmentToken);
            KOMEProgressionEscortFollowing.reconcile(s.f.data,charge);assertFalse(charge.hiredNPCInfo.teleportAutomatically);
            s.p.readFromNBT(s.p.writeToNBT());KOMEProgressionEscortFollowing.reconcile(s.f.data,charge);assertFalse(charge.hiredNPCInfo.teleportAutomatically);
            s.f.player.posX=1000;s.f.player.posZ=1000;charge.posX=-1000;charge.posZ=-1000;
            KOMESerfKnightEscortService.tickPlayer(s.f.player);assertFalse(state.isTrialCompleted());
            charge.posX=1000;charge.posZ=1000;KOMESerfKnightEscortService.tickPlayer(s.f.player);
            assertTrue(state.isTrialCompleted());assertTrue(charge.hiredNPCInfo.teleportAutomatically);assertFalse(charge.hiredNPCInfo.isActive);
        }
    }
    @Test public void noTeleportLeaseRestoresOriginalFalseAndMalformedOrphan()throws Exception {
        try(KOMEKnightCommissionGameplayTest.Session s=new KOMEKnightCommissionGameplayTest.Session()){
            KOMEKnightCommissionGameplayTest.TestNpc npc=s.npc(LOTRFaction.ROHAN);npc.hiredNPCInfo.teleportAutomatically=true;
            KOMEProgressionEscortFollowing.disable(npc);NBTTagCompound persisted=(NBTTagCompound)npc.getEntityData().copy();
            KOMEProgressionEncounterMarker.clear(npc);KOMEProgressionEscortFollowing.reconcile(s.f.data,npc);
            assertTrue(npc.hiredNPCInfo.teleportAutomatically);assertFalse(npc.getEntityData().hasKey(KOMEProgressionEscortFollowing.ORIGINAL));
            npc.hiredNPCInfo.teleportAutomatically=false;KOMEProgressionEscortFollowing.disable(npc);KOMEProgressionEscortFollowing.restore(npc);assertFalse(npc.hiredNPCInfo.teleportAutomatically);
            npc.getEntityData().setBoolean(KOMEProgressionEscortFollowing.ORIGINAL,persisted.getBoolean(KOMEProgressionEscortFollowing.ORIGINAL));
            KOMEProgressionEscortFollowing.reconcile(s.f.data,npc);assertTrue(npc.hiredNPCInfo.teleportAutomatically);
        }
    }
    @Test public void liegeQuestDispatchHasNoAdditionalAcceptanceAndIsIdempotent()throws Exception {
        try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){
            KOMEPlayerProgression p=f.s.p;KOMEKnightCommission a=new KOMEKnightCommission(Type.BORDER_INCURSION,p.getSerfKnightProgression().getLiege());
            a.stage=Stage.OFFERED;assertTrue(p.getKnightService().offer(a));
            assertTrue(KOMELiegeProgressionInteraction.ownsQuest(f.s.f.player,f.liege));
            assertEquals(KOMELiegeProgressionInteraction.Action.COMMISSION,KOMELiegeProgressionInteraction.action(p,150,"rohan"));
            assertTrue(KOMELiegeProgressionInteraction.quest(f.s.f.player,f.liege));assertEquals(Stage.ACTIVE,a.stage);String token=a.token;
            assertEquals(KOMELiegeProgressionInteraction.Action.STATUS,KOMELiegeProgressionInteraction.action(p,150,"rohan"));
            assertTrue(KOMELiegeProgressionInteraction.quest(f.s.f.player,f.liege));assertEquals(token,p.getKnightService().assignment().token);
            a.stage=Stage.READY_TO_REPORT;assertEquals(KOMELiegeProgressionInteraction.Action.REPORT,KOMELiegeProgressionInteraction.action(p,150,"rohan"));
            assertTrue(KOMELiegeProgressionInteraction.quest(f.s.f.player,f.liege));assertNull(p.getKnightService().assignment());
            assertEquals(KOMELiegeProgressionInteraction.Action.COMMISSION,KOMELiegeProgressionInteraction.action(p,150,"rohan"));
            KOMEKnightCommissionGameplayTest.TestNpc unrelated=f.s.npc(LOTRFaction.ROHAN);assertFalse(KOMELiegeProgressionInteraction.ownsQuest(f.s.f.player,unrelated));
        }
    }
    @Test public void actionableMarkersFollowDutyCadenceAndRewardIssuance()throws Exception {
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()){
            assertTrue(KOMEVisualLocationService.markersFor(s.p).get(0).actionable);
            s.state.setLastAssignmentEpochDay(KOMESerfKnightService.calendarDayNow());
            assertFalse(KOMEVisualLocationService.markersFor(s.p).get(0).actionable);
            s.state.assignDuty(KOMESerfKnightDutyType.PROVISIONING,null);
            assertTrue(KOMEVisualLocationService.markersFor(s.p).get(0).actionable);
            s.ready();assertTrue(KOMEVisualLocationService.markersFor(s.p).stream().anyMatch(m->m.role==KOMEVisualMarker.Role.MASTER_GIFT));
            s.state.setPartingGiftReceived();assertFalse(KOMEVisualLocationService.markersFor(s.p).stream().anyMatch(m->m.role==KOMEVisualMarker.Role.MASTER_GIFT));
        }
    }
    @Test public void completedTrialNeedsMasterRatherThanAnotherLiegeAfterLiegeDeath()throws Exception {
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()){
            s.ready();String liege=s.state.getLiege().entityUuid;
            assertTrue(KOMESerfKnightService.handleNpcDeath(s.state,liege,false,31));s.p.readFromNBT(s.p.writeToNBT());
            assertTrue(KOMEProgressionTrackerSnapshot.project(s.f.player,s.p).objective.contains("Return to your Master"));
            assertTrue(KOMEProgressionRankSummary.project(s.p,150,"rohan").activityObjective.contains("Return to your Master"));
            assertTrue(KOMESerfKnightService.canPromote(s.state,150));
        }
    }
    @Test public void trialQuestStatesAndExhaustedServiceMarkersUseActualAlignment()throws Exception {
        try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){
            f.credits(5);
            assertEquals(KOMELiegeProgressionInteraction.Action.UNAVAILABLE,KOMELiegeProgressionInteraction.action(f.s.p,1999,"rohan"));
            assertFalse(KOMEVisualLocationService.markersFor(f.s.p,f.s.f.data,1999).get(0).actionable);
            assertEquals(KOMELiegeProgressionInteraction.Action.TRIAL,KOMELiegeProgressionInteraction.action(f.s.p,2000,"rohan"));
            assertTrue(KOMEVisualLocationService.markersFor(f.s.p,f.s.f.data,2000).get(0).actionable);
            KOMELordshipTrial t=f.trial(KOMELordshipTrial.Scenario.BORDER_PATROL,Stage.OFFERED);
            assertTrue(KOMELiegeProgressionInteraction.quest(f.s.f.player,f.liege));assertEquals(Stage.ACTIVE,t.objective.stage);
            assertTrue(KOMELiegeProgressionInteraction.quest(f.s.f.player,f.liege));assertEquals(4,t.objective.actors.size());
            t.objective.stage=Stage.READY_TO_REPORT;
            assertEquals(KOMELiegeProgressionInteraction.Action.TRIAL_REPORT,KOMELiegeProgressionInteraction.action(f.s.p,2000,"rohan"));
        }
    }
    @Test public void dangerousEscortLeaseDoesNotChangeTrialGuardsAndRestoresOnCancellation()throws Exception {
        try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){
            KOMELordshipTrial t=f.trial(KOMELordshipTrial.Scenario.PROTECTED_EXPEDITION,Stage.ACTIVE);
            lotr.common.entity.npc.LOTREntityNPC charge=f.actor(t,Role.CHARGE),guard=f.actor(t,Role.GUARD);
            charge.hiredNPCInfo.teleportAutomatically=true;guard.hiredNPCInfo.teleportAutomatically=true;
            KOMEProgressionEscortFollowing.reconcile(f.s.f.data,charge);KOMEProgressionEscortFollowing.reconcile(f.s.f.data,guard);
            assertFalse(charge.hiredNPCInfo.teleportAutomatically);assertTrue(guard.hiredNPCInfo.teleportAutomatically);
            f.s.reload();KOMEProgressionEscortFollowing.reconcile(f.s.f.data,charge);assertFalse(charge.hiredNPCInfo.teleportAutomatically);
            KOMELordshipTrialService.cancel(f.s.f.world,f.s.f.player.id,f.s.p);
            assertTrue(charge.hiredNPCInfo.teleportAutomatically);assertFalse(charge.getEntityData().hasKey(KOMEProgressionEscortFollowing.ORIGINAL));
        }
        try(KOMEKnightCommissionGameplayTest.Session s=new KOMEKnightCommissionGameplayTest.Session()){
            KOMEKnightCommission a=s.active(Type.DANGEROUS_ESCORT);
            lotr.common.entity.npc.LOTREntityNPC charge=KOMEKnightCommissionGameplayTest.actor(s,a,Role.CHARGE);
            charge.hiredNPCInfo.isActive=true;charge.hiredNPCInfo.setHiringPlayer(s.f.player);charge.hiredNPCInfo.teleportAutomatically=true;
            KOMEProgressionEscortFollowing.reconcile(s.f.data,charge);assertFalse(charge.hiredNPCInfo.teleportAutomatically);
            KOMEKnightCommissionService.npcDeath(s.f.data,s.f.world,charge.getUniqueID().toString());
            assertTrue(charge.hiredNPCInfo.teleportAutomatically);assertFalse(charge.getEntityData().hasKey(KOMEProgressionEscortFollowing.ORIGINAL));
        }
    }
}
