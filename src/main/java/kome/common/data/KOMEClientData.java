package kome.common.data;

public class KOMEClientData extends KOMEWorldData {
    public static final KOMEClientData INSTANCE = new KOMEClientData();
    public KOMEPopulationType hireType = KOMEPopulationType.OFFENSIVE;
    public final java.util.Map<String, KOMETileTroopSummary> troopSummaries = new java.util.HashMap<String, KOMETileTroopSummary>();
    public final java.util.List<KOMEUnitMapMarker> unitMapMarkers = new java.util.ArrayList<KOMEUnitMapMarker>();
    public int conquestRevision;
    public boolean clientViewerIsAdmin;
    public boolean clientWaypointBypass;

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
        wars.clear();
        conquestClaimConfirmations.clear();
        waypointRestrictionBypasses.clear();
        allianceRequirementOverrides.clear();
        armyMovements.clear();
        armyCompanies.clear();
        playerNames.clear();
        clearFactionKingRecords();
        troopSummaries.clear();
        unitMapMarkers.clear();
        hireType = KOMEPopulationType.OFFENSIVE;
        allianceDifficulty = KOMEAllianceRequirements.STANDARD;
        waypointRestrictionEnabled = false;
        successionGraceDefaultMillis = KOMEAllianceAuthority.FOURTEEN_DAYS_MILLIS;
        contributionGraceDefaultMillis = KOMEAllianceAuthority.FOURTEEN_DAYS_MILLIS;
        clientViewerIsAdmin = false;
        clientWaypointBypass = false;
        conquestRevision++;
    }
}
