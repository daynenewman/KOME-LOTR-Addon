package kome.client.gui;

import kome.client.KOMEMinecraftClient;
import kome.common.network.KOMEPacketAllianceAction;
import kome.common.network.KOMEPacketHandler;
import kome.client.KOMEQuotaLedgerOverlay;
import kome.common.gui.KOMEContainerAllianceLedger;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.IInventory;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

public class KOMEGuiAllianceLedger extends GuiContainer {
    private static final int ID_BACK = 1;
    private static final int ID_CLAIM = 2;
    private static final int ID_SWITCH = 3;
    private static final int MARGIN = 18;
    private static final int GAP = 14;
    private static final int CARD_PADDING = 10;
    private static final int TITLE_Y = 12;
    private static final int SUMMARY_Y = 48;
    private static final int SUMMARY_HEIGHT = 70;
    private static final int PROGRESS_Y = 128;
    private static final int PROGRESS_HEIGHT = 66;
    private static final int QUOTA_Y = 204;
    private static final int QUOTA_HEIGHT = 56;
    private static final int DEPOSIT_Y = 260;
    private static final int DEPOSIT_HEIGHT = 52;
    private static final int INVENTORY_LABEL_Y = 322;
    private static final int BOTTOM_BUTTON_Y = 432;
    private static final int BOTTOM_BUTTON_HEIGHT = 22;
    private float renderScale = 1.0F;
    private boolean drawingScaled;

    public KOMEGuiAllianceLedger(IInventory playerInventory, IInventory ledgerInventory) {
        super(new KOMEContainerAllianceLedger(playerInventory, ledgerInventory));
        xSize = KOMEContainerAllianceLedger.GUI_WIDTH;
        ySize = KOMEContainerAllianceLedger.GUI_HEIGHT;
    }

    @Override
    public void initGui() {
        if (Boolean.getBoolean("kome.guiCapture") && mc.thePlayer == null) {
            guiLeft = (width - xSize) / 2;
            guiTop = (height - ySize) / 2;
        } else {
            super.initGui();
        }
        renderScale = Math.min(1.0F, Math.min((width - 8) / (float) xSize, (height - 8) / (float) ySize));
        renderScale = Math.max(0.35F, renderScale);
        guiLeft = Math.round((width / renderScale - xSize) / 2.0F);
        guiTop = Math.round((height / renderScale - ySize) / 2.0F);
        buttonList.clear();
        buttonList.add(new KOMEGuiButton(ID_BACK, guiLeft + MARGIN, guiTop + BOTTOM_BUTTON_Y, 92, BOTTOM_BUTTON_HEIGHT, "Back"));
        GuiButton switchLedger = new KOMEGuiButton(ID_SWITCH, guiLeft + MARGIN + 100, guiTop + BOTTOM_BUTTON_Y, 132, BOTTOM_BUTTON_HEIGHT, KOMEQuotaLedgerOverlay.getSwitchLabel());
        switchLedger.enabled = KOMEQuotaLedgerOverlay.canSwitchLedger();
        buttonList.add(switchLedger);
        GuiButton claim = new KOMEGuiButton(ID_CLAIM, guiLeft + xSize - MARGIN - 132, guiTop + BOTTOM_BUTTON_Y, 132, BOTTOM_BUTTON_HEIGHT, "Claim Goods", true);
        claim.enabled = KOMEQuotaLedgerOverlay.canClaimGoods();
        buttonList.add(claim);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawDefaultBackground();
        drawingScaled = true;
        GL11.glPushMatrix();
        GL11.glScalef(renderScale, renderScale, 1.0F);
        super.drawScreen(Math.round(mouseX / renderScale), Math.round(mouseY / renderScale), partialTicks);
        GL11.glPopMatrix();
        drawingScaled = false;
    }

    @Override
    public void drawDefaultBackground() {
        if (!drawingScaled) super.drawDefaultBackground();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(Math.round(mouseX / renderScale), Math.round(mouseY / renderScale), mouseButton);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int mouseButton, long heldTime) {
        super.mouseClickMove(Math.round(mouseX / renderScale), Math.round(mouseY / renderScale), mouseButton, heldTime);
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int state) {
        super.mouseMovedOrUp(Math.round(mouseX / renderScale), Math.round(mouseY / renderScale), state);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (!button.enabled) {
            return;
        }
        if (button.id == ID_BACK) {
            mc.displayGuiScreen(new KOMEGuiAllianceUnified());
        } else if (button.id == ID_SWITCH) {
            String sender = KOMEQuotaLedgerOverlay.getSwitchSenderKey();
            String receiver = KOMEQuotaLedgerOverlay.getSwitchReceiverKey();
            if (sender.length() > 0 && receiver.length() > 0) {
                KOMEQuotaLedgerOverlay.reset();
                mc.thePlayer.closeScreen();
                KOMEPacketHandler.network.sendToServer(new KOMEPacketAllianceAction("ledger", "", sender, receiver));
            }
        } else if (button.id == ID_CLAIM) {
            String sender = KOMEQuotaLedgerOverlay.getSenderKey();
            String receiver = KOMEQuotaLedgerOverlay.getReceiverKey();
            if (sender.length() > 0 && receiver.length() > 0) {
                KOMEPacketHandler.network.sendToServer(new KOMEPacketAllianceAction("claim", "", sender, receiver));
            }
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        refreshActionButtons();
        KOMEGuiTheme.drawMainPanel(guiLeft, guiTop, xSize, ySize);
        KOMEGuiTheme.drawHeader(fontRendererObj, "Alliance Goods Ledger", guiLeft + 170, guiTop + TITLE_Y, xSize - 340);
        drawSummaryCard();
        drawProgressCards();
        drawQuotaSection();
        drawDepositSection();
        drawPlayerInventorySection();
        drawClaimStatus();
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
    }

    private void drawSummaryCard() {
        int x = guiLeft + MARGIN;
        int y = guiTop + SUMMARY_Y;
        int w = xSize - MARGIN * 2;
        KOMEGuiTheme.drawCard(x, y, w, SUMMARY_HEIGHT, false);
        String relation = KOMEQuotaLedgerOverlay.getRelationSummary();
        String[] pair = relation.split(" -> ", 2);
        int badgeW = Math.min(190, (w - 64) / 2);
        if (pair.length == 2) {
            KOMEGuiTheme.drawFactionBadge(fontRendererObj, KOMEQuotaLedgerOverlay.getSenderKey(), pair[0],
                x + CARD_PADDING, y + 6, badgeW);
            KOMEGuiTheme.drawCenteredPlainText(fontRendererObj, "->", x + w / 2, y + 11, KOMEGuiTheme.COLOR_GOLD);
            KOMEGuiTheme.drawFactionBadge(fontRendererObj, KOMEQuotaLedgerOverlay.getReceiverKey(), pair[1],
                x + w - CARD_PADDING - badgeW, y + 6, badgeW);
        } else {
            fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, relation, w - CARD_PADDING * 2), x + CARD_PADDING, y + 9, KOMEGuiTheme.COLOR_GOLD);
        }
        int lineY = y + 28;
        String viewer = KOMEQuotaLedgerOverlay.getViewerName();
        if (viewer.length() > 0) {
            fontRendererObj.drawString("Your Faction: " + KOMEGuiTheme.trimToWidth(fontRendererObj, viewer, w - CARD_PADDING * 2), x + CARD_PADDING, lineY, KOMEGuiTheme.COLOR_TEXT);
        }
        int permissionsY = y + 50;
        KOMEGuiTheme.drawStatusChip(fontRendererObj, "Deposit " + yesNo(KOMEQuotaLedgerOverlay.canDepositGoods()),
            x + CARD_PADDING, permissionsY - 3, KOMEQuotaLedgerOverlay.canDepositGoods() ? KOMEGuiTheme.Status.ACTIVE : KOMEGuiTheme.Status.LOCKED);
        String claim = "Claim " + yesNo(KOMEQuotaLedgerOverlay.canClaimGoods());
        int claimW = KOMEGuiTheme.statusChipWidth(fontRendererObj, claim);
        KOMEGuiTheme.drawStatusChip(fontRendererObj, claim, x + w - CARD_PADDING - claimW, permissionsY - 3,
            KOMEQuotaLedgerOverlay.canClaimGoods() ? KOMEGuiTheme.Status.ACTIVE : KOMEGuiTheme.Status.LOCKED);
    }

    private void drawProgressCards() {
        List progress = visibleProgressLines();
        int x = guiLeft + MARGIN;
        int y = guiTop + PROGRESS_Y;
        if (progress.isEmpty()) {
            int cardW = xSize - MARGIN * 2;
            KOMEGuiTheme.drawCard(x, y, cardW, PROGRESS_HEIGHT, false);
            fontRendererObj.drawString("Directional Stage", x + CARD_PADDING, y + 8, KOMEGuiTheme.COLOR_BORDER_RED);
            KOMEGuiTheme.drawWrappedText(fontRendererObj, "No active next-stage ledger exists for this relationship.", x + CARD_PADDING, y + 26, cardW - CARD_PADDING * 2, KOMEGuiTheme.COLOR_TEXT_MUTED);
            return;
        }
        int count = progress.size();
        int cardW = (xSize - MARGIN * 2 - GAP * (count - 1)) / count;
        for (int i = 0; i < count; i++) {
            String[] parts = (String[]) progress.get(i);
            int cardX = x + i * (cardW + GAP);
            KOMEGuiTheme.drawCard(cardX, y, cardW, PROGRESS_HEIGHT, false);
            String type = KOMEQuotaLedgerOverlay.part(parts, 1);
            String status = KOMEQuotaLedgerOverlay.part(parts, 2);
            String[] quota = KOMEQuotaLedgerOverlay.getStructuredLine("QUOTA", type);
            String requirement = ledgerRequirement(type, KOMEQuotaLedgerOverlay.part(parts, 3), quota);
            String progressText = cardProgress(type, parts);
            fontRendererObj.drawString(type, cardX + CARD_PADDING, y + 7, KOMEGuiTheme.COLOR_BORDER_RED);
            KOMEGuiTheme.drawStatusChip(fontRendererObj, status, cardX + cardW - CARD_PADDING - KOMEGuiTheme.statusChipWidth(fontRendererObj, status),
                y + 5, ledgerStatus(status));
            fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, requirement, cardW - CARD_PADDING * 2), cardX + CARD_PADDING, y + 27, KOMEGuiTheme.COLOR_TEXT_MUTED);
            if (progressText.length() > 0) {
                int delivered = parseInt(KOMEQuotaLedgerOverlay.part(parts, 5));
                int required = parseInt(KOMEQuotaLedgerOverlay.part(parts, 6));
                KOMEGuiTheme.drawProgressBar(fontRendererObj, cardX + CARD_PADDING, y + 44,
                    cardW - CARD_PADDING * 2, 11, required <= 0 ? 0F : delivered / (float) required,
                    required > 0 && delivered >= required ? KOMEGuiTheme.COLOR_GOOD : KOMEGuiTheme.COLOR_GOLD,
                    KOMEGuiTheme.trimToWidth(fontRendererObj, progressText, cardW - CARD_PADDING * 2 - 8));
            }
        }
    }

    private void drawQuotaSection() {
        int x = guiLeft + MARGIN;
        int y = guiTop + QUOTA_Y;
        int w = xSize - MARGIN * 2;
        KOMEGuiTheme.drawSubPanel(x, y, w, QUOTA_HEIGHT);
        fontRendererObj.drawString("Current Quotas", x + CARD_PADDING, y + 7, KOMEGuiTheme.COLOR_BORDER_RED);
        List quotaLines = visibleQuotaLines();
        if (quotaLines.isEmpty()) {
            fontRendererObj.drawString("No current rolled item quotas for this alliance.", x + CARD_PADDING, y + 26, KOMEGuiTheme.COLOR_TEXT_MUTED);
            return;
        }
        for (int i = 0; i < quotaLines.size() && i < 3; i++) {
            fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, String.valueOf(quotaLines.get(i)), w - CARD_PADDING * 2), x + CARD_PADDING, y + 21 + i * 12, KOMEGuiTheme.COLOR_TEXT);
        }
    }

    private void drawDepositSection() {
        int x = guiLeft + MARGIN;
        int y = guiTop + DEPOSIT_Y;
        int w = xSize - MARGIN * 2;
        KOMEGuiTheme.drawSubPanel(x, y, w, DEPOSIT_HEIGHT);
        fontRendererObj.drawString(KOMEQuotaLedgerOverlay.canDepositGoods() ? "Deposit Goods" : "Ledger Storage (Read Only)", x + CARD_PADDING, y + 8, KOMEGuiTheme.COLOR_BORDER_RED);
        KOMEGuiTheme.drawWrappedText(fontRendererObj, depositHelperText(), x + CARD_PADDING, y + 22, KOMEContainerAllianceLedger.DEPOSIT_X - MARGIN - CARD_PADDING * 2, KOMEGuiTheme.COLOR_TEXT_MUTED);
        drawSlotRow(KOMEContainerAllianceLedger.DEPOSIT_X, KOMEContainerAllianceLedger.DEPOSIT_Y, 9);
    }

    private void drawPlayerInventorySection() {
        int x = guiLeft + KOMEContainerAllianceLedger.INVENTORY_X;
        int y = guiTop + INVENTORY_LABEL_Y;
        fontRendererObj.drawString("Inventory", x, y, KOMEGuiTheme.COLOR_BORDER_RED);
        for (int row = 0; row < 3; row++) {
            drawSlotRow(KOMEContainerAllianceLedger.INVENTORY_X, KOMEContainerAllianceLedger.INVENTORY_Y + row * 18, 9);
        }
        drawSlotRow(KOMEContainerAllianceLedger.INVENTORY_X, KOMEContainerAllianceLedger.HOTBAR_Y, 9);
    }

    private void drawClaimStatus() {
        String text = KOMEQuotaLedgerOverlay.getClaimText();
        int textX = guiLeft + MARGIN + 240;
        int textWidth = xSize - MARGIN * 2 - 240 - 140;
        KOMEGuiTheme.drawWrappedText(fontRendererObj, text, textX, guiTop + BOTTOM_BUTTON_Y + 2, textWidth, KOMEGuiTheme.COLOR_TEXT_MUTED);
    }

    private void drawSlotRow(int x, int y, int count) {
        for (int i = 0; i < count; i++) {
            KOMEGuiTheme.drawIconSlot(guiLeft + x + i * 18 - 1, guiTop + y - 1, 18, false);
        }
    }

    private String yesNo(boolean value) {
        return value ? "Yes" : "No";
    }

    private KOMEGuiTheme.Status ledgerStatus(String status) {
        String normalized = status == null ? "" : status.toLowerCase();
        if (normalized.indexOf("complete") >= 0 || normalized.indexOf("active") >= 0) return KOMEGuiTheme.Status.ACTIVE;
        if (normalized.indexOf("pending") >= 0) return KOMEGuiTheme.Status.WARNING;
        if (normalized.indexOf("suspend") >= 0 || normalized.indexOf("denied") >= 0) return KOMEGuiTheme.Status.DENIED;
        return KOMEGuiTheme.Status.LOCKED;
    }

    private int parseInt(String value) {
        try { return Math.max(0, Integer.parseInt(value)); }
        catch (Exception ignored) { return 0; }
    }

    private String ledgerRequirement(String type, String requirement, String[] quota) {
        return requirement == null ? "" : requirement;
    }

    private String ledgerProgress(String type, String[] parts, String[] quota) {
        String delivered = KOMEQuotaLedgerOverlay.part(parts, 5);
        String required = KOMEQuotaLedgerOverlay.part(parts, 6);
        String label = KOMEQuotaLedgerOverlay.part(parts, 4);
        String main = numericProgress(label, delivered, required);
        String quotaItem = KOMEQuotaLedgerOverlay.part(quota, 2);
        if (quotaItem.length() <= 0) {
            return main;
        }
        return "Items: " + KOMEQuotaLedgerOverlay.part(quota, 3) + "/" + KOMEQuotaLedgerOverlay.part(quota, 4) + " " + KOMEQuotaLedgerOverlay.part(quota, 5);
    }

    private String cardProgress(String type, String[] parts) {
        String label = KOMEQuotaLedgerOverlay.part(parts, 4);
        if ("Trade".equals(type) && "Coins delivered".equals(label)) {
            label = "Coins";
        }
        if ("Military".equals(type) && "Food delivered".equals(label)) {
            label = "Food";
        }
        return numericProgress(label, KOMEQuotaLedgerOverlay.part(parts, 5), KOMEQuotaLedgerOverlay.part(parts, 6));
    }

    private String quotaLine(String label, String type) {
        String[] quota = KOMEQuotaLedgerOverlay.getStructuredLine("QUOTA", type);
        String item = KOMEQuotaLedgerOverlay.part(quota, 2);
        if (item.length() == 0) {
            return label + ": Not rolled";
        }
        return label + ": " + KOMEQuotaLedgerOverlay.part(quota, 3) + "/" + KOMEQuotaLedgerOverlay.part(quota, 4) + " "
            + KOMEQuotaLedgerOverlay.part(quota, 5) + " of " + item;
    }

    private String numericProgress(String label, String delivered, String required) {
        if (required.length() == 0 || "0".equals(required)) {
            return label == null ? "" : label;
        }
        String prefix = label == null || label.length() == 0 ? "Progress" : label;
        return prefix + ": " + delivered + "/" + required;
    }

    private List visibleProgressLines() {
        List source = KOMEQuotaLedgerOverlay.getStructuredLines("PROGRESS");
        List visible = new ArrayList();
        for (int i = 0; i < source.size(); i++) {
            String[] parts = (String[]) source.get(i);
            if (isVisibleProgress(parts)) {
                visible.add(parts);
            }
        }
        return visible;
    }

    private boolean isVisibleProgress(String[] parts) {
        String type = KOMEQuotaLedgerOverlay.part(parts, 1);
        String status = KOMEQuotaLedgerOverlay.part(parts, 2);
        return type.length() > 0 && status.length() > 0 && !"None".equalsIgnoreCase(status);
    }

    private boolean hasVisibleType(String type) {
        List progress = visibleProgressLines();
        for (int i = 0; i < progress.size(); i++) {
            String[] parts = (String[]) progress.get(i);
            if (type.equals(KOMEQuotaLedgerOverlay.part(parts, 1))) {
                return true;
            }
        }
        return false;
    }

    private List visibleQuotaLines() {
        List lines = new ArrayList();
        List progress = visibleProgressLines();
        for (Object value : progress) {
            String type = KOMEQuotaLedgerOverlay.part((String[]) value, 1);
            if (type.length() > 0) lines.add(quotaLine(type, type));
        }
        return lines;
    }

    private String depositHelperText() {
        if (!KOMEQuotaLedgerOverlay.canDepositGoods()) {
            return "This is the allied faction's contribution ledger. Switch back to your side to deposit.";
        }
        if (!hasRolledQuota()) {
            return "No quota is rolled for this side. Return to Alliance Detail and use Roll Requirement first.";
        }
        List progress = visibleProgressLines();
        if (progress.size() == 1) {
            String[] parts = (String[]) progress.get(0);
            String type = KOMEQuotaLedgerOverlay.part(parts, 1);
            return "Place required " + type + " goods into the ledger slots.";
        }
        return "Place required goods for this alliance into the ledger slots.";
    }

    private boolean hasRolledQuota() {
        List quotas = KOMEQuotaLedgerOverlay.getStructuredLines("QUOTA");
        for (Object value : quotas) {
            if (KOMEQuotaLedgerOverlay.part((String[]) value, 2).length() > 0) return true;
        }
        return false;
    }

    private void refreshActionButtons() {
        for (Object object : buttonList) {
            GuiButton button = (GuiButton) object;
            if (button.id == ID_CLAIM) {
                button.enabled = KOMEQuotaLedgerOverlay.canClaimGoods();
            } else if (button.id == ID_SWITCH) {
                button.enabled = KOMEQuotaLedgerOverlay.canSwitchLedger();
                button.displayString = KOMEQuotaLedgerOverlay.getSwitchLabel();
            }
        }
    }
}
