package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;
import org.junit.Rule;
import org.junit.Test;
import java.util.UUID;
import static org.junit.Assert.*;

public class KOMETileBuildValidationTest {
    @Rule public final KOMETileTestResources geometry = new KOMETileTestResources();

    private static KOMEWorldData world() {
        KOMEWorldData data = new KOMEWorldData("tile-resolution");
        KOMEConquestTile tile = new KOMEConquestTile("T100"); tile.claim("gondor", 0L);
        data.conquestTiles.put(tile.id, tile);
        return data;
    }

    private static KOMEPlayerBuild create(KOMEWorldData data, int dimension, double x, double z) {
        return KOMEBuildService.create(data, "Hall", "T100", dimension, x, 64D, z, UUID.randomUUID(),
            "Builder", "gondor", "gondor", KOMEBuildType.NORMAL, 25L, 10L);
    }

    private static String saved(KOMEWorldData data) {
        NBTTagCompound nbt = new NBTTagCompound(); data.writeToNBT(nbt); return nbt.toString();
    }

    @Test public void resolvedCoordinateRegistersWithOriginalFractionalPosition() {
        KOMEWorldData data = world();
        KOMEPlayerBuild build = create(data, KOMETileTestResources.dimension(), KOMETileTestResources.x() + 0.5, KOMETileTestResources.z() + 0.75);
        assertEquals(1, data.builds.size()); assertEquals("T100", build.tileId);
        assertEquals(KOMETileTestResources.x() + 0.5, build.x, 0);
        assertEquals(KOMETileTestResources.z() + 0.75, build.z, 0);
        assertTrue(data.isDirty()); assertEquals(25L, build.approvedCentiHours());
    }

    @Test public void allSpatialRejectionsAreAtomicIncludingAuditCountersAndExistingBuilds() {
        KOMEWorldData data = world();
        int dimension = KOMETileTestResources.dimension();
        KOMEPlayerBuild existing = create(data, dimension, KOMETileTestResources.x(), KOMETileTestResources.z());
        assertRejected(data, dimension, KOMETileTestResources.worldX(2291), KOMETileTestResources.worldZ(58), "IN_BOUNDS_GAP");
        assertRejected(data, dimension, KOMETileTestResources.worldX(0) - 1, KOMETileTestResources.worldZ(58), "OUTSIDE_MASK");
        assertRejected(data, dimension + 1, KOMETileTestResources.x(), KOMETileTestResources.z(), "UNSUPPORTED_DIMENSION");
        assertRejected(data, dimension, Double.POSITIVE_INFINITY, 0, "finite");
        assertRejected(data, dimension, (double) Integer.MAX_VALUE + 1, 0, "INVALID_COORDINATE");
        assertRejected(data, dimension, KOMETileTestResources.worldX(2292), KOMETileTestResources.worldZ(58), "confirmed conquest tile");
        KOMETileWorldResolver.INSTANCE.invalidate();
        assertRejected(data, dimension, KOMETileTestResources.x(), KOMETileTestResources.z(), "INVALID_SNAPSHOT");
        assertSame(existing, data.builds.get(existing.id));
    }

    private static void assertRejected(KOMEWorldData data, int dimension, double x, double z, String reason) {
        String before = saved(data); data.setDirty(false);
        int audit = data.centralAudit.size();
        try { create(data, dimension, x, z); fail("Coordinate must be rejected: " + reason); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage(), expected.getMessage().contains(reason)); }
        assertEquals(before, saved(data)); assertEquals(audit, data.centralAudit.size()); assertFalse(data.isDirty());
    }

    @Test public void ownershipChangesNeverChangeSpatialIdentityAndLookupNeverRepairsOwner() {
        KOMEWorldData data = world();
        KOMEConquestTile tile = data.conquestTiles.get("T100");
        tile.setCurrentRulingFaction("rohan");
        tile.currentRulingFaction = ""; // A legacy read-with-repair would change these fields.
        tile.ownerFaction = "rohan";
        data.setDirty(false);
        for (int i = 0; i < 20; i++) {
            assertEquals("T100", KOMEConquestTileDefaults.resolveWorldCoordinates(
                KOMETileTestResources.dimension(), KOMETileTestResources.x(), KOMETileTestResources.z()).tileId);
        }
        assertEquals("", tile.currentRulingFaction); assertEquals("rohan", tile.ownerFaction);
        assertEquals(1, data.conquestTiles.size()); assertFalse(data.isDirty());
    }

    @Test public void existingPersistedBuildsOutsideCoverageAreNotRevalidatedOrModified() {
        KOMEWorldData data = world();
        KOMEPlayerBuild build = create(data, KOMETileTestResources.dimension(), KOMETileTestResources.x(), KOMETileTestResources.z());
        // Preserve dev's manager-faction reconciliation; this test isolates spatial loading.
        KOMEPlayerProgression manager = new KOMEPlayerProgression();
        manager.setPledgedLord("lord", "Lord", "gondor");
        data.progressions.put(build.managerUuid, manager);
        build.x = Integer.MIN_VALUE; build.z = Integer.MAX_VALUE; build.dimension = 0;
        NBTTagCompound serialized = build.writeToNBT();
        KOMEPlayerBuild loaded = new KOMEPlayerBuild(); loaded.readFromNBT(serialized);
        data.builds.put(loaded.id, loaded);
        data.setDirty(false); String before = saved(data);
        KOMEBuildService.tileAtWorldCoordinates(loaded.dimension, loaded.x, loaded.z);
        KOMETileWorldResolver.INSTANCE.resolve(KOMETileTestResources.dimension(), Integer.MIN_VALUE, Integer.MAX_VALUE);
        assertEquals(before, saved(data)); assertTrue(loaded.active); assertFalse(data.isDirty());
        assertEquals(serialized.toString(), loaded.writeToNBT().toString());
        NBTTagCompound entireWorld = new NBTTagCompound(); data.writeToNBT(entireWorld);
        KOMEWorldData control = new KOMEWorldData("geometry-available");
        control.readFromNBT(entireWorld);
        KOMETileWorldResolver.INSTANCE.invalidate();
        KOMEWorldData reloadedWorld = new KOMEWorldData("cold-reload");
        reloadedWorld.readFromNBT(entireWorld);
        assertEquals(serialized.toString(), reloadedWorld.getBuild(build.id).writeToNBT().toString());
        assertTrue(reloadedWorld.getBuild(build.id).active);
        // Existing WorldData load normalization can mark dirty; geometry availability must not change it.
        assertEquals(control.isDirty(), reloadedWorld.isDirty());
        reloadedWorld.setDirty(false);
        KOMEBuildService.tileAtWorldCoordinates(build.dimension, build.x, build.z);
        assertFalse(reloadedWorld.isDirty());
    }
}
