package kome.common;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import kome.common.command.KOMECommandConquest;
import kome.common.command.KOMECommandPopulation;
import kome.common.command.KOMECommandProgression;
import kome.common.command.KOMECommandTerritory;
import kome.common.network.KOMEPacketHandler;

@Mod(modid = KOMEAddon.MODID, name = "Kings of Middle-earth Server Addon", version = "1.0.0", dependencies = "required-after:lotr")
public class KOMEAddon {
    public static final String MODID = "kome";

    @Mod.Instance(MODID)
    public static KOMEAddon instance;

    @SidedProxy(clientSide = "kome.client.KOMEClientProxy", serverSide = "kome.common.KOMECommonProxy")
    public static KOMECommonProxy proxy;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        KOMEPacketHandler.init();
        proxy.init();
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new KOMECommandConquest());
        event.registerServerCommand(new KOMECommandPopulation());
        event.registerServerCommand(new KOMECommandProgression());
        event.registerServerCommand(new KOMECommandTerritory());
    }
}
