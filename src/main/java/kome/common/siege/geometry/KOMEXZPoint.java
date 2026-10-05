package kome.common.siege.geometry;

/** Immutable authored point on the Minecraft X/Z block grid. */
public final class KOMEXZPoint implements Comparable<KOMEXZPoint> {
    private final int x;
    private final int z;

    public KOMEXZPoint(int x, int z) {
        this.x = x;
        this.z = z;
    }

    public int getX() { return x; }
    public int getZ() { return z; }

    @Override
    public int compareTo(KOMEXZPoint other) {
        if (other == null) return 1;
        int result = Integer.compare(x, other.x);
        return result != 0 ? result : Integer.compare(z, other.z);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof KOMEXZPoint)) return false;
        KOMEXZPoint other = (KOMEXZPoint)object;
        return x == other.x && z == other.z;
    }

    @Override
    public int hashCode() { return 31 * x + z; }

    @Override
    public String toString() { return "(" + x + "," + z + ")"; }
}
