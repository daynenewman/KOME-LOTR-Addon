package kome.client;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import kome.client.gui.KOMEGuiConquestCapture;
import kome.client.gui.KOMEGuiPopulation;
import kome.client.gui.KOMEGuiProgression;
import kome.client.gui.KOMEGuiServerRecords;
import kome.client.gui.KOMEGuiTerritory;
import kome.common.KOMECommonProxy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.common.MinecraftForge;
import org.lwjgl.input.Keyboard;

import java.util.List;

public class KOMEClientProxy extends KOMECommonProxy {
    public static final int TERRITORY_GUI_KEY_CODE = Keyboard.KEY_G;
    public static KeyBinding populationGuiKey = new KeyBinding("Population GUI", Keyboard.KEY_P, "KOME");
    public static KeyBinding territoryGuiKey = new KeyBinding("Territory GUI", TERRITORY_GUI_KEY_CODE, "KOME");

    @Override
    public void init() {
        super.init();
        ClientRegistry.registerKeyBinding(populationGuiKey);
        ClientRegistry.registerKeyBinding(territoryGuiKey);
        FMLCommonHandler.instance().bus().register(new KOMEKeyHandler());
        MinecraftForge.EVENT_BUS.register(new KOMEChatSanitizer());
        MinecraftForge.EVENT_BUS.register(new KOMEUnitTradeOverlay());
        MinecraftForge.EVENT_BUS.register(new KOMEProgressionMenuOverlay());
        MinecraftForge.EVENT_BUS.register(new KOMEQuotaLedgerOverlay());
        MinecraftForge.EVENT_BUS.register(new KOMEUnitOverviewCapOverlay());
        KOMEConquestMapOverlay conquestMapOverlay = new KOMEConquestMapOverlay();
        FMLCommonHandler.instance().bus().register(conquestMapOverlay);
        MinecraftForge.EVENT_BUS.register(conquestMapOverlay);
        MinecraftForge.EVENT_BUS.register(new KOMEMapOverlay());
    }

    @Override
    public void displayPopulationGui(String playerName, int offensiveTotal, int offensiveUsed, int defensiveTotal, int defensiveUsed, int farmhandsUsed, int farmhandsLimit, int armyUsed, int armyTotal) {
        KOMEMinecraftClient.displayGui(new KOMEGuiPopulation(playerName, offensiveTotal, offensiveUsed, defensiveTotal, defensiveUsed, farmhandsUsed, farmhandsLimit, armyUsed, armyTotal));
    }

    @Override
    public void displayPopulationUnitsGui(String playerName, List lines, int armyUsed, int armyTotal, int farmhandsUsed, int farmhandsLimit) {
        KOMEMinecraftClient.displayGui(new kome.client.gui.KOMEGuiPopulationUnits(playerName, lines, armyUsed, armyTotal, farmhandsUsed, farmhandsLimit));
    }

    @Override
    public void displayTerritoryGui(String waypoint, String waypointName, String faction, String ruler, String displayName) {
        KOMEMinecraftClient.displayGui(new KOMEGuiTerritory(waypoint, waypointName, faction, ruler, displayName));
    }

    @Override
    public void displayConquestCaptureGui(String tileId, String ownerFaction, String ruler) {
        KOMEMinecraftClient.displayGui(new KOMEGuiConquestCapture(tileId, ownerFaction, ruler));
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
}
