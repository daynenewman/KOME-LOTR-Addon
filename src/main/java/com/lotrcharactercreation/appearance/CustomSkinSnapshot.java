package com.lotrcharactercreation.appearance;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CustomSkinSnapshot {

    private static final byte[] DIGEST_SCHEMA = "kome-custom-skin-library-v1".getBytes(StandardCharsets.US_ASCII);

    private final long revision;
    private final Map<String, CustomSkinEntry> entriesByPresetId;
    private final List<CustomSkinEntry> entries;
    private final List<AppearancePreset> appearancePresets;
    private final long totalByteCount;
    private final String libraryDigest;

    public static CustomSkinSnapshot empty(long revision) {
        return new CustomSkinSnapshot(revision, Collections.<CustomSkinEntry>emptyList());
    }

    CustomSkinSnapshot(long revision, Collection<CustomSkinEntry> entries) {
        if (revision < 0L || entries == null) {
            throw new IllegalArgumentException("invalid custom skin snapshot");
        }

        List<CustomSkinEntry> sortedEntries = new ArrayList<CustomSkinEntry>(entries);
        Collections.sort(sortedEntries, new Comparator<CustomSkinEntry>() {

            @Override
            public int compare(CustomSkinEntry first, CustomSkinEntry second) {
                return first.getPresetId().compareTo(second.getPresetId());
            }
        });

        Map<String, CustomSkinEntry> indexedEntries = new LinkedHashMap<String, CustomSkinEntry>();
        List<AppearancePreset> presets = new ArrayList<AppearancePreset>(sortedEntries.size());
        long bytes = 0L;
        for (CustomSkinEntry entry : sortedEntries) {
            if (entry == null) {
                throw new IllegalArgumentException("custom skin snapshot entry cannot be null");
            }
            if (indexedEntries.put(entry.getPresetId(), entry) != null) {
                throw new IllegalArgumentException("duplicate custom skin preset ID: " + entry.getPresetId());
            }
            presets.add(entry.getAppearancePreset());
            bytes = Math.addExact(bytes, entry.getByteSize());
        }

        this.revision = revision;
        entriesByPresetId = Collections.unmodifiableMap(indexedEntries);
        this.entries = Collections.unmodifiableList(sortedEntries);
        appearancePresets = Collections.unmodifiableList(presets);
        totalByteCount = bytes;
        libraryDigest = calculateLibraryDigest(sortedEntries);
    }

    public long getRevision() {
        return revision;
    }

    public Map<String, CustomSkinEntry> getEntriesByPresetId() {
        return entriesByPresetId;
    }

    public List<CustomSkinEntry> getEntries() {
        return entries;
    }

    public CustomSkinEntry findByPresetId(String presetId) {
        return presetId == null ? null : entriesByPresetId.get(presetId);
    }

    public long getTotalByteCount() {
        return totalByteCount;
    }

    public String getLibraryDigest() {
        return libraryDigest;
    }

    List<AppearancePreset> getAppearancePresets() {
        return appearancePresets;
    }

    private static String calculateLibraryDigest(List<CustomSkinEntry> entries) {
        MessageDigest digest = CustomSkinHashing.newSha256();
        updateBytes(digest, DIGEST_SCHEMA);
        updateInt(digest, entries.size());
        for (CustomSkinEntry entry : entries) {
            updateString(digest, entry.getPresetId());
            updateString(digest, entry.getSerializedRaceId());
            updateString(digest, entry.getSerializedSexId());
            updateString(digest, entry.getGroupToken());
            updateString(digest, entry.getGroupId());
            updateString(digest, entry.getFilenameStem());
            updateString(digest, entry.getDisplayName());
            updateString(digest, entry.getRelativePath());
            updateString(digest, entry.getSha256());
            updateInt(digest, entry.getByteSize());
            updateInt(digest, entry.getWidth());
            updateInt(digest, entry.getHeight());
            updateString(digest, entry.getSourceType().name());
        }
        return CustomSkinHashing.toLowercaseHex(digest.digest());
    }

    private static void updateString(MessageDigest digest, String value) {
        if (value == null) {
            updateInt(digest, -1);
            return;
        }
        updateBytes(digest, value.getBytes(StandardCharsets.UTF_8));
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
}
