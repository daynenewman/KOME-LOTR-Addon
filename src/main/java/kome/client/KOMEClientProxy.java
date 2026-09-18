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
    private final KOMEClientTaskQueue clientTasks = new KOMEClientTaskQueue();
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
        MinecraftForge.EVENT_BUS.register(new KOMEChatSanitizer());
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
        if (kome.client.gui.KOMEGuiVisualCaptureController.isCaptureEnabled()) {
            FMLCommonHandler.instance().bus().register(new kome.client.gui.KOMEGuiVisualCaptureController());
        }
    }

    @SubscribeEvent
    public void onClientConnect(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        clientTasks.resetSession(true, this::resetClientSessionState);
    }

    @SubscribeEvent
    public void onClientDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        clientTasks.resetSession(false, this::resetClientSessionState);
    }

    @Override
    public void enqueueClientTask(Runnable task) {
        clientTasks.enqueue(task);
    }

    private void resetClientSessionState() {
        KOMEClientData.INSTANCE.resetClientState();
        KOMEQuotaLedgerOverlay.reset();
        KOMEGuiAllianceUnified.resetData();
        KOMEGuiProgression.resetData();
        KOMEGuiServerRecords.resetData();
        KOMEUnitCapClientState.reset();
        KOMEConquestMapOverlay.resetClientMapState();
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
