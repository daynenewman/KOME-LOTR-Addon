package kome.common.data;

import kome.common.config.KOMEConfigRegistry;

import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** Authoritative KOM-71 live-boundary bottleneck and global catch-up ceiling. */
public final class KOMEPopulationDevelopmentService {
    private static final BigInteger RATE_SCALE =
        BigInteger.valueOf(KOMEPopulationRate.SCALE);
    private KOMEPopulationDevelopmentService() { }

    /** Startup always anchors/skips; development never replays offline boundaries. */
    public static Result initializeOrSkipStartup(KOMEWorldData data, Instant now) {
        try {
            require(data, now);
            KOMEConfigRegistry.ValidatedConfig config =
                KOMEConfigRegistry.requireReadySnapshot();
            KOMEConfigRegistry.PopulationSettings settings = config.getPopulation();
            KOMEDailyBoundary active = KOMEDailyBoundary.from(config.getDailyBatch());
            Instant anchor = active.latestBoundaryAtOrBefore(now);
            KOMEPopulationDevelopmentState state = data.populationDevelopment;
            if (state.initialized) requireCompatibleSettings(data, settings);
            boolean scheduleChanged = state.initialized
                && (!state.timezone.equals(active.timezone())
                    || !state.localTime.equals(active.localTime()));
            if (state.initialized && anchor.toEpochMilli() < state.lastLiveBoundaryMillis) {
                if (scheduleChanged) {
                    throw new IllegalStateException("Population development schedule change would move the "
                        + "persisted live-boundary cursor backward; restore the prior schedule or advance the clock.");
                }
                return Result.success(0L, state.rateCeilingUnits,
                    "Development cursor preserved because the server clock is behind the processed boundary.");
            }
            boolean changed = !state.initialized
                || state.lastLiveBoundaryMillis != anchor.toEpochMilli()
                || scheduleChanged;
            if (changed) publishAnchor(data, now, anchor, active, settings,
                state.initialized ? "STARTUP_SKIP" : "INITIALIZE");
            return Result.success(0L, state.rateCeilingUnits,
                changed ? "Offline development boundaries skipped." : "Development cursor already current.");
        } catch (RuntimeException failure) {
            return Result.failure(message(failure));
        }
    }

    public static Result processLiveDueBoundaries(KOMEWorldData data, Instant now) {
        long processed = 0L;
        try {
            require(data, now);
            KOMEConfigRegistry.ValidatedConfig config =
                KOMEConfigRegistry.requireReadySnapshot();
            KOMEConfigRegistry.PopulationSettings settings = config.getPopulation();
            KOMEPopulationDevelopmentState state = data.populationDevelopment;
            if (!state.initialized)
                throw new IllegalStateException("Population development startup anchor is missing.");
            requireCompatibleSettings(data, settings);
            KOMEDailyBoundary active = KOMEDailyBoundary.from(config.getDailyBatch());
            if (!state.schedule().signature().equals(active.signature()))
                throw new IllegalStateException("Population development schedule changed; startup reconciliation is required.");
            Instant latest = active.latestBoundaryAtOrBefore(now);
            Instant next = active.nextBoundary(
                Instant.ofEpochMilli(state.lastLiveBoundaryMillis));
            while (!next.isAfter(latest)) {
                processOne(data, next, active, settings);
                processed++;
                next = active.nextBoundary(next);
            }
            if (processed > 0L) data.syncConquestTiles();
            return Result.success(processed, state.rateCeilingUnits,
                processed == 0L ? "No live development boundary due." : "Live development processed.");
        } catch (RuntimeException failure) {
            return Result.failure(message(failure));
        }
    }

    public static void requireCompatibleSettings(KOMEWorldData data,
            KOMEConfigRegistry.PopulationSettings settings) {
        if (data == null || settings == null) throw new IllegalArgumentException(
            "Population development data and settings are required.");
        KOMEPopulationDevelopmentState state = data.populationDevelopment;
        if (state.initialized && state.hoursPerPopulationPointCentiHours
                != settings.getHoursPerPopulationPointCentiHours())
            throw new IllegalStateException("population.hoursPerPopulationPoint is locked at "
                + KOMEBuildTime.formatHours(state.hoursPerPopulationPointCentiHours)
                + " after population development initialization; reset/rebase is not implemented.");
    }

    private static void publishAnchor(KOMEWorldData data, Instant now, Instant anchor,
            KOMEDailyBoundary schedule, KOMEConfigRegistry.PopulationSettings settings,
            String action) {
        final KOMEAuditEntry audit = new KOMEAuditEntry(now.toEpochMilli(),
            "POPULATION_DEVELOPMENT", action, "SERVER", now.toString(),
            "Population development " + action.toLowerCase(java.util.Locale.ROOT),
            "ceiling=" + KOMEPopulationProjection.formatRate(
                data.populationDevelopment.rateCeilingUnits)
                + ";lastLiveBoundary=" + anchor + ";schedule=" + schedule.signature()
                + ";offlineDevelopment=false");
        data.publishPopulationDevelopment(new Runnable() {
            @Override public void run() {
                KOMEPopulationDevelopmentState state = data.populationDevelopment;
                state.initialized = true;
                state.lastLiveBoundaryMillis = anchor.toEpochMilli();
                state.timezone = schedule.timezone();
                state.localTime = schedule.localTime();
                state.hoursPerPopulationPointCentiHours =
                    settings.getHoursPerPopulationPointCentiHours();
                KOMEAuditService.appendPrepared(data, audit);
            }
        });
    }

    private static void processOne(final KOMEWorldData data, final Instant boundary,
            final KOMEDailyBoundary schedule,
            final KOMEConfigRegistry.PopulationSettings settings) {
        final List<KOMEPlayerBuild> eligible = eligiblePendingBuilds(data);
        final boolean ceilingAdvances =
            !settings.isPauseRateCeilingWhenNoPendingHours() || !eligible.isEmpty();
        final BigInteger nextCeiling = ceilingAdvances
            ? data.populationDevelopment.rateCeilingUnits.add(
                BigInteger.valueOf(settings.getBottleneckRateUnitsPerActiveServerDay()))
            : data.populationDevelopment.rateCeilingUnits;
        final Map<String, List<KOMEPlayerBuild>> byFaction =
            new TreeMap<String, List<KOMEPlayerBuild>>();
        for (KOMEPlayerBuild build : eligible) {
            List<KOMEPlayerBuild> builds = byFaction.get(build.populationFaction);
            if (builds == null) {
                builds = new ArrayList<KOMEPlayerBuild>();
                byFaction.put(build.populationFaction, builds);
            }
            builds.add(build);
        }
        final Map<String, BigInteger> nativeDevelopedCentiHours =
            nativeDevelopedCentiHours(data);
        final List<Allocation> allocations = new ArrayList<Allocation>();
        final Map<String, Long> nextRemainders =
            new TreeMap<String, Long>(data.populationDevelopment.factionCentiHourRemainders);
        for (Map.Entry<String, List<KOMEPlayerBuild>> entry : byFaction.entrySet()) {
            String faction = entry.getKey();
            long remainder = nextRemainders.containsKey(faction)
                ? nextRemainders.get(faction).longValue() : 0L;
            BigInteger developedCentiHours = nativeDevelopedCentiHours.containsKey(faction)
                ? nativeDevelopedCentiHours.get(faction) : BigInteger.ZERO;
            BigInteger developedNumerator = developedCentiHours.multiply(RATE_SCALE);
            BigInteger currentProgressNumerator = developedNumerator.add(
                BigInteger.valueOf(remainder));
            BigInteger ceilingTargetNumerator = nextCeiling.multiply(
                BigInteger.valueOf(settings.getHoursPerPopulationPointCentiHours()));
            BigInteger numerator;
            if (currentProgressNumerator.compareTo(ceilingTargetNumerator) < 0) {
                // Target minus persisted whole hours includes the already-earned
                // fractional remainder exactly once. It therefore reaches the ceiling
                // without dropping the remainder or adding another daily increment.
                numerator = ceilingTargetNumerator.subtract(developedNumerator);
            } else {
                numerator = BigInteger.valueOf(
                    settings.getBottleneckRateUnitsPerActiveServerDay())
                    .multiply(BigInteger.valueOf(
                        settings.getHoursPerPopulationPointCentiHours()))
                    .add(BigInteger.valueOf(remainder));
            }
            BigInteger[] split = numerator.divideAndRemainder(RATE_SCALE);
            BigInteger budget = split[0].min(totalPending(entry.getValue()));
            long nextRemainder = split[1].longValueExact();
            if (nextRemainder == 0L) nextRemainders.remove(faction);
            else nextRemainders.put(faction, Long.valueOf(nextRemainder));
            allocateOldestFirst(entry.getValue(), budget, allocations);
        }
        publishBoundary(data, boundary, schedule, settings, nextCeiling,
            ceilingAdvances, allocations, nextRemainders);
    }

    public static List<KOMEPlayerBuild> eligiblePendingBuilds(KOMEWorldData data) {
        List<KOMEPlayerBuild> result = new ArrayList<KOMEPlayerBuild>();
        if (data == null) return result;
        for (KOMEPlayerBuild build : KOMEBuildService.activeNormalBuilds(data)) {
            if (build.pendingNativeCentiHours() <= 0L) continue;
            KOMEConquestTile tile = data.conquestTiles.get(
                KOMEConquestTile.normalizeId(build.tileId));
            if (tile != null && build.populationFaction.equals(
                    KOMEAlliance.normalizeFactionKey(tile.projectRulingFaction())))
                result.add(build);
        }
        Collections.sort(result, new Comparator<KOMEPlayerBuild>() {
            @Override public int compare(KOMEPlayerBuild left, KOMEPlayerBuild right) {
                if (left.createdAtMillis < right.createdAtMillis) return -1;
                if (left.createdAtMillis > right.createdAtMillis) return 1;
                return left.id.compareTo(right.id);
            }
        });
        return result;
    }

    private static BigInteger totalPending(List<KOMEPlayerBuild> builds) {
        BigInteger result = BigInteger.ZERO;
        for (KOMEPlayerBuild build : builds)
            result = result.add(BigInteger.valueOf(build.pendingNativeCentiHours()));
        return result;
    }

    private static void allocateOldestFirst(List<KOMEPlayerBuild> builds, BigInteger budget,
            List<Allocation> allocations) {
        BigInteger remaining = budget == null || budget.signum() < 0
            ? BigInteger.ZERO : budget;
        for (KOMEPlayerBuild build : builds) {
            if (remaining.signum() == 0) break;
            BigInteger exactAmount = remaining.min(BigInteger.valueOf(
                build.pendingNativeCentiHours()));
            long amount = exactAmount.longValueExact();
            if (amount > 0L) {
                allocations.add(new Allocation(build, amount));
                remaining = remaining.subtract(exactAmount);
            }
        }
    }

    /** Exact native-controlled developed hours; captured production is deliberately excluded. */
    private static Map<String, BigInteger> nativeDevelopedCentiHours(KOMEWorldData data) {
        Map<String, BigInteger> result = new TreeMap<String, BigInteger>();
        for (KOMEPlayerBuild build : KOMEBuildService.activeNormalBuilds(data)) {
            KOMEConquestTile tile = data.conquestTiles.get(
                KOMEConquestTile.normalizeId(build.tileId));
            String nativeFaction = KOMEAlliance.normalizeFactionKey(build.populationFaction);
            if (tile == null || nativeFaction.length() == 0 || !nativeFaction.equals(
                    KOMEAlliance.normalizeFactionKey(tile.projectRulingFaction()))) continue;
            BigInteger prior = result.get(nativeFaction);
            result.put(nativeFaction, (prior == null ? BigInteger.ZERO : prior).add(
                BigInteger.valueOf(build.developedNativeCentiHours)));
        }
        return result;
    }

    private static void publishBoundary(final KOMEWorldData data, final Instant boundary,
            final KOMEDailyBoundary schedule,
            final KOMEConfigRegistry.PopulationSettings settings,
            final BigInteger nextCeiling, final boolean ceilingAdvanced,
            final List<Allocation> allocations, final Map<String, Long> nextRemainders) {
        StringBuilder detail = new StringBuilder();
        detail.append("ceiling=").append(KOMEPopulationProjection.formatRate(nextCeiling))
            .append(";ceilingAdvanced=").append(ceilingAdvanced)
            .append(";bottleneck=").append(settings.formatBottleneckRatePerActiveServerDay())
            .append(";allocations=");
        for (Allocation allocation : allocations) {
            if (detail.charAt(detail.length() - 1) != '=') detail.append(',');
            detail.append(allocation.build.id).append(':')
                .append(KOMEBuildTime.formatHours(allocation.centiHours));
        }
        final KOMEAuditEntry audit = new KOMEAuditEntry(boundary.toEpochMilli(),
            "POPULATION_DEVELOPMENT", "DEVELOP", "SERVER", boundary.toString(),
            "Population development boundary", detail.toString());
        data.publishPopulationDevelopment(new Runnable() {
            @Override public void run() {
                for (Allocation allocation : allocations) {
                    KOMEPlayerBuild build = allocation.build;
                    build.developedNativeCentiHours = Math.addExact(
                        build.developedNativeCentiHours, allocation.centiHours);
                    build.updatedAtMillis = Math.max(build.updatedAtMillis,
                        boundary.toEpochMilli());
                    build.appendAudit(boundary.toEpochMilli()
                        + "|DEVELOP|SERVER|SERVER|Population bottleneck conversion"
                        + "|developedCentiHoursDelta=" + allocation.centiHours
                        + ";developedCentiHours=" + build.developedNativeCentiHours
                        + ";pendingCentiHours=" + build.pendingNativeCentiHours());
                    build.validateContributions();
                }
                KOMEPopulationDevelopmentState state = data.populationDevelopment;
                state.rateCeilingUnits = nextCeiling;
                state.lastLiveBoundaryMillis = boundary.toEpochMilli();
                state.timezone = schedule.timezone();
                state.localTime = schedule.localTime();
                state.hoursPerPopulationPointCentiHours =
                    settings.getHoursPerPopulationPointCentiHours();
                state.factionCentiHourRemainders.clear();
                state.factionCentiHourRemainders.putAll(nextRemainders);
                KOMEAuditService.appendPrepared(data, audit);
            }
        });
    }

    public static void clampDevelopedToApproved(KOMEWorldData data,
            KOMEPlayerBuild build, UUID actor, String actorName, long nowMillis) {
        if (data == null || build == null || !build.isNormal()) return;
        long approved = build.approvedCentiHours();
        if (build.developedNativeCentiHours <= approved) return;
        long before = build.developedNativeCentiHours;
        build.developedNativeCentiHours = approved;
        String details = "build=" + build.id + ";developedCentiHoursBefore=" + before
            + ";developedCentiHoursAfter=" + approved
            + ";approvedCentiHours=" + approved;
        build.appendAudit(Math.max(0L, nowMillis)
            + "|DEVELOPED_CLAMP|" + (actor == null ? "" : actor.toString())
            + "|" + (actorName == null ? "" : actorName)
            + "|Approved hours reduced below developed hours|" + details);
        KOMEAuditService.record(data, nowMillis, "POPULATION_DEVELOPMENT",
            "DEVELOPED_CLAMP", actor == null ? "" : actor.toString(), build.id,
            "Approved hours reduced below developed hours", details);
        data.markDirty();
    }

    public static List<String> inspection(KOMEWorldData data) {
        List<String> result = new ArrayList<String>();
        KOMEPopulationDevelopmentState state = data.populationDevelopment;
        result.add("Development initialized=" + state.initialized
            + "; Rate Ceiling=" + KOMEPopulationProjection.formatRate(
                state.rateCeilingUnits));
        if (state.initialized) {
            Instant last = Instant.ofEpochMilli(state.lastLiveBoundaryMillis);
            result.add("Last live development boundary=" + last + "; next="
                + state.schedule().nextBoundary(last)
                + "; schedule=" + state.schedule().signature());
        }
        result.add("Eligible Pending Builds=" + eligiblePendingBuilds(data).size()
            + "; ceilingPaused=" + (KOMEConfigRegistry.population()
                .isPauseRateCeilingWhenNoPendingHours()
                && eligiblePendingBuilds(data).isEmpty()));
        return result;
    }

    private static final class Allocation {
        final KOMEPlayerBuild build;
        final long centiHours;
        Allocation(KOMEPlayerBuild build, long centiHours) {
            this.build = build; this.centiHours = centiHours;
        }
    }

    public static final class Result {
        public final boolean success;
        public final long processedBoundaries;
        public final BigInteger rateCeilingUnits;
        public final String message;
        private Result(boolean ok, long count, BigInteger ceiling, String text) {
            success = ok; processedBoundaries = count; rateCeilingUnits = ceiling;
            message = text == null ? "" : text;
        }
        static Result success(long count, BigInteger ceiling, String text) {
            return new Result(true, count, ceiling, text);
        }
        static Result failure(String text) {
            return new Result(false, 0L, BigInteger.ZERO, text);
        }
    }

    private static void require(KOMEWorldData data, Instant now) {
        if (data == null || now == null)
            throw new IllegalArgumentException("World data and development time are required.");
        data.ensureWritable();
        data.populationDevelopment.validate();
    }

    private static String message(RuntimeException failure) {
        return failure.getMessage() == null ? failure.getClass().getSimpleName()
            : failure.getMessage();
    }
}
