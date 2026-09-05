package com.lotrcharactercreation.appearance;

public enum OrcAppearanceGroup {

    COMMON_ORC("common_orc", "Common Orc");

    private final String serializedId;
    private final String displayName;

    OrcAppearanceGroup(String serializedId, String displayName) {
        this.serializedId = serializedId;
        this.displayName = displayName;
    }

    public String getSerializedId() {
        return serializedId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static OrcAppearanceGroup findBySerializedId(String serializedId) {
        if (serializedId == null) {
            return null;
        }

        for (OrcAppearanceGroup group : values()) {
            if (group.serializedId.equals(serializedId)) {
                return group;
            }
        }

        return null;
    }
}
