package kome.common.tactical.edit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kome.common.data.KOMETacticalEditService;
import kome.common.data.KOMEWorldData;
import kome.common.tactical.KOMETacticalConfiguration;

/** Server-thread-owned sessions. One draft per player; a conflicting Open requires explicit Cancel first. */
public final class KOMETacticalEditSessionManager {
    public static final long SESSION_TIMEOUT_TICKS = 20L * 60L * 5L;
    public static final int MAX_SESSIONS = 128;
    public enum Status {
        OPENED, UPDATED, VALIDATED, REFRESHED, SAVED, NO_CHANGE, CANCELLED, EXPIRED,
        DENIED, WRONG_DIMENSION, INVALID_SESSION, INVALID_DRAFT, SESSION_ACTIVE, LIMIT_REACHED,
        STALE_SEQUENCE, STALE_STORE, STALE_OBJECT, REJECTED, COMMIT_FAILED, RATE_LIMITED
    }
    public interface Actor {
        UUID getPlayerId();
        int getDimensionId();
        boolean isAuthorized();
        boolean isConnected();
    }
    private final Thread serverThread;
    private final long timeoutTicks;
    private final int maxSessions;
    private final Map<UUID, Session> sessions = new LinkedHashMap<UUID, Session>();
    private long tick, generation, publicationSequence;

    /** Construct only on the authoritative server tick, never in a Netty handler. */
    public KOMETacticalEditSessionManager() { this(SESSION_TIMEOUT_TICKS, MAX_SESSIONS); }
    public KOMETacticalEditSessionManager(long timeoutTicks, int maxSessions) {
        if (timeoutTicks <= 0 || maxSessions <= 0 || maxSessions > MAX_SESSIONS) throw new IllegalArgumentException("Invalid session bounds.");
        serverThread = Thread.currentThread(); this.timeoutTicks = timeoutTicks; this.maxSessions = maxSessions;
    }
    private void checkThread() {
        if (Thread.currentThread() != serverThread) throw new IllegalStateException("Tactical editor must run on the server thread.");
    }
    public Result handle(Actor actor, KOMEWorldData data, KOMETacticalEditRequest request) {
        checkThread();
        if (actor == null || actor.getPlayerId() == null || !actor.isConnected() || !actor.isAuthorized()) return result(Status.DENIED, null, false);
        if (data == null || request == null) return result(Status.REJECTED, null, false);
        if (actor.getDimensionId() != request.getScope().getDimensionId()) return result(Status.WRONG_DIMENSION, null, false);
        Session session = sessions.get(actor.getPlayerId());
        if (session != null && tick >= session.expiresAtTick) {
            sessions.remove(actor.getPlayerId());
            if (request.getAction() != KOMETacticalEditRequest.Action.OPEN) return result(Status.EXPIRED, session, true);
            session = null;
        }
        if (request.getAction() == KOMETacticalEditRequest.Action.OPEN) {
            if (session != null) {
                if (!session.scope.equals(request.getScope()) || session.data != data) return result(Status.SESSION_ACTIVE, session, false);
                refresh(session); return result(Status.OPENED, session, false);
            }
            if (sessions.size() >= maxSessions || generation == Long.MAX_VALUE) return result(Status.LIMIT_REACHED, null, false);
            try {
                synchronized (data) {
                    KOMETacticalConfiguration configuration = data.getTacticalConfigurationSnapshot();
                    KOMETacticalEditDraft draft = KOMETacticalEditService.current(configuration, request.getScope());
                    KOMETacticalEditWire.encodeDraft(draft);
                    session = new Session(actor.getPlayerId(), UUID.randomUUID(), request.getScope(), data, ++generation,
                        configuration.getRevision(), draft, request.getScope().getComplexId() == null ? Collections.emptyList()
                            : configuration.listAssignedBuildIds(request.getScope().getComplexId()));
                    refresh(session);
                    sessions.put(actor.getPlayerId(), session);
                    return result(Status.OPENED, session, false);
                }
            } catch (RuntimeException invalid) { return result(Status.INVALID_DRAFT, null, false); }
        }
        if (session == null || session.data != data || !session.token.equals(request.getToken())
                || !session.scope.equals(request.getScope())) return result(Status.INVALID_SESSION, null, false);
        if (session.sequence != request.getExpectedSequence()) return result(Status.STALE_SEQUENCE, session, false);
        refresh(session);
        switch (request.getAction()) {
            case UPDATE:
                try {
                    if (session.sequence == Long.MAX_VALUE) return result(Status.LIMIT_REACHED, session, false);
                    KOMETacticalEditDraft draft = KOMETacticalEditWire.decodeDraft(request.getPayload());
                    draft.requireScope(session.scope);
                    if (draft.getObjectRevision() != session.baseObjectRevision) return result(Status.STALE_OBJECT, session, false);
                    session.draft = draft; ++session.sequence; session.preflight = null;
                    return result(Status.UPDATED, session, false);
                } catch (RuntimeException invalid) { return result(Status.INVALID_DRAFT, session, false); }
            case PREFLIGHT:
                session.preflight = KOMETacticalEditService.preflight(data, session.scope, session.draft,
                    session.baseRevision, session.baseObjectRevision);
                return result(Status.VALIDATED, session, false);
            case SAVE:
                KOMETacticalEditService.Result saved = KOMETacticalEditService.save(data, session.scope, session.draft,
                    session.baseRevision, session.baseObjectRevision);
                session.preflight = saved.getPreflight();
                if (saved.getStatus() == KOMETacticalEditService.Status.CHANGED || saved.getStatus() == KOMETacticalEditService.Status.NO_CHANGE) {
                    sessions.remove(actor.getPlayerId());
                    return result(saved.getStatus() == KOMETacticalEditService.Status.CHANGED ? Status.SAVED : Status.NO_CHANGE, session, true);
                }
                return result(Status.valueOf(saved.getStatus().name()), session, false);
            case CANCEL:
                sessions.remove(actor.getPlayerId()); return result(Status.CANCELLED, session, true);
            case REFRESH:
                // This refresh republishes the draft; rebasing/discarding it is an explicit later UI decision.
                return result(Status.REFRESHED, session, false);
            default: return result(Status.REJECTED, session, false);
        }
    }

    /** Called once per authoritative server tick; expiry never publishes a persistent change. */
    public List<Result> tick() {
        checkThread(); if (tick < Long.MAX_VALUE) ++tick;
        List<Result> expired = new ArrayList<Result>();
        Iterator<Session> iterator = sessions.values().iterator();
        while (iterator.hasNext()) {
            Session session = iterator.next();
            if (tick >= session.expiresAtTick) { iterator.remove(); expired.add(result(Status.EXPIRED, session, true)); }
        }
        return Collections.unmodifiableList(expired);
    }
    public Result closePlayer(UUID playerId) {
        checkThread(); return result(Status.CANCELLED, sessions.remove(playerId), true);
    }
    public int getSessionCount() { checkThread(); return sessions.size(); }
    private void refresh(Session session) { session.expiresAtTick = tick > Long.MAX_VALUE - timeoutTicks ? Long.MAX_VALUE : tick + timeoutTicks; }
    private Result result(Status status, Session session, boolean closed) {
        KOMETacticalEditSnapshot snapshot = session == null ? null : new KOMETacticalEditSnapshot(session.playerId, session.token,
            session.scope, session.generation, ++publicationSequence, session.baseRevision, session.baseObjectRevision,
            session.sequence, session.data.getTacticalConfigurationSnapshot().getRevision(), closed, session.draft, session.preflight,
            session.assignedBuildIds, session.assignedBuildIds.size());
        return new Result(status, snapshot);
    }
    private static final class Session {
        final UUID playerId, token;
        final KOMETacticalEditScope scope;
        final KOMEWorldData data;
        final long generation, baseRevision, baseObjectRevision;
        long sequence, expiresAtTick;
        KOMETacticalEditDraft draft;
        KOMETacticalEditPreflight preflight;
        final List<String> assignedBuildIds;
        Session(UUID playerId, UUID token, KOMETacticalEditScope scope, KOMEWorldData data, long generation,
                long baseRevision, KOMETacticalEditDraft draft, List<String> assignedBuildIds) {
            this.playerId = playerId; this.token = token; this.scope = scope; this.data = data; this.generation = generation;
            this.baseRevision = baseRevision; this.baseObjectRevision = draft.getObjectRevision(); this.draft = draft;
            this.assignedBuildIds = assignedBuildIds;
        }
    }
    public static final class Result {
        private final Status status;
        private final KOMETacticalEditSnapshot snapshot;
        private Result(Status status, KOMETacticalEditSnapshot snapshot) { this.status = status; this.snapshot = snapshot; }
        public Status getStatus() { return status; }
        public KOMETacticalEditSnapshot getSnapshot() { return snapshot; }
    }
}
