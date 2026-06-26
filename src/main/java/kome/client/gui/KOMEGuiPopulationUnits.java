package kome.client.gui;

import kome.client.KOMEMinecraftClient;
import kome.common.data.KOMEConquestTile;
import kome.common.network.KOMEPacketConquestOpenCapture;
import kome.common.network.KOMEPacketHandler;
import kome.common.network.KOMEUnitGuiEntry;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.List;

public class KOMEGuiPopulationUnits extends GuiScreen {
    private static final int PANEL_WIDTH = 720;
    private static final int PANEL_HEIGHT = 420;
    private static final int MARGIN = 18;
    private static final int GAP = 12;
    private static final int HEADER_HEIGHT = 22;
    private static final int FILTER_HEIGHT = 20;
    private static final int ACTION_HEIGHT = 24;
    private static final int ROW_HEIGHT = 48;
    private static final int LIST_WIDTH = 270;

    private static final int FILTER_ALL = 0;
    private static final int FILTER_OFFENSIVE = 1;
    private static final int FILTER_DEFENSIVE = 2;
    private static final int FILTER_STATIONED = 3;
    private static final int FILTER_MOVING = 4;
    private static final int FILTER_MOUNTED = 5;
    private static final int FILTER_GROUND = 6;
    private static final int FILTER_FARMHANDS = 7;

    private final String playerName;
    private final String tileFilter;
    private final List units;
    private final int armyUsed;
    private final int armyTotal;
    private final int farmhandsUsed;
    private final int farmhandsLimit;
    private int filter;
    private int scroll;
    private int selectedIndex;

    public KOMEGuiPopulationUnits(String playerName, String tileFilter, List units, int armyUsed, int armyTotal, int farmhandsUsed, int farmhandsLimit) {
        this.playerName = playerName == null ? "" : playerName;
        this.tileFilter = KOMEConquestTile.normalizeId(tileFilter);
        this.units = units == null ? new ArrayList() : units;
        this.armyUsed = armyUsed;
        this.armyTotal = armyTotal;
        this.farmhandsUsed = farmhandsUsed;
        this.farmhandsLimit = farmhandsLimit;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int x = getPanelX();
        int y = getPanelY();
        int panelW = getPanelWidth();
        int filterY = y + 43;
        int filterGap = 4;
        int filterW = (panelW - MARGIN * 2 - filterGap * 7) / 8;
        String[] labels = {"All", "Offensive", "Defensive", "Stationed", "Moving", "Mounted", "Ground", "Farmhands"};
        for (int i = 0; i < labels.length; i++) {
            buttonList.add(new KOMEGuiButton(10 + i, x + MARGIN + i * (filterW + filterGap), filterY, filterW, FILTER_HEIGHT, labels[i]));
        }
        int actionY = y + getPanelHeight() - MARGIN - ACTION_HEIGHT;
        buttonList.add(new KOMEGuiButton(0, x + MARGIN, actionY, 100, ACTION_HEIGHT, "Back"));
        buttonList.add(new KOMEGuiButton(2, x + panelW / 2 - 72, actionY, 144, ACTION_HEIGHT, "Move Company"));
        buttonList.add(new KOMEGuiButton(1, x + panelW - MARGIN - 100, actionY, 100, ACTION_HEIGHT, "Refresh"));
        updateFilterButtons();
        clampSelection();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            if (tileFilter.length() > 0) {
                KOMEPacketHandler.network.sendToServer(new KOMEPacketConquestOpenCapture(tileFilter));
                KOMEMinecraftClient.closePlayerScreen();
            } else {
                KOMEMinecraftClient.sendChat("/population gui " + playerName);
                KOMEMinecraftClient.closePlayerScreen();
            }
        } else if (button.id == 1) {
            String command = "/population units " + playerName + (tileFilter.length() > 0 ? " " + tileFilter : "");
            KOMEMinecraftClient.sendChat(command);
            KOMEMinecraftClient.closePlayerScreen();
        } else if (button.id == 2) {
            KOMEUnitGuiEntry selected = getSelectedUnit();
            if (selected != null && selected.canMove) {
                KOMEMinecraftClient.sendChat("/troops companies " + selected.currentTile);
                KOMEMinecraftClient.closePlayerScreen();
            }
        } else if (button.id >= 10 && button.id <= 17) {
            filter = button.id - 10;
            scroll = 0;
            selectedIndex = 0;
            updateFilterButtons();
            clampSelection();
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton != 0) {
            return;
        }
        int listX = getPanelX() + MARGIN;
        int listY = getPanelY() + 78;
        int listH = getPanelHeight() - 78 - MARGIN - ACTION_HEIGHT - 10;
        if (!KOMEGuiTheme.isHovered(mouseX, mouseY, listX, listY, LIST_WIDTH, listH)) {
            return;
        }
        int row = (mouseY - listY - 6) / ROW_HEIGHT;
        List visible = getFilteredUnits();
        int index = scroll + row;
        if (row >= 0 && index >= 0 && index < visible.size()) {
            selectedIndex = index;
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }
        int max = Math.max(0, getFilteredUnits().size() - getVisibleRows());
        scroll = Math.max(0, Math.min(max, scroll + (wheel < 0 ? 1 : -1)));
        clampSelection();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int x = getPanelX();
        int y = getPanelY();
        int panelW = getPanelWidth();
        int panelH = getPanelHeight();
        KOMEGuiTheme.drawMainPanel(x, y, panelW, panelH);
        KOMEGuiTheme.drawHeader(fontRendererObj, "Unit Command", x + 128, y + 12, panelW - 256);
        String context = tileFilter.length() > 0 ? "Stationed at Tile " + tileFilter : playerName + "'s hired units";
        fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, context, 250), x + MARGIN, y + 20, KOMEGuiTheme.COLOR_TEXT_MUTED);
        String capacity = "Military " + armyUsed + "/" + armyTotal + "   Farmhands " + farmhandsUsed + "/" + farmhandsLimit;
        fontRendererObj.drawString(capacity, x + panelW - MARGIN - fontRendererObj.getStringWidth(capacity), y + 20, KOMEGuiTheme.COLOR_TEXT_MUTED);

        int contentY = y + 78;
        int contentH = panelH - 78 - MARGIN - ACTION_HEIGHT - 10;
        drawUnitList(x + MARGIN, contentY, LIST_WIDTH, contentH, mouseX, mouseY);
        int detailX = x + MARGIN + LIST_WIDTH + GAP;
        drawDetails(detailX, contentY, panelW - MARGIN * 2 - LIST_WIDTH - GAP, contentH);
        updateMoveButton();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawUnitList(int x, int y, int width, int height, int mouseX, int mouseY) {
        KOMEGuiTheme.drawSubPanel(x, y, width, height);
        List visible = getFilteredUnits();
        if (visible.isEmpty()) {
            KOMEGuiTheme.drawCenteredPlainText(fontRendererObj, "No units match this filter.", x + width / 2, y + height / 2 - 4, KOMEGuiTheme.COLOR_TEXT_MUTED);
            return;
        }
        KOMEGuiTheme.enableScissor(mc, x + 1, y + 4, width - 3, height - 8);
        int rows = getVisibleRows();
        for (int i = 0; i < rows && scroll + i < visible.size(); i++) {
            KOMEUnitGuiEntry unit = (KOMEUnitGuiEntry) visible.get(scroll + i);
            int rowY = y + 6 + i * ROW_HEIGHT;
            boolean selected = scroll + i == selectedIndex;
            boolean hover = KOMEGuiTheme.isHovered(mouseX, mouseY, x + 7, rowY, width - 20, ROW_HEIGHT - 5);
            KOMEGuiTheme.drawCard(x + 7, rowY, width - 20, ROW_HEIGHT - 5, hover || selected);
            fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, unit.unitName, width - 116), x + 15, rowY + 7, KOMEGuiTheme.COLOR_BORDER_RED);
            String status = unit.movementStatus;
            fontRendererObj.drawString(status, x + width - 16 - fontRendererObj.getStringWidth(status), rowY + 7,
                "Moving".equals(status) || "Pending Spawn".equals(status) ? KOMEGuiTheme.COLOR_WARN : KOMEGuiTheme.COLOR_GOOD);
            String badges = unit.populationType + "  |  " + (unit.mounted ? "Mounted" : unit.farmhand ? "Worker" : "Ground");
            fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, badges, width - 34), x + 15, rowY + 21, KOMEGuiTheme.COLOR_TEXT);
            fontRendererObj.drawString(tileLabel(unit.currentTile), x + 15, rowY + 33, KOMEGuiTheme.COLOR_TEXT_MUTED);
        }
        KOMEGuiTheme.disableScissor();
        drawScrollbar(x + width - 10, y + 7, height - 14, visible.size());
    }

    private void drawDetails(int x, int y, int width, int height) {
        KOMEGuiTheme.drawSubPanel(x, y, width, height);
        KOMEUnitGuiEntry unit = getSelectedUnit();
        if (unit == null) {
            KOMEGuiTheme.drawCenteredPlainText(fontRendererObj, "Select a unit to view its details.", x + width / 2, y + height / 2 - 4, KOMEGuiTheme.COLOR_TEXT_MUTED);
            return;
        }
        fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, unit.unitName, width - 116), x + 12, y + 10, KOMEGuiTheme.COLOR_BORDER_RED);
        String company = unit.companyName.length() == 0 ? "Unassigned" : unit.companyName + " (" + unit.companyStatus + ")";
        String owner = unit.ownerName + "  |  " + unit.factionName + "  |  " + company;
        fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, owner, width - 24), x + 12, y + 23, KOMEGuiTheme.COLOR_TEXT_MUTED);
        drawStatusBadge(unit.movementStatus, x + width - 92, y + 8, 80);

        int cardY = y + 40;
        drawLocationCard(unit, x + 10, cardY, width - 20, 76);
        cardY += 84;
        drawPopulationCard(unit, x + 10, cardY, width - 20, 94);
        cardY += 102;
        drawMovementCard(unit, x + 10, cardY, width - 20, Math.max(56, height - (cardY - y) - 10));
    }

    private void drawLocationCard(KOMEUnitGuiEntry unit, int x, int y, int width, int height) {
        KOMEGuiTheme.drawCard(x, y, width, height, false);
        KOMEGuiTheme.drawSectionTitle(fontRendererObj, "Location", x + 10, y + 8, width - 20);
        boolean inTransit = "Moving".equals(unit.movementStatus) || "Pending Spawn".equals(unit.movementStatus);
        detailLine(x, y + 24, width, inTransit ? "Current Location" : "Current Tile",
            inTransit ? "In transit to " + tileLabel(unit.destinationTile) : tileLabel(unit.currentTile));
        String hiredFrom = "PLAYER_RESERVE".equals(unit.sourceType) ? "Player Reserve" : tileLabel(unit.sourceTile);
        detailLine(x, y + 38, width, "Hired From", hiredFrom);
        if (inTransit || "Arrival Pending".equals(unit.movementStatus)) {
            detailLine(x, y + 52, width, "Origin / Destination", tileLabel(unit.currentTile) + " -> " + tileLabel(unit.destinationTile));
            detailLine(x, y + 66, width, "ETA", unit.etaMillis > 0L ? formatDuration(unit.etaMillis) : "Pending arrival");
        } else {
            detailLine(x, y + 52, width, "Movement Status", unit.movementStatus);
        }
    }

    private void drawPopulationCard(KOMEUnitGuiEntry unit, int x, int y, int width, int height) {
        KOMEGuiTheme.drawCard(x, y, width, height, false);
        KOMEGuiTheme.drawSectionTitle(fontRendererObj, "Population", x + 10, y + 8, width - 20);
        detailLine(x, y + 24, width, "Population Type", unit.populationType);
        detailLine(x, y + 38, width, "Population Cost", unit.farmhand ? "Farmhand capacity" : String.valueOf(unit.populationCost));
        detailLine(x, y + 52, width, "Funding Source", "PLAYER_RESERVE".equals(unit.sourceType) ? "Player Reserve" : "Faction Tile Population");
        String allocation = unit.allocationTile.length() > 0 ? "Yes - " + tileLabel(unit.allocationTile) : "No";
        detailLine(x, y + 66, width, "Allocation Used", allocation);
        detailLine(x, y + 80, width, "Releases To", unit.releasesTo);
    }

    private void drawMovementCard(KOMEUnitGuiEntry unit, int x, int y, int width, int height) {
        KOMEGuiTheme.drawCard(x, y, width, height, false);
        KOMEGuiTheme.drawSectionTitle(fontRendererObj, "Movement", x + 10, y + 8, width - 20);
        detailLine(x, y + 24, width, "Can Move", unit.canMove ? "Yes" : "No");
        String note = unit.canMove
            ? unit.mounted ? "Mounted units move faster in mounted-only orders." : "Ground or mixed armies use the slower movement rate."
            : unit.cannotMoveReason;
        KOMEGuiTheme.drawWrappedText(fontRendererObj, note, x + 10, y + 40, width - 20, unit.canMove ? KOMEGuiTheme.COLOR_TEXT_MUTED : KOMEGuiTheme.COLOR_WARN);
        if (unit.movementOrderId.length() > 0) {
            fontRendererObj.drawString("Order: " + unit.movementOrderId, x + 10, y + height - 14, KOMEGuiTheme.COLOR_TEXT_MUTED);
        } else {
            fontRendererObj.drawString("Movement is handled by troop movement orders.", x + 10, y + height - 14, KOMEGuiTheme.COLOR_TEXT_MUTED);
        }
    }

    private void detailLine(int x, int y, int width, String label, String value) {
        fontRendererObj.drawString(label + ":", x + 10, y, KOMEGuiTheme.COLOR_TEXT_MUTED);
        int valueX = x + 118;
        fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, value, width - 128), valueX, y, KOMEGuiTheme.COLOR_TEXT);
    }

    private void drawStatusBadge(String status, int x, int y, int width) {
        int color = "Moving".equals(status) || "Pending Spawn".equals(status)
            ? KOMEGuiTheme.COLOR_WARN : KOMEGuiTheme.COLOR_GOOD;
        KOMEGuiTheme.drawFactionBadge(fontRendererObj, status, x, y, width, color);
    }

    private List getFilteredUnits() {
        List visible = new ArrayList();
        for (Object object : units) {
            KOMEUnitGuiEntry unit = (KOMEUnitGuiEntry) object;
            if (matchesFilter(unit)) {
                visible.add(unit);
            }
        }
        return visible;
    }

    private boolean matchesFilter(KOMEUnitGuiEntry unit) {
        if (filter == FILTER_OFFENSIVE) {
            return "Offensive".equals(unit.populationType);
        }
        if (filter == FILTER_DEFENSIVE) {
            return "Defensive".equals(unit.populationType);
        }
        if (filter == FILTER_STATIONED) {
            return "Stationed".equals(unit.movementStatus);
        }
        if (filter == FILTER_MOVING) {
            return "Moving".equals(unit.movementStatus) || "Pending Spawn".equals(unit.movementStatus)
                || "Arrival Pending".equals(unit.movementStatus);
        }
        if (filter == FILTER_MOUNTED) {
            return unit.mounted && !unit.farmhand;
        }
        if (filter == FILTER_GROUND) {
            return !unit.mounted && !unit.farmhand;
        }
        if (filter == FILTER_FARMHANDS) {
            return unit.farmhand;
        }
        return true;
    }

    private KOMEUnitGuiEntry getSelectedUnit() {
        List visible = getFilteredUnits();
        if (visible.isEmpty()) {
            return null;
        }
        selectedIndex = Math.max(0, Math.min(selectedIndex, visible.size() - 1));
        return (KOMEUnitGuiEntry) visible.get(selectedIndex);
    }

    private void clampSelection() {
        List visible = getFilteredUnits();
        selectedIndex = visible.isEmpty() ? 0 : Math.max(0, Math.min(selectedIndex, visible.size() - 1));
        int maxScroll = Math.max(0, visible.size() - getVisibleRows());
        scroll = Math.max(0, Math.min(scroll, maxScroll));
    }

    private void updateFilterButtons() {
        for (Object object : buttonList) {
            GuiButton button = (GuiButton) object;
            if (button.id >= 10 && button.id <= 17 && button instanceof KOMEGuiButton) {
                ((KOMEGuiButton) button).setSelected(button.id - 10 == filter);
            }
        }
    }

    private void updateMoveButton() {
        KOMEUnitGuiEntry selected = getSelectedUnit();
        for (Object object : buttonList) {
            GuiButton button = (GuiButton) object;
            if (button.id == 2) {
                button.enabled = selected != null && selected.canMove;
                button.displayString = selected != null && "Defensive".equals(selected.populationType)
                    ? "Defensive: Cannot Move" : "Move From This Tile";
                return;
            }
        }
    }

    private int getVisibleRows() {
        int listHeight = getPanelHeight() - 78 - MARGIN - ACTION_HEIGHT - 10;
        return Math.max(1, (listHeight - 10) / ROW_HEIGHT);
    }

    private void drawScrollbar(int x, int y, int height, int size) {
        if (size <= getVisibleRows()) {
            return;
        }
        KOMEGuiTheme.drawBorderedRect(x, y, 5, height, KOMEGuiTheme.COLOR_GOLD_DARK, 0x552B2117);
        int max = Math.max(1, size - getVisibleRows());
        int handleH = Math.max(18, height * getVisibleRows() / size);
        int handleY = y + (height - handleH) * scroll / max;
        KOMEGuiTheme.drawBorderedRect(x, handleY, 5, handleH, KOMEGuiTheme.COLOR_BORDER_RED, KOMEGuiTheme.COLOR_GOLD);
    }

    private String tileLabel(String tile) {
        String normalized = KOMEConquestTile.normalizeId(tile);
        return normalized.length() == 0 ? "Unstationed" : "Tile " + normalized;
    }

    private String formatDuration(long millis) {
        long minutes = Math.max(0L, (millis + 59999L) / 60000L);
        long days = minutes / 1440L;
        long hours = (minutes % 1440L) / 60L;
        long mins = minutes % 60L;
        if (days > 0L) {
            return days + "d " + hours + "h";
        }
        if (hours > 0L) {
            return hours + "h " + mins + "m";
        }
        return mins + "m";
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
