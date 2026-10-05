package kome.common.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import kome.client.gui.KOMEJoinBattleViewModel;
import kome.common.KOMEAccessFixture;
import kome.common.data.KOMEJoinBattleService;
import kome.common.data.KOMEJoinBattleText;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMEJoinBattlePacketTest {
    @Test public void everyTypedAuthorityReasonHasDeterministicPlayerText() {
        for(KOMEJoinBattleService.Reason reason:KOMEJoinBattleService.Reason.values())
            assertFalse(reason.name(),KOMEJoinBattleText.forReason(reason).trim().isEmpty());
        assertTrue(KOMEJoinBattleText.forReason(KOMEJoinBattleService.Reason.STALE_CONFLICT).contains("Refresh"));
    }
    @Test public void viewRequestUsesConnectionPlayerAndPublishesAuthoritativeProjection() throws Exception {
        KOMEAccessFixture f=new KOMEAccessFixture();
        f.data.setDirty(false);
        KOMEPacketJoinBattleViewResponse response=(KOMEPacketJoinBattleViewResponse)
            new KOMEPacketJoinBattleViewRequest.Handler().onMessage(
                new KOMEPacketJoinBattleViewRequest("T100"),f.context);
        assertEquals(KOMEJoinBattleService.Reason.NO_ACTIVE_CONFLICT,response.reason);
        assertEquals("T100",response.tileId);
        assertTrue(response.companies.isEmpty());
        assertFalse(f.data.isDirty());
        assertTrue(f.data.getConflictService().records().isEmpty());
    }

    @Test public void viewAndSelectionWireAreBoundedAndContainNoClientFactionOrPosition() throws Exception {
        KOMEPacketJoinBattleSelectionRequest request=new KOMEPacketJoinBattleSelectionRequest("T100","CF4",9L,"C2");
        ByteBuf bytes=Unpooled.buffer();
        try{
            request.toBytes(bytes);KOMEPacketJoinBattleSelectionRequest decoded=new KOMEPacketJoinBattleSelectionRequest();decoded.fromBytes(bytes);
            assertEquals("T100",decoded.tileId);assertEquals("CF4",decoded.conflictId);assertEquals(9L,decoded.conflictRevision);assertEquals("C2",decoded.companyId);
            assertEquals(0,bytes.readableBytes());
        }finally{bytes.release();}
        String source=source("src/main/java/kome/common/network/KOMEPacketJoinBattleSelectionRequest.java");
        assertFalse(source.contains("factionId="));assertFalse(source.contains("entityUuid"));
        assertFalse(source.contains("dimension"));assertFalse(source.contains("posX"));
        assertTrue(source.contains("validateSelectedCompany"));
        assertTrue(source.contains("context.getServerHandler().playerEntity"));

        String oversized=new String(new char[65]).replace('\0','X');
        try{new KOMEPacketJoinBattleSelectionRequest("T100","CF4",9L,oversized).toBytes(Unpooled.buffer());fail("oversized company accepted");}
        catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("company"));}
    }

    @Test public void selectionWithoutExactActiveConflictRejectsWithoutMutation() throws Exception {
        KOMEAccessFixture f=new KOMEAccessFixture();f.data.setDirty(false);
        KOMEPacketJoinBattleSelectionResult response=(KOMEPacketJoinBattleSelectionResult)
            new KOMEPacketJoinBattleSelectionRequest.Handler().onMessage(
                new KOMEPacketJoinBattleSelectionRequest("T100","CF1",1L,"C1"),f.context);
        assertEquals(KOMEPacketJoinBattleSelectionResult.Status.REJECTED,response.status);
        assertEquals(KOMEJoinBattleService.Reason.NO_ACTIVE_CONFLICT,response.reason);
        assertFalse(f.data.isDirty());assertTrue(f.data.getConflictService().records().isEmpty());
    }

    @Test public void clientModelBlocksEmptyViewsAndOnlySubmitsServerRows() throws Exception {
        KOMEPacketJoinBattleViewResponse blocked=new KOMEPacketJoinBattleViewResponse();
        blocked.tileId="T100";blocked.reason=KOMEJoinBattleService.Reason.NO_ELIGIBLE_COMPANY;blocked.reasonText="blocked";
        KOMEJoinBattleViewModel model=new KOMEJoinBattleViewModel(blocked);
        model.select("C1");assertFalse(model.canSubmit());assertEquals("",model.selectedCompanyId());

        ByteBuf encoded=Unpooled.buffer();
        try{
            KOMEPopulationWire.writeText(encoded,"T100");KOMEPopulationWire.writeText(encoded,"CF1");encoded.writeLong(3L);
            KOMEPopulationWire.writeText(encoded,"ORDINARY");KOMEPopulationWire.writeText(encoded,"gondor");
            KOMEPopulationWire.writeText(encoded,"ALLOWED");KOMEPopulationWire.writeText(encoded,"allowed");encoded.writeInt(1);
            KOMEPopulationWire.writeText(encoded,"C1");KOMEPopulationWire.writeText(encoded,"First Company");
            KOMEPopulationWire.writeText(encoded,"gondor");encoded.writeInt(2);encoded.writeBoolean(true);
            KOMEPacketJoinBattleViewResponse allowed=new KOMEPacketJoinBattleViewResponse();allowed.fromBytes(encoded);model.accept(allowed);
        }finally{encoded.release();}
        model.select("C999");assertFalse(model.canSubmit());
        model.select("C1");assertTrue(model.canSubmit());assertEquals("C1",model.selectedCompanyId());

        KOMEPacketJoinBattleSelectionResult stale=new KOMEPacketJoinBattleSelectionResult();
        stale.reason=KOMEJoinBattleService.Reason.STALE_CONFLICT;stale.message="The battle changed.";stale.current=blocked;
        model.accept(stale);assertFalse(model.canSubmit());assertEquals("The battle changed.",model.status());
    }

    @Test public void registrationUsesThreadFenceAndSelectionHasNoMutationOrTeleportCalls() throws Exception {
        String registry=source("src/main/java/kome/common/network/KOMEPacketHandler.java");
        assertTrue(registry.contains("KOMEPacketJoinBattleViewRequest.class, 52, Side.SERVER"));
        assertTrue(registry.contains("KOMEPacketJoinBattleViewResponse.class, 53, Side.CLIENT"));
        assertTrue(registry.contains("KOMEPacketJoinBattleSelectionRequest.class, 54, Side.SERVER"));
        assertTrue(registry.contains("KOMEPacketJoinBattleSelectionResult.class, 55, Side.CLIENT"));
        assertTrue(registry.contains("new ServerThreadHandler<KOMEPacketJoinBattleViewRequest>"));
        assertTrue(registry.contains("new ServerThreadHandler<KOMEPacketJoinBattleSelectionRequest>"));
        String action=source("src/main/java/kome/common/network/KOMEPacketJoinBattleSelectionRequest.java");
        for(String forbidden:new String[]{"registerPlayer(","withdrawPlayer(","setPosition","teleport","markDirty","setDirty","audit"})
            assertFalse(forbidden,action.contains(forbidden));
    }

    private static String source(String path)throws Exception{return new String(Files.readAllBytes(Paths.get(path)),StandardCharsets.UTF_8);}
}
