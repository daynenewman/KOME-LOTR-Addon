package com.enovak.lotrmoremobs.siege.edit;

import com.enovak.lotrmoremobs.Main;
import com.enovak.lotrmoremobs.config.MumakilConfig;
import com.enovak.lotrmoremobs.siege.network.GateEditDraftSnapshotPacket;
import com.enovak.lotrmoremobs.siege.network.GateEditPreflightSnapshotPacket;
import com.enovak.lotrmoremobs.siege.network.GateEditSessionStatusPacket;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Bounded C2S intake for transient EDIT_EXISTING requests.
 *
 * Packet handlers enqueue only primitive/session-token intent. The existing
 * GateEditSessionManager methods still own all validation and behavior, but
 * are invoked from GateEditSessionManager.tick() on the authoritative server
 * thread before durable commit requests are processed.
 */
public final class GateEditRequestManager {

    private static final int MAX_PENDING_REQUESTS = 256;
    private static final int MAX_PENDING_PER_PLAYER = 32;
    private static final int MAX_REQUESTS_PER_TICK = 32;

    private static final Deque<PendingRequest> PENDING =
            new ArrayDeque<PendingRequest>();
    private static final Map<UUID, Integer> PENDING_PER_PLAYER =
            new HashMap<UUID, Integer>();

    private GateEditRequestManager() {
    }

    public static synchronized boolean enqueueStart(EntityPlayerMP player) {
        return enqueue(PendingRequest.start(player));
    }

    public static synchronized boolean enqueueDraft(
            EntityPlayerMP player,
            UUID token,
            GateEditDraftAction action,
            int x,
            int y,
            int z,
            boolean fillEnclosed
    ) {
        return enqueue(PendingRequest.draft(
                player,
                token,
                action,
                x,
                y,
                z,
                fillEnclosed
        ));
    }

    public static synchronized boolean enqueueCancel(
            EntityPlayerMP player,
            UUID token
    ) {
        return enqueue(PendingRequest.cancel(player, token));
    }

    public static synchronized boolean enqueuePreflight(
            EntityPlayerMP player,
            UUID token
    ) {
        return enqueue(PendingRequest.preflight(player, token));
    }

    public static synchronized boolean enqueueDraftAndPreflightRefresh(
            EntityPlayerMP player,
            UUID token
    ) {
        return enqueue(PendingRequest.refresh(player, token));
    }

    public static synchronized boolean enqueueCommit(
            EntityPlayerMP player,
            UUID token,
            long expectedDraftSequence
    ) {
        return enqueue(PendingRequest.commit(
                player,
                token,
                expectedDraftSequence
        ));
    }

    /** Called only from GateEditSessionManager.tick() on the server thread. */
    static void processServerTick() {
        for (int processed = 0;
             processed < MAX_REQUESTS_PER_TICK;
             ++processed) {
            PendingRequest request = pollPending();
            if (request == null) {
                return;
            }
            process(request);
        }
    }

    private static synchronized PendingRequest pollPending() {
        PendingRequest request = PENDING.pollFirst();
        if (request != null) {
            decrementPending(request.playerUuid);
        }
        return request;
    }

    public static synchronized void clearPlayer(UUID playerUuid) {
        if (playerUuid == null) {
            return;
        }

        Iterator<PendingRequest> iterator = PENDING.iterator();
        while (iterator.hasNext()) {
            if (playerUuid.equals(iterator.next().playerUuid)) {
                iterator.remove();
            }
        }
        PENDING_PER_PLAYER.remove(playerUuid);
    }

    public static synchronized void resetServerState() {
        PENDING.clear();
        PENDING_PER_PLAYER.clear();
    }

    private static boolean enqueue(PendingRequest request) {
        if (!MumakilConfig.enableSiegeGates || request == null) {
            return false;
        }

        if (request.isCoalescible()) {
            PendingRequest previous = PENDING.peekLast();
            if (previous != null
                    && previous.sameCoalescingKey(request)) {
                return true;
            }
        }

        Integer count = PENDING_PER_PLAYER.get(request.playerUuid);
        int playerCount = count == null ? 0 : count.intValue();
        if (PENDING.size() >= MAX_PENDING_REQUESTS
                || playerCount >= MAX_PENDING_PER_PLAYER) {
            return false;
        }

        PENDING.addLast(request);
        PENDING_PER_PLAYER.put(request.playerUuid, playerCount + 1);
        return true;
    }

    private static void process(PendingRequest request) {
        if (!MumakilConfig.enableSiegeGates) {
            return;
        }

        EntityPlayerMP player = request.player.get();
        if (player == null
                || !request.playerUuid.equals(player.getUniqueID())
                || player.playerNetServerHandler == null) {
            return;
        }

        if (request.type == RequestType.START) {
            GateEditSessionManager.Result result =
                    GateEditSessionManager.start(player);
            Main.network.sendTo(
                    new GateEditSessionStatusPacket(
                            result.getStatus(),
                            result.getSession()
                    ),
                    player
            );
            if (result.getStatus() == GateEditStatus.OPENED) {
                Main.network.sendTo(
                        new GateEditDraftSnapshotPacket(result.getSession()),
                        player
                );
                Main.network.sendTo(
                        new GateEditPreflightSnapshotPacket(
                                result.getSession(),
                                GateEditSessionManager.evaluatePreflight(
                                        player,
                                        result.getSession()
                                )
                        ),
                        player
                );
            }
            return;
        }

        if (request.type == RequestType.DRAFT) {
            GateEditSessionManager.ActionResult result =
                    GateEditSessionManager.applyDraftAction(
                            player,
                            request.token,
                            request.action,
                            request.x,
                            request.y,
                            request.z,
                            request.fillEnclosed
                    );
            if (result.getStatus() == GateEditStatus.ACTION_ACCEPTED) {
                Main.network.sendTo(
                        new GateEditDraftSnapshotPacket(result.getSession()),
                        player
                );
                Main.network.sendTo(
                        new GateEditPreflightSnapshotPacket(
                                result.getSession(),
                                GateEditSessionManager.evaluatePreflight(
                                        player,
                                        result.getSession()
                                )
                        ),
                        player
                );
            } else {
                Main.network.sendTo(
                        new GateEditSessionStatusPacket(
                                result.getStatus(),
                                null
                        ),
                        player
                );
                if (result.getSession() != null) {
                    Main.network.sendTo(
                            new GateEditDraftSnapshotPacket(result.getSession()),
                            player
                    );
                    Main.network.sendTo(
                            new GateEditPreflightSnapshotPacket(
                                    result.getSession(),
                                    GateEditSessionManager.evaluatePreflight(
                                            player,
                                            result.getSession()
                                    )
                            ),
                            player
                    );
                }
            }
            return;
        }

        if (request.type == RequestType.CANCEL) {
            Main.network.sendTo(
                    new GateEditSessionStatusPacket(
                            GateEditSessionManager.cancel(
                                    player,
                                    request.token
                            ),
                            null
                    ),
                    player
            );
            return;
        }

        if (request.type == RequestType.PREFLIGHT) {
            GateEditSessionManager.PreflightRequest result =
                    GateEditSessionManager.evaluatePreflight(
                            player,
                            request.token
                    );
            if (result.getStatus() != GateEditStatus.ACTION_ACCEPTED) {
                Main.network.sendTo(
                        new GateEditSessionStatusPacket(
                                result.getStatus(),
                                null
                        ),
                        player
                );
            } else {
                Main.network.sendTo(
                        new GateEditPreflightSnapshotPacket(
                                result.getSession(),
                                result.getResult()
                        ),
                        player
                );
            }
            return;
        }

        if (request.type == RequestType.COMMIT) {
            GateEditSessionManager.PendingCommitRequestResult result =
                    GateEditSessionManager.enqueueEditCommitRequest(
                            player,
                            request.token,
                            request.expectedDraftSequence
                    );

            if (result
                    == GateEditSessionManager.PendingCommitRequestResult.REJECTED) {
                Main.network.sendTo(
                        new com.enovak.lotrmoremobs.siege.network.GateEditCommitResultPacket(
                                GateEditSessionManager.EditCommitAdmissionResult.State.INTERNAL_REJECTED
                        ),
                        player
                );
                GateEditSessionManager.pushCurrentDraftAndPreflight(
                        player,
                        request.token
                );
            }
            return;
        }

        if (request.type == RequestType.REFRESH) {
            GateEditSessionManager.pushCurrentDraftAndPreflight(
                    player,
                    request.token
            );
        }
    }

    private static void decrementPending(UUID playerUuid) {
        Integer count = PENDING_PER_PLAYER.get(playerUuid);
        if (count == null || count.intValue() <= 1) {
            PENDING_PER_PLAYER.remove(playerUuid);
        } else {
            PENDING_PER_PLAYER.put(playerUuid, count.intValue() - 1);
        }
    }

    private enum RequestType {
        START,
        DRAFT,
        CANCEL,
        PREFLIGHT,
        REFRESH,
        COMMIT
    }

    private static final class PendingRequest {
        private final UUID playerUuid;
        private final WeakReference<EntityPlayerMP> player;
        private final RequestType type;
        private final UUID token;
        private final GateEditDraftAction action;
        private final int x;
        private final int y;
        private final int z;
        private final boolean fillEnclosed;
        private final long expectedDraftSequence;

        private PendingRequest(
                EntityPlayerMP player,
                RequestType type,
                UUID token,
                GateEditDraftAction action,
                int x,
                int y,
                int z,
                boolean fillEnclosed,
                long expectedDraftSequence
        ) {
            this.playerUuid = player == null ? null : player.getUniqueID();
            this.player = new WeakReference<EntityPlayerMP>(player);
            this.type = type;
            this.token = token;
            this.action = action;
            this.x = x;
            this.y = y;
            this.z = z;
            this.fillEnclosed = fillEnclosed;
            this.expectedDraftSequence = expectedDraftSequence;
        }

        private static PendingRequest start(EntityPlayerMP player) {
            return player == null ? null : new PendingRequest(
                    player, RequestType.START,
                    null, null, 0, 0, 0, false, -1L
            );
        }

        private static PendingRequest draft(
                EntityPlayerMP player,
                UUID token,
                GateEditDraftAction action,
                int x,
                int y,
                int z,
                boolean fillEnclosed
        ) {
            return player == null || token == null || action == null
                    ? null
                    : new PendingRequest(
                            player, RequestType.DRAFT,
                            token, action, x, y, z, fillEnclosed, -1L
                    );
        }

        private static PendingRequest cancel(
                EntityPlayerMP player,
                UUID token
        ) {
            return player == null || token == null
                    ? null
                    : new PendingRequest(
                            player, RequestType.CANCEL,
                            token, null, 0, 0, 0, false, -1L
                    );
        }

        private static PendingRequest preflight(
                EntityPlayerMP player,
                UUID token
        ) {
            return player == null || token == null
                    ? null
                    : new PendingRequest(
                            player, RequestType.PREFLIGHT,
                            token, null, 0, 0, 0, false, -1L
                    );
        }

        private static PendingRequest refresh(
                EntityPlayerMP player,
                UUID token
        ) {
            return player == null || token == null
                    ? null
                    : new PendingRequest(
                            player, RequestType.REFRESH,
                            token, null, 0, 0, 0, false, -1L
                    );
        }

        private static PendingRequest commit(
                EntityPlayerMP player,
                UUID token,
                long expectedDraftSequence
        ) {
            return player == null || token == null
                    || expectedDraftSequence < 0L
                    ? null
                    : new PendingRequest(
                            player, RequestType.COMMIT,
                            token, null, 0, 0, 0, false,
                            expectedDraftSequence
                    );
        }

        private boolean isCoalescible() {
            return type == RequestType.START
                    || type == RequestType.CANCEL
                    || type == RequestType.PREFLIGHT
                    || type == RequestType.REFRESH;
        }

        private boolean sameCoalescingKey(PendingRequest other) {
            return other != null
                    && type == other.type
                    && playerUuid != null
                    && playerUuid.equals(other.playerUuid)
                    && (token == null
                    ? other.token == null
                    : token.equals(other.token));
        }
    }
}
