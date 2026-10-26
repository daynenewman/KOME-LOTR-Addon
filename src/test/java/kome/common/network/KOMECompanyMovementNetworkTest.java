package kome.common.network;

import java.util.*;
import io.netty.buffer.*;
import kome.common.data.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMECompanyMovementNetworkTest {
    private static final String HASH=String.join("",Collections.nCopies(64,"a"));
    private KOMEPacketCompanyMovementRequest request() {return new KOMEPacketCompanyMovementRequest(new UUID(1,2),19,7,"C1","T002",HASH,HASH);}
    private void rejected(Runnable run) {try {run.run();fail("Malformed movement payload accepted");}catch(IllegalArgumentException|IndexOutOfBoundsException expected) { }}
    @Test public void requestRoundTripContainsExactGuardsAndCorrelationOnly() {
        KOMEPacketCompanyMovementRequest p=request();ByteBuf b=Unpooled.buffer();try {
            p.toBytes(b);KOMEPacketCompanyMovementRequest q=new KOMEPacketCompanyMovementRequest();q.fromBytes(b);
            assertEquals(p.session,q.session);assertEquals(19,q.sequence);assertEquals(7,q.previewSequence);assertEquals("C1",q.companyId);
            assertEquals("T002",q.destination);assertEquals(HASH,q.legacyFingerprint);assertEquals(HASH,q.confirmationFingerprint);
        } finally {b.release();}
    }
    @Test public void malformedOversizedAndTrailingRequestsFailClosed() {
        rejected(()->new KOMEPacketCompanyMovementRequest(new UUID(1,2),0,1,"C1","T002",HASH,HASH));
        rejected(()->new KOMEPacketCompanyMovementRequest(new UUID(1,2),1,0,"C1","T002",HASH,HASH));
        rejected(()->new KOMEPacketCompanyMovementRequest(new UUID(1,2),1,1,"C1","bad",HASH,HASH));
        rejected(()->new KOMEPacketCompanyMovementRequest(new UUID(1,2),1,1,"C1","T002","",HASH));
        rejected(()->new KOMEPacketCompanyMovementRequest(new UUID(1,2),1,1,"C1","T002",HASH,HASH+"a"));
        ByteBuf b=Unpooled.buffer();try {request().toBytes(b);b.writeByte(0);rejected(()->new KOMEPacketCompanyMovementRequest().fromBytes(b));}finally {b.release();}
        ByteBuf large=Unpooled.buffer();try {large.writeZero(24*1024+1);rejected(()->new KOMEPacketCompanyMovementRequest().fromBytes(large));}finally {large.release();}
    }
    @Test public void resultRoundTripsActualArrivalOrderAcceptedAndStructuredDenials() {
        for(KOMECompanyMovementResult.Outcome outcome:KOMECompanyMovementResult.Outcome.values()) {
            boolean arrived=outcome==KOMECompanyMovementResult.Outcome.ARRIVED,accepted=arrived||outcome==KOMECompanyMovementResult.Outcome.ORDER_ACCEPTED;
            KOMECompanyMovementResult value=new KOMECompanyMovementResult(new UUID(1,2),19,7,"C1","T002",outcome,
                accepted?"M1":"",accepted?(arrived?"arrived":"pending_spawn"):"",accepted?(arrived?"T002":"T001"):"",
                arrived?1:0,accepted?1:0,accepted?"":"STALE_PREVIEW",accepted?"":"Preview changed.");
            KOMECompanyMovementResult copy=KOMEPopulationWire.copyForPublication(new KOMEPacketCompanyMovementResult(value),KOMEPacketCompanyMovementResult::new).result();
            assertEquals(outcome,copy.outcome);assertEquals(value.accepted(),copy.accepted());assertEquals(value.code,copy.code);assertEquals(value.detail,copy.detail);
            assertEquals(value.orderStatus,copy.orderStatus);assertEquals(value.currentTile,copy.currentTile);assertEquals(value.previewSequence,copy.previewSequence);
        }
    }
    @Test public void canonicalCompletedArrivalIncludesFinalBattleCommitmentButNotPendingPhysicalSpawn() throws Exception {
        java.lang.reflect.Constructor<kome.common.command.KOMECompanyMovementService.Submission> constructor=
            kome.common.command.KOMECompanyMovementService.Submission.class.getDeclaredConstructor(String.class,KOMEArmyMovementOrder.class,kome.common.command.KOMECompanyMovementService.Denial.class);
        constructor.setAccessible(true);
        for(String status:Arrays.asList("arrived","conflict_held","access_halted","pending_spawn","waiting_next_step")) {
            KOMEArmyMovementOrder order=new KOMEArmyMovementOrder();order.id="M1";order.companyId="C1";order.status=status;
            order.currentTile="T002";order.completedSteps=1;order.distanceTiles=1;
            KOMECompanyMovementResult result=KOMECompanyMovementResult.from(new UUID(1,2),1,1,"C1","T002",constructor.newInstance("C1",order,null));
            assertEquals(Arrays.asList("arrived","conflict_held","access_halted").contains(status),result.outcome==KOMECompanyMovementResult.Outcome.ARRIVED);
            KOMEPopulationWire.copyForPublication(new KOMEPacketCompanyMovementResult(result),KOMEPacketCompanyMovementResult::new);
        }
    }
    @Test public void resultCannotClaimArrivalBeforePhysicalCompletionAndIsAtomicBounded() {
        KOMECompanyMovementResult falseArrival=new KOMECompanyMovementResult(new UUID(1,2),1,1,"C1","T002",KOMECompanyMovementResult.Outcome.ARRIVED,
            "M1","pending_spawn","T001",0,1,"","");
        rejected(()->KOMEPopulationWire.copyForPublication(new KOMEPacketCompanyMovementResult(falseArrival),KOMEPacketCompanyMovementResult::new));
        KOMECompanyMovementResult huge=new KOMECompanyMovementResult(new UUID(1,2),1,1,"C1","T002",KOMECompanyMovementResult.Outcome.REJECTED,
            "","","",0,0,"DENIED",String.join("",Collections.nCopies(513,"x")));
        ByteBuf b=Unpooled.buffer();try {rejected(()->new KOMEPacketCompanyMovementResult(huge).toBytes(b));assertEquals(0,b.readableBytes());}finally {b.release();}
    }
    @Test public void consumedRequestsCannotReplayEvenAfterAnotherIntentAndReconnectClearsOnlyItsLane() {
        KOMECompanyMovementReplayGuard guard=new KOMECompanyMovementReplayGuard();Object a=new Object(),b=new Object();
        assertTrue(guard.acquire(a,1));assertFalse(guard.acquire(a,1));assertTrue(guard.acquire(a,3));assertFalse(guard.acquire(a,2));
        assertTrue(guard.acquire(b,1));guard.forget(a);assertTrue(guard.acquire(a,1));assertFalse(guard.acquire(b,1));
        guard.clear();assertTrue(guard.acquire(b,1));
    }
    @Test public void requestHasOneCompanyIdentityAndNoBulkRouteOrAllowancePayload() {
        for(java.lang.reflect.Field field:KOMEPacketCompanyMovementRequest.class.getDeclaredFields()) {
            assertFalse(field.getType().isArray());assertFalse(java.util.Collection.class.isAssignableFrom(field.getType()));
            assertFalse(field.getName().toLowerCase(java.util.Locale.ROOT).contains("allowance"));
            assertFalse(field.getName().toLowerCase(java.util.Locale.ROOT).contains("route"));
        }
    }
    @Test public void replayStorageFailsClosedAtCapacityAndCleanupReleasesOnlyThatConnection() {
        KOMECompanyMovementReplayGuard guard=new KOMECompanyMovementReplayGuard();List<Object> connections=new ArrayList<Object>();
        for(int i=0;i<1024;i++){Object connection=new Object();connections.add(connection);assertTrue(guard.acquire(connection,1));}
        Object extra=new Object();assertFalse(guard.acquire(extra,1));assertFalse(guard.acquire(connections.get(1),1));
        guard.forget(connections.get(0));assertTrue(guard.acquire(extra,1));assertFalse(guard.acquire(connections.get(1),1));
        guard.clear();assertTrue(guard.acquire(connections.get(1),1));
    }
    @Test public void protocolRequiresMatchingG18Artifacts() {assertTrue(KOMEPopulationWire.accepts("1.0.9-integration-g18"));assertFalse(KOMEPopulationWire.accepts("1.0.9-integration-g17"));}
}
