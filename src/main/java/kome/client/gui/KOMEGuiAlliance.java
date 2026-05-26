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
    private static final String[] CIVIL_BENEFITS = new String[] {"Alliance begins", "Use faction WPs", "Hire farmhands"};
    private static final String[] MILITARY_BENEFITS = new String[] {"Alliance begins", "Hire 1 unit", "Attack through faction", "Command armies", "Spawn captain"};
    private static final String[] TRADE_BENEFITS = new String[] {"Alliance begins", "Build in faction land", "Produce merchant crop"};
    private static List rawLines = new ArrayList();
    private static List records = new ArrayList();
    private static List factionKeys = new ArrayList();
    private static List factionNames = new ArrayList();
    private static String summary = "Loading...";
    private static String viewerFactionKey = "";
    private static String viewerFactionName = "No pledged faction";

    private int scroll;
    private int selected = -1;
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
        ensureFactions();
        if (!viewerFactionKey.isEmpty()) {
            int index = factionKeys.indexOf(viewerFactionKey);
            if (index >= 0 && receiverIndex == index) {
                receiverIndex = wrap(index + 1, factionKeys.size());
            }
        }
        requestAlliances();
        configureButtons();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        updateScrollbarDrag(mouseX, mouseY);
        configureButtons();
        drawDefaultBackground();
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        mc.getTextureManager().bindTexture(LOTRGuiAchievements.pageTexture);
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, 220, 256);
        drawCenteredString("KOME Alliances", guiLeft + xSize / 2, guiTop - 16, 16777215);
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
        } else if (button.id == 20) {
            receiverIndex = nextReceiver(-1);
        } else if (button.id == 21) {
            receiverIndex = nextReceiver(1);
        } else if (button.id == 22) {
            sendRequestCommand();
        } else if (button.id == 30 && selected >= 0 && selected < records.size()) {
            Record record = (Record) records.get(selected);
            KOMEMinecraftClient.sendChat("/alliance goods " + record.keyA + " " + record.keyB);
            mc.displayGuiScreen(null);
        } else if (button.id == 31 && selected >= 0 && selected < records.size()) {
            Record record = (Record) records.get(selected);
            KOMEMinecraftClient.sendChat("/alliance accept " + record.keyA + " " + record.keyB);
            requestAlliances();
        } else if (button.id == 32 && selected >= 0 && selected < records.size()) {
            Record record = (Record) records.get(selected);
            KOMEMinecraftClient.sendChat("/alliance break " + record.keyA + " " + record.keyB);
            selected = -1;
            requestAlliances();
        } else if (button.id == 40) {
            selectedType = wrap(selectedType - 1, 3);
        } else if (button.id == 41) {
            selectedType = wrap(selectedType + 1, 3);
        } else {
            super.actionPerformed(button);
        }
        configureButtons();
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
            return;
        }
        if (createMode || selected >= 0) {
            return;
        }
        int x = guiLeft + 11;
        int y = guiTop + 48;
        for (int i = 0; i < getVisibleRows() && scroll + i < records.size(); i++) {
            int rowY = y + i * 44;
            if (mouseX >= x && mouseX < x + 186 && mouseY >= rowY && mouseY < rowY + 38) {
                selected = scroll + i;
                scroll = 0;
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
        buttonList.clear();
        buttonList.add(new GuiButton(10, guiLeft + 152, guiTop + 8, 40, 20, createMode ? "List" : "New"));
        if (createMode) {
            buttonList.add(new GuiButton(20, guiLeft + 17, guiTop + 119, 22, 20, "<"));
            buttonList.add(new GuiButton(21, guiLeft + 181, guiTop + 119, 22, 20, ">"));
            GuiButton request = new GuiButton(22, guiLeft + 52, guiTop + 205, 116, 20, "Send Request");
            request.enabled = !viewerFactionKey.isEmpty() && !viewerFactionKey.equals(factionKey(receiverIndex)) && !isEnemyAlliance(viewerFactionKey, factionKey(receiverIndex));
            buttonList.add(request);
        } else if (selected >= 0 && selected < records.size()) {
            Record record = (Record) records.get(selected);
            buttonList.add(new GuiButton(30, guiLeft + 121, guiTop + 212, 68, 20, "Goods"));
            GuiButton accept = new GuiButton(31, guiLeft + 43, guiTop + 212, 68, 20, "Accept");
            accept.enabled = record.hasPending();
            buttonList.add(accept);
            buttonList.add(new GuiButton(32, guiLeft + 121, guiTop + 188, 68, 20, "Break"));
            buttonList.add(new GuiButton(40, guiLeft + 18, guiTop + 60, 22, 20, "<"));
            buttonList.add(new GuiButton(41, guiLeft + 180, guiTop + 60, 22, 20, ">"));
        }
    }

    private void drawList(int mouseX, int mouseY) {
        drawReturnButton(mouseX, mouseY, false);
        mc.fontRenderer.drawString(trim(summary, 132), guiLeft + 34, guiTop + 14, 0x2B2117);
        mc.fontRenderer.drawString("Your faction: " + trim(viewerFactionName, 118), guiLeft + 13, guiTop + 31, 0x4A2C0C);
        if (records.isEmpty()) {
            mc.fontRenderer.drawString("No alliances recorded yet.", guiLeft + 18, guiTop + 62, 0x2B2117);
            mc.fontRenderer.drawString("Use New to send a request.", guiLeft + 18, guiTop + 76, 0x4A2C0C);
            return;
        }
        int x = guiLeft + 11;
        int y = guiTop + 48;
        for (int i = 0; i < getVisibleRows() && scroll + i < records.size(); i++) {
            Record record = (Record) records.get(scroll + i);
            int rowY = y + i * 44;
            boolean hover = mouseX >= x && mouseX < x + 186 && mouseY >= rowY && mouseY < rowY + 38;
            Gui.drawRect(x, rowY, x + 186, rowY + 38, 0xFF160E08);
            Gui.drawRect(x + 1, rowY + 1, x + 185, rowY + 37, hover ? 0xEE4B321F : 0xDD2F2117);
            mc.fontRenderer.drawString(trim(record.factionA, 76), x + 7, rowY + 5, 0xFFFFFFFF);
            mc.fontRenderer.drawString("->", x + 83, rowY + 5, 0xFFFFD36A);
            mc.fontRenderer.drawString(trim(record.factionB, 80), x + 100, rowY + 5, 0xFFFFFFFF);
            mc.fontRenderer.drawString(tierLabel("Civil", record.civilTier), x + 7, rowY + 21, 0xFFE8C46A);
            mc.fontRenderer.drawString(tierLabel("Mil", record.militaryTier), x + 66, rowY + 21, 0xFFFFE6A3);
            mc.fontRenderer.drawString(tierLabel("Trade", record.tradeTier), x + 119, rowY + 21, 0xFFE8C46A);
        }
    }

    private void drawCreate() {
        drawReturnButton(-1, -1, true);
        int x = guiLeft + 18;
        mc.fontRenderer.drawString("Sender faction", x, guiTop + 54, 0x4A2C0C);
        mc.fontRenderer.drawString(trim(viewerFactionName, 178), x, guiTop + 68, viewerFactionKey.isEmpty() ? 0x8A1F0C : 0x1B1208);
        mc.fontRenderer.drawString("Receiving faction", x, guiTop + 102, 0x4A2C0C);
        drawField(guiLeft + 44, guiTop + 121, 132, trim(factionName(receiverIndex), 126));
        if (viewerFactionKey.isEmpty()) {
            mc.fontRenderer.drawString("Pledge to a faction before sending.", x, guiTop + 160, 0x8A1F0C);
        } else if (viewerFactionKey.equals(factionKey(receiverIndex))) {
            mc.fontRenderer.drawString("Choose another faction.", x, guiTop + 160, 0x8A1F0C);
        } else if (isEnemyAlliance(viewerFactionKey, factionKey(receiverIndex))) {
            mc.fontRenderer.drawString("Enemy factions cannot ally.", x, guiTop + 160, 0x8A1F0C);
        } else {
            mc.fontRenderer.drawString("Request starts Civil, Military,", x, guiTop + 154, 0x1B1208);
            mc.fontRenderer.drawString("and Trade at tier 0.", x, guiTop + 168, 0x1B1208);
        }
    }

    private void drawDetail(Record record) {
        drawReturnButton(-1, -1, true);
        int x = guiLeft + 15;
        mc.fontRenderer.drawString(trim(record.factionA, 82), x, guiTop + 36, 0x1B1208);
        mc.fontRenderer.drawString("->", guiLeft + 102, guiTop + 36, 0x4A2C0C);
        mc.fontRenderer.drawString(trim(record.factionB, 82), guiLeft + 119, guiTop + 36, 0x1B1208);
        drawAllianceDetails(record, x, guiTop + 63);
        if (record.lastUpdatedBy.length() > 0) {
            mc.fontRenderer.drawString("Updated by " + trim(record.lastUpdatedBy, 94), x, guiTop + 218, 0x4A2C0C);
        }
    }

    private void drawAllianceDetails(Record record, int x, int y) {
        String title = selectedType == 0 ? "Civil Alliance" : selectedType == 1 ? "Military Alliance" : "Trade Alliance";
        int tier = selectedType == 0 ? record.civilTier : selectedType == 1 ? record.militaryTier : record.tradeTier;
        drawCenteredString(title + " - " + displayTier(tier), guiLeft + xSize / 2, y, 0x4A2C0C);
        String[] benefits = selectedType == 0 ? CIVIL_BENEFITS : selectedType == 1 ? MILITARY_BENEFITS : TRADE_BENEFITS;
        for (int i = 0; i < benefits.length; i++) {
            int rowY = y + 24 + i * 28;
            int color = tier == -2 ? 0x7A4A1C : i <= tier ? 0x1B1208 : 0x806C55;
            mc.fontRenderer.drawString("T" + i + ": " + benefits[i], x, rowY, color);
        }
    }

    private void drawField(int x, int y, int width, String text) {
        Gui.drawRect(x - 2, y - 3, x + width + 2, y + 11, 0x553B250E);
        mc.fontRenderer.drawString(text, x, y, 0x1B1208);
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
        if (createMode) {
            return;
        }
        int size = selected >= 0 ? 0 : records.size();
        int scrollBarX0 = guiLeft + 201;
        int scrollBarY0 = guiTop + 48;
        mc.getTextureManager().bindTexture(LOTRGuiAchievements.iconsTexture);
        if (size > getVisibleRows()) {
            int maxScroll = Math.max(1, getMaxScroll());
            int offset = (int) (scroll / (float) maxScroll * 175.0f);
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
        int scrollBarY0 = guiTop + 48;
        int scrollBarY1 = guiTop + 240;
        if (!wasMouseDown && isMouseDown && maxScroll > 0 && selected < 0 && !createMode && mouseX >= scrollBarX0 && mouseX < scrollBarX1 && mouseY >= scrollBarY0 && mouseY < scrollBarY1) {
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
        return 4;
    }

    private int getScrollStep() {
        return 1;
    }

    private int getMaxScroll() {
        return selected >= 0 || createMode ? 0 : Math.max(0, records.size() - getVisibleRows());
    }

    private int nextReceiver(int direction) {
        if (factionKeys.isEmpty()) {
            return 0;
        }
        int next = receiverIndex;
        for (int i = 0; i < factionKeys.size(); i++) {
            next = wrap(next + direction, factionKeys.size());
            if (!viewerFactionKey.equals(factionKey(next)) && !isEnemyAlliance(viewerFactionKey, factionKey(next))) {
                return next;
            }
        }
        return next;
    }

    private String tierLabel(String label, int tier) {
        return label + ": " + displayTier(tier);
    }

    private String displayTier(int tier) {
        return tier == -2 ? "Pending" : tier < 0 ? "None" : "T" + tier;
    }

    private String getBenefit(String title, int tier) {
        if (tier < 0) {
            return tier == -2 ? "Waiting for the receiving king to accept." : "No alliance of this type.";
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
                return "May command armies with matching units.";
            }
            return "May spawn your captain in their land.";
        }
        return tier == 0 ? "Alliance begins." : tier == 1 ? "May build in that faction's land." : "May add crop trade to produce merchant.";
    }

    private void sendRequestCommand() {
        if (viewerFactionKey.isEmpty() || viewerFactionKey.equals(factionKey(receiverIndex)) || isEnemyAlliance(viewerFactionKey, factionKey(receiverIndex))) {
            return;
        }
        KOMEMinecraftClient.sendChat("/alliance request " + viewerFactionKey + " " + factionKey(receiverIndex));
        createMode = false;
        requestAlliances();
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

    private static boolean isEnemyAlliance(String factionA, String factionB) {
        LOTRFaction a = LOTRFaction.forName(factionA);
        LOTRFaction b = LOTRFaction.forName(factionB);
        return a != null && b != null && (a.isMortalEnemy(b) || b.isMortalEnemy(a) || a.isBadRelation(b) || b.isBadRelation(a));
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
            } else if ("VIEWER".equals(parts[0]) && parts.length >= 3) {
                viewerFactionKey = parts[1];
                viewerFactionName = parts[2].length() == 0 ? "No pledged faction" : parts[2];
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
        private final String keyA;
        private final String keyB;
        private final String factionA;
        private final String factionB;
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

        private boolean hasPending() {
            return civilTier == -2 || militaryTier == -2 || tradeTier == -2;
        }
    }
}
