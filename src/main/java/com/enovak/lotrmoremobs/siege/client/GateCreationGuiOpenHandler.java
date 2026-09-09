package com.enovak.lotrmoremobs.siege.client;

import com.enovak.lotrmoremobs.siege.client.gui.GuiGateCreation;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;

public class GateCreationGuiOpenHandler {

    private static boolean openRequested;
    private static boolean closeRequested;

    public static void requestOpen() {
        openRequested = true;
        closeRequested = false;
    }

    public static void requestClose() {
        closeRequested = true;
        openRequested = false;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        if (closeRequested) {
            closeRequested = false;
            if (minecraft.currentScreen instanceof GuiGateCreation) {
                minecraft.displayGuiScreen(null);
            }
        }
        if (openRequested) {
            openRequested = false;
            if (minecraft.thePlayer != null
                    && minecraft.theWorld != null
                    && ClientGateCreationState.isActive()) {
                minecraft.displayGuiScreen(new GuiGateCreation());
            }
        }
    }
}
