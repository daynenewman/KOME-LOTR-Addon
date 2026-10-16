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
    @Before public void setup(){state=new KOMECompanyDestinationClientState();state.start(connection);flow=new KOMECompanyOriginWorkflow(state);flow.open("T001");}
    private KOMEPacketCompanyDestinationRequest choose(KOMECompanyDestinationSelector selector,String tile){
        KOMEPacketCompanyDestinationRequest r=selector.openSource(flow.source(),tile);flow.chooseDestination(tile);return r;
    }
    private KOMECompanyDestinationOptions page(KOMEPacketCompanyDestinationRequest r,int total){
        int from=r.afterCompanyId.isEmpty()?1:Integer.parseInt(r.afterCompanyId.substring(1))+1;
        List<Row> rows=new ArrayList<>();for(int i=from;i<=Math.min(total,from+7);i++)rows.add(KOMECompanyDestinationSelectorTest.row(String.format(Locale.ROOT,"C%02d",i),Status.CAN_START_AND_ARRIVE_NOW));
        return KOMECompanyDestinationSelectorTest.page(r,Result.OK,rows,from+7<total,0);
    }
    private void accept(KOMECompanyDestinationSelector selector,KOMECompanyDestinationOptions p){assertTrue(state.accept(p,connection));assertTrue(selector.accept(p,0));}
    @Test public void destinationPickPrecedesCompanyAndOriginalSourceStaysBound(){
        assertEquals(KOMECompanyOriginWorkflow.Stage.PICK,flow.stage());assertEquals("T001",flow.source());assertEquals("",flow.selectedCompanyId());assertNull(state.latest());
        KOMECompanyDestinationSelector selector=new KOMECompanyDestinationSelector(state);
        KOMEPacketCompanyDestinationRequest r=choose(selector,"T100");assertEquals(KOMECompanyOriginWorkflow.Stage.SOURCE,flow.stage());
        KOMEPacketCompanyOriginRequest source=selector.originRequest(r);assertFalse(source.targeted);assertEquals("T001",source.source);assertEquals("T100",source.destination);assertEquals("",source.company);
        accept(selector,page(r,1));assertEquals("",selector.selectedCompanyId());assertFalse(selector.canConfirm());
        selector.select("C01");flow.preview(selector.selected());assertEquals(KOMECompanyOriginWorkflow.Stage.PREVIEW,flow.stage());
        assertEquals("C01",flow.selectedCompanyId());assertEquals("T001",flow.source());assertEquals("T100",flow.destination());assertTrue(selector.canConfirm());assertFalse(selector.selectedRoute().isEmpty());
    }
    @Test public void sourceScopedPagesReuseContinuousBoundedBrowseAndNeverBecomeGlobalRequests(){
        for(int count:new int[]{9,18,20,40}){
            setup();KOMECompanyDestinationSelector selector=new KOMECompanyDestinationSelector(state);KOMEPacketCompanyDestinationRequest r=choose(selector,"T100");accept(selector,page(r,count));
            for(int top=0;top<=count-4;top++){
                for(int i=0;i<4;i++)assertEquals(String.format(Locale.ROOT,"C%02d",top+i+1),selector.visibleRow(i).companyId);
                assertTrue(selector.retainedBrowseProjections()<=32);
                if(top<count-4){r=selector.browse(1,4,0);while(r!=null){assertEquals("T001",selector.originRequest(r).source);assertEquals("T100",selector.originRequest(r).destination);accept(selector,page(r,count));r=selector.takeBrowseFollowup();}}
            }
            for(int top=count-5;top>=0;top--){r=selector.browse(-1,4,0);while(r!=null){accept(selector,page(r,count));r=selector.takeBrowseFollowup();}for(int i=0;i<4;i++)assertEquals(String.format(Locale.ROOT,"C%02d",top+i+1),selector.visibleRow(i).companyId);}
        }
    }
    @Test public void backAndNewDestinationDiscardPriorSelectionLatchAndCorrelation(){
        KOMECompanyDestinationSelector selector=new KOMECompanyDestinationSelector(state);KOMEPacketCompanyDestinationRequest first=choose(selector,"T100");KOMECompanyDestinationOptions old=page(first,1);accept(selector,old);
        selector.select("C01");flow.preview(selector.selected());selector.close();flow.pickAgain();
        assertEquals(KOMECompanyOriginWorkflow.Stage.PICK,flow.stage());assertEquals("T001",flow.source());assertEquals("",flow.destination());assertEquals("",flow.selectedCompanyId());
        KOMEPacketCompanyDestinationRequest second=choose(selector,"T200");assertNotEquals(first.session,second.session);assertFalse(state.accept(old,connection));assertFalse(selector.accept(old,0));
        assertEquals("T001",flow.source());assertEquals("T200",flow.destination());assertNull(selector.selected());assertTrue(selector.selectedRoute().isEmpty());assertNull(selector.confirm());
        state.start(new Object());assertFalse(flow.active());assertEquals("",flow.source());assertFalse(selector.isOpen());
    }
    @Test public void targetedSelectorPreselectsExactCompanyAndSharesNormalSubmissionAndJoinLatch(){
        for(Consequence consequence:Consequence.values()){
            setup();
            flow.chooseDestination("T100");
            KOMECompanyDestinationSelector selector=new KOMECompanyDestinationSelector(state);
            KOMEPacketCompanyDestinationRequest descriptor=selector.openExact(flow.source(),"C01","T100");flow.adoptSession();
            KOMEPacketCompanyOriginRequest exact=selector.originRequest(descriptor);assertEquals("C01",exact.company);assertTrue(exact.targeted);
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
    @Test public void differentSourceRowCannotBecomeSelectionOrConfirmation(){
        KOMECompanyDestinationSelector selector=new KOMECompanyDestinationSelector(state);KOMEPacketCompanyDestinationRequest r=selector.openSource("T002","T100");
        KOMECompanyDestinationOptions p=page(r,1);accept(selector,p);assertEquals(KOMECompanyDestinationSelector.Phase.ERROR,selector.phase());assertNull(selector.selected());assertFalse(selector.canConfirm());
    }
}
