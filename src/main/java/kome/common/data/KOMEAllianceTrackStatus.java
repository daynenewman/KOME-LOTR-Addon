package kome.common.data;

public enum KOMEAllianceTrackStatus {
    NONE("none"),
    PENDING("pending"),
    ACTIVE("active");

    public final String key;

    private KOMEAllianceTrackStatus(String key) {
        this.key = key;
    }

    public static KOMEAllianceTrackStatus fromKey(String value) {
        String key = value == null ? "" : value.trim().toLowerCase();
        for (KOMEAllianceTrackStatus status : values()) {
            if (status.key.equals(key)) {
                return status;
            }
        }
        return NONE;
    }

    public static KOMEAllianceTrackStatus fromLegacyTier(int tier) {
        if (tier == KOMEAlliance.PENDING) {
            return PENDING;
        }
        return tier >= 0 ? ACTIVE : NONE;
    }
}
