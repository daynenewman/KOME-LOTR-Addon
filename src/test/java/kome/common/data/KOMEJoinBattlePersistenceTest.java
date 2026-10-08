package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.Test;

import java.util.Collections;
import java.util.UUID;

import static kome.common.data.KOMEConflictContracts.ExpectedConflict;
import static kome.common.data.KOMEJoinBattleDeploymentReceipt.*;
import static org.junit.Assert.*;

public class KOMEJoinBattlePersistenceTest {
    @org.junit.Rule public final KOMETileTestResources geometry = new KOMETileTestResources();

    private static final UUID PLAYER_1 = UUID.fromString("31000000-0000-0000-0000-000000000001");
    private static final UUID PLAYER_2 = UUID.fromString("31000000-0000-0000-0000-000000000002");
    private static final UUID PLAYER_3 = UUID.fromString("31000000-0000-0000-0000-000000000003");
    private static final UUID MOUNT = UUID.fromString("32000000-0000-0000-0000-000000000001");

    @Test public void receiptAllocatorIsMonotonicCanonicalAndNeverRestoresBelowHighWater() {
        KOMEJoinBattleReceiptIdAllocator allocator = new KOMEJoinBattleReceiptIdAllocator();
        assertEquals("JB1", allocator.peek()); assertEquals("JB1", allocator.allocate());
        assertEquals("JB2", allocator.allocate()); assertEquals(3L, allocator.getNextSequence());
        assertEquals(91L, KOMEJoinBattleReceiptIdAllocator.sequenceOf("JB91"));
        for (String invalid : new String[]{"", "JB0", "JB01", "jb1", " JB1 ", "CF1"})
            assertThrows(IllegalArgumentException.class,
                () -> KOMEJoinBattleReceiptIdAllocator.requireIdentity(invalid));

        KOMEJoinBattleDeploymentReceipt receipt = pending("JB2", "token-2", PLAYER_1, "CF1", "T100");
        assertThrows(IllegalArgumentException.class, () ->
            KOMEJoinBattleDeploymentRegistry.restore(Collections.singletonMap("JB2", receipt), 2L));
    }

    @Test public void everyReceiptStateAndOldReplacedConflictObligationRoundTrips() {
        KOMEWorldData data = initialized("receipt-states");
        KOMEConflictRecord first = start(data, "T100", 10L);
        KOMEConflictRecord ended = ok(data.getConflictService().end("T100",
            expected(first), context(20L)));
        KOMEConflictRecord replacement = ok(data.getConflictService().start("T100", KOMEConflictRecord.State.ORDINARY,
            expected(ended), Collections.<KOMEConflictContracts.GarrisonSeed>emptyList(), context(30L)));
        assertEquals("CF2", replacement.getConflictId());

        KOMEJoinBattleDeploymentRegistry registry = data.getJoinBattleDeploymentReceipts();
        registry.publishNew(pending("JB1", "token-pending", PLAYER_1, "CF1", "T100"));
        registry.publishNew(deployed("JB2", "token-deployed", PLAYER_2, "CF1", "T100"));
        registry.publishNew(pendingEgress("JB3", "token-egress", PLAYER_3, "CF1", "T100"));
        registry.publishNew(closed("JB4", "token-closed",
            UUID.fromString("31000000-0000-0000-0000-000000000004"), "CF1", "T100"));

        KOMEWorldData loaded = load(save(data));
        assertEquals(5L, loaded.getJoinBattleDeploymentReceipts().getNextReceiptSequence());
        assertEquals(State.PENDING_ENTRY, loaded.getJoinBattleDeploymentReceipts().get("JB1").getState());
        assertEquals(State.DEPLOYED, loaded.getJoinBattleDeploymentReceipts().get("JB2").getState());
        assertEquals(State.PENDING_EGRESS, loaded.getJoinBattleDeploymentReceipts().get("JB3").getState());
        assertEquals(State.CLOSED, loaded.getJoinBattleDeploymentReceipts().get("JB4").getState());
        assertEquals("CF1", loaded.getJoinBattleDeploymentReceipts().get("JB3").getConflictId());
        assertEquals("CF2", loaded.getConflictService().get("T100").getConflictId());
    }

    @Test public void mountedEnvelopeRoundTripsAndTemporaryNbtIsDefensivelyCopied() {
        NBTTagCompound sourceNbt = new NBTTagCompound();
        sourceNbt.setString("id", "Horse"); sourceNbt.setInteger("Health", 17);
        KOMEJoinBattleDeploymentReceipt receipt = base("JB1", "mounted-token", PLAYER_1,
            "CF1", "T100")
            .enteredMounted(true).mountUuid(MOUNT).mountEntityType("Horse")
            .mountProfile(MountProfile.VANILLA_HORSE).mountSourceAnchor(anchor())
            .mountTransferPhase(MountTransferPhase.SOURCE_SNAPSHOT_PERSISTED)
            .temporaryMountNbt(sourceNbt).build();
        sourceNbt.setInteger("Health", 1);
        assertEquals(17, receipt.getTemporaryMountNbt().getInteger("Health"));
        NBTTagCompound exposed = receipt.getTemporaryMountNbt(); exposed.setInteger("Health", 2);
        assertEquals(17, receipt.getTemporaryMountNbt().getInteger("Health"));

        KOMEWorldData data = initialized("mounted"); start(data, "T100", 10L);
        data.getJoinBattleDeploymentReceipts().publishNew(receipt);
        NBTTagCompound saved = save(data);
        KOMEWorldData loaded = load(saved);
        saved.getTagList(KOMEConflictPersistence.JOIN_BATTLE_RECEIPTS_KEY, 10)
            .getCompoundTagAt(0).getCompoundTag("TemporaryMountNbt").setInteger("Health", 3);
        assertEquals(17, loaded.getJoinBattleDeploymentReceipts().get("JB1")
            .getTemporaryMountNbt().getInteger("Health"));
    }

    @Test public void unresolvedMountedDestinationPublicationRemainsRecoverableAcrossRestart() {
        NBTTagCompound snapshot = new NBTTagCompound();
        snapshot.setString("id", "Horse"); snapshot.setString("Recovery", "required");
        KOMEJoinBattleDeploymentReceipt receipt = base("JB1", "recovery-token", PLAYER_1,
            "CF1", "T100").deploymentDestination(new Pose(0, 20, 65, 20, 0, 0))
            .enteredMounted(true).mountUuid(MOUNT).mountEntityType("Horse")
            .mountProfile(MountProfile.VANILLA_HORSE).mountSourceAnchor(anchor())
            .mountTransferPhase(MountTransferPhase.DESTINATION_PUBLICATION_PENDING)
            .temporaryMountNbt(snapshot).build();
        KOMEWorldData data = initialized("mounted-recovery"); start(data, "T100", 10L);
        data.getJoinBattleDeploymentReceipts().publishNew(receipt);

        KOMEWorldData loaded = load(save(data));
        KOMEJoinBattleDeploymentReceipt restored =
            loaded.getJoinBattleDeploymentReceipts().get("JB1");
        assertEquals(State.PENDING_ENTRY, restored.getState());
        assertEquals(MountTransferPhase.DESTINATION_PUBLICATION_PENDING,
            restored.getMountTransferPhase());
        assertEquals("required", restored.getTemporaryMountNbt().getString("Recovery"));
        assertEquals(11, KOMEWorldData.KOME_DATA_SCHEMA_VERSION);
        assertEquals(2, KOMEConflictPersistence.DATA_SCHEMA_VERSION);
        assertEquals(1, KOMEHiredUnitPhysicalLocator.DATA_SCHEMA_VERSION);
    }

    @Test public void mountedAndLifecycleShapeValidationRejectsContradictions() {
        assertThrows(IllegalArgumentException.class, () -> base("JB1", "t1", PLAYER_1,
            "CF1", "T100").mountUuid(MOUNT).build());
        assertThrows(IllegalArgumentException.class, () -> base("JB1", "t1", PLAYER_1,
            "CF1", "T100").enteredMounted(true).mountUuid(MOUNT).mountEntityType("Horse")
            .mountSourceAnchor(anchor()).mountTransferPhase(MountTransferPhase.NOT_STARTED).build());
        assertThrows(IllegalArgumentException.class, () -> base("JB1", "t1", PLAYER_1,
            "CF1", "T100").enteredMounted(true).mountEntityType("Horse")
            .mountProfile(MountProfile.VANILLA_HORSE).mountSourceAnchor(anchor())
            .mountTransferPhase(MountTransferPhase.NOT_STARTED).build());
        assertThrows(IllegalArgumentException.class, () -> base("JB1", "t1", PLAYER_1,
            "CF1", "T100").enteredMounted(true).mountUuid(MOUNT)
            .mountProfile(MountProfile.VANILLA_HORSE).mountSourceAnchor(anchor())
            .mountTransferPhase(MountTransferPhase.NOT_STARTED).build());
        assertThrows(IllegalArgumentException.class, () -> base("JB1", "t1", PLAYER_1,
            "CF1", "T100").enteredMounted(true).mountUuid(MOUNT).mountEntityType("Horse")
            .mountProfile(MountProfile.VANILLA_HORSE).mountSourceAnchor(anchor())
            .deploymentDestination(new Pose(1, 1, 70, 1, 0, 0))
            .mountTransferPhase(MountTransferPhase.NOT_STARTED).build());
        NBTTagCompound nbt = new NBTTagCompound(); nbt.setString("id", "Horse");
        assertThrows(IllegalArgumentException.class, () -> base("JB1", "t1", PLAYER_1,
            "CF1", "T100").enteredMounted(true).mountUuid(MOUNT).mountEntityType("Horse")
            .mountProfile(MountProfile.VANILLA_HORSE).mountSourceAnchor(anchor())
            .mountTransferPhase(MountTransferPhase.DEPLOYMENT_COMPLETE).temporaryMountNbt(nbt).build());
        NBTTagCompound oversized = new NBTTagCompound();
        oversized.setByteArray("Payload", new byte[MAX_MOUNT_NBT_BYTES + 1]);
        assertThrows(IllegalArgumentException.class, () -> base("JB1", "t1", PLAYER_1,
            "CF1", "T100").enteredMounted(true).mountUuid(MOUNT).mountEntityType("Horse")
            .mountProfile(MountProfile.VANILLA_HORSE).mountSourceAnchor(anchor())
            .mountTransferPhase(MountTransferPhase.SOURCE_SNAPSHOT_PERSISTED)
            .temporaryMountNbt(oversized).build());

        KOMEJoinBattleDeploymentReceipt terminal = closed("JB1", "closed", PLAYER_1, "CF1", "T100");
        KOMEJoinBattleDeploymentRegistry terminalRegistry = KOMEJoinBattleDeploymentRegistry.restore(
            Collections.singletonMap("JB1", terminal), 2L);
        assertThrows(IllegalArgumentException.class, () -> terminalRegistry.replace(
            pending("JB1", "closed", PLAYER_1, "CF1", "T100")));

        KOMEJoinBattleDeploymentReceipt open = pending("JB1", "open", PLAYER_1, "CF1", "T100");
        KOMEJoinBattleDeploymentRegistry finalRegistry = KOMEJoinBattleDeploymentRegistry.restore(
            Collections.singletonMap("JB1", open), 2L);
        assertThrows(IllegalArgumentException.class, () -> finalRegistry.replace(
            base("JB1", "open", PLAYER_1, "CF1", "T100").factionId("rohan").build()));
        KOMEJoinBattleDeploymentReceipt deployed = deployed("JB1", "open", PLAYER_1, "CF1", "T100");
        KOMEJoinBattleDeploymentRegistry deployedRegistry = KOMEJoinBattleDeploymentRegistry.restore(
            Collections.singletonMap("JB1", deployed), 2L);
        assertThrows(IllegalArgumentException.class, () -> deployedRegistry.replace(open));
    }

    @Test public void strictCodecRejectsDuplicateIdentityTokenOpenPlayerAndBadHighWater() {
        KOMEWorldData data = initialized("strict"); start(data, "T100", 10L);
        data.getJoinBattleDeploymentReceipts().publishNew(
            pending("JB1", "token-a", PLAYER_1, "CF1", "T100"));
        data.getJoinBattleDeploymentReceipts().publishNew(
            closed("JB2", "token-b", PLAYER_2, "CF1", "T100"));
        NBTTagCompound canonical = save(data);

        NBTTagCompound duplicateId = copy(canonical);
        receiptRows(duplicateId).getCompoundTagAt(1).setString("ReceiptId", "JB1");
        expectInvalid(duplicateId, "Duplicate Join Battle receipt identity");

        NBTTagCompound duplicateToken = copy(canonical);
        receiptRows(duplicateToken).getCompoundTagAt(1).setString("ActionToken", "token-a");
        expectInvalid(duplicateToken, "Duplicate Join Battle action token");

        NBTTagCompound duplicateOpenPlayer = copy(canonical);
        NBTTagCompound second = receiptRows(duplicateOpenPlayer).getCompoundTagAt(1);
        second.setString("PlayerId", PLAYER_1.toString());
        second.setString("State", State.PENDING_ENTRY.name());
        second.removeTag("ClosedAtMillis"); second.removeTag("ClosureOutcome");
        expectInvalid(duplicateOpenPlayer, "multiple open Join Battle receipts");

        NBTTagCompound badJoinHighWater = copy(canonical);
        badJoinHighWater.setLong(KOMEConflictPersistence.JOIN_BATTLE_SEQUENCE_KEY, 2L);
        expectInvalid(badJoinHighWater, "Next Join Battle receipt sequence");

        NBTTagCompound unissuedConflict = copy(canonical);
        receiptRows(unissuedConflict).getCompoundTagAt(0).setString("ConflictId", "CF2");
        expectInvalid(unissuedConflict, "unissued conflict identity");

        NBTTagCompound wrongTile = copy(canonical);
        receiptRows(wrongTile).getCompoundTagAt(0).setString("TileId", "T101");
        expectInvalid(wrongTile, "tile disagrees");
    }

    @Test public void malformedReceiptLoadIsAtomicAndWriteBlocksExistingWorld() {
        KOMEWorldData source = initialized("source"); start(source, "T100", 10L);
        source.getJoinBattleDeploymentReceipts().publishNew(
            pending("JB1", "valid-token", PLAYER_1, "CF1", "T100"));
        NBTTagCompound malformed = save(source);
        receiptRows(malformed).getCompoundTagAt(0).setString("ReturnAnchor", "wrong");

        KOMEWorldData target = initialized("target"); start(target, "T900", 20L);
        target.getJoinBattleDeploymentReceipts().publishNew(
            pending("JB1", "target-token", PLAYER_2, "CF1", "T900"));
        try { target.readFromNBT(malformed); fail("Malformed receipt loaded."); }
        catch (IllegalStateException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("JoinBattleDeploymentReceipts"));
        }
        assertTrue(target.isWriteBlocked());
        assertNotNull(target.getConflictService().get("T900"));
        assertEquals("target-token", target.getJoinBattleDeploymentReceipts().get("JB1").getActionToken());
    }

    @Test public void unmountedRowsWithMountFieldsAndFutureConflictVersionsReject() {
        KOMEWorldData data = initialized("forbidden"); start(data, "T100", 10L);
        data.getJoinBattleDeploymentReceipts().publishNew(
            pending("JB1", "token", PLAYER_1, "CF1", "T100"));
        NBTTagCompound mountOnUnmounted = save(data);
        receiptRows(mountOnUnmounted).getCompoundTagAt(0).setString("MountUuid", MOUNT.toString());
        expectInvalid(mountOnUnmounted, "Unmounted receipt contains MountUuid");

        NBTTagCompound futureConflict = save(data);
        futureConflict.setInteger(KOMEConflictPersistence.SCHEMA_KEY, 3);
        expectInvalid(futureConflict, "Unsupported ConflictDataSchemaVersion");

        NBTTagCompound futureRoot = save(data);
        futureRoot.setInteger(KOMEWorldData.KOME_DATA_SCHEMA_KEY, 12);
        expectInvalid(futureRoot, "schema 12");
    }

    private static Builder base(String receipt, String token, UUID player, String conflict, String tile) {
        return KOMEJoinBattleDeploymentReceipt.builder().receiptId(receipt).actionToken(token)
            .playerId(player).conflictId(conflict).tileId(tile).acceptedConflictRevision(1L)
            .factionId("gondor").selectedCompanyId("C1").createdAtMillis(10L)
            .updatedAtMillis(10L).returnAnchor(anchor())
            .participationRecovery(ParticipationRecovery.REGISTRATION_REQUIRED);
    }
    private static KOMEJoinBattleDeploymentReceipt pending(String receipt, String token,
            UUID player, String conflict, String tile) {
        return base(receipt, token, player, conflict, tile).build();
    }
    private static KOMEJoinBattleDeploymentReceipt deployed(String receipt, String token,
            UUID player, String conflict, String tile) {
        return base(receipt, token, player, conflict, tile).state(State.DEPLOYED)
            .updatedAtMillis(20L).deployedAtMillis(20L).deploymentDestination(destination())
            .participationRecovery(ParticipationRecovery.PREEXISTING_ACTIVE).build();
    }
    private static KOMEJoinBattleDeploymentReceipt pendingEgress(String receipt, String token,
            UUID player, String conflict, String tile) {
        return base(receipt, token, player, conflict, tile).state(State.PENDING_EGRESS)
            .updatedAtMillis(30L).egressRequestedAtMillis(30L).egressReason("eligibility lost").build();
    }
    private static KOMEJoinBattleDeploymentReceipt closed(String receipt, String token,
            UUID player, String conflict, String tile) {
        return base(receipt, token, player, conflict, tile).state(State.CLOSED)
            .updatedAtMillis(40L).closedAtMillis(40L)
            .closureOutcome(ClosureOutcome.ENTRY_CANCELLED).build();
    }
    private static Pose anchor(){return new Pose(0, 1.25D, 70D, -2.5D, 90F, 10F);}
    private static Pose destination(){return new Pose(0, 20.5D, 72D, 30.5D, 180F, 0F);}

    private static KOMEWorldData initialized(String name) {
        KOMEWorldData data = new KOMEWorldData(name); data.initializeIntegratedWorld(); data.setDirty(false); return data;
    }
    private static KOMEConflictRecord start(KOMEWorldData data, String tile, long now) {
        return ok(data.getConflictService().start(tile, KOMEConflictRecord.State.ORDINARY, ExpectedConflict.absent(),
            Collections.<KOMEConflictContracts.GarrisonSeed>emptyList(), context(now)));
    }
    private static KOMEConflictRecord ok(KOMEConflictService.Result result) {
        assertTrue(result.code + ": " + result.reason, result.isSuccess()); return result.record;
    }
    private static ExpectedConflict expected(KOMEConflictRecord record) {
        return ExpectedConflict.at(record.getConflictId(), record.getRevision());
    }
    private static KOMEConflictContracts.Context context(long now) {
        return new KOMEConflictContracts.Context(now, "test", "Join Battle persistence");
    }
    private static NBTTagCompound save(KOMEWorldData data) {
        NBTTagCompound root = new NBTTagCompound(); data.writeToNBT(root); return root;
    }
    private static KOMEWorldData load(NBTTagCompound root) {
        KOMEWorldData data = new KOMEWorldData("loaded"); data.readFromNBT(root); return data;
    }
    private static NBTTagCompound copy(NBTTagCompound root) { return (NBTTagCompound) root.copy(); }
    private static NBTTagList receiptRows(NBTTagCompound root) {
        return (NBTTagList) root.getTag(KOMEConflictPersistence.JOIN_BATTLE_RECEIPTS_KEY);
    }
    private static void expectInvalid(NBTTagCompound source, String fragment) {
        KOMEWorldData target = new KOMEWorldData("invalid");
        try { target.readFromNBT(source); fail("Invalid Join Battle persistence loaded."); }
        catch (IllegalStateException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains(fragment));
        }
        assertTrue(target.isWriteBlocked());
    }
}
