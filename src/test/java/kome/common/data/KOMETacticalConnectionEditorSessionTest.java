package kome.common.data;

import java.util.*;
import kome.client.tactical.*;
import kome.common.siege.*;
import kome.common.tactical.*;
import kome.common.tactical.edit.*;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.*;
import static org.junit.Assert.*;
import static kome.common.data.KOMETacticalEditorFixtures.*;
import static kome.common.tactical.edit.KOMETacticalEditSessionManager.Status.*;

public class KOMETacticalConnectionEditorSessionTest {
    @Rule public final KOMETileTestResources tiles = new KOMETileTestResources();
    private CountingWorld data; private Actor actor; private KOMETacticalEditSessionManager sessions;
    @Before public void setup() { data = world(); actor = new Actor(); sessions = new KOMETacticalEditSessionManager(); }
    private KOMESiegeComplex complex() { return data.getTacticalConfigurationSnapshot().findComplex("A"); }
    private void install(KOMESiegeComplex c) {
        KOMETacticalConfiguration config = data.getTacticalConfigurationSnapshot(); config.replaceComplex(c);
        KOMETacticalMembershipFixtures.installConfiguration(data, data, config); clean();
    }
    private void clean() { data.setDirty(false); data.dirtyCalls = 0; }
    private KOMETacticalEditSnapshot open() {
        KOMETacticalEditSessionManager.Result r = sessions.handle(actor, data, KOMETacticalEditRequest.open(complexScope("A")));
        assertEquals(OPENED, r.getStatus()); return r.getSnapshot();
    }
    private KOMETacticalEditSnapshot update(KOMETacticalEditSnapshot s, KOMESiegeComplex c) {
        KOMETacticalEditSessionManager.Result r = sessions.handle(actor, data, KOMETacticalEditRequest.update(s, new KOMETacticalEditDraft(c)));
        assertEquals(UPDATED, r.getStatus()); return r.getSnapshot();
    }
    private KOMETacticalEditSessionManager.Result action(KOMETacticalEditRequest.Action a, KOMETacticalEditSnapshot s) {
        return sessions.handle(actor, data, KOMETacticalEditRequest.action(a,s));
    }
    private KOMESiegeComplex gate(KOMESiegeComplex c, String build, String record) {
        KOMESiegeConnection old = KOMETacticalConnectionDraft.find(c, "ENTRY");
        return KOMETacticalConnectionDraft.edit(c, new KOMESiegeConnection("ENTRY", old.getEndpointA(), old.getEndpointB(), old.getTransitionZoneId(),
            build == null ? null : new KOMEDefensiveGateRef(build,record)));
    }
    private KOMETacticalComplexCatalog catalog(KOMETacticalComplexCatalog.Kind kind) {
        return KOMETacticalComplexAccess.catalog(data,kind,"T100","A",dimension(),0);
    }
    @Test public void createSaveReopenDeleteConnectionPreservesGeometryMembershipAndOtherComplex() {
        KOMESiegeComplex c = KOMETacticalConnectionDraft.remove(complex(), "ENTRY");
        c = KOMETacticalComplexDraft.preferred(c, "FIELD"); install(c);
        KOMETacticalMembershipFixtures.assign(data,"B1","A"); clean();
        KOMESiegeComplex other = data.getTacticalConfigurationSnapshot().findComplex("B");
        KOMEForceDeploymentArea area = data.getTacticalConfigurationSnapshot().findForceDeploymentArea("FIELD");
        long rev = revision(data), object = complex().getRevision(); KOMETacticalEditSnapshot s = open();
        KOMESiegeComplex edited = KOMETacticalConnectionDraft.create(s.getDraft().getComplex(),"EntryLocal");
        s = update(s,edited); assertNull(KOMETacticalConnectionDraft.find(complex(),"EntryLocal")); assertFalse(data.isDirty());
        KOMETacticalEditPreflight p = action(KOMETacticalEditRequest.Action.PREFLIGHT,s).getSnapshot().getPreflight(); assertTrue(p.canSave()); assertTrue(p.isReady());
        assertEquals(SAVED,action(KOMETacticalEditRequest.Action.SAVE,s).getStatus()); assertEquals(rev+1,revision(data)); assertEquals(object+1,complex().getRevision()); assertEquals(1,data.dirtyCalls);
        assertEquals(c.getNormalSegments(),complex().getNormalSegments()); assertEquals(c.getWallZones(),complex().getWallZones()); assertEquals(c.getTransitionZones(),complex().getTransitionZones());
        assertEquals("FIELD",complex().getPreferredForceDeploymentAreaId().get()); assertEquals("A",data.getTacticalConfigurationSnapshot().findAssignedComplexId("B1").get());
        assertSame(other,data.getTacticalConfigurationSnapshot().findComplex("B")); assertSame(area,data.getTacticalConfigurationSnapshot().findForceDeploymentArea("FIELD"));
        KOMEWorldData restored = new KOMEWorldData("restored"); restored.readFromNBT(KOMETacticalMembershipFixtures.save(data));
        assertEquals(complex().getConnections(),restored.getTacticalConfigurationSnapshot().findComplex("A").getConnections());
        s = open(); assertEquals("EntryLocal",s.getDraft().getComplex().getConnections().get(0).getId());
        s = update(s,KOMETacticalConnectionDraft.remove(s.getDraft().getComplex(),"EntryLocal")); clean();
        assertEquals(SAVED,action(KOMETacticalEditRequest.Action.SAVE,s).getStatus()); assertTrue(complex().getConnections().isEmpty());
        assertEquals(c.getTransitionZones(),complex().getTransitionZones()); assertNotNull(data.getBuild("B1")); assertEquals(1,data.dirtyCalls);
    }
    @Test public void gateCatalogueOnlyOffersValidAssignedBuildsAndKeepsCompositeIds() {
        KOMETacticalMembershipFixtures.link(data,"B1"); KOMETacticalMembershipFixtures.link(data,"B2"); clean();
        assertEquals(0,catalog(KOMETacticalComplexCatalog.Kind.GATES).total);
        KOMETacticalMembershipFixtures.assign(data,"B1","A"); KOMETacticalMembershipFixtures.assign(data,"B2","B"); clean();
        KOMETacticalComplexCatalog page = catalog(KOMETacticalComplexCatalog.Kind.GATES); assertEquals(1,page.total);
        assertEquals("B1",page.rows.get(0).relatedId); assertEquals("G1",page.rows.get(0).id); assertTrue(page.rows.get(0).detail.contains("UNKNOWN"));
        KOMETacticalMembershipService.reassignBuild(data,"B2","B","A",revision(data)); clean();
        page = catalog(KOMETacticalComplexCatalog.Kind.GATES); assertEquals(2,page.total); assertEquals("B1",page.rows.get(0).relatedId); assertEquals("B2",page.rows.get(1).relatedId);
        assertEquals("G1",page.rows.get(0).id); assertEquals("G1",page.rows.get(1).id); assertEquals(0,data.dirtyCalls);
    }
    @Test public void inactiveNonDefensiveWrongTileOrDimensionBuildNotOffered() {
        KOMETacticalMembershipFixtures.link(data,"B1"); KOMETacticalMembershipFixtures.assign(data,"B1","A");
        KOMEPlayerBuild build = data.getBuild("B1"); clean();
        build.active=false; assertEquals(0,catalog(KOMETacticalComplexCatalog.Kind.GATES).total); build.active=true;
        build.type=KOMEBuildType.NORMAL; assertEquals(0,catalog(KOMETacticalComplexCatalog.Kind.GATES).total); build.type=KOMEBuildType.DEFENSIVE;
        build.tileId="T101"; assertEquals(0,catalog(KOMETacticalComplexCatalog.Kind.GATES).total); build.tileId="T100";
        build.dimension++; assertEquals(0,catalog(KOMETacticalComplexCatalog.Kind.GATES).total); assertEquals(0,data.dirtyCalls);
    }
    @Test public void serverRejectsForgedNewGateForMissingUnassignedOtherComplexOrMissingRecord() {
        KOMETacticalMembershipFixtures.link(data,"B1"); KOMETacticalMembershipFixtures.link(data,"B2"); KOMETacticalMembershipFixtures.assign(data,"B2","B"); clean();
        for (String build : Arrays.asList("MISSING","B1","B2")) {
            NBTTagCompound before = KOMETacticalMembershipFixtures.save(data); long rev=revision(data);
            KOMETacticalEditSnapshot s = update(open(),gate(complex(),build,"G1"));
            assertFalse(action(KOMETacticalEditRequest.Action.PREFLIGHT,s).getSnapshot().getPreflight().canSave());
            assertEquals(REJECTED,action(KOMETacticalEditRequest.Action.SAVE,s).getStatus()); assertEquals(before,KOMETacticalMembershipFixtures.save(data));
            assertEquals(rev,revision(data)); assertEquals(0,data.dirtyCalls); action(KOMETacticalEditRequest.Action.CANCEL,s);
        }
        KOMETacticalMembershipFixtures.assign(data,"B1","A"); clean();
        KOMETacticalEditSnapshot s=update(open(),gate(complex(),"B1","MISSING"));
        assertEquals(REJECTED,action(KOMETacticalEditRequest.Action.SAVE,s).getStatus()); assertEquals(0,data.dirtyCalls);
    }
    @Test public void legalGatedSaveIsReadyWithUnloadedPhysicalGateAndPreservesKom10() {
        KOMETacticalMembershipFixtures.link(data,"B1"); KOMETacticalMembershipFixtures.assign(data,"B1","A"); clean();
        NBTTagCompound build = data.getBuild("B1").writeToNBT();
        KOMETacticalEditSnapshot s = update(open(),gate(complex(),"B1","G1"));
        KOMETacticalEditPreflight p=action(KOMETacticalEditRequest.Action.PREFLIGHT,s).getSnapshot().getPreflight();
        assertTrue(p.canSave()); assertTrue(p.isReady()); assertTrue(p.getDiagnostics().stream().anyMatch(d->d.contains("PHYSICAL_AVAILABILITY_UNKNOWN")));
        assertEquals(SAVED,action(KOMETacticalEditRequest.Action.SAVE,s).getStatus()); assertEquals(1,data.dirtyCalls);
        assertEquals(build,data.getBuild("B1").writeToNBT()); assertTrue(KOMESiegeReadinessResolver.evaluate(data,"A").isReady());
        KOMETacticalComplexCatalog page = catalog(KOMETacticalComplexCatalog.Kind.CONNECTIONS); assertTrue(page.rows.get(0).detail.contains("Logical reference valid")); assertTrue(page.rows.get(0).detail.contains("UNKNOWN"));
    }
    @Test public void brokenBindingRemainsSelectableLogicalRecordButBlocksReadiness() {
        KOMEDefensiveGateRecord record=KOMETacticalMembershipFixtures.link(data,"B1"); KOMETacticalMembershipFixtures.assign(data,"B1","A"); record.gateDimension=dimension()+1; clean();
        assertEquals(1,catalog(KOMETacticalComplexCatalog.Kind.GATES).total); assertTrue(catalog(KOMETacticalComplexCatalog.Kind.GATES).rows.get(0).detail.contains("BROKEN"));
        KOMETacticalEditSnapshot s=update(open(),gate(complex(),"B1","G1"));
        KOMETacticalEditPreflight p=action(KOMETacticalEditRequest.Action.PREFLIGHT,s).getSnapshot().getPreflight();
        assertTrue(p.canSave()); assertFalse(p.isReady()); assertTrue(p.getDiagnostics().stream().anyMatch(d->d.contains("PHYSICAL_BINDING_INVALID")));
        assertEquals(SAVED,action(KOMETacticalEditRequest.Action.SAVE,s).getStatus()); assertTrue(complex().getConnections().get(0).isGated());
    }
    @Test public void retainedMissingBuildOrGateCanBeEditedAndSavedWithoutBecomingGateless() {
        for (String build : Arrays.asList("MISSING","B1")) {
            install(gate(complex(),build,"GONE")); clean(); KOMETacticalEditSnapshot s=open();
            KOMESiegeConnection old=s.getDraft().getComplex().getConnections().get(0);
            KOMESiegeComplex edited=KOMETacticalConnectionDraft.edit(s.getDraft().getComplex(),new KOMESiegeConnection(old.getId(),
                KOMESiegeAreaRef.exterior(),KOMESiegeAreaRef.normal("UNKNOWN_" + build),old.getTransitionZoneId(),old.getGateRef().get()));
            s=update(s,edited); assertTrue(action(KOMETacticalEditRequest.Action.PREFLIGHT,s).getSnapshot().getPreflight().canSave());
            assertEquals(SAVED,action(KOMETacticalEditRequest.Action.SAVE,s).getStatus()); assertEquals(old.getGateRef(),complex().getConnections().get(0).getGateRef());
            String detail=catalog(KOMETacticalComplexCatalog.Kind.CONNECTIONS).rows.get(0).detail; assertTrue(detail.contains(build.equals("MISSING")?"BUILD_MISSING":"GATE_RECORD_MISSING"));
        }
    }
    @Test public void relinkAndUnlinkNeverEraseAuthoredReferenceAndExplicitGatelessWorks() {
        KOMETacticalMembershipFixtures.link(data,"B1"); KOMETacticalMembershipFixtures.assign(data,"B1","A"); install(gate(complex(),"B1","G1"));
        KOMEDefensiveGateRef ref=complex().getConnections().get(0).getGateRef().get();
        assertTrue(KOMEDefensiveGateLinkService.relink(data,data.getBuild("B1"),"G1",KOMETacticalMembershipFixtures.inspection(UUID.randomUUID(),2,40),true,null,"Admin",true,20).isSuccessful());
        assertEquals(ref,complex().getConnections().get(0).getGateRef().get());
        assertTrue(KOMEDefensiveGateLinkService.unlink(data,data.getBuild("B1"),"G1",null,"Admin",true,21).isSuccessful()); clean();
        assertTrue(catalog(KOMETacticalComplexCatalog.Kind.CONNECTIONS).rows.get(0).detail.contains("GATE_RECORD_MISSING")); assertEquals(ref,complex().getConnections().get(0).getGateRef().get());
        KOMETacticalEditSnapshot s=update(open(),gate(complex(),null,null)); assertEquals(SAVED,action(KOMETacticalEditRequest.Action.SAVE,s).getStatus()); assertFalse(complex().getConnections().get(0).isGated());
        assertTrue(data.getBuild("B1").getDefensiveGateRecords().isEmpty());
    }
    @Test public void cancelledOrStaleTopologyCannotReplaceLiveData() {
        KOMETacticalEditSnapshot s=update(open(),KOMETacticalConnectionDraft.remove(complex(),"ENTRY")); NBTTagCompound before=KOMETacticalMembershipFixtures.save(data);
        action(KOMETacticalEditRequest.Action.CANCEL,s); assertEquals(before,KOMETacticalMembershipFixtures.save(data)); assertFalse(data.isDirty());
        s=update(open(),KOMETacticalConnectionDraft.remove(complex(),"ENTRY")); KOMETacticalMembershipFixtures.assign(data,"B1","A"); clean(); before=KOMETacticalMembershipFixtures.save(data);
        assertEquals(STALE_STORE,action(KOMETacticalEditRequest.Action.SAVE,s).getStatus()); assertEquals(before,KOMETacticalMembershipFixtures.save(data)); assertEquals(0,data.dirtyCalls);
    }
    @Test public void parallelEntrancesCanReferenceDistinctGatesFromOneBuildAndBecomeReady() {
        KOMETacticalMembershipFixtures.link(data,"B1"); KOMETacticalMembershipFixtures.assign(data,"B1","A");
        assertTrue(KOMEDefensiveGateLinkService.link(data,data.getBuild("B1"),KOMETacticalMembershipFixtures.inspection(UUID.randomUUID(),1,50),null,"Admin",true,20).isSuccessful());
        KOMESiegeComplex c=gate(complex(),"B1","G1"); c=KOMETacticalComplexDraft.create(c,KOMETacticalComplexDraft.ZoneType.TRANSITION,"SECOND");
        c=KOMETacticalComplexDraft.edit(c,KOMETacticalComplexDraft.ZoneType.TRANSITION,"SECOND","",KOMESiegeReadinessFixtures.prism(-2,6,0,8));
        c=KOMETacticalConnectionDraft.create(c,"ENTRY2"); KOMESiegeConnection entry=KOMETacticalConnectionDraft.find(c,"ENTRY2");
        c=KOMETacticalConnectionDraft.edit(c,new KOMESiegeConnection(entry.getId(),KOMESiegeAreaRef.exterior(),KOMESiegeAreaRef.normal("A"),"SECOND",new KOMEDefensiveGateRef("B1","G2")));
        clean(); KOMETacticalEditSnapshot s=update(open(),c); assertTrue(action(KOMETacticalEditRequest.Action.PREFLIGHT,s).getSnapshot().getPreflight().isReady());
        assertEquals(SAVED,action(KOMETacticalEditRequest.Action.SAVE,s).getStatus()); assertEquals(2,complex().getConnections().size());
        assertEquals(2,data.getBuild("B1").getDefensiveGateRecords().size()); assertEquals(1,data.dirtyCalls);
    }
    private KOMESiegeComplex rawParallel(KOMESiegeComplex base,String first,String second) {
        // Direct model construction represents persisted historical authoring or a crafted wire draft.
        return new KOMESiegeComplex(base.getComplexId(),base.getTileId(),base.getDimensionId(),base.getRevision(),
            base.getNormalSegments(),base.getWallZones(),Arrays.asList(base.getTransitionZones().get(0),
                new KOMETransitionZone("SECOND","",KOMESiegeReadinessFixtures.prism(-2,6,0,8))),base.getPreferredForceDeploymentAreaId().orElse(null),
            Arrays.asList(new KOMESiegeConnection("ENTRY",KOMESiegeAreaRef.exterior(),KOMESiegeAreaRef.normal("A"),"T_ENTRY",first==null?null:new KOMEDefensiveGateRef(first,"G1")),
                new KOMESiegeConnection("ENTRY2",KOMESiegeAreaRef.exterior(),KOMESiegeAreaRef.normal("A"),"SECOND",second==null?null:new KOMEDefensiveGateRef(second,"G1"))));
    }
    @Test public void craftedDuplicateIncludingBuildCaseAliasIsRejectedAtomicallyServerSide() {
        KOMETacticalMembershipFixtures.link(data,"B1"); KOMETacticalMembershipFixtures.assign(data,"B1","A"); clean();
        for (String alias:Arrays.asList("B1","b1")) {
            NBTTagCompound before=KOMETacticalMembershipFixtures.save(data); long revision=revision(data);
            KOMETacticalEditSnapshot s=update(open(),rawParallel(complex(),"B1",alias));
            KOMETacticalEditPreflight p=action(KOMETacticalEditRequest.Action.PREFLIGHT,s).getSnapshot().getPreflight();
            assertFalse(p.canSave()); assertTrue(p.getDiagnostics().stream().anyMatch(d->d.contains("ENTRY2")&&d.contains("B1")&&d.contains("G1")));
            assertEquals(REJECTED,action(KOMETacticalEditRequest.Action.SAVE,s).getStatus());
            assertEquals(before,KOMETacticalMembershipFixtures.save(data)); assertEquals(revision,revision(data)); assertFalse(data.isDirty()); assertEquals(0,data.dirtyCalls);
            action(KOMETacticalEditRequest.Action.CANCEL,s);
        }
    }
    @Test public void historicalDuplicatePersistsIsInspectableAndCanBeRetainedOrRepaired() {
        KOMETacticalMembershipFixtures.link(data,"B1"); KOMETacticalMembershipFixtures.assign(data,"B1","A");
        install(rawParallel(complex(),"B1","B1")); NBTTagCompound before=KOMETacticalMembershipFixtures.save(data);
        KOMETacticalEditSnapshot s=open(); KOMETacticalEditPreflight p=action(KOMETacticalEditRequest.Action.PREFLIGHT,s).getSnapshot().getPreflight();
        assertTrue(p.canSave()); assertFalse(p.isReady());
        assertTrue(p.getDiagnostics().stream().anyMatch(d->d.contains("CONNECTION_GATE_REUSED")&&d.contains("ENTRY2")&&d.contains("ENTRY")));
        assertEquals(before,KOMETacticalMembershipFixtures.save(data)); assertEquals(0,data.dirtyCalls);
        KOMESiegeComplex edited=KOMETacticalComplexDraft.edit(s.getDraft().getComplex(),KOMETacticalComplexDraft.ZoneType.NORMAL,"A","Retained authoring",complex().getNormalSegments().get(0).getPrism());
        s=update(s,edited); assertEquals(SAVED,action(KOMETacticalEditRequest.Action.SAVE,s).getStatus());
        assertEquals(2,complex().getConnections().stream().filter(KOMESiegeConnection::isGated).count());
        KOMEWorldData restored=new KOMEWorldData("duplicate-restored"); restored.readFromNBT(KOMETacticalMembershipFixtures.save(data));
        assertEquals(complex().getConnections(),restored.getTacticalConfigurationSnapshot().findComplex("A").getConnections());
        clean(); s=update(open(),gate(complex(),null,null)); p=action(KOMETacticalEditRequest.Action.PREFLIGHT,s).getSnapshot().getPreflight();
        assertTrue(p.canSave()); assertTrue(p.isReady()); assertEquals(SAVED,action(KOMETacticalEditRequest.Action.SAVE,s).getStatus());
        assertTrue(KOMESiegeReadinessResolver.evaluate(data,"A").isReady());
    }
    @Test public void savedGateChangeGatelessAndDeletionEachFreeTheOldGate() {
        KOMETacticalMembershipFixtures.link(data,"B1"); KOMETacticalMembershipFixtures.assign(data,"B1","A");
        assertTrue(KOMEDefensiveGateLinkService.link(data,data.getBuild("B1"),KOMETacticalMembershipFixtures.inspection(UUID.randomUUID(),1,50),null,"Admin",true,20).isSuccessful());
        for (String operation:Arrays.asList("change","gateless","delete")) {
            install(rawParallel(complex(),"B1",null));
            KOMESiegeComplex edited=operation.equals("delete")?KOMETacticalConnectionDraft.remove(complex(),"ENTRY"):
                gate(complex(),operation.equals("change")?"B1":null,operation.equals("change")?"G2":null);
            KOMETacticalEditSnapshot s=update(open(),edited); assertEquals(SAVED,action(KOMETacticalEditRequest.Action.SAVE,s).getStatus());
            clean(); KOMESiegeConnection old=KOMETacticalConnectionDraft.find(complex(),"ENTRY2");
            edited=KOMETacticalConnectionDraft.edit(complex(),new KOMESiegeConnection(old.getId(),old.getEndpointA(),old.getEndpointB(),old.getTransitionZoneId(),new KOMEDefensiveGateRef("B1","G1")));
            s=update(open(),edited); assertTrue(action(KOMETacticalEditRequest.Action.PREFLIGHT,s).getSnapshot().getPreflight().canSave());
            assertEquals(SAVED,action(KOMETacticalEditRequest.Action.SAVE,s).getStatus()); assertEquals(1,data.dirtyCalls);
            assertEquals(new KOMEDefensiveGateRef("B1","G1"),KOMETacticalConnectionDraft.find(complex(),"ENTRY2").getGateRef().get());
        }
    }
    @Test public void editingSameConnectionRetainingGateIsAllowedAndCatalogNamesItsOwner() {
        KOMETacticalMembershipFixtures.link(data,"B1"); KOMETacticalMembershipFixtures.assign(data,"B1","A"); install(gate(complex(),"B1","G1"));
        assertTrue(catalog(KOMETacticalComplexCatalog.Kind.GATES).rows.get(0).detail.contains("Used by ENTRY"));
        KOMESiegeConnection own=complex().getConnections().get(0);
        KOMESiegeComplex edited=KOMETacticalConnectionDraft.edit(complex(),new KOMESiegeConnection(own.getId(),own.getEndpointB(),own.getEndpointA(),own.getTransitionZoneId(),own.getGateRef().get()));
        KOMETacticalEditSnapshot s=update(open(),edited); assertTrue(action(KOMETacticalEditRequest.Action.PREFLIGHT,s).getSnapshot().getPreflight().canSave());
        // Endpoint reversal is the same undirected definition, hence a legal no-op.
        assertEquals(NO_CHANGE,action(KOMETacticalEditRequest.Action.SAVE,s).getStatus()); assertEquals(0,data.dirtyCalls);
        edited=KOMETacticalComplexDraft.edit(complex(),KOMETacticalComplexDraft.ZoneType.NORMAL,"A","Retain gate while editing",complex().getNormalSegments().get(0).getPrism());
        s=update(open(),edited); assertEquals(SAVED,action(KOMETacticalEditRequest.Action.SAVE,s).getStatus());
        assertEquals(own.getGateRef(),complex().getConnections().get(0).getGateRef());
    }
    @Test public void branchingConnectionsAuthoredViaDraftBecomeReadyAndUnreachableNormalDoesNot() {
        KOMESiegeComplex branch=KOMESiegeReadinessFixtures.branching(); install(new KOMESiegeComplex("A","T100",dimension(),complex().getRevision(),
            branch.getNormalSegments(),branch.getWallZones(),branch.getTransitionZones(),null,Collections.emptyList()));
        KOMETacticalMembershipFixtures.assign(data,"B1","A"); KOMESiegeComplex c=complex();
        for (KOMESiegeConnection connection : branch.getConnections()) { c=KOMETacticalConnectionDraft.create(c,connection.getId()); c=KOMETacticalConnectionDraft.edit(c,connection); }
        clean(); KOMETacticalEditSnapshot s=update(open(),c); assertTrue(action(KOMETacticalEditRequest.Action.PREFLIGHT,s).getSnapshot().getPreflight().isReady());
        assertEquals(SAVED,action(KOMETacticalEditRequest.Action.SAVE,s).getStatus());
        KOMESiegeComplex disconnected=KOMETacticalConnectionDraft.remove(complex(),"AC");
        disconnected=KOMETacticalComplexDraft.remove(disconnected,KOMETacticalComplexDraft.ZoneType.TRANSITION,"T_AC");
        s=update(open(),disconnected); KOMETacticalEditPreflight p=action(KOMETacticalEditRequest.Action.PREFLIGHT,s).getSnapshot().getPreflight();
        assertFalse(p.isReady()); assertTrue(p.canSave()); assertTrue(p.getDiagnostics().stream().anyMatch(d->d.contains("NORMAL_SEGMENT_UNREACHABLE")));
    }
    @Test public void reusedTransitionRemainsSaveableInvalidAuthoringNotReady() {
        KOMETacticalMembershipFixtures.assign(data,"B1","A"); clean();
        KOMETacticalEditSnapshot s=update(open(),KOMETacticalConnectionDraft.create(complex(),"EXTRA"));
        KOMETacticalEditPreflight p=action(KOMETacticalEditRequest.Action.PREFLIGHT,s).getSnapshot().getPreflight();
        assertTrue(p.canSave()); assertFalse(p.isReady()); assertTrue(p.getDiagnostics().stream().anyMatch(d->d.contains("CONNECTION_TRANSITION_REUSED")));
        assertEquals(SAVED,action(KOMETacticalEditRequest.Action.SAVE,s).getStatus()); assertEquals(2,complex().getConnections().size());
    }
    @Test public void readOnlyCataloguesDoNotMutateWorldOrForceLoadPhysicalDimension() {
        KOMETacticalMembershipFixtures.link(data,"B1"); KOMETacticalMembershipFixtures.assign(data,"B1","A"); install(gate(complex(),"B1","G1")); clean();
        NBTTagCompound before=KOMETacticalMembershipFixtures.save(data); long rev=revision(data);
        assertNull(net.minecraftforge.common.DimensionManager.getWorld(dimension())); catalog(KOMETacticalComplexCatalog.Kind.GATES); catalog(KOMETacticalComplexCatalog.Kind.CONNECTIONS);
        assertNull(net.minecraftforge.common.DimensionManager.getWorld(dimension())); assertEquals(before,KOMETacticalMembershipFixtures.save(data)); assertEquals(rev,revision(data)); assertEquals(0,data.dirtyCalls);
    }
}
