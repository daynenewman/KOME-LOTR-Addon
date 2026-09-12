package com.lotrcharactercreation.network;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/** Bounded per-connection server transfer window with deterministic FIFO queuing. */
public final class CustomSkinTransferWindow {

    private final Queue<CustomSkinRequestIdentity> queued = new ArrayDeque<CustomSkinRequestIdentity>();
    private final Set<CustomSkinRequestIdentity> queuedSet = new HashSet<CustomSkinRequestIdentity>();
    private final Map<Long, CustomSkinRequestIdentity> active =
        new HashMap<Long, CustomSkinRequestIdentity>();

    public boolean request(CustomSkinRequestIdentity identity) {
        if (identity == null || queuedSet.contains(identity) || active.containsValue(identity)
            || queued.size() >= CustomSkinSyncProtocol.MAX_QUEUED_TRANSFERS) {
            return false;
        }
        queued.add(identity);
        queuedSet.add(identity);
        return true;
    }

    public CustomSkinRequestIdentity beginNext(long transferId) {
        if (transferId <= 0L || active.size() >= CustomSkinSyncProtocol.MAX_ACTIVE_TRANSFERS
            || active.containsKey(Long.valueOf(transferId))) {
            return null;
        }
        CustomSkinRequestIdentity identity = queued.poll();
        if (identity == null) {
            return null;
        }
        queuedSet.remove(identity);
        active.put(Long.valueOf(transferId), identity);
        return identity;
    }

    public boolean complete(long transferId, CustomSkinRequestIdentity expectedIdentity) {
        CustomSkinRequestIdentity activeIdentity = active.get(Long.valueOf(transferId));
        if (activeIdentity == null || !activeIdentity.equals(expectedIdentity)) {
            return false;
        }
        active.remove(Long.valueOf(transferId));
        return true;
    }

    public int getActiveCount() {
        return active.size();
    }

    public int getQueuedCount() {
        return queued.size();
    }

    public void clear() {
        queued.clear();
        queuedSet.clear();
        active.clear();
    }
}
