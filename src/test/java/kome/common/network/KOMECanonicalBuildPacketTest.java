package kome.common.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/** Wire-format regression tests for the canonical Build UI boundary. */
public class KOMECanonicalBuildPacketTest {
    @Test public void buildActionRoundTripHasOneTypeAndHoursValue() {
        KOMEPacketBuildAction sent = new KOMEPacketBuildAction("create", "T1", "", "", "Build",
            "gondor", "DEFENSIVE", 7, 0, 1D, 2D, 3D);
        ByteBuf bytes = Unpooled.buffer();
        sent.toBytes(bytes);
        KOMEPacketBuildAction read = new KOMEPacketBuildAction();
        read.fromBytes(bytes);
        assertEquals("DEFENSIVE", read.buildType);
        assertEquals(7, read.halfHours);
        for (java.lang.reflect.Field field : KOMEPacketBuildAction.class.getFields()) {
            assertFalse("offensiveHalfHours".equals(field.getName()));
            assertFalse("defensiveHalfHours".equals(field.getName()));
        }
    }

    @Test public void captureBuildViewsRoundTripCanonicalHours() {
        KOMEPacketConquestCaptureGui.BuildView sent = new KOMEPacketConquestCaptureGui.BuildView();
        sent.id = "B1";
        sent.buildType = "NORMAL";
        sent.approvedHalfHours = 12;
        KOMEPacketConquestCaptureGui.ContributionView contribution = new KOMEPacketConquestCaptureGui.ContributionView();
        contribution.id = "H1";
        contribution.halfHours = 3;
        sent.contributions.add(contribution);
        ByteBuf bytes = Unpooled.buffer();
        sent.write(bytes);
        KOMEPacketConquestCaptureGui.BuildView read = new KOMEPacketConquestCaptureGui.BuildView();
        read.read(bytes);
        assertEquals("NORMAL", read.buildType);
        assertEquals(12, read.approvedHalfHours);
        assertEquals(3, read.contributions.get(0).halfHours);
    }
}
