package kome.client.tactical;

import java.lang.reflect.*;
import java.util.*;
import kome.client.gui.KOMEGuiTacticalAreaEditor;
import kome.common.KOMEAccessFixture;
import kome.common.siege.*;
import kome.common.tactical.edit.*;
import net.minecraft.client.gui.*;
import org.lwjgl.input.Keyboard;
import org.junit.*;
import static org.junit.Assert.*;

/** Actual shared screen controls with inert fonts; no rendering/window is exercised. */
public class KOMETacticalEditorGuiTest {
    private List<KOMETacticalEditRequest> requests; private KOMETacticalAreaEditor editor; private KOMEGuiTacticalAreaEditor screen;
    @Before public void setup() throws Exception {
        requests = new ArrayList<KOMETacticalEditRequest>(); editor = new KOMETacticalAreaEditor(requests::add);
        editor.acceptCatalog(new KOMETacticalAreaCatalog("T100", -1, 10, 0, 0, Collections.emptyList()));
        screen = new KOMEGuiTacticalAreaEditor(editor); screen.width = 320; screen.height = 240;
        Field font = GuiScreen.class.getDeclaredField("fontRendererObj"); font.setAccessible(true); font.set(screen, KOMEAccessFixture.allocate(InertFont.class));
        screen.initGui();
    }
    @After public void cleanup() { screen.onGuiClosed(); }
    private List<GuiButton> buttons() throws Exception {
        Field field = GuiScreen.class.getDeclaredField("buttonList"); field.setAccessible(true);
        return (List<GuiButton>) field.get(screen);
    }
    private GuiButton button(int id) throws Exception { for (GuiButton b : buttons()) if (b.id == id) return b; throw new AssertionError("Missing button " + id); }
    private int hoveredRow(int x, int y, int firstButtonId, int rowCount) throws Exception {
        Method hover = KOMEGuiTacticalAreaEditor.class.getDeclaredMethod("hoveredRowIndex", int.class, int.class, int.class, int.class);
        hover.setAccessible(true);
        return (Integer) hover.invoke(screen, x, y, firstButtonId, rowCount);
    }
    private int connectionTooltipIndex(int x, int y) throws Exception {
        Method hover = KOMEGuiTacticalAreaEditor.class.getDeclaredMethod("connectionTooltipIndex", int.class, int.class);
        hover.setAccessible(true);
        return (Integer) hover.invoke(screen, x, y);
    }
    private void assertTooltipMatchesRowButton(int buttonId, int firstButtonId) throws Exception {
        GuiButton row = button(buttonId);
        int y = row.yPosition + row.height / 2;
        assertEquals(buttonId - firstButtonId, hoveredRow(row.xPosition + row.width / 2, y, firstButtonId, 1));
        assertEquals(-1, hoveredRow(row.xPosition - 1, y, firstButtonId, 1));
        assertEquals(-1, hoveredRow(row.xPosition + row.width, y, firstButtonId, 1));
        assertEquals(-1, hoveredRow(row.xPosition + row.width / 2, row.yPosition + row.height, firstButtonId, 1));
    }
    private void click(int id) throws Exception {
        GuiButton b = button(id); assertTrue("Button disabled " + id, b.enabled);
        Method action = KOMEGuiTacticalAreaEditor.class.getDeclaredMethod("actionPerformed", GuiButton.class); action.setAccessible(true); action.invoke(screen, b);
        screen.updateScreen();
    }
    private void text(String name, String value) throws Exception { Field field = KOMEGuiTacticalAreaEditor.class.getDeclaredField(name); field.setAccessible(true); ((GuiTextField) field.get(screen)).setText(value); }
    private String fieldText(String name) throws Exception { Field field = KOMEGuiTacticalAreaEditor.class.getDeclaredField(name); field.setAccessible(true); return ((GuiTextField) field.get(screen)).getText(); }
    private GuiTextField field(String name) throws Exception { Field value = KOMEGuiTacticalAreaEditor.class.getDeclaredField(name); value.setAccessible(true); return (GuiTextField) value.get(screen); }
    private void focus(String name) throws Exception {
        GuiTextField target = field(name);
        Method mouse = KOMEGuiTacticalAreaEditor.class.getDeclaredMethod("mouseClicked", int.class, int.class, int.class);
        mouse.setAccessible(true); mouse.invoke(screen, target.xPosition + 4, target.yPosition + 4, 0);
    }
    private void type(char character, int key) throws Exception {
        Method typed = KOMEGuiTacticalAreaEditor.class.getDeclaredMethod("keyTyped", char.class, int.class);
        typed.setAccessible(true); typed.invoke(screen, character, key);
    }
    private void enterWorldView() throws Exception {
        Method enter = KOMEGuiTacticalAreaEditor.class.getDeclaredMethod("enterWorldView", boolean.class);
        enter.setAccessible(true); enter.invoke(screen, false);
    }
    private void enterVertexWorldView() throws Exception {
        Method enter = KOMEGuiTacticalAreaEditor.class.getDeclaredMethod("enterWorldView", boolean.class);
        enter.setAccessible(true); enter.invoke(screen, true);
    }
    @Test public void editVerticesControlStartsWithCandidateWhileEscapeViewIsPassive() throws Exception {
        openArea();
        enterWorldView();
        assertFalse(editor.isWorldVertexPlacementActive());
        assertSame(screen, editor.endWorldView());
        Method enter = KOMEGuiTacticalAreaEditor.class.getDeclaredMethod("enterWorldView", boolean.class);
        enter.setAccessible(true); enter.invoke(screen, true);
        assertTrue(editor.isWorldVertexPlacementActive());
        assertEquals("Edit Vertices in World", button(10).displayString);
        editor.endWorldView();
    }
    private void assertButtonsFitWithoutOverlap() throws Exception {
        List<GuiButton> visible = buttons();
        for (int i = 0; i < visible.size(); i++) {
            GuiButton a = visible.get(i);
            assertTrue(a.xPosition >= 0 && a.yPosition >= 0 && a.xPosition + a.width <= screen.width && a.yPosition + a.height <= screen.height);
            for (int j = i + 1; j < visible.size(); j++) {
                GuiButton b = visible.get(j);
                assertFalse("Overlapping buttons " + a.id + " and " + b.id,
                    a.xPosition < b.xPosition + b.width && b.xPosition < a.xPosition + a.width
                        && a.yPosition < b.yPosition + b.height && b.yPosition < a.yPosition + a.height);
            }
        }
    }
    @Test public void compactControlsAndMenusFitAt320And280WithoutOverlaps() throws Exception {
        for (int width : new int[] {320, 280, 800}) {
            screen.width = width; screen.height = 240; screen.initGui(); assertButtonsFitWithoutOverlap();
            for (GuiButton b : buttons()) assertTrue("Oversized control " + b.id, b.width <= 200);
        }
        openArea(); assertButtonsFitWithoutOverlap();
        assertEquals(78, field("bottom").width); assertEquals(78, field("top").width);
        assertTrue(field("bottom").xPosition + field("bottom").width < button(30).xPosition);
        assertTrue(field("top").xPosition + field("top").width < button(32).xPosition);
        click(30); assertEquals("59", fieldText("bottom"));
    }
    @Test public void complexTabsCountAuthorizedSavedGeometryOnly() throws Exception {
        click(7); editor.acceptComplexCatalog(new KOMETacticalComplexCatalog(KOMETacticalComplexCatalog.Kind.COMPLEXES,
            "T100", null, -1, 10, 0, 0, Collections.emptyList())); screen.updateScreen();
        KOMESiegeComplex saved = KOMESiegeReadinessFixtures.minimal("FORT", "T100", -1, null);
        editor.openComplex("FORT", false);
        editor.accept(new KOMETacticalEditSnapshot(UUID.randomUUID(), UUID.randomUUID(), new KOMETacticalEditScope(
            KOMETacticalEditScope.Type.SIEGE_COMPLEX, "T100", "FORT", "FORT", -1), 1, 1, 10, saved.getRevision(), 0, 10,
            false, new KOMETacticalEditDraft(saved), null), KOMETacticalEditSessionManager.Status.OPENED); screen.updateScreen();
        assertEquals("Normals (1)", button(201).displayString); assertEquals("Walls (0)", button(202).displayString);
        assertTrue(button(203).displayString.endsWith("(1)")); assertEquals("Links (1)", button(206).displayString);
        assertButtonsFitWithoutOverlap();
        assertEquals(button(201).yPosition, button(203).yPosition);
        assertTrue(button(204).yPosition > button(201).yPosition);
        screen.width = 800; screen.initGui(); assertButtonsFitWithoutOverlap();
        int singleRowY = button(201).yPosition;
        for (int i = 201; i <= 206; i++) {
            assertEquals("Category should stay on one row: " + i, singleRowY, button(i).yPosition);
            assertTrue("Category label should fit: " + i, button(i).displayString.length() * 6 <= button(i).width - 6);
        }
        assertEquals(82, button(300).yPosition);
        screen.width = 320; screen.initGui(); click(202); text("id", "W"); click(212); click(15);
        assertEquals("Walls (0)", button(202).displayString); assertEquals(1, editor.getComplexDraft().getWallZones().size());
        assertButtonsFitWithoutOverlap();
    }
    @Test public void complexListTooltipUsesActualCenteredRowAtAllWidths() throws Exception {
        click(7);
        editor.acceptComplexCatalog(new KOMETacticalComplexCatalog(KOMETacticalComplexCatalog.Kind.COMPLEXES,
            "T100", null, -1, 10, 0, 1, Collections.singletonList(
                new KOMETacticalComplexCatalog.Row("FORT", "", "READY", null, 1, 0))));
        screen.updateScreen();
        for (int width : new int[] {280, 320, 800}) {
            screen.width = width; screen.initGui();
            assertTooltipMatchesRowButton(100, 100);
            assertEquals(-1, hoveredRow(20, 60, 100, 1));
        }
    }
    @Test public void optionListTooltipUsesActualCenteredRowAtAllWidths() throws Exception {
        openEmpty(); click(204);
        editor.acceptComplexCatalog(new KOMETacticalComplexCatalog(KOMETacticalComplexCatalog.Kind.PREFERRED_AREAS,
            "T100", "FORT", -1, 10, 0, 1, Collections.singletonList(
                new KOMETacticalComplexCatalog.Row("FIELD", "Field", "READY", null, 1, 0))));
        screen.updateScreen();
        for (int width : new int[] {280, 320, 800}) {
            screen.width = width; screen.initGui();
            assertTooltipMatchesRowButton(400, 400);
            assertEquals(-1, hoveredRow(20, 80, 400, 1));
        }
    }
    @Test public void wideComplexMenuShowsThreeZonesAndNarrowGeometryControlsStillFit() throws Exception {
        openEmpty();
        editor.createZone("N1"); editor.createZone("N2"); editor.createZone("N3");
        screen.width = 800; screen.initGui(); assertButtonsFitWithoutOverlap();
        assertEquals(button(201).yPosition, button(206).yPosition);
        assertEquals(82, button(300).yPosition); assertEquals(122, button(302).yPosition);
        click(302); assertEquals("N3", editor.getZoneId());
        assertEquals("64", fieldText("bottom")); assertEquals("70", fieldText("top"));
        for (int width : new int[] {320, 280, 800}) {
            screen.width = width; screen.initGui(); assertButtonsFitWithoutOverlap();
            assertTrue(field("bottom").xPosition + field("bottom").width < button(30).xPosition);
            assertTrue(field("top").xPosition + field("top").width < button(32).xPosition);
        }
    }
    @Test public void rejectedSegmentSaveKeepsItsDraftAndGeometryScreen() throws Exception {
        openEmpty(); text("id", "N"); click(212); editor.addVertex(2, 3);
        click(21); KOMESiegeComplex unsaved = editor.getComplexDraft();
        KOMETacticalEditRequest update = requests.get(0); assertEquals(KOMETacticalEditRequest.Action.UPDATE, update.getAction());
        editor.accept(new KOMETacticalEditSnapshot(UUID.randomUUID(), update.getToken(), update.getScope(),
            1, 2, 10, unsaved.getRevision(), 0, 10, false, new KOMETacticalEditDraft(unsaved), null),
            KOMETacticalEditSessionManager.Status.STALE_STORE); screen.updateScreen();
        assertSame(unsaved, editor.getComplexDraft()); assertNotNull(button(10));
        assertTrue(editor.getMessage().contains("Cancel, refresh and reopen"));
        assertTrue(requests.stream().noneMatch(r -> r.getAction() == KOMETacticalEditRequest.Action.OPEN));
    }
    private void openArea() throws Exception {
        text("id", "FIELD"); click(4);
        kome.common.tactical.KOMEForceDeploymentArea area = new kome.common.tactical.KOMEForceDeploymentArea("FIELD", "T100", -1, "Original",
            new kome.common.siege.geometry.KOMEPolygonPrism(new kome.common.siege.geometry.KOMEPolygon(Collections.emptyList()), 60, 71), 0);
        editor.accept(new KOMETacticalEditSnapshot(UUID.randomUUID(), UUID.randomUUID(), new KOMETacticalEditScope(
            KOMETacticalEditScope.Type.TILE_FORCE_DEPLOYMENT_AREA, "T100", null, "FIELD", -1),
            1, 1, 10, 0, 0, 10, false, new KOMETacticalEditDraft(area), null), KOMETacticalEditSessionManager.Status.OPENED);
        screen.updateScreen(); requests.clear();
    }
    @Test public void expiredDraftHasReadOnlyInspectorAndExplicitDiscard() throws Exception {
        openArea(); editor.addVertex(6, 8); editor.setLabel("Unsent field");
        kome.common.tactical.KOMEForceDeploymentArea local = editor.getDraft();
        Field state = KOMETacticalAreaEditor.class.getDeclaredField("snapshot"); state.setAccessible(true);
        KOMETacticalEditSnapshot opened = (KOMETacticalEditSnapshot) state.get(editor);
        editor.accept(new KOMETacticalEditSnapshot(opened.getPlayerId(), opened.getToken(), opened.getScope(),
            opened.getGeneration(), 2, opened.getBaseRevision(), opened.getBaseObjectRevision(), opened.getDraftSequence(),
            opened.getCurrentRevision(), true, opened.getDraft(), null), KOMETacticalEditSessionManager.Status.EXPIRED);
        screen.updateScreen(); assertSame(local, editor.getDraft()); assertNotNull(button(700)); assertNotNull(button(701));
        for (int width : new int[] {280, 320, 800}) { screen.width = width; screen.initGui(); assertButtonsFitWithoutOverlap(); }
        assertNull(field("label"));
        type('x', Keyboard.KEY_X); assertSame(local, editor.getDraft());
        click(701); assertTrue(editor.hasRetainedDraft()); assertEquals("Confirm Discard", button(701).displayString);
        click(701); assertFalse(editor.hasRetainedDraft()); assertNull(editor.getDraft());
    }
    @Test public void expirationRetainsTypedUnappliedLabelAndMalformedYThroughAuthorizedRecovery() throws Exception {
        openArea(); editor.addVertex(6, 8);
        kome.common.tactical.KOMEForceDeploymentArea model = editor.getDraft();
        text("label", "Typed label"); focus("label"); type('x', Keyboard.KEY_X);
        String typedLabel = fieldText("label");
        text("bottom", "-"); focus("bottom"); type('q', Keyboard.KEY_Q);
        String typedBottom = fieldText("bottom");
        text("top", "70bad"); focus("top"); type('9', Keyboard.KEY_9);
        String typedTop = fieldText("top");
        assertNotEquals(model.getLabel(), typedLabel);
        Field state = KOMETacticalAreaEditor.class.getDeclaredField("snapshot"); state.setAccessible(true);
        KOMETacticalEditSnapshot opened = (KOMETacticalEditSnapshot) state.get(editor);
        editor.accept(new KOMETacticalEditSnapshot(opened.getPlayerId(), opened.getToken(), opened.getScope(),
            opened.getGeneration(), 2, opened.getBaseRevision(), opened.getBaseObjectRevision(), opened.getDraftSequence(),
            opened.getCurrentRevision(), true, opened.getDraft(), null), KOMETacticalEditSessionManager.Status.EXPIRED);
        screen.updateScreen();
        assertSame(model, editor.getDraft()); assertTrue(editor.hasRetainedDraft());
        assertEquals("Original", editor.getDraft().getLabel());
        assertTrue(editor.retainedInspectionLines().contains("Typed label text: " + typedLabel));
        assertTrue(editor.retainedInspectionLines().contains("Typed Min Y text: " + typedBottom));
        assertTrue(editor.retainedInspectionLines().contains("Typed Max Y text: " + typedTop));
        Method inspector = KOMEGuiTacticalAreaEditor.class.getDeclaredMethod("inspectionLines"); inspector.setAccessible(true);
        @SuppressWarnings("unchecked") List<String> visible = (List<String>) inspector.invoke(screen);
        assertTrue(String.join("", visible).contains("Typed label text: " + typedLabel));
        assertTrue(String.join("", visible).contains("Typed Min Y text: " + typedBottom));
        assertTrue(String.join("", visible).contains("Typed Max Y text: " + typedTop));
        editor.recoverRetainedDraft(opened.getPlayerId(), -1);
        editor.accept(new KOMETacticalEditSnapshot(opened.getPlayerId(), UUID.randomUUID(), opened.getScope(),
            opened.getGeneration() + 1, 3, opened.getBaseRevision(), opened.getBaseObjectRevision(), 0,
            opened.getCurrentRevision(), false, opened.getDraft(), null), KOMETacticalEditSessionManager.Status.OPENED);
        screen.updateScreen();
        assertEquals(typedLabel, fieldText("label")); assertEquals(typedBottom, fieldText("bottom"));
        assertEquals(typedTop, fieldText("top")); assertSame(model, editor.getDraft());
        text("top", "73stillbad"); screen.initGui(); assertEquals("73stillbad", fieldText("top"));
        click(20); assertTrue(editor.getMessage().contains("whole block")); assertTrue(requests.stream().noneMatch(r -> r.getAction() == KOMETacticalEditRequest.Action.UPDATE));
        text("bottom", "61"); text("top", "73"); click(20);
        assertEquals(61, editor.getGeometry().getMinYInclusive()); assertEquals(74, editor.getGeometry().getMaxYExclusive());
        assertEquals(typedLabel, editor.getGeometryLabel());
    }
    @Test public void conflictingRecoveryKeepsTypedTextAndIntentionalDiscardClearsIt() throws Exception {
        openArea(); text("label", "Unapplied conflict label"); text("bottom", "-oops"); text("top", "70");
        Field state = KOMETacticalAreaEditor.class.getDeclaredField("snapshot"); state.setAccessible(true);
        KOMETacticalEditSnapshot opened = (KOMETacticalEditSnapshot) state.get(editor);
        editor.accept(new KOMETacticalEditSnapshot(opened.getPlayerId(), opened.getToken(), opened.getScope(),
            opened.getGeneration(), 2, opened.getBaseRevision(), opened.getBaseObjectRevision(), opened.getDraftSequence(),
            opened.getCurrentRevision(), true, opened.getDraft(), null), KOMETacticalEditSessionManager.Status.EXPIRED);
        screen.updateScreen(); editor.recoverRetainedDraft(opened.getPlayerId(), -1);
        UUID fresh = UUID.randomUUID();
        editor.accept(new KOMETacticalEditSnapshot(opened.getPlayerId(), fresh, opened.getScope(),
            opened.getGeneration() + 1, 3, opened.getBaseRevision() + 1, opened.getBaseObjectRevision(), 0,
            opened.getCurrentRevision() + 1, false, opened.getDraft(), null), KOMETacticalEditSessionManager.Status.OPENED);
        assertTrue(editor.hasRetainedDraft()); assertFalse(editor.isEditing());
        assertTrue(editor.retainedInspectionLines().contains("Typed label text: Unapplied conflict label"));
        assertTrue(editor.retainedInspectionLines().contains("Typed Min Y text: -oops"));
        editor.accept(new KOMETacticalEditSnapshot(opened.getPlayerId(), fresh, opened.getScope(),
            opened.getGeneration() + 1, 4, opened.getBaseRevision() + 1, opened.getBaseObjectRevision(), 0,
            opened.getCurrentRevision() + 1, true, opened.getDraft(), null), KOMETacticalEditSessionManager.Status.CANCELLED);
        screen.updateScreen(); click(701); click(701);
        assertFalse(editor.hasRetainedDraft()); assertNull(editor.getUnappliedFields());
    }
    @Test public void complexZoneUnappliedTextSurvivesExpirationWithoutChangingGeometry() throws Exception {
        openEmpty(); text("id", "N"); click(212);
        text("label", "Pending normal"); text("bottom", "sixty"); text("top", "70-");
        Field state = KOMETacticalAreaEditor.class.getDeclaredField("snapshot"); state.setAccessible(true);
        KOMETacticalEditSnapshot opened = (KOMETacticalEditSnapshot) state.get(editor);
        KOMESiegeComplex model = editor.getComplexDraft();
        editor.accept(new KOMETacticalEditSnapshot(opened.getPlayerId(), opened.getToken(), opened.getScope(),
            opened.getGeneration(), 2, opened.getBaseRevision(), opened.getBaseObjectRevision(), opened.getDraftSequence(),
            opened.getCurrentRevision(), true, opened.getDraft(), null), KOMETacticalEditSessionManager.Status.EXPIRED);
        screen.updateScreen();
        assertSame(model, editor.getComplexDraft()); assertEquals("N", editor.getZoneId());
        assertTrue(editor.retainedInspectionLines().contains("Typed label text: Pending normal"));
        assertTrue(editor.retainedInspectionLines().contains("Typed Min Y text: sixty"));
        assertTrue(editor.retainedInspectionLines().contains("Typed Max Y text: 70-"));
        assertEquals(64, editor.getZone().getPrism().getMinYInclusive());
    }
    @Test public void closingGuiBeforeNextTickStillCapturesExpiredUnappliedText() throws Exception {
        openArea(); text("label", "Text before GUI closes"); text("bottom", "-");
        Field state = KOMETacticalAreaEditor.class.getDeclaredField("snapshot"); state.setAccessible(true);
        KOMETacticalEditSnapshot opened = (KOMETacticalEditSnapshot) state.get(editor);
        editor.accept(new KOMETacticalEditSnapshot(opened.getPlayerId(), opened.getToken(), opened.getScope(),
            opened.getGeneration(), 2, opened.getBaseRevision(), opened.getBaseObjectRevision(), opened.getDraftSequence(),
            opened.getCurrentRevision(), true, opened.getDraft(), null), KOMETacticalEditSessionManager.Status.EXPIRED);
        screen.onGuiClosed();
        assertTrue(editor.retainedInspectionLines().contains("Typed label text: Text before GUI closes"));
        assertTrue(editor.retainedInspectionLines().contains("Typed Min Y text: -"));
        screen = new KOMEGuiTacticalAreaEditor(editor); screen.width = 320; screen.height = 240;
        Field font = GuiScreen.class.getDeclaredField("fontRendererObj"); font.setAccessible(true);
        font.set(screen, KOMEAccessFixture.allocate(InertFont.class)); screen.initGui();
        assertNotNull(button(700)); assertTrue(editor.hasRetainedDraft());
    }
    @Test public void explicitCancelDiscardsUnappliedTextAndSaveUsesNormalFieldsWorkflow() throws Exception {
        openArea(); text("label", "Cancel this text"); text("bottom", "bad"); click(22);
        assertNull(editor.getDraft()); assertNull(editor.getUnappliedFields()); assertFalse(editor.hasRetainedDraft());
        openArea(); text("label", "Save this text"); text("bottom", "61"); text("top", "72"); click(21);
        assertEquals("Save this text", editor.getDraft().getLabel());
        assertEquals(61, editor.getGeometry().getMinYInclusive()); assertEquals(73, editor.getGeometry().getMaxYExclusive());
        assertNull(editor.getUnappliedFields());
        assertEquals(KOMETacticalEditRequest.Action.UPDATE, requests.get(0).getAction());
    }
    @Test public void savePendingLocksFocusedAndNewlyFocusedGeometryFieldsUntilFreshSession() throws Exception {
        openArea(); text("label", "Draft"); focus("label");
        type('x', Keyboard.KEY_X); assertNotEquals("Draft", fieldText("label"));
        text("label", "Draft"); click(21);
        KOMETacticalEditRequest update = requests.get(0);
        KOMETacticalEditDraft captured = KOMETacticalEditWire.decodeDraft(update.getPayload());
        assertEquals("Draft", captured.getArea().getLabel());
        String labelDuringSave = fieldText("label"), topDuringSave = fieldText("top");
        type('x', Keyboard.KEY_X); assertEquals(labelDuringSave, fieldText("label"));
        focus("top"); type('9', Keyboard.KEY_9); assertEquals(topDuringSave, fieldText("top"));
        assertTrue(editor.getMessage().contains("Waiting"));
        UUID player = UUID.randomUUID(), token = update.getToken();
        KOMETacticalEditScope scope = update.getScope();
        editor.accept(new KOMETacticalEditSnapshot(player, token, scope, 1, 2, 10, 0, 1, 10,
            false, captured, null), KOMETacticalEditSessionManager.Status.UPDATED);
        assertEquals("Draft", KOMETacticalEditWire.decodeDraft(update.getPayload()).getArea().getLabel());
        editor.accept(new KOMETacticalEditSnapshot(player, token, scope, 1, 3, 10, 0, 1, 11,
            true, captured, null), KOMETacticalEditSessionManager.Status.SAVED);
        screen.updateScreen();
        type('x', Keyboard.KEY_X); assertEquals(labelDuringSave, fieldText("label"));
        kome.common.tactical.KOMEForceDeploymentArea saved = new kome.common.tactical.KOMEForceDeploymentArea("FIELD", "T100", -1,
            "Draft", captured.getArea().getPrism(), 1);
        editor.accept(new KOMETacticalEditSnapshot(player, UUID.randomUUID(), scope, 2, 4, 11, 1, 0, 11,
            false, new KOMETacticalEditDraft(saved), null), KOMETacticalEditSessionManager.Status.OPENED);
        screen.updateScreen();
        focus("label"); String restored = fieldText("label"); type('y', Keyboard.KEY_Y);
        assertNotEquals(restored, fieldText("label"));
        focus("top"); String restoredTop = fieldText("top"); type('8', Keyboard.KEY_8);
        assertNotEquals(restoredTop, fieldText("top"));
    }
    @Test public void failedRenewalLeavesNoEditableGeometryFields() throws Exception {
        openArea(); click(21);
        KOMETacticalEditRequest update = requests.get(0);
        KOMETacticalEditDraft captured = KOMETacticalEditWire.decodeDraft(update.getPayload());
        UUID player = UUID.randomUUID(), token = update.getToken();
        KOMETacticalEditScope scope = update.getScope();
        editor.accept(new KOMETacticalEditSnapshot(player, token, scope, 1, 2, 10, 0, 1, 10,
            false, captured, null), KOMETacticalEditSessionManager.Status.UPDATED);
        editor.accept(new KOMETacticalEditSnapshot(player, token, scope, 1, 3, 10, 0, 1, 11,
            true, captured, null), KOMETacticalEditSessionManager.Status.SAVED);
        editor.accept(null, KOMETacticalEditSessionManager.Status.DENIED); screen.updateScreen();
        assertFalse(editor.isEditing()); assertNull(editor.getDraft());
        assertTrue(editor.getMessage().contains("could not reopen"));
        assertTrue(buttons().stream().noneMatch(b -> b.id == 10 || b.id == 21));
        assertThrows(IllegalArgumentException.class, () -> editor.setLabel("Forbidden"));
    }
    @Test public void complexGeometryFieldsIgnoreKeyboardAndFocusWhileSaveIsPending() throws Exception {
        openEmpty(); text("id", "ZONE"); click(212);
        text("label", "Unsaved zone"); focus("bottom"); click(21);
        KOMETacticalEditDraft captured = KOMETacticalEditWire.decodeDraft(requests.get(0).getPayload());
        assertEquals("Unsaved zone", captured.getComplex().findNormalSegment("ZONE").getLabel());
        String bottomBefore = fieldText("bottom"), labelBefore = fieldText("label");
        type('\b', Keyboard.KEY_BACK); focus("label"); type('x', Keyboard.KEY_X);
        assertEquals(bottomBefore, fieldText("bottom")); assertEquals(labelBefore, fieldText("label"));
        assertEquals("Unsaved zone", KOMETacticalEditWire.decodeDraft(requests.get(0).getPayload())
            .getComplex().findNormalSegment("ZONE").getLabel());
    }
    @Test public void acknowledgedAreaSaveStaysOnGeometryAndRejectsOldAcknowledgment() throws Exception {
        openArea(); text("label", "Saved area"); click(21);
        KOMETacticalEditRequest update = requests.get(0);
        KOMETacticalEditDraft draft = KOMETacticalEditWire.decodeDraft(update.getPayload());
        UUID player = UUID.randomUUID(), oldToken = update.getToken();
        KOMETacticalEditScope scope = update.getScope();
        editor.accept(new KOMETacticalEditSnapshot(player, oldToken, scope, 1, 2, 10, 0, 1, 10,
            false, draft, null), KOMETacticalEditSessionManager.Status.UPDATED);
        assertEquals(KOMETacticalEditRequest.Action.SAVE, requests.get(1).getAction());
        KOMETacticalEditSnapshot closed = new KOMETacticalEditSnapshot(player, oldToken, scope, 1, 3, 10, 0, 1, 11,
            true, draft, null);
        editor.accept(closed, KOMETacticalEditSessionManager.Status.SAVED); screen.updateScreen();
        assertEquals(KOMETacticalEditRequest.Action.OPEN, requests.get(2).getAction());
        assertEquals("FIELD", requests.get(2).getScope().getTargetId());
        assertFalse(button(21).enabled);
        kome.common.tactical.KOMEForceDeploymentArea saved = new kome.common.tactical.KOMEForceDeploymentArea("FIELD", "T100", -1,
            "Saved area", draft.getArea().getPrism(), 1);
        UUID newToken = UUID.randomUUID();
        editor.accept(new KOMETacticalEditSnapshot(player, newToken, scope, 2, 4, 11, 1, 0, 11,
            false, new KOMETacticalEditDraft(saved), null), KOMETacticalEditSessionManager.Status.OPENED);
        screen.updateScreen();
        assertNotNull(button(10)); assertEquals("Saved area", fieldText("label"));
        assertTrue(editor.getMessage().contains("Saved"));
        editor.accept(closed, KOMETacticalEditSessionManager.Status.SAVED);
        assertEquals("FIELD", editor.getTargetId()); assertTrue(editor.isEditing());
        click(30); click(21);
        KOMETacticalEditRequest continued = requests.get(3);
        assertEquals(newToken, continued.getToken()); assertEquals(0, continued.getExpectedSequence());
        assertEquals(1, KOMETacticalEditWire.decodeDraft(continued.getPayload()).getArea().getRevision());
    }
    @Test public void backProtectsUnsavedAreaWhileCancelExplicitlyDiscardsIt() throws Exception {
        openArea(); text("label", "Pending label"); click(15);
        assertNotNull(button(10)); assertEquals("Pending label", editor.getDraft().getLabel());
        assertTrue(editor.getMessage().contains("Unsaved"));
        assertTrue(requests.stream().noneMatch(r -> r.getAction() == KOMETacticalEditRequest.Action.CANCEL));
        click(22); assertNull(editor.getDraft());
        assertEquals(KOMETacticalEditRequest.Action.CANCEL, requests.get(0).getAction());
        assertTrue(requests.stream().noneMatch(r -> r.getAction() == KOMETacticalEditRequest.Action.DELETE));
    }
    @Test public void cleanAreaBackReturnsToBrowserAfterCancellationAcknowledgment() throws Exception {
        openArea(); editor.cancel(); screen.updateScreen();
        KOMETacticalEditScope scope = new KOMETacticalEditScope(KOMETacticalEditScope.Type.TILE_FORCE_DEPLOYMENT_AREA,
            "T100", null, "FIELD", -1);
        kome.common.tactical.KOMEForceDeploymentArea saved = new kome.common.tactical.KOMEForceDeploymentArea("FIELD", "T100", -1,
            "Persisted", new kome.common.siege.geometry.KOMEPolygonPrism(
                new kome.common.siege.geometry.KOMEPolygon(Collections.emptyList()), 60, 71), 1);
        editor.open("FIELD", false); requests.clear();
        UUID player = UUID.randomUUID(), token = UUID.randomUUID();
        editor.accept(new KOMETacticalEditSnapshot(player, token, scope, 2, 2, 11, 1, 0, 11, false,
            new KOMETacticalEditDraft(saved), null), KOMETacticalEditSessionManager.Status.OPENED);
        screen.updateScreen(); click(15);
        assertEquals(KOMETacticalEditRequest.Action.CANCEL, requests.get(0).getAction());
        assertTrue(editor.isBusy());
        editor.accept(new KOMETacticalEditSnapshot(player, token, scope, 2, 3, 11, 1, 0, 11, true,
            new KOMETacticalEditDraft(saved), null), KOMETacticalEditSessionManager.Status.CANCELLED);
        assertEquals(KOMETacticalEditRequest.Action.BROWSE, requests.get(1).getAction());
        editor.acceptCatalog(new KOMETacticalAreaCatalog("T100", -1, 11, 0, 1, Collections.emptyList()));
        screen.updateScreen(); assertNull(editor.getDraft()); assertNotNull(button(4));
        assertTrue(requests.stream().noneMatch(r -> r.getAction() == KOMETacticalEditRequest.Action.DELETE));
    }
    @Test public void bottomGeometryNavigationFitsAllSupportedWidths() throws Exception {
        openArea();
        for (int width : new int[] {280, 320, 800}) {
            screen.width = width; screen.initGui(); assertButtonsFitWithoutOverlap();
            for (int id : new int[] {20, 21, 15, 22, 23}) assertEquals(screen.height - 26, button(id).yPosition);
            assertTrue(button(21).xPosition + button(21).width < button(15).xPosition);
            assertTrue(button(15).xPosition + button(15).width < button(22).xPosition);
        }
    }
    @Test public void heightButtonsNudgeBothBoundsAndSynchronizeDraftAndFields() throws Exception {
        openArea(); editor.addVertex(4, 9); text("label", "Unsaved label");
        KOMETacticalAreaEditor.GeometryFeedback beforeNudge = editor.geometryFeedback();
        click(30); assertEquals(59, editor.getGeometry().getMinYInclusive()); assertEquals("59", fieldText("bottom"));
        assertNotSame(beforeNudge, editor.geometryFeedback());
        click(31); assertEquals(60, editor.getGeometry().getMinYInclusive());
        click(32); assertEquals(69, editor.getGeometry().getMaxYInclusive()); assertEquals(70, editor.getGeometry().getMaxYExclusive());
        click(33); assertEquals(70, editor.getGeometry().getMaxYInclusive()); assertEquals(71, editor.getGeometry().getMaxYExclusive());
        assertEquals("70", fieldText("top")); assertEquals("Unsaved label", fieldText("label"));
        assertEquals(new kome.common.siege.geometry.KOMEXZPoint(4, 9), editor.getConfirmedVertices().get(0));
        click(21); assertEquals(KOMETacticalEditRequest.Action.UPDATE, requests.get(0).getAction());
        KOMETacticalEditDraft sent = KOMETacticalEditWire.decodeDraft(requests.get(0).getPayload());
        assertEquals("Unsaved label", sent.getArea().getLabel()); assertEquals(71, sent.getArea().getPrism().getMaxYExclusive());
    }
    @Test public void malformedHeightTextDoesNotPartiallyApplyLabelOrNudge() throws Exception {
        openArea(); text("label", "Should remain pending"); text("top", "oops");
        click(30); assertEquals(60, editor.getGeometry().getMinYInclusive());
        assertEquals("Original", editor.getDraft().getLabel()); assertEquals("oops", fieldText("top"));
        assertTrue(editor.getMessage().contains("whole block"));
        click(20); assertEquals("Original", editor.getDraft().getLabel()); assertTrue(requests.isEmpty());
    }
    @Test public void boundaryOverflowAndReversedRangeRemainExplicit() throws Exception {
        openArea(); text("top", Integer.toString(Integer.MAX_VALUE - 2)); click(33);
        assertEquals(Integer.MAX_VALUE, editor.getGeometry().getMaxYExclusive());
        assertEquals(Integer.toString(Integer.MAX_VALUE - 1), fieldText("top")); click(33);
        assertEquals("2147483646", fieldText("top")); assertTrue(editor.getMessage().contains("too large"));
        text("bottom", Integer.toString(Integer.MIN_VALUE + 1)); click(30);
        assertEquals(Integer.MIN_VALUE, editor.getGeometry().getMinYInclusive()); click(30);
        assertEquals(Integer.MIN_VALUE, editor.getGeometry().getMinYInclusive()); assertEquals(Integer.toString(Integer.MIN_VALUE), fieldText("bottom"));
        text("bottom", "80"); text("top", "70"); click(20);
        assertEquals(80, editor.getGeometry().getMinYInclusive()); assertFalse(editor.getGeometry().hasValidYRange());
        assertEquals(KOMETacticalEditRequest.Action.UPDATE, requests.get(0).getAction());
        editor.cancel(); assertNull(editor.getDraft());
    }
    private void openEmpty() throws Exception {
        click(7);
        editor.acceptComplexCatalog(new KOMETacticalComplexCatalog(KOMETacticalComplexCatalog.Kind.COMPLEXES, "T100", null, -1, 10, 0, 0, Collections.emptyList())); screen.updateScreen();
        text("id", "fort"); click(4);
        KOMESiegeComplex complex = KOMESiegeReadinessFixtures.empty("FORT", "T100", -1);
        editor.accept(new KOMETacticalEditSnapshot(UUID.randomUUID(), UUID.randomUUID(), new KOMETacticalEditScope(
            KOMETacticalEditScope.Type.SIEGE_COMPLEX, "T100", "FORT", "FORT", -1), 1, 1, 10, complex.getRevision(), 0, 10, false, new KOMETacticalEditDraft(complex), null), KOMETacticalEditSessionManager.Status.OPENED);
        screen.updateScreen(); requests.clear();
    }
    @Test public void sameShellSwitchesScopesAndEmptyComplexHasSaveControls() throws Exception {
        assertTrue(button(7).enabled); assertFalse(button(6).enabled); openEmpty();
        assertTrue(button(21).enabled); assertTrue(button(204).enabled); assertFalse(button(205).enabled);
        click(21); assertEquals(KOMETacticalEditRequest.Action.UPDATE, requests.get(0).getAction());
        assertTrue(KOMETacticalEditWire.decodeDraft(requests.get(0).getPayload()).getComplex().getNormalSegments().isEmpty());
    }
    @Test public void complexOverviewHasAlignedBackNavigation() throws Exception {
        openEmpty();
        for (int width : new int[] {280, 320, 800}) {
            screen.width = width; screen.initGui(); assertButtonsFitWithoutOverlap();
            assertEquals(screen.height - 26, button(214).yPosition);
            assertTrue(button(21).xPosition + button(21).width < button(214).xPosition);
            assertTrue(button(214).xPosition + button(214).width < button(22).xPosition);
        }
        click(214);
        assertTrue(editor.getMessage().contains("Unsaved")); assertTrue(requests.isEmpty());
        assertNotNull(editor.getComplexDraft());
    }
    @Test public void cleanComplexBackWaitsForAcknowledgmentThenListsCurrentTile() throws Exception {
        openEmpty(); editor.cancel(); requests.clear();
        KOMESiegeComplex saved = KOMESiegeReadinessFixtures.minimal("FORT", "T100", -1, null);
        editor.openComplex("FORT", false); requests.clear();
        UUID player = UUID.randomUUID(), token = UUID.randomUUID();
        KOMETacticalEditScope scope = new KOMETacticalEditScope(KOMETacticalEditScope.Type.SIEGE_COMPLEX, "T100", "FORT", "FORT", -1);
        editor.accept(new KOMETacticalEditSnapshot(player, token, scope, 2, 2, 10, saved.getRevision(), 0, 10,
            false, new KOMETacticalEditDraft(saved), null), KOMETacticalEditSessionManager.Status.OPENED);
        screen.updateScreen(); click(214);
        assertTrue(editor.isBusy()); assertEquals(KOMETacticalEditRequest.Action.CANCEL, requests.get(0).getAction());
        editor.accept(new KOMETacticalEditSnapshot(player, token, scope, 2, 3, 10, saved.getRevision(), 0, 10,
            true, new KOMETacticalEditDraft(saved), null), KOMETacticalEditSessionManager.Status.CANCELLED);
        assertEquals(KOMETacticalEditRequest.Action.BROWSE_COMPLEXES, requests.get(1).getAction());
        assertEquals("T100", requests.get(1).getScope().getTileId());
    }
    @Test public void geometryViewerKeepsComplexAndAreaDraftsAcrossRepeatedToggles() throws Exception {
        openEmpty(); editor.createZone("N"); editor.addVertex(1, 2); editor.setYRangeInclusive(61, 75);
        editor.setLabel("Unsent normal");
        KOMESiegeComplex draft = editor.getComplexDraft();
        enterWorldView();
        assertEquals(1, editor.overlays(snapshotPlayer(), -1).size());
        assertTrue(editor.overlays(snapshotPlayer(), -1).get(0).selected);
        assertTrue(editor.overlays(snapshotPlayer(), -1).get(0).unsaved);
        assertSame(screen, editor.endWorldView());
        assertSame(draft, editor.getComplexDraft());
        screen.updateScreen(); click(300);
        text("label", "Edited zone"); text("bottom", "62"); text("top", "74");
        enterWorldView();
        assertTrue(editor.overlays(snapshotPlayer(), -1).get(0).selected);
        KOMESiegeComplex edited = editor.getComplexDraft();
        assertSame(screen, editor.endWorldView()); assertSame(edited, editor.getComplexDraft());
        screen.initGui(); assertEquals("Edited zone", fieldText("label"));
        assertEquals("62", fieldText("bottom")); assertEquals("74", fieldText("top"));
        assertEquals(1, editor.getConfirmedVertexCount());
        assertEquals(61, editor.getGeometry().getMinYInclusive()); assertEquals(76, editor.getGeometry().getMaxYExclusive());
        click(15); assertEquals(62, editor.getGeometry().getMinYInclusive()); assertEquals(75, editor.getGeometry().getMaxYExclusive());
        editor.cancel(); editor.switchBrowser(false);
        editor.acceptCatalog(new KOMETacticalAreaCatalog("T100", -1, 10, 0, 0, Collections.emptyList()));
        screen.updateScreen(); openArea(); editor.addVertex(7, 8); editor.setYRangeInclusive(63, 72);
        text("label", "Typed area"); text("bottom", "63"); text("top", "72");
        enterWorldView(); assertEquals("Original", editor.getDraft().getLabel());
        kome.common.tactical.KOMEForceDeploymentArea area = editor.getDraft();
        assertSame(screen, editor.endWorldView()); screen.initGui();
        assertEquals("63", fieldText("bottom")); assertEquals("72", fieldText("top"));
        enterWorldView(); assertSame(screen, editor.endWorldView());
        assertEquals(area.getPrism(), editor.getDraft().getPrism()); assertEquals(1, editor.getConfirmedVertexCount());
    }
    private UUID snapshotPlayer() throws Exception {
        Field field = KOMETacticalAreaEditor.class.getDeclaredField("snapshot"); field.setAccessible(true);
        return ((KOMETacticalEditSnapshot) field.get(editor)).getPlayerId();
    }
    @Test public void dirtyLabelDoesNotRestoreCleanTopOverCompletedWorldEdit() throws Exception {
        openArea(); text("label", "Typed label");
        enterWorldView();
        editor.toggleWorldHeight(KOMETacticalAreaEditor.Selection.UPPER_Y);
        editor.applyWorldHeight(75);
        assertSame(screen, editor.endWorldView()); screen.initGui();
        assertEquals("Typed label", fieldText("label"));
        assertEquals("75", fieldText("top"));
        click(21);
        KOMETacticalEditDraft submitted = KOMETacticalEditWire.decodeDraft(requests.get(0).getPayload());
        assertEquals("Typed label", submitted.getArea().getLabel());
        assertEquals(76, submitted.getArea().getPrism().getMaxYExclusive());
    }
    @Test public void dirtyLabelDoesNotRestoreCleanBottomOverCompletedWorldEdit() throws Exception {
        openArea(); text("label", "Typed bottom label");
        enterWorldView(); editor.toggleWorldHeight(KOMETacticalAreaEditor.Selection.LOWER_Y);
        editor.applyWorldHeight(62);
        assertSame(screen, editor.endWorldView()); screen.initGui();
        assertEquals("Typed bottom label", fieldText("label"));
        assertEquals("62", fieldText("bottom"));
        click(21);
        KOMETacticalEditDraft submitted = KOMETacticalEditWire.decodeDraft(requests.get(0).getPayload());
        assertEquals("Typed bottom label", submitted.getArea().getLabel());
        assertEquals(62, submitted.getArea().getPrism().getMinYInclusive());
    }
    @Test public void cleanHeightFieldsFollowBothCompletedWorldEdits() throws Exception {
        openArea(); enterWorldView();
        editor.toggleWorldHeight(KOMETacticalAreaEditor.Selection.UPPER_Y); editor.applyWorldHeight(77);
        editor.toggleWorldHeight(KOMETacticalAreaEditor.Selection.LOWER_Y); editor.applyWorldHeight(63);
        assertSame(screen, editor.endWorldView()); screen.initGui();
        assertEquals("63", fieldText("bottom")); assertEquals("77", fieldText("top"));
        click(21);
        KOMETacticalEditDraft submitted = KOMETacticalEditWire.decodeDraft(requests.get(0).getPayload());
        assertEquals(63, submitted.getArea().getPrism().getMinYInclusive());
        assertEquals(78, submitted.getArea().getPrism().getMaxYExclusive());
    }
    @Test public void dirtyTopTextSurvivesUnrelatedWorldVertexEdit() throws Exception {
        openArea(); text("top", "78pending"); enterVertexWorldView();
        editor.addWorldVertex(5, 6);
        assertSame(screen, editor.endWorldView()); screen.initGui();
        assertEquals("78pending", fieldText("top"));
        assertEquals(1, editor.getConfirmedVertexCount());
        text("top", "78"); click(21);
        KOMETacticalEditDraft submitted = KOMETacticalEditWire.decodeDraft(requests.get(0).getPayload());
        assertEquals(79, submitted.getArea().getPrism().getMaxYExclusive());
        assertEquals(1, submitted.getArea().getPrism().getPolygon().getVertices().size());
    }
    @Test public void dirtyBottomTextSurvivesUnrelatedWorldVertexEdit() throws Exception {
        openArea(); text("bottom", "6-"); enterVertexWorldView();
        editor.addWorldVertex(7, 8);
        assertSame(screen, editor.endWorldView()); screen.initGui();
        assertEquals("6-", fieldText("bottom"));
        assertEquals(1, editor.getConfirmedVertexCount());
        text("bottom", "62"); click(21);
        KOMETacticalEditDraft submitted = KOMETacticalEditWire.decodeDraft(requests.get(0).getPayload());
        assertEquals(62, submitted.getArea().getPrism().getMinYInclusive());
        assertEquals(1, submitted.getArea().getPrism().getPolygon().getVertices().size());
    }
    @Test public void sameFieldWorldEditBlocksStaleTypedTopUntilUserResolvesIt() throws Exception {
        openArea(); text("top", "79"); enterWorldView();
        editor.toggleWorldHeight(KOMETacticalAreaEditor.Selection.UPPER_Y); editor.applyWorldHeight(75);
        assertSame(screen, editor.endWorldView()); screen.initGui();
        assertEquals("79", fieldText("top"));
        click(21);
        assertTrue(requests.isEmpty());
        assertEquals(76, editor.getGeometry().getMaxYExclusive());
        assertTrue(editor.getMessage().contains("Top Y changed in the world"));
        click(31);
        assertEquals(60, editor.getGeometry().getMinYInclusive());
        assertEquals(76, editor.getGeometry().getMaxYExclusive());
        enterWorldView(); assertSame(screen, editor.endWorldView()); screen.initGui();
        assertEquals("79", fieldText("top"));
        click(21); assertTrue(requests.isEmpty());
        text("top", "80"); click(21);
        assertEquals(81, KOMETacticalEditWire.decodeDraft(requests.get(0).getPayload()).getArea().getPrism().getMaxYExclusive());
    }
    @Test public void sameFieldWorldEditBlocksStaleTypedBottomUntilUserResolvesIt() throws Exception {
        openArea(); text("bottom", "61"); enterWorldView();
        editor.toggleWorldHeight(KOMETacticalAreaEditor.Selection.LOWER_Y); editor.applyWorldHeight(62);
        assertSame(screen, editor.endWorldView()); screen.initGui();
        assertEquals("61", fieldText("bottom"));
        click(21); assertTrue(requests.isEmpty());
        assertEquals(62, editor.getGeometry().getMinYInclusive());
        assertTrue(editor.getMessage().contains("Bottom Y changed in the world"));
        text("bottom", "63"); click(21);
        assertEquals(63, KOMETacticalEditWire.decodeDraft(requests.get(0).getPayload()).getArea().getPrism().getMinYInclusive());
    }
    @Test public void switchingZonesKeepsDirtyTextOnlyOnItsOwnPolygon() throws Exception {
        openEmpty();
        editor.createZone("A");
        for (int[] p : new int[][] {{0,0},{8,0},{8,8},{0,8}}) editor.addVertex(p[0], p[1]);
        editor.createZone("B");
        for (int[] p : new int[][] {{20,0},{28,0},{28,8},{20,8}}) editor.addVertex(p[0], p[1]);
        editor.selectZone("A"); screen.focusWorldSelection(); screen.initGui();
        text("label", "A pending");
        enterWorldView(); assertTrue(editor.selectWorldPolygon(snapshotPlayer(), -1, 24, 65, 2));
        editor.toggleWorldHeight(KOMETacticalAreaEditor.Selection.UPPER_Y); editor.applyWorldHeight(77);
        assertSame(screen, editor.endWorldView()); screen.focusWorldSelection(); screen.initGui();
        assertEquals("", fieldText("label")); assertEquals("77", fieldText("top"));
        enterWorldView(); assertTrue(editor.selectWorldPolygon(snapshotPlayer(), -1, 2, 65, 2));
        assertSame(screen, editor.endWorldView()); screen.focusWorldSelection(); screen.initGui();
        assertEquals("A pending", fieldText("label")); assertEquals("70", fieldText("top"));
        assertEquals("A", editor.getZoneId());
    }
    @Test public void worldSwitchAndUndoPreserveUnappliedTextByPolygon() throws Exception {
        openEmpty();
        editor.createZone("A");
        for (int[] p : new int[][] {{0,0},{8,0},{8,8},{0,8}}) editor.addVertex(p[0], p[1]);
        editor.createZone("B");
        for (int[] p : new int[][] {{20,0},{28,0},{28,8},{20,8}}) editor.addVertex(p[0], p[1]);
        editor.selectZone("A"); screen.focusWorldSelection(); screen.initGui();
        text("label", "A typed"); text("bottom", "-"); text("top", "A bad Y");
        enterWorldView(); assertTrue(editor.selectWorldPolygon(snapshotPlayer(), -1, 24, 65, 2));
        assertSame(screen, editor.endWorldView()); screen.focusWorldSelection(); screen.initGui();
        assertEquals("B", editor.getZoneId()); assertEquals("", fieldText("label"));
        text("label", "B typed"); text("bottom", "B bad Y"); text("top", "72bad");
        click(13);
        assertEquals("B typed", fieldText("label")); assertEquals("B bad Y", fieldText("bottom"));
        assertEquals("72bad", fieldText("top")); assertEquals("B", editor.getZoneId());
        enterWorldView(); assertTrue(editor.selectWorldPolygon(snapshotPlayer(), -1, 2, 65, 2));
        assertSame(screen, editor.endWorldView()); screen.focusWorldSelection(); screen.initGui();
        assertEquals("A typed", fieldText("label")); assertEquals("-", fieldText("bottom"));
        assertEquals("A bad Y", fieldText("top")); assertEquals("", editor.getGeometryLabel());
        enterWorldView(); assertTrue(editor.selectWorldPolygon(snapshotPlayer(), -1, 25, 65, 2));
        assertSame(screen, editor.endWorldView()); screen.focusWorldSelection(); screen.initGui();
        assertEquals("B typed", fieldText("label")); assertEquals("B bad Y", fieldText("bottom"));
        assertEquals("72bad", fieldText("top")); assertEquals("", editor.getGeometryLabel());
        KOMEGuiTacticalAreaEditor replacement = new KOMEGuiTacticalAreaEditor(editor);
        replacement.width = 320; replacement.height = 240;
        Field font = GuiScreen.class.getDeclaredField("fontRendererObj"); font.setAccessible(true);
        font.set(replacement, KOMEAccessFixture.allocate(InertFont.class)); replacement.initGui();
        Field replacementLabel = KOMEGuiTacticalAreaEditor.class.getDeclaredField("label"); replacementLabel.setAccessible(true);
        assertEquals("B typed", ((GuiTextField) replacementLabel.get(replacement)).getText());
        editor.selectZone("A"); replacement.initGui();
        assertEquals("A typed", ((GuiTextField) replacementLabel.get(replacement)).getText());
        assertTrue(requests.stream().noneMatch(r -> r.getAction() == KOMETacticalEditRequest.Action.UPDATE));
    }
    @Test public void malformedFieldsCanBeParkedForWorldViewButPendingSaveStillBlocksEntry() throws Exception {
        openArea(); text("top", "broken"); enterWorldView();
        assertTrue(editor.hasWorldViewContext()); assertSame(screen, editor.endWorldView()); screen.initGui();
        assertEquals("broken", fieldText("top")); assertEquals(71, editor.getGeometry().getMaxYExclusive());
        text("top", "70"); click(21); type((char) 27, Keyboard.KEY_ESCAPE);
        assertFalse(editor.hasWorldViewContext()); assertTrue(editor.isBusy());
        KOMETacticalEditRequest update = requests.get(0);
        KOMETacticalEditDraft captured = KOMETacticalEditWire.decodeDraft(update.getPayload());
        UUID player = UUID.randomUUID();
        editor.accept(new KOMETacticalEditSnapshot(player, update.getToken(), update.getScope(), 1, 2, 10, 0, 1, 10,
            false, captured, null), KOMETacticalEditSessionManager.Status.UPDATED);
        editor.accept(new KOMETacticalEditSnapshot(player, update.getToken(), update.getScope(), 1, 3, 10, 0, 1, 11,
            true, captured, null), KOMETacticalEditSessionManager.Status.SAVED);
        type((char) 27, Keyboard.KEY_ESCAPE);
        assertTrue(editor.isReopeningAfterSave()); assertFalse(editor.hasWorldViewContext());
    }
    @Test public void sharedGeometryToolbarOffersDirectVerticesWithoutSeparateStraightTool() throws Exception {
        openEmpty();text("id","N");click(212);
        assertEquals("Edit Vertices in World",button(10).displayString);assertEquals("Undo",button(13).displayString);
        assertTrue(buttons().stream().noneMatch(b->b.id==17));
    }
    @Test public void complexPagingMustNotUseDeploymentAreaCountAfterSaveRefresh() throws Exception {
        click(7);
        List<KOMETacticalComplexCatalog.Row> rows = new ArrayList<KOMETacticalComplexCatalog.Row>();
        for (String id : Arrays.asList("A", "B", "C", "D", "E")) rows.add(new KOMETacticalComplexCatalog.Row(id, "", "NOT READY", null, 1, 0));
        editor.acceptComplexCatalog(new KOMETacticalComplexCatalog(KOMETacticalComplexCatalog.Kind.COMPLEXES, "T100", null, -1, 11, 0, 6, rows));
        screen.updateScreen();
        assertTrue("Six complexes must remain browsable with zero tile deployment areas", button(2).enabled);
        click(2);
        assertEquals(1, requests.get(requests.size() - 1).getExpectedSequence());
        editor.acceptComplexCatalog(new KOMETacticalComplexCatalog(KOMETacticalComplexCatalog.Kind.COMPLEXES, "T100", null, -1, 11, 1, 6,
            Collections.singletonList(new KOMETacticalComplexCatalog.Row("FORT", "", "NOT READY", null, 2, 0))));
        screen.updateScreen(); assertTrue(button(1).enabled); assertFalse(button(2).enabled);
        assertEquals("FORT", editor.getComplexCatalog().rows.get(0).id);
    }
    @Test public void acknowledgedWallSaveStaysOnGeometryAndUsesFreshSession() throws Exception {
        click(7); editor.acceptComplexCatalog(new KOMETacticalComplexCatalog(KOMETacticalComplexCatalog.Kind.COMPLEXES, "T100", null, -1, 10, 0, 0, Collections.emptyList())); screen.updateScreen();
        KOMESiegeComplex complex = KOMESiegeReadinessFixtures.minimal("FORT", "T100", -1, null);
        KOMETacticalEditScope scope = new KOMETacticalEditScope(KOMETacticalEditScope.Type.SIEGE_COMPLEX, "T100", "FORT", "FORT", -1);
        UUID player = UUID.randomUUID(), token = UUID.randomUUID();
        editor.openComplex("FORT", false); editor.accept(new KOMETacticalEditSnapshot(player, token, scope, 1, 1, 10, complex.getRevision(), 0, 10, false,
            new KOMETacticalEditDraft(complex), null), KOMETacticalEditSessionManager.Status.OPENED); screen.updateScreen();
        click(202); text("id", "WALL"); click(212); text("label", "Walkway");
        editor.addVertex(10,0); editor.addVertex(12,0); editor.addVertex(12,4); editor.addVertex(10,4); requests.clear();
        click(21); assertEquals(KOMETacticalEditRequest.Action.UPDATE, requests.get(0).getAction());
        KOMETacticalEditDraft draft = KOMETacticalEditWire.decodeDraft(requests.get(0).getPayload());
        assertEquals("Walkway", draft.getComplex().findWallZone("WALL").getLabel()); assertEquals(complex.getConnections(), draft.getComplex().getConnections());
        editor.accept(new KOMETacticalEditSnapshot(player, token, scope, 1, 2, 10, complex.getRevision(), 1, 10, false, draft, null), KOMETacticalEditSessionManager.Status.UPDATED);
        assertEquals(KOMETacticalEditRequest.Action.SAVE, requests.get(1).getAction());
        editor.accept(new KOMETacticalEditSnapshot(player, token, scope, 1, 3, 10, complex.getRevision(), 1, 11, true, draft,
            new KOMETacticalEditPreflight(true, true, KOMETacticalEditPreflight.State.INCOMPLETE, Collections.singletonList("NO_VALID_DEFENSIVE_BUILD"))), KOMETacticalEditSessionManager.Status.SAVED);
        assertEquals(KOMETacticalEditRequest.Action.OPEN, requests.get(2).getAction());
        assertTrue(editor.isReopeningAfterSave()); assertFalse(editor.isEditing());
        assertEquals("WALL", editor.getZoneId()); assertFalse(button(21).enabled);
        assertTrue(requests.stream().noneMatch(r -> r.getAction() == KOMETacticalEditRequest.Action.DELETE));
        KOMESiegeComplex saved = new KOMESiegeComplex("FORT", "T100", -1, complex.getRevision() + 1,
            draft.getComplex().getNormalSegments(), draft.getComplex().getWallZones(), draft.getComplex().getTransitionZones(),
            draft.getComplex().getPreferredForceDeploymentAreaId().orElse(null), draft.getComplex().getConnections());
        editor.accept(new KOMETacticalEditSnapshot(player, UUID.randomUUID(), scope, 2, 4, 11, saved.getRevision(), 0, 11,
            false, new KOMETacticalEditDraft(saved), null), KOMETacticalEditSessionManager.Status.OPENED); screen.updateScreen();
        assertTrue(editor.isComplexEditing()); assertNotNull(button(10)); assertEquals("WALL", editor.getZoneId());
        assertEquals("Walkway", fieldText("label")); assertTrue(editor.getMessage().contains("Saved"));
        click(31); click(21);
        KOMETacticalEditRequest continued = requests.get(3);
        assertEquals(KOMETacticalEditRequest.Action.UPDATE, continued.getAction());
        assertNotEquals(token, continued.getToken()); assertEquals(0, continued.getExpectedSequence());
        assertEquals(saved.getRevision(), KOMETacticalEditWire.decodeDraft(continued.getPayload()).getComplex().getRevision());
        assertEquals("WALL", editor.getZoneId());
    }
    @Test public void zoneControlsUseSharedGeometryFieldsAndWallAccessSelection() throws Exception {
        openEmpty(); text("id", "lower"); click(212);
        text("label", "Lower courtyard"); text("bottom", "60"); text("top", "70");
        click(15); // shared geometry fields -> complex view
        assertEquals("Lower courtyard", editor.getComplexDraft().findNormalSegment("lower").getLabel());
        assertEquals(71, editor.getComplexDraft().findNormalSegment("lower").getPrism().getMaxYExclusive());
        click(202); text("id", "Wall"); click(212); click(16); click(400); click(223);
        assertEquals(Collections.singleton("lower"), editor.getComplexDraft().findWallZone("Wall").getAccessibleFromNormalSegmentIds());
        for (int width : new int[] {280, 320, 800}) {
            screen.width = width; screen.initGui(); assertButtonsFitWithoutOverlap();
            assertTrue(button(14).xPosition + button(14).width < button(16).xPosition);
            assertEquals(screen.height - 26, button(15).yPosition);
            assertTrue(button(14).yPosition < button(15).yPosition);
        }
        for (GuiButton button : buttons()) {
            assertTrue(button.xPosition >= 0 && button.yPosition >= 0);
            assertTrue(button.xPosition + button.width <= screen.width); assertTrue(button.yPosition + button.height <= screen.height);
        }
    }
    @Test public void returningFromWallAccessCannotAlsoArmWholeComplexDeletionInOneMouseDispatch() throws Exception {
        openEmpty(); editor.cancel(); editor.openComplex("FORT", false);
        KOMESiegeComplex complex = KOMESiegeReadinessFixtures.empty("FORT", "T100", -1);
        editor.accept(new KOMETacticalEditSnapshot(UUID.randomUUID(), UUID.randomUUID(), new KOMETacticalEditScope(KOMETacticalEditScope.Type.SIEGE_COMPLEX, "T100", "FORT", "FORT", -1),
            2, 2, 10, complex.getRevision(), 0, 10, false, new KOMETacticalEditDraft(complex), null), KOMETacticalEditSessionManager.Status.OPENED); screen.updateScreen(); requests.clear();
        text("id", "N"); click(212); click(15); click(202); text("id", "W"); click(212); click(16);
        GuiButton back = button(223);
        // Minecraft 1.7.10 GuiScreen.mouseClicked iterates the LIVE list after actionPerformed.
        // Rebuilding controls must not dispatch a second action at the same coordinates.
        Field dispatch = KOMEGuiTacticalAreaEditor.class.getDeclaredField("dispatchingClick"); dispatch.setAccessible(true); dispatch.setBoolean(screen, true);
        Field handled = KOMEGuiTacticalAreaEditor.class.getDeclaredField("clickActionHandled"); handled.setAccessible(true); handled.setBoolean(screen, false);
        Method action = KOMEGuiTacticalAreaEditor.class.getDeclaredMethod("actionPerformed", GuiButton.class); action.setAccessible(true);
        try {
            for (int index = 0; index < buttons().size(); index++) {
                GuiButton b = buttons().get(index);
                if (b.mousePressed(null, back.xPosition + back.width / 2, back.yPosition + back.height / 2)) action.invoke(screen, b);
            }
        } finally { dispatch.setBoolean(screen, false); }
        Field armed = KOMEGuiTacticalAreaEditor.class.getDeclaredField("confirmDelete"); armed.setAccessible(true);
        assertFalse("Back must not arm deletion of the containing complex", armed.getBoolean(screen));
        assertTrue(editor.isComplexEditing()); assertTrue(requests.stream().noneMatch(r -> r.getAction() == KOMETacticalEditRequest.Action.DELETE));
    }
    @Test public void deletePromptIdentifiesEntireComplexAndDoesNotDeleteOnFirstClick() throws Exception {
        openEmpty();editor.cancel();editor.openComplex("FORT",false);
        KOMESiegeComplex c=KOMESiegeReadinessFixtures.empty("FORT","T100",-1);
        editor.accept(new KOMETacticalEditSnapshot(UUID.randomUUID(),UUID.randomUUID(),new KOMETacticalEditScope(KOMETacticalEditScope.Type.SIEGE_COMPLEX,"T100","FORT","FORT",-1),
            2,2,10,c.getRevision(),0,10,false,new KOMETacticalEditDraft(c),null),KOMETacticalEditSessionManager.Status.OPENED);screen.updateScreen();requests.clear();
        click(23);assertTrue(editor.getMessage().contains("ENTIRE Siege Complex FORT"));
        assertTrue(requests.stream().noneMatch(r->r.getAction()==KOMETacticalEditRequest.Action.DELETE));
    }
    @Test public void asynchronousOpenCanBeHandledBeforeNextGuiTickWithoutStaleFields() throws Exception {
        click(7); editor.acceptComplexCatalog(new KOMETacticalComplexCatalog(KOMETacticalComplexCatalog.Kind.COMPLEXES, "T100", null, -1, 10, 0, 0, Collections.emptyList())); screen.updateScreen();
        text("id", "FORT"); click(4);
        KOMESiegeComplex complex = KOMESiegeReadinessFixtures.empty("FORT", "T100", -1);
        editor.accept(new KOMETacticalEditSnapshot(UUID.randomUUID(), UUID.randomUUID(), new KOMETacticalEditScope(KOMETacticalEditScope.Type.SIEGE_COMPLEX, "T100", "FORT", "FORT", -1),
            1, 1, 10, complex.getRevision(), 0, 10, false, new KOMETacticalEditDraft(complex), null), KOMETacticalEditSessionManager.Status.OPENED);
        // A key event can arrive between the network publication and updateScreen().
        Method key = KOMEGuiTacticalAreaEditor.class.getDeclaredMethod("keyTyped", char.class, int.class); key.setAccessible(true); key.invoke(screen, 'n', 49);
        assertNotNull(button(212)); assertTrue(editor.isComplexEditing());
    }
    private void openExistingConnection() throws Exception {
        click(7); editor.acceptComplexCatalog(new KOMETacticalComplexCatalog(KOMETacticalComplexCatalog.Kind.COMPLEXES, "T100", null, -1, 10, 0, 0, Collections.emptyList())); screen.updateScreen();
        KOMESiegeComplex c = KOMESiegeReadinessFixtures.minimal("FORT", "T100", -1, null); editor.openComplex("FORT", false);
        editor.accept(new KOMETacticalEditSnapshot(UUID.randomUUID(), UUID.randomUUID(), new KOMETacticalEditScope(KOMETacticalEditScope.Type.SIEGE_COMPLEX,"T100","FORT","FORT",-1),
            1,1,10,c.getRevision(),0,10,false,new KOMETacticalEditDraft(c),null),KOMETacticalEditSessionManager.Status.OPENED); screen.updateScreen(); requests.clear();
        click(206); replyConnections();
    }
    @Test public void undoControlRestoresDraftYAndKeepsUnappliedText() throws Exception {
        openArea();
        String before = fieldText("bottom");
        click(31); assertEquals(1, editor.getUndoCount());
        click(13); assertEquals(before, fieldText("bottom")); assertEquals(0, editor.getUndoCount());
        click(31); text("top", "unfinished");
        click(13); assertEquals("unfinished", fieldText("top")); assertEquals(before, fieldText("bottom"));
    }
    @Test public void connectionTooltipUsesActualCenteredButtonAtAllWidths() throws Exception {
        openExistingConnection();
        for (int width : new int[] {280, 320, 800}) {
            screen.width = width; screen.initGui();
            GuiButton row = button(620);
            int y = row.yPosition + row.height / 2;
            assertEquals(0, connectionTooltipIndex(row.xPosition + row.width / 2, y));
            assertEquals(-1, connectionTooltipIndex(row.xPosition - 1, y));
            assertEquals(-1, connectionTooltipIndex(row.xPosition + row.width, y));
            assertEquals(-1, connectionTooltipIndex(row.xPosition + row.width / 2, row.yPosition + row.height));
            assertEquals(-1, connectionTooltipIndex(20, y));
        }
    }
    private void replyConnections() throws Exception {
        editor.acceptComplexCatalog(new KOMETacticalComplexCatalog(KOMETacticalComplexCatalog.Kind.CONNECTIONS,"T100","FORT",-1,10,0,1,
            Collections.singletonList(new KOMETacticalComplexCatalog.Row("ENTRY","NONE","Geometry valid / Gateless",null,17,0)))); screen.updateScreen();
    }
    @Test public void actualConnectionControlsRequireInferenceAcceptanceAndKeepManualEndpointsAvailable() throws Exception {
        openExistingConnection(); click(620); replyConnections();
        click(601); click(651); // select NORMAL(A) manually; both endpoints now A, visibly invalid
        assertEquals(KOMESiegeAreaRef.normal("A"),editor.getConnection().getEndpointA());
        click(606); assertTrue(button(607).enabled); assertTrue(editor.getMessage().contains("Suggestion:"));
        assertFalse(editor.getConnection().getEndpointA().isExterior()); click(607); assertTrue(editor.getConnection().getEndpointA().isExterior());
        click(602); assertTrue(button(650).displayString.contains("EXTERIOR")); assertTrue(button(651).displayString.contains("NORMAL")); click(657);
        for (GuiButton b : buttons()) { assertTrue(b.xPosition>=0); assertTrue(b.xPosition+b.width<=screen.width); assertTrue(b.yPosition+b.height<=screen.height); }
        requests.clear(); click(21); assertEquals(KOMETacticalEditRequest.Action.UPDATE,requests.get(0).getAction());
        assertTrue(requests.stream().noneMatch(r->r.getAction()==KOMETacticalEditRequest.Action.DELETE));
    }
    @Test public void connectionCreationRemovalIsExplicitAndPreservesTransition() throws Exception {
        openExistingConnection(); text("id","lower"); click(615); assertEquals("lower",editor.getConnectionId());
        assertFalse(editor.getConnection().isGated()); click(610); assertNotNull(editor.getConnection()); assertTrue(editor.getMessage().contains("Remove Connection lower"));
        click(610); assertNull(editor.getConnection()); assertEquals(1,editor.getComplexDraft().getConnections().size()); assertEquals(1,editor.getComplexDraft().getTransitionZones().size());
        assertTrue(requests.stream().noneMatch(r->r.getAction()==KOMETacticalEditRequest.Action.DELETE)); click(609); assertNotNull(button(212));
    }
    @Test public void gateSelectorOffersExplicitGatelessAndReturnsToSameConnectionForm() throws Exception {
        openExistingConnection(); click(620); replyConnections(); click(604);
        assertEquals(KOMETacticalEditRequest.Action.BROWSE_GATES,requests.get(requests.size()-1).getAction());
        editor.acceptComplexCatalog(new KOMETacticalComplexCatalog(KOMETacticalComplexCatalog.Kind.GATES,"T100","FORT",-1,10,0,0,Collections.emptyList())); screen.updateScreen();
        assertEquals("Gateless (NONE)",button(226).displayString); click(226); assertEquals("ENTRY",editor.getConnectionId()); assertFalse(editor.getConnection().isGated());
        assertNotNull(button(601)); assertNotNull(button(606)); assertTrue(requests.stream().noneMatch(r->r.getAction()==KOMETacticalEditRequest.Action.DELETE));
    }
    @Test public void gateSelectorKeepsBrokenAuthoredGateVisibleWithoutOfferingItAsNewChoice() throws Exception {
        openExistingConnection(); click(620); replyConnections();
        KOMESiegeComplex c=editor.getComplexDraft();
        Field draft=KOMETacticalAreaEditor.class.getDeclaredField("localComplex"); draft.setAccessible(true);
        draft.set(editor,KOMETacticalConnectionDraft.edit(c,new KOMESiegeConnection("ENTRY",KOMESiegeAreaRef.exterior(),KOMESiegeAreaRef.normal("A"),"T_ENTRY",new KOMEDefensiveGateRef("MISSING","GONE"))));
        click(604); editor.acceptComplexCatalog(new KOMETacticalComplexCatalog(KOMETacticalComplexCatalog.Kind.GATES,"T100","FORT",-1,10,0,0,Collections.emptyList())); screen.updateScreen();
        assertEquals("Keep current",button(227).displayString); click(227);
        assertEquals(new KOMEDefensiveGateRef("MISSING","GONE"),editor.getConnection().getGateRef().get());
        assertTrue(editor.getMessage().contains("Retained authored gate")); assertNotNull(button(604));
    }
    @Test public void anotherConnectionsUsedGateIsVisibleButDisabledAndOwnGateIsEnabled() throws Exception {
        openExistingConnection(); click(620); replyConnections();
        KOMESiegeComplex c=editor.getComplexDraft();
        KOMESiegeConnection other=new KOMESiegeConnection("OTHER",KOMESiegeAreaRef.exterior(),KOMESiegeAreaRef.normal("A"),"OTHER_T",new KOMEDefensiveGateRef("B7","G1"));
        Field draft=KOMETacticalAreaEditor.class.getDeclaredField("localComplex"); draft.setAccessible(true);
        draft.set(editor,new KOMESiegeComplex(c.getComplexId(),c.getTileId(),c.getDimensionId(),c.getRevision(),c.getNormalSegments(),c.getWallZones(),c.getTransitionZones(),null,
            Arrays.asList(c.getConnections().get(0),other)));
        click(604);
        KOMETacticalComplexCatalog.Row row=new KOMETacticalComplexCatalog.Row("G1","Build","UNKNOWN","B7",0,0);
        editor.acceptComplexCatalog(new KOMETacticalComplexCatalog(KOMETacticalComplexCatalog.Kind.GATES,"T100","FORT",-1,10,0,1,Collections.singletonList(row))); screen.updateScreen();
        assertFalse(button(400).enabled); assertTrue(button(400).displayString.contains("Used by OTHER")); assertTrue(button(226).enabled);
        click(223); editor.selectConnection("OTHER"); screen.updateScreen(); click(604);
        editor.acceptComplexCatalog(new KOMETacticalComplexCatalog(KOMETacticalComplexCatalog.Kind.GATES,"T100","FORT",-1,10,0,1,Collections.singletonList(row))); screen.updateScreen();
        assertTrue(button(400).enabled); assertTrue(button(227).enabled); click(400); assertEquals(other.getGateRef(),editor.getConnection().getGateRef());
    }
    public static class InertFont extends FontRenderer {
        public InertFont() { super(null, null, null, false); }
        @Override public String trimStringToWidth(String value, int width) { return value.substring(0, Math.min(value.length(), Math.max(0, width / 6))); }
        @Override public String trimStringToWidth(String value, int width, boolean reverse) { return trimStringToWidth(value, width); }
        @Override public int getStringWidth(String value) { return value.length() * 6; }
    }
}
