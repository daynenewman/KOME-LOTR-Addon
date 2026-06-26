package kome.client;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import kome.client.gui.KOMEGuiConquestCapture;
import kome.client.gui.KOMEGuiAlliance;
import kome.client.gui.KOMEGuiLordMenu;
import kome.client.gui.KOMEGuiPopulation;
import kome.client.gui.KOMEGuiProgression;
import kome.client.gui.KOMEGuiServerRecords;
import kome.common.KOMECommonProxy;
import kome.common.data.KOMEClientData;
import lotr.client.gui.LOTRGuiMap;
import net.minecraftforge.common.MinecraftForge;

import java.util.List;

public class KOMEClientProxy extends KOMECommonProxy {
    @Override
    public void init() {
        super.init();
        MinecraftForge.EVENT_BUS.register(new KOMEChatSanitizer());
        MinecraftForge.EVENT_BUS.register(new KOMEUnitTradeOverlay());
        MinecraftForge.EVENT_BUS.register(new KOMEProgressionMenuOverlay());
        MinecraftForge.EVENT_BUS.register(new KOMEQuotaLedgerOverlay());
        MinecraftForge.EVENT_BUS.register(new KOMEUnitOverviewCapOverlay());
        MinecraftForge.EVENT_BUS.register(new KOMEEntityHighlightOverlay());
        KOMEConquestMapOverlay conquestMapOverlay = new KOMEConquestMapOverlay();
        FMLCommonHandler.instance().bus().register(conquestMapOverlay);
        MinecraftForge.EVENT_BUS.register(conquestMapOverlay);
        FMLCommonHandler.instance().bus().register(this);
    }

    @SubscribeEvent
    public void onClientConnect(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        resetClientSessionState();
    }

    @SubscribeEvent
    public void onClientDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        resetClientSessionState();
    }

    private void resetClientSessionState() {
        KOMEClientData.INSTANCE.resetClientState();
        KOMEQuotaLedgerOverlay.reset();
        KOMEGuiAlliance.resetData();
        KOMEGuiProgression.resetData();
        KOMEGuiServerRecords.resetData();
        KOMEUnitCapClientState.reset();
        KOMEConquestMapOverlay.resetClientMapState();
    }

    @Override
    public void displayPopulationGui(String playerName, int offensiveTotal, int offensiveUsed, int defensiveTotal, int defensiveUsed, int farmhandsUsed, int farmhandsLimit, int armyUsed, int armyTotal, int tileOffensiveTotal, int tileOffensiveUsed, int tileDefensiveTotal, int tileDefensiveUsed, int controlledTiles, int allocatedOffensive, int allocatedOffensiveUsed, int allocatedDefensive, int allocatedDefensiveUsed, String allocationSummary, boolean canManageAllocations) {
        KOMEMinecraftClient.displayGui(new KOMEGuiPopulation(playerName, offensiveTotal, offensiveUsed, defensiveTotal, defensiveUsed, farmhandsUsed, farmhandsLimit, armyUsed, armyTotal, tileOffensiveTotal, tileOffensiveUsed, tileDefensiveTotal, tileDefensiveUsed, controlledTiles, allocatedOffensive, allocatedOffensiveUsed, allocatedDefensive, allocatedDefensiveUsed, allocationSummary, canManageAllocations));
    }

    @Override
    public void displayPopulationGui(kome.common.network.KOMEPacketPopulationGui message) {
        KOMEMinecraftClient.displayGui(new KOMEGuiPopulation(message));
    }

    @Override
    public void displayPopulationUnitsGui(String playerName, String filterTile, List units, int armyUsed, int armyTotal, int farmhandsUsed, int farmhandsLimit) {
        KOMEMinecraftClient.displayGui(new kome.client.gui.KOMEGuiPopulationUnits(playerName, filterTile, units, armyUsed, armyTotal, farmhandsUsed, farmhandsLimit));
    }

    @Override
    public void displayCompanyListGui(String tileId, List companies, boolean canCreate) {
        KOMEMinecraftClient.displayGui(new kome.client.gui.KOMEGuiCompanyList(tileId, companies, canCreate));
    }

    @Override
    public void displayCompanyMoveConfirmGui(kome.common.network.KOMEPacketCompanyMoveConfirmGui message) {
        KOMEConquestMapOverlay.beginRoutePreview(message);
        if (!(KOMEMinecraftClient.currentScreen() instanceof LOTRGuiMap)) {
            KOMEConquestMapOverlay.openPreservedMap();
        }
    }

    @Override
    public void displayCompanyMovePreviewResult(kome.common.network.KOMEPacketCompanyMovePreviewResult message) {
        KOMEConquestMapOverlay.showCompanyMovePreviewResult(message);
        if (!(KOMEMinecraftClient.currentScreen() instanceof LOTRGuiMap)) {
            KOMEConquestMapOverlay.openPreservedMap();
        }
    }

    @Override
    public void displayMovementHistory(String title, String requestFaction, boolean allFactions, List records) {
        KOMEMinecraftClient.displayGui(new kome.client.gui.KOMEGuiMovementHistory(title, requestFaction, allFactions, records));
    }

    @Override
    public void displayConquestCaptureGui(String tileId, String ownerFaction, String pendingFromFaction, String pendingToFaction) {
        KOMEMinecraftClient.displayGui(new KOMEGuiConquestCapture(tileId, ownerFaction, pendingFromFaction, pendingToFaction));
    }

    @Override
    public void displayConquestCaptureGui(String tileId, String ownerFaction, String pendingFromFaction, String pendingToFaction, int offensivePop, int defensivePop, int mountedPop, int groundPop, int incomingPop, int outgoingPop, long incomingEtaMillis) {
        KOMEMinecraftClient.displayGui(new KOMEGuiConquestCapture(tileId, ownerFaction, pendingFromFaction, pendingToFaction, offensivePop, defensivePop, mountedPop, groundPop, incomingPop, outgoingPop, incomingEtaMillis));
    }

    @Override
    public void displayConquestCaptureGui(String tileId, String ownerFaction, String pendingFromFaction, String pendingToFaction, String viewerFaction, int offensivePop, int defensivePop, int mountedPop, int groundPop, int incomingPop, int outgoingPop, long incomingEtaMillis) {
        KOMEMinecraftClient.displayGui(new KOMEGuiConquestCapture(tileId, ownerFaction, pendingFromFaction, pendingToFaction, viewerFaction, offensivePop, defensivePop, mountedPop, groundPop, incomingPop, outgoingPop, incomingEtaMillis));
    }

    @Override
    public void displayConquestCaptureGui(String tileId, String ownerFaction, String pendingFromFaction, String pendingToFaction, String viewerFaction, int offensivePop, int defensivePop, int mountedPop, int groundPop, int incomingPop, int outgoingPop, long incomingEtaMillis, int offensiveTotal, int offensiveUsed, int defensiveTotal, int defensiveUsed, int farmhandTotal, int farmhandUsed, boolean canClaim, boolean canTransfer, boolean canAcceptTransfer, boolean canCancelTransfer, boolean canMoveTroops) {
        KOMEMinecraftClient.displayGui(new KOMEGuiConquestCapture(tileId, ownerFaction, pendingFromFaction, pendingToFaction, viewerFaction, offensivePop, defensivePop, mountedPop, groundPop, incomingPop, outgoingPop, incomingEtaMillis, offensiveTotal, offensiveUsed, defensiveTotal, defensiveUsed, farmhandTotal, farmhandUsed, canClaim, canTransfer, canAcceptTransfer, canCancelTransfer, canMoveTroops));
    }

    @Override
    public void displayConquestCaptureGui(String tileId, String ownerFaction, String pendingFromFaction, String pendingToFaction, String viewerFaction, int offensivePop, int defensivePop, int mountedPop, int groundPop, int incomingPop, int outgoingPop, long incomingEtaMillis, int offensiveTotal, int offensiveUsed, int defensiveTotal, int defensiveUsed, int farmhandTotal, int farmhandUsed, boolean canClaim, boolean canTransfer, boolean canAcceptTransfer, boolean canCancelTransfer, boolean canMoveTroops, boolean canEditPopulation) {
        KOMEMinecraftClient.displayGui(new KOMEGuiConquestCapture(tileId, ownerFaction, pendingFromFaction, pendingToFaction, viewerFaction, offensivePop, defensivePop, mountedPop, groundPop, incomingPop, outgoingPop, incomingEtaMillis, offensiveTotal, offensiveUsed, defensiveTotal, defensiveUsed, farmhandTotal, farmhandUsed, canClaim, canTransfer, canAcceptTransfer, canCancelTransfer, canMoveTroops, canEditPopulation));
    }

    @Override
    public void displayConquestCaptureGui(String tileId, String ownerFaction, String pendingFromFaction, String pendingToFaction, String viewerFaction, int offensivePop, int defensivePop, int mountedPop, int groundPop, int incomingPop, int outgoingPop, long incomingEtaMillis, int offensiveTotal, int offensiveUsed, int defensiveTotal, int defensiveUsed, int farmhandTotal, int farmhandUsed, boolean canClaim, boolean canTransfer, boolean canAcceptTransfer, boolean canCancelTransfer, boolean canMoveTroops, boolean canEditPopulation, int offensiveAllocated, int defensiveAllocated, int myOffensiveAllocated, int myOffensiveUsed, int myDefensiveAllocated, int myDefensiveUsed, String claimantName, String allocationSummary, boolean ownerHasKing) {
        KOMEMinecraftClient.displayGui(new KOMEGuiConquestCapture(tileId, ownerFaction, pendingFromFaction, pendingToFaction, viewerFaction, offensivePop, defensivePop, mountedPop, groundPop, incomingPop, outgoingPop, incomingEtaMillis, offensiveTotal, offensiveUsed, defensiveTotal, defensiveUsed, farmhandTotal, farmhandUsed, canClaim, canTransfer, canAcceptTransfer, canCancelTransfer, canMoveTroops, canEditPopulation, offensiveAllocated, defensiveAllocated, myOffensiveAllocated, myOffensiveUsed, myDefensiveAllocated, myDefensiveUsed, claimantName, allocationSummary, ownerHasKing));
    }

    @Override
    public void displayConquestCaptureGui(String tileId, String ownerFaction, String pendingFromFaction, String pendingToFaction, String viewerFaction, int offensivePop, int defensivePop, int mountedPop, int groundPop, int incomingPop, int outgoingPop, long incomingEtaMillis, int offensiveTotal, int offensiveUsed, int defensiveTotal, int defensiveUsed, int farmhandTotal, int farmhandUsed, boolean canClaim, boolean canTransfer, boolean canAcceptTransfer, boolean canCancelTransfer, boolean canMoveTroops, boolean canEditPopulation, int offensiveAllocated, int defensiveAllocated, int myOffensiveAllocated, int myOffensiveUsed, int myDefensiveAllocated, int myDefensiveUsed, String claimantName, String allocationSummary, boolean ownerHasKing, int myOffensivePop, int myDefensivePop, int myMountedPop, int myGroundPop, String activeRecruitmentTile, boolean canSetRecruitmentTile) {
        KOMEMinecraftClient.displayGui(new KOMEGuiConquestCapture(tileId, ownerFaction, pendingFromFaction, pendingToFaction, viewerFaction, offensivePop, defensivePop, mountedPop, groundPop, incomingPop, outgoingPop, incomingEtaMillis, offensiveTotal, offensiveUsed, defensiveTotal, defensiveUsed, farmhandTotal, farmhandUsed, canClaim, canTransfer, canAcceptTransfer, canCancelTransfer, canMoveTroops, canEditPopulation, offensiveAllocated, defensiveAllocated, myOffensiveAllocated, myOffensiveUsed, myDefensiveAllocated, myDefensiveUsed, claimantName, allocationSummary, ownerHasKing, myOffensivePop, myDefensivePop, myMountedPop, myGroundPop, activeRecruitmentTile, canSetRecruitmentTile));
    }

    @Override
    public void displayConquestCaptureGui(String tileId, String ownerFaction, String pendingFromFaction, String pendingToFaction, String viewerFaction, int offensivePop, int defensivePop, int mountedPop, int groundPop, int incomingPop, int outgoingPop, long incomingEtaMillis, int offensiveTotal, int offensiveUsed, int defensiveTotal, int defensiveUsed, int farmhandTotal, int farmhandUsed, boolean canClaim, boolean canTransfer, boolean canAcceptTransfer, boolean canCancelTransfer, boolean canMoveTroops, boolean canEditPopulation, int offensiveAllocated, int defensiveAllocated, int myOffensiveAllocated, int myOffensiveUsed, int myDefensiveAllocated, int myDefensiveUsed, String claimantName, String allocationSummary, boolean ownerHasKing, int myOffensivePop, int myDefensivePop, int myMountedPop, int myGroundPop, String activeRecruitmentTile, boolean canSetRecruitmentTile, String lotrWaypointKey, String lotrWaypointDisplayName, String lotrWaypointRegion) {
        KOMEMinecraftClient.displayGui(new KOMEGuiConquestCapture(tileId, ownerFaction, pendingFromFaction, pendingToFaction, viewerFaction, offensivePop, defensivePop, mountedPop, groundPop, incomingPop, outgoingPop, incomingEtaMillis, offensiveTotal, offensiveUsed, defensiveTotal, defensiveUsed, farmhandTotal, farmhandUsed, canClaim, canTransfer, canAcceptTransfer, canCancelTransfer, canMoveTroops, canEditPopulation, offensiveAllocated, defensiveAllocated, myOffensiveAllocated, myOffensiveUsed, myDefensiveAllocated, myDefensiveUsed, claimantName, allocationSummary, ownerHasKing, myOffensivePop, myDefensivePop, myMountedPop, myGroundPop, activeRecruitmentTile, canSetRecruitmentTile, lotrWaypointKey, lotrWaypointDisplayName, lotrWaypointRegion));
    }

    @Override
    public void displayConquestCaptureGui(String tileId, String ownerFaction, String pendingFromFaction, String pendingToFaction, String viewerFaction, int offensivePop, int defensivePop, int mountedPop, int groundPop, int incomingPop, int outgoingPop, long incomingEtaMillis, int offensiveTotal, int offensiveUsed, int defensiveTotal, int defensiveUsed, int farmhandTotal, int farmhandUsed, boolean canClaim, boolean canTransfer, boolean canAcceptTransfer, boolean canCancelTransfer, boolean canMoveTroops, boolean canEditPopulation, int offensiveAllocated, int defensiveAllocated, int myOffensiveAllocated, int myOffensiveUsed, int myDefensiveAllocated, int myDefensiveUsed, String claimantName, String allocationSummary, boolean ownerHasKing, int myOffensivePop, int myDefensivePop, int myMountedPop, int myGroundPop, String activeRecruitmentTile, boolean canSetRecruitmentTile, String lotrWaypointKey, String lotrWaypointDisplayName, String lotrWaypointRegion, int waypointLevel, String currentRulingFaction, String defaultRulingFaction, String mapRegion) {
        KOMEMinecraftClient.displayGui(new KOMEGuiConquestCapture(tileId, ownerFaction, pendingFromFaction, pendingToFaction, viewerFaction, offensivePop, defensivePop, mountedPop, groundPop, incomingPop, outgoingPop, incomingEtaMillis, offensiveTotal, offensiveUsed, defensiveTotal, defensiveUsed, farmhandTotal, farmhandUsed, canClaim, canTransfer, canAcceptTransfer, canCancelTransfer, canMoveTroops, canEditPopulation, offensiveAllocated, defensiveAllocated, myOffensiveAllocated, myOffensiveUsed, myDefensiveAllocated, myDefensiveUsed, claimantName, allocationSummary, ownerHasKing, myOffensivePop, myDefensivePop, myMountedPop, myGroundPop, activeRecruitmentTile, canSetRecruitmentTile, lotrWaypointKey, lotrWaypointDisplayName, lotrWaypointRegion, waypointLevel, currentRulingFaction, defaultRulingFaction, mapRegion));
    }

    @Override
    public void displayLordMenu(int entityId, String lordName, String factionName, boolean currentLord) {
        KOMEMinecraftClient.displayGui(new KOMEGuiLordMenu(entityId, lordName, factionName, currentLord));
    }

    @Override
    public void updateProgressionData(String playerName, List completed) {
        KOMEGuiProgression.updateProgressionData(playerName, completed);
    }

    @Override
    public void updateProgressionData(String playerName, List completed, java.util.Map assignments) {
        KOMEGuiProgression.updateProgressionData(playerName, completed, assignments);
    }

    @Override
    public void updateQuotaLedger(List lines) {
        KOMEQuotaLedgerOverlay.update(lines);
    }

    @Override
    public void updateServerRecords(List lines) {
        KOMEGuiServerRecords.update(lines);
    }

    @Override
    public void updateServerRecords(List lines, boolean reset, boolean complete) {
        KOMEGuiServerRecords.update(lines, reset, complete);
    }

    @Override
    public void updateAllianceData(List lines) {
        KOMEGuiAlliance.update(lines);
    }

    @Override
    public void highlightEntity(int entityId, String name, double x, double y, double z) {
        KOMEEntityHighlightOverlay.highlight(entityId, name, x, y, z);
    }
}
