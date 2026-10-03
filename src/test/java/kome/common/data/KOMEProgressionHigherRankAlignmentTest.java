package kome.common.data;

import lotr.common.fac.LOTRFaction;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMEProgressionHigherRankAlignmentTest {
    @Test public void globalAlignmentUsesHigherThresholdForEliteFactions() {
        assertTrue(KOMEProgressionFactionQuotas.isElite(LOTRFaction.GONDOR));
        assertEquals(500.0f, KOMEProgressionAutoCompleter.globalAlignmentThreshold(LOTRFaction.GONDOR), 0.0f);

        LOTRFaction nonElite = null;
        for (LOTRFaction faction : LOTRFaction.values()) {
            if (faction.isPlayableAlignmentFaction() && !KOMEProgressionFactionQuotas.isElite(faction)) {
                nonElite = faction;
                break;
            }
        }
        assertNotNull(nonElite);
        assertEquals(250.0f, KOMEProgressionAutoCompleter.globalAlignmentThreshold(nonElite), 0.0f);
    }
}
