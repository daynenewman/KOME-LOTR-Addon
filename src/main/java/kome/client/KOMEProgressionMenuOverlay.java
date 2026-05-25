package kome.client;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import lotr.client.gui.LOTRGuiButtonMenu;
import lotr.client.gui.LOTRGuiMenu;
import net.minecraftforge.client.event.GuiScreenEvent;

public class KOMEProgressionMenuOverlay {
    @SubscribeEvent
    public void onInitGui(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!(event.gui instanceof LOTRGuiMenu)) {
            return;
        }
        int x = event.gui.width / 2 - 37;
        int y = event.gui.height / 2 + 47;
        event.buttonList.add(new LOTRGuiButtonMenu((LOTRGuiMenu) event.gui, 2, x, y, kome.client.gui.KOMEGuiProgression.class, "KOME Progression", -1));
        event.buttonList.add(new LOTRGuiButtonMenu((LOTRGuiMenu) event.gui, 3, x + 42, y, kome.client.gui.KOMEGuiServerRecords.class, "KOME Server Records", -1));
    }
}
