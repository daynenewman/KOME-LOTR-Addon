package com.lotrcharactercreation.appearance;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import com.lotrcharactercreation.race.PlayerRace;

/** Immutable, path-free metadata for one server-authoritative external skin. */
public final class CustomSkinManifestEntry {

    private static final byte[] DIGEST_SCHEMA = "kome-custom-skin-library-v1"
        .getBytes(StandardCharsets.US_ASCII);
    private static final Pattern POSSIBLE_EXTERNAL_ID = Pattern.compile("custom_[a-z0-9_-]{1,121}");

    private final String presetId;
    private final PlayerRace race;
    private final PlayerSex sex;
    private final String groupToken;
    private final String groupId;
    private final String filenameStem;
    private final String displayName;
    private final String logicalRelativePath;
    private final String sha256;
    private final int byteSize;
    private final int width;
    private final int height;
    private final AppearancePreset appearancePreset;

    public static CustomSkinManifestEntry fromServerEntry(CustomSkinEntry entry) {
        if (entry == null) {
            throw new IllegalArgumentException("custom skin server entry cannot be null");
        }
        return new CustomSkinManifestEntry(
            entry.getPresetId(),
            entry.getSerializedRaceId(),
            entry.getSerializedSexId(),
            entry.getGroupToken(),
            entry.getGroupId(),
            entry.getFilenameStem(),
            entry.getSha256(),
            entry.getByteSize(),
            entry.getWidth(),
            entry.getHeight());
    }

    public CustomSkinManifestEntry(String presetId, String serializedRaceId, String serializedSexId,
        String groupToken, String groupId, String filenameStem, String sha256, int byteSize, int width, int height) {
        PlayerRace parsedRace = PlayerRace.findBySerializedId(serializedRaceId);
        PlayerSex parsedSex = PlayerSex.findBySerializedId(serializedSexId);
        if (parsedRace == null || parsedSex == null
            || !AppearancePresetRegistry.isSexValidForRace(parsedRace, parsedSex)) {
            throw new IllegalArgumentException("custom skin manifest race/sex is invalid");
        }
        if (!ExternalAppearancePresetScanner.isValidGroupToken(parsedRace, groupToken)) {
            throw new IllegalArgumentException("custom skin manifest group is invalid");
        }
        String expectedGroupId = ExternalAppearancePresetScanner.getNormalizedGroupId(parsedRace, groupToken);
        if (!equalNullable(expectedGroupId, groupId)) {
            throw new IllegalArgumentException("custom skin manifest group ID is not normalized");
        }
        if (!ExternalAppearancePresetScanner.isValidFilenameStem(filenameStem)) {
            throw new IllegalArgumentException("custom skin manifest filename stem is invalid");
        }
        String expectedPresetId = ExternalAppearancePresetScanner.createDeterministicPresetId(
            parsedRace,
            parsedSex,
            groupToken,
            filenameStem);
        if (!expectedPresetId.equals(presetId) || AppearancePresetRegistry.findById(presetId) != null) {
            throw new IllegalArgumentException("custom skin manifest preset ID is invalid or collides with a built-in");
        }
        if (!CustomSkinHashing.isCanonicalSha256(sha256)) {
            throw new IllegalArgumentException("custom skin manifest SHA-256 is invalid");
        }
        if (byteSize <= 0 || byteSize > CustomSkinScanLimits.DEFAULT_MAX_PNG_BYTES) {
            throw new IllegalArgumentException("custom skin manifest byte size is invalid");
        }
        if (!ExternalAppearancePresetScanner.hasExpectedDimensions(parsedRace, width, height)) {
            throw new IllegalArgumentException("custom skin manifest dimensions are invalid");
        }

        this.presetId = presetId;
        race = parsedRace;
        sex = parsedSex;
        this.groupToken = groupToken;
        this.groupId = groupId;
        this.filenameStem = filenameStem;
        displayName = ExternalAppearancePresetScanner.createDisplayName(filenameStem);
        logicalRelativePath = parsedRace.getSerializedId() + "/"
            + parsedSex.getSerializedId()
            + "/"
            + groupToken
            + "/"
            + filenameStem
            + ".png";
        this.sha256 = sha256;
        this.byteSize = byteSize;
        this.width = width;
        this.height = height;
        appearancePreset = new AppearancePreset(
            presetId,
            race,
            sex,
            groupId,
            AppearanceSourceType.EXTERNAL,
            displayName,
            null,
            logicalRelativePath);
    }

    public String getPresetId() {
        return presetId;
    }

    public PlayerRace getRace() {
        return race;
    }

    public String getSerializedRaceId() {
        return race.getSerializedId();
    }

    public PlayerSex getSex() {
        return sex;
    }

    public String getSerializedSexId() {
        return sex.getSerializedId();
    }

    public String getGroupToken() {
        return groupToken;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getFilenameStem() {
        return filenameStem;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getLogicalRelativePath() {
        return logicalRelativePath;
    }

    public String getSha256() {
        return sha256;
    }

    public int getByteSize() {
        return byteSize;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public AppearancePreset getAppearancePreset() {
        return appearancePreset;
    }

    public static boolean isPossibleExternalPresetId(String presetId) {
        return presetId != null && POSSIBLE_EXTERNAL_ID.matcher(presetId).matches();
    }

    public static String calculateLibraryDigest(List<CustomSkinManifestEntry> entries) {
        if (entries == null) {
            throw new IllegalArgumentException("custom skin manifest entries cannot be null");
        }
        List<CustomSkinManifestEntry> ordered = new ArrayList<CustomSkinManifestEntry>(entries);
        Collections.sort(ordered, new Comparator<CustomSkinManifestEntry>() {

            @Override
            public int compare(CustomSkinManifestEntry first, CustomSkinManifestEntry second) {
                return first.presetId.compareTo(second.presetId);
            }
        });
        MessageDigest digest = CustomSkinHashing.newSha256();
        updateBytes(digest, DIGEST_SCHEMA);
        updateInt(digest, ordered.size());
        for (CustomSkinManifestEntry entry : ordered) {
            if (entry == null) {
                throw new IllegalArgumentException("custom skin manifest entry cannot be null");
            }
            updateString(digest, entry.presetId);
            updateString(digest, entry.getSerializedRaceId());
            updateString(digest, entry.getSerializedSexId());
            updateString(digest, entry.groupToken);
            updateString(digest, entry.groupId);
            updateString(digest, entry.filenameStem);
            updateString(digest, entry.displayName);
            updateString(digest, entry.logicalRelativePath);
            updateString(digest, entry.sha256);
            updateInt(digest, entry.byteSize);
            updateInt(digest, entry.width);
            updateInt(digest, entry.height);
            updateString(digest, AppearanceSourceType.EXTERNAL.name());
        }
        return CustomSkinHashing.toLowercaseHex(digest.digest());
    }

    private static void updateString(MessageDigest digest, String value) {
        if (value == null) {
            updateInt(digest, -1);
        } else {
            updateBytes(digest, value.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void updateBytes(MessageDigest digest, byte[] bytes) {
        updateInt(digest, bytes.length);
        digest.update(bytes);
    }

    private static void updateInt(MessageDigest digest, int value) {
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }

    private static boolean equalNullable(Object first, Object second) {
        return first == null ? second == null : first.equals(second);
    }
}
