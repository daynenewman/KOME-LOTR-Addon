package kome.client.gui;

import kome.client.KOMEConquestMapOverlay;
import kome.common.network.KOMECompanyGuiEntry;
import kome.common.network.KOMEPacketConquestOpenCapture;
import kome.common.network.KOMEPacketHandler;
import kome.common.network.KOMEPacketTroopGuiAction;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.List;

public class KOMEGuiCompanyList extends GuiScreen {
    private static final int PANEL_WIDTH = 520;
    private static final int PANEL_HEIGHT = 410;
    private static final int ROW_HEIGHT = 52;
    private static final int ID_CHOOSE_DESTINATION = 2;
    private static final int ID_BACK = 3;
    private static final int ID_REFRESH = 4;
    private static final int ID_CREATE_COMPANY = 5;
    private static final int ID_TENDENCY = 6;
    private static final int ID_STAY = 7;
    private static final int ID_RETREAT = 8;
    private static final int ID_RESUME = 9;
    private static final int ID_RECLAIM = 10;
    private static final int ID_DISBAND = 11;
    private static final int ID_PLEDGE_DEPARTURE = 12;
    private static final int ID_RENAME = 13;
    private final String tileId;
    private final String tileDisplayName;
    private final List<KOMECompanyGuiEntry> companies = new ArrayList<KOMECompanyGuiEntry>();
    private final boolean canCreate;
    private int selectedIndex = -1;
    private int scroll;
    private boolean confirmReclaim;
    private boolean confirmDisband;
    private GuiTextField renameField;

    public KOMEGuiCompanyList(String tileId, List companies, boolean canCreate) {
        this(tileId, "", companies, canCreate);
    }

    public KOMEGuiCompanyList(String tileId, String tileDisplayName, List companies, boolean canCreate) {
        this.tileId = tileId == null ? "" : tileId;
        this.tileDisplayName = tileDisplayName == null ? "" : tileDisplayName;
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
        renameField = new GuiTextField(fontRendererObj, x + 310, y + PANEL_HEIGHT - 112, 184, 18);
        renameField.setMaxStringLength(24);
        KOMECompanyGuiEntry selected = selectedCompany();
        renameField.setText(selected == null ? "" : displayCompanyName(selected));
        addCompanyActions(x, y);
        buttonList.add(new KOMEGuiButton(ID_BACK, x + 22, y + PANEL_HEIGHT - 34, 140, 22, "Back"));
        buttonList.add(new KOMEGuiButton(ID_PLEDGE_DEPARTURE, x + 172, y + PANEL_HEIGHT - 34, 140, 22, "Departure"));
        buttonList.add(new KOMEGuiButton(ID_REFRESH, x + 322, y + PANEL_HEIGHT - 34, 176, 22, "Refresh"));
    }

    private void addCompanyActions(int x, int y) {
        List actions = new ArrayList();
        KOMECompanyGuiEntry selected = selectedCompany();
        actions.add(new CompanyAction(ID_CHOOSE_DESTINATION, "Choose Destination", selected != null && selected.canMove));
        actions.add(new CompanyAction(ID_RENAME, "Rename Company", selected != null));
        if (selected != null && selected.canSetTendency) {
            actions.add(new CompanyAction(ID_TENDENCY, "Toggle Tendency", true));
        }
        if (selected != null && selected.canChooseAccessResponse && selected.movementOrderId.length() > 0) {
            boolean retreatOnly = "war_ended_halted".equals(selected.movementStatus);
            if (!retreatOnly) actions.add(new CompanyAction(ID_STAY, "Stay", true));
            actions.add(new CompanyAction(ID_RETREAT, "Retreat", true));
            if (!retreatOnly) actions.add(new CompanyAction(ID_RESUME, "Resume", true));
        }
        if (selected != null && selected.canReclaim && !"NATIVE".equals(selected.controllerAuthority)) {
            actions.add(new CompanyAction(ID_RECLAIM, confirmReclaim ? "Confirm Reclaim" : "Reclaim", true));
        }
        if (selected != null && selected.canDisband) {
            actions.add(new CompanyAction(ID_DISBAND, confirmDisband ? "Confirm Disband" : "Disband", true));
        }
        int gap = 6;
        int width = (PANEL_WIDTH - 44 - gap * (actions.size() - 1)) / actions.size();
        for (int i = 0; i < actions.size(); i++) {
            CompanyAction action = (CompanyAction) actions.get(i);
            GuiButton button = new KOMEGuiButton(action.id, x + 22 + i * (width + gap), y + PANEL_HEIGHT - 68, width, 22, action.label);
            button.enabled = action.enabled;
            buttonList.add(button);
        }
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
        } else if (button.id == ID_REFRESH) {
            sendTroopAction("list", "", "");
        } else if (button.id == ID_PLEDGE_DEPARTURE) {
            kome.common.network.KOMEPacketHandler.network.sendToServer(new kome.common.network.KOMEPacketPledgeDepartureRequest());
        } else if (selectedCompany() != null && button.id == ID_TENDENCY) {
            KOMECompanyGuiEntry company = selectedCompany();
            String next = "AGGRESSIVE".equals(company.tendency) ? "conservative" : "aggressive";
            sendTroopAction("tendency", company.id, next);
        } else if (selectedCompany() != null && button.id == ID_RENAME) {
            sendTroopAction("rename", selectedCompany().id, renameField == null ? "" : renameField.getText());
        } else if (selectedCompany() != null && (button.id == ID_STAY || button.id == ID_RETREAT || button.id == ID_RESUME)) {
            String action = button.id == ID_STAY ? "stay" : button.id == ID_RETREAT ? "retreat" : "resume";
            sendTroopAction("movement", selectedCompany().movementOrderId, action);
        } else if (selectedCompany() != null && button.id == ID_RECLAIM) {
            if (!confirmReclaim) {
                confirmReclaim = true;
                initGui();
                return;
            }
            sendTroopAction("reclaim", selectedCompany().id, "");
        } else if (selectedCompany() != null && button.id == ID_DISBAND) {
            if (!confirmDisband) {
                confirmDisband = true;
                initGui();
                return;
            }
            sendTroopAction("disband", selectedCompany().id, "");
        }
    }

    private void sendTroopAction(String action, String companyId, String value) {
        KOMEPacketHandler.network.sendToServer(new KOMEPacketTroopGuiAction(action, companyId, value, tileId));
    }

    @Override
    protected void keyTyped(char c, int key) {
        if (renameField != null && renameField.textboxKeyTyped(c, key)) {
            return;
        }
        super.keyTyped(c, key);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        if (renameField != null) renameField.mouseClicked(mouseX, mouseY, button);
        int x = panelX() + 22;
        int y = panelY() + 62;
        for (int row = 0; row < Math.min(5, companies.size() - scroll); row++) {
            if (mouseX >= x && mouseX < x + PANEL_WIDTH - 44 && mouseY >= y + row * ROW_HEIGHT && mouseY < y + row * ROW_HEIGHT + ROW_HEIGHT - 6) {
                selectedIndex = scroll + row;
                confirmReclaim = false;
                confirmDisband = false;
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
        fontRendererObj.drawString(fontRendererObj.trimStringToWidth("Stationed at " + screenTileLabel(), PANEL_WIDTH - 44),
            x + 22, y + 43, KOMEGuiTheme.COLOR_TEXT);
        KOMECompanyGuiEntry selectedCompany = selectedCompany();
        String authoritySummary = selectedCompany == null
            ? "Select a company to inspect owner, temporary authority, tendency, and stewardship capacity."
            : "Stewardship for " + selectedCompany.faction + ": unallocated " + selectedCompany.stewardshipUnallocated
                + " | global 100% eligible cap " + selectedCompany.stewardshipGlobalCap + " | reserved " + selectedCompany.stewardshipReserved
                + " | available " + selectedCompany.stewardshipAvailable;
        fontRendererObj.drawString(fontRendererObj.trimStringToWidth(authoritySummary, PANEL_WIDTH - 44), x + 22, y + PANEL_HEIGHT - 88, KOMEGuiTheme.COLOR_TEXT_MUTED);
        fontRendererObj.drawString("Company name:", x + 232, y + PANEL_HEIGHT - 108, KOMEGuiTheme.COLOR_TEXT_MUTED);
        if (renameField != null) renameField.drawTextBox();
        int rowY = y + 62;
        if (companies.isEmpty()) {
            KOMEGuiTheme.drawSubPanel(x + 22, rowY, PANEL_WIDTH - 44, 52);
            fontRendererObj.drawString("No companies are stationed here. Combat hires create and join their hiring-tile company automatically.", x + 36, rowY + 20, KOMEGuiTheme.COLOR_TEXT_MUTED);
        }
        for (int row = 0; row < Math.min(5, companies.size() - scroll); row++) {
            int index = scroll + row;
            KOMECompanyGuiEntry company = companies.get(index);
            int cardY = rowY + row * ROW_HEIGHT;
            KOMEGuiTheme.drawSubPanel(x + 22, cardY, PANEL_WIDTH - 44, ROW_HEIGHT - 6);
            if (index == selectedIndex) {
                drawRect(x + 24, cardY + 2, x + PANEL_WIDTH - 24, cardY + 4, KOMEGuiTheme.COLOR_BORDER_RED);
            }
            String speed = company.groundPopulation == 0 ? "2 tiles/day" : "1 tile/day";
            String status = company.status + " | " + speed;
            String companyLabel = displayCompanyName(company) + " @ " + displayTileLabel(company.tile, company.tileDisplayName);
            int statusWidth = fontRendererObj.getStringWidth(status);
            int labelWidth = Math.max(90, PANEL_WIDTH - 76 - statusWidth - 10);
            fontRendererObj.drawString(fontRendererObj.trimStringToWidth(companyLabel, labelWidth), x + 34, cardY + 7, KOMEGuiTheme.COLOR_TEXT);
            String composition = company.unitCount + " units | " + company.population + " pop | Mounted "
                + company.mountedPopulation + " | Ground " + company.groundPopulation;
            fontRendererObj.drawString(composition, x + 34, cardY + 21, KOMEGuiTheme.COLOR_TEXT_MUTED);
            String controller = "Native " + company.nativeFaction + " | Owner " + company.ownerName + " | Controller " + company.controllerName + " (" + company.controllerAuthority
                + ") | Wars " + company.authorizedWarIds + " | Targets " + company.legalTargets + " | Cleanup " + company.withdrawalState
                + (company.movementStatus.length() > 0 ? " | " + company.movementStatus : "");
            fontRendererObj.drawString(fontRendererObj.trimStringToWidth(controller, PANEL_WIDTH - 68), x + 34, cardY + 34, KOMEGuiTheme.COLOR_TEXT_MUTED);
            fontRendererObj.drawString(status, x + PANEL_WIDTH - 34 - statusWidth,
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

    private String screenTileLabel() {
        if (tileId.length() == 0) {
            return "all company tiles";
        }
        return displayTileLabel(tileId, tileDisplayName);
    }

    private String displayTileLabel(String tile, String displayName) {
        String normalizedTile = tile == null ? "" : tile.trim();
        String waypointName = displayName == null ? "" : displayName.trim();
        if (normalizedTile.length() == 0) {
            return waypointName.length() == 0 ? "unknown tile" : waypointName;
        }
        if (waypointName.length() > 0 && !waypointName.equalsIgnoreCase(normalizedTile)) {
            return waypointName + " (" + normalizedTile + ")";
        }
        return "Tile " + normalizedTile;
    }

    private String displayCompanyName(KOMECompanyGuiEntry company) {
        if (company == null) {
            return "Unknown company";
        }
        if (company.name != null && company.name.trim().length() > 0) {
            return company.name.trim();
        }
        return company.id == null || company.id.length() == 0 ? "Unknown company" : company.id;
    }

    private KOMECompanyGuiEntry selectedCompany() {
        return selectedIndex >= 0 && selectedIndex < companies.size() ? companies.get(selectedIndex) : null;
    }

    private static class CompanyAction {
        private final int id;
        private final String label;
        private final boolean enabled;

        private CompanyAction(int id, String label, boolean enabled) {
            this.id = id;
            this.label = label;
            this.enabled = enabled;
        }
    }
}
