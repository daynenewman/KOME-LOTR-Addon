package kome.common.data;

import java.util.Map;
import java.util.UUID;

import kome.common.KOMEReflection;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;

import static kome.common.data.KOMEConflictContracts.Code;
import static kome.common.data.KOMEConflictContracts.Context;
import static kome.common.data.KOMEConflictContracts.ExpectedConflict;
import static kome.common.data.KOMEJoinBattleDeploymentReceipt.MountTransferPhase;
import static kome.common.data.KOMEJoinBattleDeploymentReceipt.ParticipationRecovery;
import static kome.common.data.KOMEJoinBattleDeploymentReceipt.State;

/** Server-thread orchestration for durable, idempotent physical Join Battle entry. */
public final class KOMEJoinBattleEntryService {
    public static final KOMEJoinBattleEntryService INSTANCE = new KOMEJoinBattleEntryService();

    public enum Status { DEPLOYED, ENTRY_PENDING, REJECTED }

    public static final class Request {
        public final String tileId, conflictId, companyId, actionToken;
        public final long conflictRevision;
        public Request(String tile, String conflict, long revision, String company, String token) {
            tileId=tile==null?"":tile;conflictId=conflict==null?"":conflict;
            conflictRevision=revision;companyId=company==null?"":company;
            actionToken=token==null?"":token;
        }
    }

    public static final class Result {
        public final Status status;
        public final KOMEJoinBattleService.Reason reason;
        public final String message, receiptId, actionToken;
        public final KOMEJoinBattleService.Projection current;
        /** True only when this call crossed into physical deployment/rollback work. */
        final boolean physicalAttempted;
        private Result(Status status, KOMEJoinBattleService.Reason reason, String message,
                String receiptId, String actionToken, KOMEJoinBattleService.Projection current,
                boolean physicalAttempted) {
            this.status=status;this.reason=reason;this.message=message;
            this.receiptId=receiptId==null?"":receiptId;
            this.actionToken=actionToken==null?"":actionToken;this.current=current;
            this.physicalAttempted=physicalAttempted;
        }
    }

    /**
     * Prepared before receipt publication. Preparation may hold a bounded temporary chunk ticket,
     * but it never moves the player or mount and releases the ticket before returning.
     */
    static final class Preparation {
        final KOMEJoinBattleService.Reason reason;
        final KOMEJoinBattleDeploymentReceipt.Pose returnAnchor, destination;
        final boolean mounted;
        final UUID mountUuid;
        final String mountEntityType;
        final KOMEJoinBattleDeploymentReceipt.MountProfile mountProfile;
        final KOMEJoinBattleDeploymentReceipt.Pose mountSource;
        final NBTTagCompound mountNbt;

        private Preparation(KOMEJoinBattleService.Reason reason,
                KOMEJoinBattleDeploymentReceipt.Pose returnAnchor,
                KOMEJoinBattleDeploymentReceipt.Pose destination, boolean mounted,
                UUID mountUuid, String mountEntityType,
                KOMEJoinBattleDeploymentReceipt.MountProfile mountProfile,
                KOMEJoinBattleDeploymentReceipt.Pose mountSource, NBTTagCompound mountNbt) {
            this.reason=reason;this.returnAnchor=returnAnchor;this.destination=destination;
            this.mounted=mounted;this.mountUuid=mountUuid;this.mountEntityType=mountEntityType;
            this.mountProfile=mountProfile;this.mountSource=mountSource;
            this.mountNbt=mountNbt==null?null:(NBTTagCompound)mountNbt.copy();
        }
        static Preparation denied(KOMEJoinBattleService.Reason reason) {
            return new Preparation(reason,null,null,false,null,"",null,null,null);
        }
        static Preparation unmounted(KOMEJoinBattleDeploymentReceipt.Pose source,
                KOMEJoinBattleDeploymentReceipt.Pose destination) {
            return new Preparation(KOMEJoinBattleService.Reason.ALLOWED,source,destination,
                false,null,"",null,null,null);
        }
        static Preparation mounted(KOMEJoinBattleDeploymentReceipt.Pose source,
                KOMEJoinBattleDeploymentReceipt.Pose destination, UUID mountUuid,
                String mountEntityType, KOMEJoinBattleDeploymentReceipt.MountProfile profile,
                KOMEJoinBattleDeploymentReceipt.Pose mountSource, NBTTagCompound nbt) {
            return new Preparation(KOMEJoinBattleService.Reason.ALLOWED,source,destination,true,
                mountUuid,mountEntityType,profile,mountSource,nbt);
        }
    }

    static final class PhysicalResult {
        final boolean deployed;
        final boolean rollbackVerified;
        final KOMEJoinBattleService.Reason reason;
        final KOMEJoinBattleDeploymentReceipt.MountDisposition mountDisposition;
        private PhysicalResult(boolean deployed, boolean rollbackVerified,
                KOMEJoinBattleService.Reason reason,
                KOMEJoinBattleDeploymentReceipt.MountDisposition disposition) {
            this.deployed=deployed;this.rollbackVerified=rollbackVerified;
            this.reason=reason;this.mountDisposition=disposition;
        }
        static PhysicalResult deployed(){return new PhysicalResult(true,false,
            KOMEJoinBattleService.Reason.ALLOWED,null);}
        static PhysicalResult pending(KOMEJoinBattleService.Reason reason){return new PhysicalResult(
            false,false,reason,null);}
        static PhysicalResult rolledBack(KOMEJoinBattleDeploymentReceipt.MountDisposition disposition){
            return new PhysicalResult(false,true,
                KOMEJoinBattleService.Reason.MOUNT_TRANSFER_ROLLED_BACK,disposition);
        }
        static PhysicalResult rolledBack(KOMEJoinBattleService.Reason reason,
                KOMEJoinBattleDeploymentReceipt.MountDisposition disposition){
            return new PhysicalResult(false,true,reason==null
                ?KOMEJoinBattleService.Reason.MOUNT_TRANSFER_ROLLED_BACK:reason,disposition);
        }
    }

    interface PhysicalAccess {
        long now();
        Preparation prepare(KOMEWorldData data, EntityPlayerMP player,
            KOMEArmyCompany company, String tileId, String conflictId, long conflictRevision);
        boolean isAtDestination(EntityPlayerMP player, KOMEJoinBattleDeploymentReceipt receipt);
        /** Revalidates everything possible without consuming the source player/mount state. */
        KOMEJoinBattleService.Reason preflight(KOMEWorldData data, EntityPlayerMP player,
            KOMEJoinBattleDeploymentReceipt receipt);
        PhysicalResult deploy(EntityPlayerMP player, KOMEJoinBattleDeploymentReceipt receipt);
        PhysicalResult rollback(EntityPlayerMP player, KOMEJoinBattleDeploymentReceipt receipt);
    }

    private KOMEJoinBattleEntryService() { }

    static Result waitingForLoginMount(KOMEJoinBattleDeploymentReceipt receipt) {
        return new Result(Status.ENTRY_PENDING,KOMEJoinBattleService.Reason.ENTRY_ALREADY_IN_PROGRESS,
            "KOME is safely restoring your mounted deployment.",receipt.getReceiptId(),
            receipt.getActionToken(),null,false);
    }

    public Result enter(KOMEWorldData data, EntityPlayerMP player, Request request) {
        return enter(data,player,request,KOMEJoinBattlePhysicalAccess.INSTANCE);
    }

    Result enter(KOMEWorldData data, EntityPlayerMP player, Request request,
            PhysicalAccess physical) {
        if (data==null||player==null||request==null||physical==null)
            return rejected(data,player,request,KOMEJoinBattleService.Reason.INVALID_REQUEST);
        UUID playerId=KOMEReflection.getEntityUUID(player);

        KOMEJoinBattleDeploymentReceipt replay=data.getJoinBattleDeploymentReceipts()
            .findByActionToken(request.actionToken);
        if(replay!=null){
            if(!matches(replay,playerId,request))
                return rejected(data,player,request,KOMEJoinBattleService.Reason.INVALID_ACTION_TOKEN);
            if(replay.getState()==State.DEPLOYED)
                return deployed(data,player,replay,"Joined battle beside "+replay.getSelectedCompanyId()+".");
            if(replay.getState()==State.CLOSED
                    &&replay.getClosureOutcome()==KOMEJoinBattleDeploymentReceipt.ClosureOutcome.ENTRY_CANCELLED)
                return rejected(current(data,player,replay.getTileId()),
                    replay.isEnteredMounted()?KOMEJoinBattleService.Reason.MOUNT_TRANSFER_ROLLED_BACK:
                        KOMEJoinBattleService.Reason.INVALID_ACTION_TOKEN);
            if(replay.getState()!=State.PENDING_ENTRY)
                return rejected(data,player,request,KOMEJoinBattleService.Reason.ALREADY_DEPLOYED);
            return resume(data,player,replay,physical,false,physical.now());
        }

        KOMEJoinBattleActionTokenService.Validation token=
            KOMEJoinBattleActionTokenService.INSTANCE.validate(request.actionToken,playerId,
                request.tileId,request.conflictId,request.conflictRevision,request.companyId);
        if(token!=KOMEJoinBattleActionTokenService.Validation.ACCEPTED)
            return rejected(data,player,request,KOMEJoinBattleService.Reason.INVALID_ACTION_TOKEN);

        ExpectedConflict expected;
        try{expected=ExpectedConflict.at(request.conflictId,request.conflictRevision);}
        catch(IllegalArgumentException invalid){
            return rejected(data,player,request,KOMEJoinBattleService.Reason.INVALID_REQUEST);
        }
        KOMEJoinBattleService.SelectionResult selection=
            KOMEJoinBattleService.INSTANCE.validateSelectedCompany(data,player,request.tileId,
                expected,request.companyId);
        if(!selection.isAllowed())return rejected(selection.current,selection.reason);

        KOMEJoinBattleDeploymentReceipt open=findOpen(data,playerId);
        if(open!=null){
            if(open.getState()==State.PENDING_ENTRY&&sameEntryIdentity(open,playerId,request)){
                // A restart discards transient view tokens, but the accepted token remains in the
                // durable receipt. A fresh, fully validated token for the same exact action may
                // resume recovery; it never replaces the receipt identity or creates a second JB.
                KOMEJoinBattleActionTokenService.INSTANCE.consume(request.actionToken);
                return resume(data,player,open,physical,false,physical.now());
            }
            return rejectedOpen(selection.current,open);
        }

        KOMEArmyCompany company=data.armyCompanies.get(selection.selectedCompany.companyId);
        Preparation prepared=physical.prepare(data,player,company,selection.current.tileId,
            selection.current.conflictId,selection.current.conflictRevision);
        if(prepared==null||prepared.reason!=KOMEJoinBattleService.Reason.ALLOWED)
            return pendingUnpublished(selection.current,prepared==null
                ?KOMEJoinBattleService.Reason.SAFE_POSITION_UNAVAILABLE:prepared.reason,
                request.actionToken);

        KOMEConflictRecord record=data.getConflictService().get(selection.current.tileId);
        KOMEConflictRecord.PlayerParticipation participation=record==null?null:
            record.getPlayers().get(playerId);
        ParticipationRecovery recovery=participation==null
            ?ParticipationRecovery.REGISTRATION_REQUIRED:ParticipationRecovery.PREEXISTING_ACTIVE;
        long now=Math.max(physical.now(),record.getLastTransition().timestampMillis);
        KOMEJoinBattleDeploymentReceipt.Builder builder=KOMEJoinBattleDeploymentReceipt.builder()
            .receiptId(data.getJoinBattleDeploymentReceipts().nextReceiptId())
            .actionToken(request.actionToken).playerId(playerId)
            .conflictId(selection.current.conflictId).tileId(selection.current.tileId)
            .acceptedConflictRevision(selection.current.conflictRevision)
            .factionId(selection.current.playerFactionId)
            .selectedCompanyId(selection.selectedCompany.companyId)
            .createdAtMillis(now).updatedAtMillis(now).state(State.PENDING_ENTRY)
            .returnAnchor(prepared.returnAnchor).deploymentDestination(prepared.destination)
            .participationRecovery(recovery).enteredMounted(prepared.mounted);
        if(prepared.mounted)builder.mountUuid(prepared.mountUuid)
            .mountEntityType(prepared.mountEntityType).mountProfile(prepared.mountProfile)
            .mountSourceAnchor(prepared.mountSource)
            .mountTransferPhase(MountTransferPhase.SOURCE_SNAPSHOT_PERSISTED)
            .temporaryMountNbt(prepared.mountNbt);
        KOMEJoinBattleDeploymentReceipt receipt;
        try{
            receipt=builder.build();
            data.ensureWritable();
            data.getJoinBattleDeploymentReceipts().publishNew(receipt);
            data.markDirty();
            KOMEJoinBattleActionTokenService.INSTANCE.consume(request.actionToken);
        }catch(RuntimeException failure){
            return rejected(selection.current,KOMEJoinBattleService.Reason.INVALID_REQUEST);
        }

        KOMEJoinBattleService.SelectionResult revalidated=
            KOMEJoinBattleService.INSTANCE.validateSelectedCompany(data,player,receipt.getTileId(),
                ExpectedConflict.at(receipt.getConflictId(),receipt.getAcceptedConflictRevision()),
                receipt.getSelectedCompanyId());
        if(!revalidated.isAllowed())return pending(revalidated.current,revalidated.reason,
            receipt.getReceiptId(),receipt);
        return resume(data,player,receipt,physical,true,physical.now());
    }

    /**
     * Continues an already accepted durable entry without requiring a client token or another
     * Join click.  The receipt's immutable player/conflict/company identity is the recovery
     * authority; all mutable gameplay authority is revalidated on every attempt.
     */
    public Result reconcileAccepted(KOMEWorldData data, EntityPlayerMP player) {
        return reconcileAccepted(data,player,KOMEJoinBattlePhysicalAccess.INSTANCE);
    }

    Result reconcileAccepted(KOMEWorldData data,EntityPlayerMP player,PhysicalAccess physical) {
        return reconcileAccepted(data,player,physical,physical==null?0L:physical.now());
    }

    Result reconcileAccepted(KOMEWorldData data,EntityPlayerMP player,PhysicalAccess physical,
            long attemptAtMillis) {
        if(data==null||player==null||physical==null)return rejected(data,player,null,
            KOMEJoinBattleService.Reason.INVALID_REQUEST);
        KOMEJoinBattleDeploymentReceipt receipt=findOpen(data,KOMEReflection.getEntityUUID(player));
        if(receipt==null)return rejected(data,player,null,KOMEJoinBattleService.Reason.INVALID_REQUEST);
        if(receipt.getState()==State.DEPLOYED)return deployed(data,player,receipt,
            "Joined battle beside "+receipt.getSelectedCompanyId()+".");
        if(receipt.getState()!=State.PENDING_ENTRY)return pending(current(data,player,
            receipt.getTileId()),KOMEJoinBattleService.Reason.ENTRY_ALREADY_IN_PROGRESS,
            receipt.getReceiptId(),receipt);
        return resume(data,player,receipt,physical,false,attemptAtMillis);
    }

    /** Safely cancels one exact accepted-but-unfinished entry. */
    public Result cancelAccepted(KOMEWorldData data,EntityPlayerMP player,String receiptId,
            long requestedNow,String reason) {
        return cancelAccepted(data,player,receiptId,requestedNow,reason,
            KOMEJoinBattlePhysicalAccess.INSTANCE);
    }

    Result cancelAccepted(KOMEWorldData data,EntityPlayerMP player,String receiptId,
            long requestedNow,String reason,PhysicalAccess physical) {
        if(data==null||player==null||receiptId==null||physical==null)
            return rejected(data,player,null,KOMEJoinBattleService.Reason.INVALID_REQUEST);
        KOMEJoinBattleDeploymentReceipt receipt=data.getJoinBattleDeploymentReceipts().get(receiptId);
        if(receipt==null||!receipt.getPlayerId().equals(KOMEReflection.getEntityUUID(player)))
            return rejected(data,player,null,KOMEJoinBattleService.Reason.INVALID_REQUEST);
        if(receipt.getState()==State.CLOSED)return rejected(current(data,player,receipt.getTileId()),
            KOMEJoinBattleService.Reason.INVALID_ACTION_TOKEN);
        if(receipt.getState()!=State.PENDING_ENTRY)return pending(current(data,player,
            receipt.getTileId()),KOMEJoinBattleService.Reason.ENTRY_ALREADY_IN_PROGRESS,
            receipt.getReceiptId(),receipt);
        PhysicalResult rolledBack=physical.rollback(player,receipt);
        if(rolledBack==null||!rolledBack.rollbackVerified)return pending(current(data,player,
            receipt.getTileId()),rolledBack==null?KOMEJoinBattleService.Reason.MOUNT_TRANSFER_FAILED:
                rolledBack.reason,receipt.getReceiptId(),receipt);
        rolledBack=PhysicalResult.rolledBack(KOMEJoinBattleService.Reason.ENTRY_CANCELLED,
            rolledBack.mountDisposition);
        return closeCancelled(data,player,receipt,requestedNow,rolledBack,reason);
    }

    private Result resume(KOMEWorldData data, EntityPlayerMP player,
            KOMEJoinBattleDeploymentReceipt receipt, PhysicalAccess physical,
            boolean alreadyPrepared,long attemptAtMillis) {
        KOMEJoinBattleEntryRecoveryService gate=KOMEJoinBattleEntryRecoveryService.INSTANCE;
        KOMEJoinBattleEntryRecoveryService.Attempt attempt=gate.beginAttempt(data,receipt,attemptAtMillis);
        if(attempt==null)return pending(current(data,player,receipt.getTileId()),
            KOMEJoinBattleService.Reason.ENTRY_ALREADY_IN_PROGRESS,receipt.getReceiptId(),receipt,false);
        Result result=null;
        try{
            result=resumeAdmitted(data,player,receipt,physical,alreadyPrepared);
            return result;
        }finally{gate.finishAttempt(data,receipt,attempt,attemptAtMillis,result);}
    }

    private Result resumeAdmitted(KOMEWorldData data,EntityPlayerMP player,
            KOMEJoinBattleDeploymentReceipt receipt,PhysicalAccess physical,boolean alreadyPrepared) {
        KOMEConflictRecord record=data.getConflictService().get(receipt.getTileId());
        if(record==null||!record.isActive()||!record.getConflictId().equals(receipt.getConflictId()))
            return cancelInvalidAccepted(data,player,receipt,physical,
                KOMEJoinBattleService.Reason.STALE_CONFLICT,"The battle ended or was replaced.");
        KOMEJoinBattleService.SelectionResult selection=
            KOMEJoinBattleService.INSTANCE.validateSelectedCompany(data,player,receipt.getTileId(),
                ExpectedConflict.at(record.getConflictId(),record.getRevision()),
                receipt.getSelectedCompanyId());
        if(!selection.isAllowed())return cancelInvalidAccepted(data,player,receipt,physical,
            selection.reason,"Join Battle authority is no longer valid.");

        boolean atCurrentDestination=physical.isAtDestination(player,receipt)
            &&physical.preflight(data,player,receipt)==KOMEJoinBattleService.Reason.ALLOWED;
        if(!atCurrentDestination){
            if(receipt.isEnteredMounted() && receipt.getMountTransferPhase()
                    ==MountTransferPhase.DESTINATION_PUBLICATION_PENDING){
                PhysicalResult recovered=physical.rollback(player,receipt);
                if(recovered!=null&&recovered.rollbackVerified)
                    receipt=disarmRestoredSource(data,receipt,physical.now());
                return pending(selection.current,recovered==null
                    ?KOMEJoinBattleService.Reason.MOUNT_TRANSFER_FAILED:recovered.reason,
                    receipt.getReceiptId(),receipt,true);
            }
            if(!alreadyPrepared){
                Preparation fresh=physical.prepare(data,player,
                    data.armyCompanies.get(receipt.getSelectedCompanyId()),receipt.getTileId(),
                    receipt.getConflictId(),record.getRevision());
                if(fresh==null||fresh.reason!=KOMEJoinBattleService.Reason.ALLOWED)
                    return pending(selection.current,fresh==null
                        ?KOMEJoinBattleService.Reason.SAFE_POSITION_UNAVAILABLE:fresh.reason,
                        receipt.getReceiptId(),receipt,false);
                if(fresh.mounted!=receipt.isEnteredMounted() || fresh.mounted
                        &&(!receipt.getMountUuid().equals(fresh.mountUuid)
                            ||!receipt.getMountEntityType().equals(fresh.mountEntityType)
                            ||receipt.getMountProfile()!=fresh.mountProfile))
                    return pending(selection.current,KOMEJoinBattleService.Reason.INVALID_MOUNT_RELATIONSHIP,
                        receipt.getReceiptId(),receipt,false);
                KOMEJoinBattleDeploymentReceipt.Builder prepared=KOMEJoinBattleDeploymentReceipt.copyOf(receipt)
                    .deploymentDestination(fresh.destination).updatedAtMillis(Math.max(physical.now(),receipt.getUpdatedAtMillis()));
                if(fresh.mounted)prepared.mountTransferPhase(MountTransferPhase.SOURCE_SNAPSHOT_PERSISTED)
                    .temporaryMountNbt(fresh.mountNbt);
                receipt=prepared.build();
                data.ensureWritable();data.getJoinBattleDeploymentReceipts().prepareEntryAttempt(receipt);data.markDirty();
            }
            KOMEJoinBattleService.Reason preflight=physical.preflight(data,player,receipt);
            if(preflight!=KOMEJoinBattleService.Reason.ALLOWED)
                return pending(selection.current,preflight,receipt.getReceiptId(),receipt,false);
            if(receipt.isEnteredMounted()
                    &&receipt.getMountTransferPhase()==MountTransferPhase.SOURCE_SNAPSHOT_PERSISTED){
                receipt=armMountedTransfer(data,receipt,physical.now());
                if(receipt.getMountTransferPhase()!=MountTransferPhase.DESTINATION_PUBLICATION_PENDING)
                    return pending(selection.current,KOMEJoinBattleService.Reason.ENTRY_ALREADY_IN_PROGRESS,
                        receipt.getReceiptId(),receipt);
            }
            PhysicalResult moved=physical.deploy(player,receipt);
            if(moved==null)return pending(selection.current,
                KOMEJoinBattleService.Reason.MOUNT_TRANSFER_FAILED,receipt.getReceiptId(),receipt,true);
            if(!moved.deployed){
                if(moved.rollbackVerified){
                    if(receipt.isEnteredMounted())receipt=disarmRestoredSource(data,receipt,physical.now());
                    // A verified rollback is a stable externally visible outcome. Never consume
                    // the just-restored source mount again in the same reconciliation call.
                    return pending(selection.current,moved.reason,
                        receipt.getReceiptId(),receipt,true);
                }
                if(!moved.deployed)return pending(selection.current,moved.reason,
                    receipt.getReceiptId(),receipt,true);
            }
            if(!physical.isAtDestination(player,receipt)){
                PhysicalResult rollback=receipt.isEnteredMounted()
                    ?physical.rollback(player,receipt):null;
                if(rollback!=null&&rollback.rollbackVerified){
                    receipt=disarmRestoredSource(data,receipt,physical.now());
                    return pending(selection.current,rollback.reason,receipt.getReceiptId(),receipt,true);
                }
                return pending(selection.current,KOMEJoinBattleService.Reason.MOUNT_TRANSFER_FAILED,
                    receipt.getReceiptId(),receipt,true);
            }
        }

        receipt=publishParticipation(data,player,receipt,physical.now());
        if(receipt.getParticipationRecovery()==ParticipationRecovery.REGISTRATION_REQUIRED)
            return pending(current(data,player,receipt.getTileId()),
                KOMEJoinBattleService.Reason.ENTRY_ALREADY_IN_PROGRESS,receipt.getReceiptId(),receipt);
        long now=Math.max(physical.now(),receipt.getUpdatedAtMillis());
        try{
            KOMEJoinBattleDeploymentReceipt.Builder update=KOMEJoinBattleDeploymentReceipt.copyOf(receipt)
                .state(State.DEPLOYED).updatedAtMillis(now).deployedAtMillis(Long.valueOf(now));
            if(receipt.isEnteredMounted())update.mountTransferPhase(MountTransferPhase.DEPLOYMENT_COMPLETE)
                .temporaryMountNbt(null);
            KOMEJoinBattleDeploymentReceipt deployed=update.build();
            data.ensureWritable();data.getJoinBattleDeploymentReceipts().replace(deployed);data.markDirty();
            return deployed(data,player,deployed,"Joined battle beside "+deployed.getSelectedCompanyId()+".");
        }catch(RuntimeException failure){
            return pending(current(data,player,receipt.getTileId()),
                KOMEJoinBattleService.Reason.ENTRY_ALREADY_IN_PROGRESS,receipt.getReceiptId(),receipt);
        }
    }

    private Result cancelInvalidAccepted(KOMEWorldData data,EntityPlayerMP player,
            KOMEJoinBattleDeploymentReceipt receipt,PhysicalAccess physical,
            KOMEJoinBattleService.Reason reason,String detail){
        PhysicalResult rollback=physical.rollback(player,receipt);
        if(rollback==null||!rollback.rollbackVerified)return pending(current(data,player,
            receipt.getTileId()),rollback==null?reason:rollback.reason,receipt.getReceiptId(),receipt,true);
        rollback=PhysicalResult.rolledBack(reason,rollback.mountDisposition);
        return closeCancelled(data,player,receipt,physical.now(),rollback,detail);
    }

    private KOMEJoinBattleDeploymentReceipt armMountedTransfer(KOMEWorldData data,
            KOMEJoinBattleDeploymentReceipt receipt,long requestedNow){
        long now=Math.max(requestedNow,receipt.getUpdatedAtMillis());
        try{
            KOMEJoinBattleDeploymentReceipt armed=KOMEJoinBattleDeploymentReceipt.copyOf(receipt)
                .updatedAtMillis(now)
                .mountTransferPhase(MountTransferPhase.DESTINATION_PUBLICATION_PENDING).build();
            data.ensureWritable();data.getJoinBattleDeploymentReceipts().replace(armed);data.markDirty();
            return armed;
        }catch(RuntimeException failure){return receipt;}
    }

    private KOMEJoinBattleDeploymentReceipt disarmRestoredSource(KOMEWorldData data,
            KOMEJoinBattleDeploymentReceipt receipt,long now){
        receipt=currentRecoveryReceipt(data,receipt);
        KOMEJoinBattleDeploymentReceipt stable=KOMEJoinBattleDeploymentReceipt.copyOf(receipt)
            .mountTransferPhase(MountTransferPhase.NOT_STARTED).temporaryMountNbt(null)
            .updatedAtMillis(Math.max(now,receipt.getUpdatedAtMillis())).build();
        data.ensureWritable();data.getJoinBattleDeploymentReceipts().restoreEntrySource(stable);data.markDirty();
        return stable;
    }

    private KOMEJoinBattleDeploymentReceipt currentRecoveryReceipt(KOMEWorldData data,
            KOMEJoinBattleDeploymentReceipt receipt){
        KOMEJoinBattleDeploymentReceipt current=data.getJoinBattleDeploymentReceipts().get(receipt.getReceiptId());
        if(current==null||!receipt.sameImmutableIdentity(current)||current.getState()!=receipt.getState())
            throw new IllegalArgumentException("Entry recovery authority changed");
        return current;
    }

    private Result closeRolledBack(KOMEWorldData data,EntityPlayerMP player,
            KOMEJoinBattleDeploymentReceipt receipt,long requestedNow,PhysicalResult physical){
        return closeCancelled(data,player,receipt,requestedNow,physical,"");
    }

    private Result closeCancelled(KOMEWorldData data,EntityPlayerMP player,
            KOMEJoinBattleDeploymentReceipt receipt,long requestedNow,PhysicalResult physical,
            String detail){
        receipt=currentRecoveryReceipt(data,receipt);
        long now=Math.max(requestedNow,receipt.getUpdatedAtMillis());
        try{
            KOMEJoinBattleDeploymentReceipt.Builder builder=KOMEJoinBattleDeploymentReceipt.copyOf(receipt)
                .state(State.CLOSED).updatedAtMillis(now).closedAtMillis(Long.valueOf(now))
                .closureOutcome(KOMEJoinBattleDeploymentReceipt.ClosureOutcome.ENTRY_CANCELLED);
            if(receipt.isEnteredMounted())builder.mountTransferPhase(MountTransferPhase.TERMINAL)
                .temporaryMountNbt(null).mountDisposition(physical.mountDisposition==null
                    ?KOMEJoinBattleDeploymentReceipt.MountDisposition.RETURNED_WITH_PLAYER:
                        physical.mountDisposition);
            KOMEJoinBattleDeploymentReceipt closed=builder.build();
            data.ensureWritable();data.getJoinBattleDeploymentReceipts().replace(closed);data.markDirty();
            KOMEJoinBattleEntryRecoveryService.INSTANCE.forgetClosedAttempt(data,closed.getReceiptId());
            KOMEJoinBattleService.Reason reason=physical.reason==null
                ?KOMEJoinBattleService.Reason.MOUNT_TRANSFER_ROLLED_BACK:physical.reason;
            String message=KOMEJoinBattleText.forReason(reason);
            if(receipt.isEnteredMounted()&&reason!=KOMEJoinBattleService.Reason.MOUNT_TRANSFER_ROLLED_BACK
                    &&reason!=KOMEJoinBattleService.Reason.ENTRY_CANCELLED)
                message=message+" You and your mount were restored safely.";
            if(detail!=null&&!detail.trim().isEmpty())message=detail.trim()+" "+message;
            return new Result(Status.REJECTED,reason,message,
                closed.getReceiptId(),"",current(data,player,closed.getTileId()),true);
        }catch(RuntimeException unresolved){
            return pending(current(data,player,receipt.getTileId()),
                KOMEJoinBattleService.Reason.MOUNT_TRANSFER_FAILED,receipt.getReceiptId(),receipt);
        }
    }

    private KOMEJoinBattleDeploymentReceipt publishParticipation(KOMEWorldData data,
            EntityPlayerMP player,KOMEJoinBattleDeploymentReceipt receipt,long requestedNow){
        KOMEConflictRecord record=data.getConflictService().get(receipt.getTileId());
        if(record==null||!record.getConflictId().equals(receipt.getConflictId()))return receipt;
        KOMEConflictRecord.PlayerParticipation existing=record.getPlayers().get(receipt.getPlayerId());
        if(existing!=null){
            if(existing.status!=KOMEConflictRecord.PlayerStatus.ACTIVE)return receipt;
            if(receipt.getParticipationRecovery()==ParticipationRecovery.REGISTRATION_REQUIRED)
                return advanceParticipation(data,receipt,requestedNow);
            return receipt;
        }
        if(receipt.getParticipationRecovery()!=ParticipationRecovery.REGISTRATION_REQUIRED)return receipt;
        long now=Math.max(requestedNow,record.getLastTransition().timestampMillis);
        KOMEConflictService.Result registered=data.getConflictService().registerPlayer(
            receipt.getTileId(),ExpectedConflict.at(record.getConflictId(),record.getRevision()),
            receipt.getPlayerId(),receipt.getFactionId(),new Context(now,
                player.getCommandSenderName(),"Join Battle physical entry"));
        if(registered.code!=Code.SUCCESS)return receipt;
        data.markDirty();
        return advanceParticipation(data,receipt,now);
    }

    private KOMEJoinBattleDeploymentReceipt advanceParticipation(KOMEWorldData data,
            KOMEJoinBattleDeploymentReceipt receipt,long requestedNow){
        long now=Math.max(requestedNow,receipt.getUpdatedAtMillis());
        try{
            KOMEJoinBattleDeploymentReceipt advanced=KOMEJoinBattleDeploymentReceipt.copyOf(receipt)
                .updatedAtMillis(now).participationRecovery(
                    ParticipationRecovery.REGISTERED_BY_RECEIPT).build();
            data.ensureWritable();data.getJoinBattleDeploymentReceipts().replace(advanced);data.markDirty();
            return advanced;
        }catch(RuntimeException failure){return receipt;}
    }

    private static KOMEJoinBattleDeploymentReceipt findOpen(KOMEWorldData data,UUID player){
        for(Map.Entry<String,KOMEJoinBattleDeploymentReceipt> entry:
                data.getJoinBattleDeploymentReceipts().records().entrySet())
            if(entry.getValue().isOpen()&&entry.getValue().getPlayerId().equals(player))
                return entry.getValue();
        return null;
    }

    private static boolean matches(KOMEJoinBattleDeploymentReceipt receipt,UUID player,Request request){
        return receipt.getPlayerId().equals(player)
            &&receipt.getTileId().equals(KOMEConquestTile.normalizeId(request.tileId))
            &&receipt.getConflictId().equals(request.conflictId==null?"":request.conflictId.trim())
            &&receipt.getAcceptedConflictRevision()==request.conflictRevision
            &&receipt.getSelectedCompanyId().equals(request.companyId==null?"":request.companyId.trim());
    }

    private static boolean sameEntryIdentity(KOMEJoinBattleDeploymentReceipt receipt,UUID player,
            Request request){
        return receipt.getPlayerId().equals(player)
            &&receipt.getTileId().equals(KOMEConquestTile.normalizeId(request.tileId))
            &&receipt.getConflictId().equals(request.conflictId==null?"":request.conflictId.trim())
            &&receipt.getSelectedCompanyId().equals(request.companyId==null?"":request.companyId.trim());
    }

    private Result rejected(KOMEWorldData data,EntityPlayerMP player,Request request,
            KOMEJoinBattleService.Reason reason){
        return rejected(current(data,player,request==null?"":request.tileId),reason);
    }
    private Result rejected(KOMEJoinBattleService.Projection current,
            KOMEJoinBattleService.Reason reason){
        return new Result(Status.REJECTED,reason,KOMEJoinBattleText.forReason(reason),"","",current,false);
    }
    private Result rejectedOpen(KOMEJoinBattleService.Projection current,
            KOMEJoinBattleDeploymentReceipt open){
        KOMEJoinBattleService.Reason reason=open.getState()==State.DEPLOYED
            ?KOMEJoinBattleService.Reason.ALREADY_DEPLOYED
            :KOMEJoinBattleService.Reason.ENTRY_ALREADY_IN_PROGRESS;
        String message;
        if(open.getState()==State.PENDING_ENTRY)message="Joining "+open.getTileId()+" ("
            +open.getConflictId()+"). The server is safely completing your deployment.";
        else if(open.getState()==State.PENDING_EGRESS)message="Returning from "+open.getTileId()
            +" ("+open.getConflictId()+"). The server is safely completing your return.";
        else message="You are in battle at "+open.getTileId()+" ("+open.getConflictId()
            +"). Formally retreat before joining another battle.";
        return new Result(Status.REJECTED,reason,message,"","",current,false);
    }
    private Result pending(KOMEJoinBattleService.Projection current,
            KOMEJoinBattleService.Reason reason,String receiptId,KOMEJoinBattleDeploymentReceipt ignored){
        return pending(current,reason,receiptId,ignored,false);
    }
    private Result pending(KOMEJoinBattleService.Projection current,
            KOMEJoinBattleService.Reason reason,String receiptId,
            KOMEJoinBattleDeploymentReceipt ignored,boolean physicalAttempted){
        return new Result(Status.ENTRY_PENDING,reason,KOMEJoinBattleText.forReason(reason),receiptId,
            ignored==null?"":ignored.getActionToken(),current,physicalAttempted);
    }
    private Result pendingUnpublished(KOMEJoinBattleService.Projection current,
            KOMEJoinBattleService.Reason reason,String actionToken){
        return new Result(Status.ENTRY_PENDING,reason,KOMEJoinBattleText.forReason(reason),"",
            actionToken,current,false);
    }
    private Result deployed(KOMEWorldData data,EntityPlayerMP player,
            KOMEJoinBattleDeploymentReceipt receipt,String message){
        return new Result(Status.DEPLOYED,KOMEJoinBattleService.Reason.ALLOWED,message,
            receipt.getReceiptId(),"",current(data,player,receipt.getTileId()),true);
    }
    private static KOMEJoinBattleService.Projection current(KOMEWorldData data,
            EntityPlayerMP player,String tile){
        return KOMEJoinBattleService.INSTANCE.evaluate(data,player,tile);
    }
}
