package kome.common.data;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import static kome.common.data.KOMEPlayerBattleLifecycle.*;

/** Standalone mutation authority. No singleton, world, entity, receipt lookup, or military state. */
public final class KOMEPlayerBattleLifecycleRegistry {
    public enum Status { NONE, COOLDOWN_ACTIVE, COOLDOWN_EXPIRED, FORMAL_RETREAT_BARRED }
    public enum Change {
        APPLIED, UNCHANGED, GENERATION_MISMATCH, CONFLICT_TERMINAL,
        FORMAL_RETREAT_BARRED, COOLDOWN_ACTIVE, NOT_FOUND
    }
    public static final class Update {
        public final Change change;
        public final KOMEPlayerBattleLifecycle record;
        private Update(Change change, KOMEPlayerBattleLifecycle record) {
            this.change = change; this.record = record;
        }
    }
    /** Coherent immutable snapshot; codec cannot observe a partially applied terminal transition. */
    public static final class Snapshot {
        public final Map<Key, KOMEPlayerBattleLifecycle> records;
        public final Map<String, Long> terminalConflicts;
        private Snapshot(Map<Key, KOMEPlayerBattleLifecycle> records, Map<String, Long> terminal) {
            this.records = Collections.unmodifiableMap(new TreeMap<Key, KOMEPlayerBattleLifecycle>(records));
            terminalConflicts = Collections.unmodifiableMap(new TreeMap<String, Long>(terminal));
        }
    }

    private final Map<Key, KOMEPlayerBattleLifecycle> records = new TreeMap<Key, KOMEPlayerBattleLifecycle>();
    private final Map<String, Long> terminalConflicts = new TreeMap<String, Long>();

    public synchronized KOMEPlayerBattleLifecycle get(UUID playerId, String conflictId) {
        return records.get(new Key(playerId, conflictId));
    }
    public synchronized Snapshot snapshot() { return new Snapshot(records, terminalConflicts); }
    public synchronized boolean isConflictTerminal(String conflictId) {
        return terminalConflicts.containsKey(identity(conflictId, "CF"));
    }

    /**
     * Compare-and-set against the generation observed by the event owner (zero for no row).
     * Required even for begin: an unversioned delayed event after a clear is indistinguishable
     * from a genuine second interruption. An active duplicate never recomputes its deadline.
     */
    public synchronized Update beginInterruption(UUID playerId, String conflictId, String tileId,
            long expectedGeneration, Cause cause, long now, long delayMillis, String receiptId) {
        Key key = new Key(playerId, conflictId);
        tile(tileId); receiptId = receipt(receiptId);
        require(expectedGeneration >= 0 && cause != null && now >= 0 && delayMillis >= 0,
            "Valid generation, cause, time and delay are required.");
        KOMEPlayerBattleLifecycle previous = records.get(key);
        if (terminalConflicts.containsKey(key.conflictId)) return update(Change.CONFLICT_TERMINAL, previous);
        if (expectedGeneration != (previous == null ? 0 : previous.generation)) {
            // Exact replay of the begin that produced this still-active generation is harmless.
            // It must never reopen a cleared generation or be applied to a newer interruption.
            if (previous != null && previous.interruptionActive && expectedGeneration == previous.generation - 1
                    && previous.cause == cause && previous.interruptedAtMillis == now
                    && previous.tileId.equals(tileId) && previous.originalReceiptId.equals(receiptId))
                return update(Change.UNCHANGED, previous);
            return update(Change.GENERATION_MISMATCH, previous);
        }
        requireTile(key.conflictId, tileId);
        if (previous != null) {
            if (previous.isFormalRetreatBarred()) return update(Change.FORMAL_RETREAT_BARRED, previous);
            require(previous.originalReceiptId.equals(receiptId), "Original receipt identity cannot change.");
            if (previous.interruptionActive) return update(Change.UNCHANGED, previous);
            require(previous.reenteredAtMillis != null && now >= previous.reenteredAtMillis,
                "A new interruption precedes the prior successful re-entry.");
        }
        final long generation, deadline;
        try {
            generation = Math.addExact(previous == null ? 0 : previous.generation, 1);
            deadline = Math.addExact(now, delayMillis);
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("Interruption generation or deadline exhausted.", overflow);
        }
        KOMEPlayerBattleLifecycle next = new KOMEPlayerBattleLifecycle(key, tileId, receiptId,
            generation, cause, now, deadline, true, null, null, null);
        records.put(key, next);
        return update(Change.APPLIED, next);
    }

    /** Clock is caller supplied; offline time counts and querying never changes durable state. */
    public synchronized Status interruptionStatus(UUID playerId, String conflictId, long now) {
        require(now >= 0, "Query time cannot be negative.");
        KOMEPlayerBattleLifecycle record = records.get(new Key(playerId, conflictId));
        if (record == null) return Status.NONE;
        if (record.isFormalRetreatBarred()) return Status.FORMAL_RETREAT_BARRED;
        if (!record.interruptionActive) return Status.NONE;
        return now < record.eligibleAtMillis ? Status.COOLDOWN_ACTIVE : Status.COOLDOWN_EXPIRED;
    }

    /** Acknowledges external verified re-entry; does not itself authorize or perform delivery. */
    public synchronized Update completeReentry(UUID playerId, String conflictId,
            long expectedGeneration, long now) {
        require(expectedGeneration > 0 && now >= 0, "Positive generation and valid completion time required.");
        Key key = new Key(playerId, conflictId);
        KOMEPlayerBattleLifecycle previous = records.get(key);
        if (terminalConflicts.containsKey(key.conflictId)) return update(Change.CONFLICT_TERMINAL, previous);
        if (previous == null) return update(Change.NOT_FOUND, null);
        if (previous.generation != expectedGeneration) return update(Change.GENERATION_MISMATCH, previous);
        if (!previous.interruptionActive) return update(Change.UNCHANGED, previous);
        if (now < previous.eligibleAtMillis) return update(Change.COOLDOWN_ACTIVE, previous);
        KOMEPlayerBattleLifecycle next = new KOMEPlayerBattleLifecycle(key, previous.tileId,
            previous.originalReceiptId, previous.generation, previous.cause, previous.interruptedAtMillis,
            previous.eligibleAtMillis, false, now, previous.formalRetreatAtMillis, null);
        records.put(key, next);
        return update(Change.APPLIED, next);
    }

    /** Explicit acceptance fact; no player registration, online entity, or physical receipt required. */
    public synchronized Update recordFormalRetreat(UUID playerId, String conflictId, String tileId, long now) {
        Key key = new Key(playerId, conflictId);
        tile(tileId); require(now >= 0, "Retreat time cannot be negative.");
        KOMEPlayerBattleLifecycle previous = records.get(key);
        requireTile(key.conflictId, tileId);
        if (previous != null && previous.isFormalRetreatBarred()) return update(Change.UNCHANGED, previous);
        if (terminalConflicts.containsKey(key.conflictId)) return update(Change.CONFLICT_TERMINAL, previous);
        KOMEPlayerBattleLifecycle next = new KOMEPlayerBattleLifecycle(key, tileId,
            previous == null ? "" : previous.originalReceiptId, previous == null ? 0 : previous.generation,
            previous == null ? null : previous.cause, previous == null ? null : previous.interruptedAtMillis,
            previous == null ? null : previous.eligibleAtMillis, previous != null && previous.interruptionActive,
            previous == null ? null : previous.reenteredAtMillis, now, null);
        records.put(key, next);
        return update(Change.APPLIED, next);
    }

    /**
     * Marks only this exact conflict terminal. Stage all rows before publication; contradictory
     * timestamps cannot leave a partially terminal registry. Even an empty conflict gets a marker.
     */
    public synchronized Change markConflictTerminal(String conflictId, long now) {
        conflictId = identity(conflictId, "CF");
        require(now >= 0, "Terminal time cannot be negative.");
        if (terminalConflicts.containsKey(conflictId)) return Change.UNCHANGED;
        Map<Key, KOMEPlayerBattleLifecycle> next = new TreeMap<Key, KOMEPlayerBattleLifecycle>();
        for (KOMEPlayerBattleLifecycle previous : records.values()) if (conflictId.equals(previous.key.conflictId)) {
            next.put(previous.key, new KOMEPlayerBattleLifecycle(previous.key, previous.tileId,
                previous.originalReceiptId, previous.generation, previous.cause, previous.interruptedAtMillis,
                previous.eligibleAtMillis, false, previous.reenteredAtMillis, previous.formalRetreatAtMillis, now));
        }
        terminalConflicts.put(conflictId, now);
        records.putAll(next);
        return Change.APPLIED;
    }

    /**
     * Explicit semantic cleanup, never capacity/LRU eviction. Retain the exact terminal marker
     * to reject late callbacks after player history is removed. External delivery/receipt work
     * must be settled by the integrating owner before it elects to discard these historical rows.
     */
    public synchronized int pruneTerminalRecords(String conflictId) {
        conflictId = identity(conflictId, "CF");
        if (!terminalConflicts.containsKey(conflictId)) return 0;
        int removed = 0;
        java.util.Iterator<Key> iterator = records.keySet().iterator();
        while (iterator.hasNext()) if (conflictId.equals(iterator.next().conflictId)) {
            iterator.remove(); removed++;
        }
        return removed;
    }

    private void requireTile(String conflictId, String tileId) {
        for (KOMEPlayerBattleLifecycle record : records.values()) if (conflictId.equals(record.key.conflictId))
            require(tileId.equals(record.tileId), "One exact conflict cannot change tile identity.");
    }
    private static Update update(Change change, KOMEPlayerBattleLifecycle record) { return new Update(change, record); }

    /** Detached codec publication only; deliberately no API for replacing live rows with stale snapshots. */
    static KOMEPlayerBattleLifecycleRegistry restore(Collection<KOMEPlayerBattleLifecycle> rows,
            Map<String, Long> terminal) {
        require(rows != null && terminal != null, "Lifecycle records and terminal markers are required.");
        KOMEPlayerBattleLifecycleRegistry result = new KOMEPlayerBattleLifecycleRegistry();
        for (Map.Entry<String, Long> entry : terminal.entrySet()) {
            identity(entry.getKey(), "CF");
            require(entry.getValue() != null && entry.getValue() >= 0, "Invalid terminal timestamp.");
            result.terminalConflicts.put(entry.getKey(), entry.getValue());
        }
        for (KOMEPlayerBattleLifecycle row : rows) {
            require(row != null, "Null lifecycle row.");
            require(java.util.Objects.equals(row.terminalAtMillis, terminal.get(row.key.conflictId)),
                "Lifecycle row and terminal conflict marker disagree.");
            result.requireTile(row.key.conflictId, row.tileId);
            require(result.records.put(row.key, row) == null, "Duplicate player/conflict lifecycle key.");
        }
        return result;
    }
}
