package kome.common.data;

import org.junit.Test;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.TimeZone;
import static org.junit.Assert.*;

public class KOMEDailyBoundaryTest {
    @org.junit.Rule public final kome.common.data.KOMETileTestResources geometry =
        new kome.common.data.KOMETileTestResources();

    @Test public void springGapUsesFirstValidInstantNotShiftedMinutes() {
        assertBoundary("America/Chicago", "02:30", "2026-03-08", "2026-03-08T08:00:00Z");
        assertBoundary("Australia/Lord_Howe", "02:15", "2026-10-04", "2026-10-03T15:30:00Z");
    }

    @Test public void fallOverlapUsesEarlierOccurrenceExactlyOnce() throws Exception {
        assertBoundary("America/Chicago", "01:30", "2026-11-01", "2026-11-01T06:30:00Z");
        try (KOMEPopulationTestConfig config = new KOMEPopulationTestConfig()) {
            config.set("dailyBatch.localTime", "01:30");
            KOMEWorldData data = KOMEPopulationPayoutProcessorTest.world("gondor", 1000L);
            assertTrue(KOMEPopulationPayoutProcessor.initializeOrProcessStartup(data, Instant.parse("2026-11-01T05:00:00Z")).success);
            for (String time : new String[] {"2026-11-01T06:29:59Z", "2026-11-01T06:30:00Z", "2026-11-01T07:00:00Z", "2026-11-01T07:30:00Z"}) {
                KOMEPopulationPayoutProcessorTest.pay(data, Instant.parse(time));
                assertEquals(time.endsWith("29:59Z") ? 0L : 100L, KOMEPopulationPayoutProcessorTest.bank(data, "gondor"));
            }
            KOMEWorldData restored = KOMEPopulationPayoutProcessorTest.reload(data);
            assertTrue(KOMEPopulationPayoutProcessor.initializeOrProcessStartup(restored, Instant.parse("2026-11-01T07:45:00Z")).success);
            assertEquals(100L, KOMEPopulationPayoutProcessorTest.bank(restored, "gondor"));
        }
    }

    @Test public void calendarDatesAreStrictlyIncreasingAcrossLeapDayMonthYearAndTransitions() {
        for (String zone : new String[] {"America/Chicago", "Pacific/Auckland", "Australia/Lord_Howe", "Asia/Kathmandu"}) {
            for (String time : new String[] {"00:00", "01:30", "02:30", "20:00"}) {
                KOMEDailyBoundary schedule = schedule(zone, time);
                LocalDate date = LocalDate.parse("2023-12-30"); Instant boundary = schedule.boundary(date);
                for (int day = 0; day < 800; day++) {
                    assertEquals(boundary, schedule.latestBoundaryAtOrBefore(boundary));
                    assertTrue(schedule.latestBoundaryAtOrBefore(boundary.minusMillis(1)).isBefore(boundary));
                    Instant next = schedule.nextBoundary(boundary);
                    assertEquals(date.plusDays(1), schedule.localDate(next)); assertTrue(next.isAfter(boundary));
                    date = date.plusDays(1); boundary = next;
                }
            }
        }
    }

    @Test public void customRegionAndTimeDoNotUseJvmTimezone() throws Exception {
        TimeZone original = TimeZone.getDefault();
        try (KOMEPopulationTestConfig config = new KOMEPopulationTestConfig()) {
            config.set("dailyBatch.timezone", "Asia/Kathmandu", "dailyBatch.localTime", "09:15");
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"));
            assertEquals(Instant.parse("2026-01-10T03:30:00Z"),
                    KOMEPopulationPayoutProcessor.latestBoundaryAtOrBefore(Instant.parse("2026-01-10T04:00:00Z")));
            KOMEWorldData data = KOMEPopulationPayoutProcessorTest.world("gondor", 10L);
            KOMEPopulationPayoutProcessor.initializeOrProcessStartup(data, Instant.parse("2026-01-10T04:00:00Z"));
            KOMEPopulationPayoutProcessorTest.pay(data, Instant.parse("2026-01-11T03:30:00Z"));
            assertEquals(1L, KOMEPopulationPayoutProcessorTest.bank(data, "gondor"));
        } finally { TimeZone.setDefault(original); }
    }

    @Test public void springAndFallDayLengthsUseCalendarNotFixedMillis() {
        KOMEDailyBoundary schedule = schedule("America/Chicago", "20:00");
        for (String date : new String[] {"2026-03-07", "2026-10-31"}) {
            Instant before = schedule.boundary(LocalDate.parse(date));
            long hours = java.time.Duration.between(before, schedule.nextBoundary(before)).toHours();
            assertEquals(date.contains("03-07") ? 23L : 25L, hours);
        }
    }

    @Test public void missingOrMalformedPersistedScheduleHasNoFallback() {
        for (String[] values : new String[][] {{"CST", "20:00"}, {"UTC", "20:00"}, {"Bad/Zone", "20:00"},
                {"America/Chicago", "25:00"}, {"America/Chicago", "2:30"}, {"America/Chicago", "20:00:01"}}) {
            try { KOMEDailyBoundary.persisted(values[0], values[1]); fail(); } catch (RuntimeException expected) { }
        }
    }

    @Test public void skippedLocalDateDoesNotSkipTheFollowingDatesOwnBoundary() {
        KOMEDailyBoundary schedule = schedule("Pacific/Apia", "20:00");
        Instant gap = schedule.boundary(LocalDate.parse("2011-12-30"));
        assertEquals(Instant.parse("2011-12-30T10:00:00Z"), gap);
        assertEquals(gap, schedule.latestBoundaryAtOrBefore(gap));
        assertEquals(schedule.boundary(LocalDate.parse("2011-12-31")), schedule.nextBoundary(gap));
        KOMEDailyBoundary midnight = schedule("Pacific/Apia", "00:00");
        assertEquals(midnight.boundary(LocalDate.parse("2012-01-01")), midnight.nextBoundary(gap));
    }

    private static KOMEDailyBoundary schedule(String zone, String time) { return new KOMEDailyBoundary(ZoneId.of(zone), LocalTime.parse(time)); }
    private static void assertBoundary(String zone, String time, String date, String expected) {
        KOMEDailyBoundary schedule = schedule(zone, time); Instant boundary = schedule.boundary(LocalDate.parse(date));
        assertEquals(Instant.parse(expected), boundary); assertEquals(boundary, schedule.latestBoundaryAtOrBefore(boundary));
        assertTrue(schedule.nextBoundary(boundary).isAfter(boundary));
    }
}
