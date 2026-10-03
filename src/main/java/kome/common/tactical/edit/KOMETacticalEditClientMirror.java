package kome.common.tactical.edit;

import java.util.UUID;

/** Client-thread mirror only; old sessions, out-of-order replies and closed-session replays are rejected. */
public final class KOMETacticalEditClientMirror {
    private KOMETacticalEditSnapshot current;
    private long lastPublication, lastGeneration;
    private UUID lastToken;
    private boolean closed;
    public boolean accept(UUID localPlayerId, int dimensionId, KOMETacticalEditSnapshot snapshot) {
        if (snapshot == null || !snapshot.getPlayerId().equals(localPlayerId) || snapshot.getScope().getDimensionId() != dimensionId
                || snapshot.getPublicationSequence() <= lastPublication || snapshot.getGeneration() < lastGeneration) return false;
        if (snapshot.getGeneration() == lastGeneration && (closed || !snapshot.getToken().equals(lastToken)
                || (current != null && (!current.getScope().equals(snapshot.getScope())
                    || snapshot.getDraftSequence() < current.getDraftSequence()
                    || snapshot.getBaseRevision() != current.getBaseRevision()
                    || snapshot.getBaseObjectRevision() != current.getBaseObjectRevision()
                    || snapshot.getCurrentRevision() < current.getCurrentRevision())))) return false;
        lastPublication = snapshot.getPublicationSequence(); lastGeneration = snapshot.getGeneration(); lastToken = snapshot.getToken();
        closed = snapshot.isClosed(); current = snapshot; return true;
    }
    /** Retain the final immutable draft/result for later UI messages; it cannot authorize more requests. */
    public KOMETacticalEditSnapshot getSnapshot() { return current; }
    public boolean hasActiveSession() { return current != null && !closed; }
    /** Dimension/world transitions discard UI data but retain replay protection within this connection. */
    public void clearScope() { current = null; closed = true; }
    public void reset() { current = null; lastToken = null; lastPublication = 0L; lastGeneration = 0L; closed = false; }
}
