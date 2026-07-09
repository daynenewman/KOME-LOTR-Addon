package kome.client.gui;

import kome.client.KOMEMinecraftClient;
import kome.common.data.KOMEAlliance;
import kome.common.data.KOMEAllianceInventory;
import kome.common.network.KOMEPacketAllianceRequest;
import kome.common.network.KOMEPacketHandler;
import lotr.client.gui.LOTRGuiMenu;
import lotr.client.gui.LOTRGuiMenuBase;
import lotr.common.fac.LOTRFaction;
import lotr.common.fac.LOTRFactionRelations;
import net.minecraft.client.gui.GuiButton;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.List;

public class KOMEGuiAlliance extends LOTRGuiMenuBase {
    private static final String[] TYPES = KOMEAlliancePermissions.TYPES;
    private static final String[][] BENEFITS = KOMEAlliancePermissions.UNLOCKS;
    private static List rawLines = new ArrayList();
    private static List records = new ArrayList();
    private static List factionKeys = new ArrayList();
    private static List factionNames = new ArrayList();
    private static String summary = "Alliances: 0";
    private static String viewerFactionKey = "";
    private static String viewerFactionName = "No pledged faction";
    private static boolean viewerIsKing;
    private static boolean viewerFactionHasKing;

    private int selected = -1;
    private int selectedType;
    private int createType;
    private int receiverIndex = 1;
    private int scroll;
    private int detailScroll;
    private boolean createMode;
    private boolean permissionMode;

    public static void update(List updatedLines) {
        rawLines = updatedLines == null ? new ArrayList() : new ArrayList(updatedLines);
        parseRecords();
    }

    public static void resetData() {
        rawLines = new ArrayList();
        records = new ArrayList();
        summary = "Alliances: 0";
        viewerFactionKey = "";
        viewerFactionName = "No pledged faction";
        viewerIsKing = false;
        viewerFactionHasKing = false;
    }

    @Override
    public void initGui() {
        xSize = Math.min(700, width - 28);
        ySize = Math.min(380, height - 20);
        super.initGui();
        buttonMenuReturn = null;
        ensureFactions();
        if (!viewerFactionKey.isEmpty()) {
            int viewerIndex = factionKeys.indexOf(viewerFactionKey);
            if (viewerIndex >= 0 && receiverIndex == viewerIndex) {
                receiverIndex = wrap(receiverIndex + 1, factionKeys.size());
            }
        }
        requestAlliances();
        configureButtons();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        configureButtons();
        drawDefaultBackground();
        KOMEGuiTheme.drawMainPanel(guiLeft, guiTop, xSize, ySize);
        drawHeader();
        if (createMode) {
            drawCreate(mouseX, mouseY);
        } else {
            drawList(mouseX, mouseY);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
        drawButtonTooltip(mouseX, mouseY);
    }

    @Override
    public void actionPerformed(GuiButton button) {
        if (!button.enabled) {
            return;
        }
        if (button.id == 1) {
            mc.displayGuiScreen(new LOTRGuiMenu());
        } else if (button.id == 2) {
            createMode = !createMode;
            selected = -1;
            scroll = 0;
            detailScroll = 0;
            if (createMode) {
                receiverIndex = ensureReceiverIndex(receiverIndex, 1);
            }
        } else if (button.id == 3) {
            receiverIndex = nextReceiver(-1);
        } else if (button.id == 4) {
            receiverIndex = nextReceiver(1);
        } else if (button.id == 5) {
            sendRequestCommand();
        } else if (button.id == 6) {
            createType = wrap(createType - 1, TYPES.length);
            receiverIndex = ensureReceiverIndex(receiverIndex, -1);
        } else if (button.id == 7) {
            createType = wrap(createType + 1, TYPES.length);
            receiverIndex = ensureReceiverIndex(receiverIndex, 1);
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
        int mouseX = Mouse.getEventX() * width / mc.displayWidth;
        int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        int max = Math.max(0, records.size() - getVisibleRows());
        if (wheel > 0) {
            scroll = Math.max(0, scroll - 1);
        } else if (wheel < 0) {
            scroll = Math.min(max, scroll + 1);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        if (button != 0 || createMode) {
            return;
        }
        int x = getListX();
        int y = getListY() + 22;
        for (int i = 0; i < getVisibleRows() && scroll + i < records.size(); i++) {
            int rowY = y + i * getRowHeight();
            if (KOMEGuiTheme.isHovered(mouseX, mouseY, x + 5, rowY, getListWidth() - 18, getRowHeight() - 6)) {
                mc.displayGuiScreen(new KOMEGuiAllianceDetail((Record) records.get(scroll + i)));
                return;
            }
        }
    }

    private void configureButtons() {
        buttonList.clear();
        buttonList.add(KOMEGuiButton.small(1, guiLeft + 14, guiTop + 14, "Menu"));
        buttonList.add(KOMEGuiButton.small(2, guiLeft + xSize - 68, guiTop + 14, createMode ? "List" : "New"));
        if (createMode) {
            receiverIndex = ensureReceiverIndex(receiverIndex, 1);
            int center = guiLeft + xSize / 2;
            int formW = Math.min(500, xSize - 88);
            int formX = center - formW / 2;
            int formY = getContentY() + 6;
            int formH = getContentHeight() - 12;
            int cardX = formX + 32;
            int cardW = formW - 64;
            int typeY = formY + 100;
            int receiverY = formY + 154;
            buttonList.add(new KOMEGuiButton(6, cardX + 14, typeY + 24, 30, 20, "<"));
            buttonList.add(new KOMEGuiButton(7, cardX + cardW - 44, typeY + 24, 30, 20, ">"));
            GuiButton receiverLeft = new KOMEGuiButton(3, cardX + 14, receiverY + 24, 30, 20, "<");
            receiverLeft.enabled = hasSelectableReceiver(createType);
            buttonList.add(receiverLeft);
            GuiButton receiverRight = new KOMEGuiButton(4, cardX + cardW - 44, receiverY + 24, 30, 20, ">");
            receiverRight.enabled = hasSelectableReceiver(createType);
            buttonList.add(receiverRight);
            GuiButton send = new KOMEGuiButton(5, center - 86, formY + formH - 34, 172, 24, "Send Request");
            send.enabled = canSendRequest();
            buttonList.add(send);
            return;
        }
    }

    private void drawHeader() {
        KOMEGuiTheme.drawHeader(fontRendererObj, "Alliances", guiLeft + 126, guiTop + 13, xSize - 252);
        int metaY = guiTop + 48;
        int leftW = xSize / 2 - 42;
        fontRendererObj.drawString("Your Faction: " + KOMEGuiTheme.trimToWidth(fontRendererObj, viewerFactionName, leftW - 74), guiLeft + 22, metaY, KOMEGuiTheme.COLOR_TEXT);
        fontRendererObj.drawString("King: " + (viewerIsKing ? "Yes" : "No"), guiLeft + xSize - 94, metaY, viewerIsKing ? KOMEGuiTheme.COLOR_GOOD : KOMEGuiTheme.COLOR_TEXT_MUTED);
        fontRendererObj.drawString(summary, guiLeft + xSize / 2 - fontRendererObj.getStringWidth(summary) / 2, metaY, KOMEGuiTheme.COLOR_TEXT_MUTED);
    }

    private void drawList(int mouseX, int mouseY) {
        int x = getListX();
        int y = getListY();
        int width = getListWidth();
        int height = getContentHeight();
        KOMEGuiTheme.drawSubPanel(x, y, width, height);
        KOMEGuiTheme.drawSectionTitle(fontRendererObj, "Alliance Records", x + 14, y + 10, width - 28);
        if (records.isEmpty()) {
            KOMEGuiTheme.drawWrappedText(fontRendererObj, "No alliance records found.", x + 18, y + 42, width - 36, KOMEGuiTheme.COLOR_TEXT_MUTED);
            return;
        }
        int rowStart = y + 28;
        KOMEGuiTheme.enableScissor(mc, x + 1, rowStart, width - 3, height - 34);
        for (int i = 0; i < getVisibleRows() && scroll + i < records.size(); i++) {
            Record record = (Record) records.get(scroll + i);
            int rowY = rowStart + i * getRowHeight();
            boolean active = selected == scroll + i;
            boolean hover = KOMEGuiTheme.isHovered(mouseX, mouseY, x + 8, rowY, width - 24, getRowHeight() - 8);
            drawAllianceRow(record, x + 8, rowY, width - 24, getRowHeight() - 8, active, hover);
        }
        KOMEGuiTheme.disableScissor();
        drawListScrollbar(x + width - 10, rowStart, height - 34);
    }

    private void drawAllianceRow(Record record, int x, int y, int width, int height, boolean active, boolean hover) {
        KOMEGuiTheme.drawCard(x, y, width, height, active || hover);
        if (active) {
            KOMEGuiTheme.drawBorderedRect(x + 2, y + 2, 4, height - 4, KOMEGuiTheme.COLOR_BORDER_RED, KOMEGuiTheme.COLOR_BORDER_RED);
        }
        int hintWidth = fontRendererObj.getStringWidth("Click for details");
        int titleWidth = width > 310 ? width - hintWidth - 32 : width - 24;
        fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, record.factionA + " -> " + record.factionB, titleWidth), x + 12, y + 9, KOMEGuiTheme.COLOR_BORDER_RED);
        if (width > 310) {
            fontRendererObj.drawString("Click for details", x + width - hintWidth - 12, y + 9, KOMEGuiTheme.COLOR_TEXT_MUTED);
        }
        int chipY = y + 30;
        int chipGap = 8;
        int chipW = Math.max(64, Math.min(84, (width - 24 - chipGap * 3) / 4));
        drawStatusChip("Civil", record.civilTier, x + 12, chipY, chipW);
        drawStatusChip("Military", record.militaryTier, x + 12 + chipW + chipGap, chipY, chipW);
        drawStatusChip("Trade", record.tradeTier, x + 12 + (chipW + chipGap) * 2, chipY, chipW);
        if (record.hasPending()) {
            drawStatusChip("Pending", -2, x + 12 + (chipW + chipGap) * 3, chipY, chipW);
        }
    }

    private void drawStatusChip(String label, int tier, int x, int y, int width) {
        int fill = tier == -2 ? KOMEGuiTheme.COLOR_WARN : tier >= 0 ? KOMEGuiTheme.COLOR_GOOD : KOMEGuiTheme.COLOR_TEXT_MUTED;
        KOMEGuiTheme.drawFactionBadge(fontRendererObj, label + " " + displayTier(tier), x, y, width, fill);
    }

    private void drawDetail(int mouseX, int mouseY) {
        if (createMode) {
            return;
        }
        int x = getDetailX();
        int y = getContentY();
        int width = getDetailWidth();
        int height = getContentHeight();
        KOMEGuiTheme.drawSubPanel(x, y, width, height);
        KOMEGuiTheme.drawSectionTitle(fontRendererObj, "Alliance Detail", x + 10, y + 8, width - 20);
        if (selected < 0 || selected >= records.size()) {
            int cardW = Math.min(250, width - 40);
            int cardX = x + (width - cardW) / 2;
            int cardY = y + height / 2 - 24;
            KOMEGuiTheme.drawCard(cardX, cardY, cardW, 48, false);
            KOMEGuiTheme.drawWrappedText(fontRendererObj, "Select an alliance to manage it.", cardX + 16, cardY + 17, cardW - 32, KOMEGuiTheme.COLOR_TEXT_MUTED);
            return;
        }
        Record record = (Record) records.get(selected);
        drawTopDetailCard(record, x + 12, y + 30, width - 24, mouseX, mouseY);
        fontRendererObj.drawString("Selected Type:", x + 12, y + 91, KOMEGuiTheme.COLOR_TEXT_MUTED);
        if (permissionMode) {
            drawPermissionSummary(record, x + 12, y + 116, width - 24, getDetailViewportHeight(), mouseX, mouseY);
        } else {
            drawTierSections(record, x + 12, y + 116, width - 24, getDetailViewportHeight(), mouseX, mouseY);
        }
    }

    private void drawTopDetailCard(Record record, int x, int y, int width, int mouseX, int mouseY) {
        KOMEGuiTheme.drawCard(x, y, width, 52, KOMEGuiTheme.isHovered(mouseX, mouseY, x, y, width, 52));
        fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, record.factionA + " -> " + record.factionB, width - 14), x + 8, y + 7, KOMEGuiTheme.COLOR_BORDER_RED);
        fontRendererObj.drawString("Sender: " + KOMEGuiTheme.trimToWidth(fontRendererObj, record.factionA, width / 2 - 58), x + 8, y + 21, KOMEGuiTheme.COLOR_TEXT);
        fontRendererObj.drawString("Receiver: " + KOMEGuiTheme.trimToWidth(fontRendererObj, record.factionB, width / 2 - 68), x + width / 2, y + 21, KOMEGuiTheme.COLOR_TEXT);
        fontRendererObj.drawString("Updated by: " + KOMEGuiTheme.trimToWidth(fontRendererObj, record.lastUpdatedBy, width - 86), x + 8, y + 35, KOMEGuiTheme.COLOR_TEXT_MUTED);
    }

    private void drawTierSections(Record record, int x, int y, int width, int height, int mouseX, int mouseY) {
        int max = getMaxDetailScroll(record);
        detailScroll = Math.max(0, Math.min(max, detailScroll));
        KOMEGuiTheme.enableScissor(mc, x, y, width, height);
        String[] benefits = BENEFITS[selectedType];
        int tier = record.getTier(selectedType);
        String next = getNextRequirement(record);
        int cursorY = y - detailScroll;
        cursorY = drawStatusCard(record, x, cursorY, width, mouseX, mouseY, next) + 8;
        for (int i = 0; i < benefits.length; i++) {
            String requirement = getTierRequirement(record, i);
            int panelHeight = getTierPanelHeight(requirement, width);
            boolean unlocked = tier >= i;
            boolean current = tier == i - 1 || tier == i;
            KOMEGuiTheme.drawCard(x, cursorY, width, panelHeight, current || KOMEGuiTheme.isHovered(mouseX, mouseY, x, cursorY + detailScroll, width, panelHeight));
            fontRendererObj.drawString("Tier " + i + " - " + (unlocked ? "Unlocked" : "Locked"), x + 8, cursorY + 7, unlocked ? KOMEGuiTheme.COLOR_GOOD : KOMEGuiTheme.COLOR_TEXT_MUTED);
            fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, benefits[i], width - 16), x + 8, cursorY + 20, KOMEGuiTheme.COLOR_BORDER_RED);
            int endY = KOMEGuiTheme.drawWrappedText(fontRendererObj, requirement, x + 8, cursorY + 34, width - 16, KOMEGuiTheme.COLOR_TEXT);
            KOMEGuiTheme.drawWrappedText(fontRendererObj, "Reward: " + benefits[i], x + 8, endY + 4, width - 16, KOMEGuiTheme.COLOR_TEXT_MUTED);
            cursorY += panelHeight + 8;
        }
        KOMEGuiTheme.disableScissor();
        drawDetailScrollbar(x + width - 7, y, height, max);
    }

    private int drawStatusCard(Record record, int x, int y, int width, int mouseX, int mouseY, String next) {
        String delivered = getDeliveredProgress(record);
        int height = 56 + KOMEGuiTheme.wrapText(fontRendererObj, next, width - 16).size() * 10 + KOMEGuiTheme.wrapText(fontRendererObj, delivered, width - 16).size() * 10;
        KOMEGuiTheme.drawCard(x, y, width, height, KOMEGuiTheme.isHovered(mouseX, mouseY, x, y + detailScroll, width, height));
        fontRendererObj.drawString("Current Status: " + displayTier(record.getTier(selectedType)), x + 8, y + 7, KOMEGuiTheme.COLOR_BORDER_RED);
        fontRendererObj.drawString("Next requirement:", x + 8, y + 22, KOMEGuiTheme.COLOR_TEXT_MUTED);
        int endY = KOMEGuiTheme.drawWrappedText(fontRendererObj, next, x + 8, y + 34, width - 16, KOMEGuiTheme.COLOR_TEXT);
        KOMEGuiTheme.drawWrappedText(fontRendererObj, "Delivered: " + delivered, x + 8, endY + 4, width - 16, KOMEGuiTheme.COLOR_TEXT_MUTED);
        return y + height;
    }

    private int getMaxDetailScroll(Record record) {
        return Math.max(0, getDetailContentHeight(record) - getDetailViewportHeight());
    }

    private int getDetailContentHeight(Record record) {
        if (permissionMode) {
            return getPermissionContentHeight(record);
        }
        int width = getDetailWidth() - 24;
        int height = 72 + KOMEGuiTheme.wrapText(fontRendererObj, getNextRequirement(record), width - 16).size() * 10 + KOMEGuiTheme.wrapText(fontRendererObj, getDeliveredProgress(record), width - 16).size() * 10;
        String[] benefits = BENEFITS[selectedType];
        for (int i = 0; i < benefits.length; i++) {
            height += getTierPanelHeight(getTierRequirement(record, i), width) + 8;
        }
        return height;
    }

    private int getPermissionContentHeight(Record record) {
        int width = getDetailWidth() - 24;
        int height = 0;
        for (int type = 0; type < TYPES.length; type++) {
            height += 26 + KOMEGuiTheme.wrapText(fontRendererObj, getPermissionLine(record, type), width - 16).size() * 10;
            height += BENEFITS[type].length * 12 + 10;
        }
        return height;
    }

    private void drawPermissionSummary(Record record, int x, int y, int width, int height, int mouseX, int mouseY) {
        int max = getMaxDetailScroll(record);
        detailScroll = Math.max(0, Math.min(max, detailScroll));
        KOMEGuiTheme.enableScissor(mc, x, y, width, height);
        int cursorY = y + 2 - detailScroll;
        for (int type = 0; type < TYPES.length; type++) {
            int tier = record.getTier(type);
            int cardHeight = 28 + BENEFITS[type].length * 12 + KOMEGuiTheme.wrapText(fontRendererObj, getPermissionLine(record, type), width - 16).size() * 10;
            KOMEGuiTheme.drawCard(x, cursorY, width, cardHeight, KOMEGuiTheme.isHovered(mouseX, mouseY, x, cursorY + detailScroll, width, cardHeight));
            fontRendererObj.drawString(TYPES[type] + " Permissions - " + displayTier(tier), x + 8, cursorY + 7, KOMEGuiTheme.COLOR_BORDER_RED);
            cursorY += 21;
            cursorY = KOMEGuiTheme.drawWrappedText(fontRendererObj, getPermissionLine(record, type), x + 8, cursorY, width - 16, KOMEGuiTheme.COLOR_TEXT_MUTED) + 4;
            String[] benefits = BENEFITS[type];
            for (int i = 0; i < benefits.length; i++) {
                boolean unlocked = tier >= i;
                int color = unlocked ? KOMEGuiTheme.COLOR_GOOD : KOMEGuiTheme.COLOR_TEXT_MUTED;
                fontRendererObj.drawString((unlocked ? "* " : "- ") + benefits[i], x + 12, cursorY, color);
                cursorY += 12;
            }
            cursorY += 14;
        }
        KOMEGuiTheme.disableScissor();
        drawDetailScrollbar(x + width - 7, y, height, max);
    }

    private String getTierRequirement(Record record, int tierIndex) {
        if (tierIndex == 0) {
            return "Requirement: receiving faction accepts the request.";
        }
        if (selectedType == 0) {
            return tierIndex == 1 ? "Requirement: deposit " + KOMEAllianceInventory.CIVIL_T1_COINS_REQUIRED + " coins."
                : "Requirement: buy or sell " + KOMEAllianceInventory.CIVIL_T2_TRADE_REQUIRED + " coins with " + record.factionB
                + " traders. Progress: " + Math.min(record.civilTradeDelivered, KOMEAllianceInventory.CIVIL_T2_TRADE_REQUIRED)
                + "/" + KOMEAllianceInventory.CIVIL_T2_TRADE_REQUIRED + " coins.";
        }
        if (selectedType == 1) {
            if (tierIndex == 1) {
                return "Requirement: " + quotaStatus(record.militaryFood, record.militaryFoodDelivered);
            }
            if (tierIndex == 2) {
                return "Requirement: kill " + KOMEAllianceInventory.MILITARY_T2_KILLS_REQUIRED + " enemies of " + record.factionB
                    + ". Progress: " + Math.min(record.militaryKillsDelivered, KOMEAllianceInventory.MILITARY_T2_KILLS_REQUIRED)
                    + "/" + KOMEAllianceInventory.MILITARY_T2_KILLS_REQUIRED + " kills. Possible targets: " + enemyFactionList(record.keyB) + ".";
            }
            if (tierIndex == 3) {
                return "Requirement: complete the population build and waypoint battle.";
            }
            return "Requirement: cost " + KOMEAllianceInventory.MILITARY_T4_POP_REQUIRED + " pop and "
                + KOMEAllianceInventory.MILITARY_T4_COINS_REQUIRED + " coins. Available pop: "
                + Math.min(record.militaryT4Pop, KOMEAllianceInventory.MILITARY_T4_POP_REQUIRED) + "/"
                + KOMEAllianceInventory.MILITARY_T4_POP_REQUIRED + ". Coins: "
                + Math.min(record.militaryT4CoinsDelivered, KOMEAllianceInventory.MILITARY_T4_COINS_REQUIRED) + "/"
                + KOMEAllianceInventory.MILITARY_T4_COINS_REQUIRED + ".";
        }
        return tierIndex == 1
            ? "Requirement: " + KOMEAllianceInventory.TRADE_T1_COINS_REQUIRED + " coins and " + quotaStatus(record.tradeFood, record.tradeFoodDelivered)
            : tradeT2Requirement(record);
    }

    private void drawCreate(int mouseX, int mouseY) {
        int center = guiLeft + xSize / 2;
        int formW = Math.min(500, xSize - 88);
        int formX = center - formW / 2;
        int formY = getContentY() + 6;
        int formH = getContentHeight() - 12;
        int cardX = formX + 32;
        int cardW = formW - 64;
        int senderY = formY + 42;
        int typeY = formY + 100;
        int receiverY = formY + 154;
        int sendY = formY + formH - 34;
        String receiverName = hasSelectableReceiver(createType) && isSelectableReceiver(receiverIndex) ? factionName(receiverIndex) : "No eligible faction";
        KOMEGuiTheme.drawSubPanel(formX, formY, formW, formH);
        KOMEGuiTheme.drawSectionTitle(fontRendererObj, "Send Alliance Request", formX + 14, formY + 12, formW - 28);
        drawFormCard("Sender faction", viewerFactionName, cardX, senderY, cardW, mouseX, mouseY);
        drawFormCard("Alliance type", TYPES[createType], cardX, typeY, cardW, mouseX, mouseY);
        drawFormCard("Receiver faction", receiverName, cardX, receiverY, cardW, mouseX, mouseY);
        String description = getTypeDescription(createType);
        int descriptionY = receiverY + 56;
        int messageY = sendY - 31;
        if (messageY - descriptionY >= 14) {
            KOMEGuiTheme.drawWrappedText(fontRendererObj, description, cardX, descriptionY, cardW, KOMEGuiTheme.COLOR_TEXT);
        }
        KOMEGuiTheme.drawWrappedText(fontRendererObj, getCreateMessage(), cardX, messageY, cardW, canSendRequest() ? KOMEGuiTheme.COLOR_TEXT_MUTED : KOMEGuiTheme.COLOR_BAD);
    }

    private void drawFormCard(String title, String value, int x, int y, int width, int mouseX, int mouseY) {
        KOMEGuiTheme.drawCard(x, y, width, 48, KOMEGuiTheme.isHovered(mouseX, mouseY, x, y, width, 48));
        fontRendererObj.drawString(title, x + 10, y + 8, KOMEGuiTheme.COLOR_TEXT_MUTED);
        boolean selector = title.indexOf("Alliance type") >= 0 || title.indexOf("Receiver faction") >= 0;
        int valueX = selector ? x + 58 : x + 10;
        int valueWidth = selector ? width - 116 : width - 20;
        String text = KOMEGuiTheme.trimToWidth(fontRendererObj, value, valueWidth);
        if (selector) {
            KOMEGuiTheme.drawCenteredPlainText(fontRendererObj, text, x + width / 2, y + 29, KOMEGuiTheme.COLOR_BORDER_RED);
        } else {
            fontRendererObj.drawString(text, valueX, y + 28, KOMEGuiTheme.COLOR_BORDER_RED);
        }
    }

    private void requestAlliances() {
        rawLines = new ArrayList();
        records = new ArrayList();
        summary = "Loading...";
        KOMEPacketHandler.network.sendToServer(new KOMEPacketAllianceRequest());
    }

    private boolean canSendRequest() {
        return isSelectableReceiver(receiverIndex);
    }

    private String getCreateMessage() {
        if (viewerFactionKey.isEmpty()) {
            return "Pledge to a faction before sending alliance requests.";
        }
        if (!hasSelectableReceiver(createType)) {
            return "No eligible factions remain for " + TYPES[createType] + " requests.";
        }
        String receiver = factionKey(receiverIndex);
        if (viewerFactionKey.equals(receiver)) {
            return "Choose a different faction.";
        }
        if (!canRequestByDiplomacy(receiver, createType)) {
            if (viewerFactionHasKing) {
                return "Only your faction king can send alliance requests.";
            }
            return "Your faction has no king, so this request must match the current faction relation.";
        }
        if (hasAllAllianceTypes(receiver)) {
            return "All alliance types already exist with this faction.";
        }
        if (getAllianceTier(receiver, createType) != -1) {
            return "A " + TYPES[createType] + " alliance already exists with this faction.";
        }
        return "This sends only a " + TYPES[createType] + " request. The other alliance types must be requested separately.";
    }

    private String getTypeDescription(int type) {
        if (type == 1) {
            return "Military: military support and war-related progression.";
        }
        if (type == 2) {
            return "Trade: trade goods, food, and economic cooperation.";
        }
        return "Civil: cooperation and diplomatic access.";
    }

    private void sendRequestCommand() {
        if (!canSendRequest()) {
            return;
        }
        KOMEMinecraftClient.sendChat("/alliance request " + typeKey(createType) + " " + viewerFactionKey + " " + factionKey(receiverIndex));
        createMode = false;
        requestAlliances();
    }

    private String typeKey(int type) {
        return type == 0 ? "civil" : type == 1 ? "military" : "trade";
    }

    private String displayTier(int tier) {
        return tier == -2 ? "Pending" : tier < 0 ? "None" : "T" + tier;
    }

    private String getDeliveredProgress(Record record) {
        if (selectedType == 0) {
            return record.civilTradeDelivered > 0
                ? Math.min(record.civilTradeDelivered, KOMEAllianceInventory.CIVIL_T2_TRADE_REQUIRED)
                + "/" + KOMEAllianceInventory.CIVIL_T2_TRADE_REQUIRED + " trade coins"
                : "No delivered progress yet.";
        }
        if (selectedType == 1) {
            if (record.militaryTier == 0) {
                return quotaStatus(record.militaryFood, record.militaryFoodDelivered);
            }
            if (record.militaryTier == 1) {
                return Math.min(record.militaryKillsDelivered, KOMEAllianceInventory.MILITARY_T2_KILLS_REQUIRED)
                    + "/" + KOMEAllianceInventory.MILITARY_T2_KILLS_REQUIRED + " enemy kills";
            }
            if (record.militaryTier >= 3) {
                return Math.min(record.militaryT4Pop, KOMEAllianceInventory.MILITARY_T4_POP_REQUIRED)
                    + "/" + KOMEAllianceInventory.MILITARY_T4_POP_REQUIRED + " pop, "
                    + Math.min(record.militaryT4CoinsDelivered, KOMEAllianceInventory.MILITARY_T4_COINS_REQUIRED)
                    + "/" + KOMEAllianceInventory.MILITARY_T4_COINS_REQUIRED + " coins";
            }
            return "No delivered progress yet.";
        }
        if (record.tradeTier == 0) {
            return quotaStatus(record.tradeFood, record.tradeFoodDelivered);
        }
        if (record.tradeTier >= 1) {
            return Math.min(record.tradeFarmerPop, KOMEAllianceInventory.TRADE_T2_FARMER_POP_REQUIRED)
                + "/" + KOMEAllianceInventory.TRADE_T2_FARMER_POP_REQUIRED + " farmer pop, "
                + Math.min(record.tradeT2CoinsDelivered, KOMEAllianceInventory.TRADE_T2_COINS_REQUIRED)
                + "/" + KOMEAllianceInventory.TRADE_T2_COINS_REQUIRED + " coins";
        }
        return "No delivered progress yet.";
    }

    private String getPermissionLine(Record record, int type) {
        int tier = record.getTier(type);
        if (tier == -2) {
            return "Pending acceptance from the receiving faction.";
        }
        if (tier < 0) {
            return "No active " + TYPES[type] + " alliance.";
        }
        return "Unlocked through " + displayTier(tier) + ". Higher tiers continue to unlock the listed permissions.";
    }

    private String getNextRequirement(Record record) {
        int tier = record.getTier(selectedType);
        if (tier == -2) {
            return "Waiting for the receiving king to accept.";
        }
        if (selectedType == 0) {
            if (tier == 0) {
                return "Deposit " + KOMEAllianceInventory.CIVIL_T1_COINS_REQUIRED + " coins.";
            }
            if (tier == 1) {
                return "Trade " + KOMEAllianceInventory.CIVIL_T2_TRADE_REQUIRED + " coins worth of goods. Staff confirms this tier for now.";
            }
            return "Civil alliance complete.";
        }
        if (selectedType == 1) {
            if (tier == 0) {
                return quotaStatus(record.militaryFood, record.militaryFoodDelivered);
            }
            if (tier == 1) {
                return "Kill " + KOMEAllianceInventory.MILITARY_T2_KILLS_REQUIRED + " enemies of " + record.factionB
                    + ". Progress: " + Math.min(record.militaryKillsDelivered, KOMEAllianceInventory.MILITARY_T2_KILLS_REQUIRED)
                    + "/" + KOMEAllianceInventory.MILITARY_T2_KILLS_REQUIRED + " kills.";
            }
            if (tier == 2) {
                return "Complete the population build and waypoint battle.";
            }
            if (tier == 3) {
                return "Cost: " + KOMEAllianceInventory.MILITARY_T4_POP_REQUIRED + " pop and "
                    + KOMEAllianceInventory.MILITARY_T4_COINS_REQUIRED + " coins. Pop: "
                    + Math.min(record.militaryT4Pop, KOMEAllianceInventory.MILITARY_T4_POP_REQUIRED)
                    + "/" + KOMEAllianceInventory.MILITARY_T4_POP_REQUIRED + ". Coins: "
                    + Math.min(record.militaryT4CoinsDelivered, KOMEAllianceInventory.MILITARY_T4_COINS_REQUIRED)
                    + "/" + KOMEAllianceInventory.MILITARY_T4_COINS_REQUIRED + ".";
            }
            return "Military alliance complete.";
        }
        if (tier == 0) {
            return "Deposit " + KOMEAllianceInventory.TRADE_T1_COINS_REQUIRED + " coins and " + quotaStatus(record.tradeFood, record.tradeFoodDelivered);
        }
        if (tier == 1) {
            return tradeT2Requirement(record);
        }
        return "Trade alliance complete.";
    }

    private String tradeT2Requirement(Record record) {
        return "Spend " + KOMEAllianceInventory.TRADE_T2_FARMER_POP_REQUIRED + " farmer pop and deposit "
            + KOMEAllianceInventory.TRADE_T2_COINS_REQUIRED + " coins. Available farmer pop: "
            + Math.min(record.tradeFarmerPop, KOMEAllianceInventory.TRADE_T2_FARMER_POP_REQUIRED)
            + "/" + KOMEAllianceInventory.TRADE_T2_FARMER_POP_REQUIRED + ". Coins: "
            + Math.min(record.tradeT2CoinsDelivered, KOMEAllianceInventory.TRADE_T2_COINS_REQUIRED)
            + "/" + KOMEAllianceInventory.TRADE_T2_COINS_REQUIRED + ".";
    }

    private String quotaStatus(String assignment, int delivered) {
        int required = quotaRequiredUnits(assignment);
        if (required <= 0) {
            return "food quota not rolled yet.";
        }
        return assignment + " (" + delivered + "/" + required + " units)";
    }

    private int quotaRequiredUnits(String assignment) {
        if (assignment == null || !assignment.startsWith("Collect ")) {
            return 0;
        }
        String rest = assignment.substring("Collect ".length());
        int firstSpace = rest.indexOf(' ');
        if (firstSpace <= 0) {
            return 0;
        }
        int amount = parseInt(rest.substring(0, firstSpace));
        String afterAmount = rest.substring(firstSpace + 1);
        int ofIndex = afterAmount.indexOf(" of ");
        if (amount <= 0 || ofIndex <= 0) {
            return 0;
        }
        String unit = afterAmount.substring(0, ofIndex).trim();
        return "stacks".equalsIgnoreCase(unit) ? amount * 64 : amount;
    }

    private int getTierPanelHeight(String requirement, int width) {
        int requirementLines = KOMEGuiTheme.wrapText(fontRendererObj, requirement, width - 16).size();
        return 58 + requirementLines * 10;
    }

    private void drawListScrollbar(int x, int y, int height) {
        int rows = getVisibleRows();
        if (records.size() <= rows) {
            return;
        }
        KOMEGuiTheme.drawBorderedRect(x, y, 5, height, KOMEGuiTheme.COLOR_GOLD_DARK, 0x552B2117);
        int max = Math.max(1, records.size() - rows);
        int handleH = Math.max(18, height * rows / records.size());
        int handleY = y + (height - handleH) * scroll / max;
        KOMEGuiTheme.drawBorderedRect(x, handleY, 5, handleH, KOMEGuiTheme.COLOR_BORDER_RED, KOMEGuiTheme.COLOR_GOLD);
    }

    private void drawDetailScrollbar(int x, int y, int height, int max) {
        if (max <= 0) {
            return;
        }
        KOMEGuiTheme.drawBorderedRect(x, y, 5, height, KOMEGuiTheme.COLOR_GOLD_DARK, 0x552B2117);
        int content = height + max;
        int handleH = Math.max(18, height * height / Math.max(height, content));
        int handleY = y + (height - handleH) * detailScroll / max;
        KOMEGuiTheme.drawBorderedRect(x, handleY, 5, handleH, KOMEGuiTheme.COLOR_BORDER_RED, KOMEGuiTheme.COLOR_GOLD);
    }

    private void drawButtonTooltip(int mouseX, int mouseY) {
        for (Object object : buttonList) {
            GuiButton button = (GuiButton) object;
            if (button.enabled || !KOMEGuiTheme.isHovered(mouseX, mouseY, button.xPosition, button.yPosition, button.width, button.height)) {
                continue;
            }
            String reason = getDisabledReason(button.id);
            if (reason.length() > 0) {
                List lines = new ArrayList();
                lines.add(reason);
                KOMEGuiTheme.drawTooltip(fontRendererObj, lines, mouseX, mouseY, width, height);
            }
            return;
        }
    }

    private String getDisabledReason(int id) {
        if (createMode) {
            if (id == 5) {
                return getCreateMessage();
            }
            if (id == 3 || id == 4) {
                return "No eligible receiver faction for this alliance type.";
            }
            return "";
        }
        if (selected < 0 || selected >= records.size()) {
            return "";
        }
        Record record = (Record) records.get(selected);
        if (id == 20) {
            if (!record.hasPending(selectedType)) {
                return "This alliance type is not pending.";
            }
            if (!viewerIsKing) {
                return "Only the receiving faction king can accept.";
            }
            if (!viewerFactionKey.equals(record.keyB)) {
                return "Only the receiving faction can accept this request.";
            }
        } else if (id == 21) {
            return "Ledger is available to the sender after any alliance tier is accepted.";
        } else if (id == 22) {
            return "Only the receiving faction king can claim goods.";
        } else if (id == 23) {
            return "Roll is available only for unrolled Military or Trade quotas.";
        }
        return "";
    }

    private int nextReceiver(int direction) {
        if (factionKeys.isEmpty()) {
            return 0;
        }
        int next = receiverIndex;
        for (int i = 0; i < factionKeys.size(); i++) {
            next = wrap(next + direction, factionKeys.size());
            if (isSelectableReceiver(next)) {
                return next;
            }
        }
        return next;
    }

    private int ensureReceiverIndex(int preferred, int direction) {
        if (isSelectableReceiver(preferred)) {
            return preferred;
        }
        if (factionKeys.isEmpty()) {
            return 0;
        }
        int next = preferred;
        for (int i = 0; i < factionKeys.size(); i++) {
            if (isSelectableReceiver(next)) {
                return next;
            }
            next = wrap(next + direction, factionKeys.size());
        }
        return preferred;
    }

    private boolean hasSelectableReceiver(int type) {
        int previousType = createType;
        createType = type;
        for (int i = 0; i < factionKeys.size(); i++) {
            if (isSelectableReceiver(i)) {
                createType = previousType;
                return true;
            }
        }
        createType = previousType;
        return false;
    }

    private boolean isSelectableReceiver(int index) {
        String receiver = factionKey(index);
        return !viewerFactionKey.isEmpty()
            && !viewerFactionKey.equals(receiver)
            && canRequestByDiplomacy(receiver, createType)
            && !hasAllAllianceTypes(receiver)
            && getAllianceTier(receiver, createType) == -1;
    }

    private boolean canRequestByDiplomacy(String receiver, int type) {
        if (viewerIsKing) {
            return true;
        }
        if (viewerFactionHasKing) {
            return false;
        }
        LOTRFactionRelations.Relation relation = getRelation(viewerFactionKey, receiver);
        if (type == 1) {
            return relation == LOTRFactionRelations.Relation.ALLY;
        }
        if (type == 2) {
            return relation == LOTRFactionRelations.Relation.ALLY || relation == LOTRFactionRelations.Relation.FRIEND;
        }
        return relation == LOTRFactionRelations.Relation.ALLY
            || relation == LOTRFactionRelations.Relation.FRIEND
            || relation == LOTRFactionRelations.Relation.NEUTRAL;
    }

    private boolean hasAllAllianceTypes(String receiver) {
        Record record = findRecord(viewerFactionKey, receiver);
        return record != null && record.civilTier != -1 && record.militaryTier != -1 && record.tradeTier != -1;
    }

    private int getAllianceTier(String receiver, int type) {
        Record record = findRecord(viewerFactionKey, receiver);
        return record == null ? -1 : record.getTier(type);
    }

    private Record findRecord(String sender, String receiver) {
        for (Object object : records) {
            Record record = (Record) object;
            if (record.keyA.equals(sender) && record.keyB.equals(receiver)) {
                return record;
            }
        }
        return null;
    }

    private int getVisibleRows() {
        return Math.max(3, (getContentHeight() - 34) / getRowHeight());
    }

    private int getContentY() {
        return guiTop + 64;
    }

    private int getContentHeight() {
        return ySize - 78;
    }

    private int getListX() {
        return guiLeft + 18;
    }

    private int getListY() {
        return getContentY();
    }

    private int getListWidth() {
        return xSize - 36;
    }

    private int getDetailX() {
        return getListX() + getListWidth() + 12;
    }

    private int getDetailWidth() {
        return guiLeft + xSize - 18 - getDetailX();
    }

    private int getDetailViewportHeight() {
        return Math.max(56, guiTop + ySize - 82 - (getContentY() + 116));
    }

    private int getRowHeight() {
        return 64;
    }

    static String viewerFactionKey() {
        return viewerFactionKey;
    }

    static boolean viewerIsKing() {
        return viewerIsKing;
    }

    private String factionKey(int index) {
        ensureFactions();
        return factionKeys.isEmpty() ? "" : String.valueOf(factionKeys.get(wrap(index, factionKeys.size())));
    }

    private String factionName(int index) {
        ensureFactions();
        return factionNames.isEmpty() ? "" : String.valueOf(factionNames.get(wrap(index, factionNames.size())));
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
        LOTRFactionRelations.Relation relation = getRelation(factionA, factionB);
        return relation == LOTRFactionRelations.Relation.ENEMY || relation == LOTRFactionRelations.Relation.MORTAL_ENEMY;
    }

    private static String enemyFactionList(String factionKey) {
        List names = new ArrayList();
        LOTRFaction faction = KOMEAlliance.findLotrFaction(factionKey);
        if (faction == null) {
            return "none";
        }
        for (LOTRFaction other : LOTRFaction.values()) {
            if (other != null && other != faction && other.isPlayableAlignmentFaction()) {
                LOTRFactionRelations.Relation relation = LOTRFactionRelations.getRelations(faction, other);
                if (relation == LOTRFactionRelations.Relation.ENEMY || relation == LOTRFactionRelations.Relation.MORTAL_ENEMY) {
                    names.add(other.factionName());
                }
            }
        }
        if (names.isEmpty()) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(names.get(i));
        }
        return builder.toString();
    }

    private static LOTRFactionRelations.Relation getRelation(String factionA, String factionB) {
        LOTRFaction a = KOMEAlliance.findLotrFaction(factionA);
        LOTRFaction b = KOMEAlliance.findLotrFaction(factionB);
        return a == null || b == null ? LOTRFactionRelations.Relation.NEUTRAL : LOTRFactionRelations.getRelations(a, b);
    }

    static int wrap(int index, int size) {
        if (size <= 0) {
            return 0;
        }
        while (index < 0) {
            index += size;
        }
        return index % size;
    }

    private static void parseRecords() {
        records = new ArrayList();
        summary = "Alliances: 0";
        viewerIsKing = false;
        viewerFactionHasKing = false;
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
                viewerIsKing = parts.length > 3 && "1".equals(parts[3]);
                viewerFactionHasKing = parts.length > 4 && "1".equals(parts[4]);
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

    static class Record {
        final String keyA;
        final String keyB;
        final String factionA;
        final String factionB;
        final int civilTier;
        final int militaryTier;
        final int tradeTier;
        final String lastUpdatedBy;
        final String militaryFood;
        final int militaryFoodDelivered;
        final String tradeFood;
        final int tradeFoodDelivered;
        final int civilTradeDelivered;
        final int militaryKillsDelivered;
        final int tradeT2CoinsDelivered;
        final int tradeFarmerPop;
        final int militaryT4CoinsDelivered;
        final int militaryT4Pop;

        private Record(String[] parts) {
            keyA = parts[1];
            keyB = parts[2];
            factionA = parts[3];
            factionB = parts[4];
            civilTier = parseInt(parts[5]);
            militaryTier = parseInt(parts[6]);
            tradeTier = parseInt(parts[7]);
            lastUpdatedBy = parts[8];
            militaryFood = parts.length > 10 ? parts[10] : "";
            militaryFoodDelivered = parts.length > 11 ? parseInt(parts[11]) : 0;
            tradeFood = parts.length > 12 ? parts[12] : "";
            tradeFoodDelivered = parts.length > 13 ? parseInt(parts[13]) : 0;
            civilTradeDelivered = parts.length > 14 ? parseInt(parts[14]) : 0;
            militaryKillsDelivered = parts.length > 15 ? parseInt(parts[15]) : 0;
            tradeT2CoinsDelivered = parts.length > 16 ? parseInt(parts[16]) : 0;
            tradeFarmerPop = parts.length > 17 ? parseInt(parts[17]) : 0;
            militaryT4CoinsDelivered = parts.length > 18 ? parseInt(parts[18]) : 0;
            militaryT4Pop = parts.length > 19 ? parseInt(parts[19]) : 0;
        }

        int getTier(int type) {
            return type == 0 ? civilTier : type == 1 ? militaryTier : tradeTier;
        }

        boolean hasPending() {
            return civilTier == -2 || militaryTier == -2 || tradeTier == -2;
        }

        boolean hasPending(int type) {
            return getTier(type) == -2;
        }

        boolean hasAnyAlliance() {
            return civilTier != -1 || militaryTier != -1 || tradeTier != -1;
        }

        boolean needsQuotaRoll(int type) {
            return type == 1 && militaryTier == 0 && militaryFood.trim().isEmpty()
                || type == 2 && tradeTier == 0 && tradeFood.trim().isEmpty();
        }
    }
}
