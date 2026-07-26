package kome.client;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import kome.client.gui.KOMEGuiConquestCapture;
import kome.client.gui.KOMEGuiAlliance;
import kome.client.gui.KOMEGuiAllianceUnified;
import kome.client.gui.KOMEGuiAllianceDetail;
import kome.client.gui.KOMEGuiLordMenu;
import kome.client.gui.KOMEGuiPopulation;
import kome.client.gui.KOMEGuiProgression;
import kome.client.gui.KOMEGuiServerRecords;
import kome.common.KOMECommonProxy;
import kome.common.data.KOMEClientData;
import kome.common.data.KOMEAlliance;
import lotr.client.gui.LOTRGuiMap;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.ClientChatReceivedEvent;

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
        KOMEWaypointMapOverlay waypointMapOverlay = new KOMEWaypointMapOverlay();
        MinecraftForge.EVENT_BUS.register(waypointMapOverlay);
        FMLCommonHandler.instance().bus().register(waypointMapOverlay);
        MinecraftForge.EVENT_BUS.register(this);
        KOMEConquestMapOverlay conquestMapOverlay = new KOMEConquestMapOverlay();
        FMLCommonHandler.instance().bus().register(conquestMapOverlay);
        MinecraftForge.EVENT_BUS.register(conquestMapOverlay);
        FMLCommonHandler.instance().bus().register(this);
        if (Boolean.getBoolean("kome.guiCapture")) {
            FMLCommonHandler.instance().bus().register(new kome.client.gui.KOMEGuiVisualCaptureController());
        }
    }

    @SubscribeEvent
    public void onClientConnect(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        resetClientSessionState();
    }

    @SubscribeEvent
    public void onClientDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        resetClientSessionState();
    }

    @SubscribeEvent
    public void onClientChat(ClientChatReceivedEvent event) {
        if (event != null && event.message != null
                && KOMEMinecraftClient.currentScreen() instanceof KOMEGuiAllianceDetail) {
            KOMEGuiAlliance.setServerMessage(event.message.getUnformattedText());
        }
    }

    private void resetClientSessionState() {
        KOMEClientData.INSTANCE.resetClientState();
        KOMEQuotaLedgerOverlay.reset();
        KOMEGuiAlliance.resetData();
        KOMEGuiAllianceUnified.resetData();
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
    public void displayCompanyListGui(String tileId, String tileDisplayName, List companies, boolean canCreate) {
        KOMEMinecraftClient.displayGui(new kome.client.gui.KOMEGuiCompanyList(tileId, tileDisplayName, companies, canCreate));
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
    public void displayConquestCaptureGui(kome.common.network.KOMEPacketConquestCaptureGui message) {
        KOMEMinecraftClient.displayGui(new KOMEGuiConquestCapture(message));
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
        updateClientAllianceCache(lines);
        KOMEGuiAlliance.update(lines);
        KOMEGuiAllianceUnified.update(lines);
    }

    @Override
    public void displayPledgeDeparture(kome.common.network.KOMEPacketPledgeDepartureData message) {
        net.minecraft.client.gui.GuiScreen current = net.minecraft.client.Minecraft.getMinecraft().currentScreen;
        net.minecraft.client.gui.GuiScreen parent = current instanceof kome.client.gui.KOMEGuiPledgeDeparture
            ? ((kome.client.gui.KOMEGuiPledgeDeparture) current).getParentScreen() : current;
        kome.client.gui.KOMEGuiPledgeDeparture next = new kome.client.gui.KOMEGuiPledgeDeparture(message, parent);
        if (current instanceof kome.client.gui.KOMEGuiPledgeDeparture) {
            next.setScroll(((kome.client.gui.KOMEGuiPledgeDeparture) current).getScroll());
        }
        KOMEMinecraftClient.displayGui(next);
    }

    private void updateClientAllianceCache(List lines) {
        KOMEClientData.INSTANCE.alliances.clear();
        KOMEClientData.INSTANCE.allianceRequirementOverrides.clear();
        if (lines == null) {
            return;
        }
        for (Object value : lines) {
            String[] parts = String.valueOf(value).split("\t", -1);
            if (parts.length >= 7 && "CONFIG".equals(parts[0])) {
                KOMEClientData.INSTANCE.allianceDifficulty = parts[1];
                KOMEClientData.INSTANCE.waypointRestrictionEnabled = "1".equals(parts[3]);
                KOMEClientData.INSTANCE.clientWaypointBypass = "1".equals(parts[4]);
                KOMEClientData.INSTANCE.successionGraceDefaultMillis = parseLong(parts[5]);
                KOMEClientData.INSTANCE.contributionGraceDefaultMillis = parseLong(parts[6]);
                continue;
            }
            if (parts.length >= 6 && "REQUIREMENT".equals(parts[0])) {
                int tier = parseTier(parts[2]);
                KOMEClientData.INSTANCE.allianceRequirementOverrides.put(
                    kome.common.data.KOMEAllianceRequirements.key(parts[1], tier, "items"), Integer.valueOf(parseTier(parts[3])));
                KOMEClientData.INSTANCE.allianceRequirementOverrides.put(
                    kome.common.data.KOMEAllianceRequirements.key(parts[1], tier, "activity"), Integer.valueOf(parseTier(parts[4])));
                KOMEClientData.INSTANCE.allianceRequirementOverrides.put(
                    kome.common.data.KOMEAllianceRequirements.key(parts[1], tier, "population"), Integer.valueOf(parseTier(parts[5])));
                continue;
            }
            if (parts.length >= 11 && "STAGE_RELATION".equals(parts[0])
                    && "active".equalsIgnoreCase(parts[10])) {
                KOMEAlliance alliance = new KOMEAlliance(parts[2], parts[3]);
                alliance.requestTrack(KOMEAlliance.CIVIL, "server", 0L, false);
                alliance.setFactionStage(parts[6], parseTier(parts[8]), "server", 0L, 0L);
                alliance.setFactionStage(parts[7], parseTier(parts[9]), "server", 0L, 0L);
                KOMEClientData.INSTANCE.alliances.put(alliance.getPairKey(), alliance);
                continue;
            }
            if (parts.length < 8 || !"ALLIANCE".equals(parts[0])) {
                continue;
            }
            if (KOMEClientData.INSTANCE.alliances.containsKey(KOMEAlliance.pairKey(parts[1], parts[2]))) {
                continue;
            }
            KOMEAlliance alliance = new KOMEAlliance(parts[1], parts[2]);
            alliance.setTier(KOMEAlliance.CIVIL, parseTier(parts[5]), "server", 0L);
            alliance.setTier(KOMEAlliance.MILITARY, parseTier(parts[6]), "server", 0L);
            alliance.setTier(KOMEAlliance.TRADE, parseTier(parts[7]), "server", 0L);
            KOMEClientData.INSTANCE.alliances.put(alliance.getPairKey(), alliance);
        }
    }

    private int parseTier(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return KOMEAlliance.NONE;
        }
    }

    private long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    @Override
    public void highlightEntity(int entityId, String name, double x, double y, double z) {
        KOMEEntityHighlightOverlay.highlight(entityId, name, x, y, z);
    }
}
