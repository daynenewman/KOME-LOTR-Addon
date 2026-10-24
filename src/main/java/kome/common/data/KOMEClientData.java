package kome.common.data;

public class KOMEClientData extends KOMEWorldData {
    public static final KOMEClientData INSTANCE = new KOMEClientData();
    public final java.util.Map<String, KOMETileTroopSummary> troopSummaries = new java.util.HashMap<String, KOMETileTroopSummary>();
    public final java.util.List<KOMEUnitMapMarker> unitMapMarkers = new java.util.ArrayList<KOMEUnitMapMarker>();
    /** Public projection only: faction -> capital tile. Exact deployment anchors remain server-only. */
    public final java.util.Map<String, String> capitalTilesByFaction =
        new java.util.HashMap<String, String>();
    public int conquestRevision;
    /** Client-only baseline state; an empty completed snapshot proves absence, unlike initial loading. */
    private boolean completeConquestSnapshot;
    public boolean hasCompleteConquestSnapshot() { return completeConquestSnapshot; }
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
        completeConquestSnapshot = true;
    }

    /** Client-thread clearing; the client publisher owns pending batches and session tokens. */
    public void clearConquestTooltip() {
        tileWaypointLinksByTileId.clear();
        troopSummaries.clear();
    }

    public boolean clientViewerIsAdmin;

    private KOMEClientData() {
        super("KOME_ClientData");
    }

    public void resetClientState() {
        completeConquestSnapshot = false;
        progressions.clear();
        hiredUnits.clear();
        conquestTiles.clear();
        conquestRenderOwners = java.util.Collections.emptyMap();
        capitalTilesByFaction.clear();
        activeRecruitmentTiles.clear();
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
        clearConquestTooltip();
        unitMapMarkers.clear();
        allianceDifficulty = KOMEAllianceRequirements.STANDARD;
        clientViewerIsAdmin = false;
    }
}
