package kome.client.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import java.util.List;

public class KOMEGuiPopulationUnits extends GuiScreen {
    private final String playerName;
    private final List lines;
    private final int armyUsed;
    private final int armyTotal;
    private final int farmhandsUsed;
    private final int farmhandsLimit;
    private int scroll;

    public KOMEGuiPopulationUnits(String playerName, List lines, int armyUsed, int armyTotal, int farmhandsUsed, int farmhandsLimit) {
        this.playerName = playerName;
        this.lines = lines;
        this.armyUsed = armyUsed;
        this.armyTotal = armyTotal;
        this.farmhandsUsed = farmhandsUsed;
        this.farmhandsLimit = farmhandsLimit;
    }

    @Override
    public void initGui() {
        int x = width / 2 - 120;
        int y = height / 2 + 82;
        buttonList.add(new GuiButton(0, x, y, 75, 20, "Back"));
        buttonList.add(new GuiButton(1, x + 82, y, 75, 20, "Up"));
        buttonList.add(new GuiButton(2, x + 164, y, 75, 20, "Down"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            mc.thePlayer.sendChatMessage("/population gui " + playerName);
            mc.thePlayer.closeScreen();
        } else if (button.id == 1) {
            scroll = Math.max(0, scroll - 1);
        } else if (button.id == 2) {
            scroll = Math.min(Math.max(0, lines.size() - getVisibleRows()), scroll + 1);
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = org.lwjgl.input.Mouse.getEventDWheel();
        if (wheel > 0) {
            scroll = Math.max(0, scroll - 1);
        } else if (wheel < 0) {
            scroll = Math.min(Math.max(0, lines.size() - getVisibleRows()), scroll + 1);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int x = width / 2 - 150;
        int y = height / 2 - 100;
        drawCenteredString(fontRendererObj, "Population Units: " + playerName, width / 2, y, 16777215);
        drawString(fontRendererObj, "Army: " + armyUsed + "/" + armyTotal + " used   available " + Math.max(0, armyTotal - armyUsed), x, y + 18, 16777215);
        drawString(fontRendererObj, "Farmhands: " + farmhandsUsed + "/" + farmhandsLimit + " used", x, y + 32, 16777215);

        int listY = y + 52;
        int rows = getVisibleRows();
        if (lines.isEmpty()) {
            drawString(fontRendererObj, "No tracked hired units.", x, listY, 16777215);
        } else {
            for (int i = 0; i < rows && scroll + i < lines.size(); i++) {
                drawString(fontRendererObj, String.valueOf(lines.get(scroll + i)), x, listY + i * 12, 16777215);
            }
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private int getVisibleRows() {
        return 10;
    }
}
