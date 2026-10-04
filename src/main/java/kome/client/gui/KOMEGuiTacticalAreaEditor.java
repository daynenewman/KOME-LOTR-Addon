package kome.client.gui;

import kome.client.tactical.KOMETacticalAreaEditor;
import kome.client.tactical.KOMETacticalAreaEditor.Selection;
import kome.common.tactical.KOMEForceDeploymentArea;
import kome.common.tactical.edit.KOMETacticalAreaCatalog;
import kome.common.tactical.edit.*;
import kome.common.siege.*;
import kome.common.siege.geometry.KOMEPolygonPrism;
import kome.client.tactical.KOMETacticalComplexDraft;
import kome.client.tactical.KOMETacticalComplexDraft.ZoneType;
import kome.client.tactical.KOMETacticalConnectionDraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

/** Shared Tactical Area Editor shell for tile deployment areas and complex-local zones. */
public final class KOMEGuiTacticalAreaEditor extends GuiScreen {
    private final KOMETacticalAreaEditor editor;
    private GuiTextField id, label, bottom, top;
    private String target = "";
    private long catalogRevision = -1;
    private int page = -1;
    private boolean confirmDelete;
    private boolean geometryView, confirmZone;
    private boolean connectionView, connectionForm, confirmConnection;
    private enum ConnectionChoice { NONE, A, B, TRANSITION }
    private ConnectionChoice connectionChoice = ConnectionChoice.NONE;
    private int connectionPage, choicePage;
    private KOMETacticalComplexCatalog.Kind optionView;
    private KOMETacticalComplexCatalog.Row selectedBuild;
    private int zonePage, accessPage;
    private String complexLayout = "";
    private boolean dispatchingClick, clickActionHandled;
    public KOMEGuiTacticalAreaEditor(KOMETacticalAreaEditor editor) {
        this.editor = editor; this.connectionView = editor.getConnection() != null; this.connectionForm = connectionView;
        this.geometryView = editor.isComplexEditing() && editor.getZone() != null && !connectionView;
    }
    @Override public void initGui() {
        Keyboard.enableRepeatEvents(true); buttonList.clear(); confirmDelete = false;
        id = label = bottom = top = null;
        KOMEForceDeploymentArea area = editor.getDraft();
        target = editor.getTargetId();
        KOMETacticalAreaCatalog catalog = editor.getCatalog();
        if (catalog == null) return;
        catalogRevision = catalog.revision; page = catalog.page;
        complexLayout = layoutKey();
        if (!editor.isEditing()) {
            buttonList.add(new GuiButton(6, 16, 36, (width - 36) / 2, 18, "Force Deployment Areas"));
            buttonList.add(new GuiButton(7, width / 2 + 2, 36, (width - 36) / 2, 18, "Siege Complexes"));
        }
        if (editor.isComplexBrowser() && !editor.isEditing() || editor.isComplexEditing() && (!geometryView || optionView != null)) {
            initComplex(); updateButtons(); return;
        }
        if (!editor.isEditing()) {
            for (int i = 0; i < catalog.rows.size(); i++) {
                KOMETacticalAreaCatalog.Row row = catalog.rows.get(i);
                buttonList.add(new GuiButton(100 + i, 16, 58 + i * 18, width - 32, 18,
                    fontRendererObj.trimStringToWidth(row.id + "  " + row.label + "  (rev " + row.revision + ")", width - 48)));
            }
            buttonList.add(new GuiButton(1, 16, height - 88, 60, 20, "Previous"));
            buttonList.add(new GuiButton(2, 82, height - 88, 60, 20, "Next"));
            buttonList.add(new GuiButton(3, width - 90, height - 88, 74, 20, "Refresh"));
            id = new GuiTextField(fontRendererObj, 16, height - 54, width - 126, 18); id.setMaxStringLength(128);
            buttonList.add(new GuiButton(4, width - 102, height - 55, 86, 20, "Create area"));
            buttonList.add(new GuiButton(5, width / 2 - 40, height - 26, 80, 20, "Close"));
        } else {
            label = new GuiTextField(fontRendererObj, 62, 48, width - 78, 18); label.setMaxStringLength(256); label.setText(editor.getGeometryLabel());
            bottom = new GuiTextField(fontRendererObj, 16, 86, width / 2 - 24, 18); bottom.setMaxStringLength(12);
            bottom.setText(Integer.toString(editor.getGeometry().getMinYInclusive()));
            top = new GuiTextField(fontRendererObj, width / 2 + 8, 86, width / 2 - 24, 18); top.setMaxStringLength(12);
            top.setText(Long.toString((long) editor.getGeometry().getMaxYExclusive() - 1L));
            buttonList.add(new GuiButton(10, 16, 112, width - 32, 20, "Select vertices (Tab corners)"));
            buttonList.add(new GuiButton(11, 16, 136, width / 2 - 24, 20, "Pick bottom Y"));
            buttonList.add(new GuiButton(12, width / 2 + 8, 136, width / 2 - 24, 20, "Pick top block Y"));
            buttonList.add(new GuiButton(13, 16, 160, 72, 20, "Undo vertex"));
            buttonList.add(new GuiButton(14, 94, 160, 72, 20, "Clear polygon"));
            if (editor.isComplexEditing()) {
                buttonList.add(new GuiButton(15, width - 86, 160, 70, 20, "Back"));
                if (editor.getZoneType() == ZoneType.WALL) buttonList.add(new GuiButton(16, 170, 160, Math.max(50, width - 260), 20, "Access"));
            }
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
            if (!editor.isComplexBrowser()) {
                if (b.id == 1) b.enabled &= catalog != null && catalog.page > 0;
                if (b.id == 2) b.enabled &= catalog != null && (long) (catalog.page + 1) * KOMETacticalAreaCatalog.PAGE_SIZE < catalog.total;
            }
            if (b.id == 23) { b.enabled &= !editor.isCreating(); b.displayString = confirmDelete ? "Confirm" : "Delete"; }
            if (b.id == 7) b.enabled &= !editor.isComplexBrowser();
            if (b.id == 6) b.enabled &= editor.isComplexBrowser();
            if (b.id == 205) b.enabled &= !editor.isCreating();
            if (b.id == 213) b.enabled &= editor.getZone() != null;
            if (b.id == 222) b.enabled &= selectedBuild != null;
            if (b.id == 607 || b.id == 608) b.enabled &= editor.getInference() != null;
            if (b.id == 607) b.enabled &= editor.getInference() != null && editor.getInference().isSuccessful();
            if (b.id == 604) b.enabled &= !editor.isCreating();
            if (optionView == KOMETacticalComplexCatalog.Kind.GATES && b.id >= 400 && b.id < 405
                    && editor.getOptions() != null && editor.getOptions().kind == optionView && b.id - 400 < editor.getOptions().rows.size())
                b.enabled &= editor.canSelectConnectionGate(editor.getOptions().rows.get(b.id - 400));
            if (editor.isComplexBrowser() && !editor.isEditing()) {
                KOMETacticalComplexCatalog c = editor.getComplexCatalog();
                if (b.id == 1) b.enabled &= c != null && c.page > 0;
                if (b.id == 2) b.enabled &= c != null && (long) (c.page + 1) * 5 < c.total;
            }
        }
    }
    @Override public void updateScreen() {
        synchronizeLayout();
        updateButtons();
        if (editor.isEditing() && (editor.getDraft() != null || geometryView && optionView == null)) { label.updateCursorCounter(); bottom.updateCursorCounter(); top.updateCursorCounter(); }
        else if (id != null) id.updateCursorCounter();
    }
    private void synchronizeLayout() {
        KOMETacticalAreaCatalog c = editor.getCatalog();
        String current = editor.getTargetId();
        if (!target.equals(current)) { geometryView = false; optionView = null; zonePage = 0; selectedBuild = null;
            connectionView = connectionForm = false; connectionChoice = ConnectionChoice.NONE; }
        if (!target.equals(current) || !complexLayout.equals(layoutKey()) || c != null && (c.revision != catalogRevision || c.page != page)) initGui();
    }
    private void fields() {
        editor.setLabel(label.getText());
        try { editor.setYRangeInclusive(Integer.parseInt(bottom.getText()), Integer.parseInt(top.getText())); }
        catch (NumberFormatException invalid) { throw new IllegalArgumentException("Y bounds must be whole block numbers."); }
    }
    @Override protected void actionPerformed(GuiButton button) {
        // Vanilla 1.7.10 iterates the live buttonList after this method rebuilds it.
        // One click must never activate a newly-created control (especially Delete/Confirm).
        if (dispatchingClick && clickActionHandled) return;
        if (dispatchingClick) clickActionHandled = true;
        try {
            synchronizeLayout();
            if (button.id == 6 || button.id == 7) { editor.switchBrowser(button.id == 7); initGui(); return; }
            if (editor.isComplexBrowser() && !editor.isEditing() || editor.isComplexEditing() && (!geometryView || optionView != null)) {
                complexAction(button); return;
            }
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
                case 15: fields(); geometryView = false; initGui(); break;
                case 16: fields(); optionView = KOMETacticalComplexCatalog.Kind.COMPLEXES; accessPage = 0; initGui(); break;
                case 20: fields(); editor.validate(); break;
                case 21: fields(); editor.save(); break;
                case 22: editor.cancel(); initGui(); break;
                case 23:
                    if (!confirmDelete) { confirmDelete = true; editor.error(editor.isComplexEditing()
                        ? "Delete the ENTIRE Siege Complex " + editor.getTargetId() + " and its zones? Click Confirm. Assigned Builds prevent deletion."
                        : "Delete this area? Click Confirm. Referenced areas are protected."); }
                    else editor.delete();
                    break;
                default: break;
            }
        } catch (RuntimeException invalid) { editor.error(invalid.getMessage() == null ? "Action could not be completed." : invalid.getMessage()); }
    }
    @Override protected void keyTyped(char character, int key) {
        synchronizeLayout();
        if (key == Keyboard.KEY_ESCAPE) { if (editor.isEditing()) { editor.cancel(); initGui(); } else mc.displayGuiScreen(null); return; }
        if (editor.isEditing() && (editor.getDraft() != null || geometryView && optionView == null)) { label.textboxKeyTyped(character, key); bottom.textboxKeyTyped(character, key); top.textboxKeyTyped(character, key); }
        else if (id != null) id.textboxKeyTyped(character, key);
    }
    @Override protected void mouseClicked(int x, int y, int button) {
        synchronizeLayout();
        dispatchingClick = true; clickActionHandled = false;
        try { super.mouseClicked(x, y, button); }
        finally { dispatchingClick = false; }
        if (editor.isEditing() && (editor.getDraft() != null || geometryView && optionView == null)) { label.mouseClicked(x, y, button); bottom.mouseClicked(x, y, button); top.mouseClicked(x, y, button); }
        else if (id != null) id.mouseClicked(x, y, button);
    }
    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        synchronizeLayout();
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "Tactical Area Editor", width / 2, 8, 0xFFFFFF);
        KOMETacticalAreaCatalog c = editor.getCatalog();
        if (c != null) drawString(fontRendererObj, "Tile " + c.tileId + "  |  Dimension " + c.dimension + "  |  Config rev " + editor.displayedStoreRevision(), 16, 24, 0xBBBBBB);
        boolean complexPanel = editor.isComplexBrowser() && !editor.isEditing() || editor.isComplexEditing() && (!geometryView || optionView != null);
        if (complexPanel) drawComplex();
        else if (editor.isEditing()) {
            KOMEPolygonPrism prism = editor.getGeometry();
            long rev = editor.isComplexEditing() ? editor.getComplexDraft().getRevision() : editor.getDraft().getRevision();
            drawString(fontRendererObj, editor.getGeometryId() + "  |  " + (editor.isCreating() ? "New draft" : "Revision " + rev), 16, 36, 0x80E0FF);
            drawString(fontRendererObj, "Label:", 16, 53, 0xFFFFFF);
            label.drawTextBox();
            drawString(fontRendererObj, "Bottom block Y (included)", 16, 73, 0xFFFFFF);
            drawString(fontRendererObj, "Top block Y (included)", width / 2 + 8, 73, 0xFFFFFF);
            bottom.drawTextBox(); top.drawTextBox();
            drawString(fontRendererObj, editor.getConfirmedVertexCount() + " vertices / " + editor.getDistinctVertexCount() + " distinct", 16, 181, 0xFFFFFF);
        } else if (c != null) {
            if (c.rows.isEmpty()) drawString(fontRendererObj, "No areas on this tile yet.", 16, 60, 0xBBBBBB);
            drawString(fontRendererObj, "New stable area ID", 16, height - 66, 0xFFFFFF);
            if (id != null) id.drawTextBox();
        }
        int y = complexPanel ? height - 35 : editor.isEditing() ? 194 : height - 35;
        KOMETacticalAreaEditor.GeometryFeedback feedback = editor.geometryFeedback();
        String text = (feedback.messages.isEmpty() ? "" : String.join(" ", feedback.messages) + " ") + editor.getMessage();
        if (connectionView && editor.getConnection() != null) text = editor.connectionStatus() + " "
            + String.join(" ", editor.connectionDiagnostics()) + " " + editor.getMessage();
        if (connectionView && editor.isEditing()) y = 187;
        boolean invalidConnection = connectionView && editor.getConnection() != null && editor.connectionHasErrors(editor.getConnection());
        java.util.List<String> lines = fontRendererObj.listFormattedStringToWidth(text, width - 32);
        for (int i = 0; i < Math.min(editor.isEditing() ? Math.max(1, (height - 30 - y) / 10) : 1, lines.size()); i++)
            drawString(fontRendererObj, lines.get(i), 16, y + i * 10, feedback.isInvalid() || invalidConnection ? 0xFF7070 : 0xFFD080);
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (mouseY >= y && mouseY < height - 26 && !lines.isEmpty()) func_146283_a(lines, mouseX, mouseY);
        if (!editor.isEditing() && editor.isComplexBrowser() && editor.getComplexCatalog() != null && mouseX >= 16 && mouseX < width - 16) {
            int rowIndex = (mouseY - 58) / 18;
            if (mouseY >= 58 && rowIndex < editor.getComplexCatalog().rows.size()) {
                KOMETacticalComplexCatalog.Row row = editor.getComplexCatalog().rows.get(rowIndex);
                func_146283_a(java.util.Arrays.asList(row.id, "Revision " + row.revision, row.detail,
                    row.assignedBuildCount + " assigned Builds", "Preferred area: " + (row.relatedId == null ? "none" : row.relatedId)), mouseX, mouseY);
            }
        }
        if (editor.isComplexEditing() && mouseX >= 16 && mouseX < width - 16) {
            if (optionView != null && optionView != KOMETacticalComplexCatalog.Kind.COMPLEXES && editor.getOptions() != null
                    && editor.getOptions().kind == optionView && mouseY >= 75 && mouseY < 175) {
                int index = (mouseY - 75) / 20;
                if (index < editor.getOptions().rows.size()) {
                    KOMETacticalComplexCatalog.Row row = editor.getOptions().rows.get(index);
                    func_146283_a(java.util.Arrays.asList(optionView == KOMETacticalComplexCatalog.Kind.GATES ? row.relatedId + " / " + row.id : row.id,
                        row.label, row.detail, optionView == KOMETacticalComplexCatalog.Kind.GATES ? editor.gateChoiceUsage(row) : ""), mouseX, mouseY);
                }
            } else if (connectionView && !connectionForm && optionView == null && mouseY >= 82 && mouseY < 142) {
                int index = connectionPage * 3 + (mouseY - 82) / 20;
                if (index < editor.getConnections().size()) {
                    KOMESiegeConnection connection = editor.getConnections().get(index);
                    func_146283_a(java.util.Arrays.asList(connection.getId(), connection.getEndpointA() + " <-> " + connection.getEndpointB(),
                        "Transition: " + connection.getTransitionZoneId(), "Gate: " + (connection.isGated()
                        ? connection.getGateRef().get().getBuildId() + " / " + connection.getGateRef().get().getGateRecordId() : "NONE"),
                        editor.connectionStatus(connection.getId())), mouseX, mouseY);
                }
            }
        }
    }
    private String layoutKey() {
        KOMETacticalComplexCatalog c = editor.isEditing() ? editor.getOptions() : editor.getComplexCatalog();
        return editor.getCatalogueVersion() + ":" + editor.isComplexBrowser() + ":" + geometryView + ":" + optionView + ":" + editor.getZoneType() + ":" + editor.getZoneId()
            + ":" + connectionView + ":" + connectionForm + ":" + connectionChoice + ":" + editor.getConnectionId()
            + ":" + (c == null ? "" : c.kind + ":" + c.revision + ":" + c.page);
    }
    private void button(int id, int x, int y, int w, String title) { buttonList.add(new GuiButton(id, x, y, w, 18, title)); }
    private void initComplex() {
        if (!editor.isEditing()) {
            KOMETacticalComplexCatalog c = editor.getComplexCatalog();
            if (c != null) for (int i = 0; i < c.rows.size(); i++) {
                KOMETacticalComplexCatalog.Row row = c.rows.get(i);
                button(100 + i, 16, 58 + i * 18, width - 32, fontRendererObj.trimStringToWidth(
                    row.id + " r" + row.revision + " | " + row.detail + " | " + row.assignedBuildCount + " Builds | "
                        + (row.relatedId == null ? "no preferred area" : row.relatedId), width - 48));
            }
            button(1, 16, height - 88, 60, "Previous"); button(2, 82, height - 88, 60, "Next");
            button(3, width - 90, height - 88, 74, "Refresh");
            id = new GuiTextField(fontRendererObj, 16, height - 54, width - 144, 18); id.setMaxStringLength(128);
            button(4, width - 120, height - 54, 104, "Create complex"); button(5, width / 2 - 40, height - 26, 80, "Close");
            return;
        }
        if (optionView != null) {
            java.util.List<KOMESiegeZone> normals = KOMETacticalComplexDraft.zones(editor.getComplexDraft(), ZoneType.NORMAL);
            KOMETacticalComplexCatalog options = editor.getOptions();
            if (optionView == KOMETacticalComplexCatalog.Kind.COMPLEXES) {
                for (int i = 0, start = accessPage * 5; i < 5 && start + i < normals.size(); i++) {
                    String normal = normals.get(start + i).getId();
                    boolean checked = ((KOMEWallZone) editor.getZone()).getAccessibleFromNormalSegmentIds().contains(normal);
                    button(400 + i, 16, 75 + i * 20, width - 32, (checked ? "[x] " : "[ ] ") + normal);
                }
            } else if (options != null && options.kind == optionView) for (int i = 0; i < options.rows.size(); i++) {
                KOMETacticalComplexCatalog.Row row = options.rows.get(i);
                button(400 + i, 16, 75 + i * 20, width - 32, fontRendererObj.trimStringToWidth(
                    (optionView == KOMETacticalComplexCatalog.Kind.GATES ? row.relatedId + " / " : "") + row.id
                    + (optionView == KOMETacticalComplexCatalog.Kind.GATES ? " | " + editor.gateChoiceUsage(row) : "") + " " + row.label + " | " + row.detail, width - 48));
            }
            button(220, 16, height - 62, 60, "Previous"); button(221, 82, height - 62, 60, "Next");
            button(224, width - 90, height - 62, 74, "Refresh");
            button(223, width - 86, height - 26, 70, "Back");
            if (optionView == KOMETacticalComplexCatalog.Kind.PREFERRED_AREAS) button(225, 16, height - 26, 110, "No preferred area");
            if (optionView == KOMETacticalComplexCatalog.Kind.GATES) button(226, 16, height - 26, 110, "Gateless (NONE)");
            if (optionView == KOMETacticalComplexCatalog.Kind.GATES && editor.getConnection() != null && editor.getConnection().isGated())
                button(227, 132, height - 26, Math.max(70, width - 224), "Keep current");
            if (optionView == KOMETacticalComplexCatalog.Kind.BUILDS) button(222, 16, height - 26, 170,
                selectedBuild == null ? "Select a Build" : selectedBuild.relatedId == null ? "Confirm assign"
                    : selectedBuild.relatedId.equals(editor.getTargetId()) ? "Confirm unassign" : "Confirm reassign");
            return;
        }
        if (connectionView) { initConnections(); return; }
        int w = (width - 52) / 6;
        for (int i = 0; i < 6; i++) button(201 + i, 16 + i * (w + 4), 54, w, new String[] {"Normals", "Walls", "Transitions", "Preferred", "Builds", "Links"}[i]);
        java.util.List<KOMESiegeZone> zones = KOMETacticalComplexDraft.zones(editor.getComplexDraft(), editor.getZoneType());
        if (zonePage * 3 >= zones.size()) zonePage = 0;
        for (int i = 0, start = zonePage * 3; i < 3 && start + i < zones.size(); i++) {
            KOMESiegeZone zone = zones.get(start + i);
            button(300 + i, 16, 82 + i * 20, width - 32, fontRendererObj.trimStringToWidth(zone.getId() + " " + zone.getLabel(), width - 48));
        }
        button(210, 16, 144, 54, "Previous"); button(211, 74, 144, 54, "Next");
        id = new GuiTextField(fontRendererObj, 16, 169, width - 190, 18); id.setMaxStringLength(128);
        button(212, width - 166, 169, 70, "New zone"); button(213, width - 90, 169, 74, confirmZone ? "Confirm" : "Remove zone");
        int size = (width - 44) / 4;
        for (int i = 0; i < 4; i++) button(20 + i, 16 + i * (size + 4), height - 26, size, new String[] {"Validate", "Save", "Cancel", "Delete"}[i]);
    }
    private void complexAction(GuiButton b) {
        if (!editor.isEditing()) {
            if (b.id >= 100) { editor.openComplex(editor.getComplexCatalog().rows.get(b.id - 100).id, false); return; }
            if (b.id == 1 || b.id == 2 || b.id == 3) {
                int page = editor.getComplexCatalog() == null ? 0 : editor.getComplexCatalog().page;
                editor.complexPage(KOMETacticalComplexCatalog.Kind.COMPLEXES, b.id == 1 ? page - 1 : b.id == 2 ? page + 1 : 0);
            } else if (b.id == 4) editor.openComplex(id.getText(), true);
            else if (b.id == 5) mc.displayGuiScreen(null);
            return;
        }
        if (optionView != null) {
            if (b.id == 223) { optionView = null; selectedBuild = null; initGui(); return; }
            if (b.id == 225) { editor.setPreferredArea(null); optionView = null; initGui(); return; }
            if (b.id == 226) { editor.setConnectionGate(null); optionView = null; initGui(); return; }
            if (b.id == 227) { editor.error("Retained authored gate " + editor.getConnection().getGateRef().get() + ". Validate for unresolved or duplicate usage."); optionView = null; initGui(); return; }
            if (b.id >= 400) {
                if (optionView == KOMETacticalComplexCatalog.Kind.COMPLEXES) {
                    String normal = KOMETacticalComplexDraft.zones(editor.getComplexDraft(), ZoneType.NORMAL).get(accessPage * 5 + b.id - 400).getId();
                    editor.toggleWallAccess(normal); initGui();
                } else {
                    KOMETacticalComplexCatalog.Row row = editor.getOptions().rows.get(b.id - 400);
                    if (optionView == KOMETacticalComplexCatalog.Kind.PREFERRED_AREAS) { editor.setPreferredArea(row.id); optionView = null; initGui(); }
                    else if (optionView == KOMETacticalComplexCatalog.Kind.GATES) { editor.setConnectionGate(row); optionView = null; initGui(); }
                    else { selectedBuild = row; editor.error(row.id + ": " + row.detail + ". Confirm the membership action below."); initGui(); }
                }
                return;
            }
            if (b.id == 222) {
                KOMETacticalEditDraft.MembershipAction action = selectedBuild.relatedId == null ? KOMETacticalEditDraft.MembershipAction.ASSIGN
                    : selectedBuild.relatedId.equals(editor.getTargetId()) ? KOMETacticalEditDraft.MembershipAction.UNASSIGN : KOMETacticalEditDraft.MembershipAction.REASSIGN;
                editor.membership(selectedBuild, action); return;
            }
            if (b.id == 220 || b.id == 221 || b.id == 224) {
                if (optionView == KOMETacticalComplexCatalog.Kind.COMPLEXES) {
                    int size = KOMETacticalComplexDraft.zones(editor.getComplexDraft(), ZoneType.NORMAL).size();
                    int next = b.id == 220 ? accessPage - 1 : b.id == 221 ? accessPage + 1 : 0;
                    if (next >= 0 && next * 5 < size) accessPage = next;
                    initGui();
                } else {
                    KOMETacticalComplexCatalog c = editor.getOptions(); int page = c == null ? 0 : c.page;
                    int next = b.id == 220 ? page - 1 : b.id == 221 ? page + 1 : 0;
                    if (next >= 0 && (c == null || next == 0 || (long) next * 5 < c.total)) {
                        selectedBuild = null; editor.complexPage(optionView, next);
                    }
                }
            }
            return;
        }
        if (connectionView) { connectionAction(b); return; }
        if (b.id >= 300) {
            editor.selectZone(KOMETacticalComplexDraft.zones(editor.getComplexDraft(), editor.getZoneType()).get(zonePage * 3 + b.id - 300).getId());
            geometryView = true; confirmZone = false; initGui(); return;
        }
        if (b.id >= 201 && b.id <= 203) { editor.zoneType(ZoneType.values()[b.id - 201]); zonePage = 0; confirmZone = false; initGui(); }
        else if (b.id == 206) {
            connectionView = true; connectionForm = false; connectionPage = 0;
            if (!editor.isCreating()) editor.complexPage(KOMETacticalComplexCatalog.Kind.CONNECTIONS, 0); initGui();
        }
        else if (b.id == 204 || b.id == 205) {
            optionView = b.id == 204 ? KOMETacticalComplexCatalog.Kind.PREFERRED_AREAS : KOMETacticalComplexCatalog.Kind.BUILDS;
            selectedBuild = null; editor.complexPage(optionView, 0); initGui();
        } else if (b.id == 210 || b.id == 211) {
            int next = b.id == 210 ? zonePage - 1 : zonePage + 1;
            if (next >= 0 && next * 3 < KOMETacticalComplexDraft.zones(editor.getComplexDraft(), editor.getZoneType()).size()) zonePage = next;
            initGui();
        } else if (b.id == 212) { editor.createZone(id.getText()); geometryView = true; initGui(); }
        else if (b.id == 213) {
            if (!confirmZone) { confirmZone = true; editor.error("Remove selected zone from this draft? Click again. Existing references stay inspectable."); ((GuiButton)b).displayString = "Confirm"; }
            else { editor.deleteZone(); confirmZone = false; initGui(); }
        } else if (b.id == 20) editor.validate();
        else if (b.id == 21) editor.save();
        else if (b.id == 22) { editor.cancel(); geometryView = false; optionView = null; initGui(); }
        else if (b.id == 23) {
            if (!confirmDelete) { confirmDelete = true; editor.error("Delete the ENTIRE Siege Complex " + editor.getTargetId() + " and its zones? Confirm. Assigned Builds prevent deletion."); }
            else editor.delete();
        }
    }
    private void drawComplex() {
        if (!editor.isEditing()) {
            KOMETacticalComplexCatalog c = editor.getComplexCatalog();
            if (c == null || c.rows.isEmpty()) drawString(fontRendererObj, "No complexes listed. Refresh or create one.", 16, 62, 0xBBBBBB);
            drawString(fontRendererObj, "New stable complex ID", 16, height - 66, 0xFFFFFF);
            if (id != null) id.drawTextBox(); return;
        }
        KOMESiegeComplex c = editor.getComplexDraft();
        drawString(fontRendererObj, c.getComplexId() + " | rev " + c.getRevision() + " | " + editor.getAssignedBuildCount() + " Builds", 16, 36, 0x80E0FF);
        if (optionView != null) {
            drawString(fontRendererObj, optionView == KOMETacticalComplexCatalog.Kind.COMPLEXES ? "Wall access: select Normal Segments"
                : optionView == KOMETacticalComplexCatalog.Kind.BUILDS ? "Build membership (separate confirmed commit)"
                : optionView == KOMETacticalComplexCatalog.Kind.GATES ? "Assigned Build / gate record (or Gateless below)" : "Preferred tile-owned staging area", 16, 56, 0xFFFFFF);
            if (optionView == KOMETacticalComplexCatalog.Kind.GATES && editor.getOptions() != null
                    && editor.getOptions().kind == optionView && editor.getOptions().total == 0)
                drawString(fontRendererObj, "No eligible gate records. Gateless is legal.", 16, 80, 0xBBBBBB);
        } else if (connectionView) {
            if (connectionChoice != ConnectionChoice.NONE) drawString(fontRendererObj, "Choose " + connectionChoice + " from this complex", 16, 56, 0xFFFFFF);
            else if (connectionForm && editor.getConnection() != null) {
                drawString(fontRendererObj, "Connection " + editor.getConnectionId() + " | " + editor.readinessSummary(), 16, 51, 0xFFFFFF);
                if (editor.getInference() != null) {
                    kome.common.siege.KOMEConnectionEndpointInference.Result suggestion = editor.getInference();
                    drawString(fontRendererObj, fontRendererObj.trimStringToWidth(suggestion.isSuccessful() ? "Proposed: "
                        + suggestion.getEndpointA() + " <-> " + suggestion.getEndpointB() : editor.getMessage(), width - 32), 16, 176, 0xFFD080);
                }
            }
            else { drawString(fontRendererObj, "Connections | " + editor.readinessSummary(), 16, 74, 0xFFFFFF); if (id != null) id.drawTextBox(); }
        } else {
            drawString(fontRendererObj, editor.getZoneType() + " zones | " + editor.readinessSummary(), 16, 74, 0xBBBBBB);
            drawString(fontRendererObj, "Preferred: " + c.getPreferredForceDeploymentAreaId().orElse("none"), 138, 149, 0xBBBBBB);
            if (id != null) id.drawTextBox();
        }
    }
    private void initConnections() {
        if (connectionChoice != ConnectionChoice.NONE) {
            java.util.List<String> choices = connectionChoices();
            for (int i = 0; i < 5 && choicePage * 5 + i < choices.size(); i++) button(650 + i, 16, 75 + i * 20, width - 32,
                fontRendererObj.trimStringToWidth(choices.get(choicePage * 5 + i), width - 48));
            button(655, 16, height - 62, 60, "Previous"); button(656, 82, height - 62, 60, "Next");
            button(657, width - 86, height - 26, 70, "Back"); return;
        }
        if (connectionForm && editor.getConnection() != null) {
            KOMESiegeConnection c = editor.getConnection();
            button(601, 16, 66, width - 32, "Endpoint A: " + c.getEndpointA());
            button(602, 16, 88, width - 32, "Endpoint B: " + c.getEndpointB());
            button(603, 16, 110, width - 32, "Transition: " + c.getTransitionZoneId());
            button(604, 16, 132, width - 32, "Gate: " + (c.isGated() ? c.getGateRef().get().getBuildId() + " / " + c.getGateRef().get().getGateRecordId() : "NONE (Gateless)"));
            int third = (width - 40) / 3;
            button(606, 16, 154, third, "Infer endpoints"); button(607, 20 + third, 154, third, "Accept suggestion"); button(608, 24 + third * 2, 154, third, "Dismiss");
            int size = (width - 48) / 5;
            int[] actions = {609, 611, 20, 21, 610};
            String[] titles = {"Back", "World", "Validate", "Save", confirmConnection ? "Confirm" : "Remove"};
            for (int i = 0; i < actions.length; i++) button(actions[i], 16 + i * (size + 4), height - 26, size, titles[i]);
            return;
        }
        button(609, 16, 54, 70, "Back"); button(612, 92, 54, 90, "Refresh status");
        java.util.List<KOMESiegeConnection> connections = editor.getConnections();
        if (connectionPage * 3 >= connections.size()) connectionPage = 0;
        for (int i = 0; i < 3 && connectionPage * 3 + i < connections.size(); i++) {
            KOMESiegeConnection c = connections.get(connectionPage * 3 + i);
            button(620 + i, 16, 82 + i * 20, width - 32, fontRendererObj.trimStringToWidth(c.getId() + (editor.connectionHasErrors(c) ? " [CHECK] | " : " | ") + c.getEndpointA()
                + " <-> " + c.getEndpointB() + " | " + c.getTransitionZoneId() + " | " + (c.isGated()
                ? c.getGateRef().get().getBuildId() + " / " + c.getGateRef().get().getGateRecordId() : "NONE"), width - 48));
        }
        button(613, 16, 144, 54, "Previous"); button(614, 74, 144, 54, "Next");
        id = new GuiTextField(fontRendererObj, 16, 169, width - 146, 18); id.setMaxStringLength(128);
        button(615, width - 122, 169, 106, "New Connection");
        int size = (width - 40) / 3;
        button(20, 16, height - 26, size, "Validate"); button(21, 20 + size, height - 26, size, "Save"); button(22, 24 + size * 2, height - 26, size, "Cancel");
    }
    private java.util.List<String> connectionChoices() {
        java.util.List<String> result = new java.util.ArrayList<>();
        if (connectionChoice == ConnectionChoice.TRANSITION) for (KOMESiegeZone zone : KOMETacticalComplexDraft.zones(editor.getComplexDraft(), ZoneType.TRANSITION)) {
            boolean valid = zone.getPrism().hasValidYRange() && kome.common.siege.geometry.KOMEPolygonValidator.validate(zone.getPrism().getPolygon(), zone.getId()).isValid();
            long uses = editor.getConnections().stream().filter(c -> c.getTransitionZoneId().equals(zone.getId())).count();
            result.add(zone.getId() + " | " + (valid ? "geometry valid" : "malformed geometry") + " | " + uses + " Connection(s)");
        } else for (KOMESiegeAreaRef ref : editor.endpointChoices()) result.add(ref.toString());
        return result;
    }
    private void connectionAction(GuiButton b) {
        if (b.id >= 650 && b.id <= 654) {
            int index = choicePage * 5 + b.id - 650;
            if (connectionChoice == ConnectionChoice.TRANSITION) editor.setConnectionTransition(KOMETacticalComplexDraft.zones(editor.getComplexDraft(), ZoneType.TRANSITION).get(index).getId());
            else editor.setConnectionEndpoint(connectionChoice == ConnectionChoice.A, editor.endpointChoices().get(index));
            connectionChoice = ConnectionChoice.NONE; initGui(); return;
        }
        if (b.id == 655 || b.id == 656) { int next = choicePage + (b.id == 655 ? -1 : 1); if (next >= 0 && next * 5 < connectionChoices().size()) choicePage = next; initGui(); return; }
        if (b.id == 657) { connectionChoice = ConnectionChoice.NONE; initGui(); return; }
        if (b.id >= 620 && b.id <= 622) {
            int index = connectionPage * 3 + b.id - 620; editor.selectConnection(editor.getConnections().get(index).getId());
            connectionForm = true; confirmConnection = false;
            if (!editor.isCreating()) editor.complexPage(KOMETacticalComplexCatalog.Kind.CONNECTIONS, index / 5); initGui(); return;
        }
        switch (b.id) {
            case 601: case 602: case 603: connectionChoice = b.id == 601 ? ConnectionChoice.A : b.id == 602 ? ConnectionChoice.B : ConnectionChoice.TRANSITION; choicePage = 0; initGui(); break;
            case 604: optionView = KOMETacticalComplexCatalog.Kind.GATES; editor.complexPage(optionView, 0); initGui(); break;
            case 606: editor.inferEndpoints(); updateButtons(); break;
            case 607: editor.acceptInference(); initGui(); break;
            case 608: editor.rejectInference(); updateButtons(); break;
            case 609:
                if (connectionForm) connectionForm = false;
                else { connectionView = false; editor.zoneType(ZoneType.NORMAL); }
                confirmConnection = false; initGui(); break;
            case 610:
                if (!confirmConnection) { confirmConnection = true; editor.error("Remove Connection " + editor.getConnectionId() + " from this draft? Click Confirm remove. Transition and KOM-10 gate remain."); initGui(); }
                else { editor.deleteConnection(); connectionForm = false; confirmConnection = false; initGui(); } break;
            case 611: editor.select(Selection.TOPOLOGY); editor.error("Connection preview: yellow selected, cyan gated, white gateless, red invalid. Enter/Esc returns to editor."); mc.displayGuiScreen(null); break;
            case 612: if (!editor.isCreating()) editor.complexPage(KOMETacticalComplexCatalog.Kind.CONNECTIONS, connectionPage * 3 / 5); break;
            case 613: case 614: int next = connectionPage + (b.id == 613 ? -1 : 1); if (next >= 0 && next * 3 < editor.getConnections().size()) connectionPage = next; initGui(); break;
            case 615: editor.createConnection(id.getText()); connectionForm = true; confirmConnection = false; initGui(); break;
            case 20: editor.validate(); break;
            case 21: editor.save(); break;
            case 22: editor.cancel(); connectionView = connectionForm = false; initGui(); break;
            default: break;
        }
    }
    @Override public boolean doesGuiPauseGame() { return false; }
    @Override public void onGuiClosed() { Keyboard.enableRepeatEvents(false); }
}
