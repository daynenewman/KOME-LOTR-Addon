package kome.common.data;

import kome.common.config.KOMEConfigRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static kome.common.data.KOMEConflictContracts.Hostility;

/** Derived Emergency Defense readiness over persisted recruitment activity and ConflictRecords. */
public final class KOMEEmergencyDefenseService {
    public static final KOMEEmergencyDefenseService INSTANCE =
        new KOMEEmergencyDefenseService();
    public static final long REAL_DAY_MILLIS = 24L * 60L * 60L * 1000L;

    public enum RecruitmentSource {
        PLAYER_COMBAT_HIRE(true),
        SYSTEM_EMERGENCY_RESERVE(false);

        final boolean countsAsRulerActivity;
        RecruitmentSource(boolean counts) { countsAsRulerActivity = counts; }
    }

    public static final class Assessment {
        public final String nativeFaction;
        public final boolean recognizedKing;
        public final boolean knownQualifyingHire;
        public final long lastQualifyingHireAtMillis;
        public final long observationStartedAtMillis;
        public final long inactivityThresholdMillis;
        public final boolean inactivitySatisfied;
        public final List<String> qualifyingConflictIds;
        public final boolean eligible;
        public final boolean active;
        public final long eligibleSinceMillis;
        public final String reason;

        private Assessment(String faction, boolean king,
                KOMEEmergencyDefenseActivity activity, long threshold,
                boolean inactive, List<String> conflicts, boolean eligible,
                long eligibleSince, String reason) {
            nativeFaction = faction;
            recognizedKing = king;
            knownQualifyingHire = activity != null && activity.hasKnownQualifyingHire();
            lastQualifyingHireAtMillis = knownQualifyingHire
                ? activity.lastQualifyingHireAtMillis : -1L;
            observationStartedAtMillis = activity == null
                ? -1L : activity.observationStartedAtMillis;
            inactivityThresholdMillis = threshold;
            inactivitySatisfied = inactive;
            qualifyingConflictIds = Collections.unmodifiableList(
                new ArrayList<String>(conflicts));
            this.eligible = eligible;
            active = eligible; // Phase 1 has authority/readiness only; no reserve troops exist yet.
            eligibleSinceMillis = eligibleSince;
            this.reason = reason;
        }
    }

    private KOMEEmergencyDefenseService() { }

    public long configuredInactivityThresholdMillis() {
        return Math.multiplyExact((long) KOMEConfigRegistry.season()
            .getEmergencyDefenseRulerInactivityDays(), REAL_DAY_MILLIS);
    }

    public Assessment assess(KOMEWorldData data, String factionId, long nowMillis) {
        if (data == null || nowMillis < 0L)
            throw new IllegalArgumentException("World data and nonnegative time are required.");
        String faction = KOMEAlliance.normalizeFactionKey(factionId);
        if (faction.length() == 0 || KOMEAlliance.findLotrFaction(faction) == null)
            throw new IllegalArgumentException("Recognized native faction is required.");
        boolean king = data.hasFactionKing(faction);
        KOMEEmergencyDefenseActivity activity = data.emergencyDefenseActivities.get(faction);
        long threshold = configuredInactivityThresholdMillis();
        boolean inactive = !king;
        long inactivityAt = -1L;
        if (king && activity != null) {
            long anchor = activity.inactivityAnchorMillis();
            inactivityAt = saturatedAdd(anchor, threshold);
            inactive = nowMillis >= inactivityAt;
        }
        List<KOMEConflictRecord> conflicts = qualifyingDefensiveConflicts(data, faction);
        List<String> ids = new ArrayList<String>();
        long firstConflictAt = Long.MAX_VALUE;
        for (KOMEConflictRecord conflict : conflicts) {
            ids.add(conflict.getConflictId());
            firstConflictAt = Math.min(firstConflictAt, conflict.getCreatedAtMillis());
        }
        Collections.sort(ids);
        boolean underAttack = !ids.isEmpty();
        boolean eligible = underAttack && (!king || inactive);
        long eligibleSince = eligible
            ? king ? Math.max(firstConflictAt, inactivityAt) : firstConflictAt : -1L;
        String reason;
        if (!underAttack) reason = "No active hostile ConflictRecord defends native-owned territory.";
        else if (!king) reason = "Kingless native faction is under an authoritative hostile attack.";
        else if (activity == null) reason = "Ruler activity history is unavailable; inactivity is not inferred.";
        else if (!inactive) reason = activity.hasKnownQualifyingHire()
            ? "A recent qualifying combat hire keeps ruler military activity current."
            : "Unknown historical activity is inside its conservative observation window.";
        else reason = "No qualifying player combat hire occurred within the configured inactivity window.";
        return new Assessment(faction, king, activity, threshold, inactive, ids,
            eligible, eligibleSince, reason);
    }

    public boolean recordRecruitmentActivity(KOMEWorldData data, String factionId,
            int populationCost, boolean combat, RecruitmentSource source, long nowMillis) {
        if (data == null || source == null || nowMillis < 0L)
            throw new IllegalArgumentException("Complete recruitment activity is required.");
        if (!source.countsAsRulerActivity || !combat || populationCost <= 0) return false;
        String faction = KOMEAlliance.normalizeFactionKey(factionId);
        if (faction.length() == 0)
            throw new IllegalArgumentException("Canonical paying faction is required.");
        KOMEEmergencyDefenseActivity current = data.emergencyDefenseActivities.get(faction);
        KOMEEmergencyDefenseActivity next = current == null
            ? new KOMEEmergencyDefenseActivity(faction, nowMillis, nowMillis, nowMillis,
                KOMEEmergencyDefenseActivity.PLAYER_COMBAT_HIRE)
            : current.withQualifyingHire(nowMillis);
        data.emergencyDefenseActivities.put(faction, next);
        data.markDirty();
        return true;
    }

    public boolean anchorUnknownHistory(KOMEWorldData data, String factionId, long nowMillis) {
        if (data == null || nowMillis < 0L)
            throw new IllegalArgumentException("World data and nonnegative time are required.");
        String faction = KOMEAlliance.normalizeFactionKey(factionId);
        if (faction.length() == 0)
            throw new IllegalArgumentException("Canonical faction is required.");
        if (data.emergencyDefenseActivities.containsKey(faction)) return false;
        data.emergencyDefenseActivities.put(faction,
            KOMEEmergencyDefenseActivity.unknownHistory(faction, nowMillis));
        data.markDirty();
        return true;
    }

    void validatePersistedAuthority(KOMEWorldData data) {
        // A missing row is conservatively UNKNOWN, never an assertion of inactivity. Production
        // Ruler assignment and the schema-7 -> schema-8 migration create anchors. Retaining
        // UNKNOWN here keeps
        // partially observed legacy/admin fixtures fail-closed without fabricating history.
        KOMEEmergencyDefensePersistence.write(data.emergencyDefenseActivities);
    }

    public List<String> inspectionLines(KOMEWorldData data, String faction, long nowMillis) {
        Assessment state = assess(data, faction, nowMillis);
        List<String> lines = new ArrayList<String>();
        lines.add("Emergency Defense " + KOMEAlliance.displayFactionName(state.nativeFaction)
            + ": eligible=" + state.eligible + ", active=" + state.active);
        lines.add("Ruler: " + (state.recognizedKing ? "recognized" : "none")
            + "; last qualifying combat hire="
            + (state.knownQualifyingHire ? Long.toString(state.lastQualifyingHireAtMillis) : "UNKNOWN")
            + "; observation start="
            + (state.observationStartedAtMillis < 0L ? "NONE"
                : Long.toString(state.observationStartedAtMillis)));
        lines.add("Inactivity threshold=" + state.inactivityThresholdMillis
            + "ms; satisfied=" + state.inactivitySatisfied);
        lines.add("Qualifying defensive conflicts=" + state.qualifyingConflictIds);
        lines.add("Reason: " + state.reason);
        return Collections.unmodifiableList(lines);
    }

    private List<KOMEConflictRecord> qualifyingDefensiveConflicts(
            KOMEWorldData data, String defender) {
        List<KOMEConflictRecord> result = new ArrayList<KOMEConflictRecord>();
        for (KOMEConflictRecord record : data.getConflictService().records().values()) {
            if (record == null || !record.isActive()) continue;
            KOMEConquestTile tile = data.getConquestTileIfPresent(record.getTileId());
            if (tile == null || !defender.equals(KOMEAlliance.normalizeFactionKey(
                    tile.projectRulingFaction()))) continue;
            KOMEConflictRecord.ValidatedCommitmentEvent creation = creationEvent(record);
            if (creation == null || !defender.equals(creation.authorityFactionId)) continue;
            if (hasLiveHostileCommitment(data, record, defender, creation)) result.add(record);
        }
        return result;
    }

    private boolean hasLiveHostileCommitment(KOMEWorldData data,
            KOMEConflictRecord record, String defender,
            KOMEConflictRecord.ValidatedCommitmentEvent creation) {
        for (KOMEConflictRecord.Commitment commitment : record.getCommitments().values()) {
            String faction = commitment.validatedEvent == null
                ? creation.originalGarrisonFactions.get(commitment.detachmentId)
                : commitment.validatedEvent.detachmentFactionId;
            if (faction == null || faction.length() == 0 || faction.equals(defender)) continue;
            if (data.getConflictService().currentHostility(data, faction, defender)
                    == Hostility.HOSTILE) return true;
        }
        return false;
    }

    private static KOMEConflictRecord.ValidatedCommitmentEvent creationEvent(
            KOMEConflictRecord record) {
        for (KOMEConflictRecord.Commitment commitment : record.getCommitments().values()) {
            if (commitment.validatedEvent != null
                    && commitment.validatedEvent.createdConflict)
                return commitment.validatedEvent;
        }
        return null;
    }

    private static long saturatedAdd(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }
}
