package kome.common.data;

import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.*;
import static kome.common.data.KOMEPlayerBattleAdmission.*;
import static kome.common.data.KOMEPlayerBattleAdmission.Decision.*;
import static kome.common.data.KOMEPlayerBattleLifecycle.Cause.*;

public class KOMEPlayerBattleAdmissionTest {
    private static final UUID PLAYER = UUID.fromString("21000000-0000-0000-0000-000000000001");
    private static final UUID OTHER = UUID.fromString("21000000-0000-0000-0000-000000000002");
    private final KOMEPlayerBattleLifecycleRegistry registry = new KOMEPlayerBattleLifecycleRegistry();

    private void interrupt() {
        registry.beginInterruption(PLAYER, "CF1", "T100", 0, DEATH, 1000, 30000, "JB1");
    }

    private Result query(boolean active, boolean support, long now) {
        return evaluate(new Facts(PLAYER, "CF1", registry.snapshot(), active, support, now));
    }

    private Result pending(boolean active, boolean support, long now) {
        return evaluate(new Facts(PLAYER, "CF1", registry.snapshot(), active, support, now, 1));
    }

    @Test public void normalPlayerIsNotLifecycleBlockedEvenWithoutSupport() {
        Result result = query(true, false, 0);
        assertEquals(ALLOW_EXISTING_TACTICAL_PARTICIPATION, result.decision);
        assertTrue(result.isTacticallyAdmitted());
        assertFalse(result.shouldOfferReentry());
        assertNull(result.eligibleAtMillis);
        assertEquals(0, result.remainingMillis);
    }

    @Test public void activeCooldownBlocksTacticalAdmissionAndAction() {
        interrupt();
        Result result = query(true, true, 13000);
        assertEquals(BLOCK_COOLDOWN, result.decision);
        assertFalse(result.isTacticallyAdmitted());
        assertFalse(result.shouldOfferReentry());
        assertEquals(Long.valueOf(31000), result.eligibleAtMillis);
        assertEquals(18000, result.remainingMillis);
    }

    @Test public void lackOfSupportDoesNotHideActiveCooldown() {
        interrupt();
        assertEquals(BLOCK_COOLDOWN, query(true, false, 30999).decision);
    }

    @Test public void exactDeadlineChangesDecisionWithoutChangingDeadline() {
        interrupt();
        Result before = query(true, true, 30999);
        Result at = query(true, true, 31000);
        assertEquals(BLOCK_COOLDOWN, before.decision);
        assertEquals(1, before.remainingMillis);
        assertEquals(REENTRY_AVAILABLE, at.decision);
        assertEquals(0, at.remainingMillis);
        assertEquals(before.eligibleAtMillis, at.eligibleAtMillis);
    }

    @Test public void expiredWithSupportOffersReentryButDoesNotAdmitTactically() {
        interrupt();
        Result result = query(true, true, 99000);
        assertEquals(REENTRY_AVAILABLE, result.decision);
        assertTrue(result.shouldOfferReentry());
        assertFalse(result.isTacticallyAdmitted());
        assertTrue(registry.get(PLAYER, "CF1").interruptionActive);
    }

    @Test public void expiredWithoutOwnFactionSupportBlocksWithoutMutation() {
        interrupt();
        KOMEPlayerBattleLifecycle before = registry.get(PLAYER, "CF1");
        Result result = query(true, false, 31000);
        assertEquals(BLOCK_NO_OWN_FACTION_SUPPORT, result.decision);
        assertFalse(result.isTacticallyAdmitted());
        assertFalse(result.shouldOfferReentry());
        assertSame(before, registry.get(PLAYER, "CF1"));
        assertEquals(REENTRY_AVAILABLE, query(true, true, 31000).decision);
    }

    @Test public void successfulExplicitClearAllowsNormalParticipationOnNextQuery() {
        interrupt();
        assertFalse(query(true, true, 31000).isTacticallyAdmitted());
        registry.completeReentry(PLAYER, "CF1", 1, 31000);
        assertEquals(ALLOW_EXISTING_TACTICAL_PARTICIPATION, query(true, false, 31000).decision);
        assertTrue(query(true, false, 31000).isTacticallyAdmitted());
        assertFalse(query(true, true, 31000).shouldOfferReentry());
    }

    @Test public void staleClearCannotRestoreAdmissionDuringNewInterruption() {
        interrupt();
        registry.completeReentry(PLAYER, "CF1", 1, 31000);
        registry.beginInterruption(PLAYER, "CF1", "T100", 1, DISCONNECT, 32000, 30000, "JB1");
        registry.completeReentry(PLAYER, "CF1", 1, 99000);
        assertEquals(BLOCK_COOLDOWN, query(true, true, 32001).decision);
        assertFalse(query(true, true, 99000).isTacticallyAdmitted());
    }

    @Test public void formalRetreatOverridesActiveCooldown() {
        interrupt();
        registry.recordFormalRetreat(PLAYER, "CF1", "T100", 2000);
        Result result = query(true, true, 2000);
        assertEquals(BLOCK_FORMAL_RETREAT, result.decision);
        assertFalse(result.isTacticallyAdmitted());
        assertFalse(result.shouldOfferReentry());
    }

    @Test public void formalRetreatOverridesExpiredCooldownWithOrWithoutSupport() {
        interrupt();
        registry.recordFormalRetreat(PLAYER, "CF1", "T100", 2000);
        assertEquals(BLOCK_FORMAL_RETREAT, query(true, true, 31000).decision);
        assertEquals(BLOCK_FORMAL_RETREAT, query(true, false, 31000).decision);
    }

    @Test public void receiptFreeFormalRetreatAlsoBlocks() {
        registry.recordFormalRetreat(PLAYER, "CF1", "T100", 0);
        assertEquals(BLOCK_FORMAL_RETREAT, query(true, true, 0).decision);
    }

    @Test public void sameTileReplacementConflictDoesNotInheritBarOrCooldown() {
        interrupt();
        registry.recordFormalRetreat(PLAYER, "CF1", "T100", 2000);
        Result replacement = evaluate(new Facts(PLAYER, "CF2", registry.snapshot(), true, false, 2000));
        assertEquals(ALLOW_EXISTING_TACTICAL_PARTICIPATION, replacement.decision);
        registry.beginInterruption(PLAYER, "CF2", "T100", 0, DISCONNECT, 3000, 10, "");
        Result expired = evaluate(new Facts(PLAYER, "CF2", registry.snapshot(), true, true, 3010));
        assertEquals(REENTRY_AVAILABLE, expired.decision);
    }

    @Test public void differentPlayerInSameConflictIsUnaffected() {
        interrupt();
        registry.recordFormalRetreat(PLAYER, "CF1", "T100", 2000);
        assertTrue(evaluate(new Facts(OTHER, "CF1", registry.snapshot(), true, false, 2000))
            .isTacticallyAdmitted());
    }

    @Test public void endedConflictBlocksRegardlessOfTimerSupportOrBar() {
        assertEquals(BLOCK_CONFLICT_ENDED, query(false, true, 0).decision);
        interrupt();
        for (long now : new long[]{1000, 31000, Long.MAX_VALUE}) {
            for (boolean support : new boolean[]{false, true}) {
                Result result = query(false, support, now);
                assertEquals(BLOCK_CONFLICT_ENDED, result.decision);
                assertFalse(result.isTacticallyAdmitted());
                assertFalse(result.shouldOfferReentry());
            }
        }
        registry.recordFormalRetreat(PLAYER, "CF1", "T100", 2000);
        assertEquals(BLOCK_CONFLICT_ENDED, query(false, true, 31000).decision);
    }

    @Test public void endedProjectionDoesNotPerformTerminalCleanup() {
        interrupt();
        KOMEPlayerBattleLifecycle before = registry.get(PLAYER, "CF1");
        query(false, true, 31000);
        assertSame(before, registry.get(PLAYER, "CF1"));
        assertTrue(before.interruptionActive);
        assertTrue(registry.snapshot().terminalConflicts.isEmpty());
    }

    @Test public void persistedTerminalMarkerCannotBeOverriddenByActiveAssertionOrPruning() {
        interrupt();
        registry.markConflictTerminal("CF1", 2000);
        assertEquals(BLOCK_CONFLICT_ENDED, query(true, true, 31000).decision);
        registry.pruneTerminalRecords("CF1");
        assertEquals(BLOCK_CONFLICT_ENDED, query(true, true, 31000).decision);
        assertTrue(evaluate(new Facts(PLAYER, "CF2", registry.snapshot(), true, false, 31000))
            .isTacticallyAdmitted());
    }

    @Test(expected = IllegalArgumentException.class) public void negativeQueryTimeIsRejected() {
        query(true, true, -1);
    }

    @Test public void earlierNonnegativeClockCannotRefreshOrClearInterruption() {
        interrupt();
        Result result = query(true, true, 0);
        assertEquals(BLOCK_COOLDOWN, result.decision);
        assertEquals(31000, result.remainingMillis);
        assertEquals(Long.valueOf(31000), registry.get(PLAYER, "CF1").eligibleAtMillis);
    }

    @Test public void maximumTimestampAndTextRoundingDoNotOverflow() {
        registry.beginInterruption(PLAYER, "CF1", "T100", 0, DEATH, 0, Long.MAX_VALUE, "");
        Result longest = query(true, true, 0);
        assertEquals(Long.MAX_VALUE, longest.remainingMillis);
        assertEquals("You can re-enter this battle in 9223372036854776 seconds.", longest.reasonText());
        assertEquals(1, query(true, true, Long.MAX_VALUE - 1).remainingMillis);
        assertEquals(REENTRY_AVAILABLE, query(true, true, Long.MAX_VALUE).decision);
        assertEquals(0, query(true, true, Long.MAX_VALUE).remainingMillis);
    }

    @Test public void detachedFactsStayDeterministicAfterRegistryChanges() {
        interrupt();
        Facts facts = new Facts(PLAYER, "CF1", registry.snapshot(), true, true, 31000);
        registry.completeReentry(PLAYER, "CF1", 1, 31000);
        for (int i = 0; i < 20; i++) {
            Result result = evaluate(facts);
            assertEquals(REENTRY_AVAILABLE, result.decision);
            assertFalse(result.isTacticallyAdmitted());
            assertEquals(Long.valueOf(31000), result.eligibleAtMillis);
            assertEquals(0, result.remainingMillis);
        }
        assertTrue(query(true, true, 31000).isTacticallyAdmitted());
    }

    @Test public void repeatedQueriesLeaveAllRegistryFactsUnchanged() {
        interrupt();
        registry.recordFormalRetreat(OTHER, "CF1", "T100", 1000);
        registry.markConflictTerminal("CF2", 2000);
        KOMEPlayerBattleLifecycleRegistry.Snapshot before = registry.snapshot();
        for (int i = 0; i < 20; i++) {
            query(true, false, 10000);
            query(true, true, 31000);
            query(false, true, 99000);
        }
        assertEquals(before.records, registry.snapshot().records);
        assertEquals(before.terminalConflicts, registry.snapshot().terminalConflicts);
    }

    @Test public void pendingDeliveryNeitherAdmitsNorOffersDuplicateReentry() {
        interrupt();
        for (boolean support : new boolean[]{false, true}) {
            Result result = pending(true, support, 31000);
            assertEquals(REENTRY_DELIVERY_PENDING, result.decision);
            assertFalse(result.isTacticallyAdmitted());
            assertFalse(result.shouldOfferReentry());
            assertEquals(Long.valueOf(31000), result.eligibleAtMillis);
            assertEquals(0, result.remainingMillis);
        }
        assertTrue(registry.get(PLAYER, "CF1").interruptionActive);
    }

    @Test public void cooldownBarAndEndedConflictOverridePendingDelivery() {
        interrupt();
        assertEquals(BLOCK_COOLDOWN, pending(true, true, 2000).decision);
        assertEquals(BLOCK_CONFLICT_ENDED, pending(false, true, 31000).decision);
        registry.recordFormalRetreat(PLAYER, "CF1", "T100", 2000);
        assertEquals(BLOCK_FORMAL_RETREAT, pending(true, true, 31000).decision);
    }

    @Test(expected = IllegalArgumentException.class) public void pendingDeliveryRequiresActiveInterruption() {
        pending(true, true, 31000);
    }

    @Test(expected = IllegalArgumentException.class) public void pendingDeliveryCannotUseStaleGeneration() {
        interrupt();
        registry.completeReentry(PLAYER, "CF1", 1, 31000);
        registry.beginInterruption(PLAYER, "CF1", "T100", 1, DEATH, 32000, 0, "JB1");
        pending(true, true, 32000);
    }

    @Test public void playerTextIsUsefulAndDoesNotExposeTechnicalIdentity() {
        interrupt();
        assertEquals("You can re-enter this battle in 18 seconds.", query(true, true, 13000).reasonText());
        assertEquals("You can re-enter this battle in 1 second.", query(true, true, 30999).reasonText());
        Result[] results = new Result[]{query(true, true, 13000), query(true, true, 31000),
            query(true, false, 31000), query(false, true, 31000), pending(true, true, 31000)};
        registry.recordFormalRetreat(PLAYER, "CF1", "T100", 2000);
        for (Result result : results) assertSafeText(result);
        assertSafeText(query(true, true, 31000));
        assertTrue(query(true, false, 31000).reasonText().contains("formally retreated"));
        assertTrue(results[2].reasonText().contains("Your faction"));
    }

    @Test(expected = IllegalArgumentException.class) public void nullFactsAreRejected() { evaluate(null); }

    @Test(expected = IllegalArgumentException.class) public void missingSnapshotIsRejected() {
        new Facts(PLAYER, "CF1", null, true, true, 0);
    }

    @Test(expected = IllegalArgumentException.class) public void negativeDeliveryGenerationIsRejected() {
        new Facts(PLAYER, "CF1", registry.snapshot(), true, true, 0, -1);
    }

    private static void assertSafeText(Result result) {
        String text = result.reasonText();
        assertFalse(text.isEmpty());
        assertFalse(text.contains("CF1"));
        assertFalse(text.contains("JB1"));
        assertFalse(text.contains(PLAYER.toString()));
        assertFalse(text.contains("generation"));
    }
}
