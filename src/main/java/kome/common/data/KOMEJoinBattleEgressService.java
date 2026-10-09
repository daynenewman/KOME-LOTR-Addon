package kome.common.data;

import kome.common.KOMEReflection;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static kome.common.data.KOMEJoinBattleDeploymentReceipt.ClosureOutcome;
import static kome.common.data.KOMEJoinBattleDeploymentReceipt.MountDisposition;
import static kome.common.data.KOMEJoinBattleDeploymentReceipt.MountTransferPhase;
import static kome.common.data.KOMEJoinBattleDeploymentReceipt.State;

/** Conflict-end and login recovery for physical Join Battle delivery receipts. */
public final class KOMEJoinBattleEgressService {
    public static final KOMEJoinBattleEgressService INSTANCE = new KOMEJoinBattleEgressService();
    private static final String ENDED_REASON = "The associated conflict ended.";
    static final long RETRY_MILLIS = 60000L;
    private final Map<UUID,Long> nextPoll = new java.util.HashMap<UUID,Long>();
    private final Map<UUID,Integer> loginTicks = new java.util.HashMap<UUID,Integer>();
    private final Map<UUID,Retry> retries = new java.util.HashMap<UUID,Retry>();
    private final java.util.Set<UUID> inFlight = new java.util.HashSet<UUID>();

    private static final class Retry {
        final KOMEWorldData data;final String receipt;final long after;final CompletionResult result;
        Retry(KOMEWorldData data,String receipt,long after,CompletionResult result){
            this.data=data;this.receipt=receipt;this.after=after;this.result=result;
        }
    }

    public void resetSession(){nextPoll.clear();loginTicks.clear();retries.clear();inFlight.clear();}

    /** Forge's login callback runs BEFORE vanilla restores nested Riding. Defer physical work. */
    public void onLogin(KOMEWorldData data,EntityPlayerMP player){
        if(data==null||player==null)return;
        UUID id=KOMEReflection.getEntityUUID(player);
        KOMEJoinBattleDeploymentReceipt open=findOpen(data,id);
        if(open!=null&&open.getState()!=State.PENDING_ENTRY){
            loginTicks.put(id,Integer.valueOf(open.isEnteredMounted()?20:1));
            nextPoll.remove(id);
        }
    }

    public boolean tick(KOMEWorldData data,EntityPlayerMP player,long now){
        return tick(data,player,now,LIVE);
    }

    boolean tick(KOMEWorldData data,EntityPlayerMP player,long now,PhysicalAccess physical){
        if(data==null||player==null)return false;
        UUID id=KOMEReflection.getEntityUUID(player);
        KOMEJoinBattleDeploymentReceipt open=findOpen(data,id);
        if(open==null||open.getState()==State.PENDING_ENTRY){
            loginTicks.remove(id);nextPoll.remove(id);retries.remove(id);return false;
        }
        Integer ticks=loginTicks.get(id);
        if(ticks!=null){
            if(ticks.intValue()>1&&!KOMEJoinBattlePhysicalAccess.isRidingRecordedMount(player,open)){
                loginTicks.put(id,Integer.valueOf(ticks.intValue()-1));return false;
            }
            loginTicks.remove(id);
        }
        Long next=nextPoll.get(id);
        if(next!=null&&now<next.longValue())return false;
        nextPoll.put(id,Long.valueOf(now+5000L));
        boolean closed=reconcilePlayer(data,player,now,physical);
        if(closed){
            nextPoll.remove(id);
            if(physical==LIVE)player.addChatMessage(new net.minecraft.util.ChatComponentText(
                "Your return from battle is complete."));
        }
        return closed;
    }

    static final class Preparation {
        boolean sourceRestored;
        final boolean ready;
        final boolean transferRecordedMount;
        final NBTTagCompound mountSnapshot;
        final MountDisposition playerOnlyDisposition;
        final KOMEJoinBattleDeploymentReceipt.Pose playerTarget;
        final KOMEJoinBattleDeploymentReceipt.Pose mountTarget;
        final String failureReason;

        private Preparation(boolean ready, boolean transfer, NBTTagCompound snapshot,
                MountDisposition disposition, KOMEJoinBattleDeploymentReceipt.Pose playerTarget,
                KOMEJoinBattleDeploymentReceipt.Pose mountTarget, String failureReason) {
            this.ready = ready;
            this.transferRecordedMount = transfer;
            this.mountSnapshot = snapshot == null ? null : (NBTTagCompound) snapshot.copy();
            this.playerOnlyDisposition = disposition;
            this.playerTarget = playerTarget;
            this.mountTarget = mountTarget;
            this.failureReason = failureReason == null ? "" : failureReason;
        }

        static Preparation unavailable() {
            return unavailable("Saved return position is not currently safe.");
        }

        static Preparation restoredSource() {
            Preparation result=unavailable("Your exact mount and rider were recovered safely; the return will continue automatically.");
            result.sourceRestored=true;return result;
        }

        static Preparation unavailable(String reason) {
            return new Preparation(false, false, null, null, null, null, reason);
        }

        static Preparation playerOnly(MountDisposition disposition) {
            return new Preparation(true, false, null, disposition, null, null, "");
        }

        static Preparation playerOnly(MountDisposition disposition,
                KOMEJoinBattleDeploymentReceipt.Pose playerTarget) {
            return new Preparation(true, false, null, disposition, playerTarget, null, "");
        }

        static Preparation mounted(NBTTagCompound snapshot) {
            return new Preparation(true, true, snapshot, null, null, null, "");
        }

        static Preparation mounted(NBTTagCompound snapshot,
                KOMEJoinBattleDeploymentReceipt.Pose playerTarget,
                KOMEJoinBattleDeploymentReceipt.Pose mountTarget) {
            return new Preparation(true, true, snapshot, null, playerTarget, mountTarget, "");
        }
    }

    static final class PhysicalResult {
        final boolean completed;
        boolean sourceRestored;
        final MountDisposition mountDisposition;
        final String failureReason;
        private PhysicalResult(boolean completed, MountDisposition disposition, String reason) {
            this.completed = completed;
            this.mountDisposition = disposition;
            this.failureReason = reason == null ? "" : reason;
        }
        static PhysicalResult failed() { return failed("Return teleport could not be verified."); }
        static PhysicalResult failed(String reason) { return new PhysicalResult(false, null, reason); }
        static PhysicalResult failedAfterRollback(String reason, boolean verified) {
            PhysicalResult result = failed(reason); result.sourceRestored = verified; return result;
        }
        static PhysicalResult completed(MountDisposition disposition) {
            return new PhysicalResult(true, disposition, "");
        }
    }

    interface PhysicalAccess {
        EntityPlayerMP findOnline(UUID playerId);
        Preparation prepare(EntityPlayerMP player, KOMEJoinBattleDeploymentReceipt receipt);
        PhysicalResult egress(EntityPlayerMP player, KOMEJoinBattleDeploymentReceipt receipt,
            Preparation preparation);
    }

    private static final PhysicalAccess LIVE = new PhysicalAccess() {
        @Override public EntityPlayerMP findOnline(UUID playerId) {
            MinecraftServer server = MinecraftServer.getServer();
            if (playerId == null || server == null || server.getConfigurationManager() == null)
                return null;
            for (Object value : server.getConfigurationManager().playerEntityList)
                if (value instanceof EntityPlayerMP
                        && playerId.equals(KOMEReflection.getEntityUUID((EntityPlayerMP) value)))
                    return (EntityPlayerMP) value;
            return null;
        }
        @Override public Preparation prepare(EntityPlayerMP player,
                KOMEJoinBattleDeploymentReceipt receipt) {
            return KOMEJoinBattlePhysicalAccess.prepareEgress(player, receipt);
        }
        @Override public PhysicalResult egress(EntityPlayerMP player,
                KOMEJoinBattleDeploymentReceipt receipt, Preparation preparation) {
            return KOMEJoinBattlePhysicalAccess.egress(player, receipt, preparation);
        }
    };

    private KOMEJoinBattleEgressService() { }

    public enum Completion { CLOSED, PENDING, INVALID }

    public static final class CompletionResult {
        public final Completion completion;
        public final String reason;
        private CompletionResult(Completion completion, String reason) {
            this.completion = completion;
            this.reason = reason == null ? "" : reason;
        }
    }

    /** Exact current physical-deployment authority; CLOSED history never qualifies. */
    public KOMEJoinBattleDeploymentReceipt findOpen(KOMEWorldData data, UUID playerId) {
        if (data == null || playerId == null) return null;
        for (KOMEJoinBattleDeploymentReceipt receipt :
                data.getJoinBattleDeploymentReceipts().records().values())
            if (receipt.isOpen() && playerId.equals(receipt.getPlayerId())) return receipt;
        return null;
    }

    /** Voluntary formal-retreat egress, keyed to an already-authorized exact receipt. */
    public Completion requestAndComplete(KOMEWorldData data, EntityPlayerMP player,
            String receiptId, long timestampMillis, String reason) {
        return requestAndCompleteDetailed(data, player, receiptId, timestampMillis, reason).completion;
    }

    public CompletionResult requestAndCompleteDetailed(KOMEWorldData data, EntityPlayerMP player,
            String receiptId, long timestampMillis, String reason) {
        return requestAndCompleteDetailed(data, player, receiptId, timestampMillis, reason, LIVE);
    }

    Completion requestAndComplete(KOMEWorldData data, EntityPlayerMP player,
            String receiptId, long timestampMillis, String reason, PhysicalAccess physical) {
        return requestAndCompleteDetailed(data, player, receiptId, timestampMillis,
            reason, physical).completion;
    }

    CompletionResult requestAndCompleteDetailed(KOMEWorldData data, EntityPlayerMP player,
            String receiptId, long timestampMillis, String reason, PhysicalAccess physical) {
        if (data == null || player == null || receiptId == null)
            return attempt(Completion.INVALID, "No exact deployment receipt was available.");
        KOMEJoinBattleDeploymentReceipt receipt =
            data.getJoinBattleDeploymentReceipts().get(receiptId);
        UUID playerId = KOMEReflection.getEntityUUID(player);
        if (receipt == null || !receipt.isOpen() || !playerId.equals(receipt.getPlayerId())
                || receipt.getState() == State.PENDING_ENTRY)
            return attempt(Completion.INVALID,
                "The deployment receipt is not eligible for physical egress.");
        receipt = requestEgress(data, receipt, timestampMillis, reason);
        return complete(data, player, receipt, timestampMillis, physical);
    }

    /** Called directly by the successful conflict-end transaction. */
    public int onConflictEnded(KOMEWorldData data, String conflictId, long timestampMillis) {
        return onConflictEnded(data, conflictId, timestampMillis, LIVE);
    }

    int onConflictEnded(KOMEWorldData data, String conflictId, long timestampMillis,
            PhysicalAccess physical) {
        if (data == null || physical == null || conflictId == null) return 0;
        String exact;
        try { exact = KOMEConflictIdAllocator.requireIdentity(conflictId); }
        catch (IllegalArgumentException invalid) { return 0; }
        List<KOMEJoinBattleDeploymentReceipt> affected = new ArrayList<KOMEJoinBattleDeploymentReceipt>();
        for (KOMEJoinBattleDeploymentReceipt receipt :
                data.getJoinBattleDeploymentReceipts().records().values())
            if (receipt.isOpen() && exact.equals(receipt.getConflictId())) affected.add(receipt);
        Collections.sort(affected, new Comparator<KOMEJoinBattleDeploymentReceipt>() {
            @Override public int compare(KOMEJoinBattleDeploymentReceipt first,
                    KOMEJoinBattleDeploymentReceipt second) {
                return first.getReceiptId().compareTo(second.getReceiptId());
            }
        });
        int changed = 0;
        for (KOMEJoinBattleDeploymentReceipt receipt : affected) {
            if(receipt.getState()==State.PENDING_ENTRY){
                EntityPlayerMP player=physical.findOnline(receipt.getPlayerId());
                if(player!=null&&physical==LIVE){
                    KOMEJoinBattleEntryService.INSTANCE.cancelAccepted(data,player,
                        receipt.getReceiptId(),timestampMillis,ENDED_REASON);
                    KOMEJoinBattleDeploymentReceipt terminal=
                        data.getJoinBattleDeploymentReceipts().get(receipt.getReceiptId());
                    if(terminal!=null&&terminal.getState()==State.CLOSED)changed++;
                }
                // Accepted entry recovery owns its mount snapshot.  Offline cancellation waits
                // for login rather than being misclassified as ordinary post-deployment egress.
                continue;
            }
            KOMEJoinBattleDeploymentReceipt pending = requestEgress(data, receipt,
                timestampMillis, ENDED_REASON);
            if (pending != receipt) changed++;
            EntityPlayerMP player = physical.findOnline(pending.getPlayerId());
            if (player != null && complete(data, player, pending, timestampMillis,
                    physical).completion == Completion.CLOSED) changed++;
        }
        return changed;
    }

    /** Login recovery remains keyed to the durable receipt, not the latest tile record. */
    public boolean reconcilePlayer(KOMEWorldData data, EntityPlayerMP player,
            long timestampMillis) {
        return reconcilePlayer(data, player, timestampMillis, LIVE);
    }

    boolean reconcilePlayer(KOMEWorldData data, EntityPlayerMP player,
            long timestampMillis, PhysicalAccess physical) {
        if (data == null || player == null || physical == null) return false;
        UUID playerId = KOMEReflection.getEntityUUID(player);
        KOMEJoinBattleDeploymentReceipt open = null;
        for (KOMEJoinBattleDeploymentReceipt receipt :
                data.getJoinBattleDeploymentReceipts().records().values()) {
            if (receipt.isOpen() && playerId.equals(receipt.getPlayerId())) {
                open = receipt;
                break;
            }
        }
        if (open == null) return false;
        if(open.getState()==State.PENDING_ENTRY)return false;
        if (open.getState() != State.PENDING_EGRESS) {
            KOMEConflictRecord current = data.getConflictService().get(open.getTileId());
            boolean exactActive = current != null && current.isActive()
                && open.getConflictId().equals(current.getConflictId());
            if (exactActive) return false;
            open = requestEgress(data, open, timestampMillis, ENDED_REASON);
        }
        return complete(data, player, open, timestampMillis, physical).completion
            == Completion.CLOSED;
    }

    KOMEJoinBattleDeploymentReceipt requestEgress(KOMEWorldData data,
            KOMEJoinBattleDeploymentReceipt receipt, long requestedNow, String reason) {
        if (receipt.getState() == State.CLOSED || receipt.getState() == State.PENDING_EGRESS)
            return receipt;
        long now = Math.max(requestedNow, receipt.getUpdatedAtMillis());
        KOMEJoinBattleDeploymentReceipt pending = KOMEJoinBattleDeploymentReceipt.copyOf(receipt)
            .state(State.PENDING_EGRESS).updatedAtMillis(now)
            .egressRequestedAtMillis(Long.valueOf(now)).egressReason(reason).build();
        data.ensureWritable();
        data.getJoinBattleDeploymentReceipts().replace(pending);
        data.markDirty();
        return pending;
    }

    private CompletionResult complete(KOMEWorldData data, EntityPlayerMP player,
            KOMEJoinBattleDeploymentReceipt receipt, long requestedNow,
            PhysicalAccess physical) {
        UUID id=KOMEReflection.getEntityUUID(player);
        if(loginTicks.containsKey(id))return attempt(Completion.PENDING,
            "Waiting for login mount restoration to finish.");
        Retry retry=retries.get(id);
        if(retry!=null&&retry.data==data&&retry.receipt.equals(receipt.getReceiptId())
                &&requestedNow<retry.after)return retry.result;
        if(!inFlight.add(id))return attempt(Completion.PENDING,"Safe return is already being processed.");
        try{
            CompletionResult result=performCompletion(data,player,receipt,requestedNow,physical);
            if(result.completion==Completion.PENDING){
                retries.put(id,new Retry(data,receipt.getReceiptId(),requestedNow+RETRY_MILLIS,result));
                KOMEJoinBattleEntryRecoveryService.LOGGER.warn("Egress {} pending: {}",
                    receipt.getReceiptId(),result.reason);
            }else retries.remove(id);
            return result;
        }finally{inFlight.remove(id);}
    }

    private CompletionResult performCompletion(KOMEWorldData data, EntityPlayerMP player,
            KOMEJoinBattleDeploymentReceipt receipt, long requestedNow,
            PhysicalAccess physical) {
        KOMEJoinBattleDeploymentReceipt current =
            data.getJoinBattleDeploymentReceipts().get(receipt.getReceiptId());
        if (current == null || current.getState() == State.CLOSED)
            return attempt(Completion.INVALID, "The deployment receipt is no longer open.");
        if (current.getState() != State.PENDING_EGRESS)
            return attempt(Completion.INVALID, "The deployment receipt is not pending egress.");
        String strategicBlock=KOMEFormalRetreatService.egressBlockedReason(data,current);
        if(strategicBlock!=null)return attempt(Completion.PENDING,strategicBlock);
        Preparation prepared;
        try { prepared = physical.prepare(player, current); }
        catch (Throwable physicalFailure) {
            return attempt(Completion.PENDING, "Physical egress preparation failed: "
                + physicalFailure.getClass().getSimpleName() + ".");
        }
        if (prepared == null) return attempt(Completion.PENDING,
            "Physical egress preparation returned no result.");
        if(prepared.sourceRestored && current.isEnteredMounted()
                && current.getMountTransferPhase()==MountTransferPhase.EGRESS_TRANSFER_PENDING){
            KOMEJoinBattleDeploymentReceipt restored=KOMEJoinBattleDeploymentReceipt.copyOf(current)
                .updatedAtMillis(Math.max(requestedNow,current.getUpdatedAtMillis()))
                .mountTransferPhase(MountTransferPhase.DEPLOYMENT_COMPLETE).temporaryMountNbt(null).build();
            data.ensureWritable();data.getJoinBattleDeploymentReceipts().restoreEgressSource(restored);data.markDirty();
        }
        if (!prepared.ready) return attempt(Completion.PENDING, prepared.failureReason);
        if(current.isEnteredMounted() && current.getMountTransferPhase()==MountTransferPhase.EGRESS_TRANSFER_PENDING
                && !prepared.transferRecordedMount)
            return attempt(Completion.PENDING,"The interrupted mounted return must be recovered before closure.");

        if (current.isEnteredMounted() && prepared.transferRecordedMount) {
            if (prepared.mountSnapshot == null || prepared.mountSnapshot.hasNoTags())
                return attempt(Completion.PENDING,
                    "Mounted egress snapshot could not be captured safely.");
            long snapshotAt = Math.max(requestedNow, current.getUpdatedAtMillis());
            try {
                KOMEJoinBattleDeploymentReceipt snapshotted =
                    KOMEJoinBattleDeploymentReceipt.copyOf(current)
                        .updatedAtMillis(snapshotAt)
                        .mountTransferPhase(MountTransferPhase.EGRESS_TRANSFER_PENDING)
                        .temporaryMountNbt(prepared.mountSnapshot).build();
                data.ensureWritable();
                data.getJoinBattleDeploymentReceipts().replace(snapshotted);
                data.markDirty();
                current = snapshotted;
            } catch (RuntimeException invalidSnapshot) {
                return attempt(Completion.PENDING,
                    "Mounted egress recovery state could not be persisted.");
            }
        }

        PhysicalResult result;
        try { result = physical.egress(player, current, prepared); }
        catch (Throwable physicalFailure) {
            return attempt(Completion.PENDING, "Physical egress failed: "
                + physicalFailure.getClass().getSimpleName() + ".");
        }
        if (result == null) return attempt(Completion.PENDING,
            "Physical egress returned no verification result.");
        if (!result.completed) {
            if(result.sourceRestored && current.isEnteredMounted()
                    && current.getMountTransferPhase()==MountTransferPhase.EGRESS_TRANSFER_PENDING){
                KOMEJoinBattleDeploymentReceipt restored=KOMEJoinBattleDeploymentReceipt.copyOf(current)
                    .updatedAtMillis(Math.max(requestedNow,current.getUpdatedAtMillis()))
                    .mountTransferPhase(MountTransferPhase.DEPLOYMENT_COMPLETE)
                    .temporaryMountNbt(null).build();
                data.ensureWritable();
                data.getJoinBattleDeploymentReceipts().restoreEgressSource(restored);data.markDirty();
            }
            return attempt(Completion.PENDING, result.failureReason);
        }
        long closedAt = Math.max(requestedNow, current.getUpdatedAtMillis());
        ClosureOutcome outcome = current.getDeployedAtMillis() == null
            ? ClosureOutcome.ENTRY_CANCELLED : ClosureOutcome.EGRESS_COMPLETED;
        try {
            KOMEJoinBattleDeploymentReceipt.Builder closed =
                KOMEJoinBattleDeploymentReceipt.copyOf(current)
                    .state(State.CLOSED).updatedAtMillis(closedAt)
                    .closedAtMillis(Long.valueOf(closedAt)).closureOutcome(outcome);
            if (current.isEnteredMounted()) closed
                .mountTransferPhase(MountTransferPhase.TERMINAL)
                .temporaryMountNbt(null)
                .mountDisposition(result.mountDisposition == null
                    ? MountDisposition.UNAVAILABLE : result.mountDisposition);
            KOMEJoinBattleDeploymentReceipt terminal = closed.build();
            data.ensureWritable();
            data.getJoinBattleDeploymentReceipts().replace(terminal);
            data.markDirty();
            return attempt(Completion.CLOSED, "Physical egress completed.");
        } catch (RuntimeException finalizationFailure) {
            return attempt(Completion.PENDING,
                "Physical egress succeeded, but durable closure remains pending.");
        }
    }

    private static CompletionResult attempt(Completion completion, String reason) {
        return new CompletionResult(completion, reason);
    }
}
