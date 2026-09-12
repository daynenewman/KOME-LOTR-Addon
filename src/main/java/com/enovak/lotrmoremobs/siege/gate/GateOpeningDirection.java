package com.enovak.lotrmoremobs.siege.gate;

public enum GateOpeningDirection {
    FORWARD(1),
    BACKWARD(-1);

    private final int rotationMultiplier;

    GateOpeningDirection(int rotationMultiplier) {
        this.rotationMultiplier = rotationMultiplier;
    }

    public int getRotationMultiplier() {
        return rotationMultiplier;
    }

    public GateOpeningDirection opposite() {
        return this == FORWARD ? BACKWARD : FORWARD;
    }

    public static GateOpeningDirection fromSerializedName(
            String serializedName
    ) {
        if (serializedName != null) {
            try {
                return valueOf(serializedName);
            } catch (IllegalArgumentException ignored) {
                // Invalid direction data leaves an old gate unconfigured.
            }
        }
        return null;
    }
}
