package com.enovak.lotrmoremobs.siege.edit;

import com.enovak.lotrmoremobs.Main;
import com.enovak.lotrmoremobs.siege.SiegeRegistry;
import com.enovak.lotrmoremobs.siege.creation.GateSourceBlockValidator;
import com.enovak.lotrmoremobs.siege.gate.GateEnclosedAreaFill;
import com.enovak.lotrmoremobs.siege.gate.GateHinge;
import com.enovak.lotrmoremobs.siege.gate.GateRegistry;
import com.enovak.lotrmoremobs.siege.gate.GateSourceTileEntitySnapshot;
import com.enovak.lotrmoremobs.siege.gate.SiegeGateOwnershipData;
import com.enovak.lotrmoremobs.siege.gate.GateStructureValidator;
import com.enovak.lotrmoremobs.siege.gate.GatePartData;
import com.enovak.lotrmoremobs.siege.management.GateInspectionSession;
import com.enovak.lotrmoremobs.siege.management.GateInspectionSessionManager;
import com.enovak.lotrmoremobs.siege.network.SiegeRequestLimiter;
import com.enovak.lotrmoremobs.siege.network.GateEditCommitResultPacket;
import com.enovak.lotrmoremobs.siege.network.GateEditDraftSnapshotPacket;
import com.enovak.lotrmoremobs.siege.network.GateEditPreflightSnapshotPacket;
import com.enovak.lotrmoremobs.siege.network.GateEditSessionStatusPacket;
import com.enovak.lotrmoremobs.siege.repair.GateManagementManager;
import com.enovak.lotrmoremobs.siege.tile.TileEntitySiegeGate;
import cpw.mods.fml.common.FMLLog;
import java.util.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;
import net.minecraft.nbt.NBTTagCompound;

/** Owns only transient EDIT_EXISTING sessions and GateUUID leases. */
public final class GateEditSessionManager {
    public static final long SESSION_TIMEOUT_TICKS = 20L * 60L * 5L;
    private static final double MAX_DISTANCE_SQ = GateManagementManager.MAX_MANAGEMENT_DISTANCE * GateManagementManager.MAX_MANAGEMENT_DISTANCE;
    private static final Map<UUID, GateEditSession> SESSIONS_BY_PLAYER = new HashMap<UUID, GateEditSession>();
    private static final Map<UUID, UUID> LEASE_OWNER_BY_GATE = new HashMap<UUID, UUID>();
    private static final int MAX_PENDING_EDIT_COMMIT_REQUESTS = 64;
    private static final int MAX_PENDING_EDIT_COMMIT_REQUESTS_PER_TICK = 4;
    private static final Map<UUID, PendingEditCommitRequest> PENDING_EDIT_COMMIT_BY_PLAYER = new HashMap<UUID, PendingEditCommitRequest>();
    private static final Deque<UUID> PENDING_EDIT_COMMIT_PLAYERS = new ArrayDeque<UUID>();
    private static long serverTick;
    private static Thread authoritativeServerThread;
    private GateEditSessionManager() {}

    public static synchronized Result start(EntityPlayerMP player) {
        if (player == null) return Result.refused(GateEditStatus.GATE_UNAVAILABLE);
        GateInspectionSession inspection = GateInspectionSessionManager.getSession(player.getUniqueID());
        if (inspection == null) return Result.refused(GateEditStatus.NO_INSPECTION);
        GateEditStatus validation = validate(player, inspection);
        if (validation != null) return Result.refused(validation);
        GateEditSession existing = SESSIONS_BY_PLAYER.get(player.getUniqueID());
        if (existing != null) {
            return existing.getGateUuid().equals(inspection.getGateUuid())
                    ? Result.opened(existing) : Result.refused(GateEditStatus.EDIT_SESSION_ACTIVE);
        }
        UUID leaseOwner = LEASE_OWNER_BY_GATE.get(inspection.getGateUuid());
        if (leaseOwner != null && !leaseOwner.equals(player.getUniqueID())) return Result.refused(GateEditStatus.EDIT_LEASED);
        TileEntitySiegeGate gate = getExactGate(player, inspection);
        if (gate == null) {
            return Result.refused(GateEditStatus.GATE_UNAVAILABLE);
        }
        try {
            GateEditSession session = new GateEditSession(player.getUniqueID(), UUID.randomUUID(), GateEditOriginalSnapshot.fromController(gate), expiry());
            if (!session.getDraft().matchesOriginal(session.getOriginal())) return Result.refused(GateEditStatus.GATE_UNAVAILABLE);
            SESSIONS_BY_PLAYER.put(player.getUniqueID(), session);
            LEASE_OWNER_BY_GATE.put(session.getGateUuid(), player.getUniqueID());
            return Result.opened(session);
        } catch (IllegalArgumentException ignored) { return Result.refused(GateEditStatus.GATE_UNAVAILABLE); }
    }

    public static synchronized GateEditStatus cancel(EntityPlayerMP player, UUID token) {
        if (player == null) return GateEditStatus.SESSION_EXPIRED;
        GateEditSession session = SESSIONS_BY_PLAYER.get(player.getUniqueID());
        if (session == null) return GateEditStatus.SESSION_EXPIRED;
        if (token == null || !token.equals(session.getSessionToken())) return GateEditStatus.TOKEN_MISMATCH;
        remove(player.getUniqueID(), session); return GateEditStatus.CANCELLED;
    }
    /** All externally requested Phase-2 changes arrive here; this mutates only the detached draft. */
    public static synchronized ActionResult applyDraftAction(EntityPlayerMP player, UUID token, GateEditDraftAction action, int x, int y, int z) {
        return applyDraftAction(player, token, action, x, y, z, false);
    }

    public static synchronized ActionResult applyDraftAction(EntityPlayerMP player, UUID token, GateEditDraftAction action, int x, int y, int z, boolean fillEnclosed) {
        if (player == null || action == null) return ActionResult.refused(GateEditStatus.GATE_UNAVAILABLE, null);
        GateEditSession session = SESSIONS_BY_PLAYER.get(player.getUniqueID());
        if (session == null) return ActionResult.refused(GateEditStatus.SESSION_EXPIRED, null);
        if (token == null || !token.equals(session.getSessionToken())) return ActionResult.refused(GateEditStatus.TOKEN_MISMATCH, session);
        if (player.worldObj == null || player.worldObj.isRemote || player.dimension != session.getDimensionId()) return ActionResult.refused(GateEditStatus.WRONG_DIMENSION, session);
        if (hasPersistentMutation(player.worldObj, session)) return ActionResult.refused(GateEditStatus.MUTATION_IN_PROGRESS, session);
        if (action.isDirection()) { session.getDraft().setOpeningDirection(action.getDirection()); session.incrementDraftSequence(); session.refreshExpiry(expiry()); return ActionResult.accepted(session); }
        if (action.isBorderTexture()) { session.getDraft().setBorderTextureEnabled(action.getBorderTextureEnabled()); session.incrementDraftSequence(); session.refreshExpiry(expiry()); return ActionResult.accepted(session); }
        if (!SiegeRequestLimiter.isSaneBlockPosition(x,y,z)) return ActionResult.refused(GateEditStatus.GATE_UNAVAILABLE,session);
        if (player.getDistanceSq(x+0.5D,y+0.5D,z+0.5D)>36.0D) return ActionResult.refused(GateEditStatus.TOO_FAR,session);
        World world=player.worldObj;
        if (!world.blockExists(x,y,z)) return ActionResult.refused(GateEditStatus.CHUNK_UNLOADED,session);
        if (x==session.getControllerX()&&y==session.getControllerY()&&z==session.getControllerZ()) return ActionResult.refused(GateEditStatus.GATE_UNAVAILABLE,session);
        int rx=x-session.getControllerX(), ry=y-session.getControllerY(), rz=z-session.getControllerZ();
        GateEditCoordinate key=new GateEditCoordinate(rx,ry,rz); GateEditDraft draft=session.getDraft(); GateEditDraftPart current=draft.getPart(key);
        GateEditStatus status;
        if(action.isSelect()) status=fillEnclosed
                ? fillEnclosed(world,player,session,draft,key,action.getLeaf())
                : select(world,session,draft,key,current,action.getLeaf(),x,y,z);
        else if(action==GateEditDraftAction.SET_LEFT_HINGE) status=setHinge(world,session,draft,key,current,true,x,y,z);
        else if(action==GateEditDraftAction.SET_RIGHT_HINGE) status=setHinge(world,session,draft,key,current,false,x,y,z);
        else status=GateEditStatus.GATE_UNAVAILABLE;
        if(status!=GateEditStatus.ACTION_ACCEPTED) return ActionResult.refused(status,session);
        session.incrementDraftSequence(); session.refreshExpiry(expiry()); return ActionResult.accepted(session);
    }
    public static synchronized GateEditSession getSession(UUID playerUuid) { return playerUuid == null ? null : SESSIONS_BY_PLAYER.get(playerUuid); }

    /**
     * Bounded durable-commit admission queue. Normal packet intent reaches
     * this through GateEditRequestManager on the authoritative server tick;
     * the durable commit itself is still prepared/drained by this manager.
     */
    public static synchronized PendingCommitRequestResult enqueueEditCommitRequest(
            EntityPlayerMP player, UUID token, long expectedDraftSequence
    ) {
        if (player == null || token == null || expectedDraftSequence < 0L
                || (token.getMostSignificantBits() == 0L
                && token.getLeastSignificantBits() == 0L)) {
            return PendingCommitRequestResult.REJECTED;
        }
        UUID playerUuid = player.getUniqueID();
        if (PENDING_EDIT_COMMIT_BY_PLAYER.containsKey(playerUuid)) {
            return PendingCommitRequestResult.DUPLICATE;
        }
        if (PENDING_EDIT_COMMIT_BY_PLAYER.size()
                >= MAX_PENDING_EDIT_COMMIT_REQUESTS) {
            return PendingCommitRequestResult.REJECTED;
        }
        PENDING_EDIT_COMMIT_BY_PLAYER.put(playerUuid,
                new PendingEditCommitRequest(player, token, expectedDraftSequence));
        PENDING_EDIT_COMMIT_PLAYERS.addLast(playerUuid);
        return PendingCommitRequestResult.QUEUED;
    }

    /** Refreshes the server-owned draft mirror after a pre-PREPARED refusal. */
    public static synchronized void pushCurrentDraftAndPreflight(
            EntityPlayerMP player, UUID token
    ) {
        if (player == null || token == null) return;
        GateEditSession current = SESSIONS_BY_PLAYER.get(player.getUniqueID());
        if (current != null && token.equals(current.getSessionToken())) {
            Main.network.sendTo(new GateEditDraftSnapshotPacket(current), player);
            Main.network.sendTo(new GateEditPreflightSnapshotPacket(current,
                    GateEditPreflight.evaluate(player, current)), player);
        }
    }

    /**
     * Server-internal Phase 4C admission only. No packet, GUI, controller
     * interaction, or normal gameplay path calls this method yet.
     */
    public static synchronized EditCommitAdmissionResult prepareEditCommit(
            EntityPlayerMP player,
            GateEditSession session,
            long expectedDraftSequence
    ) {
        if (!isAuthoritativeServerThread()) {
            return EditCommitAdmissionResult.rejected(
                    EditCommitAdmissionResult.State.INTERNAL_REJECTED
            );
        }
        if (player == null || session == null
                || SESSIONS_BY_PLAYER.get(player.getUniqueID()) != session
                || !player.getUniqueID().equals(session.getPlayerUuid())
                || player.worldObj == null || player.worldObj.isRemote
                || player.dimension != session.getDimensionId()
                || session.isExpired(serverTick)) {
            return EditCommitAdmissionResult.rejected(
                    EditCommitAdmissionResult.State.INVALID_SESSION
            );
        }
        if (expectedDraftSequence != session.getDraftSequence()) {
            return EditCommitAdmissionResult.rejected(
                    EditCommitAdmissionResult.State.STALE_DRAFT
            );
        }
        if (session.getBaseRevision() <= 0
                || session.getBaseRevision() == Integer.MAX_VALUE) {
            return EditCommitAdmissionResult.rejected(
                    EditCommitAdmissionResult.State.REVISION_OVERFLOW
            );
        }

        GateEditPreflightResult preflight = GateEditPreflight.evaluate(
                player, session
        );
        if (preflight.getState() != GateEditPreflightState.READY) {
            return EditCommitAdmissionResult.rejected(
                    EditCommitAdmissionResult.State.NOT_READY
            );
        }
        GateEditCommitMaterial material = GateEditPreflight.buildCommitMaterial(
                session
        );
        if (material == null) {
            return EditCommitAdmissionResult.rejected(
                    EditCommitAdmissionResult.State.INTERNAL_REJECTED
            );
        }
        SiegeGateOwnershipData ownership = SiegeGateOwnershipData.get(
                player.worldObj, false
        );
        if (ownership == null) {
            return EditCommitAdmissionResult.rejected(
                    EditCommitAdmissionResult.State.OWNERSHIP_CONFLICT
            );
        }
        SiegeGateOwnershipData.EditCommitPrepareResult durable =
                ownership.prepareEditCommit(material, player.getUniqueID(),
                        player.worldObj.getTotalWorldTime());
        EditCommitAdmissionResult result =
                EditCommitAdmissionResult.fromDurable(durable);
        if (result.getState() != EditCommitAdmissionResult.State.PREPARED) {
            return result;
        }
        try {
            remove(player.getUniqueID(), session);
        } catch (RuntimeException exception) {
            // Durable PREPARED already owns exclusivity; never roll it back.
            FMLLog.warning("[LOTRMoreMobs] Siege Gate PREPARED %s kept durable "
                            + "ownership after transient edit cleanup failed: %s",
                    result.getJobUuid(), exception.getClass().getSimpleName());
        }
        return result;
    }
    /** Token-bound read-only preflight entry point used by request packets and server pushes. */
    public static synchronized PreflightRequest evaluatePreflight(EntityPlayerMP player, UUID token) {
        if(player==null)return PreflightRequest.refused(GateEditStatus.SESSION_EXPIRED);
        GateEditSession session=SESSIONS_BY_PLAYER.get(player.getUniqueID());
        if(session==null)return PreflightRequest.refused(GateEditStatus.SESSION_EXPIRED);
        if(token==null||!token.equals(session.getSessionToken()))return PreflightRequest.refused(GateEditStatus.TOKEN_MISMATCH);
        return PreflightRequest.accepted(session,GateEditPreflight.evaluate(player,session));
    }
    public static synchronized GateEditPreflightResult evaluatePreflight(EntityPlayerMP player, GateEditSession session) { return GateEditPreflight.evaluate(player,session); }
    public static synchronized GateEditSession getMatchingSession(EntityPlayerMP player, TileEntitySiegeGate gate) {
        GateEditSession session = player == null || gate == null ? null : SESSIONS_BY_PLAYER.get(player.getUniqueID());
        return session != null && session.getDimensionId() == player.dimension && session.getControllerX() == gate.xCoord && session.getControllerY() == gate.yCoord && session.getControllerZ() == gate.zCoord && session.getGateUuid().equals(gate.getExistingGateUuid()) ? session : null;
    }
    /**
     * Cancels the transient EDIT_EXISTING session bound to a controller that
     * has actually been removed from the world.
     *
     * breakBlock() calls this on the authoritative server side, so the server
     * lease is released and the editor's client mirror is explicitly cleared.
     */
    public static synchronized void cancelForController(
            World world,
            int controllerX,
            int controllerY,
            int controllerZ
    ) {
        if (world == null || world.isRemote) {
            return;
        }

        int dimensionId = world.provider.dimensionId;
        List<UUID> matchingPlayers = new ArrayList<UUID>();

        for (Map.Entry<UUID, GateEditSession> entry
                : SESSIONS_BY_PLAYER.entrySet()) {
            GateEditSession session = entry.getValue();

            if (session != null
                    && session.getDimensionId() == dimensionId
                    && session.getControllerX() == controllerX
                    && session.getControllerY() == controllerY
                    && session.getControllerZ() == controllerZ) {
                matchingPlayers.add(entry.getKey());
            }
        }

        for (UUID playerUuid : matchingPlayers) {
            GateEditSession session = SESSIONS_BY_PLAYER.get(playerUuid);
            if (session == null
                    || session.getDimensionId() != dimensionId
                    || session.getControllerX() != controllerX
                    || session.getControllerY() != controllerY
                    || session.getControllerZ() != controllerZ) {
                continue;
            }

            remove(playerUuid, session);

            /*
             * A controller removal wins over any still-queued transient commit
             * request. Do not let a stale request run one tick later and emit
             * an unrelated commit result after edit mode has been cancelled.
             */
            GateEditRequestManager.clearPlayer(playerUuid);
            PENDING_EDIT_COMMIT_BY_PLAYER.remove(playerUuid);
            PENDING_EDIT_COMMIT_PLAYERS.remove(playerUuid);

            EntityPlayerMP player = findPlayer(world, playerUuid);
            if (player != null && player.playerNetServerHandler != null) {
                Main.network.sendTo(
                        new GateEditSessionStatusPacket(
                                GateEditStatus.CANCELLED,
                                null
                        ),
                        player
                );
            }
        }
    }
    public static synchronized void closeForPlayer(EntityPlayerMP player) { if (player != null) closeForPlayer(player.getUniqueID()); }
    public static synchronized void closeForPlayer(UUID playerUuid) { GateEditSession session = playerUuid == null ? null : SESSIONS_BY_PLAYER.get(playerUuid); if (session != null) remove(playerUuid, session); }
    public static synchronized void tick() { if(authoritativeServerThread==null)authoritativeServerThread=Thread.currentThread(); if(authoritativeServerThread!=Thread.currentThread())return; GateEditRequestManager.processServerTick(); if (serverTick < Long.MAX_VALUE) ++serverTick; Iterator<Map.Entry<UUID,GateEditSession>> it=SESSIONS_BY_PLAYER.entrySet().iterator(); while(it.hasNext()){Map.Entry<UUID,GateEditSession> e=it.next(); GateEditSession s=e.getValue(); if(s==null||s.isExpired(serverTick)){if(s!=null && e.getKey().equals(LEASE_OWNER_BY_GATE.get(s.getGateUuid()))) LEASE_OWNER_BY_GATE.remove(s.getGateUuid()); it.remove();}} drainPendingEditCommitRequests(); }
    public static synchronized void resetServerState() { SESSIONS_BY_PLAYER.clear(); LEASE_OWNER_BY_GATE.clear(); PENDING_EDIT_COMMIT_BY_PLAYER.clear(); PENDING_EDIT_COMMIT_PLAYERS.clear(); serverTick=0L; authoritativeServerThread=null; }
    private static void remove(UUID playerUuid, GateEditSession session) { SESSIONS_BY_PLAYER.remove(playerUuid); if(playerUuid.equals(LEASE_OWNER_BY_GATE.get(session.getGateUuid()))) LEASE_OWNER_BY_GATE.remove(session.getGateUuid()); }
    private static long expiry() { return serverTick > Long.MAX_VALUE - SESSION_TIMEOUT_TICKS ? Long.MAX_VALUE : serverTick + SESSION_TIMEOUT_TICKS; }

    private static boolean isAuthoritativeServerThread() {
        return authoritativeServerThread != null
                && authoritativeServerThread == Thread.currentThread();
    }

    private static void drainPendingEditCommitRequests() {
        for (int processed = 0; processed
                < MAX_PENDING_EDIT_COMMIT_REQUESTS_PER_TICK; ++processed) {
            UUID playerUuid = PENDING_EDIT_COMMIT_PLAYERS.pollFirst();
            if (playerUuid == null) return;
            PendingEditCommitRequest request =
                    PENDING_EDIT_COMMIT_BY_PLAYER.remove(playerUuid);
            if (request == null) continue;
            processPendingEditCommitRequest(request);
        }
    }

    private static void processPendingEditCommitRequest(
            PendingEditCommitRequest request
    ) {
        EntityPlayerMP player = request.player;
        EditCommitAdmissionResult result;
        try {
            GateEditSession session = player == null ? null
                    : SESSIONS_BY_PLAYER.get(player.getUniqueID());
            if (session == null || !request.token.equals(session.getSessionToken())) {
                result = EditCommitAdmissionResult.rejected(
                        EditCommitAdmissionResult.State.INVALID_SESSION);
            } else {
                result = prepareEditCommit(player, session,
                        request.expectedDraftSequence);
            }
        } catch (RuntimeException exception) {
            FMLLog.warning("[LOTRMoreMobs] Siege Gate edit commit intake failed: %s",
                    exception.getClass().getSimpleName());
            result = EditCommitAdmissionResult.rejected(
                    EditCommitAdmissionResult.State.INTERNAL_REJECTED);
        }
        if (player == null || player.playerNetServerHandler == null) return;
        Main.network.sendTo(new GateEditCommitResultPacket(result), player);
        if (result.getState() == EditCommitAdmissionResult.State.PREPARED) return;
        pushCurrentDraftAndPreflight(player, request.token);
    }

    private static GateEditStatus validate(EntityPlayerMP player, GateInspectionSession inspection) {
        TileEntitySiegeGate gate = getExactGate(player, inspection);
        if (gate == null) return GateEditStatus.GATE_UNAVAILABLE;
        if (!gate.isFinalized() || gate.isGateStructureQuarantined()) return GateEditStatus.GATE_UNAVAILABLE;
        if (!gate.canManage(player)) return GateEditStatus.NO_PERMISSION;
        if (gate.getExistingGateUuid() == null || !gate.getExistingGateUuid().equals(inspection.getGateUuid())) return GateEditStatus.UUID_MISMATCH;
        if (gate.getStructureRevision() <= 0 || gate.getStructureRevision() != inspection.getBaseRevision()) return GateEditStatus.STALE_REVISION;
        if (hasPersistentMutation(player.worldObj, inspection.getGateUuid(), inspection.getControllerX(), inspection.getControllerY(), inspection.getControllerZ())) return GateEditStatus.MUTATION_IN_PROGRESS;
        SiegeGateOwnershipData data = SiegeGateOwnershipData.get(player.worldObj, false);
        return data != null && data.matchesActiveController(gate) ? null : GateEditStatus.OWNERSHIP_MISMATCH;
    }

    private static boolean hasPersistentMutation(World world, GateEditSession session) {
        return session != null && hasPersistentMutation(world,
                session.getGateUuid(), session.getControllerX(),
                session.getControllerY(), session.getControllerZ());
    }

    private static boolean hasPersistentMutation(
            World world, UUID gateUuid, int controllerX, int controllerY, int controllerZ
    ) {
        SiegeGateOwnershipData data = SiegeGateOwnershipData.get(world, false);
        return data != null && data.isGateMutationLocked(gateUuid,
                world.provider.dimensionId, controllerX, controllerY, controllerZ);
    }

    private static GateEditStatus fillEnclosed(
            World world,
            EntityPlayerMP player,
            GateEditSession session,
            GateEditDraft draft,
            GateEditCoordinate seed,
            com.enovak.lotrmoremobs.siege.gate.GateLeaf requested
    ) {
        if (world == null
                || player == null
                || session == null
                || draft == null
                || seed == null
                || requested == null) {
            return GateEditStatus.GATE_UNAVAILABLE;
        }
        if (requested
                == com.enovak.lotrmoremobs.siege.gate.GateLeaf.SPLIT_CENTER) {
            sendFillMessage(
                    player,
                    "Shift-fill is available for LEFT and RIGHT leaves only."
            );
            return GateEditStatus.ACTION_ACCEPTED;
        }

        List<GateEnclosedAreaFill.Position> boundary =
                new ArrayList<GateEnclosedAreaFill.Position>();

        for (GateEditDraftPart part : draft.getParts()) {
            if (part != null && part.getLeaf() == requested) {
                boundary.add(
                        new GateEnclosedAreaFill.Position(
                                part.getRelativeX(),
                                part.getRelativeY(),
                                part.getRelativeZ()
                        )
                );
            }
        }

        GateEnclosedAreaFill.Result fill =
                GateEnclosedAreaFill.find(
                        boundary,
                        new GateEnclosedAreaFill.Position(
                                seed.x,
                                seed.y,
                                seed.z
                        ),
                        draft.getOrientation()
                );

        if (!fill.isSuccess()) {
            sendFillMessage(
                    player,
                    "Shift-fill needs a closed " + requested.name()
                            + " outline around the clicked block."
            );
            /*
             * Treat a well-formed shortcut attempt as consumed. The draft is
             * unchanged, but returning ACTION_ACCEPTED prevents a second noisy
             * generic error from being layered on top of the useful chat hint.
             */
            return GateEditStatus.ACTION_ACCEPTED;
        }

        List<GateEditCoordinate> roleChanges =
                new ArrayList<GateEditCoordinate>();
        List<GateEditOriginalPart> restores =
                new ArrayList<GateEditOriginalPart>();
        List<PreparedFillAddition> additions =
                new ArrayList<PreparedFillAddition>();
        List<GateEditCoordinate> prospectiveNewCoordinates =
                new ArrayList<GateEditCoordinate>();

        for (GateEnclosedAreaFill.Position position
                : fill.getPositions()) {

            GateEditCoordinate key =
                    new GateEditCoordinate(
                            position.x,
                            position.y,
                            position.z
                    );

            if (key.x == 0 && key.y == 0 && key.z == 0) {
                continue;
            }

            int absoluteX = session.getControllerX() + key.x;
            int absoluteY = session.getControllerY() + key.y;
            int absoluteZ = session.getControllerZ() + key.z;

            if (!SiegeRequestLimiter.isSaneBlockPosition(
                    absoluteX,
                    absoluteY,
                    absoluteZ
            )
                    || !world.blockExists(
                    absoluteX,
                    absoluteY,
                    absoluteZ
            )) {
                continue;
            }

            GateEditDraftPart current = draft.getPart(key);
            if (current != null) {
                GateEditStatus present = validateCurrentDraftPart(
                        world,
                        session,
                        current,
                        absoluteX,
                        absoluteY,
                        absoluteZ
                );
                if (present != GateEditStatus.ACTION_ACCEPTED) {
                    return present;
                }
                if (current.getLeaf() != requested) {
                    roleChanges.add(key);
                }
                continue;
            }

            GateEditOriginalPart original =
                    session.getOriginal().findPart(key);
            if (original != null) {
                if (!isOwnedLiveGatePart(
                        world,
                        session,
                        absoluteX,
                        absoluteY,
                        absoluteZ
                )) {
                    return GateEditStatus.NOT_OWNED_GATEPART;
                }
                restores.add(original);
                prospectiveNewCoordinates.add(key);
                continue;
            }

            PreparedFillAddition prepared =
                    prepareFillAddition(
                            world,
                            key,
                            requested,
                            absoluteX,
                            absoluteY,
                            absoluteZ
                    );
            if (prepared != null) {
                additions.add(prepared);
                prospectiveNewCoordinates.add(key);
            }
        }

        if (draft.getPartCount()
                + restores.size()
                + additions.size()
                > GateStructureValidator.MAX_GATE_PARTS) {
            sendFillMessage(
                    player,
                    "That fill would exceed the "
                            + GateStructureValidator.MAX_GATE_PARTS
                            + "-block gate limit."
            );
            return GateEditStatus.ACTION_ACCEPTED;
        }

        if (!withinHardEnvelopeBulk(
                draft,
                prospectiveNewCoordinates
        )) {
            sendFillMessage(
                    player,
                    "That fill would exceed the gate's "
                            + GateStructureValidator.MAX_GATE_WIDTH + " x "
                            + GateStructureValidator.MAX_GATE_HEIGHT + " x "
                            + GateStructureValidator.MAX_GATE_THICKNESS
                            + " size limit."
            );
            return GateEditStatus.ACTION_ACCEPTED;
        }

        int changed = 0;

        for (GateEditCoordinate key : roleChanges) {
            if (draft.setRole(key, requested)) {
                ++changed;
            }
        }

        for (GateEditOriginalPart original : restores) {
            if (draft.restoreOriginal(original, requested) != null) {
                ++changed;
            }
        }

        for (PreparedFillAddition addition : additions) {
            if (draft.addPart(
                    addition.key.x,
                    addition.key.y,
                    addition.key.z,
                    requested,
                    addition.sourceName,
                    addition.sourceMeta,
                    addition.sourceTileEntityNbt,
                    addition.restorable
            ) != null) {
                ++changed;
            }
        }

        sendFillMessage(
                player,
                changed <= 0
                        ? "No additional gate blocks were found inside that outline."
                        : "Filled " + changed + " block"
                        + (changed == 1 ? "" : "s")
                        + " as " + requested.name() + "."
        );

        return GateEditStatus.ACTION_ACCEPTED;
    }

    private static PreparedFillAddition prepareFillAddition(
            World world,
            GateEditCoordinate key,
            com.enovak.lotrmoremobs.siege.gate.GateLeaf leaf,
            int x,
            int y,
            int z
    ) {
        if (world == null
                || key == null
                || leaf == null
                || GateRegistry.getDurablePartOwner(world, x, y, z) != null
                || !GateSourceBlockValidator.isValid(world, x, y, z)) {
            return null;
        }

        Block block = world.getBlock(x, y, z);
        String name = GateSourceBlockValidator.getRegisteredName(block);
        if (name == null) {
            return null;
        }

        int meta = world.getBlockMetadata(x, y, z);
        TileEntity sourceTileEntity = world.getTileEntity(x, y, z);
        NBTTagCompound sourceTileEntityNbt = null;

        try {
            boolean requiresTileEntity = block.hasTileEntity(meta);
            if (requiresTileEntity && sourceTileEntity == null) {
                return null;
            }
            if (sourceTileEntity != null) {
                sourceTileEntityNbt =
                        GateSourceTileEntitySnapshot
                                .captureForGateStorage(
                                        sourceTileEntity
                                );
            }
        } catch (RuntimeException ignored) {
            return null;
        }

        GatePartData captured = new GatePartData(
                key.x,
                key.y,
                key.z,
                leaf,
                name,
                meta,
                sourceTileEntityNbt
        );
        boolean restorable = captured.hasStoredSourceBlock()
                && captured.getSourceBlockForRestoration() != null;

        if (!captured.hasStoredSourceAppearance() || !restorable) {
            return null;
        }

        return new PreparedFillAddition(
                key,
                name,
                meta,
                sourceTileEntityNbt,
                restorable
        );
    }

    private static boolean withinHardEnvelopeBulk(
            GateEditDraft draft,
            Collection<GateEditCoordinate> additions
    ) {
        if (draft == null) {
            return false;
        }

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        boolean found = false;

        for (GateEditDraftPart part : draft.getParts()) {
            if (part == null) {
                continue;
            }
            minX = Math.min(minX, part.getRelativeX());
            maxX = Math.max(maxX, part.getRelativeX());
            minY = Math.min(minY, part.getRelativeY());
            maxY = Math.max(maxY, part.getRelativeY());
            minZ = Math.min(minZ, part.getRelativeZ());
            maxZ = Math.max(maxZ, part.getRelativeZ());
            found = true;
        }

        if (additions != null) {
            for (GateEditCoordinate key : additions) {
                if (key == null) {
                    continue;
                }
                minX = Math.min(minX, key.x);
                maxX = Math.max(maxX, key.x);
                minY = Math.min(minY, key.y);
                maxY = Math.max(maxY, key.y);
                minZ = Math.min(minZ, key.z);
                maxZ = Math.max(maxZ, key.z);
                found = true;
            }
        }

        if (!found) {
            return false;
        }

        int spanX = maxX - minX + 1;
        int spanY = maxY - minY + 1;
        int spanZ = maxZ - minZ + 1;

        if (spanY > GateStructureValidator.MAX_GATE_HEIGHT) {
            return false;
        }

        return draft.getOrientation()
                == com.enovak.lotrmoremobs.siege.gate.GateOrientation.WIDTH_X
                ? spanX <= GateStructureValidator.MAX_GATE_WIDTH
                && spanZ <= GateStructureValidator.MAX_GATE_THICKNESS
                : spanZ <= GateStructureValidator.MAX_GATE_WIDTH
                && spanX <= GateStructureValidator.MAX_GATE_THICKNESS;
    }

    private static void sendFillMessage(
            EntityPlayerMP player,
            String message
    ) {
        if (player != null && message != null) {
            player.addChatMessage(new ChatComponentText(message));
        }
    }

    private static final class PreparedFillAddition {
        private final GateEditCoordinate key;
        private final String sourceName;
        private final int sourceMeta;
        private final NBTTagCompound sourceTileEntityNbt;
        private final boolean restorable;

        private PreparedFillAddition(
                GateEditCoordinate key,
                String sourceName,
                int sourceMeta,
                NBTTagCompound sourceTileEntityNbt,
                boolean restorable
        ) {
            this.key = key;
            this.sourceName = sourceName;
            this.sourceMeta = sourceMeta;
            this.sourceTileEntityNbt = sourceTileEntityNbt == null
                    ? null
                    : (NBTTagCompound)sourceTileEntityNbt.copy();
            this.restorable = restorable;
        }
    }

    private static GateEditStatus add(
            World world,
            GateEditSession session,
            GateEditDraft draft,
            GateEditCoordinate key,
            com.enovak.lotrmoremobs.siege.gate.GateLeaf leaf,
            int x,
            int y,
            int z
    ) {
        if (draft.getPart(key) != null) {
            return GateEditStatus.ALREADY_IN_DRAFT;
        }

        if (draft.getPartCount()
                >= GateStructureValidator.MAX_GATE_PARTS) {
            return GateEditStatus.PART_LIMIT;
        }

        if (GateRegistry.getDurablePartOwner(
                world,
                x,
                y,
                z
        ) != null) {
            return GateEditStatus.FOREIGN_OWNER;
        }

        if (!GateSourceBlockValidator.isValid(
                world,
                x,
                y,
                z
        )) {
            return GateEditStatus.INVALID_SOURCE;
        }

        if (!withinHardEnvelope(
                draft,
                x - session.getControllerX(),
                y - session.getControllerY(),
                z - session.getControllerZ()
        )) {
            return GateEditStatus.WIDTH_LIMIT;
        }

        Block block =
                world.getBlock(
                        x,
                        y,
                        z
                );

        String name =
                GateSourceBlockValidator.getRegisteredName(
                        block
                );

        if (name == null) {
            return GateEditStatus.INVALID_SOURCE;
        }

        int meta =
                world.getBlockMetadata(
                        x,
                        y,
                        z
                );

        TileEntity sourceTileEntity =
                world.getTileEntity(
                        x,
                        y,
                        z
                );

        NBTTagCompound sourceTileEntityNbt =
                null;

        boolean requiresTileEntity;

        try {
            requiresTileEntity =
                    block.hasTileEntity(
                            meta
                    );

            /*
             * A TE-backed source is not safe to draft unless its real TE exists
             * and can be snapshotted.
             */
            if (requiresTileEntity
                    && sourceTileEntity == null) {
                return GateEditStatus.INVALID_SOURCE;
            }

            if (sourceTileEntity != null) {
                sourceTileEntityNbt =
                        GateSourceTileEntitySnapshot
                                .captureForGateStorage(
                                        sourceTileEntity
                                );
            }

        } catch (RuntimeException exception) {
            return GateEditStatus.INVALID_SOURCE;
        }

        GatePartData captured =
                new GatePartData(
                        key.x,
                        key.y,
                        key.z,
                        leaf,
                        name,
                        meta,
                        sourceTileEntityNbt
                );

        boolean restorable =
                captured.hasStoredSourceBlock()
                        && captured.getSourceBlockForRestoration()
                        != null;

        /*
         * Newly-added edit sources must be fully reversible.
         * We do not allow a new EDIT_EXISTING selection that would later have
         * to disappear into air on REMOVE/controller teardown.
         */
        if (!captured.hasStoredSourceAppearance()
                || !restorable) {
            return GateEditStatus.INVALID_SOURCE;
        }

        if (draft.addPart(
                key.x,
                key.y,
                key.z,
                leaf,
                name,
                meta,
                sourceTileEntityNbt,
                restorable
        ) == null) {
            return GateEditStatus.GATE_UNAVAILABLE;
        }

        return GateEditStatus.ACTION_ACCEPTED;
    }

    /** Classifies one public SELECT intent using only authoritative session/original/draft/world facts. */
    private static GateEditStatus select(World world, GateEditSession session, GateEditDraft draft, GateEditCoordinate key, GateEditDraftPart current, com.enovak.lotrmoremobs.siege.gate.GateLeaf requested, int x, int y, int z) {
        if (requested == null) return GateEditStatus.INVALID_ROLE;
        if (current != null) {
            GateEditStatus present = validateCurrentDraftPart(world, session, current, x, y, z);
            if (present != GateEditStatus.ACTION_ACCEPTED) return present;
            if (current.getLeaf() == requested) {
                draft.removePart(key);
                return GateEditStatus.ACTION_ACCEPTED;
            }
            return draft.setRole(key, requested)
                    ? GateEditStatus.ACTION_ACCEPTED
                    : GateEditStatus.INVALID_ROLE;
        }
        GateEditOriginalPart original = session.getOriginal().findPart(key);
        if (original != null) {
            if (!isOwnedLiveGatePart(world, session, x, y, z)) return GateEditStatus.NOT_OWNED_GATEPART;
            return draft.restoreOriginal(original, requested) == null
                    ? GateEditStatus.GATE_UNAVAILABLE
                    : GateEditStatus.ACTION_ACCEPTED;
        }
        return add(world, session, draft, key, requested, x, y, z);
    }
    private static GateEditStatus remove(World world, GateEditSession session, GateEditDraft draft, GateEditCoordinate key, GateEditDraftPart current,int x,int y,int z) {
        if(current==null)return GateEditStatus.NOT_IN_DRAFT;
        if(current.originatesFromOriginal()&&!isOwnedLiveGatePart(world,session,x,y,z))return GateEditStatus.NOT_OWNED_GATEPART;
        draft.removePart(key);return GateEditStatus.ACTION_ACCEPTED;
    }
    private static GateEditStatus setRole(World world, GateEditSession session, GateEditDraft draft, GateEditCoordinate key, GateEditDraftPart current, com.enovak.lotrmoremobs.siege.gate.GateLeaf leaf,int x,int y,int z) {
        if(current==null)return GateEditStatus.NOT_IN_DRAFT;
        if(current.originatesFromOriginal()&&!isOwnedLiveGatePart(world,session,x,y,z))return GateEditStatus.NOT_OWNED_GATEPART;
        if(!current.originatesFromOriginal()&&!matchesAddedSource(world,current,x,y,z))return GateEditStatus.SOURCE_CHANGED;
        return draft.setRole(key,leaf)?GateEditStatus.ACTION_ACCEPTED:GateEditStatus.INVALID_ROLE;
    }
    private static GateEditStatus validateCurrentDraftPart(World world, GateEditSession session, GateEditDraftPart current, int x, int y, int z) {
        if (current.originatesFromOriginal()) return isOwnedLiveGatePart(world, session, x, y, z)
                ? GateEditStatus.ACTION_ACCEPTED : GateEditStatus.NOT_OWNED_GATEPART;
        return matchesAddedSource(world, current, x, y, z)
                ? GateEditStatus.ACTION_ACCEPTED : GateEditStatus.SOURCE_CHANGED;
    }
    private static GateEditStatus setHinge(World world, GateEditSession session, GateEditDraft draft, GateEditCoordinate key, GateEditDraftPart current, boolean left,int x,int y,int z) {
        if(current==null)return GateEditStatus.NOT_IN_DRAFT;
        if(current.getLeaf()!=(left?com.enovak.lotrmoremobs.siege.gate.GateLeaf.LEFT:com.enovak.lotrmoremobs.siege.gate.GateLeaf.RIGHT))return GateEditStatus.INVALID_HINGE;
        if(current.originatesFromOriginal()&&!isOwnedLiveGatePart(world,session,x,y,z))return GateEditStatus.NOT_OWNED_GATEPART;
        if(!current.originatesFromOriginal()&&!matchesAddedSource(world,current,x,y,z))return GateEditStatus.SOURCE_CHANGED;
        GateHinge hinge=new GateHinge(key.x,key.z); if(left)draft.setLeftHinge(hinge);else draft.setRightHinge(hinge);return GateEditStatus.ACTION_ACCEPTED;
    }
    private static boolean isOwnedLiveGatePart(World world,GateEditSession session,int x,int y,int z){
        if(world.getBlock(x,y,z)!=SiegeRegistry.gatePart)return false;
        SiegeGateOwnershipData.DurablePartOwner owner=GateRegistry.getDurablePartOwner(world,x,y,z);
        return owner!=null&&session.getGateUuid().equals(owner.getGateUuid())&&owner.getStructureRevision()==session.getBaseRevision()&&owner.getStatus()==SiegeGateOwnershipData.ControllerStatus.ACTIVE;
    }
    private static boolean matchesAddedSource(
            World world,
            GateEditDraftPart part,
            int x,
            int y,
            int z
    ) {
        if (world == null
                || part == null) {
            return false;
        }

        GateEditAddedSource source =
                part.getAddedSource();

        if (source == null) {
            return false;
        }

        Block block =
                world.getBlock(
                        x,
                        y,
                        z
                );

        int metadata =
                world.getBlockMetadata(
                        x,
                        y,
                        z
                );

        if (metadata
                != source.getMetadata()
                || !source.getRegistryName().equals(
                GateSourceBlockValidator.getRegisteredName(
                        block
                )
        )
                || !GateSourceBlockValidator.isValid(
                world,
                x,
                y,
                z
        )) {

            return false;
        }

        TileEntity currentTileEntity =
                world.getTileEntity(
                        x,
                        y,
                        z
                );

        try {
            boolean requiresTileEntity =
                    block != null
                            && block.hasTileEntity(
                            metadata
                    );

            if (source.hasSourceTileEntityNbt()) {
                /*
                 * Do not compare the complete live NBT here. A legitimate TE may
                 * update transient fields while the edit GUI is open. We only
                 * require that the same block/meta still has a real TE.
                 */
                return currentTileEntity != null;
            }

            /*
             * If we captured no TE, the source must still genuinely be a
             * non-TE source.
             */
            return !requiresTileEntity
                    && currentTileEntity == null;

        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static EntityPlayerMP findPlayer(
            World world,
            UUID playerUuid
    ) {
        if (world == null || playerUuid == null) {
            return null;
        }

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

    private static boolean withinHardEnvelope(GateEditDraft draft,int addX,int addY,int addZ){int minX=addX,maxX=addX,minY=addY,maxY=addY,minZ=addZ,maxZ=addZ;for(GateEditDraftPart p:draft.getParts()){minX=Math.min(minX,p.getRelativeX());maxX=Math.max(maxX,p.getRelativeX());minY=Math.min(minY,p.getRelativeY());maxY=Math.max(maxY,p.getRelativeY());minZ=Math.min(minZ,p.getRelativeZ());maxZ=Math.max(maxZ,p.getRelativeZ());}return maxY-minY+1<=GateStructureValidator.MAX_GATE_HEIGHT&&((maxX-minX+1<=GateStructureValidator.MAX_GATE_WIDTH&&maxZ-minZ+1<=GateStructureValidator.MAX_GATE_THICKNESS)||(maxZ-minZ+1<=GateStructureValidator.MAX_GATE_WIDTH&&maxX-minX+1<=GateStructureValidator.MAX_GATE_THICKNESS));}
    private static TileEntitySiegeGate getExactGate(EntityPlayerMP player, GateInspectionSession inspection) {
        if (player == null || inspection == null || player.isDead || player.worldObj == null || player.worldObj.isRemote || player.worldObj.provider.dimensionId != inspection.getDimensionId()) return null;
        World world = player.worldObj;
        if (!world.blockExists(inspection.getControllerX(), inspection.getControllerY(), inspection.getControllerZ()) || world.getBlock(inspection.getControllerX(), inspection.getControllerY(), inspection.getControllerZ()) != SiegeRegistry.gateController) return null;
        TileEntity tile = world.getTileEntity(inspection.getControllerX(), inspection.getControllerY(), inspection.getControllerZ());
        if (!(tile instanceof TileEntitySiegeGate) || player.getDistanceSq(inspection.getControllerX()+0.5D, inspection.getControllerY()+0.5D, inspection.getControllerZ()+0.5D) > MAX_DISTANCE_SQ) return null;
        return (TileEntitySiegeGate)tile;
    }
    public static final class Result { private final GateEditStatus status; private final GateEditSession session; private Result(GateEditStatus s, GateEditSession e){status=s;session=e;} static Result opened(GateEditSession e){return new Result(GateEditStatus.OPENED,e);} static Result refused(GateEditStatus s){return new Result(s,null);} public GateEditStatus getStatus(){return status;} public GateEditSession getSession(){return session;} }
    public static final class ActionResult { private final GateEditStatus status; private final GateEditSession session; private ActionResult(GateEditStatus status,GateEditSession session){this.status=status;this.session=session;} static ActionResult accepted(GateEditSession session){return new ActionResult(GateEditStatus.ACTION_ACCEPTED,session);} static ActionResult refused(GateEditStatus status,GateEditSession session){return new ActionResult(status,session);} public GateEditStatus getStatus(){return status;} public GateEditSession getSession(){return session;} }
    public static final class PreflightRequest { private final GateEditStatus status; private final GateEditSession session; private final GateEditPreflightResult result; private PreflightRequest(GateEditStatus status,GateEditSession session,GateEditPreflightResult result){this.status=status;this.session=session;this.result=result;} static PreflightRequest accepted(GateEditSession session,GateEditPreflightResult result){return new PreflightRequest(GateEditStatus.ACTION_ACCEPTED,session,result);} static PreflightRequest refused(GateEditStatus status){return new PreflightRequest(status,null,null);} public GateEditStatus getStatus(){return status;} public GateEditSession getSession(){return session;} public GateEditPreflightResult getResult(){return result;} }
    public static final class EditCommitAdmissionResult {
        public enum State { PREPARED, INVALID_SESSION, STALE_DRAFT, NOT_READY, MUTATION_IN_PROGRESS, OWNERSHIP_CONFLICT, RESERVATION_CONFLICT, REVISION_OVERFLOW, CAPACITY_REJECTED, INTERNAL_REJECTED }
        private final State state; private final UUID jobUuid, gateUuid; private final int baseRevision, targetRevision;
        private EditCommitAdmissionResult(State state, UUID jobUuid, UUID gateUuid, int baseRevision, int targetRevision){this.state=state;this.jobUuid=jobUuid;this.gateUuid=gateUuid;this.baseRevision=baseRevision;this.targetRevision=targetRevision;}
        static EditCommitAdmissionResult rejected(State state){return new EditCommitAdmissionResult(state,null,null,0,0);}
        static EditCommitAdmissionResult fromDurable(SiegeGateOwnershipData.EditCommitPrepareResult durable){if(durable==null)return rejected(State.INTERNAL_REJECTED); switch(durable.getState()){case PREPARED:return new EditCommitAdmissionResult(State.PREPARED,durable.getJobUuid(),durable.getGateUuid(),durable.getBaseRevision(),durable.getTargetRevision());case MUTATION_IN_PROGRESS:return rejected(State.MUTATION_IN_PROGRESS);case OWNERSHIP_CONFLICT:return rejected(State.OWNERSHIP_CONFLICT);case RESERVATION_CONFLICT:return rejected(State.RESERVATION_CONFLICT);case CAPACITY_REJECTED:return rejected(State.CAPACITY_REJECTED);default:return rejected(State.INTERNAL_REJECTED);}}
        public State getState(){return state;} public UUID getJobUuid(){return jobUuid;} public UUID getGateUuid(){return gateUuid;} public int getBaseRevision(){return baseRevision;} public int getTargetRevision(){return targetRevision;}
    }
    public enum PendingCommitRequestResult { QUEUED, DUPLICATE, REJECTED }
    private static final class PendingEditCommitRequest {
        private final EntityPlayerMP player; private final UUID token; private final long expectedDraftSequence;
        private PendingEditCommitRequest(EntityPlayerMP player, UUID token, long expectedDraftSequence){this.player=player;this.token=token;this.expectedDraftSequence=expectedDraftSequence;}
    }
}
