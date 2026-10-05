package kome.common.data;

/**
 * Durable proof that this implementation observed a newly-created defensive conflict and
 * deliberately deferred mobilization until its recognized ruler becomes inactive.
 */
public final class KOMEEmergencyDefenseObservation {
    public final String conflictId;
    public final String tileId;
    public final String nativeFaction;
    public final long observedAtMillis;
    public final String reason;

    public KOMEEmergencyDefenseObservation(String conflictId, String tileId,
            String nativeFaction, long observedAtMillis, String reason) {
        if (conflictId == null || !conflictId.matches("CF[1-9][0-9]*"))
            throw new IllegalArgumentException("Invalid deferred Emergency Defense conflict ID.");
        String tile = KOMEConquestTile.normalizeId(tileId);
        String faction = KOMEAlliance.normalizeFactionKey(nativeFaction);
        if (!KOMEConquestTile.isCanonicalTileId(tile) || faction.length() == 0
                || observedAtMillis < 0L)
            throw new IllegalArgumentException("Invalid deferred Emergency Defense authority.");
        this.conflictId = conflictId;
        this.tileId = tile;
        this.nativeFaction = faction;
        this.observedAtMillis = observedAtMillis;
        this.reason = reason == null ? "" : reason;
    }
}
