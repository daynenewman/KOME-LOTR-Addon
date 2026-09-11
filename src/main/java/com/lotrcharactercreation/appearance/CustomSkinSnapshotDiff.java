package com.lotrcharactercreation.appearance;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

public final class CustomSkinSnapshotDiff {

    private final Set<String> addedPresetIds;
    private final Set<String> changedPresetIds;
    private final Set<String> removedPresetIds;
    private final Set<String> unchangedPresetIds;

    private CustomSkinSnapshotDiff(Set<String> addedPresetIds, Set<String> changedPresetIds,
        Set<String> removedPresetIds, Set<String> unchangedPresetIds) {
        this.addedPresetIds = immutableCopy(addedPresetIds);
        this.changedPresetIds = immutableCopy(changedPresetIds);
        this.removedPresetIds = immutableCopy(removedPresetIds);
        this.unchangedPresetIds = immutableCopy(unchangedPresetIds);
    }

    public static CustomSkinSnapshotDiff between(CustomSkinSnapshot previous, CustomSkinSnapshot current) {
        if (previous == null || current == null) {
            throw new IllegalArgumentException("custom skin snapshots cannot be null");
        }

        Set<String> added = new TreeSet<String>();
        Set<String> changed = new TreeSet<String>();
        Set<String> removed = new TreeSet<String>();
        Set<String> unchanged = new TreeSet<String>();

        for (CustomSkinEntry currentEntry : current.getEntries()) {
            CustomSkinEntry previousEntry = previous.findByPresetId(currentEntry.getPresetId());
            if (previousEntry == null) {
                added.add(currentEntry.getPresetId());
            } else if (previousEntry.hasSameContentIdentity(currentEntry)) {
                unchanged.add(currentEntry.getPresetId());
            } else {
                changed.add(currentEntry.getPresetId());
            }
        }
        for (CustomSkinEntry previousEntry : previous.getEntries()) {
            if (current.findByPresetId(previousEntry.getPresetId()) == null) {
                removed.add(previousEntry.getPresetId());
            }
        }
        return new CustomSkinSnapshotDiff(added, changed, removed, unchanged);
    }

    public Set<String> getAddedPresetIds() {
        return addedPresetIds;
    }

    public Set<String> getChangedPresetIds() {
        return changedPresetIds;
    }

    public Set<String> getRemovedPresetIds() {
        return removedPresetIds;
    }

    public Set<String> getUnchangedPresetIds() {
        return unchangedPresetIds;
    }

    private static Set<String> immutableCopy(Set<String> values) {
        return Collections.unmodifiableSet(new TreeSet<String>(values));
    }
}
