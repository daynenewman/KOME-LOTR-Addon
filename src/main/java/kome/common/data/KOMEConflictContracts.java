package kome.common.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Data-only boundaries for validated server adapters. These contracts do not certify a physical
 * observation as a legal arrival, resolve combat, or themselves write world data.
 */
public final class KOMEConflictContracts {
    private KOMEConflictContracts() { }

    public enum Code {
        SUCCESS, NO_CHANGE, INVALID_REQUEST, NOT_FOUND, ACTIVE_CONFLICT_EXISTS,
        STALE_CONFLICT_ID, STALE_REVISION, CONFLICT_ENDED, INVALID_TRANSITION,
        IDENTITY_EXHAUSTED, REVISION_EXHAUSTED, TIME_REGRESSION,
        ORDINARY_NOT_ELIGIBLE, DUPLICATE_DETACHMENT, DETACHMENT_ALREADY_COMMITTED,
        INVALID_ORIGIN, DUPLICATE_GARRISON_MEMBER, FACTION_ALREADY_ACTIVE, FACTION_NOT_ACTIVE,
        PLAYER_ALREADY_REGISTERED, PLAYER_NOT_ACTIVE, DUPLICATE_COMPLEX,
        INVALID_REFERENCE, AMBIGUOUS_REFERENCE, INVALID_EPISODE,
        NON_HOSTILE, UNKNOWN_HOSTILITY, DETACHMENT_UNRESOLVED,
        DETACHMENT_INCOHERENT, DETACHMENT_TILE_MISMATCH,
        DETACHMENT_FACTION_MISMATCH, ALREADY_COMMITTED_SAME_CONFLICT,
        COMMITMENT_NOT_FOUND
    }

    /** UNKNOWN is not peace; no fallback to native Neutral belongs in the conflict contract. */
    public enum Hostility { HOSTILE, NON_HOSTILE, UNKNOWN }
    public enum ConflictAuthorityKind { VALIDATED_DEFENDER, ACTIVE_PARTICIPANT }
    public enum ReferenceStatus { RESOLVED, UNKNOWN, MISSING, INCOHERENT, AMBIGUOUS }
    public enum ReferenceKind { DETACHMENT, FACTION, PLAYER, GARRISON_MEMBER, SIEGE_COMPLEX }
    public enum CatalogAvailability { UNAVAILABLE, AVAILABLE }

    public interface HostilityResolver {
        Hostility resolve(String firstFaction, String secondFaction);
    }

    public interface DetachmentResolver {
        DetachmentResolution resolve(String detachmentId);
    }

    public interface ComplexCatalogResolver {
        ComplexCatalog resolve(String normalizedTileId);
    }

    /** Canonical identity/class/location projection, not a copied troop membership list. */
    public static final class DetachmentResolution {
        public final String detachmentId;
        public final ReferenceStatus status;
        public final KOMEHiredUnitClass classification;
        public final String factionId;
        public final String strategicTileId;
        public final String reason;

        public DetachmentResolution(String detachmentId, ReferenceStatus status,
                KOMEHiredUnitClass classification, String factionId, String strategicTileId, String reason) {
            this.detachmentId = companyId(detachmentId);
            this.status = required(status, "Reference status");
            this.classification = classification;
            this.factionId = optional(factionId).isEmpty() ? "" : faction(factionId);
            this.strategicTileId = optional(strategicTileId).isEmpty() ? "" : tile(strategicTileId);
            this.reason = text(reason, "Resolution reason", 512);
            if (status == ReferenceStatus.RESOLVED
                    && (classification == null || this.factionId.isEmpty() || this.strategicTileId.isEmpty()))
                throw new IllegalArgumentException("Resolved detachment requires class, faction and strategic tile.");
        }
    }

    public static final class Context {
        public final long timestampMillis;
        public final String actor;
        public final String reason;

        public Context(long timestampMillis, String actor, String reason) {
            this.timestampMillis = nonnegative(timestampMillis, "Operation timestamp");
            this.actor = text(actor, "Actor", 128);
            this.reason = text(reason, "Reason", 512);
        }
    }

    public static final class ExpectedConflict {
        public final String conflictId;
        public final long revision;

        private ExpectedConflict(String conflictId, long revision) {
            this.conflictId = conflictId;
            this.revision = revision;
        }

        public static ExpectedConflict absent() { return new ExpectedConflict("", -1L); }
        public static ExpectedConflict at(String conflictId, long revision) {
            if (revision < 1L) throw new IllegalArgumentException("Expected revision must be positive.");
            return new ExpectedConflict(KOMEConflictIdAllocator.requireIdentity(conflictId), revision);
        }
        public boolean isAbsent() { return conflictId.isEmpty(); }
    }

    /**
     * Caller-validated commitment command. Later adapters must prove authoritative detachment
     * coherence and legal strategic arrival; a class assertion/order reference alone is NOT proof.
     * ORIGINAL_GARRISON is accepted only through the creation snapshot, never this late-arrival path.
     */
    public static final class CommitmentInput {
        public final String detachmentId;
        public final KOMEHiredUnitClass classification;
        public final KOMEConflictRecord.EntryOrigin origin;
        public final String movementOrderId;

        public CommitmentInput(String detachmentId, KOMEHiredUnitClass classification,
                KOMEConflictRecord.EntryOrigin origin, String movementOrderId) {
            this.detachmentId = companyId(detachmentId);
            this.classification = required(classification, "Classification");
            this.origin = required(origin, "Commitment origin");
            this.movementOrderId = optionalId(movementOrderId);
        }
    }

    /** Rights/provenance cohort at initialization only; not current troop membership authority. */
    public static final class GarrisonSeed {
        public final String detachmentId;
        public final KOMEHiredUnitClass classification;
        public final Set<UUID> originalMembers;

        public GarrisonSeed(String detachmentId, KOMEHiredUnitClass classification, Collection<UUID> members) {
            this.detachmentId = companyId(detachmentId);
            this.classification = required(classification, "Classification");
            this.originalMembers = uniqueSet(members);
        }
    }

    /** Explicit faction-bearing garrison fact supplied by the validated Encirclement creator. */
    public static final class GarrisonParticipantSeed {
        public final GarrisonSeed cohort;
        public final String factionId;

        public GarrisonParticipantSeed(GarrisonSeed cohort, String factionId) {
            this.cohort = required(cohort, "Garrison cohort");
            this.factionId = faction(factionId);
        }
    }

    /** Explicit authority against which live pairwise hostility must be proven. */
    public static final class ValidatedConflictAuthority {
        public final ConflictAuthorityKind kind;
        public final String factionId;

        public ValidatedConflictAuthority(ConflictAuthorityKind kind, String factionId) {
            this.kind = required(kind, "Conflict authority kind");
            this.factionId = faction(factionId);
        }

        public static ValidatedConflictAuthority defender(String factionId) {
            return new ValidatedConflictAuthority(
                ConflictAuthorityKind.VALIDATED_DEFENDER, factionId);
        }

        public static ValidatedConflictAuthority participant(String factionId) {
            return new ValidatedConflictAuthority(
                ConflictAuthorityKind.ACTIVE_PARTICIPANT, factionId);
        }
    }

    /**
     * Already-validated strategic-arrival fact. Constructing this value is not proof that an
     * entity walked, teleported or was observed in the tile; Phase 4's movement adapter will be
     * the production caller that proves the KOME arrival before invoking the service.
     */
    public static final class ValidatedCommitmentRequest {
        public final String destinationTileId;
        public final String detachmentId;
        public final String detachmentFactionId;
        public final ValidatedConflictAuthority authority;
        public final long acceptedAtMillis;
        public final KOMEConflictRecord.EntryOrigin origin;
        public final String movementOrderId;
        public final boolean qualifyingDefensiveContext;
        public final List<GarrisonParticipantSeed> originalGarrison;
        public final ExpectedConflict expectedConflict;

        public ValidatedCommitmentRequest(String destinationTileId, String detachmentId,
                String detachmentFactionId, ValidatedConflictAuthority authority,
                long acceptedAtMillis,
                KOMEConflictRecord.EntryOrigin origin, String movementOrderId,
                boolean qualifyingDefensiveContext,
                Collection<GarrisonParticipantSeed> originalGarrison,
                ExpectedConflict expectedConflict) {
            this.destinationTileId = tile(destinationTileId);
            this.detachmentId = companyId(detachmentId);
            this.detachmentFactionId = faction(detachmentFactionId);
            this.authority = required(authority, "Validated conflict authority");
            this.acceptedAtMillis = nonnegative(acceptedAtMillis, "Accepted-arrival timestamp");
            this.origin = required(origin, "Commitment origin");
            this.movementOrderId = optionalId(movementOrderId);
            required(originalGarrison, "Original-garrison snapshot");
            this.originalGarrison = Collections.unmodifiableList(
                new ArrayList<GarrisonParticipantSeed>(originalGarrison));
            this.expectedConflict = required(expectedConflict, "Expected conflict");
            if (origin == KOMEConflictRecord.EntryOrigin.ORIGINAL_GARRISON)
                throw new IllegalArgumentException(
                    "An arriving detachment cannot claim original-garrison origin.");
            if (!qualifyingDefensiveContext && !this.originalGarrison.isEmpty())
                throw new IllegalArgumentException(
                    "Only qualifying defensive context may carry an original garrison.");
            if (authority.kind == ConflictAuthorityKind.ACTIVE_PARTICIPANT
                    && (qualifyingDefensiveContext || !this.originalGarrison.isEmpty()))
                throw new IllegalArgumentException(
                    "Existing-conflict participation cannot redefine defensive context.");
            this.qualifyingDefensiveContext = qualifyingDefensiveContext;
        }
    }

    /** Explicit terminal commitment mutation; never inferred from an absent runtime reference. */
    public static final class ValidatedDepartureRequest {
        public final String tileId;
        public final String detachmentId;
        public final String factionId;
        public final ExpectedConflict expectedConflict;

        public ValidatedDepartureRequest(String tileId, String detachmentId, String factionId,
                ExpectedConflict expectedConflict) {
            this.tileId = tile(tileId);
            this.detachmentId = companyId(detachmentId);
            this.factionId = faction(factionId);
            this.expectedConflict = required(expectedConflict, "Expected conflict");
        }
    }

    /** AVAILABLE with an empty set is known zero; UNAVAILABLE is not that assertion. */
    public static final class ComplexCatalog {
        public final CatalogAvailability availability;
        public final Set<String> requiredComplexIds;

        public ComplexCatalog(CatalogAvailability availability, Collection<String> ids) {
            this.availability = required(availability, "Catalog availability");
            this.requiredComplexIds = ids(ids);
            if (availability == CatalogAvailability.UNAVAILABLE && !requiredComplexIds.isEmpty())
                throw new IllegalArgumentException("Unavailable catalog cannot assert a complete required-ID set.");
        }
        public static ComplexCatalog unavailable() {
            return new ComplexCatalog(CatalogAvailability.UNAVAILABLE, Collections.<String>emptySet());
        }
    }

    public static final class ReferenceDiagnostic {
        public final ReferenceKind kind;
        public final String referenceId;
        public final ReferenceStatus status;
        public final String reason;

        public ReferenceDiagnostic(ReferenceKind kind, String referenceId, ReferenceStatus status, String reason) {
            this.kind = required(kind, "Reference kind");
            this.referenceId = id(referenceId);
            this.status = required(status, "Reference status");
            this.reason = text(reason, "Diagnostic reason", 512);
        }
        public String key() { return kind.name() + ":" + referenceId; }
    }

    static String tile(String value) {
        String normalized = KOMEConquestTile.normalizeId(value);
        text(normalized, "Tile ID", 128);
        if (!KOMEConquestTile.isCanonicalTileId(normalized)) throw new IllegalArgumentException("Malformed tile ID.");
        return normalized; // Geometry existence/reference readiness belongs to the future resolver.
    }
    static String faction(String value) { return text(KOMEAlliance.normalizeFactionKey(value), "Faction ID", 128); }
    static String companyId(String value) {
        String normalized = text(value, "Detachment ID", 32);
        if (!normalized.matches("C[1-9][0-9]*")) throw new IllegalArgumentException("Canonical detachment ID required.");
        try { Long.parseLong(normalized.substring(1)); }
        catch (NumberFormatException invalid) { throw new IllegalArgumentException("Detachment sequence is out of range.", invalid); }
        return normalized;
    }
    static String id(String value) {
        String normalized = text(value, "Reference ID", 128);
        if (!normalized.matches("[A-Za-z0-9][A-Za-z0-9_.:-]*")) throw new IllegalArgumentException("Malformed reference ID.");
        return normalized;
    }
    static String optionalId(String value) { return optional(value).isEmpty() ? "" : id(value); }
    static String optional(String value) { return value == null ? "" : value.trim(); }
    static String text(String value, String name, int maximum) {
        String normalized = optional(value);
        if (normalized.isEmpty() || normalized.length() > maximum)
            throw new IllegalArgumentException(name + " is required and must be at most " + maximum + " characters.");
        return normalized;
    }
    static long nonnegative(long value, String name) {
        if (value < 0L) throw new IllegalArgumentException(name + " cannot be negative.");
        return value;
    }
    static <T> T required(T value, String name) {
        if (value == null) throw new IllegalArgumentException(name + " is required.");
        return value;
    }
    static <T> Set<T> uniqueSet(Collection<T> values) {
        required(values, "Collection");
        Set<T> copy = new LinkedHashSet<T>();
        for (T value : values) {
            if (value == null || !copy.add(value)) throw new IllegalArgumentException("Null or duplicate collection entry.");
        }
        return Collections.unmodifiableSet(copy);
    }
    static Set<String> ids(Collection<String> values) {
        required(values, "ID collection");
        Set<String> copy = new LinkedHashSet<String>();
        for (String value : values) if (!copy.add(id(value))) throw new IllegalArgumentException("Duplicate reference ID.");
        return Collections.unmodifiableSet(copy);
    }
}
