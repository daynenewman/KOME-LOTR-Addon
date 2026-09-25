package kome.common.data;

import java.util.Random;
import java.util.UUID;
import kome.common.KOMEAccessFixture;
import lotr.common.LOTRLevelData;
import lotr.common.fac.LOTRFaction;
import org.junit.Test;
import static org.junit.Assert.*;

/** Fresh-player server transitions through the real final Master service seam. */
public class KOMESerfKnightIntegratedJourneyTest {
    @Test public void freshPledgedPlayerReturnsToMasterForOneKnightPromotion() throws Exception {
        KOMEAccessFixture fixture=new KOMEAccessFixture();fixture.pledge(LOTRFaction.ROHAN);
        UUID playerId=fixture.player.id;
        KOMEPlayerProgression progression=fixture.data.getProgression(playerId);
        KOMESerfKnightProgression state=progression.getSerfKnightProgression();
        assertEquals(KOMEProgressionRank.WANDERER,progression.getCanonicalRank());
        assertEquals(LOTRFaction.ROHAN,LOTRLevelData.getData(fixture.player).getPledgeFaction());
        assertTrue(KOMEProgressionAutoCompleter.hasValidFactionCommitment(LOTRFaction.ROHAN));
        assertFalse("The historical miniquest record is not a prerequisite for service",
            progression.isCompleted(KOMEProgressionAchievement.forID("baseline.miniquests")));

        KOMEProgressionNpcRef master=ref("Master"),liege=ref("Liege");
        assertTrue(KOMESerfKnightService.setSerfdomMaster(state,master,KOMEProgressionNpcRank.UNRANKED,true).success);
        assertTrue(KOMECanonicalRankService.enterSerfdom(fixture.data,playerId));
        assertEquals(KOMEProgressionRank.SERF,progression.getCanonicalRank());
        long day=10L;
        for(KOMESerfKnightDutyType duty:KOMESerfKnightDutyType.values()){
            assertTrue(KOMESerfKnightService.assignDuty(state,duty,null,day++).success);
            assertTrue(KOMESerfKnightService.completeDuty(progression,duty).success);
        }
        assertTrue(KOMESerfKnightService.setProspectiveLiege(state,liege,KOMEProgressionNpcRank.LORD,true).success);
        assertTrue(KOMESerfKnightService.assignTrial(state,new Random(4L),day,playerId).success);
        assertTrue(KOMESerfKnightService.markTrialObjectiveComplete(state).success);
        assertTrue(state.isTrialCompleted());
        assertFalse(state.hasPartingGift());
        assertEquals(KOMEProgressionRank.SERF,progression.getCanonicalRank());
        KOMEProgressionRankSummary before=KOMEProgressionRankSummary.project(progression,150D);
        assertEquals(1,before.requirements.get(2).current);
        assertEquals(0,before.requirements.get(3).current);

        assertFalse(KOMESerfdomMasterService.conferKnighthood(fixture.data,playerId,liege,"rohan",150D).success);
        assertFalse(KOMESerfdomMasterService.conferKnighthood(fixture.data,playerId,master,"gondor",150D).success);
        assertFalse(KOMESerfdomMasterService.conferKnighthood(fixture.data,playerId,master,"rohan",149D).success);
        assertTrue(KOMESerfdomMasterService.conferKnighthood(fixture.data,playerId,master,"rohan",150D).success);
        assertTrue(state.hasPartingGift());assertTrue(state.isPromoted());
        assertEquals(KOMEProgressionRank.KNIGHT,progression.getCanonicalRank());
        assertTrue(state.getProspectiveLiege().hasSameIdentity(liege));
        assertTrue(KOMESerfKnightService.allDutiesComplete(state));
        assertTrue(progression.isCompleted(KOMEProgressionAchievement.forID("serf.title_knight")));
        assertEquals("Knight",KOMEProgressionRankSummary.project(progression,150D).currentRank);
        assertFalse("The Master cannot give a second gift",KOMESerfdomMasterService.conferKnighthood(fixture.data,playerId,master,"rohan",150D).success);
        KOMEPlayerProgression reloaded=new KOMEPlayerProgression();reloaded.readFromNBT(progression.writeToNBT());
        assertEquals(KOMEProgressionRank.KNIGHT,reloaded.getCanonicalRank());
        assertTrue(reloaded.getSerfKnightProgression().hasPartingGift());
        assertTrue(reloaded.getSerfKnightProgression().getProspectiveLiege().hasSameIdentity(liege));
    }

    private static KOMEProgressionNpcRef ref(String name){return new KOMEProgressionNpcRef(
        UUID.randomUUID().toString(),name,"rohan",0,0,64,0);}
}
