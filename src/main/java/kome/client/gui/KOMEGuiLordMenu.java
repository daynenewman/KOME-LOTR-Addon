package kome.client.gui;

import kome.common.network.KOMEPacketHandler;
import kome.common.network.KOMEPacketLordAction;
import lotr.client.gui.LOTRGuiButtonRedBook;
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
        buttonMenuReturn = null;
        int center = width / 2;
        buttonList.add(new LOTRGuiButtonRedBook(0, center - 72, guiTop + 72, 144, 20, currentLord ? "Re-pledge to this lord" : "Pledge to this lord"));
        if (currentLord) {
            buttonList.add(new LOTRGuiButtonRedBook(1, center - 72, guiTop + 96, 144, 20, "Open offerings"));
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "Lord Menu", width / 2, guiTop + 18, 0xFFFFFF);
        drawCenteredString(fontRendererObj, trim(lordName, 190), width / 2, guiTop + 40, 0xFFE6A3);
        drawCenteredString(fontRendererObj, factionName == null || factionName.length() == 0 ? "No faction" : trim(factionName, 190), width / 2, guiTop + 52, 0xCCCCCC);
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
        }
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
}
