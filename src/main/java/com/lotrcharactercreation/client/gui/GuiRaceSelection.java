package com.lotrcharactercreation.client.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import com.lotrcharactercreation.network.ModNetwork;
import com.lotrcharactercreation.race.PlayerRace;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GuiRaceSelection extends GuiScreen {

    private boolean selectionPending;

    @Override
    public void initGui() {
        buttonList.clear();

        int startY = height / 2 - 28;
        PlayerRace[] races = PlayerRace.values();
        for (int index = 0; index < races.length; index++) {
            int column = index % 2;
            int row = index / 2;
            int x = width / 2 - 155 + column * 160;
            int y = startY + row * 24;
            buttonList.add(new GuiButton(index, x, y, 150, 20, races[index].getDisplayName()));
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (selectionPending || !button.enabled || button.id < 0 || button.id >= PlayerRace.values().length) {
            return;
        }

        selectionPending = true;
        for (Object entry : buttonList) {
            ((GuiButton) entry).enabled = false;
        }

        ModNetwork.sendRaceSelection(PlayerRace.values()[button.id]);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode != 1) {
            super.keyTyped(typedChar, keyCode);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "LOTR Character Creation", width / 2, height / 2 - 80, 0xFFFFFF);
        drawCenteredString(fontRendererObj, "Choose Your Race", width / 2, height / 2 - 58, 0xA0A0A0);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
