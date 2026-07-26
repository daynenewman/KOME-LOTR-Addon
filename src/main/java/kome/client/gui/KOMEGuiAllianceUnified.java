package kome.client.gui;

import kome.client.KOMEMinecraftClient;
import kome.common.data.KOMEAlliance;
import kome.common.network.KOMEPacketAllianceAction;
import kome.common.network.KOMEPacketAllianceRequest;
import kome.common.network.KOMEPacketHandler;
import lotr.client.gui.LOTRGuiMenu;
import lotr.client.gui.LOTRGuiMenuBase;
import net.minecraft.client.gui.GuiButton;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Active schema-7 alliance UI. One canonical relationship has two directional
 * four-stage ladders; no three-track controls are exposed here.
 */
public class KOMEGuiAllianceUnified extends LOTRGuiMenuBase {
    private static final int ID_MENU = 1;
    private static final int ID_NEW = 2;
    private static final int ID_REFRESH = 3;
    private static final int ID_OPERATOR = 4;
    private static final int ID_PREVIOUS = 5;
    private static final int ID_NEXT = 6;
    private static final int ID_REQUEST = 7;
    private static final int ID_ACCEPT = 20;
    private static final int ID_ROLL = 21;
    private static final int ID_CLAIM = 22;
    private static final int ID_LEDGER = 23;
    private static final int ID_COMPANIES = 24;
    private static final int ID_BREAK = 25;
    private static final int ID_TAB = 40;

    private static List<Relationship> relationships = new ArrayList<Relationship>();
    private static List<RequestOption> requestOptions = new ArrayList<RequestOption>();
    private static String viewerFaction = "";
    private static String viewerFactionName = "No pledged faction";
    private static boolean viewerKing;
    private static boolean viewerFactionHasKing;
    private static boolean viewerAdmin;
    private static boolean operatorView;
    private static String summary = "Alliances: 0";

    private final KOMEGuiScrollPanel scrollPanel = new KOMEGuiScrollPanel();
    private final KOMEGuiConfirmationDialog confirmation = new KOMEGuiConfirmationDialog();
    private final Map<Integer, String> disabledReasons = new HashMap<Integer, String>();
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int mode; // 0 list, 1 create, 2 detail
    private int tab;
    private int optionIndex;
    private String selectedPair = "";
    private int contentHeight;

    public static void update(List lines) {
        List<Relationship> nextRelationships = new ArrayList<Relationship>();
        List<RequestOption> nextOptions = new ArrayList<RequestOption>();
        if (lines != null) {
            for (Object value : lines) {
                String[] parts = String.valueOf(value).split("\t", -1);
                if (parts.length >= 7 && "VIEWER".equals(parts[0])) {
                    viewerFaction = parts[1];
                    viewerFactionName = parts[2];
                    viewerKing = flag(parts[3]);
                    viewerFactionHasKing = flag(parts[4]);
                    viewerAdmin = flag(parts[5]);
                    operatorView = flag(parts[6]);
                } else if (parts.length >= 2 && "SUMMARY".equals(parts[0])) {
                    summary = "Formal Alliances: " + parts[1];
                } else if (parts.length >= 8 && "REQUEST_OPTION_V2".equals(parts[0])) {
                    RequestOption option = new RequestOption(parts);
                    if (option.allowed) nextOptions.add(option);
                } else if (parts.length >= 30 && "STAGE_RELATION".equals(parts[0])) {
                    nextRelationships.add(new Relationship(parts));
                }
            }
        }
        relationships = nextRelationships;
        requestOptions = nextOptions;
        if (KOMEMinecraftClient.currentScreen() instanceof KOMEGuiAllianceUnified) {
            ((KOMEGuiAllianceUnified) KOMEMinecraftClient.currentScreen()).refreshAfterPacket();
        }
    }

    public static void resetData() {
        relationships = new ArrayList<Relationship>();
        requestOptions = new ArrayList<RequestOption>();
        viewerFaction = "";
        viewerFactionName = "No pledged faction";
        viewerKing = false;
        viewerFactionHasKing = false;
        viewerAdmin = false;
        operatorView = false;
        summary = "Formal Alliances: 0";
    }

    @Override
    public void initGui() {
        panelW = Math.max(300, Math.min(760, width - 16));
        panelH = Math.max(220, Math.min(470, height - 16));
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        super.initGui();
        buttonMenuReturn = null;
        requestData();
        configureButtons();
    }

    private void refreshAfterPacket() {
        if (selectedPair.length() > 0 && selected() == null) {
            selectedPair = "";
            mode = 0;
        }
        optionIndex = clamp(optionIndex, 0, Math.max(0, requestOptions.size() - 1));
        configureButtons();
    }

    private void requestData() {
        KOMEPacketHandler.network.sendToServer(new KOMEPacketAllianceRequest(operatorView));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        configureButtons();
        drawDefaultBackground();
        KOMEGuiTheme.drawMainPanel(panelX, panelY, panelW, panelH);
        KOMEGuiTheme.drawHeader(fontRendererObj, mode == 2 ? "Alliance Detail"
            : mode == 1 ? "Request Alliance" : "Alliances", panelX + 76, panelY + 13, panelW - 152);
        if (mode == 0) drawList(mouseX, mouseY);
        else if (mode == 1) drawCreate(mouseX, mouseY);
        else drawDetail(mouseX, mouseY);
        super.drawScreen(mouseX, mouseY, partialTicks);
        drawDisabledTooltip(mouseX, mouseY);
        confirmation.draw(fontRendererObj, width, height, mouseX, mouseY);
    }

    @Override
    public void updateScreen() {
        if (Boolean.getBoolean("kome.guiCapture") && mc.thePlayer == null) return;
        super.updateScreen();
    }

    private void configureButtons() {
        buttonList.clear();
        disabledReasons.clear();
        buttonList.add(KOMEGuiButton.small(ID_MENU, panelX + 14, panelY + 14, mode == 0 ? "Menu" : "Back"));
        buttonList.add(KOMEGuiButton.small(ID_REFRESH, panelX + panelW - 68, panelY + 14, "Refresh"));
        if (viewerAdmin && mode == 0) {
            buttonList.add(new KOMEGuiButton(ID_OPERATOR, panelX + 74, panelY + 40, 126, 20,
                "Operator View: " + (operatorView ? "On" : "Off"), true));
        }
        if (mode == 0) configureListButtons();
        else if (mode == 1) configureCreateButtons();
        else configureDetailButtons();
    }

    private void configureListButtons() {
        KOMEGuiButton create = KOMEGuiButton.small(ID_NEW, panelX + panelW - 68, panelY + 40, "New");
        create.enabled = viewerFaction.length() > 0 && viewerKing && !requestOptions.isEmpty();
        if (!create.enabled) disabledReasons.put(ID_NEW, viewerFaction.length() == 0
            ? "Pledge to a faction before negotiating." : !viewerKing
            ? "Only your faction's recognized king may send requests."
            : "No eligible faction currently fits the alliance-request rules.");
        buttonList.add(create);
    }

    private void configureCreateButtons() {
        int center = panelX + panelW / 2;
        int selectorY = panelY + 126;
        buttonList.add(KOMEGuiButton.small(ID_PREVIOUS, center - 188, selectorY, "<"));
        buttonList.add(KOMEGuiButton.small(ID_NEXT, center + 136, selectorY, ">"));
        KOMEGuiButton request = new KOMEGuiButton(ID_REQUEST, center - 94, panelY + panelH - 48, 188, 24,
            currentOption() != null && currentOption().automatic ? "Form Alliance" : "Send Request", true);
        request.enabled = currentOption() != null && viewerKing;
        if (!request.enabled) disabledReasons.put(ID_REQUEST, "No eligible receiver is selected.");
        buttonList.add(request);
    }

    private void configureDetailButtons() {
        int tabX = panelX + 18;
        int tabY = panelY + 104;
        int gap = 4;
        int tabW = (panelW - 36 - gap * 3) / 4;
        String[] labels = {"Overview", "Requirements", "Benefits", "Military"};
        for (int i = 0; i < labels.length; i++) {
            buttonList.add(KOMEGuiButton.tab(ID_TAB + i, tabX + i * (tabW + gap), tabY,
                tabW, labels[i], tab == i));
        }
        Relationship relation = selected();
        if (relation == null) return;
        List<Action> actions = new ArrayList<Action>();
        if (relation.pending) {
            boolean mayAccept = viewerKing && viewerFaction.equals(relation.pendingReceiver);
            actions.add(new Action(ID_ACCEPT, "Accept Request", mayAccept,
                mayAccept ? "" : "Only the receiving faction's recognized king may accept."));
        } else if (tab == 1) {
            boolean active = relation.active && relation.nextStage > 0;
            actions.add(new Action(ID_ROLL, relation.quotaName.length() == 0 ? "Roll Requirement" : "Requirement Rolled",
                active && relation.canManage && relation.quotaName.length() == 0,
                relation.canManage ? "The next-stage requirement is already rolled." : "Only your faction king may roll it."));
            boolean quotaReady = relation.quotaName.length() > 0 && relation.quotaDelivered >= relation.quotaRequired;
            boolean fixedReady = relation.fixedProgress >= relation.fixedRequired;
            actions.add(new Action(ID_CLAIM, "Claim Stage " + relation.nextStage,
                active && relation.canManage && quotaReady && fixedReady,
                !relation.canManage ? "Only your faction king may claim a stage."
                    : !quotaReady ? "The rolled goods quota is incomplete."
                    : !fixedReady ? relation.fixedDescription : "No stage remains to claim."));
            actions.add(new Action(ID_LEDGER, "Open Ledger", relation.active,
                "The contribution ledger becomes available after acceptance."));
        } else if (tab == 3) {
            actions.add(new Action(ID_COMPANIES, "Companies", relation.viewerStage >= 4,
                "Stage 4 Military Partnership is required."));
            actions.add(new Action(ID_LEDGER, "Open Ledger", relation.active,
                "The contribution ledger becomes available after acceptance."));
        }
        if (relation.hasRelationship()) {
            actions.add(new Action(ID_BREAK, relation.pending ? "Cancel Request" : "Break Alliance",
                relation.canManage || viewerAdmin, "Only a participating king or administrator may break this relationship."));
        }
        layoutActions(actions);
    }

    private void layoutActions(List<Action> actions) {
        if (actions.isEmpty()) return;
        int gap = 6;
        int count = actions.size();
        int w = Math.min(150, (panelW - 36 - gap * (count - 1)) / count);
        int total = count * w + gap * (count - 1);
        int x = panelX + (panelW - total) / 2;
        int y = panelY + panelH - 38;
        for (int i = 0; i < count; i++) {
            Action action = actions.get(i);
            KOMEGuiButton button = action.id == ID_BREAK
                ? KOMEGuiButton.destructive(action.id, x + i * (w + gap), y, w, action.label)
                : new KOMEGuiButton(action.id, x + i * (w + gap), y, w, 24, action.label, true);
            button.enabled = action.enabled;
            buttonList.add(button);
            if (!action.enabled) disabledReasons.put(action.id, action.reason);
        }
    }

    @Override
    public void actionPerformed(GuiButton button) {
        if (!button.enabled || confirmation.isVisible()) return;
        if (button.id == ID_MENU) {
            if (mode == 0) mc.displayGuiScreen(new LOTRGuiMenu());
            else {
                mode = 0;
                tab = 0;
                scrollPanel.setScroll(0);
            }
        } else if (button.id == ID_NEW) {
            mode = 1;
            optionIndex = 0;
        } else if (button.id == ID_REFRESH) {
            requestData();
        } else if (button.id == ID_OPERATOR) {
            operatorView = !operatorView;
            selectedPair = "";
            requestData();
        } else if (button.id == ID_PREVIOUS || button.id == ID_NEXT) {
            optionIndex = wrap(optionIndex + (button.id == ID_NEXT ? 1 : -1), requestOptions.size());
        } else if (button.id == ID_REQUEST) {
            RequestOption option = currentOption();
            if (option != null) send("request", viewerFaction, option.key);
        } else if (button.id >= ID_TAB && button.id < ID_TAB + 4) {
            tab = button.id - ID_TAB;
            scrollPanel.setScroll(0);
        } else if (button.id == ID_ACCEPT) {
            Relationship relation = selected();
            if (relation != null) send("accept", relation.keyA, relation.keyB);
        } else if (button.id == ID_ROLL || button.id == ID_CLAIM || button.id == ID_LEDGER
                || button.id == ID_COMPANIES) {
            Relationship relation = selected();
            if (relation != null) {
                String action = button.id == ID_ROLL ? "roll" : button.id == ID_CLAIM
                    ? "claimstage" : button.id == ID_LEDGER ? "ledger" : "companies";
                send(action, relation.side, relation.partner);
            }
        } else if (button.id == ID_BREAK) {
            Relationship relation = selected();
            if (relation != null) {
                confirmation.show(relation.pending ? "Cancel Alliance Request" : "Break Alliance",
                    "Both directional stages and active requirements will be erased. Stage 1 hiring, Stage 3 passage, "
                    + "delegation, and alliance wartime authority end immediately. Existing Stage 2 merchant-slot "
                    + "entitlements remain unique and persistent.", relation.pending ? "Cancel Request" : "Break Alliance");
            }
        }
        configureButtons();
    }

    private void send(String action, String first, String second) {
        KOMEPacketHandler.network.sendToServer(new KOMEPacketAllianceAction(action, "", first, second));
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (confirmation.isVisible()) {
            int result = confirmation.click(mouseX, mouseY, mouseButton);
            if (result == KOMEGuiConfirmationDialog.CONFIRM) {
                Relationship relation = selected();
                if (relation != null) send("break", relation.keyA, relation.keyB);
                confirmation.hide();
            } else if (result == KOMEGuiConfirmationDialog.CANCEL) {
                confirmation.hide();
            }
            return;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton != 0 || mode != 0) return;
        int y = contentY() + 6 - scrollPanel.getScroll();
        for (Relationship relation : relationships) {
            if (KOMEGuiTheme.isHovered(mouseX, mouseY, panelX + 22, y, panelW - 44, 68)) {
                selectedPair = relation.pair;
                mode = 2;
                tab = 0;
                scrollPanel.setScroll(0);
                configureButtons();
                return;
            }
            y += 76;
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        int mouseX = Mouse.getEventX() * width / mc.displayWidth;
        int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        scrollPanel.wheel(mouseX, mouseY, wheel, 24);
        configureButtons();
    }

    private void drawList(int mouseX, int mouseY) {
        int top = viewerAdmin ? panelY + 68 : panelY + 48;
        fontRendererObj.drawString(summary + " | Viewing: " + viewerFactionName, panelX + 22, top,
            KOMEGuiTheme.COLOR_TEXT_MUTED);
        int viewportY = top + 17;
        int viewportH = panelY + panelH - 18 - viewportY;
        contentHeight = relationships.isEmpty() ? viewportH : relationships.size() * 76 + 8;
        scrollPanel.layout(panelX + 16, viewportY, panelW - 32, viewportH, contentHeight);
        scrollPanel.begin(mc);
        if (relationships.isEmpty()) {
            KOMEGuiTheme.drawWarningBanner(fontRendererObj, "No formal relationships",
                viewerKing ? "Use New to request a formal alliance from an eligible faction."
                    : "Only a recognized faction king may create a formal alliance.",
                panelX + 22, viewportY + 16, panelW - 44, KOMEGuiTheme.Status.NEUTRAL);
        } else {
            int y = viewportY + 6 - scrollPanel.getScroll();
            for (Relationship relation : relationships) {
                drawRelationshipCard(relation, panelX + 22, y, panelW - 44, mouseX, mouseY);
                y += 76;
            }
        }
        scrollPanel.end();
        scrollPanel.drawScrollbar();
    }

    private void drawRelationshipCard(Relationship relation, int x, int y, int w, int mouseX, int mouseY) {
        KOMEGuiTheme.drawCard(x, y, w, 68, KOMEGuiTheme.isHovered(mouseX, mouseY, x, y, w, 68));
        fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj,
            relation.displayA + " - " + relation.displayB, w - 180), x + 12, y + 9, KOMEGuiTheme.COLOR_GOLD);
        String state = relation.pending ? "Pending Request" : relation.active ? "Active" : "Archived Entitlement";
        KOMEGuiTheme.drawStatusChip(fontRendererObj, state,
            relation.active ? KOMEGuiTheme.Status.ACTIVE : relation.pending ? KOMEGuiTheme.Status.WARNING : KOMEGuiTheme.Status.NEUTRAL,
            x + w - 158, y + 6, 146);
        if (relation.active) {
            fontRendererObj.drawString("Your progress: Stage " + relation.viewerStage + " - "
                + KOMEAlliance.stageName(relation.viewerStage), x + 12, y + 27, KOMEGuiTheme.COLOR_TEXT);
            fontRendererObj.drawString(relation.partnerName() + " progress: Stage " + relation.partnerStage
                + " - " + KOMEAlliance.stageName(relation.partnerStage), x + 12, y + 41, KOMEGuiTheme.COLOR_TEXT_MUTED);
            fontRendererObj.drawString("Shared relation: " + relation.sharedRelation,
                x + w - 174, y + 43, KOMEGuiTheme.COLOR_GOLD);
        } else {
            fontRendererObj.drawString(relation.pendingDescription(), x + 12, y + 31, KOMEGuiTheme.COLOR_TEXT);
        }
    }

    private void drawCreate(int mouseX, int mouseY) {
        int x = panelX + Math.max(24, panelW / 8);
        int w = panelW - Math.max(48, panelW / 4);
        int y = panelY + 68;
        KOMEGuiTheme.drawSubPanel(x, y, w, panelH - 132);
        KOMEGuiTheme.drawSectionTitle(fontRendererObj, "Formal alliance request", x + 18, y + 16, w - 36);
        KOMEGuiTheme.drawWrappedText(fontRendererObj,
            "One accepted request creates a mutual relationship at Stage 0. Each faction then advances its own four-stage ladder independently.",
            x + 18, y + 35, w - 36, KOMEGuiTheme.COLOR_TEXT);
        RequestOption option = currentOption();
        if (option == null) {
            KOMEGuiTheme.drawWarningBanner(fontRendererObj, "No eligible receivers",
                "Enemy and Mortal Enemy kingless factions are excluded. Existing relationships and factions that fail server authority checks are also omitted.",
                x + 18, y + 88, w - 36, KOMEGuiTheme.Status.WARNING);
            return;
        }
        KOMEGuiTheme.drawFactionBadge(fontRendererObj, option.key, option.name,
            panelX + panelW / 2 - 126, panelY + 126, 252);
        String receiver = option.hasKing ? "The receiving king must accept."
            : "Kingless auto-accept: Stage " + option.automaticStage + " - "
                + KOMEAlliance.stageName(option.automaticStage) + " in both directions.";
        KOMEGuiTheme.drawWarningBanner(fontRendererObj, option.hasKing ? "King-to-king request" : "Kingless faction",
            receiver, x + 18, y + 102, w - 36,
            option.hasKing ? KOMEGuiTheme.Status.NEUTRAL : KOMEGuiTheme.Status.WARNING);
    }

    private void drawDetail(int mouseX, int mouseY) {
        Relationship relation = selected();
        if (relation == null) return;
        int cardX = panelX + 18;
        int cardY = panelY + 43;
        int cardW = panelW - 36;
        KOMEGuiTheme.drawSubPanel(cardX, cardY, cardW, 54);
        KOMEGuiTheme.drawFactionBadge(fontRendererObj, relation.side, relation.sideName(), cardX + 10, cardY + 8,
            Math.max(100, (cardW - 42) / 2));
        KOMEGuiTheme.drawFactionBadge(fontRendererObj, relation.partner, relation.partnerName(),
            cardX + cardW / 2 + 11, cardY + 8, Math.max(100, (cardW - 42) / 2));
        String state = relation.pending ? relation.pendingDescription()
            : "Shared relation: " + relation.sharedRelation + " | Directional benefits stay independently unlocked";
        fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, state, cardW - 20),
            cardX + 10, cardY + 34, relation.pending ? KOMEGuiTheme.COLOR_WARN : KOMEGuiTheme.COLOR_TEXT_MUTED);

        int viewportY = panelY + 134;
        int viewportH = panelY + panelH - 48 - viewportY;
        contentHeight = detailContentHeight(relation);
        scrollPanel.layout(panelX + 14, viewportY, panelW - 28, viewportH, contentHeight);
        scrollPanel.begin(mc);
        int y = viewportY + 6 - scrollPanel.getScroll();
        if (relation.pending) drawPending(relation, panelX + 22, y, panelW - 44);
        else if (tab == 0) drawOverview(relation, panelX + 22, y, panelW - 44);
        else if (tab == 1) drawRequirements(relation, panelX + 22, y, panelW - 44);
        else if (tab == 2) drawBenefits(relation, panelX + 22, y, panelW - 44);
        else drawMilitary(relation, panelX + 22, y, panelW - 44);
        scrollPanel.end();
        scrollPanel.drawScrollbar();
    }

    private void drawPending(Relationship relation, int x, int y, int w) {
        KOMEGuiTheme.drawWarningBanner(fontRendererObj, "Pending formal request", relation.pendingDescription()
            + " Acceptance initializes both directional ladders at Stage 0 - Formal Neutrality.",
            x, y, w, KOMEGuiTheme.Status.WARNING);
    }

    private void drawOverview(Relationship relation, int x, int y, int w) {
        y = drawStageCard("Your Progress Toward " + relation.partnerName(), relation.viewerStage, x, y, w, true) + 8;
        y = drawStageCard(relation.partnerName() + "'s Progress Toward " + relation.sideName(),
            relation.partnerStage, x, y, w, false) + 8;
        if (!relation.sideHasKing || !relation.partnerHasKing) {
            KOMEGuiTheme.drawWarningBanner(fontRendererObj, "Kingless continuity",
                "Stages and benefits remain active without a grace timer. A future king inherits the exact directional state; a kingless faction cannot manually claim a new stage.",
                x, y, w, KOMEGuiTheme.Status.WARNING);
        }
    }

    private int drawStageCard(String title, int stage, int x, int y, int w, boolean primary) {
        KOMEGuiTheme.drawCard(x, y, w, 72, false);
        fontRendererObj.drawString(title, x + 12, y + 10, primary ? KOMEGuiTheme.COLOR_GOLD : KOMEGuiTheme.COLOR_TEXT_MUTED);
        fontRendererObj.drawString("Stage " + stage + " - " + KOMEAlliance.stageName(stage),
            x + 12, y + 28, KOMEGuiTheme.COLOR_TEXT);
        KOMEGuiTheme.drawProgressBar(fontRendererObj, x + 12, y + 46, w - 24, 14,
            Math.max(0, Math.min(4, stage)) / 4.0f, primary ? KOMEGuiTheme.COLOR_GOOD : KOMEGuiTheme.COLOR_GOLD_DARK,
            stage + " / 4");
        return y + 72;
    }

    private void drawRequirements(Relationship relation, int x, int y, int w) {
        if (relation.nextStage <= 0) {
            KOMEGuiTheme.drawWarningBanner(fontRendererObj, "Ladder complete",
                "Military Partnership is claimed. No further stage requirements remain.",
                x, y, w, KOMEGuiTheme.Status.COMPLETE);
            return;
        }
        KOMEGuiTheme.drawCard(x, y, w, 90, false);
        fontRendererObj.drawString("Next: Stage " + relation.nextStage + " - " + KOMEAlliance.stageName(relation.nextStage),
            x + 12, y + 10, KOMEGuiTheme.COLOR_GOLD);
        String quota = relation.quotaName.length() == 0 ? "Rolled quota: Not rolled"
            : "Rolled quota: " + relation.quotaName;
        fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, quota, w - 24),
            x + 12, y + 28, KOMEGuiTheme.COLOR_TEXT);
        KOMEGuiTheme.drawProgressBar(fontRendererObj, x + 12, y + 47, w - 24, 14,
            ratio(relation.quotaDelivered, relation.quotaRequired),
            relation.quotaDelivered >= relation.quotaRequired && relation.quotaName.length() > 0
                ? KOMEGuiTheme.COLOR_GOOD : KOMEGuiTheme.COLOR_WARN,
            relation.quotaDelivered + " / " + relation.quotaRequired);
        fontRendererObj.drawString("Goods are delivered through the authoritative alliance ledger.",
            x + 12, y + 68, KOMEGuiTheme.COLOR_TEXT_MUTED);
        y += 98;
        int fixedH = Math.max(78, 50 + KOMEGuiTheme.wrapText(fontRendererObj, relation.fixedDescription, w - 24).size() * 10);
        KOMEGuiTheme.drawCard(x, y, w, fixedH, false);
        fontRendererObj.drawString("Fixed milestone", x + 12, y + 10, KOMEGuiTheme.COLOR_BORDER_RED_LIGHT);
        int textY = KOMEGuiTheme.drawWrappedText(fontRendererObj, relation.fixedDescription,
            x + 12, y + 26, w - 24, KOMEGuiTheme.COLOR_TEXT);
        KOMEGuiTheme.drawProgressBar(fontRendererObj, x + 12, textY + 5, w - 24, 14,
            ratio(relation.fixedProgress, relation.fixedRequired),
            relation.fixedProgress >= relation.fixedRequired ? KOMEGuiTheme.COLOR_GOOD : KOMEGuiTheme.COLOR_WARN,
            fixedDisplay(relation));
    }

    private void drawBenefits(Relationship relation, int x, int y, int w) {
        String[] names = {"Cooperation", "Friends", "Allies", "Military Partnership"};
        String[] benefits = {
            "Hire the partner faction's farmer units.",
            "One persistent produce-merchant entitlement for this partner. The merchant economy is a future integration.",
            "Move your companies through tiles currently controlled by the partner faction.",
            "Explicit company delegation and restricted kingless wartime authority."
        };
        for (int stage = 1; stage <= 4; stage++) {
            boolean unlocked = relation.viewerStage >= stage;
            KOMEGuiTheme.drawCard(x, y, w, 55, false);
            KOMEGuiTheme.drawStatusChip(fontRendererObj, unlocked ? "Unlocked" : "Locked",
                unlocked ? KOMEGuiTheme.Status.COMPLETE : KOMEGuiTheme.Status.LOCKED,
                x + w - 88, y + 7, 76);
            fontRendererObj.drawString("Stage " + stage + " - " + names[stage - 1],
                x + 12, y + 10, unlocked ? KOMEGuiTheme.COLOR_GOOD : KOMEGuiTheme.COLOR_TEXT_DISABLED);
            KOMEGuiTheme.drawWrappedText(fontRendererObj, benefits[stage - 1], x + 12, y + 27,
                w - 112, unlocked ? KOMEGuiTheme.COLOR_TEXT : KOMEGuiTheme.COLOR_TEXT_DISABLED);
            y += 63;
        }
    }

    private void drawMilitary(Relationship relation, int x, int y, int w) {
        boolean stageFour = relation.viewerStage >= 4;
        y = KOMEGuiTheme.drawWarningBanner(fontRendererObj,
            stageFour ? "Stage 4 authority available" : "Stage 4 locked",
            stageFour
                ? "A receiving king may move, dispatch, continue, halt, stay, retreat, resume, or return explicitly delegated companies. Rename, split, merge, disband, permanent transfer, and source-population mutation remain owner-only."
                : "Claim Military Partnership before company delegation or kingless wartime authority becomes available.",
            x, y, w, stageFour ? KOMEGuiTheme.Status.ACTIVE : KOMEGuiTheme.Status.LOCKED) + 8;
        KOMEGuiTheme.drawCard(x, y, w, 78, false);
        fontRendererObj.drawString("Qualifying Stage 4 deployment", x + 12, y + 10, KOMEGuiTheme.COLOR_BORDER_RED_LIGHT);
        String deployment = relation.qualifyingWar.length() == 0 ? "Not recorded"
            : "War " + relation.qualifyingWar + " | Company " + relation.qualifyingCompany;
        fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, deployment, w - 24),
            x + 12, y + 28, relation.qualifyingWar.length() == 0 ? KOMEGuiTheme.COLOR_WARN : KOMEGuiTheme.COLOR_GOOD);
        KOMEGuiTheme.drawWrappedText(fontRendererObj,
            "The partner must be the defending faction; your faction must join after Stage 3 and deploy a nonempty owned company in a tile the partner currently controls.",
            x + 12, y + 45, w - 24, KOMEGuiTheme.COLOR_TEXT_MUTED);
    }

    private int detailContentHeight(Relationship relation) {
        if (relation == null || relation.pending) return 100;
        if (tab == 0) return (!relation.sideHasKing || !relation.partnerHasKing) ? 240 : 166;
        if (tab == 1) return relation.nextStage <= 0 ? 80 : 230;
        if (tab == 2) return 260;
        return 190;
    }

    private void drawDisabledTooltip(int mouseX, int mouseY) {
        for (Object value : buttonList) {
            GuiButton button = (GuiButton) value;
            String reason = disabledReasons.get(Integer.valueOf(button.id));
            if (!button.enabled && reason != null && reason.length() > 0
                    && KOMEGuiTheme.isHovered(mouseX, mouseY, button.xPosition, button.yPosition, button.width, button.height)) {
                List<String> lines = KOMEGuiTheme.wrapText(fontRendererObj, reason, Math.min(300, width - 32));
                KOMEGuiTheme.drawTooltip(fontRendererObj, lines, mouseX, mouseY, width, height);
                return;
            }
        }
    }

    private Relationship selected() {
        for (Relationship relation : relationships) if (relation.pair.equals(selectedPair)) return relation;
        return null;
    }

    private RequestOption currentOption() {
        return requestOptions.isEmpty() ? null : requestOptions.get(clamp(optionIndex, 0, requestOptions.size() - 1));
    }

    private int contentY() {
        return viewerAdmin ? panelY + 85 : panelY + 65;
    }

    public void setVisualTestCreateMode(boolean value) {
        mode = value ? 1 : 0;
    }

    public void setVisualTestDetail(String pair) {
        selectedPair = pair == null ? "" : pair;
        mode = 2;
    }

    private static float ratio(int value, int required) {
        return required <= 0 ? 0.0f : Math.max(0.0f, Math.min(1.0f, value / (float) required));
    }

    private static String fixedDisplay(Relationship relation) {
        if (relation.nextStage == 3) return halfHours(relation.fixedProgress) + " / " + halfHours(relation.fixedRequired) + " hours";
        return relation.fixedProgress + " / " + relation.fixedRequired;
    }

    private static String halfHours(int value) {
        return value / 2 + (value % 2 == 0 ? "" : ".5");
    }

    private static boolean flag(String value) { return "1".equals(value); }
    private static int number(String value) {
        try { return Integer.parseInt(value); } catch (NumberFormatException ignored) { return 0; }
    }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private static int wrap(int value, int size) { return size <= 0 ? 0 : (value % size + size) % size; }

    private static final class Action {
        final int id;
        final String label;
        final boolean enabled;
        final String reason;
        Action(int id, String label, boolean enabled, String reason) {
            this.id = id; this.label = label; this.enabled = enabled; this.reason = reason == null ? "" : reason;
        }
    }

    private static final class RequestOption {
        final String key;
        final String name;
        final boolean allowed;
        final boolean hasKing;
        final boolean automatic;
        final int automaticStage;
        RequestOption(String[] parts) {
            key = parts[1]; name = parts[2]; allowed = flag(parts[3]); hasKing = flag(parts[4]);
            automatic = flag(parts[5]); automaticStage = number(parts[6]);
        }
    }

    private static final class Relationship {
        final String pair;
        final String keyA;
        final String keyB;
        final String displayA;
        final String displayB;
        final String side;
        final String partner;
        final int viewerStage;
        final int partnerStage;
        final String status;
        final String requestedBy;
        final String pendingReceiver;
        final String sharedRelation;
        final boolean canManage;
        final boolean sideHasKing;
        final boolean partnerHasKing;
        final int nextStage;
        final String quotaName;
        final int quotaRequired;
        final int quotaDelivered;
        final int fixedProgress;
        final int fixedRequired;
        final String fixedDescription;
        final boolean merchantUnlocked;
        final String qualifyingWar;
        final String qualifyingCompany;
        final boolean active;
        final boolean pending;

        Relationship(String[] p) {
            pair = p[1]; keyA = p[2]; keyB = p[3]; displayA = p[4]; displayB = p[5];
            side = p[6]; partner = p[7]; viewerStage = number(p[8]); partnerStage = number(p[9]);
            status = p[10]; requestedBy = p[11]; pendingReceiver = p[12]; sharedRelation = p[13];
            canManage = flag(p[14]); sideHasKing = flag(p[15]); partnerHasKing = flag(p[16]);
            nextStage = number(p[17]); quotaName = p[18]; quotaRequired = number(p[19]);
            quotaDelivered = number(p[20]); fixedProgress = number(p[21]); fixedRequired = number(p[22]);
            fixedDescription = p[23]; merchantUnlocked = flag(p[24]); qualifyingWar = p[25];
            qualifyingCompany = p[26];
            active = "active".equalsIgnoreCase(status);
            pending = "pending".equalsIgnoreCase(status);
        }

        boolean hasRelationship() { return !"none".equalsIgnoreCase(status); }

        String sideName() { return side.equals(keyA) ? displayA : displayB; }
        String partnerName() { return partner.equals(keyA) ? displayA : displayB; }
        String pendingDescription() {
            String sender = requestedBy.equals(keyA) ? displayA : displayB;
            String receiver = pendingReceiver.equals(keyA) ? displayA : displayB;
            return sender + " requested; awaiting " + receiver + ".";
        }
    }
}
