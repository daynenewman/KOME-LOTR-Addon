package kome.common.data;

import java.util.Collections;
import kome.common.siege.*;
import kome.common.tactical.*;
import kome.common.tactical.edit.*;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.*;
import static kome.common.data.KOMETacticalEditorFixtures.*;
import static kome.common.tactical.edit.KOMETacticalEditSessionManager.Status.*;
import static org.junit.Assert.*;

public class KOMETacticalAreaEditorSessionTest {
    @Rule public final KOMETileTestResources tiles = new KOMETileTestResources();
    private CountingWorld data;
    private Actor actor;
    private KOMETacticalEditSessionManager manager;
    @Before public void setup() {
        data = new CountingWorld(); data.initializeIntegratedWorld(); data.setDirty(false); data.dirtyCalls = 0;
        actor = new Actor(); manager = new KOMETacticalEditSessionManager();
    }
    private KOMETacticalEditSnapshot open(String id, boolean create) {
        KOMETacticalEditSessionManager.Result result = manager.handle(actor, data,
            create ? KOMETacticalEditRequest.create(areaScope(id)) : KOMETacticalEditRequest.open(areaScope(id)));
        assertEquals(OPENED, result.getStatus()); return result.getSnapshot();
    }
    private KOMETacticalEditSessionManager.Result action(KOMETacticalEditRequest.Action action, KOMETacticalEditSnapshot s) {
        return manager.handle(actor, data, KOMETacticalEditRequest.action(action, s));
    }
    private KOMETacticalEditSnapshot update(KOMETacticalEditSnapshot s, KOMEForceDeploymentArea area) {
        KOMETacticalEditSessionManager.Result result = manager.handle(actor, data, KOMETacticalEditRequest.update(s, new KOMETacticalEditDraft(area)));
        assertEquals(UPDATED, result.getStatus()); return result.getSnapshot();
    }
    private KOMETacticalEditSnapshot validNew(String id) { KOMETacticalEditSnapshot s = open(id, true); return update(s, area(id, "Display label", 0)); }
    private NBTTagCompound state() { return KOMETacticalConfigurationCodec.encode(data.getTacticalConfigurationSnapshot()); }
    private void clean() { data.setDirty(false); data.dirtyCalls = 0; }
    @Test public void ordinaryTileWithoutComplexBuildOrGateCanCreateSaveAndRoundTrip() {
        assertTrue(data.builds.isEmpty());
        KOMETacticalEditSnapshot s = validNew(" west_field ");
        assertTrue(data.getTacticalConfigurationSnapshot().getForceDeploymentAreasById().isEmpty()); assertFalse(data.isDirty());
        assertTrue(action(KOMETacticalEditRequest.Action.PREFLIGHT, s).getSnapshot().getPreflight().canSave());
        assertEquals(SAVED, action(KOMETacticalEditRequest.Action.SAVE, s).getStatus());
        assertEquals(1, data.dirtyCalls); assertEquals(1L, data.getTacticalConfigurationSnapshot().getRevision());
        assertEquals(1L, data.getTacticalConfigurationSnapshot().findForceDeploymentArea("WEST_FIELD").getRevision());
        assertTrue(data.getTacticalConfigurationSnapshot().getComplexesById().isEmpty());
        KOMEWorldData restored = new KOMEWorldData("roundTrip"); restored.readFromNBT(KOMETacticalMembershipFixtures.save(data));
        assertEquals(state(), KOMETacticalConfigurationCodec.encode(restored.getTacticalConfigurationSnapshot()));
    }
    @Test public void cancelledCreationAndExpiredCreationNeverPersist() {
        KOMETacticalEditSnapshot s = validNew("FIELD"); NBTTagCompound before = state();
        assertEquals(CANCELLED, action(KOMETacticalEditRequest.Action.CANCEL, s).getStatus()); assertEquals(before, state()); assertEquals(0, data.dirtyCalls);
        manager = new KOMETacticalEditSessionManager(1, 2); validNew("FIELD");
        assertEquals(EXPIRED, manager.tick().get(0).getStatus()); assertEquals(before, state()); assertFalse(data.isDirty());
    }
    @Test public void duplicateCanonicalAreaCreationCannotReplaceOriginal() {
        KOMETacticalEditSnapshot s = validNew("FIELD"); action(KOMETacticalEditRequest.Action.SAVE, s); clean(); NBTTagCompound before = state();
        assertEquals(DUPLICATE_ID, manager.handle(actor, data, KOMETacticalEditRequest.create(areaScope(" field "))).getStatus());
        assertEquals(before, state()); assertFalse(data.isDirty());
    }
    @Test public void labelIsIndependentAndReopenedSaveAdvancesBothRevisionsOnce() {
        action(KOMETacticalEditRequest.Action.SAVE, validNew("FIELD")); clean();
        KOMETacticalEditSnapshot s = update(open("FIELD", false), area("FIELD", "Renamed", 1));
        assertEquals(SAVED, action(KOMETacticalEditRequest.Action.SAVE, s).getStatus());
        assertEquals("Renamed", data.getTacticalConfigurationSnapshot().findForceDeploymentArea("FIELD").getLabel());
        assertEquals(2L, data.getTacticalConfigurationSnapshot().findForceDeploymentArea("FIELD").getRevision());
        assertEquals(2L, data.getTacticalConfigurationSnapshot().getRevision()); assertEquals(1, data.dirtyCalls);
    }
    @Test public void multipleAreasRemainIndependentAndSameLabelIsAllowed() {
        action(KOMETacticalEditRequest.Action.SAVE, validNew("WEST")); action(KOMETacticalEditRequest.Action.SAVE, validNew("EAST"));
        assertEquals(2, data.getTacticalConfigurationSnapshot().listForceDeploymentAreasForTile("T100").size());
        KOMEForceDeploymentArea east = data.getTacticalConfigurationSnapshot().findForceDeploymentArea("EAST");
        KOMETacticalEditSnapshot s = update(open("WEST", false), area("WEST", "Changed", 1)); action(KOMETacticalEditRequest.Action.SAVE, s);
        assertSame(east, data.getTacticalConfigurationSnapshot().findForceDeploymentArea("EAST"));
    }
    @Test public void staleCreationCannotOverwriteNewerAdminData() {
        KOMETacticalEditSnapshot s = validNew("FIELD"); Actor second = new Actor();
        KOMETacticalEditSnapshot other = manager.handle(second, data, KOMETacticalEditRequest.create(areaScope("OTHER"))).getSnapshot();
        assertEquals(SAVED, manager.handle(second, data, KOMETacticalEditRequest.action(KOMETacticalEditRequest.Action.SAVE, other)).getStatus());
        clean(); NBTTagCompound before = state();
        assertEquals(STALE_STORE, action(KOMETacticalEditRequest.Action.SAVE, s).getStatus()); assertEquals(before, state()); assertFalse(data.isDirty());
    }
    @Test public void deleteUnreferencedAreaPublishesOnceAndPreservesOtherObjects() {
        action(KOMETacticalEditRequest.Action.SAVE, validNew("WEST")); action(KOMETacticalEditRequest.Action.SAVE, validNew("EAST")); clean();
        KOMEForceDeploymentArea east = data.getTacticalConfigurationSnapshot().findForceDeploymentArea("EAST");
        assertEquals(DELETED, action(KOMETacticalEditRequest.Action.DELETE, open("WEST", false)).getStatus());
        assertNull(data.getTacticalConfigurationSnapshot().findForceDeploymentArea("WEST"));
        assertSame(east, data.getTacticalConfigurationSnapshot().findForceDeploymentArea("EAST"));
        assertEquals(3L, data.getTacticalConfigurationSnapshot().getRevision()); assertEquals(1, data.dirtyCalls);
    }
    @Test public void referencedAreaDeleteReportsComplexNamesAndChangesNothing() {
        KOMETacticalConfiguration c = new KOMETacticalConfiguration(); c.addForceDeploymentArea(area("FIELD", "Field", 3));
        c.addComplex(KOMESiegeReadinessFixtures.preferred(KOMESiegeReadinessFixtures.empty("FORT_A", "T100", dimension()), "FIELD"));
        KOMETacticalMembershipFixtures.installConfiguration(data, data, c); clean(); NBTTagCompound before = state();
        KOMETacticalEditSessionManager.Result result = action(KOMETacticalEditRequest.Action.DELETE, open("FIELD", false));
        assertEquals(REJECTED, result.getStatus());
        assertTrue(result.getSnapshot().getPreflight().getDiagnostics().get(0).contains("FORT_A"));
        assertEquals(before, state()); assertFalse(data.isDirty()); assertEquals(0, data.dirtyCalls);
    }
    @Test public void staleDeleteAndForgedObjectRevisionDoNotMutate() {
        action(KOMETacticalEditRequest.Action.SAVE, validNew("FIELD")); KOMETacticalEditSnapshot s = open("FIELD", false);
        assertEquals(KOMETacticalEditService.Status.STALE_OBJECT, KOMETacticalEditService.deleteArea(data, s.getScope(), s.getBaseRevision(), 99).getStatus());
        KOMETacticalConfiguration c = data.getTacticalConfigurationSnapshot(); c.addForceDeploymentArea(area("OTHER", "", 0));
        KOMETacticalMembershipFixtures.installConfiguration(data, data, c); clean(); NBTTagCompound before = state();
        assertEquals(STALE_STORE, action(KOMETacticalEditRequest.Action.DELETE, s).getStatus()); assertEquals(before, state()); assertFalse(data.isDirty());
    }
    @Test public void failedCreationAndDeletionPublicationRollBackStateAndDirty() {
        KOMETacticalEditSnapshot s = validNew("FIELD"); NBTTagCompound before = state(); data.failDirty = true;
        assertEquals(COMMIT_FAILED, action(KOMETacticalEditRequest.Action.SAVE, s).getStatus()); assertEquals(before, state()); assertFalse(data.isDirty());
        data.failDirty = false; action(KOMETacticalEditRequest.Action.SAVE, s); clean(); s = open("FIELD", false); before = state(); data.failDirty = true;
        assertEquals(COMMIT_FAILED, action(KOMETacticalEditRequest.Action.DELETE, s).getStatus()); assertEquals(before, state()); assertFalse(data.isDirty());
    }
    @Test public void catalogueIsTileScopedDeterministicPagedAndReadOnly() {
        KOMETacticalConfiguration c = new KOMETacticalConfiguration();
        for (int i = 8; i >= 0; --i) c.addForceDeploymentArea(area("AREA" + i, "", 0));
        c.addForceDeploymentArea(new KOMEForceDeploymentArea("FOREIGN", "T101", dimension(), "", area("X", "", 0).getPrism(), 0));
        KOMETacticalMembershipFixtures.installConfiguration(data, data, c); clean(); NBTTagCompound before = state();
        KOMETacticalAreaCatalog first = KOMETacticalAreaAccess.catalog(data, " t100 ", dimension(), 0);
        assertEquals(9, first.total); assertEquals(5, first.rows.size()); assertEquals("AREA0", first.rows.get(0).id);
        assertEquals(4, KOMETacticalAreaAccess.catalog(data, "T100", dimension(), 1).rows.size());
        assertThrows(UnsupportedOperationException.class, () -> first.rows.clear()); assertEquals(before, state()); assertFalse(data.isDirty());
    }
    @Test public void unknownTileWrongDimensionAndNonAdminCreationAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> KOMETacticalAreaAccess.catalog(data, "NO999", dimension(), 0));
        assertThrows(IllegalArgumentException.class, () -> KOMETacticalAreaAccess.catalog(data, "T100", dimension() + 1, 0));
        actor.authorized = false;
        assertEquals(DENIED, manager.handle(actor, data, KOMETacticalEditRequest.create(areaScope("FIELD"))).getStatus());
        assertEquals(0L, data.getTacticalConfigurationSnapshot().getRevision()); assertEquals(0, data.dirtyCalls);
    }
    @Test public void malformedDraftPreflightHasReadableDiagnosticsAndDoesNotCrashOrPersist() {
        KOMETacticalEditSnapshot s = open("FIELD", true);
        KOMETacticalEditPreflight p = action(KOMETacticalEditRequest.Action.PREFLIGHT, s).getSnapshot().getPreflight();
        assertFalse(p.isStructurallyValid()); assertFalse(p.getDiagnostics().isEmpty());
        assertTrue(data.getTacticalConfigurationSnapshot().getForceDeploymentAreasById().isEmpty());
    }
    @Test public void replayOrChangedOwnerCannotSaveOrDeleteAnotherArea() {
        KOMETacticalEditSnapshot s = open("FIELD", true);
        KOMEForceDeploymentArea wrong = new KOMEForceDeploymentArea("FIELD", "T101", dimension(), "", area("X", "", 0).getPrism(), 0);
        assertEquals(INVALID_DRAFT, manager.handle(actor, data, KOMETacticalEditRequest.update(s, new KOMETacticalEditDraft(wrong))).getStatus());
        assertEquals(REJECTED, action(KOMETacticalEditRequest.Action.DELETE, s).getStatus());
        assertEquals(0, data.dirtyCalls);
    }
}
