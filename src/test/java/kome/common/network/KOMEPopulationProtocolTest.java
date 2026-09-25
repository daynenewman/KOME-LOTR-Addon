package kome.common.network;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import kome.common.KOMEAddon;
import kome.common.data.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMEPopulationProtocolTest {
    @Test public void g2PopulationRowsAndTacticalCaptureFieldsRoundTripWithoutLegacySlots() {
        assertEquals("1.0.8-integration-g2", KOMEPopulationWire.VERSION);
        KOMEPacketPopulationGui sent = new KOMEPacketPopulationGui();
        sent.population = projection(1025L); sent.playerName = "Player"; sent.viewerFaction = "gondor";
        KOMEPacketPopulationGui.PlayerInvestment player = new KOMEPacketPopulationGui.PlayerInvestment();
        player.playerName = "Contributor"; player.playerUuid = java.util.UUID.randomUUID().toString();
        player.activePopulationCenti = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);
        sent.playerBreakdowns.add(player);
        KOMEPacketPopulationGui.TileBreakdown tile = new KOMEPacketPopulationGui.TileBreakdown();
        tile.tileId = "T100"; tile.tileDisplayName = "Gondor"; tile.ownerFaction = "gondor";
        tile.population = projection(2450L); sent.tileBreakdowns.add(tile);
        ByteBuf bytes = Unpooled.buffer();
        try {
            sent.toBytes(bytes); KOMEPacketPopulationGui read = new KOMEPacketPopulationGui(); read.fromBytes(bytes);
            assertEquals(player.activePopulationCenti, read.playerBreakdowns.get(0).activePopulationCenti);
            assertEquals(player.playerUuid, read.playerBreakdowns.get(0).playerUuid);
            assertEquals("Contributor", read.playerBreakdowns.get(0).playerName);
            assertEquals("T100", read.tileBreakdowns.get(0).tileId);
            assertProjection(tile.population, read.tileBreakdowns.get(0).population);
            assertEquals("Player", read.playerName); assertEquals("gondor", read.viewerFaction);
            assertEquals(0, bytes.readableBytes());
        } finally { bytes.release(); }

        KOMEPacketConquestCaptureGui capture = new KOMEPacketConquestCaptureGui();
        capture.population = projection(2450L); capture.tileId = "T100"; capture.ownerFaction = "gondor";
        capture.offensivePop = 70; capture.defensivePop = 25; capture.mountedPop = 40; capture.groundPop = 30;
        capture.incomingPop = 12; capture.outgoingPop = 9; capture.incomingEtaMillis = 123456789L;
        capture.myOffensivePop = 60; capture.myDefensivePop = 5; capture.myMountedPop = 40; capture.myGroundPop = 20;
        capture.canInspectWaypoint = true; capture.canMoveTroops = true; capture.canSetRecruitmentTile = true;
        capture.activeRecruitmentTile = "T100";
        capture.recruitmentLegalityReason = "Developed tile rate meets the recruitment threshold.";
        capture.recruitmentEffectiveRateUnits = BigInteger.valueOf(5000000L);
        capture.recruitmentThresholdUnits = BigInteger.valueOf(5000000L);
        capture.lotrWaypointKey = "testWaypoint";
        capture.selectablePopulationOwners.add("gondor");
        KOMEPacketConquestCaptureGui.BuildView build = new KOMEPacketConquestCaptureGui.BuildView();
        build.id = "B1"; build.buildType = "NORMAL"; build.approvedCentiHours = 2450L;
        build.developedNativeCentiHours = 2000L; build.pendingNativeCentiHours = 450L;
        build.currentRateUnits = BigInteger.valueOf(2000000L); build.currentMultiplier = "1.0000";
        capture.builds.add(build);
        bytes = Unpooled.buffer();
        try {
            capture.toBytes(bytes); KOMEPacketConquestCaptureGui read = new KOMEPacketConquestCaptureGui(); read.fromBytes(bytes);
            for (String field : new String[] {"offensivePop", "defensivePop", "mountedPop", "groundPop",
                    "incomingPop", "outgoingPop", "incomingEtaMillis", "myOffensivePop", "myDefensivePop",
                    "myMountedPop", "myGroundPop", "canInspectWaypoint", "canMoveTroops", "canSetRecruitmentTile",
                    "activeRecruitmentTile", "recruitmentLegalityReason", "recruitmentEffectiveRateUnits",
                    "recruitmentThresholdUnits", "lotrWaypointKey"})
                assertEquals(field, field(capture, field), field(read, field));
            assertEquals(capture.selectablePopulationOwners, read.selectablePopulationOwners);
            assertEquals(2450L, read.builds.get(0).approvedCentiHours);
            assertEquals(2000L, read.builds.get(0).developedNativeCentiHours);
            assertEquals(450L, read.builds.get(0).pendingNativeCentiHours);
            assertEquals(BigInteger.valueOf(2000000L), read.builds.get(0).currentRateUnits);
            assertEquals("1.0000", read.builds.get(0).currentMultiplier);
            assertEquals("NORMAL", read.builds.get(0).buildType);
            assertProjection(capture.population, read.population); assertEquals(0, bytes.readableBytes());
        } finally { bytes.release(); }
    }

    private static Object field(Object object, String name) {
        try { return object.getClass().getField(name).get(object); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private static KOMEPopulationProjection projection(long centi) {
        return new KOMEPopulationProjection("gondor", centi, BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE),
                BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(1000000L)), true, Long.MAX_VALUE);
    }

    @Test public void populationAndCapturePacketsRoundTripExactBoundaries() {
        for (long value : new long[] {0L, 1L, 50L, 100L, 2450L, Long.MAX_VALUE}) {
            KOMEPacketPopulationGui sent = new KOMEPacketPopulationGui(); sent.population = projection(value);
            ByteBuf bytes = Unpooled.buffer();
            try {
                sent.toBytes(bytes); KOMEPacketPopulationGui read = new KOMEPacketPopulationGui(); read.fromBytes(bytes);
                assertProjection(sent.population, read.population); assertEquals(0, bytes.readableBytes());
            } finally { bytes.release(); }
            KOMEPacketConquestCaptureGui capture = new KOMEPacketConquestCaptureGui(); capture.population = projection(value);
            bytes = Unpooled.buffer();
            try {
                capture.toBytes(bytes); KOMEPacketConquestCaptureGui read = new KOMEPacketConquestCaptureGui(); read.fromBytes(bytes);
                assertProjection(capture.population, read.population); assertEquals(0, bytes.readableBytes());
            } finally { bytes.release(); }
        }
    }

    @Test public void companyAndUnitPacketsCarryPermanentInvestmentAndNativeBank() {
        KOMECompanyGuiEntry company = new KOMECompanyGuiEntry();
        company.investedPopulationCenti = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.TEN);
        company.populationProjection = projection(2450L);
        KOMEPacketCompanyListGui sent = new KOMEPacketCompanyListGui("T1", java.util.Arrays.asList(company), false);
        ByteBuf bytes = Unpooled.buffer();
        try {
            sent.toBytes(bytes); KOMEPacketCompanyListGui read = new KOMEPacketCompanyListGui(); read.fromBytes(bytes);
            assertEquals(company.investedPopulationCenti, read.companies.get(0).investedPopulationCenti);
            assertProjection(company.populationProjection, read.companies.get(0).populationProjection);
            assertEquals(0, bytes.readableBytes());
        } finally { bytes.release(); }
        KOMEUnitGuiEntry combat = new KOMEUnitGuiEntry(); combat.populationCost = 25; combat.populationSpentCenti = 4000L;
        KOMEUnitGuiEntry farmhand = new KOMEUnitGuiEntry(); farmhand.farmhand = true;
        KOMEPacketPopulationUnitsGui units = new KOMEPacketPopulationUnitsGui("Tester", "T1",
                java.util.Arrays.asList(combat, farmhand), projection(1L), 1);
        bytes = Unpooled.buffer();
        try {
            units.toBytes(bytes); KOMEPacketPopulationUnitsGui read = new KOMEPacketPopulationUnitsGui(); read.fromBytes(bytes);
            assertProjection(units.population, read.population);
            assertEquals(1, read.farmhandsUsed);
            assertEquals(4000L, ((KOMEUnitGuiEntry) read.units.get(0)).populationSpentCenti);
            assertEquals(0L, ((KOMEUnitGuiEntry) read.units.get(1)).populationSpentCenti);
            assertEquals(0, bytes.readableBytes());
        } finally { bytes.release(); }
    }

    @Test public void oldProtocolFailsClosedBeforePopulationDecode() {
        assertTrue(KOMEPopulationWire.accepts(KOMEPopulationWire.VERSION));
        assertFalse(KOMEPopulationWire.accepts("1.0.8-integration-g1"));
        assertFalse(KOMEPopulationWire.accepts("1.0.8")); assertFalse(KOMEPopulationWire.accepts(null));
        assertEquals(KOMEPopulationWire.VERSION, KOMEAddon.class.getAnnotation(Mod.class).version());
        ByteBuf bytes = Unpooled.buffer();
        try {
            KOMEPopulationWire.writeText(bytes, "1.0.8-integration-g1"); bytes.writeInt(25);
            try { new KOMEPacketPopulationGui().fromBytes(bytes); fail("old payload"); }
            catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("protocol mismatch")); }
        } finally { bytes.release(); }
    }

    @Test public void exactCodecRejectsNegativeMalformedAndOversizedValues() {
        for (String bad : new String[] {"-1", "+1", "01", "1.0", "", "1e3", " 1", repeat('9', 65)}) {
            try { KOMEPopulationWire.parseExact(bad); fail(bad); } catch (IllegalArgumentException expected) { }
        }
        for (int bad : new int[] {-1, KOMEPopulationWire.MAX_ROWS + 1}) {
            try { KOMEPopulationWire.count(bad); fail("count"); } catch (IllegalArgumentException expected) { }
        }
        ByteBuf bytes = Unpooled.buffer();
        try {
            try { KOMEPopulationWire.writeText(bytes, repeat('x', KOMEPopulationWire.MAX_TEXT_BYTES + 1)); fail("length"); }
            catch (IllegalArgumentException expected) { assertEquals(0, bytes.writerIndex()); }
            bytes.writeByte(1); bytes.writeByte(0xff);
            try { KOMEPopulationWire.readText(bytes); fail("UTF-8"); } catch (IllegalArgumentException expected) { }
        } finally { bytes.release(); }
    }

    @Test public void negativeCentiAndMissingExactNbtFieldsAreRejected() {
        ByteBuf bytes = Unpooled.buffer();
        try {
            KOMEPopulationWire.writeText(bytes, "gondor"); bytes.writeLong(-1L);
            try { KOMEPopulationWire.readProjection(bytes); fail("negative bank"); } catch (IllegalArgumentException expected) { }
        } finally { bytes.release(); }
        try { KOMEPopulationWire.projectionFromTag(new NBTTagCompound()); fail("missing fields"); }
        catch (IllegalArgumentException expected) { }
        NBTTagCompound tag = KOMEPopulationWire.projectionTag(projection(2450L));
        assertTrue(tag.hasKey("AvailablePopulationCenti", 4));
        assertProjection(projection(2450L), KOMEPopulationWire.projectionFromTag(tag));
    }

    @Test public void malformedLaterConquestRecordCannotPartiallyPublishOrClearClientState() {
        KOMEClientData client = KOMEClientData.INSTANCE;
        KOMEConquestTile sentinel = new KOMEConquestTile("TEST999"); sentinel.claim("rohan", 0L);
        client.conquestTiles.put(sentinel.id, sentinel);
        int revision = client.conquestRevision;
        try {
            KOMEPacketConquestData packet = new KOMEPacketConquestData(); packet.reset = true;
            NBTTagList tiles = new NBTTagList();
            KOMEConquestTile incoming = new KOMEConquestTile("TEST998"); incoming.claim("gondor", 0L);
            tiles.appendTag(incoming.projectToNBT()); packet.data.setTag("ConquestTiles", tiles);
            NBTTagList builds = new NBTTagList(); NBTTagCompound malformed = new NBTTagCompound();
            malformed.setString("BuildType", "UNKNOWN"); builds.appendTag(malformed); packet.data.setTag("BuildMarkers", builds);
            try { new KOMEPacketConquestData.Handler().onMessage(packet, null); fail("invalid later marker"); }
            catch (IllegalArgumentException expected) { }
            assertSame(sentinel, client.conquestTiles.get(sentinel.id));
            assertFalse(client.conquestTiles.containsKey(incoming.id)); assertEquals(revision, client.conquestRevision);
        } finally { client.conquestTiles.remove(sentinel.id); client.conquestTiles.remove("TEST998"); }
    }

    @Test public void conquestProjectionUsesCanonicalBankWithoutRepairingTileOrLegacyPools() {
        KOMEWorldData data = new KOMEWorldData("projection");
        KOMEConquestTile tile = new KOMEConquestTile("T1"); tile.currentRulingFaction = " Gondor "; tile.ownerFaction = " ROHAN ";
        data.conquestTiles.put(tile.id, tile); KOMEPopulationService.grantCenti(data, "gondor", 2450L); data.setDirty(false);
        KOMEPacketConquestData packet = new KOMEPacketConquestData(data);
        assertEquals(" Gondor ", tile.currentRulingFaction); assertEquals(" ROHAN ", tile.ownerFaction); assertFalse(data.isDirty());
        KOMETileTroopSummary summary = new KOMETileTroopSummary();
        summary.readFromNBT(packet.data.getTagList("TroopSummaries", 10).getCompoundTagAt(0));
        assertEquals(2450L, summary.population.availablePopulationCenti);
        assertEquals("gondor", summary.population.faction);
    }

    @Test public void invalidConquestListCannotPublishAndMalformedContributionCannotDecode() {
        kome.common.data.KOMEClientData.INSTANCE.conquestTiles.clear();
        KOMEConquestTile sentinel = new KOMEConquestTile("T100"); sentinel.claim("gondor", 0L);
        kome.common.data.KOMEClientData.INSTANCE.conquestTiles.put(sentinel.id, sentinel);
        try {
            KOMEPacketConquestData invalid = new KOMEPacketConquestData(); invalid.reset = true;
            invalid.data.setString("ConquestTiles", "not a list");
            try { new KOMEPacketConquestData.Handler().onMessage(invalid, null); fail("Wrong list type"); }
            catch (IllegalArgumentException expected) { assertSame(sentinel, kome.common.data.KOMEClientData.INSTANCE.conquestTiles.get(sentinel.id)); }
            ByteBuf bytes = Unpooled.buffer();
            try {
                KOMEPopulationWire.writeText(bytes, "H1"); KOMEPopulationWire.writeText(bytes, "Player");
                KOMEPopulationWire.writeText(bytes, "gondor"); bytes.writeLong(1L); KOMEPopulationWire.writeText(bytes, "UNKNOWN");
                try { new KOMEPacketConquestCaptureGui.ContributionView().read(bytes); fail("Unknown status"); }
                catch (IllegalArgumentException expected) { }
            } finally { bytes.release(); }
        } finally { kome.common.data.KOMEClientData.INSTANCE.conquestTiles.clear(); }
    }

    @Test public void livePopulationPresentationCannotReadLegacyBankOrSaturatedRateAdapters() throws Exception {
        String[] paths = {"src/main/java/kome/client/gui/KOMEGuiPopulation.java", "src/main/java/kome/client/gui/KOMEGuiPopulationUnits.java",
            "src/main/java/kome/common/data/KOMEServerRecordBuilder.java", "src/main/java/kome/common/data/KOMEPopulationProjection.java",
            "src/main/java/kome/common/network/KOMEPacketConquestOpenCapture.java", "src/main/java/kome/common/network/KOMEPacketConquestData.java"};
        for (String path : paths) {
            String code = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(path)), java.nio.charset.StandardCharsets.UTF_8);
            assertFalse(path, code.contains("getAvailablePopulation("));
            assertFalse(path, code.contains("getFixedUnitsPerDay("));
            assertFalse(path, code.contains("getDailyPopulationRate("));
        }
        String units = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(paths[1])), java.nio.charset.StandardCharsets.UTF_8);
        assertFalse(units.contains("Faction Tile Population")); assertFalse(units.contains("Farmhand capacity"));
        assertTrue(units.contains("Permanent investment")); assertTrue(units.contains("No refund"));
    }

    @Test public void registryKeepsAuditedIdsSidesAndQueuedServerHandlers() throws Exception {
        String registry = source("src/main/java/kome/common/network/KOMEPacketHandler.java");
        String[] names = {"PopulationGui", "ConquestCaptureGui", "ConquestData", "CompanyListGui", "BuildAction"};
        int[] ids = {0, 5, 13, 25, 36};
        for (int i = 0; i < ids.length; i++) {
            String suffix = "KOMEPacket" + names[i] + ".class, " + ids[i] + ", Side." + (i == 4 ? "SERVER" : "CLIENT");
            assertTrue(suffix, registry.contains(suffix));
        }
        assertTrue(registry.contains("new ServerThreadHandler<KOMEPacketBuildAction>"));
        assertTrue(source("src/main/java/kome/common/KOMEAddon.java").contains("@cpw.mods.fml.common.network.NetworkCheckHandler"));
    }

    @Test public void forgeNetworkCheckOwnerAcceptsOnlyCurrentVersionInBothDirections() {
        KOMEAddon addon = new KOMEAddon();
        for (Side side : new Side[] {Side.CLIENT, Side.SERVER}) {
            assertTrue(addon.acceptsRemoteKome(java.util.Collections.singletonMap("kome", KOMEPopulationWire.VERSION), side));
            for (String version : new String[] {"1.0.8", "unknown", "1.0.8-integration-f1", "1.0.8-integration-g1"})
                assertFalse(addon.acceptsRemoteKome(java.util.Collections.singletonMap("kome", version), side));
            assertFalse(addon.acceptsRemoteKome(java.util.Collections.<String, String>emptyMap(), side));
        }
    }

    @Test public void everyChangedPacketRejectsTrailingBytes() throws Exception {
        cpw.mods.fml.common.network.simpleimpl.IMessage[] packets = {
            new KOMEPacketPopulationGui(), new KOMEPacketPopulationUnitsGui(), new KOMEPacketConquestCaptureGui(),
            new KOMEPacketConquestOpenCapture(), new KOMEPacketConquestData(), new KOMEPacketCompanyListGui(), new KOMEPacketBuildAction()
        };
        for (cpw.mods.fml.common.network.simpleimpl.IMessage packet : packets) {
            ByteBuf bytes = Unpooled.buffer();
            try {
                packet.toBytes(bytes); bytes.writeByte(0);
                try { packet.getClass().newInstance().fromBytes(bytes); fail(packet.getClass().getSimpleName()); }
                catch (IllegalArgumentException expected) { assertTrue(expected.getMessage(), expected.getMessage().contains("Trailing bytes")); }
            } finally { bytes.release(); }
        }
    }

    @Test public void textBoundIsUtf8BytesAndRejectedSenderPublishesNoPrefixOrPacket() {
        for (String text : new String[] {repeat('x', 4096), repeat('\u00e9', 2048)}) {
            ByteBuf bytes = Unpooled.buffer();
            try { KOMEPopulationWire.writeText(bytes, text); assertEquals(text, KOMEPopulationWire.readText(bytes)); }
            finally { bytes.release(); }
            ByteBuf packetBytes = Unpooled.buffer();
            try {
                KOMEPacketPopulationGui packet = new KOMEPacketPopulationGui(); packet.playerName = text + "x";
                try { packet.toBytes(packetBytes); fail("Excess UTF-8 byte"); }
                catch (IllegalArgumentException expected) { assertEquals(0, packetBytes.writerIndex()); }
                try { KOMEPopulationWire.writeText(packetBytes, text + "x"); fail("No prefix on rejection"); }
                catch (IllegalArgumentException expected) { assertEquals(0, packetBytes.writerIndex()); }
            } finally { packetBytes.release(); }
        }
        NBTTagCompound nbt = new NBTTagCompound(); nbt.setString("Name", repeat('\u00e9', 2048));
        KOMEPopulationWire.prepareNbt(nbt);
        nbt.setString("Name", repeat('\u00e9', 2048) + "x");
        try { KOMEPopulationWire.prepareNbt(nbt); fail("NBT text byte limit"); } catch (IllegalArgumentException expected) { }
    }

    @Test public void exactCollectionBoundRoundTripsAndFirstExcessWritesNothing() {
        KOMEPacketCompanyListGui packet = new KOMEPacketCompanyListGui();
        for (int i = 0; i < KOMEPopulationWire.MAX_ROWS; i++) packet.companies.add(new KOMECompanyGuiEntry());
        ByteBuf bytes = Unpooled.buffer();
        try {
            packet.toBytes(bytes); KOMEPacketCompanyListGui decoded = new KOMEPacketCompanyListGui(); decoded.fromBytes(bytes);
            assertEquals(KOMEPopulationWire.MAX_ROWS, decoded.companies.size());
            bytes.clear(); packet.companies.add(new KOMECompanyGuiEntry());
            try { packet.toBytes(bytes); fail("Excess row"); } catch (IllegalArgumentException expected) { assertEquals(0, bytes.writerIndex()); }
        } finally { bytes.release(); }
    }

    @Test public void compressedNbtSignedShortBoundariesAreExact() throws Exception {
        for (int length : new int[] {32766, 32767, 32768}) {
            NBTTagCompound nbt = nbtWithCompressedLength(length);
            assertEquals(length, net.minecraft.nbt.CompressedStreamTools.compress(nbt).length);
            KOMEPacketConquestData packet = new KOMEPacketConquestData(); packet.data = nbt;
            ByteBuf bytes = Unpooled.buffer();
            try {
                if (length <= KOMEPopulationWire.MAX_COMPRESSED_NBT_BYTES) {
                    assertEquals(length, KOMEPopulationWire.prepareNbt(nbt).length);
                    packet.toBytes(bytes); KOMEPacketConquestData read = new KOMEPacketConquestData(); read.fromBytes(bytes);
                    assertArrayEquals(nbt.getByteArray("Payload"), read.data.getByteArray("Payload"));
                } else {
                    try { packet.toBytes(bytes); fail("signed-short overflow"); }
                    catch (IllegalArgumentException expected) { assertEquals(0, bytes.writerIndex()); }
                    KOMEPopulationWire.writeHeader(bytes); bytes.writeBoolean(true); bytes.writeBoolean(true);
                    bytes.writeShort(length); bytes.writeBytes(net.minecraft.nbt.CompressedStreamTools.compress(nbt));
                    try { new KOMEPacketConquestData().fromBytes(bytes); fail("wrapped negative length"); }
                    catch (IllegalArgumentException expected) { }
                }
            } finally { bytes.release(); }
        }
    }

    @Test public void compressedBombStillHonorsDecompressedLimitOnBothSides() throws Exception {
        NBTTagCompound nbt = new NBTTagCompound(); nbt.setByteArray("Payload", new byte[KOMEPopulationWire.MAX_DECOMPRESSED_NBT_BYTES]);
        byte[] compressed = net.minecraft.nbt.CompressedStreamTools.compress(nbt);
        assertTrue(compressed.length < KOMEPopulationWire.MAX_COMPRESSED_NBT_BYTES);
        try { KOMEPopulationWire.prepareNbt(nbt); fail("sender uncompressed limit"); } catch (IllegalArgumentException expected) { }
        ByteBuf bytes = Unpooled.buffer();
        try {
            KOMEPopulationWire.writePreparedNbt(bytes, compressed);
            try { KOMEPopulationWire.readNbt(bytes); fail("receiver uncompressed limit"); } catch (IllegalArgumentException expected) { }
        } finally { bytes.release(); }
    }

    @Test public void oversizedPacketIsNeverPublishedToDestination() {
        ByteBuf destination = Unpooled.buffer();
        try {
            destination.writeByte(42);
            try { KOMEPopulationWire.writePacket(destination, buf -> buf.writeZero(KOMEPopulationWire.MAX_PACKET_BYTES + 1)); fail("packet bound"); }
            catch (IndexOutOfBoundsException expected) { assertEquals(1, destination.writerIndex()); assertEquals(42, destination.readByte()); }
        } finally { destination.release(); }
    }

    private static NBTTagCompound nbtWithCompressedLength(int target) throws Exception {
        byte[] random = new byte[target]; new java.util.Random(521L).nextBytes(random);
        for (int length = target - 160; length <= target; length++) {
            NBTTagCompound tag = new NBTTagCompound(); tag.setByteArray("Payload", java.util.Arrays.copyOf(random, length));
            if (net.minecraft.nbt.CompressedStreamTools.compress(tag).length == target) return tag;
        }
        throw new AssertionError("No deterministic compressed boundary fixture for " + target);
    }

    private static void assertProjection(KOMEPopulationProjection expected, KOMEPopulationProjection actual) {
        assertEquals(expected.faction, actual.faction); assertEquals(expected.availablePopulationCenti, actual.availablePopulationCenti);
        assertEquals(expected.activePopulationCenti, actual.activePopulationCenti); assertEquals(expected.dailyRateUnits, actual.dailyRateUnits);
        assertEquals(expected.capEnabled, actual.capEnabled); assertEquals(expected.capCenti, actual.capCenti);
        assertEquals(expected.summary(), actual.summary());
    }
    private static String repeat(char c, int count) { char[] chars = new char[count]; java.util.Arrays.fill(chars, c); return new String(chars); }
    private static String source(String path) throws Exception { return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8); }
}
