package com.enovak.lotrmoremobs.siege.repair;

import com.enovak.lotrmoremobs.Main;
import com.enovak.lotrmoremobs.siege.access.GateAccess;
import com.enovak.lotrmoremobs.siege.network.GateManagementActionPacket;
import com.enovak.lotrmoremobs.siege.network.GateManagementOpenPacket;
import com.enovak.lotrmoremobs.siege.network.SiegeNetwork;
import com.enovak.lotrmoremobs.siege.network.SiegeRequestLimiter;
import com.enovak.lotrmoremobs.siege.management.GateInspectionSessionManager;
import com.enovak.lotrmoremobs.siege.management.KOMEGateManagementSnapshot;
import com.enovak.lotrmoremobs.siege.gate.GateControlMode;
import com.enovak.lotrmoremobs.siege.edit.GateEditSession;
import com.enovak.lotrmoremobs.siege.edit.GateEditSessionManager;
import com.enovak.lotrmoremobs.siege.network.GateEditDraftSnapshotPacket;
import com.enovak.lotrmoremobs.siege.network.GateEditPreflightSnapshotPacket;
import com.enovak.lotrmoremobs.siege.tile.TileEntitySiegeGate;
import com.mojang.authlib.GameProfile;
import java.util.ArrayDeque;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import kome.common.data.KOMEBuildService;
import kome.common.config.KOMEConfigRegistry;
import kome.common.data.KOMEDefensiveGateHealthCalculator;
import kome.common.data.KOMEDefensiveGateLinkService;
import kome.common.data.KOMEDefensiveGateRecord;
import kome.common.data.KOMEGateSizeCalculator;
import kome.common.data.KOMEPhysicalGateInspection;
import kome.common.data.KOMEPlayerBuild;
import kome.common.data.KOMEWorldData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
        boolean canAdminister = GateAccess.isAdministrativePlayer(player);
        KOMEWorldData komeData = KOMEWorldData.get(player.worldObj);
        KOMEPhysicalGateInspection.Result inspection =
                KOMEPhysicalGateInspection.inspect(controller);
        if (canAdminister) {
            refreshSamePhysicalGateIfNeeded(komeData, inspection, player);
        }
        KOMEGateManagementSnapshot komeSnapshot = KOMEGateManagementSnapshot.create(
                komeData, inspection, TileEntitySiegeGate.getConfiguredDefaultMaxHealth(),
                canAdminister ? findBrokenRecords(komeData) : Collections
                    .<KOMEGateManagementSnapshot.BrokenRecord>emptyList(),
                canAdminister);
        Main.network.sendTo(
                new GateManagementOpenPacket(
                        player.dimension,
                        controller.xCoord,
                        controller.yCoord,
                        controller.zCoord,
                        controller.canManage(player),
                        controller.canManagePlayerAccess(player),
                        canAdminister,
                        komeSnapshot
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

        if (isKomeAction(request.action)) {
            processKomeAction(request, player, gate);
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
                || action == GateManagementActionPacket.CLAIM_OWNERLESS
                || isKomeAction(action);
    }

    private static boolean isKomeAction(int action) {
        return action == GateManagementActionPacket.KOME_LINK
            || action == GateManagementActionPacket.KOME_UNLINK
            || action == GateManagementActionPacket.KOME_REFRESH
            || action == GateManagementActionPacket.KOME_RELINK
            || action == GateManagementActionPacket.KOME_CONFIRM_DIMENSIONS;
    }

    private static void processKomeAction(PendingAction request, EntityPlayerMP player,
            TileEntitySiegeGate gate) {
        if (!GateAccess.isAdministrativePlayer(player)) {
            sendMessage(player, "Only a Creative player or server operator may change KOME gate links.");
            return;
        }
        KOMEWorldData data = KOMEWorldData.get(player.worldObj);
        String[] fields = request.text.split("\\|", -1);
        KOMEPlayerBuild build = fields.length == 0 ? null : data.getBuild(fields[0]);
        String recordId = fields.length > 1 ? fields[1] : "";
        long now = System.currentTimeMillis();
        KOMEDefensiveGateLinkService.OperationResult result;
        // Unlink is a purely logical administrative recovery operation. It must remain
        // available when the old controller is destroyed, unloaded, quarantined, or invalid.
        if (request.action == GateManagementActionPacket.KOME_UNLINK) {
            result = KOMEDefensiveGateLinkService.unlink(data, build, recordId,
                player.getUniqueID(), player.getCommandSenderName(), true, now);
            if (!result.isSuccessful()) {
                sendMessage(player, "KOME gate update failed: " + result.getMessage());
                return;
            }
            sendMessage(player, "KOME defensive gate association updated.");
            open(player, gate);
            return;
        }
        KOMEPhysicalGateInspection.Result inspection = KOMEPhysicalGateInspection.inspect(gate);
        if (!inspection.isLinkable()) {
            sendMessage(player, "KOME link failed: " + inspection.getDiagnostic());
            return;
        }
        if (request.action == GateManagementActionPacket.KOME_LINK) {
            KOMEDefensiveGateLinkService.OperationResult validation =
                KOMEDefensiveGateLinkService.validateNewLink(data, build, inspection, true);
            if (!validation.isSuccessful()) {
                sendMessage(player, "KOME gate update failed: " + validation.getMessage());
                return;
            }
            KOMEConfigRegistry.SiegeSettings siege =
                KOMEConfigRegistry.requireReadySnapshot().getSiege();
            InitialLinkHealth initialHealth = calculateInitialLinkHealth(build, inspection,
                siege.getGateHpPerApprovedHour(), siege.getGateSizeParameters());
            if (!initialHealth.isAvailable()) {
                sendMessage(player, "KOME link failed: " + initialHealth.getMessage());
                return;
            }
            final int initialMaxHp = initialHealth.getMaxHp();
            result = KOMEDefensiveGateLinkService.linkAndInitializePhysicalHealth(data,
                build, inspection, player.getUniqueID(), player.getCommandSenderName(), true,
                now, new KOMEDefensiveGateLinkService.PhysicalHealthApplication() {
                    public boolean canApply() {
                        return gate.canInitializeKomeLinkedHealth(initialMaxHp);
                    }

                    public boolean apply() {
                        return gate.initializeKomeLinkedHealth(initialMaxHp);
                    }
                });
        } else {
            KOMEDefensiveGateRecord record = build == null ? null
                : build.getDefensiveGateRecord(recordId);
            if (request.action != GateManagementActionPacket.KOME_RELINK
                    && (record == null || !inspection.getGateUuid().equals(record.getGateUuid()))) {
                sendMessage(player, "That KOME gate record is not linked to this physical gate.");
                return;
            }
            if (request.action == GateManagementActionPacket.KOME_REFRESH) {
                result = KOMEDefensiveGateLinkService.refresh(data, build, recordId, inspection,
                    player.getUniqueID(), player.getCommandSenderName(), true, now);
            } else if (request.action == GateManagementActionPacket.KOME_RELINK) {
                KOMEConfigRegistry.SiegeSettings siege =
                    KOMEConfigRegistry.requireReadySnapshot().getSiege();
                InitialLinkHealth relinkHealth = calculateInitialLinkHealth(build, inspection,
                    siege.getGateHpPerApprovedHour(), siege.getGateSizeParameters());
                if (!relinkHealth.isAvailable()) {
                    sendMessage(player, "KOME relink failed: " + relinkHealth.getMessage());
                    return;
                }
                final int relinkMaxHp = relinkHealth.getMaxHp();
                result = KOMEDefensiveGateLinkService.relinkAndApplyPhysicalHealth(data, build,
                    recordId, inspection, isBrokenBinding(record), player.getUniqueID(),
                    player.getCommandSenderName(), true, now,
                    new KOMEDefensiveGateLinkService.PhysicalHealthApplication() {
                        public boolean canApply() {
                            return gate.canInitializeKomeLinkedHealth(relinkMaxHp);
                        }

                        public boolean apply() {
                            return gate.initializeKomeLinkedHealth(relinkMaxHp);
                        }
                    });
            } else {
                int width = fields.length > 2 ? parsePositiveInt(fields[2]) : 0;
                int height = fields.length > 3 ? parsePositiveInt(fields[3]) : 0;
                result = KOMEDefensiveGateLinkService.confirmDimensions(data, build, recordId,
                    inspection, width, height, player.getUniqueID(),
                    player.getCommandSenderName(), true, now);
            }
        }
        if (!result.isSuccessful()) {
            sendMessage(player, "KOME gate update failed: " + result.getMessage());
            return;
        }
        sendMessage(player, "KOME defensive gate association updated.");
        open(player, gate);
    }

    static InitialLinkHealth calculateInitialLinkHealth(KOMEPlayerBuild build,
            KOMEPhysicalGateInspection.Result inspection, OptionalDouble hpPerApprovedHour,
            KOMEGateSizeCalculator.Parameters sizeParameters) {
        if (hpPerApprovedHour == null || !hpPerApprovedHour.isPresent()) {
            return InitialLinkHealth.unavailable(
                "siege.gateHpPerApprovedHour is unavailable/TBD; KOME physical health cannot be applied.");
        }
        if (inspection == null || !inspection.isLinkable()
                || inspection.getStatus()
                    != KOMEDefensiveGateRecord.DimensionDetectionStatus.RELIABLE) {
            return InitialLinkHealth.unavailable(
                "reliable physical gate dimensions are required before KOME can apply health.");
        }
        if (build == null || !build.active || !build.isDefensive()) {
            return InitialLinkHealth.unavailable(
                "an active DEFENSIVE Build is required to calculate KOME gate health.");
        }
        try {
            BigDecimal calculated = KOMEDefensiveGateHealthCalculator.calculateAutomaticMaxHp(
                build.approvedDefensiveCentiHours(), hpPerApprovedHour.getAsDouble(),
                inspection.getDetectedWidth(), inspection.getDetectedHeight(), sizeParameters);
            int physicalMaxHp = calculated.setScale(0, RoundingMode.HALF_UP).intValueExact();
            if (physicalMaxHp < 1 || physicalMaxHp > TileEntitySiegeGate.MAX_HEALTH_OVERRIDE) {
                return InitialLinkHealth.unavailable(
                    "calculated KOME gate health is outside the physical gate range 1-"
                        + TileEntitySiegeGate.MAX_HEALTH_OVERRIDE + ".");
            }
            return InitialLinkHealth.available(physicalMaxHp);
        } catch (RuntimeException invalid) {
            return InitialLinkHealth.unavailable(
                "KOME gate health could not be calculated from the approved hours and gate dimensions.");
        }
    }

    static final class InitialLinkHealth {
        private final int maxHp;
        private final String message;

        private InitialLinkHealth(int maxHp, String message) {
            this.maxHp = maxHp;
            this.message = message == null ? "" : message;
        }

        static InitialLinkHealth available(int maxHp) {
            return new InitialLinkHealth(maxHp, "");
        }

        static InitialLinkHealth unavailable(String message) {
            return new InitialLinkHealth(0, message);
        }

        boolean isAvailable() { return maxHp > 0; }
        int getMaxHp() { return maxHp; }
        String getMessage() { return message; }
    }

    private static int parsePositiveInt(String value) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : 0;
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static void refreshSamePhysicalGateIfNeeded(KOMEWorldData data,
            KOMEPhysicalGateInspection.Result inspection, EntityPlayerMP player) {
        if (data == null || inspection == null || !inspection.isLinkable()) return;
        KOMEDefensiveGateLinkService.ActiveLink link =
            KOMEDefensiveGateLinkService.findActiveLinkByPhysicalGateUuid(data,
                inspection.getGateUuid());
        if (link == null || !KOMEDefensiveGateLinkService.isSamePhysicalController(
                link.getRecord(), inspection) || metadataMatches(link.getRecord(), inspection)) return;
        KOMEDefensiveGateLinkService.refresh(data, link.getParent(), link.getRecord().getId(),
            inspection, player.getUniqueID(), player.getCommandSenderName(), true,
            System.currentTimeMillis());
    }

    private static boolean metadataMatches(KOMEDefensiveGateRecord record,
            KOMEPhysicalGateInspection.Result inspection) {
        return record.getCapturedStructureRevision() == inspection.getStructureRevision()
            && record.getDetectedOrientation().equals(inspection.getOrientation())
            && record.getDetectedWidth() == inspection.getDetectedWidth()
            && record.getDetectedHeight() == inspection.getDetectedHeight()
            && record.getDetectedProjectedArea() == inspection.getProjectedArea()
            && record.getDimensionDetectionStatus() == inspection.getStatus();
    }

    private static List<KOMEGateManagementSnapshot.BrokenRecord> findBrokenRecords(
            KOMEWorldData data) {
        List<KOMEGateManagementSnapshot.BrokenRecord> result =
            new ArrayList<KOMEGateManagementSnapshot.BrokenRecord>();
        for (KOMEPlayerBuild build : KOMEBuildService.activeDefensiveBuilds(data)) {
            for (KOMEDefensiveGateRecord record : build.getDefensiveGateRecords()) {
                if (record != null && isBrokenBinding(record)) {
                    result.add(new KOMEGateManagementSnapshot.BrokenRecord(build, record));
                }
            }
        }
        return result;
    }

    /** A loaded missing/replaced/broken controller proves Relink eligibility; unloaded chunks do not. */
    private static boolean isBrokenBinding(KOMEDefensiveGateRecord record) {
        if (record == null || !record.hasPhysicalBinding()) return true;
        WorldServer world = DimensionManager.getWorld(record.getGateDimension().intValue());
        if (world == null || !world.blockExists(record.getControllerX().intValue(),
                record.getControllerY().intValue(), record.getControllerZ().intValue())) return false;
        TileEntity tile = world.getTileEntity(record.getControllerX().intValue(),
            record.getControllerY().intValue(), record.getControllerZ().intValue());
        if (!(tile instanceof TileEntitySiegeGate)) return true;
        TileEntitySiegeGate gate = (TileEntitySiegeGate) tile;
        if (gate.isInvalid() || !gate.isFinalized() || gate.isGateStructureQuarantined()
                || gate.getExistingGateUuid() == null
                || !gate.getExistingGateUuid().equals(record.getGateUuid())) return true;
        if (gate.isPersistentGateMutationLocked()) return false;
        return !KOMEPhysicalGateInspection.inspect(gate).isLinkable();
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
