package kome.common.data;

import java.util.*;
import kome.common.KOMEAccessFixture;
import lotr.common.fac.LOTRFaction;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;
import static org.junit.Assert.*;
import static kome.common.data.KOMEKnightCommission.*;

public class KOMEProgressionGameplayPassTest {
    @org.junit.Rule public final KOMETileTestResources geometry=new KOMETileTestResources();
    @Test public void trackerStagesUseRealEncounterEvidenceAndResetCountsAcrossReload()throws Exception {
        try(KOMEKnightCommissionGameplayTest.Session s=new KOMEKnightCommissionGameplayTest.Session()){
            KOMEKnightCommission a=s.active(Type.STOLEN_GOODS);a.stage=Stage.OFFERED;
            assertEquals("Speak with your Liege",KOMEKnightCommissionPresentation.objective(a));
            a.stage=Stage.ACTIVE;assertEquals("Travel to the marked objective",KOMEKnightCommissionPresentation.objective(a));
            a.encounterCreated=true;KOMEKnightCommissionGameplayTest.actor(s,a,Role.ENEMY);KOMEKnightCommissionGameplayTest.actor(s,a,Role.ENEMY);
            a.actors.get(0).dead=true;
            assertEquals("1/2",KOMEProgressionTrackerSnapshot.project(s.f.player,s.p).progress);
            s.reload();a=s.p.getKnightService().assignment();assertEquals("1/2",KOMEProgressionTrackerSnapshot.project(s.f.player,s.p).progress);
            a.threatResolved=true;assertEquals("Recover the stolen property",KOMEKnightCommissionPresentation.objective(a));
            assertEquals("0/1",KOMEProgressionTrackerSnapshot.project(s.f.player,s.p).progress);
            a.stage=Stage.READY_TO_REPORT;assertEquals("Report to your Liege",KOMEKnightCommissionPresentation.objective(a));
            assertEquals(0F,KOMEProgressionTrackerSnapshot.project(s.f.player,s.p).completion,0F);
        }
    }
    @Test public void courierTimerBelongsInBookAndNeverTracker()throws Exception {
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()){
            KOMESerfCourierAssignment a=s.courier();a.documentIssued=true;a.nextReplacementWorldTime=2400;s.state.setDutyAssignmentData(KOMESerfKnightDutyType.COURIER,a.writeToNBT());
            KOMEProgressionTrackerSnapshot hud=KOMEProgressionTrackerSnapshot.project(s.f.player,s.p);
            assertEquals("Deliver the message",hud.objective);assertEquals("0/1",hud.progress);assertEquals(0F,hud.completion,0F);
            assertTrue(KOMEProgressionSummary.courierCopy(s.p,0).contains("Speak with your Master in"));
        }
    }
    @Test public void emptyCoordinatesAndWaypointDoNotProveShelterAndNeverWriteBlocks()throws Exception {
        try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){
            KOMEProgressionHardeningGeographyTest.territory(f);
            assertNull(KOMEProgressionDestinations.verify(f.s.f.world,LOTRFaction.ROHAN,1800,1000,"waypoint:Edoras"));
            assertNull(KOMEProgressionDestinations.find(f.s.f.world,KOMEProgressionHardeningGeographyTest.origin(f),500,1500));
            assertTrue(f.s.f.world.structureBlocks.isEmpty());assertTrue(f.s.f.data.progressionShelters.isEmpty());
        }
    }
    @Test public void existingRoofAndWallAreVerifiedAndDestroyedRoofInvalidatesDestination()throws Exception {
        try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){
            KOMEProgressionHardeningGeographyTest.territory(f);f.s.f.world.shelter(1800,1000);
            KOMEKnightCommissionLocations.Destination site=KOMEProgressionDestinations.verify(f.s.f.world,LOTRFaction.ROHAN,1800,1000,"native:test");
            assertNotNull(site);assertEquals("EXISTING_VERIFIED",site.proof.getString("Status"));assertTrue(KOMEProgressionDestinations.valid(f.s.f.world,site.proof));
            int blocks=f.s.f.world.structureBlocks.size();KOMEProgressionDestinations.verify(f.s.f.world,LOTRFaction.ROHAN,1800,1000,"native:test");assertEquals(blocks,f.s.f.world.structureBlocks.size());
            f.s.f.world.structureBlocks.clear();assertFalse(KOMEProgressionDestinations.valid(f.s.f.world,site.proof));
        }
    }
    @Test public void shelterRegistrySurvivesWorldSaveAndUsesUnloadedVerifiedEvidence()throws Exception {
        try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){
            KOMEProgressionHardeningGeographyTest.territory(f);KOMEProgressionGameplayFixture.record(f.s.f.world,1800,1000,"rohan");
            NBTTagCompound saved=new NBTTagCompound();f.s.f.data.writeToNBT(saved);KOMEWorldData reload=new KOMEWorldData();reload.readFromNBT(saved);
            assertEquals(f.s.f.data.progressionShelters,reload.progressionShelters);f.s.chunks=false;
            int probes=f.s.f.world.terrainProbes;assertNotNull(KOMEProgressionDestinations.find(f.s.f.world,KOMEProgressionHardeningGeographyTest.origin(f),500,1500));assertEquals(probes,f.s.f.world.terrainProbes);
        }
    }
    @Test public void failedDestinationUsesExistingFailureAndCleansActors()throws Exception {
        try(KOMEKnightCommissionGameplayTest.Session s=new KOMEKnightCommissionGameplayTest.Session()){
            KOMEKnightCommission a=s.active(Type.DANGEROUS_ESCORT);KOMEKnightCommissionGameplayTest.TestNpc charge=KOMEKnightCommissionGameplayTest.actor(s,a,Role.CHARGE);
            s.f.world.structureBlocks.clear();KOMEKnightCommissionService.tickPlayer(s.f.player);
            assertEquals(Stage.FAILED,a.stage);assertTrue(charge.isDead);assertTrue(KOMEKnightCommissionPresentation.objective(a).contains("Return to your Liege"));
        }
    }
    @Test public void hostileActorSitesAreAroundFiftyAndRejectBuildings()throws Exception {
        try(KOMEKnightCommissionGameplayTest.Session s=new KOMEKnightCommissionGameplayTest.Session()){
            double[] site=KOMEProgressionEncounterSites.attacker(s.f.world,640,640,s.liege,"seed",0);assertNotNull(site);
            double distance=Math.hypot(site[0]-640,site[2]-640);assertTrue(distance>=40&&distance<=64);
            assertTrue(KOMEProgressionEncounterSites.openGround(s.f.world,site));
            s.f.world.shelter((int)site[0],(int)site[2]);assertFalse(KOMEProgressionEncounterSites.openGround(s.f.world,site));
        }
    }
    @Test public void standingEscortMapReferenceMovesByBoundIdentityAcrossReload()throws Exception {
        try(KOMEKnightCommissionGameplayTest.Session s=new KOMEKnightCommissionGameplayTest.Session()){
            s.original.ready();s.p.setCanonicalRank(KOMEProgressionRank.SERF);KOMESerfKnightProgression state=s.p.getSerfKnightProgression();
            KOMESerfKnightTrialAssignment a=KOMESerfKnightTrialAssignment.create(KOMESerfKnightTrial.forId("escort"),state.getLiege(),10,0);
            KOMEKnightCommissionGameplayTest.TestNpc charge=s.npc(LOTRFaction.ROHAN);charge.hiredNPCInfo.isActive=true;charge.hiredNPCInfo.setHiringPlayer(s.f.player);
            NBTTagCompound data=KOMESerfKnightEscortService.createEncounterData(KOMEProgressionNpcRankService.referenceOf(charge),0,0,0);
            data.setDouble(KOMESerfKnightEscortService.DEST_X,1000);data.setDouble(KOMESerfKnightEscortService.DEST_Z,1000);data.setString(KOMESerfKnightEscortService.DEST_NAME,"the shelter marked on your map");
            data.setTag(KOMEProgressionDestinations.PROOF,KOMEProgressionGameplayFixture.shelter(s.f.world,1000,1000,"rohan"));
            state.setTrial(a.withStage(KOMESerfKnightTrialAssignment.Stage.ACTIVE,data));KOMEProgressionEncounterMarker.mark(charge,KOMEProgressionEncounterMarker.ESCORT,s.f.player.id,a.assignmentToken);s.f.world.loadedEntityList.add(charge);
            charge.posX=120;charge.posZ=130;assertTrue(KOMEVisualLocationService.refreshLoadedLocations(s.f.player,s.f.data,s.p));
            s.reload();List<KOMEVisualMarker> markers=KOMEVisualLocationService.markersFor(s.p);
            assertEquals(1,markers.stream().filter(m->m.entityUuid.equals(charge.getUniqueID().toString())).count());
            KOMEVisualMarker moving=markers.stream().filter(m->m.entityUuid.equals(charge.getUniqueID().toString())).findFirst().get();assertEquals(120,moving.x,0);assertEquals(130,moving.z,0);
            assertTrue(markers.stream().anyMatch(m->m.role==KOMEVisualMarker.Role.ESCORT&&m.entityUuid.isEmpty()&&m.x==1000));
        }
    }
    @Test public void questPropertyDespawnProtectionReturnsOnDeathDropAndEndsAfterReport()throws Exception {
        try(KOMEKnightCommissionGameplayTest.Session s=new KOMEKnightCommissionGameplayTest.Session()){
            KOMEKnightCommission a=s.active(Type.STOLEN_GOODS);KOMEKnightCommissionGameplayTest.goods(a);
            EntityItem drop=new EntityItem(s.f.world,640,65,640,KOMEKnightCommissionService.property(a,s.f.player.id));drop.age=5999;
            assertFalse(KOMEKnightCommissionService.reconcileItem(s.f.data,drop));assertEquals(Integer.MAX_VALUE,drop.lifespan);assertEquals(0,drop.age);
            a.stage=Stage.REPORTED;assertTrue(KOMEKnightCommissionService.reconcileItem(s.f.data,drop));assertTrue(drop.isDead);
        }
    }
    @Test public void secondaryRewardsAreModestAndReservedOnceAcrossReload()throws Exception {
        try(KOMEProgressionFollowupTest.NativeItems items=new KOMEProgressionFollowupTest.NativeItems();KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()){
            s.state.assignDuty(KOMESerfKnightDutyType.COURIER,null);s.state.completeDuty(KOMESerfKnightDutyType.COURIER);
            KOMEProgressionServiceRewards.duty(s.f.player,s.state,KOMESerfKnightDutyType.COURIER);assertEquals(1,s.drops());assertTrue(s.latest().stackSize<=2);
            s.p.readFromNBT(s.p.writeToNBT());KOMEProgressionServiceRewards.duty(s.f.player,s.state,KOMESerfKnightDutyType.COURIER);assertEquals(1,s.drops());
            assertEquals(KOMEProgressionRank.SERF,s.p.getCanonicalRank());
        }
    }
    @Test public void ownRulerFilterUsesActualFactionAndPreSpawnNamesRemainTitleOnly()throws Exception {
        KOMEWorldData data=new KOMEWorldData();data.initializeIntegratedWorld();
        for(String faction:KOMEProgressionNativeAuthority.definitions().keySet())KOMEProgressionNpcRankService.assignElevatedRank(data,UUID.randomUUID(),faction,KOMEProgressionNpcRank.KING,"",true);
        assertEquals(23,KOMEProgressionRulerService.markers(data).size());
        for(KOMEVisualMarker marker:KOMEProgressionRulerService.markers(data)){
            KOMEProgressionNpcRankRecord record=data.progressionNpcRanks.get(UUID.fromString(marker.entityUuid));
            assertEquals(KOMEProgressionFactionResolver.matches(record.factionKey,LOTRFaction.ROHAN),KOMEVisualLocationService.ownRuler(marker,data,LOTRFaction.ROHAN));
            assertEquals(KOMEProgressionNativeAuthority.definition(record.factionKey).rulerTitle,marker.title);assertFalse(KOMEVisualLocationService.ownRuler(marker,data,null));
        }
    }
    @Test public void dialogueNeverCarriesLegacyInventedRefugeNames() {
        KOMEKnightCommission a=new KOMEKnightCommission(Type.DANGEROUS_ESCORT,KOMEKnightCommissionStateTest.liege());a.place="Imaginary village";
        assertFalse(KOMEKnightCommissionPresentation.speech(a,"assigned").contains("Imaginary"));assertFalse(KOMEKnightCommissionPresentation.speech(a,"assigned").contains("shelter"));
        a.destinationProof.setString("Status","EXISTING_VERIFIED");assertTrue(KOMEKnightCommissionPresentation.speech(a,"assigned").contains("shelter marked on your map"));
    }
    @Test public void protectedHomeLeaseRestoresNativeHomeAfterFailure()throws Exception {
        try(KOMEKnightCommissionGameplayTest.Session s=new KOMEKnightCommissionGameplayTest.Session()){
            KOMEKnightCommission a=s.active(Type.SETTLEMENT_DEFENSE);KOMEKnightCommissionGameplayTest.TestNpc protectedNpc=KOMEKnightCommissionGameplayTest.actor(s,a,Role.BENEFICIARY);
            java.lang.reflect.Field home=net.minecraft.entity.EntityCreature.class.getDeclaredField("homePosition");home.setAccessible(true);home.set(protectedNpc,new net.minecraft.util.ChunkCoordinates(12,65,14));
            protectedNpc.setHomeArea(12,65,14,40);KOMEProgressionNpcRoles.syncPlayer(s.f.data,s.f.player.id);
            KOMEProgressionProtectedActors.reconcile(s.f.data,protectedNpc);assertEquals(16,protectedNpc.func_110174_bM(),0);assertEquals(640,protectedNpc.getHomePosition().posX);
            a.stage=Stage.FAILED;KOMEProgressionNpcRoles.syncPlayer(s.f.data,s.f.player.id);KOMEProgressionProtectedActors.reconcile(s.f.data,protectedNpc);
            assertEquals(40,protectedNpc.func_110174_bM(),0);assertEquals(12,protectedNpc.getHomePosition().posX);assertEquals(14,protectedNpc.getHomePosition().posZ);
        }
    }
    @Test public void newLostItemsVaryWithinCuratedFactionEquipment()throws Exception {
        try(KOMEProgressionFollowupTest.NativeItems items=new KOMEProgressionFollowupTest.NativeItems()){
            java.util.Set<net.minecraft.item.Item> selected=new java.util.HashSet<net.minecraft.item.Item>();
            for(int seed=0;seed<50;seed++){
                ItemStack stack=KOMEProgressionLostItems.choose("rohan",new java.util.Random(seed));assertNotNull(stack);assertEquals(1,stack.stackSize);
                assertTrue(java.util.Arrays.asList(KOMEPartingGiftService.equipment("rohan")).contains(stack.getItem())||stack.getItem()==lotr.common.LOTRMod.silver);selected.add(stack.getItem());
            }
            assertTrue(selected.size()>=3);
        }
    }
    @Test public void guidingEscortCannotChangeLordshipGuardTeleportPolicy()throws Exception {
        try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){
            KOMELordshipTrial t=f.trial(KOMELordshipTrial.Scenario.PROTECTED_EXPEDITION,Stage.ACTIVE);
            lotr.common.entity.npc.LOTREntityNPC guard=f.actor(t,Role.GUARD);guard.hiredNPCInfo.teleportAutomatically=true;
            KOMEProgressionEscortFollowing.guide(f.s.f.data,guard);assertTrue(guard.hiredNPCInfo.teleportAutomatically);
            assertFalse(guard.getEntityData().hasKey(KOMEProgressionEscortFollowing.ORIGINAL));
        }
    }
    @Test public void missingProtectedActorWaitsForLoadedEvidenceThenFailsWithoutSpawning()throws Exception {
        try(KOMEKnightCommissionGameplayTest.Session s=new KOMEKnightCommissionGameplayTest.Session()){
            s.original.ready();s.p.setCanonicalRank(KOMEProgressionRank.SERF);
            KOMESerfKnightProgression state=s.p.getSerfKnightProgression();
            KOMEProgressionNpcRef missing=new KOMEProgressionNpcRef(UUID.randomUUID().toString(),"Protected ally","rohan",0,640,65,640);
            NBTTagCompound data=new NBTTagCompound();data.setTag(KOMESerfKnightDefenseService.OBJECTIVE,missing.writeToNBT());
            KOMESerfKnightTrialAssignment a=KOMESerfKnightTrialAssignment.create(KOMESerfKnightTrial.forId("defense"),state.getLiege(),10,0);
            state.setTrial(a.withStage(KOMESerfKnightTrialAssignment.Stage.ACTIVE,data));s.f.player.posX=s.f.player.posZ=640;s.f.player.posY=65;
            s.chunks=false;for(int i=0;i<100;i++)KOMESerfKnightDefenseService.tickPlayer(s.f.player);
            assertEquals(KOMESerfKnightTrialAssignment.Stage.ACTIVE,state.getTrialAssignment().stage);
            s.chunks=true;for(int i=0;i<30;i++)KOMESerfKnightDefenseService.tickPlayer(s.f.player);
            KOMEKnightCommissionGameplayTest.TestNpc restored=s.npc(LOTRFaction.ROHAN);restored.setUniqueID(UUID.fromString(missing.entityUuid));restored.posX=restored.posZ=640;s.f.world.loadedEntityList.add(restored);
            KOMESerfKnightDefenseService.tickPlayer(s.f.player);assertEquals(0,state.getTrialAssignment().data.getInteger("DefenseObjectiveMissingTicks"));s.f.world.loadedEntityList.removeIf(value->value==restored);
            for(int i=0;i<30;i++)KOMESerfKnightDefenseService.tickPlayer(s.f.player);s.reload();
            for(int i=0;i<30;i++)KOMESerfKnightDefenseService.tickPlayer(s.f.player);
            assertEquals(KOMESerfKnightTrialAssignment.Stage.FAILED,state.getTrialAssignment().stage);assertFalse(state.isTrialCompleted());assertEquals(0,s.original.drops());
        }
    }
    @Test public void unavailablePhysicalTrialDoesNotRemainAssignedAfterInteraction()throws Exception {
        try(KOMEKnightCommissionGameplayTest.Session s=new KOMEKnightCommissionGameplayTest.Session()){
            s.original.ready();s.p.setCanonicalRank(KOMEProgressionRank.SERF);
            lotr.common.entity.npc.LOTREntityNPC liege=KOMEKnightCommissionInteractionTest.captain(s);
            KOMESerfKnightProgression state=s.p.getSerfKnightProgression();
            state.setTrial(KOMESerfKnightTrialAssignment.create(KOMESerfKnightTrial.forId("defense"),state.getLiege(),10,0));
            KOMEProgressionNpcInteractionService.activateTrial(s.f.player,s.p,liege,"defense");
            assertEquals(KOMESerfKnightTrialAssignment.Stage.FAILED,state.getTrialAssignment().stage);
            assertTrue(s.f.player.messages.stream().anyMatch(m->m.contains("cannot arrange this trial safely")));
            assertFalse(state.isTrialCompleted());assertEquals(0,s.original.drops());
            String token=state.getTrialAssignment().assignmentToken;KOMEProgressionNpcInteractionService.activateTrial(s.f.player,s.p,liege,"defense");
            assertEquals(token,state.getTrialAssignment().assignmentToken);assertEquals(0,s.original.drops());
        }
    }
}
