package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class KOMEMovementHistoryRecord {
    public static final String ACTIVE = "ACTIVE";
    public static final String ARRIVED = "ARRIVED";
    public static final String STOPPED = "STOPPED";
    public static final String CANCELLED = "CANCELLED";
    public static final String FAILED = "FAILED";

    public String historyId = "";
    public String movementOrderId = "";
    public String companyId = "";
    public String companyName = "";
    public UUID ownerUuid;
    public String ownerName = "";
    public String faction = "";
    public String originTile = "";
    public String finalDestinationTile = "";
    public String currentTile = "";
    public String nextTile = "";
    public final List<String> routeTiles = new ArrayList<String>();
    public int routeDistance;
    public int completedSteps;
    public int unitCount;
    public int totalPopulation;
    public int mountedPopulation;
    public int groundPopulation;
    public String speedDescription = "";
    public long createdAtMillis;
    public long firstStepMillis;
    public long lastStepMillis;
    public long nextStepAvailableMillis;
    public long completedAtMillis;
    public long stoppedAtMillis;
    public long cancelledAtMillis;
    public String status = ACTIVE;
    public UUID stoppedByUuid;
    public String stoppedByName = "";
    public String failureReason = "";
    public final List<String> usedBridgeNames = new ArrayList<String>();
    public final List<String> usedPassageNames = new ArrayList<String>();

    public boolean isActive() {
        return ACTIVE.equals(status);
    }

    public long getLatestActivityMillis() {
        long latest = 0L;
        latest = Math.max(latest, completedAtMillis);
        latest = Math.max(latest, stoppedAtMillis);
        latest = Math.max(latest, cancelledAtMillis);
        latest = Math.max(latest, lastStepMillis);
        latest = Math.max(latest, nextStepAvailableMillis);
        latest = Math.max(latest, firstStepMillis);
        latest = Math.max(latest, createdAtMillis);
        return latest;
    }

    public String stableCompanyKey() {
        if (companyId != null && companyId.length() > 0) {
            return companyId;
        }
        String owner = ownerUuid == null ? "" : ownerUuid.toString();
        String name = companyName == null ? "" : companyName;
        return owner + "|" + KOMEAlliance.normalizeFactionKey(faction) + "|" + name;
    }

    public void updateFromOrder(KOMEArmyMovementOrder order, String newStatus) {
        if (order == null) {
            return;
        }
        movementOrderId = safe(order.id);
        if (historyId.length() == 0) {
            historyId = movementOrderId.length() == 0 ? "history_" + System.currentTimeMillis() : movementOrderId;
        }
        companyId = safe(order.companyId);
        companyName = safe(order.companyName);
        ownerUuid = order.owner;
        ownerName = safe(order.ownerName);
        faction = KOMEAlliance.normalizeFactionKey(order.ownerFaction);
        originTile = KOMEConquestTile.normalizeId(order.originTile);
        finalDestinationTile = KOMEConquestTile.normalizeId(order.finalDestinationTile.length() == 0 ? order.destinationTile : order.finalDestinationTile);
        currentTile = KOMEConquestTile.normalizeId(order.currentTile);
        nextTile = KOMEConquestTile.normalizeId(order.nextTile);
        routeTiles.clear();
        for (String tile : order.routeTiles) {
            String normalized = KOMEConquestTile.normalizeId(tile);
            if (normalized.length() > 0) {
                routeTiles.add(normalized);
            }
        }
        routeDistance = Math.max(0, order.distanceTiles);
        completedSteps = Math.max(0, order.completedSteps);
        unitCount = order.units.size();
        totalPopulation = Math.max(0, order.population);
        mountedPopulation = Math.max(0, order.mountedPopulation);
        groundPopulation = Math.max(0, order.groundPopulation);
        speedDescription = order.tilesPerDay >= 2 ? "Mounted company - 2 tiles/day" : "Ground/mixed company - 1 tile/day";
        createdAtMillis = order.createdAtMillis > 0L ? order.createdAtMillis : order.departureMillis;
        if (firstStepMillis <= 0L) {
            firstStepMillis = order.departureMillis > 0L ? order.departureMillis : createdAtMillis;
        }
        lastStepMillis = order.lastStepMillis;
        nextStepAvailableMillis = order.nextStepAvailableMillis;
        if (newStatus != null && newStatus.length() > 0) {
            status = newStatus;
        }
        if (ARRIVED.equals(status)) {
            completedAtMillis = order.finalArrivalMillis > 0L ? order.finalArrivalMillis : System.currentTimeMillis();
        } else if (CANCELLED.equals(status)) {
            cancelledAtMillis = System.currentTimeMillis();
        } else if (FAILED.equals(status)) {
            failureReason = order.pendingSpawnReason == null || order.pendingSpawnReason.length() == 0
                ? order.lastSpawnFailureDetails : order.pendingSpawnReason;
        }
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString("HistoryId", safe(historyId));
        nbt.setString("MovementOrderId", safe(movementOrderId));
        nbt.setString("CompanyId", safe(companyId));
        nbt.setString("CompanyName", safe(companyName));
        nbt.setString("OwnerUuid", ownerUuid == null ? "" : ownerUuid.toString());
        nbt.setString("OwnerName", safe(ownerName));
        nbt.setString("Faction", KOMEAlliance.normalizeFactionKey(faction));
        nbt.setString("OriginTile", KOMEConquestTile.normalizeId(originTile));
        nbt.setString("FinalDestinationTile", KOMEConquestTile.normalizeId(finalDestinationTile));
        nbt.setString("CurrentTile", KOMEConquestTile.normalizeId(currentTile));
        nbt.setString("NextTile", KOMEConquestTile.normalizeId(nextTile));
        nbt.setInteger("RouteDistance", routeDistance);
        nbt.setInteger("CompletedSteps", completedSteps);
        nbt.setInteger("UnitCount", unitCount);
        nbt.setInteger("TotalPopulation", totalPopulation);
        nbt.setInteger("MountedPopulation", mountedPopulation);
        nbt.setInteger("GroundPopulation", groundPopulation);
        nbt.setString("SpeedDescription", safe(speedDescription));
        nbt.setLong("CreatedAtMillis", createdAtMillis);
        nbt.setLong("FirstStepMillis", firstStepMillis);
        nbt.setLong("LastStepMillis", lastStepMillis);
        nbt.setLong("NextStepAvailableMillis", nextStepAvailableMillis);
        nbt.setLong("CompletedAtMillis", completedAtMillis);
        nbt.setLong("StoppedAtMillis", stoppedAtMillis);
        nbt.setLong("CancelledAtMillis", cancelledAtMillis);
        nbt.setString("Status", safe(status));
        nbt.setString("StoppedByUuid", stoppedByUuid == null ? "" : stoppedByUuid.toString());
        nbt.setString("StoppedByName", safe(stoppedByName));
        nbt.setString("FailureReason", safe(failureReason));
        writeStringList(nbt, "RouteTiles", routeTiles);
        writeStringList(nbt, "UsedBridgeNames", usedBridgeNames);
        writeStringList(nbt, "UsedPassageNames", usedPassageNames);
        return nbt;
    }

    public void readFromNBT(NBTTagCompound nbt) {
        historyId = nbt.getString("HistoryId");
        movementOrderId = nbt.getString("MovementOrderId");
        companyId = nbt.getString("CompanyId");
        companyName = nbt.getString("CompanyName");
        String owner = nbt.getString("OwnerUuid");
        ownerUuid = owner.length() == 0 ? null : UUID.fromString(owner);
        ownerName = nbt.getString("OwnerName");
        faction = KOMEAlliance.normalizeFactionKey(nbt.getString("Faction"));
        originTile = KOMEConquestTile.normalizeId(nbt.getString("OriginTile"));
        finalDestinationTile = KOMEConquestTile.normalizeId(nbt.getString("FinalDestinationTile"));
        currentTile = KOMEConquestTile.normalizeId(nbt.getString("CurrentTile"));
        nextTile = KOMEConquestTile.normalizeId(nbt.getString("NextTile"));
        routeDistance = nbt.getInteger("RouteDistance");
        completedSteps = nbt.getInteger("CompletedSteps");
        unitCount = nbt.getInteger("UnitCount");
        totalPopulation = nbt.getInteger("TotalPopulation");
        mountedPopulation = nbt.getInteger("MountedPopulation");
        groundPopulation = nbt.getInteger("GroundPopulation");
        speedDescription = nbt.getString("SpeedDescription");
        createdAtMillis = nbt.getLong("CreatedAtMillis");
        firstStepMillis = nbt.getLong("FirstStepMillis");
        lastStepMillis = nbt.getLong("LastStepMillis");
        nextStepAvailableMillis = nbt.getLong("NextStepAvailableMillis");
        completedAtMillis = nbt.getLong("CompletedAtMillis");
        stoppedAtMillis = nbt.getLong("StoppedAtMillis");
        cancelledAtMillis = nbt.getLong("CancelledAtMillis");
        status = nbt.getString("Status");
        if (status.length() == 0) {
            status = ACTIVE;
        }
        String stoppedBy = nbt.getString("StoppedByUuid");
        stoppedByUuid = stoppedBy.length() == 0 ? null : UUID.fromString(stoppedBy);
        stoppedByName = nbt.getString("StoppedByName");
        failureReason = nbt.getString("FailureReason");
        routeTiles.clear();
        routeTiles.addAll(readStringList(nbt, "RouteTiles", true));
        usedBridgeNames.clear();
        usedBridgeNames.addAll(readStringList(nbt, "UsedBridgeNames", false));
        usedPassageNames.clear();
        usedPassageNames.addAll(readStringList(nbt, "UsedPassageNames", false));
    }

    private static void writeStringList(NBTTagCompound nbt, String key, List<String> values) {
        NBTTagList list = new NBTTagList();
        for (String value : values) {
            if (value != null && value.length() > 0) {
                NBTTagCompound entry = new NBTTagCompound();
                entry.setString("Value", value);
                list.appendTag(entry);
            }
        }
        nbt.setTag(key, list);
    }

    private static List<String> readStringList(NBTTagCompound nbt, String key, boolean normalizeTile) {
        List<String> values = new ArrayList<String>();
        NBTTagList list = nbt.getTagList(key, 10);
        for (int i = 0; i < list.tagCount(); i++) {
            String value = list.getCompoundTagAt(i).getString("Value");
            if (normalizeTile) {
                value = KOMEConquestTile.normalizeId(value);
            }
            if (value.length() > 0) {
                values.add(value);
            }
        }
        return values;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
