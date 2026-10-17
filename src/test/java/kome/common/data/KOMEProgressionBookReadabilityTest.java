package kome.common.data;

import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMEProgressionBookReadabilityTest {
    private KOMEPlayerProgression service(KOMEProgressionRank rank) {
        KOMEPlayerProgression p=new KOMEPlayerProgression();p.setCanonicalRank(rank);
        KOMEProgressionNpcRef npc=new KOMEProgressionNpcRef(UUID.randomUUID().toString(),
            "A very long native name with a title and a distant household","rohan",0,0,65,0);
        if(rank==KOMEProgressionRank.SERF)p.getSerfKnightProgression().setSerfdomMaster(npc);
        else p.getSerfKnightProgression().setLiege(npc);
        return p;
    }
    @Test public void idleInstructionsLeavePromotionQuotasInTheirOwnSection() {
        for(KOMEProgressionRank rank:new KOMEProgressionRank[]{KOMEProgressionRank.SERF,KOMEProgressionRank.KNIGHT}) {
            KOMEPlayerProgression p=service(rank);
            KOMEProgressionRankSummary summary=KOMEProgressionRankSummary.project(p,0,"rohan");
            assertEquals(2,summary.requirements.size());assertEquals(3,summary.requirements.get(0).required);
            assertEquals(rank==KOMEProgressionRank.SERF?150:2000,summary.requirements.get(1).required);
            assertTrue(summary.activityObjective.startsWith("Ask your"));
            assertFalse(summary.activityObjective.contains("alignment"));assertFalse(summary.activityObjective.contains("three"));
            assertTrue(summary.activityTitle.contains("very long native name"));
        }
    }
    @Test public void completedSerfServiceGivesOneAccurateNextStepForAlignmentAndReporting() {
        KOMEPlayerProgression p=service(KOMEProgressionRank.SERF);
        for(KOMESerfKnightDutyType type:new KOMESerfKnightDutyType[]{KOMESerfKnightDutyType.COURIER,KOMESerfKnightDutyType.PROVISIONING,KOMESerfKnightDutyType.RECOVERY}) {
            p.getSerfKnightProgression().assignDuty(type,null);p.getSerfKnightProgression().completeDuty(type);
        }
        assertTrue(KOMEProgressionRankSummary.project(p,149,"rohan").activityObjective.startsWith("Improve"));
        assertTrue(KOMEProgressionRankSummary.project(p,150,"rohan").activityObjective.contains("report to your Master"));
        assertEquals(3,KOMEServicePromotionService.duties(p.getSerfKnightProgression()));
    }
}
