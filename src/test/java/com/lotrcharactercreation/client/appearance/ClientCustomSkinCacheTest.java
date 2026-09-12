package com.lotrcharactercreation.client.appearance;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;

import javax.imageio.ImageIO;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.lotrcharactercreation.appearance.AppearancePresetRegistry;
import com.lotrcharactercreation.appearance.CustomSkinEntry;
import com.lotrcharactercreation.appearance.CustomSkinHashing;
import com.lotrcharactercreation.appearance.CustomSkinScanResult;
import com.lotrcharactercreation.appearance.ExternalAppearancePresetScanner;

public class ClientCustomSkinCacheTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void canonicalHashMapsToDeterministicContainedShardPath() throws Exception {
        ClientCustomSkinCache cache = newCache();
        String hash = CustomSkinHashing.sha256Hex(new byte[] { 1, 2, 3 });

        Path cached = cache.getCachedPath(hash);
        Path part = cache.getPartPath(hash);

        assertEquals(
            cache.getRoot().resolve("sha256").resolve(hash.substring(0, 2)).resolve(hash + ".png"),
            cached);
        assertEquals(cached.resolveSibling(hash + ".png.part"), part);
        assertTrue(cached.startsWith(cache.getRoot()));
        assertTrue(part.startsWith(cache.getRoot()));
    }

    @Test
    public void malformedAndTraversalLikeHashesAreRejected() throws Exception {
        ClientCustomSkinCache cache = newCache();
        String valid = CustomSkinHashing.sha256Hex(new byte[] { 4 });

        assertInvalidHash(cache, valid.substring(1));
        assertInvalidHash(cache, valid + "0");
        assertInvalidHash(cache, valid.toUpperCase(java.util.Locale.ROOT));
        assertInvalidHash(cache, "../" + valid.substring(3));
        assertInvalidHash(cache, valid.substring(0, 61) + "/ab");
        assertInvalidHash(cache, "C:" + valid.substring(2));
        assertInvalidHash(cache, null);
    }

    @Test
    public void builtInAppearancesCannotBeRepresentedAsCacheDefinitions() {
        try {
            new ClientExternalSkinDefinition(
                AppearancePresetRegistry.findById("man_gondor_m_civilian_0"),
                CustomSkinHashing.sha256Hex(new byte[] { 1 }),
                1,
                64,
                64);
            fail("Built-in appearances must never enter the custom skin disk cache");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("external preset"));
        }
    }

    @Test
    public void validCachedContentRequiresExactHashSizeAndDimensions() throws Exception {
        Fixture fixture = fixture("man/male/gondor/cache_test.png", 64, 64, 0xFF123456);
        ClientCustomSkinCache cache = newCache();

        assertNotNull(promote(cache, fixture.definition, fixture.bytes));
        assertNotNull(cache.find(fixture.definition));

        ClientExternalSkinDefinition wrongSize = new ClientExternalSkinDefinition(
            fixture.definition.getPreset(),
            fixture.definition.getSha256(),
            fixture.bytes.length + 1,
            64,
            64);
        assertNull(cache.find(wrongSize));

        String wrongHash = CustomSkinHashing.sha256Hex(new byte[] { 9, 8, 7 });
        ClientExternalSkinDefinition wrongHashDefinition = new ClientExternalSkinDefinition(
            fixture.definition.getPreset(),
            wrongHash,
            fixture.bytes.length,
            64,
            64);
        writeDirect(cache.getCachedPath(wrongHash), fixture.bytes);
        assertNull(cache.find(wrongHashDefinition));
    }

    @Test
    public void wrongDimensionsAndCorruptOrTruncatedPngsAreRejected() throws Exception {
        Fixture fixture = fixture("man/male/gondor/validation_base.png", 64, 64, 0xFF334455);
        ClientCustomSkinCache cache = newCache();

        byte[] wrongDimensions = pngBytes(64, 32, 0xFF010203);
        ClientExternalSkinDefinition wrongDimensionsDefinition = definitionForBytes(fixture, wrongDimensions);
        writeDirect(cache.getCachedPath(wrongDimensionsDefinition.getSha256()), wrongDimensions);
        assertNull(cache.find(wrongDimensionsDefinition));

        byte[] corrupt = "not a png".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        ClientExternalSkinDefinition corruptDefinition = definitionForBytes(fixture, corrupt);
        writeDirect(cache.getCachedPath(corruptDefinition.getSha256()), corrupt);
        assertNull(cache.find(corruptDefinition));

        byte[] truncated = Arrays.copyOf(fixture.bytes, 24);
        ClientExternalSkinDefinition truncatedDefinition = definitionForBytes(fixture, truncated);
        writeDirect(cache.getCachedPath(truncatedDefinition.getSha256()), truncated);
        assertNull(cache.find(truncatedDefinition));
    }

    @Test
    public void partFileIsIgnoredUntilValidatedPromotion() throws Exception {
        Fixture fixture = fixture("man/male/gondor/part_test.png", 64, 64, 0xFF556677);
        ClientCustomSkinCache cache = newCache();

        Path part = cache.writePart(fixture.definition, fixture.bytes);

        assertTrue(Files.isRegularFile(part));
        assertNull(cache.find(fixture.definition));
        ClientCustomSkinCache.CachedContent promoted = cache.promotePart(fixture.definition);
        assertNotNull(promoted);
        assertFalse(Files.exists(part, LinkOption.NOFOLLOW_LINKS));
        assertEquals(cache.getCachedPath(fixture.definition.getSha256()), promoted.getPath());
        assertArrayEquals(fixture.bytes, promoted.copyBytes());
    }

    @Test
    public void invalidPartDoesNotPromoteAndIsCleanedUp() throws Exception {
        Fixture fixture = fixture("man/male/gondor/invalid_part.png", 64, 64, 0xFF778899);
        byte[] corrupt = new byte[] { 1, 2, 3, 4 };
        ClientExternalSkinDefinition corruptDefinition = definitionForBytes(fixture, corrupt);
        ClientCustomSkinCache cache = newCache();

        Path part = cache.writePart(corruptDefinition, corrupt);

        assertNull(cache.promotePart(corruptDefinition));
        assertFalse(Files.exists(part, LinkOption.NOFOLLOW_LINKS));
        assertFalse(Files.exists(cache.getCachedPath(corruptDefinition.getSha256()), LinkOption.NOFOLLOW_LINKS));
    }

    @Test
    public void existingIdenticalFinalContentIsReused() throws Exception {
        Fixture fixture = fixture("man/male/gondor/reuse.png", 64, 64, 0xFFABCDEF);
        ClientCustomSkinCache cache = newCache();
        ClientCustomSkinCache.CachedContent first = promote(cache, fixture.definition, fixture.bytes);
        Path finalPath = first.getPath();

        cache.writePart(fixture.definition, fixture.bytes);
        ClientCustomSkinCache.CachedContent second = cache.promotePart(fixture.definition);

        assertNotNull(second);
        assertEquals(finalPath, second.getPath());
        assertArrayEquals(fixture.bytes, Files.readAllBytes(finalPath));
        assertFalse(Files.exists(cache.getPartPath(fixture.definition.getSha256()), LinkOption.NOFOLLOW_LINKS));
    }

    @Test
    public void promotionDoesNotModifyOtherHashEntries() throws Exception {
        Fixture first = fixture("man/male/gondor/first_cache.png", 64, 64, 0xFF001122);
        Fixture second = fixture("man/male/gondor/second_cache.png", 64, 64, 0xFF221100);
        ClientCustomSkinCache cache = newCache();
        promote(cache, second.definition, second.bytes);
        Path secondPath = cache.getCachedPath(second.definition.getSha256());
        byte[] secondBefore = Files.readAllBytes(secondPath);

        promote(cache, first.definition, first.bytes);

        assertArrayEquals(secondBefore, Files.readAllBytes(secondPath));
        assertNotNull(cache.find(first.definition));
        assertNotNull(cache.find(second.definition));
    }

    @Test
    public void legacyImportCopiesExactBytesAndLeavesSourceUntouched() throws Exception {
        Fixture fixture = fixture("man/male/gondor/legacy_import.png", 64, 64, 0xFF654321);
        ClientCustomSkinCache cache = newCache();
        byte[] sourceBefore = Files.readAllBytes(fixture.path);

        ClientCustomSkinCache.CachedContent imported = cache.importLegacyFile(
            fixture.definition,
            fixture.path.toFile());

        assertNotNull(imported);
        assertEquals(fixture.definition.getSha256(), CustomSkinHashing.sha256Hex(imported.copyBytes()));
        assertArrayEquals(sourceBefore, imported.copyBytes());
        assertArrayEquals(sourceBefore, Files.readAllBytes(fixture.path));
        assertTrue(Files.isRegularFile(fixture.path));
    }

    @Test
    public void invalidLegacyFileIsNotImportedOrDeleted() throws Exception {
        Fixture fixture = fixture("man/male/gondor/legacy_invalid_base.png", 64, 64, 0xFF112233);
        byte[] invalid = new byte[] { 4, 3, 2, 1 };
        Path source = temporaryFolder.newFile("invalid-legacy.png").toPath();
        Files.write(source, invalid);
        ClientExternalSkinDefinition invalidDefinition = definitionForBytes(fixture, invalid);
        ClientCustomSkinCache cache = newCache();

        assertNull(cache.importLegacyFile(invalidDefinition, source.toFile()));
        assertArrayEquals(invalid, Files.readAllBytes(source));
        assertFalse(Files.exists(cache.getCachedPath(invalidDefinition.getSha256()), LinkOption.NOFOLLOW_LINKS));
    }

    @Test
    public void symbolicLinkAtHashPathIsNeverAcceptedWhenSupported() throws Exception {
        Fixture fixture = fixture("man/male/gondor/symlink.png", 64, 64, 0xFF445566);
        ClientCustomSkinCache cache = newCache();
        Path target = cache.getCachedPath(fixture.definition.getSha256());
        Files.createDirectories(target.getParent());
        try {
            Files.createSymbolicLink(target, fixture.path);
        } catch (IOException | UnsupportedOperationException | SecurityException exception) {
            Assume.assumeNoException("Symbolic links are unavailable in this test environment", exception);
        }

        assertNull(cache.find(fixture.definition));
        assertTrue(Files.isRegularFile(fixture.path));
    }

    private ClientCustomSkinCache newCache() throws IOException {
        return new ClientCustomSkinCache(temporaryFolder.newFolder("cache").toPath().toFile());
    }

    private Fixture fixture(String relativePath, int width, int height, int color) throws Exception {
        Path legacyRoot = temporaryFolder.newFolder().toPath();
        Path file = legacyRoot.resolve(relativePath);
        Files.createDirectories(file.getParent());
        byte[] bytes = pngBytes(width, height, color);
        Files.write(file, bytes);
        CustomSkinScanResult scan = ExternalAppearancePresetScanner.scanSnapshot(legacyRoot.toFile(), 1L, null);
        assertTrue(scan.isSuccessful());
        assertEquals(1, scan.getSnapshot().getEntries().size());
        CustomSkinEntry entry = scan.getSnapshot().getEntries().get(0);
        return new Fixture(file, bytes, ClientExternalSkinDefinition.fromValidatedEntry(entry));
    }

    private static ClientExternalSkinDefinition definitionForBytes(Fixture fixture, byte[] bytes) {
        return new ClientExternalSkinDefinition(
            fixture.definition.getPreset(),
            CustomSkinHashing.sha256Hex(bytes),
            bytes.length,
            64,
            64);
    }

    private static ClientCustomSkinCache.CachedContent promote(ClientCustomSkinCache cache,
        ClientExternalSkinDefinition definition, byte[] bytes) throws IOException {
        cache.writePart(definition, bytes);
        ClientCustomSkinCache.CachedContent promoted = cache.promotePart(definition);
        assertNotNull(promoted);
        return promoted;
    }

    private static void writeDirect(Path path, byte[] bytes) throws IOException {
        Files.createDirectories(path.getParent());
        Files.write(path, bytes);
    }

    private static byte[] pngBytes(int width, int height, int color) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, color);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "png", output));
        return output.toByteArray();
    }

    private static void assertInvalidHash(ClientCustomSkinCache cache, String hash) {
        try {
            cache.getCachedPath(hash);
            fail("Expected invalid hash to be rejected: " + hash);
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("SHA-256"));
        }
    }

    private static final class Fixture {

        private final Path path;
        private final byte[] bytes;
        private final ClientExternalSkinDefinition definition;

        private Fixture(Path path, byte[] bytes, ClientExternalSkinDefinition definition) {
            this.path = path;
            this.bytes = bytes;
            this.definition = definition;
        }
    }
}
