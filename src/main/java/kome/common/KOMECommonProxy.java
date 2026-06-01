package kome.common;

import cpw.mods.fml.common.FMLCommonHandler;
import kome.common.data.KOMEEvents;
import net.minecraftforge.common.MinecraftForge;

public class KOMECommonProxy {
    public void init() {
        KOMEEvents events = new KOMEEvents();
        MinecraftForge.EVENT_BUS.register(events);
        FMLCommonHandler.instance().bus().register(events);
    }

    public void displayPopulationGui(String playerName, int offensiveTotal, int offensiveUsed, int defensiveTotal, int defensiveUsed, int farmhandsUsed, int farmhandsLimit, int armyUsed, int armyTotal) {
    }

    public void displayPopulationUnitsGui(String playerName, java.util.List lines, int armyUsed, int armyTotal, int farmhandsUsed, int farmhandsLimit) {
    }

    public void displayConquestCaptureGui(String tileId, String ownerFaction, String pendingFromFaction, String pendingToFaction) {
    }

    public void displayConquestCaptureGui(String tileId, String ownerFaction, String pendingFromFaction, String pendingToFaction, int offensivePop, int defensivePop, int mountedPop, int groundPop, int incomingPop, int outgoingPop, long incomingEtaMillis) {
        displayConquestCaptureGui(tileId, ownerFaction, pendingFromFaction, pendingToFaction);
    }

    public void displayLordMenu(int entityId, String lordName, String factionName, boolean currentLord) {
    }

    public void updateProgressionData(String playerName, java.util.List completed) {
    }

    public void updateProgressionData(String playerName, java.util.List completed, java.util.Map assignments) {
        updateProgressionData(playerName, completed);
    }

    public void updateQuotaLedger(java.util.List lines) {
    }

    public void updateServerRecords(java.util.List lines) {
    }

    public void updateAllianceData(java.util.List lines) {
    }

    public void highlightEntity(int entityId, String name, double x, double y, double z) {
    }
}
