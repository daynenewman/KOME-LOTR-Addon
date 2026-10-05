package kome.common.data;

import lotr.common.fac.LOTRFactionRelations;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static kome.common.data.KOMEConflictContracts.*;
import static kome.common.data.KOMEConflictRecord.*;
import static org.junit.Assert.*;

/** Deterministic Phase 4 movement/conflict integration tests; no physical observation is used. */
public class KOMEConflictMovementServiceTest {
    private KOMEPopulationTestConfig movementConfig;
    @org.junit.Before public void movementConfig() throws Exception { movementConfig = new KOMEPopulationTestConfig(); }
    @org.junit.After public void closeMovementConfig() throws Exception { movementConfig.close(); }
    @Rule public final KOMETileTestResources geometry = new KOMETileTestResources();

    @Before @After public void resetRelations() {
        relation("gondor", "mordor", LOTRFactionRelations.Relation.NEUTRAL);
        relation("rohan", "mordor", LOTRFactionRelations.Relation.NEUTRAL);
        relation("rohan", "gondor", LOTRFactionRelations.Relation.NEUTRAL);
    }

    @Test public void hostilePermissionIsTerminalOnlyAndUsesTileOwnerNotUnrelatedFaction() {
        Fixture f = new Fixture();
        KOMEArmyCompany attacker = f.company("C1", "gondor", "T100");
        f.tile("T100", "gondor");
        f.tile("T101", "mordor");
        relation("gondor", "mordor", LOTRFactionRelations.Relation.ENEMY);
        assertEquals(KOMEConflictMovementService.PermissionCode.HOSTILE_ATTACK_ALLOWED,
            KOMEConflictMovementService.evaluateHostileDestination(
                f.data, attacker, "T101", true).code);
        assertEquals(KOMEConflictMovementService.PermissionCode.NOT_TERMINAL_DESTINATION,
            KOMEConflictMovementService.evaluateHostileDestination(
                f.data, attacker, "T101", false).code);

        f.tile("T101", "rohan");
        relation("gondor", "rohan", LOTRFactionRelations.Relation.NEUTRAL);
        // Mordor remains hostile, but is now unrelated to destination authority.
        assertEquals(KOMEConflictMovementService.PermissionCode.NORMAL_MOVEMENT_RULE,
            KOMEConflictMovementService.evaluateHostileDestination(
                f.data, attacker, "T101", true).code);
    }

    @Test public void unknownOwnerAndNonHostileRelationsFailClosed() {
        Fixture f = new Fixture();
        KOMEArmyCompany attacker = f.company("C1", "gondor", "T100");
        f.tile("T100", "gondor");
        f.tile("T101", "");
        assertEquals(KOMEConflictMovementService.PermissionCode.INVALID_TILE_OWNER,
            KOMEConflictMovementService.evaluateHostileDestination(
                f.data, attacker, "T101", true).code);
        f.tile("T101", "mordor");
        for (LOTRFactionRelations.Relation relation : Arrays.asList(
                LOTRFactionRelations.Relation.NEUTRAL,
                LOTRFactionRelations.Relation.FRIEND,
                LOTRFactionRelations.Relation.ALLY)) {
            relation("gondor", "mordor", relation);
            assertEquals(KOMEConflictMovementService.PermissionCode.NORMAL_MOVEMENT_RULE,
                KOMEConflictMovementService.evaluateHostileDestination(
                    f.data, attacker, "T101", true).code);
        }
    }

    @Test public void movementAccessExceptionCannotAuthorizeHostileTransit() {
        Fixture f = new Fixture();
        f.tile("T100", "gondor"); f.tile("T101", "mordor");
        f.tile("T102", "mordor");
        relation("gondor", "mordor", LOTRFactionRelations.Relation.ENEMY);
        KOMEArmyCompany company = f.company("C1", "gondor", "T100");
        KOMEArmyMovementOrder order = f.order("M1", "C1", "T100", "T102");
        order.routeTiles.clear();
        order.routeTiles.add("T100"); order.routeTiles.add("T101");
        order.routeTiles.add("T102");
        order.finalRouteIndex = 2; order.totalSteps = 2; order.distanceTiles = 2;
        order.hostileAttackDestination = "T102";
        order.currentStepDestinationTile = order.nextTile = "T101";
        assertFalse(KOMEMovementAccessService.isMovementStepAuthorized(
            f.data, order, "T100", "T101", false));
        order.currentStepDestinationTile = order.nextTile = "T102";
        order.nextRouteIndex = 2;
        assertTrue(KOMEMovementAccessService.isMovementStepAuthorized(
            f.data, order, "T100", "T102", false));
        assertSame(company, f.data.armyCompanies.get("C1"));
    }

    @Test public void verifiedHostileArrivalCreatesOrdinaryAndHoldsPreservedRoute() {
        Fixture f = hostileFixture(false, false);
        KOMEConflictMovementService.ArrivalPreparation prepared = f.prepare("M1", 10L);
        assertTrue(prepared.reason, prepared.ready());
        assertNull(f.data.getConflictService().get("T101"));
        f.publish("M1", "T101");
        KOMEConflictMovementService.ArrivalCommitment accepted =
            KOMEConflictMovementService.commitLegalArrival(f.data,
                prepared.receipt, "server");
        assertTrue(accepted.reason, accepted.success());
        KOMEConflictRecord conflict = accepted.conflictResult.record;
        assertEquals(State.ORDINARY, conflict.getState());
        assertEquals("mordor", conflict.getCommitments().get("C1")
            .validatedEvent.authorityFactionId);
        KOMEArmyMovementOrder order = f.data.armyMovements.get("M1");
        int routeSize = order.routeTiles.size();
        int allowance = order.dailyStepsRemaining;
        KOMEConflictMovementService.applyConflictHold(f.data, order, conflict, 10L);
        assertEquals(KOMEArmyMovementOrder.CONFLICT_HELD, order.status);
        assertEquals(conflict.getConflictId(), order.conflictHoldId);
        assertEquals(routeSize, order.routeTiles.size());
        assertEquals(allowance, order.dailyStepsRemaining);
        assertEquals("M1", f.data.armyCompanies.get("C1").movementOrderId);
        assertEquals("T101", f.data.armyCompanies.get("C1").currentTile);
        int auditsAfterHold = f.data.centralAudit.size();
        KOMEConflictMovementService.applyConflictHold(f.data, order, conflict, 11L);
        assertEquals(auditsAfterHold, f.data.centralAudit.size());
        relation("gondor", "mordor", LOTRFactionRelations.Relation.NEUTRAL);
        assertFalse(KOMEMovementAccessService.revalidateAll(f.data, 12L));
        assertEquals(KOMEArmyMovementOrder.CONFLICT_HELD, order.status);
        assertSame(conflict, f.data.getConflictService().get("T101"));
    }

    @Test public void defensiveArrivalCreatesEncirclementWithStrategicGarrisonOnly() {
        Fixture f = hostileFixture(true, false);
        KOMEArmyCompany defender = f.company("C2", "mordor", "T101");
        UUID defenderMember = defender.units.get(0);
        KOMEArmyCompany ally = f.company("C3", "rohan", "T101");
        UUID allyMember = ally.units.get(0);
        relation("rohan", "mordor", LOTRFactionRelations.Relation.ALLY);
        KOMEHiredUnitRecord ordinary = f.record("", "mordor", "T101",
            KOMEHiredUnitClass.ORDINARY);
        f.data.hiredUnits.put(ordinary.entity, ordinary);

        KOMEConflictMovementService.ArrivalPreparation prepared = f.prepare("M1", 20L);
        assertTrue(prepared.reason, prepared.ready());
        assertEquals(2, prepared.receipt.originalGarrison.size());
        f.publish("M1", "T101");
        KOMEConflictRecord conflict = f.commit(prepared);
        assertEquals(State.ENCIRCLEMENT, conflict.getState());
        assertEquals(Collections.singleton(defenderMember),
            conflict.getOriginalGarrison().get("C2").members.keySet());
        assertEquals(Collections.singleton(allyMember),
            conflict.getOriginalGarrison().get("C3").members.keySet());
        assertFalse(conflict.getOriginalGarrison().containsKey("C1"));
        assertFalse(conflict.getOriginalGarrison().get("C2").members
            .containsKey(ordinary.entity));
    }

    @Test public void zeroGarrisonStillCreatesEncirclement() {
        Fixture f = hostileFixture(true, false);
        KOMEConflictMovementService.ArrivalPreparation prepared = f.prepare("M1", 30L);
        assertTrue(prepared.ready());
        assertTrue(prepared.receipt.originalGarrison.isEmpty());
        f.publish("M1", "T101");
        assertEquals(State.ENCIRCLEMENT, f.commit(prepared).getState());
    }

    @Test public void existingConflictJoinsOnceAndExactReceiptReplayIsIdempotent() {
        Fixture f = hostileFixture(false, false);
        KOMEConflictMovementService.ArrivalPreparation prepared = f.prepare("M1", 40L);
        f.publish("M1", "T101");
        KOMEConflictRecord conflict = f.commit(prepared);
        int audits = f.data.centralAudit.size();
        KOMEConflictMovementService.ArrivalCommitment replay =
            KOMEConflictMovementService.commitLegalArrival(f.data,
                prepared.receipt, "server");
        assertEquals(Code.ALREADY_COMMITTED_SAME_CONFLICT,
            replay.conflictResult.code);
        assertEquals(conflict.getRevision(), replay.conflictResult.record.getRevision());
        assertEquals(audits, f.data.centralAudit.size());

        KOMEConflictMovementService.ArrivalPreparation reconstructed =
            f.prepare("M1", 40L);
        assertTrue(reconstructed.reason, reconstructed.ready());
        KOMEConflictMovementService.ArrivalCommitment retried =
            KOMEConflictMovementService.commitLegalArrival(f.data,
                reconstructed.receipt, "server");
        assertEquals(Code.ALREADY_COMMITTED_SAME_CONFLICT,
            retried.conflictResult.code);
        assertEquals(conflict.getRevision(), retried.conflictResult.record.getRevision());
        assertEquals(audits, f.data.centralAudit.size());

        Fixture.Join joining = f.joining("C4", "gondor", "T100", "M4", 50L);
        assertTrue(joining.preparation.ready());
        f.publish("M4", "T101");
        KOMEConflictRecord joined = f.commit(joining.preparation);
        assertEquals(conflict.getConflictId(), joined.getConflictId());
        assertEquals(2, joined.getCommitments().size());
    }

    @Test public void materiallyDifferentArrivalReceiptIsNotAnIdempotentReplay() {
        Fixture f = hostileFixture(false, false);
        KOMEConflictMovementService.ArrivalPreparation first = f.prepare("M1", 55L);
        KOMEConflictMovementService.ArrivalPreparation changed = f.prepare("M1", 56L);
        assertTrue(first.ready()); assertTrue(changed.ready());
        f.publish("M1", "T101");
        f.commit(first);
        KOMEConflictMovementService.ArrivalCommitment rejected =
            KOMEConflictMovementService.commitLegalArrival(f.data,
                changed.receipt, "server");
        assertFalse(rejected.success());
        assertEquals(Code.DUPLICATE_DETACHMENT, rejected.conflictResult.code);
    }

    @Test public void neutralPresenceDoesNothingAndLaterHostileAlliedReliefIsExterior() {
        Fixture f = hostileFixture(true, true);
        KOMEConflictMovementService.ArrivalPreparation attack = f.prepare("M1", 60L);
        f.publish("M1", "T101");
        KOMEConflictRecord conflict = f.commit(attack);
        int garrisonSize = conflict.getOriginalGarrison().size();

        Fixture.Join relief = f.joining("C5", "rohan", "T100", "M5", 70L);
        assertFalse(relief.preparation.ready());
        assertFalse(conflict.getCommitments().containsKey("C5"));
        relation("rohan", "mordor", LOTRFactionRelations.Relation.ALLY);
        relation("rohan", "gondor", LOTRFactionRelations.Relation.ENEMY);
        relief = f.joiningExisting("C5", "rohan", "T100", "M5", 70L);
        assertTrue(relief.preparation.reason, relief.preparation.ready());
        assertEquals(EntryOrigin.RELIEF, relief.preparation.receipt.origin);
        assertFalse(conflict.getCommitments().containsKey("C5"));
        f.publish("M5", "T101");
        conflict = f.commit(relief.preparation);
        assertEquals(EntryOrigin.RELIEF, conflict.getCommitments().get("C5").origin);
        assertEquals(garrisonSize, conflict.getOriginalGarrison().size());
        assertFalse(conflict.getOriginalGarrison().containsKey("C5"));
    }

    @Test public void conflictHoldRoundTripsAndNeverLooksLikeWaitingNextStep() {
        KOMEArmyMovementOrder order = new KOMEArmyMovementOrder();
        order.id = "M1"; order.companyId = "C1";
        order.status = KOMEArmyMovementOrder.CONFLICT_HELD;
        order.conflictHoldId = "CF9";
        order.conflictHeldAtMillis = 99L;
        order.hostileAttackDestination = "T101";
        order.routeTiles.add("T100"); order.routeTiles.add("T101");
        order.routeTiles.add("T102");
        order.currentRouteIndex = 1; order.nextRouteIndex = 2;
        order.dailyStepsRemaining = 1;
        KOMEArmyMovementOrder restored = new KOMEArmyMovementOrder();
        restored.readFromNBT(order.writeToNBT());
        assertEquals(KOMEArmyMovementOrder.CONFLICT_HELD, restored.status);
        assertEquals("CF9", restored.conflictHoldId);
        assertEquals(99L, restored.conflictHeldAtMillis);
        assertEquals("T101", restored.hostileAttackDestination);
        assertEquals(order.routeTiles, restored.routeTiles);
        assertEquals(1, restored.dailyStepsRemaining);
        assertFalse(KOMEArmyMovementOrder.WAITING_NEXT_STEP.equals(restored.status));
    }

    @Test public void activeCommitmentBlocksReorganizationAndAdmissionEnlargement() {
        Fixture f = hostileFixture(false, false);
        KOMEConflictMovementService.ArrivalPreparation prepared = f.prepare("M1", 80L);
        f.publish("M1", "T101");
        KOMEConflictRecord conflict = f.commit(prepared);
        KOMEConflictMovementService.applyConflictHold(f.data,
            f.data.armyMovements.get("M1"), conflict, 80L);
        KOMECompanyReorganizationService.Result split =
            KOMECompanyReorganizationService.INSTANCE.split(f.data,
                f.data.armyCompanies.get("C1").owner, "C1",
                Collections.singleton(f.data.armyCompanies.get("C1").units.get(0)));
        assertFalse(split.success);
        assertTrue(split.code == KOMECompanyReorganizationService.Code.ROUTE_ACTIVE
            || split.code == KOMECompanyReorganizationService.Code.CONFLICT_COMMITTED);

        KOMEHiredUnitRecord hire = f.record("", "gondor", "T101",
            KOMEHiredUnitClass.CAMPAIGN);
        hire.owner = f.data.armyCompanies.get("C1").owner;
        hire.sourcePlayer = hire.owner;
        f.data.lastKnownPlayerFactions.put(hire.owner, "gondor");
        f.data.hiredUnits.put(hire.entity, hire);
        KOMECampaignCompanyAdmissionService.Result admission =
            KOMECampaignCompanyAdmissionService.INSTANCE.admit(f.data, hire,
                "Owner", "T101");
        assertTrue(admission.reason, admission.success);
        assertTrue(admission.createdNew);
        assertNotEquals("C1", admission.company.id);
    }

    @Test public void pledgeCleanupCannotEraseCommittedDetachmentUnitsOrHold() {
        Fixture f = hostileFixture(false, false);
        KOMEConflictMovementService.ArrivalPreparation prepared = f.prepare("M1", 85L);
        f.publish("M1", "T101");
        KOMEConflictRecord conflict = f.commit(prepared);
        KOMEArmyMovementOrder order = f.data.armyMovements.get("M1");
        KOMEConflictMovementService.applyConflictHold(f.data, order, conflict, 85L);
        KOMEArmyCompany company = f.data.armyCompanies.get("C1");
        UUID unitId = company.units.get(0);

        KOMEPledgeReleaseService.Result released = KOMEPledgeReleaseService.release(
            f.data, company.owner, "Owner", "gondor", "rohan", 86L,
            "test pledge transition");

        assertEquals(1, released.conflictProtectedUnits);
        assertEquals(1, released.conflictProtectedCompanies);
        assertEquals(1, released.conflictProtectedMovements);
        assertSame(company, f.data.armyCompanies.get("C1"));
        assertNotNull(f.data.hiredUnits.get(unitId));
        assertSame(order, f.data.armyMovements.get("M1"));
        assertEquals(KOMEArmyMovementOrder.CONFLICT_HELD, order.status);
        assertTrue(f.data.getConflictService().get("T101").getCommitments()
            .containsKey("C1"));
    }

    @Test public void ordinaryReconciliationRetainsMissingCommittedDetachmentShell() {
        Fixture f = hostileFixture(false, false);
        KOMEConflictMovementService.ArrivalPreparation prepared = f.prepare("M1", 87L);
        f.publish("M1", "T101");
        f.commit(prepared);
        KOMEArmyCompany company = f.data.armyCompanies.get("C1");
        for (UUID unitId : company.units) f.data.hiredUnits.remove(unitId);
        company.units.clear();
        company.movementOrderId = "";
        company.status = KOMEArmyCompany.STATIONED;
        f.data.armyMovements.remove("M1");

        KOMECompanyReconciliationService.Result result =
            KOMECompanyReconciliationService.INSTANCE.reconcile(f.data);

        assertSame(company, f.data.armyCompanies.get("C1"));
        assertEquals(1, result.emptyCompaniesRetainedForConflict);
        assertTrue(result.issues.toString(), result.issues.stream().anyMatch(issue ->
            issue.code == KOMECompanyReconciliationService.IssueCode
                .EMPTY_COMPANY_RETAINED_FOR_CONFLICT));
    }

    @Test public void failedPreparationCreatesNoConflictHoldOrStrategicMutation() {
        Fixture f = hostileFixture(false, false);
        relation("gondor", "mordor", LOTRFactionRelations.Relation.NEUTRAL);
        KOMEArmyMovementOrder order = f.data.armyMovements.get("M1");
        String companyTile = f.data.armyCompanies.get("C1").currentTile;
        KOMEConflictMovementService.ArrivalPreparation rejected = f.prepare("M1", 90L);
        assertFalse(rejected.ready());
        assertNull(f.data.getConflictService().get("T101"));
        assertEquals("", order.conflictHoldId);
        assertEquals(companyTile, f.data.armyCompanies.get("C1").currentTile);
    }

    @Test public void verifiedUuidRekeyPreservesHistoricalGarrisonCohort() {
        Fixture f = hostileFixture(true, true);
        KOMEConflictMovementService.ArrivalPreparation attack = f.prepare("M1", 100L);
        UUID oldId = f.data.armyCompanies.get("C2").units.get(0);
        f.publish("M1", "T101");
        KOMEConflictRecord conflict = f.commit(attack);
        UUID newId = UUID.fromString("90000000-0000-0000-0000-000000000001");
        KOMEConflictService.Result rekey = f.data.getConflictService()
            .rekeyOriginalGarrisonMembers(f.data, "T101",
                ExpectedConflict.at(conflict.getConflictId(), conflict.getRevision()),
                Collections.singletonMap(oldId, newId),
                new Context(101L, "server", "verified UUID replacement"));
        assertEquals(Code.SUCCESS, rekey.code);
        assertFalse(rekey.record.getOriginalGarrison().get("C2").members
            .containsKey(oldId));
        assertEquals(GarrisonMemberState.ORIGINAL,
            rekey.record.getOriginalGarrison().get("C2").members.get(newId));
        assertEquals(1, rekey.record.getOriginalGarrison().get("C2").members.size());
        KOMEConflictService restored = KOMEConflictPersistence.read(
            KOMEConflictPersistence.write(f.data.getConflictService()));
        assertEquals(GarrisonMemberState.ORIGINAL, restored.get("T101")
            .getOriginalGarrison().get("C2").members.get(newId));
        assertEquals(Operation.GARRISON_MEMBER_REKEY,
            restored.get("T101").getLastTransition().operation);
    }

    @Test public void restartRestoresConflictCommitmentAndHoldWithoutAutoResume() {
        Fixture f = hostileFixture(false, false);
        f.data.initializeIntegratedWorld();
        KOMEConflictMovementService.ArrivalPreparation prepared = f.prepare("M1", 110L);
        f.publish("M1", "T101");
        KOMEConflictRecord conflict = f.commit(prepared);
        KOMEArmyMovementOrder order = f.data.armyMovements.get("M1");
        KOMEConflictMovementService.applyConflictHold(f.data, order, conflict, 110L);
        int allowance = order.dailyStepsRemaining;
        NBTTagCompound saved = new NBTTagCompound();
        f.data.writeToNBT(saved);
        KOMEWorldData restored = new KOMEWorldData("phase4-restored");
        restored.readFromNBT(saved);
        KOMEArmyMovementOrder held = restored.armyMovements.get("M1");
        assertNotNull(held);
        assertEquals(KOMEArmyMovementOrder.CONFLICT_HELD, held.status);
        assertEquals(conflict.getConflictId(), held.conflictHoldId);
        assertEquals(allowance, held.dailyStepsRemaining);
        assertTrue(restored.getConflictService().get("T101").getCommitments()
            .containsKey("C1"));
        assertEquals("T101", restored.armyCompanies.get("C1").currentTile);
        assertEquals("M1", restored.armyCompanies.get("C1").movementOrderId);

        NBTTagCompound malformed = (NBTTagCompound) saved.copy();
        NBTTagList movements = malformed.getTagList("ArmyMovements", 10);
        movements.getCompoundTagAt(0).setString("ConflictHoldId", "CF999");
        try {
            new KOMEWorldData("bad-hold").readFromNBT(malformed);
            fail("Mismatched conflict hold must fail closed.");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("Conflict movement holds"));
        }
    }

    static Fixture hostileFixture(boolean defensive, boolean defenderCompany) {
        Fixture f = new Fixture();
        f.tile("T100", "gondor");
        f.tile("T101", "mordor");
        relation("gondor", "mordor", LOTRFactionRelations.Relation.ENEMY);
        f.company("C1", "gondor", "T100");
        f.order("M1", "C1", "T100", "T101");
        if (defensive) f.defensiveBuild("B1", "T101");
        if (defenderCompany) f.company("C2", "mordor", "T101");
        return f;
    }

    static final class Fixture {
        final KOMEWorldData data = new KOMEWorldData("phase4");
        private int uuidSequence = 1;

        void tile(String id, String faction) {
            KOMEConquestTile tile = data.conquestTiles.get(id);
            if (tile == null) {
                tile = new KOMEConquestTile(id);
                data.conquestTiles.put(id, tile);
            }
            tile.setCurrentRulingFaction(faction);
        }

        KOMEArmyCompany company(String id, String faction, String tile) {
            KOMEArmyCompany company = new KOMEArmyCompany();
            company.id = id;
            company.owner = new UUID(1L, id.hashCode());
            company.ownerName = "Owner";
            company.faction = company.nativeFaction = faction;
            company.currentTile = tile;
            company.name = id;
            company.status = KOMEArmyCompany.STATIONED;
            KOMEHiredUnitRecord record = record(id, faction, tile,
                KOMEHiredUnitClass.CAMPAIGN);
            record.owner = company.owner;
            record.sourcePlayer = company.owner;
            company.units.add(record.entity);
            company.totalPopulation = company.groundPopulation = record.cost;
            data.hiredUnits.put(record.entity, record);
            data.armyCompanies.put(id, company);
            return company;
        }

        KOMEHiredUnitRecord record(String companyId, String faction, String tile,
                KOMEHiredUnitClass classification) {
            KOMEHiredUnitRecord record = new KOMEHiredUnitRecord();
            record.entity = new UUID(2L, uuidSequence++);
            record.owner = new UUID(1L, clean(companyId).hashCode());
            record.sourcePlayer = record.owner;
            record.companyId = companyId;
            record.companyName = companyId;
            record.currentTile = tile;
            record.sourceTileId = tile;
            record.type = KOMEPopulationType.OFFENSIVE;
            record.cost = record.baseCost = record.populationSpent = 10;
            record.populationOwningFaction = faction;
            record.sourceFaction = faction;
            record.unitFaction = faction;
            record.assignPersistedUnitClass(classification);
            return record;
        }

        KOMEArmyMovementOrder order(String id, String companyId,
                String origin, String destination) {
            KOMEArmyCompany company = data.armyCompanies.get(companyId);
            KOMEArmyMovementOrder order = KOMEArmyMovementOrder.newRoute(1);
            order.id = id; order.companyId = companyId;
            order.owner = company.owner; order.ownerName = company.ownerName;
            order.ownerFaction = company.faction;
            order.originTile = origin; order.destinationTile = destination;
            order.finalDestinationTile = destination;
            order.currentTile = origin; order.nextTile = destination;
            order.currentStepOriginTile = origin;
            order.currentStepDestinationTile = destination;
            order.routeTiles.add(origin); order.routeTiles.add(destination);
            order.traveledRouteTiles.add(origin);
            order.currentRouteIndex = 0; order.nextRouteIndex = 1;
            order.finalRouteIndex = 1; order.totalSteps = 1; order.distanceTiles = 1;
            order.hostileAttackDestination = destination;
            order.status = KOMEArmyMovementOrder.MOVING;
            order.units.addAll(company.units);
            for (UUID unitId : company.units)
                data.hiredUnits.get(unitId).movementOrderId = id;
            company.status = KOMEArmyCompany.MOVING;
            company.movementOrderId = id;
            data.armyMovements.put(id, order);
            return order;
        }

        void defensiveBuild(String id, String tile) {
            KOMEPlayerBuild build = new KOMEPlayerBuild();
            build.id = id; build.tileId = tile;
            build.displayName = id; build.active = true;
            build.type = KOMEBuildType.DEFENSIVE;
            data.builds.put(id, build);
        }

        KOMEConflictMovementService.ArrivalPreparation prepare(String orderId,
                long timestamp) {
            data.armyMovements.get(orderId).arrivalMillis = timestamp;
            return KOMEConflictMovementService.prepareLegalArrival(data,
                data.armyMovements.get(orderId), "T101");
        }

        void publish(String orderId, String tile) {
            KOMEArmyMovementOrder order = data.armyMovements.get(orderId);
            KOMEArmyCompany company = data.armyCompanies.get(order.companyId);
            order.status = KOMEArmyMovementOrder.SPAWNING;
            order.currentTile = tile; order.currentRouteIndex = 1;
            order.completedSteps = 1;
            company.currentTile = tile;
            for (UUID unitId : company.units)
                data.hiredUnits.get(unitId).currentTile = tile;
        }

        KOMEConflictRecord commit(
                KOMEConflictMovementService.ArrivalPreparation preparation) {
            KOMEConflictMovementService.ArrivalCommitment result =
                KOMEConflictMovementService.commitLegalArrival(data,
                    preparation.receipt, "server");
            assertTrue(result.reason, result.success());
            return result.conflictResult.record;
        }

        Join joining(String companyId, String faction, String origin,
                String orderId, long timestamp) {
            if (!data.armyCompanies.containsKey(companyId)) company(companyId, faction, origin);
            if (!data.armyMovements.containsKey(orderId)) order(orderId, companyId, origin, "T101");
            return new Join(prepare(orderId, timestamp));
        }

        Join joiningExisting(String companyId, String faction, String origin,
                String orderId, long timestamp) {
            return joining(companyId, faction, origin, orderId, timestamp);
        }

        final class Join {
            final KOMEConflictMovementService.ArrivalPreparation preparation;
            Join(KOMEConflictMovementService.ArrivalPreparation preparation) {
                this.preparation = preparation;
            }
        }
    }

    private static void relation(String first, String second,
            LOTRFactionRelations.Relation relation) {
        LOTRFactionRelations.overrideRelations(KOMEAlliance.findLotrFaction(first),
            KOMEAlliance.findLotrFaction(second), relation);
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
