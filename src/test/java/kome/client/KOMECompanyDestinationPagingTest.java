package kome.client;

import java.util.*;
import kome.common.command.KOMECompanyDestinationQueryTest.Catalogue;
import kome.common.command.KOMECompanyMovementService.*;
import kome.common.data.*;
import kome.common.data.KOMECompanyDestinationOptions.*;
import kome.common.network.KOMEPacketCompanyDestinationRequest;
import org.junit.*;
import static org.junit.Assert.*;
import static kome.client.KOMECompanyDestinationSelectorTest.*;

public class KOMECompanyDestinationPagingTest {
    @Rule public final KOMETileTestResources geometry=new KOMETileTestResources();
    private KOMEPopulationTestConfig config;
    private final Object connection=new Object();
    private KOMECompanyDestinationClientState state;
    private KOMECompanyDestinationSelector selector;
    private KOMEPacketCompanyDestinationRequest request;
    @Before public void before() throws Exception {
        config=new KOMEPopulationTestConfig();state=new KOMECompanyDestinationClientState();state.start(connection);
        selector=new KOMECompanyDestinationSelector(state);request=selector.open("T002");selector.scrollRows(0,4);
    }
    @After public void after() throws Exception {config.close();}
    private void accept(KOMECompanyDestinationOptions response) {
        assertTrue(state.accept(response,connection));assertTrue(selector.accept(response,0));
    }
    private void load(Catalogue catalogue,KOMEPacketCompanyDestinationRequest request) {
        int before=catalogue.inspections();KOMECompanyDestinationOptions response=catalogue.query(request);
        assertEquals(Result.OK,response.result);assertTrue(response.rows.size()<=8);
        assertTrue(catalogue.inspections()-before<=KOMECompanyDestinationOptions.MAX_ARRIVAL_CHECKS);
        accept(response);
    }
    private List<String> window(int capacity) {
        List<String> ids=new ArrayList<String>();
        for(int i=selector.rowOffset();i<Math.min(selector.rows().size(),selector.rowOffset()+capacity);i++)ids.add(selector.rows().get(i).companyId);
        return ids;
    }
    @Test public void nineAndTwentyCompaniesAreReachableAcrossLocalAndServerBoundariesAndBack() throws Exception {
        for(int count:new int[]{9,20})try(Catalogue catalogue=new Catalogue(count)) {
            request=selector.open("T002");selector.scrollRows(0,4);load(catalogue,request);
            List<List<String>> windows=new ArrayList<List<String>>();Set<String> seen=new HashSet<String>();int requests=1;
            while(true) {
                List<String> current=window(4);windows.add(current);for(String id:current)assertTrue("Duplicate "+id,seen.add(id));
                assertEquals("Companies "+(seen.size()-current.size()+1)+"–"+seen.size(),selector.companyRange(4));
                if(!selector.hasNext())break;
                int offset=selector.rowOffset();KOMEPacketCompanyDestinationRequest next=selector.next();
                if(next==null) {assertEquals(offset+4,selector.rowOffset());assertFalse(state.isLoading());}
                else {requests++;assertEquals(request.session,next.session);assertEquals("T002",next.destination);load(catalogue,next);}
            }
            assertEquals(count,seen.size());assertEquals((count+7)/8,requests);
            for(int i=1;i<=count;i++)assertTrue(seen.contains("C"+i));
            for(int i=windows.size()-2;i>=0;i--) {
                assertTrue(selector.hasPrevious());KOMEPacketCompanyDestinationRequest previous=selector.previous();
                if(previous!=null)load(catalogue,previous);
                assertEquals(windows.get(i),window(4));
            }
            assertFalse(selector.hasPrevious());assertEquals(0,selector.rowOffset());
            assertEquals((count+7)/8,selector.historySize());
        }
    }
    @Test public void wheelTraversesNineEighteenAndTwentyCompaniesInFullWindowsBothWays() throws Exception {
        for(int count:new int[]{9,18,20})try(Catalogue catalogue=new Catalogue(count)) {
            request=selector.open("T002");load(catalogue,request);int requests=1;
            List<List<String>> windows=new ArrayList<List<String>>();Set<String> seen=new HashSet<String>();
            while(true) {
                List<String> current=window(4);assertEquals(4,current.size());windows.add(current);seen.addAll(current);
                assertEquals(selector.rows().size(),new HashSet<Row>(selector.rows()).size());
                if(!selector.canBrowse(1,0))break;
                KOMEPacketCompanyDestinationRequest next=selector.browse(1,4,0);if(next!=null){requests++;load(catalogue,next);}
                assertNull(selector.takeBrowseFollowup());
            }
            assertEquals(count,seen.size());assertEquals((count+7)/8,requests);
            for(int i=windows.size()-2;i>=0;i--) {
                KOMEPacketCompanyDestinationRequest back=selector.browse(-1,4,0);if(back!=null)load(catalogue,back);
                assertEquals(windows.get(i),window(4));
            }
            assertFalse(selector.canBrowse(-1,0));assertEquals((count+7)/8,selector.historySize());
        }
    }
    @Test public void nonDivisibleLocalWindowsDoNotRepeatOrSkipRows() {
        List<Row> rows=new ArrayList<Row>();for(int i=1;i<=8;i++)rows.add(row("C"+i,Status.CAN_START_AND_ARRIVE_NOW));
        selector.scrollRows(0,5);accept(page(request,Result.OK,rows,false,0));
        assertEquals(Arrays.asList("C1","C2","C3","C4","C5"),window(5));
        assertNull(selector.next());assertEquals(Arrays.asList("C6","C7","C8"),window(5));
        assertFalse(selector.hasNext());assertNull(selector.previous());assertEquals(0,selector.rowOffset());
    }
    @Test public void newCompanyInvalidatesContinuationAndRefreshExposesItWithoutAcceptingOldReply() throws Exception {
        try(Catalogue catalogue=new Catalogue(20)) {
            KOMECompanyDestinationOptions old=catalogue.query(request);accept(old);
            selector.next();KOMEPacketCompanyDestinationRequest continuation=selector.next();
            catalogue.add("C21");KOMECompanyDestinationOptions changed=catalogue.query(continuation);
            assertEquals(Result.CATALOGUE_CHANGED,changed.result);accept(changed);
            KOMEPacketCompanyDestinationRequest refresh=selector.retry(0);assertTrue(refresh.afterCompanyId.isEmpty());
            assertTrue(refresh.catalogueToken.isEmpty());assertFalse(state.accept(old,connection));assertFalse(selector.accept(old,0));
            KOMECompanyDestinationOptions fresh=catalogue.query(refresh);assertNotEquals(old.catalogueToken,fresh.catalogueToken);accept(fresh);
            Set<String> seen=new HashSet<String>();
            while(true) {
                seen.addAll(window(4));if(!selector.hasNext())break;
                KOMEPacketCompanyDestinationRequest next=selector.next();if(next!=null)load(catalogue,next);
            }
            assertEquals(21,seen.size());assertTrue(seen.contains("C21"));
            assertNotNull(selector.retry(0)); // Refresh is also reachable from a successful final page.
            assertEquals(1,selector.historySize());
        }
    }
    @Test public void previousFirstPageRevalidatesCatalogueInsteadOfKeepingObsoleteHistory() throws Exception {
        try(Catalogue catalogue=new Catalogue(9)) {
            load(catalogue,request);selector.next();load(catalogue,selector.next());catalogue.add("C10");
            KOMECompanyDestinationOptions response=catalogue.query(selector.previous());
            assertEquals(Result.OK,response.result);accept(response);
            assertEquals(KOMECompanyDestinationSelector.Phase.ERROR,selector.phase());assertTrue(selector.rows().isEmpty());
            assertTrue(selector.retry(0).catalogueToken.isEmpty());
        }
    }
    @Test public void throttledBoundaryRetriesSameContinuationAndThenCanReturnToPriorWindow() throws Exception {
        try(Catalogue catalogue=new Catalogue(20)) {
            load(catalogue,request);selector.next();KOMEPacketCompanyDestinationRequest next=selector.next();
            accept(page(next,Result.RATE_LIMITED,Collections.<Row>emptyList(),false,200));
            assertNull(selector.retry(199));KOMEPacketCompanyDestinationRequest retry=selector.retry(200);
            assertEquals(next.afterCompanyId,retry.afterCompanyId);assertEquals(next.catalogueToken,retry.catalogueToken);
            load(catalogue,retry);assertEquals("Companies 9–12",selector.companyRange(4));
            load(catalogue,selector.previous());assertEquals("Companies 5–8",selector.companyRange(4));
        }
    }
    @Test public void evaluatedRowsSortByActionabilityDistanceNameAndInvisibleIdentityWithoutChangingCursor() {
        Row immediate=row("I",Status.CAN_START_AND_ARRIVE_NOW),later=row("L",Status.CAN_START_BUT_CONTINUES_LATER);
        Row blocked=row("B",Status.BLOCKED),deferred=row("D",null,Entitlement.FOOT_OR_MIXED,EvaluationState.DEFERRED_ROUTE_LIMIT,null);
        List<Row> rows=Arrays.asList(copy(deferred,"Deferred",2,-1,""),copy(blocked,"Exhausted",0,-1,"NO_ALLOWANCE"),
            copy(blocked,"Blocked",1,-1,"PASSAGE_DENIED"),later,copy(immediate,"Zulu",2,1,""),
            copy(row("A2",Status.CAN_START_AND_ARRIVE_NOW),"Alpha",2,2,""),copy(row("A1",Status.CAN_START_AND_ARRIVE_NOW),"Alpha",2,2,""),
            copy(row("Z",Status.CAN_START_AND_ARRIVE_NOW),"Beta",2,2,""));
        accept(page(request,Result.OK,rows,true,0));
        List<String> names=new ArrayList<String>();for(Row r:selector.rows())names.add(r.name);
        assertEquals(Arrays.asList("Zulu","Alpha","Alpha","Beta","Company L","Blocked","Exhausted","Deferred"),names);
        assertEquals("A1",selector.rows().get(1).companyId);assertEquals("A2",selector.rows().get(2).companyId);
        selector.select("A1");assertEquals("A1",selector.confirm().companyId); // Original accepted row remains submit-capable.
        request=selector.open("T002");selector.scrollRows(0,8);accept(page(request,Result.OK,rows,true,0));
        assertEquals("Z",selector.next().afterCompanyId); // Server cursor, not the sorted final row D.
    }
    private static Row copy(Row r,String name,int allowance,int distance,String code) {
        return new Row(r.evaluationState,r.companyId,name,r.currentTile,r.destinationTile,r.entitlement,r.tilesPerDay,allowance,distance,r.route,
            r.status,r.scheduleMode,r.canStartNow,r.canArriveNow,r.legalRoute,r.timingAvailable,r.futureBoundaries,r.nextBoundaryMillis,
            r.estimatedCompletionMillis,code,r.denialDetail,r.heldConflictId,r.heldConflictRevision,r.consequence,r.destinationConflictId,
            r.destinationConflictRevision,r.legacyFingerprint,r.confirmationFingerprint);
    }
    @Test public void shortPackedPagesUseActualRowCountsAndHistoryIsHardBounded() {
        selector.scrollRows(0,4);
        for(int i=0;i<KOMECompanyDestinationSelector.MAX_PAGE_HISTORY;i++) {
            accept(page(request,Result.OK,Collections.singletonList(row("C"+i,Status.CAN_START_AND_ARRIVE_NOW)),true,0));
            assertEquals("Companies "+(i+1)+"–"+(i+1),selector.companyRange(4));
            if(i+1<KOMECompanyDestinationSelector.MAX_PAGE_HISTORY)request=selector.next();
        }
        assertEquals(KOMECompanyDestinationOptions.MAX_CATALOGUE,selector.historySize());assertFalse(selector.hasNext());assertNull(selector.next());
        selector.close();assertEquals(0,selector.historySize());
    }
}
