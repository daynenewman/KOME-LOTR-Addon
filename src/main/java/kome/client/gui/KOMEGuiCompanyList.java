package kome.client.gui;

import kome.client.KOMEConquestMapOverlay;
import kome.client.KOMEMinecraftClient;
import kome.common.network.KOMECompanyGuiEntry;
import kome.common.network.KOMEPacketConquestOpenCapture;
import kome.common.network.KOMEPacketHandler;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.List;

public class KOMEGuiCompanyList extends GuiScreen {
    private static final int PANEL_WIDTH = 520;
    private static final int PANEL_HEIGHT = 350;
    private static final int ID_CHOOSE_DESTINATION = 2;
    private static final int ID_BACK = 3;
    private static final int ID_REFRESH = 4;
    private static final int ID_CREATE_COMPANY = 5;
    private final String tileId;
    private final List<KOMECompanyGuiEntry> companies = new ArrayList<KOMECompanyGuiEntry>();
    private final boolean canCreate;
    private int selectedIndex = -1;
    private int scroll;

    public KOMEGuiCompanyList(String tileId, List companies, boolean canCreate) {
        this.tileId = tileId == null ? "" : tileId;
        this.canCreate = canCreate;
        for (Object object : companies) {
            if (object instanceof KOMECompanyGuiEntry) {
                this.companies.add((KOMECompanyGuiEntry) object);
            }
        }
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int x = panelX();
        int y = panelY();
        GuiButton choose = new KOMEGuiButton(ID_CHOOSE_DESTINATION, x + PANEL_WIDTH - 172, y + PANEL_HEIGHT - 67, 150, 22, "Choose Destination", true);
        choose.enabled = selectedIndex >= 0 && selectedIndex < companies.size() && companies.get(selectedIndex).canMove;
        buttonList.add(choose);
        buttonList.add(new KOMEGuiButton(ID_BACK, x + 22, y + PANEL_HEIGHT - 34, 110, 22, "Back"));
        GuiButton create = new KOMEGuiButton(ID_CREATE_COMPANY, x + 142, y + PANEL_HEIGHT - 34, 142, 22, "Create Company");
        create.enabled = canCreate;
        buttonList.add(create);
        buttonList.add(new KOMEGuiButton(ID_REFRESH, x + PANEL_WIDTH - 132, y + PANEL_HEIGHT - 34, 110, 22, "Refresh"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (!button.enabled) {
            return;
        }
        if (button.id == ID_CHOOSE_DESTINATION && selectedIndex >= 0) {
            KOMECompanyGuiEntry company = companies.get(selectedIndex);
            KOMEConquestMapOverlay.beginDestinationSelection(company.id, company.name, company.tile);
            KOMEConquestMapOverlay.openPreservedMap();
        } else if (button.id == ID_BACK) {
            KOMEPacketHandler.network.sendToServer(new KOMEPacketConquestOpenCapture(tileId));
            KOMEMinecraftClient.closePlayerScreen();
        } else if (button.id == ID_REFRESH) {
            KOMEMinecraftClient.sendChat("/troops companies " + tileId);
            KOMEMinecraftClient.closePlayerScreen();
        } else if (button.id == ID_CREATE_COMPANY) {
            KOMEMinecraftClient.sendChat("/troops createcompany " + tileId + " Company " + tileId);
            KOMEMinecraftClient.closePlayerScreen();
        }
    }

    @Override
    protected void keyTyped(char c, int key) {
        super.keyTyped(c, key);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        int x = panelX() + 22;
        int y = panelY() + 62;
        for (int row = 0; row < Math.min(5, companies.size() - scroll); row++) {
            if (mouseX >= x && mouseX < x + PANEL_WIDTH - 44 && mouseY >= y + row * 45 && mouseY < y + row * 45 + 39) {
                selectedIndex = scroll + row;
                initGui();
                return;
            }
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            int max = Math.max(0, companies.size() - 5);
            scroll = Math.max(0, Math.min(max, scroll + (wheel < 0 ? 1 : -1)));
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int x = panelX();
        int y = panelY();
        KOMEGuiTheme.drawMainPanel(x, y, PANEL_WIDTH, PANEL_HEIGHT);
        KOMEGuiTheme.drawHeader(fontRendererObj, "Select Company", x + 110, y + 10, PANEL_WIDTH - 220);
        fontRendererObj.drawString("Stationed at Tile " + tileId, x + 22, y + 43, KOMEGuiTheme.COLOR_TEXT);
        fontRendererObj.drawString("Companies come from the LOTR Unit Overview Company column.", x + 22, y + PANEL_HEIGHT - 82, KOMEGuiTheme.COLOR_TEXT_MUTED);
        int rowY = y + 62;
        if (companies.isEmpty()) {
            KOMEGuiTheme.drawSubPanel(x + 22, rowY, PANEL_WIDTH - 44, 52);
            fontRendererObj.drawString(canCreate ? "No companies are stationed here. Create one from unassigned units." : "No companies are stationed here.", x + 36, rowY + 20, KOMEGuiTheme.COLOR_TEXT_MUTED);
        }
        for (int row = 0; row < Math.min(5, companies.size() - scroll); row++) {
            int index = scroll + row;
            KOMECompanyGuiEntry company = companies.get(index);
            int cardY = rowY + row * 45;
            KOMEGuiTheme.drawSubPanel(x + 22, cardY, PANEL_WIDTH - 44, 39);
            if (index == selectedIndex) {
                drawRect(x + 24, cardY + 2, x + PANEL_WIDTH - 24, cardY + 4, KOMEGuiTheme.COLOR_BORDER_RED);
            }
            fontRendererObj.drawString(company.name, x + 34, cardY + 7, KOMEGuiTheme.COLOR_TEXT);
            String composition = company.unitCount + " units | " + company.population + " pop | Mounted "
                + company.mountedPopulation + " | Ground " + company.groundPopulation;
            fontRendererObj.drawString(composition, x + 34, cardY + 21, KOMEGuiTheme.COLOR_TEXT_MUTED);
            String speed = company.groundPopulation == 0 ? "2 tiles/day" : "1 tile/day";
            fontRendererObj.drawString(company.status + " | " + speed, x + PANEL_WIDTH - 34 - fontRendererObj.getStringWidth(company.status + " | " + speed),
                cardY + 7, company.canMove ? KOMEGuiTheme.COLOR_GOOD : KOMEGuiTheme.COLOR_WARN);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (selectedIndex >= 0 && !companies.get(selectedIndex).canMove) {
            KOMEGuiTheme.drawCenteredPlainText(fontRendererObj, companies.get(selectedIndex).cannotMoveReason,
                x + PANEL_WIDTH / 2, y + PANEL_HEIGHT - 91, KOMEGuiTheme.COLOR_WARN);
        }
    }

    private int panelX() {
        return width / 2 - PANEL_WIDTH / 2;
    }

    private int panelY() {
        return height / 2 - PANEL_HEIGHT / 2;
    }
}
