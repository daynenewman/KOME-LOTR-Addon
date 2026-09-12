package kome.common.data;

public class KOMEClientData extends KOMEWorldData {
    public static final KOMEClientData INSTANCE = new KOMEClientData();
    public final java.util.Map<String, KOMETileTroopSummary> troopSummaries = new java.util.HashMap<String, KOMETileTroopSummary>();
    public final java.util.List<KOMEUnitMapMarker> unitMapMarkers = new java.util.ArrayList<KOMEUnitMapMarker>();
    public int conquestRevision;
    public boolean clientViewerIsAdmin;

    private KOMEClientData() {
        super("KOME_ClientData");
    }

    public void resetClientState() {
        populations.clear();
        progressions.clear();
        hiredUnits.clear();
        conquestTiles.clear();
        tilePopulations.clear();
        populationAllocations.clear();
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
        conquestRevision++;
    }
}
