package kome.common.data;

import lotr.common.world.map.LOTRWaypoint;
import net.minecraft.nbt.NBTTagCompound;

import java.util.UUID;

public class KOMETileWaypointLink {
    public static final String SOURCE_MANUAL = "manual";
    public static final String SOURCE_AUTO_DEFAULT = "auto_default";
    public static final String SOURCE_AUTO_COMMAND = "auto_command";

    public String tileId = "";
    public String lotrWaypointKey = "";
    public String waypointName = "";
    public String waypointDisplayName = "";
    public String waypointRegion = "";
    public String waypointFaction = "";
    public double waypointMapX;
    public double waypointMapZ;
    public int waypointWorldX;
    public int waypointWorldZ;
    public int dimensionId;
    public UUID linkedByUuid;
    public String linkedByName = "";
    public long linkedAtMillis;
    public long updatedAtMillis;
    public String source = SOURCE_MANUAL;
    public boolean manualOverride = true;

    public KOMETileWaypointLink() {
    }

    public KOMETileWaypointLink(String tileId, LOTRWaypoint waypoint, UUID linkedByUuid, String linkedByName) {
        this(tileId, waypoint, linkedByUuid, linkedByName, SOURCE_MANUAL, true);
    }

    public KOMETileWaypointLink(String tileId, LOTRWaypoint waypoint, UUID linkedByUuid, String linkedByName, String source, boolean manualOverride) {
        this.tileId = KOMEConquestTile.normalizeId(tileId);
        this.linkedByUuid = linkedByUuid;
        this.linkedByName = linkedByName == null ? "" : linkedByName;
        this.source = normalizeSource(source);
        this.manualOverride = manualOverride;
        linkedAtMillis = System.currentTimeMillis();
        updatedAtMillis = linkedAtMillis;
        updateWaypoint(waypoint);
    }

    public void updateWaypoint(LOTRWaypoint waypoint) {
        if (waypoint == null) {
            return;
        }
        lotrWaypointKey = waypoint.getCodeName();
        waypointName = waypoint.name();
        try {
            waypointDisplayName = waypoint.getDisplayName();
        } catch (Throwable ignored) {
            waypointDisplayName = lotrWaypointKey;
        }
        if (waypointDisplayName == null || waypointDisplayName.length() == 0) {
            waypointDisplayName = lotrWaypointKey;
        }
        waypointRegion = "";
        waypointFaction = "";
        waypointMapX = waypoint.getX();
        waypointMapZ = waypoint.getY();
        waypointWorldX = waypoint.getXCoord();
        waypointWorldZ = waypoint.getZCoord();
        dimensionId = 100;
        updatedAtMillis = System.currentTimeMillis();
    }

    public LOTRWaypoint resolveWaypoint() {
        if (lotrWaypointKey == null || lotrWaypointKey.length() == 0) {
            return null;
        }
        return LOTRWaypoint.waypointForName(lotrWaypointKey);
    }

    public String displayName() {
        LOTRWaypoint waypoint = resolveWaypoint();
        if (waypoint != null) {
            try {
                String display = waypoint.getDisplayName();
                if (display != null && display.length() > 0) {
                    return display;
                }
            } catch (Throwable ignored) {
            }
        }
        return waypointDisplayName == null || waypointDisplayName.length() == 0 ? lotrWaypointKey : waypointDisplayName;
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString("TileId", KOMEConquestTile.normalizeId(tileId));
        nbt.setString("WaypointKey", lotrWaypointKey == null ? "" : lotrWaypointKey);
        nbt.setString("WaypointName", waypointName == null ? "" : waypointName);
        nbt.setString("WaypointDisplayName", waypointDisplayName == null ? "" : waypointDisplayName);
        nbt.setString("WaypointRegion", waypointRegion == null ? "" : waypointRegion);
        nbt.setString("WaypointFaction", waypointFaction == null ? "" : waypointFaction);
        nbt.setDouble("WaypointMapX", waypointMapX);
        nbt.setDouble("WaypointMapZ", waypointMapZ);
        nbt.setInteger("WaypointWorldX", waypointWorldX);
        nbt.setInteger("WaypointWorldZ", waypointWorldZ);
        nbt.setInteger("DimensionId", dimensionId);
        nbt.setString("LinkedByUuid", linkedByUuid == null ? "" : linkedByUuid.toString());
        nbt.setString("LinkedByName", linkedByName == null ? "" : linkedByName);
        nbt.setLong("LinkedAtMillis", linkedAtMillis);
        nbt.setLong("UpdatedAtMillis", updatedAtMillis);
        nbt.setString("Source", normalizeSource(source));
        nbt.setBoolean("ManualOverride", manualOverride);
        return nbt;
    }

    public void readFromNBT(NBTTagCompound nbt) {
        tileId = KOMEConquestTile.normalizeId(nbt.getString("TileId"));
        lotrWaypointKey = nbt.getString("WaypointKey");
        waypointName = nbt.getString("WaypointName");
        waypointDisplayName = nbt.getString("WaypointDisplayName");
        waypointRegion = nbt.getString("WaypointRegion");
        waypointFaction = nbt.getString("WaypointFaction");
        waypointMapX = nbt.getDouble("WaypointMapX");
        waypointMapZ = nbt.getDouble("WaypointMapZ");
        waypointWorldX = nbt.getInteger("WaypointWorldX");
        waypointWorldZ = nbt.getInteger("WaypointWorldZ");
        dimensionId = nbt.getInteger("DimensionId");
        String uuid = nbt.getString("LinkedByUuid");
        if (uuid != null && uuid.length() > 0) {
            try {
                linkedByUuid = UUID.fromString(uuid);
            } catch (IllegalArgumentException ignored) {
                linkedByUuid = null;
            }
        }
        linkedByName = nbt.getString("LinkedByName");
        linkedAtMillis = nbt.getLong("LinkedAtMillis");
        updatedAtMillis = nbt.getLong("UpdatedAtMillis");
        source = nbt.hasKey("Source") ? normalizeSource(nbt.getString("Source")) : SOURCE_AUTO_COMMAND;
        manualOverride = nbt.hasKey("ManualOverride") && nbt.getBoolean("ManualOverride");
    }

    public boolean isAutomatic() {
        return !manualOverride || SOURCE_AUTO_DEFAULT.equals(normalizeSource(source)) || SOURCE_AUTO_COMMAND.equals(normalizeSource(source));
    }

    public static String normalizeSource(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase();
        if (SOURCE_AUTO_DEFAULT.equals(normalized) || "auto".equals(normalized)) {
            return SOURCE_AUTO_DEFAULT;
        }
        if (SOURCE_AUTO_COMMAND.equals(normalized) || "command".equals(normalized)) {
            return SOURCE_AUTO_COMMAND;
        }
        return SOURCE_MANUAL;
    }
}
