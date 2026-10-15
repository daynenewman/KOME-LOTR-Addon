package kome.common.data;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import org.junit.Test;
import static org.junit.Assert.*;
import static kome.common.data.KOMEPlayerBattleLifecycle.*;
import static kome.common.data.KOMEPlayerBattleLifecycleRegistry.*;
import static kome.common.data.KOMEPlayerBattleLifecycleRegistryTest.*;

public class KOMEPlayerBattleLifecyclePersistenceTest {
    @Test public void restartRoundTripPreservesExactAbsoluteDeadlineAndDoesNotConsultNewDelay() throws Exception {
        KOMEPlayerBattleLifecycleRegistry registry = active();
        KOMEPlayerBattleLifecycle before = registry.get(PLAYER, "CF1");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CompressedStreamTools.writeCompressed(KOMEPlayerBattleLifecyclePersistence.write(registry), bytes);
        KOMEPlayerBattleLifecycleRegistry restarted = KOMEPlayerBattleLifecyclePersistence.read(
            CompressedStreamTools.readCompressed(new ByteArrayInputStream(bytes.toByteArray())));
        assertEquals(before, restarted.get(PLAYER, "CF1"));
        assertEquals(Status.COOLDOWN_ACTIVE, restarted.interruptionStatus(PLAYER, "CF1", 30999));
        assertEquals(Status.COOLDOWN_EXPIRED, restarted.interruptionStatus(PLAYER, "CF1", 31000));
        assertEquals(Change.UNCHANGED, begin(restarted, 1, Cause.DISCONNECT, 100000, 90000).change);
        assertEquals(Long.valueOf(31000), restarted.get(PLAYER, "CF1").eligibleAtMillis);
    }

    @Test public void activeCompletedBarredAndTerminalFactsAllRoundTripExactly() {
        KOMEPlayerBattleLifecycleRegistry registry = active();
        registry.beginInterruption(PLAYER, "CF2", "T100", 0, Cause.DISCONNECT, 10, 20, "JB2");
        registry.completeReentry(PLAYER, "CF2", 1, 30);
        registry.recordFormalRetreat(OTHER, "CF1", "T100", 100);
        registry.recordFormalRetreat(PLAYER, "CF2", "T100", 40);
        registry.markConflictTerminal("CF2", 50);
        registry.markConflictTerminal("CF3", 60); // Empty terminal markers must also survive restart.
        KOMEPlayerBattleLifecycleRegistry loaded = roundTrip(registry);
        assertEquals(registry.snapshot().records, loaded.snapshot().records);
        assertEquals(registry.snapshot().terminalConflicts, loaded.snapshot().terminalConflicts);
        assertEquals(Status.FORMAL_RETREAT_BARRED, loaded.interruptionStatus(OTHER, "CF1", 200));
        assertEquals(Status.NONE, loaded.interruptionStatus(OTHER, "CF2", 200));
        assertEquals(Status.NONE, loaded.interruptionStatus(PLAYER, "CF4", 200));
        assertEquals(Change.CONFLICT_TERMINAL, loaded.beginInterruption(OTHER, "CF3", "T100", 0, Cause.DEATH, 200, 1, "").change);
    }

    @Test public void completedGenerationSurvivesRestartAndStaleCompletionCannotClearNewerInterruption() {
        KOMEPlayerBattleLifecycleRegistry registry = active();
        registry.completeReentry(PLAYER, "CF1", 1, 31000);
        KOMEPlayerBattleLifecycleRegistry loaded = roundTrip(registry);
        assertEquals(1, loaded.get(PLAYER, "CF1").generation);
        assertEquals(Change.GENERATION_MISMATCH, begin(loaded, 0, Cause.DEATH, 1000, EXAMPLE_DELAY).change);
        begin(loaded, 1, Cause.DEATH, 32000, EXAMPLE_DELAY);
        loaded = roundTrip(loaded);
        assertEquals(Change.GENERATION_MISMATCH, loaded.completeReentry(PLAYER, "CF1", 1, 100000).change);
        assertEquals(2, loaded.get(PLAYER, "CF1").generation); assertTrue(loaded.get(PLAYER, "CF1").interruptionActive);
    }

    @Test public void barredCooldownCannotBeErasedByRestartDeathLogoutOrCompletion() {
        KOMEPlayerBattleLifecycleRegistry registry = active();
        registry.recordFormalRetreat(PLAYER, "CF1", "T100", 2000);
        KOMEPlayerBattleLifecycleRegistry loaded = roundTrip(registry);
        assertEquals(Change.FORMAL_RETREAT_BARRED, begin(loaded, 1, Cause.DEATH, 40000, 10).change);
        assertEquals(Change.FORMAL_RETREAT_BARRED, begin(loaded, 1, Cause.DISCONNECT, 50000, 10).change);
        loaded.completeReentry(PLAYER, "CF1", 1, 60000);
        loaded = roundTrip(loaded);
        assertEquals(Status.FORMAL_RETREAT_BARRED, loaded.interruptionStatus(PLAYER, "CF1", 100000));
        assertEquals(Long.valueOf(2000), loaded.get(PLAYER, "CF1").formalRetreatAtMillis);
        assertFalse(loaded.get(PLAYER, "CF1").interruptionActive);
    }

    @Test public void prunedTerminalHistoryCannotReappearAfterRestartOrLeakIntoReplacementConflict() {
        KOMEPlayerBattleLifecycleRegistry registry = active();
        registry.recordFormalRetreat(PLAYER, "CF1", "T100", 2000);
        registry.markConflictTerminal("CF1", 3000); registry.pruneTerminalRecords("CF1");
        KOMEPlayerBattleLifecycleRegistry loaded = roundTrip(registry);
        assertTrue(loaded.snapshot().records.isEmpty()); assertTrue(loaded.isConflictTerminal("CF1"));
        assertEquals(Change.CONFLICT_TERMINAL, begin(loaded, 0, Cause.DEATH, 4000, 1).change);
        assertEquals(Status.NONE, loaded.interruptionStatus(PLAYER, "CF2", 4000));
        assertEquals(Change.APPLIED, loaded.beginInterruption(PLAYER, "CF2", "T100", 0, Cause.DEATH, 4000, 1, "JB2").change);
    }

    @Test public void codecWritesDeterministicOrderRegardlessOfInsertionOrder() {
        KOMEPlayerBattleLifecycleRegistry first = new KOMEPlayerBattleLifecycleRegistry();
        KOMEPlayerBattleLifecycleRegistry second = new KOMEPlayerBattleLifecycleRegistry();
        first.recordFormalRetreat(PLAYER, "CF2", "T100", 1);
        first.recordFormalRetreat(OTHER, "CF1", "T100", 2);
        second.recordFormalRetreat(OTHER, "CF1", "T100", 2);
        second.recordFormalRetreat(PLAYER, "CF2", "T100", 1);
        first.markConflictTerminal("CF4", 3); first.markConflictTerminal("CF3", 4);
        second.markConflictTerminal("CF3", 4); second.markConflictTerminal("CF4", 3);
        assertEquals(KOMEPlayerBattleLifecyclePersistence.write(first), KOMEPlayerBattleLifecyclePersistence.write(second));
    }

    @Test public void nullMissingAndUnsupportedVersionsNeverBecomeEmptyRegistries() {
        assertThrows(IllegalArgumentException.class, () -> KOMEPlayerBattleLifecyclePersistence.read(null));
        assertThrows(IllegalArgumentException.class, () -> KOMEPlayerBattleLifecyclePersistence.read(new NBTTagCompound()));
        for (int version : new int[]{-1, 0, 2, Integer.MAX_VALUE}) {
            NBTTagCompound root = encoded(); root.setInteger("Version", version);
            assertThrows(IllegalArgumentException.class, () -> KOMEPlayerBattleLifecyclePersistence.read(root));
        }
        NBTTagCompound wrongType = encoded(); wrongType.setLong("Version", 1);
        assertThrows(IllegalArgumentException.class, () -> KOMEPlayerBattleLifecyclePersistence.read(wrongType));
    }

    @Test public void missingPlayerConflictCauseGenerationAndOtherRequiredFieldsReject() {
        for (String key : new String[]{"PlayerId", "ConflictId", "TileId", "OriginalReceiptId", "Generation",
                "Cause", "InterruptedAtMillis", "EligibleAtMillis", "InterruptionActive", "FormalRetreatBar"})
            invalid(row -> row.removeTag(key));
    }

    @Test public void malformedIdentitiesAndCauseRejectRatherThanNormalizeOrInventAuthority() {
        invalid(row -> row.setString("PlayerId", "1-1-1-1-1"));
        invalid(row -> row.setString("PlayerId", "not-a-uuid"));
        invalid(row -> row.setString("ConflictId", " CF1"));
        invalid(row -> row.setString("ConflictId", "CF0"));
        invalid(row -> row.setString("OriginalReceiptId", "JB01"));
        invalid(row -> row.setString("TileId", "t100"));
        invalid(row -> row.setString("Cause", "LOGOUT"));
        invalid(row -> row.setString("Cause", ""));
    }

    @Test public void wrongNumericTypesAndMalformedGenerationReject() {
        invalid(row -> row.setString("Generation", "1"));
        invalid(row -> row.setInteger("Generation", 1));
        invalid(row -> row.setLong("Generation", -1));
        invalid(row -> row.setLong("Generation", 0));
        invalid(row -> row.setInteger("InterruptedAtMillis", 1000));
        invalid(row -> row.setString("EligibleAtMillis", "31000"));
        invalid(row -> row.setByte("InterruptionActive", (byte) 2));
        invalid(row -> row.setInteger("FormalRetreatBar", 0));
    }

    @Test public void negativeOrReversedTimestampsAndImpossibleActivityReject() {
        invalid(row -> row.setLong("InterruptedAtMillis", -1));
        invalid(row -> row.setLong("EligibleAtMillis", -1));
        invalid(row -> row.setLong("EligibleAtMillis", 999));
        invalid(row -> row.setLong("ReenteredAtMillis", -1));
        invalid(row -> row.setBoolean("InterruptionActive", false));
        invalid(row -> row.setLong("ReenteredAtMillis", 31000)); // Still active.
        invalid(row -> { row.setBoolean("InterruptionActive", false); row.setLong("ReenteredAtMillis", 30999); });
        invalid(row -> { row.setBoolean("FormalRetreatBar", true); row.setLong("FormalRetreatAtMillis", -1); });
        invalid(row -> row.setLong("TerminalAtMillis", -1));
    }

    @Test public void retreatBarCannotBeSilentlyLostThroughMissingOrContradictoryFields() {
        invalid(row -> row.setBoolean("FormalRetreatBar", true));
        invalid(row -> row.setLong("FormalRetreatAtMillis", 2000));
        invalid(row -> row.setString("FormalRetreatAtMillis", "2000"));
        KOMEPlayerBattleLifecycleRegistry registry = active();
        registry.recordFormalRetreat(PLAYER, "CF1", "T100", 2000);
        NBTTagCompound root = KOMEPlayerBattleLifecyclePersistence.write(registry);
        row(root).removeTag("FormalRetreatAtMillis");
        assertThrows(IllegalArgumentException.class, () -> KOMEPlayerBattleLifecyclePersistence.read(root));
    }

    @Test public void duplicatePlayerConflictKeysRejectInsteadOfOverwritingOlderOrNewerGeneration() {
        NBTTagCompound root = encoded();
        NBTTagCompound duplicate = (NBTTagCompound) row(root).copy();
        duplicate.setLong("Generation", 2); duplicate.setLong("InterruptedAtMillis", 40000); duplicate.setLong("EligibleAtMillis", 70000);
        root.getTagList("Records", 10).appendTag(duplicate);
        assertThrows(IllegalArgumentException.class, () -> KOMEPlayerBattleLifecyclePersistence.read(root));
    }

    @Test public void sameConflictCannotDecodeDifferentTileIdentitiesAcrossPlayers() {
        NBTTagCompound root = encoded(); NBTTagCompound other = (NBTTagCompound) row(root).copy();
        other.setString("PlayerId", OTHER.toString()); other.setString("TileId", "T101");
        root.getTagList("Records", 10).appendTag(other);
        assertThrows(IllegalArgumentException.class, () -> KOMEPlayerBattleLifecyclePersistence.read(root));
    }

    @Test public void duplicateOrMalformedTerminalMarkersReject() {
        KOMEPlayerBattleLifecycleRegistry registry = active(); registry.markConflictTerminal("CF1", 2000);
        NBTTagCompound root = KOMEPlayerBattleLifecyclePersistence.write(registry);
        root.getTagList("TerminalConflicts", 10).appendTag(root.getTagList("TerminalConflicts", 10).getCompoundTagAt(0).copy());
        assertThrows(IllegalArgumentException.class, () -> KOMEPlayerBattleLifecyclePersistence.read(root));
        for (Consumer<NBTTagCompound> mutation : terminalMutations()) {
            NBTTagCompound malformed = KOMEPlayerBattleLifecyclePersistence.write(registry);
            mutation.accept(malformed.getTagList("TerminalConflicts", 10).getCompoundTagAt(0));
            assertThrows(IllegalArgumentException.class, () -> KOMEPlayerBattleLifecyclePersistence.read(malformed));
        }
    }

    @Test public void rowAndConflictTerminalMarkerMustAgreeAndCannotPrecedeKnownFacts() {
        KOMEPlayerBattleLifecycleRegistry registry = active(); registry.markConflictTerminal("CF1", 2000);
        NBTTagCompound noMarker = KOMEPlayerBattleLifecyclePersistence.write(registry);
        noMarker.setTag("TerminalConflicts", new NBTTagList());
        assertThrows(IllegalArgumentException.class, () -> KOMEPlayerBattleLifecyclePersistence.read(noMarker));
        NBTTagCompound noRowTerminal = KOMEPlayerBattleLifecyclePersistence.write(registry);
        row(noRowTerminal).removeTag("TerminalAtMillis"); row(noRowTerminal).setLong("ReenteredAtMillis", 32000);
        assertThrows(IllegalArgumentException.class, () -> KOMEPlayerBattleLifecyclePersistence.read(noRowTerminal));
        NBTTagCompound mismatched = KOMEPlayerBattleLifecyclePersistence.write(registry);
        row(mismatched).setLong("TerminalAtMillis", 2001);
        assertThrows(IllegalArgumentException.class, () -> KOMEPlayerBattleLifecyclePersistence.read(mismatched));
        NBTTagCompound tooEarly = KOMEPlayerBattleLifecyclePersistence.write(registry);
        row(tooEarly).setLong("TerminalAtMillis", 999);
        tooEarly.getTagList("TerminalConflicts", 10).getCompoundTagAt(0).setLong("TerminalAtMillis", 999);
        assertThrows(IllegalArgumentException.class, () -> KOMEPlayerBattleLifecyclePersistence.read(tooEarly));
    }

    @Test public void listsMustExistAndContainCompoundsAndUnknownFieldsFailClosed() {
        for (String list : new String[]{"Records", "TerminalConflicts"}) {
            NBTTagCompound missing = encoded(); missing.removeTag(list);
            assertThrows(IllegalArgumentException.class, () -> KOMEPlayerBattleLifecyclePersistence.read(missing));
            NBTTagCompound wrongType = encoded(); wrongType.setString(list, "not-a-list");
            assertThrows(IllegalArgumentException.class, () -> KOMEPlayerBattleLifecyclePersistence.read(wrongType));
            NBTTagCompound wrongRows = encoded(); NBTTagList strings = new NBTTagList(); strings.appendTag(new NBTTagString("bad"));
            wrongRows.setTag(list, strings);
            assertThrows(IllegalArgumentException.class, () -> KOMEPlayerBattleLifecyclePersistence.read(wrongRows));
        }
        invalid(row -> row.setLong("FormalRetreatAtMills", 2000)); // Misspelled safety fact is not ignored.
        NBTTagCompound unknown = encoded(); unknown.setBoolean("IgnoreCooldown", true);
        assertThrows(IllegalArgumentException.class, () -> KOMEPlayerBattleLifecyclePersistence.read(unknown));
    }

    @Test public void malformedLaterRowDoesNotMutateInputOrAnyExistingRegistry() {
        KOMEPlayerBattleLifecycleRegistry registry = active(); Snapshot before = registry.snapshot();
        NBTTagCompound root = KOMEPlayerBattleLifecyclePersistence.write(registry);
        root.getTagList("Records", 10).appendTag(new NBTTagCompound());
        NBTTagCompound inputBefore = (NBTTagCompound) root.copy();
        assertThrows(IllegalArgumentException.class, () -> KOMEPlayerBattleLifecyclePersistence.read(root));
        assertEquals(inputBefore, root); assertEquals(before.records, registry.snapshot().records);
    }

    @Test public void decodedRecordsShareNoMutableNbtAndEmptyVersionOneRoundTrips() {
        NBTTagCompound root = encoded(); KOMEPlayerBattleLifecycleRegistry loaded = KOMEPlayerBattleLifecyclePersistence.read(root);
        row(root).setLong("EligibleAtMillis", 999999);
        assertEquals(Long.valueOf(31000), loaded.get(PLAYER, "CF1").eligibleAtMillis);
        KOMEPlayerBattleLifecycleRegistry empty = roundTrip(new KOMEPlayerBattleLifecycleRegistry());
        assertTrue(empty.snapshot().records.isEmpty()); assertTrue(empty.snapshot().terminalConflicts.isEmpty());
        assertEquals(1, KOMEPlayerBattleLifecyclePersistence.FORMAT_VERSION);
    }

    private static List<Consumer<NBTTagCompound>> terminalMutations() {
        List<Consumer<NBTTagCompound>> mutations = new ArrayList<Consumer<NBTTagCompound>>();
        mutations.add(row -> row.removeTag("ConflictId"));
        mutations.add(row -> row.setString("ConflictId", "CF0"));
        mutations.add(row -> row.removeTag("TerminalAtMillis"));
        mutations.add(row -> row.setLong("TerminalAtMillis", -1));
        mutations.add(row -> row.setInteger("TerminalAtMillis", 2000));
        return mutations;
    }
    private static KOMEPlayerBattleLifecycleRegistry active() {
        KOMEPlayerBattleLifecycleRegistry registry = new KOMEPlayerBattleLifecycleRegistry();
        begin(registry, 0, Cause.DEATH, 1000, EXAMPLE_DELAY); return registry;
    }
    private static NBTTagCompound encoded() { return KOMEPlayerBattleLifecyclePersistence.write(active()); }
    private static NBTTagCompound row(NBTTagCompound root) { return root.getTagList("Records", 10).getCompoundTagAt(0); }
    private static void invalid(Consumer<NBTTagCompound> mutation) {
        NBTTagCompound root = encoded(); mutation.accept(row(root));
        assertThrows(IllegalArgumentException.class, () -> KOMEPlayerBattleLifecyclePersistence.read(root));
    }
    private static KOMEPlayerBattleLifecycleRegistry roundTrip(KOMEPlayerBattleLifecycleRegistry registry) {
        return KOMEPlayerBattleLifecyclePersistence.read(KOMEPlayerBattleLifecyclePersistence.write(registry));
    }
}
