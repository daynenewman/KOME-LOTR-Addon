package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;

public class KOMERelationshipDeathNotificationTest {
    @Test
    public void noticeAndPendingInboxPersistUntilDrained() {
        KOMEPlayerProgression progression = new KOMEPlayerProgression();
        KOMERelationshipDeathNotice notice = new KOMERelationshipDeathNotice(
            "npc@42", KOMERelationshipDeathNotice.Role.LIEGE, "Theoden",
            "was slain by Grima", 1234L);

        assertTrue(progression.queueRelationshipDeathNotice(notice));
        assertFalse("same event/role must not queue twice", progression.queueRelationshipDeathNotice(notice));
        assertEquals(1, progression.pendingRelationshipDeathNoticeCount());

        KOMEPlayerProgression restored = new KOMEPlayerProgression();
        restored.readFromNBT(progression.writeToNBT());
        assertEquals(1, restored.pendingRelationshipDeathNoticeCount());
        List<KOMERelationshipDeathNotice> drained = restored.drainRelationshipDeathNotices();
        assertEquals(1, drained.size());
        assertEquals("npc@42", drained.get(0).eventId);
        assertEquals(KOMERelationshipDeathNotice.Role.LIEGE, drained.get(0).role);
        assertEquals("Theoden", drained.get(0).npcName);
        assertEquals("was slain by Grima", drained.get(0).deathSuffix);
        assertEquals(0, restored.pendingRelationshipDeathNoticeCount());
    }

    @Test
    public void ownerMessagesDistinguishImmediateAndOfflineDelivery() {
        KOMERelationshipDeathNotice notice = new KOMERelationshipDeathNotice(
            "npc@12", KOMERelationshipDeathNotice.Role.MASTER, "Aldor",
            "fell from a high place", 1L);
        String immediate = KOMERelationshipDeathNotificationService.ownerMessage(notice, false);
        String offline = KOMERelationshipDeathNotificationService.ownerMessage(notice, true);

        assertTrue(immediate.contains("Your master"));
        assertTrue(immediate.contains("Aldor"));
        assertTrue(immediate.contains("fell from a high place"));
        assertFalse(immediate.contains("While you were away"));
        assertTrue(offline.contains("While you were away"));
        assertTrue(offline.contains("your master"));
    }

    @Test
    public void killerMessagesAggregatePlayersAndHandleOwnRelationship() {
        List<String> twoPlayers = new ArrayList<String>();
        twoPlayers.add("Elijah");
        twoPlayers.add("Micah");
        String normal = KOMERelationshipDeathNotificationService.killerMessage(
            "Theoden", KOMERelationshipDeathNotice.Role.KING, twoPlayers, false);
        assertTrue(normal.contains("You have slain"));
        assertTrue(normal.contains("Theoden"));
        assertTrue(normal.contains("king of"));
        assertTrue(normal.contains("Elijah"));
        assertTrue(normal.contains("Micah"));

        List<String> otherPlayers = new ArrayList<String>();
        otherPlayers.add("Micah");
        String own = KOMERelationshipDeathNotificationService.killerMessage(
            "Aldor", KOMERelationshipDeathNotice.Role.MASTER, otherPlayers, true);
        assertTrue(own.contains("your own master"));
        assertTrue(own.contains("also master of"));
    }

    @Test
    public void factionResolutionPrefersRememberedPledgeAndFallsBackToRelationship() {
        KOMEWorldData data = new KOMEWorldData("test");
        UUID player = UUID.randomUUID();
        KOMEPlayerProgression progression = data.getProgression(player);
        KOMEProgressionNpcRef master = new KOMEProgressionNpcRef(
            UUID.randomUUID().toString(), "Master", "rohan", 0, 0, 0, 0);
        assertTrue(KOMESerfKnightService.setSerfdomMaster(
            progression.getSerfKnightProgression(), master).success);
        assertEquals("rohan", KOMERelationshipDeathNotificationService.playerFaction(data, player, progression));

        data.lastKnownPlayerFactions.put(player, "gondor");
        assertEquals("gondor", KOMERelationshipDeathNotificationService.playerFaction(data, player, progression));
    }

    @Test
    public void noticeNbtRejectsUnknownRoleWithoutBreakingLoad() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("Event", "npc@1");
        tag.setString("Role", "emperor");
        tag.setString("NPC", "Someone");
        assertNull(KOMERelationshipDeathNotice.readFromNBT(tag));
    }
}
