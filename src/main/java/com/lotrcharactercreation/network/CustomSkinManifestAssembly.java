package com.lotrcharactercreation.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.lotrcharactercreation.appearance.CustomSkinHashing;
import com.lotrcharactercreation.appearance.CustomSkinManifestEntry;
import com.lotrcharactercreation.appearance.CustomSkinScanLimits;

/** Mutable, bounded assembly which yields data only after a complete manifest validates. */
public final class CustomSkinManifestAssembly {

    private final long epoch;
    private final long revision;
    private final String digest;
    private final int expectedEntryCount;
    private final long expectedTotalBytes;
    private final int expectedPageCount;
    private final Map<Integer, List<CustomSkinManifestEntry>> pages =
        new HashMap<Integer, List<CustomSkinManifestEntry>>();
    private final Set<String> presetIds = new HashSet<String>();
    private int receivedEntryCount;
    private long receivedTotalBytes;

    public CustomSkinManifestAssembly(int schemaVersion, long epoch, long revision, String digest,
        int expectedEntryCount, long expectedTotalBytes, int expectedPageCount) {
        if (schemaVersion != CustomSkinSyncProtocol.SCHEMA_VERSION || epoch <= 0L || revision < 0L
            || !CustomSkinHashing.isCanonicalSha256(digest)
            || expectedEntryCount < 0 || expectedEntryCount > CustomSkinScanLimits.DEFAULT_MAX_ENTRIES
            || expectedTotalBytes < 0L || expectedTotalBytes > CustomSkinScanLimits.DEFAULT_MAX_TOTAL_BYTES
            || expectedPageCount < 0 || expectedPageCount > CustomSkinScanLimits.DEFAULT_MAX_ENTRIES
            || (expectedEntryCount == 0 && (expectedTotalBytes != 0L || expectedPageCount != 0))
            || (expectedEntryCount > 0
                && (expectedTotalBytes <= 0L || expectedPageCount <= 0 || expectedPageCount > expectedEntryCount))) {
            throw new IllegalArgumentException("custom skin manifest header is invalid");
        }
        this.epoch = epoch;
        this.revision = revision;
        this.digest = digest;
        this.expectedEntryCount = expectedEntryCount;
        this.expectedTotalBytes = expectedTotalBytes;
        this.expectedPageCount = expectedPageCount;
    }

    public void acceptPage(long pageEpoch, int pageIndex, int pageCount,
        List<CustomSkinManifestEntry> entries) {
        if (pageEpoch != epoch || pageCount != expectedPageCount || pageIndex < 0 || pageIndex >= expectedPageCount
            || entries == null || entries.isEmpty()
            || entries.size() > CustomSkinSyncProtocol.MAX_MANIFEST_PAGE_ENTRIES
            || pages.containsKey(Integer.valueOf(pageIndex))) {
            throw new IllegalArgumentException("custom skin manifest page framing is invalid");
        }
        List<CustomSkinManifestEntry> copied = new ArrayList<CustomSkinManifestEntry>(entries.size());
        for (CustomSkinManifestEntry entry : entries) {
            if (entry == null || !presetIds.add(entry.getPresetId())) {
                throw new IllegalArgumentException("custom skin manifest contains a duplicate entry");
            }
            receivedEntryCount++;
            receivedTotalBytes += entry.getByteSize();
            if (receivedEntryCount > expectedEntryCount || receivedTotalBytes > expectedTotalBytes) {
                throw new IllegalArgumentException("custom skin manifest exceeds declared totals");
            }
            copied.add(entry);
        }
        pages.put(Integer.valueOf(pageIndex), Collections.unmodifiableList(copied));
    }

    public List<CustomSkinManifestEntry> finish(long endEpoch, long endRevision, String endDigest) {
        if (endEpoch != epoch || endRevision != revision || !digest.equals(endDigest)
            || pages.size() != expectedPageCount || receivedEntryCount != expectedEntryCount
            || receivedTotalBytes != expectedTotalBytes) {
            throw new IllegalArgumentException("custom skin manifest is incomplete or superseded");
        }
        List<CustomSkinManifestEntry> entries = new ArrayList<CustomSkinManifestEntry>(expectedEntryCount);
        for (int pageIndex = 0; pageIndex < expectedPageCount; pageIndex++) {
            List<CustomSkinManifestEntry> page = pages.get(Integer.valueOf(pageIndex));
            if (page == null) {
                throw new IllegalArgumentException("custom skin manifest is missing a page");
            }
            entries.addAll(page);
        }
        String previousPresetId = null;
        for (CustomSkinManifestEntry entry : entries) {
            if (previousPresetId != null && previousPresetId.compareTo(entry.getPresetId()) >= 0) {
                throw new IllegalArgumentException("custom skin manifest entries are not in canonical order");
            }
            previousPresetId = entry.getPresetId();
        }
        String calculatedDigest = CustomSkinManifestEntry.calculateLibraryDigest(entries);
        if (!digest.equals(calculatedDigest)) {
            throw new IllegalArgumentException("custom skin manifest digest does not match its entries");
        }
        return Collections.unmodifiableList(entries);
    }

    public long getEpoch() {
        return epoch;
    }

    public long getRevision() {
        return revision;
    }

    public String getDigest() {
        return digest;
    }
}
