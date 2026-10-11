package kome.common.command;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kome.common.KOMEAccessFixture;
import net.minecraft.network.NetHandlerPlayServer;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMECompanyMovementSessionsTest {
    @Test public void nonceStorageIsBoundedAndLogoutAndServerReplacementInvalidateIdentity() throws Exception {
        KOMECompanyMovementSessions.clear();
        List<NetHandlerPlayServer> connections=new ArrayList<NetHandlerPlayServer>();
        KOMEAccessFixture.Player player=KOMEAccessFixture.allocate(KOMEAccessFixture.Player.class);
        try {
            for(int i=0;i<KOMECompanyMovementSessions.MAX_CONNECTIONS;i++) {
                NetHandlerPlayServer connection=KOMEAccessFixture.allocate(NetHandlerPlayServer.class);
                connections.add(connection);connection.playerEntity=player;player.playerNetServerHandler=connection;
                UUID identity=KOMECompanyMovementSessions.current(player);assertEquals(identity,KOMECompanyMovementSessions.current(player));
            }
            assertEquals(KOMECompanyMovementSessions.MAX_CONNECTIONS,KOMECompanyMovementSessions.size());
            NetHandlerPlayServer extra=KOMEAccessFixture.allocate(NetHandlerPlayServer.class);
            extra.playerEntity=player;player.playerNetServerHandler=extra;
            try {KOMECompanyMovementSessions.current(player);fail("Capacity must fail closed");}
            catch(KOMECompanyMovementService.MovementDenied denied) {assertEquals("SESSION_LIMIT",denied.code);}
            player.playerNetServerHandler=connections.get(0);UUID before=KOMECompanyMovementSessions.current(player);
            KOMECompanyMovementSessions.forget(player.playerNetServerHandler);
            assertNotEquals(before,KOMECompanyMovementSessions.current(player));
            before=KOMECompanyMovementSessions.current(player);KOMECompanyMovementSessions.clear();
            assertNotEquals(before,KOMECompanyMovementSessions.current(player));
        } finally {KOMECompanyMovementSessions.clear();}
    }
    @Test public void absentOrReboundConnectionCannotIssueConfirmationIdentity() throws Exception {
        KOMEAccessFixture.Player player=KOMEAccessFixture.allocate(KOMEAccessFixture.Player.class);
        for(boolean rebound:new boolean[]{false,true}) {
            if(rebound)player.playerNetServerHandler=KOMEAccessFixture.allocate(NetHandlerPlayServer.class);
            try {KOMECompanyMovementSessions.current(player);fail("No current actor connection");}
            catch(KOMECompanyMovementService.MovementDenied denied) {assertEquals("INVALID_ACTOR",denied.code);}
        }
    }
}
