package kome.common.data;

import kome.common.KOMEAccessFixture;
import kome.common.KOMEReflection;
import lotr.common.fac.LOTRFaction;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static kome.common.data.KOMEConflictContracts.*;
import static kome.common.data.KOMEConflictRecord.*;
import static kome.common.data.KOMEJoinBattleDeploymentReceipt.*;
import static org.junit.Assert.*;

public class KOMEJoinBattleEgressServiceTest {
    @Rule public final KOMETileTestResources geometry = new KOMETileTestResources();

    @After public void clearTokens() {
        KOMEJoinBattleActionTokenService.INSTANCE.clearForTests();
        KOMEJoinBattleEgressService.INSTANCE.resetSession();
    }

    @Test public void successfulConflictEndTransitionsDeployedReceiptWithoutWithdrawal() throws Exception {
        Fixture f = new Fixture(); f.publish(deployed(f, false));
        KOMEConflictRecord.PlayerParticipation before = f.record.getPlayers().get(f.playerId);
        KOMEConflictService.EndResult ended = f.data.getConflictService().endWithMovementHandoff(
            f.data, "T100", f.expected(), f.context(50L), KOMEConflictService.EndSource.ADMIN_FORCED);
        assertTrue(ended.conflictResult.reason, ended.isSuccess());
        KOMEJoinBattleDeploymentReceipt receipt = f.receipt();
        assertEquals(KOMEJoinBattleDeploymentReceipt.State.PENDING_EGRESS, receipt.getState());
        assertEquals(Long.valueOf(50L), receipt.getEgressRequestedAtMillis());
        assertEquals(PlayerStatus.ACTIVE, ended.conflictResult.record.getPlayers().get(f.playerId).status);
        assertEquals(before.startedAtMillis,
            ended.conflictResult.record.getPlayers().get(f.playerId).startedAtMillis);
    }

    @Test public void onlinePlayerReturnsToExactPoseClosesAndNoLongerBlocksNewEntry() throws Exception {
        Fixture f = new Fixture(); f.publish(deployed(f, false));
        FakePhysical physical = new FakePhysical(f.data); physical.online(f.access.player);
        assertEquals(2, KOMEJoinBattleEgressService.INSTANCE.onConflictEnded(f.data,
            f.record.getConflictId(), 50L, physical));
        KOMEJoinBattleDeploymentReceipt closed = f.receipt();
        assertEquals(KOMEJoinBattleDeploymentReceipt.State.CLOSED, closed.getState());
        assertEquals(ClosureOutcome.EGRESS_COMPLETED, closed.getClosureOutcome());
        assertEquals(f.returnPose, physical.lastReturnPose);
        assertEquals(1, physical.egresses);

        f.replaceWithEligibleConflict(60L);
        FakeEntryPhysical entry = new FakeEntryPhysical();
        String token = KOMEJoinBattleActionTokenService.INSTANCE.issue(f.playerId,
            KOMEJoinBattleService.INSTANCE.evaluate(f.data, f.access.player, "T100"));
        KOMEJoinBattleEntryService.Result joined = KOMEJoinBattleEntryService.INSTANCE.enter(
            f.data, f.access.player, new KOMEJoinBattleEntryService.Request("T100",
                f.record.getConflictId(), f.record.getRevision(), "C1", token), entry);
        assertEquals(KOMEJoinBattleEntryService.Status.DEPLOYED, joined.status);
        assertEquals(1, entry.deployments);
    }

    @Test public void offlineObligationSurvivesRestartAndLoginCompletesIt() throws Exception {
        Fixture f = new Fixture(); f.data.initializeIntegratedWorld(); f.publish(deployed(f, false));
        FakePhysical offline = new FakePhysical(f.data);
        KOMEJoinBattleEgressService.INSTANCE.onConflictEnded(f.data, f.record.getConflictId(), 50L, offline);
        assertEquals(KOMEJoinBattleDeploymentReceipt.State.PENDING_EGRESS, f.receipt().getState());

        NBTTagCompound saved = new NBTTagCompound(); f.data.writeToNBT(saved);
        KOMEWorldData restored = new KOMEWorldData("egress-restored"); restored.readFromNBT(saved);
        assertEquals(KOMEJoinBattleDeploymentReceipt.State.PENDING_EGRESS,
            restored.getJoinBattleDeploymentReceipts().get("JB1").getState());
        FakePhysical login = new FakePhysical(restored); login.online(f.access.player);
        assertTrue(KOMEJoinBattleEgressService.INSTANCE.reconcilePlayer(restored,
            f.access.player, 60L, login));
        assertEquals(KOMEJoinBattleDeploymentReceipt.State.CLOSED,
            restored.getJoinBattleDeploymentReceipts().get("JB1").getState());
    }

    @Test public void pendingEntryRemainsOwnedByEntryRecoveryAtConflictEnd() throws Exception {
        Fixture f = new Fixture(); f.publish(pending(f, false));
        FakePhysical physical = new FakePhysical(f.data); physical.online(f.access.player);
        KOMEJoinBattleEgressService.INSTANCE.onConflictEnded(f.data, f.record.getConflictId(), 50L, physical);
        KOMEJoinBattleDeploymentReceipt pending = f.receipt();
        assertEquals(KOMEJoinBattleDeploymentReceipt.State.PENDING_ENTRY, pending.getState());
        assertEquals(0, physical.egresses);
        KOMEJoinBattleEgressService.INSTANCE.onConflictEnded(f.data, f.record.getConflictId(), 60L, physical);
        assertSame(pending, f.receipt()); assertEquals(0, physical.egresses);
    }

    @Test public void exactConflictIdentityScopesReconciliation() throws Exception {
        Fixture f = new Fixture(); f.publish(deployed(f, false));
        FakePhysical physical = new FakePhysical(f.data); physical.online(f.access.player);
        assertEquals(0, KOMEJoinBattleEgressService.INSTANCE.onConflictEnded(
            f.data, "CF999", 50L, physical));
        assertEquals(KOMEJoinBattleDeploymentReceipt.State.DEPLOYED, f.receipt().getState());
        assertEquals(0, physical.egresses);

        KOMEConflictRecord ended = ok(f.data.getConflictService().end("T100",
            f.expected(), f.context(50L)));
        f.record = ok(f.data.getConflictService().start("T100", KOMEConflictRecord.State.ORDINARY,
            ExpectedConflict.at(ended.getConflictId(), ended.getRevision()),
            Collections.<GarrisonSeed>emptyList(), f.context(60L)));
        assertTrue(KOMEJoinBattleEgressService.INSTANCE.reconcilePlayer(
            f.data, f.access.player, 70L, physical));
        assertEquals("Old CF identity remains a recoverable obligation", KOMEJoinBattleDeploymentReceipt.State.CLOSED,
            f.receipt().getState());
        assertEquals("CF1", f.receipt().getConflictId());
        assertEquals("CF2", f.record.getConflictId());
    }

    @Test public void physicalFailureLeavesDurablePendingEgressForRetry() throws Exception {
        Fixture f = new Fixture(); f.publish(deployed(f, false));
        FakePhysical physical = new FakePhysical(f.data); physical.online(f.access.player);
        physical.egressSucceeds = false;
        KOMEJoinBattleEgressService.INSTANCE.onConflictEnded(f.data, f.record.getConflictId(), 50L, physical);
        assertEquals(KOMEJoinBattleDeploymentReceipt.State.PENDING_EGRESS, f.receipt().getState());
        assertEquals(1, physical.egresses);
        physical.egressSucceeds = true;
        assertTrue(KOMEJoinBattleEgressService.INSTANCE.reconcilePlayer(
            f.data, f.access.player, 60050L, physical));
        assertEquals(KOMEJoinBattleDeploymentReceipt.State.CLOSED, f.receipt().getState());
        assertEquals(2, physical.egresses);
    }

    @Test public void pendingEgressReportsSpecificPreparationReasonAndRetries() throws Exception {
        Fixture f = new Fixture(); f.publish(deployed(f, false));
        FakePhysical physical = new FakePhysical(f.data); physical.online(f.access.player);
        physical.preparation = KOMEJoinBattleEgressService.Preparation.unavailable(
            "Saved return chunk could not be loaded.");
        KOMEJoinBattleEgressService.CompletionResult pending =
            KOMEJoinBattleEgressService.INSTANCE.requestAndCompleteDetailed(f.data,
                f.access.player, "JB1", 50L, "Formal retreat test.", physical);
        assertEquals(KOMEJoinBattleEgressService.Completion.PENDING, pending.completion);
        assertEquals("Saved return chunk could not be loaded.", pending.reason);
        assertEquals(KOMEJoinBattleDeploymentReceipt.State.PENDING_EGRESS,
            f.receipt().getState());

        physical.preparation = KOMEJoinBattleEgressService.Preparation.playerOnly(null);
        KOMEJoinBattleEgressService.CompletionResult recovered =
            KOMEJoinBattleEgressService.INSTANCE.requestAndCompleteDetailed(f.data,
                f.access.player, "JB1", 60050L, "Formal retreat test.", physical);
        assertEquals(KOMEJoinBattleEgressService.Completion.CLOSED, recovered.completion);
    }

    @Test public void mountedReturnSnapshotsBeforeMoveAndRecordsExactMountDisposition() throws Exception {
        Fixture f = new Fixture(); f.publish(deployed(f, true));
        FakePhysical physical = new FakePhysical(f.data); physical.online(f.access.player);
        physical.preparation = mountedPreparation();
        physical.resultDisposition = MountDisposition.RETURNED_WITH_PLAYER;
        KOMEJoinBattleEgressService.INSTANCE.onConflictEnded(f.data, f.record.getConflictId(), 50L, physical);
        KOMEJoinBattleDeploymentReceipt closed = f.receipt();
        assertTrue(physical.sawDurableEgressSnapshot);
        assertEquals(KOMEJoinBattleDeploymentReceipt.State.CLOSED, closed.getState());
        assertEquals(MountTransferPhase.TERMINAL, closed.getMountTransferPhase());
        assertEquals(MountDisposition.RETURNED_WITH_PLAYER, closed.getMountDisposition());
        assertNull(closed.getTemporaryMountNbt());
    }

    @Test public void dismountedDeadOrUnavailableMountNeverPreventsPlayerOnlyEgress() throws Exception {
        for (MountDisposition disposition : new MountDisposition[] {
                MountDisposition.SEPARATED, MountDisposition.DEAD, MountDisposition.UNAVAILABLE}) {
            Fixture f = new Fixture(); f.publish(deployed(f, true));
            FakePhysical physical = new FakePhysical(f.data); physical.online(f.access.player);
            physical.preparation = KOMEJoinBattleEgressService.Preparation.playerOnly(disposition);
            physical.resultDisposition = disposition;
            KOMEJoinBattleEgressService.INSTANCE.onConflictEnded(f.data,
                f.record.getConflictId(), 50L, physical);
            assertEquals(KOMEJoinBattleDeploymentReceipt.State.CLOSED, f.receipt().getState());
            assertEquals(disposition, f.receipt().getMountDisposition());
            assertFalse(physical.sawDurableEgressSnapshot);
        }
    }

    @Test public void pendingMountedEntryKeepsItsEntrySnapshotForSafeLoginRecovery() throws Exception {
        Fixture f = new Fixture(); f.publish(pending(f, true));
        FakePhysical offline = new FakePhysical(f.data);
        KOMEJoinBattleEgressService.INSTANCE.onConflictEnded(f.data,
            f.record.getConflictId(), 50L, offline);
        assertEquals(KOMEJoinBattleDeploymentReceipt.State.PENDING_ENTRY, f.receipt().getState());
        assertEquals(MountTransferPhase.DESTINATION_PUBLICATION_PENDING,
            f.receipt().getMountTransferPhase());
        assertNotNull(f.receipt().getTemporaryMountNbt());
        assertEquals(11, KOMEWorldData.KOME_DATA_SCHEMA_VERSION);
        assertEquals(2, KOMEConflictPersistence.DATA_SCHEMA_VERSION);
        assertEquals(1, KOMEHiredUnitPhysicalLocator.DATA_SCHEMA_VERSION);
    }

    @Test public void endedConflictMountedEgressRetriesAfterRollbackWithoutEntryAuthority() throws Exception {
        Fixture f=new Fixture();f.data.initializeIntegratedWorld();f.publish(deployed(f,true));
        f.record=ok(f.data.getConflictService().end("T100",f.expected(),f.context(40L)));
        assertFalse(f.record.isActive());assertTrue(f.record.getCommitments().isEmpty());
        f.access.pledge(LOTRFaction.ROHAN); // Entry faction/commitment authority is deliberately gone.
        FakePhysical physical=new FakePhysical(f.data);physical.preparation=mountedPreparation();
        physical.resultDisposition=MountDisposition.RETURNED_WITH_PLAYER;physical.egressSucceeds=false;
        KOMEJoinBattleEgressService.CompletionResult failure=
            KOMEJoinBattleEgressService.INSTANCE.requestAndCompleteDetailed(f.data,f.access.player,
                "JB1",50L,"Formal retreat",physical);
        assertEquals(KOMEJoinBattleEgressService.Completion.PENDING,failure.completion);
        assertEquals(KOMEJoinBattleDeploymentReceipt.State.PENDING_EGRESS,f.receipt().getState());
        assertNotNull(f.receipt().getTemporaryMountNbt());
        assertEquals(1,physical.egresses);
        physical.egressSucceeds=true;
        assertFalse(KOMEJoinBattleEgressService.INSTANCE.tick(f.data,f.access.player,5050L,physical));
        assertEquals("unchanged failure cannot trigger destructive work every five seconds",1,physical.egresses);
        assertTrue(KOMEJoinBattleEgressService.INSTANCE.tick(f.data,f.access.player,60050L,physical));
        assertEquals(2,physical.egresses);assertEquals(f.returnPose,physical.lastReturnPose);
        assertEquals(KOMEJoinBattleDeploymentReceipt.State.CLOSED,f.receipt().getState());
        assertEquals(MountDisposition.RETURNED_WITH_PLAYER,f.receipt().getMountDisposition());
        assertFalse(f.data.getConflictService().get("T100").isActive());
    }

    @Test public void mountedEgressLoginWaitsForVanillaThenClosesEndedReceiptAfterRestart() throws Exception {
        Fixture f=new Fixture();f.data.initializeIntegratedWorld();f.publish(deployed(f,true));
        f.record=ok(f.data.getConflictService().end("T100",f.expected(),f.context(40L)));
        FakePhysical offline=new FakePhysical(f.data);
        KOMEJoinBattleEgressService.INSTANCE.onConflictEnded(f.data,f.record.getConflictId(),50L,offline);
        NBTTagCompound saved=new NBTTagCompound();f.data.writeToNBT(saved);
        KOMEWorldData restored=new KOMEWorldData("ended-mounted-egress");restored.readFromNBT(saved);
        KOMEJoinBattleEgressService.INSTANCE.resetSession();
        FakePhysical physical=new FakePhysical(restored);physical.preparation=mountedPreparation();
        physical.resultDisposition=MountDisposition.RETURNED_WITH_PLAYER;
        KOMEJoinBattleEgressService.INSTANCE.onLogin(restored,f.access.player);
        assertEquals(0,physical.egresses);
        for(int tick=1;tick<20;tick++){
            assertFalse(KOMEJoinBattleEgressService.INSTANCE.tick(restored,f.access.player,100L+tick,physical));
            assertEquals(0,physical.egresses);
        }
        assertTrue(KOMEJoinBattleEgressService.INSTANCE.tick(restored,f.access.player,120L,physical));
        assertEquals(1,physical.egresses);
        assertEquals(KOMEJoinBattleDeploymentReceipt.State.CLOSED,
            restored.getJoinBattleDeploymentReceipts().get("JB1").getState());
        assertFalse(restored.getConflictService().get("T100").isActive());
    }

    private static KOMEJoinBattleEgressService.Preparation mountedPreparation() {
        NBTTagCompound snapshot = new NBTTagCompound(); snapshot.setString("id", "Horse");
        snapshot.setString("proof", "fresh-egress-snapshot");
        return KOMEJoinBattleEgressService.Preparation.mounted(snapshot);
    }

    @Test public void armedEgressRestartCannotDegradeToPlayerOnlyWhenRiderIsAbsent() throws Exception {
        Fixture f=new Fixture();f.data.initializeIntegratedWorld();f.publish(deployed(f,true));
        FakePhysical p=new FakePhysical(f.data);p.preparation=mountedPreparation();p.egressSucceeds=false;
        assertEquals(KOMEJoinBattleEgressService.Completion.PENDING,KOMEJoinBattleEgressService.INSTANCE
            .requestAndComplete(f.data,f.access.player,"JB1",50L,"test",p));
        assertEquals(MountTransferPhase.EGRESS_TRANSFER_PENDING,f.receipt().getMountTransferPhase());
        NBTTagCompound saved=new NBTTagCompound();f.data.writeToNBT(saved);
        KOMEWorldData restored=new KOMEWorldData("armed-egress");restored.readFromNBT(saved);
        KOMEJoinBattleEgressService.INSTANCE.resetSession();assertNull(f.access.player.ridingEntity);
        FakePhysical after=new FakePhysical(restored);after.preparation=KOMEJoinBattleEgressService.Preparation.playerOnly(MountDisposition.UNAVAILABLE);
        KOMEJoinBattleEgressService.CompletionResult denied=KOMEJoinBattleEgressService.INSTANCE
            .requestAndCompleteDetailed(restored,f.access.player,"JB1",100L,"test",after);
        assertEquals(KOMEJoinBattleEgressService.Completion.PENDING,denied.completion);
        assertTrue(denied.reason.contains("interrupted mounted return"));assertEquals(0,after.egresses);
        assertNotNull(restored.getJoinBattleDeploymentReceipts().get("JB1").getTemporaryMountNbt());
        after.preparation=mountedPreparation();after.resultDisposition=MountDisposition.RETURNED_WITH_PLAYER;
        assertEquals(KOMEJoinBattleEgressService.Completion.CLOSED,KOMEJoinBattleEgressService.INSTANCE
            .requestAndComplete(restored,f.access.player,"JB1",60100L,"test",after));
        assertTrue(after.sawDurableEgressSnapshot);assertEquals(1,after.egresses);
    }

    @Test public void verifiedInterruptedEgressRestorationDisarmsSnapshotBeforeLaterReturn() throws Exception {
        Fixture f=new Fixture();f.publish(deployed(f,true));
        FakePhysical physical=new FakePhysical(f.data);
        NBTTagCompound snapshot=new NBTTagCompound();snapshot.setString("id","EntityHorse");
        physical.preparation=KOMEJoinBattleEgressService.Preparation.mounted(snapshot);
        physical.egressSucceeds=false;
        KOMEJoinBattleEgressService.INSTANCE.requestAndComplete(f.data,f.access.player,"JB1",50L,"retreat",physical);
        assertEquals(MountTransferPhase.EGRESS_TRANSFER_PENDING,f.receipt().getMountTransferPhase());
        physical.preparation=KOMEJoinBattleEgressService.Preparation.restoredSource();
        assertFalse(KOMEJoinBattleEgressService.INSTANCE.reconcilePlayer(f.data,f.access.player,60100L,physical));
        assertEquals(MountTransferPhase.DEPLOYMENT_COMPLETE,f.receipt().getMountTransferPhase());
        assertNull(f.receipt().getTemporaryMountNbt());assertEquals(1,physical.egresses);
        physical.preparation=KOMEJoinBattleEgressService.Preparation.playerOnly(MountDisposition.DEAD);
        physical.egressSucceeds=true;
        assertTrue(KOMEJoinBattleEgressService.INSTANCE.reconcilePlayer(f.data,f.access.player,120200L,physical));
        assertEquals(KOMEJoinBattleDeploymentReceipt.State.CLOSED,f.receipt().getState());
    }

    private static final class FakePhysical implements KOMEJoinBattleEgressService.PhysicalAccess {
        final KOMEWorldData data; final Map<UUID, EntityPlayerMP> online = new HashMap<UUID, EntityPlayerMP>();
        KOMEJoinBattleEgressService.Preparation preparation =
            KOMEJoinBattleEgressService.Preparation.playerOnly(null);
        MountDisposition resultDisposition; boolean egressSucceeds = true;
        boolean sawDurableEgressSnapshot; int egresses;
        KOMEJoinBattleDeploymentReceipt.Pose lastReturnPose;
        FakePhysical(KOMEWorldData data) { this.data = data; }
        void online(EntityPlayerMP player) { online.put(KOMEReflection.getEntityUUID(player), player); }
        @Override public EntityPlayerMP findOnline(UUID playerId) { return online.get(playerId); }
        @Override public KOMEJoinBattleEgressService.Preparation prepare(EntityPlayerMP player,
                KOMEJoinBattleDeploymentReceipt receipt) { return preparation; }
        @Override public KOMEJoinBattleEgressService.PhysicalResult egress(EntityPlayerMP player,
                KOMEJoinBattleDeploymentReceipt receipt,
                KOMEJoinBattleEgressService.Preparation prepared) {
            egresses++; lastReturnPose = receipt.getReturnAnchor();
            sawDurableEgressSnapshot = receipt.isEnteredMounted()
                && receipt.getMountTransferPhase() == MountTransferPhase.EGRESS_TRANSFER_PENDING
                && receipt.getTemporaryMountNbt() != null;
            return egressSucceeds
                ? KOMEJoinBattleEgressService.PhysicalResult.completed(resultDisposition)
                : KOMEJoinBattleEgressService.PhysicalResult.failed();
        }
    }

    private static final class FakeEntryPhysical implements KOMEJoinBattleEntryService.PhysicalAccess {
        int deployments; boolean atDestination;
        @Override public long now() { return 100L; }
        @Override public KOMEJoinBattleEntryService.Preparation prepare(KOMEWorldData data,
                EntityPlayerMP player, KOMEArmyCompany company, String tileId,
                String conflictId, long conflictRevision) {
            return KOMEJoinBattleEntryService.Preparation.unmounted(
                new Pose(0, 1, 65, 1, 0, 0), new Pose(0, 2, 65, 2, 0, 0));
        }
        @Override public boolean isAtDestination(EntityPlayerMP player,
                KOMEJoinBattleDeploymentReceipt receipt) { return atDestination; }
        @Override public KOMEJoinBattleService.Reason preflight(KOMEWorldData data,
                EntityPlayerMP player,KOMEJoinBattleDeploymentReceipt receipt) {
            return KOMEJoinBattleService.Reason.ALLOWED;
        }
        @Override public KOMEJoinBattleEntryService.PhysicalResult deploy(EntityPlayerMP player,
                KOMEJoinBattleDeploymentReceipt receipt) {
            deployments++; atDestination = true;
            return KOMEJoinBattleEntryService.PhysicalResult.deployed();
        }
        @Override public KOMEJoinBattleEntryService.PhysicalResult rollback(EntityPlayerMP player,
                KOMEJoinBattleDeploymentReceipt receipt) {
            return KOMEJoinBattleEntryService.PhysicalResult.rolledBack(
                MountDisposition.RETURNED_WITH_PLAYER);
        }
    }

    private static final class Fixture {
        final KOMEAccessFixture access; final KOMEWorldData data; final UUID playerId;
        final Pose returnPose = new Pose(0, 11.25D, 65D, -7.5D, 80F, 5F);
        KOMEConflictRecord record;
        Fixture() throws Exception {
            access = new KOMEAccessFixture(); data = access.data; playerId = access.player.id;
            access.pledge(LOTRFaction.GONDOR);
            record = ok(data.getConflictService().start("T100", KOMEConflictRecord.State.ORDINARY,
                ExpectedConflict.absent(), Collections.<GarrisonSeed>emptyList(), context(10L)));
            record = ok(data.getConflictService().beginFactionParticipation("T100", expected(),
                "gondor", context(11L)));
            record = ok(data.getConflictService().registerPlayer("T100", expected(),
                playerId, "gondor", context(12L)));
        }
        void publish(KOMEJoinBattleDeploymentReceipt receipt) {
            data.getJoinBattleDeploymentReceipts().publishNew(receipt); data.markDirty();
        }
        KOMEJoinBattleDeploymentReceipt receipt() {
            return data.getJoinBattleDeploymentReceipts().get("JB1");
        }
        ExpectedConflict expected() { return ExpectedConflict.at(record.getConflictId(), record.getRevision()); }
        Context context(long now) { return new Context(now, "test", "egress test"); }
        void replaceWithEligibleConflict(long now) {
            KOMEConflictRecord ended = data.getConflictService().get("T100");
            if (ended.isActive()) ended = ok(data.getConflictService().end("T100", expected(), context(now)));
            record = ok(data.getConflictService().start("T100", KOMEConflictRecord.State.ORDINARY,
                ExpectedConflict.at(ended.getConflictId(), ended.getRevision()),
                Collections.<GarrisonSeed>emptyList(), context(now + 1L)));
            record = ok(data.getConflictService().beginFactionParticipation("T100", expected(),
                "gondor", context(now + 2L)));
            KOMEArmyCompany company = new KOMEArmyCompany(); company.id = "C1";
            company.owner = UUID.randomUUID(); company.ownerName = "Owner"; company.name = "Company";
            company.faction = "gondor"; company.currentTile = "T100";
            KOMEHiredUnitRecord unit = new KOMEHiredUnitRecord(); unit.entity = UUID.randomUUID();
            unit.owner = company.owner; unit.sourcePlayer = company.owner; unit.companyId = "C1";
            unit.companyName = company.name; unit.currentTile = "T100"; unit.sourceTileId = "T100";
            unit.unitFaction = "gondor"; unit.populationOwningFaction = "gondor";
            unit.type = KOMEPopulationType.OFFENSIVE; unit.assignPersistedUnitClass(KOMEHiredUnitClass.CAMPAIGN);
            unit.cost = unit.baseCost = unit.populationSpent = 20;
            company.units.add(unit.entity); company.totalPopulation = company.groundPopulation = 20;
            data.lastKnownPlayerFactions.put(company.owner, "gondor");
            data.armyCompanies.put(company.id, company); data.hiredUnits.put(unit.entity, unit);
            record = ok(data.getConflictService().commit("T100", expected(),
                new CommitmentInput("C1", KOMEHiredUnitClass.CAMPAIGN,
                    EntryOrigin.LEGAL_ARRIVAL, "M-C1"), context(now + 3L)));
        }
    }

    private static KOMEJoinBattleDeploymentReceipt deployed(Fixture f, boolean mounted) {
        Builder builder = base(f).state(KOMEJoinBattleDeploymentReceipt.State.DEPLOYED).updatedAtMillis(20L)
            .deployedAtMillis(Long.valueOf(20L)).deploymentDestination(
                new Pose(0, 30.5D, 65D, 30.5D, 0F, 0F))
            .participationRecovery(ParticipationRecovery.PREEXISTING_ACTIVE);
        return mounted ? mounted(builder, MountTransferPhase.DEPLOYMENT_COMPLETE, null).build()
            : builder.build();
    }

    private static KOMEJoinBattleDeploymentReceipt pending(Fixture f, boolean mounted) {
        Builder builder = base(f);
        if (!mounted) return builder.build();
        NBTTagCompound snapshot = new NBTTagCompound(); snapshot.setString("id", "Horse");
        return mounted(builder, MountTransferPhase.DESTINATION_PUBLICATION_PENDING,
            snapshot).build();
    }

    private static Builder base(Fixture f) {
        return KOMEJoinBattleDeploymentReceipt.builder().receiptId("JB1")
            .actionToken("egress-token").playerId(f.playerId)
            .conflictId(f.record.getConflictId()).tileId("T100")
            .acceptedConflictRevision(f.record.getRevision()).factionId("gondor")
            .selectedCompanyId("C1").createdAtMillis(15L).updatedAtMillis(15L)
            .returnAnchor(f.returnPose).participationRecovery(ParticipationRecovery.REGISTRATION_REQUIRED);
    }

    private static Builder mounted(Builder builder, MountTransferPhase phase,
            NBTTagCompound snapshot) {
        return builder.enteredMounted(true)
            .mountUuid(UUID.fromString("32000000-0000-0000-0000-000000000001"))
            .mountEntityType("Horse").mountProfile(MountProfile.VANILLA_HORSE)
            .mountSourceAnchor(new Pose(0, 11.25D, 65D, -7.5D, 80F, 5F))
            .mountTransferPhase(phase).temporaryMountNbt(snapshot);
    }

    private static KOMEConflictRecord ok(KOMEConflictService.Result result) {
        assertTrue(result.code + ": " + result.reason, result.isSuccess()); return result.record;
    }
}
