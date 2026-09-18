package kome.client.gui;

import kome.client.KOMEConquestMapOverlay;
import kome.client.KOMEMinecraftClient;
import kome.common.network.KOMEPacketConquestOpenCapture;
import kome.common.network.KOMEPacketHandler;
import kome.common.network.KOMEPacketPopulationGui;
import lotr.client.gui.LOTRGuiMenu;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.List;

public class KOMEGuiPopulation extends GuiScreen {
    private static final int PANEL_WIDTH = 760;
    private static final int PANEL_HEIGHT = 520;
    private static final int TAB_OVERVIEW = 0;
    private static final int TAB_PLAYERS = 1;
    private static final int TAB_TILES = 2;

    private final KOMEPacketPopulationGui data;
    private int activeTab = TAB_OVERVIEW;
    private int playerScroll;
    private int tileScroll;

    public KOMEGuiPopulation(KOMEPacketPopulationGui message) {
        data = message == null ? new KOMEPacketPopulationGui() : message;
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

    }

    private void addTabButton(int id, int x, int y, String label, int tab) {
        buttonList.add(new KOMEGuiButton(id, x, y, 88, 22, label, activeTab == tab).setSelected(activeTab == tab));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            mc.displayGuiScreen(new LOTRGuiMenu());
        } else if (button.id == 7) {
            KOMEMinecraftClient.sendChat("/population units " + data.playerName);
            KOMEMinecraftClient.closePlayerScreen();
        } else if (button.id == 8) {
            KOMEConquestMapOverlay.openPreservedMap();
        } else if (button.id >= 20 && button.id <= 22) {
            activeTab = button.id - 20;
            initGui();
        }
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
        KOMEGuiTheme.drawSubPanel(x + 24, cy, w - 48, 132);
        fontRendererObj.drawString("Canonical Faction Population", x + 36, cy + 12, KOMEGuiTheme.COLOR_BORDER_RED);
        fontRendererObj.drawString("Available Population: " + kome.common.data.KOMEPopulationProjection.formatCenti(data.population.availablePopulationCenti), x + 36, cy + 34, KOMEGuiTheme.COLOR_TEXT);
        fontRendererObj.drawString("Active Population: " + kome.common.data.KOMEPopulationProjection.formatCenti(data.population.activePopulationCenti), x + 36, cy + 52, KOMEGuiTheme.COLOR_TEXT);
        fontRendererObj.drawString("Daily Population Rate: " + kome.common.data.KOMEPopulationProjection.formatRate(data.population.dailyRateUnits), x + 36, cy + 70, KOMEGuiTheme.COLOR_TEXT);
        fontRendererObj.drawString("Population cap: " + (data.population.capEnabled ? kome.common.data.KOMEPopulationProjection.formatCenti(data.population.capCenti) : "disabled (uncapped)"), x + 36, cy + 88, KOMEGuiTheme.COLOR_TEXT);
        fontRendererObj.drawString("Farmhands: 0.00 population, excluded. Combat investment is permanent.", x + 36, cy + 106, KOMEGuiTheme.COLOR_TEXT_MUTED);
    }

    private void drawPlayersTab(int x, int y, int w, int h, int mouseX, int mouseY) {
        int cy = contentY();
        fontRendererObj.drawString("Faction Player Breakdown", x + 24, cy, KOMEGuiTheme.COLOR_BORDER_RED);
        List rows = getPlayerRows();
        if (rows.isEmpty()) {
            drawEmptyState(x + 24, cy + 18, w - 48, "No faction player population data found.");
            return;
        }
        int listY = cy + 18;
        int listH = y + h - listY - 18;
        playerScroll = clamp(playerScroll, 0, maxPlayerScroll());
        KOMEGuiTheme.enableScissor(mc, x + 18, listY, w - 36, listH);
        for (int i = 0; i < visiblePlayerRows() + 1 && playerScroll + i < rows.size(); i++) {
            drawPlayerRow(x + 24, listY + i * playerRowHeight(), w - 48, (KOMEPacketPopulationGui.PlayerInvestment) rows.get(playerScroll + i), mouseX, mouseY);
        }
        KOMEGuiTheme.disableScissor();
        drawScrollHint(x + w - 18, listY, listH, rows.size(), visiblePlayerRows(), playerScroll);
    }

    private void drawPlayerRow(int x, int y, int width, KOMEPacketPopulationGui.PlayerInvestment row, int mouseX, int mouseY) {
        KOMEGuiTheme.drawCard(x, y, width, playerRowHeight() - 6, KOMEGuiTheme.isHovered(mouseX, mouseY, x, y, width, playerRowHeight() - 6));
        fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, row.playerName, 142), x + 10, y + 7, KOMEGuiTheme.COLOR_BORDER_RED);
        fontRendererObj.drawString("Active Population: " + kome.common.data.KOMEPopulationProjection.formatCenti(row.activePopulationCenti), x + 164, y + 7, KOMEGuiTheme.COLOR_TEXT);
        fontRendererObj.drawString("Faction-funded living combat investment; no personal bank.", x + 164, y + 23, KOMEGuiTheme.COLOR_TEXT_MUTED);
        if (canInspectPlayer(row.playerName)) drawMiniButton(x + width - 202, y + 47, 44, "View", mouseX, mouseY);
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
        KOMEGuiTheme.drawCard(x, y, width, tileRowHeight() - 6, KOMEGuiTheme.isHovered(mouseX, mouseY, x, y, width, tileRowHeight() - 6));
        fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, tileTitle(row), 150), x + 10, y + 7, KOMEGuiTheme.COLOR_BORDER_RED);
        fontRendererObj.drawString("Ruler: " + row.ownerFaction, x + 10, y + 22, KOMEGuiTheme.COLOR_TEXT_MUTED);
        fontRendererObj.drawString("Faction Available: " + kome.common.data.KOMEPopulationProjection.formatCenti(row.population.availablePopulationCenti), x + 162, y + 8, KOMEGuiTheme.COLOR_TEXT);
        fontRendererObj.drawString("Faction Active: " + kome.common.data.KOMEPopulationProjection.formatCenti(row.population.activePopulationCenti), x + 162, y + 23, KOMEGuiTheme.COLOR_TEXT);
        fontRendererObj.drawString("Faction daily rate: " + kome.common.data.KOMEPopulationProjection.formatRate(row.population.dailyRateUnits), x + 162, y + 38, KOMEGuiTheme.COLOR_TEXT_MUTED);
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

    private boolean handlePlayerRowClick(int mouseX, int mouseY) {
        List rows = getPlayerRows();
        int x = getPanelX() + 24;
        int y = contentY() + 18;
        int width = getPanelWidth() - 48;
        for (int i = 0; i < visiblePlayerRows() + 1 && playerScroll + i < rows.size(); i++) {
            KOMEPacketPopulationGui.PlayerInvestment row = (KOMEPacketPopulationGui.PlayerInvestment) rows.get(playerScroll + i);
            int rowY = y + i * playerRowHeight();
            if (canInspectPlayer(row.playerName) && KOMEGuiTheme.isHovered(mouseX, mouseY, x + width - 202, rowY + 47, 44, 16)) {
                KOMEMinecraftClient.sendChat("/population units " + row.playerName);
                KOMEMinecraftClient.closePlayerScreen();
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

    private List getPlayerRows() { return data.playerBreakdowns; }

    private boolean canInspectPlayer(String name) {
        // Affordance only: the server command independently verifies self/operator authority.
        return kome.common.data.KOMEClientData.INSTANCE.clientViewerIsAdmin
            || KOMEMinecraftClient.playerName().equalsIgnoreCase(name);
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
        return Math.max(0, getPlayerRows().size() - visiblePlayerRows());
    }

    private int maxTileScroll() {
        return Math.max(0, (data.tileBreakdowns == null ? 0 : data.tileBreakdowns.size()) - visibleTileRows());
    }

    private boolean isNoFaction() {
        return data.viewerFaction == null || data.viewerFaction.length() == 0 || "None".equalsIgnoreCase(data.viewerFaction) || "No faction".equalsIgnoreCase(data.viewerFaction);
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
