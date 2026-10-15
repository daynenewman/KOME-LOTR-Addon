package kome.common.data;

import java.util.Objects;
import java.util.UUID;

/** Immutable player facts for one exact battle, independent of world and military authority. */
public final class KOMEPlayerBattleLifecycle {
    public enum Cause { DEATH, DISCONNECT }

    public static final class Key implements Comparable<Key> {
        public final UUID playerId;
        public final String conflictId;

        public Key(UUID playerId, String conflictId) {
            require(playerId != null, "Player UUID is required.");
            this.playerId = playerId;
            this.conflictId = identity(conflictId, "CF");
        }

        @Override public int compareTo(Key other) {
            int player = playerId.compareTo(other.playerId);
            return player == 0 ? conflictId.compareTo(other.conflictId) : player;
        }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Key)) return false;
            Key key = (Key) other;
            return playerId.equals(key.playerId) && conflictId.equals(key.conflictId);
        }
        @Override public int hashCode() { return Objects.hash(playerId, conflictId); }
    }

    public final Key key;
    public final String tileId;
    /** Identity only: this record never reads, closes, or replaces a deployment receipt. */
    public final String originalReceiptId;
    /** Zero means no interruption has occurred (for example a receipt-free retreat bar). */
    public final long generation;
    public final Cause cause;
    public final Long interruptedAtMillis, eligibleAtMillis, reenteredAtMillis;
    public final boolean interruptionActive;
    public final Long formalRetreatAtMillis, terminalAtMillis;

    KOMEPlayerBattleLifecycle(Key key, String tileId, String originalReceiptId,
            long generation, Cause cause, Long interruptedAtMillis, Long eligibleAtMillis,
            boolean interruptionActive, Long reenteredAtMillis, Long formalRetreatAtMillis,
            Long terminalAtMillis) {
        require(key != null, "Player/conflict key is required.");
        this.key = key;
        this.tileId = tile(tileId);
        this.originalReceiptId = receipt(originalReceiptId);
        require(generation >= 0, "Interruption generation cannot be negative.");
        this.generation = generation;
        this.cause = cause;
        this.interruptedAtMillis = timestamp(interruptedAtMillis);
        this.eligibleAtMillis = timestamp(eligibleAtMillis);
        this.reenteredAtMillis = timestamp(reenteredAtMillis);
        this.interruptionActive = interruptionActive;
        this.formalRetreatAtMillis = timestamp(formalRetreatAtMillis);
        this.terminalAtMillis = timestamp(terminalAtMillis);
        if (generation == 0) {
            require(cause == null && interruptedAtMillis == null && eligibleAtMillis == null
                && reenteredAtMillis == null && !interruptionActive,
                "Generation zero cannot contain interruption facts.");
            require(formalRetreatAtMillis != null, "A lifecycle row requires an interruption or retreat fact.");
        } else {
            require(cause != null && interruptedAtMillis != null && eligibleAtMillis != null,
                "An interruption requires cause and exact timestamps.");
            require(eligibleAtMillis >= interruptedAtMillis, "Eligibility precedes interruption.");
            require(!interruptionActive || reenteredAtMillis == null && terminalAtMillis == null,
                "An active interruption cannot already be completed or terminal.");
            require(interruptionActive || reenteredAtMillis != null || terminalAtMillis != null,
                "Inactive interruption requires explicit completion or conflict termination.");
            require(reenteredAtMillis == null || reenteredAtMillis >= eligibleAtMillis,
                "Successful re-entry precedes its eligibility deadline.");
        }
        if (terminalAtMillis != null) {
            require(interruptedAtMillis == null || terminalAtMillis >= interruptedAtMillis,
                "Conflict termination precedes interruption.");
            require(reenteredAtMillis == null || terminalAtMillis >= reenteredAtMillis,
                "Conflict termination precedes re-entry.");
            require(formalRetreatAtMillis == null || terminalAtMillis >= formalRetreatAtMillis,
                "Conflict termination precedes Formal Retreat.");
        }
    }

    public boolean isFormalRetreatBarred() { return formalRetreatAtMillis != null; }

    @Override public boolean equals(Object other) {
        if (!(other instanceof KOMEPlayerBattleLifecycle)) return false;
        KOMEPlayerBattleLifecycle r = (KOMEPlayerBattleLifecycle) other;
        return key.equals(r.key) && tileId.equals(r.tileId) && originalReceiptId.equals(r.originalReceiptId)
            && generation == r.generation && cause == r.cause && interruptionActive == r.interruptionActive
            && Objects.equals(interruptedAtMillis, r.interruptedAtMillis)
            && Objects.equals(eligibleAtMillis, r.eligibleAtMillis)
            && Objects.equals(reenteredAtMillis, r.reenteredAtMillis)
            && Objects.equals(formalRetreatAtMillis, r.formalRetreatAtMillis)
            && Objects.equals(terminalAtMillis, r.terminalAtMillis);
    }
    @Override public int hashCode() {
        return Objects.hash(key, tileId, originalReceiptId, generation, cause, interruptedAtMillis,
            eligibleAtMillis, interruptionActive, reenteredAtMillis, formalRetreatAtMillis, terminalAtMillis);
    }

    static String identity(String value, String prefix) {
        require(value != null && value.length() <= 32 && value.matches(prefix + "[1-9][0-9]*"),
            "Malformed " + prefix + " identity.");
        try {
            require(Long.parseLong(value.substring(prefix.length())) != Long.MAX_VALUE,
                "Exhausted identity sequence is reserved.");
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Identity sequence is outside the supported range.", invalid);
        }
        return value;
    }
    static String tile(String value) {
        require(value != null && value.length() <= 32 && value.matches("[A-Z]+[0-9]+"),
            "Canonical tile identity is required.");
        return value;
    }
    static String receipt(String value) {
        return value == null || value.isEmpty() ? "" : identity(value, "JB");
    }
    static Long timestamp(Long value) {
        require(value == null || value >= 0, "Timestamp cannot be negative.");
        return value;
    }
    static void require(boolean condition, String reason) {
        if (!condition) throw new IllegalArgumentException(reason);
    }
}
