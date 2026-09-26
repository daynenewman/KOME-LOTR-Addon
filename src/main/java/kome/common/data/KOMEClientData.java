package kome.common.data;

public class KOMEClientData extends KOMEWorldData {
    public static final KOMEClientData INSTANCE = new KOMEClientData();
    public final java.util.Map<String, KOMETileTroopSummary> troopSummaries = new java.util.HashMap<String, KOMETileTroopSummary>();
    public final java.util.List<KOMEUnitMapMarker> unitMapMarkers = new java.util.ArrayList<KOMEUnitMapMarker>();
    /** Public projection only: faction -> capital tile. Exact deployment anchors remain server-only. */
    public final java.util.Map<String, String> capitalTilesByFaction =
        new java.util.HashMap<String, String>();
    public int conquestRevision;
    /** Immutable render inputs from the last COMPLETE public conquest update, never partial rows. */
    private java.util.Map<String, String> conquestRenderOwners = java.util.Collections.emptyMap();

    public java.util.Map<String, String> conquestRenderOwners() { return conquestRenderOwners; }

    /** Client-thread publication; does not own or modify authoritative ownership. */
    public void completeConquestUpdate() {
        java.util.Map<String, String> owners = new java.util.HashMap<String, String>();
        for (KOMEConquestTile tile : conquestTiles.values()) {
            if (tile == null) continue;
            String owner = tile.projectRulingFaction();
            if (!owner.isEmpty()) owners.put(tile.id, owner);
        }
        conquestRenderOwners = java.util.Collections.unmodifiableMap(owners);
        conquestRevision++;
    }

    // Client-thread batch assembly. Never expose a partially received population section.
    private java.util.Map<String, KOMETileTroopSummary> pendingTroopSummaries;
    private volatile long populationGeneration;
    private boolean populationRequiresReset;

    public long conquestPopulationGeneration() { return populationGeneration; }

    /** reset begins replacement; absent sections in other chunks are not empty replacements. */
    public void applyConquestPopulation(long generation, boolean reset, boolean complete,
            java.util.Map<String, KOMETileTroopSummary> rows) {
        if (generation != populationGeneration) return; // Queued work from the previous world/session.
        if (reset) {
            pendingTroopSummaries = new java.util.HashMap<String, KOMETileTroopSummary>();
            populationRequiresReset = false;
        } else if (populationRequiresReset) {
            return; // A tail from an abandoned batch cannot initialize a new world.
        } else if (pendingTroopSummaries == null) {
            pendingTroopSummaries = new java.util.HashMap<String, KOMETileTroopSummary>(troopSummaries);
        }
        for (KOMETileTroopSummary row : rows.values()) {
            // Rows are complete records, not field patches. An explicit empty row removes that tile.
            if (row.hasAnyPopulation()) pendingTroopSummaries.put(row.tileId, row);
            else pendingTroopSummaries.remove(row.tileId);
        }
        if (complete) {
            troopSummaries.clear();
            troopSummaries.putAll(pendingTroopSummaries);
            pendingTroopSummaries = null;
        }
    }

    public void clearConquestPopulation() {
        populationGeneration++;
        pendingTroopSummaries = null;
        populationRequiresReset = true;
        troopSummaries.clear();
    }

    public boolean clientViewerIsAdmin;

    private KOMEClientData() {
        super("KOME_ClientData");
    }

    public void resetClientState() {
        progressions.clear();
        hiredUnits.clear();
        conquestTiles.clear();
        conquestRenderOwners = java.util.Collections.emptyMap();
        capitalTilesByFaction.clear();
        activeRecruitmentTiles.clear();
        tileWaypointLinksByTileId.clear();
        routeEdges.clear();
        builds.clear();
        alliances.clear();
        canonicalDiplomacyRecords.clear();
        wars.clear();
        conquestClaimConfirmations.clear();
        allianceRequirementOverrides.clear();
        armyMovements.clear();
        armyCompanies.clear();
        playerNames.clear();
        clearFactionKingRecords();
        clearConquestPopulation();
        unitMapMarkers.clear();
        allianceDifficulty = KOMEAllianceRequirements.STANDARD;
        clientViewerIsAdmin = false;
        conquestRevision++;
    }
}
