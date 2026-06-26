package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;

public class KOMETileWaypoint {
    public static final String RALLY = "rally";
    public static final String ENTRY_NORTH = "entry_north";
    public static final String ENTRY_SOUTH = "entry_south";
    public static final String ENTRY_EAST = "entry_east";
    public static final String ENTRY_WEST = "entry_west";

    public String tileId = "";
    public String type = RALLY;
    public int dimensionId;
    public double x;
    public double y;
    public double z;
    public String createdBy = "";
    public long createdAtMillis;
    public boolean manualOverride;

    public KOMETileWaypoint() {
    }

    public KOMETileWaypoint(String tileId, String type) {
        this.tileId = KOMEConquestTile.normalizeId(tileId);
        this.type = normalizeType(type);
    }

    public void set(int dimensionId, double x, double y, double z, String createdBy, boolean manualOverride) {
        this.dimensionId = dimensionId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.createdBy = createdBy == null ? "" : createdBy;
        this.createdAtMillis = System.currentTimeMillis();
        this.manualOverride = manualOverride;
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString("TileId", KOMEConquestTile.normalizeId(tileId));
        nbt.setString("Type", normalizeType(type));
        nbt.setInteger("DimensionId", dimensionId);
        nbt.setDouble("X", x);
        nbt.setDouble("Y", y);
        nbt.setDouble("Z", z);
        nbt.setString("CreatedBy", createdBy == null ? "" : createdBy);
        nbt.setLong("CreatedAtMillis", createdAtMillis);
        nbt.setBoolean("ManualOverride", manualOverride);
        return nbt;
    }

    public void readFromNBT(NBTTagCompound nbt) {
        tileId = KOMEConquestTile.normalizeId(nbt.getString("TileId"));
        type = normalizeType(nbt.getString("Type"));
        dimensionId = nbt.getInteger("DimensionId");
        x = nbt.getDouble("X");
        y = nbt.getDouble("Y");
        z = nbt.getDouble("Z");
        createdBy = nbt.getString("CreatedBy");
        createdAtMillis = nbt.getLong("CreatedAtMillis");
        manualOverride = nbt.getBoolean("ManualOverride");
    }

    public static String normalizeType(String value) {
        String type = value == null ? "" : value.trim().toLowerCase();
        if ("north".equals(type) || "entrynorth".equals(type) || "entry_north".equals(type)) {
            return ENTRY_NORTH;
        }
        if ("south".equals(type) || "entrysouth".equals(type) || "entry_south".equals(type)) {
            return ENTRY_SOUTH;
        }
        if ("east".equals(type) || "entryeast".equals(type) || "entry_east".equals(type)) {
            return ENTRY_EAST;
        }
        if ("west".equals(type) || "entrywest".equals(type) || "entry_west".equals(type)) {
            return ENTRY_WEST;
        }
        return RALLY;
    }

    public static boolean isValidType(String value) {
        String normalized = normalizeType(value);
        return RALLY.equals(normalized) || ENTRY_NORTH.equals(normalized) || ENTRY_SOUTH.equals(normalized)
            || ENTRY_EAST.equals(normalized) || ENTRY_WEST.equals(normalized);
    }

    public static String displayType(String type) {
        String normalized = normalizeType(type);
        if (ENTRY_NORTH.equals(normalized)) {
            return "North Entry";
        }
        if (ENTRY_SOUTH.equals(normalized)) {
            return "South Entry";
        }
        if (ENTRY_EAST.equals(normalized)) {
            return "East Entry";
        }
        if (ENTRY_WEST.equals(normalized)) {
            return "West Entry";
        }
        return "Rally Point";
    }
}
