package com.enovak.lotrmoremobs.siege.gate;

public enum GateLeaf {
    LEFT(0),
    RIGHT(1),
    SPLIT_CENTER(2);

    private final int wireId;

    GateLeaf(int wireId) {
        this.wireId = wireId;
    }

    public int getWireId() {
        return wireId;
    }

    public boolean contributesToLeft() {
        return this == LEFT || this == SPLIT_CENTER;
    }

    public boolean contributesToRight() {
        return this == RIGHT || this == SPLIT_CENTER;
    }

    public boolean isActualLeft() {
        return this == LEFT;
    }

    public boolean isActualRight() {
        return this == RIGHT;
    }

    public boolean isSplitCenter() {
        return this == SPLIT_CENTER;
    }

    public boolean contributesTo(GateLeaf leaf) {
        return leaf == LEFT
                ? contributesToLeft()
                : leaf == RIGHT && contributesToRight();
    }

    public static GateLeaf fromWireId(int wireId) {
        for (GateLeaf leaf : values()) {
            if (leaf.wireId == wireId) {
                return leaf;
            }
        }
        return null;
    }

    public static GateLeaf fromSerializedName(String serializedName) {
        if (serializedName != null) {
            try {
                return valueOf(serializedName);
            } catch (IllegalArgumentException ignored) {
                // Invalid leaf names do not describe usable gate parts.
            }
        }
        return null;
    }
}
