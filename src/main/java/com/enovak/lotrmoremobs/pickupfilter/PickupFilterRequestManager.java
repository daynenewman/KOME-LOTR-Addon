package com.enovak.lotrmoremobs.pickupfilter;

import com.enovak.lotrmoremobs.config.MumakilConfig;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

/**
 * Bounded Netty-thread intake for Pickup Filter C2S requests.
 * Persistent player state is mutated only from processServerTick().
 */
public final class PickupFilterRequestManager {

    private static final int MAX_PENDING_REQUESTS = 256;
    private static final int MAX_PENDING_PER_PLAYER = 32;
    private static final int MAX_REQUESTS_PER_TICK = 32;
    private static final double REQUESTS_PER_SECOND = 20.0D;
    private static final int REQUEST_BURST = 32;

    private static final Deque<PendingRequest> PENDING =
            new ArrayDeque<PendingRequest>();
    private static final Map<UUID, Integer> PENDING_PER_PLAYER =
            new HashMap<UUID, Integer>();
    private static final Map<UUID, TokenBucket> RATE_BUCKETS =
            new HashMap<UUID, TokenBucket>();

    private PickupFilterRequestManager() {
    }

    public static synchronized boolean enqueueToggle(
            EntityPlayerMP player,
            ItemStack stack
    ) {
        ItemStack sanitized = PlayerPickupFilterData.sanitizeStack(stack);
        return sanitized != null && enqueue(
                player,
                RequestType.TOGGLE,
                sanitized
        );
    }

    public static synchronized boolean enqueueClear(EntityPlayerMP player) {
        return enqueue(player, RequestType.CLEAR, null);
    }

    public static void processServerTick() {
        for (int processed = 0;
             processed < MAX_REQUESTS_PER_TICK;
             ++processed) {
            PendingRequest request = pollPending();
            if (request == null) {
                return;
            }

            if (!MumakilConfig.enableItemPickupFilter) {
                continue;
            }

            EntityPlayerMP player = request.player.get();
            if (player == null
                    || !request.playerUuid.equals(player.getUniqueID())
                    || player.isDead
                    || !player.isEntityAlive()) {
                continue;
            }

            if (request.type == RequestType.CLEAR) {
                PlayerPickupFilterData.clearExcludedItems(player);
            } else if (request.type == RequestType.TOGGLE
                    && request.stack != null) {
                if (PlayerPickupFilterData.isExcluded(player, request.stack)) {
                    PlayerPickupFilterData.removeExcludedItem(
                            player,
                            request.stack
                    );
                } else {
                    PlayerPickupFilterData.addExcludedItem(
                            player,
                            request.stack
                    );
                }
            }

            PickupFilterNetwork.syncToPlayer(player);
        }
    }

    private static synchronized PendingRequest pollPending() {
        PendingRequest request = PENDING.pollFirst();
        if (request != null) {
            decrementPending(request.playerUuid);
        }
        return request;
    }

    public static synchronized void clearPlayer(UUID playerUuid) {
        if (playerUuid == null) {
            return;
        }

        java.util.Iterator<PendingRequest> iterator = PENDING.iterator();
        while (iterator.hasNext()) {
            PendingRequest request = iterator.next();
            if (playerUuid.equals(request.playerUuid)) {
                iterator.remove();
            }
        }
        PENDING_PER_PLAYER.remove(playerUuid);
        RATE_BUCKETS.remove(playerUuid);
    }

    public static synchronized void resetServerState() {
        PENDING.clear();
        PENDING_PER_PLAYER.clear();
        RATE_BUCKETS.clear();
    }

    private static boolean enqueue(
            EntityPlayerMP player,
            RequestType type,
            ItemStack stack
    ) {
        if (!MumakilConfig.enableItemPickupFilter
                || player == null
                || type == null) {
            return false;
        }

        UUID playerUuid = player.getUniqueID();
        if (playerUuid == null || !tryAcquire(playerUuid)) {
            return false;
        }

        Integer pending = PENDING_PER_PLAYER.get(playerUuid);
        int playerPending = pending == null ? 0 : pending.intValue();
        if (PENDING.size() >= MAX_PENDING_REQUESTS
                || playerPending >= MAX_PENDING_PER_PLAYER) {
            return false;
        }

        PENDING.addLast(new PendingRequest(playerUuid, player, type, stack));
        PENDING_PER_PLAYER.put(playerUuid, playerPending + 1);
        return true;
    }

    private static boolean tryAcquire(UUID playerUuid) {
        TokenBucket bucket = RATE_BUCKETS.get(playerUuid);
        if (bucket == null) {
            bucket = new TokenBucket(REQUESTS_PER_SECOND, REQUEST_BURST);
            RATE_BUCKETS.put(playerUuid, bucket);
        }
        return bucket.tryAcquire(System.nanoTime());
    }

    private static void decrementPending(UUID playerUuid) {
        Integer count = PENDING_PER_PLAYER.get(playerUuid);
        if (count == null || count.intValue() <= 1) {
            PENDING_PER_PLAYER.remove(playerUuid);
        } else {
            PENDING_PER_PLAYER.put(playerUuid, count.intValue() - 1);
        }
    }

    private enum RequestType {
        TOGGLE,
        CLEAR
    }

    private static final class PendingRequest {
        private final UUID playerUuid;
        private final WeakReference<EntityPlayerMP> player;
        private final RequestType type;
        private final ItemStack stack;

        private PendingRequest(
                UUID playerUuid,
                EntityPlayerMP player,
                RequestType type,
                ItemStack stack
        ) {
            this.playerUuid = playerUuid;
            this.player = new WeakReference<EntityPlayerMP>(player);
            this.type = type;
            this.stack = stack == null ? null : stack.copy();
        }
    }

    private static final class TokenBucket {
        private final double refillPerNanosecond;
        private final int capacity;
        private double tokens;
        private long lastRefillNanos;

        private TokenBucket(double requestsPerSecond, int capacity) {
            refillPerNanosecond = requestsPerSecond / 1000000000.0D;
            this.capacity = capacity;
            tokens = capacity;
            lastRefillNanos = System.nanoTime();
        }

        private boolean tryAcquire(long nowNanos) {
            long elapsed = nowNanos - lastRefillNanos;
            if (elapsed > 0L) {
                tokens = Math.min(
                        capacity,
                        tokens + elapsed * refillPerNanosecond
                );
                lastRefillNanos = nowNanos;
            }
            if (tokens < 1.0D) {
                return false;
            }
            tokens -= 1.0D;
            return true;
        }
    }
}
