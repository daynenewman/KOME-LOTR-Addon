package kome.client.gui;

import kome.common.network.KOMEPacketHandler;
import kome.common.network.KOMEPacketLordAction;
import kome.client.KOMEEntityHighlightOverlay;
import lotr.client.gui.LOTRGuiMenuBase;
import net.minecraft.client.gui.GuiButton;

public class KOMEGuiLordMenu extends LOTRGuiMenuBase {
    private final int entityId;
    private final String lordName;
    private final String factionName;
    private final boolean currentLord;

    public KOMEGuiLordMenu(int entityId, String lordName, String factionName, boolean currentLord) {
        this.entityId = entityId;
        this.lordName = lordName == null || lordName.length() == 0 ? "This lord" : lordName;
        this.factionName = factionName == null ? "" : factionName;
        this.currentLord = currentLord;
    }

    @Override
    public void initGui() {
        xSize = 220;
        ySize = 140;
        super.initGui();
        buttonList.clear();
        buttonMenuReturn = null;
        int center = width / 2;
        if (currentLord) {
            buttonList.add(KOMEGuiButton.wide(1, center - 72, guiTop + 82, "Open offerings"));
            buttonList.add(KOMEGuiButton.wide(2, center - 72, guiTop + 106, "Highlight lord"));
        } else {
            buttonList.add(KOMEGuiButton.wide(0, center - 72, guiTop + 94, "Pledge to this lord"));
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        KOMEGuiTheme.drawMainPanel(guiLeft, guiTop, xSize, ySize);
        KOMEGuiTheme.drawHeader(fontRendererObj, "Lord Menu", guiLeft + 10, guiTop + 10, xSize - 20);

        int cardX = guiLeft + 18;
        int cardY = guiTop + 42;
        int cardW = xSize - 36;
        KOMEGuiTheme.drawCard(cardX, cardY, cardW, 31, KOMEGuiTheme.isHovered(mouseX, mouseY, cardX, cardY, cardW, 31));
        drawCenteredString(fontRendererObj, KOMEGuiTheme.trimToWidth(fontRendererObj, lordName, cardW - 12), width / 2, cardY + 7, KOMEGuiTheme.COLOR_BORDER_RED);
        String faction = factionName == null || factionName.length() == 0 ? "No faction" : factionName;
        drawCenteredString(fontRendererObj, KOMEGuiTheme.trimToWidth(fontRendererObj, faction, cardW - 12), width / 2, cardY + 18, KOMEGuiTheme.COLOR_TEXT_MUTED);

        String status = currentLord ? "Current pledged lord" : "Available for pledge";
        drawCenteredString(fontRendererObj, status, width / 2, guiTop + 126, KOMEGuiTheme.COLOR_TEXT_MUTED);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public void actionPerformed(GuiButton button) {
        if (!button.enabled) {
            return;
        }
        if (button.id == 0) {
            KOMEPacketHandler.network.sendToServer(new KOMEPacketLordAction(entityId, KOMEPacketLordAction.PLEDGE));
            mc.displayGuiScreen(null);
        } else if (button.id == 1) {
            KOMEPacketHandler.network.sendToServer(new KOMEPacketLordAction(entityId, KOMEPacketLordAction.OFFERINGS));
            mc.displayGuiScreen(null);
        } else if (button.id == 2) {
            KOMEEntityHighlightOverlay.highlight(entityId, lordName);
            mc.displayGuiScreen(null);
        }
    }

}
