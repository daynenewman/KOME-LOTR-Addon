package kome.client.gui;

import kome.client.KOMEMinecraftClient;
import kome.client.KOMEQuotaLedgerOverlay;
import kome.common.data.KOMEClientData;
import kome.common.data.KOMEAllianceBenefits;
import kome.common.network.KOMEPacketAllianceAction;
import kome.common.network.KOMEPacketHandler;
import kome.common.network.KOMEPacketMovementHistoryRequest;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.List;

public class KOMEGuiAllianceDetail extends GuiScreen {
    private static final String[] TYPES = KOMEAlliancePermissions.TYPES;
    private static final String[][] UNLOCKS = KOMEAlliancePermissions.UNLOCKS;
    private static final int ID_BACK = 1;
    private static final int ID_TAB_OVERVIEW = 10;
    private static final int ID_TAB_REQUIREMENTS = 11;
    private static final int ID_TAB_BENEFITS = 12;
    private static final int ID_TAB_MILITARY = 13;
    private static final int ID_ACCEPT = 20;
    private static final int ID_LEDGER = 21;
    private static final int ID_ROLL = 23;
    private static final int ID_BREAK = 24;
    private static final int ID_PERMS = 25;
    private static final int ID_CONTEXT = 26;
    private static final int ID_HISTORY = 28;
    private static final int ID_CLAIM = 29;
    private static final int ID_REQUIREMENT_ROLL = 30;
    private static final int ID_TRACK_CIVIL = 40;
    private static final int MARGIN = 18;
    private static final int GAP = 8;
    private static final int HEADER_CARD_HEIGHT = 58;
    private static final int TAB_HEIGHT = 24;
    private static final int CARD_HEIGHT = 54;
    private static final int QUOTA_CARD_HEIGHT = 58;
    private static final int BENEFITS_CARD_HEIGHT = 56;
    private static final int BUTTON_HEIGHT = 24;
    private static final int ACTION_BAR_HEIGHT = 46;

    private KOMEGuiAlliance.Record record;
    private int selectedType;
    private int selectedView;
    private boolean permissions;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int contentScroll;
    private final KOMEGuiScrollPanel contentPanel = new KOMEGuiScrollPanel();
    private final KOMEGuiConfirmationDialog confirmation = new KOMEGuiConfirmationDialog();

    public KOMEGuiAllianceDetail(KOMEGuiAlliance.Record record) {
        this.record = record;
    }

    void refreshRecord() {
        KOMEGuiAlliance.Record updated = KOMEGuiAlliance.recordFor(record.keyA, record.keyB);
        if (updated != null) record = updated;
        initGui();
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
        addTrackSelectors();
        addRequirementRollButtons();
        addRandomQuotaButton();
        layoutBottomActions();
    }

    private void addTabs() {
        int tabX = panelX + MARGIN;
        int tabY = getTabsY();
        int gap = 4;
        int tabW = (panelW - MARGIN * 2 - gap * 3) / 4;
        String[] labels = {"Overview", "Requirements", "Benefits", "Military"};
        for (int i = 0; i < labels.length; i++) {
            buttonList.add(tabButton(ID_TAB_OVERVIEW + i, tabX + i * (tabW + gap), tabY, tabW, labels[i], selectedView == i));
        }
    }

    private KOMEGuiButton tabButton(int id, int x, int y, int width, String label, boolean selected) {
        return KOMEGuiButton.tab(id, x, y, width, label, selected);
    }

    private void addTrackSelectors() {
        if (selectedView != 0) return;
        int x = panelX + MARGIN;
        int y = getContentY();
        int gap = 5;
        int w = (panelW - MARGIN * 2 - gap * 2) / 3;
        for (int type = 0; type < TYPES.length; type++) {
            buttonList.add(tabButton(ID_TRACK_CIVIL + type, x + type * (w + gap), y, w,
                TYPES[type] + " " + displayTier(record.getTier(type)), selectedType == type));
        }
    }

    private void addRequirementRollButtons() {
        if (selectedView != 1) return;
        int x = panelX + panelW - MARGIN - 144;
        for (int type = 0; type < TYPES.length; type++) {
            KOMEGuiAlliance.TrackRecord track = record.track(type, false);
            if (track == null || !track.canManage || !record.needsQuotaRoll(type)) continue;
            int y = getScrollContentY() + type * (120 + GAP) + 88 - contentScroll;
            if (isFullyInsideScrollViewport(y, BUTTON_HEIGHT)) {
                buttonList.add(new KOMEGuiButton(ID_REQUIREMENT_ROLL + type, x, y, 128, BUTTON_HEIGHT,
                    "Roll Requirement", true));
            }
        }
    }

    private void addRandomQuotaButton() {
        if (selectedView == 1 || selectedView == 2 || !shouldShowRandomQuotaCard()) {
            return;
        }
        int x = panelX + MARGIN;
        int y = getScrollContentY() + getRandomQuotaCardY() - contentScroll;
        int w = panelW - MARGIN * 2;
        int buttonY = y + 17;
        if (!isFullyInsideScrollViewport(buttonY, BUTTON_HEIGHT)) {
            return;
        }
        GuiButton roll = new KOMEGuiButton(ID_ROLL, x + w - 144, buttonY, 128, BUTTON_HEIGHT, "Roll Requirement", true);
        roll.enabled = canRollQuota();
        buttonList.add(roll);
    }

    private void layoutBottomActions() {
        List actions = new ArrayList();
        if (selectedView == 1) {
            actions.add(new Action(ID_LEDGER, "Open Our Contribution Ledger", false, canOpenLedger()));
            actions.add(new Action(ID_CLAIM, "Claim Incoming Goods", false, canOpenLedger()));
            addActionButtons(actions);
            return;
        }
        if (selectedView == 2) {
            actions.add(new Action(ID_PERMS, "Permissions", false, record.hasAnyAlliance()));
            actions.add(new Action(ID_LEDGER, "Open Our Contribution Ledger", false, canOpenLedger()));
            addActionButtons(actions);
            return;
        }
        actions.add(new Action(ID_ACCEPT, "Accept", false, canAcceptSelectedType()));
        actions.add(new Action(ID_PERMS, "Perms", false, record.hasAnyAlliance()));
        actions.add(new Action(ID_LEDGER, "Open Ledger", false, canOpenLedger()));
        if (selectedType == 1) {
            actions.add(new Action(ID_CONTEXT, "Military Command", false, selectedTier() >= 1));
        }
        if (selectedType == 1) {
            actions.add(new Action(ID_HISTORY, "History", false, selectedTier() >= 0));
        }
        actions.add(new Action(ID_BREAK, selectedTier() == -2 ? "Cancel Request" : "Break Alliance", true, canBreakSelectedType()));
        addActionButtons(actions);
    }

    private void addActionButtons(List actions) {
        int count = actions.size();
        int x = panelX + MARGIN;
        int y = panelY + panelH - getActionBarHeight() + (isCompactLayout() ? 9 : 13);
        int buttonW = (panelW - MARGIN * 2 - GAP * (count - 1)) / count;
        for (int i = 0; i < count; i++) {
            Action action = (Action) actions.get(i);
            KOMEGuiButton button = new KOMEGuiButton(action.id, x + i * (buttonW + GAP), y, buttonW, BUTTON_HEIGHT, action.label, action.danger);
            if (action.danger) button.setStyle(KOMEGuiButton.Style.DESTRUCTIVE);
            button.enabled = action.enabled;
            buttonList.add(button);
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (!button.enabled) {
            return;
        }
        if (button.id == ID_BACK) {
            mc.displayGuiScreen(new KOMEGuiAlliance());
        } else if (button.id >= ID_TAB_OVERVIEW && button.id <= ID_TAB_MILITARY) {
            selectedView = button.id - ID_TAB_OVERVIEW;
            selectedType = selectedView == 3 ? 1 : 0;
            permissions = selectedView == 2;
            contentScroll = 0;
            confirmation.hide();
            initGui();
        } else if (button.id >= ID_TRACK_CIVIL && button.id < ID_TRACK_CIVIL + TYPES.length) {
            selectedType = button.id - ID_TRACK_CIVIL;
            contentScroll = 0;
            confirmation.hide();
            initGui();
        } else if (button.id >= ID_REQUIREMENT_ROLL && button.id < ID_REQUIREMENT_ROLL + TYPES.length) {
            int type = button.id - ID_REQUIREMENT_ROLL;
            sendAction("roll", typeKey(type), record.keyA, record.keyB);
        } else if (button.id == ID_ACCEPT) {
            sendAction("accept", typeKey(selectedType), record.keyA, record.keyB);
        } else if (button.id == ID_LEDGER) {
            KOMEQuotaLedgerOverlay.reset();
            sendAction("ledger", "", ledgerContributor(), ledgerPartner());
        } else if (button.id == ID_ROLL) {
            sendAction("roll", typeKey(selectedType), record.keyA, record.keyB);
        } else if (button.id == ID_CLAIM) {
            sendAction("claim", "", ledgerPartner(), ledgerContributor());
        } else if (button.id == ID_BREAK) {
            showBreakConfirmation();
        } else if (button.id == ID_PERMS) {
            mc.displayGuiScreen(new KOMEGuiAlliancePermissions(record, selectedType));
        } else if (button.id == ID_CONTEXT && selectedType == 1) {
            sendAction("companies", "", record.keyA, record.keyB);
        } else if (button.id == ID_HISTORY) {
            KOMEPacketHandler.network.sendToServer(new KOMEPacketMovementHistoryRequest(KOMEGuiAlliance.viewerFactionKey(), false));
        }
    }

    private void sendAction(String action, String track, String first, String second) {
        KOMEPacketHandler.network.sendToServer(new KOMEPacketAllianceAction(action, track, first, second));
    }

    private void showBreakConfirmation() {
        String verb = selectedTier() == -2 ? "Cancel Request" : "Break Alliance";
        String consequence = selectedTier() == -2
            ? "Cancel the pending " + TYPES[selectedType] + " request between " + record.factionA + " and " + record.factionB + "."
            : "Revoke the mutual " + TYPES[selectedType] + " track. Its benefits stop and in-transit access is halted safely.";
        confirmation.show(verb, consequence, verb);
    }

    void setVisualTestView(int view, int type, boolean confirmBreak) {
        if (!Boolean.getBoolean("kome.guiCapture")) return;
        selectedView = Math.max(0, Math.min(3, view));
        selectedType = Math.max(0, Math.min(2, type));
        if (confirmBreak) showBreakConfirmation();
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }
        contentPanel.layout(panelX + MARGIN, getScrollContentY(), panelW - MARGIN * 2,
            getContentBottom() - getScrollContentY(), getContentHeight()).setScroll(contentScroll);
        contentPanel.setScroll(contentScroll + (wheel < 0 ? 18 : -18));
        contentScroll = contentPanel.getScroll();
        initGui();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (confirmation.isVisible()) {
            int result = confirmation.click(mouseX, mouseY, mouseButton);
            if (result == KOMEGuiConfirmationDialog.CONFIRM) {
                confirmation.hide();
                sendAction("break", typeKey(selectedType), record.keyA, record.keyB);
            } else if (result == KOMEGuiConfirmationDialog.CANCEL) {
                confirmation.hide();
            }
            return;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (confirmation.isVisible() && keyCode == 1) {
            confirmation.hide();
            return;
        }
        super.keyTyped(typedChar, keyCode);
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
        confirmation.draw(fontRendererObj, width, height, mouseX, mouseY);
    }

    private void drawHeaderCard(int mouseX, int mouseY) {
        int x = panelX + MARGIN;
        boolean compact = isCompactLayout();
        int y = panelY + (compact ? 34 : 44);
        int w = panelW - MARGIN * 2;
        int cardHeight = compact ? 38 : HEADER_CARD_HEIGHT;
        KOMEGuiTheme.drawCard(x, y, w, cardHeight, KOMEGuiTheme.isHovered(mouseX, mouseY, x, y, w, cardHeight));
        String relationship = KOMEGuiTheme.trimToWidth(fontRendererObj, record.factionA + " <-> " + record.factionB, w - 24);
        KOMEGuiTheme.drawCenteredPlainText(fontRendererObj, relationship, x + w / 2, y + (compact ? 6 : 9), KOMEGuiTheme.COLOR_BORDER_RED);
        if (compact) {
            String status = KOMEGuiTheme.trimToWidth(fontRendererObj, "Status: " + relationshipStatus(), w / 2 - 22);
            fontRendererObj.drawString(status, x + 14, y + 22, relationshipStatusColor());
            String updated = KOMEGuiTheme.trimToWidth(fontRendererObj, "Updated: " + record.lastUpdatedBy, w / 2 - 22);
            fontRendererObj.drawString(updated, x + w - fontRendererObj.getStringWidth(updated) - 14, y + 22,
                KOMEGuiTheme.COLOR_TEXT_MUTED);
            return;
        }
        fontRendererObj.drawString("Status: " + relationshipStatus(), x + 14, y + 29, relationshipStatusColor());
        String viewLabel = selectedView == 0 ? "Overview" : selectedView == 1 ? "Requirements" : selectedView == 2 ? "Benefits"
            : TYPES[selectedType] + " " + displayTier(selectedTier());
        fontRendererObj.drawString("View: " + viewLabel, x + w / 2 - 42, y + 29, KOMEGuiTheme.COLOR_TEXT);
        String updated = "Updated: " + KOMEGuiTheme.trimToWidth(fontRendererObj, record.lastUpdatedBy, 128);
        fontRendererObj.drawString(updated, x + w - fontRendererObj.getStringWidth(updated) - 14, y + 29, KOMEGuiTheme.COLOR_TEXT_MUTED);
        String lifecycle = record.provisional() ? record.graceText() : record.kingStatus();
        fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, lifecycle, w - 28), x + 14, y + 44,
            record.provisional() ? KOMEGuiTheme.COLOR_WARN : KOMEGuiTheme.COLOR_TEXT_MUTED);
    }

    private void drawTypeTabsBackdrop() {
        KOMEGuiTheme.drawDivider(panelX + MARGIN, getTabsY() + TAB_HEIGHT + 4, panelW - MARGIN * 2);
    }

    private void drawContent(int mouseX, int mouseY) {
        int x = panelX + MARGIN;
        int y = getScrollContentY();
        int w = panelW - MARGIN * 2;
        int h = getContentBottom() - y;
        contentPanel.layout(x, y, w, h, getContentHeight()).setScroll(contentScroll);
        contentScroll = contentPanel.getScroll();
        contentPanel.begin(mc);
        int cursor = y - contentScroll;
        if (selectedView == 1) {
            for (int type = 0; type < TYPES.length; type++) {
                cursor = drawRequirementSummaryCard(type, x, cursor, w, mouseX, mouseY) + GAP;
            }
        } else if (selectedView == 2) {
            cursor = drawBenefitCatalog(x, cursor, w, mouseX, mouseY);
        } else if (selectedView == 3) {
            cursor = drawMilitaryContextCard(false, x, cursor, w, mouseX, mouseY) + GAP;
            cursor = drawMilitaryContextCard(true, x, cursor, w, mouseX, mouseY) + GAP;
            List companies = new ArrayList();
            companies.addAll(record.militaryCompanies(false));
            companies.addAll(record.militaryCompanies(true));
            if (companies.isEmpty()) {
                cursor = drawMilitaryEmptyCard(x, cursor, w, mouseX, mouseY) + GAP;
            } else {
                for (Object value : companies) {
                    cursor = drawMilitaryCompanyCard((KOMEGuiAlliance.MilitaryCompany) value,
                        x, cursor, w, mouseX, mouseY) + GAP;
                }
            }
        } else {
            if (selectedView == 0) {
                int warningHeight = getOverviewWarningHeight(w);
                if (warningHeight > 0) {
                    KOMEGuiTheme.drawWarningBanner(fontRendererObj, "Alliance Notice", getOverviewWarningText(),
                        x, cursor, w, getOverviewWarningStatus());
                    cursor += warningHeight + GAP;
                }
            }
            cursor = drawCurrentTierCard(x, cursor, w, mouseX, mouseY) + GAP;
            cursor = drawNextObjectiveCard(x, cursor, w, mouseX, mouseY) + GAP;
            cursor = drawProgressCard(x, cursor, w, mouseX, mouseY) + GAP;
            if (shouldShowRandomQuotaCard()) {
                cursor = drawRandomQuotaCard(x, cursor, w, mouseX, mouseY) + GAP;
            }
            cursor = drawBenefitsCard(x, cursor, w, BENEFITS_CARD_HEIGHT, mouseX, mouseY, false) + GAP;
        }
        contentPanel.end();
        contentPanel.drawScrollbar();
    }

    private int drawBenefitCatalog(int x, int y, int w, int mouseX, int mouseY) {
        int cursor = y;
        for (int type = 0; type < TYPES.length; type++) {
            for (int tier = 1; tier <= maxTier(type); tier++) {
                KOMEAllianceBenefits.Benefit benefit = KOMEAllianceBenefits.get(typeKey(type), tier);
                int h = benefitCardHeight(benefit.restriction, w);
                KOMEGuiTheme.drawCard(x, cursor, w, h,
                    KOMEGuiTheme.isHovered(mouseX, mouseY, x, cursor, w, h));
                String heading = TYPES[type] + " T" + tier + " - " + benefit.title;
                fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, heading, w - 118),
                    x + 16, cursor + 10, KOMEGuiTheme.COLOR_BORDER_RED);
                String state = benefitState(type, tier);
                int stateColor = "Active".equals(state) ? KOMEGuiTheme.COLOR_GOOD
                    : "Suspended".equals(state) ? KOMEGuiTheme.COLOR_WARN
                    : "Planned".equals(state) ? KOMEGuiTheme.COLOR_GOLD_DARK : KOMEGuiTheme.COLOR_TEXT_DISABLED;
                fontRendererObj.drawString(state, x + w - fontRendererObj.getStringWidth(state) - 16,
                    cursor + 10, stateColor);
                KOMEGuiTheme.drawWrappedText(fontRendererObj, benefit.restriction, x + 16, cursor + 29,
                    w - 32, KOMEGuiTheme.COLOR_TEXT);
                cursor += h + GAP;
            }
        }
        return cursor;
    }

    private String benefitState(int type, int tier) {
        if (type == 2 && tier == 2) return "Planned";
        if ("suspended".equalsIgnoreCase(record.status(type))) return "Suspended";
        return record.getTier(type) >= tier ? "Active" : "Locked";
    }

    private int benefitCardHeight(String restriction, int width) {
        int lines = KOMEGuiTheme.wrapText(fontRendererObj, restriction, Math.max(1, width - 32)).size();
        return Math.max(56, 35 + lines * 10);
    }

    private int drawRequirementSummaryCard(int type, int x, int y, int w, int mouseX, int mouseY) {
        int h = 120;
        KOMEGuiTheme.drawCard(x, y, w, h, KOMEGuiTheme.isHovered(mouseX, mouseY, x, y, w, h));
        int tier = record.getTier(type);
        int max = maxTier(type);
        int target = tier >= 0 && tier < max ? tier + 1 : 0;
        KOMEGuiAlliance.TrackRecord ours = record.track(type, false);
        KOMEGuiAlliance.TrackRecord partner = record.track(type, true);
        fontRendererObj.drawString(TYPES[type] + " " + displayTier(tier), x + 16, y + 10, KOMEGuiTheme.COLOR_BORDER_RED);
        String state = trackState(ours);
        int stateColor = state.indexOf("Completed") >= 0 ? KOMEGuiTheme.COLOR_GOOD
            : state.indexOf("Invalid") >= 0 ? KOMEGuiTheme.COLOR_BAD : KOMEGuiTheme.COLOR_WARN;
        fontRendererObj.drawString(state, x + w - fontRendererObj.getStringWidth(state) - 16, y + 10, stateColor);
        String ourText = target == 0 ? (tier == -2 ? "Pending acceptance." : tier < 0 ? "No active track." : "Track complete.")
            : progressText(ours, record.quota(type, false));
        String partnerText = target == 0 ? "No partner contribution remains."
            : progressText(partner, record.quota(type, true));
        fontRendererObj.drawString("Our contribution: " + KOMEGuiTheme.trimToWidth(fontRendererObj, ourText, w - 144),
            x + 16, y + 31, KOMEGuiTheme.COLOR_TEXT);
        fontRendererObj.drawString("View Partner Progress: " + KOMEGuiTheme.trimToWidth(fontRendererObj, partnerText, w - 168),
            x + 16, y + 47, KOMEGuiTheme.COLOR_TEXT_MUTED);
        if (ours != null && target > 0) {
            String milestones = ours.activityLabel + ": " + ours.activityProgress + "/" + ours.activityRequired;
            if (ours.populationRequired > 0) milestones += " | Military population: " + ours.populationProgress + "/" + ours.populationRequired;
            fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, milestones, w - 32), x + 16, y + 63, KOMEGuiTheme.COLOR_TEXT_MUTED);
            fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, "Remaining: " + remainingText(ours), w - 32),
                x + 16, y + 79, KOMEGuiTheme.COLOR_TEXT_MUTED);
            if (ours.canManage && record.needsQuotaRoll(type)) {
                fontRendererObj.drawString("Server-authorized action:", x + 16, y + 96, KOMEGuiTheme.COLOR_GOOD);
            }
        }
        return y + h;
    }

    private String trackState(KOMEGuiAlliance.TrackRecord track) {
        if (track == null) return "Server state unavailable";
        if (track.completed) return "Completed";
        if (track.waived) return "Waived";
        if (track.successionActive) return "Succession grace";
        if (track.graceActive) return "Contribution grace";
        if (track.actionReason.indexOf("INVALID_REQUIREMENT") >= 0) return "Invalid";
        return "Active";
    }

    private String progressText(KOMEGuiAlliance.TrackRecord track, String fallback) {
        if (track == null) return fallback;
        if (track.quotaName.length() == 0) return fallback;
        return track.quotaName + " " + track.quotaDelivered + "/" + track.quotaRequired;
    }

    private String remainingText(KOMEGuiAlliance.TrackRecord track) {
        if (track.completed || track.waived) return "none";
        List parts = new ArrayList();
        if (track.quotaRequired > track.quotaDelivered) parts.add((track.quotaRequired - track.quotaDelivered) + " quota units");
        if (track.activityRequired > track.activityProgress) parts.add((track.activityRequired - track.activityProgress) + " activity");
        if (track.populationRequired > track.populationProgress) parts.add((track.populationRequired - track.populationProgress) + " offensive population");
        if (parts.isEmpty()) return track.actionReason;
        StringBuilder result = new StringBuilder();
        for (Object part : parts) {
            if (result.length() > 0) result.append(", ");
            result.append(part);
        }
        return result.toString();
    }

    private int drawMilitaryContextCard(boolean alliedSide, int x, int y, int w, int mouseX, int mouseY) {
        KOMEGuiAlliance.MilitaryContext context = record.militaryContext(alliedSide);
        int h = context == null ? 66 : 116;
        KOMEGuiTheme.drawCard(x, y, w, h, KOMEGuiTheme.isHovered(mouseX, mouseY, x, y, w, h));
        String nativeName = alliedSide ? record.factionB : record.factionA;
        if (context != null) nativeName = displayFactionForKey(context.nativeFaction);
        fontRendererObj.drawString("Native: " + nativeName, x + 16, y + 10, KOMEGuiTheme.COLOR_BORDER_RED);
        if (context == null) {
            fontRendererObj.drawString("Server Military context is unavailable.", x + 16, y + 31, KOMEGuiTheme.COLOR_WARN);
            return y + h;
        }
        String state = context.state.replace('_', ' ');
        int color = "ACTIVE".equals(context.state) ? KOMEGuiTheme.COLOR_GOOD
            : "CONTRADICTION".equals(context.state) ? KOMEGuiTheme.COLOR_BAD : KOMEGuiTheme.COLOR_WARN;
        fontRendererObj.drawString(state, x + w - fontRendererObj.getStringWidth(state) - 16, y + 10, color);
        String owner = context.nativeKingName.length() == 0 ? "Kingless (native faction owns funded troops)" : context.nativeKingName;
        String controller = context.supportingKingName.length() == 0 ? "None - supporting faction has no king" : context.supportingKingName + " (recognized king)";
        fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, "Actual owner: " + owner, w - 32), x + 16, y + 29, KOMEGuiTheme.COLOR_TEXT);
        fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, "Temporary controller: " + controller, w - 32), x + 16, y + 44, KOMEGuiTheme.COLOR_TEXT);
        fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, "Authorizing wars: " + context.wars, w - 32), x + 16, y + 59, KOMEGuiTheme.COLOR_TEXT_MUTED);
        fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, "Legal opposing factions: " + context.opponents, w - 32), x + 16, y + 74, KOMEGuiTheme.COLOR_TEXT_MUTED);
        String pool = "Global offensive population: " + context.used + " used / " + context.eligible
            + " eligible / " + context.available + " available";
        fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, pool, w - 32),
            x + 16, y + 89, KOMEGuiTheme.COLOR_TEXT_MUTED);
        fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, context.reason, w - 32), x + 16, y + 104, color);
        return y + h;
    }

    private int drawMilitaryEmptyCard(int x, int y, int w, int mouseX, int mouseY) {
        int h = 58;
        KOMEGuiTheme.drawCard(x, y, w, h, KOMEGuiTheme.isHovered(mouseX, mouseY, x, y, w, h));
        drawCardTitle("Companies", x, y);
        fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj,
            "No voluntary, stewardship, withdrawal, or reclaim company is recorded for this alliance.", w - 32),
            x + 16, y + 31, KOMEGuiTheme.COLOR_TEXT_MUTED);
        return y + h;
    }

    private int drawMilitaryCompanyCard(KOMEGuiAlliance.MilitaryCompany company, int x, int y, int w, int mouseX, int mouseY) {
        int h = 104;
        KOMEGuiTheme.drawCard(x, y, w, h, KOMEGuiTheme.isHovered(mouseX, mouseY, x, y, w, h));
        fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, company.name + " [" + company.id + "]", w - 150),
            x + 16, y + 10, KOMEGuiTheme.COLOR_BORDER_RED);
        fontRendererObj.drawString(company.cleanupState, x + w - fontRendererObj.getStringWidth(company.cleanupState) - 16, y + 10,
            "NONE".equals(company.cleanupState) ? KOMEGuiTheme.COLOR_GOOD : KOMEGuiTheme.COLOR_WARN);
        fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, "Owner: " + company.owner + " | Controller: "
            + (company.controller.length() == 0 ? "None" : company.controller) + (company.controllerIsKing ? " (king)" : ""), w - 32),
            x + 16, y + 29, KOMEGuiTheme.COLOR_TEXT);
        fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, "Wars: " + company.wars + " | Tendency: " + company.tendency
            + " | Population: " + company.population, w - 32), x + 16, y + 44, KOMEGuiTheme.COLOR_TEXT_MUTED);
        fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, "Movement: " + company.movementState + " | Cleanup: " + company.cleanupState,
            w - 32), x + 16, y + 59, KOMEGuiTheme.COLOR_TEXT_MUTED);
        fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, "Authorized actions: " + company.actions, w - 32),
            x + 16, y + 74, KOMEGuiTheme.COLOR_GOOD);
        fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, company.reason, w - 32),
            x + 16, y + 89, KOMEGuiTheme.COLOR_TEXT_MUTED);
        return y + h;
    }

    private String displayFactionForKey(String key) {
        if (record.keyA.equals(key)) return record.factionA;
        if (record.keyB.equals(key)) return record.factionB;
        return key;
    }

    private int drawCurrentTierCard(int x, int y, int w, int mouseX, int mouseY) {
        KOMEGuiTheme.drawCard(x, y, w, CARD_HEIGHT, KOMEGuiTheme.isHovered(mouseX, mouseY, x, y, w, CARD_HEIGHT));
        drawCardTitle("Current Tier", x, y);
        fontRendererObj.drawString(tierName(), x + 16, y + 30, KOMEGuiTheme.COLOR_TEXT);
        KOMEGuiTheme.drawFactionBadge(fontRendererObj, tierBadge(), x + w - 52, y + 18, 34, selectedTier() >= 0 ? KOMEGuiTheme.COLOR_BORDER_RED : KOMEGuiTheme.COLOR_TEXT_MUTED);
        return y + CARD_HEIGHT;
    }

    private int drawNextObjectiveCard(int x, int y, int w, int mouseX, int mouseY) {
        KOMEGuiTheme.drawCard(x, y, w, CARD_HEIGHT, KOMEGuiTheme.isHovered(mouseX, mouseY, x, y, w, CARD_HEIGHT));
        drawCardTitle("Next Objective", x, y);
        KOMEGuiTheme.drawWrappedText(fontRendererObj, nextRequirement(), x + 16, y + 29, w - 190, KOMEGuiTheme.COLOR_TEXT);
        String reward = selectedTier() >= maxTier(selectedType) ? "All tiers complete"
            : "Reward: " + currentUnlock(Math.max(1, selectedTier() + 1));
        fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, reward, 160), x + w - 176, y + 31, KOMEGuiTheme.COLOR_TEXT_MUTED);
        return y + CARD_HEIGHT;
    }

    private int drawProgressCard(int x, int y, int w, int mouseX, int mouseY) {
        KOMEGuiTheme.drawCard(x, y, w, CARD_HEIGHT, KOMEGuiTheme.isHovered(mouseX, mouseY, x, y, w, CARD_HEIGHT));
        drawCardTitle("Progress", x, y);
        Progress progress = getProgress();
        fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, progress.label, progress.required > 0 ? w / 2 - 22 : w - 32), x + 16, y + 31, KOMEGuiTheme.COLOR_TEXT);
        if (progress.required > 0) {
            int barW = Math.min(220, w / 2);
            KOMEGuiTheme.drawProgressBar(fontRendererObj, x + w - barW - 52, y + 29, barW, 10, progress.delivered / (float) progress.required, KOMEGuiTheme.COLOR_BORDER_RED, "");
            int percent = Math.min(100, Math.max(0, progress.delivered * 100 / progress.required));
            fontRendererObj.drawString(percent + "%", x + w - 42, y + 30, KOMEGuiTheme.COLOR_TEXT);
        }
        return y + CARD_HEIGHT;
    }

    private int drawRandomQuotaCard(int x, int y, int w, int mouseX, int mouseY) {
        KOMEGuiTheme.drawCard(x, y, w, QUOTA_CARD_HEIGHT, KOMEGuiTheme.isHovered(mouseX, mouseY, x, y, w, QUOTA_CARD_HEIGHT));
        drawCardTitle("Random Quota", x, y);
        String quota = getQuotaText();
        KOMEGuiTheme.drawWrappedText(fontRendererObj, quota, x + 16, y + 29, w - 176, KOMEGuiTheme.COLOR_TEXT);
        if (!canRollQuota()) {
            fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, rollUnavailableReason(), 128), x + w - 144, y + 43, KOMEGuiTheme.COLOR_TEXT_DISABLED);
        }
        return y + QUOTA_CARD_HEIGHT;
    }

    private int drawBenefitsCard(int x, int y, int w, int h, int mouseX, int mouseY, boolean allPermissions) {
        KOMEGuiTheme.drawCard(x, y, w, h, KOMEGuiTheme.isHovered(mouseX, mouseY, x, y, w, h));
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
        int y = panelY + panelH - getActionBarHeight();
        KOMEGuiTheme.drawDivider(panelX + MARGIN, y, panelW - MARGIN * 2);
        String message = KOMEGuiAlliance.serverMessage();
        if (message.length() > 0 && !isCompactLayout()) {
            fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, message, panelW - MARGIN * 2), panelX + MARGIN, y + 2, KOMEGuiTheme.COLOR_TEXT_MUTED);
        }
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
        if (id == ID_ACCEPT) {
            return acceptUnavailableReason();
        }
        if (id == ID_LEDGER) {
            return ledgerUnavailableReason();
        }
        if (id == ID_BREAK) {
            return breakUnavailableReason();
        }
        if (id == ID_PERMS) {
            return record.hasAnyAlliance() ? "" : "No alliance type exists for this direction.";
        }
        if (id == ID_CONTEXT) {
            return "This view unlocks at Tier 1 and remains server-authorized.";
        }
        if (id == ID_HISTORY) {
            return "Movement history is available after the Military track is accepted.";
        }
        return "This action is not available.";
    }

    private boolean canAcceptSelectedType() {
        return selectedTier() == -2 && (KOMEGuiAlliance.viewerIsAdmin() || KOMEGuiAlliance.viewerIsKing()
            && KOMEGuiAlliance.viewerFactionKey().equals(record.pendingReceiver(selectedType)));
    }

    private boolean canOpenLedger() {
        return record.hasAnyAcceptedAlliance();
    }

    private String ledgerContributor() {
        return record.contributorFaction.length() == 0 ? record.keyA : record.contributorFaction;
    }

    private String ledgerPartner() {
        return record.allyFaction.length() == 0 ? (ledgerContributor().equals(record.keyA) ? record.keyB : record.keyA) : record.allyFaction;
    }

    private boolean canBreakSelectedType() {
        return selectedTier() != -1 && (KOMEGuiAlliance.viewerIsKing() || KOMEGuiAlliance.viewerIsAdmin());
    }

    private String acceptUnavailableReason() {
        if (selectedTier() != -2) {
            return "Only pending requests can be accepted.";
        }
        if (!KOMEGuiAlliance.viewerIsKing()) {
            return "Only the receiving faction king can accept.";
        }
        if (!KOMEGuiAlliance.viewerFactionKey().equals(record.pendingReceiver(selectedType))) {
            return "Only the recorded receiving faction can accept this request.";
        }
        return "";
    }

    private String ledgerUnavailableReason() {
        return record.hasAnyAcceptedAlliance() ? "" : "Ledger unlocks after at least one alliance type is accepted.";
    }

    private String breakUnavailableReason() {
        if (selectedTier() == -1) {
            return "No " + TYPES[selectedType] + " alliance exists to break.";
        }
        return KOMEGuiAlliance.viewerIsKing() || KOMEGuiAlliance.viewerIsAdmin() ? ""
            : "Only a participating faction king or operator may break this mutual track.";
    }

    private boolean shouldShowRandomQuotaCard() {
        return selectedTier() >= 0 && selectedTier() < maxTier(selectedType);
    }

    private boolean canRollQuota() {
        return selectedTier() >= 0 && selectedTier() < maxTier(selectedType) && record.needsQuotaRoll(selectedType);
    }

    private String rollUnavailableReason() {
        if (selectedTier() < 0) {
            return "Quota rolls require an active alliance.";
        }
        if (selectedTier() >= maxTier(selectedType)) {
            return "All tiers for this track are complete.";
        }
        return "Quota already assigned.";
    }

    private String getQuotaText() {
        return "Your side: " + record.quota(selectedType, false) + ". Allied side: " + record.quota(selectedType, true) + ".";
    }

    private String relationshipStatus() {
        int tier = selectedTier();
        if (tier == -2) {
            return "Pending " + TYPES[selectedType] + " Request";
        }
        if (record.provisional() && tier >= 0) {
            return "Provisional Alliance";
        }
        if (tier >= 0) return "Active Alliance";
        return "No " + TYPES[selectedType] + " Alliance";
    }

    private int relationshipStatusColor() {
        int tier = selectedTier();
        return record.provisional() || tier == -2 ? KOMEGuiTheme.COLOR_WARN : tier >= 0 ? KOMEGuiTheme.COLOR_GOOD : KOMEGuiTheme.COLOR_TEXT_MUTED;
    }

    private String tierName() {
        int tier = selectedTier();
        if (tier == -2) {
            return "Pending";
        }
        if (tier < 0) {
            return "None";
        }
        return tier == 0 ? "Established - accepted base; no tier benefit" : "Tier " + roman(tier) + " - " + currentUnlock(tier);
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
        if (tier >= maxTier(selectedType)) {
            return "No further requirement.";
        }
        int target = tier + 1;
        return requirementText(selectedType, target);
    }

    private Progress getProgress() {
        return new Progress(0, 0, "Your side: " + record.quota(selectedType, false) + " | Ally: " + record.quota(selectedType, true));
    }

    private String benefitSummary() {
        int tier = selectedTier();
        if (tier < 0) {
            return tier == -2 ? "No benefits unlocked until the request is accepted." : "No benefits unlocked for this alliance type.";
        }
        if (tier == 0) {
            return "T0 is accepted base status and grants no tier benefit.";
        }
        String[] unlocks = UNLOCKS[selectedType];
        StringBuilder builder = new StringBuilder();
        int max = Math.min(tier, unlocks.length - 1);
        for (int i = 1; i <= max; i++) {
            if (i > 1) {
                builder.append(", ");
            }
            KOMEAllianceBenefits.Benefit benefit = KOMEAllianceBenefits.get(typeKey(selectedType), i);
            builder.append(benefit.title).append(" - ").append(benefit.restriction);
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
        if (tier == 0) {
            return "Accepted base; no tier benefit";
        }
        String[] unlocks = UNLOCKS[type];
        int index = Math.min(tier, unlocks.length - 1);
        return unlocks[index];
    }

    private int maxTier(int type) {
        return type == 1 ? 3 : 2;
    }

    private String activityRequirement(int targetTier) {
        return activityRequirement(selectedType, targetTier);
    }

    private String requirementText(int type, int targetTier) {
        String key = typeKey(type);
        int stacks = KOMEClientData.INSTANCE.getAllianceItemStackEquivalents(key, targetTier);
        return "Both sides complete an independent rolled T" + targetTier + " item quota (" + stacks
            + " ordinary-stack equivalents before item weighting)" + activityRequirement(type, targetTier) + ".";
    }

    private String activityRequirement(int type, int targetTier) {
        int activity = KOMEClientData.INSTANCE.getAllianceActivityRequirement(typeKey(type), targetTier);
        int population = KOMEClientData.INSTANCE.getAlliancePopulationRequirement(typeKey(type), targetTier);
        if (type == 0 && targetTier == 2) {
            return " plus " + activity + " legitimate allied trades per side";
        }
        if (type == 1 && targetTier >= 1) {
            return " plus " + activity + " cumulative eligible NPC kills and " + population + " effective offensive population per side";
        }
        if (type == 2 && targetTier == 1) {
            return " plus " + activity + " legitimate allied trades per side";
        }
        if (type == 2 && targetTier == 2) {
            return " plus " + activity + " cumulative legitimate allied trades";
        }
        return "";
    }

    private int selectedTier() {
        return record.getTier(selectedType);
    }

    private int getTabsY() {
        return panelY + (isCompactLayout() ? 76 : 112);
    }

    private int getContentY() {
        return getTabsY() + TAB_HEIGHT + 14;
    }

    /**
     * Top of the region that is allowed to scroll. Overview owns a fixed track-selector
     * row above this point, so those navigation controls never cover the page tabs.
     */
    private int getScrollContentY() {
        return getContentY() + (selectedView == 0 ? 32 : 0);
    }

    private boolean isFullyInsideScrollViewport(int y, int height) {
        return y >= getScrollContentY() && y + height <= getContentBottom();
    }

    private int getContentBottom() {
        return panelY + panelH - getActionBarHeight() - (isCompactLayout() ? 5 : 8);
    }

    private boolean isCompactLayout() {
        return panelH < 300;
    }

    private int getActionBarHeight() {
        return isCompactLayout() ? 42 : ACTION_BAR_HEIGHT;
    }

    private int getContentHeight() {
        if (selectedView == 1) {
            return (120 + GAP) * 3;
        }
        if (selectedView == 2) {
            int width = panelW - MARGIN * 2;
            int height = 0;
            for (int type = 0; type < TYPES.length; type++) {
                for (int tier = 1; tier <= maxTier(type); tier++) {
                    height += benefitCardHeight(KOMEAllianceBenefits.get(typeKey(type), tier).restriction, width) + GAP;
                }
            }
            return height;
        }
        if (selectedView == 3) {
            int height = 116 * 2 + GAP * 2;
            int companies = record.militaryCompanies(false).size() + record.militaryCompanies(true).size();
            return height + (companies == 0 ? 58 : companies * (104 + GAP));
        }
        int height = permissions ? BENEFITS_CARD_HEIGHT + 42 : CARD_HEIGHT * 3 + BENEFITS_CARD_HEIGHT + GAP * 3;
        if (selectedView == 0) height += getOverviewWarningHeight(panelW - MARGIN * 2) + (getOverviewWarningText().length() == 0 ? 0 : GAP);
        if (!permissions && shouldShowRandomQuotaCard()) {
            height += QUOTA_CARD_HEIGHT + GAP;
        }
        return height;
    }

    private int getMaxScroll() {
        return Math.max(0, getContentHeight() - Math.max(1, getContentBottom() - getScrollContentY()));
    }

    private int getRandomQuotaCardY() {
        int warning = 0;
        if (selectedView == 0) {
            int banner = getOverviewWarningHeight(panelW - MARGIN * 2);
            if (banner > 0) warning += banner + GAP;
        }
        return warning + CARD_HEIGHT * 3 + GAP * 3;
    }

    private String getOverviewWarningText() {
        if (record.provisional()) return record.graceText() + ". Progress and benefits remain subject to succession rules.";
        if (record.kingStatus().indexOf("Kingless") >= 0) return record.kingStatus() + ". King-only actions remain unavailable until succession completes.";
        if (selectedTier() == -2) return "This " + TYPES[selectedType] + " request is pending acceptance by " + record.pendingReceiver(selectedType) + ".";
        if ("suspended".equalsIgnoreCase(record.status(selectedType))) return "This track is suspended by an authoritative war or lifecycle restriction. Review Military context for the recorded reason.";
        if (selectedType == 1) {
            KOMEGuiAlliance.MilitaryContext ours = record.militaryContext(false);
            KOMEGuiAlliance.MilitaryContext ally = record.militaryContext(true);
            if (ours != null && "CONTRADICTION".equals(ours.state)) return ours.reason;
            if (ally != null && "CONTRADICTION".equals(ally.state)) return ally.reason;
        }
        return "";
    }

    private KOMEGuiTheme.Status getOverviewWarningStatus() {
        if ("suspended".equalsIgnoreCase(record.status(selectedType))) return KOMEGuiTheme.Status.DENIED;
        return KOMEGuiTheme.Status.WARNING;
    }

    private int getOverviewWarningHeight(int width) {
        String warning = getOverviewWarningText();
        return warning.length() == 0 ? 0 : KOMEGuiTheme.warningBannerHeight(fontRendererObj, warning, width);
    }

    private int getBenefitsCardY() {
        int y = CARD_HEIGHT * 3 + GAP * 3;
        if (shouldShowRandomQuotaCard()) {
            y += QUOTA_CARD_HEIGHT + GAP;
        }
        return y;
    }

    private static String typeKey(int type) {
        return type == 0 ? "civil" : type == 1 ? "military" : "trade";
    }

    private static String displayTier(int tier) {
        return tier == -2 ? "Pending" : tier < 0 ? "None" : tier == 0 ? "Established" : "T" + tier;
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
        private final boolean enabled;

        private Action(int id, String label, boolean danger, boolean enabled) {
            this.id = id;
            this.label = label;
            this.danger = danger;
            this.enabled = enabled;
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
