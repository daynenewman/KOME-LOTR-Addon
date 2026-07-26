package kome.client.gui;

import kome.common.data.KOMEAllianceBenefits;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.List;

public class KOMEGuiAlliancePermissions extends GuiScreen {
    private static final int ID_BACK = 1;
    private static final int ID_TAB_BASE = 10;
    private static final int MARGIN = 18;
    private static final int GAP = 8;
    private static final int TAB_HEIGHT = 24;
    private static final int STATUS_HEIGHT = 62;
    private static final int ROW_HEIGHT = 42;
    private static final int SECTION_GAP = 10;

    private final KOMEGuiAlliance.Record record;
    private final List visibleTypes = new ArrayList();
    private int selectedType;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int scroll;

    public KOMEGuiAlliancePermissions(KOMEGuiAlliance.Record record, int preferredType) {
        this.record = record;
        buildVisibleTypes();
        selectedType = visibleTypes.contains(Integer.valueOf(preferredType))
            ? preferredType
            : visibleTypes.isEmpty() ? 0 : ((Integer) visibleTypes.get(0)).intValue();
    }

    @Override
    public void initGui() {
        panelW = Math.min(620, width - 28);
        panelH = Math.min(420, height - 28);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        scroll = Math.max(0, Math.min(scroll, getMaxScroll()));
        buttonList.clear();
        buttonList.add(KOMEGuiButton.small(ID_BACK, panelX + MARGIN, panelY + 13, "Back"));
        addTabs();
    }

    private void buildVisibleTypes() {
        visibleTypes.clear();
        for (int type = 0; type < KOMEAlliancePermissions.TYPES.length; type++) {
            if (record.getTier(type) != -1) {
                visibleTypes.add(Integer.valueOf(type));
            }
        }
    }

    private void addTabs() {
        if (visibleTypes.isEmpty()) {
            return;
        }
        int count = visibleTypes.size();
        int x = panelX + MARGIN;
        int y = getTabsY();
        int width = (panelW - MARGIN * 2 - GAP * (count - 1)) / count;
        for (int i = 0; i < count; i++) {
            int type = ((Integer) visibleTypes.get(i)).intValue();
            boolean selected = type == selectedType;
            String label = KOMEAlliancePermissions.TYPES[type];
            if (record.getTier(type) == -2) {
                label += " - Pending";
            }
            buttonList.add(new KOMEGuiButton(ID_TAB_BASE + type, x + i * (width + GAP), y, width, TAB_HEIGHT, label, selected).setSelected(selected));
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (!button.enabled) {
            return;
        }
        if (button.id == ID_BACK) {
            mc.displayGuiScreen(new KOMEGuiAllianceDetail(record));
        } else if (button.id >= ID_TAB_BASE && button.id < ID_TAB_BASE + KOMEAlliancePermissions.TYPES.length) {
            selectedType = button.id - ID_TAB_BASE;
            scroll = 0;
            initGui();
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel > 0) {
            scroll = Math.max(0, scroll - 18);
        } else if (wheel < 0) {
            scroll = Math.min(getMaxScroll(), scroll + 18);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        KOMEGuiTheme.drawMainPanel(panelX, panelY, panelW, panelH);
        KOMEGuiTheme.drawHeader(fontRendererObj, "Alliance Permissions", panelX + 142, panelY + 12, panelW - 284);
        drawRelationship();
        if (visibleTypes.isEmpty()) {
            drawEmptyState();
        } else {
            drawTabDivider();
            drawPermissionContent(mouseX, mouseY);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawRelationship() {
        String relation = record.factionA + " <-> " + record.factionB;
        KOMEGuiTheme.drawCenteredPlainText(fontRendererObj, KOMEGuiTheme.trimToWidth(fontRendererObj, relation, panelW - MARGIN * 2), panelX + panelW / 2, panelY + 48, KOMEGuiTheme.COLOR_BORDER_RED);
    }

    private void drawEmptyState() {
        int x = panelX + MARGIN;
        int y = panelY + 84;
        int w = panelW - MARGIN * 2;
        KOMEGuiTheme.drawCard(x, y, w, 62, false);
        fontRendererObj.drawString("No alliance permissions", x + 14, y + 12, KOMEGuiTheme.COLOR_BORDER_RED);
        fontRendererObj.drawString("No alliance types are active or pending for this relationship.", x + 14, y + 34, KOMEGuiTheme.COLOR_TEXT_MUTED);
    }

    private void drawTabDivider() {
        KOMEGuiTheme.drawDivider(panelX + MARGIN, getTabsY() + TAB_HEIGHT + 5, panelW - MARGIN * 2);
    }

    private void drawPermissionContent(int mouseX, int mouseY) {
        int x = panelX + MARGIN;
        int y = getContentY();
        int w = panelW - MARGIN * 2;
        int h = getContentBottom() - y;
        KOMEGuiTheme.enableScissor(mc, x, y, w, h);
        int cursor = y - scroll;
        cursor = drawStatusCard(x, cursor, w) + SECTION_GAP;
        cursor = drawSection("Unlocked", true, x, cursor, w, mouseX, mouseY) + SECTION_GAP;
        drawSection("Locked", false, x, cursor, w, mouseX, mouseY);
        KOMEGuiTheme.disableScissor();
        drawScrollbar(h);
    }

    private int drawStatusCard(int x, int y, int w) {
        int tier = record.getTier(selectedType);
        KOMEGuiTheme.drawCard(x, y, w, STATUS_HEIGHT, false);
        fontRendererObj.drawString("Current Status", x + 14, y + 9, KOMEGuiTheme.COLOR_BORDER_RED);
        fontRendererObj.drawString("Type: " + KOMEAlliancePermissions.TYPES[selectedType], x + 14, y + 27, KOMEGuiTheme.COLOR_TEXT);
        fontRendererObj.drawString("Current Tier: " + displayTier(tier), x + w / 2 - 36, y + 27, KOMEGuiTheme.COLOR_TEXT);
        String status = tier == -2 ? "Pending" : record.provisional() ? "Provisional" : tier >= 0 ? "Active" : "Unavailable";
        int color = tier == -2 ? KOMEGuiTheme.COLOR_WARN : tier >= 0 ? KOMEGuiTheme.COLOR_GOOD : KOMEGuiTheme.COLOR_TEXT_MUTED;
        fontRendererObj.drawString("Status: " + status, x + w - 116, y + 27, color);
        if (tier == -2) {
            fontRendererObj.drawString("Permissions unlock after the receiving faction accepts the request.", x + 14, y + 44, KOMEGuiTheme.COLOR_WARN);
        }
        return y + STATUS_HEIGHT;
    }

    private int drawSection(String title, boolean unlockedSection, int x, int y, int w, int mouseX, int mouseY) {
        List permissions = getPermissions(unlockedSection);
        int height = 28 + Math.max(1, permissions.size()) * ROW_HEIGHT + 6;
        KOMEGuiTheme.drawSubPanel(x, y, w, height);
        KOMEGuiTheme.drawSectionTitle(fontRendererObj, title, x + 12, y + 9, w - 24);
        int rowY = y + 28;
        if (permissions.isEmpty()) {
            String empty = unlockedSection ? "No permissions unlocked yet." : "No locked permissions remain.";
            fontRendererObj.drawString(empty, x + 16, rowY + 8, KOMEGuiTheme.COLOR_TEXT_MUTED);
            return y + height;
        }
        for (int i = 0; i < permissions.size(); i++) {
            Permission permission = (Permission) permissions.get(i);
            drawPermissionRow(permission, unlockedSection, x + 10, rowY + i * ROW_HEIGHT, w - 20, mouseX, mouseY);
        }
        return y + height;
    }

    private void drawPermissionRow(Permission permission, boolean unlocked, int x, int y, int w, int mouseX, int mouseY) {
        boolean hovered = KOMEGuiTheme.isHovered(mouseX, mouseY, x, y, w, ROW_HEIGHT - 3);
        KOMEGuiTheme.drawCard(x, y, w, ROW_HEIGHT - 3, hovered);
        String indicator = unlocked ? "Unlocked" : "Locked";
        int color = unlocked ? KOMEGuiTheme.COLOR_GOOD : KOMEGuiTheme.COLOR_TEXT_MUTED;
        fontRendererObj.drawString((unlocked ? "+ " : "- ") + permission.name, x + 10, y + 7, color);
        int statusWidth = fontRendererObj.getStringWidth(indicator);
        if (unlocked) {
            fontRendererObj.drawString(indicator, x + w - statusWidth - 10, y + 7, color);
        } else {
            String requirement = permission.requirement;
            int available = Math.max(80, w / 2);
            fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, requirement, available), x + w - available - 10, y + 7, KOMEGuiTheme.COLOR_TEXT_DISABLED);
        }
        fontRendererObj.drawString(KOMEGuiTheme.trimToWidth(fontRendererObj, permission.restriction, w - 24), x + 12, y + 22, KOMEGuiTheme.COLOR_TEXT_MUTED);
    }

    private List getPermissions(boolean unlocked) {
        List permissions = new ArrayList();
        int tier = record.getTier(selectedType);
        String[] names = KOMEAlliancePermissions.UNLOCKS[selectedType];
        for (int requiredTier = 1; requiredTier < names.length; requiredTier++) {
            boolean isUnlocked = tier >= requiredTier;
            if (isUnlocked == unlocked) {
                permissions.add(new Permission(names[requiredTier], requirementText(requiredTier, tier),
                    KOMEAllianceBenefits.get(selectedType, requiredTier).restriction));
            }
        }
        return permissions;
    }

    private String requirementText(int requiredTier, int currentTier) {
        return "Requires your faction's " + KOMEAlliancePermissions.TYPES[selectedType] + " T" + requiredTier;
    }

    private int getTabsY() {
        return panelY + 68;
    }

    private int getContentY() {
        return getTabsY() + TAB_HEIGHT + 14;
    }

    private int getContentBottom() {
        return panelY + panelH - 14;
    }

    private int getContentHeight() {
        if (visibleTypes.isEmpty()) {
            return 0;
        }
        int unlocked = 0;
        int tier = record.getTier(selectedType);
        String[] permissions = KOMEAlliancePermissions.UNLOCKS[selectedType];
        for (int i = 1; i < permissions.length; i++) {
            if (tier >= i) {
                unlocked++;
            }
        }
        int locked = permissions.length - 1 - unlocked;
        return STATUS_HEIGHT + SECTION_GAP * 2
            + 28 + Math.max(1, unlocked) * ROW_HEIGHT + 6
            + 28 + Math.max(1, locked) * ROW_HEIGHT + 6;
    }

    private int getMaxScroll() {
        return Math.max(0, getContentHeight() - Math.max(1, getContentBottom() - getContentY()));
    }

    private void drawScrollbar(int viewportHeight) {
        int max = getMaxScroll();
        if (max <= 0) {
            return;
        }
        int x = panelX + panelW - MARGIN - 6;
        int y = getContentY();
        KOMEGuiTheme.drawBorderedRect(x, y, 5, viewportHeight, KOMEGuiTheme.COLOR_GOLD_DARK, 0x552B2117);
        int handleH = Math.max(18, viewportHeight * viewportHeight / Math.max(viewportHeight, getContentHeight()));
        int handleY = y + (viewportHeight - handleH) * scroll / max;
        KOMEGuiTheme.drawBorderedRect(x, handleY, 5, handleH, KOMEGuiTheme.COLOR_BORDER_RED, KOMEGuiTheme.COLOR_GOLD);
    }

    private String displayTier(int tier) {
        return tier == -2 ? "Pending" : tier < 0 ? "None" : tier == 0 ? "Established" : "T" + tier;
    }

    private static class Permission {
        private final String name;
        private final String requirement;
        private final String restriction;

        private Permission(String name, String requirement, String restriction) {
            this.name = name;
            this.requirement = requirement;
            this.restriction = restriction;
        }
    }
}
