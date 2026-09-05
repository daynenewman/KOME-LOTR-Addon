package com.lotrcharactercreation.appearance;

import java.util.Locale;

public enum ElfAppearanceGroup {

    GALADHRIM("galadhrim", "Galadhrim"),
    WOODLAND("woodland", "Woodland"),
    HIGH_ELF("high_elf", "High Elf"),
    DORWINION("dorwinion", "Dorwinion");

    private final String serializedId;
    private final String displayName;

    ElfAppearanceGroup(String serializedId, String displayName) {
        this.serializedId = serializedId;
        this.displayName = displayName;
    }

    public String getSerializedId() {
        return serializedId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static ElfAppearanceGroup findBySerializedId(String serializedId) {
        if (serializedId == null) {
            return null;
        }

        for (ElfAppearanceGroup group : values()) {
            if (group.serializedId.equals(serializedId)) {
                return group;
            }
        }

        return null;
    }

    public static ElfAppearanceGroup fromCommandArgument(String argument) {
        if (argument == null) {
            return null;
        }

        String normalized = argument.toLowerCase(Locale.ROOT);
        if (normalized.equals("wood")) {
            normalized = WOODLAND.serializedId;
        } else if (normalized.equals("high")) {
            normalized = HIGH_ELF.serializedId;
        }

        return findBySerializedId(normalized);
    }
}
