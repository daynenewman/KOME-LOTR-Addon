package kome.common;

import com.enovak.lotrmoremobs.Main;
import com.lotrcharactercreation.LOTRCharacterCreation;
import com.lotrcharactercreation.proxy.CommonProxy;
import cpw.mods.fml.common.FMLCommonHandler;
import kome.common.data.KOMEEvents;
import net.minecraftforge.common.MinecraftForge;
import com.fuzs.aquaacrobatics.AquaAcrobatics;

public class KOMECommonProxy {
    private final CommonProxy characterCreationProxy;
    private KOMEEvents events;

    public KOMECommonProxy() {
        this(new CommonProxy());
    }

    protected KOMECommonProxy(CommonProxy characterCreationProxy) {
        this(characterCreationProxy, new com.enovak.lotrmoremobs.proxy.CommonProxy());
    }

    protected KOMECommonProxy(
        CommonProxy characterCreationProxy,
        com.enovak.lotrmoremobs.proxy.CommonProxy lotrMoreMobsProxy) {
    this.characterCreationProxy = characterCreationProxy;
    LOTRCharacterCreation.proxy = characterCreationProxy;
    Main.proxy = lotrMoreMobsProxy;
    AquaAcrobatics.proxy =
            new com.fuzs.aquaacrobatics.proxy.CommonProxy();
}

    public void init() {
        events = new KOMEEvents();
        MinecraftForge.EVENT_BUS.register(events);
        FMLCommonHandler.instance().bus().register(events);
        kome.common.data.KOMETileAwarenessEvents tileAwareness = new kome.common.data.KOMETileAwarenessEvents();
        MinecraftForge.EVENT_BUS.register(tileAwareness);
        FMLCommonHandler.instance().bus().register(tileAwareness);
        kome.common.tactical.edit.KOMETacticalEditRuntime tacticalEditor = new kome.common.tactical.edit.KOMETacticalEditRuntime();
        FMLCommonHandler.instance().bus().register(tacticalEditor);
        MinecraftForge.EVENT_BUS.register(tacticalEditor);
    }

    public void resetServerSessionState() {
        kome.common.data.KOMEAllianceRecordBuilder.resetSessionState();
        if (events != null) {
            events.resetSessionState();
        }
    }

    /** Common packet handlers cross this proxy boundary without loading client classes. */
    public void enqueueClientTask(Runnable task) {
        throw new IllegalStateException("Client publication is unavailable on the dedicated server");
    }

    public void acceptConquestSnapshotChunk(
            kome.common.network.KOMEPacketConquestData.PublicationChunk chunk) {
        throw new IllegalStateException("Client conquest publication is unavailable on the dedicated server");
    }

    public void acceptTacticalEditSnapshot(kome.common.network.KOMEPacketTacticalEditSnapshot message) { }
    public void acceptTacticalAreaCatalog(kome.common.network.KOMEPacketTacticalAreaCatalog message) { }
    public void acceptTacticalComplexCatalog(kome.common.network.KOMEPacketTacticalComplexCatalog message) { }
    public void acceptPublicWaypoints(kome.common.network.KOMEPacketPublicWaypoints.Chunk chunk,Object connection) {
        throw new IllegalStateException("Public waypoint publication is unavailable on a dedicated server");
    }

    public void displayPopulationGui(kome.common.network.KOMEPacketPopulationGui message) { }
    public void displayPopulationUnitsGui(kome.common.network.KOMEPacketPopulationUnitsGui message) { }
    public void displayConquestCaptureGui(kome.common.network.KOMEPacketConquestCaptureGui message) { }

    public void displayCompanyListGui(String tileId, java.util.List companies, boolean canCreate) {
    }

    public void displayCompanyListGui(String tileId, String tileDisplayName, java.util.List companies, boolean canCreate) {
        displayCompanyListGui(tileId, companies, canCreate);
    }

    public void displayCompanyMoveConfirmGui(kome.common.network.KOMEPacketCompanyMoveConfirmGui message) {
    }

    public void displayCompanyMovePreviewResult(kome.common.network.KOMEPacketCompanyMovePreviewResult message) {
    }

    public void displayMovementHistory(String title, String requestFaction, boolean allFactions, java.util.List records) {
    }

    public void displayLordMenu(int entityId, String lordName, String factionName, boolean currentLord) {
    }

    public void displaySerfdomMasterMenu(int entityId, String masterName, String factionName, int mode, String dutyStatus) {
    }
    public void displaySerfdomMasterMenu(int entityId,String masterName,String factionName,int mode,String dutyStatus,boolean canRequestDuty,boolean hasActiveDuty){displaySerfdomMasterMenu(entityId,masterName,factionName,mode,dutyStatus);}
    public void displayRelationshipHub(int entityId, int relationship, String npcName, String factionName) {
        displayRelationshipHub(entityId, relationship, npcName, factionName, true);
    }
    public void displayRelationshipHub(int entityId, int relationship, String npcName, String factionName, boolean allowService) {
    }

    public void updateProgressionData(String playerName, java.util.List completed) {
    }

    public void updateProgressionData(String playerName, java.util.List completed, java.util.Map assignments) {
        updateProgressionData(playerName, completed);
    }
    public void updateProgressionData(String playerName, java.util.List completed, java.util.Map assignments, String summary, String findLabel, String leaveType, String leaveLabel, String leaveName) { updateProgressionData(playerName, completed, assignments); }
    public void updateProgressionData(String playerName, java.util.List completed, java.util.Map assignments, String summary, String findLabel, String leaveType, String leaveLabel, String leaveName, kome.common.data.KOMEProgressionRankSummary ranks) { updateProgressionData(playerName, completed, assignments, summary, findLabel, leaveType, leaveLabel, leaveName); }

    public void updateQuotaLedger(java.util.List lines) {
    }

    public void updateServerRecords(java.util.List lines) {
    }

    public void updateServerRecords(java.util.List lines, boolean reset, boolean complete) {
        updateServerRecords(lines);
    }

    public void updateAllianceData(java.util.List lines) {
    }

    public void updateVisualMarkers(java.util.List<kome.common.data.KOMEVisualMarker> markers) {
    }

    public void updateProgressionTracker(kome.common.data.KOMEProgressionTrackerSnapshot snapshot) {
    }

    public void updateStandingTrialEligibility(
            int entityId,
            long entityUuidMost,
            long entityUuidLeast,
            boolean eligible,
            boolean passiveOffer,
            boolean offering,
            int offerColor) {
    }

    public void displayPledgeDeparture(kome.common.network.KOMEPacketPledgeDepartureData message) {
    }

    public void highlightEntity(int entityId, String name, double x, double y, double z) {
    }
}
