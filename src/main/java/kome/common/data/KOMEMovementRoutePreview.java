package kome.common.data;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;

/** Server-authoritative movement timing and stale-confirmation snapshot. */
public final class KOMEMovementRoutePreview {
    public static final String READY_NOW = "READY_NOW";
    public static final String NO_ALLOWANCE = "NO_ALLOWANCE";
    public static final String CONFLICT_HELD = "CONFLICT_HELD";
    public static final String ROUTE_BLOCKED = "ROUTE_BLOCKED";
    public static final String ARRIVAL_UNAVAILABLE = "ARRIVAL_UNAVAILABLE";
    public static final String STALE_PREVIEW = "STALE_PREVIEW";

    private KOMEMovementRoutePreview() { }

    public static Timing timing(KOMEArmyCompany company, int distanceTiles, long nowMillis) {
        if (company == null) throw new IllegalArgumentException("Company is required");
        int distance = Math.max(0, distanceTiles);
        int entitlement = Math.max(1, company.getTilesPerDay());
        int allowance = Math.max(0, Math.min(company.movementAllowance, entitlement));
        int immediate = Math.min(distance, allowance);
        int afterCurrentDay = distance - immediate;
        int boundaries = afterCurrentDay == 0 ? 0 : (afterCurrentDay + entitlement - 1) / entitlement;
        KOMEDailyBoundary schedule = KOMEMovementDayService.schedule();
        Instant next = schedule.nextBoundary(schedule.latestBoundaryAtOrBefore(Instant.ofEpochMilli(nowMillis)));
        Instant completion = Instant.ofEpochMilli(nowMillis);
        for (int i = 0; i < boundaries; i++) {
            completion = next;
            next = schedule.nextBoundary(next);
        }
        return new Timing(entitlement, allowance, immediate > 0, afterCurrentDay > 0,
            allowance == 0, boundaries,
            boundaries == 0 ? 0L : schedule.nextBoundary(
                schedule.latestBoundaryAtOrBefore(Instant.ofEpochMilli(nowMillis))).toEpochMilli(),
            completion.toEpochMilli());
    }

    /**
     * Fingerprints every authority shown by the confirmation. It is a stale-write guard,
     * not an authorization token: confirmation still repeats all server-side validation.
     */
    public static String fingerprint(KOMEArmyCompany company, String destination,
            List<String> routeTiles, List<String> edgeNotes, int arrivalDimension,
            double arrivalX, double arrivalY, double arrivalZ, String arrivalSource,
            long rootBoundaryMillis, String rootBoundarySchedule) {
        if (company == null) throw new IllegalArgumentException("Company is required");
        StringBuilder value = new StringBuilder();
        append(value, company.id); append(value, company.name); append(value, company.currentTile);
        append(value, company.status); append(value, company.movementOrderId);
        append(value, company.updatedAtMillis); append(value, company.movementAllowance);
        append(value, company.movementBoundaryMillis); append(value, company.movementBoundarySchedule);
        append(value, company.totalPopulation); append(value, company.mountedPopulation);
        append(value, company.groundPopulation); append(value, destination);
        appendAll(value, routeTiles); appendAll(value, edgeNotes);
        append(value, arrivalDimension); append(value, Double.doubleToLongBits(arrivalX));
        append(value, Double.doubleToLongBits(arrivalY)); append(value, Double.doubleToLongBits(arrivalZ));
        append(value, arrivalSource); append(value, rootBoundaryMillis); append(value, rootBoundarySchedule);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                value.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte part : digest) result.append(String.format("%02x", part & 0xff));
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void appendAll(StringBuilder out, List<String> values) {
        if (values == null) { append(out, -1); return; }
        append(out, values.size());
        for (String value : values) append(out, value);
    }

    private static void append(StringBuilder out, Object value) {
        String text = String.valueOf(value == null ? "" : value);
        out.append(text.length()).append(':').append(text).append('|');
    }

    public static final class Timing {
        public final int entitlement;
        public final int remainingAllowance;
        public final boolean immediateMovementPossible;
        public final boolean requiresFutureBoundary;
        public final boolean exhausted;
        public final int requiredBoundaries;
        public final long nextBoundaryMillis;
        public final long estimatedCompletionMillis;

        private Timing(int entitlement, int remainingAllowance, boolean immediateMovementPossible,
                boolean requiresFutureBoundary, boolean exhausted, int requiredBoundaries,
                long nextBoundaryMillis, long estimatedCompletionMillis) {
            this.entitlement = entitlement;
            this.remainingAllowance = remainingAllowance;
            this.immediateMovementPossible = immediateMovementPossible;
            this.requiresFutureBoundary = requiresFutureBoundary;
            this.exhausted = exhausted;
            this.requiredBoundaries = requiredBoundaries;
            this.nextBoundaryMillis = nextBoundaryMillis;
            this.estimatedCompletionMillis = estimatedCompletionMillis;
        }
    }
}
