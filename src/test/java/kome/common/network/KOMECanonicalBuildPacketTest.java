package kome.common.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.*;
import kome.common.data.*;
import net.minecraft.nbt.NBTTagCompound;
import java.util.UUID;

/** Wire-format regression tests for the canonical Build UI boundary. */
public class KOMECanonicalBuildPacketTest {
    @org.junit.Rule public final KOMETileTestResources tileGeometry = new KOMETileTestResources();
    @Test public void buildActionRoundTripHasOneTypeAndHoursValue() {
        KOMEPacketBuildAction sent = new KOMEPacketBuildAction("create", "T1", "", "", "Build",
            "gondor", "DEFENSIVE", Long.MAX_VALUE, 0, 1D, 2D, 3D);
        ByteBuf bytes = Unpooled.buffer();
        sent.toBytes(bytes);
        KOMEPacketBuildAction read = new KOMEPacketBuildAction();
        read.fromBytes(bytes);
        assertEquals("DEFENSIVE", read.buildType);
        assertEquals(Long.MAX_VALUE, read.centiHours);
        assertEquals(0, bytes.readableBytes());
        bytes.release();
        for (java.lang.reflect.Field field : KOMEPacketBuildAction.class.getFields()) {
            assertFalse("offensiveHalfHours".equals(field.getName()));
            assertFalse("defensiveHalfHours".equals(field.getName()));
        }
    }

    @Test public void captureBuildViewsRoundTripCanonicalHours() {
        KOMEPacketConquestCaptureGui.BuildView sent = new KOMEPacketConquestCaptureGui.BuildView();
        sent.id = "B1";
        sent.buildType = "NORMAL";
        sent.approvedCentiHours = 2147483648L;
        KOMEPacketConquestCaptureGui.ContributionView contribution = new KOMEPacketConquestCaptureGui.ContributionView();
        contribution.id = "H1";
        contribution.centiHours = 1L;
        sent.contributions.add(contribution);
        ByteBuf bytes = Unpooled.buffer();
        sent.write(bytes);
        KOMEPacketConquestCaptureGui.BuildView read = new KOMEPacketConquestCaptureGui.BuildView();
        read.read(bytes);
        assertEquals("NORMAL", read.buildType);
        assertEquals(2147483648L, read.approvedCentiHours);
        assertEquals(1L, read.contributions.get(0).centiHours);
        assertEquals(0, bytes.readableBytes());
        bytes.release();
    }
    @Test public void buildProjectionAndMarkerAreReadOnlyAndLossless() {
        KOMEWorldData data = new KOMEWorldData("projection");
        KOMEConquestTile tile = new KOMEConquestTile("T100"); tile.claim("gondor", 0L);
        data.conquestTiles.put(tile.id, tile);
        KOMEPlayerBuild build = KOMEBuildService.create(data, "Hall", tile.id, KOMETileTestResources.dimension(),
            KOMETileTestResources.x() + 1, 64D, KOMETileTestResources.z() + 3,
            UUID.randomUUID(), "Builder", "gondor", "gondor", KOMEBuildType.DEFENSIVE, Long.MAX_VALUE, 10L);
        assertTrue(KOMEBuildService.decideSubmission(data, build, build.contributions.get(0).id,
            build.managerUuid, "Builder", true, "Approved", 20L).allowed);
        KOMEBuildService.addSubmission(data, build, UUID.randomUUID(), "Helper", "gondor", 1L, false, 30L);
        data.setDirty(false); NBTTagCompound before = new NBTTagCompound(); data.writeToNBT(before);
        KOMEPacketConquestCaptureGui.BuildView view = KOMEPacketConquestOpenCapture.projectBuild(
            data, build, "gondor", "gondor", build.managerUuid, false);
        assertEquals(Long.MAX_VALUE, view.approvedCentiHours);
        assertEquals(1L, view.contributions.get(1).centiHours);
        assertEquals("PENDING", view.contributions.get(1).status);
        assertEquals("DEFENSIVE", view.buildType); assertEquals(1, view.pendingCount);
        KOMEPacketConquestCaptureGui packet = new KOMEPacketConquestCaptureGui(); packet.tileId = "T100"; packet.ownerFaction = "gondor"; packet.builds.add(view);
        ByteBuf bytes = Unpooled.buffer(); packet.toBytes(bytes);
        KOMEPacketConquestCaptureGui read = new KOMEPacketConquestCaptureGui(); read.fromBytes(bytes);
        assertEquals(Long.MAX_VALUE, read.builds.get(0).approvedCentiHours);
        assertEquals(1L, read.builds.get(0).contributions.get(1).centiHours);
        assertEquals(0, bytes.readableBytes()); bytes.release();
        NBTTagCompound markerTag = KOMEPacketConquestData.buildMarkerTag(build);
        assertFalse(markerTag.hasKey("Contributions")); assertFalse(markerTag.hasKey("BuildSchemaVersion"));
        KOMEPlayerBuild marker = KOMEPacketConquestData.readBuildMarkerTag(markerTag);
        assertEquals(build.id, marker.id); assertEquals(build.type, marker.type); assertEquals(build.tileId, marker.tileId);
        assertTrue(marker.contributions.isEmpty());
        NBTTagCompound after = new NBTTagCompound(); data.writeToNBT(after);
        assertEquals(before.toString(), after.toString()); assertFalse(data.isDirty());
    }

    @Test public void invalidPacketTypeAndNegativeHoursCannotCreateBuildState() {
        KOMEWorldData data = new KOMEWorldData("invalid");
        for (String key : new String[] {"", "other", "0"}) {
            KOMEPacketBuildAction sent = new KOMEPacketBuildAction("create", "T100", "", "", "Hall",
                "gondor", key, 1L, 0, 0D, 64D, 0D);
            ByteBuf bytes = Unpooled.buffer(); sent.toBytes(bytes);
            KOMEPacketBuildAction read = new KOMEPacketBuildAction(); read.fromBytes(bytes); bytes.release();
            try {
                KOMEBuildService.create(data, read.text, read.tileId, read.dimension, read.x, read.y, read.z,
                    UUID.randomUUID(), "Builder", "gondor", read.populationFaction,
                    KOMEBuildType.forKey(read.buildType), read.centiHours, 1L);
                fail("Invalid wire type must be rejected");
            } catch (IllegalArgumentException expected) { assertTrue(data.builds.isEmpty()); assertFalse(data.isDirty()); }
        }
        try {
            KOMEBuildService.create(data, "Hall", "T100", KOMETileTestResources.dimension(),
            KOMETileTestResources.x(), 64D, KOMETileTestResources.z(), UUID.randomUUID(), "Builder",
                "gondor", "gondor", KOMEBuildType.NORMAL, -1L, 1L);
            fail("Negative packet time must be rejected");
        } catch (IllegalArgumentException expected) { assertTrue(data.builds.isEmpty()); assertFalse(data.isDirty()); }
    }
}
