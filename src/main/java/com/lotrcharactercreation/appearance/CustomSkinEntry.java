package com.lotrcharactercreation.appearance;

import java.util.Arrays;
import java.util.regex.Pattern;

import com.lotrcharactercreation.race.PlayerRace;

public final class CustomSkinEntry {

    private static final Pattern SHA_256_HEX = Pattern.compile("[0-9a-f]{64}");

    private final String presetId;
    private final PlayerRace race;
    private final PlayerSex sex;
    private final String groupToken;
    private final String groupId;
    private final String filenameStem;
    private final String logicalFilename;
    private final String displayName;
    private final String relativePath;
    private final String sha256;
    private final int byteSize;
    private final int width;
    private final int height;
    private final byte[] pngBytes;
    private final AppearancePreset appearancePreset;

    CustomSkinEntry(String presetId, PlayerRace race, PlayerSex sex, String groupToken, String groupId,
        String filenameStem, String displayName, String relativePath, String sha256, int width, int height,
        byte[] pngBytes) {
        if (presetId == null || race == null || sex == null || groupToken == null || filenameStem == null
            || displayName == null || relativePath == null || sha256 == null || pngBytes == null) {
            throw new IllegalArgumentException("custom skin entry fields cannot be null");
        }
        if (!SHA_256_HEX.matcher(sha256).matches()) {
            throw new IllegalArgumentException("custom skin SHA-256 must be lowercase 64-character hexadecimal");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("custom skin dimensions must be positive");
        }

        this.presetId = presetId;
        this.race = race;
        this.sex = sex;
        this.groupToken = groupToken;
        this.groupId = groupId;
        this.filenameStem = filenameStem;
        logicalFilename = filenameStem + ".png";
        this.displayName = displayName;
        this.relativePath = relativePath;
        this.sha256 = sha256;
        byteSize = pngBytes.length;
        this.width = width;
        this.height = height;
        this.pngBytes = Arrays.copyOf(pngBytes, pngBytes.length);
        appearancePreset = new AppearancePreset(
            presetId,
            race,
            sex,
            groupId,
            AppearanceSourceType.EXTERNAL,
            displayName,
            null,
            relativePath);
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

    public String getLogicalFilename() {
        return logicalFilename;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getRelativePath() {
        return relativePath;
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

    public AppearanceSourceType getSourceType() {
        return AppearanceSourceType.EXTERNAL;
    }

    public byte[] copyPngBytes() {
        return Arrays.copyOf(pngBytes, pngBytes.length);
    }

    AppearancePreset getAppearancePreset() {
        return appearancePreset;
    }

    boolean hasSameContentIdentity(CustomSkinEntry other) {
        return other != null && presetId.equals(other.presetId)
            && race == other.race
            && sex == other.sex
            && groupToken.equals(other.groupToken)
            && equalNullable(groupId, other.groupId)
            && filenameStem.equals(other.filenameStem)
            && displayName.equals(other.displayName)
            && relativePath.equals(other.relativePath)
            && sha256.equals(other.sha256)
            && byteSize == other.byteSize
            && width == other.width
            && height == other.height;
    }

    private static boolean equalNullable(Object first, Object second) {
        return first == null ? second == null : first.equals(second);
    }
}
