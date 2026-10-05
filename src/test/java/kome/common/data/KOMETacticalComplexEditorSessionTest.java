package kome.common.data;

import java.util.*;
import kome.common.siege.*;
import kome.common.tactical.*;
import kome.common.tactical.edit.*;
import kome.client.tactical.KOMETacticalComplexDraft;
import kome.client.tactical.KOMETacticalComplexDraft.ZoneType;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.*;
import static kome.common.data.KOMETacticalEditorFixtures.*;
import static kome.common.tactical.edit.KOMETacticalEditSessionManager.Status.*;
import static org.junit.Assert.*;

public class KOMETacticalComplexEditorSessionTest {
    @Rule public final KOMETileTestResources tiles = new KOMETileTestResources();
    private CountingWorld data; private Actor actor; private KOMETacticalEditSessionManager manager;
    @Before public void setup() { data = world(); actor = new Actor(); manager = new KOMETacticalEditSessionManager(); }
    private KOMETacticalEditSnapshot open(String id, boolean create) {
        KOMETacticalEditSessionManager.Result r = manager.handle(actor, data, create
            ? KOMETacticalEditRequest.create(complexScope(id)) : KOMETacticalEditRequest.open(complexScope(id)));
        assertEquals(OPENED, r.getStatus()); return r.getSnapshot();
    }
    private KOMETacticalEditSnapshot update(KOMETacticalEditSnapshot s, KOMETacticalEditDraft draft) {
        KOMETacticalEditSessionManager.Result r = manager.handle(actor, data, KOMETacticalEditRequest.update(s, draft));
        assertEquals(UPDATED, r.getStatus()); return r.getSnapshot();
    }
    private KOMETacticalEditSessionManager.Result action(KOMETacticalEditRequest.Action a, KOMETacticalEditSnapshot s) {
        return manager.handle(actor, data, KOMETacticalEditRequest.action(a, s));
    }
    private NBTTagCompound state() { return KOMETacticalConfigurationCodec.encode(data.getTacticalConfigurationSnapshot()); }
    private void clean() { data.setDirty(false); data.dirtyCalls = 0; }
    private void untouched(NBTTagCompound before) { assertEquals(before, state()); assertFalse(data.isDirty()); assertEquals(0, data.dirtyCalls); }
    private void save(KOMETacticalEditSnapshot s) { assertEquals(SAVED, action(KOMETacticalEditRequest.Action.SAVE, s).getStatus()); }
    private KOMETacticalEditSessionManager.Result membership(String complex, String build, KOMETacticalEditDraft.MembershipAction action, String old) {
        KOMETacticalEditSnapshot s = open(complex, false);
        s = update(s, s.getDraft().withMembership(action, build, old));
        return action(KOMETacticalEditRequest.Action.SAVE, s);
    }
    @Test public void complexCatalogueIsTileScopedIndependentDeterministicAndReadOnly() {
        KOMETacticalConfiguration c = data.getTacticalConfigurationSnapshot(); c.addComplex(KOMESiegeReadinessFixtures.empty("FOREIGN", "T101", dimension()));
        KOMETacticalMembershipFixtures.installConfiguration(data, data, c); clean(); NBTTagCompound before = state();
        KOMETacticalComplexCatalog page = KOMETacticalComplexAccess.catalog(data, KOMETacticalComplexCatalog.Kind.COMPLEXES, "T100", null, dimension(), 0);
        assertEquals(3, page.total); assertEquals("A", page.rows.get(0).id); assertEquals("B", page.rows.get(1).id);
        assertTrue(page.rows.get(2).detail.contains("NOT READY")); assertThrows(UnsupportedOperationException.class, () -> page.rows.clear()); untouched(before);
    }
    @Test public void creationIsDetachedCanonicalDuplicateSafeAndEmptySaveable() {
        long revision = revision(data); NBTTagCompound before = state();
        KOMETacticalEditSnapshot s = open(" west-fort ", true); assertEquals("WEST-FORT", s.getScope().getComplexId()); untouched(before);
        KOMETacticalEditPreflight p = action(KOMETacticalEditRequest.Action.PREFLIGHT, s).getSnapshot().getPreflight();
        assertTrue(p.canSave()); assertFalse(p.isReady()); save(s);
        assertEquals(revision + 1, revision(data)); assertEquals(1, data.dirtyCalls);
        assertEquals(1, data.getTacticalConfigurationSnapshot().findComplex("WEST-FORT").getRevision());
        clean(); before = state(); assertEquals(DUPLICATE_ID, manager.handle(actor, data, KOMETacticalEditRequest.create(complexScope("West-Fort"))).getStatus()); untouched(before);
    }
    @Test public void preferredAreaSetAndClearSaveIndependentlyOfTileAreaGeometry() {
        KOMEForceDeploymentArea original = data.getTacticalConfigurationSnapshot().findForceDeploymentArea("FIELD");
        KOMETacticalEditSnapshot s = open("EMPTY", false); long objectRevision = s.getBaseObjectRevision();
        s = update(s, new KOMETacticalEditDraft(KOMETacticalComplexDraft.preferred(s.getDraft().getComplex(), "FIELD"))); save(s);
        assertEquals("FIELD", data.getTacticalConfigurationSnapshot().findComplex("EMPTY").getPreferredForceDeploymentAreaId().get());
        assertEquals(objectRevision + 1, data.getTacticalConfigurationSnapshot().findComplex("EMPTY").getRevision()); assertSame(original, data.getTacticalConfigurationSnapshot().findForceDeploymentArea("FIELD"));
        s = open("EMPTY", false); s = update(s, new KOMETacticalEditDraft(KOMETacticalComplexDraft.preferred(s.getDraft().getComplex(), null))); save(s);
        assertFalse(data.getTacticalConfigurationSnapshot().findComplex("EMPTY").getPreferredForceDeploymentAreaId().isPresent());
    }
    @Test public void wrongTileAndWrongDimensionPreferredSelectionCannotPublish() {
        KOMETacticalConfiguration c = data.getTacticalConfigurationSnapshot();
        c.addForceDeploymentArea(new KOMEForceDeploymentArea("WRONG_DIM", "T100", dimension() + 1, "", area("X", "", 0).getPrism(), 0));
        KOMETacticalMembershipFixtures.installConfiguration(data, data, c); clean();
        for (String id : Arrays.asList("WRONG_TILE", "WRONG_DIM")) {
            NBTTagCompound before = state(); KOMETacticalEditSnapshot s = open("EMPTY", false);
            s = update(s, new KOMETacticalEditDraft(KOMETacticalComplexDraft.preferred(s.getDraft().getComplex(), id)));
            assertFalse(action(KOMETacticalEditRequest.Action.PREFLIGHT, s).getSnapshot().getPreflight().canSave());
            assertEquals(REJECTED, action(KOMETacticalEditRequest.Action.SAVE, s).getStatus()); untouched(before); action(KOMETacticalEditRequest.Action.CANCEL, s);
        }
    }
    @Test public void preferredChoicesOnlyIncludeSameTileAndDimension() {
        KOMETacticalConfiguration c = data.getTacticalConfigurationSnapshot(); c.addForceDeploymentArea(new KOMEForceDeploymentArea("WRONG_DIM", "T100", dimension() + 1, "", area("X", "", 0).getPrism(), 0));
        KOMETacticalMembershipFixtures.installConfiguration(data, data, c); clean();
        KOMETacticalComplexCatalog choices = KOMETacticalComplexAccess.catalog(data, KOMETacticalComplexCatalog.Kind.PREFERRED_AREAS, "T100", "EMPTY", dimension(), 0);
        assertEquals(2, choices.total); assertEquals("FIELD", choices.rows.get(0).id); assertEquals("OTHER", choices.rows.get(1).id);
    }
    @Test public void newComplexCanChoosePreferredAreaWhileStillDetached() {
        NBTTagCompound before = state(); KOMETacticalEditSnapshot s = open("NEW", true);
        assertEquals("NEW", KOMETacticalComplexAccess.catalog(data, KOMETacticalComplexCatalog.Kind.PREFERRED_AREAS, "T100", "NEW", dimension(), 0).complexId);
        s = update(s, new KOMETacticalEditDraft(KOMETacticalComplexDraft.preferred(s.getDraft().getComplex(), "FIELD"))); untouched(before);
        save(s); assertEquals("FIELD", data.getTacticalConfigurationSnapshot().findComplex("NEW").getPreferredForceDeploymentAreaId().get());
    }
    @Test public void editorMembershipAssignsManyRejectsImplicitSecondOwnershipAndUnassigns() {
        assertEquals(SAVED, membership("A", "b1", KOMETacticalEditDraft.MembershipAction.ASSIGN, null).getStatus());
        assertEquals(SAVED, membership("A", "B2", KOMETacticalEditDraft.MembershipAction.ASSIGN, null).getStatus());
        assertEquals(Arrays.asList("B1", "B2"), data.getTacticalConfigurationSnapshot().listAssignedBuildIds("A"));
        clean(); NBTTagCompound before = state(); assertEquals(REJECTED, membership("B", "B1", KOMETacticalEditDraft.MembershipAction.ASSIGN, null).getStatus()); untouched(before);
        manager.closePlayer(actor.id);
        long revision = revision(data); long objectRevision = data.getTacticalConfigurationSnapshot().findComplex("A").getRevision();
        assertEquals(SAVED, membership("A", "B1", KOMETacticalEditDraft.MembershipAction.UNASSIGN, null).getStatus());
        assertFalse(data.getTacticalConfigurationSnapshot().findAssignedComplexId("B1").isPresent());
        assertEquals(revision + 1, revision(data)); assertEquals(objectRevision, data.getTacticalConfigurationSnapshot().findComplex("A").getRevision()); assertEquals(1, data.dirtyCalls);
    }
    @Test public void explicitReassignmentRetainsOldGateRefsAndSurfacesImpact() {
        KOMETacticalConfiguration c = data.getTacticalConfigurationSnapshot();
        c.replaceComplex(KOMETacticalMembershipFixtures.complex("A", "T100", dimension(), KOMETacticalMembershipFixtures.gate("ENTRY", "B1", "G1")));
        KOMETacticalMembershipFixtures.installConfiguration(data, data, c);
        membership("A", "B1", KOMETacticalEditDraft.MembershipAction.ASSIGN, null); clean();
        KOMETacticalEditSessionManager.Result r = membership("B", "B1", KOMETacticalEditDraft.MembershipAction.REASSIGN, "A"); assertEquals(SAVED, r.getStatus());
        assertEquals("B", data.getTacticalConfigurationSnapshot().findAssignedComplexId("B1").get());
        assertEquals(1, data.getTacticalConfigurationSnapshot().findComplex("A").getConnections().size());
        assertTrue(r.getSnapshot().getPreflight().getDiagnostics().stream().anyMatch(d -> d.contains("AFFECTED A/ENTRY")));
    }
    @Test public void staleExpectedOwnerCannotReassignAndInactiveOrMissingBuildIsInspectable() {
        membership("A", "B1", KOMETacticalEditDraft.MembershipAction.ASSIGN, null); clean(); NBTTagCompound before = state();
        assertEquals(REJECTED, membership("B", "B1", KOMETacticalEditDraft.MembershipAction.REASSIGN, "EMPTY").getStatus()); untouched(before); manager.closePlayer(actor.id);
        data.getBuild("B1").active = false; data.builds.remove("B2");
        KOMETacticalComplexCatalog p = KOMETacticalComplexAccess.catalog(data, KOMETacticalComplexCatalog.Kind.BUILDS, "T100", "A", dimension(), 0);
        assertEquals("A", p.rows.get(0).relatedId); assertTrue(p.rows.get(0).detail.contains("BUILD_INACTIVE"));
    }
    @Test public void missingAssignedBuildRemainsListedAndCanBeExplicitlyUnassigned() {
        membership("A", "B2", KOMETacticalEditDraft.MembershipAction.ASSIGN, null); data.builds.remove("B2"); clean();
        KOMETacticalComplexCatalog p = KOMETacticalComplexAccess.catalog(data, KOMETacticalComplexCatalog.Kind.BUILDS, "T100", "A", dimension(), 0);
        assertTrue(p.rows.stream().anyMatch(row -> row.id.equals("B2") && row.detail.contains("BUILD_MISSING")));
        assertEquals(SAVED, membership("A", "B2", KOMETacticalEditDraft.MembershipAction.UNASSIGN, null).getStatus());
        assertFalse(data.getTacticalConfigurationSnapshot().findAssignedComplexId("B2").isPresent()); assertEquals(1, data.dirtyCalls);
    }
    @Test public void allZoneTypesAndWallAccessRoundTripWithoutEditingConnections() {
        KOMESiegeComplex other = data.getTacticalConfigurationSnapshot().findComplex("B");
        KOMEForceDeploymentArea field = data.getTacticalConfigurationSnapshot().findForceDeploymentArea("FIELD");
        KOMETacticalEditSnapshot s = open("EMPTY", false); KOMESiegeComplex c = s.getDraft().getComplex();
        c = KOMETacticalComplexDraft.create(c, ZoneType.NORMAL, "lower");
        c = KOMETacticalComplexDraft.edit(c, ZoneType.NORMAL, "lower", "Lower court", KOMESiegeReadinessFixtures.prism(0, 0, 10, 10));
        c = KOMETacticalComplexDraft.create(c, ZoneType.WALL, "Wall"); c = KOMETacticalComplexDraft.wallAccess(c, "Wall", "lower");
        c = KOMETacticalComplexDraft.edit(c, ZoneType.WALL, "Wall", "Wall walk", KOMESiegeReadinessFixtures.prism(10, 0, 12, 10));
        c = KOMETacticalComplexDraft.create(c, ZoneType.TRANSITION, "Passage");
        c = KOMETacticalComplexDraft.edit(c, ZoneType.TRANSITION, "Passage", "Passage", KOMESiegeReadinessFixtures.prism(0, 0, 3, 3));
        s = update(s, new KOMETacticalEditDraft(c)); NBTTagCompound before = state(); untouched(before);
        KOMETacticalEditPreflight p = action(KOMETacticalEditRequest.Action.PREFLIGHT, s).getSnapshot().getPreflight();
        assertTrue(p.canSave()); assertFalse(p.isReady()); assertTrue(p.getDiagnostics().stream().anyMatch(d -> d.contains("TRANSITION_UNUSED"))); save(s);
        assertSame(other, data.getTacticalConfigurationSnapshot().findComplex("B")); assertSame(field, data.getTacticalConfigurationSnapshot().findForceDeploymentArea("FIELD"));
        KOMEWorldData restored = new KOMEWorldData("roundTrip"); restored.readFromNBT(KOMETacticalMembershipFixtures.save(data));
        KOMESiegeComplex saved = restored.getTacticalConfigurationSnapshot().findComplex("EMPTY");
        assertEquals("Lower court", saved.findNormalSegment("lower").getLabel()); assertNull(saved.findNormalSegment("LOWER"));
        assertEquals(Collections.singleton("lower"), saved.findWallZone("Wall").getAccessibleFromNormalSegmentIds()); assertNotNull(saved.findTransitionZone("Passage"));
        assertTrue(saved.getConnections().isEmpty());
    }
    @Test public void zoneDeletionRetainsWallAndConnectionReferencesForDiagnostics() {
        KOMETacticalEditSnapshot s = open("A", false); KOMESiegeComplex c = s.getDraft().getComplex(); String id = c.getNormalSegments().get(0).getId();
        c = KOMETacticalComplexDraft.remove(c, ZoneType.NORMAL, id); s = update(s, new KOMETacticalEditDraft(c));
        assertEquals(1, c.getConnections().size()); assertTrue(action(KOMETacticalEditRequest.Action.PREFLIGHT, s).getSnapshot().getPreflight().canSave()); save(s);
        assertEquals(1, data.getTacticalConfigurationSnapshot().findComplex("A").getConnections().size());
    }
    @Test public void cancelStaleAndNoOpComplexSavesDoNotAlterOtherDefinitions() {
        NBTTagCompound before = state(); KOMETacticalEditSnapshot s = open("EMPTY", false);
        s = update(s, new KOMETacticalEditDraft(KOMETacticalComplexDraft.create(s.getDraft().getComplex(), ZoneType.NORMAL, "Draft")));
        action(KOMETacticalEditRequest.Action.CANCEL, s); untouched(before);
        s = open("EMPTY", false); assertEquals(NO_CHANGE, action(KOMETacticalEditRequest.Action.SAVE, s).getStatus()); untouched(before);
        s = open("EMPTY", false); KOMETacticalEditSnapshot other = manager.handle(new Actor(), data, KOMETacticalEditRequest.create(complexScope("NEW"))).getSnapshot();
        manager.handle(newActor(other), data, KOMETacticalEditRequest.action(KOMETacticalEditRequest.Action.SAVE, other)); clean(); before = state();
        assertEquals(STALE_STORE, action(KOMETacticalEditRequest.Action.SAVE, s).getStatus()); untouched(before);
    }
    private Actor newActor(KOMETacticalEditSnapshot s) { Actor a = new Actor(); a.id = s.getPlayerId(); return a; }
    @Test public void wallSaveWithoutBuildsPreservesComplexAndAllOtherAuthoredState() { zoneSaveSurvives(ZoneType.WALL, false); }
    @Test public void wallSaveWithBuildPreservesComplexAndAllOtherAuthoredState() { zoneSaveSurvives(ZoneType.WALL, true); }
    @Test public void normalSaveWithoutBuildsPreservesComplexAndAllOtherAuthoredState() { zoneSaveSurvives(ZoneType.NORMAL, false); }
    @Test public void transitionSaveWithoutBuildsPreservesComplexAndAllOtherAuthoredState() { zoneSaveSurvives(ZoneType.TRANSITION, false); }
    private void zoneSaveSurvives(ZoneType type, boolean assigned) {
        KOMETacticalConfiguration config = data.getTacticalConfigurationSnapshot();
        KOMESiegeComplex original = KOMETacticalComplexDraft.preferred(config.findComplex("A"), "FIELD");
        original = KOMETacticalComplexDraft.create(original, ZoneType.WALL, "EXISTING_WALL");
        original = KOMETacticalComplexDraft.edit(original, ZoneType.WALL, "EXISTING_WALL", "Keep wall", KOMESiegeReadinessFixtures.prism(10, 0, 12, 10));
        original = KOMETacticalComplexDraft.wallAccess(original, "EXISTING_WALL", "A");
        config.replaceComplex(original); if (assigned) config.assignBuild("B1", "A");
        KOMETacticalMembershipFixtures.installConfiguration(data, data, config); clean();
        long storeRevision = revision(data);
        KOMESiegeComplex other = data.getTacticalConfigurationSnapshot().findComplex("B"); KOMEForceDeploymentArea area = data.getTacticalConfigurationSnapshot().findForceDeploymentArea("FIELD");
        KOMETacticalEditSnapshot s = open("A", false);
        KOMESiegeComplex edited = KOMETacticalComplexDraft.create(s.getDraft().getComplex(), type, "NEW_" + type);
        kome.common.siege.geometry.KOMEPolygon polygon = kome.common.siege.geometry.KOMEPolygon.of(
            new kome.common.siege.geometry.KOMEXZPoint(20,0),new kome.common.siege.geometry.KOMEXZPoint(22,0),
            new kome.common.siege.geometry.KOMEXZPoint(22,10),new kome.common.siege.geometry.KOMEXZPoint(20,10));
        kome.common.siege.geometry.KOMEPolygonPrism authored = new kome.common.siege.geometry.KOMEPolygonPrism(polygon,0,10);
        edited = KOMETacticalComplexDraft.edit(edited, type, "NEW_" + type, "Saved zone", authored);
        if (type == ZoneType.WALL) edited = KOMETacticalComplexDraft.wallAccess(edited, "NEW_WALL", "A");
        s = update(s, new KOMETacticalEditDraft(edited));
        KOMETacticalEditPreflight preflight = action(KOMETacticalEditRequest.Action.PREFLIGHT, s).getSnapshot().getPreflight();
        assertTrue(preflight.canSave()); if (!assigned) assertFalse(preflight.isReady());
        save(s);
        KOMESiegeComplex saved = data.getTacticalConfigurationSnapshot().findComplex("A"); assertNotNull(saved);
        assertEquals(authored,KOMETacticalComplexDraft.find(saved,type,"NEW_"+type).getPrism());
        assertEquals(original.getRevision() + 1, saved.getRevision()); assertEquals(storeRevision + 1, revision(data)); assertEquals(1, data.dirtyCalls);
        assertEquals("FIELD", saved.getPreferredForceDeploymentAreaId().get()); assertEquals(original.getConnections(), saved.getConnections());
        assertEquals(original.findNormalSegment("A"), saved.findNormalSegment("A"));
        assertEquals(original.findWallZone("EXISTING_WALL"), saved.findWallZone("EXISTING_WALL"));
        assertEquals(original.findTransitionZone("T_ENTRY"), saved.findTransitionZone("T_ENTRY"));
        assertEquals(assigned ? Collections.singletonList("B1") : Collections.emptyList(), data.getTacticalConfigurationSnapshot().listAssignedBuildIds("A"));
        assertSame(other, data.getTacticalConfigurationSnapshot().findComplex("B")); assertSame(area, data.getTacticalConfigurationSnapshot().findForceDeploymentArea("FIELD"));
        assertTrue(KOMETacticalComplexAccess.catalog(data, KOMETacticalComplexCatalog.Kind.COMPLEXES, "T100", null, dimension(), 0).rows.stream().anyMatch(row -> row.id.equals("A")));
        KOMETacticalEditSnapshot reopened = open("A", false); assertTrue(reopened.getDraft().sameDefinition(new KOMETacticalEditDraft(saved))); manager.closePlayer(actor.id);
        KOMEWorldData restored = new KOMEWorldData("wallSaveRegression"); restored.readFromNBT(KOMETacticalMembershipFixtures.save(data));
        assertTrue(new KOMETacticalEditDraft(saved).sameDefinition(new KOMETacticalEditDraft(restored.getTacticalConfigurationSnapshot().findComplex("A"))));
    }
    @Test public void deletionRejectsAssignedBuildsAndSafeDeletionPreservesAreasAndOtherComplexes() {
        membership("A", "B1", KOMETacticalEditDraft.MembershipAction.ASSIGN, null); clean(); NBTTagCompound before = state();
        KOMETacticalEditSnapshot s = open("A", false); KOMETacticalEditSessionManager.Result rejected = action(KOMETacticalEditRequest.Action.DELETE, s);
        assertEquals(REJECTED, rejected.getStatus()); assertTrue(rejected.getSnapshot().getPreflight().getDiagnostics().get(0).contains("B1")); untouched(before); manager.closePlayer(actor.id);
        KOMEForceDeploymentArea area = data.getTacticalConfigurationSnapshot().findForceDeploymentArea("FIELD");
        KOMESiegeComplex b = data.getTacticalConfigurationSnapshot().findComplex("B"); long revision = revision(data);
        assertEquals(DELETED, action(KOMETacticalEditRequest.Action.DELETE, open("EMPTY", false)).getStatus());
        assertNull(data.getTacticalConfigurationSnapshot().findComplex("EMPTY")); assertSame(b, data.getTacticalConfigurationSnapshot().findComplex("B"));
        assertSame(area, data.getTacticalConfigurationSnapshot().findForceDeploymentArea("FIELD")); assertEquals(revision + 1, revision(data)); assertEquals(1, data.dirtyCalls);
    }
    @Test public void lifecyclePublicationFailureRollsBackAndWrongTokenOrPermissionCannotDelete() {
        KOMETacticalEditSnapshot s = open("NEW", true); NBTTagCompound before = state(); data.failDirty = true;
        assertEquals(COMMIT_FAILED, action(KOMETacticalEditRequest.Action.SAVE, s).getStatus()); data.failDirty = false; data.dirtyCalls = 0; untouched(before); manager.closePlayer(actor.id);
        s = open("EMPTY", false); data.failDirty = true; assertEquals(COMMIT_FAILED, action(KOMETacticalEditRequest.Action.DELETE, s).getStatus()); data.failDirty = false; data.dirtyCalls = 0; untouched(before);
        assertEquals(INVALID_SESSION, manager.handle(actor, data, new KOMETacticalEditRequest(KOMETacticalEditRequest.Action.DELETE, s.getScope(), UUID.randomUUID(), s.getDraftSequence(), new byte[0])).getStatus());
        actor.authorized = false; assertEquals(DENIED, action(KOMETacticalEditRequest.Action.DELETE, s).getStatus()); untouched(before);
    }
}
