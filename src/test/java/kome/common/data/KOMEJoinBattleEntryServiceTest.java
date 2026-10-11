package kome.common.data;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import kome.common.KOMEAccessFixture;
import kome.common.KOMEReflection;
import lotr.common.fac.LOTRFaction;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S13PacketDestroyEntities;
import net.minecraft.network.play.server.S1BPacketEntityAttach;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;

import static kome.common.data.KOMEConflictContracts.*;
import static kome.common.data.KOMEConflictRecord.*;
import static kome.common.data.KOMEJoinBattleDeploymentReceipt.*;
import static org.junit.Assert.*;

public class KOMEJoinBattleEntryServiceTest {
    @Rule public final KOMETileTestResources geometry=new KOMETileTestResources();
    @After public void clearTokens(){KOMEJoinBattleActionTokenService.INSTANCE.clearForTests();
        KOMEJoinBattleEntryRecoveryService.INSTANCE.resetSession();}

    @Test public void tokenIsServerScopedToPlayerConflictRevisionAndProjectedCompany() throws Exception{
        Fixture f=new Fixture();String token=f.token();
        assertEquals(32,token.length());assertTrue(token.matches("[0-9a-f]{32}"));
        assertTrue(KOMEJoinBattleActionTokenService.isUsableToken(token));
        for(String malformed:new String[]{"","x","0123456789abcdef0123456789abcde",
                "0123456789abcdef0123456789abcdeg","ABCDEFABCDEFABCDEFABCDEFABCDEFAB"})
            assertFalse(malformed,KOMEJoinBattleActionTokenService.isUsableToken(malformed));
        assertEquals(KOMEJoinBattleActionTokenService.Validation.ACCEPTED,
            KOMEJoinBattleActionTokenService.INSTANCE.validate(token,f.playerId,"T100",
                f.record.getConflictId(),f.record.getRevision(),"C1",100L));
        assertEquals(KOMEJoinBattleActionTokenService.Validation.SCOPE_MISMATCH,
            KOMEJoinBattleActionTokenService.INSTANCE.validate(token,UUID.randomUUID(),"T100",
                f.record.getConflictId(),f.record.getRevision(),"C1",100L));
        assertEquals(KOMEJoinBattleActionTokenService.Validation.SCOPE_MISMATCH,
            KOMEJoinBattleActionTokenService.INSTANCE.validate(token,f.playerId,"T100",
                f.record.getConflictId(),f.record.getRevision(),"C2",100L));
        assertEquals(KOMEJoinBattleActionTokenService.Validation.ABSENT_OR_EXPIRED,
            KOMEJoinBattleActionTokenService.INSTANCE.validate("not-hex",f.playerId,"T100",
                f.record.getConflictId(),f.record.getRevision(),"C1",100L));
        KOMEJoinBattleActionTokenService.INSTANCE.clearForTests();
        assertEquals(KOMEJoinBattleActionTokenService.Validation.ABSENT_OR_EXPIRED,
            KOMEJoinBattleActionTokenService.INSTANCE.validate(token,f.playerId,"T100",
                f.record.getConflictId(),f.record.getRevision(),"C1",100L));
    }

    @Test public void previouslyIssuedTokenCannotBypassNewGovernanceRestriction() throws Exception {
        Fixture f=new Fixture(); String token=f.token();
        KOMEWar war=new KOMEWar(); war.id="W1";
        war.sideOneFactions.add("gondor"); war.sideTwoFactions.add("mordor");
        f.data.wars.put(war.id,war);f.data.lastKnownPlayerFactions.put(f.playerId,"gondor");
        f.data.warSeason.phase=KOMEWarSeasonState.Phase.WAR;
        f.data.warSeason.factionDefeats.put("gondor",1L);
        FakePhysical physical=new FakePhysical(f.data);
        KOMEJoinBattleEntryService.Result result=KOMEJoinBattleEntryService.INSTANCE.enter(
            f.data,f.access.player,f.request(token,"C1"),physical);
        assertEquals(KOMEJoinBattleEntryService.Status.REJECTED,result.status);
        assertEquals("GOVERNANCE_RESTRICTED",result.reason.name());
        assertEquals(0,physical.preparations);assertEquals(0,physical.deployments);
        assertTrue(f.data.getJoinBattleDeploymentReceipts().records().isEmpty());
        assertTrue(f.data.getConflictService().get("T100").getPlayers().isEmpty());
    }

    @Test public void firstEntryPublishesReceiptBeforeMovementRegistersOnceAndReplaysIdempotently() throws Exception{
        Fixture f=new Fixture();FakePhysical physical=new FakePhysical(f.data);
        long before=f.record.getRevision();String token=f.token();
        KOMEArmyCompany unchangedCompany=f.data.armyCompanies.get("C1");
        String movementOrder=unchangedCompany.movementOrderId,status=unchangedCompany.status;
        int allowance=unchangedCompany.movementAllowance;
        KOMEJoinBattleEntryService.Request request=f.request(token,"C1");
        KOMEJoinBattleEntryService.Result first=KOMEJoinBattleEntryService.INSTANCE.enter(
            f.data,f.access.player,request,physical);
        assertEquals(KOMEJoinBattleEntryService.Status.DEPLOYED,first.status);
        assertTrue(physical.sawPublishedReceipt);assertEquals(1,physical.deployments);
        KOMEJoinBattleDeploymentReceipt receipt=f.data.getJoinBattleDeploymentReceipts().get(first.receiptId);
        assertEquals(KOMEJoinBattleDeploymentReceipt.State.DEPLOYED,receipt.getState());
        assertEquals(ParticipationRecovery.REGISTERED_BY_RECEIPT,receipt.getParticipationRecovery());
        f.record=f.data.getConflictService().get("T100");
        assertEquals(before+1L,f.record.getRevision());
        assertEquals(PlayerStatus.ACTIVE,f.record.getPlayers().get(f.playerId).status);
        assertEquals(movementOrder,unchangedCompany.movementOrderId);assertEquals(status,unchangedCompany.status);
        assertEquals(allowance,unchangedCompany.movementAllowance);assertTrue(f.data.emergencyDefenseCommitments.isEmpty());

        KOMEJoinBattleActionTokenService.INSTANCE.clearForTests(); // simulated server restart
        KOMEJoinBattleEntryService.Result replay=KOMEJoinBattleEntryService.INSTANCE.enter(
            f.data,f.access.player,request,physical);
        assertEquals(KOMEJoinBattleEntryService.Status.DEPLOYED,replay.status);
        assertEquals(first.receiptId,replay.receiptId);assertEquals(1,physical.deployments);
        assertEquals(before+1L,f.data.getConflictService().get("T100").getRevision());
        assertEquals(1,f.data.getJoinBattleDeploymentReceipts().records().size());
    }

    @Test public void existingActiveParticipationRedeployDoesNotReviseConflict() throws Exception{
        Fixture f=new Fixture();f.register();long revision=f.record.getRevision();
        FakePhysical physical=new FakePhysical(f.data);
        KOMEJoinBattleEntryService.Result result=KOMEJoinBattleEntryService.INSTANCE.enter(
            f.data,f.access.player,f.request(f.token(),"C1"),physical);
        assertEquals(KOMEJoinBattleEntryService.Status.DEPLOYED,result.status);
        assertEquals(revision,f.data.getConflictService().get("T100").getRevision());
        assertEquals(ParticipationRecovery.PREEXISTING_ACTIVE,
            f.data.getJoinBattleDeploymentReceipts().get(result.receiptId).getParticipationRecovery());
    }

    @Test public void withdrawnCannotObtainActionTokenAndMateriallyDifferentReplayFailsClosed() throws Exception{
        Fixture withdrawn=new Fixture();withdrawn.register();withdrawn.withdraw();
        FakePhysical physical=new FakePhysical(withdrawn.data);
        KOMEJoinBattleEntryService.Result denied=KOMEJoinBattleEntryService.INSTANCE.enter(
            withdrawn.data,withdrawn.access.player,withdrawn.request(withdrawn.token(),"C1"),physical);
        assertEquals(KOMEJoinBattleEntryService.Status.REJECTED,denied.status);
        assertEquals(KOMEJoinBattleService.Reason.INVALID_ACTION_TOKEN,denied.reason);
        assertEquals(0,physical.preparations);assertEquals(0,physical.deployments);

        Fixture f=new Fixture();String token=f.token();KOMEJoinBattleEntryService.Request exact=f.request(token,"C1");
        KOMEJoinBattleEntryService.Result deployed=KOMEJoinBattleEntryService.INSTANCE.enter(
            f.data,f.access.player,exact,new FakePhysical(f.data));
        assertEquals(KOMEJoinBattleEntryService.Status.DEPLOYED,deployed.status);
        KOMEJoinBattleEntryService.Request changed=new KOMEJoinBattleEntryService.Request("T100",
            f.record.getConflictId(),f.record.getRevision(),"C2",token);
        KOMEJoinBattleEntryService.Result mismatch=KOMEJoinBattleEntryService.INSTANCE.enter(
            f.data,f.access.player,changed,new FakePhysical(f.data));
        assertEquals(KOMEJoinBattleService.Reason.INVALID_ACTION_TOKEN,mismatch.reason);
    }

    @Test public void physicalPreflightFailuresCreateNoReceiptAndDoNotRegister() throws Exception{
        Fixture f=new Fixture();FakePhysical physical=new FakePhysical(f.data);
        physical.preparationReason=KOMEJoinBattleService.Reason.COMPANY_LOCATION_UNKNOWN;
        KOMEJoinBattleEntryService.Result unloaded=KOMEJoinBattleEntryService.INSTANCE.enter(
            f.data,f.access.player,f.request(f.token(),"C1"),physical);
        assertEquals(KOMEJoinBattleEntryService.Status.ENTRY_PENDING,unloaded.status);
        assertEquals(KOMEJoinBattleService.Reason.COMPANY_LOCATION_UNKNOWN,unloaded.reason);
        assertFalse(unloaded.actionToken.isEmpty());
        kome.common.network.KOMEPacketJoinBattleSelectionResult wire=
            kome.common.network.KOMEPacketJoinBattleSelectionResult.from(unloaded,f.access.player);
        assertEquals(unloaded.actionToken,wire.current.actionToken);
        assertTrue(f.data.getJoinBattleDeploymentReceipts().records().isEmpty());
        assertFalse(f.data.getConflictService().get("T100").getPlayers().containsKey(f.playerId));
    }

    @Test public void postPublicationFailureRemainsRecoverableAndRetryDoesNotDoubleDebitAuthority() throws Exception{
        Fixture f=new Fixture();FakePhysical physical=new FakePhysical(f.data);physical.deploySucceeds=false;
        String token=f.token();KOMEJoinBattleEntryService.Request request=f.request(token,"C1");
        KOMEJoinBattleEntryService.Result pending=KOMEJoinBattleEntryService.INSTANCE.enter(
            f.data,f.access.player,request,physical);
        assertEquals(KOMEJoinBattleEntryService.Status.ENTRY_PENDING,pending.status);
        assertEquals(token,pending.actionToken);
        assertEquals(KOMEJoinBattleDeploymentReceipt.State.PENDING_ENTRY,f.data.getJoinBattleDeploymentReceipts().get(pending.receiptId).getState());
        assertFalse(f.data.getConflictService().get("T100").getPlayers().containsKey(f.playerId));
        long next=f.data.getJoinBattleDeploymentReceipts().getNextReceiptSequence();
        physical.deploySucceeds=true;physical.now=62000L;
        KOMEJoinBattleEntryService.Result retried=KOMEJoinBattleEntryService.INSTANCE.enter(
            f.data,f.access.player,request,physical);
        assertEquals(KOMEJoinBattleEntryService.Status.DEPLOYED,retried.status);
        assertEquals(pending.receiptId,retried.receiptId);
        assertEquals(next,f.data.getJoinBattleDeploymentReceipts().getNextReceiptSequence());
    }

    @Test public void mountedEnvelopeIsRecoverableThenClearedOnlyAfterVerifiedDeployment() throws Exception{
        Fixture f=new Fixture();FakePhysical physical=new FakePhysical(f.data);physical.mounted=true;
        KOMEJoinBattleEntryService.Result result=KOMEJoinBattleEntryService.INSTANCE.enter(
            f.data,f.access.player,f.request(f.token(),"C1"),physical);
        assertEquals(KOMEJoinBattleEntryService.Status.DEPLOYED,result.status);
        KOMEJoinBattleDeploymentReceipt receipt=f.data.getJoinBattleDeploymentReceipts().get(result.receiptId);
        assertTrue(receipt.isEnteredMounted());assertEquals(MountProfile.VANILLA_HORSE,receipt.getMountProfile());
        assertEquals(MountTransferPhase.DEPLOYMENT_COMPLETE,receipt.getMountTransferPhase());
        assertNull(receipt.getTemporaryMountNbt());
        assertTrue(physical.sawPendingMountSnapshot);
    }

    @Test public void acceptedMountedFailureRollsBackAndAutomaticRecoveryUsesSameReceipt() throws Exception{
        Fixture f=new Fixture();FakePhysical physical=new FakePhysical(f.data);physical.mounted=true;
        physical.failureStage=MountFailureStage.AFTER_DESTINATION_CREATION;
        String token=f.token();KOMEJoinBattleEntryService.Request request=f.request(token,"C1");
        KOMEJoinBattleEntryService.Result failed=KOMEJoinBattleEntryService.INSTANCE.enter(
            f.data,f.access.player,request,physical);
        assertEquals(KOMEJoinBattleEntryService.Status.ENTRY_PENDING,failed.status);
        assertEquals(KOMEJoinBattleService.Reason.MOUNT_TRANSFER_ROLLED_BACK,failed.reason);
        KOMEJoinBattleDeploymentReceipt pending=f.data.getJoinBattleDeploymentReceipts().get(failed.receiptId);
        assertEquals(KOMEJoinBattleDeploymentReceipt.State.PENDING_ENTRY,pending.getState());
        assertNull(pending.getTemporaryMountNbt());
        assertEquals(MountTransferPhase.NOT_STARTED,pending.getMountTransferPhase());
        assertEquals(1,physical.sourceMounts);assertEquals(0,physical.destinationMounts);
        assertTrue(physical.playerAtSource);assertTrue(physical.reciprocalRide);
        assertFalse(f.data.getConflictService().get("T100").getPlayers().containsKey(f.playerId));

        physical.failureStage=null;physical.now=7000L;
        KOMEJoinBattleEntryService.Result joined=KOMEJoinBattleEntryService.INSTANCE
            .reconcileAccepted(f.data,f.access.player,physical);
        assertEquals(KOMEJoinBattleEntryService.Status.DEPLOYED,joined.status);
        assertEquals(failed.receiptId,joined.receiptId);
        assertEquals(1,f.data.getJoinBattleDeploymentReceipts().records().size());
    }

    @Test public void everyMountedDestructiveFailureStageRestoresOneSourceAndNoDestination() throws Exception{
        for(MountFailureStage stage:MountFailureStage.values()){
            Fixture f=new Fixture();FakePhysical physical=new FakePhysical(f.data);physical.mounted=true;
            physical.failureStage=stage;
            KOMEJoinBattleEntryService.Result result=KOMEJoinBattleEntryService.INSTANCE.enter(
                f.data,f.access.player,f.request(f.token(),"C1"),physical);
            assertEquals(stage.name(),KOMEJoinBattleEntryService.Status.ENTRY_PENDING,result.status);
            assertEquals(stage.name(),KOMEJoinBattleService.Reason.MOUNT_TRANSFER_ROLLED_BACK,result.reason);
            assertEquals(stage.name(),1,physical.sourceMounts);
            assertEquals(stage.name(),0,physical.destinationMounts);
            assertTrue(stage.name(),physical.playerAtSource);
            assertTrue(stage.name(),physical.reciprocalRide);
            assertEquals(stage.name(),KOMEJoinBattleDeploymentReceipt.State.PENDING_ENTRY,
                f.data.getJoinBattleDeploymentReceipts().get(result.receiptId).getState());
            assertFalse(stage.name(),f.data.getConflictService().get("T100").getPlayers().containsKey(f.playerId));
            KOMEJoinBattleActionTokenService.INSTANCE.clearForTests();
        }
    }

    @Test public void unresolvedMountedRollbackStaysPendingAndDoesNotRegister() throws Exception{
        Fixture f=new Fixture();FakePhysical physical=new FakePhysical(f.data);physical.mounted=true;
        physical.failureStage=MountFailureStage.AFTER_SOURCE_DESTRUCTION;physical.rollbackSucceeds=false;
        KOMEJoinBattleEntryService.Result result=KOMEJoinBattleEntryService.INSTANCE.enter(
            f.data,f.access.player,f.request(f.token(),"C1"),physical);
        assertEquals(KOMEJoinBattleEntryService.Status.ENTRY_PENDING,result.status);
        assertEquals(KOMEJoinBattleService.Reason.MOUNT_TRANSFER_FAILED,result.reason);
        KOMEJoinBattleDeploymentReceipt receipt=f.data.getJoinBattleDeploymentReceipts().get(result.receiptId);
        assertEquals(KOMEJoinBattleDeploymentReceipt.State.PENDING_ENTRY,receipt.getState());
        assertEquals(MountTransferPhase.DESTINATION_PUBLICATION_PENDING,receipt.getMountTransferPhase());
        assertNotNull(receipt.getTemporaryMountNbt());
        assertFalse(f.data.getConflictService().get("T100").getPlayers().containsKey(f.playerId));
    }

    @Test public void restartRecoveryNeedsNoFreshTokenOrSecondJoinClick() throws Exception{
        Fixture f=new Fixture();FakePhysical physical=new FakePhysical(f.data);physical.mounted=true;
        physical.failureStage=MountFailureStage.AFTER_SOURCE_DESTRUCTION;physical.rollbackSucceeds=false;
        KOMEJoinBattleEntryService.Result pending=KOMEJoinBattleEntryService.INSTANCE.enter(
            f.data,f.access.player,f.request(f.token(),"C1"),physical);
        assertEquals(KOMEJoinBattleEntryService.Status.ENTRY_PENDING,pending.status);
        long nextReceipt=f.data.getJoinBattleDeploymentReceipts().getNextReceiptSequence();

        NBTTagCompound saved=new NBTTagCompound();f.data.writeToNBT(saved);
        KOMEWorldData restored=new KOMEWorldData("accepted-entry-restart");restored.readFromNBT(saved);
        KOMEJoinBattleActionTokenService.INSTANCE.clearForTests();
        physical=new FakePhysical(restored);physical.mounted=true;physical.sourceMounts=0;
        physical.recoverMissingSourceOnce=true;
        KOMEJoinBattleEntryService.Result restoring=KOMEJoinBattleEntryService.INSTANCE
            .reconcileAccepted(restored,f.access.player,physical);
        assertEquals(KOMEJoinBattleEntryService.Status.ENTRY_PENDING,restoring.status);
        assertEquals(KOMEJoinBattleService.Reason.MOUNT_TRANSFER_ROLLED_BACK,
            restoring.reason);
        assertEquals("source restoration must be a stable end to this reconciliation pass",
            0,physical.deployments);
        assertEquals(1,physical.sourceMounts);assertTrue(physical.reciprocalRide);
        physical.now=7000L; // Verified rollback is stable until the shared transient gate opens.
        KOMEJoinBattleEntryService.Result recovered=KOMEJoinBattleEntryService.INSTANCE
            .reconcileAccepted(restored,f.access.player,physical);
        assertEquals(KOMEJoinBattleEntryService.Status.DEPLOYED,recovered.status);
        assertEquals(pending.receiptId,recovered.receiptId);
        assertEquals(KOMEJoinBattleDeploymentReceipt.State.DEPLOYED,
            restored.getJoinBattleDeploymentReceipts()
            .get(pending.receiptId).getState());
        assertEquals(nextReceipt,restored.getJoinBattleDeploymentReceipts().getNextReceiptSequence());
        assertEquals(1,restored.getJoinBattleDeploymentReceipts().records().size());
        assertTrue(restored.getConflictService().get("T100").getPlayers().containsKey(f.playerId));
    }

    @Test public void automaticRetryCadenceIsBoundedAndReusesAcceptedReceipt() throws Exception{
        Fixture f=new Fixture();FakePhysical physical=new FakePhysical(f.data);
        physical.deploySucceeds=false;
        KOMEJoinBattleEntryService.Result pending=KOMEJoinBattleEntryService.INSTANCE.enter(
            f.data,f.access.player,f.request(f.token(),"C1"),physical);
        assertEquals(KOMEJoinBattleEntryService.Status.ENTRY_PENDING,pending.status);
        int before=physical.deployments;
        KOMEJoinBattleEntryRecoveryService.INSTANCE.resetSession();
        assertNotNull(KOMEJoinBattleEntryRecoveryService.INSTANCE.tick(
            f.data,f.access.player,100L,physical));
        assertEquals(before+1,physical.deployments);
        assertNull("No per-tick retry spam",KOMEJoinBattleEntryRecoveryService.INSTANCE.tick(
            f.data,f.access.player,4999L,physical));
        assertEquals(before+1,physical.deployments);
        physical.deploySucceeds=true;
        KOMEJoinBattleEntryService.Result waiting=
            KOMEJoinBattleEntryRecoveryService.INSTANCE.tick(f.data,f.access.player,5100L,physical);
        assertEquals(KOMEJoinBattleEntryService.Status.ENTRY_PENDING,waiting.status);
        assertEquals("Unchanged deterministic failure only receives non-destructive checks",
            before+1,physical.deployments);
        KOMEJoinBattleEntryService.Result completed=
            KOMEJoinBattleEntryRecoveryService.INSTANCE.tick(f.data,f.access.player,60100L,physical);
        assertEquals(KOMEJoinBattleEntryService.Status.DEPLOYED,completed.status);
        assertEquals(pending.receiptId,completed.receiptId);
        assertEquals(1,f.data.getJoinBattleDeploymentReceipts().records().size());
    }

    @Test public void recoveredSourceIsStableUntilLaterTransientRetry() throws Exception{
        Fixture f=new Fixture();FakePhysical physical=new FakePhysical(f.data);physical.mounted=true;
        physical.failureStage=MountFailureStage.AFTER_SOURCE_DESTRUCTION;
        physical.rollbackSucceeds=false;
        KOMEJoinBattleEntryService.Result pending=KOMEJoinBattleEntryService.INSTANCE.enter(
            f.data,f.access.player,f.request(f.token(),"C1"),physical);
        assertEquals(KOMEJoinBattleEntryService.Status.ENTRY_PENDING,pending.status);

        physical.failureStage=null;physical.rollbackSucceeds=true;physical.sourceMounts=0;
        physical.recoverMissingSourceOnce=true;
        KOMEJoinBattleEntryRecoveryService.INSTANCE.resetSession();
        int before=physical.deployments;
        KOMEJoinBattleEntryService.Result restored=KOMEJoinBattleEntryRecoveryService.INSTANCE
            .onLogin(f.data,f.access.player,100L,physical);
        assertEquals(KOMEJoinBattleEntryService.Status.ENTRY_PENDING,restored.status);
        assertEquals("login callback precedes vanilla Riding restoration",before,physical.deployments);
        for(int tick=1;tick<KOMEJoinBattleEntryRecoveryService.LOGIN_STABILIZATION_TICKS;tick++){
            assertNull(KOMEJoinBattleEntryRecoveryService.INSTANCE.tick(
                f.data,f.access.player,100L+tick,physical));
            assertEquals(before,physical.deployments);
        }
        restored=KOMEJoinBattleEntryRecoveryService.INSTANCE.tick(
            f.data,f.access.player,120L,physical);
        assertEquals(KOMEJoinBattleEntryService.Status.ENTRY_PENDING,restored.status);
        assertEquals(before,physical.deployments);
        assertEquals(1,physical.sourceMounts);assertTrue(physical.reciprocalRide);
        assertNull(KOMEJoinBattleEntryRecoveryService.INSTANCE.tick(
            f.data,f.access.player,4999L,physical));
        assertEquals(before,physical.deployments);
        KOMEJoinBattleEntryService.Result joined=KOMEJoinBattleEntryRecoveryService.INSTANCE.tick(
            f.data,f.access.player,5120L,physical);
        assertEquals(KOMEJoinBattleEntryService.Status.DEPLOYED,joined.status);
        assertEquals(before+1,physical.deployments);
        assertTrue(physical.preflightBeforeEveryDeployment);
    }

    @Test public void automaticRecoveryCancelsWhenSelectedCompanyLosesAuthority() throws Exception{
        Fixture f=new Fixture();FakePhysical physical=new FakePhysical(f.data);
        physical.deploySucceeds=false;
        KOMEJoinBattleEntryService.Result pending=KOMEJoinBattleEntryService.INSTANCE.enter(
            f.data,f.access.player,f.request(f.token(),"C1"),physical);
        f.data.armyCompanies.remove("C1");
        physical.now=62000L;
        KOMEJoinBattleEntryService.Result cancelled=KOMEJoinBattleEntryService.INSTANCE
            .reconcileAccepted(f.data,f.access.player,physical);
        assertEquals(KOMEJoinBattleEntryService.Status.REJECTED,cancelled.status);
        assertEquals(KOMEJoinBattleDeploymentReceipt.State.CLOSED,
            f.data.getJoinBattleDeploymentReceipts().get(pending.receiptId).getState());
        assertFalse(f.data.getConflictService().get("T100").getPlayers().containsKey(f.playerId));
    }

    @Test public void automaticRecoveryCancelsWhenExactConflictEnds() throws Exception{
        Fixture f=new Fixture();FakePhysical physical=new FakePhysical(f.data);
        physical.deploySucceeds=false;
        KOMEJoinBattleEntryService.Result pending=KOMEJoinBattleEntryService.INSTANCE.enter(
            f.data,f.access.player,f.request(f.token(),"C1"),physical);
        f.record=ok(f.data.getConflictService().end("T100",f.expected(),f.context()));
        physical.now=62000L;
        KOMEJoinBattleEntryService.Result cancelled=KOMEJoinBattleEntryService.INSTANCE
            .reconcileAccepted(f.data,f.access.player,physical);
        assertEquals(KOMEJoinBattleEntryService.Status.REJECTED,cancelled.status);
        assertEquals(KOMEJoinBattleDeploymentReceipt.State.CLOSED,
            f.data.getJoinBattleDeploymentReceipts().get(pending.receiptId).getState());
    }

    @Test public void mountedDeploymentCannotPublishUntilDestinationVerificationPasses() throws Exception{
        Fixture f=new Fixture();FakePhysical physical=new FakePhysical(f.data);physical.mounted=true;
        physical.reportDeployedWithoutDestination=true;
        KOMEJoinBattleEntryService.Result result=KOMEJoinBattleEntryService.INSTANCE.enter(
            f.data,f.access.player,f.request(f.token(),"C1"),physical);
        assertEquals(KOMEJoinBattleEntryService.Status.ENTRY_PENDING,result.status);
        assertEquals(1,physical.rollbacks);
        assertEquals(KOMEJoinBattleDeploymentReceipt.State.PENDING_ENTRY,
            f.data.getJoinBattleDeploymentReceipts().get(result.receiptId).getState());
        assertFalse(f.data.getConflictService().get("T100").getPlayers().containsKey(f.playerId));
    }

    @Test public void mountProfilesAreExplicitAndArbitraryRideablesAreRejected() throws Exception{
        KOMEAccessFixture f=new KOMEAccessFixture();
        EntityHorse vanilla=KOMEAccessFixture.allocate(EntityHorse.class);
        lotr.common.entity.animal.LOTREntityHorse lotrHorse=
            KOMEAccessFixture.allocate(lotr.common.entity.animal.LOTREntityHorse.class);
        lotr.common.entity.npc.LOTREntityMordorWarg warg=
            KOMEAccessFixture.allocate(lotr.common.entity.npc.LOTREntityMordorWarg.class);
        Entity mumak=(Entity)KOMEAccessFixture.allocate((Class)Class.forName(
            "com.enovak.lotrmoremobs.entity.animal.LOTREntityMumakil"));
        EntityBoat boat=KOMEAccessFixture.allocate(EntityBoat.class);
        assertEquals(MountProfile.VANILLA_HORSE,KOMEJoinBattlePhysicalAccess.profileOf(vanilla));
        assertEquals(MountProfile.LOTR_HORSE_FAMILY,KOMEJoinBattlePhysicalAccess.profileOf(lotrHorse));
        assertEquals(MountProfile.LOTR_WARG,KOMEJoinBattlePhysicalAccess.profileOf(warg));
        assertNull(KOMEJoinBattlePhysicalAccess.profileOf(mumak));assertNull(KOMEJoinBattlePhysicalAccess.profileOf(boat));
    }

    @Test public void nativeMountNbtRoundTripPreservesPersistentIdentityAndAppearanceState() throws Exception{
        KOMEAccessFixture f=new KOMEAccessFixture();
        EntityHorse source=new EntityHorse(f.world);source.setHorseType(1);source.setHorseVariant(513);
        source.setHorseTamed(true);source.setHorseSaddled(true);source.setPosition(2,65,2);
        UUID uuid=source.getUniqueID();NBTTagCompound nbt=new NBTTagCompound();source.writeToNBT(nbt);
        EntityHorse horse=new EntityHorse(f.world);horse.readFromNBT((NBTTagCompound)nbt.copy());
        assertEquals(uuid,horse.getUniqueID());assertEquals(1,horse.getHorseType());
        assertEquals(513,horse.getHorseVariant());assertTrue(horse.isTame());
        assertEquals(2D,horse.posX,0D);assertEquals(2D,horse.posZ,0D);
    }

    @Test public void lotrWargUsesNativeTypedRecreationAndPreservesExactIdentityAndState() throws Exception{
        KOMEAccessFixture f=new KOMEAccessFixture();f.world.flatTerrain=true;f.world.spawnSucceeds=true;
        // LOTR family-data decoding only needs a real WorldServer to broadcast watcher updates.
        // Suppress that server-only side effect in this inert codec fixture.
        f.world.isRemote=true;
        final String type="lotr.MordorWarg";
        Object priorClass=EntityList.stringToClassMapping.put(type,
            lotr.common.entity.npc.LOTREntityMordorWarg.class);
        Object priorName=EntityList.classToStringMapping.put(
            lotr.common.entity.npc.LOTREntityMordorWarg.class,type);
        try{
            lotr.common.entity.npc.LOTREntityMordorWarg source=
                new lotr.common.entity.npc.LOTREntityMordorWarg(f.world);
            source.setWargType(lotr.common.entity.npc.LOTREntityWarg.WargType.BLACK);
            source.setNPCTamed(true);source.setNPCTemper(73);source.setWargSaddled(true);
            source.setHealth(17F);
            source.setPosition(4D,65D,6D);
            UUID uuid=source.getUniqueID();

            NBTTagCompound nativeSnapshot=new NBTTagCompound();
            assertTrue(source.writeMountToNBT(nativeSnapshot));
            assertEquals(type,nativeSnapshot.getString("id"));
            Entity constructionProbe=EntityList.createEntityByName(type,f.world);
            assertNotNull("registered warg type must construct",constructionProbe);
            assertEquals(MountProfile.LOTR_WARG,KOMEJoinBattlePhysicalAccess.profileOf(constructionProbe));
            java.lang.reflect.Method readWarg=
                lotr.common.entity.npc.LOTREntityWarg.class.getDeclaredMethod(
                    "readEntityFromNBT",NBTTagCompound.class);
            readWarg.setAccessible(true);
            try{readWarg.invoke(constructionProbe,(NBTTagCompound)nativeSnapshot.copy());}
            catch(java.lang.reflect.InvocationTargetException failure){
                throw new AssertionError("LOTR warg NBT read failed",failure.getCause());
            }
            KOMEJoinBattleDeploymentReceipt.Pose target=
                new KOMEJoinBattleDeploymentReceipt.Pose(0,20D,65D,22D,35F,5F);
            KOMEJoinBattlePhysicalAccess.Recreation recreated=
                KOMEJoinBattlePhysicalAccess.recreateDetailed(nativeSnapshot,f.world,target,uuid,
                    type,MountProfile.LOTR_WARG);
            assertTrue(recreated.reason.name(),recreated.success());
            assertTrue(recreated.entity instanceof lotr.common.entity.npc.LOTREntityMordorWarg);
            lotr.common.entity.npc.LOTREntityMordorWarg restored=
                (lotr.common.entity.npc.LOTREntityMordorWarg)recreated.entity;
            assertEquals(uuid,restored.getUniqueID());
            assertEquals(lotr.common.entity.npc.LOTREntityWarg.WargType.BLACK,restored.getWargType());
            assertTrue(restored.isNPCTamed());assertEquals(73,restored.getNPCTemper());
            assertEquals(17F,restored.getHealth(),0F);
            assertEquals(target.x,restored.posX,0D);assertEquals(target.z,restored.posZ,0D);
            assertNull("entity NBT must not restore a stale riding target",
                KOMEReflection.getRidingEntity(restored));
            assertNull("entity NBT must not restore a stale rider",
                KOMEReflection.getRiddenByEntity(restored));

            // Receipts created by the prior implementation have full entity data but no id tag.
            // Explicit registered-type construction keeps those live recovery obligations usable.
            NBTTagCompound legacySnapshot=new NBTTagCompound();source.writeToNBT(legacySnapshot);
            assertFalse(legacySnapshot.hasKey("id"));
            f.world.loadedEntityList.clear();
            KOMEJoinBattlePhysicalAccess.Recreation recovered=
                KOMEJoinBattlePhysicalAccess.recreateDetailed(legacySnapshot,f.world,target,uuid,
                    type,MountProfile.LOTR_WARG);
            assertTrue(recovered.reason.name(),recovered.success());
            assertEquals(uuid,recovered.entity.getUniqueID());
        }finally{
            if(priorClass==null)EntityList.stringToClassMapping.remove(type);
            else EntityList.stringToClassMapping.put(type,priorClass);
            if(priorName==null)EntityList.classToStringMapping.remove(
                lotr.common.entity.npc.LOTREntityMordorWarg.class);
            else EntityList.classToStringMapping.put(
                lotr.common.entity.npc.LOTREntityMordorWarg.class,priorName);
        }
    }

    @Test public void nativeTypedRecreationReportsIdentityProfileAndPublicationFailures() throws Exception{
        KOMEAccessFixture f=new KOMEAccessFixture();f.world.flatTerrain=true;
        EntityHorse source=new EntityHorse(f.world);source.setPosition(2D,65D,2D);
        UUID uuid=source.getUniqueID();NBTTagCompound snapshot=new NBTTagCompound();
        assertTrue(source.writeMountToNBT(snapshot));
        String type=EntityList.getEntityString(source);assertNotNull(type);
        KOMEJoinBattleDeploymentReceipt.Pose target=
            new KOMEJoinBattleDeploymentReceipt.Pose(0,8D,65D,8D,0F,0F);

        f.world.spawnSucceeds=true;
        assertEquals(KOMEJoinBattleService.Reason.DESTINATION_UUID_MISMATCH,
            KOMEJoinBattlePhysicalAccess.recreateDetailed(snapshot,f.world,target,
                UUID.randomUUID(),type,MountProfile.VANILLA_HORSE).reason);
        assertEquals(KOMEJoinBattleService.Reason.DESTINATION_PROFILE_MISMATCH,
            KOMEJoinBattlePhysicalAccess.recreateDetailed(snapshot,f.world,target,uuid,
                type,MountProfile.LOTR_WARG).reason);
        f.world.spawnSucceeds=false;
        assertEquals(KOMEJoinBattleService.Reason.DESTINATION_MOUNT_SPAWN_FAILED,
            KOMEJoinBattlePhysicalAccess.recreateDetailed(snapshot,f.world,target,uuid,
                type,MountProfile.VANILLA_HORSE).reason);
        assertTrue(f.world.loadedEntityList.isEmpty());
    }

    @Test public void destinationPublicationIgnoresDeadSourceAwaitingWorldRemoval() throws Exception{
        KOMEAccessFixture f=new KOMEAccessFixture();f.world.flatTerrain=true;
        f.world.spawnSucceeds=true;f.world.isRemote=true;
        EntityHorse source=new EntityHorse(f.world);source.setPosition(2D,65D,2D);
        UUID uuid=source.getUniqueID();NBTTagCompound snapshot=new NBTTagCompound();
        assertTrue(source.writeMountToNBT(snapshot));String type=EntityList.getEntityString(source);
        source.setDead();f.world.loadedEntityList.add(source);
        Pose target=new Pose(0,8D,65D,8D,0F,0F);
        KOMEJoinBattlePhysicalAccess.Recreation result=KOMEJoinBattlePhysicalAccess.recreateDetailed(
            snapshot,f.world,target,uuid,type,MountProfile.VANILLA_HORSE);
        assertTrue(result.reason.name(),result.success());
        assertFalse("spawn scope must end before geometry; tracking owns its own bounded scope",
            result.entity.forceSpawn);
        int live=0;for(Object value:f.world.loadedEntityList)if(value instanceof Entity
                &&!((Entity)value).isDead
                &&uuid.equals(KOMEReflection.getEntityUUID((Entity)value)))live++;
        assertEquals(1,live);
    }

    @Test public void loginWargIsAdoptedWithoutWorldChunkOrTrackerPublication() throws Exception{
        KOMEAccessFixture f=new KOMEAccessFixture();f.world.flatTerrain=true;f.world.isRemote=true;
        String type="lotr.MordorWarg";
        Object previous=EntityList.classToStringMapping.put(
            lotr.common.entity.npc.LOTREntityMordorWarg.class,type);
        try{
            lotr.common.entity.npc.LOTREntityMordorWarg nested=
                new lotr.common.entity.npc.LOTREntityMordorWarg(f.world);
            nested.setPosition(8D,65D,8D);nested.setHealth(17F);
            nested.setNPCTamed(true);nested.setNPCTemper(73);nested.setWargSaddled(true);
            NBTTagCompound snapshot=new NBTTagCompound();assertTrue(nested.writeMountToNBT(snapshot));
            // Vanilla restores Riding by deserializing native entity NBT before attaching it.
            nested.readFromNBT((NBTTagCompound)snapshot.copy());
            KOMEJoinBattleDeploymentReceipt receipt=KOMEJoinBattleDeploymentReceipt.copyOf(
                mountedRecoveryReceipt(f,nested.getUniqueID(),type,snapshot,
                    new Pose(0,8D,65D,8D,0F,0F),new Pose(0,144D,65D,144D,0F,0F)))
                .mountProfile(MountProfile.LOTR_WARG).build();
            mountFixturePlayer(f,nested);
            assertFalse(f.world.loadedEntityList.contains(nested));
            assertTrue(KOMEJoinBattlePhysicalAccess.hasExpectedRidingMount(f.player,receipt));
            KOMEJoinBattlePhysicalAccess.RollbackMountResolution resolution=
                KOMEJoinBattlePhysicalAccess.resolveContinuingMounts(f.player,receipt,
                    Collections.<Entity>emptyList());
            assertTrue(resolution.reason.name(),resolution.success());
            assertSame("empty world lookup must never authorize replacement",nested,resolution.source);
            assertTrue(resolution.remove.isEmpty());
            assertEquals(17F,nested.getHealth(),0F);assertEquals(73,nested.getNPCTemper());
            assertSame(nested,KOMEReflection.getRidingEntity(f.player));
            assertSame(f.player,KOMEReflection.getRiddenByEntity(nested));
        }finally{
            if(previous==null)EntityList.classToStringMapping.remove(
                lotr.common.entity.npc.LOTREntityMordorWarg.class);
            else EntityList.classToStringMapping.put(
                lotr.common.entity.npc.LOTREntityMordorWarg.class,previous);
        }
    }

    @Test public void continuingRecoveryPrefersNestedSourceAndRejectsAmbiguousGameplayState()
            throws Exception{
        KOMEAccessFixture f=new KOMEAccessFixture();f.world.flatTerrain=true;
        EntityHorse nested=new EntityHorse(f.world);nested.setPosition(9D,65D,9D);
        nested.setHorseTamed(true);nested.setHorseSaddled(true);nested.setHealth(17F);
        NBTTagCompound snapshot=new NBTTagCompound();assertTrue(nested.writeMountToNBT(snapshot));
        KOMEJoinBattleDeploymentReceipt receipt=mountedRecoveryReceipt(f,nested.getUniqueID(),
            EntityList.getEntityString(nested),snapshot,new Pose(0,8D,65D,8D,0F,0F),
            new Pose(0,144D,65D,144D,0F,0F));
        mountFixturePlayer(f,nested);
        EntityHorse standalone=cloneHorse(f,snapshot,8D,65D,8D);
        EntityHorse unrelated=new EntityHorse(f.world);unrelated.setHorseTamed(true);
        KOMEJoinBattlePhysicalAccess.RollbackMountResolution resolution=
            KOMEJoinBattlePhysicalAccess.resolveContinuingMounts(f.player,receipt,
                Arrays.<Entity>asList(standalone,unrelated,nested,nested));
        assertTrue(resolution.reason.name(),resolution.success());assertSame(nested,resolution.source);
        assertEquals(Collections.<Entity>singletonList(standalone),resolution.remove);
        for(Entity duplicate:resolution.remove)
            assertTrue(KOMEJoinBattlePhysicalAccess.retireMountForTransfer(f.player,duplicate));
        assertEquals(Collections.<Entity>singletonList(nested),
            KOMEJoinBattlePhysicalAccess.includeNestedMount(f.player,receipt,
                Arrays.<Entity>asList(standalone,unrelated,nested)));
        assertFalse(unrelated.isDead);assertFalse(nested.isDead);
        assertSame(nested,KOMEReflection.getRidingEntity(f.player));
        assertSame(f.player,KOMEReflection.getRiddenByEntity(nested));
        EntityHorse changed=cloneHorse(f,snapshot,8D,65D,8D);changed.setHealth(12F);
        assertEquals(KOMEJoinBattleService.Reason.MOUNT_DUPLICATE_STATE_MISMATCH,
            KOMEJoinBattlePhysicalAccess.resolveContinuingMounts(f.player,receipt,
                Collections.<Entity>singletonList(changed)).reason);
        EntityHorse outside=cloneHorse(f,snapshot,400D,65D,400D);
        assertFalse(KOMEJoinBattlePhysicalAccess.resolveContinuingMounts(f.player,receipt,
            Collections.<Entity>singletonList(outside)).success());
        EntityHorse destination=cloneHorse(f,snapshot,144D,65D,144D);
        assertFalse("continuing entry must not borrow cancellation's source-wins rule",
            KOMEJoinBattlePhysicalAccess.resolveContinuingMounts(f.player,receipt,
                Collections.<Entity>singletonList(destination)).success());
    }

    @Test public void loginCallbackDefersUntilVanillaPublishesThenResumesSameReceipt() throws Exception{
        Fixture f=new Fixture();f.access.world.flatTerrain=true;
        FakePhysical physical=new FakePhysical(f.data);physical.mounted=true;physical.deploySucceeds=false;
        KOMEJoinBattleEntryService.Result accepted=KOMEJoinBattleEntryService.INSTANCE.enter(
            f.data,f.access.player,f.request(f.token(),"C1"),physical);
        KOMEJoinBattleDeploymentReceipt original=f.data.getJoinBattleDeploymentReceipts().get(accepted.receiptId);
        EntityHorse nested=new EntityHorse(f.access.world);nested.setPosition(1D,65D,1D);
        NBTTagCompound snapshot=new NBTTagCompound();assertTrue(nested.writeMountToNBT(snapshot));
        // Fixture models the persisted native snapshot that vanilla will restore AFTER the event.
        // Install through persistence so immutable-identity replacement validation stays intact.
        NBTTagCompound saved=new NBTTagCompound();f.data.writeToNBT(saved);
        // A separate fresh registry represents restart with this native snapshot.
        KOMEWorldData restarted=new KOMEWorldData("login-native-riding");restarted.readFromNBT(saved);
        // Use the original mount UUID in native NBT, as the real saved player Riding record does.
        snapshot.setLong("UUIDMost",original.getMountUuid().getMostSignificantBits());
        snapshot.setLong("UUIDLeast",original.getMountUuid().getLeastSignificantBits());
        nested.readFromNBT(snapshot);
        KOMEJoinBattleDeploymentReceipt receipt=KOMEJoinBattleDeploymentReceipt.copyOf(original)
            .temporaryMountNbt(snapshot).build();
        restarted.getJoinBattleDeploymentReceipts().replace(receipt);
        physical=new FakePhysical(restarted);physical.mounted=true;
        KOMEJoinBattleEntryRecoveryService.INSTANCE.resetSession();
        KOMEJoinBattleEntryService.Result waiting=KOMEJoinBattleEntryRecoveryService.INSTANCE.onLogin(
            restarted,f.access.player,100L,physical);
        assertEquals(KOMEJoinBattleEntryService.Status.ENTRY_PENDING,waiting.status);
        assertEquals(0,physical.deployments);
        mountFixturePlayer(f.access,nested); // Vanilla's post-event spawn/attach.
        assertEquals(receipt.getMountUuid(),nested.getUniqueID());
        assertEquals(receipt.getMountEntityType(),EntityList.getEntityString(nested));
        assertTrue("native login snapshot should round-trip semantically",
            KOMEJoinBattlePhysicalAccess.semanticallyEquivalentMountState(snapshot,nested));
        assertTrue(KOMEJoinBattlePhysicalAccess.hasExpectedRidingMount(f.access.player,receipt));
        KOMEJoinBattleEntryService.Result result=KOMEJoinBattleEntryRecoveryService.INSTANCE.tick(
            restarted,f.access.player,101L,physical);
        assertEquals(KOMEJoinBattleEntryService.Status.ENTRY_PENDING,result.status);
        assertEquals(0,physical.deployments);
        result=KOMEJoinBattleEntryRecoveryService.INSTANCE.tick(restarted,f.access.player,5101L,physical);
        assertEquals(KOMEJoinBattleEntryService.Status.DEPLOYED,result.status);
        assertEquals(accepted.receiptId,result.receiptId);assertEquals(1,physical.deployments);
        assertEquals(1,physical.rollbacks);assertEquals(1,restarted.getJoinBattleDeploymentReceipts().records().size());
        assertTrue(restarted.getConflictService().get("T100").getPlayers().containsKey(f.playerId));
    }

    private static void mountFixturePlayer(KOMEAccessFixture f,Entity mount) throws Exception{
        java.lang.reflect.Field bounds=Entity.class.getDeclaredField("boundingBox");
        bounds.setAccessible(true);bounds.set(f.player,
            net.minecraft.util.AxisAlignedBB.getBoundingBox(0D,0D,0D,0D,0D,0D));
        f.player.setPosition(mount.posX,mount.posY,mount.posZ);f.player.mountEntity(mount);
    }

    @Test public void rollbackDiscoveryDeduplicatesIdentityAndIgnoresDeadDeferredRemoval()
            throws Exception{
        KOMEAccessFixture f=new KOMEAccessFixture();f.world.flatTerrain=true;
        EntityHorse original=new EntityHorse(f.world);original.setPosition(8D,65D,8D);
        original.setHorseTamed(true);original.setHorseSaddled(true);original.setHealth(20F);
        NBTTagCompound snapshot=new NBTTagCompound();assertTrue(original.writeMountToNBT(snapshot));
        UUID uuid=original.getUniqueID();String type=EntityList.getEntityString(original);
        KOMEJoinBattleDeploymentReceipt receipt=mountedRecoveryReceipt(
            f,uuid,type,snapshot,new Pose(0,8D,65D,8D,0F,0F),
            new Pose(0,144D,65D,144D,0F,0F));

        KOMEJoinBattlePhysicalAccess.RollbackMountResolution same=
            KOMEJoinBattlePhysicalAccess.resolveRollbackMounts(f.player,receipt,
                Arrays.<Entity>asList(original,original),original);
        assertTrue(same.reason.name(),same.success());assertSame(original,same.source);
        assertTrue(same.remove.isEmpty());

        EntityHorse dead=cloneHorse(f,snapshot,8D,65D,8D);dead.setDead();
        EntityHorse destination=cloneHorse(f,snapshot,144D,65D,144D);
        KOMEJoinBattlePhysicalAccess.RollbackMountResolution oneLive=
            KOMEJoinBattlePhysicalAccess.resolveRollbackMounts(f.player,receipt,
                Arrays.<Entity>asList(dead,destination),null);
        assertTrue(oneLive.reason.name(),oneLive.success());assertSame(destination,oneLive.source);
        assertTrue("The sole live survivor must never be deleted for historical reconstruction",oneLive.remove.isEmpty());
    }

    @Test public void cancellationChoosesEquivalentRiddenSourceAndRemovesDestinationDuplicates()
            throws Exception{
        KOMEAccessFixture f=new KOMEAccessFixture();f.world.flatTerrain=true;
        EntityHorse original=new EntityHorse(f.world);original.setPosition(8D,65D,8D);
        original.setHorseType(1);original.setHorseVariant(513);
        original.setHorseTamed(true);original.setHorseSaddled(true);original.setHealth(17F);
        NBTTagCompound snapshot=new NBTTagCompound();assertTrue(original.writeMountToNBT(snapshot));
        UUID uuid=original.getUniqueID();String type=EntityList.getEntityString(original);
        Pose sourcePose=new Pose(0,8D,65D,8D,0F,0F),
            destinationPose=new Pose(0,144D,65D,144D,0F,0F);
        KOMEJoinBattleDeploymentReceipt receipt=mountedRecoveryReceipt(
            f,uuid,type,snapshot,sourcePose,destinationPose);
        EntityHorse source=cloneHorse(f,snapshot,40D,65D,8D);
        EntityHorse sourceDuplicate=cloneHorse(f,snapshot,9D,65D,9D);
        EntityHorse destination=cloneHorse(f,snapshot,144D,65D,144D);
        java.lang.reflect.Field bounds=Entity.class.getDeclaredField("boundingBox");
        bounds.setAccessible(true);bounds.set(f.player,
            net.minecraft.util.AxisAlignedBB.getBoundingBox(0D,0D,0D,0D,0D,0D));
        f.player.setPosition(source.posX,source.posY,source.posZ);f.player.mountEntity(source);

        KOMEJoinBattlePhysicalAccess.RollbackMountResolution resolution=
            KOMEJoinBattlePhysicalAccess.resolveRollbackMounts(f.player,receipt,
                Arrays.<Entity>asList(source,sourceDuplicate,destination),source);

        assertTrue(resolution.reason.name(),resolution.success());assertSame(source,resolution.source);
        assertEquals(2,resolution.remove.size());assertTrue(resolution.remove.contains(sourceDuplicate));
        assertTrue(resolution.remove.contains(destination));assertFalse(resolution.remove.contains(source));
        assertTrue(KOMEJoinBattlePhysicalAccess.semanticallyEquivalentMountState(snapshot,source));
        assertEquals(17F,source.getHealth(),0F);assertTrue(source.isTame());
        assertEquals(513,source.getHorseVariant());
    }

    @Test public void duplicateRecoveryFailsClosedForMaterialStateOrOutsideAuthority()
            throws Exception{
        KOMEAccessFixture f=new KOMEAccessFixture();f.world.flatTerrain=true;
        EntityHorse original=new EntityHorse(f.world);original.setPosition(8D,65D,8D);
        original.setHorseTamed(true);original.setHorseSaddled(true);original.setHealth(20F);
        NBTTagCompound snapshot=new NBTTagCompound();assertTrue(original.writeMountToNBT(snapshot));
        UUID uuid=original.getUniqueID();String type=EntityList.getEntityString(original);
        KOMEJoinBattleDeploymentReceipt receipt=mountedRecoveryReceipt(
            f,uuid,type,snapshot,new Pose(0,8D,65D,8D,0F,0F),
            new Pose(0,144D,65D,144D,0F,0F));
        EntityHorse source=cloneHorse(f,snapshot,8D,65D,8D);
        EntityHorse changed=cloneHorse(f,snapshot,144D,65D,144D);changed.setHealth(12F);
        KOMEJoinBattlePhysicalAccess.RollbackMountResolution mismatch=
            KOMEJoinBattlePhysicalAccess.resolveRollbackMounts(f.player,receipt,
                Arrays.<Entity>asList(source,changed),source);
        assertEquals(KOMEJoinBattleService.Reason.MOUNT_DUPLICATE_STATE_MISMATCH,mismatch.reason);

        EntityHorse outside=cloneHorse(f,snapshot,400D,65D,400D);
        KOMEJoinBattlePhysicalAccess.RollbackMountResolution unknown=
            KOMEJoinBattlePhysicalAccess.resolveRollbackMounts(f.player,receipt,
                Arrays.<Entity>asList(source,outside),source);
        assertEquals(KOMEJoinBattleService.Reason.MOUNT_COPY_OUTSIDE_RECOVERY_AREA,unknown.reason);
    }

    @Test public void mountEquivalenceIgnoresTransferRuntimeButPreservesGameplayState(){
        NBTTagCompound expected=new NBTTagCompound();
        expected.setLong("UUIDMost",1L);expected.setLong("UUIDLeast",2L);
        expected.setShort("Health",(short)25);expected.setBoolean("NPCTamed",true);
        expected.setString("NPCTamer","owner");expected.setInteger("NPCTemper",25);
        expected.setByte("WargType",(byte)2);
        NBTTagCompound saddle=new NBTTagCompound();saddle.setShort("id",(short)329);
        saddle.setByte("Count",(byte)1);expected.setTag("WargSaddleItem",saddle);
        NBTTagCompound moved=(NBTTagCompound)expected.copy();
        moved.setInteger("Dimension",100);moved.setBoolean("OnGround",false);
        moved.setTag("Pos",new net.minecraft.nbt.NBTTagList());
        assertTrue(KOMEJoinBattlePhysicalAccess.semanticallyEquivalentMountNbt(expected,moved));

        for(String key:new String[]{"Health","NPCTamed","NPCTamer","NPCTemper","WargType",
                "WargSaddleItem"}){
            NBTTagCompound changed=(NBTTagCompound)moved.copy();
            if("Health".equals(key))changed.setShort(key,(short)17);
            else if("NPCTamed".equals(key))changed.setBoolean(key,false);
            else if("NPCTamer".equals(key))changed.setString(key,"other");
            else if("NPCTemper".equals(key))changed.setInteger(key,73);
            else if("WargType".equals(key))changed.setByte(key,(byte)1);
            else changed.setTag(key,new NBTTagCompound());
            assertFalse(key,KOMEJoinBattlePhysicalAccess.semanticallyEquivalentMountNbt(
                expected,changed));
        }
    }

    @Test public void mountedRiderSynchronizationUsesVanillaAttachAndDerivedRiderPosition() throws Exception{
        KOMEAccessFixture f=new KOMEAccessFixture();f.world.flatTerrain=true;
        EntityHorse mount=new EntityHorse(f.world);mount.setEntityId(77);
        mount.setPosition(8.5D,65D,9.5D);f.player.width=0.6F;f.player.height=1.8F;
        f.player.yOffset=0F;
        java.lang.reflect.Field bounds=Entity.class.getDeclaredField("boundingBox");
        bounds.setAccessible(true);bounds.set(f.player,
            net.minecraft.util.AxisAlignedBB.getBoundingBox(0D,0D,0D,0D,0D,0D));
        f.player.setEntityId(23);f.player.setPosition(1D,65D,1D);

        KOMEJoinBattlePhysicalAccess.RiderSyncResult synchronizedRide=
            KOMEJoinBattlePhysicalAccess.synchronizeMountedPlayerDetailed(f.player,mount);
        assertEquals(KOMEJoinBattlePhysicalAccess.RiderSyncStage.SUCCESS,synchronizedRide.stage);
        assertEquals(KOMEJoinBattlePhysicalAccess.MountPublicationMode.ENTRY_OR_RECOVERY,
            synchronizedRide.publicationMode);
        assertTrue(synchronizedRide.playerCleanBeforeAttach);
        assertTrue(synchronizedRide.mountCleanBeforeAttach);
        assertTrue(synchronizedRide.afterMount);
        assertTrue(synchronizedRide.afterRiderUpdate);
        assertTrue(synchronizedRide.afterPositionSync);
        assertSame(mount,KOMEReflection.getRidingEntity(f.player));
        assertSame(f.player,KOMEReflection.getRiddenByEntity(mount));
        assertEquals(mount.posX,f.player.posX,0D);
        assertEquals(mount.posY+mount.getMountedYOffset()+f.player.getYOffset(),
            f.player.posY,0D);
        int attachments=0;
        for(Packet packet:f.player.vanillaPackets)if(packet instanceof S1BPacketEntityAttach){
            attachments++;
            S1BPacketEntityAttach attach=(S1BPacketEntityAttach)packet;
            assertEquals(f.player.getEntityId(),attach.func_149403_d());
            assertEquals(mount.getEntityId(),attach.func_149402_e());
        }
        assertTrue("mountEntity plus final authoritative resync must emit attachment",attachments>=2);
    }

    @Test public void orderedEgressUsesSharedAttachmentInvariantsAfterDirectionSpecificPublication()
            throws Exception{
        KOMEAccessFixture f=new KOMEAccessFixture();f.world.flatTerrain=true;
        EntityHorse returned=new EntityHorse(f.world);returned.setEntityId(88);
        returned.setPosition(8D,65D,8D);f.player.setEntityId(23);
        f.player.width=0.6F;f.player.height=1.8F;f.player.yOffset=0F;
        java.lang.reflect.Field bounds=Entity.class.getDeclaredField("boundingBox");
        bounds.setAccessible(true);bounds.set(f.player,
            net.minecraft.util.AxisAlignedBB.getBoundingBox(0D,0D,0D,0D,0D,0D));

        KOMEJoinBattlePhysicalAccess.RiderSyncResult result=
            KOMEJoinBattlePhysicalAccess.synchronizeMountedPlayerDetailed(f.player,returned,
                KOMEJoinBattlePhysicalAccess.MountPublicationMode.ORDERED_EGRESS);

        assertTrue(result.stage.name(),result.success());
        assertEquals(KOMEJoinBattlePhysicalAccess.MountPublicationMode.ORDERED_EGRESS,
            result.publicationMode);
        assertTrue(result.playerCleanBeforeAttach);assertTrue(result.mountCleanBeforeAttach);
        assertTrue(result.afterMount);assertTrue(result.afterRiderUpdate);
        assertTrue(result.afterPositionSync);
        assertSame(returned,KOMEReflection.getRidingEntity(f.player));
        assertSame(f.player,KOMEReflection.getRiddenByEntity(returned));
    }

    @Test public void mountedTransferDestroysOldTransientBeforeNewAttachAndLeavesOtherMounts()
            throws Exception{
        KOMEAccessFixture f=new KOMEAccessFixture();f.world.flatTerrain=true;
        EntityHorse oldMount=new EntityHorse(f.world);oldMount.setEntityId(77);
        oldMount.setHorseTamed(true);oldMount.func_152120_b(f.player.getUniqueID().toString());
        oldMount.setPosition(1D,65D,1D);
        NBTTagCompound snapshot=new NBTTagCompound();assertTrue(oldMount.writeMountToNBT(snapshot));
        EntityHorse returned=cloneHorse(f,snapshot,8D,65D,8D);returned.setEntityId(88);
        EntityHorse otherOwnedMount=new EntityHorse(f.world);otherOwnedMount.setEntityId(99);
        otherOwnedMount.setHorseTamed(true);
        otherOwnedMount.func_152120_b(f.player.getUniqueID().toString());
        otherOwnedMount.setPosition(3D,65D,3D);
        assertNotEquals(oldMount.getUniqueID(),otherOwnedMount.getUniqueID());
        f.world.loadedEntityList.add(oldMount);f.world.loadedEntityList.add(otherOwnedMount);
        f.player.setEntityId(23);f.player.width=0.6F;f.player.height=1.8F;f.player.yOffset=0F;
        java.lang.reflect.Field bounds=Entity.class.getDeclaredField("boundingBox");
        bounds.setAccessible(true);bounds.set(f.player,
            net.minecraft.util.AxisAlignedBB.getBoundingBox(0D,0D,0D,0D,0D,0D));
        f.player.setPosition(1D,65D,1D);f.player.mountEntity(oldMount);
        f.player.vanillaPackets.clear();

        assertTrue(KOMEJoinBattlePhysicalAccess.detachMountedPlayer(f.player,oldMount));
        assertTrue(KOMEJoinBattlePhysicalAccess.retireMountForTransfer(f.player,oldMount));
        assertTrue(KOMEJoinBattlePhysicalAccess.synchronizeMountedPlayer(f.player,returned));

        int destroy=-1,newAttach=-1;
        for(int i=0;i<f.player.vanillaPackets.size();i++){
            Packet packet=f.player.vanillaPackets.get(i);
            if(packet instanceof S13PacketDestroyEntities&&destroy<0){
                destroy=i;
                assertArrayEquals(new int[]{oldMount.getEntityId()},
                    ((S13PacketDestroyEntities)packet).func_149098_c());
            }
            if(packet instanceof S1BPacketEntityAttach
                    &&((S1BPacketEntityAttach)packet).func_149402_e()==returned.getEntityId()
                    &&newAttach<0)newAttach=i;
        }
        assertTrue("the old transient entity must be destroyed for the rider",destroy>=0);
        assertTrue("the new transient entity attaches only after old cleanup",
            newAttach>destroy);
        assertEquals(oldMount.getUniqueID(),returned.getUniqueID());
        assertSame(returned,KOMEReflection.getRidingEntity(f.player));
        assertSame(f.player,KOMEReflection.getRiddenByEntity(returned));
        assertTrue(oldMount.isDead);assertFalse(otherOwnedMount.isDead);
        assertTrue(otherOwnedMount.isTame());
    }

    @Test public void duplicateRecoveryIgnoresDifferentUuidOwnedMounts() throws Exception{
        KOMEAccessFixture f=new KOMEAccessFixture();f.world.flatTerrain=true;
        EntityHorse source=new EntityHorse(f.world);source.setPosition(8D,65D,8D);
        source.setHorseTamed(true);source.func_152120_b(f.player.getUniqueID().toString());
        NBTTagCompound snapshot=new NBTTagCompound();assertTrue(source.writeMountToNBT(snapshot));
        KOMEJoinBattleDeploymentReceipt receipt=mountedRecoveryReceipt(f,source.getUniqueID(),
            EntityList.getEntityString(source),snapshot,new Pose(0,8D,65D,8D,0F,0F),
            new Pose(0,144D,65D,144D,0F,0F));
        EntityHorse otherOwnedMount=new EntityHorse(f.world);otherOwnedMount.setPosition(9D,65D,9D);
        otherOwnedMount.setHorseTamed(true);
        otherOwnedMount.func_152120_b(f.player.getUniqueID().toString());

        KOMEJoinBattlePhysicalAccess.RollbackMountResolution resolution=
            KOMEJoinBattlePhysicalAccess.resolveRollbackMounts(f.player,receipt,
                Arrays.<Entity>asList(source,otherOwnedMount),source);

        assertTrue(resolution.reason.name(),resolution.success());assertSame(source,resolution.source);
        assertTrue(resolution.remove.isEmpty());assertFalse(otherOwnedMount.isDead);
        assertNotEquals(source.getUniqueID(),otherOwnedMount.getUniqueID());
    }

    @Test public void finalMountedVerificationIgnoresTheVerifiedPairButRejectsThirdPartyOverlap()
            throws Exception{
        KOMEAccessFixture f=new KOMEAccessFixture();f.world.flatTerrain=true;
        EntityHorse mount=new EntityHorse(f.world);mount.setPosition(8.5D,65D,9.5D);
        f.player.width=0.6F;f.player.height=1.8F;f.player.yOffset=0F;
        java.lang.reflect.Field bounds=Entity.class.getDeclaredField("boundingBox");
        bounds.setAccessible(true);bounds.set(f.player,
            net.minecraft.util.AxisAlignedBB.getBoundingBox(0D,0D,0D,0D,0D,0D));
        f.player.setPosition(1D,65D,1D);f.world.loadedEntityList.add(mount);
        Pose pose=new Pose(0,mount.posX,mount.posY,mount.posZ,0F,0F);

        assertTrue(KOMEJoinBattlePhysicalAccess.synchronizeMountedPlayer(f.player,mount));
        assertTrue("the live rider/mount pair must not collide with itself during final verification",
            KOMEJoinBattlePhysicalAccess.mountedStateCoherent(f.player,mount,pose,null));

        EntityHorse unrelated=new EntityHorse(f.world);
        unrelated.setPosition(mount.posX,mount.posY,mount.posZ);
        f.world.loadedEntityList.add(unrelated);
        assertFalse("an unrelated overlapping entity remains unsafe",
            KOMEJoinBattlePhysicalAccess.mountedStateCoherent(f.player,mount,pose,null));
    }

    @Test public void canonicalDismountMakesPlayerAndSourceCleanBeforeDestinationAttachment()
            throws Exception{
        KOMEAccessFixture f=new KOMEAccessFixture();f.world.flatTerrain=true;
        EntityHorse staleSource=new EntityHorse(f.world),destination=new EntityHorse(f.world);
        staleSource.setPosition(1D,65D,1D);destination.setPosition(8D,65D,8D);
        f.player.width=0.6F;f.player.height=1.8F;f.player.yOffset=0F;
        java.lang.reflect.Field bounds=Entity.class.getDeclaredField("boundingBox");
        bounds.setAccessible(true);bounds.set(f.player,
            net.minecraft.util.AxisAlignedBB.getBoundingBox(0D,0D,0D,0D,0D,0D));
        f.player.setPosition(1D,65D,1D);f.player.mountEntity(staleSource);
        assertTrue(KOMEJoinBattlePhysicalAccess.detachMountedPlayer(f.player,staleSource));
        assertNull(KOMEReflection.getRidingEntity(f.player));
        assertNull(KOMEReflection.getRiddenByEntity(staleSource));

        KOMEJoinBattlePhysicalAccess.RiderSyncResult result=
            KOMEJoinBattlePhysicalAccess.synchronizeMountedPlayerDetailed(f.player,destination);

        assertTrue(result.stage.name(),result.success());
        assertTrue(result.playerCleanBeforeAttach);assertTrue(result.mountCleanBeforeAttach);
        assertSame(destination,KOMEReflection.getRidingEntity(f.player));
        assertSame(f.player,KOMEReflection.getRiddenByEntity(destination));
    }

    @Test public void mountedPlacementRequiresSupportAndCollisionFreeMountAndRiderVolumes() throws Exception{
        KOMEAccessFixture f=new KOMEAccessFixture();f.world.flatTerrain=true;
        EntityHorse mount=new EntityHorse(f.world);f.player.width=0.6F;f.player.height=1.8F;
        f.player.yOffset=0F;
        Pose pose=new Pose(0,8.5D,65D,8.5D,0F,0F);
        assertTrue(KOMEJoinBattlePhysicalAccess.validateMountedPlacement(
            f.world,pose,f.player,mount));
        f.world.blockedCollision=true;
        assertFalse(KOMEJoinBattlePhysicalAccess.validateMountedPlacement(
            f.world,pose,f.player,mount));
        f.world.blockedCollision=false;f.world.unsafeSurface=true;
        assertFalse(KOMEJoinBattlePhysicalAccess.validateMountedPlacement(
            f.world,pose,f.player,mount));
    }

    @Test public void authoritativeAnchorMemberRejectsWrongTileOrdinaryAndTerminalRecords() throws Exception{
        Fixture f=new Fixture();KOMEArmyCompany company=f.data.armyCompanies.get("C1");
        UUID memberId=company.units.get(0);KOMEHiredUnitRecord member=f.data.hiredUnits.get(memberId);
        assertTrue(KOMEJoinBattlePhysicalAccess.validMember(company,member,memberId,"T100"));
        member.currentTile="T101";assertFalse(KOMEJoinBattlePhysicalAccess.validMember(company,member,memberId,"T100"));
        member.currentTile="T100";member.assignPersistedUnitClass(KOMEHiredUnitClass.ORDINARY);
        assertFalse(KOMEJoinBattlePhysicalAccess.validMember(company,member,memberId,"T100"));
        member.assignPersistedUnitClass(KOMEHiredUnitClass.CAMPAIGN);member.populationReturned=true;
        assertFalse(KOMEJoinBattlePhysicalAccess.validMember(company,member,memberId,"T100"));
    }

    private enum MountFailureStage { BEFORE_DESTRUCTION, AFTER_SOURCE_DESTRUCTION,
        AFTER_DESTINATION_CREATION, AFTER_PLAYER_TELEPORT, REMOUNT_VERIFICATION }

    @Test public void stableRollbackThenMissingMountNeverRearmsOrReconstructs() throws Exception {
        Fixture f=new Fixture();FakePhysical p=new FakePhysical(f.data);p.mounted=true;
        p.failureStage=MountFailureStage.AFTER_SOURCE_DESTRUCTION;
        KOMEJoinBattleEntryService.Result first=KOMEJoinBattleEntryService.INSTANCE.enter(f.data,f.access.player,f.request(f.token(),"C1"),p);
        KOMEJoinBattleDeploymentReceipt stable=f.data.getJoinBattleDeploymentReceipts().get(first.receiptId);
        assertEquals(MountTransferPhase.NOT_STARTED,stable.getMountTransferPhase());assertNull(stable.getTemporaryMountNbt());
        p.mounted=false;p.sourceMounts=0;p.failureStage=null;int deployments=p.deployments,rollbacks=p.rollbacks;
        KOMEJoinBattleEntryService.Result retry=KOMEJoinBattleEntryService.INSTANCE.reconcileAccepted(f.data,f.access.player,p);
        assertEquals(KOMEJoinBattleEntryService.Status.ENTRY_PENDING,retry.status);
        assertEquals(deployments,p.deployments);assertEquals(rollbacks,p.rollbacks);assertEquals(0,p.sourceMounts);
        assertNull(f.data.getJoinBattleDeploymentReceipts().get(first.receiptId).getTemporaryMountNbt());
    }

    @Test public void stableMountedRetryCapturesChangedStateAndNewSameCompanyPlacement() throws Exception {
        Fixture f=new Fixture();FakePhysical p=new FakePhysical(f.data);p.mounted=true;
        p.failureStage=MountFailureStage.AFTER_DESTINATION_CREATION;
        KOMEJoinBattleEntryService.Result first=KOMEJoinBattleEntryService.INSTANCE.enter(f.data,f.access.player,f.request(f.token(),"C1"),p);
        p.failureStage=null;p.mountHealth=7F;p.destinationX=100D;p.now=62000L;
        KOMEJoinBattleEntryService.Result retry=KOMEJoinBattleEntryService.INSTANCE.reconcileAccepted(f.data,f.access.player,p);
        assertEquals(retry.message,KOMEJoinBattleEntryService.Status.DEPLOYED,retry.status);
        assertEquals(7F,p.lastSnapshot.getFloat("Health"),0F);
        KOMEJoinBattleDeploymentReceipt receipt=f.data.getJoinBattleDeploymentReceipts().get(first.receiptId);
        assertEquals(100D,receipt.getDeploymentDestination().x,0D);assertEquals("C1",p.lastCompany);
        assertEquals("C1",receipt.getSelectedCompanyId());assertEquals(1,f.data.getJoinBattleDeploymentReceipts().records().size());
    }

    @Test public void acceptedUnmountedEntryReresolvesMovedCompanyBeyondOriginalRadius() throws Exception {
        Fixture f=new Fixture();FakePhysical p=new FakePhysical(f.data);p.deploySucceeds=false;
        KOMEJoinBattleEntryService.Result first=KOMEJoinBattleEntryService.INSTANCE.enter(f.data,f.access.player,f.request(f.token(),"C1"),p);
        p.deploySucceeds=true;p.destinationX=100D;p.now=62000L;
        KOMEJoinBattleEntryService.Result retry=KOMEJoinBattleEntryService.INSTANCE.reconcileAccepted(f.data,f.access.player,p);
        assertEquals(KOMEJoinBattleEntryService.Status.DEPLOYED,retry.status);
        assertEquals(100D,f.data.getJoinBattleDeploymentReceipts().get(first.receiptId).getDeploymentDestination().x,0D);
        assertEquals("C1",p.lastCompany);
    }

    @Test public void unresolvedConsumedSourceKeepsOriginalRecoveryCoordinatesAndSnapshot() throws Exception {
        Fixture f=new Fixture();FakePhysical p=new FakePhysical(f.data);p.mounted=true;
        p.failureStage=MountFailureStage.AFTER_SOURCE_DESTRUCTION;p.rollbackSucceeds=false;
        KOMEJoinBattleEntryService.Result first=KOMEJoinBattleEntryService.INSTANCE.enter(f.data,f.access.player,f.request(f.token(),"C1"),p);
        int preparations=p.preparations;p.destinationX=100D;p.mountHealth=7F;
        KOMEJoinBattleEntryService.INSTANCE.reconcileAccepted(f.data,f.access.player,p);
        KOMEJoinBattleDeploymentReceipt armed=f.data.getJoinBattleDeploymentReceipts().get(first.receiptId);
        assertEquals(preparations,p.preparations);assertEquals(10D,armed.getDeploymentDestination().x,0D);
        assertEquals(20F,armed.getTemporaryMountNbt().getFloat("Health"),0F);
        assertEquals(MountTransferPhase.DESTINATION_PUBLICATION_PENDING,armed.getMountTransferPhase());
    }

    @Test public void realStableHorseAllowsNewHealthWithoutHistoricalSnapshotAuthority() throws Exception {
        KOMEAccessFixture f=new KOMEAccessFixture();f.world.flatTerrain=true;
        EntityHorse horse=new EntityHorse(f.world);horse.setPosition(1D,65D,1D);
        NBTTagCompound n=new NBTTagCompound();horse.writeMountToNBT(n);
        Pose source=new Pose(0,1,65,1,0,0);
        KOMEJoinBattleDeploymentReceipt stable=KOMEJoinBattleDeploymentReceipt.copyOf(
            mountedRecoveryReceipt(f,horse.getUniqueID(),EntityList.getEntityString(horse),n,source,new Pose(0,144,65,144,0,0)))
            .mountTransferPhase(MountTransferPhase.NOT_STARTED).temporaryMountNbt(null).build();
        mountFixturePlayer(f,horse);horse.setHealth(3F);
        assertTrue(KOMEJoinBattlePhysicalAccess.hasExpectedRidingMount(f.player,stable));
        horse.setPosition(144D,65D,144D);
        assertEquals("Disarmed snapshots cannot consume a later live destination-side mount",
            KOMEJoinBattleService.Reason.MOUNT_LOCATION_AMBIGUOUS,
            KOMEJoinBattlePhysicalAccess.resolveRollbackMounts(f.player,stable,
                java.util.Collections.<net.minecraft.entity.Entity>singletonList(horse),null).reason);
        assertFalse(horse.isDead);
        horse.setDead();assertFalse(KOMEJoinBattlePhysicalAccess.hasExpectedRidingMount(f.player,stable));
        assertNull(stable.getTemporaryMountNbt());
    }

    @Test public void acceptedReplayAndSchedulerShareDeterministicDestructiveBackoff() throws Exception {
        Fixture f=new Fixture();FakePhysical p=new FakePhysical(f.data);p.mounted=true;
        p.failureStage=MountFailureStage.REMOUNT_VERIFICATION;
        p.rollbackReason=KOMEJoinBattleService.Reason.REMOUNT_FAILED;
        KOMEJoinBattleEntryService.Request request=f.request(f.token(),"C1");
        KOMEJoinBattleEntryService.Result first=KOMEJoinBattleEntryService.INSTANCE.enter(f.data,f.access.player,request,p);
        assertEquals(KOMEJoinBattleEntryService.Status.ENTRY_PENDING,first.status);
        assertEquals(1,p.deployments);int rollbacks=p.rollbacks,preparations=p.preparations;
        p.failureStage=null; // A repeated packet must not turn a stable rollback into another transfer.
        for(int i=0;i<20;i++) {
            p.now=2000L+i;
            KOMEJoinBattleEntryService.Result replay=KOMEJoinBattleEntryService.INSTANCE.enter(f.data,f.access.player,request,p);
            assertEquals(KOMEJoinBattleEntryService.Status.ENTRY_PENDING,replay.status);
            assertEquals(first.receiptId,replay.receiptId);assertFalse(replay.physicalAttempted);
        }
        KOMEJoinBattleEntryService.Result scheduled=KOMEJoinBattleEntryRecoveryService.INSTANCE.tick(f.data,f.access.player,10000L,p);
        assertFalse(scheduled.physicalAttempted);
        assertEquals(1,p.deployments);assertEquals(rollbacks,p.rollbacks);assertEquals(preparations,p.preparations);
        assertEquals(1,f.data.getJoinBattleDeploymentReceipts().records().size());
        // Re-login resets publication stabilization, not destructive-attempt admission.
        KOMEJoinBattleEntryRecoveryService.INSTANCE.onLogin(f.data,f.access.player,11000L,p);
        for(int i=1;i<=KOMEJoinBattleEntryRecoveryService.LOGIN_STABILIZATION_TICKS;i++)
            KOMEJoinBattleEntryRecoveryService.INSTANCE.tick(f.data,f.access.player,11000L+i,p);
        assertEquals(1,p.deployments);assertEquals(rollbacks,p.rollbacks);
        p.now=62000L;
        KOMEJoinBattleEntryService.Result completed=KOMEJoinBattleEntryRecoveryService.INSTANCE.tick(f.data,f.access.player,62000L,p);
        assertEquals(KOMEJoinBattleEntryService.Status.DEPLOYED,completed.status);
        assertEquals(first.receiptId,completed.receiptId);assertEquals(2,p.deployments);
        assertEquals(1,f.data.getJoinBattleDeploymentReceipts().records().size());
    }

    @Test public void transientRollbackUsesSameGateButShortCadence() throws Exception {
        Fixture f=new Fixture();FakePhysical p=new FakePhysical(f.data);p.mounted=true;
        p.failureStage=MountFailureStage.AFTER_SOURCE_DESTRUCTION;
        KOMEJoinBattleEntryService.Request request=f.request(f.token(),"C1");
        KOMEJoinBattleEntryService.INSTANCE.enter(f.data,f.access.player,request,p);
        p.failureStage=null;p.now=2000L;
        assertFalse(KOMEJoinBattleEntryService.INSTANCE.enter(f.data,f.access.player,request,p).physicalAttempted);
        assertEquals(1,p.deployments);
        p.now=7000L;
        assertEquals(KOMEJoinBattleEntryService.Status.DEPLOYED,
            KOMEJoinBattleEntryService.INSTANCE.enter(f.data,f.access.player,request,p).status);
        assertEquals(2,p.deployments);
    }

    @Test public void verifiedRollbackFinalizesLatestRebasedReceiptNotOlderCallerCopy() throws Exception {
        for(boolean cancellation:new boolean[]{false,true}) {
            Fixture f=new Fixture();FakePhysical p=new FakePhysical(f.data);p.mounted=true;
            p.failureStage=MountFailureStage.AFTER_SOURCE_DESTRUCTION;p.rollbackSucceeds=false;
            KOMEJoinBattleEntryService.Result first=KOMEJoinBattleEntryService.INSTANCE.enter(f.data,f.access.player,f.request(f.token(),"C1"),p);
            p.rollbackSucceeds=true;p.rebaseDuringRollback=true;p.now=7000L;
            KOMEJoinBattleEntryService.Result recovered=cancellation
                ?KOMEJoinBattleEntryService.INSTANCE.cancelAccepted(f.data,f.access.player,first.receiptId,7000L,"retreat",p)
                :KOMEJoinBattleEntryService.INSTANCE.reconcileAccepted(f.data,f.access.player,p);
            KOMEJoinBattleDeploymentReceipt current=f.data.getJoinBattleDeploymentReceipts().get(first.receiptId);
            assertEquals(500000L,current.getUpdatedAtMillis());assertNull(current.getTemporaryMountNbt());
            assertEquals(cancellation?KOMEJoinBattleDeploymentReceipt.State.CLOSED:KOMEJoinBattleDeploymentReceipt.State.PENDING_ENTRY,current.getState());
            assertEquals(cancellation?MountTransferPhase.TERMINAL:MountTransferPhase.NOT_STARTED,current.getMountTransferPhase());
            assertEquals(cancellation?KOMEJoinBattleEntryService.Status.REJECTED:KOMEJoinBattleEntryService.Status.ENTRY_PENDING,recovered.status);
        }
    }

    private static final class FakePhysical implements KOMEJoinBattleEntryService.PhysicalAccess{
        final KOMEWorldData data;boolean mounted,deploySucceeds=true,atDestination;
        boolean rollbackSucceeds=true,reportDeployedWithoutDestination,recoverMissingSourceOnce,rebaseDuringRollback;
        boolean sawPublishedReceipt,sawPendingMountSnapshot;int preparations,deployments;long now=1000L;
        int preflights;boolean preflightSinceDeployment,preflightBeforeEveryDeployment=true;
        int rollbacks,sourceMounts=1,destinationMounts;boolean playerAtSource=true,reciprocalRide=true;
        MountFailureStage failureStage;
        KOMEJoinBattleService.Reason rollbackReason=KOMEJoinBattleService.Reason.MOUNT_TRANSFER_ROLLED_BACK;
        double destinationX=10D;float mountHealth=20F;String lastCompany;NBTTagCompound lastSnapshot;
        KOMEJoinBattleService.Reason preparationReason=KOMEJoinBattleService.Reason.ALLOWED;
        final UUID mountId;
        FakePhysical(KOMEWorldData data){
            this.data=data;UUID existing=null;
            for(KOMEJoinBattleDeploymentReceipt receipt:data.getJoinBattleDeploymentReceipts().records().values())
                if(receipt.isOpen()&&receipt.isEnteredMounted())existing=receipt.getMountUuid();
            mountId=existing==null?UUID.randomUUID():existing;
        }
        @Override public long now(){return now++;}
        @Override public KOMEJoinBattleEntryService.Preparation prepare(KOMEWorldData ignored,
                net.minecraft.entity.player.EntityPlayerMP player,KOMEArmyCompany company,String tile,
                String conflictId,long conflictRevision){
            preparations++;
            lastCompany=company.id;
            if(preparationReason!=KOMEJoinBattleService.Reason.ALLOWED)
                return KOMEJoinBattleEntryService.Preparation.denied(preparationReason);
            Pose source=new Pose(0,1,65,1,0,0),destination=new Pose(0,destinationX,65,10,0,0);
            if(!mounted)return KOMEJoinBattleEntryService.Preparation.unmounted(source,destination);
            NBTTagCompound nbt=new NBTTagCompound();nbt.setString("id","EntityHorse");nbt.setString("proof","preserved");
            nbt.setFloat("Health",mountHealth);
            return KOMEJoinBattleEntryService.Preparation.mounted(source,destination,mountId,
                "EntityHorse",MountProfile.VANILLA_HORSE,source,nbt);
        }
        @Override public boolean isAtDestination(net.minecraft.entity.player.EntityPlayerMP player,
                KOMEJoinBattleDeploymentReceipt receipt){return atDestination;}
        @Override public KOMEJoinBattleService.Reason preflight(KOMEWorldData ignored,
                net.minecraft.entity.player.EntityPlayerMP player,
                KOMEJoinBattleDeploymentReceipt receipt){
            preflights++;preflightSinceDeployment=true;return preparationReason;
        }
        @Override public KOMEJoinBattleEntryService.PhysicalResult deploy(
                net.minecraft.entity.player.EntityPlayerMP player,KOMEJoinBattleDeploymentReceipt receipt){
            deployments++;preflightBeforeEveryDeployment&=preflightSinceDeployment;
            lastSnapshot=receipt.getTemporaryMountNbt();
            preflightSinceDeployment=false;
            sawPublishedReceipt=data.getJoinBattleDeploymentReceipts().get(receipt.getReceiptId())!=null;
            if(receipt.isEnteredMounted())sawPendingMountSnapshot=receipt.getTemporaryMountNbt()!=null
                &&receipt.getMountTransferPhase()==MountTransferPhase.DESTINATION_PUBLICATION_PENDING;
            if(recoverMissingSourceOnce&&sourceMounts==0){
                recoverMissingSourceOnce=false;
                KOMEJoinBattleEntryService.PhysicalResult restored=rollback(player,receipt);
                return restored.rollbackVerified
                    ?KOMEJoinBattleEntryService.PhysicalResult.rolledBack(
                        KOMEJoinBattleService.Reason.SOURCE_MOUNT_NOT_FOUND_AFTER_REMOVAL,
                        MountDisposition.RETURNED_WITH_PLAYER):restored;
            }
            if(failureStage!=null){
                if(failureStage!=MountFailureStage.BEFORE_DESTRUCTION){sourceMounts=0;reciprocalRide=false;}
                if(failureStage==MountFailureStage.AFTER_DESTINATION_CREATION
                        ||failureStage==MountFailureStage.AFTER_PLAYER_TELEPORT
                        ||failureStage==MountFailureStage.REMOUNT_VERIFICATION)destinationMounts=1;
                if(failureStage==MountFailureStage.AFTER_PLAYER_TELEPORT
                        ||failureStage==MountFailureStage.REMOUNT_VERIFICATION)playerAtSource=false;
                return rollback(player,receipt);
            }
            if(!deploySucceeds)return KOMEJoinBattleEntryService.PhysicalResult.pending(
                receipt.isEnteredMounted()?KOMEJoinBattleService.Reason.MOUNT_TRANSFER_FAILED:
                    KOMEJoinBattleService.Reason.SAFE_POSITION_UNAVAILABLE);
            if(!reportDeployedWithoutDestination)atDestination=true;
            return KOMEJoinBattleEntryService.PhysicalResult.deployed();
        }
        @Override public KOMEJoinBattleEntryService.PhysicalResult rollback(
                net.minecraft.entity.player.EntityPlayerMP player,
                KOMEJoinBattleDeploymentReceipt receipt){
            rollbacks++;
            if(!rollbackSucceeds)return KOMEJoinBattleEntryService.PhysicalResult.pending(
                KOMEJoinBattleService.Reason.MOUNT_TRANSFER_FAILED);
            if(rebaseDuringRollback)data.getJoinBattleDeploymentReceipts().replace(
                KOMEJoinBattleDeploymentReceipt.copyOf(receipt).updatedAtMillis(500000L).build());
            sourceMounts=1;destinationMounts=0;playerAtSource=true;reciprocalRide=true;
            atDestination=false;
            return KOMEJoinBattleEntryService.PhysicalResult.rolledBack(rollbackReason,MountDisposition.RETURNED_WITH_PLAYER);
        }
    }

    private static EntityHorse cloneHorse(KOMEAccessFixture fixture,NBTTagCompound snapshot,
            double x,double y,double z){
        EntityHorse copy=new EntityHorse(fixture.world);
        copy.readFromNBT((NBTTagCompound)snapshot.copy());copy.setPosition(x,y,z);return copy;
    }

    private static KOMEJoinBattleDeploymentReceipt mountedRecoveryReceipt(KOMEAccessFixture fixture,
            UUID mountUuid,String entityType,NBTTagCompound snapshot,Pose source,Pose destination){
        return KOMEJoinBattleDeploymentReceipt.builder().receiptId("JB99")
            .actionToken("0123456789abcdef0123456789abcdef").playerId(fixture.player.id)
            .conflictId("CF1").tileId("T100").acceptedConflictRevision(1L)
            .factionId("gondor").selectedCompanyId("C1").createdAtMillis(1L)
            .state(KOMEJoinBattleDeploymentReceipt.State.PENDING_ENTRY)
            .updatedAtMillis(2L).returnAnchor(source)
            .deploymentDestination(destination)
            .participationRecovery(ParticipationRecovery.PREEXISTING_ACTIVE)
            .enteredMounted(true).mountUuid(mountUuid).mountEntityType(entityType)
            .mountProfile(MountProfile.VANILLA_HORSE).mountSourceAnchor(source)
            .mountTransferPhase(MountTransferPhase.DESTINATION_PUBLICATION_PENDING)
            .temporaryMountNbt(snapshot).build();
    }

    private static final class Fixture{
        final KOMEAccessFixture access;final KOMEWorldData data;final UUID playerId;
        KOMEConflictRecord record;long now=10;
        Fixture()throws Exception{
            access=new KOMEAccessFixture();data=access.data;playerId=access.player.id;access.pledge(LOTRFaction.GONDOR);
            record=ok(data.getConflictService().start("T100",KOMEConflictRecord.State.ORDINARY,ExpectedConflict.absent(),
                Collections.<GarrisonSeed>emptyList(),context()));
            record=ok(data.getConflictService().beginFactionParticipation("T100",expected(),"gondor",context()));
            company("C1");record=ok(data.getConflictService().commit("T100",expected(),
                new CommitmentInput("C1",KOMEHiredUnitClass.CAMPAIGN,EntryOrigin.LEGAL_ARRIVAL,"M-C1"),context()));
        }
        void company(String id){
            KOMEArmyCompany c=new KOMEArmyCompany();c.id=id;c.owner=UUID.randomUUID();c.ownerName="Owner";
            c.faction="gondor";c.name="First Company";c.currentTile="T100";data.lastKnownPlayerFactions.put(c.owner,"gondor");
            KOMEHiredUnitRecord u=new KOMEHiredUnitRecord();u.entity=UUID.randomUUID();u.owner=c.owner;
            u.companyId=id;u.companyName=c.name;u.currentTile="T100";u.sourceTileId="T001";
            u.unitFaction="gondor";u.populationOwningFaction="gondor";u.type=KOMEPopulationType.OFFENSIVE;
            u.assignPersistedUnitClass(KOMEHiredUnitClass.CAMPAIGN);u.cost=u.baseCost=u.populationSpent=20;
            c.units.add(u.entity);c.totalPopulation=c.groundPopulation=20;data.armyCompanies.put(id,c);data.hiredUnits.put(u.entity,u);
        }
        String token(){KOMEJoinBattleService.Projection p=KOMEJoinBattleService.INSTANCE.evaluate(data,access.player,"T100");
            return KOMEJoinBattleActionTokenService.INSTANCE.issue(playerId,p);}
        KOMEJoinBattleEntryService.Request request(String token,String company){return new KOMEJoinBattleEntryService.Request(
            "T100",record.getConflictId(),record.getRevision(),company,token);}
        void register(){record=ok(data.getConflictService().registerPlayer("T100",expected(),playerId,"gondor",context()));}
        void withdraw(){record=ok(data.getConflictService().withdrawPlayer("T100",expected(),playerId,context()));}
        ExpectedConflict expected(){return ExpectedConflict.at(record.getConflictId(),record.getRevision());}
        Context context(){return new Context(now++,"test","entry fixture");}
    }
    private static KOMEConflictRecord ok(KOMEConflictService.Result result){
        assertTrue(result.code+": "+result.reason,result.isSuccess());return result.record;
    }
}
