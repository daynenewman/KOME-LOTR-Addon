package kome.common;

import com.enovak.lotrmoremobs.Main;
import com.fuzs.aquaacrobatics.AquaAcrobatics;
import com.lotrcharactercreation.LOTRCharacterCreation;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLModIdMappingEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import kome.common.command.KOMECommandAlliance;
import kome.common.command.KOMECommandBuild;
import kome.common.command.KOMECommandConquest;
import kome.common.command.KOMECommandKome;
import kome.common.command.KOMECommandPopulation;
import kome.common.command.KOMECommandProgression;
import kome.common.command.KOMECommandTroops;
import kome.common.command.KOMECommandWar;
import kome.common.gui.KOMEAllianceGuiHandler;
import kome.common.network.KOMEPacketHandler;
import net.minecraftforge.common.ForgeChunkManager;

import java.util.List;

@Mod(
        modid = KOMEAddon.MODID,
        name = "Kings of Middle-earth Server Addon",
        version = "1.0.8",
        dependencies = "required-after:lotr",
        guiFactory =
                "com.enovak.lotrmoremobs.client.config."
                        + "MumakilConfigGuiFactory"
)

public class KOMEAddon {
    public static final String MODID = "kome";

    private final LOTRCharacterCreation characterCreation = new LOTRCharacterCreation();
    private final Main lotrMoreMobs = new Main();
    private final AquaAcrobatics aquaAcrobatics = new AquaAcrobatics();

    @Mod.Instance(MODID)
    public static KOMEAddon instance;

    @SidedProxy(
            clientSide = "kome.client.KOMEClientProxy",
            serverSide = "kome.common.KOMECommonProxy"
    )
    public static KOMECommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        characterCreation.commonPreInitialize(event);
        lotrMoreMobs.preInit(event);
        aquaAcrobatics.onPreInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        KOMEPacketHandler.init();

        NetworkRegistry.INSTANCE.registerGuiHandler(
                instance,
                new KOMEAllianceGuiHandler()
        );

        ForgeChunkManager.setForcedChunkLoadingCallback(
                instance,
                new ForgeChunkManager.LoadingCallback() {
                    @Override
                    public void ticketsLoaded(
                            List tickets,
                            net.minecraft.world.World world
                    ) {
                        for (Object object : tickets) {
                            if (object instanceof ForgeChunkManager.Ticket) {
                                ForgeChunkManager.releaseTicket(
                                        (ForgeChunkManager.Ticket) object
                                );
                            }
                        }
                    }
                }
        );

        proxy.init();
        characterCreation.initializeSidedProxy();
        lotrMoreMobs.init(event);
        aquaAcrobatics.onInit(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        lotrMoreMobs.postInit(event);
        aquaAcrobatics.onPostInit(event);
    }

    @Mod.EventHandler
    public void mappings(FMLModIdMappingEvent event) {
        aquaAcrobatics.onMappings(event);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        proxy.resetServerSessionState();
        KOMEAllianceGuiHandler.resetSessionState();

        event.registerServerCommand(new KOMECommandAlliance());
        event.registerServerCommand(new KOMECommandBuild());
        event.registerServerCommand(new KOMECommandConquest());
        event.registerServerCommand(new KOMECommandKome());
        event.registerServerCommand(new KOMECommandPopulation());
        event.registerServerCommand(new KOMECommandProgression());
        event.registerServerCommand(new KOMECommandTroops());
        event.registerServerCommand(new KOMECommandWar());

        characterCreation.registerServerCommands(event);
        lotrMoreMobs.serverStarting(event);
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        lotrMoreMobs.serverStopping(event);
    }
}