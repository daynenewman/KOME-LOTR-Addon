package kome.common.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import kome.common.KOMEAccessFixture;
import kome.common.data.KOMEBuildContribution;
import kome.common.data.KOMEBuildType;
import kome.common.data.KOMEConquestTile;
import kome.common.data.KOMEPlayerBuild;
import kome.common.data.KOMEPopulationTestConfig;
import kome.common.data.KOMETileTroopSummary;
import kome.common.data.KOMEWorldData;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.entity.player.EntityPlayerMP;
import org.junit.Test;

import static org.junit.Assert.*;

public class KOMEPopulationRefreshWiringTest {
    @Test public void everyRateChangingEntryPointBroadcastsTheAuthoritativeSnapshot() throws Exception {
        String packet = source("src/main/java/kome/common/network/KOMEPacketBuildAction.java");
        assertTrue(packet.contains("data.syncConquestTiles();"));
        assertFalse(packet.contains("data.syncConquestTiles(player);"));

        String command = source("src/main/java/kome/common/command/KOMECommandBuild.java");
        assertTrue(count(command, "data.syncConquestTiles();") >= 4);

        String development = source(
            "src/main/java/kome/common/data/KOMEPopulationDevelopmentService.java");
        assertTrue(development.contains(
            "if (processed > 0L) data.syncConquestTiles();"));

        String capture = source(
            "src/main/java/kome/common/network/KOMEPacketConquestClaim.java");
        assertTrue(capture.contains("data.syncConquestTiles();"));

        String world = source("src/main/java/kome/common/data/KOMEWorldData.java");
        assertTrue(world.contains("server.getConfigurationManager().playerEntityList"));
        assertTrue(world.contains("KOMEPacketConquestData.sendChunked(this, (EntityPlayerMP) player)"));
    }

    @Test public void multiClientAuthoritativeRefreshKeepsIndependentCompleteGenerations() throws Exception {
        KOMEAccessFixture first = new KOMEAccessFixture();
        KOMEAccessFixture second = new KOMEAccessFixture();
        for (int i = 0; i < 70; i++) {
            KOMEConquestTile tile = new KOMEConquestTile("T" + (5000 + i));
            tile.claim("gondor", i); first.data.conquestTiles.put(tile.id, tile);
        }
        RecipientNetwork network = KOMEAccessFixture.allocate(RecipientNetwork.class);
        network.messages = new ArrayList<IMessage>(); network.recipients = new ArrayList<EntityPlayerMP>();
        SimpleNetworkWrapper previousNetwork = KOMEPacketHandler.network;
        try {
            KOMEPacketHandler.network = network;
            KOMEPacketConquestData.sendChunked(first.data, first.player);
            KOMEPacketConquestData.sendChunked(first.data, second.player);
            assertCompleteGeneration(network, first.player);
            assertCompleteGeneration(network, second.player);
        } finally {
            KOMEPacketHandler.network = previousNetwork;
        }
    }

    @Test public void freshConquestSnapshotReplacesRecipientAndRateAfterCapture() throws Exception {
        try (KOMEPopulationTestConfig ignored = new KOMEPopulationTestConfig()) {
            KOMEWorldData data = new KOMEWorldData("refresh");
            KOMEConquestTile tile = new KOMEConquestTile("T1");
            tile.claim("mordor", 0L);
            data.conquestTiles.put(tile.id, tile);
            KOMEPlayerBuild build = new KOMEPlayerBuild();
            build.id = "B-GONDOR";
            build.type = KOMEBuildType.NORMAL;
            build.tileId = tile.id;
            build.populationFaction = "gondor";
            build.active = true;
            KOMEBuildContribution contribution = new KOMEBuildContribution();
            contribution.id = "H1";
            contribution.status = KOMEBuildContribution.APPROVED;
            contribution.centiHours = 5000L;
            build.contributions.add(contribution);
            build.developedNativeCentiHours = 5000L;
            data.builds.put(build.id, build);

            KOMETileTroopSummary captured = summary(new KOMEPacketConquestData(data));
            assertEquals("mordor", captured.ownerFaction);
            assertEquals("mordor", captured.population.faction);
            assertEquals(2_500_000L,
                captured.population.dailyRateUnits.longValueExact());

            tile.claim("gondor", 1L);
            KOMETileTroopSummary recaptured = summary(new KOMEPacketConquestData(data));
            assertEquals("gondor", recaptured.ownerFaction);
            assertEquals("gondor", recaptured.population.faction);
            assertEquals(5_000_000L,
                recaptured.population.dailyRateUnits.longValueExact());
        }
    }

    private static void assertCompleteGeneration(RecipientNetwork network, EntityPlayerMP player) {
        List<KOMEPacketConquestData> packets = new ArrayList<KOMEPacketConquestData>();
        for (int i = 0; i < network.messages.size(); i++)
            if (network.recipients.get(i) == player)
                packets.add((KOMEPacketConquestData) network.messages.get(i));
        assertTrue(packets.size() > 1);
        int resets = 0, completes = 0;
        for (KOMEPacketConquestData packet : packets) {
            if (packet.reset) resets++;
            if (packet.complete) completes++;
        }
        assertEquals(1, resets); assertEquals(1, completes);
        assertTrue(packets.get(0).reset); assertTrue(packets.get(packets.size() - 1).complete);
    }

    private static final class RecipientNetwork extends SimpleNetworkWrapper {
        private List<IMessage> messages;
        private List<EntityPlayerMP> recipients;
        private RecipientNetwork() { super("unused"); }
        @Override public void sendTo(IMessage message, EntityPlayerMP recipient) {
            messages.add(message); recipients.add(recipient);
        }
    }

    private static KOMETileTroopSummary summary(KOMEPacketConquestData packet) {
        NBTTagList rows = packet.data.getTagList("TroopSummaries", 10);
        assertEquals(1, rows.tagCount());
        KOMETileTroopSummary result = new KOMETileTroopSummary();
        result.readFromNBT(rows.getCompoundTagAt(0));
        return result;
    }

    private static int count(String text, String needle) {
        int count = 0;
        for (int at = 0; (at = text.indexOf(needle, at)) >= 0; at += needle.length())
            count++;
        return count;
    }

    private static String source(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)),
            StandardCharsets.UTF_8);
    }
}
