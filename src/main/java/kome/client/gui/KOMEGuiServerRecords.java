package kome.client.gui;

import kome.common.network.KOMEPacketHandler;
import kome.common.network.KOMEPacketMovementHistoryRequest;
import kome.common.network.KOMEPacketServerRecordRequest;
import lotr.client.gui.LOTRGuiMenu;
import lotr.client.gui.LOTRGuiMenuBase;
import net.minecraft.client.gui.GuiButton;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.List;

public class KOMEGuiServerRecords extends LOTRGuiMenuBase {
    private static List rawLines = new ArrayList();
    private static List records = new ArrayList();
    private static String summary = "Loading...";

    private int scroll;
    private int detailScroll;
    private int selected;
    private boolean isScrolling;
    private boolean isDetailScrolling;
    private boolean wasMouseDown;

    private GuiButton buttonMenu;
    private GuiButton buttonRefresh;
    private GuiButton buttonMovementHistory;

    public static void update(List updatedLines) {
        rawLines = updatedLines == null ? new ArrayList() : new ArrayList(updatedLines);
        parseRecords();
    }

    public static void resetData() {
        rawLines = new ArrayList();
        records = new ArrayList();
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
        buttonList.add(buttonMenu);
        buttonList.add(buttonMovementHistory);
        buttonList.add(buttonRefresh);
        requestRecords();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        updateScrollbarDrag(mouseX, mouseY);
        drawDefaultBackground();
        drawPanel(mouseX, mouseY);
        super.drawScreen(mouseX, mouseY, partialTicks);
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
        } else if (button == buttonRefresh) {
            requestRecords();
        } else {
            super.actionPerformed(button);
        }
    }

    private void requestRecords() {
        rawLines = new ArrayList();
        records = new ArrayList();
        summary = "Loading...";
        scroll = 0;
        detailScroll = 0;
        selected = -1;
        KOMEPacketHandler.network.sendToServer(new KOMEPacketServerRecordRequest());
    }

    private void drawPanel(int mouseX, int mouseY) {
        KOMEGuiTheme.drawMainPanel(guiLeft, guiTop, xSize, ySize);
        KOMEGuiTheme.drawHeader(fontRendererObj, "Server Records", guiLeft + 130, guiTop + 13, xSize - 260);
        fontRendererObj.drawString(summary, guiLeft + 20, guiTop + 52, KOMEGuiTheme.COLOR_TEXT_MUTED);

        int listX = guiLeft + 18;
        int listY = getContentY();
        int listW = getListWidth();
        int detailX = getDetailX();
        int detailW = getDetailWidth();
        drawTable(listX, listY, listW, mouseX, mouseY);
        drawDetail(detailX, listY, detailW, mouseX, mouseY);
        drawListScrollbar(listX + listW - 10, listY + 23);
    }

    private void drawTable(int x, int y, int width, int mouseX, int mouseY) {
        KOMEGuiTheme.drawSubPanel(x, y, width, getContentHeight());
        KOMEGuiTheme.drawSectionTitle(fontRendererObj, "Public Ledger", x + 9, y + 8, width - 18);
        if (records.isEmpty()) {
            fontRendererObj.drawString("No player records yet.", x + 14, y + 35, KOMEGuiTheme.COLOR_TEXT);
            return;
        }
        int listY = y + 23;
        int rowH = getRowHeight();
        KOMEGuiTheme.enableScissor(mc, x + 1, listY, width - 3, getContentHeight() - 28);
        for (int i = 0; i < getVisibleRows() && scroll + i < records.size(); i++) {
            Record record = (Record) records.get(scroll + i);
            int rowY = listY + i * rowH;
            boolean active = selected == scroll + i;
            boolean hover = KOMEGuiTheme.isHovered(mouseX, mouseY, x + 4, rowY, width - 18, rowH - 6);
            drawPlayerRow(record, x + 4, rowY, width - 18, rowH - 6, active, hover);
        }
        KOMEGuiTheme.disableScissor();
    }

    private void drawPlayerRow(Record record, int x, int y, int width, int height, boolean selectedRow, boolean hovered) {
        int border = selectedRow ? KOMEGuiTheme.COLOR_GOLD : hovered ? KOMEGuiTheme.COLOR_BORDER_RED_LIGHT : KOMEGuiTheme.COLOR_GOLD_DARK;
        int fill = selectedRow ? 0xFFE8D6A8 : hovered ? KOMEGuiTheme.COLOR_PARCHMENT_LIGHT : KOMEGuiTheme.COLOR_PARCHMENT_DARK;
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
        Record record = (Record) records.get(selected);
        KOMEGuiTheme.drawSectionTitle(fontRendererObj, KOMEGuiTheme.trimToWidth(fontRendererObj, record.name, width - 18), x + 9, y + 8, width - 18);
        KOMEGuiTheme.enableScissor(mc, x + 1, y + 23, width - 3, height - 28);
        int cursorY = y + 28 - detailScroll;
        cursorY = drawInfoCard("Faction / Rank", record.faction + " / " + record.rank, x + 12, cursorY, width - 24, mouseX, mouseY);
        cursorY = drawInfoCard("Progression", record.progress + " completed", x + 12, cursorY + 8, width - 24, mouseX, mouseY);
        cursorY = drawInfoCard("Population", record.population, x + 12, cursorY + 8, width - 24, mouseX, mouseY);
        cursorY = drawInfoCard("Pledged Lord", record.lord, x + 12, cursorY + 8, width - 24, mouseX, mouseY);
        cursorY = drawInfoCard("Alliances", record.alliances, x + 12, cursorY + 8, width - 24, mouseX, mouseY);
        drawInfoCard("Controlled Tiles", record.tileCount + formatNames(record.tiles), x + 12, cursorY + 8, width - 24, mouseX, mouseY);
        KOMEGuiTheme.disableScissor();
        detailScroll = Math.min(detailScroll, getMaxDetailScroll());
        drawDetailScrollbar(x + width - 10, y + 23, height - 28);
    }

    private int drawInfoCard(String title, String value, int x, int y, int width, int mouseX, int mouseY) {
        String display = value == null || value.trim().length() == 0 ? "None" : value;
        int cardHeight = getCardHeight(display, width);
        KOMEGuiTheme.drawCard(x, y, width, cardHeight, KOMEGuiTheme.isHovered(mouseX, mouseY, x, y, width, cardHeight));
        fontRendererObj.drawString(title, x + 8, y + 7, KOMEGuiTheme.COLOR_BORDER_RED);
        KOMEGuiTheme.drawWrappedText(fontRendererObj, display, x + 8, y + 20, width - 16, KOMEGuiTheme.COLOR_TEXT);
        return y + cardHeight;
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
        String key = faction.toLowerCase();
        if (key.contains("dunedain") || key.contains("ranger")) {
            return 0x2F6B3C;
        }
        if (key.contains("hobbit")) {
            return 0xC59B3F;
        }
        if (key.contains("mordor")) {
            return 0x1B1B1B;
        }
        if (key.contains("gondor")) {
            return 0xD8D8D8;
        }
        if (key.contains("rohan")) {
            return 0x5D8B2E;
        }
        if (key.contains("isengard")) {
            return 0x6E6E72;
        }
        if (key.contains("angmar")) {
            return 0x4E2A73;
        }
        if (key.contains("blue") || key.contains("mountain")) {
            return 0x337AA8;
        }
        if (key.contains("durin") || key.contains("dwarf")) {
            return 0x8A5A2B;
        }
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
        Record record = (Record) records.get(selected);
        int width = getDetailWidth() - 24;
        int total = 4 * 8;
        total += getCardHeight(record.faction + " / " + record.rank, width);
        total += getCardHeight(record.progress + " completed", width);
        total += getCardHeight(record.population, width);
        total += getCardHeight(record.lord, width);
        total += getCardHeight(record.alliances, width);
        total += getCardHeight(record.tileCount + formatNames(record.tiles), width);
        return total;
    }

    private int getCardHeight(String value, int width) {
        String display = value == null || value.trim().length() == 0 ? "None" : value;
        return 28 + KOMEGuiTheme.wrapText(fontRendererObj, display, width - 16).size() * 10;
    }

    private static void parseRecords() {
        records = new ArrayList();
        summary = "Players: 0";
        for (Object object : rawLines) {
            String line = String.valueOf(object);
            String[] parts = line.split("\t", -1);
            if (parts.length == 0) {
                continue;
            }
            if ("SUMMARY".equals(parts[0]) && parts.length >= 3) {
                summary = "Players: " + parts[1] + " | Claimed tiles: " + parts[2];
            } else if ("PLAYER".equals(parts[0]) && parts.length >= 9) {
                records.add(new Record(parts));
            }
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
