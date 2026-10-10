package kome.common.data;

import java.util.*;
import net.minecraft.nbt.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMEFormalRetreatBatchTest {
    private KOMEFormalRetreatBatch batch(KOMEFormalRetreatBatch.Progress progress,
            KOMEFormalRetreatBatch.Phase phase){
        return new KOMEFormalRetreatBatch(UUID.randomUUID(),UUID.randomUUID(),"CF1","T101","JB1",
            12L,Arrays.asList(new KOMEFormalRetreatBatch.Member("C1","M1",UUID.randomUUID(),
                Arrays.asList("T101","T100"),2,1,progress)),phase,false);
    }
    @Test public void v2PreparedManifestRoundTripsBeforeAnyRelease(){
        KOMEArmyMovementOrder order=new KOMEArmyMovementOrder();order.id="M1";order.companyId="C1";
        order.formalRetreatBatch=batch(KOMEFormalRetreatBatch.Progress.PREPARED,KOMEFormalRetreatBatch.Phase.STRATEGIC_PENDING);
        NBTTagCompound saved=order.writeToNBT();
        assertEquals(2,saved.getCompoundTag("ConflictMovementRelease").getInteger("SchemaVersion"));
        assertFalse(saved.getCompoundTag("ConflictMovementRelease").getBoolean("HasRelease"));
        KOMEArmyMovementOrder loaded=new KOMEArmyMovementOrder();loaded.readFromNBT(saved);
        assertNull(loaded.conflictRelease);assertEquals(order.formalRetreatBatch.write(),loaded.formalRetreatBatch.write());
    }
    @Test public void everyLegalProgressAndFinalizationPhaseRoundTrips(){
        for(KOMEFormalRetreatBatch.Progress p:KOMEFormalRetreatBatch.Progress.values()){
            KOMEFormalRetreatBatch b=batch(p,KOMEFormalRetreatBatch.Phase.STRATEGIC_PENDING);
            assertEquals(b.write(),KOMEFormalRetreatBatch.read(b.write()).write());
        }
        for(KOMEFormalRetreatBatch.Phase p:KOMEFormalRetreatBatch.Phase.values()){
            KOMEFormalRetreatBatch b=batch(KOMEFormalRetreatBatch.Progress.COMPLETE,p);
            assertEquals(b.write(),KOMEFormalRetreatBatch.read(b.write()).write());
        }
    }
    @Test public void genuineV1HistoryLoadsWithoutSynthesizingBatch(){
        NBTTagCompound n=new NBTTagCompound();n.setInteger("SchemaVersion",1);
        n.setString("ConflictId","CF1");n.setLong("ConflictRevision",2);
        n.setString("OrderId","M1");n.setString("CompanyId","C1");n.setString("Outcome","FORMAL_RETREAT");
        n.setLong("AppliedAtMillis",10);n.setString("Code","CANCELED_BY_FORMAL_RETREAT");n.setString("Reason","legacy");
        KOMEArmyMovementOrder order=new KOMEArmyMovementOrder();
        KOMEConflictMovementHandoff.readEnvelope(order,n);
        assertNotNull(order.conflictRelease);assertNull(order.formalRetreatBatch);
        NBTTagCompound v2=KOMEConflictMovementHandoff.writeEnvelope(order.conflictRelease,null);
        assertEquals(2,v2.getInteger("SchemaVersion"));
        KOMEArmyMovementOrder again=new KOMEArmyMovementOrder();KOMEConflictMovementHandoff.readEnvelope(again,v2);
        assertNull(again.formalRetreatBatch);assertEquals("CF1",again.conflictRelease.conflictId);
    }
    @Test public void malformedAndImpossibleBatchDataFailsClosed(){
        NBTTagCompound good=batch(KOMEFormalRetreatBatch.Progress.PREPARED,KOMEFormalRetreatBatch.Phase.STRATEGIC_PENDING).write();
        NBTTagCompound bad=(NBTTagCompound)good.copy();bad.setString("Commander","not-a-uuid");reject(bad);
        bad=(NBTTagCompound)good.copy();bad.setString("Phase","FINALIZED");reject(bad);
        bad=(NBTTagCompound)good.copy();bad.getTagList("Members",10).appendTag(bad.getTagList("Members",10).getCompoundTagAt(0).copy());reject(bad);
        bad=(NBTTagCompound)good.copy();bad.getTagList("Members",10).getCompoundTagAt(0).setInteger("AllowanceAfter",2);reject(bad);
        bad=(NBTTagCompound)good.copy();bad.removeTag("ReceiptId");reject(bad);
    }
    @Test public void unknownEnvelopeVersionAndLegacySmuggledBatchReject(){
        for(int version:new int[]{1,3}){
            NBTTagCompound tag=KOMEConflictMovementHandoff.writeEnvelope(null,
                batch(KOMEFormalRetreatBatch.Progress.PREPARED,KOMEFormalRetreatBatch.Phase.STRATEGIC_PENDING));
            tag.setInteger("SchemaVersion",version);
            try{KOMEConflictMovementHandoff.readEnvelope(new KOMEArmyMovementOrder(),tag);fail();}
            catch(IllegalArgumentException expected){}
        }
    }
    @Test public void finalizedCannotReopen(){
        KOMEFormalRetreatBatch b=batch(KOMEFormalRetreatBatch.Progress.COMPLETE,KOMEFormalRetreatBatch.Phase.FINALIZED);
        try{b.member(0,KOMEFormalRetreatBatch.Progress.PREPARED);fail();}catch(IllegalArgumentException expected){}
        try{b.advance(KOMEFormalRetreatBatch.Phase.STRATEGIC_PENDING,false);fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void publishedManifestWithoutReleaseAuthorityIsRejected(){
        KOMEArmyMovementOrder order=new KOMEArmyMovementOrder();order.id="M1";order.companyId="C1";
        order.formalRetreatBatch=batch(KOMEFormalRetreatBatch.Progress.RELEASED,KOMEFormalRetreatBatch.Phase.STRATEGIC_PENDING);
        try{new KOMEArmyMovementOrder().readFromNBT(order.writeToNBT());fail();}
        catch(IllegalArgumentException expected){}
    }
    @Test public void duplicateActiveOrderAuthorityIsRejectedWithoutGuessingAGroup(){
        KOMEWorldData data=new KOMEWorldData("duplicates");
        KOMEArmyMovementOrder a=new KOMEArmyMovementOrder();a.id="M1";
        a.formalRetreatBatch=batch(KOMEFormalRetreatBatch.Progress.PREPARED,KOMEFormalRetreatBatch.Phase.STRATEGIC_PENDING);
        KOMEArmyMovementOrder b=new KOMEArmyMovementOrder();b.id="M2";
        b.formalRetreatBatch=new KOMEFormalRetreatBatch(UUID.randomUUID(),UUID.randomUUID(),"CF2","T101","",15L,
            Arrays.asList(new KOMEFormalRetreatBatch.Member("C1","M2",UUID.randomUUID(),Arrays.asList("T101","T100"),1,0,
                KOMEFormalRetreatBatch.Progress.PREPARED),
                new KOMEFormalRetreatBatch.Member("C2","M1",UUID.randomUUID(),Arrays.asList("T101","T100"),1,0,
                KOMEFormalRetreatBatch.Progress.PREPARED)),KOMEFormalRetreatBatch.Phase.STRATEGIC_PENDING,false);
        data.armyMovements.put(a.id,a);data.armyMovements.put(b.id,b);
        try{KOMEFormalRetreatBatch.validateWorld(data);fail();}catch(IllegalArgumentException expected){}
    }
    private void reject(NBTTagCompound n){
        try{KOMEFormalRetreatBatch.read(n);fail("Malformed batch accepted");}catch(IllegalArgumentException expected){}
    }

    @Test public void completedV1RemainsUsableButIncompleteV1LatchesAcrossProgressAndSave() {
        for(boolean complete:new boolean[]{false,true}){
            NBTTagCompound release=new NBTTagCompound();release.setInteger("SchemaVersion",1);
            release.setString("ConflictId","CF1");release.setLong("ConflictRevision",2L);
            release.setString("OrderId","M1");release.setString("CompanyId","C1");
            release.setString("Outcome","FORMAL_RETREAT");release.setLong("AppliedAtMillis",10L);
            release.setString("Code","CANCELED_BY_FORMAL_RETREAT");release.setString("Reason","legacy");
            KOMEArmyMovementOrder order=new KOMEArmyMovementOrder();order.id="M1";order.companyId="C1";
            order.retreating=true;order.completedSteps=complete?1:0;
            KOMEConflictMovementHandoff.readEnvelope(order,release);
            KOMEWorldData data=new KOMEWorldData("legacy");data.armyMovements.put(order.id,order);
            KOMEFormalRetreatAuthority.quarantineIncompleteLegacy(data);
            assertEquals(!complete,KOMEFormalRetreatAuthority.isQuarantined(order));
            order.completedSteps=1;
            KOMEArmyMovementOrder loaded=new KOMEArmyMovementOrder();loaded.readFromNBT(order.writeToNBT());
            assertEquals(!complete,KOMEFormalRetreatAuthority.isQuarantined(loaded));
            assertNull(loaded.formalRetreatBatch);
        }
    }

    @Test public void finalizedBatchNoLongerReservesCompanyOrOrder(){
        KOMEWorldData data=new KOMEWorldData("finalized");
        KOMEArmyMovementOrder order=new KOMEArmyMovementOrder();order.id="M1";order.companyId="C1";
        order.formalRetreatBatch=batch(KOMEFormalRetreatBatch.Progress.COMPLETE,KOMEFormalRetreatBatch.Phase.FINALIZED);
        data.armyMovements.put(order.id,order);
        assertNull(KOMEFormalRetreatAuthority.reservation(data,"C1","M1"));
        assertFalse(KOMEFormalRetreatAuthority.protectsCompany(data,"C1"));
        assertFalse(KOMEFormalRetreatAuthority.hasUnresolvedFormalRetreatBatchAuthority(order));
    }
}
