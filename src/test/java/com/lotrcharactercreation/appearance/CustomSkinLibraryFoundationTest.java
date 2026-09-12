package com.lotrcharactercreation.appearance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.lotrcharactercreation.faction.StartingFaction;
import com.lotrcharactercreation.race.PlayerRace;

public class CustomSkinLibraryFoundationTest {

    private static final String MAN_ID = "custom_man_male_gondor_legacy_hero";

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void deterministicPresetIdRemainsLegacyCompatible() {
        assertEquals(
            MAN_ID,
            ExternalAppearancePresetScanner.createDeterministicPresetId(
                PlayerRace.MAN,
                PlayerSex.MALE,
                "gondor",
                "legacy_hero"));
        assertEquals(
            "custom_uruk_hai_none_gundabad_uruk_iron_jaw",
            ExternalAppearancePresetScanner.createDeterministicPresetId(
                PlayerRace.URUK_HAI,
                PlayerSex.NONE,
                "gundabad_uruk",
                "iron_jaw"));
    }

    @Test
    public void validLegacyLayoutScansWithCompleteMetadataAndBytes() throws Exception {
        Path root = newRoot();
        Path skin = writePng(root, "man/male/gondor/legacy_hero.png", 64, 64, 0xFF102030);

        CustomSkinScanResult result = scan(root);

        assertTrue(result.isSuccessful());
        assertEquals(0, result.getRejectedEntryCount());
        assertEquals(1, result.getSnapshot().getEntries().size());
        CustomSkinEntry entry = result.getSnapshot().getEntries().get(0);
        assertEquals(MAN_ID, entry.getPresetId());
        assertEquals(PlayerRace.MAN, entry.getRace());
        assertEquals("man", entry.getSerializedRaceId());
        assertEquals(PlayerSex.MALE, entry.getSex());
        assertEquals("male", entry.getSerializedSexId());
        assertEquals("gondor", entry.getGroupToken());
        assertEquals("gondor", entry.getGroupId());
        assertEquals("legacy_hero", entry.getFilenameStem());
        assertEquals("legacy_hero.png", entry.getLogicalFilename());
        assertEquals("Legacy Hero", entry.getDisplayName());
        assertEquals("man/male/gondor/legacy_hero.png", entry.getRelativePath());
        assertEquals(64, entry.getWidth());
        assertEquals(64, entry.getHeight());
        assertEquals(Files.size(skin), entry.getByteSize());
        assertEquals(CustomSkinHashing.sha256Hex(Files.readAllBytes(skin)), entry.getSha256());
        assertTrue(Arrays.equals(Files.readAllBytes(skin), entry.copyPngBytes()));
        assertEquals(AppearanceSourceType.EXTERNAL, entry.getSourceType());
        assertFalse(entry.getRelativePath().contains(root.toAbsolutePath().toString()));
    }

    @Test
    public void validRaceSpecificPngDimensionsAreAccepted() throws Exception {
        Path root = newRoot();
        writePng(root, "elf/female/galadhrim/leaf.png", 64, 64, 0xFF00AA00);
        writePng(root, "dwarf/female/blue_mountains/braid.png", 64, 64, 0xFFAA5500);
        writePng(root, "hobbit/male/default/shire.png", 64, 64, 0xFF55AA00);
        writePng(root, "orc/none/common_orc/ash.png", 64, 32, 0xFF555555);
        writePng(root, "uruk_hai/none/gundabad_uruk/iron.png", 64, 32, 0xFF222222);

        CustomSkinScanResult result = scan(root);

        assertTrue(result.isSuccessful());
        assertEquals(5, result.getSnapshot().getEntries().size());
        assertNotNull(result.getSnapshot().findByPresetId("custom_elf_female_galadhrim_leaf"));
        assertNotNull(result.getSnapshot().findByPresetId("custom_dwarf_female_blue_mountains_braid"));
        assertNotNull(result.getSnapshot().findByPresetId("custom_hobbit_male_default_shire"));
        assertNotNull(result.getSnapshot().findByPresetId("custom_orc_none_common_orc_ash"));
        assertNotNull(result.getSnapshot().findByPresetId("custom_uruk_hai_none_gundabad_uruk_iron"));
    }

    @Test
    public void invalidRaceSexAndGroupDirectoriesAreRejected() throws Exception {
        Path root = newRoot();
        writePng(root, "unknown/male/gondor/a.png", 64, 64, 1);
        writePng(root, "man/none/gondor/b.png", 64, 64, 2);
        writePng(root, "hobbit/male/gondor/c.png", 64, 64, 3);

        CustomSkinScanResult result = scan(root);

        assertTrue(result.isSuccessful());
        assertTrue(result.getRejectedEntryCount() >= 3);
        assertTrue(result.getSnapshot().getEntries().isEmpty());
    }

    @Test
    public void wrongDimensionsTruncationAndCorruptionAreRejected() throws Exception {
        Path root = newRoot();
        Path wrong = writePng(root, "man/male/gondor/wrong.png", 64, 32, 1);
        Path truncated = writePng(root, "man/male/gondor/truncated.png", 64, 64, 2);
        Path corrupt = writePng(root, "man/male/gondor/corrupt.png", 64, 64, 3);
        Files.write(truncated, Arrays.copyOf(Files.readAllBytes(truncated), 24));
        byte[] corruptBytes = Files.readAllBytes(corrupt);
        corruptBytes[corruptBytes.length - 1] ^= 0x01;
        Files.write(corrupt, corruptBytes);

        CustomSkinScanResult result = scan(root);

        assertTrue(result.isSuccessful());
        assertEquals(3, result.getRejectedEntryCount());
        assertTrue(result.getSnapshot().getEntries().isEmpty());
        assertTrue(Files.size(wrong) < CustomSkinScanLimits.DEFAULT_MAX_PNG_BYTES);
    }

    @Test
    public void oversizedFileIsRejectedUsingThePreDecodeSizeLimit() throws Exception {
        Path root = newRoot();
        Path file = root.resolve("man/male/gondor/huge.png");
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[CustomSkinScanLimits.DEFAULT_MAX_PNG_BYTES + 1]);

        CustomSkinScanResult result = scan(root);

        assertTrue(result.isSuccessful());
        assertEquals(1, result.getRejectedEntryCount());
        assertTrue(result.getSnapshot().getEntries().isEmpty());
        assertTrue(containsDiagnostic(result, "oversized"));
        assertEquals(256 * 1024, CustomSkinScanLimits.DEFAULT_MAX_PNG_BYTES);
        assertEquals(512, CustomSkinScanLimits.DEFAULT_MAX_ENTRIES);
        assertEquals(32L * 1024L * 1024L, CustomSkinScanLimits.DEFAULT_MAX_TOTAL_BYTES);
    }

    @Test
    public void invalidFilenameAndNestedDirectoryAreRejected() throws Exception {
        Path root = newRoot();
        writePng(root, "man/male/gondor/Bad Name.png", 64, 64, 1);
        writePng(root, "man/male/gondor/nested/valid.png", 64, 64, 2);
        writePng(root, "man/male/gondor/upper.PNG", 64, 64, 3);

        CustomSkinScanResult result = scan(root);

        assertTrue(result.isSuccessful());
        assertEquals(3, result.getRejectedEntryCount());
        assertTrue(result.getSnapshot().getEntries().isEmpty());
    }

    @Test
    public void symbolicLinkSkinIsRejectedWhenSupported() throws Exception {
        Path root = newRoot();
        Path outside = temporaryFolder.newFile("outside.png").toPath();
        writePngAt(outside, 64, 64, 1);
        Path linked = root.resolve("man/male/gondor/linked.png");
        Files.createDirectories(linked.getParent());
        try {
            Files.createSymbolicLink(linked, outside);
        } catch (IOException | UnsupportedOperationException | SecurityException exception) {
            Assume.assumeNoException("Symbolic links are unavailable in this test environment", exception);
        }

        CustomSkinScanResult result = scan(root);

        assertTrue(result.isSuccessful());
        assertEquals(1, result.getRejectedEntryCount());
        assertTrue(result.getSnapshot().getEntries().isEmpty());
        assertTrue(containsDiagnostic(result, "symbolic link"));
    }

    @Test
    public void builtInCollisionAndDuplicateExternalIdAreRejectedSafely() throws Exception {
        Path root = newRoot();
        writePng(root, "man/male/gondor/legacy_hero.png", 64, 64, 1);
        CustomSkinScanResult collision = ExternalAppearancePresetScanner.scanSnapshot(
            root.toFile(),
            1L,
            CustomSkinScanLimits.DEFAULT,
            Collections.singleton(MAN_ID),
            null);
        assertTrue(collision.isSuccessful());
        assertTrue(collision.getSnapshot().getEntries().isEmpty());
        assertTrue(containsDiagnostic(collision, "collides with a built-in"));

        CustomSkinEntry entry = entry("custom_man_male_gondor_duplicate", new byte[] { 1 });
        try {
            new CustomSkinSnapshot(1L, Arrays.asList(entry, entry));
            fail("Duplicate deterministic IDs must not enter an immutable snapshot");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("duplicate"));
        }
    }

    @Test
    public void duplicateContentHashesAreAllowedUnderDistinctValidIds() throws Exception {
        Path root = newRoot();
        Path first = writePng(root, "man/male/gondor/first.png", 64, 64, 1);
        Path second = root.resolve("man/male/gondor/second.png");
        Files.copy(first, second);

        CustomSkinScanResult result = scan(root);

        assertTrue(result.isSuccessful());
        assertEquals(2, result.getSnapshot().getEntries().size());
        assertEquals(
            result.getSnapshot().getEntries().get(0).getSha256(),
            result.getSnapshot().getEntries().get(1).getSha256());
    }

    @Test
    public void entryCountLimitIsEnforcedInCanonicalScanOrder() throws Exception {
        Path root = newRoot();
        writePng(root, "man/male/gondor/c.png", 64, 64, 3);
        writePng(root, "man/male/gondor/a.png", 64, 64, 1);
        writePng(root, "man/male/gondor/b.png", 64, 64, 2);
        CustomSkinScanLimits limits = new CustomSkinScanLimits(1024 * 1024, 2, 1024L * 1024L);

        CustomSkinScanResult result = ExternalAppearancePresetScanner.scanSnapshot(
            root.toFile(), 1L, limits, Collections.<String>emptySet(), null);

        assertTrue(result.isSuccessful());
        assertEquals(2, result.getSnapshot().getEntries().size());
        assertEquals("custom_man_male_gondor_a", result.getSnapshot().getEntries().get(0).getPresetId());
        assertEquals("custom_man_male_gondor_b", result.getSnapshot().getEntries().get(1).getPresetId());
        assertTrue(containsDiagnostic(result, "entry limit"));
    }

    @Test
    public void totalByteLimitTakesPrecedenceAndIsEnforced() throws Exception {
        Path root = newRoot();
        Path first = writePng(root, "man/male/gondor/a.png", 64, 64, 1);
        Path second = writePng(root, "man/male/gondor/b.png", 64, 64, 2);
        long totalLimit = Files.size(first) + Files.size(second) - 1L;
        CustomSkinScanLimits limits = new CustomSkinScanLimits(1024 * 1024, 10, totalLimit);

        CustomSkinScanResult result = ExternalAppearancePresetScanner.scanSnapshot(
            root.toFile(), 1L, limits, Collections.<String>emptySet(), null);

        assertTrue(result.isSuccessful());
        assertEquals(1, result.getSnapshot().getEntries().size());
        assertTrue(result.getSnapshot().getTotalByteCount() <= totalLimit);
        assertTrue(containsDiagnostic(result, "total library byte limit"));
    }

    @Test
    public void sha256IsStableLowercaseAndContentSensitive() {
        String first = CustomSkinHashing.sha256Hex("abc".getBytes(StandardCharsets.US_ASCII));
        String same = CustomSkinHashing.sha256Hex("abc".getBytes(StandardCharsets.US_ASCII));
        String changed = CustomSkinHashing.sha256Hex("abd".getBytes(StandardCharsets.US_ASCII));

        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", first);
        assertEquals(first, same);
        assertNotEquals(first, changed);
        assertTrue(first.matches("[0-9a-f]{64}"));
    }

    @Test
    public void snapshotOrderingCollectionsAndDigestAreDeterministic() {
        CustomSkinEntry beta = entry("custom_man_male_gondor_beta", new byte[] { 2 });
        CustomSkinEntry alpha = entry("custom_man_male_gondor_alpha", new byte[] { 1 });
        CustomSkinSnapshot first = new CustomSkinSnapshot(4L, Arrays.asList(beta, alpha));
        CustomSkinSnapshot second = new CustomSkinSnapshot(99L, Arrays.asList(alpha, beta));

        assertEquals("custom_man_male_gondor_alpha", first.getEntries().get(0).getPresetId());
        assertEquals("custom_man_male_gondor_beta", first.getEntries().get(1).getPresetId());
        assertEquals(first.getLibraryDigest(), second.getLibraryDigest());
        assertEquals(2L, first.getTotalByteCount());
        assertReadOnly(first.getEntries());
        assertMapReadOnly(first.getEntriesByPresetId());

        byte[] leaked = alpha.copyPngBytes();
        leaked[0] = 99;
        assertEquals(1, alpha.copyPngBytes()[0]);
    }

    @Test
    public void snapshotDiffDetectsAddsChangesRemovalsAndUnchangedEntries() {
        CustomSkinEntry unchanged = entry("custom_man_male_gondor_unchanged", new byte[] { 1 });
        CustomSkinEntry removed = entry("custom_man_male_gondor_removed", new byte[] { 2 });
        CustomSkinEntry oldChanged = entry("custom_man_male_gondor_changed", new byte[] { 3 });
        CustomSkinEntry newChanged = entry("custom_man_male_gondor_changed", new byte[] { 4 });
        CustomSkinEntry added = entry("custom_man_male_gondor_added", new byte[] { 5 });
        CustomSkinSnapshot before = new CustomSkinSnapshot(1L, Arrays.asList(unchanged, removed, oldChanged));
        CustomSkinSnapshot after = new CustomSkinSnapshot(2L, Arrays.asList(unchanged, newChanged, added));

        CustomSkinSnapshotDiff diff = CustomSkinSnapshotDiff.between(before, after);

        assertEquals(Collections.singleton(added.getPresetId()), diff.getAddedPresetIds());
        assertEquals(Collections.singleton(oldChanged.getPresetId()), diff.getChangedPresetIds());
        assertEquals(Collections.singleton(removed.getPresetId()), diff.getRemovedPresetIds());
        assertEquals(Collections.singleton(unchanged.getPresetId()), diff.getUnchangedPresetIds());
    }

    @Test
    public void catastrophicReloadFailurePreservesPreviouslyPublishedState() throws Exception {
        Path root = newRoot();
        writePng(root, "man/male/gondor/legacy_hero.png", 64, 64, 1);
        ServerCustomSkinLibrary library = ServerCustomSkinLibrary.getInstance();
        assertTrue(library.reload(root.toFile(), null).isApplied());
        CustomSkinSnapshot published = library.getCurrentSnapshot();
        AppearancePresetCatalog publishedCatalog = library.getCurrentCatalog();

        File invalidRoot = temporaryFolder.newFile("not-a-directory");
        ServerCustomSkinLibrary.ReloadResult failure = library.reload(invalidRoot, null);

        assertFalse(failure.isApplied());
        assertSame(published, failure.getCurrentSnapshot());
        assertSame(published, library.getCurrentSnapshot());
        assertSame(publishedCatalog, library.getCurrentCatalog());
        assertNotNull(library.getCurrentCatalog().findById(MAN_ID));
    }

    @Test
    public void serverCatalogStillAllowsAValidExternalAppearanceSelection() throws Exception {
        Path root = newRoot();
        writePng(root, "man/male/gondor/legacy_hero.png", 64, 64, 1);
        CustomSkinSnapshot snapshot = scan(root).getSnapshot();
        AppearancePresetCatalog catalog = AppearancePresetCatalog.combine(
            AppearancePresetRegistry.getBuiltInCatalog(),
            snapshot.getAppearancePresets());

        assertTrue(AppearancePresetRegistry.isPresetValid(catalog, PlayerRace.MAN, PlayerSex.MALE, MAN_ID));
        assertTrue(AppearanceSelectionRules.isPresetAllowed(
            catalog, PlayerRace.MAN, PlayerSex.MALE, StartingFaction.GONDOR, MAN_ID));
        assertEquals(MAN_ID, catalog.findById(MAN_ID).getId());
    }

    @Test
    public void commonLibraryFoundationHasNoMinecraftClientDependencies() throws Exception {
        String[] files = {
            "AppearancePresetCatalog.java",
            "CustomSkinEntry.java",
            "CustomSkinHashing.java",
            "CustomSkinScanLimits.java",
            "CustomSkinScanResult.java",
            "CustomSkinSnapshot.java",
            "CustomSkinSnapshotDiff.java",
            "ExternalAppearancePresetScanner.java",
            "ServerCustomSkinLibrary.java"
        };
        for (String filename : files) {
            Path source = new File("src/main/java/com/lotrcharactercreation/appearance", filename).toPath();
            String contents = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
            assertFalse(filename, contents.contains("net.minecraft.client"));
            assertFalse(filename, contents.contains("DynamicTexture"));
            assertFalse(filename, contents.contains("TextureManager"));
        }
    }

    private Path newRoot() throws IOException {
        return temporaryFolder.newFolder().toPath();
    }

    private static Path writePng(Path root, String relativePath, int width, int height, int color)
        throws IOException {
        Path file = root.resolve(relativePath);
        Files.createDirectories(file.getParent());
        writePngAt(file, width, height, color);
        return file;
    }

    private static void writePngAt(Path file, int width, int height, int color) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, color);
        assertTrue(ImageIO.write(image, "png", file.toFile()));
    }

    private static CustomSkinScanResult scan(Path root) {
        return ExternalAppearancePresetScanner.scanSnapshot(root.toFile(), 1L, null);
    }

    private static boolean containsDiagnostic(CustomSkinScanResult result, String fragment) {
        for (String diagnostic : result.getDiagnostics()) {
            if (diagnostic.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    private static CustomSkinEntry entry(String presetId, byte[] bytes) {
        String stem = presetId.substring(presetId.lastIndexOf('_') + 1);
        return new CustomSkinEntry(
            presetId,
            PlayerRace.MAN,
            PlayerSex.MALE,
            "gondor",
            "gondor",
            stem,
            stem,
            "man/male/gondor/" + stem + ".png",
            CustomSkinHashing.sha256Hex(bytes),
            64,
            64,
            bytes);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void assertReadOnly(List<?> values) {
        try {
            ((List) values).add(values.get(0));
            fail("Snapshot list must be immutable");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void assertMapReadOnly(Map<?, ?> values) {
        try {
            ((Map) values).clear();
            fail("Snapshot map must be immutable");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }
}
