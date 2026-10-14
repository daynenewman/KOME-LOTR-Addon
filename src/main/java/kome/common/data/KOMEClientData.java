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

    /** Client-thread clearing; the client publisher owns pending batches and session tokens. */
    public void clearConquestTooltip() {
        tileWaypointLinksByTileId.clear();
        troopSummaries.clear();
    }

    public boolean clientViewerIsAdmin;
    private long progressionRevision;
    private java.util.UUID progressionViewer;

    /** A single transient server projection shared by the book and legacy permission readers. */
    public boolean acceptProgression(java.util.UUID viewer, long revision, String rankKey,
            String faction, boolean enabled, java.util.List completed, java.util.Map assignments) {
        KOMEProgressionRank rank = KOMEProgressionRank.forKey(rankKey);
        if (viewer == null || viewer.equals(new java.util.UUID(0L, 0L))
                || revision <= 0L || revision <= progressionRevision || rank == null
                || progressionViewer != null && !progressionViewer.equals(viewer)) return false;
        KOMEPlayerProgression projection = new KOMEPlayerProgression();
        projection.setCanonicalRank(rank);
        for (Object id : completed) projection.grant(String.valueOf(id));
        for (Object value : assignments.entrySet()) {
            java.util.Map.Entry entry = (java.util.Map.Entry) value;
            projection.setAssignment(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
        }
        clearProgressionProjection();
        progressions.put(viewer, projection);
        lastKnownPlayerFactions.put(viewer, KOMEAlliance.normalizeFactionKey(faction));
        setProgressionEnabled(enabled);
        progressionViewer = viewer;
        progressionRevision = revision;
        return true;
    }

    /** Dimension changes clear visible values but retain the connection's stale-update fence. */
    public void clearProgressionProjection() {
        progressions.clear();
        lastKnownPlayerFactions.clear();
        setProgressionEnabled(true);
    }

    private KOMEClientData() {
        super("KOME_ClientData");
    }

    public void resetClientState() {
        clearProgressionProjection();
        progressionRevision = 0L;
        progressionViewer = null;
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
