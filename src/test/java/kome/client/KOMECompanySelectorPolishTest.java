package kome.client;

import java.util.*;
import kome.common.command.KOMECompanyMovementService.*;
import kome.common.data.*;
import kome.common.data.KOMECompanyDestinationOptions.*;
import kome.common.network.*;
import org.junit.*;
import static org.junit.Assert.*;
import static kome.client.KOMECompanyDestinationSelectorTest.*;

public class KOMECompanySelectorPolishTest {
    private final Object connection=new Object();
    private KOMECompanyDestinationClientState state;
    private KOMECompanyDestinationSelector model;
    private KOMEPacketCompanyDestinationRequest initial;
    @Before public void setup(){state=new KOMECompanyDestinationClientState();state.start(connection);model=new KOMECompanyDestinationSelector(state);initial=model.open("T100");model.scrollRows(0,4);}
    private List<Row> companies(int from,int to) {List<Row> rows=new ArrayList<Row>();for(int i=from;i<=to;i++)rows.add(row(String.format(Locale.ROOT,"C%02d",i),Status.CAN_START_AND_ARRIVE_NOW));return rows;}
    private void load(KOMEPacketCompanyDestinationRequest request,int count) {
        assertNotNull(request);int from=request.afterCompanyId.isEmpty()?1:Integer.parseInt(request.afterCompanyId.substring(1))+1;
        KOMECompanyDestinationOptions response=page(request,Result.OK,companies(from,Math.min(count,from+7)),from+7<count,0);
        assertTrue(state.accept(response,connection));assertTrue(model.accept(response,0));
    }
    private List<String> visible(){List<String> out=new ArrayList<String>();for(int i=model.rowOffset();i<Math.min(model.rows().size(),model.rowOffset()+4);i++)out.add(model.rows().get(i).companyId);return out;}
    private KOMECompanyDestinationPanel.Layout layout(int width,int height,boolean join){return new KOMECompanyDestinationPanel.Layout(new KOMEMapViewport(0,0,width,height,0,0,1),false,join);}
    @Test public void fourRowsNormalAndSmallFallbackNeverOverlapsFiltersScrollbarDetailsOrActions() {
        for(boolean join:new boolean[]{false,true})for(int[] size:new int[][]{{320,240},{480,270},{640,360},{960,540}}) {
            KOMECompanyDestinationPanel.Layout layout=layout(size[0],size[1],join);
            assertEquals(size[1]>=360?4:1,layout.capacity);assertTrue(layout.detailHeight>=20);
            for(KOMECompanyDestinationPanel.Control c:KOMECompanyDestinationPanel.Control.values()) {
                KOMECompanyDestinationPanel.Rect r=layout.controls.get(c);if(r==null){assertEquals(KOMECompanyDestinationPanel.Control.CONFIRM_JOIN,c);continue;}
                assertEquals(c,layout.controlAt(r.x+1,r.y+1));assertTrue(layout.panel.contains(r.x+r.width-1,r.y+r.height-1));
                for(int i=0;i<layout.capacity;i++)assertEquals(-1,layout.rowAt(r.x+1,r.y+1,4));
            }
            assertEquals(-1,layout.rowAt(layout.scrollTrack.x+1,layout.scrollTrack.y+1,4));
            if(join)assertTrue(layout.controls.get(KOMECompanyDestinationPanel.Control.CONFIRM_JOIN).y
                >=layout.controls.get(KOMECompanyDestinationPanel.Control.CONFIRM).y+28);
        }
    }
    @Test public void fourRowSeamComposesFiveToEightSixToNineThroughNineToTwelveAndBack() {
        load(initial,20);for(int i=0;i<4;i++)assertNull(model.browse(1,4,0));
        assertEquals(Arrays.asList("C05","C06","C07","C08"),visible());
        KOMEPacketCompanyDestinationRequest next=model.browse(1,4,0);
        assertEquals("C08",next.afterCompanyId);assertEquals(Arrays.asList("C05","C06","C07","C08"),visible());
        assertEquals(KOMECompanyDestinationSelector.Phase.LOADING,model.phase());
        load(next,20);
        for(int first=6;first<=9;first++) {
            assertEquals(Arrays.asList(String.format("C%02d",first),String.format("C%02d",first+1),String.format("C%02d",first+2),String.format("C%02d",first+3)),visible());
            assertEquals(4,visible().size());if(first<9)assertNull(model.browse(1,4,0));
        }
        for(int first=8;first>=5;first--) {assertNull(model.browse(-1,4,0));assertEquals(String.format("C%02d",first),visible().get(0));assertEquals(4,visible().size());}
        Set<String> unique=new HashSet<String>();for(Row r:model.rows())assertTrue(unique.add(r.companyId));
    }
    @Test public void rollingProjectionBoundAndBackwardRefetchRemainContiguousForFortyEightCompanies() {
        load(initial,48);List<List<String>> windows=new ArrayList<List<String>>();
        while(true) {
            windows.add(visible());assertEquals(4,visible().size());assertTrue(model.retainedBrowseRows()<=24);
            if(!model.canBrowse(1,0))break;
            KOMEPacketCompanyDestinationRequest request=model.browse(1,4,0);if(request!=null)load(request,48);
        }
        assertEquals(45,windows.size());assertEquals(Arrays.asList("C45","C46","C47","C48"),visible());
        for(int i=windows.size()-2;i>=0;i--){KOMEPacketCompanyDestinationRequest back=model.browse(-1,4,0);if(back!=null)load(back,48);assertEquals(windows.get(i),visible());assertTrue(model.retainedBrowseRows()<=24);}
        assertFalse(model.canBrowse(-1,0));
    }
    @Test public void cachedVisibleSelectionCannotSubmitUntilExactPageIsRevalidated() {
        load(initial,20);for(int i=0;i<4;i++)model.browse(1,4,0);load(model.browse(1,4,0),20);
        KOMEPacketCompanyDestinationRequest fresh=model.selectForBrowse("C06");assertNotNull(fresh);assertTrue(fresh.afterCompanyId.isEmpty());
        assertFalse(model.canConfirm());assertNull(model.confirm());assertNull(model.confirmJoinClick(0));
        load(fresh,20);assertEquals("C06",model.selected().companyId);assertTrue(model.canConfirm());
        KOMEPacketCompanyMovementRequest order=model.confirm();assertEquals(fresh.sequence,order.previewSequence);assertEquals("C06",order.companyId);
    }
    @Test public void displayOnlyScrollbarTracksLoadedBeginningMiddleEndAndPendingWithoutGlobalPercentage() {
        load(initial,20);KOMECompanyDestinationPanel.Layout layout=layout(640,360,false);
        KOMECompanyDestinationPanel.Scrollbar beginning=new KOMECompanyDestinationPanel.Scrollbar(model,layout);
        assertEquals(layout.scrollTrack.y,beginning.thumb.y);assertTrue(beginning.later);assertFalse(beginning.exactGlobalTotal);
        model.browse(1,4,0);KOMECompanyDestinationPanel.Scrollbar middle=new KOMECompanyDestinationPanel.Scrollbar(model,layout);assertTrue(middle.thumb.y>beginning.thumb.y);
        for(int i=0;i<3;i++)model.browse(1,4,0);KOMEPacketCompanyDestinationRequest next=model.browse(1,4,0);
        assertTrue(new KOMECompanyDestinationPanel.Scrollbar(model,layout).pending);load(next,20);
        while(model.canBrowse(1,0)){next=model.browse(1,4,0);if(next!=null)load(next,20);}
        KOMECompanyDestinationPanel.Scrollbar end=new KOMECompanyDestinationPanel.Scrollbar(model,layout);
        assertFalse(end.later);assertEquals(layout.scrollTrack.y+layout.scrollTrack.height,end.thumb.y+end.thumb.height);
        KOMEPacketCompanyDestinationRequest filter=model.setFilter(KOMECompanyDestinationSelector.Filter.READY);if(filter!=null)load(filter,20);
        assertFalse(new KOMECompanyDestinationPanel.Scrollbar(model,layout).exactGlobalTotal);assertEquals(0,model.rowOffset());
    }
    @Test public void explicitFiltersAreAlwaysVisibleAndHighlightOnlyTheChosenMode() {
        load(initial,8);KOMECompanyDestinationPanel.Layout layout=layout(640,360,false);
        KOMECompanyDestinationPanel.Control[] controls={KOMECompanyDestinationPanel.Control.FILTER_ALL,KOMECompanyDestinationPanel.Control.FILTER_READY,KOMECompanyDestinationPanel.Control.FILTER_ARRIVE};
        for(KOMECompanyDestinationSelector.Filter mode:KOMECompanyDestinationSelector.Filter.values()) {
            model.setFilter(mode);int active=0;
            for(KOMECompanyDestinationPanel.Control c:controls){assertNotNull(layout.controls.get(c));if(KOMECompanyDestinationPanel.filterSelected(c,model))active++;}
            assertEquals(1,active);assertEquals(0,model.rowOffset());
        }
    }
    @Test public void cachedSelectionIdentityIsImmediateStableAndStillRequiresFreshSubmissionEvidence() {
        load(initial,20);for(int i=0;i<4;i++)model.browse(1,4,0);load(model.browse(1,4,0),20);
        Row cached=model.visibleRow(1);assertEquals("C07",cached.companyId);
        KOMEPacketCompanyDestinationRequest fresh=model.selectForBrowse(cached.companyId);
        assertNotNull(fresh);assertEquals("C07",model.selectedCompanyId());assertTrue(model.isSelected("C07"));
        assertTrue(model.isSelected(row("C07",Status.CAN_START_AND_ARRIVE_NOW).companyId));
        assertFalse(model.isSelected("C08"));assertFalse(model.canConfirm());assertNull(model.confirm());
        assertEquals("C07",model.selectedCompanyId());
        load(fresh,20);assertNotSame(cached,model.selected());assertEquals("C07",model.selectedCompanyId());
        assertTrue(model.canConfirm());assertTrue(model.isSelected(model.visibleRow(1).companyId));
    }
    @Test public void pendingCachedSelectionCannotReturnAfterClearCloseDestinationOrSessionReplacement() {
        for(int clear=0;clear<4;clear++){
            setup();load(initial,20);for(int i=0;i<4;i++)model.browse(1,4,0);load(model.browse(1,4,0),20);
            KOMEPacketCompanyDestinationRequest pending=model.selectForBrowse("C07");
            if(clear==0)model.clearSelection();if(clear==1)model.close();if(clear==2)model.open("T101");if(clear==3)state.start(new Object());
            assertEquals("",model.selectedCompanyId());assertFalse(model.isSelected("C07"));
            if(clear==0){load(pending,20);assertEquals("",model.selectedCompanyId());}
            else {KOMECompanyDestinationOptions late=page(pending,Result.OK,companies(1,8),true,0);assertFalse(state.accept(late,connection));assertFalse(model.accept(late,0));}
        }
    }
    @Test public void missingOrFilterHiddenFreshSelectedRowClearsCanonicalIdentity() {
        for(int invalid=0;invalid<2;invalid++){
            setup();load(initial,20);for(int i=0;i<4;i++)model.browse(1,4,0);load(model.browse(1,4,0),20);
            model.setFilter(KOMECompanyDestinationSelector.Filter.READY);
            // Filter may keep the full loaded extent; selecting cached C07 revalidates page one.
            KOMEPacketCompanyDestinationRequest pending=model.selectForBrowse("C07");assertNotNull(pending);
            List<Row> freshRows=companies(1,8);freshRows.remove(6);
            if(invalid==1)freshRows.add(row("C07",Status.BLOCKED));
            KOMECompanyDestinationOptions response=page(pending,Result.OK,freshRows,true,0);
            assertTrue(state.accept(response,connection));assertTrue(model.accept(response,0));
            assertEquals("",model.selectedCompanyId());assertNull(model.selected());assertFalse(model.isSelected("C07"));
        }
    }    @Test public void rapidCachedSelectionChangesIdentityImmediatelyWithoutOverlappingQueriesOrOldReplyRestoringIt() {
        load(initial,20);for(int i=0;i<4;i++)model.browse(1,4,0);load(model.browse(1,4,0),20);
        KOMEPacketCompanyDestinationRequest pending=model.selectForBrowse("C06");assertNotNull(pending);
        assertNull(model.selectForBrowse("C07"));assertEquals("C07",model.selectedCompanyId());
        assertNull(model.selectForBrowse("C09"));assertEquals("C09",model.selectedCompanyId());
        load(pending,20);assertEquals("C09",model.selectedCompanyId());assertTrue(model.isSelected("C09"));
        KOMEPacketCompanyDestinationRequest next=model.takeBrowseFollowup();assertNotNull(next);
        assertEquals("C08",next.afterCompanyId);assertEquals(pending.sequence+1,next.sequence);
        assertNull(model.takeBrowseFollowup());assertFalse(model.canConfirm());
        load(next,20);assertEquals("C09",model.selectedCompanyId());assertTrue(model.canConfirm());
        assertFalse(model.isSelected("C06"));assertFalse(model.isSelected("C07"));
    }    @Test public void selectionDuringSupersededFilterRequestKeepsIdentityAndSerializesFreshEvidence() {
        load(initial,20);for(int i=0;i<4;i++)model.browse(1,4,0);
        KOMEPacketCompanyDestinationRequest pending=model.browse(1,4,0);
        assertNull(model.setFilter(KOMECompanyDestinationSelector.Filter.READY));
        assertNull(model.selectForBrowse("C03"));assertEquals("C03",model.selectedCompanyId());
        load(pending,20);assertEquals("C03",model.selectedCompanyId());
        KOMEPacketCompanyDestinationRequest fresh=model.takeBrowseFollowup();assertNotNull(fresh);
        assertTrue(fresh.afterCompanyId.isEmpty());assertEquals(pending.sequence+1,fresh.sequence);assertFalse(model.canConfirm());
        load(fresh,20);assertEquals("C03",model.selectedCompanyId());assertTrue(model.canConfirm());
    }
    @Test public void canonicalSelectionDrivesDistinctRoutePreviewAndOneClickSubmissionWithoutMapEmphasis() {
        Row first=routedRow("C01","T101","T102"),second=routedRow("C02","T103","T104");
        KOMECompanyDestinationOptions response=page(initial,Result.OK,Arrays.asList(first,second),false,0);
        assertTrue(state.accept(response,connection));assertTrue(model.accept(response,0));
        assertNull(model.selectForBrowse("C01"));assertEquals("C01",model.selectedCompanyId());
        assertTrue(model.isSelected("C01"));assertEquals(first.route,model.selectedRoute());
        assertNull(model.selectForBrowse("C02"));assertEquals("C02",model.selectedCompanyId());
        assertFalse(model.isSelected("C01"));assertTrue(model.isSelected("C02"));assertEquals(second.route,model.selectedRoute());
        KOMEPacketCompanyMovementRequest move=model.confirm();assertNotNull(move);
        assertEquals("C02",move.companyId);assertEquals(second.legacyFingerprint,move.legacyFingerprint);
        assertEquals(second.confirmationFingerprint,move.confirmationFingerprint);assertEquals(initial.sequence,move.previewSequence);
    }
    private Row routedRow(String company,String origin,String middle) {
        Row r=row(company,Status.CAN_START_AND_ARRIVE_NOW);
        return new Row(r.evaluationState,r.companyId,"Same name",origin,r.destinationTile,r.entitlement,r.tilesPerDay,r.remainingAllowance,
            r.distance,Arrays.asList(origin,middle,r.destinationTile),r.status,r.scheduleMode,r.canStartNow,r.canArriveNow,r.legalRoute,r.timingAvailable,
            r.futureBoundaries,r.nextBoundaryMillis,r.estimatedCompletionMillis,r.denialCode,r.denialDetail,r.heldConflictId,r.heldConflictRevision,
            r.consequence,r.destinationConflictId,r.destinationConflictRevision,r.legacyFingerprint,r.confirmationFingerprint);
    }    private void hostile() {
        List<Row> rows=Arrays.asList(row("A",Status.CAN_START_AND_ARRIVE_NOW,Entitlement.MOUNTED,EvaluationState.COMPLETE,Consequence.EXPECTED_CREATE_CONFLICT),
            row("B",Status.CAN_START_AND_ARRIVE_NOW,Entitlement.MOUNTED,EvaluationState.COMPLETE,Consequence.EXPECTED_JOIN_CONFLICT));
        KOMECompanyDestinationOptions response=page(initial,Result.OK,rows,false,0);assertTrue(state.accept(response,connection));assertTrue(model.accept(response,0));model.select("A");
    }
    @Test public void joinFirstClickOnlyArmsSecondExecutesOnceAndOrdinaryMoveIsOneClick() {
        hostile();KOMECompanyDestinationOptions evidence=state.latest();
        assertEquals("Move & Join Battle",KOMECompanyDestinationPanel.label(KOMECompanyDestinationPanel.Control.CONFIRM_JOIN,model,100));
        assertEquals("Confirm Move",KOMECompanyDestinationPanel.label(KOMECompanyDestinationPanel.Control.CONFIRM,model,100));
        assertNull(model.confirmJoinClick(100));assertTrue(model.joinArmed(101));assertEquals("Confirm Join Battle",KOMECompanyDestinationPanel.label(KOMECompanyDestinationPanel.Control.CONFIRM_JOIN,model,101));assertSame(evidence,state.latest());assertFalse(state.isLoading());assertNull(model.joinViewRequest());
        KOMEPacketCompanyMovementRequest order=model.confirmJoinClick(102);assertNotNull(order);assertEquals("A",order.companyId);
        assertNull(model.confirmJoinClick(103));assertNull(model.confirmJoinClick(104));assertFalse(model.joinArmed(104));
        setup();hostile();assertNotNull(model.confirm());
    }
    @Test public void joinArmingExpiresAndEverySelectionFilterQueryDestinationAndSessionChangeClearsIt() {
        for(int change=0;change<8;change++) {
            setup();hostile();assertNull(model.confirmJoinClick(0));assertTrue(model.joinArmed(1));
            if(change==0)model.select("B");if(change==1)model.clearSelection();if(change==2)model.open("T101");
            if(change==3)model.setFilter(KOMECompanyDestinationSelector.Filter.READY);if(change==4)model.retry(2);
            if(change==5)model.close();if(change==6)state.start(new Object());if(change==7)state.close();
            assertFalse(model.joinArmed(3));
        }
        setup();hostile();model.confirmJoinClick(0);assertTrue(model.joinArmed(4999));assertFalse(model.joinArmed(5000));
        assertNull(model.confirmJoinClick(5001));assertNotNull(model.confirmJoinClick(5002));
    }
    @Test public void invalidatedCachedQueryDropsWindowArmAndSelection() {
        load(initial,20);for(int i=0;i<4;i++)model.browse(1,4,0);load(model.browse(1,4,0),20);
        KOMEPacketCompanyDestinationRequest request=model.selectForBrowse("C06");
        KOMECompanyDestinationOptions denied=page(request,Result.CATALOGUE_CHANGED,Collections.<Row>emptyList(),false,0);
        assertTrue(state.accept(denied,connection));assertTrue(model.accept(denied,0));
        assertEquals(0,model.retainedBrowseRows());assertNull(model.selected());assertEquals("",model.selectedCompanyId());assertFalse(model.joinArmed(0));
        request=model.retry(0);assertTrue(request.afterCompanyId.isEmpty());assertTrue(request.catalogueToken.isEmpty());
    }

}
