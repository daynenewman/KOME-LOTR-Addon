package kome.client.gui;

import kome.client.KOMEMinecraftClient;
import lotr.client.gui.LOTRGuiMenuBase;

public class KOMEGuiPopulationLauncher extends LOTRGuiMenuBase {
    @Override
    public void initGui() {
        super.initGui();
        if (KOMEMinecraftClient.player() != null) {
            KOMEMinecraftClient.sendChat("/population gui " + KOMEMinecraftClient.playerName());
        }
        mc.displayGuiScreen(null);
    }
}
