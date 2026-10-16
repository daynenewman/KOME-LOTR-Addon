package kome.client;

import java.util.*;
import kome.common.command.KOMECompanyMovementService.*;
import kome.common.data.*;
import kome.common.data.KOMECompanyDestinationOptions.*;
import kome.common.network.*;
import org.junit.*;
import static org.junit.Assert.*;
import static kome.client.KOMECompanyDestinationSelectorTest.*;

public class KOMECompanyBattleContinuationTest {
    private final Object connection=new Object();
    private KOMECompanyDestinationClientState state;
    private KOMECompanyDestinationSelector selector;
    private KOMEPacketCompanyDestinationRequest request;
    @Before public void setup() {state=new KOMECompanyDestinationClientState();state.start(connection);selector=new KOMECompanyDestinationSelector(state);request=selector.open("T100");}
    private void select(Row row) {
        KOMECompanyDestinationOptions response=page(request,Result.OK,Collections.singletonList(row),false,0);
        assertTrue(state.accept(response,connection));assertTrue(selector.accept(response,0));selector.select(row.companyId);
    }
    private Row hostile(Consequence consequence) {return row("C1",Status.CAN_START_AND_ARRIVE_NOW,Entitlement.MOUNTED,EvaluationState.COMPLETE,consequence);}
    private KOMECompanyMovementResult arrived(KOMEPacketCompanyMovementRequest move) {
        return new KOMECompanyMovementResult(move.session,move.sequence,move.previewSequence,move.companyId,move.destination,
            KOMECompanyMovementResult.Outcome.ARRIVED,"M1",KOMEArmyMovementOrder.CONFLICT_HELD,move.destination,2,2,"","");
    }
    @Test public void joinChoiceRequiresImmediateHostileExecutableServerRow() {
        for(Consequence consequence:Consequence.values()) {
            request=selector.open("T100");select(hostile(consequence));
            assertEquals(consequence!=Consequence.NONE,selector.canConfirmAndJoin());
        }
        for(Row r:Arrays.asList(row("C1",Status.CAN_START_BUT_CONTINUES_LATER,Entitlement.MOUNTED,EvaluationState.COMPLETE,Consequence.EXPECTED_JOIN_CONFLICT),
                row("C1",Status.BLOCKED),row("C1",null,Entitlement.MOUNTED,EvaluationState.DEFERRED_ROUTE_LIMIT,null))) {
            request=selector.open("T100");select(r);assertFalse(selector.canConfirmAndJoin());assertNull(selector.confirm(true));
        }
    }
    @Test public void ordinaryConfirmationUsesSameMovementPacketButNeverRequestsJoinView() {
        select(hostile(Consequence.EXPECTED_JOIN_CONFLICT));KOMEPacketCompanyMovementRequest move=selector.confirm();
        assertEquals(HASH,move.confirmationFingerprint);KOMECompanyMovementResult result=arrived(move);
        assertTrue(state.acceptMovement(result,connection));assertNotNull(selector.acceptMovement(result));assertNull(selector.joinViewRequest());
    }
    @Test public void createAndJoinRequestFreshActualConflictViewOnlyAfterMatchingCommittedArrival() {
        for(Consequence consequence:Arrays.asList(Consequence.EXPECTED_CREATE_CONFLICT,Consequence.EXPECTED_JOIN_CONFLICT)) {
            request=selector.open("T100");select(hostile(consequence));KOMEPacketCompanyMovementRequest move=selector.confirm(true);
            assertNull(selector.joinViewRequest());assertNull(selector.confirm(true));
            KOMECompanyMovementResult result=arrived(move);assertTrue(state.acceptMovement(result,connection));assertNull(selector.acceptMovement(result));
            assertEquals(KOMECompanyDestinationSelector.Phase.JOINING,selector.phase());KOMEPacketJoinBattleViewRequest viewRequest=selector.joinViewRequest();
            assertNotNull(viewRequest);assertEquals("T100",viewRequest.tileId);assertNotEquals(new UUID(0,0),viewRequest.correlation);
            assertNull(selector.next());assertNull(selector.previous());assertFalse(selector.canConfirm());assertNull(selector.selected());
            KOMEPacketJoinBattleViewResponse live=new KOMEPacketJoinBattleViewResponse();live.tileId=viewRequest.tileId;live.correlation=viewRequest.correlation;
            live.conflictId="CF_ACTUAL";assertEquals("C1",selector.acceptJoinView(live));assertNull(selector.joinViewRequest());assertNull(selector.acceptJoinView(live));
        }
    }
    @Test public void denialStaleErrorAndAcceptedFutureOrderNeverContinueToJoin() {
        for(KOMECompanyMovementResult.Outcome outcome:Arrays.asList(KOMECompanyMovementResult.Outcome.REJECTED,
                KOMECompanyMovementResult.Outcome.STALE_PREVIEW,KOMECompanyMovementResult.Outcome.SERVER_ERROR,KOMECompanyMovementResult.Outcome.ORDER_ACCEPTED)) {
            request=selector.open("T100");select(hostile(Consequence.EXPECTED_CREATE_CONFLICT));KOMEPacketCompanyMovementRequest move=selector.confirm(true);
            KOMECompanyMovementResult result=movementResult(move,outcome);assertTrue(state.acceptMovement(result,connection));selector.acceptMovement(result);
            assertNull(selector.joinViewRequest());assertNotEquals(KOMECompanyDestinationSelector.Phase.JOINING,selector.phase());
        }
    }
    @Test public void arrivedWithoutActualConflictCommitmentNeverContinues() {
        select(hostile(Consequence.EXPECTED_CREATE_CONFLICT));KOMEPacketCompanyMovementRequest move=selector.confirm(true);
        KOMECompanyMovementResult result=movementResult(move,KOMECompanyMovementResult.Outcome.ARRIVED);
        assertTrue(state.acceptMovement(result,connection));assertNotNull(selector.acceptMovement(result));assertNull(selector.joinViewRequest());
    }
    @Test public void mismatchedMovementResultClearsCombinedIntentWithoutLosingOrdinarySuccess() {
        select(hostile(Consequence.EXPECTED_JOIN_CONFLICT));KOMEPacketCompanyMovementRequest move=selector.confirm(true);
        KOMECompanyMovementResult old=new KOMECompanyMovementResult(UUID.randomUUID(),move.sequence,move.previewSequence,move.companyId,move.destination,
            KOMECompanyMovementResult.Outcome.ARRIVED,"M1",KOMEArmyMovementOrder.CONFLICT_HELD,move.destination,2,2,"","");
        assertFalse(state.acceptMovement(old,connection));assertNull(selector.acceptMovement(old));
        KOMECompanyMovementResult real=arrived(move);assertTrue(state.acceptMovement(real,connection));assertNotNull(selector.acceptMovement(real));assertNull(selector.joinViewRequest());
    }
    @Test public void closeDestinationSessionAndDisconnectInvalidateBothMovementAndViewContinuation() {
        for(int lifecycle=0;lifecycle<4;lifecycle++) {
            state.start(connection);request=selector.open("T100");select(hostile(Consequence.EXPECTED_JOIN_CONFLICT));
            KOMEPacketCompanyMovementRequest move=selector.confirm(true);KOMECompanyMovementResult result=arrived(move);
            assertTrue(state.acceptMovement(result,connection));selector.acceptMovement(result);
            KOMEPacketJoinBattleViewRequest pending=selector.joinViewRequest();assertNotNull(pending);
            if(lifecycle==0)selector.close();else if(lifecycle==1)selector.open("T101");else if(lifecycle==2)state.close();else state.start(null);
            KOMEPacketJoinBattleViewResponse reply=new KOMEPacketJoinBattleViewResponse();reply.tileId=pending.tileId;reply.correlation=pending.correlation;
            assertNull(selector.joinViewRequest());assertNull(selector.acceptJoinView(reply));assertNull(selector.acceptMovement(result));
        }
    }
    @Test public void wrongViewCorrelationOrDestinationCancelsInsteadOfOpeningStaleJoinFlow() {
        for(boolean wrongTile:new boolean[]{true,false}) {
            request=selector.open("T100");select(hostile(Consequence.EXPECTED_JOIN_CONFLICT));KOMEPacketCompanyMovementRequest move=selector.confirm(true);
            KOMECompanyMovementResult result=arrived(move);assertTrue(state.acceptMovement(result,connection));selector.acceptMovement(result);
            KOMEPacketJoinBattleViewResponse reply=new KOMEPacketJoinBattleViewResponse();reply.tileId=wrongTile?"T101":"T100";
            reply.correlation=wrongTile?selector.joinViewRequest().correlation:UUID.randomUUID();
            assertNull(selector.acceptJoinView(reply));assertNull(selector.joinViewRequest());assertTrue(selector.notice().contains("moved successfully"));
        }
    }
    @Test public void twoExplicitButtonsFitMinimumLayoutsAndDoNotOverlapOtherControls() {
        select(hostile(Consequence.EXPECTED_JOIN_CONFLICT));
        for(int[] size:new int[][]{{320,240},{480,270},{640,360},{960,540}}) {
            KOMECompanyDestinationPanel.Layout layout=new KOMECompanyDestinationPanel.Layout(new KOMEMapViewport(0,0,size[0],size[1],0,0,1),false,true);
            for(KOMECompanyDestinationPanel.Control c:KOMECompanyDestinationPanel.Control.values()) {
                KOMECompanyDestinationPanel.Rect rect=layout.controls.get(c);assertNotNull(rect);assertEquals(c,layout.controlAt(rect.x+1,rect.y+1));
                assertTrue(layout.panel.contains(rect.x+1,rect.y+1));
            }
            assertTrue(KOMECompanyDestinationPanel.enabled(KOMECompanyDestinationPanel.Control.CONFIRM_JOIN,selector,layout,0));
            assertTrue(layout.controls.get(KOMECompanyDestinationPanel.Control.CONFIRM_JOIN).y>layout.controls.get(KOMECompanyDestinationPanel.Control.CONFIRM).y+20);
        }
    }
}
