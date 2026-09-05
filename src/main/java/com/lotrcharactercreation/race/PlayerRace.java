package com.lotrcharactercreation.race;

import java.util.Locale;

public enum PlayerRace {

    MAN("man", "Man"),
    ELF("elf", "Elf"),
    DWARF("dwarf", "Dwarf"),
    HOBBIT("hobbit", "Hobbit"),
    ORC("orc", "Orc"),
    URUK_HAI("uruk_hai", "Uruk-hai");

    private final String serializedId;
    private final String displayName;

    PlayerRace(String serializedId, String displayName) {
        this.serializedId = serializedId;
        this.displayName = displayName;
    }

    public String getSerializedId() {
        return serializedId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static PlayerRace findBySerializedId(String serializedId) {
        if (serializedId == null) {
            return null;
        }

        for (PlayerRace race : values()) {
            if (race.serializedId.equals(serializedId)) {
                return race;
            }
        }

        return null;
    }

    public static PlayerRace fromSerializedId(String serializedId) {
        PlayerRace race = findBySerializedId(serializedId);
        return race == null ? MAN : race;
    }

    public static PlayerRace fromCommandArgument(String argument) {
        if (argument == null) {
            return null;
        }

        String normalized = argument.toLowerCase(Locale.ROOT);
        if (normalized.equals("uruk") || normalized.equals("urukhai") || normalized.equals("uruk-hai")) {
            normalized = URUK_HAI.serializedId;
        }

        for (PlayerRace race : values()) {
            if (race.serializedId.equals(normalized)) {
                return race;
            }
        }

        return null;
    }
}
