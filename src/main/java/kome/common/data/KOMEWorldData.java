package kome.common.data;

import cpw.mods.fml.common.FMLCommonHandler;
import kome.common.KOMEReflection;
import kome.common.network.KOMEPacketHandler;
import kome.common.network.KOMEPacketConquestData;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.world.map.LOTRWaypoint;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.storage.MapStorage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class KOMEWorldData extends WorldSavedData {
    private static final String DATA_NAME = "KOME_ServerRules";
    private static final String AUTO_WAYPOINT_RALLY_SOURCE = "Auto LOTR waypoint";
    private static final double AUTO_RALLY_REFRESH_DISTANCE_SQ = 16.0D;
    public static final int MILITARY_PASSAGE_TIER = 2;

    public final Map<UUID, KOMEPlayerPopulation> populations = new HashMap<>();
    public final Map<UUID, KOMEPlayerProgression> progressions = new HashMap<>();
    public final Map<UUID, KOMEHiredUnitRecord> hiredUnits = new HashMap<>();
    public final Map<String, KOMEConquestTile> conquestTiles = new HashMap<>();
    public final Map<String, KOMETilePopulation> tilePopulations = new HashMap<>();
    public final Map<String, KOMEPlayerTilePopulationAllocation> populationAllocations = new HashMap<>();
    public final Map<String, String> activeRecruitmentTiles = new HashMap<>();
    public final Map<String, KOMETileWaypoint> tileWaypoints = new HashMap<>();
    public final Map<String, KOMETileWaypointLink> tileWaypointLinksByTileId = new HashMap<>();
    public final Map<String, KOMEConquestRouteEdge> routeEdges = new HashMap<>();
    public final Map<String, KOMEAlliance> alliances = new HashMap<>();
    public final Map<String, KOMEArmyMovementOrder> armyMovements = new HashMap<>();
    public final Map<String, KOMEArmyCompany> armyCompanies = new HashMap<>();
    public final Map<String, KOMEMovementHistoryRecord> movementHistory = new HashMap<>();
    public final Map<UUID, String> playerNames = new HashMap<>();
    private final Set<UUID> adminUnitMapMarkerOptOuts = new HashSet<UUID>();
    private final Map<String, UUID> kingsByFaction = new HashMap<>();
    private final Map<String, String> kingNamesByFaction = new HashMap<>();
    private boolean progressionEnabled = true;
    public int movementSecondsPerTileOverride;
    public int movementTotalSecondsOverride;
    public int movementStepDelaySeconds = 5;
    public String movementDailyResetTime = "20:00";
    public String movementDailyResetTimezone = "America/Chicago";
    public static final int MAX_MOVEMENT_HISTORY_PER_FACTION = 250;
    private boolean conquestDefaultsInitialized;

    public KOMEWorldData() {
        super(DATA_NAME);
    }

    public KOMEWorldData(String name) {
        super(name);
    }

    public static KOMEWorldData get(World world) {
        if (KOMEReflection.isRemote(world)) {
            return KOMEClientData.INSTANCE;
        }
        MapStorage storage = KOMEReflection.getMapStorage(world);
        KOMEWorldData data = (KOMEWorldData) storage.loadData(KOMEWorldData.class, DATA_NAME);
        if (data == null) {
            data = new KOMEWorldData();
            storage.setData(DATA_NAME, data);
        }
        return data;
    }

    public KOMEPlayerPopulation getPopulation(UUID player) {
        KOMEPlayerPopulation pop = populations.get(player);
        if (pop == null) {
            pop = new KOMEPlayerPopulation();
            populations.put(player, pop);
        }
        return pop;
    }

    public KOMEPlayerProgression getProgression(UUID player) {
        KOMEPlayerProgression progression = progressions.get(player);
        if (progression == null) {
            progression = new KOMEPlayerProgression();
            progressions.put(player, progression);
        }
        return progression;
    }

    public KOMEMovementHistoryRecord getOrCreateMovementHistory(KOMEArmyMovementOrder order) {
        if (order == null || order.id == null || order.id.length() == 0) {
            return null;
        }
        KOMEMovementHistoryRecord record = movementHistory.get(order.id);
        if (record == null) {
            record = new KOMEMovementHistoryRecord();
            record.historyId = order.id;
            record.movementOrderId = order.id;
            movementHistory.put(order.id, record);
        }
        return record;
    }

    public void recordMovementStarted(KOMEArmyMovementOrder order) {
        updateMovementHistory(order, KOMEMovementHistoryRecord.ACTIVE);
    }

    public void updateMovementHistory(KOMEArmyMovementOrder order, String status) {
        KOMEMovementHistoryRecord record = getOrCreateMovementHistory(order);
        if (record == null) {
            return;
        }
        record.updateFromOrder(order, status);
        captureRouteSpecialEdges(record);
        pruneMovementHistoryForFaction(record.faction);
        markDirty();
    }

    public void markMovementHistoryStopped(KOMEArmyMovementOrder order, UUID stoppedByUuid, String stoppedByName, long nowMillis) {
        KOMEMovementHistoryRecord record = getOrCreateMovementHistory(order);
        if (record == null) {
            return;
        }
        record.updateFromOrder(order, KOMEMovementHistoryRecord.STOPPED);
        record.stoppedAtMillis = nowMillis;
        record.stoppedByUuid = stoppedByUuid;
        record.stoppedByName = stoppedByName == null ? "" : stoppedByName;
        captureRouteSpecialEdges(record);
        markDirty();
    }

    public int clearCompletedMovementHistory(String factionKey) {
        String faction = normalizeFactionKey(factionKey);
        List<String> remove = new ArrayList<String>();
        for (Map.Entry<String, KOMEMovementHistoryRecord> entry : movementHistory.entrySet()) {
            KOMEMovementHistoryRecord record = entry.getValue();
            if (record == null || record.isActive()) {
                continue;
            }
            if (faction.length() == 0 || faction.equals(normalizeFactionKey(record.faction))) {
                remove.add(entry.getKey());
            }
        }
        for (String key : remove) {
            movementHistory.remove(key);
        }
        if (!remove.isEmpty()) {
            markDirty();
        }
        return remove.size();
    }

    private void captureRouteSpecialEdges(KOMEMovementHistoryRecord record) {
        if (record == null || record.routeTiles.size() < 2) {
            return;
        }
        record.usedBridgeNames.clear();
        record.usedPassageNames.clear();
        for (int i = 0; i < record.routeTiles.size() - 1; i++) {
            KOMEConquestRouteEdge edge = routeEdges.get(KOMEConquestRouteEdge.key(record.routeTiles.get(i), record.routeTiles.get(i + 1)));
            if (edge == null || !edge.isSpecialPassage()) {
                continue;
            }
            String name = edge.name == null || edge.name.length() == 0 ? KOMEConquestRouteEdge.displayEdgeType(edge.edgeType)
                + " " + edge.fromTile + " -> " + edge.toTile : edge.name;
            if (KOMEConquestRouteEdge.BRIDGE.equals(edge.edgeType) && !record.usedBridgeNames.contains(name)) {
                record.usedBridgeNames.add(name);
            } else if (KOMEConquestRouteEdge.MOUNTAIN_PASS.equals(edge.edgeType) && !record.usedPassageNames.contains(name)) {
                record.usedPassageNames.add(name);
            }
        }
    }

    private void pruneMovementHistoryForFaction(String factionKey) {
        String faction = normalizeFactionKey(factionKey);
        if (faction.length() == 0) {
            return;
        }
        List<KOMEMovementHistoryRecord> completed = new ArrayList<KOMEMovementHistoryRecord>();
        for (KOMEMovementHistoryRecord record : movementHistory.values()) {
            if (record != null && !record.isActive() && faction.equals(normalizeFactionKey(record.faction))) {
                completed.add(record);
            }
        }
        if (completed.size() <= MAX_MOVEMENT_HISTORY_PER_FACTION) {
            return;
        }
        Collections.sort(completed, new java.util.Comparator<KOMEMovementHistoryRecord>() {
            @Override
            public int compare(KOMEMovementHistoryRecord first, KOMEMovementHistoryRecord second) {
                long left = latestMovementHistoryTime(first);
                long right = latestMovementHistoryTime(second);
                return left < right ? -1 : left == right ? 0 : 1;
            }
        });
        int removeCount = completed.size() - MAX_MOVEMENT_HISTORY_PER_FACTION;
        for (int i = 0; i < removeCount; i++) {
            movementHistory.remove(completed.get(i).historyId);
        }
    }

    private static long latestMovementHistoryTime(KOMEMovementHistoryRecord record) {
        if (record == null) {
            return 0L;
        }
        return record.getLatestActivityMillis();
    }

    public KOMEConquestTile getConquestTile(String tileId) {
        String normalized = KOMEConquestTile.normalizeId(tileId);
        KOMEConquestTile tile = conquestTiles.get(normalized);
        if (tile == null) {
            tile = new KOMEConquestTile(normalized);
            conquestTiles.put(normalized, tile);
        }
        ensureDefaultArrivalPoint(tile);
        return tile;
    }

    public KOMETileWaypoint getTileWaypoint(String tileId, String type) {
        return tileWaypoints.get(tileWaypointKey(tileId, type));
    }

    public KOMETileWaypoint getOrCreateTileWaypoint(String tileId, String type) {
        String key = tileWaypointKey(tileId, type);
        KOMETileWaypoint waypoint = tileWaypoints.get(key);
        if (waypoint == null) {
            waypoint = new KOMETileWaypoint(tileId, type);
            tileWaypoints.put(key, waypoint);
        }
        return waypoint;
    }

    public void setTileWaypoint(String tileId, String type, int dimensionId, double x, double y, double z, String createdBy, boolean manualOverride) {
        KOMETileWaypoint waypoint = getOrCreateTileWaypoint(tileId, type);
        waypoint.set(dimensionId, x, y, z, createdBy, manualOverride);
        markDirty();
    }

    public boolean clearTileWaypoint(String tileId, String type) {
        boolean removed = tileWaypoints.remove(tileWaypointKey(tileId, type)) != null;
        if (removed) {
            markDirty();
        }
        return removed;
    }

    public List<KOMETileWaypoint> getTileWaypoints(String tileId) {
        String normalizedTile = KOMEConquestTile.normalizeId(tileId);
        List<KOMETileWaypoint> waypoints = new ArrayList<KOMETileWaypoint>();
        for (KOMETileWaypoint waypoint : tileWaypoints.values()) {
            if (waypoint != null && normalizedTile.equals(KOMEConquestTile.normalizeId(waypoint.tileId))) {
                waypoints.add(waypoint);
            }
        }
        Collections.sort(waypoints, new java.util.Comparator<KOMETileWaypoint>() {
            @Override
            public int compare(KOMETileWaypoint first, KOMETileWaypoint second) {
                return KOMETileWaypoint.normalizeType(first.type).compareTo(KOMETileWaypoint.normalizeType(second.type));
            }
        });
        return waypoints;
    }

    public KOMETileWaypointLink getTileWaypointLink(String tileId) {
        return tileWaypointLinksByTileId.get(KOMEConquestTile.normalizeId(tileId));
    }

    public KOMETileWaypointLink linkTileWaypoint(String tileId, LOTRWaypoint waypoint, UUID linkedByUuid, String linkedByName) {
        return linkTileWaypoint(tileId, waypoint, linkedByUuid, linkedByName, KOMETileWaypointLink.SOURCE_MANUAL, true);
    }

    public KOMETileWaypointLink linkTileWaypoint(String tileId, LOTRWaypoint waypoint, UUID linkedByUuid, String linkedByName, String source, boolean manualOverride) {
        if (waypoint == null) {
            return null;
        }
        String normalizedTile = KOMEConquestTile.normalizeId(tileId);
        KOMETileWaypointLink link = tileWaypointLinksByTileId.get(normalizedTile);
        if (link == null) {
            link = new KOMETileWaypointLink(normalizedTile, waypoint, linkedByUuid, linkedByName, source, manualOverride);
            tileWaypointLinksByTileId.put(normalizedTile, link);
        } else {
            link.tileId = normalizedTile;
            link.linkedByUuid = linkedByUuid;
            link.linkedByName = linkedByName == null ? "" : linkedByName;
            link.source = KOMETileWaypointLink.normalizeSource(source);
            link.manualOverride = manualOverride;
            if (link.linkedAtMillis <= 0L) {
                link.linkedAtMillis = System.currentTimeMillis();
            }
            link.updateWaypoint(waypoint);
        }
        markDirty();
        return link;
    }

    public boolean unlinkTileWaypoint(String tileId) {
        boolean removed = tileWaypointLinksByTileId.remove(KOMEConquestTile.normalizeId(tileId)) != null;
        if (removed) {
            markDirty();
        }
        return removed;
    }

    public void ensureAutomaticTileWaypointLinks() {
        if (this instanceof KOMEClientData) {
            return;
        }
        Map<String, KOMETileWaypointLink> preservedManual = new HashMap<String, KOMETileWaypointLink>();
        for (Map.Entry<String, KOMETileWaypointLink> entry : tileWaypointLinksByTileId.entrySet()) {
            KOMETileWaypointLink link = entry.getValue();
            if (link != null && link.manualOverride && !link.isAutomatic()) {
                preservedManual.put(entry.getKey(), link);
            }
        }

        Map<String, WaypointCandidate> candidatesByTile = new HashMap<String, WaypointCandidate>();
        Set<String> usedWaypointKeys = new HashSet<String>();
        for (KOMETileWaypointLink manual : preservedManual.values()) {
            if (manual != null && manual.lotrWaypointKey != null && manual.lotrWaypointKey.length() > 0) {
                usedWaypointKeys.add(manual.lotrWaypointKey);
            }
        }
        for (LOTRWaypoint waypoint : LOTRWaypoint.values()) {
            if (waypoint == null || waypoint.isHidden() || usedWaypointKeys.contains(waypoint.getCodeName())) {
                continue;
            }
            String tileId = KOMEConquestTileDefaults.getTileIdAtMapPosition(waypoint.getX(), waypoint.getY());
            if (tileId.length() == 0 || preservedManual.containsKey(tileId)) {
                continue;
            }
            KOMEConquestTileDefaults.TileCenter center = KOMEConquestTileDefaults.getTileCenter(tileId);
            double distance = 0.0D;
            if (center != null) {
                double dx = waypoint.getXCoord() - center.x;
                double dz = waypoint.getZCoord() - center.z;
                distance = dx * dx + dz * dz;
            }
            WaypointCandidate existing = candidatesByTile.get(tileId);
            if (existing == null || distance < existing.distanceSq) {
                candidatesByTile.put(tileId, new WaypointCandidate(waypoint, distance));
            }
        }

        Map<String, KOMETileWaypointLink> desiredLinks = new HashMap<String, KOMETileWaypointLink>();
        desiredLinks.putAll(preservedManual);
        for (Map.Entry<String, WaypointCandidate> entry : candidatesByTile.entrySet()) {
            if (desiredLinks.containsKey(entry.getKey())) {
                continue;
            }
            KOMETileWaypointLink link = new KOMETileWaypointLink(entry.getKey(), entry.getValue().waypoint, null, "Automatic LOTR waypoint",
                KOMETileWaypointLink.SOURCE_AUTO_DEFAULT, false);
            desiredLinks.put(entry.getKey(), link);
        }
        boolean changed = tileWaypointLinksDiffer(tileWaypointLinksByTileId, desiredLinks);
        if (changed) {
            tileWaypointLinksByTileId.clear();
            tileWaypointLinksByTileId.putAll(desiredLinks);
            applyWaypointDefaults(!conquestDefaultsInitialized);
            markDirty();
        } else if (applyWaypointDefaults(!conquestDefaultsInitialized)) {
            markDirty();
        }
    }

    public boolean applyWaypointDefaults(boolean initializeOwnership) {
        boolean changed = false;
        KOMEWaypointDefaults.ensureLoaded();
        KOMETileOwnershipDefaults.ensureLoaded();
        Map<String, KOMEWaypointDefaults.Entry> defaultsByTile = resolveWaypointDefaultAssignmentsByTile();
        Set<String> tilesToApply = new HashSet<String>();
        tilesToApply.addAll(KOMEConquestTileDefaults.getKnownTileIds());
        tilesToApply.addAll(conquestTiles.keySet());
        tilesToApply.addAll(tileWaypointLinksByTileId.keySet());
        for (String tileId : tilesToApply) {
            String normalizedTile = KOMEConquestTile.normalizeId(tileId);
            if (normalizedTile.length() == 0 || KOMEConquestTileDefaults.isRetiredTile(normalizedTile)) {
                continue;
            }
            KOMEConquestTile tile = getConquestTile(normalizedTile);
            KOMEWaypointDefaults.Entry defaults = defaultsByTile.get(normalizedTile);
            int level = defaults == null ? 0 : defaults.waypointLevel;
            String defaultFaction = defaults == null ? "" : defaults.defaultRulingFaction;
            String region = defaults == null ? "" : defaults.mapRegion;
            if (tile.waypointLevel != level
                    || !sameString(tile.defaultRulingFaction, defaultFaction)
                    || !sameString(tile.mapRegion, region)) {
                tile.setWaypointDefaults(level, defaultFaction, region);
                changed = true;
            }
            if (initializeOwnership && !tile.isClaimed()) {
                if (defaultFaction.length() > 0) {
                    tile.resetOwnershipToDefault(0L);
                    changed = true;
                } else if (tile.currentRulingFaction().length() > 0) {
                    tile.clearOwnershipOnly();
                    changed = true;
                }
            }
        }
        if (initializeOwnership && !conquestDefaultsInitialized) {
            conquestDefaultsInitialized = true;
            changed = true;
        }
        return changed;
    }

    private Map<String, KOMEWaypointDefaults.Entry> resolveWaypointDefaultAssignmentsByTile() {
        Map<String, KOMEWaypointDefaults.Entry> defaultsByTile = new HashMap<String, KOMEWaypointDefaults.Entry>();
        for (KOMETileWaypointLink link : tileWaypointLinksByTileId.values()) {
            if (link == null || link.tileId == null || link.tileId.length() == 0) {
                continue;
            }
            String tileId = KOMEConquestTile.normalizeId(link.tileId);
            if (tileId.length() == 0 || KOMEConquestTileDefaults.isRetiredTile(tileId)) {
                continue;
            }
            KOMEWaypointDefaults.Entry defaults = KOMEWaypointDefaults.forLink(link);
            if (defaults == null) {
                continue;
            }
            defaultsByTile.put(tileId, defaults);
        }
        // Tile ownership defaults are the final curated conquest defaults. They
        // intentionally override older waypoint-level defaults for the same tile.
        for (KOMETileOwnershipDefaults.Entry tileDefault : KOMETileOwnershipDefaults.entries()) {
            if (tileDefault == null || tileDefault.tileId.length() == 0
                    || KOMEConquestTileDefaults.isRetiredTile(tileDefault.tileId)) {
                continue;
            }
            defaultsByTile.put(tileDefault.tileId, tileDefault.asWaypointDefaultsEntry());
        }
        return defaultsByTile;
    }

    public int resetConquestOwnershipToDefaults(long worldTime) {
        ensureAutomaticTileWaypointLinks();
        applyWaypointDefaults(false);
        Set<String> resetTiles = new HashSet<String>();
        resetTiles.addAll(KOMEConquestTileDefaults.getKnownTileIds());
        resetTiles.addAll(conquestTiles.keySet());
        resetTiles.addAll(tileWaypointLinksByTileId.keySet());
        int changed = 0;
        for (String tileId : resetTiles) {
            KOMEConquestTile tile = getConquestTile(tileId);
            String before = tile.currentRulingFaction();
            String defaultFaction = KOMEAlliance.normalizeFactionKey(tile.defaultRulingFaction);
            if (defaultFaction.length() > 0) {
                tile.resetOwnershipToDefault(worldTime);
            } else {
                tile.clearOwnershipOnly();
            }
            if (!sameString(before, tile.currentRulingFaction())) {
                changed++;
            }
        }
        preserveResetTilePopulationValue(resetTiles);
        conquestDefaultsInitialized = true;
        markDirty();
        syncConquestTiles();
        return changed;
    }

    private void preserveResetTilePopulationValue(Set<String> resetTiles) {
        if (resetTiles == null || resetTiles.isEmpty()) {
            return;
        }
        Map<String, KOMETilePopulation> remapped = new HashMap<String, KOMETilePopulation>();
        Set<String> normalizedResetTiles = new HashSet<String>();
        for (String tileId : resetTiles) {
            normalizedResetTiles.add(KOMEConquestTile.normalizeId(tileId));
        }
        for (Map.Entry<String, KOMETilePopulation> entry : tilePopulations.entrySet()) {
            KOMETilePopulation population = entry.getValue();
            if (population == null) {
                continue;
            }
            String tileId = KOMEConquestTile.normalizeId(population.tileId);
            KOMEConquestTile tile = conquestTiles.get(tileId);
            String resetOwner = tile == null ? "" : KOMEAlliance.normalizeFactionKey(tile.currentRulingFaction());
            String source = KOMEAlliance.normalizeFactionKey(population.sourceFaction);
            if (normalizedResetTiles.contains(tileId) && resetOwner.length() > 0) {
                source = resetOwner;
            }
            String key = tilePopulationKey(tileId, source);
            KOMETilePopulation target = remapped.get(key);
            if (target == null) {
                target = new KOMETilePopulation(tileId, source);
                remapped.put(key, target);
            }
            target.offensiveTotal += Math.max(0, population.offensiveTotal);
            target.offensiveUsed += Math.max(0, population.offensiveUsed);
            target.defensiveTotal += Math.max(0, population.defensiveTotal);
            target.defensiveUsed += Math.max(0, population.defensiveUsed);
            target.farmhandTotal += Math.max(0, population.farmhandTotal);
            target.farmhandUsed += Math.max(0, population.farmhandUsed);
            target.offensiveUsed = Math.min(target.offensiveUsed, target.offensiveTotal);
            target.defensiveUsed = Math.min(target.defensiveUsed, target.defensiveTotal);
            target.farmhandUsed = Math.min(target.farmhandUsed, target.farmhandTotal);
        }
        tilePopulations.clear();
        tilePopulations.putAll(remapped);
    }

    public List<String> buildConquestBalanceReportLines() {
        ensureAutomaticTileWaypointLinks();
        applyWaypointDefaults(false);
        Map<String, BalanceStats> statsByFaction = new HashMap<String, BalanceStats>();
        int totalTiles = 0;
        int ownedTiles = 0;
        int unclaimedTiles = 0;
        List<String> tileIds = new ArrayList<String>(KOMEConquestTileDefaults.getKnownTileIds());
        Collections.sort(tileIds);
        for (String tileId : tileIds) {
            KOMEConquestTile tile = getConquestTile(tileId);
            String faction = KOMEAlliance.normalizeFactionKey(tile.defaultRulingFaction);
            totalTiles++;
            if (faction.length() == 0) {
                unclaimedTiles++;
                continue;
            }
            ownedTiles++;
            BalanceStats stats = getBalanceStats(statsByFaction, faction);
            stats.tileCount++;
            int level = tile.waypointLevel >= 1 && tile.waypointLevel <= 3 ? tile.waypointLevel : 0;
            if (level > 0) {
                stats.waypointTiles++;
                if (level == 1) {
                    stats.level1++;
                } else if (level == 2) {
                    stats.level2++;
                } else {
                    stats.level3++;
                }
            }
            stats.weightedScore += balanceWeightForTile(tileId, tile);
        }
        for (String tileId : tileIds) {
            KOMEConquestTile tile = getConquestTile(tileId);
            String faction = KOMEAlliance.normalizeFactionKey(tile.defaultRulingFaction);
            if (faction.length() == 0) {
                continue;
            }
            BalanceStats stats = getBalanceStats(statsByFaction, faction);
            for (String adjacentTileId : KOMEConquestTileDefaults.getAdjacentTiles(tileId)) {
                KOMEConquestTile adjacentTile = conquestTiles.get(KOMEConquestTile.normalizeId(adjacentTileId));
                String adjacentFaction = adjacentTile == null ? "" : KOMEAlliance.normalizeFactionKey(adjacentTile.defaultRulingFaction);
                if (adjacentFaction.length() == 0) {
                    stats.borderTiles.add(tileId);
                    stats.unclaimedNeighborTiles.add(KOMEConquestTile.normalizeId(adjacentTileId));
                } else if (!adjacentFaction.equals(faction)) {
                    stats.borderTiles.add(tileId);
                    stats.adjacentEnemyFactions.add(adjacentFaction);
                }
            }
        }
        List<BalanceStats> ordered = new ArrayList<BalanceStats>(statsByFaction.values());
        Collections.sort(ordered, new java.util.Comparator<BalanceStats>() {
            @Override
            public int compare(BalanceStats first, BalanceStats second) {
                if (first.weightedScore != second.weightedScore) {
                    return second.weightedScore - first.weightedScore;
                }
                return first.faction.compareTo(second.faction);
            }
        });
        List<String> lines = new ArrayList<String>();
        lines.add("Conquest default balance: " + ownedTiles + " owned, " + unclaimedTiles + " unclaimed, " + totalTiles + " total tile(s).");
        lines.add("Faction | tiles | waypoint L1/L2/L3 | score | border | enemy factions | unclaimed neighbors");
        for (BalanceStats stats : ordered) {
            lines.add(stats.faction + " | tiles " + stats.tileCount + " | wp " + stats.waypointTiles
                + " (" + stats.level1 + "/" + stats.level2 + "/" + stats.level3 + ") | score "
                + stats.weightedScore + " | border " + stats.borderTiles.size()
                + " | enemy factions " + stats.adjacentEnemyFactions.size()
                + " | unclaimed neighbors " + stats.unclaimedNeighborTiles.size());
        }
        lines.add("Tile default rows: " + KOMETileOwnershipDefaults.loadedEntryCount() + " loaded, "
            + KOMETileOwnershipDefaults.ownedEntryCount() + " owned, "
            + KOMETileOwnershipDefaults.unclaimedEntryCount() + " unclaimed.");
        return lines;
    }

    private static BalanceStats getBalanceStats(Map<String, BalanceStats> statsByFaction, String faction) {
        BalanceStats stats = statsByFaction.get(faction);
        if (stats == null) {
            stats = new BalanceStats(faction);
            statsByFaction.put(faction, stats);
        }
        return stats;
    }

    private static int balanceWeightForTile(String tileId, KOMEConquestTile tile) {
        KOMETileOwnershipDefaults.Entry explicit = KOMETileOwnershipDefaults.forTile(tileId);
        if (explicit != null && explicit.balanceWeight > 0) {
            return explicit.balanceWeight;
        }
        if (tile == null || KOMEAlliance.normalizeFactionKey(tile.defaultRulingFaction).length() == 0) {
            return 0;
        }
        if (tile.waypointLevel == 1) {
            return 2;
        }
        if (tile.waypointLevel == 2) {
            return 4;
        }
        if (tile.waypointLevel == 3) {
            return 6;
        }
        return 1;
    }

    private static boolean tileWaypointLinksDiffer(Map<String, KOMETileWaypointLink> current, Map<String, KOMETileWaypointLink> desired) {
        if (current.size() != desired.size()) {
            return true;
        }
        for (Map.Entry<String, KOMETileWaypointLink> entry : desired.entrySet()) {
            KOMETileWaypointLink left = current.get(entry.getKey());
            KOMETileWaypointLink right = entry.getValue();
            if (left == null || right == null) {
                return true;
            }
            if (!sameString(left.lotrWaypointKey, right.lotrWaypointKey)
                    || !sameString(KOMETileWaypointLink.normalizeSource(left.source), KOMETileWaypointLink.normalizeSource(right.source))
                    || left.manualOverride != right.manualOverride) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameString(String left, String right) {
        return (left == null ? "" : left).equals(right == null ? "" : right);
    }

    private static class BalanceStats {
        private final String faction;
        private int tileCount;
        private int waypointTiles;
        private int level1;
        private int level2;
        private int level3;
        private int weightedScore;
        private final Set<String> borderTiles = new HashSet<String>();
        private final Set<String> adjacentEnemyFactions = new HashSet<String>();
        private final Set<String> unclaimedNeighborTiles = new HashSet<String>();

        private BalanceStats(String faction) {
            this.faction = faction;
        }
    }

    public void ensureRallyWaypointFromLegacyAnchor(KOMEConquestTile tile) {
        if (tile == null || !tile.hasAnchor || getTileWaypoint(tile.id, KOMETileWaypoint.RALLY) != null) {
            return;
        }
        setTileWaypoint(tile.id, KOMETileWaypoint.RALLY, tile.anchorDimension, tile.anchorX, tile.anchorY, tile.anchorZ, "Legacy anchor", false);
    }

    public void ensureDefaultArrivalPoint(KOMEConquestTile tile) {
        if (tile == null || !KOMEConquestTile.isCanonicalTileId(tile.id)) {
            return;
        }
        KOMEConquestTileDefaults.TileCenter center = KOMEConquestTileDefaults.getTileCenter(tile.id);
        if (center == null) {
            return;
        }
        if (ensureDefaultArrivalPointFromLinkedWaypoint(tile, center)) {
            return;
        }
        KOMETileWaypoint existing = getTileWaypoint(tile.id, KOMETileWaypoint.RALLY);
        if (existing != null) {
            if (existing.manualOverride) {
                return;
            }
            double dx = existing.x - center.x;
            double dz = existing.z - center.z;
            if (dx * dx + dz * dz < 1048576.0D) {
                return;
            }
        }
        tile.setAnchor(center.dimensionId, center.x, center.y, center.z);
        setTileWaypoint(tile.id, KOMETileWaypoint.RALLY, center.dimensionId, center.x, center.y, center.z, "Auto tile center", false);
    }

    private boolean ensureDefaultArrivalPointFromLinkedWaypoint(KOMEConquestTile tile, KOMEConquestTileDefaults.TileCenter center) {
        KOMETileWaypoint existing = getTileWaypoint(tile.id, KOMETileWaypoint.RALLY);
        if (existing != null && existing.manualOverride) {
            syncTileAnchor(tile, existing.dimensionId, existing.x, existing.y, existing.z);
            return true;
        }
        KOMETileWaypointLink link = getTileWaypointLink(tile.id);
        if (link == null || link.lotrWaypointKey == null || link.lotrWaypointKey.length() == 0) {
            return false;
        }
        double x = link.waypointWorldX;
        double z = link.waypointWorldZ;
        int dimensionId = link.dimensionId == 0 ? center.dimensionId : link.dimensionId;
        LOTRWaypoint waypoint = link.resolveWaypoint();
        if (waypoint != null) {
            x = waypoint.getXCoord();
            z = waypoint.getZCoord();
            if (dimensionId == 0) {
                dimensionId = center.dimensionId;
            }
        }
        if (Math.abs(x) < 0.001D && Math.abs(z) < 0.001D) {
            return false;
        }
        double y = center.y;
        if (existing != null && existing.dimensionId == dimensionId) {
            double dx = existing.x - x;
            double dy = existing.y - y;
            double dz = existing.z - z;
            boolean sameAutomaticSource = AUTO_WAYPOINT_RALLY_SOURCE.equals(existing.createdBy);
            if (sameAutomaticSource && dx * dx + dy * dy + dz * dz < AUTO_RALLY_REFRESH_DISTANCE_SQ) {
                syncTileAnchor(tile, dimensionId, x, y, z);
                return true;
            }
        }
        tile.setAnchor(dimensionId, x, y, z);
        setTileWaypoint(tile.id, KOMETileWaypoint.RALLY, dimensionId, x, y, z, AUTO_WAYPOINT_RALLY_SOURCE, false);
        return true;
    }

    private void syncTileAnchor(KOMEConquestTile tile, int dimensionId, double x, double y, double z) {
        if (tile == null || (tile.hasAnchor && tile.anchorDimension == dimensionId
                && Math.abs(tile.anchorX - x) < 0.001D
                && Math.abs(tile.anchorY - y) < 0.001D
                && Math.abs(tile.anchorZ - z) < 0.001D)) {
            return;
        }
        tile.setAnchor(dimensionId, x, y, z);
        markDirty();
    }

    public KOMETilePopulation getTilePopulation(String tileId) {
        KOMEConquestTile tile = getConquestTile(tileId);
        return getOrCreateTilePopulationPool(tileId, tile.currentRulingFaction());
    }

    public List<KOMETilePopulation> getTilePopulationPools(String tileId) {
        String normalizedTile = KOMEConquestTile.normalizeId(tileId);
        List<KOMETilePopulation> pools = new ArrayList<KOMETilePopulation>();
        for (KOMETilePopulation population : tilePopulations.values()) {
            if (population != null && normalizedTile.equals(KOMEConquestTile.normalizeId(population.tileId))) {
                pools.add(population);
            }
        }
        Collections.sort(pools, new java.util.Comparator<KOMETilePopulation>() {
            @Override
            public int compare(KOMETilePopulation first, KOMETilePopulation second) {
                return KOMEAlliance.normalizeFactionKey(first.sourceFaction).compareTo(KOMEAlliance.normalizeFactionKey(second.sourceFaction));
            }
        });
        return pools;
    }

    public KOMETilePopulation getTilePopulationPool(String tileId, String sourceFaction) {
        return tilePopulations.get(tilePopulationKey(tileId, sourceFaction));
    }

    public KOMETilePopulation getOrCreateTilePopulationPool(String tileId, String sourceFaction) {
        String normalizedTile = KOMEConquestTile.normalizeId(tileId);
        String normalizedFaction = KOMEAlliance.normalizeFactionKey(sourceFaction);
        String key = tilePopulationKey(normalizedTile, normalizedFaction);
        KOMETilePopulation population = tilePopulations.get(key);
        if (population == null) {
            population = new KOMETilePopulation(normalizedTile, normalizedFaction);
            tilePopulations.put(key, population);
        }
        return population;
    }

    public int getEffectiveUsablePopulation(String tileId, String controllingFaction, KOMEPopulationType type) {
        int total = 0;
        for (KOMETilePopulation population : getTilePopulationPools(tileId)) {
            total += population.getEffectiveTotal(type, controllingFaction);
        }
        return total;
    }

    public int getEffectiveUsedPopulation(String tileId, String controllingFaction, KOMEPopulationType type) {
        int used = 0;
        for (KOMETilePopulation population : getTilePopulationPools(tileId)) {
            used += Math.min(population.getUsed(type), population.getEffectiveTotal(type, controllingFaction));
        }
        return used;
    }

    public int getEffectiveAvailablePopulation(String tileId, String controllingFaction, KOMEPopulationType type) {
        int available = 0;
        for (KOMETilePopulation population : getTilePopulationPools(tileId)) {
            available += population.getEffectiveAvailable(type, controllingFaction);
        }
        return available;
    }

    public int getEffectiveUsableOffensive(String tileId, String controllingFaction) {
        return getEffectiveUsablePopulation(tileId, controllingFaction, KOMEPopulationType.OFFENSIVE);
    }

    public int getEffectiveUsableDefensive(String tileId, String controllingFaction) {
        return getEffectiveUsablePopulation(tileId, controllingFaction, KOMEPopulationType.DEFENSIVE);
    }

    public int getEffectiveAvailableOffensive(String tileId, String controllingFaction) {
        return getEffectiveAvailablePopulation(tileId, controllingFaction, KOMEPopulationType.OFFENSIVE);
    }

    public int getEffectiveAvailableDefensive(String tileId, String controllingFaction) {
        return getEffectiveAvailablePopulation(tileId, controllingFaction, KOMEPopulationType.DEFENSIVE);
    }

    public EffectivePopulationSummary getFactionEffectivePopulationSummary(String factionKey) {
        EffectivePopulationSummary summary = new EffectivePopulationSummary();
        summary.offensiveTotal = getFactionTilePopulationTotal(factionKey, KOMEPopulationType.OFFENSIVE);
        summary.offensiveUsed = getFactionTilePopulationUsed(factionKey, KOMEPopulationType.OFFENSIVE);
        summary.defensiveTotal = getFactionTilePopulationTotal(factionKey, KOMEPopulationType.DEFENSIVE);
        summary.defensiveUsed = getFactionTilePopulationUsed(factionKey, KOMEPopulationType.DEFENSIVE);
        return summary;
    }

    public int getFactionTilePopulationTotal(String factionKey, KOMEPopulationType type) {
        String key = KOMEAlliance.normalizeFactionKey(factionKey);
        int total = 0;
        for (KOMEConquestTile tile : conquestTiles.values()) {
            if (tile != null && tile.isClaimed() && key.equals(KOMEAlliance.normalizeFactionKey(tile.currentRulingFaction()))) {
                total += getEffectiveUsablePopulation(tile.id, key, type);
            }
        }
        return total;
    }

    public int getFactionTilePopulationUsed(String factionKey, KOMEPopulationType type) {
        String key = KOMEAlliance.normalizeFactionKey(factionKey);
        int used = 0;
        for (KOMEConquestTile tile : conquestTiles.values()) {
            if (tile != null && tile.isClaimed() && key.equals(KOMEAlliance.normalizeFactionKey(tile.currentRulingFaction()))) {
                used += getEffectiveUsedPopulation(tile.id, key, type);
            }
        }
        return used;
    }

    public int getFactionControlledTileCount(String factionKey) {
        String key = KOMEAlliance.normalizeFactionKey(factionKey);
        int count = 0;
        for (KOMEConquestTile tile : conquestTiles.values()) {
            if (tile != null && tile.isClaimed() && key.equals(KOMEAlliance.normalizeFactionKey(tile.currentRulingFaction()))) {
                count++;
            }
        }
        return count;
    }

    public KOMETilePopulation findFactionTileWithPopulation(String factionKey, KOMEPopulationType type, int amount) {
        String key = KOMEAlliance.normalizeFactionKey(factionKey);
        List<String> tileIds = new ArrayList<String>(conquestTiles.keySet());
        Collections.sort(tileIds);
        for (String tileId : tileIds) {
            KOMEConquestTile tile = conquestTiles.get(tileId);
            if (tile == null || !tile.isClaimed() || !key.equals(KOMEAlliance.normalizeFactionKey(tile.currentRulingFaction()))) {
                continue;
            }
            List<KOMETilePopulation> pools = getTilePopulationPools(tileId);
            KOMETilePopulation ownPool = getTilePopulationPool(tileId, key);
            if (ownPool != null && ownPool.getEffectiveAvailable(type, key) >= amount) {
                return ownPool;
            }
            for (KOMETilePopulation population : pools) {
                if (population != ownPool && population.getEffectiveAvailable(type, key) >= amount) {
                    return population;
                }
            }
        }
        return null;
    }

    public KOMETilePopulation findPlayerAllocatedTileWithPopulation(String factionKey, UUID playerId, KOMEPopulationType type, int amount) {
        String faction = KOMEAlliance.normalizeFactionKey(factionKey);
        List<String> tileIds = new ArrayList<String>(conquestTiles.keySet());
        Collections.sort(tileIds);
        for (String tileId : tileIds) {
            KOMEConquestTile tile = conquestTiles.get(tileId);
            if (tile == null || !tile.isClaimed() || !faction.equals(KOMEAlliance.normalizeFactionKey(tile.currentRulingFaction()))) {
                continue;
            }
            KOMEPlayerTilePopulationAllocation allocation = getAllocation(tileId, faction, playerId);
            if (allocation == null || allocation.getAvailable(type) < amount) {
                continue;
            }
            KOMETilePopulation ownPool = getTilePopulationPool(tileId, faction);
            if (ownPool != null && ownPool.getEffectiveAvailable(type, faction) >= amount) {
                return ownPool;
            }
            for (KOMETilePopulation population : getTilePopulationPools(tileId)) {
                if (population != ownPool && population.getEffectiveAvailable(type, faction) >= amount) {
                    return population;
                }
            }
        }
        return null;
    }

    public KOMETilePopulation findPlayerAllocatedPopulationInTile(String tileId, String factionKey, UUID playerId, KOMEPopulationType type, int amount) {
        String tileKey = KOMEConquestTile.normalizeId(tileId);
        String faction = KOMEAlliance.normalizeFactionKey(factionKey);
        KOMEConquestTile tile = conquestTiles.get(tileKey);
        if (tile == null || !tile.isClaimed() || !faction.equals(KOMEAlliance.normalizeFactionKey(tile.currentRulingFaction()))) {
            return null;
        }
        KOMEPlayerTilePopulationAllocation allocation = getAllocation(tileKey, faction, playerId);
        if (allocation == null || allocation.getAvailable(type) < amount) {
            return null;
        }
        KOMETilePopulation ownPool = getTilePopulationPool(tileKey, faction);
        if (ownPool != null && ownPool.getEffectiveAvailable(type, faction) >= amount) {
            return ownPool;
        }
        for (KOMETilePopulation population : getTilePopulationPools(tileKey)) {
            if (population != ownPool && population.getEffectiveAvailable(type, faction) >= amount) {
                return population;
            }
        }
        return null;
    }

    public String getActiveRecruitmentTile(UUID playerId, String factionKey) {
        if (playerId == null) {
            return "";
        }
        String faction = KOMEAlliance.normalizeFactionKey(factionKey);
        String tileId = activeRecruitmentTiles.get(recruitmentTileKey(faction, playerId));
        if (!canUseRecruitmentTile(playerId, faction, tileId)) {
            return "";
        }
        return KOMEConquestTile.normalizeId(tileId);
    }

    public boolean canUseRecruitmentTile(UUID playerId, String factionKey, String tileId) {
        String faction = KOMEAlliance.normalizeFactionKey(factionKey);
        String tile = KOMEConquestTile.normalizeId(tileId);
        if (playerId == null || !isFactionControlledTile(tile, faction)) {
            return false;
        }
        KOMEPlayerTilePopulationAllocation allocation = getAllocation(tile, faction, playerId);
        boolean hasAllocation = allocation != null && (allocation.offensiveAllocated > 0 || allocation.defensiveAllocated > 0);
        return hasAllocation || getPopulation(playerId).getCombinedTotal() > 0;
    }

    public boolean setActiveRecruitmentTile(UUID playerId, String factionKey, String tileId) {
        String faction = KOMEAlliance.normalizeFactionKey(factionKey);
        String tile = KOMEConquestTile.normalizeId(tileId);
        if (!canUseRecruitmentTile(playerId, faction, tile)) {
            return false;
        }
        activeRecruitmentTiles.put(recruitmentTileKey(faction, playerId), tile);
        markDirty();
        return true;
    }

    public boolean clearActiveRecruitmentTile(UUID playerId, String factionKey) {
        if (playerId == null) {
            return false;
        }
        boolean removed = activeRecruitmentTiles.remove(recruitmentTileKey(factionKey, playerId)) != null;
        if (removed) {
            markDirty();
        }
        return removed;
    }

    public boolean isFactionControlledTile(String tileId, String factionKey) {
        String tileKey = KOMEConquestTile.normalizeId(tileId);
        String faction = KOMEAlliance.normalizeFactionKey(factionKey);
        KOMEConquestTile tile = conquestTiles.get(tileKey);
        return tile != null && tile.isClaimed() && faction.equals(KOMEAlliance.normalizeFactionKey(tile.currentRulingFaction()));
    }

    public boolean canFactionStandOnTile(String tileId, String factionKey) {
        String tileKey = KOMEConquestTile.normalizeId(tileId);
        String faction = KOMEAlliance.normalizeFactionKey(factionKey);
        KOMEConquestTile tile = conquestTiles.get(tileKey);
        return tile != null && tile.isClaimed() && canFactionUseMilitaryPassage(faction, tile.currentRulingFaction());
    }

    public KOMEPlayerTilePopulationAllocation getAllocation(String tileId, String faction, UUID playerId) {
        if (playerId == null) {
            return null;
        }
        return populationAllocations.get(populationAllocationKey(tileId, faction, playerId));
    }

    public KOMEPlayerTilePopulationAllocation getOrCreateAllocation(String tileId, String faction, UUID playerId, String playerName) {
        String key = populationAllocationKey(tileId, faction, playerId);
        KOMEPlayerTilePopulationAllocation allocation = populationAllocations.get(key);
        if (allocation == null) {
            allocation = new KOMEPlayerTilePopulationAllocation();
            allocation.tileId = KOMEConquestTile.normalizeId(tileId);
            allocation.faction = KOMEAlliance.normalizeFactionKey(faction);
            allocation.playerUuid = playerId;
            populationAllocations.put(key, allocation);
        }
        if (playerName != null && playerName.trim().length() > 0) {
            allocation.playerName = playerName;
        }
        return allocation;
    }

    public List<KOMEPlayerTilePopulationAllocation> getAllocationsForTile(String tileId, String faction) {
        String normalizedTile = KOMEConquestTile.normalizeId(tileId);
        String normalizedFaction = KOMEAlliance.normalizeFactionKey(faction);
        List<KOMEPlayerTilePopulationAllocation> result = new ArrayList<KOMEPlayerTilePopulationAllocation>();
        for (KOMEPlayerTilePopulationAllocation allocation : populationAllocations.values()) {
            if (allocation != null && normalizedTile.equals(allocation.tileId) && normalizedFaction.equals(allocation.faction)) {
                result.add(allocation);
            }
        }
        Collections.sort(result, new java.util.Comparator<KOMEPlayerTilePopulationAllocation>() {
            @Override
            public int compare(KOMEPlayerTilePopulationAllocation first, KOMEPlayerTilePopulationAllocation second) {
                return first.playerName.compareToIgnoreCase(second.playerName);
            }
        });
        return result;
    }

    public List<KOMEPlayerTilePopulationAllocation> getAllocationsForFaction(String faction) {
        String normalizedFaction = KOMEAlliance.normalizeFactionKey(faction);
        List<KOMEPlayerTilePopulationAllocation> result = new ArrayList<KOMEPlayerTilePopulationAllocation>();
        for (KOMEPlayerTilePopulationAllocation allocation : populationAllocations.values()) {
            KOMEConquestTile tile = allocation == null ? null : conquestTiles.get(allocation.tileId);
            if (allocation != null && normalizedFaction.equals(allocation.faction) && tile != null
                    && normalizedFaction.equals(KOMEAlliance.normalizeFactionKey(tile.currentRulingFaction()))) {
                result.add(allocation);
            }
        }
        return result;
    }

    public int getTotalAllocated(String tileId, String faction, KOMEPopulationType type) {
        int total = 0;
        for (KOMEPlayerTilePopulationAllocation allocation : getAllocationsForTile(tileId, faction)) {
            total += allocation.getAllocated(type);
        }
        return total;
    }

    public int getPlayerAllocatedAvailable(String tileId, String faction, UUID playerId, KOMEPopulationType type) {
        KOMEConquestTile tile = conquestTiles.get(KOMEConquestTile.normalizeId(tileId));
        if (tile == null || !KOMEAlliance.normalizeFactionKey(faction).equals(KOMEAlliance.normalizeFactionKey(tile.currentRulingFaction()))) {
            return 0;
        }
        KOMEPlayerTilePopulationAllocation allocation = getAllocation(tileId, faction, playerId);
        return allocation == null ? 0 : allocation.getAvailable(type);
    }

    public boolean allocatePopulation(String tileId, String faction, UUID playerId, String playerName, KOMEPopulationType type, int amount) {
        if (amount <= 0 || playerId == null) {
            return false;
        }
        KOMEConquestTile tile = conquestTiles.get(KOMEConquestTile.normalizeId(tileId));
        String normalizedFaction = KOMEAlliance.normalizeFactionKey(faction);
        if (tile == null || !normalizedFaction.equals(KOMEAlliance.normalizeFactionKey(tile.currentRulingFaction()))) {
            return false;
        }
        int effectiveTotal = getEffectiveUsablePopulation(tile.id, normalizedFaction, type);
        if (getTotalAllocated(tile.id, normalizedFaction, type) + amount > effectiveTotal) {
            return false;
        }
        KOMEPlayerTilePopulationAllocation allocation = getOrCreateAllocation(tile.id, normalizedFaction, playerId, playerName);
        allocation.setAllocated(type, allocation.getAllocated(type) + amount);
        markDirty();
        return true;
    }

    public boolean unallocatePopulation(String tileId, String faction, UUID playerId, KOMEPopulationType type, int amount) {
        KOMEPlayerTilePopulationAllocation allocation = getAllocation(tileId, faction, playerId);
        if (allocation == null || amount <= 0) {
            return false;
        }
        int next = allocation.getAllocated(type) - amount;
        if (next < allocation.getUsed(type)) {
            return false;
        }
        allocation.setAllocated(type, next);
        markDirty();
        return true;
    }

    public boolean consumeAllocationForHire(KOMEHiredUnitRecord record) {
        if (record == null || record.isPlayerReserveFunded() || record.allocationPlayer == null) {
            return false;
        }
        KOMEPlayerTilePopulationAllocation allocation = getAllocation(record.allocationTileId, record.allocationFaction, record.allocationPlayer);
        return allocation != null && allocation.tryUse(record.type, record.cost);
    }

    public void releaseAllocationUsed(KOMEHiredUnitRecord record) {
        if (record == null || record.allocationPlayer == null) {
            return;
        }
        KOMEPlayerTilePopulationAllocation allocation = getAllocation(record.allocationTileId, record.allocationFaction, record.allocationPlayer);
        if (allocation != null) {
            allocation.release(record.type, record.cost);
        }
    }

    public void reconcileClaimantAllocation(KOMEConquestTile tile) {
        if (tile == null || !tile.isClaimed() || hasFactionKing(tile.currentRulingFaction()) || tile.claimedByUuid == null) {
            return;
        }
        String faction = KOMEAlliance.normalizeFactionKey(tile.currentRulingFaction());
        KOMEPlayerTilePopulationAllocation allocation = getOrCreateAllocation(tile.id, faction, tile.claimedByUuid, tile.claimedByName);
        int otherOffensive = 0;
        int otherDefensive = 0;
        for (KOMEPlayerTilePopulationAllocation other : getAllocationsForTile(tile.id, faction)) {
            if (other.playerUuid.equals(tile.claimedByUuid)) {
                continue;
            }
            other.setAllocated(KOMEPopulationType.OFFENSIVE, other.offensiveUsed);
            other.setAllocated(KOMEPopulationType.DEFENSIVE, other.defensiveUsed);
            otherOffensive += other.offensiveAllocated;
            otherDefensive += other.defensiveAllocated;
        }
        allocation.setAllocated(KOMEPopulationType.OFFENSIVE, Math.max(0, getEffectiveUsablePopulation(tile.id, faction, KOMEPopulationType.OFFENSIVE) - otherOffensive));
        allocation.setAllocated(KOMEPopulationType.DEFENSIVE, Math.max(0, getEffectiveUsablePopulation(tile.id, faction, KOMEPopulationType.DEFENSIVE) - otherDefensive));
        markDirty();
    }

    public void claimTile(KOMEConquestTile tile, String faction, long worldTime, UUID playerId, String playerName) {
        if (tile == null) {
            return;
        }
        String previousOwner = KOMEAlliance.normalizeFactionKey(tile.currentRulingFaction());
        ensureDefaultArrivalPoint(tile);
        tile.claim(faction, worldTime, playerId, playerName);
        ensureDefaultArrivalPoint(tile);
        String currentOwner = KOMEAlliance.normalizeFactionKey(tile.currentRulingFaction());
        if (!previousOwner.equals(currentOwner)) {
            for (KOMEPlayerTilePopulationAllocation allocation : getAllocationsForTile(tile.id, currentOwner)) {
                allocation.setAllocated(KOMEPopulationType.OFFENSIVE, allocation.offensiveUsed);
                allocation.setAllocated(KOMEPopulationType.DEFENSIVE, allocation.defensiveUsed);
            }
        }
        reconcileClaimantAllocation(tile);
        markDirty();
    }

    public String findFactionControlledTile(String factionKey) {
        String key = KOMEAlliance.normalizeFactionKey(factionKey);
        List<String> tileIds = new ArrayList<String>(conquestTiles.keySet());
        Collections.sort(tileIds);
        for (String tileId : tileIds) {
            KOMEConquestTile tile = conquestTiles.get(tileId);
            if (tile != null && tile.isClaimed() && key.equals(KOMEAlliance.normalizeFactionKey(tile.currentRulingFaction()))) {
                return KOMEConquestTile.normalizeId(tile.id);
            }
        }
        return "";
    }

    public boolean adjustTilePopulationTotal(String tileId, KOMEPopulationType type, int delta) {
        if (type == null || delta == 0) {
            return false;
        }
        KOMEConquestTile tile = getConquestTile(tileId);
        if (!tile.isClaimed()) {
            return false;
        }
        String rulingFaction = tile.currentRulingFaction();
        KOMETilePopulation population = getOrCreateTilePopulationPool(tileId, rulingFaction);
        int currentTotal = population.getTotal(type);
        int used = population.getUsed(type);
        int minimumTotal = used;
        if (hasFactionKing(rulingFaction)) {
            int effectiveTotal = getEffectiveUsablePopulation(tile.id, rulingFaction, type);
            int allocatedTotal = getTotalAllocated(tile.id, rulingFaction, type);
            int unallocated = Math.max(0, effectiveTotal - allocatedTotal);
            minimumTotal = Math.max(minimumTotal, currentTotal - unallocated);
        }
        int nextTotal = Math.max(minimumTotal, currentTotal + delta);
        if (nextTotal == currentTotal) {
            return false;
        }
        population.setTotal(type, nextTotal);
        reconcileClaimantAllocation(tile);
        markDirty();
        return true;
    }

    public KOMEAlliance getAlliance(String factionA, String factionB, boolean create) {
        String key = KOMEAlliance.directionKey(factionA, factionB);
        KOMEAlliance alliance = alliances.get(key);
        if (alliance == null && create) {
            alliance = new KOMEAlliance(factionA, factionB);
            alliances.put(key, alliance);
        }
        return alliance;
    }

    public int getAllianceTier(String type, String factionA, String factionB) {
        KOMEAlliance alliance = getAlliance(factionA, factionB, false);
        return alliance == null ? -1 : alliance.getTier(KOMEAlliance.normalizeType(type));
    }

    public boolean clearAlliance(String factionA, String factionB) {
        String key = KOMEAlliance.directionKey(factionA, factionB);
        boolean removed = alliances.remove(key) != null;
        if (removed) {
            markDirty();
        }
        return removed;
    }

    public boolean canFactionUseMilitaryPassage(String movingFaction, String tileOwnerFaction) {
        String moving = KOMEAlliance.normalizeFactionKey(movingFaction);
        String owner = KOMEAlliance.normalizeFactionKey(tileOwnerFaction);
        if (moving.length() == 0 || owner.length() == 0) {
            return false;
        }
        if (moving.equals(owner)) {
            return true;
        }
        KOMEAlliance alliance = getAlliance(moving, owner, false);
        return alliance != null && alliance.militaryTier >= MILITARY_PASSAGE_TIER;
    }

    public KOMEConquestRouteEdge getRouteEdgeOverride(String tileA, String tileB) {
        return routeEdges.get(KOMEConquestRouteEdge.key(tileA, tileB));
    }

    public KOMEConquestRouteEdge getRouteEdge(String tileA, String tileB) {
        String a = KOMEConquestTile.normalizeId(tileA);
        String b = KOMEConquestTile.normalizeId(tileB);
        KOMEConquestRouteEdge automatic = KOMEConquestTileDefaults.getAutomaticRouteEdge(a, b);
        KOMEConquestRouteEdge override = getRouteEdgeOverride(a, b);
        if (override != null) {
            return override;
        }
        return automatic;
    }

    public KOMEConquestRouteEdge setRouteEdge(String tileA, String tileB, String edgeType, String name,
            int dimension, double x, double y, double z, String createdBy) {
        KOMEConquestRouteEdge edge = new KOMEConquestRouteEdge(tileA, tileB, edgeType);
        edge.name = name == null ? "" : name.replace('_', ' ').trim();
        edge.createdBy = createdBy == null ? "" : createdBy;
        edge.createdAtMillis = System.currentTimeMillis();
        edge.markerDimension = dimension;
        edge.markerX = x;
        edge.markerY = y;
        edge.markerZ = z;
        edge.manual = true;
        routeEdges.put(KOMEConquestRouteEdge.key(tileA, tileB), edge);
        markDirty();
        return edge;
    }

    public boolean removeRouteEdgeOverride(String tileA, String tileB) {
        boolean removed = routeEdges.remove(KOMEConquestRouteEdge.key(tileA, tileB)) != null;
        if (removed) {
            markDirty();
        }
        return removed;
    }

    public Set<String> getRouteNeighbors(String tileId) {
        String normalized = KOMEConquestTile.normalizeId(tileId);
        Set<String> neighbors = new HashSet<String>(KOMEConquestTileDefaults.getAdjacentTiles(normalized));
        for (KOMEConquestRouteEdge edge : routeEdges.values()) {
            if (edge != null && edge.connects(normalized)) {
                String other = edge.other(normalized);
                if (other.length() > 0) {
                    neighbors.add(other);
                }
            }
        }
        return neighbors;
    }

    public Set<String> getRouteGraphTiles() {
        Set<String> tiles = new HashSet<String>(KOMEConquestTileDefaults.getKnownTileIds());
        tiles.addAll(conquestTiles.keySet());
        for (KOMEConquestRouteEdge edge : routeEdges.values()) {
            if (edge != null) {
                tiles.add(KOMEConquestTile.normalizeId(edge.fromTile));
                tiles.add(KOMEConquestTile.normalizeId(edge.toTile));
            }
        }
        return tiles;
    }

    public void rememberPlayerName(UUID playerID, String playerName) {
        if (playerID == null || playerName == null || playerName.trim().isEmpty()) {
            return;
        }
        String previous = playerNames.put(playerID, playerName);
        if (!playerName.equals(previous)) {
            markDirty();
        }
    }

    public boolean claimFactionKing(String factionKey, String factionName, UUID playerID, String playerName) {
        return reconcilePlayerKingship(factionKey, playerID, playerName, true);
    }

    public boolean reconcilePlayerKingship(String factionKey, UUID playerID, String playerName, boolean eligible) {
        String key = normalizeFactionKey(factionKey);
        if (playerID == null) {
            return false;
        }
        boolean changed = false;
        List<String> staleFactions = new ArrayList<String>();
        for (Map.Entry<String, UUID> entry : kingsByFaction.entrySet()) {
            if (playerID.equals(entry.getValue()) && (!eligible || !key.equals(entry.getKey()))) {
                staleFactions.add(entry.getKey());
            }
        }
        for (String staleFaction : staleFactions) {
            kingsByFaction.remove(staleFaction);
            kingNamesByFaction.remove(staleFaction);
            changed = true;
        }
        if (!eligible || key.length() == 0) {
            if (changed) {
                markDirty();
            }
            return false;
        }
        UUID existing = kingsByFaction.get(key);
        if (existing == null) {
            kingsByFaction.put(key, playerID);
            kingNamesByFaction.put(key, playerName == null ? "" : playerName);
            markDirty();
            return true;
        }
        if (existing.equals(playerID)) {
            String currentName = kingNamesByFaction.get(key);
            if (playerName != null && playerName.length() > 0 && !playerName.equals(currentName)) {
                kingNamesByFaction.put(key, playerName);
                markDirty();
            } else if (changed) {
                markDirty();
            }
            return true;
        }
        if (changed) {
            markDirty();
        }
        return false;
    }

    public boolean isFactionKing(String factionKey, UUID playerID) {
        String key = normalizeFactionKey(factionKey);
        return key.length() > 0 && playerID != null && playerID.equals(kingsByFaction.get(key));
    }

    public boolean hasFactionKing(String factionKey) {
        String key = normalizeFactionKey(factionKey);
        return key.length() > 0 && kingsByFaction.containsKey(key);
    }

    public String getFactionKingName(String factionKey) {
        String key = normalizeFactionKey(factionKey);
        String name = kingNamesByFaction.get(key);
        return name == null ? "" : name;
    }

    protected void clearFactionKingRecords() {
        kingsByFaction.clear();
        kingNamesByFaction.clear();
    }

    public int getFactionFarmerPop(String factionKey) {
        String key = normalizeFactionKey(factionKey);
        if (key.length() == 0) {
            return 0;
        }
        int total = 0;
        for (Map.Entry<UUID, KOMEPlayerPopulation> entry : populations.entrySet()) {
            UUID playerID = entry.getKey();
            KOMEPlayerProgression progression = progressions.get(playerID);
            boolean member = progression != null && key.equals(normalizeFactionKey(progression.getPledgedLordFaction()));
            if (!member && playerID != null && playerID.equals(kingsByFaction.get(key))) {
                member = true;
            }
            if (member && entry.getValue() != null) {
                total += entry.getValue().getFarmhandLimit() * 25;
            }
        }
        return Math.max(0, total - getFactionFarmerPopSpent(key));
    }

    public int getFactionPopulation(String factionKey) {
        String key = normalizeFactionKey(factionKey);
        if (key.length() == 0) {
            return 0;
        }
        int total = 0;
        for (Map.Entry<UUID, KOMEPlayerPopulation> entry : populations.entrySet()) {
            UUID playerID = entry.getKey();
            KOMEPlayerProgression progression = progressions.get(playerID);
            boolean member = progression != null && key.equals(normalizeFactionKey(progression.getPledgedLordFaction()));
            if (!member && playerID != null && playerID.equals(kingsByFaction.get(key))) {
                member = true;
            }
            if (member && entry.getValue() != null) {
                total += entry.getValue().getCombinedTotal();
            }
        }
        return Math.max(0, total - getFactionPopulationSpent(key));
    }

    public int getFarmhandLimit(UUID owner) {
        KOMEPlayerPopulation pop = getPopulation(owner);
        int allocatedPopulation = getPlayerTilePopulationAllocated(owner);
        int populationSlots = (pop.getCombinedTotal() + allocatedPopulation) / 25;
        return Math.max(0, populationSlots - getFactionFarmerSlotsSpent(getPlayerFactionKey(owner)));
    }

    public int getPlayerTilePopulationAllocated(UUID owner) {
        if (owner == null) {
            return 0;
        }
        int allocated = 0;
        String faction = getPlayerFactionKey(owner);
        for (KOMEPlayerTilePopulationAllocation allocation : getAllocationsForFaction(faction)) {
            if (owner.equals(allocation.playerUuid)) {
                allocated += allocation.offensiveAllocated + allocation.defensiveAllocated;
            }
        }
        return allocated;
    }

    public String getPlayerFactionKey(UUID playerID) {
        KOMEPlayerProgression progression = progressions.get(playerID);
        String key = progression == null ? "" : normalizeFactionKey(progression.getPledgedLordFaction());
        if (key.length() > 0) {
            return key;
        }
        for (Map.Entry<String, UUID> entry : kingsByFaction.entrySet()) {
            if (playerID != null && playerID.equals(entry.getValue())) {
                return entry.getKey();
            }
        }
        return "";
    }

    private int getFactionFarmerSlotsSpent(String factionKey) {
        return getFactionFarmerPopSpent(factionKey) / 25;
    }

    private int getFactionFarmerPopSpent(String factionKey) {
        String key = normalizeFactionKey(factionKey);
        if (key.length() == 0) {
            return 0;
        }
        int spent = 0;
        for (KOMEAlliance alliance : alliances.values()) {
            if (alliance != null && alliance.tradeTier >= 2 && key.equals(normalizeFactionKey(alliance.factionA))) {
                spent += KOMEAllianceInventory.TRADE_T2_FARMER_POP_REQUIRED;
            }
        }
        return spent;
    }

    private int getFactionPopulationSpent(String factionKey) {
        String key = normalizeFactionKey(factionKey);
        if (key.length() == 0) {
            return 0;
        }
        int spent = 0;
        for (KOMEAlliance alliance : alliances.values()) {
            if (alliance != null && alliance.militaryTier >= 4 && key.equals(normalizeFactionKey(alliance.factionA))) {
                spent += KOMEAllianceInventory.MILITARY_T4_POP_REQUIRED;
            }
        }
        return spent;
    }

    public boolean isProgressionEnabled() {
        return progressionEnabled;
    }

    public void setProgressionEnabled(boolean enabled) {
        if (progressionEnabled != enabled) {
            progressionEnabled = enabled;
            markDirty();
        }
    }

    private static String normalizeFactionKey(String value) {
        return KOMEAlliance.normalizeFactionKey(value);
    }

    public int getFarmhandsUsed(UUID owner) {
        int count = 0;
        for (KOMEHiredUnitRecord record : hiredUnits.values()) {
            if (record.farmhand && owner.equals(record.owner)) {
                count++;
            }
        }
        return count;
    }

    public int getArmyPopulationUsed(UUID owner) {
        return getArmyPopulationUsed(owner, null);
    }

    public int getArmyPopulationUsed(UUID owner, KOMEPopulationType type) {
        int used = 0;
        for (KOMEHiredUnitRecord record : hiredUnits.values()) {
            if (!record.farmhand && owner.equals(record.owner) && (type == null || record.type == type)) {
                used += Math.max(0, record.cost);
            }
        }
        return used;
    }

    public int getPlayerReservePopulationUsed(UUID owner, KOMEPopulationType type) {
        int used = 0;
        for (KOMEHiredUnitRecord record : hiredUnits.values()) {
            if (record == null || record.farmhand || !record.isPlayerReserveFunded()) {
                continue;
            }
            UUID sourcePlayer = record.sourcePlayer == null ? record.owner : record.sourcePlayer;
            if (owner.equals(sourcePlayer) && (type == null || record.type == type)) {
                used += Math.max(0, record.cost);
            }
        }
        return used;
    }

    public int getFactionPlayerReserveTotal(String factionKey, KOMEPopulationType type) {
        String key = normalizeFactionKey(factionKey);
        int total = 0;
        for (Map.Entry<UUID, KOMEPlayerPopulation> entry : populations.entrySet()) {
            if (key.equals(getPlayerFactionKey(entry.getKey())) && entry.getValue() != null) {
                total += entry.getValue().getTotal(type);
            }
        }
        return total;
    }

    public int getFactionPlayerReserveUsed(String factionKey, KOMEPopulationType type) {
        String key = normalizeFactionKey(factionKey);
        int used = 0;
        for (KOMEHiredUnitRecord record : hiredUnits.values()) {
            if (record != null && record.isPlayerReserveFunded() && key.equals(normalizeFactionKey(record.sourceFaction)) && (type == null || record.type == type)) {
                used += Math.max(0, record.cost);
            }
        }
        return used;
    }

    public int getTrackedUnitCount(UUID owner, boolean farmhands) {
        int count = 0;
        for (KOMEHiredUnitRecord record : hiredUnits.values()) {
            if (record.farmhand == farmhands && owner.equals(record.owner)) {
                count++;
            }
        }
        return count;
    }

    public void removeInactiveLoadedHiredUnits(World world, UUID owner) {
        Set<UUID> inactiveUnits = new HashSet<>();
        for (Object object : world.loadedEntityList) {
            if (!(object instanceof LOTREntityNPC)) {
                continue;
            }
            LOTREntityNPC npc = (LOTREntityNPC) object;
            UUID entityID = KOMEReflection.getEntityUUID(npc);
            KOMEHiredUnitRecord record = hiredUnits.get(entityID);
            if (record == null || owner != null && !owner.equals(record.owner)) {
                continue;
            }
            if (record.isMoving()) {
                continue;
            }
            if (!npc.isEntityAlive() || !npc.hiredNPCInfo.isActive) {
                inactiveUnits.add(entityID);
            }
        }
        for (UUID entityID : inactiveUnits) {
            KOMEHiredUnitRecord record = hiredUnits.remove(entityID);
            removeUnitFromCompany(record);
            if (record != null && !record.farmhand) {
                if (record.isPlayerReserveFunded()) {
                    getPopulation(record.sourcePlayer == null ? record.owner : record.sourcePlayer).release(record.type, record.cost);
                } else {
                    KOMETilePopulation population = getFundingPool(record);
                    if (population != null) {
                        population.release(record.type, record.cost);
                    }
                    releaseAllocationUsed(record);
                }
            }
        }
        if (!inactiveUnits.isEmpty()) {
            markDirty();
            syncConquestTiles();
        }
    }

    public void removeUnitFromCompany(KOMEHiredUnitRecord record) {
        if (record == null || record.companyId == null || record.companyId.length() == 0) {
            return;
        }
        KOMEArmyCompany company = armyCompanies.get(record.companyId);
        if (company != null) {
            company.units.remove(record.entity);
            company.totalPopulation = Math.max(0, company.totalPopulation - Math.max(0, record.cost));
            if (record.mounted) {
                company.mountedPopulation = Math.max(0, company.mountedPopulation - Math.max(0, record.cost));
            } else {
                company.groundPopulation = Math.max(0, company.groundPopulation - Math.max(0, record.cost));
            }
            company.updatedAtMillis = System.currentTimeMillis();
        }
        record.companyId = "";
    }

    public void rebuildArmyCompaniesForPlayer(UUID owner) {
        rebuildArmyCompanies(owner);
    }

    public void rebuildArmyCompaniesForPlayer(World world, UUID owner) {
        syncLoadedLotrCompanyAssignments(world, owner);
        rebuildArmyCompanies(owner);
    }

    public void rebuildArmyCompanies() {
        rebuildArmyCompanies((UUID) null);
    }

    public void rebuildArmyCompanies(World world) {
        syncLoadedLotrCompanyAssignments(world, null);
        rebuildArmyCompanies((UUID) null);
    }

    public void syncLoadedLotrCompanyAssignments(World world, UUID onlyOwner) {
        if (world == null || KOMEReflection.isRemote(world)) {
            return;
        }
        boolean changed = false;
        for (Object object : world.loadedEntityList) {
            if (!(object instanceof LOTREntityNPC)) {
                continue;
            }
            LOTREntityNPC npc = (LOTREntityNPC) object;
            if (npc.hiredNPCInfo == null || !npc.hiredNPCInfo.isActive) {
                continue;
            }
            KOMEHiredUnitRecord record = hiredUnits.get(KOMEReflection.getEntityUUID(npc));
            if (record == null || onlyOwner != null && !onlyOwner.equals(record.owner)) {
                continue;
            }
            String lotrCompany = normalizeLotrCompanyValue(npc.hiredNPCInfo.getSquadron());
            if (!lotrCompany.equals(record.lotrCompanyValue == null ? "" : record.lotrCompanyValue)) {
                record.lotrCompanyValue = lotrCompany;
                record.companyName = lotrCompany;
                if (!record.isMoving()) {
                    record.companyId = "";
                }
                changed = true;
            }
        }
        if (changed) {
            markDirty();
        }
    }

    private void rebuildArmyCompanies(UUID onlyOwner) {
        long now = System.currentTimeMillis();
        for (KOMEArmyCompany company : new ArrayList<KOMEArmyCompany>(armyCompanies.values())) {
            if (company == null || company.isMoving() || onlyOwner != null && !onlyOwner.equals(company.owner)) {
                continue;
            }
            company.source = KOMEArmyCompany.SOURCE_LEGACY_MIGRATED;
        }

        Set<String> removable = new HashSet<String>();
        for (Map.Entry<String, KOMEArmyCompany> entry : armyCompanies.entrySet()) {
            KOMEArmyCompany company = entry.getValue();
            if (company != null && !company.isMoving() && (onlyOwner == null || onlyOwner.equals(company.owner))) {
                removable.add(entry.getKey());
            }
        }
        for (String id : removable) {
            armyCompanies.remove(id);
        }
        for (KOMEHiredUnitRecord record : hiredUnits.values()) {
            if (record != null && record.companyId != null && removable.contains(record.companyId)) {
                record.companyId = "";
            }
        }

        Map<String, KOMEArmyCompany> groups = new HashMap<String, KOMEArmyCompany>();
        for (KOMEHiredUnitRecord record : hiredUnits.values()) {
            if (!isEligibleForAutoCompany(record) || onlyOwner != null && !onlyOwner.equals(record.owner)) {
                continue;
            }
            String name = normalizeLotrCompanyValue(record.lotrCompanyValue);
            if (name.length() == 0) {
                record.companyId = "";
                continue;
            }
            String faction = normalizeFactionKey(getPlayerFactionKey(record.owner));
            String tile = KOMEConquestTile.normalizeId(record.currentTile);
            if (faction.length() == 0 || tile.length() == 0) {
                record.companyId = "";
                continue;
            }
            String key = companyGroupKey(record.owner, faction, name, tile);
            KOMEArmyCompany company = groups.get(key);
            if (company == null) {
                company = new KOMEArmyCompany();
                company.id = autoCompanyId(key, record.owner, tile);
                company.owner = record.owner;
                company.ownerName = playerNames.get(record.owner);
                if (company.ownerName == null || company.ownerName.length() == 0) {
                    company.ownerName = record.companyAssignedByName == null ? "" : record.companyAssignedByName;
                }
                company.faction = faction;
                company.name = companyDisplayName(name);
                company.lotrCompanyValue = name;
                company.currentTile = tile;
                company.status = KOMEArmyCompany.STATIONED;
                company.source = KOMEArmyCompany.SOURCE_LOTR_COMPANY_ASSIGNMENT;
                company.createdAtMillis = now;
                company.updatedAtMillis = now;
                groups.put(key, company);
            }
            company.units.add(record.entity);
            company.totalPopulation += Math.max(0, record.cost);
            if (record.mounted) {
                company.mountedPopulation += Math.max(0, record.cost);
            } else {
                company.groundPopulation += Math.max(0, record.cost);
            }
            record.companyId = company.id;
            record.companyName = name;
            record.lotrCompanyValue = name;
        }

        for (KOMEArmyCompany company : groups.values()) {
            armyCompanies.put(company.id, company);
        }
        markDirty();
        syncConquestTiles();
    }

    private static boolean isEligibleForAutoCompany(KOMEHiredUnitRecord record) {
        return record != null && record.entity != null && !record.farmhand
            && record.type == KOMEPopulationType.OFFENSIVE && !record.isMoving();
    }

    private static String companyGroupKey(UUID owner, String faction, String name, String tile) {
        return (owner == null ? "" : owner.toString()) + "|" + normalizeFactionKey(faction) + "|"
            + KOMEConquestTile.normalizeId(tile) + "|" + normalizeLotrCompanyValue(name).toLowerCase();
    }

    public static String normalizeLotrCompanyValue(String value) {
        return KOMEHiredUnitRecord.normalizeCompanyName(value);
    }

    private static String companyDisplayName(String value) {
        String name = normalizeLotrCompanyValue(value);
        if (name.length() == 0) {
            return "Company";
        }
        for (int i = 0; i < name.length(); i++) {
            if (!Character.isDigit(name.charAt(i))) {
                return name;
            }
        }
        return "Company " + name;
    }

    private String autoCompanyId(String key, UUID owner, String tile) {
        String ownerPart = owner == null ? "none" : owner.toString().substring(0, 8);
        String base = "AC_" + ownerPart + "_" + KOMEConquestTile.normalizeId(tile) + "_" + Integer.toHexString(key.hashCode());
        if (!armyCompanies.containsKey(base)) {
            return base;
        }
        int i = 2;
        while (armyCompanies.containsKey(base + "_" + i)) {
            i++;
        }
        return base + "_" + i;
    }

    public void syncConquestTiles() {
        for (Object player : FMLCommonHandler.instance().getMinecraftServerInstance().getConfigurationManager().playerEntityList) {
            KOMEPacketConquestData.sendChunked(this, (EntityPlayerMP) player);
        }
    }

    public void syncConquestTiles(EntityPlayerMP player) {
        KOMEPacketConquestData.sendChunked(this, player);
    }

    public boolean isAdminUnitMapMarkersDisabled(UUID playerId) {
        return playerId != null && adminUnitMapMarkerOptOuts.contains(playerId);
    }

    public void setAdminUnitMapMarkersDisabled(UUID playerId, boolean disabled) {
        if (playerId == null) {
            return;
        }
        boolean changed;
        if (disabled) {
            changed = adminUnitMapMarkerOptOuts.add(playerId);
        } else {
            changed = adminUnitMapMarkerOptOuts.remove(playerId);
        }
        if (changed) {
            markDirty();
        }
    }

    public KOMETilePopulation getFundingPool(KOMEHiredUnitRecord record) {
        if (record == null) {
            return null;
        }
        String sourceTile = record.sourceTileId == null || record.sourceTileId.length() == 0 ? record.currentTile : record.sourceTileId;
        String sourceFaction = record.sourceFaction;
        if (sourceFaction == null || sourceFaction.length() == 0) {
            KOMEConquestTile tile = conquestTiles.get(KOMEConquestTile.normalizeId(sourceTile));
            sourceFaction = tile == null ? "" : tile.currentRulingFaction();
        }
        KOMETilePopulation population = getTilePopulationPool(sourceTile, sourceFaction);
        if (population != null) {
            return population;
        }
        List<KOMETilePopulation> pools = getTilePopulationPools(sourceTile);
        return pools.size() == 1 ? pools.get(0) : null;
    }

    public static String tilePopulationKey(String tileId, String sourceFaction) {
        return KOMEConquestTile.normalizeId(tileId) + "|" + KOMEAlliance.normalizeFactionKey(sourceFaction);
    }

    public static String tileWaypointKey(String tileId, String type) {
        return KOMEConquestTile.normalizeId(tileId) + "|" + KOMETileWaypoint.normalizeType(type);
    }

    public static String populationAllocationKey(String tileId, String faction, UUID playerId) {
        return KOMEConquestTile.normalizeId(tileId) + "|" + KOMEAlliance.normalizeFactionKey(faction) + "|" + (playerId == null ? "" : playerId.toString());
    }

    public static String recruitmentTileKey(String faction, UUID playerId) {
        return KOMEAlliance.normalizeFactionKey(faction) + "|" + (playerId == null ? "" : playerId.toString());
    }

    private static class WaypointCandidate {
        final LOTRWaypoint waypoint;
        final double distanceSq;

        WaypointCandidate(LOTRWaypoint waypoint, double distanceSq) {
            this.waypoint = waypoint;
            this.distanceSq = distanceSq;
        }
    }

    public static class EffectivePopulationSummary {
        public int offensiveTotal;
        public int offensiveUsed;
        public int defensiveTotal;
        public int defensiveUsed;

        public int getOffensiveAvailable() {
            return Math.max(0, offensiveTotal - offensiveUsed);
        }

        public int getDefensiveAvailable() {
            return Math.max(0, defensiveTotal - defensiveUsed);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        boolean migratedPopulationData = false;
        populations.clear();
        progressions.clear();
        hiredUnits.clear();
        conquestTiles.clear();
        tilePopulations.clear();
        populationAllocations.clear();
        activeRecruitmentTiles.clear();
        tileWaypoints.clear();
        tileWaypointLinksByTileId.clear();
        routeEdges.clear();
        alliances.clear();
        armyMovements.clear();
        armyCompanies.clear();
        movementHistory.clear();
        playerNames.clear();
        adminUnitMapMarkerOptOuts.clear();
        clearFactionKingRecords();
        progressionEnabled = !nbt.hasKey("ProgressionEnabled") || nbt.getBoolean("ProgressionEnabled");
        movementSecondsPerTileOverride = Math.max(0, nbt.getInteger("MovementSecondsPerTileOverride"));
        movementTotalSecondsOverride = Math.max(0, nbt.getInteger("MovementTotalSecondsOverride"));
        movementStepDelaySeconds = nbt.hasKey("MovementStepDelaySeconds") ? Math.max(0, nbt.getInteger("MovementStepDelaySeconds")) : 5;
        movementDailyResetTime = nbt.hasKey("MovementDailyResetTime") ? nbt.getString("MovementDailyResetTime") : "20:00";
        movementDailyResetTimezone = nbt.hasKey("MovementDailyResetTimezone") ? nbt.getString("MovementDailyResetTimezone") : "America/Chicago";
        conquestDefaultsInitialized = nbt.hasKey("ConquestDefaultsInitialized") && nbt.getBoolean("ConquestDefaultsInitialized");
        if (movementDailyResetTime == null || movementDailyResetTime.length() == 0) {
            movementDailyResetTime = "20:00";
        }
        if (movementDailyResetTimezone == null || movementDailyResetTimezone.length() == 0) {
            movementDailyResetTimezone = "America/Chicago";
        }

        NBTTagList popList = nbt.getTagList("Populations", 10);
        for (int i = 0; i < popList.tagCount(); i++) {
            NBTTagCompound entry = popList.getCompoundTagAt(i);
            KOMEPlayerPopulation pop = new KOMEPlayerPopulation();
            pop.readFromNBT(entry);
            populations.put(UUID.fromString(entry.getString("Player")), pop);
        }

        NBTTagList progressionList = nbt.getTagList("Progressions", 10);
        for (int i = 0; i < progressionList.tagCount(); i++) {
            NBTTagCompound entry = progressionList.getCompoundTagAt(i);
            KOMEPlayerProgression progression = new KOMEPlayerProgression();
            progression.readFromNBT(entry);
            progressions.put(UUID.fromString(entry.getString("Player")), progression);
        }

        NBTTagList playerNameList = nbt.getTagList("PlayerNames", 10);
        for (int i = 0; i < playerNameList.tagCount(); i++) {
            NBTTagCompound entry = playerNameList.getCompoundTagAt(i);
            String player = entry.getString("Player");
            String name = entry.getString("Name");
            if (player.length() > 0 && name.length() > 0) {
                playerNames.put(UUID.fromString(player), name);
            }
        }

        NBTTagList kingList = nbt.getTagList("FactionKings", 10);
        for (int i = 0; i < kingList.tagCount(); i++) {
            NBTTagCompound entry = kingList.getCompoundTagAt(i);
            String faction = normalizeFactionKey(entry.getString("Faction"));
            String player = entry.getString("Player");
            if (faction.length() > 0 && player.length() > 0) {
                kingsByFaction.put(faction, UUID.fromString(player));
                kingNamesByFaction.put(faction, entry.getString("Name"));
            }
        }

        NBTTagList adminMarkerList = nbt.getTagList("AdminUnitMapMarkerOptOuts", 10);
        for (int i = 0; i < adminMarkerList.tagCount(); i++) {
            String player = adminMarkerList.getCompoundTagAt(i).getString("Player");
            if (player.length() > 0) {
                adminUnitMapMarkerOptOuts.add(UUID.fromString(player));
            }
        }

        NBTTagList hiredList = nbt.getTagList("HiredUnits", 10);
        for (int i = 0; i < hiredList.tagCount(); i++) {
            KOMEHiredUnitRecord record = new KOMEHiredUnitRecord();
            record.readFromNBT(hiredList.getCompoundTagAt(i));
            hiredUnits.put(record.entity, record);
        }

        NBTTagList conquestList = nbt.getTagList("ConquestTiles", 10);
        for (int i = 0; i < conquestList.tagCount(); i++) {
            KOMEConquestTile tile = new KOMEConquestTile("");
            tile.readFromNBT(conquestList.getCompoundTagAt(i));
            if (!tile.id.isEmpty()) {
                conquestTiles.put(tile.id, tile);
            }
        }

        NBTTagList tilePopulationList = nbt.getTagList("TilePopulations", 10);
        for (int i = 0; i < tilePopulationList.tagCount(); i++) {
            NBTTagCompound savedPopulation = tilePopulationList.getCompoundTagAt(i);
            KOMETilePopulation population = new KOMETilePopulation();
            population.readFromNBT(savedPopulation);
            if (population.tileId.length() > 0) {
                if (!savedPopulation.hasKey("SourceFaction")) {
                    KOMEConquestTile tile = conquestTiles.get(population.tileId);
                    String rulingFaction = tile == null ? "" : tile.currentRulingFaction();
                    if (rulingFaction.length() > 0) {
                        population.sourceFaction = KOMEAlliance.normalizeFactionKey(rulingFaction);
                    } else {
                        population.sourceFaction = KOMEAlliance.normalizeFactionKey(population.faction);
                    }
                    population.faction = population.sourceFaction;
                    migratedPopulationData = true;
                }
                tilePopulations.put(tilePopulationKey(population.tileId, population.sourceFaction), population);
            }
        }

        NBTTagList allocationList = nbt.getTagList("PopulationAllocations", 10);
        for (int i = 0; i < allocationList.tagCount(); i++) {
            KOMEPlayerTilePopulationAllocation allocation = new KOMEPlayerTilePopulationAllocation();
            allocation.readFromNBT(allocationList.getCompoundTagAt(i));
            if (allocation.playerUuid != null && allocation.tileId.length() > 0 && allocation.faction.length() > 0) {
                populationAllocations.put(populationAllocationKey(allocation.tileId, allocation.faction, allocation.playerUuid), allocation);
            }
        }

        NBTTagList recruitmentTileList = nbt.getTagList("ActiveRecruitmentTiles", 10);
        for (int i = 0; i < recruitmentTileList.tagCount(); i++) {
            NBTTagCompound entry = recruitmentTileList.getCompoundTagAt(i);
            String faction = KOMEAlliance.normalizeFactionKey(entry.getString("Faction"));
            String player = entry.getString("Player");
            String tile = KOMEConquestTile.normalizeId(entry.getString("Tile"));
            if (faction.length() > 0 && player.length() > 0 && tile.length() > 0) {
                activeRecruitmentTiles.put(recruitmentTileKey(faction, UUID.fromString(player)), tile);
            }
        }

        NBTTagList waypointList = nbt.getTagList("TileWaypoints", 10);
        for (int i = 0; i < waypointList.tagCount(); i++) {
            KOMETileWaypoint waypoint = new KOMETileWaypoint();
            waypoint.readFromNBT(waypointList.getCompoundTagAt(i));
            if (waypoint.tileId.length() > 0) {
                tileWaypoints.put(tileWaypointKey(waypoint.tileId, waypoint.type), waypoint);
            }
        }
        for (KOMEConquestTile tile : conquestTiles.values()) {
            if (tile != null && tile.hasAnchor && getTileWaypoint(tile.id, KOMETileWaypoint.RALLY) == null) {
                KOMETileWaypoint waypoint = new KOMETileWaypoint(tile.id, KOMETileWaypoint.RALLY);
                waypoint.set(tile.anchorDimension, tile.anchorX, tile.anchorY, tile.anchorZ, "Legacy anchor", false);
                tileWaypoints.put(tileWaypointKey(tile.id, KOMETileWaypoint.RALLY), waypoint);
                migratedPopulationData = true;
            }
        }

        NBTTagList tileWaypointLinkList = nbt.getTagList("TileWaypointLinks", 10);
        for (int i = 0; i < tileWaypointLinkList.tagCount(); i++) {
            KOMETileWaypointLink link = new KOMETileWaypointLink();
            link.readFromNBT(tileWaypointLinkList.getCompoundTagAt(i));
            if (link.tileId.length() > 0 && link.lotrWaypointKey.length() > 0) {
                tileWaypointLinksByTileId.put(link.tileId, link);
            }
        }

        NBTTagList routeEdgeList = nbt.getTagList("RouteEdges", 10);
        for (int i = 0; i < routeEdgeList.tagCount(); i++) {
            KOMEConquestRouteEdge edge = new KOMEConquestRouteEdge();
            edge.readFromNBT(routeEdgeList.getCompoundTagAt(i));
            if (edge.fromTile.length() > 0 && edge.toTile.length() > 0 && !edge.fromTile.equals(edge.toTile)) {
                routeEdges.put(KOMEConquestRouteEdge.key(edge.fromTile, edge.toTile), edge);
            }
        }

        for (KOMEHiredUnitRecord record : hiredUnits.values()) {
            if (record == null || record.farmhand) {
                continue;
            }
            if (record.sourceTileId == null || record.sourceTileId.length() == 0) {
                record.sourceTileId = KOMEConquestTile.normalizeId(record.currentTile);
                migratedPopulationData = true;
            }
            if (record.sourceFaction == null || record.sourceFaction.length() == 0) {
                List<KOMETilePopulation> sourcePools = getTilePopulationPools(record.sourceTileId);
                KOMEConquestTile sourceTile = conquestTiles.get(record.sourceTileId);
                String owner = sourceTile == null ? "" : KOMEAlliance.normalizeFactionKey(sourceTile.currentRulingFaction());
                KOMETilePopulation ownerPool = getTilePopulationPool(record.sourceTileId, owner);
                if (ownerPool != null) {
                    record.sourceFaction = ownerPool.sourceFaction;
                } else if (sourcePools.size() == 1) {
                    record.sourceFaction = sourcePools.get(0).sourceFaction;
                }
                migratedPopulationData = true;
            }
            if (!KOMEHiredUnitRecord.SOURCE_PLAYER_RESERVE.equals(record.sourceType)) {
                record.sourceType = KOMEHiredUnitRecord.SOURCE_TILE_POOL;
            }
            if (record.sourcePlayer == null) {
                record.sourcePlayer = record.owner;
            }
            if (record.sourceType.equals(KOMEHiredUnitRecord.SOURCE_TILE_POOL) && getFundingPool(record) == null) {
                record.sourceType = KOMEHiredUnitRecord.SOURCE_PLAYER_RESERVE;
                migratedPopulationData = true;
            }
            if (record.sourceType.equals(KOMEHiredUnitRecord.SOURCE_TILE_POOL) && record.allocationPlayer == null) {
                KOMEConquestTile sourceTile = conquestTiles.get(KOMEConquestTile.normalizeId(record.sourceTileId));
                String playerFaction = getPlayerFactionKey(record.owner);
                if (sourceTile != null && KOMEAlliance.normalizeFactionKey(sourceTile.currentRulingFaction()).equals(KOMEAlliance.normalizeFactionKey(playerFaction))) {
                    record.allocationTileId = sourceTile.id;
                    record.allocationFaction = KOMEAlliance.normalizeFactionKey(sourceTile.currentRulingFaction());
                    record.allocationPlayer = record.owner;
                    migratedPopulationData = true;
                }
            }
        }

        for (Map.Entry<UUID, KOMEPlayerPopulation> entry : populations.entrySet()) {
            entry.getValue().setUsed(KOMEPopulationType.OFFENSIVE, getPlayerReservePopulationUsed(entry.getKey(), KOMEPopulationType.OFFENSIVE));
            entry.getValue().setUsed(KOMEPopulationType.DEFENSIVE, getPlayerReservePopulationUsed(entry.getKey(), KOMEPopulationType.DEFENSIVE));
        }

        for (KOMEPlayerTilePopulationAllocation allocation : populationAllocations.values()) {
            allocation.release(KOMEPopulationType.OFFENSIVE, allocation.offensiveUsed);
            allocation.release(KOMEPopulationType.DEFENSIVE, allocation.defensiveUsed);
        }
        for (KOMEHiredUnitRecord record : hiredUnits.values()) {
            if (record != null && !record.farmhand && !record.isPlayerReserveFunded() && record.allocationPlayer != null) {
                KOMEPlayerTilePopulationAllocation allocation = getOrCreateAllocation(record.allocationTileId, record.allocationFaction, record.allocationPlayer, playerNames.get(record.allocationPlayer));
                allocation.setAllocated(record.type, Math.max(allocation.getAllocated(record.type), allocation.getUsed(record.type) + record.cost));
                allocation.addUsed(record.type, record.cost);
            }
        }

        for (KOMEConquestTile tile : conquestTiles.values()) {
            if (tile != null && tile.isClaimed() && !hasFactionKing(tile.currentRulingFaction()) && tile.claimedByUuid == null) {
                List<KOMEPlayerTilePopulationAllocation> allocations = getAllocationsForTile(tile.id, tile.currentRulingFaction());
                if (allocations.size() == 1) {
                    KOMEPlayerTilePopulationAllocation allocation = allocations.get(0);
                    tile.claimedByUuid = allocation.playerUuid;
                    tile.claimedByName = allocation.playerName;
                    migratedPopulationData = true;
                }
            }
            reconcileClaimantAllocation(tile);
        }

        NBTTagList allianceList = nbt.getTagList("Alliances", 10);
        for (int i = 0; i < allianceList.tagCount(); i++) {
            KOMEAlliance alliance = new KOMEAlliance("", "");
            alliance.readFromNBT(allianceList.getCompoundTagAt(i));
            if (alliance.factionA.length() > 0 && alliance.factionB.length() > 0 && alliance.hasAnyAlliance()) {
                alliances.put(KOMEAlliance.directionKey(alliance.factionA, alliance.factionB), alliance);
            }
        }

        NBTTagList movementList = nbt.getTagList("ArmyMovements", 10);
        for (int i = 0; i < movementList.tagCount(); i++) {
            KOMEArmyMovementOrder order = new KOMEArmyMovementOrder();
            order.readFromNBT(movementList.getCompoundTagAt(i));
            if (order.id.length() > 0) {
                armyMovements.put(order.id, order);
            }
        }

        NBTTagList historyList = nbt.getTagList("MovementHistory", 10);
        for (int i = 0; i < historyList.tagCount(); i++) {
            KOMEMovementHistoryRecord record = new KOMEMovementHistoryRecord();
            record.readFromNBT(historyList.getCompoundTagAt(i));
            if (record.historyId.length() > 0) {
                movementHistory.put(record.historyId, record);
            }
        }

        NBTTagList companyList = nbt.getTagList("ArmyCompanies", 10);
        for (int i = 0; i < companyList.tagCount(); i++) {
            KOMEArmyCompany company = new KOMEArmyCompany();
            company.readFromNBT(companyList.getCompoundTagAt(i));
            if (company.id.length() > 0 && company.owner != null) {
                armyCompanies.put(company.id, company);
            }
        }
        for (KOMEArmyCompany company : armyCompanies.values()) {
            List<UUID> missingUnits = new ArrayList<UUID>();
            for (UUID unitId : company.units) {
                KOMEHiredUnitRecord record = hiredUnits.get(unitId);
                if (record == null || record.farmhand || record.type != KOMEPopulationType.OFFENSIVE) {
                    missingUnits.add(unitId);
                } else {
                    record.companyId = company.id;
                }
            }
            company.units.removeAll(missingUnits);
        }
        for (KOMEHiredUnitRecord record : hiredUnits.values()) {
            if (record != null && record.companyId != null && record.companyId.length() > 0
                    && !armyCompanies.containsKey(record.companyId)) {
                record.companyId = "";
                migratedPopulationData = true;
            }
        }

        if (pruneRetiredConquestTileData()) {
            migratedPopulationData = true;
        }

        if (applyWaypointDefaults(!conquestDefaultsInitialized)) {
            migratedPopulationData = true;
        }

        if (migratedPopulationData) {
            markDirty();
        }
    }

    private boolean pruneRetiredConquestTileData() {
        boolean changed = false;
        for (String tileId : new ArrayList<String>(conquestTiles.keySet())) {
            if (KOMEConquestTileDefaults.isRetiredTile(tileId)) {
                conquestTiles.remove(tileId);
                changed = true;
            }
        }
        for (String key : new ArrayList<String>(tilePopulations.keySet())) {
            KOMETilePopulation population = tilePopulations.get(key);
            if (population != null && KOMEConquestTileDefaults.isRetiredTile(population.tileId)) {
                tilePopulations.remove(key);
                changed = true;
            }
        }
        for (String key : new ArrayList<String>(populationAllocations.keySet())) {
            KOMEPlayerTilePopulationAllocation allocation = populationAllocations.get(key);
            if (allocation != null && KOMEConquestTileDefaults.isRetiredTile(allocation.tileId)) {
                populationAllocations.remove(key);
                changed = true;
            }
        }
        for (String key : new ArrayList<String>(activeRecruitmentTiles.keySet())) {
            if (KOMEConquestTileDefaults.isRetiredTile(activeRecruitmentTiles.get(key))) {
                activeRecruitmentTiles.remove(key);
                changed = true;
            }
        }
        for (String key : new ArrayList<String>(tileWaypoints.keySet())) {
            KOMETileWaypoint waypoint = tileWaypoints.get(key);
            if (waypoint != null && KOMEConquestTileDefaults.isRetiredTile(waypoint.tileId)) {
                tileWaypoints.remove(key);
                changed = true;
            }
        }
        for (String key : new ArrayList<String>(tileWaypointLinksByTileId.keySet())) {
            if (KOMEConquestTileDefaults.isRetiredTile(key)) {
                tileWaypointLinksByTileId.remove(key);
                changed = true;
            }
        }
        for (String key : new ArrayList<String>(routeEdges.keySet())) {
            KOMEConquestRouteEdge edge = routeEdges.get(key);
            if (edge != null && (KOMEConquestTileDefaults.isRetiredTile(edge.fromTile)
                    || KOMEConquestTileDefaults.isRetiredTile(edge.toTile))) {
                routeEdges.remove(key);
                changed = true;
            }
        }
        return changed;
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        nbt.setBoolean("ProgressionEnabled", progressionEnabled);
        nbt.setInteger("MovementSecondsPerTileOverride", Math.max(0, movementSecondsPerTileOverride));
        nbt.setInteger("MovementTotalSecondsOverride", Math.max(0, movementTotalSecondsOverride));
        nbt.setInteger("MovementStepDelaySeconds", Math.max(0, movementStepDelaySeconds));
        nbt.setString("MovementDailyResetTime", movementDailyResetTime == null ? "20:00" : movementDailyResetTime);
        nbt.setString("MovementDailyResetTimezone", movementDailyResetTimezone == null ? "America/Chicago" : movementDailyResetTimezone);
        nbt.setBoolean("ConquestDefaultsInitialized", conquestDefaultsInitialized);

        NBTTagList popList = new NBTTagList();
        for (Map.Entry<UUID, KOMEPlayerPopulation> entry : populations.entrySet()) {
            NBTTagCompound pop = entry.getValue().writeToNBT();
            pop.setString("Player", entry.getKey().toString());
            popList.appendTag(pop);
        }
        nbt.setTag("Populations", popList);

        NBTTagList progressionList = new NBTTagList();
        for (Map.Entry<UUID, KOMEPlayerProgression> entry : progressions.entrySet()) {
            NBTTagCompound progression = entry.getValue().writeToNBT();
            progression.setString("Player", entry.getKey().toString());
            progressionList.appendTag(progression);
        }
        nbt.setTag("Progressions", progressionList);

        NBTTagList playerNameList = new NBTTagList();
        for (Map.Entry<UUID, String> entry : playerNames.entrySet()) {
            NBTTagCompound playerName = new NBTTagCompound();
            playerName.setString("Player", entry.getKey().toString());
            playerName.setString("Name", entry.getValue() == null ? "" : entry.getValue());
            playerNameList.appendTag(playerName);
        }
        nbt.setTag("PlayerNames", playerNameList);

        NBTTagList adminMarkerList = new NBTTagList();
        for (UUID playerId : adminUnitMapMarkerOptOuts) {
            if (playerId != null) {
                NBTTagCompound entry = new NBTTagCompound();
                entry.setString("Player", playerId.toString());
                adminMarkerList.appendTag(entry);
            }
        }
        nbt.setTag("AdminUnitMapMarkerOptOuts", adminMarkerList);

        NBTTagList hiredList = new NBTTagList();
        for (KOMEHiredUnitRecord record : hiredUnits.values()) {
            hiredList.appendTag(record.writeToNBT());
        }
        nbt.setTag("HiredUnits", hiredList);

        NBTTagList conquestList = new NBTTagList();
        for (KOMEConquestTile tile : conquestTiles.values()) {
            conquestList.appendTag(tile.writeToNBT());
        }
        nbt.setTag("ConquestTiles", conquestList);

        NBTTagList tilePopulationList = new NBTTagList();
        for (KOMETilePopulation population : tilePopulations.values()) {
            if (population != null && population.tileId != null && population.tileId.length() > 0) {
                tilePopulationList.appendTag(population.writeToNBT());
            }
        }
        nbt.setTag("TilePopulations", tilePopulationList);

        NBTTagList allocationList = new NBTTagList();
        for (KOMEPlayerTilePopulationAllocation allocation : populationAllocations.values()) {
            if (allocation != null && allocation.playerUuid != null) {
                allocationList.appendTag(allocation.writeToNBT());
            }
        }
        nbt.setTag("PopulationAllocations", allocationList);

        NBTTagList recruitmentTileList = new NBTTagList();
        for (Map.Entry<String, String> entry : activeRecruitmentTiles.entrySet()) {
            String[] key = entry.getKey().split("\\|", 2);
            if (key.length != 2 || key[0].length() == 0 || key[1].length() == 0) {
                continue;
            }
            NBTTagCompound recruitmentTile = new NBTTagCompound();
            recruitmentTile.setString("Faction", key[0]);
            recruitmentTile.setString("Player", key[1]);
            recruitmentTile.setString("Tile", KOMEConquestTile.normalizeId(entry.getValue()));
            recruitmentTileList.appendTag(recruitmentTile);
        }
        nbt.setTag("ActiveRecruitmentTiles", recruitmentTileList);

        NBTTagList waypointList = new NBTTagList();
        for (KOMETileWaypoint waypoint : tileWaypoints.values()) {
            if (waypoint != null && waypoint.tileId != null && waypoint.tileId.length() > 0) {
                waypointList.appendTag(waypoint.writeToNBT());
            }
        }
        nbt.setTag("TileWaypoints", waypointList);

        NBTTagList tileWaypointLinkList = new NBTTagList();
        for (KOMETileWaypointLink link : tileWaypointLinksByTileId.values()) {
            if (link != null && link.tileId != null && link.tileId.length() > 0 && link.lotrWaypointKey != null && link.lotrWaypointKey.length() > 0) {
                tileWaypointLinkList.appendTag(link.writeToNBT());
            }
        }
        nbt.setTag("TileWaypointLinks", tileWaypointLinkList);

        NBTTagList routeEdgeList = new NBTTagList();
        for (KOMEConquestRouteEdge edge : routeEdges.values()) {
            if (edge != null && edge.fromTile != null && edge.toTile != null
                    && edge.fromTile.length() > 0 && edge.toTile.length() > 0) {
                routeEdgeList.appendTag(edge.writeToNBT());
            }
        }
        nbt.setTag("RouteEdges", routeEdgeList);
        NBTTagList allianceList = new NBTTagList();
        for (KOMEAlliance alliance : alliances.values()) {
            if (alliance != null && alliance.hasAnyAlliance()) {
                allianceList.appendTag(alliance.writeToNBT());
            }
        }
        nbt.setTag("Alliances", allianceList);

        NBTTagList movementList = new NBTTagList();
        for (KOMEArmyMovementOrder order : armyMovements.values()) {
            if (order != null && order.id != null && order.id.length() > 0) {
                movementList.appendTag(order.writeToNBT());
            }
        }
        nbt.setTag("ArmyMovements", movementList);

        NBTTagList historyList = new NBTTagList();
        for (KOMEMovementHistoryRecord record : movementHistory.values()) {
            if (record != null && record.historyId != null && record.historyId.length() > 0) {
                historyList.appendTag(record.writeToNBT());
            }
        }
        nbt.setTag("MovementHistory", historyList);

        NBTTagList companyList = new NBTTagList();
        for (KOMEArmyCompany company : armyCompanies.values()) {
            if (company != null && company.id != null && company.id.length() > 0) {
                companyList.appendTag(company.writeToNBT());
            }
        }
        nbt.setTag("ArmyCompanies", companyList);

        NBTTagList kingList = new NBTTagList();
        for (Map.Entry<String, UUID> entry : kingsByFaction.entrySet()) {
            NBTTagCompound king = new NBTTagCompound();
            king.setString("Faction", entry.getKey());
            king.setString("Player", entry.getValue().toString());
            String name = kingNamesByFaction.get(entry.getKey());
            king.setString("Name", name == null ? "" : name);
            kingList.appendTag(king);
        }
        nbt.setTag("FactionKings", kingList);
    }
}
