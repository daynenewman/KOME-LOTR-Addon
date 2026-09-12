package com.lotrcharactercreation.appearance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CustomSkinScanResult {

    private final boolean successful;
    private final CustomSkinSnapshot snapshot;
    private final int rejectedEntryCount;
    private final List<String> diagnostics;

    static CustomSkinScanResult success(CustomSkinSnapshot snapshot, int rejectedEntryCount,
        List<String> diagnostics) {
        return new CustomSkinScanResult(true, snapshot, rejectedEntryCount, diagnostics);
    }

    static CustomSkinScanResult failure(List<String> diagnostics) {
        return new CustomSkinScanResult(false, null, 0, diagnostics);
    }

    private CustomSkinScanResult(boolean successful, CustomSkinSnapshot snapshot, int rejectedEntryCount,
        List<String> diagnostics) {
        this.successful = successful;
        this.snapshot = snapshot;
        this.rejectedEntryCount = rejectedEntryCount;
        this.diagnostics = Collections.unmodifiableList(new ArrayList<String>(diagnostics));
    }

    public boolean isSuccessful() {
        return successful;
    }

    public CustomSkinSnapshot getSnapshot() {
        return snapshot;
    }

    public int getRejectedEntryCount() {
        return rejectedEntryCount;
    }

    public List<String> getDiagnostics() {
        return diagnostics;
    }
}
