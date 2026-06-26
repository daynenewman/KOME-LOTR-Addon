package kome.client.gui;

import kome.client.KOMEMinecraftClient;
import kome.client.KOMEQuotaLedgerOverlay;
import kome.common.gui.KOMEContainerAllianceLedger;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.IInventory;

import java.util.ArrayList;
import java.util.List;

public class KOMEGuiAllianceLedger extends GuiContainer {
    private static final int ID_BACK = 1;
    private static final int ID_CLAIM = 2;
    private static final int MARGIN = 18;
    private static final int GAP = 14;
    private static final int CARD_PADDING = 10;
    private static final int TITLE_Y = 12;
    private static final int SUMMARY_Y = 48;
    private static final int SUMMARY_HEIGHT = 70;
    private static final int PROGRESS_Y = 128;
    private static final int PROGRESS_HEIGHT = 66;
    private static final int QUOTA_Y = 204;
    private static final int QUOTA_HEIGHT = 48;
    private static final int DEPOSIT_Y = 260;
    private static final int DEPOSIT_HEIGHT = 52;
    private static final int INVENTORY_LABEL_Y = 322;
    private static final int BOTTOM_BUTTON_Y = 432;
    private static final int BOTTOM_BUTTON_HEIGHT = 22;

    public KOMEGuiAllianceLedger(IInventory playerInventory, IInventory ledgerInventory) {
        super(new KOMEContainerAllianceLedger(playerInventory, ledgerInventory));
        xSize = KOMEContainerAllianceLedger.GUI_WIDTH;
        ySize = KOMEContainerAllianceLedger.GUI_HEIGHT;
    }

    @Override
    public void initGui() {
        super.initGui();
        buttonList.clear();
        buttonList.add(new KOMEGuiButton(ID_BACK, guiLeft + MARGIN, guiTop + BOTTOM_BUTTON_Y, 92, BOTTOM_BUTTON_HEIGHT, "Back"));
        GuiButton claim = new KOMEGuiButton(ID_CLAIM, guiLeft + xSize - MARGIN - 132, guiTop + BOTTOM_BUTTON_Y, 132, BOTTOM_BUTTON_HEIGHT, "Claim Goods", true);
        claim.enabled = KOMEQuotaLedgerOverlay.canClaimGoods();
        buttonList.add(claim);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (!button.enabled) {
            return;
        }
        if (button.id == ID_BACK) {
            mc.displayGuiScreen(new KOMEGuiAlliance());
        } else if (button.id == ID_CLAIM) {
            String sender = KOMEQuotaLedgerOverlay.getSenderKey();
            String receiver = KOMEQuotaLedgerOverlay.getReceiverKey();
            if (sender.length() > 0 && receiver.length() > 0) {
                KOMEMinecraftClient.sendChat("/alliance claimGoods " + sender + " " + receiver);
                mc.displayGuiScreen(new KOMEGuiAlliance());
            }
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
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
        fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, KOMEQuotaLedgerOverlay.getRelationSummary(), w - CARD_PADDING * 2), x + CARD_PADDING, y + 9, KOMEGuiTheme.COLOR_BORDER_RED);
        int lineY = y + 28;
        String viewer = KOMEQuotaLedgerOverlay.getViewerName();
        if (viewer.length() > 0) {
            fontRendererObj.drawString("Your Faction: " + KOMEGuiTheme.trimToWidth(fontRendererObj, viewer, w - CARD_PADDING * 2), x + CARD_PADDING, lineY, KOMEGuiTheme.COLOR_TEXT);
        }
        int permissionsY = y + 50;
        fontRendererObj.drawString("Can Deposit: " + yesNo(KOMEQuotaLedgerOverlay.canDepositGoods()), x + CARD_PADDING, permissionsY, KOMEQuotaLedgerOverlay.canDepositGoods() ? KOMEGuiTheme.COLOR_GOOD : KOMEGuiTheme.COLOR_TEXT_MUTED);
        String claim = "Can Claim: " + yesNo(KOMEQuotaLedgerOverlay.canClaimGoods());
        fontRendererObj.drawString(claim, x + w - CARD_PADDING - fontRendererObj.getStringWidth(claim), permissionsY, KOMEQuotaLedgerOverlay.canClaimGoods() ? KOMEGuiTheme.COLOR_GOOD : KOMEGuiTheme.COLOR_TEXT_MUTED);
    }

    private void drawProgressCards() {
        List progress = visibleProgressLines();
        int x = guiLeft + MARGIN;
        int y = guiTop + PROGRESS_Y;
        if (progress.isEmpty()) {
            int cardW = xSize - MARGIN * 2;
            KOMEGuiTheme.drawCard(x, y, cardW, PROGRESS_HEIGHT, false);
            fontRendererObj.drawString("Alliance Types", x + CARD_PADDING, y + 8, KOMEGuiTheme.COLOR_BORDER_RED);
            KOMEGuiTheme.drawWrappedText(fontRendererObj, "No alliance types are active or pending for this relationship.", x + CARD_PADDING, y + 26, cardW - CARD_PADDING * 2, KOMEGuiTheme.COLOR_TEXT_MUTED);
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
            fontRendererObj.drawString(status, cardX + CARD_PADDING, y + 22, KOMEGuiTheme.COLOR_TEXT);
            List wrapped = fontRendererObj.listFormattedStringToWidth(requirement, cardW - CARD_PADDING * 2);
            for (int line = 0; line < wrapped.size() && line < 2; line++) {
                fontRendererObj.drawString(String.valueOf(wrapped.get(line)), cardX + CARD_PADDING, y + 37 + line * 10, KOMEGuiTheme.COLOR_TEXT_MUTED);
            }
            if (progressText.length() > 0) {
                fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, progressText, cardW - CARD_PADDING * 2), cardX + CARD_PADDING, y + 56, KOMEGuiTheme.COLOR_TEXT);
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
            fontRendererObj.drawString("No current food quotas for this alliance.", x + CARD_PADDING, y + 26, KOMEGuiTheme.COLOR_TEXT_MUTED);
            return;
        }
        for (int i = 0; i < quotaLines.size() && i < 2; i++) {
            fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, String.valueOf(quotaLines.get(i)), w - CARD_PADDING * 2), x + CARD_PADDING, y + 22 + i * 13, KOMEGuiTheme.COLOR_TEXT);
        }
    }

    private void drawDepositSection() {
        int x = guiLeft + MARGIN;
        int y = guiTop + DEPOSIT_Y;
        int w = xSize - MARGIN * 2;
        KOMEGuiTheme.drawSubPanel(x, y, w, DEPOSIT_HEIGHT);
        fontRendererObj.drawString("Deposit Goods", x + CARD_PADDING, y + 8, KOMEGuiTheme.COLOR_BORDER_RED);
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
        int textWidth = xSize - 260;
        KOMEGuiTheme.drawWrappedText(fontRendererObj, text, guiLeft + 128, guiTop + BOTTOM_BUTTON_Y + 6, textWidth, KOMEGuiTheme.COLOR_TEXT_MUTED);
    }

    private void drawSlotRow(int x, int y, int count) {
        for (int i = 0; i < count; i++) {
            KOMEGuiTheme.drawIconSlot(guiLeft + x + i * 18 - 1, guiTop + y - 1, 18, false);
        }
    }

    private String yesNo(boolean value) {
        return value ? "Yes" : "No";
    }

    private String ledgerRequirement(String type, String requirement, String[] quota) {
        if (requirement == null || requirement.length() == 0) {
            return "";
        }
        String quotaItem = KOMEQuotaLedgerOverlay.part(quota, 2);
        if ("Military".equals(type) && quotaItem.length() > 0 && requirement.startsWith("Collect ")) {
            return "Food quota rolled";
        }
        if ("Trade".equals(type) && quotaItem.length() > 0 && requirement.indexOf("5000 coins") >= 0) {
            return "Deliver coins + food quota";
        }
        if (requirement.indexOf("1000 coins") >= 0) {
            return "Deliver 1000 Coins";
        }
        if (requirement.indexOf("5000 coins") >= 0) {
            return "Deliver 5000 Coins + Roll Trade Quota";
        }
        if (requirement.indexOf("food quota") >= 0) {
            return type.equals("Trade") ? "Roll trade quota" : "Roll food quota";
        }
        if (requirement.startsWith("Collect ")) {
            return "Quota: " + requirement.substring("Collect ".length());
        }
        if (requirement.indexOf("10000 coins") >= 0) {
            return "Deliver 10000 Coins";
        }
        if (requirement.indexOf("2000 enemies") >= 0) {
            return "Kill 2000 enemies";
        }
        if (requirement.indexOf("Receiving faction") >= 0) {
            return "Awaiting acceptance";
        }
        if (requirement.indexOf("No active") >= 0) {
            return "No active alliance";
        }
        return requirement;
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
        String quotaProgress = "Food: " + KOMEQuotaLedgerOverlay.part(quota, 3) + "/" + KOMEQuotaLedgerOverlay.part(quota, 4) + " " + KOMEQuotaLedgerOverlay.part(quota, 5);
        if ("Trade".equals(type) && main.length() > 0) {
            return main.replace("Coins delivered", "Coins") + " | " + quotaProgress;
        }
        return quotaProgress;
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
        if (hasVisibleType("Military")) {
            lines.add(quotaLine("Military", "Military"));
        }
        if (hasVisibleType("Trade")) {
            lines.add(quotaLine("Trade", "Trade"));
        }
        return lines;
    }

    private String depositHelperText() {
        List progress = visibleProgressLines();
        if (progress.size() == 1) {
            String[] parts = (String[]) progress.get(0);
            String type = KOMEQuotaLedgerOverlay.part(parts, 1);
            return "Place required " + type + " goods into the ledger slots.";
        }
        return "Place required goods for this alliance into the ledger slots.";
    }
}
