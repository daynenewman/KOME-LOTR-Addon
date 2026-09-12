package kome.common.data;

import kome.common.config.KOMEConfigRegistry;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Deterministic, caller-driven daily boundary processing. Runtime wiring is deliberately absent. */
public final class KOMEPopulationPayoutProcessor {
    private KOMEPopulationPayoutProcessor() { }
    public static Result initializeOrProcessStartup(KOMEWorldData data, Instant now) {
        if (!data.populationPayoutInitialized) return initialize(data, now);
        return KOMEConfigRegistry.population().isOfflinePopulationCatchUp() ? processDue(data, now, true) : skipToLatest(data, now);
    }
    public static Result processLiveDueBoundaries(KOMEWorldData data, Instant now) { return processDue(data, now, false); }
    public static Instant latestBoundaryAtOrBefore(Instant now) {
        ZoneId zone = KOMEConfigRegistry.dailyBatch().getTimezone();
        java.time.LocalTime time = KOMEConfigRegistry.dailyBatch().getLocalTime();
        ZonedDateTime local = now.atZone(zone); LocalDate date = local.toLocalDate();
        ZonedDateTime boundary = ZonedDateTime.of(LocalDateTime.of(date, time), zone);
        if (boundary.toInstant().isAfter(now)) boundary = ZonedDateTime.of(LocalDateTime.of(date.minusDays(1), time), zone);
        return boundary.toInstant();
    }
    public static Instant nextBoundary(Instant boundary) {
        ZoneId zone = KOMEConfigRegistry.dailyBatch().getTimezone();
        return ZonedDateTime.of(LocalDateTime.of(boundary.atZone(zone).toLocalDate().plusDays(1), KOMEConfigRegistry.dailyBatch().getLocalTime()), zone).toInstant();
    }
    private static Result initialize(KOMEWorldData data, Instant now) {
        data.populationPayoutInitialized = true; data.lastPopulationPayoutBoundaryMillis = latestBoundaryAtOrBefore(now).toEpochMilli(); data.markDirty();
        return new Result(Collections.<FactionResult>emptyList(), true, false, "initialized");
    }
    private static Result skipToLatest(KOMEWorldData data, Instant now) {
        Instant latest = latestBoundaryAtOrBefore(now); data.lastPopulationPayoutBoundaryMillis = latest.toEpochMilli(); data.markDirty();
        return new Result(Collections.<FactionResult>emptyList(), false, true, "offline boundaries skipped");
    }
    private static Result processDue(KOMEWorldData data, Instant now, boolean startup) {
        if (!data.populationPayoutInitialized) return initialize(data, now);
        Instant latest = latestBoundaryAtOrBefore(now); Instant next = nextBoundary(Instant.ofEpochMilli(data.lastPopulationPayoutBoundaryMillis));
        List<FactionResult> all = new ArrayList<FactionResult>();
        while (!next.isAfter(latest)) {
            Result one = processOne(data, next); if (!one.success) return new Result(all, false, false, one.message);
            all.addAll(one.factions); next = nextBoundary(next);
        }
        return new Result(all, false, false, startup ? "startup processed" : "live processed");
    }
    private static Result processOne(KOMEWorldData data, Instant boundary) {
        Map<String,KOMEPopulationRate> rates = new TreeMap<String,KOMEPopulationRate>(KOMEPopulationService.getAllDailyPopulationRates(data));
        List<FactionResult> plan = new ArrayList<FactionResult>();
        boolean cap = KOMEConfigRegistry.population().isPopulationCapEnabled();
        int capValue = KOMEConfigRegistry.population().getPopulationCapValue().orElse(Integer.MAX_VALUE);
        for (Map.Entry<String,KOMEPopulationRate> e : rates.entrySet()) {
            long prior = data.populationPayoutRemainders.containsKey(e.getKey()) ? data.populationPayoutRemainders.get(e.getKey()).longValue() : 0L;
            long accrued = prior > Long.MAX_VALUE - e.getValue().getFixedUnitsPerDay() ? Long.MAX_VALUE : prior + e.getValue().getFixedUnitsPerDay();
            long whole = accrued / KOMEPopulationRate.SCALE, remainder = accrued % KOMEPopulationRate.SCALE;
            int bank = KOMEPopulationService.getAvailablePopulation(data, e.getKey());
            long grant = whole, blocked = 0L;
            if (cap && bank < capValue && grant > capValue - bank) { blocked = grant - (capValue - bank); grant = capValue - bank; }
            else if (cap && bank >= capValue) { blocked = grant; grant = 0L; }
            if (grant > Integer.MAX_VALUE || grant > Integer.MAX_VALUE - bank) return new Result(Collections.<FactionResult>emptyList(), false, false, "population bank overflow at " + e.getKey());
            plan.add(new FactionResult(e.getKey(), e.getValue().getFixedUnitsPerDay(), prior, whole, (int) grant, remainder, blocked));
        }
        for (FactionResult row : plan) { if (row.granted > 0) data.grantFactionPopulation(row.faction, row.granted); if (row.remainder == 0L) data.populationPayoutRemainders.remove(row.faction); else data.populationPayoutRemainders.put(row.faction, Long.valueOf(row.remainder)); }
        data.lastPopulationPayoutBoundaryMillis = boundary.toEpochMilli(); data.markDirty(); return new Result(plan, false, false, "processed");
    }
    public static final class FactionResult { public final String faction; public final long rate, priorRemainder, generated, remainder, capBlocked; public final int granted; FactionResult(String f,long r,long p,long g,int a,long n,long b){faction=f;rate=r;priorRemainder=p;generated=g;granted=a;remainder=n;capBlocked=b;} }
    public static final class Result { public final List<FactionResult> factions; public final boolean initialized, skipped, success; public final String message; Result(List<FactionResult> f,boolean i,boolean s,String m){factions=Collections.unmodifiableList(new ArrayList<FactionResult>(f));initialized=i;skipped=s;message=m;success=!m.startsWith("population bank overflow");} }
}
