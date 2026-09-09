package com.enovak.lotrmoremobs.siege.client;

import com.enovak.lotrmoremobs.siege.management.FinalizedGateSnapshot;

/** Client display receipt only; the server remains authoritative. */
public final class GateFinalizedInspectionClientContext {

    private static FinalizedGateSnapshot snapshot;

    private GateFinalizedInspectionClientContext() {
    }

    public static void apply(FinalizedGateSnapshot receivedSnapshot) {
        snapshot = receivedSnapshot;
    }

    public static void clear() {
        snapshot = null;
    }

    public static boolean isActive() {
        return snapshot != null;
    }

    public static FinalizedGateSnapshot getSnapshot() {
        return snapshot;
    }
}
