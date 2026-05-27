package kome.client.gui;

import kome.client.KOMEMinecraftClient;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import java.util.ArrayList;
import java.util.List;

public class KOMEGuiPopulationUnits extends GuiScreen {
    private final String playerName;
    private final List lines;
    private final List offensiveLines = new ArrayList();
    private final List defensiveLines = new ArrayList();
    private final List farmhandLines = new ArrayList();
    private final List offensiveUnits = new ArrayList();
    private final List defensiveUnits = new ArrayList();
    private final List farmhandUnits = new ArrayList();
    private final int armyUsed;
    private final int armyTotal;
    private final int farmhandsUsed;
    private final int farmhandsLimit;
    private int scroll;
    private int selectedTab;

    public KOMEGuiPopulationUnits(String playerName, List lines, int armyUsed, int armyTotal, int farmhandsUsed, int farmhandsLimit) {
        this.playerName = playerName;
        this.lines = lines;
        this.armyUsed = armyUsed;
        this.armyTotal = armyTotal;
        this.farmhandsUsed = farmhandsUsed;
        this.farmhandsLimit = farmhandsLimit;
        splitLinesByTab();
    }

    @Override
    public void initGui() {
        int x = width / 2 - 150;
        int y = height / 2 - 100;
        buttonList.add(new GuiButton(1, x, y + 48, 98, 20, "Offensive"));
        buttonList.add(new GuiButton(2, x + 103, y + 48, 98, 20, "Defensive"));
        buttonList.add(new GuiButton(3, x + 206, y + 48, 98, 20, "Farmhands"));
        buttonList.add(new GuiButton(0, width / 2 - 38, height / 2 + 92, 75, 20, "Back"));
        updateTabButtons();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            KOMEMinecraftClient.sendChat("/population gui " + playerName);
            KOMEMinecraftClient.closePlayerScreen();
        } else if (button.id >= 1 && button.id <= 3) {
            selectedTab = button.id - 1;
            scroll = 0;
            updateTabButtons();
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = org.lwjgl.input.Mouse.getEventDWheel();
        List visibleUnits = getSelectedUnits();
        List visibleLines = visibleUnits.isEmpty() ? getSelectedLines() : visibleUnits;
        if (wheel > 0) {
            scroll = Math.max(0, scroll - 1);
        } else if (wheel < 0) {
            scroll = Math.min(Math.max(0, visibleLines.size() - getVisibleRows()), scroll + 1);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int x = width / 2 - 150;
        int y = height / 2 - 100;
        drawCenteredString(fontRendererObj, "Population Units: " + playerName, width / 2, y, 16777215);
        drawString(fontRendererObj, "Army total: " + armyUsed + "/" + armyTotal + " used, " + Math.max(0, armyTotal - armyUsed) + " available", x, y + 18, 16777215);
        drawString(fontRendererObj, "Farmhands: " + farmhandsUsed + "/" + farmhandsLimit + " used", x, y + 32, 16777215);

        int listX = x - 4;
        int listY = y + 78;
        int listWidth = 308;
        int listHeight = getVisibleRows() * 12 + 8;
        drawRect(listX, listY - 4, listX + listWidth, listY + listHeight, 0xAA1F1F1F);
        drawRect(listX + 1, listY - 3, listX + listWidth - 1, listY + listHeight - 1, 0x662B3322);

        int rows = getVisibleRows();
        List visibleUnits = getSelectedUnits();
        List visibleLines = getSelectedLines();
        if (visibleUnits.isEmpty() && visibleLines.isEmpty()) {
            drawString(fontRendererObj, getEmptyMessage(), x, listY, 16777215);
        } else if (!visibleUnits.isEmpty()) {
            drawString(fontRendererObj, "Name", x, listY - 14, 0xAAAAAA);
            drawString(fontRendererObj, "Type", x + 126, listY - 14, 0xAAAAAA);
            drawString(fontRendererObj, "Pop", x + 178, listY - 14, 0xAAAAAA);
            drawString(fontRendererObj, "Cap", x + 216, listY - 14, 0xAAAAAA);
            for (int i = 0; i < rows && scroll + i < visibleUnits.size(); i++) {
                UnitLine unit = (UnitLine) visibleUnits.get(scroll + i);
                int rowY = listY + i * 12;
                int color = getTabColor();
                drawString(fontRendererObj, trim(unit.name, 118), x, rowY, color);
                drawString(fontRendererObj, unit.type, x + 126, rowY, 0xFFFFFF);
                drawString(fontRendererObj, unit.cost, x + 178, rowY, 0xFFFFFF);
                drawString(fontRendererObj, unit.cap > 0 ? String.valueOf(unit.cap) : "-", x + 216, rowY, unit.cap > 0 ? 0x55FF55 : 0xAAAAAA);
            }
            if (visibleUnits.size() > rows) {
                drawString(fontRendererObj, (scroll + 1) + "-" + Math.min(visibleUnits.size(), scroll + rows) + "/" + visibleUnits.size(), listX + listWidth - 48, listY + listHeight - 12, 0xAAAAAA);
            }
        } else {
            for (int i = 0; i < rows && scroll + i < visibleLines.size(); i++) {
                String line = String.valueOf(visibleLines.get(scroll + i));
                int color = getTabColor();
                drawString(fontRendererObj, line, x, listY + i * 12, color);
            }
            if (visibleLines.size() > rows) {
                drawString(fontRendererObj, (scroll + 1) + "-" + Math.min(visibleLines.size(), scroll + rows) + "/" + visibleLines.size(), listX + listWidth - 48, listY + listHeight - 12, 0xAAAAAA);
            }
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private int getVisibleRows() {
        return 9;
    }

    private void splitLinesByTab() {
        List current = null;
        List currentUnits = null;
        for (Object object : lines) {
            String line = String.valueOf(object);
            if (line.startsWith("Offensive Units")) {
                current = offensiveLines;
                currentUnits = offensiveUnits;
            } else if (line.startsWith("Defensive Units")) {
                current = defensiveLines;
                currentUnits = defensiveUnits;
            } else if (line.startsWith("Farmhands")) {
                current = farmhandLines;
                currentUnits = farmhandUnits;
            } else if (current != null && line.trim().length() > 0) {
                UnitLine unit = UnitLine.parse(line);
                if (unit == null) {
                    current.add(line);
                } else if (currentUnits != null) {
                    currentUnits.add(unit);
                }
            }
        }
    }

    private List getSelectedLines() {
        if (selectedTab == 1) {
            return defensiveLines;
        }
        if (selectedTab == 2) {
            return farmhandLines;
        }
        return offensiveLines;
    }

    private List getSelectedUnits() {
        if (selectedTab == 1) {
            return defensiveUnits;
        }
        if (selectedTab == 2) {
            return farmhandUnits;
        }
        return offensiveUnits;
    }

    private String getEmptyMessage() {
        if (selectedTab == 1) {
            return "No defensive units.";
        }
        if (selectedTab == 2) {
            return "No farmhands.";
        }
        return "No offensive units.";
    }

    private int getTabColor() {
        if (selectedTab == 1) {
            return 0x9ED6FF;
        }
        if (selectedTab == 2) {
            return 0xD7FF9E;
        }
        return 0xF5D27A;
    }

    private void updateTabButtons() {
        for (Object object : buttonList) {
            GuiButton button = (GuiButton) object;
            if (button.id == 1) {
                button.displayString = "Offensive (" + getTabCount(offensiveUnits, offensiveLines) + ")";
                button.enabled = selectedTab != 0;
            } else if (button.id == 2) {
                button.displayString = "Defensive (" + getTabCount(defensiveUnits, defensiveLines) + ")";
                button.enabled = selectedTab != 1;
            } else if (button.id == 3) {
                button.displayString = "Farmhands (" + getTabCount(farmhandUnits, farmhandLines) + ")";
                button.enabled = selectedTab != 2;
            }
        }
    }

    private int getTabCount(List units, List fallbackLines) {
        return units.isEmpty() ? fallbackLines.size() : units.size();
    }

    private String trim(String value, int width) {
        if (value == null) {
            return "";
        }
        if (fontRendererObj.getStringWidth(value) <= width) {
            return value;
        }
        while (value.length() > 0 && fontRendererObj.getStringWidth(value + "...") > width) {
            value = value.substring(0, value.length() - 1);
        }
        return value + "...";
    }

    private static class UnitLine {
        private final String uuid;
        private final String name;
        private final String type;
        private final String cost;
        private int cap;

        private UnitLine(String uuid, String name, String type, String cost, int cap) {
            this.uuid = uuid;
            this.name = name;
            this.type = type;
            this.cost = cost;
            this.cap = cap;
        }

        private static UnitLine parse(String line) {
            String[] parts = line.split("\t", -1);
            if (parts.length < 7 || !"UNITCAP".equals(parts[0])) {
                return null;
            }
            int parsedCap = 0;
            try {
                parsedCap = Integer.parseInt(parts[6]);
            } catch (NumberFormatException ignored) {
            }
            String cost = "farmhand".equals(parts[3]) ? "-" : parts[4];
            String type = "farmhand".equals(parts[3]) ? "Farm" : ("true".equals(parts[5]) ? parts[3] + "*" : parts[3]);
            return new UnitLine(parts[1], parts[2], type, cost, parsedCap);
        }
    }

}
