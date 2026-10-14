package kome.common.data;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import kome.common.KOMEAccessFixture;
import kome.common.network.KOMEPacketProgressionData;
import kome.common.network.KOMEPopulationWire;
import lotr.common.fac.LOTRFaction;
import org.junit.After;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import static org.junit.Assert.*;

public class KOMEProgressionCleanupProjectionTest {
    @After public void cleanup() { KOMEClientData.INSTANCE.resetClientState(); KOMEProgressionAutoCompleter.resetSession(); }

    @Test public void machineRankAndPermissionFlagsRoundTripAndPublishTogether() throws Exception {
        try (KOMEProgressionFollowupTest.Session s = new KOMEProgressionFollowupTest.Session()) {
            s.p.setCanonicalRank(KOMEProgressionRank.KNIGHT); s.p.grant("baseline.fast_travel");
            s.p.grant("baseline.faction_armor");
            KOMEProgressionAutoCompleter.syncPlayer(s.f.player, s.p);
            KOMEPacketProgressionData sent = latest(s);
            ByteBuf bytes = Unpooled.buffer(); sent.toBytes(bytes);
            KOMEPacketProgressionData packet = new KOMEPacketProgressionData(); packet.fromBytes(bytes);
            assertFalse(bytes.isReadable()); assertEquals(s.f.player.id, packet.playerId);
            assertEquals("knight", packet.canonicalRankKey); assertTrue(packet.revision > 0L);
            assertEquals(s.f.player.dimension, packet.dimension);
            assertTrue(apply(packet));
            s.f.world.isRemote = true;
            assertEquals(KOMEProgressionRank.KNIGHT, KOMEClientData.INSTANCE.progressions.get(packet.playerId).getCanonicalRank());
            assertTrue(KOMEProgressionPermissions.has(s.f.player, KOMEProgressionPermissions.FAST_TRAVEL));
            assertTrue(KOMEProgressionPermissions.has(s.f.player, KOMEProgressionPermissions.FACTION_ARMOR));
            assertEquals("rohan", KOMEClientData.INSTANCE.getPlayerFactionKey(packet.playerId));
        }
    }

    @Test public void olderDuplicateAndOtherViewerSnapshotsCannotOverwriteCurrentPermissions() {
        UUID viewer = UUID.randomUUID(); KOMEClientData data = KOMEClientData.INSTANCE;
        assertTrue(data.acceptProgression(viewer, 20L, "lord", "gondor", true,
            Arrays.asList("baseline.fast_travel"), Collections.emptyMap()));
        for (long revision : new long[]{1L,19L,20L}) assertFalse(data.acceptProgression(viewer,revision,
            "wanderer","",false,Collections.emptyList(),Collections.emptyMap()));
        assertFalse(data.acceptProgression(UUID.randomUUID(), 21L, "prince", "rohan", true,
            Collections.emptyList(), Collections.emptyMap()));
        assertEquals(KOMEProgressionRank.LORD, data.progressions.get(viewer).getCanonicalRank());
        assertEquals("gondor", data.getPlayerFactionKey(viewer)); assertTrue(data.isProgressionEnabled());
        data.clearProgressionProjection(); assertTrue(data.progressions.isEmpty());
        assertFalse(data.acceptProgression(viewer,19L,"lord","gondor",true,Collections.emptyList(),Collections.emptyMap()));
        assertTrue(data.acceptProgression(viewer,21L,"serf","rohan",true,Collections.emptyList(),Collections.emptyMap()));
        data.resetClientState(); assertTrue(data.lastKnownPlayerFactions.isEmpty());
        assertTrue(data.acceptProgression(UUID.randomUUID(),1L,"wanderer","",true,Collections.emptyList(),Collections.emptyMap()));
    }

    @Test public void nativeFactionAndAdminCorrectionsRefreshEvenWithoutNewCompletionFlags() throws Exception {
        try (KOMEProgressionFollowupTest.Session s = new KOMEProgressionFollowupTest.Session()) {
            s.p.reset(); KOMEProgressionAutoCompleter.runForPlayer(s.f.player, false);
            KOMEPacketProgressionData first = latest(s); assertEquals("rohan", KOMEAlliance.normalizeFactionKey(first.rankSummary.factionKey));
            s.f.pledge(LOTRFaction.GONDOR); KOMEProgressionAutoCompleter.runForPlayer(s.f.player, false);
            KOMEPacketProgressionData changed = latest(s); assertTrue(changed.revision > first.revision);
            assertEquals("gondor", KOMEAlliance.normalizeFactionKey(changed.rankSummary.factionKey)); assertEquals("wanderer", changed.canonicalRankKey);
            assertTrue(KOMECanonicalRankService.overrideCanonicalRank(s.f.data,s.f.player,KOMEProgressionRank.LORD));
            KOMEProgressionAutoCompleter.runForPlayer(s.f.player,false);
            KOMEPacketProgressionData corrected = latest(s); assertEquals("lord",corrected.canonicalRankKey);
            assertTrue(corrected.revision > changed.revision);
            s.f.data.setProgressionEnabled(false); KOMEProgressionAutoCompleter.runForPlayer(s.f.player,false);
            assertFalse(latest(s).progressionEnabled);
            new KOMEEvents().onPlayerChangedDimension(new cpw.mods.fml.common.gameevent.PlayerEvent.PlayerChangedDimensionEvent(s.f.player,0,100));
            assertTrue(latest(s).revision > corrected.revision);
        }
    }

    @Test public void queuedSnapshotIsDetachedAndDoesNotInferStandingFromLocalizedTitle() {
        UUID id = UUID.randomUUID();
        KOMEPacketProgressionData original = new KOMEPacketProgressionData("Player",
            new java.util.ArrayList(Arrays.asList("baseline.fast_travel")),new java.util.HashMap());
        original.authority(id,"knight",2L,100,true);
        KOMEPacketProgressionData copy=original.copy(); original.completed.clear(); original.canonicalRankKey="prince";
        assertEquals("knight",copy.canonicalRankKey); assertEquals(1,copy.completed.size());
        assertTrue(apply(copy)); assertEquals(KOMEProgressionRank.KNIGHT,KOMEClientData.INSTANCE.progressions.get(id).getCanonicalRank());
        assertFalse(KOMEClientData.INSTANCE.acceptProgression(id,3L,"King","gondor",true,Collections.emptyList(),Collections.emptyMap()));
        new kome.common.KOMECommonProxy().acceptProgressionData(copy,null);
        assertEquals(KOMEProgressionRank.KNIGHT,KOMEClientData.INSTANCE.progressions.get(id).getCanonicalRank());
    }

    @Test public void codecRejectsUnknownRanksNegativeCountsAndOldArtifactHeader() {
        ByteBuf invalid=Unpooled.buffer(); KOMEPopulationWire.writeHeader(invalid);
        invalid.writeLong(1L); invalid.writeLong(2L); KOMEPopulationWire.writeText(invalid,"king");
        expectInvalid(invalid);
        ByteBuf negative=Unpooled.buffer(); KOMEPopulationWire.writeHeader(negative);
        negative.writeLong(1L);negative.writeLong(2L);KOMEPopulationWire.writeText(negative,"knight");
        negative.writeLong(1L);negative.writeInt(100);negative.writeBoolean(true);
        KOMEPopulationWire.writeText(negative,"Player"); negative.writeInt(-1);expectInvalid(negative);
        ByteBuf old=Unpooled.buffer();KOMEPopulationWire.writeText(old,"1.0.9-integration-g7");expectInvalid(old);
    }
    private static void expectInvalid(ByteBuf bytes) {
        try {new KOMEPacketProgressionData().fromBytes(bytes);fail("Malformed progression must be rejected");}
        catch(IllegalArgumentException expected) { }
    }
    private static boolean apply(KOMEPacketProgressionData packet) {
        return KOMEClientData.INSTANCE.acceptProgression(packet.playerId,packet.revision,packet.canonicalRankKey,
            packet.rankSummary.factionKey,packet.progressionEnabled,packet.completed,packet.assignments);
    }
    private static KOMEPacketProgressionData latest(KOMEProgressionFollowupTest.Session s) {
        for(int i=s.f.network.messages.size()-1;i>=0;i--)
            if(s.f.network.messages.get(i) instanceof KOMEPacketProgressionData)
                return (KOMEPacketProgressionData)s.f.network.messages.get(i);
        throw new AssertionError("Missing server progression publication");
    }
}
