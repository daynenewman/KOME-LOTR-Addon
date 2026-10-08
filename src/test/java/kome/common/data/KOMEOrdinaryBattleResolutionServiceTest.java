package kome.common.data;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import lotr.common.fac.LOTRFactionRelations;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static kome.common.data.KOMECombatStateEvaluator.*;
import static kome.common.data.KOMEConflictContracts.*;
import static kome.common.data.KOMEConflictRecord.*;
import static kome.common.data.KOMEConflictServiceTest.ok;
import static org.junit.Assert.*;

public class KOMEOrdinaryBattleResolutionServiceTest {
    @Rule public final KOMETileTestResources geometry = new KOMETileTestResources();
    private KOMEPopulationTestConfig config;

    @Before public void setUp() throws Exception {
        config = new KOMEPopulationTestConfig();
    }

    @After public void tearDown() throws Exception {
        config.close();
        LOTRFactionRelations.overrideRelations(KOMEAlliance.findLotrFaction("gondor"),
            KOMEAlliance.findLotrFaction("mordor"),
            LOTRFactionRelations.Relation.NEUTRAL);
    }

    @Test public void completeResponseAndNoMilitaryPairPermitActiveOrStoppedEpisodeResolution() {
        Fixture complete = fixture("gondor", "gondor").campaign("C1", "gondor", 10L)
            .episode(TimerStatus.COMPLETE, false);
        assertEquals(KOMEOrdinaryBattleResolutionService.Code.READY,
            complete.service().preflight(complete.data, TILE).code);

        Fixture paused = fixture("gondor", "gondor").campaign("C1", "gondor", 10L)
            .episode(TimerStatus.PAUSED, false);
        assertEquals(KOMEOrdinaryBattleResolutionService.Code.WAITING_RESPONSE_WINDOW,
            paused.service().preflight(paused.data, TILE).code);

        Fixture running = fixture("gondor", "gondor").campaign("C1", "gondor", 10L)
            .episode(TimerStatus.RUNNING, false);
        assertEquals(KOMEOrdinaryBattleResolutionService.Code.WAITING_RESPONSE_WINDOW,
            running.service().preflight(running.data, TILE).code);

        Fixture hostile = fixture("gondor", "gondor")
            .campaign("C1", "gondor", 10L).campaign("C2", "mordor", 20L)
            .relation("gondor", "mordor", Relation.HOSTILE)
            .episode(TimerStatus.COMPLETE, false);
        assertEquals(KOMEOrdinaryBattleResolutionService.Code.HOSTILITY_ACTIVE,
            hostile.service().preflight(hostile.data, TILE).code);

        Fixture active = fixture("gondor", "gondor")
            .campaign("C1", "gondor", 10L).episode(TimerStatus.COMPLETE, true);
        assertEquals(KOMEOrdinaryBattleResolutionService.Code.READY,
            active.service().preflight(active.data, TILE).code);
        Fixture activeRunning = fixture("gondor", "gondor")
            .campaign("C1", "mordor", 10L).episode(TimerStatus.RUNNING, true);
        assertEquals(KOMEOrdinaryBattleResolutionService.Code.WAITING_RESPONSE_WINDOW,
            activeRunning.service().preflight(activeRunning.data, TILE).code);
    }

    @Test public void processAllResolvesCompleteActiveLoneAttackerAndNormalizesClosure() {
        Fixture f = fixture("gondor", "gondor").campaign("C1", "mordor", 10L)
            .episode(TimerStatus.COMPLETE, true);
        java.util.List<KOMEOrdinaryBattleResolutionService.Result> results =
            f.service().processAll(f.data, 500L, 200L);
        assertEquals(1, results.size());
        assertTrue(results.get(0).reason, results.get(0).resolved());
        assertEquals("mordor", f.tile().projectRulingFaction());
        assertEquals(EpisodeState.INACTIVE, f.conflict().getCombatEpisode().state);
        assertEquals("CF1:E1", f.conflict().getCombatEpisode().episodeId);
        assertEquals(Long.valueOf(200L), f.conflict().getCombatEpisode().endedAtMillis);
        assertEquals(TimerStatus.COMPLETE, f.conflict().getResponseTimer().status);
    }

    @Test public void ownershipPlanUsesDefaultClaimContinuityTieAndCoalitionRules() {
        Fixture retained = fixture("gondor", "gondor")
            .campaign("C1", "gondor", 10L).episode(TimerStatus.COMPLETE, false);
        assertPlan(retained, "gondor", false);

        Fixture regained = fixture("mordor", "gondor")
            .campaign("C1", "gondor", 10L).episode(TimerStatus.COMPLETE, false);
        assertPlan(regained, "gondor", true);

        Fixture lone = fixture("gondor", "gondor")
            .campaign("C1", "mordor", 10L).episode(TimerStatus.COMPLETE, false);
        assertPlan(lone, "mordor", true);

        Fixture allied = fixture("gondor", "gondor")
            .campaign("C1", "mordor", 10L).campaign("C2", "rohan", 20L)
            .relation("mordor", "rohan", Relation.ALLIED)
            .episode(TimerStatus.COMPLETE, false);
        assertPlan(allied, "mordor", true);

        Fixture tie = fixture("gondor", "gondor")
            .campaign("C1", "rohan", 10L).campaign("C2", "mordor", 10L)
            .relation("mordor", "rohan", Relation.ALLIED)
            .episode(TimerStatus.COMPLETE, false);
        assertPlan(tie, "mordor", true);

        Fixture neutral = fixture("gondor", "gondor")
            .campaign("C1", "mordor", 10L).campaign("C2", "rohan", 20L)
            .relation("mordor", "rohan", Relation.NON_ALLIED)
            .episode(TimerStatus.COMPLETE, false);
        assertEquals(KOMEOrdinaryBattleResolutionService.Code.DEFERRED_NO_WINNER,
            neutral.service().preflight(neutral.data, TILE).code);

        Fixture unknown = fixture("gondor", "gondor")
            .campaign("C1", "mordor", 10L).campaign("C2", "rohan", 20L)
            .relation("mordor", "rohan", Relation.UNKNOWN)
            .episode(TimerStatus.COMPLETE, false);
        assertEquals(KOMEOrdinaryBattleResolutionService.Code.DEFERRED_UNKNOWN_AUTHORITY,
            unknown.service().preflight(unknown.data, TILE).code);
    }

    @Test public void emergencyDefenseCanPreserveOnlyTheDefaultOwner() {
        Fixture owner = fixture("mordor", "gondor").participation("gondor", 10L)
            .defense("gondor").episode(TimerStatus.COMPLETE, false);
        assertPlan(owner, "gondor", true);

        Fixture nonOwner = fixture("gondor", "gondor").participation("rohan", 10L)
            .defense("rohan").episode(TimerStatus.COMPLETE, false);
        assertEquals(KOMEOrdinaryBattleResolutionService.Code.DEFERRED_UNKNOWN_AUTHORITY,
            nonOwner.service().preflight(nonOwner.data, TILE).code);
    }

    @Test public void fortifiedEncircledAndAmbiguousContextsDefer() {
        for (KOMEFortificationContextResolver.Decision decision : new KOMEFortificationContextResolver.Decision[] {
                KOMEFortificationContextResolver.Decision.fortified("Defensive context is active."),
                KOMEFortificationContextResolver.Decision.ambiguous("Defensive authority is ambiguous.") }) {
            Fixture f = fixture("gondor", "gondor").campaign("C1", "gondor", 10L)
                .episode(TimerStatus.COMPLETE, false);
            f.fortification = decision;
            assertEquals(KOMEOrdinaryBattleResolutionService.Code.DEFERRED_FORTIFIED,
                f.service().preflight(f.data, TILE).code);
        }

        Fixture encircled = fixture("gondor", "gondor", State.ENCIRCLEMENT);
        assertEquals(KOMEOrdinaryBattleResolutionService.Code.DEFERRED_FORTIFIED,
            encircled.service().preflight(encircled.data, TILE).code);
    }

    @Test public void unchangedOwnershipEndsConflictWithoutFalseCaptureHistory() {
        Fixture f = fixture("gondor", "gondor").campaign("C1", "gondor", 10L)
            .episode(TimerStatus.COMPLETE, false);
        KOMEOrdinaryBattleResolutionService.Result result = f.service().resolve(
            f.data, TILE, 500L, 200L);
        assertEquals(KOMEOrdinaryBattleResolutionService.Code.RESOLVED_NO_TRANSFER,
            result.code);
        assertEquals(State.ENDED, f.conflict().getState());
        assertEquals("gondor", f.tile().projectRulingFaction());
        assertEquals(0, captureCount(f.data));
        assertEquals(1, f.notifications());
        assertEquals(TimerStatus.COMPLETE,
            result.endedConflict.getResponseTimer().status);
        assertEquals(EpisodeState.INACTIVE,
            result.endedConflict.getCombatEpisode().state);
    }

    @Test public void hostileCaptureUsesCanonicalOwnershipHistoryAndEndsConflictOnce() {
        Fixture f = fixture("gondor", "gondor").campaign("C1", "mordor", 10L)
            .episode(TimerStatus.COMPLETE, false);
        KOMEOrdinaryBattleResolutionService service = f.service();
        KOMEOrdinaryBattleResolutionService.Result result = service.resolve(
            f.data, TILE, 500L, 200L);
        assertEquals(KOMEOrdinaryBattleResolutionService.Code.RESOLVED_OWNERSHIP_CHANGED,
            result.code);
        assertEquals("mordor", f.tile().projectRulingFaction());
        assertEquals(State.ENDED, f.conflict().getState());
        assertEquals(1, captureCount(f.data));
        KOMEWar.TileCaptureEvent event = capture(f.data);
        assertEquals("gondor", event.formerOwner);
        assertEquals("mordor", event.newOwner);
        assertEquals(KOMEOrdinaryBattleResolutionService.CAPTURE_METHOD_PREFIX
            + result.endedConflict.getConflictId(), event.claimMethod);
        assertEquals(1, f.notifications());

        KOMEOrdinaryBattleResolutionService.Result replay = service.resolve(
            f.data, TILE, 501L, 201L);
        assertEquals(KOMEOrdinaryBattleResolutionService.Code.ALREADY_RESOLVED,
            replay.code);
        assertEquals(1, captureCount(f.data));
        assertEquals(1, f.notifications());
    }

    @Test public void alliedCoalitionSelectionChangesOwnerWithoutFalseHostileCapture() {
        Fixture f = fixture("rohan", "gondor")
            .campaign("C1", "mordor", 10L).campaign("C2", "rohan", 20L)
            .relation("mordor", "rohan", Relation.ALLIED)
            .episode(TimerStatus.COMPLETE, false);
        KOMEOrdinaryBattleResolutionService.Plan plan = f.service().preflight(f.data, TILE);
        assertEquals(KOMEOrdinaryBattleResolutionService.Code.READY, plan.code);
        assertEquals("mordor", plan.winnerFactionId);
        assertTrue(plan.ownershipChanges);
        assertFalse(plan.captureHistoryRequired);
        KOMEOrdinaryBattleResolutionService.Result result = f.service().publish(
            f.data, plan, 500L, 200L);
        assertEquals(KOMEOrdinaryBattleResolutionService.Code.RESOLVED_OWNERSHIP_CHANGED,
            result.code);
        assertEquals("mordor", f.tile().projectRulingFaction());
        assertEquals(0, captureCount(f.data));
        assertEquals(State.ENDED, f.conflict().getState());
    }

    @Test public void stalePlanPublishesNothing() {
        Fixture f = fixture("gondor", "gondor").campaign("C1", "mordor", 10L)
            .episode(TimerStatus.COMPLETE, false);
        KOMEOrdinaryBattleResolutionService service = f.service();
        KOMEOrdinaryBattleResolutionService.Plan plan = service.preflight(f.data, TILE);
        KOMEConflictRecord current = f.conflict();
        ok(f.data.getConflictService().beginFactionParticipation(TILE,
            ExpectedConflict.at(current.getConflictId(), current.getRevision()),
            "harad", context(101L)));
        KOMEOrdinaryBattleResolutionService.Result result = service.publish(
            f.data, plan, 500L, 200L);
        assertEquals(KOMEOrdinaryBattleResolutionService.Code.STALE_RETRY, result.code);
        assertEquals("gondor", f.tile().projectRulingFaction());
        assertTrue(f.conflict().isActive());
        assertEquals(0, captureCount(f.data));
        assertEquals(0, f.notifications());
    }

    @Test public void contradictoryCaptureReceiptFailsClosed() {
        Fixture f = fixture("gondor", "gondor").campaign("C1", "mordor", 10L)
            .episode(TimerStatus.COMPLETE, false);
        KOMEWar war = new KOMEWar();
        war.id = "W-test";
        KOMEWar.TileCaptureEvent receipt = new KOMEWar.TileCaptureEvent();
        receipt.tileId = TILE;
        receipt.formerOwner = "gondor";
        receipt.newOwner = "rohan";
        receipt.claimMethod = KOMEOrdinaryBattleResolutionService.CAPTURE_METHOD_PREFIX
            + f.conflict().getConflictId();
        war.tileCaptureHistory.add(receipt);
        f.data.wars.put(war.id, war);
        assertEquals(KOMEOrdinaryBattleResolutionService.Code.CAPTURE_HISTORY_CONTRADICTION,
            f.service().preflight(f.data, TILE).code);
        assertEquals("gondor", f.tile().projectRulingFaction());
        assertTrue(f.conflict().isActive());
    }

    @Test public void captureAndOwnershipCheckpointsReconcileWithoutDuplicateHistory() {
        Fixture history = fixture("gondor", "gondor").campaign("C1", "mordor", 10L)
            .episode(TimerStatus.COMPLETE, false);
        history.observer = new OneShotObserver(true, false);
        assertEquals(KOMEOrdinaryBattleResolutionService.Code.RECONCILIATION_PENDING,
            history.service().resolve(history.data, TILE, 500L, 200L).code);
        assertEquals("gondor", history.tile().projectRulingFaction());
        assertTrue(history.conflict().isActive());
        assertEquals(1, captureCount(history.data));
        assertCaptureReservedAgainstAbandonment(history, 200L);
        history.observer = KOMEOrdinaryBattleResolutionService.PublicationObserver.NONE;
        assertEquals(KOMEOrdinaryBattleResolutionService.Code.RESOLVED_OWNERSHIP_CHANGED,
            history.service().resolve(history.data, TILE, 500L, 201L).code);
        assertEquals("mordor", history.tile().projectRulingFaction());
        assertEquals(1, captureCount(history.data));

        Fixture ownership = fixture("gondor", "gondor").campaign("C1", "mordor", 10L)
            .episode(TimerStatus.COMPLETE, true);
        ownership.observer = new OneShotObserver(false, true);
        assertEquals(KOMEOrdinaryBattleResolutionService.Code.RECONCILIATION_PENDING,
            ownership.service().resolve(ownership.data, TILE, 500L, 200L).code);
        assertEquals("mordor", ownership.tile().projectRulingFaction());
        assertTrue(ownership.conflict().isActive());
        assertEquals(1, captureCount(ownership.data));
        assertCaptureReservedAgainstAbandonment(ownership, 200L);
        ownership.observer = KOMEOrdinaryBattleResolutionService.PublicationObserver.NONE;
        assertEquals(KOMEOrdinaryBattleResolutionService.Code.RESOLVED_OWNERSHIP_CHANGED,
            ownership.service().resolve(ownership.data, TILE, 500L, 201L).code);
        assertEquals(State.ENDED, ownership.conflict().getState());
        assertEquals(1, captureCount(ownership.data));
    }

    private static void assertCaptureReservedAgainstAbandonment(Fixture f, long now) {
        KOMEConflictRecord before = f.conflict();
        KOMEConflictLifecycleService.OrdinaryAbandonmentResult result =
            KOMEConflictLifecycleService.INSTANCE.reconcileOrdinaryConflictAfterCommitmentChange(
                f.data, TILE, ExpectedConflict.at(before.getConflictId(), before.getRevision()), context(now));
        assertEquals(KOMEConflictLifecycleService.OrdinaryAbandonmentCode.RESOLUTION_PENDING, result.code);
        assertSame(before, f.conflict());
        assertTrue(f.conflict().isActive());
        KOMEOrdinaryBattleLifecycleService lifecycle = new KOMEOrdinaryBattleLifecycleService(
            (data, conflict) -> f.evaluation.evaluate(data, conflict),
            (data, tile) -> KOMEFortificationContextResolver.Decision.clear(),
            (data, conflict) -> KOMEOrdinaryBattlePlanner.responseDuration(1, 1, 2, 3),
            (conflict, duration) -> fail("Pending capture must not start another response episode"),
            KOMEOrdinaryBattleLifecycleService.BeforePublication.NONE);
        assertEquals(KOMEOrdinaryBattleLifecycleService.Code.NO_CHANGE,
            lifecycle.process(f.data, TILE, now).code);
        assertSame(before, f.conflict());
    }

    @Test public void conflictRevisionChangedAfterOwnershipReconcilesOnNextTick() {
        Fixture f = fixture("gondor", "gondor").campaign("C1", "mordor", 10L)
            .episode(TimerStatus.COMPLETE, false);
        f.observer = new KOMEOrdinaryBattleResolutionService.PublicationObserver() {
            boolean changed;
            public void beforeFinalValidation(KOMEWorldData data,
                    KOMEOrdinaryBattleResolutionService.Plan plan) { }
            public void afterCaptureHistory(KOMEWorldData data,
                    KOMEOrdinaryBattleResolutionService.Plan plan) { }
            public void afterOwnership(KOMEWorldData data,
                    KOMEOrdinaryBattleResolutionService.Plan plan) {
                if (changed) return;
                changed = true;
                KOMEConflictRecord live = data.getConflictService().get(plan.tileId);
                ok(data.getConflictService().beginFactionParticipation(plan.tileId,
                    ExpectedConflict.at(live.getConflictId(), live.getRevision()),
                    "harad", context(150L)));
            }
        };
        assertEquals(KOMEOrdinaryBattleResolutionService.Code.RECONCILIATION_PENDING,
            f.service().resolve(f.data, TILE, 500L, 200L).code);
        assertEquals("mordor", f.tile().projectRulingFaction());
        assertTrue(f.conflict().isActive());
        f.observer = KOMEOrdinaryBattleResolutionService.PublicationObserver.NONE;
        assertEquals(KOMEOrdinaryBattleResolutionService.Code.RESOLVED_OWNERSHIP_CHANGED,
            f.service().resolve(f.data, TILE, 500L, 201L).code);
        assertEquals(State.ENDED, f.conflict().getState());
        assertEquals(1, captureCount(f.data));
    }

    @Test public void movementHoldsUseCanonicalReleasedPauseWithoutGuessingOutcome() {
        KOMEConflictMovementServiceTest.Fixture movement =
            KOMEConflictMovementServiceTest.hostileFixture(false, false);
        KOMEConflictMovementService.ArrivalPreparation arrival = movement.prepare("M1", 10L);
        movement.publish("M1", "T101");
        KOMEConflictRecord active = movement.commit(arrival);
        KOMEArmyMovementOrder order = movement.data.armyMovements.get("M1");
        KOMEConflictMovementService.applyConflictHold(movement.data, order, active, 10L);
        KOMEArmyCompany company = movement.data.armyCompanies.get("C1");
        KOMEMovementDayService.initializeNewCompany(company, 20L);
        company.movementAllowance = 1;
        complete(movement.data, "T101");

        Evaluation evaluation = new Evaluation();
        evaluation.factions.put("C1", "gondor");
        KOMEOrdinaryBattleResolutionService service = service(evaluation,
            KOMEFortificationContextResolver.Decision.clear(),
            new int[1], KOMEOrdinaryBattleResolutionService.PublicationObserver.NONE);
        KOMEOrdinaryBattleResolutionService.Result result = service.resolve(
            movement.data, "T101", 500L, 200L);
        assertTrue(result.reason, result.resolved());
        assertEquals(1, result.movementHoldsReleased);
        assertEquals(KOMEArmyMovementOrder.CONFLICT_RELEASED_PAUSED, order.status);
        assertNull(order.conflictRelease);
        assertEquals(1, company.movementAllowance);
        assertEquals(1, order.completedSteps);
        assertEquals("T101", company.currentTile);
    }

    @Test public void contradictoryMovementHoldBlocksOwnershipAndConflictClosure() {
        KOMEConflictMovementServiceTest.Fixture movement =
            KOMEConflictMovementServiceTest.hostileFixture(false, false);
        KOMEConflictMovementService.ArrivalPreparation arrival = movement.prepare("M1", 10L);
        movement.publish("M1", "T101");
        KOMEConflictRecord active = movement.commit(arrival);
        KOMEArmyMovementOrder order = movement.data.armyMovements.get("M1");
        KOMEConflictMovementService.applyConflictHold(movement.data, order, active, 10L);
        order.conflictHoldId = "CF999";
        complete(movement.data, "T101");
        Evaluation evaluation = new Evaluation();
        evaluation.factions.put("C1", "gondor");
        KOMEOrdinaryBattleResolutionService service = service(evaluation,
            KOMEFortificationContextResolver.Decision.clear(), new int[1],
            KOMEOrdinaryBattleResolutionService.PublicationObserver.NONE);
        assertEquals(KOMEOrdinaryBattleResolutionService.Code.MOVEMENT_HANDOFF_CONTRADICTION,
            service.preflight(movement.data, "T101").code);
        assertEquals("mordor", movement.data.getConquestTileIfPresent("T101")
            .projectRulingFaction());
        assertTrue(movement.data.getConflictService().get("T101").isActive());
        assertEquals(KOMEArmyMovementOrder.CONFLICT_HELD, order.status);
    }

    private static void assertPlan(Fixture f, String winner, boolean changes) {
        KOMEOrdinaryBattleResolutionService.Plan plan = f.service().preflight(f.data, TILE);
        assertEquals(plan.reason, KOMEOrdinaryBattleResolutionService.Code.READY, plan.code);
        assertEquals(winner, plan.winnerFactionId);
        assertEquals(changes, plan.ownershipChanges);
        assertTrue(plan.responseTimerComplete);
    }

    private static Fixture fixture(String current, String nativeOwner) {
        return fixture(current, nativeOwner, State.ORDINARY);
    }

    private static Fixture fixture(String current, String nativeOwner, State state) {
        KOMEWorldData data = new KOMEWorldData("ordinary-resolution-test");
        KOMEConquestTile tile = new KOMEConquestTile(TILE);
        tile.setWaypointDefaults(1, nativeOwner, "test");
        tile.setCurrentRulingFaction(current);
        data.conquestTiles.put(TILE, tile);
        KOMEConflictRecord conflict = ok(data.getConflictService().start(TILE, state,
            ExpectedConflict.absent(), Collections.<GarrisonSeed>emptyList(), context(1L)));
        return new Fixture(data, conflict);
    }

    private static final String TILE = "T100";

    private static final class Fixture {
        final KOMEWorldData data;
        final Evaluation evaluation = new Evaluation();
        final int[] notifications = new int[1];
        KOMEFortificationContextResolver.Decision fortification =
            KOMEFortificationContextResolver.Decision.clear();
        KOMEOrdinaryBattleResolutionService.PublicationObserver observer =
            KOMEOrdinaryBattleResolutionService.PublicationObserver.NONE;
        Fixture(KOMEWorldData data, KOMEConflictRecord ignored) { this.data = data; }

        Fixture participation(String faction, long at) {
            KOMEConflictRecord current = conflict();
            ok(data.getConflictService().beginFactionParticipation(TILE,
                ExpectedConflict.at(current.getConflictId(), current.getRevision()),
                faction, context(at)));
            return this;
        }

        Fixture campaign(String id, String faction, long at) {
            participation(faction, at);
            KOMEConflictRecord current = conflict();
            ok(data.getConflictService().commit(TILE,
                ExpectedConflict.at(current.getConflictId(), current.getRevision()),
                new CommitmentInput(id, KOMEHiredUnitClass.CAMPAIGN,
                    EntryOrigin.LEGAL_ARRIVAL, "M-" + id), context(at)));
            evaluation.factions.put(id, faction);
            return this;
        }

        Fixture relation(String first, String second, Relation relation) {
            evaluation.relations.put(pair(first, second), relation);
            return this;
        }

        Fixture defense(String faction) {
            evaluation.defense = EmergencyDefensePresence.present(faction);
            return this;
        }

        Fixture episode(TimerStatus status, boolean active) {
            KOMEConflictRecord current = conflict();
            CombatEpisode episode = new CombatEpisode(1L,
                current.getConflictId() + ":E1", active ? EpisodeState.ACTIVE
                    : EpisodeState.INACTIVE, 50L, active ? null : Long.valueOf(100L));
            long elapsed = status == TimerStatus.COMPLETE ? 50L : 20L;
            TimerSlot response = new TimerSlot(status, 50L, elapsed, 100L,
                episode.episodeId);
            ok(data.getConflictService().checkpointCombat(TILE,
                ExpectedConflict.at(current.getConflictId(), current.getRevision()),
                episode, response, TimerSlot.unstarted(), context(100L)));
            return this;
        }

        KOMEOrdinaryBattleResolutionService service() {
            return KOMEOrdinaryBattleResolutionServiceTest.service(evaluation,
                fortification, notifications, observer);
        }

        KOMEConflictRecord conflict() { return data.getConflictService().get(TILE); }
        KOMEConquestTile tile() { return data.getConquestTileIfPresent(TILE); }

        int notifications() { return notifications[0]; }
    }

    private static KOMEOrdinaryBattleResolutionService service(
            Evaluation evaluation, final KOMEFortificationContextResolver.Decision fortification,
            final int[] notifications,
            KOMEOrdinaryBattleResolutionService.PublicationObserver observer) {
        return new KOMEOrdinaryBattleResolutionService(evaluation,
            new KOMEOrdinaryBattleResolutionService.ObjectiveSource() {
                public KOMEConflictLifecycleService.ObjectiveContest inspect(KOMEWorldData data,
                        KOMEConflictRecord conflict) {
                    return new KOMEConflictLifecycleService.ObjectiveContest(
                        KOMEConflictLifecycleService.ObjectiveContestStatus.CONTESTED,
                        "test hostile objective authority");
                }
            },
            new KOMEOrdinaryBattleResolutionService.FortificationSource() {
                public KOMEFortificationContextResolver.Decision evaluate(
                        KOMEWorldData data, String tileId) { return fortification; }
            }, new KOMEOrdinaryBattleResolutionService.NotificationSink() {
                public void resolved(KOMEOrdinaryBattleResolutionService.Plan plan,
                        KOMEConflictRecord ended) { notifications[0]++; }
            }, observer, KOMEOrdinaryBattleResolutionService.ResolutionFinalizer.NONE);
    }

    private static final class Evaluation
            implements KOMEOrdinaryBattleResolutionService.EvaluationSource {
        final Map<String, String> factions = new LinkedHashMap<String, String>();
        final Map<String, Relation> relations = new LinkedHashMap<String, Relation>();
        EmergencyDefensePresence defense = EmergencyDefensePresence.none();

        public KOMECombatStateEvaluator.Result evaluate(KOMEWorldData data,
                final KOMEConflictRecord conflict) {
            return new KOMECombatStateEvaluator().evaluate(conflict, new Authority() {
                public DetachmentResolution resolveDetachment(String id) {
                    String faction = factions.get(id);
                    return faction == null ? null : new DetachmentResolution(id,
                        ReferenceStatus.RESOLVED, KOMEHiredUnitClass.CAMPAIGN,
                        faction, conflict.getTileId(), "test authority");
                }
                public EmergencyDefensePresence emergencyDefense(
                        KOMEConflictRecord ignored) { return defense; }
                public Relation relation(String first, String second) {
                    Relation value = relations.get(pair(first, second));
                    return value == null ? Relation.NON_ALLIED : value;
                }
            });
        }
    }

    private static final class OneShotObserver
            implements KOMEOrdinaryBattleResolutionService.PublicationObserver {
        final boolean failHistory;
        final boolean failOwnership;
        boolean failed;
        OneShotObserver(boolean history, boolean ownership) {
            failHistory = history; failOwnership = ownership;
        }
        public void beforeFinalValidation(KOMEWorldData data,
                KOMEOrdinaryBattleResolutionService.Plan plan) { }
        public void afterCaptureHistory(KOMEWorldData data,
                KOMEOrdinaryBattleResolutionService.Plan plan) {
            if (failHistory && !failed) { failed = true; throw new RuntimeException("test history interruption"); }
        }
        public void afterOwnership(KOMEWorldData data,
                KOMEOrdinaryBattleResolutionService.Plan plan) {
            if (failOwnership && !failed) { failed = true; throw new RuntimeException("test ownership interruption"); }
        }
    }

    private static void complete(KOMEWorldData data, String tileId) {
        KOMEConflictRecord current = data.getConflictService().get(tileId);
        CombatEpisode episode = new CombatEpisode(1L, current.getConflictId() + ":E1",
            EpisodeState.INACTIVE, 50L, 100L);
        TimerSlot response = new TimerSlot(TimerStatus.COMPLETE, 50L, 50L,
            100L, episode.episodeId);
        ok(data.getConflictService().checkpointCombat(tileId,
            ExpectedConflict.at(current.getConflictId(), current.getRevision()),
            episode, response, TimerSlot.unstarted(), context(100L)));
    }

    private static int captureCount(KOMEWorldData data) {
        int count = 0;
        for (KOMEWar war : data.wars.values()) count += war.tileCaptureHistory.size();
        return count;
    }

    private static KOMEWar.TileCaptureEvent capture(KOMEWorldData data) {
        for (KOMEWar war : data.wars.values())
            if (!war.tileCaptureHistory.isEmpty()) return war.tileCaptureHistory.get(0);
        return null;
    }

    private static String pair(String first, String second) {
        String a = KOMEAlliance.normalizeFactionKey(first);
        String b = KOMEAlliance.normalizeFactionKey(second);
        return a.compareTo(b) < 0 ? a + "/" + b : b + "/" + a;
    }

    private static Context context(long at) {
        return new Context(at, "ordinary-battle-test", "test transition");
    }
}
