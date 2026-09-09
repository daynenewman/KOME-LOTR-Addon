package com.enovak.lotrmoremobs.util;

import com.enovak.lotrmoremobs.entity.animal.LOTREntityMumakil;
import net.minecraft.entity.EntityLivingBase;

import java.util.HashMap;
import java.util.Map;

public final class MumakilPerformanceTracker {
    public static final boolean DEBUG_MUMAKIL_PERFORMANCE = false;
    public static final boolean DEBUG_DO_NOT_SPAWN_HOWDAH_ARCHERS = false;
    public static final boolean DEBUG_DISABLE_HOWDAH_ARCHER_COMBAT = false;
    public static final boolean DEBUG_DISABLE_MUMAKIL_TREE_CLEARING = false;
    public static final boolean DEBUG_DISABLE_HIRED_WAR_MOUNT_ATTACK_AI = false;
    public static final int DRIVER_PATH_REASON_NEW_TARGET = 1;
    public static final int DRIVER_PATH_REASON_NO_NAVIGATOR_PATH = 2;
    public static final int DRIVER_PATH_REASON_TARGET_MOVED = 3;
    public static final int DRIVER_PATH_REASON_FORCED_STUCK = 4;
    public static final int DRIVER_PATH_REASON_COLLISION_RECOVERY = 5;
    public static final int COMBAT_PATH_REASON_NEW_TARGET = 1;
    public static final int COMBAT_PATH_REASON_NO_PATH = 2;
    public static final int COMBAT_PATH_REASON_TARGET_MOVED = 3;
    public static final int COMBAT_PATH_REASON_NO_PROGRESS = 4;

    private static final int REPORT_INTERVAL_TICKS = 100;
    private static final long COMBAT_PATH_DETAIL_LOG_THRESHOLD_NANOS = 25000000L;
    private static final int MAX_HOWDAH_ARCHER_SLOTS = 17;
    private static final Map<Integer, Metrics> METRICS_BY_MOUNT = new HashMap<Integer, Metrics>();

    private MumakilPerformanceTracker() {
    }

    public static boolean isEnabled() {
        return DEBUG_MUMAKIL_PERFORMANCE;
    }

    public static long startTimer() {
        return DEBUG_MUMAKIL_PERFORMANCE ? System.nanoTime() : 0L;
    }

    public static void reportIfDue(LOTREntityMumakil mumakil) {
        Metrics metrics = metricsFor(mumakil);
        if (metrics == null) {
            return;
        }

        if (!mumakil.isEntityAlive() || mumakil.isDead) {
            METRICS_BY_MOUNT.remove(Integer.valueOf(mumakil.getEntityId()));
            return;
        }

        if (metrics.nextReportTick < 0) {
            metrics.nextReportTick = mumakil.ticksExisted + REPORT_INTERVAL_TICKS;
            return;
        }

        if (mumakil.ticksExisted < metrics.nextReportTick) {
            return;
        }

        metrics.printAndReset(mumakil, mumakil.ticksExisted + REPORT_INTERVAL_TICKS);
    }

    public static void recordMountTargetScan(LOTREntityMumakil mumakil, int candidates, long nanos) {
        Metrics metrics = metricsFor(mumakil);
        if (metrics == null) {
            return;
        }

        ++metrics.mountTargetScans;
        metrics.mountCandidates += candidates;
        metrics.addMeasuredTime(nanos);
    }

    public static void recordMountCandidateCheck(LOTREntityMumakil mumakil) {
        Metrics metrics = metricsFor(mumakil);
        if (metrics != null) {
            ++metrics.mountCandidateChecks;
        }
    }

    public static void recordMountPathRequest(LOTREntityMumakil mumakil, long nanos, boolean accepted, boolean repath) {
        Metrics metrics = metricsFor(mumakil);
        if (metrics == null) {
            return;
        }

        recordPathRequest(metrics, nanos, accepted, repath);
    }

    public static void recordTargetChange(LOTREntityMumakil mumakil) {
        Metrics metrics = metricsFor(mumakil);
        if (metrics != null) {
            ++metrics.targetChanges;
        }
    }

    public static void recordUnreachableCheck(LOTREntityMumakil mumakil) {
        Metrics metrics = metricsFor(mumakil);
        if (metrics != null) {
            ++metrics.unreachableChecks;
        }
    }

    public static void recordTuskCandidateCheck(LOTREntityMumakil mumakil) {
        Metrics metrics = metricsFor(mumakil);
        if (metrics != null) {
            ++metrics.tuskCandidateChecks;
        }
    }

    public static void recordTrampleScan(LOTREntityMumakil mumakil, int candidates, long nanos) {
        Metrics metrics = metricsFor(mumakil);
        if (metrics == null) {
            return;
        }

        ++metrics.trampleScans;
        metrics.trampleCandidates += candidates;
        metrics.addMeasuredTime(nanos);
    }

    public static void recordTrampleCandidateCheck(LOTREntityMumakil mumakil) {
        Metrics metrics = metricsFor(mumakil);
        if (metrics != null) {
            ++metrics.trampleCandidateChecks;
        }
    }

    public static void recordTreePass(LOTREntityMumakil mumakil) {
        Metrics metrics = metricsFor(mumakil);
        if (metrics != null) {
            ++metrics.treePasses;
        }
    }

    public static void recordTreeBlocksChecked(LOTREntityMumakil mumakil, int checked) {
        Metrics metrics = metricsFor(mumakil);
        if (metrics != null) {
            metrics.treeBlocksChecked += checked;
        }
    }

    public static void recordTreeBlocksDestroyed(LOTREntityMumakil mumakil, int destroyed) {
        Metrics metrics = metricsFor(mumakil);
        if (metrics != null) {
            metrics.treeBlocksDestroyed += destroyed;
        }
    }

    public static void recordArcherUpdate(LOTREntityMumakil mumakil, int slot, boolean hasTarget, int workUnits, long nanos) {
        Metrics metrics = metricsFor(mumakil);
        if (metrics == null) {
            return;
        }

        ++metrics.archerUpdatePasses;
        metrics.addArcherSlot(slot, hasTarget);
        metrics.addArcherWork(slot, workUnits);
        metrics.addMeasuredTime(nanos);
    }

    public static void recordArcherTargetScan(LOTREntityMumakil mumakil, int candidates, long nanos) {
        Metrics metrics = metricsFor(mumakil);
        if (metrics == null) {
            return;
        }

        ++metrics.archerTargetScans;
        metrics.archerCandidates += candidates;
        metrics.addMeasuredTime(nanos);
    }

    public static void recordArcherCandidateCheck(LOTREntityMumakil mumakil) {
        Metrics metrics = metricsFor(mumakil);
        if (metrics != null) {
            ++metrics.archerCandidateChecks;
        }
    }

    public static void recordArcherVisibilityCheck(LOTREntityMumakil mumakil, int slot) {
        Metrics metrics = metricsFor(mumakil);
        if (metrics == null) {
            return;
        }

        ++metrics.visibilityChecks;
        metrics.addArcherWork(slot, 1);
    }

    public static void recordArcherTargetChange(LOTREntityMumakil mumakil) {
        Metrics metrics = metricsFor(mumakil);
        if (metrics != null) {
            ++metrics.archerTargetChanges;
        }
    }

    public static void recordArrowFired(LOTREntityMumakil mumakil, int slot) {
        Metrics metrics = metricsFor(mumakil);
        if (metrics == null) {
            return;
        }

        ++metrics.arrowsFired;
        metrics.addArcherWork(slot, 1);
    }

    public static void recordDriverTargetRead(LOTREntityMumakil mumakil) {
        Metrics metrics = metricsFor(mumakil);
        if (metrics != null) {
            ++metrics.driverTargetReads;
        }
    }

    public static void recordDriverTargetSyncAttempt(LOTREntityMumakil mumakil) {
        Metrics metrics = metricsFor(mumakil);
        if (metrics != null) {
            ++metrics.driverTargetSyncAttempts;
        }
    }

    public static void recordDriverTargetReset(LOTREntityMumakil mumakil) {
        Metrics metrics = metricsFor(mumakil);
        if (metrics != null) {
            ++metrics.driverTargetResets;
        }
    }

    public static void recordArcherSuperLiving(LOTREntityMumakil mumakil, long nanos) {
        Metrics metrics = metricsFor(mumakil);
        if (metrics != null) {
            ++metrics.archerSuperLivingCalls;
            metrics.archerSuperLivingTotalNanos += nanos;
            metrics.archerSuperLivingMaxNanos = Math.max(metrics.archerSuperLivingMaxNanos, nanos);
        }
    }

    public static void recordArcherFullLiving(LOTREntityMumakil mumakil, long nanos) {
        Metrics metrics = metricsFor(mumakil);
        if (metrics != null) {
            metrics.archerFullLivingTotalNanos += nanos;
            metrics.archerFullLivingMaxNanos = Math.max(metrics.archerFullLivingMaxNanos, nanos);
        }
    }

    public static void recordMountSuperLiving(LOTREntityMumakil mumakil, long nanos) {
        Metrics metrics = metricsFor(mumakil);
        if (metrics != null) {
            metrics.mountSuperLivingTotalNanos += nanos;
            metrics.mountSuperLivingMaxNanos = Math.max(metrics.mountSuperLivingMaxNanos, nanos);
        }
    }

    public static void recordTreeScan(LOTREntityMumakil mumakil, long nanos) {
        Metrics metrics = metricsFor(mumakil);
        if (metrics != null) {
            metrics.treeScanTotalNanos += nanos;
            metrics.treeScanMaxNanos = Math.max(metrics.treeScanMaxNanos, nanos);
        }
    }

    public static void recordArcherHandler(LOTREntityMumakil mumakil, long nanos) {
        Metrics metrics = metricsFor(mumakil);
        if (metrics != null) {
            metrics.archerHandlerTotalNanos += nanos;
            metrics.archerHandlerMaxNanos = Math.max(metrics.archerHandlerMaxNanos, nanos);
        }
    }

    public static void recordDriverHandler(LOTREntityMumakil mumakil, long nanos) {
        Metrics metrics = metricsFor(mumakil);
        if (metrics != null) {
            metrics.driverHandlerTotalNanos += nanos;
            metrics.driverHandlerMaxNanos = Math.max(metrics.driverHandlerMaxNanos, nanos);
        }
    }

    public static void recordRiderTargetAIStart(LOTREntityMumakil mumakil) {
        Metrics metrics = metricsFor(mumakil);
        if (metrics != null) {
            ++metrics.riderTargetAIStarts;
        }
    }

    public static void recordRiderTargetAIUpdate(LOTREntityMumakil mumakil) {
        Metrics metrics = metricsFor(mumakil);
        if (metrics != null) {
            ++metrics.riderTargetAIUpdates;
        }
    }

    public static void recordRiderTargetAIReset(LOTREntityMumakil mumakil) {
        Metrics metrics = metricsFor(mumakil);
        if (metrics != null) {
            ++metrics.riderTargetAIResets;
        }
    }

    public static void recordRiderTargetPathRequest(
            LOTREntityMumakil mumakil,
            long nanos,
            boolean accepted
    ) {
        Metrics metrics = metricsFor(mumakil);
        if (metrics != null) {
            ++metrics.riderTargetPathRequests;
            if (!accepted) {
                ++metrics.riderTargetPathFailures;
            }
            metrics.riderTargetPathTotalNanos += nanos;
            metrics.riderTargetPathMaxNanos = Math.max(metrics.riderTargetPathMaxNanos, nanos);
            metrics.addMeasuredTime(nanos);
        }
    }

    public static void recordMountAttackAIStart(LOTREntityMumakil mumakil) {
        Metrics metrics = metricsFor(mumakil);
        if (metrics != null) {
            ++metrics.mountAttackAIStarts;
        }
    }

    public static void recordMountAttackShould(LOTREntityMumakil mumakil, long nanos) {
        Metrics metrics = metricsFor(mumakil);
        if (metrics != null) {
            metrics.mountAttackShouldTotalNanos += nanos;
            metrics.mountAttackShouldMaxNanos = Math.max(metrics.mountAttackShouldMaxNanos, nanos);
        }
    }

    public static void recordMountAttackContinue(LOTREntityMumakil mumakil, long nanos) {
        Metrics metrics = metricsFor(mumakil);
        if (metrics != null) {
            metrics.mountAttackContinueTotalNanos += nanos;
            metrics.mountAttackContinueMaxNanos = Math.max(metrics.mountAttackContinueMaxNanos, nanos);
        }
    }

    public static void recordMountAttackStart(LOTREntityMumakil mumakil, long nanos) {
        Metrics metrics = metricsFor(mumakil);
        if (metrics != null) {
            metrics.mountAttackStartTotalNanos += nanos;
            metrics.mountAttackStartMaxNanos = Math.max(metrics.mountAttackStartMaxNanos, nanos);
        }
    }

    public static void recordMountAttackUpdate(LOTREntityMumakil mumakil, long nanos) {
        Metrics metrics = metricsFor(mumakil);
        if (metrics != null) {
            ++metrics.mountAttackAIUpdates;
            ++metrics.mountAttackUpdateCalls;
            metrics.mountAttackUpdateTotalNanos += nanos;
            metrics.mountAttackUpdateMaxNanos = Math.max(metrics.mountAttackUpdateMaxNanos, nanos);
        }
    }

    public static void recordMountAttackReset(LOTREntityMumakil mumakil, long nanos) {
        Metrics metrics = metricsFor(mumakil);
        if (metrics != null) {
            metrics.mountAttackResetTotalNanos += nanos;
            metrics.mountAttackResetMaxNanos = Math.max(metrics.mountAttackResetMaxNanos, nanos);
        }
    }

    public static void recordMountAttackAIReset(LOTREntityMumakil mumakil) {
        Metrics metrics = metricsFor(mumakil);
        if (metrics != null) {
            ++metrics.mountAttackAIResets;
        }
    }

    public static void recordMountAttackPathRequest(
            LOTREntityMumakil mumakil,
            long nanos,
            boolean accepted
    ) {
        Metrics metrics = metricsFor(mumakil);
        if (metrics != null) {
            ++metrics.mountAttackPathRequests;
            if (!accepted) {
                ++metrics.mountAttackPathFailures;
            }
            metrics.mountAttackPathTotalNanos += nanos;
            metrics.mountAttackPathMaxNanos = Math.max(metrics.mountAttackPathMaxNanos, nanos);
            metrics.addMeasuredTime(nanos);
        }
    }

    public static void recordCombatPathRequest(
            LOTREntityMumakil mumakil,
            long nanos,
            boolean accepted,
            int reason
    ) {
        Metrics metrics = metricsFor(mumakil);
        if (metrics == null) {
            return;
        }

        switch (reason) {
            case COMBAT_PATH_REASON_NEW_TARGET:
                ++metrics.combatPathRequestsNewTarget;
                break;
            case COMBAT_PATH_REASON_NO_PATH:
                ++metrics.combatPathRequestsNoPath;
                break;
            case COMBAT_PATH_REASON_TARGET_MOVED:
                ++metrics.combatPathRequestsTargetMoved;
                break;
            case COMBAT_PATH_REASON_NO_PROGRESS:
                ++metrics.combatPathRequestsNoProgress;
                break;
            default:
                break;
        }

        if (!accepted) {
            ++metrics.combatPathFailures;
        }
        metrics.combatPathTotalNanos += nanos;
        metrics.combatPathMaxNanos = Math.max(metrics.combatPathMaxNanos, nanos);
        metrics.addMeasuredTime(nanos);
    }


    public static void recordCombatPathDetail(
            LOTREntityMumakil mumakil,
            long pathSearchNanos,
            long pathInstallNanos,
            boolean accepted,
            int reason,
            double targetDistanceSq,
            boolean preparingStart
    ) {
        if (!DEBUG_MUMAKIL_PERFORMANCE
                || mumakil == null
                || mumakil.worldObj == null
                || mumakil.worldObj.isRemote
                || pathSearchNanos < COMBAT_PATH_DETAIL_LOG_THRESHOLD_NANOS) {
            return;
        }

        StringBuilder message = new StringBuilder(224);
        message.append("[LOTRMoreMobs CombatPathDetail] mount=").append(mumakil.getEntityId())
                .append(" worldTick=").append(mumakil.worldObj.getTotalWorldTime())
                .append(" entityTick=").append(mumakil.ticksExisted)
                .append(" reason=").append(reason)
                .append(" accepted=").append(accepted)
                .append(" preparingStart=").append(preparingStart)
                .append(" targetDistance=").append(Math.sqrt(targetDistanceSq))
                .append(" searchMs=");
        appendNanosAsMillis(message, pathSearchNanos);
        message.append(" installMs=");
        appendNanosAsMillis(message, pathInstallNanos);

        System.out.println(message.toString());
    }

    public static void recordCombatPathSkippedCooldown(LOTREntityMumakil mumakil) {
        Metrics metrics = metricsFor(mumakil);
        if (metrics != null) {
            ++metrics.combatPathSkippedCooldown;
        }
    }

    public static void recordCombatPathSkippedExistingPath(LOTREntityMumakil mumakil) {
        Metrics metrics = metricsFor(mumakil);
        if (metrics != null) {
            ++metrics.combatPathSkippedExistingPath;
        }
    }

    public static void recordCombatPathBackoff(LOTREntityMumakil mumakil) {
        Metrics metrics = metricsFor(mumakil);
        if (metrics != null) {
            ++metrics.combatPathBackoffs;
        }
    }

    public static void recordMountFollowShould(
            LOTREntityMumakil mumakil,
            boolean blockedByDriver,
            boolean accepted
    ) {
        Metrics metrics = metricsFor(mumakil);
        if (metrics != null) {
            ++metrics.mountFollowShouldCalls;
            if (blockedByDriver) {
                ++metrics.mountFollowShouldBlockedByDriver;
            }
            if (accepted) {
                ++metrics.mountFollowShouldAccepted;
            }
        }
    }

    public static void recordMountFollowStart(LOTREntityMumakil mumakil) {
        Metrics metrics = metricsFor(mumakil);
        if (metrics != null) {
            ++metrics.mountFollowStarts;
        }
    }

    public static void recordMountFollowUpdate(LOTREntityMumakil mumakil, boolean pathCall) {
        Metrics metrics = metricsFor(mumakil);
        if (metrics != null) {
            ++metrics.mountFollowUpdates;
            if (pathCall) {
                ++metrics.mountFollowPathCalls;
            }
        }
    }

    public static void recordDriverPathRequest(
            LOTREntityMumakil mumakil,
            long nanos,
            boolean accepted,
            int reason
    ) {
        Metrics metrics = metricsFor(mumakil);
        if (metrics == null) {
            return;
        }

        ++metrics.driverPathRequests;
        recordDriverPathReason(metrics, reason);
        recordPathRequest(metrics, nanos, accepted, true);
    }

    private static void recordDriverPathReason(Metrics metrics, int reason) {
        switch (reason) {
            case DRIVER_PATH_REASON_NEW_TARGET:
                ++metrics.driverPathRequestsNewTarget;
                break;
            case DRIVER_PATH_REASON_NO_NAVIGATOR_PATH:
                ++metrics.driverPathRequestsNoNavigatorPath;
                break;
            case DRIVER_PATH_REASON_TARGET_MOVED:
                ++metrics.driverPathRequestsTargetMoved;
                break;
            case DRIVER_PATH_REASON_COLLISION_RECOVERY:
                ++metrics.driverPathRequestsCollisionRecovery;
                break;
            case DRIVER_PATH_REASON_FORCED_STUCK:
            default:
                ++metrics.driverPathRequestsForcedStuck;
                break;
        }
    }

    public static void recordDriverPathSkippedCooldown(LOTREntityMumakil mumakil) {
        Metrics metrics = metricsFor(mumakil);
        if (metrics != null) {
            ++metrics.driverPathSkippedCooldown;
        }
    }

    public static void recordDriverPathSkippedExistingPath(LOTREntityMumakil mumakil) {
        Metrics metrics = metricsFor(mumakil);
        if (metrics != null) {
            ++metrics.driverPathSkippedExistingPath;
        }
    }

    public static void recordDriverPathSkippedTargetStationary(LOTREntityMumakil mumakil) {
        Metrics metrics = metricsFor(mumakil);
        if (metrics != null) {
            ++metrics.driverPathSkippedTargetStationary;
        }
    }

    public static void recordDriverForcedRepath(LOTREntityMumakil mumakil) {
        Metrics metrics = metricsFor(mumakil);
        if (metrics != null) {
            ++metrics.driverForcedRepaths;
        }
    }

    public static void recordDriverFailureBackoff(LOTREntityMumakil mumakil) {
        Metrics metrics = metricsFor(mumakil);
        if (metrics != null) {
            ++metrics.driverFailureBackoffs;
        }
    }

    private static void recordPathRequest(Metrics metrics, long nanos, boolean accepted, boolean repath) {
        ++metrics.pathRequests;

        if (repath) {
            ++metrics.navigatorRepaths;
        }

        if (!accepted) {
            ++metrics.pathFailures;
        }

        metrics.pathTotalNanos += nanos;
        if (nanos > metrics.pathMaxNanos) {
            metrics.pathMaxNanos = nanos;
        }

        metrics.addMeasuredTime(nanos);
    }

    private static Metrics metricsFor(LOTREntityMumakil mumakil) {
        if (!DEBUG_MUMAKIL_PERFORMANCE
                || mumakil == null
                || mumakil.worldObj == null
                || mumakil.worldObj.isRemote) {
            return null;
        }

        Integer id = Integer.valueOf(mumakil.getEntityId());
        Metrics metrics = METRICS_BY_MOUNT.get(id);
        if (metrics == null) {
            metrics = new Metrics();
            METRICS_BY_MOUNT.put(id, metrics);
        }

        return metrics;
    }

    private static String getMode(LOTREntityMumakil mumakil) {
        if (mumakil.isHiredWarMumakil()) {
            return "HIRED_WAR";
        }

        if (mumakil.getBelongsToNPC()) {
            return "NPC";
        }

        if (mumakil.isTame()) {
            return "TAMED";
        }

        return "WILD";
    }

    private static int getTargetId(LOTREntityMumakil mumakil) {
        EntityLivingBase target = mumakil.getAttackTarget();
        return target == null || target.isDead ? 0 : target.getEntityId();
    }

    private static void appendNanosAsMillis(StringBuilder message, long nanos) {
        long millis = nanos / 1000000L;
        long micros = nanos % 1000000L / 1000L;

        message.append(millis).append('.');
        if (micros < 100L) {
            message.append('0');
        }
        if (micros < 10L) {
            message.append('0');
        }
        message.append(micros);
    }

    private static final class Metrics {
        private int mountTargetScans;
        private int mountCandidates;
        private int mountCandidateChecks;
        private int pathRequests;
        private int pathFailures;
        private int navigatorRepaths;
        private int targetChanges;
        private int unreachableChecks;
        private int tuskCandidateChecks;
        private int trampleScans;
        private int trampleCandidates;
        private int trampleCandidateChecks;
        private int treePasses;
        private int treeBlocksChecked;
        private int treeBlocksDestroyed;
        private int archerUpdatePasses;
        private int archerTargetScans;
        private int archerCandidates;
        private int archerCandidateChecks;
        private int visibilityChecks;
        private int archerTargetChanges;
        private int arrowsFired;
        private int driverTargetReads;
        private int driverTargetSyncAttempts;
        private int driverPathRequests;
        private int driverPathRequestsNewTarget;
        private int driverPathRequestsNoNavigatorPath;
        private int driverPathRequestsTargetMoved;
        private int driverPathRequestsForcedStuck;
        private int driverPathRequestsCollisionRecovery;
        private int driverTargetResets;
        private int driverPathSkippedCooldown;
        private int driverPathSkippedExistingPath;
        private int driverPathSkippedTargetStationary;
        private int driverForcedRepaths;
        private int driverFailureBackoffs;
        private int riderTargetAIStarts;
        private int riderTargetAIUpdates;
        private int riderTargetAIResets;
        private int riderTargetPathRequests;
        private int riderTargetPathFailures;
        private int mountAttackAIStarts;
        private int mountAttackAIUpdates;
        private int mountAttackAIResets;
        private int mountAttackUpdateCalls;
        private int mountAttackPathRequests;
        private int mountAttackPathFailures;
        private int mountFollowShouldCalls;
        private int mountFollowShouldBlockedByDriver;
        private int mountFollowShouldAccepted;
        private int mountFollowStarts;
        private int mountFollowUpdates;
        private int mountFollowPathCalls;
        private int combatPathRequestsNewTarget;
        private int combatPathRequestsNoPath;
        private int combatPathRequestsTargetMoved;
        private int combatPathRequestsNoProgress;
        private int combatPathSkippedCooldown;
        private int combatPathSkippedExistingPath;
        private int combatPathFailures;
        private int combatPathBackoffs;
        private int attachedArcherSlots;
        private int archerTargetSlots;
        private int[] archerWorkBySlot = new int[MAX_HOWDAH_ARCHER_SLOTS];
        private long pathTotalNanos;
        private long pathMaxNanos;
        private long riderTargetPathTotalNanos;
        private long riderTargetPathMaxNanos;
        private long mountAttackPathTotalNanos;
        private long mountAttackPathMaxNanos;
        private long combatPathTotalNanos;
        private long combatPathMaxNanos;
        private long mountAttackShouldTotalNanos;
        private long mountAttackShouldMaxNanos;
        private long mountAttackContinueTotalNanos;
        private long mountAttackContinueMaxNanos;
        private long mountAttackStartTotalNanos;
        private long mountAttackStartMaxNanos;
        private long mountAttackUpdateTotalNanos;
        private long mountAttackUpdateMaxNanos;
        private long mountAttackResetTotalNanos;
        private long mountAttackResetMaxNanos;
        private int archerSuperLivingCalls;
        private long archerSuperLivingTotalNanos;
        private long archerSuperLivingMaxNanos;
        private long archerFullLivingTotalNanos;
        private long archerFullLivingMaxNanos;
        private long mountSuperLivingTotalNanos;
        private long mountSuperLivingMaxNanos;
        private long treeScanTotalNanos;
        private long treeScanMaxNanos;
        private long archerHandlerTotalNanos;
        private long archerHandlerMaxNanos;
        private long driverHandlerTotalNanos;
        private long driverHandlerMaxNanos;
        private long totalMeasuredNanos;
        private int nextReportTick = -1;

        private void addMeasuredTime(long nanos) {
            if (nanos > 0L) {
                this.totalMeasuredNanos += nanos;
            }
        }

        private void addArcherSlot(int slot, boolean hasTarget) {
            if (slot < 0 || slot >= MAX_HOWDAH_ARCHER_SLOTS) {
                return;
            }

            int slotBit = 1 << slot;
            this.attachedArcherSlots |= slotBit;

            if (hasTarget) {
                this.archerTargetSlots |= slotBit;
            }
        }

        private void addArcherWork(int slot, int workUnits) {
            if (slot < 0 || slot >= MAX_HOWDAH_ARCHER_SLOTS || workUnits <= 0) {
                return;
            }

            this.archerWorkBySlot[slot] += workUnits;
        }

        private int getMaxArcherWork() {
            int max = 0;
            for (int i = 0; i < this.archerWorkBySlot.length; ++i) {
                if (this.archerWorkBySlot[i] > max) {
                    max = this.archerWorkBySlot[i];
                }
            }

            return max;
        }

        private void printAndReset(LOTREntityMumakil mumakil, int nextTick) {
            StringBuilder message = new StringBuilder(512);
            message.append("[LOTRMoreMobs Perf] mount=").append(mumakil.getEntityId())
                    .append(" mode=").append(getMode(mumakil))
                    .append(" target=").append(getTargetId(mumakil))
                    .append(" archers=").append(Integer.bitCount(this.attachedArcherSlots))
                    .append(" mountTargetScans=").append(this.mountTargetScans)
                    .append(" mountCandidates=").append(this.mountCandidates)
                    .append(" mountCandidateChecks=").append(this.mountCandidateChecks)
                    .append(" pathRequests=").append(this.pathRequests)
                    .append(" pathFailures=").append(this.pathFailures)
                    .append(" navigatorRepaths=").append(this.navigatorRepaths)
                    .append(" pathTotalMs=");
            appendNanosAsMillis(message, this.pathTotalNanos);
            message.append(" pathMaxMs=");
            appendNanosAsMillis(message, this.pathMaxNanos);
            message.append(" targetChanges=").append(this.targetChanges)
                    .append(" unreachableChecks=").append(this.unreachableChecks)
                    .append(" tuskChecks=").append(this.tuskCandidateChecks)
                    .append(" trampleScans=").append(this.trampleScans)
                    .append(" trampleCandidates=").append(this.trampleCandidates)
                    .append(" trampleCandidateChecks=").append(this.trampleCandidateChecks)
                    .append(" treePasses=").append(this.treePasses)
                    .append(" treeBlocksChecked=").append(this.treeBlocksChecked)
                    .append(" treeBlocksDestroyed=").append(this.treeBlocksDestroyed)
                    .append(" archerUpdates=").append(this.archerUpdatePasses)
                    .append(" archerScans=").append(this.archerTargetScans)
                    .append(" archerCandidates=").append(this.archerCandidates)
                    .append(" archerCandidateChecks=").append(this.archerCandidateChecks)
                    .append(" visibilityChecks=").append(this.visibilityChecks)
                    .append(" archerTargetChanges=").append(this.archerTargetChanges)
                    .append(" arrowsFired=").append(this.arrowsFired)
                    .append(" archersWithTargets=").append(Integer.bitCount(this.archerTargetSlots))
                    .append(" maxArcherWork=").append(this.getMaxArcherWork())
                    .append(" driverTargetReads=").append(this.driverTargetReads)
                    .append(" driverSyncAttempts=").append(this.driverTargetSyncAttempts)
                    .append(" driverPathRequests=").append(this.driverPathRequests)
                    .append(" driverPathRequestsNewTarget=").append(this.driverPathRequestsNewTarget)
                    .append(" driverPathRequestsNoNavigatorPath=").append(this.driverPathRequestsNoNavigatorPath)
                    .append(" driverPathRequestsTargetMoved=").append(this.driverPathRequestsTargetMoved)
                    .append(" driverPathRequestsForcedStuck=").append(this.driverPathRequestsForcedStuck)
                    .append(" driverPathRequestsCollisionRecovery=").append(this.driverPathRequestsCollisionRecovery)
                    .append(" driverTargetResets=").append(this.driverTargetResets)
                    .append(" driverPathSkippedCooldown=").append(this.driverPathSkippedCooldown)
                    .append(" driverPathSkippedExistingPath=").append(this.driverPathSkippedExistingPath)
                    .append(" driverPathSkippedTargetStationary=").append(this.driverPathSkippedTargetStationary)
                    .append(" driverForcedRepaths=").append(this.driverForcedRepaths)
                    .append(" driverFailureBackoffs=").append(this.driverFailureBackoffs)
                    .append(" riderTargetAIStarts=").append(this.riderTargetAIStarts)
                    .append(" riderTargetAIUpdates=").append(this.riderTargetAIUpdates)
                    .append(" riderTargetAIResets=").append(this.riderTargetAIResets)
                    .append(" riderTargetPathRequests=").append(this.riderTargetPathRequests)
                    .append(" riderTargetPathFailures=").append(this.riderTargetPathFailures)
                    .append(" riderTargetPathTotalMs=");
            appendNanosAsMillis(message, this.riderTargetPathTotalNanos);
            message.append(" riderTargetPathMaxMs=");
            appendNanosAsMillis(message, this.riderTargetPathMaxNanos);
            message.append(" mountAttackAIStarts=").append(this.mountAttackAIStarts)
                    .append(" mountAttackAIUpdates=").append(this.mountAttackAIUpdates)
                    .append(" mountAttackAIResets=").append(this.mountAttackAIResets)
                    .append(" mountAttackPathRequests=").append(this.mountAttackPathRequests)
                    .append(" mountAttackPathFailures=").append(this.mountAttackPathFailures)
                    .append(" mountAttackPathTotalMs=");
            appendNanosAsMillis(message, this.mountAttackPathTotalNanos);
            message.append(" mountAttackPathMaxMs=");
            appendNanosAsMillis(message, this.mountAttackPathMaxNanos);
            message.append(" mountFollowShouldCalls=").append(this.mountFollowShouldCalls)
                    .append(" mountFollowShouldBlockedByDriver=").append(this.mountFollowShouldBlockedByDriver)
                    .append(" mountFollowShouldAccepted=").append(this.mountFollowShouldAccepted)
                    .append(" mountFollowStarts=").append(this.mountFollowStarts)
                    .append(" mountFollowUpdates=").append(this.mountFollowUpdates)
                    .append(" mountFollowPathCalls=").append(this.mountFollowPathCalls);
            message.append(" combatPathRequestsNewTarget=").append(this.combatPathRequestsNewTarget)
                    .append(" combatPathRequestsNoPath=").append(this.combatPathRequestsNoPath)
                    .append(" combatPathRequestsTargetMoved=").append(this.combatPathRequestsTargetMoved)
                    .append(" combatPathRequestsNoProgress=").append(this.combatPathRequestsNoProgress)
                    .append(" combatPathSkippedCooldown=").append(this.combatPathSkippedCooldown)
                    .append(" combatPathSkippedExistingPath=").append(this.combatPathSkippedExistingPath)
                    .append(" combatPathFailures=").append(this.combatPathFailures)
                    .append(" combatPathBackoffs=").append(this.combatPathBackoffs)
                    .append(" combatPathTotalMs=");
            appendNanosAsMillis(message, this.combatPathTotalNanos);
            message.append(" combatPathMaxMs=");
            appendNanosAsMillis(message, this.combatPathMaxNanos);
            message.append(" mountAttackShouldTotalMs=");
            appendNanosAsMillis(message, this.mountAttackShouldTotalNanos);
            message.append(" mountAttackShouldMaxMs=");
            appendNanosAsMillis(message, this.mountAttackShouldMaxNanos);
            message.append(" mountAttackContinueTotalMs=");
            appendNanosAsMillis(message, this.mountAttackContinueTotalNanos);
            message.append(" mountAttackContinueMaxMs=");
            appendNanosAsMillis(message, this.mountAttackContinueMaxNanos);
            message.append(" mountAttackStartTotalMs=");
            appendNanosAsMillis(message, this.mountAttackStartTotalNanos);
            message.append(" mountAttackStartMaxMs=");
            appendNanosAsMillis(message, this.mountAttackStartMaxNanos);
            message.append(" mountAttackUpdateCalls=").append(this.mountAttackUpdateCalls)
                    .append(" mountAttackUpdateTotalMs=");
            appendNanosAsMillis(message, this.mountAttackUpdateTotalNanos);
            message.append(" mountAttackUpdateMaxMs=");
            appendNanosAsMillis(message, this.mountAttackUpdateMaxNanos);
            message.append(" mountAttackResetTotalMs=");
            appendNanosAsMillis(message, this.mountAttackResetTotalNanos);
            message.append(" mountAttackResetMaxMs=");
            appendNanosAsMillis(message, this.mountAttackResetMaxNanos);
            message.append(" archerSuperLivingCalls=").append(this.archerSuperLivingCalls)
                    .append(" archerSuperLivingTotalMs=");
            appendNanosAsMillis(message, this.archerSuperLivingTotalNanos);
            message.append(" archerSuperLivingMaxMs=");
            appendNanosAsMillis(message, this.archerSuperLivingMaxNanos);
            message.append(" archerFullLivingTotalMs=");
            appendNanosAsMillis(message, this.archerFullLivingTotalNanos);
            message.append(" archerFullLivingMaxMs=");
            appendNanosAsMillis(message, this.archerFullLivingMaxNanos);
            message.append(" mountSuperLivingTotalMs=");
            appendNanosAsMillis(message, this.mountSuperLivingTotalNanos);
            message.append(" mountSuperLivingMaxMs=");
            appendNanosAsMillis(message, this.mountSuperLivingMaxNanos);
            message.append(" treeScanTotalMs=");
            appendNanosAsMillis(message, this.treeScanTotalNanos);
            message.append(" treeScanMaxMs=");
            appendNanosAsMillis(message, this.treeScanMaxNanos);
            message.append(" archerHandlerTotalMs=");
            appendNanosAsMillis(message, this.archerHandlerTotalNanos);
            message.append(" archerHandlerMaxMs=");
            appendNanosAsMillis(message, this.archerHandlerMaxNanos);
            message.append(" driverHandlerTotalMs=");
            appendNanosAsMillis(message, this.driverHandlerTotalNanos);
            message.append(" driverHandlerMaxMs=");
            appendNanosAsMillis(message, this.driverHandlerMaxNanos);
            message.append(" totalMeasuredMs=");
            appendNanosAsMillis(message, this.totalMeasuredNanos);

            System.out.println(message.toString());
            this.reset(nextTick);
        }

        private void reset(int nextTick) {
            this.mountTargetScans = 0;
            this.mountCandidates = 0;
            this.mountCandidateChecks = 0;
            this.pathRequests = 0;
            this.pathFailures = 0;
            this.navigatorRepaths = 0;
            this.targetChanges = 0;
            this.unreachableChecks = 0;
            this.tuskCandidateChecks = 0;
            this.trampleScans = 0;
            this.trampleCandidates = 0;
            this.trampleCandidateChecks = 0;
            this.treePasses = 0;
            this.treeBlocksChecked = 0;
            this.treeBlocksDestroyed = 0;
            this.archerUpdatePasses = 0;
            this.archerTargetScans = 0;
            this.archerCandidates = 0;
            this.archerCandidateChecks = 0;
            this.visibilityChecks = 0;
            this.archerTargetChanges = 0;
            this.arrowsFired = 0;
            this.driverTargetReads = 0;
            this.driverTargetSyncAttempts = 0;
            this.driverPathRequests = 0;
            this.driverPathRequestsNewTarget = 0;
            this.driverPathRequestsNoNavigatorPath = 0;
            this.driverPathRequestsTargetMoved = 0;
            this.driverPathRequestsForcedStuck = 0;
            this.driverPathRequestsCollisionRecovery = 0;
            this.driverTargetResets = 0;
            this.driverPathSkippedCooldown = 0;
            this.driverPathSkippedExistingPath = 0;
            this.driverPathSkippedTargetStationary = 0;
            this.driverForcedRepaths = 0;
            this.driverFailureBackoffs = 0;
            this.riderTargetAIStarts = 0;
            this.riderTargetAIUpdates = 0;
            this.riderTargetAIResets = 0;
            this.riderTargetPathRequests = 0;
            this.riderTargetPathFailures = 0;
            this.mountAttackAIStarts = 0;
            this.mountAttackAIUpdates = 0;
            this.mountAttackAIResets = 0;
            this.mountAttackUpdateCalls = 0;
            this.mountAttackPathRequests = 0;
            this.mountAttackPathFailures = 0;
            this.mountFollowShouldCalls = 0;
            this.mountFollowShouldBlockedByDriver = 0;
            this.mountFollowShouldAccepted = 0;
            this.mountFollowStarts = 0;
            this.mountFollowUpdates = 0;
            this.mountFollowPathCalls = 0;
            this.combatPathRequestsNewTarget = 0;
            this.combatPathRequestsNoPath = 0;
            this.combatPathRequestsTargetMoved = 0;
            this.combatPathRequestsNoProgress = 0;
            this.combatPathSkippedCooldown = 0;
            this.combatPathSkippedExistingPath = 0;
            this.combatPathFailures = 0;
            this.combatPathBackoffs = 0;
            this.attachedArcherSlots = 0;
            this.archerTargetSlots = 0;
            this.pathTotalNanos = 0L;
            this.pathMaxNanos = 0L;
            this.riderTargetPathTotalNanos = 0L;
            this.riderTargetPathMaxNanos = 0L;
            this.mountAttackPathTotalNanos = 0L;
            this.mountAttackPathMaxNanos = 0L;
            this.combatPathTotalNanos = 0L;
            this.combatPathMaxNanos = 0L;
            this.mountAttackShouldTotalNanos = 0L;
            this.mountAttackShouldMaxNanos = 0L;
            this.mountAttackContinueTotalNanos = 0L;
            this.mountAttackContinueMaxNanos = 0L;
            this.mountAttackStartTotalNanos = 0L;
            this.mountAttackStartMaxNanos = 0L;
            this.mountAttackUpdateTotalNanos = 0L;
            this.mountAttackUpdateMaxNanos = 0L;
            this.mountAttackResetTotalNanos = 0L;
            this.mountAttackResetMaxNanos = 0L;
            this.archerSuperLivingCalls = 0;
            this.archerSuperLivingTotalNanos = 0L;
            this.archerSuperLivingMaxNanos = 0L;
            this.archerFullLivingTotalNanos = 0L;
            this.archerFullLivingMaxNanos = 0L;
            this.mountSuperLivingTotalNanos = 0L;
            this.mountSuperLivingMaxNanos = 0L;
            this.treeScanTotalNanos = 0L;
            this.treeScanMaxNanos = 0L;
            this.archerHandlerTotalNanos = 0L;
            this.archerHandlerMaxNanos = 0L;
            this.driverHandlerTotalNanos = 0L;
            this.driverHandlerMaxNanos = 0L;
            this.totalMeasuredNanos = 0L;
            this.nextReportTick = nextTick;

            for (int i = 0; i < this.archerWorkBySlot.length; ++i) {
                this.archerWorkBySlot[i] = 0;
            }
        }
    }
}
