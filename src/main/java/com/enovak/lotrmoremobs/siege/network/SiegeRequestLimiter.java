package com.enovak.lotrmoremobs.siege.network;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Thread-safe, UUID-keyed token buckets for hostile C2S siege intake.
 * Authoritative world, permission, distance, and state checks still run on
 * the server thread after a request has passed this availability guard.
 */
public final class SiegeRequestLimiter {

    private static final int MIN_WORLD_XZ = -30000000;
    private static final int MAX_WORLD_XZ = 30000000;
    private static final int MIN_WORLD_Y = 0;
    private static final int MAX_WORLD_Y = 256;

    private static final Map<UUID, EnumMap<RateClass, TokenBucket>> BUCKETS =
            new HashMap<UUID, EnumMap<RateClass, TokenBucket>>();

    private SiegeRequestLimiter() {
    }

    public static synchronized boolean tryAcquire(
            UUID playerUuid,
            RateClass rateClass
    ) {
        if (playerUuid == null || rateClass == null) {
            return false;
        }
        EnumMap<RateClass, TokenBucket> playerBuckets =
                BUCKETS.get(playerUuid);
        if (playerBuckets == null) {
            playerBuckets = new EnumMap<RateClass, TokenBucket>(
                    RateClass.class
            );
            BUCKETS.put(playerUuid, playerBuckets);
        }
        TokenBucket bucket = playerBuckets.get(rateClass);
        if (bucket == null) {
            bucket = new TokenBucket(
                    rateClass.requestsPerSecond,
                    rateClass.burst
            );
            playerBuckets.put(rateClass, bucket);
        }
        return bucket.tryAcquire(System.nanoTime());
    }

    public static synchronized void clearPlayer(UUID playerUuid) {
        if (playerUuid != null) {
            BUCKETS.remove(playerUuid);
        }
    }

    public static synchronized void clearAll() {
        BUCKETS.clear();
    }

    public static boolean isSaneBlockPosition(int x, int y, int z) {
        return x >= MIN_WORLD_XZ
                && x < MAX_WORLD_XZ
                && y >= MIN_WORLD_Y
                && y < MAX_WORLD_Y
                && z >= MIN_WORLD_XZ
                && z < MAX_WORLD_XZ;
    }

    public enum RateClass {
        CREATION_SELECTION(10.0D, 20),
        CREATION_ACTION(5.0D, 8),
        MANAGEMENT_UPDATE(4.0D, 8),
        MANAGEMENT_ACTION(5.0D, 8),
        RAM_CONTROL(5.0D, 8),
        RAM_TARGET(5.0D, 8),
        EDIT_SESSION_ACTION(4.0D, 8),
        EDIT_DRAFT_ACTION(12.0D, 24),
        EDIT_PREFLIGHT(2.0D, 4),
        EDIT_COMMIT(0.5D, 2);

        private final double requestsPerSecond;
        private final int burst;

        RateClass(double requestsPerSecond, int burst) {
            this.requestsPerSecond = requestsPerSecond;
            this.burst = burst;
        }
    }

    private static final class TokenBucket {
        private final double refillPerNanosecond;
        private final int capacity;
        private double tokens;
        private long lastRefillNanos;

        private TokenBucket(double requestsPerSecond, int capacity) {
            this.refillPerNanosecond = requestsPerSecond / 1000000000.0D;
            this.capacity = capacity;
            this.tokens = capacity;
            this.lastRefillNanos = System.nanoTime();
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
