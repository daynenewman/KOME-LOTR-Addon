package com.lotrcharactercreation.network;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.lotrcharactercreation.appearance.CustomSkinEntry;
import com.lotrcharactercreation.appearance.CustomSkinHashing;
import com.lotrcharactercreation.appearance.CustomSkinManifestEntry;
import com.lotrcharactercreation.appearance.CustomSkinScanLimits;
import com.lotrcharactercreation.appearance.CustomSkinScanResult;
import com.lotrcharactercreation.appearance.CustomSkinSnapshot;
import com.lotrcharactercreation.appearance.ExternalAppearancePresetScanner;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class CustomSkinManifestProtocolTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void serverSnapshotMetadataProducesTheSameCanonicalDigest() throws Exception {
        CustomSkinSnapshot snapshot = scannedSnapshot();
        List<CustomSkinManifestEntry> manifestEntries = new ArrayList<CustomSkinManifestEntry>();
        for (CustomSkinEntry entry : snapshot.getEntries()) {
            manifestEntries.add(CustomSkinManifestEntry.fromServerEntry(entry));
        }

        assertEquals(snapshot.getLibraryDigest(), CustomSkinManifestEntry.calculateLibraryDigest(manifestEntries));
        assertEquals(
            snapshot.getEntries().get(0).getRelativePath(),
            manifestEntries.get(0).getLogicalRelativePath());
    }

    @Test
    public void validManifestMessagesRoundTripAndAssembleAtomically() {
        List<CustomSkinManifestEntry> entries = Arrays.asList(entry("alpha", 1), entry("beta", 2));
        String digest = CustomSkinManifestEntry.calculateLibraryDigest(entries);
        List<List<CustomSkinManifestEntry>> pages = CustomSkinManifestPages.paginate(entries);
        long totalBytes = totalBytes(entries);

        CustomSkinManifestBeginMessage begin = roundTrip(new CustomSkinManifestBeginMessage(
            CustomSkinSyncProtocol.SCHEMA_VERSION,
            41L,
            7L,
            digest,
            entries.size(),
            totalBytes,
            pages.size()));
        CustomSkinManifestPageMessage page = roundTrip(
            new CustomSkinManifestPageMessage(41L, 0, pages.size(), pages.get(0)));
        CustomSkinManifestEndMessage end = roundTrip(new CustomSkinManifestEndMessage(41L, 7L, digest));

        assertTrue(begin.isValid());
        assertTrue(page.isValid());
        assertTrue(end.isValid());
        CustomSkinManifestAssembly assembly = new CustomSkinManifestAssembly(
            begin.getSchemaVersion(),
            begin.getEpoch(),
            begin.getRevision(),
            begin.getDigest(),
            begin.getEntryCount(),
            begin.getTotalBytes(),
            begin.getPageCount());
        assembly.acceptPage(page.getEpoch(), page.getPageIndex(), page.getPageCount(), page.getEntries());
        List<CustomSkinManifestEntry> completed = assembly.finish(
            end.getEpoch(),
            end.getRevision(),
            end.getDigest());
        assertEquals(2, completed.size());
        assertEquals("custom_man_male_gondor_alpha", completed.get(0).getPresetId());
    }

    @Test
    public void emptyExternalLibraryUsesZeroPagesAndStillCompletes() {
        List<CustomSkinManifestEntry> entries = Collections.emptyList();
        String digest = CustomSkinManifestEntry.calculateLibraryDigest(entries);
        assertTrue(CustomSkinManifestPages.paginate(entries).isEmpty());

        CustomSkinManifestAssembly assembly = new CustomSkinManifestAssembly(
            CustomSkinSyncProtocol.SCHEMA_VERSION,
            1L,
            0L,
            digest,
            0,
            0L,
            0);

        assertTrue(assembly.finish(1L, 0L, digest).isEmpty());
        assertTrue(roundTrip(new CustomSkinManifestBeginMessage(1, 1L, 0L, digest, 0, 0L, 0)).isValid());
    }

    @Test
    public void paginationIsDeterministicEntryAndByteBounded() {
        List<CustomSkinManifestEntry> entries = new ArrayList<CustomSkinManifestEntry>();
        for (int index = 0; index < 130; index++) {
            entries.add(entry(String.format("skin_%03d", Integer.valueOf(index)), index));
        }

        List<List<CustomSkinManifestEntry>> pages = CustomSkinManifestPages.paginate(entries);

        assertEquals(3, pages.size());
        assertEquals(64, pages.get(0).size());
        assertEquals(64, pages.get(1).size());
        assertEquals(2, pages.get(2).size());
        for (int pageIndex = 0; pageIndex < pages.size(); pageIndex++) {
            CustomSkinManifestPageMessage message = new CustomSkinManifestPageMessage(
                4L,
                pageIndex,
                pages.size(),
                pages.get(pageIndex));
            assertTrue(encodedBytes(message) <= CustomSkinSyncProtocol.MAX_ENCODED_PACKET_BYTES);
        }
    }

    @Test
    public void maximumLegalMetadataAndFileSizeAreAccepted() {
        char[] characters = new char[ExternalAppearancePresetScanner.MAX_FILENAME_STEM_LENGTH];
        Arrays.fill(characters, 'z');
        String stem = new String(characters);
        CustomSkinManifestEntry entry = new CustomSkinManifestEntry(
            "custom_man_male_dunedain_north_" + stem,
            "man",
            "male",
            "dunedain_north",
            "dunedain_north",
            stem,
            CustomSkinHashing.sha256Hex(new byte[] { 1 }),
            CustomSkinScanLimits.DEFAULT_MAX_PNG_BYTES,
            64,
            64);

        assertEquals(CustomSkinScanLimits.DEFAULT_MAX_PNG_BYTES, entry.getByteSize());
        assertTrue(CustomSkinSyncProtocol.encodedManifestEntryBytes(entry)
            < CustomSkinSyncProtocol.MAX_ENCODED_PACKET_BYTES);
    }

    @Test
    public void malformedManifestEntryMetadataIsRejected() {
        assertInvalidEntry("custom_man_male_gondor_test", "unknown", "male", "gondor", "gondor", "test",
            hash(), 100, 64, 64);
        assertInvalidEntry("custom_man_male_gondor_test", "man", "none", "gondor", "gondor", "test",
            hash(), 100, 64, 64);
        assertInvalidEntry("custom_man_male_gondor_test", "man", "male", "unknown", "unknown", "test",
            hash(), 100, 64, 64);
        assertInvalidEntry("custom_man_male_gondor_wrong", "man", "male", "gondor", "gondor", "test",
            hash(), 100, 64, 64);
        assertInvalidEntry("man_gondor_m_civilian_0", "man", "male", "gondor", "gondor", "test",
            hash(), 100, 64, 64);
        assertInvalidEntry("custom_man_male_gondor_bad_name", "man", "male", "gondor", "gondor", "Bad Name",
            hash(), 100, 64, 64);
        assertInvalidEntry("custom_man_male_gondor_test", "man", "male", "gondor", "gondor", "test",
            "ABC", 100, 64, 64);
        assertInvalidEntry("custom_man_male_gondor_test", "man", "male", "gondor", "gondor", "test",
            hash(), CustomSkinScanLimits.DEFAULT_MAX_PNG_BYTES + 1, 64, 64);
        assertInvalidEntry("custom_man_male_gondor_test", "man", "male", "gondor", "gondor", "test",
            hash(), 100, 64, 32);
    }

    @Test
    public void invalidManifestCountsTotalsAndIncompletePagesNeverFinish() {
        String digest = CustomSkinManifestEntry.calculateLibraryDigest(Collections.<CustomSkinManifestEntry>emptyList());
        assertInvalidAssembly(1, 1L, 1L, digest, -1, 0L, 0);
        assertInvalidAssembly(1, 1L, 1L, digest, 513, 1L, 1);
        assertInvalidAssembly(1, 1L, 1L, digest, 1, 0L, 1);
        assertInvalidAssembly(1, 1L, 1L, digest, 1, 33L * 1024L * 1024L, 1);
        assertInvalidAssembly(1, 1L, 1L, digest, 0, 0L, 1);

        CustomSkinManifestEntry entry = entry("incomplete", 1);
        CustomSkinManifestAssembly incomplete = new CustomSkinManifestAssembly(
            1,
            3L,
            4L,
            CustomSkinManifestEntry.calculateLibraryDigest(Collections.singletonList(entry)),
            1,
            entry.getByteSize(),
            1);
        try {
            incomplete.finish(3L, 4L, incomplete.getDigest());
            fail("An incomplete manifest must not finish");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("incomplete"));
        }
    }

    @Test
    public void duplicatePresetIdsAndWrongDigestAreRejected() {
        CustomSkinManifestEntry entry = entry("duplicate", 1);
        CustomSkinManifestAssembly duplicate = new CustomSkinManifestAssembly(
            1,
            9L,
            1L,
            CustomSkinManifestEntry.calculateLibraryDigest(Arrays.asList(entry, entry)),
            2,
            entry.getByteSize() * 2L,
            2);
        duplicate.acceptPage(9L, 0, 2, Collections.singletonList(entry));
        try {
            duplicate.acceptPage(9L, 1, 2, Collections.singletonList(entry));
            fail("Duplicate preset IDs must be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("duplicate"));
        }

        CustomSkinManifestAssembly wrongDigest = new CustomSkinManifestAssembly(
            1,
            10L,
            1L,
            hash(),
            1,
            entry.getByteSize(),
            1);
        wrongDigest.acceptPage(10L, 0, 1, Collections.singletonList(entry));
        try {
            wrongDigest.finish(10L, 1L, hash());
            fail("Wrong manifest digest must be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("digest"));
        }
    }

    @Test
    public void nonCanonicalManifestOrderingIsRejectedEvenWhenItsDigestIsValid() {
        CustomSkinManifestEntry alpha = entry("alpha_order", 1);
        CustomSkinManifestEntry beta = entry("beta_order", 2);
        List<CustomSkinManifestEntry> reversed = Arrays.asList(beta, alpha);
        String digest = CustomSkinManifestEntry.calculateLibraryDigest(reversed);
        CustomSkinManifestAssembly assembly = new CustomSkinManifestAssembly(
            1,
            11L,
            2L,
            digest,
            reversed.size(),
            totalBytes(reversed),
            1);
        assembly.acceptPage(11L, 0, 1, reversed);

        try {
            assembly.finish(11L, 2L, digest);
            fail("A manifest must arrive in canonical preset-ID order");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("canonical order"));
        }
    }

    @Test
    public void requestPagesRoundTripAtSixtyFourIdentitiesWithinWireLimit() {
        List<CustomSkinRequestIdentity> identities = new ArrayList<CustomSkinRequestIdentity>();
        for (int index = 0; index < 64; index++) {
            CustomSkinManifestEntry entry = entry(String.format("request_%03d", Integer.valueOf(index)), index);
            identities.add(new CustomSkinRequestIdentity(entry.getPresetId(), entry.getSha256()));
        }
        CustomSkinRequestPageMessage original = new CustomSkinRequestPageMessage(22L, 8L, identities);
        CustomSkinRequestPageMessage decoded = roundTrip(original);

        assertTrue(decoded.isValid());
        assertEquals(64, decoded.getIdentities().size());
        assertTrue(encodedBytes(original) <= CustomSkinSyncProtocol.MAX_ENCODED_PACKET_BYTES);
    }

    @Test
    public void transferMessagesRoundTripAndChunksRemainBelowPayloadTarget() {
        CustomSkinManifestEntry entry = entry("wire", 4);
        int chunkCount = CustomSkinSyncProtocol.chunkCountForSize(entry.getByteSize());
        CustomSkinTransferStartMessage start = roundTrip(new CustomSkinTransferStartMessage(
            31L,
            7L,
            2L,
            entry.getPresetId(),
            entry.getSha256(),
            entry.getByteSize(),
            chunkCount,
            entry.getWidth(),
            entry.getHeight()));
        byte[] data = new byte[CustomSkinSyncProtocol.MAX_CHUNK_BYTES];
        CustomSkinTransferChunkMessage chunk = roundTrip(new CustomSkinTransferChunkMessage(31L, 0, data));
        CustomSkinTransferEndMessage end = roundTrip(
            new CustomSkinTransferEndMessage(31L, 7L, entry.getPresetId(), entry.getSha256()));
        CustomSkinTransferResultMessage result = roundTrip(new CustomSkinTransferResultMessage(
            31L,
            7L,
            entry.getPresetId(),
            entry.getSha256(),
            CustomSkinTransferResultMessage.RESULT_SUCCESS));

        assertTrue(start.isValid());
        assertTrue(chunk.isValid());
        assertTrue(end.isValid());
        assertTrue(result.isSuccessful());
        assertArrayEquals(data, chunk.copyData());
        assertTrue(encodedBytes(new CustomSkinTransferChunkMessage(31L, 0, data))
            <= CustomSkinSyncProtocol.MAX_ENCODED_PACKET_BYTES);
    }

    @Test
    public void oversizedEncodedPacketIsRejectedBeforePayloadAllocation() {
        ByteBuf buffer = Unpooled.buffer(CustomSkinSyncProtocol.MAX_ENCODED_PACKET_BYTES + 1);
        try {
            buffer.writeZero(CustomSkinSyncProtocol.MAX_ENCODED_PACKET_BYTES + 1);
            CustomSkinTransferChunkMessage decoded = new CustomSkinTransferChunkMessage();
            decoded.fromBytes(buffer);
            assertFalse(decoded.isValid());
            assertEquals(0, decoded.copyData().length);
        } finally {
            buffer.release();
        }
    }

    @Test
    public void serverRequestLookupRequiresExactManifestIdentity() throws Exception {
        CustomSkinSnapshot snapshot = scannedSnapshot();
        CustomSkinEntry entry = snapshot.getEntries().get(0);
        HashSet<String> manifestIds = new HashSet<String>(Collections.singleton(entry.getPresetId()));

        assertNotNull(ServerCustomSkinSyncService.findRequestedEntry(
            snapshot,
            manifestIds,
            new CustomSkinRequestIdentity(entry.getPresetId(), entry.getSha256())));
        assertNull(ServerCustomSkinSyncService.findRequestedEntry(
            snapshot,
            manifestIds,
            new CustomSkinRequestIdentity(entry.getPresetId(), CustomSkinHashing.sha256Hex(new byte[] { 9 }))));
        assertNull(ServerCustomSkinSyncService.findRequestedEntry(
            snapshot,
            Collections.<String>emptySet(),
            new CustomSkinRequestIdentity(entry.getPresetId(), entry.getSha256())));
    }

    @Test
    public void serverTransferWindowAllowsTwoActiveAndQueuesTheThird() {
        CustomSkinTransferWindow window = new CustomSkinTransferWindow();
        CustomSkinRequestIdentity first = request("first", 1);
        CustomSkinRequestIdentity second = request("second", 2);
        CustomSkinRequestIdentity third = request("third", 3);
        assertTrue(window.request(first));
        assertTrue(window.request(second));
        assertTrue(window.request(third));

        assertEquals(first, window.beginNext(1L));
        assertEquals(second, window.beginNext(2L));
        assertNull(window.beginNext(3L));
        assertEquals(2, window.getActiveCount());
        assertEquals(1, window.getQueuedCount());

        assertTrue(window.complete(1L, first));
        assertEquals(third, window.beginNext(3L));
    }

    private CustomSkinSnapshot scannedSnapshot() throws IOException {
        Path root = temporaryFolder.newFolder().toPath();
        Path file = root.resolve("man/male/gondor/server_only.png");
        Files.createDirectories(file.getParent());
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xFF123456);
        assertTrue(ImageIO.write(image, "png", file.toFile()));
        CustomSkinScanResult result = ExternalAppearancePresetScanner.scanSnapshot(root.toFile(), 5L, null);
        assertTrue(result.isSuccessful());
        return result.getSnapshot();
    }

    private static CustomSkinManifestEntry entry(String stem, int seed) {
        String hash = CustomSkinHashing.sha256Hex((stem + seed).getBytes(StandardCharsets.UTF_8));
        return new CustomSkinManifestEntry(
            "custom_man_male_gondor_" + stem,
            "man",
            "male",
            "gondor",
            "gondor",
            stem,
            hash,
            100 + Math.abs(seed % 100),
            64,
            64);
    }

    private static CustomSkinRequestIdentity request(String stem, int seed) {
        CustomSkinManifestEntry entry = entry(stem, seed);
        return new CustomSkinRequestIdentity(entry.getPresetId(), entry.getSha256());
    }

    private static String hash() {
        return CustomSkinHashing.sha256Hex(new byte[] { 1, 2, 3 });
    }

    private static long totalBytes(List<CustomSkinManifestEntry> entries) {
        long total = 0L;
        for (CustomSkinManifestEntry entry : entries) {
            total += entry.getByteSize();
        }
        return total;
    }

    private static int encodedBytes(cpw.mods.fml.common.network.simpleimpl.IMessage message) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            message.toBytes(buffer);
            return buffer.readableBytes();
        } finally {
            buffer.release();
        }
    }

    private static CustomSkinManifestBeginMessage roundTrip(CustomSkinManifestBeginMessage original) {
        ByteBuf buffer = encode(original);
        try {
            CustomSkinManifestBeginMessage decoded = new CustomSkinManifestBeginMessage();
            decoded.fromBytes(buffer);
            return decoded;
        } finally {
            buffer.release();
        }
    }

    private static CustomSkinManifestPageMessage roundTrip(CustomSkinManifestPageMessage original) {
        ByteBuf buffer = encode(original);
        try {
            CustomSkinManifestPageMessage decoded = new CustomSkinManifestPageMessage();
            decoded.fromBytes(buffer);
            return decoded;
        } finally {
            buffer.release();
        }
    }

    private static CustomSkinManifestEndMessage roundTrip(CustomSkinManifestEndMessage original) {
        ByteBuf buffer = encode(original);
        try {
            CustomSkinManifestEndMessage decoded = new CustomSkinManifestEndMessage();
            decoded.fromBytes(buffer);
            return decoded;
        } finally {
            buffer.release();
        }
    }

    private static CustomSkinRequestPageMessage roundTrip(CustomSkinRequestPageMessage original) {
        ByteBuf buffer = encode(original);
        try {
            CustomSkinRequestPageMessage decoded = new CustomSkinRequestPageMessage();
            decoded.fromBytes(buffer);
            return decoded;
        } finally {
            buffer.release();
        }
    }

    private static CustomSkinTransferStartMessage roundTrip(CustomSkinTransferStartMessage original) {
        ByteBuf buffer = encode(original);
        try {
            CustomSkinTransferStartMessage decoded = new CustomSkinTransferStartMessage();
            decoded.fromBytes(buffer);
            return decoded;
        } finally {
            buffer.release();
        }
    }

    private static CustomSkinTransferChunkMessage roundTrip(CustomSkinTransferChunkMessage original) {
        ByteBuf buffer = encode(original);
        try {
            CustomSkinTransferChunkMessage decoded = new CustomSkinTransferChunkMessage();
            decoded.fromBytes(buffer);
            return decoded;
        } finally {
            buffer.release();
        }
    }

    private static CustomSkinTransferEndMessage roundTrip(CustomSkinTransferEndMessage original) {
        ByteBuf buffer = encode(original);
        try {
            CustomSkinTransferEndMessage decoded = new CustomSkinTransferEndMessage();
            decoded.fromBytes(buffer);
            return decoded;
        } finally {
            buffer.release();
        }
    }

    private static CustomSkinTransferResultMessage roundTrip(CustomSkinTransferResultMessage original) {
        ByteBuf buffer = encode(original);
        try {
            CustomSkinTransferResultMessage decoded = new CustomSkinTransferResultMessage();
            decoded.fromBytes(buffer);
            return decoded;
        } finally {
            buffer.release();
        }
    }

    private static ByteBuf encode(cpw.mods.fml.common.network.simpleimpl.IMessage message) {
        ByteBuf buffer = Unpooled.buffer();
        message.toBytes(buffer);
        return buffer;
    }

    private static void assertInvalidEntry(String presetId, String race, String sex, String groupToken,
        String groupId, String stem, String hash, int size, int width, int height) {
        try {
            new CustomSkinManifestEntry(
                presetId,
                race,
                sex,
                groupToken,
                groupId,
                stem,
                hash,
                size,
                width,
                height);
            fail("Expected invalid custom skin manifest entry");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    private static void assertInvalidAssembly(int schema, long epoch, long revision, String digest, int entries,
        long total, int pages) {
        try {
            new CustomSkinManifestAssembly(schema, epoch, revision, digest, entries, total, pages);
            fail("Expected invalid custom skin manifest header");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("header"));
        }
    }
}
