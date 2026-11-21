package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;
import org.junit.Rule;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

public class KOMEConquestBuildPromotionTest {
    @Rule public final KOMETileTestResources geometry = new KOMETileTestResources();
    private static final String TILE = "T001";
    private static int[] position;

    @Test public void registrationUsesExactCurrentApprovedThresholdsForBothTypes() {
        long[] hours = {0L, 1L, 100L, 4900L, 4990L, 5000L, 5001L};
        int[] levels = {1, 2, 2, 2, 2, 3, 3};
        for (KOMEBuildType type : new KOMEBuildType[] {KOMEBuildType.NORMAL, KOMEBuildType.DEFENSIVE}) {
            for (int i = 0; i < hours.length; i++) {
                KOMEWorldData data = world();
                KOMEPlayerBuild build = create(data, type, hours[i]);
                assertEquals(type + " " + hours[i], levels[i], level(data));
                assertEquals(hours[i], build.approvedCentiHours());
                assertEquals(0L, build.developedNativeCentiHours());
                assertEquals(0, data.armyMovements.size());
            }
        }
    }

    @Test public void pendingAndRejectedHoursDoNotPromoteAndSeveralBuildsCombine() {
        KOMEWorldData data = world();
        KOMEPlayerBuild normal = create(data, KOMEBuildType.NORMAL, 0L);
        KOMEPlayerBuild defensive = create(data, KOMEBuildType.DEFENSIVE, 0L);
        UUID helper = UUID.randomUUID();
        KOMEBuildContribution rejected = submit(data, normal, helper, 5000L);
        assertEquals(1, level(data));
        assertTrue(KOMEBuildService.decideSubmission(data, normal, rejected.id, normal.managerUuid,
            "Manager", false, "Rejected", 20L).allowed);
        assertEquals(1, level(data));
        KOMEBuildContribution first = submit(data, normal, helper, 3000L);
        KOMEBuildContribution second = submit(data, defensive, helper, 2000L);
        assertEquals(1, level(data));
        approve(data, normal, first);
        assertEquals(2, level(data));
        approve(data, defensive, second);
        assertEquals(3, level(data));
        assertEquals(0L, normal.developedNativeCentiHours());
        assertEquals(0L, defensive.developedNativeCentiHours());
    }

    @Test public void repeatedApprovalAdjustmentsRemovalAndDeletionNeverDoubleCountOrDemote() {
        KOMEWorldData data = world();
        KOMEPlayerBuild build = create(data, KOMEBuildType.NORMAL, 4990L);
        KOMEBuildContribution first = build.contributions.get(0);
        assertEquals(2, level(data));
        NBTTagCompound beforeRetry = save(data);
        data.setDirty(false);
        approve(data, build, first);
        assertEquals(beforeRetry.toString(), save(data).toString());
        assertFalse(data.isDirty());
        assertTrue(KOMEBuildService.adjustSubmission(data, build, first.id, null,
            "Admin", true, "50.00", "Measured", 30L).allowed);
        assertEquals(3, level(data));
        NBTTagCompound afterAdjustment = save(data);
        data.setDirty(false);
        assertTrue(KOMEBuildService.adjustSubmission(data, build, first.id, null,
            "Admin", true, "50.00", "Same", 31L).allowed);
        assertEquals(afterAdjustment.toString(), save(data).toString());
        assertFalse(data.isDirty());
        assertTrue(KOMEBuildService.adjustSubmission(data, build, first.id, null,
            "Admin", true, "1.00", "Correction", 32L).allowed);
        assertEquals(3, level(data));
        assertTrue(KOMEBuildService.removeApprovedContribution(data, build, first.id,
            build.managerUuid, "Manager", "Removed", 33L).allowed);
        assertEquals(3, level(data));
        assertTrue(KOMEBuildService.deleteBuild(data, build, build.managerUuid,
            "Manager", false, "Deleted", 34L).allowed);
        assertEquals(3, level(data));
        assertEquals(3, level(roundTrip(data)));

        KOMEWorldData rejected = world();
        KOMEPlayerBuild reviewed = create(rejected, KOMEBuildType.DEFENSIVE, 5000L);
        assertTrue(KOMEBuildService.decideSubmission(rejected, reviewed, reviewed.contributions.get(0).id,
            reviewed.managerUuid, "Manager", false, "Rejected after approval", 35L).allowed);
        assertEquals(0L, reviewed.approvedCentiHours());
        assertEquals(3, level(roundTrip(rejected)));
    }

    @Test public void administrativeTotalRepairAndGrandfatherImportPromote() {
        KOMEWorldData data = world();
        KOMEPlayerBuild build = create(data, KOMEBuildType.DEFENSIVE, 0L);
        assertTrue(KOMEBuildService.setApprovedHours(data, build, null, "Admin", true, "49.90", 20L).allowed);
        assertEquals(2, level(data));
        assertTrue(KOMEBuildService.setApprovedHours(data, build, null, "Admin", true, "50.00", 21L).allowed);
        assertEquals(3, level(data));

        KOMEWorldData imported = world();
        int[] point = position();
        KOMEPlayerBuild grandfather = KOMEBuildService.importGrandfatheredNormal(imported, true,
            "Old Hall", TILE, KOMETileTestResources.dimension(), point[0], 64D, point[1],
            UUID.randomUUID(), "Admin", "gondor", 5000L, 40L);
        assertEquals(3, level(imported));
        assertEquals(5000L, grandfather.approvedCentiHours());
    }

    @Test public void loadReconcilesExistingApprovedBuildsWithoutChangingRecordsOrOwnership() {
        KOMEWorldData data = world();
        KOMEPlayerBuild build = create(data, KOMEBuildType.NORMAL, 5000L);
        data.getConquestTileIfPresent(TILE).waypointLevel = 1; // pre-promotion saved world
        String buildsBeforePromotion = save(data).getTag("Builds").toString();
        assertTrue(KOMEConquestBuildPromotionService.promoteLoadedTiles(data));
        assertEquals(buildsBeforePromotion, save(data).getTag("Builds").toString());
        data.setDirty(false);
        assertFalse(KOMEConquestBuildPromotionService.promoteLoadedTiles(data));
        assertFalse(data.isDirty());
        data.getConquestTileIfPresent(TILE).waypointLevel = 1;
        NBTTagCompound saved = save(data);
        String populations = saved.getTag("FactionPopulations").toString();
        String movements = saved.getTag("ArmyMovements").toString();
        KOMEWorldData loaded = new KOMEWorldData("promotion-reload");
        loaded.readFromNBT(saved);
        assertEquals(3, level(loaded));
        assertEquals("gondor", loaded.getConquestTileIfPresent(TILE).projectRulingFaction());
        assertEquals(123L, loaded.getConquestTileIfPresent(TILE).claimedWorldTime);
        NBTTagCompound rewritten = save(loaded);
        assertEquals(populations, rewritten.getTag("FactionPopulations").toString());
        assertEquals(movements, rewritten.getTag("ArmyMovements").toString());
        assertEquals(5000L, loaded.getBuild(build.id).approvedCentiHours());
        assertEquals(KOMEBuildContribution.APPROVED, loaded.getBuild(build.id).contributions.get(0).status);
        assertEquals(3, level(roundTrip(loaded)));
        loaded.getConquestTileIfPresent(TILE).claim("rohan", 240L);
        assertEquals(3, level(roundTrip(loaded)));
    }

    @Test public void existingHigherLevelRemainsAndNewResponseUsesPromotedLevel() {
        KOMEWorldData data = world();
        assertEquals(20L * 60_000L, responseMillis(data));
        create(data, KOMEBuildType.NORMAL, 1L);
        assertEquals(35L * 60_000L, responseMillis(data));
        data.getConquestTileIfPresent(TILE).waypointLevel = 3;
        assertEquals(50L * 60_000L, responseMillis(data));
        create(data, KOMEBuildType.DEFENSIVE, 100L);
        assertEquals(3, level(data));
        assertEquals(50L * 60_000L, responseMillis(data));
    }

    private static KOMEWorldData world() {
        KOMEWorldData data = new KOMEWorldData("promotion");
        data.initializeIntegratedWorld();
        assertEquals(1, level(data));
        data.getConquestTileIfPresent(TILE).claim("gondor", 123L);
        return data;
    }

    private static KOMEPlayerBuild create(KOMEWorldData data, KOMEBuildType type, long hours) {
        int[] point = position();
        return KOMEBuildService.create(data, "Hall", TILE, KOMETileTestResources.dimension(),
            point[0], 64D, point[1], UUID.randomUUID(), "Manager", "gondor", "gondor", type, hours, 10L);
    }

    private static KOMEBuildContribution submit(KOMEWorldData data, KOMEPlayerBuild build, UUID helper, long hours) {
        return KOMEBuildService.addSubmission(data, build, helper, "Helper", "gondor", hours, false, 20L);
    }

    private static void approve(KOMEWorldData data, KOMEPlayerBuild build, KOMEBuildContribution contribution) {
        assertTrue(KOMEBuildService.decideSubmission(data, build, contribution.id,
            build.managerUuid, "Manager", true, "Approved", 25L).allowed);
    }

    private static int level(KOMEWorldData data) {
        return data.getConquestTileIfPresent(TILE).waypointLevel;
    }

    private static long responseMillis(KOMEWorldData data) {
        return KOMEOrdinaryBattlePlanner.responseDuration(level(data), 20, 35, 50).durationMillis;
    }

    private static NBTTagCompound save(KOMEWorldData data) {
        NBTTagCompound nbt = new NBTTagCompound();
        data.writeToNBT(nbt);
        return nbt;
    }

    private static KOMEWorldData roundTrip(KOMEWorldData data) {
        KOMEWorldData loaded = new KOMEWorldData("promotion-roundtrip");
        loaded.readFromNBT(save(data));
        return loaded;
    }

    private static synchronized int[] position() {
        if (position != null) return position;
        KOMETileRasterSnapshot raster = KOMETileTestResources.real();
        int color = raster.colorsById().get(TILE);
        int[] pixels = raster.copyArgbPixels();
        for (int i = 0; i < pixels.length; i++) {
            if ((pixels[i] >>> 24) <= 24 || (pixels[i] & 0xFFFFFF) != color) continue;
            int x = KOMETileTestResources.worldX(i % raster.width);
            int z = KOMETileTestResources.worldZ(i / raster.width);
            if (TILE.equals(raster.resolve(KOMETileTestResources.dimension(), x, z).tileId)) {
                position = new int[] {x, z};
                return position;
            }
        }
        throw new AssertionError("No real coordinate for " + TILE);
    }
}
