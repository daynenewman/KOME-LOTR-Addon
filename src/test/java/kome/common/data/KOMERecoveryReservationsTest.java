package kome.common.data;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.lang.reflect.Field;
import kome.common.KOMEAccessFixture;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import static kome.common.data.KOMEOrdinaryBattleRecoveryRegistry.*;
import static kome.common.data.KOMERecoveryJournalRecord.*;
import static kome.common.data.KOMERecoveryJournalTestSupport.*;

public class KOMERecoveryReservationsTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    @Rule public KOMETileTestResources geometry = new KOMETileTestResources();
    private final class Fixture implements AutoCloseable {
        final KOMEAccessFixture access = new KOMEAccessFixture();
        final KOMEWorldData data = access.data;
        final EntityPig rider = new EntityPig(access.world), mount = new EntityPig(access.world);
        final KOMEHiredUnitRecord unit = new KOMEHiredUnitRecord();
        final Faults faults = new Faults();
        final KOMERecoveryJournalManager manager;
        final KOMERecoveryStartup startup = new KOMERecoveryStartup(net.minecraft.server.MinecraftServer.getServer());
        final Field singleton;
        final Object previous;
        Fixture() throws Exception {
            Field uuid = Entity.class.getDeclaredField("entityUniqueID"); uuid.setAccessible(true);
            uuid.set(rider, RIDER_ALIAS); uuid.set(mount, MOUNT_ALIAS);
            rider.setHealth(3.25F); mount.setHealth(3.25F);
            unit.entity = RIDER_ALIAS; unit.owner = UUID.randomUUID(); unit.currentTile = "T101";
            KOMEHiredUnitClassification.assignForCampaignWorkflow(unit);
            data.hiredUnits.put(unit.entity, unit);
            setHealth(Component.RIDER, projection(Component.RIDER, 4, 3.25F, new UUID(4,1), null));
            setHealth(Component.MOUNT, projection(Component.MOUNT, 4, 3.25F, new UUID(4,1), null));
            access.world.loadedEntityList.add(rider); access.world.loadedEntityList.add(mount);
            EpisodeContext c = context();
            data.getOrdinaryBattleRecovery().replaceFrom(KOMEOrdinaryBattleRecoveryRegistry.restore(c.targets,
                Collections.singletonMap(c.episode.episodeId, c.episode)));
            data.getConflictService().replaceFrom(new KOMEConflictService(2));
            manager = new KOMERecoveryJournalManager(temporary.newFolder(), new Object(), faults);
            startup.initialize(manager, KOMEConflictPersistence.write(data.getConflictService(),
                data.getJoinBattleDeploymentReceipts(), data.getOrdinaryBattleRecovery()), data);
            assertEquals(KOMERecoveryStartup.State.READY, startup.state());
            singleton = KOMERecoveryStartup.class.getDeclaredField("session"); singleton.setAccessible(true);
            previous = singleton.get(null); singleton.set(null, startup);
        }
        void setHealth(Component component, KOMECampaignHealthProjection p) {
            KOMECampaignHealth.updateProjection(unit, p);
            KOMECampaignHealthEntity.install(component == Component.RIDER ? rider : mount, p, unit, false);
        }
        KOMERecoveryReservations.Result prepare(Component component) {
            return KOMERecoveryReservations.prepare(data, "CF1", "CF1:E1", component,
                component == Component.RIDER ? RIDER : MOUNT, component == Component.RIDER ? rider : mount);
        }
        Grant grant(Component component) { return data.getOrdinaryBattleRecovery().get("CF1", "CF1:E1").grants.get(component + ":" + (component == Component.RIDER ? RIDER : MOUNT)); }
        void episode(Episode e) {
            data.getOrdinaryBattleRecovery().replaceFrom(KOMEOrdinaryBattleRecoveryRegistry.restore(
                data.getOrdinaryBattleRecovery().targets(), Collections.singletonMap(e.episodeId, e)));
        }
        @Override public void close() throws Exception { try { startup.stop(); } finally { singleton.set(null, previous); } }
    }
    private static KOMECampaignHealthProjection projection(Component component, long revision, float hp,
            UUID token, KOMECampaignHealthProjection.Fence fence) {
        return new KOMECampaignHealthProjection(component == Component.RIDER ? RIDER : MOUNT, component,
            component == Component.RIDER ? RIDER_ALIAS : MOUNT_ALIAS, hp, revision, token, false, fence);
    }
    private static final class Faults implements KOMERecoveryJournalIO.Factory {
        int walForces, reads, failAt = -1;
        Runnable beforeForce;
        boolean failWrite;
        public KOMERecoveryJournalIO open(File path, boolean create) throws IOException {
            final KOMERecoveryJournalIO io = KOMERecoveryJournalIO.FILES.open(path, create);
            final boolean wal = path.getName().endsWith(".wal");
            return new KOMERecoveryJournalIO() {
                public long size() throws IOException { return io.size(); }
                public int read(ByteBuffer b, long offset) throws IOException { if (wal) reads++; return io.read(b, offset); }
                public int write(ByteBuffer b, long offset) throws IOException {
                    if (wal && failWrite) throw new IOException("injected write failure"); return io.write(b, offset);
                }
                public void force() throws IOException {
                    if (wal) { walForces++; if (beforeForce != null) beforeForce.run();
                        if (walForces == failAt) throw new IOException("injected force failure"); }
                    io.force();
                }
                public void truncate(long length) throws IOException { io.truncate(length); }
                public boolean tryLock() throws IOException { return io.tryLock(); }
                public void close() throws IOException { io.close(); }
            };
        }
    }
    @Test public void loadedRiderAndMountReserveIndependentlyWithOneSealedContextAndNoHpEffect() throws Exception {
        try (Fixture f = new Fixture()) {
            NBTTagCompound health = (NBTTagCompound)f.unit.survivingHealth.copy();
            f.faults.beforeForce = () -> assertNull(f.grant(Component.RIDER).transition);
            KOMERecoveryReservations.Result rider = f.prepare(Component.RIDER); f.faults.beforeForce = null;
            KOMERecoveryReservations.Result mount = f.prepare(Component.MOUNT);
            assertEquals(KOMERecoveryReservations.Status.RESERVED, rider.status);
            assertEquals(3, f.manager.view().lastSequence); assertEquals(1, f.manager.view().contexts.size());
            Prepare p = (Prepare)rider.evidence.record;
            assertEquals(4, p.sourceRevision); assertEquals(5, p.recoveryRevision); assertEquals(8.25F, p.targetHp, 0);
            assertNotEquals(p.recoveryTransitionId, ((Prepare)mount.evidence.record).recoveryTransitionId);
            assertEquals(health, f.unit.survivingHealth); assertEquals(3.25F, f.rider.getHealth(), 0);
            assertNull(KOMECampaignHealthEntity.projection(f.rider).fence);
            assertEquals(GrantState.PENDING, f.grant(Component.RIDER).state);
            assertEquals(GrantState.PENDING, f.grant(Component.MOUNT).state);
        }
    }
    @Test public void retryReturnsExactForcedSequenceTokenAndFence() throws Exception {
        try (Fixture f = new Fixture()) {
            KOMERecoveryReservations.Result first = f.prepare(Component.RIDER), retry = f.prepare(Component.RIDER);
            assertSame(first.evidence, retry.evidence); assertEquals(2, f.manager.view().lastSequence);
            assertEquals(first.expectedFence.transitionId, retry.expectedFence.transitionId);
            assertEquals(first.evidence.sequence, retry.expectedFence.prepareSequence);
        }
    }
    @Test public void unavailableGrantWritesNeitherContextNorPrepare() throws Exception {
        try (Fixture f = new Fixture()) {
            f.access.world.loadedEntityList.remove(f.rider);
            assertEquals(KOMERecoveryReservations.Status.UNAVAILABLE, f.prepare(Component.RIDER).status);
            assertEquals(0, f.manager.view().lastSequence); assertNull(f.grant(Component.RIDER).transition);
        }
    }
    @Test public void incompleteLegacyAndUnopposedEpisodesCannotPrepare() throws Exception {
        for (Coverage coverage : Coverage.values()) try (Fixture f = new Fixture()) {
            Episode e = context().episode;
            f.episode(new Episode(e.conflictId, e.tileId, e.episodeId, e.startedAt, e.endedAt, coverage, false,
                e.participants, e.intent, Collections.<String,Grant>emptyMap()));
            assertEquals(KOMERecoveryReservations.Status.INELIGIBLE, f.prepare(Component.RIDER).status);
            assertEquals(0, f.manager.view().lastSequence);
        }
    }
    @Test public void ordinaryHireAndUnrelatedReplacementCannotObtainReservation() throws Exception {
        try (Fixture f = new Fixture()) {
            f.unit.assignPersistedUnitClass(KOMEHiredUnitClass.ORDINARY);
            assertEquals(KOMERecoveryReservations.Status.UNAVAILABLE, f.prepare(Component.RIDER).status);
            assertEquals(0, f.manager.view().lastSequence);
        }
        try (Fixture f = new Fixture()) {
            EntityPig unrelated = new EntityPig(f.access.world); f.access.world.loadedEntityList.add(unrelated);
            rejects(() -> KOMERecoveryReservations.prepare(f.data,"CF1","CF1:E1",Component.MOUNT,MOUNT,unrelated));
            assertEquals(0, f.manager.view().lastSequence);
        }
    }
    @Test public void maxHpCapsSnapshotAndRevisionOverflowHasNoWrites() throws Exception {
        try (Fixture f = new Fixture()) {
            f.rider.setHealth(8F); f.setHealth(Component.RIDER, projection(Component.RIDER,5,8F,UUID.randomUUID(),null));
            Prepare p = (Prepare)f.prepare(Component.RIDER).evidence.record;
            assertEquals(10F, p.maxHp, 0); assertEquals(10F, p.targetHp, 0); assertEquals(5F, p.recoveryAmount, 0);
            assertEquals(8F, f.rider.getHealth(), 0);
        }
        try (Fixture f = new Fixture()) {
            f.setHealth(Component.RIDER,projection(Component.RIDER,Long.MAX_VALUE-1,3.25F,new UUID(4,1),null));
            rejects(() -> f.prepare(Component.RIDER)); assertEquals(0, f.manager.view().lastSequence);
        }
    }
    @Test public void conflictingSealedContextFailsClosed() throws Exception {
        try (Fixture f = new Fixture()) {
            f.startup.append(context("CF1",1,6));
            rejects(() -> f.prepare(Component.RIDER)); assertEquals(1, f.manager.view().lastSequence);
            assertNull(f.grant(Component.RIDER).transition); assertFalse(KOMERecoveryStartup.permitsTarget(f.data,RIDER_ALIAS));
        }
    }
    @Test public void contextForceFailurePublishesNothingAndRetryRescansExactContext() throws Exception {
        try (Fixture f = new Fixture()) {
            f.faults.failAt = f.faults.walForces + 1;
            rejects(() -> f.prepare(Component.RIDER)); assertNull(f.grant(Component.RIDER).transition);
            assertEquals(KOMERecoveryJournal.State.POISONED, f.manager.state());
            int reads = f.faults.reads; assertEquals(KOMERecoveryReservations.Status.RESERVED,f.prepare(Component.RIDER).status);
            assertTrue(f.faults.reads > reads); assertEquals(2, f.manager.view().lastSequence);
        }
    }
    @Test public void prepareForceFailureRetryRescansAndReusesOriginalToken() throws Exception {
        try (Fixture f = new Fixture()) {
            f.faults.failAt = f.faults.walForces + 2;
            rejects(() -> f.prepare(Component.RIDER)); assertNull(f.grant(Component.RIDER).transition);
            assertEquals(KOMERecoveryJournal.State.POISONED, f.manager.state());
            KOMERecoveryReservations.Result retry = f.prepare(Component.RIDER);
            assertEquals(2, retry.evidence.sequence); assertEquals(2, f.manager.view().lastSequence);
            assertEquals(((Prepare)retry.evidence.record).recoveryTransitionId, f.grant(Component.RIDER).transition.token);
            assertEquals(3.25F, f.rider.getHealth(),0);
        }
    }
    @Test public void absentUncertainAppendNeverAllocatesReplacement() throws Exception {
        try (Fixture f = new Fixture()) {
            f.startup.append(context()); f.faults.failWrite = true;
            rejects(() -> f.prepare(Component.RIDER)); f.faults.failWrite = false;
            rejects(() -> f.prepare(Component.RIDER)); assertNull(f.grant(Component.RIDER).transition);
            assertEquals(1, f.manager.view().lastSequence); assertNotEquals(KOMERecoveryStartup.State.READY,f.startup.state());
        }
    }
    @Test public void exactAndDescendantConsumptionPreserveLaterHpAndRemainPendingForClosure() throws Exception {
        for (boolean descendant : new boolean[]{false,true}) try (Fixture f = new Fixture()) {
            KOMERecoveryReservations.Result first = f.prepare(Component.RIDER); Prepare p = (Prepare)first.evidence.record;
            KOMECampaignHealthProjection health = projection(Component.RIDER,5,p.targetHp,p.recoveryTransitionId,
                new KOMECampaignHealthProjection.Fence(first.expectedFence.journalId,first.evidence.sequence,p.recoveryTransitionId));
            if (descendant) health = health.change(2F,false);
            f.setHealth(Component.RIDER,health); NBTTagCompound before=(NBTTagCompound)f.unit.survivingHealth.copy();
            assertEquals(KOMERecoveryReservations.Status.CONSUMED,f.prepare(Component.RIDER).status);
            assertEquals(before,f.unit.survivingHealth); assertEquals(2,f.manager.view().lastSequence);
            assertEquals(GrantState.PENDING,f.grant(Component.RIDER).state);
            assertTrue(f.startup.permitsMutation(health));
        }
    }
    @Test public void equalContradictionAndNewerWithoutAncestryQuarantine() throws Exception {
        for (long revision : new long[]{5,6}) try (Fixture f = new Fixture()) {
            f.prepare(Component.RIDER);
            // An independently loaded projection bypasses publication's earlier ancestry rejection.
            KOMECampaignHealthEntity.install(f.rider,projection(Component.RIDER,revision,2F,UUID.randomUUID(),null),f.unit,false);
            rejects(() -> f.prepare(Component.RIDER)); assertFalse(KOMERecoveryStartup.permitsTarget(f.data,RIDER_ALIAS));
            assertEquals(2,f.manager.view().lastSequence);
        }
    }
    @Test public void pendingReservationGuardsOnlyItsTarget() throws Exception {
        try (Fixture f = new Fixture()) {
            f.prepare(Component.RIDER);
            assertFalse(f.startup.permitsMutation(KOMECampaignHealthEntity.projection(f.rider)));
            assertTrue(f.startup.permitsMutation(KOMECampaignHealthEntity.projection(f.mount)));
            assertEquals(KOMERecoveryReservations.Status.RESERVED,f.prepare(Component.RIDER).status);
        }
    }
    @Test public void readinessRequiredEvenForDetachedGameplayCall() throws Exception {
        try (Fixture f = new Fixture()) {
            f.startup.stop(); rejects(() -> f.prepare(Component.RIDER)); assertNull(f.grant(Component.RIDER).transition);
        }
    }
    @Test public void verifiedLineageIsForcedBeforeActivationAndSurvivesRestart() throws Exception {
        try (Fixture f = new Fixture()) {
            f.startup.append(context()); UUID next = UUID.randomUUID();
            KOMERecoveryStartup.stageAlias(KOMECampaignHealthEntity.projection(f.rider),next);
            f.faults.beforeForce = () -> assertEquals(RIDER_ALIAS,f.data.getOrdinaryBattleRecovery().target(RIDER).physicalId);
            KOMEOrdinaryBattleRecoveryService.reconstructed(f.data,RIDER_ALIAS,next,null,null); f.faults.beforeForce=null;
            assertEquals(next,f.data.getOrdinaryBattleRecovery().target(RIDER).physicalId);
            assertEquals(next, f.manager.view().currentPhysicalAliases.get(RIDER));
            f.manager.open(); assertEquals(next,f.manager.view().currentPhysicalAliases.get(RIDER));
            KOMERecoveryStartupPlan plan = KOMERecoveryStartupPlan.build(KOMEConflictPersistence.write(new KOMEConflictService()),f.manager.view());
            assertEquals(next,KOMEConflictPersistence.readSection(plan.candidate).recovery.target(RIDER).physicalId);
        }
    }
    @Test public void failedSpeculativeOrUnrelatedReconstructionWritesNoLineage() throws Exception {
        try (Fixture f = new Fixture()) {
            f.startup.append(context()); UUID speculative=UUID.randomUUID();
            KOMERecoveryStartup.stageAlias(KOMECampaignHealthEntity.projection(f.mount),speculative);
            assertEquals(1,f.manager.view().lastSequence); // rollback before verified activation is purely detached
            rejects(() -> KOMEOrdinaryBattleRecoveryService.reconstructed(f.data,RIDER_ALIAS,UUID.randomUUID(),MOUNT_ALIAS,UUID.randomUUID()));
            assertEquals(1,f.manager.view().lastSequence); assertEquals(MOUNT_ALIAS,f.data.getOrdinaryBattleRecovery().target(MOUNT).physicalId);
        }
    }
    @Test public void mountLineagePreservesLogicalIdentityAndRollbackAppendsReverseEdge() throws Exception {
        try (Fixture f = new Fixture()) {
            f.startup.append(context()); UUID rider=UUID.randomUUID(),mount=UUID.randomUUID();
            KOMEOrdinaryBattleRecoveryRegistry before=KOMEOrdinaryBattleRecoveryService.copy(f.data);
            KOMERecoveryStartup.stageAlias(KOMECampaignHealthEntity.projection(f.rider),rider);
            KOMERecoveryStartup.stageAlias(KOMECampaignHealthEntity.projection(f.mount),mount);
            KOMEOrdinaryBattleRecoveryService.reconstructed(f.data,RIDER_ALIAS,rider,MOUNT_ALIAS,mount);
            assertEquals(MOUNT,f.data.getOrdinaryBattleRecovery().targetForPhysical(mount).id);
            KOMEOrdinaryBattleRecoveryService.rollbackReconstruction(f.data,before);
            assertEquals(5,f.manager.view().lastSequence); assertEquals(MOUNT_ALIAS,f.manager.view().currentPhysicalAliases.get(MOUNT));
            assertTrue(f.data.getOrdinaryBattleRecovery().target(MOUNT).aliases.contains(mount));
            f.manager.open(); assertEquals(MOUNT_ALIAS,f.manager.view().currentPhysicalAliases.get(MOUNT));
        }
    }
    @Test public void lineageForceFailureNeverActivatesReplacement() throws Exception {
        try (Fixture f = new Fixture()) {
            f.startup.append(context()); UUID next=UUID.randomUUID();
            KOMERecoveryStartup.stageAlias(KOMECampaignHealthEntity.projection(f.rider),next);
            f.faults.failAt=f.faults.walForces+1;
            rejects(() -> KOMEOrdinaryBattleRecoveryService.reconstructed(f.data,RIDER_ALIAS,next,null,null));
            assertEquals(RIDER_ALIAS,f.data.getOrdinaryBattleRecovery().target(RIDER).physicalId);
            assertEquals(KOMERecoveryJournal.State.POISONED,f.manager.state());
        }
    }
    @Test public void terminalCancelsUnconsumedPrepareWithoutPretendingAdoption() throws Exception {
        try (Fixture f = new Fixture()) {
            f.prepare(Component.MOUNT);
            KOMEOrdinaryBattleRecoveryService.terminal(f.data,MOUNT_ALIAS,Disposition.DESTROYED,30,"Destroyed");
            Terminal terminal=(Terminal)f.manager.view().terminals.get(MOUNT).record;
            assertNull(terminal.fence); assertEquals(5,terminal.healthRevision);
            assertNotEquals(f.grant(Component.MOUNT).transition.token,terminal.transitionId);
            assertEquals(GrantState.TERMINAL,f.grant(Component.MOUNT).state);
            assertEquals(KOMERecoveryReservations.Status.TERMINAL,f.prepare(Component.MOUNT).status);
            f.manager.open(); assertTrue(f.manager.view().terminalTargets.contains(MOUNT));
            assertEquals(3.25F,f.mount.getHealth(),0);
        }
    }
    @Test public void terminalForceFailurePreservesAuthorityAndUnloadWritesNoTerminal() throws Exception {
        try (Fixture f = new Fixture()) {
            f.prepare(Component.MOUNT); f.access.world.loadedEntityList.remove(f.mount);
            assertTrue(f.manager.view().terminals.isEmpty());
            f.faults.failAt=f.faults.walForces+1;
            rejects(() -> KOMEOrdinaryBattleRecoveryService.terminal(f.data,MOUNT_ALIAS,Disposition.DESTROYED,30,"Destroyed"));
            assertFalse(f.data.getOrdinaryBattleRecovery().target(MOUNT).terminal);
            assertEquals(GrantState.PENDING,f.grant(Component.MOUNT).state);
        }
    }
    @Test public void versionsAndJoinBattlePersistenceAreUnchanged() throws Exception {
        try (Fixture f = new Fixture()) {
            NBTTagCompound before=KOMEConflictPersistence.write(f.data.getConflictService(),f.data.getJoinBattleDeploymentReceipts(),f.data.getOrdinaryBattleRecovery());
            f.prepare(Component.RIDER);
            NBTTagCompound after=KOMEConflictPersistence.write(f.data.getConflictService(),f.data.getJoinBattleDeploymentReceipts(),f.data.getOrdinaryBattleRecovery());
            assertEquals(3,after.getInteger(KOMEConflictPersistence.SCHEMA_KEY)); assertEquals(12,KOMEWorldData.KOME_DATA_SCHEMA_VERSION);
            assertEquals(before.getTag(KOMEConflictPersistence.JOIN_BATTLE_RECEIPTS_KEY),after.getTag(KOMEConflictPersistence.JOIN_BATTLE_RECEIPTS_KEY));
            assertEquals(before.getLong(KOMEConflictPersistence.JOIN_BATTLE_SEQUENCE_KEY),after.getLong(KOMEConflictPersistence.JOIN_BATTLE_SEQUENCE_KEY));
            assertEquals(Disposition.FORMAL_RETREAT,context().episode.participants.get(RIDER).disposition);
        }
    }
    @Test public void nativeHealthBridgeBlocksReservedRiderButAllowsOtherMount() throws Exception {
        try (kome.common.KOMETestServerSession server = new kome.common.KOMETestServerSession(); Fixture f = new Fixture()) {
            net.minecraft.world.WorldServer world = KOMEAccessFixture.allocate(net.minecraft.world.WorldServer.class);
            Field provider = net.minecraft.world.World.class.getDeclaredField("provider"); provider.setAccessible(true);
            provider.set(world,new net.minecraft.world.WorldProviderSurface()); world.mapStorage=f.access.world.mapStorage;
            world.loadedEntityList=f.access.world.loadedEntityList; server.server.worldServers=new net.minecraft.world.WorldServer[]{world};
            f.rider.worldObj=world; f.mount.worldObj=world;
            f.prepare(Component.RIDER);
            rejects(() -> KOMECampaignHealthEntity.beforeChange(f.rider,2F));
            assertEquals(3.25F,f.rider.getHealth(),0);
            KOMECampaignHealthEntity.beforeChange(f.mount,2F); f.mount.setHealth(2F); KOMECampaignHealthEntity.changed(f.mount);
            assertEquals(2F,KOMECampaignHealthEntity.projection(f.mount).hp,0);
            assertEquals(5,KOMECampaignHealthEntity.projection(f.mount).revision);
        }
    }
    @Test public void pendingReservationCannotReconstructOrReleaseGuardByRekeying() throws Exception {
        try (Fixture f = new Fixture()) {
            f.prepare(Component.RIDER); UUID next=UUID.randomUUID();
            KOMERecoveryStartup.stageAlias(KOMECampaignHealthEntity.projection(f.rider),next);
            rejects(() -> KOMEOrdinaryBattleRecoveryService.reconstructed(f.data,RIDER_ALIAS,next,null,null));
            assertEquals(RIDER_ALIAS,f.data.getOrdinaryBattleRecovery().target(RIDER).physicalId);
            assertEquals(2,f.manager.view().lastSequence);
        }
    }
    @Test public void consumedPrepareAllowsVerifiedMountLineageAndLaterTerminal() throws Exception {
        try (Fixture f = new Fixture()) {
            KOMERecoveryReservations.Result reservation=f.prepare(Component.MOUNT); Prepare p=(Prepare)reservation.evidence.record;
            KOMECampaignHealthProjection health=projection(Component.MOUNT,5,p.targetHp,p.recoveryTransitionId,
                new KOMECampaignHealthProjection.Fence(reservation.expectedFence.journalId,reservation.evidence.sequence,p.recoveryTransitionId));
            f.setHealth(Component.MOUNT,health); UUID next=UUID.randomUUID();
            KOMERecoveryStartup.stageAlias(health,next);
            KOMEOrdinaryBattleRecoveryService.reconstructed(f.data,RIDER_ALIAS,RIDER_ALIAS,MOUNT_ALIAS,next);
            KOMECampaignHealth.reconstructed(f.unit,RIDER_ALIAS,RIDER_ALIAS,MOUNT_ALIAS,next);
            assertEquals(MOUNT,f.data.getOrdinaryBattleRecovery().targetForPhysical(next).id);
            KOMEOrdinaryBattleRecoveryService.terminal(f.data,next,Disposition.DEAD,40,"Death after adoption");
            Terminal terminal=(Terminal)f.manager.view().terminals.get(MOUNT).record;
            assertEquals(6,terminal.healthRevision); assertEquals(reservation.evidence.sequence,terminal.fence.prepareSequence);
            f.manager.open(); assertEquals(KOMERecoveryJournal.State.READY,f.manager.state());
        }
    }
    @Test public void partialLineageFailureKeepsAcknowledgedEdgeWhenRollbackCannotForceReverse() throws Exception {
        try (Fixture f = new Fixture()) {
            f.startup.append(context()); UUID rider=UUID.randomUUID(),mount=UUID.randomUUID();
            KOMEOrdinaryBattleRecoveryRegistry before=KOMEOrdinaryBattleRecoveryService.copy(f.data);
            KOMERecoveryStartup.stageAlias(KOMECampaignHealthEntity.projection(f.rider),rider);
            KOMERecoveryStartup.stageAlias(KOMECampaignHealthEntity.projection(f.mount),mount);
            f.faults.failAt=f.faults.walForces+2;
            rejects(() -> KOMEOrdinaryBattleRecoveryService.reconstructed(f.data,RIDER_ALIAS,rider,MOUNT_ALIAS,mount));
            assertEquals(rider,f.data.getOrdinaryBattleRecovery().target(RIDER).physicalId);
            assertEquals(MOUNT_ALIAS,f.data.getOrdinaryBattleRecovery().target(MOUNT).physicalId);
            KOMEOrdinaryBattleRecoveryService.rollbackReconstruction(f.data,before);
            assertEquals(rider,f.data.getOrdinaryBattleRecovery().target(RIDER).physicalId);
            assertFalse(KOMERecoveryStartup.permitsTarget(f.data,RIDER_ALIAS));
            assertNotEquals(KOMERecoveryStartup.State.READY,f.startup.state());
        }
    }
    @Test public void missingIntentAndQuarantinedConflictCannotPrepare() throws Exception {
        try (Fixture f = new Fixture()) {
            Episode e=context().episode; f.episode(e.update(e.endedAt,true,e.participants,null,e.grants));
            assertEquals(KOMERecoveryReservations.Status.INELIGIBLE,f.prepare(Component.RIDER).status);
            assertEquals(0,f.manager.view().lastSequence);
        }
        try (Fixture f = new Fixture()) {
            f.startup.quarantine(RIDER,"Contradictory Phase 3 intent");
            rejects(() -> f.prepare(Component.MOUNT)); assertEquals(0,f.manager.view().lastSequence);
        }
    }
    @Test public void incompatiblePersistedDuplicateCannotAllocateReplacement() throws Exception {
        try (Fixture f = new Fixture()) {
            f.prepare(Component.RIDER); Episode e=f.data.getOrdinaryBattleRecovery().get("CF1","CF1:E1");
            Grant g=f.grant(Component.RIDER); Transition old=g.transition;
            Map<String,Grant> grants=new TreeMap<String,Grant>(e.grants);
            grants.put(g.key(),new Grant(g.participantId,g.targetId,g.component,g.amount,GrantState.PENDING,
                new Transition(UUID.randomUUID(),old.physicalId,old.sourceRevision,old.sourceHealth,old.targetHealth,old.maxHealth),""));
            f.episode(e.update(e.endedAt,true,e.participants,e.intent,grants));
            rejects(() -> f.prepare(Component.RIDER)); assertEquals(2,f.manager.view().lastSequence);
        }
    }
    @Test public void sealedContextAllowsMonotoneIntentProgressButNotChangedOwnershipIntent() throws Exception {
        try (Fixture f = new Fixture()) {
            f.prepare(Component.RIDER); Episode e=f.data.getOrdinaryBattleRecovery().get("CF1","CF1:E1");
            f.data.getOrdinaryBattleRecovery().advance(e.conflictId,e.episodeId,Stage.CLOSURE_PENDING);
            assertEquals(KOMERecoveryReservations.Status.RESERVED,f.prepare(Component.MOUNT).status);
            assertEquals(1,f.manager.view().contexts.size());
        }
        try (Fixture f = new Fixture()) {
            f.prepare(Component.RIDER); Episode e=f.data.getOrdinaryBattleRecovery().get("CF1","CF1:E1"); Intent i=e.intent;
            Intent changed=new Intent(i.conflictId,i.episodeId,i.expectedRevision,i.expectedState,i.kind,"rohan",i.winner,i.recoveryAmount,i.stage,i.closedAt);
            f.episode(e.update(e.endedAt,true,e.participants,changed,e.grants));
            rejects(() -> f.prepare(Component.MOUNT)); assertEquals(2,f.manager.view().lastSequence);
        }
    }
    @Test public void terminalCancellationReplaysOverStalePendingConflictData() throws Exception {
        try (Fixture f = new Fixture()) {
            NBTTagCompound stale=KOMEConflictPersistence.write(f.data.getConflictService(),f.data.getJoinBattleDeploymentReceipts(),f.data.getOrdinaryBattleRecovery());
            f.prepare(Component.MOUNT);
            KOMEOrdinaryBattleRecoveryService.terminal(f.data,MOUNT_ALIAS,Disposition.DESTROYED,30,"Destroyed");
            KOMERecoveryStartupPlan plan=KOMERecoveryStartupPlan.build(stale,f.manager.view());
            KOMEOrdinaryBattleRecoveryRegistry recovered=KOMEConflictPersistence.readSection(plan.candidate).recovery;
            assertTrue(recovered.target(MOUNT).terminal);
            assertEquals(GrantState.TERMINAL,recovered.get("CF1","CF1:E1").grants.get("MOUNT:"+MOUNT).state);
        }
    }
    @Test public void retiredMountBeforeFirstContextDoesNotBlockSurvivingRider() throws Exception {
        try (Fixture f = new Fixture()) {
            KOMEOrdinaryBattleRecoveryService.terminal(f.data,MOUNT_ALIAS,Disposition.DESTROYED,30,"Mount destroyed before reservation");
            assertEquals(0,f.manager.view().lastSequence);
            assertEquals(KOMERecoveryReservations.Status.RESERVED,f.prepare(Component.RIDER).status);
            EpisodeContext sealed=(EpisodeContext)f.manager.view().contexts.get("CF1:E1").record;
            assertTrue(sealed.targets.get(MOUNT).terminal);
            assertFalse(sealed.episode.grants.containsKey("MOUNT:"+MOUNT));
            assertEquals(GrantState.TERMINAL,f.grant(Component.MOUNT).state);
            NBTTagCompound saved=KOMEConflictPersistence.write(f.data.getConflictService(),f.data.getJoinBattleDeploymentReceipts(),f.data.getOrdinaryBattleRecovery());
            KOMERecoveryStartupPlan plan=KOMERecoveryStartupPlan.build(saved,f.manager.view());
            assertEquals("Mount destroyed before reservation",KOMEConflictPersistence.readSection(plan.candidate).recovery
                .get("CF1","CF1:E1").grants.get("MOUNT:"+MOUNT).terminalReason);
            f.manager.open(); assertEquals(KOMERecoveryJournal.State.READY,f.manager.state());
        }
    }
    @Test public void retryAfterUnloadRetainsReservationWithoutAdvertisingExecutableWork() throws Exception {
        try (Fixture f = new Fixture()) {
            KOMERecoveryReservations.Result first=f.prepare(Component.RIDER);
            f.access.world.loadedEntityList.remove(f.rider);
            KOMERecoveryReservations.Result retry=f.prepare(Component.RIDER);
            assertEquals(KOMERecoveryReservations.Status.UNAVAILABLE,retry.status);
            assertSame(first.evidence,retry.evidence); assertEquals(2,f.manager.view().lastSequence);
        }
    }
    @Test public void duplicateLoadedAliasCannotCreateReservation() throws Exception {
        try (Fixture f = new Fixture()) {
            EntityPig duplicate=new EntityPig(f.access.world);
            Field uuid=Entity.class.getDeclaredField("entityUniqueID"); uuid.setAccessible(true); uuid.set(duplicate,RIDER_ALIAS);
            f.access.world.loadedEntityList.add(duplicate);
            rejects(() -> f.prepare(Component.RIDER)); assertEquals(0,f.manager.view().lastSequence);
        }
    }
}
