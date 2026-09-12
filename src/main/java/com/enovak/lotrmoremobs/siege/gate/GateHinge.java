package com.enovak.lotrmoremobs.siege.gate;

public final class GateHinge {

    private final int relativeX;
    private final int relativeZ;
    private final GateHingeSide side;

    public GateHinge(int relativeX, int relativeZ) {
        this(relativeX, relativeZ, null);
    }

    public GateHinge(
            int relativeX,
            int relativeZ,
            GateHingeSide side
    ) {
        this.relativeX = relativeX;
        this.relativeZ = relativeZ;
        this.side = side;
    }

    public int getRelativeX() {
        return relativeX;
    }

    public int getRelativeZ() {
        return relativeZ;
    }

    public GateHingeSide getSide() {
        return side;
    }

    public double getPivotRelativeX(GateOrientation orientation) {
        if (orientation == GateOrientation.WIDTH_X) {
            return relativeX + (side == GateHingeSide.MAXIMUM
                    ? 1.0D
                    : 0.0D);
        }
        return relativeX + 0.5D;
    }

    public double getPivotRelativeZ(GateOrientation orientation) {
        if (orientation == GateOrientation.WIDTH_Z) {
            return relativeZ + (side == GateHingeSide.MAXIMUM
                    ? 1.0D
                    : 0.0D);
        }
        return relativeZ + 0.5D;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GateHinge)) {
            return false;
        }
        GateHinge hinge = (GateHinge)other;
        return relativeX == hinge.relativeX
                && relativeZ == hinge.relativeZ
                && side == hinge.side;
    }

    @Override
    public int hashCode() {
        int result = 31 * relativeX + relativeZ;
        return 31 * result + (side == null ? 0 : side.hashCode());
    }
}
