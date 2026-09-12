package com.lotrcharactercreation.appearance;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

import javax.imageio.ImageIO;

import org.apache.logging.log4j.Logger;

import com.lotrcharactercreation.race.PlayerRace;

/** Discovers and fully validates external preset content without loading Minecraft client classes. */
public final class ExternalAppearancePresetScanner {

    public static final int MAX_FILENAME_STEM_LENGTH = 64;
    static final Pattern VALID_FILENAME_STEM = Pattern.compile("[a-z0-9_-]+");

    private static final byte[] PNG_SIGNATURE = {
        (byte) 0x89,
        0x50,
        0x4E,
        0x47,
        0x0D,
        0x0A,
        0x1A,
        0x0A
    };

    private ExternalAppearancePresetScanner() {}

    /** Legacy metadata view retained until the network-backed client catalog replaces local scanning. */
    public static List<AppearancePreset> scan(File root, Logger logger) {
        CustomSkinScanResult result = scanSnapshot(root, 0L, logger);
        return result.isSuccessful() ? result.getSnapshot().getAppearancePresets()
            : Collections.<AppearancePreset>emptyList();
    }

    public static CustomSkinScanResult scanSnapshot(File root, long revision, Logger logger) {
        return scanSnapshot(
            root,
            revision,
            CustomSkinScanLimits.DEFAULT,
            AppearancePresetRegistry.getBuiltInPresetIds(),
            logger);
    }

    static CustomSkinScanResult scanSnapshot(File root, long revision, CustomSkinScanLimits limits,
        Set<String> reservedPresetIds, Logger logger) {
        ScanContext context = new ScanContext(limits, reservedPresetIds, logger);
        try {
            if (root == null) {
                throw new IOException("custom skin root is not configured");
            }
            Path rootPath = root.toPath().toAbsolutePath().normalize();
            if (Files.isSymbolicLink(rootPath)) {
                throw new IOException("custom skin root cannot be a symbolic link");
            }
            if (!Files.isDirectory(rootPath, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("custom skin root is missing or is not a directory");
            }
            Path canonicalRoot = rootPath.toRealPath(LinkOption.NOFOLLOW_LINKS);
            scanRaceDirectories(canonicalRoot, context);
            return CustomSkinScanResult.success(
                new CustomSkinSnapshot(revision, context.acceptedEntries.values()),
                context.rejectedEntryCount,
                context.diagnostics);
        } catch (IOException | SecurityException | ScanFailureException exception) {
            context.diagnostic("Custom skin library scan failed: " + safeMessage(exception));
            return CustomSkinScanResult.failure(context.diagnostics);
        }
    }

    public static boolean hasExpectedDimensions(PlayerRace race, int width, int height) {
        if (race == PlayerRace.ORC || race == PlayerRace.URUK_HAI) {
            return width == 64 && height == 32;
        }
        return race == PlayerRace.MAN || race == PlayerRace.DWARF || race == PlayerRace.ELF || race == PlayerRace.HOBBIT
            ? width == 64 && height == 64
            : false;
    }

    /** Fully validates and decodes PNG bytes using exact trusted dimensions. */
    public static BufferedImage decodeFullyValidatedPng(byte[] bytes, int expectedWidth, int expectedHeight) {
        if (bytes == null || expectedWidth <= 0 || expectedHeight <= 0) {
            return null;
        }
        PngValidation validation = validatePng(bytes, expectedWidth, expectedHeight);
        return validation.valid ? validation.decodedImage : null;
    }

    public static String createDeterministicPresetId(PlayerRace race, PlayerSex sex, String groupToken,
        String filenameStem) {
        if (race == null || sex == null || groupToken == null || filenameStem == null) {
            throw new IllegalArgumentException("custom skin identity tokens cannot be null");
        }
        return "custom_" + race.getSerializedId()
            + "_"
            + sex.getSerializedId()
            + "_"
            + groupToken
            + "_"
            + filenameStem;
    }

    public static boolean isValidFilenameStem(String filenameStem) {
        return filenameStem != null
            && !filenameStem.isEmpty()
            && filenameStem.length() <= MAX_FILENAME_STEM_LENGTH
            && VALID_FILENAME_STEM.matcher(filenameStem).matches();
    }

    public static boolean isValidGroupToken(PlayerRace race, String groupToken) {
        return race != null && groupToken != null && parseGroup(race, groupToken).valid;
    }

    public static String getNormalizedGroupId(PlayerRace race, String groupToken) {
        GroupParseResult result = race == null || groupToken == null ? GroupParseResult.INVALID
            : parseGroup(race, groupToken);
        if (!result.valid) {
            throw new IllegalArgumentException("invalid custom skin group token");
        }
        return result.groupId;
    }

    public static String createDisplayName(String filenameStem) {
        if (!isValidFilenameStem(filenameStem)) {
            throw new IllegalArgumentException("invalid custom skin filename stem");
        }
        return displayName(filenameStem);
    }

    private static void scanRaceDirectories(Path root, ScanContext context) throws ScanFailureException {
        for (File entry : listChildren(root.toFile())) {
            Path path = validateContainedEntry(root, entry, context);
            if (path == null) {
                continue;
            }
            if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                context.reject("Skipping unexpected file at custom skin root: " + relativeTo(root, path));
                continue;
            }

            PlayerRace race = PlayerRace.findBySerializedId(entry.getName());
            if (race == null) {
                context.reject("Skipping unknown custom skin race directory: " + entry.getName());
                continue;
            }
            try {
                scanSexDirectories(root, path, race, context);
            } catch (ScanFailureException exception) {
                context.reject("Could not scan custom skin race directory " + relativeTo(root, path) + ": "
                    + safeMessage(exception));
            }
        }
    }

    private static void scanSexDirectories(Path root, Path raceDirectory, PlayerRace race, ScanContext context)
        throws ScanFailureException {
        for (File entry : listChildren(raceDirectory.toFile())) {
            Path path = validateContainedEntry(root, entry, context);
            if (path == null) {
                continue;
            }
            if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                context.reject("Skipping unexpected file outside a sex directory: " + relativeTo(root, path));
                continue;
            }

            PlayerSex sex = PlayerSex.findBySerializedId(entry.getName());
            if (!AppearancePresetRegistry.isSexValidForRace(race, sex)) {
                context.reject(
                    "Skipping invalid custom skin sex directory for " + race.getSerializedId() + ": "
                        + entry.getName());
                continue;
            }
            try {
                scanGroupDirectories(root, path, race, sex, context);
            } catch (ScanFailureException exception) {
                context.reject("Could not scan custom skin sex directory " + relativeTo(root, path) + ": "
                    + safeMessage(exception));
            }
        }
    }

    private static void scanGroupDirectories(Path root, Path sexDirectory, PlayerRace race, PlayerSex sex,
        ScanContext context) throws ScanFailureException {
        for (File entry : listChildren(sexDirectory.toFile())) {
            Path path = validateContainedEntry(root, entry, context);
            if (path == null) {
                continue;
            }
            if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                context.reject("Skipping unexpected file outside a group directory: " + relativeTo(root, path));
                continue;
            }

            GroupParseResult parsedGroup = parseGroup(race, entry.getName());
            if (!parsedGroup.valid) {
                context.reject(
                    "Skipping unknown custom skin group for " + race.getSerializedId() + ": " + entry.getName());
                continue;
            }
            try {
                scanPngFiles(root, path, race, sex, entry.getName(), parsedGroup.groupId, context);
            } catch (ScanFailureException exception) {
                context.reject("Could not scan custom skin group directory " + relativeTo(root, path) + ": "
                    + safeMessage(exception));
            }
        }
    }

    private static void scanPngFiles(Path root, Path groupDirectory, PlayerRace race, PlayerSex sex,
        String groupToken, String groupId, ScanContext context) throws ScanFailureException {
        for (File entry : listChildren(groupDirectory.toFile())) {
            Path path = validateContainedEntry(root, entry, context);
            if (path == null) {
                continue;
            }
            if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                context.reject("Skipping unexpected nested custom skin directory: " + relativeTo(root, path));
                continue;
            }
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                context.reject("Skipping non-regular custom skin file: " + relativeTo(root, path));
                continue;
            }

            String filename = entry.getName();
            if (!filename.endsWith(".png")) {
                if (filename.toLowerCase(Locale.ROOT).endsWith(".png")) {
                    context.reject("Custom skin filenames must use lowercase .png: " + relativeTo(root, path));
                }
                continue;
            }

            String stem = filename.substring(0, filename.length() - 4);
            if (!isValidFilenameStem(stem)) {
                context.reject("Skipping invalid custom skin filename: " + relativeTo(root, path));
                continue;
            }

            long announcedSize;
            try {
                announcedSize = Files.size(path);
            } catch (IOException | SecurityException exception) {
                context.reject("Could not read custom skin size " + relativeTo(root, path) + ": "
                    + safeMessage(exception));
                continue;
            }
            if (announcedSize > context.limits.getMaxPngBytes()) {
                context.reject("Skipping oversized custom skin " + relativeTo(root, path) + " ("
                    + announcedSize + " bytes; maximum " + context.limits.getMaxPngBytes() + ")");
                continue;
            }

            byte[] bytes;
            try {
                bytes = readBounded(path, context.limits.getMaxPngBytes());
            } catch (IOException | SecurityException exception) {
                context.reject("Could not read custom skin " + relativeTo(root, path) + ": "
                    + safeMessage(exception));
                continue;
            }
            if (bytes.length != announcedSize) {
                context.reject("Skipping custom skin changed while being read: " + relativeTo(root, path));
                continue;
            }

            PngValidation png = validatePng(bytes, race);
            if (!png.valid) {
                context.reject("Skipping invalid custom skin PNG " + relativeTo(root, path) + ": " + png.reason);
                continue;
            }

            String presetId = createDeterministicPresetId(race, sex, groupToken, stem);
            if (context.reservedPresetIds.contains(presetId)) {
                context.reject("Skipping custom skin whose preset ID collides with a built-in: " + presetId);
                continue;
            }
            if (context.conflictingPresetIds.contains(presetId)) {
                context.reject("Skipping additional conflicting custom skin preset ID: " + presetId);
                continue;
            }
            CustomSkinEntry previous = context.acceptedEntries.remove(presetId);
            if (previous != null) {
                context.totalBytes -= previous.getByteSize();
                context.conflictingPresetIds.add(presetId);
                context.rejectedEntryCount++;
                context.reject("Skipping all custom skins with conflicting preset ID: " + presetId);
                continue;
            }

            if (context.totalBytes + bytes.length > context.limits.getMaxTotalBytes()) {
                context.reject("Skipping custom skin because the total library byte limit was reached: "
                    + relativeTo(root, path));
                continue;
            }
            if (context.acceptedEntries.size() >= context.limits.getMaxEntries()) {
                context.reject("Skipping custom skin because the library entry limit was reached: "
                    + relativeTo(root, path));
                continue;
            }

            String relativePath = race.getSerializedId() + "/"
                + sex.getSerializedId()
                + "/"
                + groupToken
                + "/"
                + filename;
            CustomSkinEntry customSkin = new CustomSkinEntry(
                presetId,
                race,
                sex,
                groupToken,
                groupId,
                stem,
                displayName(stem),
                relativePath,
                CustomSkinHashing.sha256Hex(bytes),
                png.width,
                png.height,
                bytes);
            context.acceptedEntries.put(presetId, customSkin);
            context.totalBytes += bytes.length;
        }
    }

    private static GroupParseResult parseGroup(PlayerRace race, String groupToken) {
        if (race == PlayerRace.MAN) {
            ManAppearanceGroup group = ManAppearanceGroup.findBySerializedId(groupToken);
            return group == null ? GroupParseResult.INVALID : new GroupParseResult(group.getSerializedId());
        }
        if (race == PlayerRace.ELF) {
            ElfAppearanceGroup group = ElfAppearanceGroup.findBySerializedId(groupToken);
            return group == null ? GroupParseResult.INVALID : new GroupParseResult(group.getSerializedId());
        }
        if (race == PlayerRace.DWARF) {
            DwarfAppearanceGroup group = DwarfAppearanceGroup.fromCommandArgument(groupToken);
            return group == null || !group.getSerializedId().equals(groupToken)
                ? GroupParseResult.INVALID
                : new GroupParseResult(group.getSerializedId());
        }
        if (race == PlayerRace.HOBBIT) {
            return "default".equals(groupToken) ? new GroupParseResult(null) : GroupParseResult.INVALID;
        }
        if (race == PlayerRace.ORC) {
            OrcAppearanceGroup group = OrcAppearanceGroup.findBySerializedId(groupToken);
            return group == null ? GroupParseResult.INVALID : new GroupParseResult(group.getSerializedId());
        }
        if (race == PlayerRace.URUK_HAI) {
            UrukHaiAppearanceGroup group = UrukHaiAppearanceGroup.findBySerializedId(groupToken);
            return group == null ? GroupParseResult.INVALID : new GroupParseResult(group.getSerializedId());
        }
        return GroupParseResult.INVALID;
    }

    private static PngValidation validatePng(byte[] bytes, PlayerRace race) {
        int expectedWidth = 64;
        int expectedHeight = race == PlayerRace.ORC || race == PlayerRace.URUK_HAI ? 32 : 64;
        PngValidation validation = validatePng(bytes, expectedWidth, expectedHeight);
        if (!validation.valid) {
            return validation;
        }
        if (!hasExpectedDimensions(race, validation.width, validation.height)) {
            return PngValidation.invalid("dimensions " + validation.width + "x" + validation.height
                + " do not match " + expectedDimensions(race));
        }
        return validation;
    }

    private static PngValidation validatePng(byte[] bytes, int expectedWidth, int expectedHeight) {
        if (bytes.length < 33 || !startsWith(bytes, PNG_SIGNATURE)) {
            return PngValidation.invalid("missing PNG signature or IHDR");
        }

        int offset = PNG_SIGNATURE.length;
        int width = -1;
        int height = -1;
        boolean sawHeader = false;
        boolean sawImageData = false;
        boolean sawEnd = false;
        while (offset < bytes.length) {
            if (bytes.length - offset < 12) {
                return PngValidation.invalid("truncated PNG chunk header");
            }
            int chunkLength = readInt(bytes, offset);
            if (chunkLength < 0) {
                return PngValidation.invalid("invalid PNG chunk length");
            }
            long chunkEnd = (long) offset + 12L + chunkLength;
            if (chunkEnd > bytes.length) {
                return PngValidation.invalid("truncated PNG chunk data");
            }

            String chunkType = new String(bytes, offset + 4, 4, StandardCharsets.US_ASCII);
            if (!sawHeader) {
                if (!"IHDR".equals(chunkType) || chunkLength != 13) {
                    return PngValidation.invalid("IHDR must be the first PNG chunk");
                }
                width = readInt(bytes, offset + 8);
                height = readInt(bytes, offset + 12);
                if (width <= 0 || height <= 0) {
                    return PngValidation.invalid("invalid PNG dimensions");
                }
                sawHeader = true;
            } else if ("IHDR".equals(chunkType)) {
                return PngValidation.invalid("duplicate PNG IHDR chunk");
            }

            CRC32 crc = new CRC32();
            crc.update(bytes, offset + 4, 4 + chunkLength);
            long expectedCrc = readInt(bytes, offset + 8 + chunkLength) & 0xFFFFFFFFL;
            if (crc.getValue() != expectedCrc) {
                return PngValidation.invalid("PNG chunk CRC mismatch");
            }
            if ("IDAT".equals(chunkType)) {
                sawImageData = true;
            }
            offset = (int) chunkEnd;
            if ("IEND".equals(chunkType)) {
                if (chunkLength != 0 || offset != bytes.length) {
                    return PngValidation.invalid("invalid PNG IEND or trailing data");
                }
                sawEnd = true;
                break;
            }
        }
        if (!sawHeader || !sawImageData || !sawEnd) {
            return PngValidation.invalid("PNG is missing required chunks");
        }
        if (width != expectedWidth || height != expectedHeight) {
            return PngValidation.invalid("dimensions " + width + "x" + height + " do not match "
                + expectedWidth + "x" + expectedHeight);
        }

        BufferedImage decoded;
        try {
            decoded = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException | RuntimeException exception) {
            return PngValidation.invalid("full PNG decode failed: " + safeMessage(exception));
        }
        if (decoded == null) {
            return PngValidation.invalid("full PNG decode returned no image");
        }
        if (decoded.getWidth() != width || decoded.getHeight() != height
            || decoded.getWidth() != expectedWidth
            || decoded.getHeight() != expectedHeight) {
            return PngValidation.invalid("decoded PNG dimensions do not match validated IHDR");
        }
        return PngValidation.valid(width, height, decoded);
    }

    private static byte[] readBounded(Path path, int maximumBytes) throws IOException {
        try (InputStream input = new FileInputStream(path.toFile());
            ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maximumBytes, 8192))) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) >= 0) {
                if (count == 0) {
                    continue;
                }
                total += count;
                if (total > maximumBytes) {
                    throw new IOException("file exceeds " + maximumBytes + " bytes while being read");
                }
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    private static File[] listChildren(File directory) throws ScanFailureException {
        File[] children;
        try {
            children = directory.listFiles();
        } catch (SecurityException exception) {
            throw new ScanFailureException("could not read directory " + directory, exception);
        }
        if (children == null) {
            throw new ScanFailureException("could not read directory " + directory);
        }
        Arrays.sort(children, new Comparator<File>() {

            @Override
            public int compare(File first, File second) {
                return first.getName().compareTo(second.getName());
            }
        });
        return children;
    }

    private static Path validateContainedEntry(Path root, File entry, ScanContext context) {
        try {
            Path path = entry.toPath().toAbsolutePath().normalize();
            if (!path.startsWith(root)) {
                context.reject("Skipping custom skin path outside the configured root: " + entry);
                return null;
            }
            if (Files.isSymbolicLink(path)) {
                context.reject("Skipping symbolic link in custom skins: " + relativeTo(root, path));
                return null;
            }
            Path realPath = path.toRealPath(LinkOption.NOFOLLOW_LINKS);
            if (!realPath.startsWith(root)) {
                context.reject("Skipping custom skin path outside the configured root: " + entry);
                return null;
            }
            return realPath;
        } catch (IOException | SecurityException exception) {
            context.reject("Could not validate custom skin path " + entry + ": " + safeMessage(exception));
            return null;
        }
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if (bytes[index] != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    private static int readInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF) << 24 | (bytes[offset + 1] & 0xFF) << 16
            | (bytes[offset + 2] & 0xFF) << 8
            | bytes[offset + 3] & 0xFF;
    }

    private static String expectedDimensions(PlayerRace race) {
        return race == PlayerRace.ORC || race == PlayerRace.URUK_HAI ? "64x32" : "64x64";
    }

    private static String displayName(String stem) {
        StringBuilder displayName = new StringBuilder();
        for (String word : stem.split("[_-]+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (displayName.length() > 0) {
                displayName.append(' ');
            }
            displayName.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                displayName.append(word.substring(1));
            }
        }
        return displayName.toString();
    }

    private static String relativeTo(Path root, Path entry) {
        try {
            return root.relativize(entry).toString().replace(File.separatorChar, '/');
        } catch (IllegalArgumentException exception) {
            return entry.toString();
        }
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isEmpty() ? throwable.getClass().getSimpleName() : message;
    }

    private static final class ScanContext {

        private final CustomSkinScanLimits limits;
        private final Set<String> reservedPresetIds;
        private final Logger logger;
        private final Map<String, CustomSkinEntry> acceptedEntries = new LinkedHashMap<String, CustomSkinEntry>();
        private final Set<String> conflictingPresetIds = new HashSet<String>();
        private final List<String> diagnostics = new ArrayList<String>();
        private int rejectedEntryCount;
        private long totalBytes;

        private ScanContext(CustomSkinScanLimits limits, Set<String> reservedPresetIds, Logger logger) {
            if (limits == null || reservedPresetIds == null) {
                throw new IllegalArgumentException("custom skin scan limits and reserved IDs cannot be null");
            }
            this.limits = limits;
            this.reservedPresetIds = new HashSet<String>(reservedPresetIds);
            this.logger = logger;
        }

        private void reject(String message) {
            rejectedEntryCount++;
            diagnostic(message);
        }

        private void diagnostic(String message) {
            diagnostics.add(message);
            if (logger != null) {
                logger.warn(message);
            }
        }
    }

    private static final class GroupParseResult {

        private static final GroupParseResult INVALID = new GroupParseResult(false, null);

        private final boolean valid;
        private final String groupId;

        private GroupParseResult(String groupId) {
            this(true, groupId);
        }

        private GroupParseResult(boolean valid, String groupId) {
            this.valid = valid;
            this.groupId = groupId;
        }
    }

    private static final class PngValidation {

        private final boolean valid;
        private final int width;
        private final int height;
        private final BufferedImage decodedImage;
        private final String reason;

        private static PngValidation valid(int width, int height, BufferedImage decodedImage) {
            return new PngValidation(true, width, height, decodedImage, null);
        }

        private static PngValidation invalid(String reason) {
            return new PngValidation(false, 0, 0, null, reason);
        }

        private PngValidation(boolean valid, int width, int height, BufferedImage decodedImage, String reason) {
            this.valid = valid;
            this.width = width;
            this.height = height;
            this.decodedImage = decodedImage;
            this.reason = reason;
        }
    }

    private static final class ScanFailureException extends Exception {

        private ScanFailureException(String message) {
            super(message);
        }

        private ScanFailureException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
