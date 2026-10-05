package kome.client;

import com.lotrcharactercreation.proxy.ClientProxy;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import kome.client.gui.KOMEGuiConquestCapture;
import kome.client.gui.KOMEGuiAllianceUnified;
import kome.client.gui.KOMEGuiLordMenu;
import kome.client.gui.KOMEGuiPopulation;
import kome.client.gui.KOMEGuiProgression;
import kome.client.gui.KOMEGuiServerRecords;
import kome.common.KOMECommonProxy;
import kome.common.data.KOMEClientData;
import kome.common.data.KOMEAlliance;
import lotr.client.gui.LOTRGuiMap;
import net.minecraftforge.common.MinecraftForge;

import java.util.List;

public class KOMEClientProxy extends KOMECommonProxy {
    // Created on first editor publication; general client lifecycle does not require an editor session.
    private kome.common.tactical.edit.KOMETacticalEditClientMirror tacticalEditor;
    private kome.common.tactical.edit.KOMETacticalEditSessionManager.Status tacticalEditorStatus;
    // Network intake reads this; client world/connection lifecycle advances it.
    private volatile long tacticalEditorLifecycleEpoch;
    private kome.client.tactical.KOMETacticalAreaEditor tacticalAreaEditor;
    private final KOMEClientTaskQueue clientTasks = new KOMEClientTaskQueue(
            () -> net.minecraft.client.Minecraft.getMinecraft().func_152345_ab());
    private final KOMEConquestSnapshotPublisher conquestSnapshots =
        new KOMEConquestSnapshotPublisher(clientTasks);
    private KOMECurrentTileHud currentTileHud;
    private final KOMEProgressionTrackerOverlay progressionTrackerOverlay =
        new KOMEProgressionTrackerOverlay();
    public KOMEClientProxy() {
        super(new ClientProxy());
        com.enovak.lotrmoremobs.Main.proxy =
                new com.enovak.lotrmoremobs.proxy.ClientProxy();
com.fuzs.aquaacrobatics.AquaAcrobatics.proxy =
        new com.fuzs.aquaacrobatics.proxy.ClientProxy();
    }



    @Override
    public void init() {
        super.init();
        FMLCommonHandler.instance().bus().register(clientTasks);
        kome.client.tactical.KOMETacticalAreaInteractionHandler tacticalInput = new kome.client.tactical.KOMETacticalAreaInteractionHandler(this);
        FMLCommonHandler.instance().bus().register(tacticalInput);
        MinecraftForge.EVENT_BUS.register(tacticalInput);
        KOMEClientConfig clientConfig = new KOMEClientConfig(new java.io.File(
            cpw.mods.fml.common.Loader.instance().getConfigDir(), "kome-client.cfg"));
        currentTileHud = new KOMECurrentTileHud(net.minecraft.client.Minecraft.getMinecraft(), clientConfig);
        cpw.mods.fml.client.registry.ClientRegistry.registerKeyBinding(currentTileHud.toggle);
        ((net.minecraft.client.resources.IReloadableResourceManager)
            net.minecraft.client.Minecraft.getMinecraft().getResourceManager()).registerReloadListener(currentTileHud);
        FMLCommonHandler.instance().bus().register(currentTileHud);
        MinecraftForge.EVENT_BUS.register(currentTileHud);
        MinecraftForge.EVENT_BUS.register(new KOMEChatSanitizer());
        MinecraftForge.EVENT_BUS.register(new KOMECourierBookPagination());
        MinecraftForge.EVENT_BUS.register(new KOMEProgressionMenuOverlay());
        MinecraftForge.EVENT_BUS.register(new KOMELiegeQuestButtonOverlay());
        MinecraftForge.EVENT_BUS.register(new KOMEQuotaLedgerOverlay());
        MinecraftForge.EVENT_BUS.register(new KOMEUnitOverviewCapOverlay());
        MinecraftForge.EVENT_BUS.register(new KOMEEntityHighlightOverlay());
        MinecraftForge.EVENT_BUS.register(progressionTrackerOverlay);
        FMLCommonHandler.instance().bus().register(progressionTrackerOverlay);
        KOMEWaypointMapOverlay waypointMapOverlay = new KOMEWaypointMapOverlay();
        MinecraftForge.EVENT_BUS.register(waypointMapOverlay);
        FMLCommonHandler.instance().bus().register(waypointMapOverlay);
        MinecraftForge.EVENT_BUS.register(this);
        KOMEConquestMapOverlay conquestMapOverlay = new KOMEConquestMapOverlay();
        ((net.minecraft.client.resources.IReloadableResourceManager)
            net.minecraft.client.Minecraft.getMinecraft().getResourceManager()).registerReloadListener(conquestMapOverlay);
        FMLCommonHandler.instance().bus().register(conquestMapOverlay);
        MinecraftForge.EVENT_BUS.register(conquestMapOverlay);
        FMLCommonHandler.instance().bus().register(this);
        if (kome.client.gui.KOMEGuiVisualCaptureController.isCaptureEnabled()) {
            FMLCommonHandler.instance().bus().register(new kome.client.gui.KOMEGuiVisualCaptureController());
        }
    }

    @SubscribeEvent
    public void onClientConnect(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        invalidateTacticalEditorPublications();
        kome.common.data.KOMEPublicWaypointClientState.INSTANCE.start(event == null ? null : event.handler);
        conquestSnapshots.resetSession();
        final long tileSession = currentTileHud == null ? 0L : currentTileHud.suspendSession();
        clientTasks.resetSession(true, () -> {
            resetClientSessionState();
            if (currentTileHud != null) currentTileHud.startSession(tileSession);
        });
    }

    @SubscribeEvent
    public void onClientDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        invalidateTacticalEditorPublications();
        kome.common.data.KOMEPublicWaypointClientState.INSTANCE.start(null);
        conquestSnapshots.resetSession();
        if (currentTileHud != null) currentTileHud.suspendSession();
        clientTasks.resetSession(false, this::resetClientSessionState);
    }

    @SubscribeEvent
    public void onClientWorldUnload(net.minecraftforge.event.world.WorldEvent.Unload event) {
        if (event.world != null && event.world.isRemote) {
            invalidateTacticalEditorPublications();
            if (tacticalAreaEditor != null) tacticalAreaEditor.reset();
            if (tacticalEditor != null) tacticalEditor.clearScope();
            tacticalEditorStatus = null;
            conquestSnapshots.resetSession();
            KOMEClientData.INSTANCE.clearConquestTooltip();
        }
    }

    @Override
    public void enqueueClientTask(Runnable task) {
        clientTasks.enqueue(task);
    }

    @Override
    public void acceptConquestSnapshotChunk(
            kome.common.network.KOMEPacketConquestData.PublicationChunk chunk) {
        conquestSnapshots.accept(chunk);
    }

    @Override
    public void acceptPublicWaypoints(kome.common.network.KOMEPacketPublicWaypoints.Chunk chunk,Object connection) {
        enqueueClientTask(() -> kome.common.data.KOMEPublicWaypointClientState.INSTANCE.accept(connection,chunk));
    }

    private void resetClientSessionState() {
        if (tacticalAreaEditor != null) tacticalAreaEditor.reset();
        if (tacticalEditor != null) tacticalEditor.reset();
        tacticalEditorStatus = null;
        if (currentTileHud != null) currentTileHud.clear();
        KOMEClientData.INSTANCE.resetClientState();
        KOMEQuotaLedgerOverlay.reset();
        KOMEGuiAllianceUnified.resetData();
        KOMEGuiProgression.resetData();
        KOMEFactionTitleClientBridge.reset();
        KOMEGuiServerRecords.resetData();
        KOMEUnitCapClientState.reset();
        KOMEConquestMapOverlay.resetClientMapState();
        KOMEVisualMarkerClientState.clear();
        KOMELiegeQuestButtonOverlay.reset();
        progressionTrackerOverlay.resetSession();
    }

    private synchronized void invalidateTacticalEditorPublications() { ++tacticalEditorLifecycleEpoch; }

    @Override public void acceptTacticalEditSnapshot(final kome.common.network.KOMEPacketTacticalEditSnapshot message) {
        // Keep response ordering until the mirror can compare server publication numbers.
        final long publicationEpoch = tacticalEditorLifecycleEpoch;
        try { clientTasks.enqueue(() -> {
            if (publicationEpoch != tacticalEditorLifecycleEpoch) return;
            if (tacticalEditor == null) tacticalEditor = new kome.common.tactical.edit.KOMETacticalEditClientMirror();
            net.minecraft.entity.player.EntityPlayer player = net.minecraft.client.Minecraft.getMinecraft().thePlayer;
            if (player != null && (message.getSnapshot() == null
                    || tacticalEditor.accept(player.getUniqueID(), player.dimension, message.getSnapshot()))) {
                tacticalEditorStatus = message.getStatus();
                if (tacticalAreaEditor != null) {
                    tacticalAreaEditor.accept(message.getSnapshot(), message.getStatus());
                    if (message.getSnapshot() != null && !message.getSnapshot().isClosed() && tacticalAreaEditor.isEditing()
                            && tacticalAreaEditor.getSelection() == kome.client.tactical.KOMETacticalAreaEditor.Selection.NONE) showTacticalEditor();
                }
            }
        }); } catch (java.util.concurrent.RejectedExecutionException disconnectedOrFull) {
            // Late/disconnected or excess publications cannot revive client editor state.
        }
    }
    /** Client-thread read API for later UI; both the mirror snapshot and its domain definitions are immutable. */
    public kome.common.tactical.edit.KOMETacticalEditSnapshot getTacticalEditorSnapshot() { return tacticalEditor == null ? null : tacticalEditor.getSnapshot(); }
    public kome.common.tactical.edit.KOMETacticalEditSessionManager.Status getTacticalEditorStatus() { return tacticalEditorStatus; }
    public kome.client.tactical.KOMETacticalAreaEditor getTacticalAreaEditor() { return tacticalAreaEditor; }
    private void showTacticalEditor() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        if (mc.fontRenderer != null && !(mc.currentScreen instanceof kome.client.gui.KOMEGuiTacticalAreaEditor))
            mc.displayGuiScreen(new kome.client.gui.KOMEGuiTacticalAreaEditor(tacticalAreaEditor));
    }
    @Override public void acceptTacticalAreaCatalog(final kome.common.network.KOMEPacketTacticalAreaCatalog packet) {
        final long epoch = tacticalEditorLifecycleEpoch;
        try { clientTasks.enqueue(() -> {
            net.minecraft.entity.player.EntityPlayer player = net.minecraft.client.Minecraft.getMinecraft().thePlayer;
            if (epoch != tacticalEditorLifecycleEpoch || player == null || player.dimension != packet.getCatalog().dimension) return;
            if (tacticalAreaEditor == null) tacticalAreaEditor = new kome.client.tactical.KOMETacticalAreaEditor(
                request -> kome.common.network.KOMEPacketHandler.network.sendToServer(new kome.common.network.KOMEPacketTacticalEditRequest(request)));
            if (tacticalAreaEditor.acceptCatalog(packet.getCatalog())) showTacticalEditor();
        }); } catch (java.util.concurrent.RejectedExecutionException disconnectedOrFull) { }
    }

    @Override public void acceptTacticalComplexCatalog(final kome.common.network.KOMEPacketTacticalComplexCatalog packet) {
        final long epoch = tacticalEditorLifecycleEpoch;
        try { clientTasks.enqueue(() -> {
            net.minecraft.entity.player.EntityPlayer player = net.minecraft.client.Minecraft.getMinecraft().thePlayer;
            if (epoch != tacticalEditorLifecycleEpoch || player == null || player.dimension != packet.getCatalog().dimension || tacticalAreaEditor == null) return;
            if (tacticalAreaEditor.acceptComplexCatalog(packet.getCatalog())) showTacticalEditor();
        }); } catch (java.util.concurrent.RejectedExecutionException disconnectedOrFull) { }
    }

    @Override
    public void displayPopulationGui(kome.common.network.KOMEPacketPopulationGui message) {
        KOMEMinecraftClient.displayGui(new KOMEGuiPopulation(message));
    }

    @Override
    public void displayPopulationUnitsGui(kome.common.network.KOMEPacketPopulationUnitsGui message) {
        KOMEMinecraftClient.displayGui(new kome.client.gui.KOMEGuiPopulationUnits(message));
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
        if (!(KOMEMinecraftClient.currentScreen() instanceof LOTRGuiMap)) {
            KOMEConquestMapOverlay.openPreservedMap();
        }
        // Publish only after the map exists. An END tick with a null screen clears previews.
        KOMEConquestMapOverlay.beginRoutePreview(message);
    }

    @Override
    public void displayCompanyMovePreviewResult(kome.common.network.KOMEPacketCompanyMovePreviewResult message) {
        if (!(KOMEMinecraftClient.currentScreen() instanceof LOTRGuiMap)) {
            KOMEConquestMapOverlay.openPreservedMap();
        }
        KOMEConquestMapOverlay.showCompanyMovePreviewResult(message);
    }

    @Override public void displayJoinBattleGui(kome.common.network.KOMEPacketJoinBattleViewResponse message) {
        net.minecraft.client.gui.GuiScreen current=KOMEMinecraftClient.currentScreen();
        if(current instanceof kome.client.gui.KOMEGuiJoinBattle
                && ((kome.client.gui.KOMEGuiJoinBattle)current).tileId().equals(message.tileId))
            ((kome.client.gui.KOMEGuiJoinBattle)current).acceptView(message);
        else KOMEMinecraftClient.displayGui(new kome.client.gui.KOMEGuiJoinBattle(message));
    }

    @Override public void displayJoinBattleSelectionResult(kome.common.network.KOMEPacketJoinBattleSelectionResult message) {
        net.minecraft.client.gui.GuiScreen current=KOMEMinecraftClient.currentScreen();
        if(current instanceof kome.client.gui.KOMEGuiJoinBattle)
            ((kome.client.gui.KOMEGuiJoinBattle)current).acceptSelection(message);
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
    public void displayLordMenu(int entityId, String lordName, String factionName, boolean currentLord) {
        KOMEMinecraftClient.displayGui(new KOMEGuiLordMenu(entityId, lordName, factionName, currentLord));
    }

    @Override
    public void displaySerfdomMasterMenu(int entityId, String masterName, String factionName, int mode, String dutyStatus) {
        KOMEMinecraftClient.displayGui(new kome.client.gui.KOMEGuiSerfdomMaster(entityId, masterName, factionName, mode, dutyStatus));
    }
    @Override public void displaySerfdomMasterMenu(int entityId,String masterName,String factionName,int mode,String dutyStatus,boolean canRequestDuty,boolean hasActiveDuty){KOMEMinecraftClient.displayGui(new kome.client.gui.KOMEGuiSerfdomMaster(entityId,masterName,factionName,mode,dutyStatus,canRequestDuty,hasActiveDuty));}
    @Override public void displayRelationshipHub(int entityId, int relationship, String npcName, String factionName) {
        displayRelationshipHub(entityId, relationship, npcName, factionName, true);
    }
    @Override public void displayRelationshipHub(int entityId, int relationship, String npcName, String factionName, boolean allowService) {
        KOMEMinecraftClient.displayGui(new kome.client.gui.KOMEGuiRelationshipHub(entityId, relationship, npcName, factionName, allowService));
    }

    @Override
    public void updateProgressionData(String playerName, List completed) {
        KOMEGuiProgression.updateProgressionData(playerName, completed);
    }

    @Override
    public void updateProgressionData(String playerName, List completed, java.util.Map assignments) {
        KOMEGuiProgression.updateProgressionData(playerName, completed, assignments);
    }
    @Override public void updateProgressionData(String playerName, List completed, java.util.Map assignments, String summary, String findLabel, String leaveType, String leaveLabel, String leaveName) { KOMEGuiProgression.updateProgressionData(playerName, completed, assignments, summary, findLabel, leaveType, leaveLabel, leaveName); }
    @Override
    public void updateProgressionData(
            String playerName,
            List completed,
            java.util.Map assignments,
            String summary,
            String findLabel,
            String leaveType,
            String leaveLabel,
            String leaveName,
            kome.common.data.KOMEProgressionRankSummary ranks) {
        KOMEFactionTitleClientBridge.updateRankSummary(ranks);
        KOMEGuiProgression.updateProgressionData(
            playerName,
            completed,
            assignments,
            summary,
            findLabel,
            leaveType,
            leaveLabel,
            leaveName,
            ranks);
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
        KOMEGuiAllianceUnified.update(lines);
    }

    @Override
    public void updateVisualMarkers(List<kome.common.data.KOMEVisualMarker> markers) {
        KOMEVisualMarkerClientState.update(markers);
    }

    @Override
    public void updateProgressionTracker(
            kome.common.data.KOMEProgressionTrackerSnapshot snapshot) {
        progressionTrackerOverlay.update(snapshot);
    }

    @Override
    public void updateStandingTrialEligibility(
            int entityId,
            long entityUuidMost,
            long entityUuidLeast,
            boolean eligible,
            boolean passiveOffer,
            boolean offering,
            int offerColor) {
        KOMELiegeQuestButtonOverlay.updateEligibility(
            entityId,
            new java.util.UUID(entityUuidMost, entityUuidLeast),
            eligible,
            passiveOffer,
            offering,
            offerColor);
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
        KOMEClientData.INSTANCE.canonicalDiplomacyRecords.clear();

        if (lines == null) {
            return;
        }

        for (Object value : lines) {
            String[] parts = String.valueOf(value).split("\t", -1);

            if (parts.length >= 13 && "DIPLOMACY_RELATION".equals(parts[0])) {
                try {
                    kome.common.data.KOMEDiplomacyRecord record =
                        new kome.common.data.KOMEDiplomacyRecord(parts[2], parts[3]);

                    record.relation =
                        kome.common.data.KOMEDiplomacyRelation.parse(parts[4]);

                    if ("1".equals(parts[5]) && parts[6].length() > 0) {
                        record.pendingTarget =
                            kome.common.data.KOMEDiplomacyRelation.parse(parts[6]);
                        record.requestingFaction =
                            KOMEAlliance.normalizeFactionKey(parts[7]);
                        record.receivingFaction =
                            KOMEAlliance.normalizeFactionKey(parts[8]);
                    }

                    record.lastUpdatedBy = parts[11];

                    KOMEClientData.INSTANCE.canonicalDiplomacyRecords.put(
                        record.key(), record);
                } catch (RuntimeException ignored) {
                    // Malformed client cache data cannot grant authority.
                    // The server remains authoritative.
                }

                continue;
            }

            if (parts.length >= 6 && "REQUIREMENT".equals(parts[0])) {
                int tier = parseTier(parts[2]);

                KOMEClientData.INSTANCE.allianceRequirementOverrides.put(
                    kome.common.data.KOMEAllianceRequirements.key(
                        parts[1], tier, "items"),
                    Integer.valueOf(parseTier(parts[3])));

                KOMEClientData.INSTANCE.allianceRequirementOverrides.put(
                    kome.common.data.KOMEAllianceRequirements.key(
                        parts[1], tier, "activity"),
                    Integer.valueOf(parseTier(parts[4])));

                KOMEClientData.INSTANCE.allianceRequirementOverrides.put(
                    kome.common.data.KOMEAllianceRequirements.key(
                        parts[1], tier, "population"),
                    Integer.valueOf(parseTier(parts[5])));
            }
        }
    }
    private int parseTier(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return KOMEAlliance.NONE;
        }
    }

    @Override
    public void highlightEntity(int entityId, String name, double x, double y, double z) {
        KOMEEntityHighlightOverlay.highlight(entityId, name, x, y, z);
    }
}
