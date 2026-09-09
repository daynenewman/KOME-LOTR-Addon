package com.enovak.lotrmoremobs.client;

import com.enovak.lotrmoremobs.client.gui.LOTRGuiUnitTradePledgeNavigation;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import lotr.client.gui.LOTRGuiUnitTrade;
import lotr.common.entity.npc.LOTRUnitTradeable;
import lotr.common.inventory.LOTRContainerUnitTrade;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.GuiOpenEvent;

/**
 * Replaces only LOTR's native unit-trade screen with an addon subclass that
 * owns a real pledge-navigation button, then draws that screen's deferred
 * requirement tooltip in the final Post phase.
 */
public final class UnitTradePledgeNavigationHandler {
    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if (!(event.gui instanceof LOTRGuiUnitTrade)
                || event.gui
                instanceof LOTRGuiUnitTradePledgeNavigation) {
            return;
        }

        LOTRGuiUnitTrade original =
                (LOTRGuiUnitTrade)event.gui;
        if (!(original.inventorySlots
                instanceof LOTRContainerUnitTrade)) {
            return;
        }
        LOTRContainerUnitTrade container =
                (LOTRContainerUnitTrade)original.inventorySlots;
        if (!(container.theUnitTrader
                instanceof LOTRUnitTradeable)) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null
                || minecraft.thePlayer == null
                || minecraft.theWorld == null) {
            return;
        }

        event.gui = new LOTRGuiUnitTradePledgeNavigation(
                minecraft.thePlayer,
                (LOTRUnitTradeable)container.theUnitTrader,
                minecraft.theWorld
        );
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDrawScreenPost(
            GuiScreenEvent.DrawScreenEvent.Post event
    ) {
        if (event.gui == null
                || event.gui.getClass()
                != LOTRGuiUnitTradePledgeNavigation.class) {
            return;
        }

        ((LOTRGuiUnitTradePledgeNavigation)event.gui)
                .drawLateRequirementTooltip(
                        event.mouseX,
                        event.mouseY
                );
    }
}
