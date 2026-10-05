package kome.client;

import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.*;

public class KOMEStandingTrialEligibilityStateTest {
    @Test
    public void eligibilitySurvivesGuiLifetimeAndFollowsStableNpcIdentity() {
        KOMEStandingTrialEligibilityState state = new KOMEStandingTrialEligibilityState();
        UUID npc = UUID.fromString("12345678-1234-5678-9abc-def012345678");

        state.update(41, npc, true, true);
        assertTrue(state.isEligible(41, npc));

        // No GUI-open/close operation participates in this cache.
        assertTrue(state.isEligible(41, npc));

        state.update(77, npc, true, true);
        assertFalse(state.isEligible(41, npc));
        assertTrue(state.isEligible(77, npc));

        // A delayed packet for an old runtime entity ID cannot clear the new binding.
        state.update(41, npc, false, false);
        assertTrue(state.isEligible(77, npc));

        state.update(77, npc, false, false);
        assertFalse(state.isEligible(77, npc));
    }

    @Test
    public void sessionResetClearsAllEligibility() {
        KOMEStandingTrialEligibilityState state = new KOMEStandingTrialEligibilityState();
        UUID npc = UUID.randomUUID();
        state.update(9, npc, true, true);
        state.clear();
        assertFalse(state.isEligible(9, npc));
    }

    @Test
    public void multipleEligibleNpcsRemainIndependentAndIneligibleNpcDoesNotMatch() {
        KOMEStandingTrialEligibilityState state = new KOMEStandingTrialEligibilityState();
        UUID first = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID second = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID ineligible = UUID.fromString("33333333-3333-3333-3333-333333333333");

        state.update(11, first, true, true);
        state.update(22, second, true, true);

        assertTrue(state.isEligible(11, first));
        assertTrue(state.isEligible(22, second));
        assertFalse(state.isEligible(11, second));
        assertFalse(state.isEligible(33, ineligible));
    }

    @Test
    public void exactNativeOfferOrSynchronizedPassiveOfferCanDriveQuest() {
        KOMEStandingTrialEligibilityState state = new KOMEStandingTrialEligibilityState();
        UUID npc = UUID.fromString("44444444-4444-4444-4444-444444444444");

        assertFalse(state.isStandingTrialAvailable(44, npc, true));
        assertFalse(state.isStandingTrialAvailable(44, npc, false));

        state.update(44, npc, true, true);
        assertTrue(state.isStandingTrialAvailable(44, npc, false));
        assertFalse(state.isStandingTrialAvailable(45, npc, false));
        assertFalse(state.isStandingTrialAvailable(44, UUID.randomUUID(), false));

        state.update(44, npc, false, false);
        assertFalse(state.isStandingTrialAvailable(44, npc, false));
    }
}
