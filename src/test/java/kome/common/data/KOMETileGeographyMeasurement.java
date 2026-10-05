package kome.common.data;

import java.lang.management.ManagementFactory;
import java.io.InputStream;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.entity.Entity;
import static kome.common.data.KOMEServerTileAwarenessTest.*;

/** Opt-in benchmark of actual loader/resolver/tracker; excludes AI, IO, network and GL frame costs. */
public final class KOMETileGeographyMeasurement {
    private static volatile long sink;
    private static final int TRIALS = 5, LOOKUPS = 500000, TICKS = 200;
    public static void main(String[] args) throws Exception {
        System.out.println("ENV java=" + System.getProperty("java.version") + " vm=" + System.getProperty("java.vm.name")
            + " os=" + System.getProperty("os.name") + " arch=" + System.getProperty("os.arch")
            + " processors=" + Runtime.getRuntime().availableProcessors() + " heapMax=" + Runtime.getRuntime().maxMemory());
        KOMETileWorldResolver resolver = new KOMETileWorldResolver();
        if (!resolver.reloadBundled()) throw new AssertionError(resolver.loadDiagnostic());
        long[] loads = new long[TRIALS];
        for (int i = 0; i < TRIALS; i++) {
            System.gc(); Thread.sleep(50);
            long start = System.nanoTime();
            if (!resolver.reloadBundled()) throw new AssertionError(resolver.loadDiagnostic());
            loads[i] = System.nanoTime() - start;
        }
        report("bundledReloadMs", loads, 1000000D);
        KOMETileRasterSnapshot s = resolver.snapshot().get(); int d = s.transform.dimension;
        System.gc(); Thread.sleep(50);
        System.out.println("MEMORY rasterCellBytes=" + (4L * s.width * s.height)
            + " exclusionIndexBytes=" + s.exclusions.cellIndexBytes() + " zones=" + s.exclusions.zoneCount()
            + " classifiedCells=" + s.exclusions.classifiedCells + " heapUsedApprox="
            + ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
        int[] xs = new int[8192], zs = new int[8192]; Random random = new Random(580063);
        for (int i = 0; i < xs.length; i++) {
            xs[i] = (random.nextInt(s.width + 2) - 1 - s.transform.originX) * s.transform.scale;
            zs[i] = (random.nextInt(s.height + 2) - 1 - s.transform.originZ) * s.transform.scale;
        }
        for (int i = 0; i < 3; i++) runLookups(resolver, d, xs, zs, false);
        for (boolean doubles : new boolean[] {false, true}) {
            long[] times = new long[TRIALS];
            for (int i = 0; i < TRIALS; i++) { long start = System.nanoTime(); runLookups(resolver, d, xs, zs, doubles); times[i] = System.nanoTime() - start; }
            report(doubles ? "worldDoubleNsPerLookup" : "worldIntegerNsPerLookup", times, LOOKUPS);
        }
        // Fixture-only annotation of all canonical gaps; never writes production resources.
        KOMETileRasterSnapshot synthetic = populatedFixture(s);
        resolver.publish(synthetic);
        System.gc(); Thread.sleep(50);
        System.out.println("POPULATED_MEMORY rasterCellBytes=" + (4L * synthetic.width * synthetic.height)
            + " exclusionIndexBytes=" + synthetic.exclusions.cellIndexBytes() + " zones=" + synthetic.exclusions.zoneCount()
            + " classifiedCells=" + synthetic.exclusions.classifiedCells + " retainedRasterSnapshots=2 heapUsedApprox="
            + ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
        int classifiedPositions = 0;
        for (int n = 0; n < xs.length; n++)
            if (resolver.resolve(d, xs[n], zs[n]).status == KOMETileResolution.Status.CLASSIFIED_EXCLUSION) classifiedPositions++;
        if (classifiedPositions == 0 || classifiedPositions == xs.length) throw new AssertionError("Fixture must sample mixed cells");
        System.out.println("POPULATED_COVERAGE positions=" + xs.length + " classifiedPositions=" + classifiedPositions);
        for (boolean doubles : new boolean[] {false, true}) {
            for (int i = 0; i < 3; i++) runLookups(resolver, d, xs, zs, doubles);
            long[] times = new long[TRIALS];
            for (int i = 0; i < TRIALS; i++) {
                long start = System.nanoTime(); runLookups(resolver, d, xs, zs, doubles); times[i] = System.nanoTime() - start;
            }
            report(doubles ? "populatedDoubleNsPerLookup" : "populatedIntegerNsPerLookup", times, LOOKUPS);
        }
        resolver.publish(s); synthetic = null;
        for (int count : new int[] {100, 2000}) measureTracking(resolver, count);
        kome.client.KOMEMapGeometryMeasurement.measure(s);
        System.out.println("LIMITS syntheticCurrentHostOnly=true liveServerTicks=false clientFps=false lowSpec=false sink=" + sink);
    }
    private static KOMETileRasterSnapshot populatedFixture(KOMETileRasterSnapshot geometry) throws Exception {
        byte[] image, mapping;
        try (InputStream input = KOMETileGeographyMeasurement.class.getClassLoader().getResourceAsStream(KOMETileWorldResolver.MASK);
                InputStream ids = KOMETileGeographyMeasurement.class.getClassLoader().getResourceAsStream(KOMETileWorldResolver.MAPPING)) {
            image = KOMETileExclusions.readBounded(input, 64 * 1024 * 1024, "fixture mask");
            mapping = KOMETileExclusions.readBounded(ids, 1024 * 1024, "fixture mapping");
        }
        String hash = KOMETileExclusions.sha256(image);
        StringBuilder metadata = new StringBuilder("schema=1\nwidth=" + geometry.width + "\nheight=" + geometry.height
            + "\nmask_sha256=" + hash + "\nzone\tfixture\tbenchmark-only\tSynthetic canonical gaps; not approved geography\n");
        int[] tileChecks = geometry.copyArgbPixels(); int runs = 0, gaps = 0;
        for (int y = 0; y < geometry.height; y++) {
            for (int x = 0; x < geometry.width;) {
                int index = y * geometry.width + x;
                if ((tileChecks[index] >>> 24) > 24) { tileChecks[index] = 1; x++; continue; }
                int first = x;
                while (x < geometry.width && (tileChecks[y * geometry.width + x] >>> 24) <= 24) {
                    tileChecks[y * geometry.width + x] = 0; x++; gaps++;
                }
                metadata.append("run\t").append(y).append('\t').append(first).append('\t').append(x).append("\tfixture\n"); runs++;
            }
        }
        byte[] annotations = metadata.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        System.out.println("POPULATED_FIXTURE metadataBytes=" + annotations.length + " runs=" + runs + " cells=" + gaps + " productionUnchanged=true");
        long[] parse = new long[TRIALS], loads = new long[TRIALS]; KOMETileRasterSnapshot result = null;
        for (int i = 0; i < TRIALS; i++) {
            System.gc(); Thread.sleep(50); long start = System.nanoTime();
            KOMETileExclusions exclusions = KOMETileExclusions.load(new java.io.ByteArrayInputStream(annotations), hash,
                geometry.width, geometry.height, tileChecks);
            parse[i] = System.nanoTime() - start;
            if (exclusions.classifiedCells != gaps) throw new AssertionError("Incomplete fixture");
            sink = exclusions.classifiedCells;
        }
        report("populatedMetadataParseMs", parse, 1000000D);
        for (int i = 0; i < TRIALS; i++) {
            result = null; System.gc(); Thread.sleep(50); long start = System.nanoTime();
            result = KOMETileRasterSnapshot.load(new java.io.ByteArrayInputStream(image), new java.io.ByteArrayInputStream(mapping),
                new java.io.ByteArrayInputStream(annotations), geometry.transform,
                KOMEConquestTileDefaults.getKnownTileIds(), KOMEConquestTileDefaults.getRetiredTileIds());
            loads[i] = System.nanoTime() - start;
            if (result.exclusions.classifiedCells != gaps) throw new AssertionError("Incomplete snapshot");
        }
        report("populatedSnapshotLoadMs", loads, 1000000D);
        return result;
    }
    private static void runLookups(KOMETileWorldResolver r, int d, int[] xs, int[] zs, boolean doubles) {
        long total = 0;
        for (int n = 0; n < LOOKUPS; n++) {
            int j = n & (xs.length - 1);
            KOMETileResolution x = doubles ? r.resolveWorldPosition(d, xs[j] + 0.5, zs[j] + 0.5) : r.resolve(d, xs[j], zs[j]);
            total += x.maskX + x.status.ordinal();
        }
        sink = total;
    }
    private static void measureTracking(KOMETileWorldResolver resolver, int count) throws Exception {
        TestWorld world = world(resolver.snapshot().get().transform.dimension);
        KOMEServerTileAwareness tracker = new KOMEServerTileAwareness(resolver); tracker.startSession();
        List<Entity> entities = new ArrayList<Entity>(); final long[] events = {0}; tracker.subscribe(e -> events[0]++);
        try {
            for (int n = 0; n < count; n++) {
                Entity entity = n < count / 20 ? player(world, new java.util.UUID(58, n)) : unit(world);
                entity.posX = 237248.5; entity.posZ = 87295.5; entities.add(entity);
                tracker.consider(entity, KOMEServerTileAwareness.Cause.FIRST_OBSERVATION);
            }
            for (int i = 0; i < 100; i++) tracker.sampleTick();
            for (int mode = 0; mode < 3; mode++) {
                long[] times = new long[TRIALS]; long prior = tracker.resolutionCount(); events[0] = 0;
                for (int trial = 0; trial < TRIALS; trial++) {
                    long start = System.nanoTime();
                    for (int tick = 0; tick < TICKS; tick++) {
                        if (mode == 1) for (Entity entity : entities) entity.posX += 0.03125;
                        if (mode == 2) for (Entity entity : entities) entity.posZ = entity.posZ < 87296 ? 87296.5 : 87295.5;
                        tracker.sampleTick();
                    }
                    times[trial] = System.nanoTime() - start;
                }
                long delta = tracker.resolutionCount() - prior;
                if (mode == 0 && (delta != 0 || events[0] != 0)) throw new AssertionError("Stationary spam");
                if (mode != 0 && delta != (long) count * TICKS * TRIALS) throw new AssertionError("Missing samples");
                report("tracking" + count + "_" + mode + "MsPerTick", times, TICKS * 1000000D);
                System.out.println("TRACK entities=" + count + " players=" + count / 20 + " mode=" + mode + " trials=" + TRIALS
                    + " ticksPerTrial=" + TICKS + " lookups=" + delta + " transitions=" + events[0]);
            }
        } finally { tracker.stopSession(); }
    }
    private static void report(String label, long[] nanos, double divisor) {
        double[] values = new double[nanos.length]; for (int i = 0; i < nanos.length; i++) values[i] = nanos[i] / divisor;
        double[] sorted = values.clone(); Arrays.sort(sorted);
        System.out.println("MEASURE " + label + " trials=" + Arrays.toString(values) + " median=" + sorted[sorted.length/2]
            + " min=" + sorted[0] + " max=" + sorted[sorted.length-1]);
    }
}
