package com.enovak.lotrmoremobs.siege.management;

import com.enovak.lotrmoremobs.Main;
import com.enovak.lotrmoremobs.siege.SiegeRegistry;
import com.enovak.lotrmoremobs.siege.gate.SiegeGateOwnershipData;
import com.enovak.lotrmoremobs.siege.network.GateFinalizedInspectionSnapshotPacket;
import com.enovak.lotrmoremobs.siege.repair.GateManagementManager;
import com.enovak.lotrmoremobs.siege.tile.TileEntitySiegeGate;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

/**
 * Owns only transient, player-bound, immutable INSPECT_EXISTING sessions.
 * It never changes controller, world, or durable ownership data.
 */
public final class GateInspectionSessionManager {

    public static final long SESSION_TIMEOUT_TICKS = 20L * 60L * 5L;
    private static final double MAX_DISTANCE_SQ =
            GateManagementManager.MAX_MANAGEMENT_DISTANCE
                    * GateManagementManager.MAX_MANAGEMENT_DISTANCE;
    private static final Map<UUID, GateInspectionSession> SESSIONS_BY_PLAYER =
            new HashMap<UUID, GateInspectionSession>();
    private static long serverTick;

    private GateInspectionSessionManager() {
    }

    public static void openInspection(
            EntityPlayerMP player,
            TileEntitySiegeGate controller
    ) {
        String failure = validateOpen(player, controller);
        if (failure != null) {
            sendMessage(player, failure);
            return;
        }

        FinalizedGateSnapshot snapshot;
        try {
            snapshot = FinalizedGateSnapshot.fromController(
                    controller,
                    controller.getExistingGateUuid()
            );
        } catch (IllegalArgumentException exception) {
            sendMessage(player, "Gate structure is unavailable for inspection.");
            return;
        }

        GateInspectionSession session = new GateInspectionSession(
                player.getUniqueID(),
                snapshot,
                safeExpiryTick(serverTick)
        );
        SESSIONS_BY_PLAYER.put(player.getUniqueID(), session);
        Main.network.sendTo(
                new GateFinalizedInspectionSnapshotPacket(snapshot),
                player
        );
    }

    public static GateInspectionSession getSession(UUID playerUuid) {
        return playerUuid == null ? null : SESSIONS_BY_PLAYER.get(playerUuid);
    }

    public static void closeForPlayer(EntityPlayerMP player) {
        if (player != null) {
            closeForPlayer(player.getUniqueID());
        }
    }

    public static void closeForPlayer(UUID playerUuid) {
        if (playerUuid != null) {
            SESSIONS_BY_PLAYER.remove(playerUuid);
        }
    }

    public static void tick() {
        if (serverTick < Long.MAX_VALUE) {
            ++serverTick;
        }
        Iterator<Map.Entry<UUID, GateInspectionSession>> iterator =
                SESSIONS_BY_PLAYER.entrySet().iterator();
        while (iterator.hasNext()) {
            GateInspectionSession session = iterator.next().getValue();
            if (session == null || session.isExpired(serverTick)) {
                iterator.remove();
            }
        }
    }

    public static void resetServerState() {
        SESSIONS_BY_PLAYER.clear();
        serverTick = 0L;
    }

    private static String validateOpen(
            EntityPlayerMP player,
            TileEntitySiegeGate controller
    ) {
        if (player == null || controller == null || player.isDead
                || player.worldObj == null || player.worldObj.isRemote
                || controller.getWorldObj() != player.worldObj
                || controller.isInvalid()) {
            return "Gate structure is unavailable.";
        }
        World world = player.worldObj;
        if (!world.blockExists(controller.xCoord, controller.yCoord,
                controller.zCoord)
                || world.getBlock(controller.xCoord, controller.yCoord,
                        controller.zCoord) != SiegeRegistry.gateController) {
            return "Gate structure is unavailable.";
        }
        TileEntity exactTile = world.getTileEntity(
                controller.xCoord,
                controller.yCoord,
                controller.zCoord
        );
        if (exactTile != controller
                || player.getDistanceSq(
                        controller.xCoord + 0.5D,
                        controller.yCoord + 0.5D,
                        controller.zCoord + 0.5D
                ) > MAX_DISTANCE_SQ) {
            return "Gate structure is unavailable.";
        }
        if (!controller.isFinalized()) {
            return "This Siege Gate is not finalized.";
        }
        if (controller.isGateStructureQuarantined()) {
            return "This Siege Gate is quarantined and cannot be inspected.";
        }
        if (!controller.canManage(player)) {
            return "You do not have permission to inspect this gate structure.";
        }
        if (controller.getExistingGateUuid() == null
                || controller.getStructureRevision() <= 0) {
            return "Gate ownership data is inconsistent.";
        }
        SiegeGateOwnershipData ownership =
                SiegeGateOwnershipData.get(world, false);
        if (ownership == null || !ownership.matchesActiveController(controller)) {
            return "Gate ownership data is inconsistent.";
        }
        return null;
    }

    private static long safeExpiryTick(long currentTick) {
        return currentTick > Long.MAX_VALUE - SESSION_TIMEOUT_TICKS
                ? Long.MAX_VALUE
                : currentTick + SESSION_TIMEOUT_TICKS;
    }

    private static void sendMessage(EntityPlayerMP player, String message) {
        if (player != null && message != null) {
            player.addChatMessage(new ChatComponentText(message));
        }
    }
}
