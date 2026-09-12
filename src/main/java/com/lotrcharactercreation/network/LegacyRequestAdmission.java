package com.lotrcharactercreation.network;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Constant-time, shared admission accounting for legacy C2S request queues. */
final class LegacyRequestAdmission {

    private final int maximumPerPlayer;
    private final int maximumTotal;
    private final Map<UUID, Integer> pendingByPlayer = new HashMap<UUID, Integer>();
    private int pendingTotal;

    LegacyRequestAdmission(int maximumPerPlayer, int maximumTotal) {
        if (maximumPerPlayer <= 0 || maximumTotal < maximumPerPlayer) {
            throw new IllegalArgumentException("legacy request admission limits are invalid");
        }
        this.maximumPerPlayer = maximumPerPlayer;
        this.maximumTotal = maximumTotal;
    }

    synchronized boolean tryAcquire(UUID playerId) {
        if (playerId == null || pendingTotal >= maximumTotal) {
            return false;
        }
        Integer stored = pendingByPlayer.get(playerId);
        int playerCount = stored == null ? 0 : stored.intValue();
        if (playerCount >= maximumPerPlayer) {
            return false;
        }

        pendingByPlayer.put(playerId, Integer.valueOf(playerCount + 1));
        pendingTotal++;
        return true;
    }

    synchronized void release(UUID playerId) {
        Integer stored = pendingByPlayer.get(playerId);
        if (stored == null || stored.intValue() <= 0) {
            return;
        }

        if (stored.intValue() == 1) {
            pendingByPlayer.remove(playerId);
        } else {
            pendingByPlayer.put(playerId, Integer.valueOf(stored.intValue() - 1));
        }
        pendingTotal--;
    }

    synchronized void clearPlayer(UUID playerId) {
        Integer removed = pendingByPlayer.remove(playerId);
        if (removed != null) {
            pendingTotal -= removed.intValue();
        }
    }

    synchronized void clearAll() {
        pendingByPlayer.clear();
        pendingTotal = 0;
    }

    synchronized int getPendingForPlayer(UUID playerId) {
        Integer count = pendingByPlayer.get(playerId);
        return count == null ? 0 : count.intValue();
    }

    synchronized int getPendingTotal() {
        return pendingTotal;
    }
}
