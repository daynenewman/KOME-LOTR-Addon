package kome.common.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class KOMEPacketCampaignHireTest {
    @Test public void packetRoundTripsOnlyStableSourceAndUnitIdentifiers() {
        KOMEPacketCampaignHire original = new KOMEPacketCampaignHire(42, 3, "North Guard");
        ByteBuf encoded = Unpooled.buffer();
        original.toBytes(encoded);
        KOMEPacketCampaignHire decoded = new KOMEPacketCampaignHire();
        decoded.fromBytes(encoded);
        assertEquals(42, decoded.traderEntityId);
        assertEquals(3, decoded.tradeIndex);
        assertEquals("North Guard", decoded.squadron);
        assertFalse(encoded.isReadable());
    }

    @Test public void packetAndHandlerNeverAcceptClientPricePopulationOrTile() throws Exception {
        String source = text("src/main/java/kome/common/network/KOMEPacketCampaignHire.java");
        assertTrue(source.contains("int traderEntityId"));
        assertTrue(source.contains("int tradeIndex"));
        assertTrue(source.contains("String squadron"));
        assertFalse(source.contains("coinCost;"));
        assertFalse(source.contains("populationCost;"));
        assertFalse(source.contains("tileId;"));
        assertTrue(source.contains("KOMENativeTraderCampaignRecruitment.recruit"));
        String registration = text("src/main/java/kome/common/network/KOMEPacketHandler.java");
        assertTrue(registration.contains("KOMEPacketCampaignHire.Handler"));
        assertTrue(registration.contains("KOMEPacketCampaignHire.class, 37, Side.SERVER"));
    }

    private static String text(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
