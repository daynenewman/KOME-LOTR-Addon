package kome.client.gui;

import kome.client.KOMEConquestMapOverlay;
import kome.client.KOMEMinecraftClient;
import kome.common.network.KOMEPacketConquestOpenCapture;
import kome.common.network.KOMEPacketHandler;
import kome.common.network.KOMEPacketPopulationGui;
import lotr.client.gui.LOTRGuiMenu;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.List;

public class KOMEGuiPopulation extends GuiScreen {
    private static final int PANEL_WIDTH = 760;
    private static final int PANEL_HEIGHT = 520;
    private static final int TAB_OVERVIEW = 0;
    private static final int TAB_PLAYERS = 1;
    private static final int TAB_TILES = 2;
    private static final int TAB_ORDERS = 3;
    private static final int COLOR_OFF_USED = 0xFF8E2F2F;
    private static final int COLOR_OFF_AVAILABLE = 0xFFD6A04A;
    private static final int COLOR_DEF_USED = 0xFF2F5F8F;
    private static final int COLOR_DEF_AVAILABLE = 0xFF8FB8CC;

    private final KOMEPacketPopulationGui data;
    private GuiTextField playerField;
    private GuiTextField amountField;
    private GuiTextField tileField;
    private int activeTab = TAB_OVERVIEW;
    private int playerScroll;
    private int tileScroll;

    public KOMEGuiPopulation(KOMEPacketPopulationGui message) {
        data = message == null ? new KOMEPacketPopulationGui() : message;
        data.sanitizeTopLevel();
    }

    public KOMEGuiPopulation(String playerName, int offensiveTotal, int offensiveUsed, int defensiveTotal, int defensiveUsed,
            int farmhandsUsed, int farmhandsLimit, int armyUsed, int armyTotal, int tileOffensiveTotal,
            int tileOffensiveUsed, int tileDefensiveTotal, int tileDefensiveUsed, int controlledTiles,
            int allocatedOffensive, int allocatedOffensiveUsed, int allocatedDefensive, int allocatedDefensiveUsed,
            String allocationSummary, boolean canManageAllocations) {
        this(new KOMEPacketPopulationGui(playerName, offensiveTotal, offensiveUsed, defensiveTotal, defensiveUsed,
            farmhandsUsed, farmhandsLimit, armyUsed, armyTotal, tileOffensiveTotal, tileOffensiveUsed,
            tileDefensiveTotal, tileDefensiveUsed, controlledTiles, allocatedOffensive, allocatedOffensiveUsed,
            allocatedDefensive, allocatedDefensiveUsed, allocationSummary, canManageAllocations));
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int x = getPanelX();
        int y = getPanelY();
        int w = getPanelWidth();

        buttonList.add(KOMEGuiButton.small(0, x + 14, y + 14, "Menu"));
        buttonList.add(new KOMEGuiButton(8, x + w - 212, y + 14, 90, 22, "Tiles"));
        buttonList.add(new KOMEGuiButton(7, x + w - 114, y + 14, 90, 22, "Units"));

        int tabY = y + 46;
        int tabX = x + 22;
        addTabButton(20, tabX, tabY, "Overview", TAB_OVERVIEW);
        addTabButton(21, tabX + 96, tabY, "Players", TAB_PLAYERS);
        addTabButton(22, tabX + 192, tabY, "Tiles", TAB_TILES);

        int formY = contentY() + 34;
        int ordersFieldY = formY + 35;
        playerField = new GuiTextField(fontRendererObj, x + 154, ordersFieldY, 166, 18);
        playerField.setText(data.playerName);
        amountField = new GuiTextField(fontRendererObj, x + 404, ordersFieldY, 58, 18);
        amountField.setText("25");
        tileField = new GuiTextField(fontRendererObj, x + 154, formY + 151, 80, 18);
        tileField.setText("");

        if (false && activeTab == TAB_ORDERS && data.canManageAllocations) {
            int buttonY = formY + 94;
            buttonList.add(new KOMEGuiButton(2, x + 154, buttonY, 104, 22, "+ Offensive"));
            buttonList.add(new KOMEGuiButton(3, x + 266, buttonY, 104, 22, "- Offensive"));
            buttonList.add(new KOMEGuiButton(5, x + 378, buttonY, 104, 22, "+ Defensive"));
            buttonList.add(new KOMEGuiButton(6, x + 490, buttonY, 104, 22, "- Defensive"));
            int allocationY = formY + 184;
            buttonList.add(new KOMEGuiButton(30, x + 154, allocationY, 132, 22, "+ Offensive Allocation"));
            buttonList.add(new KOMEGuiButton(31, x + 294, allocationY, 132, 22, "- Offensive Allocation"));
            buttonList.add(new KOMEGuiButton(32, x + 434, allocationY, 132, 22, "+ Defensive Allocation"));
            buttonList.add(new KOMEGuiButton(33, x + 574, allocationY, 132, 22, "- Defensive Allocation"));
        }
    }

    private void addTabButton(int id, int x, int y, String label, int tab) {
        buttonList.add(new KOMEGuiButton(id, x, y, 88, 22, label, activeTab == tab).setSelected(activeTab == tab));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            mc.displayGuiScreen(new LOTRGuiMenu());
        } else if (button.id == 7) {
            KOMEMinecraftClient.sendChat("/population units " + safePlayer());
            KOMEMinecraftClient.closePlayerScreen();
        } else if (button.id == 8) {
            KOMEConquestMapOverlay.openPreservedMap();
        } else if (button.id >= 20 && button.id <= 23) {
            activeTab = button.id - 20;
            initGui();
        } else if (button.id == 2) {
            KOMEMinecraftClient.sendChat("/population add " + safePlayer() + " offensive " + safeAmount());
        } else if (button.id == 3) {
            KOMEMinecraftClient.sendChat("/population remove " + safePlayer() + " offensive " + safeAmount());
        } else if (button.id == 5) {
            KOMEMinecraftClient.sendChat("/population add " + safePlayer() + " defensive " + safeAmount());
        } else if (button.id == 6) {
            KOMEMinecraftClient.sendChat("/population remove " + safePlayer() + " defensive " + safeAmount());
        } else if (button.id == 30) {
            sendAllocationCommand("allocate", "offensive");
        } else if (button.id == 31) {
            sendAllocationCommand("unallocate", "offensive");
        } else if (button.id == 32) {
            sendAllocationCommand("allocate", "defensive");
        } else if (button.id == 33) {
            sendAllocationCommand("unallocate", "defensive");
        }
    }

    private void sendAllocationCommand(String action, String type) {
        String tile = tileField == null ? "" : tileField.getText().trim();
        if (tile.length() == 0) {
            return;
        }
        KOMEMinecraftClient.sendChat("/population " + action + " " + tile + " " + safePlayer() + " " + type + " " + safeAmount());
    }

    @Override
    protected void keyTyped(char c, int key) {
        if (activeTab == TAB_ORDERS && data.canManageAllocations) {
            if (playerField.textboxKeyTyped(c, key) || amountField.textboxKeyTyped(c, key) || tileField.textboxKeyTyped(c, key)) {
                return;
            }
        }
        super.keyTyped(c, key);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (activeTab == TAB_PLAYERS && handlePlayerRowClick(mouseX, mouseY)) {
            return;
        }
        if (activeTab == TAB_TILES && handleTileRowClick(mouseX, mouseY)) {
            return;
        }
        super.mouseClicked(mouseX, mouseY, button);
        if (activeTab == TAB_ORDERS && data.canManageAllocations) {
            playerField.mouseClicked(mouseX, mouseY, button);
            amountField.mouseClicked(mouseX, mouseY, button);
            tileField.mouseClicked(mouseX, mouseY, button);
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }
        if (activeTab == TAB_PLAYERS) {
            playerScroll = clamp(playerScroll + (wheel < 0 ? 1 : -1), 0, maxPlayerScroll());
        } else if (activeTab == TAB_TILES) {
            tileScroll = clamp(tileScroll + (wheel < 0 ? 1 : -1), 0, maxTileScroll());
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int x = getPanelX();
        int y = getPanelY();
        int w = getPanelWidth();
        int h = getPanelHeight();
        KOMEGuiTheme.drawMainPanel(x, y, w, h);
        KOMEGuiTheme.drawHeader(fontRendererObj, "Population", x + 246, y + 12, w - 492);
        drawFactionLine(x, y);
        if (activeTab == TAB_OVERVIEW) {
            drawOverviewTab(x, y, w);
        } else if (activeTab == TAB_PLAYERS) {
            drawPlayersTab(x, y, w, h, mouseX, mouseY);
        } else if (activeTab == TAB_TILES) {
            drawTilesTab(x, y, w, h, mouseX, mouseY);
        } else {
            drawOverviewTab(x, y, w);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawFactionLine(int x, int y) {
        String faction = data.viewerFaction == null || data.viewerFaction.length() == 0 ? "No faction" : data.viewerFaction;
        fontRendererObj.drawString("Faction: " + faction, x + 414, y + 52, KOMEGuiTheme.COLOR_TEXT_MUTED);
    }

    private void drawOverviewTab(int x, int y, int w) {
        int cy = contentY();
        if (isNoFaction()) {
            drawEmptyState(x + 24, cy, w - 48, "No faction population data found.");
            return;
        }
        KOMEGuiTheme.drawSubPanel(x + 24, cy, w - 48, 92);
        fontRendererObj.drawString("Canonical Faction Population", x + 36, cy + 12, KOMEGuiTheme.COLOR_BORDER_RED);
        fontRendererObj.drawString("Available Population: " + data.availablePopulation, x + 36, cy + 34, KOMEGuiTheme.COLOR_TEXT);
        fontRendererObj.drawString("Active Population: " + data.activePopulation, x + 36, cy + 52, KOMEGuiTheme.COLOR_TEXT);
        fontRendererObj.drawString("Daily Population Rate: " + new kome.common.data.KOMEPopulationRate(data.dailyPopulationRateUnits).formatPerDay(), x + 36, cy + 70, KOMEGuiTheme.COLOR_TEXT);
    }

    private void drawCapacityCard(int x, int y, int width, int height, String title, int offUsed, int offTotal, int offAvail, int defUsed, int defTotal, int defAvail, boolean farmhands, int farmUsed, int farmTotal) {
        KOMEGuiTheme.drawSubPanel(x, y, width, height);
        int used = clamp(offUsed, 0, offTotal) + clamp(defUsed, 0, defTotal);
        int total = Math.max(0, offTotal) + Math.max(0, defTotal);
        int available = Math.max(0, offAvail) + Math.max(0, defAvail);
        fontRendererObj.drawString(title, x + 12, y + 8, KOMEGuiTheme.COLOR_BORDER_RED);
        fontRendererObj.drawString("Used " + used + " / " + total + "     Available " + available, x + 248, y + 8, KOMEGuiTheme.COLOR_TEXT);
        if (farmhands) {
            fontRendererObj.drawString("Farmhands " + farmUsed + " / " + farmTotal, x + width - 156, y + 8, KOMEGuiTheme.COLOR_TEXT_MUTED);
        }
        fontRendererObj.drawString("Offensive: " + offUsed + " / " + offTotal + "     available " + offAvail, x + 12, y + 27, KOMEGuiTheme.COLOR_TEXT);
        fontRendererObj.drawString("Defensive: " + defUsed + " / " + defTotal + "     available " + defAvail, x + 12, y + 41, KOMEGuiTheme.COLOR_TEXT);
        drawStackedCapacityBar(x + 12, y + 62, width - 24, 10, offUsed, offAvail, defUsed, defAvail);
        drawLegend(x + 12, y + 76);
    }

    private void drawSourceCard(int x, int y, int width, String title, int offTotal, int offUsed, int offAvail, int defTotal, int defUsed, int defAvail) {
        KOMEGuiTheme.drawSubPanel(x, y, width, 76);
        fontRendererObj.drawString(title, x + 12, y + 8, KOMEGuiTheme.COLOR_BORDER_RED);
        fontRendererObj.drawString("Offensive: total " + offTotal + " / used " + offUsed + " / available " + offAvail, x + 12, y + 28, KOMEGuiTheme.COLOR_TEXT);
        fontRendererObj.drawString("Defensive: total " + defTotal + " / used " + defUsed + " / available " + defAvail, x + 12, y + 44, KOMEGuiTheme.COLOR_TEXT);
    }

    private void drawPlayersTab(int x, int y, int w, int h, int mouseX, int mouseY) {
        int cy = contentY();
        fontRendererObj.drawString("Faction Player Breakdown", x + 24, cy, KOMEGuiTheme.COLOR_BORDER_RED);
        List rows = getPlayerRowsWithUnallocated();
        if (rows.isEmpty()) {
            drawEmptyState(x + 24, cy + 18, w - 48, "No faction player population data found.");
            return;
        }
        int listY = cy + 18;
        int listH = y + h - listY - 18;
        playerScroll = clamp(playerScroll, 0, maxPlayerScroll());
        KOMEGuiTheme.enableScissor(mc, x + 18, listY, w - 36, listH);
        for (int i = 0; i < visiblePlayerRows() + 1 && playerScroll + i < rows.size(); i++) {
            drawPlayerRow(x + 24, listY + i * playerRowHeight(), w - 48, (KOMEPacketPopulationGui.CapacityBreakdown) rows.get(playerScroll + i), mouseX, mouseY);
        }
        KOMEGuiTheme.disableScissor();
        drawScrollHint(x + w - 18, listY, listH, rows.size(), visiblePlayerRows(), playerScroll);
    }

    private void drawPlayerRow(int x, int y, int width, KOMEPacketPopulationGui.CapacityBreakdown row, int mouseX, int mouseY) {
        row.sanitize();
        KOMEGuiTheme.drawCard(x, y, width, playerRowHeight() - 6, KOMEGuiTheme.isHovered(mouseX, mouseY, x, y, width, playerRowHeight() - 6));
        int total = row.offensiveTotal + row.defensiveTotal;
        int used = row.offensiveUsed + row.defensiveUsed;
        int available = row.offensiveAvailable + row.defensiveAvailable;
        String name = row.unallocated ? "Unallocated" : row.playerName;
        fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, name, 142), x + 10, y + 7, row.unallocated ? KOMEGuiTheme.COLOR_TEXT_MUTED : KOMEGuiTheme.COLOR_BORDER_RED);
        if (row.unallocated) {
            fontRendererObj.drawString("Total: " + available + " / " + total, x + 164, y + 7, KOMEGuiTheme.COLOR_TEXT);
            fontRendererObj.drawString("Offensive: " + row.offensiveAvailable + " / " + row.offensiveTotal, x + 164, y + 22, KOMEGuiTheme.COLOR_TEXT);
            fontRendererObj.drawString("Defensive: " + row.defensiveAvailable + " / " + row.defensiveTotal, x + 164, y + 36, KOMEGuiTheme.COLOR_TEXT);
        } else {
            fontRendererObj.drawString("Total: " + used + " / " + total + "     available " + available, x + 164, y + 7, KOMEGuiTheme.COLOR_TEXT);
            fontRendererObj.drawString("Offensive: " + row.offensiveUsed + " / " + row.offensiveTotal + "     available " + row.offensiveAvailable, x + 164, y + 22, KOMEGuiTheme.COLOR_TEXT);
            fontRendererObj.drawString("Defensive: " + row.defensiveUsed + " / " + row.defensiveTotal + "     available " + row.defensiveAvailable, x + 164, y + 36, KOMEGuiTheme.COLOR_TEXT);
        }
        drawStackedCapacityBar(x + 164, y + 53, 266, 8, row.offensiveUsed, row.offensiveAvailable, row.defensiveUsed, row.defensiveAvailable);
        if (!row.unallocated) {
            drawMiniButton(x + width - 202, y + 47, 44, "View", mouseX, mouseY);
            if (data.canManageAllocations && row.canManage) {
                drawMiniButton(x + width - 152, y + 47, 62, "Allocate", mouseX, mouseY);
                drawMiniButton(x + width - 84, y + 47, 58, "Reclaim", mouseX, mouseY);
            }
        }
    }

    private void drawTilesTab(int x, int y, int w, int h, int mouseX, int mouseY) {
        int cy = contentY();
        fontRendererObj.drawString("Faction-Controlled Tile Population", x + 24, cy, KOMEGuiTheme.COLOR_BORDER_RED);
        if (data.tileBreakdowns == null || data.tileBreakdowns.isEmpty()) {
            drawEmptyState(x + 24, cy + 18, w - 48, "No controlled tiles found.");
            return;
        }
        int listY = cy + 18;
        int listH = y + h - listY - 18;
        tileScroll = clamp(tileScroll, 0, maxTileScroll());
        KOMEGuiTheme.enableScissor(mc, x + 18, listY, w - 36, listH);
        for (int i = 0; i < visibleTileRows() + 1 && tileScroll + i < data.tileBreakdowns.size(); i++) {
            Object object = data.tileBreakdowns.get(tileScroll + i);
            if (object instanceof KOMEPacketPopulationGui.TileBreakdown) {
                drawTileRow(x + 24, listY + i * tileRowHeight(), w - 48, (KOMEPacketPopulationGui.TileBreakdown) object, mouseX, mouseY);
            }
        }
        KOMEGuiTheme.disableScissor();
        drawScrollHint(x + w - 18, listY, listH, data.tileBreakdowns.size(), visibleTileRows(), tileScroll);
    }

    private void drawTileRow(int x, int y, int width, KOMEPacketPopulationGui.TileBreakdown row, int mouseX, int mouseY) {
        row.sanitize();
        KOMEGuiTheme.drawCard(x, y, width, tileRowHeight() - 6, KOMEGuiTheme.isHovered(mouseX, mouseY, x, y, width, tileRowHeight() - 6));
        String title = tileTitle(row);
        fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, title, 260), x + 10, y + 7, KOMEGuiTheme.COLOR_BORDER_RED);
        fontRendererObj.drawString("Ruler: " + row.ownerFaction, x + 10, y + 22, KOMEGuiTheme.COLOR_TEXT_MUTED);
        fontRendererObj.drawString("Offensive: total " + row.offensiveTotal + "     allocated " + row.offensiveAllocated + "     unallocated " + row.offensiveUnallocated, x + 162, y + 8, KOMEGuiTheme.COLOR_TEXT);
        fontRendererObj.drawString("Defensive: total " + row.defensiveTotal + "     allocated " + row.defensiveAllocated + "     unallocated " + row.defensiveUnallocated, x + 162, y + 23, KOMEGuiTheme.COLOR_TEXT);
        fontRendererObj.drawString("Farmhands: " + row.farmhandUsed + " / " + row.farmhandTotal, x + 162, y + 38, KOMEGuiTheme.COLOR_TEXT_MUTED);
        drawMiniButton(x + width - 74, y + 18, 58, "Manage", mouseX, mouseY);
    }

    private String tileTitle(KOMEPacketPopulationGui.TileBreakdown row) {
        String tile = row == null || row.tileId == null ? "" : row.tileId.trim();
        String display = row == null || row.tileDisplayName == null ? "" : row.tileDisplayName.trim();
        if (display.length() == 0) {
            return "Tile " + tile;
        }
        if (tile.length() > 0 && !display.equalsIgnoreCase(tile)) {
            return display + " (" + tile + ")";
        }
        return display;
    }

    private void drawOrdersTab(int x, int y, int w) {
        int cy = contentY();
        fontRendererObj.drawString("Population Orders", x + 24, cy, KOMEGuiTheme.COLOR_BORDER_RED);
        if (!data.canManageAllocations) {
            drawEmptyState(x + 24, cy + 24, w - 48, "You do not have permission to issue population orders.");
            return;
        }
        int formY = cy + 34;
        KOMEGuiTheme.drawSubPanel(x + 24, formY, w - 48, 236);
        fontRendererObj.drawString("Target Player:", x + 42, formY + 18, KOMEGuiTheme.COLOR_TEXT_MUTED);
        fontRendererObj.drawString("Amount:", x + 334, formY + 18, KOMEGuiTheme.COLOR_TEXT_MUTED);
        playerField.drawTextBox();
        amountField.drawTextBox();
        fontRendererObj.drawString("Player Reserve", x + 42, formY + 64, KOMEGuiTheme.COLOR_BORDER_RED);
        fontRendererObj.drawString("Adjust the selected player's personal reserve population.", x + 42, formY + 80, KOMEGuiTheme.COLOR_TEXT_MUTED);
        fontRendererObj.drawString("Tile Allocation", x + 42, formY + 134, KOMEGuiTheme.COLOR_BORDER_RED);
        fontRendererObj.drawString("Tile:", x + 42, formY + 156, KOMEGuiTheme.COLOR_TEXT_MUTED);
        tileField.drawTextBox();
        fontRendererObj.drawString("Assign or reclaim faction tile population for the target player.", x + 250, formY + 156, KOMEGuiTheme.COLOR_TEXT_MUTED);
    }

    private boolean handlePlayerRowClick(int mouseX, int mouseY) {
        List rows = getPlayerRowsWithUnallocated();
        int x = getPanelX() + 24;
        int y = contentY() + 18;
        int width = getPanelWidth() - 48;
        for (int i = 0; i < visiblePlayerRows() + 1 && playerScroll + i < rows.size(); i++) {
            KOMEPacketPopulationGui.CapacityBreakdown row = (KOMEPacketPopulationGui.CapacityBreakdown) rows.get(playerScroll + i);
            int rowY = y + i * playerRowHeight();
            if (row.unallocated) {
                continue;
            }
            if (KOMEGuiTheme.isHovered(mouseX, mouseY, x + width - 202, rowY + 47, 44, 16)) {
                KOMEMinecraftClient.sendChat("/population units " + row.playerName);
                KOMEMinecraftClient.closePlayerScreen();
                return true;
            }
            if (data.canManageAllocations && row.canManage && KOMEGuiTheme.isHovered(mouseX, mouseY, x + width - 152, rowY + 47, 62, 16)) {
                openOrdersFor(row.playerName);
                return true;
            }
            if (data.canManageAllocations && row.canManage && KOMEGuiTheme.isHovered(mouseX, mouseY, x + width - 84, rowY + 47, 58, 16)) {
                openOrdersFor(row.playerName);
                return true;
            }
        }
        return false;
    }

    private boolean handleTileRowClick(int mouseX, int mouseY) {
        int x = getPanelX() + 24;
        int y = contentY() + 18;
        int width = getPanelWidth() - 48;
        for (int i = 0; i < visibleTileRows() + 1 && tileScroll + i < data.tileBreakdowns.size(); i++) {
            Object object = data.tileBreakdowns.get(tileScroll + i);
            if (!(object instanceof KOMEPacketPopulationGui.TileBreakdown)) {
                continue;
            }
            KOMEPacketPopulationGui.TileBreakdown row = (KOMEPacketPopulationGui.TileBreakdown) object;
            int rowY = y + i * tileRowHeight();
            if (KOMEGuiTheme.isHovered(mouseX, mouseY, x + width - 74, rowY + 18, 58, 16)) {
                KOMEPacketHandler.network.sendToServer(new KOMEPacketConquestOpenCapture(row.tileId));
                return true;
            }
        }
        return false;
    }

    private void openOrdersFor(String playerName) {
        activeTab = TAB_ORDERS;
        initGui();
        playerField.setText(playerName == null ? "" : playerName);
    }

    private List getPlayerRowsWithUnallocated() {
        List rows = new ArrayList();
        if (data.playerBreakdowns != null) {
            rows.addAll(data.playerBreakdowns);
        }
        if (data.unallocatedBreakdown != null && data.unallocatedBreakdown.hasAny()) {
            rows.add(data.unallocatedBreakdown);
        }
        return rows;
    }

    private void drawStackedCapacityBar(int x, int y, int width, int height, int offensiveUsed, int offensiveAvailable, int defensiveUsed, int defensiveAvailable) {
        offensiveUsed = Math.max(0, offensiveUsed);
        offensiveAvailable = Math.max(0, offensiveAvailable);
        defensiveUsed = Math.max(0, defensiveUsed);
        defensiveAvailable = Math.max(0, defensiveAvailable);
        int total = offensiveUsed + offensiveAvailable + defensiveUsed + defensiveAvailable;
        KOMEGuiTheme.drawBorderedRect(x, y, width, height, KOMEGuiTheme.COLOR_BORDER_DARK, 0xFF3A2A1B);
        if (total <= 0) {
            return;
        }
        int innerX = x + 1;
        int innerY = y + 1;
        int innerW = width - 2;
        int innerH = height - 2;
        int drawn = 0;
        drawn += drawStackSegment(innerX + drawn, innerY, innerW, innerH, drawn, total, offensiveUsed, COLOR_OFF_USED);
        drawn += drawStackSegment(innerX + drawn, innerY, innerW, innerH, drawn, total, offensiveAvailable, COLOR_OFF_AVAILABLE);
        drawn += drawStackSegment(innerX + drawn, innerY, innerW, innerH, drawn, total, defensiveUsed, COLOR_DEF_USED);
        drawStackSegment(innerX + drawn, innerY, innerW, innerH, drawn, total, defensiveAvailable, COLOR_DEF_AVAILABLE);
    }

    private int drawStackSegment(int x, int y, int fullWidth, int height, int alreadyDrawn, int total, int value, int color) {
        if (value <= 0 || total <= 0 || alreadyDrawn >= fullWidth) {
            return 0;
        }
        int segmentWidth = Math.max(1, Math.round(fullWidth * (value / (float) total)));
        segmentWidth = Math.min(segmentWidth, fullWidth - alreadyDrawn);
        drawRect(x, y, x + segmentWidth, y + height, color);
        drawRect(x, y, x + segmentWidth, y + 2, 0x22FFFFFF);
        return segmentWidth;
    }

    private void drawLegend(int x, int y) {
        drawLegendSwatch(x, y, COLOR_OFF_USED, "Off used");
        drawLegendSwatch(x + 78, y, COLOR_OFF_AVAILABLE, "Off available");
        drawLegendSwatch(x + 176, y, COLOR_DEF_USED, "Def used");
        drawLegendSwatch(x + 254, y, COLOR_DEF_AVAILABLE, "Def available");
    }

    private void drawLegendSwatch(int x, int y, int color, String label) {
        drawRect(x, y + 2, x + 7, y + 9, color);
        fontRendererObj.drawString(label, x + 10, y, KOMEGuiTheme.COLOR_TEXT_MUTED);
    }

    private void drawMiniButton(int x, int y, int width, String label, int mouseX, int mouseY) {
        boolean hovered = KOMEGuiTheme.isHovered(mouseX, mouseY, x, y, width, 16);
        KOMEGuiTheme.drawBorderedRect(x, y, width, 16, hovered ? KOMEGuiTheme.COLOR_GOLD : KOMEGuiTheme.COLOR_BORDER_RED, hovered ? KOMEGuiTheme.COLOR_PARCHMENT_LIGHT : KOMEGuiTheme.COLOR_PARCHMENT_DARK);
        String trimmed = KOMEGuiTheme.trimToWidth(fontRendererObj, label, width - 6);
        fontRendererObj.drawString(trimmed, x + width / 2 - fontRendererObj.getStringWidth(trimmed) / 2, y + 4, KOMEGuiTheme.COLOR_TEXT);
    }

    private void drawEmptyState(int x, int y, int width, String text) {
        KOMEGuiTheme.drawSubPanel(x, y, width, 52);
        fontRendererObj.drawString(text, x + 12, y + 20, KOMEGuiTheme.COLOR_TEXT_MUTED);
    }

    private void drawScrollHint(int x, int y, int height, int totalRows, int visibleRows, int scroll) {
        if (totalRows <= visibleRows) {
            return;
        }
        drawRect(x, y, x + 2, y + height, 0x665A171A);
        int thumbH = Math.max(12, height * visibleRows / totalRows);
        int thumbY = y + (height - thumbH) * scroll / Math.max(1, totalRows - visibleRows);
        drawRect(x - 1, thumbY, x + 3, thumbY + thumbH, KOMEGuiTheme.COLOR_BORDER_RED);
    }

    private int contentY() {
        return getPanelY() + 78;
    }

    private int playerRowHeight() {
        return 78;
    }

    private int tileRowHeight() {
        return 62;
    }

    private int visiblePlayerRows() {
        return Math.max(1, (getPanelY() + getPanelHeight() - (contentY() + 18) - 18) / playerRowHeight());
    }

    private int visibleTileRows() {
        return Math.max(1, (getPanelY() + getPanelHeight() - (contentY() + 18) - 18) / tileRowHeight());
    }

    private int maxPlayerScroll() {
        return Math.max(0, getPlayerRowsWithUnallocated().size() - visiblePlayerRows());
    }

    private int maxTileScroll() {
        return Math.max(0, (data.tileBreakdowns == null ? 0 : data.tileBreakdowns.size()) - visibleTileRows());
    }

    private boolean isNoFaction() {
        return data.viewerFaction == null || data.viewerFaction.length() == 0 || "None".equalsIgnoreCase(data.viewerFaction) || "No faction".equalsIgnoreCase(data.viewerFaction);
    }

    private String safePlayer() {
        String player = playerField == null ? "" : playerField.getText().trim();
        return player.length() == 0 ? data.playerName : player;
    }

    private String safeAmount() {
        String amount = amountField == null ? "" : amountField.getText().trim();
        return amount.length() == 0 ? "0" : amount;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private int getPanelWidth() {
        return Math.min(PANEL_WIDTH, width - 20);
    }

    private int getPanelHeight() {
        return Math.min(PANEL_HEIGHT, height - 20);
    }

    private int getPanelX() {
        return width / 2 - getPanelWidth() / 2;
    }

    private int getPanelY() {
        return height / 2 - getPanelHeight() / 2;
    }
}
