package com.fuzs.aquaacrobatics.proxy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraftforge.common.MinecraftForge;

import com.enovak.lotrmoremobs.config.PlayerMovementMode;

import com.fuzs.aquaacrobatics.AquaAcrobatics;
import com.fuzs.aquaacrobatics.biome.BiomeWaterFogColors;
import com.fuzs.aquaacrobatics.client.handler.AirMeterHandler;
import com.fuzs.aquaacrobatics.client.handler.FogHandler;
import com.fuzs.aquaacrobatics.client.AquaCameraState;
import com.fuzs.aquaacrobatics.config.ConfigHandler;
import com.fuzs.aquaacrobatics.entity.player.IPlayerResizeable;
import com.fuzs.aquaacrobatics.network.NetworkHandler;
import com.fuzs.aquaacrobatics.network.message.PacketSendKey;
import com.fuzs.aquaacrobatics.optifine.OptifineHelper;
import com.fuzs.aquaacrobatics.util.Keybindings;
import cpw.mods.fml.client.event.ConfigChangedEvent;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.common.gameevent.TickEvent.ClientTickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;

@SuppressWarnings("unused")
public class ClientProxy extends CommonProxy {

    @Override
    public void onPreInit(FMLPreInitializationEvent event) {
        super.onPreInit(event);
        MinecraftForge.EVENT_BUS.register(new AirMeterHandler());
        MinecraftForge.EVENT_BUS.register(new FogHandler());
        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance()
            .bus()
            .register(this);

        // if (ConfigHandler.BlocksConfig.newWaterColors) {
        // Minecraft mc = Minecraft.getMinecraft();
        // WaterResourcePack waterPack = new WaterResourcePack(event.getSourceFile());
        // // Add it to Minecraft's default resource packs list
        // mc.defaultResourcePacks.add(waterPack);
        // OptifineHelper.init();
        // }
    }

    @Override
    public void onInit(FMLInitializationEvent event) {
        Keybindings.register();
    }

    @Override
    public void onMappings() {
        if (OptifineHelper.isOFPresent) {
            OptifineHelper.reloadBlockAliases();
        }
    }

    @SubscribeEvent
    public void onKeyPress(InputEvent.KeyInputEvent event) {
        EntityPlayerSP localPlayer = Minecraft.getMinecraft().thePlayer;
        if (localPlayer != null && !PlayerMovementMode.useModernPlayerMovement(localPlayer)) return;

        if (ConfigHandler.MovementConfig.enableToggleCrawling && Keybindings.forceCrawling.getIsKeyPressed()) {
            //todone apply slowness when forcing crawling
            //todone when jumping, stop forcing crawling
            IPlayerResizeable player = (IPlayerResizeable) Minecraft.getMinecraft().thePlayer;
            if (player != null) {
                if (player.canForceCrawling()) NetworkHandler.INSTANCE
                    .sendToServer(new PacketSendKey(PacketSendKey.KeybindPacket.TOGGLE_CRAWLING));
                else {
                    ((EntityPlayerSP) player)
                        .addChatMessage(new ChatComponentTranslation("chat.aquaacrobatics.cannot_toggle_crawling"));
                }
            }
        }
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent event) {
        if (event.phase == Phase.END) {
            AquaCameraState.INSTANCE.tick(Minecraft.getMinecraft());
        }
    }


    @SubscribeEvent
    public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (!AquaAcrobatics.MODID.equals(event.modID)) return;
        ConfigHandler.reload();
        BiomeWaterFogColors.recomputeColors();
        FogHandler.recomputeBlacklist();
    }

    @Override
    public void onPostInit(FMLPostInitializationEvent event) {

        if (ConfigHandler.BlocksConfig.newWaterColors) {
            // WaterResourcePackInstaller.install(event);
        }
        super.onPostInit(event);
        FogHandler.recomputeBlacklist();
    }

}
