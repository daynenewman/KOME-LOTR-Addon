package kome.common.network;

import io.netty.buffer.*;
import java.util.*;
import org.junit.Test;
import kome.client.KOMECompanyDestinationClientState;
import kome.common.data.*;
import kome.common.data.KOMECompanyDestinationOptions.*;
import kome.common.command.KOMECompanyMovementService.*;
import static org.junit.Assert.*;

public class KOMECompanyDestinationNetworkTest {
    private final UUID session=new UUID(1,2);
    private static final String HASH=String.join("",Collections.nCopies(64,"a"));
    private Row row(List<String> route,String name) {
        return new Row("C1",name,"T001","T002",Entitlement.FOOT_OR_MIXED,1,1,route.size()-1,route,
            Status.CAN_START_AND_ARRIVE_NOW,ScheduleMode.DAILY,true,true,true,true,0,0,123,
            "","","",-1,Consequence.EXPECTED_JOIN_CONFLICT,"CF1",4,HASH,HASH);
    }
    private KOMECompanyDestinationOptions page(UUID session,long seq,String dest,List<Row> rows) {
        return new KOMECompanyDestinationOptions(session,seq,dest,Result.OK,rows,"00000000-0000-0000-0000-000000000001",rows.isEmpty()?"":rows.get(rows.size()-1).companyId,!rows.isEmpty());
    }
    private KOMECompanyDestinationOptions reply(KOMEPacketCompanyDestinationRequest r) {return page(r.session,r.sequence,r.destination,Collections.<Row>emptyList());}
    private void rejected(Runnable run) {try{run.run();fail("Malformed payload accepted");}catch(IllegalArgumentException|IndexOutOfBoundsException expected) { }}
    @Test public void populationUnitsAndEtaRoundTripAndRejectFalseAuthority() {
        Row base=row(Arrays.asList("T001","T002"),"Name");
        Row decoded=KOMEPacketCompanyDestinationResponse.validated(page(session,1,"T002",Arrays.asList(new Row(base,1800,12,0)))).snapshot().rows.get(0);
        assertEquals(1800,decoded.populationCenti);assertEquals(12,decoded.unitCount);assertEquals(0,decoded.etaMillis);
        rejected(()->KOMEPacketCompanyDestinationResponse.validated(page(session,1,"T002",Arrays.asList(new Row(base,-2,12,0)))));
        rejected(()->KOMEPacketCompanyDestinationResponse.validated(page(session,1,"T002",Arrays.asList(new Row(base,-1,12,0)))));
        rejected(()->KOMEPacketCompanyDestinationResponse.validated(page(session,1,"T002",Arrays.asList(new Row(base,1800,257,0)))));
        rejected(()->KOMEPacketCompanyDestinationResponse.validated(page(session,1,"T002",Arrays.asList(new Row(base,1800,12,100)))));
        assertFalse(KOMEPopulationWire.accepts("1.0.9-integration-g5"));
    }
    @Test public void requestRoundTripPreservesCorrelationAndPagingOnly() {
        KOMEPacketCompanyDestinationRequest p=new KOMEPacketCompanyDestinationRequest(session,19,"T002",4,"C1","00000000-0000-0000-0000-000000000001");
        ByteBuf b=Unpooled.buffer();try {p.toBytes(b);KOMEPacketCompanyDestinationRequest q=new KOMEPacketCompanyDestinationRequest();q.fromBytes(b);
            assertEquals(session,q.session);assertEquals(19,q.sequence);assertEquals("T002",q.destination);assertEquals(4,q.limit);assertEquals("C1",q.afterCompanyId);assertEquals(p.catalogueToken,q.catalogueToken);
        }finally{b.release();}
    }
    @Test public void responseRoundTripPreservesAuthorityAndConfirmation() {
        Row r=row(Arrays.asList("T001","T002"),"Canonical name");KOMECompanyDestinationOptions p=page(session,1,"T002",Arrays.asList(r));
        KOMECompanyDestinationOptions copy=KOMEPacketCompanyDestinationResponse.validated(p).snapshot();Row q=copy.rows.get(0);
        assertEquals(r.companyId,q.companyId);assertEquals(r.name,q.name);assertEquals(r.route,q.route);assertEquals(r.status,q.status);
        assertEquals(r.consequence,q.consequence);assertEquals("CF1",q.destinationConflictId);assertEquals(4,q.destinationConflictRevision);
        assertEquals(HASH,q.confirmationFingerprint);assertEquals(HASH,q.legacyFingerprint);assertTrue(copy.hasMore);assertEquals(p.catalogueToken,copy.catalogueToken);
        try{copy.rows.clear();fail();}catch(UnsupportedOperationException expected) { }
    }
    @Test public void malformedRequestFieldsAreRejected() {
        rejected(()->new KOMEPacketCompanyDestinationRequest(session,0,"T002",4,"",""));
        rejected(()->new KOMEPacketCompanyDestinationRequest(session,1,"garbage",4,"",""));
        rejected(()->new KOMEPacketCompanyDestinationRequest(session,1,"T002",9,"",""));
        rejected(()->new KOMEPacketCompanyDestinationRequest(session,1,"T002",1,"C1",""));
        rejected(()->new KOMEPacketCompanyDestinationRequest(session,1,"T002",1,String.join("",Collections.nCopies(65,"x")),"x"));
    }
    @Test public void truncatedTrailingOversizedAndInvalidUtf8RequestsAreRejected() {
        ByteBuf b=Unpooled.buffer();try {
            b.writeZero(KOMECompanyDestinationWire.MAX_BYTES+1);rejected(()->new KOMEPacketCompanyDestinationRequest().fromBytes(b));b.clear();
            b.writeLong(1);rejected(()->new KOMEPacketCompanyDestinationRequest().fromBytes(b));b.clear();
            new KOMEPacketCompanyDestinationRequest(session,1,"T002",1,"","").toBytes(b);b.writeByte(1);
            rejected(()->new KOMEPacketCompanyDestinationRequest().fromBytes(b));b.clear();
            b.writeLong(1);b.writeLong(2);b.writeLong(1);b.writeByte(1);b.writeByte(0xff);
            rejected(()->new KOMEPacketCompanyDestinationRequest().fromBytes(b));
        }finally{b.release();}
    }
    @Test public void oversizedRowsRoutesNamesAndDenialAreAtomicFailures() {
        Row r=row(Arrays.asList("T001","T002"),"Name");ByteBuf b=Unpooled.buffer();try {
            rejected(()->new KOMEPacketCompanyDestinationResponse(page(session,1,"T002",Collections.nCopies(9,r))).toBytes(b));assertEquals(0,b.writerIndex());
            rejected(()->new KOMEPacketCompanyDestinationResponse(page(session,1,"T002",Arrays.asList(row(Collections.nCopies(65,"T001"),"Name")))).toBytes(b));assertEquals(0,b.writerIndex());
            rejected(()->new KOMEPacketCompanyDestinationResponse(page(session,1,"T002",Arrays.asList(row(Arrays.asList("T001","T002"),String.join("",Collections.nCopies(129,"x")))))).toBytes(b));assertEquals(0,b.writerIndex());
            Row denial=new Row("C1","Name","T001","T002",Entitlement.FOOT_OR_MIXED,1,0,0,Collections.<String>emptyList(),
                Status.BLOCKED,ScheduleMode.DAILY,false,false,false,false,0,0,0,"NO_ALLOWANCE",String.join("",Collections.nCopies(513,"x")),"",-1,Consequence.NONE,"",-1,"","");
            rejected(()->new KOMEPacketCompanyDestinationResponse(page(session,1,"T002",Arrays.asList(denial))).toBytes(b));assertEquals(0,b.writerIndex());
        }finally{b.release();}
    }
    private ByteBuf responsePrefix(int count) {
        ByteBuf b=Unpooled.buffer();b.writeLong(1);b.writeLong(2);b.writeLong(1);
        KOMEPopulationWire.writeText(b,"T002");KOMEPopulationWire.writeText(b,"OK");KOMEPopulationWire.writeText(b,"");KOMEPopulationWire.writeText(b,"");b.writeBoolean(false);b.writeLong(0);b.writeInt(count);return b;
    }
    @Test public void oversizedWireRowAndRouteCountsFailBeforeAllocation() {
        final ByteBuf b=responsePrefix(Integer.MAX_VALUE);try{rejected(()->new KOMEPacketCompanyDestinationResponse().fromBytes(b));}finally{b.release();}
        final ByteBuf route=responsePrefix(1);try{
            for(String s:Arrays.asList("C1","Name","T001","T002","COMPLETE","FOOT_OR_MIXED"))KOMEPopulationWire.writeText(route,s);
            route.writeInt(1);route.writeInt(1);route.writeInt(1);route.writeInt(Integer.MAX_VALUE);
            rejected(()->new KOMEPacketCompanyDestinationResponse().fromBytes(route));
        }finally{route.release();}
    }
    @Test public void mismatchedDestinationAndDuplicateRowsRejected() {
        Row r=row(Arrays.asList("T001","T002"),"Name");
        rejected(()->KOMEPacketCompanyDestinationResponse.validated(page(session,1,"T003",Arrays.asList(r))));
        rejected(()->KOMEPacketCompanyDestinationResponse.validated(page(session,1,"T002",Arrays.asList(r,r))));
    }
    @Test public void olderOutOfOrderAndDuplicateResponsesRejected() {
        KOMECompanyDestinationClientState state=new KOMECompanyDestinationClientState();Object c=new Object();state.start(c);
        KOMEPacketCompanyDestinationRequest older=state.request("T002",1,"",""),newer=state.request("T002",1,"","");
        assertFalse(state.accept(reply(older),c));assertTrue(state.isLoading());assertTrue(state.accept(reply(newer),c));
        assertFalse(state.accept(reply(newer),c));assertEquals(newer.sequence,state.latest().sequence);
    }
    @Test public void destinationChangeInvalidatesOlderResponse() {
        KOMECompanyDestinationClientState state=new KOMECompanyDestinationClientState();Object c=new Object();state.start(c);
        KOMEPacketCompanyDestinationRequest old=state.request("T002",1,"",""),next=state.request("T003",1,"","");
        assertFalse(state.accept(reply(old),c));assertTrue(state.accept(reply(next),c));assertEquals("T003",state.latest().destination);
    }
    @Test public void closeReopenAndReconnectCannotReviveOldResponse() {
        KOMECompanyDestinationClientState state=new KOMECompanyDestinationClientState();Object c=new Object();state.start(c);
        KOMEPacketCompanyDestinationRequest old=state.request("T002",1,"","");state.close();
        KOMEPacketCompanyDestinationRequest reopened=state.request("T002",1,"","");assertFalse(state.accept(reply(old),c));assertTrue(state.accept(reply(reopened),c));
        state.start(null);assertNull(state.latest());assertFalse(state.isLoading());Object newConnection=new Object();state.start(newConnection);
        KOMEPacketCompanyDestinationRequest fresh=state.request("T002",1,"","");assertFalse(state.accept(reply(reopened),c));assertFalse(state.accept(reply(fresh),c));assertTrue(state.accept(reply(fresh),newConnection));
    }
    @Test public void serverHandlerDerivesActorAndEchoesCorrelationAndRateLimit() throws Exception {
        kome.common.KOMEAccessFixture f=new kome.common.KOMEAccessFixture();
        try {
            KOMEPacketCompanyDestinationRequest request=new KOMEPacketCompanyDestinationRequest(session,7,"T002",1,"","");
            KOMEPacketCompanyDestinationRequest.Handler handler=new KOMEPacketCompanyDestinationRequest.Handler();
            KOMECompanyDestinationOptions p=((KOMEPacketCompanyDestinationResponse)handler.onMessage(request,f.context)).snapshot();
            assertEquals(Result.OK,p.result);assertEquals(session,p.session);assertEquals(7,p.sequence);assertTrue(p.rows.isEmpty());
            p=((KOMEPacketCompanyDestinationResponse)handler.onMessage(request,f.context)).snapshot();assertEquals(Result.OK,p.result);
            p=((KOMEPacketCompanyDestinationResponse)handler.onMessage(request,f.context)).snapshot();assertEquals(Result.RATE_LIMITED,p.result);
            assertTrue(p.retryAfterMillis>0 && p.retryAfterMillis<=1000);
            assertEquals(p.retryAfterMillis,KOMEPacketCompanyDestinationResponse.validated(p).snapshot().retryAfterMillis);
        }finally{KOMEPacketHandler.clearPendingServerTasks();}
    }
    @Test public void newProtocolRejectsPreviousArtifactIdentity() {
        assertTrue(KOMEPopulationWire.accepts("1.0.9-integration-g7"));assertFalse(KOMEPopulationWire.accepts("1.0.9-integration-g4"));
    }
    @Test public void tokenBucketAllowsTwoThenRefillsOnePerSecondWithRetryTiming() {
        KOMECompanyDestinationRateLimit gate=new KOMECompanyDestinationRateLimit(2);Object connection=new Object();
        assertTrue(gate.acquire(connection,0,0).accepted);assertTrue(gate.acquire(connection,0,0).accepted);
        assertEquals(1000,gate.acquire(connection,0,0).retryMillis);
        assertEquals(500,gate.acquire(connection,500_000_000L,500_000_000L).retryMillis);
        assertEquals(1,gate.acquire(connection,999_999_999L,999_999_999L).retryMillis);
        assertTrue(gate.acquire(connection,1_000_000_000L,1_000_000_000L).accepted);
        assertFalse(gate.acquire(connection,1_000_000_000L,1_000_000_000L).accepted);
        assertEquals(1000,gate.acquire(connection,1_000_000_000L,1_000_000_000L).retryMillis);
    }
    @Test public void tokenBucketCapsIdleCreditAndOldQueuedFloodCannotConsumeRefills() {
        KOMECompanyDestinationRateLimit gate=new KOMECompanyDestinationRateLimit(2);Object connection=new Object();
        assertTrue(gate.acquire(connection,0,0).accepted);assertTrue(gate.acquire(connection,0,0).accepted);
        KOMECompanyDestinationRateLimit.Result stale=gate.acquire(connection,0,10_000_000_000L);
        assertFalse(stale.accepted);assertEquals(0,stale.retryMillis);
        assertTrue(gate.acquire(connection,10_000_000_000L,10_000_000_000L).accepted);
        assertTrue(gate.acquire(connection,10_000_000_000L,10_000_000_000L).accepted);
        assertFalse(gate.acquire(connection,10_000_000_000L,10_000_000_000L).accepted);
    }
    @Test public void tokenBucketIsPerConnectionBoundedAndClearsAtLifecycleEnd() {
        KOMECompanyDestinationRateLimit gate=new KOMECompanyDestinationRateLimit(2);Object a=new Object(),b=new Object(),c=new Object();
        assertTrue(gate.acquire(a,0,0).accepted);assertTrue(gate.acquire(b,0,0).accepted);
        assertTrue(gate.acquire(a,0,0).accepted);assertFalse(gate.acquire(a,0,0).accepted);
        KOMECompanyDestinationRateLimit.Result full=gate.acquire(c,0,0);assertFalse(full.accepted);assertEquals(-1,full.retryMillis);
        gate.forget(a);assertTrue(gate.acquire(c,0,0).accepted);gate.clear();assertEquals(0,gate.size());
        assertTrue(gate.acquire(a,0,0).accepted);
        rejected(()->gate.acquire(null,0,0));rejected(()->gate.acquire(a,1,0));
    }
    @Test public void throttledResponseRetainsRetryWithoutAutomaticClientRequest() {
        KOMECompanyDestinationClientState state=new KOMECompanyDestinationClientState();Object c=new Object();state.start(c);
        KOMEPacketCompanyDestinationRequest request=state.request("T002",8,"","");
        KOMECompanyDestinationOptions throttled=new KOMECompanyDestinationOptions(request.session,request.sequence,"T002",
            Result.RATE_LIMITED,Collections.<Row>emptyList(),"","",false,750);
        assertTrue(state.accept(KOMEPacketCompanyDestinationResponse.validated(throttled).snapshot(),c));
        assertFalse(state.isLoading());assertEquals(750,state.latest().retryAfterMillis);assertEquals(request.sequence,state.latest().sequence);
        rejected(()->KOMEPacketCompanyDestinationResponse.validated(new KOMECompanyDestinationOptions(session,1,"T002",Result.RATE_LIMITED,Collections.<Row>emptyList(),"","",false,1001)));
        rejected(()->KOMEPacketCompanyDestinationResponse.validated(new KOMECompanyDestinationOptions(session,1,"T002",Result.RATE_LIMITED,Collections.<Row>emptyList(),"","",false,-2)));
        rejected(()->KOMEPacketCompanyDestinationResponse.validated(new KOMECompanyDestinationOptions(session,1,"T002",Result.OK,Collections.<Row>emptyList(),"","",false,1)));
    }
    private Row deferred(Status status,boolean start,String denial,String token) {
        return new Row(EvaluationState.DEFERRED_ROUTE_LIMIT,"C1","Name","T001","T002",Entitlement.FOOT_OR_MIXED,2,2,-1,
            Collections.<String>emptyList(),status,ScheduleMode.DAILY,start,false,false,false,0,0,0,
            denial,"","",-1,null,"",-1,token,token);
    }
    @Test public void deferredRowsRoundTripWithoutInventingDenialEligibilityOrConfirmation() {
        Row r=KOMEPacketCompanyDestinationResponse.validated(page(session,1,"T002",Arrays.asList(deferred(null,false,"","")))).snapshot().rows.get(0);
        assertEquals(EvaluationState.DEFERRED_ROUTE_LIMIT,r.evaluationState);assertNull(r.status);assertNull(r.consequence);
        assertFalse(r.canStartNow);assertFalse(r.canArriveNow);assertFalse(r.legalRoute);assertEquals(-1,r.distance);
        assertTrue(r.route.isEmpty());assertTrue(r.denialCode.isEmpty());assertTrue(r.confirmationFingerprint.isEmpty());
        rejected(()->KOMEPacketCompanyDestinationResponse.validated(page(session,1,"T002",Arrays.asList(deferred(Status.BLOCKED,false,"","")))));
        rejected(()->KOMEPacketCompanyDestinationResponse.validated(page(session,1,"T002",Arrays.asList(deferred(null,true,"","")))));
        rejected(()->KOMEPacketCompanyDestinationResponse.validated(page(session,1,"T002",Arrays.asList(deferred(null,false,"ROUTE_BLOCKED","")))));
        rejected(()->KOMEPacketCompanyDestinationResponse.validated(page(session,1,"T002",Arrays.asList(deferred(null,false,"",HASH)))));
    }
    private Row maximumRow(String id) {
        String tile="ABCDEFGHIJKLMNOPQRSTUVW1";
        return new Row(id,String.join("",Collections.nCopies(128,"界")),tile,"T002",Entitlement.FOOT_OR_MIXED,2,0,63,
            Collections.nCopies(64,tile),Status.BLOCKED,ScheduleMode.DAILY,false,false,false,false,0,0,0,
            "ROUTE_BLOCKED",String.join("",Collections.nCopies(512,"界")),"",-1,Consequence.NONE,"",-1,"","");
    }
    @Test public void byteLimitedPageHasUsableCursorAndDoesNotLoseRemainingRows() {
        List<Row> rows=new ArrayList<Row>();for(int i=1;i<=8;i++)rows.add(maximumRow("C"+i));
        KOMECompanyDestinationOptions all=page(session,1,"T002",rows);
        rejected(()->KOMEPacketCompanyDestinationResponse.validated(all));
        KOMECompanyDestinationOptions first=KOMEPacketCompanyDestinationResponse.forQuery(all).snapshot();
        assertTrue(first.rows.size()>0 && first.rows.size()<8);assertTrue(first.hasMore);
        assertEquals(first.rows.get(first.rows.size()-1).companyId,first.nextCompanyId);
        new KOMEPacketCompanyDestinationRequest(session,2,"T002",8,first.nextCompanyId,first.catalogueToken);
        ByteBuf b=Unpooled.buffer();try {new KOMEPacketCompanyDestinationResponse(first).toBytes(b);assertTrue(b.readableBytes()<=KOMECompanyDestinationWire.MAX_BYTES);}finally{b.release();}
        Set<String> seen=new LinkedHashSet<String>();for(Row r:first.rows)assertTrue(seen.add(r.companyId));
        int offset=first.rows.size();
        while(offset<rows.size()) {
            KOMECompanyDestinationOptions rest=KOMEPacketCompanyDestinationResponse.forQuery(page(session,2,"T002",rows.subList(offset,rows.size()))).snapshot();
            assertFalse(rest.rows.isEmpty());for(Row r:rest.rows)assertTrue(seen.add(r.companyId));offset+=rest.rows.size();
        }
        assertEquals(new LinkedHashSet<String>(Arrays.asList("C1","C2","C3","C4","C5","C6","C7","C8")),seen);
    }
    @Test public void unusableOrMismatchedContinuationIsRejected() {
        String token="00000000-0000-0000-0000-000000000001";
        rejected(()->KOMEPacketCompanyDestinationResponse.validated(new KOMECompanyDestinationOptions(session,1,"T002",Result.ARRIVAL_BUDGET_EXCEEDED,Collections.<Row>emptyList(),token,"",true)));
        rejected(()->KOMEPacketCompanyDestinationResponse.validated(new KOMECompanyDestinationOptions(session,1,"T002",Result.OK,Arrays.asList(row(Arrays.asList("T001","T002"),"Name")),token,"C2",true)));
    }
}
