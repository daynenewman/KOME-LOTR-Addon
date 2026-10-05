package kome.common.data;

import java.util.ArrayList;
import java.util.UUID;
import lotr.common.fac.LOTRFactionRelations;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.*;
import kome.common.data.KOMEConflictContracts.Context;
import kome.common.data.KOMEConflictContracts.ExpectedConflict;
import kome.common.data.KOMEConflictContracts.ValidatedDepartureRequest;
import static kome.common.data.KOMEConflictMovementHandoff.*;
import static org.junit.Assert.*;

public class KOMEConflictMovementHandoffTest {
    @Rule public final KOMETileTestResources geometry = new KOMETileTestResources();
    private KOMEPopulationTestConfig config;
    private KOMEConflictMovementServiceTest.Fixture f;
    private KOMEConflictRecord ended;
    private KOMEArmyMovementOrder order;
    @Before public void setup() throws Exception {
        config = new KOMEPopulationTestConfig();
        f = KOMEConflictMovementServiceTest.hostileFixture(false, false);
        KOMEConflictMovementService.ArrivalPreparation arrival = f.prepare("M1", 10L);
        f.publish("M1", "T101");
        KOMEConflictRecord active = f.commit(arrival);
        order = f.data.armyMovements.get("M1");
        KOMEConflictMovementService.applyConflictHold(f.data, order, active, 10L);
        KOMEConflictService.EndResult end = f.data.getConflictService().endWithMovementHandoff(f.data,
            "T101", ExpectedConflict.at(active.getConflictId(), active.getRevision()), context(20L),
            KOMEConflictService.EndSource.LIFECYCLE);
        assertTrue(end.conflictResult.reason, end.isSuccess()); ended = end.conflictResult.record;
        KOMEMovementDayService.initializeNewCompany(f.data.armyCompanies.get("C1"), java.time.Instant.parse("2026-01-10T12:00:00Z").toEpochMilli());
        f.data.armyCompanies.get("C1").movementAllowance = 1;
        f.data.setDirty(false);
    }
    @After public void cleanup() throws Exception {
        config.close(); relation(LOTRFactionRelations.Relation.NEUTRAL);
    }
    private static Context context(long at) { return new Context(at, "server", "validated movement consequence"); }
    private Result apply(Outcome outcome) {
        return applyEndedConflict(f.data, "T101", ExpectedConflict.at(ended.getConflictId(), ended.getRevision()), "M1", outcome, context(30L));
    }
    private void queued(int credit) {
        // Later conflict code establishes legal ownership/passage, not the movement handoff.
        f.tile("T101", "gondor");
        f.tile("T102", "gondor");
        f.data.setRouteEdge("T101", "T102", KOMEConquestRouteEdge.OPEN, "fixture", 0, 10, 64, 10, "test");
        order.routeTiles.add("T102"); order.finalRouteIndex = order.distanceTiles = order.totalSteps = 2;
        order.destinationTile = order.finalDestinationTile = "T102";
        f.data.armyCompanies.get("C1").movementAllowance = credit;
        f.data.setDirty(false);
    }
    @Test public void completedHostileArrivalNeverReplaysAfterResume() {
        ArrayList<String> route = new ArrayList<String>(order.routeTiles);
        assertEquals(Code.ROUTE_ALREADY_COMPLETE, apply(Outcome.RESUME_ROUTE).code);
        assertEquals(KOMEArmyMovementOrder.ARRIVED, order.status);
        assertEquals(route, order.routeTiles); assertEquals(1, order.completedSteps);
        assertEquals(1, f.data.armyCompanies.get("C1").movementAllowance);
        assertEquals("T101", f.data.armyCompanies.get("C1").currentTile);
        assertFalse(order.hasArrived(Long.MAX_VALUE));
        assertEquals("", f.data.armyCompanies.get("C1").movementOrderId);
    }
    @Test public void queuedResumeRetainsIdentityCreditAndUsesGuardedDeparture() {
        queued(1); ArrayList<String> route = new ArrayList<String>(order.routeTiles);
        assertEquals(Code.ROUTE_RESUMED, apply(Outcome.RESUME_ROUTE).code);
        assertEquals(KOMEArmyMovementOrder.WAITING_NEXT_STEP, order.status);
        assertEquals(route, order.routeTiles); assertEquals(1, order.currentRouteIndex);
        assertEquals("M1", f.data.armyCompanies.get("C1").movementOrderId);
        assertEquals(1, f.data.armyCompanies.get("C1").movementAllowance);
        assertTrue(KOMEMovementDepartureGuard.evaluate(f.data, order).allowed());
        assertTrue(KOMEMovementDayService.depart(f.data, order, true,
            () -> KOMEMovementDepartureGuard.evaluate(f.data, order).allowed()));
        assertEquals(0, f.data.armyCompanies.get("C1").movementAllowance);
        assertEquals(1, order.completedSteps);
    }
    @Test public void exhaustedResumeWaitsForBoundaryWithoutCredit() {
        queued(0);
        assertEquals(Code.WAITING_FOR_ALLOWANCE, apply(Outcome.RESUME_ROUTE).code);
        assertTrue(order.nextStepDepartureMillis > 30L);
        assertEquals(0, f.data.armyCompanies.get("C1").movementAllowance);
        assertFalse(KOMEMovementDayService.depart(f.data, order, true, () -> { fail("no departure"); return true; }));
    }
    @Test public void changedDiplomacyStopsResumeSafely() {
        queued(1); f.tile("T102", "mordor"); relation(LOTRFactionRelations.Relation.ENEMY);
        assertEquals(Code.NEXT_EDGE_ILLEGAL, apply(Outcome.RESUME_ROUTE).code);
        assertTrue(order.pendingSpawnReason, order.pendingSpawnReason.contains("ACCESS_LOST"));
        assertEquals(KOMEArmyMovementOrder.STOPPED, order.status);
        assertEquals(1, f.data.armyCompanies.get("C1").movementAllowance);
    }
    @Test public void missingAndBlockedEdgesStopResumeWithoutSpending() {
        for (String kind : new String[]{null, KOMEConquestRouteEdge.MOUNTAIN}) {
            queued(1);
            if (kind == null) f.data.routeEdges.remove(KOMEConquestRouteEdge.key("T101", "T102"));
            else f.data.setRouteEdge("T101", "T102", kind, "closed", 0, 0, 0, 0, "test");
            assertEquals(Code.NEXT_EDGE_ILLEGAL, apply(Outcome.RESUME_ROUTE).code);
            assertEquals(1, f.data.armyCompanies.get("C1").movementAllowance);
            order.conflictRelease = null; order.status = KOMEArmyMovementOrder.CONFLICT_RELEASED_PAUSED;
        }
    }
    @Test public void explicitPassAndBridgeRemainLegalOnResume() {
        for (String kind : new String[]{KOMEConquestRouteEdge.MOUNTAIN_PASS, KOMEConquestRouteEdge.BRIDGE}) {
            queued(1); f.data.setRouteEdge("T101", "T102", kind, "explicit", 0, 0, 0, 0, "test");
            assertEquals(Code.ROUTE_RESUMED, apply(Outcome.RESUME_ROUTE).code);
            order.conflictRelease = null; order.status = KOMEArmyMovementOrder.CONFLICT_RELEASED_PAUSED;
        }
    }
    @Test public void releaseReplayIsReadOnlyAfterRestartAndContradictionRejected() {
        queued(1); assertEquals(Code.ROUTE_RESUMED, apply(Outcome.RESUME_ROUTE).code);
        NBTTagCompound tag = new NBTTagCompound(); f.data.writeToNBT(tag);
        KOMEWorldData loaded = new KOMEWorldData("restart"); loaded.readFromNBT(tag); loaded.setDirty(false);
        NBTTagCompound before = loaded.armyMovements.get("M1").writeToNBT();
        assertEquals(Code.REPLAYED, applyEndedConflict(loaded, "T101", ExpectedConflict.at(ended.getConflictId(), ended.getRevision()),
            "M1", Outcome.RESUME_ROUTE, context(40L)).code);
        assertEquals(before, loaded.armyMovements.get("M1").writeToNBT()); assertFalse(loaded.isDirty());
        assertEquals(1, loaded.armyCompanies.get("C1").movementAllowance);
        assertEquals(Code.CONTRADICTORY_OUTCOME, applyEndedConflict(loaded, "T101", ExpectedConflict.at(ended.getConflictId(), ended.getRevision()),
            "M1", Outcome.DEFEATED, context(40L)).code);
        assertFalse(loaded.isDirty());
    }
    @Test public void restartAfterEndBeforeOutcomeCanResumeExactOrder() {
        NBTTagCompound tag = new NBTTagCompound(); f.data.writeToNBT(tag);
        KOMEWorldData loaded = new KOMEWorldData("restart"); loaded.readFromNBT(tag);
        assertEquals(Code.ROUTE_ALREADY_COMPLETE, applyEndedConflict(loaded, "T101",
            ExpectedConflict.at(ended.getConflictId(), ended.getRevision()), "M1", Outcome.RESUME_ROUTE, context(30L)).code);
    }
    @Test public void restartedResumeRechecksEdgeBeforeStagingOrCreditSpending() throws Exception {
        queued(1); assertEquals(Code.ROUTE_RESUMED, apply(Outcome.RESUME_ROUTE).code);
        NBTTagCompound tag = new NBTTagCompound(); f.data.writeToNBT(tag);
        KOMEWorldData loaded = new KOMEWorldData("restart"); loaded.readFromNBT(tag);
        loaded.setRouteEdge("T101", "T102", KOMEConquestRouteEdge.MOUNTAIN, "new barrier", 0, 0, 0, 0, "test");
        KOMEArmyMovementOrder restored = loaded.armyMovements.get("M1");
        java.lang.reflect.Method tick = kome.common.command.KOMECommandTroops.class.getDeclaredMethod(
            "processWaitingStepDepartures", KOMEWorldData.class, net.minecraft.world.World.class, long.class);
        tick.setAccessible(true); tick.invoke(null, loaded, null, 40L);
        assertEquals(1, loaded.armyCompanies.get("C1").movementAllowance);
        assertEquals(1, restored.currentRouteIndex); assertEquals(0L, restored.lastChunkLoadAttemptMillis);
        assertEquals("ROUTE_EDGE_BLOCKED", restored.lastSpawnFailureCode);
        for (UUID id : restored.units) assertNull(loaded.hiredUnits.get(id).movingEntityData);
    }
    @Test public void duplicateResumeAfterDepartureCannotRestoreSpentCredit() {
        queued(1); apply(Outcome.RESUME_ROUTE);
        assertTrue(KOMEMovementDayService.depart(f.data, order, true, () -> true));
        NBTTagCompound before = order.writeToNBT(); f.data.setDirty(false);
        assertEquals(Code.REPLAYED, apply(Outcome.RESUME_ROUTE).code);
        assertEquals(0, f.data.armyCompanies.get("C1").movementAllowance);
        assertEquals(before, order.writeToNBT()); assertFalse(f.data.isDirty());
    }
    @Test public void incoherentNativeIdentityCannotResume() {
        f.data.hiredUnits.get(order.units.get(0)).owner = new UUID(77L, 77L);
        assertEquals(Code.INVALID_RELEASE, apply(Outcome.RESUME_ROUTE).code);
        assertEquals(KOMEArmyMovementOrder.CONFLICT_RELEASED_PAUSED, order.status);
        assertFalse(f.data.isDirty());
    }
    @Test public void newerRouteCannotBeOverwrittenByOldRelease() {
        f.data.armyCompanies.get("C1").movementOrderId = "M2";
        NBTTagCompound before = order.writeToNBT();
        assertEquals(Code.STALE_HANDOFF, apply(Outcome.RESUME_ROUTE).code);
        assertEquals(before, order.writeToNBT()); assertFalse(f.data.isDirty());
        assertEquals(1, f.data.armyCompanies.get("C1").movementAllowance);
    }
    @Test public void allCancellationOutcomesKeepAuditRouteAndNeverGrantCredit() {
        for (Outcome outcome : new Outcome[]{Outcome.DEFEATED, Outcome.DESTROYED, Outcome.FORMAL_RETREAT}) {
            ArrayList<String> route = new ArrayList<String>(order.routeTiles);
            assertTrue(apply(outcome).accepted());
            assertEquals(KOMEArmyMovementOrder.CANCELLED, order.status);
            assertEquals(route, order.routeTiles); assertEquals(outcome, order.conflictRelease.outcome);
            assertEquals(1, f.data.armyCompanies.get("C1").movementAllowance);
            assertEquals("", f.data.armyCompanies.get("C1").movementOrderId);
            f.data.setDirty(false); assertEquals(Code.REPLAYED, apply(outcome).code); assertFalse(f.data.isDirty());
            order.conflictRelease = null; order.status = KOMEArmyMovementOrder.CONFLICT_RELEASED_PAUSED;
            f.data.armyCompanies.get("C1").movementOrderId = order.id;
            for (UUID unit : order.units) f.data.hiredUnits.get(unit).movementOrderId = order.id;
        }
    }
    @Test public void destructionCanBeRecordedAfterCompanyWasRemoved() {
        f.data.armyCompanies.remove("C1");
        assertEquals(Code.CANCELED_BY_DESTRUCTION, apply(Outcome.DESTROYED).code);
        assertFalse(f.data.armyCompanies.containsKey("C1"));
    }
    @Test public void nullOutcomeAndStaleRevisionFailClosed() {
        NBTTagCompound before = order.writeToNBT();
        assertEquals(Code.INVALID_RELEASE, apply(null).code);
        assertEquals(Code.INVALID_RELEASE, applyEndedConflict(f.data, "T101",
            ExpectedConflict.at(ended.getConflictId(), ended.getRevision() + 1L), "M1", Outcome.RESUME_ROUTE, context(30L)).code);
        assertEquals(before, order.writeToNBT()); assertFalse(f.data.isDirty());
    }
    @Test public void receiptTypesIdentityAndUnknownOutcomesRejectCorruption() {
        apply(Outcome.DEFEATED); NBTTagCompound tag = order.writeToNBT();
        tag.getCompoundTag("ConflictMovementRelease").setString("Outcome", "VICTORY_GUESS");
        try { new KOMEArmyMovementOrder().readFromNBT(tag); fail(); } catch (IllegalArgumentException expected) { }
        tag = order.writeToNBT(); tag.getCompoundTag("ConflictMovementRelease").setString("OrderId", "M2");
        try { new KOMEArmyMovementOrder().readFromNBT(tag); fail(); } catch (IllegalArgumentException expected) { }
    }
    @Test public void activeCommitmentReleaseConsumesOnlyValidatedDeparture() {
        // A fresh fixture avoids deriving a release from an already ended conflict.
        KOMEConflictMovementServiceTest.Fixture active = KOMEConflictMovementServiceTest.hostileFixture(false, false);
        KOMEConflictMovementService.ArrivalPreparation arrival = active.prepare("M1", 10L);
        active.publish("M1", "T101"); KOMEConflictRecord conflict = active.commit(arrival);
        KOMEConflictMovementService.applyConflictHold(active.data, active.data.armyMovements.get("M1"), conflict, 10L);
        ValidatedDepartureRequest request = new ValidatedDepartureRequest("T101", "C1", "gondor",
            ExpectedConflict.at(conflict.getConflictId(), conflict.getRevision()));
        assertEquals(Code.CANCELED_BY_FORMAL_RETREAT, releaseCommitment(active.data, request, "M1", Outcome.FORMAL_RETREAT, context(30L)).code);
        assertFalse(active.data.getConflictService().get("T101").getCommitments().containsKey("C1"));
        active.data.setDirty(false);
        assertEquals(Code.REPLAYED, releaseCommitment(active.data, request, "M1", Outcome.FORMAL_RETREAT, context(30L)).code);
        assertFalse(active.data.isDirty());
    }
    private static void relation(LOTRFactionRelations.Relation relation) {
        LOTRFactionRelations.overrideRelations(KOMEAlliance.findLotrFaction("gondor"), KOMEAlliance.findLotrFaction("mordor"), relation);
    }
}
