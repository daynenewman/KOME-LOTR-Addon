package com.lotrcharactercreation.appearance;

import java.util.Locale;

public enum UrukHaiAppearanceGroup {

    ISENGARD_URUK_HAI("isengard_uruk_hai", "Isengard Uruk-hai"),
    MORDOR_BLACK_URUK("mordor_black_uruk", "Mordor Black Uruk"),
    GUNDABAD_URUK("gundabad_uruk", "Gundabad Uruk");

    private final String serializedId;
    private final String displayName;

    UrukHaiAppearanceGroup(String serializedId, String displayName) {
        this.serializedId = serializedId;
        this.displayName = displayName;
    }

    public String getSerializedId() {
        return serializedId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static UrukHaiAppearanceGroup findBySerializedId(String serializedId) {
        if (serializedId == null) {
            return null;
        }

        for (UrukHaiAppearanceGroup group : values()) {
            if (group.serializedId.equals(serializedId)) {
                return group;
            }
        }

        return null;
    }

    public static UrukHaiAppearanceGroup fromCommandArgument(String argument) {
        if (argument == null) {
            return null;
        }

        String normalized = argument.toLowerCase(Locale.ROOT);
        if (normalized.equals("isengard") || normalized.equals("uruk")) {
            normalized = ISENGARD_URUK_HAI.serializedId;
        } else if (normalized.equals("mordor") || normalized.equals("black_uruk") || normalized.equals("black")) {
            normalized = MORDOR_BLACK_URUK.serializedId;
        } else if (normalized.equals("gundabad")) {
            normalized = GUNDABAD_URUK.serializedId;
        }

        return findBySerializedId(normalized);
    }
}
