package com.enovak.lotrmoremobs.siege.gate;

public enum GateState {
    CLOSED,
    OPENING,
    OPEN,
    CLOSING,
    BREACHED;

    public static GateState fromSerializedName(String serializedName) {
        if (serializedName != null) {
            try {
                return valueOf(serializedName);
            } catch (IllegalArgumentException ignored) {
                // Fall through to the safe initial state.
            }
        }

        return CLOSED;
    }
}
