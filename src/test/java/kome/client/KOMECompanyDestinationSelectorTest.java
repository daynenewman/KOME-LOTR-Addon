package kome.client;

import java.util.*;
import kome.common.command.KOMECompanyMovementService.*;
import kome.common.data.KOMECompanyDestinationOptions;
import kome.common.data.KOMECompanyDestinationOptions.*;
import kome.common.network.KOMEPacketCompanyDestinationRequest;
import org.junit.*;
import static org.junit.Assert.*;

public class KOMECompanyDestinationSelectorTest {
    private final Object connection=new Object();
    private KOMECompanyDestinationClientState queries;
    private KOMECompanyDestinationSelector selector;
    private KOMEPacketCompanyDestinationRequest request;
    @Before public void setup() {
        queries=new KOMECompanyDestinationClientState();queries.start(connection);
        selector=new KOMECompanyDestinationSelector(queries);request=selector.open("T100");
    }
    @Test public void openCreatesOneCorrelatedBoundedDestinationRequestAndLoading() {
        assertEquals("T100",request.destination);assertEquals(8,request.limit);assertEquals(1,request.sequence);
        assertTrue(request.afterCompanyId.isEmpty());assertTrue(request.catalogueToken.isEmpty());
        assertTrue(queries.isLoading());assertEquals(KOMECompanyDestinationSelector.Phase.LOADING,selector.phase());
        assertTrue(selector.message(0).contains("Loading"));assertTrue(selector.rows().isEmpty());
    }
    @Test public void acceptedProjectionPopulatesSelectableRowsAndExactRoute() {
        Row row=row("C1",Status.CAN_START_AND_ARRIVE_NOW);
        deliver(request,Result.OK,Arrays.asList(row),false,0,100);
        assertSame(row,selector.rows().get(0));assertEquals(KOMECompanyDestinationSelector.Phase.READY,selector.phase());
        selector.select("C1");assertSame(row,selector.selected());assertEquals(row.route,selector.selectedRoute());
        selector.select("C1");assertNull(selector.selected());assertTrue(selector.selectedRoute().isEmpty());
    }
    @Test public void immediateFootAndMountedUseProjectedClassificationAndAllowance() {
        Row foot=row("C1",Status.CAN_START_AND_ARRIVE_NOW);
        Row mounted=row("C2",Status.CAN_START_AND_ARRIVE_NOW,Entitlement.MOUNTED,EvaluationState.COMPLETE,Consequence.NONE);
        assertEquals("Foot/Mixed",KOMECompanyDestinationSelector.classification(foot));
        assertEquals("Mounted",KOMECompanyDestinationSelector.classification(mounted));
        assertEquals(4,mounted.tilesPerDay);assertEquals("Can arrive now",KOMECompanyDestinationSelector.state(mounted));
    }
    @Test public void laterArrivalWordingDoesNotClaimImmediateArrival() {
        Row row=row("C1",Status.CAN_START_BUT_CONTINUES_LATER);
        assertEquals("Can start; arrives later",KOMECompanyDestinationSelector.state(row));
        assertEquals("Future daily boundaries: 1",KOMECompanyDestinationSelector.timing(row));
        assertTrue(row.canStartNow);assertFalse(row.canArriveNow);
    }
    @Test public void blockedRowsStaySelectableForReasonAndHoldWithoutFakeRoute() {
        Row row=row("C1",Status.BLOCKED);
        deliver(request,Result.OK,Arrays.asList(row),false,0,100);
        selector.select("C1");assertSame(row,selector.selected());
        assertEquals("Blocked",KOMECompanyDestinationSelector.state(row));
        assertTrue(KOMECompanyDestinationSelector.reason(row).contains("held"));
        assertEquals("battle-hold",row.heldConflictId);assertTrue(selector.selectedRoute().isEmpty());
    }
    @Test public void deferredRowsHaveUnknownEligibilityWithoutMovementDenial() {
        Row row=row("C1",null,Entitlement.FOOT_OR_MIXED,EvaluationState.DEFERRED_ROUTE_LIMIT,null);
        deliver(request,Result.OK,Arrays.asList(row),false,0,100);selector.select("C1");
        assertEquals("Route evaluation deferred",KOMECompanyDestinationSelector.state(row));
        assertTrue(KOMECompanyDestinationSelector.reason(row).contains("unknown"));
        assertNull(row.status);assertTrue(row.denialCode.isEmpty());assertTrue(selector.selectedRoute().isEmpty());
    }
    @Test public void hostileCreateAndJoinStayExpectedArrivalConsequences() {
        Row create=row("C1",Status.CAN_START_AND_ARRIVE_NOW,Entitlement.FOOT_OR_MIXED,EvaluationState.COMPLETE,Consequence.EXPECTED_CREATE_CONFLICT);
        Row join=row("C2",Status.CAN_START_AND_ARRIVE_NOW,Entitlement.FOOT_OR_MIXED,EvaluationState.COMPLETE,Consequence.EXPECTED_JOIN_CONFLICT);
        assertEquals("Expected: create battle on arrival",KOMECompanyDestinationSelector.consequence(create));
        assertEquals("Expected: join battle on arrival",KOMECompanyDestinationSelector.consequence(join));
        assertEquals("battle-destination",join.destinationConflictId);assertEquals(7,join.destinationConflictRevision);
        assertFalse(join.confirmationFingerprint.isEmpty());
    }
    @Test public void nextPreviousPreserveInteractionAndUseServerCursors() {
        deliver(request,Result.OK,Arrays.asList(row("C1",Status.CAN_START_AND_ARRIVE_NOW)),true,0,100);
        selector.select("C1");
        KOMEPacketCompanyDestinationRequest next=selector.next();
        assertEquals(request.session,next.session);assertEquals("T100",next.destination);assertEquals("C1",next.afterCompanyId);
        assertEquals(TOKEN,next.catalogueToken);assertTrue(selector.selectedRoute().isEmpty());assertTrue(selector.rows().isEmpty());
        deliver(next,Result.OK,Arrays.asList(row("C2",Status.CAN_START_AND_ARRIVE_NOW)),false,0,100);
        assertEquals(2,selector.pageNumber());assertEquals("C2",selector.rows().get(0).companyId);assertFalse(selector.hasNext());
        KOMEPacketCompanyDestinationRequest previous=selector.previous();
        assertEquals(request.session,previous.session);assertTrue(previous.afterCompanyId.isEmpty());assertTrue(previous.catalogueToken.isEmpty());
        deliver(previous,Result.OK,Arrays.asList(row("C1",Status.CAN_START_AND_ARRIVE_NOW)),true,0,100);
        assertEquals(1,selector.pageNumber());assertEquals(1,selector.rows().size());assertEquals("C1",selector.rows().get(0).companyId);
    }
    @Test public void staleAndDuplicatePagesCannotRestoreObsoleteRowsOrRoutes() {
        KOMECompanyDestinationOptions first=deliver(request,Result.OK,Arrays.asList(row("C1",Status.CAN_START_AND_ARRIVE_NOW)),true,0,100);
        KOMEPacketCompanyDestinationRequest newer=selector.next();
        assertFalse(queries.accept(first,connection));assertFalse(selector.accept(first,100));
        KOMECompanyDestinationOptions second=deliver(newer,Result.OK,Arrays.asList(row("C2",Status.CAN_START_AND_ARRIVE_NOW)),false,0,100);
        selector.select("C2");assertFalse(selector.accept(second,100));assertSame(second.rows.get(0),selector.selected());
    }
    @Test public void throttlingRequiresExplicitRetryAfterProjectedDelay() {
        deliver(request,Result.RATE_LIMITED,Collections.<Row>emptyList(),false,500,1000);
        assertEquals(KOMECompanyDestinationSelector.Phase.THROTTLED,selector.phase());
        assertTrue(selector.message(1100).contains("400 ms"));assertFalse(selector.message(1100).contains("No relevant"));
        assertNull(selector.retry(1499));assertTrue(selector.canRetry(1500));
        KOMEPacketCompanyDestinationRequest retry=selector.retry(1500);
        assertEquals(request.session,retry.session);assertEquals(2,retry.sequence);assertEquals(request.afterCompanyId,retry.afterCompanyId);
        assertEquals(KOMECompanyDestinationSelector.Phase.LOADING,selector.phase());
    }
    @Test public void throttledContinuationRetriesTheSamePageNotTheFirstPage() {
        deliver(request,Result.OK,Arrays.asList(row("C1",Status.CAN_START_AND_ARRIVE_NOW)),true,0,0);
        KOMEPacketCompanyDestinationRequest next=selector.next();
        deliver(next,Result.RATE_LIMITED,Collections.<Row>emptyList(),false,200,0);
        KOMEPacketCompanyDestinationRequest retry=selector.retry(200);
        assertEquals("C1",retry.afterCompanyId);assertEquals(TOKEN,retry.catalogueToken);assertEquals(2,selector.pageNumber());
    }
    @Test public void changedCatalogueRetryRefreshesFromFirstPage() {
        deliver(request,Result.OK,Arrays.asList(row("C1",Status.CAN_START_AND_ARRIVE_NOW)),true,0,0);
        KOMEPacketCompanyDestinationRequest next=selector.next();
        deliver(next,Result.CATALOGUE_CHANGED,Collections.<Row>emptyList(),false,0,0);
        KOMEPacketCompanyDestinationRequest retry=selector.retry(0);
        assertTrue(retry.afterCompanyId.isEmpty());assertTrue(retry.catalogueToken.isEmpty());assertEquals(1,selector.pageNumber());
    }
    @Test public void emptySuccessfulPageDiffersFromQueryErrors() {
        deliver(request,Result.OK,Collections.<Row>emptyList(),false,0,0);
        assertEquals(KOMECompanyDestinationSelector.Phase.READY,selector.phase());assertTrue(selector.message(0).contains("No relevant"));
        request=selector.open("T100");
        deliver(request,Result.PROJECTION_LIMIT,Collections.<Row>emptyList(),false,0,0);
        assertEquals(KOMECompanyDestinationSelector.Phase.ERROR,selector.phase());assertTrue(selector.message(0).contains("response limit"));
    }
    @Test public void closeClearsPreviewAndRejectsOutstandingReplies() {
        KOMECompanyDestinationOptions reply=page(request,Result.OK,Arrays.asList(row("C1",Status.CAN_START_AND_ARRIVE_NOW)),false,0);
        selector.close();assertFalse(queries.accept(reply,connection));assertFalse(selector.accept(reply,0));
        assertFalse(selector.isOpen());assertTrue(selector.selectedRoute().isEmpty());
    }
    @Test public void destinationReplacementClearsSelectionAndChangesCorrelation() {
        KOMECompanyDestinationOptions old=deliver(request,Result.OK,Arrays.asList(row("C1",Status.CAN_START_AND_ARRIVE_NOW)),false,0,0);
        selector.select("C1");KOMEPacketCompanyDestinationRequest replacement=selector.open("T101");
        assertNotEquals(request.session,replacement.session);assertTrue(selector.selectedRoute().isEmpty());
        assertFalse(queries.accept(old,connection));assertFalse(selector.accept(old,0));assertEquals("T101",selector.destination());
    }
    @Test public void disconnectAndSessionReplacementClearAllPresentationState() {
        deliver(request,Result.OK,Arrays.asList(row("C1",Status.CAN_START_AND_ARRIVE_NOW)),false,0,0);selector.select("C1");
        queries.start(null);assertFalse(selector.isOpen());assertTrue(selector.rows().isEmpty());assertTrue(selector.selectedRoute().isEmpty());
        queries.start(new Object());KOMEPacketCompanyDestinationRequest replacement=selector.open("T100");
        assertNotEquals(request.session,replacement.session);
    }
    @Test public void wrongConnectionCannotPopulateSelector() {
        KOMECompanyDestinationOptions reply=page(request,Result.OK,Arrays.asList(row("C1",Status.CAN_START_AND_ARRIVE_NOW)),false,0);
        assertFalse(queries.accept(reply,new Object()));assertFalse(selector.accept(reply,0));assertTrue(selector.rows().isEmpty());
    }
    @Test public void localRowWindowDoesNotCreateNetworkRequestsOrDuplicateRows() {
        List<Row> rows=new ArrayList<Row>();for(int i=1;i<=8;i++)rows.add(row("C"+i,Status.CAN_START_AND_ARRIVE_NOW));
        deliver(request,Result.OK,rows,false,0,0);selector.scrollRows(5,5);
        assertEquals(3,selector.rowOffset());assertEquals(8,selector.rows().size());assertFalse(queries.isLoading());
        selector.scrollRows(-5,5);assertEquals(0,selector.rowOffset());
    }
    @Test public void timedOverridePresentationDoesNotInventDailyEtaAndEmptyNameUsesStableId() {
        Row row=new Row("C1","","T001","T100",Entitlement.MOUNTED,4,3,2,Arrays.asList("T001","T050","T100"),
            Status.CAN_START_AND_ARRIVE_NOW,ScheduleMode.TIMED_OVERRIDE,true,true,true,false,0,0,0,
            "","","",-1,Consequence.NONE,"",-1,"legacy","confirmation");
        deliver(request,Result.OK,Collections.singletonList(row),false,0,0);selector.select("C1");
        assertEquals("C1",KOMECompanyDestinationSelector.name(row));
        assertEquals("Timed schedule; no ETA projected",KOMECompanyDestinationSelector.timing(row));
        assertTrue(KOMECompanyDestinationPanel.stats(row).contains("3/4"));assertEquals(row.route,selector.selectedRoute());
    }
    @Test public void alreadyAtDestinationShowsServerDenialAndNoRoute() {
        Row row=new Row("C1","Company","T100","T100",Entitlement.FOOT_OR_MIXED,2,2,-1,Collections.<String>emptyList(),
            Status.BLOCKED,ScheduleMode.DAILY,false,false,false,false,0,0,0,"ALREADY_AT_DESTINATION","Company is already here.",
            "",-1,Consequence.NONE,"",-1,"","");
        deliver(request,Result.OK,Collections.singletonList(row),false,0,0);selector.select("C1");
        assertEquals("Blocked",KOMECompanyDestinationSelector.state(row));assertEquals("Company is already here.",KOMECompanyDestinationSelector.reason(row));
        assertTrue(selector.selectedRoute().isEmpty());
    }
    @Test public void authoritativeEtaFormattingUsesOnlyServerDuration() {
        Row later=row("C1",Status.CAN_START_BUT_CONTINUES_LATER);
        assertEquals("ETA: 5h",KOMECompanyDestinationSelector.eta(new Row(later,1800,12,5*3600000L)));
        assertEquals("ETA: 1d 6h",KOMECompanyDestinationSelector.eta(new Row(later,1800,12,30*3600000L)));
        assertEquals("ETA: 2d",KOMECompanyDestinationSelector.eta(new Row(later,1800,12,48*3600000L)));
        assertEquals("ETA unavailable",KOMECompanyDestinationSelector.eta(later));
        assertEquals("Arrives now",KOMECompanyDestinationSelector.eta(new Row(row("C1",Status.CAN_START_AND_ARRIVE_NOW),1800,12,0)));
        assertEquals("12 units | Pop 18",KOMECompanyDestinationSelector.population(new Row(later,1800,12,1)));
        assertEquals("Units unknown | Pop unknown",KOMECompanyDestinationSelector.population(later));
    }
    @Test public void timedOverridesAndDeferredEligibilityNeverInventElapsedEta() {
        Row timed=new Row("C1","","T001","T100",Entitlement.MOUNTED,4,3,2,Arrays.asList("T001","T050","T100"),
            Status.CAN_START_AND_ARRIVE_NOW,ScheduleMode.TIMED_OVERRIDE,true,true,true,false,0,0,0,
            "","","",-1,Consequence.NONE,"",-1,"legacy","confirmation");
        assertEquals("Next movement by override",KOMECompanyDestinationSelector.eta(timed));
        assertEquals("ETA unavailable",KOMECompanyDestinationSelector.eta(row("C1",Status.BLOCKED)));
        assertEquals("ETA unavailable",KOMECompanyDestinationSelector.eta(row("C1",null,Entitlement.FOOT_OR_MIXED,EvaluationState.DEFERRED_ROUTE_LIMIT,null)));
    }
    private KOMECompanyDestinationOptions deliver(KOMEPacketCompanyDestinationRequest request,Result result,List<Row> rows,boolean more,long retry,long now) {
        KOMECompanyDestinationOptions response=page(request,result,rows,more,retry);
        assertTrue(queries.accept(response,connection));assertTrue(selector.accept(response,now));return response;
    }
    static final String TOKEN="12345678-1234-1234-1234-123456789012";
    static KOMECompanyDestinationOptions page(KOMEPacketCompanyDestinationRequest request,Result result,List<Row> rows,boolean more,long retry) {
        return new KOMECompanyDestinationOptions(request.session,request.sequence,request.destination,result,rows,
            result==Result.OK?TOKEN:"",rows.isEmpty()?"":rows.get(rows.size()-1).companyId,more,retry);
    }
    static Row row(String id,Status status) {return row(id,status,Entitlement.FOOT_OR_MIXED,EvaluationState.COMPLETE,Consequence.NONE);}
    static Row row(String id,Status status,Entitlement entitlement,EvaluationState evaluation,Consequence consequence) {
        boolean complete=evaluation==EvaluationState.COMPLETE,legal=complete&&status!=Status.BLOCKED;
        boolean later=status==Status.CAN_START_BUT_CONTINUES_LATER;
        return new Row(evaluation,id,"Company "+id,"T001","T100",entitlement,entitlement==Entitlement.MOUNTED?4:2,
            later?1:2,legal?2:-1,legal?Arrays.asList("T001","T050","T100"):Collections.<String>emptyList(),status,ScheduleMode.DAILY,
            legal,status==Status.CAN_START_AND_ARRIVE_NOW,legal,legal,later?1:0,10000,20000,
            status==Status.BLOCKED?"CONFLICT_HELD":"",status==Status.BLOCKED?"Company is held by an active battle.":"",
            status==Status.BLOCKED?"battle-hold":"",status==Status.BLOCKED?2:-1,consequence,
            consequence==Consequence.EXPECTED_JOIN_CONFLICT?"battle-destination":"",consequence==Consequence.EXPECTED_JOIN_CONFLICT?7:-1,
            legal?"legacy":"",legal?"confirmation":"");
    }
}
