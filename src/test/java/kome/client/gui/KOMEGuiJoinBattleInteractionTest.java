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

    private static GuiButton button(KOMEGuiJoinBattle screen,int id) throws Exception {
        java.lang.reflect.Field field=net.minecraft.client.gui.GuiScreen.class.getDeclaredField("buttonList");
        field.setAccessible(true);
        for(Object value:(List)field.get(screen))if(((GuiButton)value).id==id)return (GuiButton)value;
        throw new AssertionError("Missing button "+id);
    }
    private static String validToken(){return "0123456789abcdef0123456789abcdef";}

    private static KOMEPacketJoinBattleViewResponse view(String token){
        ByteBuf encoded=Unpooled.buffer();
        try{
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
