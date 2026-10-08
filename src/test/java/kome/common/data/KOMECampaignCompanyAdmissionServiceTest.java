package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;

public class KOMECampaignCompanyAdmissionServiceTest {
    private KOMEPopulationTestConfig movementConfig;
    @org.junit.Before public void movementConfig() throws Exception { movementConfig = new KOMEPopulationTestConfig(); }
    @org.junit.After public void closeMovementConfig() throws Exception { movementConfig.close(); }
    @Test public void newRecruitmentGrantsOnceAndAdmittingAnotherUnitDoesNotRefill() {
        Fixture f = new Fixture();
        KOMECampaignCompanyAdmissionService.Result first = f.admit(f.newHire(TILE), TILE);
        assertTrue(first.reason, first.success); assertEquals(1, first.company.movementAllowance);
        assertTrue(first.company.movementAllowanceInitialized);
        long boundary = first.company.movementBoundaryMillis;
        first.company.movementAllowance = 0;
        KOMECampaignCompanyAdmissionService.Result second = f.admit(f.newHire(TILE), TILE);
        assertTrue(second.reason, second.success); assertSame(first.company, second.company);
        assertEquals(0, second.company.movementAllowance); assertEquals(boundary, second.company.movementBoundaryMillis);
    }
    private static final String TILE = "T100";
    private static final String OTHER_TILE = "T101";
    @org.junit.Rule public final KOMETileTestResources tileGeometry =
        new KOMETileTestResources();

    @Test public void firstAndSubsequentNewDetachmentsUseMonotonicIds() {
        Fixture f = new Fixture();
        KOMEHiredUnitRecord first = f.newHire(TILE);
        long availableBeforeCreation = f.availablePopulation();
        KOMECampaignCompanyAdmissionService.Result firstResult = f.admit(first, TILE);
        assertTrue(firstResult.reason, firstResult.success);
        assertEquals(KOMECampaignCompanyAdmissionService.Code.CREATED_NEW, firstResult.code);
        assertEquals("C1", firstResult.company.id);
        assertEquals(KOMEArmyCompany.SOURCE_CAMPAIGN_RECRUITMENT,
            firstResult.company.source);
        assertEquals(KOMEArmyCompany.STATIONED, firstResult.company.status);
        assertEquals("", firstResult.company.movementOrderId);
        assertEquals(KOMEArmyCompany.CLEANUP_NONE,
            firstResult.company.withdrawalState);
        assertNull(firstResult.company.transferRecipient);
        assertEquals(TILE, firstResult.company.currentTile);
        assertEquals(1, firstResult.company.units.size());
        assertEquals(25, firstResult.company.totalPopulation);
        assertEquals(availableBeforeCreation, f.availablePopulation());

        KOMEHiredUnitRecord second = f.newHire(OTHER_TILE);
        KOMECampaignCompanyAdmissionService.Result secondResult = f.admit(second, OTHER_TILE);
        assertTrue(secondResult.reason, secondResult.success);
        assertEquals("C2", secondResult.company.id);
        assertEquals(3L, f.data.nextCompanySequence);
    }

    @Test public void deletedIdsAreNeverReused() {
        Fixture f = new Fixture();
        KOMEHiredUnitRecord first = f.newHire(TILE);
        KOMECampaignCompanyAdmissionService.Result created = f.admit(first, TILE);
        f.data.armyCompanies.remove(created.company.id);
        f.data.hiredUnits.remove(first.entity);

        KOMEHiredUnitRecord second = f.newHire(OTHER_TILE);
        assertEquals("C2", f.admit(second, OTHER_TILE).company.id);
    }

    @Test public void allocatorPersistsAcrossRestartWithoutSchemaBump() {
        KOMEWorldData data = new KOMEWorldData("allocator");
        data.initializeIntegratedWorld();
        assertEquals("C1", data.nextCampaignCompanyId());
        assertEquals("C2", data.nextCampaignCompanyId());
        NBTTagCompound saved = new NBTTagCompound();
        data.writeToNBT(saved);
        assertEquals(KOMEWorldData.KOME_DATA_SCHEMA_VERSION,
            saved.getInteger(KOMEWorldData.KOME_DATA_SCHEMA_KEY));
        assertEquals(3L, saved.getLong("NextCompanySequence"));

        KOMEWorldData restored = new KOMEWorldData("restored");
        restored.readFromNBT(saved);
        assertEquals("C3", restored.nextCampaignCompanyId());
    }

    @Test public void absentAllocatorInitializesAboveExistingCanonicalIds() {
        KOMEWorldData data = new KOMEWorldData("allocator");
        data.initializeIntegratedWorld();
        KOMEArmyCompany existing = new KOMEArmyCompany();
        existing.id = "C41";
        existing.owner = UUID.randomUUID();
        data.armyCompanies.put(existing.id, existing);
        NBTTagCompound saved = new NBTTagCompound();
        data.writeToNBT(saved);
        saved.removeTag("NextCompanySequence");

        KOMEWorldData restored = new KOMEWorldData("restored");
        restored.readFromNBT(saved);
        assertEquals(42L, restored.nextCompanySequence);
        assertEquals("C42", restored.nextCampaignCompanyId());
    }

    @Test public void exactlyOneCoherentLocalDetachmentReceivesHire() {
        Fixture f = new Fixture();
        KOMEArmyCompany local = f.existing("C7", TILE, "Native Alpha");
        KOMEHiredUnitRecord hire = f.newHire(TILE);
        hire.lotrCompanyValue = "Native Beta";
        hire.sourceTileId = "T900";
        hire.mounted = true;
        hire.cost = hire.baseCost = hire.populationSpent = 15;
        long available = f.availablePopulation();

        KOMECampaignCompanyAdmissionService.Result result = f.admit(hire, TILE);

        assertTrue(result.reason, result.success);
        assertEquals(KOMECampaignCompanyAdmissionService.Code.ADMITTED_EXISTING,
            result.code);
        assertSame(local, result.company);
        assertEquals(local.id, hire.companyId);
        assertTrue(local.units.contains(hire.entity));
        assertEquals(40, local.totalPopulation);
        assertEquals(15, local.mountedPopulation);
        assertEquals(25, local.groundPopulation);
        assertEquals("T900", hire.sourceTileId);
        assertEquals("Native Beta", hire.lotrCompanyValue);
        assertEquals(available, f.availablePopulation());
    }

    @Test public void sameSourceTileAndNativeSquadronEqualityDoNotForceRemoteReuse() {
        Fixture f = new Fixture();
        KOMEArmyCompany remote = f.existing("OLD", OTHER_TILE, "Native Alpha");
        remote.sourceTileId = TILE;
        KOMEHiredUnitRecord hire = f.newHire(TILE);
        hire.sourceTileId = TILE;
        hire.lotrCompanyValue = "Native Alpha";

        KOMECampaignCompanyAdmissionService.Result result = f.admit(hire, TILE);

        assertTrue(result.success);
        assertTrue(result.createdNew);
        assertNotEquals(remote.id, result.company.id);
        assertFalse(remote.units.contains(hire.entity));
        assertEquals(TILE, result.company.currentTile);
    }

    @Test public void positivePhysicalContradictionPreventsReuse() {
        Fixture f = new Fixture();
        KOMEArmyCompany contradicted = f.existing("OLD", TILE, "Native");
        UUID existingUnit = contradicted.units.get(0);
        f.physical.put(existingUnit,
            KOMECompanyCoherenceService.PhysicalEvidence.resolved(OTHER_TILE));
        KOMEHiredUnitRecord hire = f.newHire(TILE);

        KOMECampaignCompanyAdmissionService.Result result = f.admit(hire, TILE);

        assertTrue(result.success);
        assertTrue(result.createdNew);
        assertFalse(contradicted.units.contains(hire.entity));
    }

    @Test public void unknownPhysicalStateDoesNotBlockLocalReuse() {
        Fixture f = new Fixture();
        KOMEArmyCompany local = f.existing("OLD", TILE, "Native");
        UUID existingUnit = local.units.get(0);
        f.physical.put(existingUnit,
            KOMECompanyCoherenceService.PhysicalEvidence.unknown(
                KOMEServerTileAwareness.Availability.STALE_POSITION));
        KOMEHiredUnitRecord hire = f.newHire(TILE);

        KOMECampaignCompanyAdmissionService.Result result = f.admit(hire, TILE);

        assertTrue(result.reason, result.success);
        assertFalse(result.createdNew);
        assertSame(local, result.company);
    }

    @Test public void plannedRouteMakesCandidateIneligibleButDoesNotFailHire() {
        Fixture f = new Fixture();
        KOMEArmyCompany routed = f.existing("OLD", TILE, "Native");
        KOMEHiredUnitRecord member = f.data.hiredUnits.get(routed.units.get(0));
        KOMEArmyMovementOrder order = new KOMEArmyMovementOrder();
        order.id = "M1";
        order.companyId = routed.id;
        order.status = KOMEArmyMovementOrder.WAITING_NEXT_STEP;
        order.currentTile = TILE;
        order.nextTile = OTHER_TILE;
        order.finalDestinationTile = OTHER_TILE;
        order.units.add(member.entity);
        f.data.armyMovements.put(order.id, order);
        routed.status = KOMEArmyCompany.MOVING;
        routed.movementOrderId = order.id;
        member.movementOrderId = order.id;
        assertNotEquals(KOMECompanyCoherenceService.Status.INCOHERENT,
            f.coherence.assess(f.data, routed).status);

        KOMEHiredUnitRecord hire = f.newHire(TILE);
        KOMECampaignCompanyAdmissionService.Result result = f.admit(hire, TILE);

        assertTrue(result.reason, result.success);
        assertTrue(result.createdNew);
        assertFalse(routed.units.contains(hire.entity));
        assertEquals(2, f.data.armyCompanies.size());
    }

    @Test public void ambiguousLocalCandidatesCreateNewDetachment() {
        Fixture f = new Fixture();
        KOMEArmyCompany first = f.existing("C1", TILE, "One");
        KOMEArmyCompany second = f.existing("C2", TILE, "Two");
        KOMEHiredUnitRecord hire = f.newHire(TILE);

        KOMECampaignCompanyAdmissionService.Result result = f.admit(hire, TILE);

        assertTrue(result.success);
        assertTrue(result.createdNew);
        assertEquals(2, result.eligibleCandidateCount);
        assertEquals("C3", result.company.id);
        assertFalse(first.units.contains(hire.entity));
        assertFalse(second.units.contains(hire.entity));
    }

    @Test public void emptyHeldTransferAndWrongFactionDetachmentsAreNotReused() {
        Fixture f = new Fixture();
        KOMEArmyCompany empty = new KOMEArmyCompany();
        empty.id = "EMPTY"; empty.owner = f.owner; empty.ownerName = "Owner";
        empty.faction = empty.nativeFaction = "gondor";
        empty.currentTile = TILE; empty.sourceTileId = TILE;
        f.data.armyCompanies.put(empty.id, empty);
        KOMEArmyCompany held = f.existing("HELD", TILE, "Held");
        held.withdrawalState = KOMEArmyCompany.CLEANUP_WITHDRAWAL;
        KOMEArmyCompany transferring = f.existing("TRANSFER", TILE, "Transfer");
        transferring.transferRecipient = UUID.randomUUID();
        KOMEArmyCompany wrongFaction = f.existing("WRONG", TILE, "Wrong");
        wrongFaction.faction = wrongFaction.nativeFaction = "rohan";
        KOMEHiredUnitRecord hire = f.newHire(TILE);

        KOMECampaignCompanyAdmissionService.Result result = f.admit(hire, TILE);

        assertTrue(result.success);
        assertTrue(result.createdNew);
        assertEquals(0, result.eligibleCandidateCount);
    }

    @Test public void ordinaryFarmhandAndNonOffensiveRecordsCannotBeAdmitted() {
        Fixture ordinaryFixture = new Fixture();
        KOMEHiredUnitRecord ordinary = ordinaryFixture.newHire(TILE);
        ordinary.assignPersistedUnitClass(KOMEHiredUnitClass.ORDINARY);
        assertFalse(ordinaryFixture.admit(ordinary, TILE).success);

        Fixture farmhandFixture = new Fixture();
        KOMEHiredUnitRecord farmhand = farmhandFixture.newHire(TILE);
        farmhand.farmhand = true;
        assertFalse(farmhandFixture.admit(farmhand, TILE).success);

        Fixture defensiveFixture = new Fixture();
        KOMEHiredUnitRecord defensive = defensiveFixture.newHire(TILE);
        defensive.type = KOMEPopulationType.DEFENSIVE;
        assertFalse(defensiveFixture.admit(defensive, TILE).success);
    }

    @Test public void mutationFailureRestoresMembershipAndStillConsumesAllocatedId() {
        FailingCompositionData data = new FailingCompositionData();
        UUID owner = UUID.randomUUID();
        data.lastKnownPlayerFactions.put(owner, "gondor");
        KOMEHiredUnitRecord hire = campaignRecord(owner, "", TILE);
        data.hiredUnits.put(hire.entity, hire);
        KOMECampaignCompanyAdmissionService service = service(
            new HashMap<UUID, KOMECompanyCoherenceService.PhysicalEvidence>());

        KOMECampaignCompanyAdmissionService.Result result =
            service.admit(data, hire, "Owner", TILE);

        assertFalse(result.success);
        assertEquals(KOMECampaignCompanyAdmissionService.Code.MUTATION_FAILED,
            result.code);
        assertTrue(data.armyCompanies.isEmpty());
        assertEquals("", hire.companyId);
        assertEquals("", hire.companyName);
        assertEquals(2L, data.nextCompanySequence);
    }

    @Test public void legacyRebuildDoesNotRegroupCanonicalCampaignRecruitment() {
        Fixture f = new Fixture();
        KOMEHiredUnitRecord hire = f.newHire(TILE);
        KOMECampaignCompanyAdmissionService.Result admitted = f.admit(hire, TILE);
        String stableId = admitted.company.id;

        f.data.rebuildArmyCompaniesForPlayer(f.owner);

        assertEquals(stableId, hire.companyId);
        assertSame(admitted.company, f.data.armyCompanies.get(stableId));
        assertTrue(admitted.company.units.contains(hire.entity));
        assertEquals(1, f.data.armyCompanies.size());
    }

    private static final class Fixture {
        final KOMEWorldData data = new KOMEWorldData("admission");
        final UUID owner = UUID.randomUUID();
        final Map<UUID, KOMECompanyCoherenceService.PhysicalEvidence> physical =
            new HashMap<UUID, KOMECompanyCoherenceService.PhysicalEvidence>();
        final KOMECompanyCoherenceService coherence = coherence(physical);
        final KOMECampaignCompanyAdmissionService service =
            new KOMECampaignCompanyAdmissionService(coherence);

        Fixture() {
            data.lastKnownPlayerFactions.put(owner, "gondor");
            data.grantFactionPopulationCenti("gondor", 100000L);
        }

        KOMEHiredUnitRecord newHire(String tile) {
            KOMEHiredUnitRecord record = campaignRecord(owner, "", tile);
            data.hiredUnits.put(record.entity, record);
            return record;
        }

        KOMEArmyCompany existing(String id, String tile, String lotrCompany) {
            KOMEArmyCompany company = new KOMEArmyCompany();
            company.id = id;
            company.owner = owner;
            company.ownerName = "Owner";
            company.faction = company.nativeFaction = "gondor";
            company.currentTile = tile;
            company.sourceTileId = "T900";
            company.status = KOMEArmyCompany.STATIONED;
            company.lotrCompanyValue = lotrCompany;
            KOMEHiredUnitRecord member = campaignRecord(owner, id, tile);
            member.lotrCompanyValue = lotrCompany;
            data.hiredUnits.put(member.entity, member);
            company.units.add(member.entity);
            company.totalPopulation = member.cost;
            company.groundPopulation = member.cost;
            data.armyCompanies.put(company.id, company);
            return company;
        }

        KOMECampaignCompanyAdmissionService.Result admit(
                KOMEHiredUnitRecord record, String tile) {
            return service.admit(data, record, "Owner", tile);
        }

        long availablePopulation() {
            return KOMEPopulationService.getAvailablePopulationCenti(data, "gondor");
        }
    }

    private static KOMECampaignCompanyAdmissionService service(
            Map<UUID, KOMECompanyCoherenceService.PhysicalEvidence> physical) {
        return new KOMECampaignCompanyAdmissionService(coherence(physical));
    }

    private static KOMECompanyCoherenceService coherence(
            Map<UUID, KOMECompanyCoherenceService.PhysicalEvidence> physical) {
        return new KOMECompanyCoherenceService(id -> physical.containsKey(id)
            ? physical.get(id)
            : KOMECompanyCoherenceService.PhysicalEvidence.unknown(
                KOMEServerTileAwareness.Availability.NOT_TRACKED));
    }

    private static KOMEHiredUnitRecord campaignRecord(UUID owner, String companyId,
            String tile) {
        KOMEHiredUnitRecord record = new KOMEHiredUnitRecord();
        record.entity = UUID.randomUUID();
        record.owner = owner;
        record.sourcePlayer = owner;
        record.companyId = companyId;
        record.currentTile = tile;
        record.sourceTileId = "T900";
        record.type = KOMEPopulationType.OFFENSIVE;
        record.cost = record.baseCost = record.populationSpent = 25;
        record.populationOwningFaction = "gondor";
        record.sourceFaction = "gondor";
        record.unitFaction = "gondor";
        KOMEHiredUnitClassification.assignForCampaignWorkflow(record);
        return record;
    }

    private static final class FailingCompositionData extends KOMEWorldData {
        FailingCompositionData() { super("failing-admission"); }
        @Override void recalculateCampaignCompanyComposition(KOMEArmyCompany company) {
            super.recalculateCampaignCompanyComposition(company);
            throw new IllegalStateException("simulated cached-total failure");
        }
    }
}
