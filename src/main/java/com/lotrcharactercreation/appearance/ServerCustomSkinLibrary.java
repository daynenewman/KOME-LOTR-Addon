package com.lotrcharactercreation.appearance;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.logging.log4j.Logger;

/**
 * Owns the authoritative server external-skin snapshot. Candidate scans are
 * completed before one atomic state replacement, so readers never observe a
 * partially rebuilt catalog.
 */
public final class ServerCustomSkinLibrary {

    private static final ServerCustomSkinLibrary INSTANCE = new ServerCustomSkinLibrary();

    private final AtomicReference<LibraryState> activeState = new AtomicReference<LibraryState>(
        LibraryState.empty());

    private ServerCustomSkinLibrary() {}

    public static ServerCustomSkinLibrary getInstance() {
        return INSTANCE;
    }

    public CustomSkinSnapshot getCurrentSnapshot() {
        return activeState.get().snapshot;
    }

    public AppearancePresetCatalog getCurrentCatalog() {
        return activeState.get().catalog;
    }

    /**
     * Synchronously constructs and validates a complete candidate. A
     * catastrophic root scan failure leaves the exact previous state active.
     */
    public synchronized ReloadResult reload(File customSkinRoot, Logger logger) {
        LibraryState previous = activeState.get();
        long nextRevision = previous.snapshot.getRevision() + 1L;
        CustomSkinScanResult scanResult;
        try {
            scanResult = ExternalAppearancePresetScanner.scanSnapshot(customSkinRoot, nextRevision, logger);
        } catch (RuntimeException exception) {
            String diagnostic = "Custom skin candidate build failed unexpectedly: " + safeMessage(exception);
            if (logger != null) {
                logger.error(diagnostic, exception);
            }
            return ReloadResult.notApplied(previous.snapshot, Collections.singletonList(diagnostic));
        }
        if (!scanResult.isSuccessful()) {
            if (logger != null) {
                logger.error("Custom skin library reload failed; retaining revision "
                    + previous.snapshot.getRevision());
            }
            return ReloadResult.notApplied(previous.snapshot, scanResult.getDiagnostics());
        }

        CustomSkinSnapshot candidate = scanResult.getSnapshot();
        AppearancePresetCatalog candidateCatalog;
        try {
            candidateCatalog = AppearancePresetCatalog.combine(
                AppearancePresetRegistry.getBuiltInCatalog(),
                candidate.getAppearancePresets());
        } catch (RuntimeException exception) {
            String diagnostic = "Custom skin candidate catalog failed validation: " + safeMessage(exception);
            if (logger != null) {
                logger.error(diagnostic, exception);
            }
            return ReloadResult.notApplied(previous.snapshot, Collections.singletonList(diagnostic));
        }
        LibraryState replacement = new LibraryState(candidate, candidateCatalog);
        activeState.set(replacement);

        CustomSkinSnapshotDiff diff = CustomSkinSnapshotDiff.between(previous.snapshot, candidate);
        if (logger != null) {
            logger.info("Published custom skin library revision " + candidate.getRevision()
                + " with " + candidate.getEntries().size() + " external preset(s), "
                + candidate.getTotalByteCount() + " bytes, digest " + candidate.getLibraryDigest()
                + "; rejected " + scanResult.getRejectedEntryCount() + " invalid path(s)/file(s)");
        }
        return ReloadResult.applied(previous.snapshot, candidate, diff, scanResult.getRejectedEntryCount(),
            scanResult.getDiagnostics());
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isEmpty() ? throwable.getClass().getSimpleName() : message;
    }

    private static final class LibraryState {

        private final CustomSkinSnapshot snapshot;
        private final AppearancePresetCatalog catalog;

        private static LibraryState empty() {
            return new LibraryState(CustomSkinSnapshot.empty(0L), AppearancePresetRegistry.getBuiltInCatalog());
        }

        private LibraryState(CustomSkinSnapshot snapshot, AppearancePresetCatalog catalog) {
            this.snapshot = snapshot;
            this.catalog = catalog;
        }
    }

    public static final class ReloadResult {

        private final boolean applied;
        private final CustomSkinSnapshot previousSnapshot;
        private final CustomSkinSnapshot currentSnapshot;
        private final CustomSkinSnapshotDiff diff;
        private final int rejectedEntryCount;
        private final List<String> diagnostics;

        private static ReloadResult applied(CustomSkinSnapshot previousSnapshot,
            CustomSkinSnapshot currentSnapshot, CustomSkinSnapshotDiff diff, int rejectedEntryCount,
            List<String> diagnostics) {
            return new ReloadResult(
                true,
                previousSnapshot,
                currentSnapshot,
                diff,
                rejectedEntryCount,
                diagnostics);
        }

        private static ReloadResult notApplied(CustomSkinSnapshot currentSnapshot, List<String> diagnostics) {
            return new ReloadResult(false, currentSnapshot, currentSnapshot, null, 0, diagnostics);
        }

        private ReloadResult(boolean applied, CustomSkinSnapshot previousSnapshot,
            CustomSkinSnapshot currentSnapshot, CustomSkinSnapshotDiff diff, int rejectedEntryCount,
            List<String> diagnostics) {
            this.applied = applied;
            this.previousSnapshot = previousSnapshot;
            this.currentSnapshot = currentSnapshot;
            this.diff = diff;
            this.rejectedEntryCount = rejectedEntryCount;
            this.diagnostics = Collections.unmodifiableList(new ArrayList<String>(diagnostics));
        }

        public boolean isApplied() {
            return applied;
        }

        public CustomSkinSnapshot getPreviousSnapshot() {
            return previousSnapshot;
        }

        public CustomSkinSnapshot getCurrentSnapshot() {
            return currentSnapshot;
        }

        public CustomSkinSnapshotDiff getDiff() {
            return diff;
        }

        public int getRejectedEntryCount() {
            return rejectedEntryCount;
        }

        public List<String> getDiagnostics() {
            return diagnostics;
        }
    }
}
