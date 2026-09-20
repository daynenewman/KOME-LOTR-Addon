package kome.common.data;

import java.util.HashSet;
import java.util.Arrays;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMEProgressionSchemaTest {
    @Test public void ranksArePermanentOrderedAndStrictlyKeyed() {
        assertEquals(5, KOMEProgressionRank.values().length);
        assertTrue(KOMEProgressionRank.WANDERER.order < KOMEProgressionRank.SERF.order);
        assertTrue(KOMEProgressionRank.SERF.order < KOMEProgressionRank.KNIGHT.order);
        assertTrue(KOMEProgressionRank.KNIGHT.order < KOMEProgressionRank.LORD.order);
        assertTrue(KOMEProgressionRank.LORD.order < KOMEProgressionRank.PRINCE.order);
        assertEquals(KOMEProgressionRank.KNIGHT, KOMEProgressionRank.forKey("knight"));
        assertNull(KOMEProgressionRank.forKey("King"));
        assertNull(KOMEProgressionRank.forKey("prince_king"));
    }

    @Test public void branchesHaveDeterministicSpecialtyEligibility() {
        assertEquals(5, KOMEProgressionBranch.all().size());
        for (String id : new String[] {"exploration_adventure", "craft_lore", "husbandry_farming", "warfare_siegecraft"}) {
            KOMEProgressionBranch branch = KOMEProgressionBranch.forId(id);
            assertNotNull(branch); assertFalse(branch.specialty); assertTrue(branch.eligibleFactionKeys.isEmpty());
        }
        KOMEProgressionBranch horsemanship = KOMEProgressionBranch.forId("rohan_horsemanship");
        assertTrue(horsemanship.specialty); assertTrue(horsemanship.isEligibleForFaction("rohan"));
        assertFalse(horsemanship.isEligibleForFaction("gondor")); assertNull(KOMEProgressionBranch.forId("unknown"));
        Set<String> ids = new HashSet<String>();
        for (KOMEProgressionBranch branch : KOMEProgressionBranch.all()) assertTrue(ids.add(branch.id));
    }

    @Test public void canonicalMetadataIsValidatedAndDeterministic() {
        KOMEProgressionAchievement core = KOMEProgressionAchievement.canonical("test.explore", "Explore", "Travel", "exploration_adventure",
                KOMEProgressionRank.WANDERER, 1, KOMEProgressionCompletionMode.AUTOMATIC, "unlock.travel");
        assertEquals("exploration_adventure", core.branch); assertEquals(KOMEProgressionRank.WANDERER, core.rank);
        assertEquals(1, core.tier); assertEquals(KOMEProgressionCompletionMode.AUTOMATIC, core.completionMode); assertEquals("unlock.travel", core.rewardId);
        KOMEProgressionAchievement specialty = KOMEProgressionAchievement.canonical("test.horse", "Ride", "Ride", "rohan_horsemanship",
                KOMEProgressionRank.SERF, 2, KOMEProgressionCompletionMode.MANUAL, "unlock.horse");
        assertEquals("rohan_horsemanship", specialty.branch);
        assertTrue(KOMEProgressionBranch.forId(specialty.branch).isEligibleForFaction("rohan"));
        assertFalse(KOMEProgressionBranch.forId(specialty.branch).isEligibleForFaction("gondor"));
        try { KOMEProgressionAchievement.canonical("test.bad", "Bad", "Bad", "missing", KOMEProgressionRank.SERF, 1, KOMEProgressionCompletionMode.MANUAL, null); fail(); } catch (IllegalArgumentException expected) { }
        try { KOMEProgressionAchievement.canonical("test.badTier", "Bad", "Bad", "craft_lore", KOMEProgressionRank.SERF, 0, KOMEProgressionCompletionMode.MANUAL, null); fail(); } catch (IllegalArgumentException expected) { }
        try { KOMEProgressionAchievement.validateCanonicalDefinitions(Arrays.asList(core, core)); fail(); } catch (IllegalStateException expected) { }
    }

    @Test public void legacyRegistryAndPoliticalOfficeRemainUnchanged() {
        assertEquals(96, KOMEProgressionAchievement.ALL.size());
        for (String id : new String[] {"baseline.fast_travel", "wanderer.expert_traveler", "serf.quest_seeker", "knight.craftsman", "lord.redstone", "prince_king.true_silver"}) assertNotNull(KOMEProgressionAchievement.forID(id));
        assertNull(KOMEProgressionAchievement.forID("lord.redstone").branch);
        KOMEWorldData data = new KOMEWorldData("schema"); java.util.UUID player = java.util.UUID.randomUUID();
        assertTrue(KOMERulerService.assignRuler(data, "gondor", player, "Ruler"));
        assertFalse(data.getProgression(player).isCompleted(KOMEProgressionAchievement.forID("prince_king.true_silver")));
    }
}
