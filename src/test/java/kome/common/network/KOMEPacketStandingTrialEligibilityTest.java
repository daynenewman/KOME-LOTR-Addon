package kome.common.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

public class KOMEPacketStandingTrialEligibilityTest {
    @Test
    public void exactNpcEligibilityRoundTrips() {
        UUID npcId=UUID.fromString("12345678-1234-5678-9abc-def012345678");
        ByteBuf buffer=Unpooled.buffer();
        new KOMEPacketStandingTrialEligibility(417,npcId,true,true,true,0x4A7F31).toBytes(buffer);

        KOMEPacketStandingTrialEligibility decoded=
            new KOMEPacketStandingTrialEligibility();
        decoded.fromBytes(buffer);

        assertEquals(417,decoded.entityId);
        assertEquals(npcId,new UUID(decoded.entityUuidMost,decoded.entityUuidLeast));
        assertTrue(decoded.eligible);
        assertTrue(decoded.passiveOffer);
        assertTrue(decoded.offering);
        assertEquals(0x4A7F31,decoded.offerColor);
    }
}
