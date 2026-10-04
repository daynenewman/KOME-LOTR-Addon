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
    private void openExistingConnection() throws Exception {
        click(7); editor.acceptComplexCatalog(new KOMETacticalComplexCatalog(KOMETacticalComplexCatalog.Kind.COMPLEXES, "T100", null, -1, 10, 0, 0, Collections.emptyList())); screen.updateScreen();
        KOMESiegeComplex c = KOMESiegeReadinessFixtures.minimal("FORT", "T100", -1, null); editor.openComplex("FORT", false);
        editor.accept(new KOMETacticalEditSnapshot(UUID.randomUUID(), UUID.randomUUID(), new KOMETacticalEditScope(KOMETacticalEditScope.Type.SIEGE_COMPLEX,"T100","FORT","FORT",-1),
            1,1,10,c.getRevision(),0,10,false,new KOMETacticalEditDraft(c),null),KOMETacticalEditSessionManager.Status.OPENED); screen.updateScreen(); requests.clear();
        click(206); replyConnections();
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
