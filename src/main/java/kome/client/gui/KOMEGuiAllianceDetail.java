package kome.client.gui;

import kome.client.KOMEMinecraftClient;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.List;

public class KOMEGuiAllianceDetail extends GuiScreen {
    private static final String[] TYPES = KOMEAlliancePermissions.TYPES;
    private static final String[][] UNLOCKS = KOMEAlliancePermissions.UNLOCKS;
    private static final int ID_BACK = 1;
    private static final int ID_TAB_CIVIL = 10;
    private static final int ID_TAB_MILITARY = 11;
    private static final int ID_TAB_TRADE = 12;
    private static final int ID_ACCEPT = 20;
    private static final int ID_LEDGER = 21;
    private static final int ID_ROLL = 23;
    private static final int ID_BREAK = 24;
    private static final int ID_PERMS = 25;
    private static final int MARGIN = 18;
    private static final int GAP = 8;
    private static final int HEADER_CARD_HEIGHT = 58;
    private static final int TAB_HEIGHT = 24;
    private static final int CARD_HEIGHT = 54;
    private static final int QUOTA_CARD_HEIGHT = 58;
    private static final int BENEFITS_CARD_HEIGHT = 56;
    private static final int BUTTON_HEIGHT = 24;
    private static final int ACTION_BAR_HEIGHT = 46;

    private final KOMEGuiAlliance.Record record;
    private int selectedType;
    private boolean permissions;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int contentScroll;

    public KOMEGuiAllianceDetail(KOMEGuiAlliance.Record record) {
        this.record = record;
    }

    @Override
    public void initGui() {
        panelW = Math.min(640, width - 28);
        panelH = Math.min(430, height - 28);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        contentScroll = Math.max(0, Math.min(contentScroll, getMaxScroll()));
        buttonList.clear();
        buttonList.add(KOMEGuiButton.small(ID_BACK, panelX + MARGIN, panelY + 13, "Back"));
        addTabs();
        addRandomQuotaButton();
        layoutBottomActions();
    }

    private void addTabs() {
        int tabX = panelX + MARGIN;
        int tabY = getTabsY();
        int tabW = (panelW - MARGIN * 2 - GAP * 2) / 3;
        buttonList.add(tabButton(ID_TAB_CIVIL, tabX, tabY, tabW, "Civil", selectedType == 0));
        buttonList.add(tabButton(ID_TAB_MILITARY, tabX + tabW + GAP, tabY, tabW, "Military", selectedType == 1));
        buttonList.add(tabButton(ID_TAB_TRADE, tabX + (tabW + GAP) * 2, tabY, tabW, "Trade", selectedType == 2));
    }

    private KOMEGuiButton tabButton(int id, int x, int y, int width, String label, boolean selected) {
        return new KOMEGuiButton(id, x, y, width, TAB_HEIGHT, label, selected).setSelected(selected);
    }

    private void addRandomQuotaButton() {
        if (!shouldShowRandomQuotaCard()) {
            return;
        }
        int x = panelX + MARGIN;
        int y = getContentY() + getRandomQuotaCardY() - contentScroll;
        int w = panelW - MARGIN * 2;
        if (y + QUOTA_CARD_HEIGHT < getContentY() || y > getContentBottom()) {
            return;
        }
        GuiButton roll = new KOMEGuiButton(ID_ROLL, x + w - 90, y + 17, 74, BUTTON_HEIGHT, "Roll", true);
        roll.enabled = canRollQuota();
        buttonList.add(roll);
    }

    private void layoutBottomActions() {
        List actions = new ArrayList();
        if (canAcceptSelectedType()) {
            actions.add(new Action(ID_ACCEPT, "Accept", false));
        }
        if (record.hasAnyAlliance()) {
            actions.add(new Action(ID_PERMS, "Perms", false));
        }
        if (canOpenLedger()) {
            actions.add(new Action(ID_LEDGER, "Open Ledger", false));
        }
        if (canBreakSelectedType()) {
            actions.add(new Action(ID_BREAK, selectedTier() == -2 ? "Cancel" : "Break", true));
        }
        if (actions.isEmpty()) {
            return;
        }
        int count = actions.size();
        int x = panelX + MARGIN;
        int y = panelY + panelH - ACTION_BAR_HEIGHT + 13;
        int buttonW = (panelW - MARGIN * 2 - GAP * (count - 1)) / count;
        for (int i = 0; i < count; i++) {
            Action action = (Action) actions.get(i);
            buttonList.add(new KOMEGuiButton(action.id, x + i * (buttonW + GAP), y, buttonW, BUTTON_HEIGHT, action.label, action.danger));
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (!button.enabled) {
            return;
        }
        if (button.id == ID_BACK) {
            mc.displayGuiScreen(new KOMEGuiAlliance());
        } else if (button.id >= ID_TAB_CIVIL && button.id <= ID_TAB_TRADE) {
            selectedType = button.id - ID_TAB_CIVIL;
            contentScroll = 0;
            initGui();
        } else if (button.id == ID_ACCEPT) {
            KOMEMinecraftClient.sendChat("/alliance accept " + typeKey(selectedType) + " " + record.keyA + " " + record.keyB);
            mc.displayGuiScreen(new KOMEGuiAlliance());
        } else if (button.id == ID_LEDGER) {
            KOMEMinecraftClient.sendChat("/alliance goods " + record.keyA + " " + record.keyB);
            mc.displayGuiScreen(null);
        } else if (button.id == ID_ROLL) {
            KOMEMinecraftClient.sendChat("/alliance roll " + typeKey(selectedType) + " " + record.keyA + " " + record.keyB);
            mc.displayGuiScreen(new KOMEGuiAlliance());
        } else if (button.id == ID_BREAK) {
            KOMEMinecraftClient.sendChat("/alliance break " + typeKey(selectedType) + " " + record.keyA + " " + record.keyB);
            mc.displayGuiScreen(new KOMEGuiAlliance());
        } else if (button.id == ID_PERMS) {
            mc.displayGuiScreen(new KOMEGuiAlliancePermissions(record, selectedType));
        }
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
            contentScroll = Math.max(0, contentScroll - 18);
        } else if (wheel < 0) {
            contentScroll = Math.min(max, contentScroll + 18);
        }
        initGui();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        KOMEGuiTheme.drawMainPanel(panelX, panelY, panelW, panelH);
        KOMEGuiTheme.drawHeader(fontRendererObj, "Alliance Detail", panelX + 142, panelY + 12, panelW - 284);
        drawHeaderCard(mouseX, mouseY);
        drawTypeTabsBackdrop();
        drawContent(mouseX, mouseY);
        drawBottomActionBar();
        super.drawScreen(mouseX, mouseY, partialTicks);
        drawButtonTooltip(mouseX, mouseY);
    }

    private void drawHeaderCard(int mouseX, int mouseY) {
        int x = panelX + MARGIN;
        int y = panelY + 44;
        int w = panelW - MARGIN * 2;
        KOMEGuiTheme.drawCard(x, y, w, HEADER_CARD_HEIGHT, KOMEGuiTheme.isHovered(mouseX, mouseY, x, y, w, HEADER_CARD_HEIGHT));
        String relationship = KOMEGuiTheme.trimToWidth(fontRendererObj, record.factionA + " -> " + record.factionB, w - 24);
        KOMEGuiTheme.drawCenteredPlainText(fontRendererObj, relationship, x + w / 2, y + 9, KOMEGuiTheme.COLOR_BORDER_RED);
        fontRendererObj.drawString("Status: " + relationshipStatus(), x + 14, y + 29, relationshipStatusColor());
        fontRendererObj.drawString("Type: " + TYPES[selectedType] + " " + displayTier(selectedTier()), x + w / 2 - 42, y + 29, KOMEGuiTheme.COLOR_TEXT);
        String updated = "Updated: " + KOMEGuiTheme.trimToWidth(fontRendererObj, record.lastUpdatedBy, 128);
        fontRendererObj.drawString(updated, x + w - fontRendererObj.getStringWidth(updated) - 14, y + 29, KOMEGuiTheme.COLOR_TEXT_MUTED);
    }

    private void drawTypeTabsBackdrop() {
        KOMEGuiTheme.drawDivider(panelX + MARGIN, getTabsY() + TAB_HEIGHT + 4, panelW - MARGIN * 2);
    }

    private void drawContent(int mouseX, int mouseY) {
        int x = panelX + MARGIN;
        int y = getContentY();
        int w = panelW - MARGIN * 2;
        int h = getContentBottom() - getContentY();
        KOMEGuiTheme.enableScissor(mc, x, y, w, h);
        int cursor = y - contentScroll;
        if (permissions) {
            cursor = drawBenefitsCard(x, cursor, w, BENEFITS_CARD_HEIGHT + 42, mouseX, mouseY, true) + GAP;
        } else {
            cursor = drawCurrentTierCard(x, cursor, w, mouseX, mouseY) + GAP;
            cursor = drawNextObjectiveCard(x, cursor, w, mouseX, mouseY) + GAP;
            cursor = drawProgressCard(x, cursor, w, mouseX, mouseY) + GAP;
            if (shouldShowRandomQuotaCard()) {
                cursor = drawRandomQuotaCard(x, cursor, w, mouseX, mouseY) + GAP;
            }
            cursor = drawBenefitsCard(x, cursor, w, BENEFITS_CARD_HEIGHT, mouseX, mouseY, false) + GAP;
        }
        KOMEGuiTheme.disableScissor();
        drawScrollbar(cursor - y + contentScroll, h);
    }

    private int drawCurrentTierCard(int x, int y, int w, int mouseX, int mouseY) {
        KOMEGuiTheme.drawCard(x, y, w, CARD_HEIGHT, KOMEGuiTheme.isHovered(mouseX, mouseY, x, y + contentScroll, w, CARD_HEIGHT));
        drawCardTitle("Current Tier", x, y);
        fontRendererObj.drawString(tierName(), x + 16, y + 30, KOMEGuiTheme.COLOR_TEXT);
        KOMEGuiTheme.drawFactionBadge(fontRendererObj, tierBadge(), x + w - 52, y + 18, 34, selectedTier() >= 0 ? KOMEGuiTheme.COLOR_BORDER_RED : KOMEGuiTheme.COLOR_TEXT_MUTED);
        return y + CARD_HEIGHT;
    }

    private int drawNextObjectiveCard(int x, int y, int w, int mouseX, int mouseY) {
        KOMEGuiTheme.drawCard(x, y, w, CARD_HEIGHT, KOMEGuiTheme.isHovered(mouseX, mouseY, x, y + contentScroll, w, CARD_HEIGHT));
        drawCardTitle("Next Objective", x, y);
        KOMEGuiTheme.drawWrappedText(fontRendererObj, nextRequirement(), x + 16, y + 29, w - 190, KOMEGuiTheme.COLOR_TEXT);
        String reward = "Reward: " + currentUnlock(Math.max(0, selectedTier() + 1));
        fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, reward, 160), x + w - 176, y + 31, KOMEGuiTheme.COLOR_TEXT_MUTED);
        return y + CARD_HEIGHT;
    }

    private int drawProgressCard(int x, int y, int w, int mouseX, int mouseY) {
        KOMEGuiTheme.drawCard(x, y, w, CARD_HEIGHT, KOMEGuiTheme.isHovered(mouseX, mouseY, x, y + contentScroll, w, CARD_HEIGHT));
        drawCardTitle("Progress", x, y);
        Progress progress = getProgress();
        fontRendererObj.drawString(progress.label, x + 16, y + 31, KOMEGuiTheme.COLOR_TEXT);
        if (progress.required > 0) {
            int barW = Math.min(220, w / 2);
            KOMEGuiTheme.drawProgressBar(fontRendererObj, x + w - barW - 52, y + 29, barW, 10, progress.delivered / (float) progress.required, KOMEGuiTheme.COLOR_BORDER_RED, "");
            int percent = Math.min(100, Math.max(0, progress.delivered * 100 / progress.required));
            fontRendererObj.drawString(percent + "%", x + w - 42, y + 30, KOMEGuiTheme.COLOR_TEXT);
        }
        return y + CARD_HEIGHT;
    }

    private int drawRandomQuotaCard(int x, int y, int w, int mouseX, int mouseY) {
        KOMEGuiTheme.drawCard(x, y, w, QUOTA_CARD_HEIGHT, KOMEGuiTheme.isHovered(mouseX, mouseY, x, y + contentScroll, w, QUOTA_CARD_HEIGHT));
        drawCardTitle("Random Quota", x, y);
        String quota = getQuotaText();
        KOMEGuiTheme.drawWrappedText(fontRendererObj, quota, x + 16, y + 29, w - 128, KOMEGuiTheme.COLOR_TEXT);
        if (!canRollQuota()) {
            fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, rollUnavailableReason(), 116), x + w - 128, y + 43, KOMEGuiTheme.COLOR_TEXT_DISABLED);
        }
        return y + QUOTA_CARD_HEIGHT;
    }

    private int drawBenefitsCard(int x, int y, int w, int h, int mouseX, int mouseY, boolean allPermissions) {
        KOMEGuiTheme.drawCard(x, y, w, h, KOMEGuiTheme.isHovered(mouseX, mouseY, x, y + contentScroll, w, h));
        drawCardTitle(allPermissions ? "Permission Summary" : "Unlocked Benefits", x, y);
        if (allPermissions) {
            int cursor = y + 30;
            for (int i = 0; i < TYPES.length; i++) {
                int tier = record.getTier(i);
                String line = TYPES[i] + " " + displayTier(tier) + ": " + currentUnlock(i, tier);
                fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, line, w - 32), x + 16, cursor, tier >= 0 ? KOMEGuiTheme.COLOR_GOOD : KOMEGuiTheme.COLOR_TEXT_MUTED);
                cursor += 14;
            }
        } else {
            int textWidth = canOpenLedger() ? w - 156 : w - 32;
            KOMEGuiTheme.drawWrappedText(fontRendererObj, benefitSummary(), x + 16, y + 29, textWidth, KOMEGuiTheme.COLOR_TEXT);
        }
        return y + h;
    }

    private void drawBottomActionBar() {
        int y = panelY + panelH - ACTION_BAR_HEIGHT;
        KOMEGuiTheme.drawDivider(panelX + MARGIN, y, panelW - MARGIN * 2);
    }

    private void drawCardTitle(String title, int x, int y) {
        fontRendererObj.drawString(title, x + 16, y + 10, KOMEGuiTheme.COLOR_BORDER_RED);
    }

    private void drawScrollbar(int contentHeight, int viewportHeight) {
        int max = getMaxScroll();
        if (max <= 0) {
            return;
        }
        int x = panelX + panelW - MARGIN - 6;
        int y = getContentY();
        KOMEGuiTheme.drawBorderedRect(x, y, 5, viewportHeight, KOMEGuiTheme.COLOR_GOLD_DARK, 0x552B2117);
        int handleH = Math.max(18, viewportHeight * viewportHeight / Math.max(viewportHeight, contentHeight));
        int handleY = y + (viewportHeight - handleH) * contentScroll / max;
        KOMEGuiTheme.drawBorderedRect(x, handleY, 5, handleH, KOMEGuiTheme.COLOR_BORDER_RED, KOMEGuiTheme.COLOR_GOLD);
    }

    private void drawButtonTooltip(int mouseX, int mouseY) {
        for (Object object : buttonList) {
            GuiButton button = (GuiButton) object;
            if (button.visible && !button.enabled && KOMEGuiTheme.isHovered(mouseX, mouseY, button.xPosition, button.yPosition, button.width, button.height)) {
                List lines = new ArrayList();
                lines.add(disabledReason(button.id));
                KOMEGuiTheme.drawTooltip(fontRendererObj, lines, mouseX, mouseY, width, height);
                return;
            }
        }
    }

    private String disabledReason(int id) {
        if (id == ID_ROLL) {
            return rollUnavailableReason();
        }
        return "This action is not available.";
    }

    private boolean canAcceptSelectedType() {
        return selectedTier() == -2 && KOMEGuiAlliance.viewerIsKing()
            && KOMEGuiAlliance.viewerFactionKey().equals(record.keyB)
            && !KOMEGuiAlliance.viewerFactionKey().equals(record.keyA);
    }

    private boolean canOpenLedger() {
        return record.hasAnyAlliance();
    }

    private boolean canBreakSelectedType() {
        return selectedTier() != -1;
    }

    private boolean shouldShowRandomQuotaCard() {
        return selectedType == 1 || selectedType == 2;
    }

    private boolean canRollQuota() {
        return selectedTier() == 0 && record.needsQuotaRoll(selectedType);
    }

    private String rollUnavailableReason() {
        if (selectedTier() < 0) {
            return "Quota rolls require an active alliance.";
        }
        if (selectedTier() != 0) {
            return "This tier does not need a quota roll.";
        }
        return "Quota already assigned.";
    }

    private String getQuotaText() {
        String quota = selectedType == 1 ? record.militaryFood : record.tradeFood;
        if (quota == null || quota.trim().isEmpty()) {
            return "Roll for this alliance's food quota.";
        }
        return quota;
    }

    private String relationshipStatus() {
        int tier = selectedTier();
        if (tier == -2) {
            return "Pending " + TYPES[selectedType] + " Request";
        }
        if (tier >= 0) {
            return "Active Alliance";
        }
        return "No " + TYPES[selectedType] + " Alliance";
    }

    private int relationshipStatusColor() {
        int tier = selectedTier();
        return tier >= 0 ? KOMEGuiTheme.COLOR_GOOD : tier == -2 ? KOMEGuiTheme.COLOR_WARN : KOMEGuiTheme.COLOR_TEXT_MUTED;
    }

    private String tierName() {
        int tier = selectedTier();
        if (tier == -2) {
            return "Pending";
        }
        if (tier < 0) {
            return "None";
        }
        return "Tier " + roman(tier) + " - " + currentUnlock(tier);
    }

    private String tierBadge() {
        int tier = selectedTier();
        return tier < 0 ? "-" : roman(tier);
    }

    private String nextRequirement() {
        int tier = selectedTier();
        if (tier == -2) {
            return "Receiving faction must accept the request.";
        }
        if (tier < 0) {
            return "No further requirement.";
        }
        if (selectedType == 0) {
            if (tier == 0) {
                return "Deposit 1000 coins to unlock Tier I.";
            }
            if (tier == 1) {
                return "Trade 500 coins worth of goods to unlock Tier II.";
            }
            return "No further requirement.";
        }
        if (selectedType == 1) {
            if (tier == 0) return record.militaryFood.length() == 0 ? "Roll a military food quota." : "Deliver " + record.militaryFood + ".";
            if (tier == 1) return "Kill 2000 enemies of " + record.factionB + ".";
            if (tier == 2) return "Complete the population build and waypoint battle.";
            if (tier == 3) return "Deliver 50 population and 30000 coins.";
            return "No further requirement.";
        }
        if (tier == 0) return record.tradeFood.length() == 0 ? "Roll a trade food quota." : "Deliver " + record.tradeFood + " and 5000 coins.";
        if (tier == 1) return "Deliver 50 farmer population and 10000 coins.";
        return "No further requirement.";
    }

    private Progress getProgress() {
        if (selectedType == 0) {
            if (record.civilTier == 0) {
                return new Progress(0, 0, "Coin deposit progress is shown in the ledger.");
            }
            return new Progress(Math.min(record.civilTradeDelivered, 500), 500, Math.min(record.civilTradeDelivered, 500) + " / 500 trade coins");
        }
        if (selectedType == 1) {
            if (record.militaryTier == 0) {
                int required = quotaRequiredUnits(record.militaryFood);
                return new Progress(Math.min(record.militaryFoodDelivered, required), required, required > 0 ? Math.min(record.militaryFoodDelivered, required) + " / " + required + " food" : "Quota not rolled");
            }
            if (record.militaryTier == 1) {
                return new Progress(Math.min(record.militaryKillsDelivered, 2000), 2000, Math.min(record.militaryKillsDelivered, 2000) + " / 2000 kills");
            }
            return new Progress(Math.min(record.militaryT4CoinsDelivered, 30000), 30000, Math.min(record.militaryT4Pop, 50) + " / 50 pop, " + Math.min(record.militaryT4CoinsDelivered, 30000) + " / 30000 coins");
        }
        if (record.tradeTier == 0) {
            return new Progress(Math.min(record.tradeFoodDelivered, quotaRequiredUnits(record.tradeFood)), quotaRequiredUnits(record.tradeFood), record.tradeFood.length() > 0 ? record.tradeFoodDelivered + " food delivered" : "Quota not rolled");
        }
        return new Progress(Math.min(record.tradeT2CoinsDelivered, 10000), 10000, Math.min(record.tradeFarmerPop, 50) + " / 50 farmer pop, " + Math.min(record.tradeT2CoinsDelivered, 10000) + " / 10000 coins");
    }

    private String benefitSummary() {
        int tier = selectedTier();
        if (tier < 0) {
            return tier == -2 ? "No benefits unlocked until the request is accepted." : "No benefits unlocked for this alliance type.";
        }
        String[] unlocks = UNLOCKS[selectedType];
        StringBuilder builder = new StringBuilder();
        int max = Math.min(tier, unlocks.length - 1);
        for (int i = 0; i <= max; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(unlocks[i]);
        }
        return builder.toString();
    }

    private String currentUnlock(int tier) {
        return currentUnlock(selectedType, tier);
    }

    private String currentUnlock(int type, int tier) {
        if (tier < 0) {
            return tier == -2 ? "Pending request" : "No active unlock";
        }
        String[] unlocks = UNLOCKS[type];
        int index = Math.min(tier, unlocks.length - 1);
        return unlocks[index];
    }

    private int selectedTier() {
        return record.getTier(selectedType);
    }

    private int getTabsY() {
        return panelY + 112;
    }

    private int getContentY() {
        return getTabsY() + TAB_HEIGHT + 14;
    }

    private int getContentBottom() {
        return panelY + panelH - ACTION_BAR_HEIGHT - 8;
    }

    private int getContentHeight() {
        int height = permissions ? BENEFITS_CARD_HEIGHT + 42 : CARD_HEIGHT * 3 + BENEFITS_CARD_HEIGHT + GAP * 3;
        if (!permissions && shouldShowRandomQuotaCard()) {
            height += QUOTA_CARD_HEIGHT + GAP;
        }
        return height;
    }

    private int getMaxScroll() {
        return Math.max(0, getContentHeight() - Math.max(1, getContentBottom() - getContentY()));
    }

    private int getRandomQuotaCardY() {
        return CARD_HEIGHT * 3 + GAP * 3;
    }

    private int getBenefitsCardY() {
        int y = CARD_HEIGHT * 3 + GAP * 3;
        if (shouldShowRandomQuotaCard()) {
            y += QUOTA_CARD_HEIGHT + GAP;
        }
        return y;
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

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String typeKey(int type) {
        return type == 0 ? "civil" : type == 1 ? "military" : "trade";
    }

    private static String displayTier(int tier) {
        return tier == -2 ? "Pending" : tier < 0 ? "None" : "T" + tier;
    }

    private static String roman(int tier) {
        if (tier <= 0) return "0";
        if (tier == 1) return "I";
        if (tier == 2) return "II";
        if (tier == 3) return "III";
        if (tier == 4) return "IV";
        return String.valueOf(tier);
    }

    private static class Action {
        private final int id;
        private final String label;
        private final boolean danger;

        private Action(int id, String label, boolean danger) {
            this.id = id;
            this.label = label;
            this.danger = danger;
        }
    }

    private static class Progress {
        private final int delivered;
        private final int required;
        private final String label;

        private Progress(int delivered, int required, String label) {
            this.delivered = Math.max(0, delivered);
            this.required = Math.max(0, required);
            this.label = label;
        }
    }
}
