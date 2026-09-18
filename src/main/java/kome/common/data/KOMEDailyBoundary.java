package kome.common.data;

import kome.common.config.KOMEConfigRegistry;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.zone.ZoneRules;
import java.util.List;

/** Calendar boundary calculation shared by population and movement; contains no runtime state. */
public final class KOMEDailyBoundary {
    private final ZoneId zone;
    private final LocalTime time;

    public KOMEDailyBoundary(ZoneId zone, LocalTime time) {
        if (zone == null || time == null || !zone.getId().contains("/")
                || !ZoneId.getAvailableZoneIds().contains(zone.getId())
                || time.getSecond() != 0 || time.getNano() != 0) {
            throw new IllegalArgumentException("Daily schedule requires a region timezone and HH:mm local time");
        }
        this.zone = zone;
        this.time = time;
    }

    public static KOMEDailyBoundary from(KOMEConfigRegistry.DailyBatchSettings settings) {
        return new KOMEDailyBoundary(settings.getTimezone(), settings.getLocalTime());
    }

    /** Persisted identity is validated independently of the currently configured schedule. */
    public static KOMEDailyBoundary persisted(String timezone, String localTime) {
        if (localTime == null || !localTime.matches("[0-9]{2}:[0-9]{2}")) {
            throw new IllegalArgumentException("Persisted payout local time must use HH:mm");
        }
        return new KOMEDailyBoundary(ZoneId.of(timezone), LocalTime.parse(localTime));
    }

    public Instant boundary(LocalDate date) {
        LocalDateTime local = LocalDateTime.of(date, time);
        ZoneRules rules = zone.getRules();
        List<ZoneOffset> offsets = rules.getValidOffsets(local);
        // A gap uses its first valid instant, not the requested minutes shifted through the gap.
        if (offsets.isEmpty()) return rules.getTransition(local).getInstant();
        // ZoneRules orders overlap offsets with the earlier occurrence first.
        return local.toInstant(offsets.get(0));
    }

    public Instant latestBoundaryAtOrBefore(Instant now) {
        LocalDate date = localDate(now);
        Instant candidate = boundary(date);
        while (candidate.isAfter(now)) candidate = boundary(date = date.minusDays(1));
        return candidate;
    }

    public Instant nextBoundary(Instant previous) {
        LocalDate date = localDate(previous);
        Instant candidate = boundary(date);
        // A gap can land on the following date before that date's own boundary.
        // Consider that boundary before advancing, without paying coincident instants twice.
        while (!candidate.isAfter(previous)) candidate = boundary(date = date.plusDays(1));
        return candidate;
    }

    public LocalDate localDate(Instant instant) { return instant.atZone(zone).toLocalDate(); }
    public String localDescription(Instant instant) { return instant.atZone(zone).toString(); }
    public String timezone() { return zone.getId(); }
    public String localTime() { return time.toString(); }
    public String signature() { return timezone() + "@" + localTime(); }
}
