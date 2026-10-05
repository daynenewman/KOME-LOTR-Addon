package kome.common.data;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static kome.common.data.KOMEConflictContracts.*;
import static kome.common.data.KOMEConflictRecord.*;
import static kome.common.data.KOMEConflictServiceTest.*;
import static org.junit.Assert.*;

public class KOMEConflictModelTest {
    @Test public void allocatorIsMonotonicSeedableAndNeverWraps() {
        KOMEConflictIdAllocator allocator = new KOMEConflictIdAllocator(41);
        assertEquals("CF41", allocator.allocate());
        assertEquals("CF42", allocator.allocate());
        assertEquals(43L, allocator.getNextSequence());
        allocator = new KOMEConflictIdAllocator(Long.MAX_VALUE - 1L);
        assertEquals("CF" + (Long.MAX_VALUE - 1L), allocator.allocate());
        final KOMEConflictIdAllocator exhausted = allocator;
        assertThrows(IllegalStateException.class, exhausted::allocate);
        assertEquals(Long.MAX_VALUE, allocator.getNextSequence());
        assertThrows(IllegalArgumentException.class, () -> new KOMEConflictIdAllocator(0));
        assertThrows(IllegalArgumentException.class, () -> new KOMEConflictIdAllocator(-1));
    }

    @Test public void serviceReturnsTypedExhaustionAndCannotAcknowledgeMalformedIdentity() {
        KOMEConflictService service = new KOMEConflictService(Long.MAX_VALUE);
        assertCode(Code.IDENTITY_EXHAUSTED, service.start("T100", State.ORDINARY, ExpectedConflict.absent(),
            Collections.<GarrisonSeed>emptyList(), context(10)));
        assertTrue(service.records().isEmpty());
        for (String id : Arrays.asList("", "CF0", "CF01", "HC_1", "CF-1", "CF9223372036854775807", "CF9223372036854775808"))
            assertThrows(IllegalArgumentException.class, () -> ExpectedConflict.at(id, 1));
        assertThrows(IllegalArgumentException.class, () -> ExpectedConflict.at("CF1", 0));
    }

    @Test public void onlyThreeUmbrellaStatesAndNoAuthoritativeSidesExist() {
        assertArrayEquals(new State[] {State.ORDINARY, State.ENCIRCLEMENT, State.ENDED}, State.values());
        assertThrows(IllegalArgumentException.class, () -> State.valueOf("SURRENDER"));
        assertThrows(IllegalArgumentException.class, () -> State.valueOf("ACTIVE_SIEGE"));
        List<Class<?>> snapshots = new ArrayList<Class<?>>();
        snapshots.add(KOMEConflictRecord.class);
        for (Class<?> type : KOMEConflictRecord.class.getDeclaredClasses())
            if (Modifier.isPublic(type.getModifiers()) && !type.isEnum()) snapshots.add(type);
        for (Class<?> type : snapshots) for (Field field : type.getDeclaredFields()) {
            if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())) continue;
            assertTrue(type.getSimpleName() + "." + field.getName(), Modifier.isFinal(field.getModifiers()));
            String name = field.getName().toLowerCase(Locale.ROOT);
            assertFalse(name, name.contains("coalition") || name.contains("enemygraph") || name.equals("sidea")
                || name.equals("sideb") || name.equals("sideone") || name.equals("sidetwo"));
            String fieldType = field.getType().getName();
            assertFalse(fieldType, fieldType.startsWith("net.minecraft.") || fieldType.startsWith("lotr."));
        }
        for (Field field : KOMEConflictRecord.class.getDeclaredFields())
            assertTrue("Record authority is private", Modifier.isPrivate(field.getModifiers()));
    }

    @Test public void snapshotsAndNestedCollectionsDoNotAliasCallerOrServiceState() {
        KOMEConflictService service = new KOMEConflictService();
        KOMEConflictRecord original = start(service, "T100", State.ENCIRCLEMENT, 10);
        Map<String, KOMEConflictRecord> registrySnapshot = service.records();
        List<String> ids = new ArrayList<String>(Collections.singleton("castle"));
        ComplexCatalog catalog = new ComplexCatalog(CatalogAvailability.AVAILABLE, ids);
        ids.add("citadel");
        KOMEConflictRecord current = ok(service.bindComplexCatalog("T100", expected(original), catalog, context(20)));
        assertEquals(Collections.singleton("castle"), current.getComplexes().keySet());
        assertTrue(original.getComplexes().isEmpty());
        assertSame(original, registrySnapshot.get("T100"));
        assertThrows(UnsupportedOperationException.class, () -> registrySnapshot.clear());
        assertThrows(UnsupportedOperationException.class, () -> current.getComplexes().clear());
        assertThrows(UnsupportedOperationException.class, () -> current.getCommitments().clear());
        assertThrows(UnsupportedOperationException.class, () -> catalog.requiredComplexIds.clear());

        UUID member = UUID.fromString("20000000-0000-0000-0000-000000000001");
        Map<UUID, GarrisonMemberState> source = new LinkedHashMap<UUID, GarrisonMemberState>();
        source.put(member, GarrisonMemberState.UNKNOWN);
        GarrisonCohort cohort = new GarrisonCohort("C1", source);
        source.clear();
        assertEquals(1, cohort.members.size());
        assertThrows(UnsupportedOperationException.class, () -> cohort.members.clear());
        ProgressCheckpoint progress = new ProgressCheckpoint(Collections.singleton("segment-1"));
        assertThrows(UnsupportedOperationException.class, () -> progress.securedSegmentIds.clear());
    }

    @Test public void identicalDiagnosticsAndCatalogBindingAreNoOpsNotRevisionOrLogPressure() {
        KOMEConflictService service = new KOMEConflictService();
        KOMEConflictRecord record = start(service, "T100", State.ENCIRCLEMENT, 10);
        ReferenceDiagnostic diagnostic = new ReferenceDiagnostic(ReferenceKind.SIEGE_COMPLEX,
            "castle", ReferenceStatus.MISSING, "catalog unavailable");
        record = ok(service.recordDiagnostic("T100", expected(record), diagnostic, context(20)));
        KOMEConflictService.Result noChange = service.recordDiagnostic("T100", expected(record), diagnostic, context(30));
        assertEquals(Code.NO_CHANGE, noChange.code);
        assertSame(record, noChange.record);
        ComplexCatalog catalog = new ComplexCatalog(CatalogAvailability.AVAILABLE, Collections.singleton("castle"));
        record = ok(service.bindComplexCatalog("T100", expected(record), catalog, context(30)));
        noChange = service.bindComplexCatalog("T100", expected(record), catalog, context(40));
        assertEquals(Code.NO_CHANGE, noChange.code);
        assertSame(record, noChange.record);
    }

    @Test public void unknownReferenceProjectionIsNotNeutralOrAnOrdinaryFallback() {
        DetachmentResolution missing = new DetachmentResolution("C1", ReferenceStatus.MISSING, null, "", "", "not found");
        assertNull(missing.classification);
        assertEquals(ReferenceStatus.MISSING, missing.status);
        assertEquals(Hostility.UNKNOWN, Hostility.valueOf("UNKNOWN"));
        assertThrows(IllegalArgumentException.class, () -> new DetachmentResolution("C1", ReferenceStatus.RESOLVED,
            null, "gondor", "T100", "invalid resolved projection"));
        assertThrows(IllegalArgumentException.class, () -> new ComplexCatalog(CatalogAvailability.UNAVAILABLE,
            Collections.singleton("castle")));
    }

    @Test public void malformedOrAmbiguousShapesCannotEnterDataContracts() {
        assertThrows(IllegalArgumentException.class, () -> new CommitmentInput("HC_1", KOMEHiredUnitClass.CAMPAIGN,
            EntryOrigin.LEGAL_ARRIVAL, "M1"));
        assertThrows(IllegalArgumentException.class, () -> new Context(-1, "test", "reason"));
        assertThrows(IllegalArgumentException.class, () -> new Context(1, "", "reason"));
        assertThrows(IllegalArgumentException.class, () -> new Context(1, "test", ""));
        assertThrows(IllegalArgumentException.class, () -> new ProgressCheckpoint(Arrays.asList("segment-1", " segment-1 ")));
        assertThrows(IllegalArgumentException.class, () -> new ComplexCatalog(CatalogAvailability.AVAILABLE,
            Arrays.asList("castle", "castle")));
        assertThrows(IllegalArgumentException.class, () -> new ComplexSubstate("castle", ComplexState.ASSAULT_ACTIVE,
            "", ProgressCheckpoint.empty(), null));
        assertThrows(IllegalArgumentException.class, () -> new ComplexSubstate("castle", ComplexState.CAPTURED,
            "assault-1", ProgressCheckpoint.empty(), null));
        assertThrows(IllegalArgumentException.class, () -> new ComplexSubstate("castle", ComplexState.UNTAKEN,
            "", ProgressCheckpoint.empty(), new LeadAuthority("gondor", null)));
        assertThrows(IllegalArgumentException.class, () -> new TimerSlot(TimerStatus.NOT_STARTED, 1000, 10, null, ""));
        assertThrows(IllegalArgumentException.class, () -> new TimerSlot(TimerStatus.RUNNING, 1000, 10, null, "CF1:E1"));
        assertThrows(IllegalArgumentException.class, () -> new CombatEpisode(1, "CF1:E1", EpisodeState.INACTIVE, 10L, null));
    }

    @Test public void nullAndMalformedServiceRequestsAreTypedAndAtomic() {
        KOMEConflictService service = new KOMEConflictService();
        assertCode(Code.INVALID_REQUEST, service.start("bad tile", State.ORDINARY, ExpectedConflict.absent(),
            Collections.<GarrisonSeed>emptyList(), context(10)));
        KOMEConflictRecord record = start(service, "T100", State.ORDINARY, 10);
        assertCode(Code.INVALID_REQUEST, service.commit("T100", expected(record), null, context(20)));
        assertCode(Code.INVALID_REQUEST, service.end("T100", expected(record), null));
        assertCode(Code.INVALID_REQUEST, service.end("T100", null, context(20)));
        assertCode(Code.NOT_FOUND, service.end("T101", expected(record), context(20)));
        assertCode(Code.INVALID_TRANSITION, service.registerComplex("T100", expected(record), "castle", context(20)));
        assertSame(record, service.get("T100"));
    }

    @Test public void episodeIdentitySequenceAndContinuityRejectStaleCheckpointData() {
        KOMEConflictService service = new KOMEConflictService();
        KOMEConflictRecord record = start(service, "T100", State.ORDINARY, 10);
        CombatEpisode active = new CombatEpisode(1, "CF1:E1", EpisodeState.ACTIVE, 20L, null);
        record = ok(service.checkpointCombat("T100", expected(record), active, TimerSlot.unstarted(), TimerSlot.unstarted(), context(20)));
        assertCode(Code.INVALID_EPISODE, service.checkpointCombat("T100", expected(record),
            new CombatEpisode(2, "CF1:E2", EpisodeState.ACTIVE, 30L, null), TimerSlot.unstarted(), TimerSlot.unstarted(), context(30)));
        assertCode(Code.INVALID_EPISODE, service.checkpointCombat("T100", expected(record),
            new CombatEpisode(1, "CF1:E1", EpisodeState.ACTIVE, 25L, null), TimerSlot.unstarted(), TimerSlot.unstarted(), context(30)));
        record = ok(service.checkpointCombat("T100", expected(record),
            new CombatEpisode(1, "CF1:E1", EpisodeState.INACTIVE, 20L, 30L), TimerSlot.unstarted(), TimerSlot.unstarted(), context(30)));
        record = ok(service.checkpointCombat("T100", expected(record),
            new CombatEpisode(2, "CF1:E2", EpisodeState.ACTIVE, 40L, null), TimerSlot.unstarted(), TimerSlot.unstarted(), context(40)));
        assertEquals(2L, record.getCombatEpisode().sequence);
    }

    @Test public void aggregateRejectsAmbiguousKeysAndMismatchedRevisionBeforePublication() {
        KOMEConflictService service = new KOMEConflictService();
        KOMEConflictRecord record = start(service, "T100", State.ORDINARY, 10);
        KOMEConflictRecord.Draft badKey = new KOMEConflictRecord.Draft(record);
        badKey.commitments.put("C2", new Commitment("C1", EntryOrigin.LEGAL_ARRIVAL, 10, ""));
        assertThrows(IllegalArgumentException.class, () -> new KOMEConflictRecord(badKey));
        KOMEConflictRecord.Draft badRevision = new KOMEConflictRecord.Draft(record);
        badRevision.revision = 2;
        assertThrows(IllegalArgumentException.class, () -> new KOMEConflictRecord(badRevision));
        KOMEConflictRecord.Draft missingLifecycle = new KOMEConflictRecord.Draft(record);
        missingLifecycle.state = State.ENCIRCLEMENT;
        missingLifecycle.lastTransition = new LastTransition(Operation.CREATE, null, State.ENCIRCLEMENT, 1, context(10), "CF1");
        assertThrows(IllegalArgumentException.class, () -> new KOMEConflictRecord(missingLifecycle));
        assertSame(record, service.get("T100"));
    }
}
