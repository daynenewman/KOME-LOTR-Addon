package kome.client.gui;

import kome.client.tactical.KOMETacticalAreaEditor;
import kome.client.tactical.KOMETacticalAreaEditor.Selection;
import kome.common.tactical.KOMEForceDeploymentArea;
import kome.common.tactical.edit.KOMETacticalAreaCatalog;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

/** Shared Tactical Area Editor shell. This first scope edits only tile-owned deployment areas. */
public final class KOMEGuiTacticalAreaEditor extends GuiScreen {
    private final KOMETacticalAreaEditor editor;
    private GuiTextField id, label, bottom, top;
    private String target = "";
    private long catalogRevision = -1;
    private int page = -1;
    private boolean confirmDelete;
    public KOMEGuiTacticalAreaEditor(KOMETacticalAreaEditor editor) { this.editor = editor; }
    @Override public void initGui() {
        Keyboard.enableRepeatEvents(true); buttonList.clear(); confirmDelete = false;
        KOMEForceDeploymentArea area = editor.getDraft();
        target = area == null ? "" : area.getAreaId();
        KOMETacticalAreaCatalog catalog = editor.getCatalog();
        if (catalog == null) return;
        catalogRevision = catalog.revision; page = catalog.page;
        if (!editor.isEditing()) {
            for (int i = 0; i < catalog.rows.size(); i++) {
                KOMETacticalAreaCatalog.Row row = catalog.rows.get(i);
                buttonList.add(new GuiButton(100 + i, 16, 50 + i * 20, width - 32, 18,
                    fontRendererObj.trimStringToWidth(row.id + "  " + row.label + "  (rev " + row.revision + ")", width - 48)));
            }
            buttonList.add(new GuiButton(1, 16, height - 88, 60, 20, "Previous"));
            buttonList.add(new GuiButton(2, 82, height - 88, 60, 20, "Next"));
            buttonList.add(new GuiButton(3, width - 90, height - 88, 74, 20, "Refresh"));
            id = new GuiTextField(fontRendererObj, 16, height - 54, width - 126, 18); id.setMaxStringLength(128);
            buttonList.add(new GuiButton(4, width - 102, height - 55, 86, 20, "Create area"));
            buttonList.add(new GuiButton(5, width / 2 - 40, height - 26, 80, 20, "Close"));
        } else {
            label = new GuiTextField(fontRendererObj, 62, 48, width - 78, 18); label.setMaxStringLength(256); label.setText(area.getLabel());
            bottom = new GuiTextField(fontRendererObj, 16, 86, width / 2 - 24, 18); bottom.setMaxStringLength(12);
            bottom.setText(Integer.toString(area.getPrism().getMinYInclusive()));
            top = new GuiTextField(fontRendererObj, width / 2 + 8, 86, width / 2 - 24, 18); top.setMaxStringLength(12);
            top.setText(Long.toString((long) area.getPrism().getMaxYExclusive() - 1L));
            buttonList.add(new GuiButton(10, 16, 112, width - 32, 20, "Select polygon in world"));
            buttonList.add(new GuiButton(11, 16, 136, width / 2 - 24, 20, "Pick bottom Y"));
            buttonList.add(new GuiButton(12, width / 2 + 8, 136, width / 2 - 24, 20, "Pick top block Y"));
            buttonList.add(new GuiButton(13, 16, 160, 72, 20, "Undo vertex"));
            buttonList.add(new GuiButton(14, 94, 160, 72, 20, "Clear polygon"));
            int size = (width - 44) / 4;
            for (int i = 0; i < 4; i++) buttonList.add(new GuiButton(20 + i, 16 + i * (size + 4), height - 26, size, 20,
                new String[] {"Validate", "Save", "Cancel", "Delete"}[i]));
        }
        updateButtons();
    }
    private void updateButtons() {
        KOMETacticalAreaCatalog catalog = editor.getCatalog();
        for (Object value : buttonList) {
            GuiButton b = (GuiButton) value; b.enabled = !editor.isBusy();
            if (b.id == 1) b.enabled &= catalog != null && catalog.page > 0;
            if (b.id == 2) b.enabled &= catalog != null && (long) (catalog.page + 1) * KOMETacticalAreaCatalog.PAGE_SIZE < catalog.total;
            if (b.id == 23) { b.enabled &= !editor.isCreating(); b.displayString = confirmDelete ? "Confirm" : "Delete"; }
        }
    }
    @Override public void updateScreen() {
        KOMETacticalAreaCatalog c = editor.getCatalog();
        String current = editor.getDraft() == null ? "" : editor.getDraft().getAreaId();
        if (!target.equals(current) || c != null && (c.revision != catalogRevision || c.page != page)) initGui();
        updateButtons();
        if (editor.isEditing()) { label.updateCursorCounter(); bottom.updateCursorCounter(); top.updateCursorCounter(); }
        else if (id != null) id.updateCursorCounter();
    }
    private void fields() {
        editor.setLabel(label.getText());
        try { editor.setYRangeInclusive(Integer.parseInt(bottom.getText()), Integer.parseInt(top.getText())); }
        catch (NumberFormatException invalid) { throw new IllegalArgumentException("Y bounds must be whole block numbers."); }
    }
    @Override protected void actionPerformed(GuiButton button) {
        try {
            if (button.id >= 100) { editor.open(editor.getCatalog().rows.get(button.id - 100).id, false); return; }
            switch (button.id) {
                case 1: editor.browse(editor.getCatalog().page - 1); break;
                case 2: editor.browse(editor.getCatalog().page + 1); break;
                case 3: editor.browse(0); break;
                case 4: editor.open(id.getText(), true); break;
                case 5: mc.displayGuiScreen(null); break;
                case 10: case 11: case 12:
                    fields(); editor.select(button.id == 10 ? Selection.VERTICES : button.id == 11 ? Selection.LOWER_Y : Selection.UPPER_Y);
                    mc.displayGuiScreen(null); break;
                case 13: fields(); editor.undo(); break;
                case 14: fields(); editor.clearVertices(); break;
                case 20: fields(); editor.validate(); break;
                case 21: fields(); editor.save(); break;
                case 22: editor.cancel(); initGui(); break;
                case 23:
                    if (!confirmDelete) { confirmDelete = true; editor.error("Delete this area? Click Confirm. Referenced areas are protected."); }
                    else editor.delete();
                    break;
                default: break;
            }
        } catch (RuntimeException invalid) { editor.error(invalid.getMessage() == null ? "Action could not be completed." : invalid.getMessage()); }
    }
    @Override protected void keyTyped(char character, int key) {
        if (key == Keyboard.KEY_ESCAPE) { if (editor.isEditing()) { editor.cancel(); initGui(); } else mc.displayGuiScreen(null); return; }
        if (editor.isEditing()) { label.textboxKeyTyped(character, key); bottom.textboxKeyTyped(character, key); top.textboxKeyTyped(character, key); }
        else if (id != null) id.textboxKeyTyped(character, key);
    }
    @Override protected void mouseClicked(int x, int y, int button) {
        super.mouseClicked(x, y, button);
        if (editor.isEditing()) { label.mouseClicked(x, y, button); bottom.mouseClicked(x, y, button); top.mouseClicked(x, y, button); }
        else if (id != null) id.mouseClicked(x, y, button);
    }
    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "Tactical Area Editor - Force Deployment Areas", width / 2, 8, 0xFFFFFF);
        KOMETacticalAreaCatalog c = editor.getCatalog();
        if (c != null) drawString(fontRendererObj, "Tile " + c.tileId + "  |  Dimension " + c.dimension + "  |  Config rev " + c.revision, 16, 24, 0xBBBBBB);
        if (editor.isEditing()) {
            KOMEForceDeploymentArea a = editor.getDraft();
            drawString(fontRendererObj, a.getAreaId() + "  |  " + (editor.isCreating() ? "New draft" : "Area rev " + a.getRevision()), 16, 36, 0x80E0FF);
            drawString(fontRendererObj, "Label:", 16, 53, 0xFFFFFF);
            label.drawTextBox();
            drawString(fontRendererObj, "Bottom block Y (included)", 16, 73, 0xFFFFFF);
            drawString(fontRendererObj, "Top block Y (included)", width / 2 + 8, 73, 0xFFFFFF);
            bottom.drawTextBox(); top.drawTextBox();
            drawString(fontRendererObj, a.getPrism().getPolygon().getVertices().size() + " ordered vertices", 176, 166, 0xFFFFFF);
        } else if (c != null) {
            if (c.rows.isEmpty()) drawString(fontRendererObj, "No areas on this tile yet.", 16, 50, 0xBBBBBB);
            drawString(fontRendererObj, c.total + " areas  |  Page " + (c.page + 1), 16, 36, 0xBBBBBB);
            drawString(fontRendererObj, "New stable area ID", 16, height - 66, 0xFFFFFF);
            if (id != null) id.drawTextBox();
        }
        int y = editor.isEditing() ? 185 : height - 35;
        java.util.List<String> lines = fontRendererObj.listFormattedStringToWidth(editor.getMessage(), width - 32);
        for (int i = 0; i < Math.min(editor.isEditing() ? Math.max(1, (height - 30 - y) / 10) : 1, lines.size()); i++)
            drawString(fontRendererObj, lines.get(i), 16, y + i * 10, 0xFFD080);
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (mouseY >= y && mouseY < height - 26 && !lines.isEmpty()) func_146283_a(lines, mouseX, mouseY);
    }
    @Override public boolean doesGuiPauseGame() { return false; }
    @Override public void onGuiClosed() { Keyboard.enableRepeatEvents(false); }
}
