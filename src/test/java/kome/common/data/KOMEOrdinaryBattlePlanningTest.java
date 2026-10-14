package kome.common.data;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.Test;

import static kome.common.data.KOMECombatStateEvaluator.*;
import static kome.common.data.KOMEConflictContracts.*;
import static kome.common.data.KOMEConflictRecord.*;
import static org.junit.Assert.*;

public class KOMEOrdinaryBattlePlanningTest {
    @Test public void responseDurationsUseObjectiveLevelAndConfiguredValues() {
        assertDuration(1, 20, 35, 50, 20L * 60_000L);
        assertDuration(2, 20, 35, 50, 35L * 60_000L);
        assertDuration(3, 20, 35, 50, 50L * 60_000L);
        assertDuration(1, 7, 13, 29, 7L * 60_000L);
        assertDuration(2, 7, 13, 29, 13L * 60_000L);
        assertDuration(3, 7, 13, 29, 29L * 60_000L);
        assertEquals(KOMEOrdinaryBattlePlanner.DurationCode.INVALID_OBJECTIVE_LEVEL,
            KOMEOrdinaryBattlePlanner.responseDuration(0, 20, 35, 50).code);
        assertEquals(KOMEOrdinaryBattlePlanner.DurationCode.INVALID_OBJECTIVE_LEVEL,
            KOMEOrdinaryBattlePlanner.responseDuration(4, 20, 35, 50).code);
    }

    @Test public void hostileAndThirdPartyGraphsAreDeterministicAndDynamic() {
        KOMEConflictRecord conflict = conflict(State.ORDINARY,
            faction("gondor", 10L), faction("mordor", 20L), faction("rohan", 30L));
        TestAuthority authority = authority(conflict)
            .relation("gondor", "mordor", Relation.HOSTILE)
            .relation("gondor", "rohan", Relation.ALLIED)
            .relation("mordor", "rohan", Relation.HOSTILE);
        Result first = new KOMECombatStateEvaluator().evaluate(conflict, authority);
        assertEquals(KOMECombatStateEvaluator.Code.RESOLVED, first.code);
        assertTrue(first.hostilePairPresent);
        assertEquals(Arrays.asList("gondor", "mordor", "rohan"),
            Arrays.asList(first.factions.keySet().toArray(new String[0])));
        assertEquals(3, first.pairs.size());

        authority.relation("gondor", "mordor", Relation.ALLIED)
            .relation("mordor", "rohan", Relation.ALLIED);
        Result peaceful = new KOMECombatStateEvaluator().evaluate(conflict, authority);
        assertFalse(peaceful.hostilePairPresent);

        authority.relation("gondor", "rohan", Relation.HOSTILE);
        assertTrue(new KOMECombatStateEvaluator().evaluate(conflict, authority)
            .hostilePairPresent);
    }

    @Test public void alliedAndNeutralPresenceAreNotHostileButOnlyAlliesFormCoalition() {
        KOMEConflictRecord conflict = conflict(State.ORDINARY,
            faction("gondor", 10L), faction("rohan", 20L));
        TestAuthority allied = authority(conflict)
            .relation("gondor", "rohan", Relation.ALLIED);
        Result alliedState = new KOMECombatStateEvaluator().evaluate(conflict, allied);
        assertFalse(alliedState.hostilePairPresent);
        assertEquals(KOMEOrdinaryBattleOwnershipResolver.Code.WINNER_DEFAULT_OWNER,
            resolve(conflict, "gondor", alliedState).code);

        TestAuthority neutral = authority(conflict)
            .relation("gondor", "rohan", Relation.NON_ALLIED);
        KOMEOrdinaryBattleOwnershipResolver.Plan plan = resolve(conflict, "", new KOMECombatStateEvaluator()
            .evaluate(conflict, neutral));
        assertEquals(KOMEOrdinaryBattleOwnershipResolver.Code.NON_ALLIED_SURVIVORS,
            plan.code);
    }

    @Test public void unknownDiplomacyAndStrategicAuthorityFailClosed() {
        KOMEConflictRecord conflict = conflict(State.ORDINARY,
            faction("gondor", 10L), faction("mordor", 20L));
        TestAuthority unknownRelation = authority(conflict)
            .relation("gondor", "mordor", Relation.UNKNOWN);
        Result relation = new KOMECombatStateEvaluator().evaluate(conflict,
            unknownRelation);
        assertEquals(KOMECombatStateEvaluator.Code.DEFERRED_UNKNOWN_AUTHORITY, relation.code);
        assertTrue(relation.unknownAuthorityPresent);

        TestAuthority missingDetachment = authority(conflict)
            .missing("C2")
            .relation("gondor", "mordor", Relation.HOSTILE);
        Result missing = new KOMECombatStateEvaluator().evaluate(conflict,
            missingDetachment);
        assertEquals(KOMECombatStateEvaluator.Code.DEFERRED_UNKNOWN_AUTHORITY, missing.code);
        assertEquals(KOMEOrdinaryBattleOwnershipResolver.Code.DEFERRED_UNKNOWN_AUTHORITY,
            resolve(conflict, "gondor", missing).code);
    }

    @Test public void episodePlanningSeparatesExpiryFromMilitaryResolution() {
        KOMEConflictRecord conflict = activeEpisode(conflict(State.ORDINARY,
            faction("gondor", 10L), faction("mordor", 20L)), false);
        TestAuthority authority = authority(conflict)
            .relation("gondor", "mordor", Relation.HOSTILE);
        Result hostile = new KOMECombatStateEvaluator().evaluate(conflict, authority);
        assertEquals(KOMEOrdinaryBattlePlanner.EpisodeCode.HOSTILITY_CONTINUES,
            KOMEOrdinaryBattlePlanner.evaluateEpisode(conflict, hostile).code);

        KOMEConflictRecord expired = activeEpisode(conflict, true);
        Result stillHostile = new KOMECombatStateEvaluator().evaluate(expired,
            authority(expired).relation("gondor", "mordor", Relation.HOSTILE));
        assertEquals(KOMEOrdinaryBattlePlanner.EpisodeCode.HOSTILITY_CONTINUES_RESPONSE_EXPIRED,
            KOMEOrdinaryBattlePlanner.evaluateEpisode(expired, stillHostile).code);

        Result noHostility = new KOMECombatStateEvaluator().evaluate(expired,
            authority(expired).relation("gondor", "mordor", Relation.ALLIED));
        assertEquals(KOMEOrdinaryBattlePlanner.EpisodeCode.READY_FOR_OWNERSHIP_RESOLUTION,
            KOMEOrdinaryBattlePlanner.evaluateEpisode(expired, noHostility).code);
    }

    @Test public void renewedHostilityAfterInactiveEpisodeRequiresNewEpisode() {
        KOMEConflictRecord conflict = conflict(State.ORDINARY,
            faction("gondor", 10L), faction("mordor", 20L));
        Result hostile = new KOMECombatStateEvaluator().evaluate(conflict,
            authority(conflict).relation("gondor", "mordor", Relation.HOSTILE));
        assertEquals(KOMEOrdinaryBattlePlanner.EpisodeCode.START_NEW_EPISODE,
            KOMEOrdinaryBattlePlanner.evaluateEpisode(conflict, hostile).code);
    }

    @Test public void defaultOwnerLoneAttackerContinuityAndTieAreDeterministic() {
        KOMEConflictRecord ownerPresent = conflict(State.ORDINARY,
            faction("rohan", 10L), faction("gondor", 30L));
        Result ownerState = state(ownerPresent, Relation.ALLIED);
        assertWinner(KOMEOrdinaryBattleOwnershipResolver.Code.WINNER_DEFAULT_OWNER,
            "gondor", resolve(ownerPresent, "gondor", ownerState));

        KOMEConflictRecord lone = conflict(State.ORDINARY, faction("mordor", 10L));
        assertWinner(KOMEOrdinaryBattleOwnershipResolver.Code.WINNER_LONE_FACTION,
            "mordor", resolve(lone, "gondor",
                new KOMECombatStateEvaluator().evaluate(lone, authority(lone))));

        KOMEConflictRecord continuity = conflict(State.ORDINARY,
            faction("mordor", 20L), faction("rohan", 30L));
        assertWinner(KOMEOrdinaryBattleOwnershipResolver.Code.WINNER_LONGEST_CONTINUITY,
            "mordor", resolve(continuity, "gondor", state(continuity, Relation.ALLIED)));

        KOMEConflictRecord tie = conflict(State.ORDINARY,
            faction("rohan", 20L), faction("mordor", 20L));
        assertWinner(KOMEOrdinaryBattleOwnershipResolver.Code.WINNER_LONGEST_CONTINUITY,
            "mordor", resolve(tie, "gondor", state(tie, Relation.ALLIED)));
    }

    @Test public void timingHostilityAndFortificationGuardOwnershipPlanning() {
        KOMEConflictRecord conflict = conflict(State.ORDINARY, faction("mordor", 10L));
        Result state = new KOMECombatStateEvaluator().evaluate(conflict,
            authority(conflict));
        assertEquals(KOMEOrdinaryBattleOwnershipResolver.Code.TIMING_INCOMPLETE,
            KOMEOrdinaryBattleOwnershipResolver.resolve(conflict, "gondor", false,
                state, KOMEFortificationContextResolver.Decision.clear()).code);
        assertEquals(KOMEOrdinaryBattleOwnershipResolver.Code.DEFERRED_FORTIFIED,
            KOMEOrdinaryBattleOwnershipResolver.resolve(conflict, "gondor", true,
                state, KOMEFortificationContextResolver.Decision.fortified(
                    "Defensive Build present.")).code);
        assertEquals(KOMEOrdinaryBattleOwnershipResolver.Code.DEFERRED_FORTIFIED,
            KOMEOrdinaryBattleOwnershipResolver.resolve(conflict, "gondor", true,
                state, KOMEFortificationContextResolver.Decision.ambiguous(
                    "References unresolved.")).code);

        KOMEConflictRecord encirclement = conflict(State.ENCIRCLEMENT,
            faction("mordor", 10L));
        assertEquals(KOMEOrdinaryBattleOwnershipResolver.Code.DEFERRED_ENCIRCLEMENT,
            KOMEOrdinaryBattleOwnershipResolver.resolve(encirclement, "gondor", true,
                new KOMECombatStateEvaluator().evaluate(encirclement,
                    authority(encirclement)),
                KOMEFortificationContextResolver.Decision.clear()).code);
    }

    @Test public void emergencyDefensePendingAndActiveCountButFinalDefendersDoNot() {
        KOMEConflictRecord conflict = conflict(State.ORDINARY,
            faction("mordor", 10L), participationOnly("gondor", 10L));
        KOMEWorldData data = new KOMEWorldData();

        data.emergencyDefenseCommitments.put(conflict.getConflictId(),
            emergency(conflict, KOMEEmergencyDefenseCommitment.State.DEPLOYING,
                KOMEEmergencyDefenseCommitment.Disposition.PENDING));
        assertEquals(EmergencyDefensePresence.Status.PRESENT,
            KOMECombatStateEvaluator.emergencyDefensePresence(data, conflict).status);

        data.emergencyDefenseCommitments.put(conflict.getConflictId(),
            emergency(conflict, KOMEEmergencyDefenseCommitment.State.ACTIVE,
                KOMEEmergencyDefenseCommitment.Disposition.ACTIVE));
        assertEquals(EmergencyDefensePresence.Status.PRESENT,
            KOMECombatStateEvaluator.emergencyDefensePresence(data, conflict).status);

        data.emergencyDefenseCommitments.put(conflict.getConflictId(),
            emergency(conflict, KOMEEmergencyDefenseCommitment.State.ACTIVE,
                KOMEEmergencyDefenseCommitment.Disposition.DEAD));
        assertEquals(EmergencyDefensePresence.Status.NONE,
            KOMECombatStateEvaluator.emergencyDefensePresence(data, conflict).status);

        data.emergencyDefenseCommitments.put(conflict.getConflictId(),
            emergency(conflict, KOMEEmergencyDefenseCommitment.State.DEMOBILIZED,
                KOMEEmergencyDefenseCommitment.Disposition.SURVIVED_REFUNDED));
        assertEquals(EmergencyDefensePresence.Status.NONE,
            KOMECombatStateEvaluator.emergencyDefensePresence(data, conflict).status);
    }

    @Test public void emergencyDefenseMilitaryPresenceParticipatesInHostilityGraph() {
        KOMEConflictRecord conflict = conflict(State.ORDINARY,
            faction("mordor", 10L), participationOnly("gondor", 10L));
        TestAuthority authority = authority(conflict)
            .relation("gondor", "mordor", Relation.HOSTILE);
        authority.defense = EmergencyDefensePresence.present("gondor");
        Result result = new KOMECombatStateEvaluator().evaluate(conflict, authority);
        assertEquals(KOMECombatStateEvaluator.Code.RESOLVED, result.code);
        assertTrue(result.hostilePairPresent);
        assertTrue(result.faction("gondor").emergencyDefensePresent);
        assertFalse(result.faction("gondor").hasCampaignClaimAuthority());

        authority.relation("gondor", "mordor", Relation.ALLIED);
        Result allied = new KOMECombatStateEvaluator().evaluate(conflict, authority);
        assertWinner(KOMEOrdinaryBattleOwnershipResolver.Code.WINNER_DEFAULT_OWNER,
            "gondor", resolve(conflict, "gondor", allied));
    }

    @Test public void hostileContestPreventsOwnershipEvenAfterTimingCompletes() {
        KOMEConflictRecord conflict = conflict(State.ORDINARY,
            faction("gondor", 10L), faction("mordor", 20L));
        Result state = new KOMECombatStateEvaluator().evaluate(conflict,
            authority(conflict).relation("gondor", "mordor", Relation.HOSTILE));
        assertEquals(KOMEOrdinaryBattleOwnershipResolver.Code.HOSTILE_CONTEST_ACTIVE,
            resolve(conflict, "gondor", state).code);
    }

    @Test public void strategicPresenceDoesNotDependOnEntityPlayerOrChunkObservation() {
        KOMEConflictRecord conflict = conflict(State.ORDINARY,
            faction("gondor", 10L), faction("mordor", 20L));
        TestAuthority authority = authority(conflict)
            .relation("gondor", "mordor", Relation.HOSTILE);
        Result result = new KOMECombatStateEvaluator().evaluate(conflict, authority);
        assertTrue(result.hostilePairPresent);
        assertEquals(2, result.factions.size());
        assertTrue(result.diagnostics.isEmpty());
    }

    @Test public void sharedFortificationResolverPreservesArrivalClassification() {
        KOMEWorldData data = new KOMEWorldData();
        KOMEPlayerBuild build = new KOMEPlayerBuild();
        build.id = "B1"; build.tileId = "T100"; build.active = true;
        build.type = KOMEBuildType.DEFENSIVE;
        data.builds.put(build.id, build);
        assertEquals(KOMEFortificationContextResolver.Status.FORTIFIED,
            KOMEFortificationContextResolver.evaluate(data, "T100").status);
        assertTrue(KOMEFortificationContextResolver.requireForArrival(data, "T100"));

        build.contributions.add(null);
        assertEquals(KOMEFortificationContextResolver.Status.AMBIGUOUS,
            KOMEFortificationContextResolver.evaluate(data, "T100").status);
        try {
            KOMEFortificationContextResolver.requireForArrival(data, "T100");
            fail("Ambiguous defensive context must fail closed.");
        } catch (IllegalStateException expected) { }
    }

    private static void assertDuration(int level, int first, int second, int third,
            long expected) {
        KOMEOrdinaryBattlePlanner.DurationPlan plan =
            KOMEOrdinaryBattlePlanner.responseDuration(level, first, second, third);
        assertTrue(plan.selected());
        assertEquals(expected, plan.durationMillis);
    }

    private static void assertWinner(KOMEOrdinaryBattleOwnershipResolver.Code code,
            String faction, KOMEOrdinaryBattleOwnershipResolver.Plan plan) {
        assertEquals(code, plan.code);
        assertEquals(faction, plan.winnerFactionId);
        assertTrue(plan.hasWinner());
    }

    private static KOMEOrdinaryBattleOwnershipResolver.Plan resolve(KOMEConflictRecord conflict, String owner,
            Result state) {
        return KOMEOrdinaryBattleOwnershipResolver.resolve(conflict, owner, true,
            state, KOMEFortificationContextResolver.Decision.clear());
    }

    private static Result state(KOMEConflictRecord conflict, Relation relation) {
        TestAuthority authority = authority(conflict);
        java.util.List<String> factions = new java.util.ArrayList<String>();
        for (FactionParticipation participation
                : conflict.getFactionParticipation().values())
            if (participation.isActive()) factions.add(participation.factionId);
        for (int i = 0; i < factions.size(); i++)
            for (int j = i + 1; j < factions.size(); j++)
                authority.relation(factions.get(i), factions.get(j), relation);
        return new KOMECombatStateEvaluator().evaluate(conflict, authority);
    }

    private static KOMEConflictRecord activeEpisode(KOMEConflictRecord conflict,
            boolean complete) {
        KOMEConflictService service = holder;
        CombatEpisode episode = new CombatEpisode(1L,
            conflict.getConflictId() + ":E1", EpisodeState.ACTIVE, 100L, null);
        long duration = 1_000L;
        TimerSlot timer = new TimerSlot(complete ? TimerStatus.COMPLETE
            : TimerStatus.RUNNING, duration, complete ? duration : 100L,
            200L, episode.episodeId);
        return ok(service.checkpointCombat(conflict.getTileId(), expected(conflict),
            episode, timer, TimerSlot.unstarted(), context(200L)));
    }

    private static final KOMEConflictService holder = new KOMEConflictService();

    private static KOMEConflictRecord conflict(State state, FactionSeed... seeds) {
        holder.replaceFrom(new KOMEConflictService());
        KOMEConflictRecord record = ok(holder.start("T100", state,
            ExpectedConflict.absent(), Collections.<GarrisonSeed>emptyList(), context(1L)));
        int company = 1;
        for (FactionSeed seed : seeds) {
            record = ok(holder.beginFactionParticipation("T100", expected(record),
                seed.faction, context(seed.startedAt)));
            if (seed.committed) {
                record = ok(holder.commit("T100", expected(record),
                    new CommitmentInput("C" + company++, KOMEHiredUnitClass.CAMPAIGN,
                        EntryOrigin.LEGAL_ARRIVAL, "M" + company),
                    context(Math.max(seed.startedAt, record.getLastTransition().timestampMillis))));
            }
        }
        return record;
    }

    private static FactionSeed faction(String faction, long startedAt) {
        return new FactionSeed(faction, startedAt, true);
    }

    private static FactionSeed participationOnly(String faction, long startedAt) {
        return new FactionSeed(faction, startedAt, false);
    }

    private static final class FactionSeed {
        final String faction;
        final long startedAt;
        final boolean committed;
        FactionSeed(String faction, long startedAt, boolean committed) {
            this.faction = faction; this.startedAt = startedAt; this.committed = committed;
        }
    }

    private static TestAuthority authority(KOMEConflictRecord conflict) {
        TestAuthority authority = new TestAuthority();
        int index = 0;
        for (Map.Entry<String, Commitment> entry : conflict.getCommitments().entrySet()) {
            String faction = new java.util.ArrayList<String>(
                conflict.getFactionParticipation().keySet()).get(index++);
            authority.detachments.put(entry.getKey(), new DetachmentResolution(
                entry.getKey(), ReferenceStatus.RESOLVED, KOMEHiredUnitClass.CAMPAIGN,
                faction, conflict.getTileId(), "Strategically resolved; physical state unknown."));
        }
        return authority;
    }

    private static final class TestAuthority implements Authority {
        final Map<String, DetachmentResolution> detachments =
            new LinkedHashMap<String, DetachmentResolution>();
        final Map<String, Relation> relations = new LinkedHashMap<String, Relation>();
        EmergencyDefensePresence defense = EmergencyDefensePresence.none();

        TestAuthority relation(String first, String second, Relation relation) {
            relations.put(pair(first, second), relation); return this;
        }
        TestAuthority missing(String detachment) {
            detachments.put(detachment, new DetachmentResolution(detachment,
                ReferenceStatus.MISSING, null, "", "", "Missing")); return this;
        }
        @Override public DetachmentResolution resolveDetachment(String detachmentId) {
            return detachments.get(detachmentId);
        }
        @Override public EmergencyDefensePresence emergencyDefense(KOMEConflictRecord conflict) {
            return defense;
        }
        @Override public Relation relation(String first, String second) {
            Relation relation = relations.get(pair(first, second));
            return relation == null ? Relation.NON_ALLIED : relation;
        }
        private static String pair(String first, String second) {
            return first.compareTo(second) < 0 ? first + "/" + second : second + "/" + first;
        }
    }

    private static KOMEEmergencyDefenseCommitment emergency(KOMEConflictRecord conflict,
            KOMEEmergencyDefenseCommitment.State state,
            KOMEEmergencyDefenseCommitment.Disposition disposition) {
        Map<String, KOMEEmergencyDefenseCommitment.Defender> defenders =
            new LinkedHashMap<String, KOMEEmergencyDefenseCommitment.Defender>();
        boolean refunded = disposition == KOMEEmergencyDefenseCommitment.Disposition.SURVIVED_REFUNDED
            || disposition == KOMEEmergencyDefenseCommitment.Disposition.FAILED_REFUNDED;
        KOMEEmergencyDefenseCommitment.Defender defender =
            new KOMEEmergencyDefenseCommitment.Defender("ED-" + conflict.getConflictId()
                + "-1", new UUID(9L, 9L), 1, 1L, disposition, refunded, "test");
        defenders.put(defender.intentId, defender);
        return new KOMEEmergencyDefenseCommitment(conflict.getConflictId(),
            conflict.getTileId(), "gondor", 1L, 1L, 100L, "test", 1, 1,
            100L, 1L, state, defenders, "test");
    }

    private static ExpectedConflict expected(KOMEConflictRecord record) {
        return ExpectedConflict.at(record.getConflictId(), record.getRevision());
    }

    private static Context context(long time) {
        return new Context(time, "test", "ordinary battle planning test");
    }

    private static KOMEConflictRecord ok(KOMEConflictService.Result result) {
        assertTrue(result.reason, result.isSuccess()); return result.record;
    }
}
