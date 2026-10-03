package kome.common.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Arrays;
import java.util.Collections;
import kome.common.data.KOMEProgressionRankSummary;
import org.junit.Test;

import static org.junit.Assert.*;

public class KOMEPacketProgressionRankSummaryTest {
    @Test public void presentationSafeRankProjectionRoundTrips() {
        KOMEProgressionRankSummary ranks=new KOMEProgressionRankSummary("ROHAN","Eorling-at-Arms","Rider of Rohan","Requirements for Rider of Rohan",
            Arrays.asList(new KOMEProgressionRankSummary.Requirement("Duties",2,3,false,Arrays.asList(
                new KOMEProgressionRankSummary.Requirement("Provisioning",1,1,true),
                new KOMEProgressionRankSummary.Requirement("Profession",1,1,true),
                new KOMEProgressionRankSummary.Requirement("Courier",0,1,false)))),
            "Trial of Standing","Recovery","Recover the lost item.");
        KOMEPacketProgressionData sent=new KOMEPacketProgressionData("Player",Collections.emptyList(),Collections.emptyMap(),"summary","","","","",ranks);
        ByteBuf bytes=Unpooled.buffer();sent.toBytes(bytes);KOMEPacketProgressionData received=new KOMEPacketProgressionData();received.fromBytes(bytes);
        assertEquals("ROHAN",received.rankSummary.factionKey);
        assertEquals("Eorling-at-Arms",received.rankSummary.currentRank);assertEquals("Rider of Rohan",received.rankSummary.nextRank);
        assertEquals(1,received.rankSummary.requirements.size());
        KOMEProgressionRankSummary.Requirement row=received.rankSummary.requirements.get(0);
        assertEquals("Duties",row.label);assertEquals(2,row.current);assertEquals(3,row.required);assertFalse(row.complete);
        assertEquals(3,row.children.size());assertEquals("Provisioning",row.children.get(0).label);assertTrue(row.children.get(0).complete);
        assertEquals("Courier",row.children.get(2).label);assertFalse(row.children.get(2).complete);
        assertEquals("Recovery",received.rankSummary.activityTitle);assertEquals("Recover the lost item.",received.rankSummary.activityObjective);
    }
}
