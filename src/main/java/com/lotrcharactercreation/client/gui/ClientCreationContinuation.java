package com.lotrcharactercreation.client.gui;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiScreen;

/** Keeps the latest server-authorized screen while normal pause/options screens are open. */
public final class ClientCreationContinuation {
    public static final ClientCreationContinuation INSTANCE = new ClientCreationContinuation();
    private GuiScreen required;

    private ClientCreationContinuation() {}

    public void require(GuiScreen screen) {
        required = screen;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null && (mc.currentScreen == null || isCreation(mc.currentScreen))) {
            mc.displayGuiScreen(screen);
        }
    }

    public void complete() { required = null; }

    public static void pause(GuiScreen screen) {
        INSTANCE.required = screen;
        Minecraft.getMinecraft().displayGuiScreen(new GuiIngameMenu());
    }

    static boolean isCreation(GuiScreen screen) {
        return screen instanceof GuiRaceSelection || screen instanceof GuiSexSelection
            || screen instanceof GuiStartingFactionSelection || screen instanceof GuiAppearanceSelection
            || screen instanceof GuiCharacterConfirmation;
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.phase == TickEvent.Phase.END && required != null && mc.theWorld != null
                && mc.thePlayer != null && mc.currentScreen == null) mc.displayGuiScreen(required);
    }

    @SubscribeEvent
    public void disconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) { complete(); }
}
