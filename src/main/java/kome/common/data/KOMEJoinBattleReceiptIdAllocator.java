package kome.common.data;

/** Monotonic never-reused identity authority for durable Join Battle delivery receipts. */
public final class KOMEJoinBattleReceiptIdAllocator {
    private long nextSequence;

    public KOMEJoinBattleReceiptIdAllocator() { this(1L); }

    public KOMEJoinBattleReceiptIdAllocator(long nextSequence) {
        if (nextSequence < 1L)
            throw new IllegalArgumentException("Join Battle receipt sequence must be positive.");
        this.nextSequence = nextSequence;
    }

    public synchronized String allocate() {
        if (nextSequence == Long.MAX_VALUE)
            throw new IllegalStateException("Join Battle receipt identity sequence exhausted.");
        return "JB" + nextSequence++;
    }

    public synchronized String peek() {
        if (nextSequence == Long.MAX_VALUE)
            throw new IllegalStateException("Join Battle receipt identity sequence exhausted.");
        return "JB" + nextSequence;
    }

    public synchronized long getNextSequence() { return nextSequence; }

    synchronized void setNextSequence(long value) {
        if (value < 1L)
            throw new IllegalArgumentException("Join Battle receipt sequence must be positive.");
        nextSequence = value;
    }

    static long sequenceOf(String value) {
        String id = requireIdentity(value);
        return Long.parseLong(id.substring(2));
    }

    static String requireIdentity(String value) {
        String id = KOMEConflictContracts.text(value, "Join Battle receipt ID", 32);
        if (value == null || !value.equals(id))
            throw new IllegalArgumentException("Noncanonical Join Battle receipt ID: " + id);
        if (!id.matches("JB[1-9][0-9]*"))
            throw new IllegalArgumentException("Malformed Join Battle receipt ID: " + id);
        try {
            if (Long.parseLong(id.substring(2)) == Long.MAX_VALUE)
                throw new IllegalArgumentException("Reserved exhausted Join Battle receipt sequence.");
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Join Battle receipt sequence is outside the supported range.", invalid);
        }
        return id;
    }
}
