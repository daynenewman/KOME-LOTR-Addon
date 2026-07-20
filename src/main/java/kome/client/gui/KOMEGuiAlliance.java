package kome.client.gui;

import kome.client.KOMEMinecraftClient;
import kome.common.data.KOMEAlliance;
import kome.common.data.KOMEAllianceInventory;
import kome.common.network.KOMEPacketAllianceRequest;
import kome.common.network.KOMEPacketAllianceAction;
import kome.common.network.KOMEPacketHandler;
import lotr.client.gui.LOTRGuiMenu;
import lotr.client.gui.LOTRGuiMenuBase;
import lotr.common.fac.LOTRFaction;
import lotr.common.fac.LOTRFactionRelations;
import net.minecraft.client.gui.GuiButton;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KOMEGuiAlliance extends LOTRGuiMenuBase {
    private static final String[] TYPES = KOMEAlliancePermissions.TYPES;
    private static final String[][] BENEFITS = KOMEAlliancePermissions.UNLOCKS;
    private static List rawLines = new ArrayList();
    private static List records = new ArrayList();
    private static List factionKeys = new ArrayList();
    private static List factionNames = new ArrayList();
    private static List factionKingKeys = new ArrayList();
    private static List requestOptions = new ArrayList();
    private static Map trackRecords = new HashMap();
    private static Map militaryContexts = new HashMap();
    private static Map militaryCompanies = new HashMap();
    private static String summary = "Alliances: 0";
    private static String viewerFactionKey = "";
    private static String viewerFactionName = "No pledged faction";
    private static boolean viewerIsKing;
    private static boolean viewerFactionHasKing;
    private static boolean viewerIsAdmin;
    private static boolean operatorViewEnabled;
    private static String serverMessage = "";
    private static String configSummary = "Standard | Waypoints on";

    private int selected = -1;
    private int selectedType;
    private int createType;
    private int receiverIndex = 1;
    private int scroll;
    private int detailScroll;
    private boolean createMode;
    private boolean permissionMode;
    private final KOMEGuiScrollPanel listPanel = new KOMEGuiScrollPanel();

    public static void update(List updatedLines) {
        rawLines = updatedLines == null ? new ArrayList() : new ArrayList(updatedLines);
        parseRecords();
        if (KOMEMinecraftClient.currentScreen() instanceof KOMEGuiAllianceDetail) {
            ((KOMEGuiAllianceDetail) KOMEMinecraftClient.currentScreen()).refreshRecord();
        }
    }

    public static void resetData() {
        rawLines = new ArrayList();
        records = new ArrayList();
        factionKingKeys = new ArrayList();
        requestOptions = new ArrayList();
        trackRecords = new HashMap();
        militaryContexts = new HashMap();
        militaryCompanies = new HashMap();
        summary = "Alliances: 0";
        viewerFactionKey = "";
        viewerFactionName = "No pledged faction";
        viewerIsKing = false;
        viewerFactionHasKing = false;
        viewerIsAdmin = false;
        operatorViewEnabled = false;
        serverMessage = "";
        configSummary = "Standard | Waypoints on";
    }

    public static void setServerMessage(String message) {
        serverMessage = message == null ? "" : message;
    }

    static String serverMessage() {
        return serverMessage;
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
    public void updateScreen() {
        if (Boolean.getBoolean("kome.guiCapture") && mc.thePlayer == null) return;
        super.updateScreen();
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
        } else if (button.id == 8 && viewerIsAdmin) {
            operatorViewEnabled = !operatorViewEnabled;
            selected = -1;
            scroll = 0;
            detailScroll = 0;
            requestAlliances();
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
        if (viewerIsAdmin) {
            buttonList.add(new KOMEGuiButton(8, guiLeft + 76, guiTop + 14, 108, 20,
                "Operator View: " + (operatorViewEnabled ? "On" : "Off")));
        }
        GuiButton mode = KOMEGuiButton.small(2, guiLeft + xSize - 68, guiTop + 14, createMode ? "List" : "New");
        mode.enabled = createMode || !viewerFactionKey.isEmpty();
        buttonList.add(mode);
        if (createMode) {
            receiverIndex = ensureReceiverIndex(receiverIndex, 1);
            int center = guiLeft + xSize / 2;
            int formW = Math.min(500, xSize - 88);
            int formX = center - formW / 2;
            int formY = getContentY() + 6;
            int formH = getContentHeight() - 12;
            int cardX = formX + 32;
            int cardW = formW - 64;
            boolean compact = isCompactCreate(formH);
            int typeY = compact ? formY + 53 : formY + 100;
            int receiverY = compact ? formY + 76 : formY + 154;
            int selectorY = compact ? 2 : 24;
            int selectorH = compact ? 16 : 20;
            buttonList.add(new KOMEGuiButton(6, cardX + 14, typeY + selectorY, 30, selectorH, "<"));
            buttonList.add(new KOMEGuiButton(7, cardX + cardW - 44, typeY + selectorY, 30, selectorH, ">"));
            GuiButton receiverLeft = new KOMEGuiButton(3, cardX + 14, receiverY + selectorY, 30, selectorH, "<");
            receiverLeft.enabled = hasSelectableReceiver(createType);
            buttonList.add(receiverLeft);
            GuiButton receiverRight = new KOMEGuiButton(4, cardX + cardW - 44, receiverY + selectorY, 30, selectorH, ">");
            receiverRight.enabled = hasSelectableReceiver(createType);
            buttonList.add(receiverRight);
            GuiButton send = new KOMEGuiButton(5, center - 86, formY + formH - (compact ? 28 : 34), 172, 24, "Send Request");
            send.enabled = canSendRequest();
            buttonList.add(send);
            return;
        }
    }

    private void drawHeader() {
        int headerX = guiLeft + (viewerIsAdmin ? 192 : 126);
        KOMEGuiTheme.drawHeader(fontRendererObj, "Alliances", headerX, guiTop + 13,
            guiLeft + xSize - 126 - headerX);
        int metaY = guiTop + 48;
        int columnW = (xSize - 44) / 3;
        String faction = KOMEGuiTheme.trimToWidth(fontRendererObj, "Your Faction: " + viewerFactionName, columnW - 8);
        fontRendererObj.drawString(faction, guiLeft + 22, metaY, KOMEGuiTheme.COLOR_TEXT);
        String authority = "King: " + (viewerIsKing ? "Yes" : "No")
            + (viewerIsAdmin ? " | Operator view: " + (operatorViewEnabled ? "all records" : "faction only") : "");
        authority = KOMEGuiTheme.trimToWidth(fontRendererObj, authority, columnW - 8);
        fontRendererObj.drawString(authority, guiLeft + xSize - fontRendererObj.getStringWidth(authority) - 22, metaY,
            viewerIsKing || viewerIsAdmin ? KOMEGuiTheme.COLOR_GOOD : KOMEGuiTheme.COLOR_TEXT_MUTED);
        String centerMeta = summary + " | " + configSummary;
        centerMeta = KOMEGuiTheme.trimToWidth(fontRendererObj, centerMeta, columnW - 8);
        fontRendererObj.drawString(centerMeta, guiLeft + xSize / 2 - fontRendererObj.getStringWidth(centerMeta) / 2, metaY, KOMEGuiTheme.COLOR_TEXT_MUTED);
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
        listPanel.layout(x + 1, rowStart, width - 3, height - 34,
            records.size() * getRowHeight()).setScroll(scroll * getRowHeight());
        listPanel.begin(mc);
        for (int i = 0; i < getVisibleRows() && scroll + i < records.size(); i++) {
            Record record = (Record) records.get(scroll + i);
            int rowY = rowStart + i * getRowHeight();
            boolean active = selected == scroll + i;
            boolean hover = KOMEGuiTheme.isHovered(mouseX, mouseY, x + 8, rowY, width - 24, getRowHeight() - 8);
            drawAllianceRow(record, x + 8, rowY, width - 24, getRowHeight() - 8, active, hover);
        }
        listPanel.end();
        listPanel.drawScrollbar();
    }

    private void drawAllianceRow(Record record, int x, int y, int width, int height, boolean active, boolean hover) {
        KOMEGuiTheme.drawCard(x, y, width, height, active || hover);
        int accent = KOMEGuiTheme.factionColor(record.allyFaction);
        KOMEGuiTheme.drawBorderedRect(x + 2, y + 2, 4, height - 4,
            active ? KOMEGuiTheme.COLOR_GOLD : accent, accent);
        int hintWidth = fontRendererObj.getStringWidth("Click for details");
        int titleWidth = width > 310 ? width - hintWidth - 32 : width - 24;
        String title = record.factionA + " <-> " + record.factionB + " | " + record.strongestAgreement();
        fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, title, titleWidth), x + 12, y + 7, KOMEGuiTheme.COLOR_BORDER_RED);
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
        String footer = record.kingStatus() + (record.provisional() ? " | " + record.graceText() : "");
        fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, footer, width - 24), x + 12, y + height - 10,
            record.provisional() ? KOMEGuiTheme.COLOR_WARN : KOMEGuiTheme.COLOR_TEXT_MUTED);
    }

    private void drawStatusChip(String label, int tier, int x, int y, int width) {
        KOMEGuiTheme.Status status = tier == -2 ? KOMEGuiTheme.Status.WARNING
            : tier >= 0 ? KOMEGuiTheme.Status.ACTIVE : KOMEGuiTheme.Status.LOCKED;
        KOMEGuiTheme.drawStatusChip(fontRendererObj, label + " " + displayTier(tier), status, x, y, width);
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
        fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, record.factionA + " <-> " + record.factionB, width - 14), x + 8, y + 7, KOMEGuiTheme.COLOR_BORDER_RED);
        fontRendererObj.drawString("Sender: " + KOMEGuiTheme.trimToWidth(fontRendererObj, record.factionA, width / 2 - 58), x + 8, y + 21, KOMEGuiTheme.COLOR_TEXT);
        fontRendererObj.drawString("Receiver: " + KOMEGuiTheme.trimToWidth(fontRendererObj, record.factionB, width / 2 - 68), x + width / 2, y + 21, KOMEGuiTheme.COLOR_TEXT);
        String footer = "Updated by: " + record.lastUpdatedBy;
        fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, footer, width - 16), x + 8, y + 35, KOMEGuiTheme.COLOR_TEXT_MUTED);
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
        for (int i = 1; i < benefits.length; i++) {
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
        for (int i = 1; i < benefits.length; i++) {
            height += getTierPanelHeight(getTierRequirement(record, i), width) + 8;
        }
        return height;
    }

    private int getPermissionContentHeight(Record record) {
        int width = getDetailWidth() - 24;
        int height = 0;
        for (int type = 0; type < TYPES.length; type++) {
            height += 26 + KOMEGuiTheme.wrapText(fontRendererObj, getPermissionLine(record, type), width - 16).size() * 10;
            height += (BENEFITS[type].length - 1) * 12 + 10;
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
            int cardHeight = 28 + (BENEFITS[type].length - 1) * 12 + KOMEGuiTheme.wrapText(fontRendererObj, getPermissionLine(record, type), width - 16).size() * 10;
            KOMEGuiTheme.drawCard(x, cursorY, width, cardHeight, KOMEGuiTheme.isHovered(mouseX, mouseY, x, cursorY + detailScroll, width, cardHeight));
            fontRendererObj.drawString(TYPES[type] + " Permissions - " + displayTier(tier), x + 8, cursorY + 7, KOMEGuiTheme.COLOR_BORDER_RED);
            cursorY += 21;
            cursorY = KOMEGuiTheme.drawWrappedText(fontRendererObj, getPermissionLine(record, type), x + 8, cursorY, width - 16, KOMEGuiTheme.COLOR_TEXT_MUTED) + 4;
            String[] benefits = BENEFITS[type];
            for (int i = 1; i < benefits.length; i++) {
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
        String base = "Both faction sides must independently complete their rolled T" + tierIndex + " item quota.";
        if (selectedType == 0 && tierIndex == 2) {
            return base + " Each side must also complete " + KOMEAllianceInventory.CIVIL_T2_TRADE_REQUIRED + " legitimate allied trades.";
        }
        if (selectedType == 1 && tierIndex == 1) {
            return base + " Each side must reach 50 eligible kills and 50 effective offensive population.";
        }
        if (selectedType == 1 && tierIndex == 2) {
            return base + " Each side must reach 500 cumulative eligible kills and 150 effective offensive population.";
        }
        if (selectedType == 1 && tierIndex == 3) {
            return base + " Each side must reach 1,000 cumulative eligible kills and 300 effective offensive population.";
        }
        if (selectedType == 2 && tierIndex == 1) {
            return base + " Each side must complete 50 legitimate allied trades.";
        }
        if (selectedType == 2 && tierIndex == 2) {
            return base + " Each side must complete 250 cumulative legitimate allied trades.";
        }
        return base;
    }

    private void drawCreate(int mouseX, int mouseY) {
        int center = guiLeft + xSize / 2;
        int formW = Math.min(500, xSize - 88);
        int formX = center - formW / 2;
        int formY = getContentY() + 6;
        int formH = getContentHeight() - 12;
        int cardX = formX + 32;
        int cardW = formW - 64;
        boolean compact = isCompactCreate(formH);
        int cardH = compact ? 20 : 48;
        int senderY = compact ? formY + 30 : formY + 42;
        int typeY = compact ? formY + 53 : formY + 100;
        int receiverY = compact ? formY + 76 : formY + 154;
        int sendY = formY + formH - (compact ? 28 : 34);
        String receiverName = hasSelectableReceiver(createType) && isSelectableReceiver(receiverIndex) ? factionName(receiverIndex) : "No eligible faction";
        KOMEGuiTheme.drawSubPanel(formX, formY, formW, formH);
        KOMEGuiTheme.drawSectionTitle(fontRendererObj, "Send Alliance Request", formX + 14, formY + 12, formW - 28);
        drawFormCard("Sender faction", viewerFactionName, cardX, senderY, cardW, cardH, mouseX, mouseY);
        drawFormCard("Alliance type", TYPES[createType], cardX, typeY, cardW, cardH, mouseX, mouseY);
        drawFormCard("Receiver faction", receiverName, cardX, receiverY, cardW, cardH, mouseX, mouseY);
        if (compact) return;
        String description = getTypeDescription(createType);
        int descriptionY = receiverY + 56;
        int messageY = sendY - 31;
        if (messageY - descriptionY >= 14) {
            KOMEGuiTheme.drawWrappedText(fontRendererObj, description, cardX, descriptionY, cardW, KOMEGuiTheme.COLOR_TEXT);
        }
        KOMEGuiTheme.drawWrappedText(fontRendererObj, getCreateMessage(), cardX, messageY, cardW, canSendRequest() ? KOMEGuiTheme.COLOR_TEXT_MUTED : KOMEGuiTheme.COLOR_BAD);
    }

    private void drawFormCard(String title, String value, int x, int y, int width, int height, int mouseX, int mouseY) {
        KOMEGuiTheme.drawCard(x, y, width, height, KOMEGuiTheme.isHovered(mouseX, mouseY, x, y, width, height));
        if (height <= 20) {
            boolean selector = title.indexOf("Alliance type") >= 0 || title.indexOf("Receiver faction") >= 0;
            if (selector) {
                String prefix = title.indexOf("Alliance type") >= 0 ? "Type: " : "Receiver: ";
                KOMEGuiTheme.drawCenteredPlainText(fontRendererObj,
                    KOMEGuiTheme.trimToWidth(fontRendererObj, prefix + value, width - 104),
                    x + width / 2, y + 6, KOMEGuiTheme.COLOR_GOLD);
                return;
            }
            fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, title, 86), x + 7, y + 6, KOMEGuiTheme.COLOR_TEXT_MUTED);
            int inset = 92;
            int rightInset = title.indexOf("faction") >= 0 && !"No eligible faction".equals(value) ? 82 : 8;
            String text = KOMEGuiTheme.trimToWidth(fontRendererObj, value, Math.max(28, width - inset - rightInset));
            KOMEGuiTheme.drawCenteredPlainText(fontRendererObj, text, x + inset + (width - inset - rightInset) / 2,
                y + 6, KOMEGuiTheme.COLOR_TEXT);
            if (title.indexOf("faction") >= 0 && !"No eligible faction".equals(value)) {
                String key = title.indexOf("Sender") >= 0 ? viewerFactionKey
                    : hasSelectableReceiver(createType) && isSelectableReceiver(receiverIndex) ? factionKey(receiverIndex) : "";
                KOMEGuiTheme.drawFactionBadge(fontRendererObj, key, title.indexOf("Sender") >= 0 ? "Our Faction" : "Partner",
                    x + width - 78, y + 1, 70);
            }
            return;
        }

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
        if (title.indexOf("faction") >= 0 && !"No eligible faction".equals(value)) {
            String key = title.indexOf("Sender") >= 0 ? viewerFactionKey
                : hasSelectableReceiver(createType) && isSelectableReceiver(receiverIndex) ? factionKey(receiverIndex) : "";
            KOMEGuiTheme.drawFactionBadge(fontRendererObj, key,
                title.indexOf("Sender") >= 0 ? "Our Faction" : "Partner", x + width - 94, y + 7, 84);
        }
    }

    private boolean isCompactCreate(int formHeight) {
        return formHeight < 230;
    }

    private void requestAlliances() {
        if (Boolean.getBoolean("kome.guiCapture")) return;
        rawLines = new ArrayList();
        records = new ArrayList();
        summary = "Loading...";
        KOMEPacketHandler.network.sendToServer(new KOMEPacketAllianceRequest(operatorViewEnabled));
    }

    void setVisualTestCreateMode(boolean value) {
        if (Boolean.getBoolean("kome.guiCapture")) createMode = value;
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
            if (viewerFactionHasKing && !viewerIsKing) {
                return "Only your faction king can send alliance requests.";
            }
            LOTRFactionRelations.Relation relation = getDefaultRelation(viewerFactionKey, receiver);
            return "Default LOTR relation is " + relationName(relation)
                + ". Civil requires Neutral+, Trade requires Friend+, and Military requires Ally.";
        }
        if (hasAllAllianceTypes(receiver)) {
            return "All alliance types already exist with this faction.";
        }
        if (getAllianceTier(receiver, createType) != -1) {
            return "A " + TYPES[createType] + " alliance already exists with this faction.";
        }
        return requestCascadeMessage(receiver);
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
        KOMEPacketHandler.network.sendToServer(new KOMEPacketAllianceAction("request", typeKey(createType),
            viewerFactionKey, factionKey(receiverIndex)));
        createMode = false;
        requestAlliances();
    }

    private String typeKey(int type) {
        return type == 0 ? "civil" : type == 1 ? "military" : "trade";
    }

    private String displayTier(int tier) {
        return tier == -2 ? "Pending" : tier < 0 ? "None" : tier == 0 ? "Established" : "T" + tier;
    }

    private String getDeliveredProgress(Record record) {
        return "Your side: " + record.quota(selectedType, false) + ". Allied side: " + record.quota(selectedType, true) + ".";
    }

    private String getPermissionLine(Record record, int type) {
        int tier = record.getTier(type);
        if (tier == -2) {
            return "Pending acceptance from the receiving faction.";
        }
        if (tier < 0) {
            return "No active " + TYPES[type] + " alliance.";
        }
        String state = record.provisional() ? "Provisional succession/grace state. " : "";
        return state + (tier == 0 ? "T0 is accepted base status and grants no tier benefit. " : "Unlocked through " + displayTier(tier) + ". ")
            + "Higher tiers require both faction sides to complete their own shared-side objective.";
    }

    private String getNextRequirement(Record record) {
        int tier = record.getTier(selectedType);
        if (tier == -2) {
            return "Waiting for the receiving king to accept.";
        }
        int maxTier = selectedType == 1 ? 3 : 2;
        if (tier >= maxTier) {
            return TYPES[selectedType] + " alliance complete.";
        }
        int target = tier + 1;
        return getTierRequirement(record, target) + " Your side: " + record.quota(selectedType, false)
            + ". Allied side: " + record.quota(selectedType, true) + ".";
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
        if (!viewerIsKing) {
            return false;
        }
        RequestOption option = findRequestOption(receiver);
        return option != null && option.allowed(type);
    }

    private String requestCascadeMessage(String receiver) {
        String suffix = receiverHasKing(receiver) ? " Waiting for the receiving king to accept."
            : " The receiving faction has no recorded king, so it starts immediately.";
        if (createType == 1) {
            return "Military also starts Trade and Civil." + suffix;
        }
        if (createType == 2) {
            return "Trade also starts Civil." + suffix;
        }
        return "Civil starts the base diplomatic relationship." + suffix;
    }

    private static boolean receiverHasKing(String receiver) {
        RequestOption option = findRequestOption(receiver);
        return option != null ? option.receiverHasKing
            : factionKingKeys.contains(KOMEAlliance.normalizeFactionKey(receiver));
    }

    private static RequestOption findRequestOption(String receiver) {
        String key = KOMEAlliance.normalizeFactionKey(receiver);
        for (Object object : requestOptions) {
            RequestOption option = (RequestOption) object;
            if (option.faction.equals(key)) {
                return option;
            }
        }
        return null;
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
            if (KOMEAlliance.pairKey(record.keyA, record.keyB).equals(KOMEAlliance.pairKey(sender, receiver))) {
                return record;
            }
        }
        return null;
    }

    static Record recordFor(String first, String second) {
        String pair = KOMEAlliance.pairKey(first, second);
        for (Object object : records) {
            Record record = (Record) object;
            if (pair.equals(KOMEAlliance.pairKey(record.keyA, record.keyB))) return record;
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
        return 70;
    }

    static String viewerFactionKey() {
        return viewerFactionKey;
    }

    static boolean viewerIsKing() {
        return viewerIsKing;
    }

    static boolean viewerIsAdmin() {
        return viewerIsAdmin;
    }

    static boolean operatorViewEnabled() {
        return operatorViewEnabled;
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
                factionKeys.add(KOMEAlliance.normalizeFactionKey(faction.codeName()));
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

    private static LOTRFactionRelations.Relation getDefaultRelation(String factionA, String factionB) {
        LOTRFaction a = KOMEAlliance.findLotrFaction(factionA);
        LOTRFaction b = KOMEAlliance.findLotrFaction(factionB);
        if (a == null || b == null || a == b) {
            return LOTRFactionRelations.Relation.NEUTRAL;
        }
        LOTRFactionRelations.Relation relation = getDefaultRelationReflective(a, b);
        return relation == null ? LOTRFactionRelations.Relation.NEUTRAL : relation;
    }

    private static LOTRFactionRelations.Relation getDefaultRelationReflective(LOTRFaction a, LOTRFaction b) {
        try {
            java.lang.reflect.Method method = LOTRFactionRelations.class.getDeclaredMethod("getFromDefaultMap", LOTRFactionRelations.FactionPair.class);
            method.setAccessible(true);
            Object value = method.invoke(null, new LOTRFactionRelations.FactionPair(a, b));
            return value instanceof LOTRFactionRelations.Relation ? (LOTRFactionRelations.Relation) value : null;
        } catch (Throwable ignored) {
            return LOTRFactionRelations.getRelations(a, b);
        }
    }

    private static String relationName(LOTRFactionRelations.Relation relation) {
        if (relation == LOTRFactionRelations.Relation.MORTAL_ENEMY) {
            return "Mortal Enemy";
        }
        if (relation == LOTRFactionRelations.Relation.ENEMY) {
            return "Enemy";
        }
        if (relation == LOTRFactionRelations.Relation.FRIEND) {
            return "Friend";
        }
        if (relation == LOTRFactionRelations.Relation.ALLY) {
            return "Ally";
        }
        return "Neutral";
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
        factionKingKeys = new ArrayList();
        requestOptions = new ArrayList();
        trackRecords = new HashMap();
        militaryContexts = new HashMap();
        militaryCompanies = new HashMap();
        summary = "Alliances: 0";
        viewerIsKing = false;
        viewerFactionHasKing = false;
        viewerIsAdmin = false;
        operatorViewEnabled = false;
        for (Object object : rawLines) {
            String line = String.valueOf(object);
            String[] parts = line.split("\t", -1);
            if (parts.length == 0) {
                continue;
            }
            if ("SUMMARY".equals(parts[0]) && parts.length >= 2) {
                summary = "Alliances: " + parts[1];
            } else if ("VIEWER".equals(parts[0]) && parts.length >= 3) {
                viewerFactionKey = KOMEAlliance.normalizeFactionKey(parts[1]);
                viewerFactionName = parts[2].length() == 0 ? "No pledged faction" : parts[2];
                viewerIsKing = parts.length > 3 && "1".equals(parts[3]);
                viewerFactionHasKing = parts.length > 4 && "1".equals(parts[4]);
                viewerIsAdmin = parts.length > 5 && "1".equals(parts[5]);
                operatorViewEnabled = parts.length > 6 && "1".equals(parts[6]);
            } else if ("CONFIG".equals(parts[0]) && parts.length >= 5) {
                configSummary = titleCase(parts[1]) + " | Waypoints " + ("1".equals(parts[3]) ? "on" : "off")
                    + ("1".equals(parts[4]) ? " | bypass" : "");
            } else if ("KING".equals(parts[0]) && parts.length >= 2) {
                factionKingKeys.add(KOMEAlliance.normalizeFactionKey(parts[1]));
            } else if ("REQUEST_OPTION".equals(parts[0]) && parts.length >= 6) {
                requestOptions.add(new RequestOption(parts));
            } else if ("TRACK".equals(parts[0]) && parts.length >= 25) {
                TrackRecord track = new TrackRecord(parts);
                trackRecords.put(trackKey(track.pair, track.side, track.type), track);
            } else if ("MILITARY_CONTEXT".equals(parts[0]) && parts.length >= 15) {
                MilitaryContext context = new MilitaryContext(parts);
                militaryContexts.put(militaryKey(context.pair, context.nativeFaction), context);
            } else if ("MILITARY_COMPANY".equals(parts[0]) && parts.length >= 16) {
                MilitaryCompany company = new MilitaryCompany(parts);
                String key = militaryKey(company.pair, company.nativeFaction);
                List values = (List) militaryCompanies.get(key);
                if (values == null) {
                    values = new ArrayList();
                    militaryCompanies.put(key, values);
                }
                values.add(company);
            } else if ("ALLIANCE".equals(parts[0]) && parts.length >= 10) {
                records.add(new Record(parts));
            }
        }
    }

    private static String trackKey(String pair, String side, String type) {
        return pair + "|" + KOMEAlliance.normalizeFactionKey(side) + "|" + KOMEAlliance.normalizeType(type);
    }

    private static String militaryKey(String pair, String nativeFaction) {
        return pair + "|" + KOMEAlliance.normalizeFactionKey(nativeFaction);
    }

    private static String titleCase(String value) {
        return value == null || value.length() == 0 ? "Standard"
            : Character.toUpperCase(value.charAt(0)) + value.substring(1).toLowerCase();
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static final class RequestOption {
        final String faction;
        final boolean civil;
        final boolean military;
        final boolean trade;
        final boolean receiverHasKing;

        private RequestOption(String[] parts) {
            faction = KOMEAlliance.normalizeFactionKey(parts[1]);
            civil = "1".equals(parts[2]);
            military = "1".equals(parts[3]);
            trade = "1".equals(parts[4]);
            receiverHasKing = "1".equals(parts[5]);
        }

        boolean allowed(int type) {
            return type == 1 ? military : type == 2 ? trade : civil;
        }
    }

    static final class TrackRecord {
        final String pair;
        final String side;
        final String partner;
        final String type;
        final String status;
        final int tier;
        final int targetTier;
        final String quotaName;
        final int quotaRequired;
        final int quotaDelivered;
        final String activityLabel;
        final int activityRequired;
        final int activityProgress;
        final int populationRequired;
        final int populationProgress;
        final boolean completed;
        final boolean waived;
        final boolean graceActive;
        final long graceEnd;
        final boolean successionActive;
        final long successionEnd;
        final String benefit;
        final boolean canManage;
        final String actionReason;

        private TrackRecord(String[] parts) {
            pair = parts[1]; side = KOMEAlliance.normalizeFactionKey(parts[2]);
            partner = KOMEAlliance.normalizeFactionKey(parts[3]); type = KOMEAlliance.normalizeType(parts[4]);
            status = parts[5]; tier = parseInt(parts[6]); targetTier = parseInt(parts[7]); quotaName = parts[8];
            quotaRequired = parseInt(parts[9]); quotaDelivered = parseInt(parts[10]); activityLabel = parts[11];
            activityRequired = parseInt(parts[12]); activityProgress = parseInt(parts[13]);
            populationRequired = parseInt(parts[14]); populationProgress = parseInt(parts[15]);
            completed = "1".equals(parts[16]); waived = "1".equals(parts[17]); graceActive = "1".equals(parts[18]);
            graceEnd = parseLong(parts[19]); successionActive = "1".equals(parts[20]); successionEnd = parseLong(parts[21]);
            benefit = parts[22]; canManage = "1".equals(parts[23]); actionReason = parts[24];
        }
    }

    static final class MilitaryContext {
        final String pair;
        final String nativeFaction;
        final String supportingFaction;
        final String state;
        final String nativeKingName;
        final String nativeKingId;
        final String supportingKingName;
        final String supportingKingId;
        final String wars;
        final String opponents;
        final int eligible;
        final int used;
        final int available;
        final String reason;

        private MilitaryContext(String[] parts) {
            pair = parts[1]; nativeFaction = KOMEAlliance.normalizeFactionKey(parts[2]);
            supportingFaction = KOMEAlliance.normalizeFactionKey(parts[3]); state = parts[4];
            nativeKingName = parts[5]; nativeKingId = parts[6]; supportingKingName = parts[7]; supportingKingId = parts[8];
            wars = parts[9]; opponents = parts[10]; eligible = parseInt(parts[11]); used = parseInt(parts[12]);
            available = parseInt(parts[13]); reason = parts[14];
        }
    }

    static final class MilitaryCompany {
        final String pair;
        final String nativeFaction;
        final String supportingFaction;
        final String id;
        final String name;
        final String owner;
        final String controller;
        final boolean controllerIsKing;
        final String wars;
        final String tendency;
        final String movementState;
        final String cleanupState;
        final String reason;
        final int population;
        final String actions;

        private MilitaryCompany(String[] parts) {
            pair = parts[1]; nativeFaction = KOMEAlliance.normalizeFactionKey(parts[2]);
            supportingFaction = KOMEAlliance.normalizeFactionKey(parts[3]); id = parts[4]; name = parts[5]; owner = parts[6];
            controller = parts[7]; controllerIsKing = "1".equals(parts[8]); wars = parts[9]; tendency = parts[10];
            movementState = parts[11]; cleanupState = parts[12]; reason = parts[13]; population = parseInt(parts[14]); actions = parts[15];
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
        final int alliedTradesDelivered;
        final int militaryNextKillRequirement;
        final int offensiveCapacityMilestone;
        final String civilStatus;
        final String militaryStatus;
        final String tradeStatus;
        final String contributorFaction;
        final String allyFaction;
        final int contributorCivilCompleted;
        final int contributorMilitaryCompleted;
        final int contributorTradeCompleted;
        final int allyCivilCompleted;
        final int allyMilitaryCompleted;
        final int allyTradeCompleted;
        final boolean contributorWaived;
        final boolean allyWaived;
        final long contributorDeadline;
        final long allyDeadline;
        final String contributorCivilQuota;
        final String contributorMilitaryQuota;
        final String contributorTradeQuota;
        final String allyCivilQuota;
        final String allyMilitaryQuota;
        final String allyTradeQuota;
        final String civilPendingReceiver;
        final String militaryPendingReceiver;
        final String tradePendingReceiver;

        private Record(String[] parts) {
            keyA = KOMEAlliance.normalizeFactionKey(parts[1]);
            keyB = KOMEAlliance.normalizeFactionKey(parts[2]);
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
            alliedTradesDelivered = parts.length > 17 ? parseInt(parts[17]) : civilTradeDelivered;
            militaryNextKillRequirement = parts.length > 18 ? parseInt(parts[18]) : 0;
            offensiveCapacityMilestone = parts.length > 19 ? parseInt(parts[19]) : 0;
            civilStatus = parts.length > 20 ? parts[20] : statusFromTier(civilTier);
            tradeStatus = parts.length > 21 ? parts[21] : statusFromTier(tradeTier);
            militaryStatus = parts.length > 22 ? parts[22] : statusFromTier(militaryTier);
            contributorFaction = parts.length > 23 ? parts[23] : keyA;
            allyFaction = parts.length > 24 ? parts[24] : keyB;
            contributorCivilCompleted = parts.length > 25 ? parseInt(parts[25]) : civilTier;
            contributorTradeCompleted = parts.length > 26 ? parseInt(parts[26]) : tradeTier;
            contributorMilitaryCompleted = parts.length > 27 ? parseInt(parts[27]) : militaryTier;
            allyCivilCompleted = parts.length > 28 ? parseInt(parts[28]) : civilTier;
            allyTradeCompleted = parts.length > 29 ? parseInt(parts[29]) : tradeTier;
            allyMilitaryCompleted = parts.length > 30 ? parseInt(parts[30]) : militaryTier;
            contributorWaived = parts.length > 31 && "1".equals(parts[31]);
            allyWaived = parts.length > 32 && "1".equals(parts[32]);
            contributorDeadline = parts.length > 33 ? parseLong(parts[33]) : 0L;
            allyDeadline = parts.length > 34 ? parseLong(parts[34]) : 0L;
            civilPendingReceiver = parts.length > 35 ? parts[35] : keyB;
            tradePendingReceiver = parts.length > 36 ? parts[36] : keyB;
            militaryPendingReceiver = parts.length > 37 ? parts[37] : keyB;
            contributorCivilQuota = parts.length > 38 ? parts[38] : "T" + (civilTier + 1) + ": quota not rolled";
            contributorMilitaryQuota = parts.length > 39 ? parts[39] : "T" + (militaryTier + 1) + ": quota not rolled";
            contributorTradeQuota = parts.length > 40 ? parts[40] : "T" + (tradeTier + 1) + ": quota not rolled";
            allyCivilQuota = parts.length > 41 ? parts[41] : "Unknown";
            allyMilitaryQuota = parts.length > 42 ? parts[42] : "Unknown";
            allyTradeQuota = parts.length > 43 ? parts[43] : "Unknown";
        }

        private static String statusFromTier(int tier) {
            return tier == KOMEAlliance.PENDING ? "pending" : tier >= 0 ? "active" : "none";
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

        boolean hasAnyAcceptedAlliance() {
            return civilTier >= 0 || militaryTier >= 0 || tradeTier >= 0;
        }

        boolean needsQuotaRoll(int type) {
            int tier = getTier(type);
            return tier >= 0 && tier < (type == 1 ? 3 : 2) && quota(type, false).indexOf("quota not rolled") >= 0;
        }

        String quota(int type, boolean alliedSide) {
            if (alliedSide) {
                return type == 0 ? allyCivilQuota : type == 1 ? allyMilitaryQuota : allyTradeQuota;
            }
            return type == 0 ? contributorCivilQuota : type == 1 ? contributorMilitaryQuota : contributorTradeQuota;
        }

        int completedTier(int type, boolean alliedSide) {
            if (alliedSide) {
                return type == 0 ? allyCivilCompleted : type == 1 ? allyMilitaryCompleted : allyTradeCompleted;
            }
            return type == 0 ? contributorCivilCompleted : type == 1 ? contributorMilitaryCompleted : contributorTradeCompleted;
        }

        String status(int type) {
            return type == 0 ? civilStatus : type == 1 ? militaryStatus : tradeStatus;
        }

        String pendingReceiver(int type) {
            return type == 0 ? civilPendingReceiver : type == 1 ? militaryPendingReceiver : tradePendingReceiver;
        }

        boolean provisional() {
            long now = System.currentTimeMillis();
            return contributorDeadline > now || allyDeadline > now;
        }

        String strongestAgreement() {
            int type = militaryTier >= tradeTier && militaryTier >= civilTier ? 1 : tradeTier >= civilTier ? 2 : 0;
            int tier = getTier(type);
            return tier == KOMEAlliance.PENDING ? (type == 0 ? "Civil" : type == 1 ? "Military" : "Trade") + " Pending"
                : tier < 0 ? "No accepted track" : (type == 0 ? "Civil" : type == 1 ? "Military" : "Trade") + " T" + tier;
        }

        String kingStatus() {
            return factionA + ": " + (factionKingKeys.contains(keyA) ? "King" : "Kingless") + " | "
                + factionB + ": " + (factionKingKeys.contains(keyB) ? "King" : "Kingless");
        }

        String graceText() {
            long deadline = Math.max(contributorDeadline, allyDeadline);
            long remaining = Math.max(0L, deadline - System.currentTimeMillis());
            long minutes = remaining / 60000L;
            long days = minutes / 1440L;
            long hours = minutes % 1440L / 60L;
            long mins = minutes % 60L;
            return "Grace until " + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date(deadline))
                + " (" + days + "d " + hours + "h " + mins + "m remaining)";
        }

        TrackRecord track(int type, boolean alliedSide) {
            String side = alliedSide ? allyFaction : contributorFaction;
            return (TrackRecord) trackRecords.get(trackKey(KOMEAlliance.pairKey(keyA, keyB), side,
                type == 0 ? KOMEAlliance.CIVIL : type == 1 ? KOMEAlliance.MILITARY : KOMEAlliance.TRADE));
        }

        MilitaryContext militaryContext(boolean alliedSide) {
            String side = alliedSide ? allyFaction : contributorFaction;
            return (MilitaryContext) militaryContexts.get(militaryKey(KOMEAlliance.pairKey(keyA, keyB), side));
        }

        List militaryCompanies(boolean alliedSide) {
            String side = alliedSide ? allyFaction : contributorFaction;
            List values = (List) militaryCompanies.get(militaryKey(KOMEAlliance.pairKey(keyA, keyB), side));
            return values == null ? java.util.Collections.EMPTY_LIST : values;
        }
    }

}
