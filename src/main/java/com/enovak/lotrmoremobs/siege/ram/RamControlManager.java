package com.enovak.lotrmoremobs.siege.ram;

import com.enovak.lotrmoremobs.Main;
import com.enovak.lotrmoremobs.siege.access.GateAccess;
import com.enovak.lotrmoremobs.siege.gate.GatePartData;
import com.enovak.lotrmoremobs.siege.gate.GateState;
import com.enovak.lotrmoremobs.siege.network.RamControlOpenPacket;
import com.enovak.lotrmoremobs.siege.network.RamTargetModePacket;
import com.enovak.lotrmoremobs.siege.network.SiegeRequestLimiter;
import com.enovak.lotrmoremobs.siege.tile.TileEntitySiegeGate;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lotr.common.fac.LOTRFaction;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;

public final class RamControlManager {

    public static final double MAX_CONTROL_DISTANCE = 16.0D;
    public static final double TARGET_COMMAND_RANGE = 128.0D;
    public static final int DISBAND = 0;
    public static final int ENTER_TARGET_MODE = 1;
    public static final int ENTER_TARGET_MODE_REMOTE = 2;
    public static final int OPEN_CONTROL = 3;
    public static final int TOGGLE_PAUSE = 4;

    private static final double MAX_CONTROL_DISTANCE_SQ =
            MAX_CONTROL_DISTANCE * MAX_CONTROL_DISTANCE;
    private static final double TARGET_COMMAND_RANGE_SQ =
            TARGET_COMMAND_RANGE * TARGET_COMMAND_RANGE;
    private static final int MAX_PENDING_ACTIONS = 128;
    private static final int MAX_PENDING_TARGETS = 128;
    private static final int MAX_PENDING_PER_PLAYER = 8;
    private static final int ACTION_PROCESSING_BUDGET_PER_TICK = 32;
    private static final int TARGET_PROCESSING_BUDGET_PER_TICK = 32;
    private static final Object PENDING_LOCK = new Object();
    private static final ArrayDeque<PendingAction> PENDING_ACTIONS =
            new ArrayDeque<PendingAction>();
    private static final LinkedHashMap<TargetKey, PendingTargetSelection>
            PENDING_TARGETS =
            new LinkedHashMap<TargetKey, PendingTargetSelection>();
    private static final Map<UUID, Integer> PENDING_ACTION_COUNTS =
            new HashMap<UUID, Integer>();
    private static final Map<UUID, Integer> PENDING_TARGET_COUNTS =
            new HashMap<UUID, Integer>();

    private RamControlManager() {
    }

    public static void open(
            EntityPlayerMP player,
            EntityBattleRam ram
    ) {
        if (!canControlNearby(player, ram)) {
            return;
        }
        Main.network.sendTo(
                new RamControlOpenPacket(player.dimension, ram.getEntityId()),
                player
        );
    }

    public static void queueAction(
            EntityPlayerMP player,
            int action,
            int dimensionId,
            int entityId
    ) {
        if (player == null
                || !isKnownAction(action)
                || entityId <= 0
                || !SiegeRequestLimiter.tryAcquire(
                player.getUniqueID(),
                SiegeRequestLimiter.RateClass.RAM_CONTROL
        )) {
            return;
        }
        PendingAction request = new PendingAction(
                player,
                action,
                dimensionId,
                entityId
        );
        synchronized (PENDING_LOCK) {
            if (!hasEquivalentActionLocked(request)) {
                offerActionLocked(request);
            }
        }
    }

    public static void queueTargetSelection(
            EntityPlayerMP player,
            int dimensionId,
            int ramEntityId,
            int controllerX,
            int controllerY,
            int controllerZ
    ) {
        if (player == null
                || ramEntityId <= 0
                || !SiegeRequestLimiter.isSaneBlockPosition(
                controllerX,
                controllerY,
                controllerZ
        )
                || !SiegeRequestLimiter.tryAcquire(
                player.getUniqueID(),
                SiegeRequestLimiter.RateClass.RAM_TARGET
        )) {
            return;
        }
        PendingTargetSelection request = new PendingTargetSelection(
                player,
                dimensionId,
                ramEntityId,
                controllerX,
                controllerY,
                controllerZ
        );
        synchronized (PENDING_LOCK) {
            offerOrReplaceTargetLocked(request);
        }
    }

    public static void processQueuedActions() {
        for (int processed = 0;
             processed < ACTION_PROCESSING_BUDGET_PER_TICK;
             ++processed) {
            PendingAction request = pollAction();
            if (request == null) {
                break;
            }
            processAction(request);
        }
        for (int processed = 0;
             processed < TARGET_PROCESSING_BUDGET_PER_TICK;
             ++processed) {
            PendingTargetSelection selection = pollTarget();
            if (selection == null) {
                break;
            }
            processTargetSelection(selection);
        }
    }

    public static boolean isKnownAction(int action) {
        return action == DISBAND
                || action == ENTER_TARGET_MODE
                || action == ENTER_TARGET_MODE_REMOTE
                || action == OPEN_CONTROL
                || action == TOGGLE_PAUSE;
    }

    public static void clearPendingForPlayer(UUID playerUuid) {
        if (playerUuid == null) {
            return;
        }
        synchronized (PENDING_LOCK) {
            Iterator<PendingAction> actionIterator =
                    PENDING_ACTIONS.iterator();
            while (actionIterator.hasNext()) {
                if (playerUuid.equals(
                        actionIterator.next().playerUuid
                )) {
                    actionIterator.remove();
                }
            }
            Iterator<Map.Entry<TargetKey, PendingTargetSelection>>
                    targetIterator = PENDING_TARGETS.entrySet().iterator();
            while (targetIterator.hasNext()) {
                if (playerUuid.equals(
                        targetIterator.next().getValue().playerUuid
                )) {
                    targetIterator.remove();
                }
            }
            PENDING_ACTION_COUNTS.remove(playerUuid);
            PENDING_TARGET_COUNTS.remove(playerUuid);
        }
    }

    public static void syncTargetQueueToCommander(
            EntityBattleRam ram
    ) {
        if (ram == null
                || ram.worldObj == null
                || ram.worldObj.isRemote) {
            return;
        }

        EntityPlayer commander = ram.getCommander();
        if (!(commander instanceof EntityPlayerMP)) {
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP)commander;
        Main.network.sendTo(
                RamTargetModePacket.queueRefresh(
                        player.dimension,
                        ram
                ),
                player
        );
    }

    public static void resetServerState() {
        synchronized (PENDING_LOCK) {
            PENDING_ACTIONS.clear();
            PENDING_TARGETS.clear();
            PENDING_ACTION_COUNTS.clear();
            PENDING_TARGET_COUNTS.clear();
        }
    }

    private static void processAction(PendingAction request) {
        EntityPlayerMP player = request.player;
        EntityBattleRam ram = getRam(
                player,
                request.dimensionId,
                request.entityId
        );

        if (request.action == ENTER_TARGET_MODE_REMOTE) {
            if (!canControlRemotely(player, ram)) {
                if (player != null) {
                    sendMessage(
                            player,
                            "That Battle Ram is no longer available."
                    );
                }
                return;
            }
            sendTargetMode(player, true, ram);
            sendMessage(
                    player,
                    "Ram target queue editor opened."
            );
            return;
        }

        if (!canControlNearby(player, ram)) {
            if (player != null) {
                sendMessage(player, "That Battle Ram is no longer available.");
            }
            return;
        }

        if (request.action == DISBAND && ram.disband(player)) {
            sendTargetMode(player, false, ram);
            sendMessage(player, "Battle Ram disbanded.");
        } else if (request.action == ENTER_TARGET_MODE) {
            sendTargetMode(player, true, ram);
            sendMessage(
                    player,
                    "Click highlighted Siege Gates to edit the target queue."
            );
        } else if (request.action == OPEN_CONTROL) {
            open(player, ram);
        } else if (request.action == TOGGLE_PAUSE) {
            if (ram.getRamState() == BattleRamState.PAUSED) {
                ram.resumeRam();
                sendMessage(player, "Battle Ram resumed.");
            } else {
                ram.pauseRam();
                sendMessage(player, "Battle Ram paused.");
            }
        }
    }

    private static void processTargetSelection(
            PendingTargetSelection request
    ) {
        EntityPlayerMP player = request.player;
        EntityBattleRam ram = getRam(
                player,
                request.dimensionId,
                request.ramEntityId
        );

        if (!canControlRemotely(player, ram)) {
            if (player != null) {
                sendTargetMode(
                        player,
                        false,
                        request.ramEntityId
                );
                sendMessage(player, "Battle Ram target mode ended.");
            }
            return;
        }

        if (!player.worldObj.blockExists(
                request.controllerX,
                request.controllerY,
                request.controllerZ
        )) {
            return;
        }

        TileEntity tileEntity = player.worldObj.getTileEntity(
                request.controllerX,
                request.controllerY,
                request.controllerZ
        );
        if (!(tileEntity instanceof TileEntitySiegeGate)) {
            return;
        }

        TileEntitySiegeGate gate = (TileEntitySiegeGate)tileEntity;

        if (ram.isQueuedTarget(gate)) {
            if (ram.removeGateTargetFromQueue(gate)) {
                player.worldObj.playSoundEffect(
                        ram.posX,
                        ram.posY + ram.height * 0.5D,
                        ram.posZ,
                        "lotrmoremobs:siege.ram_target_set",
                        0.7F,
                        0.82F
                );
                sendTargetMode(player, true, ram);
            }
            return;
        }

        String invalidReason = getInvalidTargetReason(player, ram, gate);
        if (invalidReason != null) {
            sendMessage(player, invalidReason);
            sendTargetMode(player, true, ram);
            return;
        }

        if (ram.getTargetQueueSize() >= EntityBattleRam.MAX_TARGET_QUEUE_SIZE) {
            sendMessage(player, "That Battle Ram's target queue is full.");
            return;
        }

        if (!ram.queueGateTarget(gate)) {
            sendMessage(player, "That gate could not be reserved.");
            sendTargetMode(player, true, ram);
            return;
        }

        player.worldObj.playSoundEffect(
                ram.posX,
                ram.posY + ram.height * 0.5D,
                ram.posZ,
                "lotrmoremobs:siege.ram_target_set",
                0.9F,
                1.0F
        );

        sendTargetMode(player, true, ram);
    }

    public static String getInvalidTargetReason(
            EntityPlayerMP player,
            EntityBattleRam ram,
            TileEntitySiegeGate gate
    ) {
        if (gate == null
                || !gate.isFinalized()
                || gate.getGateState() == GateState.BREACHED) {
            return "That Siege Gate is not a valid target.";
        }
        if (!isWithinTargetCommandRange(ram, gate)) {
            return "That Siege Gate is more than "
                    + (int)TARGET_COMMAND_RANGE
                    + " blocks from the Battle Ram.";
        }
        LOTRFaction ramFaction =
                ram.getRamFaction();

        LOTRFaction gateFaction =
                gate.getGateFaction();

        /*
         * Same-faction refusal is an invariant of the ram unit itself, not an
         * ordinary permission check. Creative/admin command authority does not
         * make a faction ram attack its own gate.
         */
        if (ramFaction != null
                && gateFaction != null
                && ramFaction == gateFaction) {

            return "Your units refuse to attack their own faction.";
        }

        UUID reservation = gate.getReservedRamUuid();
        if (reservation != null
                && !reservation.equals(ram.getUniqueID())) {
            return "That Siege Gate is already reserved by another ram.";
        }

        if (!GateAccess.isAdministrativePlayer(player)
                && ramFaction != null
                && gateFaction != null
                && ramFaction.isAlly(gateFaction)) {

            return "Friendly or allied Siege Gates cannot be targeted.";
        }
        return null;
    }

    private static boolean isWithinTargetCommandRange(
            EntityBattleRam ram,
            TileEntitySiegeGate gate
    ) {
        if (ram == null
                || gate == null
                || gate.getWorldObj() != ram.worldObj) {
            return false;
        }

        for (GatePartData part : gate.getGateParts()) {
            if (part == null) {
                continue;
            }
            double gateX = gate.xCoord + part.getRelativeX() + 0.5D;
            double gateY = gate.yCoord + part.getRelativeY() + 0.5D;
            double gateZ = gate.zCoord + part.getRelativeZ() + 0.5D;
            if (ram.getDistanceSq(gateX, gateY, gateZ)
                    <= TARGET_COMMAND_RANGE_SQ) {
                return true;
            }
        }

        /*
         * A finalized gate should always have part data, but keep the
         * controller itself as a conservative fallback for legacy/corrupt
         * snapshots instead of making those gates impossible to command.
         */
        return ram.getDistanceSq(
                gate.xCoord + 0.5D,
                gate.yCoord + 0.5D,
                gate.zCoord + 0.5D
        ) <= TARGET_COMMAND_RANGE_SQ;
    }

    private static EntityBattleRam getRam(
            EntityPlayerMP player,
            int dimensionId,
            int entityId
    ) {
        if (player == null
                || player.isDead
                || player.worldObj == null
                || player.worldObj.isRemote
                || player.dimension != dimensionId) {
            return null;
        }
        Entity entity = player.worldObj.getEntityByID(entityId);
        return entity instanceof EntityBattleRam
                ? (EntityBattleRam)entity
                : null;
    }

    private static boolean canControlNearby(
            EntityPlayerMP player,
            EntityBattleRam ram
    ) {
        return player != null
                && ram != null
                && !ram.isDead
                && player.worldObj == ram.worldObj
                && ram.isCommanderOrAdministrator(player)
                && player.getDistanceSqToEntity(ram)
                <= MAX_CONTROL_DISTANCE_SQ;
    }

    private static boolean canControlRemotely(
            EntityPlayerMP player,
            EntityBattleRam ram
    ) {
        return player != null
                && ram != null
                && !ram.isDead
                && player.worldObj == ram.worldObj
                && ram.isCommanderOrAdministrator(player);
    }

    private static void sendTargetMode(
            EntityPlayerMP player,
            boolean active,
            EntityBattleRam ram
    ) {
        if (player == null) {
            return;
        }
        if (ram == null) {
            sendTargetMode(player, false, 0);
            return;
        }
        Main.network.sendTo(
                new RamTargetModePacket(
                        player.dimension,
                        ram,
                        active
                ),
                player
        );
    }

    private static void sendTargetMode(
            EntityPlayerMP player,
            boolean active,
            int ramEntityId
    ) {
        Main.network.sendTo(
                new RamTargetModePacket(
                        player.dimension,
                        ramEntityId,
                        active
                ),
                player
        );
    }

    private static void sendMessage(EntityPlayerMP player, String message) {
        player.addChatMessage(new ChatComponentText(message));
    }

    private static boolean hasEquivalentActionLocked(
            PendingAction candidate
    ) {
        for (PendingAction request : PENDING_ACTIONS) {
            if (request.playerUuid.equals(candidate.playerUuid)
                    && request.action == candidate.action
                    && request.dimensionId == candidate.dimensionId
                    && request.entityId == candidate.entityId) {
                return true;
            }
        }
        return false;
    }

    private static boolean offerActionLocked(PendingAction request) {
        if (PENDING_ACTIONS.size() >= MAX_PENDING_ACTIONS) {
            return false;
        }
        int count = getPendingCount(
                PENDING_ACTION_COUNTS,
                request.playerUuid
        );
        if (count >= MAX_PENDING_PER_PLAYER) {
            return false;
        }
        PENDING_ACTIONS.addLast(request);
        PENDING_ACTION_COUNTS.put(
                request.playerUuid,
                Integer.valueOf(count + 1)
        );
        return true;
    }

    private static boolean offerOrReplaceTargetLocked(
            PendingTargetSelection request
    ) {
        TargetKey key = request.getKey();
        if (PENDING_TARGETS.containsKey(key)) {
            PENDING_TARGETS.put(key, request);
            return true;
        }
        if (PENDING_TARGETS.size() >= MAX_PENDING_TARGETS) {
            return false;
        }
        int count = getPendingCount(
                PENDING_TARGET_COUNTS,
                request.playerUuid
        );
        if (count >= MAX_PENDING_PER_PLAYER) {
            return false;
        }
        PENDING_TARGETS.put(key, request);
        PENDING_TARGET_COUNTS.put(
                request.playerUuid,
                Integer.valueOf(count + 1)
        );
        return true;
    }

    private static PendingAction pollAction() {
        synchronized (PENDING_LOCK) {
            PendingAction request = PENDING_ACTIONS.pollFirst();
            if (request != null) {
                decrementPendingCount(
                        PENDING_ACTION_COUNTS,
                        request.playerUuid
                );
            }
            return request;
        }
    }

    private static PendingTargetSelection pollTarget() {
        synchronized (PENDING_LOCK) {
            Iterator<Map.Entry<TargetKey, PendingTargetSelection>> iterator =
                    PENDING_TARGETS.entrySet().iterator();
            if (!iterator.hasNext()) {
                return null;
            }
            PendingTargetSelection request = iterator.next().getValue();
            iterator.remove();
            decrementPendingCount(
                    PENDING_TARGET_COUNTS,
                    request.playerUuid
            );
            return request;
        }
    }

    private static int getPendingCount(
            Map<UUID, Integer> counts,
            UUID playerUuid
    ) {
        Integer count = counts.get(playerUuid);
        return count == null ? 0 : count.intValue();
    }

    private static void decrementPendingCount(
            Map<UUID, Integer> counts,
            UUID playerUuid
    ) {
        int remaining = getPendingCount(counts, playerUuid) - 1;
        if (remaining <= 0) {
            counts.remove(playerUuid);
        } else {
            counts.put(playerUuid, Integer.valueOf(remaining));
        }
    }

    private static final class PendingAction {
        private final EntityPlayerMP player;
        private final UUID playerUuid;
        private final int action;
        private final int dimensionId;
        private final int entityId;

        private PendingAction(
                EntityPlayerMP player,
                int action,
                int dimensionId,
                int entityId
        ) {
            this.player = player;
            this.playerUuid = player.getUniqueID();
            this.action = action;
            this.dimensionId = dimensionId;
            this.entityId = entityId;
        }
    }

    private static final class PendingTargetSelection {
        private final EntityPlayerMP player;
        private final UUID playerUuid;
        private final int dimensionId;
        private final int ramEntityId;
        private final int controllerX;
        private final int controllerY;
        private final int controllerZ;

        private PendingTargetSelection(
                EntityPlayerMP player,
                int dimensionId,
                int ramEntityId,
                int controllerX,
                int controllerY,
                int controllerZ
        ) {
            this.player = player;
            this.playerUuid = player.getUniqueID();
            this.dimensionId = dimensionId;
            this.ramEntityId = ramEntityId;
            this.controllerX = controllerX;
            this.controllerY = controllerY;
            this.controllerZ = controllerZ;
        }

        private TargetKey getKey() {
            return new TargetKey(
                    playerUuid,
                    dimensionId,
                    ramEntityId,
                    controllerX,
                    controllerY,
                    controllerZ
            );
        }
    }

    private static final class TargetKey {
        private final UUID playerUuid;
        private final int dimensionId;
        private final int ramEntityId;
        private final int controllerX;
        private final int controllerY;
        private final int controllerZ;

        private TargetKey(
                UUID playerUuid,
                int dimensionId,
                int ramEntityId,
                int controllerX,
                int controllerY,
                int controllerZ
        ) {
            this.playerUuid = playerUuid;
            this.dimensionId = dimensionId;
            this.ramEntityId = ramEntityId;
            this.controllerX = controllerX;
            this.controllerY = controllerY;
            this.controllerZ = controllerZ;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof TargetKey)) {
                return false;
            }
            TargetKey key = (TargetKey)other;
            return dimensionId == key.dimensionId
                    && ramEntityId == key.ramEntityId
                    && controllerX == key.controllerX
                    && controllerY == key.controllerY
                    && controllerZ == key.controllerZ
                    && playerUuid.equals(key.playerUuid);
        }

        @Override
        public int hashCode() {
            int result = playerUuid.hashCode();
            result = 31 * result + dimensionId;
            result = 31 * result + ramEntityId;
            result = 31 * result + controllerX;
            result = 31 * result + controllerY;
            result = 31 * result + controllerZ;
            return result;
        }
    }

}
