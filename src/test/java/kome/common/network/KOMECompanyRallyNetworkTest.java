package kome.common.network;

import java.util.*;
import io.netty.buffer.*;
import kome.common.data.*;
import kome.common.command.KOMECompanyMovementService.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static kome.common.data.KOMECompanyRallySnapshot.*;

public class KOMECompanyRallyNetworkTest {
    @Test public void onlyServerTokenDestinationSessionAndActionAreAcceptedNoCompanyInjection(){
        UUID client=UUID.randomUUID(),token=UUID.randomUUID();
        for(KOMEPacketCompanyRallyRequest.Action action:KOMEPacketCompanyRallyRequest.Action.values()){
            KOMEPacketCompanyRallyRequest request=new KOMEPacketCompanyRallyRequest(action,client,
                action==KOMEPacketCompanyRallyRequest.Action.OPEN?new UUID(0,0):token,"T001");
            KOMEPacketCompanyRallyRequest copy=KOMEPopulationWire.copyForPublication(request,KOMEPacketCompanyRallyRequest::new);
            assertEquals(action,copy.action);assertEquals(client,copy.session);assertEquals("T001",copy.destination);
        }
        ByteBuf bytes=Unpooled.buffer();try{
            new KOMEPacketCompanyRallyRequest(KOMEPacketCompanyRallyRequest.Action.OPEN,client,new UUID(0,0),"T001").toBytes(bytes);
            bytes.writeInt(1);KOMEPopulationWire.writeText(bytes,"C_INJECTED");
            try{new KOMEPacketCompanyRallyRequest().fromBytes(bytes);fail();}catch(IllegalArgumentException expected){ }
        }finally{bytes.release();}
    }
    private KOMECompanyDestinationOptions.Row row(){
        return new KOMECompanyDestinationOptions.Row("C1","Company","T001","T002",KOMECompanyDestinationOptions.Entitlement.FOOT_OR_MIXED,
            1,1,1,Arrays.asList("T001","T002"),Status.CAN_START_AND_ARRIVE_NOW,ScheduleMode.DAILY,true,true,true,true,0,0,0,"","","",-1,
            Consequence.EXPECTED_CREATE_CONFLICT,"",-1,String.join("",Collections.nCopies(64,"a")),String.join("",Collections.nCopies(64,"b")));
    }
    @Test public void completeAndPartialSnapshotsRoundTripOneStrictBoundedProjection(){
        Entry entry=new Entry(0,row(),"gondor",true,Outcome.PREVIEW,"Will rally");
        KOMECompanyRallySnapshot snapshot=new KOMECompanyRallySnapshot(UUID.randomUUID(),UUID.randomUUID(),"T002",Phase.READY,1,1,1,1,0,0,"1 will rally",entry);
        KOMEPacketCompanyRallyResponse copy=KOMEPopulationWire.copyForPublication(new KOMEPacketCompanyRallyResponse(snapshot),KOMEPacketCompanyRallyResponse::new);
        assertEquals(1,copy.snapshot().total);assertEquals("C1",copy.snapshot().entry.company.companyId);
        assertNotSame(entry.company,copy.snapshot().entry.company);
        ByteBuf bytes=Unpooled.buffer();try{
            new KOMEPacketCompanyRallyResponse(snapshot).toBytes(bytes);assertTrue(bytes.readableBytes()<=24*1024);
            bytes.writeByte(1);try{new KOMEPacketCompanyRallyResponse().fromBytes(bytes);fail();}catch(IllegalArgumentException expected){ }
        }finally{bytes.release();}
    }
    @Test public void incompleteCountersCandidateCeilingAndWrongDestinationCannotClaimCompleteAuthority(){
        for(int invalid=0;invalid<3;invalid++){
            KOMECompanyRallySnapshot s=new KOMECompanyRallySnapshot(UUID.randomUUID(),UUID.randomUUID(),invalid==2?"T003":"T002",
                Phase.READY,1,invalid==0?65:2,invalid==1?1:invalid==0?65:2,1,0,0,"Bad",new Entry(0,row(),"gondor",true,Outcome.PREVIEW,"Will rally"));
            try{new KOMEPacketCompanyRallyResponse(s);fail();}catch(IllegalArgumentException expected){ }
        }
        assertTrue(KOMEPopulationWire.accepts("1.0.9-integration-g13"));assertFalse(KOMEPopulationWire.accepts("1.0.9-integration-g10"));
    }
}