package kome.common.data;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static kome.common.data.KOMEConflictContracts.*;
import static kome.common.data.KOMEConflictRecord.*;
import static org.junit.Assert.*;

/** Pure, caller-clocked foundation tests: no world, diplomacy singleton or entity lifecycle. */
public class KOMEConflictServiceTest {
    private static final UUID PLAYER = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID MEMBER = UUID.fromString("20000000-0000-0000-0000-000000000001");

    @Test public void normalizedTileHasOnlyOneActiveConflictAndRejectedStartDoesNotAllocate() {
        KOMEConflictService service = new KOMEConflictService();
        KOMEConflictRecord first = start(service, " t100 ", State.ORDINARY, 10);
        assertEquals("T100", first.getTileId());
        assertSame(first, service.get("t100"));
        assertCode(Code.ACTIVE_CONFLICT_EXISTS, service.start("T100", State.ENCIRCLEMENT,
            ExpectedConflict.absent(), Collections.<GarrisonSeed>emptyList(), context(20)));
        assertEquals(2L, service.getNextConflictSequence());
        assertEquals(1, service.records().size());
    }

    @Test public void bothKindsEndAndRestartWithFreshNeverReusedIdentity() {
        KOMEConflictService service = new KOMEConflictService();
        KOMEConflictRecord first = start(service, "T100", State.ORDINARY, 10);
        KOMEConflictRecord ended = ok(service.end("T100", expected(first), context(20)));
        assertEquals(State.ENDED, ended.getState());
        assertEquals(Long.valueOf(20), ended.getEndedAtMillis());
        KOMEConflictRecord second = ok(service.start("T100", State.ENCIRCLEMENT, expected(ended),
            Collections.<GarrisonSeed>emptyList(), context(30)));
        assertEquals("CF1", first.getConflictId());
        assertEquals("CF2", second.getConflictId());
        assertEquals(1L, second.getRevision());
        ended = ok(service.end("T100", expected(second), context(40)));
        KOMEConflictRecord third = ok(service.start("T100", State.ORDINARY, expected(ended),
            Collections.<GarrisonSeed>emptyList(), context(50)));
        assertEquals("CF3", third.getConflictId());
        assertEquals("CF4", start(service, "T101", State.ENCIRCLEMENT, 60).getConflictId());
    }

    @Test public void staleIdentityAndRevisionFailClosed() {
        KOMEConflictService service = new KOMEConflictService();
        KOMEConflictRecord first = start(service, "T100", State.ORDINARY, 10);
        KOMEConflictRecord changed = ok(service.beginFactionParticipation("T100", expected(first), "gondor", context(20)));
        assertCode(Code.STALE_REVISION, service.end("T100", expected(first), context(30)));
        KOMEConflictRecord ended = ok(service.end("T100", expected(changed), context(30)));
        KOMEConflictRecord fresh = ok(service.start("T100", State.ORDINARY, expected(ended),
            Collections.<GarrisonSeed>emptyList(), context(40)));
        assertCode(Code.STALE_CONFLICT_ID, service.end("T100", expected(ended), context(50)));
        assertSame(fresh, service.get("T100"));
    }

    @Test public void invalidUmbrellaTransitionsDoNotChangeStateOrRevision() {
        KOMEConflictService service = new KOMEConflictService();
        assertCode(Code.INVALID_TRANSITION, service.start("T100", State.ENDED, ExpectedConflict.absent(),
            Collections.<GarrisonSeed>emptyList(), context(10)));
        assertEquals(1L, service.getNextConflictSequence());
        KOMEConflictRecord first = start(service, "T100", State.ORDINARY, 10);
        assertCode(Code.ACTIVE_CONFLICT_EXISTS, service.start("T100", State.ENCIRCLEMENT, expected(first),
            Collections.<GarrisonSeed>emptyList(), context(20)));
        KOMEConflictRecord ended = ok(service.end("T100", expected(first), context(20)));
        assertCode(Code.CONFLICT_ENDED, service.beginFactionParticipation("T100", expected(ended), "gondor", context(30)));
        assertCode(Code.CONFLICT_ENDED, service.end("T100", expected(ended), context(30)));
        assertSame(ended, service.get("T100"));
    }

    @Test public void revisionsAndTransitionMetadataAreDeterministic() {
        KOMEConflictService service = new KOMEConflictService();
        KOMEConflictRecord record = start(service, "T100", State.ORDINARY, 10);
        assertEquals(1L, record.getRevision());
        assertEquals(Operation.CREATE, record.getLastTransition().operation);
        assertNull(record.getLastTransition().fromState);
        record = ok(service.commit("T100", expected(record), arrival("C1", EntryOrigin.LEGAL_ARRIVAL), context(20)));
        assertEquals(2L, record.getRevision());
        assertEquals(Operation.COMMIT, record.getLastTransition().operation);
        assertEquals("C1", record.getLastTransition().subject);
        assertEquals("test", record.getLastTransition().actor);
        assertEquals("validated foundation operation", record.getLastTransition().reason);
        assertCode(Code.TIME_REGRESSION, service.end("T100", expected(record), context(19)));
        assertEquals(2L, service.get("T100").getRevision());
    }

    @Test public void campaignCommitmentIsUniqueAndCannotBeCopiedAcrossActiveTiles() {
        KOMEConflictService service = new KOMEConflictService();
        KOMEConflictRecord first = start(service, "T100", State.ORDINARY, 10);
        first = ok(service.commit("T100", expected(first), arrival("C1", EntryOrigin.LEGAL_ARRIVAL), context(20)));
        Commitment commitment = first.getCommitments().get("C1");
        assertEquals(20L, commitment.acceptedAtMillis);
        assertEquals("M1", commitment.movementOrderId);
        assertCode(Code.DUPLICATE_DETACHMENT, service.commit("T100", expected(first),
            arrival("C1", EntryOrigin.LEGAL_ARRIVAL), context(30)));
        KOMEConflictRecord second = start(service, "T101", State.ORDINARY, 30);
        assertCode(Code.DETACHMENT_ALREADY_COMMITTED, service.commit("T101", expected(second),
            arrival("C1", EntryOrigin.LEGAL_ARRIVAL), context(40)));
        ok(service.end("T100", expected(first), context(40)));
        assertTrue(service.commit("T101", expected(second), arrival("C1", EntryOrigin.LEGAL_ARRIVAL), context(50)).isSuccess());
    }

    @Test public void ordinaryClassificationIsRejectedWithoutRevisionChange() {
        KOMEConflictService service = new KOMEConflictService();
        KOMEConflictRecord record = start(service, "T100", State.ORDINARY, 10);
        assertCode(Code.ORDINARY_NOT_ELIGIBLE, service.commit("T100", expected(record),
            new CommitmentInput("C1", KOMEHiredUnitClass.ORDINARY, EntryOrigin.LEGAL_ARRIVAL, "M1"), context(20)));
        assertSame(record, service.get("T100"));
    }

    @Test public void factionContinuityIsExplicitAndLateParticipationHasNoSideSelection() {
        KOMEConflictService service = new KOMEConflictService();
        KOMEConflictRecord record = start(service, "T100", State.ORDINARY, 10);
        assertTrue(record.getFactionParticipation().isEmpty()); // Presence alone is not registration.
        record = ok(service.beginFactionParticipation("T100", expected(record), " GONDOR ", context(20)));
        record = ok(service.beginFactionParticipation("T100", expected(record), "rohan", context(30)));
        record = ok(service.commit("T100", expected(record), arrival("C1", EntryOrigin.LEGAL_ARRIVAL), context(40)));
        assertEquals(20L, record.getFactionParticipation().get("gondor").startedAtMillis);
        assertEquals(30L, record.getFactionParticipation().get("rohan").startedAtMillis);
        assertCode(Code.FACTION_ALREADY_ACTIVE, service.beginFactionParticipation("T100", expected(record), "gondor", context(50)));
        record = ok(service.endFactionParticipation("T100", expected(record), "gondor", context(50)));
        assertFalse(record.getFactionParticipation().get("gondor").isActive());
        record = ok(service.beginFactionParticipation("T100", expected(record), "gondor", context(60)));
        assertEquals(2L, record.getFactionParticipation().get("gondor").continuitySequence);
        assertEquals(60L, record.getFactionParticipation().get("gondor").startedAtMillis);
        assertEquals(30L, record.getFactionParticipation().get("rohan").startedAtMillis);
    }

    @Test public void playerRegistrationAndWithdrawalAreDataNotRetreatOrDetachmentMutation() {
        KOMEConflictService service = new KOMEConflictService();
        KOMEConflictRecord record = start(service, "T100", State.ORDINARY, 10);
        record = ok(service.registerPlayer("T100", expected(record), PLAYER, "Gondor", context(20)));
        assertEquals(PlayerStatus.ACTIVE, record.getPlayers().get(PLAYER).status);
        assertNull(record.getPlayers().get(PLAYER).withdrawnAtMillis);
        record = ok(service.withdrawPlayer("T100", expected(record), PLAYER, context(30)));
        assertEquals(PlayerStatus.WITHDRAWN, record.getPlayers().get(PLAYER).status);
        assertEquals("gondor", record.getPlayers().get(PLAYER).factionAtRegistration);
        assertEquals(Long.valueOf(30), record.getPlayers().get(PLAYER).withdrawnAtMillis);
        assertEquals(Collections.singleton(PLAYER), record.getWithdrawnPlayers());
        assertTrue(record.getCommitments().isEmpty());
        assertCode(Code.PLAYER_ALREADY_REGISTERED, service.registerPlayer("T100", expected(record), PLAYER, "rohan", context(40)));
    }

    @Test public void originalGarrisonIsCapturedOnlyAtCreationAndReliefCannotEnlargeIt() {
        KOMEConflictService service = new KOMEConflictService();
        KOMEConflictRecord record = ok(service.start("T100", State.ENCIRCLEMENT, ExpectedConflict.absent(),
            Collections.singletonList(new GarrisonSeed("C1", KOMEHiredUnitClass.CAMPAIGN, Collections.singleton(MEMBER))), context(10)));
        record = ok(service.commit("T100", expected(record), arrival("C2", EntryOrigin.RELIEF), context(20)));
        assertEquals(Collections.singleton("C1"), record.getOriginalGarrison().keySet());
        assertEquals(Collections.singleton(MEMBER), record.getOriginalGarrison().get("C1").members.keySet());
        assertEquals(EntryOrigin.ORIGINAL_GARRISON, record.getCommitments().get("C1").origin);
        assertEquals(EntryOrigin.RELIEF, record.getCommitments().get("C2").origin);
        assertCode(Code.INVALID_ORIGIN, service.commit("T100", expected(record), arrival("C3", EntryOrigin.ORIGINAL_GARRISON), context(30)));
        assertEquals(State.ENCIRCLEMENT, record.getState());
    }

    @Test public void zeroGarrisonDoesNotEndEncirclementAndMissingMembersAreNotDeaths() {
        KOMEConflictService service = new KOMEConflictService();
        KOMEConflictRecord empty = start(service, "T100", State.ENCIRCLEMENT, 10);
        assertTrue(empty.getOriginalGarrison().isEmpty());
        assertEquals(State.ENCIRCLEMENT, empty.getState());
        GarrisonCohort unknown = new GarrisonCohort("C1", Collections.singletonMap(MEMBER, GarrisonMemberState.UNKNOWN));
        assertEquals(1, unknown.unconfirmedTerminalMembers());
        GarrisonCohort terminal = new GarrisonCohort("C1", Collections.singletonMap(MEMBER, GarrisonMemberState.CONFIRMED_TERMINAL));
        assertEquals(0, terminal.unconfirmedTerminalMembers());
        assertEquals(State.ENCIRCLEMENT, service.get("T100").getState());
    }

    @Test public void invalidGarrisonSeedsFailAtomicallyAndDoNotConsumeIdentity() {
        KOMEConflictService service = new KOMEConflictService();
        GarrisonSeed seed = new GarrisonSeed("C1", KOMEHiredUnitClass.CAMPAIGN, Collections.singleton(MEMBER));
        assertCode(Code.DUPLICATE_DETACHMENT, service.start("T100", State.ENCIRCLEMENT,
            ExpectedConflict.absent(), Arrays.asList(seed, seed), context(10)));
        assertCode(Code.DUPLICATE_GARRISON_MEMBER, service.start("T100", State.ENCIRCLEMENT,
            ExpectedConflict.absent(), Arrays.asList(seed, new GarrisonSeed("C2", KOMEHiredUnitClass.CAMPAIGN,
                Collections.singleton(MEMBER))), context(10)));
        assertCode(Code.ORDINARY_NOT_ELIGIBLE, service.start("T100", State.ENCIRCLEMENT,
            ExpectedConflict.absent(), Collections.singletonList(new GarrisonSeed("C1", KOMEHiredUnitClass.ORDINARY,
                Collections.singleton(MEMBER))), context(10)));
        assertCode(Code.INVALID_TRANSITION, service.start("T100", State.ORDINARY,
            ExpectedConflict.absent(), Collections.singletonList(seed), context(10)));
        assertEquals(1L, service.getNextConflictSequence());
        assertTrue(service.records().isEmpty());
    }

    @Test public void multipleComplexesHaveIndependentNestedStateAndCatalogReadiness() {
        KOMEConflictService service = new KOMEConflictService();
        KOMEConflictRecord record = start(service, "T100", State.ENCIRCLEMENT, 10);
        assertEquals(CatalogAvailability.UNAVAILABLE, record.getComplexCatalog().availability);
        record = ok(service.bindComplexCatalog("T100", expected(record),
            new ComplexCatalog(CatalogAvailability.AVAILABLE, Arrays.asList("castle", "citadel")), context(20)));
        ProgressCheckpoint progress = new ProgressCheckpoint(Collections.singleton("segment-1"));
        record = ok(service.checkpointComplex("T100", expected(record), new ComplexSubstate("castle",
            ComplexState.ASSAULT_ACTIVE, "assault-1", progress, new LeadAuthority("gondor", PLAYER)), context(30)));
        record = ok(service.checkpointComplex("T100", expected(record), new ComplexSubstate("citadel",
            ComplexState.ASSAULT_ACTIVE, "assault-2", ProgressCheckpoint.empty(), null), context(40)));
        record = ok(service.checkpointComplex("T100", expected(record), new ComplexSubstate("castle",
            ComplexState.CAPTURED, "", progress, null), context(50)));
        assertTrue(record.getComplexes().get("castle").isCaptured());
        assertEquals(Collections.singleton("segment-1"), record.getComplexes().get("castle").progress.securedSegmentIds);
        assertEquals(ComplexState.ASSAULT_ACTIVE, record.getComplexes().get("citadel").state);
        assertTrue(record.getComplexes().get("citadel").progress.securedSegmentIds.isEmpty());
        assertEquals(State.ENCIRCLEMENT, record.getState());
        assertEquals(1, service.records().size());
    }

    @Test public void emptyAvailableCatalogIsNotUnavailableAndDuplicatesAreRejected() {
        KOMEConflictService service = new KOMEConflictService();
        KOMEConflictRecord record = start(service, "T100", State.ENCIRCLEMENT, 10);
        record = ok(service.registerComplex("T100", expected(record), "castle", context(20)));
        assertCode(Code.DUPLICATE_COMPLEX, service.registerComplex("T100", expected(record), "castle", context(30)));
        assertCode(Code.AMBIGUOUS_REFERENCE, service.bindComplexCatalog("T100", expected(record),
            new ComplexCatalog(CatalogAvailability.AVAILABLE, Collections.<String>emptyList()), context(30)));
        KOMEConflictRecord other = start(service, "T101", State.ENCIRCLEMENT, 40);
        other = ok(service.bindComplexCatalog("T101", expected(other),
            new ComplexCatalog(CatalogAvailability.AVAILABLE, Collections.<String>emptyList()), context(50)));
        assertEquals(CatalogAvailability.AVAILABLE, other.getComplexCatalog().availability);
        assertTrue(other.getComplexes().isEmpty());
    }

    @Test public void arrivalsDoNotResetDataOnlyEpisodeOrTimers() {
        KOMEConflictService service = new KOMEConflictService();
        KOMEConflictRecord record = start(service, "T100", State.ORDINARY, 10);
        CombatEpisode episode = new CombatEpisode(1, "CF1:E1", EpisodeState.ACTIVE, 20L, null);
        TimerSlot timer = new TimerSlot(TimerStatus.RUNNING, 1000, 25, 30L, "CF1:E1");
        record = ok(service.checkpointCombat("T100", expected(record), episode, timer, TimerSlot.unstarted(), context(30)));
        record = ok(service.commit("T100", expected(record), arrival("C1", EntryOrigin.EXTERIOR_ARRIVAL), context(100)));
        assertSame(episode, record.getCombatEpisode());
        assertSame(timer, record.getResponseTimer());
        assertEquals(25L, record.getResponseTimer().elapsedMillis); // No ticking, even with a later caller clock.
        assertCode(Code.INVALID_REFERENCE, service.checkpointCombat("T100", expected(record), episode,
            new TimerSlot(TimerStatus.RUNNING, 1000, 25, 100L, "CF99:E1"), TimerSlot.unstarted(), context(100)));
    }

    @Test public void progressSurvivesSeparateAssaultCheckpointsAndReliefArrival() {
        KOMEConflictService service = new KOMEConflictService();
        KOMEConflictRecord record = start(service, "T100", State.ENCIRCLEMENT, 10);
        record = ok(service.registerComplex("T100", expected(record), "castle", context(20)));
        ProgressCheckpoint progress = new ProgressCheckpoint(Collections.singleton("segment-1"));
        record = ok(service.checkpointComplex("T100", expected(record), new ComplexSubstate("castle",
            ComplexState.ASSAULT_ACTIVE, "assault-1", progress, null), context(30)));
        record = ok(service.checkpointComplex("T100", expected(record), new ComplexSubstate("castle",
            ComplexState.UNTAKEN, "", progress, null), context(40)));
        record = ok(service.commit("T100", expected(record), arrival("C1", EntryOrigin.RELIEF), context(50)));
        record = ok(service.checkpointComplex("T100", expected(record), new ComplexSubstate("castle",
            ComplexState.ASSAULT_ACTIVE, "assault-2", record.getComplexes().get("castle").progress, null), context(60)));
        assertEquals(Collections.singleton("segment-1"), record.getComplexes().get("castle").progress.securedSegmentIds);
        assertEquals(State.ENCIRCLEMENT, record.getState());
    }

    @Test public void sameEpisodeCheckpointCannotSilentlyRestartItsTimer() {
        KOMEConflictService service = new KOMEConflictService();
        KOMEConflictRecord record = start(service, "T100", State.ORDINARY, 10);
        CombatEpisode episode = new CombatEpisode(1, "CF1:E1", EpisodeState.ACTIVE, 20L, null);
        record = ok(service.checkpointCombat("T100", expected(record), episode,
            new TimerSlot(TimerStatus.RUNNING, 1000, 100, 30L, "CF1:E1"), TimerSlot.unstarted(), context(30)));
        assertCode(Code.INVALID_EPISODE, service.checkpointCombat("T100", expected(record), episode,
            TimerSlot.unstarted(), TimerSlot.unstarted(), context(40)));
        assertCode(Code.INVALID_EPISODE, service.checkpointCombat("T100", expected(record), episode,
            new TimerSlot(TimerStatus.RUNNING, 1000, 0, 40L, "CF1:E1"), TimerSlot.unstarted(), context(40)));
        assertEquals(100L, service.get("T100").getResponseTimer().elapsedMillis);
    }

    @Test public void endingPreservesFinalSnapshotAndFreshRecordInheritsNothing() {
        KOMEConflictService service = new KOMEConflictService();
        KOMEConflictRecord record = start(service, "T100", State.ENCIRCLEMENT, 10);
        record = ok(service.registerComplex("T100", expected(record), "castle", context(20)));
        record = ok(service.registerComplex("T100", expected(record), "citadel", context(25)));
        record = ok(service.checkpointComplex("T100", expected(record), new ComplexSubstate("castle",
            ComplexState.CAPTURED, "", new ProgressCheckpoint(Collections.singleton("segment-1")), null), context(30)));
        LeadAuthority lead = new LeadAuthority("rohan", PLAYER);
        record = ok(service.checkpointComplex("T100", expected(record), new ComplexSubstate("citadel",
            ComplexState.ASSAULT_ACTIVE, "assault-2", new ProgressCheckpoint(Collections.singleton("segment-2")), lead), context(40)));
        CombatEpisode episode = new CombatEpisode(1, "CF1:E1", EpisodeState.ACTIVE, 45L, null);
        TimerSlot response = new TimerSlot(TimerStatus.RUNNING, 1000, 125, 50L, "CF1:E1");
        TimerSlot capture = new TimerSlot(TimerStatus.PAUSED, 2000, 300, 50L, "CF1:E1");
        record = ok(service.checkpointCombat("T100", expected(record), episode, response, capture, context(50)));
        record = ok(service.checkpointEncirclement("T100", expected(record), 12, context(60)));
        KOMEConflictRecord activeSnapshot = record;
        KOMEConflictRecord ended = ok(service.end("T100", expected(record), context(70)));
        assertEquals(State.ENDED, ended.getState());
        assertEquals(Long.valueOf(70), ended.getEndedAtMillis());
        assertEquals(activeSnapshot.getRevision() + 1L, ended.getRevision());
        assertSame(response, ended.getResponseTimer());
        assertSame(capture, ended.getCaptureTimer());
        assertSame(episode, ended.getCombatEpisode());
        assertSame(activeSnapshot.getComplexes().get("castle"), ended.getComplexes().get("castle"));
        assertSame(activeSnapshot.getComplexes().get("citadel"), ended.getComplexes().get("citadel"));
        assertTrue(ended.getComplexes().get("castle").isCaptured());
        assertEquals(Collections.singleton("segment-1"), ended.getComplexes().get("castle").progress.securedSegmentIds);
        assertEquals(ComplexState.ASSAULT_ACTIVE, ended.getComplexes().get("citadel").state);
        assertEquals("assault-2", ended.getComplexes().get("citadel").activeAssaultId);
        assertSame(lead, ended.getComplexes().get("citadel").lead);
        assertEquals(Collections.singleton("segment-2"), ended.getComplexes().get("citadel").progress.securedSegmentIds);
        assertEquals(12L, ended.getEncirclement().liveElapsedMillis);
        assertEquals(Long.valueOf(70), ended.getEncirclement().endedAtMillis);

        assertCode(Code.CONFLICT_ENDED, service.checkpointComplex("T100", expected(ended),
            ComplexSubstate.unstarted("castle"), context(80)));
        assertSame(ended, service.get("T100"));

        ExpectedConflict endedExpectation = expected(ended);
        record = ok(service.start("T100", State.ENCIRCLEMENT, endedExpectation,
            Collections.<GarrisonSeed>emptyList(), context(90)));
        assertEquals("CF2", record.getConflictId());
        assertEquals(1L, record.getRevision());
        assertTrue(record.getCommitments().isEmpty());
        assertTrue(record.getPlayers().isEmpty());
        assertTrue(record.getFactionParticipation().isEmpty());
        assertTrue(record.getComplexes().isEmpty());
        assertTrue(record.getOriginalGarrison().isEmpty());
        assertEquals(CatalogAvailability.UNAVAILABLE, record.getComplexCatalog().availability);
        assertEquals(0L, record.getEncirclement().liveElapsedMillis);
        assertEquals(TimerStatus.NOT_STARTED, record.getResponseTimer().status);
        assertEquals(TimerStatus.NOT_STARTED, record.getCaptureTimer().status);
        assertEquals(0L, record.getCombatEpisode().sequence);
        assertEquals(EpisodeState.INACTIVE, record.getCombatEpisode().state);

        assertCode(Code.STALE_CONFLICT_ID, service.end("T100", endedExpectation, context(100)));
        assertSame(record, service.get("T100"));
    }

    @Test public void missingOrAmbiguousReferencesRetainCommitmentsWithoutInference() {
        KOMEConflictService service = new KOMEConflictService();
        KOMEConflictRecord record = start(service, "T100", State.ORDINARY, 10);
        record = ok(service.commit("T100", expected(record), arrival("C1", EntryOrigin.LEGAL_ARRIVAL), context(20)));
        record = ok(service.registerPlayer("T100", expected(record), PLAYER, "gondor", context(30)));
        for (ReferenceStatus status : new ReferenceStatus[] {ReferenceStatus.UNKNOWN, ReferenceStatus.MISSING,
                ReferenceStatus.INCOHERENT, ReferenceStatus.AMBIGUOUS}) {
            record = ok(service.recordDiagnostic("T100", expected(record),
                new ReferenceDiagnostic(ReferenceKind.DETACHMENT, "C1", status, "reference needs reconciliation"), context(40)));
            assertTrue(record.getCommitments().containsKey("C1"));
            assertEquals(PlayerStatus.ACTIVE, record.getPlayers().get(PLAYER).status);
            assertEquals(State.ORDINARY, record.getState());
            assertEquals(status, record.getDiagnostics().values().iterator().next().status);
        }
        assertEquals(Hostility.UNKNOWN, new HostilityResolver() {
            @Override public Hostility resolve(String first, String second) { return Hostility.UNKNOWN; }
        }.resolve("missing-faction", "gondor"));
    }

    static Context context(long now) { return new Context(now, "test", "validated foundation operation"); }
    static ExpectedConflict expected(KOMEConflictRecord record) {
        return ExpectedConflict.at(record.getConflictId(), record.getRevision());
    }
    static CommitmentInput arrival(String id, EntryOrigin origin) {
        return new CommitmentInput(id, KOMEHiredUnitClass.CAMPAIGN, origin, "M1");
    }
    static KOMEConflictRecord start(KOMEConflictService service, String tile, State state, long now) {
        return ok(service.start(tile, state, ExpectedConflict.absent(), Collections.<GarrisonSeed>emptyList(), context(now)));
    }
    static KOMEConflictRecord ok(KOMEConflictService.Result result) {
        assertTrue(result.code + ": " + result.reason, result.isSuccess()); return result.record;
    }
    static void assertCode(Code code, KOMEConflictService.Result result) { assertEquals(result.reason, code, result.code); }
}
