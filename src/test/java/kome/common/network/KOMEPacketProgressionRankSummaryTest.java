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
        KOMEProgressionRankSummary ranks=new KOMEProgressionRankSummary("Serf","Knight","Requirements for Knight",
            Arrays.asList(new KOMEProgressionRankSummary.Requirement("Duties",2,3,false)),
            "Trial of Knighthood","Recovery","Recover the lost item.");
        KOMEPacketProgressionData sent=new KOMEPacketProgressionData("Player",Collections.emptyList(),Collections.emptyMap(),"summary","","","","",ranks);
        ByteBuf bytes=Unpooled.buffer();sent.toBytes(bytes);KOMEPacketProgressionData received=new KOMEPacketProgressionData();received.fromBytes(bytes);
        assertEquals("Serf",received.rankSummary.currentRank);assertEquals("Knight",received.rankSummary.nextRank);
        assertEquals(1,received.rankSummary.requirements.size());
        KOMEProgressionRankSummary.Requirement row=received.rankSummary.requirements.get(0);
        assertEquals("Duties",row.label);assertEquals(2,row.current);assertEquals(3,row.required);assertFalse(row.complete);
        assertEquals("Recovery",received.rankSummary.activityTitle);assertEquals("Recover the lost item.",received.rankSummary.activityObjective);
    }
}
