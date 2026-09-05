package com.lotrcharactercreation.appearance;

import java.util.Locale;

public enum DwarfAppearanceGroup {

    STANDARD("standard", "Standard"),
    BLUE_MOUNTAINS("blue_mountains", "Blue Mountains");

    private final String serializedId;
    private final String displayName;

    DwarfAppearanceGroup(String serializedId, String displayName) {
        this.serializedId = serializedId;
        this.displayName = displayName;
    }

    public String getSerializedId() {
        return serializedId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static DwarfAppearanceGroup fromCommandArgument(String argument) {
        if (argument == null) {
            return null;
        }

        String normalized = argument.toLowerCase(Locale.ROOT);
        if (normalized.equals("blue") || normalized.equals("bluemountains")) {
            normalized = BLUE_MOUNTAINS.serializedId;
        }

        for (DwarfAppearanceGroup group : values()) {
            if (group.serializedId.equals(normalized)) {
                return group;
            }
        }

        return null;
    }
}
