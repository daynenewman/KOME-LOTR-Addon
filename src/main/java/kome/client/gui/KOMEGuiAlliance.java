package kome.client.gui;

import kome.client.KOMEMinecraftClient;
import kome.common.network.KOMEPacketAllianceRequest;
import kome.common.network.KOMEPacketHandler;
import lotr.client.gui.LOTRGuiMenu;
import lotr.client.gui.LOTRGuiMenuBase;
import lotr.common.fac.LOTRFaction;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

public class KOMEGuiAlliance extends LOTRGuiMenuBase {
    private static final String[] TYPES = new String[] {"Civil", "Military", "Trade"};
    private static final String[][] BENEFITS = new String[][] {
        {"Alliance begins", "Use faction waypoints", "Hire farmhands"},
        {"Alliance begins", "Hire 1 unit", "Attack through faction", "Command armies", "Spawn captain"},
        {"Alliance begins", "Build in faction land", "Produce merchant crop"}
    };
    private static List rawLines = new ArrayList();
    private static List records = new ArrayList();
    private static List factionKeys = new ArrayList();
    private static List factionNames = new ArrayList();
    private static String summary = "Alliances: 0";
    private static String viewerFactionKey = "";
    private static String viewerFactionName = "No pledged faction";

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

    @Override
    public void initGui() {
        xSize = 430;
        ySize = 330;
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
        drawPanel();
        drawHeader();
        if (createMode) {
            drawCreate();
        } else {
            drawList(mouseX, mouseY);
            drawDetail();
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
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
        } else if (button.id >= 10 && button.id <= 12) {
            selectedType = button.id - 10;
            detailScroll = 0;
        } else if (selected >= 0 && selected < records.size()) {
            Record record = (Record) records.get(selected);
            if (button.id == 20) {
                KOMEMinecraftClient.sendChat("/alliance accept " + typeKey(selectedType) + " " + record.keyA + " " + record.keyB);
                requestAlliances();
            } else if (button.id == 21) {
                KOMEMinecraftClient.sendChat("/alliance goods " + record.keyA + " " + record.keyB);
                mc.displayGuiScreen(null);
            } else if (button.id == 22) {
                KOMEMinecraftClient.sendChat("/alliance claimGoods " + record.keyA + " " + record.keyB);
                requestAlliances();
            } else if (button.id == 23) {
                String type = selectedType == 1 ? "military" : selectedType == 2 ? "trade" : "";
                if (!type.isEmpty()) {
                    KOMEMinecraftClient.sendChat("/alliance roll " + type + " " + record.keyA + " " + record.keyB);
                }
            } else if (button.id == 24) {
                KOMEMinecraftClient.sendChat("/alliance break " + typeKey(selectedType) + " " + record.keyA + " " + record.keyB);
                selected = -1;
                requestAlliances();
            } else if (button.id == 25) {
                permissionMode = !permissionMode;
                detailScroll = 0;
            }
        }
        configureButtons();
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (!createMode && selected >= 0 && selected < records.size()) {
            int max = getMaxDetailScroll((Record) records.get(selected));
            if (wheel > 0) {
                detailScroll = Math.max(0, detailScroll - 18);
            } else if (wheel < 0) {
                detailScroll = Math.min(max, detailScroll + 18);
            }
            return;
        }
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
        int x = guiLeft + 18;
        int y = guiTop + 58;
        for (int i = 0; i < getVisibleRows() && scroll + i < records.size(); i++) {
            int rowY = y + i * 35;
            if (mouseX >= x && mouseX < x + 138 && mouseY >= rowY && mouseY < rowY + 29) {
                selected = scroll + i;
                detailScroll = 0;
                return;
            }
        }
    }

    private void configureButtons() {
        buttonList.clear();
        buttonList.add(new GuiButton(1, guiLeft + 15, guiTop + 12, 48, 20, "Menu"));
        buttonList.add(new GuiButton(2, guiLeft + xSize - 68, guiTop + 12, 52, 20, createMode ? "List" : "New"));
        if (createMode) {
            receiverIndex = ensureReceiverIndex(receiverIndex, 1);
            int center = guiLeft + xSize / 2;
            buttonList.add(new GuiButton(6, center - 96, guiTop + 121, 24, 20, "<"));
            buttonList.add(new GuiButton(7, center + 72, guiTop + 121, 24, 20, ">"));
            GuiButton receiverLeft = new GuiButton(3, center - 126, guiTop + 191, 24, 20, "<");
            receiverLeft.enabled = hasSelectableReceiver(createType);
            buttonList.add(receiverLeft);
            GuiButton receiverRight = new GuiButton(4, center + 102, guiTop + 191, 24, 20, ">");
            receiverRight.enabled = hasSelectableReceiver(createType);
            buttonList.add(receiverRight);
            GuiButton send = new GuiButton(5, guiLeft + 163, guiTop + ySize - 58, 104, 20, "Send Request");
            send.enabled = canSendRequest();
            buttonList.add(send);
            return;
        }
        buttonList.add(new GuiButton(10, guiLeft + 178, guiTop + 52, 62, 18, "Civil"));
        buttonList.add(new GuiButton(11, guiLeft + 244, guiTop + 52, 76, 18, "Military"));
        buttonList.add(new GuiButton(12, guiLeft + 324, guiTop + 52, 62, 18, "Trade"));
        for (Object object : buttonList) {
            GuiButton button = (GuiButton) object;
            if (button.id >= 10 && button.id <= 12) {
                button.enabled = selectedType != button.id - 10;
            }
        }
        if (selected < 0 || selected >= records.size()) {
            return;
        }
        Record record = (Record) records.get(selected);
        GuiButton accept = new GuiButton(20, guiLeft + 178, guiTop + ySize - 36, 58, 20, "Accept");
        accept.enabled = record.hasPending(selectedType);
        buttonList.add(accept);
        buttonList.add(new GuiButton(21, guiLeft + 240, guiTop + ySize - 36, 62, 20, "Ledger"));
        GuiButton claim = new GuiButton(22, guiLeft + 306, guiTop + ySize - 36, 58, 20, "Claim");
        claim.enabled = viewerFactionKey.equals(record.keyB);
        buttonList.add(claim);
        GuiButton roll = new GuiButton(23, guiLeft + 240, guiTop + ySize - 61, 62, 20, "Roll");
        roll.enabled = record.needsQuotaRoll(selectedType);
        buttonList.add(roll);
        buttonList.add(new GuiButton(24, guiLeft + 178, guiTop + ySize - 61, 58, 20, "Break"));
        buttonList.add(new GuiButton(25, guiLeft + 306, guiTop + ySize - 61, 58, 20, permissionMode ? "Tiers" : "Perms"));
    }

    private void drawPanel() {
        Gui.drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, 0xDD11120F);
        Gui.drawRect(guiLeft + 4, guiTop + 4, guiLeft + xSize - 4, guiTop + ySize - 4, 0xFFE7D1A4);
        Gui.drawRect(guiLeft + 8, guiTop + 38, guiLeft + xSize - 8, guiTop + 40, 0xFF5D311E);
        if (createMode) {
            Gui.drawRect(guiLeft + 58, guiTop + 62, guiLeft + xSize - 58, guiTop + ySize - 30, 0x553B250E);
            Gui.drawRect(guiLeft + 63, guiTop + 67, guiLeft + xSize - 63, guiTop + ySize - 35, 0xFFEFD9AA);
            Gui.drawRect(guiLeft + 76, guiTop + 96, guiLeft + xSize - 76, guiTop + 98, 0x664A2A18);
        } else {
            Gui.drawRect(guiLeft + 164, guiTop + 45, guiLeft + 166, guiTop + ySize - 12, 0xAA5D311E);
            Gui.drawRect(guiLeft + 14, guiTop + 52, guiLeft + 158, guiTop + ySize - 22, 0x33281610);
            Gui.drawRect(guiLeft + 174, guiTop + 77, guiLeft + xSize - 16, guiTop + ySize - 90, 0x22281610);
        }
    }

    private void drawHeader() {
        drawCenteredString(fontRendererObj, "KOME Alliances", guiLeft + xSize / 2, guiTop + 16, 0x2B160D);
        fontRendererObj.drawString(summary, guiLeft + 75, guiTop + 18, 0x70401C);
        fontRendererObj.drawString("Your faction: " + trim(viewerFactionName, 220), guiLeft + 18, guiTop + 43, 0x2B160D);
    }

    private void drawList(int mouseX, int mouseY) {
        if (records.isEmpty()) {
            fontRendererObj.drawString("No alliances yet.", guiLeft + 27, guiTop + 82, 0x3A2115);
            fontRendererObj.drawString("Press New.", guiLeft + 46, guiTop + 96, 0x70401C);
            return;
        }
        int x = guiLeft + 18;
        int y = guiTop + 58;
        for (int i = 0; i < getVisibleRows() && scroll + i < records.size(); i++) {
            Record record = (Record) records.get(scroll + i);
            int rowY = y + i * 35;
            boolean active = selected == scroll + i;
            boolean hover = mouseX >= x && mouseX < x + 138 && mouseY >= rowY && mouseY < rowY + 29;
            Gui.drawRect(x, rowY, x + 138, rowY + 29, active ? 0xFF51331E : hover ? 0xFF7A542F : 0xFF2F2117);
            fontRendererObj.drawString(trim(record.factionB, 126), x + 6, rowY + 5, 0xFFF2E5BC);
            fontRendererObj.drawString(statusLine(record), x + 6, rowY + 17, 0xFFD9B56A);
        }
    }

    private void drawDetail() {
        if (createMode) {
            return;
        }
        if (selected < 0 || selected >= records.size()) {
            fontRendererObj.drawString("Select an alliance to manage it.", guiLeft + 188, guiTop + 112, 0x3A2115);
            return;
        }
        Record record = (Record) records.get(selected);
        int x = guiLeft + 188;
        fontRendererObj.drawString(trim(record.factionA, 96), x, guiTop + 82, 0x2B160D);
        fontRendererObj.drawString("->", x + 100, guiTop + 82, 0x70401C);
        fontRendererObj.drawString(trim(record.factionB, 96), x + 118, guiTop + 82, 0x2B160D);
        int tier = record.getTier(selectedType);
        fontRendererObj.drawString(TYPES[selectedType] + " " + displayTier(tier), x, guiTop + 100, tier >= 0 ? 0x275018 : 0x8A2B18);
        if (permissionMode) {
            drawPermissionSummary(record, x, guiTop + 112, 220, 126);
        } else {
            drawTierSections(record, x, guiTop + 112, 220, 126);
        }
    }

    private void drawTierSections(Record record, int x, int y, int width, int height) {
        int max = getMaxDetailScroll(record);
        detailScroll = Math.max(0, Math.min(max, detailScroll));
        enableScissor(x, y, width, height);
        String[] benefits = BENEFITS[selectedType];
        int tier = record.getTier(selectedType);
        int cursorY = y - detailScroll;
        for (int i = 0; i < benefits.length; i++) {
            int panelHeight = getTierPanelHeight(i);
            boolean unlocked = tier >= i;
            boolean current = tier == i - 1 || tier == i;
            int fill = unlocked ? 0xFFE2C98F : current ? 0xFFD6BA7E : 0xFFC7B48D;
            Gui.drawRect(x, cursorY, x + width, cursorY + panelHeight - 4, 0xFF7A4A25);
            Gui.drawRect(x + 1, cursorY + 1, x + width - 1, cursorY + panelHeight - 5, fill);
            fontRendererObj.drawString("Tier " + i, x + 6, cursorY + 6, unlocked ? 0x244712 : 0x6B4F37);
            fontRendererObj.drawString(unlocked ? "Unlocked" : "Locked", x + width - 52, cursorY + 6, unlocked ? 0x275018 : 0x7B5E42);
            fontRendererObj.drawString(trim(benefits[i], width - 14), x + 6, cursorY + 18, 0x2B160D);
            String requirement = getTierRequirement(record, i);
            List lines = fontRendererObj.listFormattedStringToWidth(requirement, width - 12);
            for (int line = 0; line < lines.size() && line < 4; line++) {
                fontRendererObj.drawString(String.valueOf(lines.get(line)), x + 6, cursorY + 30 + line * 10, 0x4B301E);
            }
            cursorY += panelHeight;
        }
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    private int getTierPanelHeight(int tierIndex) {
        return 78;
    }

    private int getMaxDetailScroll(Record record) {
        int visibleHeight = 126;
        return Math.max(0, getDetailContentHeight(record) - visibleHeight);
    }

    private int getDetailContentHeight(Record record) {
        if (permissionMode) {
            return getPermissionContentHeight(record);
        }
        int height = 0;
        String[] benefits = BENEFITS[selectedType];
        for (int i = 0; i < benefits.length; i++) {
            height += getTierPanelHeight(i);
        }
        return height;
    }

    private int getPermissionContentHeight(Record record) {
        int height = 10;
        for (int type = 0; type < TYPES.length; type++) {
            height += 16 + BENEFITS[type].length * 12;
        }
        return height;
    }

    private void drawPermissionSummary(Record record, int x, int y, int width, int height) {
        int max = getMaxDetailScroll(record);
        detailScroll = Math.max(0, Math.min(max, detailScroll));
        enableScissor(x, y, width, height);
        int cursorY = y + 2 - detailScroll;
        for (int type = 0; type < TYPES.length; type++) {
            int tier = record.getTier(type);
            fontRendererObj.drawString(TYPES[type] + " Permissions", x + 4, cursorY, 0x2B160D);
            cursorY += 14;
            for (int i = 0; i < BENEFITS[type].length; i++) {
                boolean unlocked = tier >= i;
                int color = unlocked ? 0x275018 : 0x7B5E42;
                String status = unlocked ? "Unlocked" : "Locked";
                fontRendererObj.drawString(status + ": " + BENEFITS[type][i], x + 10, cursorY, color);
                cursorY += 12;
            }
            cursorY += 4;
        }
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    private String getTierRequirement(Record record, int tierIndex) {
        if (tierIndex == 0) {
            return "Requirement: receiving faction accepts the request.";
        }
        if (selectedType == 0) {
            return tierIndex == 1 ? "Requirement: deposit 1000 coins." : "Requirement: buy or sell 500 coins with " + record.factionB + " traders. Progress: " + Math.min(record.civilTradeDelivered, 500) + "/500 coins.";
        }
        if (selectedType == 1) {
            if (tierIndex == 1) {
                return "Requirement: " + quotaStatus(record.militaryFood, record.militaryFoodDelivered);
            }
            if (tierIndex == 2) {
                return "Requirement: kill 2000 enemies.";
            }
            if (tierIndex == 3) {
                return "Requirement: 250 pop build in faction and waypoint battle with them.";
            }
            return "Requirement: 3k alignment, 50 pop, and 30k coins.";
        }
        return tierIndex == 1 ? "Requirement: 5000 coins and " + quotaStatus(record.tradeFood, record.tradeFoodDelivered) : "Requirement: earn 50 farmer pop points.";
    }

    private void enableScissor(int x, int y, int width, int height) {
        ScaledResolution scaled = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int scale = scaled.getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x * scale, (this.height - y - height) * scale, width * scale, height * scale);
    }

    private void drawCreate() {
        int center = guiLeft + xSize / 2;
        int labelX = center - 130;
        int receiverBoxWidth = 180;
        String receiverName = hasSelectableReceiver(createType) && isSelectableReceiver(receiverIndex) ? factionName(receiverIndex) : "No eligible faction";
        drawCenteredString(fontRendererObj, "Send a one-way alliance request", guiLeft + xSize / 2, guiTop + 77, 0x2B160D);
        fontRendererObj.drawString("Alliance Type", labelX, guiTop + 105, 0x70401C);
        drawBox(center - 55, guiTop + 123, 110, trim(TYPES[createType], 100));
        fontRendererObj.drawString("Sender", labelX, guiTop + 151, 0x70401C);
        drawBox(center - 105, guiTop + 148, 210, trim(viewerFactionName, 200));
        fontRendererObj.drawString("Receiver", labelX, guiTop + 181, 0x70401C);
        drawBox(center - receiverBoxWidth / 2, guiTop + 193, receiverBoxWidth, trim(receiverName, receiverBoxWidth - 10));
        List lines = fontRendererObj.listFormattedStringToWidth(getCreateMessage(), 286);
        int messageX = center - 143;
        for (int i = 0; i < lines.size() && i < 3; i++) {
            fontRendererObj.drawString(String.valueOf(lines.get(i)), messageX, guiTop + 224 + i * 10, canSendRequest() ? 0x3A2115 : 0x8A2B18);
        }
    }

    private void drawBox(int x, int y, int width, String text) {
        Gui.drawRect(x, y, x + width, y + 16, 0xFF24170E);
        Gui.drawRect(x + 1, y + 1, x + width - 1, y + 15, 0xFFEFD9AA);
        fontRendererObj.drawString(text, x + 5, y + 5, 0x2B160D);
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
        if (isEnemyAlliance(viewerFactionKey, receiver)) {
            return "Enemy factions cannot form alliances.";
        }
        if (hasAllAllianceTypes(receiver)) {
            return "All alliance types already exist with this faction.";
        }
        if (getAllianceTier(receiver, createType) != -1) {
            return "A " + TYPES[createType] + " alliance already exists with this faction.";
        }
        return "This sends only a " + TYPES[createType] + " request. The other alliance types must be requested separately.";
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

    private String statusLine(Record record) {
        return "C " + displayTier(record.civilTier) + "  M " + displayTier(record.militaryTier) + "  T " + displayTier(record.tradeTier);
    }

    private String displayTier(int tier) {
        return tier == -2 ? "Pending" : tier < 0 ? "None" : "T" + tier;
    }

    private String getNextRequirement(Record record) {
        int tier = record.getTier(selectedType);
        if (tier == -2) {
            return "Waiting for the receiving king to accept.";
        }
        if (selectedType == 0) {
            if (tier == 0) {
                return "Deposit 1000 coins.";
            }
            if (tier == 1) {
                return "Trade 500 coins worth of goods. Staff confirms this tier for now.";
            }
            return "Civil alliance complete.";
        }
        if (selectedType == 1) {
            if (tier == 0) {
                return quotaStatus(record.militaryFood, record.militaryFoodDelivered);
            }
            if (tier == 1) {
                return "Kill 2000 enemies. Tracker not wired yet.";
            }
            if (tier == 2) {
                return "250 pop build in faction and waypoint battle with them.";
            }
            if (tier == 3) {
                return "3k alignment, 50 pop, and 30k coins.";
            }
            return "Military alliance complete.";
        }
        if (tier == 0) {
            return "Deposit 5000 coins and " + quotaStatus(record.tradeFood, record.tradeFoodDelivered);
        }
        if (tier == 1) {
            return "Earn 50 farmer pop points. Tracker not wired yet.";
        }
        return "Trade alliance complete.";
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
            && !isEnemyAlliance(viewerFactionKey, receiver)
            && !hasAllAllianceTypes(receiver)
            && getAllianceTier(receiver, createType) == -1;
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
        return Math.max(5, (ySize - 86) / 35);
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
        LOTRFaction a = LOTRFaction.forName(factionA);
        LOTRFaction b = LOTRFaction.forName(factionB);
        return a != null && b != null && (a.isMortalEnemy(b) || b.isMortalEnemy(a) || a.isBadRelation(b) || b.isBadRelation(a));
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

    private static int wrap(int index, int size) {
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
        private final String militaryFood;
        private final int militaryFoodDelivered;
        private final String tradeFood;
        private final int tradeFoodDelivered;
        private final int civilTradeDelivered;

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
        }

        private int getTier(int type) {
            return type == 0 ? civilTier : type == 1 ? militaryTier : tradeTier;
        }

        private boolean hasPending() {
            return civilTier == -2 || militaryTier == -2 || tradeTier == -2;
        }

        private boolean hasPending(int type) {
            return getTier(type) == -2;
        }

        private boolean needsQuotaRoll(int type) {
            return type == 1 && militaryTier == 0 && militaryFood.trim().isEmpty()
                || type == 2 && tradeTier == 0 && tradeFood.trim().isEmpty();
        }
    }
}
