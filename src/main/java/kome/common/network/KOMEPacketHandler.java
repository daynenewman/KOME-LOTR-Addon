package kome.common.network;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import kome.common.KOMEAddon;

public class KOMEPacketHandler {
    public static SimpleNetworkWrapper network;

    public static void init() {
        network = NetworkRegistry.INSTANCE.newSimpleChannel(KOMEAddon.MODID);
        network.registerMessage(KOMEPacketPopulationGui.Handler.class, KOMEPacketPopulationGui.class, 0, Side.CLIENT);
        network.registerMessage(KOMEPacketTerritoryGui.Handler.class, KOMEPacketTerritoryGui.class, 1, Side.CLIENT);
        network.registerMessage(KOMEPacketTerritoryData.Handler.class, KOMEPacketTerritoryData.class, 2, Side.CLIENT);
        network.registerMessage(KOMEPacketPopulationUnitsGui.Handler.class, KOMEPacketPopulationUnitsGui.class, 3, Side.CLIENT);
    }
}
