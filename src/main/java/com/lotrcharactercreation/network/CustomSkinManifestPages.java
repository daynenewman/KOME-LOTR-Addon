package com.lotrcharactercreation.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.lotrcharactercreation.appearance.CustomSkinManifestEntry;
import com.lotrcharactercreation.appearance.CustomSkinScanLimits;

/** Deterministic byte-bounded manifest pagination shared by server code and tests. */
public final class CustomSkinManifestPages {

    static final int PAGE_FIXED_BYTES = 8 + 4 + 4 + 4;

    private CustomSkinManifestPages() {}

    public static List<List<CustomSkinManifestEntry>> paginate(List<CustomSkinManifestEntry> entries) {
        if (entries == null || entries.size() > CustomSkinScanLimits.DEFAULT_MAX_ENTRIES) {
            throw new IllegalArgumentException("custom skin manifest entry count is invalid");
        }
        List<CustomSkinManifestEntry> ordered = new ArrayList<CustomSkinManifestEntry>(entries);
        Collections.sort(ordered, new Comparator<CustomSkinManifestEntry>() {

            @Override
            public int compare(CustomSkinManifestEntry first, CustomSkinManifestEntry second) {
                return first.getPresetId().compareTo(second.getPresetId());
            }
        });
        Set<String> ids = new HashSet<String>();
        List<List<CustomSkinManifestEntry>> pages = new ArrayList<List<CustomSkinManifestEntry>>();
        List<CustomSkinManifestEntry> current = new ArrayList<CustomSkinManifestEntry>();
        int currentBytes = PAGE_FIXED_BYTES;
        for (CustomSkinManifestEntry entry : ordered) {
            if (entry == null || !ids.add(entry.getPresetId())) {
                throw new IllegalArgumentException("custom skin manifest contains a null or duplicate entry");
            }
            int entryBytes = CustomSkinSyncProtocol.encodedManifestEntryBytes(entry);
            if (PAGE_FIXED_BYTES + entryBytes > CustomSkinSyncProtocol.MAX_ENCODED_PACKET_BYTES) {
                throw new IllegalArgumentException("custom skin manifest entry cannot fit in one packet");
            }
            if (!current.isEmpty()
                && (current.size() >= CustomSkinSyncProtocol.MAX_MANIFEST_PAGE_ENTRIES
                    || currentBytes + entryBytes > CustomSkinSyncProtocol.MAX_ENCODED_PACKET_BYTES)) {
                pages.add(Collections.unmodifiableList(new ArrayList<CustomSkinManifestEntry>(current)));
                current.clear();
                currentBytes = PAGE_FIXED_BYTES;
            }
            current.add(entry);
            currentBytes += entryBytes;
        }
        if (!current.isEmpty()) {
            pages.add(Collections.unmodifiableList(new ArrayList<CustomSkinManifestEntry>(current)));
        }
        return Collections.unmodifiableList(pages);
    }
}
