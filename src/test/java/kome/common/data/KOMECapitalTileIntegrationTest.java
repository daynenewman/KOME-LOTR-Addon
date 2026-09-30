package kome.common.data;

import kome.common.KOMEAccessFixture;
import lotr.common.LOTRDimension;
import lotr.common.world.map.LOTRWaypoint;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Rule;
import org.junit.Test;
import java.util.UUID;
import static org.junit.Assert.*;

/** Cross-branch contracts through real capital, deployment and Build services. */
public class KOMECapitalTileIntegrationTest {
    @Rule public final KOMETileTestResources geometry = new KOMETileTestResources();

    @Test public void allCapitalDefinitionsUseConfiguredDimensionAndExactGeometry() {
        int previous = LOTRDimension.MIDDLE_EARTH.dimensionID;
        try {
            LOTRDimension.MIDDLE_EARTH.dimensionID = 173;
            assertTrue(KOMETileWorldResolver.INSTANCE.reloadBundled());
            int count = 0;
            for (KOMEFactionCapitalDefaults.Definition d : KOMEFactionCapitalDefaults.definitions()) {
                LOTRWaypoint w = d.resolveAndValidate();
                KOMETileResolution location = KOMEBuildService.tileAtWorldCoordinates(173, w.getXCoord(), w.getZCoord());
                assertEquals(KOMETileResolution.Status.RESOLVED, location.status);
                assertEquals(d.expectedTileId, location.tileId);
                assertTrue(KOMEStrategicDeploymentResolver.validateMetadata(d.expectedTileId,
                    173, w.getXCoord() + 0.5D, 80D, w.getZCoord() + 0.5D).valid);
                count++;
            }
            assertEquals(24, count);
            assertFalse(KOMEStrategicDeploymentResolver.validateMetadata("T388",
                previous, 78016.5D, 80D, 66240.5D).valid);
        } finally { LOTRDimension.MIDDLE_EARTH.dimensionID = previous; }
    }

    @Test public void deploymentRejectsGapOutsideUnavailableAndWrongTileExplicitly() {
        int d = LOTRDimension.MIDDLE_EARTH.dimensionID;
        rejected("T001", d, 189568D, -86016D, "IN_BOUNDS_GAP");
        rejected("T001", d, Integer.MIN_VALUE, Integer.MAX_VALUE, "OUTSIDE_MASK");
        rejected("T001", d + 1, 189696D, -86016D, "not Middle-earth");
        rejected("T132", d, 189696D, -86016D, "not inside");
        KOMETileWorldResolver.INSTANCE.invalidate();
        rejected("T001", d, 189696D, -86016D, "INVALID_SNAPSHOT");
    }

    @Test public void fractionalBoundaryDoesNotRoundIntoNeighboringCapitalTile() {
        int d = LOTRDimension.MIDDLE_EARTH.dimensionID;
        rejected("T001", d, Math.nextDown(189696D), -86016D, "IN_BOUNDS_GAP");
        assertTrue(KOMEStrategicDeploymentResolver.validateMetadata("T001", d,
            189696D, 80D, -86016D).valid);
        assertTrue(KOMEStrategicDeploymentResolver.validateMetadata("T001", d,
            Math.nextUp(189696D), 80D, -86016D).valid);
    }

    @Test public void unavailableCapitalDefinitionFailsBeforeFreshWorldPublication() {
        KOMEWorldData data = new KOMEWorldData("unavailable-capitals");
        KOMEFactionCapitalDefaults.Definition definition = KOMEFactionCapitalDefaults.definitions().get(0);
        KOMETileWorldResolver.INSTANCE.invalidate();
        try { definition.resolveAndValidate(); fail("Unavailable geometry must reject"); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("INVALID_SNAPSHOT")); }
        try { KOMEFactionCapitalDefaults.metadataFixture(0L); fail("No partial defaults"); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("INVALID_SNAPSHOT")); }
        assertNull(KOMEFactionCapitalService.getCapital(data, "hobbit"));
        assertTrue(data.centralAudit.isEmpty()); assertFalse(data.isDirty());
    }

    @Test public void liveRelocationRejectsSpatialFailureWithoutChangingCapitalOrAudit() throws Exception {
        KOMEAccessFixture f = new KOMEAccessFixture();
        f.world.provider.dimensionId = LOTRDimension.MIDDLE_EARTH.dimensionID;
        KOMEFactionCapitalService.initializeFresh(f.data, KOMEFactionCapitalDefaults.metadataFixture(1L));
        KOMEFactionCapitalRecord capital = KOMEFactionCapitalService.getCapital(f.data, "dunedain");
        int audit = f.data.centralAudit.size(); f.data.setDirty(false);
        f.player.posX = 34944D; f.player.posY = 80D; f.player.posZ = 640D;
        assertFalse(KOMEFactionCapitalService.relocateHere(f.data, f.player, "dunedain", 2L).success);
        f.player.operator = true;
        assertRelocationRejected(f, "IN_BOUNDS_GAP");
        f.player.posX = Integer.MIN_VALUE; assertRelocationRejected(f, "OUTSIDE_MASK");
        f.world.provider.dimensionId++;
        assertRelocationRejected(f, "Middle-earth");
        f.world.provider.dimensionId--;
        f.player.posX = 189696D; f.player.posZ = -86016D;
        KOMETileWorldResolver.INSTANCE.invalidate();
        assertRelocationRejected(f, "INVALID_SNAPSHOT");
        assertSame(capital, KOMEFactionCapitalService.getCapital(f.data, "dunedain"));
        assertEquals(audit, f.data.centralAudit.size()); assertFalse(f.data.isDirty());
        assertTrue(f.network.messages.isEmpty());
    }

    @Test public void safePlacementNeverAcceptsGapAndFailsImmediatelyWithoutGeometry() throws Exception {
        KOMEAccessFixture f = new KOMEAccessFixture();
        f.world.provider.dimensionId = LOTRDimension.MIDDLE_EARTH.dimensionID;
        assertFalse(KOMEStrategicDeploymentResolver.resolveAround(f.world, "T136",
            34944D, 80D, 640D, 0).valid);
        assertFalse(KOMEStrategicDeploymentResolver.resolveCompactFormation(f.world, "T136",
            34944D, 80D, 640D, 2, 0).valid);
        KOMETileWorldResolver.INSTANCE.invalidate();
        assertTrue(KOMEStrategicDeploymentResolver.resolveAround(f.world, "T001",
            189696D, 80D, -86016D, 24).reason.contains("INVALID_SNAPSHOT"));
    }

    @Test public void rejectedNewBuildPreservesExistingDefensiveGateAndSequence() {
        KOMEWorldData data = new KOMEWorldData("gate-spatial-rejection");
        KOMEConquestTile tile = new KOMEConquestTile("T100"); tile.claim("gondor", 0L);
        data.conquestTiles.put(tile.id, tile); UUID manager = UUID.randomUUID();
        KOMEPlayerBuild build = KOMEBuildService.create(data, "Wall", "T100",
            KOMETileTestResources.dimension(), KOMETileTestResources.x(), 64, KOMETileTestResources.z(),
            manager, "Manager", "gondor", "gondor", KOMEBuildType.DEFENSIVE, 100L, 1L);
        KOMEDefensiveGateRecord gate = new KOMEDefensiveGateRecord();
        gate.id = build.allocateDefensiveGateRecordId(); build.addDefensiveGateRecord(gate);
        NBTTagCompound before = new NBTTagCompound(); data.writeToNBT(before); data.setDirty(false);
        try {
            KOMEBuildService.create(data, "Invalid", "T100", KOMETileTestResources.dimension(),
                34944D, 64D, 640D, manager, "Manager", "gondor", "gondor", KOMEBuildType.DEFENSIVE, 100L, 2L);
            fail("Gap must reject");
        } catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("IN_BOUNDS_GAP")); }
        NBTTagCompound after = new NBTTagCompound(); data.writeToNBT(after);
        assertEquals(before, after); assertFalse(data.isDirty()); assertSame(gate, build.getDefensiveGateRecord("G1"));
        assertEquals(1L, build.getDefensiveGateRecordSequence());
    }

    private static void rejected(String tile, int dimension, double x, double z, String reason) {
        KOMEStrategicDeploymentResolver.Validation result =
            KOMEStrategicDeploymentResolver.validateMetadata(tile, dimension, x, 80D, z);
        assertFalse(result.valid); assertTrue(result.reason, result.reason.contains(reason));
    }
    private static void assertRelocationRejected(KOMEAccessFixture f, String reason) {
        KOMEFactionCapitalService.RelocationResult result =
            KOMEFactionCapitalService.relocateHere(f.data, f.player, "dunedain", 2L);
        assertFalse(result.success); assertTrue(result.reason, result.reason.contains(reason));
    }
}
