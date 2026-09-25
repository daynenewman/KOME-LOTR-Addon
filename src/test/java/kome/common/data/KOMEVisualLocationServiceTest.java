package kome.common.data;

import java.util.List;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import kome.common.KOMEAccessFixture;
import lotr.common.LOTRDimension;
import lotr.common.entity.npc.LOTREntityRohirrimWarrior;
import lotr.common.fac.LOTRFaction;
import lotr.common.world.map.LOTRWaypoint;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMEVisualLocationServiceTest {
    private static KOMEProgressionNpcRef npc(String name, double x, double z) {
        return new KOMEProgressionNpcRef(UUID.randomUUID().toString(), name, "rohan",
            LOTRDimension.MIDDLE_EARTH.dimensionID, x, 64.0D, z);
    }

    @Test public void relationshipRoleFollowsCanonicalRankAndClearRemovesMarker() {
        KOMEPlayerProgression player = new KOMEPlayerProgression();
        KOMEProgressionNpcRef master = npc("Eothellion", 10, 20), liege = npc("Hurin", 30, 40);
        player.setCanonicalRank(KOMEProgressionRank.SERF);
        player.getSerfKnightProgression().setSerfdomMaster(master);
        assertEquals(KOMEVisualMarker.Role.SERFDOM_MASTER,
            KOMEVisualLocationService.markersFor(player).get(0).role);
        player.getSerfKnightProgression().setProspectiveLiege(liege);
        player.setCanonicalRank(KOMEProgressionRank.KNIGHT);
        assertEquals(KOMEVisualMarker.Role.KNIGHT_LIEGE,
            KOMEVisualLocationService.markersFor(player).get(0).role);
        player.setCanonicalRank(KOMEProgressionRank.LORD);
        KOMEVisualMarker lord = KOMEVisualLocationService.markersFor(player).get(0);
        assertEquals(KOMEVisualMarker.Role.LORD_LIEGE, lord.role);
        assertEquals("Hurin", lord.title);
        assertEquals("Liege", lord.subtitle);
        player.getSerfKnightProgression().leaveSerfdomMaster();
        assertTrue(KOMEVisualLocationService.markersFor(player).isEmpty());
    }

    @Test public void lastKnownRelationshipLocationPersistsThroughSaveReload() {
        KOMEPlayerProgression player = new KOMEPlayerProgression();
        player.setCanonicalRank(KOMEProgressionRank.SERF);
        KOMEProgressionNpcRef original = npc("Aldor", 10, 20);
        player.getSerfKnightProgression().setSerfdomMaster(original);
        KOMEProgressionNpcRef moved = new KOMEProgressionNpcRef(original.entityUuid, "Aldor",
            original.factionKey, original.dimension, 150, 70, 260);
        player.getSerfKnightProgression().updateSerfdomMasterLocation(moved);
        KOMEPlayerProgression loaded = new KOMEPlayerProgression();
        loaded.readFromNBT(player.writeToNBT());
        KOMEVisualMarker marker = KOMEVisualLocationService.markersFor(loaded).get(0);
        assertEquals(150.0D, marker.x, 0.0D);
        assertEquals(260.0D, marker.z, 0.0D);
        assertEquals(original.entityUuid, marker.entityUuid);
    }

    @Test public void loadedMovementRefreshesAtLowFrequencyWithoutChunkLookups() throws Exception {
        KOMEAccessFixture fixture = new KOMEAccessFixture();
        KOMEPlayerProgression progression = fixture.data.getProgression(fixture.player.id);
        progression.setCanonicalRank(KOMEProgressionRank.SERF);
        UUID npcId = UUID.randomUUID();
        progression.getSerfKnightProgression().setSerfdomMaster(new KOMEProgressionNpcRef(
            npcId.toString(), "Old Name", "rohan", 0, 10, 64, 20));
        TestNpc npc = KOMEAccessFixture.allocate(TestNpc.class);
        npc.id = npcId; npc.setUniqueID(npcId); npc.name = "Aldor"; npc.worldObj = fixture.world;
        npc.posX = 150; npc.posY = 70; npc.posZ = 260;
        fixture.world.loadedEntityList.add(npc);
        fixture.data.setDirty(false);
        assertTrue(KOMEVisualLocationService.refreshLoadedLocations(fixture.player, fixture.data, progression));
        KOMEVisualMarker moved = KOMEVisualLocationService.markersFor(progression).get(0);
        assertEquals(150.0D, moved.x, 0.0D);
        assertEquals("Aldor", moved.title);
        assertTrue(fixture.data.isDirty());

        fixture.data.setDirty(false);
        npc.posX = 152.0D;
        assertFalse("Sub-four-block movement must not dirty canonical NBT",
            KOMEVisualLocationService.refreshLoadedLocations(fixture.player, fixture.data, progression));
        assertFalse(fixture.data.isDirty());

        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/kome/common/data/KOMEVisualLocationService.java")), StandardCharsets.UTF_8);
        for (String forbidden : new String[] {"getChunk", "loadChunk", "provideChunk", "chunkExists"})
            assertFalse(forbidden, source.contains(forbidden));
    }

    @Test public void outboundCourierUsesPersistedWaypointAndDeliveryRemovesOnlyObjective() {
        KOMEPlayerProgression player = new KOMEPlayerProgression();
        player.setCanonicalRank(KOMEProgressionRank.SERF);
        KOMEProgressionNpcRef master = npc("Aldor", LOTRWaypoint.EDORAS.getXCoord() + 900,
            LOTRWaypoint.EDORAS.getZCoord() + 900);
        KOMESerfKnightProgression state = player.getSerfKnightProgression();
        state.setSerfdomMaster(master);
        KOMESerfCourierAssignment courier = KOMESerfCourierAssignment.create(master, LOTRWaypoint.EDORAS);
        state.assignDuty(KOMESerfKnightDutyType.COURIER, courier.writeToNBT());
        List<KOMEVisualMarker> markers = KOMEVisualLocationService.markersFor(player);
        assertEquals(2, markers.size());
        KOMEVisualMarker objective = markers.get(1);
        assertEquals(KOMEVisualMarker.Role.COURIER, objective.role);
        assertEquals(LOTRWaypoint.EDORAS.getDisplayName(), objective.subtitle);
        assertEquals(LOTRWaypoint.EDORAS.getXCoord(), objective.x, 0.0D);
        courier.recipient = npc("Captain", courier.destinationX, courier.destinationZ);
        state.setDutyAssignmentData(KOMESerfKnightDutyType.COURIER, courier.writeToNBT());
        markers = KOMEVisualLocationService.markersFor(player);
        assertEquals(2, markers.size());
        objective = markers.get(1);
        assertEquals("Captain", objective.title);
        assertEquals("Deliver the dispatch", objective.subtitle);
        assertEquals(courier.recipient.entityUuid, objective.entityUuid);
        courier.stage = KOMESerfCourierAssignment.Stage.DELIVERED;
        state.setDutyAssignmentData(KOMESerfKnightDutyType.COURIER, courier.writeToNBT());
        markers = KOMEVisualLocationService.markersFor(player);
        assertEquals(1, markers.size());
        assertEquals(KOMEVisualMarker.Role.SERFDOM_MASTER, markers.get(0).role);
    }

    @Test public void markerProjectionIsPlayerSpecificAndStableAcrossReload() {
        KOMEPlayerProgression first = new KOMEPlayerProgression(), second = new KOMEPlayerProgression();
        first.setCanonicalRank(KOMEProgressionRank.SERF); second.setCanonicalRank(KOMEProgressionRank.SERF);
        first.getSerfKnightProgression().setSerfdomMaster(npc("First", 1, 2));
        second.getSerfKnightProgression().setSerfdomMaster(npc("Second", 3, 4));
        List<KOMEVisualMarker> firstMarkers = KOMEVisualLocationService.markersFor(first);
        List<KOMEVisualMarker> secondMarkers = KOMEVisualLocationService.markersFor(second);
        assertEquals("First", firstMarkers.get(0).title);
        assertEquals("Second", secondMarkers.get(0).title);
        assertNotEquals(firstMarkers.get(0).entityUuid, secondMarkers.get(0).entityUuid);
        NBTTagCompound saved = first.writeToNBT();
        KOMEPlayerProgression loaded = new KOMEPlayerProgression(); loaded.readFromNBT(saved);
        assertEquals(KOMEVisualLocationService.signature(firstMarkers),
            KOMEVisualLocationService.signature(KOMEVisualLocationService.markersFor(loaded)));
    }

    public static final class TestNpc extends LOTREntityRohirrimWarrior {
        UUID id;
        String name;
        private TestNpc() { super(null); }
        @Override public UUID getUniqueID() { return id; }
        @Override public String getNPCName() { return name; }
        @Override public LOTRFaction getFaction() { return LOTRFaction.ROHAN; }
    }
}
