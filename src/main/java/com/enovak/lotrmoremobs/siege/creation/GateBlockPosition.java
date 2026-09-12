package com.enovak.lotrmoremobs.siege.creation;

public final class GateBlockPosition {

    private final int x;
    private final int y;
    private final int z;

    public GateBlockPosition(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public GateBlockPosition offset(int offsetX, int offsetY, int offsetZ) {
        return new GateBlockPosition(
                x + offsetX,
                y + offsetY,
                z + offsetZ
        );
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GateBlockPosition)) {
            return false;
        }
        GateBlockPosition position = (GateBlockPosition)other;
        return x == position.x && y == position.y && z == position.z;
    }

    @Override
    public int hashCode() {
        int result = x;
        result = 31 * result + y;
        result = 31 * result + z;
        return result;
    }
}
