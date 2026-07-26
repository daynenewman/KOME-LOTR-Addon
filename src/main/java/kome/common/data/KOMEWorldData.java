package kome.common.data;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.FMLLog;
import kome.common.KOMEReflection;
import kome.common.command.KOMECommandAlliance;
import kome.common.command.KOMECommandTroops;
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
    public static final int ALLIANCE_DATA_SCHEMA_VERSION = KOMEAlliance.DATA_SCHEMA_VERSION;
    public static final int BUILD_DATA_SCHEMA_VERSION = 1;
    public static final int POPULATION_DATA_SCHEMA_VERSION = 2;

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
    public final Map<String, KOMEPlayerBuild> builds = new HashMap<String, KOMEPlayerBuild>();
    public final Map<String, KOMEAlliance> alliances = new HashMap<>();
    public final Set<String> recoveredLegacyTradePostIds = new HashSet<String>();
    public final List<NBTTagCompound> quarantinedTradePostRecords = new ArrayList<NBTTagCompound>();
    public final Map<String, KOMEWar> wars = new HashMap<String, KOMEWar>();
    public final Map<UUID, KOMEClaimConfirmation> conquestClaimConfirmations = new HashMap<UUID, KOMEClaimConfirmation>();
    /** Last observed base-LOTR pledge; deliberately distinct from KOME's progression lord record. */
    public final Map<UUID, String> lastKnownPlayerFactions = new HashMap<UUID, String>();
    public final Map<UUID, KOMEPledgeReleaseTombstone> pledgeReleaseTombstones = new HashMap<UUID, KOMEPledgeReleaseTombstone>();
    public final Map<UUID, NBTTagCompound> pledgeReleaseQuarantine = new HashMap<UUID, NBTTagCompound>();
    public final Map<UUID, String> pledgeReleaseLastResults = new HashMap<UUID, String>();
    public final List<String> pledgeReleaseAudit = new ArrayList<String>();
    public final Map<String, Integer> allianceRequirementOverrides = new HashMap<String, Integer>();
    public final Map<String, Integer> allianceQuotaWeightOverrides = new HashMap<String, Integer>();
    public final Map<String, Integer> allianceQuotaMaximumOverrides = new HashMap<String, Integer>();
    public final Map<String, Boolean> allianceQuotaEnabledOverrides = new HashMap<String, Boolean>();
    public final Set<UUID> waypointRestrictionBypasses = new HashSet<UUID>();
    public final List<String> allianceAdminAudit = new ArrayList<String>();
    public final List<NBTTagCompound> quarantinedAllianceRecords = new ArrayList<NBTTagCompound>();
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
    public int nextWarSequence = 1;
    public int nextBuildSequence = 1;
    public int buildPopulationPerHalfHour = KOMEBuildPopulationService.DEFAULT_POPULATION_PER_HALF_HOUR;
    public int allianceStageThreeRequiredHalfHours = KOMEBuildPopulationService.DEFAULT_STAGE_THREE_REQUIRED_HALF_HOURS;
    public String allianceDifficulty = KOMEAllianceRequirements.STANDARD;
    /**
     * Retained only for wire/save compatibility with schema 5-6 clients.  KOME no longer gates
     * LOTR waypoint travel by alliance or conquest ownership and this value is always false.
     */
    public boolean waypointRestrictionEnabled = false;
    public long successionGraceDefaultMillis = KOMEAllianceAuthority.FOURTEEN_DAYS_MILLIS;
    public long contributionGraceDefaultMillis = KOMEAllianceAuthority.FOURTEEN_DAYS_MILLIS;
    public static final int MAX_MOVEMENT_HISTORY_PER_FACTION = 250;
    private boolean conquestDefaultsInitialized;
    private boolean allianceRelationsNeedReapply;

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

    public int getAllianceItemStackEquivalents(String type, int tier) {
        return requirementBase(type, tier, "items", KOMEAllianceRequirements.standardItemStackEquivalents(type, tier));
    }

    public int getAllianceActivityRequirement(String type, int tier) {
        int base = requirementBase(type, tier, "activity", KOMEAllianceRequirements.standardActivity(type, tier));
        if (KOMEAlliance.TRADE.equals(KOMEAlliance.normalizeType(type)) && tier == 2) {
            return Math.max(1, base);
        }
        return KOMEAllianceRequirements.scaled(base, allianceDifficulty);
    }

    public int getAlliancePopulationRequirement(String type, int tier) {
        return Math.max(0, requirementBase(type, tier, "population", KOMEAllianceRequirements.standardPopulation(type, tier)));
    }

    public void setAllianceRequirement(String type, int tier, String kind, int value) {
        allianceRequirementOverrides.put(KOMEAllianceRequirements.key(type, tier, kind), Integer.valueOf(Math.max(0, value)));
        markDirty();
    }

    public int getAllianceQuotaWeight(String key, int defaultValue) {
        Integer configured = allianceQuotaWeightOverrides.get(normalizeQuotaKey(key));
        return configured == null ? KOMEAllianceRequirements.normalizeWeight(defaultValue)
            : KOMEAllianceRequirements.normalizeWeight(configured.intValue());
    }

    public int getAllianceQuotaMaximum(String key, int defaultValue) {
        Integer configured = allianceQuotaMaximumOverrides.get(normalizeQuotaKey(key));
        return configured == null ? Math.max(1, defaultValue) : Math.max(1, configured.intValue());
    }

    public boolean isAllianceQuotaItemEnabled(String key, boolean defaultValue) {
        Boolean configured = allianceQuotaEnabledOverrides.get(normalizeQuotaKey(key));
        return configured == null ? defaultValue : configured.booleanValue();
    }

    public void setAllianceQuotaWeight(String key, int value) {
        if (!KOMEAllianceRequirements.isSupportedWeight(value)) {
            throw new IllegalArgumentException("Quota weight must be 1, 2, 4, 8, 16, 32, or 64.");
        }
        allianceQuotaWeightOverrides.put(normalizeQuotaKey(key), Integer.valueOf(value));
        markDirty();
    }

    public void setAllianceQuotaMaximum(String key, int value) {
        if (value <= 0) {
            throw new IllegalArgumentException("Quota maximum must be positive.");
        }
        allianceQuotaMaximumOverrides.put(normalizeQuotaKey(key), Integer.valueOf(value));
        markDirty();
    }

    public void setAllianceQuotaItemEnabled(String key, boolean enabled) {
        allianceQuotaEnabledOverrides.put(normalizeQuotaKey(key), Boolean.valueOf(enabled));
        markDirty();
    }

    private static String normalizeQuotaKey(String key) {
        return key == null ? "" : key.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public int getFactionEffectiveOffensiveCapacity(String faction) {
        return Math.max(0, getFactionEffectivePopulationSummary(faction).offensiveTotal);
    }

    public boolean hasWaypointRestrictionBypass(UUID playerId) {
        return playerId != null && waypointRestrictionBypasses.contains(playerId);
    }

    public void setWaypointRestrictionBypass(UUID playerId, boolean enabled) {
        if (playerId == null) {
            return;
        }
        if (enabled ? waypointRestrictionBypasses.add(playerId) : waypointRestrictionBypasses.remove(playerId)) {
            markDirty();
        }
    }

    public void recordAllianceAdminAction(String actor, String action) {
        String entry = System.currentTimeMillis() + "|" + (actor == null ? "" : actor.replace('|', ' ')) + "|"
            + (action == null ? "" : action.replace('|', ' '));
        allianceAdminAudit.add(entry);
        while (allianceAdminAudit.size() > 200) {
            allianceAdminAudit.remove(0);
        }
        markDirty();
    }

    private int requirementBase(String type, int tier, String kind, int standard) {
        Integer configured = allianceRequirementOverrides.get(KOMEAllianceRequirements.key(type, tier, kind));
        return configured == null ? Math.max(0, standard) : Math.max(0, configured.intValue());
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
        // Ownership reset changes access, never provenance.  Every existing native/Build pool
        // remains keyed to its source faction so the new controller receives the deterministic
        // 100% (own) or 50% (foreign) effective amount without merging or erasing history.
        for (KOMETilePopulation population : tilePopulations.values()) {
            if (population == null || !resetTiles.contains(KOMEConquestTile.normalizeId(population.tileId))) continue;
            population.offensiveUsed = Math.min(Math.max(0, population.offensiveUsed), Math.max(0, population.offensiveTotal));
            population.defensiveUsed = Math.min(Math.max(0, population.defensiveUsed), Math.max(0, population.defensiveTotal));
            population.farmhandUsed = Math.min(Math.max(0, population.farmhandUsed), Math.max(0, population.farmhandTotal));
        }
        for (KOMEPlayerBuild build : builds.values()) {
            if (build != null && build.active
                    && resetTiles.contains(KOMEConquestTile.normalizeId(build.tileId))) {
                recalculateBuildPopulationPool(build.tileId, build.populationFaction);
            }
        }
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

    public int getKinglessStewardshipAvailable(String factionKey) {
        return Math.max(0, getKinglessStewardshipGlobalCap(factionKey) - getKinglessStewardshipReserved(factionKey));
    }

    public int getKinglessStewardshipGlobalCap(String factionKey) {
        int reserved = getKinglessStewardshipReserved(factionKey);
        return Math.max(0, getKinglessStewardshipUnallocated(factionKey) + reserved);
    }

    public int getKinglessStewardshipReserved(String factionKey) {
        String faction = normalizeFactionKey(factionKey);
        int reserved = 0;
        for (KOMEHiredUnitRecord record : hiredUnits.values()) {
            if (record != null && "MILITARY_T3_STEWARDSHIP".equals(record.benefitSource)
                    && faction.equals(normalizeFactionKey(record.populationOwningFaction))) {
                reserved += Math.max(0, record.cost);
            }
        }
        return reserved;
    }

    public int getKinglessStewardshipUnallocated(String factionKey) {
        String faction = normalizeFactionKey(factionKey);
        int unallocated = 0;
        for (KOMEConquestTile tile : conquestTiles.values()) {
            if (tile == null || !tile.isClaimed() || !faction.equals(normalizeFactionKey(tile.currentRulingFaction()))) {
                continue;
            }
            int effectiveTotal = 0;
            int effectiveUsed = 0;
            for (KOMETilePopulation pool : getTilePopulationPools(tile.id)) {
                // Wartime stewardship may mobilize only population native to the kingless
                // faction. Captured/foreign source pools retain their ordinary partial-control
                // rules but are deliberately outside the stewardship allowance.
                if (!faction.equals(normalizeFactionKey(pool.sourceFaction))) continue;
                effectiveTotal += pool.getEffectiveTotal(KOMEPopulationType.OFFENSIVE, faction);
                effectiveUsed += pool.getUsed(KOMEPopulationType.OFFENSIVE);
            }
            int allocated = getTotalAllocated(tile.id, faction, KOMEPopulationType.OFFENSIVE);
            int allocatedUsed = getTotalAllocationUsed(tile.id, faction, KOMEPopulationType.OFFENSIVE);
            int unspentAllocation = Math.max(0, allocated - allocatedUsed);
            unallocated += Math.max(0, effectiveTotal - effectiveUsed - unspentAllocation);
        }
        return unallocated;
    }

    public KOMETilePopulation findKinglessStewardshipPool(String factionKey, int amount) {
        String faction = normalizeFactionKey(factionKey);
        List<String> ids = new ArrayList<String>(conquestTiles.keySet());
        Collections.sort(ids);
        for (String id : ids) {
            KOMEConquestTile tile = conquestTiles.get(id);
            if (tile == null || !tile.isClaimed() || !faction.equals(normalizeFactionKey(tile.currentRulingFaction()))) {
                continue;
            }
            int allocated = getTotalAllocated(id, faction, KOMEPopulationType.OFFENSIVE);
            int allocatedUsed = getTotalAllocationUsed(id, faction, KOMEPopulationType.OFFENSIVE);
            int unspentAllocation = Math.max(0, allocated - allocatedUsed);
            for (KOMETilePopulation pool : getTilePopulationPools(id)) {
                if (!faction.equals(normalizeFactionKey(pool.sourceFaction))) continue;
                int available = Math.max(0, pool.getEffectiveTotal(KOMEPopulationType.OFFENSIVE, faction)
                    - pool.getUsed(KOMEPopulationType.OFFENSIVE));
                int excluded = Math.min(available, unspentAllocation);
                unspentAllocation -= excluded;
                if (available - excluded >= amount) {
                    return pool;
                }
            }
        }
        return null;
    }

    private int getTotalAllocationUsed(String tileId, String faction, KOMEPopulationType type) {
        int used = 0;
        for (KOMEPlayerTilePopulationAllocation allocation : getAllocationsForTile(tileId, faction)) {
            used += allocation.getUsed(type);
        }
        return used;
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
        int buildPopulation = getBuildPopulationTotal(tileId, population.sourceFaction, type);
        int nativeTotal = Math.max(0, nextTotal - buildPopulation);
        if (type == KOMEPopulationType.DEFENSIVE) {
            population.nativeDefensiveTotal = nativeTotal;
        } else {
            population.nativeOffensiveTotal = nativeTotal;
        }
        population.nativeBaselineInitialized = true;
        reconcileClaimantAllocation(tile);
        markDirty();
        return true;
    }

    public String nextBuildId() {
        String id;
        do {
            id = "B" + Math.max(1, nextBuildSequence++);
        } while (builds.containsKey(id));
        markDirty();
        return id;
    }

    public String nextBuildContributionId(KOMEPlayerBuild build) {
        int next = build == null ? 1 : build.contributions.size() + 1;
        String id;
        do {
            id = "H" + next++;
        } while (build != null && build.getContribution(id) != null);
        return id;
    }

    public KOMEPlayerBuild getBuild(String buildId) {
        return builds.get(buildId == null ? "" : buildId.trim().toUpperCase(java.util.Locale.ROOT));
    }

    public int getBuildPopulationTotal(String tileId, String populationFaction, KOMEPopulationType type) {
        int total = 0;
        String tile = KOMEConquestTile.normalizeId(tileId);
        String faction = normalizeFactionKey(populationFaction);
        for (KOMEPlayerBuild build : builds.values()) {
            if (build != null && build.active && tile.equals(build.tileId)
                    && faction.equals(normalizeFactionKey(build.populationFaction))) {
                total += build.approvedPopulation(type, buildPopulationPerHalfHour);
            }
        }
        return Math.max(0, total);
    }

    public int getNativePopulationTotal(String tileId, String populationFaction, KOMEPopulationType type) {
        KOMETilePopulation population = getTilePopulationPool(tileId, populationFaction);
        if (population == null) return 0;
        return type == KOMEPopulationType.DEFENSIVE
            ? Math.max(0, population.nativeDefensiveTotal) : Math.max(0, population.nativeOffensiveTotal);
    }

    public void recalculateBuildPopulationPool(String tileId, String populationFaction) {
        String tile = KOMEConquestTile.normalizeId(tileId);
        String faction = normalizeFactionKey(populationFaction);
        if (tile.length() == 0 || faction.length() == 0) return;
        KOMETilePopulation population = getOrCreateTilePopulationPool(tile, faction);
        if (!population.nativeBaselineInitialized) {
            population.nativeOffensiveTotal = Math.max(0, population.offensiveTotal);
            population.nativeDefensiveTotal = Math.max(0, population.defensiveTotal);
            population.nativeBaselineInitialized = true;
        }
        population.setTotal(KOMEPopulationType.OFFENSIVE, saturatedAdd(population.nativeOffensiveTotal,
            getBuildPopulationTotal(tile, faction, KOMEPopulationType.OFFENSIVE)));
        population.setTotal(KOMEPopulationType.DEFENSIVE, saturatedAdd(population.nativeDefensiveTotal,
            getBuildPopulationTotal(tile, faction, KOMEPopulationType.DEFENSIVE)));
        reconcileClaimantAllocation(conquestTiles.get(tile));
        markDirty();
    }

    public String assignFundingBuild(String tileId, String populationFaction, KOMEPopulationType type,
            int populationCost, String controllingFaction) {
        String tile = KOMEConquestTile.normalizeId(tileId);
        String faction = normalizeFactionKey(populationFaction);
        boolean fullyUsable = faction.equals(normalizeFactionKey(controllingFaction));
        List<KOMEPlayerBuild> candidates = KOMEBuildService.buildsInTile(this, tile, false);
        for (KOMEPlayerBuild build : candidates) {
            if (build != null && faction.equals(normalizeFactionKey(build.populationFaction))
                    && build.availablePopulation(type, buildPopulationPerHalfHour, fullyUsable) >= populationCost) {
                build.adjustCommitted(type, populationCost);
                build.updatedAtMillis = System.currentTimeMillis();
                markDirty();
                return build.id;
            }
        }
        return "";
    }

    public void releaseFundingBuild(KOMEHiredUnitRecord record) {
        if (record == null || record.sourceBuildId == null || record.sourceBuildId.length() == 0) return;
        KOMEPlayerBuild build = getBuild(record.sourceBuildId);
        if (build != null) {
            build.adjustCommitted(record.type, -Math.max(0, record.cost));
            build.updatedAtMillis = System.currentTimeMillis();
            markDirty();
        }
    }

    public void reconcileBuildManagers() {
        for (KOMEPlayerBuild build : builds.values()) {
            KOMEBuildService.reconcileManager(this, build);
        }
    }

    /**
     * Hired-unit funding records are authoritative. Rebuild cached commitments
     * after load so stale cache values cannot make destructive Build actions unsafe.
     */
    public void reconcileBuildCommitments() {
        for (KOMEPlayerBuild build : builds.values()) {
            if (build != null) build.clearCommittedPopulation();
        }
        for (KOMEHiredUnitRecord record : hiredUnits.values()) {
            if (record == null || record.populationReturned || record.sourceBuildId == null
                    || record.sourceBuildId.length() == 0) continue;
            KOMEPlayerBuild build = getBuild(record.sourceBuildId);
            if (build != null) build.adjustCommitted(record.type, Math.max(0, record.cost));
        }
    }

    private static int saturatedAdd(int left, int right) {
        long value = (long) Math.max(0, left) + Math.max(0, right);
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    public KOMEAlliance getAlliance(String factionA, String factionB, boolean create) {
        String first = KOMEAlliance.normalizeFactionKey(factionA);
        String second = KOMEAlliance.normalizeFactionKey(factionB);
        if (first.length() == 0 || second.length() == 0 || first.equals(second)) {
            return null;
        }
        String key = KOMEAlliance.pairKey(first, second);
        KOMEAlliance alliance = alliances.get(key);
        if (alliance == null && create) {
            alliance = new KOMEAlliance(first, second);
            alliances.put(key, alliance);
        }
        return alliance;
    }

    public int getAllianceTier(String type, String factionA, String factionB) {
        KOMEAlliance alliance = getAlliance(factionA, factionB, false);
        return alliance == null ? -1 : alliance.getFactionTier(factionA, KOMEAlliance.normalizeType(type));
    }

    public boolean hasProduceMerchantSlot(String faction, String partnerFaction) {
        KOMEAlliance alliance = getAlliance(faction, partnerFaction, false);
        return alliance != null && alliance.hasProduceMerchantSlot(faction);
    }

    public List<String> getUnlockedMerchantPartners(String faction) {
        String acting = normalizeFactionKey(faction);
        List<String> result = new ArrayList<String>();
        for (KOMEAlliance alliance : alliances.values()) {
            if (alliance != null && alliance.involves(acting) && alliance.hasProduceMerchantSlot(acting)) {
                String partner = alliance.getOtherFaction(acting);
                if (partner.length() > 0 && !result.contains(partner)) result.add(partner);
            }
        }
        Collections.sort(result);
        return result;
    }

    public boolean clearAlliance(String factionA, String factionB) {
        String key = KOMEAlliance.pairKey(factionA, factionB);
        KOMEAlliance alliance = alliances.get(key);
        boolean changed = alliance != null && alliance.hasPersistentData();
        if (alliance != null) {
            alliance.clearAllTracks("Alliance cleared", 0L);
            if (!alliance.hasRecoverableGoods() && !alliance.hasPersistentEntitlements()) {
                alliances.remove(key);
            }
        }
        if (changed) {
            long nowMillis = System.currentTimeMillis();
            KOMEWarService.reconcileAutomaticMilitarySupport(this, nowMillis, "Alliance cleared");
            KOMECommandTroops.revalidateTemporaryControllers(this, nowMillis, "Alliance cleared");
            markDirty();
        }
        return changed;
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
        return new KOMEAllianceAuthority(this).canFactionUseMilitaryPassage(moving, owner);
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
            onFactionKingLost(staleFaction, System.currentTimeMillis());
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
            onFactionKingGained(key, System.currentTimeMillis());
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

    public UUID getFactionKingId(String factionKey) {
        return kingsByFaction.get(normalizeFactionKey(factionKey));
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

    public void onFactionKingLost(String factionKey, long nowMillis) {
        String faction = normalizeFactionKey(factionKey);
        if (faction.length() == 0) {
            return;
        }
        reconcileBuildManagers();
        KOMEWarService.reconcileAutomaticMilitarySupport(this, nowMillis, "Faction became kingless");
        KOMEWartimeStewardshipService.revalidateAll(this, nowMillis, "Recognized king status changed");
        KOMECommandTroops.revalidateTemporaryControllers(this, nowMillis, "Recognized king status changed");
    }

    public void onFactionKingGained(String factionKey, long nowMillis) {
        String faction = normalizeFactionKey(factionKey);
        if (faction.length() == 0) {
            return;
        }
        reconcileBuildManagers();
        KOMEWarService.reconcileAutomaticMilitarySupport(this, nowMillis, "Faction gained a recognized king");
        KOMEWartimeStewardshipService.revalidateAll(this, nowMillis, "Recognized king status changed");
        KOMECommandTroops.revalidateTemporaryControllers(this, nowMillis, "Recognized king status changed");
    }

    public boolean reconcileAllianceLifecycle(long nowMillis, long worldTime) {
        if (allianceRelationsNeedReapply) {
            KOMECommandAlliance.reapplyAllAllianceRelations(this);
            allianceRelationsNeedReapply = false;
        }
        // Schema 7 deliberately has no king-loss or contribution grace lifecycle.  Losing or
        // gaining a king preserves every directional stage and unlocked benefit.
        return false;
    }

    private void downgradeToCompletedTiers(KOMEAlliance alliance, String faction, long worldTime) {
        KOMEAllianceFactionLedger ledger = alliance.getFactionLedger(faction);
        if (ledger == null) {
            return;
        }
        String[] types = new String[] {KOMEAlliance.CIVIL, KOMEAlliance.TRADE, KOMEAlliance.MILITARY};
        for (int i = 0; i < types.length; i++) {
            String type = types[i];
            if (!alliance.hasAccepted(type)) {
                continue;
            }
            int completed = Math.max(0, Math.min(KOMEAlliance.maxTier(type), ledger.getCompletedTier(type)));
            if (completed < alliance.getFactionTier(faction, type)) {
                alliance.setFactionTier(faction, type, completed, "Contribution grace expired", worldTime);
            }
        }
    }

    void expireContributionSide(KOMEAlliance alliance, String affectedFaction, long worldTime) {
        KOMEAllianceFactionLedger ledger = KOMEAllianceGraceService.requireLedger(alliance, affectedFaction);
        ledger.clearContributionGrace();
        downgradeToCompletedTiers(alliance, affectedFaction, worldTime);
        KOMECommandAlliance.syncRelationsForAlliancePair(this, alliance.factionA, alliance.factionB);
        markDirty();
    }

    private void capAllianceAfterSuccession(KOMEAlliance alliance, String kinglessFaction, long worldTime) {
        String otherFaction = alliance.getOtherFaction(kinglessFaction);
        lotr.common.fac.LOTRFactionRelations.Relation relation = KOMEAllianceAuthority.getDefaultRelation(kinglessFaction, otherFaction);
        applySuccessionRelationCap(alliance, relation, worldTime);
        KOMEAllianceFactionLedger ledger = alliance.getFactionLedger(kinglessFaction);
        if (ledger != null && alliance.hasAnyAcceptedAlliance()) {
            ledger.kinglessWaived = true;
        }
    }

    static void applySuccessionRelationCap(KOMEAlliance alliance,
            lotr.common.fac.LOTRFactionRelations.Relation relation, long worldTime) {
        if (alliance == null) {
            return;
        }
        if (relation == lotr.common.fac.LOTRFactionRelations.Relation.FRIEND) {
            alliance.breakTrack(KOMEAlliance.MILITARY, "Succession expired: default Friend cap", worldTime);
        } else if (relation == lotr.common.fac.LOTRFactionRelations.Relation.NEUTRAL) {
            alliance.breakTrack(KOMEAlliance.TRADE, "Succession expired: default Neutral cap", worldTime);
        } else if (relation != lotr.common.fac.LOTRFactionRelations.Relation.ALLY) {
            alliance.clearAllTracks("Succession expired: hostile default relation", worldTime);
        }
    }

    void expireSuccessionSide(KOMEAlliance alliance, String affectedFaction, long worldTime) {
        KOMEAllianceFactionLedger ledger = KOMEAllianceGraceService.requireLedger(alliance, affectedFaction);
        capAllianceAfterSuccession(alliance, ledger.faction, worldTime);
        ledger.clearSuccession();
        KOMECommandAlliance.syncRelationsForAlliancePair(this, alliance.factionA, alliance.factionB);
        markDirty();
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
        return Math.max(0, total);
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
        return Math.max(0, total);
    }

    public int getFarmhandLimit(UUID owner) {
        KOMEPlayerPopulation pop = getPopulation(owner);
        int allocatedPopulation = getPlayerTilePopulationAllocated(owner);
        int populationSlots = (pop.getCombinedTotal() + allocatedPopulation) / 25;
        return Math.max(0, populationSlots);
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
        // Once a player's real LOTR pledge has been observed, it is the authoritative
        // membership value, including an explicitly unpledged empty value. Progression-lord
        // data remains only a pre-schema migration baseline for players not yet observed.
        if (playerID != null && lastKnownPlayerFactions.containsKey(playerID)) {
            return normalizeFactionKey(lastKnownPlayerFactions.get(playerID));
        }
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
            releaseFundingBuild(record);
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
            KOMEArmyCompany assignedCompany = record.companyId == null ? null : armyCompanies.get(record.companyId);
            if (assignedCompany != null && KOMEArmyCompany.SOURCE_AUTO_UNIT_ASSIGNMENT.equals(assignedCompany.source)) {
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
        for (KOMEHiredUnitRecord record : hiredUnits.values()) {
            if (!isEligibleForAutoCompany(record) || onlyOwner != null && !onlyOwner.equals(record.owner)) continue;
            KOMEArmyCompany existing = record.companyId == null ? null : armyCompanies.get(record.companyId);
            if (existing != null && (existing.isMoving()
                    || KOMEArmyCompany.SOURCE_AUTO_UNIT_ASSIGNMENT.equals(existing.source))) {
                if (!existing.units.contains(record.entity)) existing.units.add(record.entity);
                continue;
            }
            assignUnitToHiringTileCompany(record, record.companyAssignedByName);
        }
        for (KOMEArmyCompany company : armyCompanies.values()) {
            if (company == null || onlyOwner != null && !onlyOwner.equals(company.owner)) continue;
            recalculateCompanyComposition(company);
        }
        markDirty();
        syncConquestTiles();
    }

    public KOMEArmyCompany assignUnitToHiringTileCompany(KOMEHiredUnitRecord record, String ownerName) {
        if (!isEligibleForAutoCompany(record) || record.owner == null) return null;
        // This service is the authoritative completion point for a successful hire.
        // Keeping the record before recomputing prevents a newly assigned live unit from
        // being mistaken for a stale company member when callers have not inserted it yet.
        hiredUnits.put(record.entity, record);
        String sourceTile = KOMEConquestTile.normalizeId(record.sourceTileId);
        if (sourceTile.length() == 0) sourceTile = KOMEConquestTile.normalizeId(record.currentTile);
        if (sourceTile.length() == 0) return null;
        KOMEArmyCompany company = findHiringCompany(record.owner, sourceTile);
        if (company == null) {
            String id = hiringCompanyId(record.owner, sourceTile);
            company = new KOMEArmyCompany();
            company.id = id;
            company.owner = record.owner;
            company.ownerName = ownerName == null || ownerName.length() == 0
                ? safePlayerName(record.owner) : ownerName;
            company.faction = "MILITARY_T3_STEWARDSHIP".equals(record.benefitSource)
                ? normalizeFactionKey(record.unitFaction) : normalizeFactionKey(getPlayerFactionKey(record.owner));
            company.nativeFaction = company.faction;
            company.sourceTileId = sourceTile;
            company.currentTile = KOMEConquestTile.normalizeId(record.currentTile);
            if (company.currentTile.length() == 0) company.currentTile = sourceTile;
            company.name = defaultHiringCompanyName(sourceTile);
            company.lotrCompanyValue = company.name;
            company.source = KOMEArmyCompany.SOURCE_AUTO_UNIT_ASSIGNMENT;
            company.status = KOMEArmyCompany.STATIONED;
            company.createdAtMillis = System.currentTimeMillis();
            company.updatedAtMillis = company.createdAtMillis;
            armyCompanies.put(id, company);
        }
        if (!company.units.contains(record.entity)) company.units.add(record.entity);
        record.companyId = company.id;
        record.companyName = company.name;
        record.companyAssignedAtMillis = System.currentTimeMillis();
        record.companyAssignedBy = record.owner;
        record.companyAssignedByName = company.ownerName;
        recalculateCompanyComposition(company);
        markDirty();
        return company;
    }

    public boolean renameHiringCompany(String companyId, UUID actor, String requestedName) {
        KOMEArmyCompany company = armyCompanies.get(companyId == null ? "" : companyId);
        if (company == null || actor == null || !actor.equals(company.owner)) return false;
        String name = KOMEHiredUnitRecord.normalizeCompanyName(requestedName);
        if (name.length() == 0) return false;
        company.name = name;
        company.lotrCompanyValue = name;
        company.updatedAtMillis = System.currentTimeMillis();
        for (UUID unitId : company.units) {
            KOMEHiredUnitRecord record = hiredUnits.get(unitId);
            if (record != null) record.companyName = name;
        }
        markDirty();
        return true;
    }

    private void recalculateCompanyComposition(KOMEArmyCompany company) {
        company.totalPopulation = 0;
        company.mountedPopulation = 0;
        company.groundPopulation = 0;
        List<UUID> missing = new ArrayList<UUID>();
        for (UUID unitId : company.units) {
            KOMEHiredUnitRecord record = hiredUnits.get(unitId);
            if (record == null || record.farmhand || record.type != KOMEPopulationType.OFFENSIVE) {
                missing.add(unitId);
                continue;
            }
            int cost = Math.max(0, record.cost);
            company.totalPopulation += cost;
            if (record.mounted) company.mountedPopulation += cost;
            else company.groundPopulation += cost;
        }
        company.units.removeAll(missing);
        company.updatedAtMillis = System.currentTimeMillis();
    }

    private String safePlayerName(UUID owner) {
        String name = playerNames.get(owner);
        return name == null ? "" : name;
    }

    private static String hiringCompanyId(UUID owner, String sourceTile) {
        return "HC_" + owner.toString().replace("-", "") + "_" + KOMEConquestTile.normalizeId(sourceTile);
    }

    private KOMEArmyCompany findHiringCompany(UUID owner, String sourceTile) {
        String tile = KOMEConquestTile.normalizeId(sourceTile);
        KOMEArmyCompany result = null;
        for (KOMEArmyCompany candidate : armyCompanies.values()) {
            if (candidate == null || owner == null || !owner.equals(candidate.owner)
                    || !KOMEArmyCompany.SOURCE_AUTO_UNIT_ASSIGNMENT.equals(candidate.source)
                    || !tile.equals(KOMEConquestTile.normalizeId(candidate.sourceTileId))) continue;
            if (result == null || candidate.createdAtMillis < result.createdAtMillis
                    || candidate.createdAtMillis == result.createdAtMillis
                        && candidate.id.compareTo(result.id) < 0) {
                result = candidate;
            }
        }
        return result;
    }

    private String defaultHiringCompanyName(String sourceTile) {
        KOMETileWaypointLink link = getTileWaypointLink(sourceTile);
        String place = link == null ? "" : link.displayName();
        return (place.length() == 0 ? KOMEConquestTile.normalizeId(sourceTile) : place) + " Company";
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
        net.minecraft.server.MinecraftServer server;
        try {
            server = FMLCommonHandler.instance().getMinecraftServerInstance();
        } catch (Throwable unavailableOutsideForgeRuntime) {
            return;
        }
        if (server == null || server.getConfigurationManager() == null) return;
        for (Object player : server.getConfigurationManager().playerEntityList) {
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
        int savedAllianceSchema = nbt.hasKey("AllianceDataSchemaVersion") ? nbt.getInteger("AllianceDataSchemaVersion") : 0;
        int savedBuildSchema = nbt.hasKey("BuildDataSchemaVersion") ? nbt.getInteger("BuildDataSchemaVersion") : 0;
        int savedPopulationSchema = nbt.hasKey("PopulationDataSchemaVersion") ? nbt.getInteger("PopulationDataSchemaVersion") : 0;
        int removedCaptainDesignations = 0;
        int returnedCaptainPopulation = 0;
        int removedPostFarmerReservations = 0;
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
        builds.clear();
        alliances.clear();
        recoveredLegacyTradePostIds.clear();
        quarantinedTradePostRecords.clear();
        wars.clear();
        conquestClaimConfirmations.clear();
        lastKnownPlayerFactions.clear();
        pledgeReleaseTombstones.clear();
        pledgeReleaseQuarantine.clear();
        pledgeReleaseLastResults.clear();
        pledgeReleaseAudit.clear();
        allianceRequirementOverrides.clear();
        allianceQuotaWeightOverrides.clear();
        allianceQuotaMaximumOverrides.clear();
        allianceQuotaEnabledOverrides.clear();
        waypointRestrictionBypasses.clear();
        allianceAdminAudit.clear();
        quarantinedAllianceRecords.clear();
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
        nextWarSequence = nbt.hasKey("NextWarSequence") ? Math.max(1, nbt.getInteger("NextWarSequence")) : 1;
        nextBuildSequence = nbt.hasKey("NextBuildSequence") ? Math.max(1, nbt.getInteger("NextBuildSequence")) : 1;
        buildPopulationPerHalfHour = nbt.hasKey("BuildPopulationPerHalfHour")
            ? Math.max(1, nbt.getInteger("BuildPopulationPerHalfHour"))
            : KOMEBuildPopulationService.DEFAULT_POPULATION_PER_HALF_HOUR;
        allianceStageThreeRequiredHalfHours = nbt.hasKey("AllianceStageThreeRequiredHalfHours")
            ? Math.max(1, nbt.getInteger("AllianceStageThreeRequiredHalfHours"))
            : KOMEBuildPopulationService.DEFAULT_STAGE_THREE_REQUIRED_HALF_HOURS;
        allianceDifficulty = KOMEAllianceRequirements.normalizeDifficulty(nbt.getString("AllianceDifficulty"));
        waypointRestrictionEnabled = false;
        successionGraceDefaultMillis = nbt.hasKey("SuccessionGraceDefaultMillis")
            ? Math.max(1000L, nbt.getLong("SuccessionGraceDefaultMillis")) : KOMEAllianceAuthority.FOURTEEN_DAYS_MILLIS;
        contributionGraceDefaultMillis = nbt.hasKey("ContributionGraceDefaultMillis")
            ? Math.max(1000L, nbt.getLong("ContributionGraceDefaultMillis")) : KOMEAllianceAuthority.FOURTEEN_DAYS_MILLIS;
        NBTTagList requirementConfig = nbt.getTagList("AllianceRequirementOverrides", 10);
        for (int i = 0; i < requirementConfig.tagCount(); i++) {
            NBTTagCompound entry = requirementConfig.getCompoundTagAt(i);
            String key = entry.getString("Key");
            if (key.length() > 0) {
                allianceRequirementOverrides.put(key, Integer.valueOf(Math.max(0, entry.getInteger("Value"))));
            }
        }
        NBTTagList quotaItemConfig = nbt.getTagList("AllianceQuotaItemOverrides", 10);
        for (int i = 0; i < quotaItemConfig.tagCount(); i++) {
            NBTTagCompound entry = quotaItemConfig.getCompoundTagAt(i);
            String key = normalizeQuotaKey(entry.getString("Key"));
            if (key.length() == 0) {
                continue;
            }
            if (entry.hasKey("Weight") && KOMEAllianceRequirements.isSupportedWeight(entry.getInteger("Weight"))) {
                allianceQuotaWeightOverrides.put(key, Integer.valueOf(entry.getInteger("Weight")));
            }
            if (entry.hasKey("Maximum") && entry.getInteger("Maximum") > 0) {
                allianceQuotaMaximumOverrides.put(key, Integer.valueOf(entry.getInteger("Maximum")));
            }
            if (entry.hasKey("Enabled")) {
                allianceQuotaEnabledOverrides.put(key, Boolean.valueOf(entry.getBoolean("Enabled")));
            }
        }
        NBTTagList bypassList = nbt.getTagList("WaypointRestrictionBypasses", 10);
        for (int i = 0; i < bypassList.tagCount(); i++) {
            try {
                waypointRestrictionBypasses.add(UUID.fromString(bypassList.getCompoundTagAt(i).getString("Player")));
            } catch (IllegalArgumentException ignored) {
            }
        }
        NBTTagList adminAuditList = nbt.getTagList("AllianceAdminAudit", 10);
        for (int i = 0; i < adminAuditList.tagCount() && allianceAdminAudit.size() < 200; i++) {
            String entry = adminAuditList.getCompoundTagAt(i).getString("Entry");
            if (entry.length() > 0) {
                allianceAdminAudit.add(entry);
            }
        }
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

        NBTTagList knownFactionList = nbt.getTagList("LastKnownPlayerFactions", 10);
        for (int i = 0; i < knownFactionList.tagCount(); i++) {
            NBTTagCompound entry = knownFactionList.getCompoundTagAt(i);
            try {
                UUID player = UUID.fromString(entry.getString("Player"));
                lastKnownPlayerFactions.put(player, KOMEAlliance.normalizeFactionKey(entry.getString("Faction")));
            } catch (IllegalArgumentException ignored) {
            }
        }
        NBTTagList tombstoneList = nbt.getTagList("PledgeReleaseTombstones", 10);
        for (int i = 0; i < tombstoneList.tagCount(); i++) {
            KOMEPledgeReleaseTombstone tombstone = new KOMEPledgeReleaseTombstone();
            tombstone.readFromNBT(tombstoneList.getCompoundTagAt(i));
            if (tombstone.unitUuid != null) pledgeReleaseTombstones.put(tombstone.unitUuid, tombstone);
        }
        NBTTagList releaseQuarantineList = nbt.getTagList("PledgeReleaseQuarantine", 10);
        for (int i = 0; i < releaseQuarantineList.tagCount(); i++) {
            NBTTagCompound entry = releaseQuarantineList.getCompoundTagAt(i);
            try {
                UUID unit = UUID.fromString(entry.getString("Unit"));
                if (entry.hasKey("Record", 10)) pledgeReleaseQuarantine.put(unit, entry.getCompoundTag("Record"));
            } catch (IllegalArgumentException ignored) {
            }
        }
        NBTTagList releaseResultList = nbt.getTagList("PledgeReleaseLastResults", 10);
        for (int i = 0; i < releaseResultList.tagCount(); i++) {
            NBTTagCompound entry = releaseResultList.getCompoundTagAt(i);
            try {
                UUID player = UUID.fromString(entry.getString("Player"));
                pledgeReleaseLastResults.put(player, entry.getString("Result"));
            } catch (IllegalArgumentException ignored) {
            }
        }
        NBTTagList releaseAuditList = nbt.getTagList("PledgeReleaseAudit", 10);
        for (int i = 0; i < releaseAuditList.tagCount() && pledgeReleaseAudit.size() < 250; i++) {
            String entry = releaseAuditList.getCompoundTagAt(i).getString("Entry");
            if (entry.length() > 0) pledgeReleaseAudit.add(entry);
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

        NBTTagList buildList = nbt.getTagList("Builds", 10);
        for (int i = 0; i < buildList.tagCount(); i++) {
            KOMEPlayerBuild build = new KOMEPlayerBuild();
            build.readFromNBT(buildList.getCompoundTagAt(i));
            if (build.id.length() > 0 && build.tileId.length() > 0 && build.populationFaction.length() > 0) {
                builds.put(build.id, build);
            }
        }
        for (KOMEPlayerBuild build : builds.values()) {
            recalculateBuildPopulationPool(build.tileId, build.populationFaction);
        }
        reconcileBuildCommitments();
        if (savedBuildSchema < BUILD_DATA_SCHEMA_VERSION) {
            safeAllianceInfo("[KOME] Build schema " + savedBuildSchema + " -> " + BUILD_DATA_SCHEMA_VERSION
                + ": initialized persistent Build collection without converting legacy population.");
        }
        if (savedPopulationSchema < POPULATION_DATA_SCHEMA_VERSION) {
            safeAllianceInfo("[KOME] Population schema " + savedPopulationSchema + " -> "
                + POPULATION_DATA_SCHEMA_VERSION + ": preserved legacy totals as native faction pools.");
            migratedPopulationData = true;
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
            if (!KOMEHiredUnitRecord.SOURCE_PLAYER_RESERVE.equals(record.sourceType)
                    && !KOMEHiredUnitRecord.SOURCE_TILE_POOL.equals(record.sourceType)
                    && !KOMEHiredUnitRecord.SOURCE_TILE_ALLOCATION.equals(record.sourceType)
                    && !KOMEHiredUnitRecord.SOURCE_STEWARDSHIP_RESERVATION.equals(record.sourceType)
                    && !KOMEHiredUnitRecord.SOURCE_OTHER_LEGACY.equals(record.sourceType)) {
                record.sourceType = KOMEHiredUnitRecord.SOURCE_OTHER_LEGACY;
                migratedPopulationData = true;
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
            if (record.allocationPlayer != null && KOMEHiredUnitRecord.SOURCE_TILE_POOL.equals(record.sourceType)) {
                record.sourceType = KOMEHiredUnitRecord.SOURCE_TILE_ALLOCATION;
                migratedPopulationData = true;
            }
            if ("MILITARY_T3_STEWARDSHIP".equals(record.benefitSource)
                    && !record.isPlayerReserveFunded() && getFundingPool(record) != null) {
                record.sourceType = KOMEHiredUnitRecord.SOURCE_STEWARDSHIP_RESERVATION;
                migratedPopulationData = true;
            }
        }
        reconcileBuildManagers();

        for (KOMEHiredUnitRecord record : hiredUnits.values()) {
            if (record == null || !record.legacyAllianceCaptain && record.legacyCaptainPopulationReservation <= 0) {
                continue;
            }
            int returned = Math.min(Math.max(0, record.cost), Math.max(0, record.legacyCaptainPopulationReservation));
            if (returned > 0 && !record.isPlayerReserveFunded()) {
                KOMETilePopulation pool = getFundingPool(record);
                if (pool != null) {
                    pool.release(KOMEPopulationType.OFFENSIVE, returned);
                }
            }
            record.cost = Math.max(0, record.cost - returned);
            returnedCaptainPopulation += returned;
            removedCaptainDesignations++;
            record.legacyAllianceCaptain = false;
            record.legacyCaptainSuspended = false;
            record.legacyCaptainPopulationReservation = 0;
            if ("MILITARY_T4_CAPTAIN".equals(record.benefitSource)) {
                record.benefitSource = "";
            }
            migratedPopulationData = true;
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

        int allianceLoaded = 0;
        int allianceMerged = 0;
        int allianceDuplicate = 0;
        int allianceQuarantined = 0;
        Set<String> seenAllianceTags = new HashSet<String>();
        NBTTagList previousQuarantine = nbt.getTagList("AllianceMigrationQuarantine", 10);
        for (int i = 0; i < previousQuarantine.tagCount(); i++) {
            quarantinedAllianceRecords.add((NBTTagCompound) previousQuarantine.getCompoundTagAt(i).copy());
        }

        NBTTagList recoveredPostIds = nbt.getTagList("RecoveredLegacyTradePostIds", 10);
        for (int i = 0; i < recoveredPostIds.tagCount(); i++) {
            String id = recoveredPostIds.getCompoundTagAt(i).getString("Id");
            if (id.length() > 0) recoveredLegacyTradePostIds.add(id);
        }
        NBTTagList previousPostQuarantine = nbt.getTagList("TradePostMigrationQuarantine", 10);
        for (int i = 0; i < previousPostQuarantine.tagCount(); i++) {
            quarantinedTradePostRecords.add((NBTTagCompound) previousPostQuarantine.getCompoundTagAt(i).copy());
        }
        NBTTagList allianceList = nbt.getTagList("Alliances", 10);
        for (int i = 0; i < allianceList.tagCount(); i++) {
            NBTTagCompound savedAlliance = allianceList.getCompoundTagAt(i);
            String fingerprint = savedAlliance.toString();
            if (!seenAllianceTags.add(fingerprint)) {
                allianceDuplicate++;
                continue;
            }
            try {
                KOMEAlliance alliance = new KOMEAlliance("", "");
                alliance.readFromNBT(savedAlliance);
                if (alliance.factionA.length() == 0 || alliance.factionB.length() == 0
                        || alliance.factionA.equals(alliance.factionB)) {
                    quarantinedAllianceRecords.add((NBTTagCompound) savedAlliance.copy());
                    allianceQuarantined++;
                    continue;
                }
                if (!alliance.hasPersistentData()) {
                    continue;
                }
                String key = alliance.getPairKey();
                KOMEAlliance existing = alliances.get(key);
                if (existing == null) {
                    alliances.put(key, alliance);
                    allianceLoaded++;
                } else {
                    existing.mergeFrom(alliance, true);
                    allianceMerged++;
                }
            } catch (Throwable problem) {
                quarantinedAllianceRecords.add((NBTTagCompound) savedAlliance.copy());
                allianceQuarantined++;
                safeAllianceWarning("[KOME] Quarantined malformed alliance record " + i + ": " + problem.toString());
            }
        }
        if (savedAllianceSchema < ALLIANCE_DATA_SCHEMA_VERSION) {
            for (KOMEAlliance alliance : alliances.values()) {
                String[] pairFactions = new String[] {alliance.factionA, alliance.factionB};
                for (int i = 0; i < pairFactions.length; i++) {
                    String faction = pairFactions[i];
                    KOMEAllianceFactionLedger ledger = alliance.getFactionLedger(faction);
                    if (ledger != null) {
                        ledger.kinglessWaived = false;
                        ledger.clearContributionGrace();
                        ledger.clearSuccession();
                        int legacyTrades = Math.max(ledger.getDelivered("civil.trade"), ledger.getDelivered("trade.trade"));
                        if (legacyTrades > ledger.getDelivered(KOMEAllianceProgressionService.ALLIED_TRADES)) {
                            ledger.setDelivered(KOMEAllianceProgressionService.ALLIED_TRADES, legacyTrades);
                        }
                        ledger.setAssignment(KOMEAllianceQuotaPool.assignmentId(KOMEAlliance.MILITARY, 4), "");
                        ledger.setDelivered(KOMEAllianceQuotaPool.assignmentId(KOMEAlliance.MILITARY, 4), 0);
                    }
                }
            }
        }
        allianceRelationsNeedReapply = true;
        boolean quotaValidationChanged = KOMEAllianceQuotaPool.validateExistingRequirements(this);
        boolean migratedAllianceData = savedAllianceSchema != ALLIANCE_DATA_SCHEMA_VERSION
            || allianceMerged > 0 || allianceDuplicate > 0 || allianceQuarantined > 0;
        migratedAllianceData |= quotaValidationChanged;
        if (migratedAllianceData || allianceList.tagCount() > 0) {
            safeAllianceInfo("[KOME] Alliance schema " + savedAllianceSchema + " -> " + ALLIANCE_DATA_SCHEMA_VERSION
                + ": loaded=" + allianceLoaded + " merged=" + allianceMerged + " duplicate=" + allianceDuplicate
                + " quarantined=" + allianceQuarantined + " pairs=" + alliances.size());
        }

        NBTTagList tradePostList = nbt.getTagList("AllianceTradePosts", 10);
        int recoveredPostCount = 0;
        int recoveredPostStacks = 0;
        int recoveredPostItems = 0;
        for (int i = 0; i < tradePostList.tagCount(); i++) {
            NBTTagCompound savedPost = tradePostList.getCompoundTagAt(i);
            KOMELegacyTradePostRecord post = new KOMELegacyTradePostRecord();
            post.readFromNBT(savedPost);
            if (post.id.length() > 0 && post.operatingFaction.length() > 0 && post.hostFaction.length() > 0
                    && !post.operatingFaction.equals(post.hostFaction)) {
                if (post.legacyFarmerReservation) {
                    post.legacyFarmerReservation = false;
                    removedPostFarmerReservations++;
                    migratedPopulationData = true;
                }
                if (!recoveredLegacyTradePostIds.contains(post.id)) {
                    KOMEAlliance recoveryAlliance = getAlliance(post.operatingFaction, post.hostFaction, true);
                    KOMEAllianceFactionLedger recoveryLedger = recoveryAlliance == null ? null
                        : recoveryAlliance.getFactionLedger(post.operatingFaction);
                    if (recoveryLedger == null) {
                        quarantinedTradePostRecords.add((NBTTagCompound) savedPost.copy());
                    } else {
                        ItemRecovery recovered = recoverTradePostStacks(post, recoveryLedger);
                        recoveredPostStacks += recovered.stacks;
                        recoveredPostItems += recovered.items;
                        recoveredPostCount++;
                        recoveredLegacyTradePostIds.add(post.id);
                    }
                }
            } else {
                quarantinedTradePostRecords.add((NBTTagCompound) savedPost.copy());
            }
        }
        if (recoveredPostCount > 0 || !quarantinedTradePostRecords.isEmpty()) {
            safeAllianceInfo("[KOME] Removed legacy trade posts: affected=" + recoveredPostCount
                + " recoveredStacks=" + recoveredPostStacks + " recoveredItems=" + recoveredPostItems
                + " quarantined=" + quarantinedTradePostRecords.size()
                + ". Goods are in the operating faction's alliance-ledger recovery storage.");
            migratedPopulationData = true;
        }

        NBTTagList warList = nbt.getTagList("Wars", 10);
        for (int i = 0; i < warList.tagCount(); i++) {
            KOMEWar war = new KOMEWar();
            war.readFromNBT(warList.getCompoundTagAt(i));
            if (war.id.length() > 0 && !war.sideOneFactions.isEmpty() && !war.sideTwoFactions.isEmpty()) {
                wars.put(war.id, war);
            }
        }
        NBTTagList confirmationList = nbt.getTagList("ConquestClaimConfirmations", 10);
        long confirmationNow = System.currentTimeMillis();
        for (int i = 0; i < confirmationList.tagCount(); i++) {
            KOMEClaimConfirmation confirmation = new KOMEClaimConfirmation();
            confirmation.readFromNBT(confirmationList.getCompoundTagAt(i));
            if (confirmation.player != null && confirmation.tileId.length() > 0
                    && confirmation.expiresAtMillis >= confirmationNow) {
                conquestClaimConfirmations.put(confirmation.player, confirmation);
            }
        }
        // Schema-5 correction: the provisional Produce runtime never became a supported
        // feature. Read its retired tags once only to preserve a genuinely generated
        // pending stack in the existing alliance-ledger recovery storage. The tags are
        // deliberately never written again, so a save followed by any number of cold
        // restarts cannot duplicate the recovered item.
        NBTTagList retiredProduceSlots = nbt.getTagList("AllianceProduceSlots", 10);
        int retiredProduceRecords = retiredProduceSlots.tagCount();
        int recoveredProduceStacks = 0;
        int quarantinedProduceRecords = 0;
        for (int i = 0; i < retiredProduceSlots.tagCount(); i++) {
            NBTTagCompound retired = retiredProduceSlots.getCompoundTagAt(i);
            net.minecraft.item.ItemStack pending = retired.hasKey("CurrentPendingProduct", 10)
                ? net.minecraft.item.ItemStack.loadItemStackFromNBT(retired.getCompoundTag("CurrentPendingProduct")) : null;
            if (pending == null || pending.stackSize <= 0 || retired.getBoolean("PendingClaimed")) continue;
            String pair = retired.getString("AlliancePair");
            String contributor = normalizeFactionKey(retired.getString("PlayerFaction"));
            if (!recoverRetiredProducePending(pair, contributor, pending)) {
                NBTTagCompound quarantined = (NBTTagCompound) retired.copy();
                quarantined.setString("MigrationReason", "Retired runtime pending item had no valid alliance contributor ledger");
                quarantinedTradePostRecords.add(quarantined);
                quarantinedProduceRecords++;
            } else {
                recoveredProduceStacks++;
            }
        }
        if (retiredProduceRecords > 0) {
            safeAllianceInfo("[KOME] Removed provisional Produce runtime records=" + retiredProduceRecords
                + " recoveredPendingStacks=" + recoveredProduceStacks + " quarantined=" + quarantinedProduceRecords
                + ". No Produce runtime state will be saved.");
            migratedPopulationData = true;
        }
        if (savedAllianceSchema < 3 || removedCaptainDesignations > 0 || removedPostFarmerReservations > 0) {
            safeAllianceInfo("[KOME] Alliance schema-3 cleanup: militaryT4Clamped=true captainDesignationsCleared="
                + removedCaptainDesignations + " captainPopulationReturned=" + returnedCaptainPopulation
                + " tradePostFarmerReservationsCleared=" + removedPostFarmerReservations);
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

        KOMEWarService.reconcileAutomaticMilitarySupport(this, System.currentTimeMillis(), "World load reconciliation");
        KOMEWartimeStewardshipService.revalidateAll(this, System.currentTimeMillis(),
            savedAllianceSchema < 5 ? "Schema-5 removed peacetime kingless stewardship" : "Restart authorization revalidation");
        KOMECommandTroops.revalidateTemporaryControllers(this, System.currentTimeMillis(), "Restart authorization revalidation");
        if (savedAllianceSchema < 5) {
            safeAllianceInfo("[KOME] Alliance schema-5 migration: coalition wars initialized without inferred history; "
                + "peacetime kingless stewardship revoked; Trade Posts recovered to ledgers; pledge-release tracking enabled.");
            migratedPopulationData = true;
        }

        if (pruneRetiredConquestTileData()) {
            migratedPopulationData = true;
        }

        if (applyWaypointDefaults(!conquestDefaultsInitialized)) {
            migratedPopulationData = true;
        }

        if (migratedPopulationData || migratedAllianceData) {
            markDirty();
        }
    }

    /** Migration-only helper retained so pending items from the retired provisional records are never discarded. */
    boolean recoverRetiredProducePending(String pair, String contributor, net.minecraft.item.ItemStack pending) {
        if (pending == null || pending.stackSize <= 0) return false;
        String[] factions = pair == null ? new String[0] : pair.split("\\|", -1);
        KOMEAlliance recoveryAlliance = factions.length == 2 ? getAlliance(factions[0], factions[1], true) : null;
        String contributorKey = normalizeFactionKey(contributor);
        KOMEAllianceFactionLedger recoveryLedger = recoveryAlliance != null && recoveryAlliance.involves(contributorKey)
            ? recoveryAlliance.getFactionLedger(contributorKey) : null;
        if (recoveryLedger == null) return false;
        recoveryLedger.addRecoveryStack(pending);
        return true;
    }

    private static void safeAllianceWarning(String message) {
        try {
            FMLLog.warning("%s", message);
        } catch (Throwable ignored) {
            System.err.println(message);
        }
    }

    private static void safeAllianceInfo(String message) {
        try {
            FMLLog.info("%s", message);
        } catch (Throwable ignored) {
            System.out.println(message);
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

    private boolean hasMissingAllianceContributions(KOMEAlliance alliance, KOMEAllianceFactionLedger ledger) {
        return alliance.hasAccepted(KOMEAlliance.CIVIL) && ledger.getCompletedTier(KOMEAlliance.CIVIL) < alliance.getFactionTier(ledger.faction, KOMEAlliance.CIVIL)
            || alliance.hasAccepted(KOMEAlliance.TRADE) && ledger.getCompletedTier(KOMEAlliance.TRADE) < alliance.getFactionTier(ledger.faction, KOMEAlliance.TRADE)
            || alliance.hasAccepted(KOMEAlliance.MILITARY) && ledger.getCompletedTier(KOMEAlliance.MILITARY) < alliance.getFactionTier(ledger.faction, KOMEAlliance.MILITARY);
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        nbt.removeTag("TradeProduceSlotsMaximum");
        nbt.removeTag("AllianceProduceSlots");
        nbt.setInteger("AllianceDataSchemaVersion", ALLIANCE_DATA_SCHEMA_VERSION);
        nbt.setInteger("BuildDataSchemaVersion", BUILD_DATA_SCHEMA_VERSION);
        nbt.setInteger("PopulationDataSchemaVersion", POPULATION_DATA_SCHEMA_VERSION);
        nbt.setBoolean("ProgressionEnabled", progressionEnabled);
        nbt.setInteger("MovementSecondsPerTileOverride", Math.max(0, movementSecondsPerTileOverride));
        nbt.setInteger("MovementTotalSecondsOverride", Math.max(0, movementTotalSecondsOverride));
        nbt.setInteger("MovementStepDelaySeconds", Math.max(0, movementStepDelaySeconds));
        nbt.setString("MovementDailyResetTime", movementDailyResetTime == null ? "20:00" : movementDailyResetTime);
        nbt.setString("MovementDailyResetTimezone", movementDailyResetTimezone == null ? "America/Chicago" : movementDailyResetTimezone);
        nbt.setInteger("NextWarSequence", Math.max(1, nextWarSequence));
        nbt.setInteger("NextBuildSequence", Math.max(1, nextBuildSequence));
        nbt.setInteger("BuildPopulationPerHalfHour", Math.max(1, buildPopulationPerHalfHour));
        nbt.setInteger("AllianceStageThreeRequiredHalfHours", Math.max(1, allianceStageThreeRequiredHalfHours));
        nbt.setString("AllianceDifficulty", KOMEAllianceRequirements.normalizeDifficulty(allianceDifficulty));
        nbt.setBoolean("WaypointRestrictionEnabled", false);
        nbt.setLong("SuccessionGraceDefaultMillis", Math.max(1000L, successionGraceDefaultMillis));
        nbt.setLong("ContributionGraceDefaultMillis", Math.max(1000L, contributionGraceDefaultMillis));
        NBTTagList requirementConfig = new NBTTagList();
        for (Map.Entry<String, Integer> entry : allianceRequirementOverrides.entrySet()) {
            if (entry.getKey() != null && entry.getKey().length() > 0 && entry.getValue() != null) {
                NBTTagCompound value = new NBTTagCompound();
                value.setString("Key", entry.getKey());
                value.setInteger("Value", Math.max(0, entry.getValue().intValue()));
                requirementConfig.appendTag(value);
            }
        }
        nbt.setTag("AllianceRequirementOverrides", requirementConfig);
        NBTTagList quotaItemConfig = new NBTTagList();
        Set<String> quotaKeys = new HashSet<String>();
        quotaKeys.addAll(allianceQuotaWeightOverrides.keySet());
        quotaKeys.addAll(allianceQuotaMaximumOverrides.keySet());
        quotaKeys.addAll(allianceQuotaEnabledOverrides.keySet());
        for (String key : quotaKeys) {
            if (key == null || key.length() == 0) {
                continue;
            }
            NBTTagCompound value = new NBTTagCompound();
            value.setString("Key", key);
            Integer weight = allianceQuotaWeightOverrides.get(key);
            Integer maximum = allianceQuotaMaximumOverrides.get(key);
            Boolean enabled = allianceQuotaEnabledOverrides.get(key);
            if (weight != null) {
                value.setInteger("Weight", weight.intValue());
            }
            if (maximum != null) {
                value.setInteger("Maximum", maximum.intValue());
            }
            if (enabled != null) {
                value.setBoolean("Enabled", enabled.booleanValue());
            }
            quotaItemConfig.appendTag(value);
        }
        nbt.setTag("AllianceQuotaItemOverrides", quotaItemConfig);
        NBTTagList bypassList = new NBTTagList();
        for (UUID playerId : waypointRestrictionBypasses) {
            if (playerId != null) {
                NBTTagCompound value = new NBTTagCompound();
                value.setString("Player", playerId.toString());
                bypassList.appendTag(value);
            }
        }
        nbt.setTag("WaypointRestrictionBypasses", bypassList);
        NBTTagList adminAuditList = new NBTTagList();
        for (String entry : allianceAdminAudit) {
            if (entry != null && entry.length() > 0) {
                NBTTagCompound value = new NBTTagCompound();
                value.setString("Entry", entry);
                adminAuditList.appendTag(value);
            }
        }
        nbt.setTag("AllianceAdminAudit", adminAuditList);
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

        NBTTagList knownFactionList = new NBTTagList();
        for (Map.Entry<UUID, String> entry : lastKnownPlayerFactions.entrySet()) {
            NBTTagCompound value = new NBTTagCompound();
            value.setString("Player", entry.getKey().toString());
            value.setString("Faction", KOMEAlliance.normalizeFactionKey(entry.getValue()));
            knownFactionList.appendTag(value);
        }
        nbt.setTag("LastKnownPlayerFactions", knownFactionList);
        NBTTagList tombstoneList = new NBTTagList();
        for (KOMEPledgeReleaseTombstone tombstone : pledgeReleaseTombstones.values()) {
            if (tombstone != null && tombstone.unitUuid != null) tombstoneList.appendTag(tombstone.writeToNBT());
        }
        nbt.setTag("PledgeReleaseTombstones", tombstoneList);
        NBTTagList releaseQuarantineList = new NBTTagList();
        for (Map.Entry<UUID, NBTTagCompound> entry : pledgeReleaseQuarantine.entrySet()) {
            NBTTagCompound value = new NBTTagCompound();
            value.setString("Unit", entry.getKey().toString());
            if (entry.getValue() != null) value.setTag("Record", entry.getValue().copy());
            releaseQuarantineList.appendTag(value);
        }
        nbt.setTag("PledgeReleaseQuarantine", releaseQuarantineList);
        NBTTagList releaseResultList = new NBTTagList();
        for (Map.Entry<UUID, String> entry : pledgeReleaseLastResults.entrySet()) {
            NBTTagCompound value = new NBTTagCompound();
            value.setString("Player", entry.getKey().toString());
            value.setString("Result", entry.getValue() == null ? "" : entry.getValue());
            releaseResultList.appendTag(value);
        }
        nbt.setTag("PledgeReleaseLastResults", releaseResultList);
        NBTTagList releaseAuditList = new NBTTagList();
        for (String entry : pledgeReleaseAudit) {
            if (entry == null || entry.length() == 0) continue;
            NBTTagCompound value = new NBTTagCompound();
            value.setString("Entry", entry);
            releaseAuditList.appendTag(value);
        }
        nbt.setTag("PledgeReleaseAudit", releaseAuditList);

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

        NBTTagList buildList = new NBTTagList();
        for (KOMEPlayerBuild build : builds.values()) {
            if (build != null && build.id != null && build.id.length() > 0) {
                buildList.appendTag(build.writeToNBT());
            }
        }
        nbt.setTag("Builds", buildList);

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
            if (alliance != null && alliance.hasPersistentData()) {
                allianceList.appendTag(alliance.writeToNBT());
            }
        }
        nbt.setTag("Alliances", allianceList);
        NBTTagList allianceQuarantine = new NBTTagList();
        for (NBTTagCompound quarantined : quarantinedAllianceRecords) {
            if (quarantined != null) {
                allianceQuarantine.appendTag(quarantined.copy());
            }
        }
        nbt.setTag("AllianceMigrationQuarantine", allianceQuarantine);

        NBTTagList recoveredPostIds = new NBTTagList();
        for (String id : recoveredLegacyTradePostIds) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setString("Id", id);
            recoveredPostIds.appendTag(entry);
        }
        nbt.setTag("RecoveredLegacyTradePostIds", recoveredPostIds);
        NBTTagList postQuarantine = new NBTTagList();
        for (NBTTagCompound entry : quarantinedTradePostRecords) {
            if (entry != null) postQuarantine.appendTag(entry.copy());
        }
        nbt.setTag("TradePostMigrationQuarantine", postQuarantine);

        NBTTagList warList = new NBTTagList();
        for (KOMEWar war : wars.values()) {
            if (war != null && war.id != null && war.id.length() > 0
                    && !war.sideOneFactions.isEmpty() && !war.sideTwoFactions.isEmpty()) {
                warList.appendTag(war.writeToNBT());
            }
        }
        nbt.setTag("Wars", warList);
        NBTTagList confirmationList = new NBTTagList();
        long confirmationNow = System.currentTimeMillis();
        for (KOMEClaimConfirmation confirmation : conquestClaimConfirmations.values()) {
            if (confirmation != null && confirmation.player != null && confirmation.expiresAtMillis >= confirmationNow) {
                confirmationList.appendTag(confirmation.writeToNBT());
            }
        }
        nbt.setTag("ConquestClaimConfirmations", confirmationList);
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

    static ItemRecovery recoverTradePostStacks(KOMELegacyTradePostRecord post, KOMEAllianceFactionLedger ledger) {
        ItemRecovery result = new ItemRecovery();
        net.minecraft.item.ItemStack[][] inventories = new net.minecraft.item.ItemStack[][] {post.inputs, post.outputs};
        for (net.minecraft.item.ItemStack[] inventory : inventories) {
            for (net.minecraft.item.ItemStack stack : inventory) {
                if (stack == null || stack.stackSize <= 0) continue;
                ledger.addRecoveryStack(stack);
                result.stacks++;
                result.items += stack.stackSize;
            }
        }
        return result;
    }

    static class ItemRecovery {
        int stacks;
        int items;
    }
}
