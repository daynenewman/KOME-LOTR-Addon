package kome.client.gui;

import kome.common.network.KOMEPacketHandler;
import kome.common.network.KOMEPacketMovementHistoryRequest;
import kome.common.network.KOMEPacketServerRecordRequest;
import lotr.client.gui.LOTRGuiMenu;
import lotr.client.gui.LOTRGuiMenuBase;
import lotr.common.fac.LOTRFaction;
import net.minecraft.client.gui.GuiButton;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.List;

public class KOMEGuiServerRecords extends LOTRGuiMenuBase {
    private static final int SUMMARY_HEADER_HEIGHT = 24;
    private static final int POPULATION_CARD_HEIGHT = 66;
    private static final int ALLIANCE_ROW_HEIGHT = 31;
    private static final int ALLIANCE_MAX_ROWS = 3;
    private static final int TILE_BADGE_HEIGHT = 16;
    private static final int TILE_BADGE_GAP = 5;
    private static final int TILE_MAX_ROWS = 3;
    private static final int PLAYER_DETAIL_TOP_INSET = 5;
    private static final int PLAYER_DETAIL_CARD_GAPS = 5;
    private static final int TILE_LIST_ROW_HEIGHT = 30;
    private static List rawLines = new ArrayList();
    private static List records = new ArrayList();
    private static List playerRecords = new ArrayList();
    private static List warRecords = new ArrayList();
    private static String summary = "Loading...";
    private static boolean showWars;
    private static String warFilter = "ALL";

    private int scroll;
    private int detailScroll;
    private int selected;
    private boolean isScrolling;
    private boolean isDetailScrolling;
    private boolean isTileListScrolling;
    private boolean wasMouseDown;
    private boolean tileListOpen;
    private int tileListScroll;
    private List tileListEntries = new ArrayList();
    private String tileListPlayer = "";
    private int controlledTilesX;
    private int controlledTilesY;
    private int controlledTilesWidth;
    private int controlledTilesHeight;
    private final KOMEGuiScrollPanel listPanel = new KOMEGuiScrollPanel();
    private final KOMEGuiScrollPanel detailPanel = new KOMEGuiScrollPanel();
    private final KOMEGuiScrollPanel tileListPanel = new KOMEGuiScrollPanel();

    private GuiButton buttonMenu;
    private GuiButton buttonRefresh;
    private GuiButton buttonMovementHistory;
    private GuiButton buttonRecordMode;
    private GuiButton buttonWarFilter;
    private GuiButton buttonTilesBack;

    public static void update(List updatedLines) {
        rawLines = updatedLines == null ? new ArrayList() : new ArrayList(updatedLines);
        parseRecords();
    }

    public static void resetData() {
        rawLines = new ArrayList();
        records = new ArrayList();
        playerRecords = new ArrayList();
        warRecords = new ArrayList();
        summary = "Loading...";
    }

    public static void update(List updatedLines, boolean reset, boolean complete) {
        if (reset) {
            rawLines = new ArrayList();
        }
        if (updatedLines != null) {
            rawLines.addAll(updatedLines);
        }
        summary = "Loading... (" + rawLines.size() + " lines)";
        if (complete) {
            parseRecords();
        }
    }

    @Override
    public void initGui() {
        xSize = Math.min(660, width - 36);
        ySize = Math.min(430, height - 44);
        super.initGui();
        buttonList.clear();
        buttonMenuReturn = null;
        selected = records.isEmpty() ? -1 : Math.max(0, Math.min(selected, records.size() - 1));
        buttonMenu = KOMEGuiButton.small(0, guiLeft + 14, guiTop + 14, "Menu");
        buttonRefresh = KOMEGuiButton.normal(1, guiLeft + xSize - 110, guiTop + 14, "Refresh");
        buttonMovementHistory = new KOMEGuiButton(2, guiLeft + xSize - 184, guiTop + 47, 164, KOMEGuiButton.HEIGHT_SMALL, "Troop Movements");
        buttonRecordMode = new KOMEGuiButton(3, guiLeft + 18, guiTop + 47, 104, KOMEGuiButton.HEIGHT_SMALL, showWars ? "Players" : "Wars");
        buttonWarFilter = new KOMEGuiButton(4, guiLeft + 128, guiTop + 47, 112, KOMEGuiButton.HEIGHT_SMALL, "Filter: " + titleCase(warFilter));
        buttonWarFilter.visible = showWars;
        buttonList.add(buttonMenu);
        buttonList.add(buttonRecordMode);
        buttonList.add(buttonWarFilter);
        buttonList.add(buttonMovementHistory);
        buttonList.add(buttonRefresh);
        if (tileListOpen) {
            setPrimaryButtonsVisible(false);
            buttonTilesBack = KOMEGuiButton.small(5, guiLeft + 14, guiTop + 14, "Back");
            buttonList.add(buttonTilesBack);
            tileListScroll = Math.min(tileListScroll, getMaxTileListScroll());
        } else {
            requestRecords();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (tileListOpen) {
            updateTileListScrollbarDrag(mouseX, mouseY);
        } else {
            updateScrollbarDrag(mouseX, mouseY);
        }
        drawDefaultBackground();
        drawPanel(mouseX, mouseY);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public void updateScreen() {
        if (Boolean.getBoolean("kome.guiCapture") && mc.thePlayer == null) return;
        super.updateScreen();
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }
        int mouseX = Mouse.getEventX() * width / mc.displayWidth;
        int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        if (tileListOpen) {
            if (KOMEGuiTheme.isHovered(mouseX, mouseY, getTileListX(), getTileListY(),
                    getTileListWidth(), getTileListHeight())) {
                tileListScroll = wheel > 0
                    ? Math.max(0, tileListScroll - TILE_LIST_ROW_HEIGHT)
                    : Math.min(getMaxTileListScroll(), tileListScroll + TILE_LIST_ROW_HEIGHT);
            }
            return;
        }
        int detailX = getDetailX();
        int detailY = getContentY();
        int detailW = getDetailWidth();
        int detailH = getContentHeight();
        if (KOMEGuiTheme.isHovered(mouseX, mouseY, detailX, detailY, detailW, detailH)) {
            int max = getMaxDetailScroll();
            if (wheel > 0) {
                detailScroll = Math.max(0, detailScroll - 18);
            } else {
                detailScroll = Math.min(max, detailScroll + 18);
            }
        } else {
            int max = getMaxScroll();
            if (wheel > 0) {
                scroll = Math.max(0, scroll - 1);
            } else {
                scroll = Math.min(max, scroll + 1);
            }
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0) {
            return;
        }
        if (tileListOpen) {
            super.mouseClicked(mouseX, mouseY, button);
            return;
        }
        if (!showWars && selected >= 0 && selected < records.size()
                && KOMEGuiTheme.isHovered(mouseX, mouseY, controlledTilesX, controlledTilesY,
                    controlledTilesWidth, controlledTilesHeight)
                && KOMEGuiTheme.isHovered(mouseX, mouseY, getDetailX(), getContentY() + 23,
                    getDetailWidth(), getContentHeight() - 28)) {
            openTileList((Record) records.get(selected));
            return;
        }
        int listX = guiLeft + 18;
        int listY = getContentY() + 23;
        for (int i = 0; i < getVisibleRows() && scroll + i < records.size(); i++) {
            int rowY = listY + i * getRowHeight();
            if (KOMEGuiTheme.isHovered(mouseX, mouseY, listX + 4, rowY, getListWidth() - 18, getRowHeight() - 6)) {
                selected = scroll + i;
                detailScroll = 0;
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void actionPerformed(GuiButton button) {
        if (!button.enabled) {
            return;
        }
        if (button == buttonMenu) {
            mc.displayGuiScreen(new LOTRGuiMenu());
        } else if (button == buttonMovementHistory) {
            KOMEPacketHandler.network.sendToServer(new KOMEPacketMovementHistoryRequest("", false));
        } else if (button == buttonRecordMode) {
            showWars = !showWars;
            applyRecordMode();
            selected = records.isEmpty() ? -1 : 0;
            scroll = 0;
            detailScroll = 0;
            initGui();
        } else if (button == buttonWarFilter) {
            warFilter = "ALL".equals(warFilter) ? "ACTIVE" : "ACTIVE".equals(warFilter) ? "ENDING"
                : "ENDING".equals(warFilter) ? "ENDED" : "ALL";
            applyRecordMode();
            selected = records.isEmpty() ? -1 : 0;
            scroll = 0;
            detailScroll = 0;
            initGui();
        } else if (button == buttonRefresh) {
            requestRecords();
        } else if (button == buttonTilesBack) {
            closeTileList();
        } else {
            super.actionPerformed(button);
        }
    }

    private void requestRecords() {
        if (Boolean.getBoolean("kome.guiCapture")) return;
        rawLines = new ArrayList();
        summary = "Loading...";
        KOMEPacketHandler.network.sendToServer(new KOMEPacketServerRecordRequest());
    }

    static void setVisualTestWarMode(boolean value) {
        if (!Boolean.getBoolean("kome.guiCapture")) return;
        showWars = value;
        warFilter = "ALL";
        applyRecordMode();
    }

    private void drawPanel(int mouseX, int mouseY) {
        if (tileListOpen) {
            drawTileListView(mouseX, mouseY);
            return;
        }
        if (records.isEmpty()) {
            selected = -1;
            scroll = 0;
        } else {
            selected = Math.max(0, Math.min(selected, records.size() - 1));
            scroll = Math.max(0, Math.min(scroll, getMaxScroll()));
        }
        KOMEGuiTheme.drawMainPanel(guiLeft, guiTop, xSize, ySize);
        KOMEGuiTheme.drawHeader(fontRendererObj, "Server Records", guiLeft + 130, guiTop + 13, xSize - 260);
        String summaryLine = KOMEGuiTheme.trimToWidth(fontRendererObj, summary, xSize - 36);
        fontRendererObj.drawString(summaryLine, guiLeft + xSize / 2 - fontRendererObj.getStringWidth(summaryLine) / 2,
            guiTop + 72, KOMEGuiTheme.COLOR_TEXT_MUTED);

        int listX = guiLeft + 18;
        int listY = getContentY();
        int listW = getListWidth();
        int detailX = getDetailX();
        int detailW = getDetailWidth();
        drawTable(listX, listY, listW, mouseX, mouseY);
        drawDetail(detailX, listY, detailW, mouseX, mouseY);
        if (!records.isEmpty()) listPanel.drawScrollbar();
    }

    private void drawTable(int x, int y, int width, int mouseX, int mouseY) {
        KOMEGuiTheme.drawSubPanel(x, y, width, getContentHeight());
        KOMEGuiTheme.drawSectionTitle(fontRendererObj, showWars ? "Coalition Wars" : "Public Ledger", x + 9, y + 8, width - 18);
        if (records.isEmpty()) {
            fontRendererObj.drawString(showWars ? "No wars match this filter." : "No player records yet.", x + 14, y + 35, KOMEGuiTheme.COLOR_TEXT);
            return;
        }
        int listY = y + 23;
        int rowH = getRowHeight();
        listPanel.layout(x + 1, listY, width - 3, getContentHeight() - 28,
            records.size() * rowH).setScroll(scroll * rowH);
        listPanel.begin(mc);
        for (int i = 0; i < getVisibleRows() && scroll + i < records.size(); i++) {
            Object record = records.get(scroll + i);
            int rowY = listY + i * rowH;
            boolean active = selected == scroll + i;
            boolean hover = KOMEGuiTheme.isHovered(mouseX, mouseY, x + 4, rowY, width - 18, rowH - 6);
            if (record instanceof WarRecord) drawWarRow((WarRecord) record, x + 4, rowY, width - 18, rowH - 6, active, hover);
            else drawPlayerRow((Record) record, x + 4, rowY, width - 18, rowH - 6, active, hover);
        }
        listPanel.end();
    }

    private void drawWarRow(WarRecord war, int x, int y, int width, int height, boolean selectedRow, boolean hovered) {
        int border = selectedRow ? KOMEGuiTheme.COLOR_GOLD : hovered ? KOMEGuiTheme.COLOR_BORDER_RED_LIGHT : KOMEGuiTheme.COLOR_GOLD_DARK;
        int fill = selectedRow ? KOMEGuiTheme.COLOR_STONE : hovered ? KOMEGuiTheme.COLOR_PARCHMENT_LIGHT : KOMEGuiTheme.COLOR_PARCHMENT_DARK;
        KOMEGuiTheme.drawBorderedRect(x, y, width, height, border, fill);
        drawFactionIcon(war.sideOneFactions, x + 8, y + 8, 22);
        drawFactionIcon(war.sideTwoFactions, x + 32, y + 8, 22);
        KOMEGuiTheme.drawBorderedRect(x + 1, y + 1, 4, height - 2, factionColor(war.sideOneFactions), factionColor(war.sideOneFactions));
        String status = titleCase(war.status);
        int chipW = Math.min(70, KOMEGuiTheme.statusChipWidth(fontRendererObj, status));
        KOMEGuiTheme.drawStatusChip(fontRendererObj, status, warStatus(war.status), x + width - chipW - 6, y + 5, chipW);
        fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, war.name + " (" + war.id + ")", width - 68 - chipW), x + 60, y + 6, KOMEGuiTheme.COLOR_GOLD);
        fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, war.sideOneName + " vs " + war.sideTwoName, width - 68), x + 60, y + 18, KOMEGuiTheme.COLOR_TEXT);
        fontRendererObj.drawString("Stewardship " + war.activeCompanies + ("None".equals(war.warning) ? "" : " | Warning recorded"), x + 60, y + 30,
            "None".equals(war.warning) ? KOMEGuiTheme.COLOR_TEXT_MUTED : KOMEGuiTheme.COLOR_WARN);
    }

    private void drawPlayerRow(Record record, int x, int y, int width, int height, boolean selectedRow, boolean hovered) {
        int border = selectedRow ? KOMEGuiTheme.COLOR_GOLD : hovered ? KOMEGuiTheme.COLOR_BORDER_RED_LIGHT : KOMEGuiTheme.COLOR_GOLD_DARK;
        int fill = selectedRow ? KOMEGuiTheme.COLOR_STONE : hovered ? KOMEGuiTheme.COLOR_PARCHMENT_LIGHT : KOMEGuiTheme.COLOR_PARCHMENT_DARK;
        KOMEGuiTheme.drawBorderedRect(x, y, width, height, border, fill);
        if (selectedRow) {
            KOMEGuiTheme.drawBorderedRect(x + 2, y + 2, 4, height - 4, KOMEGuiTheme.COLOR_BORDER_RED, KOMEGuiTheme.COLOR_BORDER_RED);
        }
        drawFactionIcon(record.faction, x + 10, y + 8, 22);
        fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, record.name, width - 48), x + 40, y + 6, KOMEGuiTheme.COLOR_BORDER_RED);
        fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, record.faction, width - 48), x + 40, y + 18, KOMEGuiTheme.COLOR_TEXT);
        fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, record.rank, width - 48), x + 40, y + 30, KOMEGuiTheme.COLOR_TEXT_MUTED);
    }

    private void drawDetail(int x, int y, int width, int mouseX, int mouseY) {
        int height = getContentHeight();
        KOMEGuiTheme.drawSubPanel(x, y, width, height);
        if (selected < 0 || selected >= records.size()) {
            int cardW = Math.min(230, width - 36);
            int cardX = x + (width - cardW) / 2;
            int cardY = y + height / 2 - 24;
            KOMEGuiTheme.drawCard(cardX, cardY, cardW, 48, false);
            KOMEGuiTheme.drawWrappedText(fontRendererObj, "Select a player record.", cardX + 16, cardY + 17, cardW - 32, KOMEGuiTheme.COLOR_TEXT_MUTED);
            return;
        }
        Object selectedRecord = records.get(selected);
        if (selectedRecord instanceof WarRecord) {
            drawWarDetail((WarRecord) selectedRecord, x, y, width, height, mouseX, mouseY);
            return;
        }
        Record record = (Record) selectedRecord;
        KOMEGuiTheme.drawSectionTitle(fontRendererObj, KOMEGuiTheme.trimToWidth(fontRendererObj, record.name, width - 18), x + 9, y + 8, width - 18);
        detailPanel.layout(x + 1, y + 23, width - 3, height - 28, getDetailContentHeight()).setScroll(detailScroll);
        detailScroll = detailPanel.getScroll();
        detailPanel.begin(mc);
        int cursorY = y + 28 - detailScroll;
        cursorY = drawInfoCard("Faction / Rank", record.faction + " / " + record.rank, x + 12, cursorY, width - 24, mouseX, mouseY);
        cursorY = drawInfoCard("Progression", record.progress + " completed", x + 12, cursorY + 8, width - 24, mouseX, mouseY);
        cursorY = drawPopulationCard(record.population, x + 12, cursorY + 8, width - 24, mouseX, mouseY);
        cursorY = drawInfoCard("Pledged Lord", record.lord, x + 12, cursorY + 8, width - 24, mouseX, mouseY);
        cursorY = drawAlliancesCard(record.alliances, x + 12, cursorY + 8, width - 24, mouseX, mouseY);
        drawControlledTilesCard(record.tileCount, record.tiles, x + 12, cursorY + 8, width - 24, mouseX, mouseY);
        detailPanel.end();
        detailScroll = Math.min(detailScroll, getMaxDetailScroll());
        detailPanel.drawScrollbar();
    }

    private void drawWarDetail(WarRecord war, int x, int y, int width, int height, int mouseX, int mouseY) {
        String status = titleCase(war.status);
        int chipW = Math.min(76, KOMEGuiTheme.statusChipWidth(fontRendererObj, status));
        KOMEGuiTheme.drawSectionTitle(fontRendererObj, KOMEGuiTheme.trimToWidth(fontRendererObj, war.name + " / " + war.id, width - 32 - chipW), x + 9, y + 8, width - 30 - chipW);
        KOMEGuiTheme.drawStatusChip(fontRendererObj, status, warStatus(war.status), x + width - chipW - 9, y + 4, chipW);
        detailPanel.layout(x + 1, y + 23, width - 3, height - 28, getDetailContentHeight()).setScroll(detailScroll);
        detailScroll = detailPanel.getScroll();
        detailPanel.begin(mc);
        int cursorY = y + 28 - detailScroll;
        cursorY = drawInfoCard("Status / Dates", war.status + " | Created " + war.createdAt + " | Ending " + war.endingAt + " | Ended " + war.endedAt, x + 12, cursorY, width - 24, mouseX, mouseY);
        cursorY = drawCoalitionCard(war.sideOneName, war.sideOneFactions, x + 12, cursorY + 8, width - 24, mouseX, mouseY);
        cursorY = drawCoalitionCard(war.sideTwoName, war.sideTwoFactions, x + 12, cursorY + 8, width - 24, mouseX, mouseY);
        cursorY = drawInfoCard("Coalition Membership Provenance", war.memberships, x + 12, cursorY + 8, width - 24, mouseX, mouseY);
        cursorY = drawInfoCard("Automatic Military T3 Support", war.supportEnrollments, x + 12, cursorY + 8, width - 24, mouseX, mouseY);
        cursorY = drawInfoCard("Latest Tile Event", war.latestCapture, x + 12, cursorY + 8, width - 24, mouseX, mouseY);
        cursorY = drawInfoCard("Tile-capture History", war.captureHistory, x + 12, cursorY + 8, width - 24, mouseX, mouseY);
        cursorY = drawInfoCard("Supporting Coordinators", war.coordinators, x + 12, cursorY + 8, width - 24, mouseX, mouseY);
        cursorY = drawInfoCard("Stewardship Reservations", war.reservations, x + 12, cursorY + 8, width - 24, mouseX, mouseY);
        cursorY = drawInfoCard("Pending Withdrawals / Demobilizations", war.pending, x + 12, cursorY + 8, width - 24, mouseX, mouseY);
        cursorY += 8;
        KOMEGuiTheme.drawWarningBanner(fontRendererObj, "War Warnings", emptyAsNone(war.warning), x + 12, cursorY,
            width - 24, "None".equals(emptyAsNone(war.warning)) ? KOMEGuiTheme.Status.NEUTRAL : KOMEGuiTheme.Status.WARNING);
        cursorY += KOMEGuiTheme.warningBannerHeight(fontRendererObj, emptyAsNone(war.warning), width - 24);
        cursorY = drawInfoCard("Administrative History", war.adminHistory, x + 12, cursorY + 8, width - 24, mouseX, mouseY);
        drawInfoCard("End Reason", war.endReason, x + 12, cursorY + 8, width - 24, mouseX, mouseY);
        detailPanel.end();
        detailScroll = Math.min(detailScroll, getMaxDetailScroll());
        detailPanel.drawScrollbar();
    }

    private int drawCoalitionCard(String title, String factions, int x, int y, int width, int mouseX, int mouseY) {
        String display = emptyAsNone(factions);
        int cardHeight = getCardHeight(display, width) + 10;
        KOMEGuiTheme.drawCard(x, y, width, cardHeight, KOMEGuiTheme.isHovered(mouseX, mouseY, x, y, width, cardHeight));
        KOMEGuiTheme.drawFactionBadge(fontRendererObj, firstFaction(display), title, x + 8, y + 6, Math.min(150, width - 16));
        KOMEGuiTheme.drawWrappedText(fontRendererObj, display, x + 8, y + 30, width - 16, KOMEGuiTheme.COLOR_TEXT);
        return y + cardHeight;
    }

    private KOMEGuiTheme.Status warStatus(String status) {
        if ("ACTIVE".equals(status)) return KOMEGuiTheme.Status.ACTIVE;
        if ("ENDED".equals(status)) return KOMEGuiTheme.Status.LOCKED;
        return KOMEGuiTheme.Status.WARNING;
    }

    private String firstFaction(String factions) {
        if (factions == null) return "";
        int split = factions.indexOf(',');
        return split < 0 ? factions.trim() : factions.substring(0, split).trim();
    }

    private String emptyAsNone(String value) {
        return value == null || value.trim().length() == 0 ? "None" : value;
    }

    private int drawInfoCard(String title, String value, int x, int y, int width, int mouseX, int mouseY) {
        String display = value == null || value.trim().length() == 0 ? "None" : value;
        int cardHeight = getCardHeight(display, width);
        KOMEGuiTheme.drawCard(x, y, width, cardHeight, KOMEGuiTheme.isHovered(mouseX, mouseY, x, y, width, cardHeight));
        fontRendererObj.drawString(title, x + 8, y + 7, KOMEGuiTheme.COLOR_BORDER_RED);
        KOMEGuiTheme.drawWrappedText(fontRendererObj, display, x + 8, y + 20, width - 16, KOMEGuiTheme.COLOR_TEXT);
        return y + cardHeight;
    }

    private int drawPopulationCard(String value, int x, int y, int width, int mouseX, int mouseY) {
        KOMEServerRecordPresentation.PopulationSummary population =
            KOMEServerRecordPresentation.parsePopulationSummary(value);
        KOMEGuiTheme.drawCard(x, y, width, POPULATION_CARD_HEIGHT,
            KOMEGuiTheme.isHovered(mouseX, mouseY, x, y, width, POPULATION_CARD_HEIGHT));
        drawSummaryHeader("Population", "Total " + population.total, x, y, width);
        drawRightAlignedRow("Offensive", String.valueOf(population.offensive), x + 10, y + 29, width - 20);
        drawRightAlignedRow("Defensive", String.valueOf(population.defensive), x + 10, y + 45, width - 20);
        return y + POPULATION_CARD_HEIGHT;
    }

    private int drawAlliancesCard(String value, int x, int y, int width, int mouseX, int mouseY) {
        List summaries = KOMEServerRecordPresentation.parseAllianceSummaries(value);
        int cardHeight = getAlliancesCardHeight(summaries.size());
        KOMEGuiTheme.drawCard(x, y, width, cardHeight,
            KOMEGuiTheme.isHovered(mouseX, mouseY, x, y, width, cardHeight));
        drawSummaryHeader("Alliances / Own Tiers",
            KOMEServerRecordPresentation.countLabel(summaries.size(), "Alliance", "Alliances"), x, y, width);
        if (summaries.isEmpty()) {
            fontRendererObj.drawString("No alliances", x + 10, y + 32, KOMEGuiTheme.COLOR_TEXT_MUTED);
            return y + cardHeight;
        }
        int shown = Math.min(ALLIANCE_MAX_ROWS, summaries.size());
        int cursorY = y + SUMMARY_HEADER_HEIGHT + 4;
        for (int i = 0; i < shown; i++) {
            KOMEServerRecordPresentation.AllianceSummary summary =
                (KOMEServerRecordPresentation.AllianceSummary) summaries.get(i);
            if (i > 0) {
                KOMEGuiTheme.drawDivider(x + 10, cursorY - 3, width - 20);
            }
            fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, summary.faction, width - 20),
                x + 10, cursorY, KOMEGuiTheme.COLOR_TEXT);
            int trackY = cursorY + 13;
            int trackWidth = (width - 20) / 3;
            drawAllianceTrack("Civilian", summary.civilian, x + 10, trackY, trackWidth);
            drawAllianceTrack("Military", summary.military, x + 10 + trackWidth, trackY, trackWidth);
            drawAllianceTrack("Trade", summary.trade, x + 10 + trackWidth * 2, trackY, width - 20 - trackWidth * 2);
            cursorY += ALLIANCE_ROW_HEIGHT;
        }
        if (summaries.size() > shown) {
            fontRendererObj.drawString("+" + (summaries.size() - shown) + " more",
                x + 10, y + cardHeight - 14, KOMEGuiTheme.COLOR_TEXT_MUTED);
        }
        return y + cardHeight;
    }

    private int drawControlledTilesCard(String count, String names, int x, int y, int width, int mouseX, int mouseY) {
        List tiles = KOMEServerRecordPresentation.parseTileIds(names);
        int declaredCount = Math.max(KOMEServerRecordPresentation.parseNonNegativeInt(count, tiles.size()), tiles.size());
        KOMEServerRecordPresentation.TileBadgeLayout layout = tileBadgeLayout(tiles, width);
        int cardHeight = getControlledTilesCardHeight(layout);
        controlledTilesX = x;
        controlledTilesY = y;
        controlledTilesWidth = width;
        controlledTilesHeight = cardHeight;
        KOMEGuiTheme.drawCard(x, y, width, cardHeight,
            KOMEGuiTheme.isHovered(mouseX, mouseY, x, y, width, cardHeight));
        drawSummaryHeader("Controlled Tiles", KOMEServerRecordPresentation.countLabel(declaredCount, "Tile", "Tiles"), x, y, width);
        if (tiles.isEmpty()) {
            fontRendererObj.drawString("No controlled tiles", x + 10, y + 32, KOMEGuiTheme.COLOR_TEXT_MUTED);
            return y + cardHeight;
        }
        int cursorX = x + 10;
        int cursorY = y + SUMMARY_HEADER_HEIGHT + 5;
        for (int i = 0; i < layout.visibleTiles; i++) {
            if (i > 0 && i % layout.perRow == 0) {
                cursorX = x + 10;
                cursorY += TILE_BADGE_HEIGHT + TILE_BADGE_GAP;
            }
            drawTileBadge(String.valueOf(tiles.get(i)), cursorX, cursorY, layout.badgeWidth);
            cursorX += layout.badgeWidth + TILE_BADGE_GAP;
        }
        if (layout.hiddenTiles > 0) {
            int badgeIndex = layout.visibleTiles;
            if (badgeIndex > 0 && badgeIndex % layout.perRow == 0) {
                cursorX = x + 10;
                cursorY += TILE_BADGE_HEIGHT + TILE_BADGE_GAP;
            }
            drawTileBadge("+" + layout.hiddenTiles + " more", cursorX, cursorY, layout.badgeWidth);
        }
        return y + cardHeight;
    }

    private void openTileList(Record record) {
        tileListEntries = KOMEServerRecordPresentation.parseTileEntries(record.tiles);
        tileListPlayer = record.name;
        tileListScroll = 0;
        tileListOpen = true;
        setPrimaryButtonsVisible(false);
        buttonTilesBack = KOMEGuiButton.small(5, guiLeft + 14, guiTop + 14, "Back");
        buttonList.add(buttonTilesBack);
    }

    private void closeTileList() {
        tileListOpen = false;
        tileListScroll = 0;
        isTileListScrolling = false;
        if (buttonTilesBack != null) {
            buttonList.remove(buttonTilesBack);
            buttonTilesBack = null;
        }
        setPrimaryButtonsVisible(true);
    }

    private void setPrimaryButtonsVisible(boolean visible) {
        buttonMenu.visible = visible;
        buttonRefresh.visible = visible;
        buttonMovementHistory.visible = visible;
        buttonRecordMode.visible = visible;
        buttonWarFilter.visible = visible && showWars;
    }

    private void drawTileListView(int mouseX, int mouseY) {
        KOMEGuiTheme.drawMainPanel(guiLeft, guiTop, xSize, ySize);
        KOMEGuiTheme.drawHeader(fontRendererObj, "Controlled Tiles", guiLeft + 130, guiTop + 13, xSize - 260);
        String subtitle = tileListPlayer + " | "
            + KOMEServerRecordPresentation.countLabel(tileListEntries.size(), "Tile", "Tiles");
        subtitle = KOMEGuiTheme.trimToWidth(fontRendererObj, subtitle, xSize - 64);
        fontRendererObj.drawString(subtitle,
            guiLeft + xSize / 2 - fontRendererObj.getStringWidth(subtitle) / 2,
            guiTop + 58, KOMEGuiTheme.COLOR_TEXT_MUTED);

        int x = getTileListX();
        int y = getTileListY();
        int width = getTileListWidth();
        int height = getTileListHeight();
        KOMEGuiTheme.drawSubPanel(x, y, width, height);
        KOMEGuiTheme.drawSectionTitle(fontRendererObj, "All Controlled Tiles", x + 9, y + 8, width - 18);
        if (tileListEntries.isEmpty()) {
            fontRendererObj.drawString("No controlled tiles", x + 14, y + 36, KOMEGuiTheme.COLOR_TEXT_MUTED);
            return;
        }

        int viewportY = y + 24;
        int viewportH = height - 26;
        int contentHeight = tileListEntries.size() * TILE_LIST_ROW_HEIGHT + 6;
        tileListPanel.layout(x + 1, viewportY, width - 3, viewportH, contentHeight).setScroll(tileListScroll);
        tileListScroll = tileListPanel.getScroll();
        tileListPanel.begin(mc);
        int rowY = viewportY + 3 - tileListScroll;
        for (int i = 0; i < tileListEntries.size(); i++) {
            KOMEServerRecordPresentation.TileEntry entry =
                (KOMEServerRecordPresentation.TileEntry) tileListEntries.get(i);
            boolean hovered = KOMEGuiTheme.isHovered(mouseX, mouseY, x + 10, rowY, width - 25,
                TILE_LIST_ROW_HEIGHT - 4);
            KOMEGuiTheme.drawCard(x + 10, rowY, width - 25, TILE_LIST_ROW_HEIGHT - 4, hovered);
            String name = entry.waypointName.length() == 0 ? entry.tileId : entry.waypointName;
            fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, name, width - 115),
                x + 20, rowY + 9, KOMEGuiTheme.COLOR_TEXT);
            int badgeW = Math.max(48, fontRendererObj.getStringWidth(entry.tileId) + 14);
            drawTileBadge(entry.tileId, x + width - badgeW - 22, rowY + 5, badgeW);
            rowY += TILE_LIST_ROW_HEIGHT;
        }
        tileListPanel.end();
        tileListScroll = Math.min(tileListScroll, getMaxTileListScroll());
        tileListPanel.drawScrollbar();
    }

    private int getTileListX() {
        return guiLeft + 18;
    }

    private int getTileListY() {
        return getContentY();
    }

    private int getTileListWidth() {
        return xSize - 36;
    }

    private int getTileListHeight() {
        return getContentHeight();
    }

    private int getMaxTileListScroll() {
        int viewport = Math.max(1, getTileListHeight() - 26);
        return Math.max(0, tileListEntries.size() * TILE_LIST_ROW_HEIGHT + 6 - viewport);
    }

    private void updateTileListScrollbarDrag(int mouseX, int mouseY) {
        boolean isMouseDown = Mouse.isButtonDown(0);
        int x = getTileListX() + getTileListWidth() - 10;
        int y = getTileListY() + 24;
        int height = getTileListHeight() - 26;
        int max = getMaxTileListScroll();
        if (!wasMouseDown && isMouseDown && max > 0
                && KOMEGuiTheme.isHovered(mouseX, mouseY, x - 2, y, 9, height)) {
            isTileListScrolling = true;
        }
        if (!isMouseDown) {
            isTileListScrolling = false;
        }
        wasMouseDown = isMouseDown;
        if (isTileListScrolling) {
            float amount = (mouseY - y) / (float) Math.max(1, height);
            amount = Math.max(0.0F, Math.min(1.0F, amount));
            tileListScroll = Math.round(amount * max);
        }
    }

    private void drawSummaryHeader(String title, String total, int x, int y, int width) {
        fontRendererObj.drawString(title, x + 10, y + 7, KOMEGuiTheme.COLOR_BORDER_RED);
        fontRendererObj.drawString(total, x + width - fontRendererObj.getStringWidth(total) - 10,
            y + 7, KOMEGuiTheme.COLOR_TEXT);
        KOMEGuiTheme.drawDivider(x + 10, y + 20, width - 20);
    }

    private void drawRightAlignedRow(String label, String value, int x, int y, int width) {
        fontRendererObj.drawString(label, x, y, KOMEGuiTheme.COLOR_TEXT_MUTED);
        fontRendererObj.drawString(value, x + width - fontRendererObj.getStringWidth(value), y, KOMEGuiTheme.COLOR_TEXT);
    }

    private void drawAllianceTrack(String label, String tier, int x, int y, int width) {
        String safeLabel = KOMEGuiTheme.trimToWidth(fontRendererObj, label, Math.max(1, width - 34));
        fontRendererObj.drawString(safeLabel, x, y, KOMEGuiTheme.COLOR_TEXT_MUTED);
        int valueX = x + fontRendererObj.getStringWidth(safeLabel) + 4;
        String safeTier = KOMEGuiTheme.trimToWidth(fontRendererObj, tier, Math.max(1, x + width - valueX - 2));
        fontRendererObj.drawString(safeTier, valueX, y, allianceTierColor(tier));
    }

    private int allianceTierColor(String tier) {
        if ("Pending".equalsIgnoreCase(tier)) {
            return KOMEGuiTheme.COLOR_WARN;
        }
        return KOMEServerRecordPresentation.isUnlockedTier(tier) ? KOMEGuiTheme.COLOR_GOLD : KOMEGuiTheme.COLOR_TEXT_DISABLED;
    }

    private void drawTileBadge(String label, int x, int y, int width) {
        KOMEGuiTheme.drawBorderedRect(x, y, width, TILE_BADGE_HEIGHT,
            KOMEGuiTheme.COLOR_GOLD_DARK, KOMEGuiTheme.COLOR_PANEL_DARK_SOFT);
        String display = KOMEGuiTheme.trimToWidth(fontRendererObj, label, width - 8);
        fontRendererObj.drawString(display, x + (width - fontRendererObj.getStringWidth(display)) / 2,
            y + 4, KOMEGuiTheme.COLOR_TEXT);
    }

    private void drawListScrollbar(int x, int y) {
        int rows = getVisibleRows();
        if (records.size() <= rows) {
            return;
        }
        int trackH = rows * getRowHeight() - 4;
        KOMEGuiTheme.drawBorderedRect(x, y, 5, trackH, KOMEGuiTheme.COLOR_GOLD_DARK, 0x552B2117);
        int max = Math.max(1, getMaxScroll());
        int handleH = Math.max(18, trackH * rows / records.size());
        int handleY = y + (trackH - handleH) * scroll / max;
        KOMEGuiTheme.drawBorderedRect(x, handleY, 5, handleH, KOMEGuiTheme.COLOR_BORDER_RED, KOMEGuiTheme.COLOR_GOLD);
    }

    private void drawDetailScrollbar(int x, int y, int height) {
        int max = getMaxDetailScroll();
        if (max <= 0) {
            return;
        }
        KOMEGuiTheme.drawBorderedRect(x, y, 5, height, KOMEGuiTheme.COLOR_GOLD_DARK, 0x552B2117);
        int content = getDetailContentHeight();
        int handleH = Math.max(18, height * height / Math.max(height, content));
        int handleY = y + (height - handleH) * detailScroll / max;
        KOMEGuiTheme.drawBorderedRect(x, handleY, 5, handleH, KOMEGuiTheme.COLOR_BORDER_RED, KOMEGuiTheme.COLOR_GOLD);
    }

    private void updateScrollbarDrag(int mouseX, int mouseY) {
        boolean isMouseDown = Mouse.isButtonDown(0);
        int listX = guiLeft + 18;
        int listY = getContentY() + 23;
        int scrollX = listX + getListWidth() - 10;
        int trackH = getVisibleRows() * getRowHeight() - 4;
        int max = getMaxScroll();
        int detailX = getDetailX() + getDetailWidth() - 10;
        int detailY = getContentY() + 23;
        int detailH = getContentHeight() - 28;
        int detailMax = getMaxDetailScroll();
        if (!wasMouseDown && isMouseDown && max > 0 && KOMEGuiTheme.isHovered(mouseX, mouseY, scrollX - 2, listY, 9, trackH)) {
            isScrolling = true;
        }
        if (!wasMouseDown && isMouseDown && detailMax > 0 && KOMEGuiTheme.isHovered(mouseX, mouseY, detailX - 2, detailY, 9, detailH)) {
            isDetailScrolling = true;
        }
        if (!isMouseDown) {
            isScrolling = false;
            isDetailScrolling = false;
        }
        wasMouseDown = isMouseDown;
        if (isScrolling) {
            float amount = (mouseY - listY) / (float) Math.max(1, trackH);
            amount = Math.max(0.0F, Math.min(1.0F, amount));
            scroll = Math.round(amount * max);
        }
        if (isDetailScrolling) {
            float amount = (mouseY - detailY) / (float) Math.max(1, detailH);
            amount = Math.max(0.0F, Math.min(1.0F, amount));
            detailScroll = Math.round(amount * detailMax);
        }
    }

    private void drawFactionIcon(String faction, int x, int y, int size) {
        int color = factionColor(faction);
        KOMEGuiTheme.drawIconSlot(x, y, size, false);
        KOMEGuiTheme.drawBorderedRect(x + 2, y + 2, size - 4, size - 4, KOMEGuiTheme.COLOR_BORDER_DARK, 0xFF000000 | color);
        String initials = factionInitials(faction);
        int textWidth = fontRendererObj.getStringWidth(initials);
        fontRendererObj.drawString(initials, x + (size - textWidth) / 2, y + 7, KOMEGuiTheme.COLOR_TEXT_LIGHT);
    }

    private int factionColor(String faction) {
        if (isUnpledgedFaction(faction)) {
            return 0x6B5A3A;
        }
        LOTRFaction resolved = LOTRFaction.forName(faction);
        if (resolved != null) return resolved.getFactionColor() & 0xFFFFFF;
        String key = faction.toLowerCase();
        int hash = key.hashCode();
        int red = 80 + (hash & 0x7F);
        int green = 80 + ((hash >> 8) & 0x7F);
        int blue = 80 + ((hash >> 16) & 0x7F);
        return red << 16 | green << 8 | blue;
    }

    private String factionInitials(String faction) {
        if (isUnpledgedFaction(faction)) {
            return "-";
        }
        String[] words = faction.split(" ");
        String initials = "";
        for (int i = 0; i < words.length && initials.length() < 2; i++) {
            String word = words[i].replaceAll("[^A-Za-z0-9]", "");
            if (word.length() == 0 || "of".equalsIgnoreCase(word) || "the".equalsIgnoreCase(word) || "and".equalsIgnoreCase(word)) {
                continue;
            }
            initials += word.substring(0, 1).toUpperCase();
        }
        return initials.length() == 0 ? faction.substring(0, 1).toUpperCase() : initials;
    }

    private String formatNames(String names) {
        return names == null || names.trim().isEmpty() ? "" : " - " + names;
    }

    private int getContentY() {
        return guiTop + 84;
    }

    private int getContentHeight() {
        return ySize - 102;
    }

    private int getListWidth() {
        return Math.max(230, Math.min(285, xSize / 2 - 20));
    }

    private int getDetailX() {
        return guiLeft + 18 + getListWidth() + 12;
    }

    private int getDetailWidth() {
        return guiLeft + xSize - 18 - getDetailX();
    }

    private int getRowHeight() {
        return 48;
    }

    private int getVisibleRows() {
        return Math.max(4, (getContentHeight() - 28) / getRowHeight());
    }

    private int getMaxScroll() {
        return Math.max(0, records.size() - getVisibleRows());
    }

    private int getMaxDetailScroll() {
        return Math.max(0, getDetailContentHeight() - (getContentHeight() - 28));
    }

    private int getDetailContentHeight() {
        if (selected < 0 || selected >= records.size()) {
            return 0;
        }
        Object selectedRecord = records.get(selected);
        int width = getDetailWidth() - 24;
        if (selectedRecord instanceof WarRecord) {
            WarRecord war = (WarRecord) selectedRecord;
            int total = 10 * 8;
            total += getCardHeight(war.status + " | Created " + war.createdAt + " | Ending " + war.endingAt + " | Ended " + war.endedAt, width);
            total += getCardHeight(war.sideOneFactions, width) + 10 + getCardHeight(war.sideTwoFactions, width) + 10;
            total += getCardHeight(war.memberships, width) + getCardHeight(war.supportEnrollments, width);
            total += getCardHeight(war.latestCapture, width) + getCardHeight(war.captureHistory, width);
            total += getCardHeight(war.coordinators, width) + getCardHeight(war.reservations, width);
            total += getCardHeight(war.pending, width) + KOMEGuiTheme.warningBannerHeight(fontRendererObj, emptyAsNone(war.warning), width);
            total += getCardHeight(war.adminHistory, width) + getCardHeight(war.endReason, width);
            return total;
        }
        Record record = (Record) selectedRecord;
        int total = PLAYER_DETAIL_TOP_INSET + PLAYER_DETAIL_CARD_GAPS * 8;
        total += getCardHeight(record.faction + " / " + record.rank, width);
        total += getCardHeight(record.progress + " completed", width);
        total += POPULATION_CARD_HEIGHT;
        total += getCardHeight(record.lord, width);
        total += getAlliancesCardHeight(KOMEServerRecordPresentation.parseAllianceSummaries(record.alliances).size());
        total += getControlledTilesCardHeight(tileBadgeLayout(KOMEServerRecordPresentation.parseTileIds(record.tiles), width));
        return total;
    }

    private int getCardHeight(String value, int width) {
        String display = value == null || value.trim().length() == 0 ? "None" : value;
        return 28 + KOMEGuiTheme.wrapText(fontRendererObj, display, width - 16).size() * 10;
    }

    private int getAlliancesCardHeight(int allianceCount) {
        if (allianceCount <= 0) {
            return 52;
        }
        int shown = Math.min(ALLIANCE_MAX_ROWS, allianceCount);
        return SUMMARY_HEADER_HEIGHT + 8 + shown * ALLIANCE_ROW_HEIGHT + (allianceCount > shown ? 14 : 0);
    }

    private int getControlledTilesCardHeight(KOMEServerRecordPresentation.TileBadgeLayout layout) {
        if (layout.totalTiles <= 0) {
            return 52;
        }
        return SUMMARY_HEADER_HEIGHT + 10 + layout.rows * TILE_BADGE_HEIGHT
            + Math.max(0, layout.rows - 1) * TILE_BADGE_GAP;
    }

    private KOMEServerRecordPresentation.TileBadgeLayout tileBadgeLayout(List tiles, int width) {
        int maxTextWidth = fontRendererObj.getStringWidth("+999 more");
        for (Object value : tiles) {
            maxTextWidth = Math.max(maxTextWidth, fontRendererObj.getStringWidth(String.valueOf(value)));
        }
        int badgeWidth = Math.max(58, Math.min(84, maxTextWidth + 14));
        return KOMEServerRecordPresentation.computeTileBadgeLayout(
            tiles.size(), badgeWidth, width, 20, TILE_BADGE_GAP, TILE_MAX_ROWS);
    }

    private static void parseRecords() {
        playerRecords = new ArrayList();
        warRecords = new ArrayList();
        summary = "Players: 0";
        for (Object object : rawLines) {
            String line = String.valueOf(object);
            String[] parts = line.split("\t", -1);
            if (parts.length == 0) {
                continue;
            }
            if ("SUMMARY".equals(parts[0]) && parts.length >= 3) {
                summary = "Players: " + parts[1] + " | Tiles: " + parts[2] + " | Wars: " + (parts.length >= 4 ? parts[3] : "0");
            } else if ("PLAYER".equals(parts[0]) && parts.length >= 9) {
                playerRecords.add(new Record(parts));
            } else if ("WAR".equals(parts[0]) && parts.length >= 20) {
                warRecords.add(new WarRecord(parts));
            }
        }
        applyRecordMode();
    }

    private static void applyRecordMode() {
        records = new ArrayList();
        if (!showWars) {
            records.addAll(playerRecords);
            return;
        }
        for (Object object : warRecords) {
            WarRecord war = (WarRecord) object;
            if ("ALL".equals(warFilter) || warFilter.equals(war.status)) records.add(war);
        }
    }

    private static class Record {
        private final String uuid;
        private final String name;
        private final String faction;
        private final String rank;
        private final String progress;
        private final String population;
        private final String lord;
        private final String alliances;
        private final String tileCount;
        private final String tiles;

        private Record(String[] parts) {
            uuid = parts[1];
            name = parts[2];
            faction = normalizeFactionDisplay(parts[3]);
            rank = parts[4];
            progress = parts[5];
            population = parts[6];
            if (parts.length >= 11) {
                lord = parts[7].length() == 0 ? "No pledged lord" : parts[7];
                alliances = parts[8].length() == 0 ? "No alliances" : parts[8];
                tileCount = parts[9];
                tiles = parts[10];
            } else if (parts.length >= 10) {
                lord = parts[7].length() == 0 ? "No pledged lord" : parts[7];
                alliances = "No alliances";
                tileCount = parts[8];
                tiles = parts[9];
            } else {
                lord = "No pledged lord";
                alliances = "No alliances";
                tileCount = parts[7];
                tiles = parts[8];
            }
        }
    }

    private static class WarRecord {
        private final String id;
        private final String name;
        private final String status;
        private final String sideOneName;
        private final String sideOneFactions;
        private final String sideTwoName;
        private final String sideTwoFactions;
        private final String createdAt;
        private final String latestCapture;
        private final String activeCompanies;
        private final String warning;
        private final String captureHistory;
        private final String coordinators;
        private final String reservations;
        private final String pending;
        private final String adminHistory;
        private final String endReason;
        private final String endingAt;
        private final String endedAt;
        private final String memberships;
        private final String supportEnrollments;

        private WarRecord(String[] parts) {
            id = parts[1]; name = parts[2]; status = parts[3]; sideOneName = parts[4]; sideOneFactions = parts[5];
            sideTwoName = parts[6]; sideTwoFactions = parts[7]; createdAt = parts[8]; latestCapture = parts[9];
            activeCompanies = parts[10]; warning = parts[11]; captureHistory = parts[12]; coordinators = parts[13];
            reservations = parts[14]; pending = parts[15]; adminHistory = parts[16]; endReason = parts[17];
            endingAt = parts[18]; endedAt = parts[19];
            memberships = parts.length > 20 ? parts[20] : "Legacy membership provenance unavailable";
            supportEnrollments = parts.length > 21 ? parts[21] : "None";
        }
    }

    private static String titleCase(String value) {
        if (value == null || value.length() == 0) return "All";
        return value.substring(0, 1).toUpperCase() + value.substring(1).toLowerCase();
    }

    private static String normalizeFactionDisplay(String faction) {
        return isUnpledgedFaction(faction) ? "Unpledged" : faction;
    }

    private static boolean isUnpledgedFaction(String faction) {
        if (faction == null) {
            return true;
        }
        String value = faction.trim();
        if (value.length() == 0 || "No faction".equalsIgnoreCase(value) || "Unpledged".equalsIgnoreCase(value)) {
            return true;
        }
        String normalized = value.toLowerCase().replaceAll("[^a-z0-9]", "");
        return normalized.length() == 0 || "unaligned".equals(normalized)
            || "lotrfactionunalignedname".equals(normalized);
    }
}
