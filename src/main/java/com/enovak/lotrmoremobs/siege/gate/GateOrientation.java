package com.enovak.lotrmoremobs.siege.gate;

public enum GateOrientation {
    WIDTH_X,
    WIDTH_Z;

    public int getForwardRotationSign(GateHingeSide hingeSide) {
        if (hingeSide == null) {
            return 0;
        }
        if (this == WIDTH_X) {
            return hingeSide == GateHingeSide.MINIMUM ? -1 : 1;
        }
        return hingeSide == GateHingeSide.MINIMUM ? 1 : -1;
    }

    public static GateOrientation fromSerializedName(
            String serializedName
    ) {
        if (serializedName != null) {
            try {
                return valueOf(serializedName);
            } catch (IllegalArgumentException ignored) {
                // Invalid orientation data leaves an old gate unconfigured.
            }
        }
        return null;
    }
}
