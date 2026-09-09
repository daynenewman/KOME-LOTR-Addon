package com.enovak.lotrmoremobs.siege.repair;

import com.enovak.lotrmoremobs.Main;
import com.enovak.lotrmoremobs.siege.access.GateAccess;
import com.enovak.lotrmoremobs.siege.network.GateManagementActionPacket;
import com.enovak.lotrmoremobs.siege.network.GateManagementOpenPacket;
import com.enovak.lotrmoremobs.siege.network.SiegeNetwork;
import com.enovak.lotrmoremobs.siege.network.SiegeRequestLimiter;
import com.enovak.lotrmoremobs.siege.management.GateInspectionSessionManager;
import com.enovak.lotrmoremobs.siege.gate.GateControlMode;
import com.enovak.lotrmoremobs.siege.edit.GateEditSession;
import com.enovak.lotrmoremobs.siege.edit.GateEditSessionManager;
import com.enovak.lotrmoremobs.siege.network.GateEditDraftSnapshotPacket;
import com.enovak.lotrmoremobs.siege.network.GateEditPreflightSnapshotPacket;
import com.enovak.lotrmoremobs.siege.tile.TileEntitySiegeGate;
import com.mojang.authlib.GameProfile;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;

public final class GateManagementManager {

    public static final double MAX_MANAGEMENT_DISTANCE = 16.0D;

    private static final double MAX_MANAGEMENT_DISTANCE_SQ =
            MAX_MANAGEMENT_DISTANCE * MAX_MANAGEMENT_DISTANCE;
    private static final int MAX_PENDING_ACTIONS = 256;
    private static final int MAX_PENDING_ACTIONS_PER_PLAYER = 16;
    private static final int ACTION_PROCESSING_BUDGET_PER_TICK = 32;
    private static final Object PENDING_ACTION_LOCK = new Object();
    private static final ArrayDeque<PendingAction> PENDING_ACTIONS =
            new ArrayDeque<PendingAction>();
    private static final Map<UUID, Integer> PENDING_COUNTS_BY_PLAYER =
            new HashMap<UUID, Integer>();

    private GateManagementManager() {
    }

    public static void open(
            EntityPlayerMP player,
            TileEntitySiegeGate controller
    ) {
        if (!isNearbyFinalizedGate(player, controller)) {
            return;
        }
        /*
         * Gate Management is an inspection surface as well as a configuration
         * surface. Any nearby player may open it; individual controls remain
         * role-gated and every mutation is still revalidated server-side.
         */
        SiegeNetwork.syncGateHealth(controller);
        SiegeNetwork.syncGateRepair(controller);
        SiegeNetwork.syncGateAccess(controller);
        Main.network.sendTo(
                new GateManagementOpenPacket(
                        player.dimension,
                        controller.xCoord,
                        controller.yCoord,
                        controller.zCoord,
                        controller.canManage(player),
                        controller.canManagePlayerAccess(player),
                        GateAccess.isAdministrativePlayer(player)
                ),
                player
        );
        if (controller.canManage(player)) {
            GateInspectionSessionManager.openInspection(player, controller);
            GateEditSession edit = GateEditSessionManager.getMatchingSession(player, controller);
            if (edit != null) {
                Main.network.sendTo(new GateEditDraftSnapshotPacket(edit), player);
                Main.network.sendTo(new GateEditPreflightSnapshotPacket(edit, GateEditSessionManager.evaluatePreflight(player, edit)), player);
            }
        }
    }

    public static void queueAction(
            EntityPlayerMP player,
            int action,
            int dimensionId,
            int x,
            int y,
            int z,
            int value,
            String text
    ) {
        if (player == null
                || !GateManagementActionPacket.isKnownAction(action)
                || !GateManagementActionPacket.isValidRequestText(
                        action,
                        text == null ? "" : text
                )
                || !SiegeRequestLimiter.isSaneBlockPosition(x, y, z)) {
            return;
        }
        SiegeRequestLimiter.RateClass rateClass =
                GateManagementActionPacket.isCoalescibleUpdate(action)
                ? SiegeRequestLimiter.RateClass.MANAGEMENT_UPDATE
                : SiegeRequestLimiter.RateClass.MANAGEMENT_ACTION;
        if (!SiegeRequestLimiter.tryAcquire(
                player.getUniqueID(),
                rateClass
        )) {
            return;
        }
        PendingAction request = new PendingAction(
                player,
                action,
                dimensionId,
                x,
                y,
                z,
                value,
                text
        );
        synchronized (PENDING_ACTION_LOCK) {
            if (GateManagementActionPacket.isCoalescibleUpdate(action)) {
                removeCoalescedUpdateLocked(request);
            } else if (isSingleTransitionAction(action)
                    && hasEquivalentActionLocked(request)) {
                return;
            }
            offerLocked(request);
        }
    }

    public static void processQueuedRequests() {
        for (int processed = 0;
                processed < ACTION_PROCESSING_BUDGET_PER_TICK;
                ++processed) {
            PendingAction request = pollAction();
            if (request == null) {
                break;
            }
            processRequest(request);
        }
    }

    public static void clearPendingForPlayer(UUID playerUuid) {
        if (playerUuid == null) {
            return;
        }
        synchronized (PENDING_ACTION_LOCK) {
            Iterator<PendingAction> iterator = PENDING_ACTIONS.iterator();
            while (iterator.hasNext()) {
                PendingAction request = iterator.next();
                if (playerUuid.equals(request.playerUuid)) {
                    iterator.remove();
                }
            }
            PENDING_COUNTS_BY_PLAYER.remove(playerUuid);
        }
    }

    public static void resetServerState() {
        synchronized (PENDING_ACTION_LOCK) {
            PENDING_ACTIONS.clear();
            PENDING_COUNTS_BY_PLAYER.clear();
        }
    }

    private static void processRequest(
            PendingAction request
    ) {
        EntityPlayerMP player =
                request.player;

        if (player == null
                || player.isDead
                || player.worldObj == null
                || player.worldObj.isRemote
                || player.dimension
                != request.dimensionId
                || !player.worldObj.blockExists(
                request.x,
                request.y,
                request.z
        )) {

            return;
        }

        TileEntity tileEntity =
                player.worldObj.getTileEntity(
                        request.x,
                        request.y,
                        request.z
                );

        if (!(tileEntity
                instanceof TileEntitySiegeGate)
                || !isNearbyFinalizedGate(
                player,
                (TileEntitySiegeGate) tileEntity
        )) {

            sendMessage(
                    player,
                    "That Siege Gate is no longer available."
            );

            return;
        }

        TileEntitySiegeGate gate =
                (TileEntitySiegeGate) tileEntity;

        if (gate.isPersistentGateMutationLocked()) {
            sendMessage(
                    player,
                    "Gate update in progress."
            );

            return;
        }

        if (request.action
                == GateManagementActionPacket.BEGIN_REPAIR) {

            processBeginRepair(
                    player,
                    gate
            );

            return;
        }

        if (request.action
                == GateManagementActionPacket.CLAIM_OWNERLESS) {

            sendMessage(
                    player,
                    gate.claimOwnerlessGate(
                            player
                    )
                            ? "You claimed this legacy Siege Gate."
                            : "Only a Creative player or server operator can claim this gate."
            );

            return;
        }

        if (request.action
                == GateManagementActionPacket.SET_MAX_HEALTH) {

            if (!gate.setMaxHealthOverride(
                    player,
                    request.value
            )) {
                sendMessage(
                        player,
                        "Maximum health can only be changed in Creative mode."
                );
            }

            return;
        }

        if (!gate.canManage(
                player
        )) {
            GateAccess.deny(
                    player,
                    gate
            );

            return;
        }

        if (request.action
                == GateManagementActionPacket.SET_NAME) {

            if (!gate.setGateName(
                    player,
                    request.text
            )) {
                sendMessage(
                        player,
                        "Gate name could not be updated."
                );
            }

        } else if (request.action
                == GateManagementActionPacket.SET_FACTION) {

            if (!gate.setGateFaction(
                    player,
                    request.text
            )) {
                sendMessage(
                        player,
                        "You need at least +100 alignment with that faction."
                );
            }

        } else if (request.action
                == GateManagementActionPacket.SET_ALIGNMENT) {

            if (!gate.setRequiredAlignment(
                    player,
                    request.value
            )) {
                sendMessage(
                        player,
                        "Required alignment could not be updated."
                );
            }

        } else if (request.action
                == GateManagementActionPacket.SET_FACTION_ACCESS) {

            if (!gate.setFactionAccessEnabled(
                    player,
                    request.value != 0
            )) {
                sendMessage(
                        player,
                        "Faction access could not be updated."
                );
            }

        } else if (request.action
                == GateManagementActionPacket.SET_GATE_CONTROL_MODE) {

            GateControlMode requestedMode =
                    GateControlMode.fromNetworkId(
                            request.value
                    );

            if (!gate.setGateControlMode(
                    player,
                    requestedMode
            )) {
                sendMessage(
                        player,
                        "Gate control mode could not be updated."
                );
            }

        } else if (request.action
                == GateManagementActionPacket
                .SET_PLAYER_ACCESS_LEVEL) {

            processPlayerAccessSet(
                    player,
                    gate,
                    request.text,
                    request.value
            );

        } else if (request.action
                == GateManagementActionPacket
                .REMOVE_PLAYER_ACCESS) {

            processPlayerAccessRemove(
                    player,
                    gate,
                    request.text
            );

        } else if (request.action
                == GateManagementActionPacket
                .SET_CONTROLLER_APPEARANCE) {

            if (!gate.setControllerAppearance(
                    player,
                    request.text,
                    request.value
            )) {
                sendMessage(
                        player,
                        "That block cannot be used as the controller appearance."
                );
            }
        }
    }

    private static void processBeginRepair(
            EntityPlayerMP player,
            TileEntitySiegeGate gate
    ) {
        if (!gate.canRepair(player)) {
            GateAccess.deny(player, gate);
            return;
        }
        GateRepairStartResult result = gate.beginRepair(player);
        if (result == GateRepairStartResult.STARTED) {
            sendMessage(
                    player,
                    "Repair started for "
                            + gate.getRepairPurchasedHealth()
                            + " HP at a cost of "
                            + gate.getRepairPurchasedCoinValue()
                            + " coin-value."
            );
        } else if (result == GateRepairStartResult.FULL_HEALTH) {
            sendMessage(player, "This Siege Gate is already at full health.");
        } else if (result == GateRepairStartResult.ALREADY_ACTIVE) {
            sendMessage(player, "This Siege Gate already has an active repair job.");
        } else if (result == GateRepairStartResult.INSUFFICIENT_FUNDS) {
            sendMessage(
                    player,
                    "Insufficient LOTR coin value. Required: "
                            + gate.getRepairCostToFull()
                            + "."
            );
        } else {
            sendMessage(player, "Repair could not be started.");
        }
    }

    private static void processPlayerAccessSet(
            EntityPlayerMP player,
            TileEntitySiegeGate gate,
            String target,
            int level
    ) {
        if (!gate.canManagePlayerAccess(player)) {
            sendMessage(
                    player,
                    "Only the gate owner, an Editor, or a server administrator can manage Player Access."
            );
            return;
        }

        UUID targetUuid =
                resolvePlayerUuid(
                        target
                );

        if (targetUuid == null) {
            sendMessage(
                    player,
                    "Player not found."
            );

            return;
        }

        if ((level == GateManagementActionPacket.ACCESS_LEVEL_EDITOR
                || gate.getEditorUuids().contains(targetUuid))
                && !gate.canManageEditors(player)) {
            sendMessage(
                    player,
                    "Only the gate owner or a server administrator can change Editor roles."
            );
            return;
        }

        boolean changed =
                gate.setPlayerAccessLevel(
                        player,
                        targetUuid,
                        level
                );

        if (!changed) {
            sendMessage(
                    player,
                    "Player access could not be updated."
            );
        }
    }

    private static void processPlayerAccessRemove(
            EntityPlayerMP player,
            TileEntitySiegeGate gate,
            String target
    ) {
        if (!gate.canManagePlayerAccess(player)) {
            sendMessage(
                    player,
                    "Only the gate owner, an Editor, or a server administrator can manage Player Access."
            );
            return;
        }

        UUID targetUuid =
                resolvePlayerUuid(
                        target
                );

        if (targetUuid == null) {
            sendMessage(
                    player,
                    "Player not found."
            );

            return;
        }

        if (gate.getEditorUuids().contains(targetUuid)
                && !gate.canManageEditors(player)) {
            sendMessage(
                    player,
                    "Only the gate owner or a server administrator can remove Editors."
            );
            return;
        }

        if (!gate.removePlayerAccessEntry(
                player,
                targetUuid
        )) {
            sendMessage(
                    player,
                    "Player access could not be removed."
            );
        }
    }

    private static void processRoleToggle(
            EntityPlayerMP player,
            TileEntitySiegeGate gate,
            String target,
            int role
    ) {
        UUID targetUuid = resolvePlayerUuid(target);
        if (targetUuid == null) {
            sendMessage(
                    player,
                    "Unknown player. Use an online/cached player name or UUID."
            );
            return;
        }
        boolean changed;
        String roleName;
        if (role == 0) {
            changed = gate.toggleEditor(player, targetUuid);
            roleName = "editor";
        } else if (role == 1) {
            changed = gate.toggleOperator(player, targetUuid);
            roleName = "operator";
        } else {
            changed = gate.toggleWhitelist(player, targetUuid);
            roleName = "whitelist";
        }
        sendMessage(
                player,
                changed
                        ? "Toggled " + roleName + " access for " + targetUuid + "."
                        : "That " + roleName + " entry could not be changed."
        );
    }

    private static UUID resolvePlayerUuid(String nameOrUuid) {
        if (nameOrUuid == null || nameOrUuid.trim().isEmpty()) {
            return null;
        }
        String value = nameOrUuid.trim();
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
        }
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) {
            return null;
        }
        GameProfile profile = server.func_152358_ax().func_152655_a(value);
        return profile == null ? null : profile.getId();
    }

    private static boolean isNearbyFinalizedGate(
            EntityPlayerMP player,
            TileEntitySiegeGate controller
    ) {
        return player != null
                && controller != null
                && controller.getWorldObj() == player.worldObj
                && !controller.isInvalid()
                && controller.isFinalized()
                && player.getDistanceSq(
                        controller.xCoord + 0.5D,
                        controller.yCoord + 0.5D,
                        controller.zCoord + 0.5D
                ) <= MAX_MANAGEMENT_DISTANCE_SQ;
    }

    private static void sendMessage(
            EntityPlayerMP player,
            String message
    ) {
        player.addChatMessage(new ChatComponentText(message));
    }

    private static boolean isSingleTransitionAction(int action) {
        return action == GateManagementActionPacket.BEGIN_REPAIR
                || action == GateManagementActionPacket.CLAIM_OWNERLESS;
    }

    private static boolean hasEquivalentActionLocked(
            PendingAction candidate
    ) {
        for (PendingAction request : PENDING_ACTIONS) {
            if (request.hasSameTargetAndAction(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static void removeCoalescedUpdateLocked(
            PendingAction candidate
    ) {
        Iterator<PendingAction> iterator = PENDING_ACTIONS.iterator();
        while (iterator.hasNext()) {
            PendingAction request = iterator.next();
            if (request.hasSameTargetAndAction(candidate)) {
                iterator.remove();
                decrementPendingCountLocked(request.playerUuid);
                return;
            }
        }
    }

    private static boolean offerLocked(PendingAction request) {
        if (PENDING_ACTIONS.size() >= MAX_PENDING_ACTIONS) {
            return false;
        }
        Integer pendingCount = PENDING_COUNTS_BY_PLAYER.get(
                request.playerUuid
        );
        int count = pendingCount == null ? 0 : pendingCount.intValue();
        if (count >= MAX_PENDING_ACTIONS_PER_PLAYER) {
            return false;
        }
        PENDING_ACTIONS.addLast(request);
        PENDING_COUNTS_BY_PLAYER.put(
                request.playerUuid,
                Integer.valueOf(count + 1)
        );
        return true;
    }

    private static PendingAction pollAction() {
        synchronized (PENDING_ACTION_LOCK) {
            PendingAction request = PENDING_ACTIONS.pollFirst();
            if (request != null) {
                decrementPendingCountLocked(request.playerUuid);
            }
            return request;
        }
    }

    private static void decrementPendingCountLocked(UUID playerUuid) {
        Integer pendingCount = PENDING_COUNTS_BY_PLAYER.get(playerUuid);
        int remaining = pendingCount == null
                ? 0
                : pendingCount.intValue() - 1;
        if (remaining <= 0) {
            PENDING_COUNTS_BY_PLAYER.remove(playerUuid);
        } else {
            PENDING_COUNTS_BY_PLAYER.put(
                    playerUuid,
                    Integer.valueOf(remaining)
            );
        }
    }

    private static final class PendingAction {
        private final EntityPlayerMP player;
        private final UUID playerUuid;
        private final int action;
        private final int dimensionId;
        private final int x;
        private final int y;
        private final int z;
        private final int value;
        private final String text;

        private PendingAction(
                EntityPlayerMP player,
                int action,
                int dimensionId,
                int x,
                int y,
                int z,
                int value,
                String text
        ) {
            this.player = player;
            this.playerUuid = player.getUniqueID();
            this.action = action;
            this.dimensionId = dimensionId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.value = value;
            this.text = text == null ? "" : text;
        }

        private boolean hasSameTargetAndAction(PendingAction other) {
            return other != null
                    && playerUuid.equals(other.playerUuid)
                    && action == other.action
                    && dimensionId == other.dimensionId
                    && x == other.x
                    && y == other.y
                    && z == other.z;
        }
    }
}
