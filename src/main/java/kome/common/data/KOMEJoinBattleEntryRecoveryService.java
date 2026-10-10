package kome.common.data;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import kome.common.KOMEReflection;
import net.minecraft.entity.player.EntityPlayerMP;

/** Transient, bounded retry scheduler for durable accepted Join Battle entries. */
public final class KOMEJoinBattleEntryRecoveryService {
    static final org.apache.logging.log4j.Logger LOGGER=
        org.apache.logging.log4j.LogManager.getLogger("KOMEJoinBattleRecovery");
    public static final KOMEJoinBattleEntryRecoveryService INSTANCE =
        new KOMEJoinBattleEntryRecoveryService();
    static final long RETRY_INTERVAL_MILLIS = 5000L;
    static final long DETERMINISTIC_RETRY_INTERVAL_MILLIS = 60000L;
    static final int LOGIN_STABILIZATION_TICKS = 20;
    private final Map<UUID,Integer> loginPublication = new HashMap<UUID,Integer>();

    private final Map<UUID,Long> nextAttempt = new HashMap<UUID,Long>();
    private final Map<KOMEWorldData,Map<String,Attempt>> attempts =
        new java.util.WeakHashMap<KOMEWorldData,Map<String,Attempt>>();
    private final Set<UUID> inFlight = new HashSet<UUID>();

    private KOMEJoinBattleEntryRecoveryService() { }

    public synchronized void resetSession() {
        nextAttempt.clear();
        attempts.clear();
        inFlight.clear();
        loginPublication.clear();
    }

    synchronized boolean awaitingLoginPublication(EntityPlayerMP player) {
        return player!=null&&loginPublication.containsKey(KOMEReflection.getEntityUUID(player));
    }

    static final class Attempt {
        boolean active;
        long after;
    }

    /** The single destructive admission gate, also used by accepted-token/client replays. */
    synchronized Attempt beginAttempt(KOMEWorldData data,KOMEJoinBattleDeploymentReceipt receipt,long now) {
        if(loginPublication.containsKey(receipt.getPlayerId()))return null;
        Map<String,Attempt> world=attempts.get(data);
        if(world==null){world=new HashMap<String,Attempt>();attempts.put(data,world);}
        Attempt attempt=world.get(receipt.getReceiptId());
        if(attempt==null){attempt=new Attempt();world.put(receipt.getReceiptId(),attempt);}
        if(attempt.active||now<attempt.after)return null;
        attempt.active=true;
        return attempt;
    }

    synchronized void finishAttempt(KOMEWorldData data,KOMEJoinBattleDeploymentReceipt receipt,
            Attempt attempt,long now,KOMEJoinBattleEntryService.Result result) {
        attempt.active=false;
        KOMEJoinBattleDeploymentReceipt current=data.getJoinBattleDeploymentReceipts().get(receipt.getReceiptId());
        if(current==null||current.getState()!=KOMEJoinBattleDeploymentReceipt.State.PENDING_ENTRY){
            Map<String,Attempt> world=attempts.get(data);
            if(world!=null)world.remove(receipt.getReceiptId());
        }else if(result==null||result.physicalAttempted){
            attempt.after=now+(result==null||isDeterministic(result.reason)
                ?DETERMINISTIC_RETRY_INTERVAL_MILLIS:RETRY_INTERVAL_MILLIS);
        }
    }

    synchronized void forgetClosedAttempt(KOMEWorldData data,String receiptId){
        KOMEJoinBattleDeploymentReceipt receipt=data.getJoinBattleDeploymentReceipts().get(receiptId);
        if(receipt!=null&&receipt.getState()!=KOMEJoinBattleDeploymentReceipt.State.CLOSED)return;
        Map<String,Attempt> world=attempts.get(data);
        if(world!=null)world.remove(receiptId);
    }

    public KOMEJoinBattleEntryService.Result onLogin(KOMEWorldData data,
            EntityPlayerMP player,long nowMillis) {
        return reconcile(data,player,nowMillis,true,null);
    }

    public KOMEJoinBattleEntryService.Result tick(KOMEWorldData data,
            EntityPlayerMP player,long nowMillis) {
        return reconcile(data,player,nowMillis,false,null);
    }

    KOMEJoinBattleEntryService.Result tick(KOMEWorldData data,EntityPlayerMP player,
            long nowMillis,KOMEJoinBattleEntryService.PhysicalAccess physical){
        return reconcile(data,player,nowMillis,false,physical);
    }

    KOMEJoinBattleEntryService.Result onLogin(KOMEWorldData data,EntityPlayerMP player,
            long nowMillis,KOMEJoinBattleEntryService.PhysicalAccess physical){
        return reconcile(data,player,nowMillis,true,physical);
    }

    private KOMEJoinBattleEntryService.Result reconcile(KOMEWorldData data,
            EntityPlayerMP player,long nowMillis,boolean force,
            KOMEJoinBattleEntryService.PhysicalAccess physical) {
        if(data==null||player==null)return null;
        UUID playerId=KOMEReflection.getEntityUUID(player);
        synchronized(this){
            KOMEJoinBattleDeploymentReceipt open=
                KOMEJoinBattleEgressService.INSTANCE.findOpen(data,playerId);
            if(open==null||open.getState()!=KOMEJoinBattleDeploymentReceipt.State.PENDING_ENTRY){
                nextAttempt.remove(playerId);
                loginPublication.remove(playerId);return null;
            }
            // Forge 1.7.10 fires login BEFORE spawning the saved nested Riding entity.
            // Never move/reconstruct here, even if another hook already attached a candidate:
            // vanilla still owns the remainder of this call stack.
            if(force&&open.isEnteredMounted()){
                loginPublication.put(playerId,Integer.valueOf(LOGIN_STABILIZATION_TICKS));
                nextAttempt.remove(playerId);
                LOGGER.info("LOGIN_MOUNT_PUBLICATION_PENDING {}; exactNestedCandidate={}",
                    open.getReceiptId(),KOMEJoinBattlePhysicalAccess.hasExpectedRidingMount(player,open));
                return KOMEJoinBattleEntryService.waitingForLoginMount(open);
            }
            Integer remaining=loginPublication.get(playerId);
            if(remaining!=null){
                if(KOMEJoinBattlePhysicalAccess.hasExpectedRidingMount(player,open)){
                    LOGGER.info("LOGIN_NESTED_MOUNT_ADOPTED {}",open.getReceiptId());
                    loginPublication.remove(playerId);
                }else if(KOMEJoinBattlePhysicalAccess.hasPublishedRecoveryMount(open)){
                    loginPublication.remove(playerId);
                }else if(remaining.intValue()>1){
                    loginPublication.put(playerId,Integer.valueOf(remaining.intValue()-1));
                    return null;
                }else{
                    loginPublication.remove(playerId);
                    LOGGER.info("LOGIN_MOUNT_PUBLICATION_SETTLED {}; checking exact locations before reconstruction",open.getReceiptId());
                }
            }
            Long next=nextAttempt.get(playerId);
            if(!force&&next!=null&&nowMillis<next.longValue())return null;
            if(!inFlight.add(playerId))return null;
            nextAttempt.put(playerId,Long.valueOf(nowMillis+RETRY_INTERVAL_MILLIS));
        }
        try{
            KOMEJoinBattleEntryService.Result result=
                physical==null?KOMEJoinBattleEntryService.INSTANCE.reconcileAccepted(data,player,
                    KOMEJoinBattlePhysicalAccess.INSTANCE,nowMillis):
                    KOMEJoinBattleEntryService.INSTANCE.reconcileAccepted(data,player,physical,
                        nowMillis);
            synchronized(this){
                KOMEJoinBattleDeploymentReceipt remaining=
                    KOMEJoinBattleEgressService.INSTANCE.findOpen(data,playerId);
                if(remaining==null||remaining.getState()!=
                        KOMEJoinBattleDeploymentReceipt.State.PENDING_ENTRY)
                    nextAttempt.remove(playerId);
            }
            return result;
        }finally{
            synchronized(this){inFlight.remove(playerId);}
        }
    }

    private static boolean isDeterministic(KOMEJoinBattleService.Reason reason){
        return reason==KOMEJoinBattleService.Reason.SAFE_POSITION_UNAVAILABLE
            ||reason==KOMEJoinBattleService.Reason.DESTINATION_UUID_MISMATCH
            ||reason==KOMEJoinBattleService.Reason.DESTINATION_PROFILE_MISMATCH
            ||reason==KOMEJoinBattleService.Reason.PLAYER_TELEPORT_FAILED
            ||reason==KOMEJoinBattleService.Reason.REMOUNT_FAILED
            ||reason==KOMEJoinBattleService.Reason.MULTIPLE_MOUNT_COPIES_FOUND
            ||reason==KOMEJoinBattleService.Reason.MOUNT_DUPLICATE_STATE_MISMATCH
            ||reason==KOMEJoinBattleService.Reason.MOUNT_COPY_OUTSIDE_RECOVERY_AREA
            ||reason==KOMEJoinBattleService.Reason.MOUNT_LOCATION_AMBIGUOUS
            ||reason==KOMEJoinBattleService.Reason.INVALID_MOUNT_RELATIONSHIP;
    }
}
