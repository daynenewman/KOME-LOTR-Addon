package kome.client.gui;

import kome.client.KOMEMinecraftClient;
import kome.common.network.KOMEPacketAllianceAction;
import kome.common.network.KOMEPacketHandler;
import lotr.client.gui.LOTRGuiMenu;
import lotr.client.gui.LOTRGuiMenuBase;
import net.minecraft.client.gui.GuiButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Canonical Neutral / Friends / Allies diplomacy screen.
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

    private static final List<String[]> relations = new ArrayList<String[]>();
    private static final List<String[]> options = new ArrayList<String[]>();

    private static String viewerFaction = "";
    private static String viewerName = "";
    private static String summary = "Diplomacy: 0";

    private int selectedRelationIndex = -1;
    private int selectedOptionIndex = -1;

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
        summary = "Diplomacy: 0";
    }

    @Override
    public void initGui() {
        buttonList.clear();

        buttonList.add(new KOMEGuiButton(MENU, 8, 8, 76, 20, "Menu"));
        buttonList.add(new KOMEGuiButton(REFRESH, 90, 8, 76, 20, "Refresh"));

        int buttonWidth = Math.max(72, Math.min(112, (width - 50) / 4));
        int totalWidth = buttonWidth * 4 + 18;
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

        normalizeSelections();
        updateButtonStates();
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
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);

        if (mouseButton != 0) {
            return;
        }

        int top = 94;
        int rowHeight = 11;
        int gap = 12;
        int columnWidth = Math.max(120, (width - 52) / 2);
        int leftX = 20;
        int rightX = leftX + columnWidth + gap;

        if (mouseX >= leftX && mouseX < leftX + columnWidth) {
            int row = (mouseY - top) / rowHeight;

            if (mouseY >= top
                    && row >= 0
                    && row < relations.size()
                    && mouseY < top + relations.size() * rowHeight) {
                selectedRelationIndex = row;
                updateButtonStates();
            }
        }

        if (mouseX >= rightX && mouseX < rightX + columnWidth) {
            int row = (mouseY - top) / rowHeight;

            if (mouseY >= top
                    && row >= 0
                    && row < options.size()
                    && mouseY < top + options.size() * rowHeight) {
                selectedOptionIndex = row;
                updateButtonStates();
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        normalizeSelections();
        updateButtonStates();

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

        int top = 94;
        int rowHeight = 11;
        int gap = 12;
        int columnWidth = Math.max(120, (width - 52) / 2);
        int leftX = 20;
        int rightX = leftX + columnWidth + gap;

        drawString(fontRendererObj, "Current Relations", leftX, top - 12, 0xE0C060);
        drawString(fontRendererObj, "Request Diplomacy", rightX, top - 12, 0xE0C060);

        for (int i = 0; i < relations.size(); i++) {
            int y = top + i * rowHeight;
            String[] relation = relations.get(i);

            if (i == selectedRelationIndex) {
                drawRect(
                    leftX - 2,
                    y - 1,
                    leftX + columnWidth,
                    y + 9,
                    0x553F6A8A);
            }

            String text =
                displayFaction(relation[2])
                    + " / "
                    + displayFaction(relation[3])
                    + " - "
                    + displayRelation(relation[4]);

            if ("1".equals(relation[5])) {
                text +=
                    " | Pending "
                        + displayRelation(relation[6])
                        + " ("
                        + displayFaction(relation[7])
                        + " -> "
                        + displayFaction(relation[8])
                        + ")";
            }

            drawString(
                fontRendererObj,
                fontRendererObj.trimStringToWidth(text, columnWidth - 6),
                leftX,
                y,
                i == selectedRelationIndex ? 0xFFFFFF : 0xDDDDDD);
        }

        for (int i = 0; i < options.size(); i++) {
            int y = top + i * rowHeight;
            String[] option = options.get(i);

            if (i == selectedOptionIndex) {
                drawRect(
                    rightX - 2,
                    y - 1,
                    rightX + columnWidth,
                    y + 9,
                    0x553F6A8A);
            }

            String text =
                option[2]
                    + " - Current: "
                    + displayRelation(option[3]);

            if (option[7].length() > 0) {
                text += " | " + option[7];
            }

            drawString(
                fontRendererObj,
                fontRendererObj.trimStringToWidth(text, columnWidth - 6),
                rightX,
                y,
                i == selectedOptionIndex ? 0xFFFFFF : 0xCCCCCC);
        }

        String[] selectedRelation = selectedRelation();
        if (selectedRelation != null
                && !"neutral".equalsIgnoreCase(selectedRelation[4])
                && !"1".equals(selectedRelation[5])) {
            drawCenteredString(
                fontRendererObj,
                "Accepted relation downgrade policy is not configured.",
                width / 2,
                height - 18,
                0xAAAAAA);
        }

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

    private String[] selectedRelation() {
        return selectedRelationIndex >= 0 && selectedRelationIndex < relations.size()
            ? relations.get(selectedRelationIndex)
            : null;
    }

    private String[] selectedOption() {
        return selectedOptionIndex >= 0 && selectedOptionIndex < options.size()
            ? options.get(selectedOptionIndex)
            : null;
    }

    private void normalizeSelections() {
        if (relations.isEmpty()) {
            selectedRelationIndex = -1;
        } else if (selectedRelationIndex < 0 || selectedRelationIndex >= relations.size()) {
            selectedRelationIndex = 0;
        }

        if (options.isEmpty()) {
            selectedOptionIndex = -1;
        } else if (selectedOptionIndex < 0 || selectedOptionIndex >= options.size()) {
            selectedOptionIndex = 0;
        }
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
        if ("friends".equalsIgnoreCase(key)) {
            return "Friends";
        }

        if ("allies".equalsIgnoreCase(key)) {
            return "Allies";
        }

        return "Neutral";
    }

    private void send(String action, String first, String second, String relation) {
        KOMEPacketHandler.network.sendToServer(
            new KOMEPacketAllianceAction(action, relation, first, second));
    }

    private void requestData() {
        if (mc != null && mc.thePlayer != null) {
            KOMEMinecraftClient.sendChat("/alliance list");
        }
    }

    /**
     * Legacy visual-capture compatibility only.
     * Canonical diplomacy no longer has retired Stage/tab/break UI states.
     */
    public void setVisualTestState(int mode, int tab, String pair, boolean confirmBreak) {
    }
}