package kome.common.data;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Conflict-domain registry for physical Join Battle delivery receipts, not participation. */
public final class KOMEJoinBattleDeploymentRegistry {
    private final Map<String, KOMEJoinBattleDeploymentReceipt> receipts =
        new LinkedHashMap<String, KOMEJoinBattleDeploymentReceipt>();
    private final KOMEJoinBattleReceiptIdAllocator allocator;

    public KOMEJoinBattleDeploymentRegistry() { this(1L); }
    public KOMEJoinBattleDeploymentRegistry(long nextSequence) {
        allocator = new KOMEJoinBattleReceiptIdAllocator(nextSequence);
    }

    public synchronized KOMEJoinBattleDeploymentReceipt get(String receiptId) {
        if (receiptId == null) return null;
        return receipts.get(receiptId.trim());
    }
    public synchronized KOMEJoinBattleDeploymentReceipt findByActionToken(String actionToken) {
        if (actionToken == null) return null;
        String token = actionToken.trim();
        for (KOMEJoinBattleDeploymentReceipt receipt : receipts.values())
            if (receipt.getActionToken().equals(token)) return receipt;
        return null;
    }
    public synchronized Map<String, KOMEJoinBattleDeploymentReceipt> records() {
        return Collections.unmodifiableMap(new LinkedHashMap<String, KOMEJoinBattleDeploymentReceipt>(receipts));
    }
    public synchronized long getNextReceiptSequence() { return allocator.getNextSequence(); }
    synchronized String nextReceiptId() { return allocator.peek(); }

    /** Publishes a fully built next-sequence receipt only after every registry invariant passes. */
    synchronized void publishNew(KOMEJoinBattleDeploymentReceipt receipt) {
        if (receipt == null) throw new IllegalArgumentException("Join Battle receipt is required.");
        if (!allocator.peek().equals(receipt.getReceiptId()))
            throw new IllegalArgumentException("Join Battle receipt is not the next allocated identity.");
        Map<String, KOMEJoinBattleDeploymentReceipt> candidate =
            new LinkedHashMap<String, KOMEJoinBattleDeploymentReceipt>(receipts);
        if (candidate.put(receipt.getReceiptId(), receipt) != null)
            throw new IllegalArgumentException("Duplicate Join Battle receipt identity.");
        restore(candidate, allocator.getNextSequence() + 1L);
        allocator.allocate();
        receipts.put(receipt.getReceiptId(), receipt);
    }

    /** Replaces lifecycle state without permitting identity mutation or terminal reopening. */
    synchronized void replace(KOMEJoinBattleDeploymentReceipt receipt) {
        if (receipt == null) throw new IllegalArgumentException("Join Battle receipt is required.");
        KOMEJoinBattleDeploymentReceipt previous = receipts.get(receipt.getReceiptId());
        if (previous == null) throw new IllegalArgumentException("Join Battle receipt is absent.");
        if (!previous.sameImmutableIdentity(receipt))
            throw new IllegalArgumentException("Join Battle receipt immutable identity changed.");
        if (!KOMEJoinBattleDeploymentReceipt.isForwardTransition(previous.getState(), receipt.getState()))
            throw new IllegalArgumentException("Join Battle receipt lifecycle cannot move backward or reopen.");
        if (receipt.getUpdatedAtMillis() < previous.getUpdatedAtMillis())
            throw new IllegalArgumentException("Join Battle receipt update time regressed.");
        if (previous.getDeploymentDestination() != null
                && !previous.getDeploymentDestination().equals(receipt.getDeploymentDestination()))
            throw new IllegalArgumentException("Resolved Join Battle deployment destination changed.");
        requireStableTimestamp(previous.getDeployedAtMillis(), receipt.getDeployedAtMillis(), "deployment");
        requireStableTimestamp(previous.getEgressRequestedAtMillis(), receipt.getEgressRequestedAtMillis(), "egress");
        requireStableTimestamp(previous.getClosedAtMillis(), receipt.getClosedAtMillis(), "closure");
        if (!participationAdvances(previous.getParticipationRecovery(), receipt.getParticipationRecovery()))
            throw new IllegalArgumentException("Join Battle participation recovery state regressed.");
        if (!mountTransferAdvances(previous.getMountTransferPhase(), receipt.getMountTransferPhase()))
            throw new IllegalArgumentException("Join Battle mount transfer phase regressed.");
        Map<String, KOMEJoinBattleDeploymentReceipt> candidate =
            new LinkedHashMap<String, KOMEJoinBattleDeploymentReceipt>(receipts);
        candidate.put(receipt.getReceiptId(), receipt);
        restore(candidate, allocator.getNextSequence());
        receipts.put(receipt.getReceiptId(), receipt);
    }

    private static void requireStableTimestamp(Long previous, Long next, String name) {
        if (previous != null && !previous.equals(next))
            throw new IllegalArgumentException("Join Battle " + name + " timestamp changed.");
    }

    private static boolean participationAdvances(
            KOMEJoinBattleDeploymentReceipt.ParticipationRecovery previous,
            KOMEJoinBattleDeploymentReceipt.ParticipationRecovery next) {
        if (previous == next) return true;
        return previous == KOMEJoinBattleDeploymentReceipt.ParticipationRecovery.REGISTRATION_REQUIRED
            && next == KOMEJoinBattleDeploymentReceipt.ParticipationRecovery.REGISTERED_BY_RECEIPT;
    }

    private static boolean mountTransferAdvances(
            KOMEJoinBattleDeploymentReceipt.MountTransferPhase previous,
            KOMEJoinBattleDeploymentReceipt.MountTransferPhase next) {
        if (previous == next) return true;
        if (previous == null || next == null) return false;
        if (next == KOMEJoinBattleDeploymentReceipt.MountTransferPhase.TERMINAL) return true;
        return next.ordinal() > previous.ordinal();
    }

    synchronized PersistenceSnapshot persistenceSnapshot() {
        return new PersistenceSnapshot(receipts, allocator.getNextSequence());
    }

    static KOMEJoinBattleDeploymentRegistry restore(
            Map<String, KOMEJoinBattleDeploymentReceipt> restored, long nextSequence) {
        if (restored == null) throw new IllegalArgumentException("Join Battle receipt registry is required.");
        if (nextSequence < 1L) throw new IllegalArgumentException("Next Join Battle receipt sequence must be positive.");
        long highest = 0L;
        Set<String> tokens = new HashSet<String>();
        Set<UUID> openPlayers = new HashSet<UUID>();
        Map<String, KOMEJoinBattleDeploymentReceipt> validated =
            new LinkedHashMap<String, KOMEJoinBattleDeploymentReceipt>();
        for (Map.Entry<String, KOMEJoinBattleDeploymentReceipt> entry : restored.entrySet()) {
            KOMEJoinBattleDeploymentReceipt value = entry.getValue();
            if (value == null || !entry.getKey().equals(value.getReceiptId()))
                throw new IllegalArgumentException("Join Battle receipt map identity disagrees.");
            KOMEJoinBattleReceiptIdAllocator.requireIdentity(value.getReceiptId());
            highest = Math.max(highest, KOMEJoinBattleReceiptIdAllocator.sequenceOf(value.getReceiptId()));
            if (!tokens.add(value.getActionToken()))
                throw new IllegalArgumentException("Duplicate Join Battle action token.");
            if (value.isOpen() && !openPlayers.add(value.getPlayerId()))
                throw new IllegalArgumentException("Player has multiple open Join Battle receipts: " + value.getPlayerId());
            if (validated.put(value.getReceiptId(), value) != null)
                throw new IllegalArgumentException("Duplicate Join Battle receipt identity: " + value.getReceiptId());
        }
        if (nextSequence <= highest)
            throw new IllegalArgumentException("Next Join Battle receipt sequence does not exceed persisted identities.");
        KOMEJoinBattleDeploymentRegistry registry = new KOMEJoinBattleDeploymentRegistry(nextSequence);
        registry.receipts.putAll(validated);
        return registry;
    }

    synchronized void replaceFrom(KOMEJoinBattleDeploymentRegistry source) {
        PersistenceSnapshot snapshot = source.persistenceSnapshot();
        KOMEJoinBattleDeploymentRegistry validated = restore(snapshot.receipts, snapshot.nextSequence);
        receipts.clear(); receipts.putAll(validated.receipts);
        allocator.setNextSequence(validated.allocator.getNextSequence());
    }

    static final class PersistenceSnapshot {
        final Map<String, KOMEJoinBattleDeploymentReceipt> receipts;
        final long nextSequence;
        PersistenceSnapshot(Map<String, KOMEJoinBattleDeploymentReceipt> receipts, long nextSequence) {
            this.receipts = Collections.unmodifiableMap(
                new LinkedHashMap<String, KOMEJoinBattleDeploymentReceipt>(receipts));
            this.nextSequence = nextSequence;
        }
    }
}
