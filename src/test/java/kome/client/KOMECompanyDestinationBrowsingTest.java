package kome.client;

import java.util.*;
import kome.common.command.KOMECompanyMovementService.*;
import kome.common.data.KOMECompanyDestinationOptions;
import kome.common.data.KOMECompanyDestinationOptions.*;
import kome.common.network.KOMEPacketCompanyDestinationRequest;
import kome.common.network.KOMEPacketCompanyMovementRequest;
import org.junit.*;
import static org.junit.Assert.*;
import static kome.client.KOMECompanyDestinationSelectorTest.*;
import static kome.client.KOMECompanyDestinationSelector.Filter.*;

/** Bounded, demand-driven browsing over authoritative rows; no renderer or server polling. */
public class KOMECompanyDestinationBrowsingTest {
    private final Object connection=new Object();
    private KOMECompanyDestinationClientState state;
    private KOMECompanyDestinationSelector selector;
    private KOMEPacketCompanyDestinationRequest request;
    @Before public void setup() {
        state=new KOMECompanyDestinationClientState();state.start(connection);
        selector=new KOMECompanyDestinationSelector(state);request=selector.open("T100");
    }
    private KOMECompanyDestinationOptions deliver(KOMEPacketCompanyDestinationRequest query,List<Row> rows,boolean more) {
        return deliver(query,Result.OK,rows,more,0,0);
    }
    private KOMECompanyDestinationOptions deliver(KOMEPacketCompanyDestinationRequest query,Result code,List<Row> rows,boolean more,long retry,long now) {
        assertNotNull(query);assertEquals(8,query.limit);
        KOMECompanyDestinationOptions response=page(query,code,rows,more,retry);
        assertTrue(state.accept(response,connection));assertTrue(selector.accept(response,now));return response;
    }
    private List<String> ids() {List<String> out=new ArrayList<String>();for(Row r:selector.rows())out.add(r.companyId);return out;}
    @Test public void allReadyAndArriveNowUseOnlyCompleteProjectedStatusNotDistanceOrAdjacency() {
        Row immediate=copy(row("I",Status.CAN_START_AND_ARRIVE_NOW),2,99,""),later=row("L",Status.CAN_START_BUT_CONTINUES_LATER);
        Row blocked=copy(row("B",Status.BLOCKED),2,1,"PASSAGE_DENIED"),exhausted=copy(row("E",Status.BLOCKED),0,1,"NO_ALLOWANCE");
        Row unavailable=row("U",Status.BLOCKED),deferred=row("D",null,Entitlement.FOOT_OR_MIXED,EvaluationState.DEFERRED_ROUTE_LIMIT,null);
        deliver(request,Arrays.asList(deferred,exhausted,blocked,later,unavailable,immediate),false);
        assertEquals(Arrays.asList("I","L","B","E","U","D"),ids());
        assertNull(selector.setFilter(READY));assertEquals(Arrays.asList("I","L"),ids());
        assertNull(selector.setFilter(ARRIVE_NOW));assertEquals(Collections.singletonList("I"),ids());
        assertNull(selector.setFilter(ALL));assertEquals(6,selector.rows().size());assertNull(selector.takeBrowseFollowup());
        assertEquals("All",ALL.label);assertEquals("Ready",READY.label);assertEquals("Arrive Now",ARRIVE_NOW.label);
    }
    @Test public void filterRetainsMatchingSelectionAndExactAuthorityButClearsHiddenChoice() {
        Row now=row("I",Status.CAN_START_AND_ARRIVE_NOW),later=row("L",Status.CAN_START_BUT_CONTINUES_LATER);
        deliver(request,Arrays.asList(now,later),false);selector.select("L");selector.setFilter(READY);
        assertSame(later,selector.selected());assertEquals(later.route,selector.selectedRoute());assertEquals(0,selector.rowOffset());
        selector.setFilter(ARRIVE_NOW);assertNull(selector.selected());assertTrue(selector.selectedRoute().isEmpty());assertFalse(selector.canConfirm());
        selector.setFilter(ALL);selector.select("I");selector.setFilter(READY);selector.setFilter(ARRIVE_NOW);
        assertSame(now,selector.selected());KOMEPacketCompanyMovementRequest move=selector.confirm();
        assertEquals(now.companyId,move.companyId);assertEquals(now.legacyFingerprint,move.legacyFingerprint);
        assertEquals(now.confirmationFingerprint,move.confirmationFingerprint);assertEquals(request.sequence,move.previewSequence);
        assertNull(selector.setFilter(ALL));assertEquals(ARRIVE_NOW,selector.filter()); // Cannot alter a submitted action.
    }
    @Test public void emptyFilteredViewFindsLaterMatchesUsingServerCursorAndStopsAtFirstMatchingPage() {
        deliver(request,Arrays.asList(row("A",Status.BLOCKED),row("B",Status.BLOCKED)),true);
        KOMEPacketCompanyDestinationRequest second=selector.setFilter(ARRIVE_NOW);assertEquals("B",second.afterCompanyId);
        assertEquals(KOMECompanyDestinationSelector.Phase.LOADING,selector.phase());assertTrue(selector.message(0).contains("Loading companies"));
        KOMECompanyDestinationOptions old=deliver(second,Collections.singletonList(row("C",Status.BLOCKED)),true);
        KOMEPacketCompanyDestinationRequest third=selector.takeBrowseFollowup();assertEquals("C",third.afterCompanyId);
        assertNull(selector.takeBrowseFollowup());assertFalse(state.accept(old,connection));assertFalse(selector.accept(old,0));
        deliver(third,Arrays.asList(row("I",Status.CAN_START_AND_ARRIVE_NOW),row("I2",Status.CAN_START_AND_ARRIVE_NOW),row("I3",Status.CAN_START_AND_ARRIVE_NOW),row("I4",Status.CAN_START_AND_ARRIVE_NOW),row("Z",Status.BLOCKED)),true);
        assertEquals(Arrays.asList("I","I2","I3","I4"),ids());assertNull(selector.takeBrowseFollowup());
        assertEquals("Arrive Now companies",selector.companyRange(4)); // No fabricated global filtered count.
        KOMEPacketCompanyDestinationRequest fourth=selector.browse(1,4,0);assertEquals("Z",fourth.afterCompanyId); // Not sorted/filtered last row I.
    }
    @Test public void emptySearchIsHardBoundedAndContinuesOnlyOnAnotherExplicitGesture() {
        deliver(request,Collections.singletonList(row("C0",Status.BLOCKED)),true);
        KOMEPacketCompanyDestinationRequest next=selector.setFilter(READY);
        for(int i=1;i<=KOMECompanyDestinationSelector.MAX_SEARCH_PAGES;i++) {
            deliver(next,Collections.singletonList(row("C"+i,Status.BLOCKED)),true);
            next=selector.takeBrowseFollowup();
            if(i<KOMECompanyDestinationSelector.MAX_SEARCH_PAGES)assertNotNull(next);else assertNull(next);
        }
        assertEquals(KOMECompanyDestinationSelector.Phase.READY,selector.phase());assertTrue(selector.message(0).contains("keep looking"));
        for(int frame=0;frame<100;frame++) {selector.rows();selector.message(frame);selector.companyRange(4);selector.canBrowse(1,frame);assertNull(selector.takeBrowseFollowup());}
        next=selector.browse(1,4,0);assertEquals(5,next.sequence);assertEquals("C3",next.afterCompanyId);
        deliver(next,Collections.singletonList(row("I",Status.CAN_START_AND_ARRIVE_NOW)),false);
        assertEquals(Collections.singletonList("I"),ids());assertNull(selector.takeBrowseFollowup());
    }
    @Test public void throttleWaitsWithoutPollingAndWheelRetriesExactContinuationAfterDelay() {
        deliver(request,Collections.singletonList(row("A",Status.BLOCKED)),true);
        KOMEPacketCompanyDestinationRequest next=selector.setFilter(READY);
        deliver(next,Result.RATE_LIMITED,Collections.<Row>emptyList(),false,500,1000);
        assertTrue(selector.message(1100).contains("available shortly"));assertNull(selector.browse(1,4,1499));assertNull(selector.takeBrowseFollowup());
        assertEquals(KOMECompanyDestinationSelector.Phase.THROTTLED,selector.phase());
        KOMEPacketCompanyDestinationRequest retry=selector.browse(1,4,1500);
        assertEquals(next.afterCompanyId,retry.afterCompanyId);assertEquals(next.catalogueToken,retry.catalogueToken);
        deliver(retry,Collections.singletonList(row("I",Status.CAN_START_AND_ARRIVE_NOW)),false);assertEquals(READY,selector.filter());
        assertEquals(Collections.singletonList("I"),ids());assertNull(selector.takeBrowseFollowup());
    }
    @Test public void backwardFilteredBrowsingRevalidatesAndSkipsEmptyPagesWithinTheSameBound() {
        List<Row> first=new ArrayList<Row>();for(int i=1;i<=4;i++)first.add(row("A"+i,Status.CAN_START_AND_ARRIVE_NOW));
        deliver(request,first,true);selector.setFilter(READY);
        KOMEPacketCompanyDestinationRequest next=selector.browse(1,4,0);deliver(next,Collections.singletonList(row("B",Status.BLOCKED)),true);
        next=selector.takeBrowseFollowup();List<Row> last=new ArrayList<Row>();for(int i=1;i<=4;i++)last.add(row("C"+i,Status.CAN_START_AND_ARRIVE_NOW));
        deliver(next,last,false);assertEquals(1,selector.rowOffset());assertEquals(8,selector.rows().size());
        assertNull(selector.browse(-1,4,0));assertEquals(0,selector.rowOffset());assertEquals("A1",selector.rows().get(0).companyId);
        assertFalse(selector.canBrowse(-1,0));
    }
    @Test public void refreshAndCatalogueInvalidationDiscardStaleFilteredEvidenceAndReapplyMode() {
        deliver(request,Arrays.asList(row("A",Status.CAN_START_AND_ARRIVE_NOW),row("A2",Status.CAN_START_AND_ARRIVE_NOW),row("A3",Status.CAN_START_AND_ARRIVE_NOW),row("A4",Status.CAN_START_AND_ARRIVE_NOW)),true);
        selector.setFilter(READY);selector.select("A");KOMEPacketCompanyDestinationRequest next=selector.browse(1,4,0);
        deliver(next,Result.CATALOGUE_CHANGED,Collections.<Row>emptyList(),false,0,0);
        assertTrue(selector.rows().isEmpty());assertNull(selector.selected());assertTrue(selector.selectedRoute().isEmpty());assertNull(selector.takeBrowseFollowup());
        KOMEPacketCompanyDestinationRequest refresh=selector.retry(0);assertTrue(refresh.afterCompanyId.isEmpty());assertTrue(refresh.catalogueToken.isEmpty());
        assertEquals(1,selector.historySize());assertEquals(READY,selector.filter());
        deliver(refresh,Arrays.asList(row("B",Status.BLOCKED),row("I",Status.CAN_START_AND_ARRIVE_NOW)),false);
        assertEquals(Collections.singletonList("I"),ids());assertNull(selector.selected());
        selector.select("I");refresh=selector.retry(0);assertTrue(selector.rows().isEmpty());assertFalse(selector.canConfirm());assertTrue(selector.selectedRoute().isEmpty());
    }
    @Test public void queuedEmptySearchCannotReviveClosedDestinationOrDisconnectedSession() {
        for(int lifecycle=0;lifecycle<3;lifecycle++) {
            setup();deliver(request,Collections.singletonList(row("A",Status.BLOCKED)),true);
            KOMEPacketCompanyDestinationRequest next=selector.setFilter(READY);deliver(next,Collections.singletonList(row("B",Status.BLOCKED)),true);
            if(lifecycle==0)selector.close();else if(lifecycle==1)selector.open("T101");else state.start(null);
            assertNull(selector.takeBrowseFollowup());assertTrue(selector.selectedRoute().isEmpty());
        }
    }
    @Test public void changingFilterWithFullMatchingViewportDoesNotRequestOtherPages() {
        List<Row> rows=new ArrayList<Row>();for(int i=0;i<4;i++)rows.add(row("I"+i,Status.CAN_START_AND_ARRIVE_NOW));
        for(int i=0;i<4;i++)rows.add(row("L"+i,Status.CAN_START_BUT_CONTINUES_LATER));deliver(request,rows,true);
        for(int i=0;i<50;i++){assertNull(selector.setFilter(READY));assertNull(selector.setFilter(ARRIVE_NOW));assertNull(selector.setFilter(ALL));assertNull(selector.takeBrowseFollowup());}
        assertEquals(1,selector.historySize());assertFalse(state.isLoading());
        for(int i=0;i<4;i++)assertNull(selector.browse(1,4,0));
        assertEquals(2,selector.browse(1,4,0).sequence);
    }
    @Test public void rapidFiltersRemainResponsiveDuringEmptyContinuationAndSupersedeOnlyLocalSearch() {
        List<Row> loaded=Arrays.asList(row("L",Status.CAN_START_BUT_CONTINUES_LATER),row("B",Status.BLOCKED));
        deliver(request,loaded,true);
        KOMEPacketCompanyDestinationRequest pending=selector.setFilter(READY);
        assertNotNull(pending);
        assertNull(selector.setFilter(ARRIVE_NOW));assertTrue(ids().isEmpty());
        assertNull(selector.setFilter(READY));assertEquals(Collections.singletonList("L"),ids());
        assertNull(selector.setFilter(ALL));assertEquals(Arrays.asList("L","B"),ids());
        for(int i=0;i<100;i++){
            assertNull(selector.setFilter(ARRIVE_NOW));assertNull(selector.setFilter(READY));assertNull(selector.setFilter(ALL));
            assertNull(selector.takeBrowseFollowup());
        }
        KOMECompanyDestinationOptions old=deliver(pending,Collections.singletonList(row("OLD",Status.CAN_START_AND_ARRIVE_NOW)),true);
        assertEquals(ALL,selector.filter());assertEquals(Arrays.asList("L","B"),ids());assertEquals(0,selector.rowOffset());
        assertFalse(selector.accept(old,0));assertFalse(state.accept(old,connection));
        KOMEPacketCompanyDestinationRequest current=selector.takeBrowseFollowup();assertNotNull(current);
        assertEquals(pending.sequence+1,current.sequence);
        deliver(current,Arrays.asList(row("N1",Status.BLOCKED),row("N2",Status.BLOCKED)),false);
        assertEquals(ALL,selector.filter());assertNull(selector.takeBrowseFollowup());
    }
    @Test public void arriveNowCanReturnToReadyWhileThrottledWithoutRefreshOrNewRequests() {
        deliver(request,Arrays.asList(row("L",Status.CAN_START_BUT_CONTINUES_LATER),row("B",Status.BLOCKED)),true);
        KOMEPacketCompanyDestinationRequest pending=selector.setFilter(ARRIVE_NOW);
        deliver(pending,Result.RATE_LIMITED,Collections.<Row>emptyList(),false,500,1000);
        assertNull(selector.setFilter(READY));assertEquals(Collections.singletonList("L"),ids());
        assertNull(selector.setFilter(ALL));assertEquals(Arrays.asList("L","B"),ids());
        assertEquals(KOMECompanyDestinationSelector.Phase.THROTTLED,selector.phase());
        assertNull(selector.browse(1,4,1499));assertNull(selector.takeBrowseFollowup());
        KOMECompanyDestinationPanel.Layout layout=new KOMECompanyDestinationPanel.Layout(new KOMEMapViewport(0,0,640,360,0,0,1),false,false);
        for(KOMECompanyDestinationPanel.Control control:new KOMECompanyDestinationPanel.Control[]{
            KOMECompanyDestinationPanel.Control.FILTER_ALL,KOMECompanyDestinationPanel.Control.FILTER_READY,KOMECompanyDestinationPanel.Control.FILTER_ARRIVE})
            assertTrue(KOMECompanyDestinationPanel.enabled(control,selector,layout,1200));
    }
    @Test public void selectedIdentityAndRouteSurviveReadOnlyPageContinuationButNotInvalidation() {
        List<Row> loaded=new ArrayList<Row>();for(int i=1;i<=8;i++)loaded.add(row("A"+i,Status.CAN_START_AND_ARRIVE_NOW));
        deliver(request,loaded,true);selector.select("A1");List<String> route=selector.selectedRoute();
        for(int i=0;i<4;i++)selector.browse(1,4,0);
        KOMEPacketCompanyDestinationRequest pending=selector.browse(1,4,0);
        assertNotNull(pending);assertEquals("A1",selector.selectedCompanyId());assertEquals(route,selector.selectedRoute());
        assertFalse(selector.canConfirm());assertEquals("A1",selector.selectedCompanyId());
        deliver(pending,Arrays.asList(row("B1",Status.CAN_START_AND_ARRIVE_NOW),row("B2",Status.CAN_START_AND_ARRIVE_NOW)),true);
        assertEquals("A1",selector.selected().companyId);assertEquals(route,selector.selectedRoute());assertEquals("A1",selector.selectedCompanyId());
        assertFalse(selector.canConfirm());selector.clearSelection();assertEquals("",selector.selectedCompanyId());
    }
    @Test public void filterDuringFirstLoadUsesFreshEvidenceUnderLatestLocalFilter() {
        assertNull(selector.setFilter(READY));assertNull(selector.setFilter(ARRIVE_NOW));assertNull(selector.setFilter(ALL));
        deliver(request,Arrays.asList(row("A",Status.BLOCKED),row("L",Status.CAN_START_BUT_CONTINUES_LATER)),false);
        assertEquals(ALL,selector.filter());assertEquals(2,ids().size());assertEquals(KOMECompanyDestinationSelector.Phase.READY,selector.phase());
    }    @Test public void sparseContinuationRetainsVisibleMatchesAndFilterRestartRevalidatesDiscardedExclusions() {
        deliver(request,Arrays.asList(row("A1",Status.CAN_START_AND_ARRIVE_NOW),row("A2",Status.CAN_START_AND_ARRIVE_NOW),
            row("A3",Status.CAN_START_AND_ARRIVE_NOW)),true);
        KOMEPacketCompanyDestinationRequest pending=selector.setFilter(ARRIVE_NOW);
        for(int page=1;page<=2;page++){
            List<Row> blocked=new ArrayList<Row>();for(int i=1;i<=8;i++)blocked.add(row("B"+page+i,Status.BLOCKED));
            deliver(pending,blocked,true);pending=selector.takeBrowseFollowup();assertNotNull(pending);
        }
        List<Row> last=new ArrayList<Row>();for(int i=1;i<=8;i++)last.add(row("C"+i,Status.CAN_START_AND_ARRIVE_NOW));
        deliver(pending,last,false);
        assertEquals(Arrays.asList("A1","A2","A3","C1"),ids().subList(0,4));assertNull(selector.takeBrowseFollowup());
        assertNull(selector.browse(1,4,0));assertEquals(1,selector.rowOffset());
        assertEquals(Arrays.asList("A2","A3","C1","C2"),ids().subList(1,5));
        assertTrue(selector.retainedBrowseProjections()<=23);
        KOMEPacketCompanyDestinationRequest fresh=selector.setFilter(ALL);
        assertNotNull(fresh);assertTrue(fresh.afterCompanyId.isEmpty());assertEquals(ALL,selector.filter());assertEquals(0,selector.rowOffset());
        deliver(fresh,Arrays.asList(row("A1",Status.CAN_START_AND_ARRIVE_NOW),row("A2",Status.CAN_START_AND_ARRIVE_NOW),
            row("A3",Status.CAN_START_AND_ARRIVE_NOW)),true);
        assertTrue(selector.rows().size()<=3); // No concatenation across discarded unknown raw pages.
    }    @Test public void continuationThrottleAndExplicitRetryPreserveSelectedIdentityAndRoute() {
        List<Row> loaded=new ArrayList<Row>();for(int i=1;i<=8;i++)loaded.add(row("A"+i,Status.CAN_START_AND_ARRIVE_NOW));
        deliver(request,loaded,true);selector.select("A1");List<String> route=selector.selectedRoute();
        for(int i=0;i<4;i++)selector.browse(1,4,0);
        KOMEPacketCompanyDestinationRequest pending=selector.browse(1,4,0);
        deliver(pending,Result.RATE_LIMITED,Collections.<Row>emptyList(),false,500,1000);
        assertEquals("A1",selector.selectedCompanyId());assertEquals(route,selector.selectedRoute());
        KOMEPacketCompanyDestinationRequest retry=selector.browse(1,4,1500);
        assertNotNull(retry);assertEquals("A1",selector.selectedCompanyId());assertEquals(route,selector.selectedRoute());
        deliver(retry,Collections.singletonList(row("B",Status.CAN_START_AND_ARRIVE_NOW)),false);
        assertEquals("A1",selector.selectedCompanyId());assertEquals(route,selector.selectedRoute());
    }    @Test public void selectedIdentityRemainsSelectedUntilMovementResultActuallyClearsSelection() {
        deliver(request,Collections.singletonList(row("A",Status.CAN_START_AND_ARRIVE_NOW)),false);
        selector.select("A");assertNotNull(selector.confirm());
        assertEquals(KOMECompanyDestinationSelector.Phase.SUBMITTING,selector.phase());
        assertEquals("A",selector.selectedCompanyId());assertEquals("A",selector.selectedCompanyId());
        selector.close();assertEquals("",selector.selectedCompanyId());
    }    private static Row copy(Row r,int allowance,int distance,String code) {
        return new Row(r.evaluationState,r.companyId,r.name,r.currentTile,r.destinationTile,r.entitlement,r.tilesPerDay,allowance,distance,r.route,
            r.status,r.scheduleMode,r.canStartNow,r.canArriveNow,r.legalRoute,r.timingAvailable,r.futureBoundaries,r.nextBoundaryMillis,
            r.estimatedCompletionMillis,code,r.denialDetail,r.heldConflictId,r.heldConflictRevision,r.consequence,r.destinationConflictId,
            r.destinationConflictRevision,r.legacyFingerprint,r.confirmationFingerprint);
    }
}
