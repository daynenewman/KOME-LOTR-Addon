package kome.client;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import kome.client.gui.KOMEGuiButtonMenuTile;
import lotr.client.gui.LOTRGuiMenu;
import net.minecraftforge.client.event.GuiScreenEvent;

public class KOMEProgressionMenuOverlay {
    @SubscribeEvent
    public void onInitGui(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!(event.gui instanceof LOTRGuiMenu)) {
            return;
        }
        int x = event.gui.width / 2 - 79;
        int y = event.gui.height / 2 + 47;
        event.buttonList.add(new KOMEGuiButtonMenuTile((LOTRGuiMenu) event.gui, 2, x, y, kome.client.gui.KOMEGuiProgression.class, "KOME Progression", "progressionimage.png"));
        event.buttonList.add(new KOMEGuiButtonMenuTile((LOTRGuiMenu) event.gui, 3, x + 42, y, kome.client.gui.KOMEGuiServerRecords.class, "KOME Server Records", "serverrecordsimage.png"));
        event.buttonList.add(new KOMEGuiButtonMenuTile((LOTRGuiMenu) event.gui, 4, x + 84, y, kome.client.gui.KOMEGuiAlliance.class, "KOME Alliances", "allianceimage.png"));
        event.buttonList.add(new KOMEGuiButtonMenuTile((LOTRGuiMenu) event.gui, 5, x + 126, y, kome.client.gui.KOMEGuiPopulationLauncher.class, "KOME Population", "populationimage.png"));
    }
}
