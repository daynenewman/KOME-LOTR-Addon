package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;
import org.junit.Rule;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;

public class KOMECompanyReorganizationServiceTest {
    private KOMEPopulationTestConfig movementConfig;
    @org.junit.Before public void movementConfig() throws Exception { movementConfig = new KOMEPopulationTestConfig(); }
    @org.junit.After public void closeMovementConfig() throws Exception { movementConfig.close(); }
    @Test public void actualSplitAndMergeCannotMintMovementCredit() {
        Fixture f = new Fixture();
        KOMEHiredUnitRecord first = f.record("T100", 25, true, "T900", "A");
        KOMEHiredUnitRecord second = f.record("T100", 25, true, "T900", "B");
        KOMEArmyCompany parent = f.company("T100", first, second);
        KOMEMovementDayService.initializeNewCompany(parent, java.time.Instant.parse("2026-01-10T12:00:00Z").toEpochMilli());
        parent.movementAllowance = 1;
        NBTTagCompound before = parent.writeToNBT();
        KOMECompanyReorganizationService.Result split = f.service.split(f.data, f.owner, parent.id, Collections.singleton(second.entity));
        assertTrue(split.reason, split.success);
        assertEquals(1, parent.movementAllowance); assertEquals(0, split.secondaryCompany.movementAllowance);
        assertEquals(parent.movementBoundaryMillis, split.secondaryCompany.movementBoundaryMillis);
        KOMECompanyReorganizationService.Result merge = f.service.merge(f.data, f.owner, parent.id, split.secondaryCompanyId);
        assertTrue(merge.reason, merge.success);
        assertEquals(before.getCompoundTag("MovementAllowance"), parent.writeToNBT().getCompoundTag("MovementAllowance"));
        assertEquals(1, parent.movementAllowance);
    }

    @Test public void actualMergeUsesMaximumContributingAllowanceCappedByMixedComposition() {
        Fixture f = new Fixture();
        KOMEArmyCompany first = f.company("T100", f.record("T100", 25, false, "T900", "A"));
        KOMEArmyCompany second = f.company("T100", f.record("T100", 25, true, "T900", "B"));
        long now = java.time.Instant.parse("2026-01-10T12:00:00Z").toEpochMilli();
        KOMEMovementDayService.initializeNewCompany(first, now); KOMEMovementDayService.initializeNewCompany(second, now);
        first.movementAllowance = 0;
        KOMECompanyReorganizationService.Result merge = f.service.merge(f.data, f.owner, first.id, second.id);
        assertTrue(merge.reason, merge.success);
        assertEquals(1, first.movementAllowance);
        assertEquals(second.movementBoundaryMillis, first.movementBoundaryMillis);
    }
    @Rule public final KOMETileTestResources geometry = new KOMETileTestResources();

    @Test public void splitCreatesStableChildAndPreservesUnitEconomicsAndProvenance() {
        Fixture f = new Fixture();
        KOMEHiredUnitRecord first = f.record("T100", 10, false, "T900", "Alpha");
        KOMEHiredUnitRecord second = f.record("T100", 20, true, "T901", "Beta");
        KOMEHiredUnitRecord third = f.record("T100", 30, false, "T902", "Gamma");
        KOMEArmyCompany parent = f.company("T100", first, second, third);
        parent.tendency = KOMEArmyCompany.AGGRESSIVE;
        parent.populationSource = "native-test";
        long available = f.available();

        KOMECompanyReorganizationService.Result result =
            f.service.split(f.data, f.owner, parent.id,
                Arrays.asList(second.entity, third.entity));

        assertTrue(result.reason, result.success);
        assertEquals(KOMECompanyReorganizationService.Code.SUCCESS, result.code);
        assertEquals("C1", parent.id);
        assertEquals("C2", result.secondaryCompanyId);
        KOMEArmyCompany child = f.data.armyCompanies.get("C2");
        assertNotNull(child);
        assertEquals("T100", parent.currentTile);
        assertEquals("T100", child.currentTile);
        assertEquals("T900", parent.sourceTileId);
        assertEquals("", child.sourceTileId);
        KOMEArmyCompany restoredChild = new KOMEArmyCompany(); restoredChild.readFromNBT(child.writeToNBT());
        assertEquals("", restoredChild.sourceTileId);
        assertEquals(f.owner, child.owner);
        assertEquals("gondor", child.faction);
        assertEquals("gondor", child.nativeFaction);
        assertEquals(KOMEArmyCompany.AGGRESSIVE, child.tendency);
        assertEquals("native-test", child.populationSource);
        assertEquals(KOMEArmyCompany.AUTHORITY_NATIVE, child.controllerAuthority);
        assertNull(child.temporaryController);
        assertNull(child.delegatedBy);
        assertTrue(child.authorizedWarIds.isEmpty());
        assertEquals(KOMEArmyCompany.CLEANUP_NONE, child.withdrawalState);
        assertNull(child.transferRecipient);
        assertFalse(child.stewardshipCreated);
        assertEquals(0, child.stewardshipReservation);
        assertEquals(Collections.singletonList(first.entity), parent.units);
        assertEquals(Arrays.asList(second.entity, third.entity), child.units);
        assertEquals("C1", first.companyId);
        assertEquals("C2", second.companyId);
        assertEquals("C2", third.companyId);
        assertEquals("T900", first.sourceTileId);
        assertEquals("T901", second.sourceTileId);
        assertEquals("T902", third.sourceTileId);
        assertEquals("Alpha", first.lotrCompanyValue);
        assertEquals("Beta", second.lotrCompanyValue);
        assertEquals("Gamma", third.lotrCompanyValue);
        assertEquals(10, parent.totalPopulation);
        assertEquals(0, parent.mountedPopulation);
        assertEquals(10, parent.groundPopulation);
        assertEquals(50, child.totalPopulation);
        assertEquals(20, child.mountedPopulation);
        assertEquals(30, child.groundPopulation);
        assertEquals(available, f.available());
        assertEquals(20, second.cost);
        assertEquals(20, second.populationSpent);
        assertTrue(KOMEHiredUnitClassification.isCampaignUnit(second));
    }

    @Test public void splitChildIdIsNeverReusedAfterRemoval() {
        Fixture f = new Fixture();
        KOMEHiredUnitRecord first = f.record("T100", 10, false, "T900", "");
        KOMEHiredUnitRecord second = f.record("T100", 10, false, "T900", "");
        KOMEArmyCompany parent = f.company("T100", first, second);

        KOMECompanyReorganizationService.Result result =
            f.service.split(f.data, f.owner, parent.id,
                Collections.singleton(second.entity));
        assertTrue(result.success);
        assertEquals("C2", result.secondaryCompanyId);
        f.data.armyCompanies.remove("C2");

        assertEquals("C3", f.data.nextCampaignCompanyId());
    }

    @Test public void splitRejectsEmptyWholeAndNonMemberSelections() {
        Fixture f = new Fixture();
        KOMEHiredUnitRecord first = f.record("T100", 10, false, "T900", "");
        KOMEHiredUnitRecord second = f.record("T100", 10, false, "T900", "");
        KOMEArmyCompany parent = f.company("T100", first, second);

        assertCode(KOMECompanyReorganizationService.Code.INVALID_MEMBER_SELECTION,
            f.service.split(f.data, f.owner, parent.id, Collections.<UUID>emptyList()));
        assertCode(KOMECompanyReorganizationService.Code.WHOLE_COMPANY_SELECTED,
            f.service.split(f.data, f.owner, parent.id,
                Arrays.asList(first.entity, second.entity)));
        assertCode(KOMECompanyReorganizationService.Code.MEMBER_NOT_IN_PARENT,
            f.service.split(f.data, f.owner, parent.id,
                Collections.singleton(UUID.randomUUID())));
        assertEquals(2, parent.units.size());
        assertEquals(1, f.data.armyCompanies.size());
    }

    @Test public void splitRejectsIneligibleOrIncoherentMembership() {
        Fixture ordinaryFixture = new Fixture();
        KOMEHiredUnitRecord campaign =
            ordinaryFixture.record("T100", 10, false, "T900", "");
        KOMEHiredUnitRecord ordinary =
            ordinaryFixture.record("T100", 10, false, "T900", "");
        ordinary.assignPersistedUnitClass(KOMEHiredUnitClass.ORDINARY);
        KOMEArmyCompany invalid =
            ordinaryFixture.company("T100", campaign, ordinary);

        assertFalse(ordinaryFixture.service.split(ordinaryFixture.data,
            ordinaryFixture.owner, invalid.id,
            Collections.singleton(ordinary.entity)).success);

        Fixture staleFixture = new Fixture();
        KOMEHiredUnitRecord first =
            staleFixture.record("T100", 10, false, "T900", "");
        KOMEHiredUnitRecord second =
            staleFixture.record("T100", 10, false, "T900", "");
        KOMEArmyCompany stale = staleFixture.company("T100", first, second);
        stale.totalPopulation++;

        assertCode(KOMECompanyReorganizationService.Code.INCOHERENT,
            staleFixture.service.split(staleFixture.data, staleFixture.owner,
                stale.id, Collections.singleton(second.entity)));
    }

    @Test public void unknownPhysicalStateAllowsSplitButContradictionBlocksIt() {
        Fixture unknown = new Fixture();
        KOMEHiredUnitRecord first =
            unknown.record("T100", 10, false, "T900", "");
        KOMEHiredUnitRecord second =
            unknown.record("T100", 10, false, "T900", "");
        KOMEArmyCompany parent = unknown.company("T100", first, second);
        assertTrue(unknown.service.split(unknown.data, unknown.owner, parent.id,
            Collections.singleton(second.entity)).success);

        Fixture contradicted = new Fixture();
        KOMEHiredUnitRecord one =
            contradicted.record("T100", 10, false, "T900", "");
        KOMEHiredUnitRecord two =
            contradicted.record("T100", 10, false, "T900", "");
        KOMEArmyCompany blocked = contradicted.company("T100", one, two);
        contradicted.physical.put(two.entity,
            KOMECompanyCoherenceService.PhysicalEvidence.resolved("T101"));

        assertCode(KOMECompanyReorganizationService.Code.PHYSICAL_CONTRADICTION,
            contradicted.service.split(contradicted.data, contradicted.owner,
                blocked.id, Collections.singleton(two.entity)));
        assertEquals("T100", two.currentTile);
        assertEquals(blocked.id, two.companyId);
    }

    @Test public void routeTransferAndWithdrawalEachBlockSplit() {
        Fixture routed = new Fixture();
        KOMEHiredUnitRecord first =
            routed.record("T100", 10, false, "T900", "");
        KOMEHiredUnitRecord second =
            routed.record("T100", 10, false, "T900", "");
        KOMEArmyCompany parent = routed.company("T100", first, second);
        routed.route(parent);
        KOMECompanyReorganizationService.Result routeResult =
            routed.service.split(routed.data, routed.owner, parent.id,
                Collections.singleton(second.entity));
        assertCode(KOMECompanyReorganizationService.Code.ROUTE_ACTIVE, routeResult);
        assertTrue(routeResult.reason.contains("Cancel"));

        Fixture transferring = new Fixture();
        first = transferring.record("T100", 10, false, "T900", "");
        second = transferring.record("T100", 10, false, "T900", "");
        parent = transferring.company("T100", first, second);
        parent.transferRecipient = UUID.randomUUID();
        assertCode(KOMECompanyReorganizationService.Code.TRANSFER_PENDING,
            transferring.service.split(transferring.data, transferring.owner,
                parent.id, Collections.singleton(second.entity)));

        Fixture withdrawing = new Fixture();
        first = withdrawing.record("T100", 10, false, "T900", "");
        second = withdrawing.record("T100", 10, false, "T900", "");
        parent = withdrawing.company("T100", first, second);
        parent.withdrawalState = KOMEArmyCompany.CLEANUP_WITHDRAWAL;
        assertCode(KOMECompanyReorganizationService.Code.WITHDRAWAL_ACTIVE,
            withdrawing.service.split(withdrawing.data, withdrawing.owner,
                parent.id, Collections.singleton(second.entity)));
    }

    @Test public void unsafeTemporaryAuthorityBlocksSplitInsteadOfBeingCopied() {
        Fixture f = new Fixture();
        KOMEHiredUnitRecord first = f.record("T100", 10, false, "T900", "");
        KOMEHiredUnitRecord second = f.record("T100", 10, false, "T900", "");
        KOMEArmyCompany parent = f.company("T100", first, second);
        parent.temporaryController = UUID.randomUUID();
        parent.controllerAuthority = KOMEArmyCompany.AUTHORITY_ALLIANCE_DELEGATE;

        assertCode(KOMECompanyReorganizationService.Code.AUTHORITY_STATE_UNSAFE,
            f.service.split(f.data, f.owner, parent.id,
                Collections.singleton(second.entity)));
        assertEquals(1, f.data.armyCompanies.size());
    }

    @Test public void splitRollbackRestoresMembershipAndConsumesAllocatedId() {
        Fixture f = new Fixture();
        KOMEHiredUnitRecord first = f.record("T100", 10, false, "T900", "");
        KOMEHiredUnitRecord second = f.record("T100", 20, false, "T901", "");
        KOMEArmyCompany parent = f.company("T100", first, second);
        KOMECompanyReorganizationService failing = f.failingService(
            KOMECompanyReorganizationService.Operation.SPLIT);

        KOMECompanyReorganizationService.Result result =
            failing.split(f.data, f.owner, parent.id,
                Collections.singleton(second.entity));

        assertCode(KOMECompanyReorganizationService.Code.MUTATION_FAILED, result);
        assertEquals(Arrays.asList(first.entity, second.entity), parent.units);
        assertEquals(parent.id, first.companyId);
        assertEquals(parent.id, second.companyId);
        assertEquals(30, parent.totalPopulation);
        assertFalse(f.data.armyCompanies.containsKey("C2"));
        assertEquals("C3", f.data.nextCampaignCompanyId());
    }

    @Test public void mergeKeepsExplicitSurvivorAndPreservesUnitMetadataAndEconomics() {
        Fixture f = new Fixture();
        KOMEHiredUnitRecord first =
            f.record("T100", 10, false, "T900", "Alpha");
        KOMEHiredUnitRecord second =
            f.record("T100", 20, true, "T901", "Beta");
        KOMEArmyCompany survivor = f.company("T100", first);
        KOMEArmyCompany absorbed = f.company("T100", second);
        long available = f.available();

        KOMECompanyReorganizationService.Result result =
            f.service.merge(f.data, f.owner, survivor.id, absorbed.id);

        assertTrue(result.reason, result.success);
        assertEquals("C1", result.primaryCompanyId);
        assertEquals("C2", result.secondaryCompanyId);
        assertSame(survivor, result.primaryCompany);
        assertTrue(f.data.armyCompanies.containsKey("C1"));
        assertFalse(f.data.armyCompanies.containsKey("C2"));
        assertEquals(Arrays.asList(first.entity, second.entity), survivor.units);
        assertEquals("C1", first.companyId);
        assertEquals("C1", second.companyId);
        assertEquals("T900", first.sourceTileId);
        assertEquals("T901", second.sourceTileId);
        assertEquals("Alpha", first.lotrCompanyValue);
        assertEquals("Beta", second.lotrCompanyValue);
        assertEquals("", survivor.sourceTileId);
        KOMEArmyCompany restoredSurvivor = new KOMEArmyCompany(); restoredSurvivor.readFromNBT(survivor.writeToNBT());
        assertEquals("", restoredSurvivor.sourceTileId);
        assertEquals(30, survivor.totalPopulation);
        assertEquals(20, survivor.mountedPopulation);
        assertEquals(10, survivor.groundPopulation);
        assertEquals(available, f.available());
        assertEquals(20, second.populationSpent);
        assertEquals("C3", f.data.nextCampaignCompanyId());
    }

    @Test public void mergeRejectsDifferentOwnerFactionAndTile() {
        Fixture ownerFixture = new Fixture();
        KOMEHiredUnitRecord one =
            ownerFixture.record("T100", 10, false, "T900", "");
        KOMEHiredUnitRecord two =
            ownerFixture.record("T100", 10, false, "T900", "");
        KOMEArmyCompany first = ownerFixture.company("T100", one);
        KOMEArmyCompany second = ownerFixture.company("T100", two);
        UUID otherOwner = UUID.randomUUID();
        second.owner = otherOwner;
        two.owner = otherOwner;
        assertCode(KOMECompanyReorganizationService.Code.DIFFERENT_OWNER,
            ownerFixture.service.merge(ownerFixture.data, ownerFixture.owner,
                true, first.id, second.id));

        Fixture factionFixture = new Fixture();
        one = factionFixture.record("T100", 10, false, "T900", "");
        two = factionFixture.record("T100", 10, false, "T900", "");
        first = factionFixture.company("T100", one);
        second = factionFixture.company("T100", two);
        second.faction = second.nativeFaction = "rohan";
        assertCode(KOMECompanyReorganizationService.Code.INCOMPATIBLE_FACTION,
            factionFixture.service.merge(factionFixture.data, factionFixture.owner,
                first.id, second.id));

        Fixture tileFixture = new Fixture();
        one = tileFixture.record("T100", 10, false, "T900", "");
        two = tileFixture.record("T101", 10, false, "T900", "");
        first = tileFixture.company("T100", one);
        second = tileFixture.company("T101", two);
        assertCode(KOMECompanyReorganizationService.Code.DIFFERENT_STRATEGIC_TILE,
            tileFixture.service.merge(tileFixture.data, tileFixture.owner,
                first.id, second.id));
    }

    @Test public void activeRouteOnEitherDetachmentBlocksMerge() {
        Fixture survivorRouted = new Fixture();
        KOMEHiredUnitRecord one =
            survivorRouted.record("T100", 10, false, "T900", "");
        KOMEHiredUnitRecord two =
            survivorRouted.record("T100", 10, false, "T900", "");
        KOMEArmyCompany first = survivorRouted.company("T100", one);
        KOMEArmyCompany second = survivorRouted.company("T100", two);
        survivorRouted.route(first);
        assertCode(KOMECompanyReorganizationService.Code.ROUTE_ACTIVE,
            survivorRouted.service.merge(survivorRouted.data,
                survivorRouted.owner, first.id, second.id));

        Fixture absorbedRouted = new Fixture();
        one = absorbedRouted.record("T100", 10, false, "T900", "");
        two = absorbedRouted.record("T100", 10, false, "T900", "");
        first = absorbedRouted.company("T100", one);
        second = absorbedRouted.company("T100", two);
        absorbedRouted.route(second);
        assertCode(KOMECompanyReorganizationService.Code.ROUTE_ACTIVE,
            absorbedRouted.service.merge(absorbedRouted.data,
                absorbedRouted.owner, first.id, second.id));
    }

    @Test public void transferAndWithdrawalOnEitherDetachmentBlockMerge() {
        Fixture transferring = new Fixture();
        KOMEHiredUnitRecord one =
            transferring.record("T100", 10, false, "T900", "");
        KOMEHiredUnitRecord two =
            transferring.record("T100", 10, false, "T900", "");
        KOMEArmyCompany first = transferring.company("T100", one);
        KOMEArmyCompany second = transferring.company("T100", two);
        second.transferOfferedBy = transferring.owner;
        assertCode(KOMECompanyReorganizationService.Code.TRANSFER_PENDING,
            transferring.service.merge(transferring.data, transferring.owner,
                first.id, second.id));

        Fixture withdrawing = new Fixture();
        one = withdrawing.record("T100", 10, false, "T900", "");
        two = withdrawing.record("T100", 10, false, "T900", "");
        first = withdrawing.company("T100", one);
        second = withdrawing.company("T100", two);
        first.withdrawalState = KOMEArmyCompany.CLEANUP_ADMIN;
        assertCode(KOMECompanyReorganizationService.Code.WITHDRAWAL_ACTIVE,
            withdrawing.service.merge(withdrawing.data, withdrawing.owner,
                first.id, second.id));
    }

    @Test public void unknownPhysicalStateAllowsMergeButContradictionBlocksIt() {
        Fixture unknown = new Fixture();
        KOMEHiredUnitRecord one =
            unknown.record("T100", 10, false, "T900", "");
        KOMEHiredUnitRecord two =
            unknown.record("T100", 10, false, "T900", "");
        KOMEArmyCompany first = unknown.company("T100", one);
        KOMEArmyCompany second = unknown.company("T100", two);
        assertTrue(unknown.service.merge(unknown.data, unknown.owner,
            first.id, second.id).success);

        Fixture contradicted = new Fixture();
        one = contradicted.record("T100", 10, false, "T900", "");
        two = contradicted.record("T100", 10, false, "T900", "");
        first = contradicted.company("T100", one);
        second = contradicted.company("T100", two);
        contradicted.physical.put(two.entity,
            KOMECompanyCoherenceService.PhysicalEvidence.resolved("T101"));
        assertCode(KOMECompanyReorganizationService.Code.PHYSICAL_CONTRADICTION,
            contradicted.service.merge(contradicted.data, contradicted.owner,
                first.id, second.id));
        assertEquals("T100", two.currentTile);
    }

    @Test public void emptyOrUnsafeAuthorityDetachmentCannotMerge() {
        Fixture emptyFixture = new Fixture();
        KOMEHiredUnitRecord member =
            emptyFixture.record("T100", 10, false, "T900", "");
        KOMEArmyCompany populated = emptyFixture.company("T100", member);
        KOMEArmyCompany empty = emptyFixture.emptyCompany("T100");
        assertCode(KOMECompanyReorganizationService.Code.EMPTY_COMPANY,
            emptyFixture.service.merge(emptyFixture.data, emptyFixture.owner,
                populated.id, empty.id));

        Fixture authorityFixture = new Fixture();
        KOMEHiredUnitRecord one =
            authorityFixture.record("T100", 10, false, "T900", "");
        KOMEHiredUnitRecord two =
            authorityFixture.record("T100", 10, false, "T900", "");
        KOMEArmyCompany first = authorityFixture.company("T100", one);
        KOMEArmyCompany second = authorityFixture.company("T100", two);
        second.authorizedWarIds.add("W1");
        assertCode(KOMECompanyReorganizationService.Code.AUTHORITY_STATE_UNSAFE,
            authorityFixture.service.merge(authorityFixture.data,
                authorityFixture.owner, first.id, second.id));

        second.authorizedWarIds.clear();
        second.populationSource = "different-funding-authority";
        assertCode(KOMECompanyReorganizationService.Code.AUTHORITY_STATE_UNSAFE,
            authorityFixture.service.merge(authorityFixture.data,
                authorityFixture.owner, first.id, second.id));
    }

    @Test public void mergeRollbackRestoresBothCompaniesAndRecords() {
        Fixture f = new Fixture();
        KOMEHiredUnitRecord first = f.record("T100", 10, false, "T900", "");
        KOMEHiredUnitRecord second = f.record("T100", 20, false, "T901", "");
        KOMEArmyCompany survivor = f.company("T100", first);
        KOMEArmyCompany absorbed = f.company("T100", second);
        KOMECompanyReorganizationService failing = f.failingService(
            KOMECompanyReorganizationService.Operation.MERGE);

        KOMECompanyReorganizationService.Result result =
            failing.merge(f.data, f.owner, survivor.id, absorbed.id);

        assertCode(KOMECompanyReorganizationService.Code.MUTATION_FAILED, result);
        assertSame(survivor, f.data.armyCompanies.get("C1"));
        assertSame(absorbed, f.data.armyCompanies.get("C2"));
        assertEquals(Collections.singletonList(first.entity), survivor.units);
        assertEquals(Collections.singletonList(second.entity), absorbed.units);
        assertEquals("C1", first.companyId);
        assertEquals("C2", second.companyId);
        assertEquals(10, survivor.totalPopulation);
        assertEquals(20, absorbed.totalPopulation);
    }

    @Test public void reconciliationDoesNotRemergeExplicitSplitPair() {
        Fixture f = new Fixture();
        KOMEHiredUnitRecord first =
            f.record("T100", 10, false, "T900", "Same Squadron");
        KOMEHiredUnitRecord second =
            f.record("T100", 10, false, "T900", "Same Squadron");
        KOMEArmyCompany parent = f.company("T100", first, second);
        assertTrue(f.service.split(f.data, f.owner, parent.id,
            Collections.singleton(second.entity)).success);

        KOMECompanyReconciliationService.Result reconciliation =
            f.data.rebuildArmyCompaniesForPlayer(f.owner);

        assertEquals(2, f.data.armyCompanies.size());
        assertEquals("C1", first.companyId);
        assertEquals("C2", second.companyId);
        assertEquals(2, reconciliation.retainedMemberships);
    }

    @Test public void splitPairSurvivesRestartWithStableMembership() {
        Fixture f = new Fixture();
        f.data.initializeIntegratedWorld();
        KOMEHiredUnitRecord first =
            f.record("T100", 10, false, "T900", "Same Squadron");
        KOMEHiredUnitRecord second =
            f.record("T100", 10, false, "T900", "Same Squadron");
        KOMEArmyCompany parent = f.company("T100", first, second);
        assertTrue(f.service.split(f.data, f.owner, parent.id,
            Collections.singleton(second.entity)).success);

        KOMEWorldData restored = f.restart();

        assertEquals(2, restored.armyCompanies.size());
        assertEquals("C1", restored.hiredUnits.get(first.entity).companyId);
        assertEquals("C2", restored.hiredUnits.get(second.entity).companyId);
        assertEquals("C3", restored.nextCampaignCompanyId());
    }

    @Test public void mergedAbsorbedCompanyDoesNotReturnAfterRestart() {
        Fixture f = new Fixture();
        f.data.initializeIntegratedWorld();
        KOMEHiredUnitRecord first =
            f.record("T100", 10, false, "T900", "Alpha");
        KOMEHiredUnitRecord second =
            f.record("T100", 10, false, "T901", "Beta");
        KOMEArmyCompany survivor = f.company("T100", first);
        KOMEArmyCompany absorbed = f.company("T100", second);
        assertTrue(f.service.merge(f.data, f.owner,
            survivor.id, absorbed.id).success);

        KOMEWorldData restored = f.restart();

        assertEquals(1, restored.armyCompanies.size());
        assertTrue(restored.armyCompanies.containsKey("C1"));
        assertFalse(restored.armyCompanies.containsKey("C2"));
        assertEquals("C1", restored.hiredUnits.get(first.entity).companyId);
        assertEquals("C1", restored.hiredUnits.get(second.entity).companyId);
        assertEquals("C3", restored.nextCampaignCompanyId());
    }

    @Test public void nonOwnerCannotReorganizeWithoutExplicitAdminContext() {
        Fixture f = new Fixture();
        KOMEHiredUnitRecord first = f.record("T100", 10, false, "T900", "");
        KOMEHiredUnitRecord second = f.record("T100", 10, false, "T900", "");
        KOMEArmyCompany parent = f.company("T100", first, second);

        assertCode(KOMECompanyReorganizationService.Code.NOT_AUTHORIZED,
            f.service.split(f.data, UUID.randomUUID(), parent.id,
                Collections.singleton(second.entity)));
        assertTrue(f.service.split(f.data, UUID.randomUUID(), true, parent.id,
            Collections.singleton(second.entity)).success);
    }

    private static void assertCode(KOMECompanyReorganizationService.Code code,
            KOMECompanyReorganizationService.Result result) {
        assertFalse(result.reason, result.success);
        assertEquals(code, result.code);
    }

    private static final class Fixture {
        final KOMEWorldData data = new KOMEWorldData("reorganization");
        final UUID owner = UUID.randomUUID();
        final Map<UUID, KOMECompanyCoherenceService.PhysicalEvidence> physical =
            new HashMap<UUID, KOMECompanyCoherenceService.PhysicalEvidence>();
        final KOMECompanyCoherenceService coherence;
        final KOMECompanyReorganizationService service;

        Fixture() {
            KOMEPlayerProgression progression = new KOMEPlayerProgression();
            progression.setPledgedLord("lord", "Lord", "gondor");
            data.progressions.put(owner, progression);
            data.grantFactionPopulationCenti("gondor", 100000L);
            coherence = new KOMECompanyCoherenceService(
                new KOMECompanyCoherenceService.PhysicalObservationSource() {
                    @Override public KOMECompanyCoherenceService.PhysicalEvidence current(
                            UUID entityId) {
                        KOMECompanyCoherenceService.PhysicalEvidence evidence =
                            physical.get(entityId);
                        return evidence == null
                            ? KOMECompanyCoherenceService.PhysicalEvidence.unknown(
                                KOMEServerTileAwareness.Availability.NOT_TRACKED)
                            : evidence;
                    }
                });
            service = new KOMECompanyReorganizationService(coherence,
                new KOMECompanyReorganizationService.MutationHook() {
                    @Override public void afterMutation(
                            KOMECompanyReorganizationService.Operation operation,
                            KOMEArmyCompany primary, KOMEArmyCompany secondary) { }
                });
        }

        KOMECompanyReorganizationService failingService(
                final KOMECompanyReorganizationService.Operation failOperation) {
            return new KOMECompanyReorganizationService(coherence,
                new KOMECompanyReorganizationService.MutationHook() {
                    @Override public void afterMutation(
                            KOMECompanyReorganizationService.Operation operation,
                            KOMEArmyCompany primary, KOMEArmyCompany secondary) {
                        if (operation == failOperation)
                            throw new IllegalStateException("injected transaction failure");
                    }
                });
        }

        KOMEHiredUnitRecord record(String tile, int cost, boolean mounted,
                String sourceTile, String nativeSquadron) {
            KOMEHiredUnitRecord record = new KOMEHiredUnitRecord();
            record.entity = UUID.randomUUID();
            record.owner = owner;
            record.sourcePlayer = owner;
            record.type = KOMEPopulationType.OFFENSIVE;
            record.cost = record.baseCost = record.populationSpent = cost;
            record.currentTile = tile;
            record.sourceTileId = sourceTile;
            record.sourceFaction = record.unitFaction =
                record.populationOwningFaction = "gondor";
            record.lotrCompanyValue = nativeSquadron;
            record.mounted = mounted;
            record.controller = owner;
            record.controllerAuthority = KOMEArmyCompany.AUTHORITY_NATIVE;
            KOMEHiredUnitClassification.assignForCampaignWorkflow(record);
            data.hiredUnits.put(record.entity, record);
            return record;
        }

        KOMEArmyCompany company(String tile, KOMEHiredUnitRecord... records) {
            KOMEArmyCompany company = emptyCompany(tile);
            for (KOMEHiredUnitRecord record : records) {
                company.units.add(record.entity);
                record.companyId = company.id;
                record.companyName = company.name;
                record.companyAssignedBy = owner;
                record.companyAssignedByName = "Owner";
            }
            data.recalculateCampaignCompanyComposition(company);
            return company;
        }

        KOMEArmyCompany emptyCompany(String tile) {
            KOMEArmyCompany company = new KOMEArmyCompany();
            company.id = data.nextCampaignCompanyId();
            company.owner = owner;
            company.ownerName = "Owner";
            company.faction = company.nativeFaction = "gondor";
            company.name = "Detachment " + company.id;
            company.currentTile = tile;
            company.sourceTileId = "T900";
            company.source = KOMEArmyCompany.SOURCE_CAMPAIGN_RECRUITMENT;
            company.status = KOMEArmyCompany.STATIONED;
            company.controllerAuthority = KOMEArmyCompany.AUTHORITY_NATIVE;
            data.armyCompanies.put(company.id, company);
            return company;
        }

        void route(KOMEArmyCompany company) {
            KOMEArmyMovementOrder order = new KOMEArmyMovementOrder();
            order.id = "M-" + company.id;
            order.companyId = company.id;
            order.owner = company.owner;
            order.status = KOMEArmyMovementOrder.WAITING_NEXT_STEP;
            order.currentTile = company.currentTile;
            order.destinationTile = "T101";
            order.units.addAll(company.units);
            data.armyMovements.put(order.id, order);
            company.status = KOMEArmyCompany.MOVING;
            company.movementOrderId = order.id;
            for (UUID unitId : company.units)
                data.hiredUnits.get(unitId).movementOrderId = order.id;
        }

        long available() {
            return KOMEPopulationService.getAvailablePopulationCenti(data, "gondor");
        }

        KOMEWorldData restart() {
            NBTTagCompound nbt = new NBTTagCompound();
            data.writeToNBT(nbt);
            KOMEWorldData restored = new KOMEWorldData("restored");
            restored.readFromNBT(nbt);
            return restored;
        }
    }
}
