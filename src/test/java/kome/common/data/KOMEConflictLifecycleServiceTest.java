package kome.common.data;

import lotr.common.fac.LOTRFactionRelations;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import kome.common.data.KOMEConflictService.Result;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static kome.common.data.KOMEConflictContracts.*;
import static kome.common.data.KOMEConflictRecord.*;
import static org.junit.Assert.*;

/** Deterministic Phase 3 orchestration tests; no movement or entity-arrival hook is installed. */
public class KOMEConflictLifecycleServiceTest {
    @Rule public final KOMETileTestResources geometry = new KOMETileTestResources();

    private static final UUID MEMBER_ONE =
        UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID MEMBER_TWO =
        UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID PLAYER =
        UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Before @After public void resetRelations() {
        setRelation("gondor", "mordor", LOTRFactionRelations.Relation.NEUTRAL);
        setRelation("rohan", "mordor", LOTRFactionRelations.Relation.NEUTRAL);
        setRelation("gondor", "rohan", LOTRFactionRelations.Relation.NEUTRAL);
    }

    @Test public void liveHostilityUsesCurrentLotrRelationsAndInvalidIsUnknown() {
        KOMEWorldData data = new KOMEWorldData("hostility");
        KOMEConflictService service = data.getConflictService();
        setRelation("gondor", "mordor", LOTRFactionRelations.Relation.ENEMY);
        assertEquals(Hostility.HOSTILE,
            service.currentHostility(data, "gondor", "mordor"));
        setRelation("gondor", "mordor", LOTRFactionRelations.Relation.MORTAL_ENEMY);
        assertEquals(Hostility.HOSTILE,
            service.currentHostility(data, "gondor", "mordor"));
        for (LOTRFactionRelations.Relation relation : Arrays.asList(
                LOTRFactionRelations.Relation.NEUTRAL,
                LOTRFactionRelations.Relation.FRIEND,
                LOTRFactionRelations.Relation.ALLY)) {
            setRelation("gondor", "mordor", relation);
            assertEquals(Hostility.NON_HOSTILE,
                service.currentHostility(data, "gondor", "mordor"));
        }
        assertEquals(Hostility.UNKNOWN,
            service.currentHostility(data, "not_a_lotr_faction", "mordor"));
        assertEquals(Hostility.NON_HOSTILE,
            service.currentHostility(data, "gondor", "gondor"));
    }

    @Test public void komeWarDoesNotOverrideLiveLotrNeutrality() {
        KOMEWorldData data = new KOMEWorldData("war-is-not-hostility");
        KOMEWar war = new KOMEWar();
        war.id = "W1";
        war.sideOneFactions.add("gondor");
        war.sideTwoFactions.add("mordor");
        data.wars.put(war.id, war);
        setRelation("gondor", "mordor", LOTRFactionRelations.Relation.NEUTRAL);
        assertEquals(Hostility.NON_HOSTILE,
            data.getConflictService().currentHostility(data, "gondor", "mordor"));
    }

    @Test public void hostileValidatedArrivalCreatesOrdinaryAndNoPhysicalApiCanDoSo() {
        Fixture f = new Fixture();
        assertNull(f.service.get("T100"));
        Result result = f.accept(request("T100", "C1", "gondor", "mordor",
            EntryOrigin.LEGAL_ARRIVAL, false, ExpectedConflict.absent(), 10));
        assertCode(Code.SUCCESS, result);
        assertEquals(State.ORDINARY, result.record.getState());
        assertEquals(Collections.singleton("C1"), result.record.getCommitments().keySet());
        assertTrue(result.record.getFactionParticipation().get("gondor").isActive());
        assertTrue(result.record.getFactionParticipation().get("mordor").isActive());
        assertEquals(1L, result.record.getRevision());
        assertEquals(Operation.CREATE, result.record.getLastTransition().operation);
    }

    @Test public void defensiveArrivalCreatesEncirclementEvenWithZeroGarrison() {
        Fixture f = new Fixture();
        Result result = f.accept(request("T100", "C1", "gondor", "mordor",
            EntryOrigin.LEGAL_ARRIVAL, true, ExpectedConflict.absent(), 10));
        assertCode(Code.SUCCESS, result);
        assertEquals(State.ENCIRCLEMENT, result.record.getState());
        assertTrue(result.record.getOriginalGarrison().isEmpty());
        assertNotNull(result.record.getEncirclement());
    }

    @Test public void newConflictRequiresExplicitValidatedDefenderAuthority() {
        Fixture f = new Fixture();
        ValidatedCommitmentRequest invalid = request("T100", "C1", "gondor", "mordor",
            EntryOrigin.LEGAL_ARRIVAL, false, ExpectedConflict.absent(), 10,
            ValidatedConflictAuthority.participant("mordor"));
        assertCode(Code.INVALID_REFERENCE, f.accept(invalid));
        assertNull(f.service.get("T100"));
        KOMEConflictRecord record = ok(f.accept(request("T100", "C1", "gondor", "mordor",
            EntryOrigin.LEGAL_ARRIVAL, false, ExpectedConflict.absent(), 20,
            ValidatedConflictAuthority.defender("mordor"))));
        assertTrue(record.getFactionParticipation().containsKey("mordor"));
        assertEquals(ConflictAuthorityKind.VALIDATED_DEFENDER,
            record.getCommitments().get("C1").validatedEvent.authorityKind);
    }

    @Test public void nonHostileUnknownAndNeutralThirdPartyDoNotCommit() {
        Fixture f = new Fixture();
        f.hostility.value = Hostility.NON_HOSTILE;
        assertCode(Code.NON_HOSTILE, f.accept(request("T100", "C1", "gondor", "mordor",
            EntryOrigin.LEGAL_ARRIVAL, false, ExpectedConflict.absent(), 10)));
        assertNull(f.service.get("T100"));
        f.hostility.value = Hostility.UNKNOWN;
        assertCode(Code.UNKNOWN_HOSTILITY, f.accept(request("T100", "C1", "gondor", "mordor",
            EntryOrigin.LEGAL_ARRIVAL, false, ExpectedConflict.absent(), 20)));
        assertNull(f.service.get("T100"));
    }

    @Test public void additionalArrivalJoinsOneRecordWithoutRestartingEpisodeOrTimer() {
        Fixture f = new Fixture();
        KOMEConflictRecord record = ok(f.accept(request("T100", "C1", "gondor", "mordor",
            EntryOrigin.LEGAL_ARRIVAL, false, ExpectedConflict.absent(), 10)));
        CombatEpisode episode = new CombatEpisode(1, "CF1:E1", EpisodeState.ACTIVE, 20L, null);
        TimerSlot timer = new TimerSlot(TimerStatus.RUNNING, 1000, 25, 30L, "CF1:E1");
        record = ok(f.service.checkpointCombat("T100", expected(record), episode,
            timer, TimerSlot.unstarted(), context(30)));
        f.resolver.put(resolved("C2", "rohan", "T100"));
        record = ok(f.accept(request("T100", "C2", "rohan", "mordor",
            EntryOrigin.EXTERIOR_ARRIVAL, false, expected(record), 40)));
        assertEquals(1, f.service.records().size());
        assertEquals(2, record.getCommitments().size());
        assertSame(episode, record.getCombatEpisode());
        assertSame(timer, record.getResponseTimer());
        assertEquals(25L, record.getResponseTimer().elapsedMillis);
    }

    @Test public void repeatedValidatedEventIsIdempotentWithoutRevisionOrHistoryDuplication() {
        Fixture f = new Fixture();
        ValidatedCommitmentRequest event = request("T100", "C1", "gondor", "mordor",
            EntryOrigin.LEGAL_ARRIVAL, false, ExpectedConflict.absent(), 10);
        KOMEConflictRecord first = ok(f.accept(event));
        ValidatedCommitmentRequest retry = request("T100", "C1", "gondor", "mordor",
            EntryOrigin.LEGAL_ARRIVAL, false,
            ExpectedConflict.at(first.getConflictId(), 1L), 10,
            ValidatedConflictAuthority.defender("mordor"));
        Result duplicate = f.accept(retry);
        assertCode(Code.ALREADY_COMMITTED_SAME_CONFLICT, duplicate);
        assertSame(first, duplicate.record);
        assertEquals(1L, duplicate.record.getRevision());
        assertEquals(1, duplicate.record.getCommitments().size());
        assertEquals(1L,
            duplicate.record.getFactionParticipation().get("gondor").continuitySequence);
        ValidatedCommitmentRequest changedTimestamp = request("T100", "C1", "gondor",
            "mordor", EntryOrigin.LEGAL_ARRIVAL, false, expected(first), 11,
            ValidatedConflictAuthority.defender("mordor"));
        assertCode(Code.DUPLICATE_DETACHMENT, f.accept(changedTimestamp));
        ValidatedCommitmentRequest changedAuthority = request("T100", "C1", "gondor",
            "rohan", EntryOrigin.LEGAL_ARRIVAL, false, expected(first), 10,
            ValidatedConflictAuthority.defender("rohan"));
        assertCode(Code.DUPLICATE_DETACHMENT, f.accept(changedAuthority));
        assertSame(first, f.service.get("T100"));
    }

    @Test public void staleIdentityAndRevisionRejectDifferentCommitmentEvents() {
        Fixture f = new Fixture();
        KOMEConflictRecord first = ok(f.accept(request("T100", "C1", "gondor", "mordor",
            EntryOrigin.LEGAL_ARRIVAL, false, ExpectedConflict.absent(), 10)));
        f.resolver.put(resolved("C2", "rohan", "T100"));
        assertCode(Code.STALE_REVISION, f.accept(request("T100", "C2", "rohan", "mordor",
            EntryOrigin.EXTERIOR_ARRIVAL, false,
            ExpectedConflict.at(first.getConflictId(), first.getRevision() + 1L), 20)));
        assertCode(Code.STALE_CONFLICT_ID, f.accept(request("T100", "C2", "rohan", "mordor",
            EntryOrigin.EXTERIOR_ARRIVAL, false,
            ExpectedConflict.at("CF99", first.getRevision()), 20)));
        assertCode(Code.STALE_REVISION, f.accept(request("T100", "C1", "gondor", "mordor",
            EntryOrigin.LEGAL_ARRIVAL, false,
            ExpectedConflict.at(first.getConflictId(), first.getRevision() + 1L), 10,
            ValidatedConflictAuthority.defender("mordor"))));
        assertSame(first, f.service.get("T100"));
        assertFalse(first.getCommitments().containsKey("C2"));
    }

    @Test public void detachmentAuthorityFailuresAreTypedAndFailClosed() {
        Fixture f = new Fixture();
        f.resolver.put(new DetachmentResolution("C1", ReferenceStatus.RESOLVED,
            KOMEHiredUnitClass.ORDINARY, "gondor", "T100", "ordinary"));
        assertCode(Code.ORDINARY_NOT_ELIGIBLE, f.accept(request("T100", "C1", "gondor",
            "mordor", EntryOrigin.LEGAL_ARRIVAL, false, ExpectedConflict.absent(), 10)));
        f.resolver.put(new DetachmentResolution("C1", ReferenceStatus.UNKNOWN,
            null, "", "", "not loaded"));
        assertCode(Code.DETACHMENT_UNRESOLVED, f.accept(request("T100", "C1", "gondor",
            "mordor", EntryOrigin.LEGAL_ARRIVAL, false, ExpectedConflict.absent(), 20)));
        f.resolver.put(new DetachmentResolution("C1", ReferenceStatus.INCOHERENT,
            null, "", "", "positive contradiction"));
        assertCode(Code.DETACHMENT_INCOHERENT, f.accept(request("T100", "C1", "gondor",
            "mordor", EntryOrigin.LEGAL_ARRIVAL, false, ExpectedConflict.absent(), 30)));
        f.resolver.put(resolved("C1", "rohan", "T100"));
        assertCode(Code.DETACHMENT_FACTION_MISMATCH, f.accept(request("T100", "C1", "gondor",
            "mordor", EntryOrigin.LEGAL_ARRIVAL, false, ExpectedConflict.absent(), 40)));
        f.resolver.put(resolved("C1", "gondor", "T101"));
        assertCode(Code.DETACHMENT_TILE_MISMATCH, f.accept(request("T100", "C1", "gondor",
            "mordor", EntryOrigin.LEGAL_ARRIVAL, false, ExpectedConflict.absent(), 50)));
        assertNull(f.service.get("T100"));
    }

    @Test public void activeDuplicateIsRejectedButEndedHistoryAllowsLaterReuse() {
        Fixture f = new Fixture();
        KOMEConflictRecord first = ok(f.accept(request("T100", "C1", "gondor", "mordor",
            EntryOrigin.LEGAL_ARRIVAL, false, ExpectedConflict.absent(), 10)));
        f.resolver.put(resolved("C1", "gondor", "T101"));
        assertCode(Code.DETACHMENT_ALREADY_COMMITTED, f.accept(request("T101", "C1", "gondor",
            "mordor", EntryOrigin.LEGAL_ARRIVAL, false, ExpectedConflict.absent(), 20)));
        KOMEConflictRecord ended = ok(f.service.end("T100", expected(first), context(30)));
        KOMEConflictRecord later = ok(f.accept(request("T101", "C1", "gondor", "mordor",
            EntryOrigin.LEGAL_ARRIVAL, false, ExpectedConflict.absent(), 40)));
        assertEquals(State.ENDED, ended.getState());
        assertTrue(ended.getCommitments().containsKey("C1"));
        assertEquals("CF2", later.getConflictId());
        assertTrue(later.getCommitments().containsKey("C1"));
    }

    @Test public void factionContinuitySurvivesAdditionalDetachmentsAndExplicitDeparture() {
        Fixture f = new Fixture();
        KOMEConflictRecord record = ok(f.accept(request("T100", "C1", "gondor", "mordor",
            EntryOrigin.LEGAL_ARRIVAL, false, ExpectedConflict.absent(), 10)));
        f.resolver.put(resolved("C2", "gondor", "T100"));
        record = ok(f.accept(request("T100", "C2", "gondor", "mordor",
            EntryOrigin.EXTERIOR_ARRIVAL, false, expected(record), 20)));
        assertEquals(10L, record.getFactionParticipation().get("gondor").startedAtMillis);
        record = ok(f.release(new ValidatedDepartureRequest("T100", "C1", "gondor",
            expected(record)), 30));
        assertTrue(record.getFactionParticipation().get("gondor").isActive());
        assertEquals(State.ORDINARY, record.getState());
        assertNull(record.getEndedAtMillis());
        record = ok(f.release(new ValidatedDepartureRequest("T100", "C2", "gondor",
            expected(record)), 40));
        assertFalse(record.getFactionParticipation().get("gondor").isActive());
        assertEquals(State.ORDINARY, record.getState());
        assertNull(record.getEndedAtMillis());
        f.resolver.put(resolved("C3", "gondor", "T100"));
        record = ok(f.accept(request("T100", "C3", "gondor", "mordor",
            EntryOrigin.EXTERIOR_ARRIVAL, false, expected(record), 50)));
        assertEquals(2L, record.getFactionParticipation().get("gondor").continuitySequence);
        assertEquals(50L, record.getFactionParticipation().get("gondor").startedAtMillis);
    }

    @Test public void unresolvedRemainingCommitmentMakesDepartureFailClosed() {
        Fixture f = new Fixture();
        KOMEConflictRecord record = ok(f.accept(request("T100", "C1", "gondor", "mordor",
            EntryOrigin.LEGAL_ARRIVAL, false, ExpectedConflict.absent(), 10)));
        f.resolver.put(resolved("C2", "gondor", "T100"));
        record = ok(f.accept(request("T100", "C2", "gondor", "mordor",
            EntryOrigin.EXTERIOR_ARRIVAL, false, expected(record), 20)));
        assertCode(Code.DETACHMENT_FACTION_MISMATCH,
            f.release(new ValidatedDepartureRequest("T100", "C1", "mordor",
                expected(record)), 25));
        assertSame(record, f.service.get("T100"));
        f.resolver.remove("C2");
        Result rejected = f.release(new ValidatedDepartureRequest("T100", "C1", "gondor",
            expected(record)), 30);
        assertCode(Code.DETACHMENT_UNRESOLVED, rejected);
        assertSame(record, f.service.get("T100"));
        assertEquals(2, record.getCommitments().size());
    }

    @Test public void diplomacyChangeOnlyChangesLiveQueryAndAllowsLaterAuthorizedEntry() {
        KOMEWorldData data = new KOMEWorldData("dynamic-hostility");
        KOMEConflictService service = data.getConflictService();
        Fixture f = new Fixture(service);
        KOMEConflictRecord record = ok(f.accept(request("T100", "C1", "gondor", "mordor",
            EntryOrigin.LEGAL_ARRIVAL, false, ExpectedConflict.absent(), 10)));
        long gondorStart = record.getFactionParticipation().get("gondor").startedAtMillis;

        setRelation("rohan", "gondor", LOTRFactionRelations.Relation.NEUTRAL);
        assertEquals(Hostility.NON_HOSTILE, service.currentHostility(data, "rohan", "gondor"));
        assertFalse(record.getFactionParticipation().containsKey("rohan"));
        setRelation("rohan", "gondor", LOTRFactionRelations.Relation.ENEMY);
        assertEquals(Hostility.HOSTILE, service.currentHostility(data, "rohan", "gondor"));
        assertSame(record, service.get("T100"));
        assertEquals(gondorStart,
            service.get("T100").getFactionParticipation().get("gondor").startedAtMillis);

        f.resolver.put(resolved("C2", "rohan", "T100"));
        f.hostility.value = Hostility.HOSTILE;
        record = ok(f.accept(request("T100", "C2", "rohan", "gondor",
            EntryOrigin.EXTERIOR_ARRIVAL, false, expected(record), 20)));
        assertTrue(record.getFactionParticipation().get("rohan").isActive());
        assertEquals(gondorStart, record.getFactionParticipation().get("gondor").startedAtMillis);
    }

    @Test public void alliedReliefUsesHostilityToAttackerWithoutFixedSides() {
        KOMEWorldData diplomacy = new KOMEWorldData("allied-relief");
        KOMEConflictService service = new KOMEConflictService();
        Resolver resolver = new Resolver();
        resolver.put(resolved("C1", "gondor", "T100"));
        resolver.put(resolved("C9", "mordor", "T100"));
        resolver.put(resolved("C2", "rohan", "T100"));
        HostilityResolver live = new HostilityResolver() {
            @Override public Hostility resolve(String first, String second) {
                return service.currentHostility(diplomacy, first, second);
            }
        };
        setRelation("gondor", "mordor", LOTRFactionRelations.Relation.ENEMY);
        setRelation("rohan", "mordor", LOTRFactionRelations.Relation.ALLY);
        setRelation("rohan", "gondor", LOTRFactionRelations.Relation.NEUTRAL);
        assertEquals(Hostility.NON_HOSTILE,
            service.currentHostility(diplomacy, "rohan", "mordor"));
        assertEquals(Hostility.NON_HOSTILE,
            service.currentHostility(diplomacy, "rohan", "gondor"));
        GarrisonParticipantSeed defender = new GarrisonParticipantSeed(
            new GarrisonSeed("C9", KOMEHiredUnitClass.CAMPAIGN,
                Collections.singleton(MEMBER_ONE)), "mordor");
        KOMEConflictRecord record = ok(service.acceptValidatedCommitment(
            new ValidatedCommitmentRequest("T100", "C1", "gondor",
                ValidatedConflictAuthority.defender("mordor"), 10,
                EntryOrigin.LEGAL_ARRIVAL, "M1", true,
                Collections.singleton(defender), ExpectedConflict.absent()),
            live, resolver, context(10)));
        assertFalse(record.getCommitments().containsKey("C2"));
        assertFalse(record.getFactionParticipation().containsKey("rohan"));

        setRelation("rohan", "gondor", LOTRFactionRelations.Relation.ENEMY);
        assertEquals(Hostility.HOSTILE,
            service.currentHostility(diplomacy, "rohan", "gondor"));
        assertFalse(service.get("T100").getCommitments().containsKey("C2"));
        record = ok(service.acceptValidatedCommitment(
            new ValidatedCommitmentRequest("T100", "C2", "rohan",
                ValidatedConflictAuthority.participant("gondor"), 20,
                EntryOrigin.RELIEF, "M2", false,
                Collections.<GarrisonParticipantSeed>emptyList(), expected(record)),
            live, resolver, context(20)));
        assertEquals(EntryOrigin.RELIEF, record.getCommitments().get("C2").origin);
        assertEquals(Collections.singleton("C9"), record.getOriginalGarrison().keySet());
        assertTrue(record.getFactionParticipation().containsKey("rohan"));
        assertEquals(State.ENCIRCLEMENT, record.getState());
    }

    @Test public void explicitGarrisonSnapshotIsImmutableAndLaterDefenderIsRelief() {
        Fixture f = new Fixture();
        f.resolver.put(resolved("C9", "mordor", "T100"));
        GarrisonParticipantSeed garrison = new GarrisonParticipantSeed(
            new GarrisonSeed("C9", KOMEHiredUnitClass.CAMPAIGN,
                Collections.singleton(MEMBER_ONE)), "mordor");
        KOMEConflictRecord record = ok(f.accept(request("T100", "C1", "gondor", "mordor",
            EntryOrigin.LEGAL_ARRIVAL, true, Collections.singletonList(garrison),
            ExpectedConflict.absent(), 10)));
        assertEquals(Collections.singleton("C9"), record.getOriginalGarrison().keySet());
        assertEquals(GarrisonMemberState.ORIGINAL,
            record.getOriginalGarrison().get("C9").members.get(MEMBER_ONE));
        f.resolver.put(resolved("C2", "mordor", "T100"));
        record = ok(f.accept(request("T100", "C2", "mordor", "gondor",
            EntryOrigin.RELIEF, false, expected(record), 20)));
        assertEquals(EntryOrigin.RELIEF, record.getCommitments().get("C2").origin);
        assertEquals(Collections.singleton("C9"), record.getOriginalGarrison().keySet());
        assertEquals(State.ENCIRCLEMENT, record.getState());
        assertEquals(1, record.getOriginalGarrison().get("C9").unconfirmedTerminalMembers());
        record = ok(f.release(new ValidatedDepartureRequest("T100", "C9", "mordor",
            expected(record)), 30));
        assertFalse(record.getCommitments().containsKey("C9"));
        assertTrue(record.getCommitments().containsKey("C2"));
        assertEquals(Collections.singleton("C9"), record.getOriginalGarrison().keySet());
        assertEquals(GarrisonMemberState.ORIGINAL,
            record.getOriginalGarrison().get("C9").members.get(MEMBER_ONE));
    }

    @Test public void playerTimerEpisodeAndComplexMutationsRemainExplicitAndStaleSafe() {
        Fixture f = new Fixture();
        KOMEConflictRecord record = ok(f.accept(request("T100", "C1", "gondor", "mordor",
            EntryOrigin.LEGAL_ARRIVAL, true, ExpectedConflict.absent(), 10)));
        KOMEConflictRecord stale = record;
        record = ok(f.service.registerPlayer("T100", expected(record), PLAYER,
            "gondor", context(20)));
        assertCode(Code.STALE_REVISION, f.service.withdrawPlayer("T100",
            expected(stale), PLAYER, context(30)));
        record = ok(f.service.withdrawPlayer("T100", expected(record), PLAYER, context(30)));
        assertEquals(PlayerStatus.WITHDRAWN, record.getPlayers().get(PLAYER).status);

        record = ok(f.service.bindComplexCatalog("T100", expected(record),
            new ComplexCatalog(CatalogAvailability.AVAILABLE,
                Arrays.asList("castle", "citadel")), context(40)));
        record = ok(f.service.checkpointComplex("T100", expected(record),
            new ComplexSubstate("castle", ComplexState.ASSAULT_ACTIVE, "assault-1",
                new ProgressCheckpoint(Collections.singleton("segment-a")),
                new LeadAuthority("gondor", PLAYER)), context(50)));
        record = ok(f.service.checkpointComplex("T100", expected(record),
            new ComplexSubstate("citadel", ComplexState.ASSAULT_ACTIVE, "assault-2",
                ProgressCheckpoint.empty(), null), context(60)));
        assertEquals(ComplexState.ASSAULT_ACTIVE, record.getComplexes().get("castle").state);
        assertEquals(ComplexState.ASSAULT_ACTIVE, record.getComplexes().get("citadel").state);
        assertEquals(State.ENCIRCLEMENT, record.getState());

        CombatEpisode episode = new CombatEpisode(1, "CF1:E1",
            EpisodeState.ACTIVE, 70L, null);
        record = ok(f.service.checkpointCombat("T100", expected(record), episode,
            new TimerSlot(TimerStatus.RUNNING, 1000, 25, 80L, "CF1:E1"),
            TimerSlot.unstarted(), context(80)));
        assertEquals(25L, record.getResponseTimer().elapsedMillis);
    }

    @Test public void endedSnapshotIsTerminalAndPhaseThreeStateRoundTrips() {
        Fixture f = new Fixture();
        KOMEConflictRecord record = ok(f.accept(request("T100", "C1", "gondor", "mordor",
            EntryOrigin.LEGAL_ARRIVAL, true, ExpectedConflict.absent(), 10)));
        record = ok(f.service.registerComplex("T100", expected(record), "castle", context(20)));
        record = ok(f.service.checkpointComplex("T100", expected(record),
            new ComplexSubstate("castle", ComplexState.CAPTURED, "",
                new ProgressCheckpoint(Collections.singleton("checkpoint-a")), null),
            context(30)));
        KOMEConflictRecord ended = ok(f.service.end("T100", expected(record), context(40)));
        assertCode(Code.CONFLICT_ENDED, f.service.registerPlayer("T100", expected(ended),
            PLAYER, "gondor", context(50)));

        KOMEConflictService restored = KOMEConflictPersistence.read(
            KOMEConflictPersistence.write(f.service));
        KOMEConflictRecord snapshot = restored.get("T100");
        assertEquals(State.ENDED, snapshot.getState());
        assertTrue(snapshot.getCommitments().containsKey("C1"));
        assertTrue(snapshot.getComplexes().get("castle").isCaptured());
        assertEquals(Collections.singleton("checkpoint-a"),
            snapshot.getComplexes().get("castle").progress.securedSegmentIds);
        assertEquals(ended.getRevision(), snapshot.getRevision());
    }

    @Test public void garrisonDepartureContinuityReentryAndReplayIdentityRoundTripWorldData() {
        Fixture f = new Fixture();
        f.resolver.put(resolved("C9", "mordor", "T100"));
        f.resolver.put(resolved("C2", "mordor", "T100"));
        GarrisonParticipantSeed garrison = new GarrisonParticipantSeed(
            new GarrisonSeed("C9", KOMEHiredUnitClass.CAMPAIGN,
                Collections.singleton(MEMBER_ONE)), "mordor");
        KOMEConflictRecord record = ok(f.accept(request("T100", "C1", "gondor", "mordor",
            EntryOrigin.LEGAL_ARRIVAL, true, Collections.singleton(garrison),
            ExpectedConflict.absent(), 10)));
        GarrisonParticipantSeed changedGarrison = new GarrisonParticipantSeed(
            new GarrisonSeed("C9", KOMEHiredUnitClass.CAMPAIGN,
                Collections.singleton(MEMBER_TWO)), "mordor");
        assertCode(Code.DUPLICATE_DETACHMENT,
            f.service.acceptValidatedCommitment(new ValidatedCommitmentRequest(
                "T100", "C1", "gondor",
                ValidatedConflictAuthority.defender("mordor"), 10,
                EntryOrigin.LEGAL_ARRIVAL, "M1", true,
                Collections.singleton(changedGarrison), expected(record)),
                f.hostility, f.resolver, context(10)));
        record = ok(f.accept(request("T100", "C2", "mordor", "gondor",
            EntryOrigin.RELIEF, false, expected(record), 20)));
        record = ok(f.release(new ValidatedDepartureRequest("T100", "C9", "mordor",
            expected(record)), 25));
        record = ok(f.release(new ValidatedDepartureRequest("T100", "C2", "mordor",
            expected(record)), 30));
        assertFalse(record.getFactionParticipation().get("mordor").isActive());
        assertEquals(Long.valueOf(30),
            record.getFactionParticipation().get("mordor").endedAtMillis);
        record = ok(f.accept(request("T100", "C2", "mordor", "gondor",
            EntryOrigin.RELIEF, false, expected(record), 40)));
        assertEquals(2L, record.getFactionParticipation().get("mordor").continuitySequence);

        KOMEWorldData world = new KOMEWorldData("phase3-round-trip");
        world.initializeIntegratedWorld();
        world.getConflictService().replaceFrom(f.service);
        NBTTagCompound saved = new NBTTagCompound();
        world.writeToNBT(saved);
        KOMEWorldData loaded = new KOMEWorldData("phase3-loaded");
        loaded.readFromNBT(saved);
        KOMEConflictRecord actual = loaded.getConflictService().get("T100");
        assertEquals(State.ENCIRCLEMENT, actual.getState());
        assertNull(actual.getEndedAtMillis());
        assertTrue(actual.getOriginalGarrison().containsKey("C9"));
        assertEquals(GarrisonMemberState.ORIGINAL,
            actual.getOriginalGarrison().get("C9").members.get(MEMBER_ONE));
        assertFalse(actual.getCommitments().containsKey("C9"));
        assertEquals(EntryOrigin.RELIEF, actual.getCommitments().get("C2").origin);
        assertEquals(2L,
            actual.getFactionParticipation().get("mordor").continuitySequence);
        assertEquals(40L, actual.getFactionParticipation().get("mordor").startedAtMillis);
        assertNull(actual.getFactionParticipation().get("mordor").endedAtMillis);
        assertEquals("mordor", actual.getCommitments().get("C1").validatedEvent
            .originalGarrisonFactions.get("C9"));
        ValidatedCommitmentEvent event = actual.getCommitments().get("C2").validatedEvent;
        assertNotNull(event);
        assertEquals(ConflictAuthorityKind.ACTIVE_PARTICIPANT, event.authorityKind);
        assertEquals("gondor", event.authorityFactionId);

        Result replay = loaded.getConflictService().acceptValidatedCommitment(
            request("T100", "C2", "mordor", "gondor", EntryOrigin.RELIEF, false,
                expected(actual), 40), f.hostility, f.resolver, context(40));
        assertCode(Code.ALREADY_COMMITTED_SAME_CONFLICT, replay);
        assertSame(actual, replay.record);
    }

    @Test public void productionAuditCoversSuccessReplayRejectionStalenessAndDeparture() {
        KOMEWorldData data = new KOMEWorldData("production-adapter");
        data.initializeIntegratedWorld();
        UUID owner = UUID.fromString("30000000-0000-0000-0000-000000000001");
        data.lastKnownPlayerFactions.put(owner, "gondor");
        KOMEArmyCompany company = new KOMEArmyCompany();
        company.id = "C1";
        company.owner = owner;
        company.faction = "gondor";
        company.currentTile = "T100";
        KOMEHiredUnitRecord unit = campaignUnit(owner, "C1", "T100");
        company.units.add(unit.entity);
        company.totalPopulation = 25;
        company.groundPopulation = 25;
        data.armyCompanies.put(company.id, company);
        data.hiredUnits.put(unit.entity, unit);
        setRelation("gondor", "mordor", LOTRFactionRelations.Relation.ENEMY);
        int auditCount = KOMEAuditService.entries(data).size();

        Result result = data.getConflictService().acceptValidatedCommitment(data,
            request("T100", "C1", "gondor", "mordor", EntryOrigin.LEGAL_ARRIVAL,
                false, ExpectedConflict.absent(), 10), context(10));
        assertCode(Code.SUCCESS, result);
        assertEquals(auditCount + 1, KOMEAuditService.entries(data).size());
        KOMEAuditEntry audit = KOMEAuditService.entries(data).get(auditCount);
        assertEquals("CONFLICT", audit.domain);
        assertEquals("COMMIT", audit.action);

        KOMEConflictRecord committed = result.record;
        ValidatedCommitmentRequest replay = request("T100", "C1", "gondor", "mordor",
            EntryOrigin.LEGAL_ARRIVAL, false, expected(committed), 10,
            ValidatedConflictAuthority.defender("mordor"));
        assertCode(Code.ALREADY_COMMITTED_SAME_CONFLICT,
            data.getConflictService().acceptValidatedCommitment(data, replay, context(10)));
        assertEquals(auditCount + 1, KOMEAuditService.entries(data).size());

        ValidatedCommitmentRequest changed = request("T100", "C1", "gondor", "mordor",
            EntryOrigin.LEGAL_ARRIVAL, false, expected(committed), 11,
            ValidatedConflictAuthority.defender("mordor"));
        assertCode(Code.DUPLICATE_DETACHMENT,
            data.getConflictService().acceptValidatedCommitment(data, changed, context(11)));
        assertEquals(auditCount + 1, KOMEAuditService.entries(data).size());

        ValidatedCommitmentRequest stale = request("T100", "C1", "gondor", "mordor",
            EntryOrigin.LEGAL_ARRIVAL, false,
            ExpectedConflict.at(committed.getConflictId(), committed.getRevision() + 1L), 10,
            ValidatedConflictAuthority.defender("mordor"));
        assertCode(Code.STALE_REVISION,
            data.getConflictService().acceptValidatedCommitment(data, stale, context(12)));
        assertEquals(auditCount + 1, KOMEAuditService.entries(data).size());

        result = data.getConflictService().releaseValidatedCommitment(data,
            new ValidatedDepartureRequest("T100", "C1", "gondor", expected(committed)),
            context(20));
        assertCode(Code.SUCCESS, result);
        assertEquals(Operation.DETACHMENT_DEPARTURE,
            result.record.getLastTransition().operation);
        assertEquals(State.ORDINARY, result.record.getState());
        assertNull(result.record.getEndedAtMillis());
        assertEquals(auditCount + 2, KOMEAuditService.entries(data).size());
        KOMEAuditEntry departureAudit = KOMEAuditService.entries(data).get(auditCount + 1);
        assertEquals("CONFLICT", departureAudit.domain);
        assertEquals("DEPART", departureAudit.action);

        assertCode(Code.COMMITMENT_NOT_FOUND,
            data.getConflictService().releaseValidatedCommitment(data,
                new ValidatedDepartureRequest("T100", "C1", "gondor",
                    expected(result.record)), context(30)));
        assertEquals(auditCount + 2, KOMEAuditService.entries(data).size());
    }

    @Test public void noFixedSideOrCoalitionAuthorityWasIntroduced() {
        for (Field field : KOMEConflictRecord.class.getDeclaredFields()) {
            String name = field.getName().toLowerCase();
            assertFalse(name.contains("sidea") || name.contains("sideb")
                || name.contains("coalition") || name.contains("enemygraph"));
        }
        for (State state : State.values()) {
            assertNotEquals("SURRENDER", state.name());
            assertNotEquals("ACTIVE_SIEGE", state.name());
        }
    }

    private static KOMEHiredUnitRecord campaignUnit(UUID owner, String companyId,
            String tileId) {
        KOMEHiredUnitRecord record = new KOMEHiredUnitRecord();
        record.entity = UUID.randomUUID();
        record.owner = owner;
        record.type = KOMEPopulationType.OFFENSIVE;
        record.cost = record.baseCost = record.populationSpent = 25;
        record.companyId = companyId;
        record.currentTile = tileId;
        record.sourceTileId = "T900";
        record.unitFaction = "gondor";
        record.populationOwningFaction = "gondor";
        KOMEHiredUnitClassification.assignForCampaignWorkflow(record);
        return record;
    }

    private static ValidatedCommitmentRequest request(String tile, String company,
            String faction, String opposed, EntryOrigin origin, boolean defensive,
            ExpectedConflict expected, long now) {
        return request(tile, company, faction, opposed, origin, defensive,
            Collections.<GarrisonParticipantSeed>emptyList(), expected, now);
    }

    private static ValidatedCommitmentRequest request(String tile, String company,
            String faction, String opposed, EntryOrigin origin, boolean defensive,
            ExpectedConflict expected, long now, ValidatedConflictAuthority authority) {
        return new ValidatedCommitmentRequest(tile, company, faction, authority, now,
            origin, "M" + company.substring(1), defensive,
            Collections.<GarrisonParticipantSeed>emptyList(), expected);
    }

    private static ValidatedCommitmentRequest request(String tile, String company,
            String faction, String opposed, EntryOrigin origin, boolean defensive,
            java.util.Collection<GarrisonParticipantSeed> garrison,
            ExpectedConflict expected, long now) {
        ValidatedConflictAuthority authority = expected.isAbsent()
            ? ValidatedConflictAuthority.defender(opposed)
            : ValidatedConflictAuthority.participant(opposed);
        return new ValidatedCommitmentRequest(tile, company, faction, authority, now,
            origin, "M" + company.substring(1), defensive, garrison, expected);
    }

    private static DetachmentResolution resolved(String company, String faction,
            String tile) {
        return new DetachmentResolution(company, ReferenceStatus.RESOLVED,
            KOMEHiredUnitClass.CAMPAIGN, faction, tile, "coherent");
    }

    private static ExpectedConflict expected(KOMEConflictRecord record) {
        return ExpectedConflict.at(record.getConflictId(), record.getRevision());
    }

    private static Context context(long now) {
        return new Context(now, "phase3-test", "validated Phase 3 mutation");
    }

    private static KOMEConflictRecord ok(Result result) {
        assertTrue(result.code + ": " + result.reason, result.isSuccess());
        return result.record;
    }

    private static void assertCode(Code expected, Result result) {
        assertEquals(result.reason, expected, result.code);
    }

    private static void setRelation(String first, String second,
            LOTRFactionRelations.Relation relation) {
        LOTRFactionRelations.overrideRelations(KOMEAlliance.findLotrFaction(first),
            KOMEAlliance.findLotrFaction(second), relation);
    }

    private static final class MutableHostility implements HostilityResolver {
        Hostility value = Hostility.HOSTILE;
        @Override public Hostility resolve(String firstFaction, String secondFaction) {
            return value;
        }
    }

    private static final class Resolver implements DetachmentResolver {
        final Map<String, DetachmentResolution> values =
            new LinkedHashMap<String, DetachmentResolution>();
        void put(DetachmentResolution value) { values.put(value.detachmentId, value); }
        void remove(String id) { values.remove(id); }
        @Override public DetachmentResolution resolve(String detachmentId) {
            DetachmentResolution value = values.get(detachmentId);
            return value == null ? new DetachmentResolution(detachmentId,
                ReferenceStatus.MISSING, null, "", "", "missing") : value;
        }
    }

    private static final class Fixture {
        final KOMEConflictService service;
        final MutableHostility hostility = new MutableHostility();
        final Resolver resolver = new Resolver();

        Fixture() { this(new KOMEConflictService()); }
        Fixture(KOMEConflictService service) {
            this.service = service;
            resolver.put(resolved("C1", "gondor", "T100"));
        }
        Result accept(ValidatedCommitmentRequest request) {
            return service.acceptValidatedCommitment(request, hostility, resolver,
                context(request.acceptedAtMillis));
        }
        Result release(ValidatedDepartureRequest request, long now) {
            return service.releaseValidatedCommitment(request, resolver, context(now));
        }
    }
}
