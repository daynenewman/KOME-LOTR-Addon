package kome.client.gui;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import kome.common.KOMEAccessFixture;
import kome.common.data.KOMEJoinBattleService;
import kome.common.network.KOMEPacketHandler;
import kome.common.network.KOMEPopulationWire;
import kome.common.network.KOMEPacketJoinBattleSelectionRequest;
import kome.common.network.KOMEPacketJoinBattleViewRequest;
import kome.common.network.KOMEPacketJoinBattleViewResponse;
import kome.common.network.KOMEPacketJoinBattleRetreatRequest;
import net.minecraft.client.gui.GuiButton;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/** Exercises the real row/action branches and outgoing client packet seam. */
public class KOMEGuiJoinBattleInteractionTest {
    private SimpleNetworkWrapper previous;
    private RecordingNetwork network;

    @Before public void setup() throws Exception {
        previous=KOMEPacketHandler.network;
        network=KOMEAccessFixture.allocate(RecordingNetwork.class);
        network.sent=new ArrayList<IMessage>();
        KOMEPacketHandler.network=network;
    }

    @After public void cleanup(){KOMEPacketHandler.network=previous;}

    @Test public void battleAndMovementConfirmScreensExplicitlyDoNotPauseIntegratedGameplay() throws Exception {
        assertFalse(new KOMEGuiJoinBattle(view(validToken())).doesGuiPauseGame());
        assertFalse(new KOMEGuiCompanyMoveConfirm(null).doesGuiPauseGame());
        assertEquals(boolean.class,KOMEGuiJoinBattle.class.getDeclaredMethod("doesGuiPauseGame").getReturnType());
        assertEquals(boolean.class,KOMEGuiCompanyMoveConfirm.class.getDeclaredMethod("doesGuiPauseGame").getReturnType());
    }
    @Test public void selectingC4AndClickingJoinSendsExactlyOneFiveFieldRequest() throws Exception {
        KOMEGuiJoinBattle screen=new KOMEGuiJoinBattle(view(validToken()));screen.initGui();
        screen.actionPerformed(button(screen,101));
        assertEquals("C4",screen.viewModel().selectedCompanyId());
        assertTrue(screen.viewModel().canSubmit());
        assertTrue(button(screen,1).enabled);

        screen.actionPerformed(button(screen,1));

        assertEquals(1,network.sent.size());
        KOMEPacketJoinBattleSelectionRequest request=
            (KOMEPacketJoinBattleSelectionRequest)network.sent.get(0);
        assertEquals("T402",request.tileId);assertEquals("CF2",request.conflictId);
        assertEquals(2L,request.conflictRevision);assertEquals("C4",request.companyId);
        assertEquals(validToken(),request.actionToken);
    }

    @Test public void tokenlessAllowedSelectionRefreshesOnceWithVisibleStatus() throws Exception {
        KOMEGuiJoinBattle screen=new KOMEGuiJoinBattle(view(""));screen.initGui();
        screen.actionPerformed(button(screen,101));
        assertEquals("C4",screen.viewModel().selectedCompanyId());
        assertTrue(screen.viewModel().canAttemptSubmit());assertFalse(screen.viewModel().canSubmit());
        assertTrue("Missing token must not produce an unclickable silent action",button(screen,1).enabled);

        screen.actionPerformed(button(screen,1));
        assertEquals("Battle view expired. Refreshing...",screen.viewModel().status());
        assertEquals(1,network.sent.size());
        assertTrue(network.sent.get(0) instanceof KOMEPacketJoinBattleViewRequest);
        assertEquals("T402",((KOMEPacketJoinBattleViewRequest)network.sent.get(0)).tileId);

        screen.actionPerformed(button(screen,1));
        assertEquals("Only one automatic refresh may be outstanding",1,network.sent.size());

        screen.acceptView(view(validToken()));
        assertEquals("Replacement projection clears stale company selection","",
            screen.viewModel().selectedCompanyId());
        assertFalse(screen.viewModel().canAttemptSubmit());
    }

    @Test public void malformedTokensNeverEnableSubmission() throws Exception {
        for(String token:new String[]{"x","0123456789abcdef0123456789abcde",
                "0123456789abcdef0123456789abcdeg","ABCDEFABCDEFABCDEFABCDEFABCDEFAB"}){
            KOMEGuiJoinBattle screen=new KOMEGuiJoinBattle(view(token));screen.initGui();
            screen.actionPerformed(button(screen,101));
            assertFalse(token,screen.viewModel().canSubmit());
        }
    }

    @Test public void deployedViewRequiresConfirmationAndSendsExactConflictOnly() throws Exception {
        KOMEPacketJoinBattleViewResponse deployed=view(validToken());
        deployed.currentDeploymentTile="T375";deployed.currentDeploymentConflict="CF7";
        deployed.currentDeploymentState="IN_BATTLE";deployed.formalRetreatAvailable=true;
        deployed.formalRetreatReason="Formal retreat available.";
        deployed.formalRetreatCompanyIds.add("C3");
        KOMEGuiJoinBattle screen=new KOMEGuiJoinBattle(deployed);screen.initGui();
        assertFalse("Existing deployment disables another Join action",button(screen,1).enabled);
        assertFalse("Accepted/deployed state is not another selectable Join action",button(screen,100).enabled);
        assertTrue(button(screen,6).enabled);
        assertTrue("Deployment/retreat area must sit below paging controls",
            button(screen,6).yPosition>button(screen,5).yPosition+button(screen,5).height+40);
        screen.actionPerformed(button(screen,6));
        assertTrue(screen.viewModel().status().contains("Confirm"));
        assertTrue(network.sent.isEmpty());
        screen.actionPerformed(button(screen,6));
        assertEquals(1,network.sent.size());
        assertTrue(network.sent.get(0) instanceof KOMEPacketJoinBattleRetreatRequest);
        assertEquals("CF2",((KOMEPacketJoinBattleRetreatRequest)network.sent.get(0)).conflictId);
    }

    @Test public void remoteRetreatButtonDoesNotRequirePhysicalDeployment() throws Exception{
        KOMEPacketJoinBattleViewResponse remote=view(validToken());
        remote.formalRetreatAvailable=true;remote.formalRetreatCompanyIds.add("C4");
        KOMEGuiJoinBattle screen=new KOMEGuiJoinBattle(remote);screen.initGui();
        assertTrue(button(screen,6).enabled);
        screen.actionPerformed(button(screen,6));screen.actionPerformed(button(screen,6));
        assertEquals("CF2",((KOMEPacketJoinBattleRetreatRequest)network.sent.get(0)).conflictId);
    }

    @Test public void movementPreferenceUsesOnlyServerSelectableRowsAndTheExistingJoinAction() throws Exception {
        KOMEGuiJoinBattle screen=new KOMEGuiJoinBattle(view(validToken()),"C4");screen.initGui();
        assertEquals("C4",screen.viewModel().selectedCompanyId());assertTrue(network.sent.isEmpty());
        assertTrue(screen.beginPreferredEntry());assertFalse(screen.beginPreferredEntry());assertEquals(1,network.sent.size());
        assertEquals("C4",((KOMEPacketJoinBattleSelectionRequest)network.sent.get(0)).companyId);
        assertEquals("",new KOMEGuiJoinBattle(view(validToken()),"C999").viewModel().selectedCompanyId());
        KOMEPacketJoinBattleViewResponse denied=view(validToken());denied.reason=KOMEJoinBattleService.Reason.WITHDRAWN;denied.reasonText="You have withdrawn from this conflict.";
        KOMEGuiJoinBattle blocked=new KOMEGuiJoinBattle(denied,"C4");assertEquals("",blocked.viewModel().selectedCompanyId());assertTrue(blocked.viewModel().status().contains("Could not join"));
        assertFalse(blocked.beginPreferredEntry());assertFalse(new KOMEGuiJoinBattle(view(validToken()),"C999").beginPreferredEntry());
        KOMEPacketJoinBattleViewResponse deployed=view(validToken());deployed.currentDeploymentConflict="CF2";
        assertEquals("",new KOMEGuiJoinBattle(deployed,"C4").viewModel().selectedCompanyId());
    }
    @Test public void existingAuthorityDenialIsDisplayedAfterMovementWithoutAnotherMovementRequest() throws Exception {
        Display display=new Display();KOMEGuiJoinBattle screen=new KOMEGuiJoinBattle(view(validToken()),"C4",display);display.current=screen;screen.initGui();screen.actionPerformed(button(screen,1));
        KOMEAccessFixture f=new KOMEAccessFixture();
        kome.common.network.KOMEPacketJoinBattleSelectionResult denial=(kome.common.network.KOMEPacketJoinBattleSelectionResult)
            new KOMEPacketJoinBattleSelectionRequest.Handler().onMessage((KOMEPacketJoinBattleSelectionRequest)network.sent.get(0),f.context);
        screen.acceptSelection(denial);assertEquals(denial.message,screen.viewModel().status());assertFalse(screen.viewModel().canSubmit());
        assertTrue(KOMEGuiJoinBattle.class.getDeclaredField("afterMovement").getType()==boolean.class);
        assertEquals(1,network.sent.size());assertTrue(f.data.armyMovements.isEmpty());
    }
    @Test public void standaloneJoinStillWaitsForExplicitPlayerClickAndDoesNotDisplayStableIds() throws Exception {
        KOMEGuiJoinBattle screen=new KOMEGuiJoinBattle(view(validToken()));screen.initGui();
        screen.actionPerformed(button(screen,101));assertFalse(screen.beginPreferredEntry());assertTrue(network.sent.isEmpty());
        assertFalse(button(screen,100).displayString.contains("C3"));assertFalse(button(screen,101).displayString.contains("C4"));
        screen.actionPerformed(button(screen,1));assertEquals(1,network.sent.size());assertEquals("C4",((KOMEPacketJoinBattleSelectionRequest)network.sent.get(0)).companyId);
    }
    @Test public void preferredFallbackExplainsEachGuardAndNeverTreatsOtherCompaniesAsAnchorChoices() throws Exception {
        KOMEGuiJoinBattle legal=new KOMEGuiJoinBattle(view(validToken()),"C4");
        assertEquals("",legal.preferredEntryFallback());assertTrue(legal.beginPreferredEntry());
        assertEquals(1,network.sent.size());assertEquals("C4",((KOMEPacketJoinBattleSelectionRequest)network.sent.get(0)).companyId);
        assertTrue(new KOMEGuiJoinBattle(view(validToken()),"MISSING").preferredEntryFallback().contains("not selectable"));
        KOMEGuiJoinBattle tokenless=new KOMEGuiJoinBattle(view(""),"C4");
        assertTrue(tokenless.preferredEntryFallback().contains("expired"));assertFalse(tokenless.beginPreferredEntry());
        assertTrue(tokenless.status().contains("Could not join"));
        KOMEPacketJoinBattleViewResponse duplicate=view(validToken());duplicate.companies.add(duplicate.companies.get(1));
        assertTrue(new KOMEGuiJoinBattle(duplicate,"C4").preferredEntryFallback().contains("ambiguous"));
        KOMEPacketJoinBattleViewResponse ended=view(validToken());ended.conflictState=kome.common.data.KOMEConflictRecord.State.ENDED;
        assertTrue(new KOMEGuiJoinBattle(ended,"C4").preferredEntryFallback().contains("no longer active"));
        assertEquals(1,network.sent.size());
    }
    @Test public void physicalPlacementDenialAfterDirectSubmissionRemainsAuthoritativeAndVisible() throws Exception {
        Display display=new Display();KOMEGuiJoinBattle screen=startJoin(display,true);
        kome.common.network.KOMEPacketJoinBattleSelectionResult denial=selection(false);
        denial.reason=KOMEJoinBattleService.Reason.SAFE_POSITION_UNAVAILABLE;
        denial.message="No safe standing position is available.";
        assertTrue(screen.acceptSelection(denial));assertEquals(denial.message,screen.status());
        assertEquals(0,display.closes);assertTrue(display.messages.isEmpty());assertEquals(1,network.sent.size());
        assertFalse(screen.viewModel().canSubmit());
    }    private static GuiButton button(KOMEGuiJoinBattle screen,int id) throws Exception {
        java.lang.reflect.Field field=net.minecraft.client.gui.GuiScreen.class.getDeclaredField("buttonList");
        field.setAccessible(true);
        for(Object value:(List)field.get(screen))if(((GuiButton)value).id==id)return (GuiButton)value;
        throw new AssertionError("Missing button "+id);
    }
    @Test public void deployedClosesOnceAndNotifiesOnlyAfterActualSuccess() throws Exception {
        Display display=new Display();KOMEGuiJoinBattle screen=startJoin(display,false);
        screen.actionPerformed(button(screen,1));assertEquals(1,network.sent.size());assertEquals(0,display.closes);
        kome.common.network.KOMEPacketJoinBattleSelectionResult result=selection(true);
        screen.acceptSelection(result);screen.acceptSelection(result);screen.actionPerformed(new KOMEGuiButton(1,0,0,1,1,"Join"));
        assertEquals(1,display.closes);assertEquals(1,display.messages.size());assertEquals(result.message,display.messages.get(0));
        assertEquals(1,network.sent.size());
    }
    @Test public void everyJoinDenialKeepsGuiAndReasonIncludingAfterSuccessfulMovement() throws Exception {
        for(boolean afterMovement:new boolean[]{false,true})for(KOMEJoinBattleService.Reason reason:KOMEJoinBattleService.Reason.values()) {
            network.sent.clear();Display display=new Display();KOMEGuiJoinBattle screen=startJoin(display,afterMovement);
            kome.common.network.KOMEPacketJoinBattleSelectionResult result=selection(false);result.reason=reason;result.message="Denied: "+reason;
            screen.acceptSelection(result);assertEquals(0,display.closes);assertTrue(display.messages.isEmpty());
            assertEquals(result.message,screen.viewModel().status());assertEquals(1,network.sent.size());
            java.lang.reflect.Field moved=KOMEGuiJoinBattle.class.getDeclaredField("afterMovement");moved.setAccessible(true);
            assertEquals(afterMovement,moved.getBoolean(screen));
        }
    }
    @Test public void entryPendingWaitsForMatchingReceiptRecoverySuccess() throws Exception {
        Display display=new Display();KOMEGuiJoinBattle screen=startJoin(display,false);
        kome.common.network.KOMEPacketJoinBattleSelectionResult pending=selection(true);
        pending.status=kome.common.network.KOMEPacketJoinBattleSelectionResult.Status.ENTRY_PENDING;
        screen.acceptSelection(pending);assertEquals(0,display.closes);
        kome.common.network.KOMEPacketJoinBattleSelectionResult success=selection(true);success.correlation=new java.util.UUID(0,0);
        success.receiptId="OTHER";screen.acceptSelection(success);assertEquals(0,display.closes);
        success.receiptId=pending.receiptId;screen.acceptSelection(success);assertEquals(1,display.closes);
    }
    @Test public void successNotificationCannotCloseScreenThatReplacedOwnerDuringNotification() throws Exception {
        final Object replacement=new Object();
        final Object[] current={null};final int[] closes={0};
        KOMEGuiJoinBattle.Presentation display=new KOMEGuiJoinBattle.Presentation(){
            public boolean isCurrent(KOMEGuiJoinBattle screen){return current[0]==screen;}
            public void notifySuccess(String message){current[0]=replacement;}
            public void close(KOMEGuiJoinBattle screen){closes[0]++;current[0]=null;}
        };
        KOMEGuiJoinBattle screen=new KOMEGuiJoinBattle(view(validToken()),display);current[0]=screen;screen.initGui();
        screen.actionPerformed(button(screen,101));screen.actionPerformed(button(screen,1));
        assertTrue(screen.acceptSelection(selection(true)));assertEquals(0,closes[0]);assertSame(replacement,current[0]);
    }
    @Test public void successfulRetreatClosesOnceAfterResultAndAllOtherCodesKeepReason() throws Exception {
        for(kome.common.data.KOMEFormalRetreatService.Code code:kome.common.data.KOMEFormalRetreatService.Code.values()) {
            network.sent.clear();Display display=new Display();KOMEPacketJoinBattleViewResponse view=view(validToken());
            view.formalRetreatAvailable=true;view.formalRetreatCompanyIds.add("C4");
            KOMEGuiJoinBattle screen=new KOMEGuiJoinBattle(view,display);display.current=screen;screen.initGui();
            screen.actionPerformed(button(screen,6));screen.actionPerformed(button(screen,6));
            screen.actionPerformed(new KOMEGuiButton(6,0,0,1,1,"Retreat"));assertEquals(1,network.sent.size());assertEquals(0,display.closes);
            kome.common.network.KOMEPacketJoinBattleRetreatResult result=new kome.common.network.KOMEPacketJoinBattleRetreatResult();
            result.correlation=((KOMEPacketJoinBattleRetreatRequest)network.sent.get(0)).correlation;
            result.tileId="T402";result.conflictId="CF2";result.code=code;result.message="Retreat: "+code;
            screen.acceptRetreat(result);screen.acceptRetreat(result);
            assertEquals(code==kome.common.data.KOMEFormalRetreatService.Code.RETREATED?1:0,display.closes);
            assertEquals(display.closes,display.messages.size());assertEquals(result.message,screen.viewModel().status());
        }
    }
    @Test public void lateSuccessCannotCloseReplacementEvenWithItsOwnPendingAction() throws Exception {
        Display display=new Display();KOMEGuiJoinBattle old=startJoin(display,false);
        kome.common.network.KOMEPacketJoinBattleSelectionResult late=selection(true);
        old.onGuiClosed();KOMEGuiJoinBattle replacement=startJoin(display,false);
        replacement.acceptSelection(late);old.acceptSelection(late);assertEquals(0,display.closes);
        kome.common.network.KOMEPacketJoinBattleSelectionResult fresh=selection(true);replacement.acceptSelection(fresh);assertEquals(1,display.closes);
    }
    @Test public void replacementViewInvalidatesEarlierActionEvenOnSameScreenInstance() throws Exception {
        Display display=new Display();KOMEGuiJoinBattle screen=startJoin(display,false);
        kome.common.network.KOMEPacketJoinBattleSelectionResult late=selection(true);
        screen.acceptView(view(validToken()));screen.initGui();screen.actionPerformed(button(screen,101));screen.actionPerformed(button(screen,1));
        screen.acceptSelection(late);assertEquals(0,display.closes);
        screen.acceptSelection(selection(true));assertEquals(1,display.closes);
    }
    @Test public void malformedOrMismatchedSuccessCannotCloseAndInactiveScreenCannotCloseAnotherGui() throws Exception {
        Display display=new Display();KOMEGuiJoinBattle screen=startJoin(display,false);
        kome.common.network.KOMEPacketJoinBattleSelectionResult result=selection(true);
        result.receiptId="";screen.acceptSelection(result);assertEquals(0,display.closes);
        result=selection(true);result.current.currentDeploymentConflict="OTHER";screen.acceptSelection(result);assertEquals(0,display.closes);
        result=selection(true);result.reason=KOMEJoinBattleService.Reason.INVALID_REQUEST;screen.acceptSelection(result);assertEquals(0,display.closes);
        result=selection(true);result.message="";screen.acceptSelection(result);assertEquals(0,display.closes);
        display.current=new Object();screen.acceptSelection(selection(true));assertEquals(0,display.closes);assertTrue(display.messages.isEmpty());
    }
    @Test public void staleRetreatCannotCloseReplacementOrResurrectClosedScreen() throws Exception {
        Display display=new Display();KOMEPacketJoinBattleViewResponse view=view(validToken());
        view.formalRetreatAvailable=true;view.formalRetreatCompanyIds.add("C4");
        KOMEGuiJoinBattle old=new KOMEGuiJoinBattle(view,display);display.current=old;old.initGui();
        old.actionPerformed(button(old,6));old.actionPerformed(button(old,6));
        kome.common.network.KOMEPacketJoinBattleRetreatResult late=new kome.common.network.KOMEPacketJoinBattleRetreatResult();
        late.correlation=((KOMEPacketJoinBattleRetreatRequest)network.sent.get(0)).correlation;
        late.tileId="T402";late.conflictId="CF2";late.code=kome.common.data.KOMEFormalRetreatService.Code.RETREATED;late.message="Retreated";
        old.onGuiClosed();KOMEGuiJoinBattle replacement=new KOMEGuiJoinBattle(view,display);display.current=replacement;replacement.initGui();
        replacement.actionPerformed(button(replacement,6));replacement.actionPerformed(button(replacement,6));
        replacement.acceptRetreat(late);old.acceptRetreat(late);assertEquals(0,display.closes);
        late.correlation=((KOMEPacketJoinBattleRetreatRequest)network.sent.get(1)).correlation;
        late.tileId="OTHER";replacement.acceptRetreat(late);assertEquals(0,display.closes);
        late.tileId="T402";replacement.acceptRetreat(late);assertEquals(1,display.closes);
    }
    private KOMEGuiJoinBattle startJoin(Display display,boolean moved) throws Exception {
        KOMEGuiJoinBattle screen=moved?new KOMEGuiJoinBattle(view(validToken()),"C4",display):new KOMEGuiJoinBattle(view(validToken()),display);
        display.current=screen;screen.initGui();
        if(moved)assertTrue(screen.beginPreferredEntry());else {screen.actionPerformed(button(screen,101));screen.actionPerformed(button(screen,1));}
        return screen;
    }
    private kome.common.network.KOMEPacketJoinBattleSelectionResult selection(boolean success) {
        kome.common.network.KOMEPacketJoinBattleSelectionResult result=new kome.common.network.KOMEPacketJoinBattleSelectionResult();
        result.correlation=((KOMEPacketJoinBattleSelectionRequest)network.sent.get(network.sent.size()-1)).correlation;
        result.status=success?kome.common.network.KOMEPacketJoinBattleSelectionResult.Status.DEPLOYED:kome.common.network.KOMEPacketJoinBattleSelectionResult.Status.REJECTED;
        result.reason=success?KOMEJoinBattleService.Reason.ALLOWED:KOMEJoinBattleService.Reason.INVALID_REQUEST;
        result.message=success?"Deployed":"Denied";result.receiptId="JB1";result.current=view(validToken());
        if(success){result.current.currentDeploymentTile="T402";result.current.currentDeploymentConflict="CF2";result.current.currentDeploymentState="IN_BATTLE";}
        return result;
    }
    private static final class Display implements KOMEGuiJoinBattle.Presentation {
        Object current;int closes;List<String> messages=new ArrayList<String>();
        public boolean isCurrent(KOMEGuiJoinBattle screen){return current==screen;}
        public void notifySuccess(String message){messages.add(message);}
        public void close(KOMEGuiJoinBattle screen){closes++;current=null;}
    }
    private static String validToken(){return "0123456789abcdef0123456789abcdef";}

    private static KOMEPacketJoinBattleViewResponse view(String token){
        ByteBuf encoded=Unpooled.buffer();
        try{
            encoded.writeLong(0);encoded.writeLong(0);
            KOMEPopulationWire.writeText(encoded,"T402");
            KOMEPopulationWire.writeText(encoded,"CF2");encoded.writeLong(2L);
            KOMEPopulationWire.writeText(encoded,"ORDINARY");
            KOMEPopulationWire.writeText(encoded,"mordor");
            KOMEPopulationWire.writeText(encoded,KOMEJoinBattleService.Reason.ALLOWED.name());
            KOMEPopulationWire.writeText(encoded,"You may join this battle.");
            KOMEPopulationWire.writeText(encoded,token);encoded.writeInt(2);
            row(encoded,"C3",2);row(encoded,"C4",1);
            KOMEPopulationWire.writeText(encoded,"");KOMEPopulationWire.writeText(encoded,"");
            KOMEPopulationWire.writeText(encoded,"");encoded.writeBoolean(false);
            KOMEPopulationWire.writeText(encoded,"");encoded.writeInt(0);
            KOMEPacketJoinBattleViewResponse result=new KOMEPacketJoinBattleViewResponse();
            result.fromBytes(encoded);return result;
        }finally{encoded.release();}
    }

    private static void row(ByteBuf encoded,String id,int combat){
        KOMEPopulationWire.writeText(encoded,id);
        KOMEPopulationWire.writeText(encoded,"Minas Morgul Company");
        KOMEPopulationWire.writeText(encoded,"mordor");
        encoded.writeInt(combat);encoded.writeBoolean(true);
    }

    public static final class RecordingNetwork extends SimpleNetworkWrapper{
        List<IMessage> sent;
        private RecordingNetwork(){super("unused");}
        @Override public void sendToServer(IMessage message){sent.add(message);}
    }
}
