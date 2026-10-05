package kome.common.data;

import java.util.Collection;
import java.util.Collections;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static kome.common.data.KOMEConflictContracts.*;
import static kome.common.data.KOMEConflictRecord.*;

/**
 * Authoritative world-backed registry/mutation boundary. Strategic adapters must authorize legal
 * gameplay requests before invoking these data commands. All mutations publish a new immutable
 * snapshot, with exact conflict-ID/revision preconditions.
 */
public final class KOMEConflictService {
    public enum EndSource { LIFECYCLE, ADMIN_FORCED }

    public static final class EndResult {
        public final Result conflictResult;
        public final int movementHoldsReleased;

        private EndResult(Result result, int holdsReleased) {
            conflictResult = result;
            movementHoldsReleased = holdsReleased;
        }

        public boolean isSuccess() {
            return conflictResult != null && conflictResult.code == Code.SUCCESS;
        }
    }
    private final Map<String, KOMEConflictRecord> records = new LinkedHashMap<String, KOMEConflictRecord>();
    private final KOMEConflictIdAllocator allocator;

    public KOMEConflictService() { this(1L); }
    public KOMEConflictService(long nextConflictSequence) { allocator = new KOMEConflictIdAllocator(nextConflictSequence); }

    public synchronized KOMEConflictRecord get(String tileId) { return records.get(KOMEConquestTile.normalizeId(tileId)); }
    public synchronized Map<String, KOMEConflictRecord> records() {
        return Collections.unmodifiableMap(new LinkedHashMap<String, KOMEConflictRecord>(records));
    }
    public synchronized long getNextConflictSequence() { return allocator.getNextSequence(); }

    /** Atomic persistence projection: registry and allocator high-water are captured together. */
    synchronized PersistenceSnapshot persistenceSnapshot() {
        return new PersistenceSnapshot(records, allocator.getNextSequence());
    }

    /** Strict detached restore; no runtime/world-dependent reference resolution occurs here. */
    static KOMEConflictService restore(Map<String, KOMEConflictRecord> restoredRecords, long nextConflictSequence) {
        required(restoredRecords, "Conflict registry");
        if (nextConflictSequence < 1L)
            throw new IllegalArgumentException("Next conflict sequence must be positive.");
        Map<String, KOMEConflictRecord> validated = new LinkedHashMap<String, KOMEConflictRecord>();
        Set<String> conflictIds = new HashSet<String>();
        Set<String> activeDetachments = new HashSet<String>();
        long highestSequence = 0L;
        for (Map.Entry<String, KOMEConflictRecord> entry : restoredRecords.entrySet()) {
            KOMEConflictRecord record = required(entry.getValue(), "Conflict record");
            String tileId = tile(entry.getKey());
            if (!tileId.equals(entry.getKey()) || !tileId.equals(record.getTileId()))
                throw new IllegalArgumentException("Conflict registry tile key is noncanonical or mismatched.");
            if (validated.put(tileId, record) != null)
                throw new IllegalArgumentException("Duplicate conflict tile authority: " + tileId);
            if (!conflictIds.add(record.getConflictId()))
                throw new IllegalArgumentException("Duplicate conflict identity: " + record.getConflictId());
            highestSequence = Math.max(highestSequence, KOMEConflictIdAllocator.sequenceOf(record.getConflictId()));
            if (record.isActive()) {
                for (String detachmentId : record.getCommitments().keySet()) {
                    if (!activeDetachments.add(detachmentId))
                        throw new IllegalArgumentException("Detachment is committed to multiple active conflicts: " + detachmentId);
                }
            }
        }
        if (nextConflictSequence <= highestSequence)
            throw new IllegalArgumentException("Next conflict sequence does not exceed persisted conflict identities.");
        KOMEConflictService service = new KOMEConflictService(nextConflictSequence);
        service.records.putAll(validated);
        return service;
    }

    /** Candidate publication preserves this service object's identity for existing readers. */
    synchronized void replaceFrom(KOMEConflictService source) {
        PersistenceSnapshot snapshot = required(source, "Conflict service").persistenceSnapshot();
        KOMEConflictService validated = restore(snapshot.records, snapshot.nextConflictSequence);
        records.clear();
        records.putAll(validated.records);
        allocator.setNextSequence(validated.allocator.getNextSequence());
    }

    static final class PersistenceSnapshot {
        final Map<String, KOMEConflictRecord> records;
        final long nextConflictSequence;

        PersistenceSnapshot(Map<String, KOMEConflictRecord> records, long nextConflictSequence) {
            this.records = Collections.unmodifiableMap(new LinkedHashMap<String, KOMEConflictRecord>(records));
            this.nextConflictSequence = nextConflictSequence;
        }
    }

    /** Current LOTR diplomacy only. KOME wars and recorded conflict participation are not inputs. */
    public Hostility currentHostility(KOMEWorldData data, String firstFaction,
            String secondFaction) {
        String first = KOMEAlliance.normalizeFactionKey(firstFaction);
        String second = KOMEAlliance.normalizeFactionKey(secondFaction);
        if (KOMEAlliance.findLotrFaction(first) == null
                || KOMEAlliance.findLotrFaction(second) == null) {
            return Hostility.UNKNOWN;
        }
        if (first.equals(second)) return Hostility.NON_HOSTILE;
        KOMEDiplomacyRelation relation = KOMEDiplomacyService.getRelation(data, first, second);
        return relation == KOMEDiplomacyRelation.ENEMIES
                || relation == KOMEDiplomacyRelation.MORTAL_ENEMIES
            ? Hostility.HOSTILE : Hostility.NON_HOSTILE;
    }

    /**
     * Production entry point for an arrival already authorized by KOME strategic movement.
     * This method does not inspect entity coordinates and cannot manufacture an arrival from
     * physical presence. Phase 4 owns the movement call site.
     */
    public Result acceptValidatedCommitment(KOMEWorldData data,
            ValidatedCommitmentRequest request, Context context) {
        if (data == null || data.getConflictService() != this) {
            return Result.failure(Code.INVALID_REQUEST, null,
                "The persisted world conflict authority is required.");
        }
        data.ensureWritable();
        Result result = acceptValidatedCommitment(request,
            new HostilityResolver() {
                @Override public Hostility resolve(String first, String second) {
                    return currentHostility(data, first, second);
                }
            }, productionDetachmentResolver(data), context);
        if (result.code == Code.SUCCESS) {
            data.markDirty();
            KOMEAuditService.record(data, context.timestampMillis, "CONFLICT", "COMMIT",
                context.actor, result.record.getConflictId(), context.reason,
                request.detachmentId + " -> " + request.destinationTileId
                    + " as " + request.origin);
        }
        return result;
    }

    /** Pure deterministic seam used by tests and the future strategic-movement adapter. */
    synchronized Result acceptValidatedCommitment(ValidatedCommitmentRequest request,
            HostilityResolver hostilityResolver, DetachmentResolver detachmentResolver,
            Context context) {
        try {
            required(request, "Validated commitment request");
            required(hostilityResolver, "Hostility resolver");
            required(detachmentResolver, "Detachment resolver");
            required(context, "Context");
        } catch (IllegalArgumentException invalid) {
            return Result.failure(Code.INVALID_REQUEST, null, invalid.getMessage());
        }
        KOMEConflictRecord previous = records.get(request.destinationTileId);
        if (request.acceptedAtMillis > context.timestampMillis) {
            return Result.failure(Code.TIME_REGRESSION, previous,
                "Accepted arrival cannot be later than its mutation context.");
        }
        Code guard = guard(previous, request.expectedConflict, context);
        if (guard != Code.SUCCESS)
            return Result.failure(guard, previous, "Commitment precondition failed.");
        Code resolution = validateDetachment(detachmentResolver.resolve(request.detachmentId),
            request.detachmentId, request.detachmentFactionId, request.destinationTileId);
        if (resolution != Code.SUCCESS)
            return Result.failure(resolution, previous, "Arriving detachment authority is not ready.");

        if (previous != null && previous.isActive()
                && previous.getCommitments().containsKey(request.detachmentId)) {
            if (request.expectedConflict.isAbsent()
                    || !request.expectedConflict.conflictId.equals(previous.getConflictId())) {
                return Result.failure(Code.STALE_CONFLICT_ID, previous,
                    "Idempotent arrival targeted another conflict.");
            }
            Commitment existing = previous.getCommitments().get(request.detachmentId);
            if (isExactReplay(previous, existing, request)) {
                return new Result(Code.ALREADY_COMMITTED_SAME_CONFLICT, previous, previous,
                    "The validated arrival was already accepted.");
            }
            return Result.failure(Code.DUPLICATE_DETACHMENT, previous,
                "The detachment is already committed with different arrival metadata.");
        }
        if (previous != null && request.acceptedAtMillis
                < previous.getLastTransition().timestampMillis) {
            return Result.failure(Code.TIME_REGRESSION, previous,
                "Accepted arrival cannot predate the current conflict checkpoint.");
        }

        if (previous != null && previous.isActive()
                && previous.getRevision() == Long.MAX_VALUE) {
            return Result.failure(Code.REVISION_EXHAUSTED, previous, "Revision exhausted.");
        }
        if (committedElsewhere(request.detachmentId, request.destinationTileId)) {
            return Result.failure(Code.DETACHMENT_ALREADY_COMMITTED, previous,
                "The detachment is committed in another active conflict.");
        }

        boolean creating = previous == null || !previous.isActive();
        if (creating && request.authority.kind
                != ConflictAuthorityKind.VALIDATED_DEFENDER)
            return Result.failure(Code.INVALID_REFERENCE, previous,
                "New conflict creation requires validated defending/owning faction authority.");
        if (!creating && request.authority.kind
                != ConflictAuthorityKind.ACTIVE_PARTICIPANT)
            return Result.failure(Code.INVALID_REFERENCE, previous,
                "Existing conflict entry requires an active participant authority.");
        if (!creating) {
            FactionParticipation authority = previous.getFactionParticipation()
                .get(request.authority.factionId);
            if (authority == null || !authority.isActive())
                return Result.failure(Code.INVALID_REFERENCE, previous,
                    "The opposed faction is not an active participant in this conflict.");
        }

        Hostility hostility = hostilityResolver.resolve(request.detachmentFactionId,
            request.authority.factionId);
        if (hostility == Hostility.UNKNOWN)
            return Result.failure(Code.UNKNOWN_HOSTILITY, previous,
                "Current LOTR faction relation is unavailable.");
        if (hostility != Hostility.HOSTILE)
            return Result.failure(Code.NON_HOSTILE, previous,
                "A validated hostile relation is required.");

        return creating
            ? createFromValidatedCommitment(previous, request, detachmentResolver, context)
            : joinFromValidatedCommitment(previous, request, context);
    }

    private Result createFromValidatedCommitment(KOMEConflictRecord previous,
            ValidatedCommitmentRequest request, DetachmentResolver resolver, Context context) {
        if (allocator.getNextSequence() == Long.MAX_VALUE)
            return Result.failure(Code.IDENTITY_EXHAUSTED, previous,
                "Conflict identities are exhausted.");
        if (request.origin == EntryOrigin.RELIEF)
            return Result.failure(Code.INVALID_ORIGIN, previous,
                "Relief classification requires an existing Encirclement.");

        State state = request.qualifyingDefensiveContext
            ? State.ENCIRCLEMENT : State.ORDINARY;
        Draft draft = new Draft("CF" + allocator.getNextSequence(),
            request.destinationTileId, state, request.acceptedAtMillis);
        Set<UUID> members = new HashSet<UUID>();
        Set<String> seededDetachments = new HashSet<String>();
        for (GarrisonParticipantSeed participant : request.originalGarrison) {
            if (participant == null)
                return Result.failure(Code.INVALID_REQUEST, previous, "Null garrison participant.");
            GarrisonSeed seed = participant.cohort;
            if (!seededDetachments.add(seed.detachmentId)
                    || seed.detachmentId.equals(request.detachmentId))
                return Result.failure(Code.DUPLICATE_DETACHMENT, previous,
                    "Duplicate initial detachment authority.");
            Code readiness = validateDetachment(resolver.resolve(seed.detachmentId),
                seed.detachmentId, participant.factionId, request.destinationTileId);
            if (readiness != Code.SUCCESS)
                return Result.failure(readiness, previous,
                    "Original-garrison detachment authority is not ready.");
            if (seed.classification != KOMEHiredUnitClass.CAMPAIGN)
                return Result.failure(Code.ORDINARY_NOT_ELIGIBLE, previous,
                    "Original garrison requires CAMPAIGN detachments.");
            if (committedElsewhere(seed.detachmentId, request.destinationTileId))
                return Result.failure(Code.DETACHMENT_ALREADY_COMMITTED, previous,
                    "An original-garrison detachment is committed elsewhere.");
            Map<UUID, GarrisonMemberState> cohort =
                new LinkedHashMap<UUID, GarrisonMemberState>();
            for (UUID member : seed.originalMembers) {
                if (!members.add(member))
                    return Result.failure(Code.DUPLICATE_GARRISON_MEMBER, previous,
                        "Original member appears in multiple cohorts.");
                cohort.put(member, GarrisonMemberState.ORIGINAL);
            }
            draft.commitments.put(seed.detachmentId, new Commitment(seed.detachmentId,
                EntryOrigin.ORIGINAL_GARRISON, request.acceptedAtMillis, ""));
            draft.originalGarrison.put(seed.detachmentId,
                new GarrisonCohort(seed.detachmentId, cohort));
            activateFaction(draft, participant.factionId, request.acceptedAtMillis);
        }
        draft.commitments.put(request.detachmentId, new Commitment(request.detachmentId,
            request.origin, request.acceptedAtMillis, request.movementOrderId,
            validatedEvent(request, true)));
        activateFaction(draft, request.authority.factionId, request.acceptedAtMillis);
        activateFaction(draft, request.detachmentFactionId, request.acceptedAtMillis);
        draft.revision = 1L;
        draft.lastTransition = new LastTransition(Operation.CREATE,
            previous == null ? null : previous.getState(), state, 1L, context,
            request.detachmentId);
        try {
            KOMEConflictRecord record = new KOMEConflictRecord(draft);
            allocator.allocate();
            records.put(request.destinationTileId, record);
            return Result.success(previous, record);
        } catch (IllegalArgumentException invalid) {
            return Result.failure(Code.INVALID_REQUEST, previous, invalid.getMessage());
        }
    }

    private Result joinFromValidatedCommitment(KOMEConflictRecord previous,
            ValidatedCommitmentRequest request, Context context) {
        if (!request.originalGarrison.isEmpty())
            return Result.failure(Code.INVALID_ORIGIN, previous,
                "Original garrison is immutable after conflict creation.");
        if (request.origin == EntryOrigin.RELIEF
                && previous.getState() != State.ENCIRCLEMENT)
            return Result.failure(Code.INVALID_ORIGIN, previous,
                "Relief classification requires an Encirclement.");

        Draft draft = new Draft(previous);
        draft.commitments.put(request.detachmentId, new Commitment(request.detachmentId,
            request.origin, request.acceptedAtMillis, request.movementOrderId,
            validatedEvent(request, false)));
        activateFaction(draft, request.detachmentFactionId, request.acceptedAtMillis);
        draft.revision = previous.getRevision() + 1L;
        draft.lastTransition = new LastTransition(Operation.COMMIT, previous.getState(),
            previous.getState(), draft.revision, context, request.detachmentId);
        try {
            KOMEConflictRecord record = new KOMEConflictRecord(draft);
            records.put(request.destinationTileId, record);
            return Result.success(previous, record);
        } catch (IllegalArgumentException invalid) {
            return Result.failure(Code.INVALID_REQUEST, previous, invalid.getMessage());
        }
    }

    /** Explicit authorized departure; missing runtime references never trigger this mutation. */
    public Result releaseValidatedCommitment(KOMEWorldData data,
            ValidatedDepartureRequest request, Context context) {
        if (data == null || data.getConflictService() != this) {
            return Result.failure(Code.INVALID_REQUEST, null,
                "The persisted world conflict authority is required.");
        }
        data.ensureWritable();
        Result result = releaseValidatedCommitment(request,
            productionDetachmentResolver(data), context);
        if (result.code == Code.SUCCESS) {
            data.markDirty();
            KOMEAuditService.record(data, context.timestampMillis, "CONFLICT", "DEPART",
                context.actor, result.record.getConflictId(), context.reason,
                request.detachmentId + " <- " + request.tileId);
        }
        return result;
    }

    synchronized Result releaseValidatedCommitment(ValidatedDepartureRequest request,
            DetachmentResolver resolver, Context context) {
        KOMEConflictRecord previous = null;
        try {
            required(request, "Validated departure request");
            required(resolver, "Detachment resolver");
            required(context, "Context");
            previous = records.get(request.tileId);
            Code guard = guard(previous, request.expectedConflict, context);
            if (guard != Code.SUCCESS)
                return Result.failure(guard, previous, "Departure precondition failed.");
            if (previous == null) return Result.failure(Code.NOT_FOUND, null, "Conflict is absent.");
            if (!previous.isActive())
                return Result.failure(Code.CONFLICT_ENDED, previous, "Ended conflict is immutable.");
            if (previous.getRevision() == Long.MAX_VALUE)
                return Result.failure(Code.REVISION_EXHAUSTED, previous, "Revision exhausted.");
            Commitment departing = previous.getCommitments().get(request.detachmentId);
            if (departing == null)
                return Result.failure(Code.COMMITMENT_NOT_FOUND, previous,
                    "Detachment is not committed to this conflict.");
            DetachmentResolution departingResolution = resolver.resolve(request.detachmentId);
            Code departingReadiness = validateDetachment(departingResolution,
                request.detachmentId, request.factionId, request.tileId);
            if (departingReadiness != Code.SUCCESS)
                return Result.failure(departingReadiness, previous,
                    "Departing detachment authority cannot be resolved.");
            FactionParticipation participation =
                previous.getFactionParticipation().get(request.factionId);
            if (participation == null || !participation.isActive())
                return Result.failure(Code.FACTION_NOT_ACTIVE, previous,
                    "Departure faction is not continuously participating.");

            boolean sameFactionRemains = false;
            for (String detachmentId : previous.getCommitments().keySet()) {
                if (detachmentId.equals(request.detachmentId)) continue;
                DetachmentResolution resolved = resolver.resolve(detachmentId);
                Code readiness = validateResolvedReference(resolved, detachmentId);
                if (readiness == Code.SUCCESS
                        && resolved.classification != KOMEHiredUnitClass.CAMPAIGN)
                    readiness = Code.ORDINARY_NOT_ELIGIBLE;
                if (readiness == Code.SUCCESS
                        && !request.tileId.equals(resolved.strategicTileId))
                    readiness = Code.DETACHMENT_TILE_MISMATCH;
                if (readiness != Code.SUCCESS)
                    return Result.failure(readiness, previous,
                        "A remaining commitment cannot be resolved; departure is fail-closed.");
                if (request.factionId.equals(resolved.factionId)) sameFactionRemains = true;
            }

            Draft draft = new Draft(previous);
            draft.commitments.remove(request.detachmentId);
            if (!sameFactionRemains) {
                draft.factionParticipation.put(request.factionId,
                    new FactionParticipation(request.factionId,
                        participation.continuitySequence, participation.startedAtMillis,
                        context.timestampMillis));
            }
            draft.revision = previous.getRevision() + 1L;
            draft.lastTransition = new LastTransition(Operation.DETACHMENT_DEPARTURE,
                previous.getState(), previous.getState(), draft.revision, context,
                request.detachmentId);
            KOMEConflictRecord record = new KOMEConflictRecord(draft);
            records.put(request.tileId, record);
            return Result.success(previous, record);
        } catch (IllegalArgumentException invalid) {
            return Result.failure(Code.INVALID_REQUEST, previous, invalid.getMessage());
        }
    }

    private static DetachmentResolver productionDetachmentResolver(final KOMEWorldData data) {
        return new DetachmentResolver() {
            @Override public DetachmentResolution resolve(String detachmentId) {
                KOMEArmyCompany company = data.armyCompanies.get(detachmentId);
                if (company == null) return new DetachmentResolution(detachmentId,
                    ReferenceStatus.MISSING, null, "", "", "Campaign Detachment is missing.");
                String factionId = KOMEAlliance.normalizeFactionKey(company.faction);
                String tileId = KOMEConquestTile.normalizeId(company.currentTile);
                if (factionId.length() == 0 || KOMEAlliance.findLotrFaction(factionId) == null
                        || tileId.length() == 0)
                    return new DetachmentResolution(detachmentId,
                        ReferenceStatus.INCOHERENT, null, "", "",
                        "Campaign Detachment lacks faction or strategic tile authority.");
                for (UUID member : company.units) {
                    KOMEHiredUnitRecord record = data.hiredUnits.get(member);
                    if (record == null) return new DetachmentResolution(detachmentId,
                        ReferenceStatus.MISSING, null, "", "",
                        "A Campaign Detachment member record is missing.");
                    if (!KOMEHiredUnitClassification.isCampaignUnit(record))
                        return new DetachmentResolution(detachmentId,
                            ReferenceStatus.RESOLVED, KOMEHiredUnitClass.ORDINARY,
                            factionId, tileId, "An ORDINARY member cannot form a commitment.");
                }
                KOMECompanyCoherenceService.Assessment assessment =
                    KOMECompanyCoherenceService.INSTANCE.assess(data, company);
                if (assessment.status == KOMECompanyCoherenceService.Status.INCOHERENT)
                    return new DetachmentResolution(detachmentId,
                        ReferenceStatus.INCOHERENT, null, "", "",
                        "Campaign Detachment coherence failed.");
                return new DetachmentResolution(detachmentId, ReferenceStatus.RESOLVED,
                    KOMEHiredUnitClass.CAMPAIGN, factionId, tileId,
                    assessment.status.name());
            }
        };
    }

    private static Code validateDetachment(DetachmentResolution resolution,
            String expectedId, String expectedFaction, String expectedTile) {
        Code readiness = validateResolvedReference(resolution, expectedId);
        if (readiness != Code.SUCCESS) return readiness;
        if (resolution.classification != KOMEHiredUnitClass.CAMPAIGN)
            return Code.ORDINARY_NOT_ELIGIBLE;
        if (!expectedFaction.equals(resolution.factionId))
            return Code.DETACHMENT_FACTION_MISMATCH;
        if (!expectedTile.equals(resolution.strategicTileId))
            return Code.DETACHMENT_TILE_MISMATCH;
        return Code.SUCCESS;
    }

    private static Code validateResolvedReference(DetachmentResolution resolution,
            String expectedId) {
        if (resolution == null || !expectedId.equals(resolution.detachmentId))
            return Code.DETACHMENT_UNRESOLVED;
        if (resolution.status == ReferenceStatus.RESOLVED) return Code.SUCCESS;
        return resolution.status == ReferenceStatus.INCOHERENT
                || resolution.status == ReferenceStatus.AMBIGUOUS
            ? Code.DETACHMENT_INCOHERENT : Code.DETACHMENT_UNRESOLVED;
    }

    private static void activateFaction(Draft draft, String factionId, long timestamp) {
        FactionParticipation previous = draft.factionParticipation.get(factionId);
        if (previous != null && previous.isActive()) return;
        if (previous != null && previous.continuitySequence == Long.MAX_VALUE)
            throw new IllegalArgumentException("Faction continuity sequence exhausted.");
        draft.factionParticipation.put(factionId, new FactionParticipation(factionId,
            previous == null ? 1L : previous.continuitySequence + 1L, timestamp, null));
    }

    private static ValidatedCommitmentEvent validatedEvent(
            ValidatedCommitmentRequest request, boolean createdConflict) {
        Map<String, String> factions = new LinkedHashMap<String, String>();
        for (GarrisonParticipantSeed participant : request.originalGarrison)
            factions.put(participant.cohort.detachmentId, participant.factionId);
        return new ValidatedCommitmentEvent(request.detachmentFactionId,
            request.authority.kind, request.authority.factionId, createdConflict,
            request.qualifyingDefensiveContext, factions);
    }

    private static boolean isExactReplay(KOMEConflictRecord record,
            Commitment existing, ValidatedCommitmentRequest request) {
        ValidatedCommitmentEvent event = existing.validatedEvent;
        if (event == null || existing.acceptedAtMillis != request.acceptedAtMillis
                || existing.origin != request.origin
                || !existing.movementOrderId.equals(request.movementOrderId)
                || !event.detachmentFactionId.equals(request.detachmentFactionId)
                || event.authorityKind != request.authority.kind
                || !event.authorityFactionId.equals(request.authority.factionId)
                || event.defensiveContext != request.qualifyingDefensiveContext)
            return false;
        Map<String, String> factions = new LinkedHashMap<String, String>();
        Map<String, Set<UUID>> members = new LinkedHashMap<String, Set<UUID>>();
        for (GarrisonParticipantSeed participant : request.originalGarrison) {
            if (participant == null
                    || participant.cohort.classification != KOMEHiredUnitClass.CAMPAIGN
                    || factions.put(participant.cohort.detachmentId,
                        participant.factionId) != null
                    || members.put(participant.cohort.detachmentId,
                        participant.cohort.originalMembers) != null)
                return false;
        }
        if (!event.originalGarrisonFactions.equals(factions)) return false;
        if (!event.createdConflict) return members.isEmpty();
        if (!record.getOriginalGarrison().keySet().equals(members.keySet())) return false;
        for (Map.Entry<String, Set<UUID>> entry : members.entrySet()) {
            GarrisonCohort cohort = record.getOriginalGarrison().get(entry.getKey());
            if (cohort == null || !cohort.members.keySet().equals(entry.getValue()))
                return false;
        }
        return true;
    }

    /** Only absent/ended -> fresh ORDINARY/ENCIRCLEMENT; an ended predecessor must be acknowledged. */
    public synchronized Result start(String tileId, State initialState, ExpectedConflict expected,
            Collection<GarrisonSeed> originalGarrison, Context context) {
        String normalized;
        try { normalized = tile(tileId); required(context, "Context"); required(expected, "Expected conflict"); }
        catch (IllegalArgumentException invalid) { return Result.failure(Code.INVALID_REQUEST, null, invalid.getMessage()); }
        KOMEConflictRecord previous = records.get(normalized);
        if (initialState != State.ORDINARY && initialState != State.ENCIRCLEMENT)
            return Result.failure(Code.INVALID_TRANSITION, previous, "Only ordinary or encirclement may be started.");
        Code guard = guard(previous, expected, context);
        if (guard != Code.SUCCESS) return Result.failure(guard, previous, "Conflict creation precondition failed.");
        if (previous != null && previous.isActive())
            return Result.failure(Code.ACTIVE_CONFLICT_EXISTS, previous, "This tile already has an active conflict.");
        if (originalGarrison == null)
            return Result.failure(Code.INVALID_REQUEST, previous, "Explicit original-garrison snapshot required.");
        if (initialState != State.ENCIRCLEMENT && !originalGarrison.isEmpty())
            return Result.failure(Code.INVALID_TRANSITION, previous, "Only Encirclement has a trapped-garrison cohort.");
        if (allocator.getNextSequence() == Long.MAX_VALUE)
            return Result.failure(Code.IDENTITY_EXHAUSTED, previous, "Conflict identities are exhausted.");

        // Validate/build before consuming an identity or publishing any part of the new record.
        Draft draft = new Draft("CF" + allocator.getNextSequence(), normalized, initialState, context.timestampMillis);
        Set<UUID> members = new HashSet<UUID>();
        for (GarrisonSeed seed : originalGarrison) {
            if (seed == null) return Result.failure(Code.INVALID_REQUEST, previous, "Null garrison seed.");
            if (seed.classification != KOMEHiredUnitClass.CAMPAIGN)
                return Result.failure(Code.ORDINARY_NOT_ELIGIBLE, previous, "Garrison commitments require CAMPAIGN classification.");
            if (draft.commitments.containsKey(seed.detachmentId))
                return Result.failure(Code.DUPLICATE_DETACHMENT, previous, "Duplicate initial detachment.");
            if (committedElsewhere(seed.detachmentId, normalized))
                return Result.failure(Code.DETACHMENT_ALREADY_COMMITTED, previous, "Detachment is committed in another active tile.");
            Map<UUID, GarrisonMemberState> cohort = new LinkedHashMap<UUID, GarrisonMemberState>();
            for (UUID member : seed.originalMembers) {
                if (!members.add(member))
                    return Result.failure(Code.DUPLICATE_GARRISON_MEMBER, previous, "Original member appears in multiple cohorts.");
                cohort.put(member, GarrisonMemberState.ORIGINAL);
            }
            draft.commitments.put(seed.detachmentId,
                new Commitment(seed.detachmentId, EntryOrigin.ORIGINAL_GARRISON, context.timestampMillis, ""));
            draft.originalGarrison.put(seed.detachmentId, new GarrisonCohort(seed.detachmentId, cohort));
        }
        draft.revision = 1L;
        draft.lastTransition = new LastTransition(Operation.CREATE, previous == null ? null : previous.getState(),
            initialState, 1L, context, draft.conflictId);
        KOMEConflictRecord record = new KOMEConflictRecord(draft);
        allocator.allocate();
        records.put(normalized, record);
        return Result.success(previous, record);
    }

    /** Explicit authorized ending, not victory inference. Preserves the final diagnostic snapshot. */
    synchronized Result end(String tileId, ExpectedConflict expected, Context context) {
        return mutate(tileId, expected, context, Operation.END, "", draft -> {
            draft.state = State.ENDED;
            draft.endedAtMillis = context.timestampMillis;
            for (String id : draft.factionParticipation.keySet()) {
                FactionParticipation faction = draft.factionParticipation.get(id);
                if (faction.isActive()) draft.factionParticipation.put(id, new FactionParticipation(id,
                    faction.continuitySequence, faction.startedAtMillis, context.timestampMillis));
            }
            if (draft.encirclement != null) draft.encirclement = new EncirclementLifecycle(
                draft.encirclement.startedAtMillis, draft.encirclement.liveElapsedMillis,
                draft.encirclement.checkpointAtMillis, context.timestampMillis);
            return Code.SUCCESS;
        });
    }

    /**
     * World-backed lifecycle transaction. It validates every owned route hold before publishing
     * ENDED, then converts those orders to a persisted non-scheduling pause without resuming them.
     */
    public synchronized EndResult endWithMovementHandoff(KOMEWorldData data,
            String tileId, ExpectedConflict expected, Context context, EndSource source) {
        if (data == null || data.getConflictService() != this || source == null) {
            return new EndResult(Result.failure(Code.INVALID_REQUEST, null,
                "Persisted world conflict authority and end source are required."), 0);
        }
        data.ensureWritable();
        String normalized;
        try {
            normalized = tile(tileId);
            required(expected, "Expected conflict");
            required(context, "Context");
            if (context.reason.trim().length() == 0)
                throw new IllegalArgumentException("Explicit conflict end reason is required.");
        } catch (IllegalArgumentException invalid) {
            return new EndResult(Result.failure(Code.INVALID_REQUEST, null,
                invalid.getMessage()), 0);
        }
        KOMEConflictRecord current = records.get(normalized);
        Code precondition = guard(current, expected, context);
        if (precondition != Code.SUCCESS)
            return new EndResult(Result.failure(precondition, current,
                "Conflict end precondition failed."), 0);
        if (current == null)
            return new EndResult(Result.failure(Code.NOT_FOUND, null,
                "Conflict is absent."), 0);
        if (!current.isActive())
            return new EndResult(Result.failure(Code.CONFLICT_ENDED, current,
                "Ended conflict is immutable."), 0);

        List<KOMEArmyMovementOrder> holds = new ArrayList<KOMEArmyMovementOrder>();
        for (KOMEArmyMovementOrder order : data.armyMovements.values()) {
            if (order == null
                    || !KOMEArmyMovementOrder.CONFLICT_HELD.equals(order.status)) continue;
            String heldConflictId = order.conflictHoldId == null
                ? "" : order.conflictHoldId.trim();
            boolean related = normalized.equals(KOMEConquestTile.normalizeId(order.currentTile))
                || current.getCommitments().containsKey(order.companyId)
                || current.getConflictId().equals(heldConflictId);
            if (!related) continue;
            if (!current.getConflictId().equals(heldConflictId))
                return new EndResult(Result.failure(Code.AMBIGUOUS_REFERENCE,
                    current, "A related movement order claims another conflict hold."), 0);
            String error = KOMEConflictMovementHoldValidator.validate(data, current, order,
                KOMEConflictMovementHoldValidator.LinkPolicy.REQUIRE_COMPLETE);
            if (error.length() > 0)
                return new EndResult(Result.failure(Code.AMBIGUOUS_REFERENCE,
                    current, error), 0);
            holds.add(order);
        }

        Result ended = end(normalized, expected, context);
        if (ended.code != Code.SUCCESS) return new EndResult(ended, 0);
        for (KOMEArmyMovementOrder order : holds) {
            KOMEConflictLifecycleService.releaseEndedHold(order,
                ended.record.getConflictId());
            KOMEArmyCompany company = data.armyCompanies.get(order.companyId);
            if (company != null) {
                company.status = KOMEArmyCompany.STATIONED;
                company.movementOrderId = order.id;
                company.currentTile = ended.record.getTileId();
                company.updatedAtMillis = context.timestampMillis;
            }
            for (UUID unitId : order.units) {
                KOMEHiredUnitRecord unit = data.hiredUnits.get(unitId);
                if (unit != null) unit.movementOrderId = order.id;
            }
            data.updateMovementHistory(order,
                KOMEMovementHistoryRecord.CONFLICT_RELEASED_PAUSED);
        }
        KOMEAuditService.record(data, context.timestampMillis, "CONFLICT",
            source == EndSource.ADMIN_FORCED ? "FORCED_END" : "END",
            context.actor, ended.record.getConflictId(), context.reason,
            "tile=" + normalized + ";releasedHolds=" + holds.size()
                + ";winner=none;ownershipTransfer=none");
        KOMEEmergencyDefenseMobilizationService.INSTANCE.onConflictEnded(data,
            ended.record.getConflictId(), context.timestampMillis);
        data.markDirty();
        return new EndResult(ended, holds.size());
    }

    public synchronized Result commit(String tileId, ExpectedConflict expected, CommitmentInput input, Context context) {
        return mutate(tileId, expected, context, Operation.COMMIT, input == null ? "" : input.detachmentId, draft -> {
            required(input, "Commitment input");
            if (input.classification != KOMEHiredUnitClass.CAMPAIGN) return Code.ORDINARY_NOT_ELIGIBLE;
            if (input.origin == EntryOrigin.ORIGINAL_GARRISON
                    || input.origin == EntryOrigin.RELIEF && draft.state != State.ENCIRCLEMENT) return Code.INVALID_ORIGIN;
            if (draft.commitments.containsKey(input.detachmentId)) return Code.DUPLICATE_DETACHMENT;
            if (committedElsewhere(input.detachmentId, draft.tileId)) return Code.DETACHMENT_ALREADY_COMMITTED;
            draft.commitments.put(input.detachmentId, new Commitment(input.detachmentId, input.origin,
                context.timestampMillis, input.movementOrderId));
            return Code.SUCCESS;
        });
    }

    /** No diplomacy/side selection occurs here. Presence alone never invokes this command. */
    public synchronized Result beginFactionParticipation(String tileId, ExpectedConflict expected, String factionId, Context context) {
        return mutate(tileId, expected, context, Operation.FACTION_JOIN, factionId, draft -> {
            String id = faction(factionId);
            FactionParticipation previous = draft.factionParticipation.get(id);
            if (previous != null && previous.isActive()) return Code.FACTION_ALREADY_ACTIVE;
            if (previous != null && previous.continuitySequence == Long.MAX_VALUE) return Code.REVISION_EXHAUSTED;
            draft.factionParticipation.put(id, new FactionParticipation(id,
                previous == null ? 1L : previous.continuitySequence + 1L, context.timestampMillis, null));
            return Code.SUCCESS;
        });
    }

    public synchronized Result endFactionParticipation(String tileId, ExpectedConflict expected, String factionId, Context context) {
        return mutate(tileId, expected, context, Operation.FACTION_LEAVE, factionId, draft -> {
            String id = faction(factionId);
            FactionParticipation previous = draft.factionParticipation.get(id);
            if (previous == null || !previous.isActive()) return Code.FACTION_NOT_ACTIVE;
            draft.factionParticipation.put(id, new FactionParticipation(id, previous.continuitySequence,
                previous.startedAtMillis, context.timestampMillis));
            return Code.SUCCESS;
        });
    }

    public synchronized Result registerPlayer(String tileId, ExpectedConflict expected, UUID playerId, String factionId, Context context) {
        return mutate(tileId, expected, context, Operation.PLAYER_REGISTER, String.valueOf(playerId), draft -> {
            required(playerId, "Player UUID");
            if (draft.players.containsKey(playerId)) return Code.PLAYER_ALREADY_REGISTERED;
            draft.players.put(playerId, new PlayerParticipation(playerId, factionId, context.timestampMillis, PlayerStatus.ACTIVE, null));
            return Code.SUCCESS;
        });
    }

    /** Stores an explicit participation fact only. No retreat, death, route or detachment effect. */
    public synchronized Result withdrawPlayer(String tileId, ExpectedConflict expected, UUID playerId, Context context) {
        return mutate(tileId, expected, context, Operation.PLAYER_WITHDRAW, String.valueOf(playerId), draft -> {
            required(playerId, "Player UUID");
            PlayerParticipation previous = draft.players.get(playerId);
            if (previous == null || previous.status != PlayerStatus.ACTIVE) return Code.PLAYER_NOT_ACTIVE;
            draft.players.put(playerId, new PlayerParticipation(playerId, previous.factionAtRegistration,
                previous.startedAtMillis, PlayerStatus.WITHDRAWN, context.timestampMillis));
            return Code.SUCCESS;
        });
    }

    public synchronized Result registerComplex(String tileId, ExpectedConflict expected, String complexId, Context context) {
        return mutate(tileId, expected, context, Operation.COMPLEX_REGISTER, complexId, draft -> {
            if (draft.state != State.ENCIRCLEMENT) return Code.INVALID_TRANSITION;
            String id = id(complexId);
            if (draft.complexes.containsKey(id)) return Code.DUPLICATE_COMPLEX;
            if (draft.complexCatalog.availability == CatalogAvailability.AVAILABLE) return Code.AMBIGUOUS_REFERENCE;
            draft.complexes.put(id, ComplexSubstate.unstarted(id));
            return Code.SUCCESS;
        });
    }

    /** Binding cannot silently discard previously referenced complexes/progress or replace a known manifest. */
    public synchronized Result bindComplexCatalog(String tileId, ExpectedConflict expected, ComplexCatalog catalog, Context context) {
        return mutate(tileId, expected, context, Operation.CATALOG_BIND, "", draft -> {
            required(catalog, "Complex catalog");
            if (draft.state != State.ENCIRCLEMENT) return Code.INVALID_TRANSITION;
            if (catalog.availability != CatalogAvailability.AVAILABLE) return Code.INVALID_REFERENCE;
            if (!catalog.requiredComplexIds.containsAll(draft.complexes.keySet())) return Code.AMBIGUOUS_REFERENCE;
            if (draft.complexCatalog.availability == CatalogAvailability.AVAILABLE)
                return draft.complexCatalog.requiredComplexIds.equals(catalog.requiredComplexIds) ? Code.NO_CHANGE : Code.AMBIGUOUS_REFERENCE;
            for (String id : catalog.requiredComplexIds)
                if (!draft.complexes.containsKey(id)) draft.complexes.put(id, ComplexSubstate.unstarted(id));
            draft.complexCatalog = catalog;
            return Code.SUCCESS;
        });
    }

    /** Validated phase-controller checkpoint, not assault authorization or capture calculation. */
    public synchronized Result checkpointComplex(String tileId, ExpectedConflict expected, ComplexSubstate complex, Context context) {
        return mutate(tileId, expected, context, Operation.COMPLEX_CHECKPOINT, complex == null ? "" : complex.complexId, draft -> {
            required(complex, "Complex substate");
            if (draft.state != State.ENCIRCLEMENT) return Code.INVALID_TRANSITION;
            if (!draft.complexes.containsKey(complex.complexId)) return Code.INVALID_REFERENCE;
            draft.complexes.put(complex.complexId, complex);
            return Code.SUCCESS;
        });
    }

    /** Atomic DATA checkpoint. No clock source, duration selection, ticking or expiry consequence. */
    public synchronized Result checkpointCombat(String tileId, ExpectedConflict expected, CombatEpisode episode,
            TimerSlot response, TimerSlot capture, Context context) {
        return mutate(tileId, expected, context, Operation.COMBAT_CHECKPOINT, episode == null ? "" : episode.episodeId, draft -> {
            required(episode, "Episode"); required(response, "Response timer"); required(capture, "Capture timer");
            CombatEpisode previous = draft.combatEpisode;
            if (episode.sequence < previous.sequence || episode.sequence > previous.sequence
                    && (previous.sequence == Long.MAX_VALUE || episode.sequence != previous.sequence + 1L
                        || previous.state == EpisodeState.ACTIVE)) return Code.INVALID_EPISODE;
            if (episode.sequence > 0 && !episode.episodeId.equals(draft.conflictId + ":E" + episode.sequence)) return Code.INVALID_EPISODE;
            if (episode.startedAtMillis != null && (episode.startedAtMillis < draft.createdAtMillis
                    || episode.startedAtMillis > context.timestampMillis)) return Code.TIME_REGRESSION;
            if (episode.endedAtMillis != null && episode.endedAtMillis > context.timestampMillis) return Code.TIME_REGRESSION;
            if (episode.sequence == previous.sequence && (!java.util.Objects.equals(episode.startedAtMillis, previous.startedAtMillis)
                    || previous.endedAtMillis != null && !java.util.Objects.equals(episode.endedAtMillis, previous.endedAtMillis)))
                return Code.INVALID_EPISODE;
            if (!timerMatches(response, episode, context) || !timerMatches(capture, episode, context)) return Code.INVALID_REFERENCE;
            if (episode.sequence == previous.sequence && (!timerAdvances(draft.responseTimer, response)
                    || !timerAdvances(draft.captureTimer, capture))) return Code.INVALID_EPISODE;
            draft.combatEpisode = episode; draft.responseTimer = response; draft.captureTimer = capture;
            return Code.SUCCESS;
        });
    }

    public synchronized Result checkpointEncirclement(String tileId, ExpectedConflict expected, long liveElapsedMillis, Context context) {
        return mutate(tileId, expected, context, Operation.ENCIRCLEMENT_CHECKPOINT, "", draft -> {
            if (draft.state != State.ENCIRCLEMENT) return Code.INVALID_TRANSITION;
            if (liveElapsedMillis < draft.encirclement.liveElapsedMillis) return Code.TIME_REGRESSION;
            draft.encirclement = new EncirclementLifecycle(draft.encirclement.startedAtMillis,
                liveElapsedMillis, context.timestampMillis, null);
            return Code.SUCCESS;
        });
    }

    /** Diagnostic fact only: it cannot remove commitments or infer peace/withdrawal/death/relocation. */
    public synchronized Result recordDiagnostic(String tileId, ExpectedConflict expected, ReferenceDiagnostic diagnostic, Context context) {
        return mutate(tileId, expected, context, Operation.REFERENCE_DIAGNOSTIC, diagnostic == null ? "" : diagnostic.key(), draft -> {
            required(diagnostic, "Diagnostic");
            ReferenceDiagnostic previous = draft.diagnostics.get(diagnostic.key());
            if (previous != null && previous.status == diagnostic.status && previous.reason.equals(diagnostic.reason)) return Code.NO_CHANGE;
            draft.diagnostics.put(diagnostic.key(), diagnostic);
            return Code.SUCCESS;
        });
    }

    /**
     * Explicit entity-identity replacement from a verified KOME lifecycle operation. This changes
     * no cohort rights or commitment identity and never interprets a missing UUID as death.
     */
    public Result rekeyOriginalGarrisonMembers(KOMEWorldData data, String tileId,
            ExpectedConflict expected, final Map<UUID, UUID> replacements,
            Context context) {
        if (data == null || data.getConflictService() != this)
            return Result.failure(Code.INVALID_REQUEST, null,
                "The persisted world conflict authority is required.");
        data.ensureWritable();
        Result result = rekeyOriginalGarrisonMembers(tileId, expected,
            replacements, context);
        if (result.code == Code.SUCCESS) {
            data.markDirty();
            KOMEAuditService.record(data, context.timestampMillis, "CONFLICT",
                "GARRISON_UUID_REKEY", context.actor, result.record.getConflictId(),
                context.reason, "Verified replacements=" + replacements.size());
        }
        return result;
    }

    synchronized Result rekeyOriginalGarrisonMembers(String tileId,
            ExpectedConflict expected, final Map<UUID, UUID> replacements,
            Context context) {
        return mutate(tileId, expected, context, Operation.GARRISON_MEMBER_REKEY,
            "", draft -> {
                required(replacements, "UUID replacements");
                if (replacements.isEmpty()) return Code.NO_CHANGE;
                Set<UUID> resulting = new HashSet<UUID>();
                boolean changed = false;
                Map<String, GarrisonCohort> updated =
                    new LinkedHashMap<String, GarrisonCohort>();
                for (Map.Entry<String, GarrisonCohort> entry
                        : draft.originalGarrison.entrySet()) {
                    Map<UUID, GarrisonMemberState> members =
                        new LinkedHashMap<UUID, GarrisonMemberState>();
                    for (Map.Entry<UUID, GarrisonMemberState> member
                            : entry.getValue().members.entrySet()) {
                        UUID replacement = replacements.get(member.getKey());
                        UUID identity = replacement == null ? member.getKey() : replacement;
                        if (identity == null || !resulting.add(identity))
                            return Code.AMBIGUOUS_REFERENCE;
                        if (replacement != null && !replacement.equals(member.getKey()))
                            changed = true;
                        members.put(identity, member.getValue());
                    }
                    updated.put(entry.getKey(), new GarrisonCohort(entry.getKey(), members));
                }
                if (!changed) return Code.NO_CHANGE;
                draft.originalGarrison.clear();
                draft.originalGarrison.putAll(updated);
                return Code.SUCCESS;
            });
    }

    private Result mutate(String tileId, ExpectedConflict expected, Context context, Operation operation, String subject, Change change) {
        String normalized;
        try { normalized = tile(tileId); required(context, "Context"); required(expected, "Expected conflict"); }
        catch (IllegalArgumentException invalid) { return Result.failure(Code.INVALID_REQUEST, null, invalid.getMessage()); }
        KOMEConflictRecord previous = records.get(normalized);
        Code guard = guard(previous, expected, context);
        if (guard != Code.SUCCESS) return Result.failure(guard, previous, "Mutation precondition failed.");
        if (previous == null) return Result.failure(Code.NOT_FOUND, null, "Conflict is absent.");
        if (!previous.isActive()) return Result.failure(Code.CONFLICT_ENDED, previous, "Ended conflict is immutable.");
        if (previous.getRevision() == Long.MAX_VALUE) return Result.failure(Code.REVISION_EXHAUSTED, previous, "Revision exhausted.");
        Draft draft = new Draft(previous);
        try {
            Code outcome = change.apply(draft);
            if (outcome == Code.NO_CHANGE) return new Result(Code.NO_CHANGE, previous, previous, "Already at this checkpoint.");
            if (outcome != Code.SUCCESS) return Result.failure(outcome, previous, "Rejected " + operation + ": " + outcome);
            draft.revision = previous.getRevision() + 1L;
            draft.lastTransition = new LastTransition(operation, previous.getState(), draft.state, draft.revision, context, subject);
            KOMEConflictRecord record = new KOMEConflictRecord(draft);
            records.put(normalized, record);
            return Result.success(previous, record);
        } catch (IllegalArgumentException invalid) {
            return Result.failure(Code.INVALID_REQUEST, previous, invalid.getMessage());
        }
    }

    private static Code guard(KOMEConflictRecord previous, ExpectedConflict expected, Context context) {
        if (expected.isAbsent()) {
            if (previous != null) return previous.isActive() ? Code.ACTIVE_CONFLICT_EXISTS : Code.STALE_CONFLICT_ID;
            return Code.SUCCESS;
        }
        if (previous == null) return Code.NOT_FOUND;
        if (!expected.conflictId.equals(previous.getConflictId())) return Code.STALE_CONFLICT_ID;
        if (expected.revision != previous.getRevision()) return Code.STALE_REVISION;
        if (context.timestampMillis < previous.getLastTransition().timestampMillis) return Code.TIME_REGRESSION;
        return Code.SUCCESS;
    }

    private boolean committedElsewhere(String detachmentId, String tileId) {
        for (KOMEConflictRecord record : records.values())
            if (record.isActive() && !record.getTileId().equals(tileId) && record.getCommitments().containsKey(detachmentId)) return true;
        return false;
    }

    private static boolean timerMatches(TimerSlot timer, CombatEpisode episode, Context context) {
        return timer.status == TimerStatus.NOT_STARTED || timer.episodeId.equals(episode.episodeId)
            && timer.checkpointAtMillis >= episode.startedAtMillis && timer.checkpointAtMillis <= context.timestampMillis;
    }

    /** A checkpoint cannot silently restart the same episode's duration/elapsed snapshot. */
    private static boolean timerAdvances(TimerSlot previous, TimerSlot next) {
        return previous.status == TimerStatus.NOT_STARTED || next.status != TimerStatus.NOT_STARTED
            && next.durationSnapshotMillis == previous.durationSnapshotMillis
            && next.elapsedMillis >= previous.elapsedMillis && next.checkpointAtMillis >= previous.checkpointAtMillis;
    }

    private interface Change { Code apply(Draft draft); }

    public static final class Result {
        public final Code code;
        public final KOMEConflictRecord previous;
        public final KOMEConflictRecord record;
        public final String reason;

        private Result(Code code, KOMEConflictRecord previous, KOMEConflictRecord record, String reason) {
            this.code = code; this.previous = previous; this.record = record; this.reason = reason;
        }
        public boolean isSuccess() {
            return code == Code.SUCCESS || code == Code.NO_CHANGE
                || code == Code.ALREADY_COMMITTED_SAME_CONFLICT;
        }
        private static Result success(KOMEConflictRecord previous, KOMEConflictRecord record) {
            return new Result(Code.SUCCESS, previous, record, "");
        }
        private static Result failure(Code code, KOMEConflictRecord record, String reason) {
            return new Result(code, record, record, reason);
        }
    }
}
