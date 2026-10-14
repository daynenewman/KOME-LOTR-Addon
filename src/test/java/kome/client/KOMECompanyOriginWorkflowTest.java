package kome.client;

import java.util.*;
import kome.common.data.*;
import kome.common.network.*;
import kome.common.command.KOMECompanyMovementService.*;
import org.junit.*;
import static org.junit.Assert.*;
import static kome.common.data.KOMECompanyDestinationOptions.*;

public class KOMECompanyOriginWorkflowTest {
    private final Object connection=new Object();
    private KOMECompanyDestinationClientState state;
    private KOMECompanyOriginWorkflow flow;
    private KOMEPacketCompanyOriginRequest request;
    @Before public void setup(){state=new KOMECompanyDestinationClientState();state.start(connection);flow=new KOMECompanyOriginWorkflow(state);request=flow.open("T001");}
    static KOMECompanySourceOptions page(KOMEPacketCompanyOriginRequest r,int total){
        int from=r.after.isEmpty()?1:Integer.parseInt(r.after.substring(1))+1;List<KOMECompanySourceOptions.Row> rows=new ArrayList<>();
        for(int i=from;i<=Math.min(total,from+7);i++)rows.add(new KOMECompanySourceOptions.Row(String.format(Locale.ROOT,"C%02d",i),
            "Same company name",1,100,false,1,1,"Stationed",false));
        return new KOMECompanySourceOptions(r.session,r.sequence,r.source,Result.OK,rows,KOMECompanyDestinationSelectorTest.TOKEN,
            rows.isEmpty()?"":rows.get(rows.size()-1).companyId,from+7<total,0);
    }
    @Test public void sourceRowsPickStableCompanyAndNoDestinationEvidenceOrMutationExists(){
        flow.accept(page(request,4),0);assertTrue(flow.select(3));assertEquals("C04",flow.selectedCompanyId());
        assertEquals(KOMECompanyOriginWorkflow.Stage.PICK,flow.stage());assertEquals("T001",flow.source());assertEquals("Same company name",flow.selectedName());
        assertFalse(flow.select(0));assertEquals("C04",flow.selectedCompanyId());
        assertEquals("Campaign Company",KOMECompanyOriginWorkflow.name(new KOMECompanySourceOptions.Row("C1","C1",1,100,false,1,1,"Stationed",false)));
    }
    @Test public void nineEighteenTwentyRowsTraverseContiguousWindowsBothDirectionsAndStayBounded(){
        for(int count:new int[]{9,18,20,40}){
            setup();flow.accept(page(request,count),0);
            for(int top=0;top<=count-4;top++){
                for(int i=0;i<4;i++)assertEquals(String.format(Locale.ROOT,"C%02d",top+i+1),flow.visible(i).companyId);
                assertTrue(flow.rows().size()<=24);
                if(top<count-4){KOMEPacketCompanyOriginRequest next=flow.scroll(1,4,0);if(next!=null)assertTrue(flow.accept(page(next,count),0));}
            }
            for(int top=count-4;top>=0;top--){
                for(int i=0;i<4;i++)assertEquals(String.format(Locale.ROOT,"C%02d",top+i+1),flow.visible(i).companyId);
                assertTrue(flow.rows().size()<=24);
                if(top>0){KOMEPacketCompanyOriginRequest next=flow.scroll(-1,4,0);if(next!=null)flow.accept(page(next,count),0);}
            }
        }
    }
    @Test public void sourceLoadingThrottleAndStaleResponsesNeverPollOrReplaceCurrentState(){
        assertTrue(flow.loading());assertNull(flow.scroll(1,4,0));
        KOMECompanySourceOptions throttled=new KOMECompanySourceOptions(request.session,request.sequence,request.source,Result.RATE_LIMITED,Collections.emptyList(),"","",false,1000);
        assertTrue(flow.accept(throttled,0));assertTrue(flow.message().contains("shortly"));assertNull(flow.scroll(1,4,999));
        KOMEPacketCompanyOriginRequest retry=flow.scroll(1,4,1000);assertNotNull(retry);
        assertFalse(flow.accept(page(request,8),1001));assertTrue(flow.loading());
        flow.accept(page(retry,8),1002);assertFalse(flow.loading());
        flow.select(0);assertFalse(flow.accept(page(retry,8),1003));assertEquals(KOMECompanyOriginWorkflow.Stage.PICK,flow.stage());
    }
    @Test public void backRefreshCloseAndSessionReplacementClearAllSelectionAndQueries(){
        flow.accept(page(request,4),0);flow.select(1);KOMEPacketCompanyOriginRequest back=flow.backToSource();
        assertEquals("",flow.selectedCompanyId());assertNotEquals(request.session,back.session);assertFalse(flow.accept(page(request,4),1));
        flow.accept(page(back,4),2);state.start(new Object());assertFalse(flow.active());assertEquals("",flow.source());
        setup();flow.clear();assertFalse(flow.accept(page(request,4),0));assertEquals("",flow.selectedCompanyId());
    }
    @Test public void targetedSelectorPreselectsExactCompanyAndSharesNormalSubmissionAndJoinLatch(){
        for(Consequence consequence:Consequence.values()){
            setup();flow.accept(page(request,1),0);flow.select(0);
            KOMECompanyDestinationSelector selector=new KOMECompanyDestinationSelector(state);
            KOMEPacketCompanyDestinationRequest descriptor=selector.openExact(flow.source(),flow.selectedCompanyId(),"T100");flow.preview();
            KOMEPacketCompanyOriginRequest exact=selector.targeted(descriptor);assertEquals("C01",exact.company);assertTrue(exact.targeted);
            Row row=KOMECompanyDestinationSelectorTest.row("C01",Status.CAN_START_AND_ARRIVE_NOW,Entitlement.FOOT_OR_MIXED,EvaluationState.COMPLETE,consequence);
            KOMECompanyDestinationOptions p=KOMECompanyDestinationSelectorTest.page(descriptor,Result.OK,Arrays.asList(row),false,0);
            assertTrue(state.accept(p,connection));assertTrue(selector.accept(p,0));assertEquals("C01",selector.selectedCompanyId());
            assertEquals(row.route,selector.selectedRoute());assertTrue(selector.canConfirm());
            KOMEPacketCompanyMovementRequest order;
            if(consequence!=Consequence.NONE){assertNull(selector.confirmJoinClick(0));assertTrue(selector.joinArmed(1));order=selector.confirmJoinClick(1);}
            else order=selector.confirm();
            assertNotNull(order);assertEquals("C01",order.companyId);assertEquals(row.confirmationFingerprint,order.confirmationFingerprint);
            assertNull(selector.confirm());assertNull(selector.confirmJoinClick(2));
        }
    }
    @Test public void exactBlockedMultidayAndReplacedDestinationRetainCanonicalSemantics(){
        for(Status status:Status.values()){
            KOMECompanyDestinationSelector selector=new KOMECompanyDestinationSelector(state);
            KOMEPacketCompanyDestinationRequest old=selector.openExact("T001","C09","T100");
            Row row=KOMECompanyDestinationSelectorTest.row("C09",status);
            KOMECompanyDestinationOptions page=KOMECompanyDestinationSelectorTest.page(old,Result.OK,Arrays.asList(row),false,0);
            assertTrue(state.accept(page,connection));selector.accept(page,0);assertEquals("C09",selector.selectedCompanyId());
            assertEquals(status!=Status.BLOCKED,selector.canConfirm());assertFalse(selector.canConfirmAndJoin());
            KOMEPacketCompanyDestinationRequest fresh=selector.openExact("T001","C09","T200");
            assertFalse(state.accept(page,connection));assertFalse(selector.accept(page,0));assertNull(selector.selected());assertTrue(selector.selectedRoute().isEmpty());
            assertEquals("T200",fresh.destination);assertNull(selector.confirm());
        }
    }
    @Test public void wrongCompanyTargetResponseCannotSelectOrArmAnotherCompany(){
        KOMECompanyDestinationSelector selector=new KOMECompanyDestinationSelector(state);
        KOMEPacketCompanyDestinationRequest descriptor=selector.openExact("T001","C01","T100");
        Row other=KOMECompanyDestinationSelectorTest.row("C02",Status.CAN_START_AND_ARRIVE_NOW,Entitlement.FOOT_OR_MIXED,
            EvaluationState.COMPLETE,Consequence.EXPECTED_CREATE_CONFLICT);
        KOMECompanyDestinationOptions p=KOMECompanyDestinationSelectorTest.page(descriptor,Result.OK,Arrays.asList(other),false,0);
        assertTrue(state.accept(p,connection));assertTrue(selector.accept(p,0));assertEquals(KOMECompanyDestinationSelector.Phase.ERROR,selector.phase());
        assertEquals("",selector.selectedCompanyId());assertFalse(selector.canConfirm());assertNull(selector.confirmJoinClick(0));assertTrue(selector.selectedRoute().isEmpty());
    }
    @Test public void sourcePanelHasNoDestinationFiltersOrConfirmButtons(){
        KOMECompanyDestinationPanel.Layout layout=KOMECompanyDestinationPanel.sourceLayout(new KOMEMapViewport(0,0,800,600,0,0,1),false);
        assertEquals(4,layout.capacity);assertFalse(layout.controls.containsKey(KOMECompanyDestinationPanel.Control.FILTER_READY));
        assertFalse(layout.controls.containsKey(KOMECompanyDestinationPanel.Control.FILTER_ARRIVE));assertFalse(layout.controls.containsKey(KOMECompanyDestinationPanel.Control.FILTER_ALL));
        assertFalse(layout.controls.containsKey(KOMECompanyDestinationPanel.Control.CONFIRM));
    }
}