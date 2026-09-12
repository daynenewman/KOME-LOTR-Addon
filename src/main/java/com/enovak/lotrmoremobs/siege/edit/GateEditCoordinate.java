package com.enovak.lotrmoremobs.siege.edit;

/** Detached relative-coordinate key for an EDIT_EXISTING draft. */
final class GateEditCoordinate {
    final int x;
    final int y;
    final int z;

    GateEditCoordinate(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    int getX() { return x; }
    int getY() { return y; }
    int getZ() { return z; }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof GateEditCoordinate)) return false;
        GateEditCoordinate coordinate = (GateEditCoordinate)other;
        return x == coordinate.x && y == coordinate.y && z == coordinate.z;
    }

    @Override
    public int hashCode() {
        return (31 * (31 * x + y)) + z;
    }
}
