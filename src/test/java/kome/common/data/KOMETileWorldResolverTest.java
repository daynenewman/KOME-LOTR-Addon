package kome.common.data;

import org.junit.Rule;
import org.junit.Test;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;
import static kome.common.data.KOMETileRasterSnapshotTest.*;
import static kome.common.data.KOMETileResolution.Status.*;

public class KOMETileWorldResolverTest {
    @Rule public final KOMETileTestResources resources = new KOMETileTestResources();

    @Test public void realMaskGapAndT001Regression() {
        KOMETileWorldResolver r = KOMETileWorldResolver.INSTANCE;
        int dimension = KOMETileTestResources.dimension();
        int z = KOMETileTestResources.worldZ(58);
        KOMETileResolution gap = r.resolve(dimension, KOMETileTestResources.worldX(2291), z);
        assertEquals(IN_BOUNDS_GAP, gap.status);
        assertEquals(2291, gap.maskX); assertEquals(58, gap.maskY);
        assertFalse(gap.resolvedTileId().isPresent()); assertNotEquals("T001", gap.tileId);
        KOMETileResolution tile = r.resolve(dimension, KOMETileTestResources.worldX(2292), z);
        assertEquals(RESOLVED, tile.status); assertEquals("T001", tile.tileId);
        assertEquals(2292, tile.maskX); assertEquals(58, tile.maskY);
        assertEquals(189696, tile.worldX); assertEquals(-86016, tile.worldZ);
    }

    @Test public void knownLotrControlPointsAndEveryMaskBoundary() {
        KOMETileRasterSnapshot s = KOMETileTestResources.real();
        // Confirm the tested packaged v36.15 transform, without hard-coding it in production.
        assertEquals(810, s.transform.originX); assertEquals(730, s.transform.originZ); assertEquals(128, s.transform.scale);
        assertEquals(3200, s.width); assertEquals(4000, s.height);
        assertEquals(810L, s.resolve(s.transform.dimension, 0, 0).maskX);
        assertEquals(730L, s.resolve(s.transform.dimension, 0, 0).maskY);
        for (int pixel = 0; pixel <= s.width; pixel++) {
            int x = KOMETileTestResources.worldX(pixel);
            assertEquals(pixel - 1L, s.resolve(s.transform.dimension, x - 1, 0).maskX);
            assertEquals(pixel, s.resolve(s.transform.dimension, x, 0).maskX);
            assertEquals(pixel, s.resolve(s.transform.dimension, x + 1, 0).maskX);
        }
        for (int pixel = 0; pixel <= s.height; pixel++) {
            int z = KOMETileTestResources.worldZ(pixel);
            assertEquals(pixel - 1L, s.resolve(s.transform.dimension, 0, z - 1).maskY);
            assertEquals(pixel, s.resolve(s.transform.dimension, 0, z).maskY);
            assertEquals(pixel, s.resolve(s.transform.dimension, 0, z + 1).maskY);
        }
    }

    @Test public void actualRasterAgreesWithIndependentPixelSamples() throws Exception {
        java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(getClass().getClassLoader().getResource(KOMETileWorldResolver.MASK));
        KOMETileRasterSnapshot snapshot = KOMETileTestResources.real();
        java.util.Random random = new java.util.Random(9917);
        for (int i = 0; i < 5000; i++) {
            int x = random.nextInt(image.getWidth()), y = random.nextInt(image.getHeight());
            int argb = image.getRGB(x, y);
            String expected = (argb >>> 24) <= 24 ? null : KOMEConquestTileDefaults.getTileIdsByColor().get(argb & 0xFFFFFF);
            KOMETileResolution r = snapshot.resolve(snapshot.transform.dimension,
                KOMETileTestResources.worldX(x), KOMETileTestResources.worldZ(y));
            assertEquals(expected == null ? IN_BOUNDS_GAP : RESOLVED, r.status);
            assertEquals(expected == null ? "" : expected, r.tileId);
        }
    }

    @Test public void commonCompatibilityAndWorldAdaptersAgreeAtAllCellEdges() {
        KOMETileWorldResolver r = KOMETileWorldResolver.INSTANCE;
        int dimension = KOMETileTestResources.dimension(), z = KOMETileTestResources.worldZ(58);
        for (int pixel = 0; pixel <= 3200; pixel++) {
            int x = KOMETileTestResources.worldX(pixel);
            for (int offset = -1; offset <= 1; offset++) {
                KOMETileResolution exact = r.resolve(dimension, x + offset, z);
                KOMETileResolution adapter = r.resolveWorldPosition(dimension, x + offset, z);
                assertEquals(exact.toString(), adapter.toString());
                assertEquals(exact.tileId, KOMEConquestTileDefaults.getTileIdAtMapPosition(dimension,
                    pixel + offset / 128.0, 58));
            }
        }
    }

    @Test public void fractionalInputsFloorOnceAndInvalidInputsAreExplicit() {
        KOMETileWorldResolver r = KOMETileWorldResolver.INSTANCE;
        int d = KOMETileTestResources.dimension();
        int edge = KOMETileTestResources.worldX(2292), z = KOMETileTestResources.worldZ(58);
        assertEquals(IN_BOUNDS_GAP, r.resolveWorldPosition(d, Math.nextDown((double) edge), z).status);
        assertEquals("T001", r.resolveWorldPosition(d, edge + 0.9, z).tileId);
        assertEquals(-1, r.resolveWorldPosition(d, -0.1, 0).worldX);
        for (double input : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
                Double.MAX_VALUE, (double) Integer.MAX_VALUE + 1, (double) Integer.MIN_VALUE - 1}) {
            assertEquals(INVALID_COORDINATE, r.resolveWorldPosition(d, input, 0).status);
            assertEquals(INVALID_COORDINATE, r.resolveMapPosition(d, input, 0).status);
        }
        assertEquals(OUTSIDE_MASK, r.resolve(d, Integer.MIN_VALUE, Integer.MAX_VALUE).status);
    }

    @Test public void configurableDimensionIsCapturedFromExistingLotrOwner() {
        int original = lotr.common.LOTRDimension.MIDDLE_EARTH.dimensionID;
        try {
            lotr.common.LOTRDimension.MIDDLE_EARTH.dimensionID = 173;
            KOMETileWorldResolver resolver = new KOMETileWorldResolver();
            assertTrue(resolver.reloadBundled());
            assertEquals(173, resolver.snapshot().get().transform.dimension);
            assertEquals(RESOLVED, resolver.resolve(173, 189696, -86016).status);
            assertEquals(UNSUPPORTED_DIMENSION, resolver.resolve(original, 189696, -86016).status);
            assertEquals(UNSUPPORTED_DIMENSION, resolver.resolve(-1, 189696, -86016).status);
        } finally { lotr.common.LOTRDimension.MIDDLE_EARTH.dimensionID = original; }
    }

    @Test public void invalidInitialStateAndFailedReplacementNeverBecomeEmptySuccess() throws Exception {
        KOMETileWorldResolver resolver = new KOMETileWorldResolver();
        assertEquals(INVALID_SNAPSHOT, resolver.resolve(137, 0, -4).status);
        assertFalse(resolver.reload(null, text(MAPPING), transform(), IDS, Collections.<String>emptySet()));
        assertEquals(INVALID_SNAPSHOT, resolver.resolve(137, 0, -4).status);
        KOMETileRasterSnapshot valid = tiny(0xFF000001, 0xFF000002);
        resolver.publish(valid);
        assertFalse(resolver.reload(png(2, 1, 0xFF123456, 0), text(MAPPING), transform(), IDS, Collections.<String>emptySet()));
        assertSame(valid, resolver.snapshot().get());
        assertEquals("T002", resolver.resolve(137, 0, -4).tileId);
        assertTrue(resolver.loadDiagnostic().contains("Unknown opaque color"));
        resolver.invalidate();
        assertEquals(INVALID_SNAPSHOT, resolver.resolve(137, 0, -4).status);
    }

    @Test public void concurrentReadersObserveOnlyWholeSnapshots() throws Exception {
        final KOMETileWorldResolver resolver = new KOMETileWorldResolver();
        final KOMETileRasterSnapshot a = tiny(0xFF000001, 0xFF000001);
        final KOMETileRasterSnapshot b = KOMETileRasterSnapshot.load(png(2, 1, 0xFF000002, 0), text(MAPPING),
            new KOMETileRasterSnapshot.Transform(137, 0, 1, 4, 2, 1), IDS, Collections.<String>emptySet());
        resolver.publish(a);
        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<Future<?>>();
        try {
            futures.add(executor.submit(() -> { start.await(); for (int i = 0; i < 2000; i++) resolver.publish((i & 1) == 0 ? b : a); return null; }));
            for (int thread = 0; thread < 4; thread++) futures.add(executor.submit(() -> {
                start.await();
                for (int i = 0; i < 4000; i++) {
                    KOMETileResolution r = resolver.resolve(137, 0, -4);
                    assertEquals(RESOLVED, r.status);
                    assertTrue(("T001".equals(r.tileId) && r.maskX == 1) || ("T002".equals(r.tileId) && r.maskX == 0));
                }
                return null;
            }));
            start.countDown();
            for (Future<?> future : futures) future.get(30, TimeUnit.SECONDS);
        } finally { executor.shutdownNow(); assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS)); }
    }
    @Test public void realFractionalEdgesAndSignedZeroUseHalfOpenCells() {
        KOMETileWorldResolver r = KOMETileWorldResolver.INSTANCE;
        int d = KOMETileTestResources.dimension();
        // Literal control coordinates are independently derived from LOTR's 2^7 scale.
        for (int[] edge : new int[][] {{-103680,0},{305920,3200},{0,810},{189696,2292}}) {
            assertEquals(edge[1]-1L, r.resolveWorldPosition(d, Math.nextDown((double)edge[0]), 0).maskX);
            assertEquals(edge[1], r.resolveWorldPosition(d, edge[0], 0).maskX);
            assertEquals(edge[1], r.resolveWorldPosition(d, Math.nextUp((double)edge[0]), 0).maskX);
        }
        for (int[] edge : new int[][] {{-93440,0},{418560,4000},{0,730},{-86016,58}}) {
            assertEquals(edge[1]-1L, r.resolveWorldPosition(d, 0, Math.nextDown((double)edge[0])).maskY);
            assertEquals(edge[1], r.resolveWorldPosition(d, 0, edge[0]).maskY);
            assertEquals(edge[1], r.resolveWorldPosition(d, 0, Math.nextUp((double)edge[0])).maskY);
        }
        assertEquals(-1, r.resolveWorldPosition(d, -Double.MIN_VALUE, -0.0).worldX);
        assertEquals(0, r.resolveWorldPosition(d, -0.0, Double.MIN_VALUE).worldX);
        for (double[] outside : new double[][] {{Math.nextDown(-103680D),0},{305920,0},{0,Math.nextDown(-93440D)},{0,418560}})
            assertEquals(OUTSIDE_MASK, r.resolveWorldPosition(d, outside[0], outside[1]).status);
        assertEquals(Integer.MAX_VALUE, r.resolveWorldPosition(d, Math.nextDown(2147483648D), 0).worldX);
        assertEquals(Integer.MIN_VALUE, r.resolveWorldPosition(d, -2147483648D, 0).worldX);
        assertEquals(INVALID_COORDINATE, r.resolveWorldPosition(d, Math.nextDown(-2147483648D), 0).status);
        assertEquals(INVALID_COORDINATE, r.resolveWorldPosition(d, 2147483648D, 0).status);
    }

    @Test public void realAssignedAndGapBoundariesAgreeAcrossMapAndWorldAdapters() {
        KOMETileWorldResolver r = KOMETileWorldResolver.INSTANCE;
        int d = KOMETileTestResources.dimension();
        assertEquals("T444", r.resolveWorldPosition(d, Math.nextDown(89600D), 103936D).tileId);
        assertEquals("T454", r.resolveWorldPosition(d, 89600D, 103936D).tileId);
        assertEquals("T454", r.resolveWorldPosition(d, Math.nextUp(89600D), 103936D).tileId);
        assertEquals("T444", r.resolveMapPosition(d, Math.nextDown(1510D), 1542D).tileId);
        assertEquals("T454", r.resolveMapPosition(d, 1510D, 1542D).tileId);
        assertEquals(IN_BOUNDS_GAP, r.resolveMapPosition(d, Math.nextDown(2292D), 58D).status);
        assertEquals("T001", r.resolveMapPosition(d, 2292D, 58D).tileId);
        assertEquals("T001", r.resolveMapPosition(d, Math.nextUp(2292D), 58D).tileId);
        for (double[] outside : new double[][] {{-Double.MIN_VALUE,730},{3200,730},{810,-Double.MIN_VALUE},{810,4000}})
            assertEquals(OUTSIDE_MASK, r.resolveMapPosition(d, outside[0], outside[1]).status);
        for (double bad : new double[] {Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}) {
            assertEquals(INVALID_COORDINATE, r.resolveMapPosition(d, 810, bad).status);
            assertEquals(INVALID_COORDINATE, r.resolveWorldPosition(d, 0, bad).status);
        }
    }

    @Test public void unavailableMapTransformDoesNotInventWorldOrigin() {
        KOMETileWorldResolver r = new KOMETileWorldResolver();
        KOMETileResolution result = r.resolveMapPosition(137, 2292, 58);
        assertEquals(INVALID_SNAPSHOT, result.status);
        assertFalse(result.hasWorldCoordinate);
        assertFalse(result.hasMaskCoordinate);
        assertFalse(result.toString().contains("world="));
        // Integer callers really did supply these world coordinates, even before initialization.
        assertTrue(r.resolve(137, 10, -20).hasWorldCoordinate);
    }

    @Test public void loadedLotrDimensionMismatchRejectsReplacementAndKeepsPriorSnapshot() {
        KOMETileWorldResolver r = new KOMETileWorldResolver();
        r.publish(KOMETileTestResources.real());
        KOMETileRasterSnapshot before = r.snapshot().get();
        int width = lotr.common.world.genlayer.LOTRGenLayerWorld.imageWidth;
        try {
            lotr.common.world.genlayer.LOTRGenLayerWorld.imageWidth = 3199;
            assertFalse(r.reloadBundled());
            assertSame(before, r.snapshot().get());
            assertTrue(r.loadDiagnostic().contains("disagree"));
            assertEquals("T001", r.resolve(before.transform.dimension, 189696, -86016).tileId);
        } finally { lotr.common.world.genlayer.LOTRGenLayerWorld.imageWidth = width; }
    }

    @Test public void existingPositionApiTracksPlayersAndUnitsWithoutLocationState() throws Exception {
        kome.common.KOMEAccessFixture fixture = new kome.common.KOMEAccessFixture();
        net.minecraft.entity.Entity unit = kome.common.KOMEAccessFixture.allocate(lotr.common.entity.npc.LOTREntityGondorSoldier.class);
        unit.worldObj = fixture.world;
        KOMETileWorldResolver r = KOMETileWorldResolver.INSTANCE;
        for (net.minecraft.entity.Entity entity : new net.minecraft.entity.Entity[] {fixture.player, unit}) {
            entity.worldObj.provider.dimensionId = KOMETileTestResources.dimension();
            entity.posX = 189695.999; entity.posZ = -86016.25;
            assertEquals(2291, r.resolveWorldPosition(entity.worldObj.provider.dimensionId, entity.posX, entity.posZ).maskX);
            entity.posX = 189696.25; entity.posZ = -86015.75;
            assertEquals("T001", r.resolveWorldPosition(entity.worldObj.provider.dimensionId, entity.posX, entity.posZ).tileId);
            // Teleport to the independently verified Weathertop location.
            entity.posX = 24128.5; entity.posZ = -831.5;
            assertEquals("T132", r.resolveWorldPosition(entity.worldObj.provider.dimensionId, entity.posX, entity.posZ).tileId);
            entity.worldObj.provider.dimensionId = -1;
            assertEquals(UNSUPPORTED_DIMENSION, r.resolveWorldPosition(entity.worldObj.provider.dimensionId, entity.posX, entity.posZ).status);
        }
        assertTrue(fixture.data.conquestTiles.isEmpty()); assertTrue(fixture.data.builds.isEmpty());
        assertFalse(fixture.data.isDirty());
    }
}
