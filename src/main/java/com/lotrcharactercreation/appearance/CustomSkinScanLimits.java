package com.lotrcharactercreation.appearance;

public final class CustomSkinScanLimits {

    public static final int DEFAULT_MAX_PNG_BYTES = 256 * 1024;
    public static final int DEFAULT_MAX_ENTRIES = 512;
    public static final long DEFAULT_MAX_TOTAL_BYTES = 32L * 1024L * 1024L;
    public static final CustomSkinScanLimits DEFAULT = new CustomSkinScanLimits(
        DEFAULT_MAX_PNG_BYTES,
        DEFAULT_MAX_ENTRIES,
        DEFAULT_MAX_TOTAL_BYTES);

    private final int maxPngBytes;
    private final int maxEntries;
    private final long maxTotalBytes;

    public CustomSkinScanLimits(int maxPngBytes, int maxEntries, long maxTotalBytes) {
        if (maxPngBytes <= 0 || maxEntries <= 0 || maxTotalBytes <= 0L) {
            throw new IllegalArgumentException("custom skin limits must be positive");
        }
        this.maxPngBytes = maxPngBytes;
        this.maxEntries = maxEntries;
        this.maxTotalBytes = maxTotalBytes;
    }

    public int getMaxPngBytes() {
        return maxPngBytes;
    }

    public int getMaxEntries() {
        return maxEntries;
    }

    public long getMaxTotalBytes() {
        return maxTotalBytes;
    }
}
