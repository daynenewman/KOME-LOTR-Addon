package kome.client.gui;

import kome.common.network.KOMEPacketHandler;
import kome.common.network.KOMEPacketServerRecordRequest;
import lotr.client.gui.LOTRGuiMenu;
import lotr.client.gui.LOTRGuiMenuBase;
import net.minecraft.client.gui.Gui;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.List;

public class KOMEGuiServerRecords extends LOTRGuiMenuBase {
    private static List rawLines = new ArrayList();
    private static List records = new ArrayList();
    private static String summary = "Loading...";

    private int scroll;
    private int selected;
    private boolean isScrolling;
    private boolean wasMouseDown;

    public static void update(List updatedLines) {
        rawLines = updatedLines == null ? new ArrayList() : new ArrayList(updatedLines);
        parseRecords();
    }

    @Override
    public void initGui() {
        xSize = Math.min(620, width - 36);
        ySize = Math.min(410, height - 44);
        super.initGui();
        buttonList.clear();
        buttonMenuReturn = null;
        selected = records.isEmpty() ? -1 : Math.max(0, Math.min(selected, records.size() - 1));
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
        int max = getMaxScroll();
        if (wheel > 0) {
            scroll = Math.max(0, scroll - 1);
        } else {
            scroll = Math.min(max, scroll + 1);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0) {
            return;
        }
        if (inside(mouseX, mouseY, guiLeft + 14, guiTop + 14, 56, 20)) {
            mc.displayGuiScreen(new LOTRGuiMenu());
            return;
        }
        if (inside(mouseX, mouseY, guiLeft + xSize - 74, guiTop + 14, 60, 20)) {
            requestRecords();
            return;
        }
        int listX = guiLeft + 18;
        int listY = guiTop + 78;
        for (int i = 0; i < getVisibleRows() && scroll + i < records.size(); i++) {
            int rowY = listY + i * 32;
            if (inside(mouseX, mouseY, listX, rowY, getListWidth(), 28)) {
                selected = scroll + i;
                return;
            }
        }
    }

    private void requestRecords() {
        rawLines = new ArrayList();
        records = new ArrayList();
        summary = "Loading...";
        scroll = 0;
        selected = -1;
        KOMEPacketHandler.network.sendToServer(new KOMEPacketServerRecordRequest());
    }

    private void drawPanel(int mouseX, int mouseY) {
        Gui.drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, 0xE20B0B0A);
        Gui.drawRect(guiLeft + 5, guiTop + 5, guiLeft + xSize - 5, guiTop + ySize - 5, 0xFFF0D9A6);
        Gui.drawRect(guiLeft + 10, guiTop + 42, guiLeft + xSize - 10, guiTop + 45, 0xFF5D311E);
        drawButton(guiLeft + 14, guiTop + 14, 56, 20, "Menu", inside(mouseX, mouseY, guiLeft + 14, guiTop + 14, 56, 20));
        drawButton(guiLeft + xSize - 74, guiTop + 14, 60, 20, "Refresh", inside(mouseX, mouseY, guiLeft + xSize - 74, guiTop + 14, 60, 20));
        drawCenteredString(fontRendererObj, "KOME Server Records", guiLeft + xSize / 2, guiTop + 18, 0x2B160D);
        fontRendererObj.drawString(summary, guiLeft + 18, guiTop + 52, 0x4A2C0C);

        int listX = guiLeft + 18;
        int listY = guiTop + 78;
        int listW = getListWidth();
        int detailX = listX + listW + 14;
        int detailW = guiLeft + xSize - 18 - detailX;
        drawTable(listX, listY, listW, mouseX, mouseY);
        drawDetail(detailX, listY, detailW);
        drawScrollbar(listX + listW - 8, listY);
    }

    private void drawTable(int x, int y, int width, int mouseX, int mouseY) {
        Gui.drawRect(x, y - 20, x + width, guiTop + ySize - 18, 0x33160E08);
        fontRendererObj.drawString("Player / Rank", x + 8, y - 13, 0x4A2C0C);
        fontRendererObj.drawString("Faction", x + 128, y - 13, 0x4A2C0C);
        if (records.isEmpty()) {
            fontRendererObj.drawString("No player records yet.", x + 12, y + 12, 0x2B160D);
            return;
        }
        for (int i = 0; i < getVisibleRows() && scroll + i < records.size(); i++) {
            Record record = (Record) records.get(scroll + i);
            int rowY = y + i * 32;
            boolean active = selected == scroll + i;
            boolean hover = inside(mouseX, mouseY, x, rowY, width - 12, 28);
            int fill = active ? 0xFF4E321D : hover ? 0xFF7A542F : 0xFF2F2117;
            Gui.drawRect(x, rowY, x + width - 12, rowY + 28, 0xFF160E08);
            Gui.drawRect(x + 1, rowY + 1, x + width - 13, rowY + 27, fill);
            drawFactionBadge(record.faction, x + 6, rowY + 5, 20);
            fontRendererObj.drawString(trim(record.name, 88), x + 30, rowY + 5, 0xFFFFFFFF);
            fontRendererObj.drawString(trim(record.rank, 88), x + 30, rowY + 17, 0xFFD9B56A);
            fontRendererObj.drawString(trim(record.faction, width - 170), x + 128, rowY + 11, 0xFFFFD36A);
        }
    }

    private void drawDetail(int x, int y, int width) {
        Gui.drawRect(x, y - 20, x + width, guiTop + ySize - 18, 0x44281610);
        if (selected < 0 || selected >= records.size()) {
            fontRendererObj.drawString("Select a player to view their record.", x + 14, y + 14, 0x2B160D);
            return;
        }
        Record record = (Record) records.get(selected);
        drawFactionBadge(record.faction, x + 14, y - 12, 20);
        fontRendererObj.drawString(trim(record.name, width - 48), x + 40, y - 14, 0x1B1208);
        fontRendererObj.drawString(trim(record.faction + " - " + record.rank, width - 48), x + 40, y - 2, 0x4A2C0C);

        int cardY = y + 24;
        drawInfoCard("Progression", record.progress + " completed", x + 14, cardY, width - 28);
        drawInfoCard("Population", record.population, x + 14, cardY + 52, width - 28);
        drawInfoCard("Pledged Lord", record.lord, x + 14, cardY + 104, width - 28);
        drawInfoCard("Alliances", record.alliances, x + 14, cardY + 156, width - 28);
        drawInfoCard("Tiles Controlled", record.tileCount + formatNames(record.tiles), x + 14, cardY + 208, width - 28);
    }

    private void drawInfoCard(String title, String value, int x, int y, int width) {
        Gui.drawRect(x, y, x + width, y + 43, 0xFF7A4A25);
        Gui.drawRect(x + 1, y + 1, x + width - 1, y + 42, 0xFFE4C98F);
        fontRendererObj.drawString(title, x + 8, y + 7, 0x4A2C0C);
        List wrapped = fontRendererObj.listFormattedStringToWidth(value == null || value.trim().isEmpty() ? "None" : value, width - 16);
        for (int i = 0; i < wrapped.size() && i < 2; i++) {
            fontRendererObj.drawString(String.valueOf(wrapped.get(i)), x + 8, y + 20 + i * 10, 0x1B1208);
        }
    }

    private void drawButton(int x, int y, int width, int height, String text, boolean hover) {
        Gui.drawRect(x, y, x + width, y + height, 0xFF2B2117);
        Gui.drawRect(x + 1, y + 1, x + width - 1, y + height - 1, hover ? 0xFFE8C46A : 0xFF4B321F);
        int color = hover ? 0xFF1B1208 : 0xFFFFE6A3;
        fontRendererObj.drawString(text, x + (width - fontRendererObj.getStringWidth(text)) / 2, y + 6, color);
    }

    private void drawScrollbar(int x, int y) {
        int rows = getVisibleRows();
        if (records.size() <= rows) {
            return;
        }
        int trackH = rows * 32 - 4;
        Gui.drawRect(x, y, x + 5, y + trackH, 0x662B2117);
        int max = Math.max(1, getMaxScroll());
        int handleH = Math.max(18, trackH * rows / records.size());
        int handleY = y + (trackH - handleH) * scroll / max;
        Gui.drawRect(x, handleY, x + 5, handleY + handleH, 0xFF5D311E);
    }

    private void updateScrollbarDrag(int mouseX, int mouseY) {
        boolean isMouseDown = Mouse.isButtonDown(0);
        int listX = guiLeft + 18;
        int listY = guiTop + 78;
        int scrollX = listX + getListWidth() - 8;
        int trackH = getVisibleRows() * 32 - 4;
        int max = getMaxScroll();
        if (!wasMouseDown && isMouseDown && max > 0 && inside(mouseX, mouseY, scrollX - 2, listY, 9, trackH)) {
            isScrolling = true;
        }
        if (!isMouseDown) {
            isScrolling = false;
        }
        wasMouseDown = isMouseDown;
        if (isScrolling) {
            float amount = (mouseY - listY) / (float) Math.max(1, trackH);
            amount = Math.max(0.0F, Math.min(1.0F, amount));
            scroll = Math.round(amount * max);
        }
    }

    private void drawFactionBadge(String faction, int x, int y, int size) {
        int color = factionColor(faction);
        Gui.drawRect(x, y, x + size, y + size, 0xFF160E08);
        Gui.drawRect(x + 1, y + 1, x + size - 1, y + size - 1, 0xFF000000 | color);
        Gui.drawRect(x + 3, y + 3, x + size - 3, y + size - 3, 0x33160E08);
        String initials = factionInitials(faction);
        int textWidth = fontRendererObj.getStringWidth(initials);
        fontRendererObj.drawString(initials, x + (size - textWidth) / 2, y + 6, 0xFFFFFFFF);
    }

    private int factionColor(String faction) {
        if (faction == null || faction.trim().length() == 0 || "No faction".equals(faction)) {
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
        if (faction == null || faction.trim().length() == 0 || "No faction".equals(faction)) {
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

    private boolean inside(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private int getListWidth() {
        return Math.max(260, Math.min(340, xSize / 2 + 30));
    }

    private int getVisibleRows() {
        return Math.max(5, (ySize - 102) / 32);
    }

    private int getMaxScroll() {
        return Math.max(0, records.size() - getVisibleRows());
    }

    private String trim(String value, int width) {
        value = value == null ? "" : value;
        if (fontRendererObj.getStringWidth(value) <= width) {
            return value;
        }
        String suffix = "...";
        while (value.length() > 0 && fontRendererObj.getStringWidth(value + suffix) > width) {
            value = value.substring(0, value.length() - 1);
        }
        return value + suffix;
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
            faction = parts[3].length() == 0 ? "No faction" : parts[3];
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
}
