package kome.common.data;

import java.util.*;
import lotr.common.entity.npc.LOTREntityNPC;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;
import static org.junit.Assert.*;
import static kome.common.data.KOMEKnightCommission.*;

public class KOMEProgressionPass3MigrationTest {
    @Test public void completedStandingTrialRetiresOnceWithCreditsAndNoCelebration()throws Exception {
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()){
            s.ready();KOMEProgressionAnnouncements.Sender before=KOMEProgressionAnnouncements.sender;List<String> announcements=new ArrayList<String>();KOMEProgressionAnnouncements.sender=announcements::add;
            try{
                String token=s.state.getTrialAssignment().assignmentToken;assertTrue(KOMEServicePromotionService.migrate(s.f.player));
                assertEquals(KOMEProgressionRank.SERF,s.p.getCanonicalRank());assertEquals(3,KOMEServicePromotionService.duties(s.state));assertNull(s.state.getTrialAssignment());assertFalse(s.state.hasLiege());assertTrue(announcements.isEmpty());assertEquals(0,s.drops());
                assertEquals(token,s.p.getServiceRecognition().write().getCompoundTag("RetiredStanding").getString("Token"));
                for(int i=0;i<3;i++){s.p.readFromNBT(s.p.writeToNBT());assertFalse(KOMEServicePromotionService.migrate(s.f.player));assertEquals(3,KOMEServicePromotionService.duties(s.state));}
                assertTrue(KOMEServicePromotionService.report(s.f.player,s.master));assertEquals(1,announcements.size());assertEquals(KOMEProgressionRank.KNIGHT,s.p.getCanonicalRank());
                s.p.readFromNBT(s.p.writeToNBT());assertFalse(KOMEServicePromotionService.report(s.f.player,s.master));assertEquals(1,announcements.size());
            }finally{KOMEProgressionAnnouncements.sender=before;}
        }
    }
    @Test public void activeOldLordshipForceRetiresWithoutPenaltyAndUnloadedGuardReconciles()throws Exception {
        try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){
            f.credits(2);KOMELordshipTrial t=f.trial(KOMELordshipTrial.Scenario.BORDER_PATROL,Stage.ACTIVE);f.force(t);
            LOTREntityNPC unloaded=(LOTREntityNPC)KOMEKnightCommissionService.findLoaded(f.s.f.world,t.objective.actors.get(0).id);f.s.f.world.loadedEntityList.remove(unloaded);
            t.nativeInvasion=true;t.invasionType=lotr.common.world.spawning.LOTRInvasions.DUNLAND.codeName();
            assertTrue(KOMEServicePromotionService.migrate(f.s.f.player));assertNull(f.s.p.getLordship().assignment());assertTrue(t.retired);assertEquals(2,f.s.p.getKnightService().qualifyingTypes("rohan").size());
            f.s.reload();assertTrue(f.s.p.getLordship().history().get(0).retired);assertFalse(KOMEServicePromotionService.migrate(f.s.f.player));
            KOMELordshipTrialService.reconcileNpc(f.s.f.data,unloaded);assertTrue(unloaded.isDead);assertFalse(unloaded.hiredNPCInfo.isActive);assertTrue(f.s.p.getSerfKnightProgression().hasLiege());assertEquals(KOMEProgressionRank.KNIGHT,f.s.p.getCanonicalRank());
        }
    }
    @Test public void completedOldLordshipDoesNotDemandPrinceOrReplayCommissionReward()throws Exception {
        try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){
            f.credits(3);KOMELordshipTrial t=f.trial(KOMELordshipTrial.Scenario.BORDER_PATROL,Stage.READY_TO_REPORT);t.nativeInvasion=true;t.invasionType=lotr.common.world.spawning.LOTRInvasions.DUNLAND.codeName();t.nativeVictory=true;
            assertTrue(KOMEServicePromotionService.migrate(f.s.f.player));assertEquals(KOMEProgressionRank.KNIGHT,f.s.p.getCanonicalRank());assertFalse(t.objective.rewardReserved);
            assertTrue(KOMEServicePromotionService.report(f.s.f.player,f.lord));assertEquals(KOMEProgressionRank.LORD,f.s.p.getCanonicalRank());assertEquals(3,f.s.p.getKnightService().history().size());assertFalse(t.objective.rewardReserved);
            assertFalse(f.s.p.getSerfKnightProgression().hasLiege());assertEquals(f.lord.getUniqueID().toString(),f.s.p.getServiceRecognition().records().get(0).issuer.entityUuid);
            f.s.reload();assertFalse(KOMEServicePromotionService.report(f.s.f.player,f.liege));assertEquals(1,f.s.p.getServiceRecognition().records().size());
        }
    }
    @Test public void existingLordAndPrinceNeverDemoteOrReceiveUnearnedRecommendation()throws Exception {
        for(KOMEProgressionRank rank:new KOMEProgressionRank[]{KOMEProgressionRank.LORD,KOMEProgressionRank.PRINCE})try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()){
            s.state.reset();s.p.setCanonicalRank(rank);s.p.grant("serf.title_knight");s.p.grant("knight.title_lord");
            assertTrue(KOMEServicePromotionService.migrate(s.f.player));s.p.readFromNBT(s.p.writeToNBT());assertEquals(rank,s.p.getCanonicalRank());assertTrue(s.p.getServiceRecognition().records().isEmpty());assertFalse(KOMEServicePromotionService.migrate(s.f.player));
        }
    }
    @Test public void staffStructuralRelationshipsAreNotEarnedEvidenceAfterRestart()throws Exception {
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()){
            assertTrue(KOMESerfKnightRelationshipService.force(s.f.data,s.f.player.id,KOMEProgressionNpcRankService.referenceOf(s.master),KOMESerfKnightRelationshipService.ForceLevel.KNIGHT).success);
            s.p.readFromNBT(s.p.writeToNBT());assertEquals(0,KOMEServicePromotionService.duties(s.state));assertTrue(KOMEServicePromotionService.migrate(s.f.player));assertTrue(s.p.getServiceRecognition().records().isEmpty());
        }
    }
    @Test public void activeOrdinaryObjectivesKeepTokenAndPreviouslyReportedCreditAcrossMigration()throws Exception {
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()){
            s.state.assignDuty(KOMESerfKnightDutyType.COURIER,null);s.state.completeDuty(KOMESerfKnightDutyType.COURIER);
            KOMESerfKnightTrialAssignment a=KOMESerfKnightTrialAssignment.create(KOMESerfKnightTrial.forId("recovery"),KOMEProgressionNpcRankService.referenceOf(s.master),0,0);a.data.setBoolean("OrdinaryDuty",true);s.state.assignDuty(KOMESerfKnightDutyType.RECOVERY,a.writeToNBT());s.state.setTrial(a);
            assertTrue(KOMEServicePromotionService.migrate(s.f.player));for(int i=0;i<3;i++){s.p.readFromNBT(s.p.writeToNBT());assertEquals(a.assignmentToken,s.state.getTrialAssignment().assignmentToken);assertEquals(1,KOMEServicePromotionService.duties(s.state));}
        }
    }
}
