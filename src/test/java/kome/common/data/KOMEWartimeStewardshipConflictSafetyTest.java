package kome.common.data;

import kome.common.KOMEAccessFixture;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;

import static kome.common.data.KOMEConflictContracts.*;
import static kome.common.data.KOMEConflictRecord.*;
import static org.junit.Assert.*;

public class KOMEWartimeStewardshipConflictSafetyTest {
    @Rule public final KOMETileTestResources geometry = new KOMETileTestResources();

    @Test public void revocationRulerDiplomacyAndPledgeCannotOverwriteConflictHold() {
        Fixture f = fixture();
        KOMEWartimeStewardshipService.revalidateCompany(f.data, f.company, 20L,
            "retired stewardship");
        assertPreserved(f);

        f.data.writeFactionKingRecord("rohan", UUID.randomUUID(), "Native King");
        f.data.onFactionKingGained("rohan", 21L);
        assertPreserved(f);

        KOMEWartimeStewardshipService.revalidateAll(f.data, 22L,
            "live diplomacy changed");
        assertPreserved(f);

        KOMEPledgeReleaseService.release(f.data, f.controller, "Steward",
            "gondor", "", 23L, "pledge changed");
        assertPreserved(f);
    }

    @Test public void activeCommitmentBlocksSafeDemobilizationAndLeavesCohortUntouched()
            throws Exception {
        Fixture f = fixture();
        f.company.withdrawalState = KOMEArmyCompany.CLEANUP_DEMOBILIZATION;
        f.company.status = KOMEArmyCompany.STATIONED;
        // Remove the movement link so every legacy demobilization condition is otherwise true;
        // the active ConflictRecord commitment must be the reason this is rejected.
        f.company.movementOrderId = "";
        f.data.getConquestTile("T100").claim("rohan", 29L);
        KOMEAccessFixture.TestWorld world =
            KOMEAccessFixture.allocate(KOMEAccessFixture.TestWorld.class);
        world.loadedEntityList = new ArrayList();
        assertTrue(KOMEWartimeStewardshipService.conflictProtects(f.data, f.company));
        assertEquals(0, KOMEWartimeStewardshipService.demobilizeIfSafe(
            f.data, f.company, world, 30L));
        assertTrue(f.data.armyCompanies.containsKey("C1"));
        assertTrue(f.data.hiredUnits.containsKey(f.unit.entity));
        assertTrue(f.company.units.contains(f.unit.entity));
        assertTrue(f.data.getConflictService().get("T100")
            .getCommitments().containsKey("C1"));
    }

    @Test public void historicalWarOpponentUnionNoLongerGrantsTargetOrMovementAuthority() {
        KOMEWorldData data = new KOMEWorldData("test"); data.initializeIntegratedWorld();
        KOMEWar war = KOMEWarService.createWar(data, "rohan", "mordor", "", "test", 1L);
        KOMEArmyCompany company = new KOMEArmyCompany();
        company.id = "C1"; company.faction = "rohan"; company.nativeFaction = "rohan";
        company.controllerAuthority = KOMEArmyCompany.AUTHORITY_STEWARDSHIP;
        company.authorizedWarIds.add(war.id);
        assertTrue(KOMEWartimeStewardshipService.authorizedOpponents(data, company).isEmpty());
        assertFalse(KOMEWartimeStewardshipService.canEnter(data, company, "mordor", false));
        assertFalse(KOMEWarService.supportingKingDecision(data, "rohan", "gondor",
            UUID.randomUUID()).allowed);
    }

    private static void assertPreserved(Fixture f) {
        assertEquals(KOMEArmyMovementOrder.CONFLICT_HELD, f.order.status);
        assertEquals("CF1", f.order.conflictHoldId);
        assertEquals("T100", f.company.currentTile);
        assertEquals("M1", f.company.movementOrderId);
        assertTrue(f.company.units.contains(f.unit.entity));
        assertTrue(f.data.hiredUnits.containsKey(f.unit.entity));
        assertTrue(f.data.getConflictService().get("T100")
            .getCommitments().containsKey("C1"));
    }

    private static Fixture fixture() {
        KOMEWorldData data = new KOMEWorldData("test"); data.initializeIntegratedWorld();
        UUID controller = UUID.randomUUID();
        KOMEArmyCompany company = new KOMEArmyCompany();
        company.id = "C1"; company.name = "Reserve"; company.owner = controller;
        company.ownerName = "Steward"; company.faction = "rohan";
        company.nativeFaction = "rohan"; company.currentTile = "T100";
        company.controllerAuthority = KOMEArmyCompany.AUTHORITY_STEWARDSHIP;
        company.temporaryController = controller; company.stewardshipCreated = true;
        company.movementOrderId = "M1"; company.status = KOMEArmyCompany.MOVING;
        KOMEHiredUnitRecord unit = new KOMEHiredUnitRecord();
        unit.entity = UUID.randomUUID(); unit.owner = controller; unit.unitFaction = "rohan";
        unit.sourceFaction = "rohan"; unit.populationOwningFaction = "rohan";
        unit.sourceType = KOMEHiredUnitRecord.SOURCE_STEWARDSHIP_RESERVATION;
        unit.type = KOMEPopulationType.OFFENSIVE; unit.currentTile = "T100";
        unit.companyId = "C1"; unit.movementOrderId = "M1";
        KOMEHiredUnitClassification.assignForCampaignWorkflow(unit);
        company.units.add(unit.entity); data.armyCompanies.put(company.id, company);
        data.hiredUnits.put(unit.entity, unit);
        KOMEArmyMovementOrder order = new KOMEArmyMovementOrder();
        order.id = "M1"; order.companyId = "C1"; order.owner = controller;
        order.ownerFaction = "gondor"; order.status = KOMEArmyMovementOrder.CONFLICT_HELD;
        order.conflictHoldId = "CF1"; order.conflictHeldAtMillis = 10L;
        order.currentTile = "T100"; order.destinationTile = "T100";
        order.finalDestinationTile = "T100"; order.hostileAttackDestination = "T100";
        order.routeTiles.add("T99"); order.routeTiles.add("T100");
        order.currentRouteIndex = 1; order.nextRouteIndex = 1; order.finalRouteIndex = 1;
        order.units.add(unit.entity); data.armyMovements.put(order.id, order);
        KOMEConflictRecord conflict = data.getConflictService().start("T100", State.ORDINARY,
            ExpectedConflict.absent(), Collections.<GarrisonSeed>emptyList(),
            new Context(1L, "test", "start")).record;
        data.getConflictService().commit("T100", ExpectedConflict.at(
            conflict.getConflictId(), conflict.getRevision()),
            new CommitmentInput("C1", KOMEHiredUnitClass.CAMPAIGN,
                EntryOrigin.LEGAL_ARRIVAL, "M1"), new Context(10L, "test", "commit"));
        return new Fixture(data, company, unit, order, controller);
    }

    private static final class Fixture {
        final KOMEWorldData data; final KOMEArmyCompany company;
        final KOMEHiredUnitRecord unit; final KOMEArmyMovementOrder order;
        final UUID controller;
        Fixture(KOMEWorldData data, KOMEArmyCompany company,
                KOMEHiredUnitRecord unit, KOMEArmyMovementOrder order, UUID controller) {
            this.data=data; this.company=company; this.unit=unit; this.order=order;
            this.controller=controller;
        }
    }
}
