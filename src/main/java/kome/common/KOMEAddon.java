package kome.common;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.common.ForgeChunkManager;
import kome.common.command.KOMECommandAlliance;
import kome.common.command.KOMECommandConquest;
import kome.common.command.KOMECommandKome;
import kome.common.command.KOMECommandPopulation;
import kome.common.command.KOMECommandProgression;
import kome.common.command.KOMECommandTroops;
import kome.common.gui.KOMEAllianceGuiHandler;
import kome.common.network.KOMEPacketHandler;

import java.util.List;

@Mod(modid = KOMEAddon.MODID, name = "Kings of Middle-earth Server Addon", version = "1.0.3", dependencies = "required-after:lotr")
public class KOMEAddon {
    public static final String MODID = "kome";

    @Mod.Instance(MODID)
    public static KOMEAddon instance;

    @SidedProxy(clientSide = "kome.client.KOMEClientProxy", serverSide = "kome.common.KOMECommonProxy")
    public static KOMECommonProxy proxy;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        KOMEPacketHandler.init();
        NetworkRegistry.INSTANCE.registerGuiHandler(instance, new KOMEAllianceGuiHandler());
        ForgeChunkManager.setForcedChunkLoadingCallback(instance, new ForgeChunkManager.LoadingCallback() {
            @Override
            public void ticketsLoaded(List tickets, net.minecraft.world.World world) {
                for (Object object : tickets) {
                    if (object instanceof ForgeChunkManager.Ticket) {
                        ForgeChunkManager.releaseTicket((ForgeChunkManager.Ticket) object);
                    }
                }
            }
        });
        proxy.init();
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        proxy.resetServerSessionState();
        KOMEAllianceGuiHandler.resetSessionState();
        event.registerServerCommand(new KOMECommandAlliance());
        event.registerServerCommand(new KOMECommandConquest());
        event.registerServerCommand(new KOMECommandKome());
        event.registerServerCommand(new KOMECommandPopulation());
        event.registerServerCommand(new KOMECommandProgression());
        event.registerServerCommand(new KOMECommandTroops());
    }
}
