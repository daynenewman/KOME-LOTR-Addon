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
        int x = width / 2 - 38;
        int y = height / 2 + 82;
        buttonList.add(new GuiButton(0, x, y, 75, 20, "Back"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            mc.thePlayer.sendChatMessage("/population gui " + playerName);
            mc.thePlayer.closeScreen();
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

        int listX = x - 4;
        int listY = y + 48;
        int listWidth = 308;
        int listHeight = getVisibleRows() * 12 + 8;
        drawRect(listX, listY - 4, listX + listWidth, listY + listHeight, 0xAA1F1F1F);
        drawRect(listX + 1, listY - 3, listX + listWidth - 1, listY + listHeight - 1, 0x662B3322);

        int rows = getVisibleRows();
        if (lines.isEmpty()) {
            drawString(fontRendererObj, "No tracked hired units.", x, listY, 16777215);
        } else {
            for (int i = 0; i < rows && scroll + i < lines.size(); i++) {
                String line = String.valueOf(lines.get(scroll + i));
                int color = line.startsWith("Army Units") || line.startsWith("Farmhands") ? 0xFFE0A0 : 0xFFFFFF;
                drawString(fontRendererObj, line, x, listY + i * 12, color);
            }
            if (lines.size() > rows) {
                drawString(fontRendererObj, (scroll + 1) + "-" + Math.min(lines.size(), scroll + rows) + "/" + lines.size(), listX + listWidth - 48, listY + listHeight - 12, 0xAAAAAA);
            }
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private int getVisibleRows() {
        return 10;
    }
}
