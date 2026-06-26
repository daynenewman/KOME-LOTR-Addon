package kome.client.gui;

import kome.client.KOMEMinecraftClient;
import kome.common.network.KOMEPacketCompanyMoveConfirmGui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

public class KOMEGuiCompanyMoveConfirm extends GuiScreen {
    private static final int PANEL_WIDTH = 480;
    private static final int PANEL_HEIGHT = 340;
    private final KOMEPacketCompanyMoveConfirmGui move;

    public KOMEGuiCompanyMoveConfirm(KOMEPacketCompanyMoveConfirmGui move) {
        this.move = move;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int x = panelX();
        int y = panelY();
        buttonList.add(new KOMEGuiButton(0, x + 24, y + PANEL_HEIGHT - 42, 150, 26, "Confirm Order", true));
        buttonList.add(new KOMEGuiButton(1, x + PANEL_WIDTH - 174, y + PANEL_HEIGHT - 42, 150, 26, "Cancel"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            KOMEMinecraftClient.sendChat("/troops movecompany " + move.companyId + " " + move.destinationTile);
            KOMEMinecraftClient.closePlayerScreen();
        } else if (button.id == 1) {
            KOMEMinecraftClient.closePlayerScreen();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int x = panelX();
        int y = panelY();
        KOMEGuiTheme.drawMainPanel(x, y, PANEL_WIDTH, PANEL_HEIGHT);
        KOMEGuiTheme.drawHeader(fontRendererObj, "Confirm Movement Order", x + 84, y + 12, PANEL_WIDTH - 168);
        drawCard(x + 24, y + 55, PANEL_WIDTH - 48, 66, "Company", move.companyName,
            move.unitCount + " units | " + move.population + " population");
        drawCard(x + 24, y + 126, PANEL_WIDTH - 48, 70, "Route",
            "Tile " + move.originTile + " -> Tile " + move.destinationTile,
            move.routeSummary);
        drawCard(x + 24, y + 204, PANEL_WIDTH - 48, 44, "Arrival Point",
            "Dim " + move.arrivalDimension + " at " + formatCoord(move.arrivalX) + ", " + formatCoord(move.arrivalY) + ", " + formatCoord(move.arrivalZ),
            move.arrivalSource == null || move.arrivalSource.length() == 0 ? "Saved Rally Point" : move.arrivalSource);
        String speed = move.tilesPerDay == 2 ? "Mounted company: 2 tiles/day" : "Ground/mixed company: 1 tile/day";
        fontRendererObj.drawString("Mounted: " + move.mountedPopulation + "   Ground: " + move.groundPopulation,
            x + 38, y + 254, KOMEGuiTheme.COLOR_TEXT);
        fontRendererObj.drawString(speed, x + 38, y + 270, KOMEGuiTheme.COLOR_TEXT_MUTED);
        String step = "Steps: " + move.distanceTiles + " | first move immediate";
        fontRendererObj.drawString(step, x + 38, y + 286, KOMEGuiTheme.COLOR_TEXT_MUTED);
        String eta = "Full route: " + formatDuration(move.travelMillis);
        fontRendererObj.drawString(eta, x + PANEL_WIDTH - 38 - fontRendererObj.getStringWidth(eta), y + 270, KOMEGuiTheme.COLOR_BORDER_RED);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawCard(int x, int y, int width, int height, String title, String line1, String line2) {
        KOMEGuiTheme.drawSubPanel(x, y, width, height);
        fontRendererObj.drawString(title, x + 12, y + 10, KOMEGuiTheme.COLOR_BORDER_RED);
        fontRendererObj.drawString(line1, x + 12, y + 28, KOMEGuiTheme.COLOR_TEXT);
        fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, line2, width - 24), x + 12, y + 45, KOMEGuiTheme.COLOR_TEXT_MUTED);
    }

    private String formatDuration(long millis) {
        long minutes = Math.max(0L, (millis + 59999L) / 60000L);
        long days = minutes / 1440L;
        long hours = (minutes % 1440L) / 60L;
        if (days > 0L) {
            return days + "d " + hours + "h";
        }
        return hours + "h " + minutes % 60L + "m";
    }

    private String formatCoord(double value) {
        return String.valueOf((int) Math.floor(value));
    }

    private int panelX() {
        return width / 2 - PANEL_WIDTH / 2;
    }

    private int panelY() {
        return height / 2 - PANEL_HEIGHT / 2;
    }
}
