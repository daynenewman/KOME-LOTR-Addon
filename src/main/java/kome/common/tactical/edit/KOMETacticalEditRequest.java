package kome.common.tactical.edit;

import java.util.UUID;

/** Bounded immutable packet intent. NBT parsing and all world work wait for the server tick. */
public final class KOMETacticalEditRequest {
    public enum Action { OPEN, UPDATE, PREFLIGHT, SAVE, CANCEL, REFRESH, CREATE, DELETE, BROWSE }
    private final Action action;
    private final KOMETacticalEditScope scope;
    private final UUID token;
    private final long expectedSequence;
    private final byte[] payload;

    public KOMETacticalEditRequest(Action action, KOMETacticalEditScope scope, UUID token,
            long expectedSequence, byte[] payload) {
        if (action == null || scope == null || payload == null || expectedSequence < 0
                || payload.length > KOMETacticalEditWire.MAX_DRAFT_BYTES) throw new IllegalArgumentException("Invalid editor request.");
        boolean starts = action == Action.OPEN || action == Action.CREATE || action == Action.BROWSE;
        if (starts ? token != null || (action != Action.BROWSE && expectedSequence != 0) : token == null
                || (token.getMostSignificantBits() == 0L && token.getLeastSignificantBits() == 0L)) {
            throw new IllegalArgumentException("Invalid session token.");
        }
        if (action == Action.UPDATE ? payload.length == 0 : payload.length != 0) {
            throw new IllegalArgumentException("Unexpected editor payload.");
        }
        if ((action == Action.CREATE || action == Action.DELETE || action == Action.BROWSE)
                && scope.getType() != KOMETacticalEditScope.Type.TILE_FORCE_DEPLOYMENT_AREA) {
            throw new IllegalArgumentException("This action requires tile-area scope.");
        }
        if (action == Action.BROWSE && expectedSequence > Integer.MAX_VALUE) throw new IllegalArgumentException("Invalid page.");
        this.action = action; this.scope = scope; this.token = token;
        this.expectedSequence = expectedSequence; this.payload = payload.clone();
    }
    public Action getAction() { return action; }
    public KOMETacticalEditScope getScope() { return scope; }
    public UUID getToken() { return token; }
    public long getExpectedSequence() { return expectedSequence; }
    public byte[] getPayload() { return payload.clone(); }
    public static KOMETacticalEditRequest open(KOMETacticalEditScope scope) {
        return new KOMETacticalEditRequest(Action.OPEN, scope, null, 0L, new byte[0]);
    }
    public static KOMETacticalEditRequest create(KOMETacticalEditScope scope) {
        return new KOMETacticalEditRequest(Action.CREATE, scope, null, 0L, new byte[0]);
    }
    public static KOMETacticalEditRequest browse(String tile, int dimension, int page) {
        return new KOMETacticalEditRequest(Action.BROWSE, new KOMETacticalEditScope(
            KOMETacticalEditScope.Type.TILE_FORCE_DEPLOYMENT_AREA, tile, null, "BROWSE", dimension),
            null, page, new byte[0]);
    }
    public static KOMETacticalEditRequest action(Action action, KOMETacticalEditSnapshot snapshot) {
        return new KOMETacticalEditRequest(action, snapshot.getScope(), snapshot.getToken(), snapshot.getDraftSequence(), new byte[0]);
    }
    public static KOMETacticalEditRequest update(KOMETacticalEditSnapshot snapshot, KOMETacticalEditDraft draft) {
        return new KOMETacticalEditRequest(Action.UPDATE, snapshot.getScope(), snapshot.getToken(), snapshot.getDraftSequence(),
            KOMETacticalEditWire.encodeDraft(draft));
    }
}
