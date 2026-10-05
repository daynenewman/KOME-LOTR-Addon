package kome.common.data;

import org.junit.Test;
import static org.junit.Assert.*;

public class KOMEProgressionQuotaRankGateTest {
    @Test public void futureRankQuotaCannotCompleteUntilCanonicalRankIsReached() {
        KOMEPlayerProgression progression = new KOMEPlayerProgression();
        assertTrue(progression.setAssignment("knight.drop_quota_1", "Collect 1 units of Cooked Chicken"));
        progression.addQuotaDelivered("knight.drop_quota_1", 1);

        assertEquals(0, KOMEProgressionQuotas.applyCompletedQuotas(progression));
        assertFalse(progression.isCompleted(KOMEProgressionAchievement.forID("knight.drop_quota_1")));

        progression.setCanonicalRank(KOMEProgressionRank.KNIGHT);
        assertEquals(1, KOMEProgressionQuotas.applyCompletedQuotas(progression));
        assertTrue(progression.isCompleted(KOMEProgressionAchievement.forID("knight.drop_quota_1")));
    }

    @Test public void aggregateDropQuotaMarkerAlsoObeysCanonicalRankGate() {
        KOMEPlayerProgression progression = new KOMEPlayerProgression();
        progression.setAssignment("knight.drop_quota_1", "Collect 1 units of Cooked Chicken");
        progression.setAssignment("knight.drop_quota_2", "Collect 1 units of Cooked Chicken");
        progression.addQuotaDelivered("knight.drop_quota_1", 1);
        progression.addQuotaDelivered("knight.drop_quota_2", 1);

        assertEquals(0, KOMEProgressionQuotas.applyCompletedQuotas(progression));
        assertFalse(progression.isCompleted(KOMEProgressionAchievement.forID("knight.deliver_drops")));

        progression.setCanonicalRank(KOMEProgressionRank.KNIGHT);
        assertEquals(3, KOMEProgressionQuotas.applyCompletedQuotas(progression));
        assertTrue(progression.isCompleted(KOMEProgressionAchievement.forID("knight.drop_quota_1")));
        assertTrue(progression.isCompleted(KOMEProgressionAchievement.forID("knight.drop_quota_2")));
        assertTrue(progression.isCompleted(KOMEProgressionAchievement.forID("knight.deliver_drops")));
    }
}
