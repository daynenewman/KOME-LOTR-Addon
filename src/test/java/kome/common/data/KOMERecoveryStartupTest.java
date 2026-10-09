package kome.common.data;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import net.minecraft.nbt.*;
import static org.junit.Assert.*;
import static kome.common.data.KOMERecoveryJournalTestSupport.*;
import static kome.common.data.KOMEOrdinaryBattleRecoveryRegistry.*;
import static kome.common.data.KOMERecoveryJournalRecord.*;

public class KOMERecoveryStartupTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    @Rule public KOMETileTestResources geometry = new KOMETileTestResources();
    private NBTTagCompound emptySection() { return KOMEConflictPersistence.write(new KOMEConflictService()); }
    private KOMERecoveryJournalManager manager(File directory) throws IOException {
        return new KOMERecoveryJournalManager(directory, new Object(), KOMERecoveryJournalIO.FILES);
    }
    private KOMERecoveryStartup start(KOMERecoveryJournalManager manager, NBTTagCompound section, KOMEWorldData data) throws IOException {
        KOMERecoveryStartup s = new KOMERecoveryStartup(new Object()); s.initialize(manager, section, data); return s;
    }
    @Test public void preWalWorldProvisionsSynchronouslyAndReopensEstablishedIdentity() throws Exception {
        File directory = temporary.newFolder(); UUID id;
        try (KOMERecoveryJournalManager m = manager(directory)) {
            KOMEWorldData data = new KOMEWorldData(); KOMERecoveryStartup s = start(m, emptySection(), data);
            assertEquals(KOMERecoveryStartup.State.READY, s.state()); assertTrue(s.diagnostic().contains("bootstrap"));
            assertTrue(KOMERecoveryStartup.permits(data, null)); id = m.view().journalId;
            assertEquals(0, m.view().lastSequence);
        }
        try (KOMERecoveryJournalManager m = manager(directory)) {
            KOMERecoveryStartup s = start(m, emptySection(), new KOMEWorldData());
            assertEquals(KOMERecoveryStartup.State.READY, s.state()); assertTrue(s.diagnostic().contains("established"));
            assertEquals(id, m.view().journalId);
        }
    }
    @Test public void persistedFenceProhibitsFirstProvisioning() throws Exception {
        try (KOMERecoveryJournalManager m = manager(temporary.newFolder())) {
            NBTTagCompound n = emptySection(); n.setTag("SavedHealth", projection(4, 3, UUID.randomUUID(),
                new KOMECampaignHealthProjection.Fence(JOURNAL, 2, UUID.randomUUID()), false).write());
            KOMEWorldData data = new KOMEWorldData(); KOMERecoveryStartup s = start(m, n, data);
            assertEquals(KOMERecoveryStartup.State.BLOCKED_CONTRADICTORY, s.state());
            assertFalse(m.file().exists()); assertFalse(KOMERecoveryStartup.permits(data, null));
        }
    }
    @Test public void missingEstablishedWalBlocksWithoutReplacement() throws Exception {
        File root = temporary.newFolder(); File wal;
        try (KOMERecoveryJournalManager m = manager(root)) { m.bootstrap(); wal = m.file(); }
        Files.delete(wal.toPath());
        try (KOMERecoveryJournalManager m = manager(root)) {
            KOMERecoveryStartup s = start(m, emptySection(), new KOMEWorldData());
            assertEquals(KOMERecoveryStartup.State.BLOCKED_MISSING, s.state()); assertFalse(wal.exists());
        }
    }
    @Test public void markerMismatchAndCorruptWalBlock() throws Exception {
        for (boolean mismatch : new boolean[]{true, false}) {
            File root = temporary.newFolder(); File wal;
            try (KOMERecoveryJournalManager m = manager(root)) { m.bootstrap(); wal = m.file(); }
            byte[] bytes = Files.readAllBytes(wal.toPath());
            if (mismatch) bytes = KOMERecoveryJournalCodec.header(UUID.randomUUID(),
                KOMERecoveryJournalCodec.hex(KOMERecoveryJournalCodec.sha(root.getCanonicalPath().getBytes("UTF-8"))), false);
            else bytes[bytes.length - 1] ^= 1;
            Files.write(wal.toPath(), bytes);
            try (KOMERecoveryJournalManager m = manager(root)) {
                assertEquals(KOMERecoveryStartup.State.BLOCKED_CORRUPT, start(m, emptySection(), new KOMEWorldData()).state());
            }
            assertArrayEquals(bytes, Files.readAllBytes(wal.toPath()));
        }
    }
    @Test public void staleSessionManagerIsClosedAndNeverReused() throws Exception {
        File root = temporary.newFolder();
        KOMERecoveryJournalManager old = KOMERecoveryJournalManager.forSession(root, new Object()); old.bootstrap();
        UUID id = old.view().journalId;
        try (KOMERecoveryJournalManager next = KOMERecoveryJournalManager.forSession(root, new Object())) {
            assertNotSame(old, next); assertEquals(KOMERecoveryJournal.State.CLOSED, old.state());
            next.open(); assertEquals(id, next.view().journalId);
        }
    }
    @Test public void missingHistoryAndParticipantsRestoreExactlyWithPendingPreparedGrants() throws Exception {
        KOMERecoveryJournal wal = open(empty());
        KOMERecoveryJournal.Acknowledgment c = append(wal, context()); Prepare p = prepare(c, Component.RIDER); append(wal, p);
        NBTTagCompound original = emptySection(); NBTTagCompound before = (NBTTagCompound)original.copy();
        KOMERecoveryStartupPlan plan = KOMERecoveryStartupPlan.build(original, wal.view());
        KOMEConflictPersistence.Loaded loaded = KOMEConflictPersistence.readSection(plan.candidate);
        Episode e = loaded.recovery.get("CF1", "CF1:E1");
        assertEquals(before, original); assertEquals(Collections.singleton(RIDER), e.participants.keySet());
        assertEquals(context().episode.participants.get(RIDER).companyId, e.participants.get(RIDER).companyId);
        assertEquals(Disposition.FORMAL_RETREAT, e.participants.get(RIDER).disposition);
        assertEquals(Collections.singleton(MOUNT), e.participants.get(RIDER).mountIds);
        assertTrue(e.actualHostileCombatObserved); assertEquals(2, e.grants.size());
        assertEquals(context().intentFingerprint(), KOMERecoveryJournalCodec.intentFingerprint(e.intent));
        for (Grant g : e.grants.values()) assertEquals(GrantState.PENDING, g.state);
        assertEquals(p.recoveryTransitionId, e.grants.get("RIDER:" + RIDER).transition.token);
        assertEquals(2, loaded.conflicts.getNextConflictSequence()); assertEquals(2, wal.view().lastSequence);
        assertEquals(3, plan.candidate.getInteger(KOMEConflictPersistence.SCHEMA_KEY)); assertEquals(11, KOMEWorldData.KOME_DATA_SCHEMA_VERSION);
    }
    @Test public void detachedRepairRestoresMissingTargetWhileNormalDecoderStillRejects() throws Exception {
        KOMERecoveryJournal wal = open(empty()); append(wal, context());
        NBTTagCompound saved = KOMERecoveryStartupPlan.build(emptySection(), wal.view()).candidate;
        saved.getCompoundTag(KOMEConflictPersistence.RECOVERY_KEY).setTag("Targets", new NBTTagList());
        rejects(() -> KOMEConflictPersistence.readSection(saved));
        assertEquals(2, KOMEConflictPersistence.readSection(KOMERecoveryStartupPlan.build(saved, wal.view()).candidate).recovery.targets().size());
        NBTTagCompound malformed = (NBTTagCompound)saved.copy();
        malformed.getCompoundTag(KOMEConflictPersistence.RECOVERY_KEY).getTagList("Episodes",10).getCompoundTagAt(0).setString("StartedAtMillis", "bad");
        rejects(() -> KOMERecoveryStartupPlan.build(malformed, wal.view()));
    }
    @Test public void allocatorNeverDecreasesAndJoinBattleReceiptBytesRemainExact() throws Exception {
        KOMERecoveryJournal wal = open(empty()); append(wal, context());
        KOMEJoinBattleDeploymentReceipt receipt = KOMEJoinBattleDeploymentReceipt.builder()
            .receiptId("JB1").actionToken("preserve-receipt").playerId(UUID.randomUUID()).conflictId("CF1")
            .tileId("T101").acceptedConflictRevision(1).factionId("gondor").selectedCompanyId("C1")
            .createdAtMillis(10).updatedAtMillis(10).returnAnchor(new KOMEJoinBattleDeploymentReceipt.Pose(0,1,65,1,0,0))
            .participationRecovery(KOMEJoinBattleDeploymentReceipt.ParticipationRecovery.REGISTRATION_REQUIRED).build();
        KOMEJoinBattleDeploymentRegistry receipts = KOMEJoinBattleDeploymentRegistry.restore(Collections.singletonMap("JB1", receipt), 9);
        NBTTagCompound n = KOMEConflictPersistence.write(new KOMEConflictService(50), receipts);
        KOMERecoveryStartupPlan plan = KOMERecoveryStartupPlan.build(n, wal.view());
        assertEquals(50, plan.candidate.getLong(KOMEConflictPersistence.SEQUENCE_KEY));
        assertEquals(n.getTag(KOMEConflictPersistence.JOIN_BATTLE_RECEIPTS_KEY), plan.candidate.getTag(KOMEConflictPersistence.JOIN_BATTLE_RECEIPTS_KEY));
        assertEquals(n.getTag(KOMEConflictPersistence.JOIN_BATTLE_SEQUENCE_KEY), plan.candidate.getTag(KOMEConflictPersistence.JOIN_BATTLE_SEQUENCE_KEY));
    }
    private KOMEConflictService conflict(long sequence) {
        KOMEConflictService conflicts = new KOMEConflictService(sequence);
        assertTrue(conflicts.start("T101", KOMEConflictRecord.State.ORDINARY, KOMEConflictContracts.ExpectedConflict.absent(),
            Collections.<KOMEConflictContracts.GarrisonSeed>emptyList(), new KOMEConflictContracts.Context(30, "test", "startup")).isSuccess());
        return conflicts;
    }
    @Test public void laterConflictOnSameTileIsNeverReplaced() throws Exception {
        KOMERecoveryJournal wal = open(empty()); append(wal, context());
        NBTTagCompound n = KOMEConflictPersistence.write(conflict(2));
        KOMERecoveryStartupPlan plan = KOMERecoveryStartupPlan.build(n, wal.view());
        assertEquals(n.getTag(KOMEConflictPersistence.RECORDS_KEY), plan.candidate.getTag(KOMEConflictPersistence.RECORDS_KEY));
        assertTrue(plan.quarantinedConflicts.isEmpty());
        assertNotNull(KOMEConflictPersistence.readSection(plan.candidate).recovery.get("CF1", "CF1:E1"));
    }
    @Test public void unsupportedLiveClosureAndRevisionContradictionBlocksWithoutInventingConflict() throws Exception {
        File root = temporary.newFolder();
        try (KOMERecoveryJournalManager m = manager(root)) {
            m.bootstrap(); m.append(context(), 0, m.view().lastHash);
            NBTTagCompound n = KOMEConflictPersistence.write(conflict(1)); KOMEWorldData data = new KOMEWorldData();
            KOMERecoveryStartup s = start(m, n, data);
            assertEquals(KOMERecoveryStartup.State.BLOCKED_CONTRADICTORY, s.state());
            assertEquals(n.getTag(KOMEConflictPersistence.RECORDS_KEY), KOMEConflictPersistence.write(data.getConflictService()).getTag(KOMEConflictPersistence.RECORDS_KEY));
            assertFalse(KOMERecoveryStartup.permits(data, data.getConflictService().get("T101")));
            assertTrue(KOMEOrdinaryBattleResolutionService.hasResolutionReservation(data, data.getConflictService().get("T101")));
        }
    }
    @Test public void walMissingPrepareNeverFabricatesEvidence() throws Exception {
        KOMERecoveryJournal wal = open(empty()); KOMERecoveryJournal.Acknowledgment c = append(wal, context());
        KOMERecoveryJournalView before = wal.view(); append(wal, prepare(c, Component.RIDER));
        NBTTagCompound n = KOMERecoveryStartupPlan.build(emptySection(), wal.view()).candidate;
        rejects(() -> KOMERecoveryStartupPlan.build(n, before));
    }
    private KOMECampaignHealthProjection projection(long revision, float hp, UUID transition,
            KOMECampaignHealthProjection.Fence fence, boolean terminal) {
        return new KOMECampaignHealthProjection(RIDER, Component.RIDER, RIDER_ALIAS, hp, revision, transition, terminal, fence);
    }
    private KOMERecoveryJournal prepared() throws Exception {
        KOMERecoveryJournal wal = open(empty()); KOMERecoveryJournal.Acknowledgment c = append(wal, context()); append(wal, prepare(c, Component.RIDER)); return wal;
    }
    private KOMERecoveryJournalView.Entry preparation(KOMERecoveryJournal wal) { return wal.view().preparations.values().iterator().next(); }
    private KOMECampaignHealthProjection.Fence fence(KOMERecoveryJournalView.Entry e) {
        return new KOMECampaignHealthProjection.Fence(JOURNAL, e.sequence, ((Prepare)e.record).recoveryTransitionId);
    }
    @Test public void olderHealthRemainsPendingWithoutHpOrWalEffects() throws Exception {
        KOMERecoveryJournal wal = prepared(); KOMERecoveryJournalView.Entry e = preparation(wal);
        KOMECampaignHealthProjection p = projection(4, 3.25F, ((Prepare)e.record).sourceTransitionId, null, false);
        assertEquals(KOMERecoveryStartupPlan.Adoption.PENDING, KOMERecoveryStartupPlan.reconcile(wal.view(), e, p));
        assertEquals(3.25F, p.hp, 0); assertEquals(2, wal.view().lastSequence);
    }
    @Test public void exactPreparedHealthIsRecognizedButGrantRemainsPending() throws Exception {
        KOMERecoveryJournal wal = prepared(); KOMERecoveryJournalView.Entry e = preparation(wal); Prepare prepare = (Prepare)e.record;
        NBTTagCompound n = emptySection(); KOMECampaignHealthProjection p = projection(5, 8.25F, prepare.recoveryTransitionId, fence(e), false);
        n.setTag("SavedHealth", p.write()); KOMERecoveryStartupPlan plan = KOMERecoveryStartupPlan.build(n, wal.view());
        assertEquals(KOMERecoveryStartupPlan.Adoption.CONSUMED, plan.preparations.get(prepare.grantIdentity()));
        assertEquals(GrantState.PENDING, KOMEConflictPersistence.readSection(plan.candidate).recovery.get("CF1","CF1:E1").grants.get("RIDER:" + RIDER).state);
        assertEquals(p.write(), plan.candidate.getCompoundTag("SavedHealth"));
    }
    @Test public void newerDescendantPreservesLaterDamageOrHealing() throws Exception {
        KOMERecoveryJournal wal = prepared(); KOMERecoveryJournalView.Entry e = preparation(wal);
        for (float hp : new float[]{1.25F, 10F}) {
            KOMECampaignHealthProjection p = projection(6, hp, UUID.randomUUID(), fence(e), false);
            assertEquals(KOMERecoveryStartupPlan.Adoption.CONSUMED, KOMERecoveryStartupPlan.reconcile(wal.view(), e, p)); assertEquals(hp, p.hp, 0);
        }
    }
    @Test public void newerWithoutAncestryAndContradictoryEqualRevisionFailClosed() throws Exception {
        KOMERecoveryJournal wal = prepared(); KOMERecoveryJournalView.Entry e = preparation(wal); Prepare p = (Prepare)e.record;
        rejects(() -> KOMERecoveryStartupPlan.reconcile(wal.view(), e, projection(6, 2, UUID.randomUUID(), null, false)));
        rejects(() -> KOMERecoveryStartupPlan.reconcile(wal.view(), e, projection(5, 9, p.recoveryTransitionId, fence(e), false)));
        rejects(() -> KOMERecoveryStartupPlan.reconcile(wal.view(), e, projection(5, p.targetHp, UUID.randomUUID(), fence(e), false)));
        rejects(() -> KOMERecoveryStartupPlan.reconcile(wal.view(), e, projection(5, p.targetHp, p.recoveryTransitionId,
            new KOMECampaignHealthProjection.Fence(UUID.randomUUID(), e.sequence, p.recoveryTransitionId), false)));
    }
    @Test public void terminalEvidenceWinsAndReplacementAliasNeverInherits() throws Exception {
        KOMERecoveryJournal wal = prepared(); KOMERecoveryJournalView.Entry e = preparation(wal); Prepare p = (Prepare)e.record;
        append(wal, new Terminal(p.binding, RIDER_ALIAS, 6, UUID.randomUUID(), Disposition.DEAD, 30, "confirmed death",
            new Fence(JOURNAL, e.sequence, p.recoveryTransitionId)));
        assertEquals(KOMERecoveryStartupPlan.Adoption.TERMINAL, KOMERecoveryStartupPlan.reconcile(wal.view(), e, projection(4, 3.25F, p.sourceTransitionId, null, false)));
        KOMEOrdinaryBattleRecoveryRegistry r = KOMEConflictPersistence.readSection(KOMERecoveryStartupPlan.build(emptySection(), wal.view()).candidate).recovery;
        assertTrue(r.target(RIDER).terminal); assertEquals(GrantState.TERMINAL, r.get("CF1","CF1:E1").grants.get("RIDER:" + RIDER).state);
        rejects(() -> KOMERecoveryStartupPlan.reconcile(wal.view(), e, new KOMECampaignHealthProjection(RIDER, Component.RIDER,
            UUID.randomUUID(), 8.25F, 5, p.recoveryTransitionId, false, fence(e))));
    }
    @Test public void gateBlocksLifecycleAndRecoveryBeforeReadyAndAfterStop() throws Exception {
        KOMEWorldData data = new KOMEWorldData(); data.getConflictService().replaceFrom(conflict(1));
        KOMERecoveryStartup s = new KOMERecoveryStartup(new Object()); data.recoveryStartup = s;
        assertFalse(KOMERecoveryStartup.permits(data, null));
        KOMEOrdinaryBattleRecoveryService.observe(data, data.getConflictService().get("T101"), 40);
        assertTrue(data.getOrdinaryBattleRecovery().episodes().isEmpty());
        assertEquals(KOMEOrdinaryBattleLifecycleService.Code.DEFERRED_UNKNOWN_AUTHORITY,
            KOMEOrdinaryBattleLifecycleService.INSTANCE.process(data, "T101", 40).code);
        assertEquals(KOMEOrdinaryBattleResolutionService.Code.DEFERRED_UNKNOWN_AUTHORITY,
            KOMEOrdinaryBattleResolutionService.INSTANCE.preflight(data, "T101").code);
        try (KOMERecoveryJournalManager m = manager(temporary.newFolder())) {
            s.initialize(m, emptySection(), data); assertTrue(KOMERecoveryStartup.permits(data, null));
            s.stop(); s.stop(); assertEquals(KOMERecoveryJournal.State.CLOSED, m.state()); assertFalse(KOMERecoveryStartup.permits(data, null));
        }
    }
    @Test public void earlyNativeEntityCallbackDefersWithoutTouchingProjectionOrHealth() throws Exception {
        kome.common.KOMEAccessFixture f = new kome.common.KOMEAccessFixture();
        net.minecraft.entity.passive.EntityPig pig = new net.minecraft.entity.passive.EntityPig(f.world);
        pig.setHealth(3); KOMEHiredUnitRecord record = new KOMEHiredUnitRecord(); record.entity = pig.getUniqueID();
        KOMEHiredUnitClassification.assignForCampaignWorkflow(record);
        net.minecraft.world.WorldServer world = kome.common.KOMEAccessFixture.allocate(net.minecraft.world.WorldServer.class);
        pig.worldObj = world; KOMERecoveryStartup.beginSession(new Object());
        assertFalse(KOMECampaignHealth.observe(record, pig)); assertNull(record.survivingHealth); assertEquals(3F, pig.getHealth(), 0);
        KOMERecoveryStartup.stopSession();
    }
    @Test public void nonRootUnloadDoesNotCloseSharedJournalAndRootUnloadDoes() throws Exception {
        KOMERecoveryStartup.beginSession(new Object());
        java.lang.reflect.Field field = KOMERecoveryStartup.class.getDeclaredField("session"); field.setAccessible(true);
        KOMERecoveryStartup s = (KOMERecoveryStartup)field.get(null);
        try (KOMERecoveryJournalManager m = manager(temporary.newFolder())) {
            s.initialize(m, emptySection(), new KOMEWorldData());
            kome.common.KOMEAccessFixture f = new kome.common.KOMEAccessFixture();
            f.world.provider.dimensionId = 1; KOMERecoveryStartup.worldUnloaded(f.world);
            assertEquals(KOMERecoveryJournal.State.READY, m.state());
            f.world.provider.dimensionId = 0; KOMERecoveryStartup.worldUnloaded(f.world);
            assertEquals(KOMERecoveryJournal.State.CLOSED, m.state());
        }
    }
    @Test public void unloadedChunkFenceBlocksBootstrapAndInventoryNeverRewritesRegion() throws Exception {
        File root = temporary.newFolder(); File regionDirectory = new File(root, "DIM100/region"); assertTrue(regionDirectory.mkdirs());
        File file = new File(regionDirectory, "r.0.0.mca");
        KOMECampaignHealthProjection p = projection(5, 8.25F, UUID.randomUUID(),
            new KOMECampaignHealthProjection.Fence(JOURNAL, 2, UUID.randomUUID()), false);
        NBTTagCompound entity = new NBTTagCompound(); entity.setLong("UUIDMost", RIDER_ALIAS.getMostSignificantBits());
        entity.setLong("UUIDLeast", RIDER_ALIAS.getLeastSignificantBits()); entity.setFloat("HealF", p.hp);
        entity.setTag(KOMECampaignHealthProjection.ENTITY_TAG, p.write());
        NBTTagList entities = new NBTTagList(); entities.appendTag(entity); NBTTagCompound chunk = new NBTTagCompound(); chunk.setTag("Entities", entities);
        net.minecraft.world.chunk.storage.RegionFile region = new net.minecraft.world.chunk.storage.RegionFile(file);
        try (DataOutputStream out = region.getChunkDataOutputStream(0, 0)) { CompressedStreamTools.write(chunk, out); }
        region.close(); byte[] before = Files.readAllBytes(file.toPath());
        assertEquals(1, KOMERecoveryHealthEvidence.scan(root).size());
        try (KOMERecoveryJournalManager m = manager(root)) {
            KOMERecoveryStartup s = start(m, emptySection(), new KOMEWorldData());
            assertEquals(KOMERecoveryStartup.State.BLOCKED_CONTRADICTORY, s.state()); assertFalse(m.file().exists());
        }
        assertArrayEquals(before, Files.readAllBytes(file.toPath()));
    }
    @Test public void coherentUnloadedHealthProvesConsumptionWithoutRewritingOlderRootProjection() throws Exception {
        KOMERecoveryJournal wal = prepared(); KOMERecoveryJournalView.Entry e = preparation(wal); Prepare prepare = (Prepare)e.record;
        NBTTagCompound n = emptySection(); n.setTag("SavedHealth", projection(4, 3.25F, prepare.sourceTransitionId, null, false).write());
        KOMECampaignHealthProjection physical = projection(5, prepare.targetHp, prepare.recoveryTransitionId, fence(e), false);
        KOMERecoveryStartupPlan plan = KOMERecoveryStartupPlan.build(n, wal.view(), Collections.singletonList(physical));
        assertEquals(KOMERecoveryStartupPlan.Adoption.CONSUMED, plan.preparations.get(prepare.grantIdentity()));
        assertEquals(n.getTag("SavedHealth"), plan.candidate.getTag("SavedHealth"));
    }
    @Test public void healthContradictionQuarantinesOnlyTargetAndItsConflicts() throws Exception {
        KOMERecoveryJournal wal = prepared(); KOMERecoveryJournalView.Entry e = preparation(wal); Prepare prepare = (Prepare)e.record;
        NBTTagCompound n = KOMEConflictPersistence.write(conflict(2));
        n.setTag("SavedHealth", projection(6, 2, UUID.randomUUID(), null, false).write());
        KOMERecoveryStartupPlan plan = KOMERecoveryStartupPlan.build(n, wal.view());
        assertEquals(Collections.singleton(RIDER), plan.quarantinedTargets.keySet());
        assertTrue(plan.quarantinedConflicts.contains("CF1")); assertFalse(plan.quarantinedConflicts.contains("CF2"));
        assertEquals(KOMERecoveryStartupPlan.Adoption.PENDING, plan.preparations.get(prepare.grantIdentity()));
    }
    @Test public void stolenPhysicalAliasQuarantinesItsDurableOwnerAsWellAsTheFalseClaimant() throws Exception {
        KOMERecoveryJournal wal = prepared(); UUID falseTarget = UUID.randomUUID();
        KOMERecoveryStartupPlan plan = KOMERecoveryStartupPlan.build(emptySection(), wal.view(), Collections.singletonList(
            new KOMECampaignHealthProjection(falseTarget, Component.RIDER, RIDER_ALIAS, 3, 0, UUID.randomUUID(), false, null)));
        assertTrue(plan.quarantinedTargets.containsKey(RIDER)); assertTrue(plan.quarantinedTargets.containsKey(falseTarget));
        assertTrue(plan.quarantinedConflicts.contains("CF1"));
    }
    @Test public void sameRevisionCopiesCannotDisagreeEvenWhenBothAreNewerThanPrepare() throws Exception {
        KOMERecoveryJournal wal = prepared(); KOMERecoveryJournalView.Entry e = preparation(wal); UUID token = UUID.randomUUID();
        KOMERecoveryStartupPlan plan = KOMERecoveryStartupPlan.build(emptySection(), wal.view(), Arrays.asList(
            projection(6, 1, token, fence(e), false), projection(7, 4, UUID.randomUUID(), fence(e), false),
            projection(6, 2, token, fence(e), false)));
        assertTrue(plan.quarantinedTargets.containsKey(RIDER));
    }
    @Test public void verifiedLineageRestoresAliasesWithoutReassigningMountsOrChangingHp() throws Exception {
        KOMERecoveryJournal wal = prepared(); KOMERecoveryJournalView.Entry e = preparation(wal); Prepare prepare = (Prepare)e.record;
        UUID replacement = UUID.randomUUID();
        append(wal, new Lineage(prepare.binding, RIDER_ALIAS, replacement, UUID.randomUUID(), 0, 5,
            prepare.recoveryTransitionId, new Fence(JOURNAL, e.sequence, prepare.recoveryTransitionId)));
        KOMERecoveryStartupPlan plan = KOMERecoveryStartupPlan.build(emptySection(), wal.view(),
            Collections.singletonList(projection(5, prepare.targetHp, prepare.recoveryTransitionId, fence(e), false)));
        KOMEOrdinaryBattleRecoveryRegistry registry = KOMEConflictPersistence.readSection(plan.candidate).recovery;
        assertEquals(replacement, registry.target(RIDER).physicalId); assertTrue(registry.target(RIDER).aliases.contains(RIDER_ALIAS));
        assertEquals(MOUNT_ALIAS, registry.target(MOUNT).physicalId); assertTrue(plan.quarantinedTargets.isEmpty());
        assertEquals(GrantState.PENDING, registry.get("CF1","CF1:E1").grants.get("RIDER:" + RIDER).state);
    }
    @Test public void repeatedRepairIsIdempotentAndDoesNotReportUnchangedEpisodes() throws Exception {
        KOMERecoveryJournal wal = prepared(); KOMERecoveryStartupPlan first = KOMERecoveryStartupPlan.build(emptySection(), wal.view());
        KOMERecoveryStartupPlan second = KOMERecoveryStartupPlan.build(first.candidate, wal.view());
        assertEquals(first.candidate, second.candidate); assertEquals(0, second.repairedEpisodes); assertFalse(second.changed);
    }
    @Test public void orphanWalAndIncompleteTailNeverBecomeReady() throws Exception {
        for (boolean orphan : new boolean[]{true, false}) {
            File root = temporary.newFolder(); File file;
            try (KOMERecoveryJournalManager m = manager(root)) { m.bootstrap(); file = m.file(); }
            if (orphan) Files.delete(new File(new File(root,"data"), KOMERecoveryJournalManager.MARKER_NAME).toPath());
            else Files.write(file.toPath(), new byte[]{1,2,3}, StandardOpenOption.APPEND);
            try (KOMERecoveryJournalManager m = manager(root)) {
                KOMERecoveryStartup s = start(m, emptySection(), new KOMEWorldData());
                assertNotEquals(KOMERecoveryStartup.State.READY, s.state());
            }
        }
    }
    @Test public void staleActiveEpisodeIsQuarantinedWithoutChangingRevisionOrCompletingTimer() throws Exception {
        KOMEConflictService conflicts = new KOMEConflictService();
        KOMEConflictRecord c = conflicts.start("T101", KOMEConflictRecord.State.ORDINARY,
            KOMEConflictContracts.ExpectedConflict.absent(), Collections.<KOMEConflictContracts.GarrisonSeed>emptyList(),
            new KOMEConflictContracts.Context(1, "test", "start")).record;
        c = conflicts.checkpointCombat("T101", KOMEConflictContracts.ExpectedConflict.at(c.getConflictId(), c.getRevision()),
            new KOMEConflictRecord.CombatEpisode(1, "CF1:E1", KOMEConflictRecord.EpisodeState.ACTIVE, 10L, null),
            new KOMEConflictRecord.TimerSlot(KOMEConflictRecord.TimerStatus.RUNNING, 60000, 0, 10L, "CF1:E1"),
            KOMEConflictRecord.TimerSlot.unstarted(), new KOMEConflictContracts.Context(10, "test", "episode")).record;
        EpisodeContext original = context(); Episode e = original.episode;
        Intent intent = new Intent(e.conflictId, e.episodeId, c.getRevision(), KOMEConflictRecord.State.ORDINARY,
            e.intent.kind, e.intent.expectedOwner, e.intent.winner, 5, e.intent.stage, e.intent.closedAt);
        EpisodeContext exact = new EpisodeContext(e.update(e.endedAt, e.actualHostileCombatObserved, e.participants, intent, e.grants),
            original.targets, 2, c.getRevision() + 1, 1, 5);
        File root = temporary.newFolder();
        try (KOMERecoveryJournalManager m = manager(root)) {
            m.bootstrap(); m.append(exact, 0, m.view().lastHash);
            KOMEWorldData data = new KOMEWorldData(); KOMERecoveryStartup s = start(m, KOMEConflictPersistence.write(conflicts), data);
            assertEquals(KOMERecoveryStartup.State.READY, s.state()); assertFalse(KOMERecoveryStartup.permits(data, c));
            KOMEConflictRecord live = data.getConflictService().get("T101");
            assertEquals(c.getRevision(), live.getRevision()); assertEquals(KOMEConflictRecord.TimerStatus.RUNNING, live.getResponseTimer().status);
            assertNotNull(data.getOrdinaryBattleRecovery().get("CF1", "CF1:E1"));
            assertFalse(data.getConflictService().checkpointCombat("T101", KOMEConflictContracts.ExpectedConflict.at(c.getConflictId(), c.getRevision()),
                new KOMEConflictRecord.CombatEpisode(2, "CF1:E2", KOMEConflictRecord.EpisodeState.ACTIVE, 40L, null),
                new KOMEConflictRecord.TimerSlot(KOMEConflictRecord.TimerStatus.RUNNING, 60000, 0, 40L, "CF1:E2"),
                KOMEConflictRecord.TimerSlot.unstarted(), new KOMEConflictContracts.Context(40, "test", "unsafe reuse")).isSuccess());
        }
    }
    @Test public void preparedSourceContradictionIsQuarantinedAndUnknownMountNeverInherits() throws Exception {
        KOMERecoveryJournal wal = prepared(); KOMERecoveryJournalView.Entry e = preparation(wal); Prepare p = (Prepare)e.record;
        rejects(() -> KOMERecoveryStartupPlan.reconcile(wal.view(), e, projection(4, 12, p.sourceTransitionId, null, false)));
        // A logical target cannot claim another component's known physical alias, even without a fence.
        rejects(() -> KOMERecoveryStartupPlan.validateFence(wal.view(), new KOMECampaignHealthProjection(UUID.randomUUID(),
            Component.MOUNT, MOUNT_ALIAS, 6, 0, UUID.randomUUID(), false, null)));
        rejects(() -> KOMERecoveryStartupPlan.validateFence(wal.view(), new KOMECampaignHealthProjection(MOUNT,
            Component.MOUNT, UUID.randomUUID(), 6, 0, UUID.randomUUID(), false, null)));
    }
    @Test public void productionRootLoadRepairsBeforeFirstTickAndReusesOnlyCurrentSession() throws Exception {
        File directory = temporary.newFolder(); File dataDirectory = new File(directory, "data"); assertTrue(dataDirectory.mkdir());
        KOMEWorldData saved = new KOMEWorldData(); saved.initializeIntegratedWorld();
        NBTTagCompound root = new NBTTagCompound(), payload = new NBTTagCompound(); saved.writeToNBT(payload); root.setTag("data", payload);
        try (OutputStream out = new FileOutputStream(new File(dataDirectory, "KOME_ServerRules.dat"))) { CompressedStreamTools.writeCompressed(root, out); }
        try (KOMERecoveryJournalManager m = manager(directory)) { m.bootstrap(); m.append(context(), 0, m.view().lastHash); }
        try (kome.common.KOMETestServerSession server = new kome.common.KOMETestServerSession()) {
            net.minecraft.world.WorldServer world = kome.common.KOMEAccessFixture.allocate(net.minecraft.world.WorldServer.class);
            java.lang.reflect.Field provider = net.minecraft.world.World.class.getDeclaredField("provider"); provider.setAccessible(true);
            provider.set(world, new net.minecraft.world.WorldProviderSurface());
            net.minecraft.world.storage.ISaveHandler handler = (net.minecraft.world.storage.ISaveHandler)java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{net.minecraft.world.storage.ISaveHandler.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getWorldDirectory")) return directory;
                    if (method.getName().equals("getMapFileFromName")) return new File(dataDirectory, args[0] + ".dat");
                    return null;
                });
            java.lang.reflect.Field saveHandler = net.minecraft.world.World.class.getDeclaredField("saveHandler"); saveHandler.setAccessible(true); saveHandler.set(world, handler);
            world.mapStorage = new net.minecraft.world.storage.MapStorage(handler); server.server.worldServers = new net.minecraft.world.WorldServer[]{world};
            KOMERecoveryStartup.beginSession(server.server);
            try {
                new KOMEEvents().onRecoveryRootLoad(new net.minecraftforge.event.world.WorldEvent.Load(world));
                KOMEWorldData loaded = KOMEWorldData.get(world);
                assertEquals(KOMERecoveryStartup.State.READY, loaded.recoveryStartup.state());
                assertNotNull(loaded.getOrdinaryBattleRecovery().get("CF1", "CF1:E1")); assertTrue(loaded.isDirty());
                assertEquals(2, loaded.getConflictService().getNextConflictSequence());
                assertSame(loaded, KOMEWorldData.get(world));
                assertEquals(1, KOMERecoveryJournalManager.forRoot(world, server.server).view().lastSequence);
            } finally { KOMERecoveryStartup.stopSession(); }
        }
    }
    @Test public void lineageHealthCannotRegressBeforeAnotherLineagePrepareOrTerminal() throws Exception {
        KOMERecoveryJournal wal = open(empty()); KOMERecoveryJournal.Acknowledgment c = append(wal, context());
        UUID alias = UUID.randomUUID(), transition = UUID.randomUUID();
        KOMERecoveryJournal.Acknowledgment lineage = append(wal, new Lineage(binding(c, Component.RIDER), RIDER_ALIAS,
            alias, UUID.randomUUID(), 0, 10, transition, null));
        rejects(() -> append(wal, new Lineage(binding(c, Component.RIDER), alias, UUID.randomUUID(), UUID.randomUUID(),
            lineage.sequence, 9, transition, null)));
        rejects(() -> append(wal, new Prepare(binding(c, Component.RIDER), RIDER, alias, lineage.sequence, 9, transition,
            3.25F, null, 10, UUID.randomUUID(), 8.25F, 5, 20, context().intentFingerprint())));
        rejects(() -> append(wal, new Terminal(binding(c, Component.RIDER), alias, 10, UUID.randomUUID(),
            Disposition.DEAD, 30, "unproven terminal transition", null)));
        assertEquals(2, wal.view().lastSequence);
    }
}
