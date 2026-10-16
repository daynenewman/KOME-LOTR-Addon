package kome.client;

import java.util.*;
import kome.common.data.*;
import kome.common.network.*;
import kome.common.command.KOMECompanyMovementService.*;
import org.junit.*;
import static org.junit.Assert.*;
import static kome.common.data.KOMECompanyRallySnapshot.*;

public class KOMECompanyRallyClientTest {
    private final Object connection=new Object();private KOMECompanyDestinationClientState state;
    private java.util.concurrent.atomic.AtomicLong time;
    private KOMECompanyRallyClient client;private KOMEPacketCompanyRallyRequest open;
    private final UUID token=UUID.randomUUID();
    @Before public void setup(){state=new KOMECompanyDestinationClientState();state.start(connection);time=new java.util.concurrent.atomic.AtomicLong();client=new KOMECompanyRallyClient(state,time::get);open=client.open("T100");}
    static Entry row(int index,boolean included,Consequence consequence){
        KOMECompanyDestinationOptions.Row row=KOMECompanyDestinationSelectorTest.row("C"+index,
            included?Status.CAN_START_AND_ARRIVE_NOW:Status.CAN_START_BUT_CONTINUES_LATER,KOMECompanyDestinationOptions.Entitlement.FOOT_OR_MIXED,
            KOMECompanyDestinationOptions.EvaluationState.COMPLETE,consequence);
        return new Entry(index,row,"gondor",included,Outcome.PREVIEW,included?"Will rally":"Requires later movement days");
    }
    private KOMECompanyRallySnapshot snapshot(Phase phase,int revision,int total,int evaluated,int included,Entry entry){
        return new KOMECompanyRallySnapshot(open.session,token,"T100",phase,revision,total,evaluated,included,
            phase==Phase.DONE?total:0,phase==Phase.DONE?included:0,phase==Phase.DONE?"Rally complete":"Preview",entry);
    }
    @Test public void confirmationRequiresEveryRowNotFirstPageOrOnlyACompleteFlag(){
        assertEquals(KOMEPacketCompanyRallyRequest.Action.OPEN,open.action);assertFalse(client.canConfirm());
        client.accept(snapshot(Phase.LOADING,0,20,0,0,null));
        for(int i=0;i<8;i++)client.accept(snapshot(Phase.LOADING,i+1,20,i+1,i+1,row(i,true,Consequence.NONE)));
        assertEquals(8,client.rows().size());assertFalse(client.canConfirm());assertTrue(client.readyCompanyIds().isEmpty());assertNull(client.confirm());
        client.accept(snapshot(Phase.READY,21,20,20,20,null));assertFalse(client.canConfirm());
        assertFalse(client.enabled());assertEquals("Loading Rally...",client.label());
    }
    @Test public void allRowsEnableOneExplicitConfirmationWithNoRowSelectionOrJoinLatch(){
        for(int i=0;i<20;i++)client.accept(snapshot(i==19?Phase.READY:Phase.LOADING,i+1,20,i+1,i+1,row(i,true,Consequence.NONE)));
        assertTrue(client.canConfirm());assertEquals(20,client.readyCompanyIds().size());
        KOMEPacketCompanyRallyRequest confirm=client.confirm();assertEquals(KOMEPacketCompanyRallyRequest.Action.CONFIRM,confirm.action);
        assertEquals(open.session,confirm.session);assertEquals(token,confirm.token);assertEquals("T100",confirm.destination);
        assertNull(client.confirm());assertFalse(client.canConfirm());assertTrue(client.readyCompanyIds().isEmpty());
        assertFalse(client.accept(snapshot(Phase.READY,21,20,20,20,null)));assertFalse(client.canConfirm());
    }
    @Test public void exactlySixtyFourRowsEnableConfirmOnlyAfterTheLastMatchingProjection(){
        for(int i=0;i<64;i++){
            boolean included=i%2==0;
            assertTrue(client.accept(snapshot(i==63?Phase.READY:Phase.LOADING,i+1,64,i+1,(i+2)/2,row(i,included,Consequence.NONE))));
            assertEquals(i==63,client.canConfirm());
            assertEquals(i==63?"Confirm Rally":"Loading Rally...",client.label());
            assertEquals(i==63,client.enabled());
        }
        assertEquals(64,client.total());assertEquals(64,client.rows().size());assertEquals(32,client.included());
        Set<String> identities=new HashSet<>();for(Entry entry:client.rows())assertTrue(identities.add(entry.company.companyId));
        assertEquals(64,identities.size());assertNotNull(client.confirm());assertNull(client.confirm());
    }
    @Test public void pendingArrivalPreservesServerSummaryAndSeparateOutcomeBesideSuccessfulCompany(){
        Entry first=row(0,true,Consequence.NONE),second=row(1,true,Consequence.NONE);
        client.accept(snapshot(Phase.LOADING,1,2,1,1,first));client.accept(snapshot(Phase.READY,2,2,2,2,second));
        assertNotNull(client.confirm());
        assertTrue(client.accept(new KOMECompanyRallySnapshot(open.session,token,"T100",Phase.EXECUTING,4,2,2,2,1,1,"Rallying companies...",
            new Entry(0,first.company,"gondor",true,Outcome.RALLIED,"Rallied"))));
        String summary="1 companies rallied. 0 skipped. 1 orders awaiting arrival.";
        assertTrue(client.accept(new KOMECompanyRallySnapshot(open.session,token,"T100",Phase.DONE,5,2,2,2,2,1,summary,
            new Entry(1,second.company,"gondor",true,Outcome.PENDING,"Order accepted; arrival not confirmed"))));
        assertEquals(summary,client.message());assertEquals(Outcome.RALLIED,client.rows().get(0).outcome);
        assertEquals(Outcome.PENDING,client.rows().get(1).outcome);assertEquals("Order accepted; arrival not confirmed",client.rows().get(1).reason);
        assertFalse(client.canConfirm());assertNull(client.confirm());assertTrue(client.canRefresh());
    }
    @Test public void authoritativeReadyIdentitiesRemainAvailableWithoutInspectionOrSelection(){
        client.accept(snapshot(Phase.LOADING,1,3,1,0,row(0,false,Consequence.NONE)));
        client.accept(snapshot(Phase.LOADING,2,3,2,1,row(1,true,Consequence.NONE)));
        client.accept(snapshot(Phase.READY,3,3,3,2,row(2,true,Consequence.NONE)));
        assertEquals(Arrays.asList("C1","C2"),client.readyCompanyIds());
        assertEquals(1,client.rows().get(0).index);assertEquals(2,client.rows().get(1).index);assertEquals(0,client.rows().get(2).index);
        assertTrue(client.canConfirm());assertEquals("Confirm Rally",client.label());assertEquals("2 ready / 1 skipped",client.summary());
    }
    @Test public void zeroIncludedAndRefreshSessionDestinationReplacementCannotExecuteOldEvidence(){
        client.accept(snapshot(Phase.READY,1,1,1,0,row(0,false,Consequence.NONE)));assertFalse(client.canConfirm());
        UUID before=open.session;KOMEPacketCompanyRallyRequest newer=client.open("T200");assertNotEquals(before,newer.session);
        assertFalse(client.accept(snapshot(Phase.READY,2,1,1,1,row(0,true,Consequence.NONE))));assertTrue(client.rows().isEmpty());assertFalse(client.canConfirm());
        state.start(new Object());assertFalse(client.active());assertNull(client.confirm());assertTrue(client.readyCompanyIds().isEmpty());
    }
    @Test public void duplicateWrongTokenAndWrongCompanyCannotReplaceEvidence(){
        Entry row=row(0,true,Consequence.NONE);KOMECompanyRallySnapshot ready=snapshot(Phase.READY,1,1,1,1,row);
        assertTrue(client.accept(ready));assertFalse(client.accept(ready));
        assertFalse(client.accept(new KOMECompanyRallySnapshot(open.session,UUID.randomUUID(),"T100",Phase.READY,2,1,1,1,0,0,"Wrong",row)));
        assertTrue(client.canConfirm());
        client.cancel();assertFalse(client.accept(ready));assertFalse(client.active());assertNull(client.confirm());
    }
    @Test public void compactSummaryWarnsAboutHostileMovementWithoutACompanyList(){
        client.accept(snapshot(Phase.LOADING,1,2,1,1,row(0,true,Consequence.EXPECTED_CREATE_CONFLICT)));
        client.accept(snapshot(Phase.READY,2,2,2,1,row(1,false,Consequence.NONE)));
        assertEquals("1 ready / 1 skipped | Will enter battle",client.summary());assertEquals("Confirm Rally",client.label());
        assertFalse(client.summary().contains("C0"));assertEquals(Collections.singletonList("C0"),client.readyCompanyIds());
    }
    @Test public void resultAndRejectionDisarmConfirmationAndPermitRefresh(){
        client.accept(snapshot(Phase.READY,1,1,1,1,row(0,true,Consequence.NONE)));client.confirm();
        assertTrue(client.accept(new KOMECompanyRallySnapshot(open.session,new UUID(0,0),"T100",Phase.ERROR,0,0,0,0,0,0,"Refresh shortly",null)));
        assertFalse(client.canConfirm());assertTrue(client.canRefresh());assertEquals("Refresh shortly",client.message());
        setup();client.accept(snapshot(Phase.READY,1,1,1,1,row(0,true,Consequence.NONE)));client.confirm();
        client.accept(snapshot(Phase.DONE,3,1,1,1,new Entry(0,row(0,true,Consequence.NONE).company,"gondor",true,Outcome.RALLIED,"Rallied")));
        assertFalse(client.canConfirm());assertTrue(client.canRefresh());assertEquals(Outcome.RALLIED,client.rows().get(0).outcome);
    }
    @Test public void loadingZeroErrorAndExpiryCannotConfirmAndFreshPreviewRestartsSafely(){
        assertEquals("Loading Rally...",client.label());assertFalse(client.enabled());assertNull(client.confirm());
        client.accept(snapshot(Phase.READY,1,1,1,0,row(0,false,Consequence.NONE)));
        assertEquals("Rally Here",client.label());assertTrue(client.summary().contains("No companies can arrive now"));assertNull(client.confirm());
        KOMEPacketCompanyRallyRequest next=client.open("T100");assertNotEquals(open.session,next.session);open=next;
        assertTrue(client.accept(snapshot(Phase.READY,1,1,1,1,row(0,true,Consequence.NONE))));assertTrue(client.canConfirm());
        time.set(LIFETIME_MILLIS);assertFalse(client.canConfirm());assertEquals("Rally Here",client.label());assertTrue(client.summary().contains("expired"));
        assertTrue(client.readyCompanyIds().isEmpty());assertTrue(client.canRefresh());assertNull(client.confirm());
        assertFalse(client.accept(snapshot(Phase.READY,2,1,1,1,row(0,true,Consequence.NONE))));assertFalse(client.canConfirm());
        open=client.open("T100");
        assertTrue(client.accept(new KOMECompanyRallySnapshot(open.session,new UUID(0,0),"T100",Phase.ERROR,0,0,0,0,0,0,"Rally supports at most 64 Campaign companies. No partial Rally was prepared.",null)));
        assertFalse(client.canConfirm());assertEquals("Rally Here",client.label());assertTrue(client.summary().contains("No partial"));assertTrue(client.canRefresh());
    }
    @Test public void authoritativeInvalidationDisarmsReadyEvidence(){
        client.accept(snapshot(Phase.READY,1,1,1,1,row(0,true,Consequence.NONE)));assertTrue(client.canConfirm());
        assertTrue(client.accept(new KOMECompanyRallySnapshot(open.session,token,"T100",Phase.ERROR,2,1,1,1,0,0,"Preview invalidated. Refresh.",null)));
        assertFalse(client.canConfirm());assertNull(client.confirm());assertTrue(client.readyCompanyIds().isEmpty());assertEquals("Preview invalidated. Refresh.",client.summary());
    }
}