package com.enovak.lotrmoremobs.spawning;

import com.enovak.lotrmoremobs.config.MumakilConfig;
import com.enovak.lotrmoremobs.entity.animal.MumakilFormationOrigin;
import com.enovak.lotrmoremobs.entity.npc.LOTREntityMumakilHowdahArcher;
import com.enovak.lotrmoremobs.util.MumakilServerPerformanceDiagnostics;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lotr.common.entity.npc.LOTREntityNPC;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;

/**
 * One bounded delayed replacement pipeline for native-home, conquest, and
 * invasion Mumak formations. Origin handlers own classification only; every
 * admitted NPC reaches random admission, capacity, placement, and the
 * transactional factory through this class.
 */
public final class MumakilFormationReplacementService {
    public static final int CANDIDATE_DELAY_MIN_TICKS = 20;
    public static final int CANDIDATE_DELAY_MAX_TICKS = 60;
    public static final int CANDIDATE_EXPIRATION_TICKS = 600;
    public static final int CANDIDATES_PER_WORLD_TICK = 2;
    public static final int QUEUE_RECORDS_VISITED_PER_WORLD_TICK = 16;
    public static final int MAX_PENDING_CANDIDATES_PER_WORLD = 512;
    public static final double RESERVATION_RADIUS = 12.0D;
    public static final int RESERVATION_TICKS = 40;

    private static final boolean ENABLE_REPLACEMENT_FUNNEL = true;
    private static final boolean DEBUG_REPLACEMENT_DETAIL = false;
    private static final long FUNNEL_REPORT_INTERVAL_TICKS = 1200L;
    private static final double RESERVATION_TRIGGER_RADIUS =
            RESERVATION_RADIUS + 10.0D;
    private static final double RESERVATION_TRIGGER_RADIUS_SQ =
            RESERVATION_TRIGGER_RADIUS * RESERVATION_TRIGGER_RADIUS;

    private static final String PENDING_KEY =
            "lotrmoremobs_mumakFormationReplacementPending";
    private static final String PENDING_SINCE_KEY =
            "lotrmoremobs_mumakFormationReplacementPendingSince";
    private static final String PENDING_ORIGIN_KEY =
            "lotrmoremobs_mumakFormationReplacementPendingOrigin";
    private static final String EVALUATED_KEY =
            "lotrmoremobs_mumakFormationReplacementEvaluated";

    private static final String LEGACY_HOME_PENDING_KEY =
            "lotrmoremobs_mumakHomeUnitRollPending";
    private static final String LEGACY_HOME_EVALUATED_KEY =
            "lotrmoremobs_mumakHomeUnitRollEvaluated";
    private static final String LEGACY_CONQUEST_PENDING_KEY =
            "lotrmoremobs_mumakConquestUnitRollPending";
    private static final String LEGACY_CONQUEST_EVALUATED_KEY =
            "lotrmoremobs_mumakConquestUnitRollEvaluated";
    private static final String LEGACY_INVASION_EVALUATED_KEY =
            "lotrmoremobs_mumakInvasionUnitRollEvaluated";

    private final Map<World, LinkedHashMap<UUID, QueuedCandidate>>
            pendingByWorld =
            new IdentityHashMap<World,
                    LinkedHashMap<UUID, QueuedCandidate>>();
    private final Map<World, List<LocalReservation>>
            reservationsByWorld =
            new IdentityHashMap<World, List<LocalReservation>>();
    private final Map<World, Map<MumakilFormationOrigin, OriginStats>>
            statsByWorld =
            new IdentityHashMap<World,
                    Map<MumakilFormationOrigin, OriginStats>>();

    public interface RevalidationPolicy {
        boolean isStillEligible(
                LOTREntityNPC candidate,
                ReplacementContext context
        );
    }

    public static final class ReplacementContext {
        private final MumakilFormationOrigin origin;
        private int rollDenominator;
        private final UUID invasionId;
        private final RevalidationPolicy revalidationPolicy;

        public ReplacementContext(
                MumakilFormationOrigin origin,
                int rollDenominator,
                UUID invasionId,
                RevalidationPolicy revalidationPolicy
        ) {
            this.origin = origin;
            this.rollDenominator = Math.max(1, rollDenominator);
            this.invasionId = invasionId;
            this.revalidationPolicy = revalidationPolicy;
        }

        public MumakilFormationOrigin getOrigin() {
            return this.origin;
        }

        public int getRollDenominator() {
            return this.rollDenominator;
        }

        public void setRollDenominator(int rollDenominator) {
            this.rollDenominator = Math.max(1, rollDenominator);
        }

        public UUID getInvasionId() {
            return this.invasionId;
        }
    }

    public static boolean isReplacementEvaluated(LOTREntityNPC npc) {
        if (npc == null) {
            return true;
        }
        NBTTagCompound data = npc.getEntityData();
        boolean evaluated = data.getBoolean(EVALUATED_KEY)
                || data.getBoolean(LEGACY_HOME_EVALUATED_KEY)
                || data.getBoolean(LEGACY_CONQUEST_EVALUATED_KEY)
                || data.getBoolean(LEGACY_INVASION_EVALUATED_KEY);
        if (evaluated && !data.getBoolean(EVALUATED_KEY)) {
            data.setBoolean(EVALUATED_KEY, true);
        }
        return evaluated;
    }

    public static void markReplacementEvaluated(LOTREntityNPC npc) {
        if (npc == null) {
            return;
        }
        NBTTagCompound data = npc.getEntityData();
        data.setBoolean(PENDING_KEY, false);
        data.setLong(PENDING_SINCE_KEY, 0L);
        data.setInteger(PENDING_ORIGIN_KEY, 0);
        data.setBoolean(EVALUATED_KEY, true);
        data.setBoolean(LEGACY_HOME_PENDING_KEY, false);
        data.setBoolean(LEGACY_CONQUEST_PENDING_KEY, false);
    }

    public static boolean isGeneratedFormationMember(LOTREntityNPC npc) {
        return npc instanceof LOTREntityMumakilHowdahArcher
                || npc != null
                && npc.getEntityData().getBoolean(
                MumakilWarFormationFactory
                        .FORMATION_REPLACEMENT_MEMBER_KEY
        );
    }

    public void recordJoined(
            World world,
            MumakilFormationOrigin origin
    ) {
        ++getStats(world, origin).joined;
    }

    public void recordClassificationReject(
            World world,
            MumakilFormationOrigin origin
    ) {
        ++getStats(world, origin).classificationReject;
    }

    public void recordAlreadyEvaluated(
            World world,
            MumakilFormationOrigin origin
    ) {
        ++getStats(world, origin).alreadyEvaluated;
    }

    public boolean queueCandidate(
            LOTREntityNPC npc,
            ReplacementContext context
    ) {
        if (!isContextValid(context)
                || npc == null
                || npc.worldObj == null
                || npc.worldObj.isRemote
                || npc.isDead) {
            return false;
        }

        World world = npc.worldObj;
        OriginStats stats = getStats(world, context.origin);
        if (isReplacementEvaluated(npc)) {
            ++stats.alreadyEvaluated;
            return false;
        }

        NBTTagCompound data = npc.getEntityData();
        long now = world.getTotalWorldTime();
        boolean wasPending = data.getBoolean(PENDING_KEY);
        boolean hasPendingSince =
                data.hasKey(PENDING_SINCE_KEY);
        long pendingSince = data.getLong(PENDING_SINCE_KEY);
        long firstPendingTick = wasPending && hasPendingSince
                ? pendingSince
                : now;
        if (wasPending
                && hasPendingSince
                && now - pendingSince
                > CANDIDATE_EXPIRATION_TICKS) {
            markReplacementEvaluated(npc);
            ++stats.expired;
            ++stats.originalPreserved;
            return false;
        }

        int pendingOrigin = data.getInteger(PENDING_ORIGIN_KEY);
        if (wasPending
                && pendingOrigin != 0
                && pendingOrigin != context.origin.getId()) {
            ++stats.classificationReject;
            return false;
        }

        LinkedHashMap<UUID, QueuedCandidate> pending =
                pendingByWorld.get(world);
        if (pending == null) {
            pending =
                    new LinkedHashMap<UUID, QueuedCandidate>();
            pendingByWorld.put(world, pending);
        }

        UUID uuid = npc.getPersistentID();
        QueuedCandidate existing = pending.get(uuid);
        if (existing != null) {
            if (existing.context.origin != context.origin) {
                ++stats.classificationReject;
                return false;
            }
            existing.entityId = npc.getEntityId();
            if (!existing.rollApproved) {
                existing.context = context;
                existing.nextAttemptTick = Math.min(
                        existing.nextAttemptTick,
                        now + CANDIDATE_DELAY_MIN_TICKS
                );
                existing.expirationTick = Math.min(
                        existing.expirationTick,
                        firstPendingTick
                                + CANDIDATE_EXPIRATION_TICKS
                );
            }
            return true;
        }

        if (pending.size() >= MAX_PENDING_CANDIDATES_PER_WORLD) {
            markReplacementEvaluated(npc);
            ++stats.revalidationReject;
            ++stats.originalPreserved;
            return false;
        }

        int delay = CANDIDATE_DELAY_MIN_TICKS
                + npc.getRNG().nextInt(
                CANDIDATE_DELAY_MAX_TICKS
                        - CANDIDATE_DELAY_MIN_TICKS
                        + 1
        );
        pending.put(
                uuid,
                new QueuedCandidate(
                        npc.getEntityId(),
                        uuid,
                        context,
                        now + delay,
                        firstPendingTick
                                + CANDIDATE_EXPIRATION_TICKS
                )
        );
        data.setBoolean(PENDING_KEY, true);
        if (!wasPending || !hasPendingSince) {
            data.setLong(PENDING_SINCE_KEY, now);
        }
        data.setInteger(PENDING_ORIGIN_KEY, context.origin.getId());
        ++stats.classificationPass;
        ++stats.queued;
        debug("queued", npc, context);
        return true;
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event == null
                || event.phase != TickEvent.Phase.END
                || event.world == null
                || event.world.isRemote) {
            return;
        }

        World world = event.world;
        LinkedHashMap<UUID, QueuedCandidate> pending =
                pendingByWorld.get(world);
        if (pending != null && !pending.isEmpty()) {
            long start =
                    MumakilServerPerformanceDiagnostics.startTimer(world);
            processDueCandidates(world);
            MumakilServerPerformanceDiagnostics
                    .recordHomeQueueProcessing(
                            world,
                            System.nanoTime() - start
                    );
        }

        reportIfDue(world, false);
        MumakilServerPerformanceDiagnostics.reportIfDue(world, false);
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event == null || event.world == null) {
            return;
        }
        World world = event.world;
        pendingByWorld.remove(world);
        reservationsByWorld.remove(world);
        reportIfDue(world, true);
        statsByWorld.remove(world);
        MumakilServerPerformanceDiagnostics.onWorldUnload(world);
    }

    private void processDueCandidates(World world) {
        long now = world.getTotalWorldTime();
        clearExpiredReservations(world, now);
        LinkedHashMap<UUID, QueuedCandidate> pending =
                pendingByWorld.get(world);
        if (pending == null || pending.isEmpty()) {
            return;
        }

        int initialRecordsProcessed = 0;
        int approvedRecordsProcessed = 0;
        int approvedRecordsLimit = Math.max(
                1,
                MumakilConfig.maxApprovedRecordsProcessedPerTick
        );
        int recordsVisitLimit = Math.max(
                QUEUE_RECORDS_VISITED_PER_WORLD_TICK,
                CANDIDATES_PER_WORLD_TICK + approvedRecordsLimit
        );
        int recordsVisited = 0;
        List<QueuedCandidate> rotateToBack =
                new ArrayList<QueuedCandidate>(
                        recordsVisitLimit
                );
        Iterator<Map.Entry<UUID, QueuedCandidate>> iterator =
                pending.entrySet().iterator();
        while (iterator.hasNext()
                && recordsVisited
                < recordsVisitLimit
                && (initialRecordsProcessed
                < CANDIDATES_PER_WORLD_TICK
                || approvedRecordsProcessed
                < approvedRecordsLimit)) {
            QueuedCandidate queued = iterator.next().getValue();
            ++recordsVisited;
            OriginStats stats =
                    getStats(world, queued.context.origin);
            if (queued.rollApproved) {
                refreshApprovedRetryTiming(queued);
            }
            if (queued.rollApproved
                    && now
                    >= queued.placementRetryDeadlineTick) {
                LOTREntityNPC exhausted =
                        resolveCandidate(world, queued);
                ++stats.placementRetryExhausted;
                ++stats.originalPreserved;
                debug(
                        "placementRetryExhausted",
                        exhausted,
                        queued.context
                );
                iterator.remove();
                continue;
            }
            if (!queued.rollApproved
                    && now > queued.expirationTick) {
                LOTREntityNPC expired =
                        resolveCandidate(world, queued);
                if (expired != null) {
                    markReplacementEvaluated(expired);
                    ++stats.originalPreserved;
                }
                ++stats.expired;
                iterator.remove();
                continue;
            }
            long nextDueTick = queued.rollApproved
                    ? queued.nextPlacementRetryTick
                    : queued.nextAttemptTick;
            if (now < nextDueTick) {
                iterator.remove();
                rotateToBack.add(queued);
                continue;
            }
            if (queued.rollApproved
                    && approvedRecordsProcessed
                    >= approvedRecordsLimit) {
                iterator.remove();
                rotateToBack.add(queued);
                continue;
            }
            if (!queued.rollApproved
                    && initialRecordsProcessed
                    >= CANDIDATES_PER_WORLD_TICK) {
                iterator.remove();
                rotateToBack.add(queued);
                continue;
            }

            if (queued.rollApproved) {
                ++approvedRecordsProcessed;
            } else {
                ++initialRecordsProcessed;
            }
            LOTREntityNPC npc = resolveCandidate(world, queued);
            if (npc == null) {
                if (queued.rollApproved) {
                    scheduleNextPlacementRetry(queued, now);
                } else {
                    queued.nextAttemptTick =
                            now + CANDIDATE_DELAY_MIN_TICKS;
                }
                iterator.remove();
                rotateToBack.add(queued);
                continue;
            }

            CandidateResult result =
                    evaluateCandidate(world, npc, queued, now, stats);
            iterator.remove();
            if (result == CandidateResult.DEFER) {
                rotateToBack.add(queued);
            }
        }

        for (int i = 0; i < rotateToBack.size(); ++i) {
            QueuedCandidate queued = rotateToBack.get(i);
            pending.put(queued.uuid, queued);
        }
        if (pending.isEmpty()) {
            pendingByWorld.remove(world);
        }
    }

    private CandidateResult evaluateCandidate(
            World world,
            LOTREntityNPC npc,
            QueuedCandidate queued,
            long now,
            OriginStats stats
    ) {
        ReplacementContext context = queued.context;
        boolean retryProcessing = queued.rollApproved;
        if (!retryProcessing && isReplacementEvaluated(npc)) {
            ++stats.alreadyEvaluated;
            return CandidateResult.REMOVE;
        }
        if (npc.worldObj != world
                || npc.isDead
                || !npc.isEntityAlive()
                || isGeneratedFormationMember(npc)
                || context.revalidationPolicy == null
                || !context.revalidationPolicy
                .isStillEligible(npc, context)) {
            if (!retryProcessing) {
                markReplacementEvaluated(npc);
            } else {
                ++stats.placementRetryInvalidated;
            }
            ++stats.revalidationReject;
            if (!npc.isDead) {
                ++stats.originalPreserved;
            }
            debug(
                    retryProcessing
                            ? "placementRetryInvalidated"
                            : "revalidationReject",
                    npc,
                    context
            );
            return CandidateResult.REMOVE;
        }

        if (!retryProcessing) {
            /*
             * This persistent marker is set exactly once, before the sole
             * probability roll. Approved placement retries remain evaluated,
             * so neither a retry nor a chunk reload can grant another roll.
             */
            markReplacementEvaluated(npc);
            int denominator = Math.max(1, context.rollDenominator);
            if (npc.getRNG().nextInt(denominator) != 0) {
                ++stats.rollFail;
                ++stats.originalPreserved;
                debug("rollFail", npc, context);
                return CandidateResult.REMOVE;
            }
            ++stats.rollPass;
            queued.rollApproved = true;
            queued.placementRetryCount = 0;
            queued.placementRetryStartTick = now;
            queued.placementRetryScheduledTick = -1L;
            queued.nextPlacementRetryTick = now;
            queued.placementRetryDeadlineTick =
                    now + Math.max(
                            1,
                            MumakilConfig.placementRetryTimeoutTicks
                    );
        } else {
            ++stats.placementRetryAttempt;
            debug("placementRetryAttempt", npc, context);
        }

        if (!MumakilWarFormationFactory
                .hasReplacementFormationSpawnCapacity(
                        world,
                        npc,
                        context.origin
                )) {
            ++stats.capacityReject;
            debug("capacityReject", npc, context);
            return schedulePlacementRetry(
                    npc,
                    queued,
                    now,
                    stats
            );
        }
        ++stats.capacityPass;

        LocalReservation reservation = findReservationNear(
                world,
                npc.posX,
                npc.posZ,
                RESERVATION_TRIGGER_RADIUS_SQ,
                now
        );
        if (reservation != null) {
            return schedulePlacementRetry(
                    npc,
                    queued,
                    now,
                    stats
            );
        }

        ++queued.placementRetryCount;
        MumakilWarFormationFactory
                .FormationPlacementSearchResult placement =
                MumakilWarFormationFactory
                        .findReplacementFormationPlacement(world, npc);
        if (!placement.isFound()) {
            ++stats.placementReject;
            debug("placementReject", npc, context);
            return schedulePlacementRetry(
                    npc,
                    queued,
                    now,
                    stats
            );
        }
        ++stats.placementPass;

        LocalReservation localReservation = addReservation(
                world,
                placement.getX(),
                placement.getZ(),
                now + RESERVATION_TICKS
        );
        ++stats.factoryAttempt;
        boolean formed;
        long factoryStart =
                MumakilServerPerformanceDiagnostics.startTimer(world);
        try {
            formed = MumakilWarFormationFactory
                    .createReplacementFormationFromUnit(
                            npc,
                            placement,
                            context.origin,
                            context.invasionId
                    );
        } finally {
            removeReservation(world, localReservation);
            MumakilServerPerformanceDiagnostics
                    .recordFormationFactory(
                            world,
                            System.nanoTime() - factoryStart
                    );
        }

        if (formed) {
            ++stats.success;
            if (context.origin
                    == MumakilFormationOrigin.NATURAL_NEAR_HARAD) {
                MumakilServerPerformanceDiagnostics
                        .recordHomeFormationSucceeded(world);
            }
            npc.setDead();
            if (retryProcessing) {
                ++stats.placementRetrySuccess;
                debug("placementRetrySuccess", npc, context);
            }
            debug("success", npc, context);
            return CandidateResult.REMOVE;
        } else {
            ++stats.rollback;
            debug("rollback", npc, context);
            return schedulePlacementRetry(
                    npc,
                    queued,
                    now,
                    stats
            );
        }
    }

    private CandidateResult schedulePlacementRetry(
            LOTREntityNPC npc,
            QueuedCandidate queued,
            long now,
            OriginStats stats
    ) {
        if (queued.placementRetryCount
                >= Math.max(
                        1,
                        MumakilConfig.maxPlacementRetries
                )
                || now
                >= queued.placementRetryDeadlineTick) {
            ++stats.placementRetryExhausted;
            ++stats.originalPreserved;
            debug(
                    "placementRetryExhausted",
                    npc,
                    queued.context
            );
            return CandidateResult.REMOVE;
        }

        scheduleNextPlacementRetry(queued, now);
        ++stats.placementRetryScheduled;
        debug("placementRetryScheduled", npc, queued.context);
        return CandidateResult.DEFER;
    }

    private static void scheduleNextPlacementRetry(
            QueuedCandidate queued,
            long now
    ) {
        queued.placementRetryScheduledTick = now;
        refreshApprovedRetryTiming(queued);
    }

    private static void refreshApprovedRetryTiming(
            QueuedCandidate queued
    ) {
        queued.placementRetryDeadlineTick =
                queued.placementRetryStartTick
                        + Math.max(
                                1,
                                MumakilConfig
                                        .placementRetryTimeoutTicks
                        );
        if (queued.placementRetryScheduledTick >= 0L) {
            queued.nextPlacementRetryTick = Math.min(
                    queued.placementRetryScheduledTick
                            + Math.max(
                                    1,
                                    MumakilConfig
                                            .placementRetryIntervalTicks
                            ),
                    queued.placementRetryDeadlineTick
            );
        }
    }

    private static boolean isContextValid(ReplacementContext context) {
        if (context == null || context.revalidationPolicy == null) {
            return false;
        }
        MumakilFormationOrigin origin = context.origin;
        return (origin == MumakilFormationOrigin.NATURAL_NEAR_HARAD
                || origin == MumakilFormationOrigin.CONQUEST_NEAR_HARAD
                || origin == MumakilFormationOrigin.INVASION_NEAR_HARAD
                && context.invasionId != null);
    }

    private LOTREntityNPC resolveCandidate(
            World world,
            QueuedCandidate queued
    ) {
        Entity entity = world.getEntityByID(queued.entityId);
        if (!(entity instanceof LOTREntityNPC)
                || entity.worldObj != world
                || !queued.uuid.equals(entity.getPersistentID())) {
            return null;
        }
        return (LOTREntityNPC)entity;
    }

    private LocalReservation addReservation(
            World world,
            double x,
            double z,
            long untilTick
    ) {
        List<LocalReservation> reservations =
                reservationsByWorld.get(world);
        if (reservations == null) {
            reservations = new ArrayList<LocalReservation>();
            reservationsByWorld.put(world, reservations);
        }
        LocalReservation reservation =
                new LocalReservation(x, z, untilTick);
        reservations.add(reservation);
        return reservation;
    }

    private void removeReservation(
            World world,
            LocalReservation reservation
    ) {
        List<LocalReservation> reservations =
                reservationsByWorld.get(world);
        if (reservations == null) {
            return;
        }
        reservations.remove(reservation);
        if (reservations.isEmpty()) {
            reservationsByWorld.remove(world);
        }
    }

    private LocalReservation findReservationNear(
            World world,
            double x,
            double z,
            double radiusSq,
            long now
    ) {
        List<LocalReservation> reservations =
                reservationsByWorld.get(world);
        if (reservations == null) {
            return null;
        }
        for (int i = 0; i < reservations.size(); ++i) {
            LocalReservation reservation = reservations.get(i);
            if (reservation.untilTick >= now) {
                double dx = reservation.x - x;
                double dz = reservation.z - z;
                if (dx * dx + dz * dz <= radiusSq) {
                    return reservation;
                }
            }
        }
        return null;
    }

    private void clearExpiredReservations(World world, long now) {
        List<LocalReservation> reservations =
                reservationsByWorld.get(world);
        if (reservations == null) {
            return;
        }
        Iterator<LocalReservation> iterator = reservations.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().untilTick < now) {
                iterator.remove();
            }
        }
        if (reservations.isEmpty()) {
            reservationsByWorld.remove(world);
        }
    }

    private OriginStats getStats(
            World world,
            MumakilFormationOrigin origin
    ) {
        Map<MumakilFormationOrigin, OriginStats> byOrigin =
                statsByWorld.get(world);
        if (byOrigin == null) {
            byOrigin =
                    new java.util.EnumMap<MumakilFormationOrigin,
                            OriginStats>(MumakilFormationOrigin.class);
            statsByWorld.put(world, byOrigin);
        }
        OriginStats stats = byOrigin.get(origin);
        if (stats == null) {
            stats = new OriginStats(
                    world == null
                            ? 0L
                            : world.getTotalWorldTime()
            );
            byOrigin.put(origin, stats);
        }
        return stats;
    }

    private void reportIfDue(World world, boolean force) {
        Map<MumakilFormationOrigin, OriginStats> byOrigin =
                statsByWorld.get(world);
        if (byOrigin == null) {
            return;
        }
        Iterator<Map.Entry<MumakilFormationOrigin, OriginStats>>
                iterator = byOrigin.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<MumakilFormationOrigin, OriginStats> entry =
                    iterator.next();
            entry.getValue().reportIfDue(
                    world,
                    entry.getKey(),
                    force
            );
        }
    }

    private static void debug(
            String stage,
            LOTREntityNPC npc,
            ReplacementContext context
    ) {
        if (!DEBUG_REPLACEMENT_DETAIL
                || npc == null
                || context == null) {
            return;
        }
        System.out.println(
                "[LOTRMoreMobs] Mumak replacement detail:"
                        + " stage=" + stage
                        + " origin=" + context.origin
                        + " entity=" + npc.getClass().getSimpleName()
                        + " id=" + npc.getEntityId()
                        + " denominator=1/"
                        + context.rollDenominator
                        + " invasionId=" + context.invasionId
        );
    }

    private enum CandidateResult {
        REMOVE,
        DEFER
    }

    private static final class QueuedCandidate {
        private int entityId;
        private final UUID uuid;
        private ReplacementContext context;
        private long nextAttemptTick;
        private long expirationTick;
        private boolean rollApproved;
        private int placementRetryCount;
        private long placementRetryStartTick;
        private long placementRetryScheduledTick;
        private long nextPlacementRetryTick;
        private long placementRetryDeadlineTick;

        private QueuedCandidate(
                int entityId,
                UUID uuid,
                ReplacementContext context,
                long nextAttemptTick,
                long expirationTick
        ) {
            this.entityId = entityId;
            this.uuid = uuid;
            this.context = context;
            this.nextAttemptTick = nextAttemptTick;
            this.expirationTick = expirationTick;
            this.rollApproved = false;
            this.placementRetryCount = 0;
            this.placementRetryStartTick = 0L;
            this.placementRetryScheduledTick = -1L;
            this.nextPlacementRetryTick = 0L;
            this.placementRetryDeadlineTick = 0L;
        }
    }

    private static final class LocalReservation {
        private final double x;
        private final double z;
        private final long untilTick;

        private LocalReservation(
                double x,
                double z,
                long untilTick
        ) {
            this.x = x;
            this.z = z;
            this.untilTick = untilTick;
        }
    }

    private static final class OriginStats {
        private long nextReportTick;
        private long joined;
        private long queued;
        private long expired;
        private long classificationPass;
        private long classificationReject;
        private long alreadyEvaluated;
        private long revalidationReject;
        private long rollPass;
        private long rollFail;
        private long capacityPass;
        private long capacityReject;
        private long placementPass;
        private long placementReject;
        private long placementRetryScheduled;
        private long placementRetryAttempt;
        private long placementRetrySuccess;
        private long placementRetryExhausted;
        private long placementRetryInvalidated;
        private long factoryAttempt;
        private long success;
        private long rollback;
        private long originalPreserved;

        private OriginStats(long now) {
            this.nextReportTick = now + FUNNEL_REPORT_INTERVAL_TICKS;
        }

        private void reportIfDue(
                World world,
                MumakilFormationOrigin origin,
                boolean force
        ) {
            if (!ENABLE_REPLACEMENT_FUNNEL
                    || world == null
                    || world.isRemote
                    || !hasActivity()) {
                return;
            }
            long now = world.getTotalWorldTime();
            if (!force && now < this.nextReportTick) {
                return;
            }
            System.out.println(
                    "[LOTRMoreMobs] Mumak replacement funnel:"
                            + " origin=" + origin
                            + " dim=" + world.provider.dimensionId
                            + " joined=" + this.joined
                            + " queued=" + this.queued
                            + " expired=" + this.expired
                            + " classificationPass="
                            + this.classificationPass
                            + " classificationReject="
                            + this.classificationReject
                            + " alreadyEvaluated="
                            + this.alreadyEvaluated
                            + " revalidationReject="
                            + this.revalidationReject
                            + " rollPass=" + this.rollPass
                            + " rollFail=" + this.rollFail
                            + " capacityPass=" + this.capacityPass
                            + " capacityReject=" + this.capacityReject
                            + " placementPass=" + this.placementPass
                            + " placementReject="
                            + this.placementReject
                            + " placementRetryScheduled="
                            + this.placementRetryScheduled
                            + " placementRetryAttempt="
                            + this.placementRetryAttempt
                            + " placementRetrySuccess="
                            + this.placementRetrySuccess
                            + " placementRetryExhausted="
                            + this.placementRetryExhausted
                            + " placementRetryInvalidated="
                            + this.placementRetryInvalidated
                            + " factoryAttempt=" + this.factoryAttempt
                            + " success=" + this.success
                            + " rollback=" + this.rollback
                            + " originalPreserved="
                            + this.originalPreserved
            );
            reset(now);
        }

        private boolean hasActivity() {
            return this.joined != 0L
                    || this.queued != 0L
                    || this.expired != 0L
                    || this.classificationPass != 0L
                    || this.classificationReject != 0L
                    || this.alreadyEvaluated != 0L
                    || this.revalidationReject != 0L
                    || this.rollPass != 0L
                    || this.rollFail != 0L
                    || this.capacityPass != 0L
                    || this.capacityReject != 0L
                    || this.placementPass != 0L
                    || this.placementReject != 0L
                    || this.placementRetryScheduled != 0L
                    || this.placementRetryAttempt != 0L
                    || this.placementRetrySuccess != 0L
                    || this.placementRetryExhausted != 0L
                    || this.placementRetryInvalidated != 0L
                    || this.factoryAttempt != 0L
                    || this.success != 0L
                    || this.rollback != 0L
                    || this.originalPreserved != 0L;
        }

        private void reset(long now) {
            this.nextReportTick = now + FUNNEL_REPORT_INTERVAL_TICKS;
            this.joined = 0L;
            this.queued = 0L;
            this.expired = 0L;
            this.classificationPass = 0L;
            this.classificationReject = 0L;
            this.alreadyEvaluated = 0L;
            this.revalidationReject = 0L;
            this.rollPass = 0L;
            this.rollFail = 0L;
            this.capacityPass = 0L;
            this.capacityReject = 0L;
            this.placementPass = 0L;
            this.placementReject = 0L;
            this.placementRetryScheduled = 0L;
            this.placementRetryAttempt = 0L;
            this.placementRetrySuccess = 0L;
            this.placementRetryExhausted = 0L;
            this.placementRetryInvalidated = 0L;
            this.factoryAttempt = 0L;
            this.success = 0L;
            this.rollback = 0L;
            this.originalPreserved = 0L;
        }
    }
}
