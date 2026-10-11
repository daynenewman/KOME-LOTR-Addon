package kome.common.data;

import lotr.common.fac.LOTRFactionRelations;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.Collections;
import java.util.UUID;

import static kome.common.data.KOMEConflictContracts.*;
import static kome.common.data.KOMEConflictRecord.*;
import static org.junit.Assert.*;

/** Narrow KOM-19 abandonment rules, intentionally separate from KOM-18 victory/timers. */
public class KOMEConflictAbandonmentReconciliationTest {
    @Rule public final KOMETileTestResources geometry = new KOMETileTestResources();

    @Before public void hostileDefaults() {
        relation("gondor", "mordor", LOTRFactionRelations.Relation.ENEMY);
        relation("gondor", "rohan", LOTRFactionRelations.Relation.ENEMY);
        relation("mordor", "rohan", LOTRFactionRelations.Relation.ALLY);
    }

    @After public void resetRelations() {
        relation("gondor", "mordor", LOTRFactionRelations.Relation.NEUTRAL);
        relation("gondor", "rohan", LOTRFactionRelations.Relation.NEUTRAL);
        relation("mordor", "rohan", LOTRFactionRelations.Relation.NEUTRAL);
    }

    @Test public void soleAttackerDepartureEndsAbandonedWithoutOwnershipOrTimerResolution() {
        Fixture f = new Fixture();
        KOMEConflictRecord conflict = f.commit("C1", "mordor", "");
        conflict = f.release(conflict, "C1", "mordor", 40L);
        KOMEConflictLifecycleService.OrdinaryAbandonmentResult result =
            f.reconcile(conflict, 50L);

        assertTrue(result.reason, result.ended());
        assertEquals(State.ENDED, result.record.getState());
        assertEquals(KOMEConflictLifecycleService.FORMAL_RETREAT_ABANDONMENT_REASON,
            result.record.getLastTransition().reason);
        assertEquals("gondor", f.data.conquestTiles.get("T402").projectRulingFaction());
        assertEquals(TimerStatus.NOT_STARTED, result.record.getResponseTimer().status);
        assertEquals(TimerStatus.NOT_STARTED, result.record.getCaptureTimer().status);
        assertFalse(result.record.getFactionParticipation().get("mordor").isActive());
    }

    @Test public void defenderRemainingDoesNotSustainAttackAndCanonicalEndReleasesItsHold() {
        Fixture f = new Fixture();
        KOMEConflictRecord conflict = f.commit("C1", "mordor", "");
        f.orderAtConflict("MD", "C2", "gondor", UUID.randomUUID());
        conflict = f.commit("C2", "gondor", "MD");
        KOMEConflictMovementService.applyConflictHold(f.data,
            f.data.armyMovements.get("MD"), conflict, 30L);
        conflict = f.release(conflict, "C1", "mordor", 40L);

        KOMEConflictLifecycleService.OrdinaryAbandonmentResult result =
            f.reconcile(conflict, 50L);
        assertTrue(result.reason, result.ended());
        assertEquals(1, result.movementHoldsReleased);
        assertTrue("Historical defender commitment is preserved in the ended record",
            result.record.getCommitments().containsKey("C2"));
        assertEquals(KOMEArmyMovementOrder.CONFLICT_RELEASED_PAUSED,
            f.data.armyMovements.get("MD").status);
        assertEquals("gondor", f.data.conquestTiles.get("T402").projectRulingFaction());
    }

    @Test public void anotherAttackerKeepsConflictAndItsHoldActive() {
        Fixture f = new Fixture();
        KOMEConflictRecord conflict = f.commit("C1", "mordor", "");
        f.orderAtConflict("M2", "C2", "mordor", UUID.randomUUID());
        conflict = f.commit("C2", "mordor", "M2");
        KOMEConflictMovementService.applyConflictHold(f.data,
            f.data.armyMovements.get("M2"), conflict, 30L);
        conflict = f.release(conflict, "C1", "mordor", 40L);

        KOMEConflictLifecycleService.OrdinaryAbandonmentResult result =
            f.reconcile(conflict, 50L);
        assertEquals(KOMEConflictLifecycleService.OrdinaryAbandonmentCode
            .OFFENSIVE_COMMITMENT_REMAINS, result.code);
        assertTrue(result.record.isActive());
        assertTrue(result.record.getCommitments().containsKey("C2"));
        assertEquals(KOMEArmyMovementOrder.CONFLICT_HELD,
            f.data.armyMovements.get("M2").status);
    }

    @Test public void alliedAttackerSustainsObjectiveConflict() {
        Fixture f = new Fixture();
        KOMEConflictRecord conflict = f.commit("C1", "mordor", "");
        conflict = f.commit("C2", "rohan", "");
        conflict = f.release(conflict, "C1", "mordor", 40L);

        KOMEConflictLifecycleService.OrdinaryAbandonmentResult result =
            f.reconcile(conflict, 50L);
        assertEquals(KOMEConflictLifecycleService.OrdinaryAbandonmentCode
            .OFFENSIVE_COMMITMENT_REMAINS, result.code);
        assertTrue(result.record.isActive());
        assertTrue(result.record.getFactionParticipation().get("rohan").isActive());
        assertFalse(result.record.getFactionParticipation().get("mordor").isActive());
    }

    @Test public void independentThirdPartyHostileAttackerAlsoSustainsConflict() {
        relation("mordor", "rohan", LOTRFactionRelations.Relation.NEUTRAL);
        Fixture f = new Fixture();
        KOMEConflictRecord conflict = f.commit("C1", "mordor", "");
        conflict = f.commit("C2", "rohan", "");
        conflict = f.release(conflict, "C1", "mordor", 40L);

        KOMEConflictLifecycleService.OrdinaryAbandonmentResult result =
            f.reconcile(conflict, 50L);
        assertEquals(KOMEConflictLifecycleService.OrdinaryAbandonmentCode
            .OFFENSIVE_COMMITMENT_REMAINS, result.code);
        assertTrue(result.record.isActive());
        assertTrue(result.record.getCommitments().containsKey("C2"));
    }

    @Test public void defenderDepartureDoesNotEndWhileAttackerOccupiesObjective() {
        Fixture f = new Fixture();
        KOMEConflictRecord conflict = f.commit("C1", "mordor", "");
        conflict = f.commit("C2", "gondor", "");
        conflict = f.release(conflict, "C2", "gondor", 40L);

        KOMEConflictLifecycleService.OrdinaryAbandonmentResult result =
            f.reconcile(conflict, 50L);
        assertEquals(KOMEConflictLifecycleService.OrdinaryAbandonmentCode
            .OFFENSIVE_COMMITMENT_REMAINS, result.code);
        assertTrue(result.record.isActive());
        assertTrue(result.record.getCommitments().containsKey("C1"));
    }

    @Test public void endedConflictReconcilesReceiptWithoutWithdrawingPlayerHistory() {
        Fixture f = new Fixture();
        KOMEConflictRecord conflict = f.commit("C1", "mordor", "");
        UUID player = UUID.randomUUID();
        conflict = ok(f.data.getConflictService().registerPlayer("T402", expected(conflict),
            player, "mordor", context(30L, "register")));
        KOMEJoinBattleDeploymentReceipt receipt = KOMEJoinBattleDeploymentReceipt.builder()
            .receiptId("JB1").actionToken("abandonment-receipt-token")
            .playerId(player).conflictId(conflict.getConflictId()).tileId("T402")
            .acceptedConflictRevision(conflict.getRevision()).factionId("mordor")
            .selectedCompanyId("C1").state(KOMEJoinBattleDeploymentReceipt.State.DEPLOYED)
            .createdAtMillis(31L).updatedAtMillis(32L).deployedAtMillis(Long.valueOf(32L))
            .returnAnchor(new KOMEJoinBattleDeploymentReceipt.Pose(
                0, 1D, 65D, 1D, 0F, 0F))
            .deploymentDestination(new KOMEJoinBattleDeploymentReceipt.Pose(
                0, 2D, 65D, 2D, 0F, 0F))
            .participationRecovery(KOMEJoinBattleDeploymentReceipt.ParticipationRecovery
                .PREEXISTING_ACTIVE).build();
        f.data.getJoinBattleDeploymentReceipts().publishNew(receipt);
        conflict = f.release(conflict, "C1", "mordor", 40L);

        KOMEConflictLifecycleService.OrdinaryAbandonmentResult result =
            f.reconcile(conflict, 50L);
        assertTrue(result.ended());
        assertEquals(KOMEJoinBattleDeploymentReceipt.State.PENDING_EGRESS,
            f.data.getJoinBattleDeploymentReceipts().get("JB1").getState());
        assertEquals(PlayerStatus.ACTIVE, result.record.getPlayers().get(player).status);
    }

    @Test public void laterLegalArrivalAfterAbandonmentCreatesFreshConflictIdentity() {
        Fixture f = new Fixture();
        KOMEConflictRecord first = f.commit("C1", "mordor", "");
        first = f.release(first, "C1", "mordor", 40L);
        assertTrue(f.reconcile(first, 50L).ended());

        KOMEConflictRecord fresh = f.legalArrival("C3", "M3", "mordor", 70L);
        assertEquals("CF2", fresh.getConflictId());
        assertTrue(fresh.isActive());
        assertTrue(fresh.getCommitments().containsKey("C3"));
        assertEquals(TimerStatus.NOT_STARTED, fresh.getResponseTimer().status);
    }

    @Test public void schemasRemainUnchanged() {
        assertEquals(12, KOMEWorldData.KOME_DATA_SCHEMA_VERSION);
        assertEquals(2, KOMEConflictPersistence.DATA_SCHEMA_VERSION);
        assertEquals(1, KOMEHiredUnitPhysicalLocator.DATA_SCHEMA_VERSION);
    }

    private static final class Fixture {
        final KOMEWorldData data = new KOMEWorldData("abandonment");
        private int uuid = 1;
        private long transitionTime = 20L;

        Fixture() {
            tile("T401", "mordor");
            tile("T402", "gondor");
            ok(data.getConflictService().start("T402", State.ORDINARY,
                ExpectedConflict.absent(), Collections.<GarrisonSeed>emptyList(),
                context(10L, "test ordinary conflict")));
        }

        KOMEConflictRecord commit(String id, String faction, String movementId) {
            KOMEArmyCompany company = data.armyCompanies.get(id);
            if (company == null) company(id, faction, "T402", UUID.randomUUID());
            KOMEConflictRecord current = data.getConflictService().get("T402");
            FactionParticipation participation =
                current.getFactionParticipation().get(faction);
            if (participation == null || !participation.isActive())
                current = ok(data.getConflictService().beginFactionParticipation("T402",
                    expected(current), faction, context(transitionTime++, "participate")));
            return ok(data.getConflictService().commit("T402", expected(current),
                new CommitmentInput(id, KOMEHiredUnitClass.CAMPAIGN,
                    EntryOrigin.EXTERIOR_ARRIVAL, movementId),
                context(transitionTime++, "commit")));
        }

        KOMEConflictRecord release(KOMEConflictRecord conflict, String id,
                String faction, long now) {
            return ok(data.getConflictService().releaseValidatedCommitment(data,
                new ValidatedDepartureRequest("T402", id, faction, expected(conflict)),
                context(now, "formal retreat departure")));
        }

        KOMEConflictLifecycleService.OrdinaryAbandonmentResult reconcile(
                KOMEConflictRecord conflict, long now) {
            return KOMEConflictLifecycleService.INSTANCE
                .reconcileOrdinaryConflictAfterCommitmentChange(data, "T402",
                    expected(conflict), context(now, "formal retreat"));
        }

        KOMEConflictRecord legalArrival(String companyId, String orderId,
                String faction, long now) {
            KOMEArmyCompany company = company(companyId, faction, "T401", UUID.randomUUID());
            KOMEArmyMovementOrder order = route(orderId, company, "T401", "T402");
            order.arrivalMillis = now;
            KOMEConflictMovementService.ArrivalPreparation preparation =
                KOMEConflictMovementService.prepareLegalArrival(data, order, "T402");
            assertTrue(preparation.reason, preparation.ready());
            order.status = KOMEArmyMovementOrder.SPAWNING;
            order.currentTile = "T402";
            order.currentRouteIndex = order.nextRouteIndex = 1;
            order.completedSteps = 1;
            company.currentTile = "T402";
            for (UUID unitId : company.units)
                data.hiredUnits.get(unitId).currentTile = "T402";
            KOMEConflictMovementService.ArrivalCommitment arrival =
                KOMEConflictMovementService.commitLegalArrival(data,
                    preparation.receipt, "server");
            assertTrue(arrival.reason, arrival.success());
            return arrival.conflictResult.record;
        }

        void orderAtConflict(String orderId, String companyId, String faction, UUID owner) {
            KOMEArmyCompany company = company(companyId, faction, "T402", owner);
            KOMEArmyMovementOrder order = route(orderId, company, "T401", "T402");
            order.currentTile = "T402";
            order.currentRouteIndex = order.nextRouteIndex = order.finalRouteIndex = 1;
            order.completedSteps = 1;
            order.traveledRouteTiles.add("T402");
        }

        KOMEArmyCompany company(String id, String faction, String tile, UUID owner) {
            KOMEArmyCompany company = new KOMEArmyCompany();
            company.id = id; company.name = id; company.owner = owner;
            company.ownerName = "Owner"; company.faction = company.nativeFaction = faction;
            company.currentTile = tile; company.status = KOMEArmyCompany.STATIONED;
            KOMEHiredUnitRecord unit = new KOMEHiredUnitRecord();
            unit.entity = new UUID(90L, uuid++); unit.owner = owner; unit.sourcePlayer = owner;
            unit.companyId = id; unit.companyName = id; unit.currentTile = tile;
            unit.sourceTileId = "T401"; unit.unitFaction = faction;
            unit.populationOwningFaction = faction; unit.type = KOMEPopulationType.OFFENSIVE;
            unit.cost = unit.baseCost = unit.populationSpent = 10;
            unit.assignPersistedUnitClass(KOMEHiredUnitClass.CAMPAIGN);
            company.units.add(unit.entity); company.totalPopulation = company.groundPopulation = 10;
            data.hiredUnits.put(unit.entity, unit); data.armyCompanies.put(id, company);
            return company;
        }

        KOMEArmyMovementOrder route(String id, KOMEArmyCompany company,
                String origin, String destination) {
            KOMEArmyMovementOrder order = KOMEArmyMovementOrder.newRoute(1);
            order.id = id; order.companyId = company.id; order.companyName = company.name;
            order.owner = company.owner; order.ownerName = company.ownerName;
            order.ownerFaction = company.faction; order.originTile = origin;
            order.destinationTile = order.finalDestinationTile = destination;
            order.currentTile = origin; order.nextTile = destination;
            order.currentStepOriginTile = origin; order.currentStepDestinationTile = destination;
            order.hostileAttackDestination = destination;
            order.routeTiles.add(origin); order.routeTiles.add(destination);
            order.traveledRouteTiles.add(origin); order.currentRouteIndex = 0;
            order.nextRouteIndex = order.finalRouteIndex = 1; order.totalSteps = 1;
            order.distanceTiles = 1; order.dailyStepsRemaining = 1;
            order.units.addAll(company.units); company.status = KOMEArmyCompany.MOVING;
            company.movementOrderId = id;
            for (UUID unitId : company.units) data.hiredUnits.get(unitId).movementOrderId = id;
            data.armyMovements.put(id, order);
            return order;
        }

        void tile(String id, String faction) {
            KOMEConquestTile tile = new KOMEConquestTile(id);
            tile.setCurrentRulingFaction(faction);
            data.conquestTiles.put(id, tile);
        }
    }

    private static ExpectedConflict expected(KOMEConflictRecord record) {
        return ExpectedConflict.at(record.getConflictId(), record.getRevision());
    }

    private static Context context(long time, String reason) {
        return new Context(time, "tester", reason);
    }

    private static KOMEConflictRecord ok(KOMEConflictService.Result result) {
        assertEquals(result.reason, Code.SUCCESS, result.code);
        return result.record;
    }

    private static void relation(String first, String second,
            LOTRFactionRelations.Relation relation) {
        LOTRFactionRelations.overrideRelations(KOMEAlliance.findLotrFaction(first),
            KOMEAlliance.findLotrFaction(second), relation);
    }
}
