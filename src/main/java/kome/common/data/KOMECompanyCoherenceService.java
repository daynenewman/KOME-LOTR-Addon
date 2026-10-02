package kome.common.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Read-only assessment of whether a campaign company currently describes one coherent strategic
 * detachment. Physical observations are evidence only and never update persisted strategic state.
 */
public final class KOMECompanyCoherenceService {
    public static final KOMECompanyCoherenceService INSTANCE =
        new KOMECompanyCoherenceService(new ServerPhysicalObservationSource());

    public enum Status {
        COHERENT, UNKNOWN_PHYSICAL_STATE, INCOHERENT
    }

    public enum MovementPhase {
        NO_ROUTE, STATIONED_BETWEEN_HOPS, TRANSITION_OR_ARRIVAL, STRATEGIC_HOLD,
        TERMINAL_HISTORY, UNKNOWN
    }

    public enum PhysicalAgreement {
        AGREES, DISAGREES, UNKNOWN
    }

    public enum IssueCode {
        EMPTY_COMPANY,
        MISSING_UNIT_RECORD,
        ORDINARY_MEMBER,
        FARMHAND_MEMBER,
        NON_OFFENSIVE_MEMBER,
        RECORD_COMPANY_ID_MISMATCH,
        OWNER_MISMATCH,
        FACTION_MISMATCH,
        COMPANY_STRATEGIC_TILE_MISSING,
        MEMBER_STRATEGIC_TILE_MISSING,
        MIXED_MEMBER_STRATEGIC_TILES,
        COMPANY_MEMBER_STRATEGIC_TILE_MISMATCH,
        PHYSICAL_TILE_MISMATCH,
        DUPLICATE_MEMBER_ENTRY,
        DUPLICATE_COMPANY_MEMBERSHIP,
        COMPANY_MOVING_WITHOUT_ORDER,
        COMPANY_ORDER_MISSING,
        MOVEMENT_ORDER_COMPANY_MISMATCH,
        COMPANY_REFERENCES_TERMINAL_ORDER,
        MOVEMENT_ORDER_MEMBERSHIP_MISMATCH,
        RECORD_MOVEMENT_ORDER_MISMATCH,
        CACHED_TOTAL_POPULATION_MISMATCH,
        CACHED_MOUNTED_POPULATION_MISMATCH,
        CACHED_GROUND_POPULATION_MISMATCH
    }

    public static final class Issue {
        public final IssueCode code;
        public final UUID memberId;
        public final String detail;

        private Issue(IssueCode code, UUID memberId, String detail) {
            this.code = code;
            this.memberId = memberId;
            this.detail = detail == null ? "" : detail;
        }
    }

    public static final class MemberAssessment {
        public final UUID entityId;
        public final boolean recordExists;
        public final KOMEHiredUnitClass unitClass;
        public final boolean campaignUnit;
        public final UUID owner;
        public final String unitFaction;
        public final String expectedCompanyFaction;
        public final String recordCompanyId;
        public final String strategicTile;
        public final String movementOrderId;
        public final boolean farmhand;
        public final boolean offensive;
        public final String sourceTileId;
        public final String nativeLotrCompanyValue;
        public final KOMEServerTileAwareness.Availability physicalAvailability;
        public final KOMETileResolution.Status physicalResolutionStatus;
        public final String physicalTile;
        public final PhysicalAgreement physicalAgreement;
        public final long observationSession;
        public final long observationIncarnation;
        public final long observedTick;

        private MemberAssessment(UUID entityId, KOMEHiredUnitRecord record,
                String expectedCompanyFaction, PhysicalEvidence physical, String companyTile) {
            this.entityId = entityId;
            recordExists = record != null;
            unitClass = KOMEHiredUnitClassification.getUnitClass(record);
            campaignUnit = KOMEHiredUnitClassification.isCampaignUnit(record);
            owner = record == null ? null : record.owner;
            unitFaction = normalizeFaction(record == null ? "" : record.unitFaction);
            this.expectedCompanyFaction = normalizeFaction(expectedCompanyFaction);
            recordCompanyId = clean(record == null ? "" : record.companyId);
            strategicTile = normalizeTile(record == null ? "" : record.currentTile);
            movementOrderId = clean(record == null ? "" : record.movementOrderId);
            farmhand = record != null && record.farmhand;
            offensive = record != null && record.type == KOMEPopulationType.OFFENSIVE;
            sourceTileId = normalizeTile(record == null ? "" : record.sourceTileId);
            nativeLotrCompanyValue = clean(record == null ? "" : record.lotrCompanyValue);
            physicalAvailability = physical.availability;
            physicalResolutionStatus = physical.resolutionStatus;
            physicalTile = normalizeTile(physical.tileId);
            observationSession = physical.session;
            observationIncarnation = physical.incarnation;
            observedTick = physical.observedTick;
            String strategic = normalizeTile(companyTile);
            physicalAgreement = physicalTile.length() == 0 || strategic.length() == 0
                ? PhysicalAgreement.UNKNOWN
                : strategic.equals(physicalTile)
                    ? PhysicalAgreement.AGREES : PhysicalAgreement.DISAGREES;
        }
    }

    public static final class Assessment {
        public final Status status;
        public final String companyId;
        public final UUID owner;
        public final String faction;
        public final String strategicTile;
        public final String sourceTileId;
        public final String nativeLotrCompanyValue;
        public final String companyStatus;
        public final String movementOrderId;
        public final boolean routeOrderExists;
        public final String routeOrderStatus;
        public final String routeCurrentTile;
        public final String routeNextTile;
        public final String routeFinalTile;
        public final MovementPhase movementPhase;
        public final boolean stationedBetweenHopsIdentifiable;
        /** Atomic mutation happens inside one server-thread operation and has no separate flag. */
        public final boolean atomicTransitionProcessingIdentifiable;
        public final int memberCount;
        public final int cachedTotalPopulation;
        public final int cachedMountedPopulation;
        public final int cachedGroundPopulation;
        public final int authoritativeTotalPopulation;
        public final int authoritativeMountedPopulation;
        public final int authoritativeGroundPopulation;
        public final int physicallyConfirmedMembers;
        public final int physicallyUnknownMembers;
        public final int physicallyContradictoryMembers;
        public final List<MemberAssessment> members;
        public final List<Issue> issues;

        private Assessment(KOMEArmyCompany company, KOMEArmyMovementOrder order,
                MovementPhase movementPhase, int authoritativeTotalPopulation,
                int authoritativeMountedPopulation, int authoritativeGroundPopulation,
                int physicallyConfirmedMembers, int physicallyUnknownMembers,
                int physicallyContradictoryMembers, List<MemberAssessment> members,
                List<Issue> issues) {
            status = !issues.isEmpty() ? Status.INCOHERENT
                : physicallyUnknownMembers > 0 ? Status.UNKNOWN_PHYSICAL_STATE : Status.COHERENT;
            companyId = clean(company.id);
            owner = company.owner;
            faction = normalizeFaction(company.faction);
            strategicTile = normalizeTile(company.currentTile);
            sourceTileId = normalizeTile(company.sourceTileId);
            nativeLotrCompanyValue = clean(company.lotrCompanyValue);
            companyStatus = clean(company.status);
            movementOrderId = clean(company.movementOrderId);
            routeOrderExists = order != null && !isTerminal(order.status);
            routeOrderStatus = order == null ? "" : clean(order.status);
            routeCurrentTile = order == null ? "" : normalizeTile(order.currentTile);
            routeNextTile = order == null ? "" : normalizeTile(order.nextTile);
            routeFinalTile = order == null ? "" : normalizeTile(order.finalDestinationTile.length() == 0
                ? order.destinationTile : order.finalDestinationTile);
            this.movementPhase = movementPhase;
            stationedBetweenHopsIdentifiable =
                movementPhase == MovementPhase.STATIONED_BETWEEN_HOPS;
            atomicTransitionProcessingIdentifiable = false;
            memberCount = company.units.size();
            cachedTotalPopulation = company.totalPopulation;
            cachedMountedPopulation = company.mountedPopulation;
            cachedGroundPopulation = company.groundPopulation;
            this.authoritativeTotalPopulation = authoritativeTotalPopulation;
            this.authoritativeMountedPopulation = authoritativeMountedPopulation;
            this.authoritativeGroundPopulation = authoritativeGroundPopulation;
            this.physicallyConfirmedMembers = physicallyConfirmedMembers;
            this.physicallyUnknownMembers = physicallyUnknownMembers;
            this.physicallyContradictoryMembers = physicallyContradictoryMembers;
            this.members = Collections.unmodifiableList(new ArrayList<MemberAssessment>(members));
            this.issues = Collections.unmodifiableList(new ArrayList<Issue>(issues));
        }

        public MemberAssessment member(UUID entityId) {
            if (entityId == null) return null;
            for (MemberAssessment member : members) {
                if (entityId.equals(member.entityId)) return member;
            }
            return null;
        }
    }

    interface PhysicalObservationSource {
        PhysicalEvidence current(UUID entityId);
    }

    static final class PhysicalEvidence {
        final KOMEServerTileAwareness.Availability availability;
        final KOMETileResolution.Status resolutionStatus;
        final String tileId;
        final long session;
        final long incarnation;
        final long observedTick;

        private PhysicalEvidence(KOMEServerTileAwareness.Availability availability,
                KOMETileResolution.Status resolutionStatus, String tileId, long session,
                long incarnation, long observedTick) {
            this.availability = availability;
            this.resolutionStatus = resolutionStatus;
            this.tileId = clean(tileId);
            this.session = session;
            this.incarnation = incarnation;
            this.observedTick = observedTick;
        }

        static PhysicalEvidence unknown(KOMEServerTileAwareness.Availability availability) {
            return new PhysicalEvidence(availability, null, "", 0L, 0L, 0L);
        }

        static PhysicalEvidence resolved(String tileId) {
            return new PhysicalEvidence(KOMEServerTileAwareness.Availability.AVAILABLE,
                KOMETileResolution.Status.RESOLVED, tileId, 1L, 1L, 1L);
        }

        static PhysicalEvidence availableUnresolved(KOMETileResolution.Status status) {
            return new PhysicalEvidence(KOMEServerTileAwareness.Availability.AVAILABLE,
                status, "", 1L, 1L, 1L);
        }
    }

    private static final class ServerPhysicalObservationSource
            implements PhysicalObservationSource {
        @Override public PhysicalEvidence current(UUID entityId) {
            KOMEServerTileAwareness.Current current =
                KOMEServerTileAwareness.INSTANCE.current(entityId);
            if (current.availability != KOMEServerTileAwareness.Availability.AVAILABLE
                    || !current.observation().isPresent()) {
                return PhysicalEvidence.unknown(current.availability);
            }
            KOMEServerTileAwareness.Observation observation = current.observation().get();
            String tile = observation.location.status == KOMETileResolution.Status.RESOLVED
                ? observation.location.tileId : "";
            return new PhysicalEvidence(current.availability, observation.location.status, tile,
                observation.session, observation.incarnation, observation.observedTick);
        }
    }

    /** Physical evidence adapter; it is never a strategic-state writer. */
    private final PhysicalObservationSource physicalObservations;

    KOMECompanyCoherenceService(PhysicalObservationSource physicalObservations) {
        if (physicalObservations == null)
            throw new IllegalArgumentException("physicalObservations");
        this.physicalObservations = physicalObservations;
    }

    public Assessment assess(KOMEWorldData data, KOMEArmyCompany company) {
        if (data == null) throw new IllegalArgumentException("data");
        if (company == null) throw new IllegalArgumentException("company");
        return assessChecked(data, company);
    }

    private Assessment assessChecked(KOMEWorldData data, KOMEArmyCompany company) {
        List<Issue> issues = new ArrayList<Issue>();
        KOMEArmyMovementOrder order = referencedOrder(data, company, issues);
        assessCompanyBasics(company, issues);
        return assessMembers(data, company, order, issues);
    }

    private static void assessCompanyBasics(KOMEArmyCompany company, List<Issue> issues) {
        if (company.units.isEmpty()) {
            issue(issues, IssueCode.EMPTY_COMPANY, null,
                "Campaign Detachment " + clean(company.id) + " has no stored members.");
        }
        if (normalizeTile(company.currentTile).length() == 0) {
            issue(issues, IssueCode.COMPANY_STRATEGIC_TILE_MISSING, null,
                "Campaign Detachment " + clean(company.id)
                    + " has no strategic conquest tile.");
        }
    }

    private Assessment assessMembers(KOMEWorldData data, KOMEArmyCompany company,
            KOMEArmyMovementOrder order, List<Issue> issues) {
        List<MemberAssessment> members = new ArrayList<MemberAssessment>();
        Set<UUID> seen = new HashSet<UUID>();
        Set<String> memberTiles = new HashSet<String>();
        Totals totals = new Totals();
        int confirmed = 0;
        int unknown = 0;
        int contradictory = 0;
        for (UUID unitId : company.units) {
            if (unitId == null) continue;
            if (!seen.add(unitId)) {
                issue(issues, IssueCode.DUPLICATE_MEMBER_ENTRY, unitId,
                    "Unit " + shortId(unitId) + " appears more than once in this detachment.");
                continue;
            }
            KOMEHiredUnitRecord record = data.hiredUnits.get(unitId);
            MemberAssessment member = new MemberAssessment(unitId, record,
                expectedCompanyFaction(data, record), physicalObservations.current(unitId),
                company.currentTile);
            members.add(member);
            if (member.physicalAgreement == PhysicalAgreement.AGREES) confirmed++;
            else if (member.physicalAgreement == PhysicalAgreement.DISAGREES) contradictory++;
            else unknown++;
            assessMember(data, company, member, record, memberTiles, order, issues, totals);
        }
        if (memberTiles.size() > 1) {
            List<String> sorted = new ArrayList<String>(memberTiles);
            Collections.sort(sorted);
            issue(issues, IssueCode.MIXED_MEMBER_STRATEGIC_TILES, null,
                "Stored member strategic tiles are mixed: " + join(sorted) + ".");
        }
        assessOrderMembership(issues, company, order);
        compareCachedTotals(issues, company, totals.total, totals.mounted, totals.ground);
        return new Assessment(company, order, movementPhase(order), totals.total,
            totals.mounted, totals.ground, confirmed, unknown, contradictory, members, issues);
    }

    private static final class Totals {
        int total;
        int mounted;
        int ground;
    }

    private static void assessMember(KOMEWorldData data, KOMEArmyCompany company,
            MemberAssessment member, KOMEHiredUnitRecord record, Set<String> memberTiles,
            KOMEArmyMovementOrder order, List<Issue> issues, Totals totals) {
        assessMembershipIdentity(data, company, member, record, issues);
        assessMemberLocation(issues, member, memberTiles, company.currentTile);
        assessRecordMovementLink(issues, member, order, company.id);
        if (member.campaignUnit && !member.farmhand && member.offensive) {
            int cost = Math.max(0, record.cost);
            totals.total += cost;
            if (record.mounted) totals.mounted += cost;
            else totals.ground += cost;
        }
    }

    private static void assessMembershipIdentity(KOMEWorldData data, KOMEArmyCompany company,
            MemberAssessment member, KOMEHiredUnitRecord record, List<Issue> issues) {
        List<String> containingCompanies = containingCompanies(data, member.entityId);
        if (containingCompanies.size() > 1) {
            issue(issues, IssueCode.DUPLICATE_COMPANY_MEMBERSHIP, member.entityId,
                "Unit " + shortId(member.entityId) + " is stored in detachments "
                    + join(containingCompanies) + ".");
        }
        if (record == null) {
            issue(issues, IssueCode.MISSING_UNIT_RECORD, member.entityId,
                "Unit " + shortId(member.entityId)
                    + " has no tracked KOME hired-unit record.");
            return;
        }
        if (!member.campaignUnit) {
            issue(issues, IssueCode.ORDINARY_MEMBER, member.entityId,
                "Unit " + shortId(member.entityId) + " is " + member.unitClass
                    + ", not CAMPAIGN.");
        }
        if (member.farmhand) {
            issue(issues, IssueCode.FARMHAND_MEMBER, member.entityId,
                "Unit " + shortId(member.entityId)
                    + " is a farmhand and is not campaign-company eligible.");
        }
        if (!member.offensive) {
            issue(issues, IssueCode.NON_OFFENSIVE_MEMBER, member.entityId,
                "Unit " + shortId(member.entityId)
                    + " is not an offensive campaign unit.");
        }
        if (!clean(company.id).equals(member.recordCompanyId)) {
            issue(issues, IssueCode.RECORD_COMPANY_ID_MISMATCH, member.entityId,
                "Unit " + shortId(member.entityId) + " records detachment "
                    + display(member.recordCompanyId) + ", not "
                    + display(company.id) + ".");
        }
        if (!same(company.owner, member.owner)) {
            issue(issues, IssueCode.OWNER_MISMATCH, member.entityId,
                "Unit " + shortId(member.entityId)
                    + " owner does not match the detachment owner.");
        }
        String companyFaction = normalizeFaction(company.faction);
        if (member.expectedCompanyFaction.length() > 0
                && !member.expectedCompanyFaction.equals(companyFaction)) {
            issue(issues, IssueCode.FACTION_MISMATCH, member.entityId,
                "Unit " + shortId(member.entityId) + " resolves to strategic faction "
                    + member.expectedCompanyFaction + ", not "
                    + display(companyFaction) + ".");
        }
    }

    private static void assessMemberLocation(List<Issue> issues, MemberAssessment member,
            Set<String> memberTiles, String companyTileValue) {
        String companyTile = normalizeTile(companyTileValue);
        if (member.strategicTile.length() == 0) {
            issue(issues, IssueCode.MEMBER_STRATEGIC_TILE_MISSING, member.entityId,
                "Unit " + shortId(member.entityId) + " has no strategic conquest tile.");
        } else {
            memberTiles.add(member.strategicTile);
            if (!member.strategicTile.equals(companyTile)) {
                issue(issues, IssueCode.COMPANY_MEMBER_STRATEGIC_TILE_MISMATCH,
                    member.entityId, "Unit " + shortId(member.entityId)
                        + " records strategic tile " + member.strategicTile
                        + ", while the detachment records " + display(companyTile) + ".");
            }
        }
        if (member.physicalAgreement == PhysicalAgreement.DISAGREES) {
            issue(issues, IssueCode.PHYSICAL_TILE_MISMATCH, member.entityId,
                "Unit " + shortId(member.entityId) + " is physically observed in "
                    + member.physicalTile + " while the detachment records "
                    + companyTile + ".");
        }
    }

    private static KOMEArmyMovementOrder referencedOrder(KOMEWorldData data,
            KOMEArmyCompany company, List<Issue> issues) {
        String orderId = clean(company.movementOrderId);
        if (orderId.length() == 0) {
            if (KOMEArmyCompany.MOVING.equals(company.status)) {
                issue(issues, IssueCode.COMPANY_MOVING_WITHOUT_ORDER, null,
                    "Detachment status is moving but no movement order is linked.");
            }
            return null;
        }
        KOMEArmyMovementOrder order = data.armyMovements.get(orderId);
        if (order == null) {
            issue(issues, IssueCode.COMPANY_ORDER_MISSING, null,
                "Detachment references missing movement order " + orderId + ".");
            return null;
        }
        if (!clean(company.id).equals(clean(order.companyId))) {
            issue(issues, IssueCode.MOVEMENT_ORDER_COMPANY_MISMATCH, null,
                "Movement order " + orderId + " belongs to detachment "
                    + display(order.companyId) + ".");
        }
        if (isTerminal(order.status)) {
            issue(issues, IssueCode.COMPANY_REFERENCES_TERMINAL_ORDER, null,
                "Detachment still references terminal movement order " + orderId
                    + " (" + clean(order.status) + ").");
        }
        return order;
    }

    private static void assessRecordMovementLink(List<Issue> issues,
            MemberAssessment member, KOMEArmyMovementOrder order, String companyId) {
        String expectedOrder = order == null || isTerminal(order.status) ? "" : clean(order.id);
        if (!expectedOrder.equals(member.movementOrderId)) {
            issue(issues, IssueCode.RECORD_MOVEMENT_ORDER_MISMATCH, member.entityId,
                "Unit " + shortId(member.entityId) + " records movement order "
                    + display(member.movementOrderId) + ", while detachment "
                    + display(companyId) + " records " + display(expectedOrder) + ".");
            return;
        }
        if (order != null && expectedOrder.length() > 0
                && (!clean(companyId).equals(clean(order.companyId))
                    || !order.units.contains(member.entityId))) {
            issue(issues, IssueCode.RECORD_MOVEMENT_ORDER_MISMATCH, member.entityId,
                "Unit " + shortId(member.entityId)
                    + " is not coherently represented by movement order "
                    + expectedOrder + ".");
        }
    }

    private static void assessOrderMembership(List<Issue> issues,
            KOMEArmyCompany company, KOMEArmyMovementOrder order) {
        if (order == null || isTerminal(order.status)) return;
        Set<UUID> companyUnits = new HashSet<UUID>(company.units);
        Set<UUID> orderUnits = new HashSet<UUID>(order.units);
        companyUnits.remove(null);
        orderUnits.remove(null);
        if (!companyUnits.equals(orderUnits)) {
            issue(issues, IssueCode.MOVEMENT_ORDER_MEMBERSHIP_MISMATCH, null,
                "Movement order " + order.id + " contains " + orderUnits.size()
                    + " unique units but the detachment contains "
                    + companyUnits.size() + ".");
        }
    }

    private static void compareCachedTotals(List<Issue> issues, KOMEArmyCompany company,
            int total, int mounted, int ground) {
        if (company.totalPopulation != total) {
            issue(issues, IssueCode.CACHED_TOTAL_POPULATION_MISMATCH, null,
                "Cached total population is " + company.totalPopulation
                    + " but authoritative member total is " + total + ".");
        }
        if (company.mountedPopulation != mounted) {
            issue(issues, IssueCode.CACHED_MOUNTED_POPULATION_MISMATCH, null,
                "Cached mounted population is " + company.mountedPopulation
                    + " but authoritative member total is " + mounted + ".");
        }
        if (company.groundPopulation != ground) {
            issue(issues, IssueCode.CACHED_GROUND_POPULATION_MISMATCH, null,
                "Cached ground population is " + company.groundPopulation
                    + " but authoritative member total is " + ground + ".");
        }
    }

    private static MovementPhase movementPhase(KOMEArmyMovementOrder order) {
        if (order == null) return MovementPhase.NO_ROUTE;
        if (KOMEArmyMovementOrder.WAITING_NEXT_STEP.equals(order.status))
            return MovementPhase.STATIONED_BETWEEN_HOPS;
        if (KOMEArmyMovementOrder.MOVING.equals(order.status)
                || KOMEArmyMovementOrder.PENDING_SPAWN.equals(order.status)
                || KOMEArmyMovementOrder.SPAWNING.equals(order.status)
                || KOMEArmyMovementOrder.SPAWN_BLOCKED.equals(order.status)
                || KOMEArmyMovementOrder.RETREATING.equals(order.status))
            return MovementPhase.TRANSITION_OR_ARRIVAL;
        if (KOMEArmyMovementOrder.ACCESS_HALTED.equals(order.status)
                || KOMEArmyMovementOrder.HOLDING.equals(order.status)
                || KOMEArmyMovementOrder.WAR_ENDED_HALTED.equals(order.status))
            return MovementPhase.STRATEGIC_HOLD;
        if (isTerminal(order.status)) return MovementPhase.TERMINAL_HISTORY;
        return MovementPhase.UNKNOWN;
    }

    private static boolean isTerminal(String status) {
        return KOMEArmyMovementOrder.ARRIVED.equals(status)
            || KOMEArmyMovementOrder.CANCELLED.equals(status)
            || KOMEArmyMovementOrder.STOPPED.equals(status);
    }

    private static String expectedCompanyFaction(KOMEWorldData data,
            KOMEHiredUnitRecord record) {
        if (record == null) return "";
        return normalizeFaction("MILITARY_T3_STEWARDSHIP".equals(record.benefitSource)
            ? record.unitFaction : data.getPlayerFactionKey(record.owner));
    }

    private static List<String> containingCompanies(KOMEWorldData data, UUID unitId) {
        List<String> result = new ArrayList<String>();
        for (KOMEArmyCompany candidate : data.armyCompanies.values()) {
            if (candidate != null && candidate.units.contains(unitId)) {
                result.add(clean(candidate.id));
            }
        }
        Collections.sort(result);
        return result;
    }

    private static void issue(List<Issue> issues, IssueCode code, UUID memberId,
            String detail) {
        issues.add(new Issue(code, memberId, detail));
    }

    private static boolean same(Object first, Object second) {
        return first == null ? second == null : first.equals(second);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeTile(String value) {
        return KOMEConquestTile.normalizeId(value);
    }

    private static String normalizeFaction(String value) {
        return KOMEAlliance.normalizeFactionKey(value);
    }

    private static String shortId(UUID id) {
        return id == null ? "unknown" : id.toString().substring(0, 8);
    }

    private static String display(String value) {
        String clean = clean(value);
        return clean.length() == 0 ? "none" : clean;
    }

    private static String join(List<String> values) {
        StringBuilder out = new StringBuilder();
        for (String value : values) {
            if (out.length() > 0) out.append(", ");
            out.append(value);
        }
        return out.toString();
    }
}
