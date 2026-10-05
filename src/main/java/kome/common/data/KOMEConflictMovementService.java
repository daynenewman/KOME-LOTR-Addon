package kome.common.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.World;

import static kome.common.data.KOMEConflictContracts.*;
import static kome.common.data.KOMEConflictRecord.*;

/**
 * Narrow strategic-movement adapter for KOM-17. It never observes physical position: callers may
 * prepare a receipt only for the terminal step of an explicit KOME movement order, after which the
 * existing {@link KOMEConflictService} remains the sole ConflictRecord mutation authority.
 */
public final class KOMEConflictMovementService {
    interface EmergencyDefensePublisher {
        boolean publish(KOMEWorldData data,
            KOMEEmergencyDefenseMobilizationService.Plan plan);
    }

    private static final EmergencyDefensePublisher LIVE_EMERGENCY_DEFENSE_PUBLISHER =
        new EmergencyDefensePublisher() {
            @Override public boolean publish(KOMEWorldData data,
                    KOMEEmergencyDefenseMobilizationService.Plan plan) {
                return KOMEEmergencyDefenseMobilizationService.INSTANCE
                    .publishPlan(data, plan);
            }
        };
    private KOMEConflictMovementService() { }

    public enum PermissionCode {
        HOSTILE_ATTACK_ALLOWED, NORMAL_MOVEMENT_RULE, NOT_TERMINAL_DESTINATION,
        INVALID_TILE_OWNER, UNKNOWN_HOSTILITY, DETACHMENT_NOT_READY,
        ALREADY_COMMITTED, NO_RELEVANT_HOSTILE_PARTICIPANT
    }

    public static final class Permission {
        public final PermissionCode code;
        public final String defenderFactionId;
        public final String authorityFactionId;
        public final String reason;

        private Permission(PermissionCode code, String defender, String authority,
                String reason) {
            this.code = code;
            defenderFactionId = faction(defender);
            authorityFactionId = faction(authority);
            this.reason = clean(reason);
        }

        public boolean allowed() { return code == PermissionCode.HOSTILE_ATTACK_ALLOWED; }
    }

    public enum ArrivalCode {
        READY, NOT_HOSTILE_ATTACK, INVALID_ORDER, DETACHMENT_NOT_READY,
        DEFENSIVE_CONTEXT_INVALID, GARRISON_NOT_READY, CONFLICT_REJECTED
    }

    /** Immutable server-produced event identity consumed after the cohort is verified. */
    public static final class LegalArrivalReceipt {
        public final String movementOrderId;
        public final String detachmentId;
        public final String destinationTileId;
        public final String detachmentFactionId;
        public final long acceptedAtMillis;
        public final ValidatedConflictAuthority authority;
        public final EntryOrigin origin;
        public final boolean defensiveContext;
        public final List<GarrisonParticipantSeed> originalGarrison;
        public final ExpectedConflict expectedConflict;

        private LegalArrivalReceipt(KOMEArmyMovementOrder order, String tileId,
                String factionId, long timestamp, ValidatedConflictAuthority authority,
                EntryOrigin origin, boolean defensive,
                List<GarrisonParticipantSeed> garrison, ExpectedConflict expected) {
            movementOrderId = clean(order.id);
            detachmentId = clean(order.companyId);
            destinationTileId = KOMEConquestTile.normalizeId(tileId);
            detachmentFactionId = faction(factionId);
            acceptedAtMillis = timestamp;
            this.authority = authority;
            this.origin = origin;
            defensiveContext = defensive;
            originalGarrison = Collections.unmodifiableList(
                new ArrayList<GarrisonParticipantSeed>(garrison));
            expectedConflict = expected;
        }

        ValidatedCommitmentRequest request() {
            return new ValidatedCommitmentRequest(destinationTileId, detachmentId,
                detachmentFactionId, authority, acceptedAtMillis, origin,
                movementOrderId, defensiveContext, originalGarrison, expectedConflict);
        }
    }

    public static final class ArrivalPreparation {
        public final ArrivalCode code;
        public final LegalArrivalReceipt receipt;
        public final String reason;

        private ArrivalPreparation(ArrivalCode code, LegalArrivalReceipt receipt,
                String reason) {
            this.code = code;
            this.receipt = receipt;
            this.reason = clean(reason);
        }

        public boolean ready() { return code == ArrivalCode.READY; }
    }

    public static final class ArrivalCommitment {
        public final ArrivalCode code;
        public final KOMEConflictService.Result conflictResult;
        public final String reason;

        private ArrivalCommitment(ArrivalCode code,
                KOMEConflictService.Result result, String reason) {
            this.code = code;
            conflictResult = result;
            this.reason = clean(reason);
        }

        public boolean success() {
            return conflictResult != null && (conflictResult.code == Code.SUCCESS
                || conflictResult.code == Code.ALREADY_COMMITTED_SAME_CONFLICT);
        }
    }

    /** The exception applies only to the explicitly selected terminal destination. */
    public static Permission evaluateHostileDestination(KOMEWorldData data,
            KOMEArmyCompany company, String destinationTileId, boolean terminalDestination) {
        if (!terminalDestination)
            return permission(PermissionCode.NOT_TERMINAL_DESTINATION, "", "",
                "Hostile territory is never valid transit.");
        if (!detachmentReady(data, company))
            return permission(PermissionCode.DETACHMENT_NOT_READY, "", "",
                "Campaign Detachment authority or coherence is not ready.");
        if (isActivelyCommitted(data, company.id))
            return permission(PermissionCode.ALREADY_COMMITTED, "", "",
                "The detachment is already committed to an active conflict.");
        String tileId = KOMEConquestTile.normalizeId(destinationTileId);
        KOMEConquestTile tile = data == null ? null : data.getConquestTileIfPresent(tileId);
        String defender = tile == null ? ""
            : KOMEAlliance.normalizeFactionKey(tile.projectRulingFaction());
        if (tile == null || defender.length() == 0
                || KOMEAlliance.findLotrFaction(defender) == null)
            return permission(PermissionCode.INVALID_TILE_OWNER, defender, "",
                "Destination owner/defender authority is unavailable.");
        String incoming = KOMEAlliance.normalizeFactionKey(company.faction);
        KOMEConflictRecord existing = data.getConflictService().get(tileId);
        if (existing != null && existing.isActive()) {
            String participant = firstHostileActiveParticipant(data, existing, incoming);
            if (participant.length() > 0)
                return permission(PermissionCode.HOSTILE_ATTACK_ALLOWED, defender,
                    participant, "Live hostility to an active tile-conflict participant was proven.");
            return permission(PermissionCode.NO_RELEVANT_HOSTILE_PARTICIPANT,
                defender, "", "No live-hostile active conflict participant was found.");
        }
        Hostility hostility = data.getConflictService().currentHostility(data,
            incoming, defender);
        if (hostility == Hostility.UNKNOWN)
            return permission(PermissionCode.UNKNOWN_HOSTILITY, defender, defender,
                "Current LOTR relation to the tile defender is unavailable.");
        if (hostility != Hostility.HOSTILE)
            return permission(PermissionCode.NORMAL_MOVEMENT_RULE, defender, defender,
                "The destination defender is not hostile; normal passage rules apply.");
        return permission(PermissionCode.HOSTILE_ATTACK_ALLOWED, defender, defender,
            "Live hostility to the authoritative destination defender was proven.");
    }

    public static boolean isAuthorizedHostileTerminalStep(KOMEWorldData data,
            KOMEArmyMovementOrder order, String destinationTileId) {
        if (data == null || order == null || order.retreating) return false;
        String destination = KOMEConquestTile.normalizeId(destinationTileId);
        String finalTile = KOMEConquestTile.normalizeId(order.finalDestinationTile.length() == 0
            ? order.destinationTile : order.finalDestinationTile);
        if (!destination.equals(finalTile)
                || !destination.equals(KOMEConquestTile.normalizeId(
                    order.hostileAttackDestination))) return false;
        KOMEArmyCompany company = data.armyCompanies.get(order.companyId);
        return evaluateHostileDestination(data, company, destination, true).allowed();
    }

    public static ArrivalPreparation prepareLegalArrival(KOMEWorldData data,
            KOMEArmyMovementOrder order, String destinationTileId) {
        if (data == null || order == null || clean(order.id).length() == 0
                || clean(order.companyId).length() == 0 || order.arrivalMillis < 0L)
            return preparation(ArrivalCode.INVALID_ORDER, null,
                "Movement order identity and arrival timestamp are required.");
        long acceptedAtMillis = order.arrivalMillis;
        String destination = KOMEConquestTile.normalizeId(destinationTileId);
        String finalTile = KOMEConquestTile.normalizeId(order.finalDestinationTile.length() == 0
            ? order.destinationTile : order.finalDestinationTile);
        if (!destination.equals(finalTile)
                || !destination.equals(KOMEConquestTile.normalizeId(
                    order.hostileAttackDestination)))
            return preparation(ArrivalCode.NOT_HOSTILE_ATTACK, null,
                "Only an explicitly authorized terminal hostile arrival creates or joins conflict.");
        KOMEArmyCompany company = data.armyCompanies.get(order.companyId);
        ArrivalPreparation replay = prepareAcceptedReplay(data, order, company,
            destination, acceptedAtMillis);
        if (replay != null) return replay;
        Permission permission = evaluateHostileDestination(data, company, destination, true);
        if (!permission.allowed())
            return preparation(ArrivalCode.NOT_HOSTILE_ATTACK, null, permission.reason);

        KOMEConflictRecord existing = data.getConflictService().get(destination);
        if (existing != null && existing.isActive()) {
            EntryOrigin origin = reliefOrigin(data, existing, company.faction)
                ? EntryOrigin.RELIEF : EntryOrigin.EXTERIOR_ARRIVAL;
            LegalArrivalReceipt receipt = new LegalArrivalReceipt(order, destination,
                company.faction, acceptedAtMillis,
                ValidatedConflictAuthority.participant(permission.authorityFactionId),
                origin, false, Collections.<GarrisonParticipantSeed>emptyList(),
                ExpectedConflict.at(existing.getConflictId(), existing.getRevision()));
            return preparation(ArrivalCode.READY, receipt, "");
        }

        boolean defensive;
        try {
            defensive = hasActiveDefensiveBuild(data, destination);
        } catch (RuntimeException invalidBuild) {
            return preparation(ArrivalCode.DEFENSIVE_CONTEXT_INVALID, null,
                "Defensive build authority is malformed: " + safeMessage(invalidBuild));
        }
        List<GarrisonParticipantSeed> garrison;
        try {
            garrison = defensive
                ? originalGarrison(data, destination, permission.defenderFactionId,
                    company.id)
                : Collections.<GarrisonParticipantSeed>emptyList();
        } catch (IllegalStateException unresolved) {
            return preparation(ArrivalCode.GARRISON_NOT_READY, null,
                unresolved.getMessage());
        }
        ExpectedConflict expected = existing == null ? ExpectedConflict.absent()
            : ExpectedConflict.at(existing.getConflictId(), existing.getRevision());
        LegalArrivalReceipt receipt = new LegalArrivalReceipt(order, destination,
            company.faction, acceptedAtMillis,
            ValidatedConflictAuthority.defender(permission.defenderFactionId),
            EntryOrigin.LEGAL_ARRIVAL, defensive, garrison, expected);
        return preparation(ArrivalCode.READY, receipt, "");
    }

    /** Reconstructs only the exact persisted event after a post-commit server retry. */
    private static ArrivalPreparation prepareAcceptedReplay(KOMEWorldData data,
            KOMEArmyMovementOrder order, KOMEArmyCompany company, String destination,
            long acceptedAtMillis) {
        KOMEConflictRecord conflict = activeConflictForDetachment(data, order.companyId);
        if (conflict == null) return null;
        if (company == null || !destination.equals(conflict.getTileId())
                || !destination.equals(KOMEConquestTile.normalizeId(company.currentTile)))
            return preparation(ArrivalCode.CONFLICT_REJECTED, null,
                "The detachment is already committed outside this arrival context.");
        Commitment commitment = conflict.getCommitments().get(order.companyId);
        ValidatedCommitmentEvent event = commitment == null
            ? null : commitment.validatedEvent;
        if (event == null || !clean(order.id).equals(commitment.movementOrderId)
                || acceptedAtMillis != commitment.acceptedAtMillis
                || !faction(company.faction).equals(event.detachmentFactionId))
            return preparation(ArrivalCode.CONFLICT_REJECTED, null,
                "The persisted commitment is not an exact replay of this arrival event.");
        ValidatedConflictAuthority authority = event.authorityKind
            == ConflictAuthorityKind.VALIDATED_DEFENDER
                ? ValidatedConflictAuthority.defender(event.authorityFactionId)
                : ValidatedConflictAuthority.participant(event.authorityFactionId);
        List<GarrisonParticipantSeed> garrison =
            new ArrayList<GarrisonParticipantSeed>();
        if (event.createdConflict) {
            for (Map.Entry<String, GarrisonCohort> entry
                    : conflict.getOriginalGarrison().entrySet()) {
                String garrisonFaction = event.originalGarrisonFactions.get(entry.getKey());
                if (garrisonFaction == null)
                    return preparation(ArrivalCode.CONFLICT_REJECTED, null,
                        "Persisted garrison replay metadata is incomplete.");
                garrison.add(new GarrisonParticipantSeed(
                    new GarrisonSeed(entry.getKey(), KOMEHiredUnitClass.CAMPAIGN,
                        new ArrayList<UUID>(entry.getValue().members.keySet())),
                    garrisonFaction));
            }
        }
        LegalArrivalReceipt receipt = new LegalArrivalReceipt(order, destination,
            company.faction, acceptedAtMillis, authority, commitment.origin,
            event.defensiveContext, garrison,
            ExpectedConflict.at(conflict.getConflictId(), conflict.getRevision()));
        return preparation(ArrivalCode.READY, receipt, "");
    }

    /** Call only after verified cohort recreation and strategic destination publication. */
    public static ArrivalCommitment commitLegalArrival(KOMEWorldData data,
            LegalArrivalReceipt receipt, String actor) {
        return commitLegalArrival(data, receipt, actor, null, null);
    }

    /** Production transaction additionally initializes any conflict-created native reserve. */
    public static ArrivalCommitment commitLegalArrival(KOMEWorldData data,
            LegalArrivalReceipt receipt, String actor, World arrivalWorld) {
        return commitLegalArrival(data, receipt, actor, arrivalWorld,
            LIVE_EMERGENCY_DEFENSE_PUBLISHER);
    }

    static ArrivalCommitment commitLegalArrival(KOMEWorldData data,
            LegalArrivalReceipt receipt, String actor, World arrivalWorld,
            EmergencyDefensePublisher emergencyDefensePublisher) {
        if (data == null || receipt == null)
            return new ArrivalCommitment(ArrivalCode.INVALID_ORDER, null,
                "A server legal-arrival receipt is required.");
        Context context = new Context(receipt.acceptedAtMillis, actor,
            "Legal KOME strategic hostile arrival");
        ValidatedCommitmentRequest request = receipt.request();
        KOMEConflictRecord current = data.getConflictService().get(
            receipt.destinationTileId);
        KOMEConflictService.PersistenceSnapshot before =
            data.getConflictService().persistenceSnapshot();
        int auditSizeBefore = data.centralAudit.size();
        KOMEEmergencyDefenseMobilizationService.Plan reservePlan = null;
        boolean creating = current == null || !current.isActive();
        if (emergencyDefensePublisher != null && creating) {
            try {
                reservePlan = KOMEEmergencyDefenseMobilizationService.INSTANCE
                    .prepareForCreatedConflict(data, arrivalWorld,
                        "CF" + data.getConflictService().getNextConflictSequence(),
                        receipt.destinationTileId, receipt.authority.factionId,
                        receipt.acceptedAtMillis);
            } catch (RuntimeException notReady) {
                return new ArrivalCommitment(ArrivalCode.CONFLICT_REJECTED, null,
                    notReady.getMessage());
            }
        }
        // A server retry of the same creation receipt necessarily began with ABSENT. Rebind only
        // when the same detachment is already present; Phase 3's exact typed event comparison then
        // proves replay identity and rejects every material mismatch.
        if (current != null && current.isActive()
                && current.getCommitments().containsKey(receipt.detachmentId)) {
            request = new ValidatedCommitmentRequest(receipt.destinationTileId,
                receipt.detachmentId, receipt.detachmentFactionId,
                receipt.authority, receipt.acceptedAtMillis, receipt.origin,
                receipt.movementOrderId, receipt.defensiveContext,
                receipt.originalGarrison, ExpectedConflict.at(
                    current.getConflictId(), current.getRevision()));
        }
        KOMEConflictService.Result result = data.getConflictService()
            .acceptValidatedCommitment(data, request, context);
        boolean reservePublished = true;
        String reserveFailure = "Emergency Defense population commitment could not be published.";
        if (result.code == Code.SUCCESS && reservePlan != null && reservePlan.applicable) {
            try {
                reservePublished = emergencyDefensePublisher.publish(data, reservePlan);
            } catch (RuntimeException failure) {
                reservePublished = false;
                if (failure.getMessage() != null && failure.getMessage().trim().length() > 0)
                    reserveFailure = failure.getMessage();
            }
        }
        if (!reservePublished) {
            data.getConflictService().replaceFrom(KOMEConflictService.restore(
                before.records, before.nextConflictSequence));
            while (data.centralAudit.size() > auditSizeBefore)
                data.centralAudit.remove(data.centralAudit.size() - 1);
            return new ArrivalCommitment(ArrivalCode.CONFLICT_REJECTED, null,
                reserveFailure);
        }
        return new ArrivalCommitment(result.code == Code.SUCCESS
                || result.code == Code.ALREADY_COMMITTED_SAME_CONFLICT
                    ? ArrivalCode.READY : ArrivalCode.CONFLICT_REJECTED,
            result, result.reason);
    }

    public static void applyConflictHold(KOMEWorldData data,
            KOMEArmyMovementOrder order, KOMEConflictRecord conflict,
            long timestampMillis) {
        if (data == null || order == null || conflict == null || !conflict.isActive()
                || !conflict.getCommitments().containsKey(order.companyId))
            throw new IllegalArgumentException("Active conflict commitment is required for a route hold.");
        if (KOMEArmyMovementOrder.CONFLICT_HELD.equals(order.status)
                && conflict.getConflictId().equals(clean(order.conflictHoldId))) return;
        if (clean(order.conflictHoldId).length() > 0
                && !conflict.getConflictId().equals(clean(order.conflictHoldId)))
            throw new IllegalStateException("Movement order is held by another conflict.");
        order.status = KOMEArmyMovementOrder.CONFLICT_HELD;
        order.conflictHoldId = conflict.getConflictId();
        order.conflictHeldAtMillis = timestampMillis;
        order.nextStepAvailableMillis = 0L;
        order.nextStepDepartureMillis = 0L;
        order.arrivalMillis = 0L;
        order.spawnRetryPaused = false;
        order.pendingSpawnReason = "Held by active conflict " + conflict.getConflictId() + ".";
        KOMEArmyCompany company = data.armyCompanies.get(order.companyId);
        if (company != null) {
            company.status = KOMEArmyCompany.STATIONED;
            company.movementOrderId = order.id;
            company.updatedAtMillis = timestampMillis;
        }
        for (UUID unitId : order.units) {
            KOMEHiredUnitRecord record = data.hiredUnits.get(unitId);
            if (record != null) record.movementOrderId = order.id;
        }
        data.updateMovementHistory(order, KOMEMovementHistoryRecord.ACTIVE);
        KOMEAuditService.record(data, timestampMillis, "CONFLICT", "ROUTE_HOLD",
            actor(order), conflict.getConflictId(),
            "Committed detachment route paused by conflict",
            order.companyId + " via " + order.id + " at " + conflict.getTileId());
        data.markDirty();
    }

    public static KOMEConflictRecord activeConflictForDetachment(KOMEWorldData data,
            String detachmentId) {
        if (data == null) return null;
        String id = clean(detachmentId);
        KOMEConflictRecord found = null;
        for (KOMEConflictRecord record : data.getConflictService().records().values()) {
            if (record != null && record.isActive()
                    && record.getCommitments().containsKey(id)) {
                if (found != null && !found.getConflictId().equals(record.getConflictId()))
                    return null; // corrupt duplicate authority is never chosen arbitrarily
                found = record;
            }
        }
        return found;
    }

    public static boolean isActivelyCommitted(KOMEWorldData data, String detachmentId) {
        if (data == null) return false;
        String id = clean(detachmentId);
        for (KOMEConflictRecord record : data.getConflictService().records().values())
            if (record != null && record.isActive()
                    && record.getCommitments().containsKey(id)) return true;
        return false;
    }

    private static List<GarrisonParticipantSeed> originalGarrison(KOMEWorldData data,
            String tileId, String defenderFactionId, String incomingDetachmentId) {
        List<KOMEArmyCompany> companies = new ArrayList<KOMEArmyCompany>(
            data.armyCompanies.values());
        Collections.sort(companies, new Comparator<KOMEArmyCompany>() {
            @Override public int compare(KOMEArmyCompany left, KOMEArmyCompany right) {
                return clean(left == null ? "" : left.id).compareTo(
                    clean(right == null ? "" : right.id));
            }
        });
        List<GarrisonParticipantSeed> result = new ArrayList<GarrisonParticipantSeed>();
        for (KOMEArmyCompany company : companies) {
            if (company == null || clean(company.id).equals(clean(incomingDetachmentId))
                    || !tileId.equals(KOMEConquestTile.normalizeId(company.currentTile))
                    || !KOMEArmyCompany.STATIONED.equals(company.status)
                    || clean(company.movementOrderId).length() > 0) continue;
            String faction = KOMEAlliance.normalizeFactionKey(company.faction);
            if (KOMEAlliance.findLotrFaction(faction) == null)
                throw new IllegalStateException("Stationed Campaign Detachment "
                    + clean(company.id) + " has unresolved faction authority.");
            if (!defendsWith(data, faction, defenderFactionId)) continue;
            if (!detachmentReady(data, company))
                throw new IllegalStateException("Stationed defender/allied detachment "
                    + clean(company.id) + " is not coherent enough to snapshot.");
            result.add(new GarrisonParticipantSeed(
                new GarrisonSeed(company.id, KOMEHiredUnitClass.CAMPAIGN,
                    new ArrayList<UUID>(company.units)), faction));
        }
        return result;
    }

    private static boolean hasActiveDefensiveBuild(KOMEWorldData data, String tileId) {
        for (KOMEPlayerBuild build : KOMEBuildService.activeDefensiveBuilds(data)) {
            if (tileId.equals(KOMEConquestTile.normalizeId(build.tileId))) return true;
        }
        return false;
    }

    private static boolean detachmentReady(KOMEWorldData data,
            KOMEArmyCompany company) {
        if (data == null || company == null || data.armyCompanies.get(company.id) != company
                || company.units.isEmpty()
                || KOMEAlliance.findLotrFaction(
                    KOMEAlliance.normalizeFactionKey(company.faction)) == null
                || KOMEConquestTile.normalizeId(company.currentTile).length() == 0)
            return false;
        for (UUID unitId : company.units) {
            KOMEHiredUnitRecord record = data.hiredUnits.get(unitId);
            if (record == null || !KOMEHiredUnitClassification.isCampaignUnit(record)
                    || !clean(company.id).equals(clean(record.companyId))) return false;
        }
        return KOMECompanyCoherenceService.INSTANCE.assess(data, company).status
            != KOMECompanyCoherenceService.Status.INCOHERENT;
    }

    private static boolean defendsWith(KOMEWorldData data, String faction,
            String defender) {
        if (faction.equals(defender)) return true;
        if (KOMEAlliance.findLotrFaction(faction) == null
                || KOMEAlliance.findLotrFaction(defender) == null) return false;
        KOMEDiplomacyRelation relation = KOMEDiplomacyService.getRelation(data,
            faction, defender);
        return relation == KOMEDiplomacyRelation.FRIENDS
            || relation == KOMEDiplomacyRelation.ALLIES;
    }

    private static boolean reliefOrigin(KOMEWorldData data,
            KOMEConflictRecord record, String incomingFaction) {
        if (record.getState() != State.ENCIRCLEMENT) return false;
        String defender = creationDefender(record);
        return defender.length() > 0 && defendsWith(data,
            KOMEAlliance.normalizeFactionKey(incomingFaction), defender);
    }

    private static String creationDefender(KOMEConflictRecord record) {
        for (Commitment commitment : record.getCommitments().values()) {
            ValidatedCommitmentEvent event = commitment.validatedEvent;
            if (event != null && event.createdConflict
                    && event.authorityKind == ConflictAuthorityKind.VALIDATED_DEFENDER)
                return event.authorityFactionId;
        }
        return "";
    }

    private static String firstHostileActiveParticipant(KOMEWorldData data,
            KOMEConflictRecord conflict, String incoming) {
        List<String> factions = new ArrayList<String>();
        for (FactionParticipation participation
                : conflict.getFactionParticipation().values())
            if (participation.isActive()) factions.add(participation.factionId);
        Collections.sort(factions);
        for (String faction : factions)
            if (data.getConflictService().currentHostility(data, incoming, faction)
                    == Hostility.HOSTILE) return faction;
        return "";
    }

    private static Permission permission(PermissionCode code, String defender,
            String authority, String reason) {
        return new Permission(code, defender, authority, reason);
    }

    private static ArrivalPreparation preparation(ArrivalCode code,
            LegalArrivalReceipt receipt, String reason) {
        return new ArrivalPreparation(code, receipt, reason);
    }

    private static String actor(KOMEArmyMovementOrder order) {
        return order.owner == null ? clean(order.ownerName) : order.owner.toString();
    }

    private static String faction(String value) {
        return KOMEAlliance.normalizeFactionKey(value);
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }

    private static String safeMessage(Throwable failure) {
        String message = failure == null ? "" : failure.getMessage();
        return clean(message).length() == 0 && failure != null
            ? failure.getClass().getSimpleName() : clean(message);
    }
}
