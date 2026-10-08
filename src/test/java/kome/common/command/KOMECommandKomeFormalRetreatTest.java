package kome.common.command;

import kome.common.KOMEAccessFixture;
import org.junit.Test;

import static org.junit.Assert.*;

public class KOMECommandKomeFormalRetreatTest {
    @Test public void publicPlayerNeedsNoIdsOrOperatorPermission() throws Exception {
        KOMEAccessFixture fixture=new KOMEAccessFixture();
        fixture.player.operator=false;
        new KOMECommandKome().processCommand(fixture.player,
            new String[]{"battle","retreat"});
        assertEquals(1,fixture.player.messages.size());
        assertEquals("You have no personally commanded committed forces available to retreat.",fixture.player.messages.get(0));
        assertTrue(fixture.data.getJoinBattleDeploymentReceipts().records().isEmpty());
        assertTrue(fixture.data.getConflictService().records().isEmpty());
    }
}
