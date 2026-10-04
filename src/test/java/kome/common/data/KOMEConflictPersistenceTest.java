package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static kome.common.data.KOMEConflictContracts.*;
import static kome.common.data.KOMEConflictRecord.*;
import static org.junit.Assert.*;

public class KOMEConflictPersistenceTest {
    @org.junit.Rule public final KOMETileTestResources geometry = new KOMETileTestResources();

    private static final UUID PLAYER_ONE = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PLAYER_TWO = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID MEMBER_ONE = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID MEMBER_TWO = UUID.fromString("20000000-0000-0000-0000-000000000002");

    @Test public void emptySchemaSevenRoundTripsWithCanonicalSectionAndAllocator() {
        KOMEWorldData source = initialized("empty");
        NBTTagCompound saved = save(source);
        assertEquals(KOMEWorldData.KOME_DATA_SCHEMA_VERSION,
            saved.getInteger(KOMEWorldData.KOME_DATA_SCHEMA_KEY));
        assertEquals(1, saved.getInteger(KOMEConflictPersistence.SCHEMA_KEY));
        assertEquals(1L, saved.getLong(KOMEConflictPersistence.SEQUENCE_KEY));
        assertEquals(0, records(saved).tagCount());

        KOMEWorldData restored = load(saved);
        assertTrue(restored.getConflictService().records().isEmpty());
        assertEquals(1L, restored.getConflictService().getNextConflictSequence());
        assertEquals(conflictSection(saved).toString(), conflictSection(save(restored)).toString());
    }

    @Test public void activeOrdinaryRecordRoundTripsCommitmentsFactionsPlayersAndDiagnostics() {
        KOMEConflictService service = new KOMEConflictService();
        KOMEConflictRecord record = start(service, "T100", State.ORDINARY, 10);
        record = ok(service.commit("T100", expected(record), arrival("C1", EntryOrigin.LEGAL_ARRIVAL, "M1"), context(20)));
        record = ok(service.commit("T100", expected(record), arrival("C2", EntryOrigin.EXTERIOR_ARRIVAL, "M2"), context(30)));
        record = ok(service.beginFactionParticipation("T100", expected(record), "gondor", context(40)));
        record = ok(service.endFactionParticipation("T100", expected(record), "gondor", context(50)));
        record = ok(service.beginFactionParticipation("T100", expected(record), "gondor", context(60)));
        record = ok(service.beginFactionParticipation("T100", expected(record), "rohan", context(70)));
        record = ok(service.registerPlayer("T100", expected(record), PLAYER_ONE, "gondor", context(80)));
        record = ok(service.registerPlayer("T100", expected(record), PLAYER_TWO, "rohan", context(90)));
        record = ok(service.withdrawPlayer("T100", expected(record), PLAYER_TWO, context(100)));
        record = ok(service.recordDiagnostic("T100", expected(record),
            new ReferenceDiagnostic(ReferenceKind.DETACHMENT, "C2", ReferenceStatus.MISSING,
                "retained for reconciliation"), context(110)));

        KOMEConflictService restored = roundTrip(service);
        KOMEConflictRecord actual = restored.get("T100");
        assertEquals(State.ORDINARY, actual.getState());
        assertEquals(2, actual.getCommitments().size());
        assertEquals(EntryOrigin.EXTERIOR_ARRIVAL, actual.getCommitments().get("C2").origin);
        assertEquals(2L, actual.getFactionParticipation().get("gondor").continuitySequence);
        assertEquals(PlayerStatus.ACTIVE, actual.getPlayers().get(PLAYER_ONE).status);
        assertEquals(PlayerStatus.WITHDRAWN, actual.getPlayers().get(PLAYER_TWO).status);
        assertEquals(ReferenceStatus.MISSING,
            actual.getDiagnostics().get("DETACHMENT:C2").status);
        assertEquals(record.getRevision(), actual.getRevision());
    }

    @Test public void validatedCommitmentReplayIdentityRoundTripsAndMalformedShapeFailsClosed() {
        Draft draft = new Draft("CF1", "T100", State.ORDINARY, 10L);
        ValidatedCommitmentEvent event = new ValidatedCommitmentEvent("gondor",
            ConflictAuthorityKind.VALIDATED_DEFENDER, "mordor", true, false,
            Collections.<String, String>emptyMap());
        draft.commitments.put("C1", new Commitment("C1", EntryOrigin.LEGAL_ARRIVAL,
            10L, "M1", event));
        draft.factionParticipation.put("gondor",
            new FactionParticipation("gondor", 1L, 10L, null));
        draft.factionParticipation.put("mordor",
            new FactionParticipation("mordor", 1L, 10L, null));
        draft.revision = 1L;
        draft.lastTransition = new LastTransition(Operation.CREATE, null, State.ORDINARY,
            1L, context(10), "C1");
        Map<String, KOMEConflictRecord> records =
            new LinkedHashMap<String, KOMEConflictRecord>();
        records.put("T100", new KOMEConflictRecord(draft));
        KOMEConflictService service = KOMEConflictService.restore(records, 2L);

        KOMEConflictRecord actual = roundTrip(service).get("T100");
        ValidatedCommitmentEvent restored =
            actual.getCommitments().get("C1").validatedEvent;
        assertNotNull(restored);
        assertEquals("gondor", restored.detachmentFactionId);
        assertEquals(ConflictAuthorityKind.VALIDATED_DEFENDER, restored.authorityKind);
        assertEquals("mordor", restored.authorityFactionId);
        assertTrue(restored.createdConflict);
        assertFalse(restored.defensiveContext);
        assertTrue(restored.originalGarrisonFactions.isEmpty());

        KOMEWorldData malformedWorld = initialized("validated-event-malformed");
        malformedWorld.getConflictService().replaceFrom(service);
        NBTTagCompound malformed = save(malformedWorld);
        records(malformed).getCompoundTagAt(0).getTagList("Commitments", 10)
            .getCompoundTagAt(0).setString("ValidatedEvent", "wrong type");
        expectInvalid(malformed, "ValidatedEvent");
    }

    @Test public void activeEncirclementRoundTripsGarrisonReliefTimersAndIndependentComplexes() {
        KOMEConflictService service = fullEncirclement(false);
        KOMEConflictService restored = roundTrip(service);
        KOMEConflictRecord actual = restored.get("T100");
        assertEquals(State.ENCIRCLEMENT, actual.getState());
        assertEquals(Collections.singleton("C1"), actual.getOriginalGarrison().keySet());
        assertEquals(EntryOrigin.ORIGINAL_GARRISON, actual.getCommitments().get("C1").origin);
        assertEquals(EntryOrigin.RELIEF, actual.getCommitments().get("C2").origin);
        assertEquals(EntryOrigin.EXTERIOR_ARRIVAL, actual.getCommitments().get("C3").origin);
        assertEquals(CatalogAvailability.AVAILABLE, actual.getComplexCatalog().availability);
        assertEquals(2, actual.getComplexes().size());
        assertTrue(actual.getComplexes().get("castle").isCaptured());
        assertEquals(Collections.singleton("outer-wall"), actual.getComplexes().get("castle").progress.securedSegmentIds);
        assertEquals(ComplexState.ASSAULT_ACTIVE, actual.getComplexes().get("citadel").state);
        assertEquals("assault-2", actual.getComplexes().get("citadel").activeAssaultId);
        assertEquals("rohan", actual.getComplexes().get("citadel").lead.factionId);
        assertEquals(PLAYER_ONE, actual.getComplexes().get("citadel").lead.actorId);
        assertEquals(TimerStatus.RUNNING, actual.getResponseTimer().status);
        assertEquals(TimerStatus.PAUSED, actual.getCaptureTimer().status);
        assertEquals(EpisodeState.ACTIVE, actual.getCombatEpisode().state);
        assertEquals(12L, actual.getEncirclement().liveElapsedMillis);
    }

    @Test public void endedFullSnapshotRoundTripsAndFreshConflictIsCleanWithFreshIdentity() {
        KOMEConflictService service = fullEncirclement(true);
        KOMEConflictRecord endedBefore = service.get("T100");
        KOMEConflictService restored = roundTrip(service);
        KOMEConflictRecord ended = restored.get("T100");
        assertEquals(State.ENDED, ended.getState());
        assertEquals(endedBefore.getRevision(), ended.getRevision());
        assertEquals(TimerStatus.RUNNING, ended.getResponseTimer().status);
        assertEquals(EpisodeState.ACTIVE, ended.getCombatEpisode().state);
        assertTrue(ended.getComplexes().get("castle").isCaptured());
        assertEquals("assault-2", ended.getComplexes().get("citadel").activeAssaultId);
        assertNotNull(ended.getComplexes().get("citadel").lead);

        KOMEConflictRecord fresh = ok(restored.start("T100", State.ENCIRCLEMENT, expected(ended),
            Collections.<GarrisonSeed>emptyList(), context(200)));
        assertEquals("CF2", fresh.getConflictId());
        assertEquals(3L, restored.getNextConflictSequence());
        assertTrue(fresh.getCommitments().isEmpty());
        assertTrue(fresh.getPlayers().isEmpty());
        assertTrue(fresh.getFactionParticipation().isEmpty());
        assertTrue(fresh.getComplexes().isEmpty());
        assertEquals(CatalogAvailability.UNAVAILABLE, fresh.getComplexCatalog().availability);
        assertEquals(TimerStatus.NOT_STARTED, fresh.getResponseTimer().status);
        assertEquals(0L, fresh.getCombatEpisode().sequence);
    }

    @Test public void unavailableKnownEmptyAndUnknownGarrisonMemberRoundTripWithoutInference() {
        Draft unknownDraft = new Draft("CF1", "T100", State.ENCIRCLEMENT, 10L);
        unknownDraft.commitments.put("C1", new Commitment("C1", EntryOrigin.ORIGINAL_GARRISON, 10L, ""));
        Map<UUID, GarrisonMemberState> members = new LinkedHashMap<UUID, GarrisonMemberState>();
        members.put(MEMBER_ONE, GarrisonMemberState.UNKNOWN);
        unknownDraft.originalGarrison.put("C1", new GarrisonCohort("C1", members));
        unknownDraft.revision = 1L;
        unknownDraft.lastTransition = new LastTransition(Operation.CREATE, null, State.ENCIRCLEMENT, 1L, context(10), "CF1");
        KOMEConflictRecord unknown = new KOMEConflictRecord(unknownDraft);

        Draft emptyDraft = new Draft("CF2", "T101", State.ENCIRCLEMENT, 20L);
        emptyDraft.complexCatalog = new ComplexCatalog(CatalogAvailability.AVAILABLE, Collections.<String>emptySet());
        emptyDraft.revision = 1L;
        emptyDraft.lastTransition = new LastTransition(Operation.CREATE, null, State.ENCIRCLEMENT, 1L, context(20), "CF2");
        KOMEConflictRecord knownEmpty = new KOMEConflictRecord(emptyDraft);
        Map<String, KOMEConflictRecord> records = new LinkedHashMap<String, KOMEConflictRecord>();
        records.put("T100", unknown); records.put("T101", knownEmpty);

        KOMEConflictService restored = roundTrip(KOMEConflictService.restore(records, 3L));
        assertEquals(CatalogAvailability.UNAVAILABLE, restored.get("T100").getComplexCatalog().availability);
        assertEquals(GarrisonMemberState.UNKNOWN,
            restored.get("T100").getOriginalGarrison().get("C1").members.get(MEMBER_ONE));
        assertEquals(1, restored.get("T100").getOriginalGarrison().get("C1").unconfirmedTerminalMembers());
        assertEquals(CatalogAvailability.AVAILABLE, restored.get("T101").getComplexCatalog().availability);
        assertTrue(restored.get("T101").getComplexCatalog().requiredComplexIds.isEmpty());
    }

    @Test public void allocatorHighWaterSurvivesRestartAndCannotReuseReplacedIdentity() {
        KOMEConflictService service = new KOMEConflictService();
        KOMEConflictRecord first = start(service, "T100", State.ORDINARY, 10);
        KOMEConflictRecord ended = ok(service.end("T100", expected(first), context(20)));
        KOMEConflictRecord second = ok(service.start("T100", State.ORDINARY, expected(ended),
            Collections.<GarrisonSeed>emptyList(), context(30)));
        assertEquals("CF2", second.getConflictId());
        KOMEConflictService restored = roundTrip(service);
        KOMEConflictRecord third = start(restored, "T101", State.ORDINARY, 40);
        assertEquals("CF3", third.getConflictId());
    }

    @Test public void endedHistoricalCommitmentDoesNotBlockLaterActiveCommitmentAcrossRestart() {
        KOMEConflictService service = new KOMEConflictService();
        KOMEConflictRecord first = start(service, "T100", State.ORDINARY, 10);
        first = ok(service.commit("T100", expected(first),
            arrival("C1", EntryOrigin.LEGAL_ARRIVAL, "M1"), context(20)));
        KOMEConflictRecord ended = ok(service.end("T100", expected(first), context(30)));

        KOMEConflictRecord second = start(service, "T101", State.ORDINARY, 40);
        second = ok(service.commit("T101", expected(second),
            arrival("C1", EntryOrigin.LEGAL_ARRIVAL, "M2"), context(50)));
        assertEquals("CF1", ended.getConflictId());
        assertEquals("CF2", second.getConflictId());
        assertEquals(3L, service.getNextConflictSequence());

        KOMEConflictService restored = roundTrip(service);
        KOMEConflictRecord restoredEnded = restored.get("T100");
        KOMEConflictRecord restoredActive = restored.get("T101");
        assertEquals(2, restored.records().size());
        assertEquals(State.ENDED, restoredEnded.getState());
        assertEquals("CF1", restoredEnded.getConflictId());
        assertEquals("M1", restoredEnded.getCommitments().get("C1").movementOrderId);
        assertEquals(State.ORDINARY, restoredActive.getState());
        assertTrue(restoredActive.isActive());
        assertEquals("CF2", restoredActive.getConflictId());
        assertEquals("M2", restoredActive.getCommitments().get("C1").movementOrderId);
        assertEquals(ended.getRevision(), restoredEnded.getRevision());
        assertEquals(second.getRevision(), restoredActive.getRevision());
        assertEquals(3L, restored.getNextConflictSequence());

        KOMEConflictRecord third = start(restored, "T102", State.ORDINARY, 60);
        KOMEConflictService.Result duplicateActive = restored.commit("T102", expected(third),
            arrival("C1", EntryOrigin.LEGAL_ARRIVAL, "M3"), context(70));
        assertEquals(Code.DETACHMENT_ALREADY_COMMITTED, duplicateActive.code);
        assertSame(third, restored.get("T102"));
        assertFalse(restored.get("T102").getCommitments().containsKey("C1"));
    }

    @Test public void duplicateTileAndDuplicateConflictIdentityAreRejected() {
        NBTTagCompound duplicateTile = twoOrdinaryRecords();
        NBTTagList tileRows = records(duplicateTile);
        tileRows.getCompoundTagAt(1).setString("TileId", "T100");
        expectInvalid(duplicateTile, "Duplicate conflict tile");

        NBTTagCompound duplicateId = twoOrdinaryRecords();
        NBTTagList idRows = records(duplicateId);
        idRows.getCompoundTagAt(1).setString("ConflictId", "CF1");
        expectInvalid(duplicateId, "Duplicate conflict identity");

        NBTTagCompound duplicateCommitment = twoOrdinaryRecords();
        addCommitment(records(duplicateCommitment).getCompoundTagAt(0), "C1", 10L);
        addCommitment(records(duplicateCommitment).getCompoundTagAt(1), "C1", 20L);
        expectInvalid(duplicateCommitment, "multiple active conflicts");
    }

    @Test public void malformedEnumsNestedTypesAndSequenceFailClosed() {
        NBTTagCompound badState = ordinaryDocument();
        records(badState).getCompoundTagAt(0).setString("State", "SURRENDER");
        expectInvalid(badState, "conflict state");

        NBTTagCompound badNested = ordinaryDocument();
        records(badNested).getCompoundTagAt(0).setString("ResponseTimer", "wrong type");
        expectInvalid(badNested, "ResponseTimer");

        NBTTagCompound badSequence = twoOrdinaryRecords();
        badSequence.setLong(KOMEConflictPersistence.SEQUENCE_KEY, 2L);
        expectInvalid(badSequence, "Next conflict sequence");

        NBTTagCompound noncanonicalId = ordinaryDocument();
        records(noncanonicalId).getCompoundTagAt(0).setString("ConflictId", " CF1 ");
        expectInvalid(noncanonicalId, "Noncanonical conflict ID");
    }

    @Test public void invalidConflictSectionVersionAndUnknownFutureRootSchemaAreRejected() {
        NBTTagCompound wrongSection = ordinaryDocument();
        wrongSection.setInteger(KOMEConflictPersistence.SCHEMA_KEY, 2);
        expectInvalid(wrongSection, KOMEConflictPersistence.SCHEMA_KEY);

        NBTTagCompound missingSection = ordinaryDocument();
        missingSection.removeTag(KOMEConflictPersistence.SCHEMA_KEY);
        expectInvalid(missingSection, KOMEConflictPersistence.SCHEMA_KEY);

        NBTTagCompound future = ordinaryDocument();
        future.setInteger(KOMEWorldData.KOME_DATA_SCHEMA_KEY,
            KOMEWorldData.KOME_DATA_SCHEMA_VERSION + 1);
        expectInvalid(future, "schema 7 -> 8");
    }

    @Test public void conflictDecodeFailureLeavesExistingLiveStateUntouchedAndWriteBlocked() {
        KOMEWorldData target = initialized("target");
        KOMEConflictRecord current = start(target.getConflictService(), "T900", State.ORDINARY, 5);
        String before = conflictSection(KOMEConflictPersistence.write(target.getConflictService())).toString();
        NBTTagCompound malformed = ordinaryDocument();
        records(malformed).getCompoundTagAt(0).getCompoundTag("CombatEpisode").setString("Sequence", "wrong");
        try { target.readFromNBT(malformed); fail("Malformed conflict section must fail."); }
        catch (IllegalStateException expected) { assertTrue(expected.getMessage().contains("ConflictRecords")); }
        assertTrue(target.isWriteBlocked());
        assertSame(current, target.getConflictService().get("T900"));
        assertEquals(before, conflictSection(KOMEConflictPersistence.write(target.getConflictService())).toString());
    }

    @Test public void sourceNbtDoesNotAliasLoadedConflictState() {
        NBTTagCompound source = ordinaryDocument();
        KOMEWorldData loaded = load(source);
        KOMEConflictRecord record = loaded.getConflictService().get("T100");
        records(source).getCompoundTagAt(0).setString("State", "ENDED");
        records(source).getCompoundTagAt(0).getTagList("Commitments", 10).appendTag(new NBTTagCompound());
        assertSame(record, loaded.getConflictService().get("T100"));
        assertEquals(State.ORDINARY, record.getState());
        assertEquals(1, record.getCommitments().size());
    }

    @Test public void writeValidationFailsBeforeDestinationMutation() throws Exception {
        KOMEWorldData data = initialized("write-atomic");
        start(data.getConflictService(), "T100", State.ORDINARY, 10);
        Field allocatorField = KOMEConflictService.class.getDeclaredField("allocator");
        allocatorField.setAccessible(true);
        ((KOMEConflictIdAllocator) allocatorField.get(data.getConflictService())).setNextSequence(1L);
        NBTTagCompound destination = new NBTTagCompound();
        destination.setString("Sentinel", "unchanged");
        try { data.writeToNBT(destination); fail("Contradictory allocator must reject write."); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("Next conflict sequence")); }
        assertEquals("unchanged", destination.getString("Sentinel"));
        assertFalse(destination.hasKey(KOMEWorldData.KOME_DATA_SCHEMA_KEY));
        assertFalse(destination.hasKey(KOMEConflictPersistence.RECORDS_KEY));
    }

    @Test public void schemaSevenUpgradePreservesAuthoritiesAndInfersNoEmergencyDefense() {
        KOMEWorldData legacy = initialized("schema-seven");
        legacy.grantFactionPopulationCenti("gondor", 2500L);
        KOMEConquestRouteEdge edge = new KOMEConquestRouteEdge("T100", "T101", KOMEConquestRouteEdge.OPEN);
        legacy.routeEdges.put(KOMEConquestRouteEdge.key("T100", "T101"), edge);
        KOMEDiplomacyRecord diplomacy = new KOMEDiplomacyRecord("gondor", "rohan");
        diplomacy.relation = KOMEDiplomacyRelation.ENEMIES;
        legacy.canonicalDiplomacyRecords.put(diplomacy.key(), diplomacy);
        KOMEPlayerBuild build = new KOMEPlayerBuild();
        build.id = "defensive-sentinel"; build.tileId = "T100"; build.type = KOMEBuildType.DEFENSIVE;
        build.populationFaction = "gondor"; build.originalBuilderFaction = "gondor";
        legacy.builds.put(build.id, build);
        UUID progressionPlayer = UUID.fromString("00000000-0000-0000-0000-000000000099");
        legacy.getProgression(progressionPlayer);
        addCoherentCompany(legacy);

        NBTTagCompound schemaSeven = save(legacy);
        schemaSeven.setInteger(KOMEWorldData.KOME_DATA_SCHEMA_KEY, 7);
        schemaSeven.removeTag(KOMEEmergencyDefensePersistence.SCHEMA_KEY);
        schemaSeven.removeTag(KOMEEmergencyDefensePersistence.RECORDS_KEY);
        KOMEWorldData upgraded = load(schemaSeven);
        assertTrue(upgraded.isDirty());
        assertEquals(2500L, upgraded.getFactionPopulationIfPresent("gondor").getAvailablePopulationCenti());
        assertTrue(upgraded.routeEdges.containsKey(KOMEConquestRouteEdge.key("T100", "T101")));
        assertEquals(KOMEDiplomacyRelation.ENEMIES, upgraded.canonicalDiplomacyRecords.get("gondor|rohan").relation);
        assertTrue(upgraded.builds.containsKey("defensive-sentinel"));
        assertTrue(upgraded.progressions.containsKey(progressionPlayer));
        assertTrue(upgraded.armyCompanies.containsKey("C1"));
        assertTrue(upgraded.getConflictService().records().isEmpty());
        assertEquals(1L, upgraded.getConflictService().getNextConflictSequence());
        assertTrue(upgraded.emergencyDefenseActivities.isEmpty());

        NBTTagCompound schemaEight = save(upgraded);
        assertEquals(8, schemaEight.getInteger(KOMEWorldData.KOME_DATA_SCHEMA_KEY));
        assertEquals(1, schemaEight.getInteger(KOMEConflictPersistence.SCHEMA_KEY));
        assertEquals(1, schemaEight.getInteger(KOMEEmergencyDefensePersistence.SCHEMA_KEY));
        assertEquals(0, records(schemaEight).tagCount());
        assertEquals(0, schemaEight.getTagList(
            KOMEEmergencyDefensePersistence.RECORDS_KEY, 10).tagCount());
        NBTTagCompound expectedAuthorities = (NBTTagCompound) schemaSeven.copy();
        expectedAuthorities.removeTag(KOMEWorldData.KOME_DATA_SCHEMA_KEY);
        NBTTagCompound actualAuthorities = (NBTTagCompound) schemaEight.copy();
        actualAuthorities.removeTag(KOMEWorldData.KOME_DATA_SCHEMA_KEY);
        actualAuthorities.removeTag(KOMEEmergencyDefensePersistence.SCHEMA_KEY);
        actualAuthorities.removeTag(KOMEEmergencyDefensePersistence.RECORDS_KEY);
        assertEquals(expectedAuthorities, actualAuthorities);
    }

    private static KOMEConflictService fullEncirclement(boolean end) {
        KOMEConflictService service = new KOMEConflictService();
        GarrisonSeed seed = new GarrisonSeed("C1", KOMEHiredUnitClass.CAMPAIGN, Arrays.asList(MEMBER_ONE, MEMBER_TWO));
        KOMEConflictRecord record = ok(service.start("T100", State.ENCIRCLEMENT, ExpectedConflict.absent(),
            Collections.singleton(seed), context(10)));
        record = ok(service.commit("T100", expected(record), arrival("C2", EntryOrigin.RELIEF, "M2"), context(20)));
        record = ok(service.commit("T100", expected(record), arrival("C3", EntryOrigin.EXTERIOR_ARRIVAL, "M3"), context(30)));
        record = ok(service.beginFactionParticipation("T100", expected(record), "gondor", context(40)));
        record = ok(service.beginFactionParticipation("T100", expected(record), "rohan", context(50)));
        record = ok(service.registerPlayer("T100", expected(record), PLAYER_ONE, "rohan", context(60)));
        record = ok(service.registerPlayer("T100", expected(record), PLAYER_TWO, "gondor", context(70)));
        record = ok(service.withdrawPlayer("T100", expected(record), PLAYER_TWO, context(80)));
        record = ok(service.registerComplex("T100", expected(record), "castle", context(90)));
        record = ok(service.registerComplex("T100", expected(record), "citadel", context(100)));
        record = ok(service.bindComplexCatalog("T100", expected(record),
            new ComplexCatalog(CatalogAvailability.AVAILABLE, Arrays.asList("castle", "citadel")), context(110)));
        record = ok(service.checkpointComplex("T100", expected(record), new ComplexSubstate("castle",
            ComplexState.CAPTURED, "", new ProgressCheckpoint(Collections.singleton("outer-wall")), null), context(120)));
        record = ok(service.checkpointComplex("T100", expected(record), new ComplexSubstate("citadel",
            ComplexState.ASSAULT_ACTIVE, "assault-2", new ProgressCheckpoint(Collections.singleton("inner-road")),
            new LeadAuthority("rohan", PLAYER_ONE)), context(130)));
        CombatEpisode episode = new CombatEpisode(1L, "CF1:E1", EpisodeState.ACTIVE, 135L, null);
        record = ok(service.checkpointCombat("T100", expected(record), episode,
            new TimerSlot(TimerStatus.RUNNING, 1000L, 125L, 140L, "CF1:E1"),
            new TimerSlot(TimerStatus.PAUSED, 2000L, 300L, 140L, "CF1:E1"), context(140)));
        record = ok(service.checkpointEncirclement("T100", expected(record), 12L, context(150)));
        record = ok(service.recordDiagnostic("T100", expected(record),
            new ReferenceDiagnostic(ReferenceKind.SIEGE_COMPLEX, "citadel", ReferenceStatus.UNKNOWN,
                "definition temporarily unavailable"), context(160)));
        if (end) ok(service.end("T100", expected(record), context(170)));
        return service;
    }

    private static void addCoherentCompany(KOMEWorldData data) {
        UUID owner = UUID.fromString("30000000-0000-0000-0000-000000000001");
        KOMEHiredUnitRecord unit = new KOMEHiredUnitRecord();
        unit.entity = UUID.fromString("40000000-0000-0000-0000-000000000001");
        unit.owner = owner; unit.sourcePlayer = owner; unit.companyId = "C1";
        unit.currentTile = "T100"; unit.sourceTileId = "T100"; unit.type = KOMEPopulationType.OFFENSIVE;
        unit.cost = unit.baseCost = unit.populationSpent = 25;
        unit.populationOwningFaction = unit.sourceFaction = unit.unitFaction = "gondor";
        KOMEHiredUnitClassification.assignForCampaignWorkflow(unit);
        data.hiredUnits.put(unit.entity, unit);
        KOMEArmyCompany company = new KOMEArmyCompany();
        company.id = "C1"; company.owner = owner; company.ownerName = "Owner";
        company.faction = company.nativeFaction = "gondor"; company.currentTile = "T100";
        company.sourceTileId = "T100"; company.status = KOMEArmyCompany.STATIONED;
        company.units.add(unit.entity); company.totalPopulation = company.groundPopulation = 25;
        data.armyCompanies.put(company.id, company);
        data.nextCompanySequence = 2L;
    }

    private static NBTTagCompound ordinaryDocument() {
        KOMEWorldData world = initialized("ordinary");
        KOMEConflictRecord record = start(world.getConflictService(), "T100", State.ORDINARY, 10);
        ok(world.getConflictService().commit("T100", expected(record),
            arrival("C1", EntryOrigin.LEGAL_ARRIVAL, "M1"), context(20)));
        return save(world);
    }

    private static NBTTagCompound twoOrdinaryRecords() {
        KOMEWorldData world = initialized("two");
        start(world.getConflictService(), "T100", State.ORDINARY, 10);
        start(world.getConflictService(), "T101", State.ORDINARY, 20);
        return save(world);
    }

    private static KOMEConflictService roundTrip(KOMEConflictService service) {
        KOMEWorldData world = initialized("source");
        world.getConflictService().replaceFrom(service);
        NBTTagCompound saved = save(world);
        KOMEWorldData restored = load(saved);
        assertEquals(conflictSection(saved).toString(), conflictSection(save(restored)).toString());
        return restored.getConflictService();
    }

    private static KOMEWorldData initialized(String name) {
        KOMEWorldData data = new KOMEWorldData(name);
        data.initializeIntegratedWorld();
        data.setDirty(false);
        return data;
    }

    private static NBTTagCompound save(KOMEWorldData data) {
        NBTTagCompound tag = new NBTTagCompound();
        data.writeToNBT(tag);
        return tag;
    }

    private static KOMEWorldData load(NBTTagCompound tag) {
        KOMEWorldData data = new KOMEWorldData("loaded");
        data.readFromNBT(tag);
        return data;
    }

    private static NBTTagCompound conflictSection(NBTTagCompound root) {
        NBTTagCompound section = new NBTTagCompound();
        section.setInteger(KOMEConflictPersistence.SCHEMA_KEY, root.getInteger(KOMEConflictPersistence.SCHEMA_KEY));
        section.setLong(KOMEConflictPersistence.SEQUENCE_KEY, root.getLong(KOMEConflictPersistence.SEQUENCE_KEY));
        section.setTag(KOMEConflictPersistence.RECORDS_KEY, root.getTag(KOMEConflictPersistence.RECORDS_KEY).copy());
        return section;
    }

    private static NBTTagList records(NBTTagCompound root) {
        return (NBTTagList) root.getTag(KOMEConflictPersistence.RECORDS_KEY);
    }

    private static void addCommitment(NBTTagCompound record, String detachmentId, long acceptedAt) {
        NBTTagCompound commitment = new NBTTagCompound();
        commitment.setString("DetachmentId", detachmentId);
        commitment.setString("Origin", EntryOrigin.LEGAL_ARRIVAL.name());
        commitment.setLong("AcceptedAtMillis", acceptedAt);
        commitment.setString("MovementOrderId", "");
        record.getTagList("Commitments", 10).appendTag(commitment);
    }

    private static void expectInvalid(NBTTagCompound source, String message) {
        KOMEWorldData target = new KOMEWorldData("invalid");
        try { target.readFromNBT(source); fail("Expected invalid conflict data."); }
        catch (IllegalStateException expected) { assertTrue(expected.getMessage(), expected.getMessage().contains(message)); }
        assertTrue(target.isWriteBlocked());
    }

    private static Context context(long timestamp) { return new Context(timestamp, "test", "persistence checkpoint"); }
    private static ExpectedConflict expected(KOMEConflictRecord record) {
        return ExpectedConflict.at(record.getConflictId(), record.getRevision());
    }
    private static CommitmentInput arrival(String id, EntryOrigin origin, String order) {
        return new CommitmentInput(id, KOMEHiredUnitClass.CAMPAIGN, origin, order);
    }
    private static KOMEConflictRecord start(KOMEConflictService service, String tile, State state, long timestamp) {
        return ok(service.start(tile, state, ExpectedConflict.absent(), Collections.<GarrisonSeed>emptyList(), context(timestamp)));
    }
    private static KOMEConflictRecord ok(KOMEConflictService.Result result) {
        assertTrue(result.code + ": " + result.reason, result.isSuccess());
        return result.record;
    }
}
