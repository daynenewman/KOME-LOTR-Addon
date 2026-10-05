package kome.client.gui;

import java.util.Arrays;
import kome.common.data.KOMEProgressionRankSummary;
import org.junit.Test;

import static org.junit.Assert.*;

public class KOMEProgressionRankLayoutTest {
    @Test public void collapsedDutiesUseOneTopLevelRowAndExpandedDutiesShiftFollowingRows() {
        KOMEProgressionRankSummary.Requirement duties=duties();
        KOMEProgressionRankSummary.Requirement trial=new KOMEProgressionRankSummary.Requirement(
            "Trial of Standing",0,1,false);
        assertEquals(26,KOMEProgressionRankLayout.requirementHeight(duties,false));
        assertEquals(86,KOMEProgressionRankLayout.requirementHeight(duties,true));
        assertEquals(52,KOMEProgressionRankLayout.requirementsHeight(Arrays.asList(duties,trial),false));
        assertEquals(112,KOMEProgressionRankLayout.requirementsHeight(Arrays.asList(duties,trial),true));
    }

    @Test public void expandedDutiesExposeEachChildCompletionState() {
        KOMEProgressionRankSummary.Requirement duties=duties();
        assertTrue(duties.hasChildren());assertEquals(3,duties.children.size());
        assertTrue(duties.children.get(0).complete);
        assertFalse(duties.children.get(1).complete);
        assertTrue(duties.children.get(2).complete);
    }

    private static KOMEProgressionRankSummary.Requirement duties() {
        return new KOMEProgressionRankSummary.Requirement("Duties",2,3,false,Arrays.asList(
            new KOMEProgressionRankSummary.Requirement("Provisioning",1,1,true),
            new KOMEProgressionRankSummary.Requirement("Profession",0,1,false),
            new KOMEProgressionRankSummary.Requirement("Courier",1,1,true)));
    }
}
