package com.lotrcharactercreation.client.appearance;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.lotrcharactercreation.appearance.CustomSkinHashing;
import com.lotrcharactercreation.appearance.CustomSkinManifestEntry;
import com.lotrcharactercreation.network.CustomSkinManifestAssembly;
import com.lotrcharactercreation.network.CustomSkinRequestIdentity;
import com.lotrcharactercreation.network.CustomSkinSyncProtocol;

public class ClientCustomSkinTransferTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void validChunksAssembleInOrderToExactOriginalBytes() throws Exception {
        Fixture fixture = fixture("valid", 64, 64, 0xFF123456);
        ClientCustomSkinTransferAssembly assembly = assembly(fixture, 10L, 20L, 3L);
        sendAllChunks(assembly, fixture.bytes, 10L);

        assertArrayEquals(
            fixture.bytes,
            assembly.finish(10L, 20L, fixture.definition.getPresetId(), fixture.definition.getSha256()));
    }

    @Test
    public void multiChunkTransferUsesBoundedChunksAndReassemblesExactly() {
        byte[] bytes = new byte[CustomSkinSyncProtocol.MAX_CHUNK_BYTES * 2 + 17];
        for (int index = 0; index < bytes.length; index++) {
            bytes[index] = (byte) (index * 31);
        }
        CustomSkinManifestEntry entry = new CustomSkinManifestEntry(
            "custom_man_male_gondor_multi_chunk",
            "man",
            "male",
            "gondor",
            "gondor",
            "multi_chunk",
            CustomSkinHashing.sha256Hex(bytes),
            bytes.length,
            64,
            64);
        Fixture fixture = new Fixture(bytes, ClientExternalSkinDefinition.fromManifestEntry(entry));
        ClientCustomSkinTransferAssembly assembly = assembly(fixture, 101L, 201L, 7L);

        assertEquals(3, CustomSkinSyncProtocol.chunkCountForSize(bytes.length));
        sendAllChunks(assembly, bytes, 101L);
        assertArrayEquals(
            bytes,
            assembly.finish(101L, 201L, entry.getPresetId(), entry.getSha256()));
    }

    @Test
    public void duplicateAndOutOfOrderChunksAreExplicitlyRejected() throws Exception {
        Fixture fixture = fixture("ordering", 64, 64, 0xFF334455);
        ClientCustomSkinTransferAssembly assembly = assembly(fixture, 11L, 21L, 4L);
        byte[] first = chunk(fixture.bytes, 0);
        assembly.acceptChunk(11L, 0, first);

        assertChunkRejected(assembly, 11L, 0, first);

        ClientCustomSkinTransferAssembly outOfOrder = assembly(fixture, 12L, 21L, 4L);
        assertChunkRejected(outOfOrder, 12L, 1, chunk(fixture.bytes, 1));
    }

    @Test
    public void missingExtraWrongTransferAndWrongLengthAreRejected() throws Exception {
        Fixture fixture = fixture("framing", 64, 64, 0xFF556677);
        ClientCustomSkinTransferAssembly missing = assembly(fixture, 13L, 22L, 5L);
        try {
            missing.finish(13L, 22L, fixture.definition.getPresetId(), fixture.definition.getSha256());
            fail("Missing chunks must reject completion");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("incomplete"));
        }

        ClientCustomSkinTransferAssembly wrongTransfer = assembly(fixture, 14L, 22L, 5L);
        assertChunkRejected(wrongTransfer, 99L, 0, chunk(fixture.bytes, 0));

        ClientCustomSkinTransferAssembly wrongLength = assembly(fixture, 15L, 22L, 5L);
        assertChunkRejected(wrongLength, 15L, 0, new byte[] { 1 });

        ClientCustomSkinTransferAssembly extra = assembly(fixture, 16L, 22L, 5L);
        sendAllChunks(extra, fixture.bytes, 16L);
        assertChunkRejected(extra, 16L, CustomSkinSyncProtocol.chunkCountForSize(fixture.bytes.length),
            new byte[] { 1 });
    }

    @Test
    public void wrongEndEpochPresetOrHashIsRejected() throws Exception {
        Fixture fixture = fixture("end_identity", 64, 64, 0xFF778899);
        ClientCustomSkinTransferAssembly epoch = assembly(fixture, 17L, 23L, 6L);
        sendAllChunks(epoch, fixture.bytes, 17L);
        assertEndRejected(epoch, 17L, 24L, fixture.definition.getPresetId(), fixture.definition.getSha256());

        ClientCustomSkinTransferAssembly preset = assembly(fixture, 18L, 23L, 6L);
        sendAllChunks(preset, fixture.bytes, 18L);
        assertEndRejected(preset, 18L, 23L, "custom_man_male_gondor_other", fixture.definition.getSha256());

        ClientCustomSkinTransferAssembly hash = assembly(fixture, 19L, 23L, 6L);
        sendAllChunks(hash, fixture.bytes, 19L);
        assertEndRejected(
            hash,
            19L,
            23L,
            fixture.definition.getPresetId(),
            CustomSkinHashing.sha256Hex(new byte[] { 9 }));
    }

    @Test
    public void cachePlannerSkipsValidContentAndRequestsMissingOrCorruptContent() throws Exception {
        final Fixture fixture = fixture("cache_compare", 64, 64, 0xFFABCDEF);
        final ClientCustomSkinCache validCache = new ClientCustomSkinCache(
            temporaryFolder.newFolder("valid-cache"));
        validCache.writePart(fixture.definition, fixture.bytes);
        assertTrue(validCache.promotePart(fixture.definition) != null);

        List<CustomSkinRequestIdentity> valid = ClientCustomSkinRequestPlanner.findMissing(
            Collections.singletonList(fixture.definition),
            new ClientCustomSkinRequestPlanner.ContentAvailability() {

                @Override
                public boolean isAvailable(ClientExternalSkinDefinition definition) {
                    return validCache.find(definition) != null;
                }
            });
        assertTrue(valid.isEmpty());

        final ClientCustomSkinCache missingCache = new ClientCustomSkinCache(
            temporaryFolder.newFolder("missing-cache"));
        assertEquals(1, plannedMissing(fixture, missingCache).size());

        final ClientCustomSkinCache corruptCache = new ClientCustomSkinCache(
            temporaryFolder.newFolder("corrupt-cache"));
        Path corruptPath = corruptCache.getCachedPath(fixture.definition.getSha256());
        Files.createDirectories(corruptPath.getParent());
        Files.write(corruptPath, new byte[] { 1, 2, 3 });
        assertEquals(1, plannedMissing(fixture, corruptCache).size());
    }

    @Test
    public void retryStateAllowsExactlyTwoRetriesAfterInitialFailure() throws Exception {
        ClientCustomSkinIdentity identity = fixture("retry", 64, 64, 0xFF010203).definition.getIdentity();
        ClientCustomSkinRetryState retries = new ClientCustomSkinRetryState();

        assertTrue(retries.shouldRetry(retries.recordFailure(identity)));
        assertTrue(retries.shouldRetry(retries.recordFailure(identity)));
        assertFalse(retries.shouldRetry(retries.recordFailure(identity)));
        assertEquals(3, retries.getFailureCount(identity));

        retries.succeeded(identity);
        assertEquals(0, retries.getFailureCount(identity));
    }

    @Test
    public void changedHashHasIndependentRetryStateAndDisconnectClearResetsAll() throws Exception {
        Fixture fixture = fixture("retry_identity", 64, 64, 0xFF102030);
        ClientCustomSkinIdentity oldIdentity = fixture.definition.getIdentity();
        ClientCustomSkinIdentity newIdentity = new ClientCustomSkinIdentity(
            oldIdentity.getPresetId(),
            CustomSkinHashing.sha256Hex(new byte[] { 8, 7, 6 }));
        ClientCustomSkinRetryState retries = new ClientCustomSkinRetryState();
        retries.recordFailure(oldIdentity);

        assertEquals(0, retries.getFailureCount(newIdentity));
        retries.clear();
        assertEquals(0, retries.getFailureCount(oldIdentity));
    }

    @Test
    public void supersededManifestRevisionsAreRejectedDeterministically() {
        String digest = CustomSkinManifestEntry.calculateLibraryDigest(
            Collections.<CustomSkinManifestEntry>emptyList());
        CustomSkinManifestAssembly pending = new CustomSkinManifestAssembly(1, 5L, 9L, digest, 0, 0L, 0);

        assertTrue(ClientCustomSkinSyncService.isSupersededRevision(8L, 7L, pending));
        assertTrue(ClientCustomSkinSyncService.isSupersededRevision(7L, 7L, null));
        assertFalse(ClientCustomSkinSyncService.isSupersededRevision(9L, 7L, pending));
        assertFalse(ClientCustomSkinSyncService.isSupersededRevision(10L, 9L, null));
    }

    private List<CustomSkinRequestIdentity> plannedMissing(final Fixture fixture,
        final ClientCustomSkinCache cache) {
        return ClientCustomSkinRequestPlanner.findMissing(
            Collections.singletonList(fixture.definition),
            new ClientCustomSkinRequestPlanner.ContentAvailability() {

                @Override
                public boolean isAvailable(ClientExternalSkinDefinition definition) {
                    return cache.find(definition) != null;
                }
            });
    }

    private Fixture fixture(String stem, int width, int height, int color) throws IOException {
        byte[] bytes = pngBytes(width, height, color);
        CustomSkinManifestEntry entry = new CustomSkinManifestEntry(
            "custom_man_male_gondor_" + stem,
            "man",
            "male",
            "gondor",
            "gondor",
            stem,
            CustomSkinHashing.sha256Hex(bytes),
            bytes.length,
            64,
            64);
        return new Fixture(bytes, ClientExternalSkinDefinition.fromManifestEntry(entry));
    }

    private static ClientCustomSkinTransferAssembly assembly(Fixture fixture, long transferId, long epoch,
        long revision) {
        return new ClientCustomSkinTransferAssembly(
            transferId,
            epoch,
            revision,
            fixture.definition,
            CustomSkinSyncProtocol.chunkCountForSize(fixture.bytes.length));
    }

    private static void sendAllChunks(ClientCustomSkinTransferAssembly assembly, byte[] bytes, long transferId) {
        int chunkCount = CustomSkinSyncProtocol.chunkCountForSize(bytes.length);
        for (int index = 0; index < chunkCount; index++) {
            assembly.acceptChunk(transferId, index, chunk(bytes, index));
        }
    }

    private static byte[] chunk(byte[] bytes, int chunkIndex) {
        int offset = chunkIndex * CustomSkinSyncProtocol.MAX_CHUNK_BYTES;
        if (offset >= bytes.length) {
            return new byte[] { 1 };
        }
        return Arrays.copyOfRange(
            bytes,
            offset,
            Math.min(bytes.length, offset + CustomSkinSyncProtocol.MAX_CHUNK_BYTES));
    }

    private static void assertChunkRejected(ClientCustomSkinTransferAssembly assembly, long transferId,
        int chunkIndex, byte[] chunk) {
        try {
            assembly.acceptChunk(transferId, chunkIndex, chunk);
            fail("Expected custom skin chunk rejection");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("custom skin transfer chunk"));
        }
    }

    private static void assertEndRejected(ClientCustomSkinTransferAssembly assembly, long transferId, long epoch,
        String presetId, String hash) {
        try {
            assembly.finish(transferId, epoch, presetId, hash);
            fail("Expected custom skin transfer end rejection");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("incomplete") || expected.getMessage().contains("identity"));
        }
    }

    private static byte[] pngBytes(int width, int height, int color) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, color);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "png", output));
        return output.toByteArray();
    }

    private static final class Fixture {

        private final byte[] bytes;
        private final ClientExternalSkinDefinition definition;

        private Fixture(byte[] bytes, ClientExternalSkinDefinition definition) {
            this.bytes = bytes;
            this.definition = definition;
        }
    }
}
