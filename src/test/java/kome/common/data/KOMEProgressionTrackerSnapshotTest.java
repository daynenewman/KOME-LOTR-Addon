package kome.common.data;

import java.util.UUID;
import kome.common.KOMEAccessFixture;
import org.junit.Test;

import static org.junit.Assert.*;

public class KOMEProgressionTrackerSnapshotTest {
    @Test
    public void completedDailyDutyBrieflyReportsCompletionInsteadOfWaiting()
            throws Exception {
        KOMEAccessFixture fixture=
            new KOMEAccessFixture();
        fixture.pledge(lotr.common.fac.LOTRFaction.ROHAN);
        java.lang.reflect.Field alignments=lotr.common.LOTRPlayerData.class.getDeclaredField("alignments");alignments.setAccessible(true);
        ((java.util.Map)alignments.get(lotr.common.LOTRLevelData.getData(fixture.player))).put(lotr.common.fac.LOTRFaction.ROHAN,150F);

        KOMEPlayerProgression progression=
            fixture.data.getProgression(
                fixture.player.getUniqueID());

        progression.setCanonicalRank(
            KOMEProgressionRank.SERF);

        KOMESerfKnightProgression state=
            progression.getSerfKnightProgression();

        state.setSerfdomMaster(
            new KOMEProgressionNpcRef(
                UUID.randomUUID().toString(),
                "Master",
                "rohan",
                0,
                0,
                64,
                0));

        state.assignDuty(
            KOMESerfKnightDutyType.PROVISIONING,
            null);

        state.completeDuty(
            KOMESerfKnightDutyType.PROVISIONING);

        long today=
            KOMESerfKnightService.calendarDayNow();

        state.setLastAssignmentEpochDay(today);

        KOMEProgressionTrackerSnapshot cooldown=
            KOMEProgressionTrackerSnapshot.project(
                fixture.player,
                progression);

        assertTrue(cooldown.visible);
        assertEquals(
            "daily_complete",
            cooldown.iconKey);
        assertEquals(
            "Today's duty is complete.",
            cooldown.objective);
        assertEquals(
            "1 / 3 duties",
            cooldown.progress);

        state.setLastAssignmentEpochDay(today-1L);

        KOMEProgressionTrackerSnapshot available=
            KOMEProgressionTrackerSnapshot.project(
                fixture.player,
                progression);

        assertTrue(available.visible);
        assertEquals(
            "Request another duty from your Master.",
            available.objective);

        state.assignDuty(
            KOMESerfKnightDutyType.PROFESSION,
            null);

        state.completeDuty(
            KOMESerfKnightDutyType.PROFESSION);

        state.assignDuty(
            KOMESerfKnightDutyType.COURIER,
            null);

        state.completeDuty(
            KOMESerfKnightDutyType.COURIER);

        state.setLastAssignmentEpochDay(today);

        KOMEProgressionTrackerSnapshot nextPhase=
            KOMEProgressionTrackerSnapshot.project(
                fixture.player,
                progression);

        assertTrue(nextPhase.visible);
        assertEquals("standing_trial_ready",nextPhase.iconKey);
        assertEquals(
            "Seek an eligible Liege for your Trial of Standing.\nEorling-at-Arms",
            nextPhase.objective);

        state.setLastTrialAssignmentEpochDay(today);
        KOMEProgressionTrackerSnapshot trialCooldown=
            KOMEProgressionTrackerSnapshot.project(
                fixture.player,
                progression);
        assertEquals("daily_complete",trialCooldown.iconKey);
        assertEquals(
            "You have already received a Trial of Standing today.",
            trialCooldown.objective);

        state.setLastTrialAssignmentEpochDay(today-1L);

        KOMEProgressionTrackerSnapshot prospectiveReady=
            KOMEProgressionTrackerSnapshot.project(
                fixture.player,
                progression);

        assertEquals("standing_trial_ready",prospectiveReady.iconKey);
        assertEquals(
            "Seek an eligible Liege for your Trial of Standing.\nEorling-at-Arms",
            prospectiveReady.objective);
        assertTrue(prospectiveReady.isReadyForStandingTrial());

        state.setLiege(
            new KOMEProgressionNpcRef(
                UUID.randomUUID().toString(),
                "Liege",
                "rohan",
                0,
                0,
                64,
                0));
        KOMEProgressionTrackerSnapshot ready=
            KOMEProgressionTrackerSnapshot.project(
                fixture.player,
                progression);

        assertEquals("standing_trial_ready",ready.iconKey);
        assertEquals("Ready for trial",ready.progress);
        assertTrue(ready.isReadyForStandingTrial());
    }
}
