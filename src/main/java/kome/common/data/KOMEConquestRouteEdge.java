package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;

public class KOMEConquestRouteEdge {
    public static final String OPEN = "open";
    public static final String RIVER = "river";
    public static final String BRIDGE = "bridge";
    public static final String MOUNTAIN = "mountain";
    public static final String MOUNTAIN_PASS = "mountain_pass";
    public static final String BLOCKED = "blocked";

    public String fromTile = "";
    public String toTile = "";
    public String edgeType = OPEN;
    public String name = "";
    public String createdBy = "";
    public long createdAtMillis;
    public int markerDimension;
    public double markerX;
    public double markerY;
    public double markerZ;
    public boolean manual;

    public KOMEConquestRouteEdge() {
    }

    public KOMEConquestRouteEdge(String fromTile, String toTile, String edgeType) {
        setTiles(fromTile, toTile);
        this.edgeType = normalizeEdgeType(edgeType);
    }

    public void setTiles(String first, String second) {
        String a = KOMEConquestTile.normalizeId(first);
        String b = KOMEConquestTile.normalizeId(second);
        if (a.compareTo(b) <= 0) {
            fromTile = a;
            toTile = b;
        } else {
            fromTile = b;
            toTile = a;
        }
    }

    public boolean connects(String tile) {
        String normalized = KOMEConquestTile.normalizeId(tile);
        return fromTile.equals(normalized) || toTile.equals(normalized);
    }

    public String other(String tile) {
        String normalized = KOMEConquestTile.normalizeId(tile);
        if (fromTile.equals(normalized)) {
            return toTile;
        }
        if (toTile.equals(normalized)) {
            return fromTile;
        }
        return "";
    }

    public boolean isPassable() {
        return OPEN.equals(edgeType) || BRIDGE.equals(edgeType) || MOUNTAIN_PASS.equals(edgeType);
    }

    public boolean isSpecialPassage() {
        return BRIDGE.equals(edgeType) || MOUNTAIN_PASS.equals(edgeType);
    }

    public String describeBlock() {
        if (RIVER.equals(edgeType)) {
            return "Blocked by river between " + fromTile + " and " + toTile + ".";
        }
        if (MOUNTAIN.equals(edgeType)) {
            return "Blocked by mountains between " + fromTile + " and " + toTile + ".";
        }
        return "Blocked edge between " + fromTile + " and " + toTile + ".";
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString("FromTile", KOMEConquestTile.normalizeId(fromTile));
        nbt.setString("ToTile", KOMEConquestTile.normalizeId(toTile));
        nbt.setString("EdgeType", normalizeEdgeType(edgeType));
        nbt.setString("Name", name == null ? "" : name);
        nbt.setString("CreatedBy", createdBy == null ? "" : createdBy);
        nbt.setLong("CreatedAtMillis", createdAtMillis);
        nbt.setInteger("MarkerDimension", markerDimension);
        nbt.setDouble("MarkerX", markerX);
        nbt.setDouble("MarkerY", markerY);
        nbt.setDouble("MarkerZ", markerZ);
        nbt.setBoolean("Manual", manual);
        return nbt;
    }

    public void readFromNBT(NBTTagCompound nbt) {
        setTiles(nbt.getString("FromTile"), nbt.getString("ToTile"));
        edgeType = normalizeEdgeType(nbt.getString("EdgeType"));
        name = nbt.getString("Name");
        createdBy = nbt.getString("CreatedBy");
        createdAtMillis = nbt.getLong("CreatedAtMillis");
        markerDimension = nbt.getInteger("MarkerDimension");
        markerX = nbt.getDouble("MarkerX");
        markerY = nbt.getDouble("MarkerY");
        markerZ = nbt.getDouble("MarkerZ");
        manual = nbt.getBoolean("Manual");
    }

    public static String key(String first, String second) {
        String a = KOMEConquestTile.normalizeId(first);
        String b = KOMEConquestTile.normalizeId(second);
        return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
    }

    public static String normalizeEdgeType(String value) {
        String type = value == null ? "" : value.trim().toLowerCase().replace('-', '_');
        if ("bridge".equals(type)) {
            return BRIDGE;
        }
        if ("mountainpass".equals(type) || "mountain_pass".equals(type) || "passage".equals(type) || "pass".equals(type)) {
            return MOUNTAIN_PASS;
        }
        if ("river".equals(type)) {
            return RIVER;
        }
        if ("mountain".equals(type) || "mountains".equals(type)) {
            return MOUNTAIN;
        }
        if ("blocked".equals(type) || "block".equals(type)) {
            return BLOCKED;
        }
        return OPEN;
    }

    public static String displayEdgeType(String edgeType) {
        String type = normalizeEdgeType(edgeType);
        if (BRIDGE.equals(type)) {
            return "Bridge";
        }
        if (MOUNTAIN_PASS.equals(type)) {
            return "Mountain Pass";
        }
        if (RIVER.equals(type)) {
            return "River";
        }
        if (MOUNTAIN.equals(type)) {
            return "Mountain";
        }
        if (BLOCKED.equals(type)) {
            return "Blocked";
        }
        return "Open";
    }
}
