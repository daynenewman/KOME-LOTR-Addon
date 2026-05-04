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

    public void displayTerritoryGui(String waypoint, String waypointName, String faction, String ruler, String displayName) {
    }
}
