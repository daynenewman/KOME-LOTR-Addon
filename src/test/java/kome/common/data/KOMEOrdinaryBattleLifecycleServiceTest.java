package kome.common.data;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

import static kome.common.data.KOMECombatStateEvaluator.*;
import static kome.common.data.KOMEConflictContracts.*;
import static kome.common.data.KOMEConflictRecord.*;
import static kome.common.data.KOMEConflictServiceTest.*;
import static org.junit.Assert.*;

public class KOMEOrdinaryBattleLifecycleServiceTest {
    @Test public void startsFreshEpisodeWithCanonicalLevelDurationsAndAlternateConfig() {
        assertStartDuration(1, 20, 35, 50, 20L * 60_000L);
        assertStartDuration(2, 20, 35, 50, 35L * 60_000L);
        assertStartDuration(3, 20, 35, 50, 50L * 60_000L);
        assertStartDuration(1, 3, 5, 8, 3L * 60_000L);
        assertStartDuration(2, 3, 5, 8, 5L * 60_000L);
        assertStartDuration(3, 3, 5, 8, 8L * 60_000L);
    }

    @Test public void durationIsSnapshottedAndAllReinforcementKindsDoNotRestart() {
        Fixture f = fixture(1, 2, 4, 6);
        assertEquals(KOMEOrdinaryBattleLifecycleService.Code.EPISODE_STARTED,
            f.lifecycle.process(f.data, TILE, 100L).code);
        KOMEConflictRecord initial = f.record();
        long duration = initial.getResponseTimer().durationSnapshotMillis;
        assertEquals(120_000L, duration);

        f.minutes[0] = 7;
        f.minutes[1] = 9;
        f.minutes[2] = 11;
        f.commit("C3", "gondor", 110L); // defender reinforcement
        f.lifecycle.process(f.data, TILE, 111L);
        f.commit("C4", "mordor", 120L); // attacker reinforcement
        f.lifecycle.process(f.data, TILE, 121L);
        f.joinAndCommit("rohan", "C5", 130L); // third party
        f.lifecycle.process(f.data, TILE, 132L);

        KOMEConflictRecord current = f.record();
        assertEquals("CF1:E1", current.getCombatEpisode().episodeId);
        assertEquals(Long.valueOf(100L), current.getCombatEpisode().startedAtMillis);
        assertEquals(duration, current.getResponseTimer().durationSnapshotMillis);
        assertEquals(1, f.notifications.count);
    }

    @Test public void elapsedUsesBoundedServerActiveCheckpointsAndCompletesOnce() {
        Fixture f = fixture(1, 1, 2, 3);
        f.lifecycle.process(f.data, TILE, 100L);
        long revision = f.record().getRevision();

        assertEquals(KOMEOrdinaryBattleLifecycleService.Code.NO_CHANGE,
            f.lifecycle.process(f.data, TILE, 5_099L).code);
        assertEquals(revision, f.record().getRevision());
        assertEquals(KOMEOrdinaryBattleLifecycleService.Code.TIMER_CHECKPOINTED,
            f.lifecycle.process(f.data, TILE, 5_100L).code);
        assertEquals(5_000L, f.record().getResponseTimer().elapsedMillis);

        assertEquals(KOMEOrdinaryBattleLifecycleService.Code.TIMER_COMPLETED_HOSTILITY_CONTINUES,
            f.lifecycle.process(f.data, TILE, 60_100L).code);
        KOMEConflictRecord complete = f.record();
        assertEquals(TimerStatus.COMPLETE, complete.getResponseTimer().status);
        assertEquals(60_000L, complete.getResponseTimer().elapsedMillis);
        assertEquals(EpisodeState.ACTIVE, complete.getCombatEpisode().state);
        long completeRevision = complete.getRevision();
        assertEquals(KOMEOrdinaryBattleLifecycleService.Code.NO_CHANGE,
            f.lifecycle.process(f.data, TILE, 120_100L).code);
        assertEquals(completeRevision, f.record().getRevision());

        Fixture restarted = f.restart();
        assertEquals(KOMEOrdinaryBattleLifecycleService.Code.NO_CHANGE,
            restarted.lifecycle.process(restarted.data, TILE, 1_000_000L).code);
        assertEquals(TimerStatus.COMPLETE,
            restarted.record().getResponseTimer().status);
        assertEquals(EpisodeState.ACTIVE,
            restarted.record().getCombatEpisode().state);
        assertEquals(0, restarted.notifications.count);
    }

    @Test public void restartDoesNotCountOfflineWallClockOrRepeatNotification() {
        Fixture f = fixture(1, 1, 2, 3);
        f.lifecycle.process(f.data, TILE, 100L);
        f.lifecycle.process(f.data, TILE, 5_100L);
        assertEquals(5_000L, f.record().getResponseTimer().elapsedMillis);

        Fixture restarted = f.restart();
        KOMEConflictRecord before = restarted.record();
        assertEquals("CF1:E1", before.getCombatEpisode().episodeId);
        assertEquals(60_000L, before.getResponseTimer().durationSnapshotMillis);
        assertEquals(KOMEOrdinaryBattleLifecycleService.Code.NO_CHANGE,
            restarted.lifecycle.process(restarted.data, TILE, 1_000_000L).code);
        assertEquals(5_000L, restarted.record().getResponseTimer().elapsedMillis);
        assertEquals(0, restarted.notifications.count);

        restarted.lifecycle.process(restarted.data, TILE, 1_005_000L);
        assertEquals(10_000L, restarted.record().getResponseTimer().elapsedMillis);
        assertEquals(0, restarted.notifications.count);
    }

    @Test public void unknownAuthorityAndPhysicalAbsenceCannotEndEpisode() {
        Fixture f = fixture(1, 1, 2, 3);
        f.lifecycle.process(f.data, TILE, 100L);
        f.evaluation.unknown = true;
        f.evaluation.objectiveUnknown = true;
        assertEquals(KOMEOrdinaryBattleLifecycleService.Code.DEFERRED_UNKNOWN_AUTHORITY,
            f.lifecycle.process(f.data, TILE, 5_100L).code);
        assertEquals(EpisodeState.ACTIVE, f.record().getCombatEpisode().state);
        assertEquals(5_000L, f.record().getResponseTimer().elapsedMillis);

        // The evaluator deliberately has no player/entity/chunk inputs. Strategic commitments
        // remain authoritative when physical observations disappear.
        f.evaluation.unknown = false;
        f.evaluation.objectiveUnknown = false;
        assertEquals(KOMEOrdinaryBattleLifecycleService.Code.NO_CHANGE,
            f.lifecycle.process(f.data, TILE, 5_200L).code);
        assertEquals(EpisodeState.ACTIVE, f.record().getCombatEpisode().state);
    }

    @Test public void genuineCessationEndsEpisodeAndRenewedHostilityStartsE2Once() {
        Fixture f = fixture(1, 1, 2, 3);
        f.lifecycle.process(f.data, TILE, 100L);
        assertEquals(1, f.notifications.count);
        f.evaluation.hostile = false;
        f.evaluation.objectiveHostile = false;
        assertEquals(KOMEOrdinaryBattleLifecycleService.Code.EPISODE_STOPPED,
            f.lifecycle.process(f.data, TILE, 5_100L).code);
        KOMEConflictRecord ended = f.record();
        assertEquals(EpisodeState.INACTIVE, ended.getCombatEpisode().state);
        assertEquals(Long.valueOf(5_100L), ended.getCombatEpisode().endedAtMillis);
        assertEquals(TimerStatus.PAUSED, ended.getResponseTimer().status);
        assertEquals(5_000L, ended.getResponseTimer().elapsedMillis);
        assertEquals(1, f.notifications.count);

        f.minutes[0] = 2;
        f.minutes[1] = 3;
        f.minutes[2] = 4;
        f.evaluation.hostile = true;
        f.evaluation.objectiveHostile = true;
        assertEquals(KOMEOrdinaryBattleLifecycleService.Code.EPISODE_STARTED,
            f.lifecycle.process(f.data, TILE, 5_200L).code);
        KOMEConflictRecord renewed = f.record();
        assertEquals("CF1:E2", renewed.getCombatEpisode().episodeId);
        assertEquals(120_000L, renewed.getResponseTimer().durationSnapshotMillis);
        assertEquals(2, f.notifications.count);
        assertEquals(KOMEOrdinaryBattleLifecycleService.Code.NO_CHANGE,
            f.lifecycle.process(f.data, TILE, 5_300L).code);
        assertEquals(2, f.notifications.count);
    }

    @Test public void defenderDisappearanceAndReturnKeepSameActiveEpisodeAndTimer() {
        Fixture f = fixture(1, 1, 2, 3);
        f.lifecycle.process(f.data, TILE, 100L);
        f.lifecycle.process(f.data, TILE, 5_100L);
        f.release("C1", 5_101L); // defender leaves, not the hostile offensive commitment
        KOMEOrdinaryBattleLifecycleService.Observation lone = f.lifecycle.inspect(f.data, f.record());
        assertTrue(lone.objective.objectiveContested);
        assertFalse(lone.hostileMilitaryPairPresent);
        assertTrue(lone.militaryAuthorityKnown);
        assertEquals(KOMEOrdinaryBattleLifecycleService.Code.TIMER_CHECKPOINTED,
            f.lifecycle.process(f.data, TILE, 10_100L).code);
        assertEquals(10_000L, f.record().getResponseTimer().elapsedMillis);
        assertEquals(EpisodeState.ACTIVE, f.record().getCombatEpisode().state);
        assertEquals("CF1:E1", f.record().getCombatEpisode().episodeId);
        f.commit("C3", "mordor", 10_101L);
        f.lifecycle.process(f.data, TILE, 10_102L);
        f.joinAndCommit("gondor", "C1", 10_103L);
        f.lifecycle.process(f.data, TILE, 15_100L);
        assertTrue(f.lifecycle.inspect(f.data, f.record()).hostileMilitaryPairPresent);
        assertEquals("CF1:E1", f.record().getCombatEpisode().episodeId);
        assertEquals(15_000L, f.record().getResponseTimer().elapsedMillis);
        assertEquals(1, f.notifications.count);
    }

    @Test public void unopposedEntryStartsResponseAndCompletesWithoutClaimingActualCombat() {
        Fixture f = fixture(1, 1, 2, 3);
        f.release("C1", 20L);
        KOMEOrdinaryBattleLifecycleService.Observation observation =
            f.lifecycle.inspect(f.data, f.record());
        assertTrue(observation.objective.objectiveContested);
        assertFalse(observation.hostileMilitaryPairPresent);
        assertEquals(KOMEOrdinaryBattleLifecycleService.Code.EPISODE_STARTED,
            f.lifecycle.process(f.data, TILE, 100L).code);
        assertEquals(KOMEOrdinaryBattleLifecycleService.Code.TIMER_COMPLETED_RESOLUTION_ELIGIBLE,
            f.lifecycle.process(f.data, TILE, 60_100L).code);
        assertEquals(EpisodeState.ACTIVE, f.record().getCombatEpisode().state);
        assertEquals(TimerStatus.COMPLETE, f.record().getResponseTimer().status);
        assertEquals("CF1:E1", f.record().getCombatEpisode().episodeId);
        assertFalse(f.lifecycle.inspect(f.data, f.record()).hostileMilitaryPairPresent);
    }

    @Test public void defenderLossThenRestartStillContinuesE1WithoutOfflineElapsed() {
        Fixture f = fixture(1, 1, 2, 3);
        f.lifecycle.process(f.data, TILE, 100L);
        f.release("C1", 101L);
        f.lifecycle.process(f.data, TILE, 5_100L);
        Fixture restarted = f.restart();
        restarted.lifecycle.process(restarted.data, TILE, 1_000_000L);
        assertEquals(5_000L, restarted.record().getResponseTimer().elapsedMillis);
        restarted.lifecycle.process(restarted.data, TILE, 1_055_000L);
        assertEquals(TimerStatus.COMPLETE, restarted.record().getResponseTimer().status);
        assertEquals("CF1:E1", restarted.record().getCombatEpisode().episodeId);
        assertEquals(EpisodeState.ACTIVE, restarted.record().getCombatEpisode().state);
    }

    @Test public void unknownMilitaryPresenceDoesNotStopKnownObjectiveResponseTime() {
        Fixture f = fixture(1, 1, 2, 3);
        f.evaluation.unknown = true;
        assertTrue(f.lifecycle.inspect(f.data, f.record()).objective.objectiveContested);
        assertFalse(f.lifecycle.inspect(f.data, f.record()).militaryAuthorityKnown);
        assertEquals(KOMEOrdinaryBattleLifecycleService.Code.EPISODE_STARTED,
            f.lifecycle.process(f.data, TILE, 100L).code);
        assertEquals(KOMEOrdinaryBattleLifecycleService.Code.TIMER_CHECKPOINTED,
            f.lifecycle.process(f.data, TILE, 5_100L).code);
        assertEquals(5_000L, f.record().getResponseTimer().elapsedMillis);
        f.lifecycle.process(f.data, TILE, 60_100L);
        assertEquals(TimerStatus.COMPLETE, f.record().getResponseTimer().status);
        assertEquals(EpisodeState.ACTIVE, f.record().getCombatEpisode().state);
    }

    @Test public void hostileMilitaryPairAloneCannotStartAnUncontestedObjectiveEpisode() {
        Fixture f = fixture(1, 1, 2, 3);
        f.evaluation.objectiveHostile = false;
        assertTrue(f.lifecycle.inspect(f.data, f.record()).hostileMilitaryPairPresent);
        assertFalse(f.lifecycle.inspect(f.data, f.record()).objective.objectiveContested);
        assertEquals(KOMEOrdinaryBattleLifecycleService.Code.NO_CHANGE,
            f.lifecycle.process(f.data, TILE, 100L).code);
        assertEquals(0L, f.record().getCombatEpisode().sequence);
        f.evaluation.hostile = false; // allied/neutral-only activity is not combat either
        assertFalse(f.lifecycle.inspect(f.data, f.record()).hostileMilitaryPairPresent);
        assertEquals(KOMEOrdinaryBattleLifecycleService.Code.NO_CHANGE,
            f.lifecycle.process(f.data, TILE, 200L).code);
    }

    @Test public void fortifiedAndAmbiguousContextConservativelyDefer() {
        Fixture f = fixture(1, 1, 2, 3);
        f.fortification.decision = KOMEFortificationContextResolver.Decision.fortified(
            "test fortified context");
        assertEquals(KOMEOrdinaryBattleLifecycleService.Code.DEFERRED_FORTIFIED,
            f.lifecycle.process(f.data, TILE, 100L).code);
        assertEquals(0L, f.record().getCombatEpisode().sequence);
        f.fortification.decision = KOMEFortificationContextResolver.Decision.ambiguous(
            "test ambiguous context");
        assertEquals(KOMEOrdinaryBattleLifecycleService.Code.DEFERRED_FORTIFIED,
            f.lifecycle.process(f.data, TILE, 200L).code);
        assertEquals(0, f.notifications.count);
    }

    @Test public void staleRevisionRejectsPublicationWithoutPartialEpisodeOrNotification() {
        Fixture f = fixture(1, 1, 2, 3, new KOMEOrdinaryBattleLifecycleService.BeforePublication() {
            private boolean changed;
            public void run(KOMEWorldData data, KOMEConflictRecord evaluated) {
                if (changed) return;
                changed = true;
                KOMEConflictService.Result mutation = data.getConflictService()
                    .beginFactionParticipation(evaluated.getTileId(),
                        ExpectedConflict.at(evaluated.getConflictId(), evaluated.getRevision()),
                        "rohan", new Context(evaluated.getLastTransition().timestampMillis + 1L,
                            "test", "force stale lifecycle publication"));
                assertEquals(KOMEConflictContracts.Code.SUCCESS, mutation.code);
            }
        });
        assertEquals(KOMEOrdinaryBattleLifecycleService.Code.STALE_RETRY,
            f.lifecycle.process(f.data, TILE, 100L).code);
        assertEquals(0L, f.record().getCombatEpisode().sequence);
        assertEquals(TimerStatus.NOT_STARTED, f.record().getResponseTimer().status);
        assertEquals(0, f.notifications.count);
    }

    private static void assertStartDuration(int level, int first, int second,
            int third, long expected) {
        Fixture f = fixture(level, first, second, third);
        KOMEOrdinaryBattleLifecycleService.Result result =
            f.lifecycle.process(f.data, TILE, 100L);
        assertEquals(KOMEOrdinaryBattleLifecycleService.Code.EPISODE_STARTED,
            result.code);
        assertEquals(expected, f.record().getResponseTimer().durationSnapshotMillis);
        assertEquals(TimerStatus.RUNNING, f.record().getResponseTimer().status);
        assertEquals(TimerStatus.NOT_STARTED, f.record().getCaptureTimer().status);
        assertEquals(1, f.notifications.count);
    }

    private static final String TILE = "T100";

    private static Fixture fixture(int level, int first, int second, int third) {
        return fixture(level, first, second, third,
            KOMEOrdinaryBattleLifecycleService.BeforePublication.NONE);
    }

    private static Fixture fixture(int level, int first, int second, int third,
            KOMEOrdinaryBattleLifecycleService.BeforePublication before) {
        KOMEWorldData data = new KOMEWorldData("ordinary-lifecycle-test");
        KOMEConquestTile tile = new KOMEConquestTile(TILE);
        tile.setWaypointDefaults(level, "gondor", "test");
        data.conquestTiles.put(TILE, tile);
        KOMEConflictService conflicts = data.getConflictService();
        KOMEConflictRecord record = ok(conflicts.start(TILE, State.ORDINARY,
            ExpectedConflict.absent(), Collections.<GarrisonSeed>emptyList(), context(10L)));
        record = ok(conflicts.beginFactionParticipation(TILE, expected(record),
            "gondor", context(11L)));
        record = ok(conflicts.commit(TILE, expected(record),
            new CommitmentInput("C1", KOMEHiredUnitClass.CAMPAIGN,
                EntryOrigin.LEGAL_ARRIVAL, "M1"), context(12L)));
        record = ok(conflicts.beginFactionParticipation(TILE, expected(record),
            "mordor", context(13L)));
        record = ok(conflicts.commit(TILE, expected(record),
            new CommitmentInput("C2", KOMEHiredUnitClass.CAMPAIGN,
                EntryOrigin.LEGAL_ARRIVAL, "M2"), context(14L)));
        Evaluation evaluation = new Evaluation();
        evaluation.factions.put("C1", "gondor");
        evaluation.factions.put("C2", "mordor");
        MutableFortification fortification = new MutableFortification();
        Notifications notifications = new Notifications();
        int[] minutes = new int[] { first, second, third };
        KOMEOrdinaryBattleLifecycleService lifecycle = lifecycle(evaluation,
            fortification, notifications, minutes, before);
        return new Fixture(data, evaluation, fortification, notifications,
            minutes, lifecycle);
    }

    private static KOMEOrdinaryBattleLifecycleService lifecycle(Evaluation evaluation,
            MutableFortification fortification, Notifications notifications,
            final int[] minutes, KOMEOrdinaryBattleLifecycleService.BeforePublication before) {
        return new KOMEOrdinaryBattleLifecycleService(evaluation, evaluation, fortification,
            new KOMEOrdinaryBattleLifecycleService.DurationSource() {
                public KOMEOrdinaryBattlePlanner.DurationPlan select(KOMEWorldData data,
                        KOMEConflictRecord conflict) {
                    KOMEConquestTile tile = data.getConquestTileIfPresent(conflict.getTileId());
                    return KOMEOrdinaryBattlePlanner.responseDuration(tile.waypointLevel,
                        minutes[0], minutes[1], minutes[2]);
                }
            }, notifications, before);
    }

    private static final class Fixture {
        final KOMEWorldData data;
        final Evaluation evaluation;
        final MutableFortification fortification;
        final Notifications notifications;
        final int[] minutes;
        final KOMEOrdinaryBattleLifecycleService lifecycle;

        Fixture(KOMEWorldData data, Evaluation evaluation,
                MutableFortification fortification, Notifications notifications,
                int[] minutes, KOMEOrdinaryBattleLifecycleService lifecycle) {
            this.data = data;
            this.evaluation = evaluation;
            this.fortification = fortification;
            this.notifications = notifications;
            this.minutes = minutes;
            this.lifecycle = lifecycle;
        }

        KOMEConflictRecord record() { return data.getConflictService().get(TILE); }

        void release(String id, long now) {
            ok(data.getConflictService().releaseValidatedCommitment(
                new ValidatedDepartureRequest(TILE, id, evaluation.factions.get(id), expected(record())),
                new DetachmentResolver() {
                    public DetachmentResolution resolve(String companyId) {
                        return new DetachmentResolution(companyId, ReferenceStatus.RESOLVED,
                            KOMEHiredUnitClass.CAMPAIGN, evaluation.factions.get(companyId), TILE,
                            "test departure authority");
                    }
                }, context(now)));
        }

        void commit(String id, String faction, long now) {
            KOMEConflictRecord current = record();
            ok(data.getConflictService().commit(TILE, expected(current),
                new CommitmentInput(id, KOMEHiredUnitClass.CAMPAIGN,
                    EntryOrigin.LEGAL_ARRIVAL, "M-" + id), context(now)));
            evaluation.factions.put(id, faction);
        }

        void joinAndCommit(String faction, String id, long now) {
            KOMEConflictRecord current = record();
            current = ok(data.getConflictService().beginFactionParticipation(TILE,
                expected(current), faction, context(now)));
            ok(data.getConflictService().commit(TILE, expected(current),
                new CommitmentInput(id, KOMEHiredUnitClass.CAMPAIGN,
                    EntryOrigin.LEGAL_ARRIVAL, "M-" + id), context(now + 1L)));
            evaluation.factions.put(id, faction);
        }

        Fixture restart() {
            KOMEWorldData restarted = new KOMEWorldData("ordinary-lifecycle-restart");
            KOMEConquestTile source = data.getConquestTileIfPresent(TILE);
            KOMEConquestTile tile = new KOMEConquestTile(TILE);
            tile.setWaypointDefaults(source.waypointLevel, source.defaultRulingFaction,
                source.mapRegion);
            restarted.conquestTiles.put(TILE, tile);
            restarted.getConflictService().replaceFrom(data.getConflictService());
            Notifications freshNotifications = new Notifications();
            return new Fixture(restarted, evaluation, fortification,
                freshNotifications, minutes, lifecycle(evaluation, fortification,
                    freshNotifications, minutes,
                    KOMEOrdinaryBattleLifecycleService.BeforePublication.NONE));
        }
    }

    private static final class Evaluation
            implements KOMEOrdinaryBattleLifecycleService.EvaluationSource,
                KOMEOrdinaryBattleLifecycleService.ObjectiveSource {
        final Map<String, String> factions = new LinkedHashMap<String, String>();
        boolean hostile = true;
        boolean objectiveHostile = true;
        boolean objectiveUnknown;
        boolean unknown;

        public KOMEConflictLifecycleService.ObjectiveContest inspect(KOMEWorldData data,
                KOMEConflictRecord conflict) {
            return new KOMEConflictLifecycleService.ObjectiveContest(objectiveUnknown
                    ? KOMEConflictLifecycleService.ObjectiveContestStatus.UNKNOWN
                    : objectiveHostile
                        ? KOMEConflictLifecycleService.ObjectiveContestStatus.CONTESTED
                        : KOMEConflictLifecycleService.ObjectiveContestStatus.UNCONTESTED,
                "test objective authority independent of military pairs");
        }

        public KOMECombatStateEvaluator.Result evaluate(KOMEWorldData data,
                KOMEConflictRecord conflict) {
            return new KOMECombatStateEvaluator().evaluate(conflict, new Authority() {
                public DetachmentResolution resolveDetachment(String id) {
                    String faction = factions.get(id);
                    return faction == null ? null : new DetachmentResolution(id,
                        ReferenceStatus.RESOLVED, KOMEHiredUnitClass.CAMPAIGN,
                        faction, TILE, "test strategic authority");
                }
                public EmergencyDefensePresence emergencyDefense(KOMEConflictRecord ignored) {
                    return EmergencyDefensePresence.none();
                }
                public Relation relation(String first, String second) {
                    return unknown ? Relation.UNKNOWN
                        : hostile ? Relation.HOSTILE : Relation.NON_ALLIED;
                }
            });
        }
    }

    private static final class MutableFortification
            implements KOMEOrdinaryBattleLifecycleService.FortificationSource {
        KOMEFortificationContextResolver.Decision decision =
            KOMEFortificationContextResolver.Decision.clear();
        public KOMEFortificationContextResolver.Decision evaluate(KOMEWorldData data,
                String tileId) { return decision; }
    }

    private static final class Notifications
            implements KOMEOrdinaryBattleLifecycleService.NotificationSink {
        int count;
        public void episodeStarted(KOMEConflictRecord record, long durationMillis) {
            count++;
        }
    }
}
