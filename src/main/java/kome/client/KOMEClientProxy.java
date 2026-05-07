package kome.client;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import kome.client.gui.KOMEGuiPopulation;
import kome.client.gui.KOMEGuiTerritory;
import kome.common.KOMECommonProxy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.common.MinecraftForge;
import org.lwjgl.input.Keyboard;

import java.util.List;

public class KOMEClientProxy extends KOMECommonProxy {
    public static KeyBinding populationGuiKey = new KeyBinding("Population GUI", Keyboard.KEY_P, "KOME");
    public static KeyBinding territoryGuiKey = new KeyBinding("Territory GUI", Keyboard.KEY_G, "KOME");

    @Override
    public void init() {
        super.init();
        ClientRegistry.registerKeyBinding(populationGuiKey);
        ClientRegistry.registerKeyBinding(territoryGuiKey);
        FMLCommonHandler.instance().bus().register(new KOMEKeyHandler());
        MinecraftForge.EVENT_BUS.register(new KOMEChatSanitizer());
        MinecraftForge.EVENT_BUS.register(new KOMEUnitTradeOverlay());
        MinecraftForge.EVENT_BUS.register(new KOMEConquestMapOverlay());
        MinecraftForge.EVENT_BUS.register(new KOMEMapOverlay());
    }

    @Override
    public void displayPopulationGui(String playerName, int offensiveTotal, int offensiveUsed, int defensiveTotal, int defensiveUsed, int farmhandsUsed, int farmhandsLimit, int armyUsed, int armyTotal) {
        Minecraft.getMinecraft().displayGuiScreen(new KOMEGuiPopulation(playerName, offensiveTotal, offensiveUsed, defensiveTotal, defensiveUsed, farmhandsUsed, farmhandsLimit, armyUsed, armyTotal));
    }

    @Override
    public void displayPopulationUnitsGui(String playerName, List lines, int armyUsed, int armyTotal, int farmhandsUsed, int farmhandsLimit) {
        Minecraft.getMinecraft().displayGuiScreen(new kome.client.gui.KOMEGuiPopulationUnits(playerName, lines, armyUsed, armyTotal, farmhandsUsed, farmhandsLimit));
    }

    @Override
    public void displayTerritoryGui(String waypoint, String waypointName, String faction, String ruler, String displayName) {
        Minecraft.getMinecraft().displayGuiScreen(new KOMEGuiTerritory(waypoint, waypointName, faction, ruler, displayName));
    }
}
