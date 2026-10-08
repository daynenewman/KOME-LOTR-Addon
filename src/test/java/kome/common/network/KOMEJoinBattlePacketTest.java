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
        KOMEPacketJoinBattleSelectionRequest request=new KOMEPacketJoinBattleSelectionRequest("T100","CF4",9L,"C2","token");
        ByteBuf bytes=Unpooled.buffer();
        try{
            request.toBytes(bytes);KOMEPacketJoinBattleSelectionRequest decoded=new KOMEPacketJoinBattleSelectionRequest();decoded.fromBytes(bytes);
            assertEquals("T100",decoded.tileId);assertEquals("CF4",decoded.conflictId);assertEquals(9L,decoded.conflictRevision);assertEquals("C2",decoded.companyId);
            assertEquals("token",decoded.actionToken);
            assertEquals(0,bytes.readableBytes());
        }finally{bytes.release();}
        String source=source("src/main/java/kome/common/network/KOMEPacketJoinBattleSelectionRequest.java");
        assertFalse(source.contains("factionId="));assertFalse(source.contains("entityUuid"));
        assertFalse(source.contains("dimension"));assertFalse(source.contains("posX"));
        assertTrue(source.contains("KOMEJoinBattleEntryService.INSTANCE.enter"));
        assertTrue(source.contains("context.getServerHandler().playerEntity"));

        String oversized=new String(new char[65]).replace('\0','X');
        try{new KOMEPacketJoinBattleSelectionRequest("T100","CF4",9L,oversized,"token").toBytes(Unpooled.buffer());fail("oversized company accepted");}
        catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("company"));}
        String oversizedToken=new String(new char[129]).replace('\0','T');
        try{new KOMEPacketJoinBattleSelectionRequest("T100","CF4",9L,"C2",oversizedToken).toBytes(Unpooled.buffer());fail("oversized token accepted");}
        catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("action token"));}
    }

    @Test public void selectionWithoutExactActiveConflictRejectsWithoutMutation() throws Exception {
        KOMEAccessFixture f=new KOMEAccessFixture();f.data.setDirty(false);
        KOMEPacketJoinBattleSelectionResult response=(KOMEPacketJoinBattleSelectionResult)
            new KOMEPacketJoinBattleSelectionRequest.Handler().onMessage(
                new KOMEPacketJoinBattleSelectionRequest("T100","CF1",1L,"C1","missing-token"),f.context);
        assertEquals(KOMEPacketJoinBattleSelectionResult.Status.REJECTED,response.status);
        assertEquals(KOMEJoinBattleService.Reason.INVALID_ACTION_TOKEN,response.reason);
        assertFalse(f.data.isDirty());assertTrue(f.data.getConflictService().records().isEmpty());
    }

    @Test public void viewWirePreservesValidTokenAndAllowedTokenlessFactoryFailsClosed() throws Exception {
        KOMEPacketJoinBattleViewResponse source=new KOMEPacketJoinBattleViewResponse();
        source.tileId="T402";source.conflictId="CF2";source.conflictRevision=2L;
        source.conflictState=kome.common.data.KOMEConflictRecord.State.ORDINARY;
        source.playerFactionId="mordor";source.reason=KOMEJoinBattleService.Reason.ALLOWED;
        source.reasonText="You may join this battle.";
        source.actionToken="0123456789abcdef0123456789abcdef";
        ByteBuf bytes=Unpooled.buffer();
        try{
            source.toBytes(bytes);KOMEPacketJoinBattleViewResponse decoded=new KOMEPacketJoinBattleViewResponse();
            decoded.fromBytes(bytes);assertEquals(source.actionToken,decoded.actionToken);
            KOMEJoinBattleViewModel model=new KOMEJoinBattleViewModel(decoded);
            assertEquals(source.actionToken,model.view().actionToken);
        }finally{bytes.release();}
        assertFalse(kome.common.data.KOMEJoinBattleActionTokenService.isUsableToken(""));
        String factory=source("src/main/java/kome/common/network/KOMEPacketJoinBattleViewResponse.java");
        assertTrue(factory.contains("Allowed Join Battle view requires a server action token"));
        assertTrue(factory.contains("Allowed Join Battle view requires a valid action token"));
    }

    @Test public void entryResultWirePreservesTypedStatusReceiptAndRetryToken() {
        KOMEPacketJoinBattleSelectionResult source=new KOMEPacketJoinBattleSelectionResult();
        source.status=KOMEPacketJoinBattleSelectionResult.Status.ENTRY_PENDING;
        source.reason=KOMEJoinBattleService.Reason.COMPANY_LOCATION_UNAVAILABLE;
        source.message="Company is not loaded.";source.receiptId="JB7";
        source.current.tileId="T100";source.current.conflictId="CF4";source.current.conflictRevision=9L;
        source.current.conflictState=kome.common.data.KOMEConflictRecord.State.ORDINARY;
        source.current.playerFactionId="gondor";source.current.reason=KOMEJoinBattleService.Reason.ALLOWED;
        source.current.reasonText="allowed";source.current.actionToken="retry-token";
        ByteBuf bytes=Unpooled.buffer();
        try{
            source.toBytes(bytes);KOMEPacketJoinBattleSelectionResult decoded=new KOMEPacketJoinBattleSelectionResult();
            decoded.fromBytes(bytes);assertEquals(source.status,decoded.status);assertEquals("JB7",decoded.receiptId);
            assertEquals("retry-token",decoded.current.actionToken);assertEquals(0,bytes.readableBytes());
        }finally{bytes.release();}
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
            KOMEPopulationWire.writeText(encoded,"ALLOWED");KOMEPopulationWire.writeText(encoded,"allowed");
            KOMEPopulationWire.writeText(encoded,"0123456789abcdef0123456789abcdef");encoded.writeInt(1);
            KOMEPopulationWire.writeText(encoded,"C1");KOMEPopulationWire.writeText(encoded,"First Company");
            KOMEPopulationWire.writeText(encoded,"gondor");encoded.writeInt(2);encoded.writeBoolean(true);
            KOMEPopulationWire.writeText(encoded,"");KOMEPopulationWire.writeText(encoded,"");
            KOMEPopulationWire.writeText(encoded,"");encoded.writeBoolean(false);
            KOMEPopulationWire.writeText(encoded,"");encoded.writeInt(0);
            KOMEPacketJoinBattleViewResponse allowed=new KOMEPacketJoinBattleViewResponse();allowed.fromBytes(encoded);model.accept(allowed);
        }finally{encoded.release();}
        model.select("C999");assertFalse(model.canSubmit());
        model.select("C1");assertTrue(model.canSubmit());assertEquals("C1",model.selectedCompanyId());

        KOMEPacketJoinBattleSelectionResult stale=new KOMEPacketJoinBattleSelectionResult();
        stale.reason=KOMEJoinBattleService.Reason.STALE_CONFLICT;stale.message="The battle changed.";stale.current=blocked;
        model.accept(stale);assertFalse(model.canSubmit());assertEquals("The battle changed.",model.status());
    }

    @Test public void registrationUsesThreadFenceAndPacketDelegatesWithoutClientAuthority() throws Exception {
        String registry=source("src/main/java/kome/common/network/KOMEPacketHandler.java");
        assertTrue(registry.contains("KOMEPacketJoinBattleViewRequest.class, 53, Side.SERVER"));
        assertTrue(registry.contains("KOMEPacketJoinBattleViewResponse.class, 54, Side.CLIENT"));
        assertTrue(registry.contains("KOMEPacketJoinBattleSelectionRequest.class, 55, Side.SERVER"));
        assertTrue(registry.contains("KOMEPacketJoinBattleSelectionResult.class, 56, Side.CLIENT"));
        assertTrue(registry.contains("KOMEPacketJoinBattleRetreatRequest.class, 57, Side.SERVER"));
        assertTrue(registry.contains("KOMEPacketJoinBattleRetreatResult.class, 58, Side.CLIENT"));
        assertTrue(registry.contains("new ServerThreadHandler<KOMEPacketJoinBattleViewRequest>"));
        assertTrue(registry.contains("new ServerThreadHandler<KOMEPacketJoinBattleSelectionRequest>"));
        assertTrue(registry.contains("new ServerThreadHandler<KOMEPacketJoinBattleRetreatRequest>"));
        String action=source("src/main/java/kome/common/network/KOMEPacketJoinBattleSelectionRequest.java");
        for(String forbidden:new String[]{"withdrawPlayer(","setPosition","teleport","audit","factionId","entityUuid","dimension"})
            assertFalse(forbidden,action.contains(forbidden));
        String view=source("src/main/java/kome/common/network/KOMEPacketJoinBattleViewRequest.java");
        assertTrue(view.contains("KOMEPacketJoinBattleViewResponse.forPlayer"));
        String response=source("src/main/java/kome/common/network/KOMEPacketJoinBattleViewResponse.java");
        assertTrue(response.contains("KOMEJoinBattleActionTokenService.INSTANCE.issue"));
        assertTrue(response.contains("currentDeploymentState=\"JOINING\""));
        assertTrue(response.contains("currentDeploymentState=\"IN_BATTLE\""));
        assertTrue(response.contains("currentDeploymentState=\"RETURNING\""));
        assertFalse("Normal player projection must not expose raw receipt enum names",
            response.contains("open.getState().name()"));
        String physical=source("src/main/java/kome/common/data/KOMEJoinBattlePhysicalAccess.java");
        for(String forbidden:new String[]{"LOTRPacketFastTravel","setTargetFTWaypoint","setTimeSinceFT","KOMEWaypointAccessService"})
            assertFalse(forbidden,physical.contains(forbidden));
    }

    @Test public void formalRetreatRequestCarriesOnlyExactConflictNotCompanyAuthority() {
        ByteBuf bytes=Unpooled.buffer();
        try{
            new KOMEPacketJoinBattleRetreatRequest("CF2").toBytes(bytes);
            KOMEPacketJoinBattleRetreatRequest decoded=new KOMEPacketJoinBattleRetreatRequest();
            decoded.fromBytes(bytes);assertEquals("CF2",decoded.conflictId);
        }finally{bytes.release();}
    }

    private static String source(String path)throws Exception{return new String(Files.readAllBytes(Paths.get(path)),StandardCharsets.UTF_8);}
}
