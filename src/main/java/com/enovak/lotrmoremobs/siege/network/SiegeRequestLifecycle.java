package com.enovak.lotrmoremobs.siege.network;

import com.enovak.lotrmoremobs.siege.creation.GateCreationManager;
import com.enovak.lotrmoremobs.siege.ram.RamControlManager;
import com.enovak.lotrmoremobs.siege.repair.GateManagementManager;
import com.enovak.lotrmoremobs.siege.management.GateInspectionSessionManager;
import com.enovak.lotrmoremobs.siege.edit.GateEditSessionManager;
import com.enovak.lotrmoremobs.siege.edit.GateEditRequestManager;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;

/** Cleans bounded siege C2S intake without retaining player/world objects. */
public final class SiegeRequestLifecycle {

    private SiegeRequestLifecycle() {
    }

    public static void clearPlayer(EntityPlayerMP player) {
        if (player == null) {
            return;
        }
        UUID playerUuid = player.getUniqueID();
        GateCreationManager.clearPendingForPlayer(playerUuid);
        GateManagementManager.clearPendingForPlayer(playerUuid);
        RamControlManager.clearPendingForPlayer(playerUuid);
        SiegeRequestLimiter.clearPlayer(playerUuid);
    }

    public static void resetServerState() {
        GateCreationManager.resetServerState();
        GateManagementManager.resetServerState();
        GateInspectionSessionManager.resetServerState();
        GateEditRequestManager.resetServerState();
        GateEditSessionManager.resetServerState();
        RamControlManager.resetServerState();
        SiegeRequestLimiter.clearAll();
    }
}
