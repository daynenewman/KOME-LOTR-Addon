package com.enovak.lotrmoremobs.util;

import com.enovak.lotrmoremobs.entity.animal.LOTREntityMumakil;
import com.enovak.lotrmoremobs.entity.npc.LOTREntityMumakilHowdahArcher;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.World;

/**
 * Low-overhead, world-level timing for Mumak server work. Reports are
 * aggregated so profiling does not add per-entity console traffic.
 */
public final class MumakilServerPerformanceDiagnostics {
    public static final boolean ENABLED = false;
    public static final long REPORT_INTERVAL_TICKS = 1200L;

    private static final Map<World, Metrics> METRICS_BY_WORLD =
            new IdentityHashMap<World, Metrics>();

    private MumakilServerPerformanceDiagnostics() {
    }

    public static long startTimer(World world) {
        return isServerWorld(world) ? System.nanoTime() : 0L;
    }

    public static void recordHomeClassification(
            World world,
            long nanos
    ) {
        metricsFor(world).homeClassification.add(nanos);
    }

    public static void recordBiomeSpawnListLookup(
            World world,
            long nanos
    ) {
        metricsFor(world).biomeSpawnListLookup.add(nanos);
    }

    public static void recordHomeQueueProcessing(
            World world,
            long nanos
    ) {
        metricsFor(world).homeQueueProcessing.add(nanos);
    }

    public static void recordBlockClearance(
            World world,
            long nanos
    ) {
        metricsFor(world).blockClearance.add(nanos);
    }

    public static void recordEntityClearance(
            World world,
            long nanos
    ) {
        metricsFor(world).entityClearance.add(nanos);
    }

    public static void recordNearbyPositionSearch(
            World world,
            long nanos
    ) {
        metricsFor(world).nearbyPositionSearch.add(nanos);
    }

    public static void recordFormationFactory(
            World world,
            long nanos
    ) {
        metricsFor(world).formationFactory.add(nanos);
    }

    public static void recordPassengerMaintenance(
            World world,
            long nanos
    ) {
        metricsFor(world).passengerMaintenance.add(nanos);
    }

    public static void recordRosterMaintenance(
            World world,
            long nanos
    ) {
        metricsFor(world).rosterMaintenance.add(nanos);
    }

    public static void recordArcherTargetScan(
            World world,
            long nanos
    ) {
        metricsFor(world).archerTargetScan.add(nanos);
    }

    public static void recordDriverTargetScan(
            World world,
            long nanos
    ) {
        metricsFor(world).driverTargetScan.add(nanos);
    }

    public static void recordAutonomousTargetAcquisition(
            World world,
            long nanos,
            boolean acquired
    ) {
        Metrics metrics = metricsFor(world);
        metrics.autonomousTargetAcquisition.add(nanos);
        if (acquired) {
            ++metrics.autonomousTargetsAcquired;
        }
    }

    public static void recordAutonomousTargetReplacement(World world) {
        ++metricsFor(world).autonomousTargetsReplaced;
    }

    public static void recordAutonomousPursuitPath(
            World world,
            long nanos,
            boolean accepted
    ) {
        Metrics metrics = metricsFor(world);
        metrics.autonomousPursuitPath.add(nanos);
        if (accepted) {
            ++metrics.autonomousPursuitPathsAccepted;
        }
    }

    public static void recordPassThroughSearch(
            World world,
            long nanos
    ) {
        metricsFor(world).passThroughSearch.add(nanos);
    }

    public static void recordPassThroughPath(
            World world,
            long nanos,
            boolean accepted
    ) {
        Metrics metrics = metricsFor(world);
        metrics.passThroughPath.add(nanos);
        if (accepted) {
            ++metrics.passThroughWaypointsAccepted;
        }
    }

    public static void recordTurnaroundSearch(
            World world,
            long nanos
    ) {
        metricsFor(world).turnaroundSearch.add(nanos);
    }

    public static void recordTurnaroundPath(
            World world,
            long nanos,
            boolean accepted
    ) {
        Metrics metrics = metricsFor(world);
        metrics.turnaroundPath.add(nanos);
        if (accepted) {
            ++metrics.turnaroundWaypointsAccepted;
        }
    }

    public static void recordTramplePassTransition(World world) {
        ++metricsFor(world).tramplePassTransitions;
    }

    public static void recordPassWaypointFailure(World world) {
        ++metricsFor(world).passWaypointFailures;
    }

    public static void recordMakeWayScan(
            World world,
            long nanos
    ) {
        metricsFor(world).makeWayScan.add(nanos);
    }

    public static void recordCollisionScan(
            World world,
            long nanos
    ) {
        metricsFor(world).collisionScan.add(nanos);
    }

    public static void recordLeafBreakingScan(
            World world,
            long nanos
    ) {
        metricsFor(world).leafBreakingScan.add(nanos);
    }

    public static void recordHomeCandidateProcessed(World world) {
        ++metricsFor(world).homeCandidatesProcessed;
    }

    public static void recordHomeFormationSucceeded(World world) {
        ++metricsFor(world).homeFormationsSucceeded;
    }

    public static void reportIfDue(World world, boolean force) {
        if (!isServerWorld(world)) {
            return;
        }

        Metrics metrics = METRICS_BY_WORLD.get(world);
        if (metrics == null) {
            return;
        }

        long now = world.getTotalWorldTime();
        if (!force && now < metrics.nextReportTick) {
            return;
        }

        int loadedMumaks = 0;
        int loadedAttachedArchers = 0;
        List loaded = world.loadedEntityList;
        for (int i = 0; i < loaded.size(); ++i) {
            Object entity = loaded.get(i);
            if (entity instanceof LOTREntityMumakil) {
                ++loadedMumaks;
            } else if (entity
                    instanceof LOTREntityMumakilHowdahArcher
                    && ((LOTREntityMumakilHowdahArcher)entity)
                    .isRuntimeHowdahPassenger()) {
                ++loadedAttachedArchers;
            }
        }

        StringBuilder message = new StringBuilder(640);
        message.append("[LOTRMoreMobs] Mumak server timing")
                .append(" dim=")
                .append(world.provider.dimensionId)
                .append(" ticks=")
                .append(Math.max(0L, now - metrics.windowStartTick))
                .append(" loadedMumaks=")
                .append(loadedMumaks)
                .append(" attachedArchers=")
                .append(loadedAttachedArchers)
                .append(" homeProcessed=")
                .append(metrics.homeCandidatesProcessed)
                .append(" homeSuccess=")
                .append(metrics.homeFormationsSucceeded)
                .append(" autonomousTargetsAcquired=")
                .append(metrics.autonomousTargetsAcquired)
                .append(" autonomousTargetsReplaced=")
                .append(metrics.autonomousTargetsReplaced)
                .append(" autonomousPursuitAccepted=")
                .append(metrics.autonomousPursuitPathsAccepted)
                .append(" tramplePassTransitions=")
                .append(metrics.tramplePassTransitions)
                .append(" passThroughWaypointsAccepted=")
                .append(metrics.passThroughWaypointsAccepted)
                .append(" turnaroundWaypointsAccepted=")
                .append(metrics.turnaroundWaypointsAccepted)
                .append(" passWaypointFailures=")
                .append(metrics.passWaypointFailures);
        appendTiming(
                message,
                "classify",
                metrics.homeClassification
        );
        appendTiming(
                message,
                "biomeLookup",
                metrics.biomeSpawnListLookup
        );
        appendTiming(
                message,
                "queue",
                metrics.homeQueueProcessing
        );
        appendTiming(
                message,
                "blockClear",
                metrics.blockClearance
        );
        appendTiming(
                message,
                "entityClear",
                metrics.entityClearance
        );
        appendTiming(
                message,
                "positionSearch",
                metrics.nearbyPositionSearch
        );
        appendTiming(
                message,
                "factory",
                metrics.formationFactory
        );
        appendTiming(
                message,
                "passenger",
                metrics.passengerMaintenance
        );
        appendTiming(
                message,
                "roster",
                metrics.rosterMaintenance
        );
        appendTiming(
                message,
                "archerTarget",
                metrics.archerTargetScan
        );
        appendTiming(
                message,
                "driverTarget",
                metrics.driverTargetScan
        );
        appendTiming(
                message,
                "autoAcquire",
                metrics.autonomousTargetAcquisition
        );
        appendTiming(
                message,
                "autoPursuit",
                metrics.autonomousPursuitPath
        );
        appendTiming(
                message,
                "passSearch",
                metrics.passThroughSearch
        );
        appendTiming(
                message,
                "passPath",
                metrics.passThroughPath
        );
        appendTiming(
                message,
                "turnSearch",
                metrics.turnaroundSearch
        );
        appendTiming(
                message,
                "turnPath",
                metrics.turnaroundPath
        );
        appendTiming(
                message,
                "makeWay",
                metrics.makeWayScan
        );
        appendTiming(
                message,
                "collision",
                metrics.collisionScan
        );
        appendTiming(
                message,
                "leaf",
                metrics.leafBreakingScan
        );
        System.out.println(message.toString());

        metrics.reset(now);
    }

    public static void onWorldUnload(World world) {
        if (!isServerWorld(world)) {
            return;
        }
        reportIfDue(world, true);
        METRICS_BY_WORLD.remove(world);
    }

    private static void appendTiming(
            StringBuilder message,
            String name,
            Timing timing
    ) {
        long totalMicros = timing.totalNanos / 1000L;
        long averageMicros = timing.calls == 0L
                ? 0L
                : totalMicros / timing.calls;
        message.append(' ')
                .append(name)
                .append("Calls=")
                .append(timing.calls)
                .append(' ')
                .append(name)
                .append("TotalUs=")
                .append(totalMicros)
                .append(' ')
                .append(name)
                .append("AvgUs=")
                .append(averageMicros);
    }

    private static Metrics metricsFor(World world) {
        if (!isServerWorld(world)) {
            return Metrics.NOOP;
        }

        Metrics metrics = METRICS_BY_WORLD.get(world);
        if (metrics == null) {
            metrics = new Metrics(world.getTotalWorldTime());
            METRICS_BY_WORLD.put(world, metrics);
        }
        return metrics;
    }

    private static boolean isServerWorld(World world) {
        return ENABLED && world != null && !world.isRemote;
    }

    private static final class Timing {
        private long calls;
        private long totalNanos;

        private void add(long nanos) {
            if (nanos < 0L) {
                return;
            }
            ++this.calls;
            this.totalNanos += nanos;
        }

        private void reset() {
            this.calls = 0L;
            this.totalNanos = 0L;
        }
    }

    private static final class Metrics {
        private static final Metrics NOOP = new Metrics(0L);

        private final Timing homeClassification = new Timing();
        private final Timing biomeSpawnListLookup = new Timing();
        private final Timing homeQueueProcessing = new Timing();
        private final Timing blockClearance = new Timing();
        private final Timing entityClearance = new Timing();
        private final Timing nearbyPositionSearch = new Timing();
        private final Timing formationFactory = new Timing();
        private final Timing passengerMaintenance = new Timing();
        private final Timing rosterMaintenance = new Timing();
        private final Timing archerTargetScan = new Timing();
        private final Timing driverTargetScan = new Timing();
        private final Timing autonomousTargetAcquisition = new Timing();
        private final Timing autonomousPursuitPath = new Timing();
        private final Timing passThroughSearch = new Timing();
        private final Timing passThroughPath = new Timing();
        private final Timing turnaroundSearch = new Timing();
        private final Timing turnaroundPath = new Timing();
        private final Timing makeWayScan = new Timing();
        private final Timing collisionScan = new Timing();
        private final Timing leafBreakingScan = new Timing();
        private long windowStartTick;
        private long nextReportTick;
        private int homeCandidatesProcessed;
        private int homeFormationsSucceeded;
        private int autonomousTargetsAcquired;
        private int autonomousTargetsReplaced;
        private int autonomousPursuitPathsAccepted;
        private int tramplePassTransitions;
        private int passThroughWaypointsAccepted;
        private int turnaroundWaypointsAccepted;
        private int passWaypointFailures;

        private Metrics(long now) {
            this.windowStartTick = now;
            this.nextReportTick = now + REPORT_INTERVAL_TICKS;
        }

        private void reset(long now) {
            this.homeClassification.reset();
            this.biomeSpawnListLookup.reset();
            this.homeQueueProcessing.reset();
            this.blockClearance.reset();
            this.entityClearance.reset();
            this.nearbyPositionSearch.reset();
            this.formationFactory.reset();
            this.passengerMaintenance.reset();
            this.rosterMaintenance.reset();
            this.archerTargetScan.reset();
            this.driverTargetScan.reset();
            this.autonomousTargetAcquisition.reset();
            this.autonomousPursuitPath.reset();
            this.passThroughSearch.reset();
            this.passThroughPath.reset();
            this.turnaroundSearch.reset();
            this.turnaroundPath.reset();
            this.makeWayScan.reset();
            this.collisionScan.reset();
            this.leafBreakingScan.reset();
            this.homeCandidatesProcessed = 0;
            this.homeFormationsSucceeded = 0;
            this.autonomousTargetsAcquired = 0;
            this.autonomousTargetsReplaced = 0;
            this.autonomousPursuitPathsAccepted = 0;
            this.tramplePassTransitions = 0;
            this.passThroughWaypointsAccepted = 0;
            this.turnaroundWaypointsAccepted = 0;
            this.passWaypointFailures = 0;
            this.windowStartTick = now;
            this.nextReportTick = now + REPORT_INTERVAL_TICKS;
        }
    }
}
