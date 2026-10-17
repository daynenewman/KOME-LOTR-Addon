package kome.common.data;

import java.util.Random;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.*;

public class KOMEProgressionRankSummaryTest {
    @Test public void ladderAndCanonicalSerfRequirementsAreProjectedInOrder() {
        assertArrayEquals(new String[]{"Commoner","Knight","Lord","Prince"},KOMEProgressionRankSummary.LADDER);
        KOMEPlayerProgression player=serf();KOMESerfKnightProgression state=player.getSerfKnightProgression();
        assertTrue(KOMESerfKnightService.setSerfdomMaster(state,npc("Master")).success);completeDuty(state,KOMESerfKnightDutyType.PROVISIONING);
        KOMEProgressionRankSummary summary=KOMEProgressionRankSummary.project(player,149D);
        assertEquals("Commoner",summary.currentRank);assertEquals("Knight",summary.nextRank);assertEquals(2,summary.requirements.size());
        assertRequirement(summary,0,"Reported service",1,3,false);assertRequirement(summary,1,"Faction Alignment",149,150,false);
        for(KOMEProgressionRankSummary.Requirement r:summary.requirements){assertFalse(r.label.contains("Trial"));assertFalse(r.label.contains("Gift"));}
    }

    @Test public void factionSpecificTitlesAreProjectedWithoutChangingCanonicalRank() {
        KOMEPlayerProgression player=serf();
        KOMEProgressionRankSummary summary=KOMEProgressionRankSummary.project(player,149D,"ROHAN");
        assertEquals(KOMEProgressionRank.SERF,player.getCanonicalRank());
        assertEquals("rohan",KOMEAlliance.normalizeFactionKey(summary.factionKey));
        assertEquals("Commoner",summary.currentRank);
        assertEquals("Knight",summary.nextRank);
        assertEquals("Requirements for Knight",summary.promotionTitle);
    }

    @Test public void noAssignmentHasNoFakePanelAndActiveDutyIsHumanReadable() {
        KOMEPlayerProgression player=serf();KOMESerfKnightProgression state=player.getSerfKnightProgression();
        assertTrue(KOMESerfKnightService.setSerfdomMaster(state,npc("Master")).success);
        assertTrue(KOMEProgressionRankSummary.project(player,0).hasActivity());
        assertTrue(KOMESerfKnightService.assignDuty(state,KOMESerfKnightDutyType.PROVISIONING,null,1L).success);
        KOMEProgressionRankSummary active=KOMEProgressionRankSummary.project(player,0);
        assertEquals("Current Duty",active.activityHeading);assertEquals("Provisions",active.activityTitle);
        assertTrue(active.activityObjective.contains("requested provisions"));assertFalse(active.activityObjective.contains("UUID"));
    }

    @Test public void awaitingAndActiveTrialFollowCanonicalStateWithoutIdentifiers() {
        KOMEPlayerProgression player=serf();KOMESerfKnightProgression state=player.getSerfKnightProgression();
        assertTrue(KOMESerfKnightService.setSerfdomMaster(state,npc("Master")).success);completeAllDuties(state);
        assertTrue(KOMESerfKnightService.commitLiegeForTrial(state,npc("Liege")).success);
        assertTrue(KOMESerfKnightService.assignTrial(state,new Random(1L),9L).success);
        KOMEProgressionRankSummary summary=KOMEProgressionRankSummary.project(player,150,"rohan");
        assertEquals("Master service",summary.activityHeading);assertTrue(summary.activityObjective.contains("Report to your Master"));
        assertFalse(summary.activityObjective.contains(state.getTrialAssignment().assignmentToken));assertFalse(summary.activityObjective.contains("Trial"));
    }

    @Test public void completedDutiesPromptSeekingLiegeBeforeAnyLiegeIsSelected() {
        KOMEPlayerProgression player=serf();KOMESerfKnightProgression state=player.getSerfKnightProgression();
        assertTrue(KOMESerfKnightService.setSerfdomMaster(state,npc("Master")).success);completeAllDuties(state);
        state.setLastAssignmentEpochDay(KOMESerfKnightService.calendarDayNow());
        KOMEProgressionRankSummary summary=KOMEProgressionRankSummary.project(player,150D,"ROHAN",UUID.randomUUID());
        assertEquals("Master service",summary.activityHeading);assertEquals("Master",summary.activityTitle);
        assertTrue(summary.activityObjective.contains("Report to your Master"));assertFalse(state.hasLiege());
    }

    @Test public void completedCanonicalStateUpdatesQuotasAndHigherRequirementsAppearAtKnight() {
        KOMEPlayerProgression player=serf();KOMESerfKnightProgression state=player.getSerfKnightProgression();
        assertTrue(KOMESerfKnightService.setSerfdomMaster(state,npc("Master")).success);completeAllDuties(state);
        assertTrue(KOMESerfKnightService.commitLiegeForTrial(state,npc("Liege")).success);
        assertTrue(KOMESerfKnightService.assignTrial(state,new Random(0L),9L).success);
        assertTrue(KOMESerfKnightService.completeTrial(state).success);assertTrue(KOMESerfKnightService.recordPartingGift(state).success);
        KOMEProgressionRankSummary ready=KOMEProgressionRankSummary.project(player,150);
        for(KOMEProgressionRankSummary.Requirement requirement:ready.requirements)assertTrue(requirement.complete);
        player.setCanonicalRank(KOMEProgressionRank.KNIGHT);
        KOMEProgressionRankSummary knight=KOMEProgressionRankSummary.project(player,150);
        assertEquals("Knight",knight.currentRank);assertEquals("Lord",knight.nextRank);
        assertEquals(2,knight.requirements.size());
        assertEquals("Reported commissions",knight.requirements.get(0).label);assertEquals(3,knight.requirements.get(0).required);
        assertEquals("Faction Alignment",knight.requirements.get(1).label);assertEquals(2000,knight.requirements.get(1).required);
        assertFalse(knight.activityObjective.contains("Trial"));
    }

    @Test public void legacyKnightFlagsDoNotExposeFalsePromotionReadiness() {
        KOMEPlayerProgression player=new KOMEPlayerProgression();player.setCanonicalRank(KOMEProgressionRank.KNIGHT);
        KOMEHigherRankTransitionService.Transition transition=KOMEHigherRankTransitionService.forCurrentRank(KOMEProgressionRank.KNIGHT);
        for(KOMEHigherRankTransitionService.RequirementGroup group:transition.groups)for(String id:group.achievementIds)player.grant(id);
        KOMEProgressionRankSummary summary=KOMEProgressionRankSummary.project(player,2000D,"rohan");
        assertEquals("Lord service",summary.activityHeading);assertEquals("Regional service",summary.activityTitle);
        assertFalse(player.isCompleted(KOMEProgressionAchievement.forID("knight.craftsman")));
        assertFalse(summary.requirements.get(0).complete);assertTrue(summary.requirements.get(1).complete);assertEquals(2,summary.requirements.size());
    }

    @Test public void lordRankProjectsOnlyQuotaAndHighestStandingRequirements() {
        KOMEPlayerProgression player=new KOMEPlayerProgression();player.setCanonicalRank(KOMEProgressionRank.LORD);
        KOMEProgressionRankSummary summary=KOMEProgressionRankSummary.project(player,2500D,"rohan");
        assertEquals("Lord",summary.currentRank);assertEquals("Prince",summary.nextRank);assertEquals(3,summary.requirements.size());
        assertEquals("Rank Quotas",summary.requirements.get(0).label);assertEquals(3,summary.requirements.get(0).required);
        assertEquals("Highest Standing",summary.requirements.get(1).label);assertEquals(2,summary.requirements.get(1).required);
        assertEquals("Reach Highest Rank",summary.requirements.get(2).label);
    }

    private static KOMEPlayerProgression serf(){KOMEPlayerProgression player=new KOMEPlayerProgression();player.setCanonicalRank(KOMEProgressionRank.SERF);return player;}
    private static KOMEProgressionNpcRef npc(String name){return new KOMEProgressionNpcRef(UUID.randomUUID().toString(),name,"rohan",0,0,64,0);}
    private static void completeDuty(KOMESerfKnightProgression state,KOMESerfKnightDutyType type){assertTrue(KOMESerfKnightService.assignDuty(state,type,null,type.ordinal()+1L).success);assertTrue(KOMESerfKnightService.completeDuty(state,type).success);}
    private static void completeAllDuties(KOMESerfKnightProgression state){for(KOMESerfKnightDutyType type:new KOMESerfKnightDutyType[]{KOMESerfKnightDutyType.PROVISIONING,KOMESerfKnightDutyType.PROFESSION,KOMESerfKnightDutyType.COURIER})completeDuty(state,type);}
    private static void assertRequirement(KOMEProgressionRankSummary summary,int index,String label,int current,int required,boolean complete){KOMEProgressionRankSummary.Requirement row=summary.requirements.get(index);assertEquals(label,row.label);assertEquals(current,row.current);assertEquals(required,row.required);assertEquals(complete,row.complete);}
    private static void assertRequirement(java.util.List<KOMEProgressionRankSummary.Requirement> requirements,int index,String label,int current,int required,boolean complete){KOMEProgressionRankSummary.Requirement row=requirements.get(index);assertEquals(label,row.label);assertEquals(current,row.current);assertEquals(required,row.required);assertEquals(complete,row.complete);}
}
