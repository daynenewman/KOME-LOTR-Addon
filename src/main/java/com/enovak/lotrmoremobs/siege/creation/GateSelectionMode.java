package com.enovak.lotrmoremobs.siege.creation;

public enum GateSelectionMode {
    BLOCKS,
    LEFT_HINGE,
    RIGHT_HINGE,
    NONE;

    public static GateSelectionMode fromOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < values().length
                ? values()[ordinal]
                : NONE;
    }
}
