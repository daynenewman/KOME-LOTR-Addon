package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class KOMEArmyMovementOrder {
    public static final String MOVING = "moving";
    public static final String ARRIVED = "arrived";
    public static final String CANCELLED = "cancelled";
    public static final String PENDING_SPAWN = "pending_spawn";
    public static final String SPAWNING = "spawning";
    public static final String SPAWN_BLOCKED = "spawn_blocked";
    public static final String WAITING_NEXT_STEP = "waiting_next_step";
    public static final String STOPPED = "stopped";
    public static final String ACCESS_HALTED = "access_halted";
    public static final String RETREATING = "retreating";
    public static final String HOLDING = "holding";
    public static final String WAR_ENDED_HALTED = "war_ended_halted";
    public static final long REAL_DAY_MILLIS = 24L * 60L * 60L * 1000L;

    public String id = "";
    public String companyId = "";
    public String companyName = "";
    public UUID owner;
    public String ownerName = "";
    public String ownerFaction = "";
    public String originTile = "";
    public String destinationTile = "";
    public final List<UUID> units = new ArrayList<UUID>();
    public final List<String> routeTiles = new ArrayList<String>();
    public final List<String> traveledRouteTiles = new ArrayList<String>();
    public int population;
    public int mountedUnits;
    public int groundUnits;
    public int mountedPopulation;
    public int groundPopulation;
    public int distanceTiles = 1;
    public int tilesPerDay = 1;
    public int currentRouteIndex;
    public int nextRouteIndex = 1;
    public int finalRouteIndex;
    public int totalSteps;
    public String currentTile = "";
    public String nextTile = "";
    public String currentStepOriginTile = "";
    public String currentStepDestinationTile = "";
    public String finalDestinationTile = "";
    public long lastStepMillis;
    public long nextStepAvailableMillis;
    public long stepDepartureMillis;
    public long stepArrivalMillis;
    public long nextStepDepartureMillis;
    public long totalDepartureMillis;
    public long finalArrivalMillis;
    public int completedSteps;
    public int dailyStepsRemaining;
    public boolean stopped;
    public long nextDailyStepMillis;
    public String movementScheduleMode = "";
    public String filter = "all";
    public long createdAtMillis;
    public long departureMillis;
    public long arrivalMillis;
    public String status = MOVING;
    public String pendingSpawnReason = "";
    public String lastSpawnLabel = "";
    public int lastSpawnDimension;
    public double lastSpawnX;
    public double lastSpawnY;
    public double lastSpawnZ;
    public String lastAttemptLabel = "";
    public int lastAttemptDimension;
    public double lastAttemptX;
    public double lastAttemptY;
    public double lastAttemptZ;
    public String arrivalPointTileId = "";
    public String arrivalPointSource = "";
    public int arrivalDimension;
    public double arrivalX;
    public double arrivalY;
    public double arrivalZ;
    public int spawnAttemptCount;
    public long lastSpawnAttemptMillis;
    public long nextSpawnRetryMillis;
    public String lastSpawnFailureCode = "";
    public String lastSpawnFailureDetails = "";
    public boolean spawnRetryPaused;
    public int lastChunkLoadDimension;
    public int lastChunkLoadChunkX;
    public int lastChunkLoadChunkZ;
    public long lastChunkLoadAttemptMillis;
    public boolean lastChunkLoadTicketAcquired;
    public boolean retreating;
    public boolean haltAfterArrival;
    public String accessLossReason = "";
    public long accessLostAtMillis;
    public String accessChoice = "";

    public boolean isMoving() {
        return MOVING.equals(status) || PENDING_SPAWN.equals(status) || SPAWNING.equals(status)
            || SPAWN_BLOCKED.equals(status) || WAITING_NEXT_STEP.equals(status)
            || ACCESS_HALTED.equals(status) || RETREATING.equals(status) || HOLDING.equals(status)
            || WAR_ENDED_HALTED.equals(status);
    }

    public boolean hasArrived(long nowMillis) {
        return (MOVING.equals(status) || PENDING_SPAWN.equals(status) || SPAWNING.equals(status)) && nowMillis >= arrivalMillis
            && !spawnRetryPaused && nowMillis >= nextSpawnRetryMillis;
    }

    public boolean isPendingSpawn() {
        return PENDING_SPAWN.equals(status) || SPAWN_BLOCKED.equals(status);
    }

    public void markArrived() {
        status = ARRIVED;
        pendingSpawnReason = "";
    }

    public void markPendingSpawn(String reason) {
        status = PENDING_SPAWN;
        pendingSpawnReason = reason == null ? "" : reason;
    }

    public long getRemainingMillis(long nowMillis) {
        return Math.max(0L, arrivalMillis - nowMillis);
    }

    public int totalSteps() {
        return totalSteps > 0 ? totalSteps : routeTiles.size() >= 2 ? routeTiles.size() - 1 : Math.max(1, distanceTiles);
    }

    public boolean isAtFinalRouteTile() {
        int finalIndex = finalRouteIndex > 0 ? finalRouteIndex : routeTiles.size() >= 2 ? routeTiles.size() - 1 : Math.max(1, distanceTiles);
        return currentRouteIndex >= finalIndex || completedSteps >= Math.max(1, distanceTiles);
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString("Id", id == null ? "" : id);
        nbt.setString("CompanyId", companyId == null ? "" : companyId);
        nbt.setString("CompanyName", companyName == null ? "" : companyName);
        nbt.setString("Owner", owner == null ? "" : owner.toString());
        nbt.setString("OwnerName", ownerName == null ? "" : ownerName);
        nbt.setString("OwnerFaction", ownerFaction == null ? "" : ownerFaction);
        nbt.setString("OriginTile", KOMEConquestTile.normalizeId(originTile));
        nbt.setString("DestinationTile", KOMEConquestTile.normalizeId(destinationTile));
        nbt.setInteger("Population", population);
        nbt.setInteger("MountedUnits", mountedUnits);
        nbt.setInteger("GroundUnits", groundUnits);
        nbt.setInteger("MountedPopulation", mountedPopulation);
        nbt.setInteger("GroundPopulation", groundPopulation);
        nbt.setInteger("DistanceTiles", distanceTiles);
        nbt.setInteger("TilesPerDay", tilesPerDay);
        nbt.setInteger("CurrentRouteIndex", currentRouteIndex);
        nbt.setInteger("NextRouteIndex", nextRouteIndex);
        nbt.setInteger("FinalRouteIndex", finalRouteIndex);
        nbt.setInteger("TotalSteps", totalSteps());
        nbt.setString("CurrentTile", KOMEConquestTile.normalizeId(currentTile));
        nbt.setString("NextTile", KOMEConquestTile.normalizeId(nextTile));
        nbt.setString("CurrentStepOriginTile", KOMEConquestTile.normalizeId(currentStepOriginTile));
        nbt.setString("CurrentStepDestinationTile", KOMEConquestTile.normalizeId(currentStepDestinationTile));
        nbt.setString("FinalDestinationTile", KOMEConquestTile.normalizeId(finalDestinationTile));
        nbt.setLong("LastStepMillis", lastStepMillis);
        nbt.setLong("NextStepAvailableMillis", nextStepAvailableMillis);
        nbt.setLong("StepDepartureMillis", stepDepartureMillis);
        nbt.setLong("StepArrivalMillis", stepArrivalMillis);
        nbt.setLong("NextStepDepartureMillis", nextStepDepartureMillis);
        nbt.setLong("TotalDepartureMillis", totalDepartureMillis);
        nbt.setLong("FinalArrivalMillis", finalArrivalMillis);
        nbt.setInteger("CompletedSteps", completedSteps);
        nbt.setInteger("DailyStepsRemaining", dailyStepsRemaining);
        nbt.setBoolean("Stopped", stopped);
        nbt.setLong("NextDailyStepMillis", nextDailyStepMillis);
        nbt.setString("MovementScheduleMode", movementScheduleMode == null ? "" : movementScheduleMode);
        nbt.setString("Filter", filter == null ? "all" : filter);
        nbt.setLong("CreatedAtMillis", createdAtMillis > 0L ? createdAtMillis : departureMillis);
        nbt.setLong("DepartureMillis", departureMillis);
        nbt.setLong("ArrivalMillis", arrivalMillis);
        nbt.setString("Status", status == null ? MOVING : status);
        nbt.setString("PendingSpawnReason", pendingSpawnReason == null ? "" : pendingSpawnReason);
        nbt.setString("LastSpawnLabel", lastSpawnLabel == null ? "" : lastSpawnLabel);
        nbt.setInteger("LastSpawnDimension", lastSpawnDimension);
        nbt.setDouble("LastSpawnX", lastSpawnX);
        nbt.setDouble("LastSpawnY", lastSpawnY);
        nbt.setDouble("LastSpawnZ", lastSpawnZ);
        nbt.setString("LastAttemptLabel", lastAttemptLabel == null ? "" : lastAttemptLabel);
        nbt.setInteger("LastAttemptDimension", lastAttemptDimension);
        nbt.setDouble("LastAttemptX", lastAttemptX);
        nbt.setDouble("LastAttemptY", lastAttemptY);
        nbt.setDouble("LastAttemptZ", lastAttemptZ);
        nbt.setString("ArrivalPointTileId", KOMEConquestTile.normalizeId(arrivalPointTileId));
        nbt.setString("ArrivalPointSource", arrivalPointSource == null ? "" : arrivalPointSource);
        nbt.setInteger("ArrivalDimension", arrivalDimension);
        nbt.setDouble("ArrivalX", arrivalX);
        nbt.setDouble("ArrivalY", arrivalY);
        nbt.setDouble("ArrivalZ", arrivalZ);
        nbt.setInteger("SpawnAttemptCount", spawnAttemptCount);
        nbt.setLong("LastSpawnAttemptMillis", lastSpawnAttemptMillis);
        nbt.setLong("NextSpawnRetryMillis", nextSpawnRetryMillis);
        nbt.setString("LastSpawnFailureCode", lastSpawnFailureCode == null ? "" : lastSpawnFailureCode);
        nbt.setString("LastSpawnFailureDetails", lastSpawnFailureDetails == null ? "" : lastSpawnFailureDetails);
        nbt.setBoolean("SpawnRetryPaused", spawnRetryPaused);
        nbt.setInteger("LastChunkLoadDimension", lastChunkLoadDimension);
        nbt.setInteger("LastChunkLoadChunkX", lastChunkLoadChunkX);
        nbt.setInteger("LastChunkLoadChunkZ", lastChunkLoadChunkZ);
        nbt.setLong("LastChunkLoadAttemptMillis", lastChunkLoadAttemptMillis);
        nbt.setBoolean("LastChunkLoadTicketAcquired", lastChunkLoadTicketAcquired);
        nbt.setBoolean("Retreating", retreating);
        nbt.setBoolean("HaltAfterArrival", haltAfterArrival);
        nbt.setString("AccessLossReason", accessLossReason == null ? "" : accessLossReason);
        nbt.setLong("AccessLostAtMillis", accessLostAtMillis);
        nbt.setString("AccessChoice", accessChoice == null ? "" : accessChoice);
        NBTTagList unitList = new NBTTagList();
        for (UUID unit : units) {
            if (unit != null) {
                NBTTagCompound entry = new NBTTagCompound();
                entry.setString("Unit", unit.toString());
                unitList.appendTag(entry);
            }
        }
        nbt.setTag("Units", unitList);
        NBTTagList routeList = new NBTTagList();
        for (String tile : routeTiles) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setString("Tile", KOMEConquestTile.normalizeId(tile));
            routeList.appendTag(entry);
        }
        nbt.setTag("RouteTiles", routeList);
        NBTTagList traveledList = new NBTTagList();
        for (String tile : traveledRouteTiles) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setString("Tile", KOMEConquestTile.normalizeId(tile));
            traveledList.appendTag(entry);
        }
        nbt.setTag("TraveledRouteTiles", traveledList);
        return nbt;
    }

    public void readFromNBT(NBTTagCompound nbt) {
        id = nbt.getString("Id");
        companyId = nbt.getString("CompanyId");
        companyName = nbt.getString("CompanyName");
        String ownerValue = nbt.getString("Owner");
        owner = ownerValue.length() == 0 ? null : UUID.fromString(ownerValue);
        ownerName = nbt.getString("OwnerName");
        ownerFaction = nbt.getString("OwnerFaction");
        originTile = KOMEConquestTile.normalizeId(nbt.getString("OriginTile"));
        destinationTile = KOMEConquestTile.normalizeId(nbt.getString("DestinationTile"));
        population = nbt.getInteger("Population");
        mountedUnits = nbt.getInteger("MountedUnits");
        groundUnits = nbt.getInteger("GroundUnits");
        mountedPopulation = nbt.hasKey("MountedPopulation") ? nbt.getInteger("MountedPopulation") : 0;
        groundPopulation = nbt.hasKey("GroundPopulation") ? nbt.getInteger("GroundPopulation") : 0;
        if (mountedPopulation == 0 && groundPopulation == 0 && population > 0) {
            if (groundUnits == 0 && mountedUnits > 0) {
                mountedPopulation = population;
            } else if (mountedUnits == 0 && groundUnits > 0) {
                groundPopulation = population;
            }
        }
        distanceTiles = Math.max(1, nbt.getInteger("DistanceTiles"));
        tilesPerDay = Math.max(1, nbt.getInteger("TilesPerDay"));
        currentRouteIndex = nbt.hasKey("CurrentRouteIndex") ? Math.max(0, nbt.getInteger("CurrentRouteIndex")) : 0;
        nextRouteIndex = nbt.hasKey("NextRouteIndex") ? Math.max(1, nbt.getInteger("NextRouteIndex")) : currentRouteIndex + 1;
        finalRouteIndex = nbt.hasKey("FinalRouteIndex") ? Math.max(0, nbt.getInteger("FinalRouteIndex")) : 0;
        totalSteps = nbt.hasKey("TotalSteps") ? Math.max(0, nbt.getInteger("TotalSteps")) : 0;
        currentTile = KOMEConquestTile.normalizeId(nbt.getString("CurrentTile"));
        nextTile = KOMEConquestTile.normalizeId(nbt.getString("NextTile"));
        currentStepOriginTile = KOMEConquestTile.normalizeId(nbt.getString("CurrentStepOriginTile"));
        currentStepDestinationTile = KOMEConquestTile.normalizeId(nbt.getString("CurrentStepDestinationTile"));
        finalDestinationTile = KOMEConquestTile.normalizeId(nbt.getString("FinalDestinationTile"));
        lastStepMillis = nbt.hasKey("LastStepMillis") ? nbt.getLong("LastStepMillis") : 0L;
        nextStepAvailableMillis = nbt.hasKey("NextStepAvailableMillis") ? nbt.getLong("NextStepAvailableMillis") : 0L;
        stepDepartureMillis = nbt.hasKey("StepDepartureMillis") ? nbt.getLong("StepDepartureMillis") : departureMillis;
        stepArrivalMillis = nbt.hasKey("StepArrivalMillis") ? nbt.getLong("StepArrivalMillis") : arrivalMillis;
        nextStepDepartureMillis = nbt.hasKey("NextStepDepartureMillis") ? nbt.getLong("NextStepDepartureMillis") : 0L;
        totalDepartureMillis = nbt.hasKey("TotalDepartureMillis") ? nbt.getLong("TotalDepartureMillis") : departureMillis;
        finalArrivalMillis = nbt.hasKey("FinalArrivalMillis") ? nbt.getLong("FinalArrivalMillis") : arrivalMillis;
        completedSteps = nbt.hasKey("CompletedSteps") ? Math.max(0, nbt.getInteger("CompletedSteps")) : currentRouteIndex;
        dailyStepsRemaining = nbt.hasKey("DailyStepsRemaining") ? Math.max(0, nbt.getInteger("DailyStepsRemaining")) : 0;
        stopped = nbt.hasKey("Stopped") && nbt.getBoolean("Stopped");
        nextDailyStepMillis = nbt.hasKey("NextDailyStepMillis") ? nbt.getLong("NextDailyStepMillis") : 0L;
        movementScheduleMode = nbt.hasKey("MovementScheduleMode") ? nbt.getString("MovementScheduleMode") : "";
        filter = nbt.hasKey("Filter") ? nbt.getString("Filter") : "all";
        if (!"mounted".equals(filter) && !"ground".equals(filter)) {
            filter = "all";
        }
        departureMillis = nbt.getLong("DepartureMillis");
        createdAtMillis = nbt.hasKey("CreatedAtMillis") ? nbt.getLong("CreatedAtMillis") : departureMillis;
        if (departureMillis <= 0L) {
            departureMillis = createdAtMillis;
        }
        arrivalMillis = nbt.getLong("ArrivalMillis");
        status = nbt.getString("Status");
        if (status.length() == 0) {
            status = MOVING;
        }
        pendingSpawnReason = nbt.hasKey("PendingSpawnReason") ? nbt.getString("PendingSpawnReason") : "";
        lastSpawnLabel = nbt.hasKey("LastSpawnLabel") ? nbt.getString("LastSpawnLabel") : "";
        lastSpawnDimension = nbt.hasKey("LastSpawnDimension") ? nbt.getInteger("LastSpawnDimension") : 0;
        lastSpawnX = nbt.hasKey("LastSpawnX") ? nbt.getDouble("LastSpawnX") : 0.0D;
        lastSpawnY = nbt.hasKey("LastSpawnY") ? nbt.getDouble("LastSpawnY") : 0.0D;
        lastSpawnZ = nbt.hasKey("LastSpawnZ") ? nbt.getDouble("LastSpawnZ") : 0.0D;
        lastAttemptLabel = nbt.hasKey("LastAttemptLabel") ? nbt.getString("LastAttemptLabel") : "";
        lastAttemptDimension = nbt.hasKey("LastAttemptDimension") ? nbt.getInteger("LastAttemptDimension") : 0;
        lastAttemptX = nbt.hasKey("LastAttemptX") ? nbt.getDouble("LastAttemptX") : 0.0D;
        lastAttemptY = nbt.hasKey("LastAttemptY") ? nbt.getDouble("LastAttemptY") : 0.0D;
        lastAttemptZ = nbt.hasKey("LastAttemptZ") ? nbt.getDouble("LastAttemptZ") : 0.0D;
        arrivalPointTileId = KOMEConquestTile.normalizeId(nbt.getString("ArrivalPointTileId"));
        arrivalPointSource = nbt.hasKey("ArrivalPointSource") ? nbt.getString("ArrivalPointSource") : "";
        arrivalDimension = nbt.hasKey("ArrivalDimension") ? nbt.getInteger("ArrivalDimension") : 0;
        arrivalX = nbt.hasKey("ArrivalX") ? nbt.getDouble("ArrivalX") : 0.0D;
        arrivalY = nbt.hasKey("ArrivalY") ? nbt.getDouble("ArrivalY") : 0.0D;
        arrivalZ = nbt.hasKey("ArrivalZ") ? nbt.getDouble("ArrivalZ") : 0.0D;
        spawnAttemptCount = nbt.hasKey("SpawnAttemptCount") ? nbt.getInteger("SpawnAttemptCount") : 0;
        lastSpawnAttemptMillis = nbt.hasKey("LastSpawnAttemptMillis") ? nbt.getLong("LastSpawnAttemptMillis") : 0L;
        nextSpawnRetryMillis = nbt.hasKey("NextSpawnRetryMillis") ? nbt.getLong("NextSpawnRetryMillis") : 0L;
        lastSpawnFailureCode = nbt.hasKey("LastSpawnFailureCode") ? nbt.getString("LastSpawnFailureCode") : "";
        lastSpawnFailureDetails = nbt.hasKey("LastSpawnFailureDetails") ? nbt.getString("LastSpawnFailureDetails") : "";
        spawnRetryPaused = nbt.hasKey("SpawnRetryPaused") && nbt.getBoolean("SpawnRetryPaused");
        lastChunkLoadDimension = nbt.hasKey("LastChunkLoadDimension") ? nbt.getInteger("LastChunkLoadDimension") : 0;
        lastChunkLoadChunkX = nbt.hasKey("LastChunkLoadChunkX") ? nbt.getInteger("LastChunkLoadChunkX") : 0;
        lastChunkLoadChunkZ = nbt.hasKey("LastChunkLoadChunkZ") ? nbt.getInteger("LastChunkLoadChunkZ") : 0;
        lastChunkLoadAttemptMillis = nbt.hasKey("LastChunkLoadAttemptMillis") ? nbt.getLong("LastChunkLoadAttemptMillis") : 0L;
        lastChunkLoadTicketAcquired = nbt.hasKey("LastChunkLoadTicketAcquired") && nbt.getBoolean("LastChunkLoadTicketAcquired");
        retreating = nbt.getBoolean("Retreating");
        haltAfterArrival = nbt.getBoolean("HaltAfterArrival");
        accessLossReason = nbt.getString("AccessLossReason");
        accessLostAtMillis = nbt.getLong("AccessLostAtMillis");
        accessChoice = nbt.getString("AccessChoice");
        units.clear();
        NBTTagList unitList = nbt.getTagList("Units", 10);
        for (int i = 0; i < unitList.tagCount(); i++) {
            String unit = unitList.getCompoundTagAt(i).getString("Unit");
            if (unit.length() > 0) {
                units.add(UUID.fromString(unit));
            }
        }
        routeTiles.clear();
        NBTTagList routeList = nbt.getTagList("RouteTiles", 10);
        for (int i = 0; i < routeList.tagCount(); i++) {
            String tile = KOMEConquestTile.normalizeId(routeList.getCompoundTagAt(i).getString("Tile"));
            if (tile.length() > 0) {
                routeTiles.add(tile);
            }
        }
        traveledRouteTiles.clear();
        NBTTagList traveledList = nbt.getTagList("TraveledRouteTiles", 10);
        for (int i = 0; i < traveledList.tagCount(); i++) {
            String tile = KOMEConquestTile.normalizeId(traveledList.getCompoundTagAt(i).getString("Tile"));
            if (tile.length() > 0) {
                traveledRouteTiles.add(tile);
            }
        }
        if (traveledRouteTiles.isEmpty() && currentTile.length() > 0) {
            for (int i = 0; i <= currentRouteIndex && i < routeTiles.size(); i++) {
                traveledRouteTiles.add(KOMEConquestTile.normalizeId(routeTiles.get(i)));
            }
        }
        if (finalDestinationTile.length() == 0) {
            finalDestinationTile = destinationTile;
        }
        if (finalRouteIndex <= 0) {
            finalRouteIndex = routeTiles.size() >= 2 ? routeTiles.size() - 1 : Math.max(1, distanceTiles);
        }
        if (totalSteps <= 0) {
            totalSteps = routeTiles.size() >= 2 ? routeTiles.size() - 1 : Math.max(1, distanceTiles);
        }
        if (currentTile.length() == 0) {
            currentTile = routeTiles.size() > currentRouteIndex ? KOMEConquestTile.normalizeId(routeTiles.get(currentRouteIndex)) : originTile;
        }
        if (nextTile.length() == 0) {
            nextTile = routeTiles.size() > nextRouteIndex ? KOMEConquestTile.normalizeId(routeTiles.get(nextRouteIndex)) : destinationTile;
        }
        if (currentStepOriginTile.length() == 0) {
            currentStepOriginTile = routeTiles.size() > currentRouteIndex ? KOMEConquestTile.normalizeId(routeTiles.get(currentRouteIndex)) : originTile;
        }
        if (currentStepDestinationTile.length() == 0) {
            currentStepDestinationTile = routeTiles.size() > nextRouteIndex ? KOMEConquestTile.normalizeId(routeTiles.get(nextRouteIndex)) : destinationTile;
        }
        if (stepDepartureMillis <= 0L) {
            stepDepartureMillis = departureMillis;
        }
        if (stepArrivalMillis <= 0L) {
            stepArrivalMillis = arrivalMillis;
        }
        if (totalDepartureMillis <= 0L) {
            totalDepartureMillis = departureMillis;
        }
        if (finalArrivalMillis <= 0L) {
            finalArrivalMillis = arrivalMillis;
        }
        if (lastStepMillis <= 0L) {
            lastStepMillis = stepDepartureMillis;
        }
        if (nextStepAvailableMillis <= 0L) {
            nextStepAvailableMillis = nextStepDepartureMillis;
        }
    }
}
