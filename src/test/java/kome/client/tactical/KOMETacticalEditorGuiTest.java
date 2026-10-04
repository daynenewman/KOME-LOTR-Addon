package kome.client.tactical;

import java.lang.reflect.*;
import java.util.*;
import kome.client.gui.KOMEGuiTacticalAreaEditor;
import kome.common.KOMEAccessFixture;
import kome.common.siege.*;
import kome.common.tactical.edit.*;
import net.minecraft.client.gui.*;
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
    private void click(int id) throws Exception {
        GuiButton b = button(id); assertTrue("Button disabled " + id, b.enabled);
        Method action = KOMEGuiTacticalAreaEditor.class.getDeclaredMethod("actionPerformed", GuiButton.class); action.setAccessible(true); action.invoke(screen, b);
        screen.updateScreen();
    }
    private void text(String name, String value) throws Exception { Field field = KOMEGuiTacticalAreaEditor.class.getDeclaredField(name); field.setAccessible(true); ((GuiTextField) field.get(screen)).setText(value); }
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
    @Test public void sharedGeometryToolbarOffersDirectVerticesWithoutSeparateStraightTool() throws Exception {
        openEmpty();text("id","N");click(212);
        assertEquals("Select vertices (Tab corners)",button(10).displayString);assertEquals("Undo vertex",button(13).displayString);
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
    @Test public void wallGeometrySaveWithZeroBuildsReturnsToBrowsableComplexList() throws Exception {
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
        assertEquals(KOMETacticalEditRequest.Action.BROWSE_COMPLEXES, requests.get(2).getAction());
        assertTrue(requests.stream().noneMatch(r -> r.getAction() == KOMETacticalEditRequest.Action.DELETE));
        editor.acceptComplexCatalog(new KOMETacticalComplexCatalog(KOMETacticalComplexCatalog.Kind.COMPLEXES, "T100", null, -1, 11, 0, 1,
            Collections.singletonList(new KOMETacticalComplexCatalog.Row("FORT", "", "NOT READY", null, complex.getRevision() + 1, 0)))); screen.updateScreen();
        assertFalse(editor.isEditing()); assertTrue(editor.isComplexBrowser()); assertTrue(button(100).displayString.contains("FORT"));
    }
    @Test public void zoneControlsUseSharedGeometryFieldsAndWallAccessSelection() throws Exception {
        openEmpty(); text("id", "lower"); click(212);
        text("label", "Lower courtyard"); text("bottom", "60"); text("top", "70");
        click(15); // shared geometry fields -> complex view
        assertEquals("Lower courtyard", editor.getComplexDraft().findNormalSegment("lower").getLabel());
        assertEquals(71, editor.getComplexDraft().findNormalSegment("lower").getPrism().getMaxYExclusive());
        click(202); text("id", "Wall"); click(212); click(16); click(400); click(223);
        assertEquals(Collections.singleton("lower"), editor.getComplexDraft().findWallZone("Wall").getAccessibleFromNormalSegmentIds());
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
    public static class InertFont extends FontRenderer {
        public InertFont() { super(null, null, null, false); }
        @Override public String trimStringToWidth(String value, int width) { return value.substring(0, Math.min(value.length(), Math.max(0, width / 6))); }
        @Override public String trimStringToWidth(String value, int width, boolean reverse) { return trimStringToWidth(value, width); }
        @Override public int getStringWidth(String value) { return value.length() * 6; }
    }
}
