package kome.common.data;

import java.util.Random;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.*;

public class KOMEProgressionRankSummaryTest {
    @Test public void ladderAndCanonicalSerfRequirementsAreProjectedInOrder() {
        assertArrayEquals(new String[] {"Serf", "Knight", "Lord", "Prince"}, KOMEProgressionRankSummary.LADDER);
        KOMEPlayerProgression player=serf();KOMESerfKnightProgression state=player.getSerfKnightProgression();
        assertTrue(KOMESerfKnightService.setSerfdomMaster(state,npc("Master")).success);
        completeDuty(state,KOMESerfKnightDutyType.PROVISIONING);
        KOMEProgressionRankSummary summary=KOMEProgressionRankSummary.project(player,149D);
        assertEquals("Serf",summary.currentRank);assertEquals("Knight",summary.nextRank);assertEquals("Requirements for Knight",summary.promotionTitle);
        assertEquals(4,summary.requirements.size());
        assertRequirement(summary,0,"Faction Alignment",149,150,false);
        assertRequirement(summary,1,"Duties",1,3,false);
        assertRequirement(summary,2,"Trial of Knighthood",0,1,false);
        assertRequirement(summary,3,"Master's Parting Gift",0,1,false);
    }

    @Test public void noAssignmentHasNoFakePanelAndActiveDutyIsHumanReadable() {
        KOMEPlayerProgression player=serf();KOMESerfKnightProgression state=player.getSerfKnightProgression();
        assertTrue(KOMESerfKnightService.setSerfdomMaster(state,npc("Master")).success);
        assertFalse(KOMEProgressionRankSummary.project(player,0).hasActivity());
        assertTrue(KOMESerfKnightService.assignDuty(state,KOMESerfKnightDutyType.PROVISIONING,null,1L).success);
        KOMEProgressionRankSummary active=KOMEProgressionRankSummary.project(player,0);
        assertEquals("Current Duty",active.activityHeading);assertEquals("Deliver Provisions",active.activityTitle);
        assertTrue(active.activityObjective.contains("requested provisions"));assertFalse(active.activityObjective.contains("UUID"));
    }

    @Test public void awaitingAndActiveTrialFollowCanonicalStateWithoutIdentifiers() {
        KOMEPlayerProgression player=serf();KOMESerfKnightProgression state=player.getSerfKnightProgression();
        assertTrue(KOMESerfKnightService.setSerfdomMaster(state,npc("Master")).success);completeAllDuties(state);
        assertTrue(KOMESerfKnightService.setProspectiveLiege(state,npc("Liege")).success);
        KOMEProgressionRankSummary waiting=KOMEProgressionRankSummary.project(player,150);
        assertEquals("Trial of Knighthood",waiting.activityHeading);assertEquals("Awaiting assignment",waiting.activityTitle);
        assertEquals("Awaiting assignment from your Liege.",waiting.activityObjective);
        assertTrue(KOMESerfKnightService.assignTrial(state,new Random(1L),9L).success);
        KOMEProgressionRankSummary active=KOMEProgressionRankSummary.project(player,150);
        assertEquals(KOMESerfKnightTrial.forId(state.getTrialId()).displayName,active.activityTitle);assertTrue(active.activityObjective.length()>0);
        assertFalse(active.activityObjective.contains(state.getTrialAssignment().assignmentToken));
    }

    @Test public void completedCanonicalStateUpdatesQuotasAndFutureRequirementsStayHidden() {
        KOMEPlayerProgression player=serf();KOMESerfKnightProgression state=player.getSerfKnightProgression();
        assertTrue(KOMESerfKnightService.setSerfdomMaster(state,npc("Master")).success);completeAllDuties(state);
        assertTrue(KOMESerfKnightService.setProspectiveLiege(state,npc("Liege")).success);
        assertTrue(KOMESerfKnightService.assignTrial(state,new Random(0L),9L).success);
        assertTrue(KOMESerfKnightService.completeTrial(state).success);assertTrue(KOMESerfKnightService.recordPartingGift(state).success);
        KOMEProgressionRankSummary ready=KOMEProgressionRankSummary.project(player,150);
        for(KOMEProgressionRankSummary.Requirement requirement:ready.requirements)assertTrue(requirement.complete);
        player.setCanonicalRank(KOMEProgressionRank.KNIGHT);
        KOMEProgressionRankSummary knight=KOMEProgressionRankSummary.project(player,150);
        assertEquals("Knight",knight.currentRank);assertEquals("Lord",knight.nextRank);assertTrue(knight.requirements.isEmpty());
    }

    private static KOMEPlayerProgression serf(){KOMEPlayerProgression player=new KOMEPlayerProgression();player.setCanonicalRank(KOMEProgressionRank.SERF);return player;}
    private static KOMEProgressionNpcRef npc(String name){return new KOMEProgressionNpcRef(UUID.randomUUID().toString(),name,"rohan",0,0,64,0);}
    private static void completeDuty(KOMESerfKnightProgression state,KOMESerfKnightDutyType type){assertTrue(KOMESerfKnightService.assignDuty(state,type,null,type.ordinal()+1L).success);assertTrue(KOMESerfKnightService.completeDuty(state,type).success);}
    private static void completeAllDuties(KOMESerfKnightProgression state){for(KOMESerfKnightDutyType type:KOMESerfKnightDutyType.values())completeDuty(state,type);}
    private static void assertRequirement(KOMEProgressionRankSummary summary,int index,String label,int current,int required,boolean complete){KOMEProgressionRankSummary.Requirement row=summary.requirements.get(index);assertEquals(label,row.label);assertEquals(current,row.current);assertEquals(required,row.required);assertEquals(complete,row.complete);}
}
