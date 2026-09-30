package kome.common.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import kome.common.data.KOMEProgressionTrackerSnapshot;
import org.junit.Test;

import static org.junit.Assert.*;

public class KOMEPacketProgressionTrackerTest {
    @Test
    public void snapshotRoundTripsAndClampsCompletion() {
        KOMEProgressionTrackerSnapshot source=
            new KOMEProgressionTrackerSnapshot(
                true,
                "provisioning",
                "Bring 28 Ceramic Mugs of Strong Ale to your Master.",
                "9 / 28",
                2F);

        ByteBuf buffer=Unpooled.buffer();

        new KOMEPacketProgressionTracker(source)
            .toBytes(buffer);

        KOMEPacketProgressionTracker decoded=
            new KOMEPacketProgressionTracker();

        decoded.fromBytes(buffer);

        assertTrue(decoded.snapshot.visible);
        assertEquals(
            "provisioning",
            decoded.snapshot.iconKey);
        assertEquals(
            source.objective,
            decoded.snapshot.objective);
        assertEquals(
            "9 / 28",
            decoded.snapshot.progress);
        assertEquals(
            1F,
            decoded.snapshot.completion,
            0F);
    }
}
