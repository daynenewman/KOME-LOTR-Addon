package com.enovak.lotrmoremobs.client.pickupfilter;

import com.enovak.lotrmoremobs.client.gui.GuiPickupFilter;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;

/**
 * Opens the pickup-filter GUI on the next client tick.
 *
 * This avoids GuiChat immediately closing a GUI that was opened
 * directly while a client command was being processed.
 */
public class PickupFilterGuiOpenHandler {

    private static boolean openRequested = false;
    private static boolean returnToCreativeInventory = false;

    public static void requestOpen() {
        requestOpen(false);
    }

    public static void requestOpen(boolean openedFromCreativeInventory) {
        openRequested = true;
        returnToCreativeInventory = openedFromCreativeInventory;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || !openRequested) {
            return;
        }

        openRequested = false;
        boolean shouldReturnToCreative = returnToCreativeInventory;
        returnToCreativeInventory = false;

        Minecraft mc = Minecraft.getMinecraft();

        if (mc.thePlayer != null
                && mc.theWorld != null) {
            mc.displayGuiScreen(
                    new GuiPickupFilter(shouldReturnToCreative)
            );
        }
    }
}
