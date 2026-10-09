package kome.common.command;

import kome.common.KOMEAccessFixture;
import kome.common.data.*;
import kome.common.network.*;
import lotr.common.LOTRLevelData;
import lotr.common.LOTRPlayerData;
import lotr.common.fac.LOTRFaction;
import lotr.common.fac.LOTRFactionRelations;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.profiler.Profiler;
import net.minecraft.world.*;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.*;
import org.junit.*;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;
import static kome.common.command.KOMECompanyMovementService.*;
import static kome.common.data.KOMEConflictContracts.*;
import static kome.common.data.KOMEConflictRecord.*;

/** Exercises the extracted authority and the real legacy command adapters, without a game launch. */
public class KOMECompanyMovementServiceTest {
    @Rule public final KOMETileTestResources geometry = new KOMETileTestResources();
    private static final long NOW = Instant.parse("2026-01-10T12:00:00Z").toEpochMilli();
    private KOMEPopulationTestConfig config;
    @Before public void before() throws Exception {
        config = new KOMEPopulationTestConfig();
        relation(LOTRFactionRelations.Relation.NEUTRAL);
    }
    @After public void after() throws Exception {
        relation(LOTRFactionRelations.Relation.NEUTRAL); config.close();
    }
    private static void relation(LOTRFactionRelations.Relation value) {
        LOTRFactionRelations.overrideRelations(LOTRFaction.GONDOR, LOTRFaction.MORDOR, value);
    }
    @Test public void immediateFootMoveAndCommandPreviewAgree() throws Exception {
        try (Fixture f = new Fixture(false, 1)) {
            Evaluation e = f.evaluate(); assertTrue(e.denial == null ? "" : e.denial.reason, e.allowed());
            assertEquals(Status.CAN_START_AND_ARRIVE_NOW, e.status);
            assertTrue(e.canStartNow); assertTrue(e.canArriveNow); assertEquals(1, e.remainingAllowance);
            assertEquals(Arrays.asList("T001", "T002"), e.routeTiles); assertEquals(1, e.distanceTiles);
            assertPreviewParity(f, e);
        }
    }
    @Test public void immediateMountedMoveAndCommandPreviewAgree() throws Exception {
        try (Fixture f = new Fixture(true, 2)) {
            Evaluation e = f.evaluate(); assertTrue(e.allowed()); assertEquals(2, e.tilesPerDay);
            assertEquals(Status.CAN_START_AND_ARRIVE_NOW, e.status); assertTrue(e.canArriveNow);
            assertPreviewParity(f, e);
        }
    }
    @Test public void startsNowButFullDestinationNeedsFutureBoundary() throws Exception {
        try (Fixture f = new Fixture(false, 2)) {
            Evaluation e = f.evaluate(); assertTrue(e.allowed()); assertTrue(e.canStartNow); assertFalse(e.canArriveNow);
            assertEquals(Status.CAN_START_BUT_CONTINUES_LATER, e.status);
            assertEquals(1, e.dailyTiming.requiredBoundaries); assertTrue(e.dailyTiming.requiresFutureBoundary);
            assertTrue(e.dailyTiming.immediateMovementPossible); assertPreviewParity(f, e);
        }
    }
    @Test public void exhaustedStillHasLegalRouteButCannotQueueNewOrder() throws Exception {
        try (Fixture f = new Fixture(false, 2)) {
            f.company.movementAllowance = 0; Evaluation e = f.evaluate(); assertBlocked(e, "NO_ALLOWANCE");
            assertTrue(e.legalRoute); assertTrue(e.exhausted); assertFalse(e.canStartNow); assertFalse(e.canArriveNow);
            assertTrue(e.dailyTiming.nextBoundaryMillis > NOW);
            f.command.processCommand(f.player, new String[]{"previewmove", "C1", f.destination});
            assertEquals("NO_ALLOWANCE", f.command.failure.failureCode);
            assertEquals(e.routeTiles, f.command.failure.routeTiles);
            assertFalse(f.service.submitUnpreviewed(f.data, f.player, "C1", f.destination).accepted);
            assertTrue(f.data.armyMovements.isEmpty());
        }
    }
    @Test public void committedCompanyProjectsHoldAndDoesNotMutateConflict() throws Exception {
        try (Fixture f = new Fixture(false, 1)) {
            KOMEConflictService conflicts = f.data.getConflictService();
            KOMEConflictRecord r = conflicts.start("T001", State.ORDINARY, ExpectedConflict.absent(),
                Collections.<GarrisonSeed>emptyList(), new Context(NOW, "test", "fixture")).record;
            r = conflicts.commit("T001", ExpectedConflict.at(r.getConflictId(), r.getRevision()),
                new CommitmentInput("C1", KOMEHiredUnitClass.CAMPAIGN, EntryOrigin.LEGAL_ARRIVAL, "M0"),
                new Context(NOW, "test", "fixture")).record;
            assertNotNull(r); Evaluation e = f.evaluate(); assertBlocked(e, "CONFLICT_HELD");
            assertTrue(e.activelyCommitted); assertEquals(r.getConflictId(), e.heldConflictId); assertEquals("T001", e.heldConflictTile);
            assertEquals(r.getRevision(), e.heldConflictRevision); assertSame(r, conflicts.get("T001"));
            assertFalse(f.service.submitUnpreviewed(f.data, f.player, "C1", f.destination).accepted);
        }
    }
    @Test public void ordinaryMembersAreNeverStrategicCompanies() throws Exception {
        try (Fixture f = new Fixture(false, 1)) {
            classifyOrdinary(f.unit);
            Evaluation e = f.evaluate(); assertBlocked(e, "INELIGIBLE_MEMBER"); assertTrue(e.denial.reason.contains("ordinary"));
            assertFalse(f.service.submitUnpreviewed(f.data, f.player, "C1", f.destination).accepted);
            assertTrue(f.data.armyMovements.isEmpty());
        }
    }
    @Test public void unknownAndDeletedCompanyIdsFailClosed() throws Exception {
        try (Fixture f = new Fixture(false, 1)) {
            assertBlocked(f.service.evaluate(f.data, f.player, "missing", f.destination), "UNKNOWN_COMPANY");
            Evaluation old = f.evaluate(); f.data.armyCompanies.remove("C1");
            Submission result = f.submit(old); assertFalse(result.accepted); assertEquals("UNKNOWN_COMPANY", result.denial.code);
        }
    }
    @Test public void unauthorizedActorCannotSeeCompanySnapshotOrSubmit() throws Exception {
        try (Fixture f = new Fixture(false, 1)) {
            Evaluation old = f.evaluate(); f.company.owner = new UUID(9, 9);
            Evaluation e = f.evaluate(); assertBlocked(e, "UNAUTHORIZED"); assertEquals("", e.companyName);
            assertTrue(e.routeTiles.isEmpty()); assertFalse(f.submit(old).accepted); assertEquals(1, f.company.movementAllowance);
        }
    }
    @Test public void mismatchedStrategicMemberTileFailsWithoutRepair() throws Exception {
        try (Fixture f = new Fixture(false, 1)) {
            f.unit.currentTile = "T003"; NBTTagCompound before = f.unit.writeToNBT();
            assertBlocked(f.evaluate(), "INELIGIBLE_MEMBER"); assertEquals(before, f.unit.writeToNBT());
        }
    }
    @Test public void incoherentCachedCompositionFailsWithoutReconciliation() throws Exception {
        try (Fixture f = new Fixture(false, 1)) {
            f.company.totalPopulation++; assertBlocked(f.evaluate(), "INCOHERENT_COMPANY");
            assertEquals(11, f.company.totalPopulation);
        }
    }
    @Test public void mountainAndRiverEdgesAreBlockedBySharedRouteSearch() throws Exception {
        for (String type : Arrays.asList(KOMEConquestRouteEdge.MOUNTAIN, KOMEConquestRouteEdge.RIVER)) {
            try (Fixture f = new Fixture(false, 1)) {
                f.edge("T001", "T002", type); Evaluation e = f.evaluate(); assertEquals(Status.BLOCKED, e.status);
                assertEquals(type.equals(KOMEConquestRouteEdge.MOUNTAIN) ? "MOUNTAIN_EDGE_BLOCKED" : "RIVER_EDGE_BLOCKED", e.denial.code);
                assertFalse(e.blockers.isEmpty());
                f.command.processCommand(f.player, new String[]{"previewmove", "C1", f.destination});
                assertEquals(e.denial.code, f.command.failure.failureCode);
            }
        }
    }
    @Test public void bridgeAndPassRemainLegalAndAreProjected() throws Exception {
        for (String type : Arrays.asList(KOMEConquestRouteEdge.BRIDGE, KOMEConquestRouteEdge.MOUNTAIN_PASS)) {
            try (Fixture f = new Fixture(false, 1)) {
                f.edge("T001", "T002", type); Evaluation e = f.evaluate(); assertTrue(e.allowed());
                assertEquals(1, e.edgeNotes.size()); assertPreviewParity(f, e);
            }
        }
    }
    @Test public void hostileTerminalUsesExistingPermissionWithoutCreatingConflict() throws Exception {
        try (Fixture f = new Fixture(false, 1)) {
            f.data.conquestTiles.get("T002").setCurrentRulingFaction("mordor"); relation(LOTRFactionRelations.Relation.ENEMY);
            assertTrue(KOMEConflictMovementService.evaluateHostileDestination(f.data, f.company, f.destination, true).allowed());
            Evaluation e = f.evaluate(); assertTrue(e.allowed()); assertEquals(Consequence.EXPECTED_CREATE_CONFLICT, e.consequence);
            assertNull(f.data.getConflictService().get(f.destination)); assertPreviewParity(f, e);
            relation(LOTRFactionRelations.Relation.NEUTRAL); assertFalse(f.submit(e).accepted);
            assertTrue(f.data.armyMovements.isEmpty()); assertEquals(1, f.company.movementAllowance);
        }
    }
    @Test public void hostileTransitNeverBecomesLegal() throws Exception {
        try (Fixture f = new Fixture(false, 2)) {
            f.data.conquestTiles.get("T002").setCurrentRulingFaction("mordor"); relation(LOTRFactionRelations.Relation.ENEMY);
            assertFalse(f.evaluate().allowed()); assertTrue(f.data.armyMovements.isEmpty());
        }
    }
    @Test public void evaluationIsDetachedAndNonMutatingIncludingRepeatedCalls() throws Exception {
        try (Fixture f = new Fixture(false, 2)) {
            NBTTagCompound company = f.company.writeToNBT(), unit = f.unit.writeToNBT();
            Map<String, KOMEConflictRecord> conflicts = f.data.getConflictService().records();
            long sequence = f.data.getConflictService().getNextConflictSequence(); f.data.setDirty(false);
            Evaluation first = f.evaluate();
            for (int i = 0; i < 3; i++) {
                Evaluation repeat = f.evaluate(); assertEquals(first.previewFingerprint, repeat.previewFingerprint);
                assertEquals(first.confirmation.serviceFingerprint, repeat.confirmation.serviceFingerprint);
            }
            assertEquals(company, f.company.writeToNBT()); assertEquals(unit, f.unit.writeToNBT());
            assertTrue(f.data.armyMovements.isEmpty()); assertTrue(f.data.movementHistory.isEmpty());
            assertTrue(f.world.loadedEntityList.isEmpty()); assertEquals(conflicts, f.data.getConflictService().records());
            assertEquals(sequence, f.data.getConflictService().getNextConflictSequence()); assertFalse(f.data.isDirty());
            try { first.routeTiles.clear(); fail("Mutable route"); } catch (UnsupportedOperationException expected) { }
            f.company.name = "Changed"; assertEquals("Company", first.companyName);
        }
    }
    @Test public void submissionRevalidatesChangedEdgeAndCampaignIdentity() throws Exception {
        try (Fixture f = new Fixture(false, 1)) {
            Evaluation e = f.evaluate(); f.edge("T001", "T002", KOMEConquestRouteEdge.RIVER);
            assertFalse(f.submit(e).accepted); assertEquals(1, f.company.movementAllowance); assertTrue(f.data.armyMovements.isEmpty());
            f.edge("T001", "T002", KOMEConquestRouteEdge.OPEN); classifyOrdinary(f.unit);
            assertFalse(f.submit(e).accepted); assertTrue(f.data.armyMovements.isEmpty());
        }
    }
    @Test public void changedFingerprintAndMismatchedPreviewAreRejectedBeforeDebit() throws Exception {
        try (Fixture f = new Fixture(false, 1)) {
            Evaluation e = f.evaluate(); f.company.name = "Renamed";
            Submission result = f.submit(e); assertFalse(result.accepted); assertEquals("STALE_PREVIEW", result.denial.code);
            assertFalse(f.service.submit(f.data, f.player, "C1", "T003", e).accepted);
            assertFalse(f.service.submit(f.data, f.player, "C1", f.destination, "forged").accepted);
            assertEquals(1, f.company.movementAllowance); assertTrue(f.data.armyMovements.isEmpty());
        }
    }
    @Test public void serviceAndCommandSubmissionUseSameCanonicalDepartureAndPhysicalPendingState() throws Exception {
        try (Fixture direct = new Fixture(false, 2); Fixture legacy = new Fixture(false, 2)) {
            Submission result = direct.submit(direct.evaluate()); assertTrue(result.denial == null ? "" : result.denial.reason, result.accepted);
            Evaluation preview = legacy.evaluate();
            legacy.command.processCommand(legacy.player, new String[]{"movecompany", "C1", legacy.destination, preview.previewFingerprint});
            KOMEArmyMovementOrder a = direct.data.armyMovements.get(result.orderId);
            KOMEArmyMovementOrder b = legacy.data.armyMovements.values().iterator().next();
            assertEquals(a.routeTiles, b.routeTiles); assertEquals(a.status, b.status);
            assertEquals(a.currentTile, b.currentTile); assertEquals(a.completedSteps, b.completedSteps);
            assertEquals(a.units, b.units); assertEquals(a.companyId, b.companyId);
            assertEquals(a.owner, b.owner); assertEquals(a.nextRouteIndex, b.nextRouteIndex);
            assertEquals(0, direct.company.movementAllowance); assertEquals(0, legacy.company.movementAllowance);
            assertEquals(direct.unit.movementOrderId, legacy.unit.movementOrderId);
            assertEquals(direct.unit.movingEntityData, legacy.unit.movingEntityData);
            assertEquals(a.id, result.orderId); assertEquals(a.status, result.orderStatus);
            assertTrue(direct.data.movementHistory.containsKey(a.id));
            assertFalse(direct.submit(preview).accepted); assertEquals(1, direct.data.armyMovements.size());
        }
    }
    @Test public void zeroCreditTimedOverrideIsExplicitAndLegacyPreviewUnchanged() throws Exception {
        try (Fixture f = new Fixture(false, 2)) {
            f.company.movementAllowance = 0; f.data.movementSecondsPerTileOverride = 30;
            Evaluation e = f.evaluate(); assertTrue(e.allowed()); assertEquals(ScheduleMode.TIMED_OVERRIDE, e.scheduleMode);
            assertTrue(e.canStartNow); assertFalse(e.canArriveNow); assertPreviewParity(f, e);
        }
    }
    @Test public void missingArrivalTargetAndInvalidDestinationAreTypedDenials() throws Exception {
        try (Fixture f = new Fixture(false, 1)) {
            assertBlocked(f.service.evaluate(f.data, f.player, "C1", "bad"), "INVALID_DESTINATION");
            f.data.conquestTiles.get(f.destination).hasAnchor = false;
            assertBlocked(f.evaluate(), "ARRIVAL_UNAVAILABLE");
        }
    }
    @Test public void lostTemporaryControlDoesNotFallBackToOwnerControl() throws Exception {
        for (String authority : Arrays.asList(KOMEArmyCompany.AUTHORITY_ALLIANCE_DELEGATE,
                KOMEArmyCompany.AUTHORITY_STEWARDSHIP)) {
            try (Fixture f = new Fixture(false, 1)) {
                f.company.controllerAuthority = authority; f.company.temporaryController = new UUID(7, 7);
                assertBlocked(f.evaluate(), "UNAUTHORIZED");
                assertFalse(f.service.submitUnpreviewed(f.data, f.player, "C1", f.destination).accepted);
            }
        }
    }
    @Test public void existingHostileParticipantProjectsJoinWithoutConflictMutation() throws Exception {
        try (Fixture f = new Fixture(false, 1)) {
            relation(LOTRFactionRelations.Relation.ENEMY);
            f.data.conquestTiles.get(f.destination).setCurrentRulingFaction("mordor");
            KOMEConflictService conflicts = f.data.getConflictService();
            KOMEConflictRecord r = conflicts.start(f.destination, State.ORDINARY, ExpectedConflict.absent(),
                Collections.<GarrisonSeed>emptyList(), new Context(NOW, "test", "fixture")).record;
            r = conflicts.beginFactionParticipation(f.destination, ExpectedConflict.at(r.getConflictId(), r.getRevision()),
                "mordor", new Context(NOW, "test", "fixture")).record;
            Evaluation e = f.evaluate(); assertTrue(e.denial == null ? "" : e.denial.reason, e.allowed());
            assertEquals(Consequence.EXPECTED_JOIN_CONFLICT, e.consequence);
            assertEquals(r.getConflictId(), e.destinationConflictId); assertEquals(r.getRevision(), e.destinationConflictRevision);
            assertSame(r, conflicts.get(f.destination)); assertTrue(f.data.armyMovements.isEmpty());
        }
    }
    @Test public void absentServerActorIsTypedAndCannotExecute() {
        assertBlocked(KOMECompanyMovementService.INSTANCE.evaluate(null, "C1", "T002"), "INVALID_ACTOR");
        assertFalse(KOMECompanyMovementService.INSTANCE.submit(null, "C1", "T002", "token").accepted);
    }
    @Test public void unfinishedRetreatReservationIsTypedEvenBeforeMovingChecks() throws Exception {
        for (KOMEFormalRetreatBatch.Progress progress : Arrays.asList(KOMEFormalRetreatBatch.Progress.PREPARED,
                KOMEFormalRetreatBatch.Progress.RELEASED, KOMEFormalRetreatBatch.Progress.DEPARTED)) {
            try (Fixture f = new Fixture(false, 1)) {
                KOMEArmyMovementOrder holder = f.reserve(progress, progress);
                f.company.status = KOMEArmyCompany.MOVING; f.company.movementOrderId = holder.id;
                f.unit.movementOrderId = holder.id;
                NBTTagCompound before = holder.writeToNBT(); f.data.setDirty(false);
                Evaluation e = f.evaluate(); assertBlocked(e, "FORMAL_RETREAT_BLOCKED");
                assertTrue(e.denial.reason.contains("unfinished Formal Retreat")); assertNull(e.confirmation);
                Submission result = f.service.submitUnpreviewed(f.data, f.player, "C1", f.destination);
                assertFalse(result.accepted); assertEquals(e.denial.code, result.denial.code);
                assertEquals(before, holder.writeToNBT()); assertEquals(1, f.company.movementAllowance);
                assertFalse(f.data.isDirty());
            }
        }
    }
    @Test public void individuallyCompletedMemberRemainsReservedUntilWholeBatchFinalizes() throws Exception {
        try (Fixture f = new Fixture(false, 2)) {
            f.company.currentTile = f.unit.currentTile = "T002";
            Evaluation old = f.evaluate();
            KOMEArmyMovementOrder holder = f.reserve(KOMEFormalRetreatBatch.Progress.COMPLETE,
                KOMEFormalRetreatBatch.Progress.RELEASED);
            holder.currentTile = "T002"; holder.status = KOMEArmyMovementOrder.ARRIVED;
            assertFalse(f.company.isMoving()); assertBlocked(f.evaluate(), "FORMAL_RETREAT_BLOCKED");
            assertRejected(f, old, "FORMAL_RETREAT_BLOCKED");
            assertSame(holder.formalRetreatBatch, KOMEFormalRetreatAuthority.reservation(f.data, "C1", null));
        }
    }
    @Test public void completedReservationClearsOnlyAfterCanonicalBatchPhaseProgression() throws Exception {
        try (Fixture f = new Fixture(false, 2)) {
            f.company.currentTile = f.unit.currentTile = "T002";
            KOMEArmyMovementOrder holder = f.reserve(KOMEFormalRetreatBatch.Progress.COMPLETE,
                KOMEFormalRetreatBatch.Progress.COMPLETE);
            holder.currentTile = "T002"; holder.status = KOMEArmyMovementOrder.ARRIVED;
            assertBlocked(f.evaluate(), "FORMAL_RETREAT_BLOCKED");
            java.lang.reflect.Method advance = KOMEFormalRetreatBatch.class.getDeclaredMethod("advance", KOMEFormalRetreatBatch.Phase.class, boolean.class);
            advance.setAccessible(true);
            // Exercise the immutable model's legal progression; lifecycle/egress effects remain separately owned.
            for (KOMEFormalRetreatBatch.Phase phase : Arrays.asList(KOMEFormalRetreatBatch.Phase.STRATEGIC_COMPLETE,
                    KOMEFormalRetreatBatch.Phase.ABANDONMENT_RECONCILED, KOMEFormalRetreatBatch.Phase.EGRESS_HANDED_OFF,
                    KOMEFormalRetreatBatch.Phase.FINALIZED)) {
                holder.formalRetreatBatch = (KOMEFormalRetreatBatch) advance.invoke(holder.formalRetreatBatch, phase, false);
                if (!holder.formalRetreatBatch.finalized()) assertBlocked(f.evaluate(), "FORMAL_RETREAT_BLOCKED");
            }
            assertEquals("", KOMEFormalRetreatAuthority.ordinaryMovementBlockReason(f.data, "C1", null));
            Evaluation e = f.evaluate(); assertTrue(e.allowed()); assertTrue(f.submit(e).accepted);
        }
    }
    @Test public void latchedIncompleteV1QuarantineBlocksEvaluationWithoutFurtherMutation() throws Exception {
        try (Fixture f = new Fixture(false, 1)) {
            KOMEArmyMovementOrder order = new KOMEArmyMovementOrder(); order.id = "M1"; order.companyId = "C1";
            order.retreating = true; order.status = KOMEArmyMovementOrder.WAITING_NEXT_STEP;
            NBTTagCompound release = new NBTTagCompound(); release.setInteger("SchemaVersion", 1);
            release.setString("ConflictId", "CF1"); release.setLong("ConflictRevision", 1L);
            release.setString("OrderId", "M1"); release.setString("CompanyId", "C1");
            release.setString("Outcome", "FORMAL_RETREAT"); release.setLong("AppliedAtMillis", NOW);
            release.setString("Code", "CANCELED_BY_FORMAL_RETREAT"); release.setString("Reason", "incomplete legacy fixture");
            order.conflictRelease = KOMEConflictMovementHandoff.Receipt.read(release);
            f.data.armyMovements.put(order.id, order); KOMEFormalRetreatAuthority.quarantineIncompleteLegacy(f.data);
            assertTrue(KOMEFormalRetreatAuthority.isQuarantined(order)); NBTTagCompound before = order.writeToNBT();
            f.data.setDirty(false); Evaluation e = f.evaluate(); assertBlocked(e, "FORMAL_RETREAT_BLOCKED");
            assertTrue(e.denial.reason.contains("quarantined"));
            Submission result = f.service.submitUnpreviewed(f.data, f.player, "C1", f.destination);
            assertEquals("FORMAL_RETREAT_BLOCKED", result.denial.code); assertEquals(before, order.writeToNBT());
            assertFalse(f.data.isDirty()); assertEquals(1, f.company.movementAllowance);
        }
    }
    @Test public void createPreviewRejectsAppearingConflictButLegacyPacketTokenStillWorks() throws Exception {
        try (Fixture f = new Fixture(false, 1)) {
            f.hostileDestination(); Evaluation create = f.evaluate();
            assertEquals(Consequence.EXPECTED_CREATE_CONFLICT, create.consequence);
            f.hostileConflict(); Evaluation join = f.evaluate();
            assertEquals(Consequence.EXPECTED_JOIN_CONFLICT, join.consequence);
            assertEquals(create.previewFingerprint, join.previewFingerprint);
            assertNotEquals(create.confirmation.serviceFingerprint, join.confirmation.serviceFingerprint);
            assertRejected(f, create, "STALE_PREVIEW");
            f.command.processCommand(f.player, new String[]{"movecompany", "C1", f.destination, create.previewFingerprint});
            assertEquals(1, f.data.armyMovements.size()); // Existing GUI packet still dispatches this exact legacy command.
        }
    }
    @Test public void joinPreviewRejectsConflictEndingBeforeSubmission() throws Exception {
        try (Fixture f = new Fixture(false, 1)) {
            KOMEConflictRecord conflict = f.hostileConflict(); Evaluation join = f.evaluate();
            assertEquals(Consequence.EXPECTED_JOIN_CONFLICT, join.consequence);
            KOMEConflictService.EndResult end = f.data.getConflictService().endWithMovementHandoff(f.data, f.destination,
                ExpectedConflict.at(conflict.getConflictId(), conflict.getRevision()), new Context(NOW + 1, "test", "explicit fixture end"),
                KOMEConflictService.EndSource.ADMIN_FORCED);
            assertEquals(KOMEConflictContracts.Code.SUCCESS, end.conflictResult.code);
            Evaluation create = f.evaluate(); assertEquals(Consequence.EXPECTED_CREATE_CONFLICT, create.consequence);
            assertEquals(join.previewFingerprint, create.previewFingerprint); assertRejected(f, join, "STALE_PREVIEW");
        }
    }
    @Test public void activeConflictRevisionChangeInvalidatesServiceConfirmation() throws Exception {
        try (Fixture f = new Fixture(false, 1)) {
            KOMEConflictRecord conflict = f.hostileConflict(); Evaluation e = f.evaluate();
            KOMEConflictService.Result changed = f.data.getConflictService().registerPlayer(f.destination,
                ExpectedConflict.at(conflict.getConflictId(), conflict.getRevision()), new UUID(8, 8), "mordor",
                new Context(NOW + 1, "test", "new defender participant"));
            assertEquals(KOMEConflictContracts.Code.SUCCESS, changed.code);
            Evaluation current = f.evaluate(); assertEquals(e.consequence, current.consequence);
            assertNotEquals(e.destinationConflictRevision, current.destinationConflictRevision);
            assertEquals(e.previewFingerprint, current.previewFingerprint); assertRejected(f, e, "STALE_PREVIEW");
        }
    }
    @Test public void scheduleModeChangeInvalidatesOnlyTheServiceGuard() throws Exception {
        try (Fixture f = new Fixture(false, 2)) {
            Evaluation daily = f.evaluate(); f.data.movementSecondsPerTileOverride = 30;
            Evaluation timed = f.evaluate(); assertEquals(daily.previewFingerprint, timed.previewFingerprint);
            assertNotEquals(daily.confirmation.serviceFingerprint, timed.confirmation.serviceFingerprint);
            assertRejected(f, daily, "STALE_PREVIEW");
        }
    }
    @Test public void activePerTileAndTotalTimingOverridesAreGuarded() throws Exception {
        for (boolean total : Arrays.asList(false, true)) {
            try (Fixture f = new Fixture(false, 2)) {
                if (total) f.data.movementTotalSecondsOverride = 60; else f.data.movementSecondsPerTileOverride = 30;
                Evaluation e = f.evaluate();
                if (total) f.data.movementTotalSecondsOverride = 90; else f.data.movementSecondsPerTileOverride = 45;
                assertEquals(e.previewFingerprint, f.evaluate().previewFingerprint); assertRejected(f, e, "STALE_PREVIEW");
            }
        }
    }
    @Test public void unrelatedConflictAndObservationTimeDoNotInvalidateConfirmation() throws Exception {
        try (Fixture f = new Fixture(false, 1)) {
            Evaluation e = f.evaluate();
            f.data.getConflictService().start("T003", State.ORDINARY, ExpectedConflict.absent(),
                Collections.<GarrisonSeed>emptyList(), new Context(NOW + 1, "test", "unrelated conflict"));
            f.data.conquestTiles.get("T003").setAnchor(0, 99, 64, 99);
            Evaluation later = new KOMECompanyMovementService(f.command, () -> NOW + 1234).evaluate(f.data, f.player, "C1", f.destination);
            assertEquals(e.confirmation.serviceFingerprint, later.confirmation.serviceFingerprint);
            assertTrue(f.submit(e).accepted);
        }
    }
    @Test public void timingIsSampledAfterArrivalValidationCrossesDailyBoundary() throws Exception {
        try (Fixture f = new Fixture(false, 2)) {
            long boundary = KOMEMovementDayService.schedule().nextBoundary(KOMEMovementDayService.schedule()
                .latestBoundaryAtOrBefore(Instant.ofEpochMilli(NOW))).toEpochMilli();
            AtomicLong time = new AtomicLong(boundary - 1);
            f.world.arrivalInspection = () -> time.set(boundary + 1);
            Evaluation e = new KOMECompanyMovementService(f.command, time::get).evaluate(f.data, f.player, "C1", f.destination);
            assertTrue(e.allowed()); assertEquals(boundary + 1, e.evaluatedAtMillis);
            assertTrue(e.dailyTiming.nextBoundaryMillis > e.evaluatedAtMillis);
            assertEquals(KOMEMovementRoutePreview.timing(f.company, 2, boundary + 1).estimatedCompletionMillis,
                e.dailyTiming.estimatedCompletionMillis);
        }
    }
    @Test public void nonNullActorWithoutWorldReturnsTypedInvalidActor() throws Exception {
        try (Fixture f = new Fixture(false, 1)) {
            Evaluation old = f.evaluate(); f.player.worldObj = null;
            assertBlocked(f.service.evaluate(f.player, "C1", f.destination), "INVALID_ACTOR");
            assertEquals("INVALID_ACTOR", f.service.submit(f.player, "C1", f.destination, old.previewFingerprint).denial.code);
            assertEquals("INVALID_ACTOR", f.service.submitConfirmed(f.player, "C1", f.destination, old.confirmation).denial.code);
            assertBlocked(f.service.evaluate(f.data, f.player, "C1", f.destination), "INVALID_ACTOR");
            assertEquals("INVALID_ACTOR", f.service.submit(f.player, "C1", f.destination, "").denial.code);
            assertEquals("INVALID_ACTOR", f.service.submitConfirmed(f.player, "C1", f.destination, null).denial.code);
            assertTrue(f.data.armyMovements.isEmpty());
        }
    }
    @Test public void partiallySpentMountedCompanyCanStartButCannotFinishTwoTiles() throws Exception {
        try (Fixture f = new Fixture(true, 2)) {
            f.company.movementAllowance = 1; Evaluation e = f.evaluate();
            assertEquals(2, e.tilesPerDay); assertEquals(1, e.remainingAllowance);
            assertEquals(Status.CAN_START_BUT_CONTINUES_LATER, e.status);
            assertTrue(e.canStartNow); assertFalse(e.canArriveNow); assertPreviewParity(f, e);
        }
    }
    @Test public void alreadyAtDestinationIsTypedAndCannotSubmit() throws Exception {
        try (Fixture f = new Fixture(false, 1)) {
            assertBlocked(f.service.evaluate(f.data, f.player, "C1", "T001"), "INVALID_DESTINATION");
            assertEquals("INVALID_DESTINATION", f.service.submitUnpreviewed(f.data, f.player, "C1", "T001").denial.code);
        }
    }
    @Test public void movingStatusAndOutstandingMovementLinkBothBlockNewOrders() throws Exception {
        for (boolean link : Arrays.asList(false, true)) {
            try (Fixture f = new Fixture(false, 1)) {
                Evaluation old = f.evaluate();
                if (link) f.company.movementOrderId = "M9"; else f.company.status = KOMEArmyCompany.MOVING;
                assertBlocked(f.evaluate(), "ALREADY_MOVING"); assertRejected(f, old, "ALREADY_MOVING");
            }
        }
    }
    @Test public void validDelegatedControllerCanEvaluateAndSubmitThroughCanonicalAuthority() throws Exception {
        try (Fixture f = new Fixture(false, 1)) {
            f.company.owner = f.unit.owner = new UUID(9, 9);
            f.company.controllerAuthority = KOMEArmyCompany.AUTHORITY_ALLIANCE_DELEGATE;
            f.company.temporaryController = f.player.id;
            f.data.lastKnownPlayerFactions.put(f.company.owner, "gondor");
            f.data.lastKnownPlayerFactions.put(f.player.id, "gondor");
            assertTrue(KOMECompanyDiplomacyAuthorization.canContinueDelegation(f.data, "gondor", f.player.id).allowed);
            Evaluation e = f.evaluate(); assertTrue(e.denial == null ? "" : e.denial.reason, e.allowed());
            assertTrue(f.submit(e).accepted);
        }
    }
    @Test public void recordedStewardshipControllerStillUsesRetiredCanonicalAuthority() throws Exception {
        try (Fixture f = new Fixture(false, 1)) {
            f.company.controllerAuthority = KOMEArmyCompany.AUTHORITY_STEWARDSHIP; f.company.temporaryController = f.player.id;
            f.data.lastKnownPlayerFactions.put(f.player.id, "gondor");
            KOMEWarService.AuthorizationDecision authority = KOMEWartimeStewardshipService.controllerDecision(f.data, f.company, f.player.id);
            assertFalse(authority.allowed); assertTrue(authority.reason.contains("retired"));
            assertBlocked(f.evaluate(), "UNAUTHORIZED");
            assertEquals("UNAUTHORIZED", f.service.submitUnpreviewed(f.data, f.player, "C1", f.destination).denial.code);
        }
    }
    @Test public void coherentMembershipReplacementInvalidatesServiceGuardEvenWithSameLegacyToken() throws Exception {
        try (Fixture f = new Fixture(false, 1)) {
            Evaluation e = f.evaluate(); UUID replacement = new UUID(2, 2);
            f.company.units.clear(); f.company.units.add(replacement);
            f.data.hiredUnits.remove(f.unit.entity); f.unit.entity = replacement; f.data.hiredUnits.put(replacement, f.unit);
            Evaluation current = f.evaluate(); assertTrue(current.allowed());
            assertEquals(e.previewFingerprint, current.previewFingerprint); assertRejected(f, e, "STALE_PREVIEW");
        }
    }
    @Test public void postPreviewBoundaryAndAllowanceChangesRejectWithoutDebit() throws Exception {
        for (boolean boundaryOnly : Arrays.asList(false, true)) {
            try (Fixture f = new Fixture(true, 2)) {
                if (boundaryOnly) f.company.movementAllowance = 1;
                Evaluation e = f.evaluate();
                if (boundaryOnly) f.data.movementBoundaryMillis++;
                else f.company.movementAllowance = 1;
                assertRejected(f, e, "STALE_PREVIEW");
            }
        }
    }
    @Test public void postPreviewArrivalCoordinatesChangeRejectsButMissingArrivalHasSpecificDenial() throws Exception {
        try (Fixture f = new Fixture(false, 1)) {
            Evaluation e = f.evaluate(); f.data.conquestTiles.get(f.destination).setAnchor(0, 15, 64, 25);
            assertRejected(f, e, "STALE_PREVIEW");
            f.data.conquestTiles.get(f.destination).hasAnchor = false;
            assertRejected(f, e, "ARRIVAL_UNAVAILABLE");
        }
    }
    @Test public void liveRouteRejectionRetainsStructuredEdgeCodeAndExactCommandText() throws Exception {
        try (Fixture f = new Fixture(false, 1)) {
            Evaluation old = f.evaluate(); f.edge("T001", "T002", KOMEConquestRouteEdge.MOUNTAIN);
            Evaluation blocked = f.evaluate(); Submission result = assertRejected(f, old, "MOUNTAIN_EDGE_BLOCKED");
            assertEquals(blocked.denial.reason, result.denial.reason);
            try { f.command.processCommand(f.player, new String[]{"movecompany", "C1", f.destination, old.previewFingerprint}); fail(); }
            catch (net.minecraft.command.WrongUsageException denied) { assertEquals(result.denial.reason, denied.getMessage()); }
        }
    }
    private static Submission assertRejected(Fixture f, Evaluation preview, String code) {
        NBTTagCompound company = f.company.writeToNBT(), unit = f.unit.writeToNBT(); int orders = f.data.armyMovements.size();
        Submission result = f.submit(preview); assertFalse(result.accepted); assertEquals(result.denial.reason, code, result.denial.code);
        assertEquals(company, f.company.writeToNBT()); assertEquals(unit, f.unit.writeToNBT()); assertEquals(orders, f.data.armyMovements.size());
        return result;
    }
    private static void classifyOrdinary(KOMEHiredUnitRecord unit) {
        NBTTagCompound nbt = unit.writeToNBT(); nbt.setString("UnitClass", "ORDINARY"); unit.readFromNBT(nbt);
    }
    private static void assertBlocked(Evaluation e, String code) {
        assertEquals(Status.BLOCKED, e.status); assertNotNull(e.denial); assertEquals(e.denial.reason, code, e.denial.code);
    }
    private static void assertPreviewParity(Fixture f, Evaluation e) {
        f.command.processCommand(f.player, new String[]{"previewmove", "C1", f.destination});
        KOMEPacketCompanyMoveConfirmGui packet = f.command.preview; assertNotNull(packet);
        assertEquals(e.routeTiles, packet.routeTiles); assertEquals(e.remainingAllowance, packet.remainingAllowance);
        assertEquals(e.tilesPerDay, packet.tilesPerDay); assertEquals(e.distanceTiles, packet.distanceTiles);
        assertEquals(e.previewFingerprint, packet.previewToken);
        assertEquals(e.dailyTiming.immediateMovementPossible, packet.immediateMovementPossible);
        assertEquals(e.dailyTiming.requiredBoundaries, packet.requiredMovementBoundaries);
    }
    private static final class RecordingCommand extends KOMECommandTroops {
        KOMEPacketCompanyMoveConfirmGui preview; KOMEPacketCompanyMovePreviewResult failure;
        @Override void publishMovementPreview(KOMEPacketCompanyMoveConfirmGui value, EntityPlayerMP actor) { preview = value; }
        @Override void publishMovementFailure(KOMEPacketCompanyMovePreviewResult value, EntityPlayerMP actor) { failure = value; }
    }
    private static final class Fixture implements AutoCloseable {
        final KOMEWorldData data = new KOMEWorldData("movement-service");
        final KOMEArmyCompany company = new KOMEArmyCompany();
        final KOMEHiredUnitRecord unit = new KOMEHiredUnitRecord();
        final RecordingCommand command = new RecordingCommand();
        final KOMECompanyMovementService service = new KOMECompanyMovementService(command, () -> NOW);
        final FixtureWorld world; final KOMEAccessFixture.Player player; final String destination;
        final Map<UUID, LOTRPlayerData> nativePlayers; final LOTRPlayerData previous;
        Fixture(boolean mounted, int distance) throws Exception {
            world = KOMEAccessFixture.allocate(FixtureWorld.class);
            Field provider = World.class.getDeclaredField("provider"); provider.setAccessible(true); provider.set(world, new WorldProviderSurface());
            world.loadedEntityList = new ArrayList<Entity>(); world.playerEntities = new ArrayList();
            world.mapStorage = new MapStorage(null); world.mapStorage.setData("KOME_ServerRules", data);
            player = KOMEAccessFixture.allocate(KOMEAccessFixture.Player.class); player.id = new UUID(1, 1);
            player.messages = new ArrayList<String>(); player.worldObj = world;
            Field uuid = Entity.class.getDeclaredField("entityUniqueID"); uuid.setAccessible(true); uuid.set(player, player.id);
            Field map = LOTRLevelData.class.getDeclaredField("playerDataMap"); map.setAccessible(true);
            nativePlayers = (Map<UUID, LOTRPlayerData>) map.get(null);
            LOTRPlayerData nativeData = new LOTRPlayerData(player.id);
            Field pledge = LOTRPlayerData.class.getDeclaredField("pledgeFaction"); pledge.setAccessible(true); pledge.set(nativeData, LOTRFaction.GONDOR);
            previous = nativePlayers.put(player.id, nativeData);
            destination = distance == 1 ? "T002" : "T003";
            for (String id : Arrays.asList("T001", "T002", "T003")) {
                KOMEConquestTile tile = new KOMEConquestTile(id); tile.claim("gondor", NOW);
                tile.setAnchor(0, 10, 64, 20); data.conquestTiles.put(id, tile);
            }
            edge("T001", "T002", KOMEConquestRouteEdge.OPEN); edge("T002", "T003", KOMEConquestRouteEdge.OPEN);
            company.id = "C1"; company.name = "Company"; company.owner = player.id;
            company.faction = company.nativeFaction = "gondor"; company.currentTile = "T001";
            company.status = KOMEArmyCompany.STATIONED;
            unit.entity = new UUID(2, 1); unit.owner = player.id; unit.companyId = "C1";
            unit.type = KOMEPopulationType.OFFENSIVE;
            unit.currentTile = unit.sourceTileId = "T001"; unit.unitFaction = unit.sourceFaction = unit.populationOwningFaction = "gondor";
            unit.cost = unit.baseCost = unit.populationSpent = 10; unit.mounted = mounted;
            unit.stationedEntityData = new NBTTagCompound(); unit.stationedEntityData.setFloat("HealF", 5.375F);
            company.units.add(unit.entity); company.totalPopulation = 10;
            company.mountedPopulation = mounted ? 10 : 0; company.groundPopulation = mounted ? 0 : 10;
            KOMEMovementDayService.initializeNewCompany(company, NOW);
            NBTTagCompound persisted = unit.writeToNBT(); persisted.setString("UnitClass", "CAMPAIGN"); unit.readFromNBT(persisted);
            data.armyCompanies.put(company.id, company); data.hiredUnits.put(unit.entity, unit);
        }
        void hostileDestination() {
            relation(LOTRFactionRelations.Relation.ENEMY);
            data.conquestTiles.get(destination).setCurrentRulingFaction("mordor");
        }
        KOMEConflictRecord hostileConflict() {
            hostileDestination(); KOMEConflictService conflicts = data.getConflictService();
            KOMEConflictRecord r = conflicts.start(destination, State.ORDINARY, ExpectedConflict.absent(),
                Collections.<GarrisonSeed>emptyList(), new Context(NOW, "test", "fixture conflict")).record;
            return conflicts.beginFactionParticipation(destination, ExpectedConflict.at(r.getConflictId(), r.getRevision()),
                "mordor", new Context(NOW, "test", "fixture defender")).record;
        }
        KOMEArmyMovementOrder reserve(KOMEFormalRetreatBatch.Progress first, KOMEFormalRetreatBatch.Progress second) {
            KOMEArmyMovementOrder holder = new KOMEArmyMovementOrder(); holder.id = "M1"; holder.companyId = "C1";
            holder.owner = player.id; holder.status = KOMEArmyMovementOrder.WAITING_NEXT_STEP;
            holder.currentTile = "T001"; holder.routeTiles.addAll(Arrays.asList("T001", "T002"));
            holder.formalRetreatBatch = new KOMEFormalRetreatBatch(new UUID(6, 1), player.id, "CF1", "T001", "", NOW,
                Arrays.asList(new KOMEFormalRetreatBatch.Member("C1", "M1", player.id, Arrays.asList("T001", "T002"), 1, 0, first),
                    new KOMEFormalRetreatBatch.Member("C2", "M2", player.id, Arrays.asList("T001", "T002"), 1, 0, second)),
                KOMEFormalRetreatBatch.Phase.STRATEGIC_PENDING, false);
            data.armyMovements.put(holder.id, holder); return holder;
        }
        void edge(String a, String b, String type) { data.setRouteEdge(a, b, type, "fixture", 0, 10, 64, 20, "test"); }
        Evaluation evaluate() { return service.evaluate(data, player, "C1", destination); }
        Submission submit(Evaluation e) { return service.submit(data, player, "C1", destination, e); }
        @Override public void close() { if (previous == null) nativePlayers.remove(player.id); else nativePlayers.put(player.id, previous); }
    }
    private static final class FixtureWorld extends World {
        Runnable arrivalInspection;
        private FixtureWorld() { super((ISaveHandler)null, "test", (WorldProvider)null, (WorldSettings)null, (Profiler)null); }
        @Override protected IChunkProvider createChunkProvider() { return null; }
        @Override protected int func_152379_p() { return 0; }
        @Override public IChunkProvider getChunkProvider() { return null; }
        @Override public Chunk getChunkFromChunkCoords(int x, int z) { if (arrivalInspection != null) arrivalInspection.run(); return null; }
        @Override public boolean blockExists(int x, int y, int z) { return true; }
        @Override public Entity getEntityByID(int id) { return null; }
    }
}
