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
        assertEquals(3,summary.requirements.size());
        assertRequirement(summary,0,"Faction Alignment",149,150,false);
        assertRequirement(summary,1,"Duties",1,3,false);
        assertRequirement(summary,2,"Trial of Standing",0,1,false);
        for(KOMEProgressionRankSummary.Requirement requirement:summary.requirements)assertFalse(requirement.label.contains("Gift"));
        KOMEProgressionRankSummary.Requirement duties=summary.requirements.get(1);
        assertTrue(duties.hasChildren());assertEquals(3,duties.children.size());
        assertRequirement(duties.children,0,"Provisioning",1,1,true);
        assertRequirement(duties.children,1,"Profession",0,1,false);
        assertRequirement(duties.children,2,"Courier",0,1,false);
    }

    @Test public void factionSpecificTitlesAreProjectedWithoutChangingCanonicalRank() {
        KOMEPlayerProgression player=serf();
        KOMEProgressionRankSummary summary=KOMEProgressionRankSummary.project(player,149D,"ROHAN");
        assertEquals(KOMEProgressionRank.SERF,player.getCanonicalRank());
        assertEquals("rohan",KOMEAlliance.normalizeFactionKey(summary.factionKey));
        assertEquals("Eorling-at-Arms",summary.currentRank);
        assertEquals("Rider of Rohan",summary.nextRank);
        assertEquals("Requirements for Rider of Rohan",summary.promotionTitle);
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
        assertTrue(KOMESerfKnightService.commitLiegeForTrial(state,npc("Liege")).success);
        KOMEProgressionRankSummary waiting=KOMEProgressionRankSummary.project(player,150,"rohan");
        assertEquals("Trial of Standing",waiting.activityHeading);assertEquals("Ready to request",waiting.activityTitle);assertEquals("Request your Trial of Standing from your Liege.",waiting.activityObjective);
        assertTrue(KOMESerfKnightService.assignTrial(state,new Random(1L),9L).success);
        KOMEProgressionRankSummary active=KOMEProgressionRankSummary.project(player,150,"rohan");
        assertEquals(KOMESerfKnightTrial.forId(state.getTrialId()).displayName,active.activityTitle);assertTrue(active.activityObjective.length()>0);
        assertFalse(active.activityObjective.contains(state.getTrialAssignment().assignmentToken));
    }

    @Test public void completedDutiesPromptSeekingLiegeBeforeAnyLiegeIsSelected() {
        KOMEPlayerProgression player=serf();KOMESerfKnightProgression state=player.getSerfKnightProgression();
        assertTrue(KOMESerfKnightService.setSerfdomMaster(state,npc("Master")).success);completeAllDuties(state);
        state.setLastAssignmentEpochDay(KOMESerfKnightService.calendarDayNow());
        assertFalse(state.getLiege().isSet());

        KOMEProgressionRankSummary summary=KOMEProgressionRankSummary.project(
            player,150D,"ROHAN",UUID.randomUUID());

        assertEquals("Trial of Standing",summary.activityHeading);
        assertEquals("Seek a prospective Liege",summary.activityTitle);
        assertEquals(KOMEProgressionNativeAuthority.guidance("rohan"),summary.activityObjective);
        assertFalse(state.getLiege().isSet());
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
        assertEquals(3,knight.requirements.size());
        assertEquals("Service to your Liege",knight.requirements.get(0).label);assertEquals(3,knight.requirements.get(0).required);
        assertEquals("Faction Alignment",knight.requirements.get(1).label);assertEquals(2000,knight.requirements.get(1).required);
        assertEquals("Trial of Lordship",knight.requirements.get(2).label);
    }

    @Test public void legacyKnightFlagsDoNotExposeFalsePromotionReadiness() {
        KOMEPlayerProgression player=new KOMEPlayerProgression();player.setCanonicalRank(KOMEProgressionRank.KNIGHT);
        KOMEHigherRankTransitionService.Transition transition=KOMEHigherRankTransitionService.forCurrentRank(KOMEProgressionRank.KNIGHT);
        for(KOMEHigherRankTransitionService.RequirementGroup group:transition.groups)for(String id:group.achievementIds)player.grant(id);
        KOMEProgressionRankSummary summary=KOMEProgressionRankSummary.project(player,2000D,"rohan");
        assertEquals("Trial of Lordship",summary.activityHeading);assertEquals("Service to your Liege",summary.activityTitle);
        assertFalse(player.isCompleted(KOMEProgressionAchievement.forID("knight.craftsman")));
        assertFalse(summary.requirements.get(0).complete);assertTrue(summary.requirements.get(1).complete);assertFalse(summary.requirements.get(2).complete);
    }

    @Test public void lordRankProjectsOnlyQuotaAndHighestStandingRequirements() {
        KOMEPlayerProgression player=new KOMEPlayerProgression();player.setCanonicalRank(KOMEProgressionRank.LORD);
        KOMEProgressionRankSummary summary=KOMEProgressionRankSummary.project(player,2500D,"rohan");
        assertEquals("Captain",summary.currentRank);assertEquals("Marshal",summary.nextRank);assertEquals(3,summary.requirements.size());
        assertEquals("Rank Quotas",summary.requirements.get(0).label);assertEquals(3,summary.requirements.get(0).required);
        assertEquals("Highest Standing",summary.requirements.get(1).label);assertEquals(2,summary.requirements.get(1).required);
        assertEquals("Reach Highest Rank",summary.requirements.get(2).label);
    }

    private static KOMEPlayerProgression serf(){KOMEPlayerProgression player=new KOMEPlayerProgression();player.setCanonicalRank(KOMEProgressionRank.SERF);return player;}
    private static KOMEProgressionNpcRef npc(String name){return new KOMEProgressionNpcRef(UUID.randomUUID().toString(),name,"rohan",0,0,64,0);}
    private static void completeDuty(KOMESerfKnightProgression state,KOMESerfKnightDutyType type){assertTrue(KOMESerfKnightService.assignDuty(state,type,null,type.ordinal()+1L).success);assertTrue(KOMESerfKnightService.completeDuty(state,type).success);}
    private static void completeAllDuties(KOMESerfKnightProgression state){for(KOMESerfKnightDutyType type:KOMESerfKnightDutyType.values())completeDuty(state,type);}
    private static void assertRequirement(KOMEProgressionRankSummary summary,int index,String label,int current,int required,boolean complete){KOMEProgressionRankSummary.Requirement row=summary.requirements.get(index);assertEquals(label,row.label);assertEquals(current,row.current);assertEquals(required,row.required);assertEquals(complete,row.complete);}
    private static void assertRequirement(java.util.List<KOMEProgressionRankSummary.Requirement> requirements,int index,String label,int current,int required,boolean complete){KOMEProgressionRankSummary.Requirement row=requirements.get(index);assertEquals(label,row.label);assertEquals(current,row.current);assertEquals(required,row.required);assertEquals(complete,row.complete);}
}
