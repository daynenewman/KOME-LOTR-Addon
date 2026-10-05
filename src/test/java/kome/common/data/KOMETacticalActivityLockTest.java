package kome.common.data;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import kome.client.tactical.KOMETacticalComplexDraft;
import kome.common.siege.*;
import kome.common.tactical.*;
import kome.common.tactical.edit.*;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.*;
import static org.junit.Assert.*;
import static kome.common.data.KOMETacticalEditorFixtures.*;
import static kome.common.tactical.edit.KOMETacticalEditSessionManager.Status.*;

public class KOMETacticalActivityLockTest {
    @Rule public final KOMETileTestResources tiles = new KOMETileTestResources();
    private CountingWorld data; private Actor actor; private KOMETacticalEditSessionManager sessions;
    @Before public void setup() { data=world(); actor=new Actor(); sessions=new KOMETacticalEditSessionManager(); }
    private KOMESiegeComplex complex() { return data.getTacticalConfigurationSnapshot().findComplex("A"); }
    private void lockA() { data.setTacticalActivityLockProvider((tile,dim,id)->id.equals("A")); }
    private KOMETacticalEditSnapshot open() {
        KOMETacticalEditSessionManager.Result r=sessions.handle(actor,data,KOMETacticalEditRequest.open(complexScope("A")));
        assertEquals(OPENED,r.getStatus()); return r.getSnapshot();
    }
    private KOMESiegeComplex edit(KOMESiegeComplex c) {
        return KOMETacticalComplexDraft.edit(c,KOMETacticalComplexDraft.ZoneType.NORMAL,"A","Edited courtyard",c.getNormalSegments().get(0).getPrism());
    }
    private void clean() { data.setDirty(false); data.dirtyCalls=0; }
    @Test public void unlockedDefaultPreservesCurrentSaveBehavior() {
        KOMETacticalEditSnapshot s=open();
        s=sessions.handle(actor,data,KOMETacticalEditRequest.update(s,new KOMETacticalEditDraft(edit(complex())))).getSnapshot();
        long revision=revision(data); assertEquals(SAVED,sessions.handle(actor,data,KOMETacticalEditRequest.action(KOMETacticalEditRequest.Action.SAVE,s)).getStatus());
        assertEquals(revision+1,revision(data)); assertEquals(1,data.dirtyCalls);
    }
    @Test public void lockedOpenUsesCanonicalComplexAndAuthoritativeLocationButDoesNotLockOtherScopes() {
        List<String> queried=new ArrayList<>();
        data.setTacticalActivityLockProvider((tile,dim,id)->{ queried.add(tile+"/"+dim+"/"+id); return id.equals("A"); });
        assertEquals(ACTIVITY_LOCKED,sessions.handle(actor,data,KOMETacticalEditRequest.open(complexScope(" a "))).getStatus());
        assertEquals(Collections.singletonList("T100/"+dimension()+"/A"),queried);
        assertEquals(OPENED,sessions.handle(actor,data,KOMETacticalEditRequest.open(complexScope("B"))).getStatus());
        sessions.closePlayer(actor.id);
        assertEquals(OPENED,sessions.handle(actor,data,KOMETacticalEditRequest.open(areaScope("FIELD"))).getStatus());
        assertEquals(0,data.dirtyCalls);
    }
    @Test public void lockActivatedAfterOpenRejectsSaveAndKeepsDraftAndCancelWorking() {
        KOMETacticalEditSnapshot s=open();
        s=sessions.handle(actor,data,KOMETacticalEditRequest.update(s,new KOMETacticalEditDraft(edit(complex())))).getSnapshot();
        NBTTagCompound before=KOMETacticalMembershipFixtures.save(data); lockA();
        assertEquals(ACTIVITY_LOCKED,sessions.handle(actor,data,KOMETacticalEditRequest.open(complexScope("A"))).getStatus());
        KOMETacticalEditSessionManager.Result result=sessions.handle(actor,data,KOMETacticalEditRequest.action(KOMETacticalEditRequest.Action.SAVE,s));
        assertEquals(ACTIVITY_LOCKED,result.getStatus()); assertFalse(result.getSnapshot().isClosed());
        assertEquals("Edited courtyard",result.getSnapshot().getDraft().getComplex().getNormalSegments().get(0).getLabel());
        assertTrue(result.getSnapshot().getPreflight().getDiagnostics().get(0).contains("ACTIVITY_LOCKED"));
        assertEquals(before,KOMETacticalMembershipFixtures.save(data)); assertEquals(0,data.dirtyCalls);
        assertEquals(CANCELLED,sessions.handle(actor,data,KOMETacticalEditRequest.action(KOMETacticalEditRequest.Action.CANCEL,s)).getStatus());
    }
    @Test public void lockIsRecheckedAtDefinitionPublicationAfterSuccessfulPreflight() {
        AtomicInteger reads=new AtomicInteger(); data.setTacticalActivityLockProvider((tile,dim,id)->reads.incrementAndGet()>=2);
        NBTTagCompound before=KOMETacticalMembershipFixtures.save(data);
        KOMETacticalEditService.Result result=KOMETacticalEditService.save(data,complexScope("A"),new KOMETacticalEditDraft(edit(complex())),revision(data),complex().getRevision());
        assertEquals(KOMETacticalEditService.Status.ACTIVITY_LOCKED,result.getStatus()); assertEquals(2,reads.get());
        assertEquals(before,KOMETacticalMembershipFixtures.save(data)); assertFalse(data.isDirty()); assertEquals(0,data.dirtyCalls);
    }
    @Test public void directPublicationAndComplexDeletionCannotBypassLock() {
        KOMETacticalConfiguration replacement=data.getTacticalConfigurationSnapshot();
        replacement.replaceComplex(new KOMETacticalEditDraft(edit(complex())).atRevision(complex().getRevision()+1).getComplex());
        lockA(); long revision=revision(data); NBTTagCompound before=KOMETacticalMembershipFixtures.save(data);
        assertThrows(KOMETacticalActivityLock.LockedException.class,()->data.publishTacticalDefinition(revision,"A",null,replacement));
        assertFalse(KOMETacticalEditService.deleteDefinition(data,complexScope("A"),revision,complex().getRevision()).getPreflight().canSave());
        assertEquals(before,KOMETacticalMembershipFixtures.save(data)); assertEquals(0,data.dirtyCalls);
    }
    @Test public void membershipProtectsBothOldAndNewOwnersIncludingFinalPublication() {
        KOMETacticalMembershipFixtures.assign(data,"B1","A"); clean(); lockA(); long rev=revision(data);
        assertEquals(KOMETacticalMembershipService.Status.ACTIVITY_LOCKED,KOMETacticalMembershipService.assignBuild(data,"B2","A",rev).getStatus());
        assertEquals(KOMETacticalMembershipService.Status.ACTIVITY_LOCKED,KOMETacticalMembershipService.unassignBuild(data,"B1",rev).getStatus());
        assertEquals(KOMETacticalMembershipService.Status.ACTIVITY_LOCKED,KOMETacticalMembershipService.reassignBuild(data,"B1","A","B",rev).getStatus());
        assertEquals(0,data.dirtyCalls); assertEquals(rev,revision(data));
        KOMETacticalConfiguration candidate=data.getTacticalConfigurationSnapshot(); candidate.reassignBuild("B1","A","B");
        assertThrows(KOMETacticalActivityLock.LockedException.class,()->data.publishTacticalMembership(rev,candidate));
        assertTrue(KOMETacticalMembershipService.assignBuild(data,"B2","B",rev).isChanged());
        clean(); data.setTacticalActivityLockProvider((tile,dim,id)->id.equals("EMPTY"));
        assertEquals(KOMETacticalMembershipService.Status.ACTIVITY_LOCKED,KOMETacticalMembershipService.reassignBuild(data,"B2","B","EMPTY",revision(data)).getStatus());
    }
    @Test public void everyKindOfDefinitionEditIncludingLogicalGateRemovalIsRejectedWhileLocked() {
        KOMETacticalMembershipFixtures.link(data,"B1"); KOMETacticalMembershipFixtures.assign(data,"B1","A"); clean(); lockA();
        for (KOMETacticalComplexDraft.ZoneType type:KOMETacticalComplexDraft.ZoneType.values()) {
            KOMESiegeComplex edited=KOMETacticalComplexDraft.create(complex(),type,"NEW_"+type);
            assertFalse(KOMETacticalEditService.preflight(data,complexScope("A"),new KOMETacticalEditDraft(edited),revision(data),complex().getRevision()).canSave());
        }
        KOMESiegeConnection entry=complex().getConnections().get(0);
        KOMESiegeComplex gated=new KOMESiegeComplex("A","T100",dimension(),complex().getRevision(),complex().getNormalSegments(),complex().getWallZones(),complex().getTransitionZones(),null,
            Collections.singletonList(new KOMESiegeConnection(entry.getId(),entry.getEndpointA(),entry.getEndpointB(),entry.getTransitionZoneId(),new KOMEDefensiveGateRef("B1","G1"))));
        assertFalse(KOMETacticalEditService.preflight(data,complexScope("A"),new KOMETacticalEditDraft(gated),revision(data),complex().getRevision()).canSave());
        assertEquals(0,data.dirtyCalls);
    }
    @Test public void defensiveHoursAndGateRecordMutationsShareTheLockWithoutChangingKom10Records() {
        KOMETacticalMembershipFixtures.link(data,"B1"); KOMETacticalMembershipFixtures.assign(data,"B1","A");
        KOMEPlayerBuild build=data.getBuild("B1");
        assertTrue(KOMEBuildService.setApprovedHours(data,build,null,"Admin",true,"10",1).allowed);
        KOMEBuildContribution pending=KOMEBuildService.addSubmission(data,build,UUID.randomUUID(),"Player","gondor",100,false,2);
        clean(); NBTTagCompound before=KOMETacticalMembershipFixtures.save(data); lockA();
        assertFalse(KOMEBuildService.setApprovedHours(data,build,null,"Admin",true,"20",3).allowed);
        assertFalse(KOMEBuildService.decideSubmission(data,build,pending.id,null,"Admin",true,true,"approve",3).allowed);
        assertFalse(KOMEBuildService.adjustSubmission(data,build,pending.id,null,"Admin",true,"2","adjust",3).allowed);
        assertThrows(KOMETacticalActivityLock.LockedException.class,()->KOMEBuildService.addSubmission(data,build,UUID.randomUUID(),"Player","gondor",100,false,3));
        assertFalse(KOMEBuildService.deleteBuild(data,build,null,"Admin",true,"delete",3).allowed);
        assertFalse(KOMEDefensiveGateLinkService.link(data,build,KOMETacticalMembershipFixtures.inspection(UUID.randomUUID(),1,50),null,"Admin",true,3).isSuccessful());
        assertFalse(KOMEDefensiveGateLinkService.unlink(data,build,"G1",null,"Admin",true,3).isSuccessful());
        assertFalse(KOMEDefensiveGateLinkService.relink(data,build,"G1",KOMETacticalMembershipFixtures.inspection(UUID.randomUUID(),2,50),true,null,"Admin",true,3).isSuccessful());
        assertFalse(KOMEDefensiveGateLinkService.refresh(data,build,"G1",KOMETacticalMembershipFixtures.inspection(UUID.randomUUID(),2,50),null,"Admin",true,3).isSuccessful());
        assertEquals(before,KOMETacticalMembershipFixtures.save(data)); assertEquals(0,data.dirtyCalls);
        assertTrue(KOMEBuildService.setApprovedHours(data,data.getBuild("B2"),null,"Admin",true,"20",3).allowed);
    }
    @Test public void providerIsTransientAndInstallingItDoesNotPersistOrDirtyConflictState() {
        NBTTagCompound before=KOMETacticalMembershipFixtures.save(data); lockA();
        assertEquals(before,KOMETacticalMembershipFixtures.save(data)); assertFalse(data.isDirty());
        KOMEWorldData restored=new KOMEWorldData("restored"); restored.readFromNBT(before);
        assertFalse(restored.isTacticalActivityLocked("T100",dimension(),"A"));
        assertEquals(KOMETacticalConfigurationCodec.encode(data.getTacticalConfigurationSnapshot()),KOMETacticalConfigurationCodec.encode(restored.getTacticalConfigurationSnapshot()));
    }
    @Test public void complexCreationAndMembershipRecheckTheProviderAtPublication() {
        AtomicInteger queries=new AtomicInteger();
        data.setTacticalActivityLockProvider((tile,dim,id)->queries.incrementAndGet()>=2);
        KOMETacticalEditDraft empty=new KOMETacticalEditDraft(KOMESiegeReadinessFixtures.empty("NEW","T100",dimension())).atRevision(0);
        long revision=revision(data);
        assertEquals(KOMETacticalEditService.Status.ACTIVITY_LOCKED,KOMETacticalEditService.saveCreation(data,complexScope("NEW"),empty,revision).getStatus());
        assertNull(data.getTacticalConfigurationSnapshot().findComplex("NEW")); assertEquals(revision,revision(data)); assertEquals(0,data.dirtyCalls);
        queries.set(0);
        assertEquals(KOMETacticalMembershipService.Status.ACTIVITY_LOCKED,KOMETacticalMembershipService.assignBuild(data,"B1","A",revision).getStatus());
        assertFalse(data.getTacticalConfigurationSnapshot().findAssignedComplexId("B1").isPresent()); assertEquals(revision,revision(data)); assertEquals(0,data.dirtyCalls);
    }
    @Test public void gateLinkRechecksActivityAfterPhysicalPreflightWithoutAllocatingLogicalRecord() {
        KOMETacticalMembershipFixtures.assign(data,"B1","A"); clean(); KOMEPlayerBuild build=data.getBuild("B1");
        KOMEDefensiveGateLinkService.PhysicalHealthApplication application=new KOMEDefensiveGateLinkService.PhysicalHealthApplication() {
            public boolean canApply() { lockA(); return true; }
            public boolean apply() { fail("Locked gate must not receive initial health"); return true; }
        };
        KOMEDefensiveGateLinkService.OperationResult result=KOMEDefensiveGateLinkService.linkAndInitializePhysicalHealth(data,build,
            KOMETacticalMembershipFixtures.inspection(UUID.randomUUID(),1,50),null,"Admin",true,3,application);
        assertFalse(result.isSuccessful()); assertTrue(result.getMessage().contains("ACTIVITY_LOCKED"));
        assertEquals(0,build.getDefensiveGateRecordSequence()); assertTrue(build.getDefensiveGateRecords().isEmpty()); assertEquals(0,data.dirtyCalls);
    }
    @Test public void tileAreaIsLockedOnlyWhenExplicitlyReferencedByALockedComplex() {
        KOMETacticalConfiguration config=data.getTacticalConfigurationSnapshot(); config.replaceComplex(KOMETacticalComplexDraft.preferred(complex(),"FIELD"));
        KOMETacticalMembershipFixtures.installConfiguration(data,data,config); clean(); lockA();
        assertEquals(ACTIVITY_LOCKED,sessions.handle(actor,data,KOMETacticalEditRequest.open(areaScope("FIELD"))).getStatus());
        assertEquals(OPENED,sessions.handle(actor,data,KOMETacticalEditRequest.open(areaScope("OTHER"))).getStatus());
    }
}
