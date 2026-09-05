package com.lotrcharactercreation.appearance;

import java.util.Locale;

public enum PlayerSex {

    MALE("male", "Male"),
    FEMALE("female", "Female"),
    NONE("none", "None");

    private final String serializedId;
    private final String displayName;

    PlayerSex(String serializedId, String displayName) {
        this.serializedId = serializedId;
        this.displayName = displayName;
    }

    public String getSerializedId() {
        return serializedId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static PlayerSex findBySerializedId(String serializedId) {
        if (serializedId == null) {
            return null;
        }

        for (PlayerSex sex : values()) {
            if (sex.serializedId.equals(serializedId)) {
                return sex;
            }
        }

        return null;
    }

    public static PlayerSex fromCommandArgument(String argument) {
        if (argument == null) {
            return null;
        }

        return findBySerializedId(argument.toLowerCase(Locale.ROOT));
    }
}
