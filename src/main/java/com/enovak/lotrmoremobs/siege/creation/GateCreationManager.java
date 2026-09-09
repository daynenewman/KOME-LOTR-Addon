package com.enovak.lotrmoremobs.siege.creation;

import com.enovak.lotrmoremobs.Main;
import com.enovak.lotrmoremobs.siege.SiegeRegistry;
import com.enovak.lotrmoremobs.siege.gate.GateEnclosedAreaFill;
import com.enovak.lotrmoremobs.siege.gate.GateLeaf;
import com.enovak.lotrmoremobs.siege.gate.GateSourceTileEntitySnapshot;
import com.enovak.lotrmoremobs.siege.network.GateCreationActionPacket;
import com.enovak.lotrmoremobs.siege.network.GateCreationSyncPacket;
import com.enovak.lotrmoremobs.siege.network.SiegeRequestLimiter;
import com.enovak.lotrmoremobs.siege.tile.TileEntitySiegeGate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;
import net.minecraft.nbt.NBTTagCompound;

public final class GateCreationManager {

    public static final double MAX_CREATION_DISTANCE = 64.0D;

    private static final double MAX_CREATION_DISTANCE_SQ =
            MAX_CREATION_DISTANCE * MAX_CREATION_DISTANCE;
    private static final int MAX_PENDING_REQUESTS = 256;
    private static final int MAX_PENDING_REQUESTS_PER_PLAYER = 20;
    private static final int REQUEST_PROCESSING_BUDGET_PER_TICK = 64;
    private static final int SELECTION_VALIDATION_BUDGET_PER_TICK = 64;
    private static final int EVENT_SELECTION_VALIDATION_BUDGET_PER_TICK = 32;
    private static final Map<UUID, GateCreationSession> SESSIONS_BY_PLAYER =
            new HashMap<UUID, GateCreationSession>();
    private static final Map<ControllerKey, UUID> CREATORS_BY_CONTROLLER =
            new HashMap<ControllerKey, UUID>();
    private static final Map<SelectionKey, Set<UUID>> CREATORS_BY_SELECTION =
            new HashMap<SelectionKey, Set<UUID>>();
    private static final ArrayDeque<SelectionKey> SELECTION_VALIDATION_QUEUE =
            new ArrayDeque<SelectionKey>();
    private static final Set<SelectionKey> QUEUED_SELECTION_KEYS =
            new HashSet<SelectionKey>();
    private static final Set<SelectionKey> PENDING_SELECTION_INVALIDATIONS =
            new LinkedHashSet<SelectionKey>();
    private static final Object PENDING_REQUEST_LOCK = new Object();
    private static final ArrayDeque<PendingRequest> PENDING_REQUESTS =
            new ArrayDeque<PendingRequest>();
    private static final Map<UUID, Integer> PENDING_COUNTS_BY_PLAYER =
            new HashMap<UUID, Integer>();

    private GateCreationManager() {
    }

    public static void openControls(
            EntityPlayerMP player,
            TileEntitySiegeGate controller
    ) {
        if (player == null || controller == null
                || !isControllerUsableBy(player, controller)) {
            return;
        }
        if (controller.isFinalized()) {
            sendMessage(player, "This Siege Gate is already finalized.");
            return;
        }

        GateBlockPosition controllerPosition = new GateBlockPosition(
                controller.xCoord,
                controller.yCoord,
                controller.zCoord
        );
        ControllerKey controllerKey = new ControllerKey(
                player.worldObj,
                player.dimension,
                controllerPosition
        );
        UUID playerUuid = player.getUniqueID();
        UUID existingCreator =
                CREATORS_BY_CONTROLLER.get(controllerKey);
        if (existingCreator != null
                && !existingCreator.equals(playerUuid)) {
            GateCreationSession existingSession =
                    SESSIONS_BY_PLAYER.get(existingCreator);
            EntityPlayerMP existingPlayer =
                    findPlayer(player.worldObj, existingCreator);
            if (existingSession == null
                    || getValidController(
                            existingPlayer,
                            existingSession
                    ) == null) {
                if (existingSession != null) {
                    finishSession(
                            existingPlayer,
                            existingSession,
                            existingPlayer != null
                    );
                } else {
                    CREATORS_BY_CONTROLLER.remove(controllerKey);
                }
            } else {
                sendMessage(
                        player,
                        "Another player is creating this gate."
                );
                return;
            }
        }

        GateCreationSession session =
                SESSIONS_BY_PLAYER.get(playerUuid);
        if (session == null
                || !controllerKey.equals(new ControllerKey(
                        session.getWorld(),
                        session.getDimensionId(),
                        session.getControllerPosition()
                ))) {
            if (session != null) {
                cancelSession(player, session, false, true);
            }
            session = new GateCreationSession(
                    playerUuid,
                    controller.getGateUuid(),
                    player.worldObj,
                    player.dimension,
                    controllerPosition
            );
            SESSIONS_BY_PLAYER.put(playerUuid, session);
            CREATORS_BY_CONTROLLER.put(controllerKey, playerUuid);
        }
        sendFullState(player, session, true);
    }

    public static void queueAction(
            EntityPlayerMP player,
            int action,
            int dimensionId,
            int controllerX,
            int controllerY,
            int controllerZ
    ) {
        if (player == null
                || !GateCreationActionPacket.isKnownAction(action)
                || !SiegeRequestLimiter.isSaneBlockPosition(
                        controllerX,
                        controllerY,
                        controllerZ
                )
                || !SiegeRequestLimiter.tryAcquire(
                        player.getUniqueID(),
                        SiegeRequestLimiter.RateClass.CREATION_ACTION
                )) {
            return;
        }
        PendingRequest request = PendingRequest.action(
                player,
                action,
                dimensionId,
                controllerX,
                controllerY,
                controllerZ
        );
        synchronized (PENDING_REQUEST_LOCK) {
            if (isSingleTransitionAction(action)
                    && hasEquivalentActionLocked(request)) {
                return;
            }
            offerLocked(request);
        }
    }

    public static void queueSelection(
            EntityPlayerMP player,
            int x,
            int y,
            int z,
            int dimensionId,
            int controllerX,
            int controllerY,
            int controllerZ,
            boolean fillEnclosed
    ) {
        if (player == null
                || !SiegeRequestLimiter.isSaneBlockPosition(x, y, z)
                || !SiegeRequestLimiter.isSaneBlockPosition(
                        controllerX,
                        controllerY,
                        controllerZ
                )
                || !SiegeRequestLimiter.tryAcquire(
                        player.getUniqueID(),
                        SiegeRequestLimiter.RateClass.CREATION_SELECTION
                )) {
            return;
        }
        PendingRequest request = PendingRequest.selection(
                player,
                x,
                y,
                z,
                dimensionId,
                controllerX,
                controllerY,
                controllerZ,
                fillEnclosed
        );
        synchronized (PENDING_REQUEST_LOCK) {
            offerLocked(request);
        }
    }

    public static void processQueuedRequests() {
        for (int processed = 0;
                processed < REQUEST_PROCESSING_BUDGET_PER_TICK;
                ++processed) {
            PendingRequest request = pollRequest();
            if (request == null) {
                break;
            }
            EntityPlayerMP player = request.player;
            if (player == null
                    || player.isDead
                    || player.worldObj == null
                    || player.worldObj.isRemote) {
                continue;
            }
            GateCreationSession session =
                    SESSIONS_BY_PLAYER.get(player.getUniqueID());
            if (!request.matches(session)) {
                continue;
            }
            if (request.selection) {
                handleSelection(
                        player,
                        request.x,
                        request.y,
                        request.z,
                        request.fillEnclosed
                );
            } else {
                handleAction(player, request.action);
            }
        }
    }

    public static void validatePlayerSession(EntityPlayerMP player) {
        GateCreationSession session = player == null
                ? null
                : SESSIONS_BY_PLAYER.get(player.getUniqueID());
        if (session != null && getValidController(player, session) == null) {
            cancelSession(
                    player,
                    session,
                    true,
                    true
            );
        }
    }

    public static void queueSelectionRevalidation(
            World world,
            int x,
            int y,
            int z
    ) {
        if (world == null || world.isRemote) {
            return;
        }
        SelectionKey key = new SelectionKey(
                world.provider.dimensionId,
                new GateBlockPosition(x, y, z)
        );
        if (CREATORS_BY_SELECTION.containsKey(key)) {
            PENDING_SELECTION_INVALIDATIONS.add(key);
        }
    }

    public static void processSelectionInvalidations() {
        int processed = 0;
        Iterator<SelectionKey> pending =
                PENDING_SELECTION_INVALIDATIONS.iterator();
        while (pending.hasNext()
                && processed < EVENT_SELECTION_VALIDATION_BUDGET_PER_TICK) {
            SelectionKey key = pending.next();
            pending.remove();
            revalidateSelectionKey(key);
            ++processed;
        }

        while (processed < SELECTION_VALIDATION_BUDGET_PER_TICK) {
            SelectionKey key = SELECTION_VALIDATION_QUEUE.pollFirst();
            if (key == null) {
                break;
            }
            if (!CREATORS_BY_SELECTION.containsKey(key)) {
                QUEUED_SELECTION_KEYS.remove(key);
                continue;
            }
            revalidateSelectionKey(key);
            if (CREATORS_BY_SELECTION.containsKey(key)) {
                SELECTION_VALIDATION_QUEUE.addLast(key);
            } else {
                QUEUED_SELECTION_KEYS.remove(key);
            }
            ++processed;
        }
    }

    public static void cancelForPlayer(EntityPlayerMP player) {
        cancelForPlayer(player, true);
    }

    public static void cancelForPlayer(
            EntityPlayerMP player,
            boolean synchronize
    ) {
        if (player == null) {
            return;
        }
        clearPendingForPlayer(player.getUniqueID());
        GateCreationSession session =
                SESSIONS_BY_PLAYER.get(player.getUniqueID());
        if (session != null) {
            cancelSession(player, session, false, synchronize);
        }
    }

    public static void clearPendingForPlayer(UUID playerUuid) {
        if (playerUuid == null) {
            return;
        }
        synchronized (PENDING_REQUEST_LOCK) {
            Iterator<PendingRequest> iterator =
                    PENDING_REQUESTS.iterator();
            while (iterator.hasNext()) {
                PendingRequest request = iterator.next();
                if (playerUuid.equals(request.playerUuid)) {
                    iterator.remove();
                }
            }
            PENDING_COUNTS_BY_PLAYER.remove(playerUuid);
        }
    }

    public static void resetServerState() {
        synchronized (PENDING_REQUEST_LOCK) {
            PENDING_REQUESTS.clear();
            PENDING_COUNTS_BY_PLAYER.clear();
        }
        SESSIONS_BY_PLAYER.clear();
        CREATORS_BY_CONTROLLER.clear();
        CREATORS_BY_SELECTION.clear();
        SELECTION_VALIDATION_QUEUE.clear();
        QUEUED_SELECTION_KEYS.clear();
        PENDING_SELECTION_INVALIDATIONS.clear();
    }

    public static void cancelForController(
            World world,
            int x,
            int y,
            int z
    ) {
        if (world == null || world.isRemote) {
            return;
        }
        ControllerKey controllerKey = new ControllerKey(
                world,
                world.provider.dimensionId,
                new GateBlockPosition(x, y, z)
        );
        UUID creatorUuid = CREATORS_BY_CONTROLLER.get(controllerKey);
        if (creatorUuid == null) {
            return;
        }
        GateCreationSession session =
                SESSIONS_BY_PLAYER.get(creatorUuid);
        EntityPlayerMP player = findPlayer(world, creatorUuid);
        if (session != null) {
            cancelSession(player, session, false, player != null);
        }
    }

    private static void handleAction(
            EntityPlayerMP player,
            int action
    ) {
        GateCreationSession session =
                SESSIONS_BY_PLAYER.get(player.getUniqueID());
        TileEntitySiegeGate controller =
                getValidController(player, session);
        if (session == null || controller == null) {
            if (session != null) {
                cancelSession(player, session, true, true);
            }
            return;
        }

        if (action == GateCreationActionPacket.SELECT_LEFT
                || action == GateCreationActionPacket.SELECT_RIGHT
                || action == GateCreationActionPacket.SELECT_CENTER_SPLIT) {
            session.setActiveLeaf(
                    action == GateCreationActionPacket.SELECT_LEFT
                    ? GateLeaf.LEFT
                    : action == GateCreationActionPacket.SELECT_RIGHT
                    ? GateLeaf.RIGHT
                    : GateLeaf.SPLIT_CENTER
            );
            session.setSelectionMode(GateSelectionMode.BLOCKS);
            sendConfiguration(player, session);
        } else if (action == GateCreationActionPacket.SET_LEFT_HINGE
                || action == GateCreationActionPacket.SET_RIGHT_HINGE) {
            GateLeaf hingeLeaf =
                    action == GateCreationActionPacket.SET_LEFT_HINGE
                    ? GateLeaf.LEFT
                    : GateLeaf.RIGHT;
            session.setActiveLeaf(hingeLeaf);
            session.setSelectionMode(
                    hingeLeaf == GateLeaf.LEFT
                    ? GateSelectionMode.LEFT_HINGE
                    : GateSelectionMode.RIGHT_HINGE
            );
            sendConfiguration(player, session);
        } else if (action == GateCreationActionPacket.TOGGLE_DIRECTION) {
            session.toggleOpeningDirection();
            sendConfiguration(player, session);
        } else if (action == GateCreationActionPacket.TOGGLE_BORDER_TEXTURE) {
            session.toggleBorderTexture();
            sendConfiguration(player, session);
        } else if (action == GateCreationActionPacket.STOP_SELECTING) {
            session.setSelectionMode(GateSelectionMode.NONE);
            sendConfiguration(player, session);
        } else if (action == GateCreationActionPacket.FINALIZE) {
            String error = GateCreationFinalizer.finalizeGate(
                    session,
                    controller
            );
            if (error != null) {
                sendMessage(player, error);
                sendFullState(player, session, true);
                return;
            }
            sendMessage(
                    player,
                    "Siege Gate finalized with "
                            + session.getSelectionCount()
                            + " blocks."
            );
            finishSession(player, session, true);
        } else if (action == GateCreationActionPacket.CANCEL) {
            cancelSession(player, session, false, true);
        }
    }

    private static void handleSelection(
            EntityPlayerMP player,
            int x,
            int y,
            int z,
            boolean fillEnclosed
    ) {
        GateCreationSession session =
                SESSIONS_BY_PLAYER.get(player.getUniqueID());
        TileEntitySiegeGate controller =
                getValidController(player, session);
        if (session == null || controller == null) {
            if (session != null) {
                cancelSession(player, session, true, true);
            }
            return;
        }
        if (session.getSelectionMode() == GateSelectionMode.NONE) {
            return;
        }

        GateBlockPosition position = new GateBlockPosition(x, y, z);
        if (position.equals(session.getControllerPosition())) {
            return;
        }
        if (!isWithinCreationDistance(
                x + 0.5D,
                y + 0.5D,
                z + 0.5D,
                session.getControllerPosition()
        )) {
            sendMessage(
                    player,
                    "Selected blocks must be within 64 blocks of the controller."
            );
            return;
        }
        if (player.getDistanceSq(
                x + 0.5D,
                y + 0.5D,
                z + 0.5D
        ) > 36.0D) {
            return;
        }

        if (session.getSelectionMode() != GateSelectionMode.BLOCKS) {
            handleHingeSelection(player, session, position);
            return;
        }

        if (fillEnclosed) {
            handleEnclosedFill(
                    player,
                    session,
                    position
            );
            return;
        }

        GateSelectionData existing = session.getSelection(position);
        if (existing != null) {
            if (existing.getLeaf() == session.getActiveLeaf()) {
                session.removeSelection(position);
                unindexSelection(session, position);
                Main.network.sendTo(
                        GateCreationSyncPacket.partUpdate(position, null),
                        player
                );
            } else {
                GateSelectionData transferred =
                        existing.withLeaf(session.getActiveLeaf());
                session.putSelection(transferred);
                Main.network.sendTo(
                        GateCreationSyncPacket.partUpdate(
                                position,
                                transferred.getLeaf()
                        ),
                        player
                );
            }
            sendConfiguration(player, session);
            return;
        }

        if (session.getSelectionCount()
                >= GateCreationFinalizer.MAX_GATE_PARTS) {
            sendMessage(player, "A gate may contain at most "
                    + GateCreationFinalizer.MAX_GATE_PARTS + " blocks.");
            return;
        }

        World world = player.worldObj;
        if (!GateSourceBlockValidator.isValid(world, x, y, z)) {
            sendMessage(
                    player,
                    "That block cannot be used as a Siege Gate source."
            );
            return;
        }

        Block sourceBlock =
                world.getBlock(x, y, z);

        String sourceBlockName =
                GateSourceBlockValidator.getRegisteredName(sourceBlock);

        int sourceMetadata =
                world.getBlockMetadata(x, y, z);

        NBTTagCompound sourceTileEntityNbt = null;

        TileEntity sourceTileEntity =
                world.getTileEntity(x, y, z);

        if (sourceTileEntity != null) {
            try {
                sourceTileEntityNbt =
                        GateSourceTileEntitySnapshot
                                .captureForGateStorage(
                                        sourceTileEntity
                                );
            } catch (RuntimeException exception) {
                sendMessage(
                        player,
                        "That block's TileEntity state could not be captured safely."
                );
                return;
            }
        }

        GateSelectionData selection =
                new GateSelectionData(
                        position,
                        session.getActiveLeaf(),
                        sourceBlock,
                        sourceBlockName,
                        sourceMetadata,
                        sourceTileEntityNbt
                );

        session.putSelection(selection);
        indexSelection(session, position);
        Main.network.sendTo(
                GateCreationSyncPacket.partUpdate(
                        position,
                        selection.getLeaf()
                ),
                player
        );
        sendConfiguration(player, session);
    }

    private static void handleEnclosedFill(
            EntityPlayerMP player,
            GateCreationSession session,
            GateBlockPosition seed
    ) {
        GateLeaf leaf = session.getActiveLeaf();
        if (leaf == null) {
            return;
        }
        if (leaf == GateLeaf.SPLIT_CENTER) {
            sendMessage(
                    player,
                    "Shift-fill is available for LEFT and RIGHT leaves only."
            );
            return;
        }

        List<GateEnclosedAreaFill.Position> boundary =
                new ArrayList<GateEnclosedAreaFill.Position>();

        for (GateSelectionData selection : session.getSelections()) {
            if (selection != null && selection.getLeaf() == leaf) {
                GateBlockPosition position = selection.getPosition();
                boundary.add(
                        new GateEnclosedAreaFill.Position(
                                position.getX(),
                                position.getY(),
                                position.getZ()
                        )
                );
            }
        }

        GateEnclosedAreaFill.Result fill =
                GateEnclosedAreaFill.find(
                        boundary,
                        new GateEnclosedAreaFill.Position(
                                seed.getX(),
                                seed.getY(),
                                seed.getZ()
                        ),
                        null
                );

        if (!fill.isSuccess()) {
            sendMessage(
                    player,
                    fill.isAmbiguous()
                            ? "Shift-fill is ambiguous here. Click inside one flat enclosed leaf region."
                            : "Shift-fill needs a closed " + leaf.name()
                            + " outline around the clicked block."
            );
            return;
        }

        List<GateSelectionData> additions =
                new ArrayList<GateSelectionData>();
        List<GateSelectionData> transfers =
                new ArrayList<GateSelectionData>();

        for (GateEnclosedAreaFill.Position fillPosition
                : fill.getPositions()) {

            GateBlockPosition position =
                    new GateBlockPosition(
                            fillPosition.x,
                            fillPosition.y,
                            fillPosition.z
                    );

            if (position.equals(session.getControllerPosition())
                    || !isWithinCreationDistance(
                    position.getX() + 0.5D,
                    position.getY() + 0.5D,
                    position.getZ() + 0.5D,
                    session.getControllerPosition()
            )) {
                continue;
            }

            GateSelectionData existing =
                    session.getSelection(position);

            if (existing != null) {
                if (existing.getLeaf() != leaf) {
                    transfers.add(existing.withLeaf(leaf));
                }
                continue;
            }

            GateSelectionData captured =
                    captureSelection(
                            session.getWorld(),
                            position,
                            leaf
                    );

            if (captured != null) {
                additions.add(captured);
            }
        }

        if (session.getSelectionCount() + additions.size()
                > GateCreationFinalizer.MAX_GATE_PARTS) {
            sendMessage(
                    player,
                    "That fill would exceed the "
                            + GateCreationFinalizer.MAX_GATE_PARTS
                            + "-block gate limit."
            );
            return;
        }

        if (additions.isEmpty() && transfers.isEmpty()) {
            sendMessage(
                    player,
                    "No additional gate blocks were found inside that outline."
            );
            return;
        }

        for (GateSelectionData transfer : transfers) {
            session.putSelection(transfer);
        }

        for (GateSelectionData addition : additions) {
            session.putSelection(addition);
            indexSelection(session, addition.getPosition());
        }

        sendFullState(player, session, false);
        sendMessage(
                player,
                "Filled " + (additions.size() + transfers.size())
                        + " block"
                        + (additions.size() + transfers.size() == 1 ? "" : "s")
                        + " as " + leaf.name() + "."
        );
    }

    private static GateSelectionData captureSelection(
            World world,
            GateBlockPosition position,
            GateLeaf leaf
    ) {
        if (world == null
                || position == null
                || leaf == null
                || !world.blockExists(
                position.getX(),
                position.getY(),
                position.getZ()
        )
                || !GateSourceBlockValidator.isValid(
                world,
                position.getX(),
                position.getY(),
                position.getZ()
        )) {
            return null;
        }

        Block sourceBlock = world.getBlock(
                position.getX(),
                position.getY(),
                position.getZ()
        );
        String sourceBlockName =
                GateSourceBlockValidator.getRegisteredName(sourceBlock);
        int sourceMetadata = world.getBlockMetadata(
                position.getX(),
                position.getY(),
                position.getZ()
        );

        if (sourceBlockName == null) {
            return null;
        }

        NBTTagCompound sourceTileEntityNbt = null;
        TileEntity sourceTileEntity = world.getTileEntity(
                position.getX(),
                position.getY(),
                position.getZ()
        );

        if (sourceTileEntity != null) {
            try {
                sourceTileEntityNbt =
                        GateSourceTileEntitySnapshot
                                .captureForGateStorage(
                                        sourceTileEntity
                                );
            } catch (RuntimeException ignored) {
                return null;
            }
        }

        return new GateSelectionData(
                position,
                leaf,
                sourceBlock,
                sourceBlockName,
                sourceMetadata,
                sourceTileEntityNbt
        );
    }

    private static void handleHingeSelection(
            EntityPlayerMP player,
            GateCreationSession session,
            GateBlockPosition position
    ) {
        GateLeaf hingeLeaf = session.getSelectionMode()
                == GateSelectionMode.LEFT_HINGE
                ? GateLeaf.LEFT
                : GateLeaf.RIGHT;
        GateSelectionData selection = session.getSelection(position);
        if (selection == null || selection.getLeaf() != hingeLeaf) {
            sendMessage(
                    player,
                    "The " + hingeLeaf.name()
                            + " hinge must be a selected "
                            + hingeLeaf.name() + " leaf block."
            );
            return;
        }

        session.setHingePosition(hingeLeaf, position);
        session.setActiveLeaf(hingeLeaf);
        session.setSelectionMode(GateSelectionMode.BLOCKS);
        sendConfiguration(player, session);
    }

    private static TileEntitySiegeGate getValidController(
            EntityPlayerMP player,
            GateCreationSession session
    ) {
        if (player == null
                || session == null
                || player.isDead
                || !player.isEntityAlive()
                || player.worldObj != session.getWorld()
                || player.dimension != session.getDimensionId()
                || player.worldObj == null
                || player.worldObj.isRemote
                || !isWithinCreationDistance(
                        player.posX,
                        player.posY,
                        player.posZ,
                        session.getControllerPosition()
                )) {
            return null;
        }

        GateBlockPosition position = session.getControllerPosition();
        World world = player.worldObj;
        if (!world.blockExists(
                position.getX(),
                position.getY(),
                position.getZ()
        ) || world.getBlock(
                position.getX(),
                position.getY(),
                position.getZ()
        ) != SiegeRegistry.gateController) {
            return null;
        }

        TileEntity tileEntity = world.getTileEntity(
                position.getX(),
                position.getY(),
                position.getZ()
        );
        if (!(tileEntity instanceof TileEntitySiegeGate)) {
            return null;
        }
        TileEntitySiegeGate controller =
                (TileEntitySiegeGate)tileEntity;
        return !controller.isFinalized()
                && session.getGateUuid().equals(controller.getGateUuid())
                ? controller
                : null;
    }

    private static boolean isControllerUsableBy(
            EntityPlayerMP player,
            TileEntitySiegeGate controller
    ) {
        return player.worldObj == controller.getWorldObj()
                && isWithinCreationDistance(
                        player.posX,
                        player.posY,
                        player.posZ,
                        new GateBlockPosition(
                                controller.xCoord,
                                controller.yCoord,
                                controller.zCoord
                        )
                );
    }

    private static boolean isWithinCreationDistance(
            double x,
            double y,
            double z,
            GateBlockPosition controller
    ) {
        double deltaX = x - (controller.getX() + 0.5D);
        double deltaY = y - (controller.getY() + 0.5D);
        double deltaZ = z - (controller.getZ() + 0.5D);
        return deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ
                <= MAX_CREATION_DISTANCE_SQ;
    }

    private static void indexSelection(
            GateCreationSession session,
            GateBlockPosition position
    ) {
        SelectionKey key = new SelectionKey(
                session.getDimensionId(),
                position
        );
        Set<UUID> creators = CREATORS_BY_SELECTION.get(key);
        if (creators == null) {
            creators = new HashSet<UUID>();
            CREATORS_BY_SELECTION.put(key, creators);
            if (QUEUED_SELECTION_KEYS.add(key)) {
                SELECTION_VALIDATION_QUEUE.addLast(key);
            }
        }
        creators.add(session.getCreatorUuid());
    }

    private static void unindexSelection(
            GateCreationSession session,
            GateBlockPosition position
    ) {
        if (session == null || position == null) {
            return;
        }
        unindexSelection(
                new SelectionKey(session.getDimensionId(), position),
                session.getCreatorUuid()
        );
    }

    private static void unindexSelection(
            SelectionKey key,
            UUID creatorUuid
    ) {
        Set<UUID> creators = CREATORS_BY_SELECTION.get(key);
        if (creators == null) {
            return;
        }
        creators.remove(creatorUuid);
        if (!creators.isEmpty()) {
            return;
        }
        CREATORS_BY_SELECTION.remove(key);
        PENDING_SELECTION_INVALIDATIONS.remove(key);
        if (QUEUED_SELECTION_KEYS.remove(key)) {
            SELECTION_VALIDATION_QUEUE.remove(key);
        }
    }

    private static void unindexSessionSelections(
            GateCreationSession session
    ) {
        if (session == null) {
            return;
        }
        for (GateSelectionData selection :
                new ArrayList<GateSelectionData>(session.getSelections())) {
            unindexSelection(session, selection.getPosition());
        }
    }

    private static void revalidateSelectionKey(SelectionKey key) {
        Set<UUID> creators = CREATORS_BY_SELECTION.get(key);
        if (creators == null || creators.isEmpty()) {
            return;
        }
        List<UUID> creatorUuids = new ArrayList<UUID>(creators);
        for (UUID creatorUuid : creatorUuids) {
            GateCreationSession session = SESSIONS_BY_PLAYER.get(creatorUuid);
            if (session == null
                    || session.getDimensionId() != key.dimensionId) {
                unindexSelection(key, creatorUuid);
                continue;
            }

            GateSelectionData selection = session.getSelection(key.position);
            if (selection == null) {
                unindexSelection(key, creatorUuid);
                continue;
            }

            World world = session.getWorld();
            if (world == null
                    || world.isRemote
                    || world.provider.dimensionId != key.dimensionId) {
                continue;
            }
            if (!world.blockExists(
                    key.position.getX(),
                    key.position.getY(),
                    key.position.getZ()
            )) {
                continue;
            }
            if (GateSourceBlockValidator.matchesSelection(world, selection)) {
                continue;
            }

            session.removeSelection(key.position);
            unindexSelection(key, creatorUuid);
            EntityPlayerMP player = findPlayer(world, creatorUuid);
            if (player != null) {
                Main.network.sendTo(
                        GateCreationSyncPacket.partUpdate(
                                key.position,
                                null
                        ),
                        player
                );
                sendConfiguration(player, session);
            }
        }
    }

    private static void sendFullState(
            EntityPlayerMP player,
            GateCreationSession session,
            boolean openControls
    ) {
        List<GateBlockPosition> positions =
                new ArrayList<GateBlockPosition>();
        List<GateLeaf> leaves = new ArrayList<GateLeaf>();
        for (GateSelectionData selection : session.getSelections()) {
            positions.add(selection.getPosition());
            leaves.add(selection.getLeaf());
        }
        Main.network.sendTo(
                GateCreationSyncPacket.start(
                        session.getDimensionId(),
                        session.getControllerPosition(),
                        session.getActiveLeaf(),
                        positions,
                        leaves,
                        session.getSelectionMode(),
                        session.getOpeningDirection(),
                        session.isBorderTextureEnabled(),
                        session.getLeftHingePosition(),
                        session.getRightHingePosition(),
                        openControls
                ),
                player
        );
    }

    private static void sendConfiguration(
            EntityPlayerMP player,
            GateCreationSession session
    ) {
        Main.network.sendTo(
                GateCreationSyncPacket.configuration(
                        session.getActiveLeaf(),
                        session.getSelectionMode(),
                        session.getOpeningDirection(),
                        session.isBorderTextureEnabled(),
                        session.getLeftHingePosition(),
                        session.getRightHingePosition()
                ),
                player
        );
    }

    private static void cancelSession(
            EntityPlayerMP player,
            GateCreationSession session,
            boolean invalidated,
            boolean synchronize
    ) {
        if (invalidated && player != null) {
            sendMessage(player, "Gate Creation Mode was cancelled.");
        }
        finishSession(player, session, synchronize);
    }

    private static void finishSession(
            EntityPlayerMP player,
            GateCreationSession session,
            boolean synchronize
    ) {
        unindexSessionSelections(session);
        SESSIONS_BY_PLAYER.remove(session.getCreatorUuid());
        ControllerKey key = new ControllerKey(
                session.getWorld(),
                session.getDimensionId(),
                session.getControllerPosition()
        );
        if (session.getCreatorUuid().equals(
                CREATORS_BY_CONTROLLER.get(key)
        )) {
            CREATORS_BY_CONTROLLER.remove(key);
        }
        if (synchronize && player != null) {
            Main.network.sendTo(GateCreationSyncPacket.end(), player);
        }
    }

    private static EntityPlayerMP findPlayer(World world, UUID playerUuid) {
        for (Object object : world.playerEntities) {
            if (object instanceof EntityPlayerMP) {
                EntityPlayerMP player = (EntityPlayerMP)object;
                if (playerUuid.equals(player.getUniqueID())) {
                    return player;
                }
            }
        }
        return null;
    }

    private static void sendMessage(EntityPlayer player, String message) {
        player.addChatMessage(new ChatComponentText(message));
    }

    private static boolean isSingleTransitionAction(int action) {
        return action == GateCreationActionPacket.FINALIZE
                || action == GateCreationActionPacket.CANCEL
                || action == GateCreationActionPacket.STOP_SELECTING;
    }

    private static boolean hasEquivalentActionLocked(
            PendingRequest candidate
    ) {
        for (PendingRequest request : PENDING_REQUESTS) {
            if (!request.selection
                    && request.action == candidate.action
                    && request.playerUuid.equals(candidate.playerUuid)
                    && request.dimensionId == candidate.dimensionId
                    && request.controllerPosition.equals(
                            candidate.controllerPosition
                    )) {
                return true;
            }
        }
        return false;
    }

    private static boolean offerLocked(PendingRequest request) {
        if (PENDING_REQUESTS.size() >= MAX_PENDING_REQUESTS) {
            return false;
        }
        Integer pendingCount = PENDING_COUNTS_BY_PLAYER.get(
                request.playerUuid
        );
        int count = pendingCount == null ? 0 : pendingCount.intValue();
        if (count >= MAX_PENDING_REQUESTS_PER_PLAYER) {
            return false;
        }
        PENDING_REQUESTS.addLast(request);
        PENDING_COUNTS_BY_PLAYER.put(
                request.playerUuid,
                Integer.valueOf(count + 1)
        );
        return true;
    }

    private static PendingRequest pollRequest() {
        synchronized (PENDING_REQUEST_LOCK) {
            PendingRequest request = PENDING_REQUESTS.pollFirst();
            if (request == null) {
                return null;
            }
            Integer pendingCount = PENDING_COUNTS_BY_PLAYER.get(
                    request.playerUuid
            );
            int remaining = pendingCount == null
                    ? 0
                    : pendingCount.intValue() - 1;
            if (remaining <= 0) {
                PENDING_COUNTS_BY_PLAYER.remove(request.playerUuid);
            } else {
                PENDING_COUNTS_BY_PLAYER.put(
                        request.playerUuid,
                        Integer.valueOf(remaining)
                );
            }
            return request;
        }
    }

    private static final class PendingRequest {
        private final EntityPlayerMP player;
        private final UUID playerUuid;
        private final boolean selection;
        private final int action;
        private final int x;
        private final int y;
        private final int z;
        private final boolean fillEnclosed;
        private final int dimensionId;
        private final GateBlockPosition controllerPosition;

        private PendingRequest(
                EntityPlayerMP player,
                boolean selection,
                int action,
                int x,
                int y,
                int z,
                boolean fillEnclosed,
                int dimensionId,
                GateBlockPosition controllerPosition
        ) {
            this.player = player;
            this.playerUuid = player.getUniqueID();
            this.selection = selection;
            this.action = action;
            this.x = x;
            this.y = y;
            this.z = z;
            this.fillEnclosed = fillEnclosed;
            this.dimensionId = dimensionId;
            this.controllerPosition = controllerPosition;
        }

        private static PendingRequest action(
                EntityPlayerMP player,
                int action,
                int dimensionId,
                int controllerX,
                int controllerY,
                int controllerZ
        ) {
            return new PendingRequest(
                    player,
                    false,
                    action,
                    0,
                    0,
                    0,
                    false,
                    dimensionId,
                    new GateBlockPosition(
                            controllerX,
                            controllerY,
                            controllerZ
                    )
            );
        }

        private static PendingRequest selection(
                EntityPlayerMP player,
                int x,
                int y,
                int z,
                int dimensionId,
                int controllerX,
                int controllerY,
                int controllerZ,
                boolean fillEnclosed
        ) {
            return new PendingRequest(
                    player,
                    true,
                    0,
                    x,
                    y,
                    z,
                    fillEnclosed,
                    dimensionId,
                    new GateBlockPosition(
                            controllerX,
                            controllerY,
                            controllerZ
                    )
            );
        }

        private boolean matches(GateCreationSession session) {
            return session != null
                    && dimensionId == session.getDimensionId()
                    && controllerPosition.equals(
                            session.getControllerPosition()
                    );
        }
    }

    private static final class ControllerKey {
        private final World world;
        private final int dimensionId;
        private final GateBlockPosition position;

        private ControllerKey(
                World world,
                int dimensionId,
                GateBlockPosition position
        ) {
            this.world = world;
            this.dimensionId = dimensionId;
            this.position = position;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ControllerKey)) {
                return false;
            }
            ControllerKey key = (ControllerKey)other;
            return world == key.world
                    && dimensionId == key.dimensionId
                    && position.equals(key.position);
        }

        @Override
        public int hashCode() {
            int result = System.identityHashCode(world);
            result = 31 * result + dimensionId;
            return 31 * result + position.hashCode();
        }
    }

    private static final class SelectionKey {
        private final int dimensionId;
        private final GateBlockPosition position;

        private SelectionKey(
                int dimensionId,
                GateBlockPosition position
        ) {
            this.dimensionId = dimensionId;
            this.position = position;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof SelectionKey)) {
                return false;
            }
            SelectionKey key = (SelectionKey)other;
            return dimensionId == key.dimensionId
                    && position.equals(key.position);
        }

        @Override
        public int hashCode() {
            return 31 * dimensionId + position.hashCode();
        }
    }
}
