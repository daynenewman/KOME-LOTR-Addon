package kome.client.gui;

import kome.common.network.KOMEPacketAllianceAction;
import kome.common.network.KOMEPacketAllianceRequest;
import kome.common.network.KOMEPacketHandler;
import lotr.client.gui.LOTRGuiMenu;
import lotr.client.gui.LOTRGuiMenuBase;
import net.minecraft.client.gui.GuiButton;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.List;

/**
 * Five-rung diplomacy screen backed by the current LOTR faction relation table.
 *
 * Server records remain authoritative. This screen only exposes actions that
 * the latest server snapshot marks as available; every action is revalidated
 * by the server command path.
 */
public class KOMEGuiAllianceUnified extends LOTRGuiMenuBase {
    private static final int MENU = 1;
    private static final int REFRESH = 3;
    private static final int REQUEST_FRIENDS = 7;
    private static final int REQUEST_ALLIES = 8;
    private static final int ACCEPT = 20;
    private static final int CANCEL = 21;
    private static final int WORSEN = 22;
    private static final int LIST_TOP = 94;
    private static final int ROW_HEIGHT = 11;
    private static final int LIST_FOOTER_HEIGHT = 34;

    private static final List<String[]> relations = new ArrayList<String[]>();
    private static final List<String[]> options = new ArrayList<String[]>();

    private static String viewerFaction = "";
    private static String viewerName = "";
    private static boolean viewerIsKing;
    private static String summary = "Diplomacy: 0";

    private final KOMEAllianceListNavigation relationNavigation =
        new KOMEAllianceListNavigation();
    private final KOMEAllianceListNavigation optionNavigation =
        new KOMEAllianceListNavigation();

    public static void update(List lines) {
        relations.clear();
        options.clear();

        if (lines == null) {
            return;
        }

        for (Object value : lines) {
            String[] parts = String.valueOf(value).split("\t", -1);

            if (parts.length > 0 && "VIEWER".equals(parts[0])) {
                viewerFaction = parts.length > 1 ? parts[1] : "";
                viewerName = parts.length > 2 ? parts[2] : "";
                viewerIsKing = parts.length > 3 && "1".equals(parts[3]);
            } else if (parts.length > 1 && "SUMMARY".equals(parts[0])) {
                summary = "Diplomacy: " + parts[1];
            } else if (parts.length >= 13 && "DIPLOMACY_RELATION".equals(parts[0])) {
                relations.add(parts);
            } else if (parts.length >= 8 && "DIPLOMACY_REQUEST_OPTION".equals(parts[0])) {
                options.add(parts);
            }
        }
    }

    public static void resetData() {
        relations.clear();
        options.clear();
        viewerFaction = "";
        viewerName = "";
        viewerIsKing = false;
        summary = "Diplomacy: 0";
    }

    @Override
    public void initGui() {
        buttonList.clear();

        buttonList.add(new KOMEGuiButton(MENU, 8, 8, 76, 20, "Menu"));
        buttonList.add(new KOMEGuiButton(REFRESH, 90, 8, 76, 20, "Refresh"));

        int buttonWidth = Math.max(64, Math.min(104, (width - 56) / 5));
        int totalWidth = buttonWidth * 5 + 24;
        int startX = Math.max(8, (width - totalWidth) / 2);
        int y = 34;

        buttonList.add(new KOMEGuiButton(
            REQUEST_FRIENDS, startX, y, buttonWidth, 20, "Request Friends"));
        buttonList.add(new KOMEGuiButton(
            REQUEST_ALLIES, startX + buttonWidth + 6, y, buttonWidth, 20, "Request Allies"));
        buttonList.add(new KOMEGuiButton(
            ACCEPT, startX + (buttonWidth + 6) * 2, y, buttonWidth, 20, "Accept"));
        buttonList.add(new KOMEGuiButton(
            CANCEL, startX + (buttonWidth + 6) * 3, y, buttonWidth, 20, "Cancel Request"));
        buttonList.add(new KOMEGuiButton(
            WORSEN, startX + (buttonWidth + 6) * 4, y, buttonWidth, 20, "Worsen 1 Step"));

        normalizeSelections();
        updateButtonStates();
        requestData();
    }

    @Override
    public void actionPerformed(GuiButton button) {
        if (button.id == MENU) {
            mc.displayGuiScreen(new LOTRGuiMenu());
            return;
        }

        if (button.id == REFRESH) {
            requestData();
            return;
        }

        String[] option = selectedOption();
        String[] relation = selectedRelation();

        if (button.id == REQUEST_FRIENDS && canRequestFriends(option)) {
            send("request", viewerFaction, option[1], "friends");
        } else if (button.id == REQUEST_ALLIES && canRequestAllies(option)) {
            send("request", viewerFaction, option[1], "allies");
        } else if (button.id == ACCEPT && canAccept(relation)) {
            send("accept", relation[7], relation[8], "");
        } else if (button.id == CANCEL && canCancel(relation)) {
            send("cancel", relation[7], relation[8], "");
        } else if (button.id == WORSEN && canWorsen(relation)) {
            send("break", viewerFaction, otherFaction(relation), "");
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);

        if (mouseButton != 0) {
            return;
        }

        int gap = 12;
        int columnWidth = Math.max(120, (width - 52) / 2);
        int leftX = 20;
        int rightX = leftX + columnWidth + gap;
        int visibleRows = visibleRows();
        int listHeight = visibleRows * ROW_HEIGHT;

        if (inside(mouseX, mouseY, leftX, LIST_TOP, columnWidth, listHeight)) {
            int row = (mouseY - LIST_TOP) / ROW_HEIGHT;
            if (relationNavigation.selectVisibleRow(row, relations.size(), visibleRows)) {
                updateButtonStates();
            }
        }

        if (inside(mouseX, mouseY, rightX, LIST_TOP, columnWidth, listHeight)) {
            int row = (mouseY - LIST_TOP) / ROW_HEIGHT;
            if (optionNavigation.selectVisibleRow(row, options.size(), visibleRows)) {
                updateButtonStates();
            }
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0 || mc == null || mc.displayWidth <= 0 || mc.displayHeight <= 0) {
            return;
        }
        int mouseX = Mouse.getEventX() * width / mc.displayWidth;
        int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        int gap = 12;
        int columnWidth = Math.max(120, (width - 52) / 2);
        int leftX = 20;
        int rightX = leftX + columnWidth + gap;
        int visibleRows = visibleRows();
        int listHeight = visibleRows * ROW_HEIGHT;

        if (inside(mouseX, mouseY, leftX, LIST_TOP, columnWidth, listHeight)) {
            relationNavigation.wheel(wheel, relations.size(), visibleRows);
        } else if (inside(mouseX, mouseY, rightX, LIST_TOP, columnWidth, listHeight)) {
            optionNavigation.wheel(wheel, options.size(), visibleRows);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        normalizeSelections();
        updateButtonStates();
        drawDefaultBackground();

        drawCenteredString(fontRendererObj, summary, width / 2, 62, 0xFFFFFF);

        String viewer = viewerName.length() > 0 ? viewerName : viewerFaction;
        if (viewer.length() > 0) {
            drawCenteredString(
                fontRendererObj,
                "Your faction: " + viewer,
                width / 2,
                74,
                0xCCCCCC);
        }

        int gap = 12;
        int columnWidth = Math.max(120, (width - 52) / 2);
        int leftX = 20;
        int rightX = leftX + columnWidth + gap;
        int visibleRows = visibleRows();
        int listHeight = visibleRows * ROW_HEIGHT;

        drawString(fontRendererObj,
            listHeader("Current Relations", relationNavigation, relations.size(), visibleRows),
            leftX, LIST_TOP - 12, 0xE0C060);
        drawString(fontRendererObj,
            listHeader("Request Diplomacy", optionNavigation, options.size(), visibleRows),
            rightX, LIST_TOP - 12, 0xE0C060);

        for (int row = 0; row < visibleRows
                && relationNavigation.getScroll() + row < relations.size(); row++) {
            int i = relationNavigation.getScroll() + row;
            int y = LIST_TOP + row * ROW_HEIGHT;
            String[] relation = relations.get(i);

            if (i == relationNavigation.getSelected()) {
                drawRect(
                    leftX - 2,
                    y - 1,
                    leftX + columnWidth - 5,
                    y + 9,
                    0x553F6A8A);
            }

            String text = relationText(relation);

            drawString(
                fontRendererObj,
                fontRendererObj.trimStringToWidth(text, columnWidth - 12),
                leftX,
                y,
                i == relationNavigation.getSelected() ? 0xFFFFFF : 0xDDDDDD);
        }

        for (int row = 0; row < visibleRows
                && optionNavigation.getScroll() + row < options.size(); row++) {
            int i = optionNavigation.getScroll() + row;
            int y = LIST_TOP + row * ROW_HEIGHT;
            String[] option = options.get(i);

            if (i == optionNavigation.getSelected()) {
                drawRect(
                    rightX - 2,
                    y - 1,
                    rightX + columnWidth - 5,
                    y + 9,
                    0x553F6A8A);
            }

            String text = optionText(option);

            drawString(
                fontRendererObj,
                fontRendererObj.trimStringToWidth(text, columnWidth - 12),
                rightX,
                y,
                i == optionNavigation.getSelected() ? 0xFFFFFF : 0xCCCCCC);
        }

        drawScrollbar(leftX + columnWidth - 5, LIST_TOP, listHeight,
            relations.size(), visibleRows, relationNavigation.getScroll());
        drawScrollbar(rightX + columnWidth - 5, LIST_TOP, listHeight,
            options.size(), visibleRows, optionNavigation.getScroll());
        drawSelectionFooter(leftX, rightX, columnWidth, LIST_TOP + listHeight + 3);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void updateButtonStates() {
        String[] option = selectedOption();
        String[] relation = selectedRelation();

        for (Object value : buttonList) {
            if (!(value instanceof GuiButton)) {
                continue;
            }

            GuiButton button = (GuiButton) value;

            if (button.id == REQUEST_FRIENDS) {
                button.enabled = canRequestFriends(option);
            } else if (button.id == REQUEST_ALLIES) {
                button.enabled = canRequestAllies(option);
            } else if (button.id == ACCEPT) {
                button.enabled = canAccept(relation);
            } else if (button.id == CANCEL) {
                button.enabled = canCancel(relation);
            } else if (button.id == WORSEN) {
                button.enabled = canWorsen(relation);
            }
        }
    }

    private boolean canRequestFriends(String[] option) {
        return option != null
            && option.length >= 8
            && viewerFaction.length() > 0
            && "1".equals(option[5]);
    }

    private boolean canRequestAllies(String[] option) {
        return option != null
            && option.length >= 8
            && viewerFaction.length() > 0
            && "1".equals(option[6]);
    }

    private boolean canAccept(String[] relation) {
        return relation != null
            && relation.length >= 13
            && "1".equals(relation[5])
            && "1".equals(relation[9])
            && relation[7].length() > 0
            && relation[8].length() > 0;
    }

    private boolean canCancel(String[] relation) {
        return relation != null
            && relation.length >= 13
            && "1".equals(relation[5])
            && "1".equals(relation[10])
            && relation[7].length() > 0
            && relation[8].length() > 0;
    }

    private boolean canWorsen(String[] relation) {
        return relation != null
            && relation.length >= 13
            && viewerIsKing
            && (viewerFaction.equals(relation[2]) || viewerFaction.equals(relation[3]))
            && !"mortal_enemy".equalsIgnoreCase(relation[4]);
    }

    private String otherFaction(String[] relation) {
        if (relation == null || relation.length < 4) {
            return "";
        }
        return viewerFaction.equals(relation[2]) ? relation[3] : relation[2];
    }

    private String[] selectedRelation() {
        int selected = relationNavigation.getSelected();
        return selected >= 0 && selected < relations.size()
            ? relations.get(selected)
            : null;
    }

    private String[] selectedOption() {
        int selected = optionNavigation.getSelected();
        return selected >= 0 && selected < options.size()
            ? options.get(selected)
            : null;
    }

    private void normalizeSelections() {
        int visibleRows = visibleRows();
        relationNavigation.normalize(relations.size(), visibleRows);
        optionNavigation.normalize(options.size(), visibleRows);
    }

    private int visibleRows() {
        return KOMEAllianceListNavigation.visibleRows(
            height, LIST_TOP, LIST_FOOTER_HEIGHT, ROW_HEIGHT);
    }

    private boolean inside(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private String listHeader(String label, KOMEAllianceListNavigation navigation,
            int size, int visibleRows) {
        if (size <= 0) {
            return label + " (0)";
        }
        int first = navigation.getScroll() + 1;
        int last = Math.min(size, navigation.getScroll() + visibleRows);
        return label + " (" + first + "-" + last + "/" + size + ")";
    }

    private String relationText(String[] relation) {
        if (relation == null || relation.length < 13) {
            return "";
        }
        String text = displayFaction(relation[2]) + " / " + displayFaction(relation[3])
            + " - " + displayRelation(relation[4]);
        if ("1".equals(relation[5])) {
            text += " | Pending " + displayRelation(relation[6]) + " ("
                + displayFaction(relation[7]) + " -> " + displayFaction(relation[8]) + ")";
        }
        return text;
    }

    private String optionText(String[] option) {
        if (option == null || option.length < 8) {
            return "";
        }
        String text = option[2] + " - Current: " + displayRelation(option[3]);
        return option[7].length() > 0 ? text + " | " + option[7] : text;
    }

    private void drawSelectionFooter(int leftX, int rightX, int columnWidth, int y) {
        String[] relation = selectedRelation();
        String[] option = selectedOption();
        String relationLine = relation == null ? "Selected: none" : "Selected: " + relationText(relation);
        String optionLine = option == null ? "Selected: none" : "Selected: " + optionText(option);
        drawString(fontRendererObj,
            fontRendererObj.trimStringToWidth(relationLine, columnWidth - 6),
            leftX, y, 0xBBBBBB);
        drawString(fontRendererObj,
            fontRendererObj.trimStringToWidth(optionLine, columnWidth - 6),
            rightX, y, 0xBBBBBB);
        drawString(fontRendererObj,
            fontRendererObj.trimStringToWidth(
                "Mouse wheel over either list to scroll.", columnWidth - 6),
            leftX, y + 11, 0x888888);
    }

    private void drawScrollbar(int x, int y, int height, int size, int visibleRows, int scroll) {
        int max = Math.max(0, size - visibleRows);
        if (max <= 0 || height <= 0) {
            return;
        }
        drawRect(x, y, x + 3, y + height, 0x88202020);
        int handleHeight = Math.max(10, height * visibleRows / Math.max(visibleRows, size));
        int handleY = y + (height - handleHeight) * scroll / max;
        drawRect(x, handleY, x + 3, handleY + handleHeight, 0xFFE0C060);
    }

    private String displayFaction(String key) {
        if (key == null || key.length() == 0) {
            return "";
        }

        if (key.equals(viewerFaction) && viewerName.length() > 0) {
            return viewerName;
        }

        for (String[] option : options) {
            if (option.length > 2 && key.equals(option[1])) {
                return option[2];
            }
        }

        return key;
    }

    private String displayRelation(String key) {
        if ("mortal_enemy".equalsIgnoreCase(key)) {
            return "Mortal Enemy";
        }

        if ("enemy".equalsIgnoreCase(key)) {
            return "Enemy";
        }

        if ("friends".equalsIgnoreCase(key)) {
            return "Friend";
        }

        if ("allies".equalsIgnoreCase(key)) {
            return "Ally";
        }

        return "Neutral";
    }

    private void send(String action, String first, String second, String relation) {
        KOMEPacketHandler.network.sendToServer(
            new KOMEPacketAllianceAction(action, relation, first, second));
    }

    private void requestData() {
        if (mc != null && mc.thePlayer != null) {
            KOMEPacketHandler.network.sendToServer(
                new KOMEPacketAllianceRequest(false));
        }
    }

    /**
     * Legacy visual-capture compatibility only.
     * Canonical diplomacy no longer has retired Stage/tab/break UI states.
     */
    public void setVisualTestState(int mode, int tab, String pair, boolean confirmBreak) {
    }
}
