package kome.common.data;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;
import static kome.common.data.KOMEPlayerBattleLifecycle.*;
import static kome.common.data.KOMEPlayerBattleLifecycleRegistry.*;

public class KOMEPlayerBattleLifecycleRegistryTest {
    static final UUID PLAYER = UUID.fromString("21000000-0000-0000-0000-000000000001");
    static final UUID OTHER = UUID.fromString("21000000-0000-0000-0000-000000000002");
    static final long EXAMPLE_DELAY = 30000L;

    @Test public void deathCreatesOneThirtySecondExampleDeadline() {
        KOMEPlayerBattleLifecycleRegistry registry = new KOMEPlayerBattleLifecycleRegistry();
        Update result = begin(registry, 0, Cause.DEATH, 1000, EXAMPLE_DELAY);
        assertEquals(Change.APPLIED, result.change);
        KOMEPlayerBattleLifecycle record = result.record;
        assertEquals(PLAYER, record.key.playerId); assertEquals("CF1", record.key.conflictId);
        assertEquals("T100", record.tileId); assertEquals("JB1", record.originalReceiptId);
        assertEquals(1, record.generation); assertEquals(Cause.DEATH, record.cause);
        assertEquals(Long.valueOf(1000), record.interruptedAtMillis);
        assertEquals(Long.valueOf(31000), record.eligibleAtMillis);
        assertTrue(record.interruptionActive); assertNull(record.reenteredAtMillis);
    }

    @Test public void disconnectCreatesDeadlineWithoutAnEntityOrReceipt() {
        KOMEPlayerBattleLifecycleRegistry registry = new KOMEPlayerBattleLifecycleRegistry();
        Update result = registry.beginInterruption(PLAYER, "CF1", "T100", 0, Cause.DISCONNECT, 40, EXAMPLE_DELAY, "");
        assertEquals(Change.APPLIED, result.change); assertEquals(Cause.DISCONNECT, result.record.cause);
        assertEquals(Long.valueOf(30040), result.record.eligibleAtMillis); assertEquals("", result.record.originalReceiptId);
    }

    @Test public void deathThenLogoutPreservesFirstCauseGenerationAndDeadline() {
        KOMEPlayerBattleLifecycleRegistry registry = new KOMEPlayerBattleLifecycleRegistry();
        KOMEPlayerBattleLifecycle first = begin(registry, 0, Cause.DEATH, 1000, EXAMPLE_DELAY).record;
        Update logout = begin(registry, first.generation, Cause.DISCONNECT, 2000, 90000);
        assertEquals(Change.UNCHANGED, logout.change); assertSame(first, logout.record);
        assertEquals(Long.valueOf(31000), logout.record.eligibleAtMillis); assertEquals(Cause.DEATH, logout.record.cause);
    }

    @Test public void exactReplayOfFirstBeginIsIdempotentEvenIfConfiguredDelayChanged() {
        KOMEPlayerBattleLifecycleRegistry registry = new KOMEPlayerBattleLifecycleRegistry();
        KOMEPlayerBattleLifecycle first = begin(registry, 0, Cause.DEATH, 1000, EXAMPLE_DELAY).record;
        Update replay = begin(registry, 0, Cause.DEATH, 1000, 90000);
        assertEquals(Change.UNCHANGED, replay.change); assertSame(first, replay.record);
        registry.completeReentry(PLAYER, "CF1", 1, 31000);
        assertEquals(Change.GENERATION_MISMATCH, begin(registry, 0, Cause.DEATH, 1000, 90000).change);
        assertFalse(registry.get(PLAYER, "CF1").interruptionActive);
    }

    @Test public void repeatedLogoutAndChangedDelayCannotRefreshAnActiveDeadlineEvenAfterExpiry() {
        KOMEPlayerBattleLifecycleRegistry registry = new KOMEPlayerBattleLifecycleRegistry();
        KOMEPlayerBattleLifecycle first = begin(registry, 0, Cause.DISCONNECT, 1000, EXAMPLE_DELAY).record;
        for (long now : new long[]{1000, 2000, 31000, 99999}) {
            Update repeated = begin(registry, first.generation, Cause.DISCONNECT, now, Long.MAX_VALUE);
            assertEquals(Change.UNCHANGED, repeated.change); assertSame(first, repeated.record);
        }
    }

    @Test public void millisecondBoundaryIsExactAndQueriesArePure() {
        KOMEPlayerBattleLifecycleRegistry registry = new KOMEPlayerBattleLifecycleRegistry();
        KOMEPlayerBattleLifecycle record = begin(registry, 0, Cause.DEATH, 1000, EXAMPLE_DELAY).record;
        assertEquals(Status.COOLDOWN_ACTIVE, registry.interruptionStatus(PLAYER, "CF1", 30999));
        assertEquals(Status.COOLDOWN_EXPIRED, registry.interruptionStatus(PLAYER, "CF1", 31000));
        assertEquals(Status.COOLDOWN_EXPIRED, registry.interruptionStatus(PLAYER, "CF1", 90000));
        assertSame(record, registry.get(PLAYER, "CF1")); assertTrue(record.interruptionActive);
    }

    @Test public void delayIsAnExplicitInputIncludingZeroAndIsNotAHardwiredThirtySeconds() {
        KOMEPlayerBattleLifecycleRegistry registry = new KOMEPlayerBattleLifecycleRegistry();
        KOMEPlayerBattleLifecycle instant = begin(registry, 0, Cause.DEATH, 40, 0).record;
        assertEquals(Long.valueOf(40), instant.eligibleAtMillis);
        assertEquals(Status.COOLDOWN_EXPIRED, registry.interruptionStatus(PLAYER, "CF1", 40));
        registry.completeReentry(PLAYER, "CF1", 1, 40);
        assertEquals(Long.valueOf(547), begin(registry, 1, Cause.DEATH, 47, 500).record.eligibleAtMillis);
    }

    @Test public void successfulReentryClearsOnlyCurrentGenerationAndRepeatIsIdempotent() {
        KOMEPlayerBattleLifecycleRegistry registry = new KOMEPlayerBattleLifecycleRegistry();
        begin(registry, 0, Cause.DEATH, 1000, EXAMPLE_DELAY);
        assertEquals(Change.COOLDOWN_ACTIVE, registry.completeReentry(PLAYER, "CF1", 1, 30999).change);
        Update completed = registry.completeReentry(PLAYER, "CF1", 1, 31000);
        assertEquals(Change.APPLIED, completed.change); assertFalse(completed.record.interruptionActive);
        assertEquals(Long.valueOf(31000), completed.record.reenteredAtMillis);
        assertEquals(Status.NONE, registry.interruptionStatus(PLAYER, "CF1", 31000));
        Update repeat = registry.completeReentry(PLAYER, "CF1", 1, 99999);
        assertEquals(Change.UNCHANGED, repeat.change); assertSame(completed.record, repeat.record);
    }

    @Test public void genuineSecondInterruptionGetsNewGenerationAndStaleClearCannotAffectIt() {
        KOMEPlayerBattleLifecycleRegistry registry = new KOMEPlayerBattleLifecycleRegistry();
        begin(registry, 0, Cause.DEATH, 1000, EXAMPLE_DELAY);
        registry.completeReentry(PLAYER, "CF1", 1, 31000);
        KOMEPlayerBattleLifecycle second = begin(registry, 1, Cause.DISCONNECT, 32000, 2000).record;
        assertEquals(2, second.generation); assertEquals(Long.valueOf(34000), second.eligibleAtMillis);
        assertEquals(Change.GENERATION_MISMATCH, registry.completeReentry(PLAYER, "CF1", 1, 35000).change);
        assertEquals(Change.GENERATION_MISMATCH, begin(registry, 0, Cause.DEATH, 1000, EXAMPLE_DELAY).change);
        assertSame(second, registry.get(PLAYER, "CF1")); assertTrue(second.interruptionActive);
    }

    @Test public void delayedOldBeginAfterSuccessfulClearCannotCreateAnotherInterruption() {
        KOMEPlayerBattleLifecycleRegistry registry = new KOMEPlayerBattleLifecycleRegistry();
        begin(registry, 0, Cause.DEATH, 1000, EXAMPLE_DELAY);
        KOMEPlayerBattleLifecycle completed = registry.completeReentry(PLAYER, "CF1", 1, 31000).record;
        assertEquals(Change.GENERATION_MISMATCH, begin(registry, 0, Cause.DEATH, 1000, EXAMPLE_DELAY).change);
        assertSame(completed, registry.get(PLAYER, "CF1"));
    }

    @Test public void unknownFutureGenerationAndMissingCompletionCannotInventRows() {
        KOMEPlayerBattleLifecycleRegistry registry = new KOMEPlayerBattleLifecycleRegistry();
        assertEquals(Change.GENERATION_MISMATCH, begin(registry, 1, Cause.DEATH, 100, 1).change);
        assertEquals(Change.NOT_FOUND, registry.completeReentry(PLAYER, "CF1", 1, 200).change);
        assertTrue(registry.snapshot().records.isEmpty());
    }

    @Test public void formalRetreatBarRequiresNeitherReceiptNorPriorRegistrationAndIsIdempotent() {
        KOMEPlayerBattleLifecycleRegistry registry = new KOMEPlayerBattleLifecycleRegistry();
        Update barred = registry.recordFormalRetreat(PLAYER, "CF1", "T100", 500);
        assertEquals(Change.APPLIED, barred.change); assertEquals(0, barred.record.generation);
        assertNull(barred.record.cause); assertEquals("", barred.record.originalReceiptId);
        assertFalse(barred.record.interruptionActive); assertEquals(Long.valueOf(500), barred.record.formalRetreatAtMillis);
        assertEquals(Status.FORMAL_RETREAT_BARRED, registry.interruptionStatus(PLAYER, "CF1", 99999));
        Update repeat = registry.recordFormalRetreat(PLAYER, "CF1", "T100", 600);
        assertEquals(Change.UNCHANGED, repeat.change); assertSame(barred.record, repeat.record);
    }

    @Test public void deathOrDisconnectCannotEraseRetreatBar() {
        for (Cause cause : Cause.values()) {
            KOMEPlayerBattleLifecycleRegistry registry = new KOMEPlayerBattleLifecycleRegistry();
            KOMEPlayerBattleLifecycle barred = registry.recordFormalRetreat(PLAYER, "CF1", "T100", 50).record;
            assertEquals(Change.FORMAL_RETREAT_BARRED,
                registry.beginInterruption(PLAYER, "CF1", "T100", 0, cause, 60, EXAMPLE_DELAY, "").change);
            assertSame(barred, registry.get(PLAYER, "CF1"));
            assertEquals(Change.FORMAL_RETREAT_BARRED,
                registry.beginInterruption(PLAYER, "CF1", "T100", 0, cause, 60, EXAMPLE_DELAY, "JB1").change);
            assertSame(barred, registry.get(PLAYER, "CF1"));
        }
    }

    @Test public void retreatBarWinsOverExpiredCooldownAndSurvivesCompletionAcknowledgement() {
        KOMEPlayerBattleLifecycleRegistry registry = new KOMEPlayerBattleLifecycleRegistry();
        begin(registry, 0, Cause.DEATH, 1000, EXAMPLE_DELAY);
        registry.recordFormalRetreat(PLAYER, "CF1", "T100", 2000);
        assertEquals(Status.FORMAL_RETREAT_BARRED, registry.interruptionStatus(PLAYER, "CF1", 31000));
        // Completion acknowledges a physical fact only; the bar still denies future admission.
        Update cleared = registry.completeReentry(PLAYER, "CF1", 1, 32000);
        assertFalse(cleared.record.interruptionActive); assertTrue(cleared.record.isFormalRetreatBarred());
        assertEquals(Status.FORMAL_RETREAT_BARRED, registry.interruptionStatus(PLAYER, "CF1", 99999));
        assertEquals(Change.FORMAL_RETREAT_BARRED, begin(registry, 1, Cause.DISCONNECT, 40000, 1).change);
        assertEquals(Change.GENERATION_MISMATCH, begin(registry, 0, Cause.DEATH, 1000, EXAMPLE_DELAY).change);
        assertTrue(registry.get(PLAYER, "CF1").isFormalRetreatBarred());
    }

    @Test public void sameTileReplacementAndDifferentPlayerAreIndependent() {
        KOMEPlayerBattleLifecycleRegistry registry = new KOMEPlayerBattleLifecycleRegistry();
        registry.recordFormalRetreat(PLAYER, "CF1", "T100", 50);
        assertEquals(Status.NONE, registry.interruptionStatus(PLAYER, "CF2", 60));
        assertEquals(Status.NONE, registry.interruptionStatus(OTHER, "CF1", 60));
        assertEquals(Change.APPLIED, registry.beginInterruption(PLAYER, "CF2", "T100", 0, Cause.DEATH, 60, 30, "JB2").change);
        assertEquals(Change.APPLIED, registry.beginInterruption(OTHER, "CF1", "T100", 0, Cause.DISCONNECT, 60, 30, "").change);
        assertEquals(3, registry.snapshot().records.size());
    }

    @Test public void completionCannotClearAnotherPlayerOrAnotherConflict() {
        KOMEPlayerBattleLifecycleRegistry registry = new KOMEPlayerBattleLifecycleRegistry();
        KOMEPlayerBattleLifecycle current = begin(registry, 0, Cause.DEATH, 100, 1).record;
        assertEquals(Change.NOT_FOUND, registry.completeReentry(OTHER, "CF1", 1, 200).change);
        assertEquals(Change.NOT_FOUND, registry.completeReentry(PLAYER, "CF2", 1, 200).change);
        assertSame(current, registry.get(PLAYER, "CF1"));
    }

    @Test public void terminalConflictStopsCooldownAndPreventsStaleReopeningWithoutTouchingReplacement() {
        KOMEPlayerBattleLifecycleRegistry registry = new KOMEPlayerBattleLifecycleRegistry();
        begin(registry, 0, Cause.DEATH, 100, EXAMPLE_DELAY);
        registry.beginInterruption(PLAYER, "CF2", "T100", 0, Cause.DEATH, 100, EXAMPLE_DELAY, "JB2");
        assertEquals(Change.APPLIED, registry.markConflictTerminal("CF1", 200));
        KOMEPlayerBattleLifecycle ended = registry.get(PLAYER, "CF1");
        assertFalse(ended.interruptionActive); assertEquals(Long.valueOf(200), ended.terminalAtMillis);
        assertEquals(Status.NONE, registry.interruptionStatus(PLAYER, "CF1", 300));
        assertEquals(Change.CONFLICT_TERMINAL, begin(registry, 1, Cause.DISCONNECT, 300, 1).change);
        assertEquals(Change.CONFLICT_TERMINAL, registry.completeReentry(PLAYER, "CF1", 1, 30100).change);
        assertEquals(Status.COOLDOWN_ACTIVE, registry.interruptionStatus(PLAYER, "CF2", 300));
        assertEquals(Change.UNCHANGED, registry.markConflictTerminal("CF1", 500));
        assertSame(ended, registry.get(PLAYER, "CF1"));
    }

    @Test public void terminalBarRemainsHistoricalUntilExplicitSemanticCleanup() {
        KOMEPlayerBattleLifecycleRegistry registry = new KOMEPlayerBattleLifecycleRegistry();
        registry.recordFormalRetreat(PLAYER, "CF1", "T100", 100);
        assertEquals(0, registry.pruneTerminalRecords("CF1")); // Active-conflict bars cannot be pruned.
        registry.markConflictTerminal("CF1", 200);
        assertEquals(Status.FORMAL_RETREAT_BARRED, registry.interruptionStatus(PLAYER, "CF1", 300));
        assertEquals(1, registry.pruneTerminalRecords("CF1"));
        assertTrue(registry.isConflictTerminal("CF1")); assertNull(registry.get(PLAYER, "CF1"));
        assertEquals(Change.CONFLICT_TERMINAL, begin(registry, 0, Cause.DEATH, 400, 1).change);
        assertEquals(Change.CONFLICT_TERMINAL, registry.recordFormalRetreat(OTHER, "CF1", "T100", 400).change);
        assertEquals(Status.NONE, registry.interruptionStatus(PLAYER, "CF2", 400));
        assertEquals(0, registry.pruneTerminalRecords("CF1"));
    }

    @Test public void emptyTerminalConflictStillRejectsLateInterruptionEvents() {
        KOMEPlayerBattleLifecycleRegistry registry = new KOMEPlayerBattleLifecycleRegistry();
        registry.markConflictTerminal("CF1", 100);
        assertEquals(Change.CONFLICT_TERMINAL, begin(registry, 0, Cause.DEATH, 200, 1).change);
        assertTrue(registry.snapshot().records.isEmpty()); assertTrue(registry.isConflictTerminal("CF1"));
    }

    @Test public void invalidTerminalTimestampCannotPartiallyPublishAnyRowsOrMarker() {
        KOMEPlayerBattleLifecycleRegistry registry = new KOMEPlayerBattleLifecycleRegistry();
        begin(registry, 0, Cause.DEATH, 100, 1);
        registry.beginInterruption(OTHER, "CF1", "T100", 0, Cause.DEATH, 200, 1, "");
        Map<Key, KOMEPlayerBattleLifecycle> before = registry.snapshot().records;
        assertThrows(IllegalArgumentException.class, () -> registry.markConflictTerminal("CF1", 150));
        assertEquals(before, registry.snapshot().records); assertFalse(registry.isConflictTerminal("CF1"));
    }

    @Test public void oneExactConflictCannotChangeTileOrOriginalReceipt() {
        KOMEPlayerBattleLifecycleRegistry registry = new KOMEPlayerBattleLifecycleRegistry();
        KOMEPlayerBattleLifecycle first = begin(registry, 0, Cause.DEATH, 100, 1).record;
        assertThrows(IllegalArgumentException.class, () -> registry.beginInterruption(PLAYER, "CF1", "T100", 1, Cause.DEATH, 100, 1, "JB2"));
        assertThrows(IllegalArgumentException.class, () -> registry.recordFormalRetreat(PLAYER, "CF1", "T101", 200));
        assertThrows(IllegalArgumentException.class, () -> registry.recordFormalRetreat(OTHER, "CF1", "T101", 200));
        assertSame(first, registry.get(PLAYER, "CF1"));
    }

    @Test public void negativeInputsAndDeadlineOverflowFailBeforePublication() {
        KOMEPlayerBattleLifecycleRegistry registry = new KOMEPlayerBattleLifecycleRegistry();
        assertThrows(IllegalArgumentException.class, () -> begin(registry, 0, Cause.DEATH, -1, 30));
        assertThrows(IllegalArgumentException.class, () -> begin(registry, 0, Cause.DEATH, 1, -30));
        assertThrows(IllegalArgumentException.class, () -> begin(registry, -1, Cause.DEATH, 1, 30));
        assertThrows(IllegalArgumentException.class, () -> begin(registry, 0, null, 1, 30));
        assertThrows(IllegalArgumentException.class, () -> begin(registry, 0, Cause.DEATH, Long.MAX_VALUE, 1));
        assertThrows(IllegalArgumentException.class, () -> registry.interruptionStatus(PLAYER, "CF1", -1));
        assertThrows(IllegalArgumentException.class, () -> registry.completeReentry(PLAYER, "CF1", 0, 1));
        assertThrows(IllegalArgumentException.class, () -> registry.recordFormalRetreat(PLAYER, "CF1", "T100", -1));
        assertThrows(IllegalArgumentException.class, () -> registry.markConflictTerminal("CF1", -1));
        assertTrue(registry.snapshot().records.isEmpty()); assertTrue(registry.snapshot().terminalConflicts.isEmpty());
    }

    @Test public void generationExhaustionAndRegressedSecondInterruptionDoNotOverwriteHistory() {
        Key key = new Key(PLAYER, "CF1");
        KOMEPlayerBattleLifecycle exhausted = new KOMEPlayerBattleLifecycle(key, "T100", "JB1",
            Long.MAX_VALUE, Cause.DEATH, 0L, 0L, false, 1L, null, null);
        KOMEPlayerBattleLifecycleRegistry registry = KOMEPlayerBattleLifecycleRegistry.restore(
            Collections.singleton(exhausted), Collections.<String, Long>emptyMap());
        assertThrows(IllegalArgumentException.class, () -> begin(registry, Long.MAX_VALUE, Cause.DEATH, 2, 1));
        assertSame(exhausted, registry.get(PLAYER, "CF1"));
        KOMEPlayerBattleLifecycleRegistry normal = new KOMEPlayerBattleLifecycleRegistry();
        begin(normal, 0, Cause.DEATH, 10, 1); normal.completeReentry(PLAYER, "CF1", 1, 20);
        assertThrows(IllegalArgumentException.class, () -> begin(normal, 1, Cause.DEATH, 19, 1));
        assertFalse(normal.get(PLAYER, "CF1").interruptionActive);
    }

    @Test public void snapshotsAreDetachedAndUnmodifiableAndLatestGenerationReplacesHistory() {
        KOMEPlayerBattleLifecycleRegistry registry = new KOMEPlayerBattleLifecycleRegistry();
        Snapshot empty = registry.snapshot();
        for (int generation = 0; generation < 100; generation++) {
            begin(registry, generation, Cause.DEATH, generation, 0);
            registry.completeReentry(PLAYER, "CF1", generation + 1, generation);
        }
        assertTrue(empty.records.isEmpty()); assertEquals(1, registry.snapshot().records.size());
        assertEquals(100, registry.get(PLAYER, "CF1").generation);
        assertThrows(UnsupportedOperationException.class, () -> registry.snapshot().records.clear());
        assertThrows(UnsupportedOperationException.class, () -> registry.snapshot().terminalConflicts.put("CF1", 1L));
    }

    @Test public void canonicalIdentitiesAreRequiredWithoutWorldLookups() {
        KOMEPlayerBattleLifecycleRegistry registry = new KOMEPlayerBattleLifecycleRegistry();
        for (String conflict : new String[]{"", "cf1", " CF1", "CF0", "CF01", "CF9223372036854775807", "CF9223372036854775808"})
            assertThrows(IllegalArgumentException.class, () -> registry.get(PLAYER, conflict));
        assertThrows(IllegalArgumentException.class, () -> registry.get(null, "CF1"));
        assertThrows(IllegalArgumentException.class, () -> registry.recordFormalRetreat(PLAYER, "CF1", " t100 ", 0));
        assertThrows(IllegalArgumentException.class, () -> registry.beginInterruption(PLAYER, "CF1", "T100", 0, Cause.DEATH, 0, 1, "jb1"));
        assertTrue(registry.snapshot().records.isEmpty());
    }

    static Update begin(KOMEPlayerBattleLifecycleRegistry registry, long generation, Cause cause, long now, long delay) {
        return registry.beginInterruption(PLAYER, "CF1", "T100", generation, cause, now, delay, "JB1");
    }
}
