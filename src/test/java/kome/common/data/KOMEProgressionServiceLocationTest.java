package kome.common.data;

import java.util.*;
import kome.common.KOMEAccessFixture;
import lotr.common.LOTRLevelData;
import lotr.common.entity.npc.*;
import lotr.common.fac.LOTRFaction;
import net.minecraft.entity.EntityCreature;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChunkCoordinates;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMEProgressionServiceLocationTest {
    static void territory(KOMEAccessFixture f)throws Exception {
        KOMECourierGeographyTest.TestBiome biome=KOMEAccessFixture.allocate(KOMECourierGeographyTest.TestBiome.class);
        biome.npcSpawnList=KOMEAccessFixture.allocate(KOMECourierGeographyTest.TestSpawnList.class);
        KOMECourierGeographyTest.TestManager manager=KOMEAccessFixture.allocate(KOMECourierGeographyTest.TestManager.class);manager.biome=biome;
        f.world.provider.worldChunkMgr=manager;f.world.provider.dimensionId=lotr.common.LOTRDimension.MIDDLE_EARTH.dimensionID;f.player.dimension=f.world.provider.dimensionId;
    }
    @Test public void ordinaryMasterNeedsSerfPledgeAndTerritoryButNoRegionalSeat()throws Exception {
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()){
            s.state.reset();assertFalse(KOMESerfdomMasterService.serve(s.f.player,s.f.data,s.master).success);
            territory(s.f);assertTrue(KOMESerfdomMasterService.ordinaryMaster(s.master));assertFalse(KOMEProgressionServiceLocation.regional(s.master));
            assertTrue(KOMESerfdomMasterService.serve(s.f.player,s.f.data,s.master).success);assertEquals(KOMEProgressionRank.SERF,s.p.getCanonicalRank());
            s.state.reset();s.p.setCanonicalRank(KOMEProgressionRank.WANDERER);assertFalse(KOMESerfdomMasterService.serve(s.f.player,s.f.data,s.master).success);
        }
    }
    /** Fixture models the native generator callback and its native home assignment. */
    static void generatedHome(LOTREntityNPC npc, String host) throws Exception {
        Object generator=null;
        for(String pkg:new String[]{"lotr.common.world.structure2.","lotr.common.world.structure."})try{
            generator=KOMEAccessFixture.allocate(Class.forName(pkg+"LOTRWorldGen"+host));break;
        }catch(ClassNotFoundException ignored){}
        assertNotNull(host,generator);
        KOMEKnightCommissionGameplayTest.setField(EntityCreature.class,npc,"homePosition",new ChunkCoordinates());
        assertTrue(KOMEProgressionServiceLocation.generated(npc.worldObj,npc,true,generator));
        npc.setHomeArea((int)npc.posX,(int)npc.posY,(int)npc.posZ,8);
    }
    static KOMESerfKnightStabilizationTest.TrialCaptain sponsored(KOMEProgressionFollowupTest.Session s) throws Exception {
        s.p.setCanonicalRank(KOMEProgressionRank.KNIGHT);
        s.state.retireSerfdomMasterAfterPromotion();
        s.p.getServiceRecognition().earn(s.f.player.id,KOMEProgressionNpcRankService.referenceOf(s.master),KOMEProgressionRank.SERF,KOMEProgressionRank.KNIGHT,"Three reported duties",0);
        assertTrue(KOMERecommendationService.deliver(s.f.player));
        java.lang.reflect.Field alignments=lotr.common.LOTRPlayerData.class.getDeclaredField("alignments");alignments.setAccessible(true);
        ((Map)alignments.get(LOTRLevelData.getData(s.f.player))).put(LOTRFaction.ROHAN,150F);
        return KOMESerfKnightStabilizationTest.captain(s);
    }
    @Test public void lordAtNativeRegionalSeatAcceptsGuaranteedIntroduction() throws Exception {
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()){
            LOTREntityNPC lord=sponsored(s);generatedHome(lord,"RohanFortress");
            assertTrue(KOMEProgressionOfferBridge.canReplaceLiegeFrom(s.f.player,lord));
            assertTrue(KOMESerfKnightRelationshipService.establishLiege(s.f.player,s.f.data,lord).success);
            assertTrue(s.p.getServiceRecognition().records().get(0).redeemed);
            assertTrue(KOMEKnightCommissionService.eligible(s.f.player,lord));
            assertFalse(KOMEProgressionOfferBridge.canReplaceLiegeFrom(s.f.player,lord));
            assertTrue(s.f.player.inventory.mainInventory[0].getTagCompound().getBoolean("KOMERecommendationRedeemed"));
        }
    }
    @Test public void princeCanReceiveRegionalKnightServiceWithoutBecomingRoyalService() throws Exception {
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()){
            LOTREntityNPC prince=sponsored(s);generatedHome(prince,"RohanFortress");
            s.f.data.progressionNpcRanks.put(prince.getUniqueID(),new KOMEProgressionNpcRankRecord(prince.getUniqueID(),"rohan",KOMEProgressionNpcRank.PRINCE,"Aldor"));
            assertTrue(KOMESerfKnightRelationshipService.establishLiege(s.f.player,s.f.data,prince).success);
            assertTrue(KOMECurrentLiege.valid(s.f.player,prince));
            assertFalse(KOMEProgressionServiceLocation.royal(s.f.data,prince));
        }
    }
    @Test public void wildernessNobleAndUnprovenLegacyHomeCannotReceiveNewService() throws Exception {
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()){
            LOTREntityNPC lord=sponsored(s);
            assertFalse(KOMEProgressionOfferBridge.canReplaceLiegeFrom(s.f.player,lord));
            assertFalse(KOMESerfKnightRelationshipService.establishLiege(s.f.player,s.f.data,lord).success);
            KOMEKnightCommissionGameplayTest.setField(EntityCreature.class,lord,"homePosition",new ChunkCoordinates());lord.setHomeArea((int)lord.posX,64,(int)lord.posZ,8);
            assertFalse(KOMEProgressionServiceLocation.regional(lord));
            assertFalse(s.p.getServiceRecognition().records().get(0).redeemed);
            assertTrue(KOMEProgressionEligibility.evaluate(s.f.player,lord).reason.contains("regional seat"));
        }
    }
    @Test public void leavingNativeHomeOrMovingHomeToWildernessInvalidatesAdmission() throws Exception {
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()){
            LOTREntityNPC lord=sponsored(s);generatedHome(lord,"RohanFortress");
            assertTrue(KOMEProgressionServiceLocation.regional(lord));
            lord.posX+=100;assertFalse(KOMEProgressionServiceLocation.regional(lord));
            lord.setHomeArea((int)lord.posX,64,(int)lord.posZ,8);assertFalse(KOMEProgressionServiceLocation.regional(lord));
        }
    }
    @Test public void copiedNpcProofWrongFactionAndWrongDimensionFailClosed() throws Exception {
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()){
            LOTREntityNPC lord=sponsored(s);generatedHome(lord,"RohanFortress");
            NBTTagCompound proof=lord.getEntityData().getCompoundTag(KOMEProgressionServiceLocation.TAG);
            assertFalse(KOMEProgressionServiceLocation.validProof(proof,UUID.randomUUID().toString(),"rohan",0));
            assertFalse(KOMEProgressionServiceLocation.validProof(proof,lord.getUniqueID().toString(),"gondor",0));
            assertFalse(KOMEProgressionServiceLocation.validProof(proof,lord.getUniqueID().toString(),"rohan",999));
            s.f.pledge(LOTRFaction.GONDOR);assertFalse(KOMESerfKnightRelationshipService.establishLiege(s.f.player,s.f.data,lord).success);
        }
    }
    @Test public void failedNativeSpawnNeverCreatesSeatProvenance() throws Exception {
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()){
            LOTREntityNPC lord=sponsored(s);Object generator=KOMEAccessFixture.allocate(lotr.common.world.structure2.LOTRWorldGenRohanFortress.class);
            assertFalse(KOMEProgressionServiceLocation.generated(s.f.world,lord,false,generator));
            assertFalse(lord.getEntityData().hasKey(KOMEProgressionServiceLocation.TAG));
        }
    }
    @Test public void preexistingAcceptedRelationshipSurvivesMissingPlacementMetadataAndRestart() throws Exception {
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()){
            LOTREntityNPC lord=sponsored(s);s.state.setLiege(KOMEProgressionNpcRankService.referenceOf(lord));
            s.p.readFromNBT(s.p.writeToNBT());assertTrue(KOMECurrentLiege.valid(s.f.player,lord));
            assertFalse(KOMEProgressionRelationshipLifecycle.reconcileLoadedRelationships(s.f.data,s.f.player));
        }
    }
    @Test public void lordLetterDoesNotOpenUnimplementedRoyalAssignments() throws Exception {
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()){
            LOTREntityNPC prince=sponsored(s);generatedHome(prince,"RohanFortress");s.p.setCanonicalRank(KOMEProgressionRank.LORD);
            s.p.getServiceRecognition().earn(s.f.player.id,KOMEProgressionNpcRankService.referenceOf(prince),KOMEProgressionRank.KNIGHT,KOMEProgressionRank.LORD,"Three reported commissions",0);
            s.f.data.progressionNpcRanks.put(prince.getUniqueID(),new KOMEProgressionNpcRankRecord(prince.getUniqueID(),"rohan",KOMEProgressionNpcRank.PRINCE,"Aldor"));
            assertTrue(KOMERecommendationService.deliver(s.f.player));assertFalse(KOMEProgressionOfferBridge.canReplaceLiegeFrom(s.f.player,prince));
            assertTrue(KOMEProgressionRankSummary.project(s.p,2000,"rohan").activityObjective.contains("legacy Prince"));
        }
    }
    @Test public void capitalNpcCrownAuthorityDoesNotDependOnPlayerKingPresence()throws Exception {
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()){
            assertTrue(kome.common.data.KOMETileWorldResolver.INSTANCE.reloadBundled());
            KOMEFactionCapitalService.initializeMetadataFixture(s.f.data);KOMEFactionCapitalRecord capital=KOMEFactionCapitalService.getCapital(s.f.data,"rohan");
            LOTREntityNPC king=KOMESerfKnightStabilizationTest.captain(s);
            s.f.world.provider.dimensionId=capital.getDeploymentDimensionId();king.posX=capital.getDeploymentX();king.posY=capital.getDeploymentY();king.posZ=capital.getDeploymentZ();
            KOMEProgressionNpcRankRecord record=new KOMEProgressionNpcRankRecord(king.getUniqueID(),"rohan",KOMEProgressionNpcRank.KING,"Aldor");s.f.data.progressionNpcRanks.put(king.getUniqueID(),record);
            KOMEProgressionEncounterMarker.stampRuler(king,record.incarnation);
            assertTrue(KOMEProgressionServiceLocation.royal(s.f.data,king));
            KOMERulerService.assignRuler(s.f.data,"rohan",UUID.randomUUID(),"Absent player King");assertTrue(KOMEProgressionServiceLocation.royal(s.f.data,king));
            king.posX+=100000;assertFalse(KOMEProgressionServiceLocation.royal(s.f.data,king));
        }
    }
}
