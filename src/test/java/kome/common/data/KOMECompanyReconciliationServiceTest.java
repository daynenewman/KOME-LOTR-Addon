package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;

public class KOMECompanyReconciliationServiceTest {
    @org.junit.Rule public final KOMETileTestResources geometry =
        new KOMETileTestResources();

    @Test public void canonicalDetachmentsSurviveRestartWithoutSourceOrSquadronRegrouping() {
        Fixture f = new Fixture();
        f.data.initializeIntegratedWorld();
        KOMEHiredUnitRecord first = f.record("T100");
        KOMEHiredUnitRecord second = f.record("T100");
        first.sourceTileId = second.sourceTileId = "T900";
        first.lotrCompanyValue = second.lotrCompanyValue = "Native Alpha";
        KOMEArmyCompany c1 = f.company("C1", "T100");
        KOMEArmyCompany c2 = f.company("C2", "T100");
        f.attach(c1, first);
        f.attach(c2, second);

        NBTTagCompound saved = new NBTTagCompound();
        f.data.writeToNBT(saved);
        KOMEWorldData restored = new KOMEWorldData("restored");
        restored.readFromNBT(saved);

        assertEquals("C1", restored.hiredUnits.get(first.entity).companyId);
        assertEquals("C2", restored.hiredUnits.get(second.entity).companyId);
        assertTrue(restored.armyCompanies.get("C1").units.contains(first.entity));
        assertTrue(restored.armyCompanies.get("C2").units.contains(second.entity));
        assertEquals(2, restored.armyCompanies.size());
    }

    @Test public void changingNativeSquadronMetadataNeverDetachesOrReassigns() {
        Fixture f = new Fixture();
        KOMEHiredUnitRecord record = f.record("T100");
        KOMEArmyCompany company = f.company("C1", "T100");
        f.attach(company, record);
        record.lotrCompanyValue = "Changed Native Squadron";
        company.lotrCompanyValue = "Original Native Squadron";

        KOMECompanyReconciliationService.Result result = f.reconcile();

        assertEquals("C1", record.companyId);
        assertTrue(company.units.contains(record.entity));
        assertEquals("Changed Native Squadron", record.lotrCompanyValue);
        assertEquals(1, result.retainedMemberships);
    }

    @Test public void invalidMembersAreRemovedWithoutChangingClassification() {
        Fixture f = new Fixture();
        KOMEArmyCompany company = f.company("C1", "T100");
        KOMEHiredUnitRecord ordinary = f.record("T100");
        ordinary.assignPersistedUnitClass(KOMEHiredUnitClass.ORDINARY);
        KOMEHiredUnitRecord farmhand = f.record("T100");
        farmhand.farmhand = true;
        KOMEHiredUnitRecord defensive = f.record("T100");
        defensive.type = KOMEPopulationType.DEFENSIVE;
        UUID missing = UUID.randomUUID();
        for (KOMEHiredUnitRecord record : new KOMEHiredUnitRecord[] {ordinary, farmhand, defensive}) {
            record.companyId = company.id;
            f.data.hiredUnits.put(record.entity, record);
            company.units.add(record.entity);
        }
        company.units.add(missing);

        KOMECompanyReconciliationService.Result result = f.reconcile();

        assertEquals(4, result.invalidMembersRemoved);
        assertFalse(f.data.armyCompanies.containsKey("C1"));
        assertEquals("", ordinary.companyId);
        assertEquals("", farmhand.companyId);
        assertEquals("", defensive.companyId);
        assertEquals(KOMEHiredUnitClass.ORDINARY,
            KOMEHiredUnitClassification.getUnitClass(ordinary));
        assertTrue(KOMEHiredUnitClassification.isCampaignUnit(farmhand));
        assertTrue(KOMEHiredUnitClassification.isCampaignUnit(defensive));
    }

    @Test public void explicitValidRecordCompanyWinsDuplicateMembership() {
        Fixture f = new Fixture();
        KOMEHiredUnitRecord record = f.record("T100");
        KOMEArmyCompany c1 = f.company("C1", "T100");
        KOMEArmyCompany c2 = f.company("C2", "T100");
        f.attach(c1, record);
        c2.units.add(record.entity);

        KOMECompanyReconciliationService.Result result = f.reconcile();

        assertEquals("C1", record.companyId);
        assertTrue(c1.units.contains(record.entity));
        assertFalse(f.data.armyCompanies.containsKey("C2"));
        assertTrue(result.duplicateMembershipsRemoved >= 1);
    }

    @Test public void ambiguousDuplicateMembershipFailsClosed() {
        Fixture f = new Fixture();
        KOMEHiredUnitRecord record = f.record("T100");
        record.companyId = "";
        f.data.hiredUnits.put(record.entity, record);
        KOMEArmyCompany c1 = f.company("C1", "T100");
        KOMEArmyCompany c2 = f.company("C2", "T100");
        c1.units.add(record.entity);
        c2.units.add(record.entity);

        KOMECompanyReconciliationService.Result result = f.reconcile();

        assertEquals("", record.companyId);
        assertTrue(f.data.armyCompanies.isEmpty());
        assertEquals(1, result.ambiguousUnitsUnassigned);
        assertTrue(KOMEHiredUnitClassification.isCampaignUnit(record));
    }

    @Test public void soleValidCompanySideMembershipRepairsRecordLink() {
        Fixture f = new Fixture();
        KOMEHiredUnitRecord record = f.record("T100");
        record.companyId = "";
        f.data.hiredUnits.put(record.entity, record);
        KOMEArmyCompany company = f.company("C1", "T100");
        company.units.add(record.entity);

        KOMECompanyReconciliationService.Result result = f.reconcile();

        assertEquals("C1", record.companyId);
        assertTrue(company.units.contains(record.entity));
        assertEquals(1, result.recordLinksRepaired);
    }

    @Test public void orphanedCampaignUnitDoesNotJoinMatchingSourceOrLocalCompany() {
        Fixture f = new Fixture();
        KOMEHiredUnitRecord member = f.record("T100");
        KOMEArmyCompany company = f.company("C1", "T100");
        f.attach(company, member);
        KOMEHiredUnitRecord orphan = f.record("T100");
        orphan.sourceTileId = member.sourceTileId;
        orphan.lotrCompanyValue = member.lotrCompanyValue = "Same Native Squadron";
        f.data.hiredUnits.put(orphan.entity, orphan);

        f.reconcile();

        assertEquals("", orphan.companyId);
        assertFalse(company.units.contains(orphan.entity));
        assertEquals(1, company.units.size());
    }

    @Test public void mixedStrategicTilesRemainDiagnosableAndAreNeverNormalized() {
        Fixture f = new Fixture();
        KOMEHiredUnitRecord first = f.record("T100");
        KOMEHiredUnitRecord second = f.record("T101");
        KOMEArmyCompany company = f.company("C1", "T100");
        f.attach(company, first);
        f.attach(company, second);

        KOMECompanyReconciliationService.Result result = f.reconcile();

        assertEquals("T100", company.currentTile);
        assertEquals("T100", first.currentTile);
        assertEquals("T101", second.currentTile);
        assertEquals("C1", first.companyId);
        assertEquals("C1", second.companyId);
        assertEquals(1, result.incoherentCompanies);
    }

    @Test public void physicalContradictionIsReportedWithoutStrategicRewrite() {
        Fixture f = new Fixture();
        KOMEHiredUnitRecord record = f.record("T100");
        KOMEArmyCompany company = f.company("C1", "T100");
        f.attach(company, record);
        f.physical.put(record.entity,
            KOMECompanyCoherenceService.PhysicalEvidence.resolved("T101"));

        KOMECompanyReconciliationService.Result result = f.reconcile();

        assertEquals("T100", record.currentTile);
        assertEquals("T100", company.currentTile);
        assertEquals("C1", record.companyId);
        assertEquals(1, result.physicalContradictions);
        assertEquals(1, result.incoherentCompanies);
    }

    @Test public void unknownPhysicalStateDoesNotCauseFalseReassignment() {
        Fixture f = new Fixture();
        KOMEHiredUnitRecord record = f.record("T100");
        KOMEArmyCompany company = f.company("C1", "T100");
        f.attach(company, record);

        KOMECompanyReconciliationService.Result result = f.reconcile();

        assertEquals("C1", record.companyId);
        assertTrue(company.units.contains(record.entity));
        assertEquals(0, result.physicalContradictions);
        assertEquals(1, result.unknownPhysicalMembers);
    }

    @Test public void emptyDetachmentIsRemovedAndStableIdRemainsConsumed() {
        Fixture f = new Fixture();
        assertEquals("C1", f.data.nextCampaignCompanyId());
        KOMEArmyCompany empty = f.company("C1", "T100");

        KOMECompanyReconciliationService.Result result = f.reconcile();

        assertEquals(1, result.emptyCompaniesRemoved);
        assertFalse(f.data.armyCompanies.containsKey("C1"));
        assertEquals("C2", f.data.nextCampaignCompanyId());
    }

    @Test public void cachedTotalsRecomputeWithoutEconomicsOrClassMutation() {
        Fixture f = new Fixture();
        f.data.grantFactionPopulationCenti("gondor", 10000L);
        KOMEHiredUnitRecord mounted = f.record("T100");
        mounted.cost = 17;
        mounted.baseCost = 19;
        mounted.populationSpent = 23;
        mounted.populationOwningFaction = "gondor";
        mounted.mounted = true;
        KOMEHiredUnitRecord ground = f.record("T100");
        ground.cost = 11;
        KOMEArmyCompany company = f.company("C1", "T100");
        f.attach(company, mounted);
        f.attach(company, ground);
        company.totalPopulation = company.mountedPopulation = company.groundPopulation = 999;
        long available = KOMEPopulationService.getAvailablePopulationCenti(f.data, "gondor");

        KOMECompanyReconciliationService.Result result = f.reconcile();

        assertEquals(28, company.totalPopulation);
        assertEquals(17, company.mountedPopulation);
        assertEquals(11, company.groundPopulation);
        assertEquals(23, mounted.populationSpent);
        assertEquals(19, mounted.baseCost);
        assertEquals(available,
            KOMEPopulationService.getAvailablePopulationCenti(f.data, "gondor"));
        assertTrue(KOMEHiredUnitClassification.isCampaignUnit(mounted));
        assertEquals(1, result.totalsRecomputed);
    }

    @Test public void validWaitingMovementCohortSurvivesUnchanged() {
        Fixture f = new Fixture();
        KOMEHiredUnitRecord record = f.record("T100");
        KOMEArmyCompany company = f.company("C1", "T100");
        f.attach(company, record);
        KOMEArmyMovementOrder order = f.order("M1", company, record);
        order.status = KOMEArmyMovementOrder.WAITING_NEXT_STEP;

        KOMECompanyReconciliationService.Result result = f.reconcile();

        assertEquals("M1", company.movementOrderId);
        assertEquals("M1", record.movementOrderId);
        assertTrue(order.units.contains(record.entity));
        assertEquals("C1", record.companyId);
        assertEquals(0, result.movementConflicts);
    }

    @Test public void mismatchedMovementCohortFailsClosedWithoutExpandingOrder() {
        Fixture f = new Fixture();
        KOMEHiredUnitRecord record = f.record("T100");
        KOMEArmyCompany company = f.company("C1", "T100");
        f.attach(company, record);
        KOMEArmyMovementOrder order = f.order("M1", company, record);
        order.units.clear();
        UUID unrelated = UUID.randomUUID();
        order.units.add(unrelated);

        KOMECompanyReconciliationService.Result result = f.reconcile();

        assertEquals("", record.companyId);
        assertEquals("", record.movementOrderId);
        assertEquals(1, order.units.size());
        assertEquals(unrelated, order.units.get(0));
        assertTrue(f.data.armyCompanies.containsKey("C1"));
        assertTrue(result.movementConflicts >= 1);
    }

    @Test public void explicitRecordLinkCanRestoreMissingCompanyListEntry() {
        Fixture f = new Fixture();
        KOMEHiredUnitRecord record = f.record("T100");
        KOMEArmyCompany company = f.company("C1", "T100");
        record.companyId = company.id;
        record.companyName = company.name;
        f.data.hiredUnits.put(record.entity, record);

        KOMECompanyReconciliationService.Result result = f.reconcile();

        assertTrue(company.units.contains(record.entity));
        assertEquals("C1", record.companyId);
        assertEquals(1, result.companyLinksRepaired);
    }

    @Test public void canonicalRebuildNeverAutoAssignsSameSourceCampaignOrphans() {
        Fixture f = new Fixture();
        KOMEHiredUnitRecord first = f.record("T100");
        KOMEHiredUnitRecord second = f.record("T100");
        first.sourceTileId = second.sourceTileId = "T900";
        first.lotrCompanyValue = second.lotrCompanyValue = "Native Alpha";
        f.data.hiredUnits.put(first.entity, first);
        f.data.hiredUnits.put(second.entity, second);

        KOMECompanyReconciliationService.Result result =
            f.data.rebuildArmyCompaniesForPlayer(f.owner);

        assertEquals("", first.companyId);
        assertEquals("", second.companyId);
        assertTrue(f.data.armyCompanies.isEmpty());
        assertEquals(0, result.retainedMemberships);
    }

    @Test public void legacySourceAndSquadronAuthoritiesAreRetiredFromRuntimePaths()
            throws Exception {
        String world = new String(Files.readAllBytes(Paths.get(
            "src/main/java/kome/common/data/KOMEWorldData.java")), StandardCharsets.UTF_8);
        String command = new String(Files.readAllBytes(Paths.get(
            "src/main/java/kome/common/command/KOMECommandTroops.java")), StandardCharsets.UTF_8);

        assertFalse(world.contains("assignUnitToHiringTileCompany"));
        assertFalse(world.contains("findHiringCompany"));
        assertFalse(world.contains("\"HC_"));
        assertFalse(world.contains("\"AC_"));
        assertTrue(world.contains("KOMECompanyReconciliationService.INSTANCE.reconcile"));
        assertFalse(command.contains("LOTR Unit Overview company column"));
        assertFalse(command.contains("nextCompanyId(KOMEWorldData"));
        assertTrue(command.contains("Canonical Campaign Detachment reconciliation"));
    }

    private static final class Fixture {
        final KOMEWorldData data = new KOMEWorldData("reconciliation");
        final UUID owner = UUID.randomUUID();
        final Map<UUID, KOMECompanyCoherenceService.PhysicalEvidence> physical =
            new HashMap<UUID, KOMECompanyCoherenceService.PhysicalEvidence>();
        final KOMECompanyReconciliationService service;

        Fixture() {
            KOMEPlayerProgression progression = new KOMEPlayerProgression();
            progression.setPledgedLord("lord", "Lord", "gondor");
            data.progressions.put(owner, progression);
            KOMECompanyCoherenceService coherence = new KOMECompanyCoherenceService(
                new KOMECompanyCoherenceService.PhysicalObservationSource() {
                    @Override public KOMECompanyCoherenceService.PhysicalEvidence current(UUID entityId) {
                        KOMECompanyCoherenceService.PhysicalEvidence evidence = physical.get(entityId);
                        return evidence == null
                            ? KOMECompanyCoherenceService.PhysicalEvidence.unknown(
                                KOMEServerTileAwareness.Availability.NOT_TRACKED)
                            : evidence;
                    }
                });
            service = new KOMECompanyReconciliationService(coherence);
        }

        KOMECompanyReconciliationService.Result reconcile() {
            return service.reconcile(data);
        }

        KOMEHiredUnitRecord record(String tile) {
            KOMEHiredUnitRecord record = new KOMEHiredUnitRecord();
            record.entity = UUID.randomUUID();
            record.owner = owner;
            record.sourcePlayer = owner;
            record.type = KOMEPopulationType.OFFENSIVE;
            record.cost = record.baseCost = record.populationSpent = 25;
            record.currentTile = tile;
            record.sourceTileId = "T900";
            record.unitFaction = "gondor";
            record.sourceFaction = "gondor";
            record.populationOwningFaction = "gondor";
            KOMEHiredUnitClassification.assignForCampaignWorkflow(record);
            return record;
        }

        KOMEArmyCompany company(String id, String tile) {
            KOMEArmyCompany company = new KOMEArmyCompany();
            company.id = id;
            company.owner = owner;
            company.ownerName = "Owner";
            company.faction = company.nativeFaction = "gondor";
            company.name = "Detachment " + id;
            company.currentTile = tile;
            company.sourceTileId = "T900";
            company.source = KOMEArmyCompany.SOURCE_CAMPAIGN_RECRUITMENT;
            company.status = KOMEArmyCompany.STATIONED;
            data.armyCompanies.put(id, company);
            return company;
        }

        void attach(KOMEArmyCompany company, KOMEHiredUnitRecord record) {
            data.hiredUnits.put(record.entity, record);
            record.companyId = company.id;
            record.companyName = company.name;
            company.units.add(record.entity);
            company.totalPopulation += Math.max(0, record.cost);
            if (record.mounted) company.mountedPopulation += Math.max(0, record.cost);
            else company.groundPopulation += Math.max(0, record.cost);
        }

        KOMEArmyMovementOrder order(String id, KOMEArmyCompany company,
                KOMEHiredUnitRecord record) {
            KOMEArmyMovementOrder order = new KOMEArmyMovementOrder();
            order.id = id;
            order.companyId = company.id;
            order.status = KOMEArmyMovementOrder.WAITING_NEXT_STEP;
            order.currentTile = company.currentTile;
            order.units.add(record.entity);
            company.movementOrderId = id;
            company.status = KOMEArmyCompany.MOVING;
            record.movementOrderId = id;
            data.armyMovements.put(id, order);
            return order;
        }
    }
}
