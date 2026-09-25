package kome.common.data;

public class KOMEClientData extends KOMEWorldData {
    public static final KOMEClientData INSTANCE = new KOMEClientData();
    public final java.util.Map<String, KOMETileTroopSummary> troopSummaries = new java.util.HashMap<String, KOMETileTroopSummary>();
    public final java.util.List<KOMEUnitMapMarker> unitMapMarkers = new java.util.ArrayList<KOMEUnitMapMarker>();
    /** Public projection only: faction -> capital tile. Exact deployment anchors remain server-only. */
    public final java.util.Map<String, String> capitalTilesByFaction =
        new java.util.HashMap<String, String>();
    public int conquestRevision;
    public boolean clientViewerIsAdmin;

    private KOMEClientData() {
        super("KOME_ClientData");
    }

    public void resetClientState() {
        progressions.clear();
        hiredUnits.clear();
        conquestTiles.clear();
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
        troopSummaries.clear();
        unitMapMarkers.clear();
        allianceDifficulty = KOMEAllianceRequirements.STANDARD;
        clientViewerIsAdmin = false;
    }
}
