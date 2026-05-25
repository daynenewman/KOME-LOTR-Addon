package kome.client.gui;

import kome.client.KOMEMinecraftClient;

import lotr.client.gui.LOTRGuiMap;
import lotr.common.LOTRLevelData;
import lotr.common.fac.LOTRFaction;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

public class KOMEGuiConquestCapture extends GuiScreen {
    private final String tileId;
    private final String ownerFaction;
    private final String ruler;

    public KOMEGuiConquestCapture(String tileId, String ownerFaction, String ruler) {
        this.tileId = tileId;
        this.ownerFaction = ownerFaction == null ? "" : ownerFaction;
        this.ruler = ruler == null ? "" : ruler;
    }

    @Override
    public void initGui() {
        int x = width / 2 - 90;
        int y = height / 2 + 28;
        buttonList.add(new GuiButton(0, x, y, 85, 20, "Claim"));
        buttonList.add(new GuiButton(1, x + 95, y, 85, 20, "Cancel"));
        ((GuiButton) buttonList.get(0)).enabled = getPledgeFaction() != null;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            LOTRFaction pledge = getPledgeFaction();
            if (pledge != null) {
                KOMEMinecraftClient.sendChat("/conquest claim " + tileId + " " + pledge.codeName() + " " + KOMEMinecraftClient.playerName());
            }
            mc.displayGuiScreen(new LOTRGuiMap());
        } else if (button.id == 1) {
            mc.displayGuiScreen(new LOTRGuiMap());
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int x = width / 2 - 120;
        int y = height / 2 - 70;
        drawCenteredString(fontRendererObj, "Conquest Tile " + tileId, width / 2, y, 0xFFFFFF);
        drawCenteredString(fontRendererObj, "Current owner: " + valueOrUnclaimed(ownerFaction), width / 2, y + 28, 0xD8D8D8);
        drawCenteredString(fontRendererObj, "Ruler: " + valueOrNone(ruler), width / 2, y + 42, 0xD8D8D8);
        LOTRFaction pledge = getPledgeFaction();
        drawCenteredString(fontRendererObj, "Your faction: " + (pledge == null ? "none" : pledge.factionName()), width / 2, y + 62, pledge == null ? 0xFF7777 : 0xAAFFAA);
        if (pledge == null) {
            drawCenteredString(fontRendererObj, "You must pledge to a faction before claiming.", width / 2, y + 86, 0xFF7777);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private LOTRFaction getPledgeFaction() {
        return KOMEMinecraftClient.player() == null ? null : LOTRLevelData.getData(KOMEMinecraftClient.player()).getPledgeFaction();
    }

    private static String valueOrUnclaimed(String value) {
        return value == null || value.trim().isEmpty() ? "unclaimed" : value;
    }

    private static String valueOrNone(String value) {
        return value == null || value.trim().isEmpty() ? "none" : value;
    }
}
