package com.enovak.lotrmoremobs.siege.gate;

/**
 * Persistent operating policy for a finalized Siege Gate.
 *
 * <p>This policy never overrides BREACHED. It only governs how an intact gate
 * behaves once normal opening/closing logic is available again.</p>
 */
public enum GateControlMode {
    AUTOMATIC(0, "Automatic"),
    LOCKED_CLOSED(1, "Locked Closed"),
    HELD_OPEN(2, "Held Open");

    private final int networkId;
    private final String displayName;

    GateControlMode(int networkId, String displayName) {
        this.networkId = networkId;
        this.displayName = displayName;
    }

    public int getNetworkId() {
        return networkId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public GateControlMode next() {
        GateControlMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static GateControlMode fromNetworkId(int networkId) {
        for (GateControlMode mode : values()) {
            if (mode.networkId == networkId) {
                return mode;
            }
        }
        return null;
    }

    public static GateControlMode fromSerializedName(String serializedName) {
        if (serializedName != null && !serializedName.isEmpty()) {
            try {
                return valueOf(serializedName);
            } catch (IllegalArgumentException ignored) {
                // Fall through to the backward-compatible default.
            }
        }
        return AUTOMATIC;
    }
}
