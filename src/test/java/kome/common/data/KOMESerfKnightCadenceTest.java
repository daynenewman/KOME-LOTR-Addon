package kome.common.data;

import java.util.Random;
import java.util.UUID;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;
import static org.junit.Assert.*;

/** Domain coverage for the one-assignment-per-server-calendar-day rule. */
public class KOMESerfKnightCadenceTest {
    private static KOMEProgressionNpcRef ref(String name) {
        return new KOMEProgressionNpcRef(UUID.randomUUID().toString(), name, "rohan", 0, 0, 0, 0);
    }
    private static void master(KOMESerfKnightProgression state) {
        assertTrue(KOMESerfKnightService.setSerfdomMaster(state, ref("Master")).success);
    }
    private static void completeDuties(KOMESerfKnightProgression state) {
        long day = 10L;
        for (KOMESerfKnightDutyType type : KOMESerfKnightDutyType.values()) {
            assertTrue(KOMESerfKnightService.assignDuty(state, type, null, day++).success);
            assertTrue(KOMESerfKnightService.completeDuty(state, type).success);
        }
    }
    private static KOMEProgressionNpcRef completeDutiesAndSetLiege(KOMESerfKnightProgression state) {
        master(state); completeDuties(state);
        KOMEProgressionNpcRef liege = ref("Liege");
        assertTrue(KOMESerfKnightService.setProspectiveLiege(state, liege).success);
        return liege;
    }

    @Test public void configuredLocalCalendarUsesMidnightRatherThanUtcAndHandlesDst() {
        ZoneId chicago = ZoneId.of("America/Chicago");
        assertEquals(chicago, KOMEProgressionCalendar.configuredTimezone());
        Instant beforeMidnight = Instant.parse("2026-09-21T04:59:00Z");
        Instant midnight = Instant.parse("2026-09-21T05:00:00Z");
        long september20 = LocalDate.of(2026, 9, 20).toEpochDay();
        assertEquals(september20, KOMEProgressionCalendar.epochDay(beforeMidnight, chicago));
        assertEquals(september20 + 1L, KOMEProgressionCalendar.epochDay(midnight, chicago));
        // Both instants are on 21 September UTC but fall on different configured local dates.
        assertNotEquals(KOMEProgressionCalendar.epochDay(beforeMidnight, chicago), KOMEProgressionCalendar.epochDay(midnight, chicago));

        long march7 = KOMEProgressionCalendar.epochDay(Instant.parse("2026-03-08T05:59:00Z"), chicago);
        long march8 = KOMEProgressionCalendar.epochDay(Instant.parse("2026-03-08T06:00:00Z"), chicago);
        long march9 = KOMEProgressionCalendar.epochDay(Instant.parse("2026-03-09T05:00:00Z"), chicago);
        assertEquals(march7 + 1L, march8);
        assertEquals(march8 + 1L, march9);
        // The skipped 02:00 local hour stays within its one LocalDate progression day.
        assertEquals(march8, KOMEProgressionCalendar.epochDay(Instant.parse("2026-03-08T07:00:00Z"), chicago));
    }

    @Test public void timezoneChoiceChangesBoundaryDeterministicallyAndIsSharedByCadenceAndLockout() {
        Instant instant = Instant.parse("2026-09-21T04:30:00Z");
        long chicagoDay = KOMEProgressionCalendar.epochDay(instant, ZoneId.of("America/Chicago"));
        long tokyoDay = KOMEProgressionCalendar.epochDay(instant, ZoneId.of("Asia/Tokyo"));
        assertEquals(LocalDate.of(2026, 9, 20).toEpochDay(), chicagoDay);
        assertEquals(LocalDate.of(2026, 9, 21).toEpochDay(), tokyoDay);
        assertNotEquals(chicagoDay, tokyoDay);
        // Both default assignment issuance and the death-event betrayal path call calendarDayNow().
        assertEquals(KOMEProgressionCalendar.currentEpochDay(), KOMESerfKnightService.calendarDayNow());
    }

    @Test public void dutiesUseOneDailySlotWithoutBankingAndExposeOrderedNextDuty() {
        KOMESerfKnightProgression state = new KOMESerfKnightProgression(); master(state);
        assertNotNull(KOMESerfKnightService.chooseAvailableDuty(state,new Random(1)));
        assertTrue(KOMESerfKnightService.assignDuty(state, KOMESerfKnightDutyType.PROVISIONING, null, 10L).success);
        assertEquals(10L, state.getLastAssignmentEpochDay());
        assertTrue(KOMESerfKnightService.completeDuty(state, KOMESerfKnightDutyType.PROVISIONING).success);
        assertNotEquals(KOMESerfKnightDutyType.PROVISIONING, KOMESerfKnightService.chooseAvailableDuty(state,new Random(1)));
        assertFalse(KOMESerfKnightService.assignDuty(state, KOMESerfKnightDutyType.PROFESSION, null, 10L).success);
        assertTrue(KOMESerfKnightService.assignDuty(state, KOMESerfKnightDutyType.PROFESSION, null, 11L).success);
        assertTrue(KOMESerfKnightService.completeDuty(state, KOMESerfKnightDutyType.PROFESSION).success);
        assertTrue(KOMESerfKnightService.assignDuty(state, KOMESerfKnightDutyType.COURIER, null, 20L).success);
        assertEquals(20L, state.getLastAssignmentEpochDay());
    }

    @Test public void activeAssignmentPreventsSecondDutyOrTrial() {
        KOMESerfKnightProgression state = new KOMESerfKnightProgression(); master(state);
        assertTrue(KOMESerfKnightService.assignDuty(state, KOMESerfKnightDutyType.PROVISIONING, null, 10L).success);
        assertTrue(state.hasActiveAssignment());
        assertFalse(KOMESerfKnightService.assignDuty(state, KOMESerfKnightDutyType.PROFESSION, null, 11L).success);
        assertFalse(KOMESerfKnightService.assignTrial(state, new Random(1L), 11L).success);
    }

    @Test public void trialConsumesDailySlotAndNormalDisruptionDoesNotRestoreIt() {
        KOMESerfKnightProgression state = new KOMESerfKnightProgression();
        KOMEProgressionNpcRef liege = completeDutiesAndSetLiege(state);
        assertTrue(KOMESerfKnightService.assignTrial(state, new Random(2L), 20L).success);
        assertEquals(20L, state.getLastAssignmentEpochDay());
        // A cancelled active trial demonstrates that disruption cannot restore today's slot.
        assertTrue(KOMESerfKnightService.handleNpcDeath(state, liege.entityUuid, false, 20L));
        assertTrue(KOMESerfKnightService.canSelectReplacement(state, 20L));
        KOMEProgressionNpcRef replacement = ref("Replacement Liege");
        assertTrue(KOMESerfKnightService.setProspectiveLiege(state, replacement).success);
        assertFalse(KOMESerfKnightService.assignTrial(state, new Random(2L), 20L).success);
        assertTrue(KOMESerfKnightService.assignTrial(state, new Random(2L), 21L).success);
    }

    @Test public void giftAndPromotionDoNotConsumeOrWaitForDailyAssignment() {
        KOMESerfKnightProgression state = new KOMESerfKnightProgression();
        completeDutiesAndSetLiege(state);
        assertTrue(KOMESerfKnightService.assignTrial(state, new Random(2L), 20L).success);
        assertTrue(KOMESerfKnightService.completeTrial(state).success);
        assertFalse(KOMESerfKnightService.mayIssueAssignment(state, 20L));
        assertTrue(KOMESerfKnightService.recordPartingGift(state).success);
        assertEquals(20L, state.getLastAssignmentEpochDay());
        assertTrue(KOMESerfKnightService.canPromote(state, 150));
        assertTrue(KOMESerfKnightService.markPromoted(state, 150).success);
    }

    @Test public void betrayalKeepsConsumedSlotAcrossRoundTripWhileNormalDisruptionDoesNotLockOut() {
        KOMESerfKnightProgression betrayal = new KOMESerfKnightProgression(); master(betrayal);
        KOMEProgressionNpcRef master = betrayal.getSerfdomMaster();
        assertTrue(KOMESerfKnightService.assignDuty(betrayal, KOMESerfKnightDutyType.PROVISIONING, null, 20L).success);
        assertTrue(KOMESerfKnightService.handleNpcDeath(betrayal, master.entityUuid, true, 20L));
        assertFalse(KOMESerfKnightService.canSelectReplacement(betrayal, 20L));
        assertTrue(KOMESerfKnightService.canSelectReplacement(betrayal, 21L));
        assertFalse(KOMESerfKnightService.mayIssueAssignment(betrayal, 20L));
        assertTrue(KOMESerfKnightService.mayIssueAssignment(betrayal, 21L));
        KOMESerfKnightProgression reloaded = new KOMESerfKnightProgression(); reloaded.readFromNBT(betrayal.writeToNBT());
        assertEquals(20L, reloaded.getLastAssignmentEpochDay());
        assertFalse(KOMESerfKnightService.canSelectReplacement(reloaded, 20L));
        assertTrue(KOMESerfKnightService.mayIssueAssignment(reloaded, 21L));

        KOMESerfKnightProgression normal = new KOMESerfKnightProgression(); master(normal);
        KOMEProgressionNpcRef normalMaster = normal.getSerfdomMaster();
        assertTrue(KOMESerfKnightService.assignDuty(normal, KOMESerfKnightDutyType.PROVISIONING, null, 20L).success);
        assertTrue(KOMESerfKnightService.handleNpcDeath(normal, normalMaster.entityUuid, false, 20L));
        assertTrue(KOMESerfKnightService.canSelectReplacement(normal, 20L));
        assertFalse(KOMESerfKnightService.mayIssueAssignment(normal, 20L));
    }

    @Test public void oldAndCorruptCadenceNbtLoadsSafelyAndMultipleActiveDutiesReconcileInOrder() {
        KOMESerfKnightProgression source = new KOMESerfKnightProgression(); master(source);
        NBTTagCompound old = source.writeToNBT(); old.removeTag("LastAssignmentEpochDay");
        NBTTagCompound duties = old.getCompoundTag("Duties");
        duties.getCompoundTag(KOMESerfKnightDutyType.PROVISIONING.key).setBoolean("Assigned", true);
        duties.getCompoundTag(KOMESerfKnightDutyType.PROFESSION.key).setBoolean("Assigned", true);
        KOMESerfKnightProgression loaded = new KOMESerfKnightProgression(); loaded.readFromNBT(old);
        assertEquals(-1L, loaded.getLastAssignmentEpochDay());
        assertTrue(loaded.getDuty(KOMESerfKnightDutyType.PROVISIONING).isAssigned());
        assertFalse(loaded.getDuty(KOMESerfKnightDutyType.PROFESSION).isAssigned());
        assertFalse(loaded.getDuty(KOMESerfKnightDutyType.COURIER).isAssigned());
        old.setLong("LastAssignmentEpochDay", Long.MAX_VALUE);
        loaded.readFromNBT(old);
        assertEquals(-1L, loaded.getLastAssignmentEpochDay());
    }
}
