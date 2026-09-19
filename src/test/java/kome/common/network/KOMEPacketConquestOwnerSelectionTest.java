package kome.common.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import kome.common.data.KOMEConquestTile;
import kome.common.data.KOMEWorldData;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class KOMEPacketConquestOwnerSelectionTest {
    @Test public void tileCommandConstructionInsertsAuthoritativeSelectableOwners() {
        KOMEWorldData data = dataWithClaimedTile("T388", "gondor");
        KOMEPacketConquestCaptureGui packet = packet();

        KOMEPacketConquestOpenCapture.populateSelectablePopulationOwners(
            packet, data, "gondor", data.getConquestTile("t388"));

        assertEquals(Collections.singletonList("gondor"), packet.selectablePopulationOwners);
    }

    @Test public void selectablePopulationOwnersSurvivePacketRoundTrip() {
        KOMEPacketConquestCaptureGui sent = packet();
        sent.selectablePopulationOwners.add("gondor");
        ByteBuf bytes = Unpooled.buffer();

        sent.toBytes(bytes);
        KOMEPacketConquestCaptureGui received = new KOMEPacketConquestCaptureGui();
        received.fromBytes(bytes);

        assertEquals(Collections.singletonList("gondor"), received.selectablePopulationOwners);
    }

    private static KOMEPacketConquestCaptureGui packet() {
        KOMEPacketConquestCaptureGui packet = new KOMEPacketConquestCaptureGui();
        packet.tileId = "T388";
        packet.ownerFaction = "gondor";
        packet.pendingFromFaction = "";
        packet.pendingToFaction = "";
        packet.viewerFaction = "gondor";
        return packet;
    }

    private static KOMEWorldData dataWithClaimedTile(String tileId, String controller) {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEConquestTile tile = new KOMEConquestTile(tileId);
        tile.defaultRulingFaction = controller;
        tile.claim(controller, 0L);
        data.conquestTiles.put(tile.id, tile);
        return data;
    }
}
