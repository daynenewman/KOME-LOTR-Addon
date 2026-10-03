package kome.common.data;

/** In-memory allocator contract. Phase 2 will save its next sequence, never a map-size estimate. */
public final class KOMEConflictIdAllocator {
    private long nextSequence;

    public KOMEConflictIdAllocator() { this(1L); }

    public KOMEConflictIdAllocator(long nextSequence) {
        if (nextSequence < 1L) throw new IllegalArgumentException("Conflict sequence must be positive.");
        this.nextSequence = nextSequence;
    }

    /** Long.MAX_VALUE is an exhaustion sentinel; allocation never wraps or reuses an identity. */
    public synchronized String allocate() {
        if (nextSequence == Long.MAX_VALUE) throw new IllegalStateException("Conflict identity sequence exhausted.");
        return "CF" + nextSequence++;
    }

    public synchronized long getNextSequence() { return nextSequence; }

    static String requireIdentity(String value) {
        String id = KOMEConflictContracts.text(value, "Conflict ID", 32);
        if (!id.matches("CF[1-9][0-9]*")) throw new IllegalArgumentException("Malformed conflict ID: " + id);
        try {
            if (Long.parseLong(id.substring(2)) == Long.MAX_VALUE)
                throw new IllegalArgumentException("Reserved exhausted conflict sequence.");
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Conflict sequence is outside the supported range.", invalid);
        }
        return id;
    }
}
