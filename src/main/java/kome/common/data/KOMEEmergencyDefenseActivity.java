package kome.common.data;

/** Persisted military-recruitment observation for one native faction. */
public final class KOMEEmergencyDefenseActivity {
    public static final long UNKNOWN_TIMESTAMP = -1L;
    public static final String UNKNOWN_HISTORY_ANCHOR = "UNKNOWN_HISTORY_ANCHOR";
    public static final String PLAYER_COMBAT_HIRE = "PLAYER_COMBAT_HIRE";

    public final String factionId;
    public final long observationStartedAtMillis;
    public final long lastQualifyingHireAtMillis;
    public final long updatedAtMillis;
    public final String updateSource;

    public KOMEEmergencyDefenseActivity(String factionId, long observationStartedAtMillis,
            long lastQualifyingHireAtMillis, long updatedAtMillis, String updateSource) {
        this.factionId = requireFaction(factionId);
        if (observationStartedAtMillis < 0L)
            throw new IllegalArgumentException("Emergency-defense observation start must be nonnegative.");
        if (lastQualifyingHireAtMillis < UNKNOWN_TIMESTAMP)
            throw new IllegalArgumentException("Emergency-defense hire timestamp is invalid.");
        if (lastQualifyingHireAtMillis >= 0L
                && lastQualifyingHireAtMillis < observationStartedAtMillis)
            throw new IllegalArgumentException("Qualifying hire predates the observation window.");
        if (updatedAtMillis < observationStartedAtMillis
                || lastQualifyingHireAtMillis >= 0L
                    && updatedAtMillis < lastQualifyingHireAtMillis)
            throw new IllegalArgumentException("Emergency-defense activity timestamps are inconsistent.");
        String source = updateSource == null ? "" : updateSource.trim();
        if (source.length() == 0 || source.length() > 64)
            throw new IllegalArgumentException("Emergency-defense activity source is required.");
        if (lastQualifyingHireAtMillis < 0L && !UNKNOWN_HISTORY_ANCHOR.equals(source)
                || lastQualifyingHireAtMillis >= 0L && !PLAYER_COMBAT_HIRE.equals(source))
            throw new IllegalArgumentException("Emergency-defense timestamp and source disagree.");
        this.observationStartedAtMillis = observationStartedAtMillis;
        this.lastQualifyingHireAtMillis = lastQualifyingHireAtMillis;
        this.updatedAtMillis = updatedAtMillis;
        this.updateSource = source;
    }

    public boolean hasKnownQualifyingHire() {
        return lastQualifyingHireAtMillis >= 0L;
    }

    public long inactivityAnchorMillis() {
        return hasKnownQualifyingHire() ? lastQualifyingHireAtMillis
            : observationStartedAtMillis;
    }

    public static KOMEEmergencyDefenseActivity unknownHistory(String faction, long nowMillis) {
        return new KOMEEmergencyDefenseActivity(faction, nowMillis, UNKNOWN_TIMESTAMP,
            nowMillis, UNKNOWN_HISTORY_ANCHOR);
    }

    public KOMEEmergencyDefenseActivity withQualifyingHire(long nowMillis) {
        if (nowMillis < observationStartedAtMillis)
            throw new IllegalArgumentException("Qualifying hire predates the observation window.");
        return new KOMEEmergencyDefenseActivity(factionId, observationStartedAtMillis,
            nowMillis, nowMillis, PLAYER_COMBAT_HIRE);
    }

    private static String requireFaction(String value) {
        String faction = KOMEAlliance.normalizeFactionKey(value);
        if (faction.length() == 0)
            throw new IllegalArgumentException("Canonical native faction is required.");
        return faction;
    }
}
