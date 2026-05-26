package kome.client.gui;

import kome.client.KOMEMinecraftClient;
import kome.common.network.KOMEPacketAllianceRequest;
import kome.common.network.KOMEPacketHandler;
import lotr.client.gui.LOTRGuiAchievements;
import lotr.client.gui.LOTRGuiMenu;
import lotr.client.gui.LOTRGuiMenuBase;
import lotr.common.fac.LOTRFaction;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

public class KOMEGuiAlliance extends LOTRGuiMenuBase {
    private static List rawLines = new ArrayList();
    private static List records = new ArrayList();
    private static List factionKeys = new ArrayList();
    private static List factionNames = new ArrayList();
    private static String summary = "Loading...";
    private int scroll;
    private int selected = -1;
    private int senderIndex;
    private int receiverIndex = 1;
    private int selectedType;
    private boolean createMode;
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
        buttonMenuReturn = null;
        configureButtons();
        requestAlliances();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        updateScrollbarDrag(mouseX, mouseY);
        configureButtons();
        drawDefaultBackground();
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        mc.getTextureManager().bindTexture(LOTRGuiAchievements.pageTexture);
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, 220, 256);
        drawCenteredString("KOME Alliances", guiLeft + xSize / 2, guiTop - 20, 16777215);
        if (createMode) {
            drawCreate();
        } else if (selected >= 0 && selected < records.size()) {
            drawDetail((Record) records.get(selected));
        } else {
            drawList(mouseX, mouseY);
        }
        drawScrollbar();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public void actionPerformed(GuiButton button) {
        if (!button.enabled) {
            return;
        }
        if (button.id == 10) {
            createMode = !createMode;
            selected = -1;
            scroll = 0;
            configureButtons();
        } else if (button.id == 20) {
            senderIndex = wrap(senderIndex - 1, factionKeys.size());
        } else if (button.id == 21) {
            senderIndex = wrap(senderIndex + 1, factionKeys.size());
        } else if (button.id == 22) {
            receiverIndex = wrap(receiverIndex - 1, factionKeys.size());
        } else if (button.id == 23) {
            receiverIndex = wrap(receiverIndex + 1, factionKeys.size());
        } else if (button.id == 24) {
            selectedType = (selectedType + 1) % 3;
        } else if (button.id == 25) {
            sendRequestCommand();
        } else if (button.id == 30 && selected >= 0 && selected < records.size()) {
            Record record = (Record) records.get(selected);
            KOMEMinecraftClient.sendChat("/alliance goods " + record.keyA + " " + record.keyB);
            mc.displayGuiScreen(null);
        } else if (button.id >= 31 && button.id <= 33 && selected >= 0 && selected < records.size()) {
            Record record = (Record) records.get(selected);
            String type = button.id == 31 ? "civil" : button.id == 32 ? "military" : "trade";
            KOMEMinecraftClient.sendChat("/alliance accept " + type + " " + record.keyA + " " + record.keyB);
            requestAlliances();
        } else {
            super.actionPerformed(button);
        }
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
        boolean overButton = isOverGuiButton(mouseX, mouseY);
        super.mouseClicked(mouseX, mouseY, button);
        if (overButton) {
            return;
        }
        if (mouseX >= guiLeft + 8 && mouseX < guiLeft + 25 && mouseY >= guiTop + 8 && mouseY < guiTop + 25) {
            if (createMode) {
                createMode = false;
            } else if (selected >= 0) {
                selected = -1;
                scroll = 0;
            } else {
                mc.displayGuiScreen(new LOTRGuiMenu());
            }
            configureButtons();
            return;
        }
        if (createMode || selected >= 0) {
            return;
        }
        int rowHeight = 36;
        int x0 = guiLeft + 9;
        int y0 = guiTop + 42;
        for (int i = 0; i < getVisibleRows() && scroll + i < records.size(); i++) {
            int y = y0 + i * rowHeight;
            if (mouseX >= x0 && mouseX < x0 + 190 && mouseY >= y && mouseY < y + 32) {
                selected = scroll + i;
                scroll = 0;
                configureButtons();
                return;
            }
        }
    }

    private void requestAlliances() {
        rawLines = new ArrayList();
        records = new ArrayList();
        summary = "Loading...";
        selected = -1;
        scroll = 0;
        KOMEPacketHandler.network.sendToServer(new KOMEPacketAllianceRequest());
    }

    private void configureButtons() {
        ensureFactions();
        buttonList.clear();
        buttonList.add(new GuiButton(10, guiLeft + 144, guiTop + 8, 48, 20, createMode ? "List" : "New"));
        if (createMode) {
            buttonList.add(new GuiButton(20, guiLeft + 15, guiTop + 78, 22, 20, "<"));
            buttonList.add(new GuiButton(21, guiLeft + 181, guiTop + 78, 22, 20, ">"));
            buttonList.add(new GuiButton(22, guiLeft + 15, guiTop + 124, 22, 20, "<"));
            buttonList.add(new GuiButton(23, guiLeft + 181, guiTop + 124, 22, 20, ">"));
            buttonList.add(new GuiButton(24, guiLeft + 46, guiTop + 166, 128, 20, typeName()));
            GuiButton request = new GuiButton(25, guiLeft + 55, guiTop + 206, 110, 20, "Send Request");
            request.enabled = senderIndex != receiverIndex && !factionKeys.isEmpty();
            buttonList.add(request);
        } else if (selected >= 0 && selected < records.size()) {
            Record record = (Record) records.get(selected);
            buttonList.add(new GuiButton(30, guiLeft + 118, guiTop + 208, 70, 20, "Goods"));
            GuiButton civil = new GuiButton(31, guiLeft + 28, guiTop + 62, 54, 16, "Accept");
            GuiButton military = new GuiButton(32, guiLeft + 28, guiTop + 108, 54, 16, "Accept");
            GuiButton trade = new GuiButton(33, guiLeft + 28, guiTop + 164, 54, 16, "Accept");
            civil.enabled = record.civilTier == -2;
            military.enabled = record.militaryTier == -2;
            trade.enabled = record.tradeTier == -2;
            buttonList.add(civil);
            buttonList.add(military);
            buttonList.add(trade);
        }
    }

    private void drawList(int mouseX, int mouseY) {
        drawReturnButton(mouseX, mouseY, false);
        mc.fontRenderer.drawString(trim(summary, 186), guiLeft + 12, guiTop + 30, 0x2B2117);
        if (records.isEmpty()) {
            mc.fontRenderer.drawString("No alliances recorded yet.", guiLeft + 18, guiTop + 56, 0x2B2117);
            mc.fontRenderer.drawString("Use New to send a request.", guiLeft + 18, guiTop + 70, 0x4A2C0C);
            return;
        }
        int rowHeight = 36;
        int x = guiLeft + 9;
        int y = guiTop + 42;
        for (int i = 0; i < getVisibleRows() && scroll + i < records.size(); i++) {
            Record record = (Record) records.get(scroll + i);
            int rowY = y + i * rowHeight;
            boolean hover = mouseX >= x && mouseX < x + 190 && mouseY >= rowY && mouseY < rowY + 32;
            int fill = hover ? 0xEE4B321F : 0xDD2F2117;
            Gui.drawRect(x, rowY, x + 190, rowY + 32, 0xFF160E08);
            Gui.drawRect(x + 1, rowY + 1, x + 189, rowY + 31, fill);
            mc.fontRenderer.drawString(trim(record.factionA, 82), x + 8, rowY + 5, 0xFFFFFFFF);
            mc.fontRenderer.drawString("->", x + 91, rowY + 5, 0xFFFFD36A);
            mc.fontRenderer.drawString(trim(record.factionB, 82), x + 108, rowY + 5, 0xFFFFFFFF);
            mc.fontRenderer.drawString(tierLabel("Civil", record.civilTier), x + 8, rowY + 19, 0xFFE8C46A);
            mc.fontRenderer.drawString(tierLabel("Mil", record.militaryTier), x + 70, rowY + 19, 0xFFFFE6A3);
            mc.fontRenderer.drawString(tierLabel("Trade", record.tradeTier), x + 126, rowY + 19, 0xFFE8C46A);
        }
    }

    private void drawCreate() {
        drawReturnButton(-1, -1, true);
        int x = guiLeft + 18;
        mc.fontRenderer.drawString("Sender faction", x, guiTop + 60, 0x4A2C0C);
        mc.fontRenderer.drawString(trim(factionName(senderIndex), 134), guiLeft + 44, guiTop + 84, 0x1B1208);
        mc.fontRenderer.drawString("Receiving faction", x, guiTop + 106, 0x4A2C0C);
        mc.fontRenderer.drawString(trim(factionName(receiverIndex), 134), guiLeft + 44, guiTop + 130, 0x1B1208);
        mc.fontRenderer.drawString("Alliance type", x, guiTop + 152, 0x4A2C0C);
        if (senderIndex == receiverIndex) {
            mc.fontRenderer.drawString("Choose two different factions.", x, guiTop + 190, 0x8A1F0C);
        } else {
            mc.fontRenderer.drawString("Request creates one-way access.", x, guiTop + 190, 0x1B1208);
        }
    }

    private void drawDetail(Record record) {
        drawReturnButton(-1, -1, true);
        int x = guiLeft + 16;
        int y = guiTop + 38 - scroll * 10;
        mc.fontRenderer.drawString(trim(record.factionA, 86), x, y, 0x1B1208);
        mc.fontRenderer.drawString("->", guiLeft + 107, y, 0x4A2C0C);
        mc.fontRenderer.drawString(trim(record.factionB, 86), guiLeft + 123, y, 0x1B1208);
        drawAllianceSection("Civil Alliance", record.civilTier, x, y + 28);
        drawAllianceSection("Military Alliance", record.militaryTier, x, y + 74);
        drawAllianceSection("Trade Alliance", record.tradeTier, x, y + 130);
        if (record.lastUpdatedBy.length() > 0) {
            mc.fontRenderer.drawString("Updated by " + trim(record.lastUpdatedBy, 120), x, y + 190, 0x4A2C0C);
        }
    }

    private void drawAllianceSection(String title, int tier, int x, int y) {
        mc.fontRenderer.drawString(title, x, y, 0x4A2C0C);
        mc.fontRenderer.drawString(displayTier(tier), x, y + 12, 0x1B1208);
        List wrapped = mc.fontRenderer.listFormattedStringToWidth(getBenefit(title, tier), 178);
        for (int i = 0; i < wrapped.size() && i < 3; i++) {
            mc.fontRenderer.drawString(String.valueOf(wrapped.get(i)), x, y + 24 + i * 10, 0x1B1208);
        }
    }

    private void drawReturnButton(int mouseX, int mouseY, boolean backToList) {
        int x = guiLeft + 8;
        int y = guiTop + 8;
        boolean hover = mouseX >= x && mouseX < x + 17 && mouseY >= y && mouseY < y + 17;
        Gui.drawRect(x, y, x + 17, y + 17, 0xFF2B2117);
        Gui.drawRect(x + 1, y + 1, x + 16, y + 16, hover ? 0xFFE8C46A : 0xFFC8A85E);
        mc.fontRenderer.drawString(backToList ? "<" : "M", x + (backToList ? 6 : 4), y + 5, 0xFF1B1208);
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
        if (!wasMouseDown && isMouseDown && maxScroll > 0 && mouseX >= scrollBarX0 && mouseX < scrollBarX1 && mouseY >= scrollBarY0 && mouseY < scrollBarY1) {
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
        return selected >= 0 ? 18 : getVisibleRows();
    }

    private int getDetailLineCount() {
        if (selected < 0 || selected >= records.size()) {
            return 0;
        }
        Record record = (Record) records.get(selected);
        return 8 + getBenefitLineCount("Civil Alliance", record.civilTier) + getBenefitLineCount("Military Alliance", record.militaryTier) + getBenefitLineCount("Trade Alliance", record.tradeTier);
    }

    private int getBenefitLineCount(String title, int tier) {
        return Math.max(1, mc.fontRenderer.listFormattedStringToWidth(getBenefit(title, tier), 178).size()) + 2;
    }

    private int getScrollStep() {
        return selected >= 0 ? 3 : 1;
    }

    private int getMaxScroll() {
        return selected >= 0 ? Math.max(0, getDetailLineCount() - getVisibleCount()) : Math.max(0, records.size() - getVisibleRows());
    }

    private String tierLabel(String label, int tier) {
        return label + ": " + displayTier(tier);
    }

    private String displayTier(int tier) {
        return tier == -2 ? "Pending" : tier < 0 ? "None" : "Tier " + tier;
    }

    private String getBenefit(String title, int tier) {
        if (tier < 0) {
            return tier == -2 ? "Waiting for the receiving faction to accept this request." : "No alliance of this type has been recorded.";
        }
        if (title.startsWith("Civil")) {
            return tier == 0 ? "Alliance begins." : tier == 1 ? "May use faction waypoints." : "May hire farmhands.";
        }
        if (title.startsWith("Military")) {
            if (tier == 0) {
                return "Alliance begins.";
            }
            if (tier == 1) {
                return "May hire 1 unit from that faction.";
            }
            if (tier == 2) {
                return "May attack through that faction.";
            }
            if (tier == 3) {
                return "May command the faction's armies while with units of that faction.";
            }
            return "May spawn your captain in that faction's land.";
        }
        return tier == 0 ? "Alliance begins." : tier == 1 ? "May build in that faction's land." : "May add a new crop trade to a produce merchant.";
    }

    private void sendRequestCommand() {
        if (factionKeys.isEmpty() || senderIndex == receiverIndex) {
            return;
        }
        KOMEMinecraftClient.sendChat("/alliance request " + typeKey() + " " + factionKey(senderIndex) + " " + factionKey(receiverIndex));
        createMode = false;
        requestAlliances();
    }

    private String typeKey() {
        return selectedType == 0 ? "civil" : selectedType == 1 ? "military" : "trade";
    }

    private String typeName() {
        return selectedType == 0 ? "Civil Alliance" : selectedType == 1 ? "Military Alliance" : "Trade Alliance";
    }

    private String factionKey(int index) {
        ensureFactions();
        return factionKeys.isEmpty() ? "" : String.valueOf(factionKeys.get(wrap(index, factionKeys.size())));
    }

    private String factionName(int index) {
        ensureFactions();
        return factionNames.isEmpty() ? "" : String.valueOf(factionNames.get(wrap(index, factionNames.size())));
    }

    private static int wrap(int index, int size) {
        if (size <= 0) {
            return 0;
        }
        while (index < 0) {
            index += size;
        }
        return index % size;
    }

    private static void ensureFactions() {
        if (!factionKeys.isEmpty()) {
            return;
        }
        for (LOTRFaction faction : LOTRFaction.values()) {
            if (faction != null && faction.isPlayableAlignmentFaction()) {
                factionKeys.add(faction.codeName());
                factionNames.add(faction.factionName());
            }
        }
    }

    private boolean isOverGuiButton(int mouseX, int mouseY) {
        for (Object object : buttonList) {
            GuiButton button = (GuiButton) object;
            if (button.visible && mouseX >= button.xPosition && mouseY >= button.yPosition && mouseX < button.xPosition + button.width && mouseY < button.yPosition + button.height) {
                return true;
            }
        }
        return false;
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

    private static void parseRecords() {
        records = new ArrayList();
        summary = "Alliances: 0";
        for (Object object : rawLines) {
            String line = String.valueOf(object);
            String[] parts = line.split("\t", -1);
            if (parts.length == 0) {
                continue;
            }
            if ("SUMMARY".equals(parts[0]) && parts.length >= 2) {
                summary = "Alliances: " + parts[1];
            } else if ("ALLIANCE".equals(parts[0]) && parts.length >= 10) {
                records.add(new Record(parts));
            }
        }
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static class Record {
        private final String factionA;
        private final String factionB;
        private final String keyA;
        private final String keyB;
        private final int civilTier;
        private final int militaryTier;
        private final int tradeTier;
        private final String lastUpdatedBy;

        private Record(String[] parts) {
            keyA = parts[1];
            keyB = parts[2];
            factionA = parts[3];
            factionB = parts[4];
            civilTier = parseInt(parts[5]);
            militaryTier = parseInt(parts[6]);
            tradeTier = parseInt(parts[7]);
            lastUpdatedBy = parts[8];
        }
    }
}
