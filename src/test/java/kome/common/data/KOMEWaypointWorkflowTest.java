package kome.common.data;
import java.util.*;
import net.minecraft.nbt.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMEWaypointWorkflowTest {
    @org.junit.Rule public final KOMETileTestResources geometry=new KOMETileTestResources();
    static final UUID A=UUID.fromString("10000000-0000-0000-0000-000000000001"),B=UUID.fromString("10000000-0000-0000-0000-000000000002");
    KOMEWorldData world(){ KOMEWorldData d=new KOMEWorldData("workflow"); d.initializeIntegratedWorld(); return d; }
    KOMEWaypointProposal propose(KOMEWorldData d,UUID owner){ return d.publicWaypoints.propose(d,owner,"Player","Place",KOMETileTestResources.dimension(),KOMETileTestResources.x(),72,KOMETileTestResources.z(),100); }
    void denied(Runnable r){ try { r.run(); fail("Must reject"); } catch(IllegalArgumentException|IllegalStateException expected){} }
    KOMEWorldData restart(KOMEWorldData d){ NBTTagCompound n=new NBTTagCompound(); d.writeToNBT(n); KOMEWorldData result=new KOMEWorldData("restart"); result.readFromNBT(n); assertFalse(result.isWriteBlocked()); return result; }
    @Test public void competingApprovalsAreUniqueAndPendingNeverPublic(){
        KOMEWorldData d=world(); KOMEWaypointProposal a=propose(d,A),b=propose(d,B);
        assertTrue(d.publicWaypoints.views(d).isEmpty()); denied(()->propose(d,A));
        KOMEPublicWaypoint r=d.publicWaypoints.approveProposal(d,a.id,0,A.toString(),"Reviewed",101);
        long rev=d.publicWaypoints.revision(); int audit=d.publicWaypoints.history().size();
        denied(()->d.publicWaypoints.approveProposal(d,b.id,0,A.toString(),"Competing",101));
        denied(()->d.publicWaypoints.approveProposal(d,a.id,0,A.toString(),"Repeat",102));
        assertEquals(rev,d.publicWaypoints.revision()); assertEquals(audit,d.publicWaypoints.history().size());
        assertEquals(KOMEWaypointProposal.Status.PENDING,d.publicWaypoints.proposal(b.id).status);
        KOMEWorldData re=restart(d); assertEquals(r.id,re.publicWaypoints.proposal(a.id).approvedWaypoint);
        assertEquals(KOMEWaypointProposal.Status.PENDING,re.publicWaypoints.proposal(b.id).status);
        assertEquals(1,re.publicWaypoints.records().size()); assertEquals(4,re.publicWaypoints.history().size());
    }
    @Test public void adjustmentRequiresCurrentVersionAndRejectHistorySurvivesRestart(){
        KOMEWorldData d=world(); KOMEWaypointProposal q=propose(d,A);
        KOMEWaypointProposal adjusted=d.publicWaypoints.adjust(d,q.id,0,"Changed",q.dimension,q.x,q.y,q.z,35,A.toString(),"Verified name",101);
        denied(()->d.publicWaypoints.approveProposal(d,q.id,0,A.toString(),"Stale",102));
        denied(()->d.publicWaypoints.adjust(d,q.id,1,"Bad",q.dimension,189568,72,-86016,0,A.toString(),"Gap",102));
        assertEquals("Changed",d.publicWaypoints.proposal(q.id).name); assertEquals(35,adjusted.level);
        d.publicWaypoints.reject(d,q.id,1,A.toString(),"Design decision",102);
        KOMEWorldData re=restart(d); assertEquals(KOMEWaypointProposal.Status.REJECTED,re.publicWaypoints.proposal(q.id).status);
        assertEquals("Design decision",re.publicWaypoints.proposal(q.id).reason); assertEquals(3,re.publicWaypoints.history().size());
        assertTrue(re.publicWaypoints.views(re).isEmpty()); denied(()->re.publicWaypoints.approveProposal(re,q.id,2,A.toString(),"Invalid",103));
    }
    @Test public void pendingLimitsBoundResourcesWithoutPreventingCompetition(){
        KOMEWorldData d=world(); for(int i=0;i<KOMEPublicWaypointRegistry.MAX_PENDING_PER_TILE;i++) propose(d,new UUID(100,i));
        denied(()->propose(d,A)); assertEquals(8,d.publicWaypoints.proposals().size());
    }
    @Test public void duplicateMalformedProposalsAreWithheldDeterministically(){
        KOMEWorldData d=world(); KOMEWaypointProposal q=propose(d,A); NBTTagCompound n=new NBTTagCompound(); d.writeToNBT(n);
        NBTTagList list=n.getCompoundTag("PublicWaypoints").getTagList("Proposals",10);
        list.appendTag(q.writeToNBT()); NBTTagCompound bad=q.writeToNBT(); bad.setString("Id",B.toString()); bad.setString("Status","BOGUS"); list.appendTag(bad);
        KOMEWorldData re=new KOMEWorldData("duplicate"); re.readFromNBT(n); assertFalse(re.isWriteBlocked());
        assertTrue(re.publicWaypoints.proposals().isEmpty()); assertEquals(3,re.publicWaypoints.quarantine().size());
    }
    @Test public void failedApprovalPreservesReviewAndResourceCounters(){
        KOMEWorldData d=world(); KOMEWaypointProposal q=propose(d,A);
        NBTTagCompound before=d.publicWaypoints.writeToNBT();
        denied(()->d.publicWaypoints.approveProposal(d,q.id,0,"bad actor","Review",101));
        denied(()->d.publicWaypoints.approveProposal(d,q.id,0,A.toString(),"Review",99));
        assertEquals(before,d.publicWaypoints.writeToNBT());
    }
    KOMEWaypointMigration.Entry entry(UUID id,int number,String name,int x,int z){ return new KOMEWaypointMigration.Entry(id,number,name,KOMETileTestResources.dimension(),x,72,z); }
    @Test public void dryRunIsReadOnlyAndAmbiguousTilesAreNeverFirstWinner(){
        KOMEWorldData d=world(); NBTTagCompound before=d.publicWaypoints.writeToNBT();
        List<KOMEWaypointMigration.Entry> entries=Arrays.asList(entry(A,0,"One",KOMETileTestResources.x(),KOMETileTestResources.z()),
            entry(B,0,"Two",KOMETileTestResources.x(),KOMETileTestResources.z()),entry(A,1,"Gap",189568,-86016));
        KOMEWaypointMigration.Report report=KOMEWaypointMigration.dryRun(d,A,entries);
        assertEquals(before,d.publicWaypoints.writeToNBT());
        assertFalse(report.rows.get(0).eligible()); assertFalse(report.rows.get(1).eligible()); assertFalse(report.rows.get(2).eligible());
        denied(()->KOMEWaypointMigration.convert(d,A,entries,report.token,Collections.singleton(entries.get(0).identity),A.toString(),101));
        assertEquals(before,d.publicWaypoints.writeToNBT());
        List<KOMEWaypointMigration.Entry> reverse=new ArrayList<KOMEWaypointMigration.Entry>(entries); Collections.reverse(reverse);
        assertEquals(report.token,KOMEWaypointMigration.dryRun(d,A,reverse).token);
    }
    @Test public void conversionRevalidatesTokenAndCutoverSurvivesRemovalRestartRollback(){
        KOMEWorldData d=world(); KOMEWaypointMigration.Entry e=entry(A,3,"Legacy",KOMETileTestResources.x(),KOMETileTestResources.z());
        List<KOMEWaypointMigration.Entry> entries=Collections.singletonList(e); KOMEWaypointMigration.Report report=KOMEWaypointMigration.dryRun(d,A,entries);
        denied(()->KOMEWaypointMigration.convert(d,B,entries,report.token,Collections.singleton(e.identity),A.toString(),101));
        denied(()->KOMEWaypointMigration.convert(d,A,Collections.singletonList(entry(A,3,"Changed",e.x,e.z)),report.token,Collections.singleton(e.identity),A.toString(),101));
        KOMEPublicWaypoint r=KOMEWaypointMigration.convert(d,A,entries,report.token,Collections.singleton(e.identity),A.toString(),101).get(0);
        assertEquals(KOMEPublicWaypoint.Source.MIGRATED,r.source); assertEquals(r.id,d.publicWaypoints.cutoverIdentities().get(e.identity));
        denied(()->KOMEWaypointMigration.convert(d,A,entries,report.token,Collections.singleton(e.identity),A.toString(),102));
        d.publicWaypoints.remove(d,r.id,A.toString(),102); KOMEWorldData re=restart(d);
        assertEquals(r.id,re.publicWaypoints.cutoverIdentities().get(e.identity)); assertTrue(re.publicWaypoints.records().isEmpty());
        re.publicWaypoints.rollbackLegacy(re,e.identity,A.toString(),103); assertTrue(re.publicWaypoints.cutoverIdentities().isEmpty());
        assertEquals("Legacy",e.name); assertEquals(3,Integer.parseInt(e.identity.substring(37)));
    }
    @Test public void importBatchFailsAtomicallyWhenAnySelectionIsInvalid(){
        KOMEWorldData d=world(); NBTTagCompound before=d.publicWaypoints.writeToNBT();
        List<KOMEWaypointMigration.Entry> entries=Arrays.asList(entry(A,1,"Good",KOMETileTestResources.x(),KOMETileTestResources.z()),entry(B,2,"Gap",189568,-86016));
        denied(()->d.publicWaypoints.importLegacy(d,entries,A.toString(),101)); assertEquals(before,d.publicWaypoints.writeToNBT());
    }
}
