package kome.common.data;

import kome.common.config.KOMEConfigRegistry;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** Server-thread, caller-driven, atomic daily population boundaries. No wall-clock worker. */
public final class KOMEPopulationPayoutProcessor {
    public static final long RATE_UNITS_PER_CENTI = rateUnitsPerCenti();
    private static final BigInteger CENTI_DIVISOR = BigInteger.valueOf(RATE_UNITS_PER_CENTI);
    private KOMEPopulationPayoutProcessor() { }

    private static long rateUnitsPerCenti() {
        if (KOMEPopulationService.CENTI_PER_POPULATION <= 0L || KOMEPopulationRate.SCALE <= 0L
                || KOMEPopulationRate.SCALE % KOMEPopulationService.CENTI_PER_POPULATION != 0L) {
            throw new IllegalStateException("Population rate and centi-bank scales must divide exactly");
        }
        return KOMEPopulationRate.SCALE / KOMEPopulationService.CENTI_PER_POPULATION;
    }

    public static Result initializeOrProcessStartup(KOMEWorldData data, Instant now) { return process(data, now, true); }
    public static Result processLiveDueBoundaries(KOMEWorldData data, Instant now) { return process(data, now, false); }
    public static Instant latestBoundaryAtOrBefore(Instant now) { return activeSchedule().latestBoundaryAtOrBefore(now); }
    public static Instant nextBoundary(Instant boundary) { return activeSchedule().nextBoundary(boundary); }
    private static KOMEDailyBoundary activeSchedule() {
        return KOMEDailyBoundary.from(KOMEConfigRegistry.requireReadySnapshot().getDailyBatch());
    }

    private static Result process(KOMEWorldData data, Instant now, boolean startup) {
        List<FactionResult> rows = new ArrayList<FactionResult>();
        long processed = 0L;
        long committedTransitions = 0L;
        boolean skippedCommitted = false;
        try {
            if (data == null || now == null) throw new IllegalArgumentException("World data and payout time are required");
            KOMEConfigRegistry.ValidatedConfig config = KOMEConfigRegistry.requireReadySnapshot();
            data.ensureWritable();
            data.validatePopulationPayoutState();
            KOMEDailyBoundary active = KOMEDailyBoundary.from(config.getDailyBatch());
            Instant newAnchor = active.latestBoundaryAtOrBefore(now);
            newAnchor.toEpochMilli();
            active.nextBoundary(newAnchor).toEpochMilli();
            if (!data.populationPayoutInitialized) {
                publish(data, now, newAnchor.toEpochMilli(), "INITIALIZED", active,
                        "Fresh anchor; no historical payout", config, Collections.<FactionResult>emptyList());
                return success(data, rows, 0L, 1L, true, false, "initialized " + active.signature());
            }
            KOMEDailyBoundary persisted = data.populationPayoutSchedule();
            boolean changed = !persisted.signature().equals(active.signature());
            if (changed && !startup) throw new IllegalStateException("Payout schedule changed; startup reconciliation is required");
            // Always interpret the old cursor with its own persisted schedule.
            Instant latest = persisted.latestBoundaryAtOrBefore(now);
            Instant cursor = Instant.ofEpochMilli(data.lastPopulationPayoutBoundaryMillis);
            boolean skip = startup && !config.getPopulation().isOfflinePopulationCatchUp();
            if (skip && latest.isAfter(cursor)) {
                latest.toEpochMilli();
                persisted.nextBoundary(latest).toEpochMilli();
                publish(data, now, latest.toEpochMilli(), "SKIPPED", persisted,
                        "Offline boundaries through " + latest + " consumed without accrual", config, Collections.<FactionResult>emptyList());
                committedTransitions++;
                skippedCommitted = true;
            } else if (!skip) {
                Instant next = persisted.nextBoundary(cursor);
                while (!next.isAfter(latest)) {
                    // Each boundary commits separately; a later failure retains earlier complete commits.
                    rows.addAll(processOne(data, next, persisted, config));
                    processed++;
                    committedTransitions++;
                    next = persisted.nextBoundary(next);
                }
            }
            if (changed) {
                publish(data, now, newAnchor.toEpochMilli(), "RECONCILED", active,
                        "old=" + persisted.signature() + "; new=" + active.signature()
                        + "; old boundaries " + (skip ? "skipped" : "processed") + "; unpaid new anchor=" + newAnchor,
                        config, Collections.<FactionResult>emptyList());
                committedTransitions++;
            }
            return success(data, rows, processed, committedTransitions, false, skippedCommitted,
                    changed ? "schedule reconciled" : skip ? "offline boundaries skipped" : startup ? "startup processed" : "live processed");
        } catch (RuntimeException failure) {
            String message = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
            // Transient diagnostic only; never persist a rejected plan as successful gameplay state.
            if (data != null) data.populationPayoutLastFailure = message;
            return new Result(rows, processed, committedTransitions, false, false, skippedCommitted, message);
        }
    }

    private static List<FactionResult> processOne(KOMEWorldData data, Instant boundary,
            KOMEDailyBoundary schedule, KOMEConfigRegistry.ValidatedConfig config) {
        long boundaryMillis = boundary.toEpochMilli();
        schedule.nextBoundary(boundary).toEpochMilli(); // preflight the following cursor
        if (!data.warSeason.isPopulationPayoutEnabled()) {
            publish(data, boundary, boundaryMillis, "FROZEN", schedule,
                    "phase=" + data.warSeason.phase + "; no accrual; prior remainders preserved",
                    config, Collections.<FactionResult>emptyList());
            return Collections.emptyList();
        }
        KOMEConfigRegistry.PopulationSettings settings = config.getPopulation();
        Map<String, BigInteger> rates = KOMEPopulationRateService.getExactDailyPopulationRates(data, settings);
        TreeSet<String> factions = new TreeSet<String>(rates.keySet());
        factions.addAll(data.populationPayoutRemainders.keySet());
        List<FactionResult> plan = new ArrayList<FactionResult>();
        for (String faction : factions) {
            try {
                long prior = data.populationPayoutRemainders.containsKey(faction) ? data.populationPayoutRemainders.get(faction) : 0L;
                BigInteger rate = rates.containsKey(faction) ? rates.get(faction) : BigInteger.ZERO;
                BigInteger[] split = rate.add(BigInteger.valueOf(prior)).divideAndRemainder(CENTI_DIVISOR);
                long bank = KOMEPopulationService.getAvailablePopulationCenti(data, faction);
                BigInteger grant = split[0];
                if (settings.isPopulationCapEnabled()) {
                    long cap = settings.getPopulationCapCenti().getAsLong();
                    long room = bank >= cap ? 0L : Math.subtractExact(cap, bank);
                    grant = grant.min(BigInteger.valueOf(room));
                }
                long grantCenti = grant.longValueExact();
                long after = Math.addExact(bank, grantCenti);
                long remainder = split[1].longValueExact();
                plan.add(new FactionResult(faction, rate, prior, split[0], grantCenti,
                        split[0].subtract(grant), remainder, after));
            } catch (RuntimeException invalid) {
                throw new IllegalArgumentException("Population payout plan failed for faction " + faction
                        + " (centi grant/bank or rate remainder): " + invalid, invalid);
            }
        }
        publish(data, boundary, boundaryMillis, "PAID", schedule, plan.toString(), config, plan);
        return plan;
    }

    private static Result success(KOMEWorldData data, List<FactionResult> rows, long count, long committedTransitions,
            boolean initialized, boolean skipped, String message) {
        data.populationPayoutLastFailure = "";
        return new Result(rows, count, committedTransitions, true, initialized, skipped, message);
    }

    /** Prepares all audit text before the single rollback-protected publication boundary. */
    private static void publish(KOMEWorldData data, Instant instant, long cursor, String action, KOMEDailyBoundary schedule,
            String details, KOMEConfigRegistry.ValidatedConfig config, List<FactionResult> plan) {
        KOMEConfigRegistry.PopulationSettings p = config.getPopulation();
        String effective = "schedule=" + schedule.signature() + "; hoursPerPoint=" + p.formatHoursPerPopulationPoint()
                + "; capturedMultiplier=" + p.formatCapturedBuildMultiplier() + "; capEnabled=" + p.isPopulationCapEnabled()
                + "; cap=" + p.formatPopulationCap() + "; catchUp=" + p.isOfflinePopulationCatchUp()
                + "; rateUnitsPerCenti=" + RATE_UNITS_PER_CENTI + "; " + details;
        KOMEAuditEntry entry = new KOMEAuditEntry(instant.toEpochMilli(), "POPULATION", action, "SERVER",
                instant.toString(), "Population " + action, effective);
        String timezone = schedule.timezone(), localTime = schedule.localTime();
        String consoleLine = "[KOME POPULATION] " + action + " " + instant + " " + effective;
        data.publishPopulationPayout(() -> {
            for (FactionResult row : plan) {
                if (row.grantedCenti > 0L) KOMEPopulationService.grantCenti(data, row.faction, row.grantedCenti);
            }
            for (FactionResult row : plan) {
                if (row.nextRemainderUnits == 0L) data.populationPayoutRemainders.remove(row.faction);
                else data.populationPayoutRemainders.put(row.faction, row.nextRemainderUnits);
            }
            data.lastPopulationPayoutBoundaryMillis = cursor;
            data.populationPayoutTimezone = timezone;
            data.populationPayoutLocalTime = localTime;
            data.populationPayoutInitialized = true;
            KOMEAuditService.appendPrepared(data, entry);
        });
        // Console output is not persisted gameplay. It cannot reject an already committed transition.
        try {
            System.out.println(consoleLine);
        } catch (RuntimeException loggingFailure) {
            try {
                System.err.println("[KOME POPULATION] Committed " + action + "; console logging failed: " + loggingFailure);
            } catch (RuntimeException unavailableConsole) {
                // Both consoles are unavailable; the persisted central audit already records the commit.
            }
        }
    }

    /** Extends existing admin population inspection; reads only maintained payout state. */
    public static List<String> inspection(KOMEWorldData data) {
        List<String> lines = new ArrayList<String>();
        KOMEConfigRegistry.ValidatedConfig config = KOMEConfigRegistry.requireReadySnapshot();
        lines.add("Payout initialized=" + data.populationPayoutInitialized + "; active schedule="
                + KOMEDailyBoundary.from(config.getDailyBatch()).signature() + "; catchUp=" + config.getPopulation().isOfflinePopulationCatchUp());
        if (data.populationPayoutInitialized) {
            KOMEDailyBoundary schedule = data.populationPayoutSchedule();
            Instant last = Instant.ofEpochMilli(data.lastPopulationPayoutBoundaryMillis), next = schedule.nextBoundary(last);
            lines.add("Cursor schedule=" + schedule.signature() + "; last=" + last + " (" + schedule.localDescription(last)
                    + "); next=" + next + " (" + schedule.localDescription(next) + ")");
        }
        lines.add("Remainders in rate units (" + RATE_UNITS_PER_CENTI + " per centi): " + new java.util.TreeMap<String, Long>(data.populationPayoutRemainders));
        lines.add("Latest payout failure=" + (data.populationPayoutLastFailure.isEmpty() ? "none" : data.populationPayoutLastFailure));
        for (KOMEAuditEntry entry : data.centralAudit) if ("POPULATION".equals(entry.domain))
            lines.add(entry.action + " " + entry.subject + " " + entry.details);
        return lines;
    }

    public static final class FactionResult {
        public final String faction;
        public final BigInteger exactRateUnits, generatedCenti, blockedCenti;
        public final long priorRemainderUnits, grantedCenti, nextRemainderUnits, resultingBankCenti;
        FactionResult(String faction, BigInteger rate, long prior, BigInteger generated, long granted,
                BigInteger blocked, long remainder, long bank) {
            this.faction = faction; exactRateUnits = rate; priorRemainderUnits = prior; generatedCenti = generated;
            grantedCenti = granted; blockedCenti = blocked; nextRemainderUnits = remainder; resultingBankCenti = bank;
        }
        @Override public String toString() {
            return faction + " rateUnits=" + exactRateUnits + " priorRemainderUnits=" + priorRemainderUnits
                    + " generatedCenti=" + generatedCenti + " grantedCenti=" + grantedCenti + " blockedCenti=" + blockedCenti
                    + " nextRemainderUnits=" + nextRemainderUnits + " bankCenti=" + resultingBankCenti
                    + " (population " + BigDecimal.valueOf(resultingBankCenti, 2).toPlainString() + ")";
        }
    }

    public static final class Result {
        public final List<FactionResult> factions;
        public final long processedBoundaries;
        /** Includes anchors/skips as well as boundaries; earlier catch-up commits survive a later failure. */
        public final long committedTransitions;
        public final boolean success, initialized, skipped;
        public final String message;
        Result(List<FactionResult> rows, long count, long committedTransitions, boolean success, boolean initialized, boolean skipped, String message) {
            factions = Collections.unmodifiableList(new ArrayList<FactionResult>(rows)); processedBoundaries = count;
            this.committedTransitions = committedTransitions;
            this.success = success; this.initialized = initialized; this.skipped = skipped; this.message = message;
        }
    }
}
