package kome.common.network;

import java.util.*;
import io.netty.buffer.*;
import kome.common.data.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMECompanyOriginNetworkTest {
    private final UUID session=new UUID(84,3);
    @Test public void sourceAndTargetRequestsRoundTripWithStrictSeparateSemantics(){
        KOMEPacketCompanyOriginRequest source=new KOMEPacketCompanyOriginRequest(session,1,"T001","","");
        KOMEPacketCompanyOriginRequest copy=KOMEPopulationWire.copyForPublication(source,KOMEPacketCompanyOriginRequest::new);
        assertFalse(copy.targeted);assertEquals("T001",copy.source);assertEquals("",copy.destination);assertEquals(session,copy.session);
        KOMEPacketCompanyOriginRequest exact=KOMEPacketCompanyOriginRequest.target(session,2,"T001","C20","T500");
        copy=KOMEPopulationWire.copyForPublication(exact,KOMEPacketCompanyOriginRequest::new);
        assertTrue(copy.targeted);assertEquals("C20",copy.company);assertEquals("T500",copy.destination);assertEquals("",copy.after);
        exact.after="C1";ByteBuf b=Unpooled.buffer();try{exact.toBytes(b);fail();}catch(IllegalArgumentException expected){assertEquals(0,b.readableBytes());}finally{b.release();}
    }
    @Test public void sourceResponseDetachedAndLimitedToEightMetadataRows(){
        List<KOMECompanySourceOptions.Row> rows=new ArrayList<>();
        for(int i=1;i<=8;i++)rows.add(new KOMECompanySourceOptions.Row("C"+i,"Company",1,100,false,1,1,"Stationed",false));
        KOMECompanySourceOptions page=new KOMECompanySourceOptions(session,1,"T001",KOMECompanyDestinationOptions.Result.OK,rows,UUID.randomUUID().toString(),"C8",true,0);
        KOMECompanySourceOptions copy=KOMEPopulationWire.copyForPublication(new KOMEPacketCompanySourceResponse(page),KOMEPacketCompanySourceResponse::new).snapshot();
        rows.clear();assertEquals(8,copy.rows.size());assertEquals("C8",copy.next);assertEquals(100,copy.rows.get(0).populationCenti);
        List<KOMECompanySourceOptions.Row> oversized=new ArrayList<>(copy.rows);oversized.add(copy.rows.get(0));
        try{new KOMEPacketCompanySourceResponse(new KOMECompanySourceOptions(session,1,"T001",page.result,oversized,page.token,"C8",true,0));fail();}
        catch(IllegalArgumentException expected){ }
    }
    @Test public void malformedTrailingBytesAndWrongProtocolAreRejected(){
        assertTrue(KOMEPopulationWire.accepts("1.0.9-integration-g11"));assertFalse(KOMEPopulationWire.accepts("1.0.9-integration-g9"));
        ByteBuf b=Unpooled.buffer();try{
            new KOMEPacketCompanyOriginRequest(session,1,"T001","","").toBytes(b);b.writeInt(42);
            try{new KOMEPacketCompanyOriginRequest().fromBytes(b);fail();}catch(IllegalArgumentException expected){ }
        }finally{b.release();}
        try{KOMEPacketCompanyOriginRequest.target(session,1,"T001","","T002");fail();}catch(IllegalArgumentException expected){ }
        try{new KOMEPacketCompanyOriginRequest(session,1,"T001","C1","");fail();}catch(IllegalArgumentException expected){ }
    }
}