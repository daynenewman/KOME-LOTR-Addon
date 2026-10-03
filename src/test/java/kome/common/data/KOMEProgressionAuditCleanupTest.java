package kome.common.data;

import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import org.junit.Test;

public class KOMEProgressionAuditCleanupTest {
    @Test public void advancementProjectionContainsOnlyAdvancementsOutsideBaseline() {
        for (String group : new String[] {"wanderer", "serf", "knight", "lord", "prince_king"}) {
            List<KOMEProgressionAchievement> visible = KOMEProgressionAchievement.forAdvancementsGroup(group);
            assertFalse(visible.isEmpty());
            for (KOMEProgressionAchievement achievement : visible) {
                assertEquals("Advancement", achievement.category);
            }
        }
        assertFalse(KOMEProgressionAchievement.forAdvancementsGroup("baseline").isEmpty());
        assertFalse(contains(KOMEProgressionAchievement.forAdvancementsGroup("serf"), "serf.food_quota_1"));
        assertFalse(contains(KOMEProgressionAchievement.forAdvancementsGroup("knight"), "knight.drop_quota_1"));
        assertFalse(contains(KOMEProgressionAchievement.forAdvancementsGroup("lord"), "lord.title_prince_king"));
    }

    @Test public void obsoleteSerfQuotaRollsAreRetiredButHigherRankRollsRemain() {
        assertFalse(KOMEProgressionTaskGenerator.canRoll("serf.food_quota_1"));
        assertFalse(KOMEProgressionTaskGenerator.canRoll("serf.drink_quota"));
        assertTrue(KOMEProgressionTaskGenerator.canRoll("knight.drop_quota_1"));
        assertTrue(KOMEProgressionTaskGenerator.canRoll("knight.faction_1"));
        assertTrue(KOMEProgressionTaskGenerator.canRoll("lord.fell_beast"));
    }

    @Test public void offeringsUseCommittedLiegeInsteadOfLegacyPledgedLord() throws Exception {
        String lords = new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/data/KOMEProgressionLords.java")), StandardCharsets.UTF_8);
        String packet = new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/network/KOMEPacketLordAction.java")), StandardCharsets.UTF_8);
        String inventory = new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/data/KOMEProgressionOfferingInventory.java")), StandardCharsets.UTF_8);
        assertTrue(lords.contains("getSerfKnightProgression().hasLiege()"));
        assertTrue(lords.contains("findNearbyCanonicalLiege"));
        assertTrue(lords.contains("isCanonicalLiege"));
        assertFalse(lords.substring(lords.indexOf("public static void openOfferings"), lords.indexOf("public static LOTRHireableBase findNearbyPledgeLord")).contains("hasPledgedLord()"));
        assertTrue(packet.contains("isCanonicalLiege(entity, progression)"));
        assertTrue(inventory.contains("Rank Offerings"));
    }

    @Test public void historicalSerfAlignmentMarkerMatchesCanonicalPledgedFactionRule() throws Exception {
        String auto = new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/data/KOMEProgressionAutoCompleter.java")), StandardCharsets.UTF_8);
        assertTrue(auto.contains("\"serf.alignment_100\", pledge != null && lotrData.getAlignment(pledge) >= KOMESerfKnightService.REQUIRED_ALIGNMENT"));
        assertFalse(auto.contains("serf.alignment_100\", hasAnyAlignmentAtLeast"));
    }

    private static boolean contains(List<KOMEProgressionAchievement> values, String id) {
        for (KOMEProgressionAchievement achievement : values) if (id.equals(achievement.id)) return true;
        return false;
    }
}
