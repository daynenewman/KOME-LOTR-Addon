package kome.client.gui;

import kome.common.network.KOMEPacketHandler;
import kome.common.network.KOMEPacketServerRecordRequest;
import lotr.client.gui.LOTRGuiAchievements;
import lotr.client.gui.LOTRGuiMenu;
import lotr.client.gui.LOTRGuiMenuBase;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

public class KOMEGuiServerRecords extends LOTRGuiMenuBase {
    private static List rawLines = new ArrayList();
    private static List records = new ArrayList();
    private static String summary = "Loading...";
    private int scroll;
    private int selected = -1;
    private boolean isScrolling;
    private boolean wasMouseDown;

    public static void update(List updatedLines) {
        rawLines = updatedLines == null ? new ArrayList() : new ArrayList(updatedLines);
        parseRecords();
    }

    @Override
    public void initGui() {
        xSize = 220;
        ySize = 256;
        super.initGui();
        buttonList.clear();
        buttonMenuReturn = null;
        requestRecords();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        updateScrollbarDrag(mouseX, mouseY);
        drawDefaultBackground();
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        mc.getTextureManager().bindTexture(LOTRGuiAchievements.pageTexture);
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, 220, 256);
        drawCenteredString("KOME Server Records", guiLeft + xSize / 2, guiTop - 20, 16777215);
        if (selected >= 0 && selected < records.size()) {
            drawDetail((Record) records.get(selected));
        } else {
            drawList(mouseX, mouseY);
        }
        drawScrollbar();
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }
        int maxScroll = getMaxScroll();
        if (wheel > 0) {
            scroll = Math.max(0, scroll - getScrollStep());
        } else {
            scroll = Math.min(maxScroll, scroll + getScrollStep());
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0) {
            return;
        }
        if (mouseX >= guiLeft + 8 && mouseX < guiLeft + 25 && mouseY >= guiTop + 8 && mouseY < guiTop + 25) {
            mc.displayGuiScreen(new LOTRGuiMenu());
            return;
        }
        if (selected >= 0) {
            if (mouseX >= guiLeft + 9 && mouseX < guiLeft + 199 && mouseY >= guiTop + 32 && mouseY < guiTop + 65) {
                selected = -1;
                scroll = 0;
            }
            return;
        }
        int rowHeight = 34;
        int x0 = guiLeft + 9;
        int y0 = guiTop + 42;
        for (int i = 0; i < getVisibleRows() && scroll + i < records.size(); i++) {
            int y = y0 + i * rowHeight;
            if (mouseX >= x0 && mouseX < x0 + 190 && mouseY >= y && mouseY < y + 30) {
                selected = scroll + i;
                scroll = 0;
                return;
            }
        }
    }

    private void requestRecords() {
        rawLines = new ArrayList();
        records = new ArrayList();
        summary = "Loading...";
        selected = -1;
        scroll = 0;
        KOMEPacketHandler.network.sendToServer(new KOMEPacketServerRecordRequest());
    }

    private void drawList(int mouseX, int mouseY) {
        drawReturnButton(mouseX, mouseY);
        mc.fontRenderer.drawString(trim(summary, 186), guiLeft + 12, guiTop + 30, 0x2B2117);
        if (records.isEmpty()) {
            mc.fontRenderer.drawString("No player records yet.", guiLeft + 18, guiTop + 56, 0x2B2117);
            return;
        }
        int rowHeight = 34;
        int x = guiLeft + 9;
        int y = guiTop + 42;
        for (int i = 0; i < getVisibleRows() && scroll + i < records.size(); i++) {
            Record record = (Record) records.get(scroll + i);
            int rowY = y + i * rowHeight;
            boolean hover = mouseX >= x && mouseX < x + 190 && mouseY >= rowY && mouseY < rowY + 30;
            int fill = hover ? 0xEE4B321F : 0xDD2F2117;
            Gui.drawRect(x, rowY, x + 190, rowY + 30, 0xFF160E08);
            Gui.drawRect(x + 1, rowY + 1, x + 189, rowY + 29, fill);
            drawPlayerHead(record.name, x + 5, rowY + 6);
            mc.fontRenderer.drawString(trim(record.name, 72), x + 27, rowY + 5, 0xFFFFFFFF);
            mc.fontRenderer.drawString(trim(record.faction, 92), x + 27, rowY + 17, 0xFFFFD36A);
            mc.fontRenderer.drawString(trim(record.rank, 42), x + 135, rowY + 5, 0xFFFFE6A3);
            mc.fontRenderer.drawString(trim(record.progress, 34) + " " + record.tileCount + "t", x + 135, rowY + 17, 0xFFFFFFFF);
        }
    }

    private void drawDetail(Record record) {
        drawReturnButton(-1, -1);
        int headerY = guiTop + 52;
        drawPlayerHead(record.name, guiLeft + 16, headerY);
        mc.fontRenderer.drawString(trim(record.name, 150), guiLeft + 40, headerY - 1, 0x1B1208);
        mc.fontRenderer.drawString(trim(record.faction + " - " + record.rank, 150), guiLeft + 40, headerY + 11, 0x3B250E);

        int x = guiLeft + 16;
        int y = guiTop + 88 - scroll * 10;
        drawSection("Population", record.population, x, y);
        drawSection("Progression", record.progress + " completed", x, y + 34);
        drawSection("Tiles controlled", record.tileCount + formatNames(record.tiles), x, y + 68);
        drawSection("Territories controlled", record.territoryCount + formatNames(record.territories), x, y + 112);
    }

    private void drawReturnButton(int mouseX, int mouseY) {
        int x = guiLeft + 8;
        int y = guiTop + 8;
        boolean hover = mouseX >= x && mouseX < x + 17 && mouseY >= y && mouseY < y + 17;
        Gui.drawRect(x, y, x + 17, y + 17, 0xFF2B2117);
        Gui.drawRect(x + 1, y + 1, x + 16, y + 16, hover ? 0xFFE8C46A : 0xFFC8A85E);
        mc.fontRenderer.drawString("<", x + 6, y + 5, 0xFF1B1208);
    }

    private void drawSection(String title, String value, int x, int y) {
        mc.fontRenderer.drawString(title, x, y, 0x4A2C0C);
        List wrapped = mc.fontRenderer.listFormattedStringToWidth(value == null || value.length() == 0 ? "None" : value, 178);
        for (int i = 0; i < wrapped.size() && i < 3; i++) {
            mc.fontRenderer.drawString(String.valueOf(wrapped.get(i)), x, y + 12 + i * 10, 0x1B1208);
        }
    }

    private String formatNames(String names) {
        return names == null || names.trim().isEmpty() ? "" : " - " + names;
    }

    private void drawPlayerHead(String playerName, int x, int y) {
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        ResourceLocation skin = AbstractClientPlayer.getLocationSkin(playerName);
        AbstractClientPlayer.getDownloadImageSkin(skin, playerName);
        mc.getTextureManager().bindTexture(skin);
        func_152125_a(x, y, 8.0f, 8.0f, 8, 8, 16, 16, 64.0f, 64.0f);
        func_152125_a(x, y, 40.0f, 8.0f, 8, 8, 16, 16, 64.0f, 64.0f);
    }

    private String trim(String value, int width) {
        value = value == null ? "" : value;
        if (mc.fontRenderer.getStringWidth(value) <= width) {
            return value;
        }
        String suffix = "...";
        while (value.length() > 0 && mc.fontRenderer.getStringWidth(value + suffix) > width) {
            value = value.substring(0, value.length() - 1);
        }
        return value + suffix;
    }

    private void drawScrollbar() {
        int size = selected >= 0 ? getDetailLineCount() : records.size();
        int scrollBarX0 = guiLeft + 201;
        int scrollBarY0 = guiTop + 42;
        mc.getTextureManager().bindTexture(LOTRGuiAchievements.iconsTexture);
        if (size > getVisibleCount()) {
            int maxScroll = Math.max(1, getMaxScroll());
            int offset = (int) (scroll / (float) maxScroll * 181.0f);
            drawTexturedModalRect(scrollBarX0, scrollBarY0 + offset, 190, 0, 10, 17);
        } else {
            drawTexturedModalRect(scrollBarX0, scrollBarY0, 200, 0, 10, 17);
        }
    }

    private void updateScrollbarDrag(int mouseX, int mouseY) {
        boolean isMouseDown = Mouse.isButtonDown(0);
        int maxScroll = getMaxScroll();
        int scrollBarX0 = guiLeft + 201;
        int scrollBarX1 = scrollBarX0 + 12;
        int scrollBarY0 = guiTop + 42;
        int scrollBarY1 = guiTop + 246;
        if (!wasMouseDown && isMouseDown && maxScroll > 0 && selected < 0 && mouseX >= scrollBarX0 && mouseX < scrollBarX1 && mouseY >= scrollBarY0 && mouseY < scrollBarY1) {
            isScrolling = true;
        }
        if (!isMouseDown) {
            isScrolling = false;
        }
        wasMouseDown = isMouseDown;
        if (isScrolling) {
            float currentScroll = (mouseY - scrollBarY0 - 8.5f) / (scrollBarY1 - scrollBarY0 - 17.0f);
            currentScroll = Math.max(0.0f, Math.min(1.0f, currentScroll));
            scroll = Math.round(currentScroll * maxScroll);
        }
    }

    private int getVisibleRows() {
        return 5;
    }

    private int getVisibleCount() {
        return selected >= 0 ? 14 : getVisibleRows();
    }

    private int getDetailLineCount() {
        if (selected < 0 || selected >= records.size()) {
            return 0;
        }
        Record record = (Record) records.get(selected);
        int lines = 8;
        lines += getWrappedLineCount(record.population);
        lines += getWrappedLineCount(record.progress + " completed");
        lines += getWrappedLineCount(record.tileCount + formatNames(record.tiles));
        lines += getWrappedLineCount(record.territoryCount + formatNames(record.territories));
        return lines;
    }

    private int getWrappedLineCount(String value) {
        return Math.max(1, mc.fontRenderer.listFormattedStringToWidth(value == null || value.length() == 0 ? "None" : value, 178).size());
    }

    private int getScrollStep() {
        return selected >= 0 ? 3 : 1;
    }

    private int getMaxScroll() {
        return selected >= 0 ? Math.max(0, getDetailLineCount() - getVisibleCount()) : Math.max(0, records.size() - getVisibleRows());
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
            if ("SUMMARY".equals(parts[0]) && parts.length >= 4) {
                summary = "Players: " + parts[1] + " | Territories: " + parts[2] + " | Tiles: " + parts[3];
            } else if ("PLAYER".equals(parts[0]) && parts.length >= 11) {
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
        private final String tileCount;
        private final String territoryCount;
        private final String tiles;
        private final String territories;

        private Record(String[] parts) {
            uuid = parts[1];
            name = parts[2];
            faction = parts[3].length() == 0 ? "No faction" : parts[3];
            rank = parts[4];
            progress = parts[5];
            population = parts[6];
            tileCount = parts[7];
            territoryCount = parts[8];
            tiles = parts[9];
            territories = parts[10];
        }
    }
}
