package kome.common.data;

import kome.common.KOMEReflection;
import kome.common.config.KOMEConfigRegistry;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
        public final boolean mobilized;
        /** Compatibility alias for callers written before mobilization authority existed. */
        public final boolean active;
        public final long eligibleSinceMillis;
        public final String reason;

        private Assessment(String faction, boolean king,
                KOMEEmergencyDefenseActivity activity, long threshold,
                boolean inactive, List<String> conflicts, boolean eligible,
                boolean mobilized, long eligibleSince, String reason) {
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
            this.mobilized = mobilized;
            active = mobilized;
            eligibleSinceMillis = eligibleSince;
            this.reason = reason;
        }
    }

    private KOMEEmergencyDefenseService() { }

    /** Prospective legal-arrival gate; the caller supplies the authoritative attack event. */
    public boolean eligibleForAuthoritativeAttack(KOMEWorldData data, String factionId,
            long nowMillis) {
        if (data == null || nowMillis < 0L) return false;
        String faction = KOMEAlliance.normalizeFactionKey(factionId);
        if (faction.length() == 0 || KOMEAlliance.findLotrFaction(faction) == null)
            return false;
        if (!data.hasFactionKing(faction)) return true;
        KOMEEmergencyDefenseActivity activity = data.emergencyDefenseActivities.get(faction);
        return activity != null && nowMillis >= saturatedAdd(
            activity.inactivityAnchorMillis(), configuredInactivityThresholdMillis());
    }

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
        boolean mobilized = false;
        for (KOMEEmergencyDefenseCommitment commitment
                : data.emergencyDefenseCommitments.values())
            if (faction.equals(commitment.nativeFaction)
                    && commitment.state != KOMEEmergencyDefenseCommitment.State.DEMOBILIZED) {
                mobilized = true;
                break;
            }
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
            eligible, mobilized, eligibleSince, reason);
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
        KOMEEmergencyDefensePersistence.write(data.emergencyDefenseActivities,
            data.emergencyDefenseCommitments, data.emergencyDefenseObservations);
    }

    public List<String> inspectionLines(KOMEWorldData data, String faction, long nowMillis) {
        return inspectionLines(data, faction, nowMillis, null);
    }

    public List<String> inspectionLines(KOMEWorldData data, String faction, long nowMillis,
            World contextWorld) {
        Assessment state = assess(data, faction, nowMillis);
        List<String> lines = new ArrayList<String>();
        Map<UUID, Entity> loaded = loadedDefenders(data, state.nativeFaction, contextWorld);
        lines.add("Emergency Defense " + KOMEAlliance.displayFactionName(state.nativeFaction)
            + ": eligible=" + state.eligible + ", mobilized=" + state.mobilized);
        lines.add("Ruler: " + (state.recognizedKing ? "recognized" : "none")
            + "; last qualifying combat hire="
            + (state.knownQualifyingHire ? Long.toString(state.lastQualifyingHireAtMillis) : "UNKNOWN")
            + "; observation start="
            + (state.observationStartedAtMillis < 0L ? "NONE"
                : Long.toString(state.observationStartedAtMillis)));
        lines.add("Inactivity threshold=" + state.inactivityThresholdMillis
            + "ms; satisfied=" + state.inactivitySatisfied);
        lines.add("Qualifying defensive conflicts=" + state.qualifyingConflictIds);
        List<String> deferred = new ArrayList<String>();
        for (KOMEEmergencyDefenseObservation observation
                : data.emergencyDefenseObservations.values())
            if (state.nativeFaction.equals(observation.nativeFaction))
                deferred.add(observation.conflictId + "@" + observation.tileId);
        Collections.sort(deferred);
        lines.add("Deferred eligibility observations=" + deferred);
        for (KOMEEmergencyDefenseCommitment commitment
                : data.emergencyDefenseCommitments.values()) {
            if (!state.nativeFaction.equals(commitment.nativeFaction)) continue;
            lines.add("Commitment " + commitment.conflictId + " tile="
                + commitment.tileId + " state=" + commitment.state + " share="
                + commitment.attackedTileProductionRate + "/"
                + commitment.totalFactionProductionRate + " basisCenti="
                + commitment.availablePopulationBasisCenti + " unitCost="
                + commitment.defenderPopulationCost + " units="
                + commitment.calculatedUnitCount + " committedCenti="
                + commitment.populationCommittedCenti + " pending="
                + commitment.count(KOMEEmergencyDefenseCommitment.Disposition.PENDING)
                + " active=" + commitment.count(
                    KOMEEmergencyDefenseCommitment.Disposition.ACTIVE)
                + " dead=" + commitment.count(
                    KOMEEmergencyDefenseCommitment.Disposition.DEAD)
                + " refundableCenti=" + commitment.refundableCenti());
            if (commitment.diagnostic.length() > 0)
                lines.add("  diagnostic=" + commitment.diagnostic);
            for (KOMEEmergencyDefenseCommitment.Defender defender
                    : commitment.defenders.values()) {
                Entity entity = loaded.get(defender.entityUuid);
                StringBuilder detail = new StringBuilder("  defender intent=")
                    .append(defender.intentId).append(" uuid=")
                    .append(defender.entityUuid).append(" disposition=")
                    .append(defender.disposition).append(" refunded=")
                    .append(defender.populationRefunded).append(" loaded=")
                    .append(entity != null && !entity.isDead);
                if (entity != null && !entity.isDead) {
                    detail.append(" dimension=").append(entity.dimension)
                        .append(" position=").append(String.format(Locale.ROOT,
                            "%.1f,%.1f,%.1f", entity.posX, entity.posY, entity.posZ));
                    if (entity instanceof lotr.common.entity.npc.LOTREntityNPC) {
                        KOMEEmergencyDefenseEntityMarker.Marker marker =
                            KOMEEmergencyDefenseEntityMarker.read(
                                (lotr.common.entity.npc.LOTREntityNPC) entity);
                        if (marker != null && marker.hasHome)
                            detail.append(" home=").append(marker.homeX).append(',')
                                .append(marker.homeY).append(',').append(marker.homeZ)
                                .append(" radius=").append(marker.homeRadius);
                    }
                }
                if (defender.diagnostic.length() > 0)
                    detail.append(" diagnostic=").append(defender.diagnostic);
                lines.add(detail.toString());
            }
        }
        lines.add("Reason: " + state.reason);
        return Collections.unmodifiableList(lines);
    }

    private static Map<UUID, Entity> loadedDefenders(KOMEWorldData data, String faction,
            World contextWorld) {
        Set<UUID> sought = new HashSet<UUID>();
        for (KOMEEmergencyDefenseCommitment commitment
                : data.emergencyDefenseCommitments.values()) {
            if (!faction.equals(commitment.nativeFaction)) continue;
            for (KOMEEmergencyDefenseCommitment.Defender defender
                    : commitment.defenders.values()) sought.add(defender.entityUuid);
        }
        Map<UUID, Entity> found = new HashMap<UUID, Entity>();
        Set<World> worlds = Collections.newSetFromMap(new IdentityHashMap<World, Boolean>());
        if (contextWorld != null) worlds.add(contextWorld);
        MinecraftServer server = MinecraftServer.getServer();
        if (server != null && server.worldServers != null)
            for (WorldServer world : server.worldServers) if (world != null) worlds.add(world);
        for (World world : worlds) {
            for (Object raw : world.loadedEntityList) {
                if (!(raw instanceof Entity)) continue;
                Entity entity = (Entity) raw;
                UUID id = KOMEReflection.getEntityUUID(entity);
                if (sought.contains(id) && !found.containsKey(id)) found.put(id, entity);
            }
        }
        return found;
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

    boolean hasNativeDefensiveConflictAuthority(KOMEWorldData data, String conflictId,
            String tileId, String defenderId) {
        if (data == null) return false;
        String defender = KOMEAlliance.normalizeFactionKey(defenderId);
        KOMEConflictRecord record = data.getConflictService().get(tileId);
        if (record == null || !record.isActive()
                || !record.getConflictId().equals(conflictId)) return false;
        KOMEConquestTile tile = data.getConquestTileIfPresent(record.getTileId());
        if (tile == null || !defender.equals(KOMEAlliance.normalizeFactionKey(
                tile.projectRulingFaction()))) return false;
        KOMEConflictRecord.ValidatedCommitmentEvent creation = creationEvent(record);
        return creation != null && defender.equals(creation.authorityFactionId)
            && record.getFactionParticipation().containsKey(defender);
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
