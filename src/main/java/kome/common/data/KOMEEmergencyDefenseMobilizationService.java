package kome.common.data;

import kome.common.KOMEReflection;
import kome.common.config.KOMEConfigRegistry;
import lotr.common.entity.npc.LOTREntityNPC;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Conflict-created native cohort delivery and population disposition authority. */
public final class KOMEEmergencyDefenseMobilizationService {
    public static final KOMEEmergencyDefenseMobilizationService INSTANCE =
        new KOMEEmergencyDefenseMobilizationService();
    public static final int MAX_DEPLOYMENTS_PER_PASS = 4;
    /** Matches the existing safe deployment search: local maneuvering without multi-chunk pursuit. */
    public static final int BATTLEFIELD_HOME_RADIUS =
        KOMEStrategicDeploymentResolver.DEFAULT_SEARCH_RADIUS;

    private KOMEEmergencyDefenseMobilizationService() { }

    interface PlacementAuthority {
        KOMEStrategicDeploymentResolver.Validation resolve(KOMEWorldData data, World world,
            String tileId, LOTREntityNPC entity);
    }

    private static final PlacementAuthority LIVE_PLACEMENT = new PlacementAuthority() {
        @Override public KOMEStrategicDeploymentResolver.Validation resolve(
                KOMEWorldData data, World world, String tileId, LOTREntityNPC entity) {
            return resolveDeployment(data, world, tileId, entity);
        }
    };

    public static final class Plan {
        public final boolean applicable;
        public final String reason;
        public final KOMEEmergencyDefenseCommitment commitment;
        public final KOMEEmergencyDefenseObservation observation;
        private Plan(boolean applicable, String reason,
                KOMEEmergencyDefenseCommitment commitment,
                KOMEEmergencyDefenseObservation observation) {
            this.applicable = applicable; this.reason = reason;
            this.commitment = commitment; this.observation = observation;
        }
        static Plan none(String reason) { return new Plan(false, reason, null, null); }
        static Plan ready(KOMEEmergencyDefenseCommitment value) {
            return new Plan(true, "Ready", value, null);
        }
        static Plan deferred(KOMEEmergencyDefenseObservation value) {
            return new Plan(true, value.reason, null, value);
        }
    }

    /** Pure/read-only preparation before ConflictRecord publication. */
    public Plan prepareForCreatedConflict(KOMEWorldData data, World world,
            String conflictId, String tileId, String nativeFaction, long nowMillis) {
        String tile = KOMEConquestTile.normalizeId(tileId);
        String faction = KOMEAlliance.normalizeFactionKey(nativeFaction);
        KOMEEmergencyDefenseTemplateRegistry.require(faction);
        if (data.emergencyDefenseCommitments.containsKey(conflictId)
                || data.emergencyDefenseObservations.containsKey(conflictId))
            return Plan.none("Emergency Defense commitment already exists.");
        for (KOMEEmergencyDefenseCommitment existing
                : data.emergencyDefenseCommitments.values())
            if (tile.equals(existing.tileId)
                    && existing.state != KOMEEmergencyDefenseCommitment.State.DEMOBILIZED)
                throw new IllegalStateException(
                    "A previous Emergency Defense commitment on this tile is not safely demobilized.");
        if (!KOMEEmergencyDefenseService.INSTANCE.eligibleForAuthoritativeAttack(
                data, faction, nowMillis))
            return Plan.deferred(new KOMEEmergencyDefenseObservation(conflictId, tile,
                faction, nowMillis,
                "Recognized ruler activity currently defers Emergency Defense eligibility."));
        return Plan.ready(buildCommitment(data, world, conflictId, tile, faction, nowMillis));
    }

    private KOMEEmergencyDefenseCommitment buildCommitment(KOMEWorldData data, World world,
            String conflictId, String tileId, String nativeFaction, long nowMillis) {
        KOMEEmergencyDefenseTemplateRegistry.Template template =
            KOMEEmergencyDefenseTemplateRegistry.require(nativeFaction);
        int cost = template.populationCost(world);
        Map<String, Map<String, BigInteger>> byTile =
            KOMEPopulationRateService.getExactTilePopulationRates(data,
                KOMEConfigRegistry.population());
        BigInteger tileRate = BigInteger.ZERO;
        Map<String, BigInteger> tileRates = byTile.get(KOMEConquestTile.normalizeId(tileId));
        if (tileRates != null && tileRates.get(KOMEAlliance.normalizeFactionKey(nativeFaction)) != null)
            tileRate = tileRates.get(KOMEAlliance.normalizeFactionKey(nativeFaction));
        BigInteger totalRate = KOMEPopulationRateService.getExactDailyPopulationRates(data,
            KOMEConfigRegistry.population()).get(KOMEAlliance.normalizeFactionKey(nativeFaction));
        if (totalRate == null) totalRate = BigInteger.ZERO;
        long available = KOMEPopulationService.getAvailablePopulationCenti(data, nativeFaction);
        int units = KOMEEmergencyDefenseCommitment.allocationUnits(available,
            tileRate, totalRate, cost);
        Map<String, KOMEEmergencyDefenseCommitment.Defender> defenders =
            new LinkedHashMap<String, KOMEEmergencyDefenseCommitment.Defender>();
        for (int sequence = 1; sequence <= units; sequence++) {
            String intent = "ED-" + conflictId.substring(2) + "-" + sequence;
            UUID entity = UUID.nameUUIDFromBytes(("KOME|EMERGENCY_DEFENSE|" + intent)
                .getBytes(StandardCharsets.UTF_8));
            defenders.put(intent, new KOMEEmergencyDefenseCommitment.Defender(intent,
                entity, cost, nowMillis,
                KOMEEmergencyDefenseCommitment.Disposition.PENDING, false,
                "Durable deployment intent"));
        }
        long committed = Math.multiplyExact(Math.multiplyExact((long) units, (long) cost),
            KOMEPopulationService.CENTI_PER_POPULATION);
        return new KOMEEmergencyDefenseCommitment(conflictId, tileId,
            nativeFaction, tileRate.longValueExact(), totalRate.longValueExact(), available,
            template.id, cost, units, committed, nowMillis,
            units == 0 ? KOMEEmergencyDefenseCommitment.State.ACTIVE
                : KOMEEmergencyDefenseCommitment.State.DEPLOYING,
            defenders, units == 0 ? "Production share funds zero whole defenders."
                : "Population committed; bounded deployment pending.");
    }

    /** Publishes the prepared snapshot and removes its exact whole-unit budget from Available. */
    public boolean publishPlan(KOMEWorldData data, Plan plan) {
        if (data == null || plan == null || !plan.applicable)
            return false;
        if (plan.observation != null) return publishObservation(data, plan.observation);
        if (plan.commitment == null) return false;
        return publishCommitment(data, plan.commitment, null);
    }

    private boolean publishObservation(KOMEWorldData data,
            KOMEEmergencyDefenseObservation value) {
        if (data.emergencyDefenseCommitments.containsKey(value.conflictId)
                || data.emergencyDefenseObservations.containsKey(value.conflictId)) return false;
        if (!KOMEEmergencyDefenseService.INSTANCE.hasNativeDefensiveConflictAuthority(
                data, value.conflictId, value.tileId, value.nativeFaction))
            return false;
        data.emergencyDefenseObservations.put(value.conflictId, value);
        data.markDirty();
        return true;
    }

    private boolean publishCommitment(KOMEWorldData data,
            KOMEEmergencyDefenseCommitment value,
            KOMEEmergencyDefenseObservation expectedObservation) {
        if (data.emergencyDefenseCommitments.containsKey(value.conflictId)) return false;
        if (expectedObservation != null
                && data.emergencyDefenseObservations.get(value.conflictId) != expectedObservation)
            return false;
        KOMEConflictRecord conflict = data.getConflictService().get(value.tileId);
        if (conflict == null || !conflict.isActive()
                || !value.conflictId.equals(conflict.getConflictId())
                || !conflict.getFactionParticipation().containsKey(value.nativeFaction))
            return false;
        if (value.populationCommittedCenti > 0L
                && !KOMEPopulationService.trySpendCenti(data, value.nativeFaction,
                    value.populationCommittedCenti)) return false;
        int auditSize = data.centralAudit.size();
        try {
            data.emergencyDefenseCommitments.put(value.conflictId, value);
            if (expectedObservation != null)
                data.emergencyDefenseObservations.remove(value.conflictId);
            KOMEAuditService.record(data, value.createdAtMillis, "EMERGENCY_DEFENSE",
                "MOBILIZE", "server", value.conflictId,
                "Conflict-created native Emergency Defense mobilization",
                "tile=" + value.tileId + ";faction=" + value.nativeFaction
                    + ";units=" + value.calculatedUnitCount + ";populationCenti="
                    + value.populationCommittedCenti);
            data.markDirty();
            return true;
        } catch (RuntimeException failure) {
            data.emergencyDefenseCommitments.remove(value.conflictId);
            if (expectedObservation != null)
                data.emergencyDefenseObservations.put(value.conflictId, expectedObservation);
            while (data.centralAudit.size() > auditSize)
                data.centralAudit.remove(data.centralAudit.size() - 1);
            if (value.populationCommittedCenti > 0L)
                KOMEPopulationService.grantCenti(data, value.nativeFaction,
                    value.populationCommittedCenti);
            throw failure;
        }
    }

    /** Bounded durable delivery. Pending intent + deterministic UUID makes retries idempotent. */
    public int processPending(KOMEWorldData data, World world, long nowMillis) {
        return processPending(data, world, nowMillis, LIVE_PLACEMENT);
    }

    int processPending(KOMEWorldData data, World world, long nowMillis,
            PlacementAuthority placementAuthority) {
        if (data == null) return 0;
        java.util.Set<String> commitmentsAtPassStart = new java.util.TreeSet<String>(
            data.emergencyDefenseCommitments.keySet());
        processDeferredObservations(data, world, nowMillis);
        int processed = 0;
        // A newly promoted receipt publishes durable intents first. Physical delivery starts on
        // a later pass, preserving the authority/placement separation even inside one tick loop.
        for (String conflictId : commitmentsAtPassStart) {
            KOMEEmergencyDefenseCommitment value =
                data.emergencyDefenseCommitments.get(conflictId);
            if (value == null) continue;
            World deploymentWorld = worldForCommitment(data, world, value);
            if (value.state == KOMEEmergencyDefenseCommitment.State.ENDING) {
                processed += processDemobilization(data, deploymentWorld, value,
                    MAX_DEPLOYMENTS_PER_PASS - processed);
            } else if (value.state == KOMEEmergencyDefenseCommitment.State.DEPLOYING) {
                if (deploymentWorld != null) processed += processDeployment(data, deploymentWorld, value,
                    nowMillis, MAX_DEPLOYMENTS_PER_PASS - processed, placementAuthority);
            }
            if (processed >= MAX_DEPLOYMENTS_PER_PASS) break;
        }
        return processed;
    }

    private void processDeferredObservations(KOMEWorldData data, World supplied,
            long nowMillis) {
        int examined = 0;
        for (String conflictId : new java.util.TreeSet<String>(
                data.emergencyDefenseObservations.keySet())) {
            if (examined++ >= MAX_DEPLOYMENTS_PER_PASS) break;
            KOMEEmergencyDefenseObservation observation =
                data.emergencyDefenseObservations.get(conflictId);
            if (observation == null) continue;
            KOMEConflictRecord conflict = data.getConflictService().get(observation.tileId);
            if (conflict == null || !conflict.isActive()
                    || !observation.conflictId.equals(conflict.getConflictId())) {
                data.emergencyDefenseObservations.remove(conflictId);
                data.markDirty();
                continue;
            }
            KOMEEmergencyDefenseService.Assessment assessment =
                KOMEEmergencyDefenseService.INSTANCE.assess(data,
                    observation.nativeFaction, nowMillis);
            if (!assessment.eligible
                    || !assessment.qualifyingConflictIds.contains(observation.conflictId))
                continue;
            World world = worldForObservation(data, supplied, observation);
            if (world == null) continue;
            try {
                KOMEEmergencyDefenseCommitment commitment = buildCommitment(data, world,
                    observation.conflictId, observation.tileId, observation.nativeFaction,
                    nowMillis);
                publishCommitment(data, commitment, observation);
            } catch (RuntimeException notReady) {
                // Structural startup validation remains fail-closed. Runtime world/config
                // readiness leaves the exact durable observation available for a later retry.
            }
        }
    }

    private int processDeployment(KOMEWorldData data, World world,
            KOMEEmergencyDefenseCommitment value, long nowMillis, int limit,
            PlacementAuthority placementAuthority) {
        int processed = 0;
        List<String> intents = new ArrayList<String>(value.defenders.keySet());
        for (String intentId : intents) {
            if (processed >= limit) break;
            value = data.emergencyDefenseCommitments.get(value.conflictId);
            KOMEEmergencyDefenseCommitment.Defender defender = value == null
                ? null : value.defenders.get(intentId);
            if (defender == null || defender.disposition
                    != KOMEEmergencyDefenseCommitment.Disposition.PENDING) continue;
            Entity existing = find(world, defender.entityUuid);
            KOMEEmergencyDefenseEntityMarker.Marker existingMarker = existing instanceof LOTREntityNPC
                ? KOMEEmergencyDefenseEntityMarker.read((LOTREntityNPC) existing) : null;
            if (existing != null && !existing.isDead) {
                if (existingMarker != null
                        && defender.intentId.equals(existingMarker.intentId)
                        && value.conflictId.equals(existingMarker.conflictId)
                        && restoreBattlefieldHome(data, value,
                            (LOTREntityNPC) existing, existingMarker)) {
                    update(data, value, defender.with(
                        KOMEEmergencyDefenseCommitment.Disposition.ACTIVE, false,
                        "Recovered previously spawned intent"));
                } else {
                    update(data, value, defender.with(defender.disposition, false,
                        "Deterministic defender UUID is occupied by unrelated authority."));
                }
                value = data.emergencyDefenseCommitments.get(value.conflictId);
                processed++;
                continue;
            }
            KOMEEmergencyDefenseTemplateRegistry.Template template =
                KOMEEmergencyDefenseTemplateRegistry.require(value.nativeFaction);
            LOTREntityNPC npc = template.create(world);
            npc.setUniqueID(defender.entityUuid);
            KOMEStrategicDeploymentResolver.Validation placement =
                placementAuthority.resolve(data, world, value.tileId, npc);
            if (!placement.valid) {
                npc.setDead();
                update(data, value, defender.with(defender.disposition, false,
                    placement.reason));
                value = data.emergencyDefenseCommitments.get(value.conflictId);
                processed++;
                continue;
            }
            npc.setLocationAndAngles(placement.anchor.x, placement.anchor.y,
                placement.anchor.z, 0F, 0F);
            establishBattlefieldHome(npc, defender.intentId, value.conflictId,
                MathHelper.floor_double(placement.anchor.x),
                MathHelper.floor_double(placement.anchor.y),
                MathHelper.floor_double(placement.anchor.z));
            if (!world.spawnEntityInWorld(npc)) {
                npc.setDead();
                update(data, value, defender.with(defender.disposition, false,
                    "World rejected defender spawn; retry remains pending."));
            } else {
                update(data, value, defender.with(
                    KOMEEmergencyDefenseCommitment.Disposition.ACTIVE, false,
                    "Native defender deployed"));
            }
            value = data.emergencyDefenseCommitments.get(value.conflictId);
            processed++;
        }
        value = data.emergencyDefenseCommitments.get(value.conflictId);
        if (value.count(KOMEEmergencyDefenseCommitment.Disposition.PENDING) == 0
                && value.state == KOMEEmergencyDefenseCommitment.State.DEPLOYING) {
            data.emergencyDefenseCommitments.put(value.conflictId,
                value.withState(KOMEEmergencyDefenseCommitment.State.ACTIVE,
                    "All Emergency Defense intents resolved."));
            data.markDirty();
        }
        return processed;
    }

    public boolean markDead(KOMEWorldData data, UUID entityUuid, long nowMillis) {
        if (data == null || entityUuid == null) return false;
        for (KOMEEmergencyDefenseCommitment value :
                new ArrayList<KOMEEmergencyDefenseCommitment>(
                    data.emergencyDefenseCommitments.values())) {
            for (KOMEEmergencyDefenseCommitment.Defender defender : value.defenders.values()) {
                if (entityUuid.equals(defender.entityUuid)
                        && defender.disposition == KOMEEmergencyDefenseCommitment.Disposition.ACTIVE) {
                    update(data, value, defender.with(
                        KOMEEmergencyDefenseCommitment.Disposition.DEAD, false,
                        "Confirmed by LivingDeathEvent at " + nowMillis));
                    return true;
                }
            }
        }
        return false;
    }

    /** Downstream battle resolution reads this cohort without manufacturing a detachment. */
    public int activeMilitaryCount(KOMEWorldData data, String conflictId) {
        KOMEEmergencyDefenseCommitment value = data == null ? null
            : data.emergencyDefenseCommitments.get(conflictId);
        return value == null ? 0
            : value.count(KOMEEmergencyDefenseCommitment.Disposition.ACTIVE);
    }

    public boolean preventsDespawn(KOMEWorldData data, UUID entityUuid) {
        if (data == null || entityUuid == null) return false;
        for (KOMEEmergencyDefenseCommitment value :
                data.emergencyDefenseCommitments.values())
            for (KOMEEmergencyDefenseCommitment.Defender defender : value.defenders.values())
                if (entityUuid.equals(defender.entityUuid)
                        && defender.disposition ==
                            KOMEEmergencyDefenseCommitment.Disposition.ACTIVE)
                    return true;
        return false;
    }

    /** Entity-NBT marker + deterministic UUID is the restart/crash delivery receipt. */
    public boolean reconcileLoadedEntity(KOMEWorldData data, LOTREntityNPC npc) {
        if (data == null || npc == null) return false;
        KOMEEmergencyDefenseEntityMarker.Marker marker =
            KOMEEmergencyDefenseEntityMarker.read(npc);
        if (marker == null) return false;
        UUID uuid = KOMEReflection.getEntityUUID(npc);
        for (KOMEEmergencyDefenseCommitment value :
                new ArrayList<KOMEEmergencyDefenseCommitment>(
                    data.emergencyDefenseCommitments.values())) {
            KOMEEmergencyDefenseCommitment.Defender defender = value.defenders.get(marker.intentId);
            if (defender == null) continue;
            if (!uuid.equals(defender.entityUuid)
                    || !value.conflictId.equals(marker.conflictId)) {
                npc.setDead();
                return true;
            }
            if (value.state == KOMEEmergencyDefenseCommitment.State.ENDING
                    && defender.disposition ==
                        KOMEEmergencyDefenseCommitment.Disposition.ACTIVE) {
                if (!KOMEPopulationService.canGrantCenti(data, value.nativeFaction,
                        KOMEPopulationService.wholeToCenti(defender.populationCost)))
                    return true;
                npc.setDead();
                refund(data, value, defender,
                    KOMEEmergencyDefenseCommitment.Disposition.SURVIVED_REFUNDED,
                    "Loaded survivor safely demobilized after conflict end");
                return true;
            }
            if (defender.disposition == KOMEEmergencyDefenseCommitment.Disposition.ACTIVE
                    || defender.disposition == KOMEEmergencyDefenseCommitment.Disposition.PENDING) {
                if (!restoreBattlefieldHome(data, value, npc, marker)) {
                    // The entity remains unresolved and unrefunded. Do not let an entity with a
                    // malformed or unavailable battlefield authority roam under this marker.
                    npc.setAttackTarget(null);
                    npc.getNavigator().clearPathEntity();
                    return true;
                }
            }
            if (defender.disposition == KOMEEmergencyDefenseCommitment.Disposition.PENDING) {
                update(data, value, defender.with(
                    KOMEEmergencyDefenseCommitment.Disposition.ACTIVE, false,
                    "Reconciled loaded native defender"));
            } else if (defender.disposition == KOMEEmergencyDefenseCommitment.Disposition.DEAD
                    || defender.populationRefunded) npc.setDead();
            return true;
        }
        // A marked entity without durable authority is an uncharged crash orphan.
        npc.setDead();
        return true;
    }

    static void establishBattlefieldHome(LOTREntityNPC npc, String intentId,
            String conflictId, int homeX, int homeY, int homeZ) {
        KOMEEmergencyDefenseEntityMarker.mark(npc, intentId, conflictId,
            homeX, homeY, homeZ, BATTLEFIELD_HOME_RADIUS);
        npc.setHomeArea(homeX, homeY, homeZ, BATTLEFIELD_HOME_RADIUS);
        KOMEEmergencyDefenseHomeAI.install(npc);
    }

    private static boolean restoreBattlefieldHome(KOMEWorldData data,
            KOMEEmergencyDefenseCommitment commitment, LOTREntityNPC npc,
            KOMEEmergencyDefenseEntityMarker.Marker marker) {
        if (marker.hasHome) {
            if (marker.homeRadius != BATTLEFIELD_HOME_RADIUS) return false;
            npc.setHomeArea(marker.homeX, marker.homeY, marker.homeZ, marker.homeRadius);
            KOMEEmergencyDefenseHomeAI.install(npc);
            return true;
        }
        // Compatibility for Phase 2 acceptance worlds created before the home marker existed:
        // recover the same canonical safe deployment hierarchy, never the NPC's wandered position.
        KOMEStrategicDeploymentResolver.Validation placement =
            resolveDeployment(data, npc.worldObj, commitment.tileId, npc);
        if (!placement.valid) return false;
        establishBattlefieldHome(npc, marker.intentId, marker.conflictId,
            MathHelper.floor_double(placement.anchor.x),
            MathHelper.floor_double(placement.anchor.y),
            MathHelper.floor_double(placement.anchor.z));
        return true;
    }

    public void onConflictEnded(KOMEWorldData data, String conflictId, long nowMillis) {
        if (data.emergencyDefenseObservations.remove(conflictId) != null)
            data.markDirty();
        KOMEEmergencyDefenseCommitment value =
            data.emergencyDefenseCommitments.get(conflictId);
        if (value == null || value.state == KOMEEmergencyDefenseCommitment.State.DEMOBILIZED
                || value.state == KOMEEmergencyDefenseCommitment.State.ENDING) return;
        data.emergencyDefenseCommitments.put(conflictId,
            value.withState(KOMEEmergencyDefenseCommitment.State.ENDING,
                "Conflict ended at " + nowMillis + "; safe demobilization pending."));
        data.markDirty();
    }

    private int processDemobilization(KOMEWorldData data, World world,
            KOMEEmergencyDefenseCommitment value, int limit) {
        int processed = 0;
        for (KOMEEmergencyDefenseCommitment.Defender defender : value.defenders.values()) {
            if (processed >= limit) break;
            if (defender.disposition == KOMEEmergencyDefenseCommitment.Disposition.DEAD
                    || defender.populationRefunded) continue;
            if (defender.disposition == KOMEEmergencyDefenseCommitment.Disposition.PENDING) {
                if (!KOMEPopulationService.canGrantCenti(data, value.nativeFaction,
                        KOMEPopulationService.wholeToCenti(defender.populationCost))) continue;
                refund(data, value, defender,
                    KOMEEmergencyDefenseCommitment.Disposition.FAILED_REFUNDED,
                    "Undeployed intent refunded after conflict end");
                value = data.emergencyDefenseCommitments.get(value.conflictId);
                processed++;
                continue;
            }
            Entity entity = find(world, defender.entityUuid);
            if (entity == null || entity.isDead) continue; // unloaded is UNKNOWN, never death/survival
            if (!KOMEPopulationService.canGrantCenti(data, value.nativeFaction,
                    KOMEPopulationService.wholeToCenti(defender.populationCost))) continue;
            entity.setDead();
            refund(data, value, defender,
                KOMEEmergencyDefenseCommitment.Disposition.SURVIVED_REFUNDED,
                "Survivor safely demobilized and refunded");
            value = data.emergencyDefenseCommitments.get(value.conflictId);
            processed++;
        }
        value = data.emergencyDefenseCommitments.get(value.conflictId);
        boolean complete = true;
        for (KOMEEmergencyDefenseCommitment.Defender defender : value.defenders.values())
            if (defender.disposition != KOMEEmergencyDefenseCommitment.Disposition.DEAD
                    && !defender.populationRefunded) complete = false;
        if (complete) {
            data.emergencyDefenseCommitments.put(value.conflictId,
                value.withState(KOMEEmergencyDefenseCommitment.State.DEMOBILIZED,
                    "All defender population dispositions are final."));
            data.markDirty();
        }
        return processed;
    }

    private void refund(KOMEWorldData data, KOMEEmergencyDefenseCommitment value,
            KOMEEmergencyDefenseCommitment.Defender defender,
            KOMEEmergencyDefenseCommitment.Disposition disposition, String reason) {
        if (defender.populationRefunded) return;
        KOMEPopulationService.grantCenti(data, value.nativeFaction,
            KOMEPopulationService.wholeToCenti(defender.populationCost));
        update(data, value, defender.with(disposition, true, reason));
    }

    private static void update(KOMEWorldData data, KOMEEmergencyDefenseCommitment value,
            KOMEEmergencyDefenseCommitment.Defender defender) {
        KOMEEmergencyDefenseCommitment.State state = value.state;
        data.emergencyDefenseCommitments.put(value.conflictId,
            value.withDefender(defender, state, value.diagnostic));
        data.markDirty();
    }

    private static KOMEStrategicDeploymentResolver.Validation resolveDeployment(
            KOMEWorldData data, World world, String tileId, Entity entity) {
        KOMEConquestTile tile = data.getConquestTileIfPresent(tileId);
        if (tile == null)
            return KOMEStrategicDeploymentResolver.Validation.invalid(
                "Attacked tile authority is unavailable.");
        data.ensureDefaultArrivalPoint(tile);
        KOMETileWaypoint waypoint = data.getTileWaypoint(tileId, KOMETileWaypoint.RALLY);
        if (waypoint == null)
            return KOMEStrategicDeploymentResolver.Validation.invalid(
                "No canonical tile deployment origin is configured.");
        if (world == null || world.provider == null
                || world.provider.dimensionId != waypoint.dimensionId)
            return KOMEStrategicDeploymentResolver.Validation.invalid(
                "Canonical tile deployment world is unavailable.");
        return KOMEStrategicDeploymentResolver.resolveAround(world, tileId,
            waypoint.x, waypoint.y, waypoint.z,
            KOMEStrategicDeploymentResolver.DEFAULT_SEARCH_RADIUS,
            entity == null ? 0.6D : entity.width,
            entity == null ? 1.8D : entity.height);
    }

    private static Entity find(World world, UUID id) {
        if (world == null || id == null) return null;
        for (Object raw : world.loadedEntityList) {
            if (raw instanceof Entity) {
                Entity entity = (Entity) raw;
                if (id.equals(KOMEReflection.getEntityUUID(entity))) return entity;
            }
        }
        return null;
    }

    private static World worldForCommitment(KOMEWorldData data, World supplied,
            KOMEEmergencyDefenseCommitment value) {
        KOMEConquestTile tile = data.getConquestTileIfPresent(value.tileId);
        if (tile != null) data.ensureDefaultArrivalPoint(tile);
        KOMETileWaypoint waypoint = data.getTileWaypoint(value.tileId, KOMETileWaypoint.RALLY);
        int dimension = waypoint == null ? Integer.MIN_VALUE : waypoint.dimensionId;
        if (supplied != null && supplied.provider != null
                && supplied.provider.dimensionId == dimension) return supplied;
        net.minecraft.server.MinecraftServer server =
            net.minecraft.server.MinecraftServer.getServer();
        return server == null || dimension == Integer.MIN_VALUE ? null
            : server.worldServerForDimension(dimension);
    }

    private static World worldForObservation(KOMEWorldData data, World supplied,
            KOMEEmergencyDefenseObservation value) {
        KOMEConquestTile tile = data.getConquestTileIfPresent(value.tileId);
        if (tile != null) data.ensureDefaultArrivalPoint(tile);
        KOMETileWaypoint waypoint = data.getTileWaypoint(value.tileId, KOMETileWaypoint.RALLY);
        int dimension = waypoint == null ? Integer.MIN_VALUE : waypoint.dimensionId;
        if (supplied != null && supplied.provider != null
                && supplied.provider.dimensionId == dimension) return supplied;
        net.minecraft.server.MinecraftServer server =
            net.minecraft.server.MinecraftServer.getServer();
        return server == null || dimension == Integer.MIN_VALUE ? null
            : server.worldServerForDimension(dimension);
    }
}
