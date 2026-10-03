package kome.common.data;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.Test;

import static kome.common.data.KOMECompanyCoherenceService.*;
import static org.junit.Assert.*;

public class KOMECompanyCoherenceServiceTest {
    @Test public void coherentStationedCampaignCompanyIsPhysicallyConfirmed() {
        Fixture f = new Fixture();
        Assessment result = f.assess();

        assertEquals(Status.COHERENT, result.status);
        assertTrue(result.issues.isEmpty());
        assertEquals("T100", result.strategicTile);
        assertEquals(1, result.physicallyConfirmedMembers);
        assertEquals(0, result.physicallyUnknownMembers);
        assertEquals(25, result.authoritativeTotalPopulation);
        assertEquals(MovementPhase.NO_ROUTE, result.movementPhase);
    }

    @Test public void emptyCompanyAndMissingStrategicTileAreIncoherent() {
        Fixture f = new Fixture();
        f.company.units.clear();
        f.company.currentTile = "";
        f.company.totalPopulation = 0;
        f.company.groundPopulation = 0;

        Assessment result = f.assess();
        assertEquals(Status.INCOHERENT, result.status);
        assertTrue(hasIssue(result, IssueCode.EMPTY_COMPANY));
        assertTrue(hasIssue(result, IssueCode.COMPANY_STRATEGIC_TILE_MISSING));
    }

    @Test public void ordinaryAndMissingRecordsAreIncoherent() {
        Fixture ordinary = new Fixture();
        ordinary.record.assignPersistedUnitClass(KOMEHiredUnitClass.ORDINARY);
        Assessment ordinaryResult = ordinary.assess();
        assertEquals(Status.INCOHERENT, ordinaryResult.status);
        assertTrue(hasIssue(ordinaryResult, IssueCode.ORDINARY_MEMBER));

        Fixture missing = new Fixture();
        missing.data.hiredUnits.remove(missing.record.entity);
        missing.company.totalPopulation = 0;
        missing.company.groundPopulation = 0;
        Assessment missingResult = missing.assess();
        assertEquals(Status.INCOHERENT, missingResult.status);
        assertTrue(hasIssue(missingResult, IssueCode.MISSING_UNIT_RECORD));
    }

    @Test public void membershipOwnerFactionAndEligibilityMismatchesAreDetailed() {
        Fixture f = new Fixture();
        f.record.companyId = "C-other";
        f.record.owner = UUID.randomUUID();
        f.data.lastKnownPlayerFactions.put(f.record.owner, "rohan");
        f.record.farmhand = true;
        f.record.type = KOMEPopulationType.DEFENSIVE;
        f.company.totalPopulation = 0;
        f.company.groundPopulation = 0;

        Assessment result = f.assess();
        assertTrue(hasIssue(result, IssueCode.RECORD_COMPANY_ID_MISMATCH));
        assertTrue(hasIssue(result, IssueCode.OWNER_MISMATCH));
        assertTrue(hasIssue(result, IssueCode.FACTION_MISMATCH));
        assertTrue(hasIssue(result, IssueCode.FARMHAND_MEMBER));
        assertTrue(hasIssue(result, IssueCode.NON_OFFENSIVE_MEMBER));
    }

    @Test public void mixedMemberTilesAndCompanyTileDisagreementAreIncoherent() {
        Fixture f = new Fixture();
        KOMEHiredUnitRecord second = campaignRecord(f.owner, f.company.id, "T101");
        f.data.hiredUnits.put(second.entity, second);
        f.company.units.add(second.entity);
        f.company.totalPopulation = 50;
        f.company.groundPopulation = 50;
        f.physical.put(second.entity, PhysicalEvidence.resolved("T101"));

        Assessment result = f.assess();
        assertTrue(hasIssue(result, IssueCode.MIXED_MEMBER_STRATEGIC_TILES));
        assertTrue(hasIssue(result, IssueCode.COMPANY_MEMBER_STRATEGIC_TILE_MISMATCH));
        assertTrue(hasIssue(result, IssueCode.PHYSICAL_TILE_MISMATCH));

        Fixture single = new Fixture();
        single.record.currentTile = "T101";
        assertTrue(hasIssue(single.assess(),
            IssueCode.COMPANY_MEMBER_STRATEGIC_TILE_MISMATCH));
    }

    @Test public void positivePhysicalEvidenceConfirmsOrContradictsStrategicTile() {
        Fixture agrees = new Fixture();
        assertEquals(PhysicalAgreement.AGREES,
            agrees.assess().member(agrees.record.entity).physicalAgreement);

        Fixture disagrees = new Fixture();
        disagrees.physical.put(disagrees.record.entity,
            PhysicalEvidence.resolved("T101"));
        Assessment result = disagrees.assess();
        assertEquals(Status.INCOHERENT, result.status);
        assertEquals(1, result.physicallyContradictoryMembers);
        assertTrue(hasIssue(result, IssueCode.PHYSICAL_TILE_MISMATCH));
    }

    @Test public void unavailablePendingStaleAndUnresolvedPhysicalEvidenceStayUnknown() {
        KOMEServerTileAwareness.Availability[] unavailable = {
            KOMEServerTileAwareness.Availability.PENDING_SAMPLE,
            KOMEServerTileAwareness.Availability.NOT_TRACKED,
            KOMEServerTileAwareness.Availability.SERVER_STOPPED,
            KOMEServerTileAwareness.Availability.STALE_POSITION,
            KOMEServerTileAwareness.Availability.STALE_GEOMETRY
        };
        for (KOMEServerTileAwareness.Availability availability : unavailable) {
            Fixture f = new Fixture();
            f.physical.put(f.record.entity, PhysicalEvidence.unknown(availability));
            Assessment result = f.assess();
            assertEquals(availability.name(), Status.UNKNOWN_PHYSICAL_STATE, result.status);
            assertFalse(hasIssue(result, IssueCode.PHYSICAL_TILE_MISMATCH));
            assertEquals(PhysicalAgreement.UNKNOWN,
                result.member(f.record.entity).physicalAgreement);
        }

        Fixture unresolved = new Fixture();
        unresolved.physical.put(unresolved.record.entity,
            PhysicalEvidence.availableUnresolved(KOMETileResolution.Status.IN_BOUNDS_GAP));
        Assessment unresolvedResult = unresolved.assess();
        assertEquals(Status.UNKNOWN_PHYSICAL_STATE, unresolvedResult.status);
        assertFalse(hasIssue(unresolvedResult, IssueCode.PHYSICAL_TILE_MISMATCH));
    }

    @Test public void duplicateMembershipAndStaleCachedTotalsAreReportedOnly() {
        Fixture f = new Fixture();
        KOMEArmyCompany duplicate = new KOMEArmyCompany();
        duplicate.id = "C2";
        duplicate.units.add(f.record.entity);
        f.data.armyCompanies.put(duplicate.id, duplicate);
        f.company.totalPopulation = 99;
        f.company.mountedPopulation = 7;
        f.company.groundPopulation = 92;

        Assessment result = f.assess();
        assertTrue(hasIssue(result, IssueCode.DUPLICATE_COMPANY_MEMBERSHIP));
        assertTrue(hasIssue(result, IssueCode.CACHED_TOTAL_POPULATION_MISMATCH));
        assertTrue(hasIssue(result, IssueCode.CACHED_MOUNTED_POPULATION_MISMATCH));
        assertTrue(hasIssue(result, IssueCode.CACHED_GROUND_POPULATION_MISMATCH));
        assertEquals(25, result.authoritativeTotalPopulation);
    }

    @Test public void validFutureRouteAndWaitingBetweenHopsRemainCoherent() {
        Fixture f = new Fixture();
        KOMEArmyMovementOrder order = route(f, KOMEArmyMovementOrder.WAITING_NEXT_STEP);

        Assessment result = f.assess();
        assertEquals(Status.COHERENT, result.status);
        assertTrue(result.routeOrderExists);
        assertEquals(MovementPhase.STATIONED_BETWEEN_HOPS, result.movementPhase);
        assertTrue(result.stationedBetweenHopsIdentifiable);
        assertFalse(result.atomicTransitionProcessingIdentifiable);
        assertFalse(hasIssue(result, IssueCode.RECORD_MOVEMENT_ORDER_MISMATCH));
        assertFalse(hasIssue(result, IssueCode.MOVEMENT_ORDER_MEMBERSHIP_MISMATCH));
        assertEquals("T103", result.routeFinalTile);

        order.status = KOMEArmyMovementOrder.MOVING;
        f.physical.put(f.record.entity, PhysicalEvidence.unknown(
            KOMEServerTileAwareness.Availability.NOT_TRACKED));
        Assessment transition = f.assess();
        assertEquals(Status.UNKNOWN_PHYSICAL_STATE, transition.status);
        assertEquals(MovementPhase.TRANSITION_OR_ARRIVAL, transition.movementPhase);
        assertTrue(transition.issues.isEmpty());
    }

    @Test public void genuineMovementLinkContradictionsAreIncoherent() {
        Fixture missing = new Fixture();
        missing.company.status = KOMEArmyCompany.MOVING;
        missing.company.movementOrderId = "M-missing";
        missing.record.movementOrderId = "M-other";
        Assessment missingResult = missing.assess();
        assertTrue(hasIssue(missingResult, IssueCode.COMPANY_ORDER_MISSING));
        assertTrue(hasIssue(missingResult, IssueCode.RECORD_MOVEMENT_ORDER_MISMATCH));

        Fixture wrongCompany = new Fixture();
        KOMEArmyMovementOrder order =
            route(wrongCompany, KOMEArmyMovementOrder.WAITING_NEXT_STEP);
        order.companyId = "C-other";
        order.units.clear();
        Assessment wrongResult = wrongCompany.assess();
        assertTrue(hasIssue(wrongResult, IssueCode.MOVEMENT_ORDER_COMPANY_MISMATCH));
        assertTrue(hasIssue(wrongResult, IssueCode.MOVEMENT_ORDER_MEMBERSHIP_MISMATCH));
        assertTrue(hasIssue(wrongResult, IssueCode.RECORD_MOVEMENT_ORDER_MISMATCH));
    }

    @Test public void nativeSquadronAndSourceTileRemainNonAuthoritativeMetadata() {
        Fixture f = new Fixture();
        f.company.lotrCompanyValue = "Native Alpha";
        f.record.lotrCompanyValue = "Native Beta";
        f.company.sourceTileId = "T900";
        f.record.sourceTileId = "T900";
        Assessment coherent = f.assess();
        assertEquals(Status.COHERENT, coherent.status);
        assertEquals("Native Alpha", coherent.nativeLotrCompanyValue);
        assertEquals("Native Beta",
            coherent.member(f.record.entity).nativeLotrCompanyValue);

        f.record.currentTile = "T101";
        Assessment incoherent = f.assess();
        assertEquals(Status.INCOHERENT, incoherent.status);
        assertTrue(hasIssue(incoherent,
            IssueCode.COMPANY_MEMBER_STRATEGIC_TILE_MISMATCH));
    }

    @Test public void assessmentIsReadOnlyAndResultsAreImmutable() {
        Fixture f = new Fixture();
        String recordBefore = f.record.writeToNBT().toString();
        String companyBefore = f.company.writeToNBT().toString();
        f.data.setDirty(false);

        Assessment result = f.assess();

        assertEquals(recordBefore, f.record.writeToNBT().toString());
        assertEquals(companyBefore, f.company.writeToNBT().toString());
        assertFalse(f.data.isDirty());
        assertTrue(f.data.centralAudit.isEmpty());
        assertSame(f.record, f.data.hiredUnits.get(f.record.entity));
        try {
            result.members.clear();
            fail("Member results must be immutable");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
        try {
            result.issues.clear();
            fail("Issue results must be immutable");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }

    private static KOMEArmyMovementOrder route(Fixture f, String status) {
        KOMEArmyMovementOrder order = new KOMEArmyMovementOrder();
        order.id = "M1";
        order.companyId = f.company.id;
        order.owner = f.owner;
        order.status = status;
        order.currentTile = "T100";
        order.nextTile = "T101";
        order.finalDestinationTile = "T103";
        order.routeTiles.add("T100");
        order.routeTiles.add("T101");
        order.routeTiles.add("T102");
        order.routeTiles.add("T103");
        order.units.add(f.record.entity);
        f.data.armyMovements.put(order.id, order);
        f.company.status = KOMEArmyCompany.MOVING;
        f.company.movementOrderId = order.id;
        f.record.movementOrderId = order.id;
        return order;
    }

    private static final class Fixture {
        final KOMEWorldData data = new KOMEWorldData("coherence");
        final UUID owner = UUID.randomUUID();
        final KOMEArmyCompany company = new KOMEArmyCompany();
        final KOMEHiredUnitRecord record;
        final Map<UUID, PhysicalEvidence> physical = new HashMap<UUID, PhysicalEvidence>();
        final KOMECompanyCoherenceService service =
            new KOMECompanyCoherenceService(id -> physical.containsKey(id)
                ? physical.get(id)
                : PhysicalEvidence.unknown(KOMEServerTileAwareness.Availability.NOT_TRACKED));

        Fixture() {
            data.lastKnownPlayerFactions.put(owner, "gondor");
            company.id = "C1";
            company.owner = owner;
            company.ownerName = "Owner";
            company.faction = "gondor";
            company.currentTile = "T100";
            company.sourceTileId = "T900";
            company.status = KOMEArmyCompany.STATIONED;
            record = campaignRecord(owner, company.id, "T100");
            data.hiredUnits.put(record.entity, record);
            data.armyCompanies.put(company.id, company);
            company.units.add(record.entity);
            company.totalPopulation = 25;
            company.groundPopulation = 25;
            physical.put(record.entity, PhysicalEvidence.resolved("T100"));
            data.setDirty(false);
        }

        Assessment assess() {
            return service.assess(data, company);
        }
    }

    private static KOMEHiredUnitRecord campaignRecord(UUID owner, String companyId,
            String tile) {
        KOMEHiredUnitRecord record = new KOMEHiredUnitRecord();
        record.entity = UUID.randomUUID();
        record.owner = owner;
        record.type = KOMEPopulationType.OFFENSIVE;
        record.cost = record.baseCost = record.populationSpent = 25;
        record.companyId = companyId;
        record.currentTile = tile;
        record.sourceTileId = "T900";
        record.unitFaction = "gondor";
        record.populationOwningFaction = "gondor";
        KOMEHiredUnitClassification.assignForCampaignWorkflow(record);
        return record;
    }

    private static boolean hasIssue(Assessment assessment, IssueCode code) {
        for (Issue issue : assessment.issues) if (issue.code == code) return true;
        return false;
    }
}
