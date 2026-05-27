package kome.client.gui;

import kome.client.KOMEMinecraftClient;
import kome.common.network.KOMEPacketAllianceRequest;
import kome.common.network.KOMEPacketHandler;
import lotr.client.gui.LOTRGuiMenu;
import lotr.client.gui.LOTRGuiMenuBase;
import lotr.common.fac.LOTRFaction;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import org.lwjgl.input.Mouse;

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
    private int receiverIndex = 1;
    private int scroll;
    private boolean createMode;

    public static void update(List updatedLines) {
        rawLines = updatedLines == null ? new ArrayList() : new ArrayList(updatedLines);
        parseRecords();
    }

    @Override
    public void initGui() {
        xSize = 360;
        ySize = 260;
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
        } else if (button.id == 3) {
            receiverIndex = nextReceiver(-1);
        } else if (button.id == 4) {
            receiverIndex = nextReceiver(1);
        } else if (button.id == 5) {
            sendRequestCommand();
        } else if (button.id >= 10 && button.id <= 12) {
            selectedType = button.id - 10;
        } else if (selected >= 0 && selected < records.size()) {
            Record record = (Record) records.get(selected);
            if (button.id == 20) {
                KOMEMinecraftClient.sendChat("/alliance accept " + record.keyA + " " + record.keyB);
                requestAlliances();
            } else if (button.id == 21) {
                KOMEMinecraftClient.sendChat("/alliance goods " + record.keyA + " " + record.keyB);
                mc.displayGuiScreen(null);
            } else if (button.id == 22) {
                KOMEMinecraftClient.sendChat("/alliance claimGoods " + record.keyA + " " + record.keyB);
                requestAlliances();
            } else if (button.id == 23) {
                KOMEMinecraftClient.sendChat("/alliance roll " + (selectedType == 1 ? "military" : "trade") + " " + record.keyA + " " + record.keyB);
                requestAlliances();
            } else if (button.id == 24) {
                KOMEMinecraftClient.sendChat("/alliance break " + record.keyA + " " + record.keyB);
                selected = -1;
                requestAlliances();
            }
        }
        configureButtons();
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
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
            if (mouseX >= x && mouseX < x + 128 && mouseY >= rowY && mouseY < rowY + 29) {
                selected = scroll + i;
                return;
            }
        }
    }

    private void configureButtons() {
        buttonList.clear();
        buttonList.add(new GuiButton(1, guiLeft + 15, guiTop + 12, 48, 20, "Menu"));
        buttonList.add(new GuiButton(2, guiLeft + 292, guiTop + 12, 52, 20, createMode ? "List" : "New"));
        if (createMode) {
            buttonList.add(new GuiButton(3, guiLeft + 90, guiTop + 132, 24, 20, "<"));
            buttonList.add(new GuiButton(4, guiLeft + 246, guiTop + 132, 24, 20, ">"));
            GuiButton send = new GuiButton(5, guiLeft + 128, guiTop + 202, 104, 20, "Send Request");
            send.enabled = canSendRequest();
            buttonList.add(send);
            return;
        }
        buttonList.add(new GuiButton(10, guiLeft + 170, guiTop + 52, 54, 18, "Civil"));
        buttonList.add(new GuiButton(11, guiLeft + 226, guiTop + 52, 62, 18, "Military"));
        buttonList.add(new GuiButton(12, guiLeft + 290, guiTop + 52, 54, 18, "Trade"));
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
        GuiButton accept = new GuiButton(20, guiLeft + 170, guiTop + 224, 54, 20, "Accept");
        accept.enabled = record.hasPending();
        buttonList.add(accept);
        buttonList.add(new GuiButton(21, guiLeft + 226, guiTop + 224, 54, 20, "Deposit"));
        buttonList.add(new GuiButton(22, guiLeft + 282, guiTop + 224, 54, 20, "Claim"));
        GuiButton roll = new GuiButton(23, guiLeft + 226, guiTop + 199, 54, 20, "Roll");
        roll.enabled = record.needsQuotaRoll(selectedType);
        buttonList.add(roll);
        buttonList.add(new GuiButton(24, guiLeft + 170, guiTop + 199, 54, 20, "Break"));
    }

    private void drawPanel() {
        Gui.drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, 0xDD11120F);
        Gui.drawRect(guiLeft + 4, guiTop + 4, guiLeft + xSize - 4, guiTop + ySize - 4, 0xFFE7D1A4);
        Gui.drawRect(guiLeft + 8, guiTop + 38, guiLeft + xSize - 8, guiTop + 40, 0xFF5D311E);
        Gui.drawRect(guiLeft + 154, guiTop + 45, guiLeft + 156, guiTop + ySize - 12, 0xAA5D311E);
        Gui.drawRect(guiLeft + 14, guiTop + 52, guiLeft + 148, guiTop + 238, 0x33281610);
        Gui.drawRect(guiLeft + 164, guiTop + 77, guiLeft + 346, guiTop + 194, 0x22281610);
    }

    private void drawHeader() {
        drawCenteredString(fontRendererObj, "KOME Alliances", guiLeft + xSize / 2, guiTop + 16, 0x2B160D);
        fontRendererObj.drawString(summary, guiLeft + 75, guiTop + 18, 0x70401C);
        fontRendererObj.drawString("Your faction: " + trim(viewerFactionName, 150), guiLeft + 18, guiTop + 43, 0x2B160D);
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
            boolean hover = mouseX >= x && mouseX < x + 128 && mouseY >= rowY && mouseY < rowY + 29;
            Gui.drawRect(x, rowY, x + 128, rowY + 29, active ? 0xFF51331E : hover ? 0xFF7A542F : 0xFF2F2117);
            fontRendererObj.drawString(trim(record.factionB, 116), x + 6, rowY + 5, 0xFFF2E5BC);
            fontRendererObj.drawString(statusLine(record), x + 6, rowY + 17, 0xFFD9B56A);
        }
    }

    private void drawDetail() {
        if (createMode) {
            return;
        }
        if (selected < 0 || selected >= records.size()) {
            fontRendererObj.drawString("Select an alliance to manage it.", guiLeft + 178, guiTop + 104, 0x3A2115);
            return;
        }
        Record record = (Record) records.get(selected);
        int x = guiLeft + 170;
        fontRendererObj.drawString(trim(record.factionA, 76), x, guiTop + 82, 0x2B160D);
        fontRendererObj.drawString("->", x + 78, guiTop + 82, 0x70401C);
        fontRendererObj.drawString(trim(record.factionB, 76), x + 96, guiTop + 82, 0x2B160D);
        int tier = record.getTier(selectedType);
        fontRendererObj.drawString(TYPES[selectedType] + " " + displayTier(tier), x, guiTop + 100, tier >= 0 ? 0x275018 : 0x8A2B18);
        String[] benefits = BENEFITS[selectedType];
        for (int i = 0; i < benefits.length; i++) {
            int color = tier >= i ? 0x213915 : 0x7B6A52;
            fontRendererObj.drawString("T" + i + " " + benefits[i], x, guiTop + 118 + i * 12, color);
        }
        List wrapped = fontRendererObj.listFormattedStringToWidth("Next: " + getNextRequirement(record), 166);
        for (int i = 0; i < wrapped.size() && i < 4; i++) {
            fontRendererObj.drawString(String.valueOf(wrapped.get(i)), x, guiTop + 170 + i * 10, 0x3A2115);
        }
        fontRendererObj.drawString("Last: " + trim(record.lastUpdatedBy.length() == 0 ? "server" : record.lastUpdatedBy, 110), x, guiTop + 210, 0x70401C);
    }

    private void drawCreate() {
        int x = guiLeft + 50;
        fontRendererObj.drawString("Send a one-way alliance request", x, guiTop + 65, 0x2B160D);
        fontRendererObj.drawString("Sender", x, guiTop + 98, 0x70401C);
        drawBox(x + 58, guiTop + 95, 220, trim(viewerFactionName, 210));
        fontRendererObj.drawString("Receiver", x, guiTop + 136, 0x70401C);
        drawBox(x + 78, guiTop + 133, 126, trim(factionName(receiverIndex), 116));
        List lines = fontRendererObj.listFormattedStringToWidth(getCreateMessage(), 260);
        for (int i = 0; i < lines.size() && i < 3; i++) {
            fontRendererObj.drawString(String.valueOf(lines.get(i)), x, guiTop + 166 + i * 10, canSendRequest() ? 0x3A2115 : 0x8A2B18);
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
        String receiver = factionKey(receiverIndex);
        return !viewerFactionKey.isEmpty() && !viewerFactionKey.equals(receiver) && !isEnemyAlliance(viewerFactionKey, receiver);
    }

    private String getCreateMessage() {
        if (viewerFactionKey.isEmpty()) {
            return "Pledge to a faction before sending alliance requests.";
        }
        if (viewerFactionKey.equals(factionKey(receiverIndex))) {
            return "Choose a different faction.";
        }
        if (isEnemyAlliance(viewerFactionKey, factionKey(receiverIndex))) {
            return "Enemy factions cannot form alliances.";
        }
        return "The request starts Civil, Military, and Trade at tier 0 after acceptance.";
    }

    private void sendRequestCommand() {
        if (!canSendRequest()) {
            return;
        }
        KOMEMinecraftClient.sendChat("/alliance request " + viewerFactionKey + " " + factionKey(receiverIndex));
        createMode = false;
        requestAlliances();
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
            if (!viewerFactionKey.equals(factionKey(next)) && !isEnemyAlliance(viewerFactionKey, factionKey(next))) {
                return next;
            }
        }
        return next;
    }

    private int getVisibleRows() {
        return 5;
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
        }

        private int getTier(int type) {
            return type == 0 ? civilTier : type == 1 ? militaryTier : tradeTier;
        }

        private boolean hasPending() {
            return civilTier == -2 || militaryTier == -2 || tradeTier == -2;
        }

        private boolean needsQuotaRoll(int type) {
            return type == 1 && militaryTier == 0 && militaryFood.trim().isEmpty()
                || type == 2 && tradeTier == 0 && tradeFood.trim().isEmpty();
        }
    }
}
