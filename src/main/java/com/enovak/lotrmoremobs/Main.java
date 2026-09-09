package com.enovak.lotrmoremobs;

import java.io.File;

import net.minecraft.item.ItemStack;
import com.enovak.lotrmoremobs.trade.MumakilItemTradeInjector; // MUMAKIL_SHANK_SYSTEM_V1_1
import com.enovak.lotrmoremobs.achievement.MumakilAchievements;
import com.enovak.lotrmoremobs.config.MumakilConfig;
import com.enovak.lotrmoremobs.recipe.MumakilHowdahRecipeRegistry; // MUMAKIL_NEAR_HARAD_HOWDAH_RECIPE_V1_1
import com.enovak.lotrmoremobs.entity.animal.LOTREntityMumakil;
import com.enovak.lotrmoremobs.entity.npc.LOTREntityMumakilHowdahArcher;
import com.enovak.lotrmoremobs.hiring.MumakilUnitTradeInjector;
import com.enovak.lotrmoremobs.hiring.BattleRamUnitTradeInjector;
import com.enovak.lotrmoremobs.item.LOTRItemMumakilHowdah;
import com.enovak.lotrmoremobs.item.LOTRItemMumakilCalfSpawnEgg; // MUMAKIL_CALF_SPAWN_EGG_V1
import com.enovak.lotrmoremobs.item.LOTRItemMumakilHowdahSpawnEgg;
import com.enovak.lotrmoremobs.item.LOTRItemMumakilShank;
import com.enovak.lotrmoremobs.item.LOTRItemMumakilTusk;
import com.enovak.lotrmoremobs.proxy.CommonProxy;
import com.enovak.lotrmoremobs.siege.SiegeRegistry;
import com.enovak.lotrmoremobs.siege.command.CommandSiegeGateDebug;
import com.enovak.lotrmoremobs.siege.network.SiegeNetwork;
import com.enovak.lotrmoremobs.siege.network.SiegeRequestLifecycle;
import com.enovak.lotrmoremobs.spawning.MumakilNaturalSpawnRegistry;
import com.enovak.lotrmoremobs.spawning.MumakilWarFormationSpawnRegistry;
import com.enovak.lotrmoremobs.spawning.MumakilInvasionFormationRegistry;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import lotr.common.entity.LOTREntities;
import net.minecraft.item.Item;
import com.enovak.lotrmoremobs.network.MumakilOpenGuiPacket;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import com.enovak.lotrmoremobs.command.CommandPickupFilter;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import com.enovak.lotrmoremobs.network.PickupFilterClearPacket;
import com.enovak.lotrmoremobs.network.PickupFilterSyncPacket;
import com.enovak.lotrmoremobs.network.PickupFilterTogglePacket;
import com.enovak.lotrmoremobs.network.ServerGameplaySyncPacket;
import com.enovak.lotrmoremobs.pickupfilter.PickupFilterRequestManager;
import com.enovak.lotrmoremobs.pickupfilter.PlayerPickupFilterData;

public class Main {

    public static final String MODID = "lotrmoremobs";
    public static final String VERSION = "1.0.2";
    public static final String NAME =
            "LOTR KOME Addon";
    public static final String DESCRIPTION =
            "Expands The Lord of the Rings Mod with rideable and hireable Mumakil, "
                    + "Mumakil equipment and war formations, customizable siege gates "
                    + "with ownership and access controls, crewed battle rams and siege "
                    + "mechanics, configurable item pickup filtering, Mortal Gandalf, "
                    + "enhanced movement, and additional immersive Middle-earth gameplay.";
    public static SimpleNetworkWrapper network;

    public static CommonProxy proxy;

    public static Item mumakilTusk;
    public static Item mumakilShank;
    public static Item mumakilCookedShank;
    public static Item mumakilHowdah;
    public static Item mumakilCalfSpawnEgg;
    public static Item mumakilHowdahSpawnEgg;

    public void preInit(FMLPreInitializationEvent event) {
        MumakilConfig.load(new File(event.getModConfigurationDirectory(), "lotrmoremobs.cfg"));
        network = NetworkRegistry.INSTANCE.newSimpleChannel("lotrmoremobs");
        network.registerMessage(
                MumakilOpenGuiPacket.Handler.class,
                MumakilOpenGuiPacket.class,
                0,
                Side.SERVER
        );

        network.registerMessage(
                PickupFilterSyncPacket.Handler.class,
                PickupFilterSyncPacket.class,
                1,
                Side.CLIENT
        );
        network.registerMessage(
                PickupFilterTogglePacket.Handler.class,
                PickupFilterTogglePacket.class,
                2,
                Side.SERVER
        );
        network.registerMessage(
                PickupFilterClearPacket.Handler.class,
                PickupFilterClearPacket.class,
                3,
                Side.SERVER
        );
        SiegeNetwork.register();
        network.registerMessage(
                ServerGameplaySyncPacket.Handler.class,
                ServerGameplaySyncPacket.class,
                28,
                Side.CLIENT
        );
    }

    public void init(FMLInitializationEvent event) {
        SiegeRegistry.register();

        LOTREntities.registerCreature(LOTREntityMumakil.class, "Mumakil", 811, 6118481, 12171165);
        LOTREntities.registerCreature(
                LOTREntityMumakilHowdahArcher.class,
                "MumakilHowdahArcher",
                812
        );
        proxy.registerRenderers();
        proxy.registerEventHandlers();

        mumakilTusk = new LOTRItemMumakilTusk();
        GameRegistry.registerItem(mumakilTusk, "mumakil_tusk");

        mumakilShank = new LOTRItemMumakilShank(false);
        GameRegistry.registerItem(mumakilShank, "mumakil_shank");

        mumakilCookedShank = new LOTRItemMumakilShank(true);
        GameRegistry.registerItem(mumakilCookedShank, "cooked_mumakil_shank");

        if (MumakilConfig.enableMumakil) {
            GameRegistry.addSmelting(
                    mumakilShank,
                    new ItemStack(mumakilCookedShank),
                    0.35F
            );
        }

        mumakilHowdah = new LOTRItemMumakilHowdah();
        GameRegistry.registerItem(mumakilHowdah, "mumakil_howdah");

        /*
         * MUMAKIL_CALF_SPAWN_EGG_V1
         * A dedicated custom egg spawns the normal Mumakil entity directly
         * into its wild-baby lifecycle. No duplicate entity ID is needed.
         */
        mumakilCalfSpawnEgg = new LOTRItemMumakilCalfSpawnEgg();
        GameRegistry.registerItem(
                mumakilCalfSpawnEgg,
                "mumakil_calf_spawn_egg"
        );

        mumakilHowdahSpawnEgg =
                new LOTRItemMumakilHowdahSpawnEgg();
        GameRegistry.registerItem(
                mumakilHowdahSpawnEgg,
                "mumakil_howdah_spawn_egg"
        );

        if (MumakilConfig.enableMumakil) {
            MumakilUnitTradeInjector.inject();
            MumakilItemTradeInjector.inject();
        }
        if (MumakilConfig.enableBattleRams) {
            BattleRamUnitTradeInjector.inject();
        }
    }

    public void postInit(FMLPostInitializationEvent event) {
        /*
         * Register after LOTR has populated its faction crafting lists.
         */
        if (MumakilConfig.enableMumakil) {
            MumakilAchievements.register();
            MumakilHowdahRecipeRegistry.register();
            if (MumakilConfig.enableNaturalMumakSpawning) {
                MumakilNaturalSpawnRegistry.register(); // MUMAKIL_NATURAL_SPAWNING_V1
            }
            MumakilWarFormationSpawnRegistry.register();
            MumakilInvasionFormationRegistry.register();
        }
    }
    public void serverStarting(FMLServerStartingEvent event) {
        if (MumakilConfig.enableItemPickupFilter) {
            event.registerServerCommand(new CommandPickupFilter());
        }
        if (MumakilConfig.enableSiegeGates) {
            event.registerServerCommand(new CommandSiegeGateDebug());
        }
    }

    public void serverStopping(FMLServerStoppingEvent event) {
        PickupFilterRequestManager.resetServerState();
        PlayerPickupFilterData.clearAllCaches();
        MumakilOpenGuiPacket.resetServerState();
        SiegeRequestLifecycle.resetServerState();
    }
}
