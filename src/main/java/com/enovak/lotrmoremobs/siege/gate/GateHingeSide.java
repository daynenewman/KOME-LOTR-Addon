package com.enovak.lotrmoremobs.siege.gate;

public enum GateHingeSide {
    MINIMUM,
    MAXIMUM;

    public static GateHingeSide fromSerializedName(
            String serializedName
    ) {
        if (serializedName != null) {
            try {
                return valueOf(serializedName);
            } catch (IllegalArgumentException ignored) {
                // Invalid hinge data leaves an old gate unconfigured.
            }
        }
        return null;
    }
}
