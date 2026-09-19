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
    public static final String KOME_DATA_SCHEMA_KEY = "KOMEDataSchemaVersion";
    /** Schema 3 retires development-era player, tile and allocation population ledgers. */
    public static final int KOME_DATA_SCHEMA_VERSION = 3;
    private static final String AUTO_WAYPOINT_RALLY_SOURCE = "Auto LOTR waypoint";
    private static final double AUTO_RALLY_REFRESH_DISTANCE_SQ = 16.0D;
    public static final int ALLIANCE_DATA_SCHEMA_VERSION = KOMEAlliance.DATA_SCHEMA_VERSION;
    public static final int BUILD_DATA_SCHEMA_VERSION = KOMEPlayerBuild.DATA_SCHEMA_VERSION;
    public static final int FACTION_POPULATION_DATA_SCHEMA_VERSION = 2;
    public static final int POPULATION_PAYOUT_DATA_SCHEMA_VERSION = 1;

    /** The sole authoritative faction-wide spendable population banks. */
    public final Map<String, KOMEFactionPopulation> factionPopulations = new HashMap<String, KOMEFactionPopulation>();
    /** KOM-7 payout state; rate remains derived from Builds and configuration. */
    public boolean populationPayoutInitialized;
    public long lastPopulationPayoutBoundaryMillis = -1L;
    public String populationPayoutTimezone = "";
    public String populationPayoutLocalTime = "";
    /** Transient failed-plan diagnostic, never a cursor or a readiness authority. */
    public String populationPayoutLastFailure = "";
    public final Map<String, Long> populationPayoutRemainders = new HashMap<String, Long>();
    public final Map<UUID, KOMEPlayerProgression> progressions = new HashMap<>();
    public final Map<UUID, KOMEHiredUnitRecord> hiredUnits = new HashMap<>();
    public final Map<String, KOMEConquestTile> conquestTiles = new HashMap<>();
    public final Map<String, String> activeRecruitmentTiles = new HashMap<>();
    public final Map<String, KOMETileWaypoint> tileWaypoints = new HashMap<>();
    public final Map<String, KOMETileWaypointLink> tileWaypointLinksByTileId = new HashMap<>();
    public final Map<String, KOMEConquestRouteEdge> routeEdges = new HashMap<>();
    public final Map<String, KOMEPlayerBuild> builds = new HashMap<String, KOMEPlayerBuild>();
    /** Explicit future-construction grants; Build provenance is deliberately stored separately. */
    public final Map<String, KOMEForeignConstructionPermission> foreignConstructionPermissions = new HashMap<String, KOMEForeignConstructionPermission>();
    public final Map<String, KOMEAlliance> alliances = new HashMap<>();
    /** KOM-13 canonical bilateral diplomacy; legacy alliances remain separate compatibility state. */
    public final Map<String, KOMEDiplomacyRecord> canonicalDiplomacyRecords = new HashMap<String, KOMEDiplomacyRecord>();
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
    public final List<String> companyDelegationAudit = new ArrayList<String>();
    /** Cross-domain bounded audit stream; specialized histories remain authoritative for their domains. */
    public final List<KOMEAuditEntry> centralAudit = new ArrayList<KOMEAuditEntry>();
    public final Map<String, Integer> allianceRequirementOverrides = new HashMap<String, Integer>();
    public final Map<String, Integer> allianceQuotaWeightOverrides = new HashMap<String, Integer>();
    public final Map<String, Integer> allianceQuotaMaximumOverrides = new HashMap<String, Integer>();
    public final Map<String, Boolean> allianceQuotaEnabledOverrides = new HashMap<String, Boolean>();
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
    public int nextWarSequence = 1;
    /** The sole persisted campaign-season authority; population and unit records remain separate. */
    public final KOMEWarSeasonState warSeason = new KOMEWarSeasonState();
    public int nextBuildSequence = 1;
    public int allianceStageThreeRequiredHalfHours = KOMEAllianceProgressionService.DEFAULT_STAGE_THREE_REQUIRED_HALF_HOURS;
    public String allianceDifficulty = KOMEAllianceRequirements.STANDARD;
    public static final int MAX_MOVEMENT_HISTORY_PER_FACTION = 250;
    private boolean conquestDefaultsInitialized;
    private boolean allianceRelationsNeedReapply;
    private boolean integratedRootInitialized;
    private boolean writeBlocked;
    private String loadFailureReason = "";
    /** Candidate-only diagnostic context; never gameplay state or persisted data. */
    private String loadSection = "root";

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

    /**
     * Runs once from the authoritative server-tick START lifecycle after worlds and MapStorage exist.
     * Access through {@link #get(World)} deliberately does not invoke this method.
     */
    public synchronized boolean initializeIntegratedWorld() {
        ensureWritable();
        if (integratedRootInitialized) {
            return false;
        }
        applyWaypointDefaults(!conquestDefaultsInitialized);
        integratedRootInitialized = true;
        super.markDirty();
        return true;
    }

    public boolean isIntegratedRootInitialized() {
        return integratedRootInitialized;
    }

    public boolean isWriteBlocked() {
        return writeBlocked;
    }

    public String getLoadFailureReason() {
        return loadFailureReason;
    }

    @Override
    public void markDirty() {
        ensureWritable();
        super.markDirty();
    }

    void ensureWritable() {
        if (writeBlocked) {
            throw new IllegalStateException("KOME world data is write-blocked after a failed schema load: "
                + loadFailureReason);
        }
    }

    /**
     * Server-thread publication of one preflighted payout transition, including its audit.
     * Not a filesystem transaction. Rollback bypasses overridable WorldData mutation hooks.
     */
    final void publishPopulationPayout(Runnable publication) {
        ensureWritable();
        Map<String, KOMEFactionPopulation> banks = new HashMap<String, KOMEFactionPopulation>(factionPopulations);
        Map<String, Long> balances = new HashMap<String, Long>();
        for (Map.Entry<String, KOMEFactionPopulation> entry : banks.entrySet())
            balances.put(entry.getKey(), entry.getValue().getAvailablePopulationCenti());
        Map<String, Long> remainders = new HashMap<String, Long>(populationPayoutRemainders);
        List<KOMEAuditEntry> audit = new ArrayList<KOMEAuditEntry>(centralAudit);
        boolean initialized = populationPayoutInitialized;
        long cursor = lastPopulationPayoutBoundaryMillis;
        String timezone = populationPayoutTimezone, localTime = populationPayoutLocalTime;
        boolean dirty = super.isDirty();
        try {
            publication.run();
            markDirty();
        } catch (RuntimeException failure) {
            // KOMEFactionPopulation is final; these captured, validated balances cannot fail its setter.
            for (Map.Entry<String, KOMEFactionPopulation> entry : banks.entrySet())
                entry.getValue().setAvailablePopulationCenti(balances.get(entry.getKey()));
            factionPopulations.clear();
            factionPopulations.putAll(banks);
            populationPayoutRemainders.clear();
            populationPayoutRemainders.putAll(remainders);
            populationPayoutInitialized = initialized;
            lastPopulationPayoutBoundaryMillis = cursor;
            populationPayoutTimezone = timezone;
            populationPayoutLocalTime = localTime;
            centralAudit.clear();
            centralAudit.addAll(audit); // includes entries trimmed from the front by a failed append
            super.setDirty(dirty);
            throw failure;
        }
    }

    private void failUnsupportedRootSchema(String reason) {
        writeBlocked = true;
        loadFailureReason = reason == null ? "Unsupported KOME world-data schema." : reason;
        safeSchemaError(loadFailureReason);
        throw new IllegalStateException(loadFailureReason);
    }

    /** Controlled creation for world-data internals and deterministic tests. */
    public KOMEFactionPopulation getFactionPopulation(String faction) {
        String normalizedFaction = normalizeFactionPopulationKey(faction);
        KOMEFactionPopulation population = factionPopulations.get(normalizedFaction);
        if (population == null) {
            population = new KOMEFactionPopulation();
            factionPopulations.put(normalizedFaction, population);
        }
        return population;
    }

    public KOMEFactionPopulation getFactionPopulationIfPresent(String faction) {
        return factionPopulations.get(normalizeFactionPopulationKey(faction));
    }

    boolean trySpendFactionPopulationCenti(String faction, long amountCenti) {
        String normalizedFaction = normalizeFactionPopulationKey(faction);
        if (amountCenti < 0L) {
            throw new IllegalArgumentException("Population spend must not be negative: " + amountCenti);
        }
        KOMEFactionPopulation population = factionPopulations.get(normalizedFaction);
        if (population == null) {
            return amountCenti == 0L;
        }
        boolean spent = population.trySpendCenti(amountCenti);
        if (spent && amountCenti > 0L) {
            markDirty();
        }
        return spent;
    }

    void grantFactionPopulationCenti(String faction, long amountCenti) {
        String normalizedFaction = normalizeFactionPopulationKey(faction);
        if (amountCenti < 0L) {
            throw new IllegalArgumentException("Population grant must not be negative: " + amountCenti);
        }
        if (amountCenti == 0L) {
            return;
        }
        KOMEFactionPopulation population = factionPopulations.get(normalizedFaction);
        if (population == null) {
            population = new KOMEFactionPopulation();
            factionPopulations.put(normalizedFaction, population);
        }
        population.grantCenti(amountCenti);
        markDirty();
    }

    void setFactionPopulationCenti(String faction, long amountCenti) {
        String normalizedFaction = normalizeFactionPopulationKey(faction);
        if (amountCenti < 0L) {
            throw new IllegalArgumentException("Available centi-population must not be negative: " + amountCenti);
        }
        KOMEFactionPopulation population = factionPopulations.get(normalizedFaction);
        if (population == null) {
            if (amountCenti == 0L) {
                return;
            }
            population = new KOMEFactionPopulation();
            factionPopulations.put(normalizedFaction, population);
        }
        if (population.getAvailablePopulationCenti() != amountCenti) {
            population.setAvailablePopulationCenti(amountCenti);
            markDirty();
        }
    }

    private static String normalizeFactionPopulationKey(String faction) {
        String normalizedFaction = KOMEAlliance.normalizeFactionKey(faction);
        if (normalizedFaction.length() == 0) {
            throw new IllegalArgumentException("Faction population requires a nonblank faction key");
        }
        return normalizedFaction;
    }

    public KOMEPlayerProgression getProgression(UUID player) {
        KOMEPlayerProgression progression = progressions.get(player);
        if (progression == null) {
            progression = new KOMEPlayerProgression();
            progressions.put(player, progression);
        }
        return progression;
    }

    /** Read-only callers may inspect defaults without publishing a new authoritative record. */
    public KOMEPlayerProgression progressionForInspection(UUID player) {
        KOMEPlayerProgression existing = progressions.get(player);
        return existing == null ? new KOMEPlayerProgression() : existing;
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

    public void recordCompanyDelegationAudit(long nowMillis, String action, KOMEArmyCompany company,
            UUID actor, String actorName, UUID controller, String controllerName, String reason) {
        String companyId = company == null || company.id == null ? "" : company.id;
        String nativeFaction = company == null ? "" : KOMEWartimeStewardshipService.nativeFaction(company);
        UUID owner = company == null ? null : company.owner;
        String entry = Math.max(0L, nowMillis)
            + "|action=" + companyAuditValue(action)
            + "|company=" + companyAuditValue(companyId)
            + "|native=" + companyAuditValue(nativeFaction)
            + "|owner=" + (owner == null ? "" : owner.toString())
            + "|actor=" + (actor == null ? "" : actor.toString())
            + "|actorName=" + companyAuditValue(actorName)
            + "|controller=" + (controller == null ? "" : controller.toString())
            + "|controllerName=" + companyAuditValue(controllerName)
            + "|reason=" + companyAuditValue(reason);
        companyDelegationAudit.add(entry);
        while (companyDelegationAudit.size() > 250) {
            companyDelegationAudit.remove(0);
        }
        String details = "stewardship=" + stewardshipFingerprint(company, controller);
        boolean duplicateRevalidation = false;
        if ("STEWARDSHIP_REVALIDATED".equals(action)) {
            for (int i = centralAudit.size() - 1; i >= 0; i--) {
                KOMEAuditEntry previous = centralAudit.get(i);
                if (!"COMPANY".equals(previous.domain) || !companyId.equals(previous.subject)) continue;
                if (action.equals(previous.action)) { duplicateRevalidation = details.equals(previous.details); break; }
                if ("STEWARDSHIP_GRANTED".equals(previous.action) || "STEWARDSHIP_REVOKED".equals(previous.action)
                        || "STEWARDSHIP_DEMOBILIZED".equals(previous.action)) break;
            }
        }
        if (!duplicateRevalidation) KOMEAuditService.record(this, nowMillis, "COMPANY", action, actor == null ? "" : actor.toString(),
            companyId, reason, details);
        markDirty();
    }

    private static String safeAudit(String value) { return value == null ? "" : value.replace('|', ' ').replace('\n', ' ').replace('\r', ' ').trim(); }

    private static String stewardshipFingerprint(KOMEArmyCompany company, UUID controller) {
        String nativeFaction = company == null ? "" : KOMEWartimeStewardshipService.nativeFaction(company);
        String authority = company == null || company.controllerAuthority == null ? "" : company.controllerAuthority;
        List<String> wars = new ArrayList<String>();
        if (company != null) wars.addAll(company.authorizedWarIds);
        Collections.sort(wars);
        String withdrawal = company == null || company.withdrawalState == null ? "" : company.withdrawalState;
        return safeAudit(nativeFaction) + ";controller=" + (controller == null ? "" : controller.toString())
            + ";authority=" + safeAudit(authority) + ";wars=" + safeAudit(wars.toString())
            + ";withdrawal=" + safeAudit(withdrawal);
    }

    private static String companyAuditValue(String value) {
        return value == null ? "" : value.replace('|', ' ').replace('\n', ' ').replace('\r', ' ').trim();
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
        updateMovementHistory(order, status, true);
    }

    public void syncMovementHistory(KOMEArmyMovementOrder order, String status) {
        updateMovementHistory(order, status, false);
    }

    private void updateMovementHistory(KOMEArmyMovementOrder order, String status, boolean audit) {
        if (order == null || order.id == null || order.id.length() == 0) return;
        boolean firstSnapshot = !movementHistory.containsKey(order.id);
        KOMEMovementHistoryRecord record = getOrCreateMovementHistory(order);
        if (record == null) {
            return;
        }
        String previousStatus = record.status;
        record.updateFromOrder(order, status);
        if (audit && status != null && (firstSnapshot || !status.equals(previousStatus))) {
            String failure = order.pendingSpawnReason == null || order.pendingSpawnReason.length() == 0
                ? (order.lastSpawnFailureDetails == null ? "" : order.lastSpawnFailureDetails) : order.pendingSpawnReason;
            KOMEAuditService.record(this, System.currentTimeMillis(), "MOVEMENT", status, order.owner == null ? "" : order.owner.toString(),
                order.id, "Movement status transitioned", "company=" + order.companyId + ";accessLoss="
                    + (order.accessLossReason == null ? "" : order.accessLossReason) + ";failure=" + failure);
        }
        captureRouteSpecialEdges(record);
        pruneMovementHistoryForFaction(record.faction);
        markDirty();
    }

    public void markMovementHistoryStopped(KOMEArmyMovementOrder order, UUID stoppedByUuid, String stoppedByName, long nowMillis) {
        if (order == null || order.id == null || order.id.length() == 0) return;
        boolean firstSnapshot = !movementHistory.containsKey(order.id);
        KOMEMovementHistoryRecord record = getOrCreateMovementHistory(order);
        if (record == null) {
            return;
        }
        String previousStatus = record.status;
        record.updateFromOrder(order, KOMEMovementHistoryRecord.STOPPED);
        if (firstSnapshot || !KOMEMovementHistoryRecord.STOPPED.equals(previousStatus)) {
            KOMEAuditService.record(this, nowMillis, "MOVEMENT", KOMEMovementHistoryRecord.STOPPED,
                order.owner == null ? "" : order.owner.toString(), order.id, "Movement status transitioned",
                "company=" + order.companyId + ";accessLoss=" + (order.accessLossReason == null ? "" : order.accessLossReason));
        }
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

    public KOMEConquestTile getConquestTileIfPresent(String tileId) {
        return conquestTiles.get(KOMEConquestTile.normalizeId(tileId));
    }

    /** Public selection uses existing map metadata, never creates tiles or resolves world coordinates. */
    public KOMEConquestTile getPublicConquestTile(String tileId) {
        String id = KOMEConquestTile.normalizeId(tileId);
        if (!KOMEConquestTile.isCanonicalTileId(id) || KOMEConquestTileDefaults.isRetiredTile(id)
                || !KOMEConquestTileDefaults.getKnownTileIds().contains(id)) return null;
        return getConquestTileIfPresent(id);
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
            String tileId = KOMEConquestTileDefaults.getTileIdAtMapPosition(
                lotr.common.LOTRDimension.MIDDLE_EARTH.dimensionID, waypoint.getX(), waypoint.getY());
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
        conquestDefaultsInitialized = true;
        if (changed > 0) {
            KOMEMovementAccessService.revalidateAll(this, System.currentTimeMillis());
        }
        markDirty();
        syncConquestTiles();
        return changed;
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
        KOMEConquestTile controlled = conquestTiles.get(tile);
        if (playerId == null || faction.isEmpty() || controlled == null
                || !faction.equals(controlled.projectRulingFaction())) {
            return false;
        }
        return KOMEPopulationService.getRepresentedPopulationCenti(this, faction).signum() > 0;
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
        return tile != null && !faction.isEmpty() && faction.equals(tile.projectRulingFaction());
    }

    public boolean canFactionStandOnTile(String tileId, String factionKey) {
        String tileKey = KOMEConquestTile.normalizeId(tileId);
        String faction = KOMEAlliance.normalizeFactionKey(factionKey);
        KOMEConquestTile tile = conquestTiles.get(tileKey);
        return tile != null && !tile.projectRulingFaction().isEmpty() && canFactionUseMilitaryPassage(faction, tile.projectRulingFaction());
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
            KOMEAuditService.record(this, worldTime, "TILE", "OWNERSHIP_CHANGE", playerId == null ? "" : playerId.toString(),
                tile.id, "Tile ownership changed", previousOwner + " -> " + currentOwner);
        }
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

    public void reconcileBuildManagers() {
        for (KOMEPlayerBuild build : builds.values()) {
            KOMEBuildService.reconcileManager(this, build);
        }
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
        return KOMECompanyDiplomacyAuthorization
            .canUseMilitaryPassage(this, movingFaction, tileOwnerFaction).allowed;
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

    public boolean isFactionKing(String factionKey, UUID playerID) {
        return KOMERulerService.isRuler(this, factionKey, playerID);
    }

    public boolean hasFactionKing(String factionKey) {
        return KOMERulerService.hasRuler(this, factionKey);
    }

    public UUID getFactionKingId(String factionKey) {
        return KOMERulerService.getRuler(this, factionKey);
    }

    public String getFactionKingName(String factionKey) {
        return KOMERulerService.getRulerName(this, factionKey);
    }

    UUID readFactionKingId(String factionKey) {
        return kingsByFaction.get(normalizeFactionKey(factionKey));
    }

    String readFactionKingName(String factionKey) {
        String name = kingNamesByFaction.get(normalizeFactionKey(factionKey));
        return name == null ? "" : name;
    }

    Map<String, UUID> factionKingRecordsSnapshot() {
        return new HashMap<String, UUID>(kingsByFaction);
    }

    void writeFactionKingRecord(String factionKey, UUID playerID, String playerName) {
        String key = normalizeFactionKey(factionKey);
        kingsByFaction.put(key, playerID);
        kingNamesByFaction.put(key, playerName == null ? "" : playerName);
    }

    boolean removeFactionKingRecord(String factionKey) {
        String key = normalizeFactionKey(factionKey);
        boolean existed = kingsByFaction.containsKey(key) || kingNamesByFaction.containsKey(key);
        kingsByFaction.remove(key);
        kingNamesByFaction.remove(key);
        return existed;
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
            KOMEDiplomacyService.reapplyLotrProjection(this);
            allianceRelationsNeedReapply = false;
        }
        // Schema 7 deliberately has no king-loss or contribution grace lifecycle.  Losing or
        // gaining a king preserves every directional stage and unlocked benefit.
        return false;
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

    /** A movement label alone never authorizes preserving a physically removed unit. */
    public boolean hasValidHiredUnitMovementLink(KOMEHiredUnitRecord record) {
        if (record == null || !record.isMoving()) return false;
        KOMEArmyMovementOrder order = armyMovements.get(record.movementOrderId);
        return order != null && record.movementOrderId.equals(order.id) && order.isMoving()
            && order.units.contains(record.entity)
            && java.util.Objects.equals(record.companyId, order.companyId);
    }

    public boolean isVirtualMovingHiredUnit(KOMEHiredUnitRecord record) {
        return hasValidHiredUnitMovementLink(record) && record.movingEntityData != null;
    }

    /** Server START lifecycle repair, never invoked by an accessor or projection. */
    public int reconcileHiredUnitMovementLinks() {
        ensureWritable();
        int repaired = 0;
        for (KOMEHiredUnitRecord record : hiredUnits.values()) {
            if (record == null || !record.isMoving() || hasValidHiredUnitMovementLink(record)) continue;
            String invalidOrder = record.movementOrderId;
            // Preserve the unit and its last saved entity for existing stationary recovery.
            // Missing movement metadata is not proof of a death and must not expire a record.
            if (record.movingEntityData != null) {
                if (record.stationedEntityData == null)
                    record.stationedEntityData = (NBTTagCompound) record.movingEntityData.copy();
                record.movingEntityData = null;
            }
            record.movementOrderId = "";
            KOMEArmyCompany company = armyCompanies.get(record.companyId);
            if (company != null && invalidOrder.equals(company.movementOrderId)) {
                KOMEArmyMovementOrder order = armyMovements.get(invalidOrder);
                if (order == null || !order.isMoving() || !company.id.equals(order.companyId)) {
                    company.movementOrderId = "";
                    company.status = KOMEArmyCompany.STATIONED;
                }
            }
            KOMEAuditService.record(this, System.currentTimeMillis(), "UNIT", "INVALID_MOVEMENT_LINK", "",
                String.valueOf(record.entity), "Cleared invalid movement link; investment unchanged", invalidOrder);
            repaired++;
        }
        if (repaired > 0) markDirty();
        return repaired;
    }

    /** Authoritative death/dismissal cleanup. Deliberate virtual despawns are not terminal. */
    public KOMEHiredUnitRecord removeTerminatedHiredUnit(UUID entityId, String reason) {
        ensureWritable();
        KOMEHiredUnitRecord record = hiredUnits.get(entityId);
        if (record == null || isVirtualMovingHiredUnit(record)) return null;
        hiredUnits.remove(entityId);
        KOMEArmyMovementOrder order = armyMovements.get(record.movementOrderId);
        if (order != null && order.isMoving()) order.units.remove(entityId);
        removeUnitFromCompany(record);
        KOMEAuditService.record(this, System.currentTimeMillis(), "UNIT", "REMOVED", "",
            String.valueOf(entityId), reason, "Population remains permanently spent");
        markDirty();
        return record;
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
            if (isVirtualMovingHiredUnit(record)) {
                continue;
            }
            if (!npc.isEntityAlive() || npc.hiredNPCInfo == null || !npc.hiredNPCInfo.isActive) {
                inactiveUnits.add(entityID);
            }
        }
        for (UUID entityID : inactiveUnits) {
            removeTerminatedHiredUnit(entityID, "Loaded unit is dead or dismissed");
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
        String companyFaction = companyFaction(record);
        KOMEArmyCompany company = findHiringCompany(record.owner, sourceTile, companyFaction);
        if (company == null) {
            String id = hiringCompanyId(record.owner, sourceTile);
            if (armyCompanies.containsKey(id)) {
                String suffix = companyFaction.length() == 0 ? "unknown" : companyFaction.toLowerCase(java.util.Locale.ROOT);
                id = id + "_" + suffix;
                int collision = 2;
                while (armyCompanies.containsKey(id)) id = hiringCompanyId(record.owner, sourceTile) + "_" + suffix + "_" + collision++;
            }
            company = new KOMEArmyCompany();
            company.id = id;
            company.owner = record.owner;
            company.ownerName = ownerName == null || ownerName.length() == 0
                ? safePlayerName(record.owner) : ownerName;
            company.faction = companyFaction;
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

    private KOMEArmyCompany findHiringCompany(UUID owner, String sourceTile, String faction) {
        String tile = KOMEConquestTile.normalizeId(sourceTile);
        String normalizedFaction = normalizeFactionKey(faction);
        KOMEArmyCompany result = null;
        for (KOMEArmyCompany candidate : armyCompanies.values()) {
            if (candidate == null || owner == null || !owner.equals(candidate.owner)
                    || !KOMEArmyCompany.SOURCE_AUTO_UNIT_ASSIGNMENT.equals(candidate.source)
                    || !tile.equals(KOMEConquestTile.normalizeId(candidate.sourceTileId))
                    || !normalizedFaction.equals(normalizeFactionKey(candidate.faction))) continue;
            if (result == null || candidate.createdAtMillis < result.createdAtMillis
                    || candidate.createdAtMillis == result.createdAtMillis
                        && candidate.id.compareTo(result.id) < 0) {
                result = candidate;
            }
        }
        return result;
    }

    private String companyFaction(KOMEHiredUnitRecord record) {
        return "MILITARY_T3_STEWARDSHIP".equals(record.benefitSource)
            ? normalizeFactionKey(record.unitFaction) : normalizeFactionKey(getPlayerFactionKey(record.owner));
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

    public static String tileWaypointKey(String tileId, String type) {
        return KOMEConquestTile.normalizeId(tileId) + "|" + KOMETileWaypoint.normalizeType(type);
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

    @Override
    public synchronized void readFromNBT(NBTTagCompound nbt) {
        ensureWritable();
        KOMEWorldData candidate = new KOMEWorldData(DATA_NAME);
        try {
            // Preserve the existing empty-root lifecycle: defaults are installed only at START tick.
            if (nbt != null && nbt.hasNoTags()) {
                integratedRootInitialized = false;
                return;
            }
            // Readers may retain nested tags. Neither candidate reconciliation nor later gameplay
            // may share mutable NBT with the caller's persisted source.
            candidate.readCandidateFromNBT(nbt == null ? null : (NBTTagCompound) nbt.copy());
        } catch (RuntimeException invalid) {
            writeBlocked = true;
            loadFailureReason = "Invalid KOME world data in " + candidate.loadSection + ": "
                + invalid.getMessage();
            safeSchemaError(loadFailureReason);
            throw new IllegalStateException(loadFailureReason, invalid);
        }
        publishLoadedState(candidate);
    }

    /** Unregistered, short-lived candidate; all existing recovery/reconciliation runs here. */
    private void readCandidateFromNBT(NBTTagCompound nbt) {
        if (writeBlocked) {
            ensureWritable();
        }
        if (nbt == null) {
            failUnsupportedRootSchema("KOME world data could not be loaded because its root tag is missing.");
        }
        if (nbt.hasNoTags()) {
            integratedRootInitialized = false;
            return;
        }
        if (!nbt.hasKey(KOME_DATA_SCHEMA_KEY)) {
            failUnsupportedRootSchema("KOME world data is non-empty but has no " + KOME_DATA_SCHEMA_KEY
                + " marker. Development-world migration is intentionally disabled.");
        }
        int savedRootSchema = nbt.getInteger(KOME_DATA_SCHEMA_KEY);
        if (savedRootSchema != KOME_DATA_SCHEMA_VERSION) {
            failUnsupportedRootSchema("Unsupported KOME world-data schema " + savedRootSchema + "; expected "
                + KOME_DATA_SCHEMA_VERSION + ". Reset this development world; migration is intentionally disabled.");
        }
        for (String retired : new String[] {"Populations", "TilePopulations", "PopulationAllocations", "PopulationDataSchemaVersion"}) {
            if (nbt.hasKey(retired)) {
                failUnsupportedRootSchema("Retired population data " + retired
                    + " is incompatible with KOME schema " + KOME_DATA_SCHEMA_VERSION
                    + ". Reset this development world; no population migration is supported.");
            }
        }
        boolean loadedStateReconciled = false;
        int savedAllianceSchema = nbt.hasKey("AllianceDataSchemaVersion") ? nbt.getInteger("AllianceDataSchemaVersion") : 0;
        loadSection = "FactionPopulations";
        Map<String, KOMEFactionPopulation> loadedPopulations = readCanonicalFactionPopulations(nbt);
        // Validate every Build before publishing any loaded state or clearing current collections.
        loadSection = "Builds";
        Map<String, KOMEPlayerBuild> loadedBuilds = readCanonicalBuilds(nbt);
        loadSection = "PopulationPayout";
        Map<String, Long> loadedRemainders = readCanonicalPayoutState(nbt);
        integratedRootInitialized = true;
        factionPopulations.clear();
        populationPayoutInitialized = nbt.getBoolean("PopulationPayoutInitialized");
        lastPopulationPayoutBoundaryMillis = populationPayoutInitialized ? nbt.getLong("LastPopulationPayoutBoundaryMillis") : -1L;
        populationPayoutRemainders.clear();
        populationPayoutRemainders.putAll(loadedRemainders);
        populationPayoutTimezone = nbt.getString("PopulationPayoutTimezone");
        populationPayoutLocalTime = nbt.getString("PopulationPayoutLocalTime");
        populationPayoutLastFailure = "";
        progressions.clear();
        hiredUnits.clear();
        conquestTiles.clear();
        activeRecruitmentTiles.clear();
        tileWaypoints.clear();
        tileWaypointLinksByTileId.clear();
        routeEdges.clear();
        builds.clear();
        foreignConstructionPermissions.clear();
        alliances.clear();
        canonicalDiplomacyRecords.clear();
        recoveredLegacyTradePostIds.clear();
        quarantinedTradePostRecords.clear();
        wars.clear();
        conquestClaimConfirmations.clear();
        lastKnownPlayerFactions.clear();
        pledgeReleaseTombstones.clear();
        pledgeReleaseQuarantine.clear();
        pledgeReleaseLastResults.clear();
        pledgeReleaseAudit.clear();
        companyDelegationAudit.clear();
        centralAudit.clear();
        allianceRequirementOverrides.clear();
        allianceQuotaWeightOverrides.clear();
        allianceQuotaMaximumOverrides.clear();
        allianceQuotaEnabledOverrides.clear();
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
        nextWarSequence = nbt.hasKey("NextWarSequence") ? Math.max(1, nbt.getInteger("NextWarSequence")) : 1;
        loadSection = "WarSeason";
        warSeason.readFromNBT(nbt.getCompoundTag("WarSeason"));
        nextBuildSequence = nbt.hasKey("NextBuildSequence") ? Math.max(1, nbt.getInteger("NextBuildSequence")) : 1;
        allianceStageThreeRequiredHalfHours = nbt.hasKey("AllianceStageThreeRequiredHalfHours")
            ? Math.max(1, nbt.getInteger("AllianceStageThreeRequiredHalfHours"))
            : KOMEAllianceProgressionService.DEFAULT_STAGE_THREE_REQUIRED_HALF_HOURS;
        allianceDifficulty = KOMEAllianceRequirements.normalizeDifficulty(nbt.getString("AllianceDifficulty"));
        loadSection = "AllianceRequirementOverrides";
        NBTTagList requirementConfig = nbt.getTagList("AllianceRequirementOverrides", 10);
        for (int i = 0; i < requirementConfig.tagCount(); i++) {
            loadSection = "AllianceRequirementOverrides[" + i + "]";
            NBTTagCompound entry = requirementConfig.getCompoundTagAt(i);
            String key = entry.getString("Key");
            if (key.length() > 0) {
                allianceRequirementOverrides.put(key, Integer.valueOf(Math.max(0, entry.getInteger("Value"))));
            }
        }
        loadSection = "AllianceQuotaItemOverrides";
        NBTTagList quotaItemConfig = nbt.getTagList("AllianceQuotaItemOverrides", 10);
        for (int i = 0; i < quotaItemConfig.tagCount(); i++) {
            loadSection = "AllianceQuotaItemOverrides[" + i + "]";
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
        loadSection = "AllianceAdminAudit";
        NBTTagList adminAuditList = nbt.getTagList("AllianceAdminAudit", 10);
        for (int i = 0; i < adminAuditList.tagCount() && allianceAdminAudit.size() < 200; i++) {
            String entry = adminAuditList.getCompoundTagAt(i).getString("Entry");
            if (entry.length() > 0) {
                allianceAdminAudit.add(entry);
            }
        }
        loadSection = "CentralAudit";
        KOMEAuditService.readFromNBT(this, nbt);
        conquestDefaultsInitialized = nbt.hasKey("ConquestDefaultsInitialized") && nbt.getBoolean("ConquestDefaultsInitialized");

        factionPopulations.putAll(loadedPopulations);

        loadSection = "CanonicalDiplomacyRecords";
        NBTTagList diplomacyList = nbt.getTagList("CanonicalDiplomacyRecords", 10);
        for (int i = 0; i < diplomacyList.tagCount(); i++) {
            loadSection = "CanonicalDiplomacyRecords[" + i + "]";
            try { KOMEDiplomacyRecord record = KOMEDiplomacyRecord.readFromNBT(diplomacyList.getCompoundTagAt(i)); canonicalDiplomacyRecords.put(record.key(), record); }
            catch (IllegalArgumentException ignored) { }
        }

        loadSection = "Progressions";
        NBTTagList progressionList = nbt.getTagList("Progressions", 10);
        for (int i = 0; i < progressionList.tagCount(); i++) {
            loadSection = "Progressions[" + i + "]";
            NBTTagCompound entry = progressionList.getCompoundTagAt(i);
            KOMEPlayerProgression progression = new KOMEPlayerProgression();
            progression.readFromNBT(entry);
            progressions.put(UUID.fromString(entry.getString("Player")), progression);
        }

        loadSection = "PlayerNames";
        NBTTagList playerNameList = nbt.getTagList("PlayerNames", 10);
        for (int i = 0; i < playerNameList.tagCount(); i++) {
            loadSection = "PlayerNames[" + i + "]";
            NBTTagCompound entry = playerNameList.getCompoundTagAt(i);
            String player = entry.getString("Player");
            String name = entry.getString("Name");
            if (player.length() > 0 && name.length() > 0) {
                playerNames.put(UUID.fromString(player), name);
            }
        }

        loadSection = "LastKnownPlayerFactions";
        NBTTagList knownFactionList = nbt.getTagList("LastKnownPlayerFactions", 10);
        for (int i = 0; i < knownFactionList.tagCount(); i++) {
            loadSection = "LastKnownPlayerFactions[" + i + "]";
            NBTTagCompound entry = knownFactionList.getCompoundTagAt(i);
            try {
                UUID player = UUID.fromString(entry.getString("Player"));
                lastKnownPlayerFactions.put(player, KOMEAlliance.normalizeFactionKey(entry.getString("Faction")));
            } catch (IllegalArgumentException ignored) {
            }
        }
        loadSection = "PledgeReleaseTombstones";
        NBTTagList tombstoneList = nbt.getTagList("PledgeReleaseTombstones", 10);
        for (int i = 0; i < tombstoneList.tagCount(); i++) {
            loadSection = "PledgeReleaseTombstones[" + i + "]";
            KOMEPledgeReleaseTombstone tombstone = new KOMEPledgeReleaseTombstone();
            tombstone.readFromNBT(tombstoneList.getCompoundTagAt(i));
            if (tombstone.unitUuid != null) pledgeReleaseTombstones.put(tombstone.unitUuid, tombstone);
        }
        loadSection = "PledgeReleaseQuarantine";
        NBTTagList releaseQuarantineList = nbt.getTagList("PledgeReleaseQuarantine", 10);
        for (int i = 0; i < releaseQuarantineList.tagCount(); i++) {
            loadSection = "PledgeReleaseQuarantine[" + i + "]";
            NBTTagCompound entry = releaseQuarantineList.getCompoundTagAt(i);
            try {
                UUID unit = UUID.fromString(entry.getString("Unit"));
                if (entry.hasKey("Record", 10)) pledgeReleaseQuarantine.put(unit, entry.getCompoundTag("Record"));
            } catch (IllegalArgumentException ignored) {
            }
        }
        loadSection = "PledgeReleaseLastResults";
        NBTTagList releaseResultList = nbt.getTagList("PledgeReleaseLastResults", 10);
        for (int i = 0; i < releaseResultList.tagCount(); i++) {
            loadSection = "PledgeReleaseLastResults[" + i + "]";
            NBTTagCompound entry = releaseResultList.getCompoundTagAt(i);
            try {
                UUID player = UUID.fromString(entry.getString("Player"));
                pledgeReleaseLastResults.put(player, entry.getString("Result"));
            } catch (IllegalArgumentException ignored) {
            }
        }
        loadSection = "PledgeReleaseAudit";
        NBTTagList releaseAuditList = nbt.getTagList("PledgeReleaseAudit", 10);
        for (int i = 0; i < releaseAuditList.tagCount() && pledgeReleaseAudit.size() < 250; i++) {
            String entry = releaseAuditList.getCompoundTagAt(i).getString("Entry");
            if (entry.length() > 0) pledgeReleaseAudit.add(entry);
        }

        loadSection = "CompanyDelegationAudit";
        NBTTagList companyDelegationAuditList = nbt.getTagList("CompanyDelegationAudit", 10);
        for (int i = 0; i < companyDelegationAuditList.tagCount() && companyDelegationAudit.size() < 250; i++) {
            String entry = companyDelegationAuditList.getCompoundTagAt(i).getString("Entry");
            if (entry.length() > 0) {
                companyDelegationAudit.add(entry);
            }
        }
        loadSection = "FactionKings";
        NBTTagList kingList = nbt.getTagList("FactionKings", 10);
        for (int i = 0; i < kingList.tagCount(); i++) {
            loadSection = "FactionKings[" + i + "]";
            NBTTagCompound entry = kingList.getCompoundTagAt(i);
            String faction = normalizeFactionKey(entry.getString("Faction"));
            String player = entry.getString("Player");
            if (faction.length() > 0 && player.length() > 0) {
                try {
                    kingsByFaction.put(faction, UUID.fromString(player));
                    kingNamesByFaction.put(faction, entry.getString("Name"));
                } catch (IllegalArgumentException ignored) {
                    // Invalid persisted ruler records are ignored rather than inventing a ruler.
                }
            }
        }

        loadSection = "AdminUnitMapMarkerOptOuts";
        NBTTagList adminMarkerList = nbt.getTagList("AdminUnitMapMarkerOptOuts", 10);
        for (int i = 0; i < adminMarkerList.tagCount(); i++) {
            loadSection = "AdminUnitMapMarkerOptOuts[" + i + "]";
            String player = adminMarkerList.getCompoundTagAt(i).getString("Player");
            if (player.length() > 0) {
                adminUnitMapMarkerOptOuts.add(UUID.fromString(player));
            }
        }

        loadSection = "HiredUnits";
        NBTTagList hiredList = nbt.getTagList("HiredUnits", 10);
        for (int i = 0; i < hiredList.tagCount(); i++) {
            loadSection = "HiredUnits[" + i + "]";
            KOMEHiredUnitRecord record = new KOMEHiredUnitRecord();
            record.readFromNBT(hiredList.getCompoundTagAt(i));
            hiredUnits.put(record.entity, record);
        }

        loadSection = "ConquestTiles";
        NBTTagList conquestList = nbt.getTagList("ConquestTiles", 10);
        for (int i = 0; i < conquestList.tagCount(); i++) {
            loadSection = "ConquestTiles[" + i + "]";
            KOMEConquestTile tile = new KOMEConquestTile("");
            tile.readFromNBT(conquestList.getCompoundTagAt(i));
            if (!tile.id.isEmpty()) {
                conquestTiles.put(tile.id, tile);
            }
        }

        builds.putAll(loadedBuilds);
        loadSection = "ForeignConstructionPermissions";
        NBTTagList foreignConstructionList = nbt.getTagList("ForeignConstructionPermissions", 10);
        for (int i = 0; i < foreignConstructionList.tagCount(); i++) {
            loadSection = "ForeignConstructionPermissions[" + i + "]";
            KOMEForeignConstructionPermission permission = new KOMEForeignConstructionPermission();
            if (permission.readFromNBT(foreignConstructionList.getCompoundTagAt(i))) foreignConstructionPermissions.put(permission.key(), permission);
        }
        loadSection = "ActiveRecruitmentTiles";
        NBTTagList recruitmentTileList = nbt.getTagList("ActiveRecruitmentTiles", 10);
        for (int i = 0; i < recruitmentTileList.tagCount(); i++) {
            loadSection = "ActiveRecruitmentTiles[" + i + "]";
            NBTTagCompound entry = recruitmentTileList.getCompoundTagAt(i);
            String faction = KOMEAlliance.normalizeFactionKey(entry.getString("Faction"));
            String player = entry.getString("Player");
            String tile = KOMEConquestTile.normalizeId(entry.getString("Tile"));
            if (faction.length() > 0 && player.length() > 0 && tile.length() > 0) {
                activeRecruitmentTiles.put(recruitmentTileKey(faction, UUID.fromString(player)), tile);
            }
        }

        loadSection = "TileWaypoints";
        NBTTagList waypointList = nbt.getTagList("TileWaypoints", 10);
        for (int i = 0; i < waypointList.tagCount(); i++) {
            loadSection = "TileWaypoints[" + i + "]";
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
                loadedStateReconciled = true;
            }
        }

        loadSection = "TileWaypointLinks";
        NBTTagList tileWaypointLinkList = nbt.getTagList("TileWaypointLinks", 10);
        for (int i = 0; i < tileWaypointLinkList.tagCount(); i++) {
            loadSection = "TileWaypointLinks[" + i + "]";
            KOMETileWaypointLink link = new KOMETileWaypointLink();
            link.readFromNBT(tileWaypointLinkList.getCompoundTagAt(i));
            if (link.tileId.length() > 0 && link.lotrWaypointKey.length() > 0) {
                tileWaypointLinksByTileId.put(link.tileId, link);
            }
        }

        loadSection = "RouteEdges";
        NBTTagList routeEdgeList = nbt.getTagList("RouteEdges", 10);
        for (int i = 0; i < routeEdgeList.tagCount(); i++) {
            loadSection = "RouteEdges[" + i + "]";
            KOMEConquestRouteEdge edge = new KOMEConquestRouteEdge();
            edge.readFromNBT(routeEdgeList.getCompoundTagAt(i));
            if (edge.fromTile.length() > 0 && edge.toTile.length() > 0 && !edge.fromTile.equals(edge.toTile)) {
                routeEdges.put(KOMEConquestRouteEdge.key(edge.fromTile, edge.toTile), edge);
            }
        }

        loadSection = "Build manager reconciliation";
        reconcileBuildManagers();

        int allianceLoaded = 0;
        int allianceMerged = 0;
        int allianceDuplicate = 0;
        int allianceQuarantined = 0;
        Set<String> seenAllianceTags = new HashSet<String>();
        loadSection = "AllianceMigrationQuarantine";
        NBTTagList previousQuarantine = nbt.getTagList("AllianceMigrationQuarantine", 10);
        for (int i = 0; i < previousQuarantine.tagCount(); i++) {
            loadSection = "AllianceMigrationQuarantine[" + i + "]";
            quarantinedAllianceRecords.add((NBTTagCompound) previousQuarantine.getCompoundTagAt(i).copy());
        }

        loadSection = "RecoveredLegacyTradePostIds";
        NBTTagList recoveredPostIds = nbt.getTagList("RecoveredLegacyTradePostIds", 10);
        for (int i = 0; i < recoveredPostIds.tagCount(); i++) {
            loadSection = "RecoveredLegacyTradePostIds[" + i + "]";
            String id = recoveredPostIds.getCompoundTagAt(i).getString("Id");
            if (id.length() > 0) recoveredLegacyTradePostIds.add(id);
        }
        loadSection = "TradePostMigrationQuarantine";
        NBTTagList previousPostQuarantine = nbt.getTagList("TradePostMigrationQuarantine", 10);
        for (int i = 0; i < previousPostQuarantine.tagCount(); i++) {
            loadSection = "TradePostMigrationQuarantine[" + i + "]";
            quarantinedTradePostRecords.add((NBTTagCompound) previousPostQuarantine.getCompoundTagAt(i).copy());
        }
        loadSection = "Alliances";
        NBTTagList allianceList = nbt.getTagList("Alliances", 10);
        for (int i = 0; i < allianceList.tagCount(); i++) {
            loadSection = "Alliances[" + i + "]";
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
            } catch (RuntimeException problem) {
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

        loadSection = "AllianceTradePosts";
        NBTTagList tradePostList = nbt.getTagList("AllianceTradePosts", 10);
        int recoveredPostCount = 0;
        int recoveredPostStacks = 0;
        int recoveredPostItems = 0;
        for (int i = 0; i < tradePostList.tagCount(); i++) {
            loadSection = "AllianceTradePosts[" + i + "]";
            NBTTagCompound savedPost = tradePostList.getCompoundTagAt(i);
            KOMELegacyTradePostRecord post = new KOMELegacyTradePostRecord();
            post.readFromNBT(savedPost);
            if (post.id.length() > 0 && post.operatingFaction.length() > 0 && post.hostFaction.length() > 0
                    && !post.operatingFaction.equals(post.hostFaction)) {
                if (post.legacyFarmerReservation) {
                    post.legacyFarmerReservation = false;
                    loadedStateReconciled = true;
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
            loadedStateReconciled = true;
        }

        loadSection = "Wars";
        NBTTagList warList = nbt.getTagList("Wars", 10);
        for (int i = 0; i < warList.tagCount(); i++) {
            loadSection = "Wars[" + i + "]";
            KOMEWar war = new KOMEWar();
            war.readFromNBT(warList.getCompoundTagAt(i));
            if (war.id.length() > 0 && !war.sideOneFactions.isEmpty() && !war.sideTwoFactions.isEmpty()) {
                wars.put(war.id, war);
            }
        }
        loadSection = "ConquestClaimConfirmations";
        NBTTagList confirmationList = nbt.getTagList("ConquestClaimConfirmations", 10);
        long confirmationNow = System.currentTimeMillis();
        for (int i = 0; i < confirmationList.tagCount(); i++) {
            loadSection = "ConquestClaimConfirmations[" + i + "]";
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
        loadSection = "AllianceProduceSlots";
        NBTTagList retiredProduceSlots = nbt.getTagList("AllianceProduceSlots", 10);
        int retiredProduceRecords = retiredProduceSlots.tagCount();
        int recoveredProduceStacks = 0;
        int quarantinedProduceRecords = 0;
        for (int i = 0; i < retiredProduceSlots.tagCount(); i++) {
            loadSection = "AllianceProduceSlots[" + i + "]";
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
            loadedStateReconciled = true;
        }
        loadSection = "ArmyMovements";
        NBTTagList movementList = nbt.getTagList("ArmyMovements", 10);
        for (int i = 0; i < movementList.tagCount(); i++) {
            loadSection = "ArmyMovements[" + i + "]";
            KOMEArmyMovementOrder order = new KOMEArmyMovementOrder();
            order.readFromNBT(movementList.getCompoundTagAt(i));
            if (order.id.length() > 0) {
                armyMovements.put(order.id, order);
            }
        }

        loadSection = "MovementHistory";
        NBTTagList historyList = nbt.getTagList("MovementHistory", 10);
        for (int i = 0; i < historyList.tagCount(); i++) {
            loadSection = "MovementHistory[" + i + "]";
            KOMEMovementHistoryRecord record = new KOMEMovementHistoryRecord();
            record.readFromNBT(historyList.getCompoundTagAt(i));
            if (record.historyId.length() > 0) {
                movementHistory.put(record.historyId, record);
            }
        }

        loadSection = "ArmyCompanies";
        NBTTagList companyList = nbt.getTagList("ArmyCompanies", 10);
        for (int i = 0; i < companyList.tagCount(); i++) {
            loadSection = "ArmyCompanies[" + i + "]";
            KOMEArmyCompany company = new KOMEArmyCompany();
            company.readFromNBT(companyList.getCompoundTagAt(i));
            if (company.id.length() > 0 && company.owner != null) {
                armyCompanies.put(company.id, company);
            }
        }
        loadSection = "Company membership reconciliation";
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
                loadedStateReconciled = true;
            }
        }

        if (savedAllianceSchema < 5) {
            safeAllianceInfo("[KOME] Alliance schema-5 migration: coalition wars initialized without inferred history; "
                + "peacetime kingless stewardship revoked; Trade Posts recovered to ledgers; pledge-release tracking enabled.");
            loadedStateReconciled = true;
        }

        loadSection = "Conquest and waypoint reconciliation";
        if (pruneRetiredConquestTileData()) {
            loadedStateReconciled = true;
        }

        if (applyWaypointDefaults(!conquestDefaultsInitialized)) {
            loadedStateReconciled = true;
        }

        loadSection = "War, stewardship and movement restart reconciliation";
        long restartRevalidationNow = System.currentTimeMillis();
        KOMEWarService.reconcileAutomaticMilitarySupport(this, restartRevalidationNow, "World load reconciliation");
        KOMEWartimeStewardshipService.revalidateAll(this, restartRevalidationNow,
            savedAllianceSchema < 5 ? "Schema-5 removed peacetime kingless stewardship" : "Restart authorization revalidation");
        KOMECommandTroops.revalidateTemporaryControllers(this, restartRevalidationNow, "Restart authorization revalidation");
        KOMEMovementAccessService.revalidateAll(this, restartRevalidationNow);

        if (loadedStateReconciled || migratedAllianceData) {
            markDirty();
        }
    }

    /**
     * Publishes an entirely parsed/reconciled candidate on the server thread. The final,
     * concrete HashMap/HashSet/ArrayList containers keep their identities for existing callers.
     * Keys are Strings/UUIDs; this phase invokes no readers, services, validation or overridable
     * mutation hooks. Allocation failures (JVM Errors) are not recoverable load rejections.
     * This is coherent in-memory publication, not a crash-durable filesystem transaction.
     */
    private void publishLoadedState(KOMEWorldData candidate) {
        factionPopulations.clear();
        factionPopulations.putAll(candidate.factionPopulations);
        populationPayoutRemainders.clear();
        populationPayoutRemainders.putAll(candidate.populationPayoutRemainders);
        progressions.clear();
        progressions.putAll(candidate.progressions);
        hiredUnits.clear();
        hiredUnits.putAll(candidate.hiredUnits);
        conquestTiles.clear();
        conquestTiles.putAll(candidate.conquestTiles);
        activeRecruitmentTiles.clear();
        activeRecruitmentTiles.putAll(candidate.activeRecruitmentTiles);
        tileWaypoints.clear();
        tileWaypoints.putAll(candidate.tileWaypoints);
        tileWaypointLinksByTileId.clear();
        tileWaypointLinksByTileId.putAll(candidate.tileWaypointLinksByTileId);
        routeEdges.clear();
        routeEdges.putAll(candidate.routeEdges);
        builds.clear();
        builds.putAll(candidate.builds);
        foreignConstructionPermissions.clear();
        foreignConstructionPermissions.putAll(candidate.foreignConstructionPermissions);
        alliances.clear();
        alliances.putAll(candidate.alliances);
        canonicalDiplomacyRecords.clear();
        canonicalDiplomacyRecords.putAll(candidate.canonicalDiplomacyRecords);
        recoveredLegacyTradePostIds.clear();
        recoveredLegacyTradePostIds.addAll(candidate.recoveredLegacyTradePostIds);
        quarantinedTradePostRecords.clear();
        quarantinedTradePostRecords.addAll(candidate.quarantinedTradePostRecords);
        wars.clear();
        wars.putAll(candidate.wars);
        conquestClaimConfirmations.clear();
        conquestClaimConfirmations.putAll(candidate.conquestClaimConfirmations);
        lastKnownPlayerFactions.clear();
        lastKnownPlayerFactions.putAll(candidate.lastKnownPlayerFactions);
        pledgeReleaseTombstones.clear();
        pledgeReleaseTombstones.putAll(candidate.pledgeReleaseTombstones);
        pledgeReleaseQuarantine.clear();
        pledgeReleaseQuarantine.putAll(candidate.pledgeReleaseQuarantine);
        pledgeReleaseLastResults.clear();
        pledgeReleaseLastResults.putAll(candidate.pledgeReleaseLastResults);
        pledgeReleaseAudit.clear();
        pledgeReleaseAudit.addAll(candidate.pledgeReleaseAudit);
        companyDelegationAudit.clear();
        companyDelegationAudit.addAll(candidate.companyDelegationAudit);
        centralAudit.clear();
        centralAudit.addAll(candidate.centralAudit);
        allianceRequirementOverrides.clear();
        allianceRequirementOverrides.putAll(candidate.allianceRequirementOverrides);
        allianceQuotaWeightOverrides.clear();
        allianceQuotaWeightOverrides.putAll(candidate.allianceQuotaWeightOverrides);
        allianceQuotaMaximumOverrides.clear();
        allianceQuotaMaximumOverrides.putAll(candidate.allianceQuotaMaximumOverrides);
        allianceQuotaEnabledOverrides.clear();
        allianceQuotaEnabledOverrides.putAll(candidate.allianceQuotaEnabledOverrides);
        allianceAdminAudit.clear();
        allianceAdminAudit.addAll(candidate.allianceAdminAudit);
        quarantinedAllianceRecords.clear();
        quarantinedAllianceRecords.addAll(candidate.quarantinedAllianceRecords);
        armyMovements.clear();
        armyMovements.putAll(candidate.armyMovements);
        armyCompanies.clear();
        armyCompanies.putAll(candidate.armyCompanies);
        movementHistory.clear();
        movementHistory.putAll(candidate.movementHistory);
        playerNames.clear();
        playerNames.putAll(candidate.playerNames);
        adminUnitMapMarkerOptOuts.clear();
        adminUnitMapMarkerOptOuts.addAll(candidate.adminUnitMapMarkerOptOuts);
        kingsByFaction.clear();
        kingsByFaction.putAll(candidate.kingsByFaction);
        kingNamesByFaction.clear();
        kingNamesByFaction.putAll(candidate.kingNamesByFaction);
        populationPayoutInitialized = candidate.populationPayoutInitialized;
        lastPopulationPayoutBoundaryMillis = candidate.lastPopulationPayoutBoundaryMillis;
        populationPayoutTimezone = candidate.populationPayoutTimezone;
        populationPayoutLocalTime = candidate.populationPayoutLocalTime;
        populationPayoutLastFailure = candidate.populationPayoutLastFailure;
        progressionEnabled = candidate.progressionEnabled;
        movementSecondsPerTileOverride = candidate.movementSecondsPerTileOverride;
        movementTotalSecondsOverride = candidate.movementTotalSecondsOverride;
        movementStepDelaySeconds = candidate.movementStepDelaySeconds;
        nextWarSequence = candidate.nextWarSequence;
        nextBuildSequence = candidate.nextBuildSequence;
        allianceStageThreeRequiredHalfHours = candidate.allianceStageThreeRequiredHalfHours;
        allianceDifficulty = candidate.allianceDifficulty;
        conquestDefaultsInitialized = candidate.conquestDefaultsInitialized;
        allianceRelationsNeedReapply = candidate.allianceRelationsNeedReapply;
        integratedRootInitialized = candidate.integratedRootInitialized;
        warSeason.seasonId = candidate.warSeason.seasonId;
        warSeason.phase = candidate.warSeason.phase;
        warSeason.minimumWarEndMillis = candidate.warSeason.minimumWarEndMillis;
        warSeason.finaleTriggerActor = candidate.warSeason.finaleTriggerActor;
        warSeason.finaleTriggerActorName = candidate.warSeason.finaleTriggerActorName;
        warSeason.finaleTriggerTimeMillis = candidate.warSeason.finaleTriggerTimeMillis;
        warSeason.finaleEndTimeMillis = candidate.warSeason.finaleEndTimeMillis;
        warSeason.resetStatus = candidate.warSeason.resetStatus;
        // Existing dirty state is never lost; successful reconciliation may require a save.
        super.setDirty(super.isDirty() || candidate.isDirty());
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
        } catch (RuntimeException ignored) {
            System.err.println(message);
        }
    }

    private static void safeAllianceInfo(String message) {
        try {
            FMLLog.info("%s", message);
        } catch (RuntimeException ignored) {
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
        ensureWritable();
        validatePopulationPayoutState(); // reject before touching the destination tag
        nbt.setInteger(KOME_DATA_SCHEMA_KEY, KOME_DATA_SCHEMA_VERSION);
        nbt.removeTag("TradeProduceSlotsMaximum");
        nbt.removeTag("AllianceProduceSlots");
        nbt.setInteger("AllianceDataSchemaVersion", ALLIANCE_DATA_SCHEMA_VERSION);
        nbt.setInteger("BuildDataSchemaVersion", BUILD_DATA_SCHEMA_VERSION);
        nbt.setBoolean("ProgressionEnabled", progressionEnabled);
        nbt.setInteger("MovementSecondsPerTileOverride", Math.max(0, movementSecondsPerTileOverride));
        nbt.setInteger("MovementTotalSecondsOverride", Math.max(0, movementTotalSecondsOverride));
        nbt.setInteger("MovementStepDelaySeconds", Math.max(0, movementStepDelaySeconds));
        nbt.setInteger("NextWarSequence", Math.max(1, nextWarSequence));
        NBTTagCompound warSeasonTag = new NBTTagCompound();
        warSeason.writeToNBT(warSeasonTag);
        nbt.setTag("WarSeason", warSeasonTag);
        KOMEAuditService.writeToNBT(this, nbt);
        nbt.setInteger("NextBuildSequence", Math.max(1, nextBuildSequence));
        nbt.setInteger("AllianceStageThreeRequiredHalfHours", Math.max(1, allianceStageThreeRequiredHalfHours));
        nbt.setString("AllianceDifficulty", KOMEAllianceRequirements.normalizeDifficulty(allianceDifficulty));
        nbt.removeTag("WaypointRestrictionEnabled");
        nbt.removeTag("WaypointRestrictionBypasses");
        nbt.removeTag("SuccessionGraceDefaultMillis");
        nbt.removeTag("ContributionGraceDefaultMillis");
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

        nbt.setInteger("FactionPopulationDataSchemaVersion", FACTION_POPULATION_DATA_SCHEMA_VERSION);
        NBTTagList factionPopulationList = new NBTTagList();
        List<String> factionPopulationKeys = new ArrayList<String>(factionPopulations.keySet());
        Collections.sort(factionPopulationKeys);
        for (String faction : factionPopulationKeys) {
            KOMEFactionPopulation population = factionPopulations.get(faction);
            if (population == null) {
                continue;
            }
            NBTTagCompound entry = new NBTTagCompound();
            entry.setString("Faction", KOMEAlliance.normalizeFactionKey(faction));
            entry.setLong("AvailablePopulationCenti", population.getAvailablePopulationCenti());
            factionPopulationList.appendTag(entry);
        }
        nbt.setTag("FactionPopulations", factionPopulationList);
        nbt.setInteger("PopulationPayoutDataSchemaVersion", POPULATION_PAYOUT_DATA_SCHEMA_VERSION);
        nbt.setBoolean("PopulationPayoutInitialized", populationPayoutInitialized);
        nbt.setString("PopulationPayoutTimezone", populationPayoutTimezone);
        nbt.setString("PopulationPayoutLocalTime", populationPayoutLocalTime);
        nbt.setLong("LastPopulationPayoutBoundaryMillis", populationPayoutInitialized ? lastPopulationPayoutBoundaryMillis : -1L);
        NBTTagList payoutRemainders = new NBTTagList();
        List<String> remainderFactions = new ArrayList<String>(populationPayoutRemainders.keySet());
        Collections.sort(remainderFactions);
        for (String faction : remainderFactions) {
            Long remainder = populationPayoutRemainders.get(faction);
            if (remainder.longValue() == 0L) continue;
            NBTTagCompound entry = new NBTTagCompound(); entry.setString("Faction", faction); entry.setLong("RemainderUnits", remainder.longValue()); payoutRemainders.appendTag(entry);
        }
        nbt.setTag("PopulationPayoutRemainders", payoutRemainders);
        NBTTagList diplomacyList = new NBTTagList();
        List<String> diplomacyKeys = new ArrayList<String>(canonicalDiplomacyRecords.keySet());
        Collections.sort(diplomacyKeys);
        for (String key : diplomacyKeys) { KOMEDiplomacyRecord record = canonicalDiplomacyRecords.get(key); if (record != null) diplomacyList.appendTag(record.writeToNBT()); }
        nbt.setTag("CanonicalDiplomacyRecords", diplomacyList);

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

        NBTTagList companyDelegationAuditList = new NBTTagList();
        for (String entry : companyDelegationAudit) {
            if (entry == null || entry.length() == 0) {
                continue;
            }
            NBTTagCompound value = new NBTTagCompound();
            value.setString("Entry", entry);
            companyDelegationAuditList.appendTag(value);
        }
        nbt.setTag("CompanyDelegationAudit", companyDelegationAuditList);
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

        NBTTagList buildList = new NBTTagList();
        for (KOMEPlayerBuild build : builds.values()) {
            if (build != null && build.id != null && build.id.length() > 0) {
                buildList.appendTag(build.writeToNBT());
            }
        }
        nbt.setTag("Builds", buildList);

        NBTTagList foreignConstructionList = new NBTTagList();
        for (KOMEForeignConstructionPermission permission : foreignConstructionPermissions.values()) if (permission != null) foreignConstructionList.appendTag(permission.writeToNBT());
        nbt.setTag("ForeignConstructionPermissions", foreignConstructionList);

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

    private static void safeSchemaError(String message) {
        try {
            FMLLog.severe("%s", message);
        } catch (RuntimeException ignored) {
            System.err.println(message);
        }
    }

    public KOMEDailyBoundary populationPayoutSchedule() {
        return KOMEDailyBoundary.persisted(populationPayoutTimezone, populationPayoutLocalTime);
    }

    void validatePopulationPayoutState() {
        validatePayoutIdentity(populationPayoutInitialized, lastPopulationPayoutBoundaryMillis,
                populationPayoutTimezone, populationPayoutLocalTime, populationPayoutRemainders);
    }

    private static void validatePayoutIdentity(boolean initialized, long cursor, String timezone,
            String time, Map<String, Long> remainders) {
        if (!initialized) {
            if (cursor != -1L || !"".equals(timezone) || !"".equals(time) || !remainders.isEmpty())
                throw new IllegalArgumentException("Uninitialized payout state must have an empty schedule/remainder and cursor -1");
        } else {
            KOMEDailyBoundary schedule = KOMEDailyBoundary.persisted(timezone, time);
            java.time.Instant boundary = java.time.Instant.ofEpochMilli(cursor);
            if (!schedule.latestBoundaryAtOrBefore(boundary).equals(boundary))
                throw new IllegalArgumentException("Payout cursor is not a boundary of its persisted schedule");
            schedule.nextBoundary(boundary).toEpochMilli();
        }
        for (Map.Entry<String, Long> entry : remainders.entrySet()) {
            String faction = entry.getKey();
            if (faction == null || faction.isEmpty() || !faction.equals(KOMEAlliance.normalizeFactionKey(faction)))
                throw new IllegalArgumentException("Payout remainder requires a canonical nonblank faction");
            Long units = entry.getValue();
            if (units == null || units < 0L || units >= KOMEPopulationPayoutProcessor.RATE_UNITS_PER_CENTI)
                throw new IllegalArgumentException("Payout RemainderUnits out of sub-centi range for " + faction + ": " + units);
        }
    }

    private Map<String, Long> readCanonicalPayoutState(NBTTagCompound nbt) {
        Map<String, Long> loaded = new HashMap<String, Long>();
        try {
            if (!nbt.hasKey("PopulationPayoutDataSchemaVersion", 3)
                    || nbt.getInteger("PopulationPayoutDataSchemaVersion") != POPULATION_PAYOUT_DATA_SCHEMA_VERSION)
                throw new IllegalArgumentException("Unsupported or missing PopulationPayoutDataSchemaVersion; expected "
                        + POPULATION_PAYOUT_DATA_SCHEMA_VERSION + ". Pre-Checkpoint E development worlds require reset; no migration.");
            if (!nbt.hasKey("PopulationPayoutInitialized", 1) || !nbt.hasKey("LastPopulationPayoutBoundaryMillis", 4)
                    || !nbt.hasKey("PopulationPayoutTimezone", 8) || !nbt.hasKey("PopulationPayoutLocalTime", 8)
                    || !nbt.hasKey("PopulationPayoutRemainders", 9))
                throw new IllegalArgumentException("Canonical payout identity/cursor/remainder fields are missing or wrong NBT type");
            NBTTagList records = nbt.getTagList("PopulationPayoutRemainders", 10);
            if (((NBTTagList) nbt.getTag("PopulationPayoutRemainders")).tagCount() != records.tagCount())
                throw new IllegalArgumentException("Payout remainders must contain compound records");
            for (int i = 0; i < records.tagCount(); i++) {
                NBTTagCompound entry = records.getCompoundTagAt(i);
                if (!entry.hasKey("Faction", 8) || !entry.hasKey("RemainderUnits", 4))
                    throw new IllegalArgumentException("Malformed payout remainder at index " + i);
                String faction = entry.getString("Faction");
                if (loaded.containsKey(faction)) throw new IllegalArgumentException("Duplicate payout remainder faction " + faction);
                loaded.put(faction, entry.getLong("RemainderUnits"));
            }
            validatePayoutIdentity(nbt.getBoolean("PopulationPayoutInitialized"), nbt.getLong("LastPopulationPayoutBoundaryMillis"),
                    nbt.getString("PopulationPayoutTimezone"), nbt.getString("PopulationPayoutLocalTime"), loaded);
            loaded.values().removeAll(Collections.singleton(Long.valueOf(0L)));
        } catch (RuntimeException invalid) {
            failUnsupportedRootSchema("Invalid population payout state: " + invalid.getMessage());
        }
        return loaded;
    }

    private Map<String, KOMEPlayerBuild> readCanonicalBuilds(NBTTagCompound nbt) {
        if (!nbt.hasKey("BuildDataSchemaVersion", 3)
                || nbt.getInteger("BuildDataSchemaVersion") != BUILD_DATA_SCHEMA_VERSION) {
            failUnsupportedRootSchema("Unsupported BuildDataSchemaVersion; expected " + BUILD_DATA_SCHEMA_VERSION
                + ". Pre-Checkpoint D development worlds require reset; Build migration is disabled.");
        }
        Map<String, KOMEPlayerBuild> loaded = new HashMap<String, KOMEPlayerBuild>();
        if (!nbt.hasKey("Builds", 9)) {
            failUnsupportedRootSchema("Canonical Builds list is missing or malformed.");
        }
        NBTTagList records = nbt.getTagList("Builds", 10);
        if (((NBTTagList) nbt.getTag("Builds")).tagCount() != records.tagCount()) {
            failUnsupportedRootSchema("Canonical Builds list must contain compound records.");
        }
        for (int i = 0; i < records.tagCount(); i++) {
            NBTTagCompound record = records.getCompoundTagAt(i);
            try {
                KOMEPlayerBuild build = new KOMEPlayerBuild();
                build.readFromNBT(record);
                if (build.id.trim().isEmpty() || build.tileId.isEmpty() || build.populationFaction.isEmpty()) {
                    throw new IllegalArgumentException("Build ID, tile and population faction are required.");
                }
                if (loaded.containsKey(build.id)) throw new IllegalArgumentException("Duplicate Build ID.");
                loaded.put(build.id, build);
            } catch (RuntimeException invalid) {
                failUnsupportedRootSchema("Invalid Build record at index " + i + " (ID="
                    + record.getString("Id") + "): " + invalid.getMessage());
            }
        }
        return loaded;
    }

    /** Strict, single-pass bank parsing: NBT's typed getters alone silently accept missing/wrong tags. */
    private Map<String, KOMEFactionPopulation> readCanonicalFactionPopulations(NBTTagCompound nbt) {
        if (!nbt.hasKey("FactionPopulationDataSchemaVersion", 3)
                || nbt.getInteger("FactionPopulationDataSchemaVersion") != FACTION_POPULATION_DATA_SCHEMA_VERSION) {
            int found = nbt.hasKey("FactionPopulationDataSchemaVersion")
                ? nbt.getInteger("FactionPopulationDataSchemaVersion") : 0;
            failUnsupportedRootSchema("Unsupported faction-population schema " + found + "; expected "
                + FACTION_POPULATION_DATA_SCHEMA_VERSION + ". Integer-bank migration is intentionally disabled.");
        }
        if (!nbt.hasKey("FactionPopulations", 9)) {
            failUnsupportedRootSchema("FactionPopulations must be an NBT list.");
        }
        NBTTagList entries = (NBTTagList) nbt.getTag("FactionPopulations");
        // An ordinary empty NBTTagList has element type END (0); an explicitly compound
        // empty list is also valid. No other declared element type is canonical.
        int elementType = entries.func_150303_d();
        if (elementType != 10 && (elementType != 0 || entries.tagCount() > 0)) {
            failUnsupportedRootSchema("FactionPopulations must contain compound records.");
        }
        Map<String, KOMEFactionPopulation> loaded = new HashMap<String, KOMEFactionPopulation>();
        for (int i = 0; i < entries.tagCount(); i++) {
            loadSection = "FactionPopulations[" + i + "]";
            NBTTagCompound entry = entries.getCompoundTagAt(i);
            if (!entry.hasKey("Faction", 8)) {
                failUnsupportedRootSchema("Invalid faction-population record at index " + i + ": Faction must be a string.");
            }
            String faction = KOMEAlliance.normalizeFactionKey(entry.getString("Faction"));
            if (faction.length() == 0) {
                failUnsupportedRootSchema("Invalid faction-population record at index " + i + ": faction is blank.");
            }
            if (loaded.containsKey(faction)) {
                failUnsupportedRootSchema("Invalid faction-population record at index " + i
                    + ": duplicate faction " + faction + ".");
            }
            if (!entry.hasKey("AvailablePopulationCenti", 4)) {
                failUnsupportedRootSchema("Invalid faction-population record at index " + i + " for " + faction
                    + ": AvailablePopulationCenti must be a long.");
            }
            long balance = entry.getLong("AvailablePopulationCenti");
            if (balance < 0L) {
                failUnsupportedRootSchema("Invalid faction-population record at index " + i + " for " + faction
                    + ": AvailablePopulationCenti must not be negative.");
            }
            KOMEFactionPopulation population = new KOMEFactionPopulation();
            population.setAvailablePopulationCenti(balance);
            loaded.put(faction, population);
        }
        return loaded;
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
