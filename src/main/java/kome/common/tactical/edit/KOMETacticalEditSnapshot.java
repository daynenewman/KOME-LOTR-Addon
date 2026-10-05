package kome.common.tactical.edit;

import java.util.UUID;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable S2C value, retaining a draft on stale rejection and on explicit session closure. */
public final class KOMETacticalEditSnapshot {
    private final UUID playerId, token;
    private final KOMETacticalEditScope scope;
    private final long generation, publicationSequence, baseRevision, baseObjectRevision, draftSequence, currentRevision;
    private final boolean closed;
    private final KOMETacticalEditDraft draft;
    private final KOMETacticalEditPreflight preflight;
    private final List<String> assignedBuildIds;
    private final int totalAssignedBuildCount;
    public KOMETacticalEditSnapshot(UUID playerId, UUID token, KOMETacticalEditScope scope, long generation,
            long publicationSequence, long baseRevision, long baseObjectRevision, long draftSequence,
            long currentRevision, boolean closed, KOMETacticalEditDraft draft, KOMETacticalEditPreflight preflight) {
        this(playerId, token, scope, generation, publicationSequence, baseRevision, baseObjectRevision, draftSequence,
            currentRevision, closed, draft, preflight, Collections.emptyList(), 0);
    }
    public KOMETacticalEditSnapshot(UUID playerId, UUID token, KOMETacticalEditScope scope, long generation,
            long publicationSequence, long baseRevision, long baseObjectRevision, long draftSequence,
            long currentRevision, boolean closed, KOMETacticalEditDraft draft, KOMETacticalEditPreflight preflight,
            List<String> assignedBuildIds, int totalAssignedBuildCount) {
        if (playerId == null || token == null || scope == null || draft == null || generation <= 0
                || publicationSequence <= 0 || baseRevision < 0 || baseObjectRevision < 0 || draftSequence < 0 || currentRevision < 0) {
            throw new IllegalArgumentException("Invalid editor snapshot metadata.");
        }
        draft.requireScope(scope);
        if (draft.getObjectRevision() != baseObjectRevision) throw new IllegalArgumentException("Snapshot draft revision changed.");
        this.playerId = playerId; this.token = token; this.scope = scope; this.generation = generation;
        this.publicationSequence = publicationSequence; this.baseRevision = baseRevision; this.baseObjectRevision = baseObjectRevision;
        this.draftSequence = draftSequence; this.currentRevision = currentRevision; this.closed = closed;
        this.draft = draft; this.preflight = preflight;
        if (assignedBuildIds == null || totalAssignedBuildCount < assignedBuildIds.size()
                || (scope.getComplexId() == null && totalAssignedBuildCount != 0)) throw new IllegalArgumentException("Invalid membership snapshot.");
        List<String> assigned = new ArrayList<String>(assignedBuildIds);
        Collections.sort(assigned); this.assignedBuildIds = Collections.unmodifiableList(assigned);
        this.totalAssignedBuildCount = totalAssignedBuildCount;
    }
    public UUID getPlayerId() { return playerId; }
    public UUID getToken() { return token; }
    public KOMETacticalEditScope getScope() { return scope; }
    public long getGeneration() { return generation; }
    public long getPublicationSequence() { return publicationSequence; }
    public long getBaseRevision() { return baseRevision; }
    public long getBaseObjectRevision() { return baseObjectRevision; }
    public long getDraftSequence() { return draftSequence; }
    public long getCurrentRevision() { return currentRevision; }
    public boolean isClosed() { return closed; }
    public KOMETacticalEditDraft getDraft() { return draft; }
    public KOMETacticalEditPreflight getPreflight() { return preflight; }
    /** Membership at the session's base revision, separate from a pending membership intent. */
    public List<String> getAssignedBuildIds() { return assignedBuildIds; }
    public int getTotalAssignedBuildCount() { return totalAssignedBuildCount; }
    public boolean isMembershipSummaryTruncated() { return assignedBuildIds.size() < totalAssignedBuildCount; }
}
