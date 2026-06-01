package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class KOMEArmyMovementOrder {
    public static final String MOVING = "moving";
    public static final String ARRIVED = "arrived";
    public static final long REAL_DAY_MILLIS = 24L * 60L * 60L * 1000L;

    public String id = "";
    public UUID owner;
    public String ownerName = "";
    public String ownerFaction = "";
    public String originTile = "";
    public String destinationTile = "";
    public final List<UUID> units = new ArrayList<UUID>();
    public int population;
    public int mountedUnits;
    public int groundUnits;
    public int distanceTiles = 1;
    public int tilesPerDay = 1;
    public long departureMillis;
    public long arrivalMillis;
    public String status = MOVING;

    public boolean isMoving() {
        return MOVING.equals(status);
    }

    public boolean hasArrived(long nowMillis) {
        return isMoving() && nowMillis >= arrivalMillis;
    }

    public void markArrived() {
        status = ARRIVED;
    }

    public long getRemainingMillis(long nowMillis) {
        return Math.max(0L, arrivalMillis - nowMillis);
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString("Id", id == null ? "" : id);
        nbt.setString("Owner", owner == null ? "" : owner.toString());
        nbt.setString("OwnerName", ownerName == null ? "" : ownerName);
        nbt.setString("OwnerFaction", ownerFaction == null ? "" : ownerFaction);
        nbt.setString("OriginTile", KOMEConquestTile.normalizeId(originTile));
        nbt.setString("DestinationTile", KOMEConquestTile.normalizeId(destinationTile));
        nbt.setInteger("Population", population);
        nbt.setInteger("MountedUnits", mountedUnits);
        nbt.setInteger("GroundUnits", groundUnits);
        nbt.setInteger("DistanceTiles", distanceTiles);
        nbt.setInteger("TilesPerDay", tilesPerDay);
        nbt.setLong("DepartureMillis", departureMillis);
        nbt.setLong("ArrivalMillis", arrivalMillis);
        nbt.setString("Status", status == null ? MOVING : status);
        NBTTagList unitList = new NBTTagList();
        for (UUID unit : units) {
            if (unit != null) {
                NBTTagCompound entry = new NBTTagCompound();
                entry.setString("Unit", unit.toString());
                unitList.appendTag(entry);
            }
        }
        nbt.setTag("Units", unitList);
        return nbt;
    }

    public void readFromNBT(NBTTagCompound nbt) {
        id = nbt.getString("Id");
        String ownerValue = nbt.getString("Owner");
        owner = ownerValue.length() == 0 ? null : UUID.fromString(ownerValue);
        ownerName = nbt.getString("OwnerName");
        ownerFaction = nbt.getString("OwnerFaction");
        originTile = KOMEConquestTile.normalizeId(nbt.getString("OriginTile"));
        destinationTile = KOMEConquestTile.normalizeId(nbt.getString("DestinationTile"));
        population = nbt.getInteger("Population");
        mountedUnits = nbt.getInteger("MountedUnits");
        groundUnits = nbt.getInteger("GroundUnits");
        distanceTiles = Math.max(1, nbt.getInteger("DistanceTiles"));
        tilesPerDay = Math.max(1, nbt.getInteger("TilesPerDay"));
        departureMillis = nbt.getLong("DepartureMillis");
        arrivalMillis = nbt.getLong("ArrivalMillis");
        status = nbt.getString("Status");
        if (status.length() == 0) {
            status = MOVING;
        }
        units.clear();
        NBTTagList unitList = nbt.getTagList("Units", 10);
        for (int i = 0; i < unitList.tagCount(); i++) {
            String unit = unitList.getCompoundTagAt(i).getString("Unit");
            if (unit.length() > 0) {
                units.add(UUID.fromString(unit));
            }
        }
    }
}
