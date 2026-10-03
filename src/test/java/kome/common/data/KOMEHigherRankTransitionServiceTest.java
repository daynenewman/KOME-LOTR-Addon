package kome.common.data;

import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMEHigherRankTransitionServiceTest {
    @Test public void historicalKnightDefinitionsRemainWhileLordPrinceRequirementsAreUnchanged() {
        KOMEHigherRankTransitionService.Transition knight =
            KOMEHigherRankTransitionService.forCurrentRank(KOMEProgressionRank.KNIGHT);
        assertNotNull(knight);
        assertEquals(KOMEProgressionRank.LORD, knight.toRank);
        assertEquals("knight.title_lord", knight.completionMarkerId);
        assertEquals(2, knight.groups.length);
        assertEquals("Rank Quotas", knight.groups[0].label);
        assertEquals("Rise in Standing", knight.groups[1].label);
        for (KOMEHigherRankTransitionService.RequirementGroup group : knight.groups) {
            for (String id : group.achievementIds) {
                KOMEProgressionAchievement achievement = KOMEProgressionAchievement.forID(id);
                assertNotNull(id, achievement);
                assertFalse("General Advancements must not gate canonical rank: " + id,
                    "Advancement".equals(achievement.category));
            }
        }

        KOMEHigherRankTransitionService.Transition lord =
            KOMEHigherRankTransitionService.forCurrentRank(KOMEProgressionRank.LORD);
        assertNotNull(lord);
        assertEquals(KOMEProgressionRank.PRINCE, lord.toRank);
        assertEquals("lord.title_prince_king", lord.completionMarkerId);
        assertEquals("Rank Quotas", lord.groups[0].label);
        assertEquals("Highest Standing", lord.groups[1].label);
        assertFalse(java.util.Arrays.asList(lord.groups[1].achievementIds)
            .contains("lord.declare_kingship"));
    }

    @Test public void legacyKnightChecklistCannotPerformCanonicalLordPromotion() {
        KOMEWorldData data = new KOMEWorldData("higher-ranks");
        UUID playerId = UUID.randomUUID();
        KOMEPlayerProgression progression = data.getProgression(playerId);
        progression.setCanonicalRank(KOMEProgressionRank.KNIGHT);
        KOMEHigherRankTransitionService.Transition transition =
            KOMEHigherRankTransitionService.forCurrentRank(KOMEProgressionRank.KNIGHT);

        KOMEHigherRankTransitionService.Result blocked =
            KOMEHigherRankTransitionService.promote(data, playerId, transition.completionMarkerId);
        assertFalse(blocked.success);
        assertEquals(KOMEProgressionRank.KNIGHT, progression.getCanonicalRank());
        assertFalse(progression.isCompleted(
            KOMEProgressionAchievement.forID(transition.completionMarkerId)));

        for (KOMEHigherRankTransitionService.RequirementGroup group : transition.groups)
            for (String id : group.achievementIds) assertTrue(progression.grant(id));

        KOMEHigherRankTransitionService.Result promoted =
            KOMEHigherRankTransitionService.promote(data, playerId, transition.completionMarkerId);
        assertFalse("Legacy Knight checklist is now historical only", promoted.success);
        assertEquals(KOMEProgressionRank.KNIGHT, progression.getCanonicalRank());
        assertFalse(progression.isCompleted(KOMEProgressionAchievement.forID("knight.title_lord")));
        assertFalse(KOMEHigherRankTransitionService.requirementsComplete(progression,transition));
    }

    @Test public void lordPromotionReachesHighestPlayerRankWithoutGrantingPoliticalOfficeStep() {
        KOMEWorldData data = new KOMEWorldData("highest-rank");
        UUID playerId = UUID.randomUUID();
        KOMEPlayerProgression progression = data.getProgression(playerId);
        progression.setCanonicalRank(KOMEProgressionRank.LORD);
        KOMEHigherRankTransitionService.Transition transition =
            KOMEHigherRankTransitionService.forCurrentRank(KOMEProgressionRank.LORD);
        for (KOMEHigherRankTransitionService.RequirementGroup group : transition.groups)
            for (String id : group.achievementIds) progression.grant(id);

        assertTrue(KOMEHigherRankTransitionService.promote(
            data, playerId, transition.completionMarkerId).success);
        assertEquals(KOMEProgressionRank.PRINCE, progression.getCanonicalRank());
        assertTrue(progression.isCompleted(
            KOMEProgressionAchievement.forID("lord.title_prince_king")));
        assertFalse(progression.isCompleted(
            KOMEProgressionAchievement.forID("lord.declare_kingship")));
    }
}
